/*
 * Copyright © 2003 - 2024 The eFaps Team (-)
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
 */
package org.efaps.esjp.accounting.transaction;

import java.math.BigDecimal;

import org.efaps.admin.event.Parameter;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.Instance;
import org.efaps.util.EFapsException;

/**
 * This class must be replaced for customization, therefore it is left empty.
 * Functional description can be found in the related "<code>_base</code>"
 * class.
 */
@EFapsUUID("dc6246d8-285e-45f0-a984-2c9a653633b9")
@EFapsApplication("eFapsApp-Accounting")
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

    /**
     * Gets the 4 config.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @param _key the key
     * @return the 4 config
     * @throws EFapsException on error
     */
    public static AccountInfo get4Config(final Parameter _parameter,
                                         final String _key)
        throws EFapsException
    {
        return AccountInfo_Base.get4Config(_parameter, _key);
    }
}
