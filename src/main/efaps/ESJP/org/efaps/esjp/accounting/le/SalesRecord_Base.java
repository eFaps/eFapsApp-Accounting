/*
 * Copyright 2003 - 2015 The eFaps Team
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

package org.efaps.esjp.accounting.le;

import java.math.BigDecimal;
import java.util.Map;

import org.efaps.admin.event.Parameter;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.dataexporter.model.LineNumberColumn;
import org.efaps.db.QueryBuilder;
import org.efaps.esjp.ci.CIERP;
import org.efaps.esjp.data.columns.export.FrmtColumn;
import org.efaps.esjp.data.columns.export.FrmtDateTimeColumn;
import org.efaps.esjp.data.columns.export.FrmtNumberColumn;
import org.efaps.esjp.erp.util.ERP;
import org.efaps.esjp.sales.report.SalesRecordReport;
import org.efaps.esjp.sales.report.SalesRecordReport_Base;
import org.efaps.esjp.sales.report.SalesRecordReport_Base.Column;
import org.efaps.esjp.sales.report.SalesRecordReport_Base.DynSalesRecordReport;
import org.efaps.util.EFapsException;
import org.joda.time.DateTime;

import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;

/**
 * 14.1 REGISTRO DE VENTAS E INGRESOS V5 30/12/2015
 * @author The eFaps Team
 */
@EFapsUUID("67776b3b-0aa9-45cf-a1a5-542e52bd619c")
@EFapsApplication("eFapsApp-Accounting")
public abstract class SalesRecord_Base
    extends AbstractExport
{

    /** The Constant PREFIX. */
    public static final String PREFIX = "LE";

    /** The Constant SUFFIX. */
    public static final String SUFFIX = "1.txt";

    /** The Constant IDENTIFIER. */
    public static final String IDENTIFIER = "140100";

    /** The Constant OPERTUNITY. */
    public static final String OPERTUNITY = "00";

    /** The Constant OPERATION. */
    public static final String OPERATION = "1";

    /** The Constant HASINFORMATION. */
    public static final String HASINFORMATION = "1";

    /** The Constant CURRENCY. */
    public static final String CURRENCY = "1";

    @Override
    public String getFileName(final Parameter _parameter)
        throws EFapsException
    {
        final String taxnumber = ERP.COMPANYTAX.get();
        final DateTime date = new DateTime(_parameter.getParameterValue("dateFrom"));
        final String dateStr = date.toString("yyyyMM00");

        return PurchaseRecord_Base.PREFIX + taxnumber + dateStr + PurchaseRecord_Base.IDENTIFIER
                        + PurchaseRecord_Base.OPERTUNITY + PurchaseRecord_Base.OPERATION
                        + PurchaseRecord_Base.HASINFORMATION
                        + PurchaseRecord_Base.CURRENCY + PurchaseRecord_Base.SUFFIX;
    }

    @Override
    public void addColumnDefinition(final Parameter _parameter,
                                    final Exporter _exporter)
    {
        _exporter.addColumns(new FrmtDateTimeColumn("", 8, "yyyyMM00"));//1
        _exporter.addColumns(new LineNumberColumn("corr", 40)); //2
        _exporter.addColumns(new FrmtColumn("no").setMaxWidth(1)); //3
        _exporter.addColumns(new FrmtDateTimeColumn(Column.DOCDATE.getKey(), 10, "dd/MM/yyyy")); //4
        _exporter.addColumns(new FrmtDateTimeColumn(Column.DOCDUEDATE.getKey(), 10, "dd/MM/yyyy")); //5
        _exporter.addColumns(new FrmtColumn(Column.DOCDOCTYPE.getKey()).setMaxWidth(2)); //6
        _exporter.addColumns(new FrmtColumn(Column.DOCSN.getKey()).setMaxWidth(20)); //7
        _exporter.addColumns(new FrmtColumn(Column.DOCNUMBER.getKey()).setMaxWidth(20)); //8



        //_exporter.addColumns(new FrmtColumn("no").setMaxWidth(4)); //8
        _exporter.addColumns(new FrmtColumn("no").setMaxWidth(20)); //9
        _exporter.addColumns(new FrmtColumn(Column.DOCDOCTYPE.getKey()).setMaxWidth(1)); //10
        _exporter.addColumns(new FrmtColumn(Column.CONTACTDOINUMBER.getKey()).setMaxWidth(15)); //11
        _exporter.addColumns(new FrmtColumn(Column.CONTACTNAME.getKey()).setMaxWidth(60)); //12
        _exporter.addColumns(new FrmtNumberColumn(Column.TAXABLEVAL.getKey(), 14, 2)); //13
        _exporter.addColumns(new FrmtNumberColumn("no", 14, 2)); //14
        _exporter.addColumns(new FrmtNumberColumn("no", 14, 2)); //15
        _exporter.addColumns(new FrmtNumberColumn("no", 14, 2)); //16
        _exporter.addColumns(new FrmtNumberColumn(Column.IGV.getKey(), 14, 2)); //17
        _exporter.addColumns(new FrmtNumberColumn("no", 14, 2)); //18
        _exporter.addColumns(new FrmtNumberColumn("no", 14, 2)); //19
        _exporter.addColumns(new FrmtNumberColumn("no", 14, 2)); //20
        _exporter.addColumns(new FrmtNumberColumn(Column.TOTAL.getKey(), 14, 2)); //21
        _exporter.addColumns(new FrmtNumberColumn(Column.RATE.getKey(), 14, 2)); //22
        _exporter.addColumns(new FrmtDateTimeColumn(Column.RELDATE.getKey(), 10, "dd/MM/yyyy")); //23
        _exporter.addColumns(new FrmtColumn(Column.RELDOCTYPE.getKey()).setMaxWidth(2)); //24
        _exporter.addColumns(new FrmtColumn(Column.RELSN.getKey()).setMaxWidth(20)); //25
        _exporter.addColumns(new FrmtColumn(Column.RELNUMBER.getKey()).setMaxWidth(20)); //26
        _exporter.addColumns(new FrmtColumn("no").setMaxWidth(1)); //27
        _exporter.addColumns(new FrmtColumn("no").setMaxWidth(1)); //28
        _exporter.addColumns(new FrmtColumn("no").setMaxWidth(1)); //29
        _exporter.addColumns(new FrmtColumn("no").setMaxWidth(1)); //30
        _exporter.addColumns(new FrmtColumn("no").setMaxWidth(1)); //31
        _exporter.addColumns(new FrmtColumn("no").setMaxWidth(1)); //32
        _exporter.addColumns(new FrmtColumn("no").setMaxWidth(1)); //33
        _exporter.addColumns(new FrmtColumn("no").setMaxWidth(1)); //34

    }

    @Override
    public void buildDataSource(final Parameter _parameter,
                                final Exporter _exporter)
        throws EFapsException
    {

        final String dateFrom = _parameter.getParameterValue("dateFrom");
        final DateTime date = new DateTime(dateFrom);

        final PLESalesRecordReport report = new PLESalesRecordReport();
        final JRMapCollectionDataSource values = report.getDynSalesRecord().getDataSource(_parameter);
        for (final Map<String, ?> value : values.getData()) {
            _exporter.addRow(date, //1, 2 is generated on the fly
                            null, //3
                            value.get(Column.DOCDATE.getKey()), //4
                            value.get(Column.DOCDUEDATE.getKey()),  //5
                            value.get(Column.DOCDOCTYPE.getKey()),  //6
                            value.get(Column.DOCSN.getKey()),       //7
                            value.get(Column.DOCNUMBER.getKey()),   //8



                            //null,                                   //8
                            null,                                   //9
                            value.get(Column.CONTACTDOINUMBER.getKey()),   //10
                            value.get(Column.CONTACTNAME.getKey()),  //11
                            BigDecimal.ZERO, //12
                            value.get(Column.TAXABLEVAL.getKey()), //13
                            BigDecimal.ZERO, //14
                            BigDecimal.ZERO, //15
                            BigDecimal.ZERO, //16
                            value.get(Column.IGV.getKey()), //17
                            BigDecimal.ZERO, //18
                            BigDecimal.ZERO, //19
                            BigDecimal.ZERO, //20
                            value.get(Column.TOTAL.getKey()), //21
                            value.get(Column.RATE.getKey()), //22
                            value.get(Column.RELDATE.getKey()), //23
                            value.get(Column.RELDOCTYPE.getKey()), //24
                            value.get(Column.RELSN.getKey()), //25
                            value.get(Column.RELNUMBER.getKey()), //26
                            null, //27
                            null, //28
                            null, //29
                            null, //30
                            null, //31
                            null, //32
                            null, //33
                            null //34
                            );

        }
    }

    public static class PLESalesRecordReport
        extends SalesRecordReport
    {

        /** The record. */
        private final DynSalesRecord record = new DynSalesRecord(this);

        /**
         * Gets the dyn sales record.
         *
         * @return the dyn sales record
         */
        protected DynSalesRecord getDynSalesRecord()
        {
            return this.record;
        }
    }

    /**
     * The Class DynSalesRecord.
     */
    public static class DynSalesRecord
        extends DynSalesRecordReport
    {

        /**
         * Instantiates a new dyn sales record.
         *
         * @param _filteredReport the filtered report
         */
        public DynSalesRecord(final SalesRecordReport_Base _filteredReport)
        {
            super(_filteredReport);
        }

        /**
         * Gets the data source.
         *
         * @param _parameter the parameter
         * @return the data source
         * @throws EFapsException the e faps exception
         */
        public JRMapCollectionDataSource getDataSource(final Parameter _parameter)
            throws EFapsException
        {
            return (JRMapCollectionDataSource) createDataSource(_parameter);
        }

        @Override
        protected void add2QueryBldr(final Parameter _parameter,
                                     final QueryBuilder _queryBldr)
            throws EFapsException
        {
            final DateTime dateFrom = new DateTime(_parameter.getParameterValue("dateFrom"));
            final DateTime dateTo = new DateTime(_parameter.getParameterValue("dateTo"));
            _queryBldr.addWhereAttrGreaterValue(CIERP.DocumentAbstract.Date, dateFrom.minusMinutes(1));
            _queryBldr.addWhereAttrLessValue(CIERP.DocumentAbstract.Date, dateTo.plusDays(1));
        }
    }
}
