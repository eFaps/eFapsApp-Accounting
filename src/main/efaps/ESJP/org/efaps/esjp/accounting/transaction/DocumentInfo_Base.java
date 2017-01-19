/*
 * Copyright 2003 - 2016 The eFaps Team
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

package org.efaps.esjp.accounting.transaction;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.text.StrSubstitutor;
import org.efaps.admin.dbproperty.DBProperties;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.admin.program.esjp.Listener;
import org.efaps.db.CachedPrintQuery;
import org.efaps.db.Context;
import org.efaps.db.Instance;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.PrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.esjp.accounting.Case;
import org.efaps.esjp.accounting.Period;
import org.efaps.esjp.accounting.listener.IOnDocumentInfo;
import org.efaps.esjp.accounting.util.Accounting;
import org.efaps.esjp.accounting.util.Accounting.ExchangeConfig;
import org.efaps.esjp.accounting.util.Accounting.SummarizeConfig;
import org.efaps.esjp.accounting.util.Accounting.SummarizeCriteria;
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
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 */
@EFapsUUID("f6508096-b3a3-4b19-9b10-0290ad0571a6")
@EFapsApplication("eFapsApp-Accounting")
public abstract class DocumentInfo_Base
{

    /**
     * Instance of this Document.
     */
    private Instance instance;

    /**
     * Instance of the applied Case.
     */
    private Instance caseInst;

    /**
     * Is this a Stock moving Document.
     */
    private boolean stockDoc;

    /**
     * Did the Document pass the validation of the cost. (Every product has its
     * cost assigned)
     */
    private boolean costValidated;

    /**
     * Is this a document containing sums.
     */
    private boolean sumsDoc;

    /**
     * Is this a document containing sums.
     */
    private boolean paymentDoc;

    /**
     * Date of this Document.
     */
    private DateTime date;

    /**
     * Date used for rateevaluation.
     */
    private DateTime rateDate;

    /** The rate prop key. */
    private ExchangeConfig exchangeConfig;

    /**
     * List of TargetAccounts for debit.
     */
    private final Set<AccountInfo> debitAccounts = new LinkedHashSet<>();

    /**
     * List of TargetAccounts for credit.
     */
    private final Set<AccountInfo> creditAccounts = new LinkedHashSet<>();

    /**
     * Amount of this Account.
     */
    private BigDecimal amount;

    /**
     * Formater.
     */
    private DecimalFormat formater;

    /**
     * Account for the Debtor.
     */
    private AccountInfo debtorAccount;

    /**
     * RateInfo of the Document.
     */
    private RateInfo rateInfo;

    /**
     * Invert this document. (Means change the map for debit and credit).
     */
    private boolean invert;

    /**
     * Summarize or not.
     */
    private SummarizeConfig config = SummarizeConfig.NONE;

    /**
     * Summarize or not.
     */
    private SummarizeCriteria summarizeCriteria = SummarizeCriteria.ACCOUNT;

    /** The doc insts. */
    private final Set<Instance> docInsts = new HashSet<>();

    /** The product2 amount. */
    private  Map<Instance, BigDecimal> product2Amount;

    /** The key2 amount. */
    private  Map<String, BigDecimal> key2Amount;

    /**
     * Constructor.
     */
    protected DocumentInfo_Base()
    {
        this(null);
    }

    /**
     * @param _instance Instance of the Document
     */
    protected DocumentInfo_Base(final Instance _instance)
    {
        setInstance(_instance);
    }

    /**
     * Gets the product2 amount.
     *
     * @return the product2 amount
     * @throws EFapsException on error
     */
    public Map<Instance, BigDecimal> getProduct2Amount()
        throws EFapsException
    {
        final Map<Instance, BigDecimal> ret;
        if (isSumsDoc()) {
            if (this.product2Amount == null) {
                this.product2Amount = new HashMap<>();
                final QueryBuilder queryBldr = new QueryBuilder(CISales.PositionAbstract);
                queryBldr.addWhereAttrEqValue(CISales.PositionAbstract.DocumentAbstractLink, this.instance);
                final MultiPrintQuery multi = queryBldr.getPrint();
                final SelectBuilder selInst = new SelectBuilder()
                                .linkto(CISales.PositionAbstract.Product).instance();
                multi.addSelect(selInst);
                multi.addAttribute(CISales.PositionSumAbstract.RateNetPrice);
                multi.execute();
                while (multi.next()) {
                    final BigDecimal posamount = multi.getAttribute(CISales.PositionSumAbstract.RateNetPrice);
                    final Instance prodInst = multi.getSelect(selInst);
                    final BigDecimal amountTmp;
                    if (this.product2Amount.containsKey(prodInst)) {
                        amountTmp = this.product2Amount.get(prodInst);
                    } else {
                        amountTmp = BigDecimal.ZERO;
                    }
                    this.product2Amount.put(prodInst, amountTmp.add(posamount));
                }
            }
            ret = this.product2Amount;
        } else {
            ret = Collections.emptyMap();
        }
        return ret;
    }


    /**
     * Gets the key2 amount.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return the key2 amount
     * @throws EFapsException on error
     */
    public Map<String, BigDecimal> getKey2Amount(final Parameter _parameter)
        throws EFapsException
    {
        final Map<String, BigDecimal> ret;
        if (isSumsDoc()) {
            if (this.key2Amount == null) {
                this.key2Amount = new HashMap<>();
                // let others participate
                for (final IOnDocumentInfo listener : Listener.get().<IOnDocumentInfo>invoke(IOnDocumentInfo.class)) {
                    for (final Entry<String, BigDecimal> entry : listener.getKey2Amount(this.instance).entrySet()) {
                        if (this.key2Amount.containsKey(entry.getKey())) {
                            if (entry.getValue() == null) {
                                this.key2Amount.remove(entry.getKey());
                            } else {
                                this.key2Amount.put(entry.getKey(),
                                                this.key2Amount.get(entry.getKey()).add(entry.getValue()));
                            }
                        } else {
                            this.key2Amount.put(entry.getKey(), entry.getValue());
                        }
                    }
                }

                final String[] swaps = _parameter.getParameterValues("swapInstance");
                if (ArrayUtils.isNotEmpty(swaps)) {
                    // find out in which of the documents are we right now
                    String[] docOids = _parameter.getParameterValues("document");
                    if (docOids == null) {
                        docOids = (String[]) Context.getThreadContext().getSessionAttribute("docOids");
                    }
                    int idx = 0;
                    for (int i = 0; i < docOids.length; i++) {
                        if (i > 0 && (i % 2) == 0) {
                            idx++;
                        }
                        if (getInstance().getOid().equals(docOids[i])) {
                            break;
                        }
                    }
                    final Instance swapInstance = Instance.get(swaps[idx]);
                    if (swapInstance.isValid()) {
                        final PrintQuery print = CachedPrintQuery.get4Request(swapInstance);
                        final SelectBuilder selFromInst = SelectBuilder.get().linkto(
                                        CISales.Document2Document4Swap.FromLink).instance();
                        final SelectBuilder selToInst = SelectBuilder.get().linkto(
                                        CISales.Document2Document4Swap.ToLink).instance();
                        print.addSelect(selFromInst, selToInst);
                        print.execute();
                        if (getInstance().equals(print.getSelect(selFromInst))) {
                            this.key2Amount.put("From", getAmount());
                        } else if (getInstance().equals(print.getSelect(selToInst))) {
                            this.key2Amount.put("To", getAmount());
                        }
                    }
                }
            }
            ret = this.key2Amount;
        } else {
            ret = Collections.emptyMap();
        }
        return ret;
    }


    /**
     * Getter method for the instance variable {@link #invert}.
     *
     * @return value of instance variable {@link #invert}
     */
    public boolean isInvert()
    {
        return this.invert;
    }

    /**
     * Setter method for instance variable {@link #invert}.
     *
     * @param _invert value for instance variable {@link #invert}
     */

    public void setInvert(final boolean _invert)
    {
        this.invert = _invert;
    }

    /**
     * Getter method for the instance variable {@link #costValidated}.
     *
     * @return value of instance variable {@link #costValidated}
     */
    public boolean isCostValidated()
    {
        return this.costValidated;
    }

    /**
     * Setter method for instance variable {@link #costValidated}.
     *
     * @param _costValidated value for instance variable {@link #costValidated}
     */

    public void setCostValidated(final boolean _costValidated)
    {
        this.costValidated = _costValidated;
    }

    /**
     * Getter method for the instance variable {@link #formater}.
     *
     * @return value of instance variable {@link #formater}
     * @throws EFapsException on error
     */
    public DecimalFormat getFormater()
        throws EFapsException
    {
        if (this.formater == null) {
            this.formater = NumberFormatter.get().getTwoDigitsFormatter();
        }
        return this.formater;
    }

    /**
     * Setter method for instance variable {@link #formater}.
     *
     * @param _formater value for instance variable {@link #formater}
     */

    public void setFormater(final DecimalFormat _formater)
    {
        this.formater = _formater;
    }

    /**
     * Getter method for the instance variable {@link #amount}.
     *
     * @return value of instance variable {@link #amount}
     */
    public BigDecimal getAmount()
    {
        return this.amount;
    }

    /**
     * Setter method for instance variable {@link #amount}.
     *
     * @param _amount value for instance variable {@link #amount}
     */

    public void setAmount(final BigDecimal _amount)
    {
        this.amount = _amount;
    }

    /**
     * Getter method for the instance variable {@link #debitAccounts}.
     *
     * @return value of instance variable {@link #debitAccounts}
     */
    public Set<AccountInfo> getDebitAccounts()
    {
        return this.invert ? this.creditAccounts : this.debitAccounts;
    }

    /**
     * @param _accInfo acinfo
     * @return this
     * @throws EFapsException on error
     */
    public DocumentInfo addDebit(final AccountInfo _accInfo)
        throws EFapsException
    {
        add(this.debitAccounts, _accInfo, true);
        return (DocumentInfo) this;
    }

    /**
     * Getter method for the instance variable {@link #creditAccounts}.
     *
     * @return value of instance variable {@link #creditAccounts}
     */
    public Set<AccountInfo> getCreditAccounts()
    {
        return this.invert ? this.debitAccounts : this.creditAccounts;
    }

    /**
     * @param _accInfo acinfo
     * @return this
     * @throws EFapsException on error
     */
    public DocumentInfo addCredit(final AccountInfo _accInfo)
        throws EFapsException
    {
        add(this.creditAccounts, _accInfo, false);
        return (DocumentInfo) this;
    }

    /**
     * Adds the.
     *
     * @param _accounts accounts to add to
     * @param _accInfo the new account
     * @param _debit the debit
     * @throws EFapsException on error
     */
    protected void add(final Set<AccountInfo> _accounts,
                       final AccountInfo _accInfo,
                       final boolean _debit)
        throws EFapsException
    {
        if (_accInfo.getRateInfo() == null) {
            if (getRateInfo() != null) {
                _accInfo.setRateInfo(getRateInfo(), getRatePropKey());
            } else {
                _accInfo.setRateInfo(RateInfo.getDummyRateInfo(), getRatePropKey());
            }
        }
        boolean add = true;
        if (SummarizeConfig.BOTH.equals(getSummarizeConfig())
                        || SummarizeConfig.DEBIT.equals(getSummarizeConfig()) && _debit
                        || SummarizeConfig.CREDIT.equals(getSummarizeConfig()) && !_debit) {
            _accInfo.setAmountRate(null); // reset the amount rate
            for (final AccountInfo acc : _accounts) {
                switch (getSummarizeCriteria()) {
                    case LABEL:
                        if (acc.getInstance().equals(_accInfo.getInstance()) && acc.getRateInfo().getCurrencyInstance()
                                        .equals(_accInfo.getRateInfo().getCurrencyInstance())
                                && (acc.getLabelInst() == null && _accInfo.getLabelInst() == null
                                        || acc.getLabelInst() != null
                                            && acc.getLabelInst().equals(_accInfo.getLabelInst()))) {
                            acc.addAmount(_accInfo.getAmount());
                            add = false;
                            break;
                        }
                        break;
                    case ALL:
                        if (acc.getInstance().equals(_accInfo.getInstance()) && acc.getRateInfo().getCurrencyInstance()
                                        .equals(_accInfo.getRateInfo().getCurrencyInstance())
                                && (acc.getLabelInst() == null && _accInfo.getLabelInst() == null
                                        || acc.getLabelInst() != null
                                                && acc.getLabelInst().equals(_accInfo.getLabelInst()))
                                && (acc.getRemark() == null && _accInfo.getRemark() == null
                                        || acc.getRemark() != null
                                                && acc.getRemark().equals(_accInfo.getRemark()))) {
                            acc.addAmount(_accInfo.getAmount());
                            add = false;
                            break;
                        }
                        break;
                    case ACCOUNT:
                    default:
                        if (acc.getInstance().equals(_accInfo.getInstance()) && acc.getRateInfo().getCurrencyInstance()
                                        .equals(_accInfo.getRateInfo().getCurrencyInstance())) {
                            acc.addAmount(_accInfo.getAmount());
                            add = false;
                            break;
                        }
                        break;
                }
            }
        }
        if (add) {
            _accounts.add(_accInfo);
        }
    }

    /**
     * Setter method for instance variable {@link #debtorAccount}.
     *
     * @param _debtorAccount value for instance variable {@link #debtorAccount}
     */

    public void setDebtorAccount(final AccountInfo _debtorAccount)
    {
        this.debtorAccount = _debtorAccount;
    }

    /**
     * Getter method for the instance variable {@link #debtorAccount}.
     *
     * @return value of instance variable {@link #debtorAccount}
     */
    public AccountInfo getDebtorAccount()
    {
        return this.debtorAccount;
    }

    /**
     * Getter method for the instance variable {@link #currSymbol}.
     *
     * @return value of instance variable {@link #currSymbol}
     * @throws EFapsException on error
     */
    public String getCurrSymbol()
        throws EFapsException
    {
        return this.rateInfo.getCurrencyInstObj().getSymbol();
    }

    /**
     * Getter method for the instance variable {@link #date}.
     *
     * @return value of instance variable {@link #date}
     */
    public DateTime getDate()
    {
        return this.date;
    }

    /**
     * @param _date date for this document
     */
    public void setDate(final DateTime _date)
    {
        this.date = _date;
    }

    /**
     * Gets the date used for rateevaluation.
     *
     * @return the date used for rateevaluation
     */
    public DateTime getRateDate()
    {
        if (this.rateDate == null) {
            this.rateDate = getDate();
        }
        return this.rateDate;
    }

    /**
     * Sets the date used for rateevaluation.
     *
     * @param _rateDate the new date used for rateevaluation
     */
    public void setRateDate(final DateTime _rateDate)
    {
        this.rateDate = _rateDate;
    }

    /**
     * @return Date as String
     * @throws EFapsException on error
     */
    public String getDateString()
        throws EFapsException
    {
        final DateTimeFormatter formatter = DateTimeFormat.mediumDate();
        return this.date.withChronology(Context.getThreadContext().getChronology()).toString(
                        formatter.withLocale(Context.getThreadContext().getLocale()));
    }

    /**
     * @return Instance of the Rate Currency
     */
    public Instance getRateCurrInst()
    {
        return this.rateInfo.getCurrencyInstance();
    }

    /**
     * Getter method for the instance variable {@link #stockDoc}.
     *
     * @return value of instance variable {@link #stockDoc}
     */
    public boolean isStockDoc()
    {
        return this.stockDoc;
    }

    /**
     * Getter method for the instance variable {@link #sumsDoc}.
     *
     * @return value of instance variable {@link #sumsDoc}
     */
    public boolean isSumsDoc()
    {
        return this.sumsDoc;
    }

    /**
     * Getter method for the instance variable {@link #instance}.
     *
     * @return value of instance variable {@link #instance}
     */
    public Instance getInstance()
    {
        return this.instance;
    }

    /**
     * Setter method for instance variable {@link #instance}.
     *
     * @param _instance value for instance variable {@link #instance}
     */

    public void setInstance(final Instance _instance)
    {
        this.instance = _instance;
        if (_instance != null && _instance.isValid()) {
            setSumsDoc(_instance.getType().isKindOf(CISales.DocumentSumAbstract.getType()));
            setPaymentDoc(_instance.getType().isKindOf(CISales.PaymentDocumentIOAbstract.getType()));
            setStockDoc(_instance.getType().isKindOf(CISales.DocumentStockAbstract.getType()));
        }
    }

    /**
     * @return the sum in the rate currency
     */
    public BigDecimal getRateCreditSum()
    {
        BigDecimal ret = BigDecimal.ZERO;
        for (final AccountInfo account : getCreditAccounts()) {
            ret = ret.add(account.getAmount().setScale(2, BigDecimal.ROUND_HALF_UP));
        }
        return ret;
    }

    /**
     * Gets the credit sum.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return the sum of credit accounts in base currency
     * @throws EFapsException on error
     */
    public BigDecimal getCreditSum(final Parameter _parameter)
        throws EFapsException
    {
        BigDecimal ret = BigDecimal.ZERO;
        for (final AccountInfo account : getCreditAccounts()) {
            ret = ret.add(account.getAmountRate(_parameter).setScale(2, BigDecimal.ROUND_HALF_UP));
        }
        return ret;
    }

    /**
     * Gets the credit sum formated.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return the sum of credit accounts formated
     * @throws EFapsException on error
     */
    public String getCreditSumFormated(final Parameter _parameter)
        throws EFapsException
    {
        return getFormater().format(getCreditSum(_parameter));
    }

    /**
     * @return the sum in the rate currency
     */
    public BigDecimal getRateDebitSum()
    {
        BigDecimal ret = BigDecimal.ZERO;
        for (final AccountInfo account : getDebitAccounts()) {
            ret = ret.add(account.getAmount().setScale(2, BigDecimal.ROUND_HALF_UP));
        }
        return ret;
    }

    /**
     * Gets the debit sum.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return the sum of all debit accounts in base currency
     * @throws EFapsException on error
     */
    public BigDecimal getDebitSum(final Parameter _parameter)
        throws EFapsException
    {
        BigDecimal ret = BigDecimal.ZERO;
        for (final AccountInfo account : getDebitAccounts()) {
            ret = ret.add(account.getAmountRate(_parameter).setScale(2, BigDecimal.ROUND_HALF_UP));
        }
        return ret;
    }

    /**
     * Gets the debit sum formated.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return the sum of all debit accounts formated
     * @throws EFapsException on error
     */
    public String getDebitSumFormated(final Parameter _parameter)
        throws EFapsException
    {
        return getFormater().format(getDebitSum(_parameter));
    }

    /**
     * Gets the difference.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return the difference between the sum of debit accounts and the sum of
     *         credit accounts
     * @throws EFapsException on error
     */
    public BigDecimal getDifference(final Parameter _parameter)
        throws EFapsException
    {
        return getDebitSum(_parameter).subtract(getCreditSum(_parameter)).abs();
    }

    /**
     * Gets the difference formated.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return the difference between the sum of debit accounts and the sum of
     *         credit accounts formated
     * @throws EFapsException on error
     */
    public String getDifferenceFormated(final Parameter _parameter)
        throws EFapsException
    {
        return getFormater().format(getDifference(_parameter));
    }

    /**
     * Getter method for the instance variable {@link #rateInfo}.
     *
     * @return value of instance variable {@link #rateInfo}
     */
    public RateInfo getRateInfo()
    {
        return this.rateInfo;
    }

    /**
     * Setter method for instance variable {@link #rateInfo}.
     *
     * @param _rateInfo value for instance variable {@link #rateInfo}
     */
    public void setRateInfo(final RateInfo _rateInfo)
    {
        this.rateInfo = _rateInfo;
    }

    /**
     * Getter method for the instance variable {@link #paymentDoc}.
     *
     * @return value of instance variable {@link #paymentDoc}
     */
    public boolean isPaymentDoc()
    {
        return this.paymentDoc;
    }

    /**
     * Setter method for instance variable {@link #paymentDoc}.
     *
     * @param _paymentDoc value for instance variable {@link #paymentDoc}
     */
    public void setPaymentDoc(final boolean _paymentDoc)
    {
        this.paymentDoc = _paymentDoc;
    }

    /**
     * Setter method for instance variable {@link #stockDoc}.
     *
     * @param _stockDoc value for instance variable {@link #stockDoc}
     */
    public void setStockDoc(final boolean _stockDoc)
    {
        this.stockDoc = _stockDoc;
    }

    /**
     * Setter method for instance variable {@link #sumsDoc}.
     *
     * @param _sumsDoc value for instance variable {@link #sumsDoc}
     */
    public void setSumsDoc(final boolean _sumsDoc)
    {
        this.sumsDoc = _sumsDoc;
    }

    /**
     * Valid means not zero and equal.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return true if valid, else false
     * @throws EFapsException on error
     */
    public boolean isValid(final Parameter _parameter)
        throws EFapsException
    {
        return getDebitSum(_parameter).compareTo(BigDecimal.ZERO) != 0
                        && getDebitSum(_parameter).compareTo(getCreditSum(_parameter)) == 0;
    }

    /**
     * Getter method for the instance variable {@link #summarize}.
     *
     * @return value of instance variable {@link #summarize}
     */
    public SummarizeConfig getSummarizeConfig()
    {
        return this.config;
    }

    /**
     * Setter method for instance variable {@link #summarize}.
     *
     * @param _config the new summarize config
     */
    public void setSummarizeConfig(final SummarizeConfig _config)
    {
        this.config = _config;
    }

    /**
     * Gets the description.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return the description
     * @throws EFapsException on error
     */
    public String getDescription(final Parameter _parameter)
        throws EFapsException
    {
        String labelStr = "";
        final Instance caseInstTmp = getCaseInst();
        if (caseInstTmp != null && caseInstTmp.isValid()) {
            final PrintQuery print = new CachedPrintQuery(caseInstTmp, Case.CACHEKEY);
            print.addAttribute(CIAccounting.CaseAbstract.Label);
            print.execute();
            labelStr = print.<String>getAttribute(CIAccounting.CaseAbstract.Label);
        } else {
            labelStr = DBProperties.getProperty(DocumentInfo.class.getName() + "."
                            + (InstanceUtils.isValid(getInstance()) ? getInstance().getType().getName() : "")
                            + ".description");
        }
        final StrSubstitutor sub = new StrSubstitutor(getMap4Substitutor(_parameter));
        return sub.replace(labelStr);
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

    /**
     * Adds the doc inst.
     *
     * @param _docInst the doc inst
     */
    public void addDocInst(final Instance _docInst)
    {
        this.docInsts.add(_docInst);
    }

    /**
     * Gets the doc insts.
     *
     * @param _include the include
     * @return array of instances to be connected
     */
    public Instance[] getDocInsts(final boolean _include)
    {
        Instance[] ret = this.docInsts.toArray(new Instance[this.docInsts.size()]);
        if (_include) {
            ret = ArrayUtils.add(ret, this.instance);
        }
        return ret;
    }

    /**
     * Gets the amount4 doc.
     *
     * @param _docInst the doc inst
     * @return the amount4 doc
     */
    public BigDecimal getAmount4Doc(final Instance _docInst)
    {
        BigDecimal ret = BigDecimal.ZERO;
        for (final AccountInfo accInfo : getDebitAccounts()) {
            if (_docInst.equals(accInfo.getDocLink())) {
                ret = ret.add(accInfo.getAmount());
            }
        }
        for (final AccountInfo accInfo : getCreditAccounts()) {
            if (_docInst.equals(accInfo.getDocLink())) {
                ret = ret.add(accInfo.getAmount());
            }
        }
        return ret;
    }

    /**
     * Gets the rate.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return the rate
     * @throws EFapsException on error
     */
    public BigDecimal getRate(final Parameter _parameter)
        throws EFapsException
    {
        return RateInfo.getRate(_parameter, getRateInfo(), getRatePropKey());
    }

    /**
     * Gets the rate prop key.
     *
     * @return the rate prop key
     */
    public String getRatePropKey()
    {
        String ret = "";
        if (this.exchangeConfig != null) {
            ret = this.exchangeConfig.name();
        } else if (getInstance() != null && getInstance().isValid()) {
            ret = getInstance().getType().getName();
        }
        return ret;
    }

    /**
     * Setter method for instance variable {@link #rounding}.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @throws EFapsException on error
     */
    public void applyRounding(final Parameter _parameter)
        throws EFapsException
    {
        if (!isValid(_parameter)) {
            final Period period = new Period();
            final Instance periodInst = period.evaluateCurrentPeriod(_parameter);
            // is does not sum to 0 but is less then the max defined
            final Properties props = Accounting.getSysConfig().getObjectAttributeValueAsProperties(periodInst);
            final BigDecimal diffMax = new BigDecimal(props.getProperty(AccountingSettings.PERIOD_ROUNDINGMAXAMOUNT,
                            "0"));
            final BigDecimal diff = getDebitSum(_parameter).subtract(getCreditSum(_parameter));
            if (diffMax.compareTo(diff.abs()) > 0) {
                final boolean debit = diff.compareTo(BigDecimal.ZERO) < 0;
                final AccountInfo accInfo;
                if (debit) {
                    accInfo = AccountInfo.get4Config(_parameter, AccountingSettings.PERIOD_ROUNDINGDEBIT);
                } else {
                    accInfo = AccountInfo.get4Config(_parameter, AccountingSettings.PERIOD_ROUNDINGCREDIT);
                }
                if (accInfo != null) {
                    accInfo.setAmount(diff.abs());
                    accInfo.setAmountRate(diff.abs());
                    final CurrencyInst currInst = period.getCurrency(periodInst);
                    accInfo.setCurrInstance(currInst.getInstance());
                    accInfo.setRateInfo(RateInfo.getDummyRateInfo(), "");
                    if (debit) {
                        addDebit(accInfo);
                    } else {
                        addCredit(accInfo);
                    }
                }
            }
        }
    }

    /**
     * Apply exchange gain loss.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @throws EFapsException on error
     */
    public void applyExchangeGainLoss(final Parameter _parameter)
        throws EFapsException
    {
        final AccountInfo gainAcc = AccountInfo.get4Config(_parameter, AccountingSettings.PERIOD_EXCHANGEGAIN);
        final AccountInfo lossAcc = AccountInfo.get4Config(_parameter, AccountingSettings.PERIOD_EXCHANGELOSS);

        if (gainAcc != null && lossAcc != null) {
            final QueryBuilder queryBldr = new QueryBuilder(CISales.Payment);
            queryBldr.addWhereAttrEqValue(CISales.Payment.TargetDocument, getInstance());
            final MultiPrintQuery multi = queryBldr.getPrint();
            final SelectBuilder selDocInst = new SelectBuilder().linkto(
                            CISales.Payment.FromAbstractLink).instance();
            final SelectBuilder selCurInst = new SelectBuilder()
                        .linkto(CISales.Payment.CurrencyLink).instance();
            multi.addSelect(selDocInst, selCurInst);
            multi.addAttribute(CISales.Payment.Amount,
                            CISales.Payment.Date);
            multi.execute();
            while (multi.next()) {
                final Instance docInst = multi.getSelect(selDocInst);
                final PrintQuery print = new PrintQuery(docInst);
                final SelectBuilder selDocCurInst = new SelectBuilder()
                                .linkto(CISales.DocumentSumAbstract.RateCurrencyId).instance();
                print.addSelect(selDocCurInst);
                print.addAttribute(CIERP.DocumentAbstract.Date);
                print.execute();
                final Instance curInst = multi.getSelect(selCurInst);
                final Instance docCurInst = print.getSelect(selDocCurInst);
                final DateTime docDate = print.getAttribute(CIERP.DocumentAbstract.Date);
                final DateTime dateTmp = multi.getAttribute(CISales.Payment.Date);
                final BigDecimal amountTmp = multi.getAttribute(CISales.Payment.Amount);

                if (!curInst.equals(Currency.getBaseCurrency()) || !docCurInst.equals(Currency.getBaseCurrency())) {
                    final Currency currency = new Currency();
                    final RateInfo[] rateInfos1 = currency.evaluateRateInfos(_parameter, dateTmp, curInst, docCurInst);
                    final RateInfo[] rateInfos2 = currency.evaluateRateInfos(_parameter, docDate, curInst, docCurInst);
                    final int idx;
                    // payment in BaseCurreny ==> Document was not BaseCurrency therefore current against target
                    if (curInst.equals(Currency.getBaseCurrency())) {
                        idx = 2;
                    // Document in  BaseCurrency ==> payment was not BaseCurrency therefore current against base
                    } else if (docCurInst.equals(Currency.getBaseCurrency())) {
                        idx = 0;
                    // neither Document nor payment are BaseCurrency but are the same
                    } else if (curInst.equals(docCurInst)) {
                        idx = 0;
                    } else {
                        idx = 0;
                    }

                    final BigDecimal rate1 = RateInfo.getRate(_parameter, rateInfos1[idx], docInst.getType().getName());
                    final BigDecimal rate2 = RateInfo.getRate(_parameter, rateInfos2[idx], docInst.getType().getName());
                    if (rate1.compareTo(rate2) != 0) {
                        final BigDecimal amount1 = amountTmp.divide(rate1, BigDecimal.ROUND_HALF_UP);
                        final BigDecimal amount2 = amountTmp.divide(rate2, BigDecimal.ROUND_HALF_UP);
                        BigDecimal gainLoss = amount1.subtract(amount2);
                        if (idx == 2) {
                            gainLoss = gainLoss.multiply(rate1);
                        }
                        if (gainLoss.compareTo(BigDecimal.ZERO) != 0) {
                            final boolean out = getInstance().getType().isKindOf(CISales.PaymentDocumentOutAbstract);
                            if (out) {
                                final boolean gain = gainLoss.compareTo(BigDecimal.ZERO) > 0;
                                for (final AccountInfo accinfo : getCreditAccounts()) {
                                    if (accinfo.getDocLink() != null && accinfo.getDocLink().equals(docInst)) {
                                        final BigDecimal accAmount;
                                        if (accinfo.getRateInfo().getCurrencyInstance()
                                                        .equals(Currency.getBaseCurrency())) {
                                            accAmount = gainLoss;
                                        } else {
                                            accAmount = gainLoss.multiply(accinfo.getRate(_parameter));
                                        }
                                        accinfo.addAmount(accAmount.negate());
                                    }
                                }
                                if (gain) {
                                    gainAcc.setAmount(gainLoss.abs()).setRateInfo(RateInfo.getDummyRateInfo(),
                                                    getInstance().getType().getName());
                                    addCredit(gainAcc);
                                } else {
                                    lossAcc.setAmount(gainLoss.abs()).setRateInfo(RateInfo.getDummyRateInfo(),
                                                    getInstance().getType().getName());
                                    addDebit(lossAcc);
                                }
                            } else {
                                final boolean gain = gainLoss.compareTo(BigDecimal.ZERO) < 0;
                                for (final AccountInfo accinfo : getDebitAccounts()) {
                                    if (accinfo.getDocLink() != null && accinfo.getDocLink().equals(docInst)) {
                                        final BigDecimal accAmount;
                                        if (!accinfo.getRateInfo().getCurrencyInstance()
                                                        .equals(Currency.getBaseCurrency())) {
                                            accAmount = gainLoss;
                                        } else {
                                            accAmount = gainLoss.multiply(accinfo.getRate(_parameter));
                                        }
                                        accinfo.addAmount(accAmount);
                                    }
                                }
                                if (gain) {
                                    gainAcc.setAmount(gainLoss.abs()).setRateInfo(RateInfo.getDummyRateInfo(),
                                                    getInstance().getType().getName());
                                    addDebit(gainAcc);
                                } else {
                                    lossAcc.setAmount(gainLoss.abs()).setRateInfo(RateInfo.getDummyRateInfo(),
                                                    getInstance().getType().getName());
                                    addCredit(lossAcc);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Getter method for the instance variable {@link #summarizeCriteria}.
     *
     * @return value of instance variable {@link #summarizeCriteria}
     */
    public SummarizeCriteria getSummarizeCriteria()
    {
        return this.summarizeCriteria;
    }

    /**
     * Setter method for instance variable {@link #summarizeCriteria}.
     *
     * @param _summarizeCriteria value for instance variable {@link #summarizeCriteria}
     */
    public void setSummarizeCriteria(final SummarizeCriteria _summarizeCriteria)
    {
        this.summarizeCriteria = _summarizeCriteria;
    }

    /**
     * Gets the rate prop key.
     *
     * @return the rate prop key
     */
    public ExchangeConfig getExchangeConfig()
    {
        return this.exchangeConfig;
    }

    /**
     * Sets the rate prop key.
     *
     * @param _exchangeConfig the new rate prop key
     */
    public void setExchangeConfig(final ExchangeConfig _exchangeConfig)
    {
        this.exchangeConfig = _exchangeConfig;
    }

    /**
     * Gets the combined.
     *
     * @param _docInfos the doc infos
     * @param _config the config
     * @param _criteria the criteria
     * @return the combined
     * @throws EFapsException on error
     */
    protected static DocumentInfo getCombined(final Collection<DocumentInfo> _docInfos,
                                              final SummarizeConfig _config,
                                              final SummarizeCriteria _criteria)
        throws EFapsException
    {
        final DocumentInfo ret;
        if (_docInfos.size() == 1) {
            ret = _docInfos.iterator().next();
        } else {
            ret = new DocumentInfo();
            ret.setRateInfo(_docInfos.iterator().next().getRateInfo());
            ret.setSummarizeConfig(_config);
            ret.setSummarizeCriteria(_criteria);
            ret.setCaseInst(_docInfos.iterator().next().getCaseInst());
            for (final DocumentInfo documentInfo : _docInfos) {
                for (final AccountInfo accInfo : documentInfo.getCreditAccounts()) {
                    ret.add(ret.getCreditAccounts(), accInfo, false);
                    if (!SummarizeConfig.BOTH.equals(_config) && !SummarizeConfig.CREDIT.equals(_config)) {
                        accInfo.setDocLink(documentInfo.getInstance());
                    }
                }
                for (final AccountInfo accInfo : documentInfo.getDebitAccounts()) {
                    ret.add(ret.getDebitAccounts(), accInfo, true);
                    if (!SummarizeConfig.BOTH.equals(_config) && !SummarizeConfig.DEBIT.equals(_config)) {
                        accInfo.setDocLink(documentInfo.getInstance());
                    }
                }
            }
        }
        return ret;
    }

    /**
     * Gets the map4 substitutor.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return the map4 substitutor
     * @throws EFapsException on error
     */
    public final Map<String, String> getMap4Substitutor(final Parameter _parameter)
        throws EFapsException
    {
        final Map<String, String> ret = new HashMap<>();
        final String transdateStr = _parameter.getParameterValue("date");
        final DateTime transdate;
        if (transdateStr == null) {
            if (_parameter.getParameterValue("date_eFapsDate") != null) {
                transdate = DateUtil.getDateFromParameter(_parameter.getParameterValue("date_eFapsDate"));
            } else {
                transdate = new DateTime();
            }
        } else {
            transdate = new DateTime(transdateStr);
        }

        ret.put(Accounting.SubstitutorKeys.TRANSACTION_DATE.name(), transdate.toString(DateTimeFormat.mediumDate()
                        .withLocale(Context.getThreadContext().getLocale())));

        ret.put(Accounting.SubstitutorKeys.DOCUMENT_DATE.name(), getDate() == null
                        ? "" : getDate().toString(DateTimeFormat.mediumDate().withLocale(
                                        Context.getThreadContext().getLocale())));

        if (getInstance() != null && getInstance().isValid()) {
            ret.put(Accounting.SubstitutorKeys.DOCUMENT_TYPE.name(), getInstance().getType().getLabel());
            if (getInstance().getType().isKindOf(CIERP.DocumentAbstract)) {
                final PrintQuery print = new PrintQuery(getInstance());
                print.addAttribute(CIERP.DocumentAbstract.Name);
                print.executeWithoutAccessCheck();
                ret.put(Accounting.SubstitutorKeys.DOCUMENT_NAME.name(),
                                print.<String>getAttribute(CIERP.DocumentAbstract.Name));
            }
        } else {
            ret.put(Accounting.SubstitutorKeys.DOCUMENT_TYPE.name(), "");
            ret.put(Accounting.SubstitutorKeys.DOCUMENT_NAME.name(), "");
        }
        return ret;
    }
}
