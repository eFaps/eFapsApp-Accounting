/*
 * Copyright 2003 - 2014 The eFaps Team
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

package org.efaps.esjp.accounting.report.balance;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JasperReport;

import org.apache.commons.collections4.comparators.ComparatorChain;
import org.efaps.admin.datamodel.Status;
import org.efaps.admin.event.Parameter;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.esjp.accounting.report.AbstractReportDS;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.ci.CIContacts;
import org.efaps.esjp.ci.CISales;
import org.efaps.util.EFapsException;
import org.joda.time.DateTime;


/**
 * TODO comment!
 *
 * @author The eFaps Team
 * @version $Id$
 */
public abstract class BalanceReport312DS_Base
extends AbstractReportDS
{
    @Override
    public void init(final JasperReport _jasperReport,
                     final Parameter _parameter,
                     final JRDataSource _parentSource,
                     final Map<String, Object> _jrParameters)
        throws EFapsException
    {
        super.init(_jasperReport, _parameter, _parentSource, _jrParameters);

        final QueryBuilder queryBldr = new QueryBuilder(CISales.IncomingInvoice);
        queryBldr.addType(CISales.IncomingReceipt);
        queryBldr.addType(CIAccounting.ExternalVoucher);
        queryBldr.addWhereAttrEqValue(CISales.DocumentSumAbstract.StatusAbstract,
                        Status.find(CISales.IncomingInvoiceStatus.Open),
                        Status.find(CISales.IncomingReceiptStatus.Open),
                        Status.find(CIAccounting.ExternalVoucherStatus.Open));

        final MultiPrintQuery multi = queryBldr.getPrint();
        final SelectBuilder selContact = SelectBuilder.get().linkto(CISales.DocumentSumAbstract.Contact);
        final SelectBuilder selContactName = new SelectBuilder(selContact).attribute(CIContacts.ContactAbstract.Name);
        final SelectBuilder selContactTaxNumber = new SelectBuilder(selContact).clazz(CIContacts.ClassOrganisation)
                        .attribute(CIContacts.ClassOrganisation.TaxNumber);
        final SelectBuilder selContactPersDOI = new SelectBuilder(selContact).clazz(CIContacts.ClassPerson)
                        .attribute(CIContacts.ClassPerson.IdentityCard);
        final SelectBuilder selContactPersDOIType = new SelectBuilder(selContact).clazz(CIContacts.ClassPerson)
                        .linkto(CIContacts.ClassPerson.DOITypeLink)
                        .attribute(CIContacts.AttributeDefinitionDOIType.Value);
        multi.addSelect(selContactName, selContactTaxNumber, selContactPersDOI, selContactPersDOIType);
        multi.addAttribute(CISales.DocumentSumAbstract.NetTotal, CISales.DocumentSumAbstract.Date);
        multi.execute();
        final List<Bean312> values = new ArrayList<>();
        while (multi.next()) {
            final Bean312 bean;
            bean = getBean(_parameter);
            values.add(bean);
            bean.setContactName(multi.<String>getSelect(selContactName));
            final String contactTaxNumber = multi.<String>getSelect(selContactTaxNumber);
            if (contactTaxNumber == null) {
                bean.setContactDOINumber(multi.<String>getSelect(selContactPersDOI));
                bean.setContactDOIType(multi.<String>getSelect(selContactPersDOIType));
            } else {
                bean.setContactDOINumber(contactTaxNumber);
                bean.setContactDOIType("7");
            }
            bean.setDocDate(multi.<DateTime>getAttribute(CISales.DocumentSumAbstract.Date));
            bean.setAmount(multi.<BigDecimal>getAttribute(CISales.DocumentSumAbstract.NetTotal));
        }
        final ComparatorChain<Bean312> chain = new ComparatorChain<>();
        chain.addComparator(new Comparator<Bean312>()
        {

            @Override
            public int compare(final Bean312 _arg0,
                               final Bean312 _arg1)
            {
                return _arg0.getContactDOIType().compareTo(_arg1.getContactDOIType());
            }
        });
        chain.addComparator(new Comparator<Bean312>()
        {

            @Override
            public int compare(final Bean312 _arg0,
                               final Bean312 _arg1)
            {
                return _arg0.getContactDOINumber().compareTo(_arg1.getContactDOINumber());
            }
        });
        chain.addComparator(new Comparator<Bean312>()
        {

            @Override
            public int compare(final Bean312 _arg0,
                               final Bean312 _arg1)
            {
                return _arg0.getDocDate().compareTo(_arg1.getDocDate());
            }
        });

        Collections.sort(values, chain);
        setData(values);
    }

    protected Bean312 getBean(final Parameter _parameter)
    {
        return new Bean312();
    }


    public static class Bean312
    {
        private String contactDOIType;
        private String contactDOINumber;
        private String contactName;
        private BigDecimal amount;
        private DateTime docDate;

        /**
         * Getter method for the instance variable {@link #contactDOIType}.
         *
         * @return value of instance variable {@link #contactDOIType}
         */
        public String getContactDOIType()
        {
            return this.contactDOIType;
        }

        /**
         * Setter method for instance variable {@link #contactDOIType}.
         *
         * @param _contactDOIType value for instance variable {@link #contactDOIType}
         */
        public void setContactDOIType(final String _contactDOIType)
        {
            this.contactDOIType = _contactDOIType;
        }

        /**
         * Getter method for the instance variable {@link #contactDOINumber}.
         *
         * @return value of instance variable {@link #contactDOINumber}
         */
        public String getContactDOINumber()
        {
            return this.contactDOINumber;
        }

        /**
         * Setter method for instance variable {@link #contactDOINumber}.
         *
         * @param _contactDOINumber value for instance variable {@link #contactDOINumber}
         */
        public void setContactDOINumber(final String _contactDOINumber)
        {
            this.contactDOINumber = _contactDOINumber;
        }

        /**
         * Getter method for the instance variable {@link #contactName}.
         *
         * @return value of instance variable {@link #contactName}
         */
        public String getContactName()
        {
            return this.contactName;
        }

        /**
         * Setter method for instance variable {@link #contactName}.
         *
         * @param _contactName value for instance variable {@link #contactName}
         */
        public void setContactName(final String _contactName)
        {
            this.contactName = _contactName;
        }

        /**
         * Getter method for the instance variable {@link #amount}.
         *
         * @return value of instance variable {@link #amount}
         */
        public BigDecimal getAmount()
        {
            return this.amount;
        }

        /**
         * Setter method for instance variable {@link #amount}.
         *
         * @param _amount value for instance variable {@link #amount}
         */
        public void setAmount(final BigDecimal _amount)
        {
            this.amount = _amount;
        }

        /**
         * Getter method for the instance variable {@link #docDate}.
         *
         * @return value of instance variable {@link #docDate}
         */
        public DateTime getDocDate()
        {
            return this.docDate;
        }

        /**
         * Setter method for instance variable {@link #docDate}.
         *
         * @param _docDate value for instance variable {@link #docDate}
         */
        public void setDocDate(final DateTime _docDate)
        {
            this.docDate = _docDate;
        }
    }
}
