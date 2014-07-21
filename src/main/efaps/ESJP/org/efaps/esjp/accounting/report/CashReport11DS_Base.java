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

import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JasperReport;

import org.efaps.admin.event.Parameter;
import org.efaps.admin.program.esjp.EFapsRevision;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.util.EFapsException;
import org.joda.time.DateTime;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 * @version $Id$
 */
@EFapsUUID("eb9fc10d-915a-4a29-8e40-513f077aae7d")
@EFapsRevision("$Rev$")
public abstract class CashReport11DS_Base
    extends AbstractCashReportDS
{

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

    public static class DataBean411
        extends DataBean
    {

    }
}
