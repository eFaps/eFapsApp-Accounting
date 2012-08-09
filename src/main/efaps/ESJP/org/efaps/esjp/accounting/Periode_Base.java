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

package org.efaps.esjp.accounting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import org.efaps.admin.datamodel.Status;
import org.efaps.admin.datamodel.Type;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Parameter.ParameterValues;
import org.efaps.admin.event.Return;
import org.efaps.admin.event.Return.ReturnValues;
import org.efaps.admin.program.esjp.EFapsRevision;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.admin.ui.AbstractCommand;
import org.efaps.db.AttributeQuery;
import org.efaps.db.Context;
import org.efaps.db.Context.FileParameter;
import org.efaps.db.Insert;
import org.efaps.db.Instance;
import org.efaps.db.InstanceQuery;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.PrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.esjp.accounting.Import_Base.ImportAccount;
import org.efaps.esjp.admin.common.SystemConf;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.ci.CIFormAccounting;
import org.efaps.esjp.ci.CISales;
import org.efaps.esjp.common.uitable.MultiPrint;
import org.efaps.esjp.erp.CurrencyInst;
import org.efaps.util.EFapsException;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 * @version $Id$
 */
@EFapsUUID("42e5a308-5d9a-4a8d-a469-b9929010858a")
@EFapsRevision("$Rev$")
public abstract class Periode_Base
{

    /**
     * Key used to store the currency in the session.
     */
    public static final String PERIODECURRENCYKEY = "eFaps_Accounting_PeriodeCurrency";



    /**
     * @param _instance instance of a period or an account
     * @return Instance of the periode the currency is wanted for
     * @throws EFapsException on error
     */
    @SuppressWarnings("unchecked")
    public CurrencyInst getCurrency(final Instance _instance)
        throws EFapsException
    {
        Map<Instance, CurrencyInst> inst2curr = (Map<Instance, CurrencyInst>) Context.getThreadContext()
                        .getSessionAttribute(Periode_Base.PERIODECURRENCYKEY);
        if (inst2curr == null || (inst2curr != null && inst2curr.get(_instance) != null)) {
            inst2curr = new HashMap<Instance, CurrencyInst>();
            Context.getThreadContext().setSessionAttribute(Periode_Base.PERIODECURRENCYKEY, inst2curr);
            final PrintQuery print = new PrintQuery(_instance);
            if (_instance.getType().isKindOf(CIAccounting.Account2AccountAbstract.getType())
                            || _instance.getType().isKindOf(CIAccounting.Transaction.getType())) {
                print.addSelect("linkto[PeriodeLink].linkto[CurrencyLink].oid");
                print.execute();
                inst2curr.put(_instance, new CurrencyInst(Instance.get(print
                                .<String> getSelect("linkto[PeriodeLink].linkto[CurrencyLink].oid"))));
            } else if (_instance.getType().isKindOf(CIAccounting.Account2AccountAbstract.getType())
                            || _instance.getType().isKindOf(CIAccounting.Transaction.getType())) {
                print.addSelect("linkto[PeriodeAbstractLink].linkto[CurrencyLink].oid");
                print.execute();
                inst2curr.put(_instance,
                                new CurrencyInst(Instance.get(print.<String> getSelect("linkto[PeriodeAbstractLink]"
                                                + ".linkto[CurrencyLink].oid"))));
            } else {
                print.addSelect("linkto[CurrencyLink].oid");
                print.execute();
                inst2curr.put(_instance,
                                new CurrencyInst(Instance.get(print.<String> getSelect("linkto[CurrencyLink].oid"))));
            }
        }
        return inst2curr.get(_instance);
    }

    /**
     * Called on a command to create a new period including account table and
     * reports.
     *
     * @param _parameter Paremeter
     * @return new Return
     * @throws EFapsException on error
     */
    public Return create(final Parameter _parameter)
        throws EFapsException
    {
        final Insert insert = new Insert(CIAccounting.Periode);
        insert.add(CIAccounting.Periode.Name,
                        _parameter.getParameterValue(CIFormAccounting.Accounting_PeriodeForm.name.name));
        insert.add(CIAccounting.Periode.FromDate,
                        _parameter.getParameterValue(CIFormAccounting.Accounting_PeriodeForm.fromDate.name));
        insert.add(CIAccounting.Periode.ToDate,
                        _parameter.getParameterValue(CIFormAccounting.Accounting_PeriodeForm.toDate.name));
        insert.add(CIAccounting.Periode.CurrencyLink,
                        _parameter.getParameterValue(CIFormAccounting.Accounting_PeriodeForm.currencyLink.name));
        insert.execute();
        final Instance periodInst = insert.getInstance();

        final SystemConf conf = new SystemConf();
        //Accounting-Configuration
        conf.addObjectAttribute(UUID.fromString("ca0a1df1-2211-45d9-97c8-07af6636a9b9"), periodInst,
                        "Name=" + _parameter.getParameterValue("name"));

        final FileParameter accountTable = Context.getThreadContext().getFileParameters().get(
                        CIFormAccounting.Accounting_PeriodeForm.accountTable.name);
        final FileParameter reports = Context.getThreadContext().getFileParameters().get(
                        CIFormAccounting.Accounting_PeriodeForm.reports.name);
        if (accountTable != null && accountTable.getSize() > 0) {
            final Import imp = new Import();
            final HashMap<String, ImportAccount> accounts = imp.createAccountTable(periodInst, accountTable);
            if (reports != null && reports.getSize() > 0) {
                imp.createReports(periodInst, reports, accounts);
            }
        }
        return new Return();
    }

    public Return updateTableAccount(final Parameter _parameter)
        throws EFapsException
    {
        final Instance periodInst = _parameter.getInstance();
        final FileParameter accountTable = Context.getThreadContext().getFileParameters().get(
                        CIFormAccounting.Accounting_PeriodeForm.accountTable.name);
        if (accountTable != null && accountTable.getSize() > 0) {
            final Import imp = new Import();
            imp.createAccountTable(periodInst, accountTable);
        }
        return new Return();
    }

    /**
     * Method is executed on an autocomplete event to present a dropdown with
     * accounts.
     *
     * @param _parameter Parameter as passed from the eFAPS API
     * @return list of map used for an autocomplete event
     * @throws EFapsException on erro
     */
    public Return autoComplete4Periode(final Parameter _parameter)
        throws EFapsException
    {
        final String input = (String) _parameter.get(ParameterValues.OTHERS);
        final Map<String, Map<String, String>> orderMap = new TreeMap<String, Map<String, String>>();

        final QueryBuilder queryBuilder = new QueryBuilder(CIAccounting.Periode);
        queryBuilder.addWhereAttrMatchValue("Name", input + "*").setIgnoreCase(true);
        final MultiPrintQuery print = queryBuilder.getPrint();
        print.addAttribute("ID", "Name", "FromDate", "ToDate");
        print.execute();
        while (print.next()) {
            final String name = print.<String> getAttribute("Name");
            final DateTime fromDate = print.<DateTime> getAttribute("FromDate");
            final DateTime toDate = print.<DateTime> getAttribute("ToDate");
            final String toDateStr = toDate.toString(DateTimeFormat.forStyle("S-")
                            .withLocale(Context.getThreadContext().getLocale()));
            final String fromDateStr = fromDate.toString(DateTimeFormat.forStyle("S-")
                            .withLocale(Context.getThreadContext().getLocale()));
            final Long id = print.<Long> getAttribute("ID");
            final String choice = name + ": " + fromDateStr + " - " + toDateStr;
            final Map<String, String> map = new HashMap<String, String>();
            map.put("eFapsAutoCompleteKEY", id.toString());
            map.put("eFapsAutoCompleteVALUE", name);
            map.put("eFapsAutoCompleteCHOICE", choice);
            orderMap.put(choice, map);
        }

        final List<Map<String, String>> list = new ArrayList<Map<String, String>>();
        list.addAll(orderMap.values());
        final Return retVal = new Return();
        retVal.put(ReturnValues.VALUES, list);
        return retVal;
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
        final Return ret = new Return();
        final Instance instance = _parameter.getInstance();
        final PrintQuery print = new PrintQuery(instance);
        print.addAttribute(CIAccounting.Periode.FromDate);
        print.addAttribute(CIAccounting.Periode.ToDate);
        print.execute();
        final DateTime from = print.<DateTime> getAttribute(CIAccounting.Periode.FromDate);
        final DateTime to = print.<DateTime> getAttribute(CIAccounting.Periode.ToDate);

        final QueryBuilder attrQueryBldr = new QueryBuilder(CIAccounting.TransactionClassDocument);
        final AttributeQuery attrQuery
            = attrQueryBldr.getAttributeQuery(CIAccounting.TransactionClassDocument.DocumentLink);

        final QueryBuilder queryBldr = new QueryBuilder(CISales.DocumentSumAbstract);
        queryBldr.addWhereAttrEqValue(CISales.DocumentSumAbstract.StatusAbstract,
                                      Status.find(CISales.InvoiceStatus.uuid, "Open").getId(),
                                      Status.find(CISales.InvoiceStatus.uuid, "Paid").getId(),
                                      Status.find(CISales.ReceiptStatus.uuid, "Open").getId(),
                                      Status.find(CISales.ReceiptStatus.uuid, "Paid").getId(),
                                      Status.find(CISales.CreditNoteStatus.uuid, "Open").getId(),
                                      Status.find(CISales.CreditNoteStatus.uuid, "Paid").getId(),
                                      Status.find(CISales.ReminderStatus.uuid, "Open").getId(),
                                      Status.find(CISales.ReminderStatus.uuid, "Paid").getId());
        queryBldr.addWhereAttrGreaterValue(CISales.DocumentSumAbstract.Date, from.minusMinutes(1));
        queryBldr.addWhereAttrLessValue(CISales.DocumentSumAbstract.Date, to.plusDays(1));
        queryBldr.addWhereAttrNotInQuery(CISales.DocumentSumAbstract.ID, attrQuery);

        final Map<?, ?> filter = (Map<?, ?>) _parameter.get(ParameterValues.OTHERS);
        final DocMulti multi = new DocMulti();
        multi.analyzeTable(_parameter, filter, queryBldr, CISales.DocumentStockAbstract.getType());

        final InstanceQuery query = queryBldr.getQuery();

        final List<Instance> instances = query.execute();

        ret.put(ReturnValues.VALUES, instances);
        return ret;
    }

    /**
     * Called from a tree menu command to present the documents that are with status
     * booked and therefor must be worked on still.
     *
     * @param _parameter Paremeter
     * @return List if Instances
     * @throws EFapsException on error
     */
    public Return getDocumentsToBook(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        ret.put(ReturnValues.VALUES, getDocumentsToBookList(_parameter));
        return ret;
    }

    /**
     * Called from a tree menu command to present the documents that are with
     * status booked and therefor must be worked on still.
     *
     * @param _parameter Parameter as passed from the eFaps API.
     * @return list with values.
     * @throws EFapsException on error
     */
    protected List<Instance> getDocumentsToBookList(final Parameter _parameter)
        throws EFapsException
    {
        final Instance instance = _parameter.getInstance();
        final AbstractCommand command = (AbstractCommand) _parameter.get(ParameterValues.UIOBJECT);
        final UUID uuidTypeDoc = command.getUUID();

        final QueryBuilder transQueryBldr = new QueryBuilder(CIAccounting.Transaction);
        transQueryBldr.addWhereAttrEqValue(CIAccounting.Transaction.PeriodeLink, instance.getId());
        final AttributeQuery transAttrQuery = transQueryBldr.getAttributeQuery(CIAccounting.Transaction.ID);

        final QueryBuilder attrQueryBldr = new QueryBuilder(CIAccounting.TransactionClassDocument);
        attrQueryBldr.addWhereAttrInQuery(CIAccounting.TransactionClassDocument.TransactionLink, transAttrQuery);
        final AttributeQuery attrQuery =
                            attrQueryBldr.getAttributeQuery(CIAccounting.TransactionClassDocument.DocumentLink);

        final QueryBuilder queryBldr = new QueryBuilder(CISales.DocumentSumAbstract);
        queryBldr.addWhereAttrEqValue(CISales.DocumentSumAbstract.StatusAbstract,
                                      Status.find(CISales.InvoiceStatus.uuid, "Open").getId(),
                                      Status.find(CISales.InvoiceStatus.uuid, "Paid").getId(),
                                      Status.find(CISales.ReceiptStatus.uuid, "Open").getId(),
                                      Status.find(CISales.ReceiptStatus.uuid, "Paid").getId(),
                                      Status.find(CISales.CreditNoteStatus.uuid, "Open").getId(),
                                      Status.find(CISales.CreditNoteStatus.uuid, "Paid").getId(),
                                      Status.find(CISales.ReminderStatus.uuid, "Open").getId(),
                                      Status.find(CISales.ReminderStatus.uuid, "Paid").getId());
        queryBldr.addWhereAttrInQuery(CISales.DocumentSumAbstract.ID, attrQuery);
        // Accounting_AccountTree_DocsToGain
        if (uuidTypeDoc.equals(UUID.fromString("63f34c69-fe9a-4e5b-adab-b90268b2a34f"))) {
            queryBldr.addWhereAttrEqValue(CISales.DocumentSumAbstract.Type,
                                        CISales.Invoice.getType().getId(),
                                        CISales.Receipt.getType().getId());
        // Accounting_AccountTree_DocsToPay
        } else if (uuidTypeDoc.equals(UUID.fromString("6fd11ce2-72e0-40ef-a959-186e3e664aa9"))) {
            queryBldr.addWhereAttrEqValue(CISales.DocumentSumAbstract.Type,
                                        CISales.CreditNote.getType().getId());
        }

        final Map<?, ?> filter = (Map<?, ?>) _parameter.get(ParameterValues.OTHERS);
        final DocMulti multi = new DocMulti();
        multi.analyzeTable(_parameter, filter, queryBldr, CISales.DocumentStockAbstract.getType());

        final InstanceQuery query = queryBldr.getQuery();
        final List<Instance> instances = query.execute();

        return instances;
    }

    /**
     * Called from a tree menu command to present the documents related with
     * stock movement that are not connected with the period and therefor
     * must be worked on still.
     *
     * @param _parameter Paremeter
     * @return List if Instances
     * @throws EFapsException on error
     */
    public Return getStockToBook(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final Instance instance = _parameter.getInstance();
        final PrintQuery print = new PrintQuery(instance);
        print.addAttribute(CIAccounting.Periode.FromDate);
        print.addAttribute(CIAccounting.Periode.ToDate);
        print.execute();
        final DateTime from = print.<DateTime>getAttribute(CIAccounting.Periode.FromDate);
        final DateTime to = print.<DateTime>getAttribute(CIAccounting.Periode.ToDate);

        final QueryBuilder attrQueryBldr = new QueryBuilder(CIAccounting.TransactionClassDocument);
        final AttributeQuery attrQuery = attrQueryBldr
                                .getAttributeQuery(CIAccounting.TransactionClassDocument.DocumentLink);

        final QueryBuilder queryBldr = new QueryBuilder(CISales.DocumentStockAbstract);
        queryBldr.addWhereAttrEqValue(CISales.DocumentStockAbstract.StatusAbstract,
                                      Status.find(CISales.DeliveryNoteStatus.uuid, "Closed").getId(),
                                      Status.find(CISales.ReturnSlipStatus.uuid, "Closed").getId());
        queryBldr.addWhereAttrGreaterValue(CISales.DocumentStockAbstract.Date, from.minusMinutes(1));
        queryBldr.addWhereAttrLessValue(CISales.DocumentStockAbstract.Date, to.plusDays(1));
        queryBldr.addWhereAttrNotInQuery(CISales.DocumentStockAbstract.ID, attrQuery);

        final Map<?, ?> filter = (Map<?, ?>) _parameter.get(ParameterValues.OTHERS);
        final DocMulti multi = new DocMulti();
        multi.analyzeTable(_parameter, filter, queryBldr, CISales.DocumentStockAbstract.getType());

        final InstanceQuery query = queryBldr.getQuery();
        final List<Instance> instances = query.execute();
        ret.put(ReturnValues.VALUES, instances);
        return ret;
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
        final Return ret = new Return();
        final Instance instance = _parameter.getInstance();
        final PrintQuery print = new PrintQuery(instance);
        print.addAttribute(CIAccounting.Periode.FromDate);
        print.addAttribute(CIAccounting.Periode.ToDate);
        print.execute();
        final DateTime from = print.<DateTime> getAttribute(CIAccounting.Periode.FromDate);
        final DateTime to = print.<DateTime> getAttribute(CIAccounting.Periode.ToDate);

        final QueryBuilder attrQueryBldr = new QueryBuilder(CIAccounting.TransactionClassExternal);
        final AttributeQuery attrQuery
            = attrQueryBldr.getAttributeQuery(CIAccounting.TransactionClassExternal.DocumentLink);

        final QueryBuilder queryBldr = new QueryBuilder(CISales.DocumentSumAbstract);
        queryBldr.addWhereAttrEqValue(CISales.DocumentSumAbstract.StatusAbstract,
                                      Status.find(CISales.IncomingInvoiceStatus.uuid, "Open").getId(),
                                      Status.find(CISales.IncomingInvoiceStatus.uuid, "Paid").getId(),
                                      Status.find(CIAccounting.ExternalVoucherStatus.uuid, "Open").getId(),
                                      Status.find(CIAccounting.ExternalVoucherStatus.uuid, "Paid").getId());
        queryBldr.addWhereAttrGreaterValue(CISales.DocumentSumAbstract.Date, from.minusMinutes(1));
        queryBldr.addWhereAttrLessValue(CISales.DocumentSumAbstract.Date, to.plusDays(1));
        queryBldr.addWhereAttrNotInQuery(CISales.DocumentSumAbstract.ID, attrQuery);

        final Map<?, ?> filter = (Map<?, ?>) _parameter.get(ParameterValues.OTHERS);
        final DocMulti multi = new DocMulti();
        multi.analyzeTable(_parameter, filter, queryBldr, CISales.DocumentStockAbstract.getType());

        final InstanceQuery query = queryBldr.getQuery();
        final List<Instance> instances = query.execute();
        ret.put(ReturnValues.VALUES, instances);
        return ret;
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
        final Return ret = new Return();
        ret.put(ReturnValues.VALUES, getExternalsToBookList(_parameter));
        return ret;
    }

    /**
     * Called from a tree menu command to present the documents that are with status
     * booked and therefor must be worked on still.
     *
     * @param _parameter Parameter as passed from the eFaps API.
     * @return list instance with values.
     * @throws EFapsException on error.
     */
    protected List<Instance> getExternalsToBookList(final Parameter _parameter)
        throws EFapsException
    {
        final Instance instance = _parameter.getInstance();

        final QueryBuilder transQueryBldr = new QueryBuilder(CIAccounting.Transaction);
        transQueryBldr.addWhereAttrEqValue(CIAccounting.Transaction.PeriodeLink, instance.getId());
        final AttributeQuery transAttrQuery = transQueryBldr.getAttributeQuery(CIAccounting.Transaction.ID);

        final QueryBuilder attrQueryBldr = new QueryBuilder(CIAccounting.TransactionClassExternal);
        attrQueryBldr.addWhereAttrInQuery(CIAccounting.TransactionClassExternal.TransactionLink, transAttrQuery);
        final AttributeQuery attrQuery = attrQueryBldr.getAttributeQuery(
                        CIAccounting.TransactionClassExternal.DocumentLink);

        final QueryBuilder queryBldr = new QueryBuilder(CISales.DocumentSumAbstract);
        queryBldr.addWhereAttrEqValue(CISales.DocumentSumAbstract.StatusAbstract,
                                        Status.find(CISales.IncomingInvoiceStatus.uuid, "Open").getId(),
                                        Status.find(CISales.IncomingInvoiceStatus.uuid, "Paid").getId(),
                                        Status.find(CIAccounting.ExternalVoucherStatus.uuid, "Open").getId(),
                                        Status.find(CIAccounting.ExternalVoucherStatus.uuid, "Paid").getId());
        queryBldr.addWhereAttrInQuery(CISales.DocumentSumAbstract.ID, attrQuery);

        final Map<?, ?> filter = (Map<?, ?>) _parameter.get(ParameterValues.OTHERS);
        final DocMulti multi = new DocMulti();
        multi.analyzeTable(_parameter, filter, queryBldr, CISales.DocumentStockAbstract.getType());

        final InstanceQuery query = queryBldr.getQuery();
        final List<Instance> instances = query.execute();
        return instances;
    }

    /**
     * MultiPrint for the Documents.
     */
    public class DocMulti
        extends MultiPrint
    {

        /**
         * {@inheritDoc}
         */
        @Override
        protected boolean analyzeTable(final Parameter _parameter,
                                       final Map<?, ?> _filter,
                                       final QueryBuilder _queryBldr,
                                       final Type _type)
            throws EFapsException
        {
            return super.analyzeTable(_parameter, _filter, _queryBldr, _type);
        }

    }

}
