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
import org.efaps.db.AttributeQuery;
import org.efaps.db.CachedPrintQuery;
import org.efaps.db.Context;
import org.efaps.db.Insert;
import org.efaps.db.Instance;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.PrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.db.Update;
import org.efaps.esjp.accounting.Periode;
import org.efaps.esjp.accounting.SubPeriod_Base;
import org.efaps.esjp.accounting.transaction.Transaction_Base.Document;
import org.efaps.esjp.accounting.transaction.Transaction_Base.TargetAccount;
import org.efaps.esjp.accounting.util.Accounting;
import org.efaps.esjp.accounting.util.AccountingSettings;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.ci.CIERP;
import org.efaps.esjp.ci.CIFormAccounting;
import org.efaps.esjp.ci.CISales;
import org.efaps.esjp.common.uiform.Create;
import org.efaps.esjp.erp.CurrencyInst;
import org.efaps.esjp.erp.RateInfo;
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
    extends Create
{
    /**
     * Logger for this class.
     */
    private static final Logger LOG = LoggerFactory.getLogger(Create_Base.class);

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
        final Instance periodeInst = _parameter.getCallInstance();
        final DateTime date = new DateTime(_parameter
                        .getParameterValue(CIFormAccounting.Accounting_TransactionCreate4PaymentForm.date.name));
        final boolean useDateForm = Boolean.parseBoolean(_parameter
                        .getParameterValue(CIFormAccounting.Accounting_TransactionCreate4PaymentForm.useDate.name));
        final String[] oidsPay = (String[]) Context.getThreadContext()
                        .getSessionAttribute(CIFormAccounting.Accounting_TransactionCreate4PaymentForm.document.name);
        final CurrencyInst curInstance = new Periode().getCurrency(periodeInst);
        for (final String oid : oidsPay) {
            final Instance payDocInst = Instance.get(oid);
            if (payDocInst.isValid()) {
                final QueryBuilder queryBldr = new QueryBuilder(CIERP.Document2PaymentDocumentAbstract);
                queryBldr.addWhereAttrEqValue(CIERP.Document2PaymentDocumentAbstract.ToAbstractLink,
                                payDocInst.getId());
                final MultiPrintQuery multi = queryBldr.getPrint();
                final SelectBuilder sel = new SelectBuilder().linkto(
                                CIERP.Document2PaymentDocumentAbstract.FromAbstractLink)
                                .oid();
                final SelectBuilder selCur = new SelectBuilder().linkto(
                                CIERP.Document2PaymentDocumentAbstract.CurrencyLink)
                                .oid();
                multi.addSelect(sel, selCur);
                multi.addAttribute(CIERP.Document2PaymentDocumentAbstract.Amount,
                                CIERP.Document2PaymentDocumentAbstract.Date);
                multi.execute();
                while (multi.next()) {
                    final Instance docInst = Instance.get(multi.<String>getSelect(sel));
                    final Instance rateCurInst = Instance.get(multi.<String>getSelect(selCur));
                    final BigDecimal amount = multi
                                    .<BigDecimal>getAttribute(CIERP.Document2PaymentDocumentAbstract.Amount);
                    final DateTime d2payDate = multi
                                    .<DateTime>getAttribute(CIERP.Document2PaymentDocumentAbstract.Date);

                    final boolean incoming = payDocInst.getType().isKindOf(CISales.PaymentDocumentAbstract.getType());

                    final Instance salesAccInst = getSalesAcccountInst4Payment(_parameter, multi.getCurrentInstance());
                    final Instance targetAccInst = getTargetAcccountInst4Payment(_parameter, salesAccInst);

                    final Instance sourceAccInst = getSourceAcccountInst4Payment(_parameter, docInst, incoming);
                    final DateTime dateTmp = useDateForm ? date : d2payDate;

                    final Insert insert = new Insert(CIAccounting.Transaction);
                    insert.add(CIAccounting.Transaction.PeriodeLink, periodeInst);
                    insert.add(CIAccounting.Transaction.Date, dateTmp);
                    insert.add(CIAccounting.Transaction.Description,
                                    getDescription4Payment(_parameter, multi.getCurrentInstance()));
                    insert.add(CIAccounting.Transaction.Status, Status.find(CIAccounting.TransactionStatus.Open));
                    insert.execute();

                    createDocClass(_parameter, insert.getInstance(), docInst);
                    createPaymentClass(_parameter, insert.getInstance(), payDocInst);

                    final RateInfo rate = new Transaction().evaluateRate(_parameter, periodeInst, dateTmp, rateCurInst);

                    final Object[] rates = rate.getRateObject();
                    final BigDecimal rateAmount = amount.setScale(12, BigDecimal.ROUND_HALF_UP)
                                    .divide(rate.getRate(), 12, BigDecimal.ROUND_HALF_UP);

                    final Insert posInsert = new Insert(CIAccounting.TransactionPositionDebit);
                    posInsert.add(CIAccounting.TransactionPositionAbstract.TransactionLink, insert.getId());
                    posInsert.add(CIAccounting.TransactionPositionAbstract.AccountLink,
                                    incoming ? targetAccInst.getId() : sourceAccInst.getId());
                    posInsert.add(CIAccounting.TransactionPositionAbstract.CurrencyLink, curInstance.getInstance());
                    posInsert.add(CIAccounting.TransactionPositionAbstract.RateCurrencyLink, rateCurInst);
                    posInsert.add(CIAccounting.TransactionPositionAbstract.Rate, rates);
                    posInsert.add(CIAccounting.TransactionPositionAbstract.RateAmount, amount.negate());
                    posInsert.add(CIAccounting.TransactionPositionAbstract.Amount, rateAmount.negate());
                    posInsert.execute();

                    final Insert posInsert2 = new Insert(CIAccounting.TransactionPositionCredit);
                    posInsert2.add(CIAccounting.TransactionPositionAbstract.TransactionLink, insert.getId());
                    posInsert2.add(CIAccounting.TransactionPositionAbstract.AccountLink,
                                    incoming ? sourceAccInst.getId() : targetAccInst.getId());
                    posInsert2.add(CIAccounting.TransactionPositionAbstract.CurrencyLink, curInstance.getInstance());
                    posInsert2.add(CIAccounting.TransactionPositionAbstract.RateCurrencyLink, rateCurInst);
                    posInsert2.add(CIAccounting.TransactionPositionAbstract.Rate, rates);
                    posInsert2.add(CIAccounting.TransactionPositionAbstract.RateAmount, amount);
                    posInsert2.add(CIAccounting.TransactionPositionAbstract.Amount, rateAmount);
                    posInsert2.execute();
                }
                setStatus4Payment(_parameter, payDocInst);
            }
        }
        return new Return();
    }

    /**
     * Get the description for the Payment Transaction.
     *
     * @param _parameter        Parameter as passe by the eFaps API
     * @param _doc2payDocInst   Instance
     * @return label
     * @throws EFapsException on erro
     */
    protected String getDescription4Payment(final Parameter _parameter,
                                            final Instance _doc2payDocInst)
        throws EFapsException
    {
        final PrintQuery print = new PrintQuery(_doc2payDocInst);
        final SelectBuilder selPayDocType = new SelectBuilder()
                        .linkto(CIERP.Document2PaymentDocumentAbstract.ToAbstractLink)
                        .type().label();
        final SelectBuilder selPayDocName = new SelectBuilder().linkto(
                        CIERP.Document2PaymentDocumentAbstract.ToAbstractLink)
                        .attribute(CIERP.PaymentDocumentAbstract.Name);
        final SelectBuilder selDocType = new SelectBuilder()
                        .linkto(CIERP.Document2PaymentDocumentAbstract.FromAbstractLink)
                        .type().label();
        final SelectBuilder selDocName = new SelectBuilder().linkto(
                        CIERP.Document2PaymentDocumentAbstract.FromAbstractLink)
                        .attribute(CIERP.PaymentDocumentAbstract.Name);
        print.addSelect(selPayDocType, selPayDocName, selDocType, selDocName);
        print.execute();

        final String payDocType = print.<String>getSelect(selPayDocType);
        final String payDocName = print.<String>getSelect(selPayDocName);
        final String docType = print.<String>getSelect(selDocType);
        final String docName = print.<String>getSelect(selDocName);

        return payDocType + " " + payDocName + " - " + docType + " " + docName;
    }


    /**
     * Set the Status to booked for a PaymentDocument
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
     * Get the Target account for a Transaction with PaymentDocument.
     * @param _parameter        Parameter as passe by the eFaps API
     * @param _salesAccInst     Instance of the Account from sales the account is searched for
     * @return Instance of the target account
     * @throws EFapsException on error
     */
    protected Instance getTargetAcccountInst4Payment(final Parameter _parameter,
                                                     final Instance _salesAccInst)
        throws EFapsException
    {
        Instance ret = Instance.get("");
        if (_salesAccInst.isValid()) {
            final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.Periode2Account);
            queryBldr.addWhereAttrEqValue(CIAccounting.Periode2Account.SalesAccountLink, _salesAccInst.getId());
            final MultiPrintQuery multi = queryBldr.getPrint();
            final SelectBuilder accSel = new SelectBuilder().linkto(CIAccounting.Periode2Account.FromLink)
                            .oid();
            multi.addSelect(accSel);
            multi.execute();
            while (multi.next()) {
                ret = Instance.get(multi.<String>getSelect(accSel));
            }
        }
        return ret;
    }

    /**
     * Get the Sales account for a Transaction with PaymentDocument.
     * @param _parameter        Parameter as passe by the eFaps API
     * @param _doc2payDocInst   Instance of the PaymentDocument
     * @return Instance of the target account
     * @throws EFapsException on error
     */
    protected Instance getSalesAcccountInst4Payment(final Parameter _parameter,
                                                    final Instance _doc2payDocInst)
        throws EFapsException
    {
        Instance ret = Instance.get("");
        if (_doc2payDocInst.isValid()) {
            final QueryBuilder queryBldr = new QueryBuilder(CISales.TransactionAbstract);
            queryBldr.addWhereAttrEqValue(CISales.TransactionAbstract.Payment, _doc2payDocInst.getId());
            final MultiPrintQuery multi = queryBldr.getPrint();
            final SelectBuilder accSel = new SelectBuilder().linkto(CISales.TransactionAbstract.Account)
                            .oid();
            multi.addSelect(accSel);
            multi.execute();

            while (multi.next()) {
                ret = Instance.get(multi.<String>getSelect(accSel));
            }
        }
        return ret;
    }

    /**
     * Get the Source account for a Transaction with PaymentDocument.
     * @param _parameter        Parameter as passe by the eFaps API
     * @param _docInst          Instance of the Document (Invoice etc.)
     * @param _incoming         is it an incoming or outgoing payment
     * @return Instance of the target account
     * @throws EFapsException on error
     */
    protected Instance getSourceAcccountInst4Payment(final Parameter _parameter,
                                                     final Instance _docInst,
                                                     final boolean _incoming)
        throws EFapsException
    {
        Instance ret = Instance.get("");
        if (_docInst.isValid()) {

            final QueryBuilder attrQueryBldr = new QueryBuilder(CIAccounting.TransactionClassDocument);
            attrQueryBldr.addWhereAttrEqValue(CIAccounting.TransactionClassDocument.DocumentLink, _docInst.getId());
            final AttributeQuery attrQuery = attrQueryBldr
                            .getAttributeQuery(CIAccounting.TransactionClassDocument.TransactionLink);
            final QueryBuilder posQueryBldr = new QueryBuilder(_incoming ? CIAccounting.TransactionPositionDebit
                            : CIAccounting.TransactionPositionCredit);
            posQueryBldr.addWhereAttrInQuery(CIAccounting.TransactionPositionAbstract.TransactionLink, attrQuery);
            final MultiPrintQuery posMulti = posQueryBldr.getPrint();
            final SelectBuilder accSel = new SelectBuilder().linkto(
                            CIAccounting.TransactionPositionAbstract.AccountLink)
                            .oid();
            final SelectBuilder dateSel = new SelectBuilder().linkto(
                            CIAccounting.TransactionPositionAbstract.TransactionLink)
                            .attribute(CIAccounting.Transaction.Date);
            posMulti.addSelect(accSel, dateSel);
            posMulti.execute();
            DateTime dateTmp = new DateTime().plusYears(100);
            while (posMulti.next()) {
                final DateTime date = posMulti.<DateTime>getSelect(dateSel);
                if (date != null && date.isBefore(dateTmp)) {
                    dateTmp = date;
                    ret = Instance.get(posMulti.<String>getSelect(accSel));
                }
            }
        }
        return ret;
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
        // in case that an account is the parent the periode is searched
        if (parent.getType().isKindOf(CIAccounting.AccountAbstract.getType())) {
            final PrintQuery print = new PrintQuery(parent);
            final SelectBuilder sel = new SelectBuilder().linkto(CIAccounting.AccountAbstract.PeriodeAbstractLink)
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
        insert.add(CIAccounting.Transaction.PeriodeLink, parent);
        insert.add(CIAccounting.Transaction.Status, Status.find(CIAccounting.TransactionStatus.uuid, "Open"));
        insert.execute();
        final Instance instance = insert.getInstance();
        insertPositions(_parameter, instance, "Credit", null);
        insertPositions(_parameter, instance, "Debit", null);

        insertReportRelation(_parameter, instance);

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
            // create classifications
            final Classification classification1 = (Classification) CIAccounting.TransactionClass.getType();
            final Insert relInsert1 = new Insert(classification1.getClassifyRelationType());
            relInsert1.add(classification1.getRelLinkAttributeName(), instance.getId());
            relInsert1.add(classification1.getRelTypeAttributeName(), classification1.getId());
            relInsert1.execute();

            final Insert classInsert1 = new Insert(classification1);
            classInsert1.add(classification1.getLinkAttributeName(), instance.getId());
            classInsert1.execute();

            final Classification classification = (Classification) CIAccounting.TransactionClassDocument.getType();
            final Insert relInsert = new Insert(classification.getClassifyRelationType());
            relInsert.add(classification.getRelLinkAttributeName(), instance.getId());
            relInsert.add(classification.getRelTypeAttributeName(), classification.getId());
            relInsert.execute();

            final Insert classInsert = new Insert(classification);
            classInsert.add(classification.getLinkAttributeName(), instance.getId());
            classInsert.add(CIAccounting.TransactionClassDocument.DocumentLink, docInst.getId());
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
     * @param _parameter Parameter as passed from the eFaps API
     * @param _transInst    Transaction Instance
     * @param _docInst      Document instance
     * @throws EFapsException on error
     */
    protected void createPaymentClass(final Parameter _parameter,
                                      final Instance _transInst,
                                      final Instance _payDocInst)
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

        final Classification classification = (Classification) CIAccounting.TransactionClassPayDoc.getType();
        final Insert relInsert = new Insert(classification.getClassifyRelationType());
        relInsert.add(classification.getRelLinkAttributeName(), _transInst.getId());
        relInsert.add(classification.getRelTypeAttributeName(), classification.getId());
        relInsert.execute();

        final Insert classInsert = new Insert(CIAccounting.TransactionClassPayDoc);
        classInsert.add(classification.getLinkAttributeName(), _transInst.getId());
        classInsert.add(CIAccounting.TransactionClassPayDoc.PayDocLink, _payDocInst.getId());
        classInsert.execute();
    }

    /**
     * Create the classifcation.
     * @param _parameter Parameter as passed from the eFaps API
     * @param _transInst    Transaction Instance
     * @param _docInst      Document instance
     * @throws EFapsException on error
     */
    protected void createDocClass(final Parameter _parameter,
                                  final Instance _transInst,
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
        createDocClass(_parameter, instance, docInst);

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
        add4CreateDoc(_parameter);

        return new Return();
    }

    /**
     * Method to execute additional events.
     *
     * @param _parameter as passed from eFaps API.
     * @throws EFapsException on error.
     */
    protected void add4CreateDoc(final Parameter _parameter)
        throws EFapsException
    {

    }



    public Return create4Exchange(final Parameter _parameter)
        throws EFapsException
    {
        return create4Doc(_parameter);
    }

    public Return create4RetPer(final Parameter _parameter)
        throws EFapsException
    {
        return create4Doc(_parameter);
    }

    public Return create4Securities(final Parameter _parameter)
        throws EFapsException
    {
        return create4Doc(_parameter);
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

        insertReportRelation(_parameter, instance);
        return new Return();
    }

    /**
     * @param _parameter Parameter as passed from the eFaps API
     * @param _instance instance of the Tranaction
     * @throws
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

        final Instance periodeInst = new Periode().evaluateCurrentPeriod(_parameter);
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
        final String[] acc2accOids = _parameter.getParameterValues("acc2acc_" + _postFix);
        final String[] label2projectOids = _parameter.getParameterValues("labelLink_" + _postFix);
        final DecimalFormat formater = new Transaction().getFormater(null, null);
        try {
            Instance inst = _parameter.getCallInstance();
            if (!inst.getType().isKindOf(CIAccounting.Periode.getType())) {
                inst = new Periode().evaluateCurrentPeriod(_parameter);
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
                                insert3.add(CIAccounting.TransactionPositionAbstract.TransactionLink, _instance.getId());
                                insert3.add(CIAccounting.TransactionPositionAbstract.AccountLink,
                                                multi.getAttribute(CIAccounting.Account2AccountAbstract.ToAccountLink));
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
    public Return createDocMassive4Stock(final Parameter _parameter)
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
            trans.getCostInformation(_parameter, date, doc);
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
                insert.add(CIAccounting.Transaction.PeriodeLink, _parameter.getInstance().getId());
                insert.add(CIAccounting.Transaction.Status,
                                Status.find(CIAccounting.TransactionStatus.uuid, "Open").getId());
                insert.execute();
                final Instance transInst = insert.getInstance();
                for (final TargetAccount account : doc.getCreditAccounts().values()) {
                    insertPosition4Massiv(_parameter, doc, transInst, CIAccounting.TransactionPositionCredit, account);
                }
                for (final TargetAccount account : doc.getDebitAccounts().values()) {
                    insertPosition4Massiv(_parameter, doc, transInst, CIAccounting.TransactionPositionDebit, account);
                }
                createDocClass(_parameter, transInst, docInst);
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
     * @param _parameter
     * @return
     * @throws EFapsException
     */
    public Return createDoc4Massive(final Parameter _parameter)
        throws EFapsException
    {
        Instance periodeInst = _parameter.getCallInstance();
        if (_parameter.getCallInstance().getType().isKindOf(CIAccounting.SubPeriod.getType())) {
            final PrintQuery print = new CachedPrintQuery(_parameter.getCallInstance(), SubPeriod_Base.CACHEKEY);
            final SelectBuilder selPeriodInst = SelectBuilder.get().linkto(CIAccounting.SubPeriod.PeriodLink)
                            .instance();
            print.addSelect(selPeriodInst);
            print.execute();
            periodeInst = print.<Instance>getSelect(selPeriodInst);
        }
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
                final RateInfo rate = txn.evaluateRate(_parameter, periodeInst, date, currInst);
                final Document doc = txn.new Document(instDoc);
                doc.setAmount(amount);
                doc.setFormater(txn.getFormater(2, 2));
                doc.setDate(date);
                doc.setRateInfo(rate);

                new Transaction().buildDoc4ExecuteButton(_parameter, doc);

                // validate the transaction and if necessary create rounding parts
                final PrintQuery checkPrint = new PrintQuery(doc.getInstance());
                checkPrint.addAttribute(CISales.DocumentSumAbstract.RateCrossTotal);
                checkPrint.executeWithoutAccessCheck();

                final BigDecimal checkCrossTotal = checkPrint
                                .<BigDecimal>getAttribute(CISales.DocumentSumAbstract.RateCrossTotal);
                BigDecimal debit = BigDecimal.ZERO;
                for (final TargetAccount acc : doc.getDebitAccounts().values()) {
                    debit = debit.add(acc.getAmount().setScale(2, BigDecimal.ROUND_HALF_UP));
                }
                BigDecimal credit = BigDecimal.ZERO;
                for (final TargetAccount acc : doc.getCreditAccounts().values()) {
                    credit = credit.add(acc.getAmount().setScale(2, BigDecimal.ROUND_HALF_UP));
                }
                if (useRounding) {
                    // is does not sum to 0 but is less then the max defined
                    final Properties props = Accounting.getSysConfig().getObjectAttributeValueAsProperties(periodeInst);
                    final String diffMinStr = props.getProperty(AccountingSettings.PERIOD_ROUNDINGMAXAMOUNT);
                    final BigDecimal diffMin = (diffMinStr != null && !diffMinStr.isEmpty())
                                    ? new BigDecimal(diffMinStr) : BigDecimal.ZERO;
                    if ((checkCrossTotal.compareTo(debit) != 0 || checkCrossTotal.compareTo(credit) != 0)
                                    && debit.subtract(credit).abs().compareTo(diffMin) < 0) {
                        final TargetAccount acc = doc.getDebitAccounts().values().iterator().next();
                        if (checkCrossTotal.compareTo(debit) > 0) {
                            final TargetAccount account = getRoundingAccount(_parameter, AccountingSettings.PERIOD_ROUNDINGDEBIT);
                            account.setAmount(checkCrossTotal.subtract(debit));
                            account.setRateInfo(acc.getRateInfo());
                            final BigDecimal rateAmount = account.getAmount().setScale(12, BigDecimal.ROUND_HALF_UP)
                                            .divide(rate.getRate(), 12, BigDecimal.ROUND_HALF_UP);
                            account.setAmountRate(rateAmount);
                            doc.getDebitAccounts().put(account.getOid(), account);
                            debit = debit.add(checkCrossTotal.subtract(debit));
                        }
                        if (checkCrossTotal.compareTo(credit) > 0) {
                            final TargetAccount account = getRoundingAccount(_parameter, AccountingSettings.PERIOD_ROUNDINGCREDIT);
                            doc.getCreditAccounts().put(account.getOid(), account);
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
                    createTrans4DocMassive(_parameter, doc, periodeInst, desc);
                }
            }
        }
        return new Return();
    }

    /**
     * @param _key  key the account is wanted for
     * @return target acccount
     * @throws EFapsException on error
     */
    protected TargetAccount getRoundingAccount(final Parameter _parameter,
                                               final String _key)
        throws EFapsException
    {
        final Instance periodInst = new Periode().evaluateCurrentPeriod(_parameter);
        TargetAccount ret = null;
        final Properties props = Accounting.getSysConfig().getObjectAttributeValueAsProperties(periodInst);
        final String name = props.getProperty(_key);
        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.AccountAbstract);
        queryBldr.addWhereAttrEqValue(CIAccounting.AccountAbstract.Name, name);
        queryBldr.addWhereAttrEqValue(CIAccounting.AccountAbstract.PeriodeAbstractLink, periodInst);
        final MultiPrintQuery multi = queryBldr.getPrint();
        multi.addAttribute(CIAccounting.AccountAbstract.Name, CIAccounting.AccountAbstract.Description);
        multi.executeWithoutAccessCheck();
        while (multi.next()) {
            ret = new Transaction().new TargetAccount(multi.getCurrentInstance().getOid(),
                            multi.<String>getAttribute(CIAccounting.AccountAbstract.Name),
                            multi.<String>getAttribute(CIAccounting.AccountAbstract.Description), BigDecimal.ZERO);
        }
        if (ret == null) {
            Create_Base.LOG.warn("Cannot find Account for: '{}'", _key);
        }
        return ret;
    }

    /**
     * @param _parameter
     * @param _doc
     * @param _instPeriode
     * @param _description
     * @throws EFapsException
     */
    protected void createTrans4DocMassive(final Parameter _parameter,
                                          final Document _doc,
                                          final Instance _instPeriode,
                                          final String _description)
        throws EFapsException
    {
        final Map<?, ?> properties = (Map<?, ?>) _parameter.get(ParameterValues.PROPERTIES);

        final Insert insert = new Insert(CIAccounting.Transaction);
        insert.add(CIAccounting.Transaction.Description, _description);
        insert.add(CIAccounting.Transaction.Date, _doc.getDate());
        insert.add(CIAccounting.Transaction.PeriodeLink, _instPeriode);
        insert.add(CIAccounting.Transaction.Status, Status.find(CIAccounting.TransactionStatus.uuid, "Open").getId());
        insert.execute();

        final Instance instance = insert.getInstance();

        final Classification classType = properties.containsKey("Classification")
                        ? Classification.get((String) properties.get("Classification")) : null;
        if (classType != null) {
            final Classification classification1 = (Classification) CIAccounting.TransactionClass.getType();
            final Insert insertClassRel = new Insert(classification1.getClassifyRelationType());
            insertClassRel.add(classification1.getRelLinkAttributeName(), instance.getId());
            insertClassRel.add(classification1.getRelTypeAttributeName(), classification1.getId());
            insertClassRel.execute();

            final Insert insertClass = new Insert(classification1);
            insertClass.add(classification1.getLinkAttributeName(), instance.getId());
            insertClass.execute();

            final Classification classification2 = classType;
            final Insert insertClassRel2 = new Insert(classification2.getClassifyRelationType());
            insertClassRel2.add(classification2.getRelLinkAttributeName(), instance.getId());
            insertClassRel2.add(classification2.getRelTypeAttributeName(), classification2.getId());
            insertClassRel2.execute();

            final Insert insertClass2 = new Insert(classification2);
            insertClass2.add(classification2.getLinkAttributeName(), instance.getId());
            insertClass2.add(CIAccounting.TransactionClassDocument.DocumentLink, _doc.getInstance());
            insertClass2.execute();
        }

        final Instance transInst = insert.getInstance();

        for (final TargetAccount account : _doc.getCreditAccounts().values()) {
            insertPosition4Massiv(_parameter, _doc, transInst, CIAccounting.TransactionPositionCredit, account);
        }
        for (final TargetAccount account : _doc.getDebitAccounts().values()) {
            insertPosition4Massiv(_parameter, _doc, transInst, CIAccounting.TransactionPositionDebit, account);
        }

    }

    /**
     * @param _doc          Document
     * @param _transInst    Transaction Instance
     * @param _type         CITYpe
     * @param _account      TargetAccount
     * @return Instance of the new position
     * @throws EFapsException   on error
     */
    protected Instance insertPosition4Massiv(final Parameter _parameter,
                                             final Document _doc,
                                             final Instance _transInst,
                                             final CIType _type,
                                             final TargetAccount _account)
        throws EFapsException
    {
        final boolean isDebitTrans = _type.equals(CIAccounting.TransactionPositionDebit);
        final Instance accInst = Instance.get(_account.getOid());
        Instance periodeInst = _parameter.getCallInstance();
        if (_parameter.getCallInstance().getType().isKindOf(CIAccounting.SubPeriod.getType())) {
            final PrintQuery print = new CachedPrintQuery(_parameter.getCallInstance(), SubPeriod_Base.CACHEKEY);
            final SelectBuilder selPeriodInst = SelectBuilder.get().linkto(CIAccounting.SubPeriod.PeriodLink)
                            .instance();
            print.addSelect(selPeriodInst);
            print.execute();
            periodeInst = print.<Instance>getSelect(selPeriodInst);
        }
        final Instance curInstance = new Periode().getCurrency(periodeInst).getInstance();
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
