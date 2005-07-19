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
import org.lockss.plugin.*;
import org.lockss.daemon.*;
import org.lockss.test.*;
import org.lockss.app.*;
import org.lockss.util.*;
import org.lockss.util.urlconn.*;
import org.lockss.repository.*;
import org.lockss.crawler.*;

/**
 * This is the test class for org.lockss.plugin.simulated.GenericFileUrlCacher
 *
 * @author  Emil Aalto
 * @version 0.0
 */
public class TestBaseUrlCacher extends LockssTestCase {

  private static final int REFETCH_FLAG = 0;
  private static final int CLEAR_DAMAGE_FLAG = 1;
  private static final int REFETCH_IF_DAMAGE_FLAG = 2;

  static DateFormat GMT_DATE_FORMAT = BaseUrlCacher.GMT_DATE_FORMAT;

  MyMockBaseUrlCacher cacher;
  MockCachedUrlSet mcus;
  MockPlugin plugin;

  private MyMockArchivalUnit mau;
  private MockLockssDaemon theDaemon;
  private LockssRepository repo;
  private int pauseBeforeFetchCounter;

  private MockNodeManager nodeMgr = new MockNodeManager();

  private static final String TEST_URL =
    "http://www.example.com/testDir/leaf1";
  private boolean saveDefaultSuppressStackTrace;

  public void setUp() throws Exception {
    super.setUp();

    String tempDirPath = getTempDir().getAbsolutePath() + File.separator;
    CIProperties props = new CIProperties();
    props.setProperty(LockssRepositoryImpl.PARAM_CACHE_LOCATION, tempDirPath);
    ConfigurationUtil.setCurrentConfigFromProps(props);

    theDaemon = getMockLockssDaemon();
    theDaemon.getHashService();

    mau = new MyMockArchivalUnit();
    mau.setCrawlSpec(new SpiderCrawlSpec(tempDirPath, null));
    plugin = new MockPlugin();
    plugin.initPlugin(theDaemon);
    mau.setPlugin(plugin);

    repo =
      (LockssRepository)theDaemon.newAuManager(LockssDaemon.LOCKSS_REPOSITORY,
					       mau);
    theDaemon.setLockssRepository(repo, mau);

    theDaemon.setNodeManager(nodeMgr, mau);

    mcus = new MockCachedUrlSet(TEST_URL);
    mcus.setArchivalUnit(mau);
    mau.setAuCachedUrlSet(mcus);
    cacher = new MyMockBaseUrlCacher(mau, TEST_URL);
    saveDefaultSuppressStackTrace =
      CacheException.setDefaultSuppressStackTrace(false);
  }

  public void tearDown() throws Exception {
    TimeBase.setReal();
    CacheException.setDefaultSuppressStackTrace(saveDefaultSuppressStackTrace);
    super.tearDown();
  }

  public void testCache() throws IOException {
    pauseBeforeFetchCounter = 0;

    cacher._input = new StringInputStream("test stream");
    cacher._headers = new CIProperties();
    // should cache
    assertEquals(UrlCacher.CACHE_RESULT_FETCHED, cacher.cache());
    assertTrue(cacher.wasStored);
    assertEquals(1, pauseBeforeFetchCounter);
  }

  public void testReCacheWCookie() throws IOException {
    pauseBeforeFetchCounter = 0;

    cacher._input = new StringInputStream("test stream");
    CIProperties headers = new CIProperties();
    headers.put("Set-Cookie", "blah");
    cacher._headers = headers;
    // should cache
    assertEquals(UrlCacher.CACHE_RESULT_FETCHED, cacher.cache());
    assertTrue(cacher.wasStored);
    assertEquals(2, cacher.getUncachedInputStreamCount);
    assertEquals(2, cacher.getUncachedPropertiesCount);
    assertEquals(1, pauseBeforeFetchCounter);
  }

  public void testReCacheWCookieOverride() throws IOException {
    BaseArchivalUnit.ParamHandlerMap pMap =
      new BaseArchivalUnit.ParamHandlerMap();
    pMap.putBoolean("refetch_on_set_cookie", false);
    cacher.setParamMap(pMap);
    pauseBeforeFetchCounter = 0;

    cacher._input = new StringInputStream("test stream");
    CIProperties headers = new CIProperties();
    headers.put("Set-Cookie", "blah");
    cacher._headers = headers;
    // should cache
    assertEquals(UrlCacher.CACHE_RESULT_FETCHED, cacher.cache());
    assertTrue(cacher.wasStored);
    assertEquals(1, cacher.getUncachedInputStreamCount);
    assertEquals(1, cacher.getUncachedPropertiesCount);
    assertEquals(1, pauseBeforeFetchCounter);
  }

  public void testLastModifiedCache() throws IOException {
    // add the 'cached' version
    CIProperties cachedProps = new CIProperties();
    cachedProps.setProperty(CachedUrl.PROPERTY_LAST_MODIFIED,
			    GMT_DATE_FORMAT.format(new Date(12345)));
//     mcus.addUrl("test stream", TEST_URL, true, true, cachedProps);
     mau.addUrl(TEST_URL, true, true, cachedProps);

    TimeBase.setSimulated(10000);
    cacher._input = new StringInputStream("test stream");
    cacher._headers = new CIProperties();
    // shouldn't cache
    assertEquals(UrlCacher.CACHE_RESULT_NOT_MODIFIED, cacher.cache());
    assertFalse(cacher.wasStored);

    TimeBase.step(5000);
    cacher._input = new StringInputStream("test stream");
    cacher._headers = new CIProperties();
    // should cache now
    assertEquals(UrlCacher.CACHE_RESULT_FETCHED, cacher.cache());
    assertTrue(cacher.wasStored);

    TimeBase.setReal();
  }

  public void testForceCache() throws IOException {
    // add the 'cached' version
    CIProperties cachedProps = new CIProperties();
    cachedProps.setProperty(CachedUrl.PROPERTY_LAST_MODIFIED,
			    GMT_DATE_FORMAT.format(new Date(12345)));
//     mcus.addUrl("test stream", TEST_URL, true, true, cachedProps);
    mau.addUrl(TEST_URL, true, true, cachedProps);

    TimeBase.setSimulated(10000);
    cacher._input = new StringInputStream("test stream");
    cacher._headers = cachedProps;
    // should still cache
//     cacher.setForceRefetch(true);
    BitSet bs = new BitSet();
    bs.set(REFETCH_FLAG);
    cacher.setFetchFlags(bs);
    cacher.cache();
    assertTrue(cacher.wasStored);

    TimeBase.setReal();
  }

  public void testCacheClearsDamage() throws IOException {
    MockDamagedNodeSet dnSet = new MockDamagedNodeSet();
    dnSet.addToDamage(TEST_URL);
    nodeMgr.setDamagedNodes(dnSet);

    cacher._input = new StringInputStream("test stream");
    cacher._headers = new CIProperties();
    // should cache
    BitSet bs = new BitSet();
    bs.set(CLEAR_DAMAGE_FLAG);
    cacher.setFetchFlags(bs);
    assertEquals(UrlCacher.CACHE_RESULT_FETCHED, cacher.cache());
    assertTrue(cacher.wasStored);
    assertFalse(dnSet.hasDamage(TEST_URL));
  }

  public void testRefetchIfDamage() throws IOException {
    MockDamagedNodeSet dnSet = new MockDamagedNodeSet();
    dnSet.addToDamage(TEST_URL);
    nodeMgr.setDamagedNodes(dnSet);

    CIProperties cachedProps = new CIProperties();
    cachedProps.setProperty(CachedUrl.PROPERTY_LAST_MODIFIED,
			    GMT_DATE_FORMAT.format(new Date(12345)));
    mau.addUrl(TEST_URL, true, true, cachedProps);

    TimeBase.setSimulated(10000);
    cacher._input = new StringInputStream("test stream");
    cacher._headers = new CIProperties();
    // should cache
    BitSet bs = new BitSet();
    bs.set(REFETCH_IF_DAMAGE_FLAG);
    cacher.setFetchFlags(bs);
    assertEquals(UrlCacher.CACHE_RESULT_FETCHED, cacher.cache());
    assertTrue(cacher.wasStored);
    TimeBase.setReal();
  }

  public void testCacheExceptions() throws IOException {
    cacher._input = new StringInputStream("test stream");
    cacher._headers = null;
    try {
      cacher.cache();
      fail("Should have thrown NullPointerException.");
    } catch (NullPointerException npe) { }
    assertFalse(cacher.wasStored);

    // no exceptions from null inputstream
    cacher._input = null;
    cacher._headers = new CIProperties();
    cacher.cache();
    // should simply skip
    assertFalse(cacher.wasStored);

    cacher._input = new StringInputStream("test stream");
    cacher._headers = new CIProperties();
    cacher.cache();
    assertTrue(cacher.wasStored);
  }

  public void testFileCache() throws IOException {
    cacher._input = new StringInputStream("test content");
    CIProperties props = new CIProperties();
    props.setProperty("test1", "value1");
    cacher._headers = props;
    cacher.cache();

    CachedUrl url = new BaseCachedUrl(mau, TEST_URL);
    InputStream is = url.getUnfilteredInputStream();
    assertReaderMatchesString("test content", new InputStreamReader(is));

    props = url.getProperties();
    assertEquals("value1", props.getProperty("test1"));
  }

  public void testCheckConnection() {
    MockLockssUrlConnection  conn = new MockLockssUrlConnection();
    conn.setResponseCode(200);
    conn.setResponseMessage("OK");
    try {
      cacher.checkConnectException(conn);
    }
    catch (IOException ex) {
      assertTrue("Unexpected exception", ex == null);
    }

    conn.setResponseCode(401);
    conn.setResponseMessage("Unauthorized");
    try {
      cacher.checkConnectException(conn);
    }
    catch (IOException ex) {
      assertTrue("Expected exception", ex != null);
    }

  }

  // The following tests do not use MyMockBaseUrlCacher, so test more of
  // BaseUrlCacher.  Mostly they test its behavior wrt the connection.
  // MockConnectionMockBaseUrlCacher is used to create a mock connection.

  MyMockLockssUrlConnection makeConn(int respCode, String respMessage,
				     String redirectTo) {
    return makeConn(respCode, respMessage, redirectTo, null);
  }

  MyMockLockssUrlConnection makeConn(int respCode, String respMessage,
				     String redirectTo, String inputStream) {
    MyMockLockssUrlConnection mconn = new MyMockLockssUrlConnection();
    mconn.setResponseCode(respCode);
    mconn.setResponseMessage(respMessage);
    if (redirectTo != null) {
      mconn.setResponseHeader("location", redirectTo);
    }
    mconn.setResponseContentType("");
    mconn.setResponseContentEncoding("");
    mconn.setResponseDate(0);
    if (inputStream != null) {
      mconn.setResponseInputStream(new StringInputStream(inputStream));
    }
    return mconn;
  }

  public void testGetUncachedProperties() throws IOException {
    TimeBase.setSimulated(555666);
    MockConnectionMockBaseUrlCacher muc =
      new MockConnectionMockBaseUrlCacher(mau, TEST_URL);
    MyMockLockssUrlConnection mconn = makeConn(200, "", null, "foo");
    muc.addConnection(mconn);
    muc.getUncachedInputStream();
    Properties props = muc.getUncachedProperties();
    assertEquals("", props.get(CachedUrl.PROPERTY_CONTENT_TYPE));
    assertEquals("555666", props.get(CachedUrl.PROPERTY_FETCH_TIME));
  }

  public void testGetUncachedPropertiesNull() throws IOException {
    TimeBase.setSimulated(555666);
    MockConnectionMockBaseUrlCacher muc =
      new MockConnectionMockBaseUrlCacher(mau, TEST_URL);
    MyMockLockssUrlConnection mconn = makeConn(200, "", null, "foo");
    mconn.setResponseContentType(null);
    muc.addConnection(mconn);
    muc.getUncachedInputStream();
    Properties props = muc.getUncachedProperties();
    assertEquals(null, props.get(CachedUrl.PROPERTY_CONTENT_TYPE));
    assertEquals("555666", props.get(CachedUrl.PROPERTY_FETCH_TIME));
  }

  public void testMalformedUrlException() throws IOException {
    MockConnectionMockBaseUrlCacher muc =
      new MockConnectionMockBaseUrlCacher(mau, TEST_URL);
    ThrowingMockLockssUrlConnection mconn =
      new ThrowingMockLockssUrlConnection(new MalformedURLException());
    muc.addConnection(mconn);
    try {
      muc.cache();
      fail("Should have thrown");
    } catch (CacheException.MalformedURLException ex) {
    }
  }

  public void testNoProxy() throws Exception {
    MockConnectionMockBaseUrlCacher muc =
      new MockConnectionMockBaseUrlCacher(mau, TEST_URL);
    MyMockLockssUrlConnection mconn = makeConn(200, "", null, "foo");
    muc.addConnection(mconn);
    muc.cache();
    assertEquals(TEST_URL, mconn.getURL());
    assertNull(mconn.proxyHost);
  }

  public void testProxy() throws Exception {
    MockConnectionMockBaseUrlCacher muc =
      new MockConnectionMockBaseUrlCacher(mau, TEST_URL);
    MyMockLockssUrlConnection mconn = makeConn(200, "", null, "foo");
    muc.addConnection(mconn);
    muc.setProxy("phost", 126);
    muc.cache();
    assertEquals(TEST_URL, mconn.getURL());
    assertEquals("phost", mconn.proxyHost);
    assertEquals(126, mconn.proxyPort);
  }

  public void testSetReqProp() throws Exception {
    MockConnectionMockBaseUrlCacher muc =
      new MockConnectionMockBaseUrlCacher(mau, TEST_URL);
    MyMockLockssUrlConnection mconn = makeConn(200, "", null, "foo");
    muc.addConnection(mconn);
    muc.setRequestProperty("foo-bar", "47");
    muc.cache();
    assertEquals(TEST_URL, mconn.getURL());
    assertEquals("47", mconn.getRequestProperty("foo-bar"));
  }

  // Shouldn't generate if-modified-since header because no existing content
  public void testIfModifiedConnectionNoContent() throws Exception {
    MockConnectionMockBaseUrlCacher muc =
      new MockConnectionMockBaseUrlCacher(mau, TEST_URL);
    MockLockssUrlConnection mconn = makeConn(200, "", null, "foo");
    muc.addConnection(mconn);
    muc.setForceRefetch(false);
    muc.cache();
    assertEquals(TEST_URL, mconn.getURL());
    assertNull("444332", mconn.getRequestProperty("if-modified-since"));
  }

  MockConnectionMockBaseUrlCacher makeMucWithContent() {
    MockConnectionMockBaseUrlCacher muc =
      new MockConnectionMockBaseUrlCacher(mau, TEST_URL);
    CIProperties cuprops = new CIProperties();

    cuprops.setProperty(CachedUrl.PROPERTY_LAST_MODIFIED,
			GMT_DATE_FORMAT.format(new Date(12345000)));

    mau.addUrl(TEST_URL, true, true, cuprops);
    return muc;
  }

  // Should generate if-modified-since header
  public void testIfModifiedConnection() throws Exception {
    MockConnectionMockBaseUrlCacher muc = makeMucWithContent();
    MockLockssUrlConnection mconn = makeConn(200, "", null, "foo");
    muc.addConnection(mconn);
    muc.setForceRefetch(false);
    muc.cache();
    assertEquals(TEST_URL, mconn.getURL());
    assertEquals("Thu, 01 Jan 1970 03:25:45 GMT",
		 mconn.getRequestProperty("if-modified-since"));
  }

  // Shouldn't generate if-modified-since header because forceRefetch true
  public void testForcedConnection() throws Exception {
    MockConnectionMockBaseUrlCacher muc = makeMucWithContent();
    MockLockssUrlConnection mconn = makeConn(200, "", null, "foo");
    muc.addConnection(mconn);
//     muc.setForceRefetch(true);
    BitSet bs = new BitSet();
    bs.set(REFETCH_FLAG);
    muc.setFetchFlags(bs);
    muc.cache();
    assertEquals(TEST_URL, mconn.getURL());
    assertNull(mconn.getRequestProperty("if-modified-since"));
    // check the CU contents and properties
    assertCuContents(TEST_URL, "foo");
    assertCuProperty(TEST_URL, null, CachedUrl.PROPERTY_REDIRECTED_TO);
    assertCuProperty(TEST_URL, null, CachedUrl.PROPERTY_CONTENT_URL);
  }

  // Should throw exception derived from response code
  public void testConnectionError() throws Exception {
    MockConnectionMockBaseUrlCacher muc =
      new MockConnectionMockBaseUrlCacher(mau, TEST_URL);
    MockLockssUrlConnection mconn = makeConn(404, "Not fond", null);
    muc.addConnection(mconn);
    try {
      InputStream is = muc.getUncachedInputStream();
      fail("Should have thrown ExpectedNoRetryException");
    } catch (CacheException.ExpectedNoRetryException e) {
      assertEquals("404 Not fond", e.getMessage());
    }
  }

  //the url connection may throw a MalformedUrlException
  public void testMalformedUrlError() throws Exception {
    MockConnectionMockBaseUrlCacher muc =
      new MockConnectionMockBaseUrlCacher(mau, TEST_URL);
    MockLockssUrlConnection mconn = makeConn(404, "Not fond", null);
    muc.addConnection(mconn);
    try {
      InputStream is = muc.getUncachedInputStream();
      fail("Should have thrown ExpectedNoRetryException");
    } catch (CacheException.ExpectedNoRetryException e) {
      assertEquals("404 Not fond", e.getMessage());
    }
  }

  // Shouldn't follow redirect because told not to.
  public void testNoRedirect() throws Exception {
    String redTo = "http://somewhere.else/foo";
    MockConnectionMockBaseUrlCacher muc =
      new MockConnectionMockBaseUrlCacher(mau, TEST_URL);
    muc.addConnection(makeConn(301, "Moved to Spain", redTo));
    muc.addConnection(makeConn(301, "Moved to tears",
			       "http://elsewhere.org/foo"));
    muc.setRedirectScheme(UrlCacher.REDIRECT_SCHEME_DONT_FOLLOW);
    try {
      InputStream is = muc.getUncachedInputStream();
      fail("Should have thrown RetryNewUrlException");
    } catch (CacheException.NoRetryNewUrlException e) {
      assertEquals("301 Moved to Spain", e.getMessage());
      CIProperties p = muc.getUncachedProperties();
      // In this case the new location should be in the UrlCacher's
      // properties, even though no CachedUrl was written
      assertEquals(redTo, p.getProperty("location"));
    }
  }

  // Can't test REDIRECT_SCHEME_FOLLOW because MockLockssUrlConnection
  // doesn't do redirection.

  // Should follow redirection to URL in crawl spec
  public void testRedirectInSpec() throws Exception {
    String redTo = "http://somewhere.else/foo";
    MockConnectionMockBaseUrlCacher muc =
        new MockConnectionMockBaseUrlCacher(mau, TEST_URL);
    PermissionMap map = new PermissionMap();
    map.putStatus(TEST_URL, PermissionMap.PERMISSION_OK);
    map.putStatus(redTo, PermissionMap.PERMISSION_OK);
    muc.setPermissionMapSource(new MockPermissionMapSource(map));
    muc.addConnection(makeConn(301, "Moved to Spain", redTo));
    muc.addConnection(makeConn(200, "Ok", null, "bar"));
    muc.setRedirectScheme(UrlCacher.REDIRECT_SCHEME_STORE_ALL_IN_SPEC);
    mau.addUrlToBeCached(redTo);
    InputStream is = muc.getUncachedInputStream();
    CIProperties p = muc.getUncachedProperties();
    assertNull(p.getProperty("location"));
    assertEquals(redTo, p.getProperty(CachedUrl.PROPERTY_REDIRECTED_TO));
    assertEquals(redTo, p.getProperty(CachedUrl.PROPERTY_CONTENT_URL));
    assertReaderMatchesString("bar", new InputStreamReader(is));
    // Make sure the UrlCacher still has the original URL
    assertEquals(TEST_URL, muc.getUrl());
  }

  // Should not follow redirection to URL not in crawl spec
  public void testRedirectNotInSpec() throws Exception {
    String redTo = "http://somewhere.else/foo";
    MockConnectionMockBaseUrlCacher muc =
      new MockConnectionMockBaseUrlCacher(mau, TEST_URL);
    muc.addConnection(makeConn(301, "Moved to Fresno", redTo));
    muc.addConnection(makeConn(200, "Ok", null, "bar"));
    muc.setRedirectScheme(UrlCacher.REDIRECT_SCHEME_STORE_ALL_IN_SPEC);
    try {
      InputStream is = muc.getUncachedInputStream();
      fail("Should have thrown RetryNewUrlException");
    } catch (CacheException.NoRetryNewUrlException e) {
      assertEquals("301 Moved to Fresno", e.getMessage());
      CIProperties p = muc.getUncachedProperties();
      assertEquals(redTo, p.getProperty("location"));
    }
  }

  // Should follow redirection to URL in crawl spec
  public void testRedirectChain() throws Exception {
    String redTo1 = "http://2.2/a";
    String redTo2 = "http://2.2/b";
    String redTo = "http://somewhere.else/foo";
    MockConnectionMockBaseUrlCacher muc =
        new MockConnectionMockBaseUrlCacher(mau, TEST_URL);
    PermissionMap map = new PermissionMap();
    map.putStatus(TEST_URL, PermissionMap.PERMISSION_OK);
    map.putStatus(redTo1, PermissionMap.PERMISSION_OK);
    map.putStatus(redTo, PermissionMap.PERMISSION_OK);
    muc.setPermissionMapSource(new MockPermissionMapSource(map));
    muc.addConnection(makeConn(301, "Moved to Spain", redTo1));
    muc.addConnection(makeConn(301, "Moved to Spain", redTo2));
    muc.addConnection(makeConn(301, "Moved to Spain", redTo));
    muc.addConnection(makeConn(200, "Ok", null, "bar"));
    muc.setRedirectScheme(UrlCacher.REDIRECT_SCHEME_STORE_ALL_IN_SPEC);
    mau.addUrlToBeCached(redTo1);
    mau.addUrlToBeCached(redTo2);
    mau.addUrlToBeCached(redTo);
    InputStream is = muc.getUncachedInputStream();
    CIProperties p = muc.getUncachedProperties();
    assertNull(p.getProperty("location"));
    assertEquals(redTo1, p.getProperty(CachedUrl.PROPERTY_REDIRECTED_TO));
    assertEquals(redTo, p.getProperty(CachedUrl.PROPERTY_CONTENT_URL));
    assertReaderMatchesString("bar", new InputStreamReader(is));
  }

  // Should throw because of max redirections
  public void testRedirectChainMax() throws Exception {
    String redTo = "http://foo.bar/foo";
    MockConnectionMockBaseUrlCacher muc =
      new MockConnectionMockBaseUrlCacher(mau, TEST_URL);
    for (int ix = 0; ix < (1+BaseUrlCacher.MAX_REDIRECTS); ix++) {
      muc.addConnection(makeConn(301, "Moved to Spain", redTo));
    }
    muc.addConnection(makeConn(200, "Ok", null, "bar"));
    muc.setRedirectScheme(UrlCacher.REDIRECT_SCHEME_STORE_ALL_IN_SPEC);
    mau.addUrlToBeCached(redTo);
    try {
      InputStream is = muc.getUncachedInputStream();
      fail("Should have thrown NoRetryNewUrlException");
    } catch (CacheException.NoRetryNewUrlException e) {
      assertEquals("301 Moved to Spain", e.getMessage());
      CIProperties p = muc.getUncachedProperties();
      assertEquals(redTo, p.getProperty("location"));
    }
  }

  // Should follow redirection to URL on same host
  public void testRedirectInSpecOnHost() throws Exception {
    String redTo = "http://www.example.com/foo";
    MockConnectionMockBaseUrlCacher muc =
      new MockConnectionMockBaseUrlCacher(mau, TEST_URL);
    muc.addConnection(makeConn(301, "Moved to Spain", redTo));
    muc.addConnection(makeConn(200, "Ok", null, "bar"));
    muc.setRedirectScheme(UrlCacher.REDIRECT_SCHEME_FOLLOW_IN_SPEC_ON_HOST);
    mau.addUrlToBeCached(redTo);
    InputStream is = muc.getUncachedInputStream();
    CIProperties p = muc.getUncachedProperties();
    assertNull(p.getProperty("location"));
    assertEquals(redTo, p.getProperty(CachedUrl.PROPERTY_REDIRECTED_TO));
    assertEquals(redTo, p.getProperty(CachedUrl.PROPERTY_CONTENT_URL));
    assertReaderMatchesString("bar", new InputStreamReader(is));
    // Make sure the UrlCacher still has the original URL
    assertEquals(TEST_URL, muc.getUrl());
  }

  // Should not follow redirection to URL on different host
  public void testRedirectInSpecNotOnHost() throws Exception {
    String redTo = "http://somewhere.else/foo";
    MockConnectionMockBaseUrlCacher muc =
      new MockConnectionMockBaseUrlCacher(mau, TEST_URL);
    muc.addConnection(makeConn(301, "Moved to Spain", redTo));
    muc.addConnection(makeConn(200, "Ok", null, "bar"));
    muc.setRedirectScheme(UrlCacher.REDIRECT_SCHEME_FOLLOW_IN_SPEC_ON_HOST);
    mau.addUrlToBeCached(redTo);
    try {
      InputStream is = muc.getUncachedInputStream();
      fail("Should have thrown NoRetryNewUrlException");
    } catch (CacheException.NoRetryNewUrlException e) {
      assertEquals("301 Moved to Spain", e.getMessage());
      CIProperties p = muc.getUncachedProperties();
      assertEquals(redTo, p.getProperty("location"));
    }
  }

  // Should follow redirection to URL on same host
  public void testRedirectOnHost() throws Exception {
    String redTo = "http://www.example.com/foo";
    MockConnectionMockBaseUrlCacher muc =
      new MockConnectionMockBaseUrlCacher(mau, TEST_URL);
    muc.addConnection(makeConn(301, "Moved to Spain", redTo));
    muc.addConnection(makeConn(200, "Ok", null, "bar"));
    muc.setRedirectScheme(UrlCacher.REDIRECT_SCHEME_FOLLOW_ON_HOST);
    InputStream is = muc.getUncachedInputStream();
    CIProperties p = muc.getUncachedProperties();
    assertNull(p.getProperty("location"));
    assertEquals(redTo, p.getProperty(CachedUrl.PROPERTY_REDIRECTED_TO));
    assertEquals(redTo, p.getProperty(CachedUrl.PROPERTY_CONTENT_URL));
    assertReaderMatchesString("bar", new InputStreamReader(is));
    // Make sure the UrlCacher still has the original URL
    assertEquals(TEST_URL, muc.getUrl());
  }

  // Should not follow redirection to URL on different host
  public void testRedirectNotOnHost() throws Exception {
    String redTo = "http://somewhere.else/foo";
    MockConnectionMockBaseUrlCacher muc =
      new MockConnectionMockBaseUrlCacher(mau, TEST_URL);
    muc.addConnection(makeConn(301, "Moved to Spain", redTo));
    muc.addConnection(makeConn(200, "Ok", null, "bar"));
    muc.setRedirectScheme(UrlCacher.REDIRECT_SCHEME_FOLLOW_ON_HOST);
    try {
      InputStream is = muc.getUncachedInputStream();
      fail("Should have thrown NoRetryNewUrlException");
    } catch (CacheException.NoRetryNewUrlException e) {
      assertEquals("301 Moved to Spain", e.getMessage());
      CIProperties p = muc.getUncachedProperties();
      assertEquals(redTo, p.getProperty("location"));
    }
  }

  public void testRedirectWritesBoth() throws Exception {
    mau.returnRealCachedUrl = true;
    String redTo = "http://somewhere.else/foo";
    MockConnectionMockBaseUrlCacher muc =
        new MockConnectionMockBaseUrlCacher(mau, TEST_URL);
    PermissionMap map = new PermissionMap();
    map.putStatus(TEST_URL, PermissionMap.PERMISSION_OK);
    map.putStatus(redTo, PermissionMap.PERMISSION_OK);
    muc.setPermissionMapSource(new MockPermissionMapSource(map));
    muc.addConnection(makeConn(301, "Moved to Spain", redTo));
    muc.addConnection(makeConn(200, "Ok", null, "bar"));
    muc.setRedirectScheme(UrlCacher.REDIRECT_SCHEME_STORE_ALL_IN_SPEC);
    mau.addUrlToBeCached(redTo);
    muc.cache();
    CIProperties p = muc.getUncachedProperties();
    assertNull(p.getProperty("location"));
    assertEquals(redTo, p.getProperty(CachedUrl.PROPERTY_REDIRECTED_TO));
    assertEquals(redTo, p.getProperty(CachedUrl.PROPERTY_CONTENT_URL));

    assertCuContents(TEST_URL, "bar");
    assertCuProperty(TEST_URL, redTo, CachedUrl.PROPERTY_REDIRECTED_TO);
    assertCuProperty(TEST_URL, redTo, CachedUrl.PROPERTY_CONTENT_URL);
    // final CU should have same contents
    assertCuContents(redTo, "bar");
    // but should *not* have a redirected-to property
    assertCuProperty(redTo, null, CachedUrl.PROPERTY_REDIRECTED_TO);
    // nor a content-url property
    assertCuProperty(redTo, null, CachedUrl.PROPERTY_CONTENT_URL);
  }

  public void testRedirectWritesAll() throws Exception {
    String content = "oft redirected content";
    mau.returnRealCachedUrl = true;
    String redTo1 = "http://somewhere.else/foo";
    String redTo2 = "http://somewhere.else/bar/x.html";
    String redTo3 = "http://somewhere.else/bar/y.html";
    MockConnectionMockBaseUrlCacher muc =
        new MockConnectionMockBaseUrlCacher(mau, TEST_URL);
    PermissionMap map = new PermissionMap();
    map.putStatus(TEST_URL, PermissionMap.PERMISSION_OK);
    map.putStatus(redTo1, PermissionMap.PERMISSION_OK);
    muc.setPermissionMapSource(new MockPermissionMapSource(map));
    muc.addConnection(makeConn(301, "Moved to Spain", redTo1));
    muc.addConnection(makeConn(301, "Moved to Spain", redTo2));
    muc.addConnection(makeConn(301, "Moved to Spain", redTo3));
    muc.addConnection(makeConn(200, "Ok", null, content));
    muc.setRedirectScheme(UrlCacher.REDIRECT_SCHEME_STORE_ALL_IN_SPEC);
    mau.addUrlToBeCached(redTo1);
    mau.addUrlToBeCached(redTo2);
    mau.addUrlToBeCached(redTo3);
    muc.cache();
    CIProperties p = muc.getUncachedProperties();
    assertNull(p.getProperty("location"));
    assertEquals(redTo1, p.getProperty(CachedUrl.PROPERTY_REDIRECTED_TO));

    // verify all have the correct contents, and all but the last have a
    // redirected-to header
    assertCuContents(TEST_URL, content);
    assertCuContents(redTo1, content);
    assertCuContents(redTo2, content);
    assertCuContents(redTo3, content);
    assertCuProperty(TEST_URL, redTo1, CachedUrl.PROPERTY_REDIRECTED_TO);
    assertCuProperty(redTo1, redTo2, CachedUrl.PROPERTY_REDIRECTED_TO);
    assertCuProperty(redTo2, redTo3, CachedUrl.PROPERTY_REDIRECTED_TO);
    assertCuProperty(redTo3, null, CachedUrl.PROPERTY_REDIRECTED_TO);

    assertCuProperty(TEST_URL, redTo3, CachedUrl.PROPERTY_CONTENT_URL);
    assertCuProperty(redTo1, redTo3, CachedUrl.PROPERTY_CONTENT_URL);
    assertCuProperty(redTo2, redTo3, CachedUrl.PROPERTY_CONTENT_URL);
    assertCuProperty(redTo3, null, CachedUrl.PROPERTY_CONTENT_URL);
  }

  public void testRedirectWritesNoTemp() throws Exception {
    String content = "oft redirected content";
    mau.returnRealCachedUrl = true;
    String redTo1 = "http://somewhere.else/foo";
    String redTo2 = "http://somewhere.else/bar/x.html";
    String redTo3 = "http://somewhere.else/bar/y.html";
    MockConnectionMockBaseUrlCacher muc =
        new MockConnectionMockBaseUrlCacher(mau, TEST_URL);
    PermissionMap map = new PermissionMap();
    map.putStatus(TEST_URL, PermissionMap.PERMISSION_OK);
    map.putStatus(redTo1, PermissionMap.PERMISSION_OK);
    muc.setPermissionMapSource(new MockPermissionMapSource(map));
    muc.addConnection(makeConn(302, "Moved to Spain", redTo1));
    muc.addConnection(makeConn(303, "Moved to Spain", redTo2));
    muc.addConnection(makeConn(307, "Moved to Spain", redTo3));
    muc.addConnection(makeConn(200, "Ok", null, content));
    muc.setRedirectScheme(UrlCacher.REDIRECT_SCHEME_STORE_ALL_IN_SPEC);
    mau.addUrlToBeCached(redTo1);
    mau.addUrlToBeCached(redTo2);
    mau.addUrlToBeCached(redTo3);
    muc.cache();
    CIProperties p = muc.getUncachedProperties();
    assertNull(p.getProperty("location"));
    //XXX now we're not tracking the interveaning URLs
    assertEquals(redTo3, p.getProperty(CachedUrl.PROPERTY_REDIRECTED_TO));

    // verify all have the correct contents, and all but the last have a
    // redirected-to header
    assertCuContents(TEST_URL, content);
    assertCuNoContent(redTo1);
    assertCuNoContent(redTo2);
    assertCuNoContent(redTo3);

    assertCuProperty(TEST_URL, redTo3, CachedUrl.PROPERTY_REDIRECTED_TO);

    assertCuProperty(TEST_URL, redTo3, CachedUrl.PROPERTY_CONTENT_URL);
  }

  public void testSimpleDirRedirect() throws Exception {
    String content = "oft redirected content";
    mau.returnRealCachedUrl = true;
    String url = "http://a.b/bar";
    String redTo = url + "/";
    MockConnectionMockBaseUrlCacher muc =
      new MockConnectionMockBaseUrlCacher(mau, url);
    muc.addConnection(makeConn(301, "Moved to Spain", redTo));
    muc.addConnection(makeConn(200, "Ok", null, content));
    muc.setRedirectScheme(UrlCacher.REDIRECT_SCHEME_STORE_ALL_IN_SPEC);
    mau.addUrlToBeCached(redTo);
    muc.cache();
    CIProperties p = muc.getUncachedProperties();
    assertNull(p.getProperty("location"));
    assertEquals(redTo, p.getProperty(CachedUrl.PROPERTY_REDIRECTED_TO));

    // verify the correct contents and redirected-to header
    // (these are the same node)
    assertCuContents(url, content);
    assertCuContents(redTo, content);
    assertCuProperty(url, redTo, CachedUrl.PROPERTY_REDIRECTED_TO);
    assertCuProperty(redTo, redTo, CachedUrl.PROPERTY_REDIRECTED_TO);
    assertCuProperty(url, redTo, CachedUrl.PROPERTY_CONTENT_URL);
    assertCuProperty(redTo, redTo, CachedUrl.PROPERTY_CONTENT_URL);
  }

  public void testDirRedirect() throws Exception {
    PermissionMap map = new PermissionMap();
    String content = "oft redirected content";
    mau.returnRealCachedUrl = true;
    String url = "http://a.b/bar";
    String redTo1 = "http://somewhere.else/foo";
    String redTo2 = "http://somewhere.else/foo/";
    MockConnectionMockBaseUrlCacher muc =
      new MockConnectionMockBaseUrlCacher(mau, url);
    map.putStatus(url, PermissionMap.PERMISSION_OK);
    map.putStatus(redTo1, PermissionMap.PERMISSION_OK);
    muc.setPermissionMapSource(new MockPermissionMapSource(map));
    muc.addConnection(makeConn(301, "Moved to Spain", redTo1));
    muc.addConnection(makeConn(301, "Moved to Spain", redTo2));
    muc.addConnection(makeConn(200, "Ok", null, content));
    muc.setRedirectScheme(UrlCacher.REDIRECT_SCHEME_STORE_ALL_IN_SPEC);
    mau.addUrlToBeCached(redTo1);
    mau.addUrlToBeCached(redTo2);
    muc.cache();
    CIProperties p = muc.getUncachedProperties();
    assertNull(p.getProperty("location"));
    assertEquals(redTo1, p.getProperty(CachedUrl.PROPERTY_REDIRECTED_TO));

    // verify all have the correct contents, and all but the last have
    // redirected-to and content-url headers
    assertCuContents(url, content);
    assertCuContents(redTo1, content);
    assertCuContents(redTo2, content);
    assertCuProperty(url, redTo1, CachedUrl.PROPERTY_REDIRECTED_TO);
    // these are the same node
    assertCuProperty(redTo1, redTo2, CachedUrl.PROPERTY_REDIRECTED_TO);
    assertCuProperty(redTo2, redTo2, CachedUrl.PROPERTY_REDIRECTED_TO);

    assertCuProperty(url, redTo2, CachedUrl.PROPERTY_CONTENT_URL);
    assertCuProperty(redTo1, redTo2, CachedUrl.PROPERTY_CONTENT_URL);
    assertCuProperty(redTo2, redTo2, CachedUrl.PROPERTY_CONTENT_URL);
  }


  public void testCacheLPC() throws IOException {
    MyMockLoginPageChecker loginPageChecker =
      new MyMockLoginPageChecker(false);
    mau.setCrawlSpec(new SpiderCrawlSpec(ListUtil.list("http://example.com"),
					 ListUtil.list("http://example.com"),
					 null, 99,
					 new ArrayList(), loginPageChecker));
    
    cacher._input = new StringInputStream("test stream");
    cacher._headers = new CIProperties();
    // should cache
    assertEquals(UrlCacher.CACHE_RESULT_FETCHED, cacher.cache());

    assertTrue(loginPageChecker.wasCalled());
  }

  public void testCacheLPCLoginPage() throws IOException {
    MyMockLoginPageChecker loginPageChecker =
      new MyMockLoginPageChecker(true);
    mau.setCrawlSpec(new SpiderCrawlSpec(ListUtil.list("http://example.com"),
					 ListUtil.list("http://example.com"),
					 null, 99,
					 new ArrayList(), loginPageChecker));
    
    cacher._input = new StringInputStream("test stream");
    cacher._headers = new CIProperties();
    // should cache
    try {
      cacher.cache();
      fail("Should have thrown a CacheException.UnretryableException");
    } catch (CacheException.PermissionException ex) {
    }
  }

  class MyMockLoginPageChecker implements LoginPageChecker {
    private boolean wasCalled = false;
    private boolean isLoginPage;

    MyMockLoginPageChecker(boolean isLoginPage) {
      this.isLoginPage = isLoginPage;
    }

    public boolean isLoginPage(Properties props, Reader reader) {
      wasCalled = true;
      return this.isLoginPage;
    }

    public boolean wasCalled() {
      return this.wasCalled;
    }
  }


  void assertCuContents(String url, String contents) throws IOException {
    CachedUrl cu = new BaseCachedUrl(mau, url);
    InputStream is = cu.getUnfilteredInputStream();
    assertReaderMatchesString(contents, new InputStreamReader(is));
  }
  /**
   * Assert that this url has no content
   */
  void assertCuNoContent(String url) throws IOException {
    CachedUrl cu = new BaseCachedUrl(mau, url);
    assertFalse(cu.hasContent());
  }

  void assertCuProperty(String url, String expected, String key) {
    CachedUrl cu = new BaseCachedUrl(mau, url);
    CIProperties props = cu.getProperties();
    assertEquals(expected, props.getProperty(key));
  }

  void assertCuUrl(String url, String expected) {
    CachedUrl cu = new BaseCachedUrl(mau, url);
    assertEquals(expected, cu.getUrl());
  }

  private class MyMockPlugin extends MockPlugin {
  }

  // Allows a list of preconfigured MockLockssUrlConnection instances to be
  // used for successive redirect fetches.
  private class MockConnectionMockBaseUrlCacher extends BaseUrlCacher {
    List connections = new ArrayList();

    public MockConnectionMockBaseUrlCacher(ArchivalUnit owner, String url) {
      super(owner, url);
    }

    void addConnection(MockLockssUrlConnection conn) {
      connections.add(conn);
    }

    protected LockssUrlConnection makeConnection(String url,
						 LockssUrlConnectionPool pool)
	throws IOException {
      if (connections != null && !connections.isEmpty()) {
	MockLockssUrlConnection mconn =
	  (MockLockssUrlConnection)connections.remove(0);
	mconn.setURL(url);
	return mconn;
      } else {
	return new MockLockssUrlConnection();
      }
    }

  }

  // Mock BaseUrlCacher that fakes the connection
  private class MyMockBaseUrlCacher extends BaseUrlCacher {
    InputStream _input = null;
    CIProperties _headers;
    boolean wasStored = false;
    int getUncachedPropertiesCount = 0;
    int getUncachedInputStreamCount = 0;
    BaseArchivalUnit.ParamHandlerMap pMap;

    public MyMockBaseUrlCacher(ArchivalUnit owner, String url) {
      super(owner, url);
    }

    protected BaseArchivalUnit.ParamHandlerMap getParamMap() {
      return pMap;
    }

    public void setParamMap(BaseArchivalUnit.ParamHandlerMap pMap) {
      this.pMap = pMap;
    }

    public InputStream getUncachedInputStream(String lastModified) {
      // simple version which returns null if shouldn't fetch
//       if (lastCached < TimeBase.nowMs()) {
      getUncachedInputStreamCount++;
      long last = -1;
      if (lastModified != null) {
	try {
	  last = BaseUrlCacher.GMT_DATE_FORMAT.parse(lastModified).getTime();
	} catch (ParseException e) {
	}
      }
      if (last < TimeBase.nowMs()) {
	return _input;
      } else {
	return null;
      }
    }

    public CIProperties getUncachedProperties() {
      getUncachedPropertiesCount++;
      return _headers;
    }

    public void storeContent(InputStream input, CIProperties headers)
	throws IOException {
      super.storeContent(input, headers);
      wasStored = true;
    }

  }

  private class MyMockArchivalUnit extends MockArchivalUnit {
    boolean returnRealCachedUrl = false;

    public CachedUrlSet makeCachedUrlSet(CachedUrlSetSpec cuss) {
      return new BaseCachedUrlSet(this, cuss);
    }

    public CachedUrl makeCachedUrl(String url) {
      if (returnRealCachedUrl) {
        return new BaseCachedUrl(this, url);
      } else {
        return super.makeCachedUrl(url);
      }
    }

    public void pauseBeforeFetch() {
      pauseBeforeFetchCounter++;
    }
  }

  private class MyMockLockssUrlConnection extends MockLockssUrlConnection {
    String proxyHost = null;
    int proxyPort = -1;

    public void setProxy(String host, int port) {
      proxyHost = host;
      proxyPort = port;
    }
  }

  private class ThrowingMockLockssUrlConnection
    extends MockLockssUrlConnection {
    IOException ex;

    public ThrowingMockLockssUrlConnection(IOException ex) {
      this.ex = ex;
    }

    public void execute() throws IOException {
      throw ex;
    }
  }

  public static void main(String[] argv) {
    String[] testCaseList = { TestBaseUrlCacher.class.getName()};
    junit.swingui.TestRunner.main(testCaseList);
  }

}
