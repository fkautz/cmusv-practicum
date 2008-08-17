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
package org.lockss.plugin.definable;

import java.io.*;
import java.net.*;
import java.util.*;

import org.lockss.app.LockssDaemon;
import org.lockss.config.*;
import org.lockss.daemon.*;
import org.lockss.plugin.*;
import org.lockss.plugin.wrapper.*;
import org.lockss.plugin.base.*;
import org.lockss.test.*;
import org.lockss.extractor.*;
import org.lockss.rewriter.*;
import org.lockss.util.*;
import org.lockss.util.urlconn.*;

/**
 * <p>TestConfigurablePlugin: test case for ConfigurablePlugin</p>
 * @author Claire Griffin
 * @version 1.0
 */

public class TestDefinablePlugin extends LockssTestCase {
  static final String DEFAULT_PLUGIN_VERSION = "1";

  private MyDefinablePlugin definablePlugin = null;
  ExternalizableMap defMap;

  protected void setUp() throws Exception {
    super.setUp();
    definablePlugin = new MyDefinablePlugin();
    defMap = new ExternalizableMap();
    definablePlugin.initPlugin(getMockLockssDaemon(), defMap);
  }

  protected void tearDown() throws Exception {
    definablePlugin = null;
    super.tearDown();
  }

  public void testInitMimeMapDefault() throws Exception {
    // 2nd plugin to verify changes to 1st don't effect global mime map
    MyDefinablePlugin p2 = new MyDefinablePlugin();
    p2.initPlugin(getMockLockssDaemon(), new ExternalizableMap());
    MimeTypeInfo mti;

    mti = definablePlugin.getMimeTypeInfo("text/html");
    assertTrue(mti.getLinkExtractorFactory()
	       instanceof GoslingHtmlLinkExtractor.Factory);
    assertTrue(""+mti.getLinkRewriterFactory().getClass(),
	       mti.getLinkRewriterFactory() instanceof
	       NodeFilterHtmlLinkRewriterFactory);
    mti = definablePlugin.getMimeTypeInfo("text/css");
    assertTrue(mti.getLinkExtractorFactory()
	       instanceof CssLinkExtractor.Factory);
    assertNull(mti.getLinkRewriterFactory()); // XXX 
    mti = definablePlugin.getMimeTypeInfo("application/pdf");
    assertNull(mti.getFilterFactory());
    assertNull(mti.getFetchRateLimiter());
    assertNull(mti.getLinkRewriterFactory()); // XXX 

    defMap.putString(  ("application/pdf"
			+ DefinableArchivalUnit.SUFFIX_FILTER_FACTORY),
		     "org.lockss.test.MockFilterFactory");
    defMap.putString(  ("text/html"
			+ DefinableArchivalUnit.SUFFIX_LINK_EXTRACTOR_FACTORY),
		     "org.lockss.test.MockLinkExtractorFactory");
    defMap.putString(  ("text/html"
			+ DefinableArchivalUnit.SUFFIX_LINK_REWRITER_FACTORY),
		     "org.lockss.test.MockLinkRewriterFactory");
    defMap.putString(  ("application/pdf"
			+ DefinableArchivalUnit.SUFFIX_FETCH_RATE_LIMITER),
		     "1/30s");
    definablePlugin.initPlugin(getMockLockssDaemon(), defMap);

    mti = definablePlugin.getMimeTypeInfo("text/html");
    System.err.println("fact: " + mti.getLinkExtractorFactory());
    assertTrue(mti.getLinkExtractorFactory()
	       instanceof LinkExtractorFactoryWrapper);
    assertTrue(WrapperUtil.unwrap(mti.getLinkExtractorFactory())
	       instanceof MockLinkExtractorFactory);
    System.err.println("fact: " + mti.getLinkRewriterFactory());
    assertTrue(mti.getLinkRewriterFactory()
	       instanceof LinkRewriterFactoryWrapper);
    assertTrue(WrapperUtil.unwrap(mti.getLinkRewriterFactory())
	       instanceof MockLinkRewriterFactory);
    assertNull(mti.getFetchRateLimiter());
    mti = definablePlugin.getMimeTypeInfo("text/css");
    assertTrue(mti.getLinkExtractorFactory()
	       instanceof CssLinkExtractor.Factory);
    assertNull(mti.getFetchRateLimiter());
    mti = definablePlugin.getMimeTypeInfo("application/pdf");
    assertTrue(mti.getFilterFactory()
	       instanceof FilterFactoryWrapper);
    assertTrue(WrapperUtil.unwrap(mti.getFilterFactory())
	       instanceof MockFilterFactory);
    assertEquals("1/30s", mti.getFetchRateLimiter().getRate());

    // verify 2nd plugin still has mime defaults
    mti = p2.getMimeTypeInfo("text/html");
    assertTrue(mti.getLinkExtractorFactory()
	       instanceof GoslingHtmlLinkExtractor.Factory);
    assertTrue(""+mti.getLinkRewriterFactory().getClass(),
	       mti.getLinkRewriterFactory() instanceof
	       NodeFilterHtmlLinkRewriterFactory);
    mti = p2.getMimeTypeInfo("text/css");
    assertTrue(mti.getLinkExtractorFactory()
	       instanceof CssLinkExtractor.Factory);
    assertNull(mti.getLinkRewriterFactory()); // XXX 

    mti = p2.getMimeTypeInfo("application/pdf");
    assertNull(mti.getFilterFactory());
    assertNull(mti.getFetchRateLimiter());
    assertNull(mti.getLinkRewriterFactory()); // XXX 
  }

  public void testInitMimeMap() {
  }

  public void testCreateAu() throws ArchivalUnit.ConfigurationException {
    Properties p = new Properties();
    p.setProperty("TEST_KEY", "TEST_VALUE");
    p.setProperty(ConfigParamDescr.BASE_URL.getKey(), "http://www.example.com/");
     p.setProperty(BaseArchivalUnit.KEY_PAUSE_TIME,"10000");
    List rules = ListUtil.list("1,\"http://www.example.com\"");
    ExternalizableMap map = definablePlugin.getDefinitionMap();
    map.putString(DefinablePlugin.KEY_PLUGIN_NAME, "testplugin");
    map.putCollection(DefinablePlugin.KEY_PLUGIN_CONFIG_PROPS,
                      Collections.EMPTY_LIST);
    map.putCollection(DefinableArchivalUnit.KEY_AU_CRAWL_RULES,rules);
    map.putString("au_start_url", "http://www.example.com/");
    ConfigurationUtil.setCurrentConfigFromProps(p);
    Configuration auConfig = CurrentConfig.getCurrentConfig();
    ArchivalUnit actualReturn = definablePlugin.createAu(auConfig);
    assertTrue(actualReturn instanceof DefinableArchivalUnit);
    assertEquals("configuration", auConfig, actualReturn.getConfiguration());
  }

  public void testGetAuConfigProperties() {
    Collection expectedReturn = ListUtil.list("Item1", "Item2");
    ExternalizableMap map = definablePlugin.getDefinitionMap();
    map.putCollection(DefinablePlugin.KEY_PLUGIN_CONFIG_PROPS,
                      expectedReturn);

    List actualReturn = definablePlugin.getLocalAuConfigDescrs();
    assertIsomorphic("return value", expectedReturn, actualReturn);
  }

  public void testGetConfigurationMap() {
    ExternalizableMap expectedReturn = definablePlugin.definitionMap;
    ExternalizableMap actualReturn = definablePlugin.getDefinitionMap();
    assertEquals("return value", expectedReturn, actualReturn);
  }

  public void testGetPluginName() {
    // no name set
    assertEquals("Internal", definablePlugin.getPluginName());

    // set the name
    String expectedReturn = "TestPlugin";
    defMap.putString(DefinablePlugin.KEY_PLUGIN_NAME, expectedReturn);
    assertEquals("return value", expectedReturn,
		 definablePlugin.getPluginName());
  }

  public void testGetVersion() {
    // no version set
    String expectedReturn = DEFAULT_PLUGIN_VERSION;
    String actualReturn = definablePlugin.getVersion();
    assertEquals("return value", expectedReturn, actualReturn);

    // set the version
    expectedReturn = "Version 1.0";
    ExternalizableMap map = definablePlugin.getDefinitionMap();
    map.putString(DefinablePlugin.KEY_PLUGIN_VERSION, expectedReturn);
    actualReturn = definablePlugin.getVersion();
    assertEquals("return value", expectedReturn, actualReturn);

  }

  public void testGetPluginId() throws Exception {
    LockssDaemon daemon = getMockLockssDaemon();
    String extMapName = "org.lockss.test.MockConfigurablePlugin";
    definablePlugin.initPlugin(daemon, extMapName);
    assertEquals("org.lockss.test.MockConfigurablePlugin",
                 definablePlugin.getPluginId());
  }

  public void testGetPublishingPlatform() throws Exception {
    assertNull("Internal", definablePlugin.getPublishingPlatform());
    String expectedReturn = "Publisher Platform Shoes";
    defMap.putString(DefinablePlugin.KEY_PUBLISHING_PLATFORM, expectedReturn);
    assertEquals("return value", expectedReturn,
		 definablePlugin.getPublishingPlatform());
  }

  public void testInitPlugin() throws Exception {
    definablePlugin = null; //   ensure don't accidentally use wrong veriable
    LockssDaemon daemon = getMockLockssDaemon();
    DefinablePlugin plug = new DefinablePlugin();
    try {
      plug.initPlugin(daemon, (String)null);
      fail("initPlugin(, null) Should throw");
    }
    catch (NullPointerException npe) {
    }
    assertEquals("DefinablePlugin", plug.getPluginName());

    String extMapName = "org.lockss.test.MockConfigurablePlugin";
    plug.initPlugin(daemon, extMapName);
    assertEquals("Absinthe Literary Review",
                 plug.getPluginName());
    assertEquals("1", plug.getVersion());

    // check some other field
    StringBuffer sb = new StringBuffer("\"%sarchives%02d.htm\", ");
    sb.append(ConfigParamDescr.BASE_URL.getKey());
    sb.append(", ");
    sb.append(ConfigParamDescr.YEAR.getKey());
    ExternalizableMap map = plug.getDefinitionMap();
    assertEquals(sb.toString(),
                 map.getString(DefinableArchivalUnit.KEY_AU_START_URL, null));

  }

  public void testInstallCacheExceptionHandler() {
    DefinablePlugin plugin = new DefinablePlugin();
    ExternalizableMap map = plugin.getDefinitionMap();
    String name = new MockHttpResultHandler().getClass().getName();
    // test using a special class
    map.putString(DefinablePlugin.KEY_EXCEPTION_HANDLER,name);
    plugin.initResultMap();
    CacheResultHandler hand = plugin.getCacheResultHandler();
    assertTrue(hand instanceof CacheResultHandlerWrapper);
    assertTrue(WrapperUtil.unwrap(hand) instanceof MockHttpResultHandler);

  }

  HttpResultMap getHttpResultMap(DefinablePlugin plugin) {
    return (HttpResultMap)plugin.getCacheResultMap();
  }

  class IOEParent extends IOException {
  }
  class IOEChild extends IOEParent {
  }

  public void testInstallCacheExceptionEntries() throws Exception {
    DefinablePlugin plugin = new DefinablePlugin();
    ExternalizableMap map = plugin.getDefinitionMap();
    IOException ioe1 = new SocketException("sock1");
    IOException ioe2 = new ConnectException("conn1");

    plugin.initResultMap();

    // nothing installed should give the default
    assertEquals(CacheException.NoRetryDeadLinkException.class,
		 getHttpResultMap(plugin).getExceptionClass(404));
    assertEquals(CacheException.RetryableNetworkException_3_30S.class,
		 getHttpResultMap(plugin).mapException(null, ioe1, null).getClass());
    assertEquals(CacheException.RetryableNetworkException_3_30S.class,
		 getHttpResultMap(plugin).mapException(null, ioe2, null).getClass());

    String spec1 =
      "404=org.lockss.util.urlconn.CacheException$RetryDeadLinkException";
    String spec2 =
      "java.net.SocketException" +
      "=org.lockss.util.urlconn.CacheException$RetryableNetworkException_2_5M";

    map.putCollection(DefinablePlugin.KEY_EXCEPTION_LIST,
		      ListUtil.list(spec1, spec2));
    plugin.initResultMap();
    assertEquals(CacheException.RetryDeadLinkException.class,
		 getHttpResultMap(plugin).getExceptionClass(404));
    // changing just SocketException should change result for
    // ConnectException as well
    assertEquals(CacheException.RetryableNetworkException_2_5M.class,
		 getHttpResultMap(plugin).mapException(null, ioe1, null).getClass());
    assertEquals(CacheException.RetryableNetworkException_2_5M.class,
		 getHttpResultMap(plugin).mapException(null, ioe2, null).getClass());
  }

  public void testSiteNormalizeUrlNull() {
    UrlNormalizer urlNormalizer = definablePlugin.getUrlNormalizer();
    assertSame(BasePlugin.NullUrlNormalizer.INSTANCE, urlNormalizer);
  }

  public void testSiteNormalizeUrl() {
    defMap.putString(ArchivalUnit.KEY_AU_URL_NORMALIZER,
		     "org.lockss.plugin.definable.TestDefinablePlugin$MyNormalizer");
    UrlNormalizer urlNormalizer = definablePlugin.getUrlNormalizer();
    assertTrue(urlNormalizer instanceof UrlNormalizerWrapper);
    assertTrue(WrapperUtil.unwrap(urlNormalizer) instanceof MyNormalizer);
  }

  public void testMakeUrlNormalizerThrowsOnBadClass()
      throws LockssRegexpException {
    defMap.putString(ArchivalUnit.KEY_AU_URL_NORMALIZER,
		     "org.lockss.bogus.FakeClass");

    try {
      UrlNormalizer urlNormalizer = definablePlugin.getUrlNormalizer();
      fail("Should have thrown on a non-existant class");
    } catch (PluginException.InvalidDefinition e){
    }
  }

  String[] badPlugins = {
    "BadPluginIllArg1",
    "BadPluginIllArg2",
    "BadPluginIllArg3",
    "BadPluginIllArg4",
    "BadPluginIllArg5",
    "BadPluginIllArg6",
  };

  public void testLoadBadPlugin() throws Exception {
    String prefix = "org.lockss.plugin.definable.";
    // first ensure that the canonical good plugin does load
    assertTrue("Control (good) plugin didn't load",
	       attemptToLoadPlugin(prefix + "GoodPlugin"));
    // then try various perturbations of it, which should all fail
    for (String bad : badPlugins) {
      testLoadBadPlugin(prefix + bad);
    }
  }

  public void testLoadBadPlugin(String pname) throws Exception {
    assertFalse("Bad plugin " + pname + " should not have loaded successfully",
		attemptToLoadPlugin(pname));
  }

  private boolean attemptToLoadPlugin(String pname)  {
    PluginManager pmgr = getMockLockssDaemon().getPluginManager();
    String key = PluginManager.pluginKeyFromId(pname);
    return pmgr.ensurePluginLoaded(key);
  }


  public static class MyDefinablePlugin extends DefinablePlugin {
    public MimeTypeInfo getMimeTypeInfo(String contentType) {
      return super.getMimeTypeInfo(contentType);
    }
  }

  public static class MyNormalizer implements UrlNormalizer {
    public String normalizeUrl (String url, ArchivalUnit au) {
      return "blah";
    }
  }

}
