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

import org.efaps.admin.program.esjp.EFapsRevision;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.CachedPrintQuery;
import org.efaps.db.Instance;
import org.efaps.db.PrintQuery;
import org.efaps.esjp.accounting.Account_Base;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.erp.NumberFormatter;
import org.efaps.esjp.erp.RateInfo;
import org.efaps.util.EFapsException;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 * @version $Id$
 */
@EFapsUUID("53f324b2-5933-43f7-a06d-91f830b86d4a")
@EFapsRevision("$Rev$")
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
    private StringBuilder link;

    private Instance docLink;

    /**
     *
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
     */

    public void setAmount(final BigDecimal _amount)
    {
        this.amount = _amount;
    }

    /**
     * Setter method for instance variable {@link #currInstance}.
     *
     * @param _currInstance value for instance variable {@link #currInstance}
     */

    public void setCurrInstance(final Instance _currInstance)
    {
        this.currInstance = _currInstance;
    }

    /**
     * Getter method for the instance variable {@link #amountRate}.
     *
     * @return value of instance variable {@link #amountRate}
     */
    public BigDecimal getAmountRate()
    {
        if (this.amountRate == null && this.amount != null && this.rateInfo != null) {
            this.amountRate = this.amount.setScale(12, BigDecimal.ROUND_HALF_UP)
                            .divide(getRateInfo().getRate(), BigDecimal.ROUND_HALF_UP);
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
     * @return amunt rate formated
     * @throws EFapsException on error
     */
    public String getAmountRateFormated()
        throws EFapsException
    {
        return NumberFormatter.get().getFormatter(2, 2).format(getAmountRate());
    }

    /**
     * Getter method for the instance variable {@link #link}.
     *
     * @return value of instance variable {@link #link}
     */
    public StringBuilder getLink()
    {
        return this.link;
    }

    /**
     * Setter method for instance variable {@link #link}.
     *
     * @param _link value for instance variable {@link #link}
     * @return this for chaining
     */
    public AccountInfo setLink(final StringBuilder _link)
    {
        this.link = _link;
        return (AccountInfo) this;
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
     * @return this for chaining
     */
    public AccountInfo setRateInfo(final RateInfo _rateInfo)
    {
        this.rateInfo = _rateInfo;
        return (AccountInfo) this;
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
     * @param _docInfo value for instance variable {@link #docLink}
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
            final PrintQuery print = new CachedPrintQuery(getInstance(), Account_Base.CACHEKEY);
            print.addAttribute(CIAccounting.AccountAbstract.Name, CIAccounting.AccountAbstract.Description);
            print.execute();
            this.description = print.getAttribute(CIAccounting.AccountAbstract.Description);
            this.name = print.getAttribute(CIAccounting.AccountAbstract.Name);
        }
    }
}
