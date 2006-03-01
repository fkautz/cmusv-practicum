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

import java.net.*;
import java.util.*;
import java.io.File;

import org.lockss.config.Configuration;
import org.lockss.daemon.*;
import org.lockss.util.*;
import org.lockss.plugin.*;
import org.lockss.plugin.base.*;

/**
 * This is ArchivalUnit of the simulated plugin, used for testing purposes.
 * It repeatably generates local content (via a file hierarchy),
 * with specific parameters obtained via Configuration.
 *
 * It emulates the fake URL 'www.example.com'.
 *
 * @author  Emil Aalto
 * @version 0.0
 */

public class SimulatedArchivalUnit extends BaseArchivalUnit {
  static final Logger log = Logger.getLogger("SAU");

  public static final String SIMULATED_URL_STEM = "http://www.example.com";

  /**
   * This is the url which the Crawler should start at.
   */
  public static final String SIMULATED_URL_START =
    SIMULATED_URL_STEM + "/index.html";

  /**
   * This is the root of the url which the SimAU pretends to be.
   * It is replaced with the actual directory root.
   */
  public static final String SIMULATED_URL_ROOT = "http://www.example.com";

  private String fileRoot; //root directory for the generated content
  private SimulatedContentGenerator scgen;
  private String auId = StringUtil.gensym("SimAU_");
  String simRoot; //sim root dir returned by content generator
  private boolean doFilter = false;

  Set toBeDamaged = new HashSet();

  public SimulatedArchivalUnit(Plugin owner) {
    super(owner);
  }

  /** Convenience methods, as most creators don't care about the plugin */
  public SimulatedArchivalUnit() {
    this(new SimulatedPlugin());
  }

  public UrlCacher makeUrlCacher(String url) {
    String fileRoot = getRootDir();
    return new SimulatedUrlCacher(this, url, fileRoot);
  }


  public String getName() {
    return makeName();
  }

  protected String makeName() {
    return "Simulated Content: " + fileRoot;
  }

  protected String makeStartUrl() {
    return SIMULATED_URL_START;
  }

  /**
   * Override to provide proper filter rules.
   * @param mimeType the mime type
   * @return null, since we don't filter by default
   */
  protected FilterRule constructFilterRule(String mimeType) {
    log.debug3("constructFilterRule("+mimeType+")");
    if (doFilter) {
      return new SimulatedFilterRule();
    }
    return super.constructFilterRule(mimeType);
  }

  // public methods

  /**
   * Set the directory where simulated content is generated
   * @param rootDir the new root dir
   */
  public void setRootDir(String rootDir) {
    fileRoot = rootDir;
  }

  /**
   * Returns the directory where simulated content is generated
   * @return the root dir
   */
  public String getRootDir() {
    return fileRoot;
  }

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
      simRoot = getContentGenerator().generateContentTree();
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
    simRoot = getContentGenerator().generateContentTree();
  }

  public void alterContentTree() {
    //XXX alters in a repeatable manner
  }

  /** @return the top of the simulated content tree */
  public String getSimRoot() {
    return simRoot;
  }

  /**
   * deleteContentTree() deletes the simulated content.
   */
  public void deleteContentTree() {
    getContentGenerator().deleteContentTree();
  }

  public void pauseBeforeFetch() {
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
    String baseStr =  StringUtil.replaceString(url, SIMULATED_URL_ROOT,
        SimulatedContentGenerator.ROOT_NAME);
    return FileUtil.sysDepPath(baseStr);
  }

  /**
   * Map a content file location to its url.
   * @param filename the filename to map
   * @return fileName the mapping result
   */
  public String mapContentFileNameToUrl(String filename) {
    String baseStr = StringUtil.replaceString(filename, simRoot,
        SIMULATED_URL_ROOT);
    return FileUtil.sysIndepPath(baseStr);
  }

  /**
   * @param url the url to parse
   * @return the number of links between the top index page and the url.
   * This knows about the structure of the simulated content */
  public int getLinkDepth(String url) {
    String relname = StringUtil.replaceString(url, SIMULATED_URL_ROOT, "");
    String absname = StringUtil.replaceString(url, SIMULATED_URL_ROOT,
					      simRoot);
    int dirDepth = StringUtil.countOccurences(relname, "/") - 1;
    File absfile = new File(absname);
    File relfile = new File(relname);
    String name = (absfile.isDirectory()
		   ? SimulatedContentGenerator.INDEX_NAME
		   : relfile.getName());
    if (SimulatedContentGenerator.INDEX_NAME.equals(name)) {
      return dirDepth;
    } else {
      return dirDepth + 1;
    }
  }

  public List getNewContentCrawlUrls() {
    return ListUtil.list(SIMULATED_URL_START);
  }

  public Collection getUrlStems() {
    return ListUtil.list(SIMULATED_URL_STEM);
  }

  protected CrawlRule makeRules() {
    throw new UnsupportedOperationException("Not implemented");
  }

  protected void loadAuConfigDescrs(Configuration config) throws
      ConfigurationException {
    try {
      fileRoot = config.get(SimulatedPlugin.AU_PARAM_ROOT);
      if (fileRoot == null) {
        throw new
          ArchivalUnit.ConfigurationException("Missing configuration value for: "+
                                              SimulatedPlugin.AU_PARAM_ROOT);
      }
      SimulatedContentGenerator gen = new SimulatedContentGenerator(fileRoot);

      if (config.containsKey(SimulatedPlugin.AU_PARAM_DEPTH)) {
        gen.setTreeDepth(config.getInt(SimulatedPlugin.AU_PARAM_DEPTH));
      }
      if (config.containsKey(SimulatedPlugin.AU_PARAM_BRANCH)) {
        gen.setNumBranches(config.getInt(SimulatedPlugin.AU_PARAM_BRANCH));
      }
      if (config.containsKey(SimulatedPlugin.AU_PARAM_NUM_FILES)) {
        gen.setNumFilesPerBranch(config.getInt(
                   SimulatedPlugin.AU_PARAM_NUM_FILES));
      }
      if (config.containsKey(SimulatedPlugin.AU_PARAM_BIN_FILE_SIZE)) {
        gen.setBinaryFileSize(config.getInt(
                   SimulatedPlugin.AU_PARAM_BIN_FILE_SIZE));
      }
      if (config.containsKey(SimulatedPlugin.AU_PARAM_MAXFILE_NAME)) {
        gen.setMaxFilenameLength(config.getInt(
                   SimulatedPlugin.AU_PARAM_MAXFILE_NAME));
      }
      if (config.containsKey(SimulatedPlugin.AU_PARAM_FILE_TYPES)) {
        gen.setFileTypes(config.getInt(SimulatedPlugin.AU_PARAM_FILE_TYPES));
      }
      if (config.containsKey(SimulatedPlugin.AU_PARAM_ODD_BRANCH_CONTENT)) {
        gen.setOddBranchesHaveContent(config.getBoolean(
            SimulatedPlugin.AU_PARAM_ODD_BRANCH_CONTENT));
      }
      if (config.containsKey(SimulatedPlugin.AU_PARAM_BAD_FILE_LOC) &&
          config.containsKey(SimulatedPlugin.AU_PARAM_BAD_FILE_NUM)) {
        gen.setAbnormalFile(config.get(SimulatedPlugin.AU_PARAM_BAD_FILE_LOC),
                            config.getInt(SimulatedPlugin.AU_PARAM_BAD_FILE_NUM));
      }
      if (config.containsKey(SimulatedPlugin.AU_PARAM_BAD_CACHED_FILE_LOC) &&
          config.containsKey(SimulatedPlugin.AU_PARAM_BAD_CACHED_FILE_NUM)) {
        toBeDamaged.add(gen.getUrlFromLoc(config.get(
          SimulatedPlugin.AU_PARAM_BAD_CACHED_FILE_LOC),
          config.get(
          SimulatedPlugin.AU_PARAM_BAD_CACHED_FILE_NUM)));
      }
      String spec = config.get(SimulatedPlugin.AU_PARAM_HASH_FILTER_SPEC);
      boolean v = !StringUtil.isNullString(spec);
      if (v != doFilter) {
	filterMap.clear();
	doFilter = v;
	log.debug("filterMap.clear()");
      }
      // if no previous generator, any content-determining parameters have
      // changed from last time, generate new content
      log.debug("gen: " + gen);

      if (scgen == null ||
	  !(gen.getContentRoot().equals(scgen.getContentRoot()) &&
	    gen.getTreeDepth() == scgen.getTreeDepth() &&
	    gen.getNumBranches() == scgen.getNumBranches() &&
	    gen.getNumFilesPerBranch() == scgen.getNumFilesPerBranch() &&
	    gen.getBinaryFileSize() == scgen.getBinaryFileSize() &&
	    gen.getMaxFilenameLength() == scgen.getMaxFilenameLength() &&
	    gen.getFileTypes() == scgen.getFileTypes() &&
	    gen.oddBranchesHaveContent() == scgen.oddBranchesHaveContent() &&
	    gen.getAbnormalBranchString().equals(scgen.getAbnormalBranchString()) &&
	    gen.getAbnormalFileNumber() == scgen.getAbnormalFileNumber())) {
	scgen = gen;
	resetContentTree();
      }
    } catch (Configuration.InvalidParam e) {
      throw new ArchivalUnit.ConfigurationException("Bad config value", e);
    }
  }

  protected void setBaseAuParams(Configuration config)
      throws ConfigurationException {
    try {
      URL baseUrl = new URL(SIMULATED_URL_START);
      paramMap.putUrl(AU_BASE_URL, baseUrl);
    }
    catch (MalformedURLException murle) {
      throw new ConfigurationException("Bad URL for " + SIMULATED_URL_START, murle);
    }
    paramMap.putLong(AU_FETCH_DELAY, 0);
    newContentCrawlIntv = config.getTimeInterval(NEW_CONTENT_CRAWL_KEY,
                                                 defaultContentCrawlIntv);
    paramMap.putLong(AU_NEW_CRAWL_INTERVAL, newContentCrawlIntv);
    crawlSpec = new SpiderCrawlSpec(SIMULATED_URL_START, null);
    paramMap.setMapElement(AU_CRAWL_SPEC, crawlSpec);
    startUrlString = makeStartUrl();
    paramMap.putString(AU_START_URL, startUrlString);
    auName = makeName();
    paramMap.putString(AU_TITLE, auName);

    titleDbChanged();
  }

  boolean isUrlToBeDamaged(String url) {
    String file = StringUtil.replaceString(url,SIMULATED_URL_ROOT,"");
    if (toBeDamaged.contains(file)) {
      boolean x = toBeDamaged.remove(file);
      return true;
    }
    else {
      return false;
    }
  }

}
