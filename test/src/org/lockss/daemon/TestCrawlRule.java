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

package org.lockss.daemon
;
import java.util.*;
import junit.framework.TestCase;
import org.apache.oro.text.regex.*;
import org.lockss.daemon.*;
import org.lockss.util.*;
import org.lockss.test.*;

/**
 * This is the test class for org.lockss.daemon.CrawlRule
 */

public class TestCrawlRule extends LockssTestCase {
  static final int incl = CrawlRules.RE.MATCH_INCLUDE;
  static final int excl = CrawlRules.RE.MATCH_EXCLUDE;
  static final int notincl = CrawlRules.RE.NO_MATCH_INCLUDE;
  static final int notexcl = CrawlRules.RE.NO_MATCH_EXCLUDE;

  public TestCrawlRule(String msg){
    super(msg);
  }

  public void testNullRE() throws LockssRegexpException {
    try {
      CrawlRule cr = new CrawlRules.RE((String)null,
				     CrawlRules.RE.MATCH_INCLUDE);
      fail("CrawlRules.RE with null string should throw");
    } catch (NullPointerException e) {
    }
    try {
      CrawlRule cr = new CrawlRules.RE((Pattern)null,
				       CrawlRules.RE.MATCH_INCLUDE);
      fail("CrawlRules.RE with null RE should throw");
    } catch (NullPointerException e) {
    }
  }

  public void testIllegalRE() {
    try {
      CrawlRule cr = new CrawlRules.RE("[", CrawlRules.RE.MATCH_INCLUDE);
      fail("CrawlRules.RE with illegal RE should throw");
    } catch (LockssRegexpException e) {
    }
  }

  public void testMatchIncl() throws LockssRegexpException {
    CrawlRule cr = new CrawlRules.RE("blahblah.*",
				     CrawlRules.RE.MATCH_INCLUDE);
    assertEquals(CrawlRule.INCLUDE, cr.match("blahblahblah"));
    assertEquals(CrawlRule.INCLUDE, cr.match("blahblahblahsdfsfd"));
    assertEquals(CrawlRule.IGNORE, cr.match("lkjlj"));
  }

  public void testREAnchor() throws LockssRegexpException {
    {
      CrawlRule cr = new CrawlRules.RE("^foo$", CrawlRules.RE.MATCH_INCLUDE);
      assertEquals(CrawlRule.INCLUDE, cr.match("foo"));
      assertEquals(CrawlRule.IGNORE, cr.match("foobar"));
      assertEquals(CrawlRule.IGNORE, cr.match("barfoo"));
      assertEquals(CrawlRule.IGNORE, cr.match("barfoobar"));
    }
    {
      CrawlRule cr = new CrawlRules.RE("^foo", CrawlRules.RE.MATCH_INCLUDE);
      assertEquals(CrawlRule.INCLUDE, cr.match("foo"));
      assertEquals(CrawlRule.INCLUDE, cr.match("foobar"));
      assertEquals(CrawlRule.IGNORE, cr.match("barfoo"));
      assertEquals(CrawlRule.IGNORE, cr.match("barfoobar"));
    }
    {
      CrawlRule cr = new CrawlRules.RE("foo$", CrawlRules.RE.MATCH_INCLUDE);
      assertEquals(CrawlRule.INCLUDE, cr.match("foo"));
      assertEquals(CrawlRule.IGNORE, cr.match("foobar"));
      assertEquals(CrawlRule.INCLUDE, cr.match("barfoo"));
      assertEquals(CrawlRule.IGNORE, cr.match("barfoobar"));
    }
    {
      CrawlRule cr = new CrawlRules.RE("foo", CrawlRules.RE.MATCH_INCLUDE);
      assertEquals(CrawlRule.INCLUDE, cr.match("foo"));
      assertEquals(CrawlRule.INCLUDE, cr.match("foobar"));
      assertEquals(CrawlRule.INCLUDE, cr.match("barfoo"));
      assertEquals(CrawlRule.INCLUDE, cr.match("barfoobar"));
    }
  }

  public void testMatchExcl() throws LockssRegexpException {
    CrawlRule cr = new CrawlRules.RE("blahblah.*",
				     CrawlRules.RE.MATCH_EXCLUDE);
    assertEquals(CrawlRule.EXCLUDE, cr.match("blahblahblah"));
    assertEquals(CrawlRule.EXCLUDE, cr.match("blahblahblahsdfsfd"));
    assertEquals(CrawlRule.IGNORE, cr.match("lkjlj"));
  }

  public void testNoMatchIncl() throws LockssRegexpException {
    CrawlRule cr = new CrawlRules.RE("blahblah.*",
				     CrawlRules.RE.NO_MATCH_INCLUDE);
    assertEquals(CrawlRule.IGNORE, cr.match("blahblahblah"));
    assertEquals(CrawlRule.IGNORE, cr.match("blahblahblahsdfsfd"));
    assertEquals(CrawlRule.INCLUDE, cr.match("lkjlj"));
  }

  public void testNoMatchExcl() throws LockssRegexpException {
    CrawlRule cr = new CrawlRules.RE("blahblah.*",
				     CrawlRules.RE.NO_MATCH_EXCLUDE);
    assertEquals(CrawlRule.IGNORE, cr.match("blahblahblah"));
    assertEquals(CrawlRule.IGNORE, cr.match("blahblahblahsdfsfd"));
    assertEquals(CrawlRule.EXCLUDE, cr.match("lkjlj"));
  }

  public void testMatchNull() throws LockssRegexpException {
    CrawlRule cr1 = new CrawlRules.RE("blahblah.*",
				     CrawlRules.RE.MATCH_INCLUDE);
    try {
      cr1.match(null);
      fail("CrawlRules.RE.match(null) should throw");
    } catch (NullPointerException e) {
    }
  }

  public void testFirstMatchNull() throws LockssRegexpException {
    try {
      CrawlRule cr = new CrawlRules.FirstMatch(null);
      fail("CrawlRules.FirstMatch with null list should throw");
    } catch (NullPointerException e) {
    }
    CrawlRule cr = new CrawlRules.FirstMatch(Collections.EMPTY_LIST);
    assertEquals(CrawlRule.IGNORE, cr.match("foo"));
  }

  public void testFirstMatchWrongClass() throws LockssRegexpException {
    try {
      CrawlRule cr = new CrawlRules.FirstMatch(ListUtil.list("foo"));
      fail("CrawlRules.FirstMatch with list of non-CrawlRules should throw");
    } catch (ClassCastException e) {
    }
  }

  public void testFirstMatch() throws LockssRegexpException {
    List l = ListUtil.list(new CrawlRules.RE("^foo.*",
					     CrawlRules.RE.MATCH_INCLUDE),
			   new CrawlRules.RE("^bar.*",
					     CrawlRules.RE.MATCH_EXCLUDE));
    CrawlRule cr = new CrawlRules.FirstMatch(l);
    assertEquals(CrawlRule.INCLUDE, cr.match("foobar"));
    assertEquals(CrawlRule.EXCLUDE, cr.match("barfoo"));
    assertEquals(CrawlRule.IGNORE, cr.match("neither"));
    try {
      cr.match(null);
      fail("CrawlRules.FirstMatch.match(null) should throw");
    } catch (NullPointerException e) {
    }
  }
}

