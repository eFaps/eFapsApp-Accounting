/*
 * Copyright © 2003 - 2024 The eFaps Team (-)
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
 */
package org.efaps.esjp.accounting.listener;

import org.efaps.admin.event.Parameter;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.admin.program.esjp.IEsjpListener;
import org.efaps.db.Instance;
import org.efaps.esjp.accounting.util.Accounting.Taxed4PurchaseRecord;
import org.efaps.util.EFapsException;
/**
 * Contains methods that are executed during the process of executing queries
 * against the eFaps Database like Autocompletes or MultiPrints.
 *
 * @author The eFaps Team
 * 
 */
@EFapsUUID("29fcd1de-8f95-4fb6-90e9-0f1765fa1620")
@EFapsApplication("eFapsApp-Accounting")
public interface IOnPurchaseRecord
    extends IEsjpListener
{
    /**
     * @param _parameter Parameter as passed by the eFaps API
     * @param _docInstance of the document to be evaluated
     * @return Taxed4PurchaseRecord, null if not found
     * @throws EFapsException on error
     */
    Taxed4PurchaseRecord evalTaxed(final Parameter _parameter,
                                   final Instance _docInstance)
        throws EFapsException;
}
