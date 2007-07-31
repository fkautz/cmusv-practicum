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

package org.lockss.util;

import java.io.*;
import java.util.*;

import org.pdfbox.cos.*;
import org.pdfbox.exceptions.*;
import org.pdfbox.pdfparser.PDFParser;
import org.pdfbox.pdmodel.*;
import org.pdfbox.pdmodel.common.*;
import org.pdfbox.pdmodel.fdf.FDFDocument;

/**
 * <p>Convenience class to provide easy access to the internals of
 * a PDF document.</p>
 * <p>The PDFBox API provides several levels of access to a PDF
 * document via different class hierarchies ({@link COSDocument},
 * {@link PDDocument}, {@link FDFDocument}). The only unified view of
 * a PDF document is through {@link PDFParser}, but unintuitively
 * it is a parser more than a document object. This class exposes an
 * API more related to the PDF document under the parser.</p>
 * @author Thib Guicherd-Callin
 */
public class PdfDocument {

  /**
   * <p>The underlying PDF parser.</p>
   */
  private PDFParser pdfParser;

  /**
   * <p>Builds a new PDF document (actually a new PDF parser).</p>
   * <p><em>You must call {@link #close()} to release the expensive
   * resources associated with this object, when it is no longer
   * needed but before it is finalized by the runtime system.</em></p>
   * @param inputStream The input stream that contains the PDF document.
   * @throws IOException if any processing error occurs.
   * @see PDFParser#PDFParser(InputStream)
   * @see PDFParser#parse
   */
  public PdfDocument(InputStream inputStream) throws IOException {
    this.pdfParser = new PDFParser(inputStream);
    parse();
  }

  protected PdfDocument() { }

  /**
   * <p>Closes the underlying {@link COSDocument} instance
   * and releases expensive memory resources held by this object.</p>
   * <p>This method does not throw {@link IOException} in case of
   * failure; use the return value to determine success. However,
   * calling this method does cause this instance to become
   * unusable; any subsequent method calls will likely yield a
   * {@link NullPointerException}.</p>
   * @return True if closing the PDF document succeeded, false
   *         otherwise.
   * @see PDDocument#close
   */
  public boolean close() {
    try {
      getPdDocument().close();
      return true;
    }
    catch (IOException ioe) {
      logger.error("Error while closing a PDF document", ioe);
      return false;
    }
    finally {
      pdfParser = null;
    }
  }

  /**
   * <p>Gets the author from the document information.</p>
   * @return The author of the document (null if not set).
   * @throws IOException if any processing error occurs.
   * @see PDDocumentInformation#getAuthor
   */
  public String getAuthor() throws IOException {
    return getDocumentInformation().getAuthor();
  }

  /**
   * <p>Provides access to the underlying {@link COSDocument}
   * instance; <em>use with care.</em></p>
   * @return The underlying {@link COSDocument} instance, pulled from
   *         the underlying {@link PDFParser} instance.
   * @throws IOException if any processing error occurs.
   * @see PDFParser#getDocument
   */
  public COSDocument getCosDocument() throws IOException {
    return getPdfParser().getDocument();
  }

  /**
   * <p>Gets the creation date from the document information.</p>
   * @return The creation date of the document (null if not set).
   * @throws IOException if any processing error occurs.
   * @see PDDocumentInformation#getCreationDate
   */
  public Calendar getCreationDate() throws IOException {
    return getDocumentInformation().getCreationDate();
  }

  /**
   * <p>Gets the creator from the document information.</p>
   * @return The creator of the document (null if not set).
   * @throws IOException if any processing error occurs.
   * @see PDDocumentInformation#getCreator
   */
  public String getCreator() throws IOException {
    return getDocumentInformation().getCreator();
  }

  /**
   * <p>Gets the keywords from the document information.</p>
   * @return The keywords of the document (null if not set).
   * @throws IOException if any processing error occurs.
   * @see PDDocumentInformation#getKeywords
   */
  public String getKeywords() throws IOException {
    return getDocumentInformation().getKeywords();
  }

  public String getMetadataAsString() throws IOException {
    PDMetadata metadata = getMetadata();
    return metadata == null ? null : metadata.getInputStreamAsString();
  }

  /**
   * <p>Gets the modification date from the document information.</p>
   * @return The modification date of the document (null if not set).
   * @throws IOException if any processing error occurs.
   * @see PDDocumentInformation#getModificationDate
   */
  public Calendar getModificationDate() throws IOException {
    return getDocumentInformation().getModificationDate();
  }

  /**
   * <p>Determines the number of pages of this PDF document.</p>
   * @return The total number of pages.
   * @throws IOException if any processing error occurs.
   * @see PDDocument#getNumberOfPages
   */
  public int getNumberOfPages() throws IOException {
    return getPdDocument().getNumberOfPages();
  }

  public PdfPage getPage(int index) throws IOException {
    return new PdfPage(this, getPdPage(index));
  }

  public ListIterator /* of PdfPage */ getPageIterator() throws IOException {
    List pdfPages = new ArrayList();
    for (Iterator iter = getPdPageIterator() ; iter.hasNext() ; ) {
      pdfPages.add(new PdfPage(this, (PDPage)iter.next()));
    }
    return pdfPages.listIterator();
  }

  /**
   * <p>Provides access to the underlying {@link PDDocument}
   * instance; <em>use with care.</em></p>
   * @return The underlying {@link PDDocument} instance, pulled from
   *         the underlying {@link PDFParser} instance.
   * @throws IOException if any processing error occurs.
   * @see PDFParser#getPDDocument
   */
  public PDDocument getPdDocument() throws IOException {
    return getPdfParser().getPDDocument();
  }

  /**
   * <p>Provides access to the underlying {@link PDFParser}
   * instance; <em>use with care.</em></p>
   * @return The underlying {@link PDFParser} instance.
   */
  public PDFParser getPdfParser() {
    return pdfParser;
  }

  public PDPage getPdPage(int index) throws IOException {
    return (PDPage)getPdPages().get(index);
  }

  public ListIterator /* of PDPage */ getPdPageIterator() throws IOException {
    return getPdPages().listIterator();
  }

  /**
   * <p>Gets the producer from the document information.</p>
   * @return The producer of the document (null if not set).
   * @throws IOException if any processing error occurs.
   * @see PDDocumentInformation#getProducer
   */
  public String getProducer() throws IOException {
    return getDocumentInformation().getProducer();
  }

  /**
   * <p>Gets the subject from the document information.</p>
   * @return The subject of the document (null if not set).
   * @throws IOException if any processing error occurs.
   * @see PDDocumentInformation#getSubject
   */
  public String getSubject() throws IOException {
    return getDocumentInformation().getSubject();
  }

  /**
   * <p>Gets the title from the document information.</p>
   * @return The title of the document (null if not set).
   * @throws IOException if any processing error occurs.
   * @see PDDocumentInformation#getTitle
   */
  public String getTitle() throws IOException {
    return getDocumentInformation().getTitle();
  }

  public COSDictionary getTrailer() throws IOException {
    return getCosDocument().getTrailer();
  }

  /**
   * <p>Instantiates a new {@link PDStream} instance based on this PDF
   * document.</p>
   * @return A newly instantiated {@link PDStream} instance.
   * @throws IOException if any processing error occurs.
   * @see PDStream#PDStream(PDDocument)
   */
  public PDStream makePdStream() throws IOException {
    return new PDStream(getPdDocument());
  }

  /**
   * <p>Unsets the author from the document information.</p>
   * @throws IOException if any processing error occurs.
   * @see #setAuthor
   */
  public void removeAuthor() throws IOException {
    setAuthor(null);
  }

  /**
   * <p>Unsets the creation date from the document information.</p>
   * @throws IOException if any processing error occurs.
   * @see #setCreationDate
   */
  public void removeCreationDate() throws IOException {
    setCreationDate(null);
  }

  /**
   * <p>Unsets the creator from the document information.</p>
   * @throws IOException if any processing error occurs.
   * @see #setCreator
   */
  public void removeCreator() throws IOException {
    setCreator(null);
  }

  /**
   * <p>Unsets the keywords from the document information.</p>
   * @throws IOException if any processing error occurs.
   * @see #setKeywords
   */
  public void removeKeywords() throws IOException {
    setKeywords(null);
  }

  /**
   * <p>Unsets the modification date from the document information.</p>
   * @throws IOException if any processing error occurs.
   * @see #setModificationDate
   */
  public void removeModificationDate() throws IOException {
    setModificationDate(null);
  }

  public void removePage(int index) throws IOException {
    getPdDocument().removePage(index);
  }

  public void removePage(PdfPage pdfPage) throws IOException {
    removePage(pdfPage.getPdPage());
  }

  /**
   * <p>Unsets the producer from the document information.</p>
   * @throws IOException if any processing error occurs.
   * @see #setProducer
   */
  public void removeProducer() throws IOException {
    setProducer(null);
  }

  /**
   * <p>Unsets the subject from the document information.</p>
   * @throws IOException if any processing error occurs.
   * @see #setSubject
   */
  public void removeSubject() throws IOException {
    setSubject(null);
  }

  /**
   * <p>Unsets the title from the document information.</p>
   * @throws IOException if any processing error occurs.
   * @see #setTitle
   */
  public void removeTitle() throws IOException {
    setTitle(null);
  }

  /**
   * <p>This will save the underlying {@link PDDocument} instance to
   * an output stream.</p>
   * @param outputStream An output stream into which this document
   *                     will be saved.
   * @throws IOException if any processing error occurs.
   */
  public void save(OutputStream outputStream) throws IOException {
    try {
      getPdDocument().save(outputStream);
    }
    catch (COSVisitorException cve) {
      IOException ioe = new IOException();
      ioe.initCause(cve);
      throw ioe;
    }
  }

  /**
   * <p>Sets the author in the document information.</p>
   * @param author The new author of the document (null to unset).
   * @throws IOException if any processing error occurs.
   * @see PDDocumentInformation#setAuthor
   */
  public void setAuthor(String author) throws IOException {
    getDocumentInformation().setAuthor(author);
  }

  /**
   * <p>Sets the creation date in the document information.</p>
   * @param date The new creation date of the document (null to unset).
   * @throws IOException if any processing error occurs.
   * @see PDDocumentInformation#setCreationDate
   */
  public void setCreationDate(Calendar date) throws IOException {
    getDocumentInformation().setCreationDate(date);
  }

  /**
   * <p>Sets the creator in the document information.</p>
   * @param creator The new creator of the document (null to unset).
   * @throws IOException if any processing error occurs.
   * @see PDDocumentInformation#setCreator
   */
  public void setCreator(String creator) throws IOException {
    getDocumentInformation().setCreator(creator);
  }

  /**
   * <p>Sets the keywords in the document information.</p>
   * @param keywords The new keywords of the document (null to unset).
   * @throws IOException if any processing error occurs.
   * @see PDDocumentInformation#setKeywords
   */
  public void setKeywords(String keywords) throws IOException {
    getDocumentInformation().setKeywords(keywords);
  }

  public void setMetadata(String metadataAsString) throws IOException {
    ByteArrayInputStream inputStream = new ByteArrayInputStream(metadataAsString.getBytes());
    PDMetadata pdMetadata = new PDMetadata(getPdDocument(), inputStream, false);
    setMetadata(pdMetadata);
  }

  /**
   * <p>Sets the modification date in the document information.</p>
   * @param date The new modification date of the document (null to unset).
   * @throws IOException if any processing error occurs.
   * @see PDDocumentInformation#setModificationDate
   */
  public void setModificationDate(Calendar date) throws IOException {
    getDocumentInformation().setModificationDate(date);
  }

  /**
   * <p>Sets the producer in the document information.</p>
   * @param producer The new producer of the document (null to unset).
   * @throws IOException if any processing error occurs.
   * @see PDDocumentInformation#setProducer
   */
  public void setProducer(String producer) throws IOException {
    getDocumentInformation().setProducer(producer);
  }

  /**
   * <p>Sets the subject in the document information.</p>
   * @param subject The new subject of the document (null to unset).
   * @throws IOException if any processing error occurs.
   * @see PDDocumentInformation#setSubject
   */
  public void setSubject(String subject) throws IOException {
    getDocumentInformation().setSubject(subject);
  }

  /**
   * <p>Sets the title in the document information.</p>
   * @param title The new title of the document (null to unset).
   * @throws IOException if any processing error occurs.
   * @see PDDocumentInformation#setTitle
   */
  public void setTitle(String title) throws IOException {
    getDocumentInformation().setTitle(title);
  }

  protected PDDocumentCatalog getDocumentCatalog() throws IOException {
    return getPdDocument().getDocumentCatalog();
  }

  protected PDDocumentInformation getDocumentInformation() throws IOException {
    return getPdDocument().getDocumentInformation();
  }

  protected PDMetadata getMetadata() throws IOException {
    return getDocumentCatalog().getMetadata();
  }

  protected List getPdPages() throws IOException {
    return getDocumentCatalog().getAllPages();
  }

  protected void parse() throws IOException {
    // Parse the document before using it
    getPdfParser().parse();

    // Trivial decryption if encrypted without a password
    if (getPdDocument().isEncrypted()) {
      try {
        getPdDocument().decrypt("");
      }
      catch (CryptographyException ce) {
        IOException ioe = new IOException();
        ioe.initCause(ce);
        throw ioe;
      }
      catch (InvalidPasswordException ipe) {
        IOException ioe = new IOException();
        ioe.initCause(ipe);
        throw ioe;
      }
    }
  }

  protected void removePage(PDPage pdPage) throws IOException {
    getPdDocument().removePage(pdPage);
  }

  protected void setMetadata(PDMetadata metadata) throws IOException {
    getDocumentCatalog().setMetadata(metadata);
  }

  private static Logger logger = Logger.getLogger("PdfDocument");

  /**
   * <p>Closes the underlying {@link COSDocument} instance
   * and releases expensive memory resources held by the given
   * object (which can be null).</p>
   * <p>This method does not throw {@link IOException} in case of
   * failure; use the return value to determine success. However,
   * calling this method does cause the given instance to become
   * unusable; any subsequent method calls will likely yield a
   * {@link NullPointerException}.</p>
   * @param pdfDocument a PDF document instance; can be null.
   * @return True if the PDF document is null or if closing it
   *         succeeded, false otherwise.
   * @see #close()
   */
  public static boolean close(PdfDocument pdfDocument) {
    if (pdfDocument == null) {
      return true;
    }
    else {
      return pdfDocument.close();
    }
  }

}
