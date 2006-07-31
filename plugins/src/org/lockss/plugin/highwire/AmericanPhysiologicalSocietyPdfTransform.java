/*
 * $Id$
 */

/*

Copyright (c) 2000-2006 Board of Trustees of Leland Stanford Jr. University,
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
import java.util.Iterator;

import org.lockss.filter.*;
import org.lockss.util.*;
import org.lockss.util.PdfPageTransformUtil.*;
import org.lockss.util.PdfTransformUtil.*;
import org.pdfbox.encryption.PDFEncryption;
import org.pdfbox.pdmodel.PDPage;

/**
 * <p>This PDF transform identifies and processes PDF documents that
 * match a template found in certain titles published by the
 * American Physiological Society.</p>
 * @author Thib Guicherd-Callin
 * @see <a href="http://www.physiology.org/">American Physiological
 * Society Journals Online</a>
 * @see HighWirePdfFilterRule
 */
public class AmericanPhysiologicalSocietyPdfTransform extends PdfConditionalTransform {

  protected static Logger logger = Logger.getLogger("PhysiologicalGenomicsPdfTransform");

  /**
   * <p>A PDF token sequence mutator that removes the variable portion
   * of the text that appears vertically on pages other than the
   * first one in PDF documents found in certain titles published by
   * the American Physiological Society.</p>
   * @author Thib Guicherd-Callin
   * @see AmericanPhysiologicalSocietyPdfTransform
   */
  public static class OtherPagesVertical
      implements PdfPageTransform, PdfTokenSequenceMutator {

    public void transform(PdfDocument pdfDocument, PDPage pdfPage) throws IOException {
      PdfPageTransformUtil.runPdfTokenSequenceMutator(this, pdfDocument, pdfPage, true);
    }

    /**
     * <p>This class cannot be publicly instantiated.</p>
     */
    protected OtherPagesVertical() {}

    /* Inherit documentation */
    public boolean isLastTokenInSequence(Object candidateToken) {
      return PdfPageTransformUtil.isEndTextObjectOperator(candidateToken);
    }

    /* Inherit documentation */
    public void processTokenSequence(Object[] pdfTokens) throws IOException {
      PdfPageTransformUtil.replacePdfString(pdfTokens[11], " ");
    }

    /* Inherit documentation */
    public boolean tokenSequenceMatches(Object[] pdfTokens) {
      boolean ret = PdfPageTransformUtil.isBeginTextObjectOperator(pdfTokens[0])
      && PdfPageTransformUtil.isEndTextObjectOperator(pdfTokens[17])
      && PdfPageTransformUtil.isBeginTextObjectOperator(pdfTokens[20])
      && PdfPageTransformUtil.isEndTextObjectOperator(pdfTokens[35])
      && PdfPageTransformUtil.isBeginTextObjectOperator(pdfTokens[38])
      && PdfPageTransformUtil.isShowTextOperator(pdfTokens[12])
      && PdfPageTransformUtil.isPdfString(pdfTokens[11])
      && PdfPageTransformUtil.isShowTextOperator(pdfTokens[32])
      && PdfPageTransformUtil.isPdfString(pdfTokens[31])
      && PdfPageTransformUtil.isShowTextOperator(pdfTokens[50])
      && PdfPageTransformUtil.isPdfString(pdfTokens[49])
      && "Downloaded from ".equals(PdfPageTransformUtil.getPdfString(pdfTokens[49]));
      logger.debug3("Evaluating candidate match: " + Boolean.toString(ret));
      return ret;
    }

    /* Inherit documentation */
    public int tokenSequenceSize() {
      return 52;
    }

    /**
     * <p>A singleton instance of this class.</p>
     */
    private static OtherPagesVertical singleton;

    /**
     * <p>Gets a singleton instance of this class.</p>
     * @return An instance of this class.
     */
    public static synchronized OtherPagesVertical makeTransform() {
      if (singleton == null) {
        singleton = new OtherPagesVertical();
      }
      return singleton;
    }

  }

  /**
   * <p>A PDF page transform that removes the variable portion
   * of the text that appears vertically on the first page in PDF
   * documents found in certain titles published by the
   * American Physiological Society.</p>
   * @author Thib Guicherd-Callin
   * @see AmericanPhysiologicalSocietyPdfTransform
   */
  protected static class FirstPageVertical
      implements PdfPageTransform, PdfTokenSequenceMutator {

    public void transform(PdfDocument pdfDocument, PDPage pdfPage) throws IOException {
      PdfPageTransformUtil.runPdfTokenSequenceMutator(this, pdfDocument, pdfPage, true);
    }

    /**
     * <p>This class cannot be publicly instantiated.</p>
     */
    protected FirstPageVertical() {}

    /* Inherit documentation */
    public boolean isLastTokenInSequence(Object candidateToken) {
      return PdfPageTransformUtil.isEndTextObjectOperator(candidateToken);
    }

    /* Inherit documentation */
    public void processTokenSequence(Object[] pdfTokens) throws IOException {
      PdfPageTransformUtil.replacePdfString(pdfTokens[11], " ");
    }

    /* Inherit documentation */
    public boolean tokenSequenceMatches(Object[] pdfTokens) {
      boolean ret = PdfPageTransformUtil.isBeginTextObjectOperator(pdfTokens[0])
      && PdfPageTransformUtil.isShowTextOperator(pdfTokens[12])
      && PdfPageTransformUtil.isPdfString(pdfTokens[11])
      && PdfPageTransformUtil.isShowTextOperator(pdfTokens[21])
      && PdfPageTransformUtil.isPdfString(pdfTokens[20])
      && PdfPageTransformUtil.isShowTextOperator(pdfTokens[28])
      && PdfPageTransformUtil.isPdfString(pdfTokens[27])
      && "Downloaded from ".equals(PdfPageTransformUtil.getPdfString(pdfTokens[27]));
      logger.debug3("Evaluating candidate match: " + Boolean.toString(ret));
      return ret;
    }

    /* Inherit documentation */
    public int tokenSequenceSize() {
      return 30;
    }

    /**
     * <p>A singleton instance of this class.</p>
     */
    private static FirstPageVertical singleton;

    /**
     * <p>Gets a singleton instance of this class.</p>
     * @return An instance of this class.
     */
    public static synchronized FirstPageVertical makeTransform() {
      if (singleton == null) {
        singleton = new FirstPageVertical();
      }
      return singleton;
    }

  }

  protected static class FirstPageString {

    /**
     * <p>This class cannot be publicly instantiated.</p>
     */
    protected FirstPageString() {}

    /**
     * <p>A singleton instance of the transform encapuslated by this
     * class.</p>
     */
    private static PdfStringReplacePageTransform underlyingTransform;

    /**
     * <p>Gets a singleton instance of this class.</p>
     * @return An instance of this class.
     */
    public static synchronized PdfStringReplacePageTransform makeTransform() {
      if (underlyingTransform == null) {
        underlyingTransform = PdfStringReplacePageTransform.makeTransformStartsWith("This information is current as of ",
                                                                                    " ",
                                                                                    true);
      }
      return underlyingTransform;
    }

  }

  /**
   * <p>A singleton instance of this class.</p>
   */
  private static AmericanPhysiologicalSocietyPdfTransform singleton;

  /**
   * <p>A singleton instance of this class' undelrying transform.</p>
   */
  private static PdfCompoundTransform underlyingTransform;

  /**
   * <p>Gets a singleton instance of this class.</p>
   * @return An instance of this class.
   */
  public static synchronized AmericanPhysiologicalSocietyPdfTransform makeTransform() {
    if (singleton == null) {
      singleton = new AmericanPhysiologicalSocietyPdfTransform(makeUnderlyingTransform());
    }
    return singleton;
  }

  protected static synchronized PdfCompoundTransform makeUnderlyingTransform() {
    if (underlyingTransform == null) {
      underlyingTransform = new PdfCompoundTransform();
      underlyingTransform.addPdfTransform(new PdfFirstPageTransform(FirstPageVertical.makeTransform()));
      underlyingTransform.addPdfTransform(new PdfFirstPageTransform(FirstPageString.makeTransform()));
      underlyingTransform.addPdfTransform(new PdfEachPageExceptFirstTransform(OtherPagesVertical.makeTransform()));
      // TODO: metadata info
      // TODO: metadata XML
    }
    return underlyingTransform;
  }

  protected AmericanPhysiologicalSocietyPdfTransform(PdfTransform underlyingTransform) {
    super(underlyingTransform);
  }

  public boolean identify(PdfDocument pdfDocument) throws IOException {
    return PdfPageTransformUtil.runPdfTokenSequenceMatcher(FirstPageVertical.makeTransform(),
                                                           (PDPage)pdfDocument.getPageIterator().next());
  }

}
