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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JasperReport;

import org.efaps.admin.event.Parameter;
import org.efaps.admin.program.esjp.EFapsRevision;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.Instance;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.esjp.accounting.Period;
import org.efaps.esjp.accounting.util.Accounting;
import org.efaps.esjp.accounting.util.AccountingSettings;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.util.EFapsException;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 * @version $Id: CashReport11DS_Base.java 13378 2014-07-21 19:12:12Z
 *          jan@moxter.net $
 */
@EFapsUUID("eb9fc10d-915a-4a29-8e40-513f077aae7d")
@EFapsRevision("$Rev$")
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

        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.TransactionPositionAbstract);

        final List<Instance> accInsts = getAccountInst(_parameter);
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
        while (multi.next()) {
            final DataBean411 bean = new DataBean411();
            bean.setTransDate(multi.<DateTime>getSelect(selTransDate));
            bean.setTransDescr(multi.<String>getSelect(selTransDescr));
            bean.setAccName(multi.<String>getSelect(selAccName));
            bean.setAccDescr(multi.<String>getSelect(selAccDescr));
            bean.setAmount(multi.<BigDecimal>getAttribute(CIAccounting.TransactionPositionAbstract.Amount));
            values.add(bean);
        }
        setData(values);
    }

    protected List<Instance> getAccountInst(final Parameter _parameter)
        throws EFapsException
    {
        final List<Instance> ret = new ArrayList<>();
        final Instance periodInst = new Period().evaluateCurrentPeriod(_parameter);

        final Properties props = Accounting.getSysConfig().getObjectAttributeValueAsProperties(periodInst);
        final String accName = props.getProperty(AccountingSettings.PERIOD_REPORT11ACCOUNT);
        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.AccountAbstract);
        queryBldr.addWhereAttrEqValue(CIAccounting.AccountAbstract.Name, accName);
        queryBldr.addWhereAttrEqValue(CIAccounting.AccountAbstract.PeriodAbstractLink, periodInst);
        final MultiPrintQuery multi = queryBldr.getPrint();

        multi.executeWithoutAccessCheck();
        while (multi.next()) {
            final Instance inst = multi.getCurrentInstance();
            ret.add(inst);
            ret.addAll(getAccountInst(_parameter, inst));
        }
        return ret;
    }

    protected List<Instance> getAccountInst(final Parameter _parameter,
                                            final Instance _parentInst)
        throws EFapsException
    {
        final List<Instance> ret = new ArrayList<>();
        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.AccountAbstract);
        queryBldr.addWhereAttrEqValue(CIAccounting.AccountAbstract.ParentLink, _parentInst);
        final MultiPrintQuery multi = queryBldr.getPrint();
        multi.executeWithoutAccessCheck();
        while (multi.next()) {
            final Instance inst = multi.getCurrentInstance();
            ret.add(inst);
            ret.addAll(getAccountInst(_parameter, inst));
        }
        return ret;
    }

    public static class DataBean411
        extends DataBean
    {

    }
}
