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

package org.lockss.plugin;

import java.util.*;
import java.util.regex.*;

import org.lockss.util.*;
import org.lockss.daemon.*;
import org.lockss.plugin.base.*;
import org.lockss.extractor.*;


/**
 * Article iterator that finds articles by iterating through all the
 * CachedUrls of the AU, or through specific subtrees, visiting those that
 * match a MIME type and/or regular expression, or a subclass-specified
 * condition.  For each node visited, an ArticleFiles may (or, by a
 * subclass, may not) be generated.
 */
public class SubTreeArticleIterator implements Iterator<ArticleFiles> {
  
  static Logger log = Logger.getLogger("SubTreeArticleIterator");
  
  /** Specification of the CachedUrls the iterator should return.  Setters
   * are chained. */
  public static class Spec {
    private MetadataTarget target;
    private String mimeType;
    private List<String> roots;
    private List<String> rootTemplates;
    private Pattern pat;
    private String patTempl;
    private int patFlags = 0;		// Pattern compilation flags


    /** Set the MIME type of the desired files.  If null or not set, MIME
     * type does not enter into  determination of which files to visit.
     * @param mimeType
     * @return this
     */
    public Spec setMimeType(String mimeType) {
      this.mimeType = mimeType;
      return this;
    }

    /** Return the desired MIME type */
    public String getMimeType() {
      return mimeType;
    }

    /** The MetadataTarget determines the type of articles desired.
     * Currently the format can be used to specify the MIME type.
     * @param target
     * @return this
     */
    public Spec setTarget(MetadataTarget target) {
      this.target = target;
      return this;
    }

    /** Return the target */
    public MetadataTarget getTarget() {
      return target;
    }

    /** Set the URL of the root of the subtree below which to iterate.
     * @param root subtree root
     * @return this
     */
    public Spec setRoot(String root) {
      return setRoots(ListUtil.list(root));
    }

    /** Set the URL(s) of the root(s) of the subtree(s) below which to
     * iterate to the result of expanding the printf template.
     * @param rootTemplate template (printf string and args) for subtree root
     * @return this
     */
    public Spec setRootTemplate(String rootTemplate) {
      return setRootTemplates(ListUtil.list(rootTemplate));
    }

    /** Set the URLs of the roots of the subtrees below which to iterate.
     * @param rootTemplates templates (printf string and args) for subtree roots
     * @return this
     */
    public Spec setRoots(List<String> roots) {
      if (rootTemplates != null) {
	throw new
	  IllegalArgumentException("Can't set both roots and rootTemplates");
      }
      this.roots = roots;
      return this;
    }

    /** Set the URL(s) of the root(s) of the subtree(s) below which to
     * iterate to the result of expanding the printf templates.
     * @param rootTemplates template (printf string and args) for subtree roots
     * @return this
     */
    public Spec setRootTemplates(List<String> rootTemplates) {
      if (roots != null) {
	throw new
	  IllegalArgumentException("Can't set both roots and rootTemplates");
      }
      this.rootTemplates = rootTemplates;
      return this;
    }

    /** Return the roots */
    public List<String> getRoots() {
      return roots;
    }

    /** Return the root templates */
    public List<String> getRootTemplates() {
      return rootTemplates;
    }

    /** Set the regular expression the article URLs must match
     * @param regex compiled regular expression
     * @return this
     */
    public Spec setPattern(Pattern regex) {
      pat = regex;
      return this;
    }

    /** Set the regular expression the article URLs must match
     * @param regex regular expression
     * @return this
     */
    public Spec setPattern(String regex) {
      return setPattern(regex, 0);
    }

    /** Set the regular expression the article URLs must match
     * @param regex regular expression
     * @param flags compilation flags for regex
     * @return this
     */
    public Spec setPattern(String regex, int flags) {
      if (patTempl != null) {
	throw new IllegalArgumentException("Can't set both pattern and patternTemplate");
      }
      patFlags = flags;
      pat = Pattern.compile(regex, flags);
      return this;
    }

    /** Set the regular expression the article URLs must match to the
     * expanstion of the template
     * @param patternTemplate printf string and args
     * @return this
     */
    public Spec setPatternTemplate(String patternTemplate) {
      return setPatternTemplate(patternTemplate, 0);
    }

    /** Set the regular expression the article URLs must match to the
     * expanstion of the template
     * @param patternTemplate printf string and args
     * @param flags compilation flags for regex
     * @return this
     */
    public Spec setPatternTemplate(String patternTemplate, int flags) {
      if (pat != null) {
	throw new IllegalArgumentException("Can't set both pattern and patternTemplate");
      }
      this.patTempl = patternTemplate;
      this.patFlags = flags;
      return this;
    }

    /** Return the pattern */
    public Pattern getPattern() {
      return pat;
    }

    /** Return the pattern template */
    public String getPatternTemplate() {
      return patTempl;
    }

    /** Return the pattern compilation flags */
    public int getPatternFlags() {
      return patFlags;
    }
  }


  /** The spec that URLs must match */
  protected Spec spec;
  /** The mimeType that files must match */
  protected String mimeType;
  /** The AU being iterated over */
  protected ArchivalUnit au;
  /** Pattern that URLs must match */
  protected Pattern pat = null;
  /** Underlying CachedUrlSet iterator */
  protected Iterator cusIter = null;
  /** Iterator over subtree roots */
  protected Iterator<CachedUrlSet> rootIter = null;

  // if null, we have to look for nextElement
  private ArticleFiles nextElement = null;

  // if any call to visitArticleCu() emits more than one ArticleFiles they
  // are accumulated in this list and moved into nextElement one at a time.
  // This simplifies the hasNext() and next() logic and avoids the overhead
  // of the intermediate list in the common case of zero or one
  // ArticleFiles per CU
  private LinkedList<ArticleFiles> nextElements;

  public SubTreeArticleIterator(ArchivalUnit au, Spec spec) {
    this.au = au;
    this.spec = spec;
    mimeType = getMimeType();
    Collection<CachedUrlSet> roots = makeRoots();
    this.pat = makePattern();
    rootIter = roots.iterator();
    log.debug2("Create: AU: " + au.getName() + ", Mime: " + this.mimeType
	       + ", roots: " + roots + ", pat: " + pat);
  }

  // XXX fix when work out how target is used
  protected String getMimeType() {
    String tmpMime =
      spec.getTarget() != null ? spec.getTarget().getFormat() : null;
    if (tmpMime == null) {
      tmpMime = spec.getMimeType();
    }
    if (tmpMime == null) {
      tmpMime = au.getPlugin().getDefaultArticleMimeType();
    }
    return tmpMime;
  }

  protected Pattern makePattern() {
    if (spec.getPattern() != null) {
      return spec.getPattern();
    }
    if (spec.getPatternTemplate() != null) {
      String re =
	convertVariableRegexpString(spec.getPatternTemplate()).getRegexp();
      return Pattern.compile(re, spec.getPatternFlags());
    }
    return null;
  }

  protected Collection<CachedUrlSet> makeRoots() {
    Collection<String> roots = makeRootUrls();
    log.debug2("rootUrls: " + roots);
    if (roots == null || roots.isEmpty()) {
      return ListUtil.list(au.getAuCachedUrlSet());
    }
    Collection<CachedUrlSet> res = new ArrayList<CachedUrlSet>();
    for (String root : roots) {
      res.add(au.makeCachedUrlSet(new RangeCachedUrlSetSpec(root)));
    }
    return res;
  }

  protected Collection<String> makeRootUrls() {
    if (spec.getRoots() != null) {
      return spec.getRoots();
    }
    if (spec.getRootTemplates() == null) {
      return null;
    }
    Collection<String> res = new ArrayList<String>();
    for (String template : spec.getRootTemplates()) {
      List<String> lst = convertUrlList(template);
      if (lst == null) {
	log.warning("Null converted string from " + template);
	continue;
      }
      res.addAll(lst);
    }
    return res;
  }

  protected List<String> convertUrlList(String printfString) {
    return new PrintfConverter.UrlListConverter(au).getUrlList(printfString);
  }

  protected PrintfConverter.MatchPattern
    convertVariableRegexpString(String printfString) {
    return new PrintfConverter.RegexpConverter(au).getMatchPattern(printfString);
  }

  private ArticleFiles findNextElement() {
    if (nextElement != null) {
      return nextElement;
    }
    while (true) {
      if (nextElements != null && !nextElements.isEmpty()) {
	nextElement = nextElements.remove();
	return nextElement;
      } else {
	CachedUrl cu = null;
	try {
	  if (cusIter == null || !cusIter.hasNext()) {
	    if (!rootIter.hasNext()) {
	      return null;
	    } else {
	      CachedUrlSet root = rootIter.next();
	      cusIter = root.contentHashIterator();
	      continue;
	    }
	  } else {
	    CachedUrlSetNode node = (CachedUrlSetNode)cusIter.next();
	    cu = AuUtil.getCu(node);
	    if (cu != null && cu.hasContent()) {
	      if (isArticleCu(cu)) {
		visitArticleCu(cu);
		if (nextElement == null) {
		  continue;
		}
		return nextElement;
	      }
	    }
	  }
	} catch (Exception ex) {
	  // No action intended - iterator should ignore this cu.
	  if (cu == null) {
	    log.error("Error", ex);
	  } else {
	    log.error("Error processing " + cu.getUrl(), ex);
	  }
	} finally {
	  AuUtil.safeRelease(cu);
	}
      }
    }
  }

  /** Emit an ArticleFiles from the iterator.  Should be called by
   * visitArticleCu() once for each ArticleFiles it wants to generate from
   * the CU */
  protected final void emitArticleFiles(ArticleFiles af) {
    if (log.isDebug3()) log.debug3("Emit: " + af);
    if (nextElement == null) {
      nextElement = af;
    } else {
      if (nextElements == null) {
	nextElements = new LinkedList();
      }
      nextElements.add(af);
    }
  }

  /** Invoked on each CachedUrl for which isArticleCu returns true, should
   * call emitArticleFiles() for each ArticleFiles to be generated for
   * the CachedUrl. Default implementation calls createArticleFiles() and
   * emits the single ArticleFiles it returns, if any.  Primarily for
   * compatibility with old subclasses prior to the introduction of this
   * method. */
  protected void visitArticleCu(CachedUrl cu) {
    if (log.isDebug3()) log.debug3("Visit: " + cu);
    ArticleFiles res = createArticleFiles(cu);
    if (res != null) {
      emitArticleFiles(res);
    }
  }

  /** Default implementation creates an ArticleFiles with the full text CU
   * set to the visited CU.  Override to create a more complex
   * ArticleFiles */
  protected ArticleFiles createArticleFiles(CachedUrl cu) {
    ArticleFiles res = new ArticleFiles();
    res.setFullTextCu(cu);
    return res;
  }


  /** Return true if the CachedUrl is of the desired MIME type and its URL
   * matches the regular expression.  Override for other article
   * criteria */
  protected boolean isArticleCu(CachedUrl cu) {
    log.debug3("isArticleCu(" + cu.getUrl() + ")");

    if (!cu.hasContent()) {
      log.debug3("No content for: " + cu.getUrl());
      return false;
    }
    // Match pattern first; it's cheaper than getContentType()
    if (pat != null) {
      Matcher match = pat.matcher(cu.getUrl());
      if (!match.find()) {
	log.debug3("No match for " + pat + ": " + cu.getUrl());
	return false;
      }
    }
    if (mimeType != null) {
      String cuMime =
	HeaderUtil.getMimeTypeFromContentType(cu.getContentType());
      if (!mimeType.equalsIgnoreCase(cuMime)) {
	log.debug3("Mime mismatch (" + mimeType + "): " + cu.getUrl()
		   + "(" + cu.getContentType() + ")");
	return false;
      }
    }
    return true;
  }

  public boolean hasNext() {
    return findNextElement() != null;
  }

  public ArticleFiles next() {
    ArticleFiles element = findNextElement();
    nextElement = null;

    if (element != null) {
      return element;
    }
    throw new NoSuchElementException();
  }
  
  /** Not implemented */
  public void remove() {
    throw new UnsupportedOperationException("Not implemented");
  }
}
