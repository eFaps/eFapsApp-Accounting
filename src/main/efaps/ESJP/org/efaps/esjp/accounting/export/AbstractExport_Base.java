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

package org.efaps.esjp.accounting.export;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.output.FileWriterWithEncoding;
import org.efaps.admin.datamodel.Type;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Return;
import org.efaps.admin.event.Return.ReturnValues;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.common.file.FileUtil;
import org.efaps.esjp.erp.util.ERP;
import org.efaps.esjp.erp.util.ERPSettings;
import org.efaps.util.EFapsException;

import com.brsanthu.dataexporter.DataExporter;
import com.brsanthu.dataexporter.LineSeparatorType;
import com.brsanthu.dataexporter.output.csv.CsvExportOptions;
import com.brsanthu.dataexporter.output.csv.CsvExporter;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 * @version $Id: $
 */
public abstract class AbstractExport_Base
{
    /**
     * Windows-1252/WinLatin 1.
     */
    public static final String CHARSET = "UTF-8";

    /**
     * Mapping of types as written in the csv and the name in eFaps.
     */
    public static final Map<Type, String> TYPE2TYPE = new HashMap<Type, String>();
    static {
        AbstractExport_Base.TYPE2TYPE.put(CIAccounting.AccountBalanceSheetAsset.getType(), "Asset");
        AbstractExport_Base.TYPE2TYPE.put(CIAccounting.AccountBalanceSheetLiability.getType(), "Liability");
        AbstractExport_Base.TYPE2TYPE.put(CIAccounting.AccountBalanceSheetEquity.getType(), "Owner's equity");
        AbstractExport_Base.TYPE2TYPE.put(CIAccounting.AccountIncomeStatementExpenses.getType(), "Expense");
        AbstractExport_Base.TYPE2TYPE.put(CIAccounting.AccountIncomeStatementRevenue.getType(), "Revenue");
        AbstractExport_Base.TYPE2TYPE.put(CIAccounting.AccountCurrent.getType(), "Current");
        AbstractExport_Base.TYPE2TYPE.put(CIAccounting.AccountCurrentCreditor.getType(), "Creditor");
        AbstractExport_Base.TYPE2TYPE.put(CIAccounting.AccountCurrentDebtor.getType(), "Debtor");
        AbstractExport_Base.TYPE2TYPE.put(CIAccounting.ViewSum2Account.getType(), "ViewSumAccount");
        AbstractExport_Base.TYPE2TYPE.put(CIAccounting.Account2AccountCosting.getType(), "AccountCosting");
        AbstractExport_Base.TYPE2TYPE.put(CIAccounting.Account2AccountCostingInverse.getType(),
                        "AccountInverseCosting");
        AbstractExport_Base.TYPE2TYPE.put(CIAccounting.Account2AccountCredit.getType(), "AccountAbono");
        AbstractExport_Base.TYPE2TYPE.put(CIAccounting.Account2AccountDebit.getType(), "AccountCargo");
        AbstractExport_Base.TYPE2TYPE.put(CIAccounting.AccountMemo.getType(), "Memo");
        AbstractExport_Base.TYPE2TYPE.put(CIAccounting.CaseBankCashGain.getType(), "CaseBankCashGain");
        AbstractExport_Base.TYPE2TYPE.put(CIAccounting.CaseBankCashPay.getType(), "CaseBankCashPay");
        AbstractExport_Base.TYPE2TYPE.put(CIAccounting.CaseDocBooking.getType(), "CaseDocBooking");
        AbstractExport_Base.TYPE2TYPE.put(CIAccounting.CaseDocRegister.getType(), "CaseDocRegister");
        AbstractExport_Base.TYPE2TYPE.put(CIAccounting.CaseExternalBooking.getType(), "CaseExternalBooking");
        AbstractExport_Base.TYPE2TYPE.put(CIAccounting.CaseExternalRegister.getType(), "CaseExternalRegister");
        AbstractExport_Base.TYPE2TYPE.put(CIAccounting.CaseGeneral.getType(), "CaseGeneral");
        AbstractExport_Base.TYPE2TYPE.put(CIAccounting.CasePayroll.getType(), "CasePayroll");
        AbstractExport_Base.TYPE2TYPE.put(CIAccounting.CasePettyCash.getType(), "CasePettyCash");
        AbstractExport_Base.TYPE2TYPE.put(CIAccounting.CasePettyCashReceiptRegister.getType(), "CasePettyCashRegister");
        AbstractExport_Base.TYPE2TYPE.put(CIAccounting.CaseStockBooking.getType(), "CaseStockBooking");
        AbstractExport_Base.TYPE2TYPE.put(CIAccounting.Account2CaseCredit.getType(), "Credit");
        AbstractExport_Base.TYPE2TYPE.put(CIAccounting.Account2CaseDebit.getType(), "Debit");
        AbstractExport_Base.TYPE2TYPE.put(CIAccounting.Account2CaseCredit4Classification.getType(),
                        "CreditClassification");
        AbstractExport_Base.TYPE2TYPE.put(CIAccounting.Account2CaseDebit4Classification.getType(),
                        "DebitClassification");
    }

    /**
     * Method to export the csv file created.
     *
     * @param _parameter as passed from eFaps API.
     * @return Return with the created file.
     * @throws EFapsException on error.
     */
    public Return execute(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final CsvExportOptions exportOption = new CsvExportOptions();
        configureReport(_parameter, exportOption);
        try {
            final File file = new FileUtil().getFile(getFileName(_parameter, getPrefix(_parameter),
                            getSuffix(_parameter)));
            final FileWriterWithEncoding writer = new FileWriterWithEncoding(file, getCharSet(_parameter));
            final DataExporter exporter = getCsvExporter(_parameter, exportOption, writer);
            addColumnDefinition(_parameter, exporter);
            buildDataSource(_parameter, exporter);
            ret.put(ReturnValues.VALUES, file);
            ret.put(ReturnValues.TRUE, true);
        } catch (final IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return ret;
    }

    /**
     * Method to configure the csv export options.
     *
     * @param _parameter as passed from eFaps API.
     * @param _exportOption CsvExportOptions to configure.
     */
    protected void configureReport(final Parameter _parameter,
                                   final CsvExportOptions _exportOption)
    {
        _exportOption.setLineSeparator(LineSeparatorType.UNIX);
        _exportOption.setPrintHeaders(true);
    }

    /**
     * Get the charset.
     *
     * @param _parameter as passed from eFaps API.
     * @return Charset.
     */
    protected Charset getCharSet(final Parameter _parameter)
    {
        return Charset.forName(AbstractExport_Base.CHARSET);
    }

    /**
     * Build the file name using a prefix and suffix.
     *
     * @param _parameter as passed from eFaps API.
     * @param _prefix Prefix of the file name.
     * @param _suffix Suffix of the file name.
     * @return String with the file name.
     * @throws EFapsException on error.
     */
    protected String getFileName(final Parameter _parameter,
                                 final String _prefix,
                                 final String _suffix)
        throws EFapsException
    {
        final String taxnumber = ERP.getSysConfig().getAttributeValue(ERPSettings.COMPANYTAX);
        return _prefix + taxnumber + "." + _suffix;
    }


    /**
     * Method to get the prefix.
     *
     * @param _parameter as passed from eFaps API.
     * @return String with the prefix.
     * @throws EFapsException on error
     */
    protected abstract String getPrefix(final Parameter _parameter)
        throws EFapsException;

    /**
     * Method to get the suffix.
     *
     * @param _parameter as passed from eFaps API.
     * @return String with the suffix.
     * @throws EFapsException on error.
     */
    protected abstract String getSuffix(final Parameter _parameter)
        throws EFapsException;

    /**
     * Method to instance the DataExporter to use.
     *
     * @param _parameter as passed from eFaps API.
     * @param _options Options for CSV export.
     * @param _output Writer with file.
     * @return new CsvExporter.
     */
    protected DataExporter getCsvExporter(final Parameter _parameter,
                                          final CsvExportOptions _options,
                                          final Writer _output)
    {
        return new CsvExporter(_options, _output);
    }

    /**
     * Method to build the header.
     *
     * @param _parameter as passed from eFaps API.
     * @param _exporter DataExporter to export.
     * @throws EFapsException on error.
     */
    public abstract void addColumnDefinition(final Parameter _parameter,
                                             final DataExporter _exporter)
        throws EFapsException;

    /**
     * Method to get the data to export.
     *
     * @param _parameter as passed from eFaps API.
     * @param _exporter DataExporter to export.
     * @throws EFapsException on error.
     */
    public abstract void buildDataSource(final Parameter _parameter,
                                         final DataExporter _exporter)
        throws EFapsException;

}
