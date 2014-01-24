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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import org.efaps.admin.datamodel.Status;
import org.efaps.admin.datamodel.Status.StatusGroup;
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
import org.efaps.esjp.accounting.util.Accounting;
import org.efaps.esjp.accounting.util.AccountingSettings;
import org.efaps.esjp.admin.common.SystemConf;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.ci.CIFormAccounting;
import org.efaps.esjp.ci.CISales;
import org.efaps.esjp.common.uitable.MultiPrint;
import org.efaps.esjp.erp.CurrencyInst;
import org.efaps.ui.wicket.util.EFapsKey;
import org.efaps.util.EFapsException;
import org.efaps.util.cache.CacheReloadException;
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
    public static final String PERIODECURRENCYKEY = Periode.class.getName() + ".PeriodeCurrencySessionKey";

    /**
     * Default setting to be added on creation of a period.
     */
    public static final Map<String, String> DEFAULTSETTINGS4PERIOD = new LinkedHashMap<String, String>();
    {
        Periode_Base.DEFAULTSETTINGS4PERIOD.put(AccountingSettings.PERIOD_NAME, null);
        Periode_Base.DEFAULTSETTINGS4PERIOD.put(AccountingSettings.PERIOD_EXVATACCOUNT, null);
        Periode_Base.DEFAULTSETTINGS4PERIOD.put(AccountingSettings.PERIOD_ROUNDINGCREDIT, null);
        Periode_Base.DEFAULTSETTINGS4PERIOD.put(AccountingSettings.PERIOD_ROUNDINGDEBIT, null);
        Periode_Base.DEFAULTSETTINGS4PERIOD.put(AccountingSettings.PERIOD_EXCHANGEGAIN, null);
        Periode_Base.DEFAULTSETTINGS4PERIOD.put(AccountingSettings.PERIOD_EXCHANGELOSS, null);
        Periode_Base.DEFAULTSETTINGS4PERIOD.put(AccountingSettings.PERIOD_TRANSFERACCOUNT, null);
        Periode_Base.DEFAULTSETTINGS4PERIOD.put(AccountingSettings.PERIOD_ROUNDINGMAXAMOUNT, "0.05");
        Periode_Base.DEFAULTSETTINGS4PERIOD.put(AccountingSettings.PERIOD_ACTIVATEEXCHANGE, "true");
        Periode_Base.DEFAULTSETTINGS4PERIOD.put(AccountingSettings.PERIOD_ACTIVATEVIEWS, "false");
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
        insert.add(CIAccounting.Periode.Status, Status.find(CIAccounting.PeriodStatus.Open));
        insert.execute();
        final Instance periodInst = insert.getInstance();
        final StringBuilder props = new StringBuilder();

        for (final Entry<String, String> entry : Periode_Base.DEFAULTSETTINGS4PERIOD.entrySet()) {
            if (props.length() > 0) {
                props.append("\n");
            }
            props.append(entry.getKey()).append("=");
            if (AccountingSettings.PERIOD_NAME.equals(entry.getKey())) {
                props.append(_parameter.getParameterValue("name"));
            } else if (entry.getValue() != null) {
                props.append(entry.getValue());
            }
        }

        final SystemConf conf = new SystemConf();
        conf.addObjectAttribute(Accounting.getSysConfig().getUUID(), periodInst,  props.toString());

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
     * @param _parameter Parameter as passed by the eFaps API
     * @return new Return
     * @throws EFapsException on error
     */
    public Return cleanPeriode(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        Context.getThreadContext().removeSessionAttribute(Periode_Base.PERIODECURRENCYKEY);
        ret.put(ReturnValues.TRUE, true);
        return ret;
    }

    /**
     * @param _parameter Parameter as passed by the eFaps API
     * @return new Return
     * @throws EFapsException on error
     */
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
     * @param _parameter Parameter as passed by the eFaps API
     * @return new Return
     * @throws EFapsException on error
     */
    public Return updateViewTableAccount(final Parameter _parameter)
        throws EFapsException
    {
        final Instance periodInst = _parameter.getInstance();
        final FileParameter accountTable = Context.getThreadContext().getFileParameters().get(
                        CIFormAccounting.Accounting_PeriodeForm.accountTable.name);
        if (accountTable != null && accountTable.getSize() > 0) {
            final Import imp = new Import();
            imp.createViewAccountTable(periodInst, accountTable);
        }
        return new Return();
    }

    /**
     * @param _parameter Parameter as passed by the eFaps API
     * @return new Return
     * @throws EFapsException on error
     */
    public Return updateCaseTable(final Parameter _parameter)
        throws EFapsException
    {
        final Instance periodInst = _parameter.getInstance();
        final FileParameter accountTable = Context.getThreadContext().getFileParameters().get(
                        CIFormAccounting.Accounting_PeriodeForm.accountTable.name);
        if (accountTable != null && accountTable.getSize() > 0) {
            final Import imp = new Import();
            imp.createCaseTable(periodInst, accountTable);
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
        final Map<?, ?> properties = (Map<?, ?>) _parameter.get(ParameterValues.PROPERTIES);
        final String key = properties.containsKey("Key") ? (String) properties.get("Key") : "ID";

        final Map<String, Map<String, String>> orderMap = new TreeMap<String, Map<String, String>>();

        final QueryBuilder queryBuilder = new QueryBuilder(CIAccounting.Periode);
        queryBuilder.addWhereAttrMatchValue(CIAccounting.Periode.Name, input + "*").setIgnoreCase(true);
        final MultiPrintQuery multi = queryBuilder.getPrint();
        multi.addAttribute(key);
        multi.addAttribute(CIAccounting.Periode.FromDate, CIAccounting.Periode.ToDate, CIAccounting.Periode.Name);
        multi.execute();
        while (multi.next()) {
            final String keyVal = multi.getAttribute(key).toString();
            final String name = multi.<String> getAttribute(CIAccounting.Periode.Name);
            final DateTime fromDate = multi.<DateTime> getAttribute(CIAccounting.Periode.FromDate);
            final DateTime toDate = multi.<DateTime> getAttribute(CIAccounting.Periode.ToDate);
            final String toDateStr = toDate.toString(DateTimeFormat.forStyle("S-")
                            .withLocale(Context.getThreadContext().getLocale()));
            final String fromDateStr = fromDate.toString(DateTimeFormat.forStyle("S-")
                            .withLocale(Context.getThreadContext().getLocale()));

            final String choice = name + ": " + fromDateStr + " - " + toDateStr;
            final Map<String, String> map = new HashMap<String, String>();
            map.put(EFapsKey.AUTOCOMPLETE_KEY.getKey(), keyVal);
            map.put(EFapsKey.AUTOCOMPLETE_VALUE.getKey(), name);
            map.put(EFapsKey.AUTOCOMPLETE_CHOICE.getKey(), choice);
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
        final MultiPrint multi = new MultiPrint()
        {
            @Override
            protected void add2QueryBldr(final Parameter _parameter,
                                         final QueryBuilder _queryBldr)
                throws EFapsException
            {
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
                                      Status.find(CISales.InvoiceStatus.Open),
                                      Status.find(CISales.InvoiceStatus.Paid),
                                      Status.find(CISales.ReceiptStatus.Open),
                                      Status.find(CISales.ReceiptStatus.Paid),
                                      Status.find(CISales.CreditNoteStatus.Open),
                                      Status.find(CISales.CreditNoteStatus.Paid),
                                      Status.find(CISales.ReminderStatus.Open),
                                      Status.find(CISales.ReminderStatus.Paid));
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
        multi.analyzeTable(_parameter, filter, queryBldr, CISales.DocumentSumAbstract.getType());

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
                                      Status.find(CISales.DeliveryNoteStatus.Closed),
                                      Status.find(CISales.ReturnSlipStatus.Closed));
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
    public Return getPettyCashExternals(final Parameter _parameter)
        throws EFapsException
    {
        final Map<?, ?> properties = (Map<?, ?>) _parameter.get(ParameterValues.PROPERTIES);

        final Return ret = new Return();
        final Instance instance = _parameter.getInstance();
        final PrintQuery print = new PrintQuery(instance);
        print.addAttribute(CIAccounting.Periode.FromDate);
        print.addAttribute(CIAccounting.Periode.ToDate);
        print.execute();
        final DateTime from = print.<DateTime> getAttribute(CIAccounting.Periode.FromDate);
        final DateTime to = print.<DateTime> getAttribute(CIAccounting.Periode.ToDate);

        final List<Status> statusArrayBalance = new ArrayList<Status>();

        final QueryBuilder queryBldr = new QueryBuilder(CISales.PettyCashBalance);
        if (properties.containsKey("PettyCashBalanceStatus")) {
            final String status = (String) properties.get("PettyCashBalanceStatus");
            if (status != null) {
                final String[] statusStr = status.split(",");
                for (final String statusId : statusStr) {
                    statusArrayBalance.add(Status.find(CISales.PettyCashBalanceStatus.uuid, statusId.trim()));
                }
            }
        } else {
            statusArrayBalance.add(Status.find(CISales.PettyCashBalanceStatus.Closed));
        }
        if (!statusArrayBalance.isEmpty()) {
            queryBldr.addWhereAttrEqValue(CISales.PettyCashBalance.Status, statusArrayBalance.toArray());
        }
        final AttributeQuery attrQuery = queryBldr.getAttributeQuery(CISales.PettyCashBalance.ID);

        final QueryBuilder queryBldr2 = new QueryBuilder(CISales.Payment);
        queryBldr2.addWhereAttrInQuery(CISales.Payment.TargetDocument, attrQuery);
        final AttributeQuery attrQuery2 = queryBldr2.getAttributeQuery(CISales.Payment.CreateDocument);

        final List<Status> statusArrayReceipt = new ArrayList<Status>();

        final QueryBuilder attrQueryBldr = new QueryBuilder(CIAccounting.ExternalVoucher2Document);
        final AttributeQuery attrQueryDoc = attrQueryBldr.getAttributeQuery(
                        CIAccounting.ExternalVoucher2Document.ToLink);

        final QueryBuilder queryBldr3 = new QueryBuilder(CISales.PettyCashReceipt);
        if (properties.containsKey("PettyCashReceiptStatus")) {
            final String status = (String) properties.get("PettyCashReceiptStatus");
            if (status != null) {
                final String[] statusStr = status.split(",");
                for (final String statusId : statusStr) {
                    statusArrayReceipt.add(Status.find(CISales.PettyCashReceiptStatus.uuid, statusId.trim()));
                }
            }
        } else {
            statusArrayReceipt.add(Status.find(CISales.PettyCashReceiptStatus.Closed));
        }
        if (!statusArrayReceipt.isEmpty()) {
            queryBldr3.addWhereAttrEqValue(CISales.PettyCashReceipt.Status, statusArrayReceipt.toArray());
        }
        queryBldr3.addWhereAttrGreaterValue(CISales.PettyCashReceipt.Date, from.minusMinutes(1));
        queryBldr3.addWhereAttrLessValue(CISales.PettyCashReceipt.Date, to.plusDays(1));
        queryBldr3.addWhereAttrInQuery(CISales.PettyCashReceipt.ID, attrQuery2);
        queryBldr3.addWhereAttrNotInQuery(CISales.PettyCashReceipt.ID, attrQueryDoc);
        final MultiPrintQuery multi = queryBldr3.getPrint();
        multi.addAttribute(CISales.PettyCashReceipt.Name,
                        CISales.PettyCashReceipt.CrossTotal);
        multi.execute();

        final Map<String, Instance> map = new HashMap<String, Instance>();
        while (multi.next()) {
            final String name = multi.<String>getAttribute(CISales.PettyCashReceipt.Name);
            if (map.containsKey(name)) {
                if (multi.getCurrentInstance().getId() > map.get(name).getId()) {
                    map.put(name, multi.getCurrentInstance());
                }
            } else {
                map.put(name, multi.getCurrentInstance());
            }
            final BigDecimal cross = multi.<BigDecimal>getAttribute(CISales.PettyCashReceipt.CrossTotal);
            if (cross.compareTo(BigDecimal.ZERO) == 0) {
                map.remove(name);
            }
        }

        final List<Long> listIds = new ArrayList<Long>();
        for (final Entry<String, Instance> entry : map.entrySet()) {
            listIds.add(entry.getValue().getId());
        }
        List<Instance> instances = new ArrayList<Instance>();
        if (!listIds.isEmpty()) {
            final QueryBuilder newQuery = new QueryBuilder(CISales.DocumentSumAbstract);
            newQuery.addWhereAttrEqValue(CISales.DocumentSumAbstract.ID, listIds.toArray());

            final Map<?, ?> filter = (Map<?, ?>) _parameter.get(ParameterValues.OTHERS);
            final DocMulti multiDoc = new DocMulti();
            multiDoc.analyzeTable(_parameter, filter, newQuery, CISales.DocumentStockAbstract.getType());

            final InstanceQuery query = newQuery.getQuery();
            instances = query.execute();
        }
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
                                      Status.find(CISales.IncomingInvoiceStatus.Open),
                                      Status.find(CISales.IncomingInvoiceStatus.Paid),
                                      Status.find(CISales.PaymentOrderStatus.Open),
                                      Status.find(CIAccounting.ExternalVoucherStatus.Open),
                                      Status.find(CIAccounting.ExternalVoucherStatus.Paid));
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
                                        Status.find(CISales.IncomingInvoiceStatus.Open),
                                        Status.find(CISales.IncomingInvoiceStatus.Paid),
                                        Status.find(CISales.PaymentOrderStatus.Open),
                                        Status.find(CIAccounting.ExternalVoucherStatus.Open),
                                        Status.find(CIAccounting.ExternalVoucherStatus.Paid));
        queryBldr.addWhereAttrInQuery(CISales.DocumentSumAbstract.ID, attrQuery);

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
    public Return getPaymentToBook(final Parameter _parameter)
        throws EFapsException
    {
        final MultiPrint multi = new MultiPrint()
        {
            @Override
            protected void add2QueryBldr(final Parameter _parameter,
                                         final QueryBuilder _queryBldr)
                throws EFapsException
            {
                _queryBldr.addWhereAttrEqValue(CISales.PaymentDocumentIOAbstract.StatusAbstract,
                                getStati4Payment(_parameter));
            }
        };
        return multi.execute(_parameter);
    }
    /**
     * @param _parameter Parameter as passed by the eFaps API
     * @return Object Array
     * @throws EFapsException on error
     */
    protected Object[] getStati4Payment(final Parameter _parameter)
        throws CacheReloadException
    {
        final List<Status> statuses = new ArrayList<Status>();
        final Set<Type> types = getTypeList(_parameter, CISales.PaymentDocumentIOAbstract.getType());
        for (final Type type : types) {
            if (!type.isAbstract()) {
                final StatusGroup statusGroup = Status.get(type.getStatusAttribute().getLink().getName());
                for (final Entry<String, Status> entry : statusGroup.entrySet()) {
                    if (!"Booked".equals(entry.getKey()) && !"Canceled".equals(entry.getKey())) {
                        statuses.add(entry.getValue());
                    }
                }
            }
        }
        return statuses.toArray();
    }


    /**
     * Recursive method to get a Type with his children and children children
     * as a simple set.
     * @param _parameter    Parameter as passed from the eFaps API
     * @param _type         Type type
     * @throws CacheReloadException on error
     * @return set of types
     */
    protected Set<Type> getTypeList(final Parameter _parameter,
                                    final Type _type)
        throws CacheReloadException
    {
        final Set<Type> ret = new HashSet<Type>();
        ret.add(_type);
        for (final Type child : _type.getChildTypes()) {
            ret.addAll(getTypeList(_parameter, child));
        }
        return ret;
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
