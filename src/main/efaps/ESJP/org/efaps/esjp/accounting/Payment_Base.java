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

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Map.Entry;

import org.efaps.admin.datamodel.Status;
import org.efaps.admin.datamodel.ui.FieldValue;
import org.efaps.admin.datamodel.ui.UIInterface;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Return;
import org.efaps.admin.event.Parameter.ParameterValues;
import org.efaps.admin.event.Return.ReturnValues;
import org.efaps.admin.program.esjp.EFapsRevision;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.AttributeQuery;
import org.efaps.db.Context;
import org.efaps.db.Insert;
import org.efaps.db.Instance;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.PrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.db.Update;
import org.efaps.esjp.accounting.transaction.Transaction_Base;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.ci.CISales;
import org.efaps.util.DateTimeUtil;
import org.efaps.util.EFapsException;
import org.joda.time.DateMidnight;


/**
 * TODO comment!
 *
 * @author The eFaps Team
 * @version $Id$
 */
@EFapsUUID("ca10ea64-78fc-4cfc-91a6-1d346dddd886")
@EFapsRevision("$Rev$")
public abstract class Payment_Base
{

    /**
     * method for generate a new Payment.
     *
     * @param _parameter Parameter as passed from the eFaps API.
     * @return new Return.
     * @throws EFapsException on error.
     */
    public Return createPaymentCheck(final Parameter _parameter)
        throws EFapsException
    {
        final String namePrefix = _parameter.getParameterValue("namePrefix");
        final String fromNumber = _parameter.getParameterValue("fromNumber");
        final String toNumber = _parameter.getParameterValue("toNumber");
        final String nameSuffix = _parameter.getParameterValue("nameSuffix");
        final Map<?, ?> props = (Map<?, ?>) _parameter.get(ParameterValues.PROPERTIES);

        final String digitCountStr = (String) props.get("digitCount");
        final int digitCount = digitCountStr == null ? 6 : Integer.parseInt(digitCountStr);
        final long from = Long.parseLong(fromNumber);

        final long to;
        if (toNumber != null && !toNumber.isEmpty()) {
            to = Long.parseLong(toNumber);
        } else {
            to = from;
        }

        for (long num = from; num < to + 1; num++) {
            final DecimalFormat formater = new DecimalFormat();
            formater.setMaximumIntegerDigits(digitCount);
            formater.setMinimumIntegerDigits(digitCount);
            formater.setGroupingUsed(false);
            final StringBuilder nameBldr = new StringBuilder();
            nameBldr.append(namePrefix == null ? "" : namePrefix).append(formater.format(num))
                .append(nameSuffix == null ? "" : nameSuffix);
            final Insert insert = new Insert(CIAccounting.PaymentCheck);
            insert.add(CIAccounting.PaymentCheck.Name, nameBldr.toString());
            insert.add(CIAccounting.PaymentCheck.Status,
                            Status.find(CIAccounting.PaymentCheckStatus.uuid, "Open").getId());
            insert.execute();

            final Instance periodeInst = Instance.get(_parameter.getParameterValue("periode"));
            if (periodeInst.isValid()) {
                final Instance accInst = Instance.get(_parameter.getParameterValue("account"));
                if (accInst.isValid() && _parameter.getParameterValue("accountAutoComplete") != null
                                && !_parameter.getParameterValue("accountAutoComplete").isEmpty()) {
                    final Insert relInsert = new Insert(CIAccounting.Account2PaymentCheck);
                    relInsert.add(CIAccounting.Account2PaymentCheck.FromLink, accInst.getId());
                    relInsert.add(CIAccounting.Account2PaymentCheck.ToLink, insert.getId());
                    relInsert.add(CIAccounting.Account2PaymentCheck.Active, true);
                    relInsert.execute();
                }
            }
        }

        return new Return();
    }

    /**
     * Called from a field update event to get the html snipplet for a payment
     * field.
     * @param _parameter    Parameter as passed from the eFaps API
     * @return  new Return
     * @throws EFapsException on error
     */
    public Return getPaymentFieldValue(final Parameter _parameter)
        throws EFapsException
    {
        final FieldValue fieldValue = (FieldValue) _parameter.get(ParameterValues.UIOBJECT);
        final Map<?, ?> props = (Map<?, ?>) _parameter.get(ParameterValues.PROPERTIES);
        final Instance periodeInstance = (Instance) Context.getThreadContext().getSessionAttribute(
                        Transaction_Base.PERIODE_SESSIONKEY);

        final QueryBuilder accQueryBldr = new QueryBuilder(CIAccounting.AccountAbstract);
        accQueryBldr.addWhereAttrEqValue(CIAccounting.AccountAbstract.PeriodeAbstractLink, periodeInstance.getId());
        final AttributeQuery accQuery = accQueryBldr.getAttributeQuery(CIAccounting.AccountAbstract.ID);

        final QueryBuilder payQueryBldr = new QueryBuilder(CIAccounting.PaymentAbstract);
        payQueryBldr.addWhereAttrEqValue(CIAccounting.PaymentAbstract.StatusAbstract,
                        Status.find(CIAccounting.PaymentCheckStatus.uuid, "Open").getId());
        final AttributeQuery payQuery = payQueryBldr.getAttributeQuery(CIAccounting.PaymentAbstract.ID);

        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.Account2PaymentAbstract);
        queryBldr.addWhereAttrInQuery(CIAccounting.Account2PaymentAbstract.FromAbstractLink, accQuery);
        queryBldr.addWhereAttrInQuery(CIAccounting.Account2PaymentAbstract.ToAbstractLink, payQuery);

        final SelectBuilder accNameSel = new SelectBuilder()
            .linkto(CIAccounting.Account2PaymentAbstract.FromAbstractLink)
            .attribute(CIAccounting.AccountAbstract.Name);
        final SelectBuilder accDescSel = new SelectBuilder()
            .linkto(CIAccounting.Account2PaymentAbstract.FromAbstractLink)
            .attribute(CIAccounting.AccountAbstract.Description);
        final SelectBuilder payNameSel = new SelectBuilder()
            .linkto(CIAccounting.Account2PaymentAbstract.ToAbstractLink)
            .attribute(CIAccounting.PaymentAbstract.Name);
        final SelectBuilder payOidSel = new SelectBuilder()
            .linkto(CIAccounting.Account2PaymentAbstract.ToAbstractLink).oid();

        final MultiPrintQuery multi = queryBldr.getPrint();
        multi.addSelect(accNameSel, accDescSel, payNameSel, payOidSel);
        multi.execute();

        final Map<String, List<String[]>> acc2Pay = new TreeMap<String, List<String[]>>();
        while (multi.next()) {
            final String accName = multi.<String> getSelect(accNameSel);
            final String accDesc = multi.<String> getSelect(accDescSel);
            final String payName = multi.<String> getSelect(payNameSel);
            final String payOid = multi.<String> getSelect(payOidSel);
            final String acc =  accName + " - " + accDesc;
            List<String[]> pays;
            if (acc2Pay.containsKey(acc)) {
                pays = acc2Pay.get(acc);
            } else {
                pays = new ArrayList<String[]>();
                acc2Pay.put(acc, pays);
            }
            pays.add(new String[] { payName, payOid });
        }
        final String columnCountStr = (String) props.get("columnCount");
        final int columnCount = columnCountStr != null && !columnCountStr.isEmpty()
                                ? Integer.parseInt(columnCountStr) : 5;
        final StringBuilder html = new StringBuilder();
        html.append("<table>");
        for (final Entry<String, List<String[]>> entry : acc2Pay.entrySet()) {
            final int c = entry.getValue().size() / columnCount + (entry.getValue().size() % columnCount > 0 ? 1 : 0);
            html.append("<tr><td rowspan=\"").append(c).append("\">").append(entry.getKey())
                .append("</td>");
            int i = 0;
            for (final String[] pay : entry.getValue()) {
                if (i < columnCount) {
                    html.append("<td>");
                } else {
                    html.append("</tr><tr><td>");
                    i = 0;
                }
                html.append("<input type=\"checkbox\" name=\"").append(fieldValue.getField().getName())
                    .append("\" value=\"").append(pay[1]).append("\" />")
                    .append(pay[0]).append("</td>");
                i++;
            }
            for (int j = i; j < columnCount; j++) {
                html.append("<td></td>");
            }
            i = 0;
        }
        html.append("</table>");
        final Return ret = new Return();
        ret.put(ReturnValues.SNIPLETT, html.toString());
        return ret;
    }

    /**
     * Called from a field value event to get the html snipplet for a dropdown
     * field.
     * @param _parameter    Parameter as passed from the eFaps API
     * @return  new Return
     * @throws EFapsException on error
     */
    public Return periodeUIFieldValue(final Parameter _parameter)
        throws EFapsException
    {
        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.Periode);
        final MultiPrintQuery print = queryBldr.getPrint();
        print.addAttribute(CIAccounting.Periode.Name);
        print.execute();
        final Map<String, String> values = new TreeMap<String, String>();
        while (print.next()) {
            values.put(print.<String> getAttribute(CIAccounting.Periode.Name), print.getCurrentInstance().getOid());
        }
        final StringBuilder html = new StringBuilder();
        final FieldValue fieldValue = (FieldValue) _parameter.get(ParameterValues.UIOBJECT);

        html.append("<select name=\"").append(fieldValue.getField().getName()).append("\" ")
            .append(UIInterface.EFAPSTMPTAG).append(" size=\"1\">")
            .append("<option value=\"\">-</option>");
        for (final Entry<String, String> entry : values.entrySet()) {
            html.append("<option value=\"").append(entry.getValue())
                .append("\">").append(entry.getKey()).append("</option>");
        }
        html.append("</select>");
        final Return ret = new Return();
        ret.put(ReturnValues.SNIPLETT, html.toString());
        return ret;
    }


    /**
     * Update the rate fields on change of the date value.
     * @param _parameter    Parameter as passed from the eFaps API
     * @return  list needed for field update event
     * @throws EFapsException on error
     */
    public Return update4Periode(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final String periodeOid = _parameter.getParameterValue("periode");
        final Instance periodeInst = Instance.get(periodeOid);
        Context.getThreadContext().setSessionAttribute(Transaction_Base.PERIODE_SESSIONKEY,
                        periodeInst.isValid() ? periodeInst : null);
        return ret;
    }

    /**
     * Trigger for connect document to check and recalculate amount.
     *
     * @param _parameter Parameter as passed from the eFaps API.
     * @return new Return.
     * @throws EFapsException on error.
     */
    public Return updateAmount2Payment(final Parameter _parameter)
        throws EFapsException
    {
        final PrintQuery print = new PrintQuery(_parameter.getInstance());
        final SelectBuilder paySel = new SelectBuilder().linkto(CIAccounting.PaymentAbstract2Document.FromAbstractLink)
                                                                .attribute(CIAccounting.PaymentAbstract.OID);
        print.addSelect(paySel);
        print.execute();

        final Instance instPay = Instance.get(print.<String>getSelect(paySel));
        final Map<String, BigDecimal> map = new HashMap<String, BigDecimal>();
        if (instPay.isValid()) {
            final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.PaymentAbstract2Document);
            queryBldr.addWhereAttrEqValue(CIAccounting.PaymentAbstract2Document.FromAbstractLink, instPay.getId());
            final MultiPrintQuery multi = queryBldr.getPrint();
            final SelectBuilder selRateCur = new SelectBuilder()
                                                    .linkto(CIAccounting.PaymentAbstract2Document.ToAbstractLink)
                                                    .attribute(CISales.DocumentSumAbstract.RateCurrencyId);
            final SelectBuilder selCross = new SelectBuilder()
                                                    .linkto(CIAccounting.PaymentAbstract2Document.ToAbstractLink)
                                                    .attribute(CISales.DocumentSumAbstract.CrossTotal);
            multi.addSelect(selRateCur, selCross);
            multi.execute();
            while (multi.next()) {
                final Long curId = multi.<Long>getSelect(selRateCur);
                final BigDecimal crossTotal = multi.<BigDecimal>getSelect(selCross);
                if (map.isEmpty()) {
                    map.put("RateCurrencyId", new BigDecimal(curId));
                    map.put("CrossTotal", crossTotal);
                } else {
                    if (map.get("RateCurrencyId").toString().equals(curId.toString())) {
                        map.put("CrossTotal", map.get("CrossTotal").add(crossTotal));
                    } else {
                        throw new EFapsException(Payment.class, "update.Currency");
                    }
                }
            }
            final Update update = new Update(instPay);
            update.add(CIAccounting.PaymentAbstract.Date, DateTimeUtil.normalize(new DateMidnight().toDateTime()));
            update.add(CIAccounting.PaymentAbstract.Amount, map.get("CrossTotal"));
            update.add(CIAccounting.PaymentAbstract.CurrencyLink, Long.parseLong(map.get("RateCurrencyId").toString()));
            update.execute();
        }
        return new Return();
    }

}
