/*
 * Copyright 2003 - 2010 The eFaps Team
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

package org.efaps.esjp.accounting.report;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.engine.fill.JRFillField;
import net.sf.jasperreports.engine.util.JRProperties;

import org.apache.commons.lang.builder.ToStringBuilder;
import org.efaps.admin.datamodel.ui.FieldValue;
import org.efaps.admin.datamodel.ui.UIInterface;
import org.efaps.admin.dbproperty.DBProperties;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Parameter.ParameterValues;
import org.efaps.admin.event.Return;
import org.efaps.admin.event.Return.ReturnValues;
import org.efaps.admin.program.esjp.EFapsRevision;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.admin.program.jasper.JasperUtil;
import org.efaps.ci.CIAdminProgram;
import org.efaps.db.Insert;
import org.efaps.db.Instance;
import org.efaps.db.InstanceQuery;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.PrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.esjp.ci.CIAccounting;
import org.efaps.esjp.common.jasperreport.StandartReport;
import org.efaps.esjp.common.jasperreport.StandartReport_Base;
import org.efaps.util.EFapsException;

import ar.com.fdvs.dj.core.FieldMapWrapper;
import ar.com.fdvs.dj.core.layout.ClassicLayoutManager;
import ar.com.fdvs.dj.domain.CustomExpression;
import ar.com.fdvs.dj.domain.DynamicReport;
import ar.com.fdvs.dj.domain.Style;
import ar.com.fdvs.dj.domain.builders.ColumnBuilder;
import ar.com.fdvs.dj.domain.builders.ColumnBuilderException;
import ar.com.fdvs.dj.domain.builders.DynamicReportBuilder;
import ar.com.fdvs.dj.domain.constants.HorizontalAlign;
import ar.com.fdvs.dj.domain.constants.Page;
import ar.com.fdvs.dj.domain.entities.columns.AbstractColumn;
import ar.com.fdvs.dj.domain.entities.conditionalStyle.ConditionStyleExpression;
import ar.com.fdvs.dj.domain.entities.conditionalStyle.ConditionalStyle;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 * @version $Id$
 */
@EFapsUUID("0f3596f4-bcfa-4541-8d6b-551562a9b223")
@EFapsRevision("$Rev$")
public abstract class Report_Base
    extends StandartReport
{

    /**
     * Method renders a drop down field containing the mime types.
     *
     * @param _parameter Parameter as passed from the eFaps API
     * @return html snipplet
     */
    public Return getMimeTypeFieldValueUI(final Parameter _parameter)
    {
        final FieldValue fieldValue = (FieldValue) _parameter.get(ParameterValues.UIOBJECT);
        final Return ret = new Return();
        final StringBuilder html = new StringBuilder();
        html.append("<select name=\"").append(fieldValue.getField().getName()).append("\" ").append(
                        UIInterface.EFAPSTMPTAG).append(" size=\"1\">");

        final Map<String, String> values = new TreeMap<String, String>();
        values.put(DBProperties.getProperty("org.efaps.esjp.accounting.report.Report.pdf"), "pdf");
        values.put(DBProperties.getProperty("org.efaps.esjp.accounting.report.Report.xls"), "xls");

        for (final Entry<String, String> entry : values.entrySet()) {
            html.append("<option value=\"").append(entry.getValue());
            if ("pdf".equals(entry.getValue())) {
                html.append("\" selected=\"selected");
            }
            html.append("\">").append(entry.getKey()).append("</option>");
        }
        html.append("</select>");

        ret.put(ReturnValues.SNIPLETT, html.toString());
        return ret;
    }

    /**
     * Executed to create a report account node from the UserInterface.
     *
     * @param _parameter Parameter as passed from the eFasp API
     * @return new Return
     * @throws EFapsException on error
     */
    public Return createAccountNode(final Parameter _parameter)
        throws EFapsException
    {
        final Insert insert = new Insert(CIAccounting.ReportNodeAccount);
        insert.add("ParentLink", _parameter.getInstance().getId());
        insert.add("Number", _parameter.getParameterValue("number"));
        insert.add("Label", _parameter.getParameterValue("label"));
        insert.add("ShowAllways", _parameter.getParameterValue("showAllways"));
        insert.add("ShowSum", _parameter.getParameterValue("showSum"));
        insert.add("AccountLink", Instance.get(_parameter.getParameterValue("accountLink")).getId());
        insert.execute();
        return new Return();
    }

    /**
     * Execute the actual creation of the report.
     *
     * @param _parameter Parameter as passed from the eFasp API
     * @throws EFapsException on error
     * @return Return containg the created file
     */
    @Override
    public Return execute(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final String mime = _parameter.getParameterValue("mime");
        final ReportTree dataTree = new ReportTree(_parameter.getInstance());
        dataTree.addChildren();
        for (final Node node : dataTree.getRootNodes()) {
            node.getSum();
        }

        final List<List<Node>> table = dataTree.getTable();
        try {

            final DynamicReportBuilder drb = new DynamicReportBuilder();
            drb.setTitle(dataTree.getName())
                            .setSubtitle(dataTree.getDescription())
                            .setUseFullPageWidth(true)
                            .setPageSizeAndOrientation(Page.Page_A4_Landscape());

            final Style columnStyle = new Style();
            columnStyle.setHorizontalAlign(HorizontalAlign.RIGHT);
            int max = 0;
            Integer y = 0;
            for (final List<Node> nodes : table) {
                for (final Node node : nodes) {
                    if (node.getLevel() > max) {
                        max = node.getLevel();
                    }
                }
                final ArrayList<ConditionalStyle> conditionalStyles = new ArrayList<ConditionalStyle>();
                for (int i = 0; i < max; i++) {
                    final Style style = new Style();
                    style.setPaddingLeft(5 + 10 * i);
                    conditionalStyles.add(new ConditionalStyle(new PaddingCondition(i + 1, "column_" + y), style));
                }
                final AbstractColumn column = ColumnBuilder.getNew()
                                .setColumnProperty("column_" + y, String.class.getName())
                                .addFieldProperty("rootIndex", y.toString())
                                .addConditionalStyles(conditionalStyles)
                                .setWidth(10)
                                .build();
                drb.addColumn(column);
                final AbstractColumn column2 = ColumnBuilder.getNew()
                                .setColumnProperty("sums_" + y, BigDecimal.class.getName())
                                .addFieldProperty("rootIndex", y.toString())
                                .setStyle(columnStyle)
                                .setWidth(5)
                                .build();
                drb.addColumn(column2);
                y++;
            }

            final Map<?, ?> properties = (Map<?, ?>) _parameter.get(ParameterValues.PROPERTIES);
            final String name = (String) properties.get("JasperReport");
            JasperDesign jasperdesign = null;
            if (name != null) {
                final QueryBuilder queryBldr = new QueryBuilder(CIAdminProgram.JasperReport);
                queryBldr.addWhereAttrEqValue(CIAdminProgram.JasperReport.Name, name);
                final InstanceQuery query = queryBldr.getQuery();
                query.execute();
                Instance instance = null;
                if (query.next()) {
                    instance = query.getCurrentValue();
                } else {
                    throw new EFapsException(StandartReport_Base.class, "execute.ReportNotFound");
                }
                jasperdesign = JasperUtil.getJasperDesign(instance);
            }
            final DynamicReport dr = drb.build();
            final JRDataSource ds = new AccountingDataSource(table);
            JRProperties.setProperty(JRProperties.COMPILER_XML_VALIDATION, false);
            setFileName(dataTree.getName());
            final JasperPrint jp = JasperUtil.generateJasperPrint(dr, new ClassicLayoutManager(), ds, null,
                            jasperdesign);
            ret.put(ReturnValues.VALUES, super.getFile(jp, mime));
            ret.put(ReturnValues.TRUE, true);
        } catch (final JRException e) {
            throw new EFapsException(Report.class, "JRException", e);
        } catch (final IOException e) {
            throw new EFapsException(Report.class, "IOException", e);
        } catch (final ColumnBuilderException e) {
            throw new EFapsException(Report.class, "ColumnBuilderException", e);
        }
        return ret;
    }

    /**
     * Class to obtain a report.
     * @author jorge.
     *
     */
    class ReportTree
    {

        /**
         * List of nodes belonging to this report.
         */
        private final List<Node> rootNodes = new ArrayList<Node>();

        /**
         * Instance of this report.
         */
        private final Instance instance;

        /**
         * Name of this report.
         */
        private final String name;

        /**
         * Description for this report.
         */
        private final String description;

        /**
         * Numbering of this report.
         */
        private final String numbering;

        /**
         * @param _instance Instrance of this report
         * @throws EFapsException on error
         */
        public ReportTree(final Instance _instance)
            throws EFapsException
        {
            this.instance = _instance;

            final PrintQuery print = new PrintQuery(this.instance);
            print.addAttribute(CIAccounting.ReportAbstract.Name, CIAccounting.ReportAbstract.Numbering,
                            CIAccounting.ReportAbstract.Description);
            print.execute();
            this.name = print.<String> getAttribute(CIAccounting.ReportAbstract.Name);
            this.description = print.<String> getAttribute(CIAccounting.ReportAbstract.Description);
            this.numbering = print.<String> getAttribute(CIAccounting.ReportAbstract.Numbering);
        }

        /**
         * Getter method for instance variable {@link #rootNodes}.
         *
         * @return value of instance variable {@link #rootNodes}
         */
        public List<Node> getRootNodes()
        {
            return this.rootNodes;
        }

        /**
         * @throws EFapsException on error.
         */
        public void addChildren()
            throws EFapsException
        {
            final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.ReportNodeRoot);
            queryBldr.addWhereAttrEqValue(CIAccounting.ReportNodeRoot.ReportLink, this.instance.getId());
            final MultiPrintQuery print = queryBldr.getPrint();
            print.addAttribute(CIAccounting.ReportNodeRoot.OID, CIAccounting.ReportNodeRoot.Number,
                            CIAccounting.ReportNodeRoot.Label, CIAccounting.ReportNodeRoot.ShowAllways,
                            CIAccounting.ReportNodeRoot.ShowSum, CIAccounting.ReportNodeRoot.Position);
            print.execute();
            while (print.next()) {
                final String oidTmp = print.<String> getAttribute(CIAccounting.ReportNodeRoot.OID);
                final String number = print.<String> getAttribute(CIAccounting.ReportNodeRoot.Number);
                final String label = print.<String> getAttribute(CIAccounting.ReportNodeRoot.Label);
                final Boolean showAllways = print.<Boolean> getAttribute(CIAccounting.ReportNodeRoot.ShowAllways);
                final Boolean showSum = print.<Boolean> getAttribute(CIAccounting.ReportNodeRoot.ShowSum);
                final Long position = print.<Long> getAttribute(CIAccounting.ReportNodeRoot.Position);
                final Node child = new RootNode(null, oidTmp, number, label, showAllways, showSum, position);
                this.rootNodes.add(child);
            }
            for (final Node root : this.rootNodes) {
                root.addChildren(1);
            }
            Collections.sort(this.rootNodes, new Comparator<Node>() {

                public int compare(final Node _node1,
                                   final Node _node2)
                {
                    return _node1.getPosition().compareTo(_node2.getPosition());
                }
            });
        }

        /**
         * @return ret.
         */
        public List<List<Node>> getTable()
        {
            final List<List<Node>> ret = new ArrayList<List<Node>>();
            for (final Node node : this.rootNodes) {
                ret.add(flatten(node));
            }
            return ret;
        }

        /**
         * @param _parent Node.
         * @return ret
         */
        private List<Node> flatten(final Node _parent)
        {
            final List<Node> ret = new ArrayList<Node>();
            if (_parent.isShowAllways()
                            || (_parent.isShowSum() && _parent.getSum().compareTo(BigDecimal.ZERO) != 0)) {
                ret.add(_parent);
            }
            for (final Node child : _parent.getChildren()) {
                ret.addAll(flatten(child));
            }
            return ret;
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
         * Getter method for the instance variable {@link #description}.
         *
         * @return value of instance variable {@link #description}
         */
        public String getDescription()
        {
            return this.description;
        }

        /**
         * Getter method for the instance variable {@link #numbering}.
         *
         * @return value of instance variable {@link #numbering}
         */
        public String getNumbering()
        {
            return this.numbering;
        }
    }

    public abstract class Node
    {

        protected final List<Report.Node> children = new ArrayList<Report.Node>();

        private final String oid;
        private final String number;
        private final String label;
        private final boolean showAllways;
        private final boolean showSum;

        private final Node parent;

        private final Long position;

        private final int level;

        /**
         * @param _oid oid of the node
         * @param _number number of the node
         * @param _label label of the node
         * @param _showAllways must the node allways be shown
         * @param _showSum must a sum be shown
         * @param _position the position of the node
         */
        public Node(final Node _parent,
                    final String _oid,
                    final String _number,
                    final String _label,
                    final boolean _showAllways,
                    final boolean _showSum,
                    final Long _position,
                    final int _level)
        {
            this.parent = _parent;
            this.oid = _oid;
            this.number = _number;
            this.label = _label;
            this.showAllways = _showAllways;
            this.showSum = _showSum;
            this.position = _position;
            this.level = _level;
        }

        /**
         * Getter method for the instance variable {@link #level}.
         *
         * @return value of instance variable {@link #level}
         */
        public int getLevel()
        {
            return this.level;
        }

        /**
         * Getter method for instance variable {@link #position}.
         *
         * @return value of instance variable {@link #position}
         */
        public Long getPosition()
        {
            return this.position;
        }

        /**
         * Getter method for instance variable {@link #parent}.
         *
         * @return value of instance variable {@link #parent}
         */
        public Node getParent()
        {
            return this.parent;
        }

        /**
         * Getter method for instance variable {@link #children}.
         *
         * @return value of instance variable {@link #children}
         */
        public List<Report.Node> getChildren()
        {
            Collections.sort(this.children, new Comparator<Report.Node>() {

                public int compare(final Node _node1,
                                   final Node _node2)
                {
                    return _node1.getPosition().compareTo(_node2.getPosition());
                }
            });
            return this.children;
        }

        /**
         * Add the children to this node instance.
         *
         * @throws EFapsException on error
         */
        protected abstract void addChildren(final int _level)
            throws EFapsException;

        abstract BigDecimal getSum();

        /**
         * Getter method for instance variable {@link #oid}.
         *
         * @return value of instance variable {@link #oid}
         */
        public String getOid()
        {
            return this.oid;
        }

        /**
         * Getter method for instance variable {@link #number}.
         *
         * @return value of instance variable {@link #number}
         */
        public String getNumber()
        {
            return this.number;
        }

        /**
         * Getter method for instance variable {@link #label}.
         *
         * @return value of instance variable {@link #label}
         */
        public String getLabel()
        {
            return this.label;
        }

        /**
         * Getter method for instance variable {@link #showAllways}.
         *
         * @return value of instance variable {@link #showAllways}
         */
        public boolean isShowAllways()
        {
            return this.showAllways;
        }

        /**
         * Getter method for instance variable {@link #showSum}.
         *
         * @return value of instance variable {@link #showSum}
         */
        public boolean isShowSum()
        {
            return this.showSum;
        }

        /**
         * @see java.lang.Object#toString()
         * @return
         */
        @Override
        public String toString()
        {
            final ToStringBuilder ret = new ToStringBuilder(this);
            ret.append("number", this.number).append("label", this.label).append("sum", getSum()).append("showSum",
                            this.showSum).append("showAllways", this.showAllways);
            for (final Node node : getChildren()) {
                ret.append("\n     ");
                ret.append("child", node.toString());
            }
            return ret.toString();
        }

    }

    class RootNode
        extends Node
    {

        /**
         * @param oid
         * @param number
         * @param label
         * @param showAllways
         * @param showSum
         */
        public RootNode(final Node _parent,
                        final String oid,
                        final String number,
                        final String label,
                        final boolean showAllways,
                        final boolean showSum,
                        final Long position)
        {
            super(_parent, oid, number, label, showAllways, showSum, position, 0);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected void addChildren(final int _level)
            throws EFapsException
        {
            final Instance rootInst = Instance.get(getOid());

            final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.ReportNodeTree);
            queryBldr.addWhereAttrEqValue("ParentLink", rootInst.getId());
            final MultiPrintQuery print = queryBldr.getPrint();
            print.addAttribute("OID", "Number", "Label", "ShowAllways", "ShowSum", "Position");
            print.execute();
            while (print.next()) {
                final String oidTmp = print.<String> getAttribute("OID");
                final String number = print.<String> getAttribute("Number");
                final String label = print.<String> getAttribute("Label");
                final Boolean showAllways = print.<Boolean> getAttribute("ShowAllways");
                final Boolean showSum = print.<Boolean> getAttribute("ShowAllways");
                final Long position = print.<Long> getAttribute("Position");
                final Node child = new TreeNode(this, oidTmp, number, label, showAllways, showSum, position, _level);
                this.children.add(child);
            }
            for (final Node child : this.children) {
                child.addChildren(_level + 1);
            }
        }

        /**
         * @see org.efaps.esjp.accounting.report.Report_Base.Node#getSum()
         * @return
         */
        @Override
        public BigDecimal getSum()
        {
            BigDecimal temp = BigDecimal.ZERO;
            for (final Node child : getChildren()) {
                temp = temp.add(child.getSum());
            }
            return temp;
        }

    }

    class TreeNode
        extends Node
    {

        /**
         * @param oid
         * @param number
         * @param label
         * @param showAllways
         * @param showSum
         */
        public TreeNode(final Node _parent,
                        final String oid,
                        final String number,
                        final String label,
                        final boolean showAllways,
                        final boolean showSum,
                        final Long position,
                        final int _level)
        {
            super(_parent, oid, number, label, showAllways, showSum, position, _level);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected void addChildren(final int _level)
            throws EFapsException
        {
            final Instance treeInst = Instance.get(getOid());

            final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.ReportNodeChildAbstract);
            queryBldr.addWhereAttrEqValue("ParentLinkAbstract", treeInst.getId());
            final MultiPrintQuery print = queryBldr.getPrint();
            print.addAttribute("OID", "Number", "Label", "ShowAllways", "ShowSum", "Position", "AccountLink");
            print.execute();
            while (print.next()) {
                final String oidTmp = print.<String> getAttribute("OID");
                final String number = print.<String> getAttribute("Number");
                final String label = print.<String> getAttribute("Label");
                final Boolean showAllways = print.<Boolean> getAttribute("ShowAllways");
                final Boolean showSum = print.<Boolean> getAttribute("ShowAllways");
                final Long position = print.<Long> getAttribute("Position");
                final Long accountLink = print.<Long> getAttribute("AccountLink");
                final Node child;
                if (print.getCurrentInstance().getType().getUUID().equals(CIAccounting.ReportNodeAccount.uuid)) {
                    child = new AccountNode(this, oidTmp, number, label, showAllways, showSum, position, _level, accountLink);
                } else {
                    child = new TreeNode(this, oidTmp, number, label, showAllways, showSum, position, _level);
                }
                this.children.add(child);
            }

            for (final Node child : this.children) {
                child.addChildren(_level + 1);
            }
        }

        @Override
        public BigDecimal getSum()
        {
            BigDecimal temp = BigDecimal.ZERO;
            for (final Node child : getChildren()) {
                temp = temp.add(child.getSum());
            }
            return temp;
        }

    }

    class AccountNode
        extends Node
    {

        private final long accountId;
        private BigDecimal sum = BigDecimal.ZERO;

        /**
         * @param oid
         * @param number
         * @param label
         * @param showAllways
         * @param showSum
         */
        public AccountNode(final Node _parent,
                           final String oid,
                           final String number,
                           final String label,
                           final boolean showAllways,
                           final boolean showSum,
                           final Long position,
                           final int _level,
                           final long accountId)
        {
            super(_parent, oid, number, label, showAllways, showSum, position, _level);
            this.accountId = accountId;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected void addChildren(final int _level)
            throws EFapsException
        {
            final QueryBuilder queryBldr = new QueryBuilder(CIAccounting.TransactionPositionAbstract);
            queryBldr.addWhereAttrEqValue("AccountLink", this.accountId);
            final MultiPrintQuery print = queryBldr.getPrint();
            print.addAttribute("Amount");
            print.execute();
            while (print.next()) {
                final BigDecimal amount = print.<BigDecimal> getAttribute("Amount");
                this.sum = this.sum.add(amount);
            }
        }

        @Override
        public BigDecimal getSum()
        {
            return this.sum;
        }
    }

    public class PaddingCondition
        extends ConditionStyleExpression
        implements CustomExpression
    {

        private static final long serialVersionUID = 1L;
        private final int level;
        private final String key;

        public PaddingCondition(final int _level,
                                final String _key)
        {
            this.level = _level;
            this.key = _key;
        }

        @SuppressWarnings("unchecked")
        public Object evaluate(final Map _fields,
                               final Map _variables,
                               final Map _parameters)
        {
            Boolean ret = Boolean.FALSE;
            final Object value = getCurrentValue();
            if (value != null) {
                final FieldMapWrapper fields = (FieldMapWrapper) _fields;
                final Set set = fields.entrySet();
                for (final Object entryObj : set) {
                    final Entry entry = ((Entry) entryObj);
                    if (entry.getKey().equals(this.key)) {
                        final JRFillField field = (JRFillField) entry.getValue();
                        if (field != null) {
                            final String levelStr = field.getPropertiesMap().getProperty("level");
                            if (levelStr != null && Integer.parseInt(levelStr) == this.level) {
                                ret = Boolean.TRUE;
                            }
                        }
                    }
                }
            }
            return ret;
        }

        public String getClassName()
        {
            return Boolean.class.getName();
        }
    }

}
