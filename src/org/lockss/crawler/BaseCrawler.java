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
import java.net.MalformedURLException;
import java.util.*;

import org.lockss.alert.*;
import org.lockss.config.*;
import org.lockss.daemon.*;
import org.lockss.plugin.*;
import org.lockss.plugin.base.BaseArchivalUnit;
import org.lockss.state.AuState;
import org.lockss.util.*;
import org.lockss.util.urlconn.*;

/**
 * The crawler.
 *
 * @author  Thomas S. Robertson
 * @version 0.0
 */
public abstract class BaseCrawler
       implements Crawler, PermissionMapSource, Crawler.PermissionHelper {
  /**
   * TODO
   * 1) write state to harddrive using whatever system we come up for for the
   * rest of LOCKSS
   * 2) check deadline and die if we run too long
   */

  static Logger logger = Logger.getLogger("BaseCrawler");

  // See comments regarding connect timeouts in HttpClientUrlConnection
  public static final String PARAM_CONNECT_TIMEOUT =
    Configuration.PREFIX + "crawler.timeout.connect";
  public static final long DEFAULT_CONNECT_TIMEOUT = 60 * Constants.SECOND;

  public static final String PARAM_DATA_TIMEOUT =
    Configuration.PREFIX + "crawler.timeout.data";
  public static final long DEFAULT_DATA_TIMEOUT = 30 * Constants.MINUTE;

  /** Proxy host for crawls (except repair-from-cache) */
  public static final String PARAM_PROXY_HOST =
    Configuration.PREFIX + "crawler.proxy.host";

  /** Proxy port for crawls (except repair-from-cache) */
  public static final String PARAM_PROXY_PORT =
    Configuration.PREFIX + "crawler.proxy.port";
  public static final int DEFAULT_PROXY_PORT = -1;

  public static final String PARAM_REFETCH_PERMISSIONS_PAGE =
    Configuration.PREFIX + "crawler.storePermissionsRefetch";
  public static final boolean DEFAULT_REFETCH_PERMISSIONS_PAGE = false;

  public static final String PARAM_ABORT_WHILE_PERMISSION_OTHER_THAN_OK =
    Configuration.PREFIX + "BaseCrawler.abortWhilePermissionOtherThanOk";
  public static final boolean DEFAULT_ABORT_WHILE_PERMISSION_OTHER_THAN_OK =
    false;

  // Max amount we'll buffer up to avoid refetching the permissions page
  static final int PERM_BUFFER_MAX = 16 * 1024;

  protected ArchivalUnit au;

  protected LockssUrlConnectionPool connectionPool =
    new LockssUrlConnectionPool();

  protected Crawler.Status crawlStatus = null;

  protected CrawlSpec spec = null;

  protected AuState aus = null;

  protected boolean crawlAborted = false;

  protected LockssWatchdog wdog = null;

  protected abstract boolean doCrawl0();
  public abstract int getType();


  /** Return the type of crawl as a string
   * @return crawl type
   */
  protected abstract String getTypeString();

  protected PermissionChecker pluginPermissionChecker;
  protected List daemonPermissionCheckers = null;
  protected AlertManager alertMgr;

  protected String proxyHost = null;
  protected int proxyPort;

  protected PermissionMap permissionMap = null;

  protected BaseCrawler(ArchivalUnit au, CrawlSpec spec, AuState aus) {
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

    /**
     There are two types of checkers: daemon and plug-in. The daemon ones are
     required, in that you must satisfy one of them to crawl a site.  The
     plug-in ones are optional, in that you don't need to specify any.
     However, you must satisfy each plug-in permission checker that is
     specified.

     The idea is that the plug-in can make the permission requirements more
     restrictive, but not less.
    */

    //At least one of these checkers must satisfied for us to crawl a site
    daemonPermissionCheckers = new LockssPermission().getCheckers();

    //Specified by the plug-in, this can be a null set.  We must satisfy
    //all of these to crawl a site.
    pluginPermissionChecker = spec.getPermissionChecker();

    alertMgr = (AlertManager)org.lockss.app.LockssDaemon.getManager(org.lockss.app.LockssDaemon.ALERT_MANAGER);
  }

  protected void setCrawlConfig(Configuration config) {
    long connectTimeout = config.getTimeInterval(PARAM_CONNECT_TIMEOUT,
                                                 DEFAULT_CONNECT_TIMEOUT);
    long dataTimeout = config.getTimeInterval(PARAM_DATA_TIMEOUT,
                                              DEFAULT_DATA_TIMEOUT);
    connectionPool.setConnectTimeout(connectTimeout);
    connectionPool.setDataTimeout(dataTimeout);

    proxyHost = config.get(PARAM_PROXY_HOST);
    proxyPort = config.getInt(PARAM_PROXY_PORT, DEFAULT_PROXY_PORT);
    if (StringUtil.isNullString(proxyHost) || proxyPort <= 0) {
      proxyHost = null;
    } else {
      if (logger.isDebug()) logger.debug("Proxying through " + proxyHost
					 + ":" + proxyPort);
    }
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
   * @return true if no errors
   */
  public boolean doCrawl() {
    setCrawlConfig(ConfigManager.getCurrentConfig());
    if (crawlAborted) {
      //don't start an aborted crawl
      return false;
    } else try {
      logger.info("Beginning crawl of "+au);
      crawlStatus.signalCrawlStarted();
      boolean res = doCrawl0();
      if (res == false || crawlStatus.getCrawlError() != null) {
	alertMgr.raiseAlert(Alert.auAlert(Alert.CRAWL_FAILED, au),
			    getTypeString() + " Crawl failed: " +
			    crawlStatus.getCrawlError());
      }
      return res;
    } catch (RuntimeException e) {
      logger.error("doCrawl0()", e);
      alertMgr.raiseAlert(Alert.auAlert(Alert.CRAWL_FAILED, au),
			  "Crawl of " + au.getName() +
			  "threw " + e.getMessage());
      throw e;
    } finally {
      crawlStatus.signalCrawlEnded();
    }
  }

  protected boolean populatePermissionMap() {
      // get the permission list from crawl spec
    permissionMap = new PermissionMap(au, this, daemonPermissionCheckers,
				      pluginPermissionChecker);
//    List permissionList = spec.getPermissionPages();
//    if (permissionList == null || permissionList.size() == 0) {
//      logger.error("spec.getPermissionPages() return null list or nothing in the list!");
//      crawlStatus.setCrawlError("Nothing in permission list");
//      return false;
//    }
    return permissionMap.init();
  }

  /**
   * Try to reset the provided input stream, if we can't then return
   * new input stream for the given url
   */
  public BufferedInputStream resetInputStream(BufferedInputStream is,
                                                 String url) throws IOException {
    try {
      is.reset();
    } catch (IOException e) {
      logger.debug("Couldn't reset input stream, so getting new one");
      is.close();
      UrlCacher uc = makeUrlCacher(url);
      uc.setRedirectScheme(UrlCacher.REDIRECT_SCHEME_FOLLOW_ON_HOST);
      is = new BufferedInputStream(uc.getUncachedInputStream());
      crawlStatus.signalUrlFetched(uc.getUrl());
    }
    return is;
  }

  public void refetchPermissionPage(String permissionPage)
      throws IOException {
    // XXX can't reuse UrlCacher
    UrlCacher uc = makeUrlCacher(permissionPage);
    uc.setRedirectScheme(UrlCacher.REDIRECT_SCHEME_FOLLOW);
    updateCacheStats(uc.cache(), uc);
  }

  protected void updateCacheStats(int cacheResult, UrlCacher uc) {
    switch (cacheResult) {
    case UrlCacher.CACHE_RESULT_FETCHED:
      crawlStatus.signalUrlFetched(uc.getUrl());
      CachedUrl cu = uc.getCachedUrl();
      if (cu != null && cu.hasContent()) {
	crawlStatus.addContentBytesFetched(cu.getContentSize());
      }
      break;
    case UrlCacher.CACHE_RESULT_NOT_MODIFIED:
      crawlStatus.signalUrlNotModified(uc.getUrl());
      break;
    }
  }

  protected void logCrawlSpecCacheRate() {
    if (au instanceof BaseArchivalUnit) {
      BaseArchivalUnit bau = (BaseArchivalUnit)au;
      long cacheHits = bau.getCrawlSpecCacheHits();
      long cacheMisses = bau.getCrawlSpecCacheMisses();
      if (cacheHits == 0) {
	logger.info(cacheHits + "/" + cacheMisses + " cache hits");
      } else {
	float per = (float)cacheHits / (float)(cacheHits + cacheMisses);
	logger.info(cacheHits + "/" + cacheMisses + " cache hits (" +
		    Integer.toString(Math.round(per * 100)) + "%)");
      }
    }
  }

  /** All UrlCachers should be made via this method, so they get their
   * connection pool set. */
  public UrlCacher makeUrlCacher(String url) {
    UrlCacher uc = au.makeUrlCacher(url);
    uc.setConnectionPool(connectionPool);
    uc.setPermissionMapSource(this);
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
    sb.append("[BaseCrawler: ");
    sb.append(au.toString());
    sb.append("]");
    return sb.toString();
  }

/**
 * Get the permission map from the crawler, creating it if it doesn't exist already
 */
  //PermissionMapSource method
  public PermissionMap getPermissionMap() {
    if (permissionMap == null) {
      populatePermissionMap();
    }
    return permissionMap;
  }

  protected boolean aborted() {
    logger.info("Crawl aborted: "+au);
    if (crawlStatus.getCrawlError() == null) {
      crawlStatus.setCrawlError(Crawler.STATUS_INCOMPLETE);
    }
    return false;
  }

  public Crawler.Status getCrawlStatus() {
    return crawlStatus;
  }
}
