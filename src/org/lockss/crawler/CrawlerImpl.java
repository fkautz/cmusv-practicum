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

package org.lockss.crawler;

import java.io.*;
import java.util.*;
import java.net.URL;
import org.lockss.daemon.*;
import org.lockss.state.*;
import org.lockss.util.*;
import org.lockss.util.urlconn.*;
import org.lockss.plugin.*;
import org.lockss.plugin.base.*;

/**
 * The crawler.
 *
 * @author  Thomas S. Robertson
 * @version 0.0
 */
public abstract class CrawlerImpl implements Crawler {
  /**
   * TODO
   * 1) write state to harddrive using whatever system we come up for for the
   * rest of LOCKSS
   * 2) check deadline and die if we run too long
   */

  private static Logger logger = Logger.getLogger("CrawlerImpl");

  // See comments regarding connect timeouts in HttpClientUrlConnection
  public static final String PARAM_CONNECT_TIMEOUT =
    Configuration.PREFIX + "crawler.timeout.connect";
  public static long DEFAULT_CONNECT_TIMEOUT = 60 * Constants.SECOND;

  public static final String PARAM_DATA_TIMEOUT =
    Configuration.PREFIX + "crawler.timeout.data";
  public static long DEFAULT_DATA_TIMEOUT = 30 * Constants.MINUTE;

  public static final String PARAM_REFETCH_PERMISSIONS_PAGE =
    Configuration.PREFIX + "crawler.storePermissionsRefetch";
  public static final boolean DEFAULT_REFETCH_PERMISSIONS_PAGE = false;

  // Max amount we'll buffer up to avoid refetching the permissions page
  static final int PERM_BUFFER_MAX = 16 * 1024;

  protected ArchivalUnit au;

  protected LockssUrlConnectionPool connectionPool =
    new LockssUrlConnectionPool();

  protected Crawler.Status crawlStatus = null;

  protected int numUrlsFetched = 0;

  protected CrawlSpec spec = null;

  protected AuState aus = null;

  protected boolean crawlAborted = false;

  protected LockssWatchdog wdog = null;

  protected abstract boolean doCrawl0();
  public abstract int getType();

  protected CrawlerImpl(ArchivalUnit au, CrawlSpec spec, AuState aus) {
    if (au == null) {
      throw new IllegalArgumentException("Called with null au");
    } else if (spec == null) {
      throw new IllegalArgumentException("Called with null spec");
    } else if (aus == null) {
      throw new IllegalArgumentException("Called with null aus");
    }
    this.au = au;
    this.spec = spec;
    this.aus = aus;
    connectionPool = new LockssUrlConnectionPool();

    long connectTimeout =
      Configuration.getTimeIntervalParam(PARAM_CONNECT_TIMEOUT,
					 DEFAULT_CONNECT_TIMEOUT);
    long dataTimeout =
      Configuration.getTimeIntervalParam(PARAM_DATA_TIMEOUT,
					 DEFAULT_DATA_TIMEOUT);
    connectionPool.setConnectTimeout(connectTimeout);
    connectionPool.setDataTimeout(dataTimeout);
  }

  public ArchivalUnit getAu() {
    return au;
  }


  public Crawler.Status getStatus() {
    return crawlStatus;
  }


  public void abortCrawl() {
    crawlAborted = true;
  }

  /**
   * Main method of the crawler; it loops through crawling and caching
   * urls.
   *
   * @param deadline when to terminate by
   * @return true if no errors
   */
  public boolean doCrawl() {
    try {
      return doCrawl0();
    } finally {
      crawlStatus.signalCrawlEnded();
    }
  }

  boolean crawlPermission(CachedUrlSet ownerCus) {
    boolean crawl_ok = false;
    String err = Crawler.STATUS_PUB_PERMISSION;

    // fetch and cache the manifest page
    List permissionList = au.getPermissionPages();
    // TODO - fix this to do the right thing for the list of permission pages
    String manifest = (String)permissionList.get(0);
    UrlCacher uc = makeUrlCacher(ownerCus, manifest);
    logger.debug("Checking for permissions on " + manifest);
    uc.setRedirectScheme(UrlCacher.REDIRECT_SCHEME_FOLLOW);
    try {
      if (!au.shouldBeCached(manifest)) {
	logger.warning("Manifest not within CrawlSpec");
      } else if ((au.getCrawlSpec()!=null) && !au.getCrawlSpec().canCrawl()) {
	logger.debug("Couldn't start crawl due to crawl window.");
	err = Crawler.STATUS_WINDOW_CLOSED;
      } else {
	// check for the permission on the page without storing
	InputStream is = new BufferedInputStream(uc.getUncachedInputStream());
	try {
	  // allow us to reread contents if reasonable size
	  is.mark(PERM_BUFFER_MAX);
	  // set the reader to our default encoding
	  //XXX try to extract encoding from source
	  Reader reader =
	    new InputStreamReader(is, Constants.DEFAULT_ENCODING);
	  crawl_ok = au.checkCrawlPermission(reader);
	  if (!crawl_ok) {
	    logger.error("Couldn't start crawl due to missing permission.");
	  } else {
	    if (Configuration.getBooleanParam(PARAM_REFETCH_PERMISSIONS_PAGE,
					      DEFAULT_REFETCH_PERMISSIONS_PAGE)) {
	      logger.debug2("Permission granted. Caching permission page.");
	      storeManifest(ownerCus, manifest);
	    } else {
	      try {
		logger.debug2("Permission granted. Storing permission page.");
		is.reset();
		uc.storeContent(is, uc.getUncachedProperties());
	      } catch (IOException e) {
		logger.debug("Couldn't store from existing stream, refetching",
			     e);
		storeManifest(ownerCus, manifest);
	      }
	    }
	  }
	} finally {
	  is.close();
	}
      }
    } catch (Exception ex) {
      logger.error("Exception reading manifest", ex);
      crawlStatus.setCrawlError(Crawler.STATUS_FETCH_ERROR);
      return false;
    }
    if (!crawl_ok) {
      crawlStatus.setCrawlError(err);
    }
    return crawl_ok;
  }

  void storeManifest(CachedUrlSet ownerCus, String manifest)
      throws IOException {
    // XXX can't reuse UrlCacher
    UrlCacher uc = makeUrlCacher(ownerCus, manifest);
    uc.setRedirectScheme(UrlCacher.REDIRECT_SCHEME_FOLLOW);
    uc.cache();
  }

  /** All UrlCachers should be made via this method, so they get their
   * connection pool set. */
  protected UrlCacher makeUrlCacher(CachedUrlSet cus, String url) {
    ArchivalUnit au = cus.getArchivalUnit();
    Plugin plugin = au.getPlugin();
    UrlCacher uc = plugin.makeUrlCacher(cus, url);
    uc.setConnectionPool(connectionPool);
    return uc;
  }

  // For now only follow http: links
  public static boolean isSupportedUrlProtocol(String url) {
    return StringUtil.startsWithIgnoreCase(url, "http://");
  }

  // Was this.  If it's going to explicitly check for http://, there's
  // no point in creating the URL.
//   protected static boolean isSupportedUrlProtocol(String url) {
//     try {
//       URL ur = new URL(url);
//       // some 1.4 machines will allow this, so we explictly exclude it for now.
//       if (StringUtil.startsWithIgnoreCase(ur.toString(), "http://")) {
//         return true;
//       }
//     }
//     catch (Exception ex) {
//     }
//     return false;
//   }

  public void setWatchdog(LockssWatchdog wdog) {
    this.wdog = wdog;
  }

  public String toString() {
    StringBuffer sb = new StringBuffer();
    sb.append("[CrawlerImpl: ");
    sb.append(au.toString());
    sb.append("]");
    return sb.toString();
  }

}
