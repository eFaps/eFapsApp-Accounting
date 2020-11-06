/*
 * Copyright 2003 - 2011 The eFaps Team
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

import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Return;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.CachedPrintQuery;
import org.efaps.db.Instance;
import org.efaps.db.PrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.eql.builder.Where;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.ui.structurbrowser.StandartStructurBrowser;
import org.efaps.util.EFapsException;

/**
 * TODO description!
 *
 * @author The eFasp Team
 *
 */
@EFapsUUID("21b4e990-00b8-44bb-9896-80719fcf8c81")
@EFapsApplication("eFapsApp-Accounting")
public abstract class StructurBrowser_Base
    extends StandartStructurBrowser
{
    /**
     * Only roots are wanted, that means the the ParentLink must be null.
     *
     * @param _parameter Parameter as passed from the eFaps API
     * @param _queryBldr QueryBuilder the criteria will be added to
     * @throws EFapsException on error
     */
    @Override
    protected void addCriteria(final Parameter _parameter,
                               final QueryBuilder _queryBldr)
        throws EFapsException
    {
        _queryBldr.addWhereAttrIsNull(CIAccounting.AccountAbstract.ParentLink);
        if (_parameter.getInstance().getType().isKindOf(CIAccounting.SubPeriod.getType())) {
            final PrintQuery print = new CachedPrintQuery(_parameter.getInstance(), SubPeriod_Base.CACHEKEY);
            final SelectBuilder selPeriodInst = SelectBuilder.get().linkto(CIAccounting.SubPeriod.PeriodLink)
                            .instance();
            print.addSelect(selPeriodInst);
            print.execute();
            final Instance instance = print.<Instance>getSelect(selPeriodInst);
            _queryBldr.addWhereAttrEqValue(CIAccounting.AccountAbstract.PeriodAbstractLink, instance);
        }
    }

    @Override
    protected void add2MainQuery(final Parameter _parameter, final Where _where)
        throws EFapsException
    {
        _where.and();
        _where.attribute(CIAccounting.AccountAbstract.ParentLink).isNull();
        if (_parameter.getInstance().getType().isKindOf(CIAccounting.SubPeriod.getType())) {
            final PrintQuery print = new CachedPrintQuery(_parameter.getInstance(), SubPeriod_Base.CACHEKEY);
            final SelectBuilder selPeriodInst = SelectBuilder.get().linkto(CIAccounting.SubPeriod.PeriodLink)
                            .instance();
            print.addSelect(selPeriodInst);
            print.execute();
            final Instance instance = print.<Instance>getSelect(selPeriodInst);
            _where.and();
            _where.attribute(CIAccounting.AccountAbstract.PeriodAbstractLink).eq(instance);
        }
    }


    /**
     * Method is called from the StructurBrowser in edit mode before rendering
     * the columns for row to be able to hide the columns for different rows by
     * setting the cell model to hide.
     * In this implementation all columns are always shown.
     * @param _parameter Paraemter as passed from the eFasp API
     * @return empty Return;
     */
    @Override
    protected Return checkHideColumn4Row(final Parameter _parameter)
    {
        return new Return();
    }
}
