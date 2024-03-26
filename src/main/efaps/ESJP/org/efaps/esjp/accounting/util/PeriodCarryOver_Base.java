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
package org.efaps.esjp.accounting.util;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import org.apache.commons.collections4.BidiMap;
import org.apache.commons.collections4.bidimap.DualHashBidiMap;
import org.efaps.admin.datamodel.Status;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.AttributeQuery;
import org.efaps.db.Insert;
import org.efaps.db.Instance;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.PrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.db.Update;
import org.efaps.esjp.admin.common.systemconfiguration.SystemConf;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.ci.CIFormAccounting;
import org.efaps.esjp.db.InstanceUtils;
import org.efaps.util.EFapsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Class PeriodCarryOver.
 */
@EFapsUUID("23fea1f5-e123-481d-90da-efed05fe86f0")
@EFapsApplication("eFapsApp-Accounting")
public abstract class PeriodCarryOver_Base
{

    /**
     * Logger for this class.
     */
    private static final Logger LOG = LoggerFactory.getLogger(PeriodCarryOver.class);

    /**
     * Creates the from period.
     *
     * @param _parameter the parameter
     * @param _basePeriodInst the base period inst
     * @throws EFapsException the eFaps exception
     */
    public void createFromPeriod(final Parameter _parameter, final Instance _basePeriodInst)
        throws EFapsException
    {
        LOG.info("Init carry over for Period");
        final Instance periodInst = createPeriod(_parameter, _basePeriodInst);

        final BidiMap<Instance, Instance> existing2new = new DualHashBidiMap<>();

        createAccounts(_parameter, _basePeriodInst, periodInst, existing2new);
        createReports(_parameter, _basePeriodInst, periodInst, existing2new);
        createCases(_parameter, _basePeriodInst, periodInst, existing2new);
        createPeriod2Account(_parameter, _basePeriodInst, periodInst, existing2new);
        createAccount2Account(_parameter, _basePeriodInst, periodInst, existing2new);

    }

    /**
     * Creates the reports.
     *
     * @param _parameter the parameter
     * @param _basePeriodInst the base period inst
     * @param _periodInst the period inst
     * @param _existing2new the existing 2 new
     * @throws EFapsException the eFaps exception
     */
    protected void createReports(final Parameter _parameter, final Instance _basePeriodInst, final Instance _periodInst,
                                 final BidiMap<Instance, Instance> _existing2new)
        throws EFapsException
    {
        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.ReportAbstract);
        queryBldr.addWhereAttrEqValue(CIAccounting.ReportAbstract.PeriodLink, _basePeriodInst);
        final MultiPrintQuery multi = queryBldr.getPrint();
        multi.addAttribute(CIAccounting.ReportAbstract.Name, CIAccounting.ReportAbstract.Description,
                        CIAccounting.ReportAbstract.Numbering, CIAccounting.ReportAbstract.Int1);
        multi.execute();
        while (multi.next()) {
            final Instance currentRepInst = multi.getCurrentInstance();
            final Insert insert = new Insert(currentRepInst.getType());
            insert.add(CIAccounting.ReportAbstract.Name, multi.<Object>getAttribute(CIAccounting.ReportAbstract.Name));
            insert.add(CIAccounting.ReportAbstract.Description, multi.<Object>getAttribute(
                            CIAccounting.ReportAbstract.Description));
            insert.add(CIAccounting.ReportAbstract.Numbering, multi.<Object>getAttribute(
                            CIAccounting.ReportAbstract.Numbering));
            insert.add(CIAccounting.ReportAbstract.PeriodLink, _periodInst);
            insert.add(CIAccounting.ReportAbstract.Int1, multi.<Object>getAttribute(CIAccounting.ReportAbstract.Int1));
            insert.execute();
            _existing2new.put(currentRepInst, insert.getInstance());
        }
    }

    /**
     * Creates the account two account.
     *
     * @param _parameter the parameter
     * @param _basePeriodInst the base period inst
     * @param _periodInst the period inst
     * @param _existing2new the existing 2 new
     * @throws EFapsException the eFaps exception
     */
    protected void createAccount2Account(final Parameter _parameter, final Instance _basePeriodInst,
                                         final Instance _periodInst, final BidiMap<Instance, Instance> _existing2new)
        throws EFapsException
    {
        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.Account2AccountAbstract);

        final QueryBuilder attrQueryBldr = new QueryBuilder(CIAccounting.AccountAbstract);
        attrQueryBldr.addWhereAttrEqValue(CIAccounting.AccountAbstract.PeriodAbstractLink, _basePeriodInst);
        final AttributeQuery attrQuery = attrQueryBldr.getAttributeQuery(CIAccounting.AccountAbstract.ID);
        queryBldr.addWhereAttrInQuery(CIAccounting.Account2AccountAbstract.FromAccountLink, attrQuery);
        queryBldr.addWhereAttrInQuery(CIAccounting.Account2AccountAbstract.ToAccountLink, attrQuery);

        final MultiPrintQuery multi = queryBldr.getPrint();
        final SelectBuilder selFromAccInst = SelectBuilder.get().linkto(
                        CIAccounting.Account2AccountAbstract.FromAccountLink).instance();
        final SelectBuilder selToAccInst = SelectBuilder.get().linkto(
                        CIAccounting.Account2AccountAbstract.ToAccountLink).instance();
        multi.addSelect(selFromAccInst, selToAccInst);
        multi.addAttribute(CIAccounting.Account2AccountAbstract.Denominator,
                        CIAccounting.Account2AccountAbstract.Numerator, CIAccounting.Account2AccountAbstract.Config);
        multi.execute();
        while (multi.next()) {
            final Instance fromAccInst = multi.getSelect(selFromAccInst);
            final Instance toAccInst = multi.getSelect(selToAccInst);
            final Instance newFromAccInst = _existing2new.get(fromAccInst);
            final Instance newToAccInst = _existing2new.get(toAccInst);
            if (InstanceUtils.isValid(newFromAccInst) && InstanceUtils.isValid(newToAccInst)) {
                final Insert insert = new Insert(multi.getCurrentInstance().getType());
                insert.add(CIAccounting.Account2AccountAbstract.FromAccountLink, newFromAccInst);
                insert.add(CIAccounting.Account2AccountAbstract.ToAccountLink, newToAccInst);
                insert.add(CIAccounting.Account2AccountAbstract.Denominator, multi.<Object>getAttribute(
                                CIAccounting.Account2AccountAbstract.Denominator));
                insert.add(CIAccounting.Account2AccountAbstract.Numerator, multi.<Object>getAttribute(
                                CIAccounting.Account2AccountAbstract.Numerator));
                insert.add(CIAccounting.Account2AccountAbstract.Config, multi.<Object>getAttribute(
                                CIAccounting.Account2AccountAbstract.Config));
                insert.execute();
            }
        }
        LOG.info("created Account2Account");
    }

    /**
     * Creates the period two account.
     *
     * @param _parameter the parameter
     * @param _basePeriodInst the base period inst
     * @param _periodInst the period inst
     * @param _existing2new the existing 2 new
     * @throws EFapsException the eFaps exception
     */
    protected void createPeriod2Account(final Parameter _parameter, final Instance _basePeriodInst,
                                        final Instance _periodInst, final BidiMap<Instance, Instance> _existing2new)
        throws EFapsException
    {

        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.Period2Account);
        queryBldr.addWhereAttrEqValue(CIAccounting.Period2Account.PeriodLink, _basePeriodInst);
        final MultiPrintQuery multi = queryBldr.getPrint();
        final SelectBuilder selAccountInst = SelectBuilder.get().linkto(
                        CIAccounting.Period2Account.FromAccountAbstractLink).instance();
        multi.addSelect(selAccountInst);
        multi.addAttribute(CIAccounting.Period2Account.SalesAccountLink);
        multi.execute();
        while (multi.next()) {
            final Instance accountInst = multi.getSelect(selAccountInst);
            final Instance targetAccInst = _existing2new.get(accountInst);
            if (InstanceUtils.isValid(targetAccInst)) {
                final Insert insert = new Insert(CIAccounting.Period2Account);
                insert.add(CIAccounting.Period2Account.PeriodLink, _periodInst);
                insert.add(CIAccounting.Period2Account.FromAccountAbstractLink, targetAccInst);
                insert.add(CIAccounting.Period2Account.SalesAccountLink, multi.<Object>getAttribute(
                                CIAccounting.Period2Account.SalesAccountLink));
                insert.execute();
            }
        }
        LOG.info("created Period2Acount");
    }

    /**
     * Creates the cases.
     *
     * @param _parameter the parameter
     * @param _basePeriodInst the base period inst
     * @param _periodInst the period inst
     * @param _existing2new the existing 2 new
     * @throws EFapsException the eFaps exception
     */
    protected void createCases(final Parameter _parameter, final Instance _basePeriodInst, final Instance _periodInst,
                               final BidiMap<Instance, Instance> _existing2new)
        throws EFapsException
    {
        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.CaseAbstract);
        queryBldr.addWhereAttrEqValue(CIAccounting.CaseAbstract.PeriodAbstractLink, _basePeriodInst);
        queryBldr.addWhereAttrEqValue(CIAccounting.CaseAbstract.StatusAbstract, Status.find(
                        CIAccounting.CaseStatus.Active));
        final MultiPrintQuery multi = queryBldr.getPrint();
        multi.addAttribute(CIAccounting.CaseAbstract.ArchiveConfig, CIAccounting.CaseAbstract.Description,
                        CIAccounting.CaseAbstract.Label, CIAccounting.CaseAbstract.Name,
                        CIAccounting.CaseAbstract.StatusAbstract, CIAccounting.CaseAbstract.SummarizeConfig);
        multi.execute();
        while (multi.next()) {
            final Instance currentCaseInst = multi.getCurrentInstance();
            final String name = multi.getAttribute(CIAccounting.CaseAbstract.Name);
            final Insert insert = new Insert(currentCaseInst.getType());
            insert.add(CIAccounting.CaseAbstract.PeriodAbstractLink, _periodInst);
            insert.add(CIAccounting.CaseAbstract.ArchiveConfig, multi.<Object>getAttribute(
                            CIAccounting.CaseAbstract.ArchiveConfig));
            insert.add(CIAccounting.CaseAbstract.Description, multi.<Object>getAttribute(
                            CIAccounting.CaseAbstract.Description));
            insert.add(CIAccounting.CaseAbstract.Label, multi.<Object>getAttribute(CIAccounting.CaseAbstract.Label));
            insert.add(CIAccounting.CaseAbstract.Name, name);
            insert.add(CIAccounting.CaseAbstract.StatusAbstract, multi.<Object>getAttribute(
                            CIAccounting.CaseAbstract.StatusAbstract));
            insert.add(CIAccounting.CaseAbstract.SummarizeConfig, multi.<Object>getAttribute(
                            CIAccounting.CaseAbstract.SummarizeConfig));
            insert.execute();
            LOG.info("added Case {}", name);
            final Instance newCaseInst = insert.getInstance();
            _existing2new.put(currentCaseInst, newCaseInst);

            createAccount2Case(_parameter, currentCaseInst, _existing2new);
            createReport2Case(_parameter, currentCaseInst, _existing2new);
        }
    }

    /**
     * Creates the report two case.
     *
     * @param _parameter the parameter
     * @param _currentCaseInst the current case inst
     * @param _existing2new the existing 2 new
     * @throws EFapsException the e faps exception
     */
    protected void createReport2Case(final Parameter _parameter, final Instance _currentCaseInst,
                                     final BidiMap<Instance, Instance> _existing2new)
        throws EFapsException
    {
        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.Report2Case);
        queryBldr.addWhereAttrEqValue(CIAccounting.Report2Case.ToLink, _currentCaseInst);
        final MultiPrintQuery multi = queryBldr.getPrint();
        final SelectBuilder selRepInst = SelectBuilder.get().linkto(CIAccounting.Report2Case.FromLink).instance();
        multi.addSelect(selRepInst);
        multi.execute();
        while (multi.next()) {
            final Instance repInst = multi.getSelect(selRepInst);
            final Instance newRepInst = _existing2new.get(repInst);
            final Instance newCaseInst = _existing2new.get(_currentCaseInst);
            if (InstanceUtils.isValid(newRepInst) && InstanceUtils.isValid(newCaseInst)) {
                final Insert insert = new Insert(multi.getCurrentInstance().getType());
                insert.add(CIAccounting.Report2Case.ToLink, newCaseInst);
                insert.add(CIAccounting.Report2Case.FromLink, newRepInst);
                insert.execute();
            }
        }
    }

    /**
     * Creates the account two case.
     *
     * @param _parameter the parameter
     * @param _currentCaseInst the current case inst
     * @param _existing2new the existing 2 new
     * @throws EFapsException the eFaps exception
     */
    protected void createAccount2Case(final Parameter _parameter, final Instance _currentCaseInst,
                                      final BidiMap<Instance, Instance> _existing2new)
        throws EFapsException
    {

        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.Account2CaseAbstract);
        queryBldr.addWhereAttrEqValue(CIAccounting.Account2CaseAbstract.ToCaseAbstractLink, _currentCaseInst);
        final MultiPrintQuery multi = queryBldr.getPrint();
        final SelectBuilder selAccountInst = SelectBuilder.get().linkto(
                        CIAccounting.Account2CaseAbstract.FromAccountAbstractLink).instance();
        multi.addSelect(selAccountInst);
        multi.addAttribute(CIAccounting.Account2CaseAbstract.AmountConfig);
        multi.execute();
        while (multi.next()) {
            final Instance currentInst = multi.getCurrentInstance();
            final Insert insert = new Insert(currentInst.getType());
            insert.add(CIAccounting.Account2CaseAbstract.AmountConfig, multi.<Object>getAttribute(
                            CIAccounting.Account2CaseAbstract.AmountConfig));
            insert.add(CIAccounting.Account2CaseAbstract.Config, multi.<Object>getAttribute(
                            CIAccounting.Account2CaseAbstract.Config));
            insert.add(CIAccounting.Account2CaseAbstract.CurrencyLink, multi.<Object>getAttribute(
                            CIAccounting.Account2CaseAbstract.CurrencyLink));
            insert.add(CIAccounting.Account2CaseAbstract.Denominator, multi.<Object>getAttribute(
                            CIAccounting.Account2CaseAbstract.Denominator));
            insert.add(CIAccounting.Account2CaseAbstract.Key, multi.<Object>getAttribute(
                            CIAccounting.Account2CaseAbstract.Key));
            insert.add(CIAccounting.Account2CaseAbstract.Numerator, multi.<Object>getAttribute(
                            CIAccounting.Account2CaseAbstract.Numerator));
            insert.add(CIAccounting.Account2CaseAbstract.Order, multi.<Object>getAttribute(
                            CIAccounting.Account2CaseAbstract.Order));
            insert.add(CIAccounting.Account2CaseAbstract.Remark, multi.<Object>getAttribute(
                            CIAccounting.Account2CaseAbstract.Remark));
            insert.add(CIAccounting.Account2CaseAbstract.LinkValue, multi.<Object>getAttribute(
                            CIAccounting.Account2CaseAbstract.LinkValue));
            final Instance accInst = multi.getSelect(selAccountInst);

            insert.add(CIAccounting.Account2CaseAbstract.FromAccountAbstractLink, _existing2new.get(accInst));
            insert.add(CIAccounting.Account2CaseAbstract.ToCaseAbstractLink, _existing2new.get(_currentCaseInst));
            insert.execute();
        }
    }

    /**
     * Creates the accounts.
     *
     * @param _parameter the parameter
     * @param _basePeriodInst the base period inst
     * @param _periodInst the period inst
     * @param _existing2new the existing 2 new
     * @throws EFapsException the eFaps exception
     */
    protected void createAccounts(final Parameter _parameter, final Instance _basePeriodInst,
                                  final Instance _periodInst, final BidiMap<Instance, Instance> _existing2new)
        throws EFapsException
    {
        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.AccountAbstract);
        queryBldr.addWhereAttrEqValue(CIAccounting.AccountAbstract.PeriodAbstractLink, _basePeriodInst);
        queryBldr.addWhereAttrEqValue(CIAccounting.AccountAbstract.Active, true);
        final MultiPrintQuery multi = queryBldr.getPrint();
        final SelectBuilder selParentInst = SelectBuilder.get().linkto(CIAccounting.AccountAbstract.ParentLink)
                        .instance();
        multi.addSelect(selParentInst);
        multi.addAttribute(CIAccounting.AccountAbstract.Description, CIAccounting.AccountAbstract.Name,
                        CIAccounting.AccountAbstract.Summary);
        multi.execute();
        final Map<Instance, Instance> child2parent = new HashMap<>();
        while (multi.next()) {
            final Instance currentAccInst = multi.getCurrentInstance();
            final Instance parentInst = multi.getSelect(selParentInst);
            if (InstanceUtils.isValid(parentInst)) {
                child2parent.put(currentAccInst, parentInst);
            }
            final String name = multi.getAttribute(CIAccounting.AccountAbstract.Name);
            final String descr = multi.<String>getAttribute(CIAccounting.AccountAbstract.Description);
            final Insert insert = new Insert(currentAccInst.getType());
            insert.add(CIAccounting.AccountAbstract.PeriodAbstractLink, _periodInst);
            insert.add(CIAccounting.AccountAbstract.Name, name);
            insert.add(CIAccounting.AccountAbstract.Description, descr);
            insert.add(CIAccounting.AccountAbstract.Summary, multi.<Boolean>getAttribute(
                            CIAccounting.AccountAbstract.Summary));
            insert.add(CIAccounting.AccountAbstract.Active, true);
            insert.execute();
            final Instance newAccInst = insert.getInstance();

            _existing2new.put(currentAccInst, newAccInst);
            LOG.info("added Account {} - {}", name, descr);
        }
        for (final Entry<Instance, Instance> entry : child2parent.entrySet()) {
            final Instance childInst = _existing2new.get(entry.getKey());
            final Instance parentInst = _existing2new.get(entry.getValue());
            final Update update = new Update(childInst);
            update.add(CIAccounting.AccountAbstract.ParentLink, parentInst);
            update.execute();
        }
    }

    /**
     * Creates the period.
     *
     * @param _parameter the parameter
     * @param _periodInst the period inst
     * @return the instance
     * @throws EFapsException the eFaps exception
     */
    protected Instance createPeriod(final Parameter _parameter, final Instance _periodInst)
        throws EFapsException
    {
        final PrintQuery print = new PrintQuery(_periodInst);
        print.addAttribute(CIAccounting.Period.CurrencyLink);
        print.execute();

        final Insert insert = new Insert(CIAccounting.Period);
        insert.add(CIAccounting.Period.Name, _parameter.getParameterValue(
                        CIFormAccounting.Accounting_PeriodCreateFromForm.name.name));
        insert.add(CIAccounting.Period.FromDate, _parameter.getParameterValue(
                        CIFormAccounting.Accounting_PeriodCreateFromForm.fromDate.name));
        insert.add(CIAccounting.Period.ToDate, _parameter.getParameterValue(
                        CIFormAccounting.Accounting_PeriodCreateFromForm.toDate.name));
        insert.add(CIAccounting.Period.CurrencyLink, print.<Long>getAttribute(CIAccounting.Period.CurrencyLink));
        insert.add(CIAccounting.Period.Status, Status.find(CIAccounting.PeriodStatus.Open));
        insert.execute();
        final Instance ret = insert.getInstance();
        final Properties props = Accounting.getSysConfig().getObjectAttributeValueAsProperties(_periodInst);
        final SystemConf conf = new SystemConf();
        try {
            final StringWriter writer = new StringWriter();
            props.store(writer, "");
            conf.addObjectAttribute(Accounting.getSysConfig().getUUID(), ret, writer.toString());
        } catch (final IOException e) {
            LOG.error("Catched", e);
        }
        LOG.info("Created base Period");
        return ret;
    }

}
