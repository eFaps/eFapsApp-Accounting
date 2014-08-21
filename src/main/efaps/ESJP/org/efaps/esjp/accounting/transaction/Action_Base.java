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
import java.util.List;

import org.efaps.admin.datamodel.Status;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.program.esjp.EFapsRevision;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.ci.CIType;
import org.efaps.db.AttributeQuery;
import org.efaps.db.Insert;
import org.efaps.db.Instance;
import org.efaps.db.InstanceQuery;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.PrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.esjp.accounting.Period;
import org.efaps.esjp.accounting.util.Accounting.ActDef2Case4DocConfig;
import org.efaps.esjp.accounting.util.Accounting.ActDef2Case4IncomingConfig;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.ci.CIERP;
import org.efaps.esjp.common.parameter.ParameterUtil;
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
     * @param _parameter Parameter as passed by the eFaps API
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
        final Instance periodInst = getPeriodInstance(_parameter, _transactionInstance);

        final CurrencyInst periodCurInst = new Period().getCurrency(periodInst);
        final CurrencyInst rateCurInst = new CurrencyInst(_rateCurrInst);

        final QueryBuilder attrQueryBldr = new QueryBuilder(CIAccounting.CasePayroll);
        attrQueryBldr.addWhereAttrEqValue(CIAccounting.CasePayroll.PeriodAbstractLink, periodInst.getId());
        final AttributeQuery attrQuery = attrQueryBldr.getAttributeQuery(CIAccounting.CasePayroll.ID);

        final QueryBuilder attrQueryBldr2 = new QueryBuilder(CIAccounting.ActionDefinition2Case);
        attrQueryBldr2.addWhereAttrEqValue(CIAccounting.ActionDefinition2Case.FromLinkAbstract, _instAction.getId());
        attrQueryBldr2.addWhereAttrInQuery(CIAccounting.ActionDefinition2Case.ToLinkAbstract, attrQuery);
        final AttributeQuery attrQuery2 = attrQueryBldr2.getAttributeQuery(CIAccounting.ActionDefinition2Case.ToLinkAbstract);

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
                                periodCurInst.getInstance(), rateCurInst.getInstance());

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
                posInsert.add(CIAccounting.TransactionPositionAbstract.CurrencyLink, periodCurInst.getInstance()
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

    protected Instance getPeriodInstance(final Parameter _parameter,
                                          final Instance _transactionInstance)
        throws EFapsException
    {
        final PrintQuery print = new PrintQuery(_transactionInstance);
        final SelectBuilder sel = new SelectBuilder().linkto(CIAccounting.TransactionAbstract.PeriodLink).instance();
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

    /**
     * @param _parameter    Parameter as passed by the eFaps API
     * @param _actionRelInst relation Instance
     * @throws EFapsException on error
     */
    public void create4External(final Parameter _parameter,
                                final Instance _actionRelInst)
        throws EFapsException
    {
        final Parameter parameter = getParameter4Incoming(_parameter, _actionRelInst);
        if (parameter != null) {
            final Create create = new Create();
            create.create4ExternalMassive(parameter);
            create.connectDocs2PurchaseRecord(parameter, Instance.get(parameter.getParameterValue("document")));
        }
    }

    /**
     * @param _parameter    Parameter as passed by the eFaps API
     * @param _actionRelInst relation Instance
     * @throws EFapsException on error
     */
    protected Parameter getParameter4Incoming(final Parameter _parameter,
                                              final Instance _actionRelInst)
        throws EFapsException
    {
        Parameter ret = null;

        final PrintQuery print = new PrintQuery(_actionRelInst);
        final SelectBuilder selActionInst = SelectBuilder.get()
                        .linkto(CIERP.ActionDefinition2DocumentAbstract.FromLinkAbstract).instance();
        final SelectBuilder selDocInst = SelectBuilder.get()
                        .linkto(CIERP.ActionDefinition2DocumentAbstract.ToLinkAbstract).instance();
        print.addSelect(selActionInst, selDocInst);
        print.execute();
        final Instance actionInst = print.getSelect(selActionInst);
        final Instance docInst = print.getSelect(selDocInst);
        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.ActionDefinition2Case4IncomingAbstract);
        queryBldr.addWhereAttrEqValue(CIAccounting.ActionDefinition2Case4IncomingAbstract.FromLinkAbstract, actionInst);
        final MultiPrintQuery multi = queryBldr.getPrint();
        final SelectBuilder selCaseInst = SelectBuilder.get()
                        .linkto(CIAccounting.ActionDefinition2Case4IncomingAbstract.ToLinkAbstract).instance();
        multi.addSelect(selCaseInst);
        multi.addAttribute(CIAccounting.ActionDefinition2Case4IncomingAbstract.Config);
        if (multi.execute()) {
            multi.next();
            final List<ActDef2Case4IncomingConfig> configs = multi
                            .getAttribute(CIAccounting.ActionDefinition2Case4IncomingAbstract.Config);
            if (configs != null) {
                final Instance caseInst = multi.getSelect(selCaseInst);
                // force the correct period by evaluating it now
                new Period().evaluateCurrentPeriod(_parameter, caseInst);
                ret = ParameterUtil.clone(_parameter, (Object) null);

                if (configs.contains(ActDef2Case4IncomingConfig.PURCHASERECORD)) {
                    final DateTime date = new DateTime();
                    final QueryBuilder prQueryBldr = new QueryBuilder(CIAccounting.PurchaseRecord);
                    prQueryBldr.addWhereAttrLessValue(CIAccounting.PurchaseRecord.Date, date.plusDays(1));
                    prQueryBldr.addWhereAttrGreaterValue(CIAccounting.PurchaseRecord.DueDate, date.minusDays(1));
                    prQueryBldr.addWhereAttrEqValue(CIAccounting.PurchaseRecord.Status,
                                    Status.find(CIAccounting.PurchaseRecordStatus.Open));
                    final InstanceQuery prQuery = prQueryBldr.getQuery();
                    prQuery.execute();
                    if (prQuery.next()) {
                        ParameterUtil.setParmeterValue(ret, "purchaseRecord", prQuery.getCurrentValue().getOid());
                    }
                }
                if (configs.contains(ActDef2Case4IncomingConfig.TRANSACTION)) {
                    ParameterUtil.setParmeterValue(ret, "case", caseInst.getOid());
                    ParameterUtil.setParmeterValue(ret, "document", docInst.getOid());
                    if (configs.contains(ActDef2Case4IncomingConfig.SUBJOURNAL)) {
                        final QueryBuilder sjQueryBldr = new QueryBuilder(CIAccounting.Report2Case);
                        sjQueryBldr.addWhereAttrEqValue(CIAccounting.Report2Case.ToLink, caseInst);
                        final MultiPrintQuery sjMulti = sjQueryBldr.getPrint();
                        final SelectBuilder sel = new SelectBuilder().linkto(CIAccounting.Report2Case.FromLink).oid();
                        sjMulti.addSelect(sel);
                        sjMulti.execute();
                        if (sjMulti.next()) {
                            ParameterUtil.setParmeterValue(ret, "subJournal", sjMulti.<String>getSelect(sel));
                        }
                    }
                }
            }
        }
        return ret;
    }

    /**
     * @param _parameter    Parameter as passed by the eFaps API
     * @param _actionRelInst relation Instance
     * @throws EFapsException on error
     */
    public void create4PettyCashReceipt(final Parameter _parameter,
                                        final Instance _actionRelInst)
        throws EFapsException
    {
        final Parameter parameter = getParameter4Incoming(_parameter, _actionRelInst);
        if (parameter != null) {
            final Create create = new Create();
            create.create4PettyCashMassive(parameter);
            create.connectDocs2PurchaseRecord(parameter, Instance.get(parameter.getParameterValue("document")));
        }
    }

    /**
     * @param _parameter    Parameter as passed by the eFaps API
     * @param _actionRelInst relation Instance
     * @throws EFapsException on error
     */
    public void create4Doc(final Parameter _parameter,
                           final Instance _actionRelInst)
        throws EFapsException
    {
        final PrintQuery print = new PrintQuery(_actionRelInst);
        final SelectBuilder selActionInst = SelectBuilder.get()
                        .linkto(CIERP.ActionDefinition2DocumentAbstract.FromLinkAbstract).instance();
        final SelectBuilder selDocInst = SelectBuilder.get()
                        .linkto(CIERP.ActionDefinition2DocumentAbstract.ToLinkAbstract).instance();
        print.addSelect(selActionInst, selDocInst);
        print.execute();
        final Instance actionInst = print.getSelect(selActionInst);
        final Instance docInst = print.getSelect(selDocInst);
        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.ActionDefinition2Case4Doc);
        queryBldr.addWhereAttrEqValue(CIAccounting.ActionDefinition2Case4Doc.FromLinkAbstract, actionInst);
        final MultiPrintQuery multi = queryBldr.getPrint();
        final SelectBuilder selCaseInst = SelectBuilder.get()
                        .linkto(CIAccounting.ActionDefinition2Case4Doc.ToLinkAbstract).instance();
        multi.addSelect(selCaseInst);
        multi.addAttribute(CIAccounting.ActionDefinition2Case4Doc.Config);
        if (multi.execute()) {
            multi.next();
            final List<ActDef2Case4DocConfig> configs = multi
                            .getAttribute(CIAccounting.ActionDefinition2Case4Doc.Config);
            if (configs != null) {
                final Instance caseInst = multi.getSelect(selCaseInst);
                // force the correct period by evaluating it now
                new Period().evaluateCurrentPeriod(_parameter, caseInst);
                final Parameter parameter = ParameterUtil.clone(_parameter, (Object) null);
                final Create create = new Create();
                if (configs.contains(ActDef2Case4DocConfig.TRANSACTION)) {
                    ParameterUtil.setParmeterValue(parameter, "case", caseInst.getOid());
                    ParameterUtil.setParmeterValue(parameter, "document", docInst.getOid());
                    if (configs.contains(ActDef2Case4DocConfig.SUBJOURNAL)) {
                        final QueryBuilder sjQueryBldr = new QueryBuilder(CIAccounting.Report2Case);
                        sjQueryBldr.addWhereAttrEqValue(CIAccounting.Report2Case.ToLink, caseInst);
                        final MultiPrintQuery sjMulti = sjQueryBldr.getPrint();
                        final SelectBuilder sel = new SelectBuilder().linkto(CIAccounting.Report2Case.FromLink).oid();
                        sjMulti.addSelect(sel);
                        sjMulti.execute();
                        if (sjMulti.next()) {
                            ParameterUtil.setParmeterValue(parameter, "subJournal", sjMulti.<String>getSelect(sel));
                        }
                    }
                    create.create4DocMassive(parameter);
                }
            }
        }
    }
}
