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
package org.efaps.esjp.accounting.report.balance;

import org.efaps.admin.event.Parameter;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.esjp.accounting.report.balance.BalanceReport313DS_Base.Bean313;
import org.efaps.esjp.accounting.util.AccountingSettings;
import org.efaps.util.EFapsException;


/**
 * TODO comment!
 *
 * @author The eFaps Team
 *
 */
@EFapsUUID("4a239dd3-03e6-4119-a79d-53df6347dd8d")
@EFapsApplication("eFapsApp-Accounting")
public abstract class BalanceReport313DS_Base
    extends AbstractBalanceReportDS<Bean313>
{

    /**
     * {@inheritDoc}
     */
    @Override
    public Bean313 getBean(final Parameter _parameter)
        throws EFapsException
    {
        return new Bean313();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getKey(final Parameter _parameter)
        throws EFapsException
    {
        return AccountingSettings.PERIOD_REPORT313ACCOUNT;
    }

    public static class Bean313
        extends AbstractDataBean
    {

    }
}
