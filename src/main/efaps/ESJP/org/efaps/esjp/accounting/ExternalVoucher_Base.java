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
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import org.efaps.admin.common.NumberGenerator;
import org.efaps.admin.common.SystemConfiguration;
import org.efaps.admin.datamodel.Classification;
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
import org.efaps.esjp.ci.CIFormAccounting;
import org.efaps.esjp.ci.CISales;
import org.efaps.esjp.common.uiform.Field;
import org.efaps.esjp.contacts.ContactsPicker;
import org.efaps.esjp.erp.CurrencyInst;
import org.efaps.esjp.sales.document.DocumentSum;
import org.efaps.esjp.sales.document.IncomingInvoice_Base;
import org.efaps.esjp.sales.util.Sales;
import org.efaps.esjp.sales.util.SalesSettings;
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
    extends DocumentSum
{
    /**
     * Called from event for creation of a transaction for a External Document.
     *
     * @param _parameter Parameter as passed from the eFaps API
     * @return new Return
     * @throws EFapsException on error
     */
    public Return create(final Parameter _parameter)
        throws EFapsException
    {
        Instance contactInst = Instance.get(_parameter.getParameterValue("contact"));
        if (!contactInst.isValid()) {
            contactInst = Instance.get(_parameter.getParameterValue("contactPicker"));
        }

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
        docInsert.add(CIAccounting.ExternalVoucher.Date, _parameter.getParameterValue("extDate"));
        docInsert.add(CIAccounting.ExternalVoucher.DueDate, _parameter.getParameterValue("extDueDate"));
        docInsert.add(CIAccounting.ExternalVoucher.RateCrossTotal, amounts[1]);
        docInsert.add(CIAccounting.ExternalVoucher.RateNetTotal, amounts[0]);
        docInsert.add(CIAccounting.ExternalVoucher.RateDiscountTotal, BigDecimal.ZERO);
        docInsert.add(CIAccounting.ExternalVoucher.DiscountTotal, BigDecimal.ZERO);
        docInsert.add(CIAccounting.ExternalVoucher.CrossTotal, amounts[1].divide(rate, BigDecimal.ROUND_HALF_UP));
        docInsert.add(CIAccounting.ExternalVoucher.NetTotal, amounts[0].divide(rate, BigDecimal.ROUND_HALF_UP));
        docInsert.add(CIAccounting.ExternalVoucher.CurrencyId, curr.getInstance().getId());
        docInsert.add(CIAccounting.ExternalVoucher.RateCurrencyId, rateCurrInst.getId());
        docInsert.add(CIAccounting.ExternalVoucher.Rate, rateObj);
        docInsert.add(CIAccounting.ExternalVoucher.Status,
                        Status.find(CIAccounting.ExternalVoucherStatus.uuid, "Open").getId());
        docInsert.add(CIAccounting.ExternalVoucher.Salesperson, Context.getThreadContext().getPersonId());
        //Sales_IncomingInvoiceSequence
        final NumberGenerator numgen = NumberGenerator.get(UUID.fromString("935a2a87-056d-4278-916b-388c53fa98e0"));
        if (numgen != null) {
            final String revision = numgen.getNextVal();
            Context.getThreadContext().setSessionAttribute(IncomingInvoice_Base.REVISIONKEY, revision);
            docInsert.add(CIAccounting.ExternalVoucher.Revision, revision);
        }

        docInsert.execute();

        _parameter.put(ParameterValues.INSTANCE, docInsert.getInstance());
        final Instance purchaseRecInst = Instance.get(_parameter.getParameterValue("purchaseRecord"));
        if (purchaseRecInst.isValid()) {
            final Insert purInsert = new Insert(CIAccounting.PurchaseRecord2Document);
            purInsert.add(CIAccounting.PurchaseRecord2Document.FromLink, purchaseRecInst.getId());
            purInsert.add(CIAccounting.PurchaseRecord2Document.ToLink, docInsert.getInstance().getId());
            purInsert.execute();
        }
        new Create().create4External(_parameter);

        connect2DocumentType(_parameter, docInsert.getInstance());
        return new Return();
    }

    @Override
    protected void connect2DocumentType(final Parameter _parameter,
                                        final Instance _instance)
        throws EFapsException
    {
        final Long docTypeId = Long.parseLong(_parameter
                        .getParameterValue(CIFormAccounting.Accounting_TransactionClassExternalForm.typeLink.name));
        if (docTypeId != null && _instance.isValid()) {
            final Insert insert = new Insert(CISales.Document2DocumentType);
            insert.add(CISales.Document2DocumentType.DocumentLink, _instance);
            insert.add(CISales.Document2DocumentType.DocumentTypeLink, docTypeId);
            insert.execute();
        }
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
     * @throws EFapsException on error
     */
    public Return accessCheck4FieldPicker(final Parameter _parameter)
        throws EFapsException
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

    /**
     * Method called from pickerForm to add a supplier contact granted to the
     * field or not.
     *
     * @param _parameter Parameter as passed from the eFaps API
     * @return Return the new Contact_Contacts recently created
     * @throws EFapsException on error
     */
    public Return addSupplier2Contact(final Parameter _parameter)
        throws EFapsException
    {
        final ContactsPicker contactsPicker = new ContactsPicker()
        {
            @Override
            protected void addClassSupplier(final Parameter _parameter,
                                            final Instance _contactInst)
                throws EFapsException
            {

                final Classification classification = (Classification) CISales.Contacts_ClassSupplier.getType();
                final Insert relInsert1 = new Insert(classification.getClassifyRelationType());
                relInsert1.add(classification.getRelLinkAttributeName(), _contactInst.getId());
                relInsert1.add(classification.getRelTypeAttributeName(), classification.getId());
                relInsert1.execute();

                final Insert classInsert1 = new Insert(classification);
                classInsert1.add(classification.getLinkAttributeName(), _contactInst.getId());
                addClassInsert(_parameter, classInsert1);
                classInsert1.execute();

                final Classification classification2 = (Classification) classification.getParentClassification();
                final Insert relInsert2 = new Insert(classification2.getClassifyRelationType());
                relInsert2.add(classification.getRelLinkAttributeName(), _contactInst.getId());
                relInsert2.add(classification.getRelTypeAttributeName(), classification2.getId());
                relInsert2.execute();

                final Insert classInsert2 = new Insert(classification2);
                classInsert2.add(classification.getLinkAttributeName(), _contactInst.getId());
                classInsert2.execute();
            }
        };
        return contactsPicker.picker4NewContact(_parameter);
    }

    @Override
    public Return dropDown4DocumentType(final Parameter _parameter)
        throws EFapsException
    {
        return new Field() {
            @Override
            protected void updatePositionList(final Parameter _parameter,
                                              final List<DropDownPosition> _values) throws EFapsException
            {
                Boolean hasSelect = false;
                for (final DropDownPosition val : _values) {
                    if (val.isSelected()) {
                        hasSelect = true;
                    }
                }
                if (!hasSelect) {
                    final Properties props = Sales.getSysConfig()
                                    .getAttributeValueAsProperties(SalesSettings.DEFAULTDOCTYPE4DOC);
                    if (props != null) {
                        final Instance defInst = Instance.get(props.getProperty(CIAccounting.ExternalVoucher.getType()
                                        .getUUID().toString()));
                        if (defInst.isValid()) {
                            for (final DropDownPosition val : _values) {
                                if (val.getValue().toString().equals(defInst.getId())) {
                                    val.setSelected(true);
                                }
                            }
                        }
                    }
                }
            }

        }.dropDownFieldValue(_parameter);
    }
}
