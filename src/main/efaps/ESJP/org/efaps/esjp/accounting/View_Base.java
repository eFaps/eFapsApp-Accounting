/*
 * Copyright 2003 - 2012 The eFaps Team
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Parameter.ParameterValues;
import org.efaps.admin.event.Return;
import org.efaps.admin.event.Return.ReturnValues;
import org.efaps.admin.program.esjp.EFapsRevision;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.Context;
import org.efaps.db.Insert;
import org.efaps.db.Instance;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.PrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.common.uiform.Create;
import org.efaps.util.EFapsException;


/**
 * TODO comment!
 *
 * @author The eFaps Team
 * @version $Id$
 */
@EFapsUUID("b446f672-1871-418f-a90a-fd928ec89d8f")
@EFapsRevision("$Rev$")
public abstract class View_Base
{

    /**
     * Key used to store the values during a request.
     */
    public final static String REQUESTKEY = "org.efaps.esjp.accounting.View.FieldValueRequestKey";

    /**
     * Create a new View.
     *
     * @param _parameter    Parameter as passed from the eFAPS API
     * @return new Return
     * @throws EFapsException on error
     */
    public Return create(final Parameter _parameter)
        throws EFapsException
    {
        final Instance instance = _parameter.getCallInstance();
        final PrintQuery print = new PrintQuery(instance);
        print.addAttribute(CIAccounting.ViewAbstract.PeriodAbstractLink);
        print.execute();
        final Long periodId = print.<Long> getAttribute(CIAccounting.ViewAbstract.PeriodAbstractLink);

        final Create create = new Create() {

            @Override
            protected void add2basicInsert(final Parameter _parameter,
                                           final Insert _insert)
                throws EFapsException
            {
                _insert.add(CIAccounting.ViewAbstract.PeriodAbstractLink, periodId);
            }
        };
        return create.execute(_parameter);
    }

    /**
     * Get the FieldValue for the Value field by retrieving the values from
     * the related Accounts.
     *
     * @param _parameter    Parameter as passed by the eFaps API
     * @return Value for the Field
     * @throws EFapsException
     */
    @SuppressWarnings("unchecked")
    public Return getValueFieldValue(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        // get the map from the request
        Map<Instance, BigDecimal> values;
        if (Context.getThreadContext().containsRequestAttribute(View_Base.REQUESTKEY)) {
            values = (Map<Instance, BigDecimal>) Context.getThreadContext().getRequestAttribute(View_Base.REQUESTKEY);
        } else {
            values = new HashMap<Instance, BigDecimal>();
            Context.getThreadContext().setRequestAttribute(View_Base.REQUESTKEY, values);
        }

        final List<Instance> requestInst = (List<Instance>) _parameter.get(ParameterValues.REQUEST_INSTANCES);
        final List<Long> queryId = new ArrayList<Long>();
        for (final Instance instance : requestInst) {
            if (!values.containsKey(instance)) {
                queryId.add(instance.getId());
                values.put(instance, BigDecimal.ZERO);
            }
        }

        if (!queryId.isEmpty()) {
            final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.View2AccountAbstract);
            queryBldr.addWhereAttrEqValue(CIAccounting.View2AccountAbstract.FromLinkAbstract, queryId.toArray());
            final MultiPrintQuery multi = queryBldr.getPrint();
            final SelectBuilder selSum = new SelectBuilder().linkto(CIAccounting.View2AccountAbstract.ToLinkAbstract)
                            .attribute(CIAccounting.AccountAbstract.SumReport);
            final SelectBuilder selOid = new SelectBuilder().linkto(CIAccounting.View2AccountAbstract.FromLinkAbstract)
                            .oid();
            multi.addSelect(selSum, selOid);
            multi.execute();
            while (multi.next()) {
                final BigDecimal tmpVal = multi.<BigDecimal>getSelect(selSum);
                final String viewOid = multi.getSelect(selOid);
                final Instance viewInst = Instance.get(viewOid);
                BigDecimal value;
                if (values.containsKey(viewInst)) {
                    value = values.get(viewInst);
                } else {
                    value = BigDecimal.ZERO;
                }
                value = value.add(tmpVal == null ? BigDecimal.ZERO : tmpVal);
                values.put(viewInst, value);
            }
        }
        ret.put(ReturnValues.VALUES, values.get(_parameter.getInstance()));
        return ret;
    }
}
