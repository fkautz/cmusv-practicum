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
import java.util.ArrayList;

import org.lockss.filter.pdf.MockTransforms.RememberDocumentTransform;
import org.lockss.test.*;
import org.lockss.util.PdfDocument;
import org.lockss.util.PdfUtil.IdentityDocumentTransform;

public class TestConditionalDocumentTransform extends LockssTestCase {

  public void testConditionFalse() throws Exception {
    DocumentTransform transform = new DocumentTransform() {
      public boolean transform(PdfDocument pdfDocument) throws IOException {
        fail("Should not have been called");
        return false;
      }
    };
    ConditionalDocumentTransform conditional = new ConditionalDocumentTransform(new IdentityDocumentTransform(false),
                                                                                transform);
    assertFalse(conditional.transform(new MockPdfDocument()));
    // Did not throw: all is well
  }

  public void testConditionTrue() throws Exception {
    RememberDocumentTransform transform = new RememberDocumentTransform(new ArrayList());
    ConditionalDocumentTransform conditional = new ConditionalDocumentTransform(new IdentityDocumentTransform(true),
                                                                                transform);
    assertTrue(conditional.transform(new MockPdfDocument()));
    assertEquals(1, transform.getCallCount());
  }

  public void testConditionTrueThenFails() throws Exception {
    assertFalse(new ConditionalDocumentTransform(new IdentityDocumentTransform(true),
                                                 new IdentityDocumentTransform(false)).transform(new MockPdfDocument()));
  }
  
}
