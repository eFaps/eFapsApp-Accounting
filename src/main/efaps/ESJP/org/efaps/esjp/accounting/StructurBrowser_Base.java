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

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.efaps.admin.datamodel.Type;
import org.efaps.admin.event.EventExecution;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Parameter.ParameterValues;
import org.efaps.admin.event.Return;
import org.efaps.admin.event.Return.ReturnValues;
import org.efaps.admin.program.esjp.EFapsRevision;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.Instance;
import org.efaps.db.SearchQuery;
import org.efaps.ui.wicket.models.objects.UIStructurBrowser;
import org.efaps.ui.wicket.models.objects.UIStructurBrowser.ExecutionStatus;
import org.efaps.util.EFapsException;

/**
 * TODO description!
 *
 * @author The eFasp Team
 * @version $Id$
 */
@EFapsUUID("21b4e990-00b8-44bb-9896-80719fcf8c81")
@EFapsRevision("$Rev$")
public abstract class StructurBrowser_Base implements EventExecution
{

    /**
     * @param _parameter Parameter
     * @throws EFapsException on error
     * @return Return
     */
    public Return execute(final Parameter _parameter) throws EFapsException
    {
        Return ret = null;

        final UIStructurBrowser strBro = (UIStructurBrowser) _parameter.get(ParameterValues.CLASS);
        final ExecutionStatus status = strBro.getExecutionStatus();
        if (status.equals(ExecutionStatus.EXECUTE)) {
            ret = internalExecute(_parameter);
        } else if (status.equals(ExecutionStatus.CHECKFORCHILDREN)) {
            ret = checkForChildren(_parameter.getInstance());
        } else if (status.equals(ExecutionStatus.ADDCHILDREN)) {
            ret = addChildren(_parameter.getInstance());
        } else if (status.equals(ExecutionStatus.SORT)) {
            ret = sort(strBro);
        }
        return ret;
    }

    /**
     * Method to get a list of instances the structurbrowser will be filled
     * with.
     * @param instance
     *
     * @param _parameter as passed from eFaps API.
     * @return Return with instances
     * @throws EFapsException on error
     */
    private Return internalExecute(final Parameter _parameter) throws EFapsException
    {
        final Return ret = new Return();

        final Map<?, ?> properties = (Map<?, ?>) _parameter.get(ParameterValues.PROPERTIES);

        final String type = (String) properties.get("Types");
        final String expand = (String) properties.get("Expand");
        final boolean expandChild = "true".equalsIgnoreCase((String) properties.get("ExpandChildTypes"));
        boolean check4parent = false;
        final SearchQuery query = new SearchQuery();
        if (type != null) {
            query.setQueryTypes(type);
        } else {
            query.setExpand(_parameter.getInstance(), expand);
            if (_parameter.getInstance().getType().isKindOf(Type.get("Accounting_Periode"))) {
                query.addSelect("ParentLink");
                check4parent = true;
            }
        }
        query.setExpandChildTypes(expandChild);
        query.addSelect("OID");
        query.execute();

        final Map<Instance, Boolean> tree = new LinkedHashMap<Instance, Boolean>();
        while (query.next()) {
            if (check4parent) {
                if (query.get("ParentLink") == null) {
                    tree.put(Instance.get((String) query.get("OID")), null);
                }
            } else {
                tree.put(Instance.get((String) query.get("OID")), null);
            }
        }
        ret.put(ReturnValues.VALUES, tree);
        return ret;
    }

    /**
     * Method to check if an instance has children. It is used in the tree to
     * determine if a "plus" to open the children must be rendered.
     *
     * @param _instance Instance to check for children
     * @return Return with true or false
     * @throws EFapsException on error
     */
    private Return checkForChildren(final Instance _instance) throws EFapsException
    {
        final Return ret = new Return();
        final SearchQuery query = new SearchQuery();
        query.setQueryTypes("Accounting_AccountAbstract");
        query.setExpandChildTypes(true);
        query.addWhereExprEqValue("ParentLink", _instance.getId());
        query.execute();

        if (query.next()) {
            ret.put(ReturnValues.TRUE, true);
        }
        return ret;
    }

    /**
     * Method to add the children to an instance. It is used to expand the
     * children of a node in the tree.
     *
     * @param _instance Instance the children must be retrieved for.
     * @return Return with instances
     * @throws EFapsException on error
     */
    private Return addChildren(final Instance _instance) throws EFapsException
    {
        final Return ret = new Return();

        final SearchQuery query = new SearchQuery();
        query.setQueryTypes("Accounting_AccountAbstract");
        query.setExpandChildTypes(true);
        query.addWhereExprEqValue("ParentLink", _instance.getId());
        query.addSelect("OID");
        query.execute();

        final Map<Instance, Boolean> map = new LinkedHashMap<Instance, Boolean>();
        while (query.next()) {
            map.put(Instance.get((String) query.get("OID")), null);
        }
        ret.put(ReturnValues.VALUES, map);
        return ret;
    }

    /**
     * Method to sort the values of the StructurBrowser.
     *
     * @param _structurBrowser _sructurBrowser to be sorted
     * @return empty Return;
     */
    private Return sort(final UIStructurBrowser _structurBrowser)
    {
        Collections.sort(_structurBrowser.getChilds(), new Comparator<UIStructurBrowser>() {

            public int compare(final UIStructurBrowser _structurBrowser1, final UIStructurBrowser _structurBrowser2)
            {

                final String value1 = getSortString(_structurBrowser1);
                final String value2 = getSortString(_structurBrowser2);

                return value1.compareTo(value2);
            }

            private String getSortString(final UIStructurBrowser _structurBrowser)
            {
                final StringBuilder ret = new StringBuilder();
                try {
                    if (_structurBrowser.getInstance() != null) {
                        final Type type = _structurBrowser.getInstance().getType();
//                        if (type.equals(Type.get(TYPE_NODEDIRECTORY))) {
//                            ret.append(0);
//                        } else if (type.equals(Type.get(TYPE_NODEFILE))) {
//                            ret.append(1);
//                        }
                    }
                } catch (final EFapsException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                ret.append(_structurBrowser.getLabel());
                return ret.toString();
            }
        });
        return new Return();
    }

}
