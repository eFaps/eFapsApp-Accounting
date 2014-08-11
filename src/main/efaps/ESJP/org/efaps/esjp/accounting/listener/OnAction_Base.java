/*
 * Copyright 2003 - 2014 The eFaps Team
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


package org.efaps.esjp.accounting.listener;

import org.efaps.admin.event.Parameter;
import org.efaps.admin.program.esjp.EFapsRevision;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.Instance;
import org.efaps.esjp.accounting.transaction.Action;
import org.efaps.esjp.ci.CISales;
import org.efaps.esjp.common.listener.ITypedClass;
import org.efaps.esjp.erp.listener.IOnAction;
import org.efaps.util.EFapsException;

/**
 *
 * @author The eFaps Team
 * @version $Id$
 */
@EFapsUUID("901fb0a0-a359-4ef0-8c11-3aa31022c459")
@EFapsRevision("$Rev$")
public abstract class OnAction_Base
    implements IOnAction
{
    /**
     * Called after the creation/insert of a new Document with the values
     * already set and the instance valid.
     *
     * @param _typeClass    typed class instance
     * @param _parameter    Parameter as passed by the eFaps API
     * @param _actionInst   instance created
     * @throws EFapsException on error
     */
    @Override
    public void afterAssign(final ITypedClass _typeClass,
                            final Parameter _parameter,
                            final Instance _actionRelInst)
        throws EFapsException
    {
        if (CISales.IncomingInvoice.equals(_typeClass.getCIType())) {
            final Action action = new Action();
            action.create4External(_parameter, _actionRelInst);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getWeight()
    {
        return 0;
    }
}
