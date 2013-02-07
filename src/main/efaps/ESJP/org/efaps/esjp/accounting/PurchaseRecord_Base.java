package org.efaps.esjp.accounting;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.efaps.admin.common.SystemConfiguration;
import org.efaps.admin.datamodel.Status;
import org.efaps.admin.datamodel.Type;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Parameter.ParameterValues;
import org.efaps.admin.event.Return;
import org.efaps.admin.program.esjp.EFapsRevision;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.AttributeQuery;
import org.efaps.db.Instance;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.PrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.db.Update;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.ci.CIERP;
import org.efaps.esjp.ci.CISales;
import org.efaps.esjp.common.uitable.MultiPrint;
import org.efaps.util.EFapsException;
import org.joda.time.DateTime;

/*
 * Copyright 2003 - 2013 The eFaps Team
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

/**
 * TODO description!
 *
 * @author The eFasp Team
 * @version $Id$
 */
@EFapsUUID("2198d008-b65f-4d3c-b84d-48250c047708")
@EFapsRevision("$Rev$")
public abstract class PurchaseRecord_Base
{

    public Return documentMultiPrint(final Parameter _parameter)
        throws EFapsException
    {
        final MultiPrint multi = new MultiPrint()
        {

            @Override
            protected void add2QueryBldr(final Parameter _parameter,
                                         final QueryBuilder _queryBldr)
                throws EFapsException
            {
                final QueryBuilder attrQueryBldr = new QueryBuilder(CIAccounting.PurchaseRecord2Document);
                final AttributeQuery attrQuery = attrQueryBldr.getAttributeQuery(
                                CIAccounting.PurchaseRecord2Document.ToLink);
                _queryBldr.addWhereAttrNotInQuery(CIERP.DocumentAbstract.ID, attrQuery);

                final Map<?, ?> properties = (Map<?, ?>) _parameter.get(ParameterValues.PROPERTIES);
                final String statusStr = (String) properties.get("NoStatus");
                final String typesStr = (String) properties.get("Types");
                final List<Long> idStatus = new ArrayList<Long>();
                final String[] types = typesStr.split(";");
                for (final String type : types) {
                    idStatus.add(Status.find(Type.get(type).getStatusAttribute().getLink().getUUID(), statusStr)
                                    .getId());
                }
                _queryBldr.addWhereAttrNotEqValue(CISales.DocumentAbstract.StatusAbstract, idStatus.toArray());
            }
        };
        return multi.execute(_parameter);
    }

    public Return insertPostTrigger(final Parameter _parameter)
        throws EFapsException
    {
        final Instance instance = _parameter.getInstance();
        final PrintQuery print = new PrintQuery(instance);
        final SelectBuilder selectDoc = new SelectBuilder().linkto(CIAccounting.PurchaseRecord2Document.ToLink).oid();
        print.addSelect(selectDoc);
        print.execute();
        final String docOid = print.<String>getSelect(selectDoc);
        final Instance docInst = Instance.get(docOid);
        if (docInst.isValid()) {
            if (docInst.getType().isKindOf(CISales.IncomingInvoice.getType())) {
                // Acccounting Configuration
                final SystemConfiguration config = SystemConfiguration.get(
                                UUID.fromString("ca0a1df1-2211-45d9-97c8-07af6636a9b9"));
                if (config != null) {
                    final Instance link = config.getLink("PurchaseRecord4IncomingInvoice.TypeLink");
                    if (link.isValid()) {
                        final Update update = new Update(instance);
                        update.add(CIAccounting.PurchaseRecord2Document.TypeLink, link.getId());
                        update.executeWithoutTrigger();
                    }
                }
            } else if (docInst.getType().isKindOf(CIAccounting.ExternalVoucher.getType())) {
                final PrintQuery docPrint = new PrintQuery(docInst);
                final SelectBuilder sel = new SelectBuilder()
                                .linkfrom(CIAccounting.TransactionClassExternal,
                                                CIAccounting.TransactionClassExternal.DocumentLink)
                                                .attribute(CIAccounting.TransactionClassExternal.TypeLink);
                docPrint.addSelect(sel);
                docPrint.executeWithoutAccessCheck();
                final Long typeLinkId = docPrint.<Long>getSelect(sel);
                if (typeLinkId != null) {
                    final Update update = new Update(instance);
                    update.add(CIAccounting.PurchaseRecord2Document.TypeLink, typeLinkId);
                    update.executeWithoutTrigger();
                }
            }

            final QueryBuilder docAttrBldr = new QueryBuilder(CISales.Payment);
            docAttrBldr.addWhereAttrEqValue(CISales.Payment.CreateDocument, docInst.getId());
            final AttributeQuery attrQuery = docAttrBldr.getAttributeQuery(CISales.Payment.TargetDocument);

            final QueryBuilder queryBldr = new QueryBuilder(CISales.PaymentDetractionOut);
            queryBldr.addWhereAttrInQuery(CISales.PaymentDetractionOut.ID, attrQuery);
            final MultiPrintQuery multi = queryBldr.getPrint();
            multi.addAttribute(CISales.PaymentDetractionOut.Date, CISales.PaymentDetractionOut.Name,
                            CISales.PaymentDetractionOut.Amount);
            multi.executeWithoutAccessCheck();
            if (multi.next()) {
                final DateTime date = multi.<DateTime>getAttribute(CISales.PaymentDetractionOut.Date);
                final String name = multi.<String>getAttribute(CISales.PaymentDetractionOut.Name);
                final BigDecimal amount = multi.<BigDecimal>getAttribute(CISales.PaymentDetractionOut.Amount);
                final Update update = new Update(instance);
                update.add(CIAccounting.PurchaseRecord2Document.DetractionName, name);
                update.add(CIAccounting.PurchaseRecord2Document.DetractionDate, date);
                update.add(CIAccounting.PurchaseRecord2Document.DetractionAmount, amount);
                update.executeWithoutTrigger();
            }
        }
        return new Return();
    }
}
