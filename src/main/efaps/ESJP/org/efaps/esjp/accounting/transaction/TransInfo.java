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

import java.util.List;

import org.efaps.admin.datamodel.Type;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.esjp.accounting.transaction.evaluation.DocumentInfo;
import org.efaps.util.EFapsException;

/**
 * This class must be replaced for customization, therefore it is left empty.
 * Functional description can be found in the related "<code>_base</code>"
 * class.
 *
 * @author The eFaps Team
 */
@EFapsUUID("00485da3-4c80-42f5-90bb-04b6eb3b3d85")
@EFapsApplication("eFapsApp-Accounting")
public class TransInfo
    extends TransInfo_Base
{

    /**
     * Gets the 4 doc info.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @param _docInfo the doc info
     * @param _setDocInst the set doc inst
     * @return the 4 doc info
     * @throws EFapsException on error
     */
    public static TransInfo get4DocInfo(final Parameter _parameter,
                                        final DocumentInfo _docInfo,
                                        final boolean _setDocInst)
        throws EFapsException
    {
        return TransInfo_Base.get4DocInfo(_parameter, _docInfo, _setDocInst);
    }

    /**
     * Gets the rel pos infos.
     *
     * @param _parameter the parameter
     * @param _accInfo the acc info
     * @param _type the type
     * @return the rel pos infos
     * @throws EFapsException the e faps exception
     */
    public static List<PositionInfo> getRelPosInfos(final Parameter _parameter,
                                                       final AccountInfo _accInfo,
                                                       final Type _type)
        throws EFapsException
    {
        return TransInfo_Base.getRelPosInfos(_parameter, _accInfo, _type);
    }

    /**
     * Gets the 4 account info.
     *
     * @param _parameter the parameter
     * @param _type the type
     * @param _accInfo the acc info
     * @return the 4 account info
     * @throws EFapsException the e faps exception
     */
    public static PositionInfo get4AccountInfo(final Parameter _parameter,
                                               final Type _type,
                                               final AccountInfo _accInfo)
        throws EFapsException
    {
        return TransInfo_Base.get4AccountInfo(_parameter, _type, _accInfo);
    }
}
