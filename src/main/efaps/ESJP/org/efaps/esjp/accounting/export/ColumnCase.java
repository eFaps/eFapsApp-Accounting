/*
 * Copyright 2003 - 2013 The eFaps Team
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


package org.efaps.esjp.accounting.export;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 * @version $Id: $
 */
public enum ColumnCase
    implements IColumn
{
    /** */
    A2CTYPE("[Account2Case_Type]"),
    /** */
    A2CACC("[Account2Case_Account]"),
    /** */
    A2CCLA("[Account2Case_Classification]"),
    /** */
    A2CNUM("[Account2Case_Numerator]"),
    /** */
    A2CDENUM("[Account2Case_Denominator]"),
    /** */
    A2CDEFAULT("[Account2Case_Default]"),
    /** */
    A2CAPPLYLABEL("[Account2Case_CostCenter]"),
    /** */
    CASETYPE("[Case_Type]"),
    /** */
    CASENAME("[Case_Name]"),
    /** */
    CASEDESC("[Case_Description]"),
    /** */
    CASEISCROSS("[Case_IsCross]"),
    /** */
    CASELABEL("[Case_Label]"),
    /** */
    CASECONFIG("[Case_SummarizeConfig]");

    /** Key. */
    private final String key;

    /**
     * @param _key key
     */
    private ColumnCase(final String _key)
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
