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

package org.efaps.esjp.accounting.report.balance;

import org.efaps.admin.event.Parameter;
import org.efaps.esjp.accounting.report.balance.BalanceReport316DS_Base.Bean316;
import org.efaps.esjp.accounting.util.AccountingSettings;
import org.efaps.util.EFapsException;


/**
 * TODO comment!
 *
 * @author The eFaps Team
 * @version $Id$
 */
public abstract class BalanceReport316DS_Base
    extends AbstractBalanceReportDS<Bean316>
{

    /**
     * {@inheritDoc}
     */
    @Override
    public Bean316 getBean(final Parameter _parameter)
        throws EFapsException
    {
        return new Bean316();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getKey(final Parameter _parameter)
        throws EFapsException
    {
        return AccountingSettings.PERIOD_REPORT316ACCOUNT;
    }

    public static class Bean316
        extends AbstractDataBean
    {

    }
}
