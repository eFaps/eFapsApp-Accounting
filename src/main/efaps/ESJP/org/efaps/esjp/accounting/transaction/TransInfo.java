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

import org.efaps.admin.event.Parameter;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.util.EFapsException;

/**
 * This class must be replaced for customization, therefore it is left empty.
 * Functional description can be found in the related "<code>_base</code>"
 * class.
 *
 * @author The eFaps Team
 * @version $Id$
 */
@EFapsUUID("00485da3-4c80-42f5-90bb-04b6eb3b3d85")
@EFapsApplication("eFapsApps-Accounting")
public class TransInfo
    extends TransInfo_Base
{
    /**
     * @param _parameter
     * @param _docInfo
     * @return
     */
    public static TransInfo get4DocInfo(final Parameter _parameter,
                                        final DocumentInfo _docInfo,
                                        final boolean _setDocInst)
        throws EFapsException
    {
        return TransInfo_Base.get4DocInfo(_parameter, _docInfo, _setDocInst);
    }
}
