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
        _exporter.addColumns(new FrmtColumn(ColumnAcccount.KEY.getKey(), 5));
        _exporter.addColumns(new FrmtColumn(ColumnAcccount.VALUE.getKey(), 12));
        _exporter.addColumns(new FrmtColumn(ColumnAcccount.PARENT.getKey()).setMaxWidth(12));
        _exporter.addColumns(new FrmtColumn(ColumnAcccount.NAME.getKey(), 80));
        _exporter.addColumns(new FrmtColumn(ColumnAcccount.TYPE.getKey(), 25));
        _exporter.addColumns(new FrmtColumn(ColumnAcccount.SUMMARY.getKey(), 5));
    }

    protected void addAdditionalGroupCols(final Parameter _parameter,
                                          final DataExporter _exporter,
                                          final Integer _order)
    {
        _exporter.addColumns(new FrmtColumn(ColumnAcccount.ACC_REL.getKey().replace("]", _order + "]"), 30));
        _exporter.addColumns(new FrmtColumn(ColumnAcccount.ACC_TARGET.getKey().replace("]", _order + "]"), 12));
        _exporter.addColumns(new FrmtColumn(ColumnAcccount.ACC_RELNUM.getKey().replace("]", _order + "]"), 3));
        _exporter.addColumns(new FrmtColumn(ColumnAcccount.ACC_RELDEN.getKey().replace("]", _order + "]"), 3));
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
        final SelectBuilder selParent = new SelectBuilder().linkto(CIAccounting.AccountAbstract.ParentLink)
                        .attribute(CIAccounting.AccountAbstract.Name);
        multi.addSelect(selParent);
        multi.setEnforceSorted(true);
        multi.execute();
        while (multi.next()) {
            final long id = multi.getCurrentInstance().getId();
            final String name = multi.<String>getAttribute(CIAccounting.AccountAbstract.Name);
            final String description = multi.<String>getAttribute(CIAccounting.AccountAbstract.Description);
            final Boolean summary = multi.<Boolean>getAttribute(CIAccounting.AccountAbstract.Summary);
            final String parentName = multi.<String>getSelect(selParent);
            final Type type = multi.getCurrentInstance().getType();

            _exporter.addRow(id, name, parentName, description, ExportAccount_Base.TYPE2TYPE.get(type),
                            summary ? "YES" : "NO");

            final QueryBuilder queryBldr2 = new QueryBuilder(CIAccounting.Account2AccountAbstract);
            queryBldr2.addWhereAttrEqValue(CIAccounting.Account2AccountAbstract.FromAccountLink,
                            multi.getCurrentInstance());
            final MultiPrintQuery multi2 = queryBldr2.getPrint();
            multi2.addAttribute(CIAccounting.Account2AccountAbstract.Numerator,
                            CIAccounting.Account2AccountAbstract.Denominator);
            final SelectBuilder selAccountName = new SelectBuilder()
                            .linkto(CIAccounting.Account2AccountAbstract.ToAccountLink)
                            .attribute(CIAccounting.AccountAbstract.Name);
            multi2.addSelect(selAccountName);
            multi2.execute();
            final int cont = 0;
            while (multi2.next()) {
                if (cont > addGrpCols) {
                    addAdditionalGroupCols(_parameter, _exporter, cont);
                    addGrpCols++;
                }
                final Type accRel = multi2.getCurrentInstance().getType();
                final String accTarget = multi2.<String>getSelect(selAccountName);
                final Integer accNum = multi2.<Integer>getAttribute(CIAccounting.Account2AccountAbstract.Numerator);
                final Integer accDen = multi2.<Integer>getAttribute(CIAccounting.Account2AccountAbstract.Denominator);

                _exporter.addRow(ExportAccount_Base.TYPE2TYPE.get(accRel), accTarget, accNum, accDen);
            }
        }
    }
}
