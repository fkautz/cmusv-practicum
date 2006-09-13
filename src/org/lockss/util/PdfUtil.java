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

package org.lockss.util;

import java.io.*;
import java.util.*;

import org.apache.commons.collections.iterators.*;
import org.lockss.filter.pdf.*;
import org.pdfbox.cos.*;
import org.pdfbox.util.PDFOperator;

/**
 * <p>Utilities for PDF processing and filtering.</p>
 * @author Thib Guicherd-Callin
 */
public class PdfUtil {

  /**
   * <p>A PDF transform that does nothing, for testing.</p>
   * @author Thib Guicherd-Callin
   */
  public static class IdentityDocumentTransform implements DocumentTransform {

    protected boolean returnValue;

    public IdentityDocumentTransform() {
      this(true);
    }

    public IdentityDocumentTransform(boolean returnValue) {
      this.returnValue = returnValue;
    }

    /* Inherit documentation */
    public boolean transform(PdfDocument pdfDocument) throws IOException {
      logger.debug3("Indentity document transform: " + returnValue);
      return returnValue;
    }

  }

  /**
   * <p>A PDF page transform that does nothing, for testing.</p>
   * @author Thib Guicherd-Callin
   */
  public static class IdentityPageTransform implements PageTransform {

    protected boolean returnValue;

    public IdentityPageTransform() {
      this(true);
    }

    public IdentityPageTransform(boolean returnValue) {
      this.returnValue = returnValue;
    }

    /* Inherit documentation */
    public boolean transform(PdfPage pdfPage) throws IOException {
      logger.debug3("Indentity page transform: " + returnValue);
      return returnValue;
    }

  }

  /**
   * <p>An interface for looping policies.</p>
   * <p>This interface is intended for the following types of loops:</p>
<pre>
boolean success = resultPolicy.resetResult();
while (...) {
  boolean oneStep = doSomething(...);
  success = resultPolicy.updateResult(success, oneStep);
  if (!resultPolicy.shouldKeepGoing(success)) {
    break;
  }
}
return success;
</pre>
   * <p>For instance, the above loop could have short-circuiting "or"
   * semantics: it returns true as soon as any of the steps returns
   * true, and if none return true it returns false. This would be
   * achieved with {@link ResultPolicy#resetResult} returning false,
   * {@link ResultPolicy#updateResult}<code>(success, oneStep)</code>
   * returning <code>success || oneStep</code>, and
   * {@link ResultPolicy#shouldKeepGoing}<code>(success)</code>
   * returning <code>!success</code>.</p>
   * <p>To give it non short-circuiting semantics, just make
   * {@link ResultPolicy#shouldKeepGoing} return true constantly.</p>
   * <p>Likewise, the loop can have "and" semantics with or without
   * short-circuiting, for appropriate values of the three
   * methods.</p>
   * <p>Examples of how to use these result policies can be found
   * for instance in {@link AggregateDocumentTransform#transform},
   * {@link AggregatePageTransform#transform} or
   * {@link TransformSelectedPages#transform}.</p>
   * @author Thib Guicherd-Callin
   * @see PdfUtil#AND
   * @see PdfUtil#AND_ALL
   * @see PdfUtil#OR
   * @see PdfUtil#OR_ALL
   */
  public interface ResultPolicy {

    /**
     * <p>Provides the initial value for the success flag.</p>
     * @return The value of the success flag before the loop.
     */
    boolean resetResult();

    /**
     * <p>Determines whether the loop should continue based on the
     * current value of the success flag (passed as argument).</p>
     * @param currentResult The current value of the success flag.
     * @return Whether the loop should continue based on the current
     *         value of the success flag.
     */
    boolean shouldKeepGoing(boolean currentResult);

    /**
     * <p>Computes the new value of the success flag, given the
     * current value of the success flag and a new result from an
     * iteration of the loop.</p>
     * @param currentResult The current value of the success flag.
     * @param update        A new result from an iteration of the loop.
     * @return The new value of the success flag.
     */
    boolean updateResult(boolean currentResult, boolean update);

  }

  /**
   * <p>A version of {@link ResultPolicy} that implements
   * short-circuiting "and" semantics.</p>
   * @see PdfUtil#AND_ALL
   */
  public static final ResultPolicy AND = new ResultPolicy() {

    /* Inherit documentation */
    public boolean resetResult() {
      return true;
    }

    /* Inherit documentation */
    public boolean shouldKeepGoing(boolean currentResult) {
      return currentResult;
    }

    /* Inherit documentation */
    public boolean updateResult(boolean currentResult, boolean update) {
      return currentResult && update;
    }

  };

  /**
   * <p>A version of {@link ResultPolicy} that implements
   * non short-circuiting "and" semantics.</p>
   * @see PdfUtil#AND
   */
  public static final ResultPolicy AND_ALL = new ResultPolicy() {

    /* Inherit documentation */
    public boolean resetResult() {
      return true;
    }

    /* Inherit documentation */
    public boolean shouldKeepGoing(boolean currentResult) {
      return true;
    }

    /* Inherit documentation */
    public boolean updateResult(boolean currentResult, boolean update) {
      return currentResult && update;
    }

  };

  /**
   * <p>A version of {@link ResultPolicy} that implements
   * short-circuiting "or" semantics.</p>
   * @see PdfUtil#OR_ALL
   */
  public static final ResultPolicy OR = new ResultPolicy() {

    /* Inherit documentation */
    public boolean resetResult() {
      return false;
    }

    /* Inherit documentation */
    public boolean shouldKeepGoing(boolean currentResult) {
      return !currentResult;
    }

    /* Inherit documentation */
    public boolean updateResult(boolean currentResult, boolean update) {
      return currentResult || update;
    }

  };

  /**
   * <p>A version of {@link ResultPolicy} that implements
   * non short-circuiting "or" semantics.</p>
   * @see PdfUtil#OR
   */
  public static final ResultPolicy OR_ALL = new ResultPolicy() {

    /* Inherit documentation */
    public boolean resetResult() {
      return false;
    }

    /* Inherit documentation */
    public boolean shouldKeepGoing(boolean currentResult) {
      return true;
    }

    /* Inherit documentation */
    public boolean updateResult(boolean currentResult, boolean update) {
      return currentResult || update;
    }

  };

  /**
   * <p>The PDF <code>c</code> operator string.</p>
   */
  public static final String APPEND_CURVED_SEGMENT = "c";

  /**
   * <p>The PDF <code>y</code> operator string.</p>
   */
  public static final String APPEND_CURVED_SEGMENT_FINAL = "y";

  /**
   * <p>The PDF <code>v</code> operator string.</p>
   */
  public static final String APPEND_CURVED_SEGMENT_INITIAL = "v";

  /**
   * <p>The PDF <code>re</code> operator string.</p>
   */
  public static final String APPEND_RECTANGLE = "re";

  /**
   * <p>The PDF <code>l</code> operator string.</p>
   */
  public static final String APPEND_STRAIGHT_LINE_SEGMENT = "l";

  /**
   * <p>The PDF <code>BX</code> operator string.</p>
   */
  public static final String BEGIN_COMPATIBILITY_SECTION = "BX";

  /**
   * <p>The PDF <code>ID</code> operator string.</p>
   */
  public static final String BEGIN_INLINE_IMAGE_DATA = "ID";

  /**
   * <p>The PDF <code>BI</code> operator string.</p>
   */
  public static final String BEGIN_INLINE_IMAGE_OBJECT = "BI";

  /**
   * <p>The PDF <code>BMC</code> operator string.</p>
   */
  public static final String BEGIN_MARKED_CONTENT = "BMC";

  /**
   * <p>The PDF <code>BDC</code> operator string.</p>
   */
  public static final String BEGIN_MARKED_CONTENT_PROP = "BDC";

  /**
   * <p>The PDF <code>m</code> operator string.</p>
   */
  public static final String BEGIN_NEW_SUBPATH = "m";

  /**
   * <p>The PDF <code>BT</code> operator string.</p>
   */
  public static final String BEGIN_TEXT_OBJECT = "BT";

  /**
   * <p>The PDF <code>b*</code> operator string.</p>
   */
  public static final String CLOSE_FILL_STROKE_EVENODD = "b*";

  /**
   * <p>The PDF <code>b</code> operator string.</p>
   */
  public static final String CLOSE_FILL_STROKE_NONZERO = "b";

  /**
   * <p>The PDF <code>s</code> operator string.</p>
   */
  public static final String CLOSE_STROKE = "s";

  /**
   * <p>The PDF <code>h</code> operator string.</p>
   */
  public static final String CLOSE_SUBPATH = "h";

  /**
   * <p>The PDF <code>cm</code> operator string.</p>
   */
  public static final String CONCATENATE_MATRIX = "cm";

  /**
   * <p>The PDF <code>MP</code> operator string.</p>
   */
  public static final String DEFINE_MARKED_CONTENT_POINT = "MP";

  /**
   * <p>The PDF <code>DP</code> operator string.</p>
   */
  public static final String DEFINE_MARKED_CONTENT_POINT_PROP = "DP";

  /**
   * <p>The PDF <code>EX</code> operator string.</p>
   */
  public static final String END_COMPATIBILITY_SECTION = "EX";

  /**
   * <p>The PDF <code>EI</code> operator string.</p>
   */
  public static final String END_INLINE_IMAGE_OBJECT = "EI";

  /**
   * <p>The PDF <code>EMC</code> operator string.</p>
   */
  public static final String END_MARKED_CONTENT = "EMC";

  /**
   * <p>The PDF <code>n</code> operator string.</p>
   */
  public static final String END_PATH = "n";

  /**
   * <p>The PDF <code>ET</code> operator string.</p>
   */
  public static final String END_TEXT_OBJECT = "ET";

  /**
   * <p>The PDF <code>f*</code> operator string.</p>
   */
  public static final String FILL_EVENODD = "f*";

  /**
   * <p>The PDF <code>f</code> operator string.</p>
   */
  public static final String FILL_NONZERO = "f";

  /**
   * <p>The PDF <code>F</code> operator string.</p>
   */
  public static final String FILL_NONZERO_OBSOLETE = "F";

  /**
   * <p>The PDF <code>B*</code> operator string.</p>
   */
  public static final String FILL_STROKE_EVENODD = "B*";

  /**
   * <p>The PDF <code>B</code> operator string.</p>
   */
  public static final String FILL_STROKE_NONZERO = "B";

  /**
   * <p>The PDF <code>Do</code> operator string.</p>
   */
  public static final String INVOKE_NAMED_XOBJECT = "Do";

  /**
   * <p>The PDF <code>Td</code> operator string.</p>
   */
  public static final String MOVE_TEXT_POSITION = "Td";

  /**
   * <p>The PDF <code>TD</code> operator string.</p>
   */
  public static final String MOVE_TEXT_POSITION_SET_LEADING = "TD";

  /**
   * <p>The PDF <code>T*</code> operator string.</p>
   */
  public static final String MOVE_TO_NEXT_LINE = "T*";

  /**
   * <p>The PDF <code>'</code> operator string.</p>
   */
  public static final String MOVE_TO_NEXT_LINE_SHOW_TEXT = "\'";

  /**
   * <p>The PDF <code>sh</code> operator string.</p>
   */
  public static final String PAINT_SHADING_PATTERN = "sh";

  /**
   * <p>The PDF <code>Q</code> operator string.</p>
   */
  public static final String RESTORE_GRAPHICS_STATE = "Q";

  /**
   * <p>The PDF <code>q</code> operator string.</p>
   */
  public static final String SAVE_GRAPHICS_STATE = "q";

  /**
   * <p>The PDF <code>Tc</code> operator string.</p>
   */
  public static final String SET_CHARACTER_SPACING = "Tc";

  /**
   * <p>The PDF <code>W*</code> operator string.</p>
   */
  public static final String SET_CLIPPING_PATH_EVENODD = "W*";

  /**
   * <p>The PDF <code>W</code> operator string.</p>
   */
  public static final String SET_CLIPPING_PATH_NONZERO = "W";

  /**
   * <p>The PDF <code>k</code> operator string.</p>
   */
  public static final String SET_CMYK_COLOR_NONSTROKING = "k";

  /**
   * <p>The PDF <code>K</code> operator string.</p>
   */
  public static final String SET_CMYK_COLOR_STROKING = "K";

  /**
   * <p>The PDF <code>sc</code> operator string.</p>
   */
  public static final String SET_COLOR_NONSTROKING = "sc";

  /**
   * <p>The PDF <code>scn</code> operator string.</p>
   */
  public static final String SET_COLOR_NONSTROKING_SPECIAL = "scn";

  /**
   * <p>The PDF <code>ri</code> operator string.</p>
   */
  public static final String SET_COLOR_RENDERING_INTENT = "ri";

  /**
   * <p>The PDF <code>cs</code> operator string.</p>
   */
  public static final String SET_COLOR_SPACE_NONSTROKING = "cs";

  /**
   * <p>The PDF <code>CS</code> operator string.</p>
   */
  public static final String SET_COLOR_SPACE_STROKING = "CS";

  /**
   * <p>The PDF <code>SC</code> operator string.</p>
   */
  public static final String SET_COLOR_STROKING = "SC";

  /**
   * <p>The PDF <code>SCN</code> operator string.</p>
   */
  public static final String SET_COLOR_STROKING_SPECIAL = "SCN";

  /**
   * <p>The PDF <code>i</code> operator string.</p>
   */
  public static final String SET_FLATNESS_TOLERANCE = "i";

  /**
   * <p>The PDF <code>gs</code> operator string.</p>
   */
  public static final String SET_FROM_GRAPHICS_STATE = "gs";

  /**
   * <p>The PDF <code>d0</code> operator string.</p>
   */
  public static final String SET_GLYPH_WIDTH = "d0";

  /**
   * <p>The PDF <code>d1</code> operator string.</p>
   */
  public static final String SET_GLYPH_WIDTH_BOUNDING_BOX = "d1";

  /**
   * <p>The PDF <code>g</code> operator string.</p>
   */
  public static final String SET_GRAY_LEVEL_NONSTROKING = "g";

  /**
   * <p>The PDF <code>G</code> operator string.</p>
   */
  public static final String SET_GRAY_LEVEL_STROKING = "G";

  /**
   * <p>The PDF <code>Tz</code> operator string.</p>
   */
  public static final String SET_HORIZONTAL_TEXT_SCALING = "Tz";

  /**
   * <p>The PDF <code>J</code> operator string.</p>
   */
  public static final String SET_LINE_CAP_STYLE = "J";

  /**
   * <p>The PDF <code>d</code> operator string.</p>
   */
  public static final String SET_LINE_DASH_PATTERN = "d";

  /**
   * <p>The PDF <code>j</code> operator string.</p>
   */
  public static final String SET_LINE_JOIN_STYLE = "j";

  /**
   * <p>The PDF <code>w</code> operator string.</p>
   */
  public static final String SET_LINE_WIDTH = "w";

  /**
   * <p>The PDF <code>M</code> operator string.</p>
   */
  public static final String SET_MITER_LIMIT = "M";

  /**
   * <p>The PDF <code>rg</code> operator string.</p>
   */
  public static final String SET_RGB_COLOR_NONSTROKING = "rg";

  /**
   * <p>The PDF <code>RG</code> operator string.</p>
   */
  public static final String SET_RGB_COLOR_STROKING = "RG";

  /**
   * <p>The PDF <code>"</code> operator string.</p>
   */
  public static final String SET_SPACING_MOVE_TO_NEXT_LINE_SHOW_TEXT = "\"";

  /**
   * <p>The PDF <code>Tf</code> operator string.</p>
   */
  public static final String SET_TEXT_FONT_AND_SIZE = "Tf";

  /**
   * <p>The PDF <code>TL</code> operator string.</p>
   */
  public static final String SET_TEXT_LEADING = "TL";

  /**
   * <p>The PDF <code>Tm</code> operator string.</p>
   */
  public static final String SET_TEXT_MATRIX = "Tm";

  /**
   * <p>The PDF <code>Tr</code> operator string.</p>
   */
  public static final String SET_TEXT_RENDERING_MODE = "Tr";

  /**
   * <p>The PDF <code>Ts</code> operator string.</p>
   */
  public static final String SET_TEXT_RISE = "Ts";

  /**
   * <p>The PDF <code>Tw</code> operator string.</p>
   */
  public static final String SET_WORD_SPACING = "Tw";

  /**
   * <p>The PDF <code>Tj</code> operator string.</p>
   */
  public static final String SHOW_TEXT = "Tj";

  /**
   * <p>The PDF <code>TJ</code> operator string.</p>
   */
  public static final String SHOW_TEXT_GLYPH_POSITIONING = "TJ";

  /**
   * <p>The PDF <code>S</code> operator string.</p>
   */
  public static final String STROKE = "S";

  /**
   * <p>A logger for use by this class.</p>
   */
  protected static Logger logger = Logger.getLogger("PdfUtil");

  /**
   * <p>All 73 operators defined by PDF 1.6, in the order they are
   * listed in the specification (Appendix A).</p>
   * @see <a href="http://partners.adobe.com/public/developer/en/pdf/PDFReference16.pdf">PDF Reference, Fifth Edition, Version 1.6</a>
   */
  protected static final String[] PDF_1_6_OPERATORS = {
    CLOSE_FILL_STROKE_NONZERO,
    FILL_STROKE_NONZERO,
    CLOSE_FILL_STROKE_EVENODD,
    FILL_STROKE_EVENODD,
    BEGIN_MARKED_CONTENT_PROP,
    BEGIN_INLINE_IMAGE_OBJECT,
    BEGIN_MARKED_CONTENT,
    BEGIN_TEXT_OBJECT,
    BEGIN_COMPATIBILITY_SECTION,
    APPEND_CURVED_SEGMENT,
    CONCATENATE_MATRIX,
    SET_COLOR_SPACE_STROKING,
    SET_COLOR_SPACE_NONSTROKING,
    SET_LINE_DASH_PATTERN,
    SET_GLYPH_WIDTH,
    SET_GLYPH_WIDTH_BOUNDING_BOX,
    INVOKE_NAMED_XOBJECT,
    DEFINE_MARKED_CONTENT_POINT_PROP,
    END_INLINE_IMAGE_OBJECT,
    END_MARKED_CONTENT,
    END_TEXT_OBJECT,
    END_COMPATIBILITY_SECTION,
    FILL_NONZERO,
    FILL_NONZERO_OBSOLETE,
    FILL_EVENODD,
    SET_GRAY_LEVEL_STROKING,
    SET_GRAY_LEVEL_NONSTROKING,
    SET_FROM_GRAPHICS_STATE,
    CLOSE_SUBPATH,
    SET_FLATNESS_TOLERANCE,
    BEGIN_INLINE_IMAGE_DATA,
    SET_LINE_JOIN_STYLE,
    SET_LINE_CAP_STYLE,
    SET_CMYK_COLOR_STROKING,
    SET_CMYK_COLOR_NONSTROKING,
    APPEND_STRAIGHT_LINE_SEGMENT,
    BEGIN_NEW_SUBPATH,
    SET_MITER_LIMIT,
    DEFINE_MARKED_CONTENT_POINT,
    END_PATH,
    SAVE_GRAPHICS_STATE,
    RESTORE_GRAPHICS_STATE,
    APPEND_RECTANGLE,
    SET_RGB_COLOR_STROKING,
    SET_RGB_COLOR_NONSTROKING,
    SET_COLOR_RENDERING_INTENT,
    CLOSE_STROKE,
    STROKE,
    SET_COLOR_STROKING,
    SET_COLOR_NONSTROKING,
    SET_COLOR_STROKING_SPECIAL,
    SET_COLOR_NONSTROKING_SPECIAL,
    PAINT_SHADING_PATTERN,
    MOVE_TO_NEXT_LINE,
    SET_CHARACTER_SPACING,
    MOVE_TEXT_POSITION,
    MOVE_TEXT_POSITION_SET_LEADING,
    SET_TEXT_FONT_AND_SIZE,
    SHOW_TEXT,
    SHOW_TEXT_GLYPH_POSITIONING,
    SET_TEXT_LEADING,
    SET_TEXT_MATRIX,
    SET_TEXT_RENDERING_MODE,
    SET_TEXT_RISE,
    SET_WORD_SPACING,
    SET_HORIZONTAL_TEXT_SCALING,
    APPEND_CURVED_SEGMENT_INITIAL,
    SET_LINE_WIDTH,
    SET_CLIPPING_PATH_NONZERO,
    SET_CLIPPING_PATH_EVENODD,
    APPEND_CURVED_SEGMENT_FINAL,
    MOVE_TO_NEXT_LINE_SHOW_TEXT,
    SET_SPACING_MOVE_TO_NEXT_LINE_SHOW_TEXT,
  };

  /**
   * <p>Parses a PDF document from an input stream, applies a
   * transform to it, and writes the result to an output stream.</p>
   * <p>This method closes the PDF document at the end of processing</p>
   * @param pdfTransform    A PDF transform.
   * @param pdfInputStream  An input stream containing a PDF document.
   * @param pdfOutputStream An output stream into which to write the
   *                        transformed PDF document.
   * @throws IOException if any processing error occurs.
   */
  public static void applyPdfTransform(DocumentTransform pdfTransform,
                                       InputStream pdfInputStream,
                                       OutputStream pdfOutputStream)
      throws IOException {
    boolean mustReleaseResources = false;
    PdfDocument pdfDocument = null;
    try {
      // Parse
      pdfDocument = new PdfDocument(pdfInputStream);
      mustReleaseResources = true;

      // Transform
      pdfTransform.transform(pdfDocument);

      // Save
      pdfDocument.save(pdfOutputStream);
    }
    finally {
      if (mustReleaseResources) {
        pdfDocument.close();
      }
    }
  }

  public static Iterator getPdf16Operators() {
    return UnmodifiableIterator.decorate(new ObjectArrayIterator(PDF_1_6_OPERATORS));
  }

  /**
   * <p>Extracts the float data associated with the PDF token at the
   * given index that is known to be a PDF float.</p>
   * <p>Preconditions:</p>
   * <ul>
   *  <li><code>isPdfFloat(tokens, index)</code></li>
   * </ul>
   * @param tokens A list of tokens.
   * @param index  The index of the selected token.
   * @return The float associated with the selected PDF float.
   * @see #isPdfFloat(List, int)
   * @see #getPdfFloat(Object)
   */
  public static float getPdfFloat(List tokens,
                                  int index) {
    return getPdfFloat(tokens.get(index));
  }

  /**
   * <p>Extracts the float data associated with a PDF token that is
   * a PDF float.</p>
   * <p>Preconditions:</p>
   * <ul>
   *  <li><code>isPdfFloat(pdfFloat)</code></li>
   * </ul>
   * @param pdfFloat A PDF float.
   * @return The float associated with this PDF float.
   * @see COSFloat#floatValue
   * @see #isPdfFloat(Object)
   */
  public static float getPdfFloat(Object pdfFloat) {
    return ((COSFloat)pdfFloat).floatValue();
  }

  /**
   * <p>Extracts the integer data associated with the PDF token at the
   * given index that is known to be a PDF integer.</p>
   * <p>Preconditions:</p>
   * <ul>
   *  <li><code>isPdfInteger(tokens, index)</code></li>
   * </ul>
   * @param tokens A list of tokens.
   * @param index  The index of the selected token.
   * @return The integer associated with the selected PDF integer.
   * @see #isPdfInteger(List, int)
   * @see #getPdfInteger(Object)
   */
  public static int getPdfInteger(List tokens,
                                  int index) {
    return getPdfInteger(tokens.get(index));
  }

  /**
   * <p>Extracts the integer data associated with a PDF token that is
   * a PDF integer.</p>
   * <p>Preconditions:</p>
   * <ul>
   *  <li><code>isPdfInteger(pdfInteger)</code></li>
   * </ul>
   * @param pdfFloat A PDF integer.
   * @return The integer associated with this PDF integer.
   * @see COSInteger#intValue
   * @see #isPdfInteger(Object)
   */
  public static int getPdfInteger(Object pdfInteger) {
    return ((COSInteger)pdfInteger).intValue();
  }

  public static Iterator getPdfOperators() {
    return getPdf16Operators();
  }

  /**
   * <p>Extracts the string data associated with the PDF token at the
   * given index that is known to be a PDF string.</p>
   * <p>Preconditions:</p>
   * <ul>
   *  <li><code>isPdfString(tokens, index)</code></li>
   * </ul>
   * @param tokens A list of tokens.
   * @param index  The index of the selected token.
   * @return The {@link String} associated with the selected PDF string.
   * @see #isPdfString(List, int)
   * @see #getPdfString(Object)
   */
  public static String getPdfString(List tokens,
                                    int index) {
    return getPdfString(tokens.get(index));
  }

  /**
   * <p>Extracts the string data associated with a PDF token that is
   * a PDF string.</p>
   * <p>Preconditions:</p>
   * <ul>
   *  <li><code>isPdfString(pdfString)</code></li>
   * </ul>
   * @param pdfString A PDF string.
   * @return The {@link String} associated with this PDF string.
   * @see COSString#getString
   * @see #isPdfString(Object)
   */
  public static String getPdfString(Object pdfString) {
    return ((COSString)pdfString).getString();
  }

  /**
   * <p>Determines if the token at the given index is the "begin text object"
   * PDF operator.</p>
   * @param tokens A list of tokens.
   * @param index  The index of the candidate token.
   * @return True if the selected token is the expected operator, false
   *         otherwise.
   * @see #isBeginTextObject(Object)
   */
  public static boolean isBeginTextObject(List tokens,
                                          int index) {
    return isBeginTextObject(tokens.get(index));
  }

  /**
   * <p>Determines if a candidate PDF token is the "begin text object"
   * PDF operator.</p>
   * @param candidateToken A candidate PDF token.
   * @return True if the argument is the expected operator, false
   *         otherwise.
   * @see #BEGIN_TEXT_OBJECT
   * @see #isPdfOperator
   */
  public static boolean isBeginTextObject(Object candidateToken) {
    return matchPdfOperator(candidateToken,
                            BEGIN_TEXT_OBJECT);
  }

  /**
   * <p>Determines if the token at the given index is the "end text object"
   * PDF operator.</p>
   * @param tokens A list of tokens.
   * @param index  The index of the candidate token.
   * @return True if the selected token is the expected operator, false
   *         otherwise.
   * @see #isEndTextObject(Object)
   */
  public static boolean isEndTextObject(List tokens,
                                        int index) {
    return isEndTextObject(tokens.get(index));
  }

  /**
   * <p>Determines if a candidate PDF token is the "end text object"
   * PDF operator.</p>
   * @param candidateToken A candidate PDF token.
   * @return True is the argument is the expected operator, false
   *         otherwise.
   * @see #END_TEXT_OBJECT
   * @see #isPdfOperator
   */
  public static boolean isEndTextObject(Object candidateToken) {
    return matchPdfOperator(candidateToken,
                            END_TEXT_OBJECT);
  }

  /**
   * <p>Determines if a candidate PDF token at the given index is a PDF float token.</p>
   * @param tokens A list of tokens.
   * @param index The index of the selected token.
   * @return True if the selected token is a PDF float, false otherwise.
   * @see #isPdfFloat(Object)
   */
  public static boolean isPdfFloat(List tokens,
                                   int index) {
    return isPdfFloat(tokens.get(index));
  }

  /**
   * <p>Determines if a candidate PDF token is a PDF float token.</p>
   * @param candidateToken A candidate PDF token.
   * @return True if the argument is a PDF float, false otherwise.
   * @see COSFloat
   */
  public static boolean isPdfFloat(Object candidateToken) {
    return candidateToken instanceof COSFloat;
  }

  /**
   * <p>Determines if a candidate PDF token at the given index is a PDF integer token.</p>
   * @param tokens A list of tokens.
   * @param index The index of the selected token.
   * @return True if the selected token is a PDF integer, false otherwise.
   * @see #isPdfInteger(Object)
   */
  public static boolean isPdfInteger(List tokens,
                                     int index) {
    return isPdfInteger(tokens.get(index));
  }

  /**
   * <p>Determines if a candidate PDF token is a PDF string integer.</p>
   * @param candidateToken A candidate PDF toekn.
   * @return True if the argument is a PDF integer, false otherwise.
   * @see COSInteger
   */
  public static boolean isPdfInteger(Object candidateToken) {
    return candidateToken instanceof COSInteger;
  }

  /**
   * <p>Determines if a candidate PDF token at the given index is a PDF string token.</p>
   * @param tokens A list of tokens.
   * @param index The index of the selected token.
   * @return True if the selected token is a PDF string, false otherwise.
   * @see #isPdfString(Object)
   */
  public static boolean isPdfString(List tokens,
                                    int index) {
    return isPdfString(tokens.get(index));
  }

  /**
   * <p>Determines if a candidate PDF token is a PDF string token.</p>
   * @param candidateToken A candidate PDF toekn.
   * @return True if the argument is a PDF string, false otherwise.
   * @see COSString
   */
  public static boolean isPdfString(Object candidateToken) {
    return candidateToken instanceof COSString;
  }

  /**
   * <p>Determines if the token at the given index is the "set RGB color for non-stroking operations"
   * PDF operator.</p>
   * @param tokens A list of tokens.
   * @param index  The index of the candidate token.
   * @return True if the selected token is the expected operator, false
   *         otherwise.
   * @see #isSetRgbColorNonStroking(Object)
   */
  public static boolean isSetRgbColorNonStroking(List tokens,
                                                 int index) {
    return isSetRgbColorNonStroking(tokens.get(index));
  }

  /**
   * <p>Determines if a candidate PDF token is the "set RGB color for non-stroking operations"
   * PDF operator.</p>
   * @param candidateToken A candidate PDF token.
   * @return True is the argument is the expected operator, false
   *         otherwise.
   * @see #SET_RGB_COLOR_NONSTROKING
   * @see #isPdfOperator
   */
  public static boolean isSetRgbColorNonStroking(Object candidateToken) {
    return matchPdfOperator(candidateToken,
                            SET_RGB_COLOR_NONSTROKING);
  }

  /**
   * <p>Determines if the token at the given index is the "show text"
   * PDF operator.</p>
   * @param tokens A list of tokens.
   * @param index  The index of the candidate token.
   * @return True if the selected token is the expected operator, false
   *         otherwise.
   * @see #isShowText(Object)
   */
  public static boolean isShowText(List tokens,
                                   int index) {
    return isShowText(tokens.get(index));
  }

  /**
   * <p>Determines if a candidate PDF token is the "show text"
   * PDF operator.</p>
   * @param candidateToken A candidate PDF token.
   * @return True is the argument is the expected operator, false
   *         otherwise.
   * @see #SHOW_TEXT
   * @see #isPdfOperator
   */
  public static boolean isShowText(Object candidateToken) {
    return matchPdfOperator(candidateToken,
                            SHOW_TEXT);
  }

  /**
   * <p>Determines if the token at the given index is a PDF float
   * with the given value.</p>
   * @param tokens A list of tokens.
   * @param index  The index of the selected token.
   * @param num    A value to match the token against.
   * @return True if the selected token is a PDF float and its value
   *         is equal to the given value, false otherwise.
   * @see #matchPdfFloat(Object, float)
   */
  public static boolean matchPdfFloat(List tokens,
                                      int index,
                                      float num) {
    return matchPdfFloat(tokens.get(index),
                         num);
  }

  /**
   * <p>Determines if the given token is a PDF float
   * with the given value.</p>
   * @param tokens A list of tokens.
   * @param index  The index of the selected token.
   * @param num    A value to match the token against.
   * @return True if the argument is a PDF float and its value
   *         is equal to the given value, false otherwise.
   * @see #isPdfFloat(Object)
   * @see #getPdfFloat(Object)
   */
  public static boolean matchPdfFloat(Object candidateToken,
                                      float num) {
    return isPdfFloat(candidateToken) && getPdfFloat(candidateToken) == num;
  }

  /**
   * <p>Determines if the token at the given index is a PDF integer
   * with the given value.</p>
   * @param tokens A list of tokens.
   * @param index  The index of the selected token.
   * @param num    A value to match the token against.
   * @return True if the selected token is a PDF integer and its value
   *         is equal to the given value, false otherwise.
   * @see #matchPdfInteger(Object, int)
   */
  public static boolean matchPdfInteger(List tokens,
                                        int index,
                                        int num) {
    return matchPdfInteger(tokens.get(index),
                           num);
  }

  /**
   * <p>Determines if the given token is a PDF integer
   * with the given value.</p>
   * @param tokens A list of tokens.
   * @param index  The index of the selected token.
   * @param num    A value to match the token against.
   * @return True if the argument is a PDF integer and its value
   *         is equal to the given value, false otherwise.
   * @see #isPdfInteger(Object)
   * @see #getPdfInteger(Object)
   */
  public static boolean matchPdfInteger(Object candidateToken,
                                        int num) {
    return isPdfInteger(candidateToken) && getPdfInteger(candidateToken) == num;
  }

  /**
   * <p>Determines if the token at the given index is a PDF operator,
   * and if so, if it is the expected operator..</p>
   * @param tokens A list of tokens.
   * @param index  The index of the selected token.
   * @param num    A PDF operator string to match the token against.
   * @return True if the selected token is a PDF operator of the expected
   *         type, false otherwise.
   * @see #matchPdfFloat(Object, float)
   */
  public static boolean matchPdfOperator(List tokens,
                                         int index,
                                         String expectedOperator) {
    return matchPdfOperator(tokens.get(index),
                            expectedOperator);
  }

  /**
   * <p>Determines if a PDF token is a PDF operator, if is so,
   * if it is the expected operator.</p>
   * @param candidateToken   A candidate PDF token.
   * @param expectedOperator A PDF operator string to match the token against.
   * @return True if the argument is a PDF operator of the expected
   *         type, false otherwise.
   */
  public static boolean matchPdfOperator(Object candidateToken,
                                         String expectedOperator) {
    return candidateToken instanceof PDFOperator
    && ((PDFOperator)candidateToken).getOperation().equals(expectedOperator);
  }

  /**
   * <p>Determines if the token at the given index is a PDF string
   * and if it equals the given value.</p>
   * @param tokens A list of tokens.
   * @param index  The index of the selected token.
   * @param num    A value to match the token against with {@link String#equals}.
   * @return True if the selected token is a PDF string and its value
   *         is equal to the given value, false otherwise.
   * @see #matchPdfString(Object, float)
   */
  public static boolean matchPdfString(List tokens,
                                       int index,
                                       String str) {
    return matchPdfString(tokens.get(index),
                          str);
  }

  /**
   * <p>Determines if the given token is a PDF string
   * and if it equals the given value.</p>
   * @param tokens A list of tokens.
   * @param index  The index of the selected token.
   * @param num    A value to match the token against with {@link String#equals}.
   * @return True if the argument is a PDF string and its value
   *         is equal to the given value, false otherwise.
   * @see #isPdfString(Object)
   * @see #getPdfString(Object)
   */
  public static boolean matchPdfString(Object candidateToken,
                                       String str) {
    return isPdfString(candidateToken) && getPdfString(candidateToken).equals(str);
  }

  /**
   * <p>Determines if the token at the given index is a PDF string
   * and if it starts with the given value.</p>
   * @param tokens A list of tokens.
   * @param index  The index of the selected token.
   * @param num    A value to match the token against with {@link String#startsWith}.
   * @return True if the selected token is a PDF string and its value
   *         starts with the given value, false otherwise.
   * @see #matchPdfStringStartsWith(Object, float)
   */
  public static boolean matchPdfStringStartsWith(List tokens,
                                                 int index,
                                                 String str) {
    return matchPdfStringStartsWith(tokens.get(index),
                                    str);
  }

  /**
   * <p>Determines if the given token is a PDF string
   * and if it starts with the given value.</p>
   * @param tokens A list of tokens.
   * @param index  The index of the selected token.
   * @param num    A value to match the token against with {@link String#startsWith}.
   * @return True if the argument is a PDF string and its value
   *         starts with the given value, false otherwise.
   * @see #isPdfString(Object)
   * @see #getPdfString(Object)
   */
  public static boolean matchPdfStringStartsWith(Object candidateToken,
                                                 String str) {
    return isPdfString(candidateToken) && getPdfString(candidateToken).startsWith(str);
  }

  public static boolean matchSetRgbColorNonStroking(List tokens,
                                                    int index,
                                                    int red,
                                                    int green,
                                                    int blue) {
    return isSetRgbColorNonStroking(tokens, index)
    && matchPdfInteger(tokens, index - 3, red)
    && matchPdfInteger(tokens, index - 2, green)
    && matchPdfInteger(tokens, index - 1, blue);
  }

  public static boolean matchShowText(List tokens,
                                      int index) {
    return isShowText(tokens, index)
    && isPdfString(tokens, index - 1);
  }

  public static boolean matchShowText(List tokens,
                                      int index,
                                      String str) {
    return isShowText(tokens, index)
    && matchPdfString(tokens, index - 1, str);
  }

  public static boolean matchTextObject(List tokens,
                                        int begin,
                                        int end) {
    return isBeginTextObject(tokens, begin)
    && isEndTextObject(tokens, end);
  }

}
