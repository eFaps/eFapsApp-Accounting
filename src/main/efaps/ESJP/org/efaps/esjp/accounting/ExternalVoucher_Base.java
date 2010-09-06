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

import org.efaps.admin.datamodel.Status;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Parameter.ParameterValues;
import org.efaps.admin.event.Return;
import org.efaps.admin.program.esjp.EFapsRevision;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.Context;
import org.efaps.db.Insert;
import org.efaps.db.Instance;
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

        final DecimalFormat formater = (DecimalFormat) NumberFormat.getInstance(Context.getThreadContext().getLocale());
        formater.setParseBigDecimal(true);

        BigDecimal amount = BigDecimal.ZERO;
        try {
            amount = (BigDecimal) formater.parse(_parameter.getParameterValue("amountExternal"));
        } catch (final ParseException e) {
            throw new EFapsException(ExternalVoucher_Base.class, "ParseException", e);
        }

        final Instance contactInst = Instance.get(_parameter.getParameterValue("contact"));

        final Instance periodeInst = (Instance) Context.getThreadContext().getSessionAttribute(
                        Transaction_Base.PERIODE_SESSIONKEY);
        final CurrencyInst curr = new Periode().getCurrency(periodeInst);

        final Object[] rateObj = new Transaction().getRateObject(_parameter, "_Debit", 0);
        final BigDecimal rate = ((BigDecimal) rateObj[0]).divide((BigDecimal) rateObj[1], 12,
                        BigDecimal.ROUND_HALF_UP);
        final Instance rateCurrInst = Instance.get(CIERP.Currency.getType(),
                        _parameter.getParameterValue("currencyExternal"));

        final Insert docInsert = new Insert(CIAccounting.ExternalVoucher);
        docInsert.add(CIAccounting.ExternalVoucher.Contact, contactInst.getId());
        docInsert.add(CIAccounting.ExternalVoucher.Name, _parameter.getParameterValue("extName"));
        docInsert.add(CIAccounting.ExternalVoucher.Date, _parameter.getParameterValue("date"));
        docInsert.add(CIAccounting.ExternalVoucher.RateCrossTotal, amount);
        docInsert.add(CIAccounting.ExternalVoucher.RateNetTotal, amount);
        docInsert.add(CIAccounting.ExternalVoucher.RateDiscountTotal, BigDecimal.ZERO);
        docInsert.add(CIAccounting.ExternalVoucher.DiscountTotal, BigDecimal.ZERO);
        docInsert.add(CIAccounting.ExternalVoucher.CrossTotal, amount.divide(rate, BigDecimal.ROUND_HALF_UP));
        docInsert.add(CIAccounting.ExternalVoucher.NetTotal, amount.divide(rate, BigDecimal.ROUND_HALF_UP));
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

}
