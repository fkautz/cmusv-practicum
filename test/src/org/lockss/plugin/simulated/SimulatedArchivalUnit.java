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

import java.util.*;
import java.io.File;
import org.lockss.daemon.*;
import org.lockss.util.*;
import org.lockss.plugin.*;
import org.lockss.plugin.base.*;

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
 * This is the url which the Crawler should start at.
 */
  public static final String SIMULATED_URL_START =
    "http://www.example.com/index.html";

  /**
   * This is the root of the url which the SimAU pretends to be.
   * It is replaced with the actual directory root.
   */
  public static final String SIMULATED_URL_ROOT = "http://www.example.com";

  private String fileRoot; //root directory for the generated content
  private SimulatedContentGenerator scgen;
  private String auId = StringUtil.gensym("SimAU_");

  public SimulatedArchivalUnit(Plugin owner) {
    super(owner, new CrawlSpec(SIMULATED_URL_START, null));
  }

  /** Convenience methods, as most creators don't care about the plugin */
  public SimulatedArchivalUnit() {
    this(new SimulatedPlugin());
  }

  public void setConfiguration(Configuration config)
      throws ArchivalUnit.ConfigurationException {
    // tk - not right, generator might have been created already with
    // different root?
    try {
      fileRoot = config.get(SimulatedPlugin.PARAM_ROOT);
      SimulatedContentGenerator gen = getContentGenerator();
      if (config.containsKey(SimulatedPlugin.PARAM_DEPTH)) {
	gen.setTreeDepth(config.getInt(SimulatedPlugin.PARAM_DEPTH));
      }
      if (config.containsKey(SimulatedPlugin.PARAM_BRANCH)) {
	gen.setNumBranches(config.getInt(SimulatedPlugin.PARAM_BRANCH));
      }
      if (config.containsKey(SimulatedPlugin.PARAM_NUM_FILES)) {
	gen.setNumFilesPerBranch(config.getInt(SimulatedPlugin.PARAM_NUM_FILES));
      }
      if (config.containsKey(SimulatedPlugin.PARAM_BIN_FILE_SIZE)) {
	gen.setBinaryFileSize(config.getInt(SimulatedPlugin.PARAM_BIN_FILE_SIZE));
      }
      if (config.containsKey(SimulatedPlugin.PARAM_MAXFILE_NAME)) {
	gen.setMaxFilenameLength(config.getInt(SimulatedPlugin.PARAM_MAXFILE_NAME));
      }
      if (config.containsKey(SimulatedPlugin.PARAM_FILE_TYPES)) {
	gen.setFileTypes(config.getInt(SimulatedPlugin.PARAM_FILE_TYPES));
      }
      if (config.containsKey(SimulatedPlugin.PARAM_BAD_FILE_LOC) &&
	  config.containsKey(SimulatedPlugin.PARAM_BAD_FILE_NUM)) {
	gen.setAbnormalFile(config.get(SimulatedPlugin.PARAM_BAD_FILE_LOC),
			    config.getInt(SimulatedPlugin.PARAM_BAD_FILE_NUM));
      }
      resetContentTree();
    } catch (Configuration.InvalidParam e) {
      throw new ArchivalUnit.ConfigurationException("Bad config value", e);
    }
  }

  public CachedUrlSet cachedUrlSetFactory(ArchivalUnit owner,
					  CachedUrlSetSpec cuss) {
    return new GenericFileCachedUrlSet(owner, cuss);
  }

  public CachedUrl cachedUrlFactory(CachedUrlSet owner, String url) {
    return new GenericFileCachedUrl(owner, checkUrlFormat(url));
  }

  public UrlCacher urlCacherFactory(CachedUrlSet owner, String url) {
    return new SimulatedUrlCacher(owner, checkUrlFormat(url), fileRoot);
  }

  public String getAUId() {
    // must agree with what SimulatedPlugin.getAUIdFromConfig() returns
    return fileRoot;
  }

  // public methods

  /** Set the directory where simulated content is generated */
  public void setRootDir(String rootDir) {
    fileRoot = rootDir;
  }

  /** Returns the directory where simulated content is generated */
  public String getRootDir() {
    return fileRoot; }

  /**
   * Returns the {@link SimulatedContentGenerator} for setting
   * parameters.
   * @return the generator
   */
  public SimulatedContentGenerator getContentGenerator() {
    if (scgen == null) {
      scgen = new SimulatedContentGenerator(fileRoot);
    }
    return scgen;
  }

  /**
   * generateContentTree() generates the simulated content.
   */
  public void generateContentTree() {
    if (!getContentGenerator().isContentTree()) {
      getContentGenerator().generateContentTree();
    }
  }

  /**
   * resetContentTree() deletes and regenerates the simulated content,
   * restoring it to its starting state.
   */
  public void resetContentTree() {
    // clears and restores content tree to starting state
    if (getContentGenerator().isContentTree()) {
      getContentGenerator().deleteContentTree();
    }
    getContentGenerator().generateContentTree();
  }

  public void alterContentTree() {
    //XXX alters in a repeatable manner
  }

  /**
   * deleteContentTree() deletes the simulated content.
   */
  public void deleteContentTree() {
    getContentGenerator().deleteContentTree();
  }

  public void pause() {
    // no pauses since this is a test unit
  }

  /**
   * mapUrlToContentFileName()
   * This maps a given url to a content file location.
   *
   * @param url the url to map
   * @return fileName the mapping result
   */
  public static String mapUrlToContentFileName(String url) {
    String urlStr = checkUrlFormat(url);
    urlStr = StringUtil.replaceString(urlStr, SIMULATED_URL_ROOT,
                             SimulatedContentGenerator.ROOT_NAME);
    return urlStr;
  }

  private static String checkUrlFormat(String url) {
    int lastSlashIdx = url.lastIndexOf(File.separator);
    int lastPeriodIdx = url.lastIndexOf(".");

    if ((lastSlashIdx >= lastPeriodIdx) ||
        (StringUtil.countOccurences(url, File.separator)==2)) {
      StringBuffer buffer = new StringBuffer(url);
      if (!url.endsWith(File.separator)) {
        buffer.append(File.separator);
      }
      buffer.append("index.html");
      return buffer.toString();
    } else {
      return url;
    }
  }

  public List getNewContentCrawlUrls() {
    return ListUtil.list(SIMULATED_URL_START);
  }

}
