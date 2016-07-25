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

package org.efaps.esjp.accounting.listener;

import java.util.List;
import java.util.Properties;

import org.efaps.admin.datamodel.Status;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.Instance;
import org.efaps.db.InstanceQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.esjp.accounting.Period;
import org.efaps.esjp.accounting.transaction.Create;
import org.efaps.esjp.accounting.transaction.FieldValue;
import org.efaps.esjp.accounting.util.Accounting;
import org.efaps.esjp.accounting.util.AccountingSettings;
import org.efaps.esjp.admin.datamodel.AbstractSetStatusListener;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.ci.CISales;
import org.efaps.esjp.common.parameter.ParameterUtil;
import org.efaps.util.EFapsException;
import org.efaps.util.cache.CacheReloadException;
/**
 * This class must be replaced for customization, therefore it is left empty.
 * Functional description can be found in the related "<code>_base</code>"
 * class.
 *
 * @author The eFaps Team
 * @version $Id$
 */
@EFapsUUID("7ae2bb2c-0e1c-4aef-bc36-1cb5e9b8dc1f")
@EFapsApplication("eFapsApp-Accounting")
public abstract class OnRetentionCertificateClose_Base
    extends AbstractSetStatusListener
{

    public OnRetentionCertificateClose_Base()
        throws CacheReloadException
    {
        super();
        getStatus().add(Status.find(CISales.RetentionCertificateStatus.Closed));
    }

    @Override
    public void after(final Parameter _parameter,
                      final Instance _instance,
                      final Status _status)
        throws EFapsException
    {
        final Instance periodInst = new Period().evaluateCurrentPeriod(_parameter);
        final Properties props = Accounting.getSysConfig().getObjectAttributeValueAsProperties(periodInst);

        if (props.containsKey(AccountingSettings.PERIOD_RETENTIONCASE)) {
            final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.CaseRetPer);
            queryBldr.addWhereAttrMatchValue(CIAccounting.CaseRetPer.Name,
                            props.get(AccountingSettings.PERIOD_RETENTIONCASE) + "*");
            final InstanceQuery query = queryBldr.getQuery();
            query.executeWithoutAccessCheck();
            if (query.next()) {
                final Parameter parameter = ParameterUtil.clone(_parameter);
                ParameterUtil.setParmeterValue(parameter, "selectedRow", _instance.getOid());

                final List<Instance> instances = new FieldValue().getSelectedDocInst(parameter);
                final String[] oids = new String[instances.size()];
                int i = 0;
                for (final Instance inst : instances) {
                    oids[i] = inst.getOid();
                    i++;
                }
                ParameterUtil.setParmeterValue(parameter, "case", query.getCurrentValue().getOid());
                ParameterUtil.setParmeterValue(parameter, "document", oids);

                final Create create = new Create();
                create.create4RetPerMassive(parameter);
            }
        }
    }
}
