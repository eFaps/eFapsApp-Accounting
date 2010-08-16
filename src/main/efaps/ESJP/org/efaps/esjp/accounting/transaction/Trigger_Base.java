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
import org.efaps.db.Instance;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.PrintQuery;
import org.efaps.db.QueryBuilder;
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
     * Update the report sum for the account.
     * @param _instance Instance of the position
     * @throws EFapsException on error
     */
    protected void updateSumReport(final Instance _instance)
        throws EFapsException
    {
        final PrintQuery print = new PrintQuery(_instance);
        print.addSelect("linkto[AccountLink].oid");
        print.addSelect("linkto[AccountLink].attribute[SumBooked]");
        print.execute();

        BigDecimal total = print.<BigDecimal>getSelect("linkto[AccountLink].attribute[SumBooked]");
        if (total == null) {
            total = BigDecimal.ZERO;
        }

        final Instance accountInst = Instance.get(print.<String>getSelect("linkto[AccountLink].oid"));
        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.TransactionWithPos);
        queryBldr.addWhereAttrEqValue(CIAccounting.TransactionWithPos.AccountLink, accountInst.getId());
        queryBldr.addWhereAttrEqValue(CIAccounting.TransactionWithPos.Status,
                        Status.find(CIAccounting.TransactionStatus.uuid, "Open").getId());
        final MultiPrintQuery multi = queryBldr.getPrint();
        multi.addAttribute(CIAccounting.TransactionWithPos.Amount);
        multi.execute();

        while (multi.next()) {
            total = total.add((BigDecimal) multi.getAttribute(CIAccounting.TransactionWithPos.Amount));
        }
        final Update update = new Update(accountInst);
        update.add(CIAccounting.AccountAbstract.SumReport, total);
        update.executeWithoutAccessCheck();
    }

}
