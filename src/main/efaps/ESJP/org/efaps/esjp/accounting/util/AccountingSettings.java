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
 * @version $Id: AccountingSettings.java 9599 2013-06-12 18:56:55Z jan@moxter.net $
 */
@EFapsUUID("21f06c50-bd20-4ed5-8325-3df5426e5986")
@EFapsRevision("$Rev: 9599 $")
public interface AccountingSettings
{

    /**
     * Rate currency type used for calculate debit and credit positions for a
     * transaction (ERP_CurrencyRateClient, Accounting_ERP_CurrencyRateAccounting).
     */
    String RATECURTYPE4DOCS = "org.efaps.accounting.RateCurrencyType4Documents";

    /**
     * Link to a Products_ProductCostTextPosition used for creating Positions
     * in an External Voucher applying VAT.
     */
    String CTP4VAT = "org.efaps.accounting.CostTextPosition4VAT";

    /**
     * Link to a Products_ProductCostTextPosition used for creating Positions
     * in an External Voucher without Tax.
     */
    String CTP4FREE = "org.efaps.accounting.CostTextPosition4TaxFree";


    //////////////////////////////////////////////////////////////////////////////////////////////
    //Setting belonging to a period, therefore only one word and not a org.efaps...
    ////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Account name for credit to round when the difference between credit and debit is minimum.
     */
    String PERIOD_NAME = "Name";

    /**
     * Account name for credit to round when the difference between credit and debit is minimum.
     */
    String PERIOD_ROUNDINGCREDIT = "RoundingCreditAccount";

    /**
     * Account name for debit to round when the difference between credit and debit is minimum.
     */
    String PERIOD_ROUNDINGDEBIT = "RoundingDebitAccount";

    /**
     * Account name for gain on recalculation of exchange rates between currencies..
     */
    String PERIOD_EXCHANGELOSS = "ExchangeLossAccount";

    /**
     * Account name for gain on recalculation of exchange rates between currencies..
     */
    String PERIOD_EXCHANGEGAIN = "ExchangeGainAccount";

    /**
     * Maximum difference amount between credit and debit with the document tota amount.
     */
    String PERIOD_ROUNDINGMAXAMOUNT = "RoundingMaxAmount";

    /**
     * Account name for debit to round when the difference between credit and debit is minimum.
     */
    String PERIOD_EXVATACCOUNT = "ExternalVATAccount";

    /**
     * Account name for credit or debit to complete the total credit or debit amount, when the
     * difference between credit and debit is bigger than the minimum.
     */
    String PERIOD_TRANSFERACCOUNT = "TransferAccount";

    /**
     * Activate the Exchange mechanism.
     */
    String PERIOD_ACTIVATEEXCHANGE = "ActivateExchange";

}
