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


package org.efaps.esjp.accounting.transaction;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.efaps.admin.common.SystemConfiguration;
import org.efaps.admin.datamodel.Classification;
import org.efaps.admin.datamodel.Dimension;
import org.efaps.admin.datamodel.Dimension.UoM;
import org.efaps.admin.datamodel.Status;
import org.efaps.admin.datamodel.Type;
import org.efaps.admin.datamodel.ui.FieldValue;
import org.efaps.admin.datamodel.ui.UIInterface;
import org.efaps.admin.dbproperty.DBProperties;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Parameter.ParameterValues;
import org.efaps.admin.event.Return;
import org.efaps.admin.event.Return.ReturnValues;
import org.efaps.admin.program.esjp.EFapsRevision;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.admin.ui.AbstractUserInterfaceObject.TargetMode;
import org.efaps.admin.ui.field.Field.Display;
import org.efaps.ci.CIAttribute;
import org.efaps.ci.CIType;
import org.efaps.db.Context;
import org.efaps.db.Instance;
import org.efaps.db.InstanceQuery;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.PrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.esjp.accounting.Periode;
import org.efaps.esjp.accounting.report.DocumentDetailsReport;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.ci.CIContacts;
import org.efaps.esjp.ci.CIERP;
import org.efaps.esjp.ci.CIProducts;
import org.efaps.esjp.ci.CISales;
import org.efaps.esjp.common.jasperreport.AbstractDynamicReport;
import org.efaps.esjp.common.uiform.Field;
import org.efaps.esjp.erp.CurrencyInst;
import org.efaps.esjp.erp.RateInfo;
import org.efaps.esjp.sales.payment.AbstractPaymentDocument;
import org.efaps.util.EFapsException;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;


/**
 * TODO comment!
 *
 * @author The eFaps Team
 * @version $Id$
 */
@EFapsUUID("5075c0e0-e95a-418c-864b-3a90c1dac404")
@EFapsRevision("$Rev$")
public abstract class FieldValue_Base
    extends Transaction
{
    /**
     * Key for the map to be stored in the request.
     */
    public static final String SALESACC_REQKEY = "org.efaps.esjp.accounting.transaction.FieldValue.reqMap4SalesAccount";

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
                final Instance periodInst = new Periode().evaluateCurrentPeriod(_parameter);
                _queryBldr.addWhereAttrEqValue(CIAccounting.ReportSubJournal.PeriodeLink, periodInst.getId());
            }
        };
        return field.dropDownFieldValue(_parameter);
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
        };
        return field.dropDownFieldValue(_parameter);
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
        final Map<?, ?> properties = (Map<?, ?>) _parameter.get(ParameterValues.PROPERTIES);
        final String type = (String) properties.get("Type");
        if (type != null) {
            final Instance periodeInstance = new Periode().evaluateCurrentPeriod(_parameter);
            _parameter.put(ParameterValues.INSTANCE, periodeInstance);
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

                    for (final DropDownPosition pos : _values) {
                        final String strTmp = pos.getOption().toString();
                        if (StringUtils.endsWith(strTmp, trueStr)) {
                            pos.setOption(StringUtils.substringBeforeLast(strTmp, trueStr) + cross);
                        } else {
                            pos.setOption(StringUtils.substringBeforeLast(strTmp, falseStr) + net);
                        }
                    }
                };
            };
            ret = field.dropDownFieldValue(_parameter);
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
        Instance inst = _parameter.getCallInstance();
        if (!inst.getType().getUUID().equals(CIAccounting.Periode)) {
            inst = new Periode().evaluateCurrentPeriod(_parameter);
        }
        final String baseCurName = new Periode().getCurrency(inst).getName();
        ret.put(ReturnValues.VALUES, baseCurName);
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
        final Map<?, ?> properties = (Map<?, ?>) _parameter.get(ParameterValues.PROPERTIES);
        final String type = (String) properties.get("Type");
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
        final Map<String, Long> values = new TreeMap<String, Long>();
        while (print.next()) {
            values.put(print.<String>getAttribute(CIERP.DocumentType.Name)
                            + " - " + print.<String>getAttribute(CIERP.DocumentType.Description),
                       print.<Long>getAttribute(CIERP.DocumentType.ID));
        }

        final StringBuilder html = new StringBuilder();
        final FieldValue fieldValue = (FieldValue) _parameter.get(ParameterValues.UIOBJECT);

        html.append("<select name=\"").append(fieldValue.getField().getName()).append("\" ")
                        .append(UIInterface.EFAPSTMPTAG).append(" size=\"1\">");
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
    public Return getCurrencySelectFieldValue(final Parameter _parameter)
        throws EFapsException
    {
        Instance inst = _parameter.getCallInstance();
        if (!inst.getType().getUUID().equals(CIAccounting.Periode.uuid)) {
            inst = new Periode().evaluateCurrentPeriod(_parameter);
        }
        final Instance baseCur = new Periode().getCurrency(inst).getInstance();
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
        return field.dropDownFieldValue(_parameter);
    }

    /**
     * Get the value for the description field for transaction for documents.
     * @param _parameter Paremter as passed from the eFaps API
     * @return Return
     * @throws EFapsException on error
     */
    public Return getDescriptionFieldValue(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final String selected = Context.getThreadContext().getParameter("selectedRow");
        if (selected != null) {
            ret.put(ReturnValues.VALUES, getDescription(_parameter, Instance.get(selected)));
        }
        return ret;
    }

    /**
     * Get the value for the description field for transaction for documents.
     * @param _parameter Parameter as passed from the eFaps API
     * @param _instance instance of the document
     * @return description
     * @throws EFapsException on error
     */
    public String getDescription(final Parameter _parameter,
                                 final Instance _instance)
        throws EFapsException
    {
        final StringBuilder ret = new StringBuilder();
        if (_instance.isValid()) {
            final PrintQuery print = new PrintQuery(_instance);
            print.addAttribute(CIERP.DocumentAbstract.Name, CIERP.DocumentAbstract.Date);
            print.execute();
            final DateTime datetime = print.<DateTime>getAttribute(CIERP.DocumentAbstract.Date);
            final DateTimeFormatter formatter = DateTimeFormat.mediumDate();
            final String dateStr = datetime.withChronology(Context.getThreadContext().getChronology()).toString(
                            formatter.withLocale(Context.getThreadContext().getLocale()));
            final String name = print.<String>getAttribute(CIERP.DocumentAbstract.Name);
            ret.append(_instance.getType().getLabel()).append(" ").append(name).append(" ").append(dateStr);
        }
        return ret.toString();
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
        final StringBuilder html = new StringBuilder();
        final String selected = Context.getThreadContext().getParameter("selectedRow");
        final org.efaps.admin.datamodel.ui.FieldValue fieldValue = (FieldValue) _parameter.get(
                        ParameterValues.UIOBJECT);
        final Instance docInst = Instance.get(selected);
        if (docInst.isValid()) {
            final DocumentInfo doc = new DocumentInfo(docInst);
            final DateTime date = _parameter.getParameterValue("date") != null
                ? new DateTime(_parameter.getParameterValue("date"))
                : new DateTime().withTime(0, 0, 0, 0);
            html.append("<span name=\"").append(fieldValue.getField().getName()).append("_span\">")
                .append(getDocumentFieldValue(_parameter, doc))
                .append(getCostInformation(_parameter, date, doc))
                .append("<script type=\"text/javascript\">")
                .append(getScript(_parameter, doc))
                .append("</script>")
                .append("</span>");
        }
        ret.put(ReturnValues.SNIPLETT, html.toString());
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
        final String selected = Context.getThreadContext().getParameter("selectedRow");
        final org.efaps.admin.datamodel.ui.FieldValue fieldValue = (FieldValue) _parameter
                        .get(ParameterValues.UIOBJECT);
        final Instance docInst = Instance.get(selected);
        if (docInst.isValid()) {
            html.append("<span name=\"").append(fieldValue.getField().getName()).append("_span\">")
                            .append(getDocDetail(_parameter, docInst))
                            .append("</span>");
        }
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
     * @param _parameter    Parameter as passed from eFaps to an esjp
     * @param _doc          Document
     * @return StringBuilder
     * @throws EFapsException on error
     */
    protected StringBuilder getDocumentFieldValue(final Parameter _parameter,
                                                  final DocumentInfo _doc)
        throws EFapsException
    {
        final boolean showNetTotal = !"true".equalsIgnoreCase(getProperty(_parameter, "noNetTotal"));
        _doc.setFormater(getFormater(2, 2));

        final StringBuilder html = new StringBuilder();
        //Selects
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

        final PrintQuery print = new PrintQuery(_doc.getInstance());
        if (_doc.isSumsDoc()) {
            print.addAttribute(CISales.DocumentSumAbstract.CrossTotal,
                               CISales.DocumentSumAbstract.NetTotal,
                               CISales.DocumentSumAbstract.RateCrossTotal,
                               CISales.DocumentSumAbstract.RateNetTotal);
            print.addSelect(rateLabelSel, currSymbSel, rateCurrSymbSel);
        }
        if (_doc.isPaymentDoc()) {
            print.addAttribute(CISales.PaymentDocumentAbstract.Note, CISales.PaymentDocumentAbstract.Amount);
            print.addSelect(rateLabelSel, currSymbSel4Pay, rateCurrSymbSel4Pay);
        }

        print.addAttribute(CISales.DocumentAbstract.Name,
                           CISales.DocumentAbstract.Date,
                           CISales.DocumentAbstract.StatusAbstract);
        print.addSelect(accDescSel, accNameSel, accInstSel, contNameSel);
        print.execute();

        final DateTime datetime = print.<DateTime>getAttribute(CISales.DocumentAbstract.Date);
        _doc.setDate(datetime);

        //Basic information for all documents
        final String name = print.<String>getAttribute(CISales.DocumentAbstract.Name);
        final String contactName = print.<String>getSelect(contNameSel);
        final Long statusId = print.<Long>getAttribute(CISales.DocumentAbstract.StatusAbstract);
        final AccountInfo account = new AccountInfo(print.<Instance>getSelect(accInstSel));
        if (account.getInstance() == null) {
            setAccounts4Debit(_parameter, _doc);
        }

        html.append("<input type=\"hidden\" name=\"document\" value=\"").append(_doc.getInstance().getOid())
            .append("\"/>")
            .append("<table>").append("<tr>")
            .append("<td colspan=\"4\">").append(_doc.getInstance().getType().getLabel())
            .append("</td>").append("</tr><tr>")
            .append("<td>").append(getLabel(_doc.getInstance(), CISales.DocumentAbstract.Name))
            .append("</td>").append("<td>").append(name).append("</td>")
            .append("<td>").append(getLabel(_doc.getInstance(), CISales.DocumentAbstract.Date))
            .append("</td>").append("<td>").append(_doc.getDateString()).append("</td>")
            .append("</tr><tr>")
            .append("<td>").append(getLabel(_doc.getInstance(), CISales.DocumentAbstract.Contact))
            .append("</td>")
            .append("<td colspan=\"3\">").append(contactName);

        if (_doc.getDebtorAccount() != null) {
            html.append(" -> ")
                .append(CIAccounting.AccountCurrentDebtor.getType().getLabel())
                .append(": ").append(_doc.getDebtorAccount().getName());
        }

        html.append("</td>")
            .append("</tr><tr>")
            .append("<td>").append(getLabel(_doc.getInstance(), "Status"))
            .append("</td><td colspan=\"").append(_doc.isSumsDoc() || _doc.isPaymentDoc() ? 1 : 3).append("\">")
            .append(Status.get(statusId).getLabel()).append("</td>");

        if (_doc.isSumsDoc()) {
            final BigDecimal crossTotal = print.<BigDecimal>getAttribute(CISales.DocumentSumAbstract.CrossTotal);
            final BigDecimal netTotal = print.<BigDecimal>getAttribute(CISales.DocumentSumAbstract.NetTotal);
            final BigDecimal rateCrossTot = print.<BigDecimal>getAttribute(CISales.DocumentSumAbstract.RateCrossTotal);
            final BigDecimal rateNetTotal = print.<BigDecimal>getAttribute(CISales.DocumentSumAbstract.RateNetTotal);
            final String currSymbol = print.<String>getSelect(currSymbSel);
            final String rateCurrSymbol = print.<String>getSelect(rateCurrSymbSel);
            final BigDecimal rate = print.<BigDecimal>getSelect(rateLabelSel);

            html.append("<td>").append(getLabel(_doc.getInstance(), CISales.DocumentSumAbstract.Rate))
                .append("</td><td>").append(rate).append("</td>")
                .append("</tr>");
            if (showNetTotal) {
                html.append("<tr>")
                    .append("<td>").append(getLabel(_doc.getInstance(), CISales.DocumentSumAbstract.NetTotal))
                    .append("</td>").append("<td>")
                    .append(getFormater(2, 2).format(netTotal)).append(" ").append(currSymbol).append("</td>")
                    .append("<td>")
                    .append(getLabel(_doc.getInstance(), CISales.DocumentSumAbstract.RateNetTotal))
                    .append("</td>").append("<td>")
                    .append(getFormater(2, 2).format(rateNetTotal)).append(" ").append(rateCurrSymbol).append("</td>")
                    .append("</tr>");
            }
            html.append("<tr>")
                .append("<td>")
                .append(getLabel(_doc.getInstance(), CISales.DocumentSumAbstract.CrossTotal))
                .append("</td>").append("<td>")
                .append(getFormater(2, 2).format(crossTotal)).append(" ").append(currSymbol).append("</td>")
                .append("<td>")
                .append(getLabel(_doc.getInstance(), CISales.DocumentSumAbstract.RateCrossTotal))
                .append("</td>").append("<td>")
                .append(getFormater(2, 2).format(rateCrossTot)).append(" ").append(rateCurrSymbol).append("</td>")
                .append("</tr>");
        }
        if (_doc.isPaymentDoc()) {
            final BigDecimal amount = print.<BigDecimal>getAttribute(CISales.PaymentDocumentAbstract.Amount);
            final String rateCurrSymbol = print.<String>getSelect(rateCurrSymbSel4Pay);
            final BigDecimal rate = print.<BigDecimal>getSelect(rateLabelSel);

            html.append("<td>").append(getLabel(_doc.getInstance(), CISales.DocumentSumAbstract.Rate))
                .append("</td><td>").append(rate).append("</td>")
                .append("</tr>").append("<tr>").append("<td>")
                .append(getLabel(_doc.getInstance(), CISales.PaymentDocumentAbstract.Amount))
                .append("</td>").append("<td colspan=\"3\">")
                .append(getFormater(2, 2).format(amount)).append(" ").append(rateCurrSymbol).append("</td>")
                .append("<tr>").append("<td>")
                .append(getLabel(_doc.getInstance(), CISales.PaymentDocumentAbstract.Note))
                .append("</td>").append("<td colspan=\"3\">")
                .append(print.getAttribute(CISales.PaymentDocumentAbstract.Note)).append("</td>")
                .append("</tr><tr><td colspan=\"4\">")
                .append(AbstractPaymentDocument.getTransactionHtml(_parameter, _doc.getInstance()))
                .append("</td></tr>");

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

                final PrintQuery print2 = new PrintQuery(docInst);
                print2.addAttribute(CISales.DocumentSumAbstract.RateCrossTotal, CISales.DocumentSumAbstract.Name);
                print2.addSelect(selCurInst);
                print2.execute();

                final Instance curInst = print2.<Instance>getSelect(selCurInst);
                final BigDecimal rateCross = print2.<BigDecimal>getAttribute(
                                CISales.DocumentSumAbstract.RateCrossTotal);
                final String docName = print2.<String>getAttribute(CISales.DocumentSumAbstract.Name);

                html.append("<tr><td>").append(docInst.getType().getLabel())
                    .append("</td><td>").append(docName).append("</td>")
                    .append("<td>").append(getLabel(docInst, CISales.DocumentSumAbstract.RateCrossTotal))
                    .append("</td><td>").append(rateCross).append(new CurrencyInst(curInst).getSymbol())
                    .append("</td></tr>");
            }
        }
        html.append(getDocInformation(_parameter, _doc))
            .append("</table>");
        return html;
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
            final Instance periodInst = new Periode().evaluateCurrentPeriod(_parameter);
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
                    html.append("<td>").append(getFormater(2, 2).format(price)).append("</td>")
                         .append("<td>").append(rateTmp.getCurrencyInst().getSymbol()).append("</td>");

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
                .append("<td colspan=4></td><td>").append(getFormater(2, 2).format(total))
                .append("<input type=\"hidden\" name=\"amountExternal\" value=\"").append(total).append("\"/>")
                .append("</td>")
                .append("<td>").append(rate.getCurrencyInst().getSymbol())
                .append("<input type=\"hidden\" name=\"currencyExternal\" value=\"")
                .append(rate.getInstance4Currency().getId()).append("\"/>")
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
        final Instance periode = new Periode().evaluateCurrentPeriod(_parameter);
        while (multi.next()) {
            final BigDecimal net = multi.<BigDecimal>getAttribute(CISales.PositionSumAbstract.NetPrice);
            final BigDecimal cross = multi.<BigDecimal>getAttribute(CISales.PositionSumAbstract.CrossPrice);
            final Object[] ratePos = multi.<Object[]>getAttribute(CISales.PositionSumAbstract.Rate);
            final BigDecimal newRatepos = ((BigDecimal) ratePos[0]).divide((BigDecimal) ratePos[1], 12,
                        BigDecimal.ROUND_HALF_UP);
            final BigDecimal taxAmount = cross.subtract(net).multiply(newRatepos);
            final BigDecimal prodAmount = net.multiply(newRatepos);
            analyzeTax(_doc, false, multi.<Instance>getSelect(selTaxInst), taxAmount);
            final RateInfo rate = evaluateRate(_parameter, periode, _doc.getDate(), _doc.getRateCurrInst());
            analyzeProduct(_doc, false, multi.<Instance>getSelect(selProdInst), prodAmount, rate,
                           CIAccounting.AccountIncomeStatementRevenue2ProductClass,
                           CIAccounting.AccountIncomeStatementRevenue);
        }
    }

    /**
     * Get the script to set the values for debit and credit on opening of the
     * form.
     *
     * @param _parameter        Parameter as passed from the eFaps API
     * @param _doc              Instance of the Document the form was opened for
     * @return StringBuilder
     * @throws EFapsException on error
     */
    protected StringBuilder getScript(final Parameter _parameter,
                                      final DocumentInfo _doc)
        throws EFapsException
    {
        final Map<?, ?> props = (Map<?, ?>) _parameter.get(ParameterValues.PROPERTIES);
        final boolean script = !"true".equalsIgnoreCase((String) props.get("noScript"));
        _doc.setInvert(_doc.getInstance().getType().isKindOf(CISales.ReturnSlip.getType()));
        final StringBuilder ret = new StringBuilder();
        if (script && !_doc.getDebitAccounts().isEmpty() && !_doc.getCreditAccounts().isEmpty()) {
            if (_doc.isSumsDoc()) {
                getPriceInformation(_parameter, _doc);
            }

            for (final AccountInfo account : _doc.getDebitAccounts()) {
                account.setLink(getLinkString(account.getInstance(), "_Debit"));
            }
            for (final AccountInfo account : _doc.getCreditAccounts()) {
                account.setLink(getLinkString(account.getInstance(), "_Credit"));
            }
            ret.append(getTableJS(_parameter, "Debit", _doc.getDebitAccounts()))
                .append(getTableJS(_parameter, "Credit", _doc.getCreditAccounts()));
        }
        return ret;
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
                        final AccountInfo account = new AccountInfo().setInstance(accInst).add(_amount);
                        account.setRateInfo(_rate);
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
        final StringBuilder html = new StringBuilder();
        final FieldValue field = (FieldValue) _parameter.get(ParameterValues.UIOBJECT);
        if (field.getDisplay().equals(Display.EDITABLE)) {
            final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.LabelProject);
            queryBldr.addWhereAttrEqValue(CIAccounting.LabelProject.Active, true);
            final MultiPrintQuery print = queryBldr.getPrint();
            print.addAttribute(CIAccounting.LabelProject.OID,
                               CIAccounting.LabelProject.Name,
                               CIAccounting.LabelProject.Description);
            print.execute();
            final Map<String, String> values = new TreeMap<String, String>();
            while (print.next()) {
                values.put(print.<String>getAttribute(CIAccounting.LabelProject.Name) + " - "
                                + print.<String>getAttribute(CIAccounting.LabelProject.Description),
                                print.<String>getAttribute(CIAccounting.LabelProject.OID));
            }

            final Set<String> oidDoc = new HashSet<String>();
            oidDoc.addAll(getSelectedLabel(_parameter));


            final FieldValue fieldValue = (FieldValue) _parameter.get(ParameterValues.UIOBJECT);

            html.append("<select name=\"").append(fieldValue.getField().getName()).append("\" ")
                            .append(UIInterface.EFAPSTMPTAG).append(" size=\"1\">");
            html.append("<option value=\"0\"></option>");
            for (final Entry<String, String> entry : values.entrySet()) {
                html.append("<option value=\"").append(entry.getValue()).append("\">");
                String value = entry.getKey();
                if (oidDoc.contains(entry.getValue())) {
                    value = "> " + value;
                }
                html.append(StringEscapeUtils.escapeHtml4(value)).append("</option>");
            }
            html.append("</select>");
        }

        final Return ret = new Return();
        ret.put(ReturnValues.SNIPLETT, html.toString());
        return ret;
    }

    /**
     * To be overwritten from implementation.
     *
     * @param _parameter Parameter as passed from the eFaps API
     * @return Collection to be added as additional information to the doc table
     * @throws EFapsException on error
     */
    protected Collection<String> getSelectedLabel(final Parameter _parameter)
        throws EFapsException
    {
        return Collections.<String>emptyList();
    }

    /**
     * To be overwritten from implementation.
     *
     * @param _parameter Parameter as passed from the eFaps API
     * @param _document  Document Object
     * @return CharSequence to be added as additional information to the doc
     *         table
     * @throws EFapsException on error
     */
    protected CharSequence getDocInformation(final Parameter _parameter,
                                                      final DocumentInfo _document)
        throws EFapsException
    {
        return "";
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
            final String selected = Context.getThreadContext().getParameter("selectedRow");
            final Instance docInst = Instance.get(selected);
            DateTime date = new DateTime();
            if (docInst.isValid()) {
                final PrintQuery print = new PrintQuery(docInst);
                print.addAttribute(CISales.DocumentAbstract.Date);
                print.executeWithoutAccessCheck();
                date = print.<DateTime>getAttribute(CISales.DocumentAbstract.Date);
            } else if (_parameter.getInstance() != null && _parameter.getInstance().isValid()) {
                final FieldValue fieldValue = (FieldValue) _parameter.get(ParameterValues.UIOBJECT);
                final Object objTmp = fieldValue.getValue();
                if (objTmp != null && objTmp instanceof DateTime) {
                    date = (DateTime) objTmp;
                }
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
        final FieldValue fieldValue = (FieldValue) _parameter.get(ParameterValues.UIOBJECT);
        if (!Display.NONE.equals(fieldValue.getDisplay())) {
            @SuppressWarnings("unchecked")
            Map<Instance, String> values = (Map<Instance, String>) Context.getThreadContext()
                            .getRequestAttribute(FieldValue_Base.SALESACC_REQKEY);
            if (values == null || (values != null && !values.containsKey(_parameter.getInstance()))) {
                values = new HashMap<Instance, String>();
                Context.getThreadContext().setRequestAttribute(FieldValue_Base.SALESACC_REQKEY, values);
                @SuppressWarnings("unchecked")
                final List<Instance> instances = (List<Instance>) _parameter.get(ParameterValues.REQUEST_INSTANCES);
                if (instances != null) {
                    final MultiPrintQuery multi = new MultiPrintQuery(instances);
                    final SelectBuilder selPayment = new SelectBuilder()
                                    .linkfrom(CISales.Payment, CISales.Payment.CreateDocument).instance();
                    multi.addSelect(selPayment);
                    multi.executeWithoutAccessCheck();
                    while (multi.next()) {
                        final Instance payment = multi.<Instance>getSelect(selPayment);
                        final PrintQuery print = new PrintQuery(payment);
                        final SelectBuilder selAccount = new SelectBuilder()
                                    .linkfrom(CISales.TransactionAbstract, CISales.TransactionAbstract.Payment)
                                    .linkto(CISales.TransactionAbstract.Account)
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
}
