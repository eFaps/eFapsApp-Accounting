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
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.efaps.admin.datamodel.ui.RateUI;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Return;
import org.efaps.admin.event.Return.ReturnValues;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.CachedPrintQuery;
import org.efaps.db.Instance;
import org.efaps.db.PrintQuery;
import org.efaps.esjp.accounting.Period;
import org.efaps.esjp.accounting.transaction.TransInfo_Base.PositionInfo;
import org.efaps.esjp.accounting.transaction.evaluation.AbstractEvaluation;
import org.efaps.esjp.accounting.util.Accounting;
import org.efaps.esjp.accounting.util.Accounting.CalculateConfig;
import org.efaps.esjp.accounting.util.Accounting.ExchangeConfig;
import org.efaps.esjp.ci.CIERP;
import org.efaps.esjp.common.parameter.ParameterUtil;
import org.efaps.esjp.db.InstanceUtils;
import org.efaps.esjp.erp.Currency;
import org.efaps.esjp.erp.NumberFormatter;
import org.efaps.esjp.erp.RateInfo;
import org.efaps.ui.wicket.util.DateUtil;
import org.efaps.util.EFapsException;
import org.joda.time.DateTime;

/**
 * The Class Calculation_Base.
 *
 * @author The eFaps Team
 */
@EFapsUUID("a73c916b-c35e-4ed6-acc0-844d1162b397")
@EFapsApplication("eFapsApp-Accounting")
public abstract class Calculation_Base
    extends Transaction
{
    /**
     * Calculate button.
     *
     * @param _parameter the parameter
     * @return the return
     * @throws EFapsException on error
     */
    public Return calculateButton(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final StringBuilder js = new StringBuilder();

        final int exConfOrd = Integer.parseInt(_parameter.getParameterValue("calculateConfig"));
        final CalculateConfig config = Accounting.CalculateConfig.values()[exConfOrd];
        final Parameter parameter = ParameterUtil.clone(_parameter);
        switch (config) {
            case EXCHANGERATE:
                // update Rate
                js.append(getJS4ExchangeRate(_parameter, parameter, "Debit"))
                    .append(getJS4ExchangeRate(_parameter, parameter, "Credit"));

                final BigDecimal sumDebit = getSum4UI(parameter, "Debit", null, null);
                final BigDecimal sumCredit = getSum4UI(parameter, "Credit", null, null);

                final DecimalFormat formater = NumberFormatter.get().getTwoDigitsFormatter();

                final Instance periodInstance = new Period().evaluateCurrentPeriod(_parameter);
                final String sumDebitStr = formater.format(sumDebit) + " " + new Period().getCurrency(periodInstance)
                                .getSymbol();
                final String sumCreditStr = formater.format(sumCredit) + " " + new Period().getCurrency(periodInstance)
                                .getSymbol();
                final String sumTotalStr = formater.format(sumDebit.subtract(sumCredit).abs()) + " " + new Period()
                                .getCurrency(periodInstance).getSymbol();

                js.append(getSetFieldValue(0, "sumDebit", sumDebitStr)).append(getSetFieldValue(0, "sumCredit",
                                sumCreditStr)).append(getSetFieldValue(0, "sumTotal", sumTotalStr));
                break;
            case FILLUPAMOUNT:
                js.append(getJS4FillUp(_parameter, parameter));
                break;
            case FITEXRATE:
                js.append(getJS4FitExRate(_parameter, parameter));
                break;
            case SUMMARIZE:
                js.append(getJS4Summarize(_parameter, parameter));
                break;
            default:
                break;
        }
        ret.put(ReturnValues.SNIPLETT, js.toString());
        return ret;
    }


    /**
     * Gets the JS 4 fit exchange rate.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @param _parameterClone the parameter clone
     * @return the JS 4 fit ex rate
     * @throws EFapsException on error
     */
    protected StringBuilder getJS4FitExRate(final Parameter _parameter,
                                            final Parameter _parameterClone)
        throws EFapsException
    {
        final StringBuilder ret = new StringBuilder();
        try {
            String postfix = null;
            boolean eval = true;
            while (eval) {
                eval = postfix == null;
                postfix = postfix == null ? "Debit" : "Credit";
                final String[] selected = _parameter.getParameterValues("posSelect_" + postfix);
                if (ArrayUtils.isNotEmpty(selected)) {
                    for (int i = 0; i < selected.length; i++) {
                        if (BooleanUtils.toBoolean(selected[i])) {
                            eval = false;
                            final DecimalFormat formater = NumberFormatter.get().getTwoDigitsFormatter();

                            final Instance periodInst = new Period().evaluateCurrentPeriod(_parameterClone);
                            final Instance periodCurrenycInstance = new Period().getCurrency(periodInst).getInstance();

                            final BigDecimal sumDebit = getSum4UI(_parameter, "Debit", null, null);
                            final BigDecimal sumCredit = getSum4UI(_parameter, "Credit", null, null);
                            final BigDecimal diff = "Debit".equals(postfix)
                                            ? sumCredit.subtract(sumDebit) : sumDebit.subtract(sumCredit);

                            final String currAmountStr = _parameter.getParameterValues("amount_" + postfix)[i];
                            final BigDecimal currAmount = StringUtils.isEmpty(currAmountStr)
                                                ? BigDecimal.ZERO : (BigDecimal) formater.parse(currAmountStr);

                            final RateInfo rateInfo = getRateInfo4UI(_parameterClone, "_" + postfix, i);

                            final BigDecimal currAmountRate = Currency.convertToCurrency(_parameter, currAmount,
                                                rateInfo, null, periodCurrenycInstance).setScale(2,
                                                                RoundingMode.HALF_UP);

                            final BigDecimal targetRate = currAmountRate.add(diff);

                            if (BigDecimal.ZERO.compareTo(targetRate) < 0
                                            && BigDecimal.ZERO.compareTo(currAmount) < 0) {
                                final BigDecimal rate;
                                if (rateInfo.isInvert()) {
                                    rate = targetRate.setScale(8, RoundingMode.HALF_UP)
                                                    .divide(currAmount, RoundingMode.HALF_UP);
                                } else {
                                    rate = currAmount.setScale(8, RoundingMode.HALF_UP)
                                                    .divide(targetRate, RoundingMode.HALF_UP);
                                }

                                final String rateStr = NumberFormatter.get().getFormatter(null, 8).format(rate);
                                final String amountRateStr = formater.format(targetRate);

                                ret.append(getSetFieldValue(i, "amountRate_" + postfix, amountRateStr))
                                    .append(getSetFieldValue(i, "rate_" + postfix, rateStr));
                                ParameterUtil.setParameterValue(_parameterClone, "amountRate_" + postfix, i,
                                                amountRateStr);
                                ParameterUtil.setParameterValue(_parameterClone, "rate_" + postfix, i, rateStr);

                                final BigDecimal sumDebit2 = getSum4UI(_parameterClone, "Debit", null, null);
                                final BigDecimal sumCredit2 = getSum4UI(_parameterClone, "Credit", null, null);


                                final String sumDebitStr = formater.format(sumDebit2) + " " + new Period().getCurrency(
                                                periodInst).getSymbol();
                                final String sumCreditStr = formater.format(sumCredit2) + " " + new Period()
                                                .getCurrency(periodInst).getSymbol();
                                final String sumTotalStr = formater.format(sumDebit2.subtract(sumCredit2).abs()) + " "
                                                + new Period().getCurrency(periodInst).getSymbol();

                                ret.append(getSetFieldValue(0, "sumDebit", sumDebitStr))
                                    .append(getSetFieldValue(0, "sumCredit", sumCreditStr))
                                    .append(getSetFieldValue(0, "sumTotal", sumTotalStr));
                                break;
                            }
                        }
                    }
                }
            }
        } catch (final ParseException e) {
            throw new EFapsException(Transaction_Base.class, "update4Currency.ParseException", e);
        }
        return ret;
    }


    /**
     * Gets the JS for fill up.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @param _parameterClone the parameter clone
     * @return the JS 4 fill up
     * @throws EFapsException on error
     */
    protected StringBuilder getJS4FillUp(final Parameter _parameter,
                                         final Parameter _parameterClone)
        throws EFapsException
    {
        final StringBuilder ret = new StringBuilder();
        try {
            String postfix = null;
            boolean eval = true;
            while (eval) {
                eval = postfix == null;
                postfix = postfix == null ? "Debit" : "Credit";
                final String[] selected = _parameter.getParameterValues("posSelect_" + postfix);
                if (ArrayUtils.isNotEmpty(selected)) {
                    for (int i = 0; i < selected.length; i++) {
                        if (BooleanUtils.toBoolean(selected[i])) {
                            eval = false;
                            final DecimalFormat formater = NumberFormatter.get().getTwoDigitsFormatter();

                            final Instance periodInst = new Period().evaluateCurrentPeriod(_parameterClone);
                            final Instance periodCurrenycInstance = new Period().getCurrency(periodInst).getInstance();

                            final BigDecimal sumDebit = getSum4UI(_parameter, "Debit", null, null);
                            final BigDecimal sumCredit = getSum4UI(_parameter, "Credit", null, null);
                            final BigDecimal diff = "Debit".equals(postfix)
                                            ? sumCredit.subtract(sumDebit) : sumDebit.subtract(sumCredit);

                            final String currAmountStr = _parameter.getParameterValues("amount_" + postfix)[i];
                            final BigDecimal currAmount = StringUtils.isEmpty(currAmountStr)
                                                ? BigDecimal.ZERO : (BigDecimal) formater.parse(currAmountStr);

                            final RateInfo rateInfo = getRateInfo4UI(_parameterClone, "_" + postfix, i);

                            final BigDecimal currAmountRate = Currency.convertToCurrency(_parameter, currAmount,
                                                rateInfo, null, periodCurrenycInstance).setScale(2,
                                                                RoundingMode.HALF_UP);

                            final BigDecimal targetRate = currAmountRate.add(diff);

                            if (BigDecimal.ZERO.compareTo(targetRate) < 0) {
                                final RateInfo revRateInfo = rateInfo.reverse();
                                final BigDecimal target =  Currency.convertToCurrency(_parameter, targetRate,
                                                revRateInfo, null, rateInfo.getCurrencyInstance())
                                                .setScale(2, RoundingMode.HALF_UP);

                                final String amountStr = formater.format(target);
                                final String amountRateStr = formater.format(targetRate);

                                ret.append(getSetFieldValue(i, "amount_" + postfix, amountStr))
                                    .append(getSetFieldValue(i, "amountRate_" + postfix, amountRateStr));
                                ParameterUtil.setParameterValue(_parameterClone, "amount_" + postfix, i, amountStr);
                                ParameterUtil.setParameterValue(_parameterClone, "amountRate_" + postfix, i,
                                                amountRateStr);

                                final BigDecimal sumDebit2 = getSum4UI(_parameterClone, "Debit", null, null);
                                final BigDecimal sumCredit2 = getSum4UI(_parameterClone, "Credit", null, null);


                                final String sumDebitStr = formater.format(sumDebit2) + " " + new Period().getCurrency(
                                                periodInst).getSymbol();
                                final String sumCreditStr = formater.format(sumCredit2) + " " + new Period()
                                                .getCurrency(periodInst).getSymbol();
                                final String sumTotalStr = formater.format(sumDebit2.subtract(sumCredit2).abs()) + " "
                                                + new Period().getCurrency(periodInst).getSymbol();

                                ret.append(getSetFieldValue(0, "sumDebit", sumDebitStr))
                                    .append(getSetFieldValue(0, "sumCredit", sumCreditStr))
                                    .append(getSetFieldValue(0, "sumTotal", sumTotalStr));
                                break;
                            }
                        }
                    }
                }
            }
        } catch (final ParseException e) {
            throw new EFapsException(Transaction_Base.class, "update4Currency.ParseException", e);
        }
        return ret;
    }

    /**
     * Gets the JS 4 exchange rate.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @param _parameterClone the parameter clone
     * @param _postfix the postfix
     * @return the JS 4 exchange rate
     * @throws EFapsException on error
     */
    protected StringBuilder getJS4ExchangeRate(final Parameter _parameter,
                                               final Parameter _parameterClone,
                                               final String _postfix)
        throws EFapsException
    {
        final StringBuilder ret = new StringBuilder();
        try {
            final String[] amounts = _parameter.getParameterValues("amount_" + _postfix);
            final String[] currencies = _parameter.getParameterValues("rateCurrencyLink_" + _postfix);
            final String[] selected = _parameter.getParameterValues("posSelect_" + _postfix);

            final ExchangeConfig exConf = getExchangeConfig(_parameter, null);

            for (int i = 0; i < selected.length; i++) {
                if (BooleanUtils.toBoolean(selected[i])) {
                    final DateTime date;
                    switch (exConf) {
                        case DOCDATEPURCHASE:
                        case DOCDATESALE:
                            final Instance docInst = Instance.get(_parameter.getParameterValues("docLink_"
                                            + _postfix)[i]);
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

                    final boolean sale = ExchangeConfig.TRANSDATESALE.equals(exConf) || ExchangeConfig.DOCDATESALE
                                    .equals(exConf);
                    final Instance periodInstance = new Period().evaluateCurrentPeriod(_parameter);

                    final RateInfo rate = evaluateRate(_parameter, periodInstance, date, Instance.get(CIERP.Currency
                                    .getType(), currencies[i]));
                    final DecimalFormat rateFormater = sale ? rate.getFormatter().getFrmt4SaleRateUI(null)
                                    : rate.getFormatter().getFrmt4RateUI(null);
                    final BigDecimal amountRate = amounts[i].isEmpty() ? BigDecimal.ZERO
                                    : (BigDecimal) rateFormater.parse(amounts[i]);

                    final DecimalFormat formater = NumberFormatter.get().getTwoDigitsFormatter();

                    final String rateStr = sale ? rate.getSaleRateUIFrmt(null) : rate.getRateUIFrmt(null);
                    final String rateInStr = "" + rate.isInvert();
                    final String amountStr = formater.format(amountRate.setScale(12).divide(sale ? rate.getSaleRate()
                                    : rate.getRate(), BigDecimal.ROUND_HALF_UP));

                    ret.append(getSetFieldValue(i, "rate_" + _postfix, rateStr))
                        .append(getSetFieldValue(i, "rate_" + _postfix + RateUI.INVERTEDSUFFIX, rateInStr))
                        .append(getSetFieldValue(i, "amountRate_" + _postfix, amountStr));

                    ParameterUtil.setParameterValue(_parameterClone, "rate_" + _postfix, i, rateStr);
                    ParameterUtil.setParameterValue(_parameterClone, "rate_" + _postfix + RateUI.INVERTEDSUFFIX, i,
                                    rateInStr);
                    ParameterUtil.setParameterValue(_parameterClone, "amountRate_" + _postfix, i, amountStr);
                }
            }
        } catch (final ParseException e) {
            throw new EFapsException(Transaction_Base.class, "update4Currency.ParseException", e);
        }
        return ret;
    }

    /**
     * Gets the JS 4 fit exchange rate.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @param _parameterClone the parameter clone
     * @return the JS 4 fit ex rate
     * @throws EFapsException on error
     */
    protected StringBuilder getJS4Summarize(final Parameter _parameter,
                                            final Parameter _parameterClone)
        throws EFapsException
    {
        final StringBuilder ret = new StringBuilder();
        String postfix = null;
        boolean eval = true;
        while (eval) {
            eval = postfix == null;
            postfix = postfix == null ? "Debit" : "Credit";
            final String[] selected = _parameter.getParameterValues("posSelect_" + postfix);
            final String[] accountOids = _parameter.getParameterValues("accountLink_" + postfix);
            _parameter.getParameterValues("amount_" + postfix);

            // 1. find the selected account
            if (ArrayUtils.isNotEmpty(selected)) {
                String accountOid = null;
                for (int i = 0; i < selected.length; i++) {
                    if (BooleanUtils.toBoolean(selected[i])) {
                        accountOid = accountOids[i];
                        break;
                    }
                }
                final Collection<AccountInfo> accounts = new ArrayList<>();
                if (StringUtils.isNoneEmpty(accountOid)) {
                    final TransInfo transInfo = new TransInfo();
                    new Create().analysePositionsFromUI(_parameter, transInfo, postfix, null, false);

                    AccountInfo sumAcc = null;
                    for (final PositionInfo pos : transInfo.getPositions()) {
                        final Object[] rateObj = (Object[]) pos.getRate();
                        final RateInfo rateInfo = RateInfo.getDummyRateInfo();
                        rateInfo.setCurrencyInstance(pos.getRateCurrInst());
                        rateInfo.setTargetCurrencyInstance(pos.getCurrInst());
                        rateInfo.setRate((BigDecimal) rateObj[0]);
                        rateInfo.setRateUI((BigDecimal) rateObj[1]);
                        rateInfo.setSaleRate((BigDecimal) rateObj[0]);
                        rateInfo.setSaleRateUI((BigDecimal) rateObj[1]);

                        final AccountInfo info = new AccountInfo()
                                        .setInstance(pos.getAccInst())
                                        .setDescription("description")
                                        .setRemark(pos.getRemark())
                                        .addAmount(pos.getRateAmount().abs())
                                        .addAmountRate(pos.getAmount().abs())
                                        .setCurrInstance(pos.getCurrInst())
                                        .setDocLink(pos.getDocInst())
                                        .setLabelInst(pos.getLabelInst())
                                        .setRateInfo(rateInfo, "");

                        if (!pos.getAccInst().getOid().equals(accountOid)) {
                            accounts.add(info);
                        } else if (sumAcc == null) {
                            sumAcc = info;
                            accounts.add(info);
                        } else {
                            sumAcc.addAmount(pos.getRateAmount().abs()).addAmountRate(pos.getAmount().abs());
                        }
                    }
                    ret.append(new Evaluation().getTableJS(_parameterClone, postfix, accounts));
                }
            }
        }
        return ret;
    }

    /**
     * The Class Evaluation.
     */
    static class Evaluation
        extends AbstractEvaluation
    {
        @Override
        public StringBuilder getTableJS(final Parameter _parameter,
                                        final String _postFix,
                                        final Collection<AccountInfo> _accounts)
            throws EFapsException
        {
            return super.getTableJS(_parameter, _postFix, _accounts);
        }
    }
}
