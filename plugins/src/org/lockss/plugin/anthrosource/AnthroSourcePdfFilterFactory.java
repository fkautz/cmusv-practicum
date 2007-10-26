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
import org.pdfbox.cos.*;
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

  public static class NormalizeTrailerId implements DocumentTransform {

    public boolean transform(PdfDocument pdfDocument) throws IOException {
      COSDictionary trailer = pdfDocument.getTrailer();
      if (trailer != null) {
        // Put bogus ID to prevent autogenerated (variable) ID
        COSArray id = new COSArray();
        id.add(new COSString("12345678901234567890123456789012"));
        id.add(id.get(0));
        trailer.setItem(COSName.getPDFName("ID"), id);
        return true; // success
      }
      return false; // all other cases are unexpected
    }

  }

  public AnthroSourcePdfFilterFactory() throws IOException {
    super(new AggregateDocumentTransform(new TransformEachPage(new RewriteStream()),
                                         new NormalizeTrailerId()));
  }

  public InputStream createFilteredInputStream(ArchivalUnit au,
                                               InputStream in,
                                               String encoding)
      throws PluginException {
    logger.debug2("PDF filter factory for: " + au.getName());
    OutputDocumentTransform documentTransform = null;
    try {
      documentTransform =
        (OutputDocumentTransform)au.getPlugin().newAuxClass(getClass().getName(),
                                                            OutputDocumentTransform.class);
      logger.debug2("Successfully loaded and instantiated " + documentTransform.getClass().getName());
    }
    catch (PluginException.InvalidDefinition id) {
      logger.error("Can't load PDF transform; unfiltered", id);
      return in;
    }
    catch (RuntimeException rte) {
      logger.error("Can't load PDF transform; unfiltered", rte);
      return in;
    }

    if (documentTransform == null) {
      logger.debug2("Unfiltered");
      return in;
    }
    else {
      logger.debug2("Filtered with " + documentTransform.getClass().getName());
      return PdfUtil.applyFromInputStream(documentTransform, in);
    }
  }

  private static Logger logger = Logger.getLogger("AnthroSourcePdfFilterFactory");

}
