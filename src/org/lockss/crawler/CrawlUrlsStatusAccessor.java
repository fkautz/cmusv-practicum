/*
 * $Id: CrawlUrlsStatusAccessor.java,v 1.9 2011/05/11 08:41:10 tlipkis Exp $
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

package org.lockss.crawler;
import java.util.*;
import org.lockss.daemon.status.*;
import org.lockss.daemon.*;
// import org.lockss.app.*;
import org.lockss.plugin.*;
// import org.lockss.plugin.base.*;
import org.lockss.util.*;
import static org.lockss.crawler.CrawlerStatus.UrlErrorInfo;

public class CrawlUrlsStatusAccessor implements StatusAccessor {

  private static final String URL = "url";
  private static final String IX = "ix";
  private static final String CRAWL_ERROR = "crawl_error";
  private static final String CRAWL_SEVERITY = "crawl_severity";

  private static final String FETCHED_TABLE_NAME = "fetched";
  private static final String ERROR_TABLE_NAME = "error";
  private static final String NOT_MODIFIED_TABLE_NAME = "not-modified";
  private static final String PARSED_TABLE_NAME = "parsed";
  private static final String PENDING_TABLE_NAME = "pending";
  private static final String EXCLUDED_TABLE_NAME = "excluded";
  private static final String MIMETYPES_TABLE_NAME = "mime-type";
   
  private static List colDescsFetched =
    ListUtil.list(new ColumnDescriptor(URL, "URL Fetched",
				       ColumnDescriptor.TYPE_STRING));

  private static List colDescsNotModified =
    ListUtil.list(new ColumnDescriptor(URL, "URL Not-Modified",
				       ColumnDescriptor.TYPE_STRING));

  private static List colDescsParsed =
    ListUtil.list(new ColumnDescriptor(URL, "URL Parsed",
				       ColumnDescriptor.TYPE_STRING));
 
  private static List colDescsPending =
    ListUtil.list(new ColumnDescriptor(URL, "URL Pending",
                                       ColumnDescriptor.TYPE_STRING));

  private static List colDescsError =
    ListUtil.list(new ColumnDescriptor(CRAWL_SEVERITY, "Severity",
				       ColumnDescriptor.TYPE_STRING,
				       "Errors and Fatal errors cause the crawl to fail, Warnings do not."),
		  new ColumnDescriptor(URL, "URL",
				       ColumnDescriptor.TYPE_STRING),
		  new ColumnDescriptor(CRAWL_ERROR, "Error",
				       ColumnDescriptor.TYPE_STRING));

  private static List colDescsExcluded =
    ListUtil.list(new ColumnDescriptor(URL, "URL Excluded",
				       ColumnDescriptor.TYPE_STRING));

  private List colDescsMimeTypeUrls; 

  
  private static final List statusSortRules =
    ListUtil.list(new StatusTable.SortRule(IX, true));

  private CrawlManager.StatusSource statusSource;

  public CrawlUrlsStatusAccessor(CrawlManager.StatusSource statusSource) {
    this.statusSource = statusSource;
  }

  public void populateTable(StatusTable table)
      throws StatusService.NoSuchTableException{
    if (table == null) {
      throw new IllegalArgumentException("Called with null table");
    } else if (table.getKey() == null) {
      throw new IllegalArgumentException("CrawlUrlsStatusAccessor requires a key");
    }
    String key = table.getKey();
    CrawlerStatus status;
    String tableStr;

    status = statusSource.getStatus().getCrawlerStatus(getStatusKeyFromTableKey(key));
    tableStr = getTableStrFromKey(key);
    if (status == null) {
      throw new StatusService.NoSuchTableException("Status info from that crawl is no longer available");
    }
    table.setDefaultSortRules(statusSortRules);
    table.setColumnDescriptors(getColDescs(tableStr, status));
    table.setTitle(getTableTitle(status, tableStr));
    table.setRows(makeRows(status, tableStr));
    table.setSummaryInfo(getSummaryInfo(tableStr, status));
  }

  private String getTableTitle(CrawlerStatus status, String tableStr) {
    String auName = status.getAuName();
    if (FETCHED_TABLE_NAME.equals(tableStr)) {
      return "URLs fetched during crawl of "+auName;
    } else if (ERROR_TABLE_NAME.equals(tableStr)) {
      return "Errors during crawl of "+auName;
    } else if (NOT_MODIFIED_TABLE_NAME.equals(tableStr)) {
      return "URLs not modified during crawl of "+auName;
    } else if (PARSED_TABLE_NAME.equals(tableStr)) {
      return "URLs parsed during crawl of "+auName;
    } else if (PENDING_TABLE_NAME.equals(tableStr)) {
      return "URLs pending during crawl of "+auName;
    } else if (EXCLUDED_TABLE_NAME.equals(tableStr)) {
      return "URLs excluded during crawl of "+auName;
    } else if (MIMETYPES_TABLE_NAME.equals(getMtTableStr(tableStr))) {
      return "URLs found during crawl of "+auName;
         //   + " with Mime type value: "+ getMimeTypeStr(tableStr) ;
    }
     return "";
  }

  private String getTableStrFromKey(String key) {
    return key.substring(key.indexOf(".")+1);
  }
  private String getMimeTypeStr(String tableStr) {
    return tableStr.substring(tableStr.indexOf(":")+1);
  }
  private String getMtTableStr(String tableStr) {
    return tableStr.substring(0, tableStr.indexOf(":"));
  }
  private String getStatusKeyFromTableKey(String key) {
    return key.substring(0, key.indexOf("."));
  }

  private List getColDescs(String tableStr, CrawlerStatus status) {
    if (FETCHED_TABLE_NAME.equals(tableStr)) {
      return colDescsFetched;
    } else if (ERROR_TABLE_NAME.equals(tableStr)) {
      return colDescsError;
    } else if (NOT_MODIFIED_TABLE_NAME.equals(tableStr)) {
      return colDescsNotModified;
    } else if (PARSED_TABLE_NAME.equals(tableStr)) {
      return colDescsParsed;
    } else if (PENDING_TABLE_NAME.equals(tableStr)) {
      return colDescsPending;
    } else if (EXCLUDED_TABLE_NAME.equals(tableStr)) {
      return colDescsExcluded;
    } else if (MIMETYPES_TABLE_NAME.equals(getMtTableStr(tableStr))) {    
      colDescsMimeTypeUrls =
	ListUtil.list(new ColumnDescriptor(URL, 
					   getMimeTypeStr(tableStr),
					   ColumnDescriptor.TYPE_STRING));
      return colDescsMimeTypeUrls;
    }
    return null;
  }

  private List makeRows(CrawlerStatus status, String tableStr) {
    List rows = null;

    if (FETCHED_TABLE_NAME.equals(tableStr)) {
      rows = urlSetToRows(status.getUrlsFetched());
    } else if (NOT_MODIFIED_TABLE_NAME.equals(tableStr)) {
      rows = urlSetToRows(status.getUrlsNotModified());
    } else if (PARSED_TABLE_NAME.equals(tableStr)) {
      rows = urlSetToRows(status.getUrlsParsed());
    } else if (PENDING_TABLE_NAME.equals(tableStr)) {
      rows = urlSetToRows(status.getUrlsPending());
    } else if (EXCLUDED_TABLE_NAME.equals(tableStr)) {
      rows = urlSetToRows(status.getUrlsExcluded());
    } else if (ERROR_TABLE_NAME.equals(tableStr)) {
      Map<String,UrlErrorInfo> errorMap = status.getUrlsErrorMap();
      Set errorUrls = errorMap.keySet();
      rows = new ArrayList(errorUrls.size());
      int ix = 1;
      for (Map.Entry<String,UrlErrorInfo> ent : errorMap.entrySet()) {
 	rows.add(makeRow(ent.getKey(), ix++, ent.getValue()));
      }
    } else if (MIMETYPES_TABLE_NAME.equals(getMtTableStr(tableStr))) {
      rows = urlSetToRows( status.getUrlsOfMimeType(getMimeTypeStr(tableStr)) );
    } 
    return rows;
  }

  /**
   * Take a set of URLs and make a row for each, where row{"URL"}=<url>
   */
  private List urlSetToRows(Collection urls) {
    List rows = new ArrayList(urls.size());
    return urlSetToRows(rows, urls);
  }

  /**
   * Take a set of URLs and make a row for each, where row{"URL"}=<url>
   */
  private List urlSetToRows(List rows, Collection urls) {
    int ix = rows.size();
    for (Iterator it = urls.iterator(); it.hasNext();) {
      String url = (String)it.next();
      rows.add(makeRow(url, ix++));
    }
    return rows;
  }

  private Map makeRow(String url, int ix) {
    Map row = new HashMap();
    row.put(URL, url);
    row.put(IX, new Integer(ix));
    return row;
  }

  private Map makeRow(String url, int ix, CrawlerStatus.UrlErrorInfo ui) {
    Map row = makeRow(url, ix);
    row.put(CRAWL_ERROR, ui.getMessage());
    row.put(CRAWL_SEVERITY, ui.getSeverity().toString());
    return row;
  }

  List getSummaryInfo(String tableStr, CrawlerStatus status) {
    if (EXCLUDED_TABLE_NAME.equals(tableStr)) {
      Collection excl = status.getUrlsExcluded();
      if (status.getNumExcludedExcludes() > 0) {
	List summary = new ArrayList();
	String str = status.getNumExcludedExcludes() +
	  " or fewer additional off-site URLs were excluded but not listed";
	summary.add(new StatusTable.SummaryInfo(null,
						ColumnDescriptor.TYPE_STRING,
						str));
	return summary;
      }
    }
    return null;
  }


  public String getDisplayName() {
    throw new UnsupportedOperationException("No generic name for SingleCrawlStatus");
  }

  public boolean requiresKey() {
    return true;
  }
}
