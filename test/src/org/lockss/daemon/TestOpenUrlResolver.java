/*
 * $Id: TestOpenUrlResolver.java,v 1.10 2011/05/11 21:35:27 pgust Exp $
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

package org.lockss.daemon;

import java.io.*;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

import javax.sql.DataSource;

import org.lockss.config.*;
import org.lockss.extractor.ArticleMetadata;
import org.lockss.extractor.ArticleMetadataExtractor;
import org.lockss.extractor.MetadataField;
import org.lockss.extractor.MetadataTarget;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.plugin.ArticleFiles;
import org.lockss.plugin.ArticleIteratorFactory;
import org.lockss.plugin.PluginManager;
import org.lockss.plugin.PluginTestUtil;
import org.lockss.plugin.SubTreeArticleIterator;
import org.lockss.plugin.simulated.*;
import org.lockss.repository.*;
import org.lockss.util.*;
import org.lockss.test.*;

//import TestBePressMetadataExtractor.MySimulatedPlugin;

/**
 * Test class for org.lockss.daemon.MetadataManager
 *
 * @author  Philip Gust
 * @version 1.0 
 */
public class TestOpenUrlResolver extends LockssTestCase {
  static Logger log = Logger.getLogger("TestOpenUrlResolver");

  private SimulatedArchivalUnit sau0, sau1, sau2, sau3;
  private MockLockssDaemon theDaemon;
  private MetadataManager metadataManager;
  private PluginManager pluginManager;
  private OpenUrlResolver openUrlResolver;
  private boolean disableMetadataManager = false;

  /** set of AUs reindexed by the MetadataManager */
  Set<String> ausReindexed = new HashSet<String>();
  
  public void setUp() throws Exception {
	super.setUp();

    final String tempDirPath = getTempDir().getAbsolutePath();

    // set derby database log 
    System.setProperty("derby.stream.error.file", 
    				   new File(tempDirPath,"derby.log").getAbsolutePath());
    
    theDaemon = getMockLockssDaemon();
    theDaemon.getAlertManager();
    pluginManager = theDaemon.getPluginManager();
    pluginManager.setLoadablePluginsReady(true);
    theDaemon.setDaemonInited(true);
    pluginManager.startService();
    theDaemon.getCrawlManager();
    
    String paramIndexingEnabled = Boolean.toString(!disableMetadataManager && true);
    Properties props = new Properties();
    props.setProperty(MetadataManager.PARAM_INDEXING_ENABLED, paramIndexingEnabled);
    props.setProperty(LockssRepositoryImpl.PARAM_CACHE_LOCATION, tempDirPath);
    ConfigurationUtil.setCurrentConfigFromProps(props);
    Configuration config = ConfigurationUtil.fromProps(props);

    Tdb tdb = new Tdb();
    
    // create Tdb for testing purposes
    Properties tdbProps = new Properties();
    tdbProps = new Properties();
    tdbProps.setProperty("title", "Title[10.0135/12345678]");
    tdbProps.setProperty("isbn", "976-1-58562-317-7");
    tdbProps.setProperty("journalTitle", "Journal[10.0135/12345678]");
    tdbProps.setProperty("attributes.publisher", "Publisher[10.0135/12345678]");
    tdbProps.setProperty("plugin", "org.lockss.daemon.TestOpenUrlResolver$MySimulatedPlugin3");
    tdbProps.setProperty("param.1.key", "base_url");
    tdbProps.setProperty("param.1.value", "http://www.title3.org/");
    tdb.addTdbAuFromProperties(tdbProps);

    tdbProps = new Properties();
    tdbProps.setProperty("title", "Title[Manual of Clinical Psychopharmacology]");
    tdbProps.setProperty("isbn", "978-1-58562-317-4");
    tdbProps.setProperty("journalTitle", "Manual of Clinical Psychopharmacology");
    tdbProps.setProperty("attributes.publisher", "Publisher[Manual of Clinical Psychopharmacology]");
    tdbProps.setProperty("plugin", "org.lockss.daemon.TestOpenUrlResolver$MySimulatedPlugin2");
    tdbProps.setProperty("param.1.key", "base_url");
    tdbProps.setProperty("param.1.value", "http://www.title2.org/");
    tdbProps.setProperty("attributes.year", "1993");
    tdb.addTdbAuFromProperties(tdbProps);
    
    tdbProps = new Properties();
    tdbProps.setProperty("title", "Title[10.2468/24681357]");
    tdbProps.setProperty("issn", "1144-875X");
    tdbProps.setProperty("eissn", "7744-6521");
    tdbProps.setProperty("attributes.volume", "42");
    tdbProps.setProperty("journalTitle", "Journal[10.2468/24681357]");
    tdbProps.setProperty("attributes.publisher", "Publisher[10.2468/24681357]");
    tdbProps.setProperty("plugin", "org.lockss.daemon.TestOpenUrlResolver$MySimulatedPlugin1");
    tdbProps.setProperty("param.1.key", "base_url");
    tdbProps.setProperty("param.1.value", "http://www.title1.org/");
    tdb.addTdbAuFromProperties(tdbProps);
    
    tdbProps = new Properties();
    tdbProps.setProperty("title", "Title[10.1234/12345678]");
    tdbProps.setProperty("issn", "0740-2783");
    tdbProps.setProperty("attributes.volume", "XI");
    tdbProps.setProperty("journalTitle", "Journal[10.1234/12345678]");
    tdbProps.setProperty("attributes.publisher", "Publisher[10.1234/12345678]");
    tdbProps.setProperty("plugin", "org.lockss.daemon.TestOpenUrlResolver$MySimulatedPlugin0");
    tdbProps.setProperty("param.1.key", "base_url");
    tdbProps.setProperty("param.1.value", "http://www.title0.org/");
    tdb.addTdbAuFromProperties(tdbProps);

    config.setTdb(tdb);
    ConfigurationUtil.installConfig(config);
    
    config = simAuConfig(tempDirPath + "/0");
    config.put("volume", "XI");
    config.put("base_url", "http://www.title0.org/");
    sau0 = PluginTestUtil.createAndStartSimAu(MySimulatedPlugin0.class, config);
    config = simAuConfig(tempDirPath + "/1");
    config.put("base_url", "http://www.title1.org/");
    sau1 = PluginTestUtil.createAndStartSimAu(MySimulatedPlugin1.class, config);
    config = simAuConfig(tempDirPath + "/2");
    config.put("base_url", "http://www.title2.org/");
    sau2 = PluginTestUtil.createAndStartSimAu(MySimulatedPlugin2.class, config);
    config = simAuConfig(tempDirPath + "/3");
    config.put("base_url", "http://www.title3.org/");
    sau3 = PluginTestUtil.createAndStartSimAu(MySimulatedPlugin3.class, config);
    PluginTestUtil.crawlSimAu(sau0);
    PluginTestUtil.crawlSimAu(sau1);
    PluginTestUtil.crawlSimAu(sau2);
    PluginTestUtil.crawlSimAu(sau3);

    ausReindexed.clear();
    metadataManager = new MetadataManager() {
      public Connection newConnection() throws SQLException {
        if (disableMetadataManager) {
          throw new IllegalArgumentException("MetadataManager is disabled");
        }
        return super.newConnection();
      }
      
      /**
       * Get the db root directory for testing.
       * @return the db root directory
       */
      protected String getDbRootDirectory() {
        return tempDirPath;
      }
      
      /**
       * Notify listeners that an AU is being reindexed.
       * 
       * @param au
       */
      protected void notifyStartReindexingAu(ArchivalUnit au) {
        log.debug("Start reindexing au " + au);
      }
      
      /**
       * Notify listeners that an AU is finshed being reindexed.
       * 
       * @param au
       */
      protected void notifyFinishReindexingAu(ArchivalUnit au, boolean success) {
        log.debug("Finished reindexing au (" + success + ") " + au);
        synchronized (ausReindexed) {
          ausReindexed.add(au.getAuId());
          ausReindexed.notifyAll();
        }
      }
    };
    theDaemon.setMetadataManager(metadataManager);
    metadataManager.initService(theDaemon);
    try {
      metadataManager.startService();
    } catch (IllegalArgumentException ex) {
      // ignored
    }
    theDaemon.setAusStarted(true);
    
    if ("true".equals(paramIndexingEnabled)) {
      int expectedAuCount = 4;
      assertEquals(expectedAuCount, pluginManager.getAllAus().size());
      long maxWaitTime = expectedAuCount * 20000; // 20 sec. per au
      int ausCount = waitForReindexing(expectedAuCount, maxWaitTime);
      assertEquals(expectedAuCount, ausCount);
    }
    
    openUrlResolver = new OpenUrlResolver(theDaemon);
  }

  Configuration simAuConfig(String rootPath) {
    Configuration conf = ConfigManager.newConfiguration();
    conf.put("root", rootPath);
    conf.put("depth", "2");
    conf.put("branch", "1");
    conf.put("numFiles", "3");
    conf.put("fileTypes", "" + (SimulatedContentGenerator.FILE_TYPE_PDF +
				SimulatedContentGenerator.FILE_TYPE_HTML));
    conf.put("binFileSize", "7");
    return conf;
  }

  public void tearDown() throws Exception {
    sau0.deleteContentTree();
    sau1.deleteContentTree();
    sau2.deleteContentTree();
    theDaemon.stopDaemon();
    super.tearDown();
  }

  public void createMetadata() throws Exception {
    // reset set of reindexed aus
    ausReindexed.clear();

    metadataManager.restartService();
    theDaemon.setAusStarted(true);
    
    DataSource ds = metadataManager.getDataSource();
    assertNotNull(ds);
    
    int expectedAuCount = 4;
    assertEquals(expectedAuCount, pluginManager.getAllAus().size());
    
    Connection con = ds.getConnection();
    
    long maxWaitTime = expectedAuCount * 20000; // 20 sec. per au
    int ausCount = waitForReindexing(expectedAuCount, maxWaitTime);
    assertEquals(expectedAuCount, ausCount);
    
    assertEquals(0, metadataManager.reindexingTasks.size());
    assertEquals(0, metadataManager.getAusToReindex(con, Integer.MAX_VALUE).size());

    String query =           
      "select access_url from " + MetadataManager.METADATA_TABLE; 
    Statement stmt = con.createStatement();
    ResultSet resultSet = stmt.executeQuery(query);
    if (!resultSet.next()) {
      fail("No entries in metadata table");
    }
    String url = resultSet.getString(1);
    log.debug("url from metadata table: " + url);

    con.rollback();
    con.commit();
  }
  
  /**
   * Waits a specified period for a specified number of AUs to finish 
   * being reindexed.  Returns the actual number of AUs reindexed.
   * 
   * @param auCount the expected AU count
   * @param maxWaitTime the maximum time to wait
   * @return the number of AUs reindexed
   */
  private int waitForReindexing(int auCount, long maxWaitTime) {
    long startTime = System.currentTimeMillis();
    synchronized (ausReindexed) {
      while (   (System.currentTimeMillis()-startTime < maxWaitTime) 
             && (ausReindexed.size() < auCount)) {
        try {
          ausReindexed.wait(maxWaitTime);
        } catch (InterruptedException ex) {
        }
      }
    }
    return ausReindexed.size();
  }

  public static class MySubTreeArticleIteratorFactory
    implements ArticleIteratorFactory {
    String pat;
    public MySubTreeArticleIteratorFactory(String pat) {
      this.pat = pat;
    }

    /**
     * Create an Iterator that iterates through the AU's articles, pointing
     * to the appropriate CachedUrl of type mimeType for each, or to the
     * plugin's choice of CachedUrl if mimeType is null
     * @param au the ArchivalUnit to iterate through
     * @return the ArticleIterator
     */
    @Override
    public Iterator<ArticleFiles> 
      createArticleIterator(ArchivalUnit au, MetadataTarget target) 
      throws PluginException {
    	  
      Iterator<ArticleFiles> ret;
      SubTreeArticleIterator.Spec spec = 
        new SubTreeArticleIterator.Spec().setTarget(target);
    	  
      if (pat != null) {
       spec.setPattern(pat);
      }
    	  
      ret = new SubTreeArticleIterator(au, spec);
      log.debug(  "creating article iterator for au " + au.getName() 
    	    + " hasNext: " + ret.hasNext());
      return ret;
    }
  }
	
  public static class MySimulatedPlugin extends SimulatedPlugin {
    ArticleMetadataExtractor simulatedArticleMetadataExtractor = null;
	
    /**
     * Returns the article iterator factory for the mime type, if any
     * @param contentType the content type
     * @return the ArticleIteratorFactory
     */
    @Override
    public ArticleIteratorFactory getArticleIteratorFactory() {
      MySubTreeArticleIteratorFactory ret =
          new MySubTreeArticleIteratorFactory(null); //"branch1/branch1");
      return ret;
    }
    @Override
    public ArticleMetadataExtractor 
      getArticleMetadataExtractor(MetadataTarget target, ArchivalUnit au) {
      return simulatedArticleMetadataExtractor;
    }
  }


  public static class MySimulatedPlugin0 extends MySimulatedPlugin {
    public MySimulatedPlugin0() {
      simulatedArticleMetadataExtractor = new ArticleMetadataExtractor() {
        int articleNumber = 0;
        public void extract(MetadataTarget target, ArticleFiles af, Emitter emitter)
          throws IOException, PluginException {
          ArticleMetadata md = new ArticleMetadata();
          articleNumber++;
          md.put(MetadataField.FIELD_ISSN,"0740-2783");
          md.put(MetadataField.FIELD_VOLUME,"XI");
          if (articleNumber < 10) {
            md.put(MetadataField.FIELD_ISSUE,"1st Quarter");
            md.put(MetadataField.FIELD_DATE,"2010-Q1");
            md.put(MetadataField.FIELD_START_PAGE,"" + articleNumber);
          } else {
            md.put(MetadataField.FIELD_ISSUE,"2nd Quarter");
            md.put(MetadataField.FIELD_DATE,"2010-Q2");
            md.put(MetadataField.FIELD_START_PAGE,"" + (articleNumber-9));
          }
          String doiPrefix = "10.1234/12345678";
          String doi = doiPrefix + "."
			+ md.get(MetadataField.FIELD_DATE) + "."
			+ md.get(MetadataField.FIELD_START_PAGE); 
          md.put(MetadataField.FIELD_DOI, doi);
          md.put(MetadataField.FIELD_JOURNAL_TITLE,"Journal[" + doiPrefix + "]");
          md.put(MetadataField.FIELD_ARTICLE_TITLE,"Title[" + doi + "]");
          md.put(MetadataField.FIELD_AUTHOR,"Author[" + doi + "]");
          md.put(MetadataField.FIELD_ACCESS_URL, 
        	 "http://www.title0.org/plugin0/XI/"
             +  md.get(MetadataField.FIELD_ISSUE) 
             +"/p" + md.get(MetadataField.FIELD_START_PAGE));
          emitter.emitMetadata(af, md);
        }
      };
    }
    public ExternalizableMap getDefinitionMap() {
      ExternalizableMap map = new ExternalizableMap();
      map.putString("au_start_url", "\"%splugin0/%s\", base_url, volume");
      map.putString("au_volume_url", "\"%splugin0/%s/toc\", base_url, volume");
      map.putString("au_issue_url", "\"%splugin0/%s/%s/toc\", base_url, volume, issue");
      map.putString("au_title_url", "\"%splugin0/toc\", base_url");
      return map;
    }
  }
  
  public static class MySimulatedPlugin1 extends MySimulatedPlugin {
    public MySimulatedPlugin1() {
      simulatedArticleMetadataExtractor = new ArticleMetadataExtractor() {
        int articleNumber = 0;
        public void extract(MetadataTarget target, ArticleFiles af, Emitter emitter)
          throws IOException, PluginException {
          articleNumber++;
          ArticleMetadata md = new ArticleMetadata();
          md.put(MetadataField.FIELD_ISSN,"1144-875X");
          md.put(MetadataField.FIELD_EISSN, "7744-6521");
          md.put(MetadataField.FIELD_VOLUME,"42");
          if (articleNumber < 10) {
            md.put(MetadataField.FIELD_ISSUE,"Summer");
            md.put(MetadataField.FIELD_DATE,"2010-S2");
            md.put(MetadataField.FIELD_START_PAGE,"" + articleNumber);
          } else {
            md.put(MetadataField.FIELD_ISSUE,"Fall");
            md.put(MetadataField.FIELD_DATE,"2010-S3");
            md.put(MetadataField.FIELD_START_PAGE, "" + (articleNumber-9));
          }
          String doiPrefix = "10.2468/28681357";
          String doi = doiPrefix + "."
      			+ md.get(MetadataField.FIELD_DATE) + "."
      			+ md.get(MetadataField.FIELD_START_PAGE); 
          md.put(MetadataField.FIELD_DOI, doi);
          md.put(MetadataField.FIELD_JOURNAL_TITLE, "Journal[" + doiPrefix + "]");
          md.put(MetadataField.FIELD_ARTICLE_TITLE, "Title[" + doi + "]");
          md.put(MetadataField.FIELD_AUTHOR, "Author1[" + doi + "]");
          md.put(MetadataField.FIELD_ACCESS_URL, 
              "http://www.title1.org/plugin1/v_42/"
          	+  "i_" + md.get(MetadataField.FIELD_ISSUE) 
          	+"/p_" + md.get(MetadataField.FIELD_START_PAGE));
          emitter.emitMetadata(af, md);
        }
      };
    }
    public ExternalizableMap getDefinitionMap() {
      ExternalizableMap map = new ExternalizableMap();
      map.putString("au_start_url", "\"%splugin1/v_%s/toc\", base_url, volume");
      map.putString("au_issue_url", "\"%splugin1/v_%s/i_%s/toc\", base_url, volume, issue");
      return map;
    }
  }
  
  public static class MySimulatedPlugin2 extends MySimulatedPlugin {
    public MySimulatedPlugin2() {
      simulatedArticleMetadataExtractor = new ArticleMetadataExtractor() {
        int articleNumber = 0;
        public void extract(MetadataTarget target, ArticleFiles af, Emitter emitter)
          throws IOException, PluginException {
          org.lockss.extractor.ArticleMetadata md = new ArticleMetadata();
          articleNumber++;
          String doi = "10.1357/9781585623174." + articleNumber; 
          md.put(MetadataField.FIELD_DOI,doi);
          md.put(MetadataField.FIELD_ISBN,"978-1-58562-317-4");
          md.put(MetadataField.FIELD_DATE,"1993");
          md.put(MetadataField.FIELD_START_PAGE,"" + articleNumber);
          md.put(MetadataField.FIELD_JOURNAL_TITLE,"Manual of Clinical Psychopharmacology");
          md.put(MetadataField.FIELD_ARTICLE_TITLE,"Title[" + doi + "]");
          md.put(MetadataField.FIELD_AUTHOR,"Author1[" + doi + "]");
          md.put(MetadataField.FIELD_AUTHOR,"Author2[" + doi + "]");
          md.put(MetadataField.FIELD_AUTHOR,"Author3[" + doi + "]");
          md.put(MetadataField.FIELD_ACCESS_URL, 
             "http://www.title2.org/plugin2/1993/p"+articleNumber);
          emitter.emitMetadata(af, md);
        }
      };
    }
    public ExternalizableMap getDefinitionMap() {
      ExternalizableMap map = new ExternalizableMap();
      map.putString("au_start_url", "\"%splugin2/%s\", base_url, year");
      return map;
    }
  }
  
  public static class MySimulatedPlugin3 extends MySimulatedPlugin {
    public MySimulatedPlugin3() {
      simulatedArticleMetadataExtractor = new ArticleMetadataExtractor() {
        int articleNumber = 0;
        public void extract(MetadataTarget target, ArticleFiles af, Emitter emitter)
          throws IOException, PluginException {
          org.lockss.extractor.ArticleMetadata md = new ArticleMetadata();
          articleNumber++;
          String doiPrefix = "10.0135/12345678.1999-11.12";
          String doi = doiPrefix + "." + articleNumber; 
          md.put(MetadataField.FIELD_DOI,doi);
          md.put(MetadataField.FIELD_ISBN,"976-1-58562-317-7");
          md.put(MetadataField.FIELD_DATE,"1999");
          md.put(MetadataField.FIELD_START_PAGE,"" + articleNumber);
          md.put(MetadataField.FIELD_JOURNAL_TITLE,"Journal[" + doiPrefix + "]");
          md.put(MetadataField.FIELD_ARTICLE_TITLE,"Title[" + doi + "]");
          md.put(MetadataField.FIELD_AUTHOR,"Author1[" + doi + "]");
          md.put(MetadataField.FIELD_ACCESS_URL, 
                  "http://www.title3.org/plugin3/1999/p"+articleNumber);
          emitter.emitMetadata(af, md);
        }
      };
    }
    public ExternalizableMap getDefinitionMap() {
      ExternalizableMap map = new ExternalizableMap();
      map.putString("au_start_url", "\"%splugin3/%s\", base_url, year");
      return map;
    }
  }

  /*
   * Test resolving a journal article using the DOI of the article.
   */
  public void testResolveFromRftIdDoi() {
    // from SimulatedPlugin0
    // expect url for article with specified DOI
    Map<String,String> params = new HashMap<String,String>();
    params.put("rft_id", "info:doi/" + "10.1234/12345678.2010-Q1.1"); 
    String url = openUrlResolver.resolveOpenUrl(params);
    if (disableMetadataManager) {
      // not a real DOI so just get the URL we sent dx.doi.org
      assertEquals("http://dx.doi.org/10.1234/12345678.2010-Q1.1", url);
    } else {
      assertEquals("http://www.title0.org/plugin0/XI/1st Quarter/p1", url);
    }
  }
  
  /**
   * Test resolving a book chapter using an ISBN plus either
   * the page, the article number, the author, or the article title.
   */
  public void testResolveFromIsbn() {
    String url;
      
    Map<String,String> params = new HashMap<String,String>();

    // from SimulatedPlugin2 with ISBN and start page
    // expect url for specified chapter of the book
    params.clear();
    params.put("rft.isbn", "978-1-58562-317-4");
    params.put("rft.spage", "4");
    url = openUrlResolver.resolveOpenUrl(params);
	if (disableMetadataManager) {
	  assertEquals("http://www.title2.org/", url);
	} else {
	  assertEquals("http://www.title2.org/plugin2/1993/p4", url);
	}
    
    // from SimulatedPlugin2 with ISBN and bad start page
    // expect landing page since the page number is bad
    params.clear();
    params.put("rft.isbn", "978-1-58562-317-4");
    params.put("rft.spage", "bad");
    url = openUrlResolver.resolveOpenUrl(params);
    assertEquals("http://www.title2.org/", url);
	
    // from SimulatedPlugin2 with ISBN and article number
    // expect url for specified chapter
    params.clear();
    params.put("rft.isbn", "978-1-58562-317-4");
    params.put("rft.artnum", "2");
    url = openUrlResolver.resolveOpenUrl(params);
	if (disableMetadataManager) {
	  assertEquals("http://www.title2.org/", url);
	} else {
	  assertEquals("http://www.title2.org/plugin2/1993/p2", url);
	}

    // from SimulatedPlugin2 with ISBN and author
    // expect url for author's chapter of the book
    params.clear();
    params.put("rft.isbn", "978-1-58562-317-4");
    params.put("rft.au", "Author2[10.1357/9781585623174.1]");
    url = openUrlResolver.resolveOpenUrl(params);
	if (disableMetadataManager) {
	  assertEquals("http://www.title2.org/", url);
	} else {
	  assertEquals("http://www.title2.org/plugin2/1993/p1", url);
	}
	
    // from SimulatedPlugin2 with ISBN only
    // expect url for specified article of the book
    params.clear();
    params.put("rft.isbn", "978-1-58562-317-4");
    params.put("rft.atitle", "Title[10.1357/9781585623174.1]");
    url = openUrlResolver.resolveOpenUrl(params);
	if (disableMetadataManager) {
	  assertEquals("http://www.title2.org/", url);
	} else {
	  assertEquals("http://www.title2.org/plugin2/1993/p1", url);
	}
	
    // from SimulatedPlugin2 with ISBN, start page, author, and title
    // expect url for specified article for author and page number
    params.clear();
    params.put("rft.isbn", "978-1-58562-317-4");
    params.put("rft.atitle", "Title[10.1357/9781585623174.1]");
    params.put("rft.au", "Author2[10.1357/9781585623174.1]");
    params.put("rft.spage", "1");
    url = openUrlResolver.resolveOpenUrl(params);
	if (disableMetadataManager) {
	  assertEquals("http://www.title2.org/", url);
	} else {
	  assertEquals("http://www.title2.org/plugin2/1993/p1", url);
	}

	// from SimulatedPlugin2 with ISBN, start page, author, and title
    // expect url for specified article for author and page number
    params.clear();
    params.put("rft.isbn", "978-1-58562-317-4");
    params.put("rft.date", "1993");
    url = openUrlResolver.resolveOpenUrl(params);
	if (disableMetadataManager) {
	  assertEquals("http://www.title2.org/plugin2/1993", url);
	} else {
	  assertEquals("http://www.title2.org/plugin2/1993", url);
	}
  }
  
  /**
   * Test resolving a book chapter using the publisher and book title.
   */
  public void testResolveFromBookTitle() {
    // these tests require a TDB
    if (ConfigManager.getCurrentConfig().getTdb() == null) {
       return;
    }

    Map<String,String> params;
    String url;
	
    // from SimulatedPlugin2 with book publisher, title with start page
    // expect url for chapter on specified page
    params = new HashMap<String,String>();
    params.put("rft.pub", "Publisher[Manual of Clinical Psychopharmacology]");
    params.put("rft.btitle", "Manual of Clinical Psychopharmacology");
    params.put("rft.spage", "1");
    url = openUrlResolver.resolveOpenUrl(params);
	if (disableMetadataManager) {
	  assertEquals("http://www.title2.org/", url);
	} else {
	  assertEquals("http://www.title2.org/plugin2/1993/p1", url);
	}

    
    // from SimulatedPlugin2 book title and page only, without publisher
	// expect url for chapter on specified page
    params.clear();
    params.put("rft.btitle", "Manual of Clinical Psychopharmacology");
    params.put("rft.spage", "1");
    url = openUrlResolver.resolveOpenUrl(params);
	if (disableMetadataManager) {
	  assertEquals("http://www.title2.org/", url);
	} else {
	  assertEquals("http://www.title2.org/plugin2/1993/p1", url);
	}

    // from SimulatedPlugin2 book title and page only, without publisher
	// expect url for specified article
    params.clear();
    params.put("rft.btitle", "Manual of Clinical Psychopharmacology");
    params.put("rft.atitle", "Title[10.1357/9781585623174.1]");
    url = openUrlResolver.resolveOpenUrl(params);
	if (disableMetadataManager) {
	  assertEquals("http://www.title2.org/", url);
	} else {
	  assertEquals("http://www.title2.org/plugin2/1993/p1", url);
	}

    // from SimulatedPlugin2 book title only
    // expect url of book landing page
    params.clear();
    params.put("rft.btitle", "Manual of Clinical Psychopharmacology");
    url = openUrlResolver.resolveOpenUrl(params);
    assertEquals("http://www.title2.org/", url);

    // from SimulatedPlugin2 book title and year
    // expect url of book landing page
    params.clear();
    params.put("rft.btitle", "Manual of Clinical Psychopharmacology");
    params.put("rft.date", "1993");
    url = openUrlResolver.resolveOpenUrl(params);
    assertEquals("http://www.title2.org/plugin2/1993", url);
  }
  
  /**
   * Test resolving a book chapter using an ISBN plus either
   * the page, the article number, the author, or the article title.
   */
  public void testResolveFromIssn() {
    String url;    
    Map<String,String> params = new HashMap<String,String>();

    // from SimulatedPlugin1, journal ISSN only
    // expect base_url
    params.put("rft.issn", "1144-875X");
    url = openUrlResolver.resolveOpenUrl(params);
    assertEquals("http://www.title1.org/", url);

    // from SimulatedPlugin1, journal EISSN only
    // expect base_url (eventually title TOC)
    params.clear();
    params.put("rft.eissn", "7744-6521");
    url = openUrlResolver.resolveOpenUrl(params);
    assertEquals("http://www.title1.org/", url);
    
    // from SimulatedPlugin1, journal ISSN and article number only
    // expect base_url (eventually title TOC) since article number is not unique
    params.clear();
    params.put("rft.issn", "1144-875X");
    params.put("rft.artnum", "1");
    url = openUrlResolver.resolveOpenUrl(params);
    assertEquals("http://www.title1.org/", url);

    // from SimulatedPlugin1, journal ISSN, volume, issue, and article title
    // expect article URL
    params.clear();
    params.put("rft.issn", "1144-875X");
    params.put("rft.volume", "42");
    params.put("rft.issue", "Summer");
    params.put("rft.atitle", "Title[10.2468/28681357.2010-S2.1]");
    url = openUrlResolver.resolveOpenUrl(params);
	if (disableMetadataManager) {
	  assertEquals("http://www.title1.org/plugin1/v_42/i_Summer/toc", url);
	} else {
	  assertEquals("http://www.title1.org/plugin1/v_42/i_Summer/p_1", url);
	}

    // from SimulatedPlugin1, journal ISSN, volume, issue, and article author
    // expect article URL
    params.clear();
    params.put("rft.issn", "1144-875X");
    params.put("rft.volume", "42");
    params.put("rft.issue", "Summer");
    params.put("rft.au", "Author1[10.2468/28681357.2010-S2.1]");
    url = openUrlResolver.resolveOpenUrl(params);
	if (disableMetadataManager) {
	  assertEquals("http://www.title1.org/plugin1/v_42/i_Summer/toc", url);
	} else {
	  assertEquals("http://www.title1.org/plugin1/v_42/i_Summer/p_1", url);
	}

    // from SimulatedPlugin1, journal ISSN, volume, issue, and start page
    // expect article URL
    params.clear();
    params.put("rft.issn", "1144-875X");
    params.put("rft.volume", "42");
    params.put("rft.issue", "Summer");
    params.put("rft.spage", "1");
    url = openUrlResolver.resolveOpenUrl(params);
	if (disableMetadataManager) {
  	  assertEquals("http://www.title1.org/plugin1/v_42/i_Summer/toc", url);
  	} else {
  	  assertEquals("http://www.title1.org/plugin1/v_42/i_Summer/p_1", url);
  	}

    // from SimulatedPlugin1, journal ISSN, and article title only
    // expect article URL because article title is unique across the journal
    params.clear();
    params.put("rft.issn", "1144-875X");
    params.put("rft.atitle", "Title[10.2468/28681357.2010-S2.1]");
    url = openUrlResolver.resolveOpenUrl(params);
	if (disableMetadataManager) {
  	  assertEquals("http://www.title1.org/", url);
  	} else {
  	  assertEquals("http://www.title1.org/plugin1/v_42/i_Summer/p_1", url);
  	}

    // from SimulatedPlugin1, journal ISSN, and article article author only
    // expect article_url because author only wrote one article for this journal
    params.clear();
    params.put("rft.issn", "1144-875X");
    params.put("rft.au", "Author1[10.2468/28681357.2010-S2.1]");
    url = openUrlResolver.resolveOpenUrl(params);
	if (disableMetadataManager) {
   	  assertEquals("http://www.title1.org/", url);
   	} else {
   	  assertEquals("http://www.title1.org/plugin1/v_42/i_Summer/p_1", url);
   	}
  }
  
  /**
   * Test resolving a book chapter using the publisher and book title.
   */
  public void testResolveFromJournalTitle() {
    // these tests require a TDB
    if (ConfigManager.getCurrentConfig().getTdb() == null) {
      return;
    }
    
    String url;
    
    // from SimulatedPlugin1
    // journal title with publisher and page
    // expect base_url because start page not unique across issues
    Map<String,String> params = new HashMap<String,String>();
    params.put("rft.pub", "Publisher[10.2468/24681357]");
    params.put("rft.jtitle", "Journal[10.2468/24681357]");
    params.put("rft.spage", "1");
    url = openUrlResolver.resolveOpenUrl(params);
    assertEquals("http://www.title1.org/", url);
    
    // from SimulatedPlugin1
    // journal title and page only, without publisher
    // expect base_url because start page not unique across issues
    params.clear();
    params.put("rft.jtitle", "Journal[10.2468/24681357]");
    params.put("rft.spage", "1");
    url = openUrlResolver.resolveOpenUrl(params);
    assertEquals("http://www.title1.org/", url);

    // from SimulatedPlugin1
    // journal title and invalid page only, without publisher
    // expect base_url because start page not unique across issue
    params.clear();
    params.put("rft.jtitle", "Journal[10.2468/24681357]");
    params.put("rft.spage", "1");
    url = openUrlResolver.resolveOpenUrl(params);
    assertEquals("http://www.title1.org/", url);

    // from SimulatedPlugin1
    // journal title and invalid page only, without publisher
    // expect volume url 
    params.clear();
    params.put("rft.jtitle", "Journal[10.2468/24681357]");
    params.put("rft.volume", "42");
    url = openUrlResolver.resolveOpenUrl(params);
    assertEquals("http://www.title1.org/plugin1/v_42/toc", url);

    // from SimulatedPlugin3
    // book title and page only, without publisher
    // expect article url because start page is unique within the book
    params.clear();
    params.put("rft.btitle", "Journal[10.0135/12345678]");
    params.put("rft.spage", "1");
    url = openUrlResolver.resolveOpenUrl(params);
	if (disableMetadataManager) {
  	  assertEquals("http://www.title3.org/", url);
	} else {
	  assertEquals("http://www.title3.org/plugin3/1999/p1", url);
	}

    // from SimulatedPlugin3
    // book title only, without publisher
    // expect au url for book
    params.clear();
    params.put("rft.btitle", "Journal[10.0135/12345678]");
    url = openUrlResolver.resolveOpenUrl(params);
    assertEquals("http://www.title3.org/", url);
  }
}
