/*
 * $Id$
 */ 

/*

Copyright (c) 2000-20010 Board of Trustees of Leland Stanford Jr. University,
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
package org.lockss.plugin.ingenta;

import java.io.IOException;
import java.io.OutputStream;

import org.lockss.util.PdfDocument;
import org.pdfbox.cos.COSArray;
import org.pdfbox.cos.COSDictionary;
import org.pdfbox.cos.COSName;
import org.pdfbox.cos.COSString;


/**
 * Utilities for Ingenta-specific PDF processing and filtering.
 * 
 * @author Philip Gust
 */
public class IngentaPdfUtil {
  
  /**
   * This transform removes the creation and modification dates,
   * and any auto-generated variable ID from the input PdfDocument.
   * 
   * @param pdfDocument the PdfDocument
   * @param outputStream the output stream
   * @return <code>true</code> if the pdfDocument has a trailer
   */
  static boolean simpleTransform(PdfDocument pdfDocument,
                                 OutputStream outputStream)
    throws IOException {
    
    IngentaPdfFilterFactory.logger.debug2("iText");
    boolean ret = false;
    
    // Remove autogenerated creation and modification dates
    pdfDocument.removeCreationDate();
    pdfDocument.removeModificationDate();
    
    COSDictionary trailer = pdfDocument.getTrailer();
    if (trailer != null) {
      // Put bogus ID to prevent autogenerated (variable) ID
      COSArray id = new COSArray();
      id.add(new COSString("12345678901234567890123456789012"));
      id.add(id.get(0));
      trailer.setItem(COSName.getPDFName("ID"), id);
      ret = true;
    }

    pdfDocument.save(outputStream);
    return ret;
  }

}