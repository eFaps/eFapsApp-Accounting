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

import org.efaps.admin.common.SystemConfiguration;
import org.efaps.admin.dbproperty.DBProperties;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Parameter.ParameterValues;
import org.efaps.admin.event.Return;
import org.efaps.admin.event.Return.ReturnValues;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.CachedPrintQuery;
import org.efaps.db.Instance;
import org.efaps.db.InstanceQuery;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.PrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.esjp.accounting.Period;
import org.efaps.esjp.accounting.util.Accounting.ReportBalancePositionConfig;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.common.jasperreport.AbstractDynamicReport;
import org.efaps.esjp.erp.FilteredReport;
import org.efaps.esjp.erp.util.ERP;
import org.efaps.util.EFapsException;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sf.dynamicreports.jasper.builder.JasperReportBuilder;
import net.sf.dynamicreports.report.base.expression.AbstractSimpleExpression;
import net.sf.dynamicreports.report.builder.DynamicReports;
import net.sf.dynamicreports.report.builder.column.TextColumnBuilder;
import net.sf.dynamicreports.report.builder.style.ConditionalStyleBuilder;
import net.sf.dynamicreports.report.builder.style.StyleBuilder;
import net.sf.dynamicreports.report.definition.ReportParameters;
import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
/**
 * TODO comment!
 *
 * @author The eFaps Team
 * @version $Id: ReportBalanceReport_Base.java 14531 2014-11-27 16:52:11Z
 *          jan@moxter.net $
 */
@EFapsUUID("cbbcab6e-6c56-480d-baee-6ffbf4eb093c")
@EFapsApplication("eFapsApp-Accounting")
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

    @Override
    protected Object getDefaultValue(final Parameter _parameter,
                                     final String _field,
                                     final String _type,
                                     final String _default)
        throws EFapsException
    {
        Object ret = null;
        if ("DateTime".equalsIgnoreCase(_type) && "dateFrom".equals(_field)) {
            final Instance periodInst = new Period().evaluateCurrentPeriod(_parameter);
            final PrintQuery print = new CachedPrintQuery(periodInst, Period.CACHEKEY);
            print.addAttribute(CIAccounting.Period.FromDate, CIAccounting.Period.ToDate);
            print.execute();
            ret = print.<DateTime>getAttribute(CIAccounting.Period.FromDate);
        } else if ("DateTime".equalsIgnoreCase(_type) && "dateTo".equals(_field)) {
            final Instance periodInst = new Period().evaluateCurrentPeriod(_parameter);
            final PrintQuery print = new CachedPrintQuery(periodInst, Period.CACHEKEY);
            print.addAttribute(CIAccounting.Period.FromDate, CIAccounting.Period.ToDate);
            print.execute();
            ret = print.<DateTime>getAttribute(CIAccounting.Period.ToDate);
        } else {
            ret = super.getDefaultValue(_parameter, _field, _type, _default);
        }
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
                            CIAccounting.ReportBalancePositionAbstract.Position,
                            CIAccounting.ReportBalancePositionAbstract.Config);
            multi.execute();
            while (multi.next()) {
                final DetailBean detail = new DetailBean()
                                .setInstance(multi.getCurrentInstance())
                                .setLabel(multi.<String>getAttribute(CIAccounting.ReportBalancePositionAbstract.Label))
                                .setPosition(multi
                                                .<Integer>getAttribute(CIAccounting.ReportBalancePositionAbstract.Position))
                                .setConfigs(multi.<List<ReportBalancePositionConfig>>getAttribute(
                                                CIAccounting.ReportBalancePositionAbstract.Config))
                                .setFilterMap(getFilteredReport().getFilterMap(_parameter));
                ;
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
                    data.setAsset(detail);
                    data.setAssetAmount(detail.getAmount(map));
                }
                if (liabiltiesIter.hasNext()) {
                    final DetailBean detail = liabiltiesIter.next();
                    data.setLiability(detail);
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
                            DynamicReports.type.stringType()).setWidth(250);

            final TextColumnBuilder<BigDecimal> assetAmountColumn = DynamicReports.col.column("assetAmount",
                            DynamicReports.type.bigDecimalType());
            final TextColumnBuilder<String> liabiltyLabelColum = DynamicReports.col.column("liabiltyLabel",
                            DynamicReports.type.stringType()).setWidth(250);

            final TextColumnBuilder<BigDecimal> liabiltyAmountColumn = DynamicReports.col.column("liabiltyAmount",
                            DynamicReports.type.bigDecimalType());

            final StyleBuilder assetStyle;
            final StyleBuilder liabilityStyle;
            switch (getExType()) {
                case PDF:
                    assetStyle = getColumnStyle4Pdf(_parameter);
                    liabilityStyle = getColumnStyle4Pdf(_parameter);
                    break;
                case EXCEL:
                    assetStyle = getColumnStyle4Excel(_parameter);
                    liabilityStyle = getColumnStyle4Excel(_parameter);
                    break;
                default:
                    assetStyle = getColumnStyle4Html(_parameter);
                    liabilityStyle = getColumnStyle4Html(_parameter);
                    break;
            };
            final ConditionalStyleBuilder assetTitleCondition = DynamicReports.stl.conditionalStyle(
                            new TitleConditionExpression("assetIsTitle")).bold();
            final ConditionalStyleBuilder assetTotalCondition = DynamicReports.stl.conditionalStyle(
                            new TitleConditionExpression("assetIsTotal")).boldItalic().underline();
            assetStyle.conditionalStyles(assetTitleCondition, assetTotalCondition);

            final ConditionalStyleBuilder liabilityTitleCondition = DynamicReports.stl.conditionalStyle(
                            new TitleConditionExpression("liabilityIsTitle")).bold();
            final ConditionalStyleBuilder liabilityTotalCondition = DynamicReports.stl.conditionalStyle(
                            new TitleConditionExpression("liabilityIsTotal")).boldItalic().underline();
            liabilityStyle.conditionalStyles(liabilityTitleCondition, liabilityTotalCondition);

            assetLabelColum.setStyle(assetStyle);
            assetAmountColumn.setStyle(assetStyle);
            liabiltyLabelColum.setStyle(liabilityStyle);
            liabiltyAmountColumn.setStyle(liabilityStyle);

            _builder.addField("assetIsTitle", Boolean.class);
            _builder.addField("liabilityIsTitle", Boolean.class);
            _builder.addField("assetIsTotal", Boolean.class);
            _builder.addField("liabilityIsTotal", Boolean.class);

            _builder.addColumn(assetLabelColum, assetAmountColumn, liabiltyLabelColum, liabiltyAmountColumn);
        }

        @Override
        protected StyleBuilder getColumnStyle4Pdf(final Parameter _parameter)
            throws EFapsException
        {
            return DynamicReports.stl.style().setPadding(DynamicReports.stl.padding(2));
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

        @Override
        protected void add2ReportParameter(final Parameter _parameter)
            throws EFapsException
        {
            super.add2ReportParameter(_parameter);
            final SystemConfiguration config = ERP.getSysConfig();
            if (config != null) {
                final String companyName = ERP.COMPANY_NAME.get();
                final String companyTaxNum = ERP.COMPANY_TAX.get();
                if (companyName != null && !companyName.isEmpty()) {
                    getParameters().put("CompanyName", companyName);
                }
                if (companyTaxNum != null && !companyTaxNum.isEmpty()) {
                    getParameters().put("CompanyTaxNum", companyTaxNum);
                }
            }
            final Map<String, Object> filtermap = getFilteredReport().getFilterMap(_parameter);
            if (filtermap.containsKey("dateFrom")) {
                final DateTime date = (DateTime) filtermap.get("dateFrom");
                getParameters().put("DateFrom", date);
            }
            if (filtermap.containsKey("dateTo")) {
                final DateTime date = (DateTime) filtermap.get("dateTo");
                getParameters().put("DateTo", date);
            }

            final PrintQuery print = new PrintQuery(_parameter.getInstance());
            print.addAttribute(CIAccounting.ReportAbstract.Name);
            final SelectBuilder sel = SelectBuilder.get().linkto(CIAccounting.ReportAbstract.PeriodLink)
                            .attribute(CIAccounting.Period.Name);
            print.addSelect(sel);
            print.execute();
            getParameters().put("ReportName", print.getAttribute(CIAccounting.ReportAbstract.Name));
            getParameters().put("PeriodName", print.getSelect(sel));
        }
    }

    public static class DetailBean
    {

        private Map<String, Object> filterMap;

        private Instance instance;

        private String label;

        private Integer position;

        private BigDecimal amount = null;

        private List<ReportBalancePositionConfig> configs;

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
            if (this.amount == null && !isTitleOnly()) {
                this.amount = BigDecimal.ZERO;
                final QueryBuilder attrQueryBldr = new QueryBuilder(CIAccounting.ReportBalancePosition2Account);
                attrQueryBldr.addWhereAttrEqValue(CIAccounting.ReportBalancePosition2Account.FromLink, getInstance());
                final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.TransactionPositionAbstract);
                queryBldr.addWhereAttrInQuery(CIAccounting.TransactionPositionAbstract.AccountLink,
                                attrQueryBldr.getAttributeQuery(CIAccounting.ReportBalancePosition2Account.ToLink));
                add2QueryBldr(queryBldr);
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
         * @param _parameter Parameter as passed by the eFaps API
         * @param _queryBldr QueryBuilder to add to
         * @throws EFapsException on error
         */
        protected void add2QueryBldr(final QueryBuilder _queryBldr)
            throws EFapsException
        {
            final QueryBuilder attrQueryBldr = new QueryBuilder(CIAccounting.Transaction);
            if (getFilterMap().containsKey("dateFrom")) {
                final DateTime date = (DateTime) getFilterMap().get("dateFrom");
                attrQueryBldr.addWhereAttrGreaterValue(CIAccounting.Transaction.Date,
                                date.withTimeAtStartOfDay().minusSeconds(1));
            }
            if (getFilterMap().containsKey("dateTo")) {
                final DateTime date = (DateTime) getFilterMap().get("dateTo");
                attrQueryBldr.addWhereAttrLessValue(CIAccounting.Transaction.Date,
                                date.withTimeAtStartOfDay().plusDays(1));
            }
            _queryBldr.addWhereAttrInQuery(CIAccounting.TransactionPositionAbstract.TransactionLink,
                            attrQueryBldr.getAttributeQuery(CIAccounting.ReportBalancePosition2Account.ToLink));
        }

        public boolean isTitleOnly()
        {
            return getConfigs().contains(ReportBalancePositionConfig.TITLEONLY);
        }

        public boolean isTotal()
        {
            return getConfigs().contains(ReportBalancePositionConfig.TOTAL);
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

        /**
         * Getter method for the instance variable {@link #configs}.
         *
         * @return value of instance variable {@link #configs}
         */
        public List<ReportBalancePositionConfig> getConfigs()
        {
            List<ReportBalancePositionConfig> ret;
            if (this.configs == null) {
                ret = Collections.<ReportBalancePositionConfig>emptyList();
            } else {
                ret = this.configs;
            }
            return ret;
        }

        /**
         * Setter method for instance variable {@link #configs}.
         *
         * @param _configs value for instance variable {@link #configs}
         */
        public DetailBean setConfigs(final List<ReportBalancePositionConfig> _configs)
        {
            this.configs = _configs;
            return this;
        }

        /**
         * Getter method for the instance variable {@link #filterMap}.
         *
         * @return value of instance variable {@link #filterMap}
         */
        public Map<String, Object> getFilterMap()
        {
            return this.filterMap;
        }

        /**
         * Setter method for instance variable {@link #filterMap}.
         *
         * @param _filterMap value for instance variable {@link #filterMap}
         */
        public DetailBean setFilterMap(final Map<String, Object> _filterMap)
        {
            this.filterMap = _filterMap;
            return this;
        }
    }

    public static class DataBean
    {

        private DetailBean asset;

        private DetailBean liability;

        private BigDecimal assetAmount;

        private BigDecimal liabilityAmount;

        /**
         * Getter method for the instance variable {@link #assetLabel}.
         *
         * @return value of instance variable {@link #assetLabel}
         */
        public String getAssetLabel()
        {
            return getAsset() != null ? getAsset().getLabel() : null;
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
            return getLiability() != null ? getLiability().getLabel() : null;
        }

        /**
         * Getter method for the instance variable {@link #liabiltyAmount}.
         *
         * @return value of instance variable {@link #liabiltyAmount}
         */
        public BigDecimal getLiabiltyAmount()
        {
            return this.liabilityAmount;
        }

        /**
         * Setter method for instance variable {@link #liabiltyAmount}.
         *
         * @param _liabiltyAmount value for instance variable
         *            {@link #liabiltyAmount}
         */
        public void setLiabiltyAmount(final BigDecimal _liabiltyAmount)
        {
            this.liabilityAmount = _liabiltyAmount;
        }

        /**
         * Getter method for the instance variable {@link #asset}.
         *
         * @return value of instance variable {@link #asset}
         */
        public DetailBean getAsset()
        {
            return this.asset;
        }

        /**
         * Setter method for instance variable {@link #asset}.
         *
         * @param _asset value for instance variable {@link #asset}
         */
        public void setAsset(final DetailBean _asset)
        {
            this.asset = _asset;
        }

        /**
         * Getter method for the instance variable {@link #liability}.
         *
         * @return value of instance variable {@link #liability}
         */
        public DetailBean getLiability()
        {
            return this.liability;
        }

        /**
         * Setter method for instance variable {@link #liability}.
         *
         * @param _liability value for instance variable {@link #liability}
         */
        public void setLiability(final DetailBean _liability)
        {
            this.liability = _liability;
        }

        public boolean getAssetIsTitle()
        {
            return getAsset() != null ? getAsset().isTitleOnly() : false;
        }

        public boolean getLiabilityIsTitle()
        {
            return getLiability() != null ? getLiability().isTitleOnly() : false;
        }

        public boolean getAssetIsTotal()
        {
            return getAsset() != null ? getAsset().isTotal() : false;
        }

        public boolean getLiabilityIsTotal()
        {
            return getLiability() != null ? getLiability().isTotal() : false;
        }
    }

    public static class TitleConditionExpression
        extends AbstractSimpleExpression<Boolean>
    {

        private static final long serialVersionUID = 1L;
        private final String fieldName;

        /**
         * @param _string
         */
        public TitleConditionExpression(final String _fieldName)
        {
            this.fieldName = _fieldName;
        }

        @Override
        public Boolean evaluate(final ReportParameters reportParameters)
        {
            return reportParameters.getValue(this.fieldName);
        }
    }

    public static class TotalConditionExpression
        extends AbstractSimpleExpression<Boolean>
    {

        private static final long serialVersionUID = 1L;
        private final String fieldName;

        /**
         * @param _string
         */
        public TotalConditionExpression(final String _fieldName)
        {
            this.fieldName = _fieldName;
        }

        @Override
        public Boolean evaluate(final ReportParameters reportParameters)
        {
            return reportParameters.getValue(this.fieldName);
        }
    }

}
