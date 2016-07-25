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

import org.efaps.admin.datamodel.Type;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.ci.CIType;
import org.efaps.db.Insert;
import org.efaps.db.Instance;
import org.efaps.db.PrintQuery;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.ci.CIFormAccounting;
import org.efaps.esjp.ci.CISales;
import org.efaps.esjp.sales.document.AbstractDocument;
import org.efaps.util.EFapsException;
import org.joda.time.DateTime;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 */
@EFapsUUID("ff265bae-8984-44ba-84cf-8529eb4424bb")
@EFapsApplication("eFapsApp-Accounting")
public abstract class TransactionDocument_Base
    extends AbstractDocument
{

    /**
     * @param _parameter Parameter as passed by the eFaps API
     * @param _transInst instance of the transaction this document belongs to
     * @return newly created doc
     * @throws EFapsException on error
     */
    public CreatedDoc createDoc4Transaction(final Parameter _parameter,
                                            final Instance _transInst)
        throws EFapsException
    {
        final CreatedDoc ret = new CreatedDoc();
        final PrintQuery print = new PrintQuery(_transInst);
        print.addAttribute(CIAccounting.Transaction.Date);
        print.executeWithoutAccessCheck();

        final Insert insert = new Insert(CIAccounting.TransactionDocument);
        insert.add(CIAccounting.TransactionDocument.Date, print.<DateTime>getAttribute(CIAccounting.Transaction.Date));

        final Instance contactInst = Instance.get(_parameter
                        .getParameterValue(CIFormAccounting.Accounting_TransactionForm.contact.name));
        if (contactInst.isValid()) {
            insert.add(CIAccounting.TransactionDocument.Contact, contactInst);
        }
        insert.add(CIAccounting.TransactionDocument.Note,
                        _parameter.getParameterValue(CIFormAccounting.Accounting_TransactionForm.note.name));

        final String name = getDocName4Create(_parameter);
        insert.add(CISales.TransactionDocument.Name, name);

        addStatus2DocCreate(_parameter, insert, ret);
        add2DocCreate(_parameter, insert, ret);
        insert.execute();
        ret.setInstance(insert.getInstance());

        connect2Object(_parameter, ret);

        return ret;
    }

    @Override
    protected Type getType4DocCreate(final Parameter _parameter)
        throws EFapsException
    {
        return getCIType().getType();
    }

    @Override
    public CIType getCIType()
        throws EFapsException
    {
        return CIAccounting.TransactionDocument;
    }
}
