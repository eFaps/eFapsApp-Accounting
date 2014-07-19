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
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;

import org.efaps.admin.datamodel.Status;
import org.efaps.admin.datamodel.Type;
import org.efaps.admin.datamodel.attributetype.DecimalType;
import org.efaps.admin.dbproperty.DBProperties;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Parameter.ParameterValues;
import org.efaps.admin.event.Return;
import org.efaps.admin.program.esjp.EFapsRevision;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.ci.CIType;
import org.efaps.db.CachedPrintQuery;
import org.efaps.db.Context;
import org.efaps.db.Insert;
import org.efaps.db.Instance;
import org.efaps.db.InstanceQuery;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.PrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.db.Update;
import org.efaps.esjp.accounting.Period;
import org.efaps.esjp.accounting.SubPeriod_Base;
import org.efaps.esjp.accounting.util.Accounting;
import org.efaps.esjp.accounting.util.AccountingSettings;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.ci.CIERP;
import org.efaps.esjp.ci.CIFormAccounting;
import org.efaps.esjp.ci.CISales;
import org.efaps.esjp.common.parameter.ParameterUtil;
import org.efaps.esjp.erp.CurrencyInst;
import org.efaps.esjp.erp.RateInfo;
import org.efaps.ui.wicket.util.DateUtil;
import org.efaps.util.EFapsException;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 * @version $Id$
 */
@EFapsUUID("7b2870fe-4566-4688-b015-263c910c34e2")
@EFapsRevision("$Rev$")
public abstract class Create_Base
    extends Transaction
{
    /**
     * Logger for this class.
     */
    private static final Logger LOG = LoggerFactory.getLogger(Create_Base.class);

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
        insert.add(CIAccounting.Transaction.PeriodLink, parent.getId());
        insert.add(CIAccounting.Transaction.Status, Status.find(CIAccounting.TransactionStatus.Open));
        insert.add(CIAccounting.Transaction.Identifier, Transaction.IDENTTEMP);
        insert.execute();

        final Instance instance = insert.getInstance();
        final int pos = insertPositions(_parameter, instance, "Debit", null, 1);
        insertPositions(_parameter, instance, "Credit", null, pos);
        // create classifications
        new org.efaps.esjp.common.uiform.Create().insertClassification(_parameter, instance);

        insertReportRelation(_parameter, instance);
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

        final Instance periodInst = new Period().evaluateCurrentPeriod(_parameter);
        final Insert insert = new Insert(CIAccounting.Transaction);
        insert.add(CIAccounting.Transaction.Name, _parameter.getParameterValue("name"));
        insert.add(CIAccounting.Transaction.Description, _parameter.getParameterValue("description"));
        insert.add(CIAccounting.Transaction.Date, _parameter.getParameterValue("date"));
        insert.add(CIAccounting.Transaction.PeriodLink, periodInst.getId());
        insert.add(CIAccounting.Transaction.Status, Status.find(CIAccounting.TransactionStatus.Open));
        insert.add(CIAccounting.Transaction.Identifier, Transaction.IDENTTEMP);
        insert.execute();
        final Instance instance = insert.getInstance();
        final int pos = insertPositions(_parameter, instance, postfix, new String[] { _parameter.getCallInstance()
                        .getOid() }, 0);
        insertPositions(_parameter, instance, "Debit".equals(postfix) ? "Credit" : "Debit", null, pos);
        new org.efaps.esjp.common.uiform.Create().insertClassification(_parameter, instance);
        return new Return();
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
        connectDoc2Transaction(_parameter, instance, docInst);
        setStatus4Doc(_parameter, docInst);

        if (_parameter.getParameterValue("payment") != null) {
            final boolean setPayStatus = "true".equals(_parameter.getParameterValue("paymentStatus"));

            for (final String paymentOid : _parameter.getParameterValues("payment")) {
                final Instance payInst = Instance.get(paymentOid);
                Insert insert = null;
                long statusId = 0;
                if (CIAccounting.PaymentCheck.getType().equals(payInst.getType())) {
                    insert = new Insert(CIAccounting.Document2PaymentDocument);
                    statusId = Status.find(CIAccounting.PaymentDocumentStatus.uuid, "Closed").getId();
                }
                insert.add(CIAccounting.Document2PaymentDocument.FromLink, docInst.getId());
                insert.add(CIAccounting.Document2PaymentDocument.ToLink, payInst.getId());
                insert.executeWithoutAccessCheck();

                if (setPayStatus) {
                    final Update update = new Update(payInst);
                    update.add(CIAccounting.PaymentDocumentAbstract.StatusAbstract, statusId);
                    update.executeWithoutTrigger();
                }
            }
        }
        add2Create4Doc(_parameter);
        return new Return();
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
        Instance docInst = Instance.get(_parameter.getParameterValue("document"));
        String descr = "";
        if (!docInst.isValid()) {
            docInst = _parameter.getInstance();
            final PrintQuery print = new PrintQuery(docInst);
            print.addAttribute(CIAccounting.ExternalVoucher.Revision);
            print.execute();
            final String revision = print.<String>getAttribute(CIAccounting.ExternalVoucher.Revision);
            if (revision != null) {
                descr = revision + " - ";
            }
        }
        descr = descr  + _parameter.getParameterValue("description");

        final Instance instance = createBaseTrans(_parameter, descr);

        if (docInst != null && docInst.isValid()) {
            connectDoc2Transaction(_parameter, instance, docInst);
            connectDoc2PurchaseRecord(_parameter, docInst);
            setStatus4Doc(_parameter, docInst);
        }
        return new Return();
    }

    /**
     * @param _parameter as passed from the eFaps API
     * @return new empty Return
     * @throws EFapsException on error.
     */
    public Return create4Exchange(final Parameter _parameter)
        throws EFapsException
    {
        return create4Doc(_parameter);
    }

    /**
     * @param _parameter as passed from the eFaps API
     * @return new empty Return
     * @throws EFapsException on error.
     */
    public Return create4FundsToBeSettled(final Parameter _parameter)
        throws EFapsException
    {
        return create4Doc(_parameter);
    }

    /**
     * @param _parameter as passed from the eFaps API
     * @return new empty Return
     * @throws EFapsException on error.
     */
    public Return create4FundsToBeSettledMassive(final Parameter _parameter)
        throws EFapsException
    {
        return create4DocMassive(_parameter);
    }

    /**
     * @param _parameter as passed from the eFaps API
     * @return new empty Return
     * @throws EFapsException on error.
     */
    public Return create4PettyCashReceipt(final Parameter _parameter)
        throws EFapsException
    {
        return create4Doc(_parameter);
    }

    /**
     * @param _parameter as passed from the eFaps API
     * @return new empty Return
     * @throws EFapsException on error.
     */
    public Return create4PettyCashMassive(final Parameter _parameter)
        throws EFapsException
    {
        return create4DocMassive(_parameter);
    }

    /**
     * @param _parameter as passed from the eFaps API
     * @return new empty Return
     * @throws EFapsException on error.
     */
    public Return create4RetPer(final Parameter _parameter)
        throws EFapsException
    {
        return create4Doc(_parameter);
    }

    /**
     * @param _parameter Parameter as passed by the eFaps API
     * @return new empty Return
     * @throws EFapsException on error
     */
    public Return create4PaymentMassive(final Parameter _parameter)
        throws EFapsException
    {

        final DateTime date = new DateTime(_parameter.getParameterValue(
                        CIFormAccounting.Accounting_TransactionCreate4PaymentMassiveForm.date.name));
        final boolean usedate = Boolean.parseBoolean(_parameter.getParameterValue(
                        CIFormAccounting.Accounting_TransactionCreate4PaymentMassiveForm.useDate.name));
        final String[] oidsPay = (String[]) Context.getThreadContext().getSessionAttribute(
                        CIFormAccounting.Accounting_TransactionCreate4PaymentMassiveForm.document.name);
        final Instance periodInst = new Period().evaluateCurrentPeriod(_parameter);
        for (final String oid : oidsPay) {
            final Instance payDocInst = Instance.get(oid);
            if (payDocInst.isValid()) {
                final Parameter parameter = ParameterUtil.clone(_parameter, _parameter);
                parameter.getParameters().put("document", new String[] { oid });
                if (usedate) {
                    parameter.getParameters().put("date_eFapsDate", new String[] { DateUtil.getDate4Parameter(date) });
                }

                final DocumentInfo docInfo = evalDocuments(parameter).get(0);
                if (docInfo.isValid()) {
                    final Insert insert = new Insert(CIAccounting.Transaction);
                    insert.add(CIAccounting.Transaction.Description,
                                    new FieldValue().getDescription(_parameter, docInfo.getInstance()));
                    insert.add(CIAccounting.Transaction.Date, docInfo.getDate());
                    insert.add(CIAccounting.Transaction.PeriodLink, periodInst);
                    insert.add(CIAccounting.Transaction.Status, Status.find(CIAccounting.TransactionStatus.Open));
                    insert.execute();
                    final Instance transInst = insert.getInstance();
                    for (final AccountInfo account : docInfo.getCreditAccounts()) {
                        insertPosition4Massiv(_parameter, docInfo, transInst, CIAccounting.TransactionPositionCredit,
                                        account);
                    }
                    for (final AccountInfo account : docInfo.getDebitAccounts()) {
                        insertPosition4Massiv(_parameter, docInfo, transInst, CIAccounting.TransactionPositionDebit,
                                        account);
                    }
                    connectDoc2Transaction(_parameter, transInst, docInfo.getInstance());
                    setStatus4Payment(_parameter, docInfo.getInstance());
                }
            }
        }
        return new Return();
    }

    /**
     * Create a Transaction for a PaymentDocument.
     * Connects the PaymentDocument and the related Document with the transaction.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return new empty Return
     * @throws EFapsException on error
     */
    public Return create4Payment(final Parameter _parameter)
        throws EFapsException
    {
        final Instance instance = createBaseTrans(_parameter, _parameter.getParameterValue("description"));
        final Instance payDocInst = Instance.get(_parameter.getParameterValue("document"));
        connectDoc2Transaction(_parameter, instance, payDocInst);
        setStatus4Payment(_parameter, payDocInst);
        return new Return();
    }

    /**
     * @param _parameter as passed from the eFaps API
     * @return new empty Return
     * @throws EFapsException on error.
     */
    public Return create4Securities(final Parameter _parameter)
        throws EFapsException
    {
        return create4Doc(_parameter);
    }

    /**
     * Create the transaction for a group of docs without user interaction.
     * @param _parameter Parameter as passed from the eFaps API
     * @return new Return
     * @throws EFapsException on error
     */
    public Return create4StockDocMassive(final Parameter _parameter)
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
                 .setSessionAttribute(Transaction_Base.PERIOD_SESSIONKEY, _parameter.getInstance());
            final Instance docInst = Instance.get(oid);
            final String description = new FieldValue().getDescription(_parameter, docInst);
            final PrintQuery print = new PrintQuery(oid);
            print.addAttribute(CIERP.DocumentAbstract.Name);
            print.execute();
            final String name = print.<String>getAttribute(CIERP.DocumentAbstract.Name);
            final DocumentInfo doc = new DocumentInfo(docInst);
            new FieldValue().getCostInformation(_parameter, date, doc);
            if (doc.getInstance() != null) {
                doc.setInvert(doc.getInstance().getType().isKindOf(CISales.ReturnSlip.getType()));
            }
            if (doc.isCostValidated() && doc.getDifference().compareTo(BigDecimal.ZERO) == 0
                            && doc.getAmount().compareTo(BigDecimal.ZERO) != 0
                            && doc.getCreditSum().compareTo(doc.getAmount()) == 0
                            && validateDoc(_parameter, doc, oids)) {
                final Insert insert = new Insert(CIAccounting.Transaction);
                insert.add(CIAccounting.Transaction.Name, name);
                insert.add(CIAccounting.Transaction.Description, description);
                insert.add(CIAccounting.Transaction.Date, date);
                insert.add(CIAccounting.Transaction.PeriodLink, _parameter.getInstance().getId());
                insert.add(CIAccounting.Transaction.Status, Status.find(CIAccounting.TransactionStatus.Open));
                insert.add(CIAccounting.Transaction.Identifier, Transaction.IDENTTEMP);
                insert.execute();
                final Instance transInst = insert.getInstance();
                for (final AccountInfo account : doc.getCreditAccounts()) {
                    insertPosition4Massiv(_parameter, doc, transInst, CIAccounting.TransactionPositionCredit, account);
                }
                for (final AccountInfo account : doc.getDebitAccounts()) {
                    insertPosition4Massiv(_parameter, doc, transInst, CIAccounting.TransactionPositionDebit, account);
                }
                connectDoc2Transaction(_parameter, transInst, docInst);
            }
        }
        return new Return();
    }

    /**
     * @param _parameter Parameter as passed from the eFaps API
     * @return new empty Return
     * @throws EFapsException on error
     */
    public Return create4DocMassive(final Parameter _parameter)
        throws EFapsException
    {
        final Instance periodInst = new Period().evaluateCurrentPeriod(_parameter);
        DateTime date = new DateTime(_parameter
                        .getParameterValue(CIFormAccounting.Accounting_MassiveRegister4DocumentForm.date.name));
        final boolean useDateForm = Boolean.parseBoolean(_parameter
                        .getParameterValue(CIFormAccounting.Accounting_MassiveRegister4DocumentForm.useDate.name));
        final boolean useRounding = Boolean.parseBoolean(_parameter
                        .getParameterValue(CIFormAccounting.Accounting_MassiveRegister4DocumentForm.useRounding.name));
        final String[] oidsDoc = (String[]) Context.getThreadContext()
                        .getSessionAttribute(CIFormAccounting.Accounting_MassiveRegister4DocumentForm.storeOIDS.name);
        for (final String oid : oidsDoc) {
            final Instance instDoc = Instance.get(oid);
            if (instDoc.isValid()) {
                final Instance caseInst = Instance.get(_parameter.getParameterValue("case"));
                final PrintQuery printCase = new PrintQuery(caseInst);
                printCase.addAttribute(CIAccounting.CaseAbstract.IsCross);
                printCase.execute();
                final Boolean isCross = printCase.<Boolean>getAttribute(CIAccounting.CaseAbstract.IsCross);
                final String attrName = isCross ? CISales.DocumentSumAbstract.RateCrossTotal.name
                                : CISales.DocumentSumAbstract.RateNetTotal.name;
                final PrintQuery print = new PrintQuery(instDoc);
                final SelectBuilder sel = SelectBuilder.get().linkto(CISales.DocumentSumAbstract.RateCurrencyId)
                                .instance();
                print.addSelect(sel);
                print.addAttribute(CISales.DocumentSumAbstract.Name,
                                CISales.DocumentSumAbstract.Date);
                print.addAttribute(attrName);
                print.execute();

                if (!useDateForm) {
                    date = print.<DateTime>getAttribute(CISales.DocumentSumAbstract.Date);
                }
                final String name = print.<String>getAttribute(CISales.DocumentSumAbstract.Name);
                final Instance currInst = print.<Instance>getSelect(sel);

                final BigDecimal amount = print.<BigDecimal>getAttribute(attrName);
                final DateTimeFormatter formatter = DateTimeFormat.mediumDate();
                final String dateStr = date.withChronology(Context.getThreadContext().getChronology())
                                .toString(formatter.withLocale(Context.getThreadContext().getLocale()));
                final StringBuilder val = new StringBuilder();
                val.append(DBProperties.getProperty(instDoc.getType().getName() + ".Label")).append(" ")
                                .append(name).append(" ").append(dateStr);
                final String desc = val.toString();

                final Transaction txn = new Transaction();
                final RateInfo rate = txn.evaluateRate(_parameter, periodInst, date, currInst);
                final DocumentInfo doc = new DocumentInfo(instDoc);
                doc.setAmount(amount);
                doc.setFormater(txn.getFormater(2, 2));
                doc.setDate(date);
                doc.setRateInfo(rate);

                new Transaction().add2Doc4Case(_parameter, doc);

                // validate the transaction and if necessary create rounding
                // parts
                final PrintQuery checkPrint = new PrintQuery(doc.getInstance());
                checkPrint.addAttribute(CISales.DocumentSumAbstract.RateCrossTotal);
                checkPrint.executeWithoutAccessCheck();

                final BigDecimal checkCrossTotal = checkPrint
                                .<BigDecimal>getAttribute(CISales.DocumentSumAbstract.RateCrossTotal);
                BigDecimal debit = doc.getRateDebitSum();
                BigDecimal credit = doc.getRateCreditSum();

                if (useRounding) {
                    // is does not sum to 0 but is less then the max defined
                    final Properties props = Accounting.getSysConfig().getObjectAttributeValueAsProperties(periodInst);
                    final String diffMinStr = props.getProperty(AccountingSettings.PERIOD_ROUNDINGMAXAMOUNT);
                    final BigDecimal diffMin = (diffMinStr != null && !diffMinStr.isEmpty())
                                    ? new BigDecimal(diffMinStr) : BigDecimal.ZERO;
                    if ((checkCrossTotal.compareTo(debit) != 0 || checkCrossTotal.compareTo(credit) != 0)
                                    && debit.subtract(credit).abs().compareTo(diffMin) < 0) {
                        final AccountInfo acc = doc.getDebitAccounts().iterator().next();
                        if (checkCrossTotal.compareTo(debit) > 0) {
                            final AccountInfo account = getRoundingAccount(_parameter,
                                            AccountingSettings.PERIOD_ROUNDINGDEBIT);
                            account.setAmount(checkCrossTotal.subtract(debit));
                            account.setRateInfo(acc.getRateInfo());
                            final BigDecimal rateAmount = account.getAmount().setScale(12, BigDecimal.ROUND_HALF_UP)
                                            .divide(rate.getRate(), 12, BigDecimal.ROUND_HALF_UP);
                            account.setAmountRate(rateAmount);
                            doc.addDebit(account);
                            debit = debit.add(checkCrossTotal.subtract(debit));
                        }
                        if (checkCrossTotal.compareTo(credit) > 0) {
                            final AccountInfo account = getRoundingAccount(_parameter,
                                            AccountingSettings.PERIOD_ROUNDINGCREDIT);
                            doc.addCredit(account);
                            account.setAmount(checkCrossTotal.subtract(credit));
                            account.setRateInfo(acc.getRateInfo());
                            final BigDecimal rateAmount = account.getAmount().setScale(12, BigDecimal.ROUND_HALF_UP)
                                            .divide(rate.getRate(), 12, BigDecimal.ROUND_HALF_UP);
                            account.setAmountRate(rateAmount);
                            credit = credit.add(checkCrossTotal.subtract(credit));
                        }
                    }
                }
                final boolean valid = checkCrossTotal.compareTo(debit) == 0 && checkCrossTotal.compareTo(credit) == 0;

                if (valid) {
                    createTrans4DocMassive(_parameter, doc, periodInst, desc);
                    setStatus4Doc(_parameter, doc.getInstance());
                    connectDoc2PurchaseRecord(_parameter, doc.getInstance());
                }
            }
        }
        return new Return();
    }

    /**
     * @param _parameter    Parameter as passe by the eFaps api
     * @param _transInst    instance of the transaction
     * @param _docInstances docuemtn instances
     * @throws EFapsException on error
     */
    public void connectDoc2Transaction(final Parameter _parameter,
                                       final Instance _transInst,
                                       final Instance... _docInstances)
        throws EFapsException
    {
        for (final Instance docInst : _docInstances) {
            if (docInst.isValid()) {
                final Insert insert;
                if (docInst.getType().isKindOf(CIERP.PaymentDocumentAbstract.getType())) {
                    insert = new Insert(CIAccounting.Transaction2PaymentDocument);
                } else {
                    insert = new Insert(CIAccounting.Transaction2SalesDocument);
                }
                insert.add(CIAccounting.Transaction2ERPDocument.FromLink, _transInst);
                insert.add(CIAccounting.Transaction2ERPDocument.ToLinkAbstract, docInst);
                insert.execute();
            }
        }
    }

    /**
     * Set the Status to booked for a PaymentDocument.
     * @param _parameter    Parameter as passe by the eFaps API
     * @param _payDocInst   Instance of the PaymentDocument the Status willl be set
     * @throws EFapsException on error
     */
    protected void setStatus4Payment(final Parameter _parameter,
                                     final Instance _payDocInst)
        throws EFapsException
    {
        // update the status
        final Status status = Status.find(_payDocInst.getType().getStatusAttribute().getLink().getUUID(), "Booked");
        if (status != null) {
            final Update update = new Update(_payDocInst);
            update.add(CIERP.PaymentDocumentAbstract.StatusAbstract, status.getId());
            update.execute();
        }
    }

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
        // in case that an account is the parent the period is searched
        if (parent.getType().isKindOf(CIAccounting.AccountAbstract.getType())) {
            final PrintQuery print = new PrintQuery(parent);
            final SelectBuilder sel = new SelectBuilder().linkto(CIAccounting.AccountAbstract.PeriodAbstractLink)
                            .instance();
            print.addSelect(sel);
            print.execute();
            parent = print.<Instance>getSelect(sel);
        } else if (parent.getType().isKindOf(CIAccounting.SubPeriod.getType())) {
            final PrintQuery print = new CachedPrintQuery(parent, SubPeriod_Base.CACHEKEY);
            final SelectBuilder selPeriodInst = SelectBuilder.get().linkto(CIAccounting.SubPeriod.PeriodLink)
                            .instance();
            print.addSelect(selPeriodInst);
            print.execute();
            parent = print.<Instance>getSelect(selPeriodInst);
        }
        final Insert insert = new Insert(CIAccounting.Transaction);
        insert.add(CIAccounting.Transaction.Name, _parameter.getParameterValue("name"));
        insert.add(CIAccounting.Transaction.Description, _description);
        insert.add(CIAccounting.Transaction.Date, _parameter.getParameterValue("date"));
        insert.add(CIAccounting.Transaction.PeriodLink, parent);
        insert.add(CIAccounting.Transaction.Status, Status.find(CIAccounting.TransactionStatus.Open));
        insert.add(CIAccounting.Transaction.Identifier, Transaction.IDENTTEMP);
        insert.execute();
        final Instance instance = insert.getInstance();

        final int pos = insertPositions(_parameter, instance, "Debit", null, 1);
        insertPositions(_parameter, instance, "Credit", null, pos);

        insertReportRelation(_parameter, instance);

        return instance;
    }

    /**
     * Connect the document to the Purchaserecord.
     * @param _parameter Parameter as passed by the eFaps API
     * @param _instance instance of the document
     * @throws EFapsException   on error
     */
    protected void connectDoc2PurchaseRecord(final Parameter _parameter,
                                             final Instance _instance)
        throws EFapsException
    {
        final Instance purchaseRecInst = Instance.get(_parameter.getParameterValue("purchaseRecord"));
        if (purchaseRecInst.isValid()) {
            final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.PurchaseRecord2Document);
            queryBldr.addWhereAttrEqValue(CIAccounting.PurchaseRecord2Document.ToLink, _instance);
            final InstanceQuery query = queryBldr.getQuery();
            if (query.executeWithoutAccessCheck().isEmpty()) {
                final Insert purInsert = new Insert(CIAccounting.PurchaseRecord2Document);
                purInsert.add(CIAccounting.PurchaseRecord2Document.FromLink, purchaseRecInst);
                purInsert.add(CIAccounting.PurchaseRecord2Document.ToLink, _instance);
                purInsert.execute();
            }
        }
    }

    /**
     * Set the status to booked if  marked in the Form. Set Status to Open if in status Digitized.
     * @param _parameter    Parameter as passed by the eFasp API
     * @param _instance     instance of the document
     * @throws EFapsException on error
     */
    protected void setStatus4Doc(final Parameter _parameter,
                                 final Instance _instance)
        throws EFapsException
    {
        final boolean setStatus = "true".equals(_parameter.getParameterValue("docStatus"));
        if (setStatus) {
            final Update update = new Update(_instance);
            update.add(CIERP.DocumentAbstract.StatusAbstract,
                            Status.find(_instance.getType().getStatusAttribute().getLink().getUUID(), "Booked"));
            update.execute();
        } else {
            final PrintQuery print = new PrintQuery(_instance);
            print.addAttribute(CIERP.DocumentAbstract.StatusAbstract);
            print.executeWithoutAccessCheck();
            final Status status = Status.get(print.<Long>getAttribute(CIERP.DocumentAbstract.StatusAbstract));
            if ("Digitized".equals(status.getKey())) {
                final Status newStatus = Status.find(status.getStatusGroup().getUUID(), "Open");
                if (newStatus != null) {
                    final Update update = new Update(_instance);
                    update.add(CIERP.DocumentAbstract.StatusAbstract, newStatus);
                    update.execute();
                }
            }
        }
    }

    /**
     * Method to execute additional events.
     *
     * @param _parameter as passed from eFaps API.
     * @throws EFapsException on error.
     */
    protected void add2Create4Doc(final Parameter _parameter)
        throws EFapsException
    {

    }

    /**
     * @param _parameter            Parameter as passed from the eFaps API
     * @param _transactionInstance  instance of the Transaction
     * @throws EFapsException on error
     */
    protected void insertReportRelation(final Parameter _parameter,
                                        final Instance _transactionInstance)
        throws EFapsException
    {
        final Instance repInst = Instance.get(_parameter.getParameterValue("subJournal"));
        if (repInst.isValid()) {
            final Insert insert = new Insert(CIAccounting.ReportSubJournal2Transaction);
            insert.add(CIAccounting.ReportSubJournal2Transaction.FromLink, repInst.getId());
            insert.add(CIAccounting.ReportSubJournal2Transaction.ToLink, _transactionInstance.getId());
            insert.execute();
        }
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
    public Integer insertPositions(final Parameter _parameter,
                                   final Instance _instance,
                                   final String _postFix,
                                   final String[] _accountOids,
                                   final int _pos)
        throws EFapsException
    {
        int ret = _pos;
        final String[] accountOids = _accountOids == null
                        ? _parameter.getParameterValues("accountLink_" + _postFix) : _accountOids;
        final String[] amounts = _parameter.getParameterValues("amount_" + _postFix);
        final String[] types = _parameter.getParameterValues("type_" + _postFix);
        final String[] curr = _parameter.getParameterValues("rateCurrencyLink_" + _postFix);
        final String[] acc2accOids = _parameter.getParameterValues("acc2acc_" + _postFix);
        final String[] label2projectOids = _parameter.getParameterValues("labelLink_" + _postFix);
        final DecimalFormat formater = new Transaction().getFormater(null, null);
        try {
            Instance inst = _parameter.getCallInstance();
            if (!inst.getType().isKindOf(CIAccounting.Period.getType())) {
                inst = new Period().evaluateCurrentPeriod(_parameter);
            }
            final Instance curInstance = new Period().getCurrency(inst).getInstance();
            if (amounts != null) {
                for (int i = 0; i < amounts.length; i++) {
                    final Object[] rateObj = new Transaction().getRateObject(_parameter, "_" + _postFix, i);
                    final BigDecimal rate = ((BigDecimal) rateObj[0]).divide((BigDecimal) rateObj[1], 12,
                                    BigDecimal.ROUND_HALF_UP);
                    final Type type = Type.get(Long.parseLong(types[i]));
                    final Insert posInsert = new Insert(type);
                    posInsert.add(CIAccounting.TransactionPositionAbstract.Position, ret++);
                    posInsert.add(CIAccounting.TransactionPositionAbstract.TransactionLink, _instance);
                    posInsert.add(CIAccounting.TransactionPositionAbstract.AccountLink, Instance.get(accountOids[i]));
                    posInsert.add(CIAccounting.TransactionPositionAbstract.CurrencyLink, curInstance);
                    posInsert.add(CIAccounting.TransactionPositionAbstract.RateCurrencyLink, curr[i]);
                    posInsert.add(CIAccounting.TransactionPositionAbstract.Rate, rateObj);

                    final BigDecimal rateAmount = ((BigDecimal) formater.parse(amounts[i]))
                                                                       .setScale(6, BigDecimal.ROUND_HALF_UP);
                    final boolean isDebitTrans = type.getUUID().equals(CIAccounting.TransactionPositionDebit.uuid);
                    posInsert.add(CIAccounting.TransactionPositionAbstract.RateAmount,
                                    isDebitTrans ? rateAmount.negate() : rateAmount);
                    final BigDecimal amount = rateAmount.divide(rate, 12, BigDecimal.ROUND_HALF_UP);
                    posInsert.add(CIAccounting.TransactionPositionAbstract.Amount,
                                    isDebitTrans ? amount.negate() : amount);
                    posInsert.execute();

                    final Instance posInstance = posInsert.getInstance();
                    if (label2projectOids != null) {
                        final Long id2Label = Instance.get(label2projectOids[i]).getId();
                        if (id2Label != 0) {
                            Insert insert2Position = new Insert(CIAccounting.LabelProject2PositionCredit);
                            if (_postFix.equalsIgnoreCase("Debit")) {
                                insert2Position = new Insert(CIAccounting.LabelProject2PositionDebit);
                            }
                            insert2Position.add(CIAccounting.LabelProject2PositionAbstract.FromLabelLink, id2Label);
                            insert2Position.add(CIAccounting.LabelProject2PositionAbstract.ToPositionAbstractLink,
                                            posInstance);
                            insert2Position.execute();
                        }
                    }

                    final Instance accountInst = Instance.get(accountOids[i]);
                    final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.Account2AccountAbstract);
                    queryBldr.addWhereAttrEqValue(CIAccounting.Account2AccountAbstract.FromAccountLink,
                                       accountInst.getId());
                    final MultiPrintQuery multi = queryBldr.getPrint();
                    multi.addAttribute(CIAccounting.Account2AccountAbstract.Numerator,
                                      CIAccounting.Account2AccountAbstract.Denominator,
                                      CIAccounting.Account2AccountAbstract.ToAccountLink,
                                      CIAccounting.Account2AccountAbstract.Deactivatable);
                    multi.execute();
                    while (multi.next()) {
                        final Instance instance = multi.getCurrentInstance();
                        Insert insert3 = null;
                        Boolean deactivatable = multi
                                        .<Boolean>getAttribute(CIAccounting.Account2AccountAbstract.Deactivatable);
                        if (deactivatable == null) {
                            deactivatable = false;
                        }
                        // if not deactivatable or selected in the UserInterface
                        if (!deactivatable || (acc2accOids != null && deactivatable
                                        &&  Arrays.asList(acc2accOids).contains(instance.getOid()))) {
                            final BigDecimal numerator = new BigDecimal(multi.<Integer>getAttribute(
                                            CIAccounting.Account2AccountAbstract.Numerator));
                            final BigDecimal denominator = new BigDecimal(multi.<Integer>getAttribute(
                                            CIAccounting.Account2AccountAbstract.Denominator));

                            BigDecimal amount2 = amount.multiply(numerator).divide(denominator,
                                            BigDecimal.ROUND_HALF_UP);
                            BigDecimal rateAmount2 = rateAmount.multiply(numerator).divide(denominator,
                                            BigDecimal.ROUND_HALF_UP);

                            if (instance.getType().getUUID().equals(CIAccounting.Account2AccountCosting.uuid)) {
                                insert3 = new Insert(type);
                            } else if (instance.getType().getUUID()
                                            .equals(CIAccounting.Account2AccountCostingInverse.uuid)) {
                                if (type.getUUID().equals(CIAccounting.TransactionPositionDebit.uuid)) {
                                    insert3 = new Insert(CIAccounting.TransactionPositionCredit.uuid);
                                } else {
                                    insert3 = new Insert(CIAccounting.TransactionPositionDebit.uuid);
                                }
                                amount2 = amount2.negate();
                            } else if (instance.getType().getUUID().equals(CIAccounting.Account2AccountCredit.uuid)) {
                                if (isDebitTrans) {
                                    insert3 = new Insert(CIAccounting.TransactionPositionCredit);
                                } else {
                                    insert3 = new Insert(CIAccounting.TransactionPositionDebit);
                                    amount2 = amount2.negate();
                                    rateAmount2 = rateAmount2.negate();
                                }
                            } else if (instance.getType().getUUID().equals(CIAccounting.Account2AccountDebit.uuid)) {
                                if (isDebitTrans) {
                                    insert3 = new Insert(CIAccounting.TransactionPositionDebit);
                                    amount2 = amount2.negate();
                                    rateAmount2 = rateAmount2.negate();
                                } else {
                                    insert3 = new Insert(CIAccounting.TransactionPositionCredit);
                                }
                            }
                            if (insert3 == null) {
                                Create_Base.LOG.error("Missing defintion");
                            } else {
                                insert3.add(CIAccounting.TransactionPositionAbstract.Position, ret++);
                                insert3.add(CIAccounting.TransactionPositionAbstract.TransactionLink, _instance);
                                insert3.add(CIAccounting.TransactionPositionAbstract.AccountLink,
                                                multi.getAttribute(CIAccounting.Account2AccountAbstract.ToAccountLink));
                                insert3.add(CIAccounting.TransactionPositionAbstract.CurrencyLink, curInstance);
                                insert3.add(CIAccounting.TransactionPositionAbstract.RateCurrencyLink, curr[i]);
                                insert3.add(CIAccounting.TransactionPositionAbstract.Rate, rateObj);
                                insert3.add(CIAccounting.TransactionPositionAbstract.Amount, amount2);
                                insert3.add(CIAccounting.TransactionPositionAbstract.RateAmount, rateAmount2);
                                insert3.execute();
                            }
                        }
                    }
                }
            }
        } catch (final ParseException e) {
            throw new EFapsException(Transaction_Base.class, "insertPositions", e);
        }
        return ret;
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
            final SelectBuilder sel = new SelectBuilder().linkto(CIAccounting.TransactionAbstract.PeriodLink).oid();
            print.addSelect(sel);
            print.execute();
            final Instance periodInst = Instance.get(print.<String>getSelect(sel));
            curInstance = new Period().getCurrency(periodInst).getInstance();
        } else {
            curInstance = new Period().getCurrency(_parameter.getCallInstance()).getInstance();
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
        final Instance periodInst = _parameter.getInstance();

        final Insert insert = new Insert(CIAccounting.TransactionOpeningBalance);
        insert.add(CIAccounting.TransactionOpeningBalance.Name, 0);
        insert.add(CIAccounting.TransactionOpeningBalance.Description,
                        DBProperties.getProperty("org.efaps.esjp.accounting.Transaction.openingBalance.description"));
        insert.add(CIAccounting.TransactionOpeningBalance.Date, _parameter.getParameterValue("date"));
        insert.add(CIAccounting.TransactionOpeningBalance.PeriodLink, periodInst.getId());
        insert.add(CIAccounting.TransactionOpeningBalance.Status,
                        ((Long) Status.find(CIAccounting.TransactionStatus.uuid, "Open").getId()).toString());
        insert.execute();

        BigDecimal debitAmount = BigDecimal.ZERO;
        final CurrencyInst curr = new Period().getCurrency(periodInst);
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
     * To be overwritten from implementation.
     * @param _parameter    Paramter as passed from the eFaps API
     * @param _doc          Document to be validates
     * @param _oids         Array of OIDs the ,method iterates
     * @return true if validation succeded else false
     * @throws EFapsException on error
     */
    protected boolean validateDoc(final Parameter _parameter,
                                  final DocumentInfo _doc,
                                  final String[] _oids)
        throws EFapsException
    {
        return true;
    }

    /**
     * @param _parameter Parameter as passed from the eFaps API
     * @param _key  key the account is wanted for
     * @return target acccount
     * @throws EFapsException on error
     */
    protected AccountInfo getRoundingAccount(final Parameter _parameter,
                                             final String _key)
        throws EFapsException
    {
        final Instance periodInst = new Period().evaluateCurrentPeriod(_parameter);
        AccountInfo ret = null;
        final Properties props = Accounting.getSysConfig().getObjectAttributeValueAsProperties(periodInst);
        final String name = props.getProperty(_key);
        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.AccountAbstract);
        queryBldr.addWhereAttrEqValue(CIAccounting.AccountAbstract.Name, name);
        queryBldr.addWhereAttrEqValue(CIAccounting.AccountAbstract.PeriodAbstractLink, periodInst);
        final MultiPrintQuery multi = queryBldr.getPrint();
        multi.executeWithoutAccessCheck();
        while (multi.next()) {
            ret = new AccountInfo(multi.getCurrentInstance());
        }
        if (ret == null) {
            Create_Base.LOG.warn("Cannot find Account for: '{}'", _key);
        }
        return ret;
    }

    /**
     * @param _parameter Parameter as passed from the eFaps API
     * @param _doc          Document to add the info
     * @param _instPeriod  period
     * @param _description description
     * @throws EFapsException on error
     */
    protected void createTrans4DocMassive(final Parameter _parameter,
                                          final DocumentInfo _doc,
                                          final Instance _instPeriod,
                                          final String _description)
        throws EFapsException
    {
        _parameter.get(ParameterValues.PROPERTIES);

        final Insert insert = new Insert(CIAccounting.Transaction);
        insert.add(CIAccounting.Transaction.Description, _description);
        insert.add(CIAccounting.Transaction.Date, _doc.getDate());
        insert.add(CIAccounting.Transaction.PeriodLink, _instPeriod);
        insert.add(CIAccounting.Transaction.Status, Status.find(CIAccounting.TransactionStatus.Open));
        insert.execute();

        final Instance transInst = insert.getInstance();
        connectDoc2Transaction(_parameter, transInst, _doc.getInstance());
        for (final AccountInfo account : _doc.getCreditAccounts()) {
            insertPosition4Massiv(_parameter, _doc, transInst, CIAccounting.TransactionPositionCredit, account);
        }
        for (final AccountInfo account : _doc.getDebitAccounts()) {
            insertPosition4Massiv(_parameter, _doc, transInst, CIAccounting.TransactionPositionDebit, account);
        }
    }

    /**
     * @param _parameter Parameter as passed from the eFaps API
     * @param _doc          Document
     * @param _transInst    Transaction Instance
     * @param _type         CITYpe
     * @param _account      TargetAccount
     * @return Instance of the new position
     * @throws EFapsException   on error
     */
    protected Instance insertPosition4Massiv(final Parameter _parameter,
                                             final DocumentInfo _doc,
                                             final Instance _transInst,
                                             final CIType _type,
                                             final AccountInfo _account)
        throws EFapsException
    {
        final boolean isDebitTrans = _type.equals(CIAccounting.TransactionPositionDebit);
        final Instance accInst = _account.getInstance();
        Instance periodInst = _parameter.getCallInstance();
        if (_parameter.getCallInstance().getType().isKindOf(CIAccounting.SubPeriod.getType())) {
            final PrintQuery print = new CachedPrintQuery(_parameter.getCallInstance(), SubPeriod_Base.CACHEKEY);
            final SelectBuilder selPeriodInst = SelectBuilder.get().linkto(CIAccounting.SubPeriod.PeriodLink)
                            .instance();
            print.addSelect(selPeriodInst);
            print.execute();
            periodInst = print.<Instance>getSelect(selPeriodInst);
        }
        final Instance curInstance = new Period().getCurrency(periodInst).getInstance();
        final Insert insert = new Insert(_type);
        insert.add(CIAccounting.TransactionPositionAbstract.TransactionLink, _transInst.getId());
        insert.add(CIAccounting.TransactionPositionAbstract.AccountLink, accInst.getId());
        insert.add(CIAccounting.TransactionPositionAbstract.CurrencyLink, curInstance);
        insert.add(CIAccounting.TransactionPositionAbstract.RateCurrencyLink,
                        _account.getRateInfo().getInstance4Currency());
        insert.add(CIAccounting.TransactionPositionAbstract.Rate,
                        new Object[] {_doc.getRateInfo().getRate(), _account.getRateInfo().getRate() });
        final BigDecimal rateAmount = _account.getAmount();
        insert.add(CIAccounting.TransactionPositionAbstract.RateAmount,
                        isDebitTrans ? rateAmount.negate() :  rateAmount);
        final BigDecimal amount = _account.getAmountRate().setScale(2, BigDecimal.ROUND_HALF_UP);
        insert.add(CIAccounting.TransactionPositionAbstract.Amount,
                        isDebitTrans ? amount.negate() : amount);
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
            BigDecimal amount2 = amount.multiply(new BigDecimal(print.<Integer>getAttribute(
                                            CIAccounting.Account2AccountAbstract.Numerator))
                                 .divide(new BigDecimal(print.<Integer>getAttribute(
                                                 CIAccounting.Account2AccountAbstract.Denominator)),
                                                            BigDecimal.ROUND_HALF_UP));
            BigDecimal rateAmount2 = rateAmount.multiply(new BigDecimal(print.<Integer>getAttribute(
                                            CIAccounting.Account2AccountAbstract.Numerator))
                                  .divide(new BigDecimal(print.<Integer>getAttribute(
                                                  CIAccounting.Account2AccountAbstract.Denominator)),
                                            BigDecimal.ROUND_HALF_UP));
            if (instance.getType().getUUID().equals(CIAccounting.Account2AccountCosting.uuid)) {
                add = true;
                insert3 = new Insert(_type);
            } else if (instance.getType().getUUID().equals(CIAccounting.Account2AccountCostingInverse.uuid)) {
                if (_type.equals(CIAccounting.TransactionPositionDebit)) {
                    insert3 = new Insert(CIAccounting.TransactionPositionCredit);
                } else {
                    insert3 = new Insert(CIAccounting.TransactionPositionDebit);
                }
                amount2 = amount2.negate();
                rateAmount2 = rateAmount2.negate();
                add = true;
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
                insert3.add(CIAccounting.TransactionPositionAbstract.TransactionLink, _transInst.getId());
                insert3.add(CIAccounting.TransactionPositionAbstract.AccountLink,
                                print.getAttribute(CIAccounting.Account2AccountAbstract.ToAccountLink));
                insert3.add(CIAccounting.TransactionPositionAbstract.CurrencyLink, curInstance);
                insert3.add(CIAccounting.TransactionPositionAbstract.RateCurrencyLink,
                                _account.getRateInfo().getInstance4Currency());
                insert3.add(CIAccounting.TransactionPositionAbstract.Rate,
                                new Object[] {_doc.getRateInfo().getRate(), _account.getRateInfo().getRate() });
                insert3.add(CIAccounting.TransactionPositionAbstract.Amount, amount2);
                insert3.add(CIAccounting.TransactionPositionAbstract.RateAmount, rateAmount2);
                insert3.execute();
            }
        }
        return insert.getInstance();
    }
}
