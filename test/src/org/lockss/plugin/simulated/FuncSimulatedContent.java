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

package org.lockss.plugin.simulated;

import java.util.*;
import java.io.*;
import java.security.*;
import org.lockss.app.*;
import org.lockss.util.*;
import org.lockss.test.*;
import org.lockss.daemon.*;
import org.lockss.repository.*;
import org.lockss.plugin.*;
import org.lockss.crawler.GoslingCrawlerImpl;
import org.lockss.state.HistoryRepositoryImpl;
import junit.framework.*;

/**
 * Test class for functional tests on the content.
 */
public class FuncSimulatedContent extends LockssTestCase {
  private SimulatedArchivalUnit sau;
  private MockLockssDaemon theDaemon;

  private boolean firstTestRun = false;

  private static String DAMAGED_CACHED_URL = "/branch2/branch2/file2.txt";

  public FuncSimulatedContent(String msg) {
    super(msg);
  }

  public void setUp() throws Exception {
    super.setUp();
    String tempDirPath = getTempDir().getAbsolutePath() + File.separator;
    String tempDirPath2 = getTempDir().getAbsolutePath() + File.separator;
    String auId = "org|lockss|plugin|simulated|SimulatedPlugin.root~" +
      PropKeyEncoder.encode(tempDirPath);
    String auId2 = "org|lockss|plugin|simulated|SimulatedPlugin.root~" +
      PropKeyEncoder.encode(tempDirPath2);
    Properties props = new Properties();
    props.setProperty(SystemMetrics.PARAM_HASH_TEST_DURATION, "1000");
    props.setProperty(SystemMetrics.PARAM_HASH_TEST_BYTE_STEP, "1024");
    props.setProperty(LockssRepositoryImpl.PARAM_CACHE_LOCATION, tempDirPath);
    props.setProperty(HistoryRepositoryImpl.PARAM_HISTORY_LOCATION,
                      tempDirPath);
    props.setProperty("org.lockss.au." + auId + ".root", tempDirPath);
    props.setProperty("org.lockss.au." + auId + ".depth", "2");
    props.setProperty("org.lockss.au." + auId + ".branch", "2");
    props.setProperty("org.lockss.au." + auId + ".numFiles", "2");

    props.setProperty("org.lockss.au." + auId + ".badCachedFileLoc", "2,2");
    props.setProperty("org.lockss.au." + auId + ".badCachedFileNum", "2");
    props.setProperty("org.lockss.au." + auId2 + ".badCachedFileLoc", "2,2");
    props.setProperty("org.lockss.au." + auId2 + ".badCachedFileNum", "2");

    props.setProperty("org.lockss.au." + auId2 + ".root", tempDirPath2);
    props.setProperty("org.lockss.au." + auId2 + ".depth", "2");
    props.setProperty("org.lockss.au." + auId2 + ".branch", "2");
    props.setProperty("org.lockss.au." + auId2 + ".numFiles", "2");
    ConfigurationUtil.setCurrentConfigFromProps(props);

    theDaemon = new MockLockssDaemon();
    theDaemon.setDaemonInited(true);
    theDaemon.getPluginManager().startService();

    theDaemon.getHistoryRepository().startService();
    theDaemon.getHashService().startService();
    MockSystemMetrics metrics = new MyMockSystemMetrics();
    metrics.initService(theDaemon);
    metrics.startService();
    metrics.setHashSpeed(100);
    theDaemon.setSystemMetrics(metrics);
    sau =
        (SimulatedArchivalUnit) theDaemon.getPluginManager().getAllAus().get(0);

    theDaemon.getLockssRepository(sau);
    theDaemon.getNodeManager(sau).initService(theDaemon);
    theDaemon.getNodeManager(sau).startService();
  }

  public void tearDown() throws Exception {
    theDaemon.getLockssRepository(sau).stopService();
    theDaemon.getNodeManager(sau).stopService();
    theDaemon.getPluginManager().stopService();
    theDaemon.getHashService().stopService();
    theDaemon.getSystemMetrics().stopService();
    super.tearDown();
  }

  public void testSimulatedContent() throws Exception {
    createContent();
    crawlContent();
    checkContent();
    hashContent();

    doDamageRemoveTest();
  }

  public void testDualContentHash() throws Exception {
    createContent();
    crawlContent();
    CachedUrlSet set = sau.getAuCachedUrlSet();
    byte[] nameH = getHash(set, true);
    byte[] contentH = getHash(set, false);

    sau =
        (SimulatedArchivalUnit)theDaemon.getPluginManager().getAllAus().get(1);
    theDaemon.getLockssRepository(sau);
    theDaemon.getNodeManager(sau).initService(theDaemon);
    theDaemon.getNodeManager(sau).startService();

    createContent();
    crawlContent();
    set = sau.getAuCachedUrlSet();
    byte[] nameH2 = getHash(set, true);
    byte[] contentH2 = getHash(set, false);
    assertTrue(Arrays.equals(nameH, nameH2));
    assertTrue(Arrays.equals(contentH, contentH2));
  }

  private void createContent() {
    SimulatedContentGenerator scgen = sau.getContentGenerator();
    scgen.setFileTypes(SimulatedContentGenerator.FILE_TYPE_HTML + SimulatedContentGenerator.FILE_TYPE_TXT);
    scgen.setAbnormalFile("1,1", 1);
    scgen.setOddBranchesHaveContent(true);

    sau.deleteContentTree();
    sau.generateContentTree();
    assertTrue(scgen.isContentTree());
  }

  private void crawlContent() {
    CrawlSpec spec =
      new CrawlSpec(SimulatedArchivalUnit.SIMULATED_URL_START, null);
    Crawler crawler =
      GoslingCrawlerImpl.makeNewContentCrawler(sau, spec);
    crawler.doCrawl(Deadline.MAX);
  }

  private void checkContent() throws IOException {
    checkRoot();
    checkLeaf();
    checkStoredContent();
  }

  private void hashContent() throws Exception {
    measureHashSpeed();
    hashSet(true);
    hashSet(false);
  }

  private void checkRoot() {
    CachedUrlSet set = sau.getAuCachedUrlSet();
    Iterator setIt = set.flatSetIterator();
    ArrayList childL = new ArrayList(1);
    CachedUrlSet cus = null;
    while (setIt.hasNext()) {
      cus = (CachedUrlSet) setIt.next();
      childL.add(cus.getUrl());
    }

    String[] expectedA = new String[] {
      SimulatedArchivalUnit.SIMULATED_URL_ROOT};
    assertIsomorphic(expectedA, childL);

    setIt = cus.flatSetIterator();
    childL = new ArrayList(7);
    while (setIt.hasNext()) {
      childL.add( ( (CachedUrlSetNode) setIt.next()).getUrl());
    }

    expectedA = new String[] {
      SimulatedArchivalUnit.SIMULATED_URL_ROOT + "/branch1",
      SimulatedArchivalUnit.SIMULATED_URL_ROOT + "/branch2",
      SimulatedArchivalUnit.SIMULATED_URL_ROOT + "/file1.html",
      SimulatedArchivalUnit.SIMULATED_URL_ROOT + "/file1.txt",
      SimulatedArchivalUnit.SIMULATED_URL_ROOT + "/file2.html",
      SimulatedArchivalUnit.SIMULATED_URL_ROOT + "/file2.txt",
      SimulatedArchivalUnit.SIMULATED_URL_ROOT + "/index.html"
    };
    assertIsomorphic(expectedA, childL);
  }

  private void checkLeaf() {
    String parent = SimulatedArchivalUnit.SIMULATED_URL_ROOT + "/branch1";
    CachedUrlSetSpec spec = new RangeCachedUrlSetSpec(parent);
    Plugin plugin = sau.getPlugin();
    CachedUrlSet set = plugin.makeCachedUrlSet(sau, spec);
    Iterator setIt = set.contentHashIterator();
    ArrayList childL = new ArrayList(16);
    while (setIt.hasNext()) {
      childL.add( ( (CachedUrlSetNode) setIt.next()).getUrl());
    }
    String[] expectedA = new String[] {
      parent,
      parent + "/branch1",
      parent + "/branch1/file1.html",
      parent + "/branch1/file1.txt",
      parent + "/branch1/file2.html",
      parent + "/branch1/file2.txt",
      parent + "/branch1/index.html",
      parent + "/branch2",
      parent + "/branch2/file1.html",
      parent + "/branch2/file1.txt",
      parent + "/branch2/file2.html",
      parent + "/branch2/file2.txt",
      parent + "/branch2/index.html",
      parent + "/file1.html",
      parent + "/file1.txt",
      parent + "/file2.html",
      parent + "/file2.txt",
      parent + "/index.html",
    };
    assertIsomorphic(expectedA, childL);
  }

  private void checkUrlContent(String path, int fileNum, int depth,
                               int branchNum, boolean isAbnormal,
                               boolean isDamaged) throws IOException {
    String file = SimulatedArchivalUnit.SIMULATED_URL_ROOT + path;
    CachedUrl url =
      sau.getPlugin().makeCachedUrl(sau.getAuCachedUrlSet(), file);
    String content = getUrlContent(url);
    String expectedContent;
    if (path.endsWith(".html")) {
      String fn = path.substring(path.lastIndexOf("/") + 1);
      expectedContent = SimulatedContentGenerator.getHtmlFileContent(fn,
        fileNum, depth, branchNum, isAbnormal);
    }
    else {
      expectedContent = SimulatedContentGenerator.getFileContent(
        fileNum, depth, branchNum, isAbnormal);
    }
    if (isDamaged) {
      assertNotEquals(expectedContent, content);
    }
    else {
      assertEquals(expectedContent, content);
    }
  }

  private void checkStoredContent() throws IOException {
    checkUrlContent("/file1.txt", 1, 0, 0, false, false);
    checkUrlContent("/branch1/branch1/file1.txt", 1, 2, 1, true, false);
    checkUrlContent(DAMAGED_CACHED_URL, 2, 2, 2, false, true);
  }

  private void doDamageRemoveTest() throws Exception {
    /* Cache the file again; this time the damage should be gone */
    String file = SimulatedArchivalUnit.SIMULATED_URL_ROOT + DAMAGED_CACHED_URL;
    UrlCacher uc = sau.getPlugin().makeUrlCacher(sau.getAuCachedUrlSet(),file);
    uc.forceCache();
    checkUrlContent(DAMAGED_CACHED_URL, 2, 2, 2, false, false);
  }

  private void measureHashSpeed() throws Exception {
    MessageDigest dig = null;
    try {
      dig = MessageDigest.getInstance("SHA-1");
    } catch (NoSuchAlgorithmException ex) {
      fail("No algorithm.");
    }
    CachedUrlSet set = sau.getAuCachedUrlSet();
    CachedUrlSetHasher hasher = set.getContentHasher(dig);
    SystemMetrics metrics = theDaemon.getSystemMetrics();
    int estimate = metrics.getBytesPerMsHashEstimate(hasher, dig);
    // should be protected against this being zero by MyMockSystemMetrics,
    // but otherwise use the proper calculation.  This avoids test failure
    // due to really slow machines
    assertTrue(estimate > 0);
    long estimatedTime = set.estimatedHashDuration();
    long size = ((Long)PrivilegedAccessor.getValue(set,
        "totalNodeSize")).longValue();
    assertTrue(size > 0);
    System.out.println("b/ms: " + estimate);
    System.out.println("size: " + size);
    System.out.println("estimate: " + estimatedTime);
    assertEquals(estimatedTime,
                 theDaemon.getHashService().padHashEstimate(size / estimate));
  }

  private void hashSet(boolean namesOnly) throws IOException {
    CachedUrlSet set = sau.getAuCachedUrlSet();
    byte[] hash = getHash(set, namesOnly);
    byte[] hash2 = getHash(set, namesOnly);
    assertTrue(Arrays.equals(hash, hash2));

    String parent = SimulatedArchivalUnit.SIMULATED_URL_ROOT + "/branch1";
    CachedUrlSetSpec spec = new RangeCachedUrlSetSpec(parent);
    set = sau.getPlugin().makeCachedUrlSet(sau, spec);
    hash2 = getHash(set, namesOnly);
    assertFalse(Arrays.equals(hash, hash2));
  }

  private byte[] getHash(CachedUrlSet set, boolean namesOnly) throws
    IOException {
    MessageDigest dig = null;
    try {
      dig = MessageDigest.getInstance("SHA-1");
    } catch (NoSuchAlgorithmException ex) {
      fail("No algorithm.");
    }
    hash(set, dig, namesOnly);
    return dig.digest();
  }

  private void hash(CachedUrlSet set, MessageDigest dig, boolean namesOnly)
      throws IOException {
    CachedUrlSetHasher hasher = null;
    if (namesOnly) {
      hasher = set.getNameHasher(dig);
    } else {
      hasher = set.getContentHasher(dig);
    }
    int bytesHashed = 0;
    long timeTaken = System.currentTimeMillis();
    while (!hasher.finished()) {
      bytesHashed += hasher.hashStep(256);
    }
    timeTaken = System.currentTimeMillis() - timeTaken;
    if ((timeTaken > 0) && (bytesHashed > 500)) {
      System.out.println("Bytes hashed: " + bytesHashed);
      System.out.println("Time taken: " + timeTaken + "ms");
      System.out.println("Bytes/sec: " + (bytesHashed * 1000 / timeTaken));
    } else {
      System.out.println("No time taken, or insufficient bytes hashed.");
      System.out.println("Bytes hashed: " + bytesHashed);
      System.out.println("Time taken: " + timeTaken + "ms");
    }
  }

  private String getUrlContent(CachedUrl url) throws IOException {
    InputStream content = url.openForReading();
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    StreamUtil.copy(content, baos);
    content.close();
    String contentStr = new String(baos.toByteArray());
    baos.close();
    return contentStr;
  }

  // this version doesn't fully override the 'measureHashSpeed()' function, but
  // protects against it returning '0' by returning the set speed
  private class MyMockSystemMetrics extends MockSystemMetrics {
    public int measureHashSpeed(CachedUrlSetHasher hasher, MessageDigest digest) throws
        IOException {
      int speed = super.measureHashSpeed(hasher, digest);
      if (speed==0) {
        speed = getHashSpeed();
        if (speed<=0) {
          throw new RuntimeException("No hash speed set.");
        }
      }
      return speed;
    }
  }


  public static void main(String[] argv) {
    String[] testCaseList = {
      FuncSimulatedContent.class.getName()};
    junit.swingui.TestRunner.main(testCaseList);
  }

  public static Test suite() {
    return new TestSuite(FuncSimulatedContent.class);
  }

}
