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

import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.sf.dynamicreports.jasper.builder.JasperReportBuilder;
import net.sf.dynamicreports.report.base.expression.AbstractSimpleExpression;
import net.sf.dynamicreports.report.builder.DynamicReports;
import net.sf.dynamicreports.report.builder.column.TextColumnBuilder;
import net.sf.dynamicreports.report.builder.group.CustomGroupBuilder;
import net.sf.dynamicreports.report.builder.group.GroupBuilder;
import net.sf.dynamicreports.report.constant.GroupHeaderLayout;
import net.sf.dynamicreports.report.datasource.DRDataSource;
import net.sf.dynamicreports.report.definition.ReportParameters;
import net.sf.jasperreports.engine.JRDataSource;

import org.apache.commons.collections.comparators.ComparatorChain;
import org.efaps.admin.dbproperty.DBProperties;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Parameter.ParameterValues;
import org.efaps.admin.event.Return;
import org.efaps.admin.event.Return.ReturnValues;
import org.efaps.admin.program.esjp.EFapsRevision;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.AttributeQuery;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.common.jasperreport.AbstractDynamicReport;
import org.efaps.esjp.sales.report.ComparativeReport;
import org.efaps.util.EFapsException;
import org.joda.time.DateTime;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 * @version $Id$
 */
@EFapsUUID("88456845-0a0a-4480-acf5-66829d63c076")
@EFapsRevision("$Rev$")
public abstract class DocumentDetailsReport_Base
{

    /**
     * @param _parameter Parameter as passed by the eFasp API
     * @return Return containing the file
     * @throws EFapsException on error
     */
    public Return exportReport(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final Map<?, ?> props = (Map<?, ?>) _parameter.get(ParameterValues.PROPERTIES);
        final String mime = (String) props.get("Mime");
        final AbstractDynamicReport dyRp = getReport(_parameter);
        dyRp.setFileName(DBProperties.getProperty(ComparativeReport.class.getName() + ".FileName"));
        File file = null;
        if ("xls".equalsIgnoreCase(mime)) {
            file = dyRp.getExcel(_parameter);
        } else if ("pdf".equalsIgnoreCase(mime)) {
            file = dyRp.getPDF(_parameter);
        }
        ret.put(ReturnValues.VALUES, file);
        ret.put(ReturnValues.TRUE, true);
        return ret;
    }
    /**
     * @param _parameter Parameter as passed by the eFasp API
     * @return Return containing html snipplet
     * @throws EFapsException on error
     */
    public Return generateReport(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final AbstractDynamicReport dyRp = getReport(_parameter);
        final String html = dyRp.getHtmlSnipplet(_parameter);
        ret.put(ReturnValues.SNIPLETT, html);
        return ret;
    }

    /**
     * @param _parameter Parameter as passed by the eFasp API
     * @return the report class
     * @throws EFapsException on error
     */
    public AbstractDynamicReport getReport(final Parameter _parameter)
        throws EFapsException
    {
        return new DocDetailsReport();
    }

    public static class DocDetailsReport
        extends AbstractDynamicReport
    {

        @SuppressWarnings("unchecked")
        @Override
        protected JRDataSource createDataSource(final Parameter _parameter)
            throws EFapsException
        {
            final DRDataSource dataSource = new DRDataSource("oid", "trName", "trDate", "trDesc", "acName", "acDesc",
                            "debit", "credit");
            final List<Map<String, Object>> values = new ArrayList<Map<String, Object>>();
            final QueryBuilder attrQueryBldr = new QueryBuilder(CIAccounting.Transaction2SalesDocument);
            attrQueryBldr.addWhereAttrEqValue(CIAccounting.Transaction2SalesDocument.ToLink,
                            _parameter.getInstance());
            final AttributeQuery attrQuery = attrQueryBldr
                            .getAttributeQuery(CIAccounting.Transaction2SalesDocument.FromLink);
            final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.TransactionPositionAbstract);
            queryBldr.addWhereAttrInQuery(CIAccounting.TransactionPositionAbstract.TransactionLink, attrQuery);
            final MultiPrintQuery multi = queryBldr.getPrint();
            final SelectBuilder selAcName = SelectBuilder.get()
                            .linkto(CIAccounting.TransactionPositionAbstract.AccountLink)
                            .attribute(CIAccounting.AccountAbstract.Name);
            final SelectBuilder selAcDesc = SelectBuilder.get()
                            .linkto(CIAccounting.TransactionPositionAbstract.AccountLink)
                            .attribute(CIAccounting.AccountAbstract.Description);
            final SelectBuilder selTrName = SelectBuilder.get()
                            .linkto(CIAccounting.TransactionPositionAbstract.TransactionLink)
                            .attribute(CIAccounting.Transaction.Name);
            final SelectBuilder selTrDesc = SelectBuilder.get()
                            .linkto(CIAccounting.TransactionPositionAbstract.TransactionLink)
                            .attribute(CIAccounting.Transaction.Description);
            final SelectBuilder selTrDate = SelectBuilder.get()
                            .linkto(CIAccounting.TransactionPositionAbstract.TransactionLink)
                            .attribute(CIAccounting.Transaction.Date);
            final SelectBuilder selTrOID = SelectBuilder.get()
                            .linkto(CIAccounting.TransactionPositionAbstract.TransactionLink)
                            .oid();
            multi.addSelect(selTrOID, selAcName, selAcDesc, selTrName, selTrDesc, selTrDate);
            multi.addAttribute(CIAccounting.TransactionPositionAbstract.Amount);
            multi.execute();
            while (multi.next()) {
                final Map<String, Object> map = new HashMap<String, Object>();
                map.put("oid", multi.getSelect(selTrOID));
                map.put("trName", multi.getSelect(selTrName));
                map.put("trDate", multi.<DateTime>getSelect(selTrDate).toDate());
                map.put("trDesc", multi.getSelect(selTrDesc));
                map.put("acName", multi.getSelect(selAcName));
                map.put("acDesc", multi.getSelect(selAcDesc));
                if (multi.getCurrentInstance().getType().isKindOf(CIAccounting.TransactionPositionCredit.getType())) {
                    map.put("credit", multi.<BigDecimal>getAttribute(CIAccounting.TransactionPositionAbstract.Amount)
                                    .abs());
                } else {
                    map.put("debit", multi.<BigDecimal>getAttribute(CIAccounting.TransactionPositionAbstract.Amount)
                                    .abs());
                }
                values.add(map);
            }
            final ComparatorChain comChain = new ComparatorChain();
            comChain.addComparator(new Comparator<Map<String, Object>>()
            {
                @Override
                public int compare(final Map<String, Object> _o1,
                                   final Map<String, Object> _o2)
                {
                    return String.valueOf(_o1.get("trName")).compareTo(String.valueOf(_o2.get("trName")));
                }
            });

            comChain.addComparator(new Comparator<Map<String, Object>>()
            {
                @Override
                public int compare(final Map<String, Object> _o1,
                                   final Map<String, Object> _o2)
                {
                    return ((Date) _o1.get("trDate")).compareTo(((Date) _o2.get("trDate")));
                }
            });

            comChain.addComparator(new Comparator<Map<String, Object>>()
            {
                @Override
                public int compare(final Map<String, Object> _o1,
                                   final Map<String, Object> _o2)
                {
                    return String.valueOf(_o1.get("oid")).compareTo(String.valueOf(_o2.get("oid")));
                }
            });

            comChain.addComparator(new Comparator<Map<String, Object>>()
            {
                @Override
                public int compare(final Map<String, Object> _o1,
                                   final Map<String, Object> _o2)
                {
                    return String.valueOf(_o1.get("acName")).compareTo(String.valueOf(_o2.get("acName")));
                }
            });

            Collections.sort(values, comChain);

            for (final Map<String, Object> map : values) {
                dataSource.add(map.get("oid"), map.get("trName"), map.get("trDate"), map.get("trDesc"),
                                map.get("acName"), map.get("acDesc"), map.get("debit"), map.get("credit"));
            }
            return dataSource;
        }

        @Override
        protected void addColumnDefintion(final Parameter _parameter,
                                          final JasperReportBuilder _builder)
            throws EFapsException
        {
            final TextColumnBuilder<String> trNameColumn = DynamicReports.col.column(DBProperties
                            .getProperty(DocumentDetailsReport.class.getName() + ".Column.trName"),
                            "trName", DynamicReports.type.stringType()).setWidth(50);
            final TextColumnBuilder<Date> trDateColumn = DynamicReports.col.column(DBProperties
                            .getProperty(DocumentDetailsReport.class.getName() + ".Column.trDate"),
                            "trDate", DynamicReports.type.dateType());
            final TextColumnBuilder<String> trDescColumn = DynamicReports.col.column(DBProperties
                            .getProperty(DocumentDetailsReport.class.getName() + ".Column.trDesc"),
                            "trDesc", DynamicReports.type.stringType()).setWidth(220);
            final TextColumnBuilder<String> acNameColumn = DynamicReports.col.column(DBProperties
                            .getProperty(DocumentDetailsReport.class.getName() + ".Column.acName"),
                            "acName", DynamicReports.type.stringType()).setWidth(50);
            final TextColumnBuilder<String> acDescColumn = DynamicReports.col.column(DBProperties
                            .getProperty(DocumentDetailsReport.class.getName() + ".Column.acDesc"),
                            "acDesc", DynamicReports.type.stringType()).setWidth(220);

            final TextColumnBuilder<BigDecimal> debitColumn = DynamicReports.col.column(DBProperties
                            .getProperty(DocumentDetailsReport.class.getName() + ".Column.debit"),
                            "debit", DynamicReports.type.bigDecimalType());
            final TextColumnBuilder<BigDecimal> creditColumn = DynamicReports.col.column(DBProperties
                            .getProperty(DocumentDetailsReport.class.getName() + ".Column.credit"),
                            "credit", DynamicReports.type.bigDecimalType());

            _builder.addColumn(trNameColumn, trDateColumn, trDescColumn, acNameColumn, acDescColumn, debitColumn,
                            creditColumn);

            final CustomGroupBuilder transGroup = DynamicReports.grp.group("oid", String.class)
                            .setHeaderLayout(GroupHeaderLayout.EMPTY) ;
            trNameColumn.setPrintWhenExpression(new NoRepeatedInGroupExpression(transGroup));
            trDateColumn.setPrintWhenExpression(new NoRepeatedInGroupExpression(transGroup));
            trDescColumn.setPrintWhenExpression(new NoRepeatedInGroupExpression(transGroup));

            _builder.groupBy(transGroup);
        }
    }

    public static class NoRepeatedInGroupExpression
        extends AbstractSimpleExpression<Boolean>
    {

        /**
         *
         */
        private static final long serialVersionUID = 1L;

        private final GroupBuilder<?> group;

        public NoRepeatedInGroupExpression(final GroupBuilder<?> group)
        {
            this.group = group;
        }

        public Boolean evaluate(final ReportParameters reportParameters)
        {
            final int groupNumber = DynamicReports.exp.groupRowNumber(this.group).evaluate(reportParameters);
            return groupNumber == 1;
        }
    }
}
