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
import java.util.HashMap;
import java.util.Map;

import org.efaps.admin.datamodel.Classification;
import org.efaps.admin.datamodel.Status;
import org.efaps.admin.datamodel.Type;
import org.efaps.admin.datamodel.attributetype.DecimalType;
import org.efaps.admin.dbproperty.DBProperties;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Parameter.ParameterValues;
import org.efaps.admin.event.Return;
import org.efaps.admin.event.Return.ReturnValues;
import org.efaps.admin.program.esjp.EFapsRevision;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.ci.CIType;
import org.efaps.db.Context;
import org.efaps.db.Insert;
import org.efaps.db.Instance;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.PrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.db.Update;
import org.efaps.esjp.accounting.Periode;
import org.efaps.esjp.accounting.transaction.Transaction_Base.Document;
import org.efaps.esjp.accounting.transaction.Transaction_Base.TargetAccount;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.ci.CIERP;
import org.efaps.esjp.common.uiform.Create;
import org.efaps.esjp.erp.CurrencyInst;
import org.efaps.esjp.erp.Rate;
import org.efaps.util.EFapsException;
import org.joda.time.DateTime;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 * @version $Id$
 */
@EFapsUUID("7b2870fe-4566-4688-b015-263c910c34e2")
@EFapsRevision("$Rev$")
public abstract class Create_Base
    extends Create
{

    /**
     * Create the base transaction.
     *
     * @param _parameter Parameter as passed from the eFaps API
     * @param _description Description for the Transaction
     * @return Instance of the Transaction
     * @throws EFapsException on error
     */
    protected Instance createBaseTrans(final Parameter _parameter,
                                       final String _description)
        throws EFapsException
    {
        Instance parent = _parameter.getCallInstance();
        // in case that an accountis the parent the periode is searched
        if (parent.getType().isKindOf(CIAccounting.AccountAbstract.getType())) {
            final PrintQuery print = new PrintQuery(parent);
            final SelectBuilder sel = new SelectBuilder()
                .linkto(CIAccounting.AccountAbstract.PeriodeAbstractLink).oid();
            print.addSelect(sel);
            print.execute();
            parent = Instance.get(print.<String>getSelect(sel));
        }
        final Insert insert = new Insert(CIAccounting.Transaction);
        insert.add(CIAccounting.Transaction.Name, _parameter.getParameterValue("name"));
        insert.add(CIAccounting.Transaction.Description, _description);
        insert.add(CIAccounting.Transaction.Date, _parameter.getParameterValue("date"));
        insert.add(CIAccounting.Transaction.PeriodeLink, parent.getId());
        insert.add(CIAccounting.Transaction.Status, Status.find(CIAccounting.TransactionStatus.uuid, "Open").getId());
        insert.execute();
        final Instance instance = insert.getInstance();
        insertPositions(_parameter, instance, "Credit", null);
        insertPositions(_parameter, instance, "Debit", null);
        return instance;
    }

    /**
     * Called from event for creation of a transaction with a external document.
     *
     * @param _parameter Parameter as passed from the eFaps API
     * @return new Return
     * @throws EFapsException on error
     */
    public Return create4External(final Parameter _parameter)
        throws EFapsException
    {
        final String descr = _parameter.getParameterValue("number") + " - "
                            + _parameter.getParameterValue("description");
        final Instance instance = createBaseTrans(_parameter, descr);

        Instance docInst = Instance.get(_parameter.getParameterValue("document"));
        if (!docInst.isValid()) {
            docInst = _parameter.getInstance();
        }

        if (docInst != null && docInst.isValid()) {
            // create classifications
            final Classification classification1 = (Classification) CIAccounting.TransactionClass.getType();
            final Insert relInsert1 = new Insert(classification1.getClassifyRelationType());
            relInsert1.add(classification1.getRelLinkAttributeName(), instance.getId());
            relInsert1.add(classification1.getRelTypeAttributeName(), classification1.getId());
            relInsert1.execute();

            final Insert classInsert1 = new Insert(classification1);
            classInsert1.add(classification1.getLinkAttributeName(), instance.getId());
            classInsert1.execute();

            final Classification classification = (Classification) CIAccounting.TransactionClassExternal.getType();
            final Insert relInsert = new Insert(classification.getClassifyRelationType());
            relInsert.add(classification.getRelLinkAttributeName(), instance.getId());
            relInsert.add(classification.getRelTypeAttributeName(), classification.getId());
            relInsert.execute();

            final Insert classInsert = new Insert(classification);
            classInsert.add(classification.getLinkAttributeName(), instance.getId());
            classInsert.add(CIAccounting.TransactionClassExternal.TypeLink, _parameter.getParameterValue("typeLink"));
            classInsert.add(CIAccounting.TransactionClassExternal.DocumentLink, docInst.getId());
            classInsert.add(CIAccounting.TransactionClassExternal.Number, _parameter.getParameterValue("number"));
            classInsert.execute();

            final boolean setStatus = "true".equals(_parameter.getParameterValue("docStatus"));

            if (setStatus) {
                final Update update = new Update(docInst);
                update.add("Status",
                             Status.find(docInst.getType().getStatusAttribute().getLink().getName(), "Booked").getId());
                update.execute();
            }
        }
        return new Return();
    }

    /**
     * Create the classifcation.
     * @param _transInst    Transaction Instance
     * @param _docInst      Document instance
     * @throws EFapsException on error
     */
    protected void createDocClass(final Instance _transInst,
                                  final Instance _docInst)
        throws EFapsException
    {
     // create classifications
        final Classification classification1 = (Classification) CIAccounting.TransactionClass.getType();
        final Insert relInsert1 = new Insert(classification1.getClassifyRelationType());
        relInsert1.add(classification1.getRelLinkAttributeName(), _transInst.getId());
        relInsert1.add(classification1.getRelTypeAttributeName(), classification1.getId());
        relInsert1.execute();

        final Insert classInsert1 = new Insert(classification1);
        classInsert1.add(classification1.getLinkAttributeName(), _transInst.getId());
        classInsert1.execute();

        final Classification classification = (Classification) CIAccounting.TransactionClassDocument.getType();
        final Insert relInsert = new Insert(classification.getClassifyRelationType());
        relInsert.add(classification.getRelLinkAttributeName(), _transInst.getId());
        relInsert.add(classification.getRelTypeAttributeName(), classification.getId());
        relInsert.execute();

        final Insert classInsert = new Insert(CIAccounting.TransactionClassDocument);
        classInsert.add(classification.getLinkAttributeName(), _transInst.getId());
        classInsert.add(CIAccounting.TransactionClassDocument.DocumentLink, _docInst.getId());
        classInsert.execute();
    }

    /**
     * Called from event for creation of a transaction with a document.
     *
     * @param _parameter Parameter as passed from the eFaps API
     * @return new Return
     * @throws EFapsException on error
     */
    public Return create4Doc(final Parameter _parameter)
        throws EFapsException
    {

        final Instance instance = createBaseTrans(_parameter, _parameter.getParameterValue("description"));
        final Instance docInst = Instance.get(_parameter.getParameterValue("document"));
        createDocClass(instance, docInst);

        final boolean setStatus = "true".equals(_parameter.getParameterValue("docStatus"));

        if (setStatus) {
            final Update update = new Update(docInst);
            update.add("Status",
                            Status.find(docInst.getType().getStatusAttribute().getLink().getName(), "Booked").getId());
            update.execute();
        }

        if (_parameter.getParameterValue("payment") != null) {
            final boolean setPayStatus = "true".equals(_parameter.getParameterValue("paymentStatus"));

            for (final String paymentOid : _parameter.getParameterValues("payment")) {
                final Instance payInst = Instance.get(paymentOid);
                Insert insert = null;
                long statusId = 0;
                if (CIAccounting.PaymentCheck.getType().equals(payInst.getType())) {
                    insert = new Insert(CIAccounting.PaymentCheck2Document);
                    statusId = Status.find(CIAccounting.PaymentCheckStatus.uuid, "Closed").getId();
                }
                insert.add(CIAccounting.PaymentAbstract2Document.FromAbstractLink, payInst.getId());
                insert.add(CIAccounting.PaymentAbstract2Document.ToAbstractLink, docInst.getId());
                insert.executeWithoutAccessCheck();

                if (setPayStatus) {
                    final Update update = new Update(payInst);
                    update.add(CIAccounting.PaymentAbstract.StatusAbstract, statusId);
                    update.executeWithoutTrigger();
                }
            }
        }

        return new Return();
    }

    /**
     * Method called to create a transaction including its positions.
     *
     * @param _parameter Parameter as passed from the eFaps API
     * @return new Return
     * @throws EFapsException on error
     */
    public Return create(final Parameter _parameter)
        throws EFapsException
    {
        final Instance parent = _parameter.getCallInstance();

        final Insert insert = new Insert(CIAccounting.Transaction);
        insert.add(CIAccounting.Transaction.Name, _parameter.getParameterValue("name"));
        insert.add(CIAccounting.Transaction.Description, _parameter.getParameterValue("description"));
        insert.add(CIAccounting.Transaction.Date, _parameter.getParameterValue("date"));
        insert.add(CIAccounting.Transaction.PeriodeLink, parent.getId());
        insert.add(CIAccounting.Transaction.Status, Status.find(CIAccounting.TransactionStatus.uuid, "Open").getId());
        insert.execute();
        final Instance instance = insert.getInstance();
        insertPositions(_parameter, instance, "Credit", null);
        insertPositions(_parameter, instance, "Debit", null);
        // create classifications
        insertClassification(_parameter, instance);
        return new Return();
    }

    /**
     * Method is used to create a transaction for a given account. e.g. the user
     * selects an account and than only selects the amount and the target
     * accounts.
     *
     * @param _parameter Parameter as passed from the eFaps API
     * @return new Return
     * @throws EFapsException on error
     */
    public Return create4Account(final Parameter _parameter)
        throws EFapsException
    {
        final Map<?, ?> properties = (Map<?, ?>) _parameter.get(ParameterValues.PROPERTIES);
        final String postfix = (String) properties.get("TypePostfix");

        final Instance periodeInst = (Instance) Context.getThreadContext().getSessionAttribute(
                        Transaction_Base.PERIODE_SESSIONKEY);
        final Insert insert = new Insert(CIAccounting.Transaction);
        insert.add(CIAccounting.Transaction.Name, _parameter.getParameterValue("name"));
        insert.add(CIAccounting.Transaction.Description, _parameter.getParameterValue("description"));
        insert.add(CIAccounting.Transaction.Date, _parameter.getParameterValue("date"));
        insert.add(CIAccounting.Transaction.PeriodeLink, periodeInst.getId());
        insert.add(CIAccounting.Transaction.Status, Status.find(CIAccounting.TransactionStatus.uuid, "Open").getId());
        insert.execute();
        final Instance instance = insert.getInstance();
        insertPositions(_parameter, instance, postfix, new String[] { _parameter.getCallInstance().getOid() });
        insertPositions(_parameter, instance, "Debit".equals(postfix) ? "Credit" : "Debit", null);
        insertClassification(_parameter, instance);
        return new Return();
    }

    /**
     * Insert a position for a transaction.
     *
     * @param _parameter Parameter as passed from the eFaps API
     * @param _instance transaction instance the positions belong to
     * @param _postFix postficx for the fields
     * @param _accountOids oid of the accounts
     * @throws EFapsException on error
     */
    public void insertPositions(final Parameter _parameter,
                                final Instance _instance,
                                final String _postFix,
                                final String[] _accountOids)
        throws EFapsException
    {
        final String[] accountOids = _accountOids == null
                        ? _parameter.getParameterValues("accountLink_" + _postFix) : _accountOids;
        final String[] amounts = _parameter.getParameterValues("amount_" + _postFix);
        final String[] types = _parameter.getParameterValues("type_" + _postFix);
        final String[] curr = _parameter.getParameterValues("rateCurrencyLink_" + _postFix);
        final String[] account2accountOids = _parameter.getParameterValues("account2accountOID_" + _postFix);
        final String[] label2projectOids = _parameter.getParameterValues("labelLink_" + _postFix);
        final DecimalFormat formater = new Transaction().getFormater(null, null);
        try {
            Instance inst = _parameter.getCallInstance();
            if (!inst.getType().getUUID().equals(CIAccounting.Periode.uuid)) {
                inst = (Instance) Context.getThreadContext().getSessionAttribute(Transaction_Base.PERIODE_SESSIONKEY);
            }
            final Instance curInstance = new Periode().getCurrency(inst).getInstance();
            if (amounts != null) {
                for (int i = 0; i < amounts.length; i++) {
                    final Object[] rateObj = new Transaction().getRateObject(_parameter, "_" + _postFix, i);
                    final BigDecimal rate = ((BigDecimal) rateObj[0]).divide((BigDecimal) rateObj[1], 12,
                                    BigDecimal.ROUND_HALF_UP);
                    final Type type = Type.get(Long.parseLong(types[i]));
                    final Insert insert2 = new Insert(type);
                    insert2.add(CIAccounting.TransactionPositionAbstract.TransactionLink, _instance.getId());
                    insert2.add(CIAccounting.TransactionPositionAbstract.AccountLink,
                                                                           Instance.get(accountOids[i]).getId());
                    insert2.add(CIAccounting.TransactionPositionAbstract.CurrencyLink, curInstance.getId());
                    insert2.add(CIAccounting.TransactionPositionAbstract.RateCurrencyLink, curr[i]);
                    insert2.add(CIAccounting.TransactionPositionAbstract.Rate, rateObj);

                    final BigDecimal rateAmount = ((BigDecimal) formater.parse(amounts[i]))
                                                                       .setScale(6, BigDecimal.ROUND_HALF_UP);
                    final boolean isDebitTrans = type.getUUID().equals(CIAccounting.TransactionPositionDebit.uuid);
                    insert2.add(CIAccounting.TransactionPositionAbstract.RateAmount,
                                    isDebitTrans ? rateAmount.negate() : rateAmount);
                    final BigDecimal amount = rateAmount.divide(rate, 12, BigDecimal.ROUND_HALF_UP);
                    insert2.add(CIAccounting.TransactionPositionAbstract.Amount,
                                    isDebitTrans ? amount.negate() : amount);
                    insert2.execute();

                    final Instance instance2 = insert2.getInstance();
                    if (label2projectOids != null) {
                        final Long id2Label = Instance.get(label2projectOids[i]).getId();
                        if (id2Label != 0) {
                            Insert insert2Position = new Insert(CIAccounting.LabelProject2PositionCredit);
                            if (_postFix.equalsIgnoreCase("Debit")) {
                                insert2Position = new Insert(CIAccounting.LabelProject2PositionDebit);
                            }
                            insert2Position.add(CIAccounting.LabelProject2PositionAbstract.FromLabelLink, id2Label);
                            insert2Position.add(CIAccounting.LabelProject2PositionAbstract.ToPositionAbstractLink,
                                            instance2.getId());
                            insert2Position.execute();
                        }
                    }

                    final Instance accountInst = Instance.get(accountOids[i]);
                    final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.Account2AccountAbstract);
                    queryBldr.addWhereAttrEqValue(CIAccounting.Account2AccountAbstract.FromAccountLink,
                                       accountInst.getId());
                    final MultiPrintQuery print = queryBldr.getPrint();
                    print.addAttribute(CIAccounting.Account2AccountAbstract.Numerator,
                                      CIAccounting.Account2AccountAbstract.Denominator,
                                      CIAccounting.Account2AccountAbstract.ToAccountLink);
                    print.execute();
                    while (print.next()) {
                        final Instance instance = print.getCurrentInstance();
                        boolean add = false;
                        Insert insert3 = null;
                        BigDecimal amount2 = amount.multiply(new BigDecimal(print.<Integer>getAttribute("Numerator"))
                                        .divide(new BigDecimal(print.<Integer>getAttribute("Denominator")),
                                                        BigDecimal.ROUND_HALF_UP));
                        BigDecimal rateAmount2 = rateAmount.multiply(
                                        new BigDecimal(print.<Integer>getAttribute("Numerator"))
                                                  .divide(new BigDecimal(print.<Integer>getAttribute("Denominator")),
                                                                        BigDecimal.ROUND_HALF_UP));
                        if (instance.getType().getUUID().equals(CIAccounting.Account2AccountCosting.uuid)) {
                            if (account2accountOids != null) {
                                for (final String check : account2accountOids) {
                                    if (instance.getOid().equals(check)) {
                                        add = true;
                                        insert3 = new Insert(type);
                                        break;
                                    }
                                }
                            }
                        } else if (instance.getType().getUUID()
                                                       .equals(CIAccounting.Account2AccountCostingInverse.uuid)) {
                            if (account2accountOids != null) {
                                for (final String check : account2accountOids) {
                                    if (instance.getOid().equals(check)) {
                                        add = true;
                                        if (type.getUUID().equals(CIAccounting.TransactionPositionDebit.uuid)) {
                                            insert3 = new Insert(CIAccounting.TransactionPositionCredit.uuid);
                                        } else {
                                            insert3 = new Insert(CIAccounting.TransactionPositionDebit.uuid);
                                        }
                                        amount2 = amount2.negate();
                                        break;
                                    }
                                }
                            }
                        } else if (instance.getType().getUUID().equals(CIAccounting.Account2AccountCredit.uuid)) {
                            if (isDebitTrans) {
                                insert3 = new Insert(CIAccounting.TransactionPositionCredit);
                            } else {
                                insert3 = new Insert(CIAccounting.TransactionPositionDebit);
                                amount2 = amount2.negate();
                                rateAmount2 = rateAmount2.negate();
                            }
                            add = true;
                        } else if (instance.getType().getUUID().equals(CIAccounting.Account2AccountDebit.uuid)) {
                            if (isDebitTrans) {
                                insert3 = new Insert(CIAccounting.TransactionPositionDebit);
                                amount2 = amount2.negate();
                                rateAmount2 = rateAmount2.negate();
                            } else {
                                insert3 = new Insert(CIAccounting.TransactionPositionCredit);
                            }
                            add = true;
                        }
                        if (add) {
                            insert3.add(CIAccounting.TransactionPositionAbstract.TransactionLink, _instance.getId());
                            insert3.add(CIAccounting.TransactionPositionAbstract.AccountLink,
                                            print.getAttribute(CIAccounting.Account2AccountAbstract.ToAccountLink));
                            insert3.add(CIAccounting.TransactionPositionAbstract.CurrencyLink, curInstance.getId());
                            insert3.add(CIAccounting.TransactionPositionAbstract.RateCurrencyLink, curr[i]);
                            insert3.add(CIAccounting.TransactionPositionAbstract.Rate, rateObj);
                            insert3.add(CIAccounting.TransactionPositionAbstract.Amount, amount2);
                            insert3.add(CIAccounting.TransactionPositionAbstract.RateAmount, rateAmount2);
                            insert3.execute();
                        }
                    }
                }
            }
        } catch (final ParseException e) {
            throw new EFapsException(Transaction_Base.class, "insertPositions", e);
        }
    }

    /**
     * Create a position. The type is given with the properties of the calling
     * command.
     *
     * @param _parameter Parameter as passed from the eFaps API
     * @return new Return
     * @throws EFapsException on error
     */
    public Return createPosition(final Parameter _parameter)
        throws EFapsException
    {
        final Instance parent = _parameter.getCallInstance();
        final String amountStr = _parameter.getParameterValue("rateAmount");
        final String account = _parameter.getParameterValue("accountLink");
        final String rateCurrencyLink = _parameter.getParameterValue("rateCurrencyLink");

        final Map<?, ?> properties = (Map<?, ?>) _parameter.get(ParameterValues.PROPERTIES);
        final String typeName = (String) properties.get("Type");
        final Instance curInstance;
        if (parent.getType().isKindOf(CIAccounting.TransactionAbstract.getType())) {
            final PrintQuery print = new PrintQuery(parent);
            final SelectBuilder sel = new SelectBuilder().linkto(CIAccounting.TransactionAbstract.PeriodeLink).oid();
            print.addSelect(sel);
            print.execute();
            final Instance periodeInst = Instance.get(print.<String>getSelect(sel));
            curInstance = new Periode().getCurrency(periodeInst).getInstance();
        } else {
            curInstance = new Periode().getCurrency(_parameter.getCallInstance()).getInstance();
        }
        BigDecimal amount = DecimalType.parseLocalized(amountStr);
        final Type type = Type.get(typeName);
        if (!type.getUUID().equals(CIAccounting.TransactionPositionCredit.uuid)) {
            amount = amount.negate();
        }
        final Object[] rateObj = new Transaction().getRateObject(_parameter, "", 0);
        final BigDecimal rate = ((BigDecimal) rateObj[0]).divide((BigDecimal) rateObj[1], 12,
                        BigDecimal.ROUND_HALF_UP);
        final Insert insert = new Insert(type);
        insert.add(CIAccounting.TransactionPositionAbstract.TransactionLink, parent.getId());
        insert.add(CIAccounting.TransactionPositionAbstract.AccountLink, Instance.get(account).getId());
        insert.add(CIAccounting.TransactionPositionAbstract.CurrencyLink, curInstance.getId());
        insert.add(CIAccounting.TransactionPositionAbstract.RateCurrencyLink, rateCurrencyLink);
        insert.add(CIAccounting.TransactionPositionAbstract.Rate, rateObj);
        insert.add(CIAccounting.TransactionPositionAbstract.Amount, amount.divide(rate, 12, BigDecimal.ROUND_HALF_UP));
        insert.add(CIAccounting.TransactionPositionAbstract.RateAmount, amount);
        insert.execute();
        return new Return();
    }

    /**
     * Used form a from to create the transaction for opening a balance.
     *
     * @param _parameter Paramter as passed from the eFaps API
     * @return new Return
     * @throws EFapsException on error
     */
    public Return createTransactionOpeningBalance(final Parameter _parameter)
        throws EFapsException
    {
        final String debitAccOId = _parameter.getParameterValue("accountLink_Debit");
        final String[] amounts = _parameter.getParameterValues("amount");
        final String[] accounts = _parameter.getParameterValues("accountLink_Credit");
        final Instance periodeInst = _parameter.getInstance();

        final Insert insert = new Insert(CIAccounting.TransactionOpeningBalance);
        insert.add(CIAccounting.TransactionOpeningBalance.Name, 0);
        insert.add(CIAccounting.TransactionOpeningBalance.Description,
                        DBProperties.getProperty("org.efaps.esjp.accounting.Transaction.openingBalance.description"));
        insert.add(CIAccounting.TransactionOpeningBalance.Date, _parameter.getParameterValue("date"));
        insert.add(CIAccounting.TransactionOpeningBalance.PeriodeLink, periodeInst.getId());
        insert.add(CIAccounting.TransactionOpeningBalance.Status,
                        ((Long) Status.find(CIAccounting.TransactionStatus.uuid, "Open").getId()).toString());
        insert.execute();

        BigDecimal debitAmount = BigDecimal.ZERO;
        final CurrencyInst curr = new Periode().getCurrency(periodeInst);
        final DecimalFormat formater = new Transaction().getFormater(null, 2);
        for (int i = 0; i < amounts.length; i++) {
            final Instance accInst = Instance.get(accounts[i]);
            final PrintQuery print = new PrintQuery(accInst);
            print.addAttribute(CIAccounting.AccountAbstract.SumReport);
            print.execute();

            final BigDecimal sumreport = print.<BigDecimal>getAttribute(CIAccounting.AccountAbstract.SumReport);
            try {
                BigDecimal amount = (BigDecimal) formater.parse(amounts[i]);
                amount = amount.subtract(sumreport == null ? BigDecimal.ZERO : sumreport);
                final CIType type = amount.compareTo(BigDecimal.ZERO) > 0 ? CIAccounting.TransactionPositionCredit
                                                                         : CIAccounting.TransactionPositionDebit;
                final Insert posInsert = new Insert(type);
                posInsert.add(CIAccounting.TransactionPositionAbstract.AccountLink, accInst.getId());
                posInsert.add(CIAccounting.TransactionPositionAbstract.Amount, amount);
                posInsert.add(CIAccounting.TransactionPositionAbstract.CurrencyLink, curr.getInstance().getId());
                posInsert.add(CIAccounting.TransactionPositionAbstract.Rate, new Object[] { 1, 1 });
                posInsert.add(CIAccounting.TransactionPositionAbstract.RateAmount, amount);
                posInsert.add(CIAccounting.TransactionPositionAbstract.RateCurrencyLink, curr.getInstance().getId());
                posInsert.add(CIAccounting.TransactionPositionAbstract.TransactionLink, insert.getInstance().getId());
                posInsert.execute();
                debitAmount = debitAmount.add(amount);
            } catch (final ParseException e) {
                throw new EFapsException(Create_Base.class, "createTransactionOpeningBalance", e);
            }
        }
        final Instance accInst = Instance.get(debitAccOId);
        final CIType type = debitAmount.compareTo(BigDecimal.ZERO) < 0 ? CIAccounting.TransactionPositionCredit
                                                                      : CIAccounting.TransactionPositionDebit;
        final Insert posInsert = new Insert(type);
        posInsert.add(CIAccounting.TransactionPositionAbstract.AccountLink, accInst.getId());
        posInsert.add(CIAccounting.TransactionPositionAbstract.Amount, debitAmount.negate());
        posInsert.add(CIAccounting.TransactionPositionAbstract.CurrencyLink, curr.getInstance().getId());
        posInsert.add(CIAccounting.TransactionPositionAbstract.Rate, new Object[] { 1, 1 });
        posInsert.add(CIAccounting.TransactionPositionAbstract.RateAmount, debitAmount.negate());
        posInsert.add(CIAccounting.TransactionPositionAbstract.RateCurrencyLink, curr.getInstance().getId());
        posInsert.add(CIAccounting.TransactionPositionAbstract.TransactionLink, insert.getInstance().getId());
        posInsert.execute();

        return new Return();
    }


    /**
     * Create the transaction for a group of docs without user interaction.
     * @param _parameter Parameter as passed from the eFaps API
     * @return new Return
     * @throws EFapsException on error
     */
    public Return create4DocMassive(final Parameter _parameter)
        throws EFapsException
    {
        String[] oids = new String[0];
        final Object obj = _parameter.get(ParameterValues.OTHERS);
        if (obj instanceof String[]) {
            oids = (String[]) obj;
        } else if (Context.getThreadContext().containsSessionAttribute("storeOIDS")
                        && Context.getThreadContext().getSessionAttribute("storeOIDS") != null) {
            oids = (String[]) Context.getThreadContext().getSessionAttribute("storeOIDS");
            Context.getThreadContext().setSessionAttribute("storeOIDS", null);
        }

        final DateTime date = _parameter.getParameterValue("date") != null
                                    ? new DateTime(_parameter.getParameterValue("date"))
                                    : new DateTime().withTime(0, 0, 0, 0);

        for (final String oid : oids) {
            Context.getThreadContext()
                 .setSessionAttribute(Transaction_Base.PERIODE_SESSIONKEY, _parameter.getInstance());

            _parameter.getParameters().put("selectedRow", new String[] {oid});
            final FieldValue trans = new FieldValue();
            final Return ret = trans.getDescriptionFieldValue(_parameter);
            final String description = (String) ret.get(ReturnValues.VALUES);
            final PrintQuery print = new PrintQuery(oid);
            print.addAttribute(CIERP.DocumentAbstract.Name);
            print.execute();
            final String name = print.<String>getAttribute(CIERP.DocumentAbstract.Name);

            final Instance docInst = Instance.get(oid);
            final Document doc = trans.new Document(docInst);
            final Map<Long, Rate> rates = new HashMap<Long, Rate>();
            trans.getCostInformation(_parameter, date, doc, rates);
            if (doc.isCostValidated() && doc.getDifference().compareTo(BigDecimal.ZERO) == 0
                            && validateDoc(_parameter, doc, oids)) {
                final Insert insert = new Insert(CIAccounting.Transaction);
                insert.add(CIAccounting.Transaction.Name, name);
                insert.add(CIAccounting.Transaction.Description, description);
                insert.add(CIAccounting.Transaction.Date, date);
                insert.add(CIAccounting.Transaction.PeriodeLink, _parameter.getInstance().getId());
                insert.add(CIAccounting.Transaction.Status,
                                Status.find(CIAccounting.TransactionStatus.uuid, "Open").getId());
                insert.execute();
                final Instance transInst = insert.getInstance();
                for (final TargetAccount account : doc.getCreditAccounts().values()) {
                    insertPosition4Massiv(doc, transInst, CIAccounting.TransactionPositionCredit, account);
                }
                for (final TargetAccount account : doc.getDebitAccounts().values()) {
                    insertPosition4Massiv(doc, transInst, CIAccounting.TransactionPositionDebit, account);
                }
                createDocClass(transInst, docInst);
            }
        }
        return new Return();
    }

    /**
     * To be overwritten from implementation.
     * @param _parameter    Paramter as passed from the eFaps API
     * @param _doc          Document to be validates
     * @param _oids         Array of OIDs the ,method iterates
     * @return true if validation succeded else false
     * @throws EFapsException on error
     */
    protected boolean validateDoc(final Parameter _parameter,
                                  final Document _doc,
                                  final String[] _oids)
        throws EFapsException
    {
        return true;
    }

    /**
     * @param _doc          Document
     * @param _transInst    Transaction Instance
     * @param _type         CITYpe
     * @param _account      TargetAccount
     * @return Instance of the new position
     * @throws EFapsException   on error
     */
    protected Instance insertPosition4Massiv(final Document _doc,
                                             final Instance _transInst,
                                             final CIType _type,
                                             final TargetAccount _account)
        throws EFapsException
    {
        final boolean check2transaction = _type.equals(CIAccounting.TransactionPositionDebit);
        final Instance accInst = Instance.get(_account.getOid());
        final Insert insert = new Insert(_type);
        insert.add(CIAccounting.TransactionPositionAbstract.TransactionLink, _transInst.getId());
        insert.add(CIAccounting.TransactionPositionAbstract.AccountLink, accInst.getId());
        insert.add(CIAccounting.TransactionPositionAbstract.CurrencyLink,
                        _doc.getRate().getCurInstance().getInstance().getId());
        insert.add(CIAccounting.TransactionPositionAbstract.RateCurrencyLink,
                        _account.getRate().getCurInstance().getInstance().getId());
        insert.add(CIAccounting.TransactionPositionAbstract.Rate,
                        new Object[] {_doc.getRate().getValue(), _account.getRate().getValue() });
        BigDecimal rateAmount = _account.getAmount();
        insert.add(CIAccounting.TransactionPositionAbstract.RateAmount,
                        check2transaction ? rateAmount.negate() :  rateAmount);
        BigDecimal amount = _account.getAmountRate().setScale(2, BigDecimal.ROUND_HALF_UP);
        insert.add(CIAccounting.TransactionPositionAbstract.Amount,
                        check2transaction ? amount.negate() : amount);
        insert.execute();

        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.Account2AccountAbstract);
        queryBldr.addWhereAttrEqValue(CIAccounting.Account2AccountAbstract.FromAccountLink, accInst.getId());
        final MultiPrintQuery print = queryBldr.getPrint();
        print.addAttribute(CIAccounting.Account2AccountAbstract.Numerator,
                           CIAccounting.Account2AccountAbstract.Denominator,
                           CIAccounting.Account2AccountAbstract.ToAccountLink);
        print.execute();
        while (print.next()) {
            final Instance instance = print.getCurrentInstance();
            boolean add = false;
            Insert insert3 = null;
            if (instance.getType().getUUID().equals(CIAccounting.Account2AccountCredit.uuid)) {
                insert3 = new Insert(CIAccounting.TransactionPositionCredit);
                amount = amount.negate();
                rateAmount = rateAmount.negate();
                add = true;
            } else if (instance.getType().getUUID().equals(CIAccounting.Account2AccountDebit.uuid)) {
                insert3 = new Insert(CIAccounting.TransactionPositionDebit);
                add = true;
            }
            if (add) {
                insert3.add(CIAccounting.TransactionPositionAbstract.TransactionLink, _transInst.getId());
                insert3.add(CIAccounting.TransactionPositionAbstract.AccountLink,
                                print.getAttribute(CIAccounting.Account2AccountAbstract.ToAccountLink));
                insert3.add(CIAccounting.TransactionPositionAbstract.CurrencyLink,
                                _doc.getRate().getCurInstance().getInstance().getId());
                insert3.add(CIAccounting.TransactionPositionAbstract.RateCurrencyLink,
                                _account.getRate().getCurInstance().getInstance().getId());
                insert3.add(CIAccounting.TransactionPositionAbstract.Rate,
                                new Object[] {_doc.getRate().getValue(), _account.getRate().getValue() });
                insert3.add(CIAccounting.TransactionPositionAbstract.Amount, amount);
                insert3.add(CIAccounting.TransactionPositionAbstract.RateAmount, rateAmount);
                insert3.execute();
            }
        }
        return insert.getInstance();
    }
}
