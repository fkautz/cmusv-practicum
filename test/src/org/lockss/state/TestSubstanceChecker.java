/*
 * $Id$
 */

/*

Copyright (c) 2000-2010 Board of Trustees of Leland Stanford Jr. University,
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


package org.lockss.state;

import java.util.*;
import org.apache.oro.text.regex.*;

import org.lockss.test.*;
import org.lockss.util.*;
import org.lockss.daemon.*;
import org.lockss.crawler.*;
import org.lockss.plugin.*;

import static org.lockss.state.SubstanceChecker.State;

public class TestSubstanceChecker extends LockssTestCase {

  List PERMS = ListUtil.list("http://perm/");
  List STARTS = ListUtil.list("http://start/");

  MockArchivalUnit mau;
  SubstanceChecker checker;
  CrawlSpec spec1 = new SpiderCrawlSpec(STARTS, PERMS, null, 1);


  public void setUp() throws Exception {
    super.setUp();
    mau = new MockArchivalUnit();
  }

  public void testConfig() throws Exception {
    mau.setCrawlSpec(spec1);
    ConfigurationUtil.addFromArgs(SubstanceChecker.PARAM_DETECT_NO_SUBSTANCE_MODE,
				  "None");
    checker = new SubstanceChecker(mau);
    assertFalse(checker.isEnabledFor(SubstanceChecker.CONTEXT_CRAWL));
    assertFalse(checker.isEnabledFor(SubstanceChecker.CONTEXT_VOTE));

    ConfigurationUtil.addFromArgs(SubstanceChecker.PARAM_DETECT_NO_SUBSTANCE_MODE,
				  "Crawl");
    checker = new SubstanceChecker(mau);
    assertFalse(checker.isEnabledFor(SubstanceChecker.CONTEXT_CRAWL));
    assertFalse(checker.isEnabledFor(SubstanceChecker.CONTEXT_VOTE));

    mau.setSubstanceUrlPatterns(RegexpUtil.compileRegexps(ListUtil.list("x")));
    checker = new SubstanceChecker(mau);
    assertTrue(checker.isEnabledFor(SubstanceChecker.CONTEXT_CRAWL));
    assertFalse(checker.isEnabledFor(SubstanceChecker.CONTEXT_VOTE));

    mau.setSubstanceUrlPatterns(null);
    checker = new SubstanceChecker(mau);
    assertFalse(checker.isEnabledFor(SubstanceChecker.CONTEXT_CRAWL));

    mau.setNonSubstanceUrlPatterns(RegexpUtil.compileRegexps(ListUtil.list("x")));
    checker = new SubstanceChecker(mau);
    assertTrue(checker.isEnabledFor(SubstanceChecker.CONTEXT_CRAWL));

    ConfigurationUtil.addFromArgs(SubstanceChecker.PARAM_DETECT_NO_SUBSTANCE_MODE,
				  "Crawl");
    checker = new SubstanceChecker(mau);
    assertTrue(checker.isEnabledFor(SubstanceChecker.CONTEXT_CRAWL));
    assertFalse(checker.isEnabledFor(SubstanceChecker.CONTEXT_VOTE));

    ConfigurationUtil.addFromArgs(SubstanceChecker.PARAM_DETECT_NO_SUBSTANCE_MODE,
				  "Vote");
    checker = new SubstanceChecker(mau);
    assertFalse(checker.isEnabledFor(SubstanceChecker.CONTEXT_CRAWL));
    assertTrue(checker.isEnabledFor(SubstanceChecker.CONTEXT_VOTE));

    ConfigurationUtil.addFromArgs(SubstanceChecker.PARAM_DETECT_NO_SUBSTANCE_MODE,
				  "All");
    checker = new SubstanceChecker(mau);
    assertTrue(checker.isEnabledFor(SubstanceChecker.CONTEXT_CRAWL));
    assertTrue(checker.isEnabledFor(SubstanceChecker.CONTEXT_VOTE));
  }

  public void testNoPatterns() {
    checker = new SubstanceChecker(mau);
    assertEquals(State.Unknown, checker.hasSubstance());
  }

  public void testSubst() throws Exception {
    mau.setSubstanceUrlPatterns(compileRegexps(ListUtil.list("one", "two" )));
    checker = new SubstanceChecker(mau);
    assertEquals(State.No, checker.hasSubstance());
    check("http://four/");
    assertEquals(State.No, checker.hasSubstance());
    check("http://two/");
    assertEquals(State.Yes, checker.hasSubstance());
  }

  public void testNonSubst() throws Exception {
    mau.setCrawlSpec(spec1);
    mau.setNonSubstanceUrlPatterns(compileRegexps(ListUtil.list("one",
								"two" )));
    checker = new SubstanceChecker(mau);
    assertEquals(State.No, checker.hasSubstance());
    check("http://two/");
    assertEquals(State.No, checker.hasSubstance());
    check("http://start/");
    assertEquals(State.No, checker.hasSubstance());
    check("http://perm/");
    assertEquals(State.No, checker.hasSubstance());
    check("http://other/");
    assertEquals(State.Yes, checker.hasSubstance());
  }

  public void testRedirSubstLast() throws Exception {
    mau.setSubstanceUrlPatterns(compileRegexps(ListUtil.list("one", "redd" )));
    checker = new SubstanceChecker(mau);
    assertEquals(State.No, checker.hasSubstance());
    check("http://four/");
    assertEquals(State.No, checker.hasSubstance());
    check("http://two/", "http://reddd/");
    assertEquals(State.Yes, checker.hasSubstance());
  }

  public void testRedirSubstFirst() throws Exception {
    ConfigurationUtil.setFromArgs(SubstanceChecker.PARAM_DETECT_NO_SUBSTANCE_REDIRECT_URL, "First");
    mau.setSubstanceUrlPatterns(compileRegexps(ListUtil.list("one", "redd" )));
    checker = new SubstanceChecker(mau);
    assertEquals(State.No, checker.hasSubstance());
    check("http://four/");
    assertEquals(State.No, checker.hasSubstance());
    check("http://two/", "http://reddd/");
    assertEquals(State.No, checker.hasSubstance());
    check("http://reddd/", "http://three/");
    assertEquals(State.Yes, checker.hasSubstance());
  }

  public void testRedirSubstAll() throws Exception {
    ConfigurationUtil.setFromArgs(SubstanceChecker.PARAM_DETECT_NO_SUBSTANCE_REDIRECT_URL, "All");
    mau.setSubstanceUrlPatterns(compileRegexps(ListUtil.list("one", "redd" )));
    checker = new SubstanceChecker(mau);
    assertEquals(State.No, checker.hasSubstance());
    check("http://four/");
    assertEquals(State.No, checker.hasSubstance());
    check("http://two/", "http://reddd/");
    assertEquals(State.No, checker.hasSubstance());
    check(ListUtil.list("http://frob/", "http://reddd/", "http://three/"));
    assertEquals(State.Yes, checker.hasSubstance());
  }

  public void testRedirNonSubstLast() throws Exception {
    mau.setCrawlSpec(spec1);
    mau.setNonSubstanceUrlPatterns(compileRegexps(ListUtil.list("one", "redd" )));
    checker = new SubstanceChecker(mau);
    assertEquals(State.No, checker.hasSubstance());
    check("http://one/");
    assertEquals(State.No, checker.hasSubstance());
    check("http://two/", "http://one/");
    assertEquals(State.No, checker.hasSubstance());
    check("http://two/", "http://six/");
    assertEquals(State.Yes, checker.hasSubstance());
  }

  public void testRedirNonSubstFirst() throws Exception {
    ConfigurationUtil.setFromArgs(SubstanceChecker.PARAM_DETECT_NO_SUBSTANCE_REDIRECT_URL, "First");
    mau.setCrawlSpec(spec1);
    mau.setNonSubstanceUrlPatterns(compileRegexps(ListUtil.list("one", "redd" )));
    checker = new SubstanceChecker(mau);
    assertEquals(State.No, checker.hasSubstance());
    check("http://one/");
    assertEquals(State.No, checker.hasSubstance());
    check("http://redd/", "http://two/");
    assertEquals(State.No, checker.hasSubstance());
    check("http://three/", "http://one/");
    assertEquals(State.Yes, checker.hasSubstance());
  }

  public void testRedirNonSubstAll() throws Exception {
    ConfigurationUtil.setFromArgs(SubstanceChecker.PARAM_DETECT_NO_SUBSTANCE_REDIRECT_URL, "All");
    mau.setCrawlSpec(spec1);
    mau.setNonSubstanceUrlPatterns(compileRegexps(ListUtil.list("one",
								"redd",
								"green")));
    checker = new SubstanceChecker(mau);
    assertEquals(State.No, checker.hasSubstance());
    check("http://one/");
    assertEquals(State.No, checker.hasSubstance());
    check("http://redd/", "http://two/");
    assertEquals(State.No, checker.hasSubstance());
    check("http://one/", "http://redd/");
    assertEquals(State.No, checker.hasSubstance());
    check(ListUtil.list("http://one/", "http://redd/", "http://green/"));
    assertEquals(State.No, checker.hasSubstance());
    check(ListUtil.list("http://one/", "http://splortch/", "http://green/"));
    assertEquals(State.Yes, checker.hasSubstance());
  }

  void check(String url) {
    checker.checkSubstance(new MockCachedUrl(url));
  }

  void check(String url, String redirTo) {
    CIProperties props = new CIProperties();
    props.put(CachedUrl.PROPERTY_CONTENT_URL, redirTo);
    MockCachedUrl mcu = new MockCachedUrl(url);
    mcu.setProperties(props);
    checker.checkSubstance(mcu);
  }

  void check(List<String> urls) {
    MockCachedUrl first = mau.addUrl(urls.get(0));
    CIProperties props = new CIProperties();
    props.put(CachedUrl.PROPERTY_CONTENT_URL, urls.get(urls.size() - 1));
    MockCachedUrl mcu = first;

    for (String url : urls) {
      if (props == null) {
	props = new CIProperties();
      }
      props.put(CachedUrl.PROPERTY_REDIRECTED_TO, url);
      mcu.setProperties(props);
      props = null;
      mcu = mau.addUrl(url);
    }
    checker.checkSubstance(first);
  }

  List<Pattern> compileRegexps(List<String> regexps)
      throws MalformedPatternException {
    return RegexpUtil.compileRegexps(regexps);
  }

}
