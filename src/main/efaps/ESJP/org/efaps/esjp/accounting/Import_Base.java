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
 * Revision:        $Rev: 10283 $
 * Last Changed:    $Date: 2013-09-24 14:46:54 -0500 (mar, 24 sep 2013) $
 * Last Changed By: $Author: jorge.cueva@moxter.net $
 */

package org.efaps.esjp.accounting;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.efaps.admin.datamodel.Classification;
import org.efaps.admin.datamodel.Type;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Return;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
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
import org.efaps.esjp.accounting.export.AbstractExport_Base;
import org.efaps.esjp.accounting.export.ColumnAccount;
import org.efaps.esjp.accounting.export.ColumnCase;
import org.efaps.esjp.accounting.export.ColumnReport;
import org.efaps.esjp.accounting.export.IColumn;
import org.efaps.esjp.accounting.util.Accounting.Account2CaseConfig;
import org.efaps.esjp.accounting.util.Accounting.SummarizeConfig;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.erp.CurrencyInst;
import org.efaps.util.EFapsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import au.com.bytecode.opencsv.CSVReader;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 * @version $Id: Period_Base.java 7855 2012-08-03 01:35:53Z jan@moxter.net $
 */
@EFapsUUID("4f7c8d48-01d0-4862-82e7-2efcf6761e5a")
@EFapsApplication("eFapsApp-Accounting")
public abstract class Import_Base
{
    /**
     * Logger used in this class.
     */
    private static final Logger LOG = LoggerFactory.getLogger(Import_Base.class);

    /**
     * Mapping of types as written in the csv and the name in eFaps.
     */
    private static final Map<String, UUID> TYPE2TYPE = new HashMap<>();
    static {
        for (final Entry<UUID, String> entry : AbstractExport_Base.TYPE2TYPE.entrySet()) {
            Import_Base.TYPE2TYPE.put(entry.getValue(), entry.getKey());
        }
    }
    /**
     * Mapping of types as written in the csv and the name in eFaps.
     */
    private static final Map<String, UUID> ACC2ACC = new HashMap<>();
    static {
        Import_Base.ACC2ACC.put("ViewSumAccount", CIAccounting.ViewSum2Account.uuid);
        Import_Base.ACC2ACC.put("AccountCosting", CIAccounting.Account2AccountCosting.uuid);
        Import_Base.ACC2ACC.put("AccountInverseCosting", CIAccounting.Account2AccountCostingInverse.uuid);
        Import_Base.ACC2ACC.put("AccountAbono", CIAccounting.Account2AccountCredit.uuid);
        Import_Base.ACC2ACC.put("AccountCargo", CIAccounting.Account2AccountDebit.uuid);
    }

    /**
     * Mapping of types as written in the csv and the name in eFaps.
     */
    private static final Map<String, UUID> ACC2CASE = new HashMap<>();
    static {
        Import_Base.ACC2CASE.put("Credit", CIAccounting.Account2CaseCredit.uuid);
        Import_Base.ACC2CASE.put("Debit", CIAccounting.Account2CaseDebit.uuid);
        Import_Base.ACC2CASE.put("CreditClassification", CIAccounting.Account2CaseCredit4Classification.uuid);
        Import_Base.ACC2CASE.put("DebitClassification", CIAccounting.Account2CaseDebit4Classification.uuid);
        Import_Base.ACC2CASE.put("CreditTreeView", CIAccounting.Account2CaseCredit4ProductTreeView.uuid);
        Import_Base.ACC2CASE.put("DebitTreeView", CIAccounting.Account2CaseDebit4ProductTreeView.uuid);
        Import_Base.ACC2CASE.put("CreditProdFamily", CIAccounting.Account2CaseCredit4ProductFamily.uuid);
        Import_Base.ACC2CASE.put("DebitProdFamily", CIAccounting.Account2CaseDebit4ProductFamily.uuid);
        Import_Base.ACC2CASE.put("CreditCatProduct", CIAccounting.Account2CaseCredit4CategoryProduct.uuid);
        Import_Base.ACC2CASE.put("DebitCatProduct", CIAccounting.Account2CaseDebit4CategoryProduct.uuid);
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
        final String period = _parameter.getParameterValue("periodLink");
        final FileParameter reports = Context.getThreadContext().getFileParameters().get("reports");
        if (period != null && reports != null) {
            final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.Period);
            queryBldr.addWhereAttrEqValue(CIAccounting.Period.ID, period);
            final MultiPrintQuery multi = queryBldr.getPrint();
            multi.addAttribute(CIAccounting.Period.OID);
            multi.execute();
            Instance instance = null;
            while (multi.next()) {
                instance = Instance.get(multi.<String>getAttribute(CIAccounting.Period.OID));
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
        final Map<String, ImportAccount> ret = new HashMap<>();
        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.AccountAbstract);
        queryBldr.addWhereAttrEqValue(CIAccounting.AccountAbstract.PeriodAbstractLink, _instance.getId());
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
            ret.put(name, new ImportAccount(multi.getCurrentInstance(), parentName, name, desc));
        }

        return ret;
    }

    /**
     * @param _periodInst period the account table belongs to
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
                final Map<String, Integer> colName2Index = evaluateCSVFileHeader(ColumnReport.values(),
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
                final Map<String, ImportReport> reports = new HashMap<>();
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
        final String type = _row[_colName2Index.get(ColumnReport.TYPE.getKey())].trim()
            .replaceAll("\n", "");
        final String name = _row[_colName2Index.get(ColumnReport.NAME.getKey())].trim()
            .replaceAll("\n", "");
        final String description = _row[_colName2Index.get(ColumnReport.DESC.getKey())].trim()
                        .replaceAll("\n", "");
        final String numbering = _row[_colName2Index.get(ColumnReport.NUMBERING.getKey())].trim()
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
     * @param _periodInst period the account table belongs to
     * @param _accountTable csv file
     * @return HashMap
     * @throws EFapsException on error
     */
    protected HashMap<String, ImportAccount> createAccountTable(final Instance _periodInst,
                                                                final FileParameter _accountTable)
        throws EFapsException
    {
        final HashMap<String, ImportAccount> accounts = new HashMap<>();
        try {
            final CSVReader reader = new CSVReader(new InputStreamReader(_accountTable.getInputStream(), "UTF-8"));
            final List<String[]> entries = reader.readAll();
            reader.close();
            final Map<String, List<String>> relAccountColumns = new HashMap<>();
            final Map<String, Integer> colName2Index = evaluateCSVFileHeader(ColumnAccount.values(),
                            entries.get(0), relAccountColumns);
            entries.remove(0);

            for (final String[] row : entries) {
                final ImportAccount account = new ImportAccount(_periodInst, colName2Index, row, relAccountColumns,
                                null);
                accounts.put(account.getKey(), account);
            }
            for (final ImportAccount account : accounts.values()) {
                if (account.getParent() != null && account.getParent().length() > 0) {
                    final ImportAccount parent = accounts.get(account.getParent());
                    if (parent != null) {
                        final Update update = new Update(account.getInstance());
                        update.add(CIAccounting.AccountAbstract.ParentLink, parent.getInstance());
                        update.execute();
                    }
                }
                if (account.getLstTypeConn() != null && !account.getLstTypeConn().isEmpty()
                                && account.getLstTargetConn() != null && !account.getLstTargetConn().isEmpty()) {
                    final List<Type> lstTypes = account.getLstTypeConn();
                    final List<String> lstTarget = account.getLstTargetConn();
                    final List<BigDecimal> lstNumerator = account.getLstNumerator();
                    final List<BigDecimal> lstDenominator = account.getLstDenominator();

                    deleteExistingConnections(lstTypes, account.getInstance());

                    int cont = 0;
                    for (final Type type : lstTypes) {
                        final String targetAcc = lstTarget.get(cont);
                        final Integer numerator = lstNumerator.get(cont).intValue();
                        final Integer denominator = lstDenominator.get(cont).intValue();
                        final ImportAccount target = accounts.get(targetAcc);
                        if (target != null) {
                            final Insert insert = new Insert(type);
                            insert.add(CIAccounting.Account2AccountAbstract.FromAccountLink, account.getInstance());
                            insert.add(CIAccounting.Account2AccountAbstract.ToAccountLink, target.getInstance());
                            insert.add(CIAccounting.Account2AccountAbstract.Numerator, numerator);
                            insert.add(CIAccounting.Account2AccountAbstract.Denominator, denominator);
                            insert.execute();
                        }
                        cont++;
                    }
                }
            }
        } catch (final IOException e) {
            throw new EFapsException(Period.class, "createAccountTable.IOException", e);
        }
        return accounts;
    }

    /**
     * @param _lstTypes list of types the relations will be deleted for
     * @param _accInstance  instanc eof the account the relations will be deleted for
     * @throws EFapsException on error
     */
    protected void deleteExistingConnections(final List<Type> _lstTypes,
                                             final Instance _accInstance)
        throws EFapsException
    {
        for (final Type type : _lstTypes) {
            final QueryBuilder queryBldrConn = new QueryBuilder(type);
            queryBldrConn.addWhereAttrEqValue(CIAccounting.Account2AccountAbstract.FromAccountLink, _accInstance);
            final InstanceQuery query = queryBldrConn.getQuery();
            query.execute();
            while (query.next()) {
                final Delete delete = new Delete(query.getCurrentValue());
                delete.execute();
            }
        }
    }

    /**
     * @param _periodInst
     * @param _accountTable
     * @return
     * @throws EFapsException
     */
    protected HashMap<String, ImportAccount> createViewAccountTable(final Instance _periodInst,
                                                                final FileParameter _accountTable)
        throws EFapsException
    {
        final HashMap<String, ImportAccount> accounts = new HashMap<>();
        final HashMap<String, ImportAccount> accountsVal = new HashMap<>();
        try {
            final CSVReader reader = new CSVReader(new InputStreamReader(_accountTable.getInputStream(), "UTF-8"));
            final List<String[]> entries = reader.readAll();
            reader.close();
            final Map<String, List<String>> validateMap = new HashMap<>();
            final Map<String, Integer> colName2Index = evaluateCSVFileHeader(ColumnAccount.values(),
                            entries.get(0), validateMap);
            entries.remove(0);

            for (final String[] row : entries) {
                final ImportAccount account = new ImportAccount(colName2Index, row);
                accountsVal.put(account.getKey(), account);
            }

            for (final ImportAccount account : accountsVal.values()) {
                ImportAccount parent = accountsVal.get(account.getParent());
                while (parent != null) {
                    account.setPath(account.getPath() + "_" + parent.getValue());
                    parent = parent.getParent() != null ? accountsVal.get(parent.getParent()) : null;
                }
            }

            for (final String[] row : entries) {
                final ImportAccount account = new ImportAccount(_periodInst, colName2Index,
                                                                row, validateMap, accountsVal);
                accounts.put(account.getKey(), account);
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
                if (account.getLstTypeConn() != null && !account.getLstTypeConn().isEmpty()
                                && account.getLstTargetConn() != null && !account.getLstTargetConn().isEmpty()) {
                    final List<Type> lstTypes = account.getLstTypeConn();
                    final List<String> lstTarget = account.getLstTargetConn();

                    int cont = 0;
                    for (final Type type : lstTypes) {
                        final String nameAcc = lstTarget.get(cont);
                        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.AccountAbstract);
                        queryBldr.addWhereAttrEqValue(CIAccounting.AccountAbstract.Name, nameAcc);
                        queryBldr.addWhereAttrEqValue(CIAccounting.AccountAbstract.PeriodAbstractLink,
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
            throw new EFapsException(Period.class, "createAccountTable.IOException", e);
        }
        return accounts;
    }


    /**
     * @param _periodInst
     * @param _accountTable
     * @throws EFapsException
     */
    protected void createCaseTable(final Instance _periodInst,
                                   final FileParameter _accountTable)
        throws EFapsException
    {
        try {
            final CSVReader reader = new CSVReader(new InputStreamReader(_accountTable.getInputStream(), "UTF-8"));
            final List<String[]> entries = reader.readAll();
            reader.close();
            final Map<String, List<String>> validateMap = new HashMap<>();
            final Map<String, Integer> colName2Index = evaluateCSVFileHeader(ColumnCase.values(),
                            entries.get(0), validateMap);
            entries.remove(0);
            final List<ImportCase> cases = new ArrayList<>();
            int i = 1;
            boolean valid = true;
            for (final String[] row : entries) {
                Import_Base.LOG.info("reading Line {}: {}", i, row);
                final ImportCase impCase = new ImportCase(_periodInst, colName2Index, row);
                if (!impCase.validate()) {
                    valid = false;
                    Import_Base.LOG.error("Line {} is invalid; {}", i , impCase);
                }
                cases.add(impCase);
                i++;
            }
            if (valid) {
                final Set<Instance> caseInsts = new HashSet<>();
                for (final ImportCase impCase : cases) {
                    impCase.update(caseInsts);
                    caseInsts.add(impCase.getCaseInst());
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
     * @param _relAccountColumns map of related (linked_ Account Columns
     * @return map defining for each key the related column number
     * @throws EFapsException if a column from the list of keys is not defined
     */
    protected Map<String, Integer> evaluateCSVFileHeader(final IColumn[] _columns,
                                                         final String[] _headerLine,
                                                         final Map<String, List<String>> _relAccountColumns)
        throws EFapsException
    {
        // evaluate header
        int idx = 0;

        final Map<String, Integer> ret = new HashMap<>();
        for (final String column : _headerLine) {
            if (_columns == null) {
                ret.put(column, idx);
            } else {
                for (final IColumn columns : _columns) {
                    if (columns.getKey().equals(column)) {
                        ret.put(column, idx);
                        break;
                    } else {
                        if (_relAccountColumns != null && column.contains(columns.getKey().replace("]", ""))) {
                            final String num = column.replace(columns.getKey().replace("]", ""), "").replace("]", "");
                            if (_relAccountColumns.containsKey(num)) {
                                final List<String> lst = _relAccountColumns.get(num);
                                lst.add(column);
                            } else {
                                final ArrayList<String> lst = new ArrayList<>();
                                lst.add(column);
                                _relAccountColumns.put(num, lst);
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
            for (final IColumn column : _columns) {
                if (ret.get(column.getKey()) == null) {
                    if (_relAccountColumns != null) {
                        for (final Entry<String, List<String>> entry : _relAccountColumns.entrySet()) {
                            if (ret.get(column.getKey().replace("]", "") + entry.getKey() + "]") == null) {
                                throw new EFapsException(Import_Base.class, "ColumnNotDefinded", column.getKey());
                            } else {
                                if (column instanceof ColumnAccount && entry.getValue().size() != 4) {
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
        private String caseLabel;
        private String caseDescription;
        private Type casetype;
        private boolean caseIsCross;
        private Type a2cType;
        private String a2cClass;
        private String a2cNum;
        private String a2cDenum;
        private boolean a2cDefault;
        private boolean a2cLabel;
        private boolean a2cEvalRel;
        private Instance accInst;
        private Instance periodInst;
        private Instance caseInst;
        private SummarizeConfig caseSummarizeConfig;
        private String currencyISO;
        /**
         * @param _periodInst
         * @param _colName2Index
         * @param _row
         */
        public ImportCase(final Instance _periodInst,
                          final Map<String, Integer> _colName2Index,
                          final String[] _row)
        {
            try {
                this.periodInst = _periodInst;
                this.caseName = _row[_colName2Index.get(ColumnCase.CASENAME.getKey())].trim()
                                .replaceAll("\n", "");
                this.caseDescription = _row[_colName2Index.get(ColumnCase.CASEDESC.getKey())].trim()
                                .replaceAll("\n", "");
                this.caseLabel = _row[_colName2Index.get(ColumnCase.CASELABEL.getKey())].trim().replaceAll("\n", "");

                final String type = _row[_colName2Index.get(ColumnCase.CASETYPE.getKey())].trim()
                                .replaceAll("\n", "");
                this.casetype = Type.get(Import_Base.TYPE2TYPE.get(type));
                this.caseIsCross = "yes".equalsIgnoreCase(_row[_colName2Index.get(ColumnCase.CASEISCROSS.getKey())])
                                || "true".equalsIgnoreCase(_row[_colName2Index.get(ColumnCase.CASEISCROSS.getKey())]);
                final String configStr = _row[_colName2Index.get(ColumnCase.CASECONFIG.getKey())].trim();
                if (!configStr.isEmpty()) {
                    this.caseSummarizeConfig = SummarizeConfig.valueOf(configStr.toUpperCase());
                }
                final String a2c = _row[_colName2Index.get(ColumnCase.A2CTYPE.getKey())].trim()
                                .replaceAll("\n", "");

                this.a2cType = Type.get(Import_Base.ACC2CASE.get(a2c));

                this.a2cNum = _row[_colName2Index.get(ColumnCase.A2CNUM.getKey())].trim()
                                .replaceAll("\n", "");
                this.a2cDenum = _row[_colName2Index.get(ColumnCase.A2CDENUM.getKey())].trim()
                                .replaceAll("\n", "");
                this.a2cDefault = BooleanUtils.toBoolean(_row[_colName2Index.get(ColumnCase.A2CDEFAULT.getKey())]);
                this.a2cLabel = BooleanUtils.toBoolean(_row[_colName2Index.get(ColumnCase.A2CAPPLYLABEL.getKey())]);
                this.a2cEvalRel = BooleanUtils.toBoolean(_row[_colName2Index.get(ColumnCase.A2CEVALRELATION.getKey())]);

                final String accName = _row[_colName2Index.get(ColumnCase.A2CACC.getKey())].trim()
                                .replaceAll("\n", "");

                if (_colName2Index.containsKey(ColumnCase.A2CCLA.getKey())
                    && (this.a2cType.isKindOf(CIAccounting.Account2CaseDebit4Classification.getType())
                        || this.a2cType.isKindOf(CIAccounting.Account2CaseCredit4Classification.getType()))) {
                    this.a2cClass = _row[_colName2Index.get(ColumnCase.A2CCLA.getKey())].trim()
                                .replaceAll("\n", "");
                }

                if (_colName2Index.containsKey(ColumnCase.A2CCURRENCY.getKey())) {
                    this.currencyISO = _row[_colName2Index.get(ColumnCase.A2CCURRENCY.getKey())].trim()
                                    .replaceAll("\n", "");
                }
                final QueryBuilder queryBuilder = new QueryBuilder(CIAccounting.AccountAbstract);
                queryBuilder.addWhereAttrEqValue(CIAccounting.AccountAbstract.Name, accName);
                queryBuilder.addWhereAttrEqValue(CIAccounting.AccountAbstract.PeriodAbstractLink, _periodInst.getId());
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
         * @throws EFapsException on error
         */
        public void update(final Set<Instance> _caseInsts)
            throws EFapsException
        {
            final QueryBuilder queryBuilder = new QueryBuilder(this.casetype);
            queryBuilder.addWhereAttrEqValue(CIAccounting.CaseAbstract.Name, this.caseName);
            queryBuilder.addWhereAttrEqValue(CIAccounting.CaseAbstract.PeriodAbstractLink, this.periodInst.getId());
            final InstanceQuery query = queryBuilder.getQuery();
            query.executeWithoutAccessCheck();

            if (query.next()) {
                this.caseInst = query.getCurrentValue();
                if (!_caseInsts.contains(this.caseInst)) {
                    final Update update = new Update(this.caseInst);
                    update.add(CIAccounting.CaseAbstract.Description, this.caseDescription);
                    update.add(CIAccounting.CaseAbstract.Label, this.caseLabel);
                    update.add(CIAccounting.CaseAbstract.IsCross, this.caseIsCross);
                    update.add(CIAccounting.CaseAbstract.SummarizeConfig,
                                    this.caseSummarizeConfig == null ? SummarizeConfig.NONE : this.caseSummarizeConfig);
                    update.execute();

                    final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.Account2CaseAbstract);
                    queryBldr.addWhereAttrEqValue(CIAccounting.Account2CaseAbstract.ToCaseAbstractLink, this.caseInst);
                    for (final Instance inst : queryBldr.getQuery().execute()) {
                        new Delete(inst).execute();
                    }
                }
            } else {
                final Insert insert = new Insert(this.casetype);
                insert.add(CIAccounting.CaseAbstract.Name, this.caseName);
                insert.add(CIAccounting.CaseAbstract.Description, this.caseDescription);
                insert.add(CIAccounting.CaseAbstract.Label, this.caseLabel);
                insert.add(CIAccounting.CaseAbstract.PeriodAbstractLink, this.periodInst.getId());
                insert.add(CIAccounting.CaseAbstract.IsCross, this.caseIsCross);
                insert.add(CIAccounting.CaseAbstract.SummarizeConfig,
                                this.caseSummarizeConfig == null ? SummarizeConfig.NONE : this.caseSummarizeConfig);
                insert.execute();
                this.caseInst = insert.getInstance();
            }

            final Insert insert = new Insert(this.a2cType);
            insert.add(CIAccounting.Account2CaseAbstract.ToCaseAbstractLink, this.caseInst);
            insert.add(CIAccounting.Account2CaseAbstract.FromAccountAbstractLink, this.accInst);
            insert.add(CIAccounting.Account2CaseAbstract.Denominator, this.a2cDenum);
            insert.add(CIAccounting.Account2CaseAbstract.Numerator, this.a2cNum);

            final List<Account2CaseConfig> configs = new ArrayList<>();
            if (this.a2cDefault) {
                configs.add(Account2CaseConfig.DEFAULTSELECTED);
            }
            if (this.a2cLabel) {
                configs.add(Account2CaseConfig.APPLYLABEL);
            }
            if (this.a2cEvalRel) {
                configs.add(Account2CaseConfig.EVALRELATION);
            }
            if (configs.isEmpty()) {
                insert.add(CIAccounting.Account2CaseAbstract.Config, (Object) null);
            } else {
                insert.add(CIAccounting.Account2CaseAbstract.Config, configs.toArray());
            }

            if (this.a2cClass != null) {
                insert.add(CIAccounting.Account2CaseAbstract.LinkValue, Classification.get(this.a2cClass).getId());
            }
            if (this.currencyISO != null && !this.currencyISO.isEmpty()) {
                for (final CurrencyInst currencyInst : CurrencyInst.getAvailable()) {
                    if (currencyInst.getISOCode().equals(this.currencyISO)) {
                        insert.add(CIAccounting.Account2CaseAbstract.CurrencyLink, currencyInst.getInstance());
                    }
                }
            }
            insert.execute();
        }

        @Override
        public String toString()
        {
            return ToStringBuilder.reflectionToString(this, ToStringStyle.MULTI_LINE_STYLE);
        }


        /**
         * Getter method for the instance variable {@link #caseInst}.
         *
         * @return value of instance variable {@link #caseInst}
         */
        public Instance getCaseInst()
        {
            return this.caseInst;
        }


        /**
         * Setter method for instance variable {@link #caseInst}.
         *
         * @param _caseInst value for instance variable {@link #caseInst}
         */
        public void setCaseInst(final Instance _caseInst)
        {
            this.caseInst = _caseInst;
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
        private final String key;

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
         * List of Numerator numbers for the account connection.
         */
        private final List<BigDecimal> lstNumerator;

        /**
         * List of Denominator numbers for the account connection.
         */
        private final List<BigDecimal> lstDenominator;

        /**
         * @param _period period this account belong to
         * @param _colName2Index mapping o column name to index
         * @param _row actual row
         * @param _relAccountColumns map of related (linked) Account  columns
         * @param _accounts mapping of accounts
         * @throws EFapsException on error
         */
        public ImportAccount(final Instance _period,
                             final Map<String, Integer> _colName2Index,
                             final String[] _row,
                             final Map<String, List<String>> _relAccountColumns,
                             final Map<String, ImportAccount> _accounts)
            throws EFapsException
        {
            this.lstTypeConn = new ArrayList<>();
            this.lstTargetConn = new ArrayList<>();
            this.lstNumerator = new ArrayList<>();
            this.lstDenominator = new ArrayList<>();

            this.value = _row[_colName2Index.get(ColumnAccount.VALUE.getKey())].trim().replaceAll("\n", "");
            this.description = _row[_colName2Index.get(ColumnAccount.NAME.getKey())].trim().replaceAll("\n", "");
            final String type = _row[_colName2Index.get(ColumnAccount.TYPE.getKey())].trim().replaceAll("\n", "");
            final boolean summary = "yes".equalsIgnoreCase(_row[_colName2Index.get(ColumnAccount.SUMMARY.getKey())]);
            final String parentTmp = _row[_colName2Index.get(ColumnAccount.PARENT.getKey())];

            this.key = _row[_colName2Index.get(ColumnAccount.KEY.getKey())].trim().replaceAll("\n", "");

            if (_relAccountColumns != null) {
                for (final Entry<String, List<String>> entry : _relAccountColumns.entrySet()) {
                    final String typeConnTmp = _row[_colName2Index.get(ColumnAccount.ACC_REL.getKey()
                                    .replace("]", entry.getKey() + "]"))].trim().replaceAll("\n", "");
                    final String targetConnTmp = _row[_colName2Index.get(ColumnAccount.ACC_TARGET.getKey()
                                    .replace("]", entry.getKey() + "]"))].trim().replaceAll("\n", "");
                    final String numeratorTmp = _row[_colName2Index.get(ColumnAccount.ACC_RELNUM.getKey()
                                    .replace("]", entry.getKey() + "]"))].trim().replaceAll("\n", "");
                    final String denominatorTmp = _row[_colName2Index.get(ColumnAccount.ACC_RELDEN.getKey()
                                    .replace("]", entry.getKey() + "]"))].trim().replaceAll("\n", "");
                    if (typeConnTmp != null && !typeConnTmp.isEmpty()
                                    && targetConnTmp != null && !targetConnTmp.isEmpty()) {
                        this.lstTypeConn.add(Type.get(Import_Base.ACC2ACC.get(typeConnTmp)));
                        this.lstTargetConn.add(targetConnTmp);
                        this.lstNumerator.add(new BigDecimal(numeratorTmp));
                        this.lstDenominator.add(new BigDecimal(denominatorTmp));
                    }
                }
            }

            if (_accounts != null) {
                this.path = _accounts.get(this.key).getPath();
            } else {
                this.path = null;
            }

            this.parent = parentTmp == null ? null : parentTmp.trim().replaceAll("\n", "");

            Update update = null;
            if (Type.get(Import_Base.TYPE2TYPE.get(type)).isKindOf(CIAccounting.AccountAbstract.getType())) {
                final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.AccountAbstract);
                queryBldr.addWhereAttrEqValue(CIAccounting.AccountBaseAbstract.Name, this.value);
                queryBldr.addWhereAttrEqValue(CIAccounting.AccountBaseAbstract.PeriodAbstractLink, _period.getId());
                final InstanceQuery query = queryBldr.getQuery();
                query.execute();
                if (query.next()) {
                    update = new Update(query.getCurrentValue());
                } else {
                    update = new Insert(Import_Base.TYPE2TYPE.get(type));
                }
            } else if (Type.get(Import_Base.TYPE2TYPE.get(type)).isKindOf(CIAccounting.ViewAbstract.getType())) {
                final String[] parts = this.path.split("_");
                final Instance updateInst = validateUpdate(this.value, null, _period, 0, parts);

                if (updateInst != null) {
                    update = new Update(updateInst);
                } else {
                    update = new Insert(Import_Base.TYPE2TYPE.get(type));
                }
            }

            if (Type.get(Import_Base.TYPE2TYPE.get(type)).isKindOf(CIAccounting.AccountAbstract.getType())) {
                update.add("Summary", summary);
            }

            update.add(CIAccounting.AccountBaseAbstract.PeriodAbstractLink, _period.getId());
            update.add(CIAccounting.AccountBaseAbstract.Name, this.value);
            update.add(CIAccounting.AccountBaseAbstract.Description, this.description);
            update.execute();
            this.instance = update.getInstance();
        }

        /**
         * new Constructor for import accounts of the period.
         *
         * @param _colName2Index column name to index mapping
         * @param _row current row
         */
        public ImportAccount(final Map<String, Integer> _colName2Index,
                              final String[] _row)
        {
            this.lstTypeConn = new ArrayList<>();
            this.lstTargetConn = new ArrayList<>();
            this.lstNumerator = new ArrayList<>();
            this.lstDenominator = new ArrayList<>();

            this.value = _row[_colName2Index.get(ColumnAccount.VALUE.getKey())].trim().replaceAll("\n", "");
            this.description = _row[_colName2Index.get(ColumnAccount.NAME.getKey())].trim().replaceAll("\n", "");
            this.key = _row[_colName2Index.get(ColumnAccount.KEY.getKey())].trim().replaceAll("\n", "");

            final String parentTmp = _row[_colName2Index.get(ColumnAccount.PARENT.getKey())];
            this.parent = parentTmp == null ? null : parentTmp.trim().replaceAll("\n", "");
            this.instance = null;
            this.path = this.value;
        }

        /**
         * new Constructor for import accounts of the period.
         * @param _accountIns Instance of the account.
         * @param _parentName name of a parent accounts.
         * @param _name name of account.
         * @param _description description
         */
        public ImportAccount(final Instance _accountIns,
                             final String _parentName,
                             final String _name,
                             final String _description)
        {
            this.instance = _accountIns;
            this.parent = _parentName;
            this.value = _name;
            this.description = _description;
            this.lstTypeConn = null;
            this.lstTargetConn = null;
            this.lstNumerator = null;
            this.lstDenominator = null;
            this.key = null;
            this.path = null;
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
        public String getKey()
        {
            return this.key;
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
         * @return the lstNumerator
         */
        private List<BigDecimal> getLstNumerator()
        {
            return this.lstNumerator;
        }

        /**
         * @return the lstDenominator
         */
        private List<BigDecimal> getLstDenominator()
        {
            return this.lstDenominator;
        }

        /**
         * @return the path
         */
        private String getPath()
        {
            return this.path;
        }

        /**
         * @param _path the path to set
         */
        private void setPath(final String _path)
        {
            this.path = _path;
        }

        /**
         * @param _name
         * @param _id
         * @param _period
         * @param _cont
         * @param _parts
         * @return
         * @throws EFapsException
         */
        private Instance validateUpdate(final String _name,
                                       final Long _id,
                                       final Instance _period,
                                       int _cont,
                                       final String[] _parts) throws EFapsException {
            Instance ret = null;
            boolean ver = false;
            Instance instCur = null;
            final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.ViewAbstract);
            if (_cont == 0) {
                queryBldr.addWhereAttrEqValue(CIAccounting.AccountBaseAbstract.Name, _name);
                queryBldr.addWhereAttrEqValue(CIAccounting.AccountBaseAbstract.PeriodAbstractLink, _period.getId());
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
                if (_cont < _parts.length) {
                    if (parentName.equals(_parts[_cont])) {
                        ret = validateUpdate(parentName, parentId, _period, _cont, _parts);
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
        private final Map<Integer, List<ImportNode>> level2Nodes = new HashMap<>();

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
            insert.add(CIAccounting.ReportAbstract.PeriodLink, _periodInst.getId());
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
                nodes = new ArrayList<>();
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
            final String type = _row[_colName2Index.get(ColumnReport.NODE_TYPE.getKey())].trim()
                            .replaceAll("\n", "");
            final boolean showAllways = !"false".equalsIgnoreCase(_row[_colName2Index
                            .get(ColumnReport.NODE_SHOW.getKey())].trim());
            final boolean showSum = !"false".equalsIgnoreCase(_row[_colName2Index
                            .get(ColumnReport.NODE_SUM.getKey())].trim());
            final String number = _row[_colName2Index.get(ColumnReport.NODE_NUMBER.getKey())].trim()
                            .replaceAll("\n", "");
            final String value = _row[_colName2Index.get(_level)].trim().replaceAll("\n", "");

            final Insert insert = new Insert(Import_Base.TYPE2TYPE.get(type));
            insert.add(CIAccounting.ReportNodeAbstract.ShowAllways, showAllways);
            insert.add(CIAccounting.ReportNodeAbstract.ShowSum, showSum);

            if (CIAccounting.ReportNodeRoot.getType().getUUID().equals(Import_Base.TYPE2TYPE.get(type))) {
                if (_parentInst.isValid()) {
                    insert.add(CIAccounting.ReportNodeRoot.Number, number);
                    insert.add(CIAccounting.ReportNodeRoot.Label, value);
                    insert.add(CIAccounting.ReportNodeRoot.ReportLink, _parentInst);
                    insert.add(CIAccounting.ReportNodeRoot.Position, _position);
                    insert.execute();
                } else {
                    Import_Base.LOG.error("Report Instance does not exist for '{}'- '{}' ", number, value);
                }
            } else if (CIAccounting.ReportNodeTree.getType().getUUID().equals(Import_Base.TYPE2TYPE.get(type))) {
                if (_parentInst.isValid()) {
                    insert.add(CIAccounting.ReportNodeTree.Number, number);
                    insert.add(CIAccounting.ReportNodeTree.Label, value);
                    insert.add(CIAccounting.ReportNodeTree.ParentLink, _parentInst);
                    insert.add(CIAccounting.ReportNodeTree.Position, _position);
                    insert.execute();
                } else {
                    Import_Base.LOG.error("Parent Node Instance does not exist for '{}'- '{}' ", number, value);
                }
            } else if (CIAccounting.ReportNodeAccount.getType().getUUID().equals(Import_Base.TYPE2TYPE.get(type))) {
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
