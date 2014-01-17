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
import java.util.List;

import org.efaps.admin.datamodel.Type;
import org.efaps.admin.event.Parameter;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.data.columns.export.FrmtColumn;
import org.efaps.util.EFapsException;

import com.brsanthu.dataexporter.DataExporter;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 * @version $Id: $
 */
public abstract class ExportAccount_Base
    extends AbstractExport
{
    /**
     *
     */
    public static final String PREFIX = "ExpAcc";

    /**
     *
     */
    public static final String SUFFIX = "csv";

    @Override
    protected String getPrefix(final Parameter _parameter)
        throws EFapsException
    {
        return ExportAccount_Base.PREFIX;
    }

    @Override
    protected String getSuffix(final Parameter _parameter)
        throws EFapsException
    {
        return ExportAccount_Base.SUFFIX;
    }


    @Override
    public void addColumnDefinition(final Parameter _parameter,
                                    final DataExporter _exporter)
    {
        _exporter.addColumns(new FrmtColumn(ColumnAccount.KEY.getKey(), 5));
        _exporter.addColumns(new FrmtColumn(ColumnAccount.VALUE.getKey(), 12));
        _exporter.addColumns(new FrmtColumn(ColumnAccount.PARENT.getKey()).setMaxWidth(12));
        _exporter.addColumns(new FrmtColumn(ColumnAccount.NAME.getKey(), 80));
        _exporter.addColumns(new FrmtColumn(ColumnAccount.TYPE.getKey(), 25));
        _exporter.addColumns(new FrmtColumn(ColumnAccount.SUMMARY.getKey(), 5));
    }

    protected void addAdditionalGroupCols(final Parameter _parameter,
                                          final DataExporter _exporter,
                                          final Integer _order)
    {
        _exporter.addColumns(new FrmtColumn(ColumnAccount.ACC_REL.getKey().replace("]", _order + "]"), 30));
        _exporter.addColumns(new FrmtColumn(ColumnAccount.ACC_TARGET.getKey().replace("]", _order + "]"), 12));
        _exporter.addColumns(new FrmtColumn(ColumnAccount.ACC_RELNUM.getKey().replace("]", _order + "]"), 3));
        _exporter.addColumns(new FrmtColumn(ColumnAccount.ACC_RELDEN.getKey().replace("]", _order + "]"), 3));
    }

    @Override
    public void buildDataSource(final Parameter _parameter,
                                final DataExporter _exporter)
        throws EFapsException
    {
        int addGrpCols = 0;
        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.AccountAbstract);
        queryBldr.addWhereAttrEqValue(CIAccounting.AccountAbstract.PeriodeAbstractLink, _parameter.getInstance());
        queryBldr.addOrderByAttributeAsc(CIAccounting.AccountAbstract.ID);
        final MultiPrintQuery multi = queryBldr.getPrint();
        multi.addAttribute(CIAccounting.AccountAbstract.Name,
                        CIAccounting.AccountAbstract.Description,
                        CIAccounting.AccountAbstract.Summary);
        final SelectBuilder selParentId = new SelectBuilder().linkto(CIAccounting.AccountAbstract.ParentLink).id();
        multi.addSelect(selParentId);
        multi.setEnforceSorted(true);
        multi.execute();
        final List<List<Object>> lstRowsData = new ArrayList<List<Object>>();
        while (multi.next()) {
            final List<Object> lstColData = new ArrayList<Object>();
            final long id = multi.getCurrentInstance().getId();
            final String name = multi.<String>getAttribute(CIAccounting.AccountAbstract.Name);
            final String description = multi.<String>getAttribute(CIAccounting.AccountAbstract.Description);
            final Boolean summary = multi.<Boolean>getAttribute(CIAccounting.AccountAbstract.Summary);
            final Long parentId = multi.<Long>getSelect(selParentId);
            final Type type = multi.getCurrentInstance().getType();

            lstColData.add(id);
            lstColData.add(name);
            lstColData.add(parentId);
            lstColData.add(description);
            lstColData.add(AbstractExport_Base.TYPE2TYPE.get(type.getUUID()));
            lstColData.add(summary ? "YES" : "NO");

            final QueryBuilder queryBldr2 = new QueryBuilder(CIAccounting.Account2AccountAbstract);
            queryBldr2.addWhereAttrEqValue(CIAccounting.Account2AccountAbstract.FromAccountLink,
                            multi.getCurrentInstance());
            final MultiPrintQuery multi2 = queryBldr2.getPrint();
            multi2.addAttribute(CIAccounting.Account2AccountAbstract.Numerator,
                            CIAccounting.Account2AccountAbstract.Denominator);
            final SelectBuilder selAccountId = new SelectBuilder()
                            .linkto(CIAccounting.Account2AccountAbstract.ToAccountLink).id();
            multi2.addSelect(selAccountId);
            multi2.execute();
            int cont = 1;
            while (multi2.next()) {
                if (cont > addGrpCols) {
                    addAdditionalGroupCols(_parameter, _exporter, cont - 1);
                    addGrpCols++;
                }
                final Type accRel = multi2.getCurrentInstance().getType();
                final Long accTarget = multi2.<Long>getSelect(selAccountId);
                final Integer accNum = multi2.<Integer>getAttribute(CIAccounting.Account2AccountAbstract.Numerator);
                final Integer accDen = multi2.<Integer>getAttribute(CIAccounting.Account2AccountAbstract.Denominator);

                lstColData.add(AbstractExport_Base.TYPE2TYPE.get(accRel.getUUID()));
                lstColData.add(accTarget);
                lstColData.add(accNum);
                lstColData.add(accDen);
                cont++;
            }

            lstRowsData.add(lstColData);
        }
        for (final List<Object> lstCols : lstRowsData) {
            _exporter.addRow(lstCols.toArray());
        }
    }
}
