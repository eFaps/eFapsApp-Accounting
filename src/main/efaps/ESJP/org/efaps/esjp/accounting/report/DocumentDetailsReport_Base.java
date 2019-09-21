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

import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.comparators.ComparatorChain;
import org.efaps.admin.dbproperty.DBProperties;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Parameter.ParameterValues;
import org.efaps.admin.event.Return;
import org.efaps.admin.event.Return.ReturnValues;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.AttributeQuery;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.common.jasperreport.AbstractDynamicReport;
import org.efaps.esjp.common.jasperreport.datatype.DateTimeDate;
import org.efaps.esjp.sales.report.ComparativeReport;
import org.efaps.util.EFapsException;
import org.joda.time.DateTime;

import net.sf.dynamicreports.jasper.builder.JasperReportBuilder;
import net.sf.dynamicreports.report.base.expression.AbstractSimpleExpression;
import net.sf.dynamicreports.report.builder.DynamicReports;
import net.sf.dynamicreports.report.builder.column.TextColumnBuilder;
import net.sf.dynamicreports.report.builder.group.CustomGroupBuilder;
import net.sf.dynamicreports.report.builder.group.GroupBuilder;
import net.sf.dynamicreports.report.constant.GroupHeaderLayout;
import net.sf.dynamicreports.report.definition.ReportParameters;
import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
/**
 * TODO comment!
 *
 * @author The eFaps Team
 * 
 */
@EFapsUUID("88456845-0a0a-4480-acf5-66829d63c076")
@EFapsApplication("eFapsApp-Accounting")
public abstract class DocumentDetailsReport_Base
{

    /**
     * @param _parameter Parameter as passed by the eFasp API
     * @return Return containing the file
     * @throws EFapsException on error
     */
    public Return exportReport(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final Map<?, ?> props = (Map<?, ?>) _parameter.get(ParameterValues.PROPERTIES);
        final String mime = (String) props.get("Mime");
        final AbstractDynamicReport dyRp = getReport(_parameter);
        dyRp.setFileName(DBProperties.getProperty(ComparativeReport.class.getName() + ".FileName"));
        File file = null;
        if ("xls".equalsIgnoreCase(mime)) {
            file = dyRp.getExcel(_parameter);
        } else if ("pdf".equalsIgnoreCase(mime)) {
            file = dyRp.getPDF(_parameter);
        }
        ret.put(ReturnValues.VALUES, file);
        ret.put(ReturnValues.TRUE, true);
        return ret;
    }
    /**
     * @param _parameter Parameter as passed by the eFasp API
     * @return Return containing html snipplet
     * @throws EFapsException on error
     */
    public Return generateReport(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final AbstractDynamicReport dyRp = getReport(_parameter);
        final String html = dyRp.getHtmlSnipplet(_parameter);
        ret.put(ReturnValues.SNIPLETT, html);
        return ret;
    }

    /**
     * @param _parameter Parameter as passed by the eFasp API
     * @return the report class
     * @throws EFapsException on error
     */
    public AbstractDynamicReport getReport(final Parameter _parameter)
        throws EFapsException
    {
        return new DocDetailsReport();
    }

    /**
     * Dynamic report.
     */
    public static class DocDetailsReport
        extends AbstractDynamicReport
    {

        @SuppressWarnings("unchecked")
        @Override
        protected JRDataSource createDataSource(final Parameter _parameter)
            throws EFapsException
        {
            final List<DataBean> data = new ArrayList<>();

            final QueryBuilder attrQueryBldr = new QueryBuilder(CIAccounting.Transaction2SalesDocument);
            attrQueryBldr.addWhereAttrEqValue(CIAccounting.Transaction2SalesDocument.ToLink,
                            _parameter.getInstance());
            final AttributeQuery attrQuery = attrQueryBldr
                            .getAttributeQuery(CIAccounting.Transaction2SalesDocument.FromLink);
            final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.TransactionPositionAbstract);
            queryBldr.addWhereAttrInQuery(CIAccounting.TransactionPositionAbstract.TransactionLink, attrQuery);
            final MultiPrintQuery multi = queryBldr.getPrint();
            final SelectBuilder selAcName = SelectBuilder.get()
                            .linkto(CIAccounting.TransactionPositionAbstract.AccountLink)
                            .attribute(CIAccounting.AccountAbstract.Name);
            final SelectBuilder selAcDesc = SelectBuilder.get()
                            .linkto(CIAccounting.TransactionPositionAbstract.AccountLink)
                            .attribute(CIAccounting.AccountAbstract.Description);
            final SelectBuilder selTrName = SelectBuilder.get()
                            .linkto(CIAccounting.TransactionPositionAbstract.TransactionLink)
                            .attribute(CIAccounting.TransactionAbstract.Name);
            final SelectBuilder selTrIdent = SelectBuilder.get()
                            .linkto(CIAccounting.TransactionPositionAbstract.TransactionLink)
                            .attribute(CIAccounting.TransactionAbstract.Identifier);
            final SelectBuilder selTrDesc = SelectBuilder.get()
                            .linkto(CIAccounting.TransactionPositionAbstract.TransactionLink)
                            .attribute(CIAccounting.TransactionAbstract.Description);
            final SelectBuilder selTrDate = SelectBuilder.get()
                            .linkto(CIAccounting.TransactionPositionAbstract.TransactionLink)
                            .attribute(CIAccounting.TransactionAbstract.Date);
            final SelectBuilder selTrOid = SelectBuilder.get()
                            .linkto(CIAccounting.TransactionPositionAbstract.TransactionLink)
                            .oid();
            multi.addSelect(selTrOid, selAcName, selAcDesc, selTrName, selTrDesc, selTrDate, selTrIdent);
            multi.addAttribute(CIAccounting.TransactionPositionAbstract.Amount,
                            CIAccounting.TransactionPositionAbstract.Position);
            multi.execute();
            while (multi.next()) {
                final DataBean bean = getDataBean(_parameter);
                data.add(bean);
                bean.setTransOid(multi.<String>getSelect(selTrOid))
                    .setTransName(multi.<String>getSelect(selTrName))
                    .setTransIdent(multi.<String>getSelect(selTrIdent))
                    .setTransDate(multi.<DateTime>getSelect(selTrDate))
                    .setTransDesc(multi.<String>getSelect(selTrDesc))
                    .setAccDesc(multi.<String>getSelect(selAcDesc))
                    .setAccName(multi.<String>getSelect(selAcName))
                    .setPosition(multi.<Integer>getAttribute(CIAccounting.TransactionPositionAbstract.Position));
                if (multi.getCurrentInstance().getType().isKindOf(CIAccounting.TransactionPositionCredit.getType())) {
                    bean.setCredit(multi.<BigDecimal>getAttribute(CIAccounting.TransactionPositionAbstract.Amount)
                                    .abs());
                } else {
                    bean.setDebit(multi.<BigDecimal>getAttribute(CIAccounting.TransactionPositionAbstract.Amount)
                                    .abs());
                }
            }
            final ComparatorChain comChain = new ComparatorChain();
            comChain.addComparator(new Comparator<DataBean>()
            {
                @Override
                public int compare(final DataBean _o1,
                                   final DataBean _o2)
                {
                    return _o1.getTransName().compareTo(_o2.getTransName());
                }
            });
            comChain.addComparator(new Comparator<DataBean>()
            {
                @Override
                public int compare(final DataBean _o1,
                                   final DataBean _o2)
                {
                    return _o1.getTransDate().compareTo(_o2.getTransDate());
                }
            });
            comChain.addComparator(new Comparator<DataBean>()
            {
                @Override
                public int compare(final DataBean _o1,
                                   final DataBean _o2)
                {
                    return _o1.getTransOid().compareTo(_o2.getTransOid());
                }
            });
            comChain.addComparator(new Comparator<DataBean>()
            {
                @Override
                public int compare(final DataBean _o1,
                                   final DataBean _o2)
                {
                    return _o1.getPosition().compareTo(_o2.getPosition());
                }
            });


            Collections.sort(data, comChain);

            return new JRBeanCollectionDataSource(data);
        }

        @Override
        protected void addColumnDefinition(final Parameter _parameter,
                                          final JasperReportBuilder _builder)
            throws EFapsException
        {
            final TextColumnBuilder<String> trNameColumn = DynamicReports.col.column(DBProperties
                            .getProperty(DocumentDetailsReport.class.getName() + ".Column.trName"),
                            "transName", DynamicReports.type.stringType()).setWidth(50);
            final TextColumnBuilder<String> trNameIdent = DynamicReports.col.column(DBProperties
                            .getProperty(DocumentDetailsReport.class.getName() + ".Column.trIdent"),
                            "transIdent", DynamicReports.type.stringType());
            final TextColumnBuilder<DateTime> trDateColumn = DynamicReports.col.column(DBProperties
                            .getProperty(DocumentDetailsReport.class.getName() + ".Column.trDate"),
                            "transDate", DateTimeDate.get());
            final TextColumnBuilder<String> trDescColumn = DynamicReports.col.column(DBProperties
                            .getProperty(DocumentDetailsReport.class.getName() + ".Column.trDesc"),
                            "transDesc", DynamicReports.type.stringType()).setWidth(220);
            final TextColumnBuilder<String> acNameColumn = DynamicReports.col.column(DBProperties
                            .getProperty(DocumentDetailsReport.class.getName() + ".Column.acName"),
                            "accName", DynamicReports.type.stringType()).setWidth(50);
            final TextColumnBuilder<String> acDescColumn = DynamicReports.col.column(DBProperties
                            .getProperty(DocumentDetailsReport.class.getName() + ".Column.acDesc"),
                            "accDesc", DynamicReports.type.stringType()).setWidth(220);

            final TextColumnBuilder<BigDecimal> debitColumn = DynamicReports.col.column(DBProperties
                            .getProperty(DocumentDetailsReport.class.getName() + ".Column.debit"),
                            "debit", DynamicReports.type.bigDecimalType());
            final TextColumnBuilder<BigDecimal> creditColumn = DynamicReports.col.column(DBProperties
                            .getProperty(DocumentDetailsReport.class.getName() + ".Column.credit"),
                            "credit", DynamicReports.type.bigDecimalType());

            _builder.addColumn(trNameColumn, trNameIdent, trDateColumn, trDescColumn, acNameColumn, acDescColumn,
                            debitColumn, creditColumn);

            final CustomGroupBuilder transGroup = DynamicReports.grp.group("transOid", String.class)
                            .setHeaderLayout(GroupHeaderLayout.EMPTY);
            trNameColumn.setPrintWhenExpression(new NoRepeatedInGroupExpression(transGroup));
            trNameIdent.setPrintWhenExpression(new NoRepeatedInGroupExpression(transGroup));
            trDateColumn.setPrintWhenExpression(new NoRepeatedInGroupExpression(transGroup));
            trDescColumn.setPrintWhenExpression(new NoRepeatedInGroupExpression(transGroup));

            _builder.groupBy(transGroup);
        }

        /**
         * @param _parameter    Parameter as passed by the eFaps API
         * @return new  DataBean
         */
        protected DataBean getDataBean(final Parameter _parameter)
        {
            return new DataBean();
        }
    }

    /**
     * databean.
     */
    public static class DataBean
    {
        /**
         * Used for grouping;
         */
        private String transOid;

        /**
         * Name of the Transaction.
         */
        private String transName;

        /**
         * Identification of the Transaction.
         */
        private String transIdent;

        /**
         * Identification of the Transaction.
         */
        private DateTime transDate;

        /**
         * Description of the Transaction.
         */
        private String transDesc;

        /**
         * Name of the Account.
         */
        private String accName;

        /**
         * Description of the Account.
         */
        private String accDesc;

        /**
         * Position.
         */
        private Integer position;

        /**
         * Debit.
         */
        private BigDecimal debit;

        /**
         * Credit.
         */
        private BigDecimal credit;

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
         * @return this for chaining
         */
        public DataBean setTransName(final String _transName)
        {
            this.transName = _transName;
            return this;
        }

        /**
         * Getter method for the instance variable {@link #transIdent}.
         *
         * @return value of instance variable {@link #transIdent}
         */
        public String getTransIdent()
        {
            return this.transIdent;
        }

        /**
         * Setter method for instance variable {@link #transIdent}.
         *
         * @param _transIdent value for instance variable {@link #transIdent}
         * @return this for chaining
         */
        public DataBean setTransIdent(final String _transIdent)
        {
            this.transIdent = _transIdent;
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
         * Setter method for instance variable {@link #transDate}.
         *
         * @param _transDate value for instance variable {@link #transDate}
         * @return this for chaining
         */
        public DataBean setTransDate(final DateTime _transDate)
        {
            this.transDate = _transDate;
            return this;
        }

        /**
         * Getter method for the instance variable {@link #transDesc}.
         *
         * @return value of instance variable {@link #transDesc}
         */
        public String getTransDesc()
        {
            return this.transDesc;
        }

        /**
         * Setter method for instance variable {@link #transDesc}.
         *
         * @param _transDesc value for instance variable {@link #transDesc}
         * @return this for chaining
         */
        public DataBean setTransDesc(final String _transDesc)
        {
            this.transDesc = _transDesc;
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
         * @return this for chaining
         */
        public DataBean setAccName(final String _accName)
        {
            this.accName = _accName;
            return this;
        }

        /**
         * Getter method for the instance variable {@link #accDesc}.
         *
         * @return value of instance variable {@link #accDesc}
         */
        public String getAccDesc()
        {
            return this.accDesc;
        }

        /**
         * Setter method for instance variable {@link #accDesc}.
         *
         * @param _accDesc value for instance variable {@link #accDesc}
         * @return this for chaining
         */
        public DataBean setAccDesc(final String _accDesc)
        {
            this.accDesc = _accDesc;
            return this;
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
         * @return this for chaining
         */
        public DataBean setDebit(final BigDecimal _debit)
        {
            this.debit = _debit;
            return this;
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
         * @return this for chaining
         */
        public DataBean setCredit(final BigDecimal _credit)
        {
            this.credit = _credit;
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
         */
        public void setPosition(final Integer _position)
        {
            this.position = _position;
        }

        /**
         * Getter method for the instance variable {@link #oid}.
         *
         * @return value of instance variable {@link #oid}
         */
        public String getTransOid()
        {
            return this.transOid;
        }

        /**
         * Setter method for instance variable {@link #oid}.
         *
         * @param _oid value for instance variable {@link #oid}
         * @return this for chaining
         */
        public DataBean setTransOid(final String _oid)
        {
            this.transOid = _oid;
            return this;
        }
    }

    /**
     * Expression to prevent repetition.
     */
    public static class NoRepeatedInGroupExpression
        extends AbstractSimpleExpression<Boolean>
    {

        /**
         * Needed for serialization.
         */
        private static final long serialVersionUID = 1L;

        /**
         * GroupBuilder.
         */
        private final GroupBuilder<?> group;

        /**
         * @param _group group to be used
         */
        public NoRepeatedInGroupExpression(final GroupBuilder<?> _group)
        {
            this.group = _group;
        }

        /**
         * @param _reportParameters parameters to be added
         * @return true if repeated
         */
        @Override
        public Boolean evaluate(final ReportParameters _reportParameters)
        {
            final int groupNumber = DynamicReports.exp.groupRowNumber(this.group).evaluate(_reportParameters);
            return groupNumber == 1;
        }
    }
}
