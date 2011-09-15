/*
 * $Id: HtmlKbartExporter.java,v 1.16 2011/09/15 19:19:44 pgust Exp $
 */

/*

Copyright (c) 2010-2011 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.exporter.kbart;

import java.util.Collections;
import java.util.List;
import java.io.OutputStream;
import java.io.IOException;

import javax.mail.search.OrTerm;

import org.lockss.config.Configuration;
import org.lockss.exporter.kbart.KbartTitle.Field;
import org.lockss.util.StringUtil;
import org.lockss.util.Logger;

/**
 * An exporter that simply writes the records to an HTML table.
 * 
 * @author Neil Mayo
 */
public class HtmlKbartExporter extends KbartExporter {

  private static Logger log = Logger.getLogger("HtmlKbartExporter");

  /** Whether to show health as tortoises by default. */
  static final String PREFIX = Configuration.PREFIX + "htmlExport.";
  public static final String PARAM_SHOW_TORTOISES = PREFIX + "showTortoises";
  public static final boolean DEFAULT_SHOW_TORTOISES = true;

  
  /** 
   * Width in pixels of columns with variable content - namely 
   * publication_title and publisher_name. 
   */
  //private int variableContentColumnWidth = 120;
  /** A switch to keep track of whether a line is odd or even. */
  private boolean odd = false;
  /** The table header will be printed every <code>headerInterval</code> rows. */
  private int headerInterval = 20;
  /** A header row for the table. */
  private String header;
  /** Summary of the export for display. */
  private String exportSummary;
  
  /** Whether to show health as tortoises! */
  private static boolean showHealthAsTortoises = DEFAULT_SHOW_TORTOISES;
  /** The minimum value for health when measured in tortoises. The maximum is 5. */
  private final int tortoiseBaseHealth = 1;
  
  /** 
   * The index of the issn field. Will have the td.issn style applied to its 
   * table cells to stop it wrapping. 
   */
  private int issnFieldIndex;
  /** 
   * The index of the eissn field. Will have the td.issn style applied to its 
   * table cells to stop it wrapping. 
   */
  private int eissnFieldIndex;
  /** 
   * The index of the issnl field. Will have the td.issn style applied to its 
   * table cells to stop it wrapping. 
   */
  private int issnlFieldIndex;
  /** 
   * The index of the optional health field. Will have an appropriate style 
   * applied to its table cells to show tortoises and stop it wrapping. 
   */
  private int healthFieldIndex;
  
  /**
   * Default constructor takes a list of KbartTitles to be exported.
   * The exporter then does some reasonably costly processing, iterating 
   * through the titles in order to establish which columns are entirely 
   * empty.
   * 
   * @param titles the titles which are to be exported
   * @param format the output format
   */
  public HtmlKbartExporter(List<KbartTitle> titles, OutputFormat format) {
    super(titles, format);
  }
  
  public void sortByField(Field f) {
    Collections.sort(titles, KbartTitleComparatorFactory.getComparator(f));
  }
  
  /** Called by org.lockss.config.MiscConfig
   */
  public static void setConfig(Configuration config,
			       Configuration oldConfig,
			       Configuration.Differences diffs) {
    if (diffs.contains(PREFIX)) {
      showHealthAsTortoises = config.getBoolean(PARAM_SHOW_TORTOISES, DEFAULT_SHOW_TORTOISES);
    }
  }

  @Override
  public void setFilter(KbartExportFilter filter) {
    super.setFilter(filter);
    // Use the filter to sort the titles for custom output 
    filter.sortTitlesByFirstTwoFields();
  }
  
  @Override
  protected void emitHeader() {
    printWriter.printf(this.header);
  }
  
  @Override
  protected void emitRecord(List<String> values) {
    odd = !odd;
    // Print a header every few rows
    if (exportCount % headerInterval == 0) {
      printWriter.printf(this.header); 
    }
    //printWriter.println( "<tr><td>" + StringUtil.separatedString(title.fieldValues(), SEPARATOR) + "</td></tr>" );
    printWriter.println("<tr class=\"" + (odd?"odd":"even") + "\">");
    
    // Add an index column at the start
    printWriter.printf("<td>%s</td>", this.exportCount);
    
    for (int i=0; i<values.size(); i++) {
      String val = values.get(i);
      if (StringUtil.isNullString(val)) val = "&nbsp;";
      // Add appropriate style to issn fields
      String cssClass = "";
      if (i==issnFieldIndex || i==eissnFieldIndex || i==issnlFieldIndex) {
	 cssClass = "issn";
      }
      // Add different type of entry for health ratings if required
      if (i==healthFieldIndex) {
	int rating = scaleHealth(val);
	cssClass = "health" + rating;
	if (showHealthAsTortoises) {
	  cssClass += "-tortoise";
	  val = "&nbsp;";
	}
      }
      if (StringUtil.isNullString(cssClass)) {
	printWriter.printf("<td>%s</td>", val);
      } else {
	printWriter.printf("<td class=\"%s\">%s</td>", cssClass, val);
      }
    }
    printWriter.println("</tr>");
  }

  /**
   * Take a health value between 0 and 1 and scale it to a value between
   * {@link tortoiseBaseHealth} and 5.
   */
  private int scaleHealth(double health) {
    int v = (int)Math.round(health * (5 - tortoiseBaseHealth));
    return tortoiseBaseHealth + v; 
  }
  
  /**
   * Take a health value between 0 and 1 as a string, and scale it to a value 
   * between {@link tortoiseBaseHealth} and 5. If the string cannot be parsed, 
   * the <code>tortoiseBaseHealth</code> is returned.
   */
  private int scaleHealth(String health) {
    try {
      return scaleHealth(Double.parseDouble(health));
    } catch (NumberFormatException e) {
      return tortoiseBaseHealth; 
    }
  }
  
  @Override
  protected void setup(OutputStream os) throws IOException {
    super.setup(os);
    // Construct a title and summary
    this.exportSummary = String.format("%s title list created on %s by %s " +
	"| %d items listed from %d titles.",
	scope, getDate(), getHostName(), titles.size(), tdbTitleTotal);
    this.header = makeHeader();
    this.issnFieldIndex = findFieldIndex(Field.PRINT_IDENTIFIER);
    this.eissnFieldIndex = findFieldIndex(Field.ONLINE_IDENTIFIER);
    this.issnlFieldIndex = findFieldIndex(Field.TITLE_ID);
    // The health field index will be one bigger than the last field index
    this.healthFieldIndex = getFieldLabels().size();
    // Write html and head tags, including a metatag declaring the content type UTF-8
    printWriter.println("<html><head><meta http-equiv=\"Content-Type\" " +
	"content=\"text/html; charset="+DEFAULT_ENCODING+"\"/>");
    printWriter.printf("<title>%s</title>", this.exportSummary);
    printWriter.printf("%s</head><body>", css);
    if (getHtmlCustomForm()!=null) printWriter.println(getHtmlCustomForm());
    // Initial attempt to get a static header on the page:
    //printWriter.printf("<div class=\"header\"><table>%s</table></div>", this.header);
    printWriter.println("<table>");
    //printWriter.printf(this.header);
  }

  /**
   * Find the index of the named field in the current output. If the field
   * cannot be found, returns -1. 
   * @param field a field
   * @return the index ofthe named field, or -1
   */
  private int findFieldIndex(Field field) {
    List<String> labs = getFieldLabels();
    String name = field.getLabel();
    for (int i=0; i<labs.size(); i++) {
      if (labs.get(i).equals(name)) return i;
    }
    return -1;
  }
  
  private String makeHeader() {
    return String.format("<tr><th>Index</th><th>%s</th></tr>", 
	StringUtil.separatedString(filter.getColumnLabels(scope), "</th><th>")	
    );
  }

  @Override
  protected void clearup() throws IOException {
    printWriter.println("</table>");
/* temporarily disabled (PJG)
    printWriter.printf("<br/><b><i>%s</i></b><br/>%s<br/>%s<br/>%s<br/><br/>", 
	this.exportSummary, 
	this.getOmittedTitlesSummary(),
	this.getOmittedFieldsSummary(), 
	this.getEmptySummary()
    );
*/
    printWriter.println("</body></html>");
    // Finally let superclass clear up
    super.clearup();
  }

  /** Some CSS for the output table. */
  private static final String css;
  static {
    StringBuilder sb = new StringBuilder();
    sb.append("<style type=\"text/css\">");
    sb.append("div.header { position: fixed; top: 0; left: 0; width: 100%; z-index: 99; background-color: navy; color: white; }");
    sb.append("table, th, tr, td { border: 1px solid black; }");
    sb.append("th { font-size: x-small; }");
    sb.append("tr.odd { background-color: #cce; }");
    sb.append("tr.even { background-color: #aac; }");
    sb.append("td.issn { white-space: nowrap; }");
    // General health style
    sb.append("td.health1, td.health2, td.health3, td.health4, td.health5 { font-weight: bold; text-align: center; }");
    sb.append("td.health1 { background-color: #cc0000; }"); // red
    sb.append("td.health2 { background-color: #cc6600; }"); // orange
    sb.append("td.health3 { background-color: #ffff00; }"); // yellow
    sb.append("td.health4 { background-color: #33ccff; }"); // blue
    sb.append("td.health5 { background-color: #00cc00; }"); // green
    // General health style for tortoise representation
    sb.append("td.health0-tortoise, td.health1-tortoise, td.health2-tortoise, td.health3-tortoise, td.health4-tortoise, td.health5-tortoise ");
    sb.append("{ min-width:175px; width: 175px; height: 35px; background: url('images/tortoises.gif') no-repeat }");
    sb.append("td.health0-tortoise { background: none; }");
    sb.append("td.health1-tortoise { background-position:  -140px }");
    sb.append("td.health2-tortoise { background-position:  -105px }");
    sb.append("td.health3-tortoise { background-position:  -70px }");
    sb.append("td.health4-tortoise { background-position:  -35px }");
    sb.append("td.health5-tortoise { background-position:  0px }");
    sb.append("</style>");
    css = sb.toString();
  };
 
}
