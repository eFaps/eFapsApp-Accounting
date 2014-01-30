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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.lang3.StringEscapeUtils;
import org.efaps.admin.datamodel.Classification;
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
import org.efaps.db.AttributeQuery;
import org.efaps.db.Context;
import org.efaps.db.Insert;
import org.efaps.db.Instance;
import org.efaps.db.InstanceQuery;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.PrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.esjp.accounting.util.Accounting;
import org.efaps.esjp.accounting.util.AccountingSettings;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.ci.CIERP;
import org.efaps.esjp.ci.CIFormAccounting;
import org.efaps.esjp.ci.CISales;
import org.efaps.esjp.erp.Currency;
import org.efaps.esjp.erp.CurrencyInst;
import org.efaps.esjp.erp.RateFormatter;
import org.efaps.esjp.erp.RateInfo;
import org.efaps.esjp.sales.PriceUtil;
import org.efaps.esjp.sales.document.DocumentSum;
import org.efaps.ui.wicket.util.EFapsKey;
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
@EFapsUUID("3748924a-3e33-45d5-805e-9c749d9fb1d6")
@EFapsRevision("$Rev$")
public abstract class Recalculate_Base
    extends Transaction
{
    /**
     * Method to obtain html with information of the document selected.
     *
     * @param _parameter Parameter as passed from the eFaps API.
     * @return ret Return Sniplett.
     * @throws EFapsException on error.
     */
    public Return transactionOnRecalculateFieldValue(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final Instance docInst = Instance.get(_parameter.getParameterValue("selectedRow"));
        if (docInst.getType().isKindOf(CISales.DocumentSumAbstract.getType())) {
            final FieldValue fieldValue = (FieldValue) _parameter.get(ParameterValues.UIOBJECT);
            final StringBuilder html = new StringBuilder();
            html.append("<span name=\"").append(fieldValue.getField().getName()).append("\" ")
                            .append(UIInterface.EFAPSTMPTAG).append(">")
                            .append(getRecalculateInfo(_parameter, docInst)).append("</span>");
            ret.put(ReturnValues.SNIPLETT, html.toString());
        }
        return ret;
    }

    /**
     * Method for recalculate and return string.
     *
     * @param _parameter Parameter as passed from the eFaps API.
     * @param _docInst Instance of the document selected.
     * @return String.
     * @throws EFapsException on error.
     */
    protected String getRecalculateInfo(final Parameter _parameter,
                                        final Instance _docInst)
        throws EFapsException
    {
        final StringBuilder html = new StringBuilder();
        final PrintQuery print = new PrintQuery(_docInst);
        print.addAttribute(CISales.DocumentSumAbstract.RateCrossTotal,
                        CISales.DocumentSumAbstract.CrossTotal,
                        CISales.DocumentSumAbstract.RateCurrencyId,
                        CISales.DocumentSumAbstract.CurrencyId,
                        CISales.DocumentSumAbstract.Date,
                        CISales.DocumentSumAbstract.Name);
        print.execute();

        final BigDecimal rateCross = print.<BigDecimal>getAttribute(CISales.DocumentSumAbstract.RateCrossTotal);
        final BigDecimal crossTotal = print.<BigDecimal>getAttribute(CISales.DocumentSumAbstract.CrossTotal);
        final String nameDoc = print.<String>getAttribute(CISales.DocumentSumAbstract.Name);
        final Instance targetCurrInst = Instance.get(CIERP.Currency.getType(),
                        print.<Long>getAttribute(CISales.DocumentSumAbstract.RateCurrencyId));
        final Instance currentInst = Instance.get(CIERP.Currency.getType(),
                        print.<Long>getAttribute(CISales.DocumentSumAbstract.CurrencyId));
        final CurrencyInst tarCurr = new CurrencyInst(targetCurrInst);
        final CurrencyInst curr = new CurrencyInst(currentInst);

        final PriceUtil priceUtil = new PriceUtil();
        final BigDecimal[] rates = priceUtil.getRates(_parameter, targetCurrInst, currentInst);
        final BigDecimal rate = rates[2];

        final BigDecimal newCrossTotal = rateCross.compareTo(BigDecimal.ZERO) == 0
                                            ? BigDecimal.ZERO : rateCross.divide(rate, BigDecimal.ROUND_HALF_UP);
        final BigDecimal gainloss = newCrossTotal.subtract(crossTotal);

        final Map<String, String[]> map = validateInfo(_parameter, gainloss);
        final String[] accs = map.get("accs");
        final String[] check = map.get("check");

        html.append("<table>")
            .append("<tr>")
            .append("<td>").append(DBProperties.getProperty("Sales_Invoice.Label")).append("</td>")
            .append("<td colspan=\"2\">").append(nameDoc).append("</td>")
            .append("</tr>")
            .append("<td>")
            .append(DBProperties.getProperty("Sales_DocumentAbstract/RateCrossTotal.Label")).append("</td>")
            .append("<td>").append(rateCross).append(" ").append(tarCurr.getSymbol()).append("</td>")
            .append("<td>").append(crossTotal).append(" ").append(curr.getSymbol()).append("</td>")
            .append("</tr>")
            .append("<tr>")
            .append("<td>")
                .append(DBProperties.getProperty("Accounting_TransactionRecalculateForm.newTotal.Label"))
            .append("</td>")
            .append("<td colspan=\"2\" align=\"right\">").append(newCrossTotal).append(" ")
                                                                .append(curr.getSymbol()).append("</td>")
            .append("</tr>")
            .append("<tr>")
            .append("<td>");
        if (gainloss.compareTo(BigDecimal.ZERO) == -1) {
            html.append(DBProperties.getProperty("Accounting_TransactionRecalculateForm.loss.Label"));
        } else {
            html.append(DBProperties.getProperty("Accounting_TransactionRecalculateForm.gain.Label"));
        }
        html.append("</td>")
            .append("<td colspan=\"2\" align=\"right\">").append(gainloss.abs()).append(" ")
                                                                .append(curr.getSymbol()).append("</td>")
            .append("</tr>")
            .append("<tr>")
            .append("<td>")
            .append(DBProperties.getProperty("Accounting_TransactionPositionDebit.Label")).append("</td>")
            .append("<td colspan=\"2\" align=\"right\">");
        if (checkAccounts(accs, 0, check).length() > 0) {
            html.append(checkAccounts(accs, 0, check));
        } else {
            html.append(DBProperties.getProperty("Accounting_TransactionRecalculateForm.reviseConfig.Label"));
        }
        html.append("</td>")
            .append("</tr>")
            .append("<tr>")
            .append("<td>")
            .append(DBProperties.getProperty("Accounting_TransactionPositionCredit.Label")).append("</td>")
            .append("<td colspan=\"2\" align=\"right\">");
        if (checkAccounts(accs, 1, check).length() > 0) {
            html.append(checkAccounts(accs, 1, check));
        } else {
            html.append(DBProperties.getProperty("Accounting_TransactionRecalculateForm.reviseConfig.Label"));
        }
        html.append("</td>")
            .append("</tr>")
            .append("</table>");
        return html.toString();
    }

    /**
     * Method for update rate and transactions if changed date to return html and rate amount.
     *
     * @param _parameter Parameter as passed from the eFaps API.
     * @return ret with values.
     * @throws EFapsException on error.
     */
    public Return update4DateOnRecalculate(final Parameter _parameter)
        throws EFapsException
    {
        final Return retVal = new Return();
        final Instance docInst = Instance.get(_parameter.getParameterValue("docInst"));
        if (docInst.getType().isKindOf(CISales.DocumentSumAbstract.getType())) {

            final PrintQuery print = new PrintQuery(docInst);
            print.addAttribute(CISales.DocumentSumAbstract.Rate);
            print.execute();

            final Object[] rateObj = print.getAttribute(CISales.DocumentSumAbstract.Rate);

            final Currency currency = getCurrency(_parameter);
            final RateInfo rateInfo = currency.evaluateRateInfo(_parameter, rateObj);

            final RateFormatter frmt = getRateFormatter(_parameter);

            final String rateStr = frmt.getFrmt4Rate().format(rateInfo.getRate());
            final List<Map<String, String>> list = new ArrayList<Map<String, String>>();
            final Map<String, String> map = new HashMap<String, String>();
            map.put("rate", rateStr);
            final StringBuilder js = new StringBuilder();
            js.append("document.getElementsByName('transactions')[0].innerHTML='")
                .append(StringEscapeUtils.escapeEcmaScript(getRecalculateInfo(_parameter, docInst)))
                .append("';");
            map.put(EFapsKey.FIELDUPDATE_JAVASCRIPT.getKey(), js.toString());
            list.add(map);
            retVal.put(ReturnValues.VALUES, list);
        }
        return retVal;
    }

    /**
     * Method to recalculate rate.
     *
     * @param _parameter Parameter as passed from the eFaps API.
     * @return new Return.
     * @throws EFapsException on error.
     */
    public Return recalculateRate(final Parameter _parameter)
        throws EFapsException
    {
        final Instance docInst = Instance.get(_parameter.getParameterValue("docInst"));
        final PrintQuery print = new PrintQuery(docInst);
        print.addAttribute(CISales.DocumentSumAbstract.RateCrossTotal,
                           CISales.DocumentSumAbstract.CrossTotal,
                           CISales.DocumentSumAbstract.RateCurrencyId,
                           CISales.DocumentSumAbstract.CurrencyId,
                           CISales.DocumentSumAbstract.Date,
                           CISales.DocumentSumAbstract.Name);
        print.execute();

        final BigDecimal rateCross = print.<BigDecimal>getAttribute(CISales.DocumentSumAbstract.RateCrossTotal);
        final BigDecimal crossTotal = print.<BigDecimal>getAttribute(CISales.DocumentSumAbstract.CrossTotal);
        final DateTime dateDoc = print.<DateTime>getAttribute(CISales.DocumentSumAbstract.Date);
        final String nameDoc = print.<String>getAttribute(CISales.DocumentSumAbstract.Name);
        final Instance targetCurrInst = Instance.get(CIERP.Currency.getType(),
                        print.<Long> getAttribute(CISales.DocumentSumAbstract.RateCurrencyId));
        final Instance currentInst = Instance.get(CIERP.Currency.getType(),
                        print.<Long> getAttribute(CISales.DocumentSumAbstract.CurrencyId));
        final CurrencyInst tarCurr = new CurrencyInst(targetCurrInst);
        final CurrencyInst curr = new CurrencyInst(currentInst);

        final PriceUtil priceUtil = new PriceUtil();
        final BigDecimal[] rates = priceUtil.getRates(_parameter, targetCurrInst, currentInst);
        final BigDecimal rate = rates[2];

        final BigDecimal newCrossTotal = rateCross.compareTo(BigDecimal.ZERO) == 0
                                            ? BigDecimal.ZERO : rateCross.divide(rate, BigDecimal.ROUND_HALF_UP);
        final BigDecimal gainloss = newCrossTotal.subtract(crossTotal);
        final Map<String, String[]> map = validateInfo(_parameter, gainloss);
        final String[] accs = map.get("accs");
        final String[] check = map.get("check");
        if (checkAccounts(accs, 0, check).length() > 0 && checkAccounts(accs, 1, check).length() > 0) {
            if (gainloss.compareTo(BigDecimal.ZERO) != 0) {
                if (!tarCurr.equals(curr)) {
                    final String[] accOids = map.get("accountOids");

                    final Insert insert = new Insert(CIAccounting.Transaction);
                    final StringBuilder description = new StringBuilder();
                    final DateTimeFormatter formater = DateTimeFormat.mediumDate();
                    final String dateStr = dateDoc.withChronology(Context.getThreadContext().getChronology()).toString(
                                    formater.withLocale(Context.getThreadContext().getLocale()));
                    description.append(DBProperties
                                .getProperty("Accounting_TransactionRecalculateForm.TxnRecalculate.Label"))
                                .append(" ").append(nameDoc).append(" ").append(dateStr);
                    insert.add(CIAccounting.Transaction.Description, description);
                    insert.add(CIAccounting.Transaction.Date, _parameter.getParameterValue("date"));
                    insert.add(CIAccounting.Transaction.PeriodeLink, _parameter.getInstance().getId());
                    insert.add(CIAccounting.Transaction.Status,
                                                    Status.find(CIAccounting.TransactionStatus.uuid, "Open").getId());
                    insert.execute();

                    final Instance instance = insert.getInstance();
                    // create classifications
                    final Classification classification1 = (Classification) CIAccounting.TransactionClass.getType();
                    final Insert relInsert1 = new Insert(classification1.getClassifyRelationType());
                    relInsert1.add(classification1.getRelLinkAttributeName(), instance.getId());
                    relInsert1.add(classification1.getRelTypeAttributeName(), classification1.getId());
                    relInsert1.execute();

                    final Insert classInsert1 = new Insert(classification1);
                    classInsert1.add(classification1.getLinkAttributeName(), instance.getId());
                    classInsert1.execute();

                    final Classification classification =
                                    (Classification) CIAccounting.TransactionClassDocument.getType();
                    final Insert relInsert = new Insert(classification.getClassifyRelationType());
                    relInsert.add(classification.getRelLinkAttributeName(), instance.getId());
                    relInsert.add(classification.getRelTypeAttributeName(), classification.getId());
                    relInsert.execute();

                    final Insert classInsert = new Insert(classification);
                    classInsert.add(classification.getLinkAttributeName(), instance.getId());
                    classInsert.add(CIAccounting.TransactionClassDocument.DocumentLink, docInst.getId());
                    classInsert.execute();

                    final Insert insert2 = new Insert(CIAccounting.TransactionPositionCredit);
                    insert2.add(CIAccounting.TransactionPositionCredit.TransactionLink, instance.getId());
                    insert2.add(CIAccounting.TransactionPositionCredit.AccountLink, Instance.get(accOids[1]).getId());
                    insert2.add(CIAccounting.TransactionPositionCredit.CurrencyLink, curr.getInstance().getId());
                    insert2.add(CIAccounting.TransactionPositionCredit.RateCurrencyLink, curr.getInstance().getId());
                    insert2.add(CIAccounting.TransactionPositionCredit.Rate, new Object[] { 1, 1 });
                    insert2.add(CIAccounting.TransactionPositionCredit.RateAmount, gainloss.abs());
                    insert2.add(CIAccounting.TransactionPositionCredit.Amount, gainloss.abs());
                    insert2.execute();

                    final Insert insert3 = new Insert(CIAccounting.TransactionPositionDebit);
                    insert3.add(CIAccounting.TransactionPositionDebit.TransactionLink, instance.getId());
                    insert3.add(CIAccounting.TransactionPositionDebit.AccountLink, Instance.get(accOids[0]).getId());
                    insert3.add(CIAccounting.TransactionPositionDebit.CurrencyLink, curr.getInstance().getId());
                    insert3.add(CIAccounting.TransactionPositionDebit.RateCurrencyLink, curr.getInstance().getId());
                    insert3.add(CIAccounting.TransactionPositionDebit.Rate, new Object[] { 1, 1 });
                    insert3.add(CIAccounting.TransactionPositionDebit.RateAmount, gainloss.abs().negate());
                    insert3.add(CIAccounting.TransactionPositionDebit.Amount, gainloss.abs().negate());
                    insert3.execute();

                    _parameter.put(ParameterValues.INSTANCE, docInst);
                    new DocumentSum().recalculateRate(_parameter);
                }
            }
        }
        return new Return();
    }


    /**
     * Method for check if account name exits for proceed to create.
     *
     * @param _accountValues String array with values.
     * @param _pos position if type credit or debit.
     * @param _checkValues values true or false.
     * @return str String.
     */
    protected String checkAccounts(final String[] _accountValues,
                                 final int _pos,
                                 final String[] _checkValues)
    {
        final StringBuilder str = new StringBuilder();
        if (_checkValues[_pos] != null && "true".equals(_checkValues[_pos])) {
            str.append(_accountValues[_pos]);
        }
        return str.toString();

    }

    /**
     * Method for obtain map with values to validate accounts.
     *
     * @param _parameter Parameter as passed from the eFaps API.
     * @param _gainloss BigDecimal of the gain or loss in the document.
     * @return map.
     * @throws EFapsException on error.
     */
    private Map<String, String[]> validateInfo(final Parameter _parameter,
                                               final BigDecimal _gainloss)
        throws EFapsException
    {
        final Properties gainLoss = Accounting.getSysConfig().getObjectAttributeValueAsProperties(
                        _parameter.getInstance());
        final String accStr;

        if (_gainloss.signum() > 0) {
            accStr = gainLoss.getProperty(AccountingSettings.PERIOD_EXCHANGELOSS);
        } else {
            accStr = gainLoss.getProperty(AccountingSettings.PERIOD_EXCHANGEGAIN);
        }
        String[] accs = new String[2];
        final String[] check = new String[2];
        final String[] accOids = new String[2];
        if (accStr != null) {
            accs = accStr.split(";");

            if (accs.length > 0) {
                for (int i = 0; i < accs.length; i++) {
                    final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.AccountAbstract);
                    queryBldr.addWhereAttrEqValue(CIAccounting.AccountAbstract.PeriodeAbstractLink,
                                    _parameter.getInstance().getId());
                    queryBldr.addWhereAttrEqValue(CIAccounting.AccountAbstract.Name, accs[i].toString());
                    final MultiPrintQuery multi = queryBldr.getPrint();
                    multi.addAttribute(CIAccounting.AccountAbstract.OID);
                    multi.execute();
                    while (multi.next()) {
                        check[i] = "true";
                        accOids[i] = multi.<String>getAttribute(CIAccounting.AccountAbstract.OID);
                    }
                }
            }
        }
        final Map<String, String[]> map = new HashMap<String, String[]>();
        map.put("check", check);
        map.put("accs", accs);
        map.put("accountOids", accOids);
        return map;
    }

    /**
     * Validate that on ly simple accounts are selected.
     * @param _parameter Parameter as passed by the eFasp API
     * @return new Return
     * @throws EFapsException on error
     */
    public Return validate4SimpleAccount(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        boolean check = true;
        final String[] relOIDs = (String[]) Context.getThreadContext()
                        .getSessionAttribute(CIFormAccounting.Accounting_GainLoss4SimpleAccountForm.config2period.name);
        for (final String oid : relOIDs) {
            final Instance relInst = Instance.get(oid);
            if (!(relInst.isValid() && relInst.getType().isKindOf(
                            CIAccounting.AccountConfigSimple2Periode.getType()))) {
                check = false;
                break;
            }
        }

        if (check) {
            ret.put(ReturnValues.TRUE, true);
        } else {
            ret.put(ReturnValues.SNIPLETT,
                            DBProperties.getProperty(Recalculate.class.getName() + ".validate4SimpleAccount"));
        }

        return ret;
    }


    /**
     * @param _parameter Parameter as passed by the eFasp API
     * @return new Return
     * @throws EFapsException on error
     */
    public Return createGainLoss4SimpleAccount(final Parameter _parameter)
        throws EFapsException
    {
        final PrintQuery printPer = new PrintQuery(_parameter.getInstance());
        final SelectBuilder selCurrInst = SelectBuilder.get().linkto(CIAccounting.Periode.CurrencyLink).instance();
        printPer.addSelect(selCurrInst);
        printPer.addAttribute(CIAccounting.Periode.FromDate, CIAccounting.Periode.CurrencyLink);
        printPer.execute();
        final DateTime dateFrom = printPer.<DateTime>getAttribute(CIAccounting.Periode.FromDate);
        final Instance currencyInst = printPer.<Instance>getSelect(selCurrInst);

        final String[] relOIDs = (String[]) Context.getThreadContext()
                        .getSessionAttribute(CIFormAccounting.Accounting_GainLoss4SimpleAccountForm.config2period.name);
        final DateTime dateTo = new DateTime(_parameter.getParameterValue(
                        CIFormAccounting.Accounting_GainLoss4SimpleAccountForm.transactionDate.name));
        final DateTime dateEx = new DateTime(_parameter.getParameterValue(
                        CIFormAccounting.Accounting_GainLoss4SimpleAccountForm.exchangeDate.name));
        Insert insert = null;
        BigDecimal totalSum = BigDecimal.ZERO;

        for (final String oid : relOIDs) {
            Instance tarCurInst = null;
            final Instance relInst = Instance.get(oid);
            final PrintQuery print = new PrintQuery(relInst);
            final SelectBuilder selAccount = new SelectBuilder()
                            .linkto(CIAccounting.AccountConfigSimple2Periode.FromLink).oid();
            print.addSelect(selAccount);
            print.addAttribute(CIAccounting.AccountConfigSimple2Periode.IsSale);
            if (print.execute()) {
                final Instance instAcc = Instance.get(print.<String>getSelect(selAccount));
                final boolean isSale = print.<Boolean>getAttribute(CIAccounting.AccountConfigSimple2Periode.IsSale);
                final QueryBuilder attrQuerBldr = new QueryBuilder(CIAccounting.Transaction);
                attrQuerBldr.addWhereAttrEqValue(CIAccounting.Transaction.PeriodeLink, _parameter.getInstance());
                attrQuerBldr.addWhereAttrGreaterValue(CIAccounting.Transaction.Date, dateFrom.minusMinutes(1));
                attrQuerBldr.addWhereAttrLessValue(CIAccounting.Transaction.Date, dateTo.plusDays(1));
                final AttributeQuery attrQuery = attrQuerBldr.getAttributeQuery(CIAccounting.Transaction.ID);

                final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.TransactionPositionAbstract);
                queryBldr.addWhereAttrEqValue(CIAccounting.TransactionPositionAbstract.AccountLink, instAcc);
                queryBldr.addWhereAttrInQuery(CIAccounting.TransactionPositionAbstract.TransactionLink, attrQuery);
                final MultiPrintQuery multi = queryBldr.getPrint();
                final SelectBuilder selTRInst = SelectBuilder.get()
                                .linkto(CIAccounting.TransactionPositionAbstract.RateCurrencyLink).instance();
                multi.addSelect(selTRInst);
                multi.addAttribute(CIAccounting.TransactionPositionAbstract.Amount,
                                CIAccounting.TransactionPositionAbstract.RateAmount);
                BigDecimal gainlossSum = BigDecimal.ZERO;
                multi.execute();
                while (multi.next()) {
                    final BigDecimal oldRateAmount = multi
                                    .<BigDecimal>getAttribute(CIAccounting.TransactionPositionAbstract.RateAmount);
                    final BigDecimal oldAmount = multi
                                    .<BigDecimal>getAttribute(CIAccounting.TransactionPositionAbstract.Amount);
                    final Instance targetCurrInst = multi.<Instance>getSelect(selTRInst);
                    final BigDecimal rate = evaluateRate(_parameter, dateEx, currencyInst, targetCurrInst, isSale);
                    final BigDecimal newAmount = oldRateAmount.divide(rate, BigDecimal.ROUND_HALF_UP);

                    BigDecimal gainloss = BigDecimal.ZERO;
                    if (!currencyInst.equals(targetCurrInst)) {
                        gainloss = newAmount.subtract(oldAmount);
                        tarCurInst = targetCurrInst;
                    } else {
                        gainloss = newAmount;
                    }
                    gainlossSum = gainlossSum.add(gainloss);
                }
                totalSum = totalSum.add(gainlossSum);

                final Map<String, String[]> map = validateInfo(_parameter, gainlossSum);
                final String[] accs = map.get("accs");
                final String[] check = map.get("check");

                if (checkAccounts(accs, 0, check).length() > 0) {
                    if (gainlossSum.compareTo(BigDecimal.ZERO) != 0) {
                        final String[] accOids = map.get("accountOids");
                        if (insert == null) {
                            final String descr = DBProperties.getFormatedDBProperty(Recalculate.class.getName()
                                            + ".gainLoss4SimpleAccountTransDesc", dateTo.toDate());
                            insert = new Insert(CIAccounting.Transaction);
                            insert.add(CIAccounting.Transaction.Description, descr);
                            insert.add(CIAccounting.Transaction.Date, dateTo);
                            insert.add(CIAccounting.Transaction.PeriodeLink, _parameter.getInstance());
                            insert.add(CIAccounting.Transaction.Status,
                                            Status.find(CIAccounting.TransactionStatus.Open));
                            insert.execute();
                        }

                        Insert insertPos = new Insert(CIAccounting.TransactionPositionDebit);
                        insertPos.add(CIAccounting.TransactionPositionDebit.TransactionLink, insert.getInstance());
                        if (gainlossSum.signum() > 0) {
                            insertPos.add(CIAccounting.TransactionPositionDebit.AccountLink, Instance.get(accOids[0]));
                        } else {
                            insertPos.add(CIAccounting.TransactionPositionDebit.AccountLink, instAcc);
                        }
                        insertPos.add(CIAccounting.TransactionPositionDebit.Amount, gainlossSum.abs().negate());
                        insertPos.add(CIAccounting.TransactionPositionDebit.RateAmount, BigDecimal.ZERO);
                        insertPos.add(CIAccounting.TransactionPositionDebit.CurrencyLink, currencyInst);
                        insertPos.add(CIAccounting.TransactionPositionDebit.RateCurrencyLink, tarCurInst);
                        insertPos.add(CIAccounting.TransactionPositionDebit.Rate,
                                        new Object[] { BigDecimal.ONE, BigDecimal.ONE });
                        insertPos.execute();

                        insertPos = new Insert(CIAccounting.TransactionPositionCredit);
                        insertPos.add(CIAccounting.TransactionPositionCredit.TransactionLink, insert.getInstance());
                        if (gainlossSum.signum() > 0) {
                            insertPos.add(CIAccounting.TransactionPositionCredit.AccountLink, instAcc);
                        } else {
                            insertPos.add(CIAccounting.TransactionPositionCredit.AccountLink, Instance.get(accOids[0]));
                        }
                        insertPos.add(CIAccounting.TransactionPositionCredit.Amount, gainlossSum.abs());
                        insertPos.add(CIAccounting.TransactionPositionCredit.RateAmount, BigDecimal.ZERO);
                        insertPos.add(CIAccounting.TransactionPositionCredit.CurrencyLink, currencyInst);
                        insertPos.add(CIAccounting.TransactionPositionCredit.RateCurrencyLink, tarCurInst);
                        insertPos.add(CIAccounting.TransactionPositionCredit.Rate,
                                        new Object[] {BigDecimal.ONE, BigDecimal.ONE });
                        insertPos.execute();
                    }
                }
            }
        }
        if (insert != null) {
            final Instance instance = insert.getInstance();
            // create classifications
            final Classification classification1 = (Classification) CIAccounting.TransactionClass.getType();
            final Insert relInsert1 = new Insert(classification1.getClassifyRelationType());
            relInsert1.add(classification1.getRelLinkAttributeName(), instance);
            relInsert1.add(classification1.getRelTypeAttributeName(), classification1.getId());
            relInsert1.execute();

            final Insert classInsert1 = new Insert(classification1);
            classInsert1.add(classification1.getLinkAttributeName(), instance);
            classInsert1.execute();

            final Classification classification =
                            (Classification) CIAccounting.TransactionClassGainLoss.getType();
            final Insert relInsert = new Insert(classification.getClassifyRelationType());
            relInsert.add(classification.getRelLinkAttributeName(), instance);
            relInsert.add(classification.getRelTypeAttributeName(), classification.getId());
            relInsert.execute();

            final Insert classInsert = new Insert(classification);
            classInsert.add(classification.getLinkAttributeName(), instance);
            classInsert.add(CIAccounting.TransactionClassGainLoss.Amount, totalSum);
            classInsert.add(CIAccounting.TransactionClassGainLoss.RateAmount, totalSum);
            classInsert.add(CIAccounting.TransactionClassGainLoss.CurrencyLink, currencyInst);
            classInsert.add(CIAccounting.TransactionClassGainLoss.RateCurrencyLink, currencyInst);
            classInsert.add(CIAccounting.TransactionClassGainLoss.Rate,
                            new Object[] { BigDecimal.ONE, BigDecimal.ONE });
            classInsert.execute();
        }
        // clean up
        Context.getThreadContext().removeSessionAttribute(
                        CIFormAccounting.Accounting_GainLoss4SimpleAccountForm.config2period.name);
        return new Return();
    }

    /**
     * @param _parameter Parameter as passed by the eFaps API
     * @param _date     date the rate must be evaluated for
     * @param _currentCurrencyInst instance of the currency the rate is wanted for
     * @param _targetCurrencyInst instance of the currency the rate is wanted for
     * @param _sale sales exchange rate or not
     * @return rate
     * @throws EFapsException on error
     */
    protected BigDecimal evaluateRate(final Parameter _parameter,
                                      final DateTime _date,
                                      final Instance _currentCurrencyInst,
                                      final Instance _targetCurrencyInst,
                                      final boolean _sale)
        throws EFapsException
    {
        final Currency currency = new Currency()
        {
            @Override
            protected Type getType4ExchangeRate(final Parameter _parameter)
            {
                return CIAccounting.ERP_CurrencyRateAccounting.getType();
            }
        };
        final RateInfo[] rateInfos = currency.evaluateRateInfos(_parameter, _date, _currentCurrencyInst,
                        _targetCurrencyInst);
        return _sale ? rateInfos[2].getSaleRate() : rateInfos[2].getRate();
    }


    public Return createGainLoss4DocumentAccount(final Parameter _parameter)
        throws EFapsException
    {
        final Set<String> setAccounts = getAccounts4DocumentConfig(_parameter);

        final String[] oidsDoc = (String[]) Context.getThreadContext().getSessionAttribute(
                        CIFormAccounting.Accounting_GainLoss4DocumentAccountForm.selectedDocuments.name);
        final DateTime dateTo = new DateTime(_parameter.getParameterValue(
                        CIFormAccounting.Accounting_GainLoss4DocumentAccountForm.transactionDate.name));
        for (final String oid : oidsDoc) {
            final Instance instDoc = Instance.get(oid);

            final QueryBuilder attrQuerBldr = new QueryBuilder(CIAccounting.TransactionClassDocument);
            attrQuerBldr.addWhereAttrEqValue(CIAccounting.TransactionClassDocument.DocumentLink, instDoc.getId());
            final AttributeQuery attrQuery = attrQuerBldr.getAttributeQuery(CIAccounting.TransactionClassDocument.TransactionLink);

            // filter classification Document
            final QueryBuilder attrQueryBldr2 = new QueryBuilder(CIAccounting.Transaction);
            attrQueryBldr2.addWhereAttrEqValue(CIAccounting.Transaction.PeriodeLink, _parameter.getInstance().getId());
            attrQueryBldr2.addWhereAttrInQuery(CIAccounting.Transaction.ID, attrQuery);
            final AttributeQuery attrQuery2 = attrQuerBldr.getAttributeQuery(CIAccounting.Transaction.ID);

            final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.TransactionPositionAbstract);
            queryBldr.addWhereAttrInQuery(CIAccounting.TransactionPositionAbstract.TransactionLink, attrQuery2);
            final MultiPrintQuery multi = queryBldr.getPrint();
            multi.addAttribute(CIAccounting.TransactionPositionAbstract.Amount,
                            CIAccounting.TransactionPositionAbstract.RateAmount,
                            CIAccounting.TransactionPositionAbstract.CurrencyLink,
                            CIAccounting.TransactionPositionAbstract.RateCurrencyLink);
            final SelectBuilder selAcc = new SelectBuilder()
                            .linkto(CIAccounting.TransactionPositionAbstract.AccountLink).oid();
            multi.addSelect(selAcc);
            multi.execute();
            final Map<String, TargetAccount> map = new HashMap<String, TargetAccount>();
            while (multi.next()) {
                final Instance accountInst = Instance.get(multi.<String>getSelect(selAcc));
                if (setAccounts.contains(accountInst.getOid())) {
                    final BigDecimal oldRateAmount = multi
                                    .<BigDecimal>getAttribute(CIAccounting.TransactionPositionAbstract.RateAmount);
                    final BigDecimal oldAmount = multi
                                    .<BigDecimal>getAttribute(CIAccounting.TransactionPositionAbstract.Amount);
                    final Instance targetCurrInst = Instance.get(CIERP.Currency.getType(),
                                multi.<Long>getAttribute(CIAccounting.TransactionPositionAbstract.RateCurrencyLink));
                    final Instance currentInst = Instance.get(CIERP.Currency.getType(),
                                multi.<Long>getAttribute(CIAccounting.TransactionPositionAbstract.CurrencyLink));

                    final PriceUtil priceUtil = new PriceUtil();
                    final BigDecimal[] rates = priceUtil.getRates(_parameter, targetCurrInst, currentInst);
                    final BigDecimal rate = rates[2];
                    final BigDecimal newAmount = oldRateAmount.divide(rate, BigDecimal.ROUND_HALF_UP);

                    BigDecimal gainloss = BigDecimal.ZERO;
                    if (!currentInst.equals(targetCurrInst)) {
                        gainloss = newAmount.subtract(oldAmount);
                    } else {
                        gainloss = newAmount;
                    }
                    if (map.containsKey(accountInst.getOid())) {
                        final TargetAccount tarAcc = map.get(accountInst.getOid());
                        tarAcc.add(gainloss);
                    } else {
                        final TargetAccount tarAcc = new TargetAccount(accountInst.getOid(), "", "", gainloss);
                        tarAcc.setAmountRate(gainloss);
                        tarAcc.setCurrInstance(currentInst);
                        map.put(accountInst.getOid(), tarAcc);
                    }
                }
            }

            if (!map.isEmpty()) {
                Insert insert = null;
                CurrencyInst curr = null;
                BigDecimal gainlossSum = BigDecimal.ZERO;
                for (final Entry<String, TargetAccount> entry : map.entrySet()) {
                    final TargetAccount tarAcc = entry.getValue();
                    final Instance instAcc = Instance.get(entry.getKey());
                    final BigDecimal gainloss = tarAcc.getAmount();
                    gainlossSum = gainlossSum.add(gainloss);
                    final Instance currInstance = tarAcc.getCurrInstance();
                    curr = new CurrencyInst(currInstance);
                    final Map<String, String[]> mapVal = validateInfo(_parameter, gainloss);
                    final String[] accs = mapVal.get("accs");
                    final String[] check = mapVal.get("check");

                    if (checkAccounts(accs, 0, check).length() > 0) {
                        if (gainloss.compareTo(BigDecimal.ZERO) != 0) {
                            final String[] accOids = mapVal.get("accountOids");
                            if (insert == null) {
                                final DateTimeFormatter formater = DateTimeFormat.mediumDate();
                                final String dateStr = dateTo.withChronology(
                                                Context.getThreadContext().getChronology()).toString(
                                                formater.withLocale(Context.getThreadContext().getLocale()));
                                final StringBuilder description = new StringBuilder();
                                description.append(DBProperties
                                                .getProperty("Accounting_DocumentAccountForm.TxnRecalculate.Label"))
                                                .append(" ").append(dateStr);
                                insert = new Insert(CIAccounting.Transaction);
                                insert.add(CIAccounting.Transaction.Description, description.toString());
                                insert.add(CIAccounting.Transaction.Date, dateTo);
                                insert.add(CIAccounting.Transaction.PeriodeLink, _parameter.getInstance().getId());
                                insert.add(CIAccounting.Transaction.Status,
                                                Status.find(CIAccounting.TransactionStatus.uuid, "Open").getId());
                                insert.execute();
                            }

                            Insert insertPos = new Insert(CIAccounting.TransactionPositionCredit);
                            insertPos.add(CIAccounting.TransactionPositionCredit.TransactionLink, insert.getId());
                            if (gainloss.signum() < 0) {
                                insertPos.add(CIAccounting.TransactionPositionCredit.AccountLink, instAcc.getId());
                            } else {
                                insertPos.add(CIAccounting.TransactionPositionCredit.AccountLink,
                                                Instance.get(accOids[0]).getId());
                            }
                            insertPos.add(CIAccounting.TransactionPositionCredit.Amount, gainloss.abs());
                            insertPos.add(CIAccounting.TransactionPositionCredit.RateAmount, gainloss.abs());
                            insertPos.add(CIAccounting.TransactionPositionCredit.CurrencyLink, currInstance.getId());
                            insertPos.add(CIAccounting.TransactionPositionCredit.RateCurrencyLink,
                                            currInstance.getId());
                            insertPos.add(CIAccounting.TransactionPositionCredit.Rate, new Object[] {
                                            BigDecimal.ONE, BigDecimal.ONE });
                            insertPos.execute();

                            insertPos = new Insert(CIAccounting.TransactionPositionDebit);
                            insertPos.add(CIAccounting.TransactionPositionDebit.TransactionLink, insert.getId());
                            if (gainloss.signum() < 0) {
                                insertPos.add(CIAccounting.TransactionPositionDebit.AccountLink,
                                                Instance.get(accOids[0]).getId());
                            } else {
                                insertPos.add(CIAccounting.TransactionPositionDebit.AccountLink, instAcc.getId());
                            }
                            insertPos.add(CIAccounting.TransactionPositionDebit.Amount, gainloss.abs().negate());
                            insertPos.add(CIAccounting.TransactionPositionDebit.RateAmount, gainloss.abs().negate());
                            insertPos.add(CIAccounting.TransactionPositionDebit.CurrencyLink, currInstance.getId());
                            insertPos.add(CIAccounting.TransactionPositionDebit.RateCurrencyLink,
                                            currInstance.getId());
                            insertPos.add(CIAccounting.TransactionPositionDebit.Rate, new Object[] {
                                            BigDecimal.ONE, BigDecimal.ONE });
                            insertPos.execute();
                        }
                    }
                }
                if (insert != null) {
                    final Instance instance = insert.getInstance();
                    // create classifications
                    final Classification classification1 = (Classification) CIAccounting.TransactionClass.getType();
                    final Insert relInsert1 = new Insert(classification1.getClassifyRelationType());
                    relInsert1.add(classification1.getRelLinkAttributeName(), instance.getId());
                    relInsert1.add(classification1.getRelTypeAttributeName(), classification1.getId());
                    relInsert1.execute();

                    final Insert classInsert1 = new Insert(classification1);
                    classInsert1.add(classification1.getLinkAttributeName(), instance.getId());
                    classInsert1.execute();

                    final Classification classification =
                                    (Classification) CIAccounting.TransactionClassGainLoss.getType();
                    final Insert relInsert = new Insert(classification.getClassifyRelationType());
                    relInsert.add(classification.getRelLinkAttributeName(), instance.getId());
                    relInsert.add(classification.getRelTypeAttributeName(), classification.getId());
                    relInsert.execute();

                    final Insert classInsert = new Insert(classification);
                    classInsert.add(classification.getLinkAttributeName(), instance.getId());
                    classInsert.add(CIAccounting.TransactionClassGainLoss.Amount, gainlossSum);
                    classInsert.add(CIAccounting.TransactionClassGainLoss.RateAmount, gainlossSum);
                    classInsert.add(CIAccounting.TransactionClassGainLoss.CurrencyLink, curr.getInstance().getId());
                    classInsert.add(CIAccounting.TransactionClassGainLoss.RateCurrencyLink,
                                    curr.getInstance().getId());
                    classInsert.add(CIAccounting.TransactionClassGainLoss.Rate,
                                    new Object[] {BigDecimal.ONE, BigDecimal.ONE});
                    classInsert.execute();

                    final Classification classification2 =
                                    (Classification) CIAccounting.TransactionClassDocument.getType();
                    final Insert relInsert2 = new Insert(classification2.getClassifyRelationType());
                    relInsert2.add(classification2.getRelLinkAttributeName(), instance.getId());
                    relInsert2.add(classification2.getRelTypeAttributeName(), classification2.getId());
                    relInsert2.execute();

                    final Insert classInsert2 = new Insert(classification2);
                    classInsert2.add(classification2.getLinkAttributeName(), instance.getId());
                    classInsert2.add(CIAccounting.TransactionClassDocument.DocumentLink, instDoc.getId());
                    classInsert2.execute();
                }
            }
        }

        return new Return();
    }

    protected Set<String> getAccounts4DocumentConfig(final Parameter _parameter)
        throws EFapsException
    {
        final Set<String> set = new HashSet<String>();
        final QueryBuilder attrQueryBldr = new QueryBuilder(CIAccounting.AccountConfigDocument2Periode);
        final AttributeQuery attrQuery = attrQueryBldr.getAttributeQuery(CIAccounting.AccountConfigDocument2Periode.FromLink);

        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.AccountAbstract);
        queryBldr.addWhereAttrInQuery(CIAccounting.AccountAbstract.ID, attrQuery);
        final InstanceQuery query = queryBldr.getQuery();
        query.execute();
        while (query.next()) {
            set.add(query.getCurrentValue().getOid());
        }
        return set;
    }


}
