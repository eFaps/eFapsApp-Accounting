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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.apache.commons.collections4.comparators.ComparatorChain;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.efaps.admin.datamodel.Status;
import org.efaps.admin.datamodel.Type;
import org.efaps.admin.event.Parameter;
import org.efaps.db.Insert;
import org.efaps.db.Instance;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.esjp.accounting.Period;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.erp.CurrencyInst;
import org.efaps.util.EFapsException;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 * @version $Id$
 */
public abstract class TransInfo_Base
{
    /**
     * Logger for this class.
     */
    private static final Logger LOG = LoggerFactory.getLogger(TransInfo.class);

    private Type type;

    private String name;

    private String description;

    private DateTime date;

    private Status status;

    private String identifier;

    private Instance periodInst;

    private Instance instance;

    private final List<PositionInfo> postions = new ArrayList<>();

    public void create(final Parameter _parameter)
        throws EFapsException
    {
        final ComparatorChain<PositionInfo> chain = new ComparatorChain<PositionInfo>();
        chain.addComparator(new Comparator<PositionInfo>()
        {

            @Override
            public int compare(final PositionInfo _o1,
                               final PositionInfo _o2)
            {
                int ret;
                if (_o1.getType().equals(_o2.getType())) {
                    ret = 0;
                } else if (_o1.getType().equals(CIAccounting.TransactionPositionDebit.getType())) {
                    ret = -1;
                } else {
                    ret = 1;
                }
                return ret;
            }
        });
        chain.addComparator(new Comparator<PositionInfo>()
        {

            @Override
            public int compare(final PositionInfo _o1,
                               final PositionInfo _o2)
            {
                return _o1.getOrder().compareTo(_o2.getOrder());
            }
        });

        chain.addComparator(new Comparator<PositionInfo>()
        {

            @Override
            public int compare(final PositionInfo _o1,
                               final PositionInfo _o2)
            {
                return _o1.getConnOrder().compareTo(_o2.getConnOrder());
            }
        });
        Collections.sort(this.postions, chain);

        final Insert insert = new Insert(getType());
        insert.add(CIAccounting.Transaction.Name, getName());
        insert.add(CIAccounting.Transaction.Description, getDescription());
        insert.add(CIAccounting.Transaction.Date, getDate());
        insert.add(CIAccounting.Transaction.PeriodLink, getPeriodInst());
        insert.add(CIAccounting.Transaction.Status, getStatus());
        insert.add(CIAccounting.Transaction.Identifier, getIdentifier());
        insert.execute();
        setInstance(insert.getInstance());

        int i = 1;
        for (final PositionInfo pos : this.postions) {
            final Insert posInsert = new Insert(pos.getType());
            posInsert.add(CIAccounting.TransactionPositionAbstract.Position, i);
            posInsert.add(CIAccounting.TransactionPositionAbstract.TransactionLink, getInstance());
            posInsert.add(CIAccounting.TransactionPositionAbstract.AccountLink, pos.getAccInst());
            posInsert.add(CIAccounting.TransactionPositionAbstract.CurrencyLink, pos.getCurrInst());
            posInsert.add(CIAccounting.TransactionPositionAbstract.RateCurrencyLink, pos.getRateCurrInst());
            posInsert.add(CIAccounting.TransactionPositionAbstract.Rate, pos.getRate());
            posInsert.add(CIAccounting.TransactionPositionAbstract.Amount, pos.getAmount());
            posInsert.add(CIAccounting.TransactionPositionAbstract.RateAmount, pos.getRateAmount());
            posInsert.add(CIAccounting.TransactionPositionAbstract.Remark, pos.getRemark());
            posInsert.execute();
            pos.setInstance(posInsert.getInstance());
            i++;
        }
        // connect labels
        for (final PositionInfo pos : this.postions) {
            if (pos.getLabelInst() != null && pos.getLabelInst().isValid() && pos.getLabelRelType() != null) {
                final Insert relInsert = new Insert(pos.getLabelRelType());
                relInsert.add(CIAccounting.TransactionPosition2ObjectAbstract.FromLinkAbstract, pos.getInstance());
                relInsert.add(CIAccounting.TransactionPosition2LabelAbstract.ToLinkAbstract, pos.getLabelInst());
                relInsert.execute();
            }
        }
        // connect docs
        for (final PositionInfo pos : this.postions) {
            if (pos.getDocInst() != null && pos.getDocInst().isValid() && pos.getDocRelType() != null) {
                final Insert relInsert = new Insert(pos.getDocRelType());
                relInsert.add(CIAccounting.TransactionPosition2ObjectAbstract.FromLinkAbstract, pos.getInstance());
                relInsert.add(CIAccounting.TransactionPosition2ERPDocument.ToLinkAbstract, pos.getDocInst());
                relInsert.execute();
            }
        }
    }

    public TransInfo addPosition(final PositionInfo _posInfo)
    {
        this.postions.add(_posInfo);
        return (TransInfo) this;
    }

    /**
     * Getter method for the instance variable {@link #periodInst}.
     *
     * @return value of instance variable {@link #periodInst}
     */
    public Instance getPeriodInst()
    {
        return this.periodInst;
    }

    /**
     * Setter method for instance variable {@link #periodInst}.
     *
     * @param _periodInst value for instance variable {@link #periodInst}
     */
    public TransInfo setPeriodInst(final Instance _periodInst)
    {
        this.periodInst = _periodInst;
        return (TransInfo) this;
    }

    /**
     * Getter method for the instance variable {@link #identifier}.
     *
     * @return value of instance variable {@link #identifier}
     */
    public String getIdentifier()
    {
        return this.identifier;
    }

    /**
     * Setter method for instance variable {@link #identifier}.
     *
     * @param _identifier value for instance variable {@link #identifier}
     */
    public TransInfo setIdentifier(final String _identifier)
    {
        this.identifier = _identifier;
        return (TransInfo) this;
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
     * Setter method for instance variable {@link #date}.
     *
     * @param _date value for instance variable {@link #date}
     */
    public TransInfo setDate(final DateTime _date)
    {
        this.date = _date;
        return (TransInfo) this;
    }

    /**
     * Getter method for the instance variable {@link #status}.
     *
     * @return value of instance variable {@link #status}
     */
    public Status getStatus()
    {
        return this.status;

    }

    /**
     * Setter method for instance variable {@link #status}.
     *
     * @param _status value for instance variable {@link #status}
     */
    public TransInfo setStatus(final Status _status)
    {
        this.status = _status;
        return (TransInfo) this;
    }

    /**
     * Getter method for the instance variable {@link #name}.
     *
     * @return value of instance variable {@link #name}
     */
    public String getName()
    {
        return this.name;
    }

    /**
     * Setter method for instance variable {@link #name}.
     *
     * @param _name value for instance variable {@link #name}
     */
    public TransInfo setName(final String _name)
    {
        this.name = _name;
        return (TransInfo) this;
    }

    /**
     * Getter method for the instance variable {@link #description}.
     *
     * @return value of instance variable {@link #description}
     */
    public String getDescription()
    {
        return this.description;
    }

    /**
     * Setter method for instance variable {@link #description}.
     *
     * @param _description value for instance variable {@link #description}
     */
    public TransInfo setDescription(final String _description)
    {
        this.description = _description;
        return (TransInfo) this;
    }

    /**
     * Getter method for the instance variable {@link #type}.
     *
     * @return value of instance variable {@link #type}
     */
    public Type getType()
    {
        return this.type;
    }

    /**
     * Setter method for instance variable {@link #type}.
     *
     * @param _type value for instance variable {@link #type}
     * @return this for chaining
     *
     */
    public TransInfo setType(final Type _type)
    {
        this.type = _type;
        return (TransInfo) this;
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


    protected static TransInfo get4DocInfo(final Parameter _parameter,
                                           final DocumentInfo _docInfo,
                                           final boolean _setDocInst)
        throws EFapsException
    {
        final Period period = new Period();
        final Instance periodInst = period.evaluateCurrentPeriod(_parameter,
                        _docInfo.getCaseInst() == null ? _docInfo.getInstance() : _docInfo.getCaseInst());
        final CurrencyInst currInst = period.getCurrency(periodInst);
        final TransInfo ret = new TransInfo()
            .setType(CIAccounting.Transaction.getType())
            .setStatus(Status.find(CIAccounting.TransactionStatus.Open))
            .setIdentifier(Transaction.IDENTTEMP)
            .setPeriodInst(periodInst)
            .setDescription(_docInfo.getDescription(_parameter))
            .setDate(_docInfo.getDate());
        int i = 0;
        for (final AccountInfo accInfo : _docInfo.getDebitAccounts()) {
            if (_setDocInst) {
                accInfo.setDocLink(_docInfo.getInstance());
            }
            final PositionInfo pos = get4AccountInfo(_parameter,
                            CIAccounting.TransactionPositionDebit.getType(), accInfo)
                    .setRateCurrInst(_docInfo.getRateCurrInst())
                    .setCurrInst(currInst.getInstance())
                    .setOrder(i);
            ret.addPosition(pos);
            for (final PositionInfo relPosInfo : getRelPosInfos(accInfo,
                            CIAccounting.TransactionPositionDebit.getType())) {
                relPosInfo.setCurrInst(pos.getCurrInst())
                    .setRateCurrInst(pos.getRateCurrInst())
                    .setRate(pos.getRate())
                    .setCurrInst(currInst.getInstance())
                    .setOrder(i);
                ret.addPosition(relPosInfo);
            }
            i++;
        }
        i = 0;
        for (final AccountInfo accInfo : _docInfo.getCreditAccounts()) {
            if (_setDocInst) {
                accInfo.setDocLink(_docInfo.getInstance());
            }
            final PositionInfo pos = get4AccountInfo(_parameter,
                            CIAccounting.TransactionPositionCredit.getType(), accInfo)
                            .setRateCurrInst(_docInfo.getRateCurrInst())
                            .setCurrInst(currInst.getInstance())
                            .setOrder(i);
            ret.addPosition(pos);
            for (final PositionInfo relPosInfo : getRelPosInfos(accInfo,
                            CIAccounting.TransactionPositionCredit.getType())) {
                relPosInfo.setCurrInst(pos.getCurrInst())
                    .setRateCurrInst(pos.getRateCurrInst())
                    .setCurrInst(currInst.getInstance())
                    .setRate(pos.getRate())
                    .setOrder(i);
                ret.addPosition(relPosInfo);
            }
            i++;
        }
        return ret;
    }

    protected static List<PositionInfo> getRelPosInfos(final AccountInfo _accInfo,
                                                       final Type _type)
        throws EFapsException
    {
        final List<PositionInfo> ret = new ArrayList<>();
        final boolean isDebitTrans = _type.getUUID().equals(CIAccounting.TransactionPositionDebit.uuid);
        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.Account2AccountAbstract);
        queryBldr.addWhereAttrEqValue(CIAccounting.Account2AccountAbstract.FromAccountLink, _accInfo.getInstance());
        final MultiPrintQuery multi = queryBldr.getPrint();
        final SelectBuilder selAcc = SelectBuilder.get()
                        .linkto(CIAccounting.Account2AccountAbstract.ToAccountLink).instance();
        multi.addSelect(selAcc);
        multi.addAttribute(CIAccounting.Account2AccountAbstract.Numerator,
                          CIAccounting.Account2AccountAbstract.Denominator);
        multi.execute();
        final int y = 1;
        while (multi.next()) {
            final Instance instance = multi.getCurrentInstance();
            final PositionInfo connPos  = new PositionInfo();
            final BigDecimal numerator = new BigDecimal(multi.<Integer>getAttribute(
                                CIAccounting.Account2AccountAbstract.Numerator));
            final BigDecimal denominator = new BigDecimal(multi.<Integer>getAttribute(
                                CIAccounting.Account2AccountAbstract.Denominator));

            BigDecimal amount = _accInfo.getAmountRate().multiply(numerator).divide(denominator,
                                BigDecimal.ROUND_HALF_UP);
            BigDecimal rateAmount = _accInfo.getAmount().multiply(numerator).divide(denominator,
                                BigDecimal.ROUND_HALF_UP);
            if (isDebitTrans) {
                amount = amount.negate();
                rateAmount = rateAmount.negate();
            }
            if (instance.getType().getUUID().equals(CIAccounting.Account2AccountCosting.uuid)) {
                    connPos.setType(_type);
                } else if (instance.getType().getUUID()
                                .equals(CIAccounting.Account2AccountCostingInverse.uuid)) {
                    if (_type.getUUID().equals(CIAccounting.TransactionPositionDebit.uuid)) {
                        connPos.setType(CIAccounting.TransactionPositionCredit.getType());
                    } else {
                        connPos.setType(CIAccounting.TransactionPositionDebit.getType());
                    }
                    amount = amount.negate();
                } else if (instance.getType().getUUID().equals(CIAccounting.Account2AccountCredit.uuid)) {
                    if (isDebitTrans) {
                        connPos.setType(CIAccounting.TransactionPositionCredit.getType());
                    } else {
                        connPos.setType(CIAccounting.TransactionPositionDebit.getType());
                        amount = amount.negate();
                        rateAmount = rateAmount.negate();
                    }
                } else if (instance.getType().getUUID().equals(CIAccounting.Account2AccountDebit.uuid)) {
                    if (isDebitTrans) {
                        connPos.setType(CIAccounting.TransactionPositionDebit.getType());
                        amount = amount.negate();
                        rateAmount = rateAmount.negate();
                    } else {
                        connPos.setType(CIAccounting.TransactionPositionCredit.getType());
                    }
                }
                if (connPos.getType() == null) {
                    LOG.error("Missing definition");
                } else {
                    connPos.setConnOrder(y)
                        .setAccInst(multi.<Instance>getSelect(selAcc))
                        .setAmount(amount)
                        .setRateAmount(rateAmount);
                    ret.add(connPos);
                }
            }
        return ret;
    }


    public static PositionInfo get4AccountInfo(final Parameter _parameter,
                                               final Type _type,
                                               final AccountInfo _accInfo)
        throws EFapsException
    {
        final PositionInfo ret = new PositionInfo()
            .setAccInst(_accInfo.getInstance())
            .setAmount(_type.isKindOf(CIAccounting.TransactionPositionDebit.getType())
                            ? _accInfo.getAmountRate().negate() : _accInfo.getAmountRate())
            .setType(_type)
            .setCurrInst(_accInfo.getCurrInstance())
            .setRateAmount(_type.isKindOf(CIAccounting.TransactionPositionDebit.getType())
                            ? _accInfo.getAmount().negate() : _accInfo.getAmount())
            .setRate(_accInfo.getRateInfo().getRateObject());
        if (_accInfo.getDocLink() != null && _accInfo.getDocLink().isValid()) {
            final DocumentInfo docInfoTmp = new DocumentInfo(_accInfo.getDocLink());
            if (docInfoTmp.isSumsDoc()) {
                ret.setDocInst(_accInfo.getDocLink())
                    .setDocRelType(_type.isKindOf(CIAccounting.TransactionPositionDebit.getType())
                                                ? CIAccounting.TransactionPositionDebit2SalesDocument.getType()
                                                : CIAccounting.TransactionPositionCredit2SalesDocument.getType());
            } else {
                ret.setDocInst(_accInfo.getDocLink())
                    .setDocRelType(_type.isKindOf(CIAccounting.TransactionPositionDebit.getType())
                                                ? CIAccounting.TransactionPositionDebit2PaymentDocument.getType()
                                                : CIAccounting.TransactionPositionCredit2PaymentDocument.getType());
            }
        }
        return ret;
    }

    @Override
    public String toString()
    {
        return ToStringBuilder.reflectionToString(this);
    }

    public static class PositionInfo
    {
        private Instance instance;

        private Integer order = 0;

        private Integer connOrder = 0;

        private Type type;

        private Instance accInst;

        private Instance currInst;

        private Instance rateCurrInst;

        private Instance labelInst;

        private Type labelRelType;

        private Instance docInst;

        private Type docRelType;

        private Object rate;

        private BigDecimal rateAmount;

        private BigDecimal amount;

        private String remark;

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
         */
        public PositionInfo setLabelInst(final Instance _labelInst)
        {
            this.labelInst = _labelInst;
            return this;
        }

        /**
         * Getter method for the instance variable {@link #accInst}.
         *
         * @return value of instance variable {@link #accInst}
         */
        public Instance getAccInst()
        {
            return this.accInst;
        }

        /**
         * Setter method for instance variable {@link #accInst}.
         *
         * @param _accInst value for instance variable {@link #accInst}
         */
        public PositionInfo setAccInst(final Instance _accInst)
        {
            this.accInst = _accInst;
            return this;
        }

        /**
         * Getter method for the instance variable {@link #currInst}.
         *
         * @return value of instance variable {@link #currInst}
         */
        public Instance getCurrInst()
        {
            return this.currInst;
        }

        /**
         * Setter method for instance variable {@link #currInst}.
         *
         * @param _currInst value for instance variable {@link #currInst}
         */
        public PositionInfo setCurrInst(final Instance _currInst)
        {
            this.currInst = _currInst;
            return this;
        }

        /**
         * Getter method for the instance variable {@link #rateCurrInst}.
         *
         * @return value of instance variable {@link #rateCurrInst}
         */
        public Instance getRateCurrInst()
        {
            return this.rateCurrInst;
        }

        /**
         * Setter method for instance variable {@link #rateCurrInst}.
         *
         * @param _rateCurrInst value for instance variable
         *            {@link #rateCurrInst}
         */
        public PositionInfo setRateCurrInst(final Instance _rateCurrInst)
        {
            this.rateCurrInst = _rateCurrInst;
            return this;
        }

        /**
         * Getter method for the instance variable {@link #rate}.
         *
         * @return value of instance variable {@link #rate}
         */
        public Object getRate()
        {
            return this.rate;
        }

        /**
         * Setter method for instance variable {@link #rate}.
         *
         * @param _rate value for instance variable {@link #rate}
         */
        public PositionInfo setRate(final Object _rate)
        {
            this.rate = _rate;
            return this;
        }

        /**
         * Getter method for the instance variable {@link #rateAmount}.
         *
         * @return value of instance variable {@link #rateAmount}
         */
        public BigDecimal getRateAmount()
        {
            return this.rateAmount;
        }

        /**
         * Setter method for instance variable {@link #rateAmount}.
         *
         * @param _rateAmount value for instance variable {@link #rateAmount}
         */
        public PositionInfo setRateAmount(final BigDecimal _rateAmount)
        {
            this.rateAmount = _rateAmount;
            return this;
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
        public PositionInfo setAmount(final BigDecimal _amount)
        {
            this.amount = _amount;
            return this;
        }

        /**
         * Getter method for the instance variable {@link #type}.
         *
         * @return value of instance variable {@link #type}
         */
        public Type getType()
        {
            return this.type;
        }

        /**
         * Setter method for instance variable {@link #type}.
         *
         * @param _type value for instance variable {@link #type}
         * @return this for chaining
         *
         */
        public PositionInfo setType(final Type _type)
        {
            this.type = _type;
            return this;
        }

        /**
         * Getter method for the instance variable {@link #labelRelType}.
         *
         * @return value of instance variable {@link #labelRelType}
         */
        public Type getLabelRelType()
        {
            return this.labelRelType;
        }

        /**
         * Setter method for instance variable {@link #labelRelType}.
         *
         * @param _labelRelType value for instance variable
         *            {@link #labelRelType}
         */
        public PositionInfo setLabelRelType(final Type _labelRelType)
        {
            this.labelRelType = _labelRelType;
            return this;
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
        public PositionInfo setOrder(final Integer _order)
        {
            this.order = _order;
            return this;
        }

        /**
         * Getter method for the instance variable {@link #connOrder}.
         *
         * @return value of instance variable {@link #connOrder}
         */
        public Integer getConnOrder()
        {
            return this.connOrder;
        }

        /**
         * Setter method for instance variable {@link #connOrder}.
         *
         * @param _connOrder value for instance variable {@link #connOrder}
         */
        public PositionInfo setConnOrder(final Integer _connOrder)
        {
            this.connOrder = _connOrder;
            return this;
        }

        /**
         * Getter method for the instance variable {@link #docRelType}.
         *
         * @return value of instance variable {@link #docRelType}
         */
        public Type getDocRelType()
        {
            return this.docRelType;
        }

        /**
         * Setter method for instance variable {@link #docRelType}.
         *
         * @param _docRelType value for instance variable {@link #docRelType}
         */
        public PositionInfo setDocRelType(final Type _docRelType)
        {
            this.docRelType = _docRelType;
            return this;
        }

        /**
         * Getter method for the instance variable {@link #docInst}.
         *
         * @return value of instance variable {@link #docInst}
         */
        public Instance getDocInst()
        {
            return this.docInst;
        }

        /**
         * Setter method for instance variable {@link #docInst}.
         *
         * @param _docInst value for instance variable {@link #docInst}
         */
        public PositionInfo setDocInst(final Instance _docInst)
        {
            this.docInst = _docInst;
            return this;
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

        @Override
        public String toString()
        {
            return ToStringBuilder.reflectionToString(this);
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
         */
        public PositionInfo setRemark(final String _remark)
        {
            this.remark = _remark;
            return this;
        }

    }
}
