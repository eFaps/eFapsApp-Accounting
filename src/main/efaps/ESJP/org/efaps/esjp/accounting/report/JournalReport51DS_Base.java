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

package org.efaps.esjp.accounting.report;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JasperReport;

import org.apache.commons.collections4.comparators.ComparatorChain;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.program.esjp.EFapsRevision;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.ci.CIFormAccounting;
import org.efaps.util.EFapsException;
import org.joda.time.DateTime;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 * @version $Id: JournalReport51DS_Base.java 13383 2014-07-22 02:47:47Z
 *          jan@moxter.net $
 */
@EFapsUUID("9994200b-40b2-466a-b1fe-bfd6e927e92e")
@EFapsRevision("$Rev$")
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

        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.TransactionPositionAbstract);
        final QueryBuilder transAttrQueryBldr = new QueryBuilder(CIAccounting.TransactionAbstract);
        transAttrQueryBldr.addWhereAttrLessValue(CIAccounting.TransactionAbstract.Date,
                        dateTo.withTimeAtStartOfDay().plusDays(1));
        transAttrQueryBldr.addWhereAttrGreaterValue(CIAccounting.TransactionAbstract.Date,
                        dateFrom.withTimeAtStartOfDay().minusSeconds(1));
        queryBldr.addWhereAttrInQuery(CIAccounting.TransactionPositionAbstract.TransactionLink,
                        transAttrQueryBldr.getAttributeQuery(CIAccounting.TransactionAbstract.ID));

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

        multi.addSelect(selAccName, selAccDescr, selTransIdentifier, selTransOID, selTransName, selTransDescr,
                        selTransDate);
        multi.addAttribute(CIAccounting.TransactionPositionAbstract.Amount,
                        CIAccounting.TransactionPositionAbstract.Position);
        multi.execute();
        final List<DataBean> values = new ArrayList<>();
        final Map<String, DataBean> map = new HashMap<>();
        while (multi.next()) {
            final String transOID = multi.<String>getSelect(selTransOID);
            final DataBean bean;
            if (map.containsKey(transOID)) {
                bean = map.get(transOID);
            } else {
                bean = new DataBean();
                values.add(bean);
                map.put(transOID, bean);
                bean.setTransOID(transOID);
                bean.setTransName(multi.<String>getSelect(selTransName));
                bean.setTransDate(multi.<DateTime>getSelect(selTransDate));
                bean.setTransDescr(multi.<String>getSelect(selTransDescr));
                bean.setTransIdentifier(multi.<String>getSelect(selTransIdentifier));
            }
            final DetailBean detailBean = new DetailBean();
            detailBean.setAccName(multi.<String>getSelect(selAccName));
            detailBean.setAccDescr(multi.<String>getSelect(selAccDescr));
            detailBean.setAmount(multi.<BigDecimal>getAttribute(CIAccounting.TransactionPositionAbstract.Amount));
            detailBean.setPosition(multi.<Integer>getAttribute(CIAccounting.TransactionPositionAbstract.Position));
            bean.addDetail(detailBean);
        }
        final ComparatorChain<DataBean> chain = new ComparatorChain<>();
        chain.addComparator(new Comparator<DataBean>()
        {

            @Override
            public int compare(final DataBean _arg0,
                               final DataBean _arg1)
            {
                return _arg0.getTransName().compareTo(_arg1.getTransName());
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

    public static class DataBean
    {

        private String transOID;
        private String transName;
        private DateTime transDate;
        private String transDescr;
        private String transIdentifier;

        private List<DetailBean> details = new ArrayList<>();


        public BigDecimal getDebit()
        {
            BigDecimal ret = BigDecimal.ZERO;
            for (final DetailBean bean : this.details) {
                if (bean.getAmount().compareTo(BigDecimal.ZERO) < 0) {
                    ret = ret.add(bean.getAmount().abs());
                }
            }
            return ret;

        }

        public BigDecimal getCredit()
        {
            BigDecimal ret = BigDecimal.ZERO;
            for (final DetailBean bean : this.details) {
                if (bean.getAmount().compareTo(BigDecimal.ZERO) > 0) {
                    ret = ret.add(bean.getAmount());
                }
            }
            return ret;
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
         * @param _detailBean
         */
        public void addDetail(final DetailBean _detailBean)
        {
            getDetails().add(_detailBean);
        }

        /**
         * Setter method for instance variable {@link #transDate}.
         *
         * @param _transDate value for instance variable {@link #transDate}
         */
        public void setTransDate(final DateTime _transDate)
        {
            this.transDate = _transDate;
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
         */
        public void setTransDescr(final String _transDescr)
        {
            this.transDescr = _transDescr;
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
         */
        public void setTransName(final String _transName)
        {
            this.transName = _transName;
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
         */
        public void setTransOID(final String _transOID)
        {
            this.transOID = _transOID;
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
         */
        public void setDetails(final List<DetailBean> _details)
        {
            this.details = _details;
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
         * @param _transIdentifier value for instance variable {@link #transIdentifier}
         */
        public void setTransIdentifier(final String _transIdentifier)
        {
            this.transIdentifier = _transIdentifier;
        }
    }

    public static class DetailBean
    {

        private String accName;
        private String accDescr;
        private Integer position;
        private BigDecimal amount;

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
         */
        public void setAccName(final String _accName)
        {
            this.accName = _accName;
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
         */
        public void setAccDescr(final String _accDescr)
        {
            this.accDescr = _accDescr;
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
         */
        public void setPosition(final Integer _position)
        {
            this.position = _position;
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
}
