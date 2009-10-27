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
import java.util.regex.*;

import org.lockss.test.*;
import org.lockss.util.*;
import org.lockss.config.*;
import org.lockss.daemon.*;
import org.lockss.crawler.*;
import org.lockss.repository.*;
import org.lockss.extractor.*;
import org.lockss.plugin.*;
import org.lockss.plugin.base.*;
import org.lockss.plugin.simulated.*;

public class TestHighWireMetadataExtractor extends LockssTestCase {
  static Logger log = Logger.getLogger("TestHighWireMetadataExtractor");

  private SimulatedArchivalUnit sau;
  private MockLockssDaemon theDaemon;
  private CrawlManager crawlMgr;
  private static int exceptionCount;
  private static final int DEFAULT_MAX_DEPTH = 1000;
  private static final int DEFAULT_FILESIZE = 3000;
  private static int fileSize = DEFAULT_FILESIZE;
  private static int maxDepth=DEFAULT_MAX_DEPTH;
  private static int urlCount = 28;
  private static int testExceptions = 3;

  private static final Map<String, String> tagMap =
    new HashMap<String, String>();
  static {
    tagMap.put("citation_journal_title", "AJP - Renal Physiology");
    tagMap.put("citation_issn", "0363-6127");

    tagMap.put("citation_authors", "Rodriguez, Mariano; Nemeth, Edward; Martin, David");
    tagMap.put("citation_title", "The calcium-sensing receptor: a key factor in the pathogenesis of secondary hyperparathyroidism");
    tagMap.put("citation_date", "02/01/2005");
    tagMap.put("citation_volume", "288");
    tagMap.put("citation_issue", "2");
    tagMap.put("citation_firstpage", "F253");
    tagMap.put("citation_id", "288/2/F253");
    tagMap.put("citation_mjid", "ajprenal;288/2/F253");
    tagMap.put("citation_doi", "10.1152/ajprenal.00302.2004");
    tagMap.put("citation_abstract_html_url", "http://ajprenal.physiology.org/cgi/content/abstract/288/2/F253");
    tagMap.put("citation_fulltext_html_url", "http://ajprenal.physiology.org/cgi/content/full/288/2/F253");
    tagMap.put("citation_pdf_url", "http://ajprenal.physiology.org/cgi/reprint/288/2/F253.pdf");
    tagMap.put("citation_pmid", "15507543");

    tagMap.put("dc.Contributor", "Rodriguez, Mariano");
    tagMap.put("dc.Contributor", "Nemeth, Edward");
    tagMap.put("dc.Contributor", "Martin, David");
    tagMap.put("dc.Title", "The calcium-sensing receptor: a key factor in the pathogenesis of secondary hyperparathyroidism");
    tagMap.put("dc.Identifier", "10.1152/ajprenal.00302.2004");
    tagMap.put("dc.Date", "02/01/2005");
  };

  public static void main(String[] args) throws Exception {
    TestHighWireMetadataExtractor test = new TestHighWireMetadataExtractor();
    if (args.length>0) {
      try {
        maxDepth = Integer.parseInt(args[0]);
      } catch (NumberFormatException ex) { }
    }

    test.setUp(maxDepth);
    test.testExtraction();
    test.tearDown();
  }

  public void setUp() throws Exception {
    super.setUp();
    this.setUp(DEFAULT_MAX_DEPTH);
  }

  public void setUp(int max) throws Exception {

    String tempDirPath = getTempDir().getAbsolutePath() + File.separator;
    String auId = "org|lockss|plugin|highwire|TestHighWireMetadataExtractor$MySimulatedPlugin.root~" +
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

  public void testExtraction() throws Exception {
    createContent();

    // get the root of the simContent
    String simDir = sau.getSimRoot();

    crawlContent();

    exceptionCount = 0;
    int count = 0;
    for (Iterator it = sau.getArticleIterator(); it.hasNext(); ) {
	BaseCachedUrl cu = (BaseCachedUrl)it.next();
	assertNotNull(cu);
	assertTrue(cu instanceof CachedUrl);
	log.debug3("count " + count + " url " + cu.getUrl());
	MetadataExtractor me = cu.getMetadataExtractor();
	log.debug3("Extractor: " + me.toString());
	assertTrue(me instanceof
		   HighWireMetadataExtractorFactory.HighWireMetadataExtractor);
	Metadata md = me.extract(cu);
	assertNotNull(md);
	checkMetadata(md);
	count++;
    }
    log.debug("Article count is " + count);
    assertEquals(urlCount, count);
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

  public void checkMetadata(Metadata md) {
    // Does md have all the fields in the meta tags with the right content?
    for (Iterator it = tagMap.keySet().iterator(); it.hasNext(); ) {
      String expected_name = (String)it.next();
      String expected_content = tagMap.get(expected_name);
      assertNotNull(expected_content);
      log.debug("key: " + expected_name + " value: " + expected_content);
      String actual_content = (String)md.get(expected_name.toLowerCase());
      assertNotNull(actual_content);
      log.debug("expected: " + expected_content + " actual: " + actual_content);
      assertEquals(expected_content, actual_content);
    }
    // Do the accessors return the expected values?
    assertEquals(tagMap.get("citation_issn"), md.getISSN());
    assertEquals(tagMap.get("citation_volume"), md.getVolume());
    assertEquals(tagMap.get("citation_issue"), md.getIssue());
    assertEquals(tagMap.get("citation_firstpage"), md.getStartPage());
    assertEquals(tagMap.get("dc.Identifier"), md.getDOI());
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
      MySubTreeArticleIteratorFactory ret =
	  new MySubTreeArticleIteratorFactory();
      ret.setSubTreeRoot("branch1/branch1");
      return ret;
    }
    public SimulatedContentGenerator getContentGenerator(Configuration cf,
							 String fileRoot) {
      return new MySimulatedContentGenerator(fileRoot);
    }
    public MetadataExtractor getMetadataExtractor(String fileType,
						  ArchivalUnit au) {
      MetadataExtractorFactory mef = new HighWireMetadataExtractorFactory();
      MetadataExtractor me = null;
      try {
	me = mef.createMetadataExtractor("text/html");
      } catch (PluginException ex) {
	log.error("createMetadataExtractor threw: " + ex);
      }
      return me;
    }

  }

  public static class MySubTreeArticleIteratorFactory
      implements ArticleIteratorFactory {
    String subTreeRoot;
    MySubTreeArticleIteratorFactory() {
    }
    /**
     * Create an Iterator that iterates through the AU's articles, pointing
     * to the appropriate CachedUrl of type mimeType for each, or to the plugin's
     * choice of CachedUrl if mimeType is null
     * @param mimeType the MIME type desired for the CachedUrls
     * @param au the ArchivalUnit to iterate through
     * @return the ArticleIterator
     */
    public Iterator createArticleIterator(String mimeType, ArchivalUnit au)
	throws PluginException {
      Iterator ret;
      Pattern pat = Pattern.compile("^.*[0-9][0-9][0-9]file.html$");
      if (exceptionCount == 0) {
	ret = new SubTreeArticleIterator(mimeType, au, subTreeRoot, pat);
      } else {
	ret = new MySubTreeArticleIterator(mimeType, au, subTreeRoot,
					   exceptionCount);
      }
      return ret;
    }
    public void setSubTreeRoot(String root) {
      subTreeRoot = root;
      log.debug("Set subTreeRoot: " + subTreeRoot);
    }
  }
  public static class MySubTreeArticleIterator extends SubTreeArticleIterator {
    int exceptionCount;
    MySubTreeArticleIterator(String mimeType, ArchivalUnit au,
			     String subTreeRoot, int exceptionCount) {
      super(mimeType, au, subTreeRoot);
      this.exceptionCount = exceptionCount;
    }
    protected void processCachedUrl(CachedUrl cu) {
      if (exceptionCount > 0 && cu.getUrl().endsWith(".html")) {
	exceptionCount--;
	throw new UnsupportedOperationException();
      }
      super.processCachedUrl(cu);
    }
  }

  public static class MySimulatedContentGenerator extends SimulatedContentGenerator {
    protected MySimulatedContentGenerator(String fileRoot) {
      super(fileRoot);
    }

    public String getHtmlFileContent(String filename, int fileNum,
				     int depth, int branchNum,
				     boolean isAbnormal) {
      String file_content =
	"<HTML><HEAD><TITLE>" + filename + "</TITLE></HEAD><BODY>\n";
      for (Iterator it = tagMap.keySet().iterator(); it.hasNext(); ) {
	String name = (String)it.next();
	String content = tagMap.get(name);
	file_content += "  <meta name=\"" + name + "\" content=\"" + content +
	  "\">\n";
      }
      file_content += getHtmlContent(fileNum, depth, branchNum, isAbnormal);
      file_content += "\n</BODY></HTML>";
      logger.debug("MySimulatedContentGenerator.getHtmlFileContent: " +
		   file_content);
      return file_content;
    }
  }
}