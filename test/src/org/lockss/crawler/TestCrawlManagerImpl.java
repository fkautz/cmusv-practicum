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
import java.util.*;
import java.net.*;
import junit.framework.TestCase;
import org.lockss.util.*;
import org.lockss.test.*;
import org.lockss.state.*;
import org.lockss.plugin.*;
import org.lockss.daemon.*;

/**
 */
public class TestCrawlManagerImpl extends LockssTestCase {
  static final long TEN_SECONDS = 10 * Constants.SECOND;
  public static final String GENERIC_URL = "http://www.example.com/index.html";
  private TestableCrawlManagerImpl crawlManager = null;
  private MockArchivalUnit mau = null;
  private MockLockssDaemon theDaemon;
  private MockNodeManager nodeManager;
  private MockActivityRegulator activityRegulator;
  private MockCrawler crawler;
  private CachedUrlSet cus;

  public TestCrawlManagerImpl(String msg) {
    super(msg);
  }

  public void setUp() throws Exception {
    super.setUp();
    mau = new MockArchivalUnit();

    crawlManager = new TestableCrawlManagerImpl();
    theDaemon = new MockLockssDaemon();
    nodeManager = new MockNodeManager();
    theDaemon.setNodeManager(nodeManager, mau);

    activityRegulator = new MockActivityRegulator(mau);
    theDaemon.setActivityRegulator(activityRegulator, mau);

    crawlManager.initService(theDaemon);
    crawlManager.startService();
    
    cus = mau.makeCachedUrlSet(new SingleNodeCachedUrlSetSpec(GENERIC_URL));
    activityRegulator.setStartCusActivity(cus, true);
//     activityRegulator.setStartCusActivity(true);
    activityRegulator.setStartAuActivity(true);
    crawler = new MockCrawler();
    crawlManager.setTestCrawler(crawler);
  }

  public void tearDown() throws Exception {
    nodeManager.stopService();
    crawlManager.stopService();
    theDaemon.stopDaemon();
    super.tearDown();
  }

  public void testNullAUForIsCrawlingAU() {
    try {
      TestCrawlCB cb = new TestCrawlCB(new SimpleBinarySemaphore());
      crawlManager.startNewContentCrawl(null, cb, "blah");
      fail("Didn't throw an IllegalArgumentException on a null AU");
    } catch (IllegalArgumentException iae) {
    }
  }

  public void testNullCallbackForIsCrawlingAU() {
    //shouldn't throw an exception
    crawlManager.startNewContentCrawl(mau, null, "blah");
  }

  public void testNewCrawlDeadlineIsMax() {
    SimpleBinarySemaphore sem = new SimpleBinarySemaphore();

    crawlManager.startNewContentCrawl(mau, new TestCrawlCB(sem), null);

    assertTrue("Crawl didn't finish in 10 seconds", sem.take(TEN_SECONDS));
    assertEquals(Deadline.MAX, crawler.getDeadline());
  }

  public void testDoesntNCCrawlWhenNotAllowed() {
    SimpleBinarySemaphore sem = new SimpleBinarySemaphore();
    activityRegulator.setStartAuActivity(false);

    crawlManager.startNewContentCrawl(mau, new TestCrawlCB(sem), null);

    assertTrue("Crawl didn't finish in 10 seconds", sem.take(TEN_SECONDS));
    assertFalse("doCrawl() called", crawler.doCrawlCalled());
  }


  public void testBasicNewContentCrawl() {
    SimpleBinarySemaphore sem = new SimpleBinarySemaphore();

    crawlManager.startNewContentCrawl(mau, new TestCrawlCB(sem), null);

    assertTrue("Crawl didn't finish in 10 seconds", sem.take(TEN_SECONDS));
    assertTrue("doCrawl() not called", crawler.doCrawlCalled());
  }

  public void testNCCrawlSignalsActivityRegulatorWhenDone() {
    SimpleBinarySemaphore sem = new SimpleBinarySemaphore();

    crawlManager.startNewContentCrawl(mau, new TestCrawlCB(sem), null);

    assertTrue("Crawl didn't finish in 10 seconds", sem.take(TEN_SECONDS));
    activityRegulator.assertNewContentCrawlFinished();
  }

  public void testRepairCrawlSignalsActivityRegulatorWhenDone()
      throws MalformedURLException {
    SimpleBinarySemaphore sem = new SimpleBinarySemaphore();
    TestCrawlCB cb = new TestCrawlCB(sem);

    crawlManager.scheduleRepair(mau, new URL(GENERIC_URL), cb, null);

    assertTrue("Crawl didn't finish in 10 seconds", sem.take(TEN_SECONDS));
    activityRegulator.assertRepairCrawlFinished(cus);
  }

  public void testRepairCrawlSignalsActivityRegulatorWhenDoneMultiUrls() 
      throws MalformedURLException {
    URL url1 = new URL("http://www.example.com/index1.html");
    URL url2 = new URL("http://www.example.com/index2.html");
    List urls = ListUtil.list(url1, url2);

    SimpleBinarySemaphore sem = new SimpleBinarySemaphore();
    TestCrawlCB cb = new TestCrawlCB(sem);
    CachedUrlSet cus1 =
      mau.makeCachedUrlSet(new SingleNodeCachedUrlSetSpec(url1.toString()));
    CachedUrlSet cus2 =
      mau.makeCachedUrlSet(new SingleNodeCachedUrlSetSpec(url2.toString()));

    activityRegulator.setStartCusActivity(cus1, true);
    activityRegulator.setStartCusActivity(cus2, true);

    crawlManager.scheduleRepair(mau, urls, cb, null);

    assertTrue("Crawl didn't finish in 10 seconds", sem.take(TEN_SECONDS));
    activityRegulator.assertRepairCrawlFinished(cus1);
    activityRegulator.assertRepairCrawlFinished(cus2);
  }


  public void testTriggersNewContentCallback() {
    SimpleBinarySemaphore sem = new SimpleBinarySemaphore();

    TestCrawlCB cb = new TestCrawlCB(sem);
    crawlManager.startNewContentCrawl(mau, cb, null);

    assertTrue("Crawl didn't finish in 10 seconds", sem.take(TEN_SECONDS));
    assertTrue("Callback wasn't triggered", cb.wasTriggered());
  }


  public void testNewContentCrawlCallbackReturnsCookie() {
    SimpleBinarySemaphore sem = new SimpleBinarySemaphore();

    String cookie = "cookie string";

    TestCrawlCB cb = new TestCrawlCB(sem);
    crawlManager.startNewContentCrawl(mau, cb, cookie);

    assertTrue("Crawl didn't finish in 10 seconds", sem.take(TEN_SECONDS));
    assertEquals(cookie, (String)cb.getCookie());
  }

  public void testNewContentCrawlCallbackReturnsNullCookie() {
    SimpleBinarySemaphore sem = new SimpleBinarySemaphore();
    String cookie = null;

    TestCrawlCB cb = new TestCrawlCB(sem);
    crawlManager.startNewContentCrawl(mau, cb, cookie);

    assertTrue("Crawl didn't finish in 10 seconds", sem.take(TEN_SECONDS));
    assertEquals(cookie, (String)cb.getCookie());
  }

  public void testKicksOffNewThread() {
    TestCrawlCB cb = new TestCrawlCB();
    crawlManager.startNewContentCrawl(mau, cb, null);

    //if the callback was triggered, the crawl completed
    assertFalse("Callback was triggered", cb.wasTriggered());
  }

  public void testScheduleRepairNullAU() throws MalformedURLException {
    try{
      crawlManager.scheduleRepair(null, new URL("http://www.example.com"),
				  new TestCrawlCB(), "blah");
      fail("Didn't throw IllegalArgumentException on null AU");
    } catch (IllegalArgumentException iae) {
    }
  }

  public void testScheduleRepairNullUrl() {
    try{
      crawlManager.scheduleRepair(mau, (URL)null,
				  new TestCrawlCB(), "blah");
      fail("Didn't throw IllegalArgumentException on null URL");
    } catch (IllegalArgumentException iae) {
    }
  }

  public void testScheduleRepairNullUrlList() {
    try{
      crawlManager.scheduleRepair(mau, (List)null,
				  new TestCrawlCB(), "blah");
      fail("Didn't throw IllegalArgumentException on null URL list");
    } catch (IllegalArgumentException iae) {
    }
  }

  public void testNoRepairIfNotAllowed() throws MalformedURLException {
    activityRegulator.setStartCusActivity(cus, false);
    SimpleBinarySemaphore sem = new SimpleBinarySemaphore();
    TestCrawlCB cb = new TestCrawlCB(sem);

    crawlManager.scheduleRepair(mau, new URL(GENERIC_URL), cb, null);

    assertTrue("Crawl didn't finish in 10 seconds", sem.take(TEN_SECONDS));
    assertFalse("doCrawl() called", crawler.doCrawlCalled());
  }

  public void testNoRepairIfNotAllowedMultiUrls()
      throws MalformedURLException {
    URL url1 = new URL("http://www.example.com/index1.html");
    URL url2 = new URL("http://www.example.com/index2.html");
    List urls = ListUtil.list(url1, url2);

    SimpleBinarySemaphore sem = new SimpleBinarySemaphore();
    TestCrawlCB cb = new TestCrawlCB(sem);
    CachedUrlSet cus1 =
      mau.makeCachedUrlSet(new SingleNodeCachedUrlSetSpec(url1.toString()));
    CachedUrlSet cus2 =
      mau.makeCachedUrlSet(new SingleNodeCachedUrlSetSpec(url2.toString()));


    activityRegulator.setStartCusActivity(cus1, true);
    activityRegulator.setStartCusActivity(cus2, false);

    crawlManager.scheduleRepair(mau, urls, cb, null);

    assertTrue("Crawl didn't finish in 10 seconds", sem.take(TEN_SECONDS));
    assertTrue("doCrawl() called", crawler.doCrawlCalled());
    assertEquals(ListUtil.list(url1.toString()), crawler.getStartUrls());
  }

  public void testBasicRepairCrawl() throws MalformedURLException {
    SimpleBinarySemaphore sem = new SimpleBinarySemaphore();
    TestCrawlCB cb = new TestCrawlCB(sem);

    crawlManager.scheduleRepair(mau, new URL(GENERIC_URL), cb, null);

    assertTrue("Crawl didn't finish in 10 seconds", sem.take(TEN_SECONDS));
    assertTrue("doCrawl() not called", crawler.doCrawlCalled());
  }

  public void testTriggersRepairCallback() throws MalformedURLException {
    SimpleBinarySemaphore sem = new SimpleBinarySemaphore();
    TestCrawlCB cb = new TestCrawlCB(sem);

    crawlManager.scheduleRepair(mau, new URL(GENERIC_URL), cb, null);

    assertTrue("Crawl didn't finish in 10 seconds", sem.take(TEN_SECONDS));
    assertTrue("Callback wasn't triggered", cb.wasTriggered());
  }

  public void testRepairCallbackGetsCookie() throws MalformedURLException {
    String cookie = "test cookie str";
    SimpleBinarySemaphore sem = new SimpleBinarySemaphore();
    TestCrawlCB cb = new TestCrawlCB(sem);

    crawlManager.scheduleRepair(mau, new URL(GENERIC_URL), cb, cookie);

    assertTrue("Crawl didn't finish in 10 seconds", sem.take(TEN_SECONDS));
    assertEquals(cookie, cb.getCookie());
  }

  public void testCompletedCrawlUpdatesLastCrawlTime() {
    MockAuState maus = new MockAuState();
    long lastCrawlTime = maus.getLastCrawlTime();
    nodeManager.setAuState(maus);

    SimpleBinarySemaphore sem = new SimpleBinarySemaphore();
    TestCrawlCB cb = new TestCrawlCB(sem);
    crawlManager.startNewContentCrawl(mau, cb, null);

    assertTrue("Crawl didn't finish in 10 seconds", sem.take(TEN_SECONDS));
    assertNotEquals(lastCrawlTime, maus.getLastCrawlTime());
  }

  public void testUnsuccessfulCrawlDoesntUpdateLastCrawlTime() {
    MockAuState maus = new MockAuState();
    long lastCrawlTime = maus.getLastCrawlTime();
    nodeManager.setAuState(maus);

    crawler.setCrawlSucessful(false);

    SimpleBinarySemaphore sem = new SimpleBinarySemaphore();
    TestCrawlCB cb = new TestCrawlCB(sem);
    crawlManager.startNewContentCrawl(mau, cb, null);

    assertTrue("Crawl didn't finish in 10 seconds", sem.take(TEN_SECONDS));
    assertEquals(lastCrawlTime, maus.getLastCrawlTime());
  }

  public void testCompletedCrawlUpdatesLastCrawlTimeIfFNFExceptionThrown() {
    MockAuState maus = new MockAuState();
    long lastCrawlTime = maus.getLastCrawlTime();
    nodeManager.setAuState(maus);

    SimpleBinarySemaphore sem = new SimpleBinarySemaphore();
    TestCrawlCB cb = new TestCrawlCB(sem);
    crawlManager.startNewContentCrawl(mau, cb, null);

    assertTrue("Crawl didn't finish in 10 seconds", sem.take(TEN_SECONDS));
    assertNotEquals(lastCrawlTime, maus.getLastCrawlTime());
  }

  private class TestCrawlCB implements CrawlManager.Callback {
    SimpleBinarySemaphore sem;
    boolean called = false;
    Object cookie;

    public TestCrawlCB() {
      this(null);
    }

    public TestCrawlCB(SimpleBinarySemaphore sem) {
      this.sem = sem;
    }

    public void signalCrawlAttemptCompleted(boolean success, Object cookie) {
      called = true;
      this.cookie = cookie;
      if (sem != null) {
	sem.give();
      }
    }

    public void signalCrawlSuspended(Object cookie) {
      this.cookie = cookie;
    }

    public Object getCookie() {
      return cookie;
    }

    public boolean wasTriggered() {
      return called;
    }
  }

  private static class TestableCrawlManagerImpl extends CrawlManagerImpl {
    private MockCrawler mockCrawler;
    protected Crawler makeCrawler(ArchivalUnit au, List urls,
				  int type, boolean followLinks) {

      mockCrawler.setAU(au);
      mockCrawler.setURLs(urls);
      mockCrawler.setFollowLinks(followLinks);
      mockCrawler.setType(type);
      return mockCrawler;
    }

    private void setTestCrawler(MockCrawler mockCrawler) {
      this.mockCrawler = mockCrawler;
    }
  }
}
