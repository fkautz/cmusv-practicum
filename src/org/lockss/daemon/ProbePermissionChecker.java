/*
 * $Id$
 */

/*

Copyright (c) 2000-2005 Board of Trustees of Leland Stanford Jr. University,
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

import java.io.*;
import java.util.*;

import org.lockss.plugin.*;
import org.lockss.util.*;
import org.lockss.crawler.*;
import org.lockss.extractor.*;

/**
 * This Permission checker looks for a probe (a URL) which is identified by
 * being in a link tag with a specific attribute.  It will then pass that URL
 * to another specified permission checker
 */

public class ProbePermissionChecker implements PermissionChecker {

  String probeUrl = null;

  private static Logger logger = Logger.getLogger("ProbePermissionChecker");

  ArchivalUnit au;

  public ProbePermissionChecker(ArchivalUnit au) {
    if (au == null) {
      throw new NullPointerException("Called with null archival unit");
    }
    this.au = au;
  }

  // For compatibility with plugins that supply a LoginPageChecker (which
  // is not used)
  public ProbePermissionChecker(LoginPageChecker checker, ArchivalUnit au) {
    this(au);
  }

  public boolean checkPermission(Crawler.PermissionHelper pHelper,
				 Reader inputReader, String permissionUrl) {
    probeUrl = null;
    CustomHtmlLinkExtractor extractor = new CustomHtmlLinkExtractor();
    logger.debug3("Checking permission on "+permissionUrl);
    try {
      // XXX ReaderInputStream needed until PermissionChecker changed to
      // take InputStream instead of Reader
      extractor.extractUrls(au, new ReaderInputStream(inputReader), null,
			       permissionUrl, new MyLinkExtractorCallback());
    } catch (IOException ex) {
      logger.error("Exception trying to parse permission url "+permissionUrl,
		   ex);
      return false;
    }
    if (probeUrl != null) {
      logger.debug3("Found probeUrl "+probeUrl);
      try {
	UrlCacher uc = pHelper.makeUrlCacher(probeUrl);
	// XXX is this the right redirect option?
	uc.setRedirectScheme(UrlCacher.REDIRECT_SCHEME_FOLLOW_ON_HOST);
 	InputStream is = new BufferedInputStream(uc.getUncachedInputStream());
	logger.debug3("Not a login page");
	return true;
      } catch (org.lockss.util.urlconn.CacheException.PermissionException ex) {
	logger.debug3("Found a login page");
	return false;
      } catch (IOException ex) {
	logger.error("Exception trying to check for login page "+probeUrl, ex);
	return false;
      }	
    } else {
      logger.warning("Didn't find a probe URL on "+permissionUrl);
    }
    return false;
  }


  private static class CustomHtmlLinkExtractor
    extends GoslingHtmlLinkExtractor {

    private static final String LOCKSSPROBE = "lockss-probe";

    protected String extractLinkFromTag(StringBuffer link, ArchivalUnit au,
					LinkExtractor.Callback cb) {
      String returnStr = null;

      switch (link.charAt(0)) {
        case 'l': //<link href=blah.css>
        case 'L':
	  logger.debug3("Looking for probe in "+link);
	  if (beginsWithTag(link, LINKTAG)) {
	    returnStr = getAttributeValue(HREF, link);
	    String probeStr = getAttributeValue(LOCKSSPROBE, link);
	    if (!"true".equalsIgnoreCase(probeStr)) {
	      returnStr = null;
	    }
	  }
	  break;
        default:
	  return null;
      }
      return returnStr;
    }
  }

  private class MyLinkExtractorCallback implements LinkExtractor.Callback {
    public MyLinkExtractorCallback() {
    }

    public void foundLink(String url) {
      if (probeUrl != null) {
	logger.warning("Multiple probe URLs found on manifest page.  " +
			"Old: "+probeUrl+" New: "+url);
      }
      probeUrl = url;
    }
  }
}
