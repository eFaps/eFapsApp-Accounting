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
 * Revision:        $Rev: 9442 $
 * Last Changed:    $Date: 2013-05-16 18:05:46 -0500 (jue, 16 may 2013) $
 * Last Changed By: $Author: jan@moxter.net $
 */

package org.efaps.esjp.accounting.util;

import java.util.UUID;

import org.efaps.admin.common.SystemConfiguration;
import org.efaps.admin.datamodel.IBitEnum;
import org.efaps.admin.datamodel.IEnum;
import org.efaps.admin.datamodel.attributetype.BitEnumType;
import org.efaps.admin.program.esjp.EFapsRevision;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.util.cache.CacheReloadException;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 * @version $Id: TwoPlan.java 9442 2013-05-16 23:05:46Z jan@moxter.net $
 */
@EFapsUUID("ded4c72c-82c2-4881-856f-ad5c579a2f14")
@EFapsRevision("$Rev: 9442 $")
public final class Accounting
{
    /**
     * Singelton.
     */
    private Accounting()
    {
    }

    public enum SubJournalConfig
        implements IEnum
    {
        /**Internal Report. */
        INTERNAL,
        /** Official Report. */
        OFFICIAL;

        @Override
        public int getInt()
        {
            return ordinal();
        }
    }

    public enum ActDef2Case4IncomingConfig
       implements IBitEnum
    {
        /** Internal Report. */
        PURCHASERECORD,
        /** Official Report. */
        TRANSACTION,
        /** Official Report. */
        SUBJOURNAL;

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

    public enum ActDef2Case4DocConfig
         implements IBitEnum
    {
        /** Official Report. */
        TRANSACTION,
        /** Official Report. */
        SUBJOURNAL;

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

    public enum SummarizeDefinition
    {
        /** Never summarize for this period. Default if not set. */
        NEVER,
        /** Always summarize for this period. */
        ALWAYS,
        /** Summarize is defined only by the cases. */
        CASE,
        /** Summarize is defined only by the user. */
        USER,
        /** Summarize is defined first by case but can be overwritten by the user. */
        CASEUSER;
    }

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
     * @return the SystemConfigruation for Accounting
     * @throws CacheReloadException on error
     */
    public static SystemConfiguration getSysConfig()
        throws CacheReloadException
    {
        // Accounting-Configuration
        return SystemConfiguration.get(UUID.fromString("ca0a1df1-2211-45d9-97c8-07af6636a9b9"));
    }
}
