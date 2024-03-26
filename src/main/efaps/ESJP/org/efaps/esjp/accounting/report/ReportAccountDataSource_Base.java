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
package org.efaps.esjp.accounting.report;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.efaps.admin.common.SystemConfiguration;
import org.efaps.admin.datamodel.Type;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.AttributeQuery;
import org.efaps.db.Instance;
import org.efaps.db.InstanceQuery;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.PrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.esjp.accounting.util.Accounting;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.ci.CIERP;
import org.efaps.esjp.common.jasperreport.EFapsMapDataSource;
import org.efaps.esjp.erp.Currency;
import org.efaps.esjp.erp.CurrencyInst;
import org.efaps.esjp.erp.RateInfo;
import org.efaps.esjp.erp.util.ERP;
import org.efaps.util.EFapsException;
import org.joda.time.DateTime;

import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JasperReport;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 *
 */
@EFapsUUID("28b48013-d393-4e99-8068-89d57ae2d879")
@EFapsApplication("eFapsApp-Accounting")
public abstract class ReportAccountDataSource_Base
    extends EFapsMapDataSource
{
    /**
     * Enum used to define the keys for the map.
     */
    public enum Field
    {
        /** */
        ACCDESC("accountDescription"),
        /** */
        ACCNAME("accountName"),
        /** */
        ACCTYPEUUID("accountTypeUUID"),
        /** */
        CREDIT("amountCredit"),
        /** */
        DEBIT("amountDebit"),
        /** */
        WINLOSSNETO("winLossNeto"),
        /** */
        WINLOSSFINAL("winLossFinal");

        /**
         * key.
         */
        private final String key;

        /**
         * @param _key key
         */
        Field(final String _key)
        {
            this.key = _key;
        }

        /**
         * Getter method for the instance variable {@link #key}.
         *
         * @return value of instance variable {@link #key}
         */
        public String getKey()
        {
            return this.key;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void init(final JasperReport _jasperReport,
                     final Parameter _parameter,
                     final JRDataSource _parentSource,
                     final Map<String, Object> _jrParameters)
        throws EFapsException
    {
        final Instance instance = _parameter.getInstance();
        final DateTime dateFrom = new DateTime(_parameter.getParameterValue("dateFrom"));
        final DateTime dateTo = new DateTime(_parameter.getParameterValue("dateTo"));
        _jrParameters.put("FromDate", dateFrom.toDate());
        _jrParameters.put("ToDate", dateTo.toDate());
        _jrParameters.put("Mime", _parameter.getParameterValue("mime"));

        final PrintQuery printRep = new PrintQuery(instance);
        printRep.addAttribute(CIAccounting.ReportAbstract.Name);
        printRep.execute();
        final String name = printRep.<String>getAttribute(CIAccounting.ReportAbstract.Name).replaceAll(" ", "_");
        _jrParameters.put("FileName", name);

        // get the root node, only the first one will be used
        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.ReportNodeRoot);
        queryBldr.addWhereAttrEqValue(CIAccounting.ReportNodeRoot.ReportLink, instance.getId());
        final InstanceQuery query = queryBldr.getQuery();
        query.execute();
        if (query.next()) {
            // only the ReportAccounts will be evaluated
            final QueryBuilder accQueryBldr = new QueryBuilder(CIAccounting.ReportNodeAccount);
            accQueryBldr.addWhereAttrEqValue(CIAccounting.ReportNodeAccount.ParentLink,
                            query.getCurrentValue().getId());
            final MultiPrintQuery multi = accQueryBldr.getPrint();
            final SelectBuilder sel = new SelectBuilder().linkto(CIAccounting.ReportNodeAccount.AccountLink).oid();
            multi.addSelect(sel);
            multi.addAttribute(CIAccounting.ReportNodeAccount.ShowSum, CIAccounting.ReportNodeAccount.ShowAllways);
            multi.execute();
            while (multi.next()) {
                final String accOid = multi.<String>getSelect(sel);
                final Instance accInst = Instance.get(accOid);
                final BigDecimal[] debitCredit = getDebitCredit(_parameter, accInst, dateFrom, dateTo);
                final PrintQuery print = new PrintQuery(accInst);
                print.addAttribute(CIAccounting.AccountAbstract.Name, CIAccounting.AccountAbstract.Description);
                print.execute();
                final Map<String, Object> values = new HashMap<>();
                values.put(ReportAccountDataSource.Field.ACCNAME.getKey(),
                                print.getAttribute(CIAccounting.AccountAbstract.Name));
                values.put(ReportAccountDataSource.Field.ACCDESC.getKey(),
                                print.getAttribute(CIAccounting.AccountAbstract.Description));
                values.put(ReportAccountDataSource.Field.DEBIT.getKey(), debitCredit[0]);
                values.put(ReportAccountDataSource.Field.CREDIT.getKey(), debitCredit[1]);
                values.put(ReportAccountDataSource.Field.ACCTYPEUUID.getKey(), accInst.getType().getUUID().toString());
                values.put(ReportAccountDataSource.Field.WINLOSSNETO.getKey(),
                                multi.getAttribute(CIAccounting.ReportNodeAccount.ShowAllways));
                values.put(ReportAccountDataSource.Field.WINLOSSFINAL.getKey(),
                                multi.getAttribute(CIAccounting.ReportNodeAccount.ShowSum));
                getValues().add(values);
            }
        }

        final SystemConfiguration config = ERP.getSysConfig();
        if (config != null) {
            final String companyName = ERP.COMPANY_NAME.get();
            final String companyTaxNumb = ERP.COMPANY_TAX.get();

            if (companyName != null && companyTaxNumb != null && !companyName.isEmpty() && !companyTaxNumb.isEmpty()) {
                _jrParameters.put("CompanyName", companyName);
                _jrParameters.put("CompanyTaxNum", companyTaxNumb);
            }
        }
    }


    /**
     * Recursive method to get the Debit and Credit Amount of all Transactions
     * of the given account and the accounts children.
     *
     * @param _accInst  Instance of the Account
     * @param _dateFrom dateFrom
     * @param _dateTo   date To
     * @return Array of BigDecimal { Debit Amount, Credit Amount}
     * @throws EFapsException on error
     */
    protected BigDecimal[] getDebitCredit(final Parameter _parameter,
                                          final Instance _accInst,
                                          final DateTime _dateFrom,
                                          final DateTime _dateTo)
        throws EFapsException
    {
        final boolean active = Boolean.parseBoolean(_parameter.getParameterValue("filterActive"));
        final String currency = _parameter.getParameterValue("currency");
        final Instance curBase = Currency.getBaseCurrency();

        BigDecimal debit = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
        // get the transactions fot the given account instance
        final QueryBuilder attrQueryBldr = new QueryBuilder(CIAccounting.TransactionAbstract);
        attrQueryBldr.addWhereAttrLessValue(CIAccounting.TransactionAbstract.Date,
                        _dateTo.plusDays(1));
        attrQueryBldr.addWhereAttrGreaterValue(CIAccounting.TransactionAbstract.Date,
                        _dateFrom.minusSeconds(1));
        final AttributeQuery attrQuery = attrQueryBldr.getAttributeQuery(CIAccounting.TransactionAbstract.ID);

        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.TransactionPositionAbstract);
        queryBldr.addWhereAttrEqValue(CIAccounting.TransactionPositionAbstract.AccountLink, _accInst.getId());
        queryBldr.addWhereAttrInQuery(CIAccounting.TransactionPositionAbstract.TransactionLink, attrQuery);
        final MultiPrintQuery multi = queryBldr.getPrint();
        multi.addAttribute(CIAccounting.TransactionPositionAbstract.Amount,
                            CIAccounting.TransactionPositionAbstract.RateAmount);
        final SelectBuilder selRateCur = new SelectBuilder()
                .linkto(CIAccounting.TransactionPositionAbstract.RateCurrencyLink).instance();
        final SelectBuilder selTxnDate = new SelectBuilder()
                .linkto(CIAccounting.TransactionPositionAbstract.TransactionLink)
                .attribute(CIAccounting.TransactionAbstract.Date);
        multi.addSelect(selRateCur, selTxnDate);
        multi.execute();
        while (multi.next()) {
            final CurrencyInst curInstTxnPos = new CurrencyInst(multi.<Instance>getSelect(selRateCur));
            final DateTime date = multi.<DateTime>getSelect(selTxnDate);

            BigDecimal amount = BigDecimal.ZERO;
            if (active) {
                final Long rateCurType = Long.parseLong(_parameter.getParameterValue("rateCurrencyType"));
                final CurrencyInst curInst = new CurrencyInst(Instance.get(CIERP.Currency.getType(), currency));
                if (curInstTxnPos.getInstance().getId() != curInst.getInstance().getId()) {
                    if (curInstTxnPos.getInstance().getId() != curBase.getId()) {
                        RateInfo rateInfo = getCurrency(_parameter).evaluateRateInfo(_parameter, date,
                                        curInstTxnPos.getInstance());
                        BigDecimal rate = RateInfo.getRate(_parameter, rateInfo,
                                        ReportAccountDataSource.class.getName());
                        final BigDecimal amountTmp = multi
                                    .<BigDecimal>getAttribute(CIAccounting.TransactionPositionAbstract.RateAmount)
                                                .divide(rate, BigDecimal.ROUND_HALF_UP);

                        rateInfo = getCurrency(_parameter).evaluateRateInfo(_parameter, date, curInst.getInstance());
                        rate = RateInfo.getRate(_parameter, rateInfo, Report.class.getName());
                        amount = amountTmp.divide(rate, BigDecimal.ROUND_HALF_UP);
                    } else {
                        final RateInfo rateInfo = getCurrency(_parameter)
                                        .evaluateRateInfo(_parameter, date, curInst.getInstance());
                        final BigDecimal rate = RateInfo.getRate(_parameter, rateInfo, Report.class.getName());
                        amount = multi
                                    .<BigDecimal>getAttribute(CIAccounting.TransactionPositionAbstract.RateAmount)
                                    .divide(rate, BigDecimal.ROUND_HALF_UP);
                    }

                } else {
                    amount = multi.<BigDecimal>getAttribute(CIAccounting.TransactionPositionAbstract.RateAmount);
                }
            } else {
                amount = multi.<BigDecimal>getAttribute(CIAccounting.TransactionPositionAbstract.Amount);
            }

            if (multi.getCurrentInstance().getType().equals(CIAccounting.TransactionPositionCredit.getType())) {
                credit = credit.add(amount);
            } else {
                debit = debit.add(amount);
            }
        }
        // get the child accounts, andd the amounts by calling recursive
        final QueryBuilder accQueryBldr = new QueryBuilder(CIAccounting.AccountAbstract);
        accQueryBldr.addWhereAttrEqValue(CIAccounting.AccountAbstract.ParentLink, _accInst.getId());
        final InstanceQuery accQuery = accQueryBldr.getQuery();
        accQuery.execute();
        while (accQuery.next()) {
            final BigDecimal[] amounts = getDebitCredit(_parameter, accQuery.getCurrentValue(), _dateFrom, _dateTo);
            debit = debit.add(amounts[0]);
            credit = credit.add(amounts[1]);
        }
        return new BigDecimal[] { debit, credit };
    }

    protected Currency getCurrency(final Parameter _parameter)
        throws EFapsException
    {
        final Currency ret = new Currency()
        {

            @Override
            protected Type getType4ExchangeRate(final Parameter _parameter)
                throws EFapsException
            {
                Type typeRet;
                // TODO evaluate that
                // final Long rateCurType =
                // Long.parseLong(_parameter.getParameterValue("rateCurrencyType"));

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

}
