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
import java.util.*;
import org.lockss.daemon.*;
import org.lockss.test.*;
import org.lockss.plugin.*;
import org.lockss.util.*;
import org.lockss.daemon.Configuration;
import org.lockss.crawler.*;
import org.lockss.plugin.ArchivalUnit.*;
import java.net.*;
import org.lockss.plugin.ArchivalUnit.ConfigurationException;

/**
 * This is the test class for org.lockss.plugin.simulated.GenericFileUrlCacher
 *
 * @author  Emil Aalto
 * @version 0.0
 */
public class TestBaseArchivalUnit extends LockssTestCase {
  private MyMockBaseArchivalUnit mbau;
  private MyMockPlugin mplug;
  private String baseUrl = "http://www.example.com";
  private String startUrl = baseUrl + "/index.html";
  private String auName = "MockBaseArchivalUnit";
  private CrawlRule crawlRule = null;

  public void setUp() throws Exception {
    super.setUp();
    Properties props = new Properties();
    props.setProperty(BaseArchivalUnit.PARAM_TOPLEVEL_POLL_INTERVAL_MIN, "5s");
    props.setProperty(BaseArchivalUnit.PARAM_TOPLEVEL_POLL_INTERVAL_MAX, "10s");
    props.setProperty(BaseArchivalUnit.PARAM_TOPLEVEL_POLL_PROB_INITIAL, "50");
    props.setProperty(BaseArchivalUnit.PARAM_TOPLEVEL_POLL_PROB_INCREMENT, "5");
    props.setProperty(BaseArchivalUnit.PARAM_TOPLEVEL_POLL_PROB_MAX, "85");
    ConfigurationUtil.setCurrentConfigFromProps(props);

    List rules = new LinkedList();
    // exclude anything which doesn't start with our base url
    rules.add(new CrawlRules.RE("^" + baseUrl, CrawlRules.RE.NO_MATCH_EXCLUDE));
    // include the start url
    rules.add(new CrawlRules.RE(startUrl, CrawlRules.RE.MATCH_INCLUDE));
    CrawlRule rule = new CrawlRules.FirstMatch(rules);
    mplug = new MyMockPlugin();
    mbau =  new MyMockBaseArchivalUnit(mplug, auName, rule, startUrl);

  }

  public void tearDown() throws Exception {
    TimeBase.setReal();
    super.tearDown();
  }


  public void testConfiguration() throws ConfigurationException {
    // unconfigured  - return null
    Configuration expectedReturn = null;
    Configuration actualReturn = mbau.getConfiguration();
    assertEquals("return value", expectedReturn, actualReturn);

    // null configuration throws when set
    try {
      mbau.setConfiguration(null);
      assertTrue("null value should throw", true);    }
    catch (ConfigurationException ex) {
    }

    // cofiguration return by getConfiguration same as one previously set
    Properties props = new Properties();
    props.setProperty(ConfigParamDescr.BASE_URL.getKey(), baseUrl);
    props.setProperty(ConfigParamDescr.VOLUME_NUMBER.getKey(), "10");
    expectedReturn = ConfigurationUtil.fromProps(props);
    mbau.setConfiguration(expectedReturn);
    actualReturn = mbau.getConfiguration();
    assertEquals("return value", expectedReturn, actualReturn);

  }

  public void testLoadConfigInt() {
    Properties props = new Properties();
    props.setProperty(ConfigParamDescr.VOLUME_NUMBER.getKey(), "10");
    ConfigParamDescr descr = ConfigParamDescr.VOLUME_NUMBER;
    Configuration config = ConfigurationUtil.fromProps(props);
    int expectedReturn = 10;
    int actualReturn = 0;
    try {
      actualReturn = mbau.loadConfigInt(descr, config);
      assertEquals("return value", expectedReturn, actualReturn);
    }
    catch (ConfigurationException ex1) {
    }

    props.setProperty(ConfigParamDescr.VOLUME_NUMBER.getKey(), "xyz");
    config = ConfigurationUtil.fromProps(props);

    try {
      actualReturn = mbau.loadConfigInt(descr, config);
      assertTrue("invalid value should throw", true);
    }
    catch (ConfigurationException ex) {
    }
  }

  public void testLoadConfigString() throws ArchivalUnit.ConfigurationException {
    Properties props = new Properties();
    props.setProperty(ConfigParamDescr.JOURNAL_DIR.getKey(), "foo");
    ConfigParamDescr descr = ConfigParamDescr.JOURNAL_DIR;
    Configuration config = ConfigurationUtil.fromProps(props);
    String expectedReturn = "foo";
    String actualReturn = mbau.loadConfigString(descr, config);
    assertEquals("return value", expectedReturn, actualReturn);
    // no value assigned should throw
    try  {
      expectedReturn =
          mbau.loadConfigString(ConfigParamDescr.JOURNAL_ABBR, config);
      assertTrue("missing value should throw", true);
    }
    catch(ConfigurationException ex) {

    }
  }

  public void testGetPlugin() {
    Plugin expectedReturn = mplug;
    Plugin actualReturn = mbau.getPlugin();
    assertEquals("return value", expectedReturn, actualReturn);
  }

  public void testGetPluginId() {
    String expectedReturn = mplug.getClass().getName();
    String actualReturn = mbau.getPluginId();
    assertEquals("return value", expectedReturn, actualReturn);
  }

  public void testGetAuId() throws ConfigurationException {
    // cofiguration return by getConfiguration same as one previously set
    Properties props = new Properties();
    props.setProperty(ConfigParamDescr.BASE_URL.getKey(), baseUrl);
    props.setProperty(ConfigParamDescr.VOLUME_NUMBER.getKey(), "10");
    Configuration config = ConfigurationUtil.fromProps(props);
    mbau.setConfiguration(config);
    String expectedReturn =
        "org|lockss|plugin|base|TestBaseArchivalUnit$MyMockPlugin&base_url~http%3A%2F%2Fwww%2Eexample%2Ecom&volume~10";
    String actualReturn = mbau.getAuId();
    assertEquals("return value", expectedReturn, actualReturn);
  }

  public void testGetFetchDelay() {
    long expectedReturn =
        BaseArchivalUnit.DEFAULT_MILLISECONDS_BETWEEN_CRAWL_HTTP_REQUESTS;
    long actualReturn = mbau.getFetchDelay();
    assertEquals("return value", expectedReturn, actualReturn);
  }

  public void testGetName() throws ConfigurationException {
    String expectedReturn = auName;
    String actualReturn = mbau.getName();
    assertNull(actualReturn);
    Properties props = new Properties();
    props.setProperty(ConfigParamDescr.BASE_URL.getKey(), baseUrl);
    ConfigParamDescr descr = ConfigParamDescr.BASE_URL;
    Configuration config = ConfigurationUtil.fromProps(props);
    mbau.setBaseAuParams(config);
    actualReturn = mbau.getName();
    assertEquals("return value", expectedReturn, actualReturn);

  }

  public void testGetParamMap() {
    BaseArchivalUnit.ParamHandlerMap expectedReturn =
        (BaseArchivalUnit.ParamHandlerMap)mbau.paramMap;
    BaseArchivalUnit.ParamHandlerMap actualReturn = mbau.getParamMap();
    assertEquals("return value", expectedReturn, actualReturn);
  }

  public void testGetProperties() {
    TypedEntryMap expectedReturn = mbau.paramMap;
    TypedEntryMap actualReturn = mbau.getProperties();
    assertEquals("return value", expectedReturn, actualReturn);
  }


  public void testLoadConfigUrl() throws ArchivalUnit.ConfigurationException {
    Properties props = new Properties();
    props.setProperty(ConfigParamDescr.BASE_URL.getKey(), baseUrl);
    ConfigParamDescr descr = ConfigParamDescr.BASE_URL;
    Configuration config = ConfigurationUtil.fromProps(props);
    URL expectedReturn = null;
    try {
      expectedReturn = new URL(baseUrl);
    }
    catch (MalformedURLException ex) {
    }
    URL actualReturn = mbau.loadConfigUrl(descr, config);
    assertEquals("return value", expectedReturn, actualReturn);
  }

  public void testConstructFilterRule() {
    String mimeType = "text/html";
    FilterRule actualReturn = mbau.constructFilterRule(mimeType);
    assertNull("return value", actualReturn);
  }

  public void testGetCrawlSpec() throws ConfigurationException {
    // we're null until we're configured
    assertNull(mbau.getCrawlSpec());
    Properties props = new Properties();
    props.setProperty(ConfigParamDescr.BASE_URL.getKey(), baseUrl);
    Configuration config = ConfigurationUtil.fromProps(props);
    mbau.setBaseAuParams(config);
    assertNotNull(mbau.getCrawlSpec());
  }

  public void testGetFilterRule() {
    String mimeType = "text/html";
    FilterRule actualReturn = mbau.getFilterRule(mimeType);
    assertNull("return value",actualReturn);
  }

  public void testGetNewContentCrawlUrls() throws ConfigurationException {
    Properties props = new Properties();
    props.setProperty(ConfigParamDescr.BASE_URL.getKey(), baseUrl);
    Configuration config = ConfigurationUtil.fromProps(props);
    mbau.setBaseAuParams(config);
    List expectedReturn = ListUtil.list(startUrl);
    List actualReturn = mbau.getNewContentCrawlUrls();
    assertIsomorphic("return value", expectedReturn, actualReturn);
  }
  public void testGetPermissionPages() throws ConfigurationException {
    Properties props = new Properties();
    props.setProperty(ConfigParamDescr.BASE_URL.getKey(), baseUrl);
    Configuration config = ConfigurationUtil.fromProps(props);
    mbau.setBaseAuParams(config);
    List expectedReturn = ListUtil.list(startUrl);
    List actualReturn = mbau.getPermissionPages();
    assertIsomorphic("return value", expectedReturn, actualReturn);
  }

  public void testGetUrlStems() throws ConfigurationException  {
    // uncofigured base url - return an empty list
    assertIsomorphic(Collections.EMPTY_LIST, mbau.getUrlStems());
    Properties props = new Properties();
    props.setProperty(ConfigParamDescr.BASE_URL.getKey(), baseUrl);
    Configuration config = ConfigurationUtil.fromProps(props);
    mbau.setBaseAuParams(config);
    Collection expectedReturn = ListUtil.list("http://www.example.com");
    Collection actualReturn = mbau.getUrlStems();
    assertIsomorphic("return value", expectedReturn, actualReturn);
  }

  public void testSiteNormalizeUrl() {
    String url = "http://www.foo.com";
    String actualReturn = mbau.siteNormalizeUrl(url);
    assertEquals("return value", url, actualReturn);
  }

  public void testSetBaseAuParams() throws ArchivalUnit.ConfigurationException {
    Properties props = new Properties();
    props.setProperty(ConfigParamDescr.BASE_URL.getKey(), baseUrl);
    props.setProperty(ConfigParamDescr.VOLUME_NUMBER.getKey(), "10");
    props.setProperty(BaseArchivalUnit.PAUSE_TIME_KEY, "10000");
    props.setProperty(BaseArchivalUnit.USE_CRAWL_WINDOW, "true");
    props.setProperty(BaseArchivalUnit.NEW_CONTENT_CRAWL_KEY, "10000");
    Configuration config = ConfigurationUtil.fromProps(props);
    mbau.setBaseAuParams(config);
    assertEquals(10000, mbau.getFetchDelay());
    assertEquals(10000, mbau.newContentCrawlIntv);
    assertEquals(auName,mbau.getName());
    assertEquals(ListUtil.list(startUrl), mbau.getNewContentCrawlUrls());
    assertTrue(mbau.getCrawlSpec().getCrawlWindow() instanceof MyMockCrawlWindow);
  }


  public void testCheckNextPollInterval() {
    TimeBase.setSimulated();
    for (int ii=0; ii<10; ii++) {
      mbau.nextPollInterval = -1;
      mbau.checkNextPollInterval();
      assertTrue(mbau.nextPollInterval >= 5000);
      assertTrue(mbau.nextPollInterval <= 10000);
    }

    Properties props = new Properties();
    props.setProperty(mbau.PARAM_TOPLEVEL_POLL_INTERVAL_MIN, "1s");
    props.setProperty(mbau.PARAM_TOPLEVEL_POLL_INTERVAL_MAX, "2s");
    ConfigurationUtil.setCurrentConfigFromProps(props);

    mbau.checkNextPollInterval();
    assertTrue(mbau.nextPollInterval >= 1000);
    assertTrue(mbau.nextPollInterval <= 2000);
  }

  public void testIncrementPollProb() {
    assertEquals(0.15, mbau.incrementPollProb(0.10), 0.001);
    assertEquals(0.50, mbau.incrementPollProb(0.45), 0.001);
    // shouldn't increment past max
    assertEquals(0.85, mbau.incrementPollProb(0.83), 0.001);
    assertEquals(0.85, mbau.incrementPollProb(0.85), 0.001);
  }

  public void testCheckPollProb() {
    mbau.curTopLevelPollProb = -1.0;
    mbau.checkPollProb();
    assertEquals(0.50, mbau.curTopLevelPollProb, 0.001);

    mbau.curTopLevelPollProb = .35;
    mbau.checkPollProb();
    assertEquals(0.50, mbau.curTopLevelPollProb, 0.001);

    mbau.curTopLevelPollProb = .90;
    mbau.checkPollProb();
    assertEquals(0.85, mbau.curTopLevelPollProb, 0.001);
  }

  public void testShouldCallTopLevelPoll() throws IOException {
    TimeBase.setSimulated(100);
    MockAuState state = new MockAuState(mbau, -1, TimeBase.nowMs(), -1, null);

    // no interval yet
    assertEquals(-1, mbau.nextPollInterval);
    assertEquals(-1.0, mbau.curTopLevelPollProb, 0);
    assertFalse(mbau.shouldCallTopLevelPoll(state));
    // should determine random interval
    assertTrue(mbau.nextPollInterval >= 5000);
    assertTrue(mbau.nextPollInterval <= 10000);
    assertEquals(0.5, mbau.curTopLevelPollProb, 0.001);

    // move to proper time
    TimeBase.step(mbau.nextPollInterval);
    boolean result = mbau.shouldCallTopLevelPoll(state);
    // should have reset interval
    assertEquals(-1, mbau.nextPollInterval);
    // may or may not have allowed poll
    if (mbau.curTopLevelPollProb != -1) {
      assertEquals(0.55, mbau.curTopLevelPollProb, 0.001);
      assertFalse(result);
    } else {
      assertEquals(-1.0, mbau.curTopLevelPollProb, 0);
      assertTrue(result);
    }

    TimeBase.setReal();
  }


  public void testGetContentParserReturnsNullForNullMimeTupe() {
    assertNull(mbau.getContentParser(null));
  }

  public void testGetContentParserReturnsNullForMissingMimeType() {
    assertNull(mbau.getContentParser(""));
  }

  public void testGetContentParserReturnsGoslingHtmlParser() {
    assertTrue(mbau.getContentParser("text/html")
	       instanceof org.lockss.crawler.GoslingHtmlParser);

    assertTrue(mbau.getContentParser("Text/Html")
	       instanceof org.lockss.crawler.GoslingHtmlParser);
  }

  public void testReturnsGHPWithJunkAfterContentType() {
    assertTrue(mbau.getContentParser("text/html blah")
	       instanceof org.lockss.crawler.GoslingHtmlParser);
  }

  public void testGetContentParserReturnsSameGoslingHtmlParser() {
    assertSame(mbau.getContentParser("text/html"),
	       mbau.getContentParser("text/html"));
  }

  public void testFilterRuleCaching() throws IOException {
    MockFilterRule rule1 = new MockFilterRule();
    rule1.setFilteredReader(new StringReader("rule1"));
    MockFilterRule rule2 = new MockFilterRule();
    rule2.setFilteredReader(new StringReader("rule2"));

    assertNull(mbau.rule);
    assertEquals(0, mbau.cacheMiss);
    assertNull(mbau.getFilterRule("test1"));
    assertEquals(1, mbau.cacheMiss);
    mbau.rule = rule1;
    assertNotNull(mbau.getFilterRule("test1"));
    assertEquals(2, mbau.cacheMiss);
    mbau.rule = rule2;
    assertNotNull(mbau.getFilterRule("test2"));
    assertEquals(3, mbau.cacheMiss);

    rule1 = (MockFilterRule)mbau.getFilterRule("test2");
    assertEquals(3, mbau.cacheMiss);
    assertEquals("rule2", StringUtil.fromReader(
        rule1.createFilteredReader(null)));
    rule2 = (MockFilterRule)mbau.getFilterRule("test1");
    assertEquals(3, mbau.cacheMiss);
    assertEquals("rule1", StringUtil.fromReader(
        rule2.createFilteredReader(null)));
  }

  TitleConfig makeTitleConfig() {
    ConfigParamDescr d1 = new ConfigParamDescr("key1");
    ConfigParamDescr d2 = new ConfigParamDescr("key2");
    ConfigParamAssignment a1 = new ConfigParamAssignment(d1, "a");
    ConfigParamAssignment a2 = new ConfigParamAssignment(d2, "foo");
    a1.setEditable(false);
    a2.setEditable(false);
    TitleConfig tc1 = new TitleConfig("a", "b");
    tc1.setParams(ListUtil.list(a1, a2));
    return tc1;
  }


  public void testGetTitleConfig() throws IOException {
    TitleConfig tc = makeTitleConfig();
    mplug.setTitleConfig(tc);
    mplug.setSupportedTitles(ListUtil.list("a", "b"));

    Configuration config = ConfigurationUtil.fromArgs("key1", "a",
						      "key2", "foo");
    assertEquals(tc, mbau.findTitleConfig(config));

    Configuration config2 = ConfigurationUtil.fromArgs("key1", "b",
						       "key2", "foo");
    assertNull(mbau.findTitleConfig(config2));
  }

  public static void main(String[] argv) {
    String[] testCaseList = { MyMockBaseArchivalUnit.class.getName()};
    junit.swingui.TestRunner.main(testCaseList);
  }

  static class MyMockPlugin extends MockPlugin {
    TitleConfig tc;
    List titles;

    public TitleConfig getTitleConfig(String title) {
      return tc;
    }

    public List getSupportedTitles() {
      return titles;
    }

    void setTitleConfig(TitleConfig tc) {
      this.tc = tc;
    }
    void setSupportedTitles(List titles) {
      this.titles = titles;
    }

  }
  static class MyMockCrawlWindow implements CrawlWindow {
    /**
     * canCrawl
     *
     * @return boolean
     */
    public boolean canCrawl() {
      return false;
    }

    /**
     * canCrawl
     *
     * @param date Date
     * @return boolean
     */
    public boolean canCrawl(Date date) {
      return false;
    }

  }
  static class MyMockBaseArchivalUnit extends BaseArchivalUnit {
    private String auId = null;
    private String m_name = "MockBaseArchivalUnit";
    int cacheMiss = 0;
    FilterRule rule = null;
    private CrawlRule m_rules = null;
    private String m_startUrl ="http://www.example.com/index.html";

    MyMockBaseArchivalUnit(Plugin plugin, String name, CrawlRule rules, String startUrl) {
      super(plugin);
      m_name = name;
      m_startUrl = startUrl;
      m_rules = rules;
   }

    public MyMockBaseArchivalUnit(Plugin myPlugin) {
      super(myPlugin);
    }

    public void setStartUrl(String url) {
      m_startUrl = url;
    }

    public void setName(String name) {
      m_name = name;
    }

    protected String makeName() {
      return m_name;
    }

    protected CrawlRule makeRules() {
      if(m_rules == null) {
        return new MockCrawlRule();
      }
      return m_rules;
    }

    protected String makeStartUrl() {
      return m_startUrl;
    }

    protected CrawlWindow makeCrawlWindow() {
      return new MyMockCrawlWindow();
    }

    protected void loadAuConfigDescrs(Configuration config) {
    }

    protected FilterRule constructFilterRule(String mimeType) {
      cacheMiss++;
      return rule;
    }
  }
}
