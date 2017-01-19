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

import java.util.List;

import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Return;
import org.efaps.admin.event.Return.ReturnValues;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.esjp.accounting.transaction.DocumentInfo;
import org.efaps.esjp.accounting.transaction.Transaction;
import org.efaps.util.EFapsException;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 */
@EFapsUUID("2c12b39c-679e-46ca-b972-13866326e4c4")
@EFapsApplication("eFapsApp-Accounting")
public abstract class AbstractEvaluation_Base
    extends Transaction
{

    /**
     * Executed the command on the button.
     *
     * @param _parameter Parameter as passed from the eFaps API
     * @return new Return
     * @throws EFapsException on error
     */
    @Override
    public Return executeButton(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final List<DocumentInfo> docs = evalDocuments(_parameter);
        if (docs != null && !docs.isEmpty()) {
            final DocumentInfo docInfo = DocumentInfo.getCombined(docs,
                            getSummarizeConfig(_parameter), getSummarizeCriteria(_parameter));
            docInfo.applyRounding(_parameter);
            final StringBuilder js = getScript4ExecuteButton(_parameter, docInfo);
            ret.put(ReturnValues.SNIPLETT, js.toString());
        }
        return ret;
    }

    /**
     * @param _parameter Parameter as passed by the eFasp API
     * @param _doc      doc the script must be build for
     * @return StringBuilder
     * @throws EFapsException on error
     */
    @Override
    protected StringBuilder getScript4ExecuteButton(final Parameter _parameter,
                                                    final DocumentInfo _doc)
        throws EFapsException
    {
        final StringBuilder js = new StringBuilder()
            .append(getSetFieldValue(0, "sumDebit", _doc.getDebitSumFormated(_parameter)))
            .append(getSetFieldValue(0, "sumCredit", _doc.getCreditSumFormated(_parameter)))
            .append(getSetFieldValue(0, "sumTotal", _doc.getDifferenceFormated(_parameter)))
            .append(getSetSubJournalScript(_parameter, _doc))
            .append(getTableJS(_parameter, "Debit", _doc.getDebitAccounts()))
            .append(getTableJS(_parameter, "Credit", _doc.getCreditAccounts()));

        final String desc = _doc.getDescription(_parameter);
        if (desc != null) {
            js.append(getSetFieldValue(0, "description", desc));
        }
        return js;
    }
}
