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
import java.util.*;
import org.lockss.util.*;
import org.lockss.test.*;

/**
 * Test class for org.lockss.util.CatalogueOrderComparator
 */
public class TestCatalogueOrderComparator extends LockssTestCase {
  CatalogueOrderComparator coc;

  public void setUp() {
    coc = new CatalogueOrderComparator();
  }

  public void testDeleteInitial() {
    assertSame("foo", coc.deleteInitial("foo", "bar"));
    assertEquals("foo", coc.deleteInitial("bar foo", "bar"));
    assertEquals("foo", coc.deleteInitial("bar  foo", "bar"));
    assertEquals("barfoo", coc.deleteInitial("barfoo", "bar"));
  }

  public void testDeleteAll() {
    assertSame("foo", coc.deleteAll("foo", "bar"));
    assertEquals("foo", coc.deleteAll("f.o,o..", ".,"));
    assertEquals("foo", coc.deleteAll("fo--o", "--!"));
  }

  public void testXlate() {
    assertEquals("Tao of Pooh", coc.xlate("The Tao of Pooh"));
    assertEquals("Tao of Pooh", coc.xlate("A  Tao of Pooh"));
    assertEquals("Tao of Pooh", coc.xlate("An Tao of Pooh"));
    assertEquals("IBM Tech Journal", coc.xlate("I.B.M. Tech Journal"));
  }

  public void testOrder() {
    // titles in sorted order
    String[] titles = {
      "The Aardvark of the Baskervilles", 
      "An Apple and its Eve",
      "a boy and his bog",
      "A Boy and his Dog",
      "IBM Tech Journak",
      "I.B.M. Tech. Journal",
      "IBM Tech Journam",
    };
    List tl = ListUtil.fromArray(titles);
    Collections.reverse(tl);
    assertFalse(CollectionUtil.isIsomorphic(titles, tl));
    assertIsomorphic(titles, sort(tl));
    Collections.shuffle(tl);
    assertIsomorphic(titles, sort(tl));
  }

  List sort(List l) {
    Collections.sort(l, new CatalogueOrderComparator());
    return l;
  }

}
