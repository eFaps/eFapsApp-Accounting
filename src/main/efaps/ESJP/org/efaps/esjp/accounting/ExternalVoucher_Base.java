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
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import org.efaps.admin.common.SystemConfiguration;
import org.efaps.admin.datamodel.Status;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Parameter.ParameterValues;
import org.efaps.admin.event.Return;
import org.efaps.admin.event.Return.ReturnValues;
import org.efaps.admin.program.esjp.EFapsRevision;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.Context;
import org.efaps.db.Insert;
import org.efaps.db.Instance;
import org.efaps.db.InstanceQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.esjp.accounting.transaction.Create;
import org.efaps.esjp.accounting.transaction.Transaction;
import org.efaps.esjp.accounting.transaction.Transaction_Base;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.ci.CIERP;
import org.efaps.esjp.erp.CurrencyInst;
import org.efaps.esjp.sales.document.AbstractDocument;
import org.efaps.util.EFapsException;


/**
 * TODO comment!
 *
 * @author The eFaps Team
 * @version $Id$
 */
@EFapsUUID("204d6d77-a034-455e-9867-afcada575c09")
@EFapsRevision("$Rev$")
public abstract class ExternalVoucher_Base
    extends AbstractDocument
{
    /**
     * Called from event for creation of a transaction for a External Document.
     *
     * @param _parameter Parameter as passed from the eFaps API
     * @return new Return
     * @throws EFapsException on error
     */
    @SuppressWarnings("unchecked")
    public Return create(final Parameter _parameter)
        throws EFapsException
    {

        final Instance contactInst = Instance.get(_parameter.getParameterValue("contact"));

        final Instance periodeInst = (Instance) Context.getThreadContext().getSessionAttribute(
                        Transaction_Base.PERIODE_SESSIONKEY);
        final CurrencyInst curr = new Periode().getCurrency(periodeInst);

        final Object[] rateObj = new Transaction().getRateObject(_parameter, "_Debit", 0);
        final BigDecimal rate = ((BigDecimal) rateObj[0]).divide((BigDecimal) rateObj[1], 12,
                        BigDecimal.ROUND_HALF_UP);
        final Instance rateCurrInst = Instance.get(CIERP.Currency.getType(),
                        _parameter.getParameterValue("currencyExternal"));
        final BigDecimal[] amounts = evalAmounts(_parameter);
        final Insert docInsert = new Insert(CIAccounting.ExternalVoucher);
        docInsert.add(CIAccounting.ExternalVoucher.Contact, contactInst.getId());
        docInsert.add(CIAccounting.ExternalVoucher.Name, _parameter.getParameterValue("extName"));
        docInsert.add(CIAccounting.ExternalVoucher.Date, _parameter.getParameterValue("date"));
        docInsert.add(CIAccounting.ExternalVoucher.RateCrossTotal, amounts[1]);
        docInsert.add(CIAccounting.ExternalVoucher.RateNetTotal, amounts[0]);
        docInsert.add(CIAccounting.ExternalVoucher.RateDiscountTotal, BigDecimal.ZERO);
        docInsert.add(CIAccounting.ExternalVoucher.DiscountTotal, BigDecimal.ZERO);
        docInsert.add(CIAccounting.ExternalVoucher.CrossTotal, amounts[1].divide(rate, BigDecimal.ROUND_HALF_UP));
        docInsert.add(CIAccounting.ExternalVoucher.NetTotal,  amounts[0].divide(rate, BigDecimal.ROUND_HALF_UP));
        docInsert.add(CIAccounting.ExternalVoucher.CurrencyId, curr.getInstance().getId());
        docInsert.add(CIAccounting.ExternalVoucher.RateCurrencyId, rateCurrInst.getId());
        docInsert.add(CIAccounting.ExternalVoucher.Rate, rateObj);
        docInsert.add(CIAccounting.ExternalVoucher.Status,
                        Status.find(CIAccounting.ExternalVoucherStatus.uuid, "Open").getId());
        docInsert.execute();

        _parameter.put(ParameterValues.INSTANCE, docInsert.getInstance());

        final Map<String, Object> parameters = (Map<String, Object>) _parameter.get(ParameterValues.PARAMETERS);
        parameters.put("date", _parameter.getParameterValues("extDate"));
        _parameter.put(ParameterValues.PARAMETERS, parameters);

        new Create().create4External(_parameter);
        return new Return();
    }


    /**
     * Get the Amounts for Net and Cross Total.
     *
     * @param _parameter Parameter as passed from the eFaps API
     * @return BigDecimal Array { NET, CROSS}
     * @throws EFapsException on error
     */
    protected BigDecimal[] evalAmounts(final Parameter _parameter)
        throws EFapsException
    {
        final Instance periodeInst = (Instance) Context.getThreadContext().getSessionAttribute(
                        Transaction_Base.PERIODE_SESSIONKEY);

        final DecimalFormat formater = (DecimalFormat) NumberFormat.getInstance(Context.getThreadContext().getLocale());
        formater.setParseBigDecimal(true);

        BigDecimal cross = BigDecimal.ZERO;
        BigDecimal net = BigDecimal.ZERO;
        try {
            final BigDecimal amount = (BigDecimal) formater.parse(_parameter.getParameterValue("amountExternal"));
            //Accounting-Configuration
            final SystemConfiguration config = SystemConfiguration.get(
                            UUID.fromString("ca0a1df1-2211-45d9-97c8-07af6636a9b9"));
            if (config != null) {
                final Properties props = config.getObjectAttributeValueAsProperties(periodeInst);
                final boolean isCross = "true".equalsIgnoreCase(props.getProperty("ExternalAmountIsCross"));
                final String vatAcc = props.getProperty("ExternalVATAccount");
                if (vatAcc != null && !vatAcc.isEmpty()) {
                    final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.AccountAbstract);
                    queryBldr.addWhereAttrEqValue(CIAccounting.AccountAbstract.PeriodeAbstractLink,
                                    periodeInst.getId());
                    queryBldr.addWhereAttrEqValue(CIAccounting.AccountAbstract.Name, vatAcc);
                    final InstanceQuery query = queryBldr.getQuery();
                    query.execute();
                    if (query.next()) {
                        final Instance accInst = query.getCurrentValue();
                        BigDecimal vat = getAmount(_parameter, accInst, "Debit", formater);
                        vat = vat.add(getAmount(_parameter, accInst, "Credit", formater));
                        vat = vat.abs();
                        if (isCross) {
                            cross = amount;
                            net = cross.subtract(vat);
                        } else {
                            net = amount;
                            cross = net.add(vat);
                        }
                    } else {
                        cross = amount;
                        net = amount;
                    }
                }

            }
        } catch (final ParseException e) {
            throw new EFapsException(ExternalVoucher_Base.class, "ParseException", e);
        }

        return new BigDecimal[] { net, cross };
    }

    /**
     * @param _parameter    Parameter as passed from the eFaps API
     * @param _accInst      instance of an account
     * @param _suffix       suffix
     * @param _formater     Formater
     * @return teh amount
     * @throws EFapsException on error
     * @throws ParseException on parse error
     */
    protected BigDecimal getAmount(final Parameter _parameter,
                                   final Instance _accInst,
                                   final String _suffix,
                                   final DecimalFormat _formater)
        throws EFapsException, ParseException
    {
        BigDecimal ret = BigDecimal.ZERO;
        final String[] accs = _parameter.getParameterValues("accountLink_" + _suffix);
        final String[] amounts = _parameter.getParameterValues("amount_" + _suffix);
        for (int i = 0; i < accs.length; i++) {
            final String acc = accs[i];
            if (acc.equals(_accInst.getOid())) {
                final Object[] rateObj = new Transaction().getRateObject(_parameter, "_" + _suffix, i);
                final BigDecimal rate = ((BigDecimal) rateObj[0]).divide((BigDecimal) rateObj[1], 12,
                                BigDecimal.ROUND_HALF_UP);
                BigDecimal rateAmount = ((BigDecimal) _formater.parse(amounts[i]))
                                                        .setScale(6, BigDecimal.ROUND_HALF_UP);
                if ("Debit".equalsIgnoreCase(_suffix)) {
                    rateAmount = rateAmount.negate();
                }
                ret = ret.add(rateAmount.divide(rate, 12, BigDecimal.ROUND_HALF_UP));
            }
        }
        return ret;
    }

    /**
     * Executed from an UIACCES tricker to determine if access must be
     * granted to the field or not.
     * @param _parameter Parameter as passed from the eFaps API
     * @return Return containing true if access is granted
     */
    public Return accessCheck4FieldPicker(final Parameter _parameter)
    {
        final Return ret = new Return();

        final Map<?, ?> properties = (Map<?, ?>) _parameter.get(ParameterValues.PROPERTIES);
        final String value = (String) properties.get("hasFieldPicker");

        // Accounting-Configuration
        final SystemConfiguration sysconf = SystemConfiguration.get(
                            UUID.fromString("ca0a1df1-2211-45d9-97c8-07af6636a9b9"));
        if ("true".equalsIgnoreCase(value)
                            && !sysconf.getAttributeValueAsBoolean("DeactivateFieldPicker4ExternalVoucher")) {
            ret.put(ReturnValues.TRUE, true);
        } else if ("false".equalsIgnoreCase(value)
                            && sysconf.getAttributeValueAsBoolean("DeactivateFieldPicker4ExternalVoucher")) {
            ret.put(ReturnValues.TRUE, true);
        }
        return ret;
    }
}
