/*
 * Copyright 2003 - 2016 The eFaps Team
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
 */


package org.efaps.esjp.accounting.transaction;

import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Return;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.util.DateTimeUtil;
import org.efaps.util.EFapsException;


/**
 * TODO comment!
 *
 * @author The eFaps Team
 */
@EFapsUUID("28e080be-74e1-4ed8-a632-763404fa13ba")
@EFapsApplication("eFapsApp-Accounting")
public abstract class Edit_Base
    extends Transaction
{
    /**
     * @param _parameter Parameter as passed by the eFaps API
     * @return new empty Return
     * @throws EFapsException on error
     */
    public Return execute(final Parameter _parameter)
        throws EFapsException
    {
        final TransInfo transInfo = new TransInfo();
        transInfo.setInstance(_parameter.getInstance())
            .setDescription(_parameter.getParameterValue("description"))
            .setDate(DateTimeUtil.translateFromUI(_parameter.getParameterValue("date")));

        final Create create = new Create();
        create.analysePositionsFromUI(_parameter, transInfo, "Debit", null, false);
        create.analysePositionsFromUI(_parameter, transInfo, "Credit", null, false);
        transInfo.update(_parameter);
        return new Return();
    }
}
