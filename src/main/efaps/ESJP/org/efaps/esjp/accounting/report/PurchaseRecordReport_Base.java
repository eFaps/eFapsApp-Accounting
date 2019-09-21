/*
 * Copyright 2003 - 2013 The eFaps Team
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.collections4.comparators.ComparatorChain;
import org.efaps.admin.common.SystemConfiguration;
import org.efaps.admin.datamodel.Type;
import org.efaps.admin.dbproperty.DBProperties;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Parameter.ParameterValues;
import org.efaps.admin.event.Return;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.AttributeQuery;
import org.efaps.db.Instance;
import org.efaps.db.InstanceQuery;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.PrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.QueryCache;
import org.efaps.db.SelectBuilder;
import org.efaps.esjp.accounting.util.Accounting.Taxed4PurchaseRecord;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.ci.CIContacts;
import org.efaps.esjp.ci.CIERP;
import org.efaps.esjp.ci.CISales;
import org.efaps.esjp.common.jasperreport.EFapsMapDataSource;
import org.efaps.esjp.common.jasperreport.StandartReport;
import org.efaps.esjp.erp.util.ERP;
import org.efaps.util.EFapsException;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JasperReport;
/**
 * TODO comment!
 *
 * @author The eFaps Team
 * 
 */
@EFapsUUID("49f0e409-000e-45f2-8e46-80dec6218365")
@EFapsApplication("eFapsApp-Accounting")
public abstract class PurchaseRecordReport_Base
    extends EFapsMapDataSource
{

    private static String CACHEDQUERYKEY = PurchaseRecordReport.class.getName() + "CachedQueryKey";

    /**
     * Logger for this class.
     */
    protected static final Logger LOG = LoggerFactory.getLogger(PurchaseRecordReport_Base.class);

    public enum DocState
    {
        /**
         * Registrar '1' cuando se anota el Comprobante de Pago o documento en
         * el periodo que se emitió o que se pagó el impuesto, según
         * corresponda.
         */
        NORMAL("1"),
        /**
         * Registrar '6' cuando la fecha de emisión del Comprobante de Pago o de
         * pago del impuesto es anterior al periodo de anotación y esta se
         * produce dentro de los doce meses siguientes a la emisión o pago del
         * impuesto, según corresponda.
         */
        INSIDE("6"),
        /**
         * Registrar '7' cuando la fecha de emisión del Comprobante de Pago o
         * pago del impuesto es anterior al periodo de anotación y esta se
         * produce luego de los doce meses siguientes a la emisión o pago del
         * impuesto, según corresponda.
         */
        OUSIDE("7"),
        /**
         * Registrar '9' cuando se realice un ajuste en la anotación de la
         * información de una operación registrada en un periodo anterior.
         */
        CORRECTION("9");

        private final String key;

        /**
         * @param _key key
         */
        private DocState(final String _key)
        {
            this.key = _key;
        }

        /**
         * Getter method for the instance variable {@link #key}.
         *
         * @return value of instance variable {@link #key}
         */
        public String getKey()
        {
            return this.key;
        }
    }


    /**
     * Enum used to define the keys for the map.
     */
    public enum Field
    {
        /** */
        PURCHASE_DATE("datePurchaser"),
        /** */
        DOC_REVISION("docRevision"),
        /** */
        DOC_DATE("date"),
        /** */
        DOC_STATE("docState"),
        /** */
        DOC_DUEDATE("dueDate"),
        /** */
        DOC_SN("docSerialNo"),
        /** */
        DOC_DOCTYPE("documentType"),
        /** */
        DOC_NAME("name"),
        /** */
        DOC_NUMBER("docNumber"),
        /** */
        DOC_TAXNUM("taxNumber"),
        /** */
        DOC_CONTACT("contact"),
        /**Tipo de Documento de Identidad del proveedor. */
        DOC_CONTACTDOI("contactDOI"),

        /** */
        DOC_NETTOTALTAXED("netTotalTaxed"),
        /** */
        DOC_IGVTAXED("igvTaxed"),

        /** */
        DOC_NETTOTALEXPORT("netTotalExport"),
        /** */
        DOC_IGVEXPORT("igvExport"),

        /** */
        DOC_NETTOTALUNTAXED("netTotalUntaxed"),
        /** */
        DOC_IGVUNTAXED("igvUntaxed"),

        /** */
        DOC_CROSSTOTAL("crossTotal"),


        /** */
        DOC_VALUENOTAX("valueNoTax"),
        /** */
        DOC_RATE("rate"),
        /** */
        DOCREL_DATE("derivedDocumentDate"),
        /** */
        DOCREL_TYPE("derivedDocumentType"),
        /** */
        DOCREL_PREFNAME("derivedDocumentPreffixName"),
        /** */
        DOCREL_SUFNAME("derivedDocumentSuffixName"),
        /** */
        DETRACTION_NAME("detractionName"),
        /** */
        DETRACTION_AMOUNT("detractionAmount"),
        /** */
        DETRACTION_DATE("detractionDate"),
        /** Año de emisión de la DUA o DSI. LE Column 7*/
        DUA_YEAR("duaYear"),
        /** Marca del comprobante de pago sujeto a retención. LE Column 31*/
        RETENCION_APPLIES("retencionApplies"),
        /** Número del comprobante de pago emitido por sujeto no domiciliado. */
        DOC_FOREIGNNAME("docForeignName");

        /**
         * key.
         */
        private final String key;

        /**
         * @param _key key
         */
        private Field(final String _key)
        {
            this.key = _key;
        }

        /**
         * Getter method for the instance variable {@link #key}.
         *
         * @return value of instance variable {@link #key}
         */
        public String getKey()
        {
            return this.key;
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void init(final JasperReport _jasperReport,
                     final Parameter _parameter,
                     final JRDataSource _parentSource,
                     final Map<String, Object> _jrParameters)
        throws EFapsException
    {
        QueryCache.cleanByKey(PurchaseRecordReport_Base.CACHEDQUERYKEY);

        final List<Map<String, Object>> values = new ArrayList<>();
        final List<Instance> instances = getInstances(_parameter);

        if (instances.size() > 0) {
            final Map<Instance, PosSum4Doc> posSums = getPosSums(_parameter);

            final SelectBuilder selRel = new SelectBuilder().linkto(CIAccounting.PurchaseRecord2Document.ToLink);
            final SelectBuilder selRelDocType = new SelectBuilder(selRel).type();
            final SelectBuilder selRelDocInst = new SelectBuilder(selRel).instance();
            final SelectBuilder selRelDocName = new SelectBuilder(selRel).attribute(CISales.DocumentSumAbstract.Name);
            final SelectBuilder selRelDocRevision = new SelectBuilder(selRel)
                            .attribute(CISales.DocumentSumAbstract.Revision);
            final SelectBuilder selRelDocDate = new SelectBuilder(selRel).attribute(CISales.DocumentSumAbstract.Date);
            final SelectBuilder selRelDocDueDate = new SelectBuilder(selRel)
                            .attribute(CISales.DocumentSumAbstract.DueDate);
            final SelectBuilder selRelDocNTotal = new SelectBuilder(selRel)
                            .attribute(CISales.DocumentSumAbstract.NetTotal);
            final SelectBuilder selRelDocCTotal = new SelectBuilder(selRel)
                            .attribute(CISales.DocumentSumAbstract.CrossTotal);
            final SelectBuilder selRelDocRNTotal = new SelectBuilder(selRel)
                            .attribute(CISales.DocumentSumAbstract.RateNetTotal);
            final SelectBuilder selRelDocRCTotal = new SelectBuilder(selRel)
                            .attribute(CISales.DocumentSumAbstract.RateCrossTotal);
            final SelectBuilder selRelDocRateLabel = new SelectBuilder(selRel).attribute(
                            CISales.DocumentSumAbstract.Rate).label();

            final SelectBuilder selRelDocCurInst = new SelectBuilder(selRel)
                            .attribute(CISales.DocumentSumAbstract.CurrencyId).instance();
            final SelectBuilder selRelDocRCurInst = new SelectBuilder(selRel)
                            .attribute(CISales.DocumentSumAbstract.RateCurrencyId).instance();

            final SelectBuilder selRelDocContact = new SelectBuilder(selRel)
                            .linkto(CISales.DocumentSumAbstract.Contact);
            final SelectBuilder selRelDocContactName = new SelectBuilder(selRelDocContact)
                            .attribute(CIContacts.Contact.Name);
            final SelectBuilder selRelDocContactTax = new SelectBuilder(selRelDocContact)
                            .clazz(CIContacts.ClassOrganisation).attribute(CIContacts.ClassOrganisation.TaxNumber);
            final SelectBuilder selRelDocContactIdenityCard = new SelectBuilder(selRelDocContact)
                    .clazz(CIContacts.ClassPerson).attribute(CIContacts.ClassPerson.IdentityCard);
            final SelectBuilder selRelDocContactDOIType = new SelectBuilder(selRelDocContact)
                    .clazz(CIContacts.ClassPerson).linkto(CIContacts.ClassPerson.DOITypeLink)
                    .attribute(CIContacts.AttributeDefinitionDOIType.MappingKey);

            final SelectBuilder selRelTypeLink = new SelectBuilder()
                            .linkto(CIAccounting.PurchaseRecord2Document.TypeLink);
            final SelectBuilder selRelTypeLinkName = new SelectBuilder(selRelTypeLink)
                            .attribute(CIERP.DocumentType.Name);

            final MultiPrintQuery multi = new MultiPrintQuery(instances);
            multi.addSelect(selRelDocType, selRelDocInst, selRelDocName, selRelDocRevision, selRelDocDate,
                            selRelDocDueDate,
                            selRelDocNTotal, selRelDocCTotal, selRelDocRNTotal, selRelDocRCTotal, selRelDocRateLabel,
                            selRelDocCurInst, selRelDocRCurInst, selRelDocContactName, selRelDocContactTax,
                            selRelTypeLinkName, selRelDocContactIdenityCard, selRelDocContactDOIType);
            multi.addAttribute(CIAccounting.PurchaseRecord2Document.DetractionDate,
                            CIAccounting.PurchaseRecord2Document.DetractionName,
                            CIAccounting.PurchaseRecord2Document.DetractionAmount,
                            CIAccounting.PurchaseRecord2Document.Taxed);
            multi.execute();

            while (multi.next()) {
                final Map<String, Object> map = new HashMap<>();
                final Type docType = multi.<Type>getSelect(selRelDocType);
                final Instance instDoc = multi.<Instance>getSelect(selRelDocInst);
                final String docName = multi.<String>getSelect(selRelDocName);
                final DateTime docDate = multi.<DateTime>getSelect(selRelDocDate);
                final DateTime docDueDate = multi.<DateTime>getSelect(selRelDocDueDate);
                final String contactName = multi.<String>getSelect(selRelDocContactName);
                final String contactTaxNum = multi.<String>getSelect(selRelDocContactTax);
                final BigDecimal rateTmp = multi.<BigDecimal>getSelect(selRelDocRateLabel);
                final String typeLinkName = multi.<String>getSelect(selRelTypeLinkName);
                final String docRevision = multi.<String>getSelect(selRelDocRevision);
                final String docContactIdenityCard = multi.<String>getSelect(selRelDocContactIdenityCard);
                final String docContactDOIType = multi.<String>getSelect(selRelDocContactDOIType);
                final Taxed4PurchaseRecord taxed = multi.getAttribute(CIAccounting.PurchaseRecord2Document.Taxed);

                final String detractionName = multi
                                .<String>getAttribute(CIAccounting.PurchaseRecord2Document.DetractionName);
                final BigDecimal detractionAmount = multi
                                .<BigDecimal>getAttribute(CIAccounting.PurchaseRecord2Document.DetractionAmount);
                final DateTime detractionDate = multi
                                .<DateTime>getAttribute(CIAccounting.PurchaseRecord2Document.DetractionDate);
                final Instance docDerivatedRel = getDocumentDerivated(instDoc, true) == null
                                ? getDocumentDerivated(instDoc, false) : getDocumentDerivated(instDoc, true);

                BigDecimal netTotal = multi.<BigDecimal>getSelect(selRelDocNTotal);
                BigDecimal crossTotal = multi.<BigDecimal>getSelect(selRelDocCTotal);

                final PosSum4Doc posSum = posSums.get(instDoc);
                BigDecimal taxfree;
                if (posSum != null) {
                    taxfree = posSum.getTaxFree(_parameter);
                } else {
                    taxfree = BigDecimal.ZERO;
                }
                BigDecimal igv = crossTotal.subtract(netTotal);
                netTotal = netTotal.subtract(taxfree);

                if (crossTotal.compareTo(netTotal) == 0) {
                    taxfree = netTotal;
                    netTotal = BigDecimal.ZERO;
                }

                if (CISales.IncomingCreditNote.getType().equals(docType)) {
                    netTotal = netTotal.negate();
                    crossTotal = crossTotal.negate();
                    igv = igv.negate();
                    taxfree = taxfree.negate();
                }

                PurchaseRecordReport_Base.LOG.debug("Document OID '{}'", instDoc.getOid());
                PurchaseRecordReport_Base.LOG.debug("Document name '{}'", docName);

                map.put(PurchaseRecordReport_Base.Field.DOC_RATE.getKey(), rateTmp);
                map.put(PurchaseRecordReport_Base.Field.DOC_DATE.getKey(), docDate);
                map.put(PurchaseRecordReport_Base.Field.DOC_DUEDATE.getKey(), docDueDate);
                map.put(PurchaseRecordReport_Base.Field.DOC_NAME.getKey(), docName);
                map.put(PurchaseRecordReport_Base.Field.DOC_CONTACT.getKey(), contactName);
                Boolean isDOI = false;
                String taxNum = contactTaxNum;
                if (taxNum == null || taxNum != null && taxNum.isEmpty()) {
                    if (docContactIdenityCard != null && !docContactIdenityCard.isEmpty()) {
                        taxNum = docContactIdenityCard;
                        isDOI =true;
                    }
                }
                map.put(PurchaseRecordReport_Base.Field.DOC_TAXNUM.getKey(), taxNum);

                switch (taxed) {
                    case TAXED:
                        map.put(PurchaseRecordReport_Base.Field.DOC_NETTOTALTAXED.getKey(), netTotal);
                        map.put(PurchaseRecordReport_Base.Field.DOC_IGVTAXED.getKey(), igv);
                        break;
                    case EXPORT:
                        map.put(PurchaseRecordReport_Base.Field.DOC_NETTOTALEXPORT.getKey(), netTotal);
                        map.put(PurchaseRecordReport_Base.Field.DOC_IGVEXPORT.getKey(), igv);
                        break;
                    case UNTAXED:
                        map.put(PurchaseRecordReport_Base.Field.DOC_NETTOTALUNTAXED.getKey(), netTotal);
                        map.put(PurchaseRecordReport_Base.Field.DOC_IGVUNTAXED.getKey(), igv);
                        break;
                    default:
                        break;
                }

                map.put(PurchaseRecordReport_Base.Field.DOC_CROSSTOTAL.getKey(), crossTotal);
                map.put(PurchaseRecordReport_Base.Field.DOC_VALUENOTAX.getKey(), taxfree);

                final String[] nameAr = docName.split("\\W");

                if (nameAr.length == 2 && nameAr[0].length() < nameAr[1].length()) {
                    map.put(PurchaseRecordReport_Base.Field.DOC_SN.getKey(), nameAr[0]);
                    map.put(PurchaseRecordReport_Base.Field.DOC_NUMBER.getKey(), nameAr[1]);
                } else {
                    map.put(PurchaseRecordReport_Base.Field.DOC_SN.getKey(), "");
                    map.put(PurchaseRecordReport_Base.Field.DOC_NUMBER.getKey(), docName);
                }

                map.put(PurchaseRecordReport_Base.Field.DOC_DOCTYPE.getKey(), typeLinkName);
                map.put(PurchaseRecordReport_Base.Field.DOC_REVISION.getKey(), docRevision);
                map.put(PurchaseRecordReport_Base.Field.DETRACTION_NAME.getKey(), detractionName);
                map.put(PurchaseRecordReport_Base.Field.DETRACTION_AMOUNT.getKey(), detractionAmount);
                map.put(PurchaseRecordReport_Base.Field.DETRACTION_DATE.getKey(), detractionAmount == null ? null
                                : detractionDate);

                if (docDerivatedRel != null && docDerivatedRel.isValid()) {
                    final SelectBuilder selLinkName = new SelectBuilder()
                                    .linkfrom(CISales.Document2DocumentType, CISales.Document2DocumentType.DocumentLink)
                                    .linkto(CISales.Document2DocumentType.DocumentTypeLink)
                                    .attribute(CIERP.DocumentType.Name);

                    final PrintQuery printDocRel = new PrintQuery(docDerivatedRel);
                    printDocRel.addAttribute(CISales.DocumentSumAbstract.Date,
                                    CISales.DocumentSumAbstract.Name);
                    printDocRel.addSelect(selLinkName);
                    printDocRel.execute();

                    final DateTime docRelDate = printDocRel.<DateTime>getAttribute(CISales.DocumentSumAbstract.Date);
                    final String docRelName = printDocRel.<String>getAttribute(CISales.DocumentSumAbstract.Name);

                    map.put(PurchaseRecordReport_Base.Field.DOCREL_DATE.getKey(), docRelDate);
                    map.put(PurchaseRecordReport_Base.Field.DOCREL_PREFNAME.getKey(),
                                    docRelName.split("-").length == 2 ? docRelName.split("-")[0] : "");
                    map.put(PurchaseRecordReport_Base.Field.DOCREL_SUFNAME.getKey(),
                                    docRelName.split("-").length == 2 ? docRelName.split("-")[1] : docRelName);
                    map.put(PurchaseRecordReport_Base.Field.DOCREL_TYPE.getKey(),
                                    printDocRel.<String>getSelect(selLinkName));
                }

                // TODO falta implementar
                map.put(PurchaseRecordReport_Base.Field.DUA_YEAR.getKey(), "0");
                map.put(PurchaseRecordReport_Base.Field.RETENCION_APPLIES.getKey(), false);

                if (isDOI) {
                    map.put(PurchaseRecordReport_Base.Field.DOC_CONTACTDOI.getKey(), docContactDOIType);
                } else if (contactTaxNum.length() == 11) {
                    map.put(PurchaseRecordReport_Base.Field.DOC_CONTACTDOI.getKey(), "6");
                } else {
                    map.put(PurchaseRecordReport_Base.Field.DOC_CONTACTDOI.getKey(), "0");
                }

                final DateTime purchaseDate = getDate4Purchase(_parameter);
                final Integer diff = purchaseDate.getMonthOfYear() - docDate.getMonthOfYear();

                if (Math.abs(diff) > 12) {
                    map.put(PurchaseRecordReport_Base.Field.DOC_STATE.getKey(), DocState.OUSIDE.getKey());
                } else if (Math.abs(diff) > 0) {
                    map.put(PurchaseRecordReport_Base.Field.DOC_STATE.getKey(), DocState.INSIDE.getKey());
                } else {
                    map.put(PurchaseRecordReport_Base.Field.DOC_STATE.getKey(), DocState.NORMAL.getKey());
                }

                values.add(map);
            }
        }
        final ComparatorChain chain = new ComparatorChain();
        chain.addComparator(new Comparator<Map<String, Object>>() {

            @Override
            public int compare(final Map<String, Object> _o1,
                               final Map<String, Object> _o2)
            {
                final String val1 = (String) _o1.get(PurchaseRecordReport_Base.Field.DOC_DOCTYPE.getKey());
                final String val2 = (String) _o2.get(PurchaseRecordReport_Base.Field.DOC_DOCTYPE.getKey());
                return val1.compareTo(val2);
            }
        });
        chain.addComparator(new Comparator<Map<String, Object>>() {

            @Override
            public int compare(final Map<String, Object> _o1,
                               final Map<String, Object> _o2)
            {
                final DateTime date1 = (DateTime) _o1.get(PurchaseRecordReport_Base.Field.DOC_DATE.getKey());
                final DateTime date2 = (DateTime) _o2.get(PurchaseRecordReport_Base.Field.DOC_DATE.getKey());
                return date1.compareTo(date2);
            }
        });
        chain.addComparator(new Comparator<Map<String, Object>>() {

            @Override
            public int compare(final Map<String, Object> _o1,
                               final Map<String, Object> _o2)
            {
                final String val1 = (String) _o1.get(PurchaseRecordReport_Base.Field.DOC_NAME.getKey());
                final String val2 = (String) _o2.get(PurchaseRecordReport_Base.Field.DOC_NAME.getKey());
                return val1.compareTo(val2);
            }
        });

        Collections.sort(values, chain);
        getValues().addAll(values);
    }

    protected Instance getDocumentDerivated(final Instance _document,
                                            final boolean _inverse)
        throws EFapsException
    {
        Instance ret = null;
        if (CISales.IncomingCreditNote.getType().equals(_document.getType())
                        || CISales.IncomingReminder.getType().equals(_document.getType())) {
            final QueryBuilder attrQueryBldr = new QueryBuilder(CISales.Document2DocumentAbstract);
            if (_inverse) {
                attrQueryBldr.addWhereAttrEqValue(CISales.Document2DocumentAbstract.ToAbstractLink, _document);
            } else {
                attrQueryBldr.addWhereAttrEqValue(CISales.Document2DocumentAbstract.FromAbstractLink, _document);
            }
            final AttributeQuery attrQuery;
            if (_inverse) {
                attrQuery = attrQueryBldr.getAttributeQuery(CISales.Document2DocumentAbstract.FromAbstractLink);
            } else {
                attrQuery = attrQueryBldr.getAttributeQuery(CISales.Document2DocumentAbstract.ToAbstractLink);
            }

            final QueryBuilder queryBldr = new QueryBuilder(CISales.DocumentSumAbstract);
            queryBldr.addWhereAttrInQuery(CISales.DocumentSumAbstract.ID, attrQuery);
            queryBldr.addWhereAttrEqValue(CISales.DocumentSumAbstract.Type,
                            CISales.IncomingInvoice.getType().getId(),
                            CIAccounting.ExternalVoucher.getType().getId());
            final InstanceQuery query = queryBldr.getQuery();
            query.execute();

            if (query.next()) {
                ret = query.getCurrentValue();
            }
        }

        return ret;
    }


    protected Map<Instance, PosSum4Doc> getPosSums(final Parameter _parameter)
        throws EFapsException
    {
        final Map<Instance, PosSum4Doc> ret = new HashMap<>();
        if (_parameter.getInstance() != null) {
            final Instance purchaseInst = _parameter.getInstance();
            final QueryBuilder attrQueryBldr = new QueryBuilder(CIAccounting.PurchaseRecord2Document);
            attrQueryBldr.addWhereAttrEqValue(CIAccounting.PurchaseRecord2Document.FromLink, purchaseInst);
            final AttributeQuery attrQuery = attrQueryBldr
                            .getAttributeQuery(CIAccounting.PurchaseRecord2Document.ToLink);
            final QueryBuilder queryBldr = new QueryBuilder(CISales.PositionSumAbstract);
            queryBldr.addWhereAttrInQuery(CISales.PositionSumAbstract.DocumentAbstractLink, attrQuery);
            final MultiPrintQuery multi = queryBldr.getPrint();
            final SelectBuilder selDocInst = SelectBuilder.get()
                            .linkto(CISales.PositionSumAbstract.DocumentAbstractLink).instance();
            final SelectBuilder selTaxInst = SelectBuilder.get().linkto(CISales.PositionSumAbstract.Tax).instance();
            multi.addSelect(selDocInst, selTaxInst);
            multi.addAttribute(CISales.PositionSumAbstract.CrossPrice, CISales.PositionSumAbstract.NetPrice);
            multi.execute();
            while (multi.next()) {
                final BigDecimal cross = multi.<BigDecimal>getAttribute(CISales.PositionSumAbstract.CrossPrice);
                final BigDecimal net = multi.<BigDecimal>getAttribute(CISales.PositionSumAbstract.NetPrice);
                final Instance docInst = multi.<Instance>getSelect(selDocInst);
                final Instance taxCatInst = multi.<Instance>getSelect(selTaxInst);
                if (!ret.containsKey(docInst)) {
                    ret.put(docInst, getPosSum4Doc(_parameter));
                }
                final PosSum4Doc posSum = ret.get(docInst);
                posSum.addCross(cross, taxCatInst);
                posSum.addNet(net, taxCatInst);
            }
        }
        return ret;
    }

    protected PosSum4Doc getPosSum4Doc(final Parameter _parameter)
    {
        return new PosSum4Doc();
    }

    /**
     * Method for obtains a new List with instance of the documents.
     *
     * @param _parameter Parameter as passed from the eFaps API.
     * @param _from Datetime from.
     * @param _to Datetime to.
     * @return ret with list instance.
     * @throws EFapsException on error.
     */
    protected List<Instance> getInstances(final Parameter _parameter)
        throws EFapsException
    {
        final List<Instance> ret = new ArrayList<>();
        final Map<String, List<Instance>> values = new TreeMap<>();
        if (_parameter.getInstance() != null) {
            final Instance purchaseInst = _parameter.getInstance();
            final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.PurchaseRecord2Document);
            queryBldr.addWhereAttrEqValue(CIAccounting.PurchaseRecord2Document.FromLink, purchaseInst);
            values.put("A", queryBldr.getQuery().execute());
            for (final List<Instance> instances : values.values()) {
                for (final Instance inst : instances) {
                    PurchaseRecordReport_Base.LOG.debug("PurchaseRecord2Document OID '{}'", inst.getOid());
                    ret.add(inst);
                }
            }
        }

        return ret;
    }

    /**
     * Create the Document Report.
     * @param _parameter    Parameter as passed from the eFaps API
     * @return report
     * @throws EFapsException on error
     */
    @SuppressWarnings("unchecked")
    public Return createDocReport(final Parameter _parameter)
        throws EFapsException
    {
        final Map<String, Object> props = (Map<String, Object>) _parameter.get(ParameterValues.PROPERTIES);
        String mime = "xls";
        if (props.containsKey("Mime")) {
            mime = (String) props.get("Mime");
        }

        final StandartReport report = new StandartReport();
        report.setFileName(getReportName(_parameter));
        report.getJrParameters().put("Mime", mime);
        report.getJrParameters().put("PurchaseDate", getDate4Purchase(_parameter));
        addAdditionalParameters(_parameter, report);

        return report.execute(_parameter);
    }

    /**
     * Get the date for the report.
     * @param _parameter    Parameter as passed from the eFaps API
     * @return report
     * @throws EFapsException on error
     */
    public DateTime getDate4Purchase(final Parameter _parameter)
        throws EFapsException
    {
        DateTime date = new DateTime();
        if (_parameter.get(ParameterValues.INSTANCE) != null) {
            final PrintQuery print = new PrintQuery(_parameter.getInstance());
            print.addAttribute(CIAccounting.PurchaseRecord.Date);
            print.execute();

            date = print.<DateTime>getAttribute(CIAccounting.PurchaseRecord.Date);
        }
        return date;
    }


    protected void addAdditionalParameters(final Parameter _parameter,
                                           final StandartReport report)
        throws EFapsException
    {
        final SystemConfiguration config = ERP.getSysConfig();
        if (config != null) {
            final String companyName = ERP.COMPANY_NAME.get();
            final String companyTaxNumb = ERP.COMPANY_TAX.get();

            if (companyName != null && companyTaxNumb != null && !companyName.isEmpty() && !companyTaxNumb.isEmpty()) {
                report.getJrParameters().put("CompanyName", companyName);
                report.getJrParameters().put("CompanyTaxNum", companyTaxNumb);
            }
        }
    }

    /**
     * Get the name for the report.
     *
     * @param _parameter Parameter as passed form the eFaps API
     * @param _from fromdate
     * @param _to   to date
     * @return name of the report
     */
    protected String getReportName(final Parameter _parameter)
    {
        return DBProperties.getProperty("Accounting_PurchaseRecordReport.Label", "es");
    }

    public Return updateFilterActiveUIValue(final Parameter _parameter) {
        return new Return();
    }

    public static class PosSum4Doc
    {

        private final Map<Instance, BigDecimal> taxCat2cross = new HashMap<>();

        private final Map<Instance, BigDecimal> taxCat2net = new HashMap<>();

        /**
         * @param _cross
         * @param _taxInst
         */
        public void addCross(final BigDecimal _cross,
                             final Instance _taxInst)
        {
            if (!this.taxCat2cross.containsKey(_taxInst)) {
                this.taxCat2cross.put(_taxInst, BigDecimal.ZERO);
            }
            this.taxCat2cross.put(_taxInst, this.taxCat2cross.get(_taxInst).add(_cross));
        }

        /**
         * @param _net
         * @param _taxInst
         */
        public void addNet(final BigDecimal _net,
                           final Instance _taxInst)
        {
            if (!this.taxCat2net.containsKey(_taxInst)) {
                this.taxCat2net.put(_taxInst, BigDecimal.ZERO);
            }
            this.taxCat2net.put(_taxInst, this.taxCat2net.get(_taxInst).add(_net));
        }

        /**
         * @param _parameter
         */
        public BigDecimal getTaxFree(final Parameter _parameter)
            throws EFapsException
        {
            BigDecimal ret = BigDecimal.ZERO;
            final QueryBuilder attrQueryBldr = new QueryBuilder(CISales.TaxCategory);
            attrQueryBldr.addWhereAttrEqValue(CISales.TaxCategory.UUID, "25267a7d-84a9-428f-990e-9d99b133faf4");
            final InstanceQuery query = attrQueryBldr.getCachedQuery(PurchaseRecordReport_Base.CACHEDQUERYKEY);
            query.execute();
            while (query.next()) {
                final Instance taxFreeInst = query.getCurrentValue();
                if (this.taxCat2net.containsKey(taxFreeInst)) {
                    ret = ret.add(this.taxCat2net.get(taxFreeInst));
                }
            }
            return ret;
        }
    }

}
