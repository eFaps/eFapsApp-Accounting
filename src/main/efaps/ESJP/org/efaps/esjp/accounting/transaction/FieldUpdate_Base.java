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
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.efaps.admin.datamodel.ui.RateUI;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Parameter.ParameterValues;
import org.efaps.admin.event.Return;
import org.efaps.admin.event.Return.ReturnValues;
import org.efaps.admin.program.esjp.EFapsRevision;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.Context;
import org.efaps.db.Instance;
import org.efaps.esjp.accounting.Periode;
import org.efaps.esjp.ci.CIERP;
import org.efaps.esjp.erp.CurrencyInst;
import org.efaps.esjp.erp.Rate;
import org.efaps.ui.wicket.util.DateUtil;
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
            final DecimalFormat rateFormater = getFormater(0, 8);
            final DecimalFormat formater = getFormater(2, 2);
            final BigDecimal amountRate = amounts[pos].isEmpty() ? BigDecimal.ZERO
                                                           : (BigDecimal) rateFormater.parse(amounts[pos]);

            final String dateStr = _parameter.getParameterValue("date_eFapsDate");
            final DateTime date = DateUtil.getDateFromParameter(dateStr);
            final Instance periodeInstance = (Instance) Context.getThreadContext().getSessionAttribute(
                            Transaction_Base.PERIODE_SESSIONKEY);

            final Rate rate = getExchangeRate(periodeInstance, Long.parseLong(currIds[pos]), date, null);
            final BigDecimal sum = getSum(_parameter, postfix, pos, null, rate.getValue());
            final String postfix2 = "Debit".equals(postfix) ? "Credit" : "Debit";
            final BigDecimal sum2 = getSum(_parameter, postfix2, null, null, null);
            final String sumStr = formater.format(sum) + " "
                            + new Periode().getCurrency(periodeInstance).getSymbol();
            final String sumStr2 = formater.format(sum.subtract(sum2).abs()) + " "
                            + new Periode().getCurrency(periodeInstance).getSymbol();

            final List<Map<String, String>> list = new ArrayList<Map<String, String>>();
            final Map<String, String> map = new HashMap<String, String>();
            map.put("rate_" + postfix, rateFormater.format(rate.getLabel()));
            map.put("rate_" + postfix + RateUI.INVERTEDSUFFIX, "" + rate.getCurInstance().isInvert());
            map.put("sum" + postfix, sumStr);
            map.put("amountRate_" + postfix,
                            formater.format(amountRate.setScale(8).divide(rate.getValue(), BigDecimal.ROUND_HALF_UP)));
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

        final Map<String, String> map = new HashMap<String, String>();
        final StringBuilder js = new StringBuilder();

        js.append(getCurrencyJS(_parameter, "rateCurrencyLink_Debit", "rate_Debit"))
            .append(getCurrencyJS(_parameter, "rateCurrencyLink_Credit", "rate_Credit"));
        map.put("eFapsFieldUpdateJS", js.toString());
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
     * Method to get the value for the number in case of updated a number.
     *
     * @param _parameter Parameter as passed by the eFaps API.
     * @return Return containing the value
     * @throws EFapsException on error
     */
    public Return updateNumberFieldValueUI(final Parameter _parameter)
        throws EFapsException
    {
        Return retVal = new Return();
        final String number = _parameter.getParameterValue("number");
        final String date = _parameter.getParameterValue("date_eFapsDate");
        String number4read;
        if (!number.isEmpty() && number.trim().length() > 0) {
            final Integer month = DateUtil.getDateFromParameter(date).getMonthOfYear();
            final String[] numTmp = number.split("-");
            try {
                final Integer numInt = Integer.parseInt(numTmp[0].trim());
                final NumberFormat nf = NumberFormat.getInstance();
                nf.setMinimumIntegerDigits(4);
                nf.setMaximumIntegerDigits(4);
                nf.setGroupingUsed(false);
                number4read = nf.format(numInt) + "-" + (month.intValue() < 10 ? "0" + month : month);
                final List<Map<String, String>> list = new ArrayList<Map<String, String>>();
                final Map<String, String> map = new HashMap<String, String>();
                map.put("number", number4read);
                list.add(map);
                retVal.put(ReturnValues.VALUES, list);
            } catch (final NumberFormatException e) {
                // in case that the user used an invalid value a default is set
                retVal = updateNumberValueUI(_parameter);
            }
        } else {
            retVal = updateNumberValueUI(_parameter);
        }
        return retVal;
    }


    /**
     * Method to get the value for the number in case of selected a new Date.
     *
     * @param _parameter Parameter as passed by the eFaps API.
     * @return Return containing the value
     * @throws EFapsException on error
     */
    public Return updateNumberValueUI(final Parameter _parameter)
        throws EFapsException
    {
        final Return retVal = new Return();
        final String dateSel = (_parameter.getParameterValue("extDate_eFapsDate") != null
                                                ? _parameter.getParameterValue("extDate_eFapsDate")
                                                                : _parameter.getParameterValue("date_eFapsDate"));
        final DateTime date = DateUtil.getDateFromParameter(dateSel);
        final DateTime newDate = new DateTime(date);
        final DateTime firstDate = newDate.dayOfMonth().withMinimumValue();
        final DateTime lastDate = newDate.dayOfMonth().withMaximumValue();

        if (firstDate != null && lastDate != null) {
            final Instance inst = (Instance) Context.getThreadContext()
                                                .getSessionAttribute(Transaction_Base.PERIODE_SESSIONKEY);
            String number = getMaxNumber(firstDate, lastDate, inst.getId());
            if (number == null) {
                final Integer month = firstDate.getMonthOfYear();
                number = "0001-" + (month.intValue() < 10 ? "0" + month : month);
            } else {
                final String numTmp = number.substring(0, number.indexOf("-"));
                final int length = numTmp.trim().length();
                final Integer numInt = Integer.parseInt(numTmp.trim()) + 1;
                final NumberFormat nf = NumberFormat.getInstance();
                nf.setMinimumIntegerDigits(length);
                nf.setMaximumIntegerDigits(length);
                nf.setGroupingUsed(false);
                number = nf.format(numInt) + number.substring(number.indexOf("-")).trim();
            }
            final List<Map<String, String>> list = new ArrayList<Map<String, String>>();
            final Map<String, String> map = new HashMap<String, String>();
            map.put("number", number);
            list.add(map);
            retVal.put(ReturnValues.VALUES, list);
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
        final Map<Long, Rate> curr2Rate = new HashMap<Long, Rate>();
        final Instance periodeInstance = (Instance) Context.getThreadContext().getSessionAttribute(
                        Transaction_Base.PERIODE_SESSIONKEY);
        if (currIds != null) {
            for (int i = 0; i < currIds.length; i++) {
                final Long id = Long.parseLong(currIds[i]);
                final Rate rate = getExchangeRate(periodeInstance, id, date, curr2Rate);
                ret.append("document.getElementsByName('").append(_targetFieldName).append("')[").append(i)
                    .append("].value='").append(rate.getLabel()).append("';")
                    .append("document.getElementsByName('").append(_targetFieldName).append(RateUI.INVERTEDSUFFIX)
                    .append("')[").append(i).append("].value='").append(rate.getCurInstance().isInvert())
                    .append("';");
            }
        }
        return ret;
    }
}
