/*
 * $Id$
 */

/*

Copyright (c) 2007 Board of Trustees of Leland Stanford Jr. University,
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
import org.archive.io.*;
import org.archive.io.arc.*;
import org.lockss.util.*;
import org.lockss.config.*;
import org.lockss.daemon.*;
import org.lockss.plugin.*;
import org.lockss.state.*;

/**
 * A crawler that extends NewContentCrawler to both ingest Internet
 * Archive ARC files,  and to behave as if it had ingested each
 * file in each ARC file directly from its original source.
 *
 * @author  David S. H. Rosenthal
 * @version 0.0
 */

public class ArcCrawler extends NewContentCrawler {

  private static Logger logger = Logger.getLogger("ArcCrawler");
  private String arcUrl = null;
  protected static final String PARAM_ARC_FILE_SUFFIX =
    Configuration.PREFIX + "ArcCrawler.suffix";
  private static final String DEFAULT_ARC_FILE_SUFFIX = "\\.arc\\.gz$";
  protected String arcFileExtension = DEFAULT_ARC_FILE_SUFFIX;
  protected static final String PARAM_EXPLODE_ARC_FILES =
    Configuration.PREFIX + "ArcCrawler.explodeArcFiles";
  private static final boolean DEFAULT_EXPLODE_ARC_FILES = true;
  protected boolean explodeArcFiles = DEFAULT_EXPLODE_ARC_FILES;
  private Configuration myConfig = null;
  private String arcPattern = null;

  public ArcCrawler(ArchivalUnit au, CrawlSpec crawlSpec, AuState aus) {
    super(au, crawlSpec, aus);
    arcPattern = crawlSpec.arcFilePattern();
    if (arcPattern == null) {
      arcPattern = DEFAULT_ARC_FILE_SUFFIX;
    }
  }

  protected void setCrawlConfig(Configuration config) {
    super.setCrawlConfig(config);
    arcFileExtension = config.get(PARAM_ARC_FILE_SUFFIX,
				  DEFAULT_ARC_FILE_SUFFIX);
    explodeArcFiles = config.getBoolean(PARAM_EXPLODE_ARC_FILES,
					DEFAULT_EXPLODE_ARC_FILES);
    logger.info("Files ending " + arcFileExtension +
		(explodeArcFiles ? " will" : " won't") +
		" be exploded");
  }

  public int getType() {
    return Crawler.ARC;
  }

  public String getTypeString() {
    return "ARC";
  }

  protected void fetchedUrlHook(UrlCacher uc) {
    arcUrl = uc.getUrl();
    if (RegexpUtil.isMatchRe(arcUrl, arcPattern)) {
      // We just fetched and cached an ARC file.
      logger.info("Fetched an ARC file: " + uc.getUrl() +
		  (explodeArcFiles ? " will" : " won't") + " explode");
      InputStream arcStream = null;
      CachedUrl cachedUrl = null;
      ArchiveReader arcReader = null;
      try {
	// Get a stream from which the ARC data can be read
	logger.debug3("About to get ARC stream from " + uc.toString());
	cachedUrl = uc.getCachedUrl();
	arcStream = cachedUrl.getUnfilteredInputStream();
	// Wrap it in an ArchiveReader
	logger.debug3("About to wrap stream");
	arcReader = wrapStream(uc, arcStream);
	logger.debug3("wrapStream() returns " + (arcReader == null ? "null" : "non-null"));
	// Explode it
	if (arcReader != null) {
          explode(uc.getUrl(), uc.getArchivalUnit(), arcReader);
        }
      } catch (IOException ex) {
	logger.siteError("ArcCrawler.explode() threw", ex);
      } finally {
	if (arcReader != null) try {
	  arcReader.close();
	  arcReader = null;
	} catch (IOException ex) {
	  logger.error(uc.getUrl() + " arcReader.close() threw ", ex);
	}
	if (cachedUrl != null) {
	  cachedUrl.release();
	}
	IOUtil.safeClose(arcStream);
      }
    } else {
      logger.info("Fetched a non-ARC file: " + uc.getUrl());
    }
  }

  protected CIProperties makeCIProperties(ArchiveRecordHeader elementHeader) {
    CIProperties ret = new CIProperties();
    Set elementHeaderFieldKeys = elementHeader.getHeaderFieldKeys();
    for (Iterator i = elementHeaderFieldKeys.iterator(); i.hasNext(); ) {
      String key = (String) i.next();
      try {
	String value = (String) elementHeader.getHeaderValue(key).toString();
	logger.debug3(key + ": " + value);
	ret.put(key, value);
      } catch (ClassCastException ex) {
	logger.error("makeCIProperties: " + key + " threw ", ex);
      }
    }
    return (ret);
  }

  protected void explode(String arcUrl, ArchivalUnit au, ArchiveReader arcReader) throws IOException {
    int elementCount = 1;
    Set stemSet = new HashSet();
    logger.debug("Exploding " + arcUrl);
    // Iterate through the elements in the ARC file, except the first
    Iterator i = arcReader.iterator();
    // Skip first record
    i.next();
    while (i.hasNext()) {
      // XXX probably not necessary
      if (wdog != null) {
	wdog.pokeWDog();
      }
      ArchiveRecord element = (ArchiveRecord)i.next();
      // Each element is a URL to be cached in the AU
      ArchiveRecordHeader elementHeader = element.getHeader();
      String elementUrl = elementHeader.getUrl();
      String elementMimeType = elementHeader.getMimetype();
      logger.debug2("ARC url " + elementUrl + " mime " + elementMimeType + " # " + elementCount);
      if (elementUrl.startsWith("http:")) {
	// Create a new UrlCacher from the ArchivalUnit and store the
	// element using it.
	UrlCacher newUc = au.makeUrlCacher(elementUrl);
	// XXX either fetch or storeContent synthesizes some properties
	// XXX for the URL - check and move the place to storeContent
	newUc.storeContent(element, makeCIProperties(elementHeader));
	stemSet.add(UrlUtil.getUrlPrefix(elementUrl));
	elementCount++;
      }
    }
    // Now adjust the AU's host list and crawl rules if necessary
    logger.debug("Exploding " + arcUrl + " found " + elementCount + " URLs");
    updateAU(stemSet);
  }

  protected ArchiveReader wrapStream(UrlCacher uc, InputStream arcStream) throws IOException {
    ArchiveReader ret = null;
    if (explodeArcFiles) {
	logger.debug3("Getting an ArchiveReader");
      ret = ArchiveReaderFactory.get(uc.getUrl(), arcStream, true);
      // Just don't ask why the next line is necessary
      ((ARCReader)ret).setParseHttpHeaders(false);
    }
    return (ret);
  }

  private void updateAU(Set stemSet) {
    for (Iterator i = stemSet.iterator(); i.hasNext(); ) {
      String url = (String)i.next();
      //  XXX do something to the AU.  XXX there is a getUrlStem()
      //  XXX on ArchivalUnit.  For ArcCrawler AUs this should
      //  XXX enumerate the second level of the repo to get
      //  XXX the list.
      logger.debug3("stem set includes: " + url);
    }
  }
}
