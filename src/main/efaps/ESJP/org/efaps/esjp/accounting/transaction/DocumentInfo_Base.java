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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.efaps.admin.datamodel.Classification;
import org.efaps.admin.program.esjp.EFapsRevision;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.Context;
import org.efaps.db.Instance;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.esjp.ci.CISales;
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
 * @version $Id$
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
     * Is this a Stock moving Document.
     */
    private boolean stockDoc;

    /**
     * Did the Document pass the validation of the cost.
     * (Every product has its cost assigned)
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
    private boolean summarize = true;

    /**
     * Constructor.
     */
    public DocumentInfo_Base()
    {
        this(null);
    }

    /**
     * @param _instance Instance of the Document
     */
    public DocumentInfo_Base(final Instance _instance)
    {
        setInstance(_instance);
    }

    /**
     * @param _linkId   id of the Classification the amount is wanted for
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
        return this.invert ?  this.creditAccounts : this.debitAccounts;
    }

    /**
     * @param _accInfo acinfo
     * @return this
     * @throws EFapsException on error
     */
    public DocumentInfo addDebit(final AccountInfo _accInfo)
        throws EFapsException
    {
        add(this.debitAccounts, _accInfo);
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
        add(this.creditAccounts, _accInfo);
        return (DocumentInfo) this;
    }

    /**
     * @param _accounts accounts to add to
     * @param _accInfo the new account
     * @throws EFapsException on error
     */
    protected void add(final Set<AccountInfo> _accounts,
                       final AccountInfo _accInfo)
        throws EFapsException
    {
        if (_accInfo.getRateInfo() == null) {
            if (getRateInfo() != null) {
                _accInfo.setRateInfo(getRateInfo());
            } else {
                _accInfo.setRateInfo(RateInfo.getDummyRateInfo());
            }
        }
        boolean add = true;
        if (isSummarize()) {
            for (final AccountInfo acc : _accounts) {
                if (acc.getInstance().equals(_accInfo.getInstance()) && acc.getRateInfo().getInstance4Currency()
                                .equals(_accInfo.getRateInfo().getInstance4Currency())) {
                    acc.add(_accInfo.getAmount());
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
        return this.rateInfo.getCurrencyInst().getSymbol();
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
        return this.rateInfo.getInstance4Currency();
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
     * @return the sum of credit accounts
     */
    public BigDecimal getCreditSum()
    {
        BigDecimal ret = BigDecimal.ZERO;
        for (final AccountInfo account : getCreditAccounts()) {
            ret = ret.add(account.getAmountRate().setScale(2, BigDecimal.ROUND_HALF_UP));
        }
        return ret;
    }

    /**
     * @return the sum of credit accounts formated
     * @throws EFapsException on error
     */
    public String getCreditSumFormated()
        throws EFapsException
    {
        return getFormater().format(getCreditSum());
    }

    /**
     * @return the sum of all debit accounts
     */
    public BigDecimal getDebitSum()
    {
        BigDecimal ret = BigDecimal.ZERO;
        for (final AccountInfo account : getDebitAccounts()) {
            ret = ret.add(account.getAmountRate().setScale(2, BigDecimal.ROUND_HALF_UP));
        }
        return ret;
    }

    /**
     * @return the sum of all debit accounts formated
     * @throws EFapsException on error
     */
    public String getDebitSumFormated()
        throws EFapsException
    {
        return getFormater().format(getDebitSum());
    }

    /**
     * @return the difference between the sum of debit accounts and the sum
     *         of credit accounts
     */
    public BigDecimal getDifference()
    {
        return getDebitSum().subtract(getCreditSum()).abs();
    }

    /**
     * @return the difference between the sum of debit accounts and the sum
     *         of credit accounts formated
     * @throws EFapsException on error
     */
    public String getDifferenceFormated()
        throws EFapsException
    {
        return getFormater().format(getDifference());
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
     * @return true if valid, else false
     */
    public boolean isValid()
    {
        return getDebitSum().compareTo(BigDecimal.ZERO) != 0 && getDebitSum().compareTo(getCreditSum()) == 0;
    }


    /**
     * Getter method for the instance variable {@link #summarize}.
     *
     * @return value of instance variable {@link #summarize}
     */
    public boolean isSummarize()
    {
        return this.summarize;
    }


    /**
     * Setter method for instance variable {@link #summarize}.
     *
     * @param _summarize value for instance variable {@link #summarize}
     */
    public void setSummarize(final boolean _summarize)
    {
        this.summarize = _summarize;
    }
}
