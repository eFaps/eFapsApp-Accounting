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

package org.efaps.esjp.accounting;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.efaps.admin.datamodel.Classification;
import org.efaps.admin.datamodel.Type;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.program.esjp.EFapsRevision;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.Instance;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.PrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.esjp.accounting.util.Accounting.Account2CaseConfig;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.util.EFapsException;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 * @version $Id$
 */
@EFapsUUID("d1eefab1-b171-4c24-b190-3da41369fe30")
@EFapsRevision("$Rev$")
public abstract class Account2CaseInfo_Base
{

    private Instance instance;

    private Instance accountInstance;

    private Instance currencyInstance;

    private List<Account2CaseConfig> configs;

    private Integer denominator;

    private Integer numerator;

    private Long linkId;

    private Integer order;

    private BigDecimal amount;

    /**
     * Getter method for the instance variable {@link #type}.
     *
     * @return value of instance variable {@link #type}
     */
    public Type getType()
    {
        return getInstance().getType();
    }

    /**
     * Getter method for the instance variable {@link #configs}.
     *
     * @return value of instance variable {@link #configs}
     */
    public List<Account2CaseConfig> getConfigs()
    {
        return this.configs;
    }

    /**
     * Setter method for instance variable {@link #configs}.
     *
     * @param _configs value for instance variable {@link #configs}
     */
    public void setConfigs(final List<Account2CaseConfig> _configs)
    {
        this.configs = _configs;
    }

    /**
     * Getter method for the instance variable {@link #accountInstance}.
     *
     * @return value of instance variable {@link #accountInstance}
     */
    public Instance getAccountInstance()
    {
        return this.accountInstance;
    }

    /**
     * Setter method for instance variable {@link #accountInstance}.
     *
     * @param _accountInstance value for instance variable
     *            {@link #accountInstance}
     */
    public void setAccountInstance(final Instance _accountInstance)
    {
        this.accountInstance = _accountInstance;
    }

    /**
     * Getter method for the instance variable {@link #currencyInstance}.
     *
     * @return value of instance variable {@link #currencyInstance}
     */
    public Instance getCurrencyInstance()
    {
        return this.currencyInstance;
    }

    /**
     * Setter method for instance variable {@link #currencyInstance}.
     *
     * @param _currencyInstance value for instance variable
     *            {@link #currencyInstance}
     */
    public void setCurrencyInstance(final Instance _currencyInstance)
    {
        this.currencyInstance = _currencyInstance;
    }

    /**
     * Getter method for the instance variable {@link #denominator}.
     *
     * @return value of instance variable {@link #denominator}
     */
    public Integer getDenominator()
    {
        return this.denominator;
    }

    /**
     * Setter method for instance variable {@link #denominator}.
     *
     * @param _denominator value for instance variable {@link #denominator}
     */
    public void setDenominator(final Integer _denominator)
    {
        this.denominator = _denominator;
    }

    /**
     * Getter method for the instance variable {@link #numerator}.
     *
     * @return value of instance variable {@link #numerator}
     */
    public Integer getNumerator()
    {
        return this.numerator;
    }

    /**
     * Setter method for instance variable {@link #numerator}.
     *
     * @param _numerator value for instance variable {@link #numerator}
     */
    public void setNumerator(final Integer _numerator)
    {
        this.numerator = _numerator;
    }

    /**
     * Getter method for the instance variable {@link #linkId}.
     *
     * @return value of instance variable {@link #linkId}
     */
    public Long getLinkId()
    {
        return this.linkId;
    }

    /**
     * Setter method for instance variable {@link #linkId}.
     *
     * @param _linkId value for instance variable {@link #linkId}
     */
    public void setLinkId(final Long _linkId)
    {
        this.linkId = _linkId;
    }

    public boolean isClassRelation()
    {
        return getType().equals(
                        CIAccounting.Account2CaseCredit4Classification.getType())
                        || getType().equals(CIAccounting.Account2CaseDebit4Classification.getType());
    }

    public boolean isCategoryProduct()
    {
        return getType().equals(
                        CIAccounting.Account2CaseCredit4CategoryProduct.getType())
                        || getType().equals(CIAccounting.Account2CaseDebit4CategoryProduct.getType());
    }

    public boolean isDefault()
    {
        return getConfigs() != null && getConfigs().contains(Account2CaseConfig.DEFAULTSELECTED);
    }

    public boolean isApplyLabel()
    {
        return getConfigs() != null && getConfigs().contains(Account2CaseConfig.APPLYLABEL);
    }

    /**
     * @return
     */
    public boolean isCredit()
    {
        return getType().isCIType(CIAccounting.Account2CaseCredit)
                        || getType().isCIType(CIAccounting.Account2CaseCredit4Classification)
                        || getType().isCIType(CIAccounting.Account2CaseCredit4CategoryProduct);
    }

    /**
     * @return
     */
    public boolean isDebit()
    {
        return !isCredit();
    }

    public boolean isCheckCurrency()
    {
        return getCurrencyInstance() != null && getCurrencyInstance().isValid();
    }

    protected static List<Account2CaseInfo> getStandards(final Parameter _parameter,
                                                         final Instance _caseInst)
        throws EFapsException
    {
        final List<Account2CaseInfo> ret = new ArrayList<>();
        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.Account2CaseDebit);
        queryBldr.addType(CIAccounting.Account2CaseCredit);
        queryBldr.addWhereAttrEqValue(CIAccounting.Account2CaseAbstract.ToCaseAbstractLink, _caseInst);
        final MultiPrintQuery multi = queryBldr.getPrint();
        final SelectBuilder selAccInst = new SelectBuilder()
                        .linkto(CIAccounting.Account2CaseAbstract.FromAccountAbstractLink).instance();
        final SelectBuilder selCurrInst = new SelectBuilder()
                        .linkto(CIAccounting.Account2CaseAbstract.CurrencyLink).instance();
        multi.addAttribute(CIAccounting.Account2CaseAbstract.Numerator,
                        CIAccounting.Account2CaseAbstract.Denominator,
                        CIAccounting.Account2CaseAbstract.LinkValue,
                        CIAccounting.Account2CaseAbstract.Config,
                        CIAccounting.Account2CaseAbstract.Order);
        multi.addSelect(selAccInst, selCurrInst);
        multi.execute();
        while (multi.next()) {
            final Account2CaseInfo acc2case = new Account2CaseInfo();
            acc2case.setInstance(multi.getCurrentInstance());
            acc2case.setConfigs(multi.<List<Account2CaseConfig>>getAttribute(CIAccounting.Account2CaseAbstract.Config));
            acc2case.setAccountInstance(multi.<Instance>getSelect(selAccInst));
            acc2case.setCurrencyInstance(multi.<Instance>getSelect(selCurrInst));
            acc2case.setDenominator(multi.<Integer>getAttribute(CIAccounting.Account2CaseAbstract.Denominator));
            acc2case.setNumerator(multi.<Integer>getAttribute(CIAccounting.Account2CaseAbstract.Numerator));
            acc2case.setLinkId(multi.<Long>getAttribute(CIAccounting.Account2CaseAbstract.LinkValue));
            acc2case.setOrder(multi.<Integer>getAttribute(CIAccounting.Account2CaseAbstract.Order));
            ret.add(acc2case);
        }
        return ret;
    }

    /**
     * @param _parameter
     * @param _caseInst
     * @param _productInstance
     * @return
     */
    protected static Account2CaseInfo get4Product(final Parameter _parameter,
                                                  final Instance _caseInst,
                                                  final Instance _productInstance)
        throws EFapsException
    {
        Account2CaseInfo ret = null;
        // first priority is CategoryProduct and then Classification
        final QueryBuilder prodAttrQueryBldr = new QueryBuilder(CIAccounting.CategoryProduct2Product);
        prodAttrQueryBldr.addWhereAttrEqValue(CIAccounting.CategoryProduct2Product.ToLink, _productInstance);

        final QueryBuilder attrQueryBldr = new QueryBuilder(CIAccounting.CategoryProduct);
        attrQueryBldr.addWhereAttrInQuery(CIAccounting.CategoryProduct.ID,
                        prodAttrQueryBldr.getAttributeQuery(CIAccounting.CategoryProduct2Product.FromLink));
        attrQueryBldr.addWhereAttrEqValue(CIAccounting.CategoryProduct.PeriodAbstractLink,
                        new Period().evaluateCurrentPeriod(_parameter));

        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.Account2CaseCredit4CategoryProduct);
        queryBldr.addType(CIAccounting.Account2CaseDebit4CategoryProduct);
        queryBldr.addWhereAttrEqValue(CIAccounting.Account2CaseAbstract.ToCaseAbstractLink, _caseInst);
        queryBldr.addWhereAttrInQuery(CIAccounting.Account2CaseAbstract.LinkValue,
                        attrQueryBldr.getAttributeQuery(CIAccounting.CategoryProduct.ID));
        ret = getAccount2CaseInfo(queryBldr);

        if (ret == null) {
            final PrintQuery print = new PrintQuery(_productInstance);
            final SelectBuilder sel = new SelectBuilder().clazz().type();
            print.addSelect(sel);
            print.execute();
            final List<Classification> clazzes = print.getSelect(sel);
            if (clazzes != null) {
                for (final Classification clazz : clazzes) {
                    Classification classTmp = clazz;
                    while (classTmp != null && ret == null) {
                        final QueryBuilder clazzQueryBldr = new QueryBuilder(
                                        CIAccounting.Account2CaseCredit4Classification);
                        clazzQueryBldr.addType(CIAccounting.Account2CaseDebit4Classification);
                        clazzQueryBldr.addWhereAttrEqValue(CIAccounting.Account2CaseAbstract.ToCaseAbstractLink,
                                        _caseInst);
                        clazzQueryBldr.addWhereAttrEqValue(CIAccounting.Account2CaseCredit4Classification.LinkValue,
                                        classTmp.getId());
                        ret = getAccount2CaseInfo(clazzQueryBldr);
                        classTmp = classTmp.getParentClassification();
                    }
                }
            }
        }
        return ret;
    }

    protected static Account2CaseInfo getAccount2CaseInfo(final QueryBuilder _queryBldr)
        throws EFapsException
    {
        Account2CaseInfo ret = null;
        final MultiPrintQuery multi = _queryBldr.getPrint();
        final SelectBuilder selAccInst = new SelectBuilder()
                        .linkto(CIAccounting.Account2CaseAbstract.FromAccountAbstractLink).instance();
        final SelectBuilder selCurrInst = new SelectBuilder()
                        .linkto(CIAccounting.Account2CaseAbstract.CurrencyLink).instance();
        multi.addAttribute(CIAccounting.Account2CaseAbstract.Numerator,
                        CIAccounting.Account2CaseAbstract.Denominator,
                        CIAccounting.Account2CaseAbstract.LinkValue,
                        CIAccounting.Account2CaseAbstract.Config,
                        CIAccounting.Account2CaseAbstract.Order);
        multi.addSelect(selAccInst, selCurrInst);
        multi.execute();
        if (multi.next()) {
            ret = new Account2CaseInfo();
            ret.setInstance(multi.getCurrentInstance());
            ret.setConfigs(multi.<List<Account2CaseConfig>>getAttribute(CIAccounting.Account2CaseAbstract.Config));
            ret.setAccountInstance(multi.<Instance>getSelect(selAccInst));
            ret.setCurrencyInstance(multi.<Instance>getSelect(selCurrInst));
            ret.setDenominator(multi.<Integer>getAttribute(CIAccounting.Account2CaseAbstract.Denominator));
            ret.setNumerator(multi.<Integer>getAttribute(CIAccounting.Account2CaseAbstract.Numerator));
            ret.setLinkId(multi.<Long>getAttribute(CIAccounting.Account2CaseAbstract.LinkValue));
            ret.setOrder(multi.<Integer>getAttribute(CIAccounting.Account2CaseAbstract.Order));
        }
        return ret;
    }

    /**
     * Getter method for the instance variable {@link #order}.
     *
     * @return value of instance variable {@link #order}
     */
    public Integer getOrder()
    {
        return this.order;
    }

    /**
     * Setter method for instance variable {@link #order}.
     *
     * @param _order value for instance variable {@link #order}
     */
    public void setOrder(final Integer _order)
    {
        this.order = _order;
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

}