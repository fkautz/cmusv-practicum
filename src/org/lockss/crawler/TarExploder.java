/*
 * $Id: TarExploder.java,v 1.15 2011/06/20 06:59:15 tlipkis Exp $
 */

/*

Copyright (c) 2007-2011 Board of Trustees of Leland Stanford Jr. University,
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
import java.io.*;
import org.lockss.daemon.*;
import org.lockss.util.*;
import org.lockss.util.urlconn.*;
import org.lockss.plugin.*;
import org.lockss.plugin.ExploderHelper;
import org.lockss.plugin.base.*;
import org.lockss.plugin.exploded.*;
import org.lockss.crawler.BaseCrawler;
import org.lockss.config.Configuration;
import org.lockss.app.LockssDaemon;
import org.lockss.state.*;
import org.lockss.config.*;
import com.ice.tar.*;

/**
 * The Exploder for TAR archives.
 *
 * @author  David S. H. Rosenthal
 * @version 0.0
 */

public class TarExploder extends Exploder {

  private static Logger logger = Logger.getLogger("TarExploder");
  protected ExploderHelper helper = null;
  protected int reTry = 0;
  protected CIProperties arcProps = null;

  /**
   * Constructor
   * @param uc UrlCacher for the archive
   * @param maxRetries
   * @param crawlSpec the CrawlSpec for the crawl that found the archive
   * @param crawler the crawler that found the archive
   * @param explode true to explode the archives
   * @param store true to store the archive as well
   */
  protected TarExploder(UrlCacher uc, int maxRetries, CrawlSpec crawlSpec,
		     BaseCrawler crawler, boolean explode, boolean store) {
    super(uc, maxRetries, crawlSpec, crawler, explode, store);
    helper = crawlSpec.getExploderHelper();
    if (helper == null) {
      helper = new DefaultExploderHelper(uc, crawlSpec, crawler);
    }
  }

  /**
   * Explode the archive into its constituent elements
   */
  protected void explodeUrl() throws CacheException {
    InputStream arcStream = null;
    CachedUrl cachedUrl = null;
    TarInputStream tis = null;
    int goodEntries = 0;
    int badEntries = 0;
    int ignoredEntries = 0;
    int entriesBetweenSleep = 0;
    String tarArchiveUrl = archiveUrl;
    logger.info((storeArchive ? "Storing" : "Fetching") + " a TAR file: " +
		archiveUrl + (explodeFiles ? " will" : " won't") + " explode");
    while (++reTry < maxRetries) try {
      goodEntries = 0;
      badEntries = 0;
      if (storeArchive) {
	crawler.cacheWithRetries(urlCacher);
	// Get a stream from which the TAR data can be read
	logger.debug3("About to get TAR stream from " + urlCacher.toString());
	cachedUrl = urlCacher.getCachedUrl();
	arcStream = cachedUrl.getUnfilteredInputStream();
      } else {
	arcStream = urlCacher.getUncachedInputStream();
	arcProps = urlCacher.getUncachedProperties();
      }
      tis = new TarInputStream(arcStream);
      TarEntry te;
      while ((te = tis.getNextEntry()) != null) {
	// XXX probably not necessary
	if (crawler.wdog != null) {
	  crawler.wdog.pokeWDog();
	}
	if ((++entriesBetweenSleep % sleepAfter) == 0) {
	  long pauseTime =
            CurrentConfig.getTimeIntervalParam(PARAM_RETRY_PAUSE,
                                               DEFAULT_RETRY_PAUSE);
	  Deadline pause = Deadline.in(pauseTime);
	  logger.debug3("Sleeping for " +
			StringUtil.timeIntervalToString(pauseTime));
	  while (!pause.expired()) {
	    try {
	      pause.sleep();
	    } catch (InterruptedException ie) {
	      // no action
	    }
	  }
	}
	if (!te.isDirectory()) {
	  ArchiveEntry ae = new ArchiveEntry(te.getName(),
					     te.getSize(),
					     te.getModTime().getTime(),
					     tis, crawlSpec, this,
					     urlCacher.getUrl());
	  long bytesStored = ae.getSize();
	  logger.debug3("ArchiveEntry: " + ae.getName()
			+ " bytes "  + bytesStored);
          try {
	    helper.process(ae);
          } catch (PluginException ex) {
	    throw new CacheException.ExploderException("helper.process() threw " +
						   ex);
          }
	  if (ae.getBaseUrl() != null) {
	    if (ae.getRestOfUrl() != null &&
		ae.getHeaderFields() != null) {
	      storeEntry(ae);
	      handleAddText(ae);
	      goodEntries++;
	      crawler.getCrawlerStatus().addContentBytesFetched(bytesStored);
	    } else {
	      ignoredEntries++;
	    }
	  } else {
	    badEntries++;
	    logger.debug2("Can't map " + te.getName() + " from " + archiveUrl);
	  }
	} else {
	  logger.debug2("Directory " + te.getName() + " in " + archiveUrl);
	}
      }
      addText();
      if (badEntries > 0) {
	String msg = archiveUrl + " had " + badEntries + "/" +
	  (goodEntries + badEntries) + " bad entries";
	if (ignoredEntries > 0) {
	  msg += " " + ignoredEntries + " ignored";
	}
	throw new CacheException.ExploderException(msg);
      } else {
	String msg = archiveUrl + " had " + goodEntries + " entries";
	if (ignoredEntries > 0) {
	  msg += " " + ignoredEntries + " ignored";
	}
	logger.info(msg);
	if (!storeArchive) {
	  // Leave stub archive behind to prevent re-fetch
	  byte[] dummy = { 0, };
	  urlCacher.storeContent(new ByteArrayInputStream(dummy), arcProps);
	}
	reTry = maxRetries+1;
      }
      break;
    } catch (IOException ex) {
      throw new CacheException.ExploderException(ex);
    } finally {
      if (cachedUrl != null) {
	cachedUrl.release();
      }
      IOUtil.safeClose(tis);
      IOUtil.safeClose(arcStream);
    }
    if (badEntries == 0) {
      if (reTry >= maxRetries && goodEntries > 0) {
	// Make it look like a new crawl finished on each AU to which
	// URLs were added.
	for (Iterator it = touchedAus.iterator(); it.hasNext(); ) {
	  ArchivalUnit au = (ArchivalUnit)it.next();
	  logger.debug3(archiveUrl + " touching " + au.toString());
	  crawler.getDaemon().getNodeManager(au).newContentCrawlFinished();
	}
      }
    } else {
      ArchivalUnit au = crawler.getAu();
      String msg = archiveUrl + ": " + badEntries + "/" +
	goodEntries + " bad entries";
      throw new CacheException.UnretryableException(msg);
    }
  }

}
