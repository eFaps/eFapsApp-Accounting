/*
 * Copyright 2003 - 2010 The eFaps Team
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
 * Revision:        $Rev$
 * Last Changed:    $Date$
 * Last Changed By: $Author$
 */

package org.efaps.esjp.accounting.report;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import net.sf.dynamicreports.jasper.builder.JasperReportBuilder;
import net.sf.dynamicreports.report.builder.DynamicReports;
import net.sf.dynamicreports.report.builder.column.TextColumnBuilder;
import net.sf.dynamicreports.report.builder.style.StyleBuilder;
import net.sf.dynamicreports.report.builder.style.Styles;
import net.sf.dynamicreports.report.constant.HorizontalAlignment;
import net.sf.dynamicreports.report.constant.VerticalAlignment;
import net.sf.dynamicreports.report.exception.DRException;
import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.design.JasperDesign;

import org.apache.commons.lang.builder.ToStringBuilder;
import org.efaps.admin.datamodel.ui.FieldValue;
import org.efaps.admin.datamodel.ui.UIInterface;
import org.efaps.admin.dbproperty.DBProperties;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Parameter.ParameterValues;
import org.efaps.admin.event.Return;
import org.efaps.admin.event.Return.ReturnValues;
import org.efaps.admin.program.esjp.EFapsRevision;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.admin.program.jasper.JasperUtil;
import org.efaps.ci.CIAdminProgram;
import org.efaps.db.AttributeQuery;
import org.efaps.db.Insert;
import org.efaps.db.Instance;
import org.efaps.db.InstanceQuery;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.PrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.common.jasperreport.StandartReport;
import org.efaps.esjp.common.jasperreport.StandartReport_Base;
import org.efaps.util.EFapsException;
import org.joda.time.DateTime;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 * @version $Id$
 */
@EFapsUUID("0f3596f4-bcfa-4541-8d6b-551562a9b223")
@EFapsRevision("$Rev$")
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

    /**
     * Method renders a drop down field containing the mime types.
     *
     * @param _parameter Parameter as passed from the eFaps API
     * @return html snipplet
     */
    public Return getMimeTypeFieldValueUI(final Parameter _parameter)
    {
        final FieldValue fieldValue = (FieldValue) _parameter.get(ParameterValues.UIOBJECT);
        final Return ret = new Return();
        final StringBuilder html = new StringBuilder();
        html.append("<select name=\"").append(fieldValue.getField().getName()).append("\" ").append(
                        UIInterface.EFAPSTMPTAG).append(" size=\"1\">");

        final Map<String, String> values = new TreeMap<String, String>();
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
        final PrintQuery print = new PrintQuery(_parameter.getInstance());
        final SelectBuilder sel = new SelectBuilder().linkto(CIAccounting.ReportAbstract.PeriodeLink)
            .attribute(CIAccounting.Periode.FromDate);
        print.addSelect(sel);
        DateTime date;
        if (print.execute()) {
            date = print.getSelect(sel);
        } else {
            date = new DateTime();
        }

        final Return ret = new Return();
        ret.put(ReturnValues.VALUES, date);
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
        final PrintQuery print = new PrintQuery(_parameter.getInstance());
        final SelectBuilder sel = new SelectBuilder().linkto(CIAccounting.ReportAbstract.PeriodeLink)
            .attribute(CIAccounting.Periode.ToDate);
        print.addSelect(sel);
        DateTime date;
        if (print.execute()) {
            date = print.getSelect(sel);
        } else {
            date = new DateTime();
        }

        final Return ret = new Return();
        ret.put(ReturnValues.VALUES, date);
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

        final ReportTree dataTree = new ReportTree(_parameter.getInstance());
        dataTree.addChildren();
        for (final AbstractNode node : dataTree.getRootNodes()) {
            node.getSum();
        }

        final List<List<AbstractNode>> table = dataTree.getTable();
        try {

            final JasperReportBuilder jrb = DynamicReports.report();
            addTitle(_parameter, jrb, dataTree);
            addPageHeader(_parameter, jrb, dataTree);
            addFooter(_parameter, jrb, dataTree);
            if (print) {
                jrb.highlightDetailEvenRows();
            } else {
                jrb.setIgnorePagination(true);
            }

            final StyleBuilder numberStyle = getNumberStyle();

            final StyleBuilder textStyle = getTextStyle();

            for (Integer y = 0; y < table.size(); y++) {

                final TextColumnBuilder<String> textColumn = DynamicReports.col.column("column_" + y,
                                DynamicReports.type.stringType());
                textColumn.setStyle(textStyle).setWidth(80);
                jrb.addColumn(textColumn);

                final TextColumnBuilder<BigDecimal> numberColumn = DynamicReports.col.column("sums_" + y,
                                DynamicReports.type.bigDecimalType());
                numberColumn.setStyle(numberStyle).setWidth(20);
                jrb.addColumn(numberColumn);
            }

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
                jrb.setTemplateDesign(design);
            }

            final JRDataSource ds = new AccountingDataSource(table);
            setFileName(dataTree.getName());
            jrb.setDataSource(ds);

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



    protected void addFooter(final Parameter _parameter,
                           final JasperReportBuilder _jrb,
                           final ReportTree _dataTree)
    {
        // for implementation purpose
    }

    protected void addTitle(final Parameter _parameter,
                            final JasperReportBuilder _jrb,
                            final ReportTree _dataTree)
    {
        // for implementation purpose
    }


    protected void addPageHeader(final Parameter _parameter,
                                 final JasperReportBuilder _jrb,
                                 final ReportTree _dataTree)
    {
        _jrb.addPageHeader(DynamicReports.cmp.verticalList(
                        DynamicReports.cmp.text(_dataTree.getName()),
                        DynamicReports.cmp.text(_dataTree.getDescription())));
    }

    protected StyleBuilder getNumberStyle()
    {
        return DynamicReports.stl.style()
                        .setFont(Styles.font().setFontSize(9))
                        .setAlignment(HorizontalAlignment.RIGHT, VerticalAlignment.MIDDLE)
                        .setPadding(DynamicReports.stl.padding().setRight(5));
    }

    protected StyleBuilder getTextStyle()
    {
        return DynamicReports.stl.style()
                        .setFont(Styles.font().setFontSize(9))
                        .setAlignment(HorizontalAlignment.LEFT, VerticalAlignment.MIDDLE);
    }


    protected String getTotalLabel(final AbstractNode _parent)
    {
        return "TOTAL " +  _parent.getLabel();
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
        private final List<AbstractNode> rootNodes = new ArrayList<AbstractNode>();

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
         * @throws EFapsException on error.
         */
        public void addChildren()
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
                root.addChildren(1);
            }
            Collections.sort(this.rootNodes, new Comparator<AbstractNode>() {

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
            final List<List<AbstractNode>> ret = new ArrayList<List<AbstractNode>>();
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
            final List<AbstractNode> ret = new ArrayList<AbstractNode>();
            boolean added = false;
            if (_parent.isShowAllways()
                            || (_parent.isShowSum() && _parent.getSum().compareTo(BigDecimal.ZERO) != 0)) {
                ret.add(_parent);
                added = true;
            }
            for (final AbstractNode child : _parent.getChildren()) {
                ret.addAll(flatten(child));
            }
            if (added && !_parent.getChildren().isEmpty()) {
                ret.add(new TotalNode(getTotalLabel(_parent), _parent.getSum(), _parent.getLevel()));
                _parent.setTextOnly(true);
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
    public static abstract class AbstractNode
    {
        /**
         * List of children for this node.
         */
        private final List<Report.AbstractNode> children = new ArrayList<Report.AbstractNode>();

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
        private final int level;

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
         * @param _total value for instance variable {@link #total}
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
         * @param _level levle for the child nodes
         * @throws EFapsException on error
         */
        protected abstract void addChildren(final int _level)
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
        protected void addChildren(final int _level)
            throws EFapsException
        {
            final Instance rootInst = Instance.get(getOid());

            final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.ReportNodeTree);
            queryBldr.addWhereAttrEqValue(CIAccounting.ReportNodeTree.ParentLink, rootInst.getId());
            final MultiPrintQuery print = queryBldr.getPrint();
            print.addAttribute(CIAccounting.ReportNodeTree.OID, CIAccounting.ReportNodeTree.Number,
                            CIAccounting.ReportNodeTree.Label, CIAccounting.ReportNodeTree.ShowAllways,
                            CIAccounting.ReportNodeTree.ShowSum, CIAccounting.ReportNodeTree.Position);
            print.execute();
            while (print.next()) {
                final String oidTmp = print.<String> getAttribute(CIAccounting.ReportNodeTree.OID);
                final String number = print.<String> getAttribute(CIAccounting.ReportNodeTree.Number);
                final String label = print.<String> getAttribute(CIAccounting.ReportNodeTree.Label);
                final Boolean showAllways = print.<Boolean> getAttribute(CIAccounting.ReportNodeTree.ShowAllways);
                final Boolean showSum = print.<Boolean> getAttribute(CIAccounting.ReportNodeTree.ShowAllways);
                final Long position = print.<Long> getAttribute(CIAccounting.ReportNodeTree.Position);
                final AbstractNode child = new TreeNode(this, oidTmp, number, label, showAllways, showSum, position,
                                _level);
                getChildren().add(child);
            }
            for (final AbstractNode child : getChildren()) {
                child.addChildren(_level + 1);
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
        protected void addChildren(final int _level)
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
                child.addChildren(_level + 1);
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
        protected void addChildren(final int _level)
            throws EFapsException
        {
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
            print.addAttribute(CIAccounting.TransactionPositionAbstract.Amount);
            print.execute();
            while (print.next()) {
                final BigDecimal amount = print.<BigDecimal>getAttribute(
                                CIAccounting.TransactionPositionAbstract.Amount);
                this.sum = this.sum.add(amount);
            }
        }

        @Override
        public BigDecimal getSum()
        {
            return this.sum;
        }
    }

    public static class TotalNode
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
        protected void addChildren(final int _level)
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
