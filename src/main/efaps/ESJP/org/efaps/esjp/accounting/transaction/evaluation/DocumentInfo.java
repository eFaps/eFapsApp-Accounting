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
package org.efaps.esjp.accounting.transaction.evaluation;

import java.util.Collection;

import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.Instance;
import org.efaps.esjp.accounting.util.Accounting.SummarizeConfig;
import org.efaps.esjp.accounting.util.Accounting.SummarizeCriteria;
import org.efaps.util.EFapsException;

/**
 * This class must be replaced for customization, therefore it is left empty.
 * Functional description can be found in the related "<code>_base</code>"
 * class.
 *
 * @author The eFaps Team
 */
@EFapsUUID("ea1a8ce4-50e8-44dd-b43c-dd517ebadb2a")
@EFapsApplication("eFapsApp-Accounting")
public class DocumentInfo
    extends DocumentInfo_Base
{

    /**
     * Constructor.
     */
    public DocumentInfo()
    {
        super();
    }

    /**
     * @param _instance Instance of the Document
     */
    public DocumentInfo(final Instance _instance)
    {
        super(_instance);
    }

    /**
     * Gets the combined.
     *
     * @param _docInfos the doc infos
     * @param _config the config
     * @param _criteria the criteria
     * @return the combined
     * @throws EFapsException on error
     */
    public static DocumentInfo getCombined(final Collection<DocumentInfo> _docInfos,
                                           final SummarizeConfig _config,
                                           final SummarizeCriteria _criteria)
        throws EFapsException
    {
        return DocumentInfo_Base.getCombined(_docInfos, _config, _criteria);
    }

}
