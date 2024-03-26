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
package org.efaps.esjp.accounting;
import java.util.ArrayList;
import java.util.List;

import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Return;
import org.efaps.admin.event.Return.ReturnValues;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.admin.program.esjp.Listener;
import org.efaps.db.Instance;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.esjp.accounting.listener.IOnLabel;
import org.efaps.esjp.accounting.util.Accounting;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.ci.CIHumanResource;
import org.efaps.update.AppDependency;
import org.efaps.update.util.InstallationException;
import org.efaps.util.EFapsException;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 * 
 */
@EFapsUUID("35d9cfa6-4231-42bf-9018-246ace6fe095")
@EFapsApplication("eFapsApp-Accounting")
public abstract class Label_Base
{

    /**
     * Check access to label.
     *
     * @param _parameter Paremeter as passed from the eFaPS API
     * @return Return
     */
    public Return accessCheck4Label(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        if (!Accounting.getSysConfig().getAttributeValueAsBoolean("DeactivateLabel")) {
            ret.put(ReturnValues.TRUE, true);
        }
        return ret;
    }

    /**
     * @param _parameter Paremeter as passed from the eFaPS API
     * @param _instance document of instance
     * @return list of labels
     * @throws EFapsException on error
     */
    public List<Instance> getLabelInst4Documents(final Parameter _parameter,
                                                 final Instance _instance)
        throws EFapsException
    {
        final List<Instance> ret = new ArrayList<>();

        // evaluate own ones
        try {
            if (AppDependency.getAppDependency("eFapsApp-HumanResource").isMet()) {
                final QueryBuilder attrQueryBldr = new QueryBuilder(CIHumanResource.Department2DocumentAbstract);
                attrQueryBldr.addWhereAttrEqValue(CIHumanResource.Department2DocumentAbstract.ToAbstractLink, _instance);

                final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.LabelDepartment2Department);
                queryBldr.addWhereAttrInQuery(CIAccounting.LabelDepartment2Department.ToLink,
                                attrQueryBldr.getAttributeQuery(
                                                CIHumanResource.Department2DocumentAbstract.FromAbstractLink));
                final MultiPrintQuery multi = queryBldr.getPrint();
                final SelectBuilder selInst = SelectBuilder.get()
                                .linkto(CIAccounting.LabelDepartment2Department.FromLink)
                                .instance();
                multi.addSelect(selInst);
                multi.execute();
                while (multi.next()) {
                    ret.add(multi.<Instance>getSelect(selInst));
                }
            }
        } catch (final InstallationException e) {
            throw new EFapsException("Dependency validation vailed.", e);
        }
        // let others participate
        for (final IOnLabel listener : Listener.get().<IOnLabel>invoke(IOnLabel.class)) {
            ret.addAll(listener.evalLabelsForDocument(_parameter, _instance));
        }
        return ret;
    }

    /**
     * @param _parameter Paremeter as passed from the eFaPS API
     * @param _instance document of instance
     * @param _periodInst Instance of the Period
     * @return list of labels
     * @throws EFapsException on error
     */
    public List<Instance> getLabelInst4Documents(final Parameter _parameter,
                                                 final Instance _instance,
                                                 final Instance _periodInst)
        throws EFapsException
    {
        final List<Instance> ret = new ArrayList<>();
        final List<Instance> labelInst = getLabelInst4Documents(_parameter, _instance);

        final MultiPrintQuery multi = new MultiPrintQuery(labelInst);
        final SelectBuilder selPeriodInst = SelectBuilder.get()
                        .linkto(CIAccounting.LabelAbstract.PeriodAbstractLink)
                        .instance();
        multi.addSelect(selPeriodInst);
        multi.execute();
        while (multi.next()) {
            final Instance periodInst = multi.getSelect(selPeriodInst);
            if (periodInst.equals(_periodInst)) {
                ret.add(multi.getCurrentInstance());
            }
        }
        return ret;
    }
}
