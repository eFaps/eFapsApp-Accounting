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


package org.efaps.esjp.accounting;

import net.sf.jasperreports.engine.JRField;

import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Return;
import org.efaps.admin.event.Return.ReturnValues;
import org.efaps.admin.program.esjp.EFapsRevision;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.PrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.common.jasperreport.EFapsDataSource;
import org.efaps.util.EFapsException;
import org.joda.time.DateTime;


/**
 * TODO comment!
 *
 * @author The eFaps Team
 * @version $Id$
 */
@EFapsUUID("f6ff24a0-764a-4aba-998f-3d30428e7353")
@EFapsRevision("$Rev$")
public abstract class JournalDataSource_Base
    extends EFapsDataSource
{

    /**
     * Called from a field value event to get the value for the date from field.
     * @param _parameter    Parameter as passed from the eFaps API
     * @return  new Return containing value
     * @throws EFapsException on error
     */
    public Return getDateFromFieldValue(final Parameter _parameter)
        throws EFapsException
    {
        final PrintQuery print = new PrintQuery(_parameter.getInstance());
        print.addAttribute(CIAccounting.Periode.FromDate);
        DateTime date;
        if (print.execute()) {
            date = print.getAttribute(CIAccounting.Periode.FromDate);
        } else {
            date = new DateTime();
        }

        final Return ret = new Return();
        ret.put(ReturnValues.VALUES, date);
        return ret;
    }

    /* (non-Javadoc)
     * @see org.efaps.esjp.common.jasperreport.EFapsDataSource_Base#analyze()
     */
    @Override
    protected void analyze()
        throws EFapsException
    {
        if (isSubDataSource()) {
            super.analyze();
        } else {
            final DateTime dateFrom = new DateTime(getParameter().getParameterValue("dateFrom"));
            final DateTime dateTo = new DateTime(getParameter().getParameterValue("dateTo"));

            final QueryBuilder queryBuilder = new QueryBuilder(CIAccounting.TransactionAbstract);
            queryBuilder.addWhereAttrEqValue(CIAccounting.TransactionAbstract.PeriodeLink, getInstance().getId());
            queryBuilder.addWhereAttrLessValue(CIAccounting.TransactionAbstract.Date, dateTo.plusDays(1));
            queryBuilder.addWhereAttrGreaterValue(CIAccounting.TransactionAbstract.Date, dateFrom.minusSeconds(1));
            queryBuilder.addOrderByAttributeAsc(CIAccounting.TransactionAbstract.Date);
            setPrint(queryBuilder.getPrint());
            getPrint().setEnforceSorted(true);
            if (getJasperReport().getMainDataset().getFields() != null) {
                for (final JRField field : getJasperReport().getMainDataset().getFields()) {
                    final String select = field.getPropertiesMap().getProperty("Select");
                    if (select != null) {
                        getPrint().addSelect(select);
                        getSelects().add(select);
                    }
                }
            }
            getPrint().execute();
        }
    }


}
