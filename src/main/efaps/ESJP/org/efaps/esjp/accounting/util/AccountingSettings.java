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


package org.efaps.esjp.accounting.util;

import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;


/**
 * TODO comment!
 *
 * @author The eFaps Team
 */
@EFapsUUID("21f06c50-bd20-4ed5-8325-3df5426e5986")
@EFapsApplication("eFapsApp-Accounting")
public interface AccountingSettings
{

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
     * Activate the mechanism for Swap.
     */
    String PERIOD_ACTIVATESWAP = "ActivateSwap";

    /**
     * Activate the mechanism for Retention and Perception.
     */
    String PERIOD_ACTIVATESECURITIES = "ActivateSecurities";

    /**
     * Activate the mechanism for Retention and Perception.
     */
    String PERIOD_INCOMINGPERWITHDOC = "IncomingPerceptionWithDocument";

    /**
     * Activate the mechanism for Retention and Perception.
     */
    String PERIOD_INCOMINGPERACC = "IncomingPerceptionAccount";

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

    /**
     * Activate the TreeView relation for Cases.
     */
    String PERIOD_CASEACTIVATETREEVIEW = "Case.ActivateTreeView";

    /**
     * Activate the TreeView relation for Cases.
     */
    String PERIOD_CASEACTIVATEFAMILY = "Case.ActivateFamily";

    /**
     * Activate the TreeView relation for Cases.
     */
    String PERIOD_CASEACTIVATECATEGORY = "Case.ActivateCategory";

    /**
     * Activate the TreeView relation for Cases.
     */
    String PERIOD_CASEACTIVATEKEY = "Case.ActivateKey";

    /**
     * Activate the TreeView relation for Cases.
     */
    String PERIOD_CASEACTIVATECLASSIFICATION = "Case.ActivateClassification";

    /**
     * Activate the mechanism for Retention and Perception.
     */
    String PERIOD_RETENTIONCASE = "Case.RetentionCertificate";

    /** The period transposorder. */
    String PERIOD_TRANSPOSORDER = "TransPosOrder";

    /** The PERIO d_ repor t11 account. */
    String PERIOD_REPORT11ACCOUNT = "ReportCash11Account";

    /** The PERIO d_ repor t302 account. */
    String PERIOD_REPORT302ACCOUNT = "ReportBalance302Account";

    /** The PERIO d_ repor t303 account. */
    String PERIOD_REPORT303ACCOUNT = "ReportBalance303Account";

    /** The PERIO d_ repor t304 account. */
    String PERIOD_REPORT304ACCOUNT = "ReportBalance304Account";

    /** The PERIO d_ repor t305 account. */
    String PERIOD_REPORT305ACCOUNT = "ReportBalance305Account";

    /** The PERIO d_ repor t306 account. */
    String PERIOD_REPORT306ACCOUNT = "ReportBalance306Account";

    /** The PERIO d_ repor t307 account. */
    String PERIOD_REPORT307ACCOUNT = "ReportBalance307Account";

    /** The PERIO d_ repor t308 account. */
    String PERIOD_REPORT308ACCOUNT = "ReportBalance308Account";

    /** The PERIO d_ repor t309 account. */
    String PERIOD_REPORT309ACCOUNT = "ReportBalance309Account";

    /** The PERIO d_ repor t310 account. */
    String PERIOD_REPORT310ACCOUNT = "ReportBalance310Account";

    /** The PERIO d_ repor t311 account. */
    String PERIOD_REPORT311ACCOUNT = "ReportBalance311Account";

    /** The PERIO d_ repor t312 account. */
    String PERIOD_REPORT312ACCOUNT = "ReportBalance312Account";

    /** The PERIO d_ repor t313 account. */
    String PERIOD_REPORT313ACCOUNT = "ReportBalance313Account";

    /** The PERIO d_ repor t314 account. */
    String PERIOD_REPORT314ACCOUNT = "ReportBalance314Account";

    /** The PERIO d_ repor t315 account. */
    String PERIOD_REPORT315ACCOUNT = "ReportBalance315Account";

    /** The PERIO d_ repor t316 account. */
    String PERIOD_REPORT316ACCOUNT = "ReportBalance316Account";

}
