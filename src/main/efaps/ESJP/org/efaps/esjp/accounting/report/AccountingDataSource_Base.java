/*
 * Copyright 2003 - 2010 The eFaps Team
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

package org.efaps.esjp.accounting.report;

import java.math.BigDecimal;
import java.util.List;

import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRField;

import org.efaps.admin.program.esjp.EFapsRevision;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.esjp.accounting.report.Report_Base.Node;
import org.efaps.util.EFapsException;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 * @version $Id: AccountingDataSource_Base.java 3955 2010-04-06 00:13:21Z
 *          jan.moxter $
 */
@EFapsUUID("fa9df3f8-ff95-4cbd-868a-48ed8e23741d")
@EFapsRevision("$Rev$")
public abstract class AccountingDataSource_Base
    implements JRDataSource
{

    private final List<List<Node>> values;
    private final int size;
    private int current = -1;

    /**
     * @param _values JasperReport
     * @throws EFapsException on error
     */
    public AccountingDataSource_Base(final List<List<Node>> _values)
        throws EFapsException
    {
        this.values = _values;
        int max = 0;
        for (final List<Node> list : _values) {
            if (max < list.size()) {
                max = list.size();
            }
        }
        this.size = max;
    }

    /**
     * @see net.sf.jasperreports.engine.JRDataSource#getFieldValue(net.sf.jasperreports.engine.JRField)
     * @param _field JRField
     * @return value for the given field
     * @throws JRException on error
     */
    public Object getFieldValue(final JRField _field)
        throws JRException
    {
        final int rootIndex = Integer.parseInt(_field.getPropertiesMap().getProperty("rootIndex"));
        _field.getPropertiesMap().removeProperty("level");
        Object ret = null;
        final List<Node> nodes = this.values.get(rootIndex);
        if (this.current < nodes.size()) {
            final Node node = nodes.get(this.current);
            if (_field.getValueClass().equals(BigDecimal.class)) {
                ret = node.getSum();
            } else {
                ret = node.getLabel();
            }
            _field.getPropertiesMap().setProperty("level", "" + node.getLevel());
        }
        return ret;
    }

    /**
     * @see net.sf.jasperreports.engine.JRDataSource#next()
     * @return true if a next value exist, else false
     * @throws JRException on error
     */
    public boolean next()
        throws JRException
    {
        final boolean ret = this.current < this.size;
        this.current++;
        return ret;
    }
}
