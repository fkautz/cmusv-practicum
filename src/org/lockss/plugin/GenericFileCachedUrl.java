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

package org.lockss.plugin;

import java.io.*;
import java.util.Properties;
import java.net.MalformedURLException;
import org.lockss.app.*;
import org.lockss.crawler.HtmlTagFilter;
import org.lockss.daemon.*;
import org.lockss.repository.*;
import org.lockss.util.*;
import org.lockss.plugin.*;
import org.lockss.plugin.base.*;

/**
 * This is a generic file implementation of CachedUrl which uses the
 * {@link LockssRepository}.
 *
 * @author  Emil Aalto
 * @version 0.0
 */
public class GenericFileCachedUrl extends BaseCachedUrl {
  private LockssRepository repository;
  private RepositoryNode leaf = null;
  protected static Logger logger = Logger.getLogger("CachedUrl");

  private static final String PARAM_SHOULD_FILTER_HASH_STREAM =
    Configuration.PREFIX+".genericFileCachedUrl.filterHashStream";

  public GenericFileCachedUrl(CachedUrlSet owner, String url) {
    super(owner, url);
  }

  public boolean hasContent() {
    ensureLeafLoaded();
    return leaf.hasContent();
  }

  public InputStream openForReading() {
    ensureLeafLoaded();
    return leaf.getNodeContents().input;
  }

  /**
   * Currently simply returns 'openForReading()'.
   * @return an InputStream
   */
  public InputStream openForHashing() {
     if (Configuration.getBooleanParam(PARAM_SHOULD_FILTER_HASH_STREAM,
  				      false)) {
       return getFilteredStream();
     } else {
       return openForReading();
     }
  }

  private InputStream getFilteredStream() {
    //XXX test me
    Properties props = getProperties();
    if ("text/html".equals(props.getProperty("content-type"))) {
      logger.debug2("Filtering "+url);
//       return new ReaderInputStream(getReader());
      HtmlTagFilter.TagPair tagPair = new HtmlTagFilter.TagPair("<", ">");
      Reader filteredReader = new HtmlTagFilter(getReader(), tagPair);
      return new ReaderInputStream(filteredReader);
    }
    logger.debug2("Not filtering "+url);
    return openForReading();
  }

  public Reader getReader() {
    ensureLeafLoaded();
    return leaf.getNodeContents().reader;
  }

  public Properties getProperties() {
    ensureLeafLoaded();
    return leaf.getNodeContents().props;
  }

  public byte[] getContentSize() {
    ensureLeafLoaded();
    return ByteArray.encodeLong(leaf.getContentSize());
  }

  private void ensureLeafLoaded() {
    if (repository==null) {
      repository = (LockssRepository)LockssDaemon.getAUSpecificManager(
          LockssDaemon.LOCKSS_REPOSITORY, cus.getArchivalUnit());
    }
    if (leaf==null) {
      try {
        leaf = repository.createNewNode(url);
      } catch (MalformedURLException mue) {
        logger.error("Couldn't load node due to bad url: "+url);
        throw new IllegalArgumentException("Couldn't parse url properly.");
      }
    }
  }
}

