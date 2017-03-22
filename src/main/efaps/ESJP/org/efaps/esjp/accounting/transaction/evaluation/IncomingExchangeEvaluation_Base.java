/*
 * Copyright 2003 - 2017 The eFaps Team
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


package org.efaps.esjp.accounting.transaction.evaluation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.efaps.admin.event.Parameter;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.Instance;
import org.efaps.db.InstanceQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.esjp.accounting.Account2CaseInfo;
import org.efaps.esjp.accounting.Label;
import org.efaps.esjp.accounting.Period;
import org.efaps.esjp.accounting.transaction.AccountInfo;
import org.efaps.esjp.ci.CISales;
import org.efaps.esjp.erp.Currency;
import org.efaps.esjp.sales.Swap;
import org.efaps.esjp.sales.Swap_Base.SwapInfo;
import org.efaps.util.EFapsException;

/**
 * The Class IncomingExchangeEvaluation_Base.
 *
 * @author The eFaps Team
 */
@EFapsUUID("cbb10cd1-4d21-41a6-87ac-5e7b87b492e7")
@EFapsApplication("eFapsApp-Accounting")
public abstract class IncomingExchangeEvaluation_Base
    extends AbstractEvaluation
{
    /**
     * @param _parameter Parameter as passed by the eFaps API
     * @param _doc Document the calculation must be done for
     * @throws EFapsException on error
     */
    @Override
    protected void add2Doc4Case(final Parameter _parameter,
                                final DocumentInfo _doc)
        throws EFapsException
    {
        final Instance caseInst = Instance.get(_parameter.getParameterValue("case"));
        if (caseInst.isValid()) {
            _doc.setCaseInst(caseInst);

            final Instance periodInst = Period.evalCurrent(_parameter);
            final Instance periodCurrenycInstance = Period.evalCurrentCurrency(_parameter).getInstance();

            final List<Instance> labelInsts = new Label().getLabelInst4Documents(_parameter, _doc.getInstance(),
                            periodInst);

            final List<Account2CaseInfo> infos = Account2CaseInfo.getStandards(_parameter, caseInst);

            Collections.sort(infos, new Comparator<Account2CaseInfo>()
            {
                @Override
                public int compare(final Account2CaseInfo _o1,
                                   final Account2CaseInfo _o2)
                {
                    return _o1.getOrder().compareTo(_o2.getOrder());
                }
            });

            for (final Account2CaseInfo acc2caseInfo : infos) {
                final List<Account2CaseInfo> infoList = new ArrayList<>();
                if (acc2caseInfo.isEvalRelation()) {
                    infoList.addAll(getAcc2CaseInfo4Relation(_parameter, _doc, acc2caseInfo));
                } else {
                    infoList.add(acc2caseInfo);
                }

                for (final Account2CaseInfo acc2case : infoList) {
                    boolean isDefault = acc2caseInfo.isDefault();
                    final boolean currencyCheck;
                    if (acc2case.isCheckCurrency()) {
                        currencyCheck = _doc.getRateInfo().getCurrencyInstance().equals(acc2case.getCurrencyInstance());
                    } else {
                        currencyCheck = true;
                    }

                    if (acc2case.isCheckKey()) {
                        if (_doc.getKey2Amount(_parameter).containsKey(acc2case.getKey())) {
                            isDefault = true;
                            acc2case.setAmount(DocumentInfo_Base.getAmount4Map(_doc.getKey2Amount(_parameter).get(
                                            acc2case.getKey()), acc2case.getAmountConfig(), acc2case
                                                            .getAccountInstance()));
                        }
                    }

                    final boolean add = (isDefault || acc2case.isClassRelation() || acc2case.isCategoryProduct()
                                    || acc2case.isTreeView()) && currencyCheck;
                    if (add) {
                        final BigDecimal mul = new BigDecimal(acc2case.getNumerator()).setScale(12).divide(
                                        new BigDecimal(acc2case.getDenominator()), RoundingMode.HALF_UP);
                        final BigDecimal amountTmp = acc2case.isClassRelation() || acc2case.isCategoryProduct()
                                        || acc2case.isCheckKey() || acc2case.isTreeView()
                                        || acc2caseInfo.isEvalRelation() ? acc2case.getAmount()
                                                        : _doc.getAmount(acc2case);
                        final BigDecimal accAmount = mul.multiply(amountTmp).setScale(2, RoundingMode.HALF_UP);
                        final BigDecimal accAmountRate = Currency.convertToCurrency(_parameter, accAmount, _doc
                                        .getRateInfo(), _doc.getRatePropKey(), periodCurrenycInstance);

                        final AccountInfo account = new AccountInfo(acc2case.getAccountInstance(), accAmount);
                        account.setRemark(acc2case.getRemark());
                        if (acc2case.isApplyLabel() && !labelInsts.isEmpty()) {
                            account.setLabelInst(labelInsts.get(0));
                        }
                        account.setAmountRate(accAmountRate);
                        if (_doc.getInstance() != null) {
                            account.setRateInfo(_doc.getRateInfo(), _doc.getInstance().getType().getName());
                        } else {
                            account.setRateInfo(_doc.getRateInfo(), getProperty(_parameter, "Type4RateInfo"));
                        }
                        if (acc2case.isCredit()) {
                            account.setPostFix("_Credit");
                            _doc.addCredit(account);
                        } else {
                            account.setPostFix("_Debit");
                            _doc.addDebit(account);
                        }
                    }
                }
            }
        }
    }

    /**
     * Gets the acc two case info for relation.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @param _doc the doc
     * @param _acc2caseInfo the acc 2 case info
     * @return the acc two case info for relation
     * @throws EFapsException on error
     */
    protected List<Account2CaseInfo> getAcc2CaseInfo4Relation(final Parameter _parameter,
                                                              final DocumentInfo _doc,
                                                              final Account2CaseInfo _acc2caseInfo)
        throws EFapsException
    {
        final List<Account2CaseInfo> ret = new ArrayList<>();

        final QueryBuilder swapQueryBldr = new QueryBuilder(CISales.Document2Document4Swap);
        swapQueryBldr.setOr(true);
        swapQueryBldr.addWhereAttrEqValue(CISales.Document2Document4Swap.FromLink, _doc.getInstance());
        swapQueryBldr.addWhereAttrEqValue(CISales.Document2Document4Swap.ToLink, _doc.getInstance());
        final InstanceQuery swapQuery = swapQueryBldr.getQuery();
        final List<Instance> relInst = swapQuery.execute();
        if (!relInst.isEmpty()) {
            for (final SwapInfo info : Swap.getSwapInfos(_parameter, _doc.getInstance(), relInst).values()) {
                final Account2CaseInfo acc2caseInfo = Account2CaseInfo.getAccount2CaseInfo(_acc2caseInfo.getInstance());
                ret.add(acc2caseInfo);
                acc2caseInfo.setAmount(info.getAmount()).setCurrencyInstance(info.getCurrencyInstance())
                    .setRemark(info.getDocName());
            }
        }
        return ret;
    }
}
