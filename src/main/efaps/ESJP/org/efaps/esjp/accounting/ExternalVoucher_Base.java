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

import org.efaps.admin.common.NumberGenerator;
import org.efaps.admin.common.SystemConfiguration;
import org.efaps.admin.datamodel.Classification;
import org.efaps.admin.datamodel.Dimension;
import org.efaps.admin.datamodel.Status;
import org.efaps.admin.datamodel.Type;
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
import org.efaps.db.PrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.esjp.accounting.transaction.Create;
import org.efaps.esjp.accounting.transaction.Transaction;
import org.efaps.esjp.accounting.transaction.Transaction_Base;
import org.efaps.esjp.accounting.util.Accounting;
import org.efaps.esjp.accounting.util.AccountingSettings;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.ci.CIContacts;
import org.efaps.esjp.ci.CIERP;
import org.efaps.esjp.ci.CIFormAccounting;
import org.efaps.esjp.ci.CIProducts;
import org.efaps.esjp.ci.CISales;
import org.efaps.esjp.contacts.ContactsPicker;
import org.efaps.esjp.erp.CurrencyInst;
import org.efaps.esjp.sales.Tax_Base;
import org.efaps.esjp.sales.document.DocumentSum;
import org.efaps.esjp.sales.document.IncomingInvoice_Base;
import org.efaps.esjp.sales.util.Sales;
import org.efaps.esjp.sales.util.SalesSettings;
import org.efaps.util.EFapsException;
import org.joda.time.DateTime;

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
     * Used as key for passing values.
     */
    private static final String AMOUNTSKEY = ExternalVoucher.class.getName() + ".Key4Amounts";

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
        final CreatedDoc createdDoc = createDoc(_parameter);
        createPositions(_parameter, createdDoc);
        connect2DocumentType(_parameter, createdDoc);

        _parameter.put(ParameterValues.INSTANCE, createdDoc.getInstance());
        new Create().create4External(_parameter);

        final Instance purchaseRecInst = Instance.get(_parameter.getParameterValue("purchaseRecord"));
        if (purchaseRecInst.isValid()) {
            final Insert purInsert = new Insert(CIAccounting.PurchaseRecord2Document);
            purInsert.add(CIAccounting.PurchaseRecord2Document.FromLink, purchaseRecInst.getId());
            purInsert.add(CIAccounting.PurchaseRecord2Document.ToLink, createdDoc.getInstance().getId());
            purInsert.execute();
        }


        return new Return();
    }

    @Override
    protected CreatedDoc createDoc(final Parameter _parameter)
        throws EFapsException
    {
        final CreatedDoc createdDoc = new CreatedDoc();
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
        BigDecimal netTotal = amounts[0];
        BigDecimal crossTotal = amounts[1];
        final BigDecimal taxfree = amounts[2];

        netTotal = netTotal.add(taxfree);
        crossTotal = crossTotal.add(taxfree);

        createdDoc.addValue(ExternalVoucher_Base.AMOUNTSKEY, amounts);

        final Insert docInsert = new Insert(CIAccounting.ExternalVoucher);
        if (contactInst != null && contactInst.isValid()) {
            docInsert.add(CIAccounting.ExternalVoucher.Contact, contactInst.getId());
            createdDoc.addValue(getFieldName4Attribute(_parameter,
                            CIAccounting.ExternalVoucher.Contact.name), contactInst.getId());
        }

        final String extName = _parameter.getParameterValue("extName");
        if (extName != null) {
            docInsert.add(CIAccounting.ExternalVoucher.Name, extName);
            createdDoc.addValue(getFieldName4Attribute(_parameter,
                            CIAccounting.ExternalVoucher.Name.name), extName);
        }

        final String extDate = _parameter.getParameterValue("extDate");
        if (extDate != null) {
            docInsert.add(CIAccounting.ExternalVoucher.Date, extDate);
            createdDoc.addValue(getFieldName4Attribute(_parameter,
                            CIAccounting.ExternalVoucher.Date.name), extDate);
        }

        final String extDueDate = _parameter.getParameterValue("extDueDate");
        if (extDueDate != null) {
            docInsert.add(CIAccounting.ExternalVoucher.DueDate, extDueDate);
            createdDoc.addValue(getFieldName4Attribute(_parameter,
                            CIAccounting.ExternalVoucher.DueDate.name), extDueDate);
        }

        final String note = _parameter.getParameterValue("note");
        if (note != null) {
            docInsert.add(CIAccounting.ExternalVoucher.Note, note);
            createdDoc.addValue(getFieldName4Attribute(_parameter,
                            CIAccounting.ExternalVoucher.Note.name), note);
        }

        if (amounts != null) {
            docInsert.add(CIAccounting.ExternalVoucher.RateCrossTotal, crossTotal);
            createdDoc.addValue(getFieldName4Attribute(_parameter,
                            CIAccounting.ExternalVoucher.RateCrossTotal.name), crossTotal);

            docInsert.add(CIAccounting.ExternalVoucher.RateNetTotal, netTotal);
            createdDoc.addValue(getFieldName4Attribute(_parameter,
                            CIAccounting.ExternalVoucher.RateNetTotal.name), netTotal);

            docInsert.add(CIAccounting.ExternalVoucher.CrossTotal,
                            crossTotal.setScale(8).divide(rate, BigDecimal.ROUND_HALF_UP));
            createdDoc.addValue(getFieldName4Attribute(_parameter, CIAccounting.ExternalVoucher.CrossTotal.name),
                            crossTotal.setScale(8).divide(rate, BigDecimal.ROUND_HALF_UP));

            docInsert.add(CIAccounting.ExternalVoucher.NetTotal,
                            netTotal.setScale(8).divide(rate, BigDecimal.ROUND_HALF_UP));
            createdDoc.addValue(getFieldName4Attribute(_parameter, CIAccounting.ExternalVoucher.NetTotal.name),
                            netTotal.setScale(8).divide(rate, BigDecimal.ROUND_HALF_UP));
        }
        docInsert.add(CIAccounting.ExternalVoucher.RateDiscountTotal, BigDecimal.ZERO);
        docInsert.add(CIAccounting.ExternalVoucher.DiscountTotal, BigDecimal.ZERO);

        if (curr != null && curr.getInstance().isValid()) {
            docInsert.add(CIAccounting.ExternalVoucher.CurrencyId, curr.getInstance().getId());
            createdDoc.addValue(getFieldName4Attribute(_parameter, CIAccounting.ExternalVoucher.CurrencyId.name),
                            curr.getInstance().getId());
        }

        if (rateCurrInst != null && rateCurrInst.isValid()) {
            docInsert.add(CIAccounting.ExternalVoucher.RateCurrencyId, rateCurrInst.getId());
            createdDoc.addValue(getFieldName4Attribute(_parameter, CIAccounting.ExternalVoucher.CurrencyId.name),
                            rateCurrInst.getId());
        }

        if (rateObj != null) {
            docInsert.add(CIAccounting.ExternalVoucher.Rate, rateObj);
            createdDoc.addValue(getFieldName4Attribute(_parameter, CIAccounting.ExternalVoucher.Rate.name), rateObj);
        }

        final long statusId = Status.find(CIAccounting.ExternalVoucherStatus.uuid, "Open").getId();
        if (statusId > 0) {
            docInsert.add(CIAccounting.ExternalVoucher.Status, statusId);
            createdDoc.addValue(getFieldName4Attribute(_parameter, CIAccounting.ExternalVoucher.Status.name), statusId);
        }

        docInsert.add(CIAccounting.ExternalVoucher.Salesperson, Context.getThreadContext().getPersonId());
        createdDoc.addValue(getFieldName4Attribute(_parameter, CIAccounting.ExternalVoucher.Salesperson.name),
                        Context.getThreadContext().getPersonId());

        //Sales_IncomingInvoiceSequence
        final NumberGenerator numgen = NumberGenerator.get(UUID.fromString("935a2a87-056d-4278-916b-388c53fa98e0"));
        if (numgen != null) {
            final String revision = numgen.getNextVal();
            Context.getThreadContext().setSessionAttribute(IncomingInvoice_Base.REVISIONKEY, revision);
            docInsert.add(CIAccounting.ExternalVoucher.Revision, revision);
            createdDoc.addValue(getFieldName4Attribute(_parameter, CIAccounting.ExternalVoucher.Revision.name), revision);
        }

        docInsert.execute();
        createdDoc.setInstance(docInsert.getInstance());
        return createdDoc;
    }

    @Override
    protected Type getType4PositionCreate(final Parameter _parameter)
        throws EFapsException
    {
        return CIAccounting.ExternalVoucherPosition.getType();
    }

    protected void insertPosition(final Parameter _parameter,
                                  final CreatedDoc _createdDoc,
                                  final Instance _productInst,
                                  final int _idx,
                                  final BigDecimal _netTotal,
                                  final BigDecimal _crossTotal)
        throws EFapsException
    {
        final Instance periodeInst = (Instance) Context.getThreadContext().getSessionAttribute(
                        Transaction_Base.PERIODE_SESSIONKEY);
        new Periode().getCurrency(periodeInst);

        final Object[] rateObj = new Transaction().getRateObject(_parameter, "_Debit", 0);
        final BigDecimal rate = ((BigDecimal) rateObj[0]).divide((BigDecimal) rateObj[1], 12,
                        BigDecimal.ROUND_HALF_UP);
        final Instance rateCurrInst = Instance.get(CIERP.Currency.getType(),
                        _parameter.getParameterValue("currencyExternal"));

        final Insert posIns = new Insert(getType4PositionCreate(_parameter));
        posIns.add(CISales.PositionAbstract.Quantity, 1);
        posIns.add(CISales.PositionAbstract.DocumentAbstractLink, _createdDoc.getInstance());
        posIns.add(CISales.PositionAbstract.PositionNumber, _idx);
        posIns.add(CISales.PositionAbstract.ProductDesc, _parameter.getParameterValue(
                        CIFormAccounting.Accounting_TransactionCreate4ExternalVoucherForm.description.name));

        final PrintQuery print = new PrintQuery(_productInst);
        print.addAttribute(CIProducts.ProductAbstract.Dimension, CIProducts.ProductAbstract.TaxCategory);
        print.executeWithoutAccessCheck();
        final Long dimId = print.<Long>getAttribute(CIProducts.ProductAbstract.Dimension);
        final Long taxId = print.<Long>getAttribute(CIProducts.ProductAbstract.TaxCategory);

        posIns.add(CISales.PositionAbstract.Product, _productInst);
        posIns.add(CISales.PositionAbstract.UoM, Dimension.get(dimId).getBaseUoM());
        posIns.add(CISales.PositionSumAbstract.Discount, BigDecimal.ZERO);

        final String dateStr = (String) _createdDoc.getValue(getFieldName4Attribute(_parameter,
                        CIAccounting.ExternalVoucher.Date.name));
        DateTime date;
        if (dateStr == null) {
            date = new DateTime();
        } else {
            date = new DateTime(dateStr);
        }
        posIns.add(CISales.PositionSumAbstract.Tax, Tax_Base.get(taxId).getTaxRate(date.toLocalDate()).getId());

        posIns.add(CISales.PositionSumAbstract.CrossUnitPrice, _crossTotal.setScale(12, BigDecimal.ROUND_HALF_UP)
                        .divide(rate, BigDecimal.ROUND_HALF_UP));
        posIns.add(CISales.PositionSumAbstract.NetUnitPrice, _netTotal.setScale(12, BigDecimal.ROUND_HALF_UP)
                        .divide(rate, BigDecimal.ROUND_HALF_UP));
        posIns.add(CISales.PositionSumAbstract.CrossPrice, _crossTotal.setScale(12, BigDecimal.ROUND_HALF_UP)
                        .divide(rate, BigDecimal.ROUND_HALF_UP));
        posIns.add(CISales.PositionSumAbstract.NetPrice, _netTotal.setScale(12, BigDecimal.ROUND_HALF_UP)
                        .divide(rate, BigDecimal.ROUND_HALF_UP));

        posIns.add(CISales.PositionSumAbstract.DiscountNetUnitPrice, _netTotal.setScale(12, BigDecimal.ROUND_HALF_UP)
                        .divide(rate, BigDecimal.ROUND_HALF_UP));
        posIns.add(CISales.PositionSumAbstract.CurrencyId, Sales.getSysConfig().getLink(SalesSettings.CURRENCYBASE));
        posIns.add(CISales.PositionSumAbstract.Rate, rateObj);
        posIns.add(CISales.PositionSumAbstract.RateCurrencyId, rateCurrInst.getId());
        posIns.add(CISales.PositionSumAbstract.RateNetUnitPrice, _netTotal);
        posIns.add(CISales.PositionSumAbstract.RateCrossUnitPrice, _crossTotal);
        posIns.add(CISales.PositionSumAbstract.RateDiscountNetUnitPrice, _netTotal);
        posIns.add(CISales.PositionSumAbstract.RateNetPrice, _netTotal);
        posIns.add(CISales.PositionSumAbstract.RateCrossPrice, _crossTotal);
        posIns.execute();
        _createdDoc.addPosition(posIns.getInstance());
    }

    @Override
    protected void createPositions(final Parameter _parameter,
                                   final CreatedDoc _createdDoc)
        throws EFapsException
    {
        final Instance vatProdInst = Accounting.getSysConfig().getLink(AccountingSettings.CTP4VAT);
        final BigDecimal[] amounts = (BigDecimal[]) _createdDoc.getValue(ExternalVoucher_Base.AMOUNTSKEY);
        final BigDecimal netTotal = amounts[0];
        final BigDecimal crossTotal = amounts[1];
        final BigDecimal taxfree = amounts[2];

        insertPosition(_parameter, _createdDoc, vatProdInst, 1, netTotal, crossTotal);
        if (taxfree.compareTo(BigDecimal.ZERO) > 0) {
            final Instance freeProdInst = Accounting.getSysConfig().getLink(AccountingSettings.CTP4FREE);
            insertPosition(_parameter, _createdDoc, freeProdInst, 2, taxfree, taxfree);
        }
    }


    public Return create4PettyCashReceipt(final Parameter _parameter)
        throws EFapsException
    {
        final Instance docInst = Instance.get(_parameter.getParameterValue("document"));
        if (docInst.isValid()) {
            final SelectBuilder selContactOid = new SelectBuilder().linkto(CISales.PettyCashReceipt.Contact).oid();
            final SelectBuilder selActDefName = new SelectBuilder().clazz(CISales.PettyCashReceipt_Class)
                            .linkto(CIAccounting.PettyCashReceipt_Class.ActionDefinitionLink)
                            .attribute(CIAccounting.ActionDefinitionPettyCash.Name);
            final PrintQuery print = new PrintQuery(docInst);
            print.addAttribute(CISales.PettyCashReceipt.Date,
                            CISales.PettyCashReceipt.RateCurrencyId,
                            CISales.PettyCashReceipt.RateCrossTotal);
            print.addSelect(selContactOid, selActDefName);
            print.execute();
            _parameter.getParameters().put("currencyExternal",
                                            new String[] { print.<Long>getAttribute(
                                                            CISales.PettyCashReceipt.RateCurrencyId).toString() });
            _parameter.getParameters().put("rate_Credit", new String[] { "1", "1" });
            _parameter.getParameters().put("rate_Debit", new String[] { "1", "1" });
            _parameter.getParameters().put("extDate", new String[] {
                      print.<DateTime>getAttribute(CISales.PettyCashReceipt.Date).withTimeAtStartOfDay().toString() });
            _parameter.getParameters().put("extDueDate",
                            new String[] { print.<DateTime>getAttribute(CISales.PettyCashReceipt.Date)
                                            .withTimeAtStartOfDay().toString() });
            _parameter.getParameters().put("contact", new String[] { print.<String>getSelect(selContactOid) });
            _parameter.getParameters().put("amountExternal",
                            new String[] { print.<BigDecimal>getAttribute(CISales.PettyCashReceipt.RateCrossTotal)
                                            .toString() });
            _parameter.getParameters().put("note",
                            new String[] { print.<String>getSelect(selActDefName) });

            final CreatedDoc createdDoc = createDoc(_parameter);

            if (createdDoc.getInstance().isValid()) {
                final Insert insert = new Insert(CIAccounting.ExternalVoucher2Document);
                insert.add(CIAccounting.ExternalVoucher2Document.FromLink, createdDoc.getInstance().getId());
                insert.add(CIAccounting.ExternalVoucher2Document.ToLink, docInst.getId());
                insert.execute();
            }

            _parameter.put(ParameterValues.INSTANCE, createdDoc.getInstance());
            if (createdDoc.getInstance().isValid()) {
                _parameter.getParameters().remove("document");
            }
            new Create().create4External(_parameter);
            connect2DocumentType(_parameter, createdDoc);
        }
        return new Return();
    }

    @Override
    protected void connect2DocumentType(final Parameter _parameter,
                                        final CreatedDoc _createdDoc)
        throws EFapsException
    {
        final Long docTypeId = Long.parseLong(_parameter.getParameterValue(
                        CIFormAccounting.Accounting_TransactionCreate4ExternalVoucherForm.typeLink.name));
        if (docTypeId != null && _createdDoc.getInstance().isValid()) {
            final Insert insert = new Insert(CISales.Document2DocumentType);
            insert.add(CISales.Document2DocumentType.DocumentLink, _createdDoc.getInstance());
            insert.add(CISales.Document2DocumentType.DocumentTypeLink, docTypeId);
            insert.execute();
        }
    }
    /**
     * Get the Amounts for Net, Cross Total and TaxFree.
     *
     * @param _parameter Parameter as passed from the eFaps API
     * @return BigDecimal Array { NET, CROSS, TAXFREE}
     * @throws EFapsException on error
     */
    protected BigDecimal[] evalAmounts(final Parameter _parameter)
        throws EFapsException
    {
        final Instance caseInst = Instance.get(_parameter.getParameterValue(CIFormAccounting
                        .Accounting_TransactionCreate4ExternalVoucherForm.case_field.name));
        final PrintQuery print = new PrintQuery(caseInst);
        print.addAttribute(CIAccounting.CaseAbstract.IsCross);
        print.execute();
        final boolean isCross = print.<Boolean>getAttribute(CIAccounting.CaseAbstract.IsCross);
        return evalAmounts(_parameter, isCross);
    }

    /**
     * Get the Amounts for Net, Cross Total and TaxFree.
     *
     * @param _parameter Parameter as passed from the eFaps API
     * @param _isCross is the given amount the cross value or net value
     * @return BigDecimal Array { NET, CROSS, TAXFREE}
     * @throws EFapsException on error
     */
    protected BigDecimal[] evalAmounts(final Parameter _parameter,
                                       final boolean _isCross)
        throws EFapsException
    {
        final Instance periodeInst = (Instance) Context.getThreadContext().getSessionAttribute(
                        Transaction_Base.PERIODE_SESSIONKEY);

        final DecimalFormat formater = (DecimalFormat) NumberFormat.getInstance(Context.getThreadContext().getLocale());
        formater.setParseBigDecimal(true);

        BigDecimal cross = BigDecimal.ZERO;
        BigDecimal net = BigDecimal.ZERO;
        BigDecimal taxfree = BigDecimal.ZERO;
        try {
            final BigDecimal amount = (BigDecimal) formater.parse(_parameter.getParameterValue("amountExternal"));
            final String taxFreeStr = _parameter.getParameterValue("amountExternalWithoutTax");
            if (taxFreeStr != null && !taxFreeStr.isEmpty()) {
                taxfree =  (BigDecimal) formater.parse(taxFreeStr);
            }
            //Accounting-Configuration
            final SystemConfiguration config = Accounting.getSysConfig();
            if (config != null) {
                final Properties props = config.getObjectAttributeValueAsProperties(periodeInst);
                final String vatAcc = props.getProperty(AccountingSettings.PERIOD_EXVATACCOUNT);
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
                        if (_isCross) {
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
        return new BigDecimal[] { net, cross, taxfree };
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
        final SystemConfiguration sysconf = Accounting.getSysConfig();
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

                final Classification classification = (Classification) CIContacts.ClassSupplier.getType();
                final Insert relInsert1 = new Insert(classification.getClassifyRelationType());
                relInsert1.add(classification.getRelLinkAttributeName(), _contactInst.getId());
                relInsert1.add(classification.getRelTypeAttributeName(), classification.getId());
                relInsert1.execute();

                final Insert classInsert1 = new Insert(classification);
                classInsert1.add(classification.getLinkAttributeName(), _contactInst.getId());
                addClassInsert(_parameter, classInsert1);
                classInsert1.execute();

                final Classification classification2 = classification.getParentClassification();
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
}
