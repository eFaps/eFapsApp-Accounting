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

package org.efaps.esjp.accounting;

import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Parameter.ParameterValues;
import org.efaps.admin.event.Return;
import org.efaps.admin.event.Return.ReturnValues;
import org.efaps.admin.program.esjp.EFapsRevision;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.CachedPrintQuery;
import org.efaps.db.Context;
import org.efaps.db.Instance;
import org.efaps.db.PrintQuery;
import org.efaps.db.SelectBuilder;
import org.efaps.esjp.accounting.transaction.Transaction_Base;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.util.EFapsException;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 * @version $Id$
 */
@EFapsUUID("948a690d-9934-470a-b8fb-b1e4d212010a")
@EFapsRevision("$Rev$")
public abstract class SubPeriod_Base
{

    /**
     * CacheKey for SubPeriods.
     */
    public static final String CACHEKEY = SubPeriod.class.getName() + ".CacheKey";

    public Return setPeriodInstance(final Parameter _parameter)
        throws EFapsException
    {
        final Instance subPeriodInst;
        if (_parameter.getInstance() != null) {
            subPeriodInst = _parameter.getInstance();
        } else {
            subPeriodInst = Instance.get((String) _parameter.get(ParameterValues.OTHERS));
        }
        final PrintQuery print = new CachedPrintQuery(subPeriodInst, SubPeriod_Base.CACHEKEY);
        final SelectBuilder selPeriodInst = SelectBuilder.get().linkto(CIAccounting.SubPeriod.PeriodLink)
                        .instance();
        print.addSelect(selPeriodInst);
        print.execute();
        final Instance periodInst = print.<Instance>getSelect(selPeriodInst);

        if (periodInst != null && periodInst.isValid()) {
            Context.getThreadContext().setSessionAttribute(Transaction_Base.PERIODE_SESSIONKEY, periodInst);
        }
        final Return ret = new Return();
        ret.put(ReturnValues.VALUES, subPeriodInst);
        return ret;
    }

    public Return getDocuments(final Parameter _parameter)
        throws EFapsException
    {
        return new Periode().getDocuments(_parameter);
    }
}
