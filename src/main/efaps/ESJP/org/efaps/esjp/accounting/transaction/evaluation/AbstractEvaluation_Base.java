/*
 * Copyright 2003 - 2017 The eFaps Team
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
 */

package org.efaps.esjp.accounting.transaction.evaluation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang3.RandomStringUtils;
import org.efaps.admin.datamodel.ui.RateUI;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Parameter.ParameterValues;
import org.efaps.admin.event.Return;
import org.efaps.admin.event.Return.ReturnValues;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.AttributeQuery;
import org.efaps.db.Context;
import org.efaps.db.Instance;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.PrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.esjp.accounting.Account2CaseInfo;
import org.efaps.esjp.accounting.Label;
import org.efaps.esjp.accounting.Period;
import org.efaps.esjp.accounting.transaction.AccountInfo;
import org.efaps.esjp.accounting.transaction.Transaction;
import org.efaps.esjp.accounting.transaction.Transaction_Base;
import org.efaps.esjp.accounting.util.Accounting;
import org.efaps.esjp.accounting.util.Accounting.Account2CaseConfig;
import org.efaps.esjp.accounting.util.Accounting.ActDef2Case4DocConfig;
import org.efaps.esjp.accounting.util.Accounting.ExchangeConfig;
import org.efaps.esjp.accounting.util.Accounting.SummarizeConfig;
import org.efaps.esjp.accounting.util.AccountingSettings;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.ci.CIERP;
import org.efaps.esjp.ci.CISales;
import org.efaps.esjp.db.InstanceUtils;
import org.efaps.esjp.erp.Currency;
import org.efaps.esjp.erp.CurrencyInst;
import org.efaps.esjp.erp.NumberFormatter;
import org.efaps.esjp.erp.RateInfo;
import org.efaps.ui.wicket.util.DateUtil;
import org.efaps.util.EFapsException;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 */
@EFapsUUID("2c12b39c-679e-46ca-b972-13866326e4c4")
@EFapsApplication("eFapsApp-Accounting")
public abstract class AbstractEvaluation_Base
    extends Transaction
{
    /**
     *  Logger for this class.
     */
    private static final Logger LOG = LoggerFactory.getLogger(AbstractEvaluation.class);

    /**
     * Executed the command on the button.
     *
     * @param _parameter Parameter as passed from the eFaps API
     * @return new Return
     * @throws EFapsException on error
     */
    public Return executeButton(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final List<DocumentInfo> docs = evalDocuments(_parameter);
        if (docs != null && !docs.isEmpty()) {
            final DocumentInfo docInfo = DocumentInfo.getCombined(docs,
                            getSummarizeConfig(_parameter), getSummarizeCriteria(_parameter));
            docInfo.applyRounding(_parameter);
            final StringBuilder js = getScript4ExecuteButton(_parameter, docInfo);
            ret.put(ReturnValues.SNIPLETT, js.toString());
        }
        return ret;
    }

    /**
     * @param _parameter Parameter as passed by the eFaps API
     * @return new Document
     * @throws EFapsException on error
     */
    public List<DocumentInfo> evalDocuments(final Parameter _parameter)
        throws EFapsException
    {
        final List<DocumentInfo> ret = new ArrayList<>();
        final Instance caseInst = Instance.get(_parameter.getParameterValue("case"));
        String[] docOids;
        if ("true".equalsIgnoreCase(getProperty(_parameter, "WithoutDocument"))) {
            docOids = new String[] { "0.0" };
        } else {
            docOids = _parameter.getParameterValues("document");
            if (docOids == null) {
                docOids = (String[]) Context.getThreadContext().getSessionAttribute("docOids");
            }
        }

        for (final String docOid : docOids) {
            AbstractEvaluation_Base.LOG.debug("Evaluating Document {}", docOid);
            final Instance docInst = Instance.get(docOid);
            final DocumentInfo docInfo = new DocumentInfo();
            if (caseInst.isValid() && docInst.isValid() && docInst.getType().isKindOf(CIERP.DocumentAbstract.getType())
                            || docInst.isValid()
                                && docInst.getType().isKindOf(CIERP.PaymentDocumentAbstract.getType())) {
                try {
                    ret.add(docInfo);
                    final String curr = _parameter.getParameterValue("currencyExternal");
                    final String amountStr = _parameter.getParameterValue("amountExternal");
                    docInfo.setFormater(NumberFormatter.get().getTwoDigitsFormatter());
                    final Instance currInst;
                    if (curr == null && amountStr == null) {
                        docInfo.setInstance(docInst);
                        final PrintQuery print = new PrintQuery(docInst);
                        final SelectBuilder sel;
                        if (docInfo.isPaymentDoc()) {
                            sel = SelectBuilder.get().linkto(CISales.PaymentDocumentAbstract.RateCurrencyLink)
                                            .instance();
                        } else {
                            sel = SelectBuilder.get().linkto(CISales.DocumentSumAbstract.RateCurrencyId)
                                            .instance();
                        }
                        print.addSelect(sel);
                        print.addAttribute(CIERP.DocumentAbstract.Date);
                        print.execute();
                        currInst = print.<Instance>getSelect(sel);
                        docInfo.setDate(print.<DateTime>getAttribute(CIERP.DocumentAbstract.Date));
                    } else {
                        docInfo.setAmount((BigDecimal) docInfo.getFormater().parse(
                                        amountStr.isEmpty() ? "0" : amountStr));
                        currInst = Instance.get(CIERP.Currency.getType(), Long.parseLong(curr));
                        if (_parameter.getParameterValue("date") != null) {
                            docInfo.setDate(new DateTime(_parameter.getParameterValue("date")));
                        } else {
                            docInfo.setDate(DateUtil.getDateFromParameter(
                                            _parameter.getParameterValue("date_eFapsDate")));
                        }
                    }
                    final ExchangeConfig exConfig = getExchangeConfig(_parameter, caseInst);
                    docInfo.setExchangeConfig(exConfig);
                    switch (exConfig) {
                        case DOCDATEPURCHASE:
                            docInfo.setRateDate(docInfo.getDate());
                            break;
                        case DOCDATESALE:
                            docInfo.setRateDate(docInfo.getDate());
                            break;
                        case TRANSDATEPURCHASE:
                        case TRANSDATESALE:
                        default:
                            if (_parameter.getParameterValue("date") != null) {
                                docInfo.setRateDate(new DateTime(_parameter.getParameterValue("date")));
                            } else if (_parameter.getParameterValue("date_eFapsDate") != null) {
                                docInfo.setRateDate(DateUtil.getDateFromParameter(
                                                _parameter.getParameterValue("date_eFapsDate")));
                            } else {
                                docInfo.setRateDate(docInfo.getDate());
                            }
                            break;
                    }

                    final RateInfo rateInfo = evaluateRate(_parameter, docInfo.getRateDate(), currInst);
                    docInfo.setRateInfo(rateInfo);

                    add2Doc4Case(_parameter, docInfo);

                    if (docInfo.getInstance() != null) {
                        docInfo.setInvert(docInfo.getInstance().getType().isKindOf(CISales.ReturnSlip.getType()));
                        add2Doc4BankCash(_parameter, docInfo);
                    }
                    add2Doc4SalesTransaction(_parameter, docInfo);
                    add2Doc4Payment(_parameter, docInfo);
                } catch (final ParseException e) {
                    throw new EFapsException(Transaction_Base.class, "executeButton.ParseException", e);
                }
            }
        }
        return ret;
    }

    /**
     * @param _parameter Parameter as passed by the eFaps API
     * @param _doc Document the calculation must be done for
     * @throws EFapsException on error
     */
    protected void add2Doc4Case(final Parameter _parameter,
                                final DocumentInfo _doc)
        throws EFapsException
    {
        final Instance caseInst = Instance.get(_parameter.getParameterValue("case"));
        if (caseInst.isValid()) {
            _doc.setCaseInst(caseInst);

            final Instance periodInst = Period.evalCurrent(_parameter);
            final Instance periodCurrenycInstance = Period.evalCurrentCurrency(_parameter).getInstance();

            final List<Instance> labelInsts = new Label().getLabelInst4Documents(_parameter, _doc.getInstance(),
                            periodInst);

            final Map<String, Account2CaseInfo> inst2caseInfo = new HashMap<>();
            for (final Entry<Instance, Map<String, BigDecimal>> entry : _doc.getProduct2Amount().entrySet()) {
                final Account2CaseInfo acc2case = Account2CaseInfo.get4Product(_parameter, caseInst, entry.getKey());
                if (acc2case != null) {
                    if (inst2caseInfo.containsKey(acc2case.getInstance().getOid())) {
                        final Account2CaseInfo existing = inst2caseInfo.get(acc2case.getInstance().getOid());
                        existing.setAmount(existing.getAmount().add(DocumentInfo_Base.getAmount4Map(entry.getValue(),
                                        existing.getAmountConfig(), existing.getAccountInstance())));
                    } else {
                        // acc2case.setAmount(entry.getValue());
                        if (acc2case.getConfigs().contains(Accounting.Account2CaseConfig.SEPARATELY)) {
                            inst2caseInfo.put(RandomStringUtils.random(4), acc2case);
                        } else {
                            inst2caseInfo.put(acc2case.getInstance().getOid(), acc2case);
                        }
                    }
                }
            }
            final List<Account2CaseInfo> infos = Account2CaseInfo.getStandards(_parameter, caseInst);
            infos.addAll(inst2caseInfo.values());

            Collections.sort(infos, new Comparator<Account2CaseInfo>()
            {
                @Override
                public int compare(final Account2CaseInfo _o1,
                                   final Account2CaseInfo _o2)
                {
                    return _o1.getOrder().compareTo(_o2.getOrder());
                }
            });

            for (final Account2CaseInfo acc2case : infos) {
                boolean isDefault = acc2case.isDefault();
                if (!isDefault && !acc2case.isClassRelation() && !acc2case.isCategoryProduct()
                                && acc2case.getConfigs() != null
                                && acc2case.getConfigs().contains(Account2CaseConfig.EVALRELATION)) {
                    isDefault = evalRelatedDocuments(_parameter, _doc, acc2case.getAccountInstance());
                }
                final boolean currencyCheck;
                if (acc2case.isCheckCurrency()) {
                    currencyCheck = _doc.getRateInfo().getCurrencyInstance().equals(acc2case.getCurrencyInstance());
                } else {
                    currencyCheck = true;
                }

                if (acc2case.isCheckKey()) {
                    if (_doc.getKey2Amount(_parameter).containsKey(acc2case.getKey())) {
                        isDefault = true;
                        acc2case.setAmount(DocumentInfo_Base.getAmount4Map(_doc.getKey2Amount(_parameter).get(acc2case
                                        .getKey()), acc2case.getAmountConfig(), acc2case.getAccountInstance()));
                    }
                }

                final boolean add = (isDefault || acc2case.isClassRelation() || acc2case.isCategoryProduct()
                                || acc2case.isTreeView()) && currencyCheck;
                if (add) {
                    final BigDecimal mul = new BigDecimal(acc2case.getNumerator()).setScale(12).divide(
                                    new BigDecimal(acc2case.getDenominator()),
                                    RoundingMode.HALF_UP);
                    final BigDecimal amountTmp = acc2case.isClassRelation() || acc2case.isCategoryProduct()
                                    || acc2case.isCheckKey() || acc2case.isTreeView()
                                    ? acc2case.getAmount() : _doc.getAmount(acc2case);
                    final BigDecimal accAmount = mul.multiply(amountTmp).setScale(2, RoundingMode.HALF_UP);
                    final BigDecimal accAmountRate = Currency.convertToCurrency(_parameter, accAmount,
                                    _doc.getRateInfo(), _doc.getRatePropKey(), periodCurrenycInstance);

                    final AccountInfo account = new AccountInfo(acc2case.getAccountInstance(), accAmount);
                    account.setRemark(acc2case.getRemark());
                    if (acc2case.isApplyLabel() && !labelInsts.isEmpty()) {
                        account.setLabelInst(labelInsts.get(0));
                    }
                    account.setAmountRate(accAmountRate);
                    if (_doc.getInstance() != null) {
                        account.setRateInfo(_doc.getRateInfo(), _doc.getInstance().getType().getName());
                    } else {
                        account.setRateInfo(_doc.getRateInfo(), getProperty(_parameter, "Type4RateInfo"));
                    }
                    if (acc2case.isCredit()) {
                        account.setPostFix("_Credit");
                        _doc.addCredit(account);
                    } else {
                        account.setPostFix("_Debit");
                        _doc.addDebit(account);
                    }
                }
            }
        }
    }

    /**
     * method for add account bank cash in case existing DocumentInfo instance.
     *
     * @param _parameter Parameter as passed from the eFaps API.
     * @param _doc Document.
     * @param _accInst  instance of the account to be checked for
     * @return true, if successful
     * @throws EFapsException on error.
     */
    protected boolean evalRelatedDocuments(final Parameter _parameter,
                                           final DocumentInfo _doc,
                                           final Instance _accInst)
        throws EFapsException
    {
        boolean ret = false;
        Instance relDocInst = null;
        if (_doc.getInstance().getType().isCIType(CISales.IncomingRetention)) {
            final PrintQuery print = new PrintQuery(_doc.getInstance());
            final SelectBuilder selRelDoc = SelectBuilder.get()
                            .linkfrom(CISales.IncomingRetention2IncomingInvoice.FromLink)
                            .linkto(CISales.IncomingRetention2IncomingInvoice.ToAbstractLink).instance();
            print.addSelect(selRelDoc);
            print.executeWithoutAccessCheck();
            relDocInst = print.getSelect(selRelDoc);
        }
        if (relDocInst != null && relDocInst.isValid()) {
            final QueryBuilder attrQueryBldr = new QueryBuilder(CIAccounting.Transaction2SalesDocument);
            attrQueryBldr.addWhereAttrEqValue(CIAccounting.Transaction2SalesDocument.ToLink, relDocInst);
            final AttributeQuery attrQuery = attrQueryBldr
                            .getAttributeQuery(CIAccounting.Transaction2SalesDocument.FromLink);

            final QueryBuilder posQueryBldr = new QueryBuilder(CIAccounting.TransactionPositionAbstract);
            posQueryBldr.addWhereAttrInQuery(CIAccounting.TransactionPositionAbstract.TransactionLink,
                            attrQuery);
            posQueryBldr.addWhereAttrEqValue(CIAccounting.TransactionPositionAbstract.AccountLink, _accInst);
            ret = posQueryBldr.getQuery().executeWithoutAccessCheck().isEmpty();
        }
        return ret;
    }

    /**
     * method for add account bank cash in case existing DocumentInfo instance.
     *
     * @param _parameter Parameter as passed from the eFaps API.
     * @param _doc Document.
     * @throws EFapsException on error.
     */
    protected void add2Doc4SalesTransaction(final Parameter _parameter,
                                            final DocumentInfo _doc)
        throws EFapsException
    {
        if (_doc.isPaymentDoc()) {
            _doc.setSummarizeConfig(SummarizeConfig.NONE);
            final QueryBuilder attrQueryBldr = new QueryBuilder(CISales.Payment);
            attrQueryBldr.addWhereAttrEqValue(CISales.Payment.TargetDocument, _doc.getInstance());
            final AttributeQuery attrQuery = attrQueryBldr.getAttributeQuery(CISales.Payment.ID);

            final QueryBuilder queryBldr = new QueryBuilder(CISales.TransactionAbstract);
            queryBldr.addWhereAttrInQuery(CISales.TransactionAbstract.Payment, attrQuery);
            final MultiPrintQuery multi = queryBldr.getPrint();
            final SelectBuilder selCurInst = SelectBuilder.get().linkto(CISales.TransactionAbstract.CurrencyId)
                            .instance();
            final SelectBuilder selSalesAccInst = SelectBuilder.get().linkto(CISales.TransactionAbstract.Account)
                            .instance();
            final SelectBuilder selDocInst = SelectBuilder.get().linkto(CISales.TransactionAbstract.Payment)
                            .linkto(CISales.Payment.CreateDocument)
                            .instance();
            final SelectBuilder selDocName = SelectBuilder.get().linkto(CISales.TransactionAbstract.Payment)
                            .linkto(CISales.Payment.CreateDocument)
                            .attribute(CIERP.DocumentAbstract.Name);
            multi.addSelect(selCurInst, selSalesAccInst, selDocInst, selDocName);
            multi.addAttribute(CISales.TransactionAbstract.Amount, CISales.TransactionAbstract.CurrencyId);
            multi.execute();
            while (multi.next()) {
                final BigDecimal amount = multi.<BigDecimal>getAttribute(CISales.TransactionAbstract.Amount);
                final Instance salesAccInst = multi.<Instance>getSelect(selSalesAccInst);
                final AccountInfo account = getTargetAccount4SalesAccount(_parameter, salesAccInst)
                                .addAmount(amount);
                final Instance docInst = multi.getSelect(selDocInst);
                final String docName = multi.getSelect(selDocName);
                account.setDocLink(docInst).setRemark(docName);
                // special handling for transferdocument
                if (InstanceUtils.isKindOf(_doc.getInstance(), CISales.TransferDocument)) {
                    final CurrencyInst currInst = CurrencyInst.get(multi.<Long>getAttribute(
                                    CISales.TransactionAbstract.CurrencyId));
                    final Period period = new Period();
                    if (currInst.getInstance().equals(period.getCurrency(
                                    period.evaluateCurrentPeriod(_parameter)).getInstance())) {
                        account.setRateInfo(RateInfo.getDummyRateInfo(), _doc.getInstance().getType().getName());
                    } else {
                        account.setRateInfo(_doc.getRateInfo(), _doc.getInstance().getType().getName());
                    }
                } else {
                    account.setRateInfo(_doc.getRateInfo(), _doc.getInstance().getType().getName());
                }
                if (multi.getCurrentInstance().getType().isKindOf(CISales.TransactionInbound.getType())) {
                    _doc.addDebit(account);
                } else {
                    _doc.addCredit(account);
                }
            }
        }
    }

    /**
     * Get the Source account for a Transaction with PaymentDocument.
     *
     * @param _parameter Parameter as passe by the eFaps API
     * @param _doc Doc to add to
     * @throws EFapsException on error
     */
    protected void add2Doc4Payment(final Parameter _parameter,
                                   final DocumentInfo _doc)
        throws EFapsException
    {
        if (_doc.isPaymentDoc()) {
            final QueryBuilder queryBldr = new QueryBuilder(CIERP.Document2PaymentDocumentAbstract);
            queryBldr.addWhereAttrEqValue(CIERP.Document2PaymentDocumentAbstract.ToAbstractLink, _doc.getInstance());
            final MultiPrintQuery multi = queryBldr.getPrint();
            final SelectBuilder selDocInst = new SelectBuilder().linkto(
                            CIERP.Document2PaymentDocumentAbstract.FromAbstractLink).instance();
            final SelectBuilder selDocName = new SelectBuilder().linkto(
                            CIERP.Document2PaymentDocumentAbstract.FromAbstractLink)
                            .attribute(CIERP.DocumentAbstract.Name);
            final SelectBuilder selCurInst = new SelectBuilder().linkto(
                            CIERP.Document2PaymentDocumentAbstract.CurrencyLink).instance();
            multi.addSelect(selDocInst, selCurInst, selDocName);
            multi.addAttribute(CIERP.Document2PaymentDocumentAbstract.Amount,
                            CIERP.Document2PaymentDocumentAbstract.Date);
            multi.execute();
            while (multi.next()) {
                final Instance docInst = multi.<Instance>getSelect(selDocInst);
                final String docName = multi.<String>getSelect(selDocName);
                Instance docInst4Trans = docInst;
                if (docInst.isValid()) {
                    final boolean outDoc = _doc.getInstance().getType().isKindOf(
                                    CISales.PaymentDocumentOutAbstract.getType());
                    // for an incoming retention check if it is managed by configuration
                    if (docInst.getType().isCIType(CISales.IncomingPerceptionCertificate)
                                    && AccountInfo.get4Config(_parameter,
                                                    AccountingSettings.PERIOD_INCOMINGPERACC) != null) {
                        final AccountInfo accInfo = AccountInfo.get4Config(_parameter,
                                        AccountingSettings.PERIOD_INCOMINGPERACC);
                        accInfo.addAmount(_doc.getAmount4Doc(docInst))
                                    .setDocLink(_doc.getInstance())
                                    .setRemark(docName)
                                    .setRateInfo(_doc.getRateInfo(), _doc.getInstance().getType().getName());
                        if (outDoc) {
                            _doc.addDebit(accInfo);
                        } else {
                            _doc.addCredit(accInfo);
                        }
                    } else {
                        // for an incoming detraction the transaction of the orginal document must be evaluated
                        if (docInst.getType().isCIType(CISales.IncomingDetraction)) {
                            final QueryBuilder altQueryBldr = new QueryBuilder(CISales.IncomingDocumentTax2Document);
                            altQueryBldr.addWhereAttrEqValue(CISales.IncomingDocumentTax2Document.FromAbstractLink,
                                            docInst);
                            final MultiPrintQuery altMulti = altQueryBldr.getPrint();
                            final SelectBuilder selDoc = SelectBuilder.get().linkto(
                                            CISales.IncomingDocumentTax2Document.ToAbstractLink).instance();
                            altMulti.addSelect(selDoc);
                            altMulti.execute();
                            if (altMulti.next()) {
                                final Instance altDocInst = altMulti.getSelect(selDoc);
                                if (altDocInst.isValid()) {
                                    docInst4Trans = altDocInst;
                                }
                            }
                        }

                        _doc.addDocInst(docInst);
                        // evaluate the transactions
                        final QueryBuilder attrQueryBldr = new QueryBuilder(CIAccounting.Transaction2SalesDocument);
                        attrQueryBldr.addWhereAttrEqValue(CIAccounting.Transaction2SalesDocument.ToLink, docInst4Trans);
                        final AttributeQuery attrQuery = attrQueryBldr
                                        .getAttributeQuery(CIAccounting.Transaction2SalesDocument.FromLink);

                        final QueryBuilder posQueryBldr = new QueryBuilder(outDoc
                                        ? CIAccounting.TransactionPositionCredit
                                        : CIAccounting.TransactionPositionDebit);
                        posQueryBldr.addWhereAttrInQuery(CIAccounting.TransactionPositionAbstract.TransactionLink,
                                        attrQuery);
                        posQueryBldr.addOrderByAttributeAsc(CIAccounting.TransactionPositionAbstract.Position);
                        final MultiPrintQuery posMulti = posQueryBldr.getPrint();
                        posMulti.setEnforceSorted(true);
                        final SelectBuilder selAccInst = new SelectBuilder().linkto(
                                        CIAccounting.TransactionPositionAbstract.AccountLink).instance();
                        final SelectBuilder dateSel = new SelectBuilder().linkto(
                                        CIAccounting.TransactionPositionAbstract.TransactionLink)
                                        .attribute(CIAccounting.Transaction.Date);
                        posMulti.addSelect(selAccInst, dateSel);
                        posMulti.execute();
                        DateTime dateTmp = new DateTime().plusYears(100);
                        while (posMulti.next()) {
                            final DateTime date = posMulti.<DateTime>getSelect(dateSel);
                            if (date != null && date.isBefore(dateTmp)) {
                                dateTmp = date;
                                final Instance accInst = posMulti.<Instance>getSelect(selAccInst);
                                final AccountInfo acc = new AccountInfo().setInstance(accInst)
                                            .addAmount(_doc.getAmount4Doc(docInst))
                                            .setDocLink(_doc.getInstance())
                                            .setRemark(docName)
                                            .setRateInfo(_doc.getRateInfo(), _doc.getInstance().getType().getName());
                                if (outDoc) {
                                    _doc.addDebit(acc);
                                } else {
                                    _doc.addCredit(acc);
                                }
                            }
                        }
                        add2Doc4Actions(_parameter, _doc, docInst);
                    }
                }
            }
            _doc.applyExchangeGainLoss(_parameter);
        }
    }

    /**
     * @param _parameter Parameter as passed by the eFaps API
     * @param _doc Document
     * @param _docInst insatcne of the current document
     * @throws EFapsException on error
     */
    protected void add2Doc4Actions(final Parameter _parameter,
                                   final DocumentInfo _doc,
                                   final Instance _docInst)
        throws EFapsException
    {
        final QueryBuilder attrQueryBldr = new QueryBuilder(CIERP.ActionDefinition2DocumentAbstract);
        attrQueryBldr.addWhereAttrEqValue(CIERP.ActionDefinition2DocumentAbstract.ToLinkAbstract, _docInst);

        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.ActionDefinition2Case4DocAbstract);
        queryBldr.addWhereAttrInQuery(CIAccounting.ActionDefinition2Case4DocAbstract.FromLinkAbstract,
                        attrQueryBldr.getAttributeQuery(CIERP.ActionDefinition2DocumentAbstract.FromLinkAbstract));
        queryBldr.addWhereAttrEqValue(CIAccounting.ActionDefinition2Case4DocAbstract.Config,
                        ActDef2Case4DocConfig.EVALONPAYMENT);
        final MultiPrintQuery multi = queryBldr.getPrint();
        final SelectBuilder selCaseInst = SelectBuilder.get()
                        .linkto(CIAccounting.ActionDefinition2Case4DocAbstract.ToLinkAbstract).instance();
        multi.addSelect(selCaseInst);
        multi.execute();
        while (multi.next()) {
            final boolean outDoc = _doc.getInstance().getType().isKindOf(CISales.PaymentDocumentOutAbstract);
            final Instance caseInst = multi.getSelect(selCaseInst);
            _doc.setCaseInst(caseInst);
            final QueryBuilder caseQueryBldr = new QueryBuilder(outDoc ? CIAccounting.Account2CaseDebit
                            : CIAccounting.Account2CaseCredit);
            caseQueryBldr.addWhereAttrEqValue(CIAccounting.Account2CaseAbstract.ToCaseAbstractLink, caseInst);
            final MultiPrintQuery caseMulti = caseQueryBldr.getPrint();
            final SelectBuilder selAccInst = SelectBuilder.get()
                            .linkto(CIAccounting.Account2CaseAbstract.FromAccountAbstractLink).instance();
            final SelectBuilder selCurrInst = SelectBuilder.get()
                            .linkto(CIAccounting.Account2CaseAbstract.CurrencyLink).instance();
            caseMulti.addSelect(selAccInst, selCurrInst);
            caseMulti.execute();
            while (caseMulti.next()) {
                final Instance currInst = caseMulti.<Instance>getSelect(selCurrInst);
                boolean add = true;
                if (currInst != null && currInst.isValid()) {
                    add = _doc.getRateInfo().getCurrencyInstance().equals(currInst);
                }
                if (add) {
                    final Instance accInst = caseMulti.<Instance>getSelect(selAccInst);
                    final AccountInfo acc = new AccountInfo().setInstance(accInst)
                                    .addAmount(_doc.getAmount4Doc(_docInst))
                                    .setRateInfo(_doc.getRateInfo(), _doc.getInstance().getType().getName());
                    if (outDoc) {
                        _doc.addDebit(acc);
                    } else {
                        _doc.addCredit(acc);
                    }
                }
            }
        }
    }

    /**
     * method for add account bank cash in case existing document instance.
     *
     * @param _parameter Parameter as passed from the eFaps API.
     * @param _doc Document.
     * @throws EFapsException on error.
     */
    protected void add2Doc4BankCash(final Parameter _parameter,
                                    final DocumentInfo _doc)
        throws EFapsException
    {
        final Map<?, ?> properties = (Map<?, ?>) _parameter.get(ParameterValues.PROPERTIES);
        final String addAccount = (String) properties.get("addAccount");
        if (addAccount != null && addAccount.length() > 0) {
            //final UIFormCell uiform = (UIFormCell) _parameter.get(ParameterValues.CLASS);
            final Instance instance = null; //Instance.get(uiform.getParent().getInstanceKey());
            final BigDecimal amount = _doc.getAmount(null);

            final AccountInfo account = new AccountInfo(instance);
            account.setRateInfo(_doc.getRateInfo(), _doc.getInstance().getType().getName());

            if ("Credit".equals(addAccount)) {
                account.setAmount(amount.subtract(_doc.getCreditSum(_parameter)));
                _doc.addCredit(account);
            } else {
                account.setAmount(amount.subtract(_doc.getDebitSum(_parameter)));
                _doc.addDebit(account);
            }
        }
    }

    /**
     * @param _parameter Parameter as passed by the eFasp API
     * @param _doc      doc the script must be build for
     * @return StringBuilder
     * @throws EFapsException on error
     */
    protected StringBuilder getScript4ExecuteButton(final Parameter _parameter,
                                                    final DocumentInfo _doc)
        throws EFapsException
    {
        final StringBuilder js = new StringBuilder()
            .append(getSetFieldValue(0, "sumDebit", _doc.getDebitSumFormated(_parameter)))
            .append(getSetFieldValue(0, "sumCredit", _doc.getCreditSumFormated(_parameter)))
            .append(getSetFieldValue(0, "sumTotal", _doc.getDifferenceFormated(_parameter)))
            .append(getSetSubJournalScript(_parameter, _doc))
            .append(getTableJS(_parameter, "Debit", _doc.getDebitAccounts()))
            .append(getTableJS(_parameter, "Credit", _doc.getCreditAccounts()));

        final String desc = _doc.getDescription(_parameter);
        if (desc != null) {
            js.append(getSetFieldValue(0, "description", desc));
        }
        return js;
    }

    /**
     * @param _parameter    Parameter as passed by the eFaps API
     * @param _postFix      postFix
     * @param _accounts     accounts the script will be created for
     * @return javascript
     * @throws EFapsException on error
     */
    protected StringBuilder getTableJS(final Parameter _parameter,
                                       final String _postFix,
                                       final Collection<AccountInfo> _accounts)
        throws EFapsException
    {
        final String tableName = "transactionPosition" + _postFix + "Table";
        final StringBuilder ret = new StringBuilder()
                        .append(getTableRemoveScript(_parameter, tableName));
        final StringBuilder onJs = new StringBuilder();
        final Collection<Map<String, Object>> values = new ArrayList<>();
        int i = 0;
        for (final AccountInfo account : _accounts) {
            final Map<String, Object> map = new HashMap<>();
            values.add(map);

            map.put("amount_" + _postFix, account.getAmountFormated());
            map.put("rateCurrencyLink_" + _postFix, account.getRateInfo().getCurrencyInstObj().getInstance().getId());
            map.put("rate_" + _postFix, account.getRateUIFrmt(_parameter));
            map.put("rate_" + _postFix + RateUI.INVERTEDSUFFIX, account.getRateInfo().getCurrencyInstObj().isInvert());
            map.put("amountRate_" + _postFix, account.getAmountRateFormated(_parameter));
            map.put("accountLink_" + _postFix, new String[] { account.getInstance().getOid(), account.getName() });
            map.put("description_" + _postFix, account.getDescription());
            map.put("remark_" + _postFix, account.getRemark() == null ? "" : account.getRemark());

            final StringBuilder linkHtml = "debit".equalsIgnoreCase(_postFix)
                            ? account.getLinkDebitHtml() : account.getLinkCreditHtml();
            if (linkHtml != null && linkHtml.length() > 0) {
                onJs.append("document.getElementsByName('account2account_")
                                .append(_postFix).append("')[").append(i).append("].innerHTML='")
                                .append(linkHtml.toString().replaceAll("'", "\\\\\\'")).append("';");
            }

            if (account.getDocLink() != null && account.getDocLink().isValid()) {
                onJs.append(getSetFieldValue(i, "docLink_" + _postFix, account.getDocLink().getOid()));
            }
            if (account.getLabelInst() != null && account.getLabelInst().isValid()) {
                onJs.append(getSetFieldValue(i, "labelLink_" + _postFix, account.getLabelInst().getOid()));
            }

            i++;
        }
        ret.append(getTableAddNewRowsScript(_parameter, tableName, values, onJs, false, false, null,
                             Period.evalCurrent(_parameter).getOid()));
        return ret;
    }
}
