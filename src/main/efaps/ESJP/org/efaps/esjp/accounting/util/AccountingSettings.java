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
 * Revision:        $Rev: 9599 $
 * Last Changed:    $Date: 2013-06-12 13:56:55 -0500 (mié, 12 jun 2013) $
 * Last Changed By: $Author: jan@moxter.net $
 */


package org.efaps.esjp.accounting.util;

import org.efaps.admin.program.esjp.EFapsRevision;
import org.efaps.admin.program.esjp.EFapsUUID;


/**
 * TODO comment!
 *
 * @author The eFaps Team
 * @version $Id: SalesSettings.java 9599 2013-06-12 18:56:55Z jan@moxter.net $
 */
@EFapsUUID("3cad9ef8-22e6-4b3f-9a97-3aa984d8d6c6")
@EFapsRevision("$Rev: 9599 $")
public interface AccountingSettings
{

    /**
     * UUID's. <br/>
     * UUID's of the documents with the accounting amount configuration.
     */
    String DOCUMENT_DOCPERCONF = "org.efaps.accounting.Document2DocumentPeriodeConfig";

    /**
     * Rate currency type used for calculate debit and credit positions for a
     * transaction (ERP_CurrencyRateClient, Accounting_ERP_CurrencyRateAccounting).
     */
    String RATECURTYPE4DOCS = "org.efaps.accounting.RateCurrencyType4Documents";

}
