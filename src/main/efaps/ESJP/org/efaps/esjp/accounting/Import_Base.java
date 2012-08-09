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
 * Revision:        $Rev: 7855 $
 * Last Changed:    $Date: 2012-08-02 20:35:53 -0500 (jue, 02 ago 2012) $
 * Last Changed By: $Author: jan@moxter.net $
 */

package org.efaps.esjp.accounting;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Return;
import org.efaps.admin.program.esjp.EFapsRevision;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.ci.CIType;
import org.efaps.db.Context;
import org.efaps.db.Context.FileParameter;
import org.efaps.db.Insert;
import org.efaps.db.Instance;
import org.efaps.db.InstanceQuery;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.db.Update;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.util.EFapsException;

import au.com.bytecode.opencsv.CSVReader;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 * @version $Id: Periode_Base.java 7855 2012-08-03 01:35:53Z jan@moxter.net $
 */
@EFapsUUID("4f7c8d48-01d0-4862-82e7-2efcf6761e5a")
@EFapsRevision("$Rev: 7855 $")
public abstract class Import_Base
{

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
    private enum AcccountColumn implements Import_Base.Column {
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
        @Override
        public String getKey()
        {
            return this.key;
        }
    }

    /**
     * Columns for an report.
     */
    private enum ReportColumn implements Import_Base.Column {
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
        @Override
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
        Import_Base.TYPE2TYPE.put("Asset", CIAccounting.AccountBalanceSheetAsset);
        Import_Base.TYPE2TYPE.put("Liability", CIAccounting.AccountBalanceSheetLiability);
        Import_Base.TYPE2TYPE.put("Owner's equity", CIAccounting.AccountBalanceSheetEquity);
        Import_Base.TYPE2TYPE.put("Expense", CIAccounting.AccountIncomeStatementExpenses);
        Import_Base.TYPE2TYPE.put("Revenue", CIAccounting.AccountIncomeStatementRevenue);
        Import_Base.TYPE2TYPE.put("Current", CIAccounting.AccountCurrent);
        Import_Base.TYPE2TYPE.put("Creditor", CIAccounting.AccountCurrentCreditor);
        Import_Base.TYPE2TYPE.put("Debtor", CIAccounting.AccountCurrentDebtor);
        Import_Base.TYPE2TYPE.put("Memo", CIAccounting.AccountMemo);
        Import_Base.TYPE2TYPE.put("Balance", CIAccounting.ReportBalance);
        Import_Base.TYPE2TYPE.put("ProfitLoss", CIAccounting.ReportProfitLoss);
        Import_Base.TYPE2TYPE.put("Other", CIAccounting.ReportTransaction);
        Import_Base.TYPE2TYPE.put("Root", CIAccounting.ReportNodeRoot);
        Import_Base.TYPE2TYPE.put("Tree", CIAccounting.ReportNodeTree);
        Import_Base.TYPE2TYPE.put("Account", CIAccounting.ReportNodeAccount);
        Import_Base.TYPE2TYPE.put("ReportAccount", CIAccounting.ReportAccount);
    }

    /**
     * Key for a level column.
     */
    private static String LEVELCOLUMN = "[Level #]";


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
                final Map<String, Integer> colName2Index = evaluateCSVFileHeader(Import_Base.ReportColumn.values(),
                                entries.get(0));
                reader.close();
                Integer i = 0;
                final String[] headRow = entries.get(0);
                boolean found = true;
                while (found) {
                    found = false;
                    final String level = Import_Base.LEVELCOLUMN.replace("#", i.toString());
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
            throw new EFapsException(Import_Base.class, "createReports.IOException", e);
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
        final String type = _row[_colName2Index.get(Import_Base.ReportColumn.TYPE.getKey())].trim()
            .replaceAll("\n", "");
        final String name = _row[_colName2Index.get(Import_Base.ReportColumn.NAME.getKey())].trim()
            .replaceAll("\n", "");
        final String description = _row[_colName2Index.get(Import_Base.ReportColumn.DESC.getKey())].trim()
                        .replaceAll("\n", "");
        final String numbering = _row[_colName2Index.get(Import_Base.ReportColumn.NUMBERING.getKey())].trim()
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
            final Map<String, Integer> colName2Index = evaluateCSVFileHeader(Import_Base.AcccountColumn.values(),
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
    protected Map<String, Integer> evaluateCSVFileHeader(final Import_Base.Column[] _columns,
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
                    throw new EFapsException(Import_Base.class, "ColumnNotDefinded", column.getKey());
                }
            }
        }
        return ret;
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
            this.value = _row[_colName2Index.get(Import_Base.AcccountColumn.VALUE.getKey())].trim().replaceAll("\n",
                            "");
            final String descName = _row[_colName2Index.get(Import_Base.AcccountColumn.NAME.getKey())].trim().replaceAll(
                            "\n", "");
            final String type = _row[_colName2Index.get(Import_Base.AcccountColumn.TYPE.getKey())].trim().replaceAll(
                            "\n", "");
            final boolean summary = "yes".equalsIgnoreCase(_row[_colName2Index.get(Import_Base.AcccountColumn.SUMMARY
                            .getKey())]);
            final String parentTmp = _row[_colName2Index.get(Import_Base.AcccountColumn.PARENT.getKey())];

            Update update = null;
            final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.AccountAbstract);
            queryBldr.addWhereAttrEqValue(CIAccounting.AccountAbstract.Name, this.value);
            queryBldr.addWhereAttrEqValue(CIAccounting.AccountAbstract.PeriodeAbstractLink, _periode.getId());
            final InstanceQuery query = queryBldr.getQuery();
            query.execute();
            if (query.next()) {
                update = new Update(query.getCurrentValue());
            } else {
                update = new Insert(Import_Base.TYPE2TYPE.get(type));
            }

            this.parent = parentTmp == null ? null : parentTmp.trim().replaceAll("\n", "");

            update.add("PeriodeLink", _periode.getId());
            update.add("Name", this.value);
            update.add("Description", descName);
            update.add("Summary", summary);
            update.execute();
            this.instance = update.getInstance();
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

            final Insert insert = new Insert(Import_Base.TYPE2TYPE.get(_type));
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
                            final Map<String, Import_Base.ImportAccount> _accounts)
            throws EFapsException
        {
            // search the level
            int level = 0;
            String levelkey = "";
            for (Integer i = 0; i < _max; i++) {
                levelkey = Import_Base.LEVELCOLUMN.replace("#", i.toString());
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
                          final Map<String, Import_Base.ImportAccount> _accounts,
                          final int _position)
            throws EFapsException
        {
            final String type = _row[_colName2Index.get(Import_Base.ReportColumn.NODE_TYPE.getKey())].trim()
                            .replaceAll("\n", "");
            final boolean showAllways = !"false".equalsIgnoreCase(_row[_colName2Index
                            .get(Import_Base.ReportColumn.NODE_SHOW.getKey())]);
            final boolean showSum = !"false".equalsIgnoreCase(_row[_colName2Index
                            .get(Import_Base.ReportColumn.NODE_SUM.getKey())]);
            final String number = _row[_colName2Index.get(Import_Base.ReportColumn.NODE_NUMBER.getKey())].trim()
                            .replaceAll("\n", "");
            final String value = _row[_colName2Index.get(_level)].trim().replaceAll("\n", "");

            final Insert insert = new Insert(Import_Base.TYPE2TYPE.get(type));
            insert.add(CIAccounting.ReportNodeAbstract.ShowAllways, showAllways);
            insert.add(CIAccounting.ReportNodeAbstract.ShowSum, showSum);

            if (CIAccounting.ReportNodeRoot.equals(Import_Base.TYPE2TYPE.get(type))) {
                insert.add(CIAccounting.ReportNodeRoot.Number, number);
                insert.add(CIAccounting.ReportNodeRoot.Label, value);
                insert.add(CIAccounting.ReportNodeRoot.ReportLink, _parentInst.getId());
                insert.add(CIAccounting.ReportNodeRoot.Position, _position);
            } else if (CIAccounting.ReportNodeTree.equals(Import_Base.TYPE2TYPE.get(type))) {
                insert.add(CIAccounting.ReportNodeTree.Number, number);
                insert.add(CIAccounting.ReportNodeTree.Label, value);
                insert.add(CIAccounting.ReportNodeTree.ParentLink, _parentInst.getId());
                insert.add(CIAccounting.ReportNodeTree.Position, _position);
            } else if (CIAccounting.ReportNodeAccount.equals(Import_Base.TYPE2TYPE.get(type))) {
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
