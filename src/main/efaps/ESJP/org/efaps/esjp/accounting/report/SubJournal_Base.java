/*
 * Copyright 2003 - 2013 The eFaps Team
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
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.sf.dynamicreports.jasper.builder.JasperReportBuilder;
import net.sf.dynamicreports.report.base.expression.AbstractSimpleExpression;
import net.sf.dynamicreports.report.builder.DynamicReports;
import net.sf.dynamicreports.report.builder.VariableBuilder;
import net.sf.dynamicreports.report.builder.column.ComponentColumnBuilder;
import net.sf.dynamicreports.report.builder.column.TextColumnBuilder;
import net.sf.dynamicreports.report.builder.component.HorizontalListBuilder;
import net.sf.dynamicreports.report.builder.component.SubreportBuilder;
import net.sf.dynamicreports.report.builder.component.TextFieldBuilder;
import net.sf.dynamicreports.report.builder.style.StyleBuilder;
import net.sf.dynamicreports.report.constant.Calculation;
import net.sf.dynamicreports.report.constant.Evaluation;
import net.sf.dynamicreports.report.constant.HorizontalAlignment;
import net.sf.dynamicreports.report.constant.StretchType;
import net.sf.dynamicreports.report.definition.ReportParameters;
import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;

import org.efaps.admin.common.SystemConfiguration;
import org.efaps.admin.dbproperty.DBProperties;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Return;
import org.efaps.admin.event.Return.ReturnValues;
import org.efaps.admin.program.esjp.EFapsRevision;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.AttributeQuery;
import org.efaps.db.Instance;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.PrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.ci.CIFormAccounting;
import org.efaps.esjp.common.jasperreport.AbstractDynamicReport;
import org.efaps.esjp.erp.util.ERP;
import org.efaps.esjp.erp.util.ERPSettings;
import org.efaps.util.EFapsException;
import org.joda.time.DateTime;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 * @version $Id$
 */
@EFapsUUID("2fb19c5b-419b-4503-9f70-11d5d0544713")
@EFapsRevision("$Rev$")
public abstract class SubJournal_Base
{

    public Return createReport(final Parameter _parameter)
        throws EFapsException
    {
        final Instance subJourInst = _parameter.getCallInstance();
        final PrintQuery print = new PrintQuery(subJourInst);
        print.addAttribute(CIAccounting.ReportSubJournal.Name);
        print.execute();
        final String name = print.<String>getAttribute(CIAccounting.ReportSubJournal.Name);

        final DateTime dateFrom = new DateTime(_parameter
                        .getParameterValue(CIFormAccounting.Accounting_Journal_CreateForm.dateFrom.name));

        final DateTime dateTo = new DateTime(_parameter
                        .getParameterValue(CIFormAccounting.Accounting_Journal_CreateForm.dateTo.name));

        final String mime = _parameter.getParameterValue(CIFormAccounting.Accounting_Journal_CreateForm.mime.name);

        final AbstractDynamicReport dyRp = getReport(_parameter);
        dyRp.getReport().addParameter("FromDate", dateFrom.toDate());
        dyRp.getReport().addParameter("ToDate", dateTo.toDate());
        dyRp.getReport().addParameter("ReportName",
                        DBProperties.getFormatedDBProperty(SubJournal.class.getName() + ".ReportName", name));

        dyRp.setFileName(DBProperties.getFormatedDBProperty(SubJournal.class.getName() + ".FileName", name));

        final SystemConfiguration config = ERP.getSysConfig();
        if (config != null) {
            final String companyName = config.getAttributeValue(ERPSettings.COMPANYNAME);
            final String companyTaxNumb = config.getAttributeValue(ERPSettings.COMPANYTAX);

            if (companyName != null && companyTaxNumb != null
                            && !companyName.isEmpty() && !companyTaxNumb.isEmpty()) {
                dyRp.getReport().addParameter("CompanyName", companyName);
                dyRp.getReport().addParameter("CompanyTaxNum", companyTaxNumb);
            }
        }

        File file = null;
        if ("xls".equalsIgnoreCase(mime)) {
            file = dyRp.getExcel(_parameter);
        } else if ("pdf".equalsIgnoreCase(mime)) {
            file = dyRp.getPDF(_parameter);
        }

        final Return ret = new Return();
        ret.put(ReturnValues.VALUES, file);
        ret.put(ReturnValues.TRUE, true);
        return ret;
    }



    protected AbstractDynamicReport getReport(final Parameter _parameter)
        throws EFapsException
    {
        return new SubJournalReport();
    }

    protected SubJournalData getSubJournalData(final Parameter _parameter)
    {
        return new SubJournalData();
    }

    protected SubreportDesign getSubreportDesign(final Parameter _parameter)
    {
        return new SubreportDesign();
    }
    protected SubreportData getSubreportData(final Parameter _parameter)
    {
        return new SubreportData();
    }


    public class SubJournalReport
        extends AbstractDynamicReport
    {

        @Override
        protected StyleBuilder getColumnStyle4Pdf(final Parameter _parameter)
            throws EFapsException
        {
            return super.getColumnStyle4Pdf(_parameter).setTopBorder(DynamicReports.stl.pen1Point())
                            .setBottomBorder(DynamicReports.stl.pen1Point());
        }


        @Override
        protected JRDataSource createDataSource(final Parameter _parameter)
            throws EFapsException
        {
            final List<SubJournalData> datasource = new ArrayList<SubJournalData>();

            final Map<Instance, SubJournalData> values = new HashMap<Instance, SubJournalData>();
            final Instance subJourInst = _parameter.getCallInstance();
            final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.ReportSubJournal2Transaction);
            queryBldr.addWhereAttrEqValue(CIAccounting.ReportSubJournal2Transaction.FromLink, subJourInst);
            final MultiPrintQuery multi = queryBldr.getPrint();
            final SelectBuilder selTransInst = SelectBuilder.get()
                            .linkto(CIAccounting.ReportSubJournal2Transaction.ToLink).instance();
            final SelectBuilder selDate = SelectBuilder.get()
                            .linkto(CIAccounting.ReportSubJournal2Transaction.ToLink)
                            .attribute(CIAccounting.TransactionAbstract.Date);
            final SelectBuilder selName = SelectBuilder.get()
                            .linkto(CIAccounting.ReportSubJournal2Transaction.ToLink)
                            .attribute(CIAccounting.TransactionAbstract.Name);
            final SelectBuilder selDescr = SelectBuilder.get()
                            .linkto(CIAccounting.ReportSubJournal2Transaction.ToLink)
                            .attribute(CIAccounting.TransactionAbstract.Description);
            multi.addSelect(selTransInst, selDate, selName, selDescr);
            multi.addAttribute(CIAccounting.ReportSubJournal2Transaction.Number);
            multi.execute();
            while (multi.next()) {
                final String number = multi.<String>getAttribute(CIAccounting.ReportSubJournal2Transaction.Number);
                final Instance transInst = multi.<Instance>getSelect(selTransInst);
                final DateTime date = multi.<DateTime>getSelect(selDate);
                final String name = multi.<String>getSelect(selName);
                final String descr = multi.<String>getSelect(selDescr);

                final SubJournalData data = getSubJournalData(_parameter);
                data.setNumber(number);
                data.setDate(date.toDate());
                data.setName(name);
                data.setDescr(descr);
                values.put(transInst, data);
            }

            final QueryBuilder attrQueryBldr = new QueryBuilder(CIAccounting.ReportSubJournal2Transaction);
            attrQueryBldr.addWhereAttrEqValue(CIAccounting.ReportSubJournal2Transaction.FromLink, subJourInst);
            final AttributeQuery attrQuery = attrQueryBldr
                            .getAttributeQuery(CIAccounting.ReportSubJournal2Transaction.ToLink);
            final QueryBuilder posQueryBldr = new QueryBuilder(CIAccounting.TransactionPositionAbstract);
            posQueryBldr.addWhereAttrInQuery(CIAccounting.TransactionPositionAbstract.TransactionLink, attrQuery);
            final MultiPrintQuery posMulti = posQueryBldr.getPrint();
            final SelectBuilder selTransInst2 = SelectBuilder.get()
                            .linkto(CIAccounting.TransactionPositionAbstract.TransactionLink).instance();
            final SelectBuilder selAccName = SelectBuilder.get()
                            .linkto(CIAccounting.TransactionPositionAbstract.AccountLink)
                            .attribute(CIAccounting.AccountAbstract.Name);
            final SelectBuilder selAccDesc = SelectBuilder.get()
                            .linkto(CIAccounting.TransactionPositionAbstract.AccountLink)
                            .attribute(CIAccounting.AccountAbstract.Description);
            posMulti.addSelect(selTransInst2, selAccName, selAccDesc);
            posMulti.addAttribute(CIAccounting.TransactionPositionAbstract.Amount);
            posMulti.execute();
            while (posMulti.next()) {
                final Instance transInst = posMulti.<Instance>getSelect(selTransInst2);
                final SubJournalData data = values.get(transInst);
                final Map<String, Object> map = new HashMap<String, Object>();
                final BigDecimal amount = posMulti
                                .<BigDecimal>getAttribute(CIAccounting.TransactionPositionAbstract.Amount);
                if (amount.compareTo(BigDecimal.ZERO) < 0) {
                    map.put("debit", amount.abs());
                    data.addDebit(amount.abs());
                } else {
                    map.put("credit", amount.abs());
                    data.addCredit(amount.abs());
                }
                map.put("accName", posMulti.<String>getSelect(selAccName));
                map.put("accDescr", posMulti.<String>getSelect(selAccDesc));

                data.addPosition(map);
            }
            datasource.addAll(values.values());
            return new JRBeanCollectionDataSource(datasource);
        }

        @Override
        protected void addColumnDefintion(final Parameter _parameter,
                                          final JasperReportBuilder _builder)
            throws EFapsException
        {
            final VariableBuilder<BigDecimal> debitSumPage = DynamicReports.variable("debitSum", BigDecimal.class,
                            Calculation.SUM);
            debitSumPage.setResetType(Evaluation.PAGE);
            final VariableBuilder<BigDecimal> creditSumPage = DynamicReports.variable("creditSum", BigDecimal.class,
                            Calculation.SUM);
            creditSumPage.setResetType(Evaluation.PAGE);

            final VariableBuilder<BigDecimal> debitSumTotal = DynamicReports.variable("debitSum", BigDecimal.class,
                            Calculation.SUM);

            final VariableBuilder<BigDecimal> creditSumTotal = DynamicReports.variable("creditSum", BigDecimal.class,
                            Calculation.SUM);

            final TextColumnBuilder<Integer> rowNumCol = DynamicReports.col.reportRowNumberColumn().setWidth(3);

            final TextColumnBuilder<String> nameColumn = DynamicReports.col.column("name",
                            DynamicReports.type.stringType()).setWidth(8);

            final TextColumnBuilder<String> numberColumn = DynamicReports.col.column("number",
                            DynamicReports.type.stringType()).setWidth(8);

            final TextColumnBuilder<Date> dateColumn = DynamicReports.col.column("date",
                            DynamicReports.type.dateType()).setWidth(8);

            final TextColumnBuilder<String> descrColumn = DynamicReports.col.column("descr",
                            DynamicReports.type.stringType()).setWidth(19);

            final SubreportBuilder subreport = DynamicReports.cmp.subreport(getSubreportDesign(_parameter))
                            .setDataSource(getSubreportData(_parameter))
                            .setStretchType(StretchType.RELATIVE_TO_TALLEST_OBJECT)
                            .setStyle(DynamicReports.stl.style().setBorder(DynamicReports.stl.pen1Point()));

            final ComponentColumnBuilder posColumn = DynamicReports.col.componentColumn(subreport).setWidth(54);

            final TextFieldBuilder<String> debitSbtPage = DynamicReports.cmp.text(new CustomTextSubtotal(debitSumPage));
            final TextFieldBuilder<String> creditSbtPage = DynamicReports.cmp
                            .text(new CustomTextSubtotal(creditSumPage));
            final TextFieldBuilder<String> debitSbtTotal = DynamicReports.cmp
                            .text(new CustomTextSubtotal(debitSumTotal));
            final TextFieldBuilder<String> creditSbtTotal = DynamicReports.cmp.text(new CustomTextSubtotal(
                            creditSumTotal));

            final HorizontalListBuilder header = DynamicReports.cmp.horizontalList();
            header.add(DynamicReports.cmp.text("N").setWidth(3))
                            .add(DynamicReports.cmp.text("Name").setWidth(8))
                            .add(DynamicReports.cmp.text("Number").setWidth(8))
                            .add(DynamicReports.cmp.text("Date").setWidth(8))
                            .add(DynamicReports.cmp.text("Description").setWidth(19))

                            .add(DynamicReports.cmp.text("accDescr").setWidth(9))
                            .add(DynamicReports.cmp.text("accName").setWidth(27))
                            .add(DynamicReports.cmp.text("Debit").setWidth(9))
                            .add(DynamicReports.cmp.text("Credit").setWidth(9));

            final HorizontalListBuilder sumList = DynamicReports.cmp.horizontalList();
            sumList.add(DynamicReports.cmp.text("").setWidth(82))
                   .add(debitSbtPage.setWidth(9).setHorizontalAlignment(HorizontalAlignment.RIGHT))
                   .add(creditSbtPage.setWidth(9).setHorizontalAlignment(HorizontalAlignment.RIGHT))
                   .newRow()
                   .add(DynamicReports.cmp.text("").setWidth(82))
                   .add(debitSbtTotal.setWidth(9).setHorizontalAlignment(HorizontalAlignment.RIGHT))
                   .add(creditSbtTotal.setWidth(9).setHorizontalAlignment(HorizontalAlignment.RIGHT));

            _builder.fields(DynamicReports.field("positions", List.class))
                            .addColumn(rowNumCol, nameColumn, numberColumn, dateColumn, descrColumn, posColumn)
                            .addColumnHeader(header)
                            .variables(debitSumPage, creditSumPage, debitSumTotal, creditSumTotal)
                            .addColumnFooter(sumList);
        }
    }

    public static class SubJournalData
    {
        private BigDecimal debitSum = BigDecimal.ZERO;
        private BigDecimal creditSum = BigDecimal.ZERO;

        private String number;
        private String name;
        private String descr;
        private Date date;
        private final Collection<Map<String, Object>> positions = new ArrayList<Map<String, Object>>();

        /**
         * Getter method for the instance variable {@link #number}.
         *
         * @return value of instance variable {@link #number}
         */
        public String getNumber()
        {
            return this.number;
        }

        /**
         * Setter method for instance variable {@link #number}.
         *
         * @param _number value for instance variable {@link #number}
         */
        public void setNumber(final String _number)
        {
            this.number = _number;
        }


        /**
         * Getter method for the instance variable {@link #positions}.
         *
         * @return value of instance variable {@link #positions}
         */
        public Collection<Map<String, Object>> getPositions()
        {
            return this.positions;
        }


        /**
         * Setter method for instance variable {@link #positions}.
         *
         * @param _positions value for instance variable {@link #positions}
         */
        public void addPosition(final Map<String, Object> _position)
        {
            this.positions.add(_position);
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
        public void setName(final String _name)
        {
            this.name = _name;
        }


        /**
         * Getter method for the instance variable {@link #descr}.
         *
         * @return value of instance variable {@link #descr}
         */
        public String getDescr()
        {
            return this.descr;
        }


        /**
         * Setter method for instance variable {@link #descr}.
         *
         * @param _descr value for instance variable {@link #descr}
         */
        public void setDescr(final String _descr)
        {
            this.descr = _descr;
        }


        /**
         * Getter method for the instance variable {@link #date}.
         *
         * @return value of instance variable {@link #date}
         */
        public Date getDate()
        {
            return this.date;
        }


        /**
         * Setter method for instance variable {@link #date}.
         *
         * @param _date value for instance variable {@link #date}
         */
        public void setDate(final Date _date)
        {
            this.date = _date;
        }


        /**
         * Getter method for the instance variable {@link #debitSum}.
         *
         * @return value of instance variable {@link #debitSum}
         */
        public BigDecimal getDebitSum()
        {
            return this.debitSum;
        }


        /**
         * Setter method for instance variable {@link #debitSum}.
         *
         * @param _debitSum value for instance variable {@link #debitSum}
         */
        public void setDebitSum(final BigDecimal _debitSum)
        {
            this.debitSum = _debitSum;
        }


        /**
         * Getter method for the instance variable {@link #creditSum}.
         *
         * @return value of instance variable {@link #creditSum}
         */
        public BigDecimal getCreditSum()
        {
            return this.creditSum;
        }


        /**
         * Setter method for instance variable {@link #creditSum}.
         *
         * @param _creditSum value for instance variable {@link #creditSum}
         */
        public void setCreditSum(final BigDecimal _creditSum)
        {
            this.creditSum = _creditSum;
        }

        public SubJournalData addCredit(final BigDecimal _credit)
        {
            this.creditSum = this.creditSum.add(_credit);
            return this;
        }
        public SubJournalData addDebit(final BigDecimal _debit)
        {
            this.debitSum = this.debitSum.add(_debit);
            return this;
        }
    }

    public static class SubreportDesign
        extends AbstractSimpleExpression<JasperReportBuilder>
    {

        private static final long serialVersionUID = 1L;

        @Override
        public JasperReportBuilder evaluate(final ReportParameters reportParameters)
        {
            final TextColumnBuilder<String> accName = DynamicReports.col.column("accName",
                            DynamicReports.type.stringType()).setWidth(17);
            final TextColumnBuilder<String> accDescr = DynamicReports.col.column("accDescr",
                            DynamicReports.type.stringType()).setWidth(49);

            final TextColumnBuilder<BigDecimal> debit = DynamicReports.col.column("debit",
                            DynamicReports.type.bigDecimalType()).setWidth(17);
            final TextColumnBuilder<BigDecimal> credit = DynamicReports.col.column("credit",
                            DynamicReports.type.bigDecimalType()).setWidth(17);
            final JasperReportBuilder report = DynamicReports.report()
                            .columns(accName, accDescr, debit, credit)
                            .setColumnStyle(DynamicReports.stl.style().setPadding(DynamicReports.stl.padding(2))
                                            .setLeftBorder(DynamicReports.stl.pen1Point())
                                            .setRightBorder(DynamicReports.stl.pen1Point())
                                            .setBottomBorder(DynamicReports.stl.pen1Point())
                                            .setTopBorder(DynamicReports.stl.pen1Point()));
            return report;
        }
    }

    public static class SubreportData
        extends AbstractSimpleExpression<JRDataSource>
    {

        private static final long serialVersionUID = 1L;

        @Override
        public JRDataSource evaluate(final ReportParameters reportParameters)
        {
            final Collection<Map<String, ?>> value = reportParameters.getValue("positions");
            return new JRMapCollectionDataSource(value);
        }
    }


    private class CustomTextSubtotal
        extends AbstractSimpleExpression<String>
    {

        private static final long serialVersionUID = 1L;
        private final VariableBuilder<BigDecimal>total;

        public CustomTextSubtotal( final VariableBuilder<BigDecimal> _total)
        {
            this.total = _total;
        }

        @Override
        public String evaluate(final ReportParameters _reportParameters)
        {
            return DynamicReports.type.bigDecimalType().valueToString(this.total, _reportParameters);
        }
    }

}
