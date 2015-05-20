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
     * Boolean: true/false
     * The CurrencyRate for Accounting is the same as the one for sales.
     * Means only one CurrencyRate for the system is used.
     */
    String CURRATEEQ = "org.efaps.accounting.CurrencyRate4AccountingEqualsSales";

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
     * Account name for credit or debit to complete the total credit or debit amount, when the
     * difference between credit and debit is bigger than the minimum.
     */
    String PERIOD_TRANSFERACCOUNT = "TransferAccount";

    /**
     * Activate the Exchange mechanism.
     */
    String PERIOD_ACTIVATEEXCHANGE = "ActivateExchange";

    /**
     * Activate the Exchange mechanism.
     */
    String PERIOD_ACTIVATEPAYROLL = "ActivatePayroll";

    /**
     * Activate the Exchange mechanism.
     */
    String PERIOD_ACTIVATEREMARK4TRANSPOS = "ActivateRemark4TransPos";

    /**
     * Activate the Exchange mechanism.
     */
    String PERIOD_ACTIVATEVIEWS = "ActivateViews";

    /**
     * Activate the Stock mechanism.
     */
    String PERIOD_ACTIVATESTOCK = "ActivateStock";

    /**
     * Activate the mechanism for Retention and Perception.
     */
    String PERIOD_ACTIVATERETPER = "ActivateRetentionPerception";

    /**
     * Activate the mechanism for Retention and Perception.
     */
    String PERIOD_ACTIVATESECURITIES = "ActivateSecurities";

    /**
     * Activate the mechanism for Retention and Perception.
     */
    String PERIOD_SHOWREPORT = "ShowReportOnCreate";

    /**
     * Definitions of the Summarize mechanism.
     */
    String PERIOD_SUMMARIZETRANS = "SummarizeDefinition";

    /**
     * Definitions of the Summarize mechanism.
     */
    String PERIOD_LABELDEF = "LabelDefinition";

    /**
     * Name of the SubJournal to be applied for PaymentOut.
     */
    String PERIOD_SUBJOURNAL4PAYOUT = "SubJournal4PaymentOut";


    /**
     * Name of the SubJournal to be applied for PaymentIn.
     */
    String PERIOD_SUBJOURNAL4PAYIN = "SubJournal4PaymentIn";

    /**
     * Name of the SubJournal to be applied for PaymentOut.
     */
    String PERIOD_ACTIVATEPETTYCASHWD = "ActivatePettyCashWithoutDoc";

    /**
     * Name of the SubJournal to be applied for PaymentOut.
     */
    String PERIOD_ACTIVATEFTBSWD = "ActivateFundsToBeSettledWithoutDoc";


    String PERIOD_TRANSPOSORDER = "TransPosOrder";

    /**
     * Activate the mechanism for Retention and Perception.
     */
    String PERIOD_RETENTIONCASE = "Case4RetentionCertificate";

    String PERIOD_REPORT11ACCOUNT = "ReportCash11Account";

    String PERIOD_REPORT302ACCOUNT = "ReportBalance302Account";
    String PERIOD_REPORT303ACCOUNT = "ReportBalance303Account";
    String PERIOD_REPORT304ACCOUNT = "ReportBalance304Account";
    String PERIOD_REPORT305ACCOUNT = "ReportBalance305Account";
    String PERIOD_REPORT306ACCOUNT = "ReportBalance306Account";
    String PERIOD_REPORT307ACCOUNT = "ReportBalance307Account";
    String PERIOD_REPORT308ACCOUNT = "ReportBalance308Account";
    String PERIOD_REPORT309ACCOUNT = "ReportBalance309Account";
    String PERIOD_REPORT310ACCOUNT = "ReportBalance310Account";
    String PERIOD_REPORT311ACCOUNT = "ReportBalance311Account";
    String PERIOD_REPORT312ACCOUNT = "ReportBalance312Account";
    String PERIOD_REPORT313ACCOUNT = "ReportBalance313Account";
    String PERIOD_REPORT314ACCOUNT = "ReportBalance314Account";
    String PERIOD_REPORT315ACCOUNT = "ReportBalance315Account";
    String PERIOD_REPORT316ACCOUNT = "ReportBalance316Account";

}
