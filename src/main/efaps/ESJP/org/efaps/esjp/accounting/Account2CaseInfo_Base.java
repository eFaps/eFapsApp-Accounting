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
package org.efaps.esjp.accounting;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.text.StrSubstitutor;
import org.efaps.admin.datamodel.Classification;
import org.efaps.admin.datamodel.Type;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.CachedPrintQuery;
import org.efaps.db.Instance;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.PrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.esjp.accounting.util.Accounting;
import org.efaps.esjp.accounting.util.Accounting.Account2Case4AmountConfig;
import org.efaps.esjp.accounting.util.Accounting.Account2CaseConfig;
import org.efaps.esjp.accounting.util.AccountingSettings;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.ci.CIProducts;
import org.efaps.esjp.db.InstanceUtils;
import org.efaps.esjp.products.TreeView;
import org.efaps.util.EFapsException;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 */
@EFapsUUID("d1eefab1-b171-4c24-b190-3da41369fe30")
@EFapsApplication("eFapsApp-Accounting")
public abstract class Account2CaseInfo_Base
{

    /** The instance. */
    private Instance instance;

    /** The account instance. */
    private Instance accountInstance;

    /** The currency instance. */
    private Instance currencyInstance;

    /** The configs. */
    private List<Account2CaseConfig> configs;

    /** The amount config. */
    private Account2Case4AmountConfig amountConfig;

    /** The denominator. */
    private Integer denominator;

    /** The numerator. */
    private Integer numerator;

    /** The link id. */
    private Long linkId;

    /** The order. */
    private Integer order;

    /** The amount. */
    private BigDecimal amount;

    /** The key. */
    private String key;

    /** The remark. */
    private String remark;

    /** The deactivate currency check. */
    private boolean deactCurrencyCheck;

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
     * @return the account2 case info
     */
    public Account2CaseInfo setConfigs(final List<Account2CaseConfig> _configs)
    {
        this.configs = _configs;
        return (Account2CaseInfo) this;
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
     * @return the account2 case info
     */
    public Account2CaseInfo setAccountInstance(final Instance _accountInstance)
    {
        this.accountInstance = _accountInstance;
        return (Account2CaseInfo) this;
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
     * @return the account2 case info
     */
    public Account2CaseInfo setCurrencyInstance(final Instance _currencyInstance)
    {
        this.currencyInstance = _currencyInstance;
        return (Account2CaseInfo) this;
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
     * @return the account2 case info
     */
    public Account2CaseInfo setDenominator(final Integer _denominator)
    {
        this.denominator = _denominator;
        return (Account2CaseInfo) this;
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
     * @return the account2 case info
     */
    public Account2CaseInfo setNumerator(final Integer _numerator)
    {
        this.numerator = _numerator;
        return (Account2CaseInfo) this;
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
     * @return the account2 case info
     */
    public Account2CaseInfo setLinkId(final Long _linkId)
    {
        this.linkId = _linkId;
        return (Account2CaseInfo) this;
    }

    /**
     * Checks if is class relation.
     *
     * @return true, if is class relation
     */
    public boolean isClassRelation()
    {
        return getType().isCIType(CIAccounting.Account2CaseCredit4Classification)
                        || getType().isCIType(CIAccounting.Account2CaseDebit4Classification);
    }

    /**
     * Checks if is category product.
     *
     * @return true, if is category product
     */
    public boolean isCategoryProduct()
    {
        return getType().isCIType(CIAccounting.Account2CaseCredit4CategoryProduct)
                        || getType().isCIType(CIAccounting.Account2CaseDebit4CategoryProduct);
    }

    /**
     * Checks if is category product.
     *
     * @return true, if is category product
     */
    public boolean isTreeView()
    {
        return getType().isKindOf(CIAccounting.Account2Case4ProductTreeViewAbstract.getType());
    }

    /**
     * Checks if is default.
     *
     * @return true, if is default
     */
    public boolean isDefault()
    {
        return getConfigs() != null && getConfigs().contains(Account2CaseConfig.DEFAULTSELECTED);
    }

    /**
     * Checks if is apply label.
     *
     * @return true, if is apply label
     */
    public boolean isApplyLabel()
    {
        return getConfigs() != null && getConfigs().contains(Account2CaseConfig.APPLYLABEL);
    }

    /**
     * Checks if is apply label.
     *
     * @return true, if is apply label
     */
    public boolean isEvalRelation()
    {
        return getConfigs() != null && getConfigs().contains(Account2CaseConfig.EVALRELATION);
    }

    /**
     * Checks if is apply label.
     *
     * @return true, if is apply label
     */
    public boolean isSeparately()
    {
        return getConfigs() != null && getConfigs().contains(Account2CaseConfig.SEPARATELY);
    }

    /**
     * Checks if is credit.
     *
     * @return true, if is credit
     */
    public boolean isCredit()
    {
        return getType().isCIType(CIAccounting.Account2CaseCredit)
                        || getType().isCIType(CIAccounting.Account2CaseCredit4Key)
                        || getType().isCIType(CIAccounting.Account2CaseCredit4Classification)
                        || getType().isCIType(CIAccounting.Account2CaseCredit4CategoryProduct)
                        || getType().isCIType(CIAccounting.Account2CaseCredit4ProductTreeView);
    }

    /**
     * Checks if is debit.
     *
     * @return true, if is debit
     */
    public boolean isDebit()
    {
        return !isCredit();
    }

    /**
     * Checks if is check key.
     *
     * @return true, if is check key
     */
    public boolean isCheckKey()
    {
        return this.key != null && !this.key.isEmpty();
    }

    /**
     * Checks if is check currency.
     *
     * @return true, if is check currency
     */
    public boolean isCheckCurrency()
    {
        return this.deactCurrencyCheck ? false : getCurrencyInstance() != null && getCurrencyInstance().isValid();
    }

    /**
     * Setter method for instance variable {@link #deactivateCurrencyCheck}.
     *
     * @param _deactCurrencyCheck the deact currency check
     * @return the account two case info
     */
    public Account2CaseInfo setDeactCurrencyCheck(final boolean _deactCurrencyCheck)
    {
        this.deactCurrencyCheck = _deactCurrencyCheck;
        return (Account2CaseInfo) this;
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
     * @return the account2 case info
     */
    public Account2CaseInfo setOrder(final Integer _order)
    {
        this.order = _order;
        return (Account2CaseInfo) this;
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
     * @return the account2 case info
     */
    public Account2CaseInfo setInstance(final Instance _instance)
    {
        this.instance = _instance;
        return (Account2CaseInfo) this;
    }

    /**
     * Getter method for the instance variable {@link #amount}.
     *
     * @return value of instance variable {@link #amount}
     */
    public BigDecimal getAmount()
    {
        return this.amount == null ? BigDecimal.ZERO : this.amount;
    }

    /**
     * Setter method for instance variable {@link #amount}.
     *
     * @param _amount value for instance variable {@link #amount}
     * @return the account2 case info
     */
    public Account2CaseInfo setAmount(final BigDecimal _amount)
    {
        this.amount = _amount;
        return (Account2CaseInfo) this;
    }

    /**
     * Getter method for the instance variable {@link #key}.
     *
     * @return value of instance variable {@link #key}
     */
    public String getKey()
    {
        return this.key;
    }

    /**
     * Setter method for instance variable {@link #key}.
     *
     * @param _key value for instance variable {@link #key}
     * @return the account2 case info
     */
    public Account2CaseInfo setKey(final String _key)
    {
        this.key = _key;
        return (Account2CaseInfo) this;
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
     * @return the account2 case info
     */
    public Account2CaseInfo setRemark(final String _remark)
    {
        this.remark = _remark;
        return (Account2CaseInfo) this;
    }

    /**
     * Getter method for the instance variable {@link #amountConfig}.
     *
     * @return value of instance variable {@link #amountConfig}
     */
    public Account2Case4AmountConfig getAmountConfig()
    {
        return this.amountConfig;
    }

    /**
     * Setter method for instance variable {@link #amountConfig}.
     *
     * @param _amountConfig value for instance variable {@link #amountConfig}
     * @return the account two case info
     */
    public Account2CaseInfo setAmountConfig(final Account2Case4AmountConfig _amountConfig)
    {
        this.amountConfig = _amountConfig;
        return (Account2CaseInfo) this;
    }

    /**
     * Getter method for the instance variable {@link #deactCurrencyCheck}.
     *
     * @return value of instance variable {@link #deactCurrencyCheck}
     */
    public boolean isDeactCurrencyCheck()
    {
        return this.deactCurrencyCheck;
    }

    @Override
    public String toString()
    {
        return ToStringBuilder.reflectionToString(this);
    }

    /**
     * Gets the standards.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @param _caseInst the case inst
     * @return the standards
     * @throws EFapsException on error
     */
    protected static List<Account2CaseInfo> getStandards(final Parameter _parameter,
                                                         final Instance _caseInst)
        throws EFapsException
    {
        final List<Account2CaseInfo> ret = new ArrayList<>();
        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.Account2CaseDebit);
        queryBldr.addType(CIAccounting.Account2CaseCredit, CIAccounting.Account2CaseCredit4Key,
                        CIAccounting.Account2CaseDebit4Key);
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
                        CIAccounting.Account2CaseAbstract.AmountConfig,
                        CIAccounting.Account2CaseAbstract.Order,
                        CIAccounting.Account2CaseAbstract.Key,
                        CIAccounting.Account2CaseAbstract.Remark);
        multi.addSelect(selAccInst, selCurrInst);
        multi.execute();
        while (multi.next()) {
            final Account2CaseInfo acc2case = new Account2CaseInfo()
                    .setInstance(multi.getCurrentInstance())
                    .setConfigs(multi
                                    .<List<Account2CaseConfig>>getAttribute(CIAccounting.Account2CaseAbstract.Config))
                    .setAmountConfig(multi.getAttribute(CIAccounting.Account2CaseAbstract.AmountConfig))
                    .setAccountInstance(multi.<Instance>getSelect(selAccInst))
                    .setCurrencyInstance(multi.<Instance>getSelect(selCurrInst))
                    .setDenominator(multi.<Integer>getAttribute(CIAccounting.Account2CaseAbstract.Denominator))
                    .setNumerator(multi.<Integer>getAttribute(CIAccounting.Account2CaseAbstract.Numerator))
                    .setLinkId(multi.<Long>getAttribute(CIAccounting.Account2CaseAbstract.LinkValue))
                    .setOrder(multi.<Integer>getAttribute(CIAccounting.Account2CaseAbstract.Order))
                    .setKey(multi.<String>getAttribute(CIAccounting.Account2CaseAbstract.Key))
                    .setRemark(multi.<String>getAttribute(CIAccounting.Account2CaseAbstract.Remark));
            ret.add(acc2case);
        }
        return ret;
    }

    /**
     * Gets the 4 product.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @param _caseInst the case inst
     * @param _productInstance the product instance
     * @return the 4 product
     * @throws EFapsException on error
     */
    protected static Account2CaseInfo get4Product(final Parameter _parameter,
                                                  final Instance _caseInst,
                                                  final Instance _productInstance)
        throws EFapsException
    {
        Account2CaseInfo ret = null;
        // first priority is CategoryProduct then TreeView and then Classification
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
        ret = Account2CaseInfo_Base.getAccount2CaseInfo(queryBldr);

        if (ret == null) {
            final QueryBuilder tvQueryBldr = new QueryBuilder(CIAccounting.Account2Case4ProductTreeViewAbstract);
            tvQueryBldr.addWhereAttrEqValue(CIAccounting.Account2Case4ProductTreeViewAbstract.ToCaseAbstractLink,
                            _caseInst);
            final MultiPrintQuery tvMulti = tvQueryBldr.getPrint();
            final SelectBuilder selNodeInst = SelectBuilder.get()
                            .linkto(CIAccounting.Account2Case4ProductTreeViewAbstract.ProductTreeViewLink).instance();
            tvMulti.addSelect(selNodeInst);
            tvMulti.execute();

            int current = Integer.MAX_VALUE;
            Instance treeViewInst = null;
            while (tvMulti.next()) {
                final Instance nodeInst = tvMulti.getSelect(selNodeInst);
                final Set<Instance> products = TreeView.getProductDescendants(_parameter, nodeInst);
                if (products.contains(_productInstance) && products.size() < current) {
                    current = products.size();
                    treeViewInst = nodeInst;
                }
            }
            if (InstanceUtils.isValid(treeViewInst)) {
                final QueryBuilder ciQueryBldr = new QueryBuilder(CIAccounting.Account2Case4ProductTreeViewAbstract);
                ciQueryBldr.addWhereAttrEqValue(CIAccounting.Account2Case4ProductTreeViewAbstract.ToCaseAbstractLink,
                                _caseInst);
                ciQueryBldr.addWhereAttrEqValue(CIAccounting.Account2Case4ProductTreeViewAbstract.ProductTreeViewLink,
                                treeViewInst);
                ret = Account2CaseInfo_Base.getAccount2CaseInfo(ciQueryBldr);
            }
        }

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
                        ret = Account2CaseInfo_Base.getAccount2CaseInfo(clazzQueryBldr);
                        classTmp = classTmp.getParentClassification();
                    }
                }
            }
        }

        if (ret != null && !ret.getRemark().isEmpty()) {
            final Instance periodInst = new Period().evaluateCurrentPeriod(_parameter, _caseInst);
            final Properties props = Accounting.getSysConfig().getObjectAttributeValueAsProperties(periodInst);

            if (BooleanUtils.toBoolean(props.getProperty(AccountingSettings.PERIOD_ACTIVATEREMARK4TRANSPOS))) {
                final PrintQuery print = CachedPrintQuery.get4Request(_productInstance);
                print.addAttribute(CIProducts.ProductAbstract.Name, CIProducts.ProductAbstract.Description);
                print.execute();

                final Map<String, String> map = new HashMap<>();
                map.put(Accounting.SubstitutorKeys.PRODUCT_NAME.name(),
                                print.getAttribute(CIProducts.ProductAbstract.Name));
                map.put(Accounting.SubstitutorKeys.PRODUCT_DESCR.name(),
                                print.getAttribute(CIProducts.ProductAbstract.Description));
                final StrSubstitutor sub = new StrSubstitutor(map);
                ret.setRemark(sub.replace(ret.getRemark()));
            }
        }
        return ret;
    }

    /**
     * Gets the account2 case info.
     *
     * @param _queryBldr the query bldr
     * @return the account2 case info
     * @throws EFapsException on error
     */
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
                        CIAccounting.Account2CaseAbstract.AmountConfig,
                        CIAccounting.Account2CaseAbstract.Order,
                        CIAccounting.Account2CaseAbstract.Key,
                        CIAccounting.Account2CaseAbstract.Remark);
        multi.addSelect(selAccInst, selCurrInst);
        multi.execute();
        if (multi.next()) {
            ret = new Account2CaseInfo()
                    .setInstance(multi.getCurrentInstance())
                    .setConfigs(multi
                                    .<List<Account2CaseConfig>>getAttribute(CIAccounting.Account2CaseAbstract.Config))
                    .setAmountConfig(multi.getAttribute(CIAccounting.Account2CaseAbstract.AmountConfig))
                    .setAccountInstance(multi.<Instance>getSelect(selAccInst))
                    .setCurrencyInstance(multi.<Instance>getSelect(selCurrInst))
                    .setDenominator(multi.<Integer>getAttribute(CIAccounting.Account2CaseAbstract.Denominator))
                    .setNumerator(multi.<Integer>getAttribute(CIAccounting.Account2CaseAbstract.Numerator))
                    .setLinkId(multi.<Long>getAttribute(CIAccounting.Account2CaseAbstract.LinkValue))
                    .setOrder(multi.<Integer>getAttribute(CIAccounting.Account2CaseAbstract.Order))
                    .setKey(multi.<String>getAttribute(CIAccounting.Account2CaseAbstract.Key))
                    .setRemark(multi.<String>getAttribute(CIAccounting.Account2CaseAbstract.Remark));
        }
        return ret;
    }

    /**
     * Gets the account2 case info.
     *
     * @param _acc2CaseInfoInstance the account two case info instance
     * @return the account2 case info
     * @throws EFapsException on error
     */
    protected static Account2CaseInfo getAccount2CaseInfo(final Instance _acc2CaseInfoInstance)
        throws EFapsException
    {
        Account2CaseInfo ret = null;
        final PrintQuery print = CachedPrintQuery.get4Request(_acc2CaseInfoInstance);
        final SelectBuilder selAccInst = new SelectBuilder()
                        .linkto(CIAccounting.Account2CaseAbstract.FromAccountAbstractLink).instance();
        final SelectBuilder selCurrInst = new SelectBuilder()
                        .linkto(CIAccounting.Account2CaseAbstract.CurrencyLink).instance();
        print.addAttribute(CIAccounting.Account2CaseAbstract.Numerator,
                        CIAccounting.Account2CaseAbstract.Denominator,
                        CIAccounting.Account2CaseAbstract.LinkValue,
                        CIAccounting.Account2CaseAbstract.Config,
                        CIAccounting.Account2CaseAbstract.AmountConfig,
                        CIAccounting.Account2CaseAbstract.Order,
                        CIAccounting.Account2CaseAbstract.Key,
                        CIAccounting.Account2CaseAbstract.Remark);
        print.addSelect(selAccInst, selCurrInst);
        print.execute();
        ret = new Account2CaseInfo()
                .setInstance(print.getCurrentInstance())
                .setConfigs(print.<List<Account2CaseConfig>>getAttribute(CIAccounting.Account2CaseAbstract.Config))
                .setAmountConfig(print.getAttribute(CIAccounting.Account2CaseAbstract.AmountConfig))
                .setAccountInstance(print.<Instance>getSelect(selAccInst))
                .setCurrencyInstance(print.<Instance>getSelect(selCurrInst))
                .setDenominator(print.<Integer>getAttribute(CIAccounting.Account2CaseAbstract.Denominator))
                .setNumerator(print.<Integer>getAttribute(CIAccounting.Account2CaseAbstract.Numerator))
                .setLinkId(print.<Long>getAttribute(CIAccounting.Account2CaseAbstract.LinkValue))
                .setOrder(print.<Integer>getAttribute(CIAccounting.Account2CaseAbstract.Order))
                .setKey(print.<String>getAttribute(CIAccounting.Account2CaseAbstract.Key))
                .setRemark(print.<String>getAttribute(CIAccounting.Account2CaseAbstract.Remark));
        return ret;
    }
}
