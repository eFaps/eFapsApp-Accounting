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
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Return;
import org.efaps.ci.CIType;
import org.efaps.db.InstanceQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.esjp.ci.CIAccounting;
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
            checkAccounts(file, CISales.Invoice);

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

    protected List<Document> checkAccounts(final File _file,
                                           final CIType _type)
        throws IOException, EFapsException
    {
        final List<Document> ret = new ArrayList<Document>();
        final CSVReader reader = new CSVReader(new InputStreamReader(new FileInputStream(_file), "UTF-8"));
        final List<String[]> entries = reader.readAll();
        reader.close();
        entries.remove(0);
        int i = 1;
        final Map<String, Document> map = new HashMap<String, Document>();
        for (final String[] row : entries) {
            i++;
            final String docNumber = row[0];
            final String account = row[5];
            final DecimalFormat formater = (DecimalFormat) NumberFormat.getInstance(Locale.GERMAN);
            formater.setParseBigDecimal(true);
            final String amountMEStr = row[6];
            final String amountMNStr = row[7];

            final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.AccountAbstract);
            queryBldr.addWhereAttrEqValue(CIAccounting.AccountAbstract.Name, account.trim());
            final InstanceQuery query = queryBldr.getQuery();
            query.executeWithoutAccessCheck();
            if (query.next()) {
                ImportDetails.LOG.info("Found account: '{}' ", account);
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

                        final String name = criteria.toString();

                        Document doc;
                        if (map.containsKey(name)) {
                            doc = map.get(name);
                        } else {
                            doc = new Document(name, account);
                        }
                        final BigDecimal amountME = (BigDecimal) formater.parse(amountMEStr);
                        final BigDecimal amountMN = (BigDecimal) formater.parse(amountMNStr);

                        if (amountME.compareTo(BigDecimal.ZERO) >= 0) {
                            doc.addAmountMECredit(amountME);
                            doc.addAmountMNCredit(amountMN);
                        } else {
                            doc.addAmountMEDebit(amountME);
                            doc.addAmountMNDebit(amountMN);
                        }
                        map.put(name, doc);
                        criteria.close();
                    } catch (final NumberFormatException e) {
                        ImportDetails.LOG.error("wrong format for document '{}'", docNumber);
                    } catch (final ParseException e) {
                        ImportDetails.LOG.error("wrong format for amounts '{}' - '{}'", amountMEStr, amountMNStr);
                    }
                }
            } else {
                ImportDetails.LOG.error("Not found account: {}", account);
            }
        }

        for (final Document doc : map.values()) {
            final BigDecimal amountCreditMN = doc.getAmountMNCredit() != null ? doc.getAmountMNCredit() : BigDecimal.ZERO;
            final BigDecimal amountDebitMN = doc.getAmountMNDebit() != null ? doc.getAmountMNDebit() : BigDecimal.ZERO;
            final BigDecimal amountMN = amountCreditMN.add(amountDebitMN);
            final BigDecimal amountCreditME = doc.getAmountMECredit() != null ? doc.getAmountMECredit() : BigDecimal.ZERO;
            final BigDecimal amountDebitME = doc.getAmountMEDebit() != null ? doc.getAmountMEDebit() : BigDecimal.ZERO;
            final BigDecimal amountME = amountCreditME.add(amountDebitME);
            if (BigDecimal.ZERO.compareTo(amountMN) == 0 && BigDecimal.ZERO.compareTo(amountME) == 0) {
                ImportDetails.LOG.info("For Document: '{}'. Sum of Credit with Debit Amount (ME): '{}' + '{}' and Credit with Debit Amount (MN): '{}' + '{}' are Zero (0)", doc.getName(), amountCreditME, amountDebitME, amountCreditMN, amountDebitMN);
            } else {
                ImportDetails.LOG.error("For Document: '{}'. Sum of Credit with Debit Amount (ME): '{}' + '{}' = '{}' and Credit with Debit Amount (MN): '{}' + '{}' = '{}'", doc.getName(), amountCreditME, amountDebitME, amountME, amountCreditMN, amountDebitMN, amountMN);
            }
        }
        return ret;
    }


    public static class Document
    {
        private String name;
        private BigDecimal amountMECredit;
        private BigDecimal amountMEDebit;
        private BigDecimal amountMNCredit;
        private BigDecimal amountMNDebit;
        private String account;

        public Document(final String _name,
                        final String _account) {
            this.name = _name;
            this.account = _account;
            this.amountMECredit = BigDecimal.ZERO;
            this.amountMEDebit = BigDecimal.ZERO;
            this.amountMNCredit = BigDecimal.ZERO;
            this.amountMNDebit = BigDecimal.ZERO;
        }

        /**
         * @return the name
         */
        private String getName()
        {
            return name;
        }
        /**
         * @param name the name to set
         */
        private void setName(final String name)
        {
            this.name = name;
        }
        /**
         * @return the account
         */
        private String getAccount()
        {
            return account;
        }
        /**
         * @param account the account to set
         */
        private void setAccount(final String account)
        {
            this.account = account;
        }

        /**
         * @return the amountMECredit
         */
        private BigDecimal getAmountMECredit()
        {
            return amountMECredit;
        }

        /**
         * @param amountMECredit the amountMECredit to set
         */
        private void addAmountMECredit(final BigDecimal amountMECredit)
        {
            this.amountMECredit = this.amountMECredit.add(amountMECredit);
        }

        /**
         * @return the amountMEDebit
         */
        private BigDecimal getAmountMEDebit()
        {
            return amountMEDebit;
        }

        /**
         * @param amountMEDebit the amountMEDebit to set
         */
        private void addAmountMEDebit(final BigDecimal amountMEDebit)
        {
            this.amountMEDebit = this.amountMEDebit.add(amountMEDebit);
        }

        /**
         * @return the amountMNCredit
         */
        private BigDecimal getAmountMNCredit()
        {
            return amountMNCredit;
        }

        /**
         * @param amountMNCredit the amountMNCredit to set
         */
        private void addAmountMNCredit(final BigDecimal amountMNCredit)
        {
            this.amountMNCredit = this.amountMNCredit.add(amountMNCredit);
        }

        /**
         * @return the amountMNDebit
         */
        private BigDecimal getAmountMNDebit()
        {
            return amountMNDebit;
        }

        /**
         * @param amountMNDebit the amountMNDebit to set
         */
        private void addAmountMNDebit(final BigDecimal amountMNDebit)
        {
            this.amountMNDebit = this.amountMNDebit.add(amountMNDebit);
        }


    }
}
