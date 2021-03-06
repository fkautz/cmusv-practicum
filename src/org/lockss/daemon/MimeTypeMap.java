/*
 * $Id: MimeTypeMap.java,v 1.11 2011/02/14 00:07:54 tlipkis Exp $
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

import java.util.*;
import org.lockss.util.*;
import org.lockss.config.*;
import org.lockss.daemon.*;
import org.lockss.extractor.*;
import org.lockss.plugin.*;
import org.lockss.rewriter.*;

/** Record of MIME type-specific factories (<i>eg</i>, FilterFactory,
 * LinkExtractorFactory), and static global defaults
 */
public class MimeTypeMap {
  static Logger log = Logger.getLogger("MimeTypeMap");

  public static MimeTypeMap DEFAULT = new MimeTypeMap();

  static final String PREFIX = Configuration.PREFIX + "mimeInfo.";

  public static final String PARAM_DEFAULT_CSS_EXTRACTOR_FACTORY =
    PREFIX + "defaultCssExtractorFactory";
  public static final String DEFAULT_DEFAULT_CSS_EXTRACTOR_FACTORY =
    "org.lockss.extractor.RegexpCssLinkExtractor$Factory";

  public static final String PARAM_DEFAULT_CSS_REWRITER_FACTORY =
    PREFIX + "defaultCssRewriterFactory";
  public static final String DEFAULT_DEFAULT_CSS_REWRITER_FACTORY =
    "org.lockss.rewriter.RegexpCssLinkRewriterFactory";

  private static MimeTypeInfo.Mutable HTML = new MimeTypeInfo.Impl();
  private static MimeTypeInfo.Mutable CSS = new MimeTypeInfo.Impl();

  static {
    HTML.setLinkExtractorFactory(new GoslingHtmlLinkExtractor.Factory());
    // XXX
    // HTML.setLinkRewriterFactory(new JavascriptHtmlLinkRewriterFactory());
    HTML.setLinkRewriterFactory(new NodeFilterHtmlLinkRewriterFactory());
    DEFAULT.putMimeTypeInfo("text/html", HTML);
    DEFAULT.putMimeTypeInfo("application/xhtml+xml", HTML);
    setLinkExtractorFactory(CSS,
			    DEFAULT_DEFAULT_CSS_EXTRACTOR_FACTORY);
    setLinkRewriterFactory(CSS,
			    DEFAULT_DEFAULT_CSS_REWRITER_FACTORY);
   DEFAULT.putMimeTypeInfo("text/css", CSS);
  }

  /** Called by org.lockss.config.MiscConfig
   */
  public static void setConfig(Configuration config,
			       Configuration oldConfig,
			       Configuration.Differences diffs) {
    if (diffs.contains(PARAM_DEFAULT_CSS_EXTRACTOR_FACTORY)) {
      setLinkExtractorFactory(CSS,
			      config.get(PARAM_DEFAULT_CSS_EXTRACTOR_FACTORY,
					 DEFAULT_DEFAULT_CSS_EXTRACTOR_FACTORY));
    }
    if (diffs.contains(PARAM_DEFAULT_CSS_REWRITER_FACTORY)) {
      setLinkRewriterFactory(CSS,
			      config.get(PARAM_DEFAULT_CSS_REWRITER_FACTORY,
					 DEFAULT_DEFAULT_CSS_REWRITER_FACTORY));
    }
  }
					  
  private static void setLinkExtractorFactory(MimeTypeInfo.Mutable mti,
					      String className) {
    LinkExtractorFactory fact =
      (LinkExtractorFactory)newFact(className,
				    LinkExtractorFactory.class,
				    new RegexpCssLinkExtractor.Factory());
    mti.setLinkExtractorFactory(fact);
  }

  private static void setLinkRewriterFactory(MimeTypeInfo.Mutable mti,
					      String className) {
    LinkRewriterFactory fact =
      (LinkRewriterFactory)newFact(className,
				    LinkRewriterFactory.class,
				    new RegexpCssLinkRewriterFactory());
    mti.setLinkRewriterFactory(fact);
  }

  private Map map = new HashMap();
  private MimeTypeMap parent;

  public MimeTypeMap() {
  }

  public MimeTypeMap(MimeTypeMap parent) {
    this.parent = parent;
  }

  public MimeTypeMap getParent() {
    return parent;
  }      

  public static Object newFact(String factClassName, Class expectedType,
			       Object dfault) {
    Object obj = null;
    try {
      obj = Class.forName(factClassName).newInstance();
      if (!expectedType.isInstance(obj)) {
	throw new IllegalArgumentException(factClassName + " is not a "
					   + expectedType.getName());
      }
      return obj;
  } catch (Exception e) {
      log.error(e.toString());
      return dfault;
    }
  }

  void putMimeTypeInfo(String contentType, MimeTypeInfo mti) {
    String mime = HeaderUtil.getMimeTypeFromContentType(contentType);
    map.put(mime, mti);
  }

  /** Return immutable view of MimeTypeInfo for the specified contentType,
   * from this map or its nearest parent.  Use {@link
   * #modifyMimeTypeInfo(String)} to get a mutable view.
   * @param contentType MIME type or value of Content-Type: header
   * @return MimeTypeInfo if exists, else null. */
  public MimeTypeInfo getMimeTypeInfo(String contentType) {
    String mime = HeaderUtil.getMimeTypeFromContentType(contentType);
    MimeTypeInfo res = (MimeTypeInfo)map.get(mime);
    if (res == null && parent != null) {
      return parent.getMimeTypeInfo(mime);
    }
    return res;
  }

  /** Return a modifiable (<i>Ie</i>, owned by this map) MimeTypeInfo for
   * the given MIME type, creating one if necessary.
   * @param contentType MIME type or value of Content-Type: header
   * @return a MimeTypeInfo local to this MimeTypeMap. */
  public MimeTypeInfo.Mutable modifyMimeTypeInfo(String contentType) {
    String mime = HeaderUtil.getMimeTypeFromContentType(contentType);
    MimeTypeInfo.Mutable res = (MimeTypeInfo.Mutable)map.get(mime);
    if (res == null) {
      if (parent != null) {
	res = new MimeTypeInfo.Impl(parent.getMimeTypeInfo(mime));
      } else {
	res = new MimeTypeInfo.Impl();
      }
      map.put(mime, res);
    }
    return res;
  }
}
