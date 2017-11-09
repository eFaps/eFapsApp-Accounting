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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StrSubstitutor;
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
import org.efaps.esjp.accounting.util.Accounting;
import org.efaps.esjp.ci.CISales;
import org.efaps.esjp.erp.Currency;
import org.efaps.esjp.erp.RateInfo;
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

            Collections.sort(infos, (_o1,
             _o2) -> _o1.getOrder().compareTo(_o2.getOrder()));
            evalAccount2CaseInfo4Relation(_parameter, _doc, infos);

            for (final Account2CaseInfo acc2case : infos) {
                boolean isDefault = acc2case.isDefault();
                final boolean currencyCheck;
                if (acc2case.isCheckCurrency()) {
                    currencyCheck = _doc.getRateInfo().getCurrencyInstance().equals(acc2case.getCurrencyInstance());
                } else {
                    currencyCheck = true;
                }

                if (acc2case.isCheckKey()) {
                    if (_doc.getKey2Amount(_parameter).containsKey(acc2case.getKey())) {
                        isDefault = true;
                        acc2case.setAmount(DocumentInfo_Base.getAmount4Map(_doc.getKey2Amount(_parameter).get(acc2case
                                        .getKey()), acc2case.getAmountConfig(), acc2case.getAccountInstance()));
                    }
                }

                final boolean add = (isDefault || acc2case.isClassRelation() || acc2case.isCategoryProduct() || acc2case
                                .isTreeView()) && currencyCheck;
                if (add) {
                    final BigDecimal mul = new BigDecimal(acc2case.getNumerator()).setScale(12).divide(new BigDecimal(
                                    acc2case.getDenominator()), RoundingMode.HALF_UP);
                    final BigDecimal amountTmp = acc2case.isClassRelation() || acc2case.isCategoryProduct() || acc2case
                                    .isCheckKey() || acc2case.isTreeView() || acc2case.isEvalRelation() ? acc2case
                                                    .getAmount() : _doc.getAmount(acc2case);
                    final BigDecimal accAmount = mul.multiply(amountTmp).setScale(2, RoundingMode.HALF_UP);
                    RateInfo rateInfo;
                    if (acc2case.isDeactCurrencyCheck() && !_doc.getRateInfo().getCurrencyInstance().equals(acc2case
                                    .getCurrencyInstance()) && !acc2case.getCurrencyInstance().equals(
                                                    periodCurrenycInstance)) {
                        rateInfo = new Currency().evaluateRateInfo(_parameter, _doc.getDate(), acc2case
                                        .getCurrencyInstance());
                    } else {
                        rateInfo = _doc.getRateInfo();
                    }

                    final BigDecimal accAmountRate = Currency.convertToCurrency(_parameter, accAmount, rateInfo, _doc
                                    .getRatePropKey(), periodCurrenycInstance);

                    final AccountInfo account = new AccountInfo(acc2case.getAccountInstance(), accAmount);
                    account.setRemark(acc2case.getRemark());
                    if (acc2case.isApplyLabel() && !labelInsts.isEmpty()) {
                        account.setLabelInst(labelInsts.get(0));
                    }
                    account.setAmountRate(accAmountRate);
                    if (_doc.getInstance() != null) {
                        account.setRateInfo(rateInfo, _doc.getRatePropKey());
                    } else {
                        account.setRateInfo(rateInfo, getProperty(_parameter, "Type4RateInfo"));
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

    /**
     * Gets the acc two case info for relation.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @param _doc the doc
     * @param _infos the infos
     * @throws EFapsException on error
     */
    @Override
    protected void evalAccount2CaseInfo4Relation(final Parameter _parameter,
                                                 final DocumentInfo _doc,
                                                 final List<Account2CaseInfo> _infos)
        throws EFapsException
    {
        final List<Account2CaseInfo> tempInfos = new ArrayList<>();
        for (final Account2CaseInfo acc2caseInfo : _infos) {
            if (acc2caseInfo.isEvalRelation()) {
                boolean added = false;
                final QueryBuilder swapQueryBldr = new QueryBuilder(CISales.Document2Document4Swap);
                swapQueryBldr.setOr(true);
                swapQueryBldr.addWhereAttrEqValue(CISales.Document2Document4Swap.FromLink, _doc.getInstance());
                swapQueryBldr.addWhereAttrEqValue(CISales.Document2Document4Swap.ToLink, _doc.getInstance());
                final InstanceQuery swapQuery = swapQueryBldr.getQuery();
                final List<Instance> relInst = swapQuery.execute();
                if (!relInst.isEmpty()) {
                    final String remark = acc2caseInfo.getRemark();
                    acc2caseInfo.setRemark("");
                    for (final SwapInfo info : Swap.getSwapInfos(_parameter, _doc.getInstance(), relInst).values()) {
                        if (acc2caseInfo.isSeparately()) {
                            final Account2CaseInfo acc2caseInfoTmp = Account2CaseInfo.getAccount2CaseInfo(acc2caseInfo
                                            .getInstance());
                            tempInfos.add(acc2caseInfoTmp);
                            acc2caseInfoTmp.setAmount(info.getAmount()).setCurrencyInstance(info.getCurrencyInstance())
                                            .setDeactCurrencyCheck(true);
                            final Map<String, String> subMap = new HashMap<>();
                            subMap.put(Accounting.RemarkSubstitutorKeys.RELDOC_NAME.name(), info.getDocName());
                            final StrSubstitutor sub = new StrSubstitutor(subMap);
                            acc2caseInfoTmp.setRemark(sub.replace(remark));
                        } else {
                            final Map<String, String> subMap = new HashMap<>();
                            subMap.put(Accounting.RemarkSubstitutorKeys.RELDOC_NAME.name(), info.getDocName());
                            final StrSubstitutor sub = new StrSubstitutor(subMap);
                            final String remarkTmp = sub.replace(remark);
                            if (acc2caseInfo.getRemark().isEmpty()) {
                                acc2caseInfo.setRemark(remarkTmp);
                            } else {
                                acc2caseInfo.setRemark(StringUtils.join(new String[] { acc2caseInfo.getRemark(),
                                                remarkTmp }, ", "));
                            }
                            if (!added) {
                                added = true;
                                tempInfos.add(acc2caseInfo);
                            }
                        }
                    }
                } else {
                    tempInfos.add(acc2caseInfo);
                }
            } else {
                tempInfos.add(acc2caseInfo);
            }
        }
        _infos.clear();
        _infos.addAll(tempInfos);
    }
}
