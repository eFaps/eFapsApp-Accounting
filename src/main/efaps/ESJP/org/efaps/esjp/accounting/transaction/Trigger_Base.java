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
package org.efaps.esjp.accounting.transaction;

import java.math.BigDecimal;
import java.util.List;
import java.util.Properties;

import org.efaps.admin.datamodel.Status;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Return;
import org.efaps.admin.program.esjp.EFapsApplication;
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
import org.efaps.esjp.accounting.util.Accounting;
import org.efaps.esjp.accounting.util.AccountingSettings;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.util.EFapsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * TODO comment!
 *
 * @author The eFaps Team
 */
@EFapsUUID("137e3d98-fe02-466e-8af4-4bb7287b82bf")
@EFapsApplication("eFapsApp-Accounting")
public abstract class Trigger_Base
{
    /**
     * Logger for this class.
     */
    private static final Logger LOG = LoggerFactory.getLogger(Trigger.class);

    /**
     * Method is executed as trigger after the insert of an
     * Accounting_Transaction.
     *
     * @param _parameter Parameters as passed from eFaps
     * @return Return
     * @throws EFapsException on error
     */
    public Return afterInsertTrigger(final Parameter _parameter)
        throws EFapsException
    {
        final Instance transInst = _parameter.getInstance();
        final PrintQuery print = new PrintQuery(transInst);
        final SelectBuilder selPeriodInst = SelectBuilder.get().linkto(CIAccounting.TransactionAbstract.PeriodLink)
                        .instance();
        print.addSelect(selPeriodInst);
        print.executeWithoutAccessCheck();

        final Instance periodInst = print.getSelect(selPeriodInst);

        // go backwar in the given period and search the last assigned ident to be able to evaluate the new one
        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.TransactionAbstract);
        queryBldr.addWhereAttrEqValue(CIAccounting.TransactionAbstract.PeriodLink, periodInst);
        queryBldr.addOrderByAttributeDesc(CIAccounting.TransactionAbstract.ID);
        queryBldr.setLimit(500);
        final MultiPrintQuery multi = queryBldr.getPrint();
        multi.setEnforceSorted(true);
        multi.addAttribute(CIAccounting.TransactionAbstract.Identifier);
        multi.executeWithoutAccessCheck();
        int diff = 0;
        String currentIdent = null;
        boolean found = false;
        while (multi.next() && !found) {
            final Instance currentInst = multi.getCurrentInstance();
            if (!currentInst.equals(transInst)) {
                diff++;
                currentIdent = multi.getAttribute(CIAccounting.TransactionAbstract.Identifier);
                if (!Transaction.IDENTTEMP.equals(currentIdent)) {
                    found = true;
                }
            }
        }
        final Properties props = Accounting.getSysConfig().getObjectAttributeValueAsProperties(periodInst);
        final String name = props.getProperty(AccountingSettings.PERIOD_NAME) + "-";
        int currentIdx = 1;
        if (found) {
            try {
                currentIdx = Integer.parseInt(currentIdent.replace(name, ""));
            } catch (final NumberFormatException e) {
                Trigger_Base.LOG.error("Could not evaluate next number", e);
            }
        }
        currentIdx = currentIdx + diff;
        final String ident = name + String.format("%06d", currentIdx);

        final Update update = new Update(transInst);
        update.add(CIAccounting.TransactionAbstract.Identifier, ident);
        update.executeWithoutTrigger();
        return new Return();
    }

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
        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.TransactionPosition2ObjectAbstract);
        queryBldr.addWhereAttrEqValue(CIAccounting.TransactionPosition2ObjectAbstract.FromLinkAbstract, _instance);
        final InstanceQuery query = queryBldr.getQuery();
        query.execute();
        while (query.next()) {
            final Delete del = new Delete(query.getCurrentValue());
            del.execute();
        }
    }

    /**
     * Transaction two document insert post trigger.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return the return
     * @throws EFapsException on error
     */
    public Return transaction2DocumentInsertPostTrigger(final Parameter _parameter)
        throws EFapsException
    {
        final PrintQuery print = new PrintQuery(_parameter.getInstance());
        print.addAttribute(CIAccounting.Transaction2ERPDocument.FromLinkAbstract);
        print.executeWithoutAccessCheck();
        final Long transId = print.getAttribute(CIAccounting.Transaction2ERPDocument.FromLinkAbstract);

        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.Transaction2ERPDocument);
        queryBldr.addWhereAttrEqValue(CIAccounting.Transaction2ERPDocument.FromLinkAbstract, transId);
        queryBldr.addWhereAttrNotEqValue(CIAccounting.Transaction2ERPDocument.ID, _parameter.getInstance());
        final List<Instance> insts = queryBldr.getQuery().execute();

        final Update update = new Update(_parameter.getInstance());
        update.add(CIAccounting.Transaction2ERPDocument.Position, insts.size() + 1);
        update.executeWithoutTrigger();
        return new Return();
    }

    /**
     * Transaction two document delete pre trigger.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return the return
     * @throws EFapsException
     */
    public Return transaction2DocumentDeletePreTrigger(final Parameter _parameter)
        throws EFapsException
    {
        final PrintQuery print = new PrintQuery(_parameter.getInstance());
        print.addAttribute(CIAccounting.Transaction2ERPDocument.FromLinkAbstract);
        print.executeWithoutAccessCheck();
        final Long transId = print.getAttribute(CIAccounting.Transaction2ERPDocument.FromLinkAbstract);

        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.Transaction2ERPDocument);
        queryBldr.addWhereAttrEqValue(CIAccounting.Transaction2ERPDocument.FromLinkAbstract, transId);
        queryBldr.addWhereAttrNotEqValue(CIAccounting.Transaction2ERPDocument.ID, _parameter.getInstance());
        queryBldr.addOrderByAttributeAsc(CIAccounting.Transaction2ERPDocument.Position);
        final InstanceQuery query = queryBldr.getQuery();
        query.executeWithoutAccessCheck();
        int i = 1;
        while (query.next()) {
            final Update update = new Update(query.getCurrentValue());
            update.add(CIAccounting.Transaction2ERPDocument.Position, i);
            update.executeWithoutTrigger();
            i++;
        }
        return new Return();
    }
}
