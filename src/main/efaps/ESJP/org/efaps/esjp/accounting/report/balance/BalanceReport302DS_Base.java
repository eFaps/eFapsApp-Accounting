/*
 * Copyright 2003 - 2014 The eFaps Team
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

package org.efaps.esjp.accounting.report.balance;

import org.efaps.admin.event.Parameter;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.Instance;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.PrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.esjp.accounting.report.balance.BalanceReport302DS_Base.Bean32;
import org.efaps.esjp.accounting.util.AccountingSettings;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.ci.CIERP;
import org.efaps.esjp.ci.CISales;
import org.efaps.util.EFapsException;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 */
@EFapsUUID("073f9604-af24-4010-b706-5612bd5fdb84")
@EFapsApplication("eFapsApp-Accounting")
public abstract class BalanceReport302DS_Base
    extends AbstractBalanceReportDS<Bean32>
{

    /**
     * {@inheritDoc}
     */
    @Override
    public Bean32 getBean(final Parameter _parameter)
        throws EFapsException
    {
        return new Bean32();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getKey(final Parameter _parameter)
        throws EFapsException
    {
        return AccountingSettings.PERIOD_REPORT302ACCOUNT;
    }

    public static class Bean32
        extends AbstractDataBean
    {

        private String salesAccName;
        private String salesAccFinIn;
        private String salesAccCurrency;

        private boolean initialized;

        protected void init()
            throws EFapsException
        {
            if (!this.initialized) {
                final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.Period2Account);
                queryBldr.addWhereAttrEqValue(CIAccounting.Period2Account.FromAccountAbstractLink, getAccIntance());
                final MultiPrintQuery multi = queryBldr.getPrint();
                final SelectBuilder selSalesAcc = SelectBuilder.get().linkto(CIAccounting.Period2Account.SalesAccountLink);
                final SelectBuilder selSalesAccInst = new SelectBuilder(selSalesAcc).instance();
                final SelectBuilder selSalesAccName = new SelectBuilder(selSalesAcc).attribute(CISales.AccountAbstract.Name);
                final SelectBuilder selSalesAccCurrency = new SelectBuilder(selSalesAcc)
                                .linkto(CISales.AccountAbstract.CurrencyLink).attribute(CIERP.Currency.Name);
                multi.addSelect(selSalesAccInst, selSalesAccName, selSalesAccCurrency);
                multi.execute();
                if (multi.next()) {
                    setSalesAccName(multi.<String>getSelect(selSalesAccName));
                    setSalesAccCurrency(multi.<String>getSelect(selSalesAccCurrency));
                    final Instance salesAccInst = multi.<Instance>getSelect(selSalesAccInst);

                    if (salesAccInst.getType().isKindOf(CISales.AccountCashDesk.getType())) {
                        final SelectBuilder selSalesAccFIValue = SelectBuilder.get()
                                        .linkto(CISales.AccountCashDesk.FinancialInstitute)
                                        .attribute(CISales.AttributeDefinitionFinancialInstitute.Value);
                        final SelectBuilder selSalesAccFImapKey = SelectBuilder.get()
                                        .linkto(CISales.AccountCashDesk.FinancialInstitute)
                                        .attribute(CISales.AttributeDefinitionFinancialInstitute.MappingKey);

                        final PrintQuery salesAccPrint = new PrintQuery(salesAccInst);
                        salesAccPrint.addSelect(selSalesAccFIValue, selSalesAccFImapKey);
                        salesAccPrint.execute();
                        final String fiMapKey = salesAccPrint.getSelect(selSalesAccFImapKey);
                        final String fiValue = salesAccPrint.getSelect(selSalesAccFIValue);
                        setSalesAccFinIn(fiMapKey + " " + fiValue);
                    }

                }
                this.initialized = true;
            }
        }

        /**
         * Getter method for the instance variable {@link #salesAccName}.
         *
         * @return value of instance variable {@link #salesAccName}
         */
        public String getSalesAccName()
            throws EFapsException
        {
            init();
            return this.salesAccName;
        }

        /**
         * Setter method for instance variable {@link #salesAccName}.
         *
         * @param _salesAccName value for instance variable
         *            {@link #salesAccName}
         */
        public void setSalesAccName(final String _salesAccName)
        {
            this.salesAccName = _salesAccName;
        }

        /**
         * Getter method for the instance variable {@link #salesAccFinIn}.
         *
         * @return value of instance variable {@link #salesAccFinIn}
         */
        public String getSalesAccFinIn()
            throws EFapsException
        {
            init();
            return this.salesAccFinIn;
        }

        /**
         * Setter method for instance variable {@link #salesAccFinIn}.
         *
         * @param _salesAccFinIn value for instance variable
         *            {@link #salesAccFinIn}
         */
        public void setSalesAccFinIn(final String _salesAccFinIn)
        {
            this.salesAccFinIn = _salesAccFinIn;
        }

        /**
         * Getter method for the instance variable {@link #salesAccCurrency}.
         *
         * @return value of instance variable {@link #salesAccCurrency}
         */
        public String getSalesAccCurrency()
            throws EFapsException
        {
            init();
            return this.salesAccCurrency;
        }

        /**
         * Setter method for instance variable {@link #salesAccCurrency}.
         *
         * @param _salesAccCurrency value for instance variable
         *            {@link #salesAccCurrency}
         */
        public void setSalesAccCurrency(final String _salesAccCurrency)
        {
            this.salesAccCurrency = _salesAccCurrency;
        }
    }
}
