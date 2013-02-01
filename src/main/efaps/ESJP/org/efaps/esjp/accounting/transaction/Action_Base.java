/*
 * Copyright 2003 - 2012 The eFaps Team
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

import org.efaps.admin.event.Parameter;
import org.efaps.admin.program.esjp.EFapsRevision;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.ci.CIType;
import org.efaps.db.AttributeQuery;
import org.efaps.db.Insert;
import org.efaps.db.Instance;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.PrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.esjp.accounting.Periode;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.erp.CurrencyInst;
import org.efaps.esjp.sales.PriceUtil;
import org.efaps.util.EFapsException;
import org.joda.time.DateTime;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 * @version $Id$
 */
@EFapsUUID("ca033fa0-4682-49b8-aa4a-6efdeef44dbe")
@EFapsRevision("$Rev$")
public abstract class Action_Base
{

    /**
     * @param _parameter Parameter as passed by the efasp API
     * @param _transactionInstance Instance of the Transaction the position will
     *            be connected to
     * @param _instAction Instance of the Action defining the positions
     * @param _amount Amount of the position
     * @param _rateCurrInst Instance of the Currency for the amount
     * @throws EFapsException on error
     */
    public void insertPositions(final Parameter _parameter,
                                final Instance _transactionInstance,
                                final Instance _instAction,
                                final BigDecimal _amount,
                                final Instance _rateCurrInst)
        throws EFapsException
    {
        final Instance periodeInst = getPeriodeInstance(_parameter, _transactionInstance);

        final CurrencyInst periodeCurInst = new Periode().getCurrency(periodeInst);
        final CurrencyInst rateCurInst = new CurrencyInst(_rateCurrInst);

        final QueryBuilder attrQueryBldr = new QueryBuilder(CIAccounting.CasePayroll);
        attrQueryBldr.addWhereAttrEqValue(CIAccounting.CasePayroll.PeriodeAbstractLink, periodeInst.getId());
        final AttributeQuery attrQuery = attrQueryBldr.getAttributeQuery(CIAccounting.CasePayroll.ID);

        final QueryBuilder attrQueryBldr2 = new QueryBuilder(CIAccounting.ActionDefinition2Case);
        attrQueryBldr2.addWhereAttrEqValue(CIAccounting.ActionDefinition2Case.FromLink, _instAction.getId());
        attrQueryBldr2.addWhereAttrInQuery(CIAccounting.ActionDefinition2Case.ToLink, attrQuery);
        final AttributeQuery attrQuery2 = attrQueryBldr2.getAttributeQuery(CIAccounting.ActionDefinition2Case.ToLink);

        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.Account2CaseAbstract);
        queryBldr.addWhereAttrInQuery(CIAccounting.Account2CaseAbstract.ToCaseAbstractLink, attrQuery2);
        final MultiPrintQuery multi = queryBldr.getPrint();
        multi.addAttribute(CIAccounting.Account2CaseAbstract.Numerator, CIAccounting.Account2CaseAbstract.Denominator);
        final SelectBuilder selAccountInst = new SelectBuilder().linkto(
                        CIAccounting.Account2CaseAbstract.FromAccountAbstractLink).instance();
        multi.addSelect(selAccountInst);
        multi.execute();

        while (multi.next()) {
            final Instance accountInst = multi.<Instance>getSelect(selAccountInst);
            final BigDecimal numerator = new BigDecimal(
                            multi.<Integer>getAttribute(CIAccounting.Account2CaseAbstract.Numerator));
            final BigDecimal denominator = new BigDecimal(
                            multi.<Integer>getAttribute(CIAccounting.Account2CaseAbstract.Denominator));

            CIType type = null;
            boolean isDebitTrans = false;
            if (multi.getCurrentInstance().getType()
                            .isKindOf(CIAccounting.Account2CaseCredit.getType())) {
                type = CIAccounting.TransactionPositionCredit;
                isDebitTrans = false;
            } else if (multi.getCurrentInstance().getType()
                            .isKindOf(CIAccounting.Account2CaseDebit.getType())) {
                type = CIAccounting.TransactionPositionDebit;
                isDebitTrans = true;
            }
            if (type != null) {

                final PriceUtil util = new PriceUtil();
                final BigDecimal[] rates = util.getRates(getExchangeDate(_parameter, _transactionInstance),
                                periodeCurInst.getInstance(), rateCurInst.getInstance());

                final Object[] rateObj = new Object[] { rateCurInst.isInvert() ? BigDecimal.ONE : rates[3],
                                rateCurInst.isInvert() ? rates[3] : BigDecimal.ONE };

                final BigDecimal rate = ((BigDecimal) rateObj[0]).divide((BigDecimal) rateObj[1], 12,
                                BigDecimal.ROUND_HALF_UP);
                final BigDecimal rateAmount = _amount.abs().setScale(8, BigDecimal.ROUND_HALF_UP);
                final BigDecimal amount = rateAmount.divide(rate, 12, BigDecimal.ROUND_HALF_UP);

                final BigDecimal rateAmount2 = rateAmount.multiply(numerator
                                .divide(denominator, 8, BigDecimal.ROUND_HALF_UP));
                final BigDecimal amount2 = amount.multiply(numerator
                                .divide(denominator, 8, BigDecimal.ROUND_HALF_UP));

                final Insert posInsert = new Insert(type);
                posInsert.add(CIAccounting.TransactionPositionAbstract.TransactionLink, _transactionInstance.getId());
                posInsert.add(CIAccounting.TransactionPositionAbstract.AccountLink, accountInst.getId());
                posInsert.add(CIAccounting.TransactionPositionAbstract.CurrencyLink, periodeCurInst.getInstance()
                                .getId());
                posInsert.add(CIAccounting.TransactionPositionAbstract.RateCurrencyLink, rateCurInst.getInstance()
                                .getId());
                posInsert.add(CIAccounting.TransactionPositionAbstract.Rate, rateObj);
                posInsert.add(CIAccounting.TransactionPositionAbstract.RateAmount, isDebitTrans ? rateAmount2.negate()
                                : rateAmount2);
                posInsert.add(CIAccounting.TransactionPositionAbstract.Amount, isDebitTrans ? amount2.negate()
                                : amount2);
                posInsert.execute();
            }
        }
    }

    protected Instance getPeriodeInstance(final Parameter _parameter,
                                          final Instance _transactionInstance)
        throws EFapsException
    {
        final PrintQuery print = new PrintQuery(_transactionInstance);
        final SelectBuilder sel = new SelectBuilder().linkto(CIAccounting.TransactionAbstract.PeriodeLink).instance();
        print.addSelect(sel);
        print.executeWithoutAccessCheck();
        return print.<Instance>getSelect(sel);
    }

    protected DateTime getExchangeDate(final Parameter _parameter,
                                       final Instance _transactionInstance)
        throws EFapsException
    {
        final PrintQuery print = new PrintQuery(_transactionInstance);
        print.addAttribute(CIAccounting.TransactionAbstract.Date);
        print.executeWithoutAccessCheck();
        return print.<DateTime>getAttribute(CIAccounting.TransactionAbstract.Date);
    }
}
