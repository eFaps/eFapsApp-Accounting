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

package org.efaps.esjp.accounting.transaction;

import java.math.BigDecimal;

import org.efaps.admin.program.esjp.EFapsRevision;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.Instance;

/**
 * This class must be replaced for customization, therefore it is left empty.
 * Functional description can be found in the related "<code>_base</code>"
 * class.
 *
 * @author The eFaps Team
 * @version $Id$
 */
@EFapsUUID("dc6246d8-285e-45f0-a984-2c9a653633b9")
@EFapsRevision("$Rev$")
public class AccountInfo
    extends AccountInfo_Base
{
    /**
     *
     */
    public AccountInfo()
    {
        super();
    }

    /**
     * @param _instance Instance.
     */
    public AccountInfo(final Instance _instance)
    {
        super(_instance);
    }

    /**
     * new TargetAccount.
     *
     * @param _instance Instance.
     * @param _amount amount.
     */
    public AccountInfo(final Instance _instance,
                       final BigDecimal _amount)
    {
        super(_instance, _amount);
    }
}
