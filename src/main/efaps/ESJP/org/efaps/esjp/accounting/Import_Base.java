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
 * Revision:        $Rev: 7855 $
 * Last Changed:    $Date: 2012-08-02 20:35:53 -0500 (jue, 02 ago 2012) $
 * Last Changed By: $Author: jan@moxter.net $
 */

package org.efaps.esjp.accounting;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.efaps.admin.datamodel.Classification;
import org.efaps.admin.datamodel.Type;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Return;
import org.efaps.admin.program.esjp.EFapsRevision;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.ci.CIType;
import org.efaps.db.Context;
import org.efaps.db.Context.FileParameter;
import org.efaps.db.Delete;
import org.efaps.db.Insert;
import org.efaps.db.Instance;
import org.efaps.db.InstanceQuery;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.db.Update;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.util.EFapsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger LOG = LoggerFactory.getLogger(Import_Base.class);

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

    private enum CaseColumn implements Import_Base.Column {

        /** */
        A2CTYPE("[Account2Case_Type]"),
        A2CACC("[Account2Case_Account]"),
        A2CCLA("[Account2Case_Classification]"),
        A2CNUM("[Account2Case_Numerator]"),
        A2CDENUM("[Account2Case_Denominator]"),
        A2CDEFAULT("[Account2Case_Default]"),
        CASETYPE("[Case_Type]"),
        CASENAME("[Case_Name]"),
        CASEDESC("[Case_Description]");
        ;

        /** Key. */
        private final String key;

        /**
         * @param _key key
         */
        private CaseColumn(final String _key)
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
        PARENT("[Account_Parent]"),
        /** */
        KEY("[Account_Key]"),
        /** */
        ACC_REL("[Account_Relation]"),
        /** */
        ACC_TARGET("[Account_Target]");

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
        Import_Base.TYPE2TYPE.put("ViewRoot", CIAccounting.ViewRoot);
        Import_Base.TYPE2TYPE.put("ViewSum", CIAccounting.ViewSum);
        Import_Base.TYPE2TYPE.put("CaseBankCashGain", CIAccounting.CaseBankCashGain);
        Import_Base.TYPE2TYPE.put("CaseBankCashPay", CIAccounting.CaseBankCashPay);
        Import_Base.TYPE2TYPE.put("CaseDocBooking", CIAccounting.CaseDocBooking);
        Import_Base.TYPE2TYPE.put("CaseDocRegister", CIAccounting.CaseDocRegister);
        Import_Base.TYPE2TYPE.put("CaseExternalBooking", CIAccounting.CaseExternalBooking);
        Import_Base.TYPE2TYPE.put("CaseExternalRegister", CIAccounting.CaseExternalRegister);
        Import_Base.TYPE2TYPE.put("CaseGeneral", CIAccounting.CaseGeneral);
        Import_Base.TYPE2TYPE.put("CasePayroll", CIAccounting.CasePayroll);
        Import_Base.TYPE2TYPE.put("CasePettyCash", CIAccounting.CasePettyCash);
        Import_Base.TYPE2TYPE.put("CaseStockBooking", CIAccounting.CaseStockBooking);
    }

    protected static final Map<String, CIType> ACC2ACC = new HashMap<String, CIType>();
    static {
        Import_Base.ACC2ACC.put("ViewSumAccount", CIAccounting.ViewSum2Account);
        Import_Base.ACC2ACC.put("AccountCosting", CIAccounting.Account2AccountCosting);
        Import_Base.ACC2ACC.put("AccountInverseCosting", CIAccounting.Account2AccountCostingInverse);
        Import_Base.ACC2ACC.put("AccountAbono", CIAccounting.Account2AccountCredit);
        Import_Base.ACC2ACC.put("AccountCargo", CIAccounting.Account2AccountDebit);
    }


    protected static final Map<String, CIType> ACC2CASE = new HashMap<String, CIType>();
    static {
        Import_Base.ACC2CASE.put("Credit", CIAccounting.Account2CaseCredit);
        Import_Base.ACC2CASE.put("Debit", CIAccounting.Account2CaseDebit);
        Import_Base.ACC2CASE.put("CreditClassification", CIAccounting.Account2CaseCredit4Classification);
        Import_Base.ACC2CASE.put("DebitClassification", CIAccounting.Account2CaseDebit4Classification);
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
                                        .attribute(CIAccounting.AccountAbstract.Name);
        multi.addSelect(sel);
        multi.addAttribute(CIAccounting.AccountAbstract.Name,
                            CIAccounting.AccountAbstract.Description);
        multi.execute();
        while (multi.next()) {
            final String parentName = multi.<String>getSelect(sel);
            final String name = multi.<String>getAttribute(CIAccounting.AccountAbstract.Name);
            final String desc = multi.<String>getAttribute(CIAccounting.AccountAbstract.Description);
            ret.put(name, new ImportAccount(multi.getCurrentInstance(), parentName, name, desc, null, null, null, null));
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
                                entries.get(0), null);
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
            final Map<String, List<String>> validateMap = new HashMap<String, List<String>>();
            final Map<String, Integer> colName2Index = evaluateCSVFileHeader(Import_Base.AcccountColumn.values(),
                            entries.get(0), validateMap);
            entries.remove(0);

            for (final String[] row : entries) {
                final ImportAccount account = new ImportAccount(_periodInst, colName2Index, row, validateMap, null);
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
                if (account.getLstTypeConn() != null && !account.getLstTypeConn().isEmpty() &&
                                account.getLstTargetConn() != null && !account.getLstTargetConn().isEmpty()) {
                    final List<Type> lstTypes = account.getLstTypeConn();
                    final List<String> lstTarget = account.getLstTargetConn();

                    int cont = 0;
                    for (final Type type : lstTypes) {
                        deleteExistingConnections(type, account.getInstance());

                        final String nameAcc = lstTarget.get(cont);
                        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.AccountAbstract);
                        queryBldr.addWhereAttrEqValue(CIAccounting.AccountAbstract.Name, nameAcc);
                        queryBldr.addWhereAttrEqValue(CIAccounting.AccountAbstract.PeriodeAbstractLink,
                                        _periodInst.getId());
                        final InstanceQuery query = queryBldr.getQuery();
                        query.execute();
                        if (query.next()) {
                            final Insert insert = new Insert(type);
                            insert.add(CIAccounting.Account2AccountAbstract.FromAccountLink,
                                            account.getInstance().getId());
                            insert.add(CIAccounting.Account2AccountAbstract.ToAccountLink,
                                            query.getCurrentValue().getId());
                            insert.execute();
                        }
                        cont++;
                    }
                }
            }
        } catch (final IOException e) {
            throw new EFapsException(Periode.class, "createAccountTable.IOException", e);
        }
        return accounts;
    }

    protected void deleteExistingConnections(final Type _type,
                                             final Instance _accInstance)
        throws EFapsException
    {
        final QueryBuilder queryBldrConn = new QueryBuilder(_type);
        queryBldrConn.addWhereAttrEqValue(CIAccounting.Account2AccountAbstract.FromAccountLink, _accInstance);
        final InstanceQuery query = queryBldrConn.getQuery();
        query.execute();
        while (query.next()) {
            final Delete delete = new Delete(query.getCurrentValue());
            delete.execute();
        }
    }

    protected HashMap<String, ImportAccount> createViewAccountTable(final Instance _periodInst,
                                                                final FileParameter _accountTable)
        throws EFapsException
    {
        final HashMap<String, ImportAccount> accounts = new HashMap<String, ImportAccount>();
        final HashMap<String, ImportAccount> accountsVal = new HashMap<String, ImportAccount>();
        try {
            final CSVReader reader = new CSVReader(new InputStreamReader(_accountTable.getInputStream(), "UTF-8"));
            final List<String[]> entries = reader.readAll();
            reader.close();
            final Map<String, List<String>> validateMap = new HashMap<String, List<String>>();
            final Map<String, Integer> colName2Index = evaluateCSVFileHeader(Import_Base.AcccountColumn.values(),
                            entries.get(0), validateMap);
            entries.remove(0);

            for (final String[] row : entries) {
                final ImportAccount account = new ImportAccount(colName2Index, row);
                accountsVal.put(account.getOrder(), account);
            }

            for (final ImportAccount account : accountsVal.values()) {
                ImportAccount parent = accountsVal.get(account.getParent());
                while (parent != null) {
                    account.setPath(account.getPath() + "_" + parent.getValue());
                    parent = parent.getParent() != null ? accountsVal.get(parent.getParent()) : null;
                }
            }

            for (final String[] row : entries) {
                final ImportAccount account = new ImportAccount(_periodInst, colName2Index, row, validateMap, accountsVal);
                accounts.put(account.getOrder(), account);
            }
            for (final ImportAccount account : accounts.values()) {
                if (account.getParent() != null && account.getParent().length() > 0) {
                    final ImportAccount parent = accounts.get(account.getParent());
                    if (parent != null) {
                        final Update update = new Update(account.getInstance());
                        update.add(CIAccounting.AccountBaseAbstract.ParentLink, parent.getInstance().getId());
                        update.execute();
                    }
                }
                if (account.getLstTypeConn() != null && !account.getLstTypeConn().isEmpty() &&
                                account.getLstTargetConn() != null && !account.getLstTargetConn().isEmpty()) {
                    final List<Type> lstTypes = account.getLstTypeConn();
                    final List<String> lstTarget = account.getLstTargetConn();

                    int cont = 0;
                    for (final Type type : lstTypes) {
                        final String nameAcc = lstTarget.get(cont);
                        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.AccountAbstract);
                        queryBldr.addWhereAttrEqValue(CIAccounting.AccountAbstract.Name, nameAcc);
                        queryBldr.addWhereAttrEqValue(CIAccounting.AccountAbstract.PeriodeAbstractLink,
                                        _periodInst.getId());
                        final InstanceQuery query = queryBldr.getQuery();
                        query.execute();
                        if (query.next()) {
                            final Insert insert = new Insert(type);
                            insert.add(CIAccounting.View2AccountAbstract.FromLinkAbstract,
                                            account.getInstance().getId());
                            insert.add(CIAccounting.View2AccountAbstract.ToLinkAbstract,
                                            query.getCurrentValue().getId());
                            insert.execute();
                        }
                        cont++;
                    }
                }
            }
        } catch (final IOException e) {
            throw new EFapsException(Periode.class, "createAccountTable.IOException", e);
        }
        return accounts;
    }


    protected void createCaseTable(final Instance _periodInst,
                                   final FileParameter _accountTable)
        throws EFapsException
    {
        try {
            final CSVReader reader = new CSVReader(new InputStreamReader(_accountTable.getInputStream(), "UTF-8"));
            final List<String[]> entries = reader.readAll();
            reader.close();
            final Map<String, List<String>> validateMap = new HashMap<String, List<String>>();
            final Map<String, Integer> colName2Index = evaluateCSVFileHeader(Import_Base.CaseColumn.values(),
                            entries.get(0), validateMap);
            entries.remove(0);
            final List<ImportCase> cases = new ArrayList<ImportCase>();
            int i = 1;
            boolean valid = true;
            for (final String[] row : entries) {
                Import_Base.LOG.info("reading Line {}: {}",i, row);
                final ImportCase impCase = new ImportCase(_periodInst, colName2Index, row);
                if (!impCase.validate()) {
                    valid = false;
                    Import_Base.LOG.error("Line {} is invalid; {}",i , impCase);
                }
                cases.add(impCase);
                i++;
            }
            if (valid) {
                for (final ImportCase impCase : cases) {
                    impCase.update();
                }
            }
        } catch (final UnsupportedEncodingException e) {
            throw new EFapsException("UnsupportedEncodingException", e);
        } catch (final IOException e) {
            throw new EFapsException("IOException", e);
        }
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
                                                         final String[] _headerLine,
                                                         final Map<String, List<String>> _validateMap)
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
                    } else {
                        if (_validateMap != null && column.contains(columns.getKey().replace("]", ""))) {
                            final String numStr = column.replace(columns.getKey().replace("]", ""), "").replace("]", "");
                            if (_validateMap.containsKey(numStr)) {
                                final List<String> lst = _validateMap.get(numStr);
                                lst.add(column);
                            } else {
                                final ArrayList<String> lst = new ArrayList<String>();
                                lst.add(column);
                                _validateMap.put(numStr, lst);
                            }
                            ret.put(column, idx);
                            break;
                        }
                    }
                }
            }
            idx++;
        }

        // if keys are defined, check for all required indexes
        if (_columns != null) {
            for (final Column column : _columns) {
                if (ret.get(column.getKey()) == null) {
                    if (_validateMap != null) {
                        for (final Entry<String, List<String>> entry : _validateMap.entrySet()) {
                            if (ret.get(column.getKey().replace("]", "") + entry.getKey() + "]") == null) {
                                throw new EFapsException(Import_Base.class, "ColumnNotDefinded", column.getKey());
                            } else {
                                if (entry.getValue().size() != 2) {
                                    throw new EFapsException(Import_Base.class, "ColumnNotDefinded",
                                                    column.getKey() + entry.getKey());
                                }
                            }
                        }
                    } else {
                        throw new EFapsException(Import_Base.class, "ColumnNotDefinded",
                                        column.getKey() + column.getKey());
                    }
                }
            }
        }
        Import_Base.LOG.info("analysed table headers: {}", ret);
        return ret;
    }

    public class ImportCase
    {

        private String caseName;
        private String caseDescription;
        private CIType casetype;
        private CIType a2cType;
        private String a2cClass;
        private String a2cNum;
        private String a2cDenum;
        private boolean a2cDefault;
        private Instance accInst;
        private Instance periodeInst;

        /**
         * @param _colName2Index
         * @param _row
         */
        public ImportCase(final Instance _periodInst,
                          final Map<String, Integer> _colName2Index,
                          final String[] _row)
        {
            try {
                this.periodeInst = _periodInst;
                this.caseName = _row[_colName2Index.get(Import_Base.CaseColumn.CASENAME.getKey())].trim()
                                .replaceAll("\n", "");
                this.caseDescription = _row[_colName2Index.get(Import_Base.CaseColumn.CASEDESC.getKey())].trim()
                                .replaceAll("\n", "");
                final String type = _row[_colName2Index.get(Import_Base.CaseColumn.CASETYPE.getKey())].trim()
                                .replaceAll("\n", "");
                this.casetype = Import_Base.TYPE2TYPE.get(type);
                final String a2c = _row[_colName2Index.get(Import_Base.CaseColumn.A2CTYPE.getKey())].trim()
                                .replaceAll("\n", "");

                this.a2cType = Import_Base.ACC2CASE.get(a2c);

                this.a2cNum = _row[_colName2Index.get(Import_Base.CaseColumn.A2CNUM.getKey())].trim()
                                .replaceAll("\n", "");
                this.a2cDenum = _row[_colName2Index.get(Import_Base.CaseColumn.A2CDENUM.getKey())].trim()
                                .replaceAll("\n", "");
                this.a2cDefault = "yes".equalsIgnoreCase(_row[_colName2Index.get(Import_Base.CaseColumn.A2CDEFAULT
                                .getKey())])
                                || "true".equalsIgnoreCase(_row[_colName2Index.get(Import_Base.CaseColumn.A2CDEFAULT
                                                .getKey())]);

                final String accName = _row[_colName2Index.get(Import_Base.CaseColumn.A2CACC.getKey())].trim()
                                .replaceAll("\n", "");

                if (_colName2Index.containsKey(Import_Base.CaseColumn.A2CCLA.getKey())
                        && (a2cType.getType().isKindOf(CIAccounting.Account2CaseDebit4Classification.getType())
                            || a2cType.getType().isKindOf(CIAccounting.Account2CaseCredit4Classification.getType()))) {
                    this.a2cClass = _row[_colName2Index.get(Import_Base.CaseColumn.A2CCLA.getKey())].trim()
                                .replaceAll("\n", "");
                }

                final QueryBuilder queryBuilder = new QueryBuilder(CIAccounting.AccountAbstract);
                queryBuilder.addWhereAttrEqValue(CIAccounting.AccountAbstract.Name, accName);
                queryBuilder.addWhereAttrEqValue(CIAccounting.AccountAbstract.PeriodeAbstractLink, _periodInst.getId());
                final InstanceQuery query = queryBuilder.getQuery();
                query.executeWithoutAccessCheck();
                if (query.next()) {
                    this.accInst = query.getCurrentValue();
                }
            } catch (final Exception e) {
                Import_Base.LOG.error("Catched error on Import.", e);
            }
        }

        /**
         * @return
         */
        public boolean validate()
        {
            return this.caseName != null && this.casetype != null && this.a2cDenum != null && this.a2cNum != null
                            && this.accInst != null
                            && this.accInst.isValid();
        }

        /**
         *
         */
        public void update()
            throws EFapsException
        {
            final QueryBuilder queryBuilder = new QueryBuilder(this.casetype);
            queryBuilder.addWhereAttrEqValue(CIAccounting.CaseAbstract.Name, this.caseName);
            queryBuilder.addWhereAttrEqValue(CIAccounting.CaseAbstract.PeriodeAbstractLink, this.periodeInst.getId());
            final InstanceQuery query = queryBuilder.getQuery();
            query.executeWithoutAccessCheck();
            Instance caseInst;
            if (query.next()) {
                caseInst = query.getCurrentValue();
            } else {
                final Insert insert = new Insert(this.casetype);
                insert.add(CIAccounting.CaseAbstract.Name, this.caseName);
                insert.add(CIAccounting.CaseAbstract.Description, this.caseDescription);
                insert.add(CIAccounting.CaseAbstract.PeriodeAbstractLink, this.periodeInst.getId());
                insert.execute();
                caseInst = insert.getInstance();
            }

            final Insert insert = new Insert(this.a2cType);
            insert.add(CIAccounting.Account2CaseAbstract.ToCaseAbstractLink, caseInst.getId());
            insert.add(CIAccounting.Account2CaseAbstract.FromAccountAbstractLink, this.accInst.getId());
            insert.add(CIAccounting.Account2CaseAbstract.Denominator, this.a2cDenum);
            insert.add(CIAccounting.Account2CaseAbstract.Numerator, this.a2cNum);
            insert.add(CIAccounting.Account2CaseAbstract.Default, this.a2cDefault);
            if (this.a2cClass != null) {
                insert.add(CIAccounting.Account2CaseAbstract.LinkValue, Classification.get(this.a2cClass).getId());
            }
            insert.execute();
        }

        @Override
        public String toString()
        {
            return ToStringBuilder.reflectionToString(this, ToStringStyle.MULTI_LINE_STYLE);
        }
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
         * Description for this account.
         */
        private final String description;

        /**
         * Value for this account.
         */
        private final String order;

        /**
         * Parent of this account.
         */
        private final String parent;

        /**
         * Path of this account.
         */
        private String path;

        /**
         * Instance of this account.
         */
        private final Instance instance;

        /**
         * Type for the account connection.
         */
        private final List<Type> lstTypeConn;

        /**
         * Type for the account connection.
         */
        private final List<String> lstTargetConn;

        /**
         * @param _periode periode this account belong to
         * @param _colName2Index mapping o column name to index
         * @param _row actual row
         * @throws EFapsException on error
         */
        public ImportAccount(final Instance _periode,
                             final Map<String, Integer> _colName2Index,
                             final String[] _row,
                             final Map<String, List<String>> _validateMap,
                             final Map<String, ImportAccount> _accountVal)
            throws EFapsException
        {
            this.lstTypeConn = new ArrayList<Type>();
            this.lstTargetConn = new ArrayList<String>();

            this.value = _row[_colName2Index.get(Import_Base.AcccountColumn.VALUE.getKey())].trim().replaceAll("\n",
                            "");
            this.description = _row[_colName2Index.get(Import_Base.AcccountColumn.NAME.getKey())].trim()
                            .replaceAll("\n", "");
            final String type = _row[_colName2Index.get(Import_Base.AcccountColumn.TYPE.getKey())].trim().replaceAll(
                            "\n", "");
            final boolean summary = "yes".equalsIgnoreCase(_row[_colName2Index.get(Import_Base.AcccountColumn.SUMMARY
                            .getKey())]);
            final String parentTmp = _row[_colName2Index.get(Import_Base.AcccountColumn.PARENT.getKey())];

            if (_validateMap != null) {
                for (final Entry<String, List<String>> entry : _validateMap.entrySet()) {
                    final String typeConnTmp = _row[_colName2Index.get(Import_Base.AcccountColumn.ACC_REL.getKey()
                                    .replace("]", entry.getKey() + "]"))].trim().replaceAll("\n", "");
                    final String targetConnTmp = _row[_colName2Index.get(Import_Base.AcccountColumn.ACC_TARGET.getKey()
                                    .replace("]", entry.getKey() + "]"))].trim().replaceAll("\n", "");
                    if (typeConnTmp != null && !typeConnTmp.isEmpty()
                                    && targetConnTmp != null && !targetConnTmp.isEmpty()) {
                        this.lstTypeConn.add(Type.get(Import_Base.ACC2ACC.get(typeConnTmp).uuid));
                        this.lstTargetConn.add(targetConnTmp);
                    }
                }
                this.order = _row[_colName2Index.get(Import_Base.AcccountColumn.KEY.getKey())]
                                                                    .trim().replaceAll("\n", "");
            } else {
                this.order = null;
            }

            if (_accountVal != null) {
                this.path = _accountVal.get(this.order).getPath();
            } else {
                this.path = null;
            }

            this.parent = parentTmp == null ? null : parentTmp.trim().replaceAll("\n", "");

            Update update = null;
            if (Import_Base.TYPE2TYPE.get(type).getType().isKindOf(CIAccounting.AccountAbstract.getType())) {
                final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.AccountAbstract);
                queryBldr.addWhereAttrEqValue(CIAccounting.AccountBaseAbstract.Name, this.value);
                queryBldr.addWhereAttrEqValue(CIAccounting.AccountBaseAbstract.PeriodeAbstractLink, _periode.getId());
                final MultiPrintQuery multi = queryBldr.getPrint();
                final SelectBuilder selParent = new SelectBuilder().linkto(CIAccounting.AccountBaseAbstract.ParentLink)
                                .attribute(CIAccounting.AccountBaseAbstract.Name);
                multi.addSelect(selParent);
                multi.execute();
                if (multi.next()) {
                    final String parentName = multi.<String>getSelect(selParent);
                    if (parentName != null && parentName.equals(parentTmp)) {
                        update = new Update(multi.getCurrentInstance());
                    } else {
                        update = new Insert(Import_Base.TYPE2TYPE.get(type));
                    }
                } else {
                    update = new Insert(Import_Base.TYPE2TYPE.get(type));
                }
            } else if (Import_Base.TYPE2TYPE.get(type).getType().isKindOf(CIAccounting.ViewAbstract.getType())) {
                final String[] parts = this.path.split("_");
                final Instance updateInst = validateUpdate(this.value, null, _periode, 0, parts);

                if (updateInst != null) {
                    update = new Update(updateInst);
                } else {
                    update = new Insert(Import_Base.TYPE2TYPE.get(type));
                }
            }

            if (Import_Base.TYPE2TYPE.get(type).getType().isKindOf(CIAccounting.AccountAbstract.getType())) {
                update.add("Summary", summary);
            }

            update.add(CIAccounting.AccountBaseAbstract.PeriodeAbstractLink, _periode.getId());
            update.add(CIAccounting.AccountBaseAbstract.Name, this.value);
            update.add(CIAccounting.AccountBaseAbstract.Description, this.description);
            update.execute();
            this.instance = update.getInstance();
        }

        private Instance validateUpdate(final String _name,
                                       final Long _id,
                                       final Instance _periode,
                                       int _cont,
                                       final String[] _parts) throws EFapsException {
            Instance ret = null;
            boolean ver = false;
            Instance instCur = null;
            final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.ViewAbstract);
            if (_cont == 0) {
                queryBldr.addWhereAttrEqValue(CIAccounting.AccountBaseAbstract.Name, _name);
                queryBldr.addWhereAttrEqValue(CIAccounting.AccountBaseAbstract.PeriodeAbstractLink, _periode.getId());
                ver = true;
            } else {
                queryBldr.addWhereAttrEqValue(CIAccounting.AccountBaseAbstract.ID, _id);
            }
            final MultiPrintQuery multi = queryBldr.getPrint();
            final SelectBuilder selParent = new SelectBuilder().linkto(CIAccounting.AccountBaseAbstract.ParentLink)
                            .attribute(CIAccounting.AccountBaseAbstract.Name);
            multi.addSelect(selParent);
            multi.addAttribute(CIAccounting.ViewAbstract.ParentLink, CIAccounting.ViewAbstract.Name);
            multi.execute();
            _cont++;
            while (multi.next()) {
                instCur = multi.getCurrentInstance();
                final Long parentId = multi.<Long>getAttribute(CIAccounting.ViewAbstract.ParentLink);
                final String parentName = multi.<String>getSelect(selParent);
                if (_cont < (_parts.length)) {
                    if (parentName.equals(_parts[_cont])) {
                        ret = validateUpdate(parentName, parentId, _periode, _cont, _parts);
                        if (ret != null) {
                            break;
                        }
                    }
                } else if (_cont == _parts.length && parentId == null) {
                    ret = multi.getCurrentInstance();
                    break;
                }
            }
            if (ver && ret != null) {
                ret = instCur;
            }
            return ret;
        }

        /**
         * new Constructor for import accounts of the period.
         * @param _accountIns Instance of the account.
         * @param _parentName name of a parent accounts.
         * @param _name name of account.
         */
        public ImportAccount(final Map<String, Integer> _colName2Index,
                              final String[] _row)
        {
            this.lstTypeConn = new ArrayList<Type>();
            this.lstTargetConn = new ArrayList<String>();

            this.value = _row[_colName2Index.get(Import_Base.AcccountColumn.VALUE.getKey())].trim().replaceAll("\n",
                            "");
            this.description = _row[_colName2Index.get(Import_Base.AcccountColumn.NAME.getKey())].trim().replaceAll("\n",
                            "");
            final String parentTmp = _row[_colName2Index.get(Import_Base.AcccountColumn.PARENT.getKey())];

            this.order = _row[_colName2Index.get(Import_Base.AcccountColumn.KEY.getKey())]
                            .trim().replaceAll("\n", "");
            this.parent = parentTmp == null ? null : parentTmp.trim().replaceAll("\n", "");
            this.instance = null;
            this.path = this.value;
        }

        /**
         * new Constructor for import accounts of the period.
         * @param _accountIns Instance of the account.
         * @param _parentName name of a parent accounts.
         * @param _name name of account.
         */
        public ImportAccount(final Instance _accountIns,
                             final String _parentName,
                             final String _name,
                             final String _description,
                             final String _order,
                             final String _path,
                             final List<Type> _lstTypeConn,
                             final List<String> _lstTargetConn)
        {
            this.instance = _accountIns;
            this.parent = _parentName;
            this.value = _name;
            this.description = _description;
            this.lstTypeConn = _lstTypeConn;
            this.lstTargetConn = _lstTargetConn;
            this.order = _order;
            this.path = _path;
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
         * Getter method for instance variable {@link #description}.
         *
         * @return description of instance variable {@link #description}
         */
        public String getDescription()
        {
            return this.description;
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
         * Getter method for instance variable {@link #order}.
         *
         * @return value of instance variable {@link #order}
         */
        public String getOrder()
        {
            return this.order;
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

        /**
         * Getter method for instance variable {@link #lstTypeConn}.
         *
         * @return the lstTypeConn
         */
        private List<Type> getLstTypeConn()
        {
            return this.lstTypeConn;
        }

        /**
         * Getter method for instance variable {@link #lstTargetConn}.
         *
         * @return the lstTargetConn
         */
        private List<String> getLstTargetConn()
        {
            return this.lstTargetConn;
        }

        /**
         * @return the path
         */
        private String getPath()
        {
            return this.path;
        }

        /**
         * @param path the path to set
         */
        private void setPath(final String path)
        {
            this.path = path;
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
                if (_parentInst.isValid()) {
                    insert.add(CIAccounting.ReportNodeRoot.Number, number);
                    insert.add(CIAccounting.ReportNodeRoot.Label, value);
                    insert.add(CIAccounting.ReportNodeRoot.ReportLink, _parentInst);
                    insert.add(CIAccounting.ReportNodeRoot.Position, _position);
                    insert.execute();
                } else {
                    Import_Base.LOG.error("Report Instance does not exist for '{}'- '{}' ", number, value);
                }
            } else if (CIAccounting.ReportNodeTree.equals(Import_Base.TYPE2TYPE.get(type))) {
                if (_parentInst.isValid()) {
                    insert.add(CIAccounting.ReportNodeTree.Number, number);
                    insert.add(CIAccounting.ReportNodeTree.Label, value);
                    insert.add(CIAccounting.ReportNodeTree.ParentLink, _parentInst);
                    insert.add(CIAccounting.ReportNodeTree.Position, _position);
                    insert.execute();
                } else {
                    Import_Base.LOG.error("Parent Node Instance does not exist for '{}'- '{}' ", number, value);
                }
            } else if (CIAccounting.ReportNodeAccount.equals(Import_Base.TYPE2TYPE.get(type))) {
                if (_parentInst.isValid()) {
                    insert.add(CIAccounting.ReportNodeAccount.ParentLink, _parentInst.getId());
                    if (_accounts.get(value) == null) {
                        Import_Base.LOG.error("Account Instance not exist: " + value);
                    } else {
                        insert.add(CIAccounting.ReportNodeAccount.AccountLink, _accounts.get(value).getInstance());
                        insert.add(CIAccounting.ReportNodeAccount.Label,
                                        value + " - " + _accounts.get(value).getDescription());
                        insert.execute();
                    }
                } else {
                    Import_Base.LOG.error("Parent Node Instance does not exist for Account Node '{}'- '{}' ",
                                    number, value);
                }
            }

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
