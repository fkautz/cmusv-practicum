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

package org.lockss.filter.pdf;

import java.io.IOException;

import org.lockss.filter.pdf.PageTransformUtil.ExtractStringsToOutputStream;

/**
 * <p>A version of {@link OutputStreamDocumentTransform} that first
 * applies a document transform to the PDF document being processed,
 * then collects all string constants in the resulting PDF document
 * into the output stream.</p>
 * @author Thib Guicherd-Callin.
 * @see ExtractStringsToOutputStream
 */
public abstract class TextScrapingDocumentTransform extends OutputStreamDocumentTransform {

  /**
   * <p>Makes a new document transform which will be applied before
   * scraping all string constants from the document with
   * {@link ExtractStringsToOutputStream}.</p>
   * @return A preliminary document transform.
   * @throws IOException if any processing error occurs.
   */
  public abstract DocumentTransform makePreliminaryTransform() throws IOException;

  /* Inherit documentation */
  public DocumentTransform makeTransform() throws IOException {
    return new ConditionalDocumentTransform(makePreliminaryTransform(),
                                            new TransformEachPage(new ExtractStringsToOutputStream(outputStream)));
  }

}
