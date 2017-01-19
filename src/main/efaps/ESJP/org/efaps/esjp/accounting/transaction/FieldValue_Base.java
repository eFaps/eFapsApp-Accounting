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


package org.efaps.esjp.accounting.transaction;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.TreeMap;
import java.util.UUID;

import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.efaps.admin.common.SystemConfiguration;
import org.efaps.admin.datamodel.Classification;
import org.efaps.admin.datamodel.Dimension;
import org.efaps.admin.datamodel.Dimension.UoM;
import org.efaps.admin.datamodel.Status;
import org.efaps.admin.datamodel.Type;
import org.efaps.admin.datamodel.ui.IUIValue;
import org.efaps.admin.datamodel.ui.UIValue;
import org.efaps.admin.dbproperty.DBProperties;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Parameter.ParameterValues;
import org.efaps.admin.event.Return;
import org.efaps.admin.event.Return.ReturnValues;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.admin.ui.AbstractUserInterfaceObject.TargetMode;
import org.efaps.admin.ui.field.Field.Display;
import org.efaps.api.ui.IUserInterface;
import org.efaps.ci.CIAttribute;
import org.efaps.ci.CIType;
import org.efaps.db.CachedPrintQuery;
import org.efaps.db.Context;
import org.efaps.db.Instance;
import org.efaps.db.InstanceQuery;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.PrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.esjp.accounting.Case;
import org.efaps.esjp.accounting.Label;
import org.efaps.esjp.accounting.Period;
import org.efaps.esjp.accounting.report.DocumentDetailsReport;
import org.efaps.esjp.accounting.util.Accounting;
import org.efaps.esjp.accounting.util.Accounting.LabelDefinition;
import org.efaps.esjp.accounting.util.Accounting.SummarizeConfig;
import org.efaps.esjp.accounting.util.Accounting.SummarizeDefinition;
import org.efaps.esjp.accounting.util.AccountingSettings;
import org.efaps.esjp.admin.datamodel.RangesValue_Base.RangeValueOption;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.ci.CIContacts;
import org.efaps.esjp.ci.CIERP;
import org.efaps.esjp.ci.CIFormAccounting;
import org.efaps.esjp.ci.CIProducts;
import org.efaps.esjp.ci.CISales;
import org.efaps.esjp.common.jasperreport.AbstractDynamicReport;
import org.efaps.esjp.common.uiform.Field;
import org.efaps.esjp.common.uiform.Field_Base.DropDownPosition;
import org.efaps.esjp.common.util.InterfaceUtils;
import org.efaps.esjp.common.util.InterfaceUtils_Base.DojoLibs;
import org.efaps.esjp.erp.CurrencyInst;
import org.efaps.esjp.erp.NumberFormatter;
import org.efaps.esjp.erp.RateInfo;
import org.efaps.esjp.erp.util.ERP.DocTypeConfiguration;
import org.efaps.esjp.sales.Swap;
import org.efaps.esjp.sales.Swap_Base.SwapInfo;
import org.efaps.esjp.sales.document.AbstractDocumentTax;
import org.efaps.esjp.sales.document.AbstractDocumentTax_Base.DocTaxInfo;
import org.efaps.esjp.ui.html.Table;
import org.efaps.esjp.ui.html.Table_Base.Row;
import org.efaps.util.EFapsException;
import org.joda.time.DateTime;


/**
 * TODO comment!
 *
 * @author The eFaps Team
 */
@EFapsUUID("5075c0e0-e95a-418c-864b-3a90c1dac404")
@EFapsApplication("eFapsApp-Accounting")
public abstract class FieldValue_Base
    extends Transaction
{
    /**
     * Key for the map to be stored in the request.
     */
    protected static final String SALESACC_REQKEY = FieldValue.class.getName() + ".SalesAccountRequestKey";

    /**
     * Key for the map to be stored in the request.
     */
    protected static final String CASE_REQKEY = FieldValue.class.getName() + ".CaseRequestKey";


    /**
     * @param _parameter Parameter as passed from the eFaps API
     * @return values for dropdown
     * @throws EFapsException on error
     */
    public Return getSubJournalFieldValue(final Parameter _parameter)
        throws EFapsException
    {
        final Field field = new Field() {

            @Override
            protected void add2QueryBuilder4List(final Parameter _parameter,
                                                 final QueryBuilder _queryBldr)
                throws EFapsException
            {
                final Instance periodInst = new Period().evaluateCurrentPeriod(_parameter);
                _queryBldr.addWhereAttrEqValue(CIAccounting.ReportSubJournal.PeriodLink, periodInst.getId());
            }
        };
        return field.getOptionListFieldValue(_parameter);
    }

    /**
     * Gets the document link field value.
     *
     * @param _parameter Parameter as passed from the eFaps API
     * @return values for dropdown
     * @throws EFapsException on error
     */
    public Return getDocLinkFieldValue(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final IUIValue uiValue = (IUIValue) _parameter.get(ParameterValues.UIOBJECT);
        final List<DropDownPosition> values = new ArrayList<>();
        if (TargetMode.EDIT.equals(_parameter.get(ParameterValues.ACCESSMODE))
                        && org.efaps.admin.ui.field.Field.Display.EDITABLE.equals(uiValue.getDisplay())) {
            final Instance transInst;
            final Instance docInst;
            if (_parameter.getInstance() != null && _parameter.getInstance().isValid()) {
                final PrintQuery print = new PrintQuery(_parameter.getInstance());
                final SelectBuilder selTransInst = SelectBuilder.get().linkto(
                                CIAccounting.TransactionPositionAbstract.TransactionLink).instance();
                final SelectBuilder selDocInst = SelectBuilder.get().linkfrom(
                                CIAccounting.TransactionPosition2ERPDocument.FromLinkAbstract).linkto(
                                                CIAccounting.TransactionPosition2ERPDocument.ToLinkAbstract).instance();
                print.addSelect(selTransInst, selDocInst);
                print.execute();
                transInst = print.getSelect(selTransInst);
                docInst = print.getSelect(selDocInst);
            } else {
                transInst = _parameter.getCallInstance();
                docInst = null;
            }
            final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.Transaction2ERPDocument);
            queryBldr.addWhereAttrEqValue(CIAccounting.Transaction2ERPDocument.FromLink, transInst);
            queryBldr.addOrderByAttributeAsc(CIAccounting.Transaction2ERPDocument.Position);
            final MultiPrintQuery multi = queryBldr.getPrint();
            final SelectBuilder selCurDocInst = SelectBuilder.get().linkto(
                            CIAccounting.Transaction2ERPDocument.ToLinkAbstract).instance();
            multi.addSelect(selCurDocInst);
            multi.addAttribute(CIAccounting.Transaction2ERPDocument.Position);
            multi.setEnforceSorted(true);
            multi.execute();
            while (multi.next()) {
                final Instance curDocInst = multi.getSelect(selCurDocInst);
                final DropDownPosition drpD = new DropDownPosition(curDocInst.getOid(), String.valueOf(multi
                                .<Object>getAttribute(CIAccounting.Transaction2ERPDocument.Position)) + ".");
                drpD.setSelected(curDocInst.equals(docInst));
                values.add(drpD);
            }
        } else if (org.efaps.admin.ui.field.Field.Display.EDITABLE.equals(uiValue.getDisplay())) {
            final List<Instance> insts = getSelectedDocInst(_parameter);

            int i = 1;
            for (final Instance inst : insts) {
                values.add(new DropDownPosition(inst.getOid(), i + "."));
                i++;
            }
        }
        if (!values.isEmpty()) {
            values.add(0, new DropDownPosition("", "-"));
        }
        ret.put(ReturnValues.VALUES, values);
        return ret;
    }

    /**
     * @param _parameter Parameter as passed from the eFaps API
     * @return values for dropdown
     * @throws EFapsException on error
     */
    public Return getPurchaseRecordFieldValue(final Parameter _parameter)
        throws EFapsException
    {
        final Field field = new Field()
        {

            @Override
            protected void add2QueryBuilder4List(final Parameter _parameter,
                                                 final QueryBuilder _queryBldr)
                throws EFapsException
            {
                final DateTime[] dates = getDateMaxMin(_parameter);
                _queryBldr.addWhereAttrGreaterValue(CIAccounting.PurchaseRecord.Date, dates[0].minusDays(1));
            }

            @Override
            protected void updatePositionList(final Parameter _parameter,
                                              final List<DropDownPosition> _values)
                throws EFapsException
            {
                super.updatePositionList(_parameter, _values);
                boolean select = true;
                final List<Instance> docInsts = getSelectedDocInst(_parameter);
                if (!docInsts.isEmpty()) {
                    final QueryBuilder dtQueryBldr = new QueryBuilder(CISales.Document2DocumentType);
                    dtQueryBldr.addWhereAttrEqValue(CISales.Document2DocumentType.DocumentLink, docInsts.toArray());
                    final MultiPrintQuery multi = dtQueryBldr.getPrint();
                    final SelectBuilder docTypeSel = new SelectBuilder()
                                    .linkto(CISales.Document2DocumentType.DocumentTypeLink)
                                    .attribute(CIERP.DocumentType.Configuration);
                    multi.addSelect(docTypeSel);
                    multi.executeWithoutAccessCheck();
                    while (multi.next()) {
                        final List<DocTypeConfiguration> configs = multi.getSelect(docTypeSel);
                        if (configs == null
                                       || configs != null && !configs.contains(DocTypeConfiguration.PURCHASERECORD)) {
                            select = false;
                            break;
                        }
                    }
                }
                if (select) {
                    final DateTime date = new DateTime();
                    final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.PurchaseRecord);
                    queryBldr.addWhereAttrLessValue(CIAccounting.PurchaseRecord.Date, date.plusDays(1));
                    queryBldr.addWhereAttrGreaterValue(CIAccounting.PurchaseRecord.DueDate, date.minusDays(1));
                    queryBldr.addOrderByAttributeAsc(CIAccounting.PurchaseRecord.Date);
                    final InstanceQuery query = queryBldr.getQuery();
                    boolean selected = false;
                    for (final Instance inst : query.executeWithoutAccessCheck()) {
                        for (final DropDownPosition dd : _values) {
                            if (inst.getOid().equals(dd.getValue())) {
                                dd.setSelected(true);
                                selected = true;
                                break;
                            }
                        }
                        if (selected) {
                            break;
                        }
                    }
                }
            }
        };
        return field.getOptionListFieldValue(_parameter);
    }

    /**
     * Called from field for the case.
     *
     * @param _parameter Parameter as passed from the eFaps API
     * @return new Return
     * @throws EFapsException on error
     */
    public Return getCaseFieldValue(final Parameter _parameter)
        throws EFapsException
    {
        Return ret = new Return();
        final String type = getProperty(_parameter, "Type");
        if (type != null) {
            final Instance periodInstance = new Period().evaluateCurrentPeriod(_parameter);
            _parameter.put(ParameterValues.INSTANCE, periodInstance);
            final org.efaps.esjp.common.uiform.Field field = new org.efaps.esjp.common.uiform.Field()
            {

                @Override
                protected void updatePositionList(final Parameter _parameter,
                                                  final List<DropDownPosition> _values)
                    throws EFapsException
                {
                    final String trueStr = DBProperties.getProperty("Accounting_CaseAbstract/IsCross.true");
                    final String falseStr = DBProperties.getProperty("Accounting_CaseAbstract/IsCross.false");

                    final String cross = DBProperties
                                    .getProperty("org.efaps.esjp.accounting.transaction.FieldValue.Cross");
                    final String net = DBProperties.getProperty("org.efaps.esjp.accounting.transaction.FieldValue.Net");
                    boolean first = true;
                    for (final DropDownPosition pos : _values) {
                        if (first) {
                            // store the selected
                            Context.getThreadContext().setRequestAttribute(FieldValue_Base.CASE_REQKEY, pos.getValue());
                            first = false;
                        }
                        final String strTmp = pos.getOption().toString();
                        if (StringUtils.endsWith(strTmp, trueStr)) {
                            pos.setOption(StringUtils.substringBeforeLast(strTmp, trueStr) + cross);
                        } else if (StringUtils.endsWith(strTmp, falseStr)) {
                            pos.setOption(StringUtils.substringBeforeLast(strTmp, falseStr) + net);
                        }
                    }
                };
            };
            ret = field.getOptionListFieldValue(_parameter);
        }
        return ret;
    }

    /**
     * @param _parameter Parameter as passed from the eFaps API
     * @return  default value for the currency field
     * @throws EFapsException on error
     */
    public Return getCurrencyFieldValue(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final IUIValue uiValue = (IUIValue) _parameter.get(ParameterValues.UIOBJECT);
        if (uiValue instanceof UIValue) {
            final Period period = new Period();
            final Instance inst = period.evaluateCurrentPeriod(_parameter);
            final CurrencyInst baseCurInstObj = period.getCurrency(inst);

            @SuppressWarnings("unchecked")
            final List<RangeValueOption> values = (List<RangeValueOption>) ((UIValue) uiValue).getUIProvider()
                            .getValue((UIValue) uiValue);
            for (final RangeValueOption option : values) {
                option.setSelected(option.getValue().equals(baseCurInstObj.getInstance().getId()));
            }
            ret.put(ReturnValues.VALUES, values);
        }
        return ret;
    }

    /**
     * Return type of the value the fields.
     *
     * @param _parameter Parameter as passed from the eFaps API.
     * @return ret Return.
     * @throws EFapsException on error.
     */
    public Return getTypeFieldValue(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final String type = getProperty(_parameter, "Type");
        ret.put(ReturnValues.VALUES, Type.get(type).getId());
        return ret;
    }


    /**
     * Renders a dropdown containing the Types for external documents.
     * @param _parameter Parameter as passed from the eFasp API
     * @return html snipplet
     * @throws EFapsException on error
     */
    public Return getTypeLinkFieldValue(final Parameter _parameter)
        throws EFapsException
    {
        final QueryBuilder queryBldr = new QueryBuilder(CIERP.DocumentType);
        final MultiPrintQuery print = queryBldr.getPrint();
        print.addAttribute(CIERP.DocumentType.ID, CIERP.DocumentType.Name, CIERP.DocumentType.Description);
        print.execute();
        final Map<String, Long> values = new TreeMap<>();
        while (print.next()) {
            values.put(print.<String>getAttribute(CIERP.DocumentType.Name)
                            + " - " + print.<String>getAttribute(CIERP.DocumentType.Description),
                       print.<Long>getAttribute(CIERP.DocumentType.ID));
        }

        final StringBuilder html = new StringBuilder();
        final IUIValue fieldValue = (IUIValue) _parameter.get(ParameterValues.UIOBJECT);

        html.append("<select name=\"").append(fieldValue.getField().getName()).append("\" ")
                        .append(IUserInterface.EFAPSTMPTAG).append(" size=\"1\">");
        for (final Entry<String, Long> entry : values.entrySet()) {
            html.append("<option value=\"").append(entry.getValue());
            html.append("\">").append(entry.getKey()).append("</option>");
        }
        html.append("</select>");
        final Return ret = new Return();
        ret.put(ReturnValues.SNIPLETT, html.toString());
        return ret;
    }

    /**
     * Set the currency for the drop down.
     * @param _parameter    Parameter as passed form the eFaps API
     * @return  html snipplet
     * @throws EFapsException on errro
     */
    public Return getCurrencyOptionListFieldValue(final Parameter _parameter)
        throws EFapsException
    {
        Instance inst = _parameter.getCallInstance();
        if (!inst.getType().getUUID().equals(CIAccounting.Period.uuid)) {
            inst = new Period().evaluateCurrentPeriod(_parameter);
        }
        final Instance baseCur = new Period().getCurrency(inst).getInstance();
        final Field field = new Field()
        {
            @Override
            public DropDownPosition getDropDownPosition(final Parameter _parameter,
                                                        final Object _value,
                                                        final Object _option)
                throws EFapsException
            {
                final DropDownPosition position = super.getDropDownPosition(_parameter, _value, _option);
                if (baseCur.isValid()) {
                    // check if the value is long, and assume that it is the id
                    if (position.getValue() instanceof Long) {
                        position.setSelected(baseCur.getId() == (Long) position.getValue());
                    } else {
                        position.setSelected(baseCur.getOid().equals(String.valueOf(position.getValue())));
                    }
                }
                return position;
            }
        };
        return field.getOptionListFieldValue(_parameter);
    }

    /**
     * @param _parameter Parameter as passed from the eFaps API
     * @return value for field
     * @throws EFapsException on error
     */
    public Return getPettyCashReceiptClazzNameFieldValue(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final String selected = Context.getThreadContext().getParameter("selectedRow");

        if (selected != null) {
            final SelectBuilder selClazzName = new SelectBuilder().clazz(CISales.PettyCashReceipt_Class)
                            .attribute(CISales.PettyCashReceipt_Class.Name);
            final Instance docInst = Instance.get(selected);
            final PrintQuery print = new PrintQuery(docInst);
            print.addSelect(selClazzName);
            print.execute();

            final String name = print.<String>getSelect(selClazzName);
            final StringBuilder val = new StringBuilder();
            val.append(name);
            ret.put(ReturnValues.VALUES, val.toString());
        }
        return ret;
    }

    /**
     * Gets the document4 swap field value.
     *
     * @param _parameter the _parameter
     * @return the document4 swap field value
     * @throws EFapsException the e faps exception
     */
    public Return getDocument4SwapFieldValue(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final StringBuilder html = getDocumentFieldSnipplet(_parameter, null);
        for (final Instance inst : getSelectedInstances(_parameter)) {
            html.append("<input type=\"hidden\" name=\"swapInstance\" value=\"")
                .append(inst.getOid()).append("\"/>");
        }
        ret.put(ReturnValues.SNIPLETT, html.toString());
        return ret;
    }

    /**
     * Renders a field containing information about the selected document, sets
     * the description of the description field and set the values for the
     * accounts in debit and credit.
     *
     * @param _parameter Parameter as passed from eFaps to an esjp
     * @return html snipplet
     * @throws EFapsException on error
     */
    public Return getDocumentFieldValue(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final StringBuilder html = getDocumentFieldSnipplet(_parameter, null);

        for (final Instance docInst : getSelectedDocInst(_parameter)) {
            html.append("<input type=\"hidden\" name=\"originalSelected\" value=\"")
                .append(docInst.getOid()).append("\"/>");
        }

        final StringBuilder inner = new StringBuilder()
                .append("var docs = query('input[name=document]');\n")
                .append("query('select[name^=docLink]').forEach(function (node) {\n")
                .append("for (i = node.options.length; i > docs.length; i--) {\n")
                .append("node.remove(i);\n")
                .append("}\n")
                .append("for (i = node.options.length; i < docs.length + 1; i++) {\n")
                .append("var option = document.createElement('option');\n")
                .append("node.add(option);\n")
                .append("}\n")
                .append("for (i = 1; i < node.options.length; i++) {\n")
                .append("node.options[i].text =  i + '.';\n")
                .append("node.options[i].value =  docs[i-1].value;\n")
                .append("}\n")
                .append("});\n");

        final StringBuilder topic = new StringBuilder()
                .append("topic.subscribe(\"eFaps/addRowBeforeScript/transactionPositionDebitTable\", function(){\n")
                .append("ud();\n")
                .append("});\n")
                .append("topic.subscribe(\"eFaps/addRowBeforeScript/transactionPositionCreditTable\", function(){\n")
                .append("ud();\n")
                .append("});\n");

        final StringBuilder js = new StringBuilder();
        js.append("var ud = function () {\n")
                .append(InterfaceUtils.wrapInDojoRequire(_parameter, inner, DojoLibs.QUERY))
                .append("}\n")
                .append(InterfaceUtils.wrapInDojoRequire(_parameter, topic, DojoLibs.TOPIC));

        html.append(InterfaceUtils.wrappInScriptTag(_parameter, js, true, 0));

        ret.put(ReturnValues.SNIPLETT, html.toString());
        return ret;
    }

    /**
     * Gets the document field snipplet.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @param _js the js
     * @return the document field snipplet
     * @throws EFapsException on error
     */
    protected StringBuilder getDocumentFieldSnipplet(final Parameter _parameter,
                                                     final StringBuilder _js)
        throws EFapsException
    {
        final StringBuilder ret = new StringBuilder();
        final List<DocumentInfo> docs = new ArrayList<>();
        final List<Integer> rowspan = new ArrayList<>();
        final Instance periodInst = Period.evalCurrent(_parameter);
        final Table table = new Table().setStyle("width:350px;").addAttribute("id", "documentTable");
        for (final Instance docInst : getSelectedDocInst(_parameter)) {
            // if it is a contact instance the document will be created afterwards
            if (docInst.getType().isKindOf(CIContacts.ContactAbstract)) {
                final PrintQuery print = new PrintQuery(docInst);
                print.addAttribute(CIContacts.ContactAbstract.Name);
                print.execute();
                final StringBuilder label = new StringBuilder().append(docInst.getType().getLabel())
                            .append("<input type=\"hidden\" name=\"document\" value=\"").append(docInst.getOid())
                                .append("\"/>").append(getRemoveBtn(_parameter, _js));
                table.addRow()
                    .addColumn(label).getCurrentColumn().setStyle("font-weight: bold").getCurrentTable()
                    .addRow()
                        .addColumn(print.<String>getAttribute(CIContacts.ContactAbstract.Name));
                rowspan.add(table.getRows().size());
            } else {
                final DocumentInfo doc = new DocumentInfo(docInst);
                addDocumentInfo(_parameter, table, doc, _js);
                final DocTaxInfo taxInfo = AbstractDocumentTax.getDocTaxInfo(_parameter, doc.getInstance());
                final Properties props = Accounting.getSysConfig().getObjectAttributeValueAsProperties(periodInst);
                if (BooleanUtils.toBoolean(props.getProperty(AccountingSettings.PERIOD_INCOMINGPERWITHDOC,
                                SummarizeDefinition.NEVER.name())) && taxInfo.isPerception()) {
                    final DocumentInfo percDoc = new DocumentInfo(taxInfo.getTaxDocInstance(
                                    CISales.IncomingPerceptionCertificate));
                    addDocumentInfo(_parameter, table, percDoc, _js);
                }
                rowspan.add(table.getRows().size());
            }
        }
        int current = 0;
        int i = 1;
        for (final Integer span : rowspan) {
            final Row row = table.getRows().get(current);
            row.insertColumn(0, i + ".").setRowSpan(span - current);
            current = span;
            i++;
        }
        ret.append(table.toHtml()).append(add2DocumentField(_parameter, docs, _js));
        return ret;
    }

    /**
     * Adds the two document field.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @param _docs the docs
     * @param _js the js
     * @return the string builder
     */
    protected StringBuilder add2DocumentField(final Parameter _parameter,
                                              final List<DocumentInfo> _docs,
                                              final StringBuilder _js)
    {
        return new StringBuilder();
    }

    /**
     * Gets the removes the btn.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @param _js the js
     * @return the removes the btn
     */
    protected StringBuilder getRemoveBtn(final Parameter _parameter,
                                         final StringBuilder _js)
    {
        final String id = RandomStringUtils.randomAlphanumeric(8);
        final StringBuilder ret = new StringBuilder()
            .append("<span id=\"").append(id).append("\" ")
            .append("style=\"float: right; background-image:")
            .append(" url(&quot;../servlet/image/org.efaps.ui.wicket.components.menutree.Remove.gif?&quot;); ")
            .append("background-repeat: no-repeat; height: 12px; width: 14px; cursor: pointer;\"></span>");

        _js.append("on.once(dom.byId('").append(id).append("'), 'click', function (e) {\n")
            .append("var sb = dojo.query(e.target.parentNode.parentNode).nextAll().some(function (node) {\n")
            .append("var c = query('td[rowspan]', node);\n")
            .append("if (c.length < 1) {\n")
            .append("domConstruct.destroy(node);\n")
            .append("return false;\n")
            .append("} else {\n")
            .append("return true;\n")
            .append("}\n")
            .append("});\n")
            .append("domConstruct.destroy(e.target.parentNode.parentNode);\n")
            .append("var i = 1;\n")
            .append("query('td[rowspan]', 'documentTable').forEach(function (node) {\n")
            .append("node.textContent = i + '.';\n")
            .append("i++;\n")
            .append("});\n")
            .append("topic.publish(\"eFaps/addRowBeforeScript/transactionPositionDebitTable\");\n")
            .append("});\n");

        return ret;
    }

   /**
    * @param _parameter Parameter as passed from eFaps to an esjp
    * @return list of selected instances
    * @throws EFapsException on error
    */
    public List<Instance> getSelectedDocInst(final Parameter _parameter)
        throws EFapsException
    {
        final List<Instance> ret = new ArrayList<>();
        final String[] oids = _parameter.getParameterValues("selectedRow");
        if (oids != null) {
            for (final String oid : oids) {
                final Instance docInst = Instance.get(oid);
                if (docInst.isValid()) {
                    if (docInst.getType().isCIType(CISales.RetentionCertificate)) {
                        final QueryBuilder queryBldr = new QueryBuilder(CISales.RetentionCertificate2IncomingRetention);
                        queryBldr.addWhereAttrEqValue(CISales.RetentionCertificate2IncomingRetention.FromLink, docInst);
                        final MultiPrintQuery multi = queryBldr.getCachedPrint(Context.getThreadContext()
                                        .getRequestId());
                        final SelectBuilder sel = SelectBuilder.get()
                                        .linkto(CISales.RetentionCertificate2IncomingRetention.ToLink)
                                        .instance();
                        multi.addSelect(sel);
                        multi.execute();
                        while (multi.next()) {
                            final Instance relInst = multi.getSelect(sel);
                            if (relInst != null && relInst.isValid()) {
                                ret.add(relInst);
                            }
                        }
                    } else  if (docInst.getType().isCIType(CISales.Document2Document4Swap)) {
                        final PrintQuery print = new PrintQuery(docInst);
                        final SelectBuilder selFromInst = SelectBuilder.get().linkto(
                                        CISales.Document2Document4Swap.FromLink).instance();
                        final SelectBuilder selToInst = SelectBuilder.get().linkto(
                                        CISales.Document2Document4Swap.ToLink).instance();
                        print.addSelect(selFromInst, selToInst);
                        print.execute();
                        ret.add(print.<Instance>getSelect(selFromInst));
                        ret.add(print.<Instance>getSelect(selToInst));
                    } else {
                        ret.add(docInst);
                    }
                }
            }
        }
        return ret;
    }

    /**
     * Renders a field containing information about the selected document.
     *
     * @param _parameter Parameter as passed from eFaps to an esjp
     * @return html snipplet
     * @throws EFapsException on error
     */
    public Return getDocDetailFieldValue(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final StringBuilder html = new StringBuilder();
        final IUIValue fieldValue = (IUIValue) _parameter.get(ParameterValues.UIOBJECT);
        html.append("<span name=\"").append(fieldValue.getField().getName()).append("_span\">");
        for (final Instance docInst : getSelectedDocInst(_parameter)) {
            html.append(getDocDetail(_parameter, docInst));
        }
        html.append("</span>");
        ret.put(ReturnValues.SNIPLETT, html.toString());
        return ret;
    }

    /**
     * @param _parameter Parameter as passed from the eFaps API
     * @return document detail
     * @param _docInst instance of the document
     * @throws EFapsException on error
     */
    protected String getDocDetail(final Parameter _parameter,
                                  final Instance _docInst)
        throws EFapsException
    {
        final DocumentDetailsReport report = new DocumentDetailsReport();
        final Parameter parameter = new Parameter();
        parameter.put(ParameterValues.INSTANCE, _docInst);
        final AbstractDynamicReport dyRp = report.getReport(parameter);
        return dyRp.getHtmlSnipplet(parameter);
    }

    /**
     * Internal method for {@link #getDocumentFieldValue(Parameter)}.
     *
     * @param _parameter    Parameter as passed from eFaps to an esjp
     * @param _table the table
     * @param _doc          Document
     * @param _js the javascript
     * @throws EFapsException on error
     */
    @SuppressWarnings("checkstyle:methodlength")
    protected void addDocumentInfo(final Parameter _parameter,
                                   final Table _table,
                                   final DocumentInfo _doc,
                                   final StringBuilder _js)
        throws EFapsException
    {
        final boolean showNetTotal = !"true".equalsIgnoreCase(getProperty(_parameter, "NoNetTotal"));
        final boolean showAction = !"true".equalsIgnoreCase(getProperty(_parameter, "NoAction"));
        final boolean showLabel = !"true".equalsIgnoreCase(getProperty(_parameter, "NoLabel"));
        final boolean showNote = "true".equalsIgnoreCase(getProperty(_parameter, "ShowNote"));
        final boolean showSwap = !"true".equalsIgnoreCase(getProperty(_parameter, "NoSwap"));
        final boolean showContact = !"true".equalsIgnoreCase(getProperty(_parameter, "NoContact"));

        final String[] origSel = _parameter.getParameterValues("originalSelected");

        _doc.setFormater(NumberFormatter.get().getTwoDigitsFormatter());

        final SelectBuilder accSel = new SelectBuilder().linkto(CISales.DocumentSumAbstract.Contact)
                        .clazz(CIContacts.ClassClient)
                        .linkfrom(CIAccounting.AccountCurrentDebtor2ContactClassClient,
                                        CIAccounting.AccountCurrentDebtor2ContactClassClient.ToClassClientLink)
                        .linkto(CIAccounting.AccountCurrentDebtor2ContactClassClient.FromAccountLink);
        final SelectBuilder accInstSel = new SelectBuilder(accSel).instance();
        final SelectBuilder accNameSel = new SelectBuilder(accSel).attribute(CIAccounting.AccountAbstract.Name);
        final SelectBuilder accDescSel = new SelectBuilder(accSel)
                        .attribute(CIAccounting.AccountAbstract.Description);
        final SelectBuilder currSymbSel = new SelectBuilder()
                        .linkto(CISales.DocumentSumAbstract.CurrencyId).attribute(CIERP.Currency.Symbol);
        final SelectBuilder rateCurrSymbSel = new SelectBuilder()
                        .linkto(CISales.DocumentSumAbstract.RateCurrencyId).attribute(CIERP.Currency.Symbol);
        final SelectBuilder contNameSel = new SelectBuilder()
                        .linkto(CISales.DocumentSumAbstract.Contact).attribute(CIContacts.Contact.Name);
        final SelectBuilder rateLabelSel = new SelectBuilder()
                        .attribute(CISales.DocumentSumAbstract.Rate).label();

        final SelectBuilder currSymbSel4Pay = new SelectBuilder()
            .linkto(CISales.PaymentDocumentAbstract.CurrencyLink).attribute(CIERP.Currency.Symbol);
        final SelectBuilder rateCurrSymbSel4Pay = new SelectBuilder()
            .linkto(CISales.PaymentDocumentAbstract.RateCurrencyLink).attribute(CIERP.Currency.Symbol);

        final SelectBuilder docTypeSel = new SelectBuilder()
            .linkfrom(CISales.Document2DocumentType.DocumentLink)
            .linkto(CISales.Document2DocumentType.DocumentTypeLink)
            .attribute(CIERP.DocumentType.Name);

        final PrintQuery print = new PrintQuery(_doc.getInstance());
        if (_doc.isSumsDoc()) {
            print.addAttribute(CISales.DocumentSumAbstract.CrossTotal,
                               CISales.DocumentSumAbstract.NetTotal,
                               CISales.DocumentSumAbstract.RateCrossTotal,
                               CISales.DocumentSumAbstract.RateNetTotal);
            print.addSelect(rateLabelSel, currSymbSel, rateCurrSymbSel, docTypeSel);
        }
        if (_doc.isPaymentDoc()) {
            print.addAttribute(CISales.PaymentDocumentAbstract.Note, CISales.PaymentDocumentAbstract.Amount);
            print.addSelect(rateLabelSel, currSymbSel4Pay, rateCurrSymbSel4Pay);
        }

        print.addAttribute(CISales.DocumentAbstract.Name,
                           CISales.DocumentAbstract.Date,
                           CISales.DocumentAbstract.StatusAbstract,
                           CISales.DocumentAbstract.Note);
        print.addSelect(accDescSel, accNameSel, accInstSel, contNameSel);
        print.execute();

        String docType = null;
        if (_doc.isSumsDoc()) {
            docType = print.getSelect(docTypeSel);
        }

        _doc.setDate(print.<DateTime>getAttribute(CISales.DocumentAbstract.Date));

        //Basic information for all documents
        final String name = print.<String>getAttribute(CISales.DocumentAbstract.Name);
        final String contactName = print.<String>getSelect(contNameSel);
        final Long statusId = print.<Long>getAttribute(CISales.DocumentAbstract.StatusAbstract);
        final AccountInfo account = new AccountInfo(print.<Instance>getSelect(accInstSel));
        if (account.getInstance() == null) {
            setAccounts4Debit(_parameter, _doc);
        }
        final StringBuilder label = new StringBuilder().append(_doc.getInstance().getType().getLabel())
            .append(docType == null ? "" : " - " + CIERP.DocumentType.getType().getLabel() + ": " + docType)
            .append("<input type=\"hidden\" name=\"document\" value=\"").append(_doc.getInstance().getOid())
            .append("\"/>");

        if (!ArrayUtils.isEmpty(origSel) && !ArrayUtils.contains(origSel, _doc.getInstance().getOid())) {
            label.append(getRemoveBtn(_parameter, _js));
        }

        _table.addRow()
                .addColumn(label).getCurrentColumn().setStyle("font-weight: bold").getCurrentTable()
            .addRow()
                .addColumn(getLabel(_doc.getInstance(), CISales.DocumentAbstract.Name))
                .addColumn(name)
                .addColumn(getLabel(_doc.getInstance(), CISales.DocumentAbstract.Date))
                .addColumn(_doc.getDateString());

        if (showContact) {
            _table.addRow()
                .addColumn(getLabel(_doc.getInstance(), CISales.DocumentAbstract.Contact));
            if (_doc.getDebtorAccount() == null) {
                _table.addColumn(contactName);
            } else {
                _table.addColumn(new StringBuilder().append(contactName).append(" -> ")
                    .append(CIAccounting.AccountCurrentDebtor.getType().getLabel())
                    .append(": ").append(_doc.getDebtorAccount().getName()));
            }
        }
        _table.addRow()
                .addColumn(getLabel(_doc.getInstance(), "Status"))
                .addColumn(Status.get(statusId).getLabel()).getCurrentColumn()
                    .setColSpan(_doc.isSumsDoc() || _doc.isPaymentDoc() ? 1 : 3);

        if (_doc.isSumsDoc()) {
            final BigDecimal crossTotal = print.<BigDecimal>getAttribute(CISales.DocumentSumAbstract.CrossTotal);
            final BigDecimal netTotal = print.<BigDecimal>getAttribute(CISales.DocumentSumAbstract.NetTotal);
            final BigDecimal rateCrossTot = print.<BigDecimal>getAttribute(CISales.DocumentSumAbstract.RateCrossTotal);
            final BigDecimal rateNetTotal = print.<BigDecimal>getAttribute(CISales.DocumentSumAbstract.RateNetTotal);
            final String currSymbol = print.<String>getSelect(currSymbSel);
            final String rateCurrSymbol = print.<String>getSelect(rateCurrSymbSel);
            final BigDecimal rate = print.<BigDecimal>getSelect(rateLabelSel);
            _table.addColumn(getLabel(_doc.getInstance(), CISales.DocumentSumAbstract.Rate))
                .addColumn(rate.toString());

            if (showNetTotal) {
                _table.addRow()
                    .addColumn(getLabel(_doc.getInstance(), CISales.DocumentSumAbstract.NetTotal))
                    .addColumn(NumberFormatter.get().getTwoDigitsFormatter().format(netTotal) + " " + currSymbol)
                    .addColumn(getLabel(_doc.getInstance(), CISales.DocumentSumAbstract.RateNetTotal))
                    .addColumn(NumberFormatter.get().getTwoDigitsFormatter().format(rateNetTotal)
                                    + " " + rateCurrSymbol);
            }
            _table.addRow()
                .addColumn(getLabel(_doc.getInstance(), CISales.DocumentSumAbstract.CrossTotal))
                .addColumn(NumberFormatter.get().getTwoDigitsFormatter().format(crossTotal) + " " + currSymbol)
                .addColumn(getLabel(_doc.getInstance(), CISales.DocumentSumAbstract.RateCrossTotal))
                .addColumn(NumberFormatter.get().getTwoDigitsFormatter().format(rateCrossTot) + " " + rateCurrSymbol);
        }
        if (_doc.isPaymentDoc()) {
            final BigDecimal amount = print.<BigDecimal>getAttribute(CISales.PaymentDocumentAbstract.Amount);
            final String rateCurrSymbol = print.<String>getSelect(rateCurrSymbSel4Pay);
            final BigDecimal rate = print.<BigDecimal>getSelect(rateLabelSel);

            _table.addRow()
                .addColumn(getLabel(_doc.getInstance(), CISales.DocumentSumAbstract.Rate))
                .addColumn(rate.toString())
                .addColumn(getLabel(_doc.getInstance(), CISales.PaymentDocumentAbstract.Amount))
                .addColumn(NumberFormatter.get().getTwoDigitsFormatter().format(amount) + " " + rateCurrSymbol)
                .addColumn(getLabel(_doc.getInstance(), CISales.DocumentSumAbstract.Note))
                .addColumn(print.<String>getAttribute(CISales.PaymentDocumentAbstract.Note));

            final QueryBuilder queryBldr = new QueryBuilder(CIERP.Document2PaymentDocumentAbstract);
            queryBldr.addWhereAttrEqValue(CIERP.Document2PaymentDocumentAbstract.ToAbstractLink, _doc.getInstance());
            final MultiPrintQuery multi = queryBldr.getPrint();
            final SelectBuilder selDoc = new SelectBuilder().linkto(
                            CIERP.Document2PaymentDocumentAbstract.FromAbstractLink);
            final SelectBuilder selDocInst = new SelectBuilder(selDoc).instance();
            multi.addSelect(selDocInst);
            multi.execute();
            final SelectBuilder selCurInst = new SelectBuilder().linkto(CISales.DocumentSumAbstract.RateCurrencyId)
                            .instance();
            while (multi.next()) {
                final Instance docInst = multi.<Instance>getSelect(selDocInst);
                if (docInst.isValid()) {
                    final PrintQuery print2 = new PrintQuery(docInst);
                    print2.addAttribute(CISales.DocumentSumAbstract.RateCrossTotal, CISales.DocumentSumAbstract.Name);
                    print2.addSelect(selCurInst);
                    print2.execute();

                    final Instance curInst = print2.<Instance>getSelect(selCurInst);
                    final BigDecimal rateCross = print2.<BigDecimal>getAttribute(
                                    CISales.DocumentSumAbstract.RateCrossTotal);
                    final String docName = print2.<String>getAttribute(CISales.DocumentSumAbstract.Name);
                    _table.addRow()
                        .addColumn(docInst.getType().getLabel())
                        .addColumn(docName)
                        .addColumn(getLabel(docInst, CISales.DocumentSumAbstract.RateCrossTotal))
                        .addColumn(rateCross + " " + new CurrencyInst(curInst).getSymbol());
                }
            }
        }

        if (showAction && !_doc.isPaymentDoc()) {
            final QueryBuilder actionQueryBldr = new QueryBuilder(CIERP.ActionDefinition2DocumentAbstract);
            actionQueryBldr.addWhereAttrEqValue(CIERP.ActionDefinition2DocumentAbstract.ToLinkAbstract,
                            _doc.getInstance());
            final MultiPrintQuery actionMulti = actionQueryBldr.getPrint();
            final SelectBuilder selName = SelectBuilder.get()
                            .linkto(CIERP.ActionDefinition2DocumentAbstract.FromLinkAbstract)
                            .attribute(CIERP.ActionDefinitionAbstract.Name);
            actionMulti.addSelect(selName);
            actionMulti.execute();
            while (actionMulti.next()) {
                _table.addRow()
                    .addColumn(DBProperties.getProperty(org.efaps.esjp.accounting.transaction.FieldValue.class.getName()
                                    + ".ActionInfo"))
                    .addColumn(actionMulti.<String>getSelect(selName));
            }
        }

        if (showLabel) {
            final List<Instance> labelInsts = new Label().getLabelInst4Documents(_parameter, _doc.getInstance());
            if (labelInsts != null) {
                final MultiPrintQuery labelMulti = new MultiPrintQuery(labelInsts);
                final SelectBuilder selPeriodName = SelectBuilder.get()
                                .linkto(CIAccounting.LabelAbstract.PeriodAbstractLink)
                                .attribute(CIAccounting.Period.Name);
                final SelectBuilder selPeriodInst = SelectBuilder.get()
                                .linkto(CIAccounting.LabelAbstract.PeriodAbstractLink)
                                .instance();
                labelMulti.addSelect(selPeriodInst, selPeriodName);
                labelMulti.addAttribute(CIAccounting.LabelAbstract.Name);
                labelMulti.execute();
                while (labelMulti.next()) {
                    _table.addRow()
                        .addColumn(DBProperties.getProperty(org.efaps.esjp.accounting.transaction.FieldValue.class
                                                                    .getName() + ".LabelInfo"))
                        .addColumn(labelMulti.<String>getAttribute(CIAccounting.LabelAbstract.Name));
                    final Instance periodInst = labelMulti.getSelect(selPeriodInst);
                    if (!periodInst.equals(new Period().evaluateCurrentPeriod(_parameter))) {
                        _table.addColumn(CIAccounting.Period.getType().getLabel())
                            .addColumn(labelMulti.<String>getSelect(selPeriodName));
                    }
                }
            }
        }

        if (showSwap && !_doc.isPaymentDoc()) {
            final QueryBuilder swapQueryBldr = new QueryBuilder(CISales.Document2Document4Swap);
            swapQueryBldr.setOr(true);
            swapQueryBldr.addWhereAttrEqValue(CISales.Document2Document4Swap.FromLink, _doc.getInstance());
            swapQueryBldr.addWhereAttrEqValue(CISales.Document2Document4Swap.ToLink, _doc.getInstance());
            final InstanceQuery swapQuery = swapQueryBldr.getQuery();
            final List<Instance> relInst = swapQuery.execute();
            if (!relInst.isEmpty()) {
                for (final SwapInfo info : Swap.getSwapInfos(_parameter, _doc.getInstance(), relInst).values()) {
                    _table.addRow()
                        .addColumn(info.getDirection()).addColumn(info.getDocument())
                        .addColumn(info.getAmount() + " " + CurrencyInst.get(info.getCurrencyInstance()).getSymbol());
                }
            }
        }

        if (showNote) {
            final String note = print.<String>getAttribute(CISales.DocumentAbstract.Note);
            _table.addRow().addColumn(note);
        }
    }

    /**
     * Add the information about the costs of the positions.
     *
     * @param _parameter        Parameter as passed from the eFaps API
     * @param _date             Date the cost will be search for
     * @param _doc              Instance of the Document the form was opened for
     * @return html snipplet
     * @throws EFapsException on error
     */
    protected StringBuilder getCostInformation(final Parameter _parameter,
                                               final DateTime _date,
                                               final DocumentInfo _doc)
        throws EFapsException
    {
        final StringBuilder html = new StringBuilder();
        if (_doc.isStockDoc()) {
            boolean costValidated = true;
            final Map<?, ?> props = (Map<?, ?>) _parameter.get(ParameterValues.PROPERTIES);
            final boolean script = !"true".equalsIgnoreCase((String) props.get("noScript"));
            final Instance periodInst = new Period().evaluateCurrentPeriod(_parameter);
            final RateInfo rate = evaluateRate(_parameter, periodInst, _date == null ? new DateTime() : _date, null);
            _doc.setRateInfo(rate);

            html.append("<table>");
            final QueryBuilder queryBldr = new QueryBuilder(CISales.PositionAbstract);
            queryBldr.addWhereAttrEqValue(CISales.PositionAbstract.DocumentAbstractLink, _doc.getInstance().getId());
            final MultiPrintQuery multi = queryBldr.getPrint();
            multi.addAttribute(CISales.PositionAbstract.Quantity, CISales.PositionAbstract.UoM);
            final SelectBuilder sel = new SelectBuilder()
                .linkto(CISales.PositionAbstract.Product);
            final SelectBuilder oidSel = new SelectBuilder(sel).oid();
            final SelectBuilder nameSel = new SelectBuilder(sel).attribute(CIProducts.ProductAbstract.Name);
            final SelectBuilder descSel = new SelectBuilder(sel).attribute(CIProducts.ProductAbstract.Description);
            multi.addSelect(oidSel, nameSel, descSel);
            multi.execute();
            boolean first = true;
            BigDecimal total = BigDecimal.ZERO;
            while (multi.next()) {
                final Instance prodInst = Instance.get(multi.<String>getSelect(oidSel));
                if (first) {
                    first = false;
                    html.append("<tr>")
                        .append("<th>").append(getLabel(multi.getCurrentInstance(), CISales.PositionAbstract.Quantity))
                        .append("</th>")
                        .append("<th>").append(getLabel(multi.getCurrentInstance(), CISales.PositionAbstract.UoM))
                        .append("</th>")
                        .append("<th>").append(getLabel(prodInst, CIProducts.ProductAbstract.Name)).append("</th>")
                        .append("<th>").append(getLabel(prodInst, CIProducts.ProductAbstract.Description))
                        .append("</th>")
                        .append("<th>").append(DBProperties.getProperty(CIProducts.ProductCost.getType().getName()
                                        + "/" + CIProducts.ProductCost.Price.name  + ".Label")).append("</th>")
                        .append("<th>").append(DBProperties.getProperty(CIProducts.ProductCost.getType().getName()
                                        + "/" + CIProducts.ProductCost.CurrencyLink.name + ".Label")).append("</th>")
                        .append("<tr>");
                }
                html.append("<tr>");

                final BigDecimal quantity = multi.<BigDecimal>getAttribute(CISales.PositionAbstract.Quantity);
                final UoM uom = Dimension.getUoM(multi.<Long>getAttribute(CISales.PositionAbstract.UoM));
                html.append("<td>").append(quantity).append("</td>")
                    .append("<td>").append(uom.getName()).append("</td>")
                    .append("<td>").append(multi.<String>getSelect(nameSel)).append("</td>")
                    .append("<td>").append(multi.<String>getSelect(descSel)).append("</td>");

                final QueryBuilder costQueryBuilder = new QueryBuilder(CIProducts.ProductCost);
                costQueryBuilder.addWhereAttrEqValue(CIProducts.ProductCost.ProductLink, prodInst.getId());
                costQueryBuilder.addWhereAttrLessValue(CIProducts.ProductCost.ValidFrom,
                                _date == null ? new DateTime() : _date.plusMinutes(1));
                costQueryBuilder.addWhereAttrGreaterValue(CIProducts.ProductCost.ValidUntil,
                                _date == null ? new DateTime() : _date.minusMinutes(1));
                costQueryBuilder.addOrderByAttributeDesc(CIProducts.ProductCost.ID);
                final InstanceQuery query = costQueryBuilder.getQuery();
                query.setLimit(1);
                query.executeWithoutAccessCheck();
                if (query.next()) {
                    final PrintQuery print = new PrintQuery(query.getCurrentValue());
                    print.addAttribute(CIProducts.ProductCost.Price);
                    final SelectBuilder currSel = new SelectBuilder().linkto(CIProducts.ProductCost.CurrencyLink)
                                    .instance();
                    print.addSelect(currSel);
                    print.executeWithoutAccessCheck();
                    final BigDecimal price = print.<BigDecimal>getAttribute(CIProducts.ProductCost.Price);
                    final Instance currInst = print.<Instance>getSelect(currSel);

                    final RateInfo rateTmp = evaluateRate(_parameter, periodInst,
                                    _date == null ? new DateTime() : _date,  currInst);

                    final BigDecimal cost = quantity.multiply(new BigDecimal(uom.getNumerator()))
                                        .divide(new BigDecimal(uom.getDenominator())).multiply(price);
                    html.append("<td>").append(NumberFormatter.get().getTwoDigitsFormatter().format(price))
                        .append("</td>")
                         .append("<td>").append(rateTmp.getCurrencyInstObj().getSymbol()).append("</td>");

                    if (script) {
                        analyzeProduct(_doc, true, prodInst, cost, rate,
                                       CIAccounting.AccountBalanceSheetAsset2ProductClass,
                                       CIAccounting.AccountBalanceSheetAsset);
                        analyzeProduct(_doc, false, prodInst, cost, rate,
                                        CIAccounting.AccountIncomeStatementExpenses2ProductClass,
                                        CIAccounting.AccountIncomeStatementExpenses);
                    }
                    total = total.add(cost.setScale(12, BigDecimal.ROUND_HALF_UP)
                                    .divide(rateTmp.getRate(), BigDecimal.ROUND_HALF_UP));
                } else {
                    html.append("<td></td>")
                        .append("<td></td>");
                    if (costValidated) {
                        costValidated = false;
                    }
                }
                html.append("</tr>");
            }
            html.append("<tr>")
                .append("<td colspan=4></td><td>").append(NumberFormatter.get().getTwoDigitsFormatter().format(total))
                .append("<input type=\"hidden\" name=\"amountExternal\" value=\"").append(total).append("\"/>")
                .append("</td>")
                .append("<td>").append(rate.getCurrencyInstObj().getSymbol())
                .append("<input type=\"hidden\" name=\"currencyExternal\" value=\"")
                .append(rate.getCurrencyInstance().getId()).append("\"/>")
                .append("</td>")
                .append("<tr>")
                .append("</table>");
            _doc.setCostValidated(costValidated);
            _doc.setAmount(total);
        }
        return html;
    }

    /**
     * Get the script to get the Prices for the Products.
     *
     * @param _parameter        Parameter as passed from the eFaps API
     * @param _doc              Instance of the Document the form was opened for
     * @throws EFapsException on error
     */
    protected void getPriceInformation(final Parameter _parameter,
                                       final DocumentInfo _doc)
        throws EFapsException
    {
        final QueryBuilder queryBldr = new QueryBuilder(CISales.PositionAbstract);
        queryBldr.addWhereAttrEqValue(CISales.PositionAbstract.DocumentAbstractLink, _doc.getInstance().getId());
        final MultiPrintQuery multi = queryBldr.getPrint();
        final SelectBuilder selTaxInst = new SelectBuilder().linkto(CISales.PositionSumAbstract.Tax).instance();
        final SelectBuilder selProdInst = new SelectBuilder().linkto(CISales.PositionAbstract.Product).instance();
        multi.addSelect(selTaxInst, selProdInst);
        multi.addAttribute(CISales.PositionSumAbstract.NetPrice,
                           CISales.PositionSumAbstract.CrossPrice,
                           CISales.PositionSumAbstract.Rate);
        multi.execute();
        final Instance period = new Period().evaluateCurrentPeriod(_parameter);
        while (multi.next()) {
            final BigDecimal net = multi.<BigDecimal>getAttribute(CISales.PositionSumAbstract.NetPrice);
            final BigDecimal cross = multi.<BigDecimal>getAttribute(CISales.PositionSumAbstract.CrossPrice);
            final Object[] ratePos = multi.<Object[]>getAttribute(CISales.PositionSumAbstract.Rate);
            final BigDecimal newRatepos = ((BigDecimal) ratePos[0]).divide((BigDecimal) ratePos[1], 12,
                        BigDecimal.ROUND_HALF_UP);
            final BigDecimal taxAmount = cross.subtract(net).multiply(newRatepos);
            final BigDecimal prodAmount = net.multiply(newRatepos);
            analyzeTax(_doc, false, multi.<Instance>getSelect(selTaxInst), taxAmount);
            final RateInfo rate = evaluateRate(_parameter, period, _doc.getDate(), _doc.getRateCurrInst());
            analyzeProduct(_doc, false, multi.<Instance>getSelect(selProdInst), prodAmount, rate,
                           CIAccounting.AccountIncomeStatementRevenue2ProductClass,
                           CIAccounting.AccountIncomeStatementRevenue);
        }
    }

    /**
     * Analyse a product for income statement.
     *
     * @param _doc          Document
     * @param _debit        debit or credit
     * @param  _productInstance  insatnce of the product
     * @param _amount       amount
     * @param _rate         Rate
     * @param _relType      CIType for the relation
     * @param _acountType   CIType for the account
     * @throws EFapsException on error
     */
    protected void analyzeProduct(final DocumentInfo _doc,
                                  final boolean _debit,
                                  final Instance _productInstance,
                                  final BigDecimal _amount,
                                  final RateInfo _rate,
                                  final CIType _relType,
                                  final CIType _acountType)
        throws EFapsException
    {
        final PrintQuery print = new PrintQuery(_productInstance);
        final SelectBuilder sel = SelectBuilder.get().clazz().type();
        print.addSelect(sel);
        print.execute();
        final List<Classification> list = print.<List<Classification>>getSelect(sel);
        if (list != null) {
            for (final Classification clazz : list) {
                Long id = null;
                Classification current = clazz;
                while (current.getParentClassification() != null) {
                    final QueryBuilder queryBldr = new QueryBuilder(_relType);
                    queryBldr.addWhereAttrEqValue(CIAccounting.Account2ProductClass.ToProductClassAbstractLink,
                                                  current.getId());
                    final MultiPrintQuery multi = queryBldr.getPrint();
                    multi.addAttribute(CIAccounting.Account2ProductClass.FromAccountAbstractLink);
                    multi.execute();
                    if (multi.next()) {
                        id = multi.<Long>getAttribute(CIAccounting.Account2ProductClass.FromAccountAbstractLink);
                        break;
                    }
                    current = current.getParentClassification();
                }
                if (id != null) {
                    final Instance accInst = Instance.get(_acountType.getType(), id);
                    if (accInst.isValid()) {
                        final AccountInfo account = new AccountInfo().setInstance(accInst).addAmount(_amount);
                        account.setRateInfo(_rate, _doc.getRatePropKey());
                        if (_debit) {
                            _doc.addDebit(account);
                        } else {
                            _doc.addCredit(account);
                        }
                    }
                    break;
                }
            }
        }
    }

    /**
     * Method for analizeTax.
     *
     * @param _doc docuemtn info
     * @param _debit dbeit or credit
     * @param _taxInst instance of the Tax.
     * @param _tax contains amount of the Tax.
     * @throws EFapsException on error.
     */
    protected void analyzeTax(final DocumentInfo _doc,
                              final boolean _debit,
                              final Instance _taxInst,
                              final BigDecimal _tax)
        throws EFapsException
    {
        final SelectBuilder sel = new SelectBuilder()
            .linkfrom(CIAccounting.AccountBalanceSheetLiability2Tax,
                            CIAccounting.AccountBalanceSheetLiability2Tax.ToTaxLink)
            .linkto(CIAccounting.AccountBalanceSheetLiability2Tax.FromAccountLink);

        final SelectBuilder selInst = new SelectBuilder(sel).instance();

        final PrintQuery print = new PrintQuery(_taxInst);
        print.addSelect(selInst);
        print.execute();
        final Instance accountInst = print.<Instance>getSelect(selInst);
        if (accountInst != null) {
            if (_debit) {
                _doc.addDebit(new AccountInfo(accountInst, _tax));
            } else {
                _doc.addCredit(new AccountInfo(accountInst, _tax));
            }
        }
    }

    /**
     * @param _parameter    Parameter as passed from the eFaps API
     * @param _doc          Document the account information will be set
     * @throws EFapsException on error
     */
    protected void setAccounts4Debit(final Parameter _parameter,
                                     final DocumentInfo _doc)
        throws EFapsException
    {
        // Accounting-Configuration
        final Instance accInst = SystemConfiguration.get(UUID.fromString("ca0a1df1-2211-45d9-97c8-07af6636a9b9"))
                        .getLink("DefaultAccount4DocClient");
        if (accInst.isValid()) {
            final AccountInfo account = new AccountInfo().setInstance(accInst);
            _doc.setDebtorAccount(account);
        }
    }

    /**
     * @param _docInst  instance of the Document
     * @param _attr     abstract CIAttribute the label is wanted for
     * @return Label
     */
    protected String getLabel(final Instance _docInst,
                              final String _attr)
    {
        final StringBuilder bldr = new StringBuilder()
            .append(_docInst.getType().getName());
        if (_attr != null) {
            bldr.append("/").append(_attr);
        }
        bldr.append(".Label");
        return DBProperties.getProperty(bldr.toString());
    }

    /**
     * @param _docInst  instance of the Document
     * @param _attr     abstract CIAttribute the label is wanted for
     * @return Label
     */
    protected String getLabel(final Instance _docInst,
                              final CIAttribute _attr)
    {
        return getLabel(_docInst, _attr.name);
    }

    /**
     * Renders a drop-down containing the Types for label projects.
     *
     * @param _parameter Parameter as passed from the eFaps API
     * @return html snipplet
     * @throws EFapsException on error
     */
    public Return getLabelFieldValue(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final Object uiObject = _parameter.get(ParameterValues.UIOBJECT);
        if (uiObject instanceof IUIValue) {
            final List<DropDownPosition> values = new ArrayList<>();
            if (org.efaps.admin.ui.field.Field.Display.EDITABLE.equals(((IUIValue) uiObject).getDisplay())) {
                Instance labelInst = null;
                if (_parameter.getInstance() != null && _parameter.getInstance().isValid()) {
                    final PrintQuery print = new PrintQuery(_parameter.getInstance());
                    final SelectBuilder selLabelInst = SelectBuilder.get()
                                    .linkfrom(CIAccounting.TransactionPosition2LabelAbstract.FromLinkAbstract)
                                    .linkto(CIAccounting.TransactionPosition2LabelAbstract.ToLinkAbstract).instance();
                    print.addSelect(selLabelInst);
                    print.execute();
                    labelInst = print.getSelect(selLabelInst);
                }

                final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.LabelAbstract);
                queryBldr.addWhereAttrEqValue(CIAccounting.LabelAbstract.StatusAbstract,
                                Status.find(CIAccounting.LabelStatus.Active));
                final MultiPrintQuery print = queryBldr.getPrint();
                print.addAttribute(CIAccounting.LabelAbstract.Name,
                                CIAccounting.LabelAbstract.Description);
                print.execute();
                while (print.next()) {
                    final DropDownPosition drP = new DropDownPosition(
                                    print.getCurrentInstance().getOid(),
                                    print.<String>getAttribute(CIAccounting.LabelAbstract.Name)
                                                    + " - "
                                                  + print.<String>getAttribute(CIAccounting.LabelAbstract.Description));

                    drP.setSelected(print.getCurrentInstance().equals(labelInst));
                    values.add(drP);
                }

                final LabelDefinition labelDef = new Period().getLabelDefinition(_parameter);
                switch (labelDef) {
                    case BALANCE:
                    case COST:
                        values.add(0, new DropDownPosition("", "-"));
                        break;
                    default:
                        break;
                }
            }
            ret.put(ReturnValues.VALUES, values);
        }
        return ret;
    }

    /**
     * @param _parameter Parameter as passed from the eFaps API
     * @return  default value for the currency field
     * @throws EFapsException on error
     */
    public Return getRateFieldValue(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        ret.put(ReturnValues.VALUES, BigDecimal.ONE);
        return ret;
    }

    /**
     * @param _parameter Parameter as passed by the eFaps API
     * @return  value for date on opening the form
     * @throws EFapsException on error
     */
    public Return getDateFieldValue(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        // only for edit or create the date is set
        if (TargetMode.EDIT.equals(_parameter.get(ParameterValues.ACCESSMODE))
                        || TargetMode.CREATE.equals(_parameter.get(ParameterValues.ACCESSMODE))) {
            DateTime date = null;
            if (_parameter.getInstance() != null && _parameter.getInstance().isValid()) {
                final IUIValue fieldValue = (IUIValue) _parameter.get(ParameterValues.UIOBJECT);
                final Object objTmp = fieldValue.getObject();
                if (objTmp != null && objTmp instanceof DateTime) {
                    date = (DateTime) objTmp;
                }
            }
            if (date == null) {
                date = new DateTime();
            }
            final DateTime[] dates = getDateMaxMin(_parameter);
            final DateTime fromDate = dates[0];
            final DateTime toDate = dates[1];
            if (fromDate != null && toDate != null) {
                if (date.isBefore(fromDate)) {
                    date = fromDate;
                } else if (date.isAfter(toDate)) {
                    date = toDate;
                }
            }
            ret.put(ReturnValues.VALUES, date);
        }
        return ret;
    }

    /**
     * @param _parameter Parameter as passed by the eFaps API
     * @return  value for date on opening the form
     * @throws EFapsException on error
     */
    public Return getSalesAccountFieldValue(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final IUIValue fieldValue = (IUIValue) _parameter.get(ParameterValues.UIOBJECT);
        if (!Display.NONE.equals(fieldValue.getDisplay())) {
            @SuppressWarnings("unchecked")
            Map<Instance, String> values = (Map<Instance, String>) Context.getThreadContext().getRequestAttribute(
                            FieldValue_Base.SALESACC_REQKEY);
            if (values == null || values != null && !values.containsKey(_parameter.getInstance())) {
                values = new HashMap<>();
                Context.getThreadContext().setRequestAttribute(FieldValue_Base.SALESACC_REQKEY, values);
                @SuppressWarnings("unchecked")
                final List<Instance> instances = (List<Instance>) _parameter.get(ParameterValues.REQUEST_INSTANCES);
                if (instances != null) {
                    final MultiPrintQuery multi = new MultiPrintQuery(instances);
                    final SelectBuilder selPayment = new SelectBuilder().linkfrom(CISales.Payment,
                                    CISales.Payment.CreateDocument).instance();
                    multi.addSelect(selPayment);
                    multi.executeWithoutAccessCheck();
                    while (multi.next()) {
                        final Instance payment = multi.<Instance>getSelect(selPayment);
                        final PrintQuery print = new PrintQuery(payment);
                        final SelectBuilder selAccount = new SelectBuilder().linkfrom(CISales.TransactionAbstract,
                                        CISales.TransactionAbstract.Payment).linkto(CISales.TransactionAbstract.Account)
                                        .attribute(CISales.AccountAbstract.Name);
                        print.addSelect(selAccount);
                        print.executeWithoutAccessCheck();
                        final String accountName = print.<String>getSelect(selAccount);
                        values.put(multi.getCurrentInstance(), accountName);
                    }
                }
            }
            ret.put(ReturnValues.VALUES, values.get(_parameter.getInstance()));
        }
        return ret;
    }

    /**
     * Method is called from a hidden field to include javascript in the form.
     *
     * @param _parameter Parameter as passed from the eFaps API
     * @return Return containing the javascript
     * @throws EFapsException on error
     */
    public Return getJavaScriptFieldValue(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final StringBuilder js = new StringBuilder();
        final SummarizeDefinition summarizeDef = new Period().getSummarizeDefinition(_parameter);
        if (SummarizeDefinition.CASE.equals(summarizeDef) || SummarizeDefinition.CASEUSER.equals(summarizeDef)) {
            final Instance caseInst = Instance
                            .get((String) Context.getThreadContext().getRequestAttribute(FieldValue_Base.CASE_REQKEY));
            if (caseInst.isValid()) {
                final PrintQuery print = new CachedPrintQuery(caseInst, Case.CACHEKEY);
                print.addAttribute(CIAccounting.CaseAbstract.SummarizeConfig);
                print.executeWithoutAccessCheck();

                final StringBuilder caseJs = new StringBuilder();
                final String fieldName = CIFormAccounting.Accounting_TransactionCreate4ExternalForm
                                .summarizeConfig.name;
                final SummarizeConfig config =  print.getAttribute(CIAccounting.CaseAbstract.SummarizeConfig);

                caseJs.append(" query(\"input[name=\\\"").append(fieldName).append("\\\"][value=")
                    .append(config.getInt()).append("] \").forEach(function(node){\n")
                    .append(" domAttr.set(node, \"checked\", true); \n")
                    .append("});\n");

                if (SummarizeDefinition.CASE.equals(summarizeDef)) {
                    caseJs.append(" query(\"input[name=\\\"").append(fieldName)
                        .append("\\\"]\").forEach(function(node){\n")
                        .append(" domAttr.set(node, \"readonly\", true); \n").append("});\n");
                }

                js.append(InterfaceUtils.wrapInDojoRequire(_parameter, caseJs, DojoLibs.QUERY, DojoLibs.DOMATTR));
            }
        }

        final LabelDefinition labelDef = new Period().getLabelDefinition(_parameter);
        if (LabelDefinition.BALANCE.equals(labelDef) || LabelDefinition.BALANCEREQUIRED.equals(labelDef)) {
            final StringBuilder labelJs = new StringBuilder();
            labelJs.append("topic.subscribe(\"eFaps/addRow/transactionPositionCreditTable\", function(){\n")
                     .append("query(\"select[name^=\\\"labelLink\\\"]\").some(function(node){\n")
                         .append("query(\"select[name^=\\\"labelLink\\\"]\").forEach(function(node2){\n")
                             .append("node2.value=node.value\n")
                          .append("});\n")
                     .append("return false; });\n")
                 .append("});\n")
                 .append("topic.subscribe(\"eFaps/addRow/transactionPositionDebitTable\", function(){\n")
                    .append("query(\"select[name^=\\\"labelLink\\\"]\").some(function(node){\n")
                         .append("query(\"select[name^=\\\"labelLink\\\"]\").forEach(function(node2){\n")
                             .append("node2.value=node.value\n")
                          .append("});\n")
                     .append("return false; });\n")
                 .append("});\n");

            js.append(InterfaceUtils.wrapInDojoRequire(_parameter, labelJs, DojoLibs.TOPIC, DojoLibs.QUERY));
        }

        if (js.length() > 0) {
            ret.put(ReturnValues.SNIPLETT, InterfaceUtils.wrappInScriptTag(_parameter, js, true, 0));
        }
        return ret;
    }

    /**
     * Gets the value sum.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return the value sum
     * @throws EFapsException on error
     */
    public Return getValueSum(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();

        final QueryBuilder queryBldr = getQueryBldrFromProperties(_parameter);
        final MultiPrintQuery multi = queryBldr.getPrint();
        multi.addAttribute(CIAccounting.TransactionPositionAbstract.Amount);
        multi.execute();

        BigDecimal value = BigDecimal.ZERO;

        while (multi.next()) {
            final BigDecimal amount = multi.<BigDecimal>getAttribute(CIAccounting.TransactionPositionAbstract.Amount);
            value = value.add(amount.abs());
        }
        ret.put(ReturnValues.VALUES, value);
        return ret;
    }

    /**
     * Pos select field value. Renders a checkbox with an hidden input.
     *
     * @param _parameter the parameter
     * @return the return
     * @throws EFapsException the eFaps exception
     */
    public Return posSelectFieldValue(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final IUIValue fieldValue = (IUIValue) _parameter.get(ParameterValues.UIOBJECT);

        final StringBuilder html = new StringBuilder()
            .append("<input type=\"hidden\" name=\"").append(fieldValue.getField().getName()).append("\"/>");

        final StringBuilder js = new StringBuilder()
            .append("query(\"input[name^='posSelect_']\").forEach(function (node) {")
            .append("if (node.nextSibling.tagName != \"DIV\") {")
            .append("var nn = domConstruct.create(\"div\");")
            .append("domConstruct.place(nn, node, \"after\");")
            .append("var checkBox = new CheckBox({")
            .append("name: \"checkBox\",")
            .append("checked: false,")
            .append("onChange: function(b){ ")
            .append("b ? node.value = \"true\" : node.value = \"false\";")
            .append("}")
            .append("}, nn).startup();")
            .append("}")
            .append("});");
        html.append(InterfaceUtils.wrappInScriptTag(_parameter,
                        InterfaceUtils.wrapInDojoRequire(_parameter, js, DojoLibs.QUERY, DojoLibs.DOMCONSTRUCT,
                                        DojoLibs.CHECKBOX), true, 10));
        ret.put(ReturnValues.SNIPLETT, html.toString());
        return ret;
    }
}
