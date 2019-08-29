/*
 * Copyright 2003 - 2019 The eFaps Team
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

package org.efaps.esjp.accounting;

import java.util.List;

import org.apache.commons.collections.CollectionUtils;
import org.efaps.admin.datamodel.Classification;
import org.efaps.admin.datamodel.Type;
import org.efaps.admin.datamodel.ui.IUIValue;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Parameter.ParameterValues;
import org.efaps.admin.event.Return;
import org.efaps.admin.event.Return.ReturnValues;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.Instance;
import org.efaps.db.QueryBuilder;
import org.efaps.db.Update;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.ci.CIFormAccounting;
import org.efaps.esjp.common.uiform.Edit;
import org.efaps.esjp.common.uiform.Field;
import org.efaps.esjp.db.InstanceUtils;
import org.efaps.util.EFapsException;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 */
@EFapsUUID("d240e6e6-b3e9-44a7-9bed-81452240a555")
@EFapsApplication("eFapsApp-Accounting")
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
        return field.getOptionListFieldValue(_parameter);
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
        final IUIValue fieldvalue = (IUIValue) _parameter.get(ParameterValues.UIOBJECT);
        final Long value = (Long) fieldvalue.getObject();
        final StringBuilder html = new StringBuilder();

        if (value != null && value > 0) {
            final Type type = Type.get(value);
            if (type == null || !(type instanceof Classification)) {
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
        if (InstanceUtils.isType(_parameter.getInstance(), CIAccounting.Account2CaseCredit4Classification)
                        || InstanceUtils.isType(_parameter.getInstance(), CIAccounting.Account2CaseDebit4Classification)) {
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
        if (InstanceUtils.isType(_parameter.getInstance(), CIAccounting.Account2CaseCredit4CategoryProduct)
                        || InstanceUtils.isType(_parameter.getInstance(), CIAccounting.Account2CaseDebit4CategoryProduct)
                        || InstanceUtils.isType(_parameter.getInstance(), CIAccounting.CategoryProduct)) {
            ret.put(ReturnValues.TRUE, true);
        }
        return ret;
    }

    /**
     * Key access check.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return the return
     * @throws EFapsException on error
     */
    public Return keyAccessCheck(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        if (InstanceUtils.isType(_parameter.getInstance(), CIAccounting.Account2CaseCredit4Key)
                        || InstanceUtils.isType(_parameter.getInstance(), CIAccounting.Account2CaseDebit4Key)) {
            ret.put(ReturnValues.TRUE, true);
        }
        return ret;
    }

    /**
     *
     * @param _parameter Paremeter as passed from the eFaPS API
     * @return Return SNIPPLET
     * @throws EFapsException on error
     */
    public Return assignNode(final Parameter _parameter)
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
                final List<Instance> instances = getSelectedInstances(_parameter);
                if (CollectionUtils.isNotEmpty(instances)) {
                    _update.add(CIAccounting.Account2Case4ProductTreeViewAbstract.ProductTreeViewLink,
                                    instances.get(0));
                }
            }
        };
        return edit.execute(_parameter);
    }
}
