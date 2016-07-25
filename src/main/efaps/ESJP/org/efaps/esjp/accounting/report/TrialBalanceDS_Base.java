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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.commons.collections4.comparators.ComparatorChain;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.CachedPrintQuery;
import org.efaps.db.Context;
import org.efaps.db.Instance;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.PrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.ci.CIFormAccounting;
import org.efaps.util.EFapsException;
import org.joda.time.DateTime;

import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JasperReport;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 * @version $Id$
 */
@EFapsUUID("8c892184-88d0-4c28-ac34-2bff22632dc9")
@EFapsApplication("eFapsApp-Accounting")
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

        final Map<Instance, DataBean> mapping = new HashMap<>();
        final List<Instance> instances = new ArrayList<>();

        final DateTime dateFrom = new DateTime(_parameter.getParameterValue(
                        CIFormAccounting.Accounting_PReportTrialBalanceForm.dateFrom.name));
        final DateTime dateTo = new DateTime(_parameter.getParameterValue(
                        CIFormAccounting.Accounting_PReportTrialBalanceForm.dateTo.name));
        final boolean includeInit = Boolean.parseBoolean(_parameter.getParameterValue(
                        CIFormAccounting.Accounting_PReportTrialBalanceForm.includeInit.name));
        int level = 2;
        final String levelStr =_parameter.getParameterValue(
                        CIFormAccounting.Accounting_PReportTrialBalanceForm.level.name);
        if (levelStr != null && !levelStr.isEmpty()) {
            level =Integer.parseInt(levelStr);
        }

        _jrParameters.put("IncludeInit", includeInit);
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
        if (!includeInit) {
            attrQueryBldr.addWhereAttrGreaterValue(CIAccounting.TransactionAbstract.Date, dateFrom.minusSeconds(1));
        }
        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.TransactionPositionAbstract);
        queryBldr.addWhereAttrInQuery(CIAccounting.TransactionPositionAbstract.TransactionLink,
                        attrQueryBldr.getAttributeQuery(CIAccounting.TransactionAbstract.ID));
        queryBldr.addWhereAttrEqValue(CIAccounting.TransactionPositionAbstract.AccountLink, instances.toArray());
        final MultiPrintQuery multi = queryBldr.getPrint();
        final SelectBuilder selTrans = SelectBuilder.get().linkto(
                        CIAccounting.TransactionPositionAbstract.TransactionLink);
        final SelectBuilder selTransDate = new SelectBuilder(selTrans).attribute(CIAccounting.TransactionAbstract.Date);
        final SelectBuilder selAcc = SelectBuilder.get().linkto(CIAccounting.TransactionPositionAbstract.AccountLink);
        final SelectBuilder selAccInst = new SelectBuilder(selAcc).instance();
        final SelectBuilder selAccName = new SelectBuilder(selAcc).attribute(CIAccounting.AccountAbstract.Name);
        final SelectBuilder selAccDescr = new SelectBuilder(selAcc).attribute(CIAccounting.AccountAbstract.Description);
        multi.addSelect(selTransDate, selAccInst, selAccName, selAccDescr);
        multi.addAttribute(CIAccounting.TransactionPositionAbstract.Amount);
        multi.execute();
        while (multi.next()) {
            final DateTime date = multi.<DateTime>getSelect(selTransDate);
            final BigDecimal amount = multi.<BigDecimal>getAttribute(CIAccounting.TransactionPositionAbstract.Amount);
            final Instance accInst = multi.getSelect(selAccInst);
            DataBean bean;
            if (mapping.containsKey(accInst)) {
                bean = mapping.get(accInst);
            } else {
                bean = new DataBean();
                mapping.put(accInst, bean);
                bean.setAccName(multi.<String>getSelect(selAccName));
                bean.setAccDescr(multi.<String>getSelect(selAccDescr));
            }
            if (multi.getCurrentInstance().getType().isKindOf(CIAccounting.TransactionPositionDebit.getType())) {
                if (includeInit && date.isBefore(dateFrom.plusSeconds(1))) {
                    bean.addInitDebit(amount);
                } else {
                    bean.addDebit(amount);
                }
            } else {
                if (includeInit && date.isBefore(dateFrom.plusSeconds(1))) {
                    bean.addInitCredit(amount);
                } else {
                    bean.addCredit(amount);
                }
            }
        }
        final List<DataBean> values;
        if (level > 0) {
            values = new ArrayList<>();
            final Map<Instance, Leveler> accMap = new HashMap<>();
            for (final Entry<Instance, DataBean> entry : mapping.entrySet()) {
                final Leveler accInfo = getLeveler(_parameter).setInstance(entry.getKey()).setLevel(level);
                if (accMap.containsKey(accInfo.getInstance())) {
                    accMap.get(accInfo.getInstance()).addBean(entry.getValue());
                } else {
                    accInfo.addBean(entry.getValue());
                    accMap.put(accInfo.getInstance(), accInfo);
                }
            }
            for (final Leveler leveler : accMap.values()) {
                values.add(leveler.getDataBean());
            }
        } else {
            values = new ArrayList<>(mapping.values());
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


    protected Leveler getLeveler(final Parameter _parameter)
    {
        return new Leveler();
    }


    public static class Leveler
    {
        private Instance instance;

        private final Set<DataBean> beans = new HashSet<>();

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
         * @return
         */
        public DataBean getDataBean()
            throws EFapsException
        {
            final DataBean ret = new DataBean();
            final PrintQuery print = new PrintQuery(getInstance());
            print.addAttribute(CIAccounting.AccountAbstract.Name, CIAccounting.AccountAbstract.Description);
            print.execute();
            ret.setAccDescr(print.<String>getAttribute(CIAccounting.AccountAbstract.Description));
            ret.setAccName(print.<String>getAttribute(CIAccounting.AccountAbstract.Name));
            for (final DataBean bean : this.beans) {
                ret.addDebit(bean.getDebit());
                ret.addCredit(bean.getCredit());
                ret.addInitDebit(bean.getInitDebit());
                ret.addInitCredit(bean.getInitCredit());
            }
            return ret;
        }

        /**
         * @param _value
         */
        public void addBean(final DataBean _bean)
        {
            this.beans.add(_bean);
        }

        /**
         * @param _level
         * @return
         */
        public Leveler setLevel(final int _level)
            throws EFapsException
        {
            int currentLevel = 0;
            Instance childInst = getInstance();
            final List<Instance> instances = new ArrayList<>();
            while (childInst != null && childInst.isValid()) {
                currentLevel++;
                instances.add(childInst);
                final PrintQuery print = new CachedPrintQuery(childInst).setLifespan(1).setLifespanUnit(
                                TimeUnit.MINUTES);
                final SelectBuilder sel = SelectBuilder.get().linkto(CIAccounting.AccountAbstract.ParentLink)
                                .instance();
                print.addSelect(sel);
                print.execute();
                childInst = print.getSelect(sel);
            }
            if (currentLevel > _level) {
                Collections.reverse(instances);
                setInstance(instances.get(_level - 1));
            }
            return this;
        }

        /**
         * Setter method for instance variable {@link #instance}.
         *
         * @param _instance value for instance variable {@link #instance}
         */
        public Leveler setInstance(final Instance _instance)
        {
            this.instance = _instance;
            return this;
        }
    }


    public static class DataBean
    {

        private String accName;
        private String accDescr;
        private BigDecimal debit;
        private BigDecimal credit;

        private BigDecimal initDebit;
        private BigDecimal initCredit;

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
            if (_amount != null) {
                if (this.credit == null) {
                    this.credit = BigDecimal.ZERO;
                }
                this.credit = this.credit.add(_amount.abs());
            }
        }

        /**
         * @param _amount
         */
        public void addDebit(final BigDecimal _amount)
        {
            if (_amount != null) {
                if (this.debit == null) {
                    this.debit = BigDecimal.ZERO;
                }
                this.debit = this.debit.add(_amount.abs());
            }
        }

        /**
         * @param _amount
         */
        public void addInitCredit(final BigDecimal _amount)
        {
            if (_amount != null) {
                if (this.initCredit == null) {
                    this.initCredit = BigDecimal.ZERO;
                }
                this.initCredit = this.initCredit.add(_amount.abs());
            }
        }

        /**
         * @param _amount
         */
        public void addInitDebit(final BigDecimal _amount)
        {
            if (_amount != null) {
                if (this.initDebit == null) {
                    this.initDebit = BigDecimal.ZERO;
                }
                this.initDebit = this.initDebit.add(_amount.abs());
            }
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

        /**
         * Getter method for the instance variable {@link #initDebtor}.
         *
         * @return value of instance variable {@link #initDebtor}
         */
        public BigDecimal getInitDebtor()
        {
            final BigDecimal debitTmp = getInitDebit() == null ? BigDecimal.ZERO : getInitDebit();
            final BigDecimal creditTmp = getInitCredit() == null ? BigDecimal.ZERO : getInitCredit();
            final BigDecimal diffTmp = debitTmp.subtract(creditTmp);
            return diffTmp.compareTo(BigDecimal.ZERO) > 0 ? diffTmp : null;
        }

        /**
         * Getter method for the instance variable {@link #initCreditor}.
         *
         * @return value of instance variable {@link #initCreditor}
         */
        public BigDecimal getInitCreditor()
        {
            final BigDecimal debitTmp = getInitDebit() == null ? BigDecimal.ZERO : getInitDebit();
            final BigDecimal creditTmp = getInitCredit() == null ? BigDecimal.ZERO : getInitCredit();
            final BigDecimal diffTmp = creditTmp.subtract(debitTmp);
            return diffTmp.compareTo(BigDecimal.ZERO) > 0 ? diffTmp : null;
        }

        /**
         * Getter method for the instance variable {@link #initDebtor}.
         *
         * @return value of instance variable {@link #initDebtor}
         */
        public BigDecimal getFinalDebtor()
        {
            final BigDecimal initDebitor = getInitDebtor() == null ? BigDecimal.ZERO : getInitDebtor();
            final BigDecimal initCreditor = getInitCreditor() == null ? BigDecimal.ZERO : getInitCreditor();
            final BigDecimal debitor = getDebtor() == null ? BigDecimal.ZERO : getDebtor();
            final BigDecimal creditor = getCreditor() == null ? BigDecimal.ZERO : getCreditor();
            final BigDecimal diffTmp = initDebitor.add(debitor).subtract(initCreditor).subtract(creditor);
            return diffTmp.compareTo(BigDecimal.ZERO) > 0 ? diffTmp : null;
        }

        /**
         * Getter method for the instance variable {@link #initDebtor}.
         *
         * @return value of instance variable {@link #initDebtor}
         */
        public BigDecimal getFinalCreditor()
        {
            final BigDecimal initDebitor = getInitDebtor() == null ? BigDecimal.ZERO : getInitDebtor();
            final BigDecimal initCreditor = getInitCreditor() == null ? BigDecimal.ZERO : getInitCreditor();
            final BigDecimal debitor = getDebtor() == null ? BigDecimal.ZERO : getDebtor();
            final BigDecimal creditor = getCreditor() == null ? BigDecimal.ZERO : getCreditor();
            final BigDecimal diffTmp = initCreditor.add(creditor).subtract(initDebitor).subtract(debitor);
            return diffTmp.compareTo(BigDecimal.ZERO) > 0 ? diffTmp : null;
        }

        /**
         * Getter method for the instance variable {@link #initDebit}.
         *
         * @return value of instance variable {@link #initDebit}
         */
        public BigDecimal getInitDebit()
        {
            return this.initDebit;
        }

        /**
         * Setter method for instance variable {@link #initDebit}.
         *
         * @param _initDebit value for instance variable {@link #initDebit}
         */
        public void setInitDebit(final BigDecimal _initDebit)
        {
            this.initDebit = _initDebit;
        }

        /**
         * Getter method for the instance variable {@link #initCredit}.
         *
         * @return value of instance variable {@link #initCredit}
         */
        public BigDecimal getInitCredit()
        {
            return this.initCredit;
        }

        /**
         * Setter method for instance variable {@link #initCredit}.
         *
         * @param _initCredit value for instance variable {@link #initCredit}
         */
        public void setInitCredit(final BigDecimal _initCredit)
        {
            this.initCredit = _initCredit;
        }
    }
}
