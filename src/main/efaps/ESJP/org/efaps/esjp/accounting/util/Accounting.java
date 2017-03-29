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

import java.util.UUID;

import org.efaps.admin.common.SystemConfiguration;
import org.efaps.admin.datamodel.IBitEnum;
import org.efaps.admin.datamodel.IEnum;
import org.efaps.admin.datamodel.attributetype.BitEnumType;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.api.annotation.EFapsSysConfAttribute;
import org.efaps.api.annotation.EFapsSysConfLink;
import org.efaps.api.annotation.EFapsSystemConfiguration;
import org.efaps.esjp.admin.common.systemconfiguration.BooleanSysConfAttribute;
import org.efaps.esjp.admin.common.systemconfiguration.PropertiesSysConfAttribute;
import org.efaps.esjp.admin.common.systemconfiguration.SysConfLink;
import org.efaps.esjp.ci.CISales;
import org.efaps.util.cache.CacheReloadException;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 */
@EFapsUUID("ded4c72c-82c2-4881-856f-ad5c579a2f14")
@EFapsApplication("eFapsApp-Accounting")
@EFapsSystemConfiguration("ca0a1df1-2211-45d9-97c8-07af6636a9b9")
public final class Accounting
{

    /** The base. */
    public static final String BASE = "org.efaps.accounting.";

    /** Accounting-Configuration. */
    public static final UUID SYSCONFUUID = UUID.fromString("ca0a1df1-2211-45d9-97c8-07af6636a9b9");

    /** See description. */
    @EFapsSysConfAttribute
    public static final BooleanSysConfAttribute CURRATEEQ = new BooleanSysConfAttribute()
                    .sysConfUUID(Accounting.SYSCONFUUID)
                    .key(Accounting.BASE + "CurrencyRate4AccountingEqualsSales")
                    .description("The CurrencyRate for Accounting is the same as the one for sales.\n"
                                    + "Means only one CurrencyRate for the system is used.");

    /** See description. */
    @EFapsSysConfLink
    public static final SysConfLink CTP4VAT = new SysConfLink()
                    .sysConfUUID(Accounting.SYSCONFUUID)
                    .key(Accounting.BASE + "CostTextPosition4VAT")
                    .description("Link to a Products_ProductCostTextPosition used for creating Positions."
                                    + "in an External Voucher applying VAT.");

    /** See description. */
    @EFapsSysConfLink
    public static final SysConfLink CTP4FREE = new SysConfLink()
                    .sysConfUUID(Accounting.SYSCONFUUID)
                    .key(Accounting.BASE + "CostTextPosition4TaxFree")
                    .description(" Link to a Products_ProductCostTextPosition used for creating Positions"
                                    + "in an External Voucher without Tax.");

    /** See description. */
    @EFapsSysConfAttribute
    public static final PropertiesSysConfAttribute CREATE4EXTERNAL_AUTOCOMPLETE4ADDDOC
        = new PropertiesSysConfAttribute()
                    .sysConfUUID(Accounting.SYSCONFUUID)
                    .key(Accounting.BASE + "Create4External.AutoComplete4AdditionalDocuments")
                    .description("Possibility to overwrite the default Autocomplete "
                                    + "for additional Document in Create4External.");

    /** See description. */
    @EFapsSysConfAttribute
    public static final PropertiesSysConfAttribute CREATE4DOC_AUTOCOMPLETE4ADDDOC
        = new PropertiesSysConfAttribute()
                    .sysConfUUID(Accounting.SYSCONFUUID)
                    .key(Accounting.BASE + "Create4Doc.AutoComplete4AdditionalDocuments")
                    .description("Possibility to overwrite the default Autocomplete "
                                    + "for additional Document in Create4Doc.");

    /** See description. */
    @EFapsSysConfAttribute
    public static final PropertiesSysConfAttribute CREATE4DOCBOOK_AUTOCOMPLETE4ADDDOC
        = new PropertiesSysConfAttribute()
                    .sysConfUUID(Accounting.SYSCONFUUID)
                    .key(Accounting.BASE + "Create4DocToBook.AutoComplete4AdditionalDocuments")
                    .description("Possibility to overwrite the default Autocomplete "
                                    + "for additional Document in Create4External.");

    /** See description. */
    @EFapsSysConfAttribute
    public static final PropertiesSysConfAttribute CREATE4EXCHANGE_AUTOCOMPLETE4ADDDOC
        = new PropertiesSysConfAttribute()
                    .sysConfUUID(Accounting.SYSCONFUUID)
                    .key(Accounting.BASE + "Create4Exchange.AutoComplete4AdditionalDocuments")
                    .description("Possibility to overwrite the default Autocomplete "
                                    + "for additional Document in Create4External.");

    /** See description. */
    @EFapsSysConfAttribute
    public static final PropertiesSysConfAttribute CREATE4EXTERNALBOOK_AUTOCOMPLETE4ADDDOC
        = new PropertiesSysConfAttribute()
                    .sysConfUUID(Accounting.SYSCONFUUID)
                    .key(Accounting.BASE + "Create4ExternalToBook.AutoComplete4AdditionalDocuments")
                    .description("Possibility to overwrite the default Autocomplete "
                                    + "for additional Document in Create4External.");

    /** See description. */
    @EFapsSysConfAttribute
    public static final PropertiesSysConfAttribute CREATE4PAYIN_AUTOCOMPLETE4ADDDOC
        = new PropertiesSysConfAttribute()
                    .sysConfUUID(Accounting.SYSCONFUUID)
                    .key(Accounting.BASE + "Create4PaymentIn.AutoComplete4AdditionalDocuments")
                    .description("Possibility to overwrite the default Autocomplete "
                                    + "for additional Document in Create4External.");

    /** See description. */
    @EFapsSysConfAttribute
    public static final PropertiesSysConfAttribute CREATE4PAYOUT_AUTOCOMPLETE4ADDDOC
        = new PropertiesSysConfAttribute()
                    .sysConfUUID(Accounting.SYSCONFUUID)
                    .key(Accounting.BASE + "Create4PaymentOut.AutoComplete4AdditionalDocuments")
                    .description("Possibility to overwrite the default Autocomplete "
                                    + "for additional Document in Create4External.");

    /** See description. */
    @EFapsSysConfAttribute
    public static final PropertiesSysConfAttribute EXPORT_SC1617
        = new PropertiesSysConfAttribute()
                    .sysConfUUID(Accounting.SYSCONFUUID)
                    .key(Accounting.BASE + "export.Siscont1617")
                    .description("Possibility to configure the export for Siscont 16/17. \n"
                                    + "(DocCode applies only to PaymentDocuments)\n"
                                    + "TYPE.Number=TransName|DocName|DocRevision|DocCode\n"
                                    + "JournalSC1617.TYPE.Number=TransName|DocName|DocRevision|DocCode\n"
                                    + "JournalSC1617RC.TYPE.Number=TransName|DocName|DocRevision|DocCode\n")
                    .addDefaultValue(CISales.IncomingInvoice.getType().getName() + ".Number", "DocName")
                    .addDefaultValue(CISales.IncomingProfServReceipt.getType().getName() + ".Number", "DocName")
                    .addDefaultValue(CISales.IncomingExchange.getType().getName() + ".Number", "DocName")
                    .addDefaultValue(CISales.PaymentDepositOut.getType().getName() + ".Number", "DocCode")
                    .addDefaultValue("JournalSC1617." + CISales.IncomingReceipt.getType().getName() + ".Number",
                                    "TransName")
                    .addDefaultValue("JournalSC1617RC." + CISales.IncomingCreditNote.getType().getName() + ".Number",
                                    "DocRevision");

    /** See description. */
    @EFapsSysConfAttribute
    public static final BooleanSysConfAttribute PERIOD_PERMITCREATE = new BooleanSysConfAttribute()
                    .sysConfUUID(Accounting.SYSCONFUUID)
                    .key(Accounting.BASE + "Period.PermitCreationOfIncomingDocs")
                    .defaultValue(true)
                    .description("Permit to create incoming documents inside Period.");

    /** See description. */
    @EFapsSysConfAttribute
    public static final PropertiesSysConfAttribute PERIOD_COLLECT_INCEXCH = new PropertiesSysConfAttribute()
                    .sysConfUUID(Accounting.SYSCONFUUID)
                    .key(Accounting.BASE + "Period.Collect.IncomingExchange")
                    .addDefaultValue("Type", "Sales_IncomingExchange")
                    .description("Permit to overwrite the MultiPrint for IncomingExchange.");

    /** See description. */
    @EFapsSysConfAttribute
    public static final PropertiesSysConfAttribute PERIOD_COLLECT_DOC2BOOK = new PropertiesSysConfAttribute()
                    .sysConfUUID(Accounting.SYSCONFUUID)
                    .key(Accounting.BASE + "Period.Collect.DocsToBook")
                    .addDefaultValue("Type01", "Sales_Invoice")
                    .addDefaultValue("Type02", "Sales_Receipt")
                    .addDefaultValue("Type03", "Sales_CreditNote")
                    .addDefaultValue("Type04", "Sales_Reminder")
                    .description("Permit to overwrite the MultiPrint for DocsToBook.");

    /** See description. */
    @EFapsSysConfAttribute
    public static final PropertiesSysConfAttribute PERIOD_COLLECT_DOC = new PropertiesSysConfAttribute()
                    .sysConfUUID(Accounting.SYSCONFUUID)
                    .key(Accounting.BASE + "Period.Collect.Document")
                    .addDefaultValue("Type01", "Sales_Invoice")
                    .addDefaultValue("Type02", "Sales_Receipt")
                    .addDefaultValue("Type03", "Sales_CreditNote")
                    .addDefaultValue("Type04", "Sales_Reminder")
                    .description("Permit to overwrite the MultiPrint for IncomingExchange.");

    /** See description. */
    @EFapsSysConfAttribute
    public static final PropertiesSysConfAttribute PERIOD_COLLECT_PAYMENT = new PropertiesSysConfAttribute()
                    .sysConfUUID(Accounting.SYSCONFUUID)
                    .key(Accounting.BASE + "Period.Collect.Payment")
                    .addDefaultValue("Type01", "Sales_PaymentCash")
                    .addDefaultValue("Type02", "Sales_PaymentCheck")
                    .addDefaultValue("Type03", "Sales_PaymentCreditCardAbstract")
                    .addDefaultValue("Type04", "Sales_PaymentDebitCardAbstract")
                    .addDefaultValue("Type05", "Sales_PaymentDeposit")
                    .addDefaultValue("Type06", "Sales_PaymentDetraction")
                    .addDefaultValue("Type07", "Sales_PaymentInternal")
                    .addDefaultValue("Type08", "Sales_PaymentRetention")
                    .description("Permit to overwrite the MultiPrint for IncomingExchange.");

    /**
     * Singelton.
     */
    private Accounting()
    {
    }

    /**
     * The Enum SubstitutorKeys.
     *
     * @author The eFaps Team
     */
    public enum SubstitutorKeys
    {

        /** The transaction date. */
        TRANSACTION_DATE,

        /** The document date. */
        DOCUMENT_DATE,

        /** The document type. */
        DOCUMENT_TYPE,

        /** The document name. */
        DOCUMENT_NAME,

        /** The product name. */
        PRODUCT_NAME,

        /** The product descr. */
        PRODUCT_DESCR;
    }

    /**
     * The Enum SubstitutorKeys.
     *
     * @author The eFaps Team
     */
    public enum RemarkSubstitutorKeys
    {
        /** The related doc name. */
        RELDOC_NAME,
        /** The realted document type. */
        RELDOC_TYPE;
    }

    /**
     * The Enum Taxed4PurchaseRecord.
     *
     */
    public enum Taxed4PurchaseRecord
        implements IEnum
    {
        /** Internal Report. */
        TAXED,
        /** Official Report. */
        EXPORT,
        /** Official Report. */
        UNTAXED;

        @Override
        public int getInt()
        {
            return ordinal();
        }
    }

    /**
     * The Enum SubJournalConfig.
     *
     */
    public enum SubJournalConfig
        implements IEnum
    {
        /** Internal Report. */
        INTERNAL,
        /** Official Report. */
        OFFICIAL;

        @Override
        public int getInt()
        {
            return ordinal();
        }
    }

    /**
     * The Enum ActDef2Case4IncomingConfig.
     *
     */
    public enum ActDef2Case4IncomingConfig
        implements IBitEnum
    {
        /** Internal Report. */
        PURCHASERECORD,
        /** Official Report. */
        TRANSACTION,
        /** Official Report. */
        SUBJOURNAL,
        /** The setstatus. */
        SETSTATUS,
        /** The withoutdoc. */
        WITHOUTDOC,
        /** The evalonpayment. */
        PERIOD4ACTIONDATE,
        /** The evalonpayment. */
        PERIOD4DOCDATE;

        /**
         * {@inheritDoc}
         */
        @Override
        public int getInt()
        {
            return BitEnumType.getInt4Index(ordinal());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int getBitIndex()
        {
            return ordinal();
        }
    }

    /**
     * The Enum ActDef2Case4DocConfig.
     *
     */
    public enum ActDef2Case4DocConfig
         implements IBitEnum
    {
        /** Official Report. */
        TRANSACTION,
        /** Official Report. */
        SUBJOURNAL,
        /** The setstatus. */
        MARKBOOKED,
        /** The evalonpayment. */
        EVALONPAYMENT,
        /** The evalonpayment. */
        PERIOD4ACTIONDATE,
        /** The evalonpayment. */
        PERIOD4DOCDATE;

        /**
         * {@inheritDoc}
         */
        @Override
        public int getInt()
        {
            return BitEnumType.getInt4Index(ordinal());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int getBitIndex()
        {
            return ordinal();
        }
    }


    /**
     * The Enum Account2AccountConfig.
     *
     */
    public enum Account2AccountConfig
        implements IBitEnum
    {
        /** Is as default selected. */
        DEACTIVATABLE,
        /** Official Report. */
        APPLY4DEBIT,
        /** Official Report. */
        APPLY4CREDIT;

        /**
         * {@inheritDoc}
         */
        @Override
        public int getInt()
        {
            return BitEnumType.getInt4Index(ordinal());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int getBitIndex()
        {
            return ordinal();
        }
    }

    /**
     * The Enum Account2CaseConfig.
     */
    public enum Account2CaseConfig
        implements IBitEnum
    {
        /** Is as default selected. */
        DEFAULTSELECTED,
        /** Official Report. */
        APPLYLABEL,
        /** Official Report. */
        EVALRELATION,
        /** Do not join the information. Applies only for product evaluations */
        SEPARATELY;

        /**
         * {@inheritDoc}
         */
        @Override
        public int getInt()
        {
            return BitEnumType.getInt4Index(ordinal());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int getBitIndex()
        {
            return ordinal();
        }
    }

    /**
     * The Enum Account2CaseConfig.
     */
    public enum Account2Case4AmountConfig
        implements IEnum
    {
        /** Net Value. */
        NET,
        /** Cross Value. */
        CROSS,
        /** Tax Value. */
        TAX;

        /**
         * {@inheritDoc}
         */
        @Override
        public int getInt()
        {
            return ordinal();
        }
    }

    /**
     * The Enum SummarizeDefinition.
     *
     */
    public enum SummarizeDefinition
    {
        /** Never summarize for this period. Default if not set. */
        NEVER,
        /** Always summarize for this period. */
        ALWAYS,
        /** Always summarize for this period. */
        ALWAYSDEBIT,
        /** Always summarize for this period. */
        ALWAYSCREDIT,
        /** Summarize is defined only by the cases. */
        CASE,
        /** Summarize is defined only by the user. */
        USER,
        /** Summarize is defined first by case but can be overwritten by the user. */
        CASEUSER;
    }

    /**
     * The Enum SummarizeConfig.
     *
     */
    public enum SummarizeConfig
        implements IEnum
    {
        /** Never summarize for this period. Default if not set. */
        NONE,
        /** Always summarize for this period. */
        DEBIT,
        /** Summarize is defined only by the cases. */
        CREDIT,
        /** Summarize is defined only by the user. */
        BOTH;

        @Override
        public int getInt()
        {
            return ordinal();
        }
    }

    /**
     * The Enum SummarizeCriteria.
     *
     */
    public enum SummarizeCriteria
        implements IEnum
    {
        /** Summarize with Account as only criteria. (default) */
        ACCOUNT,
        /** Summarize with Account and Label must be the same. */
        LABEL,
        /** Summarize with Account, Label and Remark must  be the same. */
        ALL;

        @Override
        public int getInt()
        {
            return ordinal();
        }
    }

    /**
     * The Enum LabelDefinition.
     *
     */
    public enum LabelDefinition
    {
        /** Never summarize for this period. Default if not set. */
        COST,
        /** Never summarize for this period. Default if not set. */
        COSTREQUIRED,
        /** Always summarize for this period. */
        BALANCE,
        /** Summarize is defined only by the cases. */
        BALANCEREQUIRED;
    }

    /**
     * The Enum TransPosOrder.
     *
     */
    public enum TransPosOrder
    {
        /** Sort by first Debit and than Credit over all positions by their sequences. */
        DEBITCREDIT,
        /** Sort by first Credit and than Debit over all positions by their sequences. */
        CREDITDEBIT,
        /** Sort by first Debit and than Credit and group than by "SubTransactions". */
        DEBITCREDITGROUP,
        /** Sort by first Credit and than DEBIT and group than by "SubTransactions". */
        CREDITDEBITGROUP,
        /** Sort by over all positions by the name of the account. */
        NAME,
        /** Sort positions by the name of the account and group than by "SubTransactions". */
        NAMEGROUP;;
    }

    /**
     * The Enum TransPosType.
     */
    public enum TransPosType
        implements IEnum
    {
        /** Main position. */
        MAIN,
        /** create throu a linkage. */
        CONNECTION;

        @Override
        public int getInt()
        {
            return ordinal();
        }
    }

    /**
     * The Enum ReportBalancePositionConfig.
     */
    public enum ReportBalancePositionConfig
        implements IBitEnum
    {
        /** Is Only a title. */
        TITLEONLY,
        /** Is Only a title. */
        TOTAL;

        /**
         * {@inheritDoc}
         */
        @Override
        public int getInt()
        {
            return BitEnumType.getInt4Index(ordinal());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int getBitIndex()
        {
            return ordinal();
        }
    }

    /**
     * The Enum ExchangeConfig.
     *
     */
    public enum ExchangeConfig
        implements IEnum
    {

        /** The docdatepurchase. */
        DOCDATEPURCHASE,

        /** The docdatesale. */
        DOCDATESALE,

        /** The transdatepurchase. */
        TRANSDATEPURCHASE,

        /** The transdatesale. */
        TRANSDATESALE;

        @Override
        public int getInt()
        {
            return ordinal();
        }
    }

    /**
     * The Enum CalculateConfig.
     */
    public enum CalculateConfig
        implements IEnum
    {
        /** Calculate exchange rate for selected rows. */
        EXCHANGERATE,

        /** Calculate the selected row to fill up perfectly. */
        FILLUPAMOUNT,

        /** Calculate the selected row to fit the exchange rate. */
        FITEXRATE,

        /** Summarize by the first selected row. */
        SUMMARIZE;

        @Override
        public int getInt()
        {
            return ordinal();
        }
    }

    /**
     * The Enum CalculateConfig.
     */
    public enum ArchiveConfig
        implements IEnum
    {
        /** Do nothng. */
        NONE,
        /** Document is archived. (for all time). */
        ARCHIVED,
        /** Document was entered for this Period (will show in other Periods). */
        ENTERED;

        @Override
        public int getInt()
        {
            return ordinal();
        }
    }

    /**
     * @return the SystemConfigruation for Accounting
     * @throws CacheReloadException on error
     */
    public static SystemConfiguration getSysConfig()
        throws CacheReloadException
    {
        // Accounting-Configuration
        return SystemConfiguration.get(Accounting.SYSCONFUUID);
    }
}
