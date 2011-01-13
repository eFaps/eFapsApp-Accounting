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
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import org.efaps.admin.common.SystemConfiguration;
import org.efaps.admin.datamodel.Classification;
import org.efaps.admin.datamodel.Status;
import org.efaps.admin.dbproperty.DBProperties;
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
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.esjp.accounting.transaction.Create;
import org.efaps.esjp.accounting.transaction.Transaction;
import org.efaps.esjp.accounting.transaction.Transaction_Base;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.ci.CIContacts;
import org.efaps.esjp.ci.CIERP;
import org.efaps.esjp.erp.CurrencyInst;
import org.efaps.esjp.sales.document.AbstractDocument;
import org.efaps.ui.wicket.util.EFapsKey;
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

        final Instance contactInst = Instance.get(_parameter.getParameterValue("contact"));

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
        docInsert.add(CIAccounting.ExternalVoucher.Date, _parameter.getParameterValue("date"));
        docInsert.add(CIAccounting.ExternalVoucher.RateCrossTotal, amounts[1]);
        docInsert.add(CIAccounting.ExternalVoucher.RateNetTotal, amounts[0]);
        docInsert.add(CIAccounting.ExternalVoucher.RateDiscountTotal, BigDecimal.ZERO);
        docInsert.add(CIAccounting.ExternalVoucher.DiscountTotal, BigDecimal.ZERO);
        docInsert.add(CIAccounting.ExternalVoucher.CrossTotal, amounts[1].divide(rate, BigDecimal.ROUND_HALF_UP));
        docInsert.add(CIAccounting.ExternalVoucher.NetTotal,  amounts[0].divide(rate, BigDecimal.ROUND_HALF_UP));
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
     */
    public Return accessCheck4FieldPicker(final Parameter _parameter)
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
     * Executed on validate event to check the information for a new contact.
     *
     * @param _parameter Parameter as passed from the eFaps API
     * @return Return containing true if valid
     * @throws EFapsException on error
     */
    public Return validateContact(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final String name = _parameter.getParameterValue("name");
        final String taxNumber = _parameter.getParameterValue("taxNumber");
        final StringBuilder html4Name = validateName4Contact(name, taxNumber);
        StringBuilder html4TaxNumber = new StringBuilder();

        if (html4Name.length() == 0) {
            html4TaxNumber = validateTaxNumber4Contact(html4Name, taxNumber);
            if (html4TaxNumber.length() > 0) {
                ret.put(ReturnValues.SNIPLETT, html4TaxNumber.toString());
            } else {
                ret.put(ReturnValues.TRUE, true);
            }
        } else {
            html4TaxNumber = validateTaxNumber4Contact(html4Name, taxNumber);
            if (html4TaxNumber.length() > 0) {
                ret.put(ReturnValues.SNIPLETT, html4TaxNumber.toString());
            } else {
                ret.put(ReturnValues.SNIPLETT, html4Name.toString());
                ret.put(ReturnValues.TRUE, true);
            }
        }

        return ret;
    }

    /**
     * method for return the name of a contact.
     *
     * @param _name String
     * @param _taxnumber String
     * @return StringBuilder with html.
     * @throws EFapsException on error
     */
    public StringBuilder validateName4Contact(final String _name,
                                              final String _taxNumber)
        throws EFapsException
    {
        StringBuilder html = new StringBuilder();

        final QueryBuilder queryBldr = new QueryBuilder(CIContacts.Contact);
        queryBldr.addWhereAttrEqValue(CIContacts.Contact.Name, _name).setIgnoreCase(true);
        final MultiPrintQuery multi = queryBldr.getPrint();
        multi.addAttribute(CIContacts.Contact.OID);
        multi.execute();
        boolean check = true;
        while (multi.next()) {
            if (check) {
                html.append("<div style=\"text-align:center;\">")
                    .append(DBProperties.getProperty("org.efaps.esjp.Accounting.existingContact"));
                check = false;
            }
        }
        html = (html.length() > 0 ? html.append("</div>") : new StringBuilder());

        return html;
    }

    /**
     * method for return the taxNumber of a contact.
     *
     * @param _name StringBuilder
     * @param _taxnumber String
     * @return StringBuilder with html.
     * @throws EFapsException on error.
     */
    public StringBuilder validateTaxNumber4Contact(final StringBuilder _name,
                                                   final String _taxNumber)
        throws EFapsException
    {
        StringBuilder html = new StringBuilder();

        if (_name.length() == 0) {
            if (_taxNumber != null) {
                html = queryTaxNumber4Contact(html, _taxNumber);
            }
        } else {
            if (_taxNumber != null) {
                html = queryTaxNumber4Contact(html, _taxNumber);
            }
        }

        return html;
    }

    /**
     * Method for search the taxNumber of the Contact if exists
     * return SNIPLETT.
     *
     * @param _html StringBuilder.
     * @param _taxNumber TaxNumber.
     * @return html StringBuilder.
     * @throws EFapsException on error.
     */
    private StringBuilder queryTaxNumber4Contact(final StringBuilder _html,
                                                 final String _taxNumber)
        throws EFapsException
    {
        StringBuilder html = new StringBuilder();

        final QueryBuilder queryBldr = new QueryBuilder(CIContacts.ClassOrganisation);
        queryBldr.addWhereAttrEqValue(CIContacts.ClassOrganisation.TaxNumber, _taxNumber);
        final MultiPrintQuery multi = queryBldr.getPrint();
        final SelectBuilder selOID = new SelectBuilder().linkto(CIContacts.ClassOrganisation.ContactId)
                                                                    .attribute(CIContacts.Contact.OID);
        multi.addSelect(selOID);
        multi.execute();
        boolean check = true;
        while (multi.next()) {
            if (check) {
                _html.append("<div style=\"text-align:left;\">")
                     .append(DBProperties.getProperty("org.efaps.esjp.Accounting.existingTaxNumber"));
                check = false;
            }
        }
        html = (_html.length() > 0 ? _html.append("</div>") : new StringBuilder());

        return html;
    }

    /**
     * Executed on picker event. Creates a new contact and returns it
     * as map to the calling form picker.
     * @param _parameter    Parameter as passed from the eFaps API
     * @return map for the picker to fill the form
     * @throws EFapsException on error
     */
    public Return picker4NewContact(final Parameter _parameter)
        throws EFapsException
    {
        final Return retVal = new Return();
        final Map<String, String> map = new HashMap<String, String>();
        retVal.put(ReturnValues.VALUES, map);
        final String name = _parameter.getParameterValue("name");
        final Insert insert = new Insert(CIContacts.Contact);
        insert.add(CIContacts.Contact.Name, name);
        insert.execute();

        final Instance contactInst = insert.getInstance();

        if (contactInst != null && contactInst.isValid()) {
            // create classifications
            final Classification classification = (Classification) CIContacts.ClassOrganisation.getType();
            final Insert relInsert1 = new Insert(classification.getClassifyRelationType());
            relInsert1.add(classification.getRelLinkAttributeName(), contactInst.getId());
            relInsert1.add(classification.getRelTypeAttributeName(), classification.getId());
            relInsert1.execute();

            final Insert classInsert1 = new Insert(classification);
            classInsert1.add(classification.getLinkAttributeName(), contactInst.getId());
            classInsert1.add(CIContacts.ClassOrganisation.TaxNumber, _parameter.getParameterValue("taxNumber"));
            classInsert1.execute();

            final Classification classification2 = (Classification) classification.getParentClassification();
            final Insert relInsert2 = new Insert(classification2.getClassifyRelationType());
            relInsert2.add(classification.getRelLinkAttributeName(), contactInst.getId());
            relInsert2.add(classification.getRelTypeAttributeName(), classification2.getId());
            relInsert2.execute();

            final Insert classInsert2 = new Insert(classification2);
            classInsert2.add(classification.getLinkAttributeName(), contactInst.getId());
            classInsert2.execute();

            map.put(EFapsKey.PICKER_VALUE.getKey(), name);
            map.put("contact", contactInst.getOid());
            map.put("contactData", getFieldValue4Contact(contactInst));
        }
        return retVal;
    }
}
