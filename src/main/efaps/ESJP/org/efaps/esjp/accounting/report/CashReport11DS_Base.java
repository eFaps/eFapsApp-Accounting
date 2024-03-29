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
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.comparators.ComparatorChain;
import org.efaps.admin.dbproperty.DBProperties;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.Instance;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.esjp.accounting.util.AccountingSettings;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.ci.CIFormAccounting;
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
public abstract class CashReport11DS_Base
    extends AbstractCashReportDS
{
    /**
     * Logger for this instance.
     */
    private static Logger LOG = LoggerFactory.getLogger(CashReport11DS.class);

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
                        CIFormAccounting.Accounting_PReportCash11ReportForm.dateFrom.name));
        final DateTime dateTo = new DateTime(_parameter.getParameterValue(
                        CIFormAccounting.Accounting_PReportCash11ReportForm.dateTo.name));

        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.TransactionPositionAbstract);

        final QueryBuilder transAttrQueryBldr = new QueryBuilder(CIAccounting.TransactionAbstract);
        transAttrQueryBldr.addWhereAttrLessValue(CIAccounting.TransactionAbstract.Date,
                        dateTo.withTimeAtStartOfDay().plusDays(1));
        queryBldr.addWhereAttrInQuery(CIAccounting.TransactionPositionAbstract.TransactionLink,
                        transAttrQueryBldr.getAttributeQuery(CIAccounting.TransactionAbstract.ID));

        final List<Instance> accInsts = getAccountInst(_parameter, AccountingSettings.PERIOD_REPORT11ACCOUNT);
        if (accInsts.isEmpty()) {
            LOG.error("Missing configuration '{}' for this report.", AccountingSettings.PERIOD_REPORT11ACCOUNT);
        } else {
            final QueryBuilder attrQueryBldr = new QueryBuilder(CIAccounting.TransactionPositionAbstract);
            attrQueryBldr.addWhereAttrEqValue(CIAccounting.TransactionPositionAbstract.AccountLink, accInsts.toArray());

            queryBldr.addWhereAttrInQuery(CIAccounting.TransactionPositionAbstract.TransactionLink,
                            attrQueryBldr.getAttributeQuery(CIAccounting.TransactionPositionAbstract.TransactionLink));
            queryBldr.addWhereAttrNotEqValue(CIAccounting.TransactionPositionAbstract.AccountLink, accInsts.toArray());
        }

        final MultiPrintQuery multi = queryBldr.getPrint();
        final SelectBuilder selAcc = SelectBuilder.get().linkto(CIAccounting.TransactionPositionAbstract.AccountLink);
        final SelectBuilder selAccName = new SelectBuilder(selAcc).attribute(CIAccounting.AccountAbstract.Name);
        final SelectBuilder selAccDescr = new SelectBuilder(selAcc).attribute(CIAccounting.AccountAbstract.Description);

        final SelectBuilder selTrans = SelectBuilder.get().linkto(
                        CIAccounting.TransactionPositionAbstract.TransactionLink);
        final SelectBuilder selTransDescr = new SelectBuilder(selTrans)
                        .attribute(CIAccounting.TransactionAbstract.Description);
        final SelectBuilder selTransDate = new SelectBuilder(selTrans).attribute(CIAccounting.TransactionAbstract.Date);

        multi.addSelect(selAccName, selAccDescr, selTransDescr, selTransDate);
        multi.addAttribute(CIAccounting.TransactionPositionAbstract.Amount);
        multi.execute();
        final List<DataBean> values = new ArrayList<>();
        final DataBean411 carryOver = new DataBean411();
        carryOver.setAmount(BigDecimal.ZERO);
        carryOver.setTransDescr(DBProperties.getProperty(CashReport11DS.class.getName() + ".CarryOver"));
        while (multi.next()) {
            final DateTime date = multi.<DateTime>getSelect(selTransDate);
            final BigDecimal amount = multi.<BigDecimal>getAttribute(CIAccounting.TransactionPositionAbstract.Amount);
            if (date.isBefore(dateFrom)) {
                carryOver.setAmount(carryOver.getAmount().add(amount));
            } else {
                final DataBean411 bean = new DataBean411();
                bean.setTransDate(date);
                bean.setTransDescr(multi.<String>getSelect(selTransDescr));
                bean.setAccName(multi.<String>getSelect(selAccName));
                bean.setAccDescr(multi.<String>getSelect(selAccDescr));
                bean.setAmount(amount);
                values.add(bean);
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



    public static class DataBean411
        extends DataBean
    {

    }
}
