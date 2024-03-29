/*
 * Copyright © 2003 - 2024 The eFaps Team (-)
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
 */
package org.efaps.esjp.accounting.transaction;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Properties;

import org.apache.commons.text.StringEscapeUtils;
import org.efaps.admin.dbproperty.DBProperties;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.CachedPrintQuery;
import org.efaps.db.Instance;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.PrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.esjp.accounting.Account;
import org.efaps.esjp.accounting.Period;
import org.efaps.esjp.accounting.util.Accounting;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.erp.NumberFormatter;
import org.efaps.esjp.erp.RateInfo;
import org.efaps.util.EFapsException;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 */
@EFapsUUID("53f324b2-5933-43f7-a06d-91f830b86d4a")
@EFapsApplication("eFapsApp-Accounting")
public abstract class AccountInfo_Base
{

    /**
     * Instance of this account.
     */
    private Instance instance;

    /**
     * Name of this account.
     */
    private String name;

    /**
     * Description for this account.
     */
    private String description;

    /**
     * Amount of this account. The Part editable for through the User meaning
     * that it can be in other currencies than the base currency for the period.
     */
    private BigDecimal amount;

    /**
     * Rate.
     */
    private Instance currInstance;

    /**
     * RateInfo.
     */
    private RateInfo rateInfo;

    /**
     * The Amount of this account converted into the base currency for this
     * period by applying the exchange rate.
     */
    private BigDecimal amountRate;

    /**
     * Link string.
     */
    private StringBuilder linkDebitHtml;

    /**
     * Link string.
     */
    private StringBuilder linkCreditHtml;

    /** The doc link. */
    private Instance docLink;

    /** The post fix. */
    private String postFix;

    /** The rate prop key. */
    private String ratePropKey;

    /** The label inst. */
    private Instance labelInst;

    /** The remark. */
    private String remark;

    /**
     * Instantiates a new account info_ base.
     */
    protected AccountInfo_Base()
    {
        this(null);
    }

    /**
     * @param _instance Instance.
     */
    protected AccountInfo_Base(final Instance _instance)
    {
        this(_instance, null);
    }

    /**
     * new TargetAccount.
     *
     * @param _instance Instance.
     * @param _amount amount.
     */
    protected AccountInfo_Base(final Instance _instance,
                               final BigDecimal _amount)
    {
        this.instance = _instance;
        this.amount = _amount == null ? BigDecimal.ZERO : _amount;
    }

    /**
     * add amount for constructor TargetAccount.
     *
     * @param _amount amount.
     * @return this.
     */
    public AccountInfo addAmount(final BigDecimal _amount)
    {
        this.amount = this.amount.add(_amount);
        return (AccountInfo) this;
    }

    /**
     * add amount for constructor TargetAccount.
     *
     * @param _amount amount.
     * @return this.
     */
    public AccountInfo addAmountRate(final BigDecimal _amount)
    {
        if (this.amountRate == null) {
            this.amountRate = BigDecimal.ZERO;
        }
        this.amountRate = this.amountRate.add(_amount);
        return (AccountInfo) this;
    }

    /**
     * Getter method for instance variable {@link #oid}.
     *
     * @return value of instance variable {@link #oid}
     */
    public Instance getInstance()
    {
        if (this.instance == null) {
            this.instance = Instance.get("");
        }
        return this.instance;
    }

    /**
     * Getter method for instance variable {@link #name}.
     *
     * @return value of instance variable {@link #name}
     * @throws EFapsException on error
     */
    public String getName()
        throws EFapsException
    {
        init();
        return this.name;
    }

    /**
     * Getter method for instance variable {@link #description}.
     *
     * @return value of instance variable {@link #description}
     * @throws EFapsException on error
     */
    public String getDescription()
        throws EFapsException
    {
        init();
        return this.description;
    }

    /**
     * Getter method for instance variable {@link #amount}.
     *
     * @return value of instance variable {@link #amount}
     */
    public BigDecimal getAmount()
    {
        return this.amount;
    }

    /**
     * Getter method for instance variable {@link #currInstance}.
     *
     * @return value of instance variable {@link #currInstance}
     */
    public Instance getCurrInstance()
    {
        return this.currInstance;
    }

    /**
     * @return the amount formated
     * @throws EFapsException on error
     */
    public String getAmountFormated()
        throws EFapsException
    {
        return NumberFormatter.get().getFormatter(2, 2).format(getAmount());
    }

    /**
     * Setter method for instance variable {@link #amount}.
     *
     * @param _amount value for instance variable {@link #amount}
     * @return the account info
     */
    public AccountInfo setAmount(final BigDecimal _amount)
    {
        this.amount = _amount;
        return (AccountInfo) this;
    }

    /**
     * Setter method for instance variable {@link #currInstance}.
     *
     * @param _currInstance value for instance variable {@link #currInstance}
     * @return the account info
     */
    public AccountInfo setCurrInstance(final Instance _currInstance)
    {
        this.currInstance = _currInstance;
        return (AccountInfo) this;
    }

    /**
     * Getter method for the instance variable {@link #amountRate}.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return value of instance variable {@link #amountRate}
     * @throws EFapsException on error
     */
    public BigDecimal getAmountRate(final Parameter _parameter)
        throws EFapsException
    {
        if (this.amountRate == null && this.amount != null && this.rateInfo != null) {
            this.amountRate = this.amount.setScale(12, BigDecimal.ROUND_HALF_UP)
                            .divide(getRate(_parameter), BigDecimal.ROUND_HALF_UP);
        }
        return this.amountRate;
    }

    /**
     * Setter method for instance variable {@link #amountRate}.
     *
     * @param _amountRate value for instance variable {@link #amountRate}
     */
    public void setAmountRate(final BigDecimal _amountRate)
    {
        this.amountRate = _amountRate;
    }

    /**
     * Gets the amount rate formated.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return amunt rate formated
     * @throws EFapsException on error
     */
    public String getAmountRateFormated(final Parameter _parameter)
        throws EFapsException
    {
        return NumberFormatter.get().getFormatter(2, 2).format(getAmountRate(_parameter));
    }

    /**
     * Getter method for the instance variable {@link #link}.
     *
     * @return value of instance variable {@link #link}
     * @throws EFapsException on error
     */
    public StringBuilder getLinkCreditHtml()
        throws EFapsException
    {
        initLinkHtml();
        return this.linkCreditHtml;
    }

    /**
     * Getter method for the instance variable {@link #link}.
     *
     * @return value of instance variable {@link #link}
     * @throws EFapsException on error
     */
    public StringBuilder getLinkDebitHtml()
        throws EFapsException
    {
        initLinkHtml();
        return this.linkDebitHtml;
    }

    /**
     * Inits the link html.
     *
     * @throws EFapsException on error
     */
    protected void initLinkHtml()
        throws EFapsException
    {
        if (this.linkCreditHtml == null && getInstance() != null && getInstance().isValid()) {
            this.linkCreditHtml = new StringBuilder();
            this.linkDebitHtml = new StringBuilder();
            final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.Account2AccountAbstract);
            queryBldr.addWhereAttrEqValue(CIAccounting.Account2AccountAbstract.FromAccountLink, getInstance());
            final MultiPrintQuery multi = queryBldr.getPrint();
            multi.addAttribute(CIAccounting.Account2AccountAbstract.Numerator,
                            CIAccounting.Account2AccountAbstract.Denominator,
                            CIAccounting.Account2AccountAbstract.Config);
            final SelectBuilder sel = SelectBuilder.get().linkto(CIAccounting.Account2AccountAbstract.ToAccountLink)
                            .attribute(CIAccounting.AccountAbstract.Name);
            multi.addSelect(sel);
            multi.execute();
            final StringBuilder tmpBldr = new StringBuilder();
            while (multi.next()) {
                final String to = multi.<String>getSelect(sel);
                final Collection<Accounting.Account2AccountConfig> configs = multi
                                .getAttribute(CIAccounting.Account2AccountAbstract.Config);
                final Integer numerator = multi.<Integer>getAttribute(CIAccounting.Account2AccountAbstract.Numerator);
                final Integer denominator = multi
                                .<Integer>getAttribute(CIAccounting.Account2AccountAbstract.Denominator);
                final BigDecimal percent = new BigDecimal(numerator).divide(new BigDecimal(denominator),
                                BigDecimal.ROUND_HALF_UP).multiply(new BigDecimal(100));
                final Instance tmpInstance = multi.getCurrentInstance();
                if (configs != null && configs.contains(Accounting.Account2AccountConfig.DEACTIVATABLE)) {
                    tmpBldr.append("<input type='checkbox' name='acc2acc").append(getPostFix())
                                    .append("' checked='checked' value='").append(tmpInstance.getOid()).append("'/>");
                }
                tmpBldr.append(DBProperties.getFormatedDBProperty(
                                Transaction.class.getName() + ".LinkString4" + tmpInstance.getType().getName(),
                                new Object[] { percent, StringEscapeUtils.escapeEcmaScript(to) }));
                if (configs != null && configs.contains(Accounting.Account2AccountConfig.APPLY4DEBIT)) {
                    this.linkDebitHtml = tmpBldr;
                }
                if (configs != null && configs.contains(Accounting.Account2AccountConfig.APPLY4CREDIT)) {
                    this.linkCreditHtml = tmpBldr;
                }
            }
        }
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
     * @param _ratePropKey the rate prop key
     * @return this for chaining
     */
    public AccountInfo setRateInfo(final RateInfo _rateInfo,
                                   final String _ratePropKey)
    {
        this.rateInfo = _rateInfo;
        this.ratePropKey = _ratePropKey;
        return (AccountInfo) this;
    }

    /**
     * Gets the rate object.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return the rate object
     * @throws EFapsException on error
     */
    public Object[] getRateObject(final Parameter _parameter)
        throws EFapsException
    {
        return RateInfo.getRateObject(_parameter, getRateInfo(), getRatePropKey());
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
     * Gets the rate ui frmt.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return the rate ui frmt
     * @throws EFapsException on error
     */
    public String getRateUIFrmt(final Parameter _parameter)
        throws EFapsException
    {
        return RateInfo.getRateUIFrmt(_parameter, getRateInfo(), getRatePropKey());
    }

    /**
     * Getter method for the instance variable {@link #ratePropKey}.
     *
     * @return value of instance variable {@link #ratePropKey}
     */
    public String getRatePropKey()
    {
        return this.ratePropKey;
    }

    /**
     * Setter method for instance variable {@link #instance}.
     *
     * @param _instance value for instance variable {@link #instance}
     * @return this for chaining
     */
    public AccountInfo setInstance(final Instance _instance)
    {
        this.instance = _instance;
        return (AccountInfo) this;
    }

    /**
     * Setter method for instance variable {@link #name}.
     *
     * @param _name value for instance variable {@link #name}
     * @return this for chaining
     */
    public AccountInfo setName(final String _name)
    {
        this.name = _name;
        return (AccountInfo) this;
    }

    /**
     * Setter method for instance variable {@link #description}.
     *
     * @param _description value for instance variable {@link #description}
     * @return this for chaining
     */
    public AccountInfo setDescription(final String _description)
    {
        this.description = _description;
        return (AccountInfo) this;
    }

    /**
     * Getter method for the instance variable {@link #docInfo}.
     *
     * @return value of instance variable {@link #docInfo}
     */
    public Instance getDocLink()
    {
        return this.docLink;
    }

    /**
     * Setter method for instance variable {@link #docLink}.
     *
     * @param _docLink the doc link
     * @return the account info
     */
    public AccountInfo setDocLink(final Instance _docLink)
    {
        this.docLink = _docLink;
        return (AccountInfo) this;
    }

    /**
     * Init.
     *
     * @throws EFapsException on error
     */
    protected void init()
        throws EFapsException
    {
        if ((this.name == null || this.description == null) && getInstance().isValid()) {
            final PrintQuery print = new CachedPrintQuery(getInstance(), Account.CACHEKEY);
            print.addAttribute(CIAccounting.AccountAbstract.Name, CIAccounting.AccountAbstract.Description);
            print.execute();
            this.description = print.getAttribute(CIAccounting.AccountAbstract.Description);
            this.name = print.getAttribute(CIAccounting.AccountAbstract.Name);
        }
    }

    /**
     * Getter method for the instance variable {@link #postFix}.
     *
     * @return value of instance variable {@link #postFix}
     */
    public String getPostFix()
    {
        return this.postFix;
    }


    /**
     * Setter method for instance variable {@link #postFix}.
     *
     * @param _postFix value for instance variable {@link #postFix}
     */
    public void setPostFix(final String _postFix)
    {
        this.postFix = _postFix;
    }

    /**
     * Getter method for the instance variable {@link #labelInst}.
     *
     * @return value of instance variable {@link #labelInst}
     */
    public Instance getLabelInst()
    {
        return this.labelInst;
    }

    /**
     * Setter method for instance variable {@link #labelInst}.
     *
     * @param _labelInst value for instance variable {@link #labelInst}
     * @return the account info
     */
    public AccountInfo setLabelInst(final Instance _labelInst)
    {
        this.labelInst = _labelInst;
        return (AccountInfo) this;
    }

    /**
     * Getter method for the instance variable {@link #remark}.
     *
     * @return value of instance variable {@link #remark}
     */
    public String getRemark()
    {
        return this.remark;
    }

    /**
     * Setter method for instance variable {@link #remark}.
     *
     * @param _remark value for instance variable {@link #remark}
     * @return the account info
     */
    public AccountInfo setRemark(final String _remark)
    {
        this.remark = _remark;
        return (AccountInfo) this;
    }

    /**
     * Gets the info for a config.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @param _key the key
     * @return the 4 config
     * @throws EFapsException on error
     */
    protected static AccountInfo get4Config(final Parameter _parameter,
                                            final String _key)
        throws EFapsException
    {
        final Instance periodInst = Period.evalCurrent(_parameter);
        AccountInfo ret = null;
        final Properties props = Accounting.getSysConfig().getObjectAttributeValueAsProperties(periodInst);
        final String name = props.getProperty(_key);
        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.AccountAbstract);
        queryBldr.addWhereAttrEqValue(CIAccounting.AccountAbstract.Name, name);
        queryBldr.addWhereAttrEqValue(CIAccounting.AccountAbstract.PeriodAbstractLink, periodInst);
        final MultiPrintQuery multi = queryBldr.getCachedPrint(Account.CACHEKEY);
        multi.executeWithoutAccessCheck();
        while (multi.next()) {
            ret = new AccountInfo(multi.getCurrentInstance());
        }
        return ret;
    }
}
