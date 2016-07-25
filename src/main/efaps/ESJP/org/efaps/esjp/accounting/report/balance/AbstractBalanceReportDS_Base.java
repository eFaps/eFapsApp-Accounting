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

import org.efaps.admin.event.Parameter;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.Instance;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.esjp.accounting.report.AbstractReportDS;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.util.EFapsException;

import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JasperReport;


/**
 * TODO comment!
 *
 * @author The eFaps Team
 *
 */
@EFapsUUID("51473ec3-9043-47dc-a74e-3461c0a54fd7")
@EFapsApplication("eFapsApp-Accounting")
public abstract class AbstractBalanceReportDS_Base<T extends AbstractDataBean>
    extends AbstractReportDS
{


    @Override
    public void init(final JasperReport _jasperReport,
                     final Parameter _parameter,
                     final JRDataSource _parentSource,
                     final Map<String, Object> _jrParameters)
        throws EFapsException
    {
        super.init(_jasperReport, _parameter, _parentSource, _jrParameters);
        final List<Instance> accInsts = getAccountInst(_parameter, getKey(_parameter));

        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.TransactionPositionAbstract);
        queryBldr.addWhereAttrEqValue(CIAccounting.TransactionPositionAbstract.AccountLink, accInsts.toArray());
        final MultiPrintQuery multi = queryBldr.getPrint();
        final SelectBuilder selAcc = SelectBuilder.get().linkto(CIAccounting.TransactionPositionAbstract.AccountLink);
        final SelectBuilder selAccInst = new SelectBuilder(selAcc).instance();
        final SelectBuilder selAccName = new SelectBuilder(selAcc).attribute(CIAccounting.AccountAbstract.Name);
        final SelectBuilder selAccDescr = new SelectBuilder(selAcc).attribute(CIAccounting.AccountAbstract.Description);
        multi.addSelect(selAccInst, selAccName, selAccDescr);
        multi.addAttribute(CIAccounting.TransactionPositionAbstract.Amount);
        multi.execute();
        final List<AbstractDataBean> values = new ArrayList<>();
        final Map<Instance, AbstractDataBean> map = new HashMap<>();
        while (multi.next()) {
            final Instance accInst = multi.getSelect(selAccInst);
            final AbstractDataBean bean;
            if (map.containsKey(accInst)) {
                bean = map.get(accInst);
            } else {
                bean = getBean(_parameter);
                bean.setAccIntance(accInst);;
                bean.setAccName(multi.<String>getSelect(selAccName));
                bean.setAccDesc(multi.<String>getSelect(selAccDescr));
                map.put(accInst, bean);
                values.add(bean);
            }
            bean.add(multi.<BigDecimal>getAttribute(CIAccounting.TransactionPositionAbstract.Amount));
        }
        Collections.sort(values, new Comparator<AbstractDataBean>(){

            @Override
            public int compare(final AbstractDataBean _arg0,
                               final AbstractDataBean _arg1)
            {
                return _arg0.getAccName().compareTo(_arg1.getAccName());
            }});
        setData(values);
    }

    public abstract T getBean(final Parameter _parameter)
        throws EFapsException;

    public abstract String getKey(final Parameter _parameter)
        throws EFapsException;

}
