/*
 * Copyright 2003 - 2012 The eFaps Team
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
import org.efaps.admin.program.esjp.EFapsRevision;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.Insert;
import org.efaps.db.Instance;
import org.efaps.db.PrintQuery;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.common.uiform.Create;
import org.efaps.util.EFapsException;


/**
 * TODO comment!
 *
 * @author The eFaps Team
 * @version $Id$
 */
@EFapsUUID("b446f672-1871-418f-a90a-fd928ec89d8f")
@EFapsRevision("$Rev$")
public abstract class View_Base
{
    /**
     * Create a new View.
     *
     * @param _parameter    Parameter as passed from the eFAPS API
     * @return new Return
     * @throws EFapsException on error
     */
    public Return create(final Parameter _parameter)
        throws EFapsException
    {
        final Instance instance = _parameter.getCallInstance();
        final PrintQuery print = new PrintQuery(instance);
        print.addAttribute(CIAccounting.ViewAbstract.PeriodeAbstractLink);
        print.execute();
        final Long periodeId = print.<Long> getAttribute(CIAccounting.ViewAbstract.PeriodeAbstractLink);

        final Create create = new Create() {

            @Override
            protected void add2basicInsert(final Parameter _parameter,
                                           final Insert _insert)
                throws EFapsException
            {
                _insert.add(CIAccounting.ViewAbstract.PeriodeAbstractLink, periodeId);
            }
        };
        return create.execute(_parameter);
    }
}
