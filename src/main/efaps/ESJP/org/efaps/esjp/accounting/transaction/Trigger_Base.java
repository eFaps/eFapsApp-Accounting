/*
 * Copyright 2003 - 2010 The eFaps Team
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

import org.efaps.admin.datamodel.Status;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Return;
import org.efaps.admin.program.esjp.EFapsRevision;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.AttributeQuery;
import org.efaps.db.Delete;
import org.efaps.db.Instance;
import org.efaps.db.InstanceQuery;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.PrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.db.Update;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.util.EFapsException;


/**
 * TODO comment!
 *
 * @author The eFaps Team
 * @version $Id$
 */
@EFapsUUID("137e3d98-fe02-466e-8af4-4bb7287b82bf")
@EFapsRevision("$Rev$")
public abstract class Trigger_Base
{
    /**
     * Method is executed as trigger after the insert of an
     * Accounting_TransactionPositionCredit.
     *
     * @param _parameter Parameters as passed from eFaps
     * @return Return
     * @throws EFapsException on error
     */
    public Return positionCreditInsertTrigger(final Parameter _parameter)
        throws EFapsException
    {
        updateSumReport(_parameter.getInstance());
        return new Return();
    }

    /**
     * Method is executed as trigger after the update of an
     * Accounting_TransactionPositionCredit.
     *
     * @param _parameter Parameters as passed from eFaps
     * @return Return
     * @throws EFapsException on error
     */
    public Return positionCreditUpdateTrigger(final Parameter _parameter)
        throws EFapsException
    {
        updateSumReport(_parameter.getInstance());
        return new Return();
    }

    /**
     * Method is executed as trigger before the deletion of an
     * Accounting_TransactionPositionCredit.
     *
     * @param _parameter Parameters as passed from eFaps
     * @return Return
     * @throws EFapsException on error
     */
    public Return positionCreditDeleteTrigger(final Parameter _parameter)
        throws EFapsException
    {
        deleteRelations(_parameter.getInstance());
        return new Return();
    }

    /**
     * Method is executed as trigger after the insert of an
     * Accounting_TransactionPositionDebit.
     *
     * @param _parameter Parameters as passed from eFaps
     * @return Return
     * @throws EFapsException on error
     */
    public Return positionDebitInsertTrigger(final Parameter _parameter)
        throws EFapsException
    {
        updateSumReport(_parameter.getInstance());
        return new Return();
    }

    /**
     * Method is executed as trigger after the update of an
     * Accounting_TransactionPositionDebit.
     *
     * @param _parameter Parameters as passed from eFaps
     * @return Return
     * @throws EFapsException on error
     */
    public Return positionDebitUpdateTrigger(final Parameter _parameter)
        throws EFapsException
    {
        updateSumReport(_parameter.getInstance());
        return new Return();
    }

    /**
     * Method is executed as trigger before the deletion of an
     * Accounting_TransactionPositionDebit.
     *
     * @param _parameter Parameters as passed from eFaps
     * @return Return
     * @throws EFapsException on error
     */
    public Return positionDebitDeleteTrigger(final Parameter _parameter)
        throws EFapsException
    {
        deleteRelations(_parameter.getInstance());
        return new Return();
    }

    /**
     * Update the report sum for the account.
     * @param _instance Instance of the position
     * @throws EFapsException on error
     */
    protected void updateSumReport(final Instance _instance)
        throws EFapsException
    {
        final PrintQuery print = new PrintQuery(_instance);
        print.addAttribute(CIAccounting.TransactionPositionAbstract.TransactionLink);
        final SelectBuilder selAccInst = SelectBuilder.get()
                        .linkto(CIAccounting.TransactionPositionAbstract.AccountLink).instance();
        final SelectBuilder selAccBooked = SelectBuilder.get()
                        .linkto(CIAccounting.TransactionPositionAbstract.AccountLink)
                        .attribute(CIAccounting.AccountAbstract.SumBooked);
        print.addSelect(selAccInst, selAccBooked);
        print.execute();

        BigDecimal total = print.<BigDecimal>getSelect(selAccBooked);
        if (total == null) {
            total = BigDecimal.ZERO;
        }

        final Instance accountInst = print.<Instance>getSelect(selAccInst);
        final QueryBuilder attrQueryBldr = new QueryBuilder(CIAccounting.Transaction);
        attrQueryBldr.addWhereAttrEqValue(CIAccounting.Transaction.Status,
                        Status.find(CIAccounting.TransactionStatus.uuid, "Open").getId());
        final AttributeQuery attrQuery = attrQueryBldr.getAttributeQuery(CIAccounting.Transaction.ID);

        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.TransactionPositionAbstract);
        queryBldr.addWhereAttrInQuery(CIAccounting.TransactionPositionAbstract.TransactionLink, attrQuery);
        queryBldr.addWhereAttrEqValue(CIAccounting.TransactionPositionAbstract.AccountLink, accountInst.getId());

        final MultiPrintQuery multi = queryBldr.getPrint();
        multi.addAttribute(CIAccounting.TransactionPositionAbstract.Amount);
        multi.execute();

        while (multi.next()) {
            total = total.add((BigDecimal) multi.getAttribute(CIAccounting.TransactionPositionAbstract.Amount));
        }
        final Update update = new Update(accountInst);
        update.add(CIAccounting.AccountAbstract.SumReport, total);
        update.executeWithoutAccessCheck();
    }

    /**
     * Remove the relations to Labels of the Position.
     * @param _instance Instance the Relations will be removed for
     * @throws EFapsException on error
     */
    protected void deleteRelations(final Instance _instance)
        throws EFapsException
    {
        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.Label2PositionAbstract);
        queryBldr.addWhereAttrEqValue(CIAccounting.Label2PositionAbstract.ToPositionAbstractLink, _instance.getId());
        final InstanceQuery query = queryBldr.getQuery();
        query.execute();
        while (query.next()) {
            final Delete del = new Delete(query.getCurrentValue());
            del.execute();
        }
    }

}
