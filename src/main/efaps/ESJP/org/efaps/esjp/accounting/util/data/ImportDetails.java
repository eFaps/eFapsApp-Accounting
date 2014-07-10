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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.efaps.admin.datamodel.Status;
import org.efaps.admin.datamodel.Type;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Return;
import org.efaps.db.Insert;
import org.efaps.db.Instance;
import org.efaps.db.InstanceQuery;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.esjp.accounting.transaction.Create;
import org.efaps.esjp.accounting.util.Accounting;
import org.efaps.esjp.accounting.util.AccountingSettings;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.ci.CIERP;
import org.efaps.esjp.sales.util.Sales;
import org.efaps.esjp.sales.util.SalesSettings;
import org.efaps.util.EFapsException;
import org.joda.time.DateTime;
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

    public Return importDetailsInvoice(final Parameter _parameter) throws ParseException
    {
        final String[] params = _parameter.getParameterValues("valueField");
        final Map<String, String> paramMap = new HashMap<String, String>();
        int cont = 0;
        for (final String param : params) {
            final String key = _parameter.getParameterValues("keyField")[cont];
            final String valueStr = param;
            paramMap.put(key, valueStr);
            cont++;
        }
        final String filename = paramMap.get("location");
        final String dateStr = paramMap.get("date");
        final String typeStr = paramMap.get("type");
        final Boolean inverse = "true".equalsIgnoreCase(paramMap.get("inverse"));
        final SimpleDateFormat format = new SimpleDateFormat("yyyy/MM/dd");
        final DateTime date = dateStr != null ? new DateTime(format.parse(dateStr)) : new DateTime();
        final File file = new File(filename);
        try {
            if (typeStr != null) {
                final Map<String, Instance> map = checkDocs(file, Type.get(typeStr));
                checkAccounts(_parameter, file, map, date, inverse);
            } else {
                checkAccounts(_parameter, file, null, date, inverse);
            }

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
    protected Map<String, Instance> checkDocs(final File _file,
                                              final Type _type)
        throws IOException, EFapsException
    {
        final CSVReader reader = new CSVReader(new InputStreamReader(new FileInputStream(_file), "UTF-8"));
        final List<String[]> entries = reader.readAll();
        reader.close();
        final Map<String, Instance> ret = new HashMap<String, Instance>();
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
                            ret.put(criteria.toString(), query.getCurrentValue());
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

    protected List<Document> checkAccounts(final Parameter _parameter,
                                            final File _file,
                                           final Map<String, Instance> _docMap,
                                           final DateTime _date,
                                           final Boolean _inverse)
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
            final String ruc = row[1];
            final String dateStr = row[2];
            final String accountStr = row[5];
            final String accountDesc = row[4];
            final DecimalFormat formater = (DecimalFormat) NumberFormat.getInstance(Locale.GERMAN);
            formater.setParseBigDecimal(true);
            final String amountMEStr = row[6];
            final String amountMNStr = row[7];

            final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.AccountAbstract);
            queryBldr.addWhereAttrEqValue(CIAccounting.AccountAbstract.Name, accountStr.trim());
            final InstanceQuery query = queryBldr.getQuery();
            query.executeWithoutAccessCheck();
            if (query.next()) {
                ImportDetails.LOG.info("Found account: '{}' ", accountStr);
                final String[] docSplit = docNumber.split("-");
                if (docSplit.length != 2 && _docMap != null) {
                    ImportDetails.LOG.warn("Document '{}'  - Line: {} has no '-' to distinguish SerialNumber and No.",
                                    docNumber, i);
                } else {
                    try {
                        final Formatter criteria = new Formatter();
                        String name = docNumber;
                        if (_docMap != null) {
                            final String serialNo = docSplit[0];
                            final String docNo = docSplit[1];
                            final int serial = Integer.parseInt(serialNo.trim().replaceAll("\\D", ""));
                            final int no = Integer.parseInt(docNo.trim().replaceAll("\\D", ""));
                            criteria.format("%03d-%06d", serial, no);
                            name = criteria.toString();
                        }

                        Document doc;
                        if (map.containsKey(name)) {
                            doc = map.get(name);
                        } else {
                            if (_docMap != null && _docMap.containsKey(name)) {
                                doc = new Document(name, _docMap.get(name), ruc, dateStr, accountDesc);
                            } else {
                                doc = new Document(name, null, ruc, dateStr, accountDesc);
                            }
                        }

                        BigDecimal amountME = (BigDecimal) formater.parse(amountMEStr);
                        BigDecimal amountMN = (BigDecimal) formater.parse(amountMNStr);

                        if (_inverse) {
                            amountME = amountME.negate();
                            amountMN = amountMN.negate();
                        }

                        if (amountMN.compareTo(BigDecimal.ZERO) >= 0) {
                            doc.addAmountMECredit(amountME);
                            doc.addAmountMNCredit(amountMN);
                        } else {
                            doc.addAmountMEDebit(amountME);
                            doc.addAmountMNDebit(amountMN);
                        }

                        final Map<String, Account> accounts = doc.getAccounts();
                        Account acc;
                        if (accounts.containsKey(accountStr)) {
                            acc = accounts.get(accountStr);
                        } else {
                            acc = new Account(accountStr, accountDesc);
                            accounts.put(accountStr, acc);
                        }
                        acc.addAmountME(amountME);
                        acc.addAmountMN(amountMN);
                        acc.setInstance(query.getCurrentValue());

                        map.put(name, doc);

                        criteria.close();
                    } catch (final NumberFormatException e) {
                        ImportDetails.LOG.error("wrong format for document '{}'", docNumber);
                    } catch (final ParseException e) {
                        ImportDetails.LOG.error("wrong format for amounts '{}' - '{}'", amountMEStr, amountMNStr);
                    }
                }
            } else {
                ImportDetails.LOG.error("Not found account: {}", accountStr);
            }
        }

        final Instance periodInst = getPeriodInstance();
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

            final Insert insert = new Insert(CIAccounting.TransactionOpeningBalance);
            insert.add(CIAccounting.TransactionOpeningBalance.Date, _date);
            final StringBuilder descBldr = new StringBuilder()
                .append(doc.getInstance() != null ? doc.getInstance().getType().getLabel() : "Sin Documento").append(": ")
                .append(doc.getName()).append(" - RUC: ")
                .append(doc.getRuc()).append(" - ")
                .append(doc.getDate()).append(" - ")
                .append(doc.getDesc());

            insert.add(CIAccounting.TransactionOpeningBalance.Description, descBldr.toString());
            insert.add(CIAccounting.TransactionOpeningBalance.Status,
                            Status.find(CIAccounting.TransactionStatus.Open));
            insert.add(CIAccounting.TransactionOpeningBalance.PeriodLink, periodInst);
            insert.executeWithoutAccessCheck();

            if (_docMap != null) {
                final Instance instance = insert.getInstance();
                new Create().connectDoc2Transaction(_parameter, instance, doc.getInstance());
            }

            final Map<String, Account> accounts = doc.getAccounts();
            final Instance basCur = Sales.getSysConfig().getLink(SalesSettings.CURRENCYBASE);
            for (final Account acc : accounts.values()) {
                final Insert insertpos = new Insert(
                                acc.getAmountMN().compareTo(BigDecimal.ZERO) > 0
                                ? CIAccounting.TransactionPositionCredit : CIAccounting.TransactionPositionDebit);
                insertpos.add(CIAccounting.TransactionPositionAbstract.AccountLink, acc.getInstance());
                insertpos.add(CIAccounting.TransactionPositionAbstract.Amount, acc.getAmountMN());
                insertpos.add(CIAccounting.TransactionPositionAbstract.CurrencyLink, basCur);
                insertpos.add(CIAccounting.TransactionPositionAbstract.Rate, acc.getRateObject());
                insertpos.add(CIAccounting.TransactionPositionAbstract.RateAmount, acc.getAmountME());
                insertpos.add(CIAccounting.TransactionPositionAbstract.RateCurrencyLink, 1);
                insertpos.add(CIAccounting.TransactionPositionAbstract.TransactionLink,  insert.getInstance());
                insertpos.executeWithoutAccessCheck();
            }

            if (amountCreditMN.compareTo(amountDebitMN.abs()) != 0
                        && amountCreditMN.subtract(amountDebitMN.abs()).abs().compareTo(new BigDecimal("0.05")) <= 0) {
                Insert insertpos = null;
                Account acc = null;
                if (amountCreditMN.compareTo(amountDebitMN.abs()) > 0) {
                    acc = getRoundingAccount(AccountingSettings.PERIOD_ROUNDINGDEBIT);
                    acc.addAmountMN(amountCreditMN.subtract(amountDebitMN.abs()).negate());
                    acc.addAmountME(amountCreditME.subtract(amountDebitME.abs()).negate());
                    insertpos = new Insert(CIAccounting.TransactionPositionDebit);
                } else {
                    acc = getRoundingAccount(AccountingSettings.PERIOD_ROUNDINGCREDIT);
                    acc.addAmountMN(amountDebitMN.abs().subtract(amountCreditMN));
                    acc.addAmountME(amountDebitME.abs().subtract(amountCreditME));
                    insertpos = new Insert(CIAccounting.TransactionPositionCredit);
                }
                insertpos.add(CIAccounting.TransactionPositionAbstract.AccountLink, acc.getInstance());
                insertpos.add(CIAccounting.TransactionPositionAbstract.Amount, acc.getAmountMN());
                insertpos.add(CIAccounting.TransactionPositionAbstract.CurrencyLink, basCur);
                insertpos.add(CIAccounting.TransactionPositionAbstract.Rate, acc.getRateObject());
                insertpos.add(CIAccounting.TransactionPositionAbstract.RateAmount, acc.getAmountME());
                insertpos.add(CIAccounting.TransactionPositionAbstract.RateCurrencyLink, 1);
                insertpos.add(CIAccounting.TransactionPositionAbstract.TransactionLink,  insert.getInstance());
                insertpos.executeWithoutAccessCheck();
            } else if (amountCreditMN.compareTo(amountDebitMN.abs()) != 0
                        && amountCreditMN.subtract(amountDebitMN.abs()).abs().compareTo(new BigDecimal("0.05")) > 0) {
                Insert insertpos = null;
                final Account acc = getRoundingAccount(AccountingSettings.PERIOD_TRANSFERACCOUNT);;
                if (amountCreditMN.compareTo(amountDebitMN.abs()) > 0) {
                    acc.addAmountMN(amountCreditMN.subtract(amountDebitMN.abs()).negate());
                    acc.addAmountME(amountCreditME.subtract(amountDebitME.abs()).negate());
                    insertpos = new Insert(CIAccounting.TransactionPositionDebit);
                } else {
                    acc.addAmountMN(amountDebitMN.abs().subtract(amountCreditMN));
                    acc.addAmountME(amountDebitME.abs().subtract(amountCreditME));
                    insertpos = new Insert(CIAccounting.TransactionPositionCredit);
                }
                insertpos.add(CIAccounting.TransactionPositionAbstract.AccountLink, acc.getInstance());
                insertpos.add(CIAccounting.TransactionPositionAbstract.Amount, acc.getAmountMN());
                insertpos.add(CIAccounting.TransactionPositionAbstract.CurrencyLink, basCur);
                insertpos.add(CIAccounting.TransactionPositionAbstract.Rate, acc.getRateObject());
                insertpos.add(CIAccounting.TransactionPositionAbstract.RateAmount, acc.getAmountME());
                insertpos.add(CIAccounting.TransactionPositionAbstract.RateCurrencyLink, 1);
                insertpos.add(CIAccounting.TransactionPositionAbstract.TransactionLink,  insert.getInstance());
                insertpos.executeWithoutAccessCheck();
            }
        }

        return ret;
    }

    protected Account getRoundingAccount(final String _key)
        throws EFapsException
    {
        final Instance periodInst = getPeriodInstance();
        Account ret = null;
        final Properties props = Accounting.getSysConfig().getObjectAttributeValueAsProperties(periodInst);
        final String name = props.getProperty(_key);
        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.AccountAbstract);
        queryBldr.addWhereAttrEqValue(CIAccounting.AccountAbstract.Name, name);
        queryBldr.addWhereAttrEqValue(CIAccounting.AccountAbstract.PeriodAbstractLink, periodInst);
        final MultiPrintQuery multi = queryBldr.getPrint();
        multi.addAttribute(CIAccounting.AccountAbstract.Name, CIAccounting.AccountAbstract.Description);
        multi.executeWithoutAccessCheck();
        while (multi.next()) {
            ret = new Account(multi.<String>getAttribute(CIAccounting.AccountAbstract.Name),
                            multi.<String>getAttribute(CIAccounting.AccountAbstract.Description));
            ret.setInstance(multi.getCurrentInstance());
        }
        return ret;
    }

    protected Instance getPeriodInstance()
        throws EFapsException
    {
        Instance ret = null;
        final DateTime date = new DateTime();
        final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.Period);
        queryBldr.addWhereAttrGreaterValue(CIAccounting.Period.ToDate, date);
        queryBldr.addWhereAttrLessValue(CIAccounting.Period.FromDate, date);
        final InstanceQuery query = queryBldr.getQuery();
        query.executeWithoutAccessCheck();
        if (query.next()) {
            ret = query.getCurrentValue();
        }

        return ret;
    }

    public static class Document
    {
        private final String name;
        private final Instance instance;
        private final String ruc;
        private final String date;
        private final String desc;
        private BigDecimal amountMECredit;
        private BigDecimal amountMEDebit;
        private BigDecimal amountMNCredit;
        private BigDecimal amountMNDebit;
        private final Map<String, Account> accounts;

        public Document(final String _name,
                        final Instance _instance,
                        final String _ruc,
                        final String _date,
                        final String _desc) {
            this.name = _name;
            this.instance = _instance;
            this.ruc = _ruc;
            this.date = _date;
            this.desc = _desc;
            this.amountMECredit = BigDecimal.ZERO;
            this.amountMEDebit = BigDecimal.ZERO;
            this.amountMNCredit = BigDecimal.ZERO;
            this.amountMNDebit = BigDecimal.ZERO;
            this.accounts = new HashMap<String, Account>();
        }

        /**
         * @return the name
         */
        private String getName()
        {
            return this.name;
        }

        /**
         * @return the instance
         */
        private Instance getInstance()
        {
            return this.instance;
        }


        /**
         * @return the ruc
         */
        private String getRuc()
        {
            return this.ruc;
        }

        /**
         * @return the date
         */
        private String getDate()
        {
            return this.date;
        }

        /**
         * @return the desc
         */
        private String getDesc()
        {
            return this.desc;
        }

        /**
         * @return the amountMECredit
         */
        private BigDecimal getAmountMECredit()
        {
            return this.amountMECredit;
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
            return this.amountMEDebit;
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
            return this.amountMNCredit;
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
            return this.amountMNDebit;
        }

        /**
         * @param amountMNDebit the amountMNDebit to set
         */
        private void addAmountMNDebit(final BigDecimal amountMNDebit)
        {
            this.amountMNDebit = this.amountMNDebit.add(amountMNDebit);
        }

        /**
         * @return the accounts
         */
        private Map<String, Account> getAccounts()
        {
            return this.accounts;
        }
    }

    public static class Account
    {
        private String name;
        private String description;
        private BigDecimal amountMN;
        private BigDecimal amountME;
        private Instance instance;

        public Account(final String _name,
                       final String _desc) {
            this.name = _name;
            this.description = _desc;
            this.amountMN = BigDecimal.ZERO;
            this.amountME = BigDecimal.ZERO;
        }

        /**
         * Getter method for the instance variable {@link #name}.
         *
         * @return value of instance variable {@link #name}
         */
        public String getName()
        {
            return this.name;
        }

        /**
         * @return
         */
        public BigDecimal getRate()
        {
            return this.amountME.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ONE : this.amountMN.setScale(8).divide(
                            this.amountME, BigDecimal.ROUND_HALF_UP);
        }

        public Object[] getRateObject()
        {
            return new Object[] {BigDecimal.ONE, getRate()};
        }

        /**
         * @param _currentValue
         */
        public void setInstance(final Instance _instance)
        {
            this.instance = _instance;
        }

        /**
         * Setter method for instance variable {@link #name}.
         *
         * @param _name value for instance variable {@link #name}
         */
        public void setName(final String _name)
        {
            this.name = _name;
        }

        /**
         * Getter method for the instance variable {@link #description}.
         *
         * @return value of instance variable {@link #description}
         */
        public String getDescription()
        {
            return this.description;
        }

        /**
         * Setter method for instance variable {@link #description}.
         *
         * @param _description value for instance variable {@link #description}
         */
        public void setDescription(final String _description)
        {
            this.description = _description;
        }

        /**
         * Getter method for the instance variable {@link #amountMN}.
         *
         * @return value of instance variable {@link #amountMN}
         */
        public BigDecimal getAmountMN()
        {
            return this.amountMN;
        }


        /**
         * Setter method for instance variable {@link #amountMN}.
         *
         * @param _amountMN value for instance variable {@link #amountMN}
         */
        public void addAmountMN(final BigDecimal _amountMN)
        {
            this.amountMN = this.amountMN.add(_amountMN);
        }


        /**
         * Getter method for the instance variable {@link #amountME}.
         *
         * @return value of instance variable {@link #amountME}
         */
        public BigDecimal getAmountME()
        {
            return this.amountME;
        }


        /**
         * Setter method for instance variable {@link #amountME}.
         *
         * @param _amountME value for instance variable {@link #amountME}
         */
        public void addAmountME(final BigDecimal _amountME)
        {
            this.amountME = this.amountME.add(_amountME);
        }

        /**
         * Getter method for the instance variable {@link #instance}.
         *
         * @return value of instance variable {@link #instance}
         */
        public Instance getInstance()
        {
            return this.instance;
        }

        @Override
        public String toString()
        {
            return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
        }

    }
}
