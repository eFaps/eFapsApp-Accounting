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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import net.sf.dynamicreports.jasper.builder.JasperReportBuilder;
import net.sf.dynamicreports.report.builder.DynamicReports;
import net.sf.dynamicreports.report.builder.column.TextColumnBuilder;
import net.sf.dynamicreports.report.builder.group.ColumnGroupBuilder;
import net.sf.dynamicreports.report.builder.subtotal.AggregationSubtotalBuilder;
import net.sf.dynamicreports.report.datasource.DRDataSource;
import net.sf.jasperreports.engine.JRDataSource;

import org.apache.commons.lang3.BooleanUtils;
import org.efaps.admin.common.SystemConfiguration;
import org.efaps.admin.datamodel.Status;
import org.efaps.admin.datamodel.Type;
import org.efaps.admin.datamodel.ui.RateUI;
import org.efaps.admin.dbproperty.DBProperties;
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
import org.efaps.db.InstanceQuery;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.PrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.db.Update;
import org.efaps.db.transaction.ConnectionResource;
import org.efaps.esjp.accounting.Case;
import org.efaps.esjp.accounting.Period;
import org.efaps.esjp.accounting.SubPeriod_Base;
import org.efaps.esjp.accounting.util.Accounting;
import org.efaps.esjp.accounting.util.Accounting.SummarizeDefintion;
import org.efaps.esjp.accounting.util.AccountingSettings;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.ci.CIERP;
import org.efaps.esjp.ci.CIFormAccounting;
import org.efaps.esjp.ci.CISales;
import org.efaps.esjp.common.jasperreport.AbstractDynamicReport;
import org.efaps.esjp.common.jasperreport.StandartReport;
import org.efaps.esjp.contacts.Contacts;
import org.efaps.esjp.erp.CommonDocument;
import org.efaps.esjp.erp.Currency;
import org.efaps.esjp.erp.NumberFormatter;
import org.efaps.esjp.erp.RateFormatter;
import org.efaps.esjp.erp.RateInfo;
import org.efaps.esjp.erp.util.ERP;
import org.efaps.esjp.erp.util.ERPSettings;
import org.efaps.esjp.sales.document.AbstractDocument_Base;
import org.efaps.esjp.sales.util.Sales;
import org.efaps.esjp.sales.util.SalesSettings;
import org.efaps.ui.wicket.models.cell.UIFormCell;
import org.efaps.ui.wicket.util.DateUtil;
import org.efaps.util.EFapsException;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for transaction in accounting.
 *
 * @author The eFaps Team
 * @version $Id$
 */
@EFapsUUID("803f24bc-7c4f-4168-97bc-a9cb01872f76")
@EFapsRevision("$Rev$")
public abstract class Transaction_Base
    extends CommonDocument
{
    /**
     * Key for the active Period to store it in the session.
     */
    public static final String PERIOD_SESSIONKEY = Transaction.class.getName() + ".ActiveAccountingPeriod";

    /**
     * Temporary ident key for transactions.
     */
    protected static final String IDENTTEMP = "WMAFESMUDCHIPZAZG";

    /**
     * Logger for this class.
     */
    private static final Logger LOG = LoggerFactory.getLogger(Transaction.class);

    /**
     * Numbering of the transaction.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return new Return
     * @throws EFapsException on error
     */
    public Return renumber(final Parameter _parameter)
        throws EFapsException
    {
        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.TransactionAbstract);
        queryBldr.addWhereAttrEqValue(CIAccounting.TransactionAbstract.StatusAbstract,
                        Status.find(CIAccounting.TransactionStatus.Booked));
        queryBldr.addWhereAttrEqValue(CIAccounting.TransactionAbstract.PeriodLink, _parameter.getInstance());
        queryBldr.addOrderByAttributeAsc(CIAccounting.TransactionAbstract.Date);
        queryBldr.addOrderByAttributeAsc(CIAccounting.TransactionAbstract.Name);
        final MultiPrintQuery multi = queryBldr.getPrint();
        multi.addAttribute(CIAccounting.TransactionAbstract.Name);
        multi.setEnforceSorted(true);
        multi.execute();
        String currentValue = "0";
        while (multi.next()) {
            currentValue = setName(_parameter, multi.getCurrentInstance(),
                            multi.<String>getAttribute(CIAccounting.TransactionAbstract.Name),
                            currentValue, false);
        }
        return new Return();
    }




    /**
     * Numbering of the transaction.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return new Return
     * @throws EFapsException on error
     */
    public Return asignNumber(final Parameter _parameter)
        throws EFapsException
    {
        final DateTime date = new DateTime(
                        _parameter.getParameterValue(CIFormAccounting.Accounting_TransactionAsignNumberForm.date.name));
        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.TransactionAbstract);
        queryBldr.addWhereAttrEqValue(CIAccounting.TransactionAbstract.StatusAbstract,
                        Status.find(CIAccounting.TransactionStatus.Closed));
        queryBldr.addWhereAttrEqValue(CIAccounting.TransactionAbstract.PeriodLink, _parameter.getInstance());
        queryBldr.addWhereAttrLessValue(CIAccounting.TransactionAbstract.Date, date.withTimeAtStartOfDay().plusDays(1));
        queryBldr.addOrderByAttributeAsc(CIAccounting.TransactionAbstract.Date);
        queryBldr.addOrderByAttributeAsc(CIAccounting.TransactionAbstract.Name);
        final MultiPrintQuery multi = queryBldr.getPrint();
        multi.addAttribute(CIAccounting.TransactionAbstract.Name);
        multi.setEnforceSorted(true);
        multi.execute();
        String currentValue = getStartValue(_parameter);
        while (multi.next()) {
            currentValue = setName(_parameter, multi.getCurrentInstance(),
                            multi.<String>getAttribute(CIAccounting.TransactionAbstract.Name),
                            currentValue, true);
        }
        return new Return();
    }

    /**
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return startvalue
     * @throws EFapsException on error
     */
    protected String getStartValue(final Parameter _parameter)
        throws EFapsException
    {
        final String ret;
        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.TransactionAbstract);
        queryBldr.addWhereAttrEqValue(CIAccounting.TransactionAbstract.StatusAbstract,
                        Status.find(CIAccounting.TransactionStatus.Booked));
        queryBldr.addWhereAttrEqValue(CIAccounting.TransactionAbstract.PeriodLink, _parameter.getInstance());
        queryBldr.addOrderByAttributeDesc(CIAccounting.TransactionAbstract.Name);
        final MultiPrintQuery multi = queryBldr.getPrint();
        multi.addAttribute(CIAccounting.TransactionAbstract.Name);
        multi.setEnforceSorted(true);
        multi.execute();
        if (multi.next()) {
            ret = multi.<String>getAttribute(CIAccounting.TransactionAbstract.Name);
        } else {
            ret = "0";
        }
        return ret;
    }

    /**
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @param _transInst instance of the transaction
     * @param _instName current name
     * @param _previousName previous name
     * @param _setStatus set the status
     * @return startvalue
     * @throws EFapsException on error
     */
    protected String setName(final Parameter _parameter,
                             final Instance _transInst,
                             final String _instName,
                             final String _previousName,
                             final boolean _setStatus)
        throws EFapsException
    {
        final Long previous = Long.parseLong(_previousName);
        final Long current = _instName != null && !_instName.isEmpty() ? Long.parseLong(_instName) : -1;
        final String ret = String.valueOf(previous + 1);
        if (previous + 1 != current || _setStatus) {
            final Update update = new Update(_transInst);
            update.add(CIAccounting.TransactionAbstract.Name, ret);
            if (_setStatus) {
                update.add(CIAccounting.TransactionAbstract.StatusAbstract,
                                Status.find(CIAccounting.TransactionStatus.uuid, "Booked").getId());
            }
            update.execute();
        }
        return ret;
    }

    /**
     * Method is only used to store the calling Instance in the session ,so that
     * it can be accessed from the subtables in this form.
     *
     * @param _parameter paremter as passed to an esjp
     * @return new Return
     * @throws EFapsException on error
     */
    public Return setPeriodUIFieldValue(final Parameter _parameter)
        throws EFapsException
    {
        Instance instance = _parameter.getCallInstance();
        if (instance.getType().isKindOf(CIAccounting.AccountAbstract.getType())
                        || instance.getType().isKindOf(CIAccounting.Transaction.getType())) {
            final PrintQuery print = new PrintQuery(instance);
            final SelectBuilder sel = new SelectBuilder().linkto(
                            instance.getType().isKindOf(CIAccounting.AccountAbstract.getType())
                                            ? CIAccounting.AccountAbstract.PeriodAbstractLink
                                            : CIAccounting.Transaction.PeriodLink).oid();
            print.addSelect(sel);
            print.execute();
            instance = Instance.get(print.<String>getSelect(sel));
        } else if (instance.getType().isKindOf(CIAccounting.ReportNodeAbstract.getType())) {
            while (!instance.getType().isKindOf(CIAccounting.ReportNodeRoot.getType())) {
                final PrintQuery print = new PrintQuery(instance);
                final SelectBuilder sel = new SelectBuilder()
                    .linkto(CIAccounting.ReportNodeTree.ParentLinkAbstract).oid();
                print.addSelect(sel);
                print.execute();
                instance = Instance.get(print.<String>getSelect(sel));
            }
            final PrintQuery print = new PrintQuery(instance);
            final SelectBuilder selPeriod = new SelectBuilder()
                .linkto(CIAccounting.ReportNodeRoot.ReportLink)
                .linkto(CIAccounting.ReportAbstract.PeriodLink).instance();
            final SelectBuilder selReport = new SelectBuilder()
                .linkto(CIAccounting.ReportNodeRoot.ReportLink).instance();
            print.addSelect(selReport, selPeriod);
            print.execute();
            instance = print.<Instance>getSelect(selPeriod);
            final Instance reportInst = print.<Instance>getSelect(selReport);
            // for a multiple report there is no instance of a period in specific
            if (reportInst.isValid() && reportInst.getType().isKindOf(CIAccounting.ReportMultipleAbstract.getType())) {
                instance = reportInst;
            }
        } else if (instance.getType().isKindOf(CIAccounting.SubPeriod.getType())) {
            final PrintQuery print = new CachedPrintQuery(instance, SubPeriod_Base.CACHEKEY);
            final SelectBuilder selPeriodInst = SelectBuilder.get().linkto(CIAccounting.SubPeriod.PeriodLink)
                            .instance();
            print.addSelect(selPeriodInst);
            print.execute();
            instance = print.<Instance>getSelect(selPeriodInst);

        }
        Context.getThreadContext().setSessionAttribute(Transaction_Base.PERIOD_SESSIONKEY, instance);
        return new Return();
    }

    /**
     * @param _parameter Parameter as passed by the eFaps API
     * @param _instance Instance of the period or subperiod
     * @param _date     date the rate must be evaluated for
     * @param _currentCurrencyInst instance of the currency the rate is wanted for
     * @return RateInfo instance
     * @throws EFapsException on error
     */
    protected RateInfo evaluateRate(final Parameter _parameter,
                                    final Instance _instance,
                                    final DateTime _date,
                                    final Instance _currentCurrencyInst)
        throws EFapsException
    {
        Instance instance;
        if (_instance.getType().isKindOf(CIAccounting.SubPeriod.getType())) {
            final PrintQuery print = new CachedPrintQuery(_instance, SubPeriod_Base.CACHEKEY);
            final SelectBuilder selPeriodInst = SelectBuilder.get().linkto(CIAccounting.SubPeriod.PeriodLink)
                            .instance();
            print.addSelect(selPeriodInst);
            print.execute();
            instance = print.<Instance>getSelect(selPeriodInst);
        } else {
            instance = _instance;
        }

        final PrintQuery print = new CachedPrintQuery(instance).setLifespan(1).setLifespanUnit(TimeUnit.HOURS);
        final SelectBuilder sel = SelectBuilder.get().linkto(CIAccounting.Period.CurrencyLink).instance();
        print.addSelect(sel);
        print.execute();
        final Instance perCurrInst = print.<Instance>getSelect(sel);
        final Instance baseCurrency = Sales.getSysConfig().getLink(SalesSettings.CURRENCYBASE);

        final Currency currency = getCurrency(_parameter);
        final RateInfo ret;
        if (perCurrInst.equals(baseCurrency) || _currentCurrencyInst == null) {
            ret = currency.evaluateRateInfo(_parameter, _date, _currentCurrencyInst == null ? perCurrInst
                            : _currentCurrencyInst);
        } else {
            ret = currency.evaluateRateInfos(_parameter, _date, _currentCurrencyInst, perCurrInst)[2];
        }
        return ret;
    }

    /**
     * @param _parameter Parameter as passed by the eFaps API
     * @return a Currency Instance
     * @throws EFapsException on error
     */
    protected Currency getCurrency(final Parameter _parameter)
        throws EFapsException
    {
        final Currency ret = new Currency()
        {
            @Override
            protected Type getType4ExchangeRate(final Parameter _parameter)
                throws EFapsException
            {
                Type typeRet;
                if (Accounting.getSysConfig().getAttributeValueAsBoolean(AccountingSettings.CURRATEEQ)) {
                    typeRet = super.getType4ExchangeRate(_parameter);
                } else {
                    typeRet = CIAccounting.ERP_CurrencyRateAccounting.getType();
                }
                return typeRet;
            }
        };
        return ret;
    }

    /**
     * @param _parameter Parameter as passed by the eFaps API
     * @return a RateFormatter Instance
     * @throws EFapsException on error
     */
    protected RateFormatter getRateFormatter(final Parameter _parameter)
        throws EFapsException
    {
        return new RateFormatter();
    }


    /**
     * @param _parameter Parameter as passed by the eFaps API
     * @param _date     date the rate must be evaluated for
     * @param _currentCurrencyInst instance of the currency the rate is wanted for
     * @return RateInfo instance
     * @throws EFapsException on error
     */
    protected RateInfo evaluateRate(final Parameter _parameter,
                                    final DateTime _date,
                                    final Instance _currentCurrencyInst)
        throws EFapsException
    {
        return getCurrency(_parameter).evaluateRateInfo(_parameter, _date, _currentCurrencyInst);
    }

    /**
     * Calculate the sum for one of the tables.
     *
     * @param _parameter Parameter as passed from the eFaps API
     * @param _postFix postfix for the field names
     * @param _pos position
     * @param _amount amount
     * @param _rate rate
     * @return sum for the table
     * @throws EFapsException on error
     */
    protected BigDecimal getSum(final Parameter _parameter,
                                final String _postFix,
                                final Integer _pos,
                                final BigDecimal _amount,
                                final BigDecimal _rate)
        throws EFapsException
    {
        BigDecimal ret = BigDecimal.ZERO;
        try {
            final DecimalFormat formater = NumberFormatter.get().getFormatter(null, null);
            final String[] amounts = _parameter.getParameterValues("amount_" + _postFix);
            final String[] rates = _parameter.getParameterValues("rate_" + _postFix);
            final String[] ratesInv = _parameter.getParameterValues("rate_" + _postFix + RateUI.INVERTEDSUFFIX);
            if (amounts != null) {
                for (int i = 0; i < amounts.length; i++) {
                    BigDecimal amount = amounts[i].isEmpty()
                                    ? BigDecimal.ZERO : (BigDecimal) formater.parse(amounts[i]);

                    BigDecimal rate = rates[i].isEmpty() ? BigDecimal.ONE : (BigDecimal) formater.parse(rates[i]);
                    final boolean rateInv = "true".equalsIgnoreCase(ratesInv[i]);
                    if (rateInv && rate.compareTo(BigDecimal.ZERO) != 0) {
                        rate = BigDecimal.ONE.divide(rate, 12, BigDecimal.ROUND_HALF_UP);
                    }
                    if (_pos != null && i == _pos) {
                        if (_amount != null) {
                            amount = _amount;
                        }
                        if (_rate != null) {
                            rate = _rate;
                        }
                    }
                    if (rate.compareTo(BigDecimal.ZERO) != 0) {
                        if (amount.scale() < 2) {
                            amount = amount.setScale(2);
                        }
                        amount = amount.divide(rate, BigDecimal.ROUND_HALF_UP);
                    }
                    ret = ret.add(amount);
                }
            }
        } catch (final ParseException e) {
            throw new EFapsException(Transaction_Base.class, "getSum.ParseException", e);
        }
        return ret;
    }

    /**
     * Get a rate Object from the user interface.
     *
     * @param _parameter Parameter as passed from the eFaps API.
     * @param _postfix postfix for the fields.
     * @param _index index of the rate.
     * @return Object[] a new object.
     * @throws EFapsException on error.
     */
    public Object[] getRateObject(final Parameter _parameter,
                                  final String _postfix,
                                  final int _index)
        throws EFapsException
    {
        BigDecimal rate = BigDecimal.ONE;
        try {
            final String[] rates = _parameter.getParameterValues("rate" + _postfix);
            rate = (BigDecimal) RateFormatter.get().getFrmt4Rate().parse(rates[_index]);
        } catch (final ParseException e) {
            throw new EFapsException(AbstractDocument_Base.class, "analyzeRate.ParseException", e);
        }
        final boolean rInv = "true".equalsIgnoreCase(_parameter.getParameterValues("rate"
                        + _postfix + RateUI.INVERTEDSUFFIX)[_index]);
        return new Object[] { rInv ? BigDecimal.ONE : rate, rInv ? rate : BigDecimal.ONE };
    }

    /**
     * Executed as a validate event.
     *
     * @param _parameter Parameter as passed from the eFaps API
     * @return new Return
     * @throws EFapsException on error
     */
    public Return validate(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final StringBuilder html = validateUsedName(_parameter);
        if (evalValues(_parameter, "Debit") && evalValues(_parameter, "Credit")) {
            final BigDecimal debit = getSum(_parameter, "Debit", null, null, null);
            final BigDecimal credit = getSum(_parameter, "Credit", null, null, null);
            if (credit.compareTo(BigDecimal.ZERO) == 0 && debit.compareTo(BigDecimal.ZERO) == 0) {
                html.append(DBProperties.getProperty(Transaction.class.getName() + ".noCreateWithZeroTotal"));
                ret.put(ReturnValues.SNIPLETT, html.toString());
            } else if (credit.subtract(debit).compareTo(BigDecimal.ZERO) == 0) {
                if (html.length() == 0) {
                    ret.put(ReturnValues.TRUE, true);
                } else {
                    ret.put(ReturnValues.SNIPLETT, html.toString());
                    ret.put(ReturnValues.TRUE, true);
                }
            } else {
                Instance inst = _parameter.getCallInstance();
                if (!inst.getType().getUUID().equals(CIAccounting.Period.uuid)) {
                    inst = new Period().evaluateCurrentPeriod(_parameter);
                }
                final Instance currinst = new Period().getCurrency(inst).getInstance();
                final PrintQuery query = new PrintQuery(currinst);
                query.addAttribute("Symbol");
                query.execute();
                final String symbol = query.getAttribute("Symbol");
                html.append("Debit: ").append(debit).append(symbol).append(" &lt;&gt; ").append("Credit: ")
                                .append(credit).append(symbol);
                ret.put(ReturnValues.SNIPLETT, html.toString());
            }
        } else {
            ret.put(ReturnValues.SNIPLETT, "Check");
        }
        return ret;
    }

    /**
     * Method to validate if the name of the external voucher was used.
     *
     * @param _parameter as passed from eFaps API.
     * @return StringBuilder
     * @throws EFapsException on error.
     */
    protected StringBuilder validateUsedName(final Parameter _parameter)
        throws EFapsException
    {
        final Map<?, ?> properties = (Map<?, ?>) _parameter.get(ParameterValues.PROPERTIES);
        final String types = (String) properties.get("Types");
        final StringBuilder html = new StringBuilder();
        if (types != null && Type.get(types).isKindOf(CIAccounting.ExternalVoucher.getType())) {
            final Instance contact = Instance.get(_parameter.getParameterValue("contact"));
            final String name = _parameter.getParameterValue("extName");
            if (contact != null && contact.isValid() && name != null) {
                final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.ExternalVoucher);
                queryBldr.addWhereAttrEqValue(CIAccounting.ExternalVoucher.Contact, contact.getId());
                queryBldr.addWhereAttrEqValue(CIAccounting.ExternalVoucher.Name, name);
                final MultiPrintQuery multi = queryBldr.getPrint();
                multi.execute();
                if (multi.next()) {
                    html.append(DBProperties.getProperty("org.efaps.esjp.accounting.transaction.usedName"));
                }
            }
        }
        return html;
    }

    /**
     * @param _parameter Parameter as passed from the eFaps API
     * @param _postFix postfix
     * @return true
     * @throws EFapsException on error
     */
    protected boolean evalValues(final Parameter _parameter,
                                 final String _postFix)
        throws EFapsException
    {
        boolean ret = true;
        final String[] amounts = _parameter.getParameterValues("amount_" + _postFix);
        final String[] rates = _parameter.getParameterValues("rate_" + _postFix);
        final String[] accountOids = _parameter.getParameterValues("accountLink_" + _postFix);
        final RateFormatter frmt = getRateFormatter(_parameter);
        try {
            if (amounts != null && accountOids != null) {
                for (int i = 0; i < amounts.length; i++) {
                    final String amount = amounts[i];
                    final String accountOid = accountOids[i];
                    final BigDecimal rate = amounts[i].length() > 0
                                    ? (BigDecimal) frmt.getFrmt4RateUI().parse(rates[i]) : BigDecimal.ZERO;
                    if (!(amount.length() > 0 && accountOid.length() > 0 && rate.compareTo(BigDecimal.ZERO) != 0)) {
                        ret = false;
                        break;
                    }
                }
            } else {
                ret = false;
            }
        } catch (final ParseException e) {
            Transaction_Base.LOG.error("Catched ParserException", e);
        }
        return ret;
    }

    /**
     * Method for return the instance of the document selected.
     *
     * @param _parameter Parameter as passed from the eFaps API.
     * @return ret Return with value.
     */
    public Return setDocumentUIFieldValue(final Parameter _parameter)
    {
        final Return ret = new Return();
        final Instance docInst = Instance.get(_parameter.getParameterValue("selectedRow"));
        if (docInst.isValid()) {
            ret.put(ReturnValues.VALUES, docInst.getOid());
        }
        return ret;
    }

    /**
     * @param _oldValue old Value
     * @param _oldRate old Rate
     * @param _newRate new Rate
     * @return new Value
     */
    protected BigDecimal getNewValue(final BigDecimal _oldValue,
                                     final BigDecimal _oldRate,
                                     final BigDecimal _newRate)
    {
        BigDecimal ret = BigDecimal.ZERO;
        if (_oldValue.compareTo(BigDecimal.ZERO) != 0) {
            ret = _oldValue.multiply(_oldRate).divide(_newRate, BigDecimal.ROUND_HALF_UP)
                            .setScale(2, BigDecimal.ROUND_HALF_UP);
        }
        return ret;
    }

    /**
     * Method for search contact by name and auto-complete.
     *
     * @param _parameter Parameter as passed from the eFaps API.
     * @return retVal with values.
     * @throws EFapsException on error.
     */
    public Return autoComplete4Contact(final Parameter _parameter)
        throws EFapsException
    {
        final Contacts contacts = new Contacts();
        return contacts.autoComplete4Contact(_parameter);
    }

    protected boolean summarizeTransaction(final Parameter _parameter)
        throws EFapsException
    {
        boolean ret = false;
        final SummarizeDefintion summarize = new Period().getSummarizeDefintion(_parameter);
        switch (summarize) {
            case NEVER:
                ret = false;
                break;
            case ALWAYS:
                ret = true;
                break;
            case CASE:
                final Instance caseInst = Instance.get(_parameter.getParameterValue("case"));
                final PrintQuery print = new CachedPrintQuery(caseInst,Case.CACHEKEY);
                print.addAttribute(CIAccounting.CaseAbstract.Summarize);
                print.executeWithoutAccessCheck();
                ret = BooleanUtils.isTrue(print.<Boolean>getAttribute(CIAccounting.CaseAbstract.Summarize));
                break;
            case CASEUSER:
            case USER:
                ret = "true".equalsIgnoreCase(_parameter.getParameterValue("checkbox4Summarize"));
                break;
            default:
                ret = false;
                break;
        }
        return ret;
    }


    /**
     * Executed the command on the button.
     *
     * @param _parameter Parameter as passed from the eFaps API
     * @return new Return
     * @throws EFapsException on error
     */
    public Return executeButton(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();

        final StringBuilder js = getScript4ExecuteButton(_parameter,
                        DocumentInfo.getCombined(evalDocuments(_parameter), summarizeTransaction(_parameter)));
        ret.put(ReturnValues.SNIPLETT, js.toString());
        return ret;
    }

    /**
     * @param _parameter Parameter as passed by the eFaps API
     * @return new Document
     * @throws EFapsException on error
     */
    public List<DocumentInfo> evalDocuments(final Parameter _parameter)
        throws EFapsException
    {
        final List<DocumentInfo> ret = new ArrayList<>();
        final Instance caseInst = Instance.get(_parameter.getParameterValue("case"));

        String[] docOids = _parameter.getParameterValues("document");
        if (docOids == null) {
            docOids = (String[]) Context.getThreadContext().getSessionAttribute("docOids");
        }
        for (final String docOid : docOids) {
            final Instance docInst = Instance.get(docOid);
            final DocumentInfo docInfo = new DocumentInfo();
            if (caseInst.isValid() || docInst.isValid()
                            && docInst.getType().isKindOf(CIERP.PaymentDocumentAbstract.getType())) {
                try {
                    ret.add(docInfo);
                    final String curr = _parameter.getParameterValue("currencyExternal");
                    final String amountStr = _parameter.getParameterValue("amountExternal");
                    docInfo.setFormater(NumberFormatter.get().getTwoDigitsFormatter());
                    final Instance currInst;
                    if (curr == null && amountStr == null) {
                        docInfo.setInstance(docInst);
                        boolean isCross = false;
                        if (caseInst.isValid()) {
                            final PrintQuery printCase = new CachedPrintQuery(caseInst, Case.CACHEKEY);
                            printCase.addAttribute(CIAccounting.CaseAbstract.IsCross);
                            printCase.execute();
                            isCross = printCase.<Boolean>getAttribute(CIAccounting.CaseAbstract.IsCross);
                        }
                        final PrintQuery print = new PrintQuery(docInst);
                        final SelectBuilder sel;
                        final String attrName;
                        if (docInfo.isPaymentDoc()) {
                            sel = SelectBuilder.get().linkto(CISales.PaymentDocumentAbstract.RateCurrencyLink)
                                            .instance();
                            attrName = CISales.PaymentDocumentAbstract.Amount.name;
                        } else {
                            sel = SelectBuilder.get().linkto(CISales.DocumentSumAbstract.RateCurrencyId)
                                            .instance();
                            attrName = isCross ? CISales.DocumentSumAbstract.RateCrossTotal.name
                                            : CISales.DocumentSumAbstract.RateNetTotal.name;
                        }
                        print.addSelect(sel);
                        print.addAttribute(attrName);
                        print.execute();
                        currInst = print.<Instance>getSelect(sel);
                        docInfo.setAmount(print.<BigDecimal>getAttribute(attrName));
                    } else {
                        docInfo.setAmount((BigDecimal) docInfo.getFormater().parse(
                                        amountStr.isEmpty() ? "0" : amountStr));
                        currInst = Instance.get(CIERP.Currency.getType(), Long.parseLong(curr));
                    }

                    final String dateStr = _parameter.getParameterValue("date_eFapsDate");
                    docInfo.setDate(DateUtil.getDateFromParameter(dateStr));

                    final RateInfo rateInfo = evaluateRate(_parameter, docInfo.getDate(), currInst);
                    docInfo.setRateInfo(rateInfo);

                    add2Doc4Case(_parameter, docInfo);

                    if (docInfo.getInstance() != null) {
                        docInfo.setInvert(docInfo.getInstance().getType().isKindOf(CISales.ReturnSlip.getType()));
                        add2Doc4BankCash(_parameter, docInfo);
                    }
                    add2Doc4SalesTransaction(_parameter, docInfo);
                    add2Doc4Payment(_parameter, docInfo);
                } catch (final ParseException e) {
                    throw new EFapsException(Transaction_Base.class, "executeButton.ParseException", e);
                }
            }
        }
        return ret;
    }

    /**
     * @param _parameter    Parameter as passed by the eFaps API
     * @param _doc          Document the calculation must be done for
     * @throws EFapsException on error
     */
    protected void add2Doc4Case(final Parameter _parameter,
                                final DocumentInfo _doc)
        throws EFapsException
    {
        final Instance caseInst = Instance.get(_parameter.getParameterValue("case"));
        if (caseInst.isValid()) {
            _doc.setCaseInst(caseInst);
            final RateInfo rateInfo = _doc.getRateInfo();
            final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.Account2CaseAbstract);
            queryBldr.addWhereAttrEqValue(CIAccounting.Account2CaseAbstract.ToCaseAbstractLink, caseInst);
            queryBldr.addOrderByAttributeAsc(CIAccounting.Account2CaseAbstract.Order);
            final MultiPrintQuery multi = queryBldr.getPrint();
            multi.setEnforceSorted(true);
            final SelectBuilder selInst = new SelectBuilder()
                            .linkto(CIAccounting.Account2CaseAbstract.FromAccountAbstractLink).instance();
            multi.addAttribute(CIAccounting.Account2CaseAbstract.Numerator,
                            CIAccounting.Account2CaseAbstract.Denominator,
                            CIAccounting.Account2CaseAbstract.LinkValue,
                            CIAccounting.Account2CaseAbstract.Default);
            multi.addSelect(selInst);
            multi.execute();
            while (multi.next()) {
                final Type type = multi.getCurrentInstance().getType();
                final boolean classRel = type.equals(CIAccounting.Account2CaseCredit4Classification.getType())
                                || type.equals(CIAccounting.Account2CaseDebit4Classification.getType());
                final Boolean isDefault = multi.<Boolean>getAttribute(CIAccounting.Account2CaseAbstract.Default);
                // classRel or default selected will be added
                boolean add = classRel || isDefault;
                if (add) {
                    final Instance inst = multi.<Instance>getSelect(selInst);
                    final Integer denom = multi.<Integer>getAttribute(CIAccounting.Account2CaseAbstract.Denominator);
                    final Integer numer = multi.<Integer>getAttribute(CIAccounting.Account2CaseAbstract.Numerator);
                    final Long linkId = multi.<Long>getAttribute(CIAccounting.Account2CaseAbstract.LinkValue);
                    final BigDecimal mul = new BigDecimal(numer).setScale(12).divide(new BigDecimal(denom),
                                    BigDecimal.ROUND_HALF_UP);
                    final BigDecimal accAmount;

                    if (classRel) {
                        accAmount = mul.multiply(_doc.getAmount4Class(linkId)).setScale(2, BigDecimal.ROUND_HALF_UP);
                        add = isDefault || accAmount.compareTo(BigDecimal.ZERO) != 0;
                    } else {
                        accAmount = mul.multiply(_doc.getAmount()).setScale(2, BigDecimal.ROUND_HALF_UP);
                    }

                    final BigDecimal accAmountRate = accAmount.setScale(12, BigDecimal.ROUND_HALF_UP)
                                    .divide(rateInfo.getRate(), BigDecimal.ROUND_HALF_UP);

                    if (add) {
                        final AccountInfo account = new AccountInfo(inst, accAmount);
                        account.setAmountRate(accAmountRate);
                        account.setRateInfo(rateInfo);
                        if (type.getUUID().equals(CIAccounting.Account2CaseCredit.uuid)
                                        || type.equals(CIAccounting.Account2CaseCredit4Classification.getType())) {
                            account.setPostFix("_Credit");
                            _doc.addCredit(account);
                        } else {
                            account.setPostFix("_Debit");
                            _doc.addDebit(account);
                        }
                    }
                }
            }
        }
    }

    /**
     * method for add account bank cash in case existing DocumentInfo instance.
     *
     * @param _parameter Parameter as passed from the eFaps API.
     * @param _doc Document.
     * @throws EFapsException on error.
     */
    protected void add2Doc4SalesTransaction(final Parameter _parameter,
                                            final DocumentInfo _doc)
        throws EFapsException
    {
        if (_doc.getInstance() != null && _doc.getInstance().isValid()
                        && _doc.getInstance().getType().isKindOf(CIERP.PaymentDocumentAbstract.getType())) {

            final QueryBuilder attrQueryBldr = new QueryBuilder(CISales.Payment);
            attrQueryBldr.addWhereAttrEqValue(CISales.Payment.TargetDocument, _doc.getInstance());
            final AttributeQuery attrQuery = attrQueryBldr.getAttributeQuery(CISales.Payment.ID);

            final QueryBuilder queryBldr = new QueryBuilder(CISales.TransactionAbstract);
            queryBldr.addWhereAttrInQuery(CISales.TransactionAbstract.Payment, attrQuery);
            final MultiPrintQuery multi = queryBldr.getPrint();
            final SelectBuilder selCurInst = SelectBuilder.get().linkto(CISales.TransactionAbstract.CurrencyId)
                            .instance();
            final SelectBuilder selSalesAccInst = SelectBuilder.get().linkto(CISales.TransactionAbstract.Account)
                            .instance();
            multi.addSelect(selCurInst, selSalesAccInst);
            multi.addAttribute(CISales.TransactionAbstract.Amount);
            multi.execute();
            new Period().evaluateCurrentPeriod(_parameter);
            while (multi.next()) {
                final BigDecimal amount = multi.<BigDecimal>getAttribute(CISales.TransactionAbstract.Amount);
                final Instance salesAccInst = multi.<Instance>getSelect(selSalesAccInst);
                final AccountInfo account = getTargetAccount4SalesAccount(_parameter, salesAccInst).addAmount(amount);
                account.setRateInfo(_doc.getRateInfo());
                if (multi.getCurrentInstance().getType().isKindOf(CISales.TransactionInbound.getType())) {
                    _doc.addDebit(account);
                } else {
                    _doc.addCredit(account);
                }
            }
        }
    }

    /**
     * Get the Source account for a Transaction with PaymentDocument.
     *
     * @param _parameter Parameter as passe by the eFaps API
     * @param _doc Doc to add to
     * @throws EFapsException on error
     */
    protected void add2Doc4Payment(final Parameter _parameter,
                                   final DocumentInfo _doc)
        throws EFapsException
    {
        if (_doc.getInstance() != null && _doc.getInstance().isValid()
                        && _doc.getInstance().getType().isKindOf(CISales.PaymentDocumentIOAbstract.getType())) {
            final QueryBuilder queryBldr = new QueryBuilder(CIERP.Document2PaymentDocumentAbstract);
            queryBldr.addWhereAttrEqValue(CIERP.Document2PaymentDocumentAbstract.ToAbstractLink, _doc.getInstance());
            final MultiPrintQuery multi = queryBldr.getPrint();
            final SelectBuilder selDocInst = new SelectBuilder().linkto(
                            CIERP.Document2PaymentDocumentAbstract.FromAbstractLink).instance();
            final SelectBuilder selCurInst = new SelectBuilder().linkto(
                            CIERP.Document2PaymentDocumentAbstract.CurrencyLink).instance();
            multi.addSelect(selDocInst, selCurInst);
            multi.addAttribute(CIERP.Document2PaymentDocumentAbstract.Amount,
                            CIERP.Document2PaymentDocumentAbstract.Date);
            multi.execute();
            while (multi.next()) {
                final Instance docInst = multi.<Instance>getSelect(selDocInst);
                if (docInst.isValid()) {

                    final QueryBuilder attrQueryBldr = new QueryBuilder(CIAccounting.Transaction2SalesDocument);
                    attrQueryBldr.addWhereAttrEqValue(CIAccounting.Transaction2SalesDocument.ToLink, docInst);
                    final AttributeQuery attrQuery = attrQueryBldr
                                    .getAttributeQuery(CIAccounting.Transaction2SalesDocument.FromLink);
                    final boolean outDoc = _doc.getInstance().getType().isKindOf(
                                    CISales.PaymentDocumentOutAbstract.getType());
                    final QueryBuilder posQueryBldr = new QueryBuilder(outDoc
                                    ? CIAccounting.TransactionPositionCredit
                                    : CIAccounting.TransactionPositionDebit);
                    posQueryBldr.addWhereAttrInQuery(CIAccounting.TransactionPositionAbstract.TransactionLink,
                                    attrQuery);
                    final MultiPrintQuery posMulti = posQueryBldr.getPrint();
                    final SelectBuilder selAccInst = new SelectBuilder().linkto(
                                    CIAccounting.TransactionPositionAbstract.AccountLink).instance();
                    final SelectBuilder dateSel = new SelectBuilder().linkto(
                                    CIAccounting.TransactionPositionAbstract.TransactionLink)
                                    .attribute(CIAccounting.Transaction.Date);
                    posMulti.addSelect(selAccInst, dateSel);
                    posMulti.execute();
                    DateTime dateTmp = new DateTime().plusYears(100);
                    while (posMulti.next()) {
                        final DateTime date = posMulti.<DateTime>getSelect(dateSel);
                        if (date != null && date.isBefore(dateTmp)) {
                            dateTmp = date;
                            final Instance accInst = posMulti.<Instance>getSelect(selAccInst);
                            final AccountInfo acc = new AccountInfo().setInstance(accInst)
                                            .addAmount(_doc.getAmount()).setRateInfo(_doc.getRateInfo());
                            if (outDoc) {
                                _doc.addDebit(acc);
                            } else {
                                _doc.addCredit(acc);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * method for add account bank cash in case existing document instance.
     *
     * @param _parameter Parameter as passed from the eFaps API.
     * @param _doc Document.
     * @throws EFapsException on error.
     */
    protected void add2Doc4BankCash(final Parameter _parameter,
                                    final DocumentInfo _doc)
        throws EFapsException
    {
        final Map<?, ?> properties = (Map<?, ?>) _parameter.get(ParameterValues.PROPERTIES);
        final String addAccount = (String) properties.get("addAccount");
        if (addAccount != null && addAccount.length() > 0) {
            final UIFormCell uiform = (UIFormCell) _parameter.get(ParameterValues.CLASS);
            final Instance instance = Instance.get(uiform.getParent().getInstanceKey());
            final BigDecimal amount = _doc.getAmount();

            final AccountInfo account = new AccountInfo(instance);
            account.setRateInfo(_doc.getRateInfo());

            if ("Credit".equals(addAccount)) {
                account.setAmount(amount.subtract(_doc.getCreditSum()));
                _doc.addCredit(account);
            } else {
                account.setAmount(amount.subtract(_doc.getDebitSum()));
                _doc.addDebit(account);
            }
        }
    }

    /**
     * Get the Target account for a Transaction with PaymentDocument.
     * @param _parameter        Parameter as passe by the eFaps API
     * @param _salesAccInst     Instance of the Account from sales the account is searched for
     * @return Instance of the target account
     * @throws EFapsException on error
     */
    protected AccountInfo getTargetAccount4SalesAccount(final Parameter _parameter,
                                                          final Instance _salesAccInst)
        throws EFapsException
    {
        final AccountInfo ret = new AccountInfo();
        if (_salesAccInst.isValid()) {
            final Instance periodInst = new Period().evaluateCurrentPeriod(_parameter);
            final QueryBuilder accQueryBldr = new QueryBuilder(CIAccounting.Period2Account);
            accQueryBldr.addWhereAttrEqValue(CIAccounting.Period2Account.SalesAccountLink, _salesAccInst);
            accQueryBldr.addWhereAttrEqValue(CIAccounting.Period2Account.PeriodLink, periodInst);
            final MultiPrintQuery accMulti = accQueryBldr.getPrint();
            final SelectBuilder selAccInst = SelectBuilder.get().linkto(
                            CIAccounting.Period2Account.FromAccountAbstractLink).instance();
            accMulti.addSelect(selAccInst);
            accMulti.execute();
            if (accMulti.next()) {
                ret.setInstance(accMulti.<Instance>getSelect(selAccInst));
            }
        }
        return ret;
    }

    /**
     * @param _parameter Parameter as passed by the eFasp API
     * @param _doc      doc the script must be build for
     * @return StringBuilder
     * @throws EFapsException on error
     */
    protected StringBuilder getScript4ExecuteButton(final Parameter _parameter,
                                                    final DocumentInfo _doc)
        throws EFapsException
    {
        final StringBuilder js = new StringBuilder()
            .append(getSetFieldValue(0, "sumDebit", _doc.getDebitSumFormated()))
            .append(getSetFieldValue(0, "sumCredit", _doc.getCreditSumFormated()))
            .append(getSetFieldValue(0, "sumTotal", _doc.getDifferenceFormated()))
            .append(getSetSubJournalScript(_parameter, _doc))
            .append(getTableJS(_parameter, "Debit", _doc.getDebitAccounts()))
            .append(getTableJS(_parameter, "Credit", _doc.getCreditAccounts()));

        final String desc = _doc.getDescription(_parameter);
        if (desc != null) {
            js.append(getSetFieldValue(0, "description", desc));
        }
        return js;
    }

    /**
     * @param _parameter    Parameter as passed by the eFaps API
     * @param _postFix      postFix
     * @param _accounts     accounts the script will be created for
     * @return javascript
     * @throws EFapsException on error
     */
    protected StringBuilder getTableJS(final Parameter _parameter,
                                       final String _postFix,
                                       final Collection<AccountInfo> _accounts)
        throws EFapsException
    {
        final String tableName = "transactionPosition" + _postFix + "Table";
        final StringBuilder ret = new StringBuilder()
                        .append(getTableRemoveScript(_parameter, tableName));
        final StringBuilder onJs = new StringBuilder();
        final Collection<Map<String, Object>> values = new ArrayList<Map<String, Object>>();
        int i = 0;
        for (final AccountInfo account : _accounts) {
            final Map<String, Object> map = new HashMap<String, Object>();
            values.add(map);

            map.put("amount_" + _postFix, account.getAmountFormated());
            map.put("rateCurrencyLink_" + _postFix, account.getRateInfo().getCurrencyInst().getInstance().getId());
            map.put("rate_" + _postFix, account.getRateInfo().getRateUIFrmt());
            map.put("rate_" + _postFix + RateUI.INVERTEDSUFFIX, account.getRateInfo().getCurrencyInst().isInvert());
            map.put("amountRate_" + _postFix, account.getAmountRateFormated());
            map.put("accountLink_" + _postFix, new String[] { account.getInstance().getOid(), account.getName() });
            map.put("description_" + _postFix, account.getDescription());

            if (account.getLinkHtml() != null && account.getLinkHtml().length() > 0) {
                onJs.append("document.getElementsByName('account2account_")
                                .append(_postFix).append("')[").append(i).append("].innerHTML='")
                                .append(account.getLinkHtml().toString().replaceAll("'", "\\\\\\'")).append("';");
            }

            if (account.getDocLink() != null && account.getDocLink().isValid()) {
                onJs.append(getSetFieldValue(i, "docLink_" + _postFix, account.getDocLink().getOid()));
            }
            i++;
        }
        ret.append(getTableAddNewRowsScript(_parameter, tableName, values, onJs));
        return ret;
    }

    /**
     * @param _parameter Parameter as passed from the eFaps API.
     * @param _doc  Document
     * @return StringBuilder
     * @throws EFapsException on error
     */
    protected StringBuilder getSetSubJournalScript(final Parameter _parameter,
                                                   final DocumentInfo _doc)
        throws EFapsException
    {
        // check if the field is existing
        final StringBuilder ret = new StringBuilder();
        if (_parameter.getParameterValue("subJournal") != null) {
            final Instance caseInst = Instance.get(_parameter.getParameterValue("case"));
            if (caseInst.isValid()) {
                final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.Report2Case);
                queryBldr.addWhereAttrEqValue(CIAccounting.Report2Case.ToLink, caseInst.getId());
                final MultiPrintQuery multi = queryBldr.getPrint();
                final SelectBuilder sel = new SelectBuilder().linkto(CIAccounting.Report2Case.FromLink).oid();
                multi.addSelect(sel);
                multi.execute();
                if (multi.next()) {
                    ret.append(getSetFieldValue(0, "subJournal", multi.<String>getSelect(sel)));
                }
            }
        }
        return ret;
    }

    /**
     * @param _parameter Parameter as passed  by the eFaps API
     * @return array containing { fromDate, toDate }
     * @throws EFapsException on error
     */
    protected DateTime[] getDateMaxMin(final Parameter _parameter)
        throws EFapsException
    {
        Instance periodInst = _parameter.getInstance();
        DateTime fromDate = null;
        DateTime toDate = null;
        if (periodInst.getType().isKindOf(CIAccounting.Period.getType())) {
            final PrintQuery print = new CachedPrintQuery(periodInst, Period.CACHEKEY);
            print.addAttribute(CIAccounting.Period.FromDate, CIAccounting.Period.ToDate);
            print.execute();
            fromDate = print.<DateTime>getAttribute(CIAccounting.Period.FromDate);
            toDate = print.<DateTime>getAttribute(CIAccounting.Period.ToDate);
        } else if (periodInst.getType().isKindOf(CIAccounting.SubPeriod.getType())) {
            final PrintQuery print = new CachedPrintQuery(periodInst, SubPeriod_Base.CACHEKEY);
            final SelectBuilder selPeriod = SelectBuilder.get().linkto(CIAccounting.SubPeriod.PeriodLink).instance();
            print.addSelect(selPeriod);
            print.addAttribute(CIAccounting.SubPeriod.FromDate, CIAccounting.SubPeriod.ToDate);
            print.execute();
            fromDate = print.<DateTime>getAttribute(CIAccounting.SubPeriod.FromDate);
            toDate = print.<DateTime>getAttribute(CIAccounting.SubPeriod.ToDate);
            periodInst = print.getSelect(selPeriod);
        } else if (periodInst.getType().isKindOf(CIAccounting.TransactionAbstract.getType())) {
            final PrintQuery print = new CachedPrintQuery(periodInst, Period.CACHEKEY);
            final SelectBuilder selPeriod = SelectBuilder.get().linkto(CIAccounting.TransactionAbstract.PeriodLink);
            final SelectBuilder selInst = new SelectBuilder(selPeriod).instance();
            final SelectBuilder selDateFrom = new SelectBuilder(selPeriod).attribute(CIAccounting.Period.FromDate);
            final SelectBuilder selDateTo = new SelectBuilder(selPeriod).attribute(CIAccounting.Period.ToDate);
            print.addSelect(selInst, selDateFrom, selDateTo);
            print.execute();
            periodInst = print.getSelect(selInst);
            fromDate = print.getSelect(selDateFrom);
            toDate = print.getSelect(selDateTo);
        }
        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.Transaction);
        queryBldr.addWhereAttrEqValue(CIAccounting.Transaction.PeriodLink, periodInst);
        queryBldr.addWhereAttrEqValue(CIAccounting.Transaction.Status,
                        Status.find(CIAccounting.TransactionStatus.Booked));
        queryBldr.addOrderByAttributeDesc(CIAccounting.Transaction.Date);
        final InstanceQuery query = queryBldr.getQuery();
        query.setLimit(1);
        final MultiPrintQuery multi = new MultiPrintQuery(query.executeWithoutAccessCheck());
        multi.addAttribute(CIAccounting.Transaction.Date);
        if (multi.executeWithoutAccessCheck()) {
            final DateTime fromDate2 = multi.getAttribute(CIAccounting.Transaction.Date);
            if (fromDate == null) {
                fromDate = fromDate2;
            } else if (fromDate.isBefore(fromDate2)) {
                fromDate = fromDate2;
            }
        }
        return new DateTime[] { fromDate, toDate };
    }


    /**
     * @param _parameter Parameter as passed from eFaps API
     * @return Return conting validation result
     * @throws EFapsException on error
     */
    public Return validateEdit4CheckAmount(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();

        final Instance transPosInst = _parameter.getInstance();

        if (transPosInst.isValid()) {
            final String rateAmountStr = _parameter
                            .getParameterValue(CIFormAccounting.Accounting_TransactionPositionForm.rateAmount.name);
            final String amountStr = _parameter
                            .getParameterValue(CIFormAccounting.Accounting_TransactionPositionForm.amount.name);

            if (!validateAmounts4EditTransactionPos(_parameter, transPosInst, rateAmountStr, amountStr)) {
                ret.put(ReturnValues.SNIPLETT, DBProperties
                        .getProperty("org.efaps.esjp.accounting.transaction.Transaction.NonEdit4TransactionPosition"));
            } else {
                ret.put(ReturnValues.TRUE, true);
            }
        }
        return ret;
    }

    /**
     * @param _parameter Parameter as passed from eFaps API
     * @param _instance insatcne to be checked
     * @param _amounts  amounts to be checked
     * @return true if valid else false
     * @throws EFapsException on error
     */
    protected boolean validateAmounts4EditTransactionPos(final Parameter _parameter,
                                                         final Instance _instance,
                                                         final String... _amounts)
        throws EFapsException
    {
        final DecimalFormat formatter = NumberFormatter.get().getFormatter(null, 2);

        boolean ret = true;

        if (_amounts != null && _amounts.length > 0) {
            for (final String amountStr : _amounts) {
                try {
                    final BigDecimal amount = (BigDecimal) formatter.parse(amountStr);
                    if (CIAccounting.TransactionPositionCredit.getType().equals(_instance.getType())) {
                        if (amount.signum() < 0) {
                            ret = false;
                            break;
                        }
                    } else if (CIAccounting.TransactionPositionDebit.getType().equals(_instance.getType())) {
                        if (amount.signum() > 0) {
                            ret = false;
                            break;
                        }
                    }
                } catch (final ParseException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }

        return ret;
    }
    /**
     * @param _parameter Parameter as passed from the eFaps API
     * @return new Return containing snipplet
     * @throws EFapsException on error
     */
    public Return getHtml4InvalidTransactions(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final AbstractDynamicReport dyRp = new TransactionInvalid();
        dyRp.setFileName("NO LO");
        final String html = dyRp.getHtmlSnipplet(_parameter);
        ret.put(ReturnValues.SNIPLETT, html);
        return ret;
    }

    /**
     * @param _parameter Parameter as passed from the eFaps API
     * @return new Return containing report
     * @throws EFapsException on error
     */
    public Return printReport(final Parameter _parameter)
        throws EFapsException
    {
        final StandartReport report = new StandartReport();

        final SystemConfiguration config = ERP.getSysConfig();
        if (config != null) {
            final String companyName = config.getAttributeValue(ERPSettings.COMPANYNAME);
            final String companyTaxNumb = config.getAttributeValue(ERPSettings.COMPANYTAX);

            if (companyName != null && companyTaxNumb != null && !companyName.isEmpty() && !companyTaxNumb.isEmpty()) {
                report.getJrParameters().put("CompanyName", companyName);
                report.getJrParameters().put("CompanyTaxNum", companyTaxNumb);
            }
        }

        return report.execute(_parameter);
    }

    /**
     *
     */
    public class TransactionInvalid
        extends AbstractDynamicReport
    {

        @Override
        protected JRDataSource createDataSource(final Parameter _parameter)
            throws EFapsException
        {
            final DRDataSource dataSource = new DRDataSource("date", "transaction", "accountName",
                            "accountDescription", "debit", "credit");
            final List<Map<String, Object>> lst = new ArrayList<Map<String, Object>>();
            ConnectionResource con = null;
            final String complStmt = "select date,descr,accName,accDescr,amount,rateamount,S1"
                        + " from t_acctransaction t0 inner join "
                        + " (select t1.transactionid,amount,rateamount,t2.name as accName,t2.descr as accDescr,S1"
                        + " from t_acctransactionpos t1 inner join"
                        + " t_accaccount t2 on t1.accountid=t2.id"
                        + " inner join"
                        + " (select transactionid, sum(amount) as S1 from t_acctransactionpos group by transactionid)"
                        + " as t3 on t1.transactionid=t3.transactionid where t3.S1>0 or t3.S1<0) as t4"
                        + " on t0.id=t4.transactionid";
            try {
                con = Context.getThreadContext().getConnectionResource();
                Statement stmt = null;

                try {
                    stmt = con.getConnection().createStatement();
                    final ResultSet rs = stmt.executeQuery(complStmt);
                    while (rs.next()) {
                        final Map<String, Object> map = new HashMap<String, Object>();
                        map.put("date", rs.getDate("date"));
                        map.put("transaction", rs.getString("descr"));
                        map.put("accountName", rs.getString("accName"));
                        map.put("accountDescription", rs.getString("accDescr"));
                        final BigDecimal amount = rs.getBigDecimal("amount");
                        map.put("debit", amount.signum() > 0 ? amount : null);
                        map.put("credit", amount.signum() < 0 ? amount.abs() : null);
                        lst.add(map);
                    }
                    rs.close();
                } finally {
                    if (stmt != null) {
                        stmt.close();
                    }
                }
                con.commit();
            } catch (final SQLException e) {
                throw new EFapsException(Transaction_Base.class, "executeQuery4InvalidTransactions", e);
            } finally {
                if (con != null && con.isOpened()) {
                    con.abort();
                }
            }

            Collections.sort(lst, new Comparator<Map<String, Object>>()
            {

                @Override
                public int compare(final Map<String, Object> _o1,
                                   final Map<String, Object> _o2)
                {
                    final Date date1 = (Date) _o1.get("date");
                    final Date date2 = (Date) _o2.get("date");
                    final int ret;
                    if (date1.equals(date2)) {
                        final String txn1 = (String) _o1.get("transaction");
                        final String txn2 = (String) _o2.get("transaction");
                        ret = txn1.compareTo(txn2);
                    } else {
                        ret = date1.compareTo(date2);
                    }
                    return ret;
                }
            });

            for (final Map<String, Object> map : lst) {
                dataSource.add(map.get("date"),
                                map.get("transaction"),
                                map.get("accountName"),
                                map.get("accountDescription"),
                                map.get("debit"),
                                map.get("credit"));
            }
            return dataSource;
        }

        /**
         * @param _parameter Parameter as passed from the eFaps API
         * @param _queryBldr QueryBuilder the criteria will be added to
         * @throws EFapsException on error
         */
        protected void add2QueryBuilder(final Parameter _parameter,
                                        final QueryBuilder _queryBldr)
            throws EFapsException
        {
            // to be implemented by subclasses
        }

        @Override
        protected void addColumnDefintion(final Parameter _parameter,
                                          final JasperReportBuilder _builder)
            throws EFapsException
        {
            final TextColumnBuilder<Date> dateColumn = DynamicReports.col.column(DBProperties
                            .getProperty("org.efaps.esjp.accounting.transaction.TransactionInvalid.Date"), "date",
                            DynamicReports.type.dateType());
            final TextColumnBuilder<String> transaction  = DynamicReports.col.column(DBProperties
                            .getProperty("org.efaps.esjp.accounting.transaction.TransactionInvalid.Transaction"),
                            "transaction", DynamicReports.type.stringType());
            final TextColumnBuilder<String> accountName  = DynamicReports.col.column(DBProperties
                            .getProperty("org.efaps.esjp.accounting.transaction.TransactionInvalid.AccountName"),
                            "accountName", DynamicReports.type.stringType());
            final TextColumnBuilder<String> accountDescription  = DynamicReports.col.column(DBProperties
                            .getProperty("org.efaps.esjp.accounting.transaction.TransactionInvalid.AccountDescription"),
                            "accountDescription", DynamicReports.type.stringType());
            accountDescription.setWidth(350);
            final TextColumnBuilder<BigDecimal> debit = DynamicReports.col.column(DBProperties
                            .getProperty("org.efaps.esjp.accounting.transaction.TransactionInvalid.Debit"), "debit",
                            DynamicReports.type.bigDecimalType());
            final TextColumnBuilder<BigDecimal> credit = DynamicReports.col.column(DBProperties
                            .getProperty("org.efaps.esjp.accounting.transaction.TransactionInvalid.Credit"), "credit",
                            DynamicReports.type.bigDecimalType());

            final ColumnGroupBuilder transactionGroup = DynamicReports.grp.group(transaction).groupByDataType();
            final ColumnGroupBuilder dateGroup = DynamicReports.grp.group(dateColumn).groupByDataType();

            final AggregationSubtotalBuilder<BigDecimal> debitSum = DynamicReports.sbt.sum(debit);
            final AggregationSubtotalBuilder<BigDecimal> creditSum = DynamicReports.sbt.sum(credit);

            _builder.addColumn(dateColumn, transaction, accountName, accountDescription, debit, credit);

            _builder.groupBy(dateGroup, transactionGroup);

            _builder.addSubtotalAtGroupFooter(transactionGroup, debitSum);
            _builder.addSubtotalAtGroupFooter(transactionGroup, creditSum);
        }
    }
}
