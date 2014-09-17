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

package org.efaps.esjp.ci;

import org.efaps.admin.program.esjp.EFapsNoUpdate;
import org.efaps.ci.CIAttribute;
import org.efaps.ci.CIType;

/**
 * This class is only used in case that the HumanResource App is not installed
 * to be able to compile the classes.
 * @author The eFaps Team
 */
@EFapsNoUpdate
public final class CIHumanResource
{
    public static final _Department2DocumentAbstract Department2DocumentAbstract = new _Department2DocumentAbstract("0abf800f-ae0e-4133-9c2d-b335bc158721");
    public static class _Department2DocumentAbstract extends CIType
    {
        protected _Department2DocumentAbstract(final String _uuid)
        {
            super(_uuid);
        }
        public final CIAttribute Created = new CIAttribute(this, "Created");
        public final CIAttribute Creator = new CIAttribute(this, "Creator");
        public final CIAttribute FromAbstractLink = new CIAttribute(this, "FromAbstractLink");
        public final CIAttribute Modified = new CIAttribute(this, "Modified");
        public final CIAttribute Modifier = new CIAttribute(this, "Modifier");
        public final CIAttribute ToAbstractLink = new CIAttribute(this, "ToAbstractLink");
    }
}
