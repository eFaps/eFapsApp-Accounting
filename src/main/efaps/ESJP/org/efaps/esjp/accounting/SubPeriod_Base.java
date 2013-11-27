/*
 * Copyright 2003 - 2013 The eFaps Team
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

package org.efaps.esjp.accounting;

import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Parameter.ParameterValues;
import org.efaps.admin.event.Return;
import org.efaps.admin.event.Return.ReturnValues;
import org.efaps.admin.program.esjp.EFapsRevision;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.AttributeQuery;
import org.efaps.db.CachedPrintQuery;
import org.efaps.db.Context;
import org.efaps.db.Instance;
import org.efaps.db.PrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.esjp.accounting.transaction.Transaction_Base;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.ci.CISales;
import org.efaps.esjp.common.uitable.MultiPrint;
import org.efaps.util.EFapsException;
import org.joda.time.DateTime;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 * @version $Id$
 */
@EFapsUUID("948a690d-9934-470a-b8fb-b1e4d212010a")
@EFapsRevision("$Rev$")
public abstract class SubPeriod_Base
{

    /**
     * CacheKey for SubPeriods.
     */
    public static final String CACHEKEY = SubPeriod.class.getName() + ".CacheKey";

    /**
     * Set the related period as an instance key.
     *
     * @param _parameter Paremeter
     * @return List if Instances
     * @throws EFapsException on error
     */
    public Return setPeriodInstance(final Parameter _parameter)
        throws EFapsException
    {
        final Instance subPeriodInst;
        if (_parameter.getInstance() != null) {
            subPeriodInst = _parameter.getInstance();
        } else {
            subPeriodInst = Instance.get((String) _parameter.get(ParameterValues.OTHERS));
        }
        final PrintQuery print = new CachedPrintQuery(subPeriodInst, SubPeriod_Base.CACHEKEY);
        final SelectBuilder selPeriodInst = SelectBuilder.get().linkto(CIAccounting.SubPeriod.PeriodLink)
                        .instance();
        print.addSelect(selPeriodInst);
        print.execute();
        final Instance periodInst = print.<Instance>getSelect(selPeriodInst);

        if (periodInst != null && periodInst.isValid()) {
            Context.getThreadContext().setSessionAttribute(Transaction_Base.PERIODE_SESSIONKEY, periodInst);
        }
        final Return ret = new Return();
        ret.put(ReturnValues.VALUES, subPeriodInst);
        return ret;
    }

    /**
     * Called from a tree menu command to present the documents that are not
     * included in accounting yet.
     *
     * @param _parameter Paremeter
     * @return List if Instances
     * @throws EFapsException on error
     */
    public Return getDocuments(final Parameter _parameter)
        throws EFapsException
    {
        final MultiPrint multi = new MultiPrint()
        {
            @Override
            protected void add2QueryBldr(final Parameter _parameter,
                                         final QueryBuilder _queryBldr)
                throws EFapsException
            {
                final Instance instance = _parameter.getInstance();
                final PrintQuery print = new CachedPrintQuery(instance, SubPeriod_Base.CACHEKEY);
                print.addAttribute(CIAccounting.SubPeriod.FromDate);
                print.addAttribute(CIAccounting.SubPeriod.ToDate);
                print.execute();
                final DateTime from = print.<DateTime>getAttribute(CIAccounting.SubPeriod.FromDate);
                final DateTime to = print.<DateTime>getAttribute(CIAccounting.SubPeriod.ToDate);

                final QueryBuilder attrQueryBldr = new QueryBuilder(CIAccounting.TransactionClassDocument);
                final AttributeQuery attrQuery = attrQueryBldr
                                .getAttributeQuery(CIAccounting.TransactionClassDocument.DocumentLink);
                _queryBldr.addWhereAttrGreaterValue(CISales.DocumentSumAbstract.Date, from.minusMinutes(1));
                _queryBldr.addWhereAttrLessValue(CISales.DocumentSumAbstract.Date, to.plusDays(1));
                _queryBldr.addWhereAttrNotInQuery(CISales.DocumentSumAbstract.ID, attrQuery);
            }
        };
        return multi.execute(_parameter);
    }

    /**
     * Called from a tree menu command to present the documents that are already
     * included in accounting.
     *
     * @param _parameter Paremeter
     * @return List if Instances
     * @throws EFapsException on error
     */
    public Return getDocumentsToBook(final Parameter _parameter)
        throws EFapsException
    {
        final MultiPrint multi = new MultiPrint()
        {
            @Override
            protected void add2QueryBldr(final Parameter _parameter,
                                         final QueryBuilder _queryBldr)
                throws EFapsException
            {
                final Instance instance = _parameter.getInstance();
                final QueryBuilder transQueryBldr = new QueryBuilder(CIAccounting.Transaction);
                transQueryBldr.addWhereAttrEqValue(CIAccounting.Transaction.PeriodeLink, instance.getId());
                final AttributeQuery tranAttrQuery = transQueryBldr.getAttributeQuery(CIAccounting.Transaction.ID);

                final QueryBuilder attrQueryBldr = new QueryBuilder(CIAccounting.TransactionClassDocument);
                attrQueryBldr.addWhereAttrInQuery(CIAccounting.TransactionClassDocument.TransactionLink, tranAttrQuery);
                final AttributeQuery attrQuery = attrQueryBldr.getAttributeQuery(
                                CIAccounting.TransactionClassDocument.DocumentLink);
                _queryBldr.addWhereAttrInQuery(CISales.DocumentSumAbstract.ID, attrQuery);
            }
        };
        return multi.execute(_parameter);
    }


    /**
     * Called from a tree menu command to present the documents that are with status
     * booked and therefor must be worked on still.
     *
     * @param _parameter Paremeter
     * @return List if Instances
     * @throws EFapsException on error
     */
    public Return getExternals(final Parameter _parameter)
        throws EFapsException
    {
        final MultiPrint multi = new MultiPrint()
        {
            @Override
            protected void add2QueryBldr(final Parameter _parameter,
                                         final QueryBuilder _queryBldr)
                throws EFapsException
            {
                final Instance instance = _parameter.getInstance();
                final PrintQuery print = new CachedPrintQuery(instance, SubPeriod_Base.CACHEKEY);
                print.addAttribute(CIAccounting.SubPeriod.FromDate);
                print.addAttribute(CIAccounting.SubPeriod.ToDate);
                print.execute();
                final DateTime from = print.<DateTime>getAttribute(CIAccounting.SubPeriod.FromDate);
                final DateTime to = print.<DateTime>getAttribute(CIAccounting.SubPeriod.ToDate);

                final QueryBuilder attrQueryBldr = new QueryBuilder(CIAccounting.TransactionClassExternal);
                final AttributeQuery attrQuery = attrQueryBldr
                                .getAttributeQuery(CIAccounting.TransactionClassExternal.DocumentLink);
                _queryBldr.addWhereAttrGreaterValue(CISales.DocumentSumAbstract.Date, from.minusMinutes(1));
                _queryBldr.addWhereAttrLessValue(CISales.DocumentSumAbstract.Date, to.plusDays(1));
                _queryBldr.addWhereAttrNotInQuery(CISales.DocumentSumAbstract.ID, attrQuery);
            }
        };
        return multi.execute(_parameter);
    }

    /**
     * Called from a tree menu command to present the documents that are with status
     * booked and therefor must be worked on still.
     *
     * @param _parameter Paremeter
     * @return List if Instances
     * @throws EFapsException on error
     */
    public Return getExternalsToBook(final Parameter _parameter)
        throws EFapsException
    {
        final MultiPrint multi = new MultiPrint()
        {
            @Override
            protected void add2QueryBldr(final Parameter _parameter,
                                         final QueryBuilder _queryBldr)
                throws EFapsException
            {
                final Instance instance = _parameter.getInstance();
                final QueryBuilder transQueryBldr = new QueryBuilder(CIAccounting.Transaction);
                transQueryBldr.addWhereAttrEqValue(CIAccounting.Transaction.PeriodeLink, instance.getId());
                final AttributeQuery tranAttrQuery = transQueryBldr.getAttributeQuery(CIAccounting.Transaction.ID);

                final QueryBuilder attrQueryBldr = new QueryBuilder(CIAccounting.TransactionClassExternal);
                attrQueryBldr.addWhereAttrInQuery(CIAccounting.TransactionClassExternal.TransactionLink, tranAttrQuery);
                final AttributeQuery attrQuery = attrQueryBldr.getAttributeQuery(
                                CIAccounting.TransactionClassExternal.DocumentLink);
                _queryBldr.addWhereAttrInQuery(CISales.DocumentSumAbstract.ID, attrQuery);
            }
        };
        return multi.execute(_parameter);
    }
}