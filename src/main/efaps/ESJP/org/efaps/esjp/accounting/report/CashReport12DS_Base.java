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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections4.comparators.ComparatorChain;
import org.efaps.admin.dbproperty.DBProperties;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Return;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.AttributeQuery;
import org.efaps.db.Instance;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.PrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.esjp.accounting.Period;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.ci.CIContacts;
import org.efaps.esjp.ci.CIERP;
import org.efaps.esjp.ci.CIFormAccounting;
import org.efaps.esjp.ci.CISales;
import org.efaps.esjp.common.uiform.Field;
import org.efaps.util.EFapsException;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JasperReport;
/**
 * TODO comment!
 *
 * @author The eFaps Team
 * @version $Id: CashReport11DS_Base.java 13378 2014-07-21 19:12:12Z
 *          jan@moxter.net $
 */
@EFapsUUID("eb9fc10d-915a-4a29-8e40-513f077aae7d")
@EFapsApplication("eFapsApp-Accounting")
public abstract class CashReport12DS_Base
    extends AbstractCashReportDS
{
    /**
     * Logger for this instance.
     */
    private static Logger LOG = LoggerFactory.getLogger(CashReport12DS.class);


    public Return getAccountFieldValue(final Parameter _parameter) throws EFapsException {
        final Field field = new Field()
        {

            @Override
            protected void add2QueryBuilder4List(final Parameter _parameter,
                                                 final QueryBuilder _queryBldr)
                throws EFapsException
            {
                final Instance periodInst = new Period().evaluateCurrentPeriod(_parameter);
                _queryBldr.addWhereAttrEqValue(CIAccounting.Period2Account.PeriodLink, periodInst);
            }
        };
        return field.dropDownFieldValue(_parameter);
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
        super.init(_jasperReport, _parameter, _parentSource, _jrParameters);

        final DateTime dateFrom = new DateTime(_parameter.getParameterValue(
                        CIFormAccounting.Accounting_PReportCash12ReportForm.dateFrom.name));
        final DateTime dateTo = new DateTime(_parameter.getParameterValue(
                        CIFormAccounting.Accounting_PReportCash12ReportForm.dateTo.name));

        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.TransactionPositionAbstract);

        final QueryBuilder transAttrQueryBldr = new QueryBuilder(CIAccounting.TransactionAbstract);
        transAttrQueryBldr.addWhereAttrLessValue(CIAccounting.TransactionAbstract.Date,
                        dateTo.withTimeAtStartOfDay().plusDays(1));

        final AttributeQuery transAttrQuery = transAttrQueryBldr.getAttributeQuery(CIAccounting.TransactionAbstract.ID);
        queryBldr.addWhereAttrInQuery(CIAccounting.TransactionPositionAbstract.TransactionLink, transAttrQuery);

        final Instance relinst = Instance.get(_parameter.getParameterValue(
                        CIFormAccounting.Accounting_PReportCash12ReportForm.account.name));
        final PrintQuery print = new PrintQuery(relinst);
        final SelectBuilder selAccInst = SelectBuilder.get().linkto(CIAccounting.Period2Account.FromAccountAbstractLink)
                        .instance();
        final SelectBuilder selSalesAccInst = SelectBuilder.get()
                        .linkto(CIAccounting.Period2Account.SalesAccountLink)
                        .instance();
        final SelectBuilder selSalesAccName = SelectBuilder.get()
                        .linkto(CIAccounting.Period2Account.SalesAccountLink)
                        .attribute(CISales.AccountAbstract.Name);
        print.addSelect(selAccInst, selSalesAccInst, selSalesAccName);
        print.execute();
        final Instance accInst = print.<Instance>getSelect(selAccInst);
        final Instance salesAccInst = print.<Instance>getSelect(selSalesAccInst);
        _jrParameters.put("AccountName", print.getSelect(selSalesAccName));

        if (salesAccInst.getType().isKindOf(CISales.AccountCashDesk.getType())) {
            final SelectBuilder selSalesAccFIValue = SelectBuilder.get()
                            .linkto(CISales.AccountCashDesk.FinancialInstitute)
                            .attribute(CISales.AttributeDefinitionFinancialInstitute.Value);
            final SelectBuilder selSalesAccFImapKey = SelectBuilder.get()
                            .linkto(CISales.AccountCashDesk.FinancialInstitute)
                            .attribute(CISales.AttributeDefinitionFinancialInstitute.MappingKey);

            final PrintQuery salesAccPrint = new PrintQuery(salesAccInst);
            salesAccPrint.addSelect(selSalesAccFIValue, selSalesAccFImapKey);
            salesAccPrint.execute();
            final String fiMapKey = salesAccPrint.getSelect(selSalesAccFImapKey);
            final String fiValue = salesAccPrint.getSelect(selSalesAccFIValue);
            _jrParameters.put("Banc", fiMapKey + " - " + fiValue);
        }

        final QueryBuilder attrQueryBldr = new QueryBuilder(CIAccounting.TransactionPositionAbstract);
        attrQueryBldr.addWhereAttrEqValue(CIAccounting.TransactionPositionAbstract.AccountLink, accInst);

        queryBldr.addWhereAttrInQuery(CIAccounting.TransactionPositionAbstract.TransactionLink,
                            attrQueryBldr.getAttributeQuery(CIAccounting.TransactionPositionAbstract.TransactionLink));
        queryBldr.addWhereAttrNotEqValue(CIAccounting.TransactionPositionAbstract.AccountLink, accInst);

        final MultiPrintQuery multi = queryBldr.getPrint();
        final SelectBuilder selAcc = SelectBuilder.get().linkto(CIAccounting.TransactionPositionAbstract.AccountLink);
        final SelectBuilder selAccName = new SelectBuilder(selAcc).attribute(CIAccounting.AccountAbstract.Name);
        final SelectBuilder selAccDescr = new SelectBuilder(selAcc).attribute(CIAccounting.AccountAbstract.Description);

        final SelectBuilder selTrans = SelectBuilder.get().linkto(
                        CIAccounting.TransactionPositionAbstract.TransactionLink);
        final SelectBuilder selTransInst = SelectBuilder.get().linkto(
                        CIAccounting.TransactionPositionAbstract.TransactionLink).instance();
        final SelectBuilder selTransDescr = new SelectBuilder(selTrans)
                        .attribute(CIAccounting.TransactionAbstract.Description);
        final SelectBuilder selTransDate = new SelectBuilder(selTrans).attribute(CIAccounting.TransactionAbstract.Date);

        multi.addSelect(selAccName, selAccDescr, selTransInst, selTransDescr, selTransDate);
        multi.addAttribute(CIAccounting.TransactionPositionAbstract.Amount);
        multi.execute();
        final List<DataBean> values = new ArrayList<>();
        final Map<Instance, Set<DataBean412>> map = new HashMap<>();
        final DataBean412 carryOver = new DataBean412();
        carryOver.setAmount(BigDecimal.ZERO);
        carryOver.setTransDescr(DBProperties.getProperty(CashReport12DS.class.getName() + ".CarryOver"));
        while (multi.next()) {
            final DateTime date = multi.<DateTime>getSelect(selTransDate);
            final BigDecimal amount = multi.<BigDecimal>getAttribute(CIAccounting.TransactionPositionAbstract.Amount);
            if (date.isBefore(dateFrom)) {
                carryOver.setAmount(carryOver.getAmount().add(amount));
            } else {
                final Instance transInst = multi.getSelect(selTransInst);
                final DataBean412 bean = new DataBean412();
                bean.setTransDate(date);
                bean.setTransDescr(multi.<String>getSelect(selTransDescr));
                bean.setAccName(multi.<String>getSelect(selAccName));
                bean.setAccDescr(multi.<String>getSelect(selAccDescr));
                bean.setAmount(amount);
                values.add(bean);
                Set<DataBean412> beans;
                if (!map.containsKey(transInst)) {
                    map.put(transInst, new HashSet<DataBean412>());
                }
                beans = map.get(transInst);
                beans.add(bean);
            }
        }

        final QueryBuilder relQueryBldr = new QueryBuilder(CIAccounting.Transaction2ERPDocument);
        relQueryBldr.addWhereAttrInQuery(CIAccounting.Transaction2ERPDocument.FromLink, transAttrQuery);
        final MultiPrintQuery relMulti = relQueryBldr.getPrint();
        final SelectBuilder transSel = SelectBuilder.get().linkto(CIAccounting.Transaction2ERPDocument.FromLink)
                        .instance();
        final SelectBuilder docContactNameSel = SelectBuilder.get()
                        .linkto(CIAccounting.Transaction2ERPDocument.ToLinkAbstract)
                        .linkto(CIERP.DocumentAbstract.Contact)
                        .attribute(CIContacts.ContactAbstract.Name);
        final SelectBuilder docNameSel = SelectBuilder.get()
                        .linkto(CIAccounting.Transaction2ERPDocument.ToLinkAbstract)
                        .attribute(CIERP.DocumentAbstract.Name);
        relMulti.addSelect(transSel, docNameSel, docContactNameSel);
        relMulti.execute();
        while (relMulti.next()) {
            final Instance transInst = relMulti.getSelect(transSel);
            if (map.containsKey(transInst)) {
                final String docContactName = relMulti.getSelect(docContactNameSel);
                final String docName = relMulti.getSelect(docNameSel);
                final Set<DataBean412> beans = map.get(transInst);
                for (final DataBean412 bean : beans) {
                    bean.addDocContactName(docContactName);
                    bean.addCode(docName);
                }
            }
        }
        final ComparatorChain<DataBean> chain = new ComparatorChain<>();
        chain.addComparator(new Comparator<DataBean>() {

            @Override
            public int compare(final DataBean _arg0,
                               final DataBean _arg1)
            {
                return _arg0.getTransDate().compareTo(_arg1.getTransDate());
            }

        });
        Collections.sort(values, chain);
        if (carryOver.getAmount().compareTo(BigDecimal.ZERO) != 0) {
            values.add(0, carryOver);
        }
        setData(values);
    }



    public static class DataBean412
        extends DataBean
    {
        private String paymentMean;
        private String docContactName;
        private String code;

        /**
         * Getter method for the instance variable {@link #paymentMean}.
         *
         * @return value of instance variable {@link #paymentMean}
         */
        public String getPaymentMean()
        {
            return this.paymentMean;
        }

        /**
         * @param _docName
         */
        public void addCode(final String _docName)
        {
            setCode(getCode() == null ? _docName : getCode() + ", " + _docName);
        }

        /**
         * @param _docContactName
         */
        public void addDocContactName(final String _docContactName)
        {
            setDocContactName(getDocContactName() == null ? _docContactName : getDocContactName() + ", "
                            + _docContactName);
        }

        /**
         * Setter method for instance variable {@link #paymentMean}.
         *
         * @param _paymentMean value for instance variable {@link #paymentMean}
         */
        public void setPaymentMean(final String _paymentMean)
        {
            this.paymentMean = _paymentMean;
        }

        /**
         * Getter method for the instance variable {@link #docContactName}.
         *
         * @return value of instance variable {@link #docContactName}
         */
        public String getDocContactName()
        {
            return this.docContactName;
        }

        /**
         * Setter method for instance variable {@link #docContactName}.
         *
         * @param _docContactName value for instance variable {@link #docContactName}
         */
        public void setDocContactName(final String _docContactName)
        {
            this.docContactName = _docContactName;
        }

        /**
         * Getter method for the instance variable {@link #code}.
         *
         * @return value of instance variable {@link #code}
         */
        public String getCode()
        {
            return this.code;
        }

        /**
         * Setter method for instance variable {@link #code}.
         *
         * @param _code value for instance variable {@link #code}
         */
        public void setCode(final String _code)
        {
            this.code = _code;
        }
    }
}
