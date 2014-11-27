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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.sf.dynamicreports.jasper.builder.JasperReportBuilder;
import net.sf.dynamicreports.report.builder.DynamicReports;
import net.sf.dynamicreports.report.builder.column.TextColumnBuilder;
import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;

import org.efaps.admin.dbproperty.DBProperties;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Parameter.ParameterValues;
import org.efaps.admin.event.Return;
import org.efaps.admin.event.Return.ReturnValues;
import org.efaps.admin.program.esjp.EFapsRevision;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.Instance;
import org.efaps.db.InstanceQuery;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.common.jasperreport.AbstractDynamicReport;
import org.efaps.esjp.erp.FilteredReport;
import org.efaps.util.EFapsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 * @version $Id$
 */
@EFapsUUID("cbbcab6e-6c56-480d-baee-6ffbf4eb093c")
@EFapsRevision("$Rev$")
public abstract class ReportBalanceReport_Base
    extends FilteredReport
{

    /**
     * Logging instance used in this class.
     */
    protected static final Logger LOG = LoggerFactory.getLogger(ReportBalanceReport.class);

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
        dyRp.setFileName(DBProperties.getProperty(ReportBalanceReport.class.getName() + ".FileName"));
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
     * @return the report class
     * @throws EFapsException on error
     */
    protected AbstractDynamicReport getReport(final Parameter _parameter)
        throws EFapsException
    {
        return new DynBalanceReport(this);
    }

    public static class DynBalanceReport
        extends AbstractDynamicReport
    {

        /**
         * variable to report.
         */
        private final ReportBalanceReport_Base filteredReport;

        /**
         * @param _report class used
         */
        public DynBalanceReport(final ReportBalanceReport_Base _report)
        {
            this.filteredReport = _report;
        }

        @Override
        protected JRDataSource createDataSource(final Parameter _parameter)
            throws EFapsException
        {
            final List<DataBean> datasource = new ArrayList<>();

            final List<DetailBean> assets = new ArrayList<>();
            final List<DetailBean> liabilties = new ArrayList<>();
            final Map<Instance, DetailBean> map = new HashMap<>();
            final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.ReportBalancePositionAbstract);
            queryBldr.addWhereAttrEqValue(CIAccounting.ReportBalancePositionAbstract.ReportLink,
                            _parameter.getInstance());
            final MultiPrintQuery multi = queryBldr.getPrint();
            multi.addAttribute(CIAccounting.ReportBalancePositionAbstract.Label,
                            CIAccounting.ReportBalancePositionAbstract.Position);
            multi.execute();
            while (multi.next()) {
                final DetailBean detail = new DetailBean()
                        .setInstance(multi.getCurrentInstance())
                        .setLabel(multi.<String>getAttribute(CIAccounting.ReportBalancePositionAbstract.Label))
                        .setPosition(multi.<Integer>getAttribute(CIAccounting.ReportBalancePositionAbstract.Position));
                if (multi.getCurrentInstance().getType().isCIType(CIAccounting.ReportBalancePositionAsset)) {
                    assets.add(detail);
                } else {
                    liabilties.add(detail);
                }
                map.put(multi.getCurrentInstance(), detail);
            }
            Collections.sort(assets, new Comparator<DetailBean>()
            {

                @Override
                public int compare(final DetailBean _arg0,
                                   final DetailBean _arg1)
                {
                    return _arg0.getPosition().compareTo(_arg1.getPosition());
                }
            });

            Collections.sort(liabilties, new Comparator<DetailBean>()
            {

                @Override
                public int compare(final DetailBean _arg0,
                                   final DetailBean _arg1)
                {
                    return _arg0.getPosition().compareTo(_arg1.getPosition());
                }
            });

            final int size = liabilties.size() > assets.size() ? liabilties.size() : assets.size();

            final Iterator<DetailBean> assetsIter = assets.iterator();
            final Iterator<DetailBean> liabiltiesIter = liabilties.iterator();
            for (int i = 0; i < size; i++) {
                final DataBean data = new DataBean();
                if (assetsIter.hasNext()) {
                    final DetailBean detail = assetsIter.next();
                    data.setAssetLabel(detail.getLabel());
                    data.setAssetAmount(detail.getAmount(map));
                }
                if (liabiltiesIter.hasNext()) {
                    final DetailBean detail = liabiltiesIter.next();
                    data.setLiabiltyLabel(detail.getLabel());
                    data.setLiabiltyAmount(detail.getAmount(map));
                }
                datasource.add(data);
            }
            return new JRBeanCollectionDataSource(datasource);
        }

        @Override
        protected void addColumnDefintion(final Parameter _parameter,
                                          final JasperReportBuilder _builder)
            throws EFapsException
        {
            final TextColumnBuilder<String> assetLabelColum = DynamicReports.col.column("assetLabel",
                            DynamicReports.type.stringType()).setWidth(150);

            final TextColumnBuilder<BigDecimal> assetAmountColumn = DynamicReports.col.column("assetAmount",
                            DynamicReports.type.bigDecimalType());
            final TextColumnBuilder<String> liabiltyLabelColum = DynamicReports.col.column("liabiltyLabel",
                            DynamicReports.type.stringType()).setWidth(150);

            final TextColumnBuilder<BigDecimal> liabiltyAmountColumn = DynamicReports.col.column("liabiltyAmount",
                            DynamicReports.type.bigDecimalType());

            _builder.addColumn(assetLabelColum, assetAmountColumn, liabiltyLabelColum, liabiltyAmountColumn);
        }

        /**
         * Getter method for the instance variable {@link #filteredReport}.
         *
         * @return value of instance variable {@link #filteredReport}
         */
        public ReportBalanceReport_Base getFilteredReport()
        {
            return this.filteredReport;
        }
    }

    public static class DetailBean
    {

        private Instance instance;

        private String label;

        private Integer position;

        private BigDecimal amount = null;

        /**
         * Getter method for the instance variable {@link #label}.
         *
         * @return value of instance variable {@link #label}
         */
        public String getLabel()
        {
            return this.label;
        }

        /**
         * @return
         */
        public BigDecimal getAmount(final Map<Instance, DetailBean> _map)
            throws EFapsException
        {
            if (this.amount == null) {
                this.amount = BigDecimal.ZERO;
                final QueryBuilder attrQueryBldr = new QueryBuilder(CIAccounting.ReportBalancePosition2Account);
                attrQueryBldr.addWhereAttrEqValue(CIAccounting.ReportBalancePosition2Account.FromLink, getInstance());
                final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.TransactionPositionAbstract);
                queryBldr.addWhereAttrInQuery(CIAccounting.TransactionPositionAbstract.AccountLink,
                                attrQueryBldr.getAttributeQuery(CIAccounting.ReportBalancePosition2Account.ToLink));
                final MultiPrintQuery multi = queryBldr.getPrint();
                multi.addAttribute(CIAccounting.TransactionPositionAbstract.Amount);
                multi.execute();
                while (multi.next()) {
                    this.amount = this.amount.add(multi
                                    .<BigDecimal>getAttribute(CIAccounting.TransactionPositionAbstract.Amount));
                }

                final QueryBuilder attrQueryBldr2 = new QueryBuilder(CIAccounting.ReportBalancePosition2Position);
                attrQueryBldr2.addWhereAttrEqValue(CIAccounting.ReportBalancePosition2Position.FromLink, getInstance());

                final QueryBuilder queryBldr2 = new QueryBuilder(CIAccounting.ReportBalancePositionAbstract);
                queryBldr2.addWhereAttrInQuery(CIAccounting.ReportBalancePositionAbstract.ID,
                                attrQueryBldr2.getAttributeQuery(CIAccounting.ReportBalancePosition2Position.ToLink));
                final InstanceQuery query = queryBldr2.getQuery();
                query.execute();
                while (query.next()) {
                    final DetailBean bean = _map.get(query.getCurrentValue());
                    this.amount = this.amount.add(bean.getAmount(_map));
                }
            }
            return this.amount;
        }

        /**
         * Setter method for instance variable {@link #label}.
         *
         * @param _label value for instance variable {@link #label}
         */
        public DetailBean setLabel(final String _label)
        {
            this.label = _label;
            return this;
        }

        /**
         * Getter method for the instance variable {@link #position}.
         *
         * @return value of instance variable {@link #position}
         */
        public Integer getPosition()
        {
            return this.position;
        }

        /**
         * Setter method for instance variable {@link #position}.
         *
         * @param _position value for instance variable {@link #position}
         */
        public DetailBean setPosition(final Integer _position)
        {
            this.position = _position;
            return this;
        }

        /**
         * Getter method for the instance variable {@link #instance}.
         *
         * @return value of instance variable {@link #instance}
         */
        public Instance getInstance()
        {
            return this.instance;
        }

        /**
         * Setter method for instance variable {@link #instance}.
         *
         * @param _instance value for instance variable {@link #instance}
         */
        public DetailBean setInstance(final Instance _instance)
        {
            this.instance = _instance;
            return this;
        }
    }

    public static class DataBean
    {

        private String assetLabel;
        private BigDecimal assetAmount;
        private String liabiltyLabel;
        private BigDecimal liabiltyAmount;

        /**
         * Getter method for the instance variable {@link #assetLabel}.
         *
         * @return value of instance variable {@link #assetLabel}
         */
        public String getAssetLabel()
        {
            return this.assetLabel;
        }

        /**
         * Setter method for instance variable {@link #assetLabel}.
         *
         * @param _assetLabel value for instance variable {@link #assetLabel}
         */
        public void setAssetLabel(final String _assetLabel)
        {
            this.assetLabel = _assetLabel;
        }

        /**
         * Getter method for the instance variable {@link #assetAmount}.
         *
         * @return value of instance variable {@link #assetAmount}
         */
        public BigDecimal getAssetAmount()
        {
            return this.assetAmount;
        }

        /**
         * Setter method for instance variable {@link #assetAmount}.
         *
         * @param _assetAmount value for instance variable {@link #assetAmount}
         */
        public void setAssetAmount(final BigDecimal _assetAmount)
        {
            this.assetAmount = _assetAmount;
        }

        /**
         * Getter method for the instance variable {@link #liabiltyLabel}.
         *
         * @return value of instance variable {@link #liabiltyLabel}
         */
        public String getLiabiltyLabel()
        {
            return this.liabiltyLabel;
        }

        /**
         * Setter method for instance variable {@link #liabiltyLabel}.
         *
         * @param _liabiltyLabel value for instance variable
         *            {@link #liabiltyLabel}
         */
        public void setLiabiltyLabel(final String _liabiltyLabel)
        {
            this.liabiltyLabel = _liabiltyLabel;
        }

        /**
         * Getter method for the instance variable {@link #liabiltyAmount}.
         *
         * @return value of instance variable {@link #liabiltyAmount}
         */
        public BigDecimal getLiabiltyAmount()
        {
            return this.liabiltyAmount;
        }

        /**
         * Setter method for instance variable {@link #liabiltyAmount}.
         *
         * @param _liabiltyAmount value for instance variable
         *            {@link #liabiltyAmount}
         */
        public void setLiabiltyAmount(final BigDecimal _liabiltyAmount)
        {
            this.liabiltyAmount = _liabiltyAmount;
        }
    }
}
