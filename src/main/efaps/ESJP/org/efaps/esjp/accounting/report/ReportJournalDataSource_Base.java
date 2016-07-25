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

import java.util.Map;

import org.efaps.admin.common.SystemConfiguration;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.AttributeQuery;
import org.efaps.db.Instance;
import org.efaps.db.InstanceQuery;
import org.efaps.db.PrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.ci.CIERP;
import org.efaps.esjp.common.jasperreport.EFapsDataSource;
import org.efaps.esjp.erp.Currency;
import org.efaps.esjp.erp.CurrencyInst;
import org.efaps.esjp.erp.util.ERP;
import org.efaps.util.EFapsException;
import org.joda.time.DateTime;

import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JRField;
import net.sf.jasperreports.engine.JasperReport;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 * 
 */
@EFapsUUID("cc114cbc-c1fc-4275-92b7-c3e3acd96022")
@EFapsApplication("eFapsApp-Accounting")
public abstract class ReportJournalDataSource_Base
    extends EFapsDataSource
{


    private Map<String, Object> jrParameters;

    /**
     * {@inheritDoc}
     */
    @Override
    public void init(final JasperReport _jasperReport,
                     final Parameter _parameter,
                     final JRDataSource _parentSource,
                     final Map<String, Object> _jrParameters)
        throws EFapsException
    {
        this.jrParameters = _jrParameters;
        super.init(_jasperReport, _parameter, _parentSource, _jrParameters);

        final Boolean active = Boolean.parseBoolean(_parameter.getParameterValue("filterActive"));
        final String currency = _parameter.getParameterValue("currency");
        final Long rateCurType = Long.parseLong(_parameter.getParameterValue("rateCurrencyType"));
        final CurrencyInst curInst = new CurrencyInst(Instance.get(CIERP.Currency.getType(), currency));
        final Instance curBase = Currency.getBaseCurrency();

        _jrParameters.put("Active", active);
        _jrParameters.put("CurrencyBase", curBase);
        _jrParameters.put("CurrencyUI", curInst.getInstance());
        _jrParameters.put("RateCurrencyType", rateCurType);

        final SystemConfiguration config = ERP.getSysConfig();
        if (config != null) {
            final String companyName = ERP.COMPANYNAME.get();
            final String companyTaxNumb = ERP.COMPANYTAX.get();

            if (companyName != null && companyTaxNumb != null && !companyName.isEmpty() && !companyTaxNumb.isEmpty()) {
                _jrParameters.put("CompanyName", companyName);
                _jrParameters.put("CompanyTaxNum", companyTaxNumb);
            }
        }
    }

    @Override
    protected void analyze()
        throws EFapsException
    {
        if (isSubDataSource()) {
            super.analyze();
        } else {
            final Instance instance = getParameter().getInstance();
            final DateTime dateFrom = new DateTime(getParameter().getParameterValue("dateFrom"));
            final DateTime dateTo = new DateTime(getParameter().getParameterValue("dateTo"));
            this.jrParameters.put("FromDate", dateFrom.toDate());
            this.jrParameters.put("ToDate", dateTo.toDate());
            this.jrParameters.put("Mime", getParameter().getParameterValue("mime"));

            final PrintQuery printRep = new PrintQuery(instance);
            printRep.addAttribute(CIAccounting.ReportAbstract.Name);
            printRep.execute();
            final String name = printRep.<String>getAttribute(CIAccounting.ReportAbstract.Name).replaceAll(" ", "_");
            this.jrParameters.put("FileName", name);

            // get the root node, only the first one will be used
            final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.ReportNodeRoot);
            queryBldr.addWhereAttrEqValue(CIAccounting.ReportNodeRoot.ReportLink, instance.getId());
            final InstanceQuery query = queryBldr.getQuery();
            query.execute();
            if (query.next()) {
                // only the ReportAccounts will be evaluated
                final QueryBuilder accQueryBldr = new QueryBuilder(CIAccounting.ReportNodeAccount);
                accQueryBldr.addWhereAttrEqValue(CIAccounting.ReportNodeAccount.ParentLink,
                                query.getCurrentValue().getId());
                final AttributeQuery attrQuery = accQueryBldr.getAttributeQuery(
                                CIAccounting.ReportNodeAccount.AccountLink);
                final QueryBuilder tpQueryBldr = new QueryBuilder(CIAccounting.TransactionPositionAbstract);
                tpQueryBldr.addWhereAttrInQuery(CIAccounting.TransactionPositionAbstract.AccountLink, attrQuery);
                final AttributeQuery tpAttrQuery = tpQueryBldr.getAttributeQuery(
                                CIAccounting.TransactionPositionAbstract.TransactionLink);

                final QueryBuilder queryBuilder = new QueryBuilder(CIAccounting.TransactionAbstract);
                queryBuilder.addWhereAttrLessValue(CIAccounting.TransactionAbstract.Date, dateTo.plusDays(1));
                queryBuilder.addWhereAttrGreaterValue(CIAccounting.TransactionAbstract.Date, dateFrom.minusSeconds(1));
                queryBuilder.addOrderByAttributeAsc(CIAccounting.TransactionAbstract.Date);
                queryBuilder.addWhereAttrInQuery(CIAccounting.TransactionAbstract.ID, tpAttrQuery);
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
}
