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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.efaps.admin.common.SystemConfiguration;
import org.efaps.admin.datamodel.Type;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.AttributeQuery;
import org.efaps.db.Context;
import org.efaps.db.Instance;
import org.efaps.db.InstanceQuery;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.PrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.esjp.accounting.util.Accounting;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.ci.CIERP;
import org.efaps.esjp.common.jasperreport.EFapsDataSource;
import org.efaps.esjp.erp.Currency;
import org.efaps.esjp.erp.CurrencyInst;
import org.efaps.esjp.erp.RateInfo;
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
@EFapsUUID("bcdd8bf3-b076-403a-af2f-f09b52bb92e7")
@EFapsApplication("eFapsApp-Accounting")
public abstract class AccountDataSource_Base
    extends EFapsDataSource
{

    /**
     * @see org.efaps.esjp.common.jasperreport.EFapsDataSource_Base#init(net.sf.jasperreports.engine.JasperReport)
     * @param _jasperReport JasperReport
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
        final List<Instance> instances = new ArrayList<>();
        if (_parameter.getInstance() != null && _parameter.getInstance().isValid()
                        && _parameter.getInstance().getType().isKindOf(CIAccounting.AccountAbstract.getType())) {
            instances.add(_parameter.getInstance());
            final PrintQuery printRep = new PrintQuery(_parameter.getInstance());
            printRep.addAttribute(CIAccounting.AccountAbstract.Name);
            printRep.execute();
            final String name = printRep.<String>getAttribute(CIAccounting.ReportAbstract.Name).replaceAll(" ", "_");
            _jrParameters.put("FileName", name);
        } else {
            final String[] oids = (String[]) Context.getThreadContext().getSessionAttribute("selectedOIDs");
            final Set<Instance> instSet = new LinkedHashSet<>();
            for (final String oid : oids) {
                final Instance instancetmp = Instance.get(oid);
                if (instancetmp.isValid()) {
                    instSet.addAll(getAccountInstances(_parameter, instancetmp));
                }
            }
            instances.addAll(instSet);
        }
        final List<Instance> posInstances = new ArrayList<>();
        final DateTime dateFrom = new DateTime(_parameter.getParameterValue("dateFrom"));
        final DateTime dateTo = new DateTime(_parameter.getParameterValue("dateTo"));
        final Map<DateTime, BigDecimal> rates = new HashMap<>();
        _jrParameters.put("Rates", rates);
        if ("true".equalsIgnoreCase(_parameter.getParameterValue("filterActive"))) {
            final Instance curr = Instance.get(CIERP.Currency.getType(),
                            Long.valueOf(_parameter.getParameterValue("currency")));
            if (curr.isValid()) {
                final CurrencyInst curInst = new CurrencyInst(curr);
                _jrParameters.put("TargetCurrencyId", curr.getId());
                _jrParameters.put("TargetCurrencyLabel", curInst.getName());

                rates.putAll(getRates4DateRange(_parameter, curInst.getInstance(), dateFrom, dateTo));
            }
        } else {
            _jrParameters.put("TargetCurrencyId", null);
        }
        for (final Instance accInst : instances) {
            final QueryBuilder attrQueryBldr = new QueryBuilder(CIAccounting.TransactionAbstract);
            attrQueryBldr.addWhereAttrLessValue(CIAccounting.TransactionAbstract.Date, dateTo.plusDays(1));
            attrQueryBldr.addWhereAttrGreaterValue(CIAccounting.TransactionAbstract.Date, dateFrom.minusSeconds(1));

            final AttributeQuery attrQuery = attrQueryBldr.getAttributeQuery(CIAccounting.TransactionAbstract.ID);

            final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.TransactionPositionAbstract);
            queryBldr.addWhereAttrEqValue(CIAccounting.TransactionPositionAbstract.AccountLink, accInst.getId());
            queryBldr.addWhereAttrInQuery(CIAccounting.TransactionPositionAbstract.TransactionLink, attrQuery);
            final InstanceQuery query = queryBldr.getQuery();
            posInstances.addAll(query.execute());
        }

        if (posInstances.size() > 0) {
            final MultiPrintQuery multi = new MultiPrintQuery(posInstances);
            for (final JRField field : _jasperReport.getMainDataset().getFields()) {
                final String select = field.getPropertiesMap().getProperty("Select");
                if (select != null) {
                    multi.addSelect(select);
                }
            }
            multi.setEnforceSorted(true);
            multi.execute();
            setPrint(multi);
        }

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

    protected Set<Instance> getAccountInstances(final Parameter _parameter,
                                                final Instance _instance)
                                                    throws EFapsException
    {
        final Set<Instance> ret = new LinkedHashSet<>();
        final PrintQuery print = new PrintQuery(_instance);
        print.addAttribute(CIAccounting.AccountAbstract.Summary);
        print.execute();
        if (print.<Boolean>getAttribute(CIAccounting.AccountAbstract.Summary)) {
            final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.AccountAbstract);
            queryBldr.addWhereAttrEqValue(CIAccounting.AccountAbstract.ParentLink, _instance.getId());
            final InstanceQuery query = queryBldr.getQuery();
            query.execute();
            while (query.next()) {
                ret.addAll(getAccountInstances(_parameter, query.getCurrentValue()));
            }
        } else {
            ret.add(_instance);
        }
        return ret;
    }

    protected Map<DateTime, BigDecimal> getRates4DateRange(final Parameter _parameter,
                                                           final Instance _curInst,
                                                           final DateTime _from,
                                                           final DateTime _to)
                                                               throws EFapsException
    {
        final Map<DateTime, BigDecimal> map = new HashMap<>();
        DateTime fromAux = _from;
        final Currency currency = getCurrency(_parameter);

        while (fromAux.isBefore(_to)) {
            final RateInfo rateInfo = currency.evaluateRateInfo(_parameter, fromAux, _curInst);
            final BigDecimal rate = RateInfo.getRate(_parameter, rateInfo, AccountDataSource.class.getName());
            map.put(fromAux, rate);
            fromAux = fromAux.plusDays(1);
        }
        return map;
    }

    protected Currency getCurrency(final Parameter _parameter)
        throws EFapsException
    {
        final Currency ret = new Currency()
        {

            @Override
            protected Type getType4ExchangeRate(final Parameter _parameter)
                throws EFapsException
            {
                // TODO final Long rateCurType =
                // Long.parseLong(_parameter.getParameterValue("rateCurrencyType"));
                Type typeRet;
                if (Accounting.CURRATEEQ.get()) {
                    typeRet = super.getType4ExchangeRate(_parameter);
                } else {
                    typeRet = CIAccounting.ERP_CurrencyRateAccounting.getType();
                }
                return typeRet;
            }
        };
        return ret;
    }

}
