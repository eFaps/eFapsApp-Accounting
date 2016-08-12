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
import org.efaps.esjp.admin.common.systemconfiguration.SysConfLink;
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
                    .sysConfUUID(SYSCONFUUID)
                    .key(BASE + "CurrencyRate4AccountingEqualsSales")
                    .description("The CurrencyRate for Accounting is the same as the one for sales.\n"
                                    + "Means only one CurrencyRate for the system is used.");

    /** See description. */
    @EFapsSysConfLink
    public static final SysConfLink CTP4VAT = new SysConfLink()
                    .sysConfUUID(SYSCONFUUID)
                    .key(BASE + "CostTextPosition4VAT")
                    .description("Link to a Products_ProductCostTextPosition used for creating Positions."
                                    + "in an External Voucher applying VAT.");

    /** See description. */
    @EFapsSysConfLink
    public static final SysConfLink CTP4FREE = new SysConfLink()
                    .sysConfUUID(SYSCONFUUID)
                    .key(BASE + "CostTextPosition4TaxFree")
                    .description(" Link to a Products_ProductCostTextPosition used for creating Positions"
                                    + "in an External Voucher without Tax.");

    /**
     * Singelton.
     */
    private Accounting()
    {
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
        WITHOUTDOC;

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
        SETSTATUS,

        /** The evalonpayment. */
        EVALONPAYMENT;

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
        EVALRELATION;

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
     * The Enum SubJournalConfig.
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
     * @return the SystemConfigruation for Accounting
     * @throws CacheReloadException on error
     */
    public static SystemConfiguration getSysConfig()
        throws CacheReloadException
    {
        // Accounting-Configuration
        return SystemConfiguration.get(SYSCONFUUID);
    }
}
