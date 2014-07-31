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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import org.efaps.admin.common.NumberGenerator;
import org.efaps.admin.common.SystemConfiguration;
import org.efaps.admin.datamodel.Classification;
import org.efaps.admin.datamodel.Dimension;
import org.efaps.admin.datamodel.Type;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Parameter.ParameterValues;
import org.efaps.admin.event.Return;
import org.efaps.admin.event.Return.ReturnValues;
import org.efaps.admin.program.esjp.EFapsRevision;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.admin.ui.AbstractUserInterfaceObject.TargetMode;
import org.efaps.db.AttributeQuery;
import org.efaps.db.Context;
import org.efaps.db.Insert;
import org.efaps.db.Instance;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.PrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.esjp.accounting.transaction.Create;
import org.efaps.esjp.accounting.transaction.Transaction;
import org.efaps.esjp.accounting.util.Accounting;
import org.efaps.esjp.accounting.util.AccountingSettings;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.ci.CIContacts;
import org.efaps.esjp.ci.CIERP;
import org.efaps.esjp.ci.CIFormAccounting;
import org.efaps.esjp.ci.CIProducts;
import org.efaps.esjp.ci.CISales;
import org.efaps.esjp.common.parameter.ParameterUtil;
import org.efaps.esjp.contacts.ContactsPicker;
import org.efaps.esjp.erp.NumberFormatter;
import org.efaps.esjp.sales.Calculator;
import org.efaps.esjp.sales.document.AbstractDocumentSum;
import org.efaps.esjp.sales.document.AbstractDocumentTax;
import org.efaps.esjp.sales.document.AbstractDocument_Base;
import org.efaps.esjp.sales.document.IncomingInvoice;
import org.efaps.esjp.sales.document.IncomingInvoice_Base;
import org.efaps.esjp.sales.tax.Tax;
import org.efaps.esjp.sales.tax.TaxCat;
import org.efaps.esjp.sales.util.Sales;
import org.efaps.esjp.sales.util.SalesSettings;
import org.efaps.ui.wicket.util.DateUtil;
import org.efaps.util.EFapsException;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 * @version $Id$
 */
@EFapsUUID("204d6d77-a034-455e-9867-afcada575c09")
@EFapsRevision("$Rev$")
public abstract class ExternalVoucher_Base
    extends AbstractDocumentSum
{
    /**
     * Logging instance used in this class.
     */
    private static final Logger LOG = LoggerFactory.getLogger(ExternalVoucher.class);

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
        ParameterUtil.setParmeterValue(_parameter, "document", createdDoc.getInstance().getOid());
        return  new Create().create4External(_parameter);
    }

    /**
     * @param _parameter Parameter as passed from the eFaps API
     * @return new empty Return
     * @throws EFapsException on error
     */
    public Return edit(final Parameter _parameter)
        throws EFapsException
    {
        final EditedDoc editDoc = editDoc(_parameter);
        updatePositions(_parameter, editDoc);
        return new Return();
    }

    @Override
    protected CreatedDoc createDoc(final Parameter _parameter)
        throws EFapsException
    {
        final CreatedDoc createdDoc = new CreatedDoc();
        //create a list of calculators
        final List<Calculator> calcList = analyseAmountsFromUI(_parameter);
        createdDoc.addValue(AbstractDocument_Base.CALCULATORS_VALUE, calcList);

        final Instance baseCurrInst = Sales.getSysConfig().getLink(SalesSettings.CURRENCYBASE);

        Instance contactInst = Instance.get(_parameter.getParameterValue("contact"));
        if (!contactInst.isValid()) {
            contactInst = Instance.get(_parameter.getParameterValue("contactPicker"));
        }

        final Object[] rateObj = getRateObject(_parameter);
        final BigDecimal rate = ((BigDecimal) rateObj[0]).divide((BigDecimal) rateObj[1], 12,
                        BigDecimal.ROUND_HALF_UP);
        final Instance rateCurrInst = Instance.get(CIERP.Currency.getType(),
                        _parameter.getParameterValue("currencyExternal"));

        final Insert docInsert = new Insert(getType4DocCreate(_parameter));
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

        final DecimalFormat frmt = NumberFormatter.get().getFrmt4Total(getTypeName4SysConf(_parameter));
        final int scale = frmt.getMaximumFractionDigits();
        final BigDecimal rateCrossTotal = getCrossTotal(_parameter, calcList)
                        .setScale(scale, BigDecimal.ROUND_HALF_UP);
        docInsert.add(CISales.DocumentSumAbstract.RateCrossTotal, rateCrossTotal);
        createdDoc.getValues().put(CISales.DocumentSumAbstract.RateCrossTotal.name, rateCrossTotal);

        final BigDecimal rateNetTotal = getNetTotal(_parameter, calcList).setScale(scale, BigDecimal.ROUND_HALF_UP);
        docInsert.add(CISales.DocumentSumAbstract.RateNetTotal, rateNetTotal);
        createdDoc.getValues().put(CISales.DocumentSumAbstract.RateNetTotal.name, rateNetTotal);

        docInsert.add(CISales.DocumentSumAbstract.RateDiscountTotal, BigDecimal.ZERO);
        docInsert.add(CISales.DocumentSumAbstract.RateTaxes, getRateTaxes(_parameter, calcList, rateCurrInst));
        docInsert.add(CISales.DocumentSumAbstract.Taxes, getTaxes(_parameter, calcList, rate, baseCurrInst));

        final BigDecimal crossTotal = getCrossTotal(_parameter, calcList).divide(rate, BigDecimal.ROUND_HALF_UP)
                        .setScale(scale, BigDecimal.ROUND_HALF_UP);
        docInsert.add(CISales.DocumentSumAbstract.CrossTotal, crossTotal);
        createdDoc.getValues().put(CISales.DocumentSumAbstract.CrossTotal.name, crossTotal);

        final BigDecimal netTotal = getNetTotal(_parameter, calcList).divide(rate, BigDecimal.ROUND_HALF_UP)
                        .setScale(scale, BigDecimal.ROUND_HALF_UP);
        docInsert.add(CISales.DocumentSumAbstract.NetTotal, netTotal);
        createdDoc.getValues().put(CISales.DocumentSumAbstract.CrossTotal.name, netTotal);

        docInsert.add(CISales.DocumentSumAbstract.DiscountTotal, BigDecimal.ZERO);

        docInsert.add(CISales.DocumentSumAbstract.CurrencyId, baseCurrInst);
        docInsert.add(CISales.DocumentSumAbstract.Rate, rateObj);
        docInsert.add(CISales.DocumentSumAbstract.RateCurrencyId, rateCurrInst);

        createdDoc.getValues().put(CISales.DocumentSumAbstract.CurrencyId.name, baseCurrInst);
        createdDoc.getValues().put(CISales.DocumentSumAbstract.RateCurrencyId.name, rateCurrInst);
        createdDoc.getValues().put(CISales.DocumentSumAbstract.Rate.name, rateObj);

        docInsert.add(CIAccounting.ExternalVoucher.Salesperson, Context.getThreadContext().getPersonId());
        createdDoc.addValue(getFieldName4Attribute(_parameter, CIAccounting.ExternalVoucher.Salesperson.name),
                        Context.getThreadContext().getPersonId());
        addStatus2DocCreate(_parameter, docInsert, createdDoc);
        add2DocCreate(_parameter, docInsert, createdDoc);
        docInsert.execute();

        createdDoc.setInstance(docInsert.getInstance());
        return createdDoc;
    }

    @Override
    protected Object[] getRateObject(final Parameter _parameter)
        throws EFapsException
    {
        Object[] ret;
        if (TargetMode.EDIT.equals(_parameter.get(ParameterValues.ACCESSMODE))) {
            ret = super.getRateObject(_parameter);
        } else {
            ret = new Transaction().getRateObject(_parameter, "_Debit", 0);
        }
        return ret;
    }

    @Override
    protected Instance getRateCurrencyInstance(final Parameter _parameter,
                                               final CreatedDoc _createdDoc)
        throws EFapsException
    {
        Instance ret;
        if (TargetMode.EDIT.equals(_parameter.get(ParameterValues.ACCESSMODE))) {
            ret = super.getRateCurrencyInstance(_parameter, _createdDoc);
        } else {
            ret = Instance.get(CIERP.Currency.getType(), _parameter.getParameterValue(
                            CIFormAccounting.Accounting_TransactionCreate4ExternalVoucherForm.currencyExternal.name));
        }
        return ret;
    }

    @Override
    protected void add2PositionInsert(final Parameter _parameter,
                                      final Calculator _calc,
                                      final Insert _posIns,
                                      final int _idx)
        throws EFapsException
    {
        final Instance prodInst = Instance.get(_calc.getOid());

        _posIns.add(CISales.PositionAbstract.Product, prodInst);
        final String productDesc = _parameter.getParameterValue(
                        CIFormAccounting.Accounting_TransactionCreate4ExternalVoucherForm.description.name);
        _posIns.add(CISales.PositionAbstract.ProductDesc, productDesc);

        final PrintQuery print = new PrintQuery(prodInst);
        print.addAttribute(CIProducts.ProductAbstract.Dimension);
        print.executeWithoutAccessCheck();
        final Long dimId = print.<Long>getAttribute(CIProducts.ProductAbstract.Dimension);
        _posIns.add(CISales.PositionAbstract.UoM, Dimension.get(dimId).getBaseUoM());

    }

    @Override
    protected void add2DocCreate(final Parameter _parameter,
                                 final Insert _insert,
                                 final CreatedDoc _createdDoc)
        throws EFapsException
    {
        final SystemConfiguration config = Sales.getSysConfig();
        final Properties props = config.getAttributeValueAsProperties(SalesSettings.INCOMINGINVOICESEQUENCE);

        final NumberGenerator numgen = NumberGenerator.get(UUID.fromString(props.getProperty("UUID")));
        if (numgen != null) {
            final String revision = numgen.getNextVal();
            Context.getThreadContext().setSessionAttribute(IncomingInvoice_Base.REVISIONKEY, revision);
            _insert.add(CIAccounting.ExternalVoucher.Revision, revision);
            _createdDoc.addValue(getFieldName4Attribute(_parameter, CIAccounting.ExternalVoucher.Revision.name),
                            revision);
        }
    }

    @Override
    protected Type getType4PositionCreate(final Parameter _parameter)
        throws EFapsException
    {
        return CIAccounting.ExternalVoucherPosition.getType();
    }

    @Override
    protected Type getType4DocCreate(final Parameter _parameter)
        throws EFapsException
    {
        return CIAccounting.ExternalVoucher.getType();
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
     * @param _parameter Parameter as passed from the eFaps API
     * @return List of Calcualtors
     * @throws EFapsException on error
     */
    protected List<Calculator> analyseAmountsFromUI(final Parameter _parameter)
        throws EFapsException
    {
        final List<Calculator> ret = new ArrayList<Calculator>();
        final BigDecimal[] amounts = evalAmountsFromUI(_parameter);
        final BigDecimal net = amounts[0];
        final BigDecimal cross = amounts[1];
        BigDecimal taxfree = amounts[2];
        final DecimalFormat unitFrmt = NumberFormatter.get().getFrmt4UnitPrice(getTypeName4SysConf(_parameter));
        final boolean prodPriceIsNet = Sales.getSysConfig().getAttributeValueAsBoolean(SalesSettings.PRODPRICENET);

        if (net.compareTo(cross) == 0) {
            taxfree = taxfree.add(net);
        } else {
            final Instance vatProdInst = Accounting.getSysConfig().getLink(AccountingSettings.CTP4VAT);
            final Calculator cals = getCalculator(_parameter, null, vatProdInst.getOid(), "1",
                                prodPriceIsNet ? unitFrmt.format(net) : unitFrmt.format(cross), "0", false, 0);
            ret.add(cals);
        }

        if (taxfree.compareTo(BigDecimal.ZERO) > 0) {
            final Instance freeProdInst = Accounting.getSysConfig().getLink(AccountingSettings.CTP4FREE);
            final Calculator calsTmp = getCalculator(_parameter, null, freeProdInst.getOid(), "1",
                            unitFrmt.format(taxfree), "0", false, 0);
            ret.add(calsTmp);
        }
        return ret;
    }

    /**
     * Get the Amounts for Net, Cross Total and TaxFree.
     *
     * @param _parameter Parameter as passed from the eFaps API
     * @return BigDecimal Array { NET, CROSS, TAXFREE}
     * @throws EFapsException on error
     */
    protected BigDecimal[] evalAmountsFromUI(final Parameter _parameter)
        throws EFapsException
    {
        final Instance caseInst = Instance.get(_parameter.getParameterValue(CIFormAccounting
                        .Accounting_TransactionCreate4ExternalVoucherForm.case_field.name));
        final PrintQuery print = new PrintQuery(caseInst);
        print.addAttribute(CIAccounting.CaseAbstract.IsCross);
        print.execute();
        final boolean isCross = print.<Boolean>getAttribute(CIAccounting.CaseAbstract.IsCross);
        return evalAmountsFromUI(_parameter, isCross);
    }

    /**
     * Get the Amounts for Net, Cross Total and TaxFree.
     *
     * @param _parameter Parameter as passed from the eFaps API
     * @param _isCross is the given amount the cross value or net value
     * @return BigDecimal Array { NET, CROSS, TAXFREE}
     * @throws EFapsException on error
     */
    protected BigDecimal[] evalAmountsFromUI(final Parameter _parameter,
                                             final boolean _isCross)
        throws EFapsException
    {
        final Instance periodInst = new Period().evaluateCurrentPeriod(_parameter);

        final DecimalFormat formater = (DecimalFormat) NumberFormat.getInstance(Context.getThreadContext().getLocale());
        formater.setParseBigDecimal(true);

        BigDecimal cross = BigDecimal.ZERO;
        BigDecimal net = BigDecimal.ZERO;
        BigDecimal taxfree = BigDecimal.ZERO;
        try {
            final BigDecimal amount = (BigDecimal) formater.parse(_parameter.getParameterValue(
                            CIFormAccounting.Accounting_TransactionCreate4ExternalVoucherForm.amountExternal.name));
            final String taxFreeStr = _parameter.getParameterValue(
                       CIFormAccounting.Accounting_TransactionCreate4ExternalVoucherForm.amountExternalWithoutTax.name);
            if (taxFreeStr != null && !taxFreeStr.isEmpty()) {
                taxfree =  (BigDecimal) formater.parse(taxFreeStr);
            }
            final Instance vatProdInst = Accounting.getSysConfig().getLink(AccountingSettings.CTP4VAT);

            final PrintQuery print = new PrintQuery(vatProdInst);
            print.addAttribute(CISales.ProductAbstract.TaxCategory);
            print.execute();
            final Long taxCatId = print.<Long>getAttribute(CISales.ProductAbstract.TaxCategory);
            final TaxCat taxcat = TaxCat.get(taxCatId);
            final String dateStr = _parameter.getParameterValue(
                            CIFormAccounting.Accounting_TransactionCreate4ExternalVoucherForm.extDate.name
                            + "_eFapsDate");
            final Collection<? extends Tax> taxes = taxcat.getTaxes(dateStr != null && !dateStr.isEmpty() ? DateUtil
                            .getDateFromParameter(dateStr) : new DateTime());

            if (!taxes.isEmpty()) {
                final QueryBuilder attrQueryBldr = new QueryBuilder(CIAccounting.AccountAbstract);
                attrQueryBldr.addWhereAttrEqValue(CIAccounting.AccountAbstract.PeriodAbstractLink, periodInst);
                final AttributeQuery attrQuery = attrQueryBldr.getAttributeQuery(CIAccounting.AccountAbstract.ID);
                final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.AccountBalanceSheetLiability2Tax);
                queryBldr.addWhereAttrEqValue(CIAccounting.AccountBalanceSheetLiability2Tax.ToTaxLink,
                                taxes.iterator().next().getInstance());
                queryBldr.addWhereAttrInQuery(CIAccounting.AccountBalanceSheetLiability2Tax.FromAccountLink, attrQuery);
                final MultiPrintQuery multi = queryBldr.getPrint();
                final SelectBuilder sel = SelectBuilder.get()
                                .linkto(CIAccounting.AccountBalanceSheetLiability2Tax.FromAccountLink).instance();
                multi.addSelect(sel);
                if (multi.execute()) {
                    while (multi.next()) {
                        final Instance accInst = multi.<Instance>getSelect(sel);
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
                    }
                } else {
                    cross = amount;
                    net = amount;
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
     * @param _parameter Parameter as passed from the eFaps API
     * @return Return new Return
     * @throws EFapsException on error
     */
    public Return getDocTaxInfoFieldValueUI(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        @SuppressWarnings("unchecked")
        final List<Instance> instances = (List<Instance>) _parameter.get(ParameterValues.REQUEST_INSTANCES);
        AbstractDocumentTax.evaluateDocTaxInfo(_parameter, instances);
        final StringBuilder html = AbstractDocumentTax.getSmallTaxField4Doc(_parameter, _parameter.getInstance());
        ret.put(ReturnValues.SNIPLETT, html.toString());
        return ret;
    }

    /**
     * @param _parameter Parameter as passed from the eFaps API
     * @return Return new Return
     * @throws EFapsException on error
     */
    public Return updateFields4PerceptionPercent(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final List<Map<String, Object>> list = new ArrayList<>();
        final Map<String, Object> map = new HashMap<>();
        list.add(map);
        final String percentStr = _parameter.getParameterValue(
                        CIFormAccounting.Accounting_TransactionCreate4ExternalVoucherForm.perceptionPercent.name);
        if (percentStr != null && !percentStr.isEmpty()) {
            final BigDecimal cross = evalAmountsFromUI(_parameter)[1];
            if (cross.compareTo(BigDecimal.ZERO) != 0) {
                try {
                    final DecimalFormat formatter = NumberFormatter.get().getFormatter();
                    final BigDecimal percent = (BigDecimal) formatter.parse(percentStr);
                    final BigDecimal amount = cross.multiply(percent
                                    .setScale(8, BigDecimal.ROUND_HALF_UP)
                                    .divide(new BigDecimal(100), BigDecimal.ROUND_HALF_UP));
                    final String amountStr = NumberFormatter.get().getFrmt4Total(getTypeName4SysConf(_parameter))
                                    .format(amount);
                    map.put(CIFormAccounting.Accounting_TransactionCreate4ExternalVoucherForm.perceptionValue.name,
                                    amountStr);
                } catch (final ParseException e) {
                    ExternalVoucher_Base.LOG.error("Catched ParseException", e);
                }
            }
        }
        ret.put(ReturnValues.VALUES, list);
        return ret;
    }

    /**
     * @param _parameter Parameter as passed from the eFaps API
     * @return Return new Return
     * @throws EFapsException on error
     */
    public Return updateFields4RetentionPercent(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final List<Map<String, Object>> list = new ArrayList<>();
        final Map<String, Object> map = new HashMap<>();
        list.add(map);
        final String percentStr = _parameter.getParameterValue(
                        CIFormAccounting.Accounting_TransactionCreate4ExternalVoucherForm.retentionPercent.name);
        if (percentStr != null && !percentStr.isEmpty()) {
            final BigDecimal cross = evalAmountsFromUI(_parameter)[1];
            if (cross.compareTo(BigDecimal.ZERO) != 0) {
                try {
                    final DecimalFormat formatter = NumberFormatter.get().getFormatter();
                    final BigDecimal percent = (BigDecimal) formatter.parse(percentStr);
                    final BigDecimal amount = cross.multiply(percent
                                    .setScale(8, BigDecimal.ROUND_HALF_UP)
                                    .divide(new BigDecimal(100), BigDecimal.ROUND_HALF_UP));
                    final String amountStr = NumberFormatter.get().getFrmt4Total(getTypeName4SysConf(_parameter))
                                    .format(amount);
                    map.put(CIFormAccounting.Accounting_TransactionCreate4ExternalVoucherForm.retentionValue.name,
                                    amountStr);
                } catch (final ParseException e) {
                    ExternalVoucher_Base.LOG.error("Catched ParseException", e);
                }
            }
        }
        ret.put(ReturnValues.VALUES, list);
        return ret;
    }

    /**
     * @param _parameter Parameter as passed from the eFaps API
     * @return Return new Return
     * @throws EFapsException on error
     */
    public Return updateFields4DetractionPercent(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final List<Map<String, Object>> list = new ArrayList<>();
        final Map<String, Object> map = new HashMap<>();
        list.add(map);
        final String percentStr = _parameter.getParameterValue(
                        CIFormAccounting.Accounting_TransactionCreate4ExternalVoucherForm.detractionPercent.name);
        if (percentStr != null && !percentStr.isEmpty()) {
            final BigDecimal cross = evalAmountsFromUI(_parameter)[1];
            if (cross.compareTo(BigDecimal.ZERO) != 0) {
                try {
                    final DecimalFormat formatter = NumberFormatter.get().getFormatter();
                    final BigDecimal percent = (BigDecimal) formatter.parse(percentStr);
                    final BigDecimal amount = cross.multiply(percent
                                    .setScale(8, BigDecimal.ROUND_HALF_UP)
                                    .divide(new BigDecimal(100), BigDecimal.ROUND_HALF_UP));
                    final String amountStr = NumberFormatter.get().getFrmt4Total(getTypeName4SysConf(_parameter))
                                    .format(amount);
                    map.put(CIFormAccounting.Accounting_TransactionCreate4ExternalVoucherForm.detractionValue.name,
                                    amountStr);
                } catch (final ParseException e) {
                    ExternalVoucher_Base.LOG.error("Catched ParseException", e);
                }
            }
        }
        ret.put(ReturnValues.VALUES, list);
        return ret;
    }

    /**
     * @param _parameter Parameter as passed from the eFaps API
     * @return Return new Return
     * @throws EFapsException on error
     */
    public Return getJavaScript4TaxDocUIValue(final Parameter _parameter)
        throws EFapsException
    {
        return new IncomingInvoice().getJavaScript4TaxDocUIValue(_parameter);
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
           }
        };
        return contactsPicker.picker4NewContact(_parameter);
    }
}
