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
package org.efaps.esjp.accounting;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.EnumUtils;
import org.efaps.admin.common.SystemConfiguration;
import org.efaps.admin.dbproperty.DBProperties;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Parameter.ParameterValues;
import org.efaps.admin.event.Return;
import org.efaps.admin.event.Return.ReturnValues;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.admin.program.esjp.Listener;
import org.efaps.db.AttributeQuery;
import org.efaps.db.CachedMultiPrintQuery;
import org.efaps.db.Delete;
import org.efaps.db.Insert;
import org.efaps.db.Instance;
import org.efaps.db.InstanceQuery;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.PrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.db.Update;
import org.efaps.esjp.accounting.listener.IOnPurchaseRecord;
import org.efaps.esjp.accounting.util.Accounting.Taxed4PurchaseRecord;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.ci.CIERP;
import org.efaps.esjp.ci.CISales;
import org.efaps.esjp.common.jasperreport.StandartReport;
import org.efaps.esjp.common.uitable.MultiPrint;
import org.efaps.esjp.common.util.InterfaceUtils;
import org.efaps.esjp.db.InstanceUtils;
import org.efaps.esjp.erp.CommonDocument;
import org.efaps.esjp.erp.util.ERP;
import org.efaps.esjp.sales.document.AbstractDocumentTax;
import org.efaps.esjp.sales.document.AbstractDocumentTax_Base;
import org.efaps.esjp.sales.document.AbstractDocumentTax_Base.DocTaxInfo;
import org.efaps.util.EFapsException;
import org.joda.time.DateTime;

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

/**
 * TODO description!
 *
 * @author The eFasp Team
 * @version $Id: PurchaseRecord_Base.java 13599 2014-08-13 15:24:38Z
 *          jan@moxter.net $
 */
@EFapsUUID("2198d008-b65f-4d3c-b84d-48250c047708")
@EFapsApplication("eFapsApp-Accounting")
public abstract class PurchaseRecord_Base
    extends CommonDocument
{

    /**
     * Used to store the PerceptionValue in the Context.
     */
    public static final String REQKEY4DOCTAXINFO = PurchaseRecord.class.getName() + ".RequestKey4DocTaxInfo";

    /**
     * @param _parameter Parameter as passed by the eFaps API
     * @return Return containg the map for multi
     * @throws EFapsException on error
     */
    public Return documentMultiPrint(final Parameter _parameter)
        throws EFapsException
    {
        final MultiPrint multi = new MultiPrint()
        {

            @Override
            protected void add2QueryBldr(final Parameter _parameter,
                                         final QueryBuilder _queryBldr)
                throws EFapsException
            {
                // not yet used in a purchaserecord
                final QueryBuilder attrQueryBldr = new QueryBuilder(CIAccounting.PurchaseRecord2Document);
                final AttributeQuery attrQuery = attrQueryBldr.getAttributeQuery(
                                CIAccounting.PurchaseRecord2Document.ToLink);
                _queryBldr.addWhereAttrNotInQuery(CIERP.DocumentAbstract.ID, attrQuery);

                // only the ones that have a documentype assigned that is
                // configured for puchaserecord
                final QueryBuilder docTypeQueryBldr = new QueryBuilder(CIERP.DocumentType);
                docTypeQueryBldr.addWhereAttrEqValue(CIERP.DocumentType.Configuration,
                                ERP.DocTypeConfiguration.PURCHASERECORD);

                final QueryBuilder relQueryBldr = new QueryBuilder(CIERP.Document2DocumentTypeAbstract);
                relQueryBldr.addWhereAttrInQuery(CIERP.Document2DocumentTypeAbstract.DocumentTypeLinkAbstract,
                                docTypeQueryBldr.getAttributeQuery(CIERP.DocumentType.ID));
                _queryBldr.addWhereAttrInQuery(CIERP.DocumentAbstract.ID,
                                relQueryBldr.getAttributeQuery(
                                                CIERP.Document2DocumentTypeAbstract.DocumentLinkAbstract));
            }
        };
        return multi.execute(_parameter);
    }

    /**
     * Insert post trigger.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return the return
     * @throws EFapsException on error
     */
    public Return insertPostTrigger(final Parameter _parameter)
        throws EFapsException
    {
        updatePurchaseRecord2Document(_parameter, _parameter.getInstance());
        return new Return();
    }

    /**
     * Update purchase record.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return the return
     * @throws EFapsException on error
     */
    public Return updatePurchaseRecord(final Parameter _parameter)
        throws EFapsException
    {
        final Instance instance = _parameter.getInstance();
        if (instance != null && instance.isValid() && CIAccounting.PurchaseRecord.isType(instance.getType())) {
            final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.PurchaseRecord2Document);
            queryBldr.addWhereAttrEqValue(CIAccounting.PurchaseRecord2Document.FromLink, instance);
            final InstanceQuery query = queryBldr.getQuery();
            query.execute();
            while (query.next()) {
                updatePurchaseRecord2Document(_parameter, query.getCurrentValue());
            }
        }
        return new Return();
    }

    /**
     * Update purchase record two document.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @param _instance the instance
     * @throws EFapsException on error
     */
    protected void updatePurchaseRecord2Document(final Parameter _parameter,
                                                 final Instance _instance)
        throws EFapsException
    {
        final SelectBuilder selectDocInst = new SelectBuilder()
                        .linkto(CIAccounting.PurchaseRecord2Document.ToLink).instance();
        final PrintQuery print = new PrintQuery(_instance);
        print.addSelect(selectDocInst);
        print.execute();

        final Instance docInst = print.<Instance>getSelect(selectDocInst);
        if (docInst != null && docInst.isValid()) {
            final Taxed4PurchaseRecord taxed = evalTaxed(_parameter, docInst);
            final Update update1 = new Update(_instance);
            update1.add(CIAccounting.PurchaseRecord2Document.Taxed, taxed);
            update1.executeWithoutTrigger();

            final PrintQuery printDocQuery = new PrintQuery(docInst);
            final SelectBuilder selDocTypeIns = new SelectBuilder()
                            .linkfrom(CISales.Document2DocumentType, CISales.Document2DocumentType.DocumentLink)
                            .linkto(CISales.Document2DocumentType.DocumentTypeLink).instance();
            printDocQuery.addSelect(selDocTypeIns);
            printDocQuery.executeWithoutAccessCheck();
            final Instance docTypeIns = printDocQuery.<Instance>getSelect(selDocTypeIns);
            if (docTypeIns != null && docTypeIns.isValid()) {
                final Update update = new Update(_instance);
                update.add(CIAccounting.PurchaseRecord2Document.TypeLink, docTypeIns);
                update.executeWithoutTrigger();
            }

            final DocTaxInfo docTaxInfo = AbstractDocumentTax_Base.getDocTaxInfo(_parameter, docInst);
            final Update update = new Update(_instance);
            String detrName = null;
            DateTime detrDate = null;
            BigDecimal detrAmount = null;
            if (docTaxInfo.isDetractionPaid()) {
                detrName = docTaxInfo.getPaymentName();
                detrDate = docTaxInfo.getPaymentDate();
                detrAmount = docTaxInfo.getPaymentAmount();
            } else if (docTaxInfo.isDetraction()) {
                detrName = DBProperties.getFormatedDBProperty(PurchaseRecord.class.getName()
                                                + ".detractionPlanned", (Object) docTaxInfo.getTaxAmount());
            }
            update.add(CIAccounting.PurchaseRecord2Document.DetractionName, detrName);
            update.add(CIAccounting.PurchaseRecord2Document.DetractionDate, detrDate);
            update.add(CIAccounting.PurchaseRecord2Document.DetractionAmount, detrAmount);
            update.executeWithoutTrigger();
        }
    }

    /**
     * Prints the report.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return the return
     * @throws EFapsException on error
     */
    public Return printReport(final Parameter _parameter)
        throws EFapsException
    {
        final StandartReport report = new StandartReport();

        final SystemConfiguration config = ERP.getSysConfig();
        if (config != null) {
            final String companyName = ERP.COMPANY_NAME.get();
            final String companyTaxNumb = ERP.COMPANY_TAX.get();

            if (companyName != null && companyTaxNumb != null && !companyName.isEmpty() && !companyTaxNumb.isEmpty()) {
                report.getJrParameters().put("CompanyName", companyName);
                report.getJrParameters().put("CompanyTaxNum", companyTaxNumb);
            }
        }

        return report.execute(_parameter);
    }

    /**
     * Connect documents.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @param _docInsts the doc insts
     * @throws EFapsException on error
     */
    public void connectDocuments(final Parameter _parameter,
                                 final Instance... _docInsts)
        throws EFapsException
    {
        final Instance purchaseRecInst = Instance.get(_parameter.getParameterValue("purchaseRecord"));
        if (purchaseRecInst.isValid()) {
            connectDocuments(_parameter, purchaseRecInst, _docInsts);
        }
    }

    /**
     * Connect documents.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @param _purchaseRecInst the purchase rec inst
     * @param _docInsts the doc insts
     * @throws EFapsException on error
     */
    public void connectDocuments(final Parameter _parameter,
                                 final Instance _purchaseRecInst,
                                 final Instance... _docInsts)
        throws EFapsException
    {
        if (_purchaseRecInst != null && _docInsts != null && _purchaseRecInst.isValid()) {
            for (final Instance docInst : _docInsts) {
                if (InstanceUtils.isKindOf(docInst, CIERP.DocumentAbstract)) {
                    final Taxed4PurchaseRecord taxed = evalTaxed(_parameter, docInst);

                    final PrintQuery print = new PrintQuery(docInst);
                    final SelectBuilder sel = SelectBuilder.get().linkfrom(CISales.Document2DocumentType.DocumentLink)
                                    .linkto(CISales.Document2DocumentType.DocumentTypeLink).attribute(
                                                    CIERP.DocumentType.Configuration);
                    print.addSelect(sel);
                    print.executeWithoutAccessCheck();
                    final List<ERP.DocTypeConfiguration> configs = print.getSelect(sel);
                    if (configs != null && configs.contains(ERP.DocTypeConfiguration.PURCHASERECORD)) {
                        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.PurchaseRecord2Document);
                        queryBldr.addWhereAttrEqValue(CIAccounting.PurchaseRecord2Document.ToLink, docInst);
                        final InstanceQuery query = queryBldr.getQuery();
                        if (query.executeWithoutAccessCheck().isEmpty()) {
                            final Insert purInsert = new Insert(CIAccounting.PurchaseRecord2Document);
                            purInsert.add(CIAccounting.PurchaseRecord2Document.FromLink, _purchaseRecInst);
                            purInsert.add(CIAccounting.PurchaseRecord2Document.ToLink, docInst);
                            purInsert.add(CIAccounting.PurchaseRecord2Document.Taxed, taxed);
                            purInsert.execute();
                        }
                    }
                }
            }
        }
    }

    /**
     * Eval taxed.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @param _docInstance Instance of the document to be evaluated
     * @return the taxed for purchase record
     * @throws EFapsException on error
     */
    protected Taxed4PurchaseRecord evalTaxed(final Parameter _parameter,
                                             final Instance _docInstance)
        throws EFapsException
    {
        Taxed4PurchaseRecord ret = Taxed4PurchaseRecord.TAXED;
        // let others participate
        for (final IOnPurchaseRecord listener : Listener.get().<IOnPurchaseRecord>invoke(IOnPurchaseRecord.class)) {
            final Taxed4PurchaseRecord rettmp = listener.evalTaxed(_parameter, _docInstance);
            if (rettmp != null) {
                ret = rettmp;
            }
        }
        return ret;
    }

    /**
     * Sets the taxed value.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return empty Return
     * @throws EFapsException on error
     */
    public Return setTaxedValue(final Parameter _parameter)
        throws EFapsException
    {
        final String[] oids = (String[]) _parameter.get(ParameterValues.OTHERS);
        final Taxed4PurchaseRecord taxed = EnumUtils.getEnum(Taxed4PurchaseRecord.class,
                        getProperty(_parameter, "Taxed"));
        if (taxed != null && oids != null) {
            for (final String oid : oids) {
                final Update update = new Update(oid);
                update.add(CIAccounting.PurchaseRecord2Document.Taxed, taxed);
                update.execute();
            }
        }
        return new Return();
    }

    /**
     * Configure.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return empty Return
     * @throws EFapsException on error
     */
    public Return configure(final Parameter _parameter)
        throws EFapsException
    {
        @SuppressWarnings("unchecked")
        final Map<String, String> oidMap = (Map<String, String>) _parameter.get(ParameterValues.OIDMAP4UI);
        final String[] rowKeys = InterfaceUtils.getRowKeys(_parameter, "project", "project");
        final String[] projects = _parameter.getParameterValues("project");
        if (projects != null) {
            final Set<Instance> validInst = new HashSet<>();
            for (int i = 0; i < projects.length; i++) {
                final Instance inst = Instance.get(oidMap.get(rowKeys[i]));
                final Instance projInst = Instance.get(projects[i]);
                final Update update;
                if (inst.isValid()) {
                    update = new Update(inst);
                } else {
                    update = new Insert(CIAccounting.PurchaseRecordConfigProjectUntaxed);
                }
                if (projInst.isValid()) {
                    update.add(CIAccounting.PurchaseRecordConfigProjectUntaxed.Projectlink, projInst);
                }
                update.execute();
                validInst.add(update.getInstance());
            }
            final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.PurchaseRecordConfigProjectUntaxed);
            queryBldr.addWhereAttrNotEqValue(CIAccounting.PurchaseRecordConfigProjectUntaxed.ID, validInst.toArray());
            final InstanceQuery query = queryBldr.getQuery();
            query.execute();
            while (query.next()) {
                new Delete(query.getCurrentValue()).execute();
            }
        }
        return new Return();
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
        final MultiPrintQuery multi = CachedMultiPrintQuery.get4Request(instances);
        final SelectBuilder sel = SelectBuilder.get().linkto(CIAccounting.PurchaseRecord2Document.ToLink).instance();
        multi.addSelect(sel);
        multi.execute();
        final Map<Instance, Instance> rel2doc = new HashMap<>();
        while (multi.next()) {
            rel2doc.put(multi.getCurrentInstance(), multi.<Instance>getSelect(sel));
        }

        AbstractDocumentTax.evaluateDocTaxInfo(_parameter, new ArrayList<>(rel2doc.values()));
        final StringBuilder html = AbstractDocumentTax.getSmallTaxField4Doc(_parameter,
                        rel2doc.get(_parameter.getInstance()));
        ret.put(ReturnValues.SNIPLETT, html.toString());
        return ret;
    }
}
