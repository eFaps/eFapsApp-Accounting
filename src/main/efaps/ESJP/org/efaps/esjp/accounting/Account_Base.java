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
import java.util.UUID;

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
import org.efaps.admin.ui.AbstractCommand;
import org.efaps.admin.ui.field.Field;
import org.efaps.admin.ui.field.Field.Display;
import org.efaps.db.Context;
import org.efaps.db.Insert;
import org.efaps.db.Instance;
import org.efaps.db.InstanceQuery;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.PrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.db.Update;
import org.efaps.esjp.accounting.transaction.Transaction_Base;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.ui.wicket.util.EFapsKey;
import org.efaps.util.EFapsException;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 * @version $Id$
 */
@EFapsUUID("e9b8eabd-7122-4b30-a78b-891d1cec1a90")
@EFapsRevision("$Rev$")
public abstract class Account_Base
{
    /**
     * Method to show the tree transaction in periode.
     *
     * @param _parameter Parameter as passed from eFaps API
     * @return ret Return
     * @throws EFapsException on error
     */
    public Return accessCheck(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.Periode2Account);
        queryBldr.addWhereAttrEqValue(CIAccounting.Periode2Account.ToLink, _parameter.getInstance().getId());
        final MultiPrintQuery multi = queryBldr.getPrint();
        final SelectBuilder selOid = new SelectBuilder().linkto(CIAccounting.Periode2Account.FromLink).oid();
        multi.addSelect(selOid);
        multi.execute();
        final String summary = multi.<String>getSelect(selOid);
        if (Instance.get(summary).isValid()) {
            ret.put(ReturnValues.TRUE, true);
        }
        return ret;
    }

    /**
     * Called from a tree menu command to present the documents that are with status
     * booked and therefor must be worked on still.
     *
     * @param _parameter Paremeter
     * @return List if Instances
     * @throws EFapsException on error
     */
    public Return getDocumentsToBook(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final Instance instance = _parameter.getInstance();
        final AbstractCommand command = (AbstractCommand) _parameter.get(ParameterValues.UIOBJECT);
        final UUID uuidTypeDoc = command.getUUID();
        final PrintQuery print = new PrintQuery(instance);
        print.addAttribute(CIAccounting.AccountAbstract.PeriodeAbstractLink);
        print.execute();

        final Long id = print.<Long>getAttribute(CIAccounting.AccountAbstract.PeriodeAbstractLink);

        _parameter.put(ParameterValues.INSTANCE, Instance.get(CIAccounting.Periode.getType(), id));

        final List<Instance> instances = new Periode().getDocumentsToBookList(_parameter);
        // Accounting_AccountTree_DocsToPay
        if (uuidTypeDoc.equals(UUID.fromString("6fd11ce2-72e0-40ef-a959-186e3e664aa9"))) {
            instances.addAll(new Periode().getExternalsToBookList(_parameter));
        }

        ret.put(ReturnValues.VALUES, instances);
        return ret;
    }

    /**
     * Method is used to render a dropdown field containing the account types.
     *
     * @param _parameter Parameter as passed from the eFAPS API
     * @return Return containing the dropdown
     */
    public Return getTypeFieldValue(final Parameter _parameter)
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
     */
    protected List<Type> getChildren(final Type _parent)
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
        final Instance periode = (Instance) Context.getThreadContext()
                                                    .getSessionAttribute(Transaction_Base.PERIODE_SESSIONKEY);

        final String caseOid = (String) Context.getThreadContext()
                                                    .getSessionAttribute(Transaction_Base.CASE_SESSIONKEY);
        final String input = (String) _parameter.get(ParameterValues.OTHERS);
        final Map<String, Map<String, String>> orderMap = new TreeMap<String, Map<String, String>>();
        final QueryBuilder queryBuilder;
        final Map<?, ?> properties = (Map<?, ?>) _parameter.get(ParameterValues.PROPERTIES);
        boolean showSumAccount = true;
        String postfix = "";
        if (properties != null && properties.containsKey("TypePostfix")) {
            postfix = (String) properties.get("TypePostfix");
            showSumAccount = !"false".equalsIgnoreCase((String) properties.get("ShowSumAccount"));
        }
        final String oidStr;
        final String nameStr;
        final String descStr;
        if (caseOid == null) {
            oidStr = "oid";
            nameStr = "attribute[Name]";
            descStr = "attribute[Description]";
            queryBuilder = new QueryBuilder(CIAccounting.AccountAbstract);
            queryBuilder.addWhereAttrMatchValue("Name", input + "*").setIgnoreCase(true);
            if (periode != null) {
                queryBuilder.addWhereAttrEqValue("PeriodeAbstractLink", periode.getId());
            }
            if (!showSumAccount) {
                queryBuilder.addWhereAttrEqValue("Summary", false);
            }
        } else {
            oidStr = "linkto[FromAccountAbstractLink].oid";
            nameStr = "linkto[FromAccountAbstractLink].attribute[Name]";
            descStr = "linkto[FromAccountAbstractLink].attribute[Description]";
            if (postfix.equalsIgnoreCase("debit")) {
                queryBuilder = new QueryBuilder(CIAccounting.Account2CaseDebit);
            } else {
                queryBuilder = new QueryBuilder(CIAccounting.Account2CaseCredit);
            }
            queryBuilder.addWhereAttrEqValue("ToCaseAbstractLink", Instance.get(caseOid).getId());
        }
        final MultiPrintQuery print = queryBuilder.getPrint();
        print.addSelect(oidStr, nameStr, descStr);
        print.execute();
        while (print.next()) {
            final String name = print.<String>getSelect(nameStr);
            if (caseOid == null
                            || ((input.length() < 2) || (input.length() > 1 && name.startsWith(input)))) {
                final String description = print.<String>getSelect(descStr);
                final String oid = print.<String>getSelect(oidStr);
                final String choice = name + " - " + description;
                final Map<String, String> map = new HashMap<String, String>();
                map.put(EFapsKey.AUTOCOMPLETE_KEY.getKey() , oid);
                map.put(EFapsKey.AUTOCOMPLETE_VALUE.getKey(), name);
                map.put(EFapsKey.AUTOCOMPLETE_CHOICE.getKey(), choice);
                map.put("description" + (postfix.equals("") ? "" : "_" + postfix), description);
                orderMap.put(choice, map);
            }
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
                clazz = (Classification) clazz.getParentClassification();
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
     */
    protected List<Type> getChildClassifications(final Classification _parent)
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
                sumerize(_parameter, "SumReport");
            } else if (((Attribute) obj).getName().equals("SumBooked")) {
                sumerize(_parameter, "SumBooked");
            }
        }
        return new Return();
    }

    /**
     *
     * @param _parameter    Parameter as passed from the efaps API
     * @param _attrName     name of tghe Attribute
     * @throws EFapsException on error
     */
    protected void sumerize(final Parameter _parameter,
                            final String _attrName)
        throws EFapsException
    {
        BigDecimal sum = BigDecimal.ZERO;
        final PrintQuery print = new PrintQuery(_parameter.getInstance());
        print.addSelect("linkto[ParentLink].oid");
        print.addSelect("linkto[ParentLink].attribute[Summary]");
        print.execute();
        final Boolean parentSum = print.<Boolean> getSelect("linkto[ParentLink].attribute[Summary]");
        if (parentSum != null && parentSum) {
            final Instance parentInst = Instance.get(print.<String> getSelect("linkto[ParentLink].oid"));

            final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.AccountAbstract);
            queryBldr.addWhereAttrEqValue("ParentLink", parentInst.getId());
            final MultiPrintQuery multi = new MultiPrintQuery(queryBldr.getQuery().executeWithoutAccessCheck());
            multi.addAttribute(_attrName);
            multi.executeWithoutAccessCheck();

            while (multi.next()) {
                final BigDecimal tmpsum = multi.<BigDecimal>getAttribute(_attrName);
                if (tmpsum != null) {
                    sum = sum.add(tmpsum);
                }
            }
            final Update update = new Update(parentInst);
            update.add(_attrName, sum);
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
                if (statusId.equals(Status.find(CIAccounting.TransactionStatus.uuid, "Booked"))) {
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
        final Map<?,?> props = (Map<?, ?>) _parameter.get(ParameterValues.PROPERTIES);
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
     * 
     * @param _parameter    Parameter as passed by the eFaps API
     * @return new Return
     * @throws EFapsException on error
     */
    public Return updateAccount(final Parameter _parameter) throws EFapsException {
    	Return ret = new Return();
    	String param = _parameter.getParameterValue("account");
    	if(param.length() > 0) {
	    	final Instance instance = _parameter.getInstance();
	    	final Update update = new Update(instance);
	    	update.add(CIAccounting.Account2CaseAbstract.FromAccountAbstractLink, param);
	    	update.execute();
	    	ret.put(ReturnValues.TRUE, "true");
    	} else {
    		ret.put(ReturnValues.VALUES, "Accounting_Account2Case4EditAccountForm/Account.updateAccount.NoRight");
    	}
    	return ret;
    }

}
