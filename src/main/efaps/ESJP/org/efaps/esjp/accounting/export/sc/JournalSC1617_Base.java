/*
 * Copyright 2003 - 2016 The eFaps Team
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

package org.efaps.esjp.accounting.export.sc;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.apache.commons.collections4.comparators.ComparatorChain;
import org.apache.commons.lang3.StringUtils;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.AttributeQuery;
import org.efaps.db.CachedMultiPrintQuery;
import org.efaps.db.Instance;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.ci.CIContacts;
import org.efaps.esjp.ci.CIERP;
import org.efaps.esjp.ci.CIFormAccounting;
import org.efaps.esjp.ci.CISales;
import org.efaps.esjp.data.columns.export.FrmtColumn;
import org.efaps.esjp.data.columns.export.FrmtDateTimeColumn;
import org.efaps.esjp.data.columns.export.FrmtNumberColumn;
import org.efaps.esjp.db.InstanceUtils;
import org.efaps.esjp.erp.Currency;
import org.efaps.esjp.erp.CurrencyInst;
import org.efaps.esjp.erp.RateInfo;
import org.efaps.util.EFapsException;
import org.joda.time.DateTime;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 */
@EFapsUUID("245e77d8-eda7-4ce3-9153-1c71fec8eefa")
@EFapsApplication("eFapsApp-Accounting")
public abstract class JournalSC1617_Base
    extends AbstractSCExport
{

    @Override
    public void addColumnDefinition(final Parameter _parameter,
                                    final Exporter _exporter)
    {
        // T
        _exporter.addColumns(new FrmtColumn("origin", 2));
        // VOU Número de voucher (correlativo de cada operación)
        _exporter.addColumns(new FrmtColumn("transDoc", 5));
        // FECHA
        _exporter.addColumns(new FrmtDateTimeColumn("transDate", 8, "dd/MM/yy"));
        // CUENTA
        _exporter.addColumns(new FrmtColumn("accName", 10));
        // DEBE
        _exporter.addColumns(new FrmtNumberColumn("amountDebit", 12, 2, 9));
        // HABER
        _exporter.addColumns(new FrmtNumberColumn("amountCredit", 12, 2, 9));
        // MONEDA
        _exporter.addColumns(new FrmtColumn("currency", 1));
        // TC
        _exporter.addColumns(new FrmtNumberColumn("rate", 10, 7, 2));
        // DOC
        _exporter.addColumns(new FrmtColumn("documentType", 2));
        // NUMERO
        _exporter.addColumns(new FrmtColumn("docName", 40));
        // FECHAD
        _exporter.addColumns(new FrmtDateTimeColumn("docDate", 8, "dd/MM/yy"));
        // FECHAV
        _exporter.addColumns(new FrmtDateTimeColumn("docDueDate", 8, "dd/MM/yy"));
        // CÓDIGO
        _exporter.addColumns(new FrmtColumn("taxNumber", 15));
        // CC
        _exporter.addColumns(new FrmtColumn("empty", 10));
        // FE
        _exporter.addColumns(new FrmtColumn("empty", 4));
        // PRE
        _exporter.addColumns(new FrmtColumn("empty", 10));
        // MPAGO
        _exporter.addColumns(new FrmtColumn("empty", 3));
        // GLOSA
        _exporter.addColumns(new FrmtColumn("transDescr", 60));
        // RNUMERO
        _exporter.addColumns(new FrmtColumn("empty", 40));
        // RTDOC
        _exporter.addColumns(new FrmtColumn("empty", 2));
        // RFECHA
        _exporter.addColumns(new FrmtColumn("empty", 8));
        // SNUMERO
        _exporter.addColumns(new FrmtColumn("empty", 40));
        // SFECHA Fecha de la DETRACCION
        _exporter.addColumns(new FrmtColumn("empty", 8));
        // TL C:Compra, V:Venta, R:Retenciones
        _exporter.addColumns(new FrmtColumn("empty", 1));
        // NETO Monto sub Total, va a la Cta. 40
        _exporter.addColumns(new FrmtNumberColumn("netTotal", 12, 2, 9));
        // NETO2 Monto Adq. no gravadas o Exonerados/Inafectos, va a la Cta. 40
        _exporter.addColumns(new FrmtNumberColumn("zero", 12, 2, 9));
        // NETO3 Monto IGV B., va a la Cta. 40
        _exporter.addColumns(new FrmtNumberColumn("zero", 12, 2, 9));
        // NETO4 Monto de Valor de exportación- Base imponible B,va a la Cta. 40
        _exporter.addColumns(new FrmtNumberColumn("zero", 12, 2, 9));
        // IGV Monto IGV, va a la Cta. 40
        _exporter.addColumns(new FrmtNumberColumn("vat", 12, 2, 9));
        // NETO5 Monto otros tributos, va a la Cta. 40
        _exporter.addColumns(new FrmtNumberColumn("zero", 12, 2, 9));
        // NETO6 Monto Base imponible C, va a la Cta. 40
        _exporter.addColumns(new FrmtNumberColumn("zero", 12, 2, 9));
        // NETO7 Monto IGV C, va a la Cta. 40
        _exporter.addColumns(new FrmtNumberColumn("zero", 12, 2, 9));
        // NETO8 Monto I.S.C., va a la Cta. 40
        _exporter.addColumns(new FrmtNumberColumn("zero", 12, 2, 9));
        // RUC
        _exporter.addColumns(new FrmtColumn("taxNumber", 15));
        // TIPO De acuerdo a tabla de Sc
        _exporter.addColumns(new FrmtColumn("empty", 1));
        // RS
        _exporter.addColumns(new FrmtColumn("contactName", 60));
        // APE1 Primer Apellido
        _exporter.addColumns(new FrmtColumn("lastName", 20));
        // APE2 Segundo Apellido
        _exporter.addColumns(new FrmtColumn("secondLastName", 20));
        // NOMBRE Primer Nombre
        _exporter.addColumns(new FrmtColumn("firstname", 20));
        // TDOCI
        _exporter.addColumns(new FrmtColumn("empty", 1));
        // RNUMDES
        _exporter.addColumns(new FrmtColumn("empty", 1));
        // RCODTASA
        _exporter.addColumns(new FrmtColumn("empty", 5));
        // RINDRET
        _exporter.addColumns(new FrmtColumn("empty", 1));
        // RMONTO
        _exporter.addColumns(new FrmtNumberColumn("zero", 12, 2, 9));
        // RIGV
        _exporter.addColumns(new FrmtNumberColumn("zero", 12, 2, 9));
        // TBIEN
        _exporter.addColumns(new FrmtColumn("empty", 1));
    }

    @Override
    public void buildDataSource(final Parameter _parameter,
                                final Exporter _exporter)
        throws EFapsException
    {
        final DateTime dateFrom = new DateTime(_parameter.getParameterValue(
                        CIFormAccounting.Accounting_ExportJournalSC1617Form.dateFrom.name));
        final DateTime dateTo = new DateTime(_parameter.getParameterValue(
                        CIFormAccounting.Accounting_ExportJournalSC1617Form.dateTo.name));
        final Instance subJournalInst = Instance.get(_parameter.getParameterValue(
                        CIFormAccounting.Accounting_ExportJournalSC1617Form.subJournal.name));
        final String origin = _parameter.getParameterValue(
                        CIFormAccounting.Accounting_ExportJournalSC1617Form.origin.name);

        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.TransactionPositionAbstract);
        final QueryBuilder transAttrQueryBldr = new QueryBuilder(CIAccounting.TransactionAbstract);
        transAttrQueryBldr.addWhereAttrLessValue(CIAccounting.TransactionAbstract.Date,
                        dateTo.withTimeAtStartOfDay().plusDays(1));
        transAttrQueryBldr.addWhereAttrGreaterValue(CIAccounting.TransactionAbstract.Date,
                        dateFrom.withTimeAtStartOfDay().minusSeconds(1));

        if (InstanceUtils.isValid(subJournalInst)) {
            final QueryBuilder attrQueryBuilder = new QueryBuilder(CIAccounting.ReportSubJournal2Transaction);
            attrQueryBuilder.addWhereAttrEqValue(CIAccounting.ReportSubJournal2Transaction.FromLink, subJournalInst);
            transAttrQueryBldr.addWhereAttrInQuery(CIAccounting.TransactionAbstract.ID,
                            attrQueryBuilder.getAttributeQuery(CIAccounting.ReportSubJournal2Transaction.ToLink));
        }
        final AttributeQuery transAttrQuery = transAttrQueryBldr.getAttributeQuery(CIAccounting.TransactionAbstract.ID);

        queryBldr.addWhereAttrInQuery(CIAccounting.TransactionPositionAbstract.TransactionLink, transAttrQuery);

        final MultiPrintQuery multi = queryBldr.getPrint();
        final SelectBuilder selAcc = SelectBuilder.get().linkto(CIAccounting.TransactionPositionAbstract.AccountLink);
        final SelectBuilder selAccName = new SelectBuilder(selAcc).attribute(CIAccounting.AccountAbstract.Name);

        final SelectBuilder selTrans = SelectBuilder.get().linkto(
                        CIAccounting.TransactionPositionAbstract.TransactionLink);
        final SelectBuilder selTransOID = new SelectBuilder(selTrans).oid();
        final SelectBuilder selTransDescr = new SelectBuilder(selTrans)
                        .attribute(CIAccounting.TransactionAbstract.Description);
        final SelectBuilder selTransIdentifier = new SelectBuilder(selTrans)
                        .attribute(CIAccounting.TransactionAbstract.Identifier);
        final SelectBuilder selTransName = new SelectBuilder(selTrans)
                        .attribute(CIAccounting.TransactionAbstract.Name);
        final SelectBuilder selTransDate = new SelectBuilder(selTrans).attribute(CIAccounting.TransactionAbstract.Date);

        final SelectBuilder selDoc = SelectBuilder.get()
                        .linkfrom(CIAccounting.TransactionPosition2ERPDocument.FromLinkAbstract)
                        .linkto(CIAccounting.TransactionPosition2ERPDocument.ToLinkAbstract);
        final SelectBuilder selDocInst = new SelectBuilder(selDoc).instance();
        final SelectBuilder selDocName = new SelectBuilder(selDoc).attribute(CISales.DocumentSumAbstract.Name);
        final SelectBuilder selDocDate = new SelectBuilder(selDoc).attribute(CISales.DocumentSumAbstract.Date);
        final SelectBuilder selDocDueDate = new SelectBuilder(selDoc).attribute(CISales.DocumentSumAbstract.DueDate);
        final SelectBuilder selNetTotal = new SelectBuilder(selDoc).attribute(CISales.DocumentSumAbstract.RateNetTotal);
        final SelectBuilder selCrossTotal = new SelectBuilder(selDoc)
                        .attribute(CISales.DocumentSumAbstract.RateCrossTotal);

        final SelectBuilder selContact = new SelectBuilder(selDoc).linkto(CISales.DocumentAbstract.Contact);
        final SelectBuilder selContactName = new SelectBuilder(selContact).attribute(CIContacts.Contact.Name);
        final SelectBuilder selTaxNumber = new SelectBuilder(selContact).clazz(CIContacts.ClassOrganisation)
                        .attribute(CIContacts.ClassOrganisation.TaxNumber);

        multi.addSelect(selAccName, selTransIdentifier, selTransOID, selTransName, selTransDescr, selTransDate,
                        selDocInst, selDocName, selDocDate, selDocDueDate, selContactName, selTaxNumber, selNetTotal,
                        selCrossTotal);
        multi.addAttribute(CIAccounting.TransactionPositionAbstract.RateAmount,
                        CIAccounting.TransactionPositionAbstract.Position,
                        CIAccounting.TransactionPositionAbstract.PositionType,
                        CIAccounting.TransactionPositionAbstract.RateCurrencyLink,
                        CIAccounting.TransactionPositionAbstract.Rate);
        multi.execute();
        final List<DataBean> beans = new ArrayList<>();
        while (multi.next()) {
            final DataBean bean = new DataBean()
                            .setOrigin(origin)
                            .setTransDate(multi.<DateTime>getSelect(selTransDate))
                            .setTransDoc(multi.getSelect(selTransIdentifier))
                            .setPosition(multi.<Integer>getAttribute(CIAccounting.TransactionPositionAbstract.Position))
                            .setAccName(multi.<String>getSelect(selAccName))
                            .setAmount(multi.<BigDecimal>getAttribute(
                                            CIAccounting.TransactionPositionAbstract.RateAmount))
                            .setTransDescr(multi.<String>getSelect(selTransDescr))
                            .setCurrencyId(multi.<Long>getAttribute(
                                            CIAccounting.TransactionPositionAbstract.RateCurrencyLink))
                            .setDocInst(multi.<Instance>getSelect(selDocInst))
                            .setDocName(multi.<String>getSelect(selDocName))
                            .setDocDate(multi.<DateTime>getSelect(selDocDate))
                            .setDocDueDate(multi.<DateTime>getSelect(selDocDueDate))
                            .setContactName(multi.<String>getSelect(selContactName))
                            .setTaxNumber(multi.<String>getSelect(selTaxNumber))
                            .setRate(multi.<Object[]>getAttribute(CIAccounting.TransactionPositionAbstract.Rate))
                            .setNetTotal(multi.<BigDecimal>getSelect(selNetTotal))
                            .setCrossTotal(multi.<BigDecimal>getSelect(selCrossTotal));
            beans.add(bean);
        }
        final ComparatorChain<DataBean> chain = new ComparatorChain<>();
        chain.addComparator(new Comparator<DataBean>() {

            @Override
            public int compare(final DataBean _o1,
                               final DataBean _o2)
            {
                return _o1.getTransDate().compareTo(_o2.getTransDate());
            }
        });
        chain.addComparator(new Comparator<DataBean>() {

            @Override
            public int compare(final DataBean _o1,
                               final DataBean _o2)
            {
                return _o1.getTransDoc().compareTo(_o2.getTransDoc());
            }
        });
        chain.addComparator(new Comparator<DataBean>() {

            @Override
            public int compare(final DataBean _o1,
                               final DataBean _o2)
            {
                return _o1.getPosition().compareTo(_o2.getPosition());
            }
        });
        Collections.sort(beans, chain);

        for (final DataBean bean : beans) {
            _exporter.addBeanRows(bean);
        }
    }

    @Override
    public String getFileName(final Parameter _parameter)
        throws EFapsException
    {
        final DateTime dateFrom = new DateTime(_parameter.getParameterValue(
                        CIFormAccounting.Accounting_ExportJournalSC1617Form.dateFrom.name));
        final DateTime dateTo = new DateTime(_parameter.getParameterValue(
                        CIFormAccounting.Accounting_ExportJournalSC1617Form.dateTo.name));
        return dateFrom.toString("yyyyMMdd") + "-" + dateTo.toString("yyyyMMdd") + ".txt";
    }

    /**
     * The Class DataBean.
     */
    public static class DataBean
    {
        /**
         * The origin. (Origen, definido en sc (01: compras, 02:ventas, etc.)
         */
        private String origin;

        /** Name of the transaction Document. (Numero de Comprobante ) */
        private String transDoc;

        /** The transaction date. (Fecha del Comprobante ) */
        private DateTime transDate;

        /** The account name. (Cuenta Contable) */
        private String accName;

        /** The amount debit. */
        private BigDecimal amount;

        /** The currency. S or D (Moneda) */
        private String currency;

        /** The rate. */
        private BigDecimal rate;

        /** The doc inst. */
        private Instance docInst;

        /** The doc name. (Numero del Documento) */
        private String docName;

        /** The doc date. */
        private DateTime docDate;

        /** The doc due date. */
        private DateTime docDueDate;

        /** The trans descr. */
        private String transDescr;

        /** The net total. */
        private BigDecimal netTotal;

        /** The cross total. */
        private BigDecimal crossTotal;

        /** The contact name. */
        private String contactName;

        /** The tax number. */
        private String taxNumber;

        /** The last name. */
        private String lastName;

        /** The second last name. */
        private String secondLastName;

        /** The firstname. */
        private String firstname;

        /** The position. */
        private int position;

        /** An empty field. */
        private String empty;

        /** The zero. */
        private BigDecimal zero;

        /**
         * Getter method for the instance variable {@link #transDate}.
         *
         * @return value of instance variable {@link #transDate}
         */
        public DateTime getTransDate()
        {
            return this.transDate;
        }

        /**
         * Sets the currency id.
         *
         * @param _currencyId the _currency id
         * @return the data bean
         * @throws EFapsException the e faps exception
         */
        public DataBean setCurrencyId(final Long _currencyId)
            throws EFapsException
        {
            final CurrencyInst currencyInst = CurrencyInst.get(_currencyId);
            // only dolar and soles are working!
            if (currencyInst.getUUID().toString().equals("691758fc-a060-4bd5-b1fa-b33296638126")) {
                this.currency = "D";
            } else {
                this.currency = "S";
            }
            return this;
        }

        /**
         * Setter method for instance variable {@link #transDate}.
         *
         * @param _transDate value for instance variable {@link #transDate}
         * @return the data bean
         */
        public DataBean setTransDate(final DateTime _transDate)
        {
            this.transDate = _transDate;
            return this;
        }

        /**
         * Getter method for the instance variable {@link #accName}.
         *
         * @return value of instance variable {@link #accName}
         */
        public String getAccName()
        {
            return this.accName;
        }

        /**
         * Setter method for instance variable {@link #accName}.
         *
         * @param _accName value for instance variable {@link #accName}
         * @return the data bean
         */
        public DataBean setAccName(final String _accName)
        {
            this.accName = _accName;
            return this;
        }

        /**
         * Getter method for the instance variable {@link #amountDebit}.
         *
         * @return value of instance variable {@link #amountDebit}
         */
        public BigDecimal getAmountDebit()
        {
            return this.amount.compareTo(BigDecimal.ZERO) < 0 ? this.amount.abs() : BigDecimal.ZERO;
        }

        /**
         * Getter method for the instance variable {@link #amountCredit}.
         *
         * @return value of instance variable {@link #amountCredit}
         */
        public BigDecimal getAmountCredit()
        {
            return this.amount.compareTo(BigDecimal.ZERO) > 0 ? this.amount.abs() : BigDecimal.ZERO;
        }

        /**
         * Setter method for instance variable {@link #amountCredit}.
         *
         * @param _amount the _amount
         * @return the data bean
         */
        public DataBean setAmount(final BigDecimal _amount)
        {
            this.amount = _amount;
            return this;
        }

        /**
         * Getter method for the instance variable {@link #documentType}.
         *
         * @return value of instance variable {@link #documentType}
         * @throws EFapsException on error
         */
        public String getDocumentType()
            throws EFapsException
        {
            String ret = null;
            if (InstanceUtils.isValid(getDocInst())) {
                if (getDocInst().getType().isCIType(CISales.Invoice)) {
                    ret = "01";
                } else if (getDocInst().getType().isCIType(CISales.Receipt)) {
                    ret = "03";
                } else if (getDocInst().getType().isCIType(CISales.CreditNote)) {
                    ret = "07";
                } else if (getDocInst().getType().isCIType(CISales.Reminder)) {
                    ret = "08";
                } else {
                    final QueryBuilder queryBldr = new QueryBuilder(CISales.Document2DocumentType);
                    queryBldr.addWhereAttrEqValue(CISales.Document2DocumentType.DocumentLink, getDocInst());
                    final CachedMultiPrintQuery multi = queryBldr.getCachedPrint4Request();
                    final SelectBuilder selDocType = SelectBuilder.get()
                                    .linkto(CISales.Document2DocumentType.DocumentTypeLink)
                                    .attribute(CIERP.DocumentType.Name);
                    multi.addSelect(selDocType);
                    multi.execute();
                    if (multi.next()) {
                        ret = multi.getSelect(selDocType);
                    }
                }
            }
            return ret;
        }

        /**
         * Getter method for the instance variable {@link #transDoc}.
         *
         * @return value of instance variable {@link #transDoc}
         */
        public String getTransDoc()
        {
            return this.transDoc == null ? null : StringUtils.substring(this.transDoc, -5, this.transDoc.length());
        }

        /**
         * Setter method for instance variable {@link #transDoc}.
         *
         * @param _transDoc value for instance variable {@link #transDoc}
         * @return the data bean
         */
        public DataBean setTransDoc(final String _transDoc)
        {
            this.transDoc = _transDoc;
            return this;
        }

        /**
         * Getter method for the instance variable {@link #currency}.
         *
         * @return value of instance variable {@link #currency}
         */
        public String getCurrency()
        {
            return this.currency;
        }

        /**
         * Setter method for instance variable {@link #currency}.
         *
         * @param _currency value for instance variable {@link #currency}
         * @return the data bean
         */
        public DataBean setCurrency(final String _currency)
        {
            this.currency = _currency;
            return this;
        }

        /**
         * Getter method for the instance variable {@link #rate}.
         *
         * @return value of instance variable {@link #rate}
         * @throws EFapsException
         */
        public BigDecimal getRate()
            throws EFapsException
        {
            BigDecimal ret = this.rate;
            if (BigDecimal.ONE.compareTo(ret) == 0 && "S".equals(this.currency)) {
                final RateInfo rateInfo = new Currency().evaluateRateInfo(new Parameter(), getTransDate(), CurrencyInst
                                .get(UUID.fromString("691758fc-a060-4bd5-b1fa-b33296638126")).getInstance());
                ret = rateInfo.getSaleRateUI();
            }
            return ret;
        }

        /**
         * Setter method for instance variable {@link #rate}.
         *
         * @param _rate value for instance variable {@link #rate}
         * @return the data bean
         * @throws EFapsException the e faps exception
         */
        public DataBean setRate(final Object[] _rate)
            throws EFapsException
        {
            this.rate = new Currency().evalRate(_rate, true);
            return this;
        }

        /**
         * Getter method for the instance variable {@link #docName}.
         *
         * @return value of instance variable {@link #docName}
         */
        public String getDocName()
        {
            return this.docName;
        }

        /**
         * Setter method for instance variable {@link #docName}.
         *
         * @param _docName value for instance variable {@link #docName}
         * @return the data bean
         */
        public DataBean setDocName(final String _docName)
        {
            this.docName = _docName;
            return this;
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
         * @return the data bean
         */
        public DataBean setDocDate(final DateTime _docDate)
        {
            this.docDate = _docDate;
            return this;
        }

        /**
         * Getter method for the instance variable {@link #docDueDate}.
         *
         * @return value of instance variable {@link #docDueDate}
         */
        public DateTime getDocDueDate()
        {
            return this.docDueDate;
        }

        /**
         * Setter method for instance variable {@link #docDueDate}.
         *
         * @param _docDueDate value for instance variable {@link #docDueDate}
         * @return the data bean
         */
        public DataBean setDocDueDate(final DateTime _docDueDate)
        {
            this.docDueDate = _docDueDate;
            return this;
        }

        /**
         * Getter method for the instance variable {@link #transDescr}.
         *
         * @return value of instance variable {@link #transDescr}
         */
        public String getTransDescr()
        {
            return this.transDescr;
        }

        /**
         * Setter method for instance variable {@link #transDescr}.
         *
         * @param _transDescr value for instance variable {@link #transDescr}
         * @return the data bean
         */
        public DataBean setTransDescr(final String _transDescr)
        {
            this.transDescr = _transDescr;
            return this;
        }

        /**
         * Getter method for the instance variable {@link #empty}.
         *
         * @return value of instance variable {@link #empty}
         */
        public String getEmpty()
        {
            return this.empty;
        }

        /**
         * Setter method for instance variable {@link #empty}.
         *
         * @param _empty value for instance variable {@link #empty}
         * @return the data bean
         */
        public DataBean setEmpty(final String _empty)
        {
            this.empty = _empty;
            return this;
        }

        /**
         * Getter method for the instance variable {@link #netTotal}.
         *
         * @return value of instance variable {@link #netTotal}
         */
        public BigDecimal getNetTotal()
        {
            return this.netTotal;
        }

        /**
         * Setter method for instance variable {@link #netTotal}.
         *
         * @param _netTotal value for instance variable {@link #netTotal}
         * @return the data bean
         */
        public DataBean setNetTotal(final BigDecimal _netTotal)
        {
            this.netTotal = _netTotal;
            return this;
        }

        /**
         * Getter method for the instance variable {@link #vat}.
         *
         * @return value of instance variable {@link #vat}
         */
        public BigDecimal getVat()
        {
            return getCrossTotal() == null ? BigDecimal.ZERO : getCrossTotal().subtract(getNetTotal());
        }

        /**
         * Setter method for instance variable {@link #vat}.
         *
         * @param _vat value for instance variable {@link #vat}
         * @return the data bean
         */
        public DataBean setVat(final BigDecimal _vat)
        {
            return this;
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
         * @return the data bean
         */
        public DataBean setContactName(final String _contactName)
        {
            this.contactName = _contactName;
            return this;
        }

        /**
         * Getter method for the instance variable {@link #zero}.
         *
         * @return value of instance variable {@link #zero}
         */
        public BigDecimal getZero()
        {
            return this.zero;
        }

        /**
         * Setter method for instance variable {@link #zero}.
         *
         * @param _zero value for instance variable {@link #zero}
         * @return the data bean
         */
        public DataBean setZero(final BigDecimal _zero)
        {
            this.zero = _zero;
            return this;
        }

        /**
         * Getter method for the instance variable {@link #taxNumber}.
         *
         * @return value of instance variable {@link #taxNumber}
         */
        public String getTaxNumber()
        {
            return this.taxNumber;
        }

        /**
         * Setter method for instance variable {@link #taxNumber}.
         *
         * @param _taxNumber value for instance variable {@link #taxNumber}
         * @return the data bean
         */
        public DataBean setTaxNumber(final String _taxNumber)
        {
            this.taxNumber = _taxNumber;
            return this;
        }

        /**
         * Getter method for the instance variable {@link #origin}.
         *
         * @return value of instance variable {@link #origin}
         */
        public String getOrigin()
        {
            return this.origin;
        }

        /**
         * Setter method for instance variable {@link #origin}.
         *
         * @param _origin value for instance variable {@link #origin}
         * @return the data bean
         */
        public DataBean setOrigin(final String _origin)
        {
            this.origin = _origin;
            return this;
        }

        /**
         * Getter method for the instance variable {@link #lastName}.
         *
         * @return value of instance variable {@link #lastName}
         */
        public String getLastName()
        {
            return this.lastName;
        }

        /**
         * Setter method for instance variable {@link #lastName}.
         *
         * @param _lastName value for instance variable {@link #lastName}
         * @return the data bean
         */
        public DataBean setLastName(final String _lastName)
        {
            this.lastName = _lastName;
            return this;
        }

        /**
         * Getter method for the instance variable {@link #secondLastName}.
         *
         * @return value of instance variable {@link #secondLastName}
         */
        public String getSecondLastName()
        {
            return this.secondLastName;
        }

        /**
         * Setter method for instance variable {@link #secondLastName}.
         *
         * @param _secondLastName value for instance variable
         *            {@link #secondLastName}
         * @return the data bean
         */
        public DataBean setSecondLastName(final String _secondLastName)
        {
            this.secondLastName = _secondLastName;
            return this;
        }

        /**
         * Getter method for the instance variable {@link #firstname}.
         *
         * @return value of instance variable {@link #firstname}
         */
        public String getFirstname()
        {
            return this.firstname;
        }

        /**
         * Setter method for instance variable {@link #firstname}.
         *
         * @param _firstname value for instance variable {@link #firstname}
         * @return the data bean
         */
        public DataBean setFirstname(final String _firstname)
        {
            this.firstname = _firstname;
            return this;
        }

        /**
         * Getter method for the instance variable {@link #crossTotal}.
         *
         * @return value of instance variable {@link #crossTotal}
         */
        public BigDecimal getCrossTotal()
        {
            return this.crossTotal;
        }

        /**
         * Setter method for instance variable {@link #crossTotal}.
         *
         * @param _crossTotal value for instance variable {@link #crossTotal}
         * @return the data bean
         */
        public DataBean setCrossTotal(final BigDecimal _crossTotal)
        {
            this.crossTotal = _crossTotal;
            return this;
        }

        /**
         * Getter method for the instance variable {@link #docInst}.
         *
         * @return value of instance variable {@link #docInst}
         */
        public Instance getDocInst()
        {
            return this.docInst;
        }

        /**
         * Setter method for instance variable {@link #docInst}.
         *
         * @param _docInst value for instance variable {@link #docInst}
         * @return the data bean
         */
        public DataBean setDocInst(final Instance _docInst)
        {
            this.docInst = _docInst;
            return this;
        }

        /**
         * Getter method for the instance variable {@link #position}.
         *
         * @return value of instance variable {@link #position}
         */
        public Integer getPosition()
        {
            return this.position;
        }

        /**
         * Setter method for instance variable {@link #position}.
         *
         * @param _position value for instance variable {@link #position}
         * @return the data bean
         */
        public DataBean setPosition(final int _position)
        {
            this.position = _position;
            return this;
        }
    }
}
