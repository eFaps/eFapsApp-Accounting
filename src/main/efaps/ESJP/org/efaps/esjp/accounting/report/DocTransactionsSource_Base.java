/*
 * Copyright 2003 - 2012 The eFaps Team
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


package org.efaps.esjp.accounting.report;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.collections.comparators.ComparatorChain;
import org.efaps.admin.common.SystemConfiguration;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.AttributeQuery;
import org.efaps.db.Instance;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.ci.CIContacts;
import org.efaps.esjp.ci.CIERP;
import org.efaps.esjp.ci.CIFormAccounting;
import org.efaps.esjp.common.jasperreport.EFapsMapDataSource;
import org.efaps.esjp.erp.util.ERP;
import org.efaps.util.EFapsException;
import org.joda.time.DateTime;

import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JasperReport;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 * @version $Id$
 */
@EFapsUUID("412cf393-f815-4029-b1e8-d2c42a98383b")
@EFapsApplication("eFapsApp-Accounting")
public abstract class DocTransactionsSource_Base
    extends EFapsMapDataSource
{
    /**
     * @see org.efaps.esjp.common.jasperreport.EFapsDataSource_Base#init(net.sf.jasperreports.engine.JasperReport)
     * @param _jasperReport  JasperReport
     * @param _parameter Parameter
     * @param _parentSource JRDataSource
     * @param _jrParameters map that contains the report parameters
     * @throws EFapsException on error
     */
    @SuppressWarnings("unchecked")
    @Override
    public void init(final JasperReport _jasperReport,
                     final Parameter _parameter,
                     final JRDataSource _parentSource,
                     final Map<String, Object> _jrParameters)
        throws EFapsException
    {
        final Instance instance = _parameter.getInstance();

        final DateTime dateFrom = new DateTime(
                        _parameter.getParameterValue(CIFormAccounting.Accounting_DocTransactionsForm.dateFrom.name));
        final DateTime dateTo = new DateTime(
                        _parameter.getParameterValue(CIFormAccounting.Accounting_DocTransactionsForm.dateTo.name));
        _jrParameters.put("FromDate", dateFrom.toDate());
        _jrParameters.put("ToDate", dateTo.toDate());
        _jrParameters.put("Mime", _parameter.getParameterValue("mime"));

        final boolean filter = "true".equalsIgnoreCase(_parameter
                        .getParameterValue(CIFormAccounting.Accounting_DocTransactionsForm.filterActive.name));

        final Instance contactInst = Instance.get(_parameter
                        .getParameterValue(CIFormAccounting.Accounting_DocTransactionsForm.contact.name));

        final String[] creditAccount = _parameter
                        .getParameterValues(CIFormAccounting.Accounting_DocTransactionsForm.creditAccount.name);
        final String[] debitAccount = _parameter
                          .getParameterValues(CIFormAccounting.Accounting_DocTransactionsForm.debitAccount.name);

        // filter the documents by the given dates
        final QueryBuilder docAttrQueryBldr = new QueryBuilder(CIERP.DocumentAbstract);
        docAttrQueryBldr.addWhereAttrLessValue(CIERP.DocumentAbstract.Date, dateTo.plusDays(1));
        docAttrQueryBldr.addWhereAttrGreaterValue(CIERP.DocumentAbstract.Date, dateFrom.minusSeconds(1));
        if (filter && contactInst.isValid() && contactInst.getType().isKindOf(CIContacts.Contact.getType())) {
            docAttrQueryBldr.addWhereAttrEqValue(CIERP.DocumentAbstract.Contact, contactInst.getId());
        }

        final AttributeQuery docAttrQuery = docAttrQueryBldr.getAttributeQuery(CIERP.DocumentAbstract.ID);


        final QueryBuilder attrQuerBldr = new QueryBuilder(CIAccounting.Transaction2SalesDocument);
        attrQuerBldr.addWhereAttrInQuery(CIAccounting.Transaction2SalesDocument.ToLink, docAttrQuery);
        final AttributeQuery attrQuery = attrQuerBldr
                        .getAttributeQuery(CIAccounting.Transaction2SalesDocument.FromLink);

        final QueryBuilder queryBuilder = new QueryBuilder(CIAccounting.Transaction);
        queryBuilder.addWhereAttrInQuery(CIAccounting.Transaction.ID, attrQuery);
        queryBuilder.addWhereAttrEqValue(CIAccounting.Transaction.PeriodLink, instance.getId());

        final MultiPrintQuery multi = queryBuilder.getPrint();
        final SelectBuilder selDoc = new SelectBuilder()
            .linkfrom(CIAccounting.Transaction2SalesDocument, CIAccounting.Transaction2SalesDocument.FromLink)
                        .linkto(CIAccounting.Transaction2SalesDocument.ToLink);
        final SelectBuilder selDocName = new SelectBuilder(selDoc).attribute(CIERP.DocumentAbstract.Name);
        final SelectBuilder selDocDate = new SelectBuilder(selDoc).attribute(CIERP.DocumentAbstract.Date);
        final SelectBuilder selDocType = new SelectBuilder(selDoc).type().label();
        final SelectBuilder selDocContactName = new SelectBuilder(selDoc).linkto(CIERP.DocumentAbstract.Contact)
                        .attribute(CIContacts.Contact.Name);
        final SelectBuilder selDocContactTaxNumber = new SelectBuilder(selDoc).linkto(CIERP.DocumentAbstract.Contact)
                        .clazz(CIContacts.ClassOrganisation).attribute(CIContacts.ClassOrganisation.TaxNumber);
        multi.addSelect(selDocName, selDocContactName, selDocContactTaxNumber, selDocType, selDocDate);
        multi.addAttribute(CIAccounting.Transaction.Date, CIAccounting.Transaction.Description,
                           CIAccounting.Transaction.Name);
        multi.execute();
        while (multi.next()) {
            final String docType = multi.<String>getSelect(selDocType);
            final String docName = multi.<String>getSelect(selDocName);
            final DateTime docDate = multi.<DateTime>getSelect(selDocDate);
            final String contactName = multi.<String>getSelect(selDocContactName);
            final String taxNumber = multi.<String>getSelect(selDocContactTaxNumber);
            final DateTime transDate = multi.<DateTime>getAttribute(CIAccounting.Transaction.Date);
            final String transDescr = multi.<String>getAttribute(CIAccounting.Transaction.Description);
            final String transOID = multi.getCurrentInstance().getOid();
            final String transName = multi.<String>getAttribute(CIAccounting.Transaction.Name);

            final QueryBuilder posQueryBldr = new QueryBuilder(CIAccounting.TransactionPositionAbstract);
            posQueryBldr.addWhereAttrEqValue(CIAccounting.TransactionPositionAbstract.TransactionLink, multi
                            .getCurrentInstance().getId());
            final MultiPrintQuery posMulti = posQueryBldr.getPrint();
            final SelectBuilder selAcc = new SelectBuilder()
                            .linkto(CIAccounting.TransactionPositionAbstract.AccountLink);
            final SelectBuilder selAccName = new SelectBuilder(selAcc).attribute(CIAccounting.AccountAbstract.Name);
            final SelectBuilder selAccDesc = new SelectBuilder(selAcc)
                            .attribute(CIAccounting.AccountAbstract.Description);
            posMulti.addSelect(selAccName, selAccDesc);
            posMulti.addAttribute(CIAccounting.TransactionPositionAbstract.Amount);
            posMulti.execute();
            while (posMulti.next()) {
                final String accName = posMulti.<String>getSelect(selAccName);
                if (!filter || (filter && add(accName,posMulti.getCurrentInstance().getType()
                                                                .isKindOf(CIAccounting.TransactionPositionCredit.getType())
                                                                    ? creditAccount
                                                                    : debitAccount))) {
                    final Map<String, Object> row = new HashMap<>();
                    row.put("docName", docName);
                    row.put("docType", docType);
                    row.put("docDate", docDate);
                    row.put("contactName", contactName);
                    row.put("taxNumber", taxNumber);
                    row.put("transOID", transOID);
                    row.put("transDate", transDate);
                    row.put("transName", transName);
                    row.put("transDescr", transDescr);
                    row.put("amount", posMulti.getAttribute(CIAccounting.TransactionPositionAbstract.Amount));
                    row.put("accName", accName);
                    row.put("accDescr", posMulti.getSelect(selAccDesc));
                    getValues().add(row);
                }
            }
        }
        final ComparatorChain chain = new ComparatorChain();
        chain.addComparator(new Comparator<Map<String, Object>>(){

            @Override
            public int compare(final Map<String, Object> _arg0,
                               final Map<String, Object> _arg1)
            {
                return String.valueOf(_arg0.get("taxNumber")).compareTo(String.valueOf(_arg1.get("taxNumber")));
            }});
        chain.addComparator(new Comparator<Map<String, Object>>(){

            @Override
            public int compare(final Map<String, Object> _arg0,
                               final Map<String, Object> _arg1)
            {
                return String.valueOf(_arg0.get("docName")).compareTo(String.valueOf(_arg1.get("docName")));
            }});
        chain.addComparator(new Comparator<Map<String, Object>>(){

            @Override
            public int compare(final Map<String, Object> _arg0,
                               final Map<String, Object> _arg1)
            {
                return  ((DateTime) _arg0.get("transDate")).compareTo((DateTime) _arg1.get("transDate"));
            }});
        chain.addComparator(new Comparator<Map<String, Object>>(){

            @Override
            public int compare(final Map<String, Object> _arg0,
                               final Map<String, Object> _arg1)
            {
                return String.valueOf(_arg0.get("transOID")).compareTo(String.valueOf(_arg1.get("transOID")));
            }});
        chain.addComparator(new Comparator<Map<String, Object>>(){

            @Override
            public int compare(final Map<String, Object> _arg0,
                               final Map<String, Object> _arg1)
            {
                return String.valueOf(_arg0.get("accName")).compareTo(String.valueOf(_arg1.get("accName")));
            }});
        Collections.sort(getValues(), chain);

        final SystemConfiguration config = ERP.getSysConfig();
        if (config != null) {
            final String companyName = ERP.COMPANYNAME.get();
            final String companyTaxNumb = ERP.COMPANYTAX.get();

            if (companyName != null && companyTaxNumb != null && !companyName.isEmpty() && !companyTaxNumb.isEmpty()) {
                _jrParameters.put("CompanyName", companyName);
                _jrParameters.put("CompanyTaxNum", companyTaxNumb);
            }
        }
    }

    protected boolean add(final String _accName,
                          final String[] _filters) {
        boolean ret = _filters == null || _filters.length == 0 ? true : false;
        if (!ret) {
            for (final String filter : _filters) {
                ret = _accName.matches(filter.replace("*", ".*"));
                if (ret) {
                    break;
                }
            }
        }
        return ret;
    }
}
