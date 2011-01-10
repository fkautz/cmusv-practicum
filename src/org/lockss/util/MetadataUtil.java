/*
 * $Id$
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

package org.lockss.util;

import org.apache.commons.lang.StringEscapeUtils;

import java.io.*;
import java.net.URLDecoder;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import org.lockss.config.*;
import org.lockss.app.*;
import org.lockss.daemon.*;
import org.lockss.extractor.*;
import org.lockss.plugin.*;
import static org.lockss.extractor.MetadataField.*;

public class MetadataUtil {

  static Logger log = Logger.getLogger("MetadataUtil");

  public static String getTargetMimeType(MetadataTarget target,
					 ArchivalUnit au) {
    String mimeType = target != null ? target.getFormat() : null;
    if (mimeType == null) {
      mimeType = au.getPlugin().getDefaultArticleMimeType();
    }
    return mimeType;
  }

  private static Pattern ISSN_PAT =
    Pattern.compile("\\d{4}-\\d{3}[\\d{1}|x{1}|X{1}]");

  /**
   * Check that ISSN is valid. Method checks that ISSN number is correctly
   * balanced (4 digits on either side of a hyphen) and that the ckeck
   * digit (rightmost digit) is valid.
   * @param issn the issn string
   * @return true if issn is valid, false otherwise
   */
  public static boolean isISSN(String issn) {

    if (issn == null) {
      return false;
    }
    Matcher m = ISSN_PAT.matcher(issn);

    if(!m.matches()){
      log.debug("ISSN is not valid: "+issn);
      return false;
    }

    String issnArr[] = issn.split("-");
    String issnStr = issnArr[0] + "" + issnArr[1];

    char issnChars[] = issnStr.toCharArray();
    int checkSum = 0;

    // calculate what the check digit should be
    for (int i = 0; i < issnChars.length - 1; i++) {
      checkSum += Integer.parseInt(String.valueOf(issnChars[i])) * (issnChars.length - i);
    }

    char checkDigitChar = issnChars[issnChars.length - 1];
    int checkDigit;

    // a check digit of X means it's digit 10 in roman numerals
    if (String.valueOf(checkDigitChar).equalsIgnoreCase("x")) {
      checkDigit = 10;
    } else {
      checkDigit = Integer.parseInt(String.valueOf(checkDigitChar));
    }

    int remainder = checkSum % 11;
    int correctCheckDigit;

    if (checkDigit != 0 && remainder == 0) {
      log.debug("Check digit is not right. Expected: 0, Found: "+checkDigit);
      return false;
    } else if (checkDigit == 0 && remainder != 0) {
      correctCheckDigit = (11 - remainder);
      log.debug("Check digit is not right. Expected "+correctCheckDigit+", Found: "+checkDigit);
      return false;
    } else if (checkDigit != 0 && 11 - remainder != checkDigit) {

      String found;
      String shouldBe;

      correctCheckDigit = (11 - remainder);
      found = checkDigit == 10 ? "X" : checkDigit + "";
      shouldBe = correctCheckDigit == 10 ? "X" : correctCheckDigit + "";

      log.debug("Check digit is not right. Expected: "+shouldBe+" Found: "+found);
      return false;
    }


    return true;
  }

  private static Pattern DOI_PAT = Pattern.compile("10\\.\\d{4}/.*");

  /**
   * Check that DOI number is a valid DOI string. 
   * @param doi the DOI string
   * @return true if DOI is a valid string, false otherwise
   */
  public static boolean isDOI(String doi) {    

    if (doi == null) {
      return false;
    }
    Matcher m = DOI_PAT.matcher(doi);

    if(!m.matches()){
      log.debug("DOI is not valid: "+doi);
      return false;
    }

    int firstIndexOfSlash = doi.indexOf("/");
    String doiSuffix = doi.substring(firstIndexOfSlash+1);

    // there should NOT be a '/' character in the suffix
    if (doiSuffix.indexOf("/") >= 0) {
      log.debug("Suffix should not include '/' characters: "+doi);
      return false;
    }
    return true;
  }  

  /////////////////////////////////////////////////////////////////
  //
  // Temporary static methods and global data to collect and search
  // metadata, supporting metadata-based access to content.
  //
  // To be replaced by MatadataManager.
  //
  /////////////////////////////////////////////////////////////////

  public static final String PARAM_DOIMAP =
    Configuration.PREFIX + "metadata.doimap";
  public static final String DEFAULT_DOIMAP = "doi";
  public static final String PARAM_DOI_ENABLE =
    Configuration.PREFIX + "metadata.doi_enable";
  public static final Boolean DEFAULT_DOI_ENABLE = false;
  public static final String PARAM_OPENURLMAP =
    Configuration.PREFIX + "metadata.openurlmap";
  public static final String DEFAULT_OPENURLMAP = "openurl";
  public static final String PARAM_OPENURL_ENABLE =
    Configuration.PREFIX + "metadata.openurl_enable";
  public static final Boolean DEFAULT_OPENURL_ENABLE = false;

  // XXX maps should persist across daemon restart
  // XXX should lookup DOI prefix to get map in which to look up suffix
  private static CIProperties doiMap = null;
  // XXX should lookup ISSN to get map in which to look up rest of
  // XXX OpenURL metadata
  private static CIProperties openUrlMap = null;

  private static void initDoiMap() {
    Configuration config = CurrentConfig.getCurrentConfig();
    if (!config.getBoolean(PARAM_DOI_ENABLE, DEFAULT_DOI_ENABLE)) {
      return;
    }
    if (doiMap == null) {
      String doiFileName = config.get(PARAM_DOIMAP, DEFAULT_DOIMAP);
      log.debug("initDoiMap(" + doiFileName + ")");
      File doiFile = new File(doiFileName);
      if (doiFile.exists()) {
	FileInputStream fis = null;
	try {
	  fis = new FileInputStream(doiFile);
	  if (fis != null) {
	    doiMap = new CIProperties();
	    doiMap.load(fis);
	  }
	} catch (IOException ex) {
	  log.error(doiFile + " threw " + ex);
	}
      } else {
	// There isn't a cached DOI map - create one
	// XXX this isn't feasible in production because it
	// XXX would take too long and the map would be way
	// XXX too big, but it is OK for a demo.
	doiMap = createDoiMap();
	if (doiMap != null) {
	  FileOutputStream fos = null;
	  try {
	    fos = new FileOutputStream(new File(doiFileName));
	    if (fos != null) {
	      doiMap.store(fos, "Doi Map");
	    }
	  } catch (IOException ex) {
	    log.error(doiFileName + " threw " + ex);
	  } finally {
	    IOUtil.safeClose(fos);
	  }
	}
      }
    }
  }


  protected static CIProperties createDoiMap() {
    PluginManager pluginMgr = LockssDaemon.getLockssDaemon().getPluginManager();

    final CIProperties ret = new CIProperties();
    ArticleMetadataExtractor.Emitter emitter =
      new ArticleMetadataExtractor.Emitter() {
	public void emitMetadata(ArticleFiles af,
				 ArticleMetadata md) {
	  if (md != null) {
	    CachedUrl cu = af.getFullTextCu();
	    String doi = md.get(MetadataField.FIELD_DOI);
	    if (doi != null) {
	      ret.put(doi, cu.getUrl());
	    } else {
	      log.warning(cu.getUrl() + " has no DOI ");
	    }
	  }      
	}
      };

    for (ArchivalUnit au : pluginMgr.getAllAus()) {
      if (pluginMgr.isInternalAu(au)) {
	continue;
      }
      ArticleMetadataExtractor mdExtractor =
	au.getPlugin().getArticleMetadataExtractor(MetadataTarget.DOI, au);
      for (Iterator<ArticleFiles> iter = au.getArticleIterator();
	   iter.hasNext(); ) {
	ArticleFiles af = iter.next();
	CachedUrl cu = af.getFullTextCu();
	try {
	  if (cu.hasContent()) {
	    mdExtractor.extract(af, emitter);
	  }
	} catch (IOException e) {
	  log.warning("createDoiMap() threw " + e);
	} catch (PluginException e) {
	  log.warning("createDoiMap() threw " + e);
	} finally {
	  AuUtil.safeRelease(cu);
	}
      }
    }
    return ret;
  }

  private static void initOpenUrlMap() {
    Configuration config = CurrentConfig.getCurrentConfig();
    if (!config.getBoolean(PARAM_OPENURL_ENABLE, DEFAULT_OPENURL_ENABLE)) {
      return;
    }
    if (openUrlMap == null) {
      String openUrlFileName = config.get(PARAM_OPENURLMAP, DEFAULT_OPENURLMAP);
      log.debug("initOpenUrlMap(" + openUrlFileName + ")");
      File openUrlFile = new File(openUrlFileName);
      if (openUrlFile.exists()) {
	FileInputStream fis = null;
	try {
	  fis = new FileInputStream(openUrlFile);
	  if (fis != null) {
	    // There is a cached OpenURL map
	    openUrlMap = new CIProperties();
	    openUrlMap.load(fis);
	  }
	} catch (IOException ex) {
	  log.error(openUrlFileName + " threw " + ex);
	} finally {
	  IOUtil.safeClose(fis);
	}
      } else {
	// There isn't a cached OpenURL map - create one
	// XXX this isn't feasible in production because it
	// XXX would take too long and the map would be way
	// XXX too big, but it is OK for a demo.
	openUrlMap = createOpenUrlMap();
	if (openUrlMap != null) {
	  FileOutputStream fos = null;
	  try {
	    fos = new FileOutputStream(new File(openUrlFileName));
	    if (fos != null) {
	      openUrlMap.store(fos, "OpenURL Map");
	    }
	  } catch (IOException ex) {
	    log.error(openUrlFileName + " threw " + ex);
	  } finally {
	    IOUtil.safeClose(fos);
	  }
	}
      }
    }
  }

  protected static CIProperties createOpenUrlMap() {
    PluginManager pluginMgr = LockssDaemon.getLockssDaemon().getPluginManager();

    final CIProperties ret = new CIProperties();
    ArticleMetadataExtractor.Emitter emitter =
      new ArticleMetadataExtractor.Emitter() {
	public void emitMetadata(ArticleFiles af,
				 ArticleMetadata md) {
	  if (md != null) {
	    CachedUrl cu = af.getFullTextCu();
	    // Key for OpenURL map is
	    // issn + "/" + volume + "/" + issue + "/" + spage
	    String issn = md.get(MetadataField.FIELD_ISSN);
	    String volume = md.get(FIELD_VOLUME);
	    String issue = md.get(FIELD_ISSUE);
	    String spage = md.get(MetadataField.FIELD_START_PAGE);
	    if (issn != null && volume != null &&
		issue != null && spage != null) {
	      String key = issn + "/" + volume + "/" + issue + "/" + spage;
	      ret.put(key, cu.getUrl());
	    } else {
	      log.warning(cu.getUrl() + " has content but bad metadata " +
			  (issn == null ? "null" : issn) + "/" +
			  (volume == null ? "null" : volume) + "/" +
			  (issue == null ? "null" : issue) + "/" +
			  (spage == null ? "null" : spage));
	    }
	  }      
	}
      };
    for (ArchivalUnit au : pluginMgr.getAllAus()) {
      if (pluginMgr.isInternalAu(au)) {
	continue;
      }
      ArticleMetadataExtractor mdExtractor =
	au.getPlugin().getArticleMetadataExtractor(MetadataTarget.OpenURL, au);
      for (Iterator<ArticleFiles> iter = au.getArticleIterator();
	   iter.hasNext(); ) {
	ArticleFiles af = iter.next();
	CachedUrl cu = af.getFullTextCu();
	try {
	  if (cu.hasContent()) {
	    mdExtractor.extract(af, emitter);
	  }
	} catch (IOException e) {
	  log.warning("createOpenUrlMap() threw " + e);
	} catch (PluginException e) {
	  log.warning("createOpenUrlMap() threw " + e);
	} finally {
	  AuUtil.safeRelease(cu);
	}
      }
    }
    return ret;
  }

  public static String doiToUrl(String doi) {
    String ret = null;
    if (doiMap == null) {
      initDoiMap();
    }
    if (doiMap != null) {
      ret = doiMap.getProperty(doi);
    }
    log.debug2("doiToUrl(" + doi + ") = " + (ret == null ? "null" : ret));
    return ret;
  }

  public static String openUrlToUrl(String openUrl) {
    String ret = null;
    if (openUrlMap == null) {
      initOpenUrlMap();
    }
    if (openUrlMap != null) {
      ret = openUrlMap.getProperty(openUrl);
    }
    return ret;
  }

  protected static void doiForUrl(String doi, String url) {
    if (doiMap == null) {
      initDoiMap();
    }
    if (doiMap != null) {
      doiMap.setProperty(doi, url);
    }
  }

  protected static void openUrlForUrl(String openUrl, String url) {
    if (openUrlMap == null) {
      initOpenUrlMap();
    }
    if (openUrlMap != null) {
      openUrlMap.setProperty(openUrl, url);
    }
  }

  private static String[] doiResolvers = {
    "http://dx.doi.org/",
  };
  private static String[] openUrlResolvers = {
    "http://www.crossref.org/openurl?",
  };
  // If the URL specifies a publisher's DOI or OpenURL resolver,
  // strip the stuff before the ?, reformat the rest and hand it
  // to the Metadata resolver to get the URL for the content in
  // the cache.
  public static String proxyResolver(String url) {
    String ret = null;
    if (StringUtil.isNullString(url)) {
      return ret;
    }
    log.debug2("proxyResolver(" + url + ")");
    boolean found = false;
    // Is it a DOI resolver URL?
    // XXX should use host part to find plugin, then ask plugin if
    // XXX URL specifies resolver, and if so get it to reformat
    // XXX resolver query and feed to Metadata.
    for (int i = 0; i < doiResolvers.length; i++) {
      if (url.startsWith(doiResolvers[i])) {
	String param = url.substring(doiResolvers[i].length());
	log.debug3("doiResolver: " + url + " doi " + param);
	String newUrl =
	  MetadataUtil.doiToUrl(param);
	if (newUrl != null) {
	  ret = newUrl;
	  found = true;
	}
      }
    }
    if (!found) {
      for (int i = 0; i < openUrlResolvers.length; i++) {
	if (url.startsWith(openUrlResolvers[i])) {
	  // issn/volume/issue/spage
	  String query = url.substring(openUrlResolvers[i].length());
	  log.debug3("openUrlResolver: " + url + " openUrl " + query);
	  if (!StringUtil.isNullString(query)) {
	    String[] params = query.split("&");
	    String issn = null;
	    String volume = null;
	    String issue = null;
	    String spage = null;
	    for (int j = 0; j < params.length; j++) {
	      if (params[j].startsWith("issn=")) {
		issn = params[j].substring(5);
	      }
	      if (params[j].startsWith("volume=")) {
		volume = params[j].substring(7);
	      }
	      if (params[j].startsWith("issue=")) {
		issue = params[j].substring(6);
	      }
	      if (params[j].startsWith("spage=")) {
		spage = params[j].substring(6);
	      }
	    }
	    if (issn != null &&
		volume != null &&
		issue != null &&
		spage != null) {
	      String openUrl = issn + "/" + volume + "/" +
		issue + "/" + spage;
	      log.debug3("openUrl: " + openUrl);
	      String newUrl =
		MetadataUtil.openUrlToUrl(openUrl);
	      if (newUrl != null) {
		ret = newUrl;
		found = true;
	      }
	    }
	  }
	}
      }
    }
    log.debug2("proxyResolver returns " + ret);
    return ret;
  }



}
