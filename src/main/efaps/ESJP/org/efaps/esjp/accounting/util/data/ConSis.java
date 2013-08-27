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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Return;
import org.efaps.admin.program.esjp.EFapsRevision;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.InstanceQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.util.EFapsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



/**
 * Importation etc for Syscon and Concar.
 *
 * @author The eFaps Team
 * @version $Id$
 */
@EFapsUUID("68b27549-d0ed-4d9a-a897-eda16c422a60")
@EFapsRevision("$Rev$")
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
     * Read "P L A N   G E N E R A L   D E   C U E N T A S" from a txt. file/
     *
     *
     */
    protected List<Account> readAccounts4Concar(final File _file)
        throws IOException
    {
        final BufferedReader in = new BufferedReader(new FileReader(_file));
        final List<Account> ret = new ArrayList<Account>();
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


    public static class Account
    {
        private String name;
        private String description;

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

        @Override
        public String toString()
        {
            return ToStringBuilder.reflectionToString(this);
        }

    }
}
