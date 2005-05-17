/*
 * $Id$
 */

/*

Copyright (c) 2000-2003 Board of Trustees of Leland Stanford Jr. University,
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

/**
 * This is a class to contain generic stream utilities
 *
 * @author  Emil Aalto
 * @version 0.0
 */

public class StreamUtil {

  private static final int BUFFER_SIZE = 256;

  /**
   * This function copies the contents of in InputStream to an Outputstream.
   * It buffers the copying, and closes neither.
   * @param is input
   * @param os output
   * @return number of bytes copied
   * @throws IOException
   */
  public static long copy(InputStream is, OutputStream os) throws IOException {
    if (is == null || os == null) {
      return 0;
    }
    long totalByteCount = 0;
    byte[] bytes = new byte[BUFFER_SIZE];
    int byteCount;
    while ((byteCount = is.read(bytes)) > 0) {
      totalByteCount += byteCount;
      os.write(bytes, 0, byteCount);
    }
    os.flush();
    return totalByteCount;
  }

  /**
   * This function copies the contents of a Reader to a Writer
   * It buffers the copying, and closes neither.
   * @param reader reader
   * @param writer writer
   * @return number of charscopied
   * @throws IOException
   */
  public static long copy(Reader reader, Writer writer) throws IOException {
    if (reader == null || writer == null) {
      return 0;
    }
    long totalCharCount = 0;
    char[] chars = new char[BUFFER_SIZE];
    int count;
    while ((count = reader.read(chars)) > 0) {
      totalCharCount += count;
      writer.write(chars, 0, count);
    }
    writer.flush();
    return totalCharCount;
  }

  /** Read size bytes from stream into buf.  Keeps trying to read until
   * enough bytes have been read or EOF or error.
   * @param ins stream to read from
   * @param buf buffer to read into
   * @param size number of bytes to read
   * @return number of bytes read, which will be less than size iff EOF is
   * reached
   * @throws IOException
   */
  public static int readBytes(InputStream ins, byte[] buf, int size)
      throws IOException {
    int off = 0;
    while ( off < size) {
      int nread = ins.read(buf, off, size - off);
      if (nread == -1) {
	return off;
      }
      off += nread;
    }
    return off;
  }

  /** Read size chars from reader into buf.  Keeps trying to read until
   * enough chars have been read or EOF or error.
   * @param reader reader to read from 
   * @param buf buffer to read into
   * @param size number of chars to read
   * @return number of chars read, which will be less than size iff EOF is
   * reached
   * @throws IOException
   */
  public static int readChars(Reader reader, char[] buf, int size)
      throws IOException {
    int off = 0;
    while (off < size) {
      int nread = reader.read(buf, off, size - off);
      if (nread == -1) {
	return off;
      }
      off += nread;
    }
    return off;
  }

  /** Read from two input streams and compare their contents.  The streams
   * are not closed, and may get left at any position.
   * @param ins1 1st stream
   * @param ins2 2nd stream
   * @return true iff streams have same contents and reach EOF at the same
   * point.
   * @throws IOException
   */
  public static boolean compare(InputStream ins1, InputStream ins2)
      throws IOException {
    byte[] b1 = new byte[BUFFER_SIZE];
    byte[] b2 = new byte[BUFFER_SIZE];
    while (true) {
      int len1 = readBytes(ins1, b1, BUFFER_SIZE);
      int len2 = readBytes(ins2, b2, BUFFER_SIZE);
      if (len1 != len2) return false;
      if (len1 == 0) return true;
      for (int ix = 0; ix < len1; ix++) {
	if (b1[ix] != b2[ix]) return false;
      }
    }
  }

}

