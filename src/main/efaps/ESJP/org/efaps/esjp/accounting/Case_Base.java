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

package org.efaps.esjp.accounting;

import org.efaps.admin.datamodel.Classification;
import org.efaps.admin.datamodel.Type;
import org.efaps.admin.datamodel.ui.FieldValue;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Parameter.ParameterValues;
import org.efaps.admin.event.Return;
import org.efaps.admin.event.Return.ReturnValues;
import org.efaps.admin.program.esjp.EFapsRevision;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.Instance;
import org.efaps.db.QueryBuilder;
import org.efaps.db.Update;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.ci.CIFormAccounting;
import org.efaps.esjp.common.uiform.Edit;
import org.efaps.esjp.common.uiform.Field;
import org.efaps.util.EFapsException;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 * @version $Id$
 */
@EFapsUUID("d240e6e6-b3e9-44a7-9bed-81452240a555")
@EFapsRevision("$Rev$")
public abstract class Case_Base
{

    /**
     * Key used for Caching.
     */
    protected static final String CACHEKEY = Case.class.getName() + ".CacheKey";

    /**
     * @param _parameter Paremeter as passed from the eFaPS API
     * @return Return SNIPPLET
     * @throws EFapsException on error
     */
    public Return edit(final Parameter _parameter)
        throws EFapsException
    {
        final Edit edit = new Edit()
        {

            @Override
            protected void add2MainUpdate(final Parameter _parameter,
                                          final Update _update)
                throws EFapsException
            {
                super.add2MainUpdate(_parameter, _update);
                _update.add(CIAccounting.Account2CaseAbstract.FromAccountAbstractLink, Instance.get(_parameter
                                .getParameterValue(CIFormAccounting.Accounting_Account2CaseForm.account.name)));
            }
        };
        return edit.execute(_parameter);
    }

    /**
     * @param _parameter Paremeter as passed from the eFaPS API
     * @return Return SNIPPLET
     * @throws EFapsException on error
     */
    public Return dropDownFieldValue(final Parameter _parameter)
        throws EFapsException
    {
        final Field field = new Field()
        {
            @Override
            protected void add2QueryBuilder4List(final Parameter _parameter,
                                                 final QueryBuilder _queryBldr)
                throws EFapsException
            {
                super.add2QueryBuilder4List(_parameter, _queryBldr);
                _queryBldr.addWhereAttrEqValue(CIAccounting.CategoryProduct.PeriodAbstractLink,
                                new Period().evaluateCurrentPeriod(_parameter));
            }

        };
        return field.dropDownFieldValue(_parameter);
    }

    /**
     * Get the value for the link field.
     *
     * @param _parameter Paremeter as passed from the eFaPS API
     * @return Return SNIPPLET
     * @throws EFapsException on error
     */
    public Return linkFieldValue(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final FieldValue fieldvalue = (FieldValue) _parameter.get(ParameterValues.UIOBJECT);
        final Long value = (Long) fieldvalue.getValue();
        final StringBuilder html = new StringBuilder();

        if (value != null && value > 0) {
            final Type type = Type.get(value);
            if (type == null) {
                html.append("-");
            } else {
                Classification clazz = (Classification) type;
                String label = type.getLabel();
                while (clazz.getParentClassification() != null) {
                    clazz = clazz.getParentClassification();
                    label = clazz.getLabel() + " - " + label;
                }
                html.append(label);
            }
        } else {
            html.append("-");
        }
        ret.put(ReturnValues.SNIPLETT, html.toString());
        return ret;
    }

    /**
     * Check the access to the link field.
     *
     * @param _parameter Paremeter as passed from the eFaPS API
     * @return return
     * @throws EFapsException on error
     */
    public Return classificationLinkAccessCheck(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        if (_parameter.getInstance().getType().equals(CIAccounting.Account2CaseCredit4Classification.getType())
                        || _parameter.getInstance().getType()
                                        .equals(CIAccounting.Account2CaseDebit4Classification.getType())) {
            ret.put(ReturnValues.TRUE, true);
        }
        return ret;
    }

    /**
     * Check the access to the link field.
     *
     * @param _parameter Paremeter as passed from the eFaPS API
     * @return return
     * @throws EFapsException on error
     */
    public Return categoryProductLinkAccessCheck(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        if (_parameter.getInstance().getType().equals(CIAccounting.Account2CaseCredit4CategoryProduct.getType())
                        || _parameter.getInstance().getType()
                                        .equals(CIAccounting.Account2CaseDebit4CategoryProduct.getType())) {
            ret.put(ReturnValues.TRUE, true);
        }
        return ret;
    }

    public Return keyAccessCheck(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        if (_parameter.getInstance().getType().equals(CIAccounting.Account2CaseCredit4Key.getType())
                        || _parameter.getInstance().getType()
                                        .equals(CIAccounting.Account2CaseDebit4Key.getType())) {
            ret.put(ReturnValues.TRUE, true);
        }
        return ret;
    }
}
