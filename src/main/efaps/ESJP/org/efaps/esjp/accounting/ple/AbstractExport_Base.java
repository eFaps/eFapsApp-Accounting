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

package org.efaps.esjp.accounting.ple;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.Charset;

import org.apache.commons.io.output.FileWriterWithEncoding;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Return;
import org.efaps.admin.event.Return.ReturnValues;
import org.efaps.admin.program.esjp.EFapsRevision;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.esjp.common.file.FileUtil;
import org.efaps.util.EFapsException;

import com.brsanthu.dataexporter.DataExporter;
import com.brsanthu.dataexporter.LineSeparatorType;
import com.brsanthu.dataexporter.model.RowDetails;
import com.brsanthu.dataexporter.output.csv.CsvExportOptions;
import com.brsanthu.dataexporter.output.csv.CsvWriter;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 * @version $Id$
 */
@EFapsUUID("638174fb-6560-448a-913b-942337e8531e")
@EFapsRevision("$Rev$")
public abstract class AbstractExport_Base
{

    /**
     * Charset for the reports.
     * Windows-1252/WinLatin 1
     */
    public static final String CHARSET = "Cp1252";

    public Return execute(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final CsvExportOptions exportOption = new CsvExportOptions();
        configureReport(_parameter, exportOption);
        try {
            final File file = new FileUtil().getFile(getFileName(_parameter));
            final FileWriterWithEncoding writer = new FileWriterWithEncoding(file, getCharSet(_parameter));
            final Exporter exporter = getExporter(_parameter, exportOption, writer);
            addColumnDefinition(_parameter, exporter);
            buildDataSource(_parameter, exporter);
            ret.put(ReturnValues.VALUES, file);
            ret.put(ReturnValues.TRUE, true);
        } catch (final IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return ret;
    }

    /**
     * @param _parameter
     * @return
     */
    protected Charset getCharSet(final Parameter _parameter)
    {
        return Charset.forName(AbstractExport_Base.CHARSET);
    }


    /**
     * @param _parameter
     * @param _exporter
     */
    public abstract void addColumnDefinition(final Parameter _parameter,
                                             final Exporter _exporter)
        throws EFapsException;

    public abstract void buildDataSource(final Parameter _parameter,
                                         final Exporter _exporter)
        throws EFapsException;

    public abstract String getFileName(final Parameter _parameter)
        throws EFapsException;

    protected void configureReport(final Parameter _parameter,
                                   final CsvExportOptions _exportOption)
    {
        _exportOption.setLineSeparator(LineSeparatorType.WINDOWS);
        _exportOption.setQuote("");
        _exportOption.setDelimiter("|");
        _exportOption.setPrintHeaders(false);
    }

    /**
     * @param _parameter    Parameter as passed by the eFaps API
     * @param _options      options for the exporter
     * @param _output       writer
     * @return new Exporter
     */
    protected Exporter getExporter(final Parameter _parameter,
                                   final CsvExportOptions _options,
                                   final Writer _output)
    {
        return new Exporter(_parameter, _options, _output);
    }

    /**
     * Exporter class.
     */
    public static class Exporter
        extends DataExporter
    {

        /**
         * @param _parameter    Parameter as passed by the eFaps API
         * @param _options      options for the exporter
         * @param _output       writer
         */
        protected Exporter(final Parameter _parameter,
                           final CsvExportOptions _options,
                           final Writer _output)
        {
            super(new CsvWriter(_options, _output)
            {

                @Override
                public void beforeRow(final RowDetails _rowDetails)
                {
                    if (_rowDetails.getRowIndex() > 0) {
                        println();
                    }
                }

                @Override
                public void afterRow(final RowDetails _rowDetails)
                {
                    print(_options.getDelimiter());
                }
            });
        }
    }
}
