/*
 * Copyright 2003 - 2017 The eFaps Team
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

package org.efaps.esjp.accounting.transaction.evaluation;

import java.math.BigDecimal;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import org.efaps.admin.event.Parameter;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.Instance;
import org.efaps.esjp.ci.CIERP;
import org.efaps.esjp.erp.NumberFormatter;
import org.efaps.esjp.erp.RateInfo;
import org.efaps.ui.wicket.util.DateUtil;
import org.efaps.util.EFapsException;
import org.joda.time.DateTime;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 */
@EFapsUUID("9c425213-497b-41ba-9a9a-cfcbd296120f")
@EFapsApplication("eFapsApp-Accounting")
public abstract class GenericEvaluation_Base
    extends AbstractEvaluation
{

    /**
     * @param _parameter Parameter as passed by the eFaps API
     * @return new Document
     * @throws EFapsException on error
     */
    @Override
    public List<DocumentInfo> evalDocuments(final Parameter _parameter)
        throws EFapsException
    {
        final List<DocumentInfo> ret = new ArrayList<>();
        final DocumentInfo docInfo = new DocumentInfo();
        ret.add(docInfo);
        final String curr = _parameter.getParameterValue("currencyExternal");
        final String amountStr = _parameter.getParameterValue("amountExternal");
        docInfo.setFormater(NumberFormatter.get().getTwoDigitsFormatter());
        try {
            docInfo.setAmount((BigDecimal) docInfo.getFormater().parse(
                            amountStr.isEmpty() ? "0" : amountStr));
        } catch (final ParseException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        final Instance currInst = Instance.get(CIERP.Currency.getType(), Long.parseLong(curr));
        if (_parameter.getParameterValue("date") != null) {
            docInfo.setDate(new DateTime(_parameter.getParameterValue("date")));
        } else {
            docInfo.setDate(DateUtil.getDateFromParameter(
                            _parameter.getParameterValue("date_eFapsDate")));
        }
        final RateInfo rateInfo = evaluateRate(_parameter, docInfo.getRateDate(), currInst);
        docInfo.setRateInfo(rateInfo);

        add2Doc4Case(_parameter, docInfo);
        return ret;
    }
}
