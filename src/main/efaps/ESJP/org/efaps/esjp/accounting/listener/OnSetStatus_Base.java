/*
 * Copyright 2003 - 2018 The eFaps Team
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

package org.efaps.esjp.accounting.listener;

import java.util.HashSet;
import java.util.Set;

import org.efaps.admin.datamodel.Status;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.Instance;
import org.efaps.db.QueryBuilder;
import org.efaps.esjp.accounting.util.Accounting;
import org.efaps.esjp.admin.datamodel.AbstractSetStatusListener;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.common.parameter.ParameterUtil;
import org.efaps.esjp.common.tag.Tag;
import org.efaps.util.EFapsException;

/**
 * This class must be replaced for customization, therefore it is left empty.
 * Functional description can be found in the related "<code>_base</code>"
 * class.
 *
 * @author The eFaps Team
 *
 */
@EFapsUUID("2eced7f8-b4fa-4bc3-90f9-dc2d20765a2a")
@EFapsApplication("eFapsApp-Accounting")
public abstract class OnSetStatus_Base
    extends AbstractSetStatusListener
{

    @Override
    public void after(final Parameter _parameter, final Instance _instance, final Status _status)
        throws EFapsException
    {
        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.Period2ERPDocument);
        queryBldr.addWhereAttrEqValue(CIAccounting.Period2ERPDocument.ToLink, _instance);
        if (!queryBldr.getQuery().executeWithoutAccessCheck().isEmpty()) {
            Tag.tagObject(_parameter, _instance, CIAccounting.CanceledTag4Documents.getType());
        }
    }

    @Override
    public Set<Status> getStatus()
        throws EFapsException
    {
        return new HashSet<>(getStatusListFromProperties(ParameterUtil.instance(), Accounting.CANCELEDTAG.get()));
    }
}
