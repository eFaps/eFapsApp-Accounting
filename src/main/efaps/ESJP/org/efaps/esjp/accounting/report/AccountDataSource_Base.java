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

package org.efaps.esjp.accounting.report;

import java.util.List;
import java.util.Map;

import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JRField;
import net.sf.jasperreports.engine.JasperReport;

import org.efaps.admin.event.Parameter;
import org.efaps.admin.program.esjp.EFapsRevision;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.Instance;
import org.efaps.db.InstanceQuery;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.common.jasperreport.EFapsDataSource;
import org.efaps.util.EFapsException;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 * @version $Id$
 */
@EFapsUUID("bcdd8bf3-b076-403a-af2f-f09b52bb92e7")
@EFapsRevision("$Rev$")
public abstract class AccountDataSource_Base
    extends EFapsDataSource
{

    /**
     * @see org.efaps.esjp.common.jasperreport.EFapsDataSource_Base#init(net.sf.jasperreports.engine.JasperReport)
     * @param _jasperReport  JasperReport
     * @param _parameter Parameter
     * @param _parentSource JRDataSource
     * @param _jrParameters map that contains the report parameters
     * @throws EFapsException on error
     */
    @Override
    public void init(final JasperReport _jasperReport,
                     final Parameter _parameter,
                     final JRDataSource _parentSource,
                     final Map<String, Object> _jrParameters)
        throws EFapsException
    {
        Instance instance = null;
        if (_parameter.getInstance() != null) {
            instance = _parameter.getInstance();
        } else {
            final QueryBuilder query = new QueryBuilder(CIAccounting.AccountAbstract);
            query.addWhereAttrEqValue(CIAccounting.AccountAbstract.Name, "101");
            final MultiPrintQuery multi = query.getPrint();
            multi.addAttribute(CIAccounting.AccountAbstract.OID);
            multi.execute();

            while (multi.next()) {
                instance = Instance.get(multi.<String>getAttribute(CIAccounting.AccountAbstract.OID));
            }
        }
        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.TransactionPositionAbstract);
        queryBldr.addWhereAttrEqValue(CIAccounting.TransactionPositionAbstract.AccountLink, instance.getId());
        final InstanceQuery query = queryBldr.getQuery();
        final List<Instance> instances  = query.execute();

        if (instances.size() > 0) {
            final MultiPrintQuery multi = new MultiPrintQuery(instances);
            for (final JRField field : _jasperReport.getMainDataset().getFields()) {
                final String select = field.getPropertiesMap().getProperty("Select");
                if (select != null) {
                    multi.addSelect(select);
                }
            }
            multi.execute();
            setPrint(multi);
        }
    }

}
