/*
 * $Id$
 */

/*

Copyright (c) 2002 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.hasher;

import java.io.*;
import java.security.*;
import java.util.*;
import org.lockss.test.*;
import org.lockss.util.*;
import org.lockss.daemon.*;
import org.lockss.plugin.*;

public class TestGenericNameHasher extends LockssTestCase {
  private static final byte CUSN_NO_CONTENT=0;
  private static final byte CUSN_CONTENT_LEAF=1;
  private static final byte CUSN_CONTENT_NOT_LEAF=2;

  public TestGenericNameHasher(String msg) {
    super(msg);
  }

  public void testNullMessageDigest() throws IOException {
    CachedUrlSet cus = new MockCachedUrlSet();
    try {
      CachedUrlSetHasher hasher = new GenericNameHasher(cus, null);
      fail("Creating a GenericNameHasher with a null digest should throw "+
	   "an IllegalArgumentException");
    } catch (IllegalArgumentException iae) {
    }
  }

  public void testNullCachedUrlSet() throws IOException {
    MessageDigest dig = new MockMessageDigest();
    try {
      CachedUrlSetHasher hasher = new GenericNameHasher(null, dig);
      fail("Creating a GenericNameHasher with a null cus should throw "+
	   "an IllegalArgumentException");
    } catch (IllegalArgumentException iae) {
    }
  }

  public void testNullIterator() {
    MockCachedUrlSet cus = new MockCachedUrlSet();
    cus.setFlatIterator(null);
    MessageDigest dig = new MockMessageDigest();
    try {
      CachedUrlSetHasher hasher = new GenericNameHasher(cus, dig);
      fail("Creating a GenericNameHasher with a null iterator should "+
 	   "throw an IllegalArgumentException");
    } catch (IllegalArgumentException iae) {
    }
  }

  public void testUnfinishedHasherNotFinished() {
    String name = "TestName1";
    MockMessageDigest dig = new MockMessageDigest();
    MockCachedUrlSet cus = new MockCachedUrlSet(name);
    cus.setHasContent(true);
    cus.setIsLeaf(false);
    MockCachedUrlSet root = new MockCachedUrlSet();
    root.setFlatItSource(ListUtil.list(cus));

    CachedUrlSetHasher hasher = new GenericNameHasher(root, dig);

    assertFalse(hasher.finished());
  }

  public void testHashOneName() throws IOException {
    String name = "TestName1";
    MockMessageDigest dig = new MockMessageDigest();

    MockCachedUrlSet cus = new MockCachedUrlSet(name);
    cus.setHasContent(true);
    cus.setIsLeaf(false);
    MockCachedUrlSet root = new MockCachedUrlSet();
    root.setFlatItSource(ListUtil.list(cus));

    CachedUrlSetHasher hasher = new GenericNameHasher(root, dig);

    byte[] bytes = getExpectedCUSBytes(root);
    int totalHashed = 0;
    while (!hasher.finished()) {
      totalHashed += hasher.hashStep(1);
    }
    assertEquals(bytes.length, totalHashed);
    assertEquals(0, hasher.hashStep(1));
    assertTrue(hasher.finished());
    for (int ix=0; ix<bytes.length; ix++) {
      assertEquals(bytes[ix], dig.getUpdatedByte());
    }
  }

   public void testComplexHashSmallStep() throws IOException {
     MockMessageDigest dig = new MockMessageDigest();
     CachedUrlSet cus = makeTestCUS();
     CachedUrlSetHasher hasher = new GenericNameHasher(cus, dig);
     byte[] expectedBytes = getExpectedCUSBytes(cus);

     int totalHashed = 0;
     while (!hasher.finished()) {
       totalHashed += hasher.hashStep(1);
     }
     assertEquals(expectedBytes.length, totalHashed);
     for (int ix=0; ix<expectedBytes.length; ix++) {
       assertEquals(expectedBytes[ix], dig.getUpdatedByte());
     }
   }

   public void testComplexHashLargerStep() throws IOException {
     MockMessageDigest dig = new MockMessageDigest();
     CachedUrlSet cus = makeTestCUS();
     CachedUrlSetHasher hasher = new GenericNameHasher(cus, dig);
     byte[] expectedBytes = getExpectedCUSBytes(cus);

     int totalHashed = 0;
     while (!hasher.finished()) {
       totalHashed += hasher.hashStep(20);
     }
     assertEquals(expectedBytes.length, totalHashed);
     for (int ix=0; ix<expectedBytes.length; ix++) {
       assertEquals(expectedBytes[ix], dig.getUpdatedByte());
     }
   }

   public void testComplexHashVeryLargeStep() throws IOException {
     MockMessageDigest dig = new MockMessageDigest();
     CachedUrlSet cus = makeTestCUS();
     CachedUrlSetHasher hasher = new GenericNameHasher(cus, dig);
     byte[] expectedBytes = getExpectedCUSBytes(cus);

     int totalHashed = 0;
     while (!hasher.finished()) {
       totalHashed += hasher.hashStep(10000);
     }
     assertEquals(expectedBytes.length, totalHashed);
     for (int ix=0; ix<expectedBytes.length; ix++) {
       assertEquals(expectedBytes[ix], dig.getUpdatedByte());
     }
   }

  private CachedUrlSet makeTestCUS() {
    List list = new LinkedList();
    MockCachedUrlSet cus = new MockCachedUrlSet("TestName1");
    cus.setHasContent(true);
    cus.setIsLeaf(false);
    list.add(cus);

    cus = new MockCachedUrlSet("AnotherTestName");
    cus.setHasContent(true);
    cus.setIsLeaf(true);
    list.add(cus);

    cus = new MockCachedUrlSet("StillAnotherTestName");
    cus.setHasContent(false);
    cus.setIsLeaf(false);
    list.add(cus);

    MockCachedUrlSet root = new MockCachedUrlSet();
    root.setFlatItSource(list);
    return root;
  }

  private byte[] getExpectedCUSBytes(CachedUrlSet cus) throws IOException {
    Iterator it = cus.flatSetIterator();
    List byteArrays = new LinkedList();
    int totalSize = 0;
    while (it.hasNext()) {
      CachedUrlSetNode cusn = (CachedUrlSetNode) it.next();
      byte[] arr = getExpectedCUSNBytes(cusn);
      totalSize += arr.length;
      byteArrays.add(arr);
    }
    byte[] returnArr = new byte[totalSize];
    int pos = 0;
    it = byteArrays.iterator();
    while (it.hasNext()) {
      byte[] curArr = (byte[]) it.next();
      for (int ix=0; ix<curArr.length; ix++) {
	returnArr[pos++] = curArr[ix];
      }
    }
    return returnArr;
  }

  private byte[] getExpectedCUSNBytes(CachedUrlSetNode cusn) {
    String name = cusn.getUrl();
    byte[] sizeBytes = ByteArray.encodeLong(name.length());
    byte[] returnArray = new byte[name.length() + sizeBytes.length + 2];

    if (cusn.hasContent()) {
      returnArray[0] =
	cusn.isLeaf() ? CUSN_CONTENT_LEAF : CUSN_CONTENT_NOT_LEAF;
    } else {
      returnArray[0] = CUSN_NO_CONTENT;
    }
    returnArray[1] = (byte)sizeBytes.length;
    int curPos = 2;
    for (int ix=0; ix<sizeBytes.length; ix++) {
      returnArray[curPos++] = sizeBytes[ix];
    }

    byte[] nameBytes = name.getBytes();
    for (int ix=0; ix<nameBytes.length; ix++) {
      returnArray[curPos++] = nameBytes[ix];
    }
    return returnArray;
  }
}



