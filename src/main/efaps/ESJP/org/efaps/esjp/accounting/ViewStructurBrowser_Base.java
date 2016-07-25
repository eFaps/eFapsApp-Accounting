/*
 * Copyright 2003 - 2012 The eFaps Team
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


package org.efaps.esjp.accounting;

import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Return;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.esjp.ui.structurbrowser.StandartStructurBrowser;


/**
 * TODO comment!
 *
 * @author The eFaps Team
 * 
 */
@EFapsUUID("1a783876-93da-4db1-8557-b2e36cafc594")
@EFapsApplication("eFapsApp-Accounting")
public abstract class ViewStructurBrowser_Base
    extends StandartStructurBrowser
{
    /**
     * Method is called from the StructurBrowser in edit mode before rendering
     * the columns for row to be able to hide the columns for different rows by
     * setting the cell model to hide.
     * In this implementation all columns are always shown.
     * @param _parameter Paraemter as passed from the eFaps API
     * @return empty Return;
     */
    @Override
    protected Return checkHideColumn4Row(final Parameter _parameter)
    {
        return new Return();
    }
}
