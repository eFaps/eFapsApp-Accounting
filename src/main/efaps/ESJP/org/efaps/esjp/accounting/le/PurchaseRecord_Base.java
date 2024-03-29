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
package org.efaps.esjp.accounting.le;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.efaps.admin.event.Parameter;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.dataexporter.model.BooleanColumn.Format;
import org.efaps.esjp.accounting.report.PurchaseRecordReport;
import org.efaps.esjp.accounting.report.PurchaseRecordReport_Base.Field;
import org.efaps.esjp.data.columns.export.FrmtBooleanColumn;
import org.efaps.esjp.data.columns.export.FrmtColumn;
import org.efaps.esjp.data.columns.export.FrmtDateTimeColumn;
import org.efaps.esjp.data.columns.export.FrmtNumberColumn;
import org.efaps.esjp.erp.util.ERP;
import org.efaps.util.EFapsException;
import org.joda.time.DateTime;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 * 
 */
@EFapsUUID("dc809e4c-205b-4f85-9367-b86e94ff1949")
@EFapsApplication("eFapsApp-Accounting")
public abstract class PurchaseRecord_Base
    extends AbstractExport
{

    public static final String PREFIX = "LE";

    public static final String SUFFIX = "1.txt";

    public static final String IDENTIFIER = "080100";

    public static final String OPERTUNITY = "00";

    public static final String OPERATION = "1";

    public static final String HASINFORMATION = "1";

    public static final String CURRENCY = "1";

    @Override
    public String getFileName(final Parameter _parameter)
        throws EFapsException
    {
        final String taxnumber = ERP.COMPANY_TAX.get();
        final DateTime date4Purchase = new PurchaseRecordReport().getDate4Purchase(_parameter);
        final String dateStr = date4Purchase.toString("yyyyMM00");

        return PurchaseRecord_Base.PREFIX + taxnumber + dateStr + PurchaseRecord_Base.IDENTIFIER
                        + PurchaseRecord_Base.OPERTUNITY + PurchaseRecord_Base.OPERATION
                        + PurchaseRecord_Base.HASINFORMATION
                        + PurchaseRecord_Base.CURRENCY + PurchaseRecord_Base.SUFFIX;
    }

    @Override
    public void addColumnDefinition(final Parameter _parameter,
                                    final Exporter _exporter)
    {
        _exporter.addColumns(new FrmtDateTimeColumn(Field.PURCHASE_DATE.getKey(), 8, "yyyyMM00"));  //1
        _exporter.addColumns(new FrmtColumn(Field.DOC_REVISION.getKey()).setMaxWidth(40)); //2
        _exporter.addColumns(new FrmtDateTimeColumn(Field.DOC_DATE.getKey(), 10, "dd/MM/yyyy")); //3
        _exporter.addColumns(new FrmtDateTimeColumn(Field.DOC_DUEDATE.getKey(), 10, "dd/MM/yyyy")); //4
        _exporter.addColumns(new FrmtColumn(Field.DOC_DOCTYPE.getKey()).setMaxWidth(2)); //5
        _exporter.addColumns(new FrmtColumn(Field.DOC_SN.getKey())
                        .setMaxWidth(20).setNullValue("-").setEmptyValue("-")); //6
        _exporter.addColumns(new FrmtColumn(Field.DUA_YEAR.getKey()).setMaxWidth(4)); //7
        _exporter.addColumns(new FrmtColumn(Field.DOC_NUMBER.getKey()).setMaxWidth(20)); //8
        _exporter.addColumns(new FrmtColumn("no").setMaxWidth(20)); //9
        _exporter.addColumns(new FrmtColumn(Field.DOC_CONTACTDOI.getKey())
                        .setMaxWidth(1).setNullValue("0").setEmptyValue("0")); //10
        _exporter.addColumns(new FrmtColumn(Field.DOC_TAXNUM.getKey()).setMaxWidth(15)); //11
        _exporter.addColumns(new FrmtColumn(Field.DOC_CONTACT.getKey()).setMaxWidth(60)); //12
        _exporter.addColumns(new FrmtNumberColumn(Field.DOC_NETTOTALTAXED.getKey(), 14, 2)); //13
        _exporter.addColumns(new FrmtNumberColumn(Field.DOC_IGVTAXED.getKey(), 14, 2)); //14
        _exporter.addColumns(new FrmtNumberColumn("no", 14, 2)); //15
        _exporter.addColumns(new FrmtNumberColumn("no", 14, 2)); //16
        _exporter.addColumns(new FrmtNumberColumn("no", 14, 2)); //17
        _exporter.addColumns(new FrmtNumberColumn("no", 14, 2)); //18
        _exporter.addColumns(new FrmtNumberColumn("no", 14, 2)); //19
        _exporter.addColumns(new FrmtNumberColumn("no", 14, 2)); //20
        _exporter.addColumns(new FrmtNumberColumn("no", 14, 2)); // 21
        _exporter.addColumns(new FrmtNumberColumn(Field.DOC_CROSSTOTAL.getKey(), 14, 2)); // 22
        _exporter.addColumns(new FrmtNumberColumn(Field.DOC_RATE.getKey(), 14, 3)); // 23
        _exporter.addColumns(new FrmtDateTimeColumn(Field.DOCREL_DATE.getKey(), 10, "dd/MM/yyyy")
                        .setNullValue("01/01/0001").setEmptyValue("01/01/0001")); // 24
        _exporter.addColumns(new FrmtColumn(Field.DOCREL_TYPE.getKey())
                        .setMaxWidth(2).setNullValue("00").setEmptyValue("00")); // 25
        _exporter.addColumns(new FrmtColumn(Field.DOCREL_PREFNAME.getKey())
                        .setMaxWidth(20).setNullValue("-").setEmptyValue("-")); // 26
        _exporter.addColumns(new FrmtColumn(Field.DOCREL_SUFNAME.getKey())
                        .setMaxWidth(20).setNullValue("-").setEmptyValue("-")); // 27
        _exporter.addColumns(new FrmtColumn(Field.DOC_FOREIGNNAME.getKey())
                        .setMaxWidth(20).setNullValue("-").setEmptyValue("-")); // 28
        _exporter.addColumns(new FrmtDateTimeColumn(Field.DETRACTION_DATE.getKey(), 10, "dd/MM/yyyy")
                        .setNullValue("01/01/0001").setEmptyValue("01/01/0001")); // 29
        _exporter.addColumns(new FrmtColumn(Field.DETRACTION_NAME.getKey())
                        .setMaxWidth(20).setNullValue("0").setEmptyValue("0")); // 30
        _exporter.addColumns(new FrmtBooleanColumn(Field.RETENCION_APPLIES.getKey(), 1, Format.ONE_ZERO)); // 31
        _exporter.addColumns(new FrmtColumn(Field.DOC_STATE.getKey()).setMaxWidth(1)); // 32
    }

    @Override
    public void buildDataSource(final Parameter _parameter,
                                final Exporter _exporter)
        throws EFapsException
    {
        final PurchaseRecordReport report = new PurchaseRecordReport();

        final DateTime date4Purchase = report.getDate4Purchase(_parameter);
        final Map<String, Object> jrParameters = new HashMap<>();
        report.init(null, _parameter, null, jrParameters);
        final List<Map<String, Object>> values = report.getValues();
        for (final Map<String, Object> value : values) {

            String sn = (String) value.get(Field.DOC_SN.getKey());
            if (sn != null && !sn.isEmpty() && sn.length() ==3) {
                sn = "0" + sn;
            }

            _exporter.addRow(date4Purchase,
                            value.get(Field.DOC_REVISION.getKey()),
                            value.get(Field.DOC_DATE.getKey()),
                            value.get(Field.DOC_DUEDATE.getKey()),  //4
                            value.get(Field.DOC_DOCTYPE.getKey()),  //5
                            sn,       //6
                            value.get(Field.DUA_YEAR.getKey()),     //7
                            value.get(Field.DOC_NUMBER.getKey()),   //8
                            "0",                                    //9
                            value.get(Field.DOC_CONTACTDOI.getKey()),  //10
                            value.get(Field.DOC_TAXNUM.getKey()),   //11
                            value.get(Field.DOC_CONTACT.getKey()),  //12
                            value.get(Field.DOC_NETTOTALTAXED.getKey()), //13
                            value.get(Field.DOC_IGVTAXED.getKey()),      //14
                            BigDecimal.ZERO, //15
                            BigDecimal.ZERO, //16
                            BigDecimal.ZERO, //17
                            BigDecimal.ZERO, //18
                            BigDecimal.ZERO, //19
                            BigDecimal.ZERO, //20
                            BigDecimal.ZERO, //21
                            value.get(Field.DOC_CROSSTOTAL.getKey()), //22
                            value.get(Field.DOC_RATE.getKey()), //23
                            value.get(Field.DOCREL_DATE.getKey()), //24
                            value.get(Field.DOCREL_TYPE.getKey()), //25
                            value.get(Field.DOCREL_PREFNAME.getKey()), //26
                            value.get(Field.DOCREL_SUFNAME.getKey()), //27
                            value.get(Field.DOC_FOREIGNNAME.getKey()), //28
                            value.get(Field.DETRACTION_DATE.getKey()), //29
                            value.get(Field.DETRACTION_NAME.getKey()), //30
                            value.get(Field.RETENCION_APPLIES.getKey()), //31
                            value.get(Field.DOC_STATE.getKey())); //32
        }
    }
}
