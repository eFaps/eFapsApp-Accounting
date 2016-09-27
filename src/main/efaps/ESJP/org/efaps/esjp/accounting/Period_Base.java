/*
 * Copyright 2003 - 2015 The eFaps Team
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
 */

package org.efaps.esjp.accounting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

import org.efaps.admin.datamodel.Status;
import org.efaps.admin.datamodel.Type;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Parameter.ParameterValues;
import org.efaps.admin.event.Return;
import org.efaps.admin.event.Return.ReturnValues;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.AttributeQuery;
import org.efaps.db.CachedPrintQuery;
import org.efaps.db.Context;
import org.efaps.db.Context.FileParameter;
import org.efaps.db.Delete;
import org.efaps.db.Insert;
import org.efaps.db.Instance;
import org.efaps.db.InstanceQuery;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.PrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.esjp.accounting.Import_Base.ImportAccount;
import org.efaps.esjp.accounting.util.Accounting;
import org.efaps.esjp.accounting.util.Accounting.LabelDefinition;
import org.efaps.esjp.accounting.util.Accounting.SummarizeDefinition;
import org.efaps.esjp.accounting.util.AccountingSettings;
import org.efaps.esjp.admin.access.AccessCheck4UI;
import org.efaps.esjp.admin.common.systemconfiguration.SystemConf;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.ci.CIERP;
import org.efaps.esjp.ci.CIFormAccounting;
import org.efaps.esjp.ci.CISales;
import org.efaps.esjp.common.AbstractCommon;
import org.efaps.esjp.common.parameter.ParameterUtil;
import org.efaps.esjp.common.uitable.MultiPrint;
import org.efaps.esjp.erp.CurrencyInst;
import org.efaps.ui.wicket.util.EFapsKey;
import org.efaps.util.EFapsException;
import org.efaps.util.cache.CacheReloadException;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 */
@EFapsUUID("42e5a308-5d9a-4a8d-a469-b9929010858a")
@EFapsApplication("eFapsApp-Accounting")
public abstract class Period_Base
    extends AbstractCommon
{
    /**
     * CacheKey for Periods.
     */
    protected static final String CACHEKEY = Period.class.getName() + ".CacheKey";

    /**
     * RequestKey for current Periods.
     */
    protected static final String REQKEY4CUR = Period.class.getName() + ".RequestKey4CurrentPeriod";

    /**
     * Default setting to be added on creation of a period.
     */
    protected static final Map<String, String> DEFAULTSETTINGS4PERIOD = new LinkedHashMap<>();
    {
        Period_Base.DEFAULTSETTINGS4PERIOD.put(AccountingSettings.PERIOD_NAME, null);
        Period_Base.DEFAULTSETTINGS4PERIOD.put(AccountingSettings.PERIOD_ROUNDINGCREDIT, null);
        Period_Base.DEFAULTSETTINGS4PERIOD.put(AccountingSettings.PERIOD_ROUNDINGDEBIT, null);
        Period_Base.DEFAULTSETTINGS4PERIOD.put(AccountingSettings.PERIOD_EXCHANGEGAIN, null);
        Period_Base.DEFAULTSETTINGS4PERIOD.put(AccountingSettings.PERIOD_EXCHANGELOSS, null);
        Period_Base.DEFAULTSETTINGS4PERIOD.put(AccountingSettings.PERIOD_TRANSFERACCOUNT, null);
        Period_Base.DEFAULTSETTINGS4PERIOD.put(AccountingSettings.PERIOD_ROUNDINGMAXAMOUNT, "0.05");
        Period_Base.DEFAULTSETTINGS4PERIOD.put(AccountingSettings.PERIOD_REPORT11ACCOUNT, "101");
        Period_Base.DEFAULTSETTINGS4PERIOD.put(AccountingSettings.PERIOD_REPORT302ACCOUNT, "10");
        Period_Base.DEFAULTSETTINGS4PERIOD.put(AccountingSettings.PERIOD_REPORT303ACCOUNT, "12");
        Period_Base.DEFAULTSETTINGS4PERIOD.put(AccountingSettings.PERIOD_REPORT304ACCOUNT, "14");
        Period_Base.DEFAULTSETTINGS4PERIOD.put(AccountingSettings.PERIOD_REPORT305ACCOUNT, "16");
        Period_Base.DEFAULTSETTINGS4PERIOD.put(AccountingSettings.PERIOD_REPORT306ACCOUNT, "19");
        Period_Base.DEFAULTSETTINGS4PERIOD.put(AccountingSettings.PERIOD_REPORT307ACCOUNT, "21;22");
        Period_Base.DEFAULTSETTINGS4PERIOD.put(AccountingSettings.PERIOD_REPORT308ACCOUNT, "31");
        Period_Base.DEFAULTSETTINGS4PERIOD.put(AccountingSettings.PERIOD_REPORT309ACCOUNT, "34");
        Period_Base.DEFAULTSETTINGS4PERIOD.put(AccountingSettings.PERIOD_REPORT310ACCOUNT, "40");
        Period_Base.DEFAULTSETTINGS4PERIOD.put(AccountingSettings.PERIOD_REPORT311ACCOUNT, "41");
        Period_Base.DEFAULTSETTINGS4PERIOD.put(AccountingSettings.PERIOD_REPORT312ACCOUNT, "42");
        Period_Base.DEFAULTSETTINGS4PERIOD.put(AccountingSettings.PERIOD_REPORT313ACCOUNT, "46");
        Period_Base.DEFAULTSETTINGS4PERIOD.put(AccountingSettings.PERIOD_REPORT314ACCOUNT, "47");
        Period_Base.DEFAULTSETTINGS4PERIOD.put(AccountingSettings.PERIOD_REPORT315ACCOUNT, "49");
        Period_Base.DEFAULTSETTINGS4PERIOD.put(AccountingSettings.PERIOD_REPORT316ACCOUNT, "50");
        Period_Base.DEFAULTSETTINGS4PERIOD.put(AccountingSettings.PERIOD_ACTIVATEEXCHANGE, "false");
        Period_Base.DEFAULTSETTINGS4PERIOD.put(AccountingSettings.PERIOD_ACTIVATEVIEWS, "false");
        Period_Base.DEFAULTSETTINGS4PERIOD.put(AccountingSettings.PERIOD_ACTIVATESTOCK, "false");
        Period_Base.DEFAULTSETTINGS4PERIOD.put(AccountingSettings.PERIOD_ACTIVATERETPER, "false");
        Period_Base.DEFAULTSETTINGS4PERIOD.put(AccountingSettings.PERIOD_ACTIVATESECURITIES, "false");
        Period_Base.DEFAULTSETTINGS4PERIOD.put(AccountingSettings.PERIOD_ACTIVATEPAYROLL, "false");
        Period_Base.DEFAULTSETTINGS4PERIOD.put(AccountingSettings.PERIOD_ACTIVATESWAP, "false");
        Period_Base.DEFAULTSETTINGS4PERIOD.put(AccountingSettings.PERIOD_SHOWREPORT, "false");
        Period_Base.DEFAULTSETTINGS4PERIOD.put(AccountingSettings.PERIOD_SUBJOURNAL4PAYOUT, "??");
        Period_Base.DEFAULTSETTINGS4PERIOD.put(AccountingSettings.PERIOD_SUBJOURNAL4PAYIN, "??");
        Period_Base.DEFAULTSETTINGS4PERIOD.put(AccountingSettings.PERIOD_ACTIVATEREMARK4TRANSPOS, "false");
        Period_Base.DEFAULTSETTINGS4PERIOD.put(AccountingSettings.PERIOD_ACTIVATEPETTYCASHWD, "false");
        Period_Base.DEFAULTSETTINGS4PERIOD.put(AccountingSettings.PERIOD_ACTIVATEFTBSWD, "false");
        Period_Base.DEFAULTSETTINGS4PERIOD.put(AccountingSettings.PERIOD_RETENTIONCASE, "");
        Period_Base.DEFAULTSETTINGS4PERIOD.put(AccountingSettings.PERIOD_SUMMARIZETRANS,
                        SummarizeDefinition.CASEUSER.name());
        Period_Base.DEFAULTSETTINGS4PERIOD.put(AccountingSettings.PERIOD_LABELDEF, LabelDefinition.COST.name());
        Period_Base.DEFAULTSETTINGS4PERIOD.put(AccountingSettings.PERIOD_CASEACTIVATECATEGORY, "true");
        Period_Base.DEFAULTSETTINGS4PERIOD.put(AccountingSettings.PERIOD_CASEACTIVATECLASSIFICATION, "true");
        Period_Base.DEFAULTSETTINGS4PERIOD.put(AccountingSettings.PERIOD_CASEACTIVATEFAMILY, "true");
        Period_Base.DEFAULTSETTINGS4PERIOD.put(AccountingSettings.PERIOD_CASEACTIVATEKEY, "true");
        Period_Base.DEFAULTSETTINGS4PERIOD.put(AccountingSettings.PERIOD_CASEACTIVATETREEVIEW, "true");
        Period_Base.DEFAULTSETTINGS4PERIOD.put(AccountingSettings.PERIOD_INCOMINGPERWITHDOC, "true");
    }

    /**
     * Logger for this class.
     */
    private static final Logger LOG = LoggerFactory.getLogger(Period.class);


    /**
     * Access check4 summarize trans.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return the return
     * @throws EFapsException on error
     */
    public Return accessCheck4SummarizeTrans(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final boolean inverse = "true".equalsIgnoreCase(getProperty(_parameter, "Inverse"));
        final SummarizeDefinition summarize = getSummarizeDefinition(_parameter);
        final boolean access;
        switch (summarize) {
            case NEVER:
            case ALWAYS:
                access = false;
                break;
            case CASE:
            case CASEUSER:
            case USER:
                access = true;
                break;
            default:
                access = false;
                break;
        }
        if (!inverse && access || inverse && !access) {
            ret.put(ReturnValues.TRUE, true);
        }
        return ret;
    }


    /**
     * Access check by config.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return the return
     * @throws EFapsException on error
     */
    public Return accessCheckByConfig(final Parameter _parameter)
        throws EFapsException
    {
        final Instance inst = evaluateCurrentPeriod(_parameter);
        final Parameter parameter = ParameterUtil.clone(_parameter, ParameterValues.INSTANCE, inst);
        ParameterUtil.setProperty(parameter, "SystemConfig", Accounting.getSysConfig().getUUID().toString());
        return new AccessCheck4UI().configObjectCheck(parameter);
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
        final Insert insert = new Insert(CIAccounting.Period);
        insert.add(CIAccounting.Period.Name,
                        _parameter.getParameterValue(CIFormAccounting.Accounting_PeriodForm.name.name));
        insert.add(CIAccounting.Period.FromDate,
                        _parameter.getParameterValue(CIFormAccounting.Accounting_PeriodForm.fromDate.name));
        insert.add(CIAccounting.Period.ToDate,
                        _parameter.getParameterValue(CIFormAccounting.Accounting_PeriodForm.toDate.name));
        insert.add(CIAccounting.Period.CurrencyLink,
                        _parameter.getParameterValue(CIFormAccounting.Accounting_PeriodForm.currencyLink.name));
        insert.add(CIAccounting.Period.Status, Status.find(CIAccounting.PeriodStatus.Open));
        insert.execute();
        final Instance periodInst = insert.getInstance();
        final StringBuilder props = new StringBuilder();

        for (final Entry<String, String> entry : Period_Base.DEFAULTSETTINGS4PERIOD.entrySet()) {
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
                        CIFormAccounting.Accounting_PeriodForm.accountTable.name);
        final FileParameter reports = Context.getThreadContext().getFileParameters().get(
                        CIFormAccounting.Accounting_PeriodForm.reports.name);
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
     * @return Instance of the period the currency is wanted for
     * @throws EFapsException on error
     */
    public CurrencyInst getCurrency(final Instance _instance)
        throws EFapsException
    {
        final CurrencyInst ret;
        if (_instance.getType().isKindOf(CIAccounting.TransactionAbstract.getType())) {
            final PrintQuery print = new CachedPrintQuery(_instance, Period_Base.CACHEKEY);
            final SelectBuilder sel = SelectBuilder.get().linkto(CIAccounting.TransactionAbstract.PeriodLink)
                            .linkto(CIAccounting.Period.CurrencyLink).instance();
            print.addSelect(sel);
            print.execute();
            ret = new CurrencyInst(print.<Instance>getSelect(sel));
        } else {
            final PrintQuery print = new CachedPrintQuery(_instance, Period_Base.CACHEKEY);
            final SelectBuilder sel = SelectBuilder.get().linkto(CIAccounting.Period.CurrencyLink).instance();
            print.addSelect(sel);
            print.execute();
            ret = new CurrencyInst(print.<Instance>getSelect(sel));
        }
        return ret;
    }

    /**
     * Evalute current currency.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return the currency inst
     * @throws EFapsException on error
     */
    public CurrencyInst evaluteCurrentCurrency(final Parameter _parameter)
        throws EFapsException
    {
        final Instance periodInst = evaluateCurrentPeriod(_parameter);
        return getCurrency(periodInst);
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
                        CIFormAccounting.Accounting_PeriodForm.accountTable.name);
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
                        CIFormAccounting.Accounting_PeriodForm.accountTable.name);
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
                        CIFormAccounting.Accounting_PeriodForm.accountTable.name);
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
    public Return autoComplete4Period(final Parameter _parameter)
        throws EFapsException
    {
        final String input = (String) _parameter.get(ParameterValues.OTHERS);
        final Map<?, ?> properties = (Map<?, ?>) _parameter.get(ParameterValues.PROPERTIES);
        final String key = properties.containsKey("Key") ? (String) properties.get("Key") : "ID";

        final Map<String, Map<String, String>> orderMap = new TreeMap<>();

        final QueryBuilder queryBuilder = new QueryBuilder(CIAccounting.Period);
        queryBuilder.addWhereAttrMatchValue(CIAccounting.Period.Name, input + "*").setIgnoreCase(true);
        final MultiPrintQuery multi = queryBuilder.getPrint();
        multi.addAttribute(key);
        multi.addAttribute(CIAccounting.Period.FromDate, CIAccounting.Period.ToDate, CIAccounting.Period.Name);
        multi.execute();
        while (multi.next()) {
            final String keyVal = multi.getAttribute(key).toString();
            final String name = multi.<String> getAttribute(CIAccounting.Period.Name);
            final DateTime fromDate = multi.<DateTime> getAttribute(CIAccounting.Period.FromDate);
            final DateTime toDate = multi.<DateTime> getAttribute(CIAccounting.Period.ToDate);
            final String toDateStr = toDate.toString(DateTimeFormat.forStyle("S-")
                            .withLocale(Context.getThreadContext().getLocale()));
            final String fromDateStr = fromDate.toString(DateTimeFormat.forStyle("S-")
                            .withLocale(Context.getThreadContext().getLocale()));

            final String choice = name + ": " + fromDateStr + " - " + toDateStr;
            final Map<String, String> map = new HashMap<>();
            map.put(EFapsKey.AUTOCOMPLETE_KEY.getKey(), keyVal);
            map.put(EFapsKey.AUTOCOMPLETE_VALUE.getKey(), name);
            map.put(EFapsKey.AUTOCOMPLETE_CHOICE.getKey(), choice);
            orderMap.put(choice, map);
        }

        final List<Map<String, String>> list = new ArrayList<>();
        list.addAll(orderMap.values());
        final Return retVal = new Return();
        retVal.put(ReturnValues.VALUES, list);
        return retVal;
    }

    /**
     * @param _parameter Parameter as passed by the eFasp API
     * @return the instance of the Period
     * @throws EFapsException on error
     */
    public Instance evaluateCurrentPeriod(final Parameter _parameter)
        throws EFapsException
    {
        return evaluateCurrentPeriod(_parameter, _parameter.getInstance());
    }

    /**
     * @param _parameter Parameter as passed by the eFasp API
     * @param _instance instance to be used explicitly
     * @return the instance of the Period
     * @throws EFapsException on error
     */
    public Instance evaluateCurrentPeriod(final Parameter _parameter,
                                          final Instance _instance)
        throws EFapsException
    {
        Instance ret = null;
        if (Context.getThreadContext().containsRequestAttribute(Period.REQKEY4CUR)) {
            ret = (Instance) Context.getThreadContext().getRequestAttribute(Period.REQKEY4CUR);
        } else {
            Instance instance = _instance;
            // this happens only on create mode
            if (instance != null && !instance.isValid()
                            || instance == null && _parameter.getCallInstance() != null) {
                instance = _parameter.getCallInstance();
            }

            // for the case of adding new positions to a table on javascript
            if (instance == null || instance == null && !instance.isValid()) {
                instance = Instance.get(_parameter.getParameterValue("eFapsExtraParameter"));
            }
            if (instance != null && instance.isValid()) {
                if (instance.getType().isKindOf(CIAccounting.Period)) {
                    ret = instance;
                } else if (instance.getType().isKindOf(CIAccounting.Account2ObjectAbstract)) {
                    final PrintQuery print = new PrintQuery(instance);
                    final SelectBuilder sel = SelectBuilder.get()
                                    .linkto(CIAccounting.Account2ObjectAbstract.FromAccountAbstractLink)
                                    .linkto(CIAccounting.AccountAbstract.PeriodAbstractLink).instance();
                    print.addSelect(sel);
                    print.execute();
                    ret = print.<Instance>getSelect(sel);
                } else if (instance.getType().isKindOf(CIAccounting.AccountAbstract)) {
                    final PrintQuery print = new PrintQuery(instance);
                    final SelectBuilder sel = SelectBuilder.get()
                                    .linkto(CIAccounting.AccountAbstract.PeriodAbstractLink).instance();
                    print.addSelect(sel);
                    print.execute();
                    ret = print.<Instance>getSelect(sel);
                } else if (instance.getType().isKindOf(CIAccounting.Transaction)) {
                    final PrintQuery print = new PrintQuery(instance);
                    final SelectBuilder selPeriodInst = SelectBuilder.get().linkto(CIAccounting.Transaction.PeriodLink)
                                    .instance();
                    print.addSelect(selPeriodInst);
                    print.execute();
                    ret = print.<Instance>getSelect(selPeriodInst);
                } else if (instance.getType().isKindOf(CIAccounting.SubPeriod)) {
                    final PrintQuery print = new CachedPrintQuery(instance, SubPeriod_Base.CACHEKEY);
                    final SelectBuilder selPeriodInst = SelectBuilder.get().linkto(CIAccounting.SubPeriod.PeriodLink)
                                    .instance();
                    print.addSelect(selPeriodInst);
                    print.execute();
                    ret = print.<Instance>getSelect(selPeriodInst);
                } else if (instance.getType().isKindOf(CIAccounting.TransactionPositionAbstract)) {
                    final PrintQuery print = new PrintQuery(instance);
                    final SelectBuilder selPeriodInst = SelectBuilder.get()
                                    .linkto(CIAccounting.TransactionPositionAbstract.TransactionLink)
                                    .linkto(CIAccounting.Transaction.PeriodLink)
                                    .instance();
                    print.addSelect(selPeriodInst);
                    print.execute();
                    ret = print.<Instance>getSelect(selPeriodInst);
                } else if (instance.getType().isKindOf(CIAccounting.CaseAbstract)) {
                    final PrintQuery print = new PrintQuery(instance);
                    final SelectBuilder selPeriodInst = SelectBuilder.get()
                                    .linkto(CIAccounting.CaseAbstract.PeriodAbstractLink).instance();
                    print.addSelect(selPeriodInst);
                    print.execute();
                    ret = print.<Instance>getSelect(selPeriodInst);
                } else if (instance.getType().isKindOf(CISales.PaymentDocumentIOAbstract)) {
                    final PrintQuery print = new PrintQuery(instance);
                    print.addAttribute(CISales.PaymentDocumentIOAbstract.Date);
                    print.executeWithoutAccessCheck();
                    ret = getCurrentPeriodByDate(_parameter,
                                    print.<DateTime>getAttribute(CISales.PaymentDocumentIOAbstract.Date));
                } else if (instance.getType().isKindOf(CISales.RetentionCertificate)) {
                    final PrintQuery print = new PrintQuery(instance);
                    print.addAttribute(CISales.RetentionCertificate.Date);
                    print.executeWithoutAccessCheck();
                    ret = getCurrentPeriodByDate(_parameter,
                                    print.<DateTime>getAttribute(CISales.RetentionCertificate.Date));
                } else if (instance.getType().isKindOf(CIAccounting.ReportAbstract)) {
                    final PrintQuery print = new PrintQuery(instance);
                    final SelectBuilder sel = SelectBuilder.get()
                                    .linkto(CIAccounting.ReportAbstract.PeriodLink).instance();
                    print.addSelect(sel);
                    print.execute();
                    ret = print.<Instance>getSelect(sel);
                } else if (instance.getType().isKindOf(CIAccounting.ActionDefinition2Case)) {
                    final PrintQuery print = new PrintQuery(instance);
                    final SelectBuilder sel = SelectBuilder.get()
                                    .linkto(CIAccounting.ActionDefinition2Case.ToLinkAbstract)
                                    .linkto(CIAccounting.CaseAbstract.PeriodAbstractLink).instance();
                    print.addSelect(sel);
                    print.execute();
                    ret = print.<Instance>getSelect(sel);
                }
            }
            // last way to get the valid period
            if (ret == null) {
                ret = getCurrentPeriodByDate(_parameter, null);
            } else {
                Context.getThreadContext().setRequestAttribute(Period.REQKEY4CUR, ret);
            }
        }
        return ret;
    }

    /**
     * @param _parameter Parameter as passed by the eFaps API
     * @param _date date the period will be searched for
     * @return instance of the period
     * @throws EFapsException on error
     */
    public Instance getCurrentPeriodByDate(final Parameter _parameter,
                                           final DateTime _date)
        throws EFapsException
    {
        final DateTime date;
        if (_date == null) {
            LOG.warn("Evaluation Period by current Date only!!!!!");
            date = new DateTime();
        } else {
            date = _date;
        }
        Instance ret = null;
        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.Period);
        queryBldr.addWhereAttrEqValue(CIAccounting.Period.Status, Status.find(CIAccounting.PeriodStatus.Open));
        queryBldr.addWhereAttrGreaterValue(CIAccounting.Period.ToDate, date);
        queryBldr.addWhereAttrLessValue(CIAccounting.Period.FromDate, date);
        final InstanceQuery query = queryBldr.getQuery();
        query.executeWithoutAccessCheck();
        if (query.next()) {
            ret = query.getCurrentValue();
        }
        return ret;
    }

    /**
     * Gets the summarize definition.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return the summarize definition
     * @throws EFapsException on error
     */
    public SummarizeDefinition getSummarizeDefinition(final Parameter _parameter)
        throws EFapsException
    {
        final Instance periodInst = evaluateCurrentPeriod(_parameter);
        final Properties props = Accounting.getSysConfig().getObjectAttributeValueAsProperties(periodInst);
        return SummarizeDefinition.valueOf(props.getProperty(
                        AccountingSettings.PERIOD_SUMMARIZETRANS, SummarizeDefinition.NEVER.name()));
    }

    /**
     * Gets the label definition.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return the label definition
     * @throws EFapsException on error
     */
    public LabelDefinition getLabelDefinition(final Parameter _parameter)
        throws EFapsException
    {
        final Instance periodInst = evaluateCurrentPeriod(_parameter);
        final Properties props = Accounting.getSysConfig().getObjectAttributeValueAsProperties(periodInst);
        return LabelDefinition.valueOf(props.getProperty(
                        AccountingSettings.PERIOD_LABELDEF, LabelDefinition.COST.name()));
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
                add2DocQueryBldr(_parameter, _queryBldr);
                final QueryBuilder attrQueryBldr = new QueryBuilder(CIAccounting.Transaction2SalesDocument);
                final AttributeQuery attrQuery = attrQueryBldr
                                .getAttributeQuery(CIAccounting.Transaction2SalesDocument.ToLink);
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
        final MultiPrint multi = new MultiPrint()
        {
            @Override
            protected void add2QueryBldr(final Parameter _parameter,
                                         final QueryBuilder _queryBldr)
                throws EFapsException
            {
                add2DocQueryBldr(_parameter, _queryBldr);
                final QueryBuilder attrQueryBldr = new QueryBuilder(CIAccounting.Transaction2SalesDocument);
                final AttributeQuery attrQuery = attrQueryBldr
                                .getAttributeQuery(CIAccounting.Transaction2SalesDocument.ToLink);
                _queryBldr.addWhereAttrInQuery(CISales.DocumentSumAbstract.ID, attrQuery);
            }
        };
        return multi.execute(_parameter);
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
        print.addAttribute(CIAccounting.Period.FromDate);
        print.addAttribute(CIAccounting.Period.ToDate);
        print.execute();
        final DateTime from = print.<DateTime>getAttribute(CIAccounting.Period.FromDate);
        final DateTime to = print.<DateTime>getAttribute(CIAccounting.Period.ToDate);

        final QueryBuilder attrQueryBldr = new QueryBuilder(CIAccounting.Transaction2SalesDocument);
        final AttributeQuery attrQuery = attrQueryBldr
                                .getAttributeQuery(CIAccounting.Transaction2SalesDocument.ToLink);

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
     * @param _parameter Paremeter
     * @return List if Instances
     * @throws EFapsException on error
     */
    public Return getFundsToBeSettled(final Parameter _parameter)
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
                final PrintQuery print = new CachedPrintQuery(instance, Period_Base.CACHEKEY);
                print.addAttribute(CIAccounting.Period.FromDate);
                print.execute();
                final DateTime from = print.<DateTime>getAttribute(CIAccounting.Period.FromDate);
                _queryBldr.addWhereAttrGreaterValue(CISales.DocumentSumAbstract.Date, from.minusMinutes(1));
                final List<Status> statusArrayBalance = new ArrayList<>();

                final QueryBuilder queryBldr = new QueryBuilder(CISales.FundsToBeSettledBalance);
                if (containsProperty(_parameter, "FundsToBeSettledBalanceStatus")) {
                    for (final String balanceStatus : analyseProperty(_parameter, "FundsToBeSettledBalanceStatus")
                                    .values()) {
                        statusArrayBalance.add(Status.find(CISales.FundsToBeSettledBalanceStatus.uuid, balanceStatus));
                    }
                } else {
                    statusArrayBalance.add(Status.find(CISales.FundsToBeSettledBalanceStatus.Closed));
                }
                if (!statusArrayBalance.isEmpty()) {
                    queryBldr.addWhereAttrEqValue(CISales.FundsToBeSettledBalance.Status, statusArrayBalance.toArray());
                }
                final AttributeQuery attrQuery = queryBldr.getAttributeQuery(CISales.FundsToBeSettledBalance.ID);

                final Properties props = Accounting.getSysConfig().getObjectAttributeValueAsProperties(instance);
                final boolean withoutDoc = "true".equalsIgnoreCase(props
                                .getProperty(AccountingSettings.PERIOD_ACTIVATEFTBSWD));

                final QueryBuilder queryBldr2 = new QueryBuilder(CISales.Document2DocumentAbstract);
                queryBldr2.addWhereAttrInQuery(CISales.Document2DocumentAbstract.FromAbstractLink, attrQuery);
                final AttributeQuery attrQuery2 = queryBldr2
                                .getAttributeQuery(CISales.Document2DocumentAbstract.ToAbstractLink);

                if (withoutDoc) {
                    _queryBldr.addWhereAttrInQuery(CISales.DocumentSumAbstract.ID, attrQuery2);
                } else {
                    final QueryBuilder docTypeAttrQueryBldr = new QueryBuilder(CIERP.Document2DocumentTypeAbstract);
                    docTypeAttrQueryBldr.addWhereAttrInQuery(CIERP.Document2DocumentTypeAbstract.DocumentLinkAbstract,
                                    attrQuery2);
                    final AttributeQuery docTypeAttrQuery = docTypeAttrQueryBldr.getAttributeQuery(
                                    CIERP.Document2DocumentTypeAbstract.DocumentLinkAbstract);

                    _queryBldr.addWhereAttrInQuery(CISales.DocumentSumAbstract.ID, docTypeAttrQuery);
                }
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
    public Return getPettyCashReceipt(final Parameter _parameter)
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
                final PrintQuery print = new CachedPrintQuery(instance, Period_Base.CACHEKEY);
                print.addAttribute(CIAccounting.Period.FromDate);
                print.execute();
                final DateTime from = print.<DateTime>getAttribute(CIAccounting.Period.FromDate);
                _queryBldr.addWhereAttrGreaterValue(CISales.DocumentSumAbstract.Date, from.minusMinutes(1));

                final List<Status> statusArrayBalance = new ArrayList<>();

                final QueryBuilder queryBldr = new QueryBuilder(CISales.PettyCashBalance);
                if (containsProperty(_parameter, "PettyCashBalanceStatus")) {
                    for (final String balanceStatus : analyseProperty(_parameter, "PettyCashBalanceStatus").values()) {
                        statusArrayBalance.add(Status.find(CISales.PettyCashBalanceStatus.uuid, balanceStatus));
                    }
                } else {
                    statusArrayBalance.add(Status.find(CISales.PettyCashBalanceStatus.Closed));
                }
                if (!statusArrayBalance.isEmpty()) {
                    queryBldr.addWhereAttrEqValue(CISales.PettyCashBalance.Status, statusArrayBalance.toArray());
                }
                final AttributeQuery attrQuery = queryBldr.getAttributeQuery(CISales.PettyCashBalance.ID);

                final Properties props = Accounting.getSysConfig().getObjectAttributeValueAsProperties(instance);
                final boolean withoutDoc = "true".equalsIgnoreCase(props
                                .getProperty(AccountingSettings.PERIOD_ACTIVATEPETTYCASHWD));

                final QueryBuilder queryBldr2 = new QueryBuilder(CISales.Document2DocumentAbstract);
                queryBldr2.addWhereAttrInQuery(CISales.Document2DocumentAbstract.FromAbstractLink, attrQuery);
                final AttributeQuery attrQuery2 = queryBldr2
                                .getAttributeQuery(CISales.Document2DocumentAbstract.ToAbstractLink);
                if (withoutDoc) {
                    _queryBldr.addWhereAttrInQuery(CISales.DocumentSumAbstract.ID, attrQuery2);
                } else {
                    final QueryBuilder docTypeAttrQueryBldr = new QueryBuilder(CIERP.Document2DocumentTypeAbstract);
                    docTypeAttrQueryBldr.addWhereAttrInQuery(CIERP.Document2DocumentTypeAbstract.DocumentLinkAbstract,
                                    attrQuery2);
                    final AttributeQuery docTypeAttrQuery = docTypeAttrQueryBldr.getAttributeQuery(
                                    CIERP.Document2DocumentTypeAbstract.DocumentLinkAbstract);

                    _queryBldr.addWhereAttrInQuery(CISales.DocumentSumAbstract.ID, docTypeAttrQuery);
                }
            }
        };
        return multi.execute(_parameter);
    }

    /**
     * Called from a tree menu command to present the documents that are with status
     * booked and therefore must be worked on still.
     * @param _parameter Parameter as passed by the eFaps API
     * @return reeutun with map
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
                add2DocQueryBldr(_parameter, _queryBldr);
                final QueryBuilder attrQueryBldr = new QueryBuilder(CIAccounting.Transaction2SalesDocument);
                final AttributeQuery attrQuery = attrQueryBldr
                                .getAttributeQuery(CIAccounting.Transaction2SalesDocument.ToLink);
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
                add2DocQueryBldr(_parameter, _queryBldr);
                final QueryBuilder attrQueryBldr = new QueryBuilder(CIAccounting.Transaction2SalesDocument);
                final AttributeQuery attrQuery = attrQueryBldr
                                .getAttributeQuery(CIAccounting.Transaction2SalesDocument.ToLink);
                _queryBldr.addWhereAttrInQuery(CISales.DocumentSumAbstract.ID, attrQuery);
            }
        };
        return multi.execute(_parameter);
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
        return new MultiPrint().execute(_parameter);
    }

    /**
     * Called from a tree menu command to present the Account2Account relations
     * for the current period.
     *
     * @param _parameter Paremeter
     * @return List if Instances
     * @throws EFapsException on error
     */
    public Return getAcc2AccMultiPrint(final Parameter _parameter)
        throws EFapsException
    {
        final MultiPrint multi = new MultiPrint()
        {
            @Override
            protected void add2QueryBldr(final Parameter _parameter,
                                         final QueryBuilder _queryBldr)
                throws EFapsException
            {
                final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.AccountAbstract);
                queryBldr.addWhereAttrEqValue(CIAccounting.AccountAbstract.PeriodAbstractLink,
                                _parameter.getInstance());
                final AttributeQuery attrQuery = queryBldr.getAttributeQuery(CIAccounting.AccountAbstract.ID);
                _queryBldr.addWhereAttrInQuery(CIAccounting.Account2AccountAbstract.FromAccountLink, attrQuery);
                _queryBldr.addWhereAttrInQuery(CIAccounting.Account2AccountAbstract.ToAccountLink, attrQuery);
            }
        };
        return multi.execute(_parameter);
    }

    /**
     * Called from a tree menu command to present the documents that are not
     * included in accounting yet.
     *
     * @param _parameter Paremeter
     * @return List if Instances
     * @throws EFapsException on error
     */
    public Return getOthersPay(final Parameter _parameter)
        throws EFapsException
    {
        return new MultiPrint()
        {

            @Override
            protected void add2QueryBldr(final Parameter _parameter,
                                         final QueryBuilder _queryBldr)
                throws EFapsException
            {
                add2DocQueryBldr(_parameter, _queryBldr);
            };

        }.execute(_parameter);
    }

    /**
     * Gets the others collect.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return the others collect
     * @throws EFapsException on error
     */
    public Return getOthersCollect(final Parameter _parameter)
        throws EFapsException
    {
        return new MultiPrint()
        {
            @Override
            protected void add2QueryBldr(final Parameter _parameter,
                                         final QueryBuilder _queryBldr)
                throws EFapsException
            {
                add2DocQueryBldr(_parameter, _queryBldr);
            };
        }.execute(_parameter);
    }

    /**
     * Called from a tree menu command to present the documents that are not
     * included in accounting yet.
     *
     * @param _parameter Paremeter
     * @return List if Instances
     * @throws EFapsException on error
     */
    public Return getPayrollPay(final Parameter _parameter)
        throws EFapsException
    {
        return new MultiPrint()
        {

            @Override
            protected void add2QueryBldr(final Parameter _parameter,
                                         final QueryBuilder _queryBldr)
                throws EFapsException
            {
                add2DocQueryBldr(_parameter, _queryBldr);
            };

        }.execute(_parameter);
    }

    /**
     * Adds to the queryBuilder for Documents.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @param _queryBldr the query bldr
     * @throws EFapsException on error
     */
    protected void add2DocQueryBldr(final Parameter _parameter,
                                    final QueryBuilder _queryBldr)
        throws EFapsException
    {
        final Instance periodInst = evaluateCurrentPeriod(_parameter);

        final PrintQuery print = new PrintQuery(periodInst);
        print.addAttribute(CIAccounting.Period.FromDate);
        print.addAttribute(CIAccounting.Period.ToDate);
        print.execute();
        final DateTime from = print.<DateTime>getAttribute(CIAccounting.Period.FromDate);
        final DateTime to = print.<DateTime>getAttribute(CIAccounting.Period.ToDate);
        _queryBldr.addWhereAttrGreaterValue(CIERP.DocumentAbstract.Date, from.minusMinutes(1));
        _queryBldr.addWhereAttrLessValue(CIERP.DocumentAbstract.Date, to.plusDays(1));

        final QueryBuilder attrQueryBldr = new QueryBuilder(CIAccounting.Period2ERPDocument);
        attrQueryBldr.addWhereAttrEqValue(CIAccounting.Period2ERPDocument.FromLink, periodInst);
        final AttributeQuery attrQuery = attrQueryBldr
                        .getAttributeQuery(CIAccounting.Period2ERPDocument.ToLink);
        _queryBldr.addWhereAttrNotInQuery(CIERP.DocumentAbstract.ID, attrQuery);
    }

    /**
     * Release doc.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return the return
     * @throws EFapsException on error
     */
    public Return releaseDoc(final Parameter _parameter)
        throws EFapsException
    {
        final Instance docInst = _parameter.getCallInstance();
        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.Period2ERPDocument);
        queryBldr.addWhereAttrEqValue(CIAccounting.Period2ERPDocument.ToLink, docInst);
        for (final Instance inst : queryBldr.getQuery().execute()) {
            new Delete(inst).execute();
        }
        return new Return();
    }

    /**
     * Release doc.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return the return
     * @throws EFapsException on error
     */
    public Return assignDoc(final Parameter _parameter)
        throws EFapsException
    {
        for (final Instance docInst : getSelectedInstances(_parameter)) {
            final Instance periodInst = evaluateCurrentPeriod(_parameter, null);
            final Insert insert = new Insert(CIAccounting.Period2ERPDocument);
            insert.add(CIAccounting.Period2ERPDocument.FromLink, periodInst);
            insert.add(CIAccounting.Period2ERPDocument.ToLink, docInst);
            insert.execute();
        }
        return new Return();
    }

    /**
     * Recursive method to get a Type with his children and children children
     * as a simple set.
     * @param _parameter    Parameter as passed from the eFaps API
     * @param _type         Type type
     * @throws CacheReloadException on error
     * @return set of types
     */
    @Override
    protected Set<Type> getTypeList(final Parameter _parameter,
                                    final Type _type)
        throws CacheReloadException
    {
        final Set<Type> ret = new HashSet<>();
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
