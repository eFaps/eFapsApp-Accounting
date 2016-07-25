/*
 * Copyright 2003 - 2016 The eFaps Team
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

package org.efaps.esjp.accounting.transaction;

import java.math.BigDecimal;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Return;
import org.efaps.admin.event.Return.ReturnValues;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.esjp.erp.AbstractPositionWarning;
import org.efaps.esjp.erp.AbstractWarning;
import org.efaps.esjp.erp.IWarning;
import org.efaps.esjp.erp.RateFormatter;
import org.efaps.esjp.erp.WarningUtil;
import org.efaps.esjp.sales.document.Validation_Base.AreYouSureWarning;
import org.efaps.util.EFapsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 */
@EFapsUUID("3af252ad-bfc4-4b28-af9b-aed39a65fdfc")
@EFapsApplication("eFapsApp-Accounting")
public abstract class Validation_Base
    extends Transaction
{

    /**
     * Logger for this class.
     */
    private static final Logger LOG = LoggerFactory.getLogger(Validation.class);

    /**
     * Validate.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return the return
     * @throws EFapsException on error
     */
    public Return validate(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final boolean areyousure = true;

        final List<IWarning> warnings = new ArrayList<IWarning>();
        warnings.addAll(new org.efaps.esjp.sales.document.Validation().validateName(_parameter, null));
        warnings.addAll(validatePositions(_parameter, "Debit"));
        warnings.addAll(validatePositions(_parameter, "Credit"));
        warnings.addAll(validateSums(_parameter));

        if (warnings.isEmpty() && areyousure) {
            warnings.add(new AreYouSureWarning());
        }

        if (warnings.isEmpty()) {
            ret.put(ReturnValues.TRUE, true);
        } else {
            ret.put(ReturnValues.SNIPLETT, WarningUtil.getHtml4Warning(warnings).toString());
            if (!WarningUtil.hasError(warnings)) {
                ret.put(ReturnValues.TRUE, true);
            }
        }
        return ret;
    }

    /**
     * Validate sums.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return the list< i warning>
     * @throws EFapsException on error
     */
    protected List<IWarning> validateSums(final Parameter _parameter)
        throws EFapsException
    {
        final List<IWarning> ret = new ArrayList<>();
        final BigDecimal debit = getSum4UI(_parameter, "Debit", null, null);
        final BigDecimal credit = getSum4UI(_parameter, "Credit", null, null);
        if (debit.compareTo(BigDecimal.ZERO) == 0) {
            ret.add(new AmountGreateZeroWarning());
        } else if (credit.compareTo(BigDecimal.ZERO) == 0) {
            ret.add(new AmountGreateZeroWarning());
        } else if (credit.subtract(debit).compareTo(BigDecimal.ZERO) != 0) {
            ret.add(new AmountsNotEqualWarning());
        }
        return ret;
    }

    /**
     * @param _parameter Parameter as passed from the eFaps API
     * @param _postFix postfix
     * @return true
     * @throws EFapsException on error
     */
    protected List<IWarning> validatePositions(final Parameter _parameter,
                                               final String _postFix)
        throws EFapsException
    {
        final List<IWarning> ret = new ArrayList<>();
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
                        ret.add(new PositionWarning().setPosition(i));
                    }
                }
            }
        } catch (final ParseException e) {
            LOG.error("Catched ParserException", e);
        }
        return ret;
    }

    /**
     * Warning for not enough Stock.
     */
    public static class PositionWarning
        extends AbstractPositionWarning
    {

        /**
         * Constructor.
         */
        public PositionWarning()
        {
            setError(true);
        }
    }

    /**
     * Warning for amount greater zero.
     */
    public static class AmountGreateZeroWarning
        extends AbstractWarning
    {

        /**
         * Constructor.
         */
        public AmountGreateZeroWarning()
        {
            setError(true);
        }
    }

    /**
     * Warning for amount greater zero.
     */
    public static class AmountsNotEqualWarning
        extends AbstractWarning
    {

        /**
         * Constructor.
         */
        public AmountsNotEqualWarning()
        {
            setError(true);
        }
    }
}
