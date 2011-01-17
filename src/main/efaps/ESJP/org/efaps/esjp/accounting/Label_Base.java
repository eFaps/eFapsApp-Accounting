/*
 * Copyright 2003 - 2010 The eFaps Team
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

import java.util.UUID;

import org.efaps.admin.common.SystemConfiguration;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Return;
import org.efaps.admin.event.Return.ReturnValues;
import org.efaps.admin.program.esjp.EFapsRevision;
import org.efaps.admin.program.esjp.EFapsUUID;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 * @version $Id$
 */
@EFapsUUID("35d9cfa6-4231-42bf-9018-246ace6fe095")
@EFapsRevision("$Rev$")
public abstract class Label_Base
{
    /**
     * Check access to label.
     *
     * @param _parameter Paremeter as passed from the eFaPS API
     * @return Return
     */
    public Return accessCheck4Label(final Parameter _parameter)
    {
        final Return ret = new Return();
        // Accounting-Configuration
        final SystemConfiguration sysconf = SystemConfiguration.get(
                        UUID.fromString("ca0a1df1-2211-45d9-97c8-07af6636a9b9"));
        if (!sysconf.getAttributeValueAsBoolean("DeactivateLabel")) {
            ret.put(ReturnValues.TRUE, true);
        }
        return ret;
    }
}
