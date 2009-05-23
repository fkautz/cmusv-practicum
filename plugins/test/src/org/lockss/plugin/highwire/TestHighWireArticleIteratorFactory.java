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

package org.lockss.plugin.highwire;

import java.io.*;
import java.util.*;

import org.lockss.test.*;
import org.lockss.util.*;
import org.lockss.config.*;
import org.lockss.daemon.*;
import org.lockss.crawler.*;
import org.lockss.repository.*;
import org.lockss.plugin.*;
import org.lockss.plugin.base.*;
import org.lockss.plugin.simulated.*;

public class TestHighWireArticleIteratorFactory extends LockssTestCase {
  static Logger log = Logger.getLogger("TestHighWireArticleIteratorFactory");

  private SimulatedArchivalUnit sau;
  private MockLockssDaemon theDaemon;
  private CrawlManager crawlMgr;
  private static final int DEFAULT_MAX_DEPTH = 1000;
  private static final int DEFAULT_FILESIZE = 3000;
  private static int fileSize = DEFAULT_FILESIZE;
  private static int maxDepth=DEFAULT_MAX_DEPTH;

  public static void main(String[] args) throws Exception {
    TestHighWireArticleIteratorFactory test = new TestHighWireArticleIteratorFactory();
    if (args.length>0) {
      try {
        maxDepth = Integer.parseInt(args[0]);
      } catch (NumberFormatException ex) { }
    }

    test.setUp(maxDepth);
    test.testArticleCount();
    test.tearDown();
  }

  public void setUp() throws Exception {
    super.setUp();
    this.setUp(DEFAULT_MAX_DEPTH);
  }

  public void setUp(int max) throws Exception {

    String tempDirPath = getTempDir().getAbsolutePath() + File.separator;
    String auId = "org|lockss|plugin|highwire|TestHighWireArticleIteratorFactory$MySimulatedPlugin.root~" +
      PropKeyEncoder.encode(tempDirPath);
    Properties props = new Properties();
    props.setProperty(NewContentCrawler.PARAM_MAX_CRAWL_DEPTH, ""+max);
    maxDepth=max;
    props.setProperty(LockssRepositoryImpl.PARAM_CACHE_LOCATION, tempDirPath);

    props.setProperty("org.lockss.au." + auId + "." +
                      SimulatedPlugin.AU_PARAM_ROOT, tempDirPath);
    // the simulated Content's depth will be (AU_PARAM_DEPTH + 1)
    props.setProperty("org.lockss.au." + auId + "." +
                      SimulatedPlugin.AU_PARAM_DEPTH, "3");
    props.setProperty("org.lockss.au." + auId + "." +
                      SimulatedPlugin.AU_PARAM_BRANCH, "3");
    props.setProperty("org.lockss.au." + auId + "." +
                      SimulatedPlugin.AU_PARAM_NUM_FILES, "7");
    props.setProperty("org.lockss.au." + auId + "." +
                      SimulatedPlugin.AU_PARAM_FILE_TYPES, "" +
                      (SimulatedContentGenerator.FILE_TYPE_PDF +
		       SimulatedContentGenerator.FILE_TYPE_HTML));
    props.setProperty("org.lockss.au." + auId + "." +
                      SimulatedPlugin.AU_PARAM_BIN_FILE_SIZE, ""+fileSize);

    theDaemon = getMockLockssDaemon();
    theDaemon.getAlertManager();
    theDaemon.getPluginManager().setLoadablePluginsReady(true);
    theDaemon.setDaemonInited(true);
    theDaemon.getPluginManager().startService();
    crawlMgr = theDaemon.getCrawlManager();

    ConfigurationUtil.setCurrentConfigFromProps(props);

    sau =
        (SimulatedArchivalUnit)theDaemon.getPluginManager().getAllAus().get(0);
    theDaemon.getLockssRepository(sau).startService();
    theDaemon.setNodeManager(new MockNodeManager(), sau);
  }

  public void tearDown() throws Exception {
    sau.deleteContentTree();
    theDaemon.stopDaemon();
    super.tearDown();
  }

  public void testArticleCount() throws Exception {
    createContent();

    // get the root of the simContent
    String simDir = sau.getSimRoot();

    crawlContent();

    int count = 0;
    for (Iterator it = sau.getArticleIterator(); it.hasNext(); ) {
	BaseCachedUrl cu = (BaseCachedUrl)it.next();
	assertNotNull(cu);
	assert(cu instanceof CachedUrl);
	log.debug("count " + count + " url " + cu.getUrl());
	count++;
    }
    log.debug("Article count is " + count);
    assertEquals(32, count);
  }

  private void createContent() {
    log.debug("Generating tree of size 3x1x2 with "+fileSize
	      +"byte files...");
    sau.generateContentTree();
  }

  private void crawlContent() {
    log.debug("Crawling tree...");
    CrawlSpec spec = new SpiderCrawlSpec(sau.getNewContentCrawlUrls(), null);
    NewContentCrawler crawler =
      new NewContentCrawler(sau, spec, new MockAuState());
    //crawler.setCrawlManager(crawlMgr);
    crawler.doCrawl();
  }

  public static class MySimulatedPlugin extends SimulatedPlugin {
    public ArchivalUnit createAu0(Configuration auConfig)
	throws ArchivalUnit.ConfigurationException {
      ArchivalUnit au = new SimulatedArchivalUnit(this);
      au.setConfiguration(auConfig);
      return au;
    }
    /**
     * Returns the article iterator factory for the mime type, if any
     * @param contentType the content type
     * @return the ArticleIteratorFactory
     */
    public ArticleIteratorFactory getArticleIteratorFactory(String contentType) {
      MyHighWireArticleIteratorFactory ret =
	  new MyHighWireArticleIteratorFactory();
      ret.setSubTreeRoot("branch1/branch1");
      return ret;
    }
  }

  public static class MyHighWireArticleIteratorFactory
      extends HighWireArticleIteratorFactory {
    MyHighWireArticleIteratorFactory() {
    }
    public void setSubTreeRoot(String root) {
      subTreeRoot = root;
      log.debug("Set subTreeRoot: " + subTreeRoot);
    }
  }
}
