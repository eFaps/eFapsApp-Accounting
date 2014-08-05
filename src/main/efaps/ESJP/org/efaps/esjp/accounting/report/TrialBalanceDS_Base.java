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
import org.efaps.db.Context;
import org.efaps.db.Instance;
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
 * @version $Id$
 */
@EFapsUUID("8c892184-88d0-4c28-ac34-2bff22632dc9")
@EFapsRevision("$Rev$")
public abstract class TrialBalanceDS_Base
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

        final List<DataBean> values = new ArrayList<>();
        final Map<Instance, DataBean> mapping = new HashMap<>();
        final List<Instance> instances = new ArrayList<Instance>();

        final DateTime dateFrom = new DateTime(_parameter.getParameterValue(
                        CIFormAccounting.Accounting_PReportTrialBalanceForm.dateFrom.name));
        final DateTime dateTo = new DateTime(_parameter.getParameterValue(
                        CIFormAccounting.Accounting_PReportTrialBalanceForm.dateTo.name));

        _jrParameters.put("DateFrom", dateFrom);
        _jrParameters.put("DateTo", dateTo);

        final String[] oids = (String[]) Context.getThreadContext().getSessionAttribute("selectedOIDs");
        for (final String oid : oids) {
            final Instance instancetmp = Instance.get(oid);
            if (instancetmp.isValid()) {
                instances.addAll(getAccountInst(_parameter, instancetmp, false));
            }
        }

        final QueryBuilder attrQueryBldr = new QueryBuilder(CIAccounting.TransactionAbstract);
        attrQueryBldr.addWhereAttrLessValue(CIAccounting.TransactionAbstract.Date, dateTo.plusDays(1));
        attrQueryBldr.addWhereAttrGreaterValue(CIAccounting.TransactionAbstract.Date, dateFrom.minusSeconds(1));

        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.TransactionPositionAbstract);
        queryBldr.addWhereAttrInQuery(CIAccounting.TransactionPositionAbstract.TransactionLink,
                        attrQueryBldr.getAttributeQuery(CIAccounting.TransactionAbstract.ID));
        queryBldr.addWhereAttrEqValue(CIAccounting.TransactionPositionAbstract.AccountLink, instances.toArray());
        final MultiPrintQuery multi = queryBldr.getPrint();
        final SelectBuilder selAcc = SelectBuilder.get().linkto(CIAccounting.TransactionPositionAbstract.AccountLink);
        final SelectBuilder selAccInst = new SelectBuilder(selAcc).instance();
        final SelectBuilder selAccName = new SelectBuilder(selAcc).attribute(CIAccounting.AccountAbstract.Name);
        final SelectBuilder selAccDescr = new SelectBuilder(selAcc).attribute(CIAccounting.AccountAbstract.Description);
        multi.addSelect(selAccInst, selAccName, selAccDescr);
        multi.addAttribute(CIAccounting.TransactionPositionAbstract.Amount);
        multi.execute();
        while (multi.next()) {
            final BigDecimal amount = multi.<BigDecimal>getAttribute(CIAccounting.TransactionPositionAbstract.Amount);
            final Instance accInst = multi.getSelect(selAccInst);
            DataBean bean;
            if (mapping.containsKey(accInst)) {
                bean = mapping.get(accInst);
            } else {
                bean = new DataBean();
                values.add(bean);
                mapping.put(accInst, bean);
                bean.setAccName(multi.<String>getSelect(selAccName));
                bean.setAccDescr(multi.<String>getSelect(selAccDescr));
            }
            if (multi.getCurrentInstance().getType().isKindOf(CIAccounting.TransactionPositionDebit.getType())) {
                bean.addDebit(amount);
            } else {
                bean.addCredit(amount);
            }
        }

        final ComparatorChain<DataBean> chain = new ComparatorChain<>();
        chain.addComparator(new Comparator<DataBean>()
        {

            @Override
            public int compare(final DataBean _arg0,
                               final DataBean _arg1)
            {
                return _arg0.getAccName().compareTo(_arg1.getAccName());
            }
        });
        Collections.sort(values, chain);

        setData(values);
    }

    public static class DataBean
    {

        private String accName;
        private String accDescr;
        private BigDecimal debit;
        private BigDecimal credit;

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
         * @param _amount
         */
        public void addCredit(final BigDecimal _amount)
        {
            if (this.credit == null) {
                this.credit = BigDecimal.ZERO;
            }
            this.credit = this.credit.add(_amount.abs());
        }

        /**
         * @param _amount
         */
        public void addDebit(final BigDecimal _amount)
        {
            if (this.debit == null) {
                this.debit = BigDecimal.ZERO;
            }
            this.debit = this.debit.add(_amount.abs());
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
         * Getter method for the instance variable {@link #debit}.
         *
         * @return value of instance variable {@link #debit}
         */
        public BigDecimal getDebit()
        {
            return this.debit;
        }

        /**
         * Setter method for instance variable {@link #debit}.
         *
         * @param _debit value for instance variable {@link #debit}
         */
        public void setDebit(final BigDecimal _debit)
        {
            this.debit = _debit;
        }

        /**
         * Getter method for the instance variable {@link #credit}.
         *
         * @return value of instance variable {@link #credit}
         */
        public BigDecimal getCredit()
        {
            return this.credit;
        }

        /**
         * Setter method for instance variable {@link #credit}.
         *
         * @param _credit value for instance variable {@link #credit}
         */
        public void setCredit(final BigDecimal _credit)
        {
            this.credit = _credit;
        }

        /**
         * Getter method for the instance variable {@link #debtor}.
         *
         * @return value of instance variable {@link #debtor}
         */
        public BigDecimal getDebtor()
        {
            final BigDecimal debitTmp = getDebit() == null ? BigDecimal.ZERO : getDebit();
            final BigDecimal creditTmp = getCredit() == null ? BigDecimal.ZERO : getCredit();
            final BigDecimal diffTmp = debitTmp.subtract(creditTmp);
            return diffTmp.compareTo(BigDecimal.ZERO) > 0 ? diffTmp : null;
        }

        /**
         * Getter method for the instance variable {@link #creditor}.
         *
         * @return value of instance variable {@link #creditor}
         */
        public BigDecimal getCreditor()
        {
            final BigDecimal debitTmp = getDebit() == null ? BigDecimal.ZERO : getDebit();
            final BigDecimal creditTmp = getCredit() == null ? BigDecimal.ZERO : getCredit();
            final BigDecimal diffTmp = creditTmp.subtract(debitTmp);
            return diffTmp.compareTo(BigDecimal.ZERO) > 0 ? diffTmp : null;
        }
    }
}
