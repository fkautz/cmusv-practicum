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
import org.lockss.test.*;

public class TestReaderInputStream extends LockssTestCase {

  public void testEmptyReaderYieldsEmptyStream() throws IOException {
    Reader reader = new StringReader("");
    InputStream is = new ReaderInputStream(reader);
    assertEquals(-1, is.read());
  }

  public void testBytifiedString() throws IOException {
    String testStr = "Test string";
    byte bytes[] = testStr.getBytes();
    Reader reader = new StringReader(testStr);
    InputStream is = new ReaderInputStream(reader);
    for (int ix = 0; ix < bytes.length; ix++) {
      assertEquals(bytes[ix], is.read());
    }
    assertEquals(-1, is.read());
  }

  public void testReadIntoArray() throws IOException {
    String testStr = "Test string";
    byte expected[] = testStr.getBytes();
    Reader reader = new StringReader(testStr);
    InputStream is = new ReaderInputStream(reader);
    byte actual[] = new byte[expected.length];
    assertEquals(expected.length, is.read(actual));
    assertEquals(expected, actual);
    assertEquals(-1, is.read());
  }

  public void testReadIntoArrayWithOffset() throws IOException {
    String testStr = "Test string";
    byte expected[] = testStr.getBytes();
    Reader reader = new StringReader(testStr);
    InputStream is = new ReaderInputStream(reader);
    byte actual[] = new byte[expected.length];
    assertEquals(5, is.read(actual, 2, 5));
    for (int ix = 0; ix < 5; ix++) {
      assertEquals(expected[ix], actual[ix+2]);
    }
    assertEquals(expected[5], is.read());
  }
}
