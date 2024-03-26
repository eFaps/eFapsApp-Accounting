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
package org.efaps.esjp.accounting.util.data;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.efaps.admin.datamodel.Status;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Return;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.Insert;
import org.efaps.db.Instance;
import org.efaps.db.InstanceQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.erp.Currency;
import org.efaps.util.EFapsException;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import au.com.bytecode.opencsv.CSVReader;


/**
 * Importation etc for Syscon and Concar.
 *
 * @author The eFaps Team
 * 
 */
@EFapsUUID("68b27549-d0ed-4d9a-a897-eda16c422a60")
@EFapsApplication("eFapsApp-Accounting")
public class ConSis
{
   private static Logger LOG = LoggerFactory.getLogger(ConSis.class);

    /**
     * Needs a txt file from Concar. Reads it, analyses it and checks if an account with the same name exists.
     * @param _parameter
     * @return
     */
    public Return compareAccounts(final Parameter _parameter)
    {
        final String filename = _parameter.getParameterValue("valueField");
        final File file = new File(filename);
        try {
            final List<Account> accs = readAccounts4Concar(file);
            for (final Account acc  :accs) {
                final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.AccountAbstract);
                queryBldr.addWhereAttrEqValue(CIAccounting.AccountAbstract.Name, acc.getName());
                final InstanceQuery query = queryBldr.getQuery();
                if (query.executeWithoutAccessCheck().isEmpty()) {
                    ConSis.LOG.info("Not found: {}", acc);
                }
            }
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
     * Needs a txt file from Concar. Reads it, analyses it and checks if an account with the same name exists.
     * @param _parameter
     * @return
     */
    public Return initialTransaction(final Parameter _parameter)
    {
        final String filename = _parameter.getParameterValue("valueField");
        final File file = new File(filename);
        try {
            final List<Account> accs = readAccounts4SaldoConcar(file);
            BigDecimal amountMN = BigDecimal.ZERO;
            BigDecimal amountME = BigDecimal.ZERO;
            boolean found = true;
            for (final Account acc  :accs) {
                final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.AccountAbstract);
                queryBldr.addWhereAttrEqValue(CIAccounting.AccountAbstract.Name, acc.getName());
                final InstanceQuery query = queryBldr.getQuery();
                query.executeWithoutAccessCheck();
                if (query.next()) {
                    acc.setInstance(query.getCurrentValue());
                } else {
                    ConSis.LOG.info("Not found: {}", acc);
                    found  = false;
                }
                amountMN = amountMN.add(acc.getAmountMN());
                amountME = amountME.add(acc.getAmountME());
            }
            ConSis.LOG.info("amountMN: {}", amountMN);
            ConSis.LOG.info("amountME: {}", amountME);
            if (found && amountMN.compareTo(BigDecimal.ZERO) == 0 && amountME.compareTo(BigDecimal.ZERO) == 0) {
                final Insert insert = new Insert(CIAccounting.TransactionOpeningBalance);
                insert.add(CIAccounting.TransactionOpeningBalance.Date, new DateTime());
                insert.add(CIAccounting.TransactionOpeningBalance.Description, "Asiento de Apertura");
                insert.add(CIAccounting.TransactionOpeningBalance.Status,
                                Status.find(CIAccounting.TransactionStatus.Open));
                insert.add(CIAccounting.TransactionOpeningBalance.PeriodLink, _parameter.getInstance());
                insert.executeWithoutAccessCheck();

                final Instance basCur = Currency.getBaseCurrency();
                for (final Account acc : accs) {
                    final Insert insertpos = new Insert(
                                    acc.getAmountME().compareTo(BigDecimal.ZERO) > 0
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
            }
        } catch (final IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (final EFapsException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (final ParseException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return new Return();
    }


    /**
     * Read "P L A N   G E N E R A L   D E   C U E N T A S" from a txt. file/
     */
    protected List<Account> readAccounts4Concar(final File _file)
        throws IOException
    {
        final BufferedReader in = new BufferedReader(new FileReader(_file));
        final List<Account> ret = new ArrayList<>();
        final Pattern accPattern = Pattern.compile("^(\\d)*");

        while (in.ready()) {
            final String s = in.readLine();
            final Matcher matcher = accPattern.matcher(s);
            matcher.find();
            final String name = matcher.group();
            if (name != null &&  !name.isEmpty()) {
                matcher.group();
                final String descr = s.substring(13, 50);
                final Account ac = new Account();
                ac.setName(name);
                ac.setDescription(descr);
                ret.add(ac);
            }
        }
        in.close();
        return ret;
    }


    protected List<Account> readAccounts4SaldoConcar(final File _file)
        throws IOException, ParseException
    {
        final List<Account> ret = new ArrayList<>();
        final BufferedReader in = new BufferedReader(new FileReader(_file));
        final CSVReader reader = new CSVReader(in);
        final List<String[]> rows = reader.readAll();
        reader.close();
        final Pattern accPattern = Pattern.compile("^[\\d ]*");
        final DecimalFormat formater = (DecimalFormat) NumberFormat.getInstance(Locale.GERMAN);
        formater.setParseBigDecimal(true);
//        final DecimalFormatSymbols frmSym = DecimalFormatSymbols.getInstance();
//        frmSym.setDecimalSeparator(",".toCharArray()[0]);
//        frmSym.setPatternSeparator(".".toCharArray()[0]);
//        formater.setDecimalFormatSymbols(frmSym);

        for (final String[] row : rows) {
            final String accStr = row[0];
            if (!accStr.isEmpty() && accPattern.matcher(accStr).matches()) {
                final Account acc = new Account();
                ret.add(acc);
                acc.setName(accStr.trim());
                final String amountMEDebit = row[10];
                final String amountMECredit = row[11];
                final String amountMNDebit = row[12];
                final String amountMNCredit = row[13];
                if (!amountMEDebit.isEmpty()) {
                    acc.setAmountME(((BigDecimal) formater.parse(amountMEDebit)).negate());
                }
                if (!amountMECredit.isEmpty()) {
                    acc.setAmountME((BigDecimal) formater.parse(amountMECredit));
                }
                if (!amountMNDebit.isEmpty()) {
                    acc.setAmountMN(((BigDecimal) formater.parse(amountMNDebit)).negate());
                }
                if (!amountMNCredit.isEmpty()) {
                    acc.setAmountMN((BigDecimal) formater.parse(amountMNCredit));
                }
                ConSis.LOG.info("Read Account {} - Rate: {}", acc, acc.getRate());
            }
        }
        return ret;
    }


    public static class Account
    {
        private String name;
        private String description;
        private BigDecimal amountMN = BigDecimal.ZERO;
        private BigDecimal amountME = BigDecimal.ZERO;
        private Instance instance;
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
        public void setAmountMN(final BigDecimal _amountMN)
        {
            this.amountMN = _amountMN;
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
        public void setAmountME(final BigDecimal _amountME)
        {
            this.amountME = _amountME;
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
