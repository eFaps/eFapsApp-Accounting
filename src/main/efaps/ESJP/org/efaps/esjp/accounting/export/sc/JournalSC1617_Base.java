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
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import org.apache.commons.collections4.comparators.ComparatorChain;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Return;
import org.efaps.admin.event.Return.ReturnValues;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.AttributeQuery;
import org.efaps.db.CachedMultiPrintQuery;
import org.efaps.db.CachedPrintQuery;
import org.efaps.db.Instance;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.PrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.esjp.accounting.util.Accounting;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.ci.CIContacts;
import org.efaps.esjp.ci.CIERP;
import org.efaps.esjp.ci.CIFormAccounting;
import org.efaps.esjp.ci.CISales;
import org.efaps.esjp.common.properties.PropertiesUtil;
import org.efaps.esjp.common.uiform.Field_Base.DropDownPosition;
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
        _exporter.addColumns(new FrmtColumn("number", 5));
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
        _exporter.addColumns(new FrmtColumn("marker", 1));
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
    @SuppressWarnings("checkstyle:MethodLength")
    public void buildDataSource(final Parameter _parameter,
                                final Exporter _exporter)
        throws EFapsException
    {
        final String key = PropertiesUtil.getProperty(_parameter, "Key", "");

        final DateTime dateFrom = new DateTime(_parameter.getParameterValue(
                        CIFormAccounting.Accounting_ExportJournalSC1617Form.dateFrom.name));
        final DateTime dateTo = new DateTime(_parameter.getParameterValue(
                        CIFormAccounting.Accounting_ExportJournalSC1617Form.dateTo.name));
        final Instance purchaseRecordInst = Instance.get(_parameter.getParameterValue(
                        CIFormAccounting.Accounting_ExportJournalSC1617RCForm.purchaseRecord.name));
        final Instance subJournalInst = Instance.get(_parameter.getParameterValue(
                        CIFormAccounting.Accounting_ExportJournalSC1617Form.subJournal.name));
        final String marker = _parameter.getParameterValue(
                        CIFormAccounting.Accounting_ExportJournalSC1617Form.marker.name);

        final String origin = _parameter.getParameterValue(
                        CIFormAccounting.Accounting_ExportJournalSC1617Form.origin.name);

        final Properties oProps = PropertiesUtil.getProperties4Prefix(Accounting.EXPORT_SC1617.get(), origin, false);

        final boolean analyzeRemark = BooleanUtils.toBoolean(oProps.getProperty("AnalyzeRemark", "false"));
        final boolean useDate4Number = BooleanUtils.toBoolean(oProps.getProperty("UseDate4Number", "false"));
        final boolean useOrigDoc4Number = BooleanUtils.toBoolean(oProps.getProperty("UseOrigDoc4Number", "false"));
        final boolean concatenate = BooleanUtils.toBoolean(oProps.getProperty("Concatenate", "false"));

        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.TransactionPositionAbstract);
        final QueryBuilder transAttrQueryBldr = new QueryBuilder(CIAccounting.TransactionAbstract);

        // if a purchase record was selected use it as filter
        if (InstanceUtils.isValid(purchaseRecordInst)) {
            final QueryBuilder attrQueryBuilder = new QueryBuilder(CIAccounting.PurchaseRecord2Document);
            attrQueryBuilder.addWhereAttrEqValue(CIAccounting.PurchaseRecord2Document.FromLink, purchaseRecordInst);

            final QueryBuilder attrQueryBldr = new QueryBuilder(CIAccounting.Transaction2ERPDocument);
            attrQueryBldr.addWhereAttrInQuery(CIAccounting.Transaction2ERPDocument.ToLinkAbstract,
                            attrQueryBuilder.getAttributeQuery(CIAccounting.PurchaseRecord2Document.ToLink));
            transAttrQueryBldr.addWhereAttrInQuery(CIAccounting.TransactionAbstract.ID,
                            attrQueryBldr.getAttributeQuery(CIAccounting.Transaction2ERPDocument.FromLink));
        } else {
            transAttrQueryBldr.addWhereAttrLessValue(CIAccounting.TransactionAbstract.Date,
                        dateTo.withTimeAtStartOfDay().plusDays(1));
            transAttrQueryBldr.addWhereAttrGreaterValue(CIAccounting.TransactionAbstract.Date,
                        dateFrom.withTimeAtStartOfDay().minusSeconds(1));
        }
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
        final SelectBuilder selTransInst = new SelectBuilder(selTrans).instance();
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
        final SelectBuilder selDocRev = new SelectBuilder(selDoc).attribute(CISales.DocumentSumAbstract.Revision);
        final SelectBuilder selDocDate = new SelectBuilder(selDoc).attribute(CISales.DocumentSumAbstract.Date);
        final SelectBuilder selDocDueDate = new SelectBuilder(selDoc).attribute(CISales.DocumentSumAbstract.DueDate);
        final SelectBuilder selNetTotal = new SelectBuilder(selDoc).attribute(CISales.DocumentSumAbstract.RateNetTotal);
        final SelectBuilder selCrossTotal = new SelectBuilder(selDoc)
                        .attribute(CISales.DocumentSumAbstract.RateCrossTotal);

        final SelectBuilder selContact = new SelectBuilder(selDoc).linkto(CISales.DocumentAbstract.Contact);
        final SelectBuilder selContactName = new SelectBuilder(selContact).attribute(CIContacts.Contact.Name);
        final SelectBuilder selTaxNumber = new SelectBuilder(selContact).clazz(CIContacts.ClassOrganisation)
                        .attribute(CIContacts.ClassOrganisation.TaxNumber);

        multi.addSelect(selAccName, selTransIdentifier, selTransInst, selTransName, selTransDescr, selTransDate,
                        selDocInst, selDocName, selDocDate, selDocDueDate, selContactName, selTaxNumber, selNetTotal,
                        selCrossTotal, selDocRev);
        multi.addAttribute(CIAccounting.TransactionPositionAbstract.RateAmount,
                        CIAccounting.TransactionPositionAbstract.Position,
                        CIAccounting.TransactionPositionAbstract.PositionType,
                        CIAccounting.TransactionPositionAbstract.RateCurrencyLink,
                        CIAccounting.TransactionPositionAbstract.Rate,
                        CIAccounting.TransactionPositionAbstract.Remark);
        multi.execute();
        final List<DataBean> beans = new ArrayList<>();
        while (multi.next()) {
            final Instance transInst = multi.getSelect(selTransInst);
            final String remark = multi.getAttribute(CIAccounting.TransactionPositionAbstract.Remark);
            String descr = multi.<String>getSelect(selTransDescr);
            String contactName = multi.<String>getSelect(selContactName);
            String taxNumber = multi.<String>getSelect(selTaxNumber);
            Instance docInst = multi.<Instance>getSelect(selDocInst);
            String docName = multi.<String>getSelect(selDocName);
            String docRev = multi.<String>getSelect(selDocRev);
            if (StringUtils.isNotEmpty(remark)) {
                if (concatenate) {
                    descr = descr + " " + remark;
                } else {
                    descr = remark;
                }
                if (analyzeRemark) {
                    final QueryBuilder tr2docQueryBldr = new QueryBuilder(CIAccounting.Transaction2ERPDocument);
                    tr2docQueryBldr.addWhereAttrEqValue(CIAccounting.Transaction2ERPDocument.FromLink, transInst);

                    final QueryBuilder docQueryBldr = new QueryBuilder(CIERP.DocumentAbstract);
                    docQueryBldr.addWhereAttrInQuery(CIERP.DocumentAbstract.ID, tr2docQueryBldr.getAttributeQuery(
                                    CIAccounting.Transaction2ERPDocument.ToLinkAbstract));
                    docQueryBldr.addWhereAttrEqValue(CIERP.DocumentAbstract.Name, remark);
                    final MultiPrintQuery docMulti = docQueryBldr.getCachedPrint4Request();
                    final SelectBuilder docSelContact = SelectBuilder.get().linkto(CISales.DocumentAbstract.Contact);
                    final SelectBuilder docSelContactName = new SelectBuilder(docSelContact).attribute(
                                    CIContacts.Contact.Name);
                    final SelectBuilder docSelTaxNumber = new SelectBuilder(docSelContact).clazz(
                                    CIContacts.ClassOrganisation).attribute(CIContacts.ClassOrganisation.TaxNumber);
                    docMulti.addSelect(docSelContactName, docSelTaxNumber);
                    docMulti.addAttribute(CIERP.DocumentAbstract.Revision);
                    docMulti.execute();
                    if (docMulti.getInstanceList().size() == 1) {
                        docMulti.next();
                        docInst = docMulti.getCurrentInstance();
                        contactName = docMulti.getSelect(docSelContactName);
                        taxNumber = docMulti.getSelect(docSelTaxNumber);
                        docRev = docMulti.getAttribute(CIERP.DocumentAbstract.Revision);
                        if (concatenate) {
                            descr = multi.<String>getSelect(selTransDescr) + " " + docName;
                        } else {
                            descr = docName;
                        }
                        docName = remark;
                    }
                }
            }
            final DataBean bean = new DataBean()
                            .setReportKey(key)
                            .setTransInstance(transInst)
                            .setPosInstance(multi.getCurrentInstance())
                            .setOrigin(oProps.getProperty("Value", "--"))
                            .setMarker(marker)
                            .setTransDate(multi.<DateTime>getSelect(selTransDate))
                            .setNumber(multi.getSelect(selTransIdentifier))
                            .setPosition(multi.<Integer>getAttribute(CIAccounting.TransactionPositionAbstract.Position))
                            .setAccName(multi.<String>getSelect(selAccName))
                            .setAmount(multi.<BigDecimal>getAttribute(
                                            CIAccounting.TransactionPositionAbstract.RateAmount))
                            .setTransDescr(descr)
                            .setCurrencyId(multi.<Long>getAttribute(
                                            CIAccounting.TransactionPositionAbstract.RateCurrencyLink))
                            .setOrigDocName( multi.<String>getSelect(selDocName))
                            .setDocInst(docInst)
                            .setDocName(docName)
                            .setDocRevision(docRev)
                            .setContactName(contactName)
                            .setTaxNumber(taxNumber)
                            .setDocDate(multi.<DateTime>getSelect(selDocDate))
                            .setDocDueDate(multi.<DateTime>getSelect(selDocDueDate))
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
                return _o1.getNumber().compareTo(_o2.getNumber());
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
        final Properties props = PropertiesUtil.getProperties4Prefix(Accounting.EXPORT_SC1617.get(), key);
        int i = 1;
        String currentID = "";
        String currentVal = "";
        // it must be ensured that one transaction has all the time the same number, therefore the first value wins
        for (final DataBean bean : beans) {
            if (!currentID.equals(bean.getNumber())) {
                currentID = bean.getNumber();
                // first priority are the related documents
                if (useDate4Number) {
                    currentVal = String.format("%05d", bean.getTransDate().getDayOfMonth());
                } else if (useOrigDoc4Number) {
                    currentVal = bean.getOrigDocName();
                } else {
                    final String def;
                    if (InstanceUtils.isValid(bean.getDocInst())
                                    && props.containsKey(bean.getDocInst().getType().getName() + ".Number")) {
                        def = props.getProperty(bean.getDocInst().getType().getName() + ".Number");
                    } else {
                        def = "";
                    }
                    switch (def) {
                        case "TransName":
                            currentVal = bean.getNumber();
                            break;
                        case "DocName":
                            currentVal = bean.getDocName();
                            break;
                        case "DocRevision":
                            currentVal = bean.getDocRevision();
                            break;
                        case "DocCode":
                            currentVal = bean.getDocCode();
                            break;
                        default:
                            currentVal = String.format("%05d", i);
                            i++;
                            break;
                    }
                }
            }
            bean.setNumber(currentVal);
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
     * Gets the option list for origin.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return the option list for origin
     * @throws EFapsException on error
     */
    public Return getOptionList4Origin(final Parameter _parameter)
        throws EFapsException
    {
        final List<DropDownPosition> positions = new ArrayList<>();

        final Map<Integer, String> values = PropertiesUtil.analyseProperty(Accounting.EXPORT_SC1617.get(), "Origin", 0);
        for (final String value : values.values()) {
            final Properties props = PropertiesUtil.getProperties4Prefix(Accounting.EXPORT_SC1617.get(), value);
            final String label = props.getProperty("Label");
            positions.add(new DropDownPosition(value, label, label));
        }
        final Return ret = new Return();
        ret.put(ReturnValues.VALUES, positions);
        return ret;
    }

    /**
     * The Class DataBean.
     */
    public static class DataBean
    {
        /** The report key. */
        private String reportKey;

        /** The pos instance. */
        private Instance posInstance;

        /** The pos instance. */
        private Instance transInstance;

        /**
         * The origin. (Origen, definido en sc (01: compras, 02:ventas, etc.)
         */
        private String origin;

        /** Name of the transaction Document. (Numero de Comprobante ) */
        private String number;

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
        private String origDocName;

        /** The doc name. (Numero del Documento) */
        private String docName;

        /** The doc name. (Numero del Documento) */
        private String docRevision;

        /** The doc date. */
        private DateTime docDate;

        /** The doc due date. */
        private DateTime docDueDate;

        /** The trans descr. */
        private String transDescr;

        /** The marker. TL C:Compra, V:Venta, R:Retenciones */
        private String marker;

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
                if (StringUtils.isEmpty(ret)) {
                    final Properties props = PropertiesUtil.getProperties4Prefix(Accounting.EXPORT_SC1617.get(),
                                getReportKey());
                    ret = props.getProperty(getDocInst().getType().getName() + ".DocumentType");
                }
            }
            if (StringUtils.isEmpty(ret)) {
                ret = "00";
            }
            return ret;
        }

        /**
         * Getter method for the instance variable {@link #number}.
         *
         * @return value of instance variable {@link #number}
         */
        public String getNumber()
        {
            return this.number == null ? null : StringUtils.substring(this.number, -5, this.number.length());
        }

        /**
         * Setter method for instance variable {@link #number}.
         *
         * @param _number value for instance variable {@link #number}
         * @return the data bean
         */
        public DataBean setNumber(final String _number)
        {
            this.number = _number;
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
         * @throws EFapsException on error
         */
        public BigDecimal getRate()
            throws EFapsException
        {
            BigDecimal ret = this.rate;
            if (BigDecimal.ONE.compareTo(ret) == 0 && "S".equals(this.currency)
                            && InstanceUtils.isKindOf(getDocInst(), CIERP.PaymentDocumentAbstract)) {
                    final PrintQuery print = new PrintQuery(getPosInstance());
                    print.addAttribute(CIAccounting.TransactionPositionAbstract.Remark);
                    print.execute();
                    final String remark = print.getAttribute(CIAccounting.TransactionPositionAbstract.Remark);
                    if (StringUtils.isNotEmpty(remark)) {
                        final QueryBuilder tr2docQueryBldr = new QueryBuilder(CIAccounting.Transaction2ERPDocument);
                        tr2docQueryBldr.addWhereAttrEqValue(CIAccounting.Transaction2ERPDocument.FromLink,
                                        getTransInstance());

                        final QueryBuilder docQueryBldr = new QueryBuilder(CIERP.DocumentAbstract);
                        docQueryBldr.addWhereAttrInQuery(CIERP.DocumentAbstract.ID, tr2docQueryBldr.getAttributeQuery(
                                        CIAccounting.Transaction2ERPDocument.ToLinkAbstract));
                        docQueryBldr.addWhereAttrEqValue(CIERP.DocumentAbstract.Name, remark);
                        final MultiPrintQuery docMulti = docQueryBldr.getCachedPrint4Request();
                        docMulti.execute();
                        if (docMulti.getInstanceList().size() == 1) {
                            docMulti.next();
                            final Instance relDocInst = docMulti.getCurrentInstance();

                            final QueryBuilder payQueryBldr = new QueryBuilder(CISales.Payment);
                            payQueryBldr.addWhereAttrEqValue(CISales.Payment.TargetDocument, getDocInst());
                            payQueryBldr.addWhereAttrEqValue(CISales.Payment.CreateDocument, relDocInst);
                            final MultiPrintQuery payMulti = payQueryBldr.getPrint();
                            payMulti.addAttribute(CISales.Payment.Rate);
                            payMulti.execute();
                            if (payMulti.next()) {
                                final Object rateTmp = payMulti.getAttribute(CISales.Payment.Rate);
                                final RateInfo rateInfo = RateInfo.getRateInfo((Object[]) rateTmp);
                                ret = rateInfo.getRateUI();
                            }
                        }
                    }
            }
            // check if still needs to be replaced
            if (BigDecimal.ONE.compareTo(ret) == 0 && "S".equals(this.currency)) {
                final RateInfo rateInfo = new Currency().evaluateRateInfo(new Parameter(), getTransDate(),
                                    CurrencyInst.get(UUID.fromString("691758fc-a060-4bd5-b1fa-b33296638126"))
                                                    .getInstance());
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
            return this.docDueDate == null ? getDocDate() : this.docDate;
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

        /**
         * Getter method for the instance variable {@link #docRevision}.
         *
         * @return value of instance variable {@link #docRevision}
         */
        public String getDocRevision()
        {
            return this.docRevision;
        }

        /**
         * Setter method for instance variable {@link #docRevision}.
         *
         * @param _docRevision value for instance variable {@link #docRevision}
         * @return the data bean
         */
        public DataBean setDocRevision(final String _docRevision)
        {
            this.docRevision = _docRevision;
            return this;
        }

        /**
         * Getter method for the instance variable {@link #marker}.
         *
         * @return value of instance variable {@link #marker}
         */
        public String getMarker()
        {
            return this.marker;
        }

        /**
         * Setter method for instance variable {@link #marker}.
         *
         * @param _marker value for instance variable {@link #marker}
         * @return the data bean
         */
        public DataBean setMarker(final String _marker)
        {
            this.marker = _marker;
            return this;
        }

        /**
         * Gets the doc code.
         *
         * @return the doc code
         * @throws EFapsException on error
         */
        public String getDocCode()
            throws EFapsException
        {
            String ret = getDocName();
            if (InstanceUtils.isKindOf(getDocInst(), CIERP.PaymentDocumentAbstract)) {
                final PrintQuery print = CachedPrintQuery.get4Request(getDocInst());
                print.addAttribute(CIERP.PaymentDocumentAbstract.Code);
                print.execute();
                ret = print.getAttribute(CIERP.PaymentDocumentAbstract.Code);
            }
            return ret;
        }

        /**
         * Getter method for the instance variable {@link #reportKey}.
         *
         * @return value of instance variable {@link #reportKey}
         */
        public String getReportKey()
        {
            return this.reportKey;
        }

        /**
         * Setter method for instance variable {@link #reportKey}.
         *
         * @param _reportKey value for instance variable {@link #reportKey}
         * @return the data bean
         */
        public DataBean setReportKey(final String _reportKey)
        {
            this.reportKey = _reportKey;
            return this;
        }

        /**
         * Getter method for the instance variable {@link #posInstance}.
         *
         * @return value of instance variable {@link #posInstance}
         */
        public Instance getPosInstance()
        {
            return this.posInstance;
        }

        /**
         * Setter method for instance variable {@link #posInstance}.
         *
         * @param _posInstance value for instance variable {@link #posInstance}
         * @return the data bean
         */
        public DataBean setPosInstance(final Instance _posInstance)
        {
            this.posInstance = _posInstance;
            return this;
        }

        /**
         * Getter method for the instance variable {@link #transInstance}.
         *
         * @return value of instance variable {@link #transInstance}
         */
        public Instance getTransInstance()
        {
            return this.transInstance;
        }

        /**
         * Setter method for instance variable {@link #transInstance}.
         *
         * @param _transInstance value for instance variable {@link #transInstance}
         * @return the data bean
         */
        public DataBean setTransInstance(final Instance _transInstance)
        {
            this.transInstance = _transInstance;
            return this;
        }

        /**
         * Getter method for the instance variable {@link #origDocName}.
         *
         * @return value of instance variable {@link #origDocName}
         */
        public String getOrigDocName()
        {
            return this.origDocName;
        }

        /**
         * Setter method for instance variable {@link #origDocName}.
         *
         * @param _origDocName value for instance variable {@link #origDocName}
         * @return the data bean
         */
        public DataBean setOrigDocName(final String _origDocName)
        {
            this.origDocName = _origDocName;
            return this;
        }
    }
}
