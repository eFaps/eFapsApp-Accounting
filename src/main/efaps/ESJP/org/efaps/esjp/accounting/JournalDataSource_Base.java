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

import java.util.Map;

import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JRField;
import net.sf.jasperreports.engine.JasperReport;

import org.efaps.admin.datamodel.Classification;
import org.efaps.admin.datamodel.Type;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Parameter.ParameterValues;
import org.efaps.admin.event.Return;
import org.efaps.admin.event.Return.ReturnValues;
import org.efaps.admin.program.esjp.EFapsRevision;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.AttributeQuery;
import org.efaps.db.PrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
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
        final SelectBuilder sel = new SelectBuilder().linkto(CIAccounting.ReportSubJournal.PeriodeLink)
                        .attribute(CIAccounting.Periode.FromDate);
        if (_parameter.getInstance().getType().isKindOf(CIAccounting.ReportSubJournal.getType())) {
            print.addSelect(sel);
        } else {
            print.addAttribute(CIAccounting.Periode.FromDate);
        }

        DateTime date;
        if (print.execute()) {
            date = print.getAttribute(CIAccounting.Periode.FromDate) == null
                            ? print.<DateTime>getSelect(sel)
                                            : print.<DateTime>getAttribute(CIAccounting.Periode.FromDate);
        } else {
            date = new DateTime();
        }

        final Return ret = new Return();
        ret.put(ReturnValues.VALUES, date);
        return ret;
    }

    @Override
    public void init(final JasperReport _jasperReport,
                     final Parameter _parameter,
                     final JRDataSource _parentSource,
                     final Map<String, Object> _jrParameters)
        throws EFapsException
    {
        if (_parameter.getInstance().isValid()
                        && _parameter.getInstance().getType().isKindOf(CIAccounting.ReportSubJournal.getType())) {
            final PrintQuery print = new PrintQuery(_parameter.getInstance());
            print.addAttribute(CIAccounting.ReportSubJournal.Name);
            print.execute();
            _jrParameters.put("Name", print.getAttribute(CIAccounting.ReportSubJournal.Name));
        }
        super.init(_jasperReport, _parameter, _parentSource, _jrParameters);
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

            final Map<?, ?> props = (Map<?, ?>) getParameter().get(ParameterValues.PROPERTIES);
            final QueryBuilder queryBuilder = new QueryBuilder(CIAccounting.TransactionAbstract);
            queryBuilder.addWhereAttrEqValue(CIAccounting.TransactionAbstract.PeriodeLink, getInstance().getId());
            queryBuilder.addWhereAttrLessValue(CIAccounting.TransactionAbstract.Date, dateTo.plusDays(1));
            queryBuilder.addWhereAttrGreaterValue(CIAccounting.TransactionAbstract.Date, dateFrom.minusSeconds(1));
            queryBuilder.addOrderByAttributeAsc(CIAccounting.TransactionAbstract.Date);
            queryBuilder.addOrderByAttributeAsc(CIAccounting.TransactionAbstract.Name);
            queryBuilder.addOrderByAttributeAsc(CIAccounting.TransactionAbstract.Description);

            if (getParameter().getInstance().isValid()
                            && getParameter().getInstance().getType().isKindOf(CIAccounting.ReportSubJournal.getType())) {
                final QueryBuilder attrQueryBldr = new QueryBuilder(CIAccounting.Report2Transaction);
                attrQueryBldr.addWhereAttrEqValue(CIAccounting.Report2Transaction.FromLinkAbstract, getParameter()
                                .getInstance().getId());
                final AttributeQuery attrQuery = attrQueryBldr
                                .getAttributeQuery(CIAccounting.Report2Transaction.ToLinkAbstract);
                queryBuilder.addWhereAttrInQuery(CIAccounting.TransactionAbstract.ID, attrQuery);
            }

            if (props.containsKey("Classifications")) {
                final String classStr = (String) props.get("Classifications");
                final String[] clazzes = classStr.split(";");
                for (final String clazz : clazzes) {
                    final Classification classif = (Classification) Type.get(clazz);
                    if (classif != null) {
                        queryBuilder.addWhereClassification(classif);
                    }
                }
            }
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
