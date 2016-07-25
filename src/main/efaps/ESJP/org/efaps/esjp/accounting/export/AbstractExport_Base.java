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

package org.efaps.esjp.accounting.export;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.io.output.FileWriterWithEncoding;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Return;
import org.efaps.admin.event.Return.ReturnValues;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.dataexporter.DataExporter;
import org.efaps.dataexporter.LineSeparatorType;
import org.efaps.dataexporter.output.csv.CsvExportOptions;
import org.efaps.dataexporter.output.csv.CsvExporter;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.common.file.FileUtil;
import org.efaps.esjp.erp.util.ERP;
import org.efaps.util.EFapsException;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 */
@EFapsUUID("e9b019b5-3e7d-4c9c-9c4b-24ae3a50bbf9")
@EFapsApplication("eFapsApp-Accounting")
public abstract class AbstractExport_Base
{
    /**
     * Windows-1252/WinLatin 1.
     */
    public static final String CHARSET = "UTF-8";

    /**
     * Mapping of types as written in the csv and the name in eFaps.
     */
    public static final Map<UUID, String> TYPE2TYPE = new HashMap<UUID, String>();
    static {
        AbstractExport_Base.TYPE2TYPE.put(CIAccounting.AccountBalanceSheetAsset.uuid, "Asset");
        AbstractExport_Base.TYPE2TYPE.put(CIAccounting.AccountBalanceSheetLiability.uuid, "Liability");
        AbstractExport_Base.TYPE2TYPE.put(CIAccounting.AccountBalanceSheetEquity.uuid, "Owner's equity");
        AbstractExport_Base.TYPE2TYPE.put(CIAccounting.AccountIncomeStatementExpenses.uuid, "Expense");
        AbstractExport_Base.TYPE2TYPE.put(CIAccounting.AccountIncomeStatementRevenue.uuid, "Revenue");
        AbstractExport_Base.TYPE2TYPE.put(CIAccounting.AccountCurrent.uuid, "Current");
        AbstractExport_Base.TYPE2TYPE.put(CIAccounting.AccountCurrentCreditor.uuid, "Creditor");
        AbstractExport_Base.TYPE2TYPE.put(CIAccounting.AccountCurrentDebtor.uuid, "Debtor");
        AbstractExport_Base.TYPE2TYPE.put(CIAccounting.ViewSum2Account.uuid, "ViewSumAccount");
        AbstractExport_Base.TYPE2TYPE.put(CIAccounting.Account2AccountCosting.uuid, "AccountCosting");
        AbstractExport_Base.TYPE2TYPE.put(CIAccounting.Account2AccountCostingInverse.uuid, "AccountInverseCosting");
        AbstractExport_Base.TYPE2TYPE.put(CIAccounting.Account2AccountCredit.uuid, "AccountAbono");
        AbstractExport_Base.TYPE2TYPE.put(CIAccounting.Account2AccountDebit.uuid, "AccountCargo");
        AbstractExport_Base.TYPE2TYPE.put(CIAccounting.AccountMemo.uuid, "Memo");

        AbstractExport_Base.TYPE2TYPE.put(CIAccounting.CaseDocBooking.uuid, "CaseDocBooking");
        AbstractExport_Base.TYPE2TYPE.put(CIAccounting.CaseDocRegister.uuid, "CaseDocRegister");
        AbstractExport_Base.TYPE2TYPE.put(CIAccounting.CaseExchange.uuid, "CaseExchange");
        AbstractExport_Base.TYPE2TYPE.put(CIAccounting.CaseExternalBooking.uuid, "CaseExternalBooking");
        AbstractExport_Base.TYPE2TYPE.put(CIAccounting.CaseExternalRegister.uuid, "CaseExternalRegister");
        AbstractExport_Base.TYPE2TYPE.put(CIAccounting.CaseFundsToBeSettled.uuid, "CaseFundsToBeSettled");
        AbstractExport_Base.TYPE2TYPE.put(CIAccounting.CaseGeneral.uuid, "CaseGeneral");
        AbstractExport_Base.TYPE2TYPE.put(CIAccounting.CaseIncomingExchange.uuid, "CaseIncomingExchange");
        AbstractExport_Base.TYPE2TYPE.put(CIAccounting.CaseOthersCollect.uuid, "CaseOthersCollect");
        AbstractExport_Base.TYPE2TYPE.put(CIAccounting.CaseOthersPay.uuid, "CaseOthersPay");
        AbstractExport_Base.TYPE2TYPE.put(CIAccounting.CasePayroll.uuid, "CasePayroll");
        AbstractExport_Base.TYPE2TYPE.put(CIAccounting.CasePettyCashReceiptRegister.uuid, "CasePettyCashRegister");
        AbstractExport_Base.TYPE2TYPE.put(CIAccounting.CaseRetPer.uuid, "CaseRetentionPerception");
        AbstractExport_Base.TYPE2TYPE.put(CIAccounting.CaseSecurities.uuid, "CaseSecurities");
        AbstractExport_Base.TYPE2TYPE.put(CIAccounting.CaseStockBooking.uuid, "CaseStockBooking");
        AbstractExport_Base.TYPE2TYPE.put(CIAccounting.CaseSwap.uuid, "CaseSwap");

        AbstractExport_Base.TYPE2TYPE.put(CIAccounting.Account2CaseCredit.uuid, "Credit");
        AbstractExport_Base.TYPE2TYPE.put(CIAccounting.Account2CaseDebit.uuid, "Debit");
        AbstractExport_Base.TYPE2TYPE.put(CIAccounting.Account2CaseCredit4Classification.uuid, "CreditClassification");
        AbstractExport_Base.TYPE2TYPE.put(CIAccounting.Account2CaseDebit4Classification.uuid, "DebitClassification");
        AbstractExport_Base.TYPE2TYPE.put(CIAccounting.ReportAccount.uuid, "ReportAccount");
        AbstractExport_Base.TYPE2TYPE.put(CIAccounting.ReportBalance.uuid, "Balance");
        AbstractExport_Base.TYPE2TYPE.put(CIAccounting.ReportProfitLoss.uuid, "ProfitLoss");
        AbstractExport_Base.TYPE2TYPE.put(CIAccounting.ReportNodeRoot.uuid, "Root");
        AbstractExport_Base.TYPE2TYPE.put(CIAccounting.ReportNodeTree.uuid, "Tree");
        AbstractExport_Base.TYPE2TYPE.put(CIAccounting.ReportNodeAccount.uuid, "Account");


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
        final String taxnumber = ERP.COMPANYTAX.get();
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
