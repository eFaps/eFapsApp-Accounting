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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.UUID;

import org.efaps.admin.common.SystemConfiguration;
import org.efaps.admin.datamodel.Classification;
import org.efaps.admin.datamodel.Type;
import org.efaps.admin.datamodel.ui.FieldValue;
import org.efaps.admin.datamodel.ui.UIInterface;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Parameter.ParameterValues;
import org.efaps.admin.event.Return;
import org.efaps.admin.event.Return.ReturnValues;
import org.efaps.admin.program.esjp.EFapsRevision;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.admin.ui.AbstractUserInterfaceObject.TargetMode;
import org.efaps.db.InstanceQuery;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.ui.wicket.util.EFapsKey;
import org.efaps.util.EFapsException;
import org.efaps.util.cache.CacheReloadException;
import org.joda.time.DateTime;


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
     * Check access to label.
     *
     * @param _parameter Paremeter as passed from the eFaPS API
     * @return Return
     * @throws EFapsException on error
     */
    public Return accessCheck4Case(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        // Accounting-Configuration
        final SystemConfiguration sysconf = SystemConfiguration.get(
                        UUID.fromString("ca0a1df1-2211-45d9-97c8-07af6636a9b9"));
        if (!sysconf.getAttributeValueAsBoolean("DeactivateCase")) {
            ret.put(ReturnValues.TRUE, true);
        }
        return ret;
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
     * Get the value for a dropdown field for classifications.
     *@param _parameter Paremeter as passed from the eFaPS API
     * @return Return with html snipplet
     * @throws EFapsException on error
     */
    public Return classificationLinkFieldValue(final Parameter _parameter)
        throws EFapsException
    {
        final FieldValue fieldvalue = (FieldValue) _parameter.get(ParameterValues.UIOBJECT);
        final Return ret;
        if (fieldvalue.getTargetMode().equals(TargetMode.EDIT)) {
            ret = new Return();
            final Map<?, ?> props = (Map<?, ?>) _parameter.get(ParameterValues.PROPERTIES);
            final List<Type> typesList = new ArrayList<Type>();
            final String typesStr = (String) props.get("Types");
            final String[] types = typesStr.split(";");
            for (final String type : types) {
                final Type rootClass = Type.get(type);
                typesList.addAll(getChildClassifications((Classification) rootClass));
            }

            final Map<String, Long> values = new TreeMap<String, Long>();
            for (final Type type : typesList) {
                Classification clazz = (Classification) type;
                String label = type.getLabel();
                while (clazz.getParentClassification() != null) {
                    clazz = clazz.getParentClassification();
                    label = clazz.getLabel() + " - " + label;
                }
                values.put(label, type.getId());
            }
            final StringBuilder html = new StringBuilder();
            html.append("<select name=\"").append(fieldvalue.getField().getName()).append("\" ")
                            .append(UIInterface.EFAPSTMPTAG).append(" >");
            for (final Entry<String, Long> entry : values.entrySet()) {
                html.append("<option value=\"").append(entry.getValue()).append("\"");
                if (entry.getValue().equals(fieldvalue.getValue())) {
                    html.append(" selected=\"selected\" ");
                }
                html.append(">").append(entry.getKey()).append("</option>");
            }
            html.append("</select>");
            ret.put(ReturnValues.SNIPLETT, html.toString());
        } else {
            ret = linkFieldValue(_parameter);
        }
        return ret;
    }

    /**
     * Get the list of child classifications.
     * @param _parent parent classification
     * @return list of classifications
     * @throws CacheReloadException on error
     */
    protected List<Type> getChildClassifications(final Classification _parent)
        throws CacheReloadException
    {
        final List<Type> ret = new ArrayList<Type>();
        for (final Type child : _parent.getChildClassifications()) {
            ret.addAll(getChildClassifications((Classification) child));
            ret.add(child);
        }
        return ret;
    }

    /**
     * Check the access to the link field.
     * @param _parameter Paremeter as passed from the eFaPS API
     * @return return
     * @throws EFapsException on error
     */
    public Return classificationLinkAccessCheck(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        if (_parameter.getInstance().getType().equals(CIAccounting.Account2CaseCredit4Classification.getType())
                || _parameter.getInstance().getType().equals(CIAccounting.Account2CaseDebit4Classification.getType())) {
            ret.put(ReturnValues.TRUE, true);
        }
        return ret;
    }

    /**
     * AutoComplete for Accounts.
     * @param _parameter Paremeter as passed from the eFaPS API
     * @return return
     * @throws EFapsException on error
     */
    public Return autoComplete4Account(final Parameter _parameter)
        throws EFapsException
    {
        final String input = (String) _parameter.get(ParameterValues.OTHERS);
        final List<Map<String, String>> list = new ArrayList<Map<String, String>>();
        final Map<?, ?> properties = (Map<?, ?>) _parameter.get(ParameterValues.PROPERTIES);

        final String key = properties.containsKey("Key") ? (String) properties.get("Key") : "OID";
        final Map<String, Map<String, String>> tmpMap = new TreeMap<String, Map<String, String>>();
        if (input.length() > 0) {
            final QueryBuilder queryBldrPer = new QueryBuilder(CIAccounting.Periode);
            queryBldrPer.addWhereAttrGreaterValue(CIAccounting.Periode.ToDate, new DateTime());
            queryBldrPer.addWhereAttrLessValue(CIAccounting.Periode.FromDate, new DateTime());
            final InstanceQuery query = queryBldrPer.getQuery();
            query.execute();
            if (query.next()) {
                final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.AccountAbstract);
                queryBldr.addWhereAttrEqValue(CIAccounting.AccountAbstract.PeriodeAbstractLink,
                                query.getCurrentValue().getId());
                queryBldr.addWhereAttrEqValue(CIAccounting.AccountAbstract.Summary, false);
                final boolean nameSearch = Character.isDigit(input.charAt(0));
                if (nameSearch) {
                    queryBldr.addWhereAttrMatchValue(CIAccounting.AccountAbstract.Name, input + "*")
                                    .setIgnoreCase(true);
                } else {
                    queryBldr.addWhereAttrMatchValue(CIAccounting.AccountAbstract.Description, input + "*")
                                    .setIgnoreCase(true);
                }
                final MultiPrintQuery multi = queryBldr.getPrint();
                multi.addAttribute(CIAccounting.AccountAbstract.Name, CIAccounting.AccountAbstract.Description);
                multi.addAttribute(key);
                multi.execute();
                while (multi.next()) {
                    final String name = multi.<String>getAttribute(CIAccounting.AccountAbstract.Name);
                    final String descr = multi.<String>getAttribute(CIAccounting.AccountAbstract.Description);
                    final String keyVal = multi.getAttribute(key).toString();
                    final Map<String, String> map = new HashMap<String, String>();
                    map.put(EFapsKey.AUTOCOMPLETE_KEY.getKey(), keyVal);
                    map.put(EFapsKey.AUTOCOMPLETE_VALUE.getKey(), name);
                    map.put(EFapsKey.AUTOCOMPLETE_CHOICE.getKey(), name + " - " + descr);
                    tmpMap.put(name, map);
                }
            }
        }
        final Return retVal = new Return();
        list.addAll(tmpMap.values());
        retVal.put(ReturnValues.VALUES, list);
        return retVal;
    }
}
