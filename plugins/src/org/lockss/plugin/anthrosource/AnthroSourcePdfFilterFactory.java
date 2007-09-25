/*
 * $Id$
 */

/*

Copyright (c) 2000-2007 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.plugin.anthrosource;

import java.io.*;
import java.util.*;

import org.lockss.daemon.PluginException;
import org.lockss.filter.pdf.*;
import org.lockss.plugin.*;
import org.lockss.util.*;
import org.pdfbox.util.PDFOperator;

public class AnthroSourcePdfFilterFactory
    extends SimpleOutputDocumentTransform
    implements FilterFactory {

  public static class RewriteStream extends PageStreamTransform {

    public static class AlwaysChanged extends SimpleOperatorProcessor {
      @Override
      public void process(PageStreamTransform pageStreamTransform, PDFOperator operator, List operands) throws IOException {
        pageStreamTransform.signalChange();
        super.process(pageStreamTransform, operator, operands);
      }

    }

    public RewriteStream() throws IOException {
      super(PageStreamTransform.rewriteProperties(new Properties(),
                                                  AlwaysChanged.class.getName()));
    }

  }

  public AnthroSourcePdfFilterFactory() throws IOException {
    super(new TransformEachPage(new RewriteStream()));
  }

  public InputStream createFilteredInputStream(ArchivalUnit au,
                                               InputStream in,
                                               String encoding)
      throws PluginException {
    return PdfUtil.applyFromInputStream(this, in);
  }

}
