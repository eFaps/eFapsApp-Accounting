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

package org.efaps.esjp.accounting.report;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.comparators.ComparatorChain;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.AttributeQuery;
import org.efaps.db.Instance;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.esjp.accounting.Period;
import org.efaps.esjp.accounting.util.Accounting;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.ci.CIERP;
import org.efaps.esjp.ci.CIFormAccounting;
import org.efaps.esjp.common.parameter.ParameterUtil;
import org.efaps.esjp.erp.RateInfo;
import org.efaps.util.EFapsException;
import org.joda.time.DateTime;

import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JasperReport;
/**
 * TODO comment!
 *
 * @author The eFaps Team
 */
@EFapsUUID("9994200b-40b2-466a-b1fe-bfd6e927e92e")
@EFapsApplication("eFapsApp-Accounting")
public abstract class JournalReport51DS_Base
    extends AbstractReportDS
{

    @Override
    public void init(final JasperReport _jasperReport,
                     final Parameter _parameter,
                     final JRDataSource _parentSource,
                     final Map<String, Object> _jrParameters)
        throws EFapsException
    {
        super.init(_jasperReport, _parameter, _parentSource, _jrParameters);

        final DateTime dateFrom = new DateTime(_parameter.getParameterValue(
                        CIFormAccounting.Accounting_PReportJournal51Form.dateFrom.name));
        final DateTime dateTo = new DateTime(_parameter.getParameterValue(
                        CIFormAccounting.Accounting_PReportJournal51Form.dateTo.name));

        final Instance periodInstance = Period.evalCurrent(_parameter);

        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.TransactionPositionAbstract);
        final QueryBuilder transAttrQueryBldr = new QueryBuilder(CIAccounting.TransactionAbstract);
        transAttrQueryBldr.addWhereAttrLessValue(CIAccounting.TransactionAbstract.Date,
                        dateTo.withTimeAtStartOfDay().plusDays(1));
        transAttrQueryBldr.addWhereAttrGreaterValue(CIAccounting.TransactionAbstract.Date,
                        dateFrom.withTimeAtStartOfDay().minusSeconds(1));
        transAttrQueryBldr.addWhereAttrEqValue(CIAccounting.TransactionAbstract.PeriodLink, periodInstance);

        final AttributeQuery transAttrQuery = transAttrQueryBldr.getAttributeQuery(CIAccounting.TransactionAbstract.ID);

        queryBldr.addWhereAttrInQuery(CIAccounting.TransactionPositionAbstract.TransactionLink, transAttrQuery);

        final MultiPrintQuery multi = queryBldr.getPrint();
        final SelectBuilder selAcc = SelectBuilder.get().linkto(CIAccounting.TransactionPositionAbstract.AccountLink);
        final SelectBuilder selAccName = new SelectBuilder(selAcc).attribute(CIAccounting.AccountAbstract.Name);
        final SelectBuilder selAccDescr = new SelectBuilder(selAcc).attribute(CIAccounting.AccountAbstract.Description);

        final SelectBuilder selTrans = SelectBuilder.get().linkto(
                        CIAccounting.TransactionPositionAbstract.TransactionLink);
        final SelectBuilder selTransOID = new SelectBuilder(selTrans).oid();
        final SelectBuilder selTransDescr = new SelectBuilder(selTrans)
                        .attribute(CIAccounting.TransactionAbstract.Description);
        final SelectBuilder selTransIdentifier = new SelectBuilder(selTrans)
                        .attribute(CIAccounting.TransactionAbstract.Identifier);
        final SelectBuilder selTransName = new SelectBuilder(selTrans)
                        .attribute(CIAccounting.TransactionAbstract.Name);
        final SelectBuilder selTransDate = new SelectBuilder(selTrans).attribute(CIAccounting.TransactionAbstract.Date);

        final SelectBuilder selCurInst = SelectBuilder.get().linkto(
                        CIAccounting.TransactionPositionAbstract.CurrencyLink).instance();
        final SelectBuilder selRateCurInst = SelectBuilder.get().linkto(
                        CIAccounting.TransactionPositionAbstract.RateCurrencyLink).instance();
        multi.addSelect(selAccName, selAccDescr, selTransIdentifier, selTransOID, selTransName, selTransDescr,
                        selTransDate, selCurInst, selRateCurInst);
        multi.addAttribute(CIAccounting.TransactionPositionAbstract.Amount,
                        CIAccounting.TransactionPositionAbstract.Position,
                        CIAccounting.TransactionPositionAbstract.RateAmount);
        multi.execute();
        final List<DataBean> values = new ArrayList<>();
        final Map<String, DataBean> map = new HashMap<>();
        while (multi.next()) {
            final String transOID = multi.getSelect(selTransOID);
            final DataBean bean;
            if (map.containsKey(transOID)) {
                bean = map.get(transOID);
            } else {
                bean = new DataBean()
                            .setParameter(_parameter)
                            .setTransOID(transOID)
                            .setTransName(multi.getSelect(selTransName))
                            .setTransDate(multi.getSelect(selTransDate))
                            .setTransDescr(multi.getSelect(selTransDescr))
                            .setTransIdentifier(multi.getSelect(selTransIdentifier));
                values.add(bean);
                map.put(transOID, bean);
            }
            final DetailBean detailBean = new DetailBean()
                        .setAccName(multi.getSelect(selAccName))
                        .setAccDescr(multi.getSelect(selAccDescr))
                        .setAmount(multi.getAttribute(CIAccounting.TransactionPositionAbstract.Amount))
                        .setCurrencyInstance(multi.getSelect(selCurInst))
                        .setRateAmount(multi.getAttribute(CIAccounting.TransactionPositionAbstract.RateAmount))
                        .setCurrencyInstance(multi.getSelect(selCurInst))
                        .setPosition(multi.getAttribute(CIAccounting.TransactionPositionAbstract.Position))
                        .setRateCurrencyInstance(multi.getSelect(selRateCurInst));
            bean.addDetail(detailBean);
        }

        final QueryBuilder relQueryBldr = new QueryBuilder(CIAccounting.Transaction2ERPDocument);
        relQueryBldr.addWhereAttrInQuery(CIAccounting.Transaction2ERPDocument.FromLink, transAttrQuery);
        final MultiPrintQuery relMulti = relQueryBldr.getPrint();
        final SelectBuilder transSel = SelectBuilder.get().linkto(CIAccounting.Transaction2ERPDocument.FromLink)
                        .instance();
        final SelectBuilder docNameSel = SelectBuilder.get()
                        .linkto(CIAccounting.Transaction2ERPDocument.ToLinkAbstract)
                        .attribute(CIERP.DocumentAbstract.Name);
        relMulti.addSelect(transSel, docNameSel);
        relMulti.execute();
        while (relMulti.next()) {
            final Instance transInst = relMulti.getSelect(transSel);
            final DataBean bean = map.get(transInst.getOid());
            bean.addDoc(relMulti.<String>getSelect(docNameSel));
        }

        final QueryBuilder subJAttrQueryBldr = new QueryBuilder(CIAccounting.ReportSubJournal);
        subJAttrQueryBldr.addWhereAttrEqValue(CIAccounting.ReportSubJournal.Config,
                        Accounting.SubJournalConfig.OFFICIAL);
        final QueryBuilder subJQueryBldr = new QueryBuilder(CIAccounting.ReportSubJournal2Transaction);
        subJQueryBldr.addWhereAttrInQuery(CIAccounting.ReportSubJournal2Transaction.FromLink,
                        subJAttrQueryBldr.getAttributeQuery(CIAccounting.ReportSubJournal.ID));
        subJQueryBldr.addWhereAttrInQuery(CIAccounting.ReportSubJournal2Transaction.ToLink, transAttrQuery);
        final MultiPrintQuery subJMulti = subJQueryBldr.getPrint();
        final SelectBuilder transSel2 = SelectBuilder.get().linkto(CIAccounting.ReportSubJournal2Transaction.ToLink)
                        .instance();
        final SelectBuilder subJNameSel = SelectBuilder.get()
                        .linkto(CIAccounting.ReportSubJournal2Transaction.FromLink)
                        .attribute(CIAccounting.ReportSubJournal.Name);
        subJMulti.addSelect(transSel2, subJNameSel);
        subJMulti.addAttribute(CIAccounting.ReportSubJournal2Transaction.Number);
        subJMulti.execute();
        while (subJMulti.next()) {
            final Instance transInst = subJMulti.getSelect(transSel2);
            map.get(transInst.getOid())
                .addDocReg(subJMulti.<String>getSelect(subJNameSel))
                .addDocNum(subJMulti.<String>getAttribute(CIAccounting.ReportSubJournal2Transaction.Number));
        }

        final ComparatorChain<DataBean> chain = new ComparatorChain<>();
        chain.addComparator(new Comparator<DataBean>()
        {

            @Override
            public int compare(final DataBean _arg0,
                               final DataBean _arg1)
            {
                final String arg0 = _arg0.getTransName().isEmpty() ? "XXXXXXXX" : _arg0.getTransName();
                final String arg1 = _arg1.getTransName().isEmpty() ? "XXXXXXXX" : _arg1.getTransName();
                return arg0.compareTo(arg1);
            }
        });
        chain.addComparator(new Comparator<DataBean>()
        {

            @Override
            public int compare(final DataBean _arg0,
                               final DataBean _arg1)
            {
                return _arg0.getTransDate().compareTo(_arg1.getTransDate());
            }
        });
        chain.addComparator(new Comparator<DataBean>()
        {

            @Override
            public int compare(final DataBean _o1,
                               final DataBean _o2)
            {
                return _o1.getTransIdentifier().compareTo(_o2.getTransIdentifier());
            }
        });

        Collections.sort(values, chain);
        setData(values);
    }

    /**
     * The Class DataBean.
     */
    public static class DataBean
    {
        /** The parameter. */
        private Parameter parameter;

        /** The trans OID. */
        private String transOID;

        /** The trans name. */
        private String transName;

        /** The trans date. */
        private DateTime transDate;

        /** The trans descr. */
        private String transDescr;

        /** The trans identifier. */
        private String transIdentifier;

        /** The doc reg. */
        private String docReg;

        /** The doc num. */
        private String docNum;

        /** The doc name. */
        private String docName;

        /** The details. */
        private List<DetailBean> details = new ArrayList<>();

        /**
         * Gets the debit.
         *
         * @return the debit
         * @throws EFapsException on error
         */
        public BigDecimal getDebit() throws EFapsException
        {
            BigDecimal ret = BigDecimal.ZERO;
            for (final DetailBean bean : this.details) {
                if (bean.getReportAmount().compareTo(BigDecimal.ZERO) < 0) {
                    ret = ret.add(bean.getReportAmount().abs());
                }
            }
            return ret;
        }

        /**
         * Gets the credit.
         *
         * @return the credit
         * @throws EFapsException on error
         */
        public BigDecimal getCredit()
            throws EFapsException
        {
            BigDecimal ret = BigDecimal.ZERO;
            for (final DetailBean bean : this.details) {
                if (bean.getReportAmount().compareTo(BigDecimal.ZERO) > 0) {
                    ret = ret.add(bean.getReportAmount());
                }
            }
            return ret;
        }

        /**
         * Adds the doc.
         *
         * @param _docName the doc name
         * @return the data bean
         */
        public DataBean addDoc(final String _docName)
        {
            if (getDocName() == null) {
                setDocName(_docName);
            } else {
                setDocName(getDocName() + ", " + _docName);
            }
            return this;
        }

        /**
         * Adds the doc reg.
         *
         * @param _docReg the doc reg
         * @return the data bean
         */
        public DataBean addDocReg(final String _docReg)
        {
            if (getDocReg() == null) {
                setDocReg(_docReg);
            } else {
                setDocReg(getDocReg() + ", " + _docReg);
            }
            return this;
        }

        /**
         * Adds the doc num.
         *
         * @param _docNum the doc num
         * @return the data bean
         */
        public DataBean addDocNum(final String _docNum)
        {
            if (getDocNum() == null) {
                setDocNum(_docNum);
            } else {
                setDocNum(getDocNum() + ", " + _docNum);
            }
            return this;
        }

        /**
         * Getter method for the instance variable {@link #transDate}.
         *
         * @return value of instance variable {@link #transDate}
         */
        public DateTime getTransDate()
        {
            return this.transDate;
        }

        /**
         * Adds the detail.
         *
         * @param _detailBean the detail bean
         * @return the data bean
         */
        public DataBean addDetail(final DetailBean _detailBean)
        {
            getDetails().add(_detailBean);
            _detailBean.setParent(this);
            return this;
        }

        /**
         * Setter method for instance variable {@link #transDate}.
         *
         * @param _transDate value for instance variable {@link #transDate}
         * @return the data bean
         */
        public DataBean setTransDate(final DateTime _transDate)
        {
            this.transDate = _transDate;
            return this;
        }

        /**
         * Getter method for the instance variable {@link #transDescr}.
         *
         * @return value of instance variable {@link #transDescr}
         */
        public String getTransDescr()
        {
            return this.transDescr;
        }

        /**
         * Setter method for instance variable {@link #transDescr}.
         *
         * @param _transDescr value for instance variable {@link #transDescr}
         * @return the data bean
         */
        public DataBean setTransDescr(final String _transDescr)
        {
            this.transDescr = _transDescr;
            return this;
        }

        /**
         * Getter method for the instance variable {@link #transName}.
         *
         * @return value of instance variable {@link #transName}
         */
        public String getTransName()
        {
            return this.transName;
        }

        /**
         * Setter method for instance variable {@link #transName}.
         *
         * @param _transName value for instance variable {@link #transName}
         * @return the data bean
         */
        public DataBean setTransName(final String _transName)
        {
            this.transName = _transName;
            return this;
        }

        /**
         * Getter method for the instance variable {@link #transOID}.
         *
         * @return value of instance variable {@link #transOID}
         */
        public String getTransOID()
        {
            return this.transOID;
        }

        /**
         * Setter method for instance variable {@link #transOID}.
         *
         * @param _transOID value for instance variable {@link #transOID}
         * @return the data bean
         */
        public DataBean setTransOID(final String _transOID)
        {
            this.transOID = _transOID;
            return this;
        }

        /**
         * Getter method for the instance variable {@link #details}.
         *
         * @return value of instance variable {@link #details}
         */
        public List<DetailBean> getDetails()
        {
            Collections.sort(this.details, new Comparator<DetailBean>()
            {

                @Override
                public int compare(final DetailBean _arg0,
                                   final DetailBean _arg1)
                {
                    return _arg0.getPosition().compareTo(_arg1.getPosition());
                }
            });
            return this.details;
        }

        /**
         * Setter method for instance variable {@link #details}.
         *
         * @param _details value for instance variable {@link #details}
         * @return the data bean
         */
        public DataBean setDetails(final List<DetailBean> _details)
        {
            this.details = _details;
            return this;
        }

        /**
         * Getter method for the instance variable {@link #transIdentifier}.
         *
         * @return value of instance variable {@link #transIdentifier}
         */
        public String getTransIdentifier()
        {
            return this.transIdentifier;
        }

        /**
         * Setter method for instance variable {@link #transIdentifier}.
         *
         * @param _transIdentifier value for instance variable
         *            {@link #transIdentifier}
         * @return the data bean
         */
        public DataBean setTransIdentifier(final String _transIdentifier)
        {
            this.transIdentifier = _transIdentifier;
            return this;
        }

        /**
         * Getter method for the instance variable {@link #docReg}.
         *
         * @return value of instance variable {@link #docReg}
         */
        public String getDocReg()
        {
            return this.docReg;
        }

        /**
         * Setter method for instance variable {@link #docReg}.
         *
         * @param _docReg value for instance variable {@link #docReg}
         * @return the data bean
         */
        public DataBean setDocReg(final String _docReg)
        {
            this.docReg = _docReg;
            return this;
        }

        /**
         * Getter method for the instance variable {@link #docNum}.
         *
         * @return value of instance variable {@link #docNum}
         */
        public String getDocNum()
        {
            return this.docNum;
        }

        /**
         * Setter method for instance variable {@link #docNum}.
         *
         * @param _docNum value for instance variable {@link #docNum}
         * @return the data bean
         */
        public DataBean setDocNum(final String _docNum)
        {
            this.docNum = _docNum;
            return this;
        }

        /**
         * Getter method for the instance variable {@link #docName}.
         *
         * @return value of instance variable {@link #docName}
         */
        public String getDocName()
        {
            return this.docName;
        }

        /**
         * Setter method for instance variable {@link #docName}.
         *
         * @param _docName value for instance variable {@link #docName}
         * @return the data bean
         */
        public DataBean setDocName(final String _docName)
        {
            this.docName = _docName;
            return this;
        }

        /**
         * Gets the parameter.
         *
         * @return the parameter
         */
        public Parameter getParameter()
        {
            return this.parameter;
        }

        /**
         * Sets the parameter.
         *
         * @param _parameter Parameter as passed by the eFaps API
         * @return the data bean
         */
        public DataBean setParameter(final Parameter _parameter)
        {
            this.parameter = _parameter;
            return this;
        }
    }

    /**
     * The Class DetailBean.
     *
     */
    public static class DetailBean
    {

        /** The parameter. */
        private DataBean parent;

        /** The acc name. */
        private String accName;

        /** The acc descr. */
        private String accDescr;

        /** The position. */
        private Integer position;

        /** The amount. */
        private BigDecimal amount;

        /** The amount. */
        private BigDecimal rateAmount;

        /** The currency instance. */
        private Instance currencyInstance;

        /** The rate currency instance. */
        private Instance rateCurrencyInstance;

        /**
         * Gets the parameter.
         *
         * @return the parameter
         */
        public DataBean getParent()
        {
            return this.parent;
        }

        /**
         * Sets the parent.
         *
         * @param _parent the parent
         * @return the detail bean
         */
        public DetailBean setParent(final DataBean _parent)
        {
            this.parent = _parent;
            return this;
        }

        /**
         * Getter method for the instance variable {@link #accName}.
         *
         * @return value of instance variable {@link #accName}
         */
        public String getAccName()
        {
            return this.accName;
        }

        /**
         * Setter method for instance variable {@link #accName}.
         *
         * @param _accName value for instance variable {@link #accName}
         * @return the detail bean
         */
        public DetailBean setAccName(final String _accName)
        {
            this.accName = _accName;
            return this;
        }

        /**
         * Getter method for the instance variable {@link #accDescr}.
         *
         * @return value of instance variable {@link #accDescr}
         */
        public String getAccDescr()
        {
            return this.accDescr;
        }

        /**
         * Setter method for instance variable {@link #accDescr}.
         *
         * @param _accDescr value for instance variable {@link #accDescr}
         * @return the detail bean
         */
        public DetailBean setAccDescr(final String _accDescr)
        {
            this.accDescr = _accDescr;
            return this;
        }

        /**
         * Getter method for the instance variable {@link #position}.
         *
         * @return value of instance variable {@link #position}
         */
        public Integer getPosition()
        {
            return this.position;
        }

        /**
         * Setter method for instance variable {@link #position}.
         *
         * @param _position value for instance variable {@link #position}
         * @return the detail bean
         */
        public DetailBean setPosition(final Integer _position)
        {
            this.position = _position;
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
         * @return the detail bean
         */
        public DetailBean setAmount(final BigDecimal _amount)
        {
            this.amount = _amount;
            return this;
        }

        /**
         * Gets the amount.
         *
         * @return the amount
         */
        public BigDecimal getRateAmount()
        {
            return this.rateAmount;
        }

        /**
         * Sets the rate amount.
         *
         * @param _rateAmount the rate amount
         * @return the detail bean
         */
        public DetailBean setRateAmount(final BigDecimal _rateAmount)
        {
            this.rateAmount = _rateAmount;
            return this;
        }

        /**
         * Gets the currency instance.
         *
         * @return the currency instance
         */
        public Instance getCurrencyInstance()
        {
            return this.currencyInstance;
        }

        /**
         * Sets the currency instance.
         *
         * @param _currencyInstance the currency instance
         * @return the detail bean
         */
        public DetailBean setCurrencyInstance(final Instance _currencyInstance)
        {
            this.currencyInstance = _currencyInstance;
            return this;
        }

        /**
         * Gets the rate currency instance.
         *
         * @return the rate currency instance
         */
        public Instance getRateCurrencyInstance()
        {
            return this.rateCurrencyInstance;
        }

        /**
         * Sets the rate currency instance.
         *
         * @param _rateCurrencyInstance the rate currency instance
         * @return the detail bean
         */
        public DetailBean setRateCurrencyInstance(final Instance _rateCurrencyInstance)
        {
            this.rateCurrencyInstance = _rateCurrencyInstance;
            return this;
        }

        /**
         * Gets the report amount.
         *
         * @return the report amount
         * @throws EFapsException on error
         */
        public BigDecimal getReportAmount()
            throws EFapsException
        {
            BigDecimal ret;
            final Instance currencyInstance = Instance.get(ParameterUtil.getParameterValue(getParent().getParameter(),
                            "currency"));
            if (currencyInstance.isValid()) {
                if (currencyInstance.equals(getCurrencyInstance())) {
                    ret = getAmount();
                } else if (currencyInstance.equals(getRateCurrencyInstance())) {
                    ret = getRateAmount();
                } else {
                    final RateInfo[] rateInfos = new Report().getCurrency(getParent().getParameter())
                                    .evaluateRateInfos(getParent().getParameter(), getParent()
                                    .getTransDate(), getRateCurrencyInstance(), currencyInstance);
                    ret = getRateAmount().divide(rateInfos[2].getRate(), 8, RoundingMode.HALF_UP);
                }
            } else {
                ret = getAmount();
            }
            return ret;
        }
    }
}
