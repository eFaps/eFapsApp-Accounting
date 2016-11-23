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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.efaps.admin.common.SystemConfiguration;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.AttributeQuery;
import org.efaps.db.Instance;
import org.efaps.db.InstanceQuery;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.common.jasperreport.EFapsMapDataSource;
import org.efaps.esjp.erp.util.ERP;
import org.efaps.util.EFapsException;
import org.joda.time.DateTime;

import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JasperReport;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 * 
 */
@EFapsUUID("b03be6a2-6681-4e06-9629-0795fe8d830d")
@EFapsApplication("eFapsApp-Accounting")
public abstract class TransactionDataSource_Base
    extends EFapsMapDataSource
{
    /**
     * Enum used to define the keys for the map.
     */
    public enum Field
    {
        /** */
        ACCDESC("accountDescription"),
        /** */
        ACCNAME("accountName"),
        /** */
        CREDIT("amountCredit"),
        /** */
        DATE("date"),
        /** */
        DEBIT("amountDebit"),
        /** */
        DESC("description"),
        /** */
        SORT("sort");

        /**
         * key.
         */
        private final String key;

        /**
         * @param _key key
         */
        private Field(final String _key)
        {
            this.key = _key;
        }

        /**
         * Getter method for the instance variable {@link #key}.
         *
         * @return value of instance variable {@link #key}
         */
        public String getKey()
        {
            return this.key;
        }
    }

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
        final Instance instance = _parameter.getInstance();
        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.ReportNodeRoot);
        queryBldr.addWhereAttrEqValue(CIAccounting.ReportNodeRoot.ReportLink, instance.getId());
        final InstanceQuery query = queryBldr.getQuery();
        query.execute();

        if (query.next()) {
            final List<Map<String, Object>> values = new ArrayList<>();
            values.addAll(getValues(query.getCurrentValue(), true));

            // search the child to be grouped
            final QueryBuilder subQueryBldr = new QueryBuilder(CIAccounting.ReportNodeTree);
            subQueryBldr.addWhereAttrEqValue(CIAccounting.ReportNodeTree.ParentLink,
                                            query.getCurrentValue().getId());
            final MultiPrintQuery multi = subQueryBldr.getPrint();
            multi.addAttribute(CIAccounting.ReportNodeTree.Label, CIAccounting.ReportNodeTree.Position);
            multi.execute();
            while (multi.next()) {
                final Map<DateTime, Map<String, Object>> sumMap = new HashMap<>();
                final Long sort = multi.<Long>getAttribute(CIAccounting.ReportNodeTree.Position);
                for (final Map<String, Object> map : getValues(multi.getCurrentInstance(), false)) {
                    final DateTime date = (DateTime) map.get(TransactionDataSource_Base.Field.DATE.getKey());
                    map.put(TransactionDataSource_Base.Field.SORT.getKey(), sort == null ? 0 : sort);
                    map.put(TransactionDataSource_Base.Field.ACCNAME.getKey(), null);
                    map.put(TransactionDataSource_Base.Field.ACCDESC.getKey(),
                                    multi.getAttribute(CIAccounting.ReportNodeTree.Label));
                    map.put(TransactionDataSource_Base.Field.DESC.getKey(), null);
                    if (sumMap.containsKey(date)) {
                        final BigDecimal addCredit = (BigDecimal) map.get(
                                        TransactionDataSource_Base.Field.CREDIT.getKey());
                        final BigDecimal addDebit = (BigDecimal) map.get(
                                        TransactionDataSource_Base.Field.DEBIT.getKey());
                        if (addCredit != null) {
                            final BigDecimal credit = (BigDecimal) sumMap.get(date).get(
                                            TransactionDataSource_Base.Field.CREDIT.getKey());
                            sumMap.get(date).put(TransactionDataSource_Base.Field.CREDIT.getKey(),
                                            addCredit.add(credit == null ? BigDecimal.ZERO : credit));
                        }
                        if (addDebit != null) {
                            final BigDecimal debit = (BigDecimal) sumMap.get(date).get(
                                            TransactionDataSource_Base.Field.DEBIT.getKey());
                            sumMap.get(date).put(TransactionDataSource_Base.Field.DEBIT.getKey(),
                                            addDebit.add(debit == null ? BigDecimal.ZERO : debit));
                        }
                    } else {
                        sumMap.put(date, map);
                    }
                }
                values.addAll(sumMap.values());
            }

            Collections.sort(values, new Comparator<Map<String, Object>>() {

                @Override
                public int compare(final Map<String, Object> _o1,
                                   final Map<String, Object> _o2)
                {
                    final DateTime date1 = (DateTime) _o1.get(TransactionDataSource_Base.Field.DATE.getKey());
                    final DateTime date2 = (DateTime) _o2.get(TransactionDataSource_Base.Field.DATE.getKey());
                    final int ret;
                    if (date1.isEqual(date2)) {
                        final Long sort1 = (Long) _o1.get(TransactionDataSource_Base.Field.SORT.getKey());
                        final Long sort2 = (Long) _o2.get(TransactionDataSource_Base.Field.SORT.getKey());
                        ret = sort1.compareTo(sort2);
                    } else {
                        ret = date1.compareTo(date2);
                    }
                    return ret;
                }
            });
            getValues().addAll(values);

            final SystemConfiguration config = ERP.getSysConfig();
            if (config != null) {
                final String companyName = ERP.COMPANY_NAME.get();
                final String companyTaxNumb = ERP.COMPANY_TAX.get();

                if (companyName != null && companyTaxNumb != null && !companyName.isEmpty() && !companyTaxNumb.isEmpty()) {
                    _jrParameters.put("CompanyName", companyName);
                    _jrParameters.put("CompanyTaxNum", companyTaxNumb);
                }
            }
        }
    }


    /**
     * @param _instance Instane of the parent node
     * @param _sortable must the map contain the values to be sortable
     * @return  List of map values
     * @throws EFapsException on error
     */
    protected List<Map<String, Object>> getValues(final Instance _instance,
                                                  final boolean _sortable)
        throws EFapsException
    {
        final List<Map<String, Object>> values = new ArrayList<>();
        final QueryBuilder accQueryBldr = new QueryBuilder(CIAccounting.ReportNodeAccount);
        accQueryBldr.addWhereAttrEqValue(CIAccounting.ReportNodeAccount.ParentLink,
                                         _instance.getId());
        final AttributeQuery accQuery = accQueryBldr.getAttributeQuery(CIAccounting.ReportNodeAccount.AccountLink);
        final Map<Long, Long> sortMap = new HashMap<>();
        if (_sortable) {
            final QueryBuilder acc2QueryBldr = new QueryBuilder(CIAccounting.ReportNodeAccount);
            acc2QueryBldr.addWhereAttrEqValue(CIAccounting.ReportNodeAccount.ParentLink,
                                             _instance.getId());
            final MultiPrintQuery sortMulti = acc2QueryBldr.getPrint();
            sortMulti.addAttribute(CIAccounting.ReportNodeAccount.Position, CIAccounting.ReportNodeAccount.AccountLink);
            sortMulti.execute();
            while (sortMulti.next()) {
                sortMap.put(sortMulti.<Long>getAttribute(CIAccounting.ReportNodeAccount.AccountLink),
                            sortMulti.<Long>getAttribute(CIAccounting.ReportNodeAccount.Position));
            }
        }

        final QueryBuilder transQueryBldr = new QueryBuilder(CIAccounting.TransactionPositionAbstract);
        transQueryBldr.addWhereAttrInQuery(CIAccounting.TransactionPositionAbstract.AccountLink, accQuery);
        final MultiPrintQuery multi = transQueryBldr.getPrint();
        final SelectBuilder descSel = new SelectBuilder()
            .linkto(CIAccounting.TransactionPositionAbstract.TransactionLink)
            .attribute(CIAccounting.Transaction.Description);
        final SelectBuilder dateSel = new SelectBuilder()
            .linkto(CIAccounting.TransactionPositionAbstract.TransactionLink)
            .attribute(CIAccounting.Transaction.Date);
        final SelectBuilder accNameSel = new SelectBuilder()
            .linkto(CIAccounting.TransactionPositionAbstract.AccountLink)
            .attribute(CIAccounting.AccountAbstract.Name);
        final SelectBuilder accDescSel = new SelectBuilder()
            .linkto(CIAccounting.TransactionPositionAbstract.AccountLink)
            .attribute(CIAccounting.AccountAbstract.Description);
        multi.addSelect(descSel, dateSel, accNameSel, accDescSel);
        multi.addAttribute(CIAccounting.TransactionPositionAbstract.Amount,
                        CIAccounting.TransactionPositionAbstract.CurrencyLink,
                        CIAccounting.TransactionPositionAbstract.RateAmount,
                        CIAccounting.TransactionPositionAbstract.RateCurrencyLink,
                        CIAccounting.TransactionPositionAbstract.AccountLink);
        multi.execute();
        while (multi.next()) {
            final Map<String, Object> map = new HashMap<>();
            values.add(map);
            final boolean credit = CIAccounting.TransactionPositionCredit.getType().equals(
                            multi.getCurrentInstance().getType());
            map.put(TransactionDataSource_Base.Field.DESC.getKey(), multi.getSelect(descSel));
            map.put(credit ? TransactionDataSource_Base.Field.CREDIT.getKey()
                            : TransactionDataSource_Base.Field.DEBIT.getKey(),
                            multi.getAttribute(CIAccounting.TransactionPositionAbstract.Amount));
            map.put(TransactionDataSource_Base.Field.DATE.getKey(), multi.getSelect(dateSel));
            map.put(TransactionDataSource_Base.Field.ACCNAME.getKey(), multi.getSelect(accNameSel));
            map.put(TransactionDataSource_Base.Field.ACCDESC.getKey(), multi.getSelect(accDescSel));
            if (_sortable) {
                final Long sort = sortMap.get(multi.getAttribute(
                                CIAccounting.TransactionPositionAbstract.AccountLink));
                map.put(TransactionDataSource_Base.Field.SORT.getKey(), sort == null ? 0 : sort);
            }
        }
        return values;
    }
}
