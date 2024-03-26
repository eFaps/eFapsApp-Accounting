/*
 * Copyright © 2003 - 2024 The eFaps Team (-)
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
 */
package org.efaps.esjp.accounting.report;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.efaps.admin.common.SystemConfiguration;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.CachedPrintQuery;
import org.efaps.db.Instance;
import org.efaps.db.InstanceQuery;
import org.efaps.db.PrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.esjp.accounting.Period;
import org.efaps.esjp.accounting.util.Accounting;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.common.jasperreport.AbstractBeanCollectionDataSource;
import org.efaps.esjp.erp.util.ERP;
import org.efaps.util.EFapsException;

import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JasperReport;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 *
 */
@EFapsUUID("778ba07a-7111-45cd-9a76-fe12f9f67054")
@EFapsApplication("eFapsApp-Accounting")
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
            final String companyName = ERP.COMPANY_NAME.get();
            if (companyName != null && !companyName.isEmpty()) {
                _jrParameters.put("CompanyName", companyName);
            }
            final String companyTaxNum = ERP.COMPANY_TAX.get();
            if (companyTaxNum != null && !companyTaxNum.isEmpty()) {
                _jrParameters.put("CompanyTaxNum", companyTaxNum);
            }
            final String companyActivity = ERP.COMPANY_ACTIVITY.get();
            if (companyActivity != null && !companyActivity.isEmpty()) {
                _jrParameters.put("CompanyActivity", companyActivity);
            }
            final String companyStreet = ERP.COMPANY_STREET.get();
            if (companyStreet != null && !companyStreet.isEmpty()) {
                _jrParameters.put("CompanyStreet", companyStreet);
            }
            final String companyRegion = ERP.COMPANY_REGION.get();
            if (companyRegion != null && !companyRegion.isEmpty()) {
                _jrParameters.put("CompanyRegion", companyRegion);
            }
            final String companyCity = ERP.COMPANY_CITY.get();
            if (companyCity != null && !companyCity.isEmpty()) {
                _jrParameters.put("CompanyCity", companyCity);
            }
            final String companyDistrict = ERP.COMPANY_DISTRICT.get();
            if (companyDistrict != null && !companyDistrict.isEmpty()) {
                _jrParameters.put("CompanyDistrict", companyDistrict);
            }
        }
        final Instance periodInst = new Period().evaluateCurrentPeriod(_parameter);
        final PrintQuery print = new CachedPrintQuery(periodInst, Period.CACHEKEY);
        print.addAttribute(CIAccounting.Period.Name);
        print.execute();
        _jrParameters.put("Period", print.getAttribute(CIAccounting.Period.Name));
    }


    /**
     * Gets the list of account instances.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @param _key the key
     * @return the account inst
     * @throws EFapsException on error
     */
    protected List<Instance> getAccountInst(final Parameter _parameter,
                                            final String _key)
        throws EFapsException
    {
        final List<Instance> ret = new ArrayList<>();
        final Instance periodInst = new Period().evaluateCurrentPeriod(_parameter);

        final Properties props = Accounting.getSysConfig().getObjectAttributeValueAsProperties(periodInst);
        final String accNameStr = props.getProperty(_key, "NN");
        final String[] accNameAr = accNameStr.split(";");
        for (final String accName : accNameAr) {
            final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.AccountAbstract);
            queryBldr.addWhereAttrEqValue(CIAccounting.AccountAbstract.Name, accName);
            queryBldr.addWhereAttrEqValue(CIAccounting.AccountAbstract.PeriodAbstractLink, periodInst);
            final InstanceQuery query = queryBldr.getQuery();
            query.executeWithoutAccessCheck();
            while (query.next()) {
                final Instance inst = query.getCurrentValue();
                ret.add(inst);
                ret.addAll(getAccountInst(_parameter, inst, true));
            }
        }
        return ret;
    }

    /**
     * Gets the account inst.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @param _parentInst the parent inst
     * @param _includeSummary the include summary
     * @return the account inst
     * @throws EFapsException on error
     */
    protected List<Instance> getAccountInst(final Parameter _parameter,
                                            final Instance _parentInst,
                                            final boolean _includeSummary)
        throws EFapsException
    {
        final List<Instance> ret = new ArrayList<>();
        final boolean descend;
        if (_includeSummary) {
            descend = true;
        } else {
            final PrintQuery print = new PrintQuery(_parentInst);
            print.addAttribute(CIAccounting.AccountAbstract.Summary);
            print.execute();
            descend = print.<Boolean>getAttribute(CIAccounting.AccountAbstract.Summary);
        }
        if (descend) {
            final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.AccountAbstract);
            queryBldr.addWhereAttrEqValue(CIAccounting.AccountAbstract.ParentLink, _parentInst);
            final InstanceQuery query = queryBldr.getQuery();
            query.executeWithoutAccessCheck();
            while (query.next()) {
                final Instance inst = query.getCurrentValue();
                if (_includeSummary) {
                    ret.add(inst);
                }
                ret.addAll(getAccountInst(_parameter, inst, _includeSummary));
            }
        } else {
            ret.add(_parentInst);
        }
        return ret;
    }
}
