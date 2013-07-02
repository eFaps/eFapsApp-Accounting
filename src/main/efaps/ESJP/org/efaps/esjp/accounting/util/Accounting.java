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
 * Revision:        $Rev: 9442 $
 * Last Changed:    $Date: 2013-05-16 18:05:46 -0500 (jue, 16 may 2013) $
 * Last Changed By: $Author: jan@moxter.net $
 */

package org.efaps.esjp.accounting.util;

import java.util.UUID;

import org.efaps.admin.common.SystemConfiguration;
import org.efaps.admin.program.esjp.EFapsRevision;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.util.cache.CacheReloadException;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 * @version $Id: Sales.java 9442 2013-05-16 23:05:46Z jan@moxter.net $
 */
@EFapsUUID("70a6a397-b8ef-40c5-853e-cff331bc79bb")
@EFapsRevision("$Rev: 9442 $")
public final class Accounting
{
    /**
     * Singelton.
     */
    private Accounting()
    {
    }

    /**
     * @return the SystemConfigruation for Accounting
     * @throws CacheReloadException on error
     */
    public static SystemConfiguration getSysConfig()
        throws CacheReloadException
    {
        // Accounting-Configuration
        return SystemConfiguration.get(UUID.fromString("ca0a1df1-2211-45d9-97c8-07af6636a9b9"));
    }
}