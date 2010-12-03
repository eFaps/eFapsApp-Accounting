/*
 * Copyright 2003 - 2010 The eFaps Team
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

package org.efaps.esjp.accounting;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import org.efaps.admin.datamodel.Status;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Parameter.ParameterValues;
import org.efaps.admin.event.Return;
import org.efaps.admin.event.Return.ReturnValues;
import org.efaps.admin.program.esjp.EFapsRevision;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.ci.CIType;
import org.efaps.db.AttributeQuery;
import org.efaps.db.Context;
import org.efaps.db.Context.FileParameter;
import org.efaps.db.Insert;
import org.efaps.db.Instance;
import org.efaps.db.InstanceQuery;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.PrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.db.Update;
import org.efaps.esjp.admin.common.SystemConf;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.ci.CISales;
import org.efaps.esjp.erp.CurrencyInst;
import org.efaps.util.EFapsException;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;

import au.com.bytecode.opencsv.CSVReader;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 * @version $Id$
 */
@EFapsUUID("42e5a308-5d9a-4a8d-a469-b9929010858a")
@EFapsRevision("$Rev$")
public abstract class Periode_Base
{

    /**
     * Key used to store the currency in the session.
     */
    public static final String PERIODECURRENCYKEY = "eFaps_Accounting_PeriodeCurrency";

    /**
     * Definitions for Columns.
     */
    public interface Column
    {
        /**
         * @return key
         */
        String getKey();
    }

    /**
     * Columns for an account table.
     *
     */
    private enum AcccountColumn implements Periode_Base.Column {
        /** */
        VALUE("[Account_Value]"),
                /** */
        NAME("[Account_Name]"),
                /** */
        TYPE("[Account_Type]"),
                /** */
        SUMMARY("[Account_Summary]"),
                /** */
        PARENT("[Account_Parent]");

        /** Key. */
        private final String key;

        /**
         * @param _key key
         */
        private AcccountColumn(final String _key)
        {
            this.key = _key;
        }

        /**
         * Getter method for instance variable {@link #key}.
         *
         * @return value of instance variable {@link #key}
         */
        public String getKey()
        {
            return this.key;
        }
    }

    /**
     * Columns for an report.
     */
    private enum ReportColumn implements Periode_Base.Column {
        /** */
        TYPE("[Report_Type]"),
        /** */
        NAME("[Report_Name]"),
        /** */
        DESC("[Report_Description]"),
        /** */
        NUMBERING("[Report_Numbering]"),
        /** */
        NODE_TYPE("[Node_Type]"),
        /** */
        NODE_SHOW("[Node_ShowAllways]"),
        /** */
        NODE_SUM("[Node_ShowSum]"),
        /** */
        NODE_NUMBER("[Node_Number]");

        /** Key. */
        private final String key;

        /**
         * @param _key key
         */
        private ReportColumn(final String _key)
        {
            this.key = _key;
        }

        /**
         * Getter method for instance variable {@link #key}.
         *
         * @return value of instance variable {@link #key}
         */
        public String getKey()
        {
            return this.key;
        }
    }

    /**
     * Mapping of types as written in the csv and the name in eFaps.
     */
    protected static final Map<String, CIType> TYPE2TYPE = new HashMap<String, CIType>();
    static {
        Periode_Base.TYPE2TYPE.put("Asset", CIAccounting.AccountBalanceSheetAsset);
        Periode_Base.TYPE2TYPE.put("Liability", CIAccounting.AccountBalanceSheetLiability);
        Periode_Base.TYPE2TYPE.put("Owner's equity", CIAccounting.AccountBalanceSheetEquity);
        Periode_Base.TYPE2TYPE.put("Expense", CIAccounting.AccountIncomeStatementExpenses);
        Periode_Base.TYPE2TYPE.put("Revenue", CIAccounting.AccountIncomeStatementRevenue);
        Periode_Base.TYPE2TYPE.put("Current", CIAccounting.AccountCurrent);
        Periode_Base.TYPE2TYPE.put("Creditor", CIAccounting.AccountCurrentCreditor);
        Periode_Base.TYPE2TYPE.put("Debtor", CIAccounting.AccountCurrentDebtor);
        Periode_Base.TYPE2TYPE.put("Memo", CIAccounting.AccountMemo);
        Periode_Base.TYPE2TYPE.put("Balance", CIAccounting.ReportBalance);
        Periode_Base.TYPE2TYPE.put("ProfitLoss", CIAccounting.ReportProfitLoss);
        Periode_Base.TYPE2TYPE.put("Other", CIAccounting.ReportTransaction);
        Periode_Base.TYPE2TYPE.put("Root", CIAccounting.ReportNodeRoot);
        Periode_Base.TYPE2TYPE.put("Tree", CIAccounting.ReportNodeTree);
        Periode_Base.TYPE2TYPE.put("Account", CIAccounting.ReportNodeAccount);
        Periode_Base.TYPE2TYPE.put("ReportAccount", CIAccounting.ReportAccount);
    }

    /**
     * Key for a level column.
     */
    private static String LEVELCOLUMN = "[Level #]";

    /**
     * @param _instance instance of a period or an account
     * @return Instance of the periode the currency is wanted for
     * @throws EFapsException on error
     */
    @SuppressWarnings("unchecked")
    public CurrencyInst getCurrency(final Instance _instance)
        throws EFapsException
    {
        Map<Instance, CurrencyInst> inst2curr = (Map<Instance, CurrencyInst>) Context.getThreadContext()
                        .getSessionAttribute(Periode_Base.PERIODECURRENCYKEY);
        if (inst2curr == null || (inst2curr != null && inst2curr.get(_instance) != null)) {
            inst2curr = new HashMap<Instance, CurrencyInst>();
            Context.getThreadContext().setSessionAttribute(Periode_Base.PERIODECURRENCYKEY, inst2curr);
            final PrintQuery print = new PrintQuery(_instance);
            if (_instance.getType().isKindOf(CIAccounting.Account2AccountAbstract.getType())
                            || _instance.getType().isKindOf(CIAccounting.Transaction.getType())) {
                print.addSelect("linkto[PeriodeLink].linkto[CurrencyLink].oid");
                print.execute();
                inst2curr.put(_instance, new CurrencyInst(Instance.get(print
                                .<String> getSelect("linkto[PeriodeLink].linkto[CurrencyLink].oid"))));
            } else if (_instance.getType().isKindOf(CIAccounting.Account2AccountAbstract.getType())
                            || _instance.getType().isKindOf(CIAccounting.Transaction.getType())) {
                print.addSelect("linkto[PeriodeAbstractLink].linkto[CurrencyLink].oid");
                print.execute();
                inst2curr.put(_instance,
                                new CurrencyInst(Instance.get(print.<String> getSelect("linkto[PeriodeAbstractLink]"
                                                + ".linkto[CurrencyLink].oid"))));
            } else {
                print.addSelect("linkto[CurrencyLink].oid");
                print.execute();
                inst2curr.put(_instance,
                                new CurrencyInst(Instance.get(print.<String> getSelect("linkto[CurrencyLink].oid"))));
            }
        }
        return inst2curr.get(_instance);
    }

    /**
     * Called on a command to create a new period including account table and
     * reports.
     *
     * @param _parameter Paremeter
     * @return new Return
     * @throws EFapsException on error
     */
    public Return create(final Parameter _parameter)
        throws EFapsException
    {
        final Insert insert = new Insert(CIAccounting.Periode);
        insert.add("Name", _parameter.getParameterValue("name"));
        insert.add("FromDate", _parameter.getParameterValue("fromDate"));
        insert.add("ToDate", _parameter.getParameterValue("toDate"));
        insert.add("CurrencyLink", _parameter.getParameterValue("currencyLink"));
        insert.execute();
        final Instance periodInst = insert.getInstance();
        final FileParameter accountTable = Context.getThreadContext().getFileParameters().get("accountTable");
        final FileParameter reports = Context.getThreadContext().getFileParameters().get("reports");
        if (accountTable != null) {
            final HashMap<String, ImportAccount> accounts = createAccountTable(periodInst, accountTable);
            if (reports != null) {
                createReports(periodInst, reports, accounts);
            }
        }
        final SystemConf conf = new SystemConf();
        //Accounting-Configuration
        conf.addObjectAttribute(UUID.fromString("ca0a1df1-2211-45d9-97c8-07af6636a9b9"), periodInst,
                        "Name=" + _parameter.getParameterValue("name"));
        return new Return();
    }

    /**
     * Called on a command to create a new reports of the file.
     *
     * @param _parameter Paremeter as passed from the eFaps API.
     * @return new Return
     * @throws EFapsException on error
     */
    public Return createReportOfFile(final Parameter _parameter)
        throws EFapsException
    {
        final String periode = _parameter.getParameterValue("periodeLink");
        final FileParameter reports = Context.getThreadContext().getFileParameters().get("reports");
        if (periode != null && reports != null) {
            final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.Periode);
            queryBldr.addWhereAttrEqValue(CIAccounting.Periode.ID, periode);
            final MultiPrintQuery multi = queryBldr.getPrint();
            multi.addAttribute(CIAccounting.Periode.OID);
            multi.execute();
            Instance instance = null;
            while (multi.next()) {
                instance = Instance.get(multi.<String>getAttribute(CIAccounting.Periode.OID));
            }
            if (instance != null) {
                createReports(instance, reports, getImportAccountFromDB(_parameter, instance));
            }
        }

        return new Return();
    }

    /**
     * Obtains account from the DB.
     *
     * @param _parameter Parameter as passed from the eFaps API.
     * @param _instance Instance of the period.
     * @return ret with accounts.
     * @throws EFapsException on error.
     */
    protected Map<String, ImportAccount> getImportAccountFromDB(final Parameter _parameter,
                                                                final Instance _instance)
        throws EFapsException
    {
        final Map<String, ImportAccount> ret = new HashMap<String, ImportAccount>();
        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.AccountAbstract);
        queryBldr.addWhereAttrEqValue(CIAccounting.AccountAbstract.PeriodeAbstractLink, _instance.getId());
        final MultiPrintQuery multi = queryBldr.getPrint();
        final SelectBuilder sel = new SelectBuilder().linkto(CIAccounting.AccountAbstract.ParentLink)
                                        .attributeset(CIAccounting.AccountAbstract.Name);
        multi.addSelect(sel);
        multi.addAttribute(CIAccounting.AccountAbstract.Name);
        multi.execute();
        while (multi.next()) {
            final String parentName = multi.<String>getSelect(sel);
            final String name = multi.<String>getAttribute(CIAccounting.AccountAbstract.Name);
            ret.put(name, new ImportAccount(multi.getCurrentInstance(), parentName, name));
        }

        return ret;
    }

    /**
     * @param _periodInst periode the account table belongs to
     * @param _reports csv file
     * @param _accounts map of accounts
     * @throws EFapsException on error
     */
    protected void createReports(final Instance _periodInst,
                                 final FileParameter _reports,
                                 final Map<String, ImportAccount> _accounts)
        throws EFapsException
    {
        try {
            final CSVReader reader = new CSVReader(new InputStreamReader(_reports.getInputStream(), "UTF-8"));
            final List<String[]> entries = reader.readAll();
            if (!entries.isEmpty()) {
                final Map<String, Integer> colName2Index = evaluateCSVFileHeader(Periode_Base.ReportColumn.values(),
                                entries.get(0));
                reader.close();
                Integer i = 0;
                final String[] headRow = entries.get(0);
                boolean found = true;
                while (found) {
                    found = false;
                    final String level = Periode_Base.LEVELCOLUMN.replace("#", i.toString());
                    int idx = 0;
                    for (final String column : headRow) {
                        if (level.equals(column.trim())) {
                            i++;
                            found = true;
                            colName2Index.put(level, idx);
                            break;
                        }
                        idx++;
                    }
                }

                entries.remove(0);
                final Map<String, ImportReport> reports = new HashMap<String, ImportReport>();
                ImportReport report = null;
                for (final String[] row : entries) {
                    report = getImportReport(_periodInst, colName2Index, row, reports);
                    report.addNode(colName2Index, row, i, _accounts);
                }
            }
        } catch (final IOException e) {
            throw new EFapsException(Periode_Base.class, "createReports.IOException", e);
        }
    }


    /**
     * method for obtains reports of a import.
     *
     * @param _periodInst Instance of the period.
     * @param _colName2Index cols of the report.
     * @param _row rows of the report.
     * @param _reports values of the report.
     * @return ret with values.
     * @throws EFapsException on error.
     */
    protected ImportReport getImportReport(final Instance _periodInst,
                                           final Map<String, Integer> _colName2Index,
                                           final String[] _row,
                                           final Map<String, ImportReport> _reports)
        throws EFapsException
    {
        final ImportReport ret;
        final String type = _row[_colName2Index.get(Periode_Base.ReportColumn.TYPE.getKey())].trim()
            .replaceAll("\n", "");
        final String name = _row[_colName2Index.get(Periode_Base.ReportColumn.NAME.getKey())].trim()
            .replaceAll("\n", "");
        final String description = _row[_colName2Index.get(Periode_Base.ReportColumn.DESC.getKey())].trim()
                        .replaceAll("\n", "");
        final String numbering = _row[_colName2Index.get(Periode_Base.ReportColumn.NUMBERING.getKey())].trim()
                        .replaceAll("\n", "");

        final String key = type + ":" + name;
        if (_reports.containsKey(key)) {
            ret = _reports.get(key);
        } else {
            ret = new ImportReport(_periodInst, type, name, description, numbering);
            _reports.put(key, ret);
        }
        return ret;
    }

    /**
     * @param _periodInst periode the account table belongs to
     * @param _accountTable csv file
     * @return HashMap
     * @throws EFapsException on error
     */
    protected HashMap<String, ImportAccount> createAccountTable(final Instance _periodInst,
                                                                final FileParameter _accountTable)
        throws EFapsException
    {
        final HashMap<String, ImportAccount> accounts = new HashMap<String, ImportAccount>();
        try {
            final CSVReader reader = new CSVReader(new InputStreamReader(_accountTable.getInputStream(), "UTF-8"));
            final List<String[]> entries = reader.readAll();
            reader.close();
            final Map<String, Integer> colName2Index = evaluateCSVFileHeader(Periode_Base.AcccountColumn.values(),
                            entries.get(0));
            entries.remove(0);

            for (final String[] row : entries) {
                final ImportAccount account = new ImportAccount(_periodInst, colName2Index, row);
                accounts.put(account.getValue(), account);
            }
            for (final ImportAccount account : accounts.values()) {
                if (account.getParent() != null && account.getParent().length() > 0) {
                    final ImportAccount parent = accounts.get(account.getParent());
                    if (parent != null) {
                        final Update update = new Update(account.getInstance());
                        update.add("ParentLink", parent.getInstance().getId());
                        update.execute();
                    }
                }
            }
        } catch (final IOException e) {
            throw new EFapsException(Periode.class, "createAccountTable.IOException", e);
        }
        return accounts;
    }

    /**
     * Evaluates the header of a CSV file to get for each defined column the
     * related column number. If no keys are given all columns will be read;
     *
     * @param _columns columns to read (defined in the header of the file),
     *            <code>null</code> if all must be read
     * @param _headerLine string array with the header line
     * @return map defining for each key the related column number
     * @throws EFapsException if a column from the list of keys is not defined
     */
    protected Map<String, Integer> evaluateCSVFileHeader(final Periode_Base.Column[] _columns,
                                                         final String[] _headerLine)
        throws EFapsException
    {
        // evaluate header
        int idx = 0;
        final Map<String, Integer> ret = new HashMap<String, Integer>();
        for (final String column : _headerLine) {
            if (_columns == null) {
                ret.put(column, idx);
            } else {
                for (final Column columns : _columns) {
                    if (columns.getKey().equals(column)) {
                        ret.put(column, idx);
                        break;
                    }
                }
            }
            idx++;
        }

        // if keys are defined, check for all required indexes
        if (_columns != null) {
            for (final Column column : _columns) {
                if (ret.get(column.getKey()) == null) {
                    throw new EFapsException(Periode_Base.class, "ColumnNotDefinded", column.getKey());
                }
            }
        }
        return ret;
    }

    /**
     * Method is executed on an autocomplete event to present a dropdown with
     * accounts.
     *
     * @param _parameter Parameter as passed from the eFAPS API
     * @return list of map used for an autocomplete event
     * @throws EFapsException on erro
     */
    public Return autoComplete4Periode(final Parameter _parameter)
        throws EFapsException
    {
        final String input = (String) _parameter.get(ParameterValues.OTHERS);
        final Map<String, Map<String, String>> orderMap = new TreeMap<String, Map<String, String>>();

        final QueryBuilder queryBuilder = new QueryBuilder(CIAccounting.Periode);
        queryBuilder.addWhereAttrMatchValue("Name", input + "*").setIgnoreCase(true);
        final MultiPrintQuery print = queryBuilder.getPrint();
        print.addAttribute("ID", "Name", "FromDate", "ToDate");
        print.execute();
        while (print.next()) {
            final String name = print.<String> getAttribute("Name");
            final DateTime fromDate = print.<DateTime> getAttribute("FromDate");
            final DateTime toDate = print.<DateTime> getAttribute("ToDate");
            final String toDateStr = toDate.toString(DateTimeFormat.forStyle("S-")
                            .withLocale(Context.getThreadContext().getLocale()));
            final String fromDateStr = fromDate.toString(DateTimeFormat.forStyle("S-")
                            .withLocale(Context.getThreadContext().getLocale()));
            final Long id = print.<Long> getAttribute("ID");
            final String choice = name + ": " + fromDateStr + " - " + toDateStr;
            final Map<String, String> map = new HashMap<String, String>();
            map.put("eFapsAutoCompleteKEY", id.toString());
            map.put("eFapsAutoCompleteVALUE", name);
            map.put("eFapsAutoCompleteCHOICE", choice);
            orderMap.put(choice, map);
        }

        final List<Map<String, String>> list = new ArrayList<Map<String, String>>();
        list.addAll(orderMap.values());
        final Return retVal = new Return();
        retVal.put(ReturnValues.VALUES, list);
        return retVal;
    }

    /**
     * Called from a tree menu command to present the documents that are not
     * included in accounting yet.
     *
     * @param _parameter Paremeter
     * @return List if Instances
     * @throws EFapsException on error
     */
    public Return getDocuments(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final Instance instance = _parameter.getInstance();
        final PrintQuery print = new PrintQuery(instance);
        print.addAttribute(CIAccounting.Periode.FromDate);
        print.addAttribute(CIAccounting.Periode.ToDate);
        print.execute();
        final DateTime from = print.<DateTime> getAttribute(CIAccounting.Periode.FromDate);
        final DateTime to = print.<DateTime> getAttribute(CIAccounting.Periode.ToDate);

        final QueryBuilder attrQueryBldr = new QueryBuilder(CIAccounting.TransactionClassDocument);
        final AttributeQuery attrQuery
            = attrQueryBldr.getAttributeQuery(CIAccounting.TransactionClassDocument.DocumentLink);

        final QueryBuilder queryBldr = new QueryBuilder(CISales.DocumentSumAbstract);
        queryBldr.addWhereAttrEqValue(CISales.DocumentSumAbstract.StatusAbstract,
                                      Status.find(CISales.InvoiceStatus.uuid, "Open").getId(),
                                      Status.find(CISales.InvoiceStatus.uuid, "Paid").getId(),
                                      Status.find(CISales.ReceiptStatus.uuid, "Open").getId(),
                                      Status.find(CISales.ReceiptStatus.uuid, "Paid").getId(),
                                      Status.find(CISales.CreditNoteStatus.uuid, "Open").getId(),
                                      Status.find(CISales.CreditNoteStatus.uuid, "Paid").getId(),
                                      Status.find(CISales.ReminderStatus.uuid, "Open").getId(),
                                      Status.find(CISales.ReminderStatus.uuid, "Paid").getId());
        queryBldr.addWhereAttrGreaterValue(CISales.DocumentSumAbstract.Date, from.minusMinutes(1));
        queryBldr.addWhereAttrLessValue(CISales.DocumentSumAbstract.Date, to.plusDays(1));
        queryBldr.addWhereAttrNotInQuery(CISales.DocumentSumAbstract.ID, attrQuery);

        final InstanceQuery query = queryBldr.getQuery();

        final List<Instance> instances = query.execute();

        ret.put(ReturnValues.VALUES, instances);
        return ret;
    }

    /**
     * Called from a tree menu command to present the documents that are with status
     * booked and therefor must be worked on still.
     *
     * @param _parameter Paremeter
     * @return List if Instances
     * @throws EFapsException on error
     */
    public Return getDocumentsToBook(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        ret.put(ReturnValues.VALUES, getDocumentsToBookList(_parameter));
        return ret;
    }

    /**
     * Called from a tree menu command to present the documents that are with
     * status booked and therefor must be worked on still.
     *
     * @param _parameter Parameter as passed from the eFaps API.
     * @return list with values.
     * @throws EFapsException on error
     */
    protected List<Instance> getDocumentsToBookList(final Parameter _parameter)
        throws EFapsException
    {
        final Instance instance = _parameter.getInstance();

        final QueryBuilder transQueryBldr = new QueryBuilder(CIAccounting.Transaction);
        transQueryBldr.addWhereAttrEqValue(CIAccounting.Transaction.PeriodeLink, instance.getId());
        final AttributeQuery transAttrQuery = transQueryBldr.getAttributeQuery(CIAccounting.Transaction.ID);

        final QueryBuilder attrQueryBldr = new QueryBuilder(CIAccounting.TransactionClassDocument);
        attrQueryBldr.addWhereAttrInQuery(CIAccounting.TransactionClassDocument.TransactionLink, transAttrQuery);
        final AttributeQuery attrQuery =
                            attrQueryBldr.getAttributeQuery(CIAccounting.TransactionClassDocument.DocumentLink);

        final QueryBuilder queryBldr = new QueryBuilder(CISales.DocumentSumAbstract);
        queryBldr.addWhereAttrEqValue(CISales.DocumentSumAbstract.StatusAbstract,
                                      Status.find(CISales.InvoiceStatus.uuid, "Open").getId(),
                                      Status.find(CISales.InvoiceStatus.uuid, "Paid").getId(),
                                      Status.find(CISales.ReceiptStatus.uuid, "Open").getId(),
                                      Status.find(CISales.ReceiptStatus.uuid, "Paid").getId(),
                                      Status.find(CISales.CreditNoteStatus.uuid, "Open").getId(),
                                      Status.find(CISales.CreditNoteStatus.uuid, "Paid").getId(),
                                      Status.find(CISales.ReminderStatus.uuid, "Open").getId(),
                                      Status.find(CISales.ReminderStatus.uuid, "Paid").getId());
        queryBldr.addWhereAttrInQuery(CISales.DocumentSumAbstract.ID, attrQuery);

        final InstanceQuery query = queryBldr.getQuery();
        final List<Instance> instances = query.execute();

        return instances;
    }

    /**
     * Called from a tree menu command to present the documents related with
     * stock movement that are not connected with the period and therefor
     * must be worked on still.
     *
     * @param _parameter Paremeter
     * @return List if Instances
     * @throws EFapsException on error
     */
    public Return getStockToBook(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final Instance instance = _parameter.getInstance();
        final PrintQuery print = new PrintQuery(instance);
        print.addAttribute(CIAccounting.Periode.FromDate);
        print.addAttribute(CIAccounting.Periode.ToDate);
        print.execute();
        final DateTime from = print.<DateTime>getAttribute(CIAccounting.Periode.FromDate);
        final DateTime to = print.<DateTime>getAttribute(CIAccounting.Periode.ToDate);

        final QueryBuilder attrQueryBldr = new QueryBuilder(CIAccounting.TransactionClassDocument);
        final AttributeQuery attrQuery = attrQueryBldr
                                .getAttributeQuery(CIAccounting.TransactionClassDocument.DocumentLink);

        final QueryBuilder queryBldr = new QueryBuilder(CISales.DocumentStockAbstract);
        queryBldr.addWhereAttrEqValue(CISales.DocumentStockAbstract.StatusAbstract,
                                      Status.find(CISales.DeliveryNoteStatus.uuid, "Closed").getId(),
                                      Status.find(CISales.ReturnSlipStatus.uuid, "Closed").getId());
        queryBldr.addWhereAttrGreaterValue(CISales.DocumentStockAbstract.Date, from.minusMinutes(1));
        queryBldr.addWhereAttrLessValue(CISales.DocumentStockAbstract.Date, to.plusDays(1));
        queryBldr.addWhereAttrNotInQuery(CISales.DocumentStockAbstract.ID, attrQuery);

        final InstanceQuery query = queryBldr.getQuery();
        final List<Instance> instances = query.execute();
        ret.put(ReturnValues.VALUES, instances);
        return ret;
    }

    /**
     * Called from a tree menu command to present the documents that are with status
     * booked and therefor must be worked on still.
     *
     * @param _parameter Paremeter
     * @return List if Instances
     * @throws EFapsException on error
     */
    public Return getExternals(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final Instance instance = _parameter.getInstance();
        final PrintQuery print = new PrintQuery(instance);
        print.addAttribute(CIAccounting.Periode.FromDate);
        print.addAttribute(CIAccounting.Periode.ToDate);
        print.execute();
        final DateTime from = print.<DateTime> getAttribute(CIAccounting.Periode.FromDate);
        final DateTime to = print.<DateTime> getAttribute(CIAccounting.Periode.ToDate);

        final QueryBuilder attrQueryBldr = new QueryBuilder(CIAccounting.TransactionClassExternal);
        final AttributeQuery attrQuery
            = attrQueryBldr.getAttributeQuery(CIAccounting.TransactionClassExternal.DocumentLink);

        final QueryBuilder queryBldr = new QueryBuilder(CISales.DocumentSumAbstract);
        queryBldr.addWhereAttrEqValue(CISales.DocumentSumAbstract.StatusAbstract,
                                      Status.find(CISales.IncomingInvoiceStatus.uuid, "Open").getId(),
                                      Status.find(CISales.IncomingInvoiceStatus.uuid, "Paid").getId(),
                                      Status.find(CIAccounting.ExternalVoucherStatus.uuid, "Open").getId(),
                                      Status.find(CIAccounting.ExternalVoucherStatus.uuid, "Paid").getId());
        queryBldr.addWhereAttrGreaterValue(CISales.DocumentSumAbstract.Date, from.minusMinutes(1));
        queryBldr.addWhereAttrLessValue(CISales.DocumentSumAbstract.Date, to.plusDays(1));
        queryBldr.addWhereAttrNotInQuery(CISales.DocumentSumAbstract.ID, attrQuery);

        final InstanceQuery query = queryBldr.getQuery();
        final List<Instance> instances = query.execute();
        ret.put(ReturnValues.VALUES, instances);
        return ret;
    }

    /**
     * Called from a tree menu command to present the documents that are with status
     * booked and therefor must be worked on still.
     *
     * @param _parameter Paremeter
     * @return List if Instances
     * @throws EFapsException on error
     */
    public Return getExternalsToBook(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        ret.put(ReturnValues.VALUES, getExternalsToBookList(_parameter));
        return ret;
    }

    /**
     * Called from a tree menu command to present the documents that are with status
     * booked and therefor must be worked on still.
     *
     * @param _parameter Parameter as passed from the eFaps API.
     * @return list instance with values.
     * @throws EFapsException on error.
     */
    protected List<Instance> getExternalsToBookList(final Parameter _parameter)
        throws EFapsException
    {
        final Instance instance = _parameter.getInstance();

        final QueryBuilder transQueryBldr = new QueryBuilder(CIAccounting.Transaction);
        transQueryBldr.addWhereAttrEqValue(CIAccounting.Transaction.PeriodeLink, instance.getId());
        final AttributeQuery transAttrQuery = transQueryBldr.getAttributeQuery(CIAccounting.Transaction.ID);

        final QueryBuilder attrQueryBldr = new QueryBuilder(CIAccounting.TransactionClassExternal);
        attrQueryBldr.addWhereAttrInQuery(CIAccounting.TransactionClassExternal.TransactionLink, transAttrQuery);
        final AttributeQuery attrQuery = attrQueryBldr.getAttributeQuery(CIAccounting.TransactionClassExternal.DocumentLink);

        final QueryBuilder queryBldr = new QueryBuilder(CISales.DocumentSumAbstract);
        queryBldr.addWhereAttrEqValue(CISales.DocumentSumAbstract.StatusAbstract,
                                        Status.find(CISales.IncomingInvoiceStatus.uuid, "Open").getId(),
                                        Status.find(CISales.IncomingInvoiceStatus.uuid, "Paid").getId(),
                                        Status.find(CIAccounting.ExternalVoucherStatus.uuid, "Open").getId(),
                                        Status.find(CIAccounting.ExternalVoucherStatus.uuid, "Paid").getId());
        queryBldr.addWhereAttrInQuery(CISales.DocumentSumAbstract.ID, attrQuery);

        final InstanceQuery query = queryBldr.getQuery();
        final List<Instance> instances = query.execute();
        return instances;
    }

    /**
     * represents one account to be imported.
     */
    public class ImportAccount
    {

        /**
         * Value for this account.
         */
        private final String value;

        /**
         * Parent of this account.
         */
        private final String parent;

        /**
         * Instance of this account.
         */
        private final Instance instance;

        /**
         * @param _periode periode this account belong to
         * @param _colName2Index mapping o ccolumn name to index
         * @param _row actual row
         * @throws EFapsException on error
         */
        public ImportAccount(final Instance _periode,
                             final Map<String, Integer> _colName2Index,
                             final String[] _row)
            throws EFapsException
        {
            this.value = _row[_colName2Index.get(Periode_Base.AcccountColumn.VALUE.getKey())].trim().replaceAll("\n",
                            "");
            final String name = _row[_colName2Index.get(Periode_Base.AcccountColumn.NAME.getKey())].trim().replaceAll(
                            "\n", "");
            final String type = _row[_colName2Index.get(Periode_Base.AcccountColumn.TYPE.getKey())].trim().replaceAll(
                            "\n", "");
            final boolean summary = "yes".equalsIgnoreCase(_row[_colName2Index.get(Periode_Base.AcccountColumn.SUMMARY
                            .getKey())]);
            final String parentTmp = _row[_colName2Index.get(Periode_Base.AcccountColumn.PARENT.getKey())];
            this.parent = parentTmp == null ? null : parentTmp.trim().replaceAll("\n", "");

            final Insert insert = new Insert(Periode_Base.TYPE2TYPE.get(type));
            insert.add("PeriodeLink", _periode.getId());
            insert.add("Name", this.value);
            insert.add("Description", name);
            insert.add("Summary", summary);
            insert.execute();
            this.instance = insert.getInstance();
        }

        /**
         * new Constructor for import accounts of the period.
         * @param _accountIns Instance of the account.
         * @param _parentName name of a parent accounts.
         * @param _name name of account.
         */
        public ImportAccount(final Instance _accountIns,
                             final String _parentName,
                             final String _name)
        {
            this.instance = _accountIns;
            this.parent = _parentName;
            this.value = _name;
        }

        /**
         * Getter method for instance variable {@link #value}.
         *
         * @return value of instance variable {@link #value}
         */
        public String getValue()
        {
            return this.value;
        }

        /**
         * Getter method for instance variable {@link #parent}.
         *
         * @return value of instance variable {@link #parent}
         */
        public String getParent()
        {
            return this.parent;
        }

        /**
         * Getter method for instance variable {@link #instance}.
         *
         * @return value of instance variable {@link #instance}
         */
        public Instance getInstance()
        {
            return this.instance;
        }
    }

    /**
     * Represents on report to be imported.
     */
    public class ImportReport
    {

        /**
         * Instance of this report.
         */
        private final Instance instance;

        /**
         * Mapping of node to node level.
         */
        private final Map<Integer, List<ImportNode>> level2Nodes = new HashMap<Integer, List<ImportNode>>();

        /**
         * @param _periodInst instance of the period.
         * @param _type name of type.
         * @param _name name of the report.
         * @param _description description of the report.
         * @param _numbering numbering of the report.
         * @throws EFapsException on error
         */
        protected ImportReport(final Instance _periodInst,
                            final String _type,
                            final String _name,
                            final String _description,
                            final String _numbering)
            throws EFapsException
        {

            final Insert insert = new Insert(Periode_Base.TYPE2TYPE.get(_type));
            insert.add(CIAccounting.ReportAbstract.PeriodeLink, _periodInst.getId());
            insert.add(CIAccounting.ReportAbstract.Name, _name);
            insert.add(CIAccounting.ReportAbstract.Description, _description);
            insert.add(CIAccounting.ReportAbstract.Numbering, _numbering);
            insert.execute();
            this.instance = insert.getInstance();
        }

        /**
         * Add a node to this report.
         *
         * @param _colName2Index mapping of column name 2 index
         * @param _row actual row
         * @param _max max level key
         * @param _accounts list of accounts
         * @throws EFapsException on error
         */
        public void addNode(final Map<String, Integer> _colName2Index,
                            final String[] _row,
                            final Integer _max,
                            final Map<String, Periode_Base.ImportAccount> _accounts)
            throws EFapsException
        {
            // search the level
            int level = 0;
            String levelkey = "";
            for (Integer i = 0; i < _max; i++) {
                levelkey = Periode_Base.LEVELCOLUMN.replace("#", i.toString());
                final String value = _row[_colName2Index.get(levelkey)];
                if (value != null && value.length() > 1) {
                    level = i;
                    break;
                }
            }

            final Instance parentInst;
            if (level < 1) {
                parentInst = this.instance;
            } else {
                final List<ImportNode> lis = this.level2Nodes.get(level - 1);
                parentInst = lis.get(lis.size() - 1).getInstance();
            }
            List<ImportNode> nodes;
            if (this.level2Nodes.containsKey(level)) {
                nodes = this.level2Nodes.get(level);
            } else {
                nodes = new ArrayList<ImportNode>();
                this.level2Nodes.put(level, nodes);
            }
            final ImportNode node = new ImportNode(_colName2Index, _row, levelkey, parentInst, _accounts, nodes.size());
            nodes.add(node);
        }

    }

    /**
     * Represents one node to be imported.
     */
    public class ImportNode
    {

        /**
         * Instance of this node.
         */
        private final Instance instance;

        /**
         * new Constructor for import Nodes.
         *
         * @param _colName2Index mapping of column name 2 index.
         * @param _row actual row.
         * @param _level level key.
         * @param _parentInst parent instance.
         * @param _accounts list of accounts.
         * @param _position order position.
         * @throws EFapsException on error.
         */
        public ImportNode(final Map<String, Integer> _colName2Index,
                          final String[] _row,
                          final String _level,
                          final Instance _parentInst,
                          final Map<String, Periode_Base.ImportAccount> _accounts,
                          final int _position)
            throws EFapsException
        {
            final String type = _row[_colName2Index.get(Periode_Base.ReportColumn.NODE_TYPE.getKey())].trim()
                            .replaceAll("\n", "");
            final boolean showAllways = !"false".equalsIgnoreCase(_row[_colName2Index
                            .get(Periode_Base.ReportColumn.NODE_SHOW.getKey())]);
            final boolean showSum = !"false".equalsIgnoreCase(_row[_colName2Index
                            .get(Periode_Base.ReportColumn.NODE_SUM.getKey())]);
            final String number = _row[_colName2Index.get(Periode_Base.ReportColumn.NODE_NUMBER.getKey())].trim()
                            .replaceAll("\n", "");
            final String value = _row[_colName2Index.get(_level)].trim().replaceAll("\n", "");

            final Insert insert = new Insert(Periode_Base.TYPE2TYPE.get(type));
            insert.add(CIAccounting.ReportNodeAbstract.ShowAllways, showAllways);
            insert.add(CIAccounting.ReportNodeAbstract.ShowSum, showSum);

            if (CIAccounting.ReportNodeRoot.equals(Periode_Base.TYPE2TYPE.get(type))) {
                insert.add(CIAccounting.ReportNodeRoot.Number, number);
                insert.add(CIAccounting.ReportNodeRoot.Label, value);
                insert.add(CIAccounting.ReportNodeRoot.ReportLink, _parentInst.getId());
                insert.add(CIAccounting.ReportNodeRoot.Position, _position);
            } else if (CIAccounting.ReportNodeTree.equals(Periode_Base.TYPE2TYPE.get(type))) {
                insert.add(CIAccounting.ReportNodeTree.Number, number);
                insert.add(CIAccounting.ReportNodeTree.Label, value);
                insert.add(CIAccounting.ReportNodeTree.ParentLink, _parentInst.getId());
                insert.add(CIAccounting.ReportNodeTree.Position, _position);
            } else if (CIAccounting.ReportNodeAccount.equals(Periode_Base.TYPE2TYPE.get(type))) {
                insert.add(CIAccounting.ReportNodeAccount.ParentLink, _parentInst.getId());
                insert.add(CIAccounting.ReportNodeAccount.AccountLink, _accounts.get(value).getInstance().getId());
            }
            insert.execute();
            this.instance = insert.getInstance();
        }

        /**
         * Getter method for instance variable {@link #instance}.
         *
         * @return value of instance variable {@link #instance}
         */
        public Instance getInstance()
        {
            return this.instance;
        }
    }
}
