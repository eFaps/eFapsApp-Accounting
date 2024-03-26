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
package org.efaps.esjp.accounting.report;

import java.math.BigDecimal;

import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.joda.time.DateTime;


/**
 * TODO comment!
 *
 * @author The eFaps Team
 * 
 */
@EFapsUUID("006f758a-dba6-4b09-b697-fa290503bb1b")
@EFapsApplication("eFapsApp-Accounting")
public abstract class AbstractCashReportDS_Base
    extends AbstractReportDS
{


    public abstract static class DataBean
    {
        private DateTime transDate;
        private String transDescr;
        private String accName;
        private String accDescr;
        private BigDecimal amount;




        /**
         * Getter method for the instance variable {@link #transDate}.
         *
         * @return value of instance variable {@link #transDate}
         */
        public DateTime getTransDate()
        {
            return this.transDate;
        }


        /**
         * Setter method for instance variable {@link #transDate}.
         *
         * @param _transDate value for instance variable {@link #transDate}
         */
        public void setTransDate(final DateTime _transDate)
        {
            this.transDate = _transDate;
        }



        /**
         * Getter method for the instance variable {@link #transDescr}.
         *
         * @return value of instance variable {@link #transDescr}
         */
        public String getTransDescr()
        {
            return this.transDescr;
        }



        /**
         * Setter method for instance variable {@link #transDescr}.
         *
         * @param _transDescr value for instance variable {@link #transDescr}
         */
        public void setTransDescr(final String _transDescr)
        {
            this.transDescr = _transDescr;
        }



        /**
         * Getter method for the instance variable {@link #accName}.
         *
         * @return value of instance variable {@link #accName}
         */
        public String getAccName()
        {
            return this.accName;
        }



        /**
         * Setter method for instance variable {@link #accName}.
         *
         * @param _accName value for instance variable {@link #accName}
         */
        public void setAccName(final String _accName)
        {
            this.accName = _accName;
        }



        /**
         * Getter method for the instance variable {@link #accDescr}.
         *
         * @return value of instance variable {@link #accDescr}
         */
        public String getAccDescr()
        {
            return this.accDescr;
        }



        /**
         * Setter method for instance variable {@link #accDescr}.
         *
         * @param _accDescr value for instance variable {@link #accDescr}
         */
        public void setAccDescr(final String _accDescr)
        {
            this.accDescr = _accDescr;
        }



        /**
         * Getter method for the instance variable {@link #amount}.
         *
         * @return value of instance variable {@link #amount}
         */
        public BigDecimal getAmount()
        {
            return this.amount;
        }



        /**
         * Setter method for instance variable {@link #amount}.
         *
         * @param _amount value for instance variable {@link #amount}
         */
        public void setAmount(final BigDecimal _amount)
        {
            this.amount = _amount;
        }
    }
}
