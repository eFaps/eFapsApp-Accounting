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
package org.efaps.esjp.accounting;

import java.util.List;

import org.efaps.admin.event.Parameter;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.Instance;
import org.efaps.util.EFapsException;

/**
 * This class must be replaced for customization, therefore it is left empty.
 * Functional description can be found in the related "<code>_base</code>"
 * class.
 *
 * @author The eFaps Team
 */
@EFapsUUID("2e44431b-f2fe-45ed-acd6-4d86c59cbeb2")
@EFapsApplication("eFapsApp-Accounting")
public class Account2CaseInfo
    extends Account2CaseInfo_Base
{

    /**
     * Gets the standards.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @param _caseInst the case inst
     * @return the standards
     * @throws EFapsException on error
     */
    public static List<Account2CaseInfo> getStandards(final Parameter _parameter,
                                                      final Instance _caseInst)
        throws EFapsException
    {
        return Account2CaseInfo_Base.getStandards(_parameter, _caseInst);
    }

    /**
     * Gets the 4 product.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @param _caseInst the case inst
     * @param _productInstance the product instance
     * @return the 4 product
     * @throws EFapsException on error
     */
    public static Account2CaseInfo get4Product(final Parameter _parameter,
                                               final Instance _caseInst,
                                               final Instance _productInstance)
        throws EFapsException
    {
        return Account2CaseInfo_Base.get4Product(_parameter, _caseInst, _productInstance);
    }

    /**
     * Gets the account two case info.
     *
     * @param _acc2CaseInfoInstance the acc 2 case info instance
     * @return the account two case info
     * @throws EFapsException on error
     */
    public static Account2CaseInfo getAccount2CaseInfo(final Instance _acc2CaseInfoInstance)
        throws EFapsException
    {
        return Account2CaseInfo_Base.getAccount2CaseInfo(_acc2CaseInfoInstance);
    }
}
