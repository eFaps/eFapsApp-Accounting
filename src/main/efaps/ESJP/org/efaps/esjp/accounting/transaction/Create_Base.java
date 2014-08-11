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

import java.io.File;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.collections4.CollectionUtils;
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
import org.efaps.esjp.accounting.transaction.TransInfo_Base.PositionInfo;
import org.efaps.esjp.accounting.util.Accounting;
import org.efaps.esjp.accounting.util.AccountingSettings;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.ci.CIERP;
import org.efaps.esjp.ci.CIFormAccounting;
import org.efaps.esjp.ci.CISales;
import org.efaps.esjp.common.parameter.ParameterUtil;
import org.efaps.esjp.erp.CurrencyInst;
import org.efaps.esjp.erp.NumberFormatter;
import org.efaps.esjp.erp.RateInfo;
import org.efaps.util.DateTimeUtil;
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
        final Return ret = new Return();
        final Instance instance = createFromUI(_parameter);
        connect2SubJournal(_parameter, instance, null);
        final Parameter parameter = ParameterUtil.clone(_parameter, Parameter.ParameterValues.INSTANCE, instance);

        final File file = getTransactionReport(parameter, true);
        if (file != null) {
            ret.put(ReturnValues.VALUES, file);
            ret.put(ReturnValues.TRUE, true);
        }
        return ret;
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
        properties.get("TypePostfix");

        final Instance periodInst = new Period().evaluateCurrentPeriod(_parameter);
        final Insert insert = new Insert(CIAccounting.Transaction);
        insert.add(CIAccounting.Transaction.Name, _parameter.getParameterValue("name"));
        insert.add(CIAccounting.Transaction.Description, _parameter.getParameterValue("description"));
        insert.add(CIAccounting.Transaction.Date, _parameter.getParameterValue("date"));
        insert.add(CIAccounting.Transaction.PeriodLink, periodInst.getId());
        insert.add(CIAccounting.Transaction.Status, Status.find(CIAccounting.TransactionStatus.Open));
        insert.add(CIAccounting.Transaction.Identifier, Transaction.IDENTTEMP);
        insert.execute();
//        final Instance instance = insert.getInstance();
//        final int pos = insertPositions(_parameter, instance, postfix, new String[] { _parameter.getCallInstance()
//                        .getOid() }, 0);
//        insertPositions(_parameter, instance, "Debit".equals(postfix) ? "Credit" : "Debit", null, pos);
//        new org.efaps.esjp.common.uiform.Create().insertClassification(_parameter, instance);
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
        final Return ret = new Return();
        final Instance transInst = createFromUI(_parameter);
        connect2SubJournal(_parameter, transInst, null);
        final List<Instance> docInsts = getDocInstsFromUI(_parameter);
        connectDocs2Transaction(_parameter, transInst, docInsts.toArray(new Instance[docInsts.size()]));
        setStatus4Docs(_parameter, docInsts.toArray(new Instance[docInsts.size()]));
        add2Create4Doc(_parameter);
        final Parameter parameter = ParameterUtil.clone(_parameter, Parameter.ParameterValues.INSTANCE, transInst);
        final File file = getTransactionReport(parameter, true);
        if (file != null) {
            ret.put(ReturnValues.VALUES, file);
            ret.put(ReturnValues.TRUE, true);
        }
        return ret;
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
        final Return ret = new Return();
        final List<Instance> docInsts = getDocInstsFromUI(_parameter);
        final Instance transInst = createFromUI(_parameter);
        connect2SubJournal(_parameter, transInst, null);
        connectDocs2Transaction(_parameter, transInst, docInsts.toArray(new Instance[docInsts.size()]));
        connectDocs2PurchaseRecord(_parameter, docInsts.toArray(new Instance[docInsts.size()]));
        setStatus4Docs(_parameter, docInsts.toArray(new Instance[docInsts.size()]));

        final Parameter parameter = ParameterUtil.clone(_parameter, Parameter.ParameterValues.INSTANCE, transInst);
        final File file = getTransactionReport(parameter, true);
        if (file != null) {
            ret.put(ReturnValues.VALUES, file);
            ret.put(ReturnValues.TRUE, true);
        }
        return ret;
    }

    public Return create4ExternalMassive(final Parameter _parameter)
        throws EFapsException
    {
        final List<DocumentInfo> docInfos = evalDocuments(_parameter);
        final DateTime date = new DateTime(_parameter.getParameterValue("date"));
        final boolean useDate = Boolean.parseBoolean(_parameter.getParameterValue("useDate"));
        final boolean oneTransPerDoc = Boolean.parseBoolean(_parameter.getParameterValue("oneTransPerDoc"));

        if (!oneTransPerDoc) {
            final DocumentInfo docInfo = DocumentInfo.getCombined(docInfos, summarizeTransaction(_parameter));
            docInfo.setDate(date);
            if (docInfo.isValid(_parameter)) {
                final TransInfo transinfo = TransInfo.get4DocInfo(_parameter, docInfo, false);
                if (useDate) {
                    transinfo.setDate(date);
                }
                transinfo.create(_parameter);
                final List<Instance> docInsts = getDocInstsFromDocInfoList(_parameter, docInfos);
                connectDocs2Transaction(_parameter, transinfo.getInstance(),
                                docInsts.toArray(new Instance[docInsts.size()]));
                connectDocs2PurchaseRecord(_parameter, docInsts.toArray(new Instance[docInsts.size()]));
                setStatus4Docs(_parameter, docInsts.toArray(new Instance[docInsts.size()]));
                connect2SubJournal(_parameter, transinfo.getInstance(), null);
            }
        } else {
            for (final DocumentInfo docInfo : docInfos) {
                if (docInfo.isValid(_parameter)) {
                    final TransInfo transinfo = TransInfo.get4DocInfo(_parameter, docInfo, true);
                    if (useDate) {
                        transinfo.setDate(date);
                    }
                    transinfo.create(_parameter);
                    connectDocs2Transaction(_parameter, transinfo.getInstance(), docInfo.getInstance());
                    connectDocs2PurchaseRecord(_parameter, docInfo.getInstance());
                    setStatus4Docs(_parameter, docInfo.getInstance());
                    connect2SubJournal(_parameter, transinfo.getInstance(), docInfo.getInstance());
                }
            }
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
    public Return create4IncomingExchange(final Parameter _parameter)
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
        final Return ret = new Return();
        final Instance transInst = createFromUI(_parameter);
        final List<Instance> docInsts = getDocInstsFromUI(_parameter);
        setStatus4Payments(_parameter, docInsts.toArray(new Instance[docInsts.size()]));
        for (final Instance docInst  : docInsts) {
            connect2SubJournal(_parameter, transInst, docInst);
        }
        // evaluate for the documents the payment belongs to
        final List<DocumentInfo> docInfos = evalDocuments(_parameter);
        for (final DocumentInfo docInfo : docInfos) {
            CollectionUtils.addAll(docInsts, docInfo.getDocInsts(false));
        }
        connectDocs2Transaction(_parameter, transInst, docInsts.toArray(new Instance[docInsts.size()]));


        final Parameter parameter = ParameterUtil.clone(_parameter, Parameter.ParameterValues.INSTANCE, transInst);
        final File file = getTransactionReport(parameter, true);
        if (file != null) {
            ret.put(ReturnValues.VALUES, file);
            ret.put(ReturnValues.TRUE, true);
        }
        return ret;
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
        final List<DocumentInfo> docInfos = evalDocuments(_parameter);
        for (final DocumentInfo docInfo : docInfos) {
            if (docInfo.isValid(_parameter)) {
                final TransInfo transinfo = TransInfo.get4DocInfo(_parameter, docInfo, true);
                if (usedate) {
                    transinfo.setDate(date);
                }
                transinfo.create(_parameter);
                connectDocs2Transaction(_parameter, transinfo.getInstance(), docInfo.getDocInsts(true));
                setStatus4Payments(_parameter, docInfo.getInstance());
                connect2SubJournal(_parameter, transinfo.getInstance(), docInfo.getInstance());
            }
        }
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
            if (doc.isCostValidated() && doc.getDifference(_parameter).compareTo(BigDecimal.ZERO) == 0
                            && doc.getAmount().compareTo(BigDecimal.ZERO) != 0
                            && doc.getCreditSum(_parameter).compareTo(doc.getAmount()) == 0
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
                connectDocs2Transaction(_parameter, transInst, docInst);
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
                doc.setFormater(NumberFormatter.get().getTwoDigitsFormatter());
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
                    final BigDecimal diffMin = diffMinStr != null && !diffMinStr.isEmpty()
                                    ? new BigDecimal(diffMinStr) : BigDecimal.ZERO;
                    if ((checkCrossTotal.compareTo(debit) != 0 || checkCrossTotal.compareTo(credit) != 0)
                                    && debit.subtract(credit).abs().compareTo(diffMin) < 0) {
                        final AccountInfo acc = doc.getDebitAccounts().iterator().next();
                        if (checkCrossTotal.compareTo(debit) > 0) {
                            final AccountInfo account = getRoundingAccount(_parameter,
                                            AccountingSettings.PERIOD_ROUNDINGDEBIT);
                            account.setAmount(checkCrossTotal.subtract(debit));
                            account.setRateInfo(acc.getRateInfo(), instDoc.getType().getName());
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
                            account.setRateInfo(acc.getRateInfo(), instDoc.getType().getName());
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
                    setStatus4Docs(_parameter, doc.getInstance());
                    connectDocs2PurchaseRecord(_parameter, doc.getInstance());
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
    public void connectDocs2Transaction(final Parameter _parameter,
                                        final Instance _transInst,
                                        final Instance... _docInstances)
        throws EFapsException
    {
        int i = 1;
        for (final Instance docInst : _docInstances) {
            if (docInst.isValid()) {
                final Insert insert;
                if (docInst.getType().isKindOf(CIERP.PaymentDocumentAbstract.getType())) {
                    insert = new Insert(CIAccounting.Transaction2PaymentDocument);
                } else {
                    insert = new Insert(CIAccounting.Transaction2SalesDocument);
                }
                insert.add(CIAccounting.Transaction2ERPDocument.Position, i);
                insert.add(CIAccounting.Transaction2ERPDocument.FromLink, _transInst);
                insert.add(CIAccounting.Transaction2ERPDocument.ToLinkAbstract, docInst);
                insert.execute();
                i++;
            }
        }
    }

    /**
     * Get the list of documents.
     *
     * @param _parameter Parameter as passed from the eFaps API
     * @return List of document instances
     * @throws EFapsException on error
     */
    protected List<Instance> getDocInstsFromUI(final Parameter _parameter)
        throws EFapsException
    {
        final String[] docOids = _parameter.getParameterValues("document");
        final List<Instance> ret = new ArrayList<>();
        for (final String docOid : docOids) {
            final Instance docInst = Instance.get(docOid);
            if (docInst.isValid()) {
                ret.add(docInst);
            }
        }
        return ret;
    }


    /**
     * Get the list of documents.
     *
     * @param _parameter Parameter as passed from the eFaps API
     * @return List of document instances
     * @throws EFapsException on error
     */
    protected List<Instance> getDocInstsFromDocInfoList(final Parameter _parameter,
                                                        final List<DocumentInfo> _docInfos)
        throws EFapsException
    {

        final List<Instance> ret = new ArrayList<>();
        for (final DocumentInfo docInfo : _docInfos) {
            ret.add(docInfo.getInstance());
        }
        return ret;
    }

    /**
     * Set the Status to booked for a PaymentDocument.
     * @param _parameter    Parameter as passe by the eFaps API
     * @param _payDocInst   Instance of the PaymentDocument the Status willl be set
     * @throws EFapsException on error
     */
    protected void setStatus4Payments(final Parameter _parameter,
                                      final Instance... _payDocInst)
        throws EFapsException
    {
        for (final Instance instance : _payDocInst) {
            // update the status
            final Status status = Status.find(instance.getType().getStatusAttribute().getLink().getUUID(), "Booked");
            if (status != null) {
                final Update update = new Update(instance);
                update.add(CIERP.PaymentDocumentAbstract.StatusAbstract, status.getId());
                update.execute();
            }
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
    protected Instance createFromUI(final Parameter _parameter)
        throws EFapsException
    {
        final Instance periodInst = new Period().evaluateCurrentPeriod(_parameter);

        final TransInfo transInfo = new TransInfo();
        transInfo.setType(CIAccounting.Transaction.getType())
            .setName(_parameter.getParameterValue("name"))
            .setDescription(_parameter.getParameterValue("description"))
            .setDate(DateTimeUtil.translateFromUI(_parameter.getParameterValue("date")))
            .setStatus(Status.find(CIAccounting.TransactionStatus.Open))
            .setIdentifier(Transaction.IDENTTEMP)
            .setPeriodInst(periodInst);

        analysePositionsFromUI(_parameter, transInfo, "Debit", null);
        analysePositionsFromUI(_parameter, transInfo, "Credit", null);

        transInfo.create(_parameter);

        return transInfo.getInstance();
    }

    /**
     * Connect the document to the Purchaserecord.
     * @param _parameter Parameter as passed by the eFaps API
     * @param _instance instance of the document
     * @throws EFapsException   on error
     */
    protected void connectDocs2PurchaseRecord(final Parameter _parameter,
                                              final Instance... _docInsts)
        throws EFapsException
    {
        final Instance purchaseRecInst = Instance.get(_parameter.getParameterValue("purchaseRecord"));
        if (purchaseRecInst.isValid()) {
            for (final Instance docInst : _docInsts) {
                final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.PurchaseRecord2Document);
                queryBldr.addWhereAttrEqValue(CIAccounting.PurchaseRecord2Document.ToLink, docInst);
                final InstanceQuery query = queryBldr.getQuery();
                if (query.executeWithoutAccessCheck().isEmpty()) {
                    final Insert purInsert = new Insert(CIAccounting.PurchaseRecord2Document);
                    purInsert.add(CIAccounting.PurchaseRecord2Document.FromLink, purchaseRecInst);
                    purInsert.add(CIAccounting.PurchaseRecord2Document.ToLink, docInst);
                    purInsert.execute();
                }
            }
        }
    }

    /**
     * Set the status to booked if  marked in the Form. Set Status to Open if in status Digitized.
     * @param _parameter    Parameter as passed by the eFasp API
     * @param _instance     instance of the document
     * @throws EFapsException on error
     */
    protected void setStatus4Docs(final Parameter _parameter,
                                  final Instance... _instance)
        throws EFapsException
    {
        final boolean setStatus = "true".equals(_parameter.getParameterValue("docStatus"));
        for (final Instance instance : _instance) {
            if (setStatus) {
                final Update update = new Update(instance);
                update.add(CIERP.DocumentAbstract.StatusAbstract,
                                Status.find(instance.getType().getStatusAttribute().getLink().getUUID(), "Booked"));
                update.execute();
            } else {
                final PrintQuery print = new PrintQuery(instance);
                print.addAttribute(CIERP.DocumentAbstract.StatusAbstract);
                print.executeWithoutAccessCheck();
                final Status status = Status.get(print.<Long>getAttribute(CIERP.DocumentAbstract.StatusAbstract));
                if ("Digitized".equals(status.getKey())) {
                    final Status newStatus = Status.find(status.getStatusGroup().getUUID(), "Open");
                    if (newStatus != null) {
                        final Update update = new Update(instance);
                        update.add(CIERP.DocumentAbstract.StatusAbstract, newStatus);
                        update.execute();
                    }
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
    protected void connect2SubJournal(final Parameter _parameter,
                                      final Instance _transactionInstance,
                                      final Instance _docInstance)
        throws EFapsException
    {
        Instance repInst = Instance.get(_parameter.getParameterValue("subJournal"));
        if (!repInst.isValid() && _docInstance != null && _docInstance.isValid()
                        && _docInstance.getType().isKindOf(CIERP.PaymentDocumentAbstract.getType()))  {
            final Instance periodInst = new Period().evaluateCurrentPeriod(_parameter, _transactionInstance);
            final Properties props = Accounting.getSysConfig().getObjectAttributeValueAsProperties(periodInst);
            String journalname = "NONE";
            if (_docInstance.getType().isKindOf(CISales.PaymentDocumentOutAbstract.getType())) {
                journalname = props.getProperty( AccountingSettings.PERIOD_SUBJOURNAL4PAYOUT, "NONE");
            } else if (_docInstance.getType().isKindOf(CISales.PaymentDocumentAbstract.getType())) {
                journalname = props.getProperty( AccountingSettings.PERIOD_SUBJOURNAL4PAYIN, "NONE");
            }
            final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.ReportSubJournal);
            queryBldr.addWhereAttrEqValue(CIAccounting.ReportSubJournal.PeriodLink, periodInst);
            queryBldr.addWhereAttrEqValue(CIAccounting.ReportSubJournal.Name, journalname);
            final InstanceQuery query = queryBldr.getQuery();
            query.execute();
            if (query.next()) {
                repInst = query.getCurrentValue();
            }
        }
        if (repInst.isValid()) {
            final Insert insert = new Insert(CIAccounting.ReportSubJournal2Transaction);
            insert.add(CIAccounting.ReportSubJournal2Transaction.FromLink, repInst.getId());
            insert.add(CIAccounting.ReportSubJournal2Transaction.ToLink, _transactionInstance.getId());
            insert.execute();
        }
    }


    public void analysePositionsFromUI(final Parameter _parameter,
                                       final TransInfo _transInfo,
                                       final String _postFix,
                                       final String[] _accountOids)
        throws EFapsException
    {

        final String[] accountOids = _accountOids == null
                        ? _parameter.getParameterValues("accountLink_" + _postFix) : _accountOids;
        final String[] amounts = _parameter.getParameterValues("amount_" + _postFix);
        final String[] types = _parameter.getParameterValues("type_" + _postFix);
        final String[] rateCurIds = _parameter.getParameterValues("rateCurrencyLink_" + _postFix);
        final String[] acc2accOids = _parameter.getParameterValues("acc2acc_" + _postFix);
        final String[] labelLinkOids = _parameter.getParameterValues("labelLink_" + _postFix);
        final String[] docLinkOids = _parameter.getParameterValues("docLink_" + _postFix);
        final String[] remarks = _parameter.getParameterValues("remark_" + _postFix);
        final DecimalFormat formater = NumberFormatter.get().getFormatter();
        try {
            Instance inst = _parameter.getCallInstance();
            if (!inst.getType().isKindOf(CIAccounting.Period.getType())) {
                inst = new Period().evaluateCurrentPeriod(_parameter);
            }
            final Instance curInstance = new Period().getCurrency(inst).getInstance();
            if (amounts != null) {
                for (int i = 0; i < amounts.length; i++) {
                    final Instance rateCurrInst = CurrencyInst.get(Long.parseLong(rateCurIds[i])).getInstance();
                    final Instance accInst = Instance.get(accountOids[i]);
                    final Object[] rateObj = new Transaction().getRateObject(_parameter, "_" + _postFix, i);
                    final BigDecimal rate = ((BigDecimal) rateObj[0]).divide((BigDecimal) rateObj[1], 12,
                                    BigDecimal.ROUND_HALF_UP);
                    final Type type = Type.get(Long.parseLong(types[i]));

                    final BigDecimal rateAmount = ((BigDecimal) formater.parse(amounts[i]))
                                    .setScale(6, BigDecimal.ROUND_HALF_UP);
                    final boolean isDebitTrans = type.getUUID().equals(CIAccounting.TransactionPositionDebit.uuid);
                    final BigDecimal amount = rateAmount.divide(rate, 12, BigDecimal.ROUND_HALF_UP);

                    final PositionInfo pos  = new PositionInfo();
                    _transInfo.addPosition(pos);
                    pos.setType(type)
                        .setAccInst(accInst)
                        .setCurrInst(curInstance)
                        .setRateCurrInst(rateCurrInst)
                        .setRate(rateObj)
                        .setRateAmount( isDebitTrans ? rateAmount.negate() : rateAmount)
                        .setAmount( isDebitTrans ? amount.negate() : amount)
                        .setOrder(i)
                        .setRemark(remarks == null ? null : remarks[i]);;

                    if (labelLinkOids != null) {
                        final Instance labelInst = Instance.get(labelLinkOids[i]);
                        if (labelInst.isValid()) {
                            pos.setLabelInst(labelInst)
                                .setLabelRelType(_postFix.equalsIgnoreCase("Debit")
                                                ? CIAccounting.TransactionPositionDebit2LabelProject.getType()
                                                : CIAccounting.TransactionPositionCredit2LabelProject.getType());
                        }
                    }

                    if (docLinkOids != null) {
                        final Instance docInst = Instance.get(docLinkOids[i]);
                        if (docInst.isValid()) {
                            final DocumentInfo docInfoTmp = new DocumentInfo(docInst);
                            if (docInfoTmp.isSumsDoc()) {
                                pos.setDocInst(docInst)
                                    .setDocRelType(_postFix.equalsIgnoreCase("Debit")
                                                ? CIAccounting.TransactionPositionDebit2SalesDocument.getType()
                                                : CIAccounting.TransactionPositionCredit2SalesDocument.getType());
                            } else {
                                pos.setDocInst(docInst)
                                .setDocRelType(_postFix.equalsIgnoreCase("Debit")
                                            ? CIAccounting.TransactionPositionDebit2PaymentDocument.getType()
                                            : CIAccounting.TransactionPositionCredit2PaymentDocument.getType());
                            }
                        }
                    }

                    final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.Account2AccountAbstract);
                    queryBldr.addWhereAttrEqValue(CIAccounting.Account2AccountAbstract.FromAccountLink, accInst);
                    final MultiPrintQuery multi = queryBldr.getPrint();
                    final SelectBuilder selAcc = SelectBuilder.get()
                                    .linkto(CIAccounting.Account2AccountAbstract.ToAccountLink).instance();
                    multi.addSelect(selAcc);
                    multi.addAttribute(CIAccounting.Account2AccountAbstract.Numerator,
                                      CIAccounting.Account2AccountAbstract.Denominator,
                                      CIAccounting.Account2AccountAbstract.Deactivatable);
                    multi.execute();
                    int y = 1;
                    final int group = _transInfo.getNextGroup();
                    while (multi.next()) {
                        final Instance instance = multi.getCurrentInstance();
                        final PositionInfo connPos  = new PositionInfo();
                        Boolean deactivatable = multi
                                        .<Boolean>getAttribute(CIAccounting.Account2AccountAbstract.Deactivatable);
                        if (deactivatable == null) {
                            deactivatable = false;
                        }
                        // if cannot be deactivated or selected in the UserInterface
                        if (!deactivatable || acc2accOids != null && deactivatable
                                        &&  Arrays.asList(acc2accOids).contains(instance.getOid())) {
                            final BigDecimal numerator = new BigDecimal(multi.<Integer>getAttribute(
                                            CIAccounting.Account2AccountAbstract.Numerator));
                            final BigDecimal denominator = new BigDecimal(multi.<Integer>getAttribute(
                                            CIAccounting.Account2AccountAbstract.Denominator));

                            BigDecimal amount2 = amount.multiply(numerator).divide(denominator,
                                            BigDecimal.ROUND_HALF_UP);
                            BigDecimal rateAmount2 = rateAmount.multiply(numerator).divide(denominator,
                                            BigDecimal.ROUND_HALF_UP);

                            if (instance.getType().getUUID().equals(CIAccounting.Account2AccountCosting.uuid)) {
                                connPos.setType(type);
                            } else if (instance.getType().getUUID()
                                            .equals(CIAccounting.Account2AccountCostingInverse.uuid)) {
                                if (type.getUUID().equals(CIAccounting.TransactionPositionDebit.uuid)) {
                                    connPos.setType(CIAccounting.TransactionPositionCredit.getType());
                                } else {
                                    connPos.setType(CIAccounting.TransactionPositionDebit.getType());
                                }
                                amount2 = amount2.negate();
                            } else if (instance.getType().getUUID().equals(CIAccounting.Account2AccountCredit.uuid)) {
                                if (isDebitTrans) {
                                    connPos.setType(CIAccounting.TransactionPositionCredit.getType());
                                } else {
                                    connPos.setType(CIAccounting.TransactionPositionDebit.getType());
                                    amount2 = amount2.negate();
                                    rateAmount2 = rateAmount2.negate();
                                }
                            } else if (instance.getType().getUUID().equals(CIAccounting.Account2AccountDebit.uuid)) {
                                if (isDebitTrans) {
                                    connPos.setType(CIAccounting.TransactionPositionDebit.getType());
                                    amount2 = amount2.negate();
                                    rateAmount2 = rateAmount2.negate();
                                } else {
                                    connPos.setType(CIAccounting.TransactionPositionCredit.getType());
                                }
                            }
                            if (connPos.getType() == null) {
                                Create_Base.LOG.error("Missing definition");
                            } else {
                                connPos.setOrder(i)
                                    .setConnOrder(y)
                                    .setGroupId(group)
                                    .setAccInst( multi.<Instance>getSelect(selAcc))
                                    .setCurrInst(curInstance)
                                    .setRateCurrInst(rateCurrInst)
                                    .setRate(rateObj)
                                    .setAmount(amount2)
                                    .setRateAmount(rateAmount2);
                                _transInfo.addPosition(connPos);
                            }
                            y++;
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
        final DecimalFormat formater = NumberFormatter.get().getFormatter(null, 2);
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
        connectDocs2Transaction(_parameter, transInst, _doc.getInstance());
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
        final BigDecimal amount = _account.getAmountRate(_parameter).setScale(2, BigDecimal.ROUND_HALF_UP);
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
