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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.efaps.admin.datamodel.Attribute;
import org.efaps.admin.datamodel.Classification;
import org.efaps.admin.datamodel.Status;
import org.efaps.admin.datamodel.Type;
import org.efaps.admin.datamodel.ui.FieldValue;
import org.efaps.admin.datamodel.ui.UIInterface;
import org.efaps.admin.dbproperty.DBProperties;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Parameter.ParameterValues;
import org.efaps.admin.event.Return;
import org.efaps.admin.event.Return.ReturnValues;
import org.efaps.admin.program.esjp.EFapsRevision;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.admin.ui.field.Field.Display;
import org.efaps.admin.user.Company;
import org.efaps.ci.CIAttribute;
import org.efaps.db.Context;
import org.efaps.db.Insert;
import org.efaps.db.Instance;
import org.efaps.db.InstanceQuery;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.PrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.db.Update;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.common.AbstractCommon;
import org.efaps.esjp.common.uisearch.Search;
import org.efaps.esjp.common.util.InterfaceUtils;
import org.efaps.ui.wicket.util.EFapsKey;
import org.efaps.util.EFapsException;
import org.efaps.util.cache.CacheReloadException;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 * @version $Id$
 */
@EFapsUUID("e9b8eabd-7122-4b30-a78b-891d1cec1a90")
@EFapsRevision("$Rev$")
public abstract class Account_Base
    extends AbstractCommon
{
    /**
     * Key used for Caching.
     */
    public static final String CACHEKEY = Account.class.getName() + ".CacheKey";

    /**
     * Method to show the tree transaction in periode.
     *
     * @param _parameter Parameter as passed from eFaps API
     * @return ret Return
     * @throws EFapsException on error
     */
    public Return accessCheck4ValidAccount(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.Periode2Account);
        queryBldr.addWhereAttrEqValue(CIAccounting.Periode2Account.FromLink, _parameter.getInstance());
        final MultiPrintQuery multi = queryBldr.getPrint();
        final SelectBuilder selInst = new SelectBuilder().linkto(CIAccounting.Periode2Account.ToLink).instance();
        multi.addSelect(selInst);
        multi.execute();
        final Instance accInst = multi.<Instance>getSelect(selInst);
        if (accInst != null && accInst.isValid()) {
            ret.put(ReturnValues.TRUE, true);
        }
        return ret;
    }

    /**
     * Method to manage access to subtreemenus.
     *
     * @param _parameter Parameter as passed from eFaps API
     * @return Return containing access
     * @throws EFapsException on error
     */
    public Return accessCheckOnSummary(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final PrintQuery print = new PrintQuery(_parameter.getInstance());
        print.addAttribute(CIAccounting.AccountAbstract.Summary);
        print.execute();
        final Boolean summary = print.<Boolean>getAttribute(CIAccounting.AccountAbstract.Summary);
        final boolean inverse = "true".equalsIgnoreCase(getProperty(_parameter, "Inverse"));
        if ((summary != null && !summary && !inverse)
                        || (summary != null && summary && inverse)) {
            ret.put(ReturnValues.TRUE, true);
        }
        return ret;
    }

    /**
     * Method is used to render a dropdown field containing the account types.
     *
     * @param _parameter Parameter as passed from the eFAPS API
     * @return Return containing the dropdown
     * @throws EFapsException on error
     */
    public Return getTypeFieldValue(final Parameter _parameter)
        throws EFapsException
    {
        final FieldValue fieldvalue = (FieldValue) _parameter.get(ParameterValues.UIOBJECT);
        final Return ret = new Return();
        final Type rootType = CIAccounting.AccountAbstract.getType();
        final List<Type> types = getChildren(rootType);
        final Map<String, Long> values = new TreeMap<String, Long>();
        for (final Type type : types) {
            String name = DBProperties.getProperty(type.getName() + ".Label");
            Type curType = type;
            while (curType.getParentType() != null && !curType.getParentType().getUUID().equals(rootType.getUUID())) {
                curType = curType.getParentType();
                name = DBProperties.getProperty(curType.getName() + ".Label") + " - " + name;
            }
            values.put(name, type.getId());
        }
        final StringBuilder html = new StringBuilder();
        html.append("<select name=\"").append(fieldvalue.getField().getName()).append("\" ")
                        .append(UIInterface.EFAPSTMPTAG).append(" >");
        for (final Entry<String, Long> entry : values.entrySet()) {
            html.append("<option value=\"").append(entry.getValue());
            html.append("\">").append(entry.getKey()).append("</option>");
        }
        html.append("</select>");
        ret.put(ReturnValues.SNIPLETT, html.toString());
        return ret;
    }

    /**
     * Recursive Method to get all child types of a type.
     *
     * @param _parent tyep the current children will be retrieved for
     * @return list of all child type for a type
     * @throws CacheReloadException on error
     */
    protected List<Type> getChildren(final Type _parent)
        throws CacheReloadException
    {
        final List<Type> ret = new ArrayList<Type>();
        for (final Type child : _parent.getChildTypes()) {
            ret.addAll(getChildren(child));
            ret.add(child);
        }
        return ret;
    }

    /**
     * Method is executed on an autocomplete event to present a dropdown with
     * accounts.
     *
     * @param _parameter Parameter as passed from the eFAPS API
     * @return list of map used for an autocomplete event
     * @throws EFapsException on erro
     */
    public Return autoComplete4Account(final Parameter _parameter)
        throws EFapsException
    {
        final Instance instance = new Periode().evaluateCurrentPeriod(_parameter);

        final String input = (String) _parameter.get(ParameterValues.OTHERS);
        final boolean caseFilter = "true".equalsIgnoreCase(_parameter.getParameterValue("checkbox4Account"));
        final Instance caseInst = Instance.get(_parameter.getParameterValue("case"));

        final Map<String, Map<String, String>> orderMap = new TreeMap<String, Map<String, String>>();

        final boolean showSumAccount = !"false".equalsIgnoreCase(getProperty(_parameter, "ShowSumAccount"));
        String postfix = "";
        if (containsProperty(_parameter, "TypePostfix")) {
            postfix = getProperty(_parameter, "TypePostfix");
        }
        final boolean nameSearch = Character.isDigit(input.charAt(0)) || input.equals("*");

        final QueryBuilder queryBuilder = new QueryBuilder(CIAccounting.AccountAbstract);
        if (nameSearch) {
            queryBuilder.addWhereAttrMatchValue(CIAccounting.AccountAbstract.Name, input + "*").setIgnoreCase(true);
        } else {
            queryBuilder.addWhereAttrMatchValue(CIAccounting.AccountAbstract.Description, input + "*").setIgnoreCase(
                            true);
        }

        if (!showSumAccount) {
            queryBuilder.addWhereAttrEqValue(CIAccounting.AccountAbstract.Summary, false);
        }
        boolean showPeriod = false;
        if (!caseFilter || !caseInst.isValid()) {
            // if we do not filter for period we must show it
            if (instance != null && instance.getType().isKindOf(CIAccounting.Periode.getType())) {
                queryBuilder.addWhereAttrEqValue(CIAccounting.AccountAbstract.PeriodeAbstractLink, instance);
            } else {
                showPeriod = true;
                queryBuilder.addWhereAttrEqValue(CIAccounting.AccountAbstract.Company,
                                Context.getThreadContext().getPerson().getCompanies().toArray());
            }
        } else {
            QueryBuilder attrQueryBldr;
            if (postfix.equalsIgnoreCase("debit")) {
                attrQueryBldr = new QueryBuilder(CIAccounting.Account2CaseDebit);
            } else {
                attrQueryBldr = new QueryBuilder(CIAccounting.Account2CaseCredit);
            }
            attrQueryBldr.addWhereAttrEqValue(CIAccounting.Account2CaseAbstract.ToCaseAbstractLink, caseInst);
            queryBuilder.addWhereAttrInQuery(CIAccounting.AccountAbstract.ID,
                            attrQueryBldr.getAttributeQuery(CIAccounting.Account2CaseAbstract.FromAccountAbstractLink));
        }
        InterfaceUtils.addMaxResult2QueryBuilder4AutoComplete(_parameter, queryBuilder);

        final InstanceQuery query = queryBuilder.getQuery();
        if (instance.getType().isKindOf(CIAccounting.ReportMultipleAbstract.getType())) {
            query.setCompanyDepended(false);
        }

        final MultiPrintQuery multi = new MultiPrintQuery(query.execute());
        multi.addAttribute(CIAccounting.AccountAbstract.Name, CIAccounting.AccountAbstract.Description,
                        CIAccounting.AccountAbstract.Company);
        SelectBuilder selPeriod = null;
        if (showPeriod) {
            selPeriod = SelectBuilder.get().linkto(CIAccounting.AccountAbstract.PeriodeAbstractLink)
                        .attribute(CIAccounting.Periode.Name);
            multi.addSelect(selPeriod);
        }
        multi.execute();
        while (multi.next()) {
            final String name = multi.<String>getAttribute(CIAccounting.AccountAbstract.Name);
            String description = multi.getAttribute(CIAccounting.AccountAbstract.Description);
            if (showPeriod) {
                final Company company = multi.<Company>getAttribute(CIAccounting.AccountAbstract.Company);
                description = description + " - " + multi.<String>getSelect(selPeriod)
                                + (company == null ? "" : (" - " + company.getName()));
            }
            final String choice;
            if (nameSearch) {
                choice = name + " - " + description;
            } else {
                choice = description + " - " + name;
            }
            final Map<String, String> map = new HashMap<String, String>();
            map.put(EFapsKey.AUTOCOMPLETE_KEY.getKey(), multi.getCurrentInstance().getOid());
            map.put(EFapsKey.AUTOCOMPLETE_VALUE.getKey(), name);
            map.put(EFapsKey.AUTOCOMPLETE_CHOICE.getKey(), choice);
            orderMap.put(choice, map);
        }

        final List<Map<String, String>> list = new ArrayList<Map<String, String>>();
        list.addAll(orderMap.values());
        final Return retVal = new Return();
        retVal.put(ReturnValues.VALUES, list);
        return retVal;
    }

    /**
     * Create a new Account.
     *
     * @param _parameter    Parameter as passed from the eFAPS API
     * @return new Return
     * @throws EFapsException on error
     */
    public Return create(final Parameter _parameter)
        throws EFapsException
    {
        final Instance instance = _parameter.getCallInstance();
        long periode;
        boolean parent = false;
        if ("Accounting_Periode".equals(instance.getType().getName())) {
            periode = instance.getId();
        } else {
            final PrintQuery print = new PrintQuery(instance);
            print.addAttribute("PeriodeAbstractLink");
            print.execute();
            periode = print.<Long> getAttribute("PeriodeAbstractLink");
            parent = true;
        }

        final String typeId = _parameter.getParameterValue("type");
        final Type type = Type.get(Long.parseLong(typeId));
        final Insert insert = new Insert(type);
        insert.add("Name", _parameter.getParameterValue("name"));
        insert.add("Description", _parameter.getParameterValue("description"));
        if (_parameter.getParameterValue("active") != null) {
            insert.add("Active", _parameter.getParameterValue("active"));
        }
        if (_parameter.getParameterValue("summary") != null) {
            insert.add("Summary", _parameter.getParameterValue("summary"));
        }
        if (parent) {
            insert.add("ParentLink", instance.getId());
        }
        insert.add("PeriodeLink", periode);
        insert.execute();
        return new Return();
    }

    /**
     * @param _parameter    Parameter as passed from the eFAPS API
     * @return  Return with list of instances
     * @throws EFapsException on error
     */
    public Return searchContact(final Parameter _parameter)
        throws EFapsException
    {
        final Map<?, ?> properties = (Map<?, ?>) _parameter.get(ParameterValues.PROPERTIES);
        final String name = Context.getThreadContext().getParameter("name");
        final String classType = (String) properties.get("ClassType");
        final String types = (String) properties.get("Types");

        final QueryBuilder queryBldr = new QueryBuilder(Type.get(types));
        queryBldr.addWhereAttrMatchValue("Name", name).setIgnoreCase(true);
        queryBldr.addWhereClassification((Classification) Type.get(classType));
        final InstanceQuery query = queryBldr.getQuery();

        final List<Instance> instances  = query.execute();
        final Return ret = new Return();
        ret.put(ReturnValues.VALUES, instances);
        return ret;
    }

    /**
     * Create a Relation from Revenue to ProductClass.
     *
     * @param _parameter    Parameter as passed from the eFAPS API
     * @return new Return
     * @throws EFapsException on error
     */
    public Return createAccount2ProductClass(final Parameter _parameter)
        throws EFapsException
    {
        final Instance instance = _parameter.getCallInstance();
        final Map<?, ?> props = (Map<?, ?>) _parameter.get(ParameterValues.PROPERTIES);
        if (props.containsKey("Type")) {
            final Insert insert = new Insert((String) props.get("Type"));
            insert.add("FromAccountLink", instance.getId());
            insert.add("ToProductClassLink", _parameter.getParameterValue("productClass"));
            insert.execute();
        }
        return new Return();
    }

    /**
     * Get the value for a dropdown field for classifications.
     * @param _parameter    parent classification
     * @return Return with html snipplet
     * @throws EFapsException on error
     */
    public Return getProducClassFieldValueUI(final Parameter _parameter)
        throws EFapsException
    {
        final FieldValue fieldvalue = (FieldValue) _parameter.get(ParameterValues.UIOBJECT);
        final Return ret = new Return();

        final Type rootClass = Type.get("Products_Class");

        final List<Type> types = getChildClassifications((Classification) rootClass);
        final Map<String, Long> values = new TreeMap<String, Long>();
        for (final Type type : types) {
            Classification clazz = (Classification) type;
            String label = DBProperties.getProperty(type.getName() + ".Label");
            while (clazz.getParentClassification() != null) {
                clazz = clazz.getParentClassification();
                label = DBProperties.getProperty(clazz.getName() + ".Label") + " - " + label;
            }
            values.put(label, type.getId());
        }
        final StringBuilder html = new StringBuilder();
        html.append("<select name=\"").append(fieldvalue.getField().getName()).append("\" ")
                        .append(UIInterface.EFAPSTMPTAG).append(" >");
        for (final Entry<String, Long> entry : values.entrySet()) {
            html.append("<option value=\"").append(entry.getValue());
            html.append("\">").append(entry.getKey()).append("</option>");
        }
        html.append("</select>");
        ret.put(ReturnValues.SNIPLETT, html.toString());
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
     * Executed from trigger to update the sums of parent accounts.
     *
     * @param _parameter    Parameter as passed from the eFAPS API
     * @return new Return
     * @throws EFapsException on error
     */
    public Return sumUpdateTrigger(final Parameter _parameter)
        throws EFapsException
    {
        final Map<?, ?> values = (Map<?, ?>) _parameter.get(ParameterValues.NEW_VALUES);
        // only if this attributes where changed
        for (final Object obj : values.keySet()) {
            if (((Attribute) obj).getName().equals("SumReport")) {
                sumerize(_parameter, CIAccounting.AccountAbstract.SumReport);
            } else if (((Attribute) obj).getName().equals("SumBooked")) {
                sumerize(_parameter, CIAccounting.AccountAbstract.SumBooked);
            }
        }
        return new Return();
    }

    /**
     *
     * @param _parameter    Parameter as passed from the eFaps API
     * @param _attr         Attribute
     * @throws EFapsException on error
     */
    protected void sumerize(final Parameter _parameter,
                            final CIAttribute _attr)
        throws EFapsException
    {
        BigDecimal sum = BigDecimal.ZERO;
        final PrintQuery print = new PrintQuery(_parameter.getInstance());
        final SelectBuilder selInst = SelectBuilder.get().linkto(CIAccounting.AccountAbstract.ParentLink).instance();
        final SelectBuilder selSummary = SelectBuilder.get().linkto(CIAccounting.AccountAbstract.ParentLink)
                        .attribute(CIAccounting.AccountAbstract.Summary);
        print.addSelect(selInst, selSummary);
        print.execute();
        final Boolean parentSum = print.<Boolean>getSelect(selSummary);
        if (parentSum != null && parentSum) {
            final Instance parentInst = print.<Instance>getSelect(selInst);
            final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.AccountAbstract);
            queryBldr.addWhereAttrEqValue(CIAccounting.AccountAbstract.ParentLink, parentInst);
            final MultiPrintQuery multi = new MultiPrintQuery(queryBldr.getQuery().executeWithoutAccessCheck());
            multi.addAttribute(_attr);
            multi.executeWithoutAccessCheck();

            while (multi.next()) {
                final BigDecimal tmpsum = multi.<BigDecimal>getAttribute(_attr);
                if (tmpsum != null) {
                    sum = sum.add(tmpsum);
                }
            }
            final Update update = new Update(parentInst);
            update.add(_attr, sum);
            update.executeWithoutAccessCheck();
        }
    }


    /**
     * Method is called from a periode to recacluate all values for the accounts.
     * @param _parameter Parameter as passed from the eFaps API
     * @return empty Return
     * @throws EFapsException on error
     */
    public Return reCalculateAccounts(final Parameter _parameter) throws EFapsException
    {
        final Map<Instance, BigDecimal> acc2sumBooked = new HashMap<Instance, BigDecimal>();
        final Map<Instance, BigDecimal> acc2sumReport = new HashMap<Instance, BigDecimal>();
        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.TransactionAbstract);
        queryBldr.addWhereAttrEqValue(CIAccounting.TransactionAbstract.PeriodeLink, _parameter.getInstance().getId());
        final MultiPrintQuery multi = queryBldr.getPrint();
        multi.addAttribute(CIAccounting.TransactionAbstract.StatusAbstract);
        multi.execute();
        final SelectBuilder sel = new SelectBuilder()
            .linkto(CIAccounting.TransactionPositionAbstract.AccountLink).oid();
        while (multi.next()) {
            final Long statusId = multi.<Long> getAttribute(CIAccounting.TransactionAbstract.StatusAbstract);
            final QueryBuilder posQueryBldr = new QueryBuilder(CIAccounting.TransactionPositionAbstract);
            posQueryBldr.addWhereAttrEqValue(CIAccounting.TransactionPositionAbstract.TransactionLink,
                                             multi.getCurrentInstance().getId());
            final MultiPrintQuery posMulti = posQueryBldr.getPrint();
            posMulti.addAttribute(CIAccounting.TransactionPositionAbstract.Amount);
            posMulti.addSelect(sel);
            posMulti.execute();
            while (posMulti.next()) {
                final BigDecimal amount = posMulti.<BigDecimal>
                    getAttribute(CIAccounting.TransactionPositionAbstract.Amount);
                final String accountOid = posMulti.<String> getSelect(sel);
                final Instance accInst = Instance.get(accountOid);
                if (acc2sumReport.containsKey(accInst)) {
                    acc2sumReport.put(accInst, acc2sumReport.get(accInst).add(amount));
                } else {
                    acc2sumReport.put(accInst, amount);
                }
                if (statusId.equals(Status.find(CIAccounting.TransactionStatus.Booked))) {
                    if (acc2sumBooked.containsKey(accInst)) {
                        acc2sumBooked.put(accInst, acc2sumBooked.get(accInst).add(amount));
                    } else {
                        acc2sumBooked.put(accInst, amount);
                    }
                }
            }
        }

        final QueryBuilder accQueryBldr = new QueryBuilder(CIAccounting.AccountAbstract);
        accQueryBldr.addWhereAttrIsNull(CIAccounting.AccountAbstract.ParentLink);
        accQueryBldr.addWhereAttrEqValue(CIAccounting.AccountAbstract.PeriodeAbstractLink,
                                         _parameter.getInstance().getId());
        final InstanceQuery accQuery = accQueryBldr.getQuery();
        accQuery.execute();
        while (accQuery.next()) {
            reCalc(accQuery.getCurrentValue(), acc2sumReport, acc2sumBooked);
        }
        return new Return();
    }

    /**
     * @param _instance instance the sums are calculated
     * @param _acc2sumReport    account to Sum
     * @param _acc2sumBooked    account to sum
     * @return array with BigDecimal
     * @throws EFapsException on error
     */
    protected BigDecimal[] reCalc(final Instance _instance,
                                  final Map<Instance, BigDecimal> _acc2sumReport,
                                  final Map<Instance, BigDecimal> _acc2sumBooked)
        throws EFapsException
    {
        BigDecimal sumBooked = BigDecimal.ZERO;
        BigDecimal sumReport = BigDecimal.ZERO;
        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.AccountAbstract);
        queryBldr.addWhereAttrEqValue(CIAccounting.AccountAbstract.ParentLink, _instance.getId());
        final InstanceQuery query = queryBldr.getQuery();
        query.execute();
        boolean hasChilds = false;
        while (query.next()) {
            hasChilds = true;
            final BigDecimal[] subSums = reCalc(query.getCurrentValue(), _acc2sumReport, _acc2sumBooked);
            sumBooked = sumBooked.add(subSums[0]);
            sumReport = sumReport.add(subSums[1]);
        }
        final BigDecimal newbooked = _acc2sumBooked.containsKey(_instance)
                ? _acc2sumBooked.get(_instance) : BigDecimal.ZERO;
        final BigDecimal newReport = _acc2sumReport.containsKey(_instance)
                ? _acc2sumReport.get(_instance) : BigDecimal.ZERO;

        final PrintQuery print = new PrintQuery(_instance);
        print.addAttribute(CIAccounting.AccountAbstract.SumBooked, CIAccounting.AccountAbstract.SumReport);
        print.execute();
        BigDecimal booked = print.<BigDecimal>getAttribute(CIAccounting.AccountAbstract.SumBooked);
        BigDecimal report = print.<BigDecimal>getAttribute(CIAccounting.AccountAbstract.SumReport);
        if (booked == null) {
            booked = BigDecimal.ZERO;
        }
        if (report == null) {
            report = BigDecimal.ZERO;
        }
        if (!hasChilds) {
            sumBooked = sumBooked.add(newbooked);
            sumReport = sumReport.add(newReport);
        }
        if ((sumBooked.compareTo(booked) != 0)
                        || (report != null && sumReport.compareTo(report) != 0)) {
            final Update update = new Update(_instance);
            update.add(CIAccounting.AccountAbstract.SumBooked, sumBooked);
            update.add(CIAccounting.AccountAbstract.SumReport, sumReport);
            update.executeWithoutTrigger();
        }

        return new BigDecimal[] { sumBooked, sumReport };
    }


    /**
     * Get the value only if it has the given signum.
     * Used to be able to display &quot;debit&quot; and &quot;credit&quot; separately.
     *
     * @param _parameter    Parameter as passed by the eFaps API
     * @return new Return
     * @throws EFapsException on error
     */
    public Return getValue4Signum(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final Map<?, ?> props = (Map<?, ?>) _parameter.get(ParameterValues.PROPERTIES);
        final FieldValue fieldvalue = (FieldValue) _parameter.get(ParameterValues.UIOBJECT);
        final BigDecimal value = (BigDecimal) fieldvalue.getValue();
        if (value != null && !Display.NONE.equals(fieldvalue.getDisplay())) {
            BigDecimal retValue = null;
            fieldvalue.setValue(null);
            if (("negativ".equalsIgnoreCase((String) props.get("Signum")) && value.signum() == -1)
                            || (!"negativ".equalsIgnoreCase((String) props.get("Signum")) && value.signum() == 1)) {
                retValue = value.abs();
            }
            ret.put(ReturnValues.VALUES, retValue);
        }
        return ret;
    }

    /**
     * Search that ensures that only accounts of the same period are searched.
     * @param _parameter    Parameter as passed by the eFaps API
     * @return new Return containing search result
     * @throws EFapsException on error
     */
    public Return searchAccount(final Parameter _parameter)
        throws EFapsException
    {
        final Search search = new Search()
        {
            @Override
            protected void add2QueryBuilder(final Parameter _parameter,
                                            final QueryBuilder _queryBldr)
                throws EFapsException
            {
                super.add2QueryBuilder(_parameter, _queryBldr);
                final Instance accInst = _parameter.getInstance();
                if (accInst != null && accInst.isValid()) {
                    if (accInst.getType().isKindOf(CIAccounting.AccountAbstract.getType())) {
                        final PrintQuery print = new PrintQuery(accInst);
                        print.addAttribute(CIAccounting.AccountAbstract.PeriodeAbstractLink);
                        print.executeWithoutAccessCheck();
                        _queryBldr.addWhereAttrEqValue(CIAccounting.AccountAbstract.PeriodeAbstractLink,
                                    print.getAttribute(CIAccounting.AccountAbstract.PeriodeAbstractLink));
                    } else if (accInst.getType().isKindOf(CIAccounting.CaseAbstract.getType())) {
                        final PrintQuery print = new PrintQuery(accInst);
                        print.addAttribute(CIAccounting.CaseAbstract.PeriodeAbstractLink);
                        print.executeWithoutAccessCheck();
                        _queryBldr.addWhereAttrEqValue(CIAccounting.AccountAbstract.PeriodeAbstractLink,
                                        print.getAttribute(CIAccounting.CaseAbstract.PeriodeAbstractLink));
                    }
                }
            }
        };
        return search.execute(_parameter);
    }

}
