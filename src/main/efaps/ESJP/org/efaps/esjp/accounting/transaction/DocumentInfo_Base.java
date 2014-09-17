/*
 * Copyright 2003 - 2014 The eFaps Team
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
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.text.StrSubstitutor;
import org.efaps.admin.datamodel.Classification;
import org.efaps.admin.dbproperty.DBProperties;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.program.esjp.EFapsRevision;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.CachedPrintQuery;
import org.efaps.db.Context;
import org.efaps.db.Instance;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.PrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.esjp.accounting.Case;
import org.efaps.esjp.accounting.Period;
import org.efaps.esjp.accounting.util.Accounting;
import org.efaps.esjp.accounting.util.Accounting.SummarizeConfig;
import org.efaps.esjp.accounting.util.AccountingSettings;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.ci.CISales;
import org.efaps.esjp.erp.CurrencyInst;
import org.efaps.esjp.erp.NumberFormatter;
import org.efaps.esjp.erp.RateInfo;
import org.efaps.util.EFapsException;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 * @version $Id: DocumentInfo_Base.java 13528 2014-08-04 20:06:28Z
 *          jan@moxter.net $
 */
@EFapsUUID("f6508096-b3a3-4b19-9b10-0290ad0571a6")
@EFapsRevision("$Rev$")
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
     * List of TargetAccounts for debit.
     */
    private final Set<AccountInfo> debitAccounts = new LinkedHashSet<AccountInfo>();

    /**
     * List of TargetAccounts for credit.
     */
    private final Set<AccountInfo> creditAccounts = new LinkedHashSet<AccountInfo>();

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
     * Mapping of classification id 2 amount.
     */
    private HashMap<Long, BigDecimal> clazz2Amount;

    /**
     * Summarize or not.
     */
    private SummarizeConfig config = SummarizeConfig.NONE;

    private final Set<Instance> docInsts = new HashSet<>();

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
     * @param _linkId id of the Classification the amount is wanted for
     * @return Amount
     * @throws EFapsException on error
     */
    public BigDecimal getAmount4Class(final Long _linkId)
        throws EFapsException
    {
        BigDecimal ret;
        if (isSumsDoc()) {
            if (this.clazz2Amount == null) {
                this.clazz2Amount = new HashMap<Long, BigDecimal>();
                final QueryBuilder queryBldr = new QueryBuilder(CISales.PositionAbstract);
                queryBldr.addWhereAttrEqValue(CISales.PositionAbstract.DocumentAbstractLink, this.instance.getId());
                final MultiPrintQuery multi = queryBldr.getPrint();
                final SelectBuilder sel = new SelectBuilder()
                                .linkto(CISales.PositionAbstract.Product).clazz().type();
                multi.addSelect(sel);
                multi.addAttribute(CISales.PositionSumAbstract.NetPrice);
                multi.execute();
                while (multi.next()) {
                    final BigDecimal posamount = multi
                                    .<BigDecimal>getAttribute(CISales.PositionSumAbstract.NetPrice);
                    final List<Classification> clazzes = multi.getSelect(sel);
                    if (clazzes != null) {
                        for (final Classification clazz : clazzes) {
                            Classification classTmp = clazz;
                            while (classTmp != null) {
                                BigDecimal currAmount;
                                if (this.clazz2Amount.containsKey(classTmp.getId())) {
                                    currAmount = this.clazz2Amount.get(classTmp.getId());
                                } else {
                                    currAmount = BigDecimal.ZERO;
                                }
                                this.clazz2Amount.put(classTmp.getId(), currAmount.add(posamount));
                                classTmp = classTmp.getParentClassification();
                            }
                        }
                    }
                }
            }
            ret = this.clazz2Amount.containsKey(_linkId) ? this.clazz2Amount.get(_linkId) : BigDecimal.ZERO;
        } else {
            ret = BigDecimal.ZERO;
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
     * @param _accounts accounts to add to
     * @param _accInfo the new account
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
                if (acc.getInstance().equals(_accInfo.getInstance()) && acc.getRateInfo().getCurrencyInstance()
                                .equals(_accInfo.getRateInfo().getCurrencyInstance())) {
                    acc.addAmount(_accInfo.getAmount());
                    add = false;
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
     * @return the sum of credit accounts in base currency
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
     * @return the sum of all debit accounts in base currency
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
     * @return the sum of all debit accounts formated
     * @throws EFapsException on error
     */
    public String getDebitSumFormated(final Parameter _parameter)
        throws EFapsException
    {
        return getFormater().format(getDebitSum(_parameter));
    }

    /**
     * @return the difference between the sum of debit accounts and the sum of
     *         credit accounts
     */
    public BigDecimal getDifference(final Parameter _parameter)
        throws EFapsException
    {
        return getDebitSum(_parameter).subtract(getCreditSum(_parameter)).abs();
    }

    /**
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
     * @return true if valid, else false
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
     * @param _summarize value for instance variable {@link #summarize}
     */
    public void setSummarizeConfig(final SummarizeConfig _config)
    {
        this.config = _config;
    }

    /**
     * @param _parameter
     * @return
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
            labelStr = DBProperties.getProperty(DocumentInfo.class.getName() + "." + getInstance().getType().getName()
                            + ".description");
        }

        final StrSubstitutor sub = new StrSubstitutor(getMap4Substitutor(_parameter));
        return sub.replace(labelStr);
    }

    public final Map<String, String> getMap4Substitutor(final Parameter _parameter)
        throws EFapsException
    {
        final Map<String, String> ret = new HashMap<String, String>();
        final String transdateStr = _parameter.getParameterValue("date");
        DateTime transdate;
        if (transdateStr == null) {
            transdate = new DateTime();
        } else {
            transdate = new DateTime(transdateStr);
        }

        ret.put("date", transdate.toString(DateTimeFormat.mediumDate().withLocale(
                        Context.getThreadContext().getLocale())));
        return ret;
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
     * @param _docInst
     */
    public void addDocInst(final Instance _docInst)
    {
        this.docInsts.add(_docInst);
    }

    /**
     * @_include include the instance of this info also
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
     * @param _docInst
     * @return
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
     * @return
     */
    public BigDecimal getRate(final Parameter _parameter)
        throws EFapsException
    {
        return RateInfo.getRate(_parameter, getRateInfo(), getRatePropKey());
    }

    /**
     * @return
     */
    public String getRatePropKey()
    {
        String ret = "";
        if (getInstance() != null && getInstance().isValid()) {
            ret = getInstance().getType().getName();
        }
        return ret;
    }

    /**
     * Setter method for instance variable {@link #rounding}.
     *
     * @param _rounding value for instance variable {@link #rounding}
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
            boolean debit;
            if (diffMax.compareTo(diff.abs()) > 0) {
                debit = diff.compareTo(BigDecimal.ZERO) < 0;
                AccountInfo accInfo;
                if (debit) {
                    accInfo = AccountInfo_Base.get4Config(_parameter, AccountingSettings.PERIOD_ROUNDINGDEBIT);
                } else {
                    accInfo = AccountInfo_Base.get4Config(_parameter, AccountingSettings.PERIOD_ROUNDINGCREDIT);
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

    protected static DocumentInfo getCombined(final Collection<DocumentInfo> _docInfos,
                                              final SummarizeConfig _config)
        throws EFapsException
    {
        DocumentInfo ret;
        if (_docInfos.size() == 1) {
            ret = _docInfos.iterator().next();
        } else {
            ret = new DocumentInfo();
            ret.setRateInfo(_docInfos.iterator().next().getRateInfo());
            ret.setSummarizeConfig(_config);
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
}
