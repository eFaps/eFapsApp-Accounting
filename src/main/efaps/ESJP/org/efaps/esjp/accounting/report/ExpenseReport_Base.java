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

import org.efaps.admin.datamodel.Classification;
import org.efaps.admin.dbproperty.DBProperties;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.program.esjp.EFapsRevision;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.Instance;
import org.efaps.db.InstanceQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.ci.CIERP;
import org.efaps.esjp.sales.document.DocReport;
import org.efaps.util.EFapsException;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;


/**
 * TODO comment!
 *
 * @author The eFaps Team
 * @version $Id$
 */
@EFapsUUID("25cc9a9b-0cd9-49e4-ae2e-70540a6edead")
@EFapsRevision("$Rev$")
public abstract class ExpenseReport_Base
    extends DocReport
{

    /**
     * Get the name for the report.
     * @param _parameter Parameter as passed form the eFaps API
     * @param _from fromdate
     * @param _to   to date
     * @return name of the report
     */
    @Override
    protected String getReportName(final Parameter _parameter,
                                   final DateTime _from,
                                   final DateTime _to)
    {
        return DBProperties.getProperty("Accounting_ExpenseReport.Label", "es")
            + "-" + _from.toString(DateTimeFormat.shortDate())
            + "-" + _to.toString(DateTimeFormat.shortDate());
    }

    @Override
    protected List<Instance> getInstances(final Parameter _parameter,
                                          final DateTime _from,
                                          final DateTime _to)
        throws EFapsException
    {
        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.TransactionAbstract);
        queryBldr.addWhereClassification((Classification) CIAccounting.TransactionClassExternal.getType());
        queryBldr.addWhereAttrGreaterValue(CIERP.DocumentAbstract.Date, _from.minusMinutes(1));
        queryBldr.addWhereAttrLessValue(CIERP.DocumentAbstract.Date, _to.plusDays(1));
        queryBldr.addWhereAttrEqValue(CIAccounting.TransactionAbstract.PeriodeLink, _parameter.getInstance().getId());
        final InstanceQuery query = queryBldr.getQuery();
        query.execute();
        return query.getValues();
    }
}
