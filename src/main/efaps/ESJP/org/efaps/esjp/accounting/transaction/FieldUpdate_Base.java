/*
 * Copyright © 2003 - 2024 The eFaps Team (-)
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
 */
package org.efaps.esjp.accounting.transaction;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.efaps.admin.datamodel.ui.RateUI;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Return;
import org.efaps.admin.event.Return.ReturnValues;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.CachedPrintQuery;
import org.efaps.db.Instance;
import org.efaps.db.PrintQuery;
import org.efaps.esjp.accounting.Case;
import org.efaps.esjp.accounting.Period;
import org.efaps.esjp.accounting.util.Accounting.ArchiveConfig;
import org.efaps.esjp.accounting.util.Accounting.ExchangeConfig;
import org.efaps.esjp.accounting.util.Accounting.LabelDefinition;
import org.efaps.esjp.accounting.util.Accounting.SummarizeConfig;
import org.efaps.esjp.accounting.util.Accounting.SummarizeDefinition;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.ci.CIERP;
import org.efaps.esjp.ci.CIFormAccounting;
import org.efaps.esjp.common.parameter.ParameterUtil;
import org.efaps.esjp.common.util.InterfaceUtils;
import org.efaps.esjp.common.util.InterfaceUtils_Base.DojoLibs;
import org.efaps.esjp.db.InstanceUtils;
import org.efaps.esjp.erp.CurrencyInst;
import org.efaps.esjp.erp.NumberFormatter;
import org.efaps.esjp.erp.RateInfo;
import org.efaps.ui.wicket.util.DateUtil;
import org.efaps.ui.wicket.util.EFapsKey;
import org.efaps.util.EFapsException;
import org.joda.time.DateTime;


/**
 *
 * @author The eFaps Team
 */
@EFapsUUID("af9547fa-3caf-4013-9e81-d637736ac62b")
@EFapsApplication("eFapsApp-Accounting")
public abstract class FieldUpdate_Base
    extends Transaction
{

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
        final String postfix = getProperty(_parameter, "TypePostfix");
        final String[] accountOIDs = _parameter.getParameterValues("accountLink_" + postfix);

        final int pos = getSelectedRow(_parameter);
        final String accountOID = accountOIDs[pos];
        final List<Map<String, Object>> list = new ArrayList<>();
        final Instance accInst = Instance.get(accountOID);

        final AccountInfo accInfo = new AccountInfo();
        accInfo.setInstance(accInst).setPostFix("_" + postfix);

        final Map<String, Object> map = new HashMap<>();

        map.put("description" + (postfix.equals("") ? "" : "_" + postfix), accInfo.getDescription());
        final StringBuilder js = new StringBuilder();
        js.append("var rv = \"").append("debit".equalsIgnoreCase(postfix)
                        ? accInfo.getLinkDebitHtml() : accInfo.getLinkCreditHtml()).append("\";")
            .append("document.getElementsByName('account2account_")
            .append(postfix).append("')[").append(pos).append("].innerHTML=rv;");
        InterfaceUtils.appendScript4FieldUpdate(map, js);
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
            final String postfix = getProperty(_parameter, "TypePostfix");
            final String[] amounts = _parameter.getParameterValues("amount_" + postfix);
            final String[] rates = _parameter.getParameterValues("rate_" + postfix);
            final String[] ratesInv = _parameter.getParameterValues("rate_" + postfix + RateUI.INVERTEDSUFFIX);

            final int pos = getSelectedRow(_parameter);
            final DecimalFormat rateFormater = NumberFormatter.get().getFormatter(0, 8);
            final DecimalFormat formater = NumberFormatter.get().getTwoDigitsFormatter();
            final BigDecimal amount = amounts[pos].isEmpty() ? BigDecimal.ZERO
                                                           : (BigDecimal) rateFormater.parse(amounts[pos]);
            BigDecimal rate = rates[pos].isEmpty() ? BigDecimal.ZERO
                                                         : (BigDecimal) rateFormater.parse(rates[pos]);
            final boolean rateInv = "true".equalsIgnoreCase(ratesInv[pos]);
            if (rateInv && rate.compareTo(BigDecimal.ZERO) != 0) {
                rate = BigDecimal.ONE.divide(rate, 12, BigDecimal.ROUND_HALF_UP);
            }
            final List<Map<String, String>> list = new ArrayList<>();
            final Instance periodInstance = new Period().evaluateCurrentPeriod(_parameter);

            final BigDecimal sum = getSum4UI(_parameter, postfix, null, null);
            final String postfix2 = "Debit".equals(postfix) ? "Credit" : "Debit";
            final BigDecimal sum2 = getSum4UI(_parameter, postfix2, null, null);
            final String sumStr = formater.format(sum) + " "
                            + new Period().getCurrency(periodInstance).getSymbol();
            final String sumStr2 = formater.format(sum.subtract(sum2).abs()) + " "
                            + new Period().getCurrency(periodInstance).getSymbol();

            final Map<String, String> map = new HashMap<>();
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
            final String postfix = getProperty(_parameter, "TypePostfix");

            final String[] currIds = _parameter.getParameterValues("rateCurrencyLink_" + postfix);
            final String[] amounts = _parameter.getParameterValues("amount_" + postfix);

            final int pos = getSelectedRow(_parameter);

            final ExchangeConfig exConf = getExchangeConfig(_parameter, null);
            final DateTime date;
            switch (exConf) {
                case DOCDATEPURCHASE:
                case DOCDATESALE:
                    final Instance docInst = Instance.get(_parameter.getParameterValues("docLink_" + postfix)[pos]);
                    if (InstanceUtils.isValid(docInst)) {
                        final PrintQuery print = CachedPrintQuery.get4Request(docInst);
                        print.addAttribute(CIERP.DocumentAbstract.Date);
                        print.execute();
                        date = print.getAttribute(CIERP.DocumentAbstract.Date);
                    } else {
                        final String dateStr = _parameter.getParameterValue("date_eFapsDate");
                        date = DateUtil.getDateFromParameter(dateStr);
                    }
                    break;
                case TRANSDATESALE:
                case TRANSDATEPURCHASE:
                default:
                    final String dateStr = _parameter.getParameterValue("date_eFapsDate");
                    date = DateUtil.getDateFromParameter(dateStr);
                    break;
            }

            final boolean sale = ExchangeConfig.TRANSDATESALE.equals(exConf)
                            || ExchangeConfig.DOCDATESALE.equals(exConf);

            final Instance periodInstance = new Period().evaluateCurrentPeriod(_parameter);
            final RateInfo rate = evaluateRate(_parameter, periodInstance, date,
                            Instance.get(CIERP.Currency.getType(), currIds[pos]));
            final DecimalFormat rateFormater = sale ? rate.getFormatter().getFrmt4SaleRateUI(null)
                            : rate.getFormatter().getFrmt4RateUI(null);
            final DecimalFormat formater = NumberFormatter.get().getTwoDigitsFormatter();
            final BigDecimal amountRate = amounts[pos].isEmpty() ? BigDecimal.ZERO
                            : (BigDecimal) rateFormater.parse(amounts[pos]);

            final BigDecimal sum = getSum4UI(_parameter, postfix, pos, rate);
            final String postfix2 = "Debit".equals(postfix) ? "Credit" : "Debit";
            final BigDecimal sum2 = getSum4UI(_parameter, postfix2, null, null);
            final String sumStr = formater.format(sum) + " "
                            + new Period().getCurrency(periodInstance).getSymbol();
            final String sumStr2 = formater.format(sum.subtract(sum2).abs()) + " "
                            + new Period().getCurrency(periodInstance).getSymbol();

            final List<Map<String, String>> list = new ArrayList<>();
            final Map<String, String> map = new HashMap<>();
            map.put("rate_" + postfix, sale ? rate.getSaleRateUIFrmt(null) : rate.getRateUIFrmt(null));
            map.put("rate_" + postfix + RateUI.INVERTEDSUFFIX, "" + rate.isInvert());
            map.put("sum" + postfix, sumStr);
            map.put("amountRate_" + postfix, formater.format(amountRate.setScale(12)
                                        .divide(sale ? rate.getSaleRate() : rate.getRate(), BigDecimal.ROUND_HALF_UP)));
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
        final List<Map<String, String>> list = new ArrayList<>();
        final Map<String, String> map = new HashMap<>();
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
        final List<Map<String, String>> list = new ArrayList<>();
        final Map<String, String> map = new HashMap<>();

        final String dateStr = _parameter.getParameterValue("date_eFapsDate");
        final DateTime date = DateUtil.getDateFromParameter(dateStr);

        // validate and correct the date, put it in _parameter so that other methods use the correct date
        final DateTime[] dates = getDateMaxMin(_parameter);
        final DateTime fromDate = dates[0];
        final DateTime toDate = dates[1];
        final StringBuilder js = new StringBuilder();
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
                js.append("require([\"dojo/query\",\"dojo/_base/fx\", \"dojo/dom-style\"], function(query,fx,style){\n")
                    .append(" query(\"input[name=\\\"date_eFapsDate\\\"]\").forEach(function(node){\n")
                    .append("var oc = style.getComputedStyle(node).backgroundColor;\n")
                    .append("fx.animateProperty({ \n")
                    .append("node: node,\n")
                    .append("duration: 700,\n")
                    .append("properties:{\n")
                    .append("backgroundColor: {start:\"red\", end:oc }\n")
                    .append(" }\n")
                    .append(" }).play();\n")
                    .append("});\n")
                    .append("});");
            }
        }
        map.put(EFapsKey.FIELDUPDATE_JAVASCRIPT.getKey(), js.toString());
        list.add(map);
        final Return retVal = new Return();
        retVal.put(ReturnValues.VALUES, list);
        return retVal;
    }

    /**
     * Method is executed on update trigger for the case dropdown.
     *
     * @param _parameter Parameter as passed from the eFaps API
     * @return list for update trigger
     * @throws EFapsException on error
     */
    public Return update4Case(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final List<Map<String, Object>> list = new ArrayList<>();
        ret.put(ReturnValues.VALUES, list);
        final SummarizeDefinition summarizeDef = new Period().getSummarizeDefinition(_parameter);
        final StringBuilder js = new StringBuilder();
        if (SummarizeDefinition.CASE.equals(summarizeDef) || SummarizeDefinition.CASEUSER.equals(summarizeDef)) {
            final Instance caseInst = Instance.get(_parameter.getParameterValue("case"));
            final SummarizeConfig config;
            final ExchangeConfig exchangeConfig;
            final ArchiveConfig archiveConfig;
            if (caseInst.isValid()) {
                final PrintQuery print = new CachedPrintQuery(caseInst, Case.CACHEKEY);
                print.addAttribute(CIAccounting.CaseAbstract.SummarizeConfig, CIAccounting.CaseAbstract.ExchangeConfig,
                                CIAccounting.CaseAbstract.ArchiveConfig);
                print.executeWithoutAccessCheck();
                config = print.getAttribute(CIAccounting.CaseAbstract.SummarizeConfig);
                exchangeConfig = print.getAttribute(CIAccounting.CaseAbstract.ExchangeConfig);
                archiveConfig = print.getAttribute(CIAccounting.CaseAbstract.ArchiveConfig);
            } else {
                config = SummarizeConfig.NONE;
                exchangeConfig = ExchangeConfig.TRANSDATESALE;
                archiveConfig = null;
            }

            final String fieldName = CIFormAccounting.Accounting_TransactionCreate4ExternalForm
                            .summarizeConfig.name;

            final Map<String, Object> map = new HashMap<>();
            map.put("exchangeConfig", exchangeConfig == null
                            ? ExchangeConfig.TRANSDATESALE.ordinal() : exchangeConfig.ordinal());
            map.put(fieldName, config.ordinal());

            if (archiveConfig != null) {
                map.put("archiveConfig", archiveConfig.getInt());
            }

            if (SummarizeDefinition.CASE.equals(summarizeDef)) {
                js.append(" query(\"input[name=\\\"").append(fieldName).append("\\\"]\").forEach(function(node){\n")
                    .append(" domAttr.set(node, \"readonly\", true); \n").append("});\n");
            }

            list.add(map);
            InterfaceUtils.appendScript4FieldUpdate(map,
                            InterfaceUtils.wrapInDojoRequire(_parameter, js, DojoLibs.QUERY, DojoLibs.DOMATTR));
        }
        return ret;
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
            final String postfix = getProperty(_parameter, "TypePostfix");

            final String[] amounts = _parameter.getParameterValues("amount_" + postfix);
            final String[] rates = _parameter.getParameterValues("rate_" + postfix);
            final String[] ratesInv = _parameter.getParameterValues("rate_" + postfix + RateUI.INVERTEDSUFFIX);

            final int pos = getSelectedRow(_parameter);
            final DecimalFormat rateFormater = NumberFormatter.get().getFormatter(0, 8);
            final DecimalFormat formater = NumberFormatter.get().getTwoDigitsFormatter();
            final BigDecimal amount = amounts[pos].isEmpty() ? BigDecimal.ZERO
                                                           : (BigDecimal) rateFormater.parse(amounts[pos]);
            BigDecimal rate = rates[pos].isEmpty() ? BigDecimal.ONE
                                                         : (BigDecimal) rateFormater.parse(rates[pos]);
            final boolean rateInv = "true".equalsIgnoreCase(ratesInv[pos]);
            if (rateInv && rate.compareTo(BigDecimal.ZERO) != 0) {
                rate = BigDecimal.ONE.divide(rate, 12, BigDecimal.ROUND_HALF_UP);
            }
            final List<Map<String, String>> list = new ArrayList<>();
            final Instance periodInstance = new Period().evaluateCurrentPeriod(_parameter);

            final BigDecimal sum = getSum4UI(_parameter, postfix, null, null);
            final String postfix2 = "Debit".equals(postfix) ? "Credit" : "Debit";
            final BigDecimal sum2 = getSum4UI(_parameter, postfix2, null, null);
            final String sumStr = formater.format(sum) + " "
                            + new Period().getCurrency(periodInstance).getSymbol();
            final String sumStr2 = formater.format(sum.subtract(sum2).abs()) + " "
                            + new Period().getCurrency(periodInstance).getSymbol();

            final Map<String, String> map = new HashMap<>();
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
        final Instance periodInstance = new Period().evaluateCurrentPeriod(_parameter);
        if (currIds != null) {
            for (int i = 0; i < currIds.length; i++) {
                final RateInfo rate = evaluateRate(_parameter, periodInstance, date,
                                Instance.get(CIERP.Currency.getType(), currIds[i]));
                ret.append("document.getElementsByName('").append(_targetFieldName).append("')[").append(i)
                    .append("].value='").append(rate.getRateUIFrmt(null)).append("';")
                    .append("document.getElementsByName('").append(_targetFieldName)
                    .append(RateUI.INVERTEDSUFFIX)
                    .append("')[").append(i).append("].value='").append(rate.isInvert())
                    .append("';");
            }
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
    public Return update4Label(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final LabelDefinition labelDef = new Period().getLabelDefinition(_parameter);
        switch (labelDef) {
            case BALANCE:
            case BALANCEREQUIRED:
                final List<Map<String, Object>> list = new ArrayList<>();
                ret.put(ReturnValues.VALUES, list);
                final String postfix = getProperty(_parameter, "TypePostfix");

                final StringBuilder js = new StringBuilder();
                final String fieldName = "labelLink_";

                final int selected = getSelectedRow(_parameter);
                final String value = _parameter.getParameterValues(fieldName + postfix)[selected];

                js.append(" query(\"select[name^=\\\"").append(fieldName).append("\\\"]\").forEach(function(node){\n")
                                .append(" node.value='").append(value).append("'; \n").append("});\n");

                final Map<String, Object> map = new HashMap<>();
                list.add(map);
                InterfaceUtils.appendScript4FieldUpdate(map,
                                InterfaceUtils.wrapInDojoRequire(_parameter, js, DojoLibs.QUERY));
                break;
            default:
                break;
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
    public Return update4AdditionalDocument(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final List<Map<String, Object>> list = new ArrayList<>();
        final Map<String, Object> map = new HashMap<>();
        list.add(map);
        ret.put(ReturnValues.VALUES, list);

        final Parameter parameter = ParameterUtil.clone(_parameter);

        final String[] docs = _parameter.getParameterValues("document");
        final String[] addDocs = _parameter.getParameterValues("additionalDocument");
        final String[] selectedRow = ArrayUtils.addAll(ArrayUtils.removeElements(docs, addDocs), addDocs);

        ParameterUtil.setParameterValues(parameter, "selectedRow", selectedRow);
        final StringBuilder ajs = new StringBuilder();

        final StringBuilder tableHtml = new FieldValue().getDocumentFieldSnipplet(parameter, ajs);
        final StringBuilder js = new StringBuilder()
                    .append("var s = \"").append(StringEscapeUtils.escapeEcmaScript(tableHtml.toString())).append("\";")
                    .append("domConstruct.place(s, \"documentTable\", \"replace\");")
                    .append("topic.publish(\"eFaps/addRowBeforeScript/transactionPositionDebitTable\");\n")
                    .append(ajs);

        InterfaceUtils.appendScript4FieldUpdate(map,
                        InterfaceUtils.wrapInDojoRequire(_parameter, js, DojoLibs.DOMCONSTRUCT, DojoLibs.TOPIC,
                                        DojoLibs.ON, DojoLibs.QUERY, DojoLibs.DOM, DojoLibs.NLTRAVERSE));

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
    public Return update4AdditionalContact(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final List<Map<String, Object>> list = new ArrayList<>();
        final Map<String, Object> map = new HashMap<>();
        list.add(map);
        ret.put(ReturnValues.VALUES, list);

        final Parameter parameter = ParameterUtil.clone(_parameter);

        final String[] docs = _parameter.getParameterValues("document");
        final String[] addDocs = _parameter.getParameterValues("additionalContact");
        final String[] selectedRow = ArrayUtils.addAll(ArrayUtils.removeElements(docs, addDocs), addDocs);

        ParameterUtil.setParameterValues(parameter, "selectedRow", selectedRow);
        final StringBuilder ajs = new StringBuilder();
        final StringBuilder tableHtml = new FieldValue().getDocumentFieldSnipplet(parameter, ajs);

        final StringBuilder js = new StringBuilder()
                    .append("var s = \"").append(StringEscapeUtils.escapeEcmaScript(tableHtml.toString())).append("\";")
                    .append("domConstruct.place(s, \"documentTable\", \"replace\");")
                    .append("topic.publish(\"eFaps/addRowBeforeScript/transactionPositionDebitTable\");\n")
                    .append(ajs);

        InterfaceUtils.appendScript4FieldUpdate(map,
                        InterfaceUtils.wrapInDojoRequire(_parameter, js, DojoLibs.DOMCONSTRUCT, DojoLibs.TOPIC,
                                        DojoLibs.ON, DojoLibs.QUERY, DojoLibs.DOM, DojoLibs.NLTRAVERSE));
        return ret;
    }
}
