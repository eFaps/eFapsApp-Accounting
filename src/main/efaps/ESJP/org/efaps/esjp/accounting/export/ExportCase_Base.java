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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.efaps.admin.datamodel.Type;
import org.efaps.admin.event.Parameter;
import org.efaps.ci.CIAdminDataModel;
import org.efaps.dataexporter.DataExporter;
import org.efaps.db.AttributeQuery;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.PrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.esjp.accounting.util.Accounting.Account2CaseConfig;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.data.columns.export.FrmtColumn;
import org.efaps.esjp.erp.CurrencyInst;
import org.efaps.util.EFapsException;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 * @version $Id: $
 */
public abstract class ExportCase_Base
    extends AbstractExport
{
    /**
    *
    */
    public static final String PREFIX = "ExpDef";

    /**
    *
    */
    public static final String SUFFIX = "csv";

    @Override
    protected String getPrefix(final Parameter _parameter)
        throws EFapsException
    {
        return ExportCase_Base.PREFIX;
    }

    @Override
    protected String getSuffix(final Parameter _parameter)
        throws EFapsException
    {
        return ExportCase_Base.SUFFIX;
    }

    @Override
    public void addColumnDefinition(final Parameter _parameter,
                                    final DataExporter _exporter)
    {
        _exporter.addColumns(new FrmtColumn(ColumnCase.CASETYPE.getKey(), 30));
        _exporter.addColumns(new FrmtColumn(ColumnCase.CASENAME.getKey(), 40));
        _exporter.addColumns(new FrmtColumn(ColumnCase.CASEDESC.getKey()).setMaxWidth(256));
        _exporter.addColumns(new FrmtColumn(ColumnCase.CASELABEL.getKey()).setMaxWidth(512));
        _exporter.addColumns(new FrmtColumn(ColumnCase.CASEISCROSS.getKey(), 5));
        _exporter.addColumns(new FrmtColumn(ColumnCase.CASECONFIG.getKey(), 10));
        _exporter.addColumns(new FrmtColumn(ColumnCase.A2CTYPE.getKey(), 30));
        _exporter.addColumns(new FrmtColumn(ColumnCase.A2CACC.getKey(), 12));
        _exporter.addColumns(new FrmtColumn(ColumnCase.A2CCLA.getKey(), 80));
        _exporter.addColumns(new FrmtColumn(ColumnCase.A2CNUM.getKey(), 3));
        _exporter.addColumns(new FrmtColumn(ColumnCase.A2CDENUM.getKey(), 3));
        _exporter.addColumns(new FrmtColumn(ColumnCase.A2CDEFAULT.getKey(), 5));
        _exporter.addColumns(new FrmtColumn(ColumnCase.A2CAPPLYLABEL.getKey(), 5));
        _exporter.addColumns(new FrmtColumn(ColumnCase.A2CEVALRELATION.getKey(), 5));
        _exporter.addColumns(new FrmtColumn(ColumnCase.A2CCURRENCY.getKey(), 4));
    }

    @Override
    public void buildDataSource(final Parameter _parameter,
                                final DataExporter _exporter)
        throws EFapsException
    {
        final QueryBuilder attrQueryBldr = new QueryBuilder(CIAccounting.CaseAbstract);
        attrQueryBldr.addWhereAttrEqValue(CIAccounting.CaseAbstract.PeriodAbstractLink, _parameter.getInstance());
        final AttributeQuery attrQuery = attrQueryBldr.getAttributeQuery(CIAccounting.CaseAbstract.ID);

        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.Account2CaseAbstract);
        queryBldr.addWhereAttrInQuery(CIAccounting.Account2CaseAbstract.ToCaseAbstractLink, attrQuery);
        final MultiPrintQuery multi = queryBldr.getPrint();
        multi.addAttribute(CIAccounting.Account2CaseAbstract.Numerator,
                        CIAccounting.Account2CaseAbstract.Denominator,
                        CIAccounting.Account2CaseAbstract.Config,
                        CIAccounting.Account2CaseAbstract.CurrencyLink);
        final SelectBuilder selCaseName = new SelectBuilder()
                        .linkto(CIAccounting.Account2CaseAbstract.ToCaseAbstractLink)
                        .attribute(CIAccounting.CaseAbstract.Name);
        final SelectBuilder selCaseDesc = new SelectBuilder()
                        .linkto(CIAccounting.Account2CaseAbstract.ToCaseAbstractLink)
                        .attribute(CIAccounting.CaseAbstract.Description);
        final SelectBuilder selCaseLabel = new SelectBuilder()
                        .linkto(CIAccounting.Account2CaseAbstract.ToCaseAbstractLink)
                        .attribute(CIAccounting.CaseAbstract.Label);
        final SelectBuilder selCaseIsCross = new SelectBuilder()
                        .linkto(CIAccounting.Account2CaseAbstract.ToCaseAbstractLink)
                        .attribute(CIAccounting.CaseAbstract.IsCross);
        final SelectBuilder selCaseConfig = new SelectBuilder()
                        .linkto(CIAccounting.Account2CaseAbstract.ToCaseAbstractLink)
                         .attribute(CIAccounting.CaseAbstract.SummarizeConfig);
        final SelectBuilder selCaseType = new SelectBuilder()
                        .linkto(CIAccounting.Account2CaseAbstract.ToCaseAbstractLink).type();
        final SelectBuilder selAccountName = new SelectBuilder()
                        .linkto(CIAccounting.Account2CaseAbstract.FromAccountAbstractLink)
                        .attribute(CIAccounting.AccountAbstract.Name);
        multi.addSelect(selAccountName, selCaseDesc, selCaseLabel, selCaseName, selCaseType, selCaseConfig,
                        selCaseIsCross);
        multi.execute();
        final List<Map<String, Object>> lstCols = new ArrayList<Map<String, Object>>();
        while (multi.next()) {
            final Map<String, Object> row = new HashMap<String, Object>();
            final Type caseType = multi.<Type>getSelect(selCaseType);
            final String caseName = multi.<String>getSelect(selCaseName);
            final String caseDesc = multi.<String>getSelect(selCaseDesc);
            final String caseLabel = multi.<String>getSelect(selCaseLabel);

            final Boolean caseIsCross = multi.<Boolean>getSelect(selCaseIsCross);
            final Type acc2CaseType = multi.getCurrentInstance().getType();
            final String acc2CaseAcc = multi.<String>getSelect(selAccountName);
            final Long currencyId = multi.getAttribute(CIAccounting.Account2CaseAbstract.CurrencyLink);
            final String currency;
            if (currencyId == null) {
                currency = "";
            } else {
                currency = CurrencyInst.get(currencyId).getISOCode();
            }
            final Object config =   multi.getSelect(selCaseConfig);

            if (acc2CaseType.isKindOf(CIAccounting.Account2CaseCredit4Classification.getType())
                            || acc2CaseType.isKindOf(CIAccounting.Account2CaseDebit4Classification.getType())) {
                final PrintQuery print = new PrintQuery(multi.getCurrentInstance());
                final SelectBuilder selClassName = new SelectBuilder()
                                .linkto(CIAccounting.Account2CaseCredit4Classification.ClassificationLink)
                                .attribute(CIAdminDataModel.Type.Name);
                print.addSelect(selClassName);
                print.execute();
                final String className = print.<String>getSelect(selClassName);

                row.put(ColumnCase.A2CCLA.getKey(), className);
            }

            final Integer acc2CaseNum = multi.<Integer>getAttribute(CIAccounting.Account2CaseAbstract.Numerator);
            final Integer acc2CaseDen = multi.<Integer>getAttribute(CIAccounting.Account2CaseAbstract.Denominator);
            final List<Account2CaseConfig> configs = multi.getAttribute(CIAccounting.Account2CaseAbstract.Config);
            // classRel or default selected will be added
            final boolean acc2CaseDef = configs != null && configs.contains(Account2CaseConfig.DEFAULTSELECTED);
            final boolean acc2CaseLabel = configs != null && configs.contains(Account2CaseConfig.APPLYLABEL);
            final boolean acc2CaseEvalRelation = configs != null && configs.contains(Account2CaseConfig.EVALRELATION);

            row.put(ColumnCase.CASETYPE.getKey(), caseType.getUUID());
            row.put(ColumnCase.CASENAME.getKey(), caseName);
            row.put(ColumnCase.CASEDESC.getKey(), caseDesc);
            row.put(ColumnCase.CASELABEL.getKey(), caseLabel);
            row.put(ColumnCase.CASEISCROSS.getKey(), caseIsCross);
            row.put(ColumnCase.CASECONFIG.getKey(), config);
            row.put(ColumnCase.A2CTYPE.getKey(), acc2CaseType.getUUID());
            row.put(ColumnCase.A2CACC.getKey(), acc2CaseAcc);
            row.put(ColumnCase.A2CNUM.getKey(), acc2CaseNum);
            row.put(ColumnCase.A2CDENUM.getKey(), acc2CaseDen);
            row.put(ColumnCase.A2CDEFAULT.getKey(), acc2CaseDef);
            row.put(ColumnCase.A2CAPPLYLABEL.getKey(), acc2CaseLabel);
            row.put(ColumnCase.A2CEVALRELATION.getKey(), acc2CaseEvalRelation);
            row.put(ColumnCase.A2CCURRENCY.getKey(), currency);
            lstCols.add(row);
        }

        Collections.sort(lstCols, new Comparator<Map<String, Object>>()
        {
            @Override
            public int compare(final Map<String, Object> _o1,
                               final Map<String, Object> _o2)
            {
                final int ret;
                final String caseType1 = AbstractExport_Base.TYPE2TYPE.get(_o1.get(ColumnCase.CASETYPE.getKey()));
                final String caseType2 = AbstractExport_Base.TYPE2TYPE.get(_o2.get(ColumnCase.CASETYPE.getKey()));

                if (caseType1.equals(caseType2)) {
                    final String caseName1 = (String) _o1.get(ColumnCase.CASENAME.getKey());
                    final String caseName2 = (String) _o2.get(ColumnCase.CASENAME.getKey());
                    ret = caseName1.compareTo(caseName2);
                } else {
                    ret = caseType1.compareTo(caseType2);
                }

                return ret;
            }
        });

        for (final Map<String, Object> map : lstCols) {
            _exporter.addRow(AbstractExport_Base.TYPE2TYPE.get(map.get(ColumnCase.CASETYPE.getKey())),
                            map.get(ColumnCase.CASENAME.getKey()),
                            map.get(ColumnCase.CASEDESC.getKey()),
                            map.get(ColumnCase.CASELABEL.getKey()),
                            map.get(ColumnCase.CASEISCROSS.getKey()),
                            map.get(ColumnCase.CASECONFIG.getKey()),
                            AbstractExport_Base.TYPE2TYPE.get(map.get(ColumnCase.A2CTYPE.getKey())),
                            map.get(ColumnCase.A2CACC.getKey()),
                            map.get(ColumnCase.A2CCLA.getKey()),
                            map.get(ColumnCase.A2CNUM.getKey()),
                            map.get(ColumnCase.A2CDENUM.getKey()),
                            map.get(ColumnCase.A2CDEFAULT.getKey()),
                            map.get(ColumnCase.A2CAPPLYLABEL.getKey()),
                            map.get(ColumnCase.A2CEVALRELATION.getKey()),
                            map.get(ColumnCase.A2CCURRENCY.getKey()));
        }
    }
}
