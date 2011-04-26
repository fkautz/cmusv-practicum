/*
 * $Id: ListObjects.java,v 1.18 2011/04/26 23:55:06 tlipkis Exp $
 */

/*

Copyright (c) 2000-2011 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.servlet;

import javax.servlet.*;
import java.io.*;
import java.util.*;

import org.apache.commons.lang.mutable.*;
import org.mortbay.html.Page;
import org.mortbay.html.Composite;

import org.lockss.util.*;
import org.lockss.daemon.*;
import org.lockss.plugin.*;
import org.lockss.extractor.*;

/** Plain output of various lists - AUs, URLs, metadata, etc.  Mostly for
 * debugging/testing; also able to handle unlimited length lists. */
public class ListObjects extends LockssServlet {
  static final Logger log = Logger.getLogger("ListObjects");

  private String auid;
  private ArchivalUnit au;

  private PluginManager pluginMgr;

  // don't hold onto objects after request finished
  protected void resetLocals() {
    au = null;
    auid = null;
    super.resetLocals();
  }

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
    pluginMgr = getLockssDaemon().getPluginManager();
  }

  /**
   * Handle a request
   * @throws IOException
   */
  public void lockssHandleRequest() throws IOException {
    if (!pluginMgr.areAusStarted()) {
      displayNotStarted();
      return;
    }
    String type = getParameter("type");
    if (StringUtil.isNullString(type)) {
      displayError("\"type\" arg must be specified");
      return;
    }
    if (type.equalsIgnoreCase("aus")) {
      new AuNameList().execute();
    } else if (type.equalsIgnoreCase("auids")) {
      new AuidList().execute();
    } else {
      // all others need au
      auid = getParameter("auid");
      au = pluginMgr.getAuFromId(auid);
      if (au == null) {
	displayError("No such AU: " + auid);
	return;
      }
      if (type.equalsIgnoreCase("urls")) {
	new UrlList(au).execute();
      } else if (type.equalsIgnoreCase("files")) {
	new FileList(au).execute();
      } else if (type.equalsIgnoreCase("articles")) {
	boolean isDoi = !StringUtil.isNullString(getParameter("doi"));
	if (isDoi) {
	  // XXX Backwards compatible - still needed?
	  new DoiList(au, MetadataTarget.DOI).setIncludeUrl(true).execute();
	} else {
	  new ArticleUrlList(au).execute();
	}
      } else if (type.equalsIgnoreCase("dois")) {
	new DoiList(au, MetadataTarget.DOI).execute();
      } else if (type.equalsIgnoreCase("metadata")) {
	new MetadataList(au, MetadataTarget.Any).execute();
      } else {
	displayError("Unknown list type: " + type);
	return;
      }
    }
  }

  // Used by status table(s) to determine whether to display link to DOIs
  public static boolean hasDoiList(ArchivalUnit au) {
    return null !=
      au.getPlugin().getArticleMetadataExtractor(MetadataTarget.Article, au);

      // Shouldn't invoke factory, but this method isn't in interface
//     return null !=
//       au.getPlugin().getArticleMetadataExtractorFactory(MetadataTarget.Article);
  }

  // Used by status table(s) to determine whether to display link to articles
  public static boolean hasArticleList(ArchivalUnit au) {
    return hasDoiList(au);
  }

  void displayError(String error) throws IOException {
    Page page = newPage();
    Composite comp = new Composite();
    comp.add("<center><font color=red size=+1>");
    comp.add(error);
    comp.add("</font></center><br>");
    page.add(comp);
    layoutFooter(page);
    ServletUtil.writePage(resp, page);
  }

  /** Base for classes that print lists of objexts */
  abstract class BaseList {
    PrintWriter wrtr;
    boolean isError = false;

    /** Subs must print a header */
    abstract void printHeader();

    /** Subs must execute a body between begin() and end() */
    abstract void doBody() throws IOException;

    void begin() throws IOException {
      wrtr = resp.getWriter();
      resp.setContentType("text/plain");
      printHeader();
      wrtr.println();
    }

    void finish() {;
      wrtr.println(isError ? "# end (errors)" : "# end");
    }

    final void execute() throws IOException {
      begin();
      doBody();
      finish();
    }
  }

  /** Lists of objects (URLs, files, etc.) which are based on AU's
   * repository nodes */
  abstract class BaseNodeList extends BaseList {
    ArchivalUnit au;

    BaseNodeList(ArchivalUnit au) {
      super();
      this.au = au;
    }

    /** Subs must process content CUs */
    abstract void processContentCu(CachedUrl cu);

    void doBody() {
      for (Iterator iter = au.getAuCachedUrlSet().contentHashIterator();
	   iter.hasNext(); ) {
	CachedUrlSetNode cusn = (CachedUrlSetNode)iter.next();
	CachedUrl cu = AuUtil.getCu(cusn);
	try {
	  if (cu != null) {
	    processCu(cu);
	  }	  
	} finally {
	  AuUtil.safeRelease(cu);
	}
      }
    }

    void processCu(CachedUrl cu) {
      if (cu.hasContent()) {
	processContentCu(cu);
      }
    }
  }

  /** List URLs in AU */
  class UrlList extends BaseNodeList {
    UrlList(ArchivalUnit au) {
      super(au);
    }

    void printHeader() {
      wrtr.println("# URLs in " + au.getName());
      wrtr.println();
    }

    void processContentCu(CachedUrl cu) {
      wrtr.println(cu.getUrl());
    }
  }

  /** List URLs, content type and length. */
  // MetaArchive experiment 2010ish.  Still in use?
  class FileList extends BaseNodeList {
    FileList(ArchivalUnit au) {
      super(au);
    }

    void printHeader() {
      wrtr.println("# Files in " + au.getName());
      wrtr.println("# URL\tContentType\tsize");
      wrtr.println();
    }

    void processContentCu(CachedUrl cu) {
      String url = cu.getUrl();
      String contentType = cu.getContentType();
      long bytes = cu.getContentSize();
      if (contentType == null) {
	contentType = "unknown";
      }
      wrtr.println(url + "\t" + contentType + "\t" + bytes);
    }
  }

  /** Base for lists based on ArticleIterator */
  abstract class BaseArticleList extends BaseList {
    ArchivalUnit au;
    int errCnt = 0;
    int maxErrs = 3;

    BaseArticleList(ArchivalUnit au) {
      super();
      this.au = au;
    }

    /** Subs must process each article */
    abstract void processArticle(ArticleFiles af)
	throws IOException, PluginException;

    boolean isLogError() {
      isError = true;
      return errCnt++ <= maxErrs;
    }

    void doBody() throws IOException {
      for (Iterator<ArticleFiles> iter = au.getArticleIterator();
	   iter.hasNext(); ) {
	ArticleFiles af = iter.next();
	if (af.isEmpty()) {
	  // Probable plugin error.  Shouldn't happen, but if it does it
	  // likely will many times.
	  if (isLogError()) {
	    log.error("ArticleIterator generated empty ArticleFiles");
	  }
	  continue;
	}
	try {
	  processArticle(af);
	} catch (Exception e) {
	  if (isLogError()) {
	    log.warning("listDOIs() threw", e);
	  }
	}
      }
    }
  }

  /** Base for lists requiring metadata extraction */
  abstract class BaseMetadataList extends BaseArticleList {
    MetadataTarget target;
    ArticleMetadataExtractor mdExtractor;
    ArticleMetadataExtractor.Emitter emitter;

    BaseMetadataList(ArchivalUnit au, MetadataTarget target) {
      super(au);
      this.target = target;
    }

    void begin() throws IOException {
      super.begin();
      mdExtractor = au.getPlugin().getArticleMetadataExtractor(target, au);
    }

    /** Subs must process each article and list of metadata */
    abstract void processArticle(ArticleFiles af, List<ArticleMetadata> amlst);

    void doBody() throws IOException {
      if (mdExtractor == null) {
	// XXX error format
	wrtr.println("# Plugin " + au.getPlugin().getPluginName() +
		     " does not supply a metadata extractor.");
	isError = true;
	return;
      }
      super.doBody();
    }

    void processArticle(ArticleFiles af) throws IOException, PluginException {
      // Create a ListEmitter per article
      ListEmitter emitter = new ListEmitter();
      mdExtractor.extract(target, af, emitter);
      processArticle(af, emitter.getAmList());
      // If we finish one normally, start logging errors again.
      errCnt = 0;
    }
  }

  /** Metadata emitter that collects ArticleMetadata into a list. */
  static class ListEmitter implements ArticleMetadataExtractor.Emitter {
    List<ArticleMetadata> amlst = new ArrayList<ArticleMetadata>();

    public void emitMetadata(ArticleFiles af, ArticleMetadata md) {
      if (log.isDebug3()) log.debug3("emit("+af+", "+md+")");
      if (md != null) {
	amlst.add(md);
      };
    }

    public List<ArticleMetadata> getAmList() {
      return amlst;
    }
  }

  /** List DOIs of articles in AU */
  class DoiList extends BaseMetadataList {
    protected boolean isIncludeUrl = false;
    int logMissing = 3;

    DoiList(ArchivalUnit au, MetadataTarget target) {
      super(au, target);
    }

    void printHeader() {
      wrtr.println("# DOIs in " + au.getName());
    }

    void processArticle(ArticleFiles af, List<ArticleMetadata> amlst) {
      if (amlst == null || amlst.isEmpty()) {
	if (isIncludeUrl) {
	  CachedUrl cu = af.getFullTextCu();
	  if (cu != null) {
	    wrtr.println(cu.getUrl());
	  } else {
	    // shouldn't happen, but if it does it likely will many times.
	    if (logMissing-- > 0) {
	      log.error("ArticleIterator generated ArticleFiles with no full text CU: " + af);
	    }
	  }
	}
      } else {
	for (ArticleMetadata md : amlst) {
	  if (md != null) {
	    String doi = md.get(MetadataField.FIELD_DOI);
	    if (doi != null) {
	      if (isIncludeUrl) {
		String url = md.get(MetadataField.FIELD_ACCESS_URL);
		wrtr.println(url + "\t" + doi);
	      } else {
		wrtr.println(doi);
	      }
	    }
	  }
	}
      }
    }

    DoiList setIncludeUrl(boolean val) {
      isIncludeUrl = val;
      return this;
    }
  }

  /** List URL of articles in AU */
  class ArticleUrlList extends BaseArticleList {
    int logMissing = 3;

    ArticleUrlList(ArchivalUnit au) {
      super(au);
    }

    void printHeader() {
      wrtr.println("# Articles in " + au.getName());
    }

    void processArticle(ArticleFiles af) {
      CachedUrl cu = af.getFullTextCu();
      if (cu != null) {
	wrtr.println(cu.getUrl());
      } else {
	// shouldn't happen, but if it does it likely will many times.
	if (logMissing-- > 0) {
	  log.error("ArticleIterator generated ArticleFiles with no full text CU: " + af);
	}
      }
    }
  }

  /** Dump all ArticleFiles and metadata of articles in AU */
  class MetadataList extends BaseMetadataList {
    MetadataList(ArchivalUnit au, MetadataTarget target) {
      super(au, target);
    }
    void printHeader() {
      wrtr.println("# All metadata in " + au.getName());
    }

    void processArticle(ArticleFiles af, List<ArticleMetadata> amlst) {
// 	  wrtr.println(af);
      wrtr.println("ArticleFiles");
      wrtr.print(af.ppString(2));
      
      for (ArticleMetadata md : amlst) {
	if (md != null) {
	  wrtr.println("Metadata");
	  wrtr.print(md.ppString(2));
// 	  wrtr.println(md);
	}
      }      
      wrtr.println();
    }
  }

  /** Base for lists of AUs */
  abstract class AuList extends BaseList {

    void doBody() throws IOException {
      boolean includeInternalAus = isDebugUser();
      for (ArchivalUnit au : pluginMgr.getAllAus()) {
	if (includeInternalAus || !pluginMgr.isInternalAu(au)) {
	  processAu(au);
	}
      }
    }

    /** Subs must process each AU */
    abstract void processAu(ArchivalUnit au);
  }

  /** List AU names */
  class AuNameList extends AuList {
    void printHeader() {
      wrtr.println("# AUs on " + getMachineName());
    }

    void processAu(ArchivalUnit au) {
      wrtr.println(au.getName());
    }
  }

  /** List AUIDs */
  class AuidList extends AuList {
    void printHeader() {
      wrtr.println("# AUIDs on " + getMachineName());
    }

    void processAu(ArchivalUnit au) {
      wrtr.println(au.getAuId());
    }
  }
}
