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

package org.efaps.esjp.accounting;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import org.efaps.admin.common.NumberGenerator;
import org.efaps.admin.common.SystemConfiguration;
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
import org.efaps.admin.ui.AbstractCommand;
import org.efaps.db.AttributeQuery;
import org.efaps.db.Context;
import org.efaps.db.Delete;
import org.efaps.db.Insert;
import org.efaps.db.Instance;
import org.efaps.db.InstanceQuery;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.PrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.db.Update;
import org.efaps.esjp.accounting.transaction.Transaction_Base;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.ci.CIERP;
import org.efaps.esjp.ci.CISales;
import org.efaps.esjp.erp.CurrencyInst;
import org.efaps.esjp.sales.Calculator_Base;
import org.efaps.esjp.sales.PriceUtil;
import org.efaps.esjp.sales.payment.AbstractPaymentDocument;
import org.efaps.util.DateTimeUtil;
import org.efaps.util.EFapsException;
import org.efaps.util.cache.CacheReloadException;
import org.joda.time.DateMidnight;
import org.joda.time.DateTime;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 * @version $Id: Payment_Base.java 7671 2012-06-14 17:25:53Z
 *          jorge.cueva@moxter.net $
 */
@EFapsUUID("ca10ea64-78fc-4cfc-91a6-1d346dddd886")
@EFapsRevision("$Rev$")
public abstract class Payment_Base
    extends AbstractPaymentDocument
{
    public static final String OPENAMOUNT_SESSIONKEY = "eFaps_SalesPayment_OpenAmount_SessionKey";

    @Override
    protected String getCode4GeneratedDocWithSysConfig(final Parameter _parameter)
        throws EFapsException
    {
        String ret = "";
        // Sales-Configuration
        final SystemConfiguration config = SystemConfiguration.get(
                        UUID.fromString("c9a1cbc3-fd35-4463-80d2-412422a3802f"));
        if (config != null) {
            final boolean active = config.getAttributeValueAsBoolean("ActivateCode4PaymentDocumentOut");
            if (active) {
                // Sales_PaymentDocumentAbstractOutSequence
                ret = NumberGenerator.get(UUID.fromString("c930aeab-31ad-47d9-9aa4-fbf803a472b2")).getNextVal();
            }
        }
        return !ret.isEmpty() ? ret : null;
    }

    public Return createPaymentOut(final Parameter _parameter)
        throws EFapsException
    {
        Return ret = new Return();
        Instance docInst = null;
        if (_parameter.getInstance() != null) {
            docInst = _parameter.getInstance();
        } else if (_parameter.getParameterValue("createdocument") != null) {
            docInst = Instance.get(_parameter.getParameterValue("createdocument"));
        }

        if (docInst != null && docInst.isValid()) {
            final String dateStr = _parameter.getParameterValue("date");

            final String[] amounts = _parameter.getParameterValues("amount");
            final String[] currencies = _parameter.getParameterValues("currency");
            final String[] paymentType = _parameter.getParameterValues("paymentType");
            final String[] descriptions = _parameter.getParameterValues("description");
            final String[] accounts = _parameter.getParameterValues("account");

            for (int i = 0; i < amounts.length; i++) {
                final CreatedDoc createdDoc = new CreatedDoc();
                final String targetTypeId = paymentType[i];
                Instance targetDocInst = null;
                if (targetTypeId != null && !targetTypeId.isEmpty()) {
                    // Sales-Configuration
                    final Instance baseInst = SystemConfiguration.get(
                                    UUID.fromString("c9a1cbc3-fd35-4463-80d2-412422a3802f")).getLink("CurrencyBase");
                    final Instance newInst = Instance.get(CIERP.Currency.getType(), currencies[i]);
                    final CurrencyInst currency = new CurrencyInst(newInst);
                    final PriceUtil util = new PriceUtil();
                    final BigDecimal[] rate = util.getExchangeRate(util.getDateFromParameter(_parameter), newInst);
                    final Object[] rateObj = new Object[] { currency.isInvert() ? BigDecimal.ONE : rate[1],
                                    currency.isInvert() ? rate[1] : BigDecimal.ONE };
                    final Type targetType = Type.get(Long.valueOf(targetTypeId));
                    final Status status = Status.find(targetType.getStatusAttribute().getLink().getUUID(), "Open");
                    final Insert targetDocIns = new Insert(targetType);
                    final PrintQuery docPrint = new PrintQuery(docInst);
                    docPrint.addAttribute(CIERP.DocumentAbstract.Contact, CIERP.DocumentAbstract.Name);
                    docPrint.execute();
                    targetDocIns.add(CISales.PaymentDocumentOutAbstract.StatusAbstract, status.getId());
                    targetDocIns.add(CISales.PaymentDocumentOutAbstract.Contact,
                                    docPrint.getAttribute(CIERP.DocumentAbstract.Contact));
                    targetDocIns.add(CISales.PaymentDocumentOutAbstract.Name,
                                    docPrint.getAttribute(CIERP.DocumentAbstract.Name));
                    final String code = getCode4GeneratedDocWithSysConfig(_parameter);
                    if (code != null) {
                        targetDocIns.add(CISales.PaymentDocumentOutAbstract.Code, code);

                    }
                    targetDocIns.add(CISales.PaymentDocumentOutAbstract.Date, dateStr);
                    targetDocIns.add(CISales.PaymentDocumentOutAbstract.CurrencyLink, baseInst.getId());
                    targetDocIns.add(CISales.PaymentDocumentOutAbstract.Amount, amounts[i]);
                    targetDocIns.add(CISales.PaymentDocumentOutAbstract.RateCurrencyLink, currencies[i]);
                    targetDocIns.add(CISales.PaymentDocumentOutAbstract.Rate, rateObj);

                    createdDoc.getValues().put(getFieldName4Attribute(_parameter, CISales.PaymentDocumentOutAbstract.Name.name),
                                    docPrint.getAttribute(CIERP.DocumentAbstract.Name));
                    createdDoc.getValues().put(getFieldName4Attribute(_parameter, CISales.PaymentDocumentOutAbstract.Contact.name),
                                    docPrint.getAttribute(CIERP.DocumentAbstract.Contact));
                    createdDoc.getValues().put(getFieldName4Attribute(_parameter, CISales.PaymentDocumentOutAbstract.Code.name), code);
                    createdDoc.getValues().put(getFieldName4Attribute(_parameter, CISales.PaymentDocumentOutAbstract.Rate.name), rateObj);
                    createdDoc.getValues().put(getFieldName4Attribute(_parameter, CISales.PaymentDocumentOutAbstract.CurrencyLink.name),
                                    baseInst.getId());
                    createdDoc.getValues().put(getFieldName4Attribute(_parameter, CISales.PaymentDocumentOutAbstract.Date.name), dateStr);
                    createdDoc.getValues().put(
                                    getFieldName4Attribute(_parameter, CISales.PaymentDocumentOutAbstract.RateCurrencyLink.name),
                                    currencies[i]);
                    createdDoc.getValues().put(getFieldName4Attribute(_parameter, CISales.PaymentDocumentOutAbstract.Amount.name),
                                    amounts[i]);
                    targetDocIns.execute();
                    targetDocInst = targetDocIns.getInstance();
                    createdDoc.setInstance(targetDocInst);
                }

                final Insert insert = new Insert(CISales.Payment);
                insert.add(CISales.Payment.Date, dateStr);
                insert.add(CISales.Payment.CreateDocument, docInst.getId());
                if (targetDocInst != null) {
                    insert.add(CISales.Payment.TargetDocument, targetDocInst.getId());
                }
                insert.add(CISales.Payment.CurrencyLink, currencies[i]);
                insert.add(CISales.Payment.Amount, amounts[i]);
                insert.execute();
                final Instance paymentInst = insert.getInstance();

                final Insert transIns = new Insert(CISales.TransactionOutbound);
                transIns.add(CISales.TransactionOutbound.Amount, amounts[i]);
                transIns.add(CISales.TransactionOutbound.CurrencyId, currencies[i]);
                transIns.add(CISales.TransactionOutbound.Payment, ((Long) paymentInst.getId()).toString());
                transIns.add(CISales.TransactionOutbound.Description, descriptions[i].length() < 1
                                ? DBProperties.getProperty("Sales_Payment.Label")
                                : descriptions[i]);
                transIns.add(CISales.TransactionOutbound.Date, dateStr);
                transIns.add(CISales.TransactionOutbound.Account, accounts[i]);
                createdDoc.getValues().put(getFieldName4Attribute(_parameter, CISales.TransactionOutbound.Account.name),
                                accounts[i]);
                transIns.execute();

                ret = createReportDoc(_parameter, createdDoc);
            }
            // if the open amount is zero or less than 1 Cent.
            final BigDecimal amount = getOpenAmount(_parameter, docInst).getCrossTotal();
            if (amount.compareTo(BigDecimal.ZERO) == 0 || amount.abs().compareTo(new BigDecimal("0.01")) < 0) {
                final Update update = new Update(docInst);
                Status status = null;
                // Sales_IncomingInvoice
                if (docInst.getType().getUUID().equals(CISales.IncomingInvoice.uuid)) {
                    status = Status.find(CISales.IncomingInvoiceStatus.uuid, "Paid");
                    // Accounting_ExternalVoucherStatus
                } else if (docInst.getType().getUUID().equals(CIAccounting.ExternalVoucher.uuid)) {
                    status = Status.find(CIAccounting.ExternalVoucherStatus.uuid, "Paid");
                }
                update.add("Status", ((Long) status.getId()).toString());
                update.execute();
            }
        }
        return ret;
    }

    public OpenAmount getOpenAmount(final Parameter _parameter,
                                    final Instance _instance)
        throws EFapsException
    {
        final PrintQuery print = new PrintQuery(_instance);
        print.addAttribute(CISales.DocumentSumAbstract.RateCrossTotal,
                        CISales.DocumentSumAbstract.RateCurrencyId,
                        CISales.DocumentSumAbstract.Date);
        final SelectBuilder sel = new SelectBuilder().linkto(CISales.DocumentSumAbstract.RateCurrencyId)
                        .attribute(CIERP.Currency.Symbol);
        print.addSelect(sel);
        print.execute();
        final BigDecimal ratecrossTotal = print.<BigDecimal>getAttribute(CISales.DocumentSumAbstract.RateCrossTotal);
        print.<String>getSelect(sel);

        final Instance rateCurrInst = Instance.get(CIERP.Currency.getType(),
                        print.<Long>getAttribute(CISales.DocumentSumAbstract.RateCurrencyId));
        final DateTime rateDate = print.<DateTime>getAttribute(CISales.DocumentSumAbstract.Date);

        final QueryBuilder queryBldr = new QueryBuilder(CISales.Payment);
        queryBldr.addWhereAttrEqValue(CISales.Payment.CreateDocument, _instance.getId());
        final InstanceQuery query = queryBldr.getQuery();
        query.execute();
        final List<Instance> instances = query.getValues();
        BigDecimal paid = BigDecimal.ZERO;

        if (instances.size() > 0) {
            final SelectBuilder amountSel = new SelectBuilder()
                            .linkfrom(CISales.TransactionOutbound, CISales.TransactionOutbound.Payment)
                            .attribute(CISales.TransactionOutbound.Amount);
            final SelectBuilder currIdSel = new SelectBuilder()
                            .linkfrom(CISales.TransactionOutbound, CISales.TransactionOutbound.Payment)
                            .attribute(CISales.TransactionOutbound.CurrencyId);
            final SelectBuilder oidSel = new SelectBuilder()
                            .linkfrom(CISales.TransactionOutbound, CISales.TransactionOutbound.Payment)
                            .oid();

            final PriceUtil priceUtil = new PriceUtil();
            final MultiPrintQuery multi = new MultiPrintQuery(instances);
            multi.addSelect(amountSel, currIdSel, oidSel);
            multi.execute();
            final Set<String> oids = new HashSet<String>();
            while (multi.next()) {
                if (multi.isList4Select(amountSel.toString())) {
                    final List<BigDecimal> list = multi.<List<BigDecimal>>getSelect(amountSel);
                    final List<Long> currList = multi.<List<Long>>getSelect(currIdSel);
                    final List<String> oidList = multi.<List<String>>getSelect(oidSel);

                    final Iterator<Long> currIter = currList.iterator();
                    final Iterator<String> oidIter = oidList.iterator();
                    for (final BigDecimal pos : list) {
                        BigDecimal temp;
                        final Long currId = currIter.next();
                        final String oid = oidIter.next();
                        if (!oids.contains(oid)) {
                            oids.add(oid);
                            if (rateCurrInst.getId() == currId) {
                                temp = pos;
                            } else {
                                final BigDecimal[] rates = priceUtil.getRates(_parameter,
                                                rateCurrInst,
                                                Instance.get(CIERP.Currency.getType(), (currId).toString()));
                                temp = pos.multiply(rates[2]);
                            }
                            paid = paid.add(temp);
                        }
                    }
                } else {
                    final BigDecimal temp = multi.<BigDecimal>getSelect(amountSel);
                    final Long currId = multi.<Long>getSelect(currIdSel);
                    final String oid = multi.<String>getSelect(oidSel);

                    if (temp != null && !oids.contains(oid)) {
                        oids.add(oid);
                        if (rateCurrInst.getId() == currId) {
                            paid = paid.add(temp);
                        } else {
                            final BigDecimal[] rates = priceUtil.getRates(_parameter,
                                            rateCurrInst,
                                            Instance.get(CIERP.Currency.getType(), (currId).toString()));
                            paid = paid.add(temp.multiply(rates[2]));
                        }
                    }
                }
            }
        }
        return new OpenAmount(new CurrencyInst(rateCurrInst), ratecrossTotal.subtract(paid), rateDate);
    }

    /**
     * @param _parameter Parameter as passed from the eFaps API
     * @param _openAmount open Amount
     * @return StringBuilder for html snipplet
     * @throws EFapsException on error
     */
    protected StringBuilder getOpenAmountInnerHtml(final Parameter _parameter,
                                                   final OpenAmount _openAmount)
        throws EFapsException
    {
        final StringBuilder html = new StringBuilder();

        // Sales-Configuration
        final Instance baseCurrInst = SystemConfiguration.get(
                        UUID.fromString("c9a1cbc3-fd35-4463-80d2-412422a3802f")).getLink("CurrencyBase");
        final DecimalFormat formater = Calculator_Base.getFormatInstance();
        final PriceUtil priceUtil = new PriceUtil();

        final QueryBuilder queryBldr = new QueryBuilder(CIERP.Currency);
        final InstanceQuery query = queryBldr.getQuery();
        while (query.next()) {
            // only add other currencies to the list
            if (!_openAmount.getCurrencyInstance().equals(query.getCurrentValue())) {
                // if the base is part of the list put it on first place
                final BigDecimal[] rates = priceUtil.getRates(_parameter, query.getCurrentValue(),
                                _openAmount.getCurrencyInstance());

                final CurrencyInst currInst = new CurrencyInst(query.getCurrentValue());
                final StringBuilder row = new StringBuilder();
                row.append("<tr><td>").append(formater.format(_openAmount.getCrossTotal().multiply(rates[2])))
                                .append(" ").append(currInst.getSymbol())
                                .append("</td><td>").append(rates[2]).append("</td></tr>");
                if (query.getCurrentValue().equals(baseCurrInst)) {
                    html.insert(0, row);
                } else {
                    html.append(row);
                }
            }
        }
        html.insert(0, "<table>");
        html.append("</table>");
        return html;
    }

    /**
     * \ Method is used to set the Value for the open amount field on opening
     * the form.
     *
     * @param _parameter Parameter as passed from the efaps API
     * @return Snipplets with value for open amount field
     * @throws EFapsException on error
     */
    public Return getOpenAmountUI(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        Instance instance = null;
        if (_parameter.getCallInstance() != null) {
            instance = _parameter.getInstance();
        } else if (_parameter.getParameterValue("createdocument") != null) {
            instance = Instance.get(_parameter.getParameterValue("createdocument"));
        }
        final FieldValue fieldValue = (FieldValue) _parameter.get(ParameterValues.UIOBJECT);

        if (instance != null && instance.isValid()) {
            final OpenAmount openAmount = getOpenAmount(_parameter, instance);

            Context.getThreadContext().setSessionAttribute(Payment_Base.OPENAMOUNT_SESSIONKEY, openAmount);
            final StringBuilder html = new StringBuilder();
            html.append(Calculator_Base.getFormatInstance().format(openAmount.getCrossTotal())).append(" ")
                            .append(openAmount.getSymbol()).append("<hr/>")
                            .append("<span name=\"").append(fieldValue.getField().getName()).append("_table\">")
                            .append(getOpenAmountInnerHtml(_parameter, openAmount))
                            .append("</span>");

            ret.put(ReturnValues.SNIPLETT, html.toString());
        }
        return ret;
    }

    /**
     * Method is called as an update event on changes on one of the amount
     * fields.
     *
     * @param _parameter Parameter as passed from the efaps API
     * @return List of maps of the fields to be updated
     * @throws EFapsException on error
     */
    public Return updateFields4Amount(final Parameter _parameter)
        throws EFapsException
    {
        final Return retVal = new Return();
        retVal.put(ReturnValues.VALUES, getListMap(_parameter, false));
        return retVal;
    }

    /**
     * Get the list of maps for the field to be updated.
     *
     * @param _parameter Parameter as passed from the eFaps API
     * @param _updateRate mus the field with the rate also be updated
     * @return List of maps of the fields to be updated
     * @throws EFapsException on error
     */
    protected List<Map<String, String>> getListMap(final Parameter _parameter,
                                                   final boolean _updateRate)
        throws EFapsException
    {
        final List<Map<String, String>> list = new ArrayList<Map<String, String>>();
        final Map<String, String> map = new HashMap<String, String>();
        final String[] amounts = _parameter.getParameterValues("amount");
        final String[] currencies = _parameter.getParameterValues("currency");

        final DecimalFormat formatter = Calculator_Base.getFormatInstance();
        formatter.setMaximumFractionDigits(2);
        formatter.setMinimumFractionDigits(2);
        formatter.setRoundingMode(RoundingMode.HALF_UP);

        final Map<String, BigDecimal> curr2Rate = new HashMap<String, BigDecimal>();
        final Map<String, String> curr2Symb = new HashMap<String, String>();

        final OpenAmount openAmount = (OpenAmount) Context.getThreadContext().getSessionAttribute(
                        Payment_Base.OPENAMOUNT_SESSIONKEY);
        BigDecimal openTotalAmount = openAmount.getCrossTotal();
        final PriceUtil priceUtil = new PriceUtil();
        for (int i = 0; i < amounts.length; i++) {
            BigDecimal amount;
            try {
                amount = amounts[i].length() < 1
                                ? BigDecimal.ZERO.setScale(2) : (BigDecimal) formatter.parse(amounts[i]);
            } catch (final ParseException e) {
                throw new EFapsException(Payment_Base.class, "getListMap.ParseException", e);
            }

            final BigDecimal rate;
            if (curr2Rate.containsKey(currencies[i])) {
                rate = curr2Rate.get(currencies[i]);
            } else {
                final Instance currInst = Instance.get(CIERP.Currency.getType(), currencies[i]);
                final BigDecimal[] rates = priceUtil.getRates(_parameter, openAmount.getCurrencyInstance(), currInst);
                rate = rates[2];
                curr2Rate.put(currencies[i], rate);
                curr2Symb.put(currencies[i], new CurrencyInst(currInst).getSymbol());
            }
            openTotalAmount = openTotalAmount.subtract(amount.multiply(rate));
        }
        final StringBuilder value = new StringBuilder();

        for (final Entry<String, BigDecimal> entry : curr2Rate.entrySet()) {
            if (value.length() > 0) {
                value.append(" - ");
            }
            value.append(formatter.format(openTotalAmount.divide(entry.getValue(), BigDecimal.ROUND_HALF_UP)))
                            .append(" ").append(curr2Symb.get(entry.getKey()));
        }
        map.put("openAmountTotal", value.toString());
        // if the date was changed the rate and the original value must be
        // changed
        if (_updateRate) {
            final StringBuilder js = new StringBuilder();
            js.append("document.getElementsByName(\"openAmount_table\")[0].innerHTML=\"")
                            .append(getOpenAmountInnerHtml(_parameter, openAmount)).append("\"");
            map.put("eFapsFieldUpdateJS", js.toString());
        }

        list.add(map);
        return list;
    }

    /**
     * Method is called as an update event on changes in the date fields.
     *
     * @param _parameter Parameter as passed from the efaps API
     * @return List of maps of the fields to be updated
     * @throws EFapsException on error
     */
    public Return updateFields4Date(final Parameter _parameter)
        throws EFapsException
    {
        final Return retVal = new Return();
        retVal.put(ReturnValues.VALUES, getListMap(_parameter, true));
        return retVal;
    }

    /**
     * method for generate a new Payment.
     *
     * @param _parameter Parameter as passed from the eFaps API.
     * @return new Return.
     * @throws EFapsException on error.
     */
    public Return createPaymentPlanned(final Parameter _parameter)
        throws EFapsException
    {
        final AbstractCommand command = (AbstractCommand) _parameter.get(ParameterValues.UIOBJECT);
        final String namePrefix = _parameter.getParameterValue("namePrefix");
        final String fromNumber = _parameter.getParameterValue("fromNumber");
        final String toNumber = _parameter.getParameterValue("toNumber");
        final String nameSuffix = _parameter.getParameterValue("nameSuffix");
        final Map<?, ?> props = (Map<?, ?>) _parameter.get(ParameterValues.PROPERTIES);

        final String digitCountStr = (String) props.get("digitCount");
        final int digitCount = digitCountStr == null ? 6 : Integer.parseInt(digitCountStr);
        final long from = Long.parseLong(fromNumber);

        final long to;
        if (toNumber != null && !toNumber.isEmpty()) {
            to = Long.parseLong(toNumber);
        } else {
            to = from;
        }

        for (long num = from; num < to + 1; num++) {
            final DecimalFormat formater = new DecimalFormat();
            formater.setMaximumIntegerDigits(digitCount);
            formater.setMinimumIntegerDigits(digitCount);
            formater.setGroupingUsed(false);
            final StringBuilder nameBldr = new StringBuilder();
            nameBldr.append(namePrefix == null ? "" : namePrefix).append(formater.format(num))
                            .append(nameSuffix == null ? "" : nameSuffix);
            final Insert insert = new Insert(command.getTargetCreateType());
            insert.add(CIAccounting.PaymentDocumentPlanned.Name, nameBldr.toString());
            insert.add(CIAccounting.PaymentDocumentPlanned.StatusAbstract,
                            Status.find(CIAccounting.PaymentDocumentStatus.uuid, "Open").getId());
            insert.add(CIAccounting.PaymentDocumentPlanned.Date, new DateTime());
            insert.execute();

            final Instance periodeInst = Instance.get(_parameter.getParameterValue("periode"));
            if (periodeInst.isValid()) {
                final Instance accInst = Instance.get(_parameter.getParameterValue("account"));
                if (accInst.isValid() && _parameter.getParameterValue("accountAutoComplete") != null
                                && !_parameter.getParameterValue("accountAutoComplete").isEmpty()) {
                    Insert relInsert = null;
                    if (command.getTargetCreateType().isKindOf(CIAccounting.PaymentCheck.getType())) {
                        relInsert = new Insert(CIAccounting.Account2PaymentCheck);
                    } else {
                        relInsert = new Insert(CIAccounting.Account2PaymentLetter);
                    }
                    relInsert.add(CIAccounting.Account2PaymentAbstract.FromAbstractLink, accInst.getId());
                    relInsert.add(CIAccounting.Account2PaymentAbstract.ToAbstractLink, insert.getId());
                    relInsert.add(CIAccounting.Account2PaymentAbstract.Active, true);
                    relInsert.execute();
                }
            }
        }

        return new Return();
    }

    /**
     * Called from a field update event to get the html snipplet for a payment
     * field.
     *
     * @param _parameter Parameter as passed from the eFaps API
     * @return new Return
     * @throws EFapsException on error
     */
    public Return getPaymentFieldValue(final Parameter _parameter)
        throws EFapsException
    {
        final FieldValue fieldValue = (FieldValue) _parameter.get(ParameterValues.UIOBJECT);
        final Map<?, ?> props = (Map<?, ?>) _parameter.get(ParameterValues.PROPERTIES);
        final Instance periodeInstance = (Instance) Context.getThreadContext().getSessionAttribute(
                        Transaction_Base.PERIODE_SESSIONKEY);

        final QueryBuilder accQueryBldr = new QueryBuilder(CIAccounting.AccountAbstract);
        accQueryBldr.addWhereAttrEqValue(CIAccounting.AccountAbstract.PeriodeAbstractLink, periodeInstance.getId());
        final AttributeQuery accQuery = accQueryBldr.getAttributeQuery(CIAccounting.AccountAbstract.ID);

        final QueryBuilder payQueryBldr = new QueryBuilder(CIAccounting.PaymentDocumentAbstract);
        payQueryBldr.addWhereAttrEqValue(CIAccounting.PaymentDocumentAbstract.StatusAbstract,
                        Status.find(CIAccounting.PaymentDocumentStatus.uuid, "Open").getId());
        final AttributeQuery payQuery = payQueryBldr.getAttributeQuery(CIERP.PaymentDocumentAbstract.ID);

        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.Account2PaymentAbstract);
        queryBldr.addWhereAttrInQuery(CIAccounting.Account2PaymentAbstract.FromAbstractLink, accQuery);
        queryBldr.addWhereAttrInQuery(CIAccounting.Account2PaymentAbstract.ToAbstractLink, payQuery);

        final SelectBuilder accNameSel = new SelectBuilder()
                        .linkto(CIAccounting.Account2PaymentAbstract.FromAbstractLink)
                        .attribute(CIAccounting.AccountAbstract.Name);
        final SelectBuilder accDescSel = new SelectBuilder()
                        .linkto(CIAccounting.Account2PaymentAbstract.FromAbstractLink)
                        .attribute(CIAccounting.AccountAbstract.Description);
        final SelectBuilder payNameSel = new SelectBuilder()
                        .linkto(CIAccounting.Account2PaymentAbstract.ToAbstractLink)
                        .attribute(CIERP.PaymentDocumentAbstract.Name);
        final SelectBuilder payOidSel = new SelectBuilder()
                        .linkto(CIAccounting.Account2PaymentAbstract.ToAbstractLink).oid();

        final MultiPrintQuery multi = queryBldr.getPrint();
        multi.addSelect(accNameSel, accDescSel, payNameSel, payOidSel);
        multi.execute();

        final Map<String, List<String[]>> acc2Pay = new TreeMap<String, List<String[]>>();
        while (multi.next()) {
            final String accName = multi.<String>getSelect(accNameSel);
            final String accDesc = multi.<String>getSelect(accDescSel);
            final String payName = multi.<String>getSelect(payNameSel);
            final String payOid = multi.<String>getSelect(payOidSel);
            final String acc = accName + " - " + accDesc;
            List<String[]> pays;
            if (acc2Pay.containsKey(acc)) {
                pays = acc2Pay.get(acc);
            } else {
                pays = new ArrayList<String[]>();
                acc2Pay.put(acc, pays);
            }
            pays.add(new String[] { payName, payOid });
        }
        final String columnCountStr = (String) props.get("columnCount");
        final int columnCount = columnCountStr != null && !columnCountStr.isEmpty()
                        ? Integer.parseInt(columnCountStr) : 5;
        final StringBuilder html = new StringBuilder();
        html.append("<table>");
        for (final Entry<String, List<String[]>> entry : acc2Pay.entrySet()) {
            final int c = entry.getValue().size() / columnCount + (entry.getValue().size() % columnCount > 0 ? 1 : 0);
            html.append("<tr><td rowspan=\"").append(c).append("\">").append(entry.getKey())
                            .append("</td>");
            int i = 0;
            for (final String[] pay : entry.getValue()) {
                if (i < columnCount) {
                    html.append("<td>");
                } else {
                    html.append("</tr><tr><td>");
                    i = 0;
                }
                html.append("<input type=\"checkbox\" name=\"").append(fieldValue.getField().getName())
                                .append("\" value=\"").append(pay[1]).append("\" />")
                                .append(pay[0]).append("</td>");
                i++;
            }
            for (int j = i; j < columnCount; j++) {
                html.append("<td></td>");
            }
            i = 0;
        }
        html.append("</table>");
        final Return ret = new Return();
        ret.put(ReturnValues.SNIPLETT, html.toString());
        return ret;
    }

    /**
     * Called from a field value event to get the html snipplet for a dropdown
     * field.
     *
     * @param _parameter Parameter as passed from the eFaps API
     * @return new Return
     * @throws EFapsException on error
     */
    public Return periodeUIFieldValue(final Parameter _parameter)
        throws EFapsException
    {
        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.Periode);
        final MultiPrintQuery print = queryBldr.getPrint();
        print.addAttribute(CIAccounting.Periode.Name);
        print.execute();
        final Map<String, String> values = new TreeMap<String, String>();
        while (print.next()) {
            values.put(print.<String>getAttribute(CIAccounting.Periode.Name), print.getCurrentInstance().getOid());
        }
        final StringBuilder html = new StringBuilder();
        final FieldValue fieldValue = (FieldValue) _parameter.get(ParameterValues.UIOBJECT);

        html.append("<select name=\"").append(fieldValue.getField().getName()).append("\" ")
                        .append(UIInterface.EFAPSTMPTAG).append(" size=\"1\">")
                        .append("<option value=\"\">-</option>");
        for (final Entry<String, String> entry : values.entrySet()) {
            html.append("<option value=\"").append(entry.getValue())
                            .append("\">").append(entry.getKey()).append("</option>");
        }
        html.append("</select>");
        final Return ret = new Return();
        ret.put(ReturnValues.SNIPLETT, html.toString());
        return ret;
    }

    /**
     * Update the rate fields on change of the date value.
     *
     * @param _parameter Parameter as passed from the eFaps API
     * @return list needed for field update event
     * @throws EFapsException on error
     */
    public Return update4Periode(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final String periodeOid = _parameter.getParameterValue("periode");
        final Instance periodeInst = Instance.get(periodeOid);
        Context.getThreadContext().setSessionAttribute(Transaction_Base.PERIODE_SESSIONKEY,
                        periodeInst.isValid() ? periodeInst : null);
        return ret;
    }

    /**
     * Trigger for connect document to check and recalculate amount.
     *
     * @param _parameter Parameter as passed from the eFaps API.
     * @return new Return.
     * @throws EFapsException on error.
     */
    public Return updateAmount2Payment(final Parameter _parameter)
        throws EFapsException
    {
        final PrintQuery print = new PrintQuery(_parameter.getInstance());
        final SelectBuilder paySel = new SelectBuilder().linkto(CIERP.Document2PaymentDocumentAbstract.ToAbstractLink)
                        .attribute(CIERP.PaymentDocumentAbstract.OID);
        print.addSelect(paySel);
        print.execute();

        final Instance instPay = Instance.get(print.<String>getSelect(paySel));
        final Map<String, BigDecimal> map = new HashMap<String, BigDecimal>();
        if (instPay.isValid()) {
            final QueryBuilder queryBldr = new QueryBuilder(CIERP.Document2PaymentDocumentAbstract);
            queryBldr.addWhereAttrEqValue(CIERP.Document2PaymentDocumentAbstract.ToAbstractLink, instPay.getId());
            final MultiPrintQuery multi = queryBldr.getPrint();
            final SelectBuilder selRateCur = new SelectBuilder()
                            .linkto(CIERP.Document2PaymentDocumentAbstract.FromAbstractLink)
                            .attribute(CISales.DocumentSumAbstract.RateCurrencyId);
            final SelectBuilder selCross = new SelectBuilder()
                            .linkto(CIERP.Document2PaymentDocumentAbstract.FromAbstractLink)
                            .attribute(CISales.DocumentSumAbstract.CrossTotal);
            multi.addSelect(selRateCur, selCross);
            multi.execute();
            while (multi.next()) {
                final Long curId = multi.<Long>getSelect(selRateCur);
                final BigDecimal crossTotal = multi.<BigDecimal>getSelect(selCross);
                if (map.isEmpty()) {
                    map.put("RateCurrencyId", new BigDecimal(curId));
                    map.put("CrossTotal", crossTotal);
                } else {
                    if (map.get("RateCurrencyId").toString().equals(curId.toString())) {
                        map.put("CrossTotal", map.get("CrossTotal").add(crossTotal));
                    } else {
                        throw new EFapsException(Payment.class, "update.Currency");
                    }
                }
            }
            final Update update = new Update(instPay);
            update.add(CIERP.PaymentDocumentAbstract.Date, DateTimeUtil.normalize(new DateMidnight().toDateTime()));
            update.add(CIERP.PaymentDocumentAbstract.Amount, map.get("CrossTotal"));
            update.add(CIERP.PaymentDocumentAbstract.CurrencyLink, Long.parseLong(map.get("RateCurrencyId").toString()));
            update.execute();
        }
        return new Return();
    }

    /**
     * Method to create the connection between an sales document and a payment
     * document.
     *
     * @param _parameter as passed from eFaps API.
     * @return new Return
     * @throws EFapsException on error.
     */
    public Return createPaymentDefinition(final Parameter _parameter)
        throws EFapsException
    {
        final Instance instDoc = _parameter.getCallInstance();
        final BigDecimal amount4Doc2Pay = new BigDecimal(_parameter.getParameterValue("amount"));
        final long curId = Long.parseLong(_parameter.getParameterValue("currency"));
        final Insert insert = new Insert(CIAccounting.Document2PaymentDocument);
        insert.add(CIAccounting.Document2PaymentDocument.Date, _parameter.getParameterValue("date"));
        insert.add(CIAccounting.Document2PaymentDocument.CurrencyLink, _parameter.getParameterValue("currency"));
        insert.add(CIAccounting.Document2PaymentDocument.Amount, amount4Doc2Pay);
        insert.add(CIAccounting.Document2PaymentDocument.FromLink, instDoc.getId());
        insert.execute();

        // create a transaction when the payment will be realized (check)

        createPayment(_parameter, amount4Doc2Pay, curId, insert.getInstance());
        return new Return();
    }

    /**
     * Method to edit the connection between a sales document and a payment
     * document.
     *
     * @param _parameter as passed from eFaps API.
     * @return new Return
     * @throws EFapsException on error.
     */
    public Return payPaymentDocument(final Parameter _parameter)
        throws EFapsException
    {
        final Instance instDoc2Pay = _parameter.getInstance();
        final PrintQuery print = new PrintQuery(instDoc2Pay);
        print.addAttribute(CIAccounting.Document2PaymentDocument.Amount,
                        CIAccounting.Document2PaymentDocument.CurrencyLink);
        print.execute();
        final BigDecimal amount4Doc2Pay = print.<BigDecimal>getAttribute(CIAccounting.Document2PaymentDocument.Amount);
        final Long curId = print.<Long>getAttribute(CIAccounting.Document2PaymentDocument.CurrencyLink);

        createPayment(_parameter, amount4Doc2Pay, curId, instDoc2Pay);

        return new Return();
    }

    /**
     * Method to create a payment.
     *
     * @param _parameter as passed from eFaps API.
     * @param _amount4Doc2Pay BigDecimal with the amount to pay.
     * @param _currencyId Long with the Id of the currency.
     * @param _instDoc2Pay Instance of the connection.
     * @throws EFapsException on error.
     */
    protected void createPayment(final Parameter _parameter,
                                 final BigDecimal _amount4Doc2Pay,
                                 final Long _currencyId,
                                 final Instance _instDoc2Pay)
        throws EFapsException
    {
        if (_parameter.getParameterValue("paymentType") != null
                        && !_parameter.getParameterValue("paymentType").equals("*")) {
            final Type payType = Type.get(Long.parseLong(_parameter.getParameterValue("paymentType")));
            if (!payType.isKindOf(CIAccounting.PaymentDocumentImmediate.getType())) {
                final String payDocument = _parameter.getParameterValue("payDocument");
                final boolean setPayStatus = "true".equals(_parameter.getParameterValue("paymentStatus"));
                if (payDocument != null) {
                    final Instance instPayDoc = Instance.get(payDocument);
                    if (instPayDoc.isValid()) {
                        final PrintQuery print = new PrintQuery(instPayDoc);
                        print.addAttribute(CIAccounting.PaymentDocumentAbstract.Amount,
                                        CIAccounting.PaymentDocumentAbstract.CurrencyLink);
                        print.execute();
                        BigDecimal amount4Doc = print
                                        .<BigDecimal>getAttribute(CIAccounting.PaymentDocumentAbstract.Amount);
                        amount4Doc = amount4Doc != null ? amount4Doc.add(_amount4Doc2Pay) : _amount4Doc2Pay;
                        final Update update = new Update(instPayDoc);
                        update.add(CIAccounting.PaymentDocumentAbstract.Amount, amount4Doc);
                        update.add(CIAccounting.PaymentDocumentAbstract.CurrencyLink, _currencyId);
                        if (setPayStatus) {
                            update.add(CIAccounting.PaymentDocumentAbstract.StatusAbstract,
                                            Status.find(CIAccounting.PaymentDocumentStatus.uuid, "Closed").getId());
                        }
                        update.execute();

                        final Update update2 = new Update(_instDoc2Pay);
                        update2.add(CIAccounting.Document2PaymentDocument.ToLink, instPayDoc.getId());
                        update2.execute();
                    }
                }
            } else {
                final String payDocumentName = _parameter.getParameterValue("payDocument");
                final Insert insert = new Insert(payType);
                insert.add(CIAccounting.PaymentDocumentImmediate.Name, payDocumentName);
                insert.add(CIAccounting.PaymentDocumentImmediate.Date, _parameter.getParameterValue("date"));
                insert.add(CIAccounting.PaymentDocumentImmediate.Amount, _amount4Doc2Pay);
                insert.add(CIAccounting.PaymentDocumentImmediate.CurrencyLink, _currencyId);
                insert.add(CIAccounting.PaymentDocumentImmediate.StatusAbstract,
                                Status.find(CIAccounting.PaymentDocumentStatus.uuid, "Closed").getId());
                insert.execute();

                final Update update2 = new Update(_instDoc2Pay);
                update2.add(CIAccounting.Document2PaymentDocument.ToLink, insert.getInstance().getId());
                update2.execute();
            }
        }
    }

    /**
     * Get a dropdown with the types.
     *
     * @param _parameter Parameter as passed from the eFaps API
     * @return Return containing HTML snipplet
     */
    public Return getTypesFieldValue(final Parameter _parameter)
        throws CacheReloadException
    {
        final Return ret = new Return();
        final FieldValue fieldValue = (FieldValue) _parameter.get(ParameterValues.UIOBJECT);
        final Map<?, ?> properties = (Map<?, ?>) _parameter.get(ParameterValues.PROPERTIES);
        final String typeStr = (String) properties.get("Types");
        final Type type = Type.get(typeStr);

        final Map<String, Long> values = new TreeMap<String, Long>();
        final Set<Type> types = getChildTypes(type);
        for (final Type atype : types) {
            if (!atype.isAbstract()) {
                values.put(atype.getLabel(), atype.getId());
            }
        }

        final StringBuilder html = new StringBuilder();
        html.append("<select ").append(UIInterface.EFAPSTMPTAG).append(" size=\"1\" name=\"")
                        .append(fieldValue.getField().getName()).append("\">");
        html.append("<option value=\"*\">*</option>");
        for (final Entry<String, Long> value : values.entrySet()) {
            html.append("<option value=\"").append(value.getValue()).append("\">").append(value.getKey())
                            .append("</option>");
        }
        html.append("</select>");
        ret.put(ReturnValues.SNIPLETT, html.toString());
        return ret;
    }

    /**
     * Recursive method to get all types.
     *
     * @param _parent parent type
     * @return all children
     */
    protected Set<Type> getChildTypes(final Type _parent)
    {
        final Set<Type> ret = new HashSet<Type>();
        ret.add(_parent);
        for (final Type child : _parent.getChildTypes()) {
            ret.addAll(getChildTypes(child));
        }
        return ret;
    }

    /**
     * Get all the payment documents related to the type selected.
     *
     * @param _parameter as passed from eFaps API.
     * @return
     * @throws EFapsException on error.
     */
    public Return updateField4PaymentDocuments(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final List<Map<String, String>> list = new ArrayList<Map<String, String>>();
        final Map<String, String> map = new HashMap<String, String>();
        final Map<?, ?> props = (Map<?, ?>) _parameter.get(ParameterValues.PROPERTIES);
        final StringBuilder html = new StringBuilder();
        final StringBuilder html2 = new StringBuilder();
        final StringBuilder js = new StringBuilder();
        final Type type = !_parameter.getParameterValue("paymentType").equals("*")
                        ? Type.get(Long.parseLong(_parameter.getParameterValue("paymentType"))) : Type.get(0);
        if (type != null && !type.isKindOf(CIAccounting.PaymentDocumentImmediate.getType())) {
            final QueryBuilder queryBlrd = new QueryBuilder(CIAccounting.Periode);
            queryBlrd.addWhereAttrGreaterValue(CIAccounting.Periode.ToDate, new DateTime().minusDays(1));
            queryBlrd.addWhereAttrLessValue(CIAccounting.Periode.FromDate, new DateTime().plusSeconds(1));
            final MultiPrintQuery multiP = queryBlrd.getPrint();
            multiP.execute();
            html.append("<table>");
            if (multiP.next()) {
                final QueryBuilder accQueryBldr = new QueryBuilder(CIAccounting.AccountAbstract);
                accQueryBldr.addWhereAttrEqValue(CIAccounting.AccountAbstract.PeriodeAbstractLink,
                                multiP.getCurrentInstance().getId());
                final AttributeQuery accQuery = accQueryBldr.getAttributeQuery(CIAccounting.AccountAbstract.ID);

                final QueryBuilder payQueryBldr = new QueryBuilder(type);
                payQueryBldr.addWhereAttrEqValue(CIAccounting.PaymentDocumentAbstract.StatusAbstract,
                                Status.find(CIAccounting.PaymentDocumentStatus.uuid, "Open").getId());
                final AttributeQuery payQuery = payQueryBldr.getAttributeQuery(CIERP.PaymentDocumentAbstract.ID);

                final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.Account2PaymentAbstract);
                queryBldr.addWhereAttrInQuery(CIAccounting.Account2PaymentAbstract.FromAbstractLink, accQuery);
                queryBldr.addWhereAttrInQuery(CIAccounting.Account2PaymentAbstract.ToAbstractLink, payQuery);

                final SelectBuilder accNameSel = new SelectBuilder()
                                .linkto(CIAccounting.Account2PaymentAbstract.FromAbstractLink)
                                .attribute(CIAccounting.AccountAbstract.Name);
                final SelectBuilder accDescSel = new SelectBuilder()
                                .linkto(CIAccounting.Account2PaymentAbstract.FromAbstractLink)
                                .attribute(CIAccounting.AccountAbstract.Description);
                final SelectBuilder payNameSel = new SelectBuilder()
                                .linkto(CIAccounting.Account2PaymentAbstract.ToAbstractLink)
                                .attribute(CIERP.PaymentDocumentAbstract.Name);
                final SelectBuilder payOidSel = new SelectBuilder()
                                .linkto(CIAccounting.Account2PaymentAbstract.ToAbstractLink).oid();

                final MultiPrintQuery multi = queryBldr.getPrint();
                multi.addSelect(accNameSel, accDescSel, payNameSel, payOidSel);
                multi.execute();

                final Map<String, List<String[]>> acc2Pay = new TreeMap<String, List<String[]>>();
                while (multi.next()) {
                    final String accName = multi.<String>getSelect(accNameSel);
                    final String accDesc = multi.<String>getSelect(accDescSel);
                    final String payName = multi.<String>getSelect(payNameSel);
                    final String payOid = multi.<String>getSelect(payOidSel);
                    final String acc = accName + " - " + accDesc;
                    List<String[]> pays;
                    if (acc2Pay.containsKey(acc)) {
                        pays = acc2Pay.get(acc);
                    } else {
                        pays = new ArrayList<String[]>();
                        acc2Pay.put(acc, pays);
                    }
                    pays.add(new String[] { payName, payOid });
                }
                final String columnCountStr = (String) props.get("columnCount");
                final int columnCount = columnCountStr != null && !columnCountStr.isEmpty()
                                ? Integer.parseInt(columnCountStr) : 5;

                for (final Entry<String, List<String[]>> entry : acc2Pay.entrySet()) {
                    final int c = entry.getValue().size() / columnCount
                                    + (entry.getValue().size() % columnCount > 0 ? 1 : 0);
                    html.append("<tr><td rowspan=\"").append(c).append("\">").append(entry.getKey())
                                    .append("</td>");
                    int i = 0;
                    for (final String[] pay : entry.getValue()) {
                        if (i < columnCount) {
                            html.append("<td>");
                        } else {
                            html.append("</tr><tr><td>");
                            i = 0;
                        }
                        html.append("<input type=\"checkbox\" name=\"").append("payDocument")
                                        .append("\" value=\"").append(pay[1]).append("\" />")
                                        .append(pay[0]).append("</td>");
                        i++;
                    }
                    for (int j = i; j < columnCount; j++) {
                        html.append("<td></td>");
                    }
                    i = 0;
                }
            }
            html.append("</table>");
            html2.append("<input type=\"checkbox\" name=\"").append("paymentStatus").append("\"")
                            .append(" value=\"").append("true").append("\" ")
                            .append(" checked=\"checked\" ")
                            .append("/>");
        } else if (type != null && type.isKindOf(CIAccounting.PaymentDocumentImmediate.getType())) {
            html.append("<input type=\"text\" name=\"").append("payDocument").append("\"")
                            .append("/>");

        }
        js.append(" document.getElementsByName('paymentStatus')[0].innerHTML='").append(html2.toString()).append("';")
                        .append("document.getElementsByName('paymentDoc')[0].innerHTML='")
                        .append(html.toString()).append("';");
        map.put("eFapsFieldUpdateJS", js.toString());
        list.add(map);
        ret.put(ReturnValues.VALUES, list);
        return ret;
    }

    /**
     * Get the remaining amount to pay.
     *
     * @param _parameter as passed from eFaps API.
     * @return
     * @throws EFapsException on error.
     */
    public Return getJavaScripUIValue(final Parameter _parameter)
        throws EFapsException
    {
        final DecimalFormat formater = (DecimalFormat) NumberFormat.getInstance(Context.getThreadContext().getLocale());
        formater.setMaximumFractionDigits(2);
        final Instance instanceDoc = _parameter.getInstance();
        final PrintQuery print = new PrintQuery(instanceDoc);
        print.addAttribute(CISales.DocumentSumAbstract.CrossTotal,
                        CISales.DocumentSumAbstract.CurrencyId);
        print.execute();
        BigDecimal doc2PayAmount = print.<BigDecimal>getAttribute(CISales.DocumentSumAbstract.CrossTotal);
        final Long curId = print.<Long>getAttribute(CISales.DocumentSumAbstract.CurrencyId);

        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.Document2PaymentDocument);
        queryBldr.addWhereAttrEqValue(CIAccounting.Document2PaymentDocument.FromLink, instanceDoc.getId());
        final MultiPrintQuery multi = queryBldr.getPrint();
        multi.addAttribute(CIAccounting.Document2PaymentDocument.Amount);
        multi.execute();
        while (multi.next()) {
            final BigDecimal tempAmount = multi.<BigDecimal>getAttribute(CIAccounting.Document2PaymentDocument.Amount);
            doc2PayAmount = doc2PayAmount.subtract(tempAmount);
        }

        final StringBuilder js = new StringBuilder();
        js.append("<script type=\"text/javascript\">")
                        .append("Wicket.Event.add(window, \"domready\", function(event) {")
                        .append("document.getElementsByName('amount')[0].value='")
                        .append(formater.format(doc2PayAmount)).append("';")
                        .append(" document.getElementsByName('currency')[0].selectedIndex=")
                        .append(curId - 1).append(";")
                        .append("});")
                        .append("</script>");
        final Return ret = new Return();
        ret.put(ReturnValues.SNIPLETT, js.toString());
        return ret;
    }

    /**
     * Method to check if the connection payment type has a payment document
     * connected.
     *
     * @param _parameter as passed from eFaps API.
     * @return Return true if it hasn't a payment document connected.
     * @throws EFapsException on error.
     */
    public Return accessCheck4Pay2Doc(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final Instance instance = _parameter.getInstance();
        final PrintQuery print = new PrintQuery(instance);
        final SelectBuilder select = new SelectBuilder().linkto(CIAccounting.Document2PaymentDocument.ToLink).oid();
        print.addSelect(select);
        print.execute();
        final String oid = print.<String>getSelect(select);
        if (oid == null) {
            ret.put(ReturnValues.TRUE, true);
        }
        return ret;
    }

    /**
     * Method to check the access if the total payment sum amounts is equal with
     * the cross total of the sales document.
     *
     * @param _parameter as passed from eFaps API.
     * @return Return with true if the amount is different.
     * @throws EFapsException on error.
     */
    public Return accessCheck4Payment(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final Instance instance = _parameter.getInstance();
        final PrintQuery print = new PrintQuery(instance);
        print.addAttribute(CISales.DocumentSumAbstract.CrossTotal);
        print.execute();
        BigDecimal totalPay = BigDecimal.ZERO;
        final BigDecimal total = print.<BigDecimal>getAttribute(CISales.DocumentSumAbstract.CrossTotal);

        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.Document2PaymentDocument);
        queryBldr.addWhereAttrEqValue(CIAccounting.Document2PaymentDocument.FromLink, instance.getId());
        final MultiPrintQuery multi = queryBldr.getPrint();
        multi.addAttribute(CIAccounting.Document2PaymentDocument.Amount);
        multi.execute();
        while (multi.next()) {
            final BigDecimal amount = multi.<BigDecimal>getAttribute(CIAccounting.Document2PaymentDocument.Amount);
            totalPay = totalPay.add(amount);
        }

        if (totalPay.compareTo(total) != 0) {
            ret.put(ReturnValues.TRUE, true);
        }
        return ret;
    }

    /**
     * Validate if only one or at least one payment document is selected when a
     * planned document child is selected.
     *
     * @param _parameter as passed from eFaps API.
     * @return Return with true if it's correct.
     * @throws EFapsException
     * @throws
     */
    public Return validate4Pay2Doc(final Parameter _parameter)
        throws EFapsException
    {
        StringBuilder st = new StringBuilder();
        final Return ret = new Return();
        st = validateAmount(_parameter);

        if (st.toString().isEmpty()) {
            if (_parameter.getParameterValue("paymentType") != null
                            && !_parameter.getParameterValue("paymentType").equals("*")) {
                final Long payTypeId = Long.parseLong(_parameter.getParameterValue("paymentType"));
                if (Type.get(payTypeId).isKindOf(CIAccounting.PaymentDocumentPlanned.getType())) {
                    final String[] payDocs = _parameter.getParameterValues("payDocument");
                    if (payDocs != null) {
                        if (payDocs.length != 1) {
                            st.append(DBProperties.getProperty("org.efaps.esjp.accounting.Payment.OnlyOne"));
                            ret.put(ReturnValues.SNIPLETT, st.toString());
                        } else {
                            ret.put(ReturnValues.TRUE, true);
                        }
                    } else {
                        st.append(DBProperties.getProperty("org.efaps.esjp.accounting.Payment.AtLeastOne"));
                        ret.put(ReturnValues.SNIPLETT, st.toString());
                    }
                } else if (Type.get(payTypeId).isKindOf(CIAccounting.PaymentDocumentImmediate.getType())) {
                    final String nameDoc = _parameter.getParameterValue("payDocument");
                    if (nameDoc != null && !nameDoc.isEmpty()) {
                        ret.put(ReturnValues.TRUE, true);
                    } else {
                        st.append(DBProperties.getProperty("org.efaps.esjp.accounting.Payment.NotName"));
                        ret.put(ReturnValues.SNIPLETT, st.toString());
                    }
                }
            } else {
                ret.put(ReturnValues.TRUE, true);
            }
        } else {
            ret.put(ReturnValues.SNIPLETT, st.toString());
        }
        return ret;
    }

    protected StringBuilder validateAmount(final Parameter _parameter)
        throws EFapsException
    {
        final StringBuilder html = new StringBuilder();
        final BigDecimal amount = new BigDecimal(_parameter.getParameterValue("amount"));

        final Instance instDoc = _parameter.getInstance();
        final PrintQuery print = new PrintQuery(instDoc);
        print.addAttribute(CISales.DocumentSumAbstract.CrossTotal);
        print.execute();
        final BigDecimal amountTotal = print.<BigDecimal>getAttribute(CISales.DocumentSumAbstract.CrossTotal);
        BigDecimal amountTotalTemp = amountTotal;

        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.Document2PaymentDocument);
        queryBldr.addWhereAttrEqValue(CIAccounting.Document2PaymentDocument.FromLink, instDoc.getId());
        final MultiPrintQuery multi = queryBldr.getPrint();
        multi.addAttribute(CIAccounting.Document2PaymentDocument.Amount);
        multi.execute();
        while (multi.next()) {
            final BigDecimal tempAmount = multi.<BigDecimal>getAttribute(CIAccounting.Document2PaymentDocument.Amount);
            amountTotalTemp = amountTotalTemp.subtract(tempAmount);
        }

        if (amount.compareTo(BigDecimal.ZERO) > 0) {
            if (amount.compareTo(amountTotalTemp) > 0) {
                html.append(DBProperties.getProperty("org.efaps.esjp.accounting.Payment.ExcesiveAmount"));
            }
        } else {
            html.append(DBProperties.getProperty("org.efaps.esjp.accounting.Payment.NegativeAmount"));
        }
        return html;
    }

    /**
     * Disconnect the sales document from the payment document deleting the
     * connection, and subtract the amount of the connection from the payment
     * document.
     *
     * @param _parameter as passed from eFaps API.
     * @return
     * @throws EFapsException on error.
     */
    public Return disconnectDocument(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final Instance instDoc = _parameter.getInstance();
        final PrintQuery printDoc = new PrintQuery(instDoc);
        printDoc.addAttribute(CIAccounting.PaymentDocumentAbstract.Amount);
        printDoc.execute();
        final BigDecimal amountPayDoc = printDoc.<BigDecimal>getAttribute(CIAccounting.PaymentDocumentAbstract.Amount);
        BigDecimal newAmountPayDoc = amountPayDoc;

        final String[] allOids = (String[]) _parameter.get(ParameterValues.OTHERS);
        if (allOids != null && allOids.length != 0) {
            for (final String oid : allOids) {
                final Instance instDoc2Pay = Instance.get(oid);
                final PrintQuery print = new PrintQuery(instDoc2Pay);
                print.addAttribute(CIAccounting.Document2PaymentDocument.Amount);
                print.execute();
                final BigDecimal amount = print.<BigDecimal>getAttribute(CIAccounting.Document2PaymentDocument.Amount);
                newAmountPayDoc = newAmountPayDoc.subtract(amount);

                final Delete delete = new Delete(instDoc2Pay);
                delete.execute();
            }

            if (newAmountPayDoc.compareTo(amountPayDoc) != 0) {
                final Update update = new Update(instDoc);
                update.add(CIAccounting.PaymentDocumentAbstract.Amount, newAmountPayDoc);
                update.add(CIAccounting.PaymentDocumentAbstract.StatusAbstract,
                                Status.find(CIAccounting.PaymentDocumentStatus.uuid, "Open").getId());
                update.execute();
            }
        }
        return ret;
    }

    /**
     * Represents on open amount.
     */
    public class OpenAmount
        implements Serializable
    {
        /**
         * Needed for serialization.
         */
        private static final long serialVersionUID = 1L;

        /**
         * CurrencyInst of this OpenAmount.
         */
        private final CurrencyInst currencyInstance;

        /**
         * Cross Total.
         */
        private final BigDecimal crossTotal;

        /**
         * Date of this OpenAmount.
         */
        private final DateTime date;

        /**
         * @param _currencyInstance Currency Instance
         * @param _crossTotal Cross Total
         * @param _rateDate date of the rate
         */
        public OpenAmount(final CurrencyInst _currencyInstance,
                          final BigDecimal _crossTotal,
                          final DateTime _rateDate)
        {
            this.currencyInstance = _currencyInstance;
            this.crossTotal = _crossTotal;
            this.date = _rateDate;
        }

        /**
         * Getter method for the instance variable {@link #symbol}.
         *
         * @return value of instance variable {@link #symbol}
         * @throws EFapsException on error
         */
        public String getSymbol()
            throws EFapsException
        {
            return this.currencyInstance.getSymbol();
        }

        /**
         * Getter method for the instance variable {@link #currencyInstance}.
         *
         * @return value of instance variable {@link #currencyInstance}
         */
        public Instance getCurrencyInstance()
        {
            return this.currencyInstance.getInstance();
        }

        /**
         * Getter method for the instance variable {@link #crossTotal}.
         *
         * @return value of instance variable {@link #crossTotal}
         */
        public BigDecimal getCrossTotal()
        {
            return this.crossTotal;
        }

        /**
         * Getter method for the instance variable {@link #date}.
         *
         * @return value of instance variable {@link #date}
         */
        public DateTime getDate()
        {
            return this.date;
        }

    }
}
