/*
 * $Id: TestDefinableArchivalUnit.java,v 1.51 2011/06/20 07:12:45 tlipkis Exp $
 */

/*

Copyright (c) 2000-2011 Board of Trustees of Leland Stanford Jr. University,
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
import org.apache.oro.text.regex.*;

import org.lockss.config.*;
import org.lockss.plugin.*;
import org.lockss.plugin.base.*;
import org.lockss.plugin.wrapper.*;
import org.lockss.daemon.*;
import org.lockss.test.*;
import org.lockss.util.*;
import org.lockss.util.Constants.RegexpContext;
import org.lockss.util.urlconn.*;
import org.lockss.crawler.*;
import org.lockss.oai.*;
import org.lockss.extractor.*;

/**
 * TestConfigurableArchivalUnit: test case for the ConfigurableArchivalUnit
 * @author Claire Griffin
 * @version 1.0
 */

public class TestDefinableArchivalUnit extends LockssTestCase {
  private DefinableArchivalUnit cau = null;
  private TypedEntryMap configMap;
  private TypedEntryMap additionalAuConfig = new TypedEntryMap();
  private ExternalizableMap defMap;
  private List configProps;
  private List crawlRules = ListUtil.list("1,\"%spath/\", base_url",
                                          "1,\".*\\.gif\"");

  private static String PLUGIN_NAME = "Test Plugin";
  private static String CURRENT_VERSION = "Version 1.0";

  DefinablePlugin cp;

  protected void setUp() throws Exception {
    super.setUp();

    configProps = ListUtil.list(ConfigParamDescr.BASE_URL,
                                ConfigParamDescr.VOLUME_NUMBER);
    defMap = new ExternalizableMap();

  }

  protected void tearDown() throws Exception {
    cau = null;
    super.tearDown();
  }

  public DefinableArchivalUnit setupAu() {
    return setupAu(null);
  }

  public DefinableArchivalUnit setupAu(TypedEntryMap addMap) {
    defMap.putCollection(DefinablePlugin.KEY_PLUGIN_CONFIG_PROPS, configProps);
    cp = new DefinablePlugin();
    cp.initPlugin(getMockLockssDaemon(), defMap);
    cau = new DefinableArchivalUnit(cp, defMap);
    configMap = cau.getProperties();
    configMap.putString(DefinablePlugin.KEY_PLUGIN_NAME, PLUGIN_NAME);
    configMap.putString(DefinablePlugin.KEY_PLUGIN_VERSION, CURRENT_VERSION);
    configMap.putCollection(DefinablePlugin.KEY_PLUGIN_CONFIG_PROPS, configProps);
    if (addMap != null) {
      for (Iterator iter = addMap.entrySet().iterator(); iter.hasNext(); ) {
	Map.Entry ent = (Map.Entry)iter.next();
	configMap.setMapElement((String)ent.getKey(), ent.getValue());
      }
    }
    return cau;
  }

  public static final ConfigParamDescr CPD_BOOLE =
    new ConfigParamDescr()
    .setKey("boole")
    .setDisplayName("BBBool")
    .setType(ConfigParamDescr.TYPE_BOOLEAN)
    .setDescription("BBBool");

  public void testConvertUrlList() {
    configProps.add(ConfigParamDescr.VOLUME_NAME);
    configProps.add(ConfigParamDescr.YEAR);
    configProps.add(CPD_BOOLE);
    additionalAuConfig.putInt("volume", 10);
    additionalAuConfig.putBoolean("boole", true);
    //  ensure dot in substitution string doesn't get regexp quoted
    additionalAuConfig.putString("base_url", "Yo.Ma ma!");
    additionalAuConfig.putInt(ConfigParamDescr.YEAR.getKey(), 2003);
    additionalAuConfig.putInt(DefinableArchivalUnit.PREFIX_AU_SHORT_YEAR +
               ConfigParamDescr.YEAR.getKey(),3);
    setupAu(additionalAuConfig);

    assertEquals(ListUtil.list("Test Integer = 10"),
		 cau.convertUrlList("\"Test Integer = %d\", volume"));

    assertEquals(ListUtil.list("Test Boolean = true"),
		 cau.convertUrlList("\"Test Boolean = %s\", boole"));

    assertEquals(ListUtil.list("Test String = Yo.Ma ma!"),
		 cau.convertUrlList("\"Test String = %s\", base_url"));

    assertEquals(ListUtil.list("Test Short Year = 03"),
		 cau.convertUrlList("\"Test Short Year = %02d\", au_short_year"));

    assertNull(cau.convertUrlList("\"Test no param = %s\", no_param"));
  }

  public void testConvertVariableRegexStringWithNumRange() {
    Vector vec = new Vector(2);
    String rkey = ConfigParamDescr.NUM_ISSUE_RANGE.getKey();
    String vkey = ConfigParamDescr.VOLUME_NAME.getKey();
    vec.add(0, new Long(10));
    vec.add(1, new Long(20));
    additionalAuConfig.setMapElement(rkey, vec);
    additionalAuConfig.setMapElement(vkey, "a volume");
    configProps.add(ConfigParamDescr.NUM_ISSUE_RANGE);
    setupAu(additionalAuConfig);
    PrintfConverter.MatchPattern mp =
      cau.convertVariableRegexpString("\"not a URL: iss:%s, vol:%s\", "
				      + rkey + ", " + vkey,
				      RegexpContext.String);
    assertEquals("not a URL: iss:(\\d+), vol:a\\ volume", mp.getRegexp());
  }

  public void testConvertVariableUrlRegexStringWithNumRange() {
    Vector vec = new Vector(2);
    String rkey = ConfigParamDescr.NUM_ISSUE_RANGE.getKey();
    String vkey = ConfigParamDescr.VOLUME_NAME.getKey();
    vec.add(0, new Long(10));
    vec.add(1, new Long(20));
    additionalAuConfig.setMapElement(rkey, vec);
    additionalAuConfig.setMapElement(vkey, "a volume");
    configProps.add(ConfigParamDescr.NUM_ISSUE_RANGE);
    setupAu(additionalAuConfig);
    PrintfConverter.MatchPattern mp =
      cau.convertVariableRegexpString("\"http://host.foo/%s/iss/%s\", "
				      + rkey + ", " + vkey,
				      RegexpContext.Url);
    assertEquals("http://host.foo/(\\d+)/iss/a( |\\+|%20)volume",
		 mp.getRegexp());
  }

  public void testConvertUrlListWithNumRange() {
    configProps.add(ConfigParamDescr.NUM_ISSUE_RANGE);
    Vector vec = new Vector(2);
    String key = ConfigParamDescr.NUM_ISSUE_RANGE.getKey();
    vec.add(0, new Long(10));
    vec.add(1, new Long(20));
    additionalAuConfig.setMapElement(key, vec);
    setupAu(additionalAuConfig);
    String[] exp = {
      "My Test Range = 10",
      "My Test Range = 11",
      "My Test Range = 12",
      "My Test Range = 13",
      "My Test Range = 14",
      "My Test Range = 15",
      "My Test Range = 16",
      "My Test Range = 17",
      "My Test Range = 18",
      "My Test Range = 19",
      "My Test Range = 20"
    };
    List res = cau.convertUrlList("\"My Test Range = %d\", " + key);
    assertIsomorphic(exp, res);
  }

  public void testConvertUrlListWithRange() {
    configProps.add(ConfigParamDescr.ISSUE_RANGE);
    Vector vec = new Vector(2);
    String key = ConfigParamDescr.ISSUE_RANGE.getKey();
    vec.add(0, "aaa");
    vec.add(1, "zzz");
    additionalAuConfig.setMapElement(key, vec);
    setupAu(additionalAuConfig);
    try {
      cau.convertUrlList("\"My Test Range = %s\", " + key);
      fail("Range param in URL template should throw");
    } catch (PluginException.InvalidDefinition e) {
    }
  }

  public void testConvertUrlListWithSet() {
    configProps.add(ConfigParamDescr.ISSUE_SET);
    Vector vec = new Vector();
    String key = ConfigParamDescr.ISSUE_SET.getKey();
    vec.add("apple");
    vec.add("bananna");
    vec.add("grape");
    additionalAuConfig.setMapElement(key, vec);
    setupAu(additionalAuConfig);
    assertEquals(ListUtil.list("Test Set = apple", "Test Set = bananna", "Test Set = grape"),
		 cau.convertUrlList("\"Test Set = %s\", " + key));
  }

  //XXX

  public void testConvertRegexpString() {
    additionalAuConfig.putInt("INTEGER", 10);
    additionalAuConfig.putBoolean("BOOLEAN", true);
    //  ensure meta chars in substitution string't get regexp quoted
    additionalAuConfig.putString("STRING", "Yo.M[a m]a?foo=bar!");
    additionalAuConfig.putInt(ConfigParamDescr.YEAR.getKey(), 2003);
    additionalAuConfig.putInt(DefinableArchivalUnit.PREFIX_AU_SHORT_YEAR +
               ConfigParamDescr.YEAR.getKey(),3);

    setupAu(additionalAuConfig);
    PrintfConverter.MatchPattern mp =
      cau.convertVariableRegexpString("\"Test Integer = %d\", INTEGER",
				      RegexpContext.String);
    assertEquals("Test Integer = 10", mp.getRegexp());

    mp = cau.convertVariableRegexpString("\"Test Boolean = %s\", BOOLEAN",
					 RegexpContext.String);
    assertEquals("Test Boolean = true", mp.getRegexp());

    mp = cau.convertVariableRegexpString("\"Test String = %s\", STRING",
					 RegexpContext.String);
    assertEquals("Test String = Yo\\.M\\[a\\ m\\]a\\?foo\\=bar\\!",
		 mp.getRegexp());

    mp = cau.convertVariableRegexpString("\"Test Short Year = %02d\", au_short_year",
					 RegexpContext.String);
    assertEquals("Test Short Year = 03", mp.getRegexp());
  }

  public void testConvertUrlRegexpString() {
    additionalAuConfig.putInt("INTEGER", 10);
    additionalAuConfig.putBoolean("BOOLEAN", true);
    //  ensure meta chars in substitution string't get regexp quoted, and
    //  blanks get turned into pattern to match URL-encoded blanks
    additionalAuConfig.putString("STRING", "Yo.M[am]a?foo = bar!");
    additionalAuConfig.putInt(ConfigParamDescr.YEAR.getKey(), 2003);
    additionalAuConfig.putInt(DefinableArchivalUnit.PREFIX_AU_SHORT_YEAR +
               ConfigParamDescr.YEAR.getKey(),3);

    setupAu(additionalAuConfig);
    PrintfConverter.MatchPattern mp =
      cau.convertVariableRegexpString("\"Test Integer = %d\", INTEGER",
					 RegexpContext.Url);
    assertEquals("Test Integer = 10", mp.getRegexp());

    mp = cau.convertVariableRegexpString("\"Test Boolean = %s\", BOOLEAN",
					    RegexpContext.Url);
    assertEquals("Test Boolean = true", mp.getRegexp());

    mp = cau.convertVariableRegexpString("\"Test String = %s\", STRING",
					    RegexpContext.Url);
    assertEquals("Test String = Yo\\.M\\[am\\]a\\?foo( |\\+|%20)\\=( |\\+|%20)bar\\!",
		 mp.getRegexp());

    mp = cau.convertVariableRegexpString("\"Test Short Year = %02d\", au_short_year",
					    RegexpContext.Url);
    assertEquals("Test Short Year = 03", mp.getRegexp());
  }

  public void testConvertRule() throws LockssRegexpException {
    additionalAuConfig.putString("URL", "http://www.example.com/");
    additionalAuConfig.putString("VOL", "vol ume");
    setupAu(additionalAuConfig);
    String rule1 = "1,\".*\\.gif\"";
    String rule2 = "1,\"%s\",URL";
    String rule3 = "1,\"%s%s\",URL,VOL";

    CrawlRule rule = cau.convertRule(rule1, false);
    assertEquals(CrawlRule.INCLUDE,
                 rule.match("http://www.example.com/mygif.gif"));
    assertEquals(CrawlRule.IGNORE,
                 rule.match("http://www.example.com/mygif.GIF"));

    rule = cau.convertRule(rule1, true);
    assertEquals(CrawlRule.INCLUDE,
                 rule.match("http://www.example.com/mygif.gif"));
    assertEquals(CrawlRule.INCLUDE,
                 rule.match("http://www.example.com/mygif.GIF"));

    rule = cau.convertRule(rule2, false);
    assertEquals(CrawlRule.INCLUDE,
                 rule.match("http://www.example.com/"));

    // shouldn't match if dot properly quoted
    assertEquals(CrawlRule.IGNORE,
                 rule.match("http://www1example.com/"));

    rule = cau.convertRule(rule3, false);
    assertEquals(CrawlRule.INCLUDE,
                 rule.match("http://www.example.com/vol ume"));
    assertEquals(CrawlRule.INCLUDE,
                 rule.match("http://www.example.com/vol+ume"));
    assertEquals(CrawlRule.INCLUDE,
                 rule.match("http://www.example.com/vol%20ume"));

    // shouldn't match if dot properly quoted
    assertEquals(CrawlRule.IGNORE,
                 rule.match("http://www1example.com/"));
  }

  public void testConvertRuleMissingRequiredParam()
      throws LockssRegexpException {
    additionalAuConfig.putString("URL", "http://www.example.com/");
    setupAu(additionalAuConfig);
    String rule1 = "1,\"%s\",URL,UNknown";

    assertEquals(null, cau.convertRule(rule1, false));
  }

  public void testConvertRuleMissingOptionalParam()
      throws LockssRegexpException {
    additionalAuConfig.putString("URL", "http://www.example.com/");
    setupAu(additionalAuConfig);
    String rule1 = "1,\"%s\",URL";
    String rule2 = "1,\"%s%d\",URL,FOO";

    assertNotNull(cau.convertRule(rule1, false));
    assertNull(cau.convertRule(rule2, false));
  }

  public void testConvertRangeRule() throws LockssRegexpException {
    Vector vec = new Vector(2);
    String key = ConfigParamDescr.ISSUE_RANGE.getKey();
    vec.add(0, "aaa");
    vec.add(1, "hhh");
    additionalAuConfig.setMapElement(key, vec);

    configProps.add(ConfigParamDescr.ISSUE_RANGE);
    setupAu(additionalAuConfig);

    String rule = "1,\"http://www.example.com/%sissue.html\", " + key;
    CrawlRule crule = cau.convertRule(rule, false);
    assertEquals(CrawlRule.INCLUDE,
                 crule.match("http://www.example.com/abxissue.html"));
    assertEquals(CrawlRule.IGNORE,
                 crule.match("http://www.example.com/Abxissue.html"));
    assertEquals(CrawlRule.IGNORE,
                 crule.match("http://www.example.com/zylophoneissue.html"));

    crule = cau.convertRule(rule, true);
    assertEquals(CrawlRule.INCLUDE,
                 crule.match("http://www.example.com/abxissue.html"));
    assertEquals(CrawlRule.INCLUDE,
                 crule.match("http://www.example.com/Abxissue.html"));
    assertEquals(CrawlRule.IGNORE,
                 crule.match("http://www.example.com/zylophoneissue.html"));
  }

  public void testConvertNumRangeRule() throws LockssRegexpException {
    Vector vec = new Vector(2);
    String key = ConfigParamDescr.NUM_ISSUE_RANGE.getKey();
    vec.add(0, new Long(10));
    vec.add(1, new Long(20));
    additionalAuConfig.setMapElement(key, vec);

    configProps.add(ConfigParamDescr.NUM_ISSUE_RANGE);
    setupAu(additionalAuConfig);

    String rule = "1,\"http://www.example.com/issue%s.html\", " + key;
    CrawlRule crule = cau.convertRule(rule, false);
    assertEquals(CrawlRule.INCLUDE,
                 crule.match("http://www.example.com/issue13.html"));
    assertEquals(CrawlRule.IGNORE,
                 crule.match("http://www.example.com/Issue13.html"));
    assertEquals(CrawlRule.IGNORE,
                 crule.match("http://www.example.com/issue44.html"));

    crule = cau.convertRule(rule, true);
    assertEquals(CrawlRule.INCLUDE,
                 crule.match("http://www.example.com/issue13.html"));
    assertEquals(CrawlRule.INCLUDE,
                 crule.match("http://www.example.com/Issue13.html"));
    assertEquals(CrawlRule.IGNORE,
                 crule.match("http://www.example.com/issue44.html"));
  }

  public void testConvertSetRule() throws LockssRegexpException {
    Vector vec = new Vector();
    String key = ConfigParamDescr.ISSUE_SET.getKey();
    vec.add("apple");
    vec.add("banana");
    vec.add("grape");
    vec.add("fig");
    additionalAuConfig.setMapElement(key, vec);

    configProps.add(ConfigParamDescr.ISSUE_SET);
    setupAu(additionalAuConfig);

    String rule = "1,\"http://www.example.com/%sissue.html\", " + key;
    CrawlRule crule = cau.convertRule(rule, false);
    assertEquals(CrawlRule.INCLUDE,
                 crule.match("http://www.example.com/appleissue.html"));
    assertEquals(CrawlRule.INCLUDE,
                 crule.match("http://www.example.com/bananaissue.html"));
    assertEquals(CrawlRule.INCLUDE,
                 crule.match("http://www.example.com/grapeissue.html"));
    assertEquals(CrawlRule.INCLUDE,
                 crule.match("http://www.example.com/figissue.html"));
    assertEquals(CrawlRule.IGNORE,
                 crule.match("http://www.example.com/appleIssue.html"));
    assertEquals(CrawlRule.IGNORE,
                 crule.match("http://www.example.com/Appleissue.html"));
    assertEquals(CrawlRule.IGNORE,
                 crule.match("http://www.example.com/orangeissue.html"));

    crule = cau.convertRule(rule, true);
    assertEquals(CrawlRule.INCLUDE,
                 crule.match("http://www.example.com/appleissue.html"));
    assertEquals(CrawlRule.INCLUDE,
                 crule.match("http://www.example.com/appleIssue.html"));
    assertEquals(CrawlRule.INCLUDE,
                 crule.match("http://www.example.com/Appleissue.html"));
    assertEquals(CrawlRule.IGNORE,
                 crule.match("http://www.example.com/orangeissue.html"));
  }

  public void testMakeName() {
    additionalAuConfig.putString("journal_id", "MyJournal");
    additionalAuConfig.putInt("volume", 43);
    additionalAuConfig.putCollection("issue_range", ListUtil.list("a", "d"));
    additionalAuConfig.putCollection("num_issue_range", ListUtil.list(12,17));
    additionalAuConfig.putCollection("issue_set",
				     ListUtil.list("red", "green", "blue"));
    configProps.add(ConfigParamDescr.JOURNAL_ID);
    configProps.add(ConfigParamDescr.ISSUE_RANGE);
    configProps.add(ConfigParamDescr.NUM_ISSUE_RANGE);
    configProps.add(ConfigParamDescr.ISSUE_SET);
    defMap.putString(DefinableArchivalUnit.KEY_AU_NAME,
                  "\"%s Vol: %d Range: %s NumRange: %s Set: %s\"," +
		     "journal_id,volume,issue_range,num_issue_range,issue_set");
    setupAu(additionalAuConfig);
    assertEquals("MyJournal Vol: 43 Range: a-d NumRange: 12-17 Set: red, green, blue",
		 cau.makeName());
  }

  public void testMakeRules(boolean includeStart) throws Exception {
    ConfigurationUtil.addFromArgs(DefinableArchivalUnit.PARAM_CRAWL_RULES_INCLUDE_START,
				  ""+includeStart);
    defMap.putCollection(DefinableArchivalUnit.KEY_AU_CRAWL_RULES, crawlRules);
    defMap.putString(ArchivalUnit.KEY_AU_START_URL,
		     "\"%svolume/%i.html\", base_url, volume");
    String permUrl = "http://other.host/permission.html";
    defMap.putString(DefinableArchivalUnit.KEY_AU_MANIFEST,
		     "\"" + permUrl + "\"");
    setupAu();

    Configuration auConf = ConfigManager.newConfiguration();
    auConf.put("base_url", "http://www.example.com/");
    auConf.put("volume", "43");
    cau.setConfiguration(auConf);

    SpiderCrawlSpec spec = (SpiderCrawlSpec)cau.getCrawlSpec();
    CrawlRule rule = spec.getCrawlRule();
    assertEquals(CrawlRule.INCLUDE,
                 rule.match("http://www.example.com/mygif.gif"));
    assertEquals(CrawlRule.INCLUDE,
                 rule.match("http://www.example.com/path/"));
    if (includeStart) {
      assertEquals(CrawlRule.INCLUDE,
		   rule.match("http://www.example.com/volume/43.html"));
      assertEquals(CrawlRule.INCLUDE, rule.match(permUrl));
    } else {
      assertEquals(CrawlRule.IGNORE,
		   rule.match("http://www.example.com/volume/43.html"));
      assertEquals(CrawlRule.IGNORE, rule.match(permUrl));
    }
  }

  public void testMakeRules() throws Exception {
    testMakeRules(false);
  }

  public void testMakeRulesWithImplicitStart() throws Exception {
    testMakeRules(true);
  }

  public void testMakeRulesIgnCase() throws Exception {
    additionalAuConfig.putString("base_url", "http://www.example.com/");
    defMap.putCollection(DefinableArchivalUnit.KEY_AU_CRAWL_RULES, crawlRules);
    defMap.putBoolean(DefinableArchivalUnit.KEY_AU_CRAWL_RULES_IGNORE_CASE,
		      true);
    setupAu(additionalAuConfig);

    CrawlRule rules = cau.makeRules();
    assertEquals(CrawlRule.INCLUDE,
                 rules.match("http://www.example.com/mygif.gif"));
    assertEquals(CrawlRule.INCLUDE,
                 rules.match("http://www.example.com/mygif.GIF"));
    assertEquals(CrawlRule.INCLUDE,
                 rules.match("http://www.EXAMPLE.com/mygif.GIF"));
    assertEquals(CrawlRule.INCLUDE,
                 rules.match("http://www.example.com/path/"));
  }

  public void testMakeRulesDontIgnCase() throws LockssRegexpException {
    additionalAuConfig.putString("base_url", "http://www.example.com/");
    defMap.putCollection(DefinableArchivalUnit.KEY_AU_CRAWL_RULES, crawlRules);
    defMap.putBoolean(DefinableArchivalUnit.KEY_AU_CRAWL_RULES_IGNORE_CASE,
		      false);
    setupAu(additionalAuConfig);

    CrawlRule rules = cau.makeRules();
    assertEquals(CrawlRule.INCLUDE,
                 rules.match("http://www.example.com/mygif.gif"));
    assertEquals(CrawlRule.IGNORE,
                 rules.match("http://www.example.com/mygif.GIF"));
    assertEquals(CrawlRule.IGNORE,
                 rules.match("http://www.EXAMPLE.com/mygif.GIF"));
    assertEquals(CrawlRule.INCLUDE,
                 rules.match("http://www.example.com/path/"));
  }

  public void testMakeStartUrl() throws Exception {
    additionalAuConfig.putInt("volume", 43);
    additionalAuConfig.putString("base_url", "http://www.example.com/");
    defMap.putString(ArchivalUnit.KEY_AU_START_URL,
		     "\"%slockss-volume/%d.html\", base_url, volume");
    setupAu(additionalAuConfig);

    assertEquals(ListUtil.list("http://www.example.com/lockss-volume/43.html"),
		 cau.makeStartUrls());
  }

  public void testMakeStartUrlList() throws Exception {
    additionalAuConfig.putInt("volume", 43);
    additionalAuConfig.putString("base_url", "http://www.example.com/");
    defMap.putCollection(ArchivalUnit.KEY_AU_START_URL,
			 ListUtil.list("\"%sl-volume/%d.html\", base_url, volume",
				       "\"%sunl-vol/%d.html\", base_url, volume"));
    setupAu(additionalAuConfig);

    assertEquals(ListUtil.list("http://www.example.com/l-volume/43.html",
			       "http://www.example.com/unl-vol/43.html"),
		 cau.makeStartUrls());
  }

  public void xxxxtestMakeStartUrlListWithSet() throws Exception {
    additionalAuConfig.putString("base_url", "http://www.example.com/");
    additionalAuConfig.putString("issue_set", "1, 2, 3, 3a");
    defMap.putCollection(ArchivalUnit.KEY_AU_START_URL,
			 ListUtil.list("\"%sfoo/\", base_url",
				       "\"%sbar/issue-%s\", base_url, issue_set"));

    assertEquals(ListUtil.list("http://www.example.com/l-volume/43.html",
			       "http://www.example.com/unl-vol/43.html"),
		 cau.makeStartUrls());
  }

  public void testMakeStartUrlListNoVal() throws Exception {
    configProps.add(ConfigParamDescr.JOURNAL_ID);
    additionalAuConfig.putInt("volume", 43);
    additionalAuConfig.putString("base_url", "http://www.example.com/");
    defMap.putCollection(ArchivalUnit.KEY_AU_START_URL,
			 ListUtil.list("\"%svolume/%d.html\", base_url, volume",
				       "\"%snoval-vol/%d.html\", base_url, journal_id"));
    setupAu(additionalAuConfig);

    assertEquals(ListUtil.list("http://www.example.com/volume/43.html"),
		 cau.makeStartUrls());
  }

  void setStdConfigProps() {
    Collection configProps = ListUtil.list(ConfigParamDescr.BASE_URL,
					   ConfigParamDescr.VOLUME_NUMBER);
    defMap.putCollection(DefinablePlugin.KEY_PLUGIN_CONFIG_PROPS,
			 configProps);
  }  

  public void testUserMessage() throws Exception {
    String str = "test user msg";
    setStdConfigProps();
    defMap.putString(DefinablePlugin.KEY_PLUGIN_AU_CONFIG_USER_MSG, str);
    defMap.putString(ArchivalUnit.KEY_AU_START_URL,
                  "\"%slockss-volume/%d.html\", base_url, volume");
    setupAu();
    Properties props = new Properties();
    props.put(ConfigParamDescr.BASE_URL.getKey(), "http://www.example.com/");
    props.put(ConfigParamDescr.VOLUME_NUMBER.getKey(), "42");
    cau.setConfiguration(ConfigurationUtil.fromProps(props));
    assertEquals(str, cau.getProperties().getString(DefinableArchivalUnit.KEY_AU_CONFIG_USER_MSG, null));
  }

  public void testGetManifestPage() throws Exception {
    String baseKey = ConfigParamDescr.BASE_URL.getKey();
    String volKey = ConfigParamDescr.VOLUME_NUMBER.getKey();
    setStdConfigProps();
    defMap.putString(DefinableArchivalUnit.KEY_AU_MANIFEST,
		     "\"%scontents-by-date.%d.shtml\", " +
		     baseKey + ", " + volKey);
    defMap.putString(ArchivalUnit.KEY_AU_START_URL,
                  "\"%slockss-volume/%d.html\", base_url, volume");
    setupAu();
    Properties props = new Properties();
    props.put(baseKey, "http://www.example.com/");
    props.put(volKey, "2003");
    cau.setConfiguration(ConfigurationUtil.fromProps(props));
    String expected = "http://www.example.com/contents-by-date.2003.shtml";
    assertEquals(ListUtil.list(expected), cau.getPermissionPages());
    assertSameElements(ListUtil.list("http://www.example.com/"),
		       cau.getUrlStems());
  }

  public void testGetMultiplePermissionPages() throws Exception {
    String baseKey = ConfigParamDescr.BASE_URL.getKey();
    String b2Key = ConfigParamDescr.BASE_URL2.getKey();
    configProps.add(ConfigParamDescr.BASE_URL2);
    defMap.putCollection(DefinableArchivalUnit.KEY_AU_MANIFEST,
			 ListUtil.list("\"%sfoo/\", " + baseKey,
				       "\"%sbar/\", " + b2Key));
    defMap.putString(ArchivalUnit.KEY_AU_START_URL,
                  "\"%slockss-volume\", base_url");
    setupAu();
    Properties props = new Properties();
    props.put(baseKey, "http://www.example.com/");
    props.put(b2Key, "http://mmm.example.org/");
    props.put("volume", "32");
    cau.setConfiguration(ConfigurationUtil.fromProps(props));
    String exp1 = "http://www.example.com/foo/";
    String exp2 = "http://mmm.example.org/bar/";
    assertEquals(ListUtil.list(exp1, exp2), cau.getPermissionPages());
    assertSameElements(ListUtil.list("http://www.example.com/",
			       "http://mmm.example.org/"),
		 cau.getUrlStems());
  }

  public void testGetLinkExtractor() {
    setupAu();
    // test we find the default
    LinkExtractor extractor;
    extractor = cau.getLinkExtractor("text/html");
    assertTrue(extractor instanceof GoslingHtmlLinkExtractor);

    // test we don't find one that doesn't exist
    extractor = cau.getLinkExtractor("text/ram");
    assertNull(extractor);

    // test we find one we've added
    defMap.putString("text/ram_link_extractor_factory",
		     "org.lockss.test.MockLinkExtractorFactory");
    cp.initMimeMap();
    setupAu();
    // hard to test wrapper here because getLinkExtractorFactory isn't exposed

    extractor = cau.getLinkExtractor("text/ram");
    assertTrue("extractor is " + extractor,
	       extractor instanceof MockLinkExtractor);
    extractor = cau.getLinkExtractor("text/ram;charset=oddly");
    assertTrue("extractor is " + extractor,
	       extractor instanceof MockLinkExtractor);
  }

  public void testGetCrawlRule() throws LockssRegexpException {
    setupAu();
    defMap.putString(DefinableArchivalUnit.KEY_AU_CRAWL_RULES,
 		  "org.lockss.plugin.definable.TestDefinableArchivalUnit$NegativeCrawlRuleFactory");

    CrawlRule rules = cau.makeRules();
    assertEquals(CrawlRule.EXCLUDE,
                 rules.match("http://www.example.com/mygif.gif"));
    assertEquals(CrawlRule.EXCLUDE,
                 rules.match("http://www.example.com/"));

    defMap.putString(DefinableArchivalUnit.KEY_AU_CRAWL_RULES,
		  "org.lockss.plugin.definable.TestDefinableArchivalUnit$PositiveCrawlRuleFactory");

    rules = cau.makeRules();
    assertEquals(CrawlRule.INCLUDE,
                 rules.match("http://www.example.com/mygif.gif"));
    assertEquals(CrawlRule.INCLUDE,
                 rules.match("http://www.example.com/"));
  }


  public static class NegativeCrawlRuleFactory
    implements CrawlRuleFromAuFactory {

    public CrawlRule createCrawlRule(ArchivalUnit au) {
      return new NegativeCrawlRule();
    }
  }

  public void testGetCrawlRuleThrowsOnBadClass() throws LockssRegexpException {
    setupAu();
    defMap.putString(DefinableArchivalUnit.KEY_AU_CRAWL_RULES,
		  "org.lockss.bogus.FakeClass");

    try {
      CrawlRule rules = cau.makeRules();
      fail("Should have thrown on a non-existant class");
    } catch (PluginException.InvalidDefinition e){
    }
  }


  public void testMakePermissionCheckersNone() {
    setupAu();
    PermissionChecker permissionChecker = cau.makePermissionChecker();
    assertNull(permissionChecker);
  }

  /**
   * Verify that the permission checker returned by makePermissionChecker
   * is the same class that the factory would make
   */
  public void testMakePermissionCheckers() {
    setupAu();
    defMap.putString(DefinableArchivalUnit.KEY_AU_PERMISSION_CHECKER_FACTORY,
 		  "org.lockss.plugin.definable.TestDefinableArchivalUnit$MyPermissionCheckerFactory");
    PermissionChecker permissionChecker = cau.makePermissionChecker();
    assertTrue(permissionChecker instanceof MockPermissionChecker);
  }

  public static class MyPermissionCheckerFactory
    implements PermissionCheckerFactory {
    public List createPermissionCheckers(ArchivalUnit au) {
      List checkers = new ArrayList();
      checkers.add(new MockPermissionChecker());
      return checkers;
    }
  }

  private static class MockPermissionChecker implements PermissionChecker {
    public boolean checkPermission(Crawler.PermissionHelper pHelper,
				   Reader inputReader, String url) {
      throw new UnsupportedOperationException("not implemented");
    }
  }

  public void testMakeLoginPageChecker() {
    setupAu();
    assertNull(cau.makeLoginPageChecker());
    defMap.putString(DefinableArchivalUnit.KEY_AU_LOGIN_PAGE_CHECKER,
 		  "org.lockss.plugin.definable.TestDefinableArchivalUnit$MyLoginPageChecker");
    LoginPageChecker lpc = cau.makeLoginPageChecker();
    assertTrue(lpc instanceof LoginPageCheckerWrapper);
    assertTrue(WrapperUtil.unwrap(lpc) instanceof MyLoginPageChecker);
  }

  public static class MyLoginPageChecker implements LoginPageChecker {
    public boolean isLoginPage(Properties props, Reader reader) {
      throw new UnsupportedOperationException("not implemented");
    }
  }

  public void testIsLoginPageUrl() throws ArchivalUnit.ConfigurationException {
    setupAu();
    String pat = "\"%s.*\\?.*\\blogin=yes\\b.*\", base_url";
    defMap.putString(DefinableArchivalUnit.KEY_AU_REDIRECT_TO_LOGIN_URL_PATTERN, pat);
    defMap.putString(ArchivalUnit.KEY_AU_START_URL,
                  "\"%slockss-volume/%d.html\", base_url, volume");
    Configuration config =
      ConfigurationUtil.fromArgs(ConfigParamDescr.BASE_URL.getKey(),
				 "http://example.com/",
				 ConfigParamDescr.VOLUME_NUMBER.getKey(),
				 "42");
    cau.setConfiguration(config);

    assertFalse(cau.isLoginPageUrl("http://example.com/baz/"));
    assertTrue(cau.isLoginPageUrl("http://example.com/baz?login=yes"));
    assertTrue(cau.isLoginPageUrl("http://example.com/baz?a=b&login=yes"));
    assertTrue(cau.isLoginPageUrl("http://example.com/baz?login=yes&b=a"));
    assertFalse(cau.isLoginPageUrl("http://example.com/baz?xlogin=yes&b=a"));
    assertFalse(cau.isLoginPageUrl("http://example.com/baz?login=yesy&b=a"));
  }

  public void testIsLoginPageUrlWithBlank()
      throws ArchivalUnit.ConfigurationException {
    setupAu();
    String pat = "\"%s.*\\?.*\\blogin=yes\\b.*\", base_url";
    defMap.putString(DefinableArchivalUnit.KEY_AU_REDIRECT_TO_LOGIN_URL_PATTERN, pat);
    defMap.putString(ArchivalUnit.KEY_AU_START_URL,
                  "\"%slockss-volume/%d.html\", base_url, volume");
    Configuration config =
      ConfigurationUtil.fromArgs(ConfigParamDescr.BASE_URL.getKey(),
				 "http://example.com/dir name",
				 ConfigParamDescr.VOLUME_NUMBER.getKey(),
				 "42");
    cau.setConfiguration(config);

    assertFalse(cau.isLoginPageUrl("http://example.com/dir name/baz/"));
    assertTrue(cau.isLoginPageUrl("http://example.com/dir name/baz?login=yes"));
    assertTrue(cau.isLoginPageUrl("http://example.com/dir+name/baz?login=yes"));
    assertTrue(cau.isLoginPageUrl("http://example.com/dir%20name/baz?login=yes"));
    assertTrue(cau.isLoginPageUrl("http://example.com/dir name/baz?a=b&login=yes"));
    assertTrue(cau.isLoginPageUrl("http://example.com/dir+name/baz?a=b&login=yes"));
    assertTrue(cau.isLoginPageUrl("http://example.com/dir%20name/baz?login=yes&b=a"));
    assertFalse(cau.isLoginPageUrl("http://example.com/dir name/baz?xlogin=yes&b=a"));
    assertFalse(cau.isLoginPageUrl("http://example.com/dir name/baz?login=yesy&b=a"));
  }

  public void testIsLoginPageUrlBadPat()
      throws ArchivalUnit.ConfigurationException {
    setupAu();
    String pat = "\"%s.*\\?.*\\mal[formed_pattern\\b.*\", base_url";
    defMap.putString(DefinableArchivalUnit.KEY_AU_REDIRECT_TO_LOGIN_URL_PATTERN, pat);
    Configuration config =
      ConfigurationUtil.fromArgs(ConfigParamDescr.BASE_URL.getKey(),
				 "http://example.com/",
				 ConfigParamDescr.VOLUME_NUMBER.getKey(),
				 "42");
    try {
      cau.setConfiguration(config);
      cau.addImpliedConfigParams();
    } catch (ArchivalUnit.ConfigurationException e) {
      assertMatchesRE("Can't compile URL pattern", e.getMessage());
    }
  }

  public void testMakeNonSubstanceUrlPatterns() throws Exception {
    defMap.putString(DefinableArchivalUnit.KEY_AU_NON_SUBSTANCE_URL_PATTERN,
		     "\"vol%d/images/\",volume");
    additionalAuConfig.putInt("volume", 43);
    setupAu(additionalAuConfig);

    assertNull(cau.makeSubstanceUrlPatterns());
    assertIsomorphic(ListUtil.list("vol43/images/"),
		     RegexpUtil.regexpCollection(cau.makeNonSubstanceUrlPatterns()));
  }

  public void testMakeSubstanceUrlPatterns() throws Exception {
    defMap.putCollection(DefinableArchivalUnit.KEY_AU_SUBSTANCE_URL_PATTERN,
			 ListUtil.list("\"vol%d/pdf/\",volume",
				       "\"%s/toc\",base_url"));
    additionalAuConfig.putInt("volume", 47);
    additionalAuConfig.putString("base_url", "http://si.te/path/");
    setupAu(additionalAuConfig);

    assertNull(cau.makeNonSubstanceUrlPatterns());
    assertIsomorphic(ListUtil.list("vol47/pdf/",
				   "http\\:\\/\\/si\\.te\\/path\\//toc"),
		     RegexpUtil.regexpCollection(cau.makeSubstanceUrlPatterns()));
  }

  /*
  public void testMakeCrawlSpec() throws Exception {
    setupAu();
    Properties props = new Properties();
//      props.setProperty(ConfigParamDescr.BASE_URL.getKey(), baseUrl);
//      props.setProperty(ConfigParamDescr.VOLUME_NUMBER.getKey(), "10");
//      props.setProperty(BaseArchivalUnit.PAUSE_TIME_KEY, "10000");
//      props.setProperty(BaseArchivalUnit.USE_CRAWL_WINDOW, "true");
//      props.setProperty(BaseArchivalUnit.NEW_CONTENT_CRAWL_KEY, "10000");
//      Configuration config = ConfigurationUtil.fromProps(props);

     cau.setConfiguration(ConfigurationUtil.fromProps(props));

     defMap.putString("plugin_crawl_type", "HTML Links");
     defMap.putString("au_manifest", "http://www.example.com");
//      defMap.putString("au_start_url", "http://www.example.com");
//     cau = new DefinableArchivalUnit(cp, defMap);

    CrawlSpec spec = cau.makeCrawlSpec();
    assertNotNull("makeCrawlSpec returned a null crawl spec", spec);
    assertTrue("makeCrawlSpec should have returned a SpiderCrawlSpec",
	       spec instanceof SpiderCrawlSpec);
  }
  */
  public void testGetFilterRule() {
    setupAu();
    assertNull(cau.getFilterRule(null));
  }

  public void testGetFilterRuleMimeType() {
    setupAu();
    defMap.putString("text/html"+DefinableArchivalUnit.SUFFIX_FILTER_RULE,
		     "org.lockss.plugin.definable.TestDefinableArchivalUnit$MyMockFilterRule");
    FilterRule rule = cau.getFilterRule("text/html");
    assertTrue(rule instanceof FilterRuleWrapper);
    assertTrue(WrapperUtil.unwrap(rule) instanceof MyMockFilterRule);
  }

  public void testGetFilterRuleMimeTypeSpace() {
    setupAu();
    defMap.putString("text/html"+DefinableArchivalUnit.SUFFIX_FILTER_RULE,
		     "org.lockss.plugin.definable.TestDefinableArchivalUnit$MyMockFilterRule");
    FilterRule rule = cau.getFilterRule(" text/html ");
    assertTrue(rule instanceof FilterRuleWrapper);
    assertTrue(WrapperUtil.unwrap(rule) instanceof MyMockFilterRule);
  }

  public void testGetFilterRuleContentType() {
    setupAu();
    defMap.putString("text/html"+DefinableArchivalUnit.SUFFIX_FILTER_RULE,
		     "org.lockss.plugin.definable.TestDefinableArchivalUnit$MyMockFilterRule");
    FilterRule rule = cau.getFilterRule("text/html ; random-char-set");
    assertTrue(rule instanceof FilterRuleWrapper);
    assertTrue(WrapperUtil.unwrap(rule) instanceof MyMockFilterRule);
  }

  public void testGetFilterRuleContentTypeSpace() {
    setupAu();
    defMap.putString("text/html"+DefinableArchivalUnit.SUFFIX_FILTER_RULE,
		     "org.lockss.plugin.definable.TestDefinableArchivalUnit$MyMockFilterRule");
    FilterRule rule = cau.getFilterRule(" text/html ; random-char-set");
    assertTrue(rule instanceof FilterRuleWrapper);
    assertTrue(WrapperUtil.unwrap(rule) instanceof MyMockFilterRule);
  }

  public void testGetHashFilterFactoryMimeType() throws Exception {
    setupAu();
    defMap.putString("text/html"+DefinableArchivalUnit.SUFFIX_HASH_FILTER_FACTORY,
		     "org.lockss.plugin.definable.TestDefinableArchivalUnit$MyMockFilterFactory");
    cp.initPlugin(getMockLockssDaemon(), defMap);
    FilterFactory fact = cau.getHashFilterFactory("text/html");
    assertTrue(fact instanceof FilterFactoryWrapper);
    assertTrue(WrapperUtil.unwrap(fact) instanceof MyMockFilterFactory);
  }

  public void testGetHashFilterFactoryMimeTypeSpace() throws Exception {
    setupAu();
    defMap.putString("text/html"+DefinableArchivalUnit.SUFFIX_HASH_FILTER_FACTORY,
		     "org.lockss.plugin.definable.TestDefinableArchivalUnit$MyMockFilterFactory");
    cp.initPlugin(getMockLockssDaemon(), defMap);
    FilterFactory fact = cau.getHashFilterFactory(" text/html ");
    assertTrue(fact instanceof FilterFactoryWrapper);
    assertTrue(WrapperUtil.unwrap(fact) instanceof MyMockFilterFactory);
  }

  public void testGetHashFilterFactoryContentType() throws Exception {
    setupAu();
    defMap.putString("text/html"+DefinableArchivalUnit.SUFFIX_HASH_FILTER_FACTORY,
		     "org.lockss.plugin.definable.TestDefinableArchivalUnit$MyMockFilterFactory");
    cp.initPlugin(getMockLockssDaemon(), defMap);
    FilterFactory fact = cau.getHashFilterFactory("text/html ; random-char-set");
    assertTrue(fact instanceof FilterFactoryWrapper);
    assertTrue(WrapperUtil.unwrap(fact) instanceof MyMockFilterFactory);
  }

  public void testGetHashFilterFactoryContentTypeSpace() throws Exception {
    setupAu();
    defMap.putString("text/html"+DefinableArchivalUnit.SUFFIX_HASH_FILTER_FACTORY,
		     "org.lockss.plugin.definable.TestDefinableArchivalUnit$MyMockFilterFactory");
    cp.initPlugin(getMockLockssDaemon(), defMap);
    FilterFactory fact = cau.getHashFilterFactory(" text/html ; random-char-set");
    assertTrue(fact instanceof FilterFactoryWrapper);
    assertTrue(WrapperUtil.unwrap(fact) instanceof MyMockFilterFactory);
  }

  public void testGetCrawlFilterFactoryMimeType() throws Exception {
    setupAu();
    defMap.putString("text/html"+DefinableArchivalUnit.SUFFIX_CRAWL_FILTER_FACTORY,
		     "org.lockss.plugin.definable.TestDefinableArchivalUnit$MyMockFilterFactory");
    cp.initPlugin(getMockLockssDaemon(), defMap);
    FilterFactory fact = cau.getCrawlFilterFactory("text/html");
    assertTrue(fact instanceof FilterFactoryWrapper);
    assertTrue(WrapperUtil.unwrap(fact) instanceof MyMockFilterFactory);
  }

  public void testGetCrawlFilterFactoryMimeTypeSpace() throws Exception {
    setupAu();
    defMap.putString("text/html"+DefinableArchivalUnit.SUFFIX_CRAWL_FILTER_FACTORY,
		     "org.lockss.plugin.definable.TestDefinableArchivalUnit$MyMockFilterFactory");
    cp.initPlugin(getMockLockssDaemon(), defMap);
    FilterFactory fact = cau.getCrawlFilterFactory(" text/html ");
    assertTrue(fact instanceof FilterFactoryWrapper);
    assertTrue(WrapperUtil.unwrap(fact) instanceof MyMockFilterFactory);
  }

  public void testGetCrawlFilterFactoryContentType() throws Exception {
    setupAu();
    defMap.putString("text/html"+DefinableArchivalUnit.SUFFIX_CRAWL_FILTER_FACTORY,
		     "org.lockss.plugin.definable.TestDefinableArchivalUnit$MyMockFilterFactory");
    cp.initPlugin(getMockLockssDaemon(), defMap);
    FilterFactory fact = cau.getCrawlFilterFactory("text/html ; random-char-set");
    assertTrue(fact instanceof FilterFactoryWrapper);
    assertTrue(WrapperUtil.unwrap(fact) instanceof MyMockFilterFactory);
  }

  public void testGetCrawlFilterFactoryContentTypeSpace() throws Exception {
    setupAu();
    defMap.putString("text/html"+DefinableArchivalUnit.SUFFIX_CRAWL_FILTER_FACTORY,
		     "org.lockss.plugin.definable.TestDefinableArchivalUnit$MyMockFilterFactory");
    cp.initPlugin(getMockLockssDaemon(), defMap);
    FilterFactory fact = cau.getCrawlFilterFactory(" text/html ; random-char-set");
    assertTrue(fact instanceof FilterFactoryWrapper);
    assertTrue(WrapperUtil.unwrap(fact) instanceof MyMockFilterFactory);
  }

  CrawlRule makeExpRule() {
    try {
      CrawlRules.RE expRules[] = {
	new CrawlRules.RE("^(http\\:\\/\\/base\\.foo\\/base_path\\/|http\\:\\/\\/resolv\\.er\\/path\\/)", 4),
	new CrawlRules.RE("^http\\:\\/\\/base\\.foo\\/base_path\\/publishing/journals/lockss/\\?journalcode=J47&year=1984", 1),
	new CrawlRules.RE("^http\\:\\/\\/resolv\\.er\\/path\\/\\?DOI=", 1),
	new CrawlRules.RE("^http\\:\\/\\/base\\.foo\\/base_path\\/errorpage\\.asp", 2),
	new CrawlRules.RE("^http\\:\\/\\/base\\.foo\\/base_path\\/host/base\\.foo", 2),

	new CrawlRules.RE("^http\\:\\/\\/base\\.foo\\/base_path\\/publishing/journals/J47/article\\.asp\\?Type=Issue&VolumeYear=1984&JournalCode=J47", 1),
	new CrawlRules.RE("^http\\:\\/\\/base\\.foo\\/base_path\\/.*\\.(bmp|css|ico|gif|jpe?g|js|mol|png|tif?f)$", 1),
	new CrawlRules.RE("http\\:\\/\\/base\\.foo\\/base_path\\/issueset/issue-(?:1|2|3|3a)/.*",
				  DefinableArchivalUnit.DEFAULT_CRAWL_RULES_IGNORE_CASE,
				  1),
	new CrawlRules.REMatchRange("http\\:\\/\\/base\\.foo\\/base_path\\/issuerange/issue-(\\d+)/.*",
				    1, 3, 7),
      };
      return new CrawlRules.FirstMatch(ListUtil.fromArray(expRules));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  MyDefinablePlugin loadLargePlugin() {
    MyPluginManager pmgr = new MyPluginManager();
    getMockLockssDaemon().setPluginManager(pmgr);
    pmgr.initService(getMockLockssDaemon());

    // Load a complex plugin definition
    String pname = "org.lockss.plugin.definable.LargeTestPlugin";
    String key = PluginManager.pluginKeyFromId(pname);
    assertTrue("Plugin was not successfully loaded",
	       pmgr.ensurePluginLoaded(key));
    Plugin plug = pmgr.getPlugin(key);
    assertTrue(plug.toString() + " not a DefinablePlugin",
	       plug instanceof DefinablePlugin);
    return (MyDefinablePlugin)plug;
  }

  public void testLargePlugin() throws Exception {
    ConfigurationUtil.addFromArgs(DefinableArchivalUnit.PARAM_CRAWL_RULES_INCLUDE_START,
				  "false");

    MyDefinablePlugin defplug = loadLargePlugin();

    // Configure and create an AU
    Properties p = new Properties();
    p.put("base_url", "http://base.foo/base_path/");
    p.put("resolver_url", "http://resolv.er/path/");
    p.put("journal_code", "J47");
    p.put("year", "1984");
    p.put("issue_set", "1,2,3,3a");
    p.put("num_issue_range", "3-7");
    Configuration auConfig = ConfigManager.fromProperties(p);
    DefinableArchivalUnit au =
      (DefinableArchivalUnit)defplug.createAu(auConfig);

    assertSame(defplug, au.getPlugin());

    // Test that the AU does everything correctly

    assertEquals(auConfig, au.getConfiguration());
    // normalize
    SpiderCrawlSpec cspec = (SpiderCrawlSpec)au.getCrawlSpec();
    assertEquals(3, cspec.getRefetchDepth());
    assertEquals(null, cspec.getPermissionChecker());
    assertEquals(null, cspec.getExploderPattern());
    assertEquals(null, cspec.getExploderHelper());
    assertEquals(makeExpRule(), cspec.getCrawlRule());
    
    assertEquals(ListUtil.list("http://base.foo/base_path/publishing/journals/lockss/?journalcode=J47&year=1984",
			       "http://resolv.er/path/lockss.htm",
			       "http://resolv.er/path//issue-3/issue.htm",
			       "http://resolv.er/path//issue-4/issue.htm",
			       "http://resolv.er/path//issue-5/issue.htm",
			       "http://resolv.er/path//issue-6/issue.htm",
			       "http://resolv.er/path//issue-7/issue.htm"),
		 cspec.getPermissionPages());

    List expStartUrls =
      ListUtil.list("http://base.foo/base_path/publishing/journals/lockss/?journalcode=J47&year=1984",
		    "http://base.foo/base_path/issuestart/issue-1/",
		    "http://base.foo/base_path/issuestart/issue-2/",
		    "http://base.foo/base_path/issuestart/issue-3/",
		    "http://base.foo/base_path/issuestart/issue-3a/");
    assertEquals(expStartUrls, cspec.getStartingUrls());
    assertEquals(expStartUrls, au.getNewContentCrawlUrls());
    assertEquals("Large Plugin AU, Base URL http://base.foo/base_path/, Resolver URL http://resolv.er/path/, Journal Code J47, Year 1984, Issues 1, 2, 3, 3a, Range 3-7",
		 au.makeName());

    assertEquals("application/pdf", defplug.getDefaultArticleMimeType());

    ArticleIteratorFactory afact = defplug.getArticleIteratorFactory();
    assertTrue(afact instanceof ArticleIteratorFactoryWrapper);
    assertTrue(""+WrapperUtil.unwrap(afact),
	       WrapperUtil.unwrap(afact) instanceof MockFactories.ArtIterFact);
    assertEquals(CollectionUtil.EMPTY_ITERATOR, au.getArticleIterator());

    assertNull(au.getFileMetadataExtractor(MetadataTarget.Any,
					   "application/pdf"));
    FileMetadataExtractor ext =
      au.getFileMetadataExtractor(MetadataTarget.Any, "text/xml");
    assertTrue(ext instanceof FileMetadataExtractorWrapper);
    assertTrue(""+WrapperUtil.unwrap(ext),
	       WrapperUtil.unwrap(ext) instanceof MockFactories.XmlMetaExt);

    MimeTypeInfo mti = defplug.getMimeTypeInfo("text/xml");
    FileMetadataExtractorFactory mfact = mti.getFileMetadataExtractorFactory();
    assertTrue(mfact instanceof FileMetadataExtractorFactoryWrapper);
    assertTrue(""+WrapperUtil.unwrap(mfact),
	       WrapperUtil.unwrap(mfact) instanceof MockFactories.XmlMetaExtFact);
    FileMetadataExtractorFactory mfact2 =
      mti.getFileMetadataExtractorFactory("DublinCore");
    assertTrue(""+WrapperUtil.unwrap(mfact2),
	       WrapperUtil.unwrap(mfact2) instanceof MockFactories.XmlMetaExtFact);
    FileMetadataExtractorFactory mfact3 =
      mti.getFileMetadataExtractorFactory("DublinRind");
    assertTrue(""+WrapperUtil.unwrap(mfact3),
	       WrapperUtil.unwrap(mfact3) instanceof MockFactories.XmlRindMetaExtFact);

    CrawlWindow window = cspec.getCrawlWindow();
    CrawlWindows.Interval intr = (CrawlWindows.Interval)window;
    assertNotNull(window);
    long testTime = new Date("Jan 1 2010, 11:59:00 EST").getTime();
    for (int ix = 0; ix <= 24; ix++) {
      boolean isOpen = window.canCrawl(new Date(testTime));
      if (ix <= 12 || ix >= 19) {
	assertFalse("Window should be closed at ix " + ix, isOpen);
      } else {
	assertTrue("Window should be open at ix " + ix, isOpen);
      }
      testTime += Constants.HOUR;
    }

    CacheResultMap resultMap = defplug.getCacheResultMap();
    assertClass(CacheException.NoRetryDeadLinkException.class,
		getHttpResultMap(defplug).mapException(null, null, 404, null));
    assertClass(CacheException.PermissionException.class,
		getHttpResultMap(defplug).mapException(null, null, 300, null));
    assertClass(CacheException.RetryableNetworkException_5_60S.class,
		getHttpResultMap(defplug).mapException(null, null, 500, null));
    assertClass(CacheException.RetryableNetworkException_5_30S.class,
		getHttpResultMap(defplug).mapException(null, null,
						       new IOException("foo"),
						       null));
    CacheException ex =
      getHttpResultMap(defplug).mapException(null, null, 522, null);
    assertClass(CacheException.RetryDeadLinkException.class, ex);
    assertEquals("522 from handler", ex.getMessage());
  }

  public void testFeatureUrls() throws Exception {
    ConfigurationUtil.addFromArgs(DefinableArchivalUnit.PARAM_CRAWL_RULES_INCLUDE_START,
				  "false");

    MyDefinablePlugin defplug = loadLargePlugin();
    // Configure and create an AU
    Properties p = new Properties();
    p.put("base_url", "http://base.foo/base_path/");
    p.put("resolver_url", "http://resolv.er/path/");
    p.put("journal_code", "J47");
    p.put("year", "1984");
//     p.put("issue_set", "1,2,3,3a");
    p.put("issue_set", "1,2");
    p.put("num_issue_range", "3-3");
    Configuration auConfig = ConfigManager.fromProperties(p);
    DefinableArchivalUnit au =
      (DefinableArchivalUnit)defplug.createAu(auConfig);

    assertEmpty(au.getAuFeatureUrls("wrong_key"));
    assertEquals(ListUtil.list("http://base.foo/base_path/?issue=3"),
		 au.getAuFeatureUrls("au_feat_single"));
    assertEquals(ListUtil.list("http://base.foo/base_path/?issue=3",
			       "http://base.foo/base_path/foo",
			       "http://base.foo/base_path/set/1",
			       "http://base.foo/base_path/set/2"),
		 au.getAuFeatureUrls("au_feat_list"));
    assertEquals(ListUtil.list("http://base.foo/base_path/222",
			       "http://base.foo/base_path/333/1",
			       "http://base.foo/base_path/333/2"),
		 au.getAuFeatureUrls("au_feat_map"));

    // Set selector attr in tdb to key1
    MyDefinableArchivalUnit au2 =
      (MyDefinableArchivalUnit)defplug.createAu(auConfig);
    TitleConfig tc = new TitleConfig("Foo", defplug);
    tc.setAttributes(MapUtil.map("au_feature_key", "key1"));
    au2.setTitleConfig(tc);
    assertEquals(ListUtil.list("http://base.foo/base_path/bar"),
		 au2.getAuFeatureUrls("au_feat_map"));

    // Set selector attr in tdb to unknown key, should select * entry
    MyDefinableArchivalUnit au3 =
      (MyDefinableArchivalUnit)defplug.createAu(auConfig);
    TitleConfig tc2 = new TitleConfig("Foo", defplug);
    tc2.setAttributes(MapUtil.map("au_feature_key", "notkey17"));
    au3.setTitleConfig(tc2);
    assertEquals(ListUtil.list("http://base.foo/base_path/222",
			       "http://base.foo/base_path/333/1",
			       "http://base.foo/base_path/333/2"),
		 au3.getAuFeatureUrls("au_feat_map"));
  }

  public void testOaiDefaultDC() throws Exception {
    MyPluginManager pmgr = new MyPluginManager();
    getMockLockssDaemon().setPluginManager(pmgr);
    pmgr.initService(getMockLockssDaemon());

    // Load an OAI plugin definition
    String pname = "org.lockss.plugin.definable.OaiTestPlugin1";
    String key = PluginManager.pluginKeyFromId(pname);
    assertTrue("Plugin was not successfully loaded",
	       pmgr.ensurePluginLoaded(key));
    Plugin plug = pmgr.getPlugin(key);
    assertTrue(plug.toString() + " not a DefinablePlugin",
	       plug instanceof DefinablePlugin);
    MyDefinablePlugin defplug = (MyDefinablePlugin)plug;

    // Configure and create an AU
    Properties p = new Properties();
    p.put("base_url", "http://base.foo/base_path/");
    p.put("resolver_url", "http://resolv.er/path/");
    p.put("oai_request_url", "http://oai.meta/path/");
    p.put("journal_code", "J47");
    p.put("year", "1984");
    p.put("issue_set", "1,2,3,3a");
    p.put("num_issue_range", "3-7");
    p.put("oai_spec", "http://oai.spec/specspec");
    Configuration auConfig = ConfigManager.fromProperties(p);
    DefinableArchivalUnit au = (DefinableArchivalUnit)plug.createAu(auConfig);

    assertSame(plug, au.getPlugin());

    assertTrue(au.getCrawlSpec() instanceof OaiCrawlSpec);
    OaiCrawlSpec cspec = (OaiCrawlSpec)au.getCrawlSpec();
    OaiRequestData reqdata = cspec.getOaiRequestData();
    assertEquals("http://oai.meta/path/", reqdata.getOaiRequestHandlerUrl());
    assertEquals("http://purl.org/dc/elements/1.1/", reqdata.getMetadataNamespaceUrl());
    assertEquals("identifier", reqdata.getUrlContainerTagName());
    assertEquals("http://oai.spec/specspec", reqdata.getAuSetSpec());
    assertEquals("oai_dc", reqdata.getMetadataPrefix());

    // XXX more tests when OaiRequestData and handler creation driven by
    // plugin.
  }

  HttpResultMap getHttpResultMap(DefinablePlugin plugin) {
    return (HttpResultMap)plugin.getCacheResultMap();
  }

  public void testRateLimiterSourceDefault() throws Exception {
    ConfigurationUtil.addFromArgs(BaseArchivalUnit.PARAM_DEFAULT_FETCH_RATE_LIMITER_SOURCE,
				  "plugin");
    PluginManager pmgr = getMockLockssDaemon().getPluginManager();
    // Load a plugin definition that doesn't set the limiter source
    String pname = "org.lockss.plugin.definable.GoodPlugin";
    String key = PluginManager.pluginKeyFromId(pname);
    assertTrue("Plugin was not successfully loaded",
	       pmgr.ensurePluginLoaded(key));
    Plugin plug = pmgr.getPlugin(key);
    assertTrue(plug instanceof DefinablePlugin);
    Properties p = new Properties();
    p.put("base_url", "http://base.foo/base_path/");
    p.put("num_issue_range", "3-7");
    Configuration auConfig = ConfigManager.fromProperties(p);
    DefinableArchivalUnit au = (DefinableArchivalUnit)plug.createAu(auConfig);

    assertEquals("plugin", au.getFetchRateLimiterSource());
    assertEquals(pname, au.getFetchRateLimiterKey());

    ConfigurationUtil.addFromArgs(BaseArchivalUnit.PARAM_DEFAULT_FETCH_RATE_LIMITER_SOURCE,
				  "au");
    assertEquals("au", au.getFetchRateLimiterSource());
    assertEquals(null, au.getFetchRateLimiterKey());

    ConfigurationUtil.addFromArgs(BaseArchivalUnit.PARAM_OVERRIDE_FETCH_RATE_LIMITER_SOURCE,
				  "foo");
    assertEquals("foo", au.getFetchRateLimiterSource());
  }

  public void testRateLimiterSourceAu() throws Exception {
    PluginManager pmgr = getMockLockssDaemon().getPluginManager();
    // Load a complex plugin definition
    String pname = "org.lockss.plugin.definable.RateLimiterSource1";
    String key = PluginManager.pluginKeyFromId(pname);
    assertTrue("Plugin was not successfully loaded",
	       pmgr.ensurePluginLoaded(key));
    Plugin plug = pmgr.getPlugin(key);
    assertTrue(plug instanceof DefinablePlugin);
    Properties p = new Properties();
    p.put("base_url", "http://base.foo/base_path/");
    p.put("num_issue_range", "3-7");
    Configuration auConfig = ConfigManager.fromProperties(p);
    DefinableArchivalUnit au = (DefinableArchivalUnit)plug.createAu(auConfig);

    assertEquals("au", au.getFetchRateLimiterSource());
    assertEquals(null, au.getFetchRateLimiterKey());

    ConfigurationUtil.addFromArgs(BaseArchivalUnit.PARAM_OVERRIDE_FETCH_RATE_LIMITER_SOURCE,
				  "foo");
    assertEquals("foo", au.getFetchRateLimiterSource());
  }

  public void testRateLimiterSourcePlugin() throws Exception {
    PluginManager pmgr = getMockLockssDaemon().getPluginManager();
    // Load a complex plugin definition
    String pname = "org.lockss.plugin.definable.RateLimiterSource2";
    String key = PluginManager.pluginKeyFromId(pname);
    assertTrue("Plugin was not successfully loaded",
	       pmgr.ensurePluginLoaded(key));
    Plugin plug = pmgr.getPlugin(key);
    assertTrue(plug instanceof DefinablePlugin);
    Properties p = new Properties();
    p.put("base_url", "http://base.foo/base_path/");
    p.put("num_issue_range", "3-7");
    Configuration auConfig = ConfigManager.fromProperties(p);
    DefinableArchivalUnit au = (DefinableArchivalUnit)plug.createAu(auConfig);

    assertEquals("plugin", au.getFetchRateLimiterSource());
    assertEquals(pname, au.getFetchRateLimiterKey());

    ConfigurationUtil.addFromArgs(BaseArchivalUnit.PARAM_OVERRIDE_FETCH_RATE_LIMITER_SOURCE,
				  "au");
    assertEquals("au", au.getFetchRateLimiterSource());
  }

  public void testRateLimiterSourceKey() throws Exception {
    PluginManager pmgr = getMockLockssDaemon().getPluginManager();
    // Load a complex plugin definition
    String pname = "org.lockss.plugin.definable.RateLimiterSource3";
    String key = PluginManager.pluginKeyFromId(pname);
    assertTrue("Plugin was not successfully loaded",
	       pmgr.ensurePluginLoaded(key));
    Plugin plug = pmgr.getPlugin(key);
    assertTrue(plug instanceof DefinablePlugin);
    Properties p = new Properties();
    p.put("base_url", "http://base.foo/base_path/");
    p.put("num_issue_range", "3-7");
    Configuration auConfig = ConfigManager.fromProperties(p);
    DefinableArchivalUnit au = (DefinableArchivalUnit)plug.createAu(auConfig);

    String limiterKey = "org.lockss.rateKey.SharedLimiter";
    assertEquals("key:" + limiterKey, au.getFetchRateLimiterSource());
    assertEquals(limiterKey, au.getFetchRateLimiterKey());
  }

  public void testRateLimiterSourceHost() throws Exception {
    PluginManager pmgr = getMockLockssDaemon().getPluginManager();
    // Load a complex plugin definition
    String pname = "org.lockss.plugin.definable.RateLimiterSource4";
    String key = PluginManager.pluginKeyFromId(pname);
    assertTrue(pmgr.ensurePluginLoaded(key));
    Plugin plug = pmgr.getPlugin(key);
    assertTrue(plug instanceof DefinablePlugin);
    Properties p = new Properties();
    p.put("base_url", "http://base.foo/base_path/");
    p.put("num_issue_range", "3-7");
    Configuration auConfig = ConfigManager.fromProperties(p);
    DefinableArchivalUnit au = (DefinableArchivalUnit)plug.createAu(auConfig);

    assertEquals("host:base_url", au.getFetchRateLimiterSource());
    assertEquals("host:base.foo", au.getFetchRateLimiterKey());
  }

  public static class PositiveCrawlRuleFactory
    implements CrawlRuleFromAuFactory {

    public CrawlRule createCrawlRule(ArchivalUnit au) {
      return new PositiveCrawlRule();
    }
  }

  public static class MyMockFilterRule implements FilterRule {
    public Reader createFilteredReader(Reader reader) {
      throw new UnsupportedOperationException("not implemented");
    }

  }

  public static class MyMockFilterFactory implements FilterFactory {
    public InputStream createFilteredInputStream(ArchivalUnit au,
						 InputStream in,
						 String encoding) {
      throw new UnsupportedOperationException("not implemented");
    }
  }

  public static class MyDefinablePlugin extends DefinablePlugin {
    public MimeTypeInfo getMimeTypeInfo(String contentType) {
      return super.getMimeTypeInfo(contentType);
    }

    protected ArchivalUnit createAu0(Configuration auConfig)
	throws ArchivalUnit.ConfigurationException {
      DefinableArchivalUnit au =
	new MyDefinableArchivalUnit(this, definitionMap);
      au.setConfiguration(auConfig);
      return au;
    }
  }

  public static class MyDefinableArchivalUnit extends DefinableArchivalUnit {
    protected MyDefinableArchivalUnit(DefinablePlugin myPlugin,
				      ExternalizableMap definitionMap) {
      super(myPlugin, definitionMap);
    }

    public void setTitleConfig(TitleConfig tc) {
      titleConfig = tc;
    }
  }

  public static class MyPluginManager extends PluginManager {
    protected String getConfigurablePluginName(String pluginName) {
      return MyDefinablePlugin.class.getName();
    }
  }
}
