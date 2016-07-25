/*
 * Copyright 2003 - 2013 The eFaps Team
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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Return;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.AttributeQuery;
import org.efaps.db.Instance;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.PrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.db.Update;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.util.EFapsException;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * TODO comment!
 *
 * @author The eFaps Team
 * @version $Id$
 */
@EFapsUUID("ffd4cd11-e67b-4ba1-a16e-af2ae08b3460")
@EFapsApplication("eFapsApp-Accounting")
public abstract class SubJournal_Base
{
    /**
     * Logger for this class.
     */
    private static final Logger LOG = LoggerFactory.getLogger(SubJournal.class);

    public Return insertPostTrigger(final Parameter _parameter)
        throws EFapsException
    {
        final PrintQuery print = new PrintQuery(_parameter.getInstance());
        final SelectBuilder selRepInst = SelectBuilder.get().linkto(CIAccounting.ReportSubJournal2Transaction.FromLink)
                        .instance();
        final SelectBuilder selRepName = SelectBuilder.get().linkto(CIAccounting.ReportSubJournal2Transaction.FromLink)
                        .attribute(CIAccounting.ReportSubJournal.Name);
        final SelectBuilder selTransDate = SelectBuilder.get().linkto(CIAccounting.ReportSubJournal2Transaction.ToLink)
                        .attribute(CIAccounting.Transaction.Date);
        print.addSelect(selRepInst, selRepName, selTransDate);
        print.executeWithoutAccessCheck();
        final Instance repInst = print.<Instance>getSelect(selRepInst);
        final String repName = print.<String>getSelect(selRepName);
        final DateTime transDate = print.<DateTime>getSelect(selTransDate);

        final QueryBuilder attrQueryBldr = new QueryBuilder(CIAccounting.Transaction);
        attrQueryBldr.addWhereAttrGreaterValue(CIAccounting.Transaction.Date, transDate.withDayOfMonth(1)
                        .withTimeAtStartOfDay().minusSeconds(1));
        attrQueryBldr.addWhereAttrLessValue(CIAccounting.Transaction.Date, transDate.withDayOfMonth(1)
                        .withTimeAtStartOfDay().plusMonths(1));
        final AttributeQuery attrQuery = attrQueryBldr.getAttributeQuery(CIAccounting.Transaction.ID);

        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.ReportSubJournal2Transaction);
        queryBldr.addWhereAttrEqValue(CIAccounting.ReportSubJournal2Transaction.FromLink, repInst);
        queryBldr.addWhereAttrInQuery(CIAccounting.ReportSubJournal2Transaction.ToLink, attrQuery);
        queryBldr.addWhereAttrNotEqValue(CIAccounting.ReportSubJournal2Transaction.ID,
                        _parameter.getInstance());
        queryBldr.addOrderByAttributeDesc(CIAccounting.ReportSubJournal2Transaction.Number);
        final MultiPrintQuery multi = queryBldr.getPrint();
        multi.setEnforceSorted(true);
        multi.addAttribute(CIAccounting.ReportSubJournal2Transaction.Number);
        multi.executeWithoutAccessCheck();
        String numberStr = "";
        if (multi.next()) {
            numberStr = multi.<String>getAttribute(CIAccounting.ReportSubJournal2Transaction.Number);
        }
        final int month = transDate.getMonthOfYear();

        Integer curr = 0;
        try {
            final Pattern pattern = Pattern.compile("\\d*$");
            final Matcher matcher =  pattern.matcher(numberStr.trim());
            if (matcher.find()) {
                curr = Integer.parseInt( matcher.group());
            }
        } catch (final NumberFormatException e) {
            SubJournal_Base.LOG.warn("Catched NumberFormatException");
        }
        curr = curr + 1;

        numberStr = String.format("%s/%02d/%04d",repName, month, curr);

        final Update update = new Update(_parameter.getInstance());
        update.add(CIAccounting.ReportSubJournal2Transaction.Number, numberStr);
        update.executeWithoutTrigger();
        return new Return();
    }
}
