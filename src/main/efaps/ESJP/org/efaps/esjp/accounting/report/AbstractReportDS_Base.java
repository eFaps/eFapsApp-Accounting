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


package org.efaps.esjp.accounting.report;

import java.util.Map;

import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JasperReport;

import org.efaps.admin.common.SystemConfiguration;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.program.esjp.EFapsRevision;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.CachedPrintQuery;
import org.efaps.db.Instance;
import org.efaps.db.PrintQuery;
import org.efaps.esjp.accounting.Period;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.common.jasperreport.AbstractBeanCollectionDataSource;
import org.efaps.esjp.erp.util.ERP;
import org.efaps.esjp.erp.util.ERPSettings;
import org.efaps.util.EFapsException;


/**
 * TODO comment!
 *
 * @author The eFaps Team
 * @version $Id$
 */
@EFapsUUID("778ba07a-7111-45cd-9a76-fe12f9f67054")
@EFapsRevision("$Rev$")
public abstract class AbstractReportDS_Base
    extends AbstractBeanCollectionDataSource
{

    /**
     * Executed on initialization of the DataSource.
     *
     * @param _jasperReport JasperReport this DataSource belongs to
     * @param _parameter    Parameter as passed from the eFaps API
     * @param _parentSource parent DataSource in case that this DataSource
     *                      belongs to a subreport
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
        final SystemConfiguration config = ERP.getSysConfig();
        if (config != null) {
            final String companyName = config.getAttributeValue(ERPSettings.COMPANYNAME);
            final String companyTaxNumb = config.getAttributeValue(ERPSettings.COMPANYTAX);
            if (companyName != null && companyTaxNumb != null && !companyName.isEmpty() && !companyTaxNumb.isEmpty()) {
                _jrParameters.put("CompanyName", companyName);
                _jrParameters.put("CompanyTaxNum", companyTaxNumb);
            }
        }
        final Instance periodInst = new Period().evaluateCurrentPeriod(_parameter);
        final PrintQuery print = new CachedPrintQuery(periodInst, Period.CACHEKEY);
        print.addAttribute(CIAccounting.Period.Name);
        print.execute();
        _jrParameters.put("Period", print.getAttribute(CIAccounting.Period.Name));
    }
}
