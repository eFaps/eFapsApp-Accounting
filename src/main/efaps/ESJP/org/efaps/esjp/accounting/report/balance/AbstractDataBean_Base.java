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

import java.math.BigDecimal;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 * @version $Id$
 */
public abstract class AbstractDataBean_Base
{

    private String accOID;

    private String accName;

    private String accDesc;

    private BigDecimal amount;

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
     * Getter method for the instance variable {@link #accDesc}.
     *
     * @return value of instance variable {@link #accDesc}
     */
    public String getAccDesc()
    {
        return this.accDesc;
    }

    /**
     * Setter method for instance variable {@link #accDesc}.
     *
     * @param _accDesc value for instance variable {@link #accDesc}
     */
    public void setAccDesc(final String _accDesc)
    {
        this.accDesc = _accDesc;
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

    /**
     * Setter method for instance variable {@link #amount}.
     *
     * @param _amount value for instance variable {@link #amount}
     */
    public void add(final BigDecimal _amount)
    {
        if (this.amount == null) {
            this.amount = BigDecimal.ZERO;
        }
        this.amount = this.amount.add(_amount);
    }

    /**
     * Getter method for the instance variable {@link #accOID}.
     *
     * @return value of instance variable {@link #accOID}
     */
    public String getAccOID()
    {
        return this.accOID;
    }

    /**
     * Setter method for instance variable {@link #accOID}.
     *
     * @param _accOID value for instance variable {@link #accOID}
     */
    public void setAccOID(final String _accOID)
    {
        this.accOID = _accOID;
    }
}
