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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JasperReport;

import org.apache.commons.collections.comparators.ComparatorChain;
import org.efaps.admin.datamodel.Type;
import org.efaps.admin.dbproperty.DBProperties;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Parameter.ParameterValues;
import org.efaps.admin.event.Return;
import org.efaps.admin.program.esjp.EFapsRevision;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.AttributeQuery;
import org.efaps.db.Instance;
import org.efaps.db.InstanceQuery;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.PrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.ci.CIContacts;
import org.efaps.esjp.ci.CIERP;
import org.efaps.esjp.ci.CISales;
import org.efaps.esjp.common.jasperreport.EFapsMapDataSource;
import org.efaps.esjp.common.jasperreport.StandartReport;
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
@EFapsUUID("49f0e409-000e-45f2-8e46-80dec6218365")
@EFapsRevision("$Rev$")
public abstract class PurchaseRecordReport_Base
    extends EFapsMapDataSource
{

    /**
     * Logger for this class.
     */
    protected static final Logger LOG = LoggerFactory.getLogger(PurchaseRecordReport_Base.class);

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
        /** */
        DOC_CROSSTOTAL("crossTotal"),
        /** */
        DOC_NETTOTAL("netTotal"),
        /** */
        DOC_IGV("igv"),
        /** */
        DOC_EXPORT("export"),
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
        DETRACTION_DATE("detractionDate");

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
        final List<Map<String, Object>> values = new ArrayList<Map<String, Object>>();
        final List<Instance> instances = getInstances(_parameter);

        if (instances.size() > 0) {
            final SelectBuilder selRel = new SelectBuilder().linkto(CIAccounting.PurchaseRecord2Document.ToLink);
            final SelectBuilder selRelDocType = new SelectBuilder(selRel).type();
            final SelectBuilder selRelDocInst = new SelectBuilder(selRel).instance();
            final SelectBuilder selRelDocName = new SelectBuilder(selRel).attribute(CISales.DocumentSumAbstract.Name);
            final SelectBuilder selRelDocRevision = new SelectBuilder(selRel).attribute(CISales.DocumentSumAbstract.Revision);
            final SelectBuilder selRelDocDate = new SelectBuilder(selRel).attribute(CISales.DocumentSumAbstract.Date);
            final SelectBuilder selRelDocDueDate = new SelectBuilder(selRel).attribute(CISales.DocumentSumAbstract.DueDate);
            final SelectBuilder selRelDocNTotal = new SelectBuilder(selRel).attribute(CISales.DocumentSumAbstract.NetTotal);
            final SelectBuilder selRelDocCTotal = new SelectBuilder(selRel).attribute(CISales.DocumentSumAbstract.CrossTotal);
            final SelectBuilder selRelDocRNTotal = new SelectBuilder(selRel).attribute(CISales.DocumentSumAbstract.RateNetTotal);
            final SelectBuilder selRelDocRCTotal = new SelectBuilder(selRel).attribute(CISales.DocumentSumAbstract.RateCrossTotal);
            final SelectBuilder selRelDocRateLabel = new SelectBuilder(selRel).attribute(CISales.DocumentSumAbstract.Rate).label();

            final SelectBuilder selRelDocCurInst = new SelectBuilder(selRel)
                                            .attribute(CISales.DocumentSumAbstract.CurrencyId).instance();
            final SelectBuilder selRelDocRCurInst = new SelectBuilder(selRel)
                                            .attribute(CISales.DocumentSumAbstract.RateCurrencyId).instance();

            final SelectBuilder selRelDocContact = new SelectBuilder(selRel).linkto(CISales.DocumentSumAbstract.Contact);
            final SelectBuilder selRelDocContactName = new SelectBuilder(selRelDocContact).attribute(CIContacts.Contact.Name);
            final SelectBuilder selRelDocContactTax = new SelectBuilder(selRelDocContact)
                                            .clazz(CIContacts.ClassOrganisation).attribute(CIContacts.ClassOrganisation.TaxNumber);

            final SelectBuilder selRelTypeLink = new SelectBuilder().linkto(CIAccounting.PurchaseRecord2Document.TypeLink);
            final SelectBuilder selRelTypeLinkName = new SelectBuilder(selRelTypeLink).attribute(CIERP.DocumentType.Name);

            final MultiPrintQuery multi = new MultiPrintQuery(instances);
            multi.addSelect(selRelDocType, selRelDocInst, selRelDocName, selRelDocRevision, selRelDocDate, selRelDocDueDate,
                            selRelDocNTotal, selRelDocCTotal, selRelDocRNTotal, selRelDocRCTotal, selRelDocRateLabel,
                            selRelDocCurInst, selRelDocRCurInst, selRelDocContactName, selRelDocContactTax, selRelTypeLinkName);
            multi.addAttribute(CIAccounting.PurchaseRecord2Document.DetractionDate,
                            CIAccounting.PurchaseRecord2Document.DetractionName,
                            CIAccounting.PurchaseRecord2Document.DetractionAmount);
            multi.execute();

            while (multi.next()) {
                final Map<String, Object> map = new HashMap<String, Object>();
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
                final String detractionName = multi.<String>getAttribute(CIAccounting.PurchaseRecord2Document.DetractionName);
                final BigDecimal detractionAmount = multi.<BigDecimal>getAttribute(CIAccounting.PurchaseRecord2Document.DetractionAmount);
                final DateTime detractionDate = multi.<DateTime>getAttribute(CIAccounting.PurchaseRecord2Document.DetractionDate);
                final Instance docDerivatedRel = getDocumentDerivated(instDoc, CISales.IncomingInvoice.getType());

                BigDecimal netTotal = multi.<BigDecimal>getSelect(selRelDocNTotal);
                BigDecimal crossTotal = multi.<BigDecimal>getSelect(selRelDocCTotal);
                BigDecimal igv = crossTotal.subtract(netTotal);

                final Boolean export = BigDecimal.ZERO.compareTo(igv) == 0;

                if (CISales.IncomingCreditNote.getType().equals(docType)) {
                    netTotal = netTotal.negate();
                    crossTotal = crossTotal.negate();
                    igv = igv.negate();
                }

                PurchaseRecordReport_Base.LOG.debug("Document OID '{}'", instDoc.getOid());
                PurchaseRecordReport_Base.LOG.debug("Document name '{}'", docName);

                map.put(PurchaseRecordReport_Base.Field.DOC_RATE.getKey(), rateTmp);
                map.put(PurchaseRecordReport_Base.Field.DOC_DATE.getKey(), docDate);
                map.put(PurchaseRecordReport_Base.Field.DOC_DUEDATE.getKey(), docDueDate);
                map.put(PurchaseRecordReport_Base.Field.DOC_NAME.getKey(), docName);
                map.put(PurchaseRecordReport_Base.Field.DOC_CONTACT.getKey(), contactName);
                map.put(PurchaseRecordReport_Base.Field.DOC_TAXNUM.getKey(), contactTaxNum);
                map.put(PurchaseRecordReport_Base.Field.DOC_NETTOTAL.getKey(), export ? null : netTotal);
                map.put(PurchaseRecordReport_Base.Field.DOC_CROSSTOTAL.getKey(), crossTotal);
                map.put(PurchaseRecordReport_Base.Field.DOC_IGV.getKey(), export ? null : igv);
                map.put(PurchaseRecordReport_Base.Field.DOC_EXPORT.getKey(), export ? crossTotal : null);

                final Pattern patternSN = Pattern.compile("^\\d+");
                final Matcher matcherSN = patternSN.matcher(docName);
                if (matcherSN.find()) {
                    map.put(PurchaseRecordReport_Base.Field.DOC_SN.getKey(), matcherSN.group());
                } else {
                    map.put(PurchaseRecordReport_Base.Field.DOC_SN.getKey(), docName);
                }

                final Pattern patternNo = Pattern.compile("(?<=\\D)\\d+");
                final Matcher matcherNo = patternNo.matcher(docName);
                if (matcherNo.find()) {
                    map.put(PurchaseRecordReport_Base.Field.DOC_NUMBER.getKey(), matcherNo.group());
                } else {
                    map.put(PurchaseRecordReport_Base.Field.DOC_NUMBER.getKey(), docName);
                }

                map.put(PurchaseRecordReport_Base.Field.DOC_DOCTYPE.getKey(), typeLinkName);
                map.put(PurchaseRecordReport_Base.Field.DOC_REVISION.getKey(), docRevision);
                map.put(PurchaseRecordReport_Base.Field.DETRACTION_NAME.getKey(), detractionName);
                map.put(PurchaseRecordReport_Base.Field.DETRACTION_AMOUNT.getKey(), detractionAmount);
                map.put(PurchaseRecordReport_Base.Field.DETRACTION_DATE.getKey(), detractionDate);

                if (docDerivatedRel != null && docDerivatedRel.isValid()) {
                    final SelectBuilder selLinkName = new SelectBuilder()
                                            .linkfrom(CISales.Document2DocumentType, CISales.Document2DocumentType.DocumentLink)
                                            .linkto(CISales.Document2DocumentType.DocumentTypeLink).attribute(CIERP.DocumentType.Name);

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
                    map.put(PurchaseRecordReport_Base.Field.DOCREL_TYPE.getKey(), printDocRel.<String>getSelect(selLinkName));
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
                                            final Type _type)
        throws EFapsException
    {
        Instance ret = Instance.get(null);
        if (CISales.IncomingCreditNote.getType().equals(_document.getType())
                        || CISales.IncomingReminder.getType().equals(_document.getType())) {
            final QueryBuilder attrQueryBldr = new QueryBuilder(CISales.Document2DerivativeDocument);
            attrQueryBldr.addWhereAttrEqValue(CISales.Document2DerivativeDocument.To, _document.getId());
            final AttributeQuery attrQuery = attrQueryBldr.getAttributeQuery(CISales.Document2DerivativeDocument.From);

            final QueryBuilder queryBldr = new QueryBuilder(_type);
            queryBldr.addWhereAttrInQuery(CISales.DocumentSumAbstract.ID, attrQuery);
            final InstanceQuery query = queryBldr.getQuery();
            query.execute();
            while (query.next()) {
                ret = query.getCurrentValue();
                break;
            }
        }

        return ret;
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
        final List<Instance> ret = new ArrayList<Instance>();
        final Map<String, List<Instance>> values = new TreeMap<String, List<Instance>>();

        if (_parameter.get(ParameterValues.INSTANCE) != null) {
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
     * CReate the Document Report.
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

    protected DateTime getDate4Purchase(final Parameter _parameter)
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
        // TODO Auto-generated method stub
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
}
