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

package org.lockss.plugin.base;

import java.io.*;
import java.net.*;
import java.util.*;
import java.text.*;

import org.lockss.app.*;
import org.lockss.plugin.*;
import org.lockss.repository.*;
import org.lockss.util.*;
import org.lockss.util.urlconn.*;
import org.lockss.daemon.*;
import org.lockss.crawler.PermissionMap;

/**
 * Basic, fully functional UrlCacher.  Utilizes the LockssRepository for
 * caching, and {@link LockssUrlConnection}s for fetching.  Plugins may
 * extend this to achieve, <i>eg</i>, specialized host connection or
 * authentication.  The redirection semantics offered here must be
 * preserved.
 * @todo remove use of CachedUrlSet and replace with ArchivalUnit
 */
public class BaseUrlCacher implements UrlCacher {
  protected static Logger logger = Logger.getLogger("UrlCacher");

  /** Maximum number of redirects that will be followed */
  static final int MAX_REDIRECTS = 10;

  // Preferred date format according to RFC 2068(HTTP1.1),
  // RFC 822 and RFC 1123
  public static final SimpleDateFormat GMT_DATE_FORMAT =
    new SimpleDateFormat ("EEE, dd MMM yyyy HH:mm:ss 'GMT'");
  static {
    GMT_DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT"));
  }

  protected CachedUrlSet cus;
  protected ArchivalUnit au;
  protected String origUrl;		// URL with which I was created
  protected String fetchUrl;		// possibly affected by redirects
  private List otherNames;
  protected boolean forceRefetch = false;
  protected int redirectOptions = REDIRECT_OPTION_FOLLOW_AUTO;
  private LockssUrlConnectionPool connectionPool;
  private LockssUrlConnection conn;
  private LockssRepository repository;
  private CacheResultMap resultMap;
  private CIProperties uncachedProperties;
  private PermissionMapSource permissionMapSource;
  private String proxyHost = null;
  private int proxyPort;
  private Properties reqProps;


  private static final String SHOULD_REFETCH_ON_SET_COOKIE =
    "refetch_on_set_cookie";
  private static final boolean DEFAULT_SHOULD_REFETCH_ON_SET_COOKIE = true;

  public BaseUrlCacher(ArchivalUnit owner, String url) {
    //this.cus = owner;
    this.origUrl = url;
    this.fetchUrl = url;
    //au = owner.getArchivalUnit();
    au = owner;
    Plugin plugin = au.getPlugin();
    repository = plugin.getDaemon().getLockssRepository(au);
    resultMap = ((BasePlugin)plugin).getCacheResultMap();
  }

  /**
   * Returns the original URL (the one the UrlCacher was created with),
   * independent of any redirects followed.
   * @return the url string
   */
  public String getUrl() {
    return origUrl;
  }

  /**
   * Overrides normal <code>toString()</code> to return a string like
   * "BUC: <url>"
   * @return the class-url string
   */
  public String toString() {
    return "[BUC: "+origUrl+"]";
  }

  /**
   * Return the CachedUrlSet to which this UrlCacher belongs.
   * @return the owner CachedUrlSet
   */
  public CachedUrlSet getCachedUrlSet() {
    return cus;
  }

  /**
   * Return the ArchivalUnit to which this CachedUrl belongs.
   * @return the owner ArchivalUnit
   */
  public ArchivalUnit getArchivalUnit() {
    return au;
  }

  /**
   * Return <code>true</code> if the underlying url is one that the plug-in
   * believes should be preserved.  (<i>Ie</i>, is within the AU's
   * crawlSpec.)
   * @return a boolean indicating if it should be cached
   */
  public boolean shouldBeCached() {
    return au.shouldBeCached(origUrl);
  }

  /**
   * Return a CachedUrl for the content stored.  May be
   * called only after the content is completely written.
   * @return CachedUrl for the content stored.
   */
  public CachedUrl getCachedUrl() {
    return au.makeCachedUrl(origUrl);
  }

  public void setConnectionPool(LockssUrlConnectionPool connectionPool) {
    this.connectionPool = connectionPool;
  }

  public void setProxy(String proxyHost, int proxyPort) {
    this.proxyHost = proxyHost;
    this.proxyPort = proxyPort;
  }

  public void setForceRefetch(boolean force) {
    this.forceRefetch = force;
  }

  public void setRequestProperty(String key, String value) {
    if (reqProps == null) {
      reqProps = new Properties();
    }
    reqProps.put(key, value);
  }

  public void setRedirectScheme(RedirectScheme scheme) {
    this.redirectOptions = scheme.getOptions();
  }

  public int cache() throws IOException {
    String lastModified = null;
    if (!forceRefetch) {
      CachedUrl cachedVersion = getCachedUrl();

      // if it's been cached, get the last modified date and use that
      if ((cachedVersion!=null) && cachedVersion.hasContent()) {
	CIProperties cachedProps = cachedVersion.getProperties();
	lastModified =
	  cachedProps.getProperty(CachedUrl.PROPERTY_LAST_MODIFIED);
	cachedVersion.release();
      }
    }
    return cache(lastModified);
  }

  private int cache(String lastModified) throws IOException {
    logger.debug3("Pausing before fetching content");
    au.pauseBeforeFetch();
    logger.debug3("Done pausing");
    InputStream input = getUncachedInputStream(lastModified);
    // null input indicates unmodified content, so skip caching
    if (input == null) {
      return CACHE_RESULT_NOT_MODIFIED;
    }
    try {
      CIProperties headers = getHeaders();
      if (headers.get("Set-Cookie") != null) {
	if (shouldRefetchOnCookies()) {
	  logger.debug3("Found set-cookie header, refetching");
	  input.close();
	  input = null; // ensure don't reclose in finally if next line throws
	  input = getUncachedInputStream(lastModified);
	  if (input == null) {
	    //this is odd if it happens.
	    logger.warning("Got null input stream on second call to getUncachedInputStream");
	    return CACHE_RESULT_NOT_MODIFIED;
	  }
	  headers = getHeaders();
	}
      }
      storeContent(input, headers);
      return CACHE_RESULT_FETCHED;
    } finally {
      if (input != null) {
	input.close();
      }
    }
  }

  private boolean shouldRefetchOnCookies() {
    TypedEntryMap pMap = getParamMap();
    if (pMap != null) {
      boolean shouldRefetchOnCookies =
	pMap.getBoolean(SHOULD_REFETCH_ON_SET_COOKIE,
			DEFAULT_SHOULD_REFETCH_ON_SET_COOKIE);
      logger.debug3("Should refetch on cookies is "+shouldRefetchOnCookies);
      return shouldRefetchOnCookies;
    }
    logger.debug3("No param map found, returning default shouldRefetchOnCookies: "
		  +DEFAULT_SHOULD_REFETCH_ON_SET_COOKIE);
    return DEFAULT_SHOULD_REFETCH_ON_SET_COOKIE;
  }

  private CIProperties getHeaders() throws IOException {
    CIProperties headers = getUncachedProperties();
    if (headers == null) {
      String err = "Received null headers for url '" + origUrl + "'.";
      logger.error(err);
      throw new NullPointerException(err);
    }
    return headers;
  }

  /** Store into the repository the content and headers from a successful
   * fetch.  If redirects were followed and
   * REDIRECT_OPTION_STORE_ALL was specified, store the content and
   * headers under each name in the chain of redirections.
   */
  public void storeContent(InputStream input, CIProperties headers)
      throws IOException {
    if (logger.isDebug2()) logger.debug2("Storing url '"+ origUrl +"'");
    storeContentIn(origUrl, input, headers);
    if (otherNames != null &&
	isRedirectOption(REDIRECT_OPTION_STORE_ALL)) {
      CachedUrl cu = getCachedUrl();
      CIProperties headerCopy  = CIProperties.fromProperties(headers);
      int last = otherNames.size() - 1;
      for (int ix = 0; ix <= last; ix++) {
	String name = (String)otherNames.get(ix);
	if (logger.isDebug2())
	  logger.debug2("Storing in redirected-to url '"+ name +"'");
	InputStream is = cu.getUnfilteredInputStream();
	if (ix < last) {
	  // this one was redirected, set its redirected-to prop to the
	  // next in the list.
	  headerCopy.setProperty(CachedUrl.PROPERTY_REDIRECTED_TO,
				 (String)otherNames.get(ix + 1));
	} else if (!name.equals(fetchUrl)) {
	  // Last in list.  If not same as fetchUrl, means the final
	  // redirection was a directory(slash) redirection, which we don't
	  // store as a different name or put on otherNames.  Indicate the
	  // redirection to the slashed version.  The proxy must be aware
	  // of this.  (It can't rely on this property being present,
	  // becuase foo/ might later be fetched, not due to a redirect
	  // from foo.)
	  headerCopy.setProperty(CachedUrl.PROPERTY_REDIRECTED_TO, fetchUrl);
	} else {
	  // This is the name that finally got fetched, don't store
	  // redirect prop or content-url
	  headerCopy.remove(CachedUrl.PROPERTY_REDIRECTED_TO);
	  headerCopy.remove(CachedUrl.PROPERTY_CONTENT_URL);
	}
	storeContentIn(name, is, headerCopy);
      }
    }
  }

  public void storeContentIn(String url, InputStream input,
			     CIProperties headers)
      throws IOException {
    RepositoryNode leaf = null;
    try {
      leaf = repository.createNewNode(url);
      leaf.makeNewVersion();

      OutputStream os = leaf.getNewOutputStream();
      StreamUtil.copy(input, os);
      input.close();
      os.close();
      headers.setProperty(CachedUrl.PROPERTY_NODE_URL, url);
      leaf.setNewProperties(headers);
      leaf.sealNewVersion();
    }
    catch (Exception ex) {
      logger.debug("storeContentIn", ex);
      if (leaf != null) {
	try {
	  leaf.abandonNewVersion();
	} catch (Exception e) {
	  // just being paranoid
	}
      }
      throw resultMap.getRepositoryException(ex);
    }
  }

  public InputStream getUncachedInputStream() throws IOException {
    return getUncachedInputStream(null);
  }

  /**
   * Gets an InputStream for this URL, using the last modified time as
   * 'if-modified-since'.  If a 304 is generated (not modified), it returns
   * null.
   * @param lastModified the last modified time
   * @return the InputStream, or null
   * @throws IOException
   */
  protected InputStream getUncachedInputStream(String lastModified)
      throws IOException {
    InputStream input = null;
    try {
      openConnection(lastModified);
      if (conn.isHttp()) {
	// http connection; check response code
	int code = conn.getResponseCode();
	if (code == HttpURLConnection.HTTP_NOT_MODIFIED) {
	  logger.debug2("Unmodified content not cached for url '" +
			origUrl + "'");
	  return null;
	}
      }
      input = conn.getResponseInputStream();
    } finally {
      if (conn != null && input == null) {
	logger.debug3("Releasing connection");
	conn.release();
      }
    }
    return input;
  }

  public CIProperties getUncachedProperties() throws IOException {
    if (conn == null) {
      throw new UnsupportedOperationException("Called getUncachedProperties before calling getUncachedInputStream.");
    }
//     openConnection();
    if (uncachedProperties == null) {
      CIProperties props = new CIProperties();
      // set header properties in which we have interest

      String ctype = conn.getResponseContentType();
      if (ctype != null) {
	props.setProperty(CachedUrl.PROPERTY_CONTENT_TYPE, ctype);
      }
      props.setProperty(CachedUrl.PROPERTY_FETCH_TIME,
			Long.toString(TimeBase.nowMs()));
      if (origUrl != fetchUrl &&
	  !UrlUtil.isDirectoryRedirection(origUrl, fetchUrl)) {
	// XXX this property does not have consistent semantics.  It will be
	// set to the first url in a chain of redirects that led to content,
	// which could be different depending on fetch order.
	props.setProperty(CachedUrl.PROPERTY_ORIG_URL, origUrl);
      }
      conn.storeResponseHeaderInto(props, CachedUrl.HEADER_PREFIX);
      String actualURL = conn.getActualUrl();
      if (!origUrl.equals(actualURL)) {
	props.setProperty(CachedUrl.PROPERTY_CONTENT_URL, actualURL);
      }
      if (otherNames != null) {
	props.setProperty(CachedUrl.PROPERTY_REDIRECTED_TO,
			  (String)otherNames.get(0));
      } else if (!origUrl.equals(actualURL)) {
	props.setProperty(CachedUrl.PROPERTY_REDIRECTED_TO, actualURL);
      }
      uncachedProperties = props;
    }
    return uncachedProperties;
  }

  void checkConnectException(LockssUrlConnection conn) throws IOException {
    if(conn.isHttp()) {
      if (logger.isDebug3()) {
	logger.debug3("Response: " + conn.getResponseCode() + ": " +
		      conn.getResponseMessage());
      }
      CacheException c_ex = resultMap.checkResult(conn);
      if(c_ex != null) {
	// The stack below here is misleading.  Makes more sense for it
	// to reflect the point at which it's thrown
	c_ex.fillInStackTrace();
	throw c_ex;
      }
    }
  }

  /**
   * If we haven't already connected, creates a connection from url, setting
   * the user-agent and ifmodifiedsince values.  Then actually connects to the
   * site and throws if we get an error code
   */
  private void openConnection(String lastModified) throws IOException {
    if (conn==null) {
      if (isRedirectOption(REDIRECT_OPTION_IF_CRAWL_SPEC +
			   REDIRECT_OPTION_ON_HOST_ONLY)) {
	openWithRedirects(lastModified);
      } else {
	openOneConnection(lastModified);
      }
    }
  }

  private void openWithRedirects(String lastModified) throws IOException {
    int retry = 0;
    while (true) {
      try {
	openOneConnection(lastModified);
	break;
      } catch (CacheException.NoRetryNewUrlException e) {
	if (++retry >= MAX_REDIRECTS) {
	  logger.warning("Max redirects hit, not redirecting " + origUrl +
			 " past " + fetchUrl);
	  throw e;
	} else if (!processRedirectResponse(e instanceof CacheException.NoRetryPermUrlException)) {
	  throw e;
	}
      }
    }
  }

  /** Overridable so testing code can return a MockLockssUrlConnection */
  protected LockssUrlConnection makeConnection(String url,
					       LockssUrlConnectionPool pool)
      throws IOException {
    return UrlUtil.openConnection(url, pool);
  }

  /**
   * If we haven't already connected, creates a connection from url, setting
   * the user-agent and ifmodifiedsince values.  Then actually connects to the
   * site and throws if we get an error code
   */
  private void openOneConnection(String lastModified) throws IOException {
    try {
      conn = makeConnection(fetchUrl, connectionPool);
      if (proxyHost != null) {
	if (logger.isDebug3()) logger.debug3("Proxying through " + proxyHost
					     + ":" + proxyPort);
	conn.setProxy(proxyHost, proxyPort);
      }
      if (reqProps != null) {
	for (Iterator iter = reqProps.keySet().iterator(); iter.hasNext(); ) {
	  String key = (String)iter.next();
	  conn.setRequestProperty(key, reqProps.getProperty(key));
	}
      }
      conn.setFollowRedirects(isRedirectOption(REDIRECT_OPTION_FOLLOW_AUTO));
      conn.setRequestProperty("user-agent", LockssDaemon.getUserAgent());

      if (lastModified != null) {
	conn.setIfModifiedSince(lastModified);
      }
      conn.execute();
    } catch (MalformedURLException ex) {
      logger.warning("openConnection", ex);
      throw resultMap.getMalformedURLException(ex);
    } catch (IOException ex) {
      logger.warning("openConnection", ex);
      throw resultMap.getHostException(ex);
    } catch (RuntimeException e) {
      logger.warning("openConnection: unexpected exception", e);
      throw e;
    }
    checkConnectException(conn);
  }

  /** Handle a single redirect response: determine whether it should be
   * followed and change the state (fetchUrl) to set up for the next fetch.
   * @param writeToRedirectedUrl boolean indicating whether content should
   * be written to the redirected URL
   * @return true if another request should be issued, false if not. */
  private boolean processRedirectResponse(boolean writeToRedirectedUrl) {
    //get the location header to find out where to redirect to
    String location = conn.getResponseHeaderValue("location");
    if (location == null) {
      // got a redirect response, but no location header
      logger.error("Received redirect response " + conn.getResponseCode()
		   + " but no location header");
      return false;
    }
    if (logger.isDebug3()) {
      logger.debug3("Redirect requested from '" + fetchUrl +
		    "' to '" + location + "'");
    }
    // update the current location with the redirect location.
    try {
      String newUrlString = UrlUtil.resolveUri(fetchUrl, location);
      if (isRedirectOption(REDIRECT_OPTION_IF_CRAWL_SPEC)) {
	if (!au.shouldBeCached(newUrlString)) {
	  logger.warning("Redirect not in crawl spec: " + newUrlString +
			 " from: " + origUrl);
	  return false;
	}
      }
      PermissionMap permissionMap = null;
      if (permissionMapSource != null) {
	logger.debug3("Getting permission map");
	permissionMap = permissionMapSource.getPermissionMap();
      }

      // TODO: swap isSameHost with isRedirectOption and
      // add permission check on same level as redirectOption.
      if(!UrlUtil.isSameHost(fetchUrl, newUrlString)) {
	if (isRedirectOption(REDIRECT_OPTION_ON_HOST_ONLY)) {
	  logger.warning("Redirect to different host: " + newUrlString +
			 " from: " + origUrl);
	  return false;
	} else if(permissionMap == null ||
		  permissionMap.getStatus(newUrlString) != PermissionMap.PERMISSION_OK) {
          logger.warning("No permission for redirect to different host: "
                         + newUrlString + " from: " + origUrl);
          return false;
        }
      }
      conn.release();
      conn = null;

      // XXX
      // The names .../foo and .../foo/ map to the same repository node, so
      // the case of a slash-appending redirect requires special handling.
      // (Still. sigh.)  The node should be written only once, so don't add
      // another entry for the slash redirection.

      if (writeToRedirectedUrl &&
	  !UrlUtil.isDirectoryRedirection(fetchUrl, newUrlString)) {
	if (otherNames == null) {
	  otherNames = new ArrayList();
	}
	//XXX don't add to otherNames if 302, 303, 307
	//
	otherNames.add(newUrlString);
      }
      fetchUrl = newUrlString;
      logger.debug2("Following redirect to " + newUrlString);
      return true;
    } catch (MalformedURLException e) {
      logger.warning("Redirected location '" + location + "' is malformed", e);
      return false;
    }
  }

  /** Return true iff there are options in common between the argument and
   * redirectOptions */
  private boolean isRedirectOption(int option) {
    return (redirectOptions & option) != 0;
  }
  /**
   * setPermissionMap
   *
   * @param permissionMap PermissionMap
   */
  /*
  public void setPermissionMap(PermissionMap permissionMap) {
    this.permissionMap = permissionMap;
  }
*/

  /**
   * Sets the PermissionMapSource object, which is what BaseUrlCacher
   * will use to get the permission map when needed
   *
   */
  public void setPermissionMapSource(PermissionMapSource permissionMapSource) {
    this.permissionMapSource = permissionMapSource;
  }

  protected BaseArchivalUnit.ParamHandlerMap getParamMap() {
    try {
      BaseArchivalUnit bau = (BaseArchivalUnit) au;
      return bau.getParamMap();
    } catch (ClassCastException ex) {
      logger.error("Expected au to be BaseArchivalUnit", ex);
    }
    return null;
  }
}
