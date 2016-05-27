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

package org.efaps.esjp.accounting.export.sc;

import java.math.BigDecimal;

import org.efaps.admin.event.Parameter;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.AttributeQuery;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.data.columns.export.FrmtColumn;
import org.efaps.esjp.data.columns.export.FrmtDateTimeColumn;
import org.efaps.esjp.data.columns.export.FrmtNumberColumn;
import org.efaps.util.EFapsException;
import org.joda.time.DateTime;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 */
@EFapsUUID("245e77d8-eda7-4ce3-9153-1c71fec8eefa")
@EFapsApplication("eFapsApp-Accounting")
public abstract class Journal_Base
    extends AbstractSCExport
{

    @Override
    public void addColumnDefinition(final Parameter _parameter,
                                    final Exporter _exporter)
    {
        _exporter.addColumns(new FrmtColumn("todo", 7)); // 1
        _exporter.addColumns(new FrmtDateTimeColumn("transDate", 8, "dd/MM/yy"));
        _exporter.addColumns(new FrmtColumn("accName", 10));
        _exporter.addColumns(new FrmtNumberColumn("amountDebit", 11,2 ));
        _exporter.addColumns(new FrmtNumberColumn("amountCredit", 11, 2));
    }

    @Override
    public void buildDataSource(final Parameter _parameter,
                                final Exporter _exporter)
        throws EFapsException
    {
        // final DateTime dateFrom = new DateTime(_parameter.getParameterValue(
        // CIFormAccounting.Accounting_PReportJournal51Form.dateFrom.name));
        // final DateTime dateTo = new DateTime(_parameter.getParameterValue(
        // CIFormAccounting.Accounting_PReportJournal51Form.dateTo.name));

        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.TransactionPositionAbstract);
        final QueryBuilder transAttrQueryBldr = new QueryBuilder(CIAccounting.TransactionAbstract);
        // transAttrQueryBldr.addWhereAttrLessValue(CIAccounting.TransactionAbstract.Date,
        // dateTo.withTimeAtStartOfDay().plusDays(1));
        // transAttrQueryBldr.addWhereAttrGreaterValue(CIAccounting.TransactionAbstract.Date,
        // dateFrom.withTimeAtStartOfDay().minusSeconds(1));
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

        multi.addSelect(selAccName, selAccDescr, selTransIdentifier, selTransOID, selTransName, selTransDescr,
                        selTransDate);
        multi.addAttribute(CIAccounting.TransactionPositionAbstract.Amount,
                        CIAccounting.TransactionPositionAbstract.Position);
        multi.execute();
        while (multi.next()) {
            final DataBean bean = new DataBean()
                            .setTransDate(multi.<DateTime>getSelect(selTransDate))
                            .setAccName(multi.<String>getSelect(selAccName))
                            .setAmountDebit(BigDecimal.ZERO)
                            .setAmountCredit(BigDecimal.ZERO);
            _exporter.addBeanRows(bean);
        }
    }

    @Override
    public String getFileName(final Parameter _parameter)
        throws EFapsException
    {
        return "HalloWelt";
    }

    public static class DataBean
    {

        private String todo;

        /** The trans date. */
        private DateTime transDate;

        /** The acc name. */
        private String accName;

        /** The amount debit. */
        private BigDecimal amountDebit;

        /** The amount credit. */
        private BigDecimal amountCredit;

        /**
         * Getter method for the instance variable {@link #todo}.
         *
         * @return value of instance variable {@link #todo}
         */
        public String getTodo()
        {
            return this.todo;
        }

        /**
         * Setter method for instance variable {@link #todo}.
         *
         * @param _todo value for instance variable {@link #todo}
         */
        public void setTodo(final String _todo)
        {
            this.todo = _todo;
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
         * @return the data bean
         */
        public DataBean setAccName(final String _accName)
        {
            this.accName = _accName;
            return this;
        }


        /**
         * Getter method for the instance variable {@link #amountDebit}.
         *
         * @return value of instance variable {@link #amountDebit}
         */
        public BigDecimal getAmountDebit()
        {
            return this.amountDebit;
        }


        /**
         * Setter method for instance variable {@link #amountDebit}.
         *
         * @param _amountDebit value for instance variable {@link #amountDebit}
         */
        public DataBean setAmountDebit(final BigDecimal _amountDebit)
        {
            this.amountDebit = _amountDebit;
            return this;
        }


        /**
         * Getter method for the instance variable {@link #amountCredit}.
         *
         * @return value of instance variable {@link #amountCredit}
         */
        public BigDecimal getAmountCredit()
        {
            return this.amountCredit;
        }


        /**
         * Setter method for instance variable {@link #amountCredit}.
         *
         * @param _amountCredit value for instance variable {@link #amountCredit}
         */
        public DataBean setAmountCredit(final BigDecimal _amountCredit)
        {
            this.amountCredit = _amountCredit;
            return this;
        }
    }
}
