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

package org.efaps.esjp.accounting;

import org.efaps.admin.event.Parameter;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.Instance;
import org.efaps.esjp.erp.CurrencyInst;
import org.efaps.util.EFapsException;

/**
 * This class must be replaced for customization, therefore it is left empty.
 * Functional description can be found in the related "<code>_base</code>"
 * class.
 *
 * @author The eFaps Team
 */
@EFapsUUID("1c1f748a-9724-4f22-bf15-e6f9cb0a8c61")
@EFapsApplication("eFapsApp-Accounting")
public class Period
    extends Period_Base
{
    /**
     * CacheKey for Periods.
     */
    public static final String CACHEKEY = Period_Base.CACHEKEY;

    /**
     * RequestKey for current Periods.
     */
    public static final String REQKEY4CUR = Period_Base.REQKEY4CUR;

    /**
     * Eval current.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return the instance
     * @throws EFapsException on error
     */
    public static Instance evalCurrent(final Parameter _parameter)
        throws EFapsException
    {
        return Period_Base.evalCurrent(_parameter);
    }

    /**
     * Eval current currency.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return the currency inst
     * @throws EFapsException on error
     */
    public static CurrencyInst evalCurrentCurrency(final Parameter _parameter)
        throws EFapsException
    {
        return Period_Base.evalCurrentCurrency(_parameter);
    }
}
