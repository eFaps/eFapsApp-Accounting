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

package org.efaps.esjp.accounting.export;

import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;

/**
 * Columns for an report.
 *
 * @author The eFaps Team
 */
@EFapsUUID("e2a1c241-fbe7-484b-aa07-49d8d0f6fa94")
@EFapsApplication("eFapsApp-Accounting")
public enum ColumnReport
    implements IColumn
{
    /** */
    TYPE("[Report_Type]"),
    /** */
    NAME("[Report_Name]"),
    /** */
    DESC("[Report_Description]"),
    /** */
    NUMBERING("[Report_Numbering]"),
    /** */
    NODE_TYPE("[Node_Type]"),
    /** */
    NODE_SHOW("[Node_ShowAllways]"),
    /** */
    NODE_SUM("[Node_ShowSum]"),
    /** */
    NODE_NUMBER("[Node_Number]");

    /** Key. */
    private final String key;

    /**
     * @param _key key
     */
    private ColumnReport(final String _key)
    {
        this.key = _key;
    }

    /**
     * Getter method for instance variable {@link #key}.
     *
     * @return value of instance variable {@link #key}
     */
    @Override
    public String getKey()
    {
        return this.key;
    }
}
