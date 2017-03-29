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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.StrSubstitutor;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.CachedPrintQuery;
import org.efaps.db.Instance;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.PrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.esjp.accounting.Account2CaseInfo;
import org.efaps.esjp.accounting.util.Accounting;
import org.efaps.esjp.ci.CIERP;
import org.efaps.esjp.ci.CISales;
import org.efaps.esjp.db.InstanceUtils;
import org.efaps.esjp.erp.CurrencyInst;
import org.efaps.util.EFapsException;

/**
 * The Class PaymentDocEvaluation_Base.
 *
 * @author The eFaps Team
 */
@EFapsUUID("7193df24-107a-4498-9821-94b9aec54bbf")
@EFapsApplication("eFapsApp-Accounting")
public abstract class PaymentDocEvaluation_Base
    extends AbstractEvaluation
{

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
                final QueryBuilder queryBldr = new QueryBuilder(CIERP.Document2PaymentDocumentAbstract);
                queryBldr.addWhereAttrEqValue(CIERP.Document2PaymentDocumentAbstract.ToAbstractLink, _doc
                                .getInstance());
                final MultiPrintQuery multi = queryBldr.getPrint();
                final SelectBuilder selDoc = new SelectBuilder().linkto(
                                CIERP.Document2PaymentDocumentAbstract.FromAbstractLink);
                final SelectBuilder selDocInst = new SelectBuilder(selDoc).instance();
                multi.addSelect(selDocInst);
                multi.addAttribute(CIERP.Document2PaymentDocumentAbstract.Amount,
                                CIERP.Document2PaymentDocumentAbstract.RateCurrencyLink);
                if (multi.execute()) {
                    final String remark = acc2caseInfo.getRemark();
                    acc2caseInfo.setRemark("");
                    while (multi.next()) {
                        final Instance docInst = multi.<Instance>getSelect(selDocInst);
                        final String docName;
                        if (InstanceUtils.isKindOf(docInst, CIERP.DocumentAbstract)) {
                            final PrintQuery print = CachedPrintQuery.get4Request(docInst);
                            print.addAttribute(CISales.DocumentSumAbstract.Name);
                            print.execute();
                            docName = print.getAttribute(CISales.DocumentSumAbstract.Name);
                        } else {
                            docName = "";
                        }
                        if (acc2caseInfo.isSeparately()) {
                            final Account2CaseInfo acc2caseInfoTmp = Account2CaseInfo.getAccount2CaseInfo(acc2caseInfo
                                            .getInstance());
                            tempInfos.add(acc2caseInfoTmp);
                            final CurrencyInst currencyInstObj = CurrencyInst.get(multi.<Long>getAttribute(
                                            CIERP.Document2PaymentDocumentAbstract.RateCurrencyLink));
                            acc2caseInfoTmp.setAmount(multi.getAttribute(CIERP.Document2PaymentDocumentAbstract.Amount))
                                            .setCurrencyInstance(currencyInstObj.getInstance()).setDeactCurrencyCheck(
                                                            true);
                            final Map<String, String> subMap = new HashMap<>();
                            subMap.put(Accounting.RemarkSubstitutorKeys.RELDOC_NAME.name(), docName);
                            subMap.put(Accounting.RemarkSubstitutorKeys.RELDOC_TYPE.name(), docInst.getType()
                                            .getLabel());
                            final StrSubstitutor sub = new StrSubstitutor(subMap);
                            acc2caseInfoTmp.setRemark(sub.replace(remark));
                        } else {
                            final Map<String, String> subMap = new HashMap<>();
                            subMap.put(Accounting.RemarkSubstitutorKeys.RELDOC_NAME.name(), docName);
                            subMap.put(Accounting.RemarkSubstitutorKeys.RELDOC_TYPE.name(), docInst.getType()
                                            .getLabel());
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
