/*
 * $Id: HighWirePressArticleIteratorFactory.java,v 1.6 2011/01/22 08:22:30 tlipkis Exp $
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

package org.lockss.plugin.highwire;

import java.io.IOException;
import java.util.*;
import java.util.regex.*;

import org.lockss.daemon.PluginException;
import org.lockss.extractor.*;
import org.lockss.plugin.*;
import org.lockss.util.*;

public class HighWirePressArticleIteratorFactory
    implements ArticleIteratorFactory,
               ArticleMetadataExtractorFactory {

  protected static Logger log = Logger.getLogger("HighWirePressArticleIteratorFactory");

  protected static final String ROOT_TEMPLATE_HTML = "\"%scgi/content/full/%s/\", base_url, volume_name";
  
  protected static final String OLD_ROOT_TEMPLATE_HTML = "\"%scgi/content/full/%d/\", base_url, volume";
  
  protected static final String ROOT_TEMPLATE_PDF = "\"%scgi/reprint/%s/\", base_url, volume_name";
  
  protected static final String OLD_ROOT_TEMPLATE_PDF = "\"%scgi/reprint/%d/\", base_url, volume";
  
  protected static final String PATTERN_TEMPLATE = "\"^%scgi/(content/full/([^/]+;)?%s/[^/]+/[^/]+|reprint/([^/]+;)?%s/[^/]+/[^/]+\\.pdf)$\", base_url, volume_name, volume_name";

  protected static final String OLD_PATTERN_TEMPLATE = "\"^%scgi/(content/full/([^/]+;)?%d/[^/]+/[^/]+|reprint/([^/]+;)?%d/[^/]+/[^/]+\\.pdf)$\", base_url, volume, volume";

  public Iterator<ArticleFiles> createArticleIterator(ArchivalUnit au,
                                                      MetadataTarget target)
      throws PluginException {
    List<String> rootTemplates = new ArrayList<String>(2);
    if ("org.lockss.plugin.highwire.HighWirePlugin".equals(au.getPluginId())) {
      rootTemplates.add(OLD_ROOT_TEMPLATE_HTML);
      rootTemplates.add(OLD_ROOT_TEMPLATE_PDF);
      return new HighWirePressArticleIterator(au, new SubTreeArticleIterator.Spec()
                                                  .setTarget(target)
                                                  .setRootTemplates(rootTemplates)
                                                  .setPatternTemplate(OLD_PATTERN_TEMPLATE));
    }
    rootTemplates.add(ROOT_TEMPLATE_HTML);
    rootTemplates.add(ROOT_TEMPLATE_PDF);
    return new HighWirePressArticleIterator(au, new SubTreeArticleIterator.Spec()
                                                .setTarget(target)
                                                .setRootTemplates(rootTemplates)
                                                .setPatternTemplate(PATTERN_TEMPLATE));
  }

  public ArticleMetadataExtractor createArticleMetadataExtractor(MetadataTarget target)
      throws PluginException {
    return new HighWirePressArticleMetadataExtractor();
  }
  
  protected static class HighWirePressArticleIterator extends SubTreeArticleIterator {
    
    protected static Pattern HTML_PATTERN = Pattern.compile("/cgi/content/full/([^/]+;)?([^/]+/[^/]+/[^/]+)$", Pattern.CASE_INSENSITIVE);
    
    protected static Pattern PDF_PATTERN = Pattern.compile("/cgi/reprint/([^/]+;)?([^/]+/[^/]+/[^/]+)\\.pdf$", Pattern.CASE_INSENSITIVE);
    
    public HighWirePressArticleIterator(ArchivalUnit au,
                                        SubTreeArticleIterator.Spec spec) {
      super(au, spec);
    }
    
    @Override
    protected ArticleFiles createArticleFiles(CachedUrl cu) {
      String url = cu.getUrl();
      Matcher mat;
      
      mat = HTML_PATTERN.matcher(url);
      if (mat.find()) {
        if (log.isDebug2()) {
          log.debug2("HTML match: " + url);
        }
        return processFullTextHtml(cu, mat);
      }
        
      mat = PDF_PATTERN.matcher(url);
      if (mat.find()) {
        if (log.isDebug2()) {
          log.debug2("PDF match: " + url);
        }
        return processFullTextPdf(cu, mat);
      }
      
      log.warning("Mismatch between article iterator factory and article iterator: " + url);
      return null;
    }

    protected ArticleFiles processFullTextHtml(CachedUrl htmlCu, Matcher htmlMat) {
      CachedUrl altCu = guessDifferentFrom(htmlCu, htmlMat,
                                           "/cgi/content/full/$2");

      if (altCu != null) {
        if (log.isDebug2()) {
          log.debug2("Skipping " + htmlCu.getUrl() + " because of " + altCu.getUrl());
        }
        return null;
      }

      ArticleFiles af = new ArticleFiles();
      af.setFullTextCu(htmlCu);
      af.setRoleCu(ArticleFiles.ROLE_FULL_TEXT_HTML, htmlCu);
      af.setRoleCu(ArticleFiles.ROLE_ARTICLE_METADATA, htmlCu);
//      guessFullTextPdf(af, htmlMat);
//      guessOtherParts(af, htmlMat);
      return af;
    }
    
    protected ArticleFiles processFullTextPdf(CachedUrl pdfCu, Matcher pdfMat) {
      CachedUrl altCu = guessDifferentFrom(pdfCu, pdfMat,
                                           "/cgi/reprint/$2.pdf",
                                           "/cgi/content/full/$1$2",
                                           "/cgi/content/full/$2");
      if (altCu != null) {
        if (log.isDebug2()) {
          log.debug2("Skipping " + pdfCu.getUrl() + " because of " + altCu.getUrl());
        }
        return null;
      }

      ArticleFiles af = new ArticleFiles();
      af.setRoleCu(ArticleFiles.ROLE_FULL_TEXT_PDF, pdfCu);
//      guessPdfLandingPage(af, pdfMat);
      af.setFullTextCu(af.getRoleCu(ArticleFiles.ROLE_FULL_TEXT_PDF_LANDING_PAGE) != null
                       ? af.getRoleCu(ArticleFiles.ROLE_FULL_TEXT_PDF_LANDING_PAGE)
                       : pdfCu);
//      guessOtherParts(af, pdfMat);
      return af;
    }
    
//    protected void guessOtherParts(ArticleFiles af, Matcher mat) {
//      guessAbstract(af, mat);
//      guessReferences(af, mat);
//      guessSupplementaryMaterials(af, mat);
//    }
//    
//    protected void guessFullTextPdf(ArticleFiles af, Matcher htmlMat) {
//      CachedUrl cu = guess(htmlMat,
//                           "/cgi/reprint/$1$2.pdf",
//                           "/cgi/reprint/$2.pdf");
//      if (cu != null) {
//        af.setRoleCu(ArticleFiles.ROLE_FULL_TEXT_PDF, cu);
//        guessPdfLandingPage(af, htmlMat);
//      }
//    }
//    
//    protected void guessPdfLandingPage(ArticleFiles af, Matcher mat) {
//      CachedUrl pdfLandCu = guess(mat,
//                                  "/cgi/reprint/$1$2",
//                                  "/cgi/reprint/$2",
//                                  "/cgi/reprintframed/$1$2",
//                                  "/cgi/reprintframed/$2",
//                                  "/cgi/framedreprint/$1$2",
//                                  "/cgi/framedreprint/$2");
//      if (pdfLandCu != null) {
//        af.setRoleCu(ArticleFiles.ROLE_FULL_TEXT_PDF_LANDING_PAGE, pdfLandCu);
//      }
//    }
//
//    protected void guessAbstract(ArticleFiles af, Matcher mat) {
//      CachedUrl absCu = guess(mat,
//                              "/cgi/content/abstract/$1$2",
//                              "/cgi/content/abstract/$2");
//      if (absCu != null) {
//        af.setRoleCu(ArticleFiles.ROLE_ABSTRACT, absCu);
//        if (af.getRoleCu(ArticleFiles.ROLE_ARTICLE_METADATA) == null) {
//          af.setRoleCu(ArticleFiles.ROLE_ARTICLE_METADATA, absCu);
//        }
//      }
//    }
//    
//    protected void guessReferences(ArticleFiles af, Matcher mat) {
//      CachedUrl refsCu = guess(mat,
//                               "/cgi/content/refs/$1$2",
//                               "/cgi/content/refs/$2");
//      if (refsCu != null) {
//        af.setRoleCu(ArticleFiles.ROLE_REFERENCES, refsCu);
//      }
//    }
//    
//    protected void guessSupplementaryMaterials(ArticleFiles af, Matcher mat) {
//      CachedUrl suppCu = guess(mat,
//                               "/cgi/content/full/$1$2/DC1",
//                               "/cgi/content/full/$2/DC1");
//      if (suppCu != null) {
//        af.setRoleCu(ArticleFiles.ROLE_SUPPLEMENTARY_MATERIALS, suppCu);
//      }
//    }
//    
//    /**
//     * <p>Tries various URLs in this AU similar to the one in the
//     * given matcher, using each one of the given matcher replacement
//     * patterns in turn, until one is found that exists and has
//     * content.</p>
//     * <p>The replacement patterns are applied to the matcher using
//     * {@link Matcher#replaceFirst(String)}.</p>
//     * <p>This method is a candidate to be refactored into a utility
//     * framework for article iterators.</p>
//     * @param mat      A matcher encapsulating a previously-matched
//     *                 URL in this AU.
//     * @param replPats Any number of replacement patterns consistent
//     *                 with the matcher, to try in sequence.
//     * @return A {@link CachedUrl} if one of the replacement patterns
//     *         produces a URL in this AU that exists and has content,
//     *         or <code>null</code> otherwise.
//     * @see Matcher#replaceFirst(String)
//     * @see CachedUrl#hasContent()
//     */
//    protected CachedUrl guess(Matcher mat,
//                              String... replPats) {
//      for (String replPat : replPats) {
//        CachedUrl guessCu = au.makeCachedUrl(mat.replaceFirst(replPat));
//        if (guessCu != null && guessCu.hasContent()) {
//          return guessCu;
//        }
//      }
//      return null;
//    }
    
    /**
     * <p>Does the same thing as
     * {@link #guess(Matcher, String...)}, with the
     * further guarantee that the returned variant URL does not
     * represent the same URL as the given URL.</p>
     * <p>This method is a candidate to be refactored into a utility
     * framework for article iterators.</p>
     * @param original The original URL from this AU.
     * @param mat      A matcher encapsulating a previously-matched
     *                 URL in this AU.
     * @param replPats Any number of replacement patterns consistent
     *                 with the matcher, to try in sequence.
     * @return A {@link CachedUrl} if one of the replacement patterns
     *         produces a URL in this AU that exists, does not
     *         represent the same URL as the original, and has
     *         content, or <code>null</code> otherwise.
     * @see #guess(Matcher, String...)
     */
    protected CachedUrl guessDifferentFrom(CachedUrl original,
                                           Matcher mat,
                                           String... replPats) {
      for (String replPat : replPats) {
        CachedUrl guessCu = au.makeCachedUrl(mat.replaceFirst(replPat));
        if (guessCu != null && guessCu.hasContent() && !guessCu.getUrl().equals(original.getUrl())) {
          return guessCu;
        }
      }
      return null;
    }
   
  }

  protected static class HighWirePressArticleMetadataExtractor
    extends SingleArticleMetadataExtractor {

    @Override
    public ArticleMetadata extract(MetadataTarget target, ArticleFiles af)
	throws IOException, PluginException {
      String url = af.getFullTextUrl();
      ArticleMetadata am = new ArticleMetadata();
      am.put(MetadataField.FIELD_ACCESS_URL, url);
      return am;
    }

  }

  public static void main(String[] args) {
    String input = "http://pediatrics.aappublications.org/cgi/reprint/foo;125/Supplement_3/S69.pdf";
    Matcher mat = HighWirePressArticleIterator.PDF_PATTERN.matcher(input);
    System.out.println(mat.find());
    System.out.println(mat.replaceFirst("/cgi/content/full/$1$2"));
    System.out.println(mat.reset().group());
  }
  
}
