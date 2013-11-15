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

import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JRField;
import net.sf.jasperreports.engine.JasperReport;

import org.efaps.admin.common.SystemConfiguration;
import org.efaps.admin.datamodel.Type;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.program.esjp.EFapsRevision;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.AttributeQuery;
import org.efaps.db.Context;
import org.efaps.db.Instance;
import org.efaps.db.InstanceQuery;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.PrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.ci.CIERP;
import org.efaps.esjp.common.jasperreport.EFapsDataSource;
import org.efaps.esjp.erp.CurrencyInst;
import org.efaps.esjp.erp.Rate;
import org.efaps.esjp.erp.util.ERP;
import org.efaps.esjp.erp.util.ERPSettings;
import org.efaps.util.EFapsException;
import org.joda.time.DateTime;

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
        final List<Instance> instances = new ArrayList<Instance>();
        if (_parameter.getInstance() != null  && _parameter.getInstance().isValid()
                        && _parameter.getInstance().getType().isKindOf(CIAccounting.AccountAbstract.getType())) {
            instances.add(_parameter.getInstance());
            final PrintQuery printRep = new PrintQuery(_parameter.getInstance());
            printRep.addAttribute(CIAccounting.AccountAbstract.Name);
            printRep.execute();
            final String name = printRep.<String>getAttribute(CIAccounting.ReportAbstract.Name).replaceAll(" ", "_");
            _jrParameters.put("FileName", name);
        } else {
            final String[] oids = (String[]) Context.getThreadContext().getSessionAttribute("selectedOIDs");
            final Set<Instance>instSet = new LinkedHashSet<Instance>();
            for (final String oid : oids) {
                final Instance instancetmp = Instance.get(oid);
                if (instancetmp.isValid()) {
                    instSet.addAll(getAccountInstances(_parameter, instancetmp));
                }
            }
            instances.addAll(instSet);
        }
        final List<Instance> posInstances = new ArrayList<Instance>();
        final DateTime dateFrom = new DateTime(_parameter.getParameterValue("dateFrom"));
        final DateTime dateTo = new DateTime(_parameter.getParameterValue("dateTo"));
        final Map<DateTime, BigDecimal> rates = new HashMap<DateTime, BigDecimal>();
        _jrParameters.put("Rates", rates);
        if ("true".equalsIgnoreCase(_parameter.getParameterValue("filterActive"))) {
            final Instance curr = Instance.get(CIERP.Currency.getType(),
                            Long.valueOf(_parameter.getParameterValue("currency")));
            if (curr.isValid()) {
                final CurrencyInst curInst = new CurrencyInst(curr);
                _jrParameters.put("TargetCurrencyId", curr.getId());
                _jrParameters.put("TargetCurrencyLabel", curInst.getName());
                final Long rateCurType = Long.parseLong(_parameter.getParameterValue("rateCurrencyType"));
                rates.putAll(getRates4DateRange(curInst.getInstance(), dateFrom, dateTo, rateCurType));
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
            final String companyName = config.getAttributeValue(ERPSettings.COMPANYNAME);
            final String companyTaxNumb = config.getAttributeValue(ERPSettings.COMPANYTAX);

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
        final Set<Instance> ret = new LinkedHashSet<Instance>();
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

    protected Map<DateTime, BigDecimal> getRates4DateRange(final Instance _curInst,
                                                           final DateTime _from,
                                                           final DateTime _to,
                                                           final Long _rateCurType)
        throws EFapsException
    {
        final Map<DateTime, BigDecimal> map = new HashMap<DateTime, BigDecimal>();
        DateTime fromAux = _from;
        Rate rate;
        while (fromAux.isBefore(_to)) {
            final QueryBuilder queryBldr = new QueryBuilder(Type.get(_rateCurType));
            queryBldr.addWhereAttrEqValue(CIERP.CurrencyRateAbstract.CurrencyLink, _curInst.getId());
            queryBldr.addWhereAttrGreaterValue(CIERP.CurrencyRateAbstract.ValidUntil, fromAux.minusMinutes(1));
            queryBldr.addWhereAttrLessValue(CIERP.CurrencyRateAbstract.ValidFrom, fromAux.plusMinutes(1));
            final MultiPrintQuery multi = queryBldr.getPrint();
            final SelectBuilder valSel = new SelectBuilder()
                            .attribute(CIERP.CurrencyRateAbstract.Rate).value();
            final SelectBuilder labSel = new SelectBuilder()
                            .attribute(CIERP.CurrencyRateAbstract.Rate).label();
            final SelectBuilder curSel = new SelectBuilder()
                            .linkto(CIERP.CurrencyRateAbstract.CurrencyLink).oid();
            multi.addSelect(valSel, labSel, curSel);
            multi.execute();
            if (multi.next()) {
                rate = new Rate(new CurrencyInst(Instance.get(multi.<String>getSelect(curSel))),
                                multi.<BigDecimal>getSelect(valSel),
                                multi.<BigDecimal>getSelect(labSel));
            } else {
                rate = new Rate(new CurrencyInst(Instance.get(CIERP.Currency.getType(),
                                _curInst.getId())), BigDecimal.ONE);
            }
            map.put(fromAux, rate.getValue());
            fromAux = fromAux.plusDays(1);
        }
        return map;
    }

}
