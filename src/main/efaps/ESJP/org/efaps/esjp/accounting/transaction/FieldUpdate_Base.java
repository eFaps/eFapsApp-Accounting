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
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringEscapeUtils;
import org.efaps.admin.datamodel.ui.RateUI;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Parameter.ParameterValues;
import org.efaps.admin.event.Return;
import org.efaps.admin.event.Return.ReturnValues;
import org.efaps.admin.program.esjp.EFapsRevision;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.CachedPrintQuery;
import org.efaps.db.Context;
import org.efaps.db.Instance;
import org.efaps.db.PrintQuery;
import org.efaps.esjp.accounting.Periode;
import org.efaps.esjp.accounting.SubPeriod_Base;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.ci.CIERP;
import org.efaps.esjp.erp.CurrencyInst;
import org.efaps.esjp.erp.RateInfo;
import org.efaps.ui.wicket.util.DateUtil;
import org.efaps.ui.wicket.util.EFapsKey;
import org.efaps.util.EFapsException;
import org.joda.time.DateTime;


/**
 * TODO comment!
 *
 * @author The eFaps Team
 * @version $Id$
 */
@EFapsUUID("af9547fa-3caf-4013-9e81-d637736ac62b")
@EFapsRevision("$Rev$")
public abstract class FieldUpdate_Base
    extends Transaction
{
    /**
     * Executed as update event form the case field.
     * @param _parameter Parameter as passed from the eFaps API
     * @return new Return
     * @throws EFapsException on error
     */
    public Return update4Case(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final String caseOid = _parameter.getParameterValue("case");
        Context.getThreadContext().setSessionAttribute(Transaction_Base.CASE_SESSIONKEY, caseOid);
        return ret;
    }

    /**
     * Activate/deactivate the filter for the accounts.
     * @param _parameter Parameter as passed from the eFaps API
     * @return new Return
     * @throws EFapsException on error
     */
    public Return update4checkbox4Account(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final String caseOid = _parameter.getParameterValue("case");
        final String check = _parameter.getParameterValue("checkbox4Account");
        if (check != null && !check.isEmpty() && "true".equalsIgnoreCase(check)) {
            Context.getThreadContext().setSessionAttribute(Transaction_Base.CASE_SESSIONKEY, caseOid);
        } else {
            Context.getThreadContext().setSessionAttribute(Transaction_Base.CASE_SESSIONKEY, null);
        }
        return ret;
    }

    /**
     * Method is executed on update trigger for the account field in the debit
     * and credit table inside the transaction form.
     *
     * @param _parameter Parameter as passed from the eFaps API
     * @return list for update trigger
     * @throws EFapsException on error
     */
    public Return update4Account(final Parameter _parameter)
        throws EFapsException
    {
        final Map<?, ?> properties = (Map<?, ?>) _parameter.get(ParameterValues.PROPERTIES);
        final String postfix = (String) properties.get("TypePostfix");
        final String[] accountOIDs = _parameter.getParameterValues("accountLink_" + postfix);
        final String selected = _parameter.getParameterValue("eFapsRowSelectedRow");
        final int pos = Integer.parseInt(selected);
        final String accountOID = accountOIDs[pos];
        final List<Map<String, String>> list = new ArrayList<Map<String, String>>();

        final StringBuilder inner = new Transaction().getLinkString(accountOID, "_" + postfix);

        final Map<String, String> map = new HashMap<String, String>();
        final StringBuilder js = new StringBuilder();
        js.append("var rv = \"").append(inner).append("\";").append("document.getElementsByName('account2account_")
                        .append(postfix).append("')[").append(pos).append("].innerHTML=rv;");
        map.put("eFapsFieldUpdateJS", js.toString());
        list.add(map);
        final Return retVal = new Return();
        retVal.put(ReturnValues.VALUES, list);
        return retVal;
    }

    /**
     * Method is executed on update trigger for the amount field in the debit
     * and credit table inside the transaction form.
     *
     * @param _parameter Parameter as passed from the eFaps API
     * @return list for update trigger
     * @throws EFapsException on error
     */
    public Return update4Amount(final Parameter _parameter)
        throws EFapsException
    {
        final Return retVal = new Return();
        try {
            final Map<?, ?> properties = (Map<?, ?>) _parameter.get(ParameterValues.PROPERTIES);
            final String postfix = (String) properties.get("TypePostfix");
            final String[] amounts = _parameter.getParameterValues("amount_" + postfix);
            final String[] rates = _parameter.getParameterValues("rate_" + postfix);
            final String[] ratesInv = _parameter.getParameterValues("rate_" + postfix + RateUI.INVERTEDSUFFIX);

            final int pos = getSelectedRow(_parameter);
            final DecimalFormat rateFormater = getFormater(0, 8);
            final DecimalFormat formater = getFormater(2, 2);
            final BigDecimal amount = amounts[pos].isEmpty() ? BigDecimal.ZERO
                                                           : (BigDecimal) rateFormater.parse(amounts[pos]);
            BigDecimal rate = rates[pos].isEmpty() ? BigDecimal.ZERO
                                                         : (BigDecimal) rateFormater.parse(rates[pos]);
            final boolean rateInv = "true".equalsIgnoreCase(ratesInv[pos]);
            if (rateInv && rate.compareTo(BigDecimal.ZERO) != 0) {
                rate = BigDecimal.ONE.divide(rate, 12, BigDecimal.ROUND_HALF_UP);
            }
            final List<Map<String, String>> list = new ArrayList<Map<String, String>>();
            final Instance periodeInstance = (Instance) Context.getThreadContext().getSessionAttribute(
                            Transaction_Base.PERIODE_SESSIONKEY);

            final BigDecimal sum = getSum(_parameter, postfix, null, null, null);
            final String postfix2 = "Debit".equals(postfix) ? "Credit" : "Debit";
            final BigDecimal sum2 = getSum(_parameter, postfix2, null, null, null);
            final String sumStr = formater.format(sum) + " "
                            + new Periode().getCurrency(periodeInstance).getSymbol();
            final String sumStr2 = formater.format(sum.subtract(sum2).abs()) + " "
                            + new Periode().getCurrency(periodeInstance).getSymbol();

            final Map<String, String> map = new HashMap<String, String>();
            map.put("sum" + postfix, sumStr);
            map.put("amountRate_" + postfix,
                            formater.format(amount.setScale(8).divide(rate, BigDecimal.ROUND_HALF_UP)));
            map.put("sumTotal", sumStr2);
            list.add(map);
            retVal.put(ReturnValues.VALUES, list);
        } catch (final ParseException e) {
            throw new EFapsException(Transaction_Base.class, "update4Amount.ParseException", e);
        }
        return retVal;
    }

    /**
     * Executed on update event of the currency field.
     * @param _parameter    parameter as passed from the eFaps API
     * @return  list of maps as needed by the update event
     * @throws EFapsException on error
     */
    public Return update4Currency(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        try {
            final Map<?, ?> properties = (Map<?, ?>) _parameter.get(ParameterValues.PROPERTIES);
            final String postfix = (String) properties.get("TypePostfix");

            final String[] currIds = _parameter.getParameterValues("rateCurrencyLink_" + postfix);
            final String[] amounts = _parameter.getParameterValues("amount_" + postfix);

            final int pos = getSelectedRow(_parameter);
            final String dateStr = _parameter.getParameterValue("date_eFapsDate");
            final DateTime date = DateUtil.getDateFromParameter(dateStr);
            final Instance periodeInstance = (Instance) Context.getThreadContext().getSessionAttribute(
                            Transaction_Base.PERIODE_SESSIONKEY);

            final RateInfo rate = evaluateRate(_parameter, periodeInstance, date,
                            Instance.get(CIERP.Currency.getType(), currIds[pos]));
            final DecimalFormat rateFormater = rate.getFormatter().getFrmt4RateUI();
            final DecimalFormat formater = getFormater(2, 2);
            final BigDecimal amountRate = amounts[pos].isEmpty() ? BigDecimal.ZERO
                            : (BigDecimal) rateFormater.parse(amounts[pos]);

            final BigDecimal sum = getSum(_parameter, postfix, pos, null, rate.getRate());
            final String postfix2 = "Debit".equals(postfix) ? "Credit" : "Debit";
            final BigDecimal sum2 = getSum(_parameter, postfix2, null, null, null);
            final String sumStr = formater.format(sum) + " "
                            + new Periode().getCurrency(periodeInstance).getSymbol();
            final String sumStr2 = formater.format(sum.subtract(sum2).abs()) + " "
                            + new Periode().getCurrency(periodeInstance).getSymbol();

            final List<Map<String, String>> list = new ArrayList<Map<String, String>>();
            final Map<String, String> map = new HashMap<String, String>();
            map.put("rate_" + postfix, rate.getRateUIFrmt());
            map.put("rate_" + postfix + RateUI.INVERTEDSUFFIX, "" + rate.getCurrencyInst().isInvert());
            map.put("sum" + postfix, sumStr);
            map.put("amountRate_" + postfix,
                            formater.format(amountRate.setScale(12).divide(rate.getRate(), BigDecimal.ROUND_HALF_UP)));
            map.put("sumTotal", sumStr2);
            list.add(map);
            ret.put(ReturnValues.VALUES, list);
        } catch (final ParseException e) {
            throw new EFapsException(Transaction_Base.class, "update4Currency.ParseException", e);
        }
        return ret;
    }

    /**
     * Executed on update event of the currency rate field.
     * @param _parameter parameter as passed from the eFaps API.
     * @return  list of maps as needed by the update event.
     * @throws EFapsException on error.
     */
    public Return update4CurrenyRate(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final String cur = _parameter.getParameterValue("rateCurrencyLink");
        final CurrencyInst inst = new CurrencyInst(Instance.get(CIERP.Currency.getType(), cur));
        final List<Map<String, String>> list = new ArrayList<Map<String, String>>();
        final Map<String, String> map = new HashMap<String, String>();
        map.put("rate" + RateUI.INVERTEDSUFFIX, "" + inst.isInvert());
        list.add(map);
        ret.put(ReturnValues.VALUES, list);
        return ret;
    }

    /**
     * Update the rate fields on change of the date value.
     * @param _parameter    Parameter as passed from the eFaps API
     * @return  list needed for field update event
     * @throws EFapsException on error
     */
    public Return update4Date(final Parameter _parameter)
        throws EFapsException
    {
        final List<Map<String, String>> list = new ArrayList<Map<String, String>>();
        final Map<?, ?> props = (Map<?, ?>) _parameter.get(ParameterValues.PROPERTIES);
        final Instance periodInst = _parameter.getInstance();
        final String dateStr = _parameter.getParameterValue("date_eFapsDate");
        final DateTime date = DateUtil.getDateFromParameter(dateStr);
        final Map<String, String> map = new HashMap<String, String>();

        // validate and correct the date, put it in _parameter so that other methods use the correct date
        DateTime fromDate = null;
        DateTime toDate = null;
        if (periodInst.getType().isKindOf(CIAccounting.Periode.getType())) {
            final PrintQuery print = new PrintQuery(periodInst);
            print.addAttribute(CIAccounting.Periode.FromDate, CIAccounting.Periode.ToDate);
            print.execute();
            fromDate = print.<DateTime>getAttribute(CIAccounting.Periode.FromDate);
            toDate = print.<DateTime>getAttribute(CIAccounting.Periode.ToDate);
        } else if (periodInst.getType().isKindOf(CIAccounting.SubPeriod.getType())) {
            final PrintQuery print = new CachedPrintQuery(periodInst, SubPeriod_Base.CACHEKEY);
            print.addAttribute(CIAccounting.SubPeriod.FromDate, CIAccounting.SubPeriod.ToDate);
            print.execute();
            fromDate = print.<DateTime>getAttribute(CIAccounting.SubPeriod.FromDate);
            toDate = print.<DateTime>getAttribute(CIAccounting.SubPeriod.ToDate);
        }
        if (fromDate != null && toDate != null) {
            DateTime newDate = null;
            if (date.isBefore(fromDate)) {
                newDate = fromDate;
            } else if (date.isAfter(toDate)) {
                newDate = toDate;
            }
            if (newDate != null) {
                final String newDateStr = DateUtil.getDate4Parameter(newDate);
                map.put("date_eFapsDate", newDateStr);
                _parameter.getParameters().put("date_eFapsDate", new String[]{ newDateStr });
            }
        }

        final StringBuilder js = new StringBuilder();
        if ("true".equalsIgnoreCase((String) props.get("UpdateDocInfo"))) {
            final String docOid = _parameter.getParameterValue("document");
            final Instance docInst = Instance.get(docOid);
            if (docInst.isValid()) {
                final Document doc = new Document(docInst);
                final FieldValue fielValue = new FieldValue();
                js.append("document.getElementsByName(\"document_span\")[0].innerHTML='")
                    .append(StringEscapeUtils.escapeEcmaScript(
                                    fielValue.getDocumentFieldValue(_parameter, doc).toString()))
                    .append(StringEscapeUtils.escapeEcmaScript(
                                    fielValue.getCostInformation(_parameter, date, doc).toString()))
                    .append("'; ")
                    .append(fielValue.getScript(_parameter, doc));
            }
        }

        js.append(getCurrencyJS(_parameter, "rateCurrencyLink_Debit", "rate_Debit"))
            .append(getCurrencyJS(_parameter, "rateCurrencyLink_Credit", "rate_Credit"));
        map.put(EFapsKey.FIELDUPDATE_JAVASCRIPT.getKey(), js.toString());
        list.add(map);
        final Return retVal = new Return();
        retVal.put(ReturnValues.VALUES, list);
        return retVal;
    }

    /**
     * Method is executed on update trigger for the rate field in the debit
     * and credit table inside the transaction form.
     *
     * @param _parameter Parameter as passed from the eFaps API
     * @return list for update trigger
     * @throws EFapsException on error
     */
    public Return update4Rate(final Parameter _parameter)
        throws EFapsException
    {
        final Return retVal = new Return();

        try {
            final Map<?, ?> properties = (Map<?, ?>) _parameter.get(ParameterValues.PROPERTIES);
            final String postfix = (String) properties.get("TypePostfix");

            final String[] amounts = _parameter.getParameterValues("amount_" + postfix);
            final String[] rates = _parameter.getParameterValues("rate_" + postfix);
            final String[] ratesInv = _parameter.getParameterValues("rate_" + postfix + RateUI.INVERTEDSUFFIX);

            final int pos = getSelectedRow(_parameter);
            final DecimalFormat rateFormater = getFormater(0, 8);
            final DecimalFormat formater = getFormater(2, 2);
            final BigDecimal amount = amounts[pos].isEmpty() ? BigDecimal.ZERO
                                                           : (BigDecimal) rateFormater.parse(amounts[pos]);
            BigDecimal rate = rates[pos].isEmpty() ? BigDecimal.ONE
                                                         : (BigDecimal) rateFormater.parse(rates[pos]);
            final boolean rateInv = "true".equalsIgnoreCase(ratesInv[pos]);
            if (rateInv && rate.compareTo(BigDecimal.ZERO) != 0) {
                rate = BigDecimal.ONE.divide(rate, 12, BigDecimal.ROUND_HALF_UP);
            }
            final List<Map<String, String>> list = new ArrayList<Map<String, String>>();
            final Instance periodeInstance = (Instance) Context.getThreadContext().getSessionAttribute(
                            Transaction_Base.PERIODE_SESSIONKEY);

            final BigDecimal sum = getSum(_parameter, postfix, null, null, null);
            final String postfix2 = "Debit".equals(postfix) ? "Credit" : "Debit";
            final BigDecimal sum2 = getSum(_parameter, postfix2, null, null, null);
            final String sumStr = formater.format(sum) + " "
                            + new Periode().getCurrency(periodeInstance).getSymbol();
            final String sumStr2 = formater.format(sum.subtract(sum2).abs()) + " "
                            + new Periode().getCurrency(periodeInstance).getSymbol();

            final Map<String, String> map = new HashMap<String, String>();
            map.put("sum" + postfix, sumStr);
            map.put("amountRate_" + postfix,
                            formater.format(amount.setScale(8).divide(rate, BigDecimal.ROUND_HALF_UP)));
            map.put("sumTotal", sumStr2);
            list.add(map);

            retVal.put(ReturnValues.VALUES, list);
        } catch (final ParseException e) {
            throw new EFapsException(Transaction_Base.class, "update4Rate.ParseException", e);
        }
        return retVal;
    }

    /**
     * Method to evaluate the selected row.
     *
     * @param _parameter paaremter
     * @return number of selected row.
     */
    protected int getSelectedRow(final Parameter _parameter)
    {
        int ret = 0;
        final String value = _parameter.getParameterValue("eFapsRowSelectedRow");
        if (value != null && value.length() > 0) {
            ret = Integer.parseInt(value);
        }
        return ret;
    }


    /**
     * Get a javascript to set the rate fields.
     * @param _parameter        Parameter from the eFaps API
     * @param _fieldName        name of the fields containing the currency
     * @param _targetFieldName  name of the rate fiel.d
     * @return javascript
     * @throws EFapsException on error
     */
    protected StringBuilder getCurrencyJS(final Parameter _parameter,
                                          final String _fieldName,
                                          final String _targetFieldName)
        throws EFapsException
    {
        final String[] currIds = _parameter.getParameterValues(_fieldName);
        final StringBuilder ret = new StringBuilder();
        final String dateStr = _parameter.getParameterValue("date_eFapsDate");
        final DateTime date = DateUtil.getDateFromParameter(dateStr);
        final Instance periodeInstance = (Instance) Context.getThreadContext().getSessionAttribute(
                        Transaction_Base.PERIODE_SESSIONKEY);
        if (currIds != null) {
            for (int i = 0; i < currIds.length; i++) {
                final RateInfo rate = evaluateRate(_parameter, periodeInstance, date,
                                Instance.get(CIERP.Currency.getType(), currIds[i]));
                ret.append("document.getElementsByName('").append(_targetFieldName).append("')[").append(i)
                    .append("].value='").append(rate.getRateUIFrmt()).append("';")
                    .append("document.getElementsByName('").append(_targetFieldName)
                    .append(RateUI.INVERTEDSUFFIX)
                    .append("')[").append(i).append("].value='").append(rate.getCurrencyInst().isInvert())
                    .append("';");
            }
        }
        return ret;
    }
}
