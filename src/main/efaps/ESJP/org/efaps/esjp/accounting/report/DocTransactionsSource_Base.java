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

import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JasperReport;

import org.apache.commons.collections.comparators.ComparatorChain;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.program.esjp.EFapsRevision;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.AttributeQuery;
import org.efaps.db.Instance;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.ci.CIContacts;
import org.efaps.esjp.ci.CIERP;
import org.efaps.esjp.common.jasperreport.EFapsMapDataSource;
import org.efaps.util.EFapsException;
import org.joda.time.DateTime;


/**
 * TODO comment!
 *
 * @author The eFaps Team
 * @version $Id$
 */
@EFapsUUID("412cf393-f815-4029-b1e8-d2c42a98383b")
@EFapsRevision("$Rev$")
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

        final QueryBuilder attrQueryBldr = new QueryBuilder(CIAccounting.TransactionClassDocument);
        final AttributeQuery attrQuery =
                        attrQueryBldr.getAttributeQuery(CIAccounting.TransactionClassDocument.TransactionLink);
        final QueryBuilder queryBuilder = new QueryBuilder(CIAccounting.Transaction);
        queryBuilder.addWhereAttrInQuery(CIAccounting.Transaction.ID, attrQuery);
        queryBuilder.addWhereAttrEqValue(CIAccounting.Transaction.PeriodeLink, instance.getId());

        final MultiPrintQuery multi = queryBuilder.getPrint();
        final SelectBuilder selDoc = new SelectBuilder().clazz(CIAccounting.TransactionClassDocument)
                        .linkto(CIAccounting.TransactionClassDocument.DocumentLink);
        final SelectBuilder selDocName = new SelectBuilder(selDoc).attribute(CIERP.DocumentAbstract.Name);
        final SelectBuilder selDocContactName = new SelectBuilder(selDoc).linkto(CIERP.DocumentAbstract.Contact)
                        .attribute(CIContacts.Contact.Name);
        final SelectBuilder selDocContactTaxNumber = new SelectBuilder(selDoc).linkto(CIERP.DocumentAbstract.Contact)
                        .clazz(CIContacts.ClassOrganisation).attribute(CIContacts.ClassOrganisation.TaxNumber);
        multi.addSelect(selDocName, selDocContactName, selDocContactTaxNumber);
        multi.addAttribute(CIAccounting.Transaction.Date);
        multi.execute();
        while (multi.next()) {
            final String docName = multi.<String>getSelect(selDocName);
            final String contactName = multi.<String>getSelect(selDocContactName);
            final String taxNumber = multi.<String>getSelect(selDocContactTaxNumber);
            final DateTime date = multi.<DateTime>getAttribute(CIAccounting.Transaction.Date);

            final QueryBuilder posQueryBldr = new QueryBuilder(CIAccounting.TransactionPositionAbstract);
            posQueryBldr.addWhereAttrEqValue(CIAccounting.TransactionPositionAbstract.TransactionLink, multi.getCurrentInstance().getId());
            final MultiPrintQuery posMulti = posQueryBldr.getPrint();
            final SelectBuilder selAcc = new SelectBuilder().linkto(CIAccounting.TransactionPositionAbstract.AccountLink);
            final SelectBuilder selAccName = new SelectBuilder(selAcc).attribute(CIAccounting.AccountAbstract.Name);
            final SelectBuilder selAccDesc = new SelectBuilder(selAcc).attribute(CIAccounting.AccountAbstract.Description);
            posMulti.addSelect(selAccName, selAccDesc);
            posMulti.addAttribute(CIAccounting.TransactionPositionAbstract.Amount);
            posMulti.execute();
            while (posMulti.next()) {
                final Map<String, Object> row = new HashMap<String, Object>();
                row.put("docName", docName);
                row.put("contactName", contactName);
                row.put("taxNumber", taxNumber);
                row.put("date", date);
                row.put("amount", posMulti.getAttribute(CIAccounting.TransactionPositionAbstract.Amount));
                row.put("accName", posMulti.getSelect(selAccName));
                row.put("accDescr", posMulti.getSelect(selAccDesc));
                getValues().add(row);
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

        System.out.println(getValues());
        Collections.sort(getValues(), chain);
    }
}
