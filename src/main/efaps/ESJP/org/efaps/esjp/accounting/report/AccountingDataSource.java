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

package org.efaps.esjp.accounting.report;

import java.util.List;

import org.efaps.admin.program.esjp.EFapsRevision;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.esjp.accounting.report.Report_Base.Node;
import org.efaps.util.EFapsException;

/**
 * This class must be replaced for customization, therefore it is left empty.
 * Functional description can be found in the related "<code>_base</code>"
 * class.
 *
 * @author The eFaps Team
 * @version $Id: AccountingDataSource.java 3878 2010-03-25 13:37:03Z
 *          miguel.a.aranya $
 */
@EFapsUUID("7e96dfca-eecc-487b-95e3-0a1c933d1f51")
@EFapsRevision("$Rev$")
public class AccountingDataSource
    extends AccountingDataSource_Base
{

    /**
     * Constructor.
     *
     * @param _jasperReport JasperReport
     * @throws EFapsException on error
     */
    public AccountingDataSource(final List<List<Node>> _values)
        throws EFapsException
    {
        super(_values);
    }

}
