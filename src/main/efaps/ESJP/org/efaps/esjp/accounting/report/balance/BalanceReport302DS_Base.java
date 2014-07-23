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

package org.efaps.esjp.accounting.report.balance;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JasperReport;

import org.efaps.admin.event.Parameter;
import org.efaps.db.Instance;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.esjp.accounting.util.AccountingSettings;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.util.EFapsException;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 * @version $Id$
 */
public abstract class BalanceReport302DS_Base
    extends AbstractBalanceReportDS

{

    @Override
    public void init(final JasperReport _jasperReport,
                     final Parameter _parameter,
                     final JRDataSource _parentSource,
                     final Map<String, Object> _jrParameters)
        throws EFapsException
    {
        super.init(_jasperReport, _parameter, _parentSource, _jrParameters);
        final List<Instance> accInst = getAccountInst(_parameter, AccountingSettings.PERIOD_REPORT302ACCOUNT);

        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.TransactionPositionAbstract);
        queryBldr.addWhereAttrEqValue(CIAccounting.TransactionPositionAbstract.AccountLink, accInst.toArray());
        final MultiPrintQuery multi = queryBldr.getPrint();
        final SelectBuilder selAcc = SelectBuilder.get().linkto(CIAccounting.TransactionPositionAbstract.AccountLink);
        final SelectBuilder selAccOID = new SelectBuilder(selAcc).oid();
        final SelectBuilder selAccName = new SelectBuilder(selAcc).attribute(CIAccounting.AccountAbstract.Name);
        final SelectBuilder selAccDescr = new SelectBuilder(selAcc).attribute(CIAccounting.AccountAbstract.Description);
        multi.addSelect(selAccOID, selAccName, selAccDescr);
        multi.addAttribute(CIAccounting.TransactionPositionAbstract.Amount);
        multi.execute();
        final List<DataBean> values = new ArrayList<>();
        final Map<String, Bean32> map = new HashMap<>();
        while (multi.next()) {
            final String accOID = multi.getSelect(selAccOID);
            final Bean32 bean;
            if (map.containsKey(accOID)) {
                bean = map.get(accOID);
            } else {
                bean = new Bean32();
                bean.setAccName(multi.<String>getSelect(selAccName));
                bean.setAccDesc(multi.<String>getSelect(selAccDescr));
                map.put(accOID, bean);
                values.add(bean);
            }
            bean.add(multi.<BigDecimal>getAttribute(CIAccounting.TransactionPositionAbstract.Amount));
        }
        Collections.sort(values, new Comparator<DataBean>(){

            @Override
            public int compare(final DataBean _arg0,
                               final DataBean _arg1)
            {
                return _arg0.getAccName().compareTo(_arg1.getAccName());
            }});
        setData(values);
    }


    public static class Bean32
        extends DataBean
    {

    }
}
