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

package org.lockss.daemon;

import java.util.*;
import org.lockss.util.*;
import org.lockss.oai.*;

/**
 * Specification for a crawl: a Oai request handler Url  and a rule
 * that determines whether a candidate URL should be included in the crawl.
 */
public class OaiCrawlSpec extends BaseCrawlSpec {

  private boolean followLink;
  private OaiRequestData oaiRequestData;

  //XXXOAI for testing purposes ------------------------
  public OaiCrawlSpec(String oaiRequestHandlerUrl, CrawlRule rule) {
    this(new OaiRequestData(oaiRequestHandlerUrl,
			 "http://purl.org/dc/elements/1.1/",
			 "identifier",
			 "",
			 "oai_dc"), 
	 ListUtil.list("http://171.66.236.27:8181/html/permission.html"),
	 Collections.EMPTY_LIST,
 	 rule,
 	 true);
  }

  public OaiCrawlSpec(String oaiRequestHandlerUrl, CrawlRule rule, List permissionList, boolean follow) {
    this(new OaiRequestData(oaiRequestHandlerUrl,
			    "http://purl.org/dc/elements/1.1/",
			    "identifier",
			    "",
			    "oai_dc"), 
	 permissionList,
	 Collections.EMPTY_LIST,
 	 rule,
 	 follow);
  }

  //----------------------------------------------------
		      
  /**
   * Construct an OaiCrawlSpec with an OaiRequestData object, permission urls,
   * permission checkers, crawl rule and a follow link flag
   * 
   * @param OaiRequestData the object contain all the information for issuing
   * and Oai request and do the proper parsing in the response.
   * @param permissionUrls a list of urls from which permission can be obtained.
   * @param permissionCheckers a list of permissionCheckers specify by the plugin
   * @param rule filter to determine which URLs encountered in the crawl
   * should themselves be crawled.  A null rule is always true.
   * @param followLink set to true if the crawl want to have the follow link behavior
   * @throws IllegalArgumentException if the oaiRequestData is null.
   */
  public OaiCrawlSpec(OaiRequestData oaiRequestData,
		      List permissionUrls,
		      List permissionCheckers,
		      CrawlRule rule,
		      boolean followLink
		      ) {
    super(permissionUrls,
	  rule,
	  permissionCheckers);
    if (oaiRequestData == null){
      throw new IllegalArgumentException("Called with null oaiRequestData");
    }
    this.oaiRequestData = oaiRequestData;
    this.followLink = followLink;
  }

  /** 
   * Gets the oaiRequestData object and return it.
   * @return the oaiRequestData object
   */
  public OaiRequestData getOaiRequestData(){
    return oaiRequestData;
  }
  
  /**
   * Check if the crawler need to follow the link it parsed.
   * @return true iff the crawl is a follow link crawl
   */
  public boolean getFollowLinkFlag(){
    return followLink;
  }

//   public void setFollowLinkFlag(boolean follow){
//     followLink = follow;
//   }

}
