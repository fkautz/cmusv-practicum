/*
 * $Id$
 */

/*

Copyright (c) 2002 Board of Trustees of Leland Stanford Jr. University,
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

import org.lockss.daemon.*;
import org.lockss.util.*;
import org.lockss.plugin.*;

/**
 * This is ArchivalUnit of the simulated plugin, used for testing purposes.
 * It repeatably generates local content (via a file hierarchy),
 * with specific parameters obtained via Configuration.
 *
 * It emulates the fake URL 'www.simcontent.org'.
 *
 * @author  Emil Aalto
 * @version 0.0
 */

public class SimulatedArchivalUnit extends BaseArchivalUnit {
/**
 * This is the url prefix which the SimAU pretends to be.
 */
  public static final String SIMULATED_URL = "http://www.example.com";

  private String fileRoot; //root directory for the generated content
  private SimulatedContentGenerator scgen;

  public SimulatedArchivalUnit(String new_fileRoot) {
    super(new CrawlSpec(SIMULATED_URL, null));
    fileRoot = new_fileRoot;
    scgen = new SimulatedContentGenerator(fileRoot);
  }

  public SimulatedArchivalUnit() {
    this("");
  }

  public CachedUrlSet cachedUrlSetFactory(ArchivalUnit owner, CachedUrlSetSpec cuss) {
    return new GenericFileCachedUrlSet(owner, cuss);
  }

  public CachedUrl cachedUrlFactory(CachedUrlSet owner, String url) {
    return new GenericFileCachedUrl(owner, url);
  }

  public UrlCacher urlCacherFactory(CachedUrlSet owner, String url) {
    return new SimulatedUrlCacher(owner, url);
  }

  public CachedUrlSet getAUCachedUrlSet() {
    return new GenericFileCachedUrlSet(this,
               new RECachedUrlSetSpec(SIMULATED_URL));
  }

  public String getPluginId() {
    return "simulated";
  }

  public String getAUId() {
    return "content";
  }

  // public methods
  public String getUrlRoot() { return fileRoot; }

  /**
   * generateContentTree() generates the simulated content.
   */
  public void generateContentTree() {
    if (!scgen.isContentTree()) {
      scgen.generateContentTree();
    }
  }

  /**
   * resetContentTree() deletes and regenerates the simulated content,
   * restoring it to its starting state.
   */
  public void resetContentTree() {
    // clears and restores content tree to starting state
    if (scgen.isContentTree()) scgen.deleteContentTree();
    scgen.generateContentTree();
  }

  public void alterContentTree() {
    //XXX alters in a repeatable manner
  }

  /**
   * Returns the {@link SimulatedContentGenerator} for setting
   * parameters.
   * @return the generator
   */

  public SimulatedContentGenerator getContentGenerator() {
    return scgen;
  }

  /**
   * mapUrlToContentFileName()
   * This maps a given url to a content file location.
   *
   * @param url the url to map
   * @return fileName the mapping result
   */
  public static String mapUrlToContentFileName(String url) {
    int lastSlashIdx = url.lastIndexOf("/");
    int lastPeriodIdx = url.lastIndexOf(".");

    String fileName = StringUtil.replaceString(url, SIMULATED_URL,
                                 SimulatedContentGenerator.ROOT_NAME);

    if (lastSlashIdx >= lastPeriodIdx) {
      if (!url.endsWith("/")) {
        fileName += "/";
      }
      fileName += "index.html";
    }
    return fileName;
  }

}
