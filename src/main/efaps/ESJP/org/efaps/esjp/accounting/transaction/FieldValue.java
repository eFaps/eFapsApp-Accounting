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


package org.efaps.esjp.accounting.transaction;

import java.util.ArrayList;
import java.util.Collection;

import org.efaps.admin.event.Parameter;
import org.efaps.admin.program.esjp.EFapsRevision;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.util.EFapsException;


/**
 * This class must be replaced for customization, therefore it is left empty.
 * Functional description can be found in the related "<code>_base</code>"
 * class.
 *
 * @author The eFaps Team
 * @version $Id: Transaction.java 4759 2010-06-14 17:34:27Z miguel.a.aranya $
 */
@EFapsUUID("f3b159cd-31f5-4d41-b880-645a3405bad0")
@EFapsRevision("$Rev: 4759 $")
public class FieldValue
    extends FieldValue_Base
{
    /**
     * {@inheritDoc}
     */
    @Override
    protected Collection<String> getSelectedLabel(final Parameter _parameter)
        throws EFapsException
    {
        return new ArrayList<String>();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected CharSequence getDocInformation(final Parameter _parameter,
                                             final Document _document)
        throws EFapsException
    {
        return "";
    }
}
