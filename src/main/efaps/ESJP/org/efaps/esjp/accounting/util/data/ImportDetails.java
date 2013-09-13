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


package org.efaps.esjp.accounting.util.data;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;

import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Return;
import org.efaps.ci.CIType;
import org.efaps.db.InstanceQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.esjp.ci.CIERP;
import org.efaps.esjp.ci.CISales;
import org.efaps.util.EFapsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import au.com.bytecode.opencsv.CSVReader;


/**
 * TODO comment!
 *
 * @author The eFaps Team
 * @version $Id$
 */
public class ImportDetails
{
    private static Logger LOG = LoggerFactory.getLogger(ImportDetails.class);

    public Return importDetailsInvoice(final Parameter _parameter)
    {
        final String filename = _parameter.getParameterValue("valueField");
        final File file = new File(filename);
        try {
            checkDocs(file, CISales.Invoice);


//            final List<Account> accs = readAccounts4Concar(file);
//            for (final Account acc  :accs) {
//                final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.AccountAbstract);
//                queryBldr.addWhereAttrEqValue(CIAccounting.AccountAbstract.Name, acc.getName());
//                final InstanceQuery query = queryBldr.getQuery();
//                if (query.executeWithoutAccessCheck().isEmpty()) {
//                    ImportDetails.LOG.info("Not found: {}", acc);
//                }
//            }
        } catch (final IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (final EFapsException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return new Return();
    }

    /**
     *
     */
    protected List<Document> checkDocs(final File _file,
                                       final CIType _type)
        throws IOException, EFapsException
    {
        final CSVReader reader = new CSVReader(new InputStreamReader(new FileInputStream(_file), "UTF-8"));
        final List<String[]> entries = reader.readAll();
        reader.close();
        final List<Document> ret = new ArrayList<Document>();
        entries.remove(0);
        String docNumber = "";
        int i = 1;
        for (final String[] row : entries) {
            i++;
            final String docNumberTmp = row[0];
            if (!docNumber.equals(docNumberTmp)) {
                docNumber = docNumberTmp;
                ImportDetails.LOG.info("Trying to read Document '{}' - Line: {}", docNumber, i);
                final String[] docSplit = docNumber.split("-");
                if (docSplit.length != 2) {
                    ImportDetails.LOG.warn("Document '{}'  - Line: {} has no '-' to distinguish SerialNumber and No.",
                                    docNumber, i);
                } else {
                    final String serialNo = docSplit[0];
                    final String docNo = docSplit[1];
                    try {
                        final int serial = Integer.parseInt(serialNo.trim().replaceAll("\\D", ""));
                        final int no = Integer.parseInt(docNo.trim().replaceAll("\\D", ""));
                        final Formatter criteria = new Formatter();
                        criteria.format("%03d-%06d", serial, no);
                        ImportDetails.LOG.info("Applying Criteria: '{}'", criteria);
                        final QueryBuilder queryBldr = new QueryBuilder(_type);
                        queryBldr.addWhereAttrEqValue(CIERP.DocumentAbstract.Name, criteria.toString());
                        final InstanceQuery query = queryBldr.getQuery();
                        query.executeWithoutAccessCheck();
                        if (query.next()) {
                            ImportDetails.LOG.info("Found Document: '{}'", query.getCurrentValue());
                            if (query.next()) {
                                ImportDetails.LOG.error("Found duplicated Document: '{}'", query.getCurrentValue());
                            }
                        } else {
                            ImportDetails.LOG.error("No Document found for : '{}' - Line: {}", docNumber, i);
                        }
                        criteria.close();
                    } catch (final NumberFormatException e) {
                        ImportDetails.LOG.error("wrong format '{}'", docNumber);
                    }

                }
            }
        }

        return ret;
    }


    public static class Document
    {

    }
}
