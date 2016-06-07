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

package org.efaps.esjp.accounting.transaction;

import java.math.BigDecimal;
import java.util.UUID;

import org.efaps.admin.datamodel.Status;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Return;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.AttributeQuery;
import org.efaps.db.Context;
import org.efaps.db.Insert;
import org.efaps.db.Instance;
import org.efaps.db.InstanceQuery;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.PrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.ci.CIFormAccounting;
import org.efaps.esjp.ci.CISales;
import org.efaps.util.EFapsException;
import org.joda.time.DateTime;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 */
@EFapsUUID("f9def40c-2334-4313-b2ac-d9e60beb1934")
@EFapsApplication("eFapsApps-Accounting")
public abstract class Payroll_Base
{

    /**
     * Creates the transaction4 pay slip.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return the return
     * @throws EFapsException on error
     */
    public Return createTransaction4PaySlip(final Parameter _parameter)
        throws EFapsException
    {
        final Instance paySlipInst = _parameter.getInstance();
        final DateTime date = new DateTime();
        final Instance periodInst = Instance.get(_parameter
                        .getParameterValue(CIFormAccounting.Accounting_Payroll_CreateTransactionForm.periodLink.name));
        if (allowCreate(_parameter, paySlipInst)) {
            createTransaction(_parameter, periodInst, paySlipInst, date);
        }
        return new Return();
    }

    /**
     * Creates the transaction4 pay slips.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return the return
     * @throws EFapsException on error
     */
    public Return createTransaction4PaySlips(final Parameter _parameter)
        throws EFapsException
    {
        final String[] oids = (String[]) Context.getThreadContext().getSessionAttribute("storeSelectedRowOIDs");
        final DateTime date = new DateTime();
        final Instance periodInst = Instance.get(_parameter
                        .getParameterValue(CIFormAccounting.Accounting_Payroll_CreateTransactionForm.periodLink.name));

        for (final String oid : oids) {
            final Instance paySlipInst = Instance.get(oid);
            if (allowCreate(_parameter, paySlipInst)) {
                createTransaction(_parameter, periodInst, paySlipInst, date);
            }
        }
        Context.getThreadContext().removeSessionAttribute("storeSelectedRowOIDs");
        return new Return();
    }


    /**
     * @param _parameter    Parameter as passed by the efaps API
     * @param _docInst      instance of the document
     * @return true if create, else false
     * @throws EFapsException on error
     */
    protected boolean allowCreate(final Parameter _parameter,
                                  final Instance _docInst)
        throws EFapsException
    {
        boolean ret = false;
        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.Transaction2SalesDocument);
        queryBldr.addWhereAttrEqValue(CIAccounting.Transaction2SalesDocument.ToLink, _docInst);
        final InstanceQuery query = queryBldr.getQuery();
        ret = query.execute().isEmpty();
        return ret;
    }


    /**
     * Creates the transaction.
     *
     * @param _parameter as passed from eFaps API.
     * @param _periodInst the period inst
     * @param _docInst the doc inst
     * @param _transDate the trans date
     * @return the return
     * @throws EFapsException on error.
     */
    protected Return createTransaction(final Parameter _parameter,
                                       final Instance _periodInst,
                                       final Instance _docInst,
                                       final DateTime _transDate)
        throws EFapsException
    {

        if (_periodInst.isValid()) {

            final PrintQuery print = new PrintQuery(_docInst);
            print.addAttribute(CISales.DocumentSumAbstract.Name);
            print.executeWithoutAccessCheck();
            final String name = print.<String>getAttribute(CISales.DocumentSumAbstract.Name);

            final Insert insert = new Insert(CIAccounting.Transaction);
            insert.add(CIAccounting.Transaction.Description, name);
            insert.add(CIAccounting.Transaction.Date, _transDate);
            insert.add(CIAccounting.Transaction.PeriodLink, _periodInst.getId());
            insert.add(CIAccounting.Transaction.Status, Status.find(CIAccounting.TransactionStatus.uuid, "Open")
                            .getId());
            insert.execute();

            // make the classifications
            final Create create = new Create();
            create.connectDocs2Transaction(_parameter, insert.getInstance(), _docInst);

            // Payroll might not be installed so no CITypes are used
            //Payroll_CasePositionCalc
            final QueryBuilder attQueryBldr = new QueryBuilder(UUID.fromString("158d6092-d4e7-4154-b29a-72fa69c287e0"));
            final AttributeQuery attrQuery = attQueryBldr.getAttributeQuery("ID");

            // Payroll_PositionAbstract
            final QueryBuilder queryBldr = new QueryBuilder(UUID.fromString("0f928423-ef1a-4ca8-8a3d-e68182a4ffcf"));
            queryBldr.addWhereAttrInQuery("CasePositionAbstractLink", attrQuery);
            queryBldr.addWhereAttrEqValue("DocumentAbstractLink", _docInst.getId());
            final MultiPrintQuery multi = queryBldr.getPrint();
            final SelectBuilder selCaseInst = new SelectBuilder().linkto("CasePositionAbstractLink").instance();
            final SelectBuilder selRateCurInst = new SelectBuilder().linkto("RateCurrencyLink").instance();
            multi.addSelect(selCaseInst, selRateCurInst);
            multi.addAttribute("RateAmount");
            multi.execute();

            final Action action = new Action() {
                @Override
                protected Instance getPeriodInstance(final Parameter _parameter,
                                                      final Instance _transactionInstance)
                    throws EFapsException
                {
                    return _periodInst;
                }

                @Override
                protected DateTime getExchangeDate(final Parameter _parameter,
                                                   final Instance _transactionInstance)
                    throws EFapsException
                {
                    return _transDate;
                }
            };

            while (multi.next()) {
                final PrintQuery print2 = new PrintQuery(multi.<Instance>getSelect(selCaseInst));
                final SelectBuilder selActionInst = new SelectBuilder().linkto("ActionDefinitionLink").instance();
                print2.addSelect(selActionInst);
                print2.execute();
                final Instance actionInst = print2.<Instance>getSelect(selActionInst);
                if (actionInst.isValid()) {
                    final BigDecimal amount = multi.<BigDecimal>getAttribute("RateAmount");
                    final Instance curInst = multi.<Instance>getSelect(selRateCurInst);
                    action.insertPositions(_parameter, insert.getInstance(), actionInst, amount, curInst);
                }
            }
        }
        return new Return();
    }
}
