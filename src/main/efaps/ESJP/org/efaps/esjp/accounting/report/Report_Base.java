/*
 * Copyright 2003 - 2016 The eFaps Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.efaps.esjp.accounting.report;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.efaps.admin.common.SystemConfiguration;
import org.efaps.admin.datamodel.Type;
import org.efaps.admin.datamodel.ui.IUIValue;
import org.efaps.admin.dbproperty.DBProperties;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Parameter.ParameterValues;
import org.efaps.admin.event.Return;
import org.efaps.admin.event.Return.ReturnValues;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.admin.program.jasper.JasperUtil;
import org.efaps.api.ui.IUserInterface;
import org.efaps.ci.CIAdminProgram;
import org.efaps.db.AttributeQuery;
import org.efaps.db.Delete;
import org.efaps.db.Insert;
import org.efaps.db.Instance;
import org.efaps.db.InstanceQuery;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.PrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.esjp.accounting.Period;
import org.efaps.esjp.accounting.util.Accounting;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.ci.CIERP;
import org.efaps.esjp.common.jasperreport.StandartReport;
import org.efaps.esjp.common.jasperreport.StandartReport_Base;
import org.efaps.esjp.common.uiform.Field_Base.DropDownPosition;
import org.efaps.esjp.erp.Currency;
import org.efaps.esjp.erp.CurrencyInst;
import org.efaps.esjp.erp.RateInfo;
import org.efaps.esjp.erp.util.ERP;
import org.efaps.util.EFapsException;
import org.joda.time.DateTime;

import net.sf.dynamicreports.jasper.builder.JasperReportBuilder;
import net.sf.dynamicreports.report.base.expression.AbstractSimpleExpression;
import net.sf.dynamicreports.report.builder.DynamicReports;
import net.sf.dynamicreports.report.builder.column.TextColumnBuilder;
import net.sf.dynamicreports.report.builder.style.ConditionalStyleBuilder;
import net.sf.dynamicreports.report.builder.style.StyleBuilder;
import net.sf.dynamicreports.report.builder.style.Styles;
import net.sf.dynamicreports.report.constant.HorizontalAlignment;
import net.sf.dynamicreports.report.constant.VerticalAlignment;
import net.sf.dynamicreports.report.definition.ReportParameters;
import net.sf.dynamicreports.report.exception.DRException;
import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.design.JasperDesign;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 */
@EFapsUUID("0f3596f4-bcfa-4541-8d6b-551562a9b223")
@EFapsApplication("eFapsApp-Accounting")
public abstract class Report_Base
    extends StandartReport
{
    /**
     * Date the transaction must be older.
     */
    private DateTime dateFrom;

    /**
     * Date the transaction must be younger.
     */
    private DateTime dateTo;

    /** The indent. */
    private boolean indent;

    /**
     * Method renders a drop down field containing the mime types.
     *
     * @param _parameter Parameter as passed from the eFaps API
     * @return html snipplet
     */
    public Return getMimeTypeFieldValueUI(final Parameter _parameter)
    {
        final IUIValue uiValue = (IUIValue) _parameter.get(ParameterValues.UIOBJECT);
        final Return ret = new Return();
        final StringBuilder html = new StringBuilder();
        html.append("<select name=\"").append(uiValue.getField().getName()).append("\" ").append(
                        IUserInterface.EFAPSTMPTAG).append(" size=\"1\">");

        final Map<String, String> values = new TreeMap<>();
        values.put(DBProperties.getProperty("org.efaps.esjp.accounting.report.Report.pdf"), "pdf");
        values.put(DBProperties.getProperty("org.efaps.esjp.accounting.report.Report.xls"), "xls");

        for (final Entry<String, String> entry : values.entrySet()) {
            html.append("<option value=\"").append(entry.getValue());
            if ("pdf".equals(entry.getValue())) {
                html.append("\" selected=\"selected");
            }
            html.append("\">").append(entry.getKey()).append("</option>");
        }
        html.append("</select>");

        ret.put(ReturnValues.SNIPLETT, html.toString());
        return ret;
    }

    /**
     * Called from a field value event to get the value for the date from field.
     * @param _parameter    Parameter as passed from the eFaps API
     * @return  new Return containing value
     * @throws EFapsException on error
     */
    public Return getDateFromFieldValue(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        ret.put(ReturnValues.VALUES, getDate(_parameter, true));
        return ret;
    }

    /**
     * Called from a field value event to get the value for the date from field.
     * @param _parameter    Parameter as passed from the eFaps API
     * @return  new Return containing value
     * @throws EFapsException on error
     */
    public Return getDateToFieldValue(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        ret.put(ReturnValues.VALUES, getDate(_parameter, false));
        return ret;
    }

    /**
     * @param _parameter    Parameter as passed from the eFaps API
     * @return  new Return containing value
     * @throws EFapsException on error
     */
    public Return validateDates(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final String dateFromStr = _parameter.getParameterValue("dateFrom");
        final String dateToStr = _parameter.getParameterValue("dateTo");
        final StringBuilder html = new StringBuilder();
        if (dateFromStr != null && !dateFromStr.isEmpty()) {
            final DateTime dateFromTmp = new DateTime(dateFromStr);
            final DateTime fromDate = getDate(_parameter, true);
            if (dateFromTmp.isBefore(fromDate)) {
                html.append(DBProperties.getProperty(Report.class.getName() + ".ValidateDates.From"));
            }
        }
        if (dateToStr != null && !dateToStr.isEmpty()) {
            final DateTime dateToTmp = new DateTime(dateToStr);
            final DateTime toDate = getDate(_parameter, false);
            if (dateToTmp.isAfter(toDate)) {
                html.append(DBProperties.getProperty(Report.class.getName() + ".ValidateDates.To"));
            }
        }
        if (html.length() > 0) {
            ret.put(ReturnValues.SNIPLETT, html.toString());
        } else {
            ret.put(ReturnValues.TRUE, true);
        }
        return ret;
    }

    /**
     * @param _parameter Parameter as passed by the eFaps Date
     * @param _from fomr date or to date is wanted
     * @return date for the UserInterface
     * @throws EFapsException on error
     */
    protected DateTime getDate(final Parameter _parameter,
                               final boolean _from)
        throws EFapsException
    {
        final DateTime ret;
        final SelectBuilder sel = new SelectBuilder();
        Instance inst = null;
        if (_parameter.getInstance() != null && _parameter.getInstance().isValid()) {
            inst = _parameter.getInstance();
            if (_parameter.getInstance().getType().isKindOf(CIAccounting.Period.getType())) {
                sel.attribute(_from ? CIAccounting.Period.FromDate : CIAccounting.Period.ToDate);
            } else if (_parameter.getInstance().getType().isKindOf(CIAccounting.SubPeriod.getType())) {
                sel.attribute(_from ? CIAccounting.SubPeriod.FromDate : CIAccounting.SubPeriod.ToDate);
            } else {
                sel.linkto(CIAccounting.ReportAbstract.PeriodLink).attribute(
                                _from ? CIAccounting.Period.FromDate : CIAccounting.Period.ToDate);
            }
        } else {
            final String[] oids = _parameter.getParameterValues("selectedRow");
            if (oids != null && oids.length > 0) {
                inst = Instance.get(oids[0]);
                if (inst.getType().isKindOf(CIAccounting.AccountAbstract.getType())) {
                    sel.linkto(CIAccounting.AccountAbstract.PeriodAbstractLink)
                                    .attribute(_from ? CIAccounting.Period.FromDate : CIAccounting.Period.ToDate);
                }
            }
        }
        if (inst != null) {
            final PrintQuery print = new PrintQuery(inst);
            print.addSelect(sel);
            if (print.execute()) {
                ret = print.getSelect(sel);
            } else {
                ret = new DateTime();
            }
        } else {
            ret = new DateTime();
        }
        return ret;
    }

    /**
     * Executed to create a report account node from the UserInterface.
     *
     * @param _parameter Parameter as passed from the eFasp API
     * @return new Return
     * @throws EFapsException on error
     */
    public Return createAccountNode(final Parameter _parameter)
        throws EFapsException
    {
        final Insert insert = new Insert(CIAccounting.ReportNodeAccount);
        insert.add(CIAccounting.ReportNodeAccount.ParentLink, _parameter.getInstance().getId());
        insert.add(CIAccounting.ReportNodeAccount.Number, _parameter.getParameterValue("number"));
        insert.add(CIAccounting.ReportNodeAccount.Label, _parameter.getParameterValue("label"));
        insert.add(CIAccounting.ReportNodeAccount.ShowAllways, _parameter.getParameterValue("showAllways"));
        insert.add(CIAccounting.ReportNodeAccount.ShowSum, _parameter.getParameterValue("showSum"));
        insert.add(CIAccounting.ReportNodeAccount.AccountLink,
                        Instance.get(_parameter.getParameterValue("accountLink")).getId());
        insert.execute();
        return new Return();
    }

    /**
     * Executed to delete the report from the UserInterface.
     *
     * @param _parameter Parameter as passed from the eFasp API
     * @return new Return
     * @throws EFapsException on error
     */
    public Return deleteReportTrigger(final Parameter _parameter)
        throws EFapsException
    {
        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.ReportNodeRoot);
        queryBldr.addWhereAttrEqValue(CIAccounting.ReportNodeRoot.ReportLink, _parameter.getInstance().getId());
        final InstanceQuery query = queryBldr.getQuery();
        query.execute();
        while (query.next()) {
            final Delete del = new Delete(query.getCurrentValue());
            del.execute();
        }
        return new Return();
    }

    /**
     * Executed to delete nodes of a report.
     *
     * @param _parameter Parameter as passed from the eFasp API
     * @return new Return
     * @throws EFapsException on error
     */
    public Return deleteNodeTrigger(final Parameter _parameter)
        throws EFapsException
    {
        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.ReportNodeChildAbstract);
        queryBldr.addWhereAttrEqValue(CIAccounting.ReportNodeChildAbstract.ParentLinkAbstract,
                        _parameter.getInstance().getId());
        final InstanceQuery query = queryBldr.getQuery();
        query.execute();
        while (query.next()) {
            final Delete del = new Delete(query.getCurrentValue());
            del.execute();
        }
        return new Return();
    }

    /**
     * Execute the actual creation of the report.
     *
     * @param _parameter Parameter as passed from the eFasp API
     * @throws EFapsException on error
     * @return Return containg the created file
     */
    @Override
    public Return execute(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final String mime = _parameter.getParameterValue("mime");
        final boolean print = "pdf".equalsIgnoreCase(mime);
        this.dateFrom = new DateTime(_parameter.getParameterValue("dateFrom"));
        this.dateTo = new DateTime(_parameter.getParameterValue("dateTo"));
        this.indent = isIndent(_parameter);

        final ReportTree dataTree = new ReportTree(_parameter.getInstance());
        dataTree.addChildren(_parameter);
        for (final AbstractNode node : dataTree.getRootNodes()) {
            node.getSum();
        }

        final List<List<AbstractNode>> table = dataTree.getTable();
        try {

            final JasperReportBuilder jrb = DynamicReports.report();
            final boolean addTitle;
            final boolean addPageHeader;
            final boolean addPageFooter;
            final Map<?, ?> properties = (Map<?, ?>) _parameter.get(ParameterValues.PROPERTIES);
            final String name = (String) properties.get("JasperReport");
            if (name != null) {
                final QueryBuilder queryBldr = new QueryBuilder(CIAdminProgram.JasperReport);
                queryBldr.addWhereAttrEqValue(CIAdminProgram.JasperReport.Name, name);
                final InstanceQuery query = queryBldr.getQuery();
                query.execute();
                Instance instance = null;
                if (query.next()) {
                    instance = query.getCurrentValue();
                } else {
                    throw new EFapsException(StandartReport_Base.class, "execute.ReportNotFound");
                }
                final JasperDesign design = JasperUtil.getJasperDesign(instance);
                addTitle = design.getTitle() == null;
                addPageHeader = design.getPageHeader() == null;
                addPageFooter = design.getPageFooter() == null;
                jrb.setTemplateDesign(design);
            } else {
                addTitle = true;
                addPageHeader = true;
                addPageFooter = true;
            }

            if (addTitle) {
                addTitle(_parameter, jrb, dataTree);
            }
            if (addPageHeader) {
                addPageHeader(_parameter, jrb, dataTree);
            }
            if (addPageFooter) {
                addPageFooter(_parameter, jrb, dataTree);
            }
            if (print) {
                jrb.highlightDetailEvenRows();
            } else {
                jrb.setIgnorePagination(true);
            }

            for (Integer y = 0; y < table.size(); y++) {
                final StyleBuilder numberStyle = getNumberStyle(_parameter, y);
                final StyleBuilder textStyle = getTextStyle(_parameter, y);

                final TextColumnBuilder<String> textColumn = DynamicReports.col.column("column_" + y,
                                DynamicReports.type.stringType());
                textColumn.setStyle(textStyle).setWidth(80);
                jrb.addColumn(textColumn);

                final TextColumnBuilder<BigDecimal> numberColumn = DynamicReports.col.column("sums_" + y,
                                DynamicReports.type.bigDecimalType());
                numberColumn.setStyle(numberStyle).setWidth(20);
                jrb.addColumn(numberColumn);

                jrb.addField("node_" + y, Object.class);
            }

            final JRDataSource ds = new AccountingDataSource(table);
            setFileName(dataTree.getName());
            jrb.setDataSource(ds);

            final SystemConfiguration config = ERP.getSysConfig();
            if (config != null) {
                final String companyName = ERP.COMPANYNAME.get();
                final String companyTaxNumb = ERP.COMPANYTAX.get();

                if (companyName != null && companyTaxNumb != null
                                && !companyName.isEmpty() && !companyTaxNumb.isEmpty()) {
                    jrb.getJasperParameters().put("CompanyName", companyName);
                    jrb.getJasperParameters().put("CompanyTaxNum", companyTaxNumb);
                }
            }
            final PrintQuery printQ = new PrintQuery(_parameter.getInstance());
            final SelectBuilder selPeriodDate = new SelectBuilder()
                            .linkto(CIAccounting.ReportAbstract.PeriodLink)
                            .attribute(CIAccounting.Period.FromDate);
            printQ.addSelect(selPeriodDate);
            printQ.execute();
            final DateTime periodDate = printQ.<DateTime>getSelect(selPeriodDate);
            jrb.getJasperParameters().put("FromDate", this.dateFrom);
            jrb.getJasperParameters().put("ToDate", this.dateTo);
            jrb.getJasperParameters().put("PeriodYear", periodDate);

            ret.put(ReturnValues.VALUES, super.getFile(jrb.toJasperPrint(), mime));
            ret.put(ReturnValues.TRUE, true);
        } catch (final JRException e) {
            throw new EFapsException(Report.class, "JRException", e);
        } catch (final IOException e) {
            throw new EFapsException(Report.class, "IOException", e);
        } catch (final DRException e) {
            throw new EFapsException(Report.class, "DRException", e);
        }
        return ret;
    }



    /**
     * Adds the page footer.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @param _jrb the jrb
     * @param _dataTree the data tree
     */
    protected void addPageFooter(final Parameter _parameter,
                                 final JasperReportBuilder _jrb,
                                 final ReportTree _dataTree)
    {
        // for implementation purpose
    }

    /**
     * Adds the title.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @param _jrb the jrb
     * @param _dataTree the data tree
     */
    protected void addTitle(final Parameter _parameter,
                            final JasperReportBuilder _jrb,
                            final ReportTree _dataTree)
    {
        // for implementation purpose
    }


    /**
     * Adds the page header.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @param _jrb the jrb
     * @param _dataTree the data tree
     */
    protected void addPageHeader(final Parameter _parameter,
                                 final JasperReportBuilder _jrb,
                                 final ReportTree _dataTree)
    {
        _jrb.addPageHeader(DynamicReports.cmp.verticalList(
                        DynamicReports.cmp.text(_dataTree.getName()),
                        DynamicReports.cmp.text(_dataTree.getDescription())));
    }

    /**
     * Gets the number style.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @param _y the _y
     * @return the number style
     */
    protected StyleBuilder getNumberStyle(final Parameter _parameter,
                                          final Integer _y)
    {
        final ConditionalStyleBuilder condition1 = DynamicReports.stl.conditionalStyle(
                        new BoldCondition(_parameter, _y)).setBold(true);

        return DynamicReports.stl.style()
                        .setFont(Styles.font().setFontSize(9))
                        .setAlignment(HorizontalAlignment.RIGHT, VerticalAlignment.MIDDLE)
                        .setPadding(DynamicReports.stl.padding().setRight(5)).addConditionalStyle(condition1);
    }

    /**
     * Gets the text style.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @param _y the _y
     * @return the text style
     */
    protected StyleBuilder getTextStyle(final Parameter _parameter,
                                        final Integer _y)
    {
        final ConditionalStyleBuilder condition1 = DynamicReports.stl.conditionalStyle(
                        new BoldCondition(_parameter, _y)).setBold(true);

        return DynamicReports.stl.style()
                        .setFont(Styles.font().setFontSize(9))
                        .setAlignment(HorizontalAlignment.LEFT, VerticalAlignment.MIDDLE)
                        .addConditionalStyle(condition1);
    }

    /**
     * Gets the total label.
     *
     * @param _parent the parent
     * @return the total label
     */
    protected String getTotalLabel(final AbstractNode _parent)
    {
        return "TOTAL " +  _parent.getLabel();
    }

    /**
     * Checks if is indent.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return true, if is indent
     */
    protected boolean isIndent(final Parameter _parameter)
    {
        return !_parameter.getInstance().getType().isKindOf(CIAccounting.ReportProfitLoss.getType());
    }

    /**
     * Gets the currency.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return the currency
     * @throws EFapsException on error
     */
    protected Currency getCurrency(final Parameter _parameter)
        throws EFapsException
    {
        final Currency ret = new Currency()
        {

            @Override
            protected Type getType4ExchangeRate(final Parameter _parameter)
                throws EFapsException
            {
                final Type typeRet;
                //TODO evaluate that
                //final Long rateCurType = Long.parseLong(_parameter.getParameterValue("rateCurrencyType"));

                if (Accounting.CURRATEEQ.get()) {
                    typeRet = super.getType4ExchangeRate(_parameter);
                } else {
                    typeRet = CIAccounting.ERP_CurrencyRateAccounting.getType();
                }
                return typeRet;
            }
        };
        return ret;
    }

    /**
     * Gets the currency option list.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return the currency option list
     * @throws EFapsException on error
     */
    public Return getCurrencyOptionList(final Parameter _parameter)
        throws EFapsException
    {
        final Currency currency = new Currency();
        final Return ret = currency.currencyDropDownFieldValue(_parameter);
        @SuppressWarnings("unchecked")
        final List<DropDownPosition> values = (List<DropDownPosition>) ret.get(ReturnValues.VALUES);
        final CurrencyInst curInstObj = new Period().evaluteCurrentCurrency(_parameter);
        for (final ListIterator<DropDownPosition> iter = values.listIterator(); iter.hasNext(); ) {
             final DropDownPosition element = iter.next();
             if (element.getValue().equals(curInstObj.getInstance().getOid())) {
                 iter.remove();
             }
        }
        return ret;
    }

    /**
     * The Class BoldCondition.
     */
    private class BoldCondition
        extends AbstractSimpleExpression<Boolean>
    {

        private static final long serialVersionUID = 1L;

        /** The idx. */
        private final Integer idx;

        /**
         * Instantiates a new bold condition.
         *
         * @param _parameter Parameter as passed by the eFaps API
         * @param _idx the idx
         */
        public BoldCondition(final Parameter _parameter,
                             final Integer _idx)
        {
            this.idx = _idx;
        }

        @Override
        public Boolean evaluate(final ReportParameters _reportParameters)
        {
            final AbstractNode node = (AbstractNode) _reportParameters.getFieldValue("node_" + this.idx);
            return Report_Base.this.indent && (node.getLevel() == 0 || node instanceof TotalNode)
                            || !Report_Base.this.indent && node instanceof RootNode;
        }
    }

    /**
     * Class to obtain a report.
     * @author jorge.
     *
     */
    public class ReportTree
    {

        /**
         * List of nodes belonging to this report.
         */
        private final List<AbstractNode> rootNodes = new ArrayList<>();

        /**
         * Instance of this report.
         */
        private final Instance instance;

        /**
         * Name of this report.
         */
        private final String name;

        /**
         * Description for this report.
         */
        private final String description;

        /**
         * Numbering of this report.
         */
        private final String numbering;

        /**
         * @param _instance Instrance of this report
         * @throws EFapsException on error
         */
        public ReportTree(final Instance _instance)
            throws EFapsException
        {
            this.instance = _instance;

            final PrintQuery print = new PrintQuery(this.instance);
            print.addAttribute(CIAccounting.ReportAbstract.Name, CIAccounting.ReportAbstract.Numbering,
                            CIAccounting.ReportAbstract.Description);
            print.execute();
            this.name = print.<String> getAttribute(CIAccounting.ReportAbstract.Name);
            this.description = print.<String> getAttribute(CIAccounting.ReportAbstract.Description);
            this.numbering = print.<String> getAttribute(CIAccounting.ReportAbstract.Numbering);
        }

        /**
         * Getter method for instance variable {@link #rootNodes}.
         *
         * @return value of instance variable {@link #rootNodes}
         */
        public List<AbstractNode> getRootNodes()
        {
            return this.rootNodes;
        }

        /**
         * Adds the children.
         *
         * @param _parameter Parameter as passed by the eFaps API
         * @throws EFapsException on error.
         */
        public void addChildren(final Parameter _parameter)
            throws EFapsException
        {
            final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.ReportNodeRoot);
            queryBldr.addWhereAttrEqValue(CIAccounting.ReportNodeRoot.ReportLink, this.instance.getId());
            final MultiPrintQuery print = queryBldr.getPrint();
            print.addAttribute(CIAccounting.ReportNodeRoot.OID, CIAccounting.ReportNodeRoot.Number,
                            CIAccounting.ReportNodeRoot.Label, CIAccounting.ReportNodeRoot.ShowAllways,
                            CIAccounting.ReportNodeRoot.ShowSum, CIAccounting.ReportNodeRoot.Position);
            print.execute();
            while (print.next()) {
                final String oidTmp = print.<String> getAttribute(CIAccounting.ReportNodeRoot.OID);
                final String number = print.<String> getAttribute(CIAccounting.ReportNodeRoot.Number);
                final String label = print.<String> getAttribute(CIAccounting.ReportNodeRoot.Label);
                final Boolean showAllways = print.<Boolean> getAttribute(CIAccounting.ReportNodeRoot.ShowAllways);
                final Boolean showSum = print.<Boolean> getAttribute(CIAccounting.ReportNodeRoot.ShowSum);
                final Long position = print.<Long> getAttribute(CIAccounting.ReportNodeRoot.Position);
                final AbstractNode child = new RootNode(null, oidTmp, number, label, showAllways, showSum, position);
                this.rootNodes.add(child);
            }
            for (final AbstractNode root : this.rootNodes) {
                root.addChildren(_parameter, 1);
            }
            Collections.sort(this.rootNodes, new Comparator<AbstractNode>() {

                @Override
                public int compare(final AbstractNode _node1,
                                   final AbstractNode _node2)
                {
                    return _node1.getPosition().compareTo(_node2.getPosition());
                }
            });
        }

        /**
         * @return ret.
         */
        public List<List<AbstractNode>> getTable()
        {
            final List<List<AbstractNode>> ret = new ArrayList<>();
            for (final AbstractNode node : this.rootNodes) {
                ret.add(flatten(node));
            }
            return ret;
        }

        /**
         * @param _parent Node.
         * @return ret
         */
        private List<AbstractNode> flatten(final AbstractNode _parent)
        {
            final List<AbstractNode> ret = new ArrayList<>();
            boolean added = false;
            if ((_parent.isShowAllways()
                            || _parent.isShowSum() && _parent.getSum().compareTo(BigDecimal.ZERO) != 0)
                            && Report_Base.this.indent) {
                ret.add(_parent);
                added = true;
            }
            for (final AbstractNode child : _parent.getChildren()) {
                ret.addAll(flatten(child));
            }
            if (added && !_parent.getChildren().isEmpty() && _parent.getChildren().size() > 1) {
                ret.add(new TotalNode(getTotalLabel(_parent), _parent.getSum(), _parent.getLevel()));
                _parent.setTextOnly(true);
            } else if ((_parent.isShowAllways()
                            || _parent.isShowSum() && _parent.getSum().compareTo(BigDecimal.ZERO) != 0)
                            && !Report_Base.this.indent) {
                _parent.setLevel(0);
                ret.add(_parent);
            }
            return ret;
        }

        /**
         * Getter method for the instance variable {@link #instance}.
         *
         * @return value of instance variable {@link #instance}
         */
        public Instance getInstance()
        {
            return this.instance;
        }

        /**
         * Getter method for the instance variable {@link #name}.
         *
         * @return value of instance variable {@link #name}
         */
        public String getName()
        {
            return this.name;
        }

        /**
         * Getter method for the instance variable {@link #description}.
         *
         * @return value of instance variable {@link #description}
         */
        public String getDescription()
        {
            return this.description;
        }

        /**
         * Getter method for the instance variable {@link #numbering}.
         *
         * @return value of instance variable {@link #numbering}
         */
        public String getNumbering()
        {
            return this.numbering;
        }
    }

    /**
     * Base class for all types of nodes.
     */
    public abstract class AbstractNode
    {
        /**
         * List of children for this node.
         */
        private final List<Report.AbstractNode> children = new ArrayList<>();

        /**
         * OID of this node.
         */
        private final String oid;

        /**
         * NUmber of this node.
         */
        private final String number;

        /**
         * Label for this node.
         */
        private final String label;

        /**
         * Show allways.
         */
        private final boolean showAllways;

        /**
         * Show sum.
         */
        private final boolean showSum;

        /**
         * Parent node of this node.
         */
        private final AbstractNode parent;

        /**
         * Position of this node.
         */
        private final Long position;

        /**
         * Level of this node.
         */
        private int level;


        /** The text only. */
        private boolean textOnly = false;


        /**
         * Getter method for the instance variable {@link #textOnly}.
         *
         * @return value of instance variable {@link #textOnly}
         */
        public boolean isTextOnly()
        {
            return this.textOnly;
        }

        /**
         * Setter method for instance variable {@link #total}.
         *
         * @param _textOnly the new text only
         */
        public void setTextOnly(final boolean _textOnly)
        {
            this.textOnly = _textOnly;
        }

        /**
         * @param _parent  paent of this node
         * @param _oid oid of the node
         * @param _number number of the node
         * @param _label label of the node
         * @param _showAllways must the node always be shown
         * @param _showSum must a sum be shown
         * @param _position the position of the node
         * @param _level level of this node
         */
        //CHECKSTYLE:OFF
        public AbstractNode(final AbstractNode _parent,
                    final String _oid,
                    final String _number,
                    final String _label,
                    final boolean _showAllways,
                    final boolean _showSum,
                    final Long _position,
                    final int _level)
        { //CHECKSTYLE:ON
            this.parent = _parent;
            this.oid = _oid;
            this.number = _number;
            this.label = _label;
            this.showAllways = _showAllways;
            this.showSum = _showSum;
            this.position = _position;
            this.level = _level;
        }

        /**
         * Getter method for the instance variable {@link #level}.
         *
         * @return value of instance variable {@link #level}
         */
        public int getLevel()
        {
            return this.level;
        }

        /**
         * Setter method for instance variable {@link #level}.
         *
         * @param _level value for instance variable {@link #level}
         */

        public void setLevel(final int _level)
        {
            this.level = _level;
        }


        /**
         * Getter method for instance variable {@link #position}.
         *
         * @return value of instance variable {@link #position}
         */
        public Long getPosition()
        {
            return this.position;
        }

        /**
         * Getter method for instance variable {@link #parent}.
         *
         * @return value of instance variable {@link #parent}
         */
        public AbstractNode getParent()
        {
            return this.parent;
        }

        /**
         * Getter method for instance variable {@link #children}.
         *
         * @return value of instance variable {@link #children}
         */
        public List<Report.AbstractNode> getChildren()
        {
            Collections.sort(this.children, new Comparator<Report.AbstractNode>() {

                @Override
                public int compare(final AbstractNode _node1,
                                   final AbstractNode _node2)
                {
                    return _node1.getPosition().compareTo(_node2.getPosition());
                }
            });
            return this.children;
        }

        /**
         * Add the children to this node instance.
         *
         * @param _parameter Parameter as passed by the eFaps API
         * @param _level levle for the child nodes
         * @throws EFapsException on error
         */
        protected abstract void addChildren(Parameter _parameter,
                                            final int _level)
            throws EFapsException;

        /**
         * @return the sum for this node.
         */
        protected abstract BigDecimal getSum();

        /**
         * Getter method for instance variable {@link #oid}.
         *
         * @return value of instance variable {@link #oid}
         */
        public String getOid()
        {
            return this.oid;
        }

        /**
         * Getter method for instance variable {@link #number}.
         *
         * @return value of instance variable {@link #number}
         */
        public String getNumber()
        {
            return this.number;
        }

        /**
         * Getter method for instance variable {@link #label}.
         *
         * @return value of instance variable {@link #label}
         */
        public String getLabel()
        {
            return this.label;
        }

        /**
         * Getter method for instance variable {@link #showAllways}.
         *
         * @return value of instance variable {@link #showAllways}
         */
        public boolean isShowAllways()
        {
            return this.showAllways;
        }

        /**
         * Getter method for instance variable {@link #showSum}.
         *
         * @return value of instance variable {@link #showSum}
         */
        public boolean isShowSum()
        {
            return this.showSum;
        }

        /**
         * @see java.lang.Object#toString()
         * @return String representation of this node
         */
        @Override
        public String toString()
        {
            final ToStringBuilder ret = new ToStringBuilder(this);
            ret.append("number", this.number).append("label", this.label).append("sum", getSum()).append("showSum",
                            this.showSum).append("showAllways", this.showAllways);
            for (final AbstractNode node : getChildren()) {
                ret.append("\n     ");
                ret.append("child", node.toString());
            }
            return ret.toString();
        }
    }

    /**
     *  Root Node.
     */
    public class RootNode
        extends Report.AbstractNode
    {

        /**
         * @param _parent  paent of this node
         * @param _oid oid of the node
         * @param _number number of the node
         * @param _label label of the node
         * @param _showAllways must the node always be shown
         * @param _showSum must a sum be shown
         * @param _position the position of the node
         */
        public RootNode(final AbstractNode _parent,
                        final String _oid,
                        final String _number,
                        final String _label,
                        final boolean _showAllways,
                        final boolean _showSum,
                        final Long _position)
        {
            super(_parent, _oid, _number, _label, _showAllways, _showSum, _position, 0);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected void addChildren(final Parameter _parameter,
                                   final int _level)
            throws EFapsException
        {
            final Instance rootInst = Instance.get(getOid());

            final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.ReportNodeChildAbstract);
            queryBldr.addWhereAttrEqValue(CIAccounting.ReportNodeChildAbstract.ParentLinkAbstract, rootInst.getId());
            final MultiPrintQuery print = queryBldr.getPrint();
            print.addAttribute(CIAccounting.ReportNodeChildAbstract.OID, CIAccounting.ReportNodeChildAbstract.Number,
                            CIAccounting.ReportNodeChildAbstract.Label,
                            CIAccounting.ReportNodeChildAbstract.ShowAllways,
                            CIAccounting.ReportNodeChildAbstract.ShowSum, CIAccounting.ReportNodeChildAbstract.Position,
                            CIAccounting.ReportNodeAccount.AccountLink);
            print.execute();
            while (print.next()) {
                final String oidTmp = print.<String>getAttribute(CIAccounting.ReportNodeChildAbstract.OID);
                final String number = print.<String>getAttribute(CIAccounting.ReportNodeChildAbstract.Number);
                final String label = print.<String>getAttribute(CIAccounting.ReportNodeChildAbstract.Label);
                final Boolean showAllways = print
                                .<Boolean>getAttribute(CIAccounting.ReportNodeChildAbstract.ShowAllways);
                final Boolean showSum = print.<Boolean>getAttribute(CIAccounting.ReportNodeChildAbstract.ShowAllways);
                final Long position = print.<Long>getAttribute(CIAccounting.ReportNodeChildAbstract.Position);
                final Long accountLink = print.<Long> getAttribute(CIAccounting.ReportNodeAccount.AccountLink);
                final AbstractNode child;
                if (Instance.get(oidTmp).getType().isKindOf(CIAccounting.ReportNodeTree.getType())) {
                    child = new TreeNode(this, oidTmp, number, label, showAllways, showSum, position, _level);
                } else {
                    child = new AccountNode(this, oidTmp, number, label, showAllways, showSum, position, _level,
                                    accountLink);
                }
                getChildren().add(child);
            }
            for (final AbstractNode child : getChildren()) {
                child.addChildren(_parameter, _level + 1);
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public BigDecimal getSum()
        {
            BigDecimal temp = BigDecimal.ZERO;
            for (final AbstractNode child : getChildren()) {
                temp = temp.add(child.getSum());
            }
            return temp;
        }
    }

    /**
     * Tree Node.
     */
    public class TreeNode
        extends Report.AbstractNode
    {

        /**
         * @param _parent  parent of this node
         * @param _oid oid of the node
         * @param _number number of the node
         * @param _label label of the node
         * @param _showAllways must the node always be shown
         * @param _showSum must a sum be shown
         * @param _position the position of the node
         * @param _level level of this node
         */
        //CHECKSTYLE:OFF
        public TreeNode(final AbstractNode _parent,
                        final String _oid,
                        final String _number,
                        final String _label,
                        final boolean _showAllways,
                        final boolean _showSum,
                        final Long _position,
                        final int _level)
        {
        //CHECKSTYLE:ON
            super(_parent, _oid, _number, _label, _showAllways, _showSum, _position, _level);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected void addChildren(final Parameter _parameter,
                                   final int _level)
            throws EFapsException
        {
            final Instance treeInst = Instance.get(getOid());

            final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.ReportNodeChildAbstract);
            queryBldr.addWhereAttrEqValue(CIAccounting.ReportNodeChildAbstract.ParentLinkAbstract, treeInst.getId());
            final MultiPrintQuery print = queryBldr.getPrint();
            print.addAttribute(CIAccounting.ReportNodeChildAbstract.OID, CIAccounting.ReportNodeChildAbstract.Number,
                            CIAccounting.ReportNodeChildAbstract.Label,
                            CIAccounting.ReportNodeChildAbstract.ShowAllways,
                            CIAccounting.ReportNodeChildAbstract.ShowSum,
                            CIAccounting.ReportNodeChildAbstract.Position,
                            CIAccounting.ReportNodeAccount.AccountLink);
            print.execute();
            while (print.next()) {
                final String oidTmp = print.<String> getAttribute(CIAccounting.ReportNodeChildAbstract.OID);
                final String number = print.<String> getAttribute(CIAccounting.ReportNodeChildAbstract.Number);
                final String label = print.<String> getAttribute(CIAccounting.ReportNodeChildAbstract.Label);
                final Boolean showAllways = print.<Boolean> getAttribute(
                                CIAccounting.ReportNodeChildAbstract.ShowAllways);
                final Boolean showSum = print.<Boolean> getAttribute(CIAccounting.ReportNodeChildAbstract.ShowAllways);
                final Long position = print.<Long> getAttribute(CIAccounting.ReportNodeChildAbstract.Position);
                final Long accountLink = print.<Long> getAttribute(CIAccounting.ReportNodeAccount.AccountLink);
                final AbstractNode child;
                if (print.getCurrentInstance().getType().getUUID().equals(CIAccounting.ReportNodeAccount.uuid)) {
                    child = new AccountNode(this, oidTmp, number, label, showAllways, showSum, position, _level,
                                    accountLink);
                } else {
                    child = new TreeNode(this, oidTmp, number, label, showAllways, showSum, position, _level);
                }
                getChildren().add(child);
            }

            for (final AbstractNode child : getChildren()) {
                child.addChildren(_parameter, _level + 1);
            }
        }

        @Override
        public BigDecimal getSum()
        {
            BigDecimal temp = BigDecimal.ZERO;
            for (final AbstractNode child : getChildren()) {
                temp = temp.add(child.getSum());
            }
            return temp;
        }

    }

    /**
     * Account Node.
     */
    public class AccountNode
        extends Report.AbstractNode
    {

        /**
         * Id of the account.
         */
        private final long accountId;

        /**
         * Sum for his account.
         */
        private BigDecimal sum = BigDecimal.ZERO;

        /**
         * @param _parent       parent of this node
         * @param _oid          oid of the node
         * @param _number       number of the node
         * @param _label        label of the node
         * @param _showAllways  must the node always be shown
         * @param _showSum      must a sum be shown
         * @param _position     the position of the node
         * @param _level        level of this node
         * @param _accountId    id of the account
         */
        //CHECKSTYLE:OFF
        public AccountNode(final AbstractNode _parent,
                           final String _oid,
                           final String _number,
                           final String _label,
                           final boolean _showAllways,
                           final boolean _showSum,
                           final Long _position,
                           final int _level,
                           final long _accountId)
        {
        //CHECKSTYLE:ON
            super(_parent, _oid, _number, _label, _showAllways, _showSum, _position, _level);
            this.accountId = _accountId;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected void addChildren(final Parameter _parameter,
                                   final int _level)
            throws EFapsException
        {
            final boolean active = Boolean.parseBoolean(_parameter.getParameterValue("filterActive"));
            final String currency = _parameter.getParameterValue("currency");

            final Instance curBase = Currency.getBaseCurrency();

            final QueryBuilder transQueryBldr = new QueryBuilder(CIAccounting.TransactionAbstract);
            transQueryBldr.addWhereAttrLessValue(CIAccounting.TransactionAbstract.Date,
                            Report_Base.this.dateTo.plusDays(1));
            transQueryBldr.addWhereAttrGreaterValue(CIAccounting.TransactionAbstract.Date,
                            Report_Base.this.dateFrom.minusSeconds(1));
            final AttributeQuery attrQuery = transQueryBldr.getAttributeQuery(CIAccounting.TransactionAbstract.ID);

            final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.TransactionPositionAbstract);
            queryBldr.addWhereAttrEqValue(CIAccounting.TransactionPositionAbstract.AccountLink, this.accountId);
            queryBldr.addWhereAttrInQuery(CIAccounting.TransactionPositionAbstract.TransactionLink, attrQuery);
            final MultiPrintQuery print = queryBldr.getPrint();
            print.addAttribute(CIAccounting.TransactionPositionAbstract.Amount,
                                CIAccounting.TransactionPositionAbstract.RateAmount);
            final SelectBuilder selRateCur = new SelectBuilder()
                                .linkto(CIAccounting.TransactionPositionAbstract.RateCurrencyLink).instance();
            final SelectBuilder selTxnDate = new SelectBuilder()
                                .linkto(CIAccounting.TransactionPositionAbstract.TransactionLink)
                                .attribute(CIAccounting.TransactionAbstract.Date);
            print.addSelect(selRateCur, selTxnDate);
            print.execute();
            while (print.next()) {
                final CurrencyInst curInstTxnPos = new CurrencyInst(print.<Instance>getSelect(selRateCur));
                final DateTime date = print.<DateTime>getSelect(selTxnDate);
                BigDecimal amount = BigDecimal.ZERO;
                if (active) {
                    final CurrencyInst curInst = new CurrencyInst(Instance.get(CIERP.Currency.getType(), currency));
                    if (curInstTxnPos.getInstance().getId() != curInst.getInstance().getId()) {
                        if (curInstTxnPos.getInstance().getId() != curBase.getId()) {
                            RateInfo rateInfo = getCurrency(_parameter).evaluateRateInfo(_parameter, date, curInstTxnPos
                                            .getInstance());
                            BigDecimal rate = RateInfo.getRate(_parameter, rateInfo, Report.class.getName());
                            final BigDecimal amountTmp = print.<BigDecimal>getAttribute(
                                            CIAccounting.TransactionPositionAbstract.RateAmount).divide(rate,
                                                            BigDecimal.ROUND_HALF_UP);
                            rateInfo = getCurrency(_parameter).evaluateRateInfo(_parameter, date, curInst
                                            .getInstance());
                            rate = RateInfo.getRate(_parameter, rateInfo, Report.class.getName());
                            amount = amountTmp.divide(rate, BigDecimal.ROUND_HALF_UP);
                        } else {
                            final RateInfo rateInfo = getCurrency(_parameter).evaluateRateInfo(_parameter, date, curInst
                                            .getInstance());
                            final BigDecimal rate = RateInfo.getRate(_parameter, rateInfo, Report.class.getName());
                            amount = print.<BigDecimal>getAttribute(CIAccounting.TransactionPositionAbstract.RateAmount)
                                            .divide(rate, BigDecimal.ROUND_HALF_UP);
                        }
                    } else {
                        amount = print.<BigDecimal>getAttribute(CIAccounting.TransactionPositionAbstract.RateAmount);
                    }
                } else {
                    amount = print.<BigDecimal>getAttribute(CIAccounting.TransactionPositionAbstract.Amount);
                }

                this.sum = this.sum.add(amount);
            }
        }

        @Override
        public BigDecimal getSum()
        {
            return this.sum;
        }
    }

    public class TotalNode
        extends AbstractNode
    {

        private final BigDecimal sum;

        /**
         * @param _label    the label
         * @param _sum      the sum
         */
        public TotalNode(final String _label,
                         final BigDecimal _sum,
                         final int _level)
        {
            super(null, null, null, _label, false, false, null, _level);
            this.sum = _sum;
        }

        @Override
        protected void addChildren(final Parameter _parameter,
                                   final int _level)
            throws EFapsException
        {
            // Nothing must be done
        }

        @Override
        protected BigDecimal getSum()
        {
            return this.sum;
        }
    }
}
