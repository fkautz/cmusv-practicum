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
import java.util.*;
import org.lockss.app.*;
import org.lockss.test.*;
import org.lockss.util.*;
import org.lockss.crawler.*;
import org.lockss.daemon.*;
// import org.lockss.daemon.status.*;
import org.lockss.plugin.*;

public class TestCrawlManagerStatus extends LockssTestCase {
  private CrawlManagerStatus cmStatus;

  public void setUp() throws Exception {
  }

  public void testHist() {
    CrawlerStatus c1 = newCStat("one");
    CrawlerStatus c2 = newCStat("two");
    CrawlerStatus c3 = newCStat("three");
    CrawlerStatus c4 = newCStat("four");
    cmStatus = new CrawlManagerStatus(3);
    assertEmpty(cmStatus.getCrawlStatusList());
    cmStatus.addCrawlStatus(c1);
    cmStatus.addCrawlStatus(c2);
    cmStatus.addCrawlStatus(c3);
    assertEquals(ListUtil.list(c1, c2, c3), cmStatus.getCrawlStatusList());
    cmStatus.addCrawlStatus(c4);
    assertEquals(ListUtil.list(c2, c3, c4), cmStatus.getCrawlStatusList());

    assertNull(cmStatus.getCrawlStatus(c1.getKey()));
    assertSame(c2, cmStatus.getCrawlStatus(c2.getKey()));
    assertSame(c3, cmStatus.getCrawlStatus(c3.getKey()));
    assertSame(c4, cmStatus.getCrawlStatus(c4.getKey()));

    cmStatus.setHistSize(2);
    assertEquals(ListUtil.list(c3, c4), cmStatus.getCrawlStatusList());
    assertNull(cmStatus.getCrawlStatus(c2.getKey()));
    assertSame(c3, cmStatus.getCrawlStatus(c3.getKey()));
    assertSame(c4, cmStatus.getCrawlStatus(c4.getKey()));

    cmStatus.setHistSize(5);
    assertEquals(ListUtil.list(c3, c4), cmStatus.getCrawlStatusList());

    cmStatus.addCrawlStatus(c1);
    cmStatus.addCrawlStatus(c2);
    assertEquals(ListUtil.list(c3, c4, c1, c2), cmStatus.getCrawlStatusList());

    CrawlerStatus c5 = newCStat("five");
    CrawlerStatus c6 = newCStat("six");
    cmStatus.addCrawlStatus(c5);
    cmStatus.addCrawlStatus(c6);
    assertEquals(ListUtil.list(c4, c1, c2, c5, c6),
		 cmStatus.getCrawlStatusList());
  }

  public void testCounts() {
    cmStatus = new CrawlManagerStatus(3);
    assertEquals(0, cmStatus.getSuccessCount());
    assertEquals(0, cmStatus.getFailedCount());
    cmStatus.incrFinished(true);
    assertEquals(1, cmStatus.getSuccessCount());
    assertEquals(0, cmStatus.getFailedCount());
    cmStatus.incrFinished(false);
    assertEquals(1, cmStatus.getSuccessCount());
    assertEquals(1, cmStatus.getFailedCount());
    cmStatus.incrFinished(false);
    assertEquals(1, cmStatus.getSuccessCount());
    assertEquals(2, cmStatus.getFailedCount());
  }

  CrawlerStatus newCStat(String url) {
    return new CrawlerStatus(new MockArchivalUnit(),
			      ListUtil.list(url), "new");
  }
}
