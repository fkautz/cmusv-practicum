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

import java.util.*;
import java.io.*;

import org.lockss.plugin.*;
import org.lockss.daemon.*;
import org.lockss.test.*;
import org.lockss.util.*;
import org.lockss.crawler.*;

/**
 * TestConfigurableArchivalUnit: test case for the ConfigurableArchivalUnit
 * @author Claire Griffin
 * @version 1.0
 */

public class TestDefinableArchivalUnit extends LockssTestCase {
  private DefinableArchivalUnit cau = null;
  private TypedEntryMap configMap;
  private ExternalizableMap defMap;
  private List configProps = ListUtil.list(ConfigParamDescr.BASE_URL,
                                           ConfigParamDescr.VOLUME_NUMBER);
  private List crawlRules = ListUtil.list("1,\"%s\", base_url",
                                          "1,\".*\\.gif\"");

  private static String PLUGIN_NAME = "Test Plugin";
  private static String CURRENT_VERSION = "Version 1.0";

  protected void setUp() throws Exception {
    super.setUp();

    DefinablePlugin cp = new DefinablePlugin();
    defMap = cp.getDefinitionMap();
    cau = new DefinableArchivalUnit(cp, defMap);
    configMap = cau.getProperties();
    configMap.putString(DefinablePlugin.CM_NAME_KEY, PLUGIN_NAME);
    configMap.putString(DefinablePlugin.CM_VERSION_KEY, CURRENT_VERSION);
    configMap.putCollection(DefinablePlugin.CM_CONFIG_PROPS_KEY, configProps);
  }

  protected void tearDown() throws Exception {
    cau = null;
    super.tearDown();
  }

  public void testConverVariableStringWithNumRange() {
    Vector vec = new Vector(2);
    String key = ConfigParamDescr.NUM_ISSUE_RANGE.getKey();
    vec.add(0, new Long(10));
    vec.add(1, new Long(20));
    configMap.setMapElement(key, vec);
    String substr = "\"My Test Range = %s\", " + key;
    String expectedReturn = "My Test Range = (\\d+)";
    String actualReturn = cau.convertVariableString(substr);
    assertEquals("return value", expectedReturn, actualReturn);
  }

  public void testConverVariableStringWithRange() {
    Vector vec = new Vector(2);
    String key = ConfigParamDescr.ISSUE_RANGE.getKey();
    vec.add(0, "aaa");
    vec.add(1, "zzz");
    configMap.setMapElement(key, vec);
    String substr = "\"My Test Range = %s\", " + key;
    String expectedReturn = "My Test Range = (.*)";
    String actualReturn = cau.convertVariableString(substr);
    assertEquals("return value", expectedReturn, actualReturn);
  }

  public void testConvertVariableStringWithSet() {
    Vector vec = new Vector();
    String key = ConfigParamDescr.ISSUE_SET.getKey();
    vec.add("apple");
    vec.add("bananna");
    vec.add("grape");
    configMap.setMapElement(key, vec);
    String substr = "\"My Test Range = %s\", " + key;
    String expectedReturn = "My Test Range = (.*)";
    String actualReturn = cau.convertVariableString(substr);
    assertEquals("return value", expectedReturn, actualReturn);
  }

  public void testConvertVariableString() {
    configMap.putInt("INTEGER", 10);
    configMap.putBoolean("BOOLEAN", true);
    configMap.putString("STRING", "Yo Mama!");
    configMap.putInt(ConfigParamDescr.YEAR.getKey(), 2003);
    configMap.putInt(DefinableArchivalUnit.AU_SHORT_YEAR_PREFIX +
               ConfigParamDescr.YEAR.getKey(),3);

    String substr = "\"My Test Integer = %d\", INTEGER";
    String expectedReturn = "My Test Integer = 10";
    String actualReturn = cau.convertVariableString(substr);
    assertEquals("return value", expectedReturn, actualReturn);

    substr = "\"My Test Boolean = %s\", BOOLEAN";
    expectedReturn = "My Test Boolean = true";
    actualReturn = cau.convertVariableString(substr);
    assertEquals("return value", expectedReturn, actualReturn);

    substr = "\"My Test String = %s\", STRING";
    expectedReturn = "My Test String = Yo Mama!";
    actualReturn = cau.convertVariableString(substr);
    assertEquals("return value", expectedReturn, actualReturn);

    substr = "\"My Test Short Year = %02d\", au_short_year";
    expectedReturn = "My Test Short Year = 03";
    actualReturn = cau.convertVariableString(substr);
    assertEquals("return value", expectedReturn, actualReturn);
  }

  public void testConvertRule() throws LockssRegexpException {
    configMap.putString("URL", "http://www.example.com/");
    String rule1 = "1,\".*\\.gif\"";
    String rule2 = "1,\"%s\",URL";

    CrawlRule actualReturn = cau.convertRule(rule1);
    assertEquals(CrawlRule.INCLUDE,
                 actualReturn.match("http://www.example.com/mygif.gif"));

    actualReturn = cau.convertRule(rule2);
    assertEquals(CrawlRule.INCLUDE,
                 actualReturn.match("http://www.example.com/"));
  }

  public void testConvertRangeRule() throws LockssRegexpException {
    Vector vec = new Vector(2);
    String key = ConfigParamDescr.ISSUE_RANGE.getKey();
    vec.add(0, "aaa");
    vec.add(1, "hhh");
    configMap.setMapElement(key, vec);
    String rule = "1,\"http://www.example.com/%sissue.html\", " + key;
    CrawlRule actualReturn = cau.convertRule(rule);
    assertEquals(CrawlRule.INCLUDE,
                 actualReturn.match("http://www.example.com/abxissue.html"));
    assertEquals(CrawlRule.IGNORE,
                 actualReturn.match("http://www.example.com/zylophoneissue.html"));
  }
  public void testConvertNumRangeRule() throws LockssRegexpException {
    Vector vec = new Vector(2);
    String key = ConfigParamDescr.NUM_ISSUE_RANGE.getKey();
    vec.add(0, new Long(10));
    vec.add(1, new Long(20));
    configMap.setMapElement(key, vec);
    String rule = "1,\"http://www.example.com/issue%s.html\", " + key;
    CrawlRule actualReturn = cau.convertRule(rule);
    assertEquals(CrawlRule.INCLUDE,
                 actualReturn.match("http://www.example.com/issue13.html"));
    assertEquals(CrawlRule.IGNORE,
                 actualReturn.match("http://www.example.com/issue44.html"));
  }

  public void testConvertSetRule() throws LockssRegexpException {
    Vector vec = new Vector();
    String key = ConfigParamDescr.ISSUE_SET.getKey();
    vec.add("apple");
    vec.add("bananna");
    vec.add("grape");
    vec.add("fig");
    configMap.setMapElement(key, vec);
    String rule = "1,\"http://www.example.com/%sissue.html\", " + key;
    CrawlRule actualReturn = cau.convertRule(rule);
    assertEquals(CrawlRule.INCLUDE,
                 actualReturn.match("http://www.example.com/appleissue.html"));
    assertEquals(CrawlRule.IGNORE,
                 actualReturn.match("http://www.example.com/orangeissue.html"));
  }

  public void testMakeName() {
    configMap.putString("JOURNAL_NAME", "MyJournal");
    configMap.putInt("VOLUME", 43);
    defMap.putString(DefinableArchivalUnit.AU_NAME_KEY,
                  "\"%s Vol %d\",JOURNAL_NAME,VOLUME");
    String expectedReturn = "MyJournal Vol 43";
    String actualReturn = cau.makeName();
    assertEquals("return value", expectedReturn, actualReturn);
  }

  public void testMakeRules() throws LockssRegexpException {
    configMap.putString("base_url", "http://www.example.com/");
    defMap.putCollection(DefinableArchivalUnit.AU_RULES_KEY, crawlRules);

    CrawlRule rules = cau.makeRules();
    assertEquals(CrawlRule.INCLUDE,
                 rules.match("http://www.example.com/mygif.gif"));
    assertEquals(CrawlRule.INCLUDE,
                 rules.match("http://www.example.com/"));
  }

  public void testMakeStartUrl() {
    configMap.putInt("VOLUME", 43);
    configMap.putString("URL", "http://www.example.com/");
    defMap.putString(DefinableArchivalUnit.AU_START_URL_KEY,
                  "\"%slockss-volume/%d.html\", URL, VOLUME");

    String expectedReturn = "http://www.example.com/lockss-volume/43.html";
    String actualReturn = cau.makeStartUrl();
    assertEquals("return value", expectedReturn, actualReturn);
  }

  public void testGetManifestPage() {

    configMap.putString("HOST", "www.example.com");
    configMap.putInt("YEAR", 2003);
    defMap.putString(DefinableArchivalUnit.AU_MANIFEST_KEY,
            "\"http://%s/contents-by-date.%d.shtml\", HOST, YEAR");
    String expectedReturn = "http://www.example.com/contents-by-date.2003.shtml";
    String actualReturn = (String)cau.getPermissionPages().get(0);
    assertEquals("return valuse", expectedReturn, actualReturn);
  }

  public void testGetContentParser() {
    // test we find the default
    ContentParser parser = null;
    parser = cau.getContentParser("text/html");
    assertTrue(parser instanceof org.lockss.crawler.GoslingHtmlParser);

    // test we don't find one that doesn't exist
    parser = cau.getContentParser("text/ram");
    assertNull(parser);

    // test we find one we've added
    defMap.putString("text/ram_parser",
		     "org.lockss.test.MockContentParser");
    parser = cau.getContentParser("text/ram");
    assertTrue(parser instanceof org.lockss.test.MockContentParser);
  }

  public void testGetContentParserHandlesContentType() {
    defMap.putString("text/ram_parser",
		     "org.lockss.test.MockContentParser");

    ContentParser parser = null;
    parser = cau.getContentParser("text/ram ; random-content-type");
    assertTrue(parser instanceof org.lockss.test.MockContentParser);
 
    parser = cau.getContentParser(" text/ram ");
    assertTrue(parser instanceof org.lockss.test.MockContentParser);
  }

  public void testGetCrawlRule() throws LockssRegexpException {
    defMap.putString(DefinableArchivalUnit.AU_RULES_KEY,
 		  "org.lockss.plugin.definable.TestDefinableArchivalUnit$NegativeCrawlRuleFactory");

    CrawlRule rules = cau.makeRules();
    assertEquals(CrawlRule.EXCLUDE,
                 rules.match("http://www.example.com/mygif.gif"));
    assertEquals(CrawlRule.EXCLUDE,
                 rules.match("http://www.example.com/"));

    defMap.putString(DefinableArchivalUnit.AU_RULES_KEY,
		  "org.lockss.plugin.definable.TestDefinableArchivalUnit$PositiveCrawlRuleFactory");

    rules = cau.makeRules();
    assertEquals(CrawlRule.INCLUDE,
                 rules.match("http://www.example.com/mygif.gif"));
    assertEquals(CrawlRule.INCLUDE,
                 rules.match("http://www.example.com/"));
  }

  public void testGetCrawlRuleThrowsOnBadClass() throws LockssRegexpException {
    defMap.putString(DefinableArchivalUnit.AU_RULES_KEY,
		  "org.lockss.bogus.FakeClass");

    try {
      CrawlRule rules = cau.makeRules();
      fail("Should have thrown on a non-existant class");
    } catch (DefinablePlugin.InvalidDefinitionException e){
    }
  }


  public void testSiteNormalizeUrlNull() {
    UrlNormalizer urlNormalizer = cau.makeUrlNormalizer();
    assertNull(urlNormalizer);
  }

  public void testSiteNormalizeUrl() {
    defMap.putString(DefinableArchivalUnit.AU_URL_NORMALIZER_KEY,
		  "org.lockss.plugin.definable.TestDefinableArchivalUnit$MyNormalizer");
    UrlNormalizer urlNormalizer = cau.makeUrlNormalizer();
    assertTrue(urlNormalizer instanceof org.lockss.plugin.definable.TestDefinableArchivalUnit.MyNormalizer);
  }

  public void testMakeUrlNormalizerThrowsOnBadClass()
      throws LockssRegexpException {
    defMap.putString(DefinableArchivalUnit.AU_URL_NORMALIZER_KEY,
		  "org.lockss.bogus.FakeClass");

    try {
      UrlNormalizer urlNormalizer = cau.makeUrlNormalizer();
      fail("Should have thrown on a non-existant class");
    } catch (DefinablePlugin.InvalidDefinitionException e){
    }
  }

  public void testConstructFilterRule() {
    assertNull(cau.constructFilterRule(null));
  }

  public void testConstructFilterRuleMimeType() {
    defMap.putString("text/html"+DefinableArchivalUnit.AU_FILTER_SUFFIX,
		     "org.lockss.plugin.definable.TestDefinableArchivalUnit$MyMockFilterRule");
    assertTrue(cau.constructFilterRule("text/html") instanceof
	       org.lockss.plugin.definable.TestDefinableArchivalUnit.MyMockFilterRule);
  }

  public void testConstructFilterRuleMimeTypeSpace() {
    defMap.putString("text/html"+DefinableArchivalUnit.AU_FILTER_SUFFIX,
		     "org.lockss.plugin.definable.TestDefinableArchivalUnit$MyMockFilterRule");
    assertTrue(cau.constructFilterRule(" text/html ") instanceof
	       org.lockss.plugin.definable.TestDefinableArchivalUnit.MyMockFilterRule);
  }

  public void testConstructFilterRuleContentType() {
    defMap.putString("text/html"+DefinableArchivalUnit.AU_FILTER_SUFFIX,
		     "org.lockss.plugin.definable.TestDefinableArchivalUnit$MyMockFilterRule");
    assertTrue(cau.constructFilterRule("text/html ; random-char-set") instanceof
	       org.lockss.plugin.definable.TestDefinableArchivalUnit.MyMockFilterRule);
  }

  public void testConstructFilterRuleContentTypeSpace() {
    defMap.putString("text/html"+DefinableArchivalUnit.AU_FILTER_SUFFIX,
		     "org.lockss.plugin.definable.TestDefinableArchivalUnit$MyMockFilterRule");
    assertTrue(cau.constructFilterRule(" text/html ; random-char-set") instanceof
	       org.lockss.plugin.definable.TestDefinableArchivalUnit.MyMockFilterRule);
  }

  public static class NegativeCrawlRuleFactory
    implements CrawlRuleFromAuFactory {

    public CrawlRule createCrawlRule(ArchivalUnit au) {
      return new NegativeCrawlRule();
    }
  }

  public static class PositiveCrawlRuleFactory
    implements CrawlRuleFromAuFactory {

    public CrawlRule createCrawlRule(ArchivalUnit au) {
      return new PositiveCrawlRule();
    }
  }

  public static class MyNormalizer implements UrlNormalizer {
    public String normalizeUrl (String url, ArchivalUnit au) {
      return "blah";
    }
  }

  public static class MyMockFilterRule implements FilterRule {
    public Reader createFilteredReader(Reader reader) {
      throw new UnsupportedOperationException("not implemented");
    }

  }


}
