package org.efaps.esjp.accounting;

import java.math.BigDecimal;
import java.util.List;

import org.efaps.admin.common.SystemConfiguration;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Return;
import org.efaps.admin.program.esjp.EFapsRevision;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.AttributeQuery;
import org.efaps.db.Insert;
import org.efaps.db.Instance;
import org.efaps.db.InstanceQuery;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.PrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.db.Update;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.ci.CIERP;
import org.efaps.esjp.ci.CISales;
import org.efaps.esjp.common.jasperreport.StandartReport;
import org.efaps.esjp.common.uitable.MultiPrint;
import org.efaps.esjp.erp.util.ERP;
import org.efaps.esjp.erp.util.ERPSettings;
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
@EFapsRevision("$Rev$")
public abstract class PurchaseRecord_Base
{

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

    public Return insertPostTrigger(final Parameter _parameter)
        throws EFapsException
    {
        final Instance instance = _parameter.getInstance();
        final PrintQuery print = new PrintQuery(instance);
        final SelectBuilder selectDoc = new SelectBuilder().linkto(CIAccounting.PurchaseRecord2Document.ToLink).oid();
        print.addSelect(selectDoc);
        print.execute();
        final String docOid = print.<String>getSelect(selectDoc);
        final Instance docInst = Instance.get(docOid);
        if (docInst.isValid()) {
            final PrintQuery printDocQuery = new PrintQuery(docInst);
            final SelectBuilder selDocTypeIns = new SelectBuilder()
                            .linkfrom(CISales.Document2DocumentType, CISales.Document2DocumentType.DocumentLink)
                            .linkto(CISales.Document2DocumentType.DocumentTypeLink).instance();
            printDocQuery.addSelect(selDocTypeIns);
            printDocQuery.executeWithoutAccessCheck();
            final Instance docTypeIns = printDocQuery.<Instance>getSelect(selDocTypeIns);
            if (docTypeIns != null && docTypeIns.isValid()) {
                final Update update = new Update(instance);
                update.add(CIAccounting.PurchaseRecord2Document.TypeLink, docTypeIns.getId());
                update.executeWithoutTrigger();
            }

            final QueryBuilder docAttrBldr = new QueryBuilder(CISales.Payment);
            docAttrBldr.addWhereAttrEqValue(CISales.Payment.CreateDocument, docInst.getId());
            final AttributeQuery attrQuery = docAttrBldr.getAttributeQuery(CISales.Payment.TargetDocument);

            final QueryBuilder queryBldr = new QueryBuilder(CISales.PaymentDetractionOut);
            queryBldr.addWhereAttrInQuery(CISales.PaymentDetractionOut.ID, attrQuery);
            final MultiPrintQuery multi = queryBldr.getPrint();
            multi.addAttribute(CISales.PaymentDetractionOut.Date, CISales.PaymentDetractionOut.Name,
                            CISales.PaymentDetractionOut.Amount);
            multi.executeWithoutAccessCheck();
            if (multi.next()) {
                final DateTime date = multi.<DateTime>getAttribute(CISales.PaymentDetractionOut.Date);
                final String name = multi.<String>getAttribute(CISales.PaymentDetractionOut.Name);
                final BigDecimal amount = multi.<BigDecimal>getAttribute(CISales.PaymentDetractionOut.Amount);
                final Update update = new Update(instance);
                update.add(CIAccounting.PurchaseRecord2Document.DetractionName, name);
                update.add(CIAccounting.PurchaseRecord2Document.DetractionDate, date);
                update.add(CIAccounting.PurchaseRecord2Document.DetractionAmount, amount);
                update.executeWithoutTrigger();
            }
        }
        return new Return();
    }

    public Return printReport(final Parameter _parameter)
        throws EFapsException
    {
        final StandartReport report = new StandartReport();

        final SystemConfiguration config = ERP.getSysConfig();
        if (config != null) {
            final String companyName = config.getAttributeValue(ERPSettings.COMPANYNAME);
            final String companyTaxNumb = config.getAttributeValue(ERPSettings.COMPANYTAX);

            if (companyName != null && companyTaxNumb != null && !companyName.isEmpty() && !companyTaxNumb.isEmpty()) {
                report.getJrParameters().put("CompanyName", companyName);
                report.getJrParameters().put("CompanyTaxNum", companyTaxNumb);
            }
        }

        return report.execute(_parameter);
    }

    public void connectDocuments(final Parameter _parameter,
                                 final Instance... _docInsts)
        throws EFapsException
    {
        final Instance purchaseRecInst = Instance.get(_parameter.getParameterValue("purchaseRecord"));
        if (purchaseRecInst.isValid()) {
            connectDocuments(_parameter, purchaseRecInst, _docInsts);
        }
    }

    public void connectDocuments(final Parameter _parameter,
                                 final Instance _purchaseRecInst,
                                 final Instance... _docInsts)
        throws EFapsException
    {
        if (_purchaseRecInst != null && _docInsts != null && _purchaseRecInst.isValid()) {
            for (final Instance docInst : _docInsts) {
                final PrintQuery print = new PrintQuery(docInst);
                final SelectBuilder sel = SelectBuilder.get()
                                .linkfrom(CISales.Document2DocumentType.DocumentLink)
                                .linkto(CISales.Document2DocumentType.DocumentTypeLink)
                                .attribute(CIERP.DocumentType.Configuration);
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
                        purInsert.execute();
                    }
                }
            }
        }
    }

}
