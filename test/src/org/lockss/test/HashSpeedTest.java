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

package org.lockss.test;

import java.io.File;
import java.security.MessageDigest;
import org.lockss.daemon.*;
import org.lockss.util.*;
import org.lockss.plugin.*;
import org.lockss.plugin.simulated.*;
import org.lockss.repository.LockssRepositoryImpl;
import org.lockss.poller.PollManager;
import org.lockss.crawler.GoslingCrawlerImpl;
import org.lockss.protocol.*;
import java.util.Properties;

public class HashSpeedTest extends LockssTestCase {
  private SimulatedArchivalUnit sau;
  private MockLockssDaemon theDaemon;
  private static final int DEFAULT_DURATION = 1000;
  private static final int DEFAULT_BYTESTEP = 1024;
  private static final int DEFAULT_FILESIZE = 3000;
  private static int duration = DEFAULT_DURATION;
  private static int byteStep = DEFAULT_BYTESTEP;
  private static int fileSize = DEFAULT_FILESIZE;

  public static void main(String[] args) throws Exception {
    HashSpeedTest test = new HashSpeedTest();
    if (args.length>0) {
      try {
        duration = Integer.parseInt(args[0]);
        if (args.length>1) {
          byteStep = Integer.parseInt(args[1]);
          if (args.length>2) {
            fileSize = Integer.parseInt(args[2]);
          }
        }
      } catch (NumberFormatException ex) { }
    }
    test.setUp(duration, byteStep);
    test.testRunSelf();
    test.tearDown();
  }

  public void setUp() throws Exception {
    super.setUp();
    theDaemon = new MockLockssDaemon();
    this.setUp(DEFAULT_DURATION, DEFAULT_BYTESTEP);
  }

  public void setUp(int duration, int byteStep) throws Exception {
    String tempDirPath = getTempDir().getAbsolutePath() + File.separator;
    String auId = "org|lockss|plugin|simulated|SimulatedPlugin.root~" +
      PropKeyEncoder.encode(tempDirPath);
    Properties props = new Properties();
    props.setProperty(SystemMetrics.PARAM_HASH_TEST_DURATION, ""+duration);
    props.setProperty(SystemMetrics.PARAM_HASH_TEST_BYTE_STEP, ""+byteStep);
    props.setProperty(LockssRepositoryImpl.PARAM_CACHE_LOCATION, tempDirPath);
    props.setProperty("org.lockss.au." + auId + "." +
                      SimulatedPlugin.AU_PARAM_ROOT, tempDirPath);
    props.setProperty("org.lockss.au." + auId + "." +
                      SimulatedPlugin.AU_PARAM_DEPTH, "3");
    props.setProperty("org.lockss.au." + auId + "." +
                      SimulatedPlugin.AU_PARAM_BRANCH, "5");
    props.setProperty("org.lockss.au." + auId + "." +
                      SimulatedPlugin.AU_PARAM_NUM_FILES, "5");
    props.setProperty("org.lockss.au." + auId + "." +
                      SimulatedPlugin.AU_PARAM_FILE_TYPES,
                      ""+SimulatedContentGenerator.FILE_TYPE_BIN);
    props.setProperty("org.lockss.au." + auId + "." +
                      SimulatedPlugin.AU_PARAM_BIN_FILE_SIZE, ""+fileSize);
    ConfigurationUtil.setCurrentConfigFromProps(props);

    theDaemon = new MockLockssDaemon();
    theDaemon.setDaemonInited(true);
    theDaemon.getPluginManager().startService();
    theDaemon.getSystemMetrics().startService();
    theDaemon.getHashService().startService();

    sau =
        (SimulatedArchivalUnit)theDaemon.getPluginManager().getAllAus().get(0);
    theDaemon.getLockssRepository(sau);
  }

  public void tearDown() throws Exception {
    theDaemon.stopDaemon();
    super.tearDown();
  }

  public void testRunSelf() throws Exception {
    createContent();
    crawlContent();
    hashContent();
  }

  private void createContent() {
    System.out.println("Generating tree of size 3x5x5 with "+fileSize
                       +"byte files...");
    sau.generateContentTree();
  }

  private void crawlContent() {
    System.out.println("Crawling tree...");
    CrawlSpec spec = new CrawlSpec(sau.SIMULATED_URL_START, null);
    Crawler crawler =
      new GoslingCrawlerImpl(sau, spec.getStartingUrls(), true, 1);
    crawler.doCrawl(Deadline.MAX);
  }

  private void hashContent() throws Exception {
    MessageDigest digest = LcapMessage.getDefaultHasher();
    System.out.println("Hashing-");
    System.out.println("  Algorithm: "+digest.getAlgorithm());
    System.out.println("  Duration: "+duration+"ms");
    System.out.println("  Byte/step: "+byteStep+"bytes");
    CachedUrlSetHasher hasher = sau.getAuCachedUrlSet().getContentHasher(digest);

    SystemMetrics metrics = SystemMetrics.getSystemMetrics();
    double estimate = metrics.getBytesPerMsHashEstimate(hasher, digest);
    System.out.println("Estimate-");
    System.out.println("  Bytes/ms: "+estimate);
    System.out.println("  GB/hr: "+
                       ((estimate*Constants.HOUR)/(1024*1024*1024)));
  }
}

