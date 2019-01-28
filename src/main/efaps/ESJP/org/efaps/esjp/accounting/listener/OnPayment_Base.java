/*
 * Copyright 2003 - 2019 The eFaps Team
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

package org.efaps.esjp.accounting.listener;

import org.efaps.admin.datamodel.Status;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.Instance;
import org.efaps.db.PrintQuery;
import org.efaps.esjp.accounting.transaction.Create;
import org.efaps.esjp.accounting.util.Accounting;
import org.efaps.esjp.ci.CISales;
import org.efaps.esjp.common.listener.ITypedClass;
import org.efaps.esjp.common.parameter.ParameterUtil;
import org.efaps.esjp.erp.CommonDocument_Base.CreatedDoc;
import org.efaps.esjp.sales.listener.IOnPayment;
import org.efaps.esjp.sales.payment.AbstractPaymentDocument;
import org.efaps.esjp.sales.util.Sales.AccountAutomation;
import org.efaps.util.EFapsException;

/**
 * @author The eFaps Team
 */
@EFapsUUID("7985e04b-a2e2-45f5-a516-f973a45548a1")
@EFapsApplication("eFapsApp-Accounting")
public abstract class OnPayment_Base
    implements IOnPayment
{

    @Override
    public void executeAutomation(final ITypedClass _typedClass,
                                  final Parameter _parameter,
                                  final CreatedDoc _createdDoc)
        throws EFapsException
    {
        if (Accounting.ACTIVATE.get() && _createdDoc.getInstance() != null && _createdDoc.getInstance().isValid()
                        && _createdDoc.getInstance().getType().isKindOf(CISales.PaymentDocumentIOAbstract.getType())) {
            final Instance paymentDocInst = _createdDoc.getInstance();
            if (_typedClass instanceof AbstractPaymentDocument) {
                final AccountAutomation auto = ((AbstractPaymentDocument) _typedClass).evaluateAutomation(_parameter,
                                paymentDocInst);
                switch (auto) {
                    // for transaction mode validate for the status, if closed fall through to "FULL"
                    case TRANSACTION:
                        final PrintQuery print = new PrintQuery(paymentDocInst);
                        print.addAttribute(CISales.PaymentDocumentIOAbstract.StatusAbstract);
                        print.executeWithoutAccessCheck();
                        final Status status = Status.get(print
                                        .<Long>getAttribute(CISales.PaymentDocumentIOAbstract.StatusAbstract));
                        if (!"Closed".equals(status.getKey())) {
                            break;
                        }
                    case FULL:
                        final Parameter parameter = ParameterUtil.clone(_parameter);
                        ParameterUtil.setParameterValues(parameter, "document", paymentDocInst.getOid());
                        new Create().create4PaymentMassive(parameter);
                        break;
                    default:
                        break;
                }
            }
        }
    }

    @Override
    public int getWeight()
    {
        return 0;
    }
}
