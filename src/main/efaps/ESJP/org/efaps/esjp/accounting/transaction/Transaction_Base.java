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
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.lang.StringEscapeUtils;
import org.efaps.admin.common.NumberGenerator;
import org.efaps.admin.datamodel.Classification;
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
import org.efaps.db.Context;
import org.efaps.db.Instance;
import org.efaps.db.InstanceQuery;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.PrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.db.Update;
import org.efaps.esjp.accounting.Periode;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.ci.CIContacts;
import org.efaps.esjp.ci.CIERP;
import org.efaps.esjp.ci.CISales;
import org.efaps.esjp.erp.CurrencyInst;
import org.efaps.esjp.erp.Rate;
import org.efaps.esjp.sales.Calculator_Base;
import org.efaps.esjp.sales.PriceUtil;
import org.efaps.esjp.sales.document.AbstractDocument_Base;
import org.efaps.ui.wicket.models.cell.UIFormCell;
import org.efaps.ui.wicket.util.DateUtil;
import org.efaps.util.EFapsException;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

/**
 * Base class for transaction in accounting.
 *
 * @author The eFaps Team
 * @version $Id$
 */
@EFapsUUID("803f24bc-7c4f-4168-97bc-a9cb01872f76")
@EFapsRevision("$Rev$")
public abstract class Transaction_Base
{

    /**
     * Key for the active Periode to store it in the session.
     */
    public static final String PERIODE_SESSIONKEY = "eFaps_Active_Accounting_Periode";

    /**
     * Key for the selected case to store it in the session.
     */
    public static final String CASE_SESSIONKEY = "eFaps_Selected_Accounting_Case";

    /**
     * Method is only used to store the calling Instance in the session ,so that
     * it can be accessed from the subtables in this form.
     *
     * @param _parameter paremter as passed to an esjp
     * @return new Return
     * @throws EFapsException on error
     */
    public Return setPeriodeUIFieldValue(final Parameter _parameter)
        throws EFapsException
    {
        Instance instance = _parameter.getCallInstance();
        if (instance.getType().isKindOf(CIAccounting.AccountAbstract.getType())
                        || instance.getType().isKindOf(CIAccounting.Transaction.getType())) {
            final PrintQuery print = new PrintQuery(instance);
            final SelectBuilder sel = new SelectBuilder().linkto(
                            instance.getType().isKindOf(CIAccounting.AccountAbstract.getType())
                                            ? CIAccounting.AccountAbstract.PeriodeAbstractLink
                                            : CIAccounting.Transaction.PeriodeLink).oid();
            print.addSelect(sel);
            print.execute();
            instance = Instance.get(print.<String>getSelect(sel));
        }
        if (instance.getType().isKindOf(CIAccounting.ReportNodeAbstract.getType())) {
            while (!instance.getType().isKindOf(CIAccounting.ReportNodeRoot.getType())) {
                final PrintQuery print = new PrintQuery(instance);
                final SelectBuilder sel = new SelectBuilder()
                    .linkto(CIAccounting.ReportNodeTree.ParentLinkAbstract).oid();
                print.addSelect(sel);
                print.execute();
                instance = Instance.get(print.<String>getSelect(sel));
            }
            final PrintQuery print = new PrintQuery(instance);
            final SelectBuilder sel = new SelectBuilder()
                .linkto(CIAccounting.ReportNodeRoot.ReportLink).linkto(CIAccounting.ReportAbstract.PeriodeLink).oid();
            print.addSelect(sel);
            print.execute();
            instance = Instance.get(print.<String>getSelect(sel));
        }
        final Map<?, ?> props = (Map<?, ?>) _parameter.get(ParameterValues.PROPERTIES);
        if (!props.containsKey("case")
                        || (props.containsKey("case") && !((String) props.get("case")).equalsIgnoreCase("true"))) {
            Context.getThreadContext().setSessionAttribute(Transaction_Base.CASE_SESSIONKEY, null);
        }
        Context.getThreadContext().setSessionAttribute(Transaction_Base.PERIODE_SESSIONKEY, instance);
        return new Return();
    }


    /**
     * @return a formater used to format bigdecimal for the user interface
     * @param _maxFrac maximum Faction, null to deactivate
     * @param _minFrac minimum Faction, null to activate
     * @throws EFapsException on error
     */
    protected DecimalFormat getFormater(final Integer _minFrac,
                                        final Integer _maxFrac)
        throws EFapsException
    {
        final DecimalFormat formater = (DecimalFormat) NumberFormat.getInstance(Context.getThreadContext().getLocale());
        if (_maxFrac != null) {
            formater.setMaximumFractionDigits(_maxFrac);
        }
        if (_minFrac != null) {
            formater.setMinimumFractionDigits(_minFrac);
        }
        formater.setRoundingMode(RoundingMode.HALF_UP);
        formater.setParseBigDecimal(true);
        return formater;
    }

    /**
     * Get the exchange Rate for a Date inside a periode.
     *
     * @param _periodeInst  Instance of the periode
     * @param _currId       id of the currency
     * @param _date         date the exchange rate is wanted for
     * @param _curr2Rate    map of currencyid to rates
     * @return rate
     * @throws EFapsException on error
     */
    protected Rate getExchangeRate(final Instance _periodeInst,
                                   final long _currId,
                                   final DateTime _date,
                                   final Map<Long, Rate> _curr2Rate)
        throws EFapsException
    {
        final Rate ret;
        if (_curr2Rate != null && _curr2Rate.containsKey(_currId)) {
            ret = _curr2Rate.get(_currId);
        } else {
            final CurrencyInst curInstance = new Periode().getCurrency(_periodeInst);
            if (curInstance.getInstance().getId() == _currId) {
                ret = new Rate(curInstance, BigDecimal.ONE);
            } else {
                final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.ERP_CurrencyRateAccounting);
                queryBldr.addWhereAttrEqValue(CIAccounting.ERP_CurrencyRateAccounting.CurrencyLink, _currId);
                queryBldr.addWhereAttrLessValue(CIAccounting.ERP_CurrencyRateAccounting.ValidFrom,
                                _date.plusMinutes(1));
                queryBldr.addWhereAttrGreaterValue(CIAccounting.ERP_CurrencyRateAccounting.ValidUntil,
                                _date.minusMinutes(1));
                final MultiPrintQuery print = queryBldr.getPrint();
                final SelectBuilder valSel = new SelectBuilder()
                                .attribute(CIAccounting.ERP_CurrencyRateAccounting.Rate).value();
                final SelectBuilder labSel = new SelectBuilder()
                                .attribute(CIAccounting.ERP_CurrencyRateAccounting.Rate).label();
                final SelectBuilder curSel = new SelectBuilder()
                                .linkto(CIAccounting.ERP_CurrencyRateAccounting.CurrencyLink).oid();
                print.addSelect(valSel, labSel, curSel);
                print.execute();
                if (print.next()) {
                    ret = new Rate(new CurrencyInst(Instance.get(print.<String>getSelect(curSel))),
                                   print.<BigDecimal>getSelect(valSel),
                                   print.<BigDecimal>getSelect(labSel));
                } else {
                    ret = new Rate(new CurrencyInst(Instance.get(CIERP.Currency.getType(), _currId)), BigDecimal.ONE);
                }
            }
            if (_curr2Rate != null) {
                _curr2Rate.put(_currId, ret);
            }
        }
        return ret;
    }

    /**
     * Make a string for the link of account2account.
     *
     * @param _accountOid oid of the eaccount
     * @param _postFix postfix
     * @return StringBuilder
     * @throws EFapsException on error
     */
    protected StringBuilder getLinkString(final String _accountOid,
                                          final String _postFix)
        throws EFapsException
    {
        final StringBuilder ret = new StringBuilder();
        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.Account2AccountAbstract);
        queryBldr.addWhereAttrEqValue("FromAccountLink", Instance.get(_accountOid).getId());
        final MultiPrintQuery print = queryBldr.getPrint();
        print.addAttribute("OID", "Numerator", "Denominator");
        print.addSelect("linkto[ToAccountLink].attribute[Name]");
        print.execute();

        while (print.next()) {
            final String to = print.<String>getSelect("linkto[ToAccountLink].attribute[Name]");
            final String oid = print.<String>getAttribute("OID");
            final Integer numerator = print.<Integer>getAttribute("Numerator");
            final Integer denominator = print.<Integer>getAttribute("Denominator");
            final BigDecimal percent = new BigDecimal(numerator).divide(new BigDecimal(denominator),
                            BigDecimal.ROUND_HALF_UP).multiply(new BigDecimal(100));
            final Instance instance = Instance.get(oid);
            if (instance.getType().getUUID().equals(CIAccounting.Account2AccountCosting.uuid)) {
                ret.append("<input type='checkbox' name='account2accountOID").append(_postFix)
                    .append("' checked='checked' value='").append(oid).append("'/>").append(percent).append("% ==> ")
                    .append(to).append("; ");
            } else if (instance.getType().getUUID().equals(CIAccounting.Account2AccountCostingInverse.uuid)) {
                ret.append("<input type='checkbox' name='account2accountOID").append(_postFix)
                    .append("' checked='checked' value='").append(oid).append("'/>-").append(percent).append("% ==> ")
                    .append(to).append("; ");
            } else {
                ret.append("<span>").append(DBProperties.getProperty(instance.getType().getName() + ".ShortName"))
                                .append(": ").append(percent).append("% ==> ").append(to).append("; </span>");
            }
        }
        return ret;
    }

    /**
     * Method to get the maximum for a value from the database.
     *
     * @param _firstDate DateTime for search of date initial.
     * @param _lastDate DateTime for search of date finally.
     * @param _periode Long for search filter to periode.
     * @return ret Return for maximum value.
     * @throws EFapsException on error
     */
    protected String getMaxNumber(final DateTime _firstDate,
                                  final DateTime _lastDate,
                                  final Long _periode)
        throws EFapsException
    {
        String ret = null;
        final QueryBuilder queryBuilder = new QueryBuilder(CIAccounting.Transaction);
        queryBuilder.addWhereAttrGreaterValue(CIAccounting.Transaction.Date, _firstDate.minusMinutes(1));
        queryBuilder.addWhereAttrLessValue(CIAccounting.Transaction.Date, _lastDate.plusMinutes(1));
        queryBuilder.addWhereAttrEqValue(CIAccounting.Transaction.PeriodeLink, _periode);
        final AttributeQuery attrQuery = queryBuilder.getAttributeQuery(CIAccounting.Transaction.ID);

        final QueryBuilder docQueryBuilder = new QueryBuilder(CIAccounting.TransactionClassExternal);
        docQueryBuilder.addWhereAttrInQuery(CIAccounting.TransactionClassExternal.TransactionLink, attrQuery);
        docQueryBuilder.addOrderByAttributeDesc(CIAccounting.TransactionClassExternal.Number);
        final InstanceQuery query = docQueryBuilder.getQuery();
        query.setLimit(1);
        final MultiPrintQuery multi = new MultiPrintQuery(query.execute());
        multi.addAttribute(CIAccounting.TransactionClassExternal.Number);
        multi.execute();
        if (multi.next()) {
            ret = multi.getAttribute(CIAccounting.TransactionClassExternal.Number);
        }
        return ret;
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
            final DecimalFormat formater = getFormater(null, null);
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
     * Method is used to close a range of transactions. (Meaning that they
     * cannot be altered afterwards.)
     *
     * @param _parameter Parameter as passed from the eFaps API
     * @return new Return
     * @throws EFapsException on error TODO select for update!!!!!!
     */
    public Return close(final Parameter _parameter)
        throws EFapsException
    {
        final String[] selectedOids = (String[]) _parameter.get(ParameterValues.OTHERS);

        final Set<Instance> transInstances = new HashSet<Instance>();
        final Map<Instance, BigDecimal> acc2Sum = new HashMap<Instance, BigDecimal>();
        final long openId = Status.find(CIAccounting.TransactionStatus.uuid, "Open").getId();
        for (final String oid : selectedOids) {
            final Instance instance = Instance.get(oid);
            final PrintQuery print = new PrintQuery(instance);
            print.addAttribute("Status");
            print.execute();

            if (print.<Long>getAttribute("Status").equals(openId)) {
                transInstances.add(instance);
                final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.TransactionPositionAbstract);
                queryBldr.addWhereAttrEqValue(CIAccounting.TransactionPositionAbstract.TransactionLink,
                                instance.getId());
                final MultiPrintQuery multi = queryBldr.getPrint();
                multi.addAttribute(CIAccounting.TransactionPositionAbstract.Amount);
                final SelectBuilder sel = new SelectBuilder()
                                .linkto(CIAccounting.TransactionPositionAbstract.AccountLink).oid();
                multi.addSelect(sel);
                multi.execute();

                while (multi.next()) {
                    final Instance accInst = Instance.get(multi.<String>getSelect(sel));
                    BigDecimal amount;
                    if (acc2Sum.containsKey(accInst)) {
                        amount = acc2Sum.get(accInst);
                    } else {
                        amount = BigDecimal.ZERO;
                    }
                    amount = amount.add(multi.
                                    <BigDecimal>getAttribute(CIAccounting.TransactionPositionAbstract.Amount));
                    acc2Sum.put(accInst, amount);
                }
            }
        }
        for (final Entry<Instance, BigDecimal> entry : acc2Sum.entrySet()) {
            final PrintQuery print = new PrintQuery(entry.getKey());
            print.addAttribute(CIAccounting.AccountAbstract.SumBooked);
            print.execute();

            BigDecimal booked = print.getAttribute(CIAccounting.AccountAbstract.SumBooked);
            if (booked == null) {
                booked = BigDecimal.ZERO;
            }

            final Update update = new Update(entry.getKey());
            update.add(CIAccounting.AccountAbstract.SumBooked, booked.add(entry.getValue()));
            update.execute();
        }

        for (final Instance instance : transInstances) {
            final Update update = new Update(instance);
            update.add("Status", Status.find(CIAccounting.TransactionStatus.uuid, "Booked").getId());
            update.add("Name", // Accounting_TransactionSequence
                            NumberGenerator.get(UUID.fromString("be5684bc-14b9-44e1-b6cd-7b1011af4b0b")).getNextVal());
            update.execute();
        }
        return new Return();
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
            rate = (BigDecimal) Calculator_Base.getFormatInstance().parse(rates[_index]);
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
            if (credit.subtract(debit).compareTo(BigDecimal.ZERO) == 0) {
                if (html.length() == 0) {
                    ret.put(ReturnValues.TRUE, true);
                } else {
                    ret.put(ReturnValues.SNIPLETT, html.toString());
                    ret.put(ReturnValues.TRUE, true);
                }
            } else {
                Instance inst = _parameter.getCallInstance();
                if (!inst.getType().getUUID().equals(CIAccounting.Periode.uuid)) {
                    inst = (Instance) Context.getThreadContext()
                                                .getSessionAttribute(Transaction_Base.PERIODE_SESSIONKEY);
                }
                final Instance currinst = new Periode().getCurrency(inst).getInstance();
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
     */
    protected boolean evalValues(final Parameter _parameter,
                                 final String _postFix)
    {
        boolean ret = true;
        final String[] amounts = _parameter.getParameterValues("amount_" + _postFix);
        final String[] rates = _parameter.getParameterValues("rate_" + _postFix);
        final String[] accountOids = _parameter.getParameterValues("accountLink_" + _postFix);
        if (amounts != null && accountOids != null) {
            for (int i = 0; i < amounts.length; i++) {
                final String amount = amounts[i];
                final String accountOid = accountOids[i];
                final BigDecimal rate = amounts[i].length() > 0 ? new BigDecimal(rates[i]) : BigDecimal.ZERO;
                if (!(amount.length() > 0 && accountOid.length() > 0 && rate.compareTo(BigDecimal.ZERO) != 0)) {
                    ret = false;
                    break;
                }
            }
        } else {
            ret = false;
        }
        return ret;
    }

    /**
     * Method to show rate of the document.
     *
     * @param _parameter Parameter as passed from the eFaps API
     * @return BigDecimal
     * @throws EFapsException on error
     */
    public Return setCurrencyRate(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final Instance docInst = Instance.get(_parameter.getParameterValue("selectedRow"));
        final DecimalFormat formater = getFormater(4, 4);
        final String rate = formater.format(getCurrencyRate(_parameter, docInst));
        ret.put(ReturnValues.VALUES, rate);
        return ret;
    }

    /**
     * Method to get the target currency rate.
     *
     * @param _parameter Parameter as passed from the eFaps API.
     * @param _docInst Instance from the document.
     * @return BigDecimal.
     * @throws EFapsException on error.
     */
    protected BigDecimal getCurrencyRate(final Parameter _parameter,
                                         final Instance _docInst)
        throws EFapsException
    {
        BigDecimal rate = BigDecimal.ZERO;
        if (_docInst.getType().isKindOf(CISales.DocumentSumAbstract.getType())) {
            final PrintQuery print = new PrintQuery(_docInst);
            print.addAttribute(CISales.DocumentSumAbstract.RateCurrencyId, CISales.DocumentSumAbstract.CurrencyId);
            print.execute();
            final Instance targetCurrInst = Instance.get(CIERP.Currency.getType(),
                            print.<Long>getAttribute(CISales.DocumentSumAbstract.RateCurrencyId));
            final Instance currentInst = Instance.get(CIERP.Currency.getType(),
                            print.<Long>getAttribute(CISales.DocumentSumAbstract.CurrencyId));
            final PriceUtil priceUtil = new PriceUtil();
            final BigDecimal[] rates = priceUtil.getRates(_parameter, targetCurrInst, currentInst);
            rate = rates[3];
        }
        return rate;
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
     * method obtains transaction of the documents for UUID and show the
     * instances.
     *
     * @param _parameter Parameter as passed from the eFaps API
     * @return list of instances
     * @throws EFapsException on error
     */
    public Return getTransaction4Doc(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final Map<?, ?> properties = (Map<?, ?>) _parameter.get(ParameterValues.PROPERTIES);
        final String typeUUID = (String) properties.get("typeUUID");
        final List<Instance> instances = new ArrayList<Instance>();

        if (typeUUID != null) {
            final String[] uuid2Type = typeUUID.split(";");
            for (int x = 0; x < uuid2Type.length; x++) {
                final UUID uuid = UUID.fromString(uuid2Type[x]);
                final QueryBuilder queryBuilder = new QueryBuilder(uuid);
                queryBuilder.addWhereAttrEqValue("DocumentLink", _parameter.getInstance().getId());
                final MultiPrintQuery multi = queryBuilder.getPrint();
                final SelectBuilder sel = new SelectBuilder().linkto("TransactionLink").oid();
                multi.addSelect(sel);
                multi.execute();
                while (multi.next()) {
                    instances.add(Instance.get(multi.<String>getSelect(sel)));
                }
            }
        }
        ret.put(ReturnValues.VALUES, instances);
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
        final String input = (String) _parameter.get(ParameterValues.OTHERS);
        final List<Map<String, String>> list = new ArrayList<Map<String, String>>();
        final QueryBuilder queryBldr = new QueryBuilder(CIContacts.Contact);
        queryBldr.addWhereAttrMatchValue(CIContacts.Contact.Name, input + "*").setIgnoreCase(true);
        final MultiPrintQuery multi = queryBldr.getPrint();
        multi.addAttribute(CIContacts.Contact.ID, CIContacts.Contact.Name);
        multi.execute();
        while (multi.next()) {
            final String name = multi.<String>getAttribute(CIContacts.Contact.Name);
            final Long id = multi.<Long>getAttribute(CIContacts.Contact.ID);
            final Map<String, String> map = new HashMap<String, String>();
            map.put("eFapsAutoCompleteKEY", id.toString());
            map.put("eFapsAutoCompleteVALUE", name);
            map.put("eFapsAutoCompleteCHOICE", name);
            list.add(map);
        }
        final Return retVal = new Return();
        retVal.put(ReturnValues.VALUES, list);
        return retVal;
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
        try {
            final Instance periodeInstance = (Instance) Context.getThreadContext().getSessionAttribute(
                            Transaction_Base.PERIODE_SESSIONKEY);

            final String curr = _parameter.getParameterValue("currencyExternal");
            final String amountStr = _parameter.getParameterValue("amountExternal");

            final Document doc = new Document();
            doc.setFormater(getFormater(2, 2));
            final Long currId;
            if (curr == null && amountStr == null) {
                final Map<?, ?> props = (Map<?, ?>) _parameter.get(ParameterValues.PROPERTIES);
                final String attrName = props.containsKey("amountAttribute") ? (String) props.get("amountAttribute")
                                : CISales.DocumentSumAbstract.RateNetTotal.name;
                final Instance docInst = Instance.get(_parameter.getParameterValue("document"));
                doc.setInstance(docInst);
                final PrintQuery print = new PrintQuery(docInst);
                print.addAttribute(CISales.DocumentSumAbstract.RateCurrencyId);
                print.addAttribute(attrName);
                print.execute();
                currId = print.<Long>getAttribute(CISales.DocumentSumAbstract.RateCurrencyId);
                doc.setAmount(print.<BigDecimal>getAttribute(attrName));
            } else {
                doc.setAmount((BigDecimal) doc.getFormater().parse(amountStr.isEmpty() ? "0" : amountStr));
                currId = Long.parseLong(curr);
            }

            final String dateStr = _parameter.getParameterValue("date_eFapsDate");
            doc.setDate(DateUtil.getDateFromParameter(dateStr));

            final Rate rate = getExchangeRate(periodeInstance, currId, doc.getDate(), null);
            doc.setRate(rate);

            buildDoc4ExecuteButton(_parameter, doc, rate);

            if (doc.getInstance() != null) {
                doc.setInvert(doc.getInstance().getType().isKindOf(CISales.ReturnSlip.getType()));
                addAccount4BankCash(_parameter, doc);
            }

            final StringBuilder js = buildHtml4ExecuteButton(doc);
            ret.put(ReturnValues.SNIPLETT, js.toString());
        } catch (final ParseException e) {
            throw new EFapsException(Transaction_Base.class, "executeButton.ParseException", e);
        }
        return ret;
    }

    protected void buildDoc4ExecuteButton(final Parameter _parameter,
                                             final Document _doc,
                                             final Rate _rate)
        throws EFapsException
    {
        final String caseOid = _parameter.getParameterValue("case");
        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.Account2CaseAbstract);
        queryBldr.addWhereAttrEqValue(CIAccounting.Account2CaseAbstract.ToCaseAbstractLink,
                        Instance.get(caseOid).getId());
        queryBldr.addWhereAttrEqValue(CIAccounting.Account2CaseAbstract.Default, true);
        final MultiPrintQuery print = queryBldr.getPrint();

        final SelectBuilder oidSel = new SelectBuilder()
                        .linkto(CIAccounting.Account2CaseAbstract.FromAccountAbstractLink).oid();
        final SelectBuilder nameSel = new SelectBuilder()
                        .linkto(CIAccounting.Account2CaseAbstract.FromAccountAbstractLink)
                        .attribute(CIAccounting.AccountAbstract.Name);
        final SelectBuilder descSel = new SelectBuilder()
                        .linkto(CIAccounting.Account2CaseAbstract.FromAccountAbstractLink)
                        .attribute(CIAccounting.AccountAbstract.Description);
        print.addAttribute(CIAccounting.Account2CaseAbstract.Numerator,
                        CIAccounting.Account2CaseAbstract.Denominator,
                        CIAccounting.Account2CaseAbstract.LinkValue);
        print.addSelect(oidSel, nameSel, descSel);
        print.execute();
        while (print.next()) {
            final String oid = print.<String>getSelect(oidSel);
            final String name = print.<String>getSelect(nameSel);
            final String desc = print.<String>getSelect(descSel);
            final Integer denom = print.<Integer>getAttribute(CIAccounting.Account2CaseAbstract.Denominator);
            final Integer numer = print.<Integer>getAttribute(CIAccounting.Account2CaseAbstract.Numerator);
            final Long linkId = print.<Long>getAttribute(CIAccounting.Account2CaseAbstract.LinkValue);
            final BigDecimal mul = new BigDecimal(numer).setScale(12).divide(new BigDecimal(denom),
                            BigDecimal.ROUND_HALF_UP);
            final BigDecimal accAmount;
            final Type type = print.getCurrentInstance().getType();
            if (type.equals(CIAccounting.Account2CaseCredit4Classification.getType())
                            || type.equals(CIAccounting.Account2CaseDebit4Classification.getType())) {
                accAmount = mul.multiply(_doc.getAmount4Class(linkId)).setScale(2, BigDecimal.ROUND_HALF_UP);
            } else {
                accAmount = mul.multiply(_doc.getAmount()).setScale(2, BigDecimal.ROUND_HALF_UP);
            }

            final BigDecimal accAmountRate = accAmount.setScale(12, BigDecimal.ROUND_HALF_UP)
                                                            .divide(_rate.getValue(), BigDecimal.ROUND_HALF_UP);
            String postFix;
            Map<String, TargetAccount> acounts;
            if (type.getUUID().equals(CIAccounting.Account2CaseCredit.uuid)
                            || type.equals(CIAccounting.Account2CaseCredit4Classification.getType())) {
                postFix = "_Credit";
                acounts = _doc.getCreditAccounts();
            } else {
                postFix = "_Debit";
                acounts = _doc.getDebitAccounts();
            }
            final TargetAccount account = new TargetAccount(oid, name, desc, accAmount);
            account.setAmountRate(accAmountRate);
            account.setLink(getLinkString(oid, postFix));
            account.setRate(_rate);
            acounts.put(oid, account);
        }
    }

    protected StringBuilder buildHtml4ExecuteButton(final Document _doc)
        throws EFapsException
    {
        final StringBuilder js = new StringBuilder();
        js.append("function removeRows(elName){")
                        .append("var e = document.getElementsByName(elName);")
                        .append("var zz = e.length;")
                        .append("for (var i=0; i <zz;i++) {")
                        .append("var x = e[0].parentNode.parentNode;")
                        .append("var p = x.parentNode;p.removeChild(x);")
                        .append("}}\n")
                        .append("removeRows('amount_Debit');")
                        .append("removeRows('amount_Credit');\n");

        js.append("function setDebit(){");
        int index = 0;
        for (final TargetAccount account : _doc.getDebitAccounts().values()) {
            js.append(getScriptLine(account, "_Debit", index));
            index++;
        }
        js.append("}\n");

        js.append("function setCredit(){");
        index = 0;
        for (final TargetAccount account : _doc.getCreditAccounts().values()) {
            js.append(getScriptLine(account, "_Credit", index));
            index++;
        }
        js.append("}\n").append(getScriptValues(_doc));

        return js;
    }

    /**
     * method for add account bank cash in case existing document instance.
     *
     * @param _parameter Parameter as passed from the eFaps API.
     * @param _doc Document.
     * @throws EFapsException on error.
     */
    protected void addAccount4BankCash(final Parameter _parameter,
                                       final Document _doc)
        throws EFapsException
    {
        final Map<?, ?> properties = (Map<?, ?>) _parameter.get(ParameterValues.PROPERTIES);
        final String addAccount = (String) properties.get("addAccount");
        if (addAccount != null && addAccount.length() > 0) {
            final UIFormCell uiform = (UIFormCell) _parameter.get(ParameterValues.CLASS);
            final Instance instance = Instance.get(uiform.getParent().getInstanceKey());
            final PrintQuery print = new PrintQuery(instance);
            print.addAttribute(CIAccounting.AccountAbstract.Name, CIAccounting.AccountAbstract.Description);
            print.execute();
            BigDecimal amount = _doc.getAmount();

            Map<String, TargetAccount> accounts;
            if ("Credit".equals(addAccount)) {
                accounts = _doc.getCreditAccounts();
                amount = amount.subtract(_doc.getCreditSum());
            } else {
                accounts = _doc.getDebitAccounts();
                amount = amount.subtract(_doc.getDebitSum());
            }
            final TargetAccount account = new TargetAccount(instance.getOid(),
                            print.<String>getAttribute(CIAccounting.AccountAbstract.Name),
                            print.<String>getAttribute(CIAccounting.AccountAbstract.Description), amount);
            account.setRate(_doc.getRate());
            accounts.put(instance.getOid(), account);
        }
    }

    /**
     * @param _doc  Document
     * @return StringBuilder
     */
    protected StringBuilder getScriptValues(final Document _doc)
    {
        final StringBuilder ret = new StringBuilder();
        ret.append(" addNewRows_transactionPositionDebitTable(").append(_doc.getDebitAccounts().size())
            .append(", setDebit, null);")
            .append(" addNewRows_transactionPositionCreditTable(").append(_doc.getCreditAccounts().size())
            .append(", setCredit, null);")
            .append("eFapsSetFieldValue(document.getElementsByName('sumDebit')[0].id,'sumDebit','")
            .append(_doc.getDebitSumFormated()).append("');")
            .append("eFapsSetFieldValue(document.getElementsByName('sumCredit')[0].id,'sumCredit','")
            .append(_doc.getCreditSumFormated()).append("');")
            .append("eFapsSetFieldValue(document.getElementsByName('sumTotal')[0].id,'sumTotal','")
            .append(_doc.getDifferenceFormated()).append("');");
        return ret;
    }

    /**
     * @param _account  TargetAccount
     * @param _suffix   suffix
     * @param _index    index
     * @return StringBuilder
     * @throws EFapsException on error
     */
    protected StringBuilder getScriptLine(final TargetAccount _account,
                                          final String _suffix,
                                          final Integer _index)
        throws EFapsException
    {
        final StringBuilder ret = new StringBuilder();
        ret.append("document.getElementsByName('amount").append(_suffix).append("')[").append(_index)
            .append("].value = '").append(StringEscapeUtils.escapeJavaScript(_account.getAmountFormated())).append("';")
            .append("document.getElementsByName('rateCurrencyLink").append(_suffix).append("')[").append(_index)
            .append("].value = '").append(_account.getRate().getCurInstance().getInstance().getId()).append("';")
            .append("document.getElementsByName('rate").append(_suffix).append("')[").append(_index)
            .append("].value = '").append(_account.getRate().getLabel()).append("';")
            .append("document.getElementsByName('rate").append(_suffix).append("").append(RateUI.INVERTEDSUFFIX)
            .append("')[").append(_index).append("].value ='")
            .append(_account.getRate().getCurInstance().isInvert()).append("';")
            .append("document.getElementsByName('amountRate").append(_suffix)
            .append("')[").append(_index).append("].appendChild(document.createTextNode('")
            .append(StringEscapeUtils.escapeJavaScript(_account.getAmountRateFormated())).append("'));")
            .append("document.getElementsByName('accountLink").append(_suffix).append("')[").append(_index)
            .append("].value = '").append(_account.getOid()).append("';")
            .append("document.getElementsByName('accountLink").append(_suffix).append("AutoComplete')[").append(_index)
            .append("].value = '").append(_account.getName()).append("';")
            .append("document.getElementsByName('description").append(_suffix).append("')[").append(_index)
            .append("].appendChild(document.createTextNode('")
            .append(StringEscapeUtils.escapeJavaScript(_account.getDescription())).append("'));");

        if (_account.getLink() != null &&  _account.getLink().length() > 0) {
            ret.append("document.getElementsByName('account2account")
                            .append(_suffix).append("')[").append(_index).append("].innerHTML='")
                            .append(_account.getLink().toString().replaceAll("'", "\\\\\\'")).append("';");
        }

        return ret;
    }
    /**
     * Method to show the tree transaction in periode.
     *
     * @param _parameter Parameter as passed from eFaps API
     * @return ret Return
     * @throws EFapsException on error
     */
    public Return accessCheck(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final PrintQuery print = new PrintQuery(_parameter.getInstance());
        print.addAttribute("Summary");
        print.execute();
        final Boolean summary = print.<Boolean>getAttribute("Summary");
        if (summary != null && !summary) {
            ret.put(ReturnValues.TRUE, true);
        }
        return ret;
    }

    /**
     * Used to hold necessary informations about a document.
     */
    public class Document
    {
        /**
         * Instance of this Document.
         */
        private Instance instance;

        /**
         * Is this a Stock moving Document.
         */
        private boolean stockDoc;

        /**
         * Did the Document pass the validation of the cost.
         * (Every product has its cost assigned)
         */
        private boolean costValidated;


        /**
         * Is this a document containing sums.
         */
        private boolean sumsDoc;

        /**
         * OID of the rate currency.
         */
        private String rateCurrOID;

        /**
         * Date of this Document.
         */
        private DateTime date;

        /**
         * Symbol of the Curency.
         */
        private String currSymbol;

        /**
         * List of TargetAccounts for debit.
         */
        private final Map<String, TargetAccount> debitAccounts = new LinkedHashMap<String, TargetAccount>();

        /**
         * List of TargetAccounts for credit.
         */
        private final Map<String, TargetAccount> creditAccounts = new LinkedHashMap<String, TargetAccount>();

        /**
         * Amount of this Account.
         */
        private BigDecimal amount;

        /**
         * Formater.
         */
        private DecimalFormat formater;

        /**
         * Account for the Debtor.
         */
        private TargetAccount debtorAccount;

        /**
         * Rate for the document.
         */
        private Rate rate;

        /**
         * Invert this document. (Means change the map for debit and credit).
         */
        private boolean invert;

        /**
         * Mapping of classification id 2 amount.
         */
        private HashMap<Long, BigDecimal> clazz2Amount;

        /**
         * Constructor.
         */
        public Document()
        {
        }

        /**
         * @param _instance Instance of the Document
         */
        public Document(final Instance _instance)
        {
            setInstance(_instance);
        }

        /**
         * @param _linkId   id of the Classification the amount is wanted for
         * @return Amount
         * @throws EFapsException on error
         */
        public BigDecimal getAmount4Class(final Long _linkId)
            throws EFapsException
        {
            BigDecimal ret;
            if (isSumsDoc()) {
                if (this.clazz2Amount == null) {
                    this.clazz2Amount = new HashMap<Long, BigDecimal>();
                    final QueryBuilder queryBldr = new QueryBuilder(CISales.PositionAbstract);
                    queryBldr.addWhereAttrEqValue(CISales.PositionAbstract.DocumentAbstractLink, this.instance.getId());
                    final MultiPrintQuery multi = queryBldr.getPrint();
                    final SelectBuilder sel = new SelectBuilder()
                        .linkto(CISales.PositionAbstract.Product).clazz().type();
                    multi.addSelect(sel);
                    multi.addAttribute(CISales.PositionSumAbstract.NetPrice);
                    multi.execute();
                    while (multi.next()) {
                        final BigDecimal posamount = multi.<BigDecimal>getAttribute(CISales.PositionSumAbstract.NetPrice);
                        final List<Classification> clazzes = multi.getSelect(sel);
                        if (clazzes != null) {
                            for (final Classification clazz : clazzes) {
                                Classification classTmp = clazz;
                                while (classTmp != null) {
                                    BigDecimal currAmount;
                                    if (this.clazz2Amount.containsKey(classTmp.getId())) {
                                        currAmount = this.clazz2Amount.get(classTmp.getId());
                                    } else {
                                        currAmount = BigDecimal.ZERO;
                                    }
                                    this.clazz2Amount.put(classTmp.getId(), currAmount.add(posamount));
                                    classTmp = (Classification) classTmp.getParentClassification();
                                }
                            }
                        }
                    }
                }
                ret = this.clazz2Amount.containsKey(_linkId) ?  this.clazz2Amount.get(_linkId) : BigDecimal.ZERO;
            } else {
                ret = BigDecimal.ZERO;
            }
            return ret;
        }

        /**
         * Getter method for the instance variable {@link #invert}.
         *
         * @return value of instance variable {@link #invert}
         */
        public boolean isInvert()
        {
            return this.invert;
        }


        /**
         * Setter method for instance variable {@link #invert}.
         *
         * @param _invert value for instance variable {@link #invert}
         */

        public void setInvert(final boolean _invert)
        {
            this.invert = _invert;
        }


        /**
         * Getter method for the instance variable {@link #costValidated}.
         *
         * @return value of instance variable {@link #costValidated}
         */
        public boolean isCostValidated()
        {
            return this.costValidated;
        }


        /**
         * Setter method for instance variable {@link #costValidated}.
         *
         * @param _costValidated value for instance variable {@link #costValidated}
         */

        public void setCostValidated(final boolean _costValidated)
        {
            this.costValidated = _costValidated;
        }


        /**
         * Getter method for the instance variable {@link #rate}.
         *
         * @return value of instance variable {@link #rate}
         */
        public Rate getRate()
        {
            return this.rate;
        }

        /**
         * Setter method for instance variable {@link #rate}.
         *
         * @param _rate value for instance variable {@link #rate}
         */

        public void setRate(final Rate _rate)
        {
            this.rate = _rate;
        }


        /**
         * Getter method for the instance variable {@link #formater}.
         *
         * @return value of instance variable {@link #formater}
         */
        public DecimalFormat getFormater()
        {
            return this.formater;
        }

        /**
         * Setter method for instance variable {@link #formater}.
         *
         * @param _formater value for instance variable {@link #formater}
         */

        public void setFormater(final DecimalFormat _formater)
        {
            this.formater = _formater;
        }

        /**
         * Getter method for the instance variable {@link #amount}.
         *
         * @return value of instance variable {@link #amount}
         */
        public BigDecimal getAmount()
        {
            return this.amount;
        }


        /**
         * Setter method for instance variable {@link #amount}.
         *
         * @param _amount value for instance variable {@link #amount}
         */

        public void setAmount(final BigDecimal _amount)
        {
            this.amount = _amount;
        }

        /**
         * Getter method for the instance variable {@link #debitAccounts}.
         *
         * @return value of instance variable {@link #debitAccounts}
         */
        public Map<String, TargetAccount> getDebitAccounts()
        {
            return this.invert ?  this.creditAccounts : this.debitAccounts;
        }

        /**
         * Getter method for the instance variable {@link #creditAccounts}.
         *
         * @return value of instance variable {@link #creditAccounts}
         */
        public Map<String, TargetAccount> getCreditAccounts()
        {
            return this.invert ? this.debitAccounts : this.creditAccounts;
        }

        /**
         * Setter method for instance variable {@link #debtorAccount}.
         *
         * @param _debtorAccount value for instance variable {@link #debtorAccount}
         */

        public void setDebtorAccount(final TargetAccount _debtorAccount)
        {
            this.debtorAccount = _debtorAccount;
        }

        /**
         * Getter method for the instance variable {@link #debtorAccount}.
         *
         * @return value of instance variable {@link #debtorAccount}
         */
        public TargetAccount getDebtorAccount()
        {
            return this.debtorAccount;
        }

        /**
         * @param _currSymbol   Symbol for the Currency
         */
        public void setCurrSymbol(final String _currSymbol)
        {
            this.currSymbol = _currSymbol;
        }

        /**
         * Getter method for the instance variable {@link #currSymbol}.
         *
         * @return value of instance variable {@link #currSymbol}
         */
        public String getCurrSymbol()
        {
            return this.currSymbol;
        }

        /**
         * Getter method for the instance variable {@link #date}.
         *
         * @return value of instance variable {@link #date}
         */
        public DateTime getDate()
        {
            return this.date;
        }

        /**
         * @param _date date for this document
         */
        public void setDate(final DateTime _date)
        {
            this.date = _date;
        }

        /**
         * @return Date as String
         * @throws EFapsException on error
         */
        public String getDateString()
            throws EFapsException
        {
            final DateTimeFormatter formatter = DateTimeFormat.mediumDate();
            return this.date.withChronology(Context.getThreadContext().getChronology()).toString(
                            formatter.withLocale(Context.getThreadContext().getLocale()));
        }

        /**
         * @param _oid  OID
         */
        public void setRateCurrOID(final String _oid)
        {
            this.rateCurrOID = _oid;
        }

        /**
         * @return Instance of the Rate Currency
         */
        public Instance getRateCurrInst()
        {
            return Instance.get(this.rateCurrOID);
        }

        /**
         * Getter method for the instance variable {@link #stockDoc}.
         *
         * @return value of instance variable {@link #stockDoc}
         */
        public boolean isStockDoc()
        {
            return this.stockDoc;
        }

        /**
         * Getter method for the instance variable {@link #sumsDoc}.
         *
         * @return value of instance variable {@link #sumsDoc}
         */
        public boolean isSumsDoc()
        {
            return this.sumsDoc;
        }

        /**
         * Getter method for the instance variable {@link #instance}.
         *
         * @return value of instance variable {@link #instance}
         */
        public Instance getInstance()
        {
            return this.instance;
        }

        /**
         * Setter method for instance variable {@link #instance}.
         *
         * @param _instance value for instance variable {@link #instance}
         */

        public void setInstance(final Instance _instance)
        {
            this.instance = _instance;
            this.sumsDoc = _instance.getType().isKindOf(CISales.DocumentSumAbstract.getType());
            this.stockDoc = _instance.getType().isKindOf(CISales.DocumentStockAbstract.getType());
        }

        /**
         * @return the sum of credit accounts
         */
        public BigDecimal getCreditSum()
        {
            BigDecimal ret = BigDecimal.ZERO;
            for (final TargetAccount account : getCreditAccounts().values()) {
                ret = ret.add(account.getAmountRate().setScale(2, BigDecimal.ROUND_HALF_UP));
            }
            return ret;
        }

        /**
         * @return the sum of credit accounts formated
         */
        public String getCreditSumFormated()
        {
            return getFormater().format(getCreditSum());
        }

        /**
         * @return the sum of all debit accounts
         */
        public BigDecimal getDebitSum()
        {
            BigDecimal ret = BigDecimal.ZERO;
            for (final TargetAccount account : getDebitAccounts().values()) {
                ret = ret.add(account.getAmountRate().setScale(2, BigDecimal.ROUND_HALF_UP));
            }
            return ret;
        }

        /**
         * @return the sum of all debit accounts formated
         */
        public String getDebitSumFormated()
        {
            return getFormater().format(getDebitSum());
        }

        /**
         * @return the difference between the sum of debit accounts
         *          and the sum of credit accounts
         */
        public BigDecimal getDifference()
        {
            return getDebitSum().subtract(getCreditSum()).abs();
        }

        /**
         * @return the difference between the sum of debit accounts
         *          and the sum of credit accounts formated
         */
        public String getDifferenceFormated()
        {
            return  getFormater().format(getDifference());
        }
    }


    /**
     * Used to store information about an account temporarily.
     */
    public class TargetAccount
    {

        /**
         * OID of this account.
         */
        private final String oid;

        /**
         * Name of this account.
         */
        private final String name;

        /**
         * Description for this account.
         */
        private final String description;

        /**
         * Amount of this account.
         */
        private BigDecimal amount;

        /**
         * Rate.
         */
        private Rate rate;

        /**
         * Amount of this account.
         */
        private BigDecimal amountRate;

        /**
         * Link string.
         */
        private StringBuilder link;

        /**
         * new TargetAccount.
         *
         * @param _oid oid.
         * @param _name name.
         * @param _description description.
         * @param _amount amount.
         */
        public TargetAccount(final String _oid,
                             final String _name,
                             final String _description,
                             final BigDecimal _amount)
        {
            this.oid = _oid;
            this.name = _name;
            this.description = _description;
            this.amount = _amount;
        }

        /**
         * add amount for constructor TargetAccount.
         *
         * @param _amount amount.
         * @return this.
         */
        public TargetAccount add(final BigDecimal _amount)
        {
            this.amount = this.amount.add(_amount);
            return this;
        }

        /**
         * Getter method for instance variable {@link #oid}.
         *
         * @return value of instance variable {@link #oid}
         */
        public String getOid()
        {
            return this.oid;
        }

        /**
         * Getter method for instance variable {@link #name}.
         *
         * @return value of instance variable {@link #name}
         */
        public String getName()
        {
            return this.name;
        }

        /**
         * Getter method for instance variable {@link #description}.
         *
         * @return value of instance variable {@link #description}
         */
        public String getDescription()
        {
            return this.description;
        }

        /**
         * Getter method for instance variable {@link #amount}.
         *
         * @return value of instance variable {@link #amount}
         */
        public BigDecimal getAmount()
        {
            return this.amount;
        }

        /**
         * @return the amount formated
         * @throws EFapsException on error
         */
        public String getAmountFormated()
            throws EFapsException
        {
            return getFormater(2, 2).format(getAmount());
        }

        /**
         * Setter method for instance variable {@link #amount}.
         *
         * @param _amount value for instance variable {@link #amount}
         */

        public void setAmount(final BigDecimal _amount)
        {
            this.amount = _amount;
        }

        /**
         * Getter method for the instance variable {@link #rate}.
         *
         * @return value of instance variable {@link #rate}
         */
        public Rate getRate()
        {
            return this.rate;
        }

        /**
         * Setter method for instance variable {@link #rate}.
         *
         * @param _rate value for instance variable {@link #rate}
         */

        public void setRate(final Rate _rate)
        {
            this.rate = _rate;
        }

        /**
         * Getter method for the instance variable {@link #amountRate}.
         *
         * @return value of instance variable {@link #amountRate}
         */
        public BigDecimal getAmountRate()
        {
            if (this.amountRate == null && this.amount != null && this.rate != null) {
                this.amountRate = this.amount.setScale(12, BigDecimal.ROUND_HALF_UP)
                    .divide(this.rate.getValue(), BigDecimal.ROUND_HALF_UP);
            }
            return this.amountRate;
        }

        /**
         * Setter method for instance variable {@link #amountRate}.
         *
         * @param _amountRate value for instance variable {@link #amountRate}
         */

        public void setAmountRate(final BigDecimal _amountRate)
        {
            this.amountRate = _amountRate;
        }

        /**
         * @return amunt rate formated
         * @throws EFapsException on error
         */
        public String getAmountRateFormated()
            throws EFapsException
        {
            return getFormater(2, 2).format(getAmountRate());
        }

        /**
         * Getter method for the instance variable {@link #link}.
         *
         * @return value of instance variable {@link #link}
         */
        public StringBuilder getLink()
        {
            return this.link;
        }

        /**
         * Setter method for instance variable {@link #link}.
         *
         * @param _link value for instance variable {@link #link}
         */

        public void setLink(final StringBuilder _link)
        {
            this.link = _link;
        }
    }
}
