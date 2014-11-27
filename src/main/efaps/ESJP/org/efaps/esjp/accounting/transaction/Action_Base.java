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
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.efaps.admin.datamodel.Status;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Return;
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
import org.efaps.esjp.accounting.Label;
import org.efaps.esjp.accounting.Period;
import org.efaps.esjp.accounting.util.Accounting;
import org.efaps.esjp.accounting.util.Accounting.ActDef2Case4DocConfig;
import org.efaps.esjp.accounting.util.Accounting.ActDef2Case4IncomingConfig;
import org.efaps.esjp.accounting.util.AccountingSettings;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.ci.CIERP;
import org.efaps.esjp.ci.CISales;
import org.efaps.esjp.common.parameter.ParameterUtil;
import org.efaps.esjp.common.uiform.Field;
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
        final AttributeQuery attrQuery2 = attrQueryBldr2
                        .getAttributeQuery(CIAccounting.ActionDefinition2Case.ToLinkAbstract);

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
     * @param _parameter Parameter as passed by the eFaps API
     * @param _actionRelInst relation Instance
     * @throws EFapsException on error
     */
    public void create4External(final Parameter _parameter,
                                final Instance _actionRelInst)
        throws EFapsException
    {
        final IncomingActionDef def = evalActionDef4Incoming(_parameter, _actionRelInst);
        if (def.getParameter() != null) {
            final Create create = new Create();
            create.create4ExternalMassive(def.getParameter());
            create.connectDocs2PurchaseRecord(def.getParameter(),
                            Instance.get(def.getParameter().getParameterValue("document")));
        }
    }

    /**
     * @param _parameter Parameter as passed by the eFaps API
     * @param _actionRelInst relation Instance
     * @throws EFapsException on error
     */
    protected IncomingActionDef evalActionDef4Incoming(final Parameter _parameter,
                                                       final Instance _actionRelInst)
        throws EFapsException
    {
        final IncomingActionDef ret = getIncomingActionDef(_parameter);

        final PrintQuery print = new PrintQuery(_actionRelInst);
        final SelectBuilder selActionInst = SelectBuilder.get()
                        .linkto(CIERP.ActionDefinition2DocumentAbstract.FromLinkAbstract).instance();
        final SelectBuilder selDocInst = SelectBuilder.get()
                        .linkto(CIERP.ActionDefinition2DocumentAbstract.ToLinkAbstract).instance();
        final SelectBuilder selDocTypeInst = SelectBuilder.get()
                        .linkto(CIERP.ActionDefinition2DocumentAbstract.ToLinkAbstract)
                        .linkfrom(CIERP.Document2DocumentTypeAbstract.DocumentLinkAbstract)
                        .linkto(CIERP.Document2DocumentTypeAbstract.DocumentTypeLinkAbstract).instance();
        print.addSelect(selActionInst, selDocInst, selDocTypeInst);
        print.execute();
        ret.setActionInst(print.<Instance>getSelect(selActionInst));
        ret.setDocInst(print.<Instance>getSelect(selDocInst));
        ret.setDocTypeInst(print.<Instance>getSelect(selDocTypeInst));

        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.ActionDefinition2Case4IncomingAbstract);
        queryBldr.addWhereAttrEqValue(CIAccounting.ActionDefinition2Case4IncomingAbstract.FromLinkAbstract,
                        ret.getActionInst());
        final MultiPrintQuery multi = queryBldr.getPrint();
        final SelectBuilder selCaseInst = SelectBuilder.get()
                        .linkto(CIAccounting.ActionDefinition2Case4IncomingAbstract.ToLinkAbstract).instance();
        final SelectBuilder selLabelInst = SelectBuilder.get()
                        .linkto(CIAccounting.ActionDefinition2Case4IncomingAbstract.LabelLink).instance();
        multi.addSelect(selCaseInst, selLabelInst);
        multi.addAttribute(CIAccounting.ActionDefinition2Case4IncomingAbstract.Config);
        multi.execute();
        while (multi.next()) {
            final List<ActDef2Case4IncomingConfig> configs = multi
                            .getAttribute(CIAccounting.ActionDefinition2Case4IncomingAbstract.Config);
            if (configs != null) {

                final Instance caseInst = multi.getSelect(selCaseInst);
                // force the correct period by evaluating it now
                final Instance periodInst = new Period().evaluateCurrentPeriod(_parameter, caseInst);
                boolean execute = true;
                // for pettyCash receipt evaluation if legal document or not
                if (ret.getDocInst().getType().isKindOf(CISales.PettyCashReceipt)) {
                    final Properties props = Accounting.getSysConfig().getObjectAttributeValueAsProperties(periodInst);
                    final boolean withoutDoc = "true".equalsIgnoreCase(props
                                    .getProperty(AccountingSettings.PERIOD_ACTIVATEPETTYCASHWD));
                    if (!withoutDoc && ret.getDocTypeInst() == null) {
                        execute = false;
                    } else {
                        if (ret.getDocTypeInst() == null) {
                            execute = configs.contains(ActDef2Case4IncomingConfig.WITHOUTDOC);
                        } else {
                            execute = !configs.contains(ActDef2Case4IncomingConfig.WITHOUTDOC);
                        }
                    }
                } else if (ret.getDocInst().getType().isKindOf(CISales.FundsToBeSettledReceipt)) {
                    final Properties props = Accounting.getSysConfig().getObjectAttributeValueAsProperties(periodInst);
                    final boolean withoutDoc = "true".equalsIgnoreCase(props
                                    .getProperty(AccountingSettings.PERIOD_ACTIVATEFTBSWD));
                    if (!withoutDoc && ret.getDocTypeInst() == null) {
                        execute = false;
                    } else {
                        if (ret.getDocTypeInst() == null) {
                            execute = configs.contains(ActDef2Case4IncomingConfig.WITHOUTDOC);
                        } else {
                            execute = !configs.contains(ActDef2Case4IncomingConfig.WITHOUTDOC);
                        }
                    }
                }
                if (execute) {
                    final Instance labelInst = multi.getSelect(selLabelInst);
                    if (labelInst != null && labelInst.isValid()) {
                        final List<Instance> labels = new Label().getLabelInst4Documents(_parameter, ret.getDocInst());
                        if (labels.contains(labelInst)) {
                            ret.setConfigs(configs);
                            ret.setCaseInst(caseInst);
                            break;
                        }
                    } else {
                        ret.setConfigs(configs);
                        ret.setCaseInst(caseInst);
                    }
                }
            }
        }

        if (ret.getCaseInst() != null && ret.getCaseInst().isValid()) {
            ret.setParameter(ParameterUtil.clone(_parameter, (Object) null));

            if (ret.getConfigs().contains(ActDef2Case4IncomingConfig.PURCHASERECORD)) {
                final DateTime date = new DateTime();
                final QueryBuilder prQueryBldr = new QueryBuilder(CIAccounting.PurchaseRecord);
                prQueryBldr.addWhereAttrLessValue(CIAccounting.PurchaseRecord.Date, date.plusDays(1));
                prQueryBldr.addWhereAttrGreaterValue(CIAccounting.PurchaseRecord.DueDate, date.minusDays(1));
                prQueryBldr.addWhereAttrEqValue(CIAccounting.PurchaseRecord.Status,
                                Status.find(CIAccounting.PurchaseRecordStatus.Open));
                final InstanceQuery prQuery = prQueryBldr.getQuery();
                prQuery.execute();
                if (prQuery.next()) {
                    ParameterUtil.setParmeterValue(ret.getParameter(), "purchaseRecord", prQuery
                                    .getCurrentValue()
                                    .getOid());
                }
            }
            if (ret.getConfigs().contains(ActDef2Case4IncomingConfig.TRANSACTION)) {
                ParameterUtil.setParmeterValue(ret.getParameter(), "case", ret.getCaseInst().getOid());
                ParameterUtil.setParmeterValue(ret.getParameter(), "document", ret.getDocInst().getOid());
                if (ret.getConfigs().contains(ActDef2Case4IncomingConfig.SUBJOURNAL)) {
                    final QueryBuilder sjQueryBldr = new QueryBuilder(CIAccounting.Report2Case);
                    sjQueryBldr.addWhereAttrEqValue(CIAccounting.Report2Case.ToLink, ret.getCaseInst());
                    final MultiPrintQuery sjMulti = sjQueryBldr.getPrint();
                    final SelectBuilder sel = new SelectBuilder().linkto(CIAccounting.Report2Case.FromLink)
                                    .oid();
                    sjMulti.addSelect(sel);
                    sjMulti.execute();
                    if (sjMulti.next()) {
                        ParameterUtil.setParmeterValue(ret.getParameter(), "subJournal",
                                        sjMulti.<String>getSelect(sel));
                    }
                }
                if (ret.getConfigs().contains(ActDef2Case4IncomingConfig.SETSTATUS)) {
                    ParameterUtil.setParmeterValue(ret.getParameter(), "docStatus", "true");
                }
            }
        }
        return ret;
    }

    /**
     * @param _parameter Parameter as passed by the eFaps API
     * @param _actionRelInst relation Instance
     * @throws EFapsException on error
     */
    public void create4PettyCashReceipt(final Parameter _parameter,
                                        final Instance _actionRelInst)
        throws EFapsException
    {
        final IncomingActionDef def = evalActionDef4Incoming(_parameter, _actionRelInst);
        if (def.getParameter() != null) {
            final Create create = new Create();
            create.create4PettyCashMassive(def.getParameter());
            create.connectDocs2PurchaseRecord(def.getParameter(),
                            Instance.get(def.getParameter().getParameterValue("document")));
        }
    }

    /**
     * @param _parameter Parameter as passed by the eFaps API
     * @param _actionRelInst relation Instance
     * @throws EFapsException on error
     */
    public void create4FundsToBeSettledReceipt(final Parameter _parameter,
                                               final Instance _actionRelInst)
        throws EFapsException
    {
        final IncomingActionDef def = evalActionDef4Incoming(_parameter, _actionRelInst);
        if (def.getParameter() != null) {
            final Create create = new Create();
            create.create4FundsToBeSettledMassive(def.getParameter());
            create.connectDocs2PurchaseRecord(def.getParameter(),
                            Instance.get(def.getParameter().getParameterValue("document")));
        }
    }

    /**
     * @param _parameter Parameter as passed by the eFaps API
     * @param _actionRelInst relation Instance
     * @throws EFapsException on error
     */
    public void create4IncomingExchange(final Parameter _parameter,
                                        final Instance _actionRelInst)
        throws EFapsException
    {
        final DocActionDef def = evalActionDef4Doc(_parameter, _actionRelInst);
        if (def.getParameter() != null) {
            final Create create = new Create();
            create.create4ExternalMassive(def.getParameter());
        }
    }

    /**
     * @param _parameter Parameter as passed by the eFaps API
     * @param _actionRelInst relation Instance
     * @throws EFapsException on error
     */
    public void create4Doc(final Parameter _parameter,
                           final Instance _actionRelInst)
        throws EFapsException
    {
        final DocActionDef def = evalActionDef4Doc(_parameter, _actionRelInst);
        if (def.getParameter() != null) {
            final Create create = new Create();
            create.create4DocMassive(def.getParameter());
        }
    }

    protected DocActionDef evalActionDef4Doc(final Parameter _parameter,
                                          final Instance _actionRelInst)
        throws EFapsException
    {
        final DocActionDef ret = getDocActionDef(_parameter);
        final PrintQuery print = new PrintQuery(_actionRelInst);
        final SelectBuilder selActionInst = SelectBuilder.get()
                        .linkto(CIERP.ActionDefinition2DocumentAbstract.FromLinkAbstract).instance();
        final SelectBuilder selDocInst = SelectBuilder.get()
                        .linkto(CIERP.ActionDefinition2DocumentAbstract.ToLinkAbstract).instance();
        print.addSelect(selActionInst, selDocInst);
        print.execute();
        ret.setActionInst(print.<Instance>getSelect(selActionInst));
        ret.setDocInst(print.<Instance>getSelect(selDocInst));
        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.ActionDefinition2Case4DocAbstract);
        queryBldr.addWhereAttrEqValue(CIAccounting.ActionDefinition2Case4DocAbstract.FromLinkAbstract,
                        ret.getActionInst());
        final MultiPrintQuery multi = queryBldr.getPrint();
        final SelectBuilder selCaseInst = SelectBuilder.get()
                        .linkto(CIAccounting.ActionDefinition2Case4DocAbstract.ToLinkAbstract).instance();
        final SelectBuilder selLabelInst = SelectBuilder.get()
                        .linkto(CIAccounting.ActionDefinition2Case4IncomingAbstract.LabelLink).instance();
        multi.addSelect(selCaseInst, selLabelInst);
        multi.addAttribute(CIAccounting.ActionDefinition2Case4DocAbstract.Config);
        multi.execute();
        while( multi.next()) {
            final List<ActDef2Case4DocConfig> configs = multi
                            .getAttribute(CIAccounting.ActionDefinition2Case4DocAbstract.Config);
            if (configs != null) {
                ret.getConfigs().addAll(configs);
                final Instance caseInst = multi.getSelect(selCaseInst);
                // force the correct period by evaluating it now
                new Period().evaluateCurrentPeriod(_parameter, caseInst);
                ret.setParameter(ParameterUtil.clone(_parameter, (Object) null));

                final Instance labelInst = multi.getSelect(selLabelInst);
                if (labelInst != null && labelInst.isValid()) {
                    final List<Instance> labels = new Label().getLabelInst4Documents(_parameter, ret.getDocInst());
                    if (labels.contains(labelInst)) {
                        ret.setConfigs(configs);
                        ret.setCaseInst(caseInst);
                        break;
                    }
                } else {
                    ret.setConfigs(configs);
                    ret.setCaseInst(caseInst);
                }
            }
        }
        if (ret.getCaseInst() != null && ret.getCaseInst().isValid()) {
            ParameterUtil.setParmeterValue(ret.getParameter(), "document", ret.getDocInst().getOid());
            if (ret.getConfigs().contains(ActDef2Case4DocConfig.TRANSACTION)) {
                ParameterUtil.setParmeterValue(ret.getParameter(), "case", ret.getCaseInst().getOid());
                if (ret.getConfigs().contains(ActDef2Case4DocConfig.SUBJOURNAL)) {
                    final QueryBuilder sjQueryBldr = new QueryBuilder(CIAccounting.Report2Case);
                    sjQueryBldr.addWhereAttrEqValue(CIAccounting.Report2Case.ToLink, ret.getCaseInst());
                    final MultiPrintQuery sjMulti = sjQueryBldr.getPrint();
                    final SelectBuilder sel = new SelectBuilder().linkto(CIAccounting.Report2Case.FromLink).oid();
                    sjMulti.addSelect(sel);
                    sjMulti.execute();
                    if (sjMulti.next()) {
                        ParameterUtil.setParmeterValue(ret.getParameter(), "subJournal",
                                        sjMulti.<String>getSelect(sel));
                    }
                }
            }
            if (ret.getConfigs().contains(ActDef2Case4DocConfig.SETSTATUS)) {
                ParameterUtil.setParmeterValue(ret.getParameter(), "docStatus", "true");
            }
        }
        return ret;
    }

    /**
     * @param _parameter
     * @param _actionRelInst
     */
    public void create4OthersPay(final Parameter _parameter,
                                 final Instance _actionRelInst)
        throws EFapsException
    {
        final DocActionDef def = evalActionDef4Doc(_parameter, _actionRelInst);
        if (def.getParameter() != null) {
            final Create create = new Create();
            if (def.getConfigs().contains(ActDef2Case4DocConfig.TRANSACTION)) {
                create.create4OthersPayMassiv(def.getParameter());
            } else if (def.getConfigs().contains(ActDef2Case4DocConfig.SETSTATUS)) {
                create.setStatus4Docs(_parameter, def.getDocInst());
            }
        }
    }

    /**
     * @param _parameter
     * @param _actionRelInst
     */
    public void create4OthersCollect(final Parameter _parameter,
                                     final Instance _actionRelInst)
        throws EFapsException
    {
        final DocActionDef def = evalActionDef4Doc(_parameter, _actionRelInst);
        if (def.getParameter() != null) {
            final Create create = new Create();
            if (def.getConfigs().contains(ActDef2Case4DocConfig.TRANSACTION)) {
                create.create4OthersCollectMassive(def.getParameter());
            } else if (def.getConfigs().contains(ActDef2Case4DocConfig.SETSTATUS)) {
                create.setStatus4Docs(_parameter, def.getDocInst());
            }
        }
    }

    protected DocActionDef getDocActionDef(final Parameter _parameter)
    {
        return new DocActionDef();
    }

    protected IncomingActionDef getIncomingActionDef(final Parameter _parameter)
    {
        return new IncomingActionDef();
    }

    public Return getLabelUIFieldValue(final Parameter _parameter)
        throws EFapsException
    {
        final Field field = new Field(){
            @Override
            protected void add2QueryBuilder4List(final Parameter _parameter,
                                                 final QueryBuilder _queryBldr)
                throws EFapsException
            {
                super.add2QueryBuilder4List(_parameter, _queryBldr);
                _queryBldr.addWhereAttrEqValue(CIAccounting.Label.PeriodAbstractLink,
                                new Period().evaluateCurrentPeriod(_parameter));
            }
        };
        return field.dropDownFieldValue(_parameter);
    }

    public static abstract class AbstractActionDef {

        private Instance docTypeInst;

        /**
         * Parameter used to simulate the forms.
         */
        private Parameter parameter;

        /**
         * Instance of the action.
         */
        private Instance actionInst;

        /**
         * Instance of the document.
         */
        private Instance docInst;

        /**
         * Instance of the document.
         */
        private Instance caseInst;

        /**
         * Getter method for the instance variable {@link #_parameter}.
         *
         * @return value of instance variable {@link #_parameter}
         */
        public Parameter getParameter()
        {
            return this.parameter;
        }

        /**
         * Setter method for instance variable {@link #_parameter}.
         *
         * @param __parameter value for instance variable {@link #_parameter}
         */
        public void setParameter(final Parameter __parameter)
        {
            this.parameter = __parameter;
        }

        /**
         * Getter method for the instance variable {@link #actionInst}.
         *
         * @return value of instance variable {@link #actionInst}
         */
        public Instance getActionInst()
        {
            return this.actionInst;
        }

        /**
         * Getter method for the instance variable {@link #docInst}.
         *
         * @return value of instance variable {@link #docInst}
         */
        public Instance getDocInst()
        {
            return this.docInst;
        }

        /**
         * Setter method for instance variable {@link #actionInst}.
         *
         * @param _actionInst value for instance variable {@link #actionInst}
         */
        public void setActionInst(final Instance _actionInst)
        {
            this.actionInst = _actionInst;
        }

        /**
         * Setter method for instance variable {@link #docInst}.
         *
         * @param _docInst value for instance variable {@link #docInst}
         */
        public void setDocInst(final Instance _docInst)
        {
            this.docInst = _docInst;
        }

        /**
         * Getter method for the instance variable {@link #docTypeInst}.
         *
         * @return value of instance variable {@link #docTypeInst}
         */
        public Instance getDocTypeInst()
        {
            return this.docTypeInst;
        }

        /**
         * Setter method for instance variable {@link #docTypeInst}.
         *
         * @param _docTypeInst value for instance variable {@link #docTypeInst}
         */
        public void setDocTypeInst(final Instance _docTypeInst)
        {
            this.docTypeInst = _docTypeInst;
        }


        /**
         * Getter method for the instance variable {@link #caseInst}.
         *
         * @return value of instance variable {@link #caseInst}
         */
        public Instance getCaseInst()
        {
            return this.caseInst;
        }


        /**
         * Setter method for instance variable {@link #caseInst}.
         *
         * @param _caseInst value for instance variable {@link #caseInst}
         */
        public void setCaseInst(final Instance _caseInst)
        {
            this.caseInst = _caseInst;
        }
    }


    public static class DocActionDef
        extends AbstractActionDef
    {
        /**
         * Configs.
         */
        private List<ActDef2Case4DocConfig> configs = new ArrayList<>();

        /**
         * Getter method for the instance variable {@link #configs}.
         *
         * @return value of instance variable {@link #configs}
         */
        public List<ActDef2Case4DocConfig> getConfigs()
        {
            return this.configs;
        }

        /**
         * Setter method for instance variable {@link #configs}.
         *
         * @param _configs value for instance variable {@link #configs}
         */
        public void setConfigs(final List<ActDef2Case4DocConfig> _configs)
        {
            this.configs = _configs;
        }
    }

    public static class IncomingActionDef
        extends AbstractActionDef
    {

        /**
         * Configs.
         */
        private List<ActDef2Case4IncomingConfig> configs = new ArrayList<>();

        /**
         * Getter method for the instance variable {@link #configs}.
         *
         * @return value of instance variable {@link #configs}
         */
        public List<ActDef2Case4IncomingConfig> getConfigs()
        {
            return this.configs;
        }

        /**
         * Setter method for instance variable {@link #configs}.
         *
         * @param _configs value for instance variable {@link #configs}
         */
        public void setConfigs(final List<ActDef2Case4IncomingConfig> _configs)
        {
            this.configs = _configs;
        }
    }
}
