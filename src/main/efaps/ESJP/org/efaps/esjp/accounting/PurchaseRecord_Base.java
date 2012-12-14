package org.efaps.esjp.accounting;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import org.apache.ecs.xhtml.select;
import org.efaps.admin.access.AccessSet;
import org.efaps.admin.common.SystemConfiguration;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Return;
import org.efaps.admin.event.Parameter.ParameterValues;
import org.efaps.admin.program.esjp.EFapsRevision;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.AttributeQuery;
import org.efaps.db.Context;
import org.efaps.db.Insert;
import org.efaps.db.Instance;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.PrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.Context.FileParameter;
import org.efaps.db.SelectBuilder;
import org.efaps.db.Update;
import org.efaps.esjp.accounting.Import_Base.ImportAccount;
import org.efaps.esjp.accounting.transaction.Transaction_Base;
import org.efaps.esjp.admin.common.SystemConf;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.ci.CIERP;
import org.efaps.esjp.ci.CIFormAccounting;
import org.efaps.esjp.ci.CISales;
import org.efaps.esjp.common.uitable.MultiPrint;
import org.efaps.update.schema.common.SystemConfigurationUpdate;
import org.efaps.util.EFapsException;
import org.jfree.util.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

/**
 * TODO description!
 *
 * @author The eFasp Team
 * @version $Id$
 */
@EFapsUUID("2198d008-b65f-4d3c-b84d-48250c047708")
@EFapsRevision("$Rev$")
public abstract class PurchaseRecord_Base
{
    
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
                final QueryBuilder attrQueryBldr = new QueryBuilder(CIAccounting.PurchaseRecord2Document);
                final AttributeQuery attrQuery = attrQueryBldr.getAttributeQuery(
                                CIAccounting.PurchaseRecord2Document.ToLink);
                _queryBldr.addWhereAttrNotInQuery(CIERP.DocumentAbstract.ID, attrQuery);
            }
        };
        return multi.execute(_parameter);
    }

    public Return insertPostTrigger(final Parameter _parameter)
        throws EFapsException
    {
        Instance instance = _parameter.getInstance();
        PrintQuery print = new PrintQuery(instance);
        SelectBuilder selectDoc = new SelectBuilder().linkto(CIAccounting.PurchaseRecord2Document.ToLink).oid();
        print.addSelect(selectDoc);
        print.execute();
        String docOid = print.<String>getSelect(selectDoc);
        Instance docInst = Instance.get(docOid);
        if(docInst.isValid()){
            if(docInst.getType().isKindOf(CISales.IncomingInvoice.getType())){
                //Acccounting Configuration
                SystemConfiguration config = SystemConfiguration.get(
                                UUID.fromString("ca0a1df1-2211-45d9-97c8-07af6636a9b9"));
                
                if (config != null) {
                    Instance link = config.getLink("externalLink");
                    if (link.isValid()) {
                    Update update = new Update(instance);
                    update.add(CIAccounting.PurchaseRecord2Document.TypeLink, link.getId());
                    update.executeWithoutTrigger();
                    }
                }
            }
        }

        return new Return();
    }
}
