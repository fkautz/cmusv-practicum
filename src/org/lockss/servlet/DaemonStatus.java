/*
 * $Id$
 */

/*

Copyright (c) 2000-2003 Board of Trustees of Leland Stanford Jr. University,
all rights reserved.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL
STANFORD UNIVERSITY BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

Except as contained in this notice, the name of Stanford University shall not
be used in advertising or otherwise to promote the sale, use or other dealings
in this Software without prior written authorization from Stanford University.

*/

package org.lockss.servlet;

import javax.servlet.*;
import java.io.*;
import java.util.*;
import java.text.*;
import org.mortbay.html.*;
import org.lockss.util.*;
import org.lockss.config.Configuration;
import org.lockss.daemon.*;
import org.lockss.daemon.status.*;
import org.lockss.plugin.*;

import org.w3c.dom.*;

/**
 * DaemonStatus servlet
 */
public class DaemonStatus extends LockssServlet {
  protected static Logger log = Logger.getLogger("DaemonStatus");

  /** Supported output formats */
  static final int OUTPUT_HTML = 1;
  static final int OUTPUT_TEXT = 2;
  static final int OUTPUT_XML = 3;

  /** Format to display date/time in tables */
  public static final DateFormat tableDf =
    new SimpleDateFormat("HH:mm:ss MM/dd/yy");

//   public static final DateFormat tableDf =
//     DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);

  private String tableName;
  private String tableKey;
  private String sortKey;
  private StatusService statSvc;
  private int outputFmt;
  private BitSet tableOptions;
  private PluginManager pluginMgr;

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
    statSvc = getLockssDaemon().getStatusService();
    pluginMgr = getLockssDaemon().getPluginManager();
  }

  /**
   * Handle a request
   * @throws IOException
   */
  public void lockssHandleRequest() throws IOException {
    outputFmt = OUTPUT_HTML;	// default output is html

    // allow "text=" for backwards compatibility
    if (req.getParameter("text") != null) {
      outputFmt = OUTPUT_TEXT;
    }
    String outputParam = req.getParameter("output");
    if (!StringUtil.isNullString(outputParam)) {
      if ("html".equalsIgnoreCase(outputParam)) {
	outputFmt = OUTPUT_HTML;
      } else if ("xml".equalsIgnoreCase(outputParam)) {
	outputFmt = OUTPUT_XML;
      } else if ("text".equalsIgnoreCase(outputParam)) {
	outputFmt = OUTPUT_TEXT;
      } else {
	log.warning("Unknown output format: " + outputParam);
      }
    }
    String optionsParam = req.getParameter("options");

    tableOptions = new BitSet();

    if (isDebugUser()) {
      log.debug2("Debug user.  Setting OPTION_INCLUDE_INTERNAL_AUS");
      tableOptions.set(StatusTable.OPTION_INCLUDE_INTERNAL_AUS);
    }
    
    for (Iterator iter = StringUtil.breakAt(optionsParam, ',').iterator();
	 iter.hasNext(); ) {
      String s = (String)iter.next();
      if ("norows".equalsIgnoreCase(s)) {
	tableOptions.set(StatusTable.OPTION_NO_ROWS);
      }
    }

    tableName = req.getParameter("table");
    tableKey = req.getParameter("key");
    if (StringUtil.isNullString(tableName)) {
      tableName = StatusService.ALL_TABLES_TABLE;
    }
    if (StringUtil.isNullString(tableKey)) {
      tableKey = null;
    }
    sortKey = req.getParameter("sort");
    if (StringUtil.isNullString(sortKey)) {
      sortKey = null;
    }

    switch (outputFmt) {
    case OUTPUT_HTML:
      doHtmlStatusTable();
      break;
    case OUTPUT_XML:
      try {
        doXmlStatusTable();
      } catch (XmlDomBuilder.XmlDomException xde) {
        throw new IOException("Error with XML: "+xde.toString());
      }
      break;
    case OUTPUT_TEXT:
      doTextStatusTable();
      break;
    }
  }

  /** Display a "The cache isn't ready yet, come back later" message if
   *  not all of the AUs have started yet.
   */
  protected void displayNotStarted(Page page) throws IOException {
    Composite warning = new Composite();
    warning.add("<center><font color=red size=+1>");
    warning.add("This LOCKSS Cache is still starting.  Table contents may be incomplete.");
    warning.add("</font></center><br>");
    page.add(warning);
  }

  private void doHtmlStatusTable() throws IOException {
    Page page = newPage();
    resp.setContentType("text/html");

    if (!pluginMgr.areAusStarted()) {
      displayNotStarted(page);
    }

    // After resp.getWriter() has been called, throwing an exception will
    // result in a blank page, so don't call it until the end.
    // (HttpResponse.sendError() calls getOutputStream(), and only one of
    // getWriter() or getOutputStream() may be called.)

    // all pages but index get a select box to choose a different table
    if (!isAllTablesTable()) {
      Block centeredBlock = new Block(Block.Center);
      centeredBlock.add(getSelectTableForm());
      page.add(centeredBlock);
    }

    //       page.add("<center>");
    //       page.add(srvLink(SERVLET_DAEMON_STATUS, ".",
    // 		       concatParams("text=1", req.getQueryString())));
    //       page.add("</center><br><br>");
    doHtmlOrTextStatusTable(page, null);
    page.add(getFooter());
    page.write(resp.getWriter());
  }

  private void doTextStatusTable() throws IOException {
    PrintWriter wrtr = resp.getWriter();
    resp.setContentType("text/plain");

    String vPlatform = Configuration.getParam(PARAM_PLATFORM_VERSION);
    if (vPlatform != null) {
      vPlatform = ", cd=" + vPlatform;
    } else {
      vPlatform = "";
    }
    Date now = new Date();
    Date startDate = getLockssDaemon().getStartDate();
    wrtr.println("host=" + getLcapIPAddr() +
		 ",time=" + now.getTime() +
		 ",up=" + TimeBase.msSince(startDate.getTime()) +
		 ",version=" + BuildInfo.getBuildInfoString() +
		 vPlatform);
    doHtmlOrTextStatusTable(null, wrtr);
  }

  // build and send an XML DOM Document of the StatusTable
  private void doXmlStatusTable()
      throws IOException, XmlDomBuilder.XmlDomException {
    PrintWriter wrtr = resp.getWriter();
    resp.setContentType("text/xml");
    try {
      StatusTable statTable = statSvc.getTable(tableName, tableKey,
					       tableOptions);
      XmlStatusTable xmlTable = new XmlStatusTable(statTable);
      Document xmlTableDoc = xmlTable.getTableDocument();
      xmlTable.getXmlDomBuilder().serialize(xmlTableDoc, wrtr);
    } catch (Exception e) {
      XmlDomBuilder xmlBuilder =
          new XmlDomBuilder(XmlStatusConstants.NS_PREFIX,
                            XmlStatusConstants.NS_URI,
                            "1.0");
      Document errorDoc = xmlBuilder.createDocument();
      org.w3c.dom.Element rootElem = xmlBuilder.createRoot(errorDoc,
          XmlStatusConstants.ERROR);
      if (e instanceof StatusService.NoSuchTableException) {
        xmlBuilder.addText(rootElem, "No such table: " + e.toString());
      } else {
        String emsg = e.toString();
        StringBuffer buffer = new StringBuffer("Error getting table: ");
        buffer.append(emsg);
        buffer.append("\n");
        buffer.append(StringUtil.trimStackTrace(emsg,
                                                StringUtil.stackTraceString(e)));
        xmlBuilder.addText(rootElem, buffer.toString());
      }
      xmlBuilder.serialize(errorDoc, wrtr);
      return;
    }
  }

  // Build the table, adding elements to page or writing text to wrtr
  private void doHtmlOrTextStatusTable(Page page, PrintWriter wrtr)
      throws IOException {
    StatusTable statTable;
    try {
      statTable = statSvc.getTable(tableName, tableKey, tableOptions);
    } catch (StatusService.NoSuchTableException e) {
      if (outputFmt == OUTPUT_HTML) {
	page.add("No such table: ");
	page.add(e.toString());
      } else {
	wrtr.println("No table: " + e.toString());
      }
      return;
    } catch (Exception e) {
      if (outputFmt == OUTPUT_HTML) {
	page.add("Error getting table: ");
	String emsg = e.toString();
	page.add(emsg);
	page.add("<br><pre>    ");
	page.add(StringUtil.trimStackTrace(emsg,
					   StringUtil.stackTraceString(e)));
	page.add("</pre>");
      } else {
	wrtr.println("Error getting table: " + e.toString());
      }
      return;
    }
    java.util.List colList = statTable.getColumnDescriptors();
    java.util.List rowList;
    java.util.List rules = null;
    if (sortKey != null) {
      try {
	rules = makeSortRules(statTable, sortKey);
	rowList = statTable.getSortedRows(rules);
      } catch (Exception e) {
	// There are lots of ways a user-specified sort can fail if the
	// table creator isn't careful.  Fall back to default if that
	// happens.
	log.warning("Error sorting table by: " + rules, e);
	// XXX should display some sort of error msg
	rowList = statTable.getSortedRows();
	rules = null;		  // prevent column titles from indicating
				  // the sort order that didn't work
      }
    } else {
      rowList = statTable.getSortedRows();
    }
    String title0 = statTable.getTitle();
    String titleFoot = statTable.getTitleFootnote();

    Table table = null;

    // convert list of ColumnDescriptors to array of ColumnDescriptors
    ColumnDescriptor cds[];
    int cols;
    if (colList != null) {
      cds = (ColumnDescriptor [])colList.toArray(new ColumnDescriptor[0]);
      cols = cds.length;
    } else {
      cds = new ColumnDescriptor[0];
      cols = 1;
    }
    if (true || !rowList.isEmpty()) {
      // if table not empty, output column headings

      // Make the table.  Make a narrow empty column between real columns,
      // for spacing.  Resulting table will have 2*cols-1 columns
      table = new Table(0, "ALIGN=CENTER CELLSPACING=2 CELLPADDING=0");
      if (outputFmt == OUTPUT_HTML) {
	String title = title0 + addFootnote(titleFoot);

	table.newRow();
	table.addHeading(title, "ALIGN=CENTER COLSPAN=" + (cols * 2 - 1));
	table.newRow();
	addSummaryInfo(table, statTable, cols);

	if (colList != null) {
	  // output column headings
	  for (int ix = 0; ix < cols; ix++) {
	    ColumnDescriptor cd = cds[ix];
	    table.newCell("class=colhead valign=bottom align=" +
			  ((cols == 1) ? "center" : getColAlignment(cd)));
	    table.add(getColumnTitleElement(statTable, cd, rules));
	    if (ix < (cols - 1)) {
	      table.newCell("width=8");
	      table.add("&nbsp;");
	    }
	  }
	}
      } else {
	wrtr.println();
	wrtr.println("table=" + title0);
	if (tableKey != null) {
	  wrtr.println("key=" + tableKey);
	}
	// tk write summary info
      }

    }
    if (rowList != null) {
      // output rows
      for (Iterator rowIter = rowList.iterator(); rowIter.hasNext(); ) {
	Map rowMap = (Map)rowIter.next();
	if (outputFmt == OUTPUT_HTML) {
	  if (rowMap.get(StatusTable.ROW_SEPARATOR) != null) {
	    table.newRow();
	    table.newCell("align=center colspan=" + (cols * 2 - 1));
	    table.add("<hr>");
	  }
	  table.newRow();
	  for (int ix = 0; ix < cols; ix++) {
	    ColumnDescriptor cd = cds[ix];
	    Object val = rowMap.get(cd.getColumnName());

	    table.newCell("valign=top align=" + getColAlignment(cd));
	    table.add(getDisplayString(val, cd.getType()));
	    if (ix < (cols - 1)) {
	      table.newCell();	// empty column for spacing
	    }
	  }
	} else {
	  for (Iterator iter = rowMap.keySet().iterator(); iter.hasNext(); ) {
	    Object o = iter.next();
	    if (!(o instanceof String)) {
	      // ignore special markers (eg, StatusTable.ROW_SEPARATOR)
	      continue;
	    }
	    String key = (String)o;
	    Object val = rowMap.get(key);
	    Object dispVal = StatusTable.getActualValue(val);
	    String valStr = dispVal != null ? dispVal.toString() : "(null)";
	    wrtr.print(key + "=" + valStr);
	    if (iter.hasNext()) {
	      wrtr.print(",");
	    } else {
	      wrtr.println();
	    }
	  }
	}
      }
    }
    if (outputFmt == OUTPUT_HTML && table != null) {
      Form frm = new Form(srvURL(myServletDescr(), null));
      // use GET so user can refresh in browser
      frm.method("GET");
      frm.add(table);
      page.add(frm);
      page.add("<br>");
      String heading = getHeading();
      // put table name in page title so appears in browser title & tabs
      page.title("LOCKSS: " + title0 + " - " + heading);
    }
  }

  static final Image UPARROW1 = image("uparrow1blue.gif", 16, 16, 0,
				      "Primary sort column, ascending");
  static final Image UPARROW2 = image("uparrow2blue.gif", 16, 16, 0,
				      "Secondary sort column, ascending");
  static final Image DOWNARROW1 = image("downarrow1blue.gif", 16, 16, 0,
					"Primary sort column, descending");
  static final Image DOWNARROW2 = image("downarrow2blue.gif", 16, 16, 0,
					"Secondary sort column, descending");

  /** Create a column heading element:<ul>
   *   <li> plain text if not sortable
   *   <li> if sortable, link with sortkey set to solumn name,
   *        descending if was previous primary ascending key
   *   <li> plus a possible secondary sort key set to previous sort column
   *   <li> if is current primary or secondary sort colume, display an up or
   *        down arrow.</ul>
   * @param statTable
   * @param cd
   * @param rules the SortRules used to sort the currently displayed table
   */
  Composite getColumnTitleElement(StatusTable statTable, ColumnDescriptor cd,
				  java.util.List rules) {
    Composite elem = new Composite();
    Image sortArrow = null;
    boolean ascending = true;
    String colTitle = cd.getTitle();
    if (true && statTable.isResortable() && cd.isSortable()) {
      String ruleParam;
      if (rules != null && !rules.isEmpty()) {
	StatusTable.SortRule rule1 = (StatusTable.SortRule)rules.get(0);
	if (cd.getColumnName().equals(rule1.getColumnName())) {
	  // This column is the current primary sort; link to reverse order
	  ascending = !rule1.sortAscending();
	  // and display a primary arrow
	  sortArrow = rule1.sortAscending() ? UPARROW1 : DOWNARROW1;
	  if (rules.size() > 1) {
	    // keep same secondary sort key if there was one
	    StatusTable.SortRule rule2 = (StatusTable.SortRule)rules.get(1);
	    ruleParam = ruleParam(cd, ascending) + "," + ruleParam(rule2);
	  } else {
	    ruleParam = ruleParam(cd, ascending);
	  }
	} else {
	  if (rules.size() > 1) {
	    StatusTable.SortRule rule2 = (StatusTable.SortRule)rules.get(1);
	    if (cd.getColumnName().equals(rule2.getColumnName())) {
	      // This is the secondary sort column; display secondary arrow
	      sortArrow = rule2.sortAscending() ? UPARROW2 : DOWNARROW2;
	    }
	  }
	  // primary sort is this column, secondary is previous primary
	  ruleParam = ruleParam(cd, ascending) + "," + ruleParam(rule1);
	}
      } else {
	// no previous, sort by column
	ruleParam = ruleParam(cd, ascending);
      }
      Link link = new Link(srvURL(myServletDescr(),
				  modifyParams("sort", ruleParam)),
			   colTitle);
      link.attribute("class", "colhead");
      elem.add(link);
      String foot = cd.getFootnote();
      if (foot != null) {
	elem.add(addFootnote(foot));
      }
      if (sortArrow != null) {
	elem.add(sortArrow);
      }
    } else {
      elem.add(colTitle);
      elem.add(addFootnote(cd.getFootnote()));
    }
    return elem;
  }

  String ruleParam(ColumnDescriptor cd, boolean ascending) {
    return (ascending ? "A" : "D") + cd.getColumnName();
  }

  String ruleParam(StatusTable.SortRule rule) {
    return (rule.sortAscending() ? "A" : "D") + rule.getColumnName();
  }

  java.util.List makeSortRules(StatusTable statTable, String sortKey) {
    Map columnDescriptorMap = statTable.getColumnDescriptorMap();
    java.util.List cols = StringUtil.breakAt(sortKey, ',');
    java.util.List res = new ArrayList();
    for (Iterator iter = cols.iterator(); iter.hasNext(); ) {
      String spec = (String)iter.next();
      boolean ascending = spec.charAt(0) == 'A';
      String col = spec.substring(1);
      StatusTable.SortRule defaultRule =
	getDefaultRuleForColumn(statTable, col);
      StatusTable.SortRule rule;
      Comparator comparator = null;
      if (columnDescriptorMap.containsKey(col)) {
	ColumnDescriptor cd = (ColumnDescriptor)columnDescriptorMap.get(col);
	comparator = cd.getComparator();
      }
      if (defaultRule != null && defaultRule.getComparator() != null) {
	comparator = defaultRule.getComparator();
      }
      if (comparator != null) {
	rule = new StatusTable.SortRule(col, comparator, ascending);
      } else {
	rule = new StatusTable.SortRule(col, ascending);
      }
      res.add(rule);
    }
    log.debug2("rules: " + res);
    return res;
  }

  private StatusTable.SortRule getDefaultRuleForColumn(StatusTable statTable,
						       String col) {
    java.util.List defaults = statTable.getDefaultSortRules();
    for (Iterator iter = defaults.iterator(); iter.hasNext(); ) {
      StatusTable.SortRule rule = (StatusTable.SortRule)iter.next();
      if (col.equals(rule.getColumnName())) {
	return rule;
      }
    }
    return null;
  }


  private void addSummaryInfo(Table table, StatusTable statTable, int cols) {
    java.util.List summary = statTable.getSummaryInfo();
    if (summary != null && !summary.isEmpty()) {
      for (Iterator iter = summary.iterator(); iter.hasNext(); ) {
	StatusTable.SummaryInfo sInfo =
	  (StatusTable.SummaryInfo)iter.next();
	table.newRow();
	StringBuffer sb = new StringBuffer();
	sb.append("<b>");
	sb.append(sInfo.getTitle());
	if (sInfo.getFootnote() != null) {
	  sb.append(addFootnote(sInfo.getFootnote()));
	}
	sb.append("</b>: ");
	sb.append(getDisplayString(sInfo.getValue(), sInfo.getType()));
	table.newCell("COLSPAN=" + (cols * 2 - 1));
	table.add(sb.toString());
      }
      table.newRow();
    }
  }

  private String getColAlignment(ColumnDescriptor cd) {
    switch (cd.getType()) {
    case ColumnDescriptor.TYPE_STRING:
    case ColumnDescriptor.TYPE_FLOAT:	// tk - should align decimal points?
    case ColumnDescriptor.TYPE_DATE:
    case ColumnDescriptor.TYPE_IP_ADDRESS:
    case ColumnDescriptor.TYPE_TIME_INTERVAL:
    default:
      return "LEFT";
    case ColumnDescriptor.TYPE_INT:
    case ColumnDescriptor.TYPE_PERCENT:
      return "RIGHT";
    }
  }


  // Handle lists
  private String getDisplayString(Object val, int type) {
    if (val instanceof java.util.List) {
      StringBuffer sb = new StringBuffer();
      for (Iterator iter = ((java.util.List)val).iterator(); iter.hasNext(); ) {
	sb.append(getDisplayString0(iter.next(), type));
      }
      return sb.toString();
    } else {
      return getDisplayString0(val, type);
    }
  }

  // turn References into html links
  private String getDisplayString0(Object val, int type) {
    if (val instanceof StatusTable.Reference) {
      StatusTable.Reference ref = (StatusTable.Reference)val;
      StringBuffer sb = new StringBuffer();
      sb.append("table=");
      sb.append(ref.getTableName());
      String key = ref.getKey();
      if (!StringUtil.isNullString(key)) {
	sb.append("&key=");
	sb.append(urlEncode(key));
      }
      return srvLink(myServletDescr(), getDisplayString1(ref.getValue(), type),
		     sb.toString());
    } else {
      return getDisplayString1(val, type);
    }
  }

  // add display attributes from a DisplayedValue
  private String getDisplayString1(Object val, int type) {
    if (val instanceof StatusTable.DisplayedValue) {
      StatusTable.DisplayedValue aval = (StatusTable.DisplayedValue)val;
      String str = getDisplayString1(aval.getValue(), type);
      String color = aval.getColor();
      if (color != null) {
	str = "<font color=" + color + ">" + str + "</font>";
      }
      if (aval.getBold()) {
	str = "<b>" + str + "</b>";
      }
      return str;
    } else {
      return convertDisplayString(val, type);
    }
  }

  static NumberFormat bigIntFmt = NumberFormat.getInstance();
  static {
    if (bigIntFmt instanceof DecimalFormat) {
//       ((DecimalFormat)bigIntFmt).setDecimalSeparatorAlwaysShown(true);
    }
  };

  // turn a value into a display string
  public static String convertDisplayString(Object val, int type) {
    if (val == null) {
      return "";
    }
    try {
      switch (type) {
      case ColumnDescriptor.TYPE_INT:
	if (val instanceof Number) {
	  long lv = ((Number)val).longValue();
	  if (lv >= 1000000) {
	    return bigIntFmt.format(lv);
	  }
	}
	// fall thru
      case ColumnDescriptor.TYPE_STRING:
      case ColumnDescriptor.TYPE_FLOAT:
      default:
	return val.toString();
      case ColumnDescriptor.TYPE_PERCENT:
	float fv = ((Number)val).floatValue();
	return Integer.toString(Math.round(fv * 100)) + "%";
      case ColumnDescriptor.TYPE_DATE:
	Date d;
	if (val instanceof Number) {
	  d = new Date(((Number)val).longValue());
	} else if (val instanceof Date) {
	  d = (Date)val;
	} else if (val instanceof Deadline) {
	  d = ((Deadline)val).getExpiration();
	} else {
	  return val.toString();
	}
	return dateString(d);
      case ColumnDescriptor.TYPE_IP_ADDRESS:
	return ((IPAddr)val).getHostAddress();
      case ColumnDescriptor.TYPE_TIME_INTERVAL:
	long millis = ((Number)val).longValue();
	return StringUtil.timeIntervalToString(millis);
      }
    } catch (NumberFormatException e) {
      log.warning("Bad number: " + val.toString() + ": " + e.toString());
      return val.toString();
    } catch (ClassCastException e) {
      log.warning("Wrong type value: " + val.toString() + ": " + e.toString());
      return val.toString();
    } catch (Exception e) {
      log.warning("Error formatting value: " + val.toString() + ": " + e.toString());
      return val.toString();
    }
  }

  static String dateString(Date d) {
    long val = d.getTime();
    if (val == 0 || val == -1) {
      return "never";
    } else {
      return tableDf.format(d);
    }
  }

  /**
   * Build a form with a select box that fetches a named table
   * @return the Composite object
   */
  private Composite getSelectTableForm() {
    try {
      StatusTable statTable =
        statSvc.getTable(StatusService.ALL_TABLES_TABLE, null);
      java.util.List colList = statTable.getColumnDescriptors();
      java.util.List rowList = statTable.getSortedRows();
      ColumnDescriptor cd = (ColumnDescriptor)colList.get(0);
      Select sel = new Select("table", false);
      sel.attribute("onchange", "this.form.submit()");
      boolean foundIt = false;
      for (Iterator rowIter = rowList.iterator(); rowIter.hasNext(); ) {
        Map rowMap = (Map)rowIter.next();
        Object val = rowMap.get(cd.getColumnName());
        String display = StatusTable.getActualValue(val).toString();
        if (val instanceof StatusTable.Reference) {
          StatusTable.Reference ref = (StatusTable.Reference)val;
          String key = ref.getTableName();
          // select the current table
          boolean isThis = (tableKey == null) && tableName.equals(key);
          foundIt = foundIt || isThis;
          sel.add(display, isThis, key);
        } else {
          sel.add(display, false);
        }
      }
      // if not currently displaying a table in the list, select a blank entry
      if (!foundIt) {
        sel.add(" ", true, "");
      }
      Form frm = new Form(srvURL(myServletDescr(), null));
      // use GET so user can refresh in browser
      frm.method("GET");
      frm.add(sel);
      return frm;
    } catch (Exception e) {
      // if this fails for any reason, just don't include this form
      log.warning("Failed to build status table selector", e);
      return new Composite();
    }
  }

  protected boolean isAllTablesTable() {
    return StatusService.ALL_TABLES_TABLE.equals(tableName);
  }

  // make me a link in nav table unless I'm displaying table of all tables
  protected boolean linkMeInNav() {
    return !isAllTablesTable();
  }
}
