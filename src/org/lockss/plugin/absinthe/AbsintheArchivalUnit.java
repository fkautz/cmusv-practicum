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
package org.lockss.plugin.absinthe;

import java.net.*;
import java.util.*;
import org.lockss.util.*;
import org.lockss.daemon.*;
import org.lockss.state.*;
import org.lockss.plugin.*;
import org.lockss.plugin.base.*;
import gnu.regexp.REException;

/**
 * <p>AbsintheArchivalUnit: The Archival Unit Class for Absinthe Literary
 * Review</p>
 * @author Emil Aalto
 * @version 1.0
 */

public class AbsintheArchivalUnit extends BaseArchivalUnit {
  /**
   * Configuration parameter for new content crawl interval
   */
  static final String AUPARAM_NEW_CONTENT_CRAWL = NEW_CONTENT_CRAWL_KEY;
  private static final long DEFAULT_NEW_CONTENT_CRAWL = 2 * Constants.WEEK;

  /**
   * Configuration parameter for pause time between fetchs.
   */
  public static final String AUPARAM_PAUSE_TIME = PAUSE_TIME_KEY;
  private static final long DEFAULT_PAUSE_TIME = 10 * Constants.SECOND;

  protected Logger logger = Logger.getLogger("AbsinthePlugin");

  private String year; // the year

  protected AbsintheArchivalUnit(Plugin myPlugin) {
    super(myPlugin);
  }

  protected void setAuParams(Configuration config) throws
      ConfigurationException {
    if (config == null) {
      throw new ConfigurationException("Null configInfo");
    }
    // get the year string
    year = config.get(AbsinthePlugin.AUPARAM_YEAR);
    // turn them into appropriate types
    if ( (year.length() != 4) && (year.length() != 2)) {
      throw new ConfigurationException("Invalid year: " + year);
    }

  }

  protected String makeName() {
    StringBuffer name = new StringBuffer(baseUrl.getHost());
    name.append(", ");
    if (year.length()==2) {
      // convert to '03
      name.append("'");
    }
    name.append(year);
    return name.toString();
  }

  protected String makeStartUrl() {
    String ret;
    StringBuffer sb = new StringBuffer();
    sb.append(baseUrl.toString());
    sb.append("archives");
    if (year.length()==4) {
      sb.append(year.substring(year.length()-2));
    } else if (year.length()==2) {
      sb.append(year);
    }
    sb.append(".htm");
    ret = sb.toString();
    logger.debug("starting url is " + ret);
    return ret;
  }

  protected CrawlRule makeRules()
      throws REException {
    List rules = new LinkedList();
    final int incl = CrawlRules.RE.MATCH_INCLUDE;
    final int excl = CrawlRules.RE.MATCH_EXCLUDE;
    String rootUrl = baseUrl.toString();
    rules.add(new CrawlRules.RE("^" + rootUrl, CrawlRules.RE.NO_MATCH_EXCLUDE));
    rules.add(new CrawlRules.RE(startUrlString, incl));
    rules.add(new CrawlRules.RE(rootUrl + "stories/.*", incl));
    rules.add(new CrawlRules.RE(rootUrl + "poetics/.*", incl));
    rules.add(new CrawlRules.RE(rootUrl + "archives/.*", incl));
    // exclude book review index page
    rules.add(new CrawlRules.RE(rootUrl + "book_reviews/book_reviews.htm", excl));
    // include other pages
    rules.add(new CrawlRules.RE(rootUrl + "book_reviews/.*", incl));
    rules.add(new CrawlRules.RE(rootUrl + "images/.*", incl));
    return new CrawlRules.FirstMatch(rules);
  }

}
