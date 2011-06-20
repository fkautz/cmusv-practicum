/*
 * $Id: TestMapUtil.java,v 1.2 2011/06/20 07:06:34 tlipkis Exp $
 */

/*

Copyright (c) 2000-2011 Board of Trustees of Leland Stanford Jr. University,
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

import java.util.*;
import java.net.*;
import junit.framework.TestCase;
import org.lockss.test.*;

public class TestMapUtil extends LockssTestCase {
  public void testFromList1() {
    assertEquals(MapUtil.map(), MapUtil.fromList(ListUtil.list()));
    assertEquals(MapUtil.map("FOO", "bar", "One", "Two"),
		 MapUtil.fromList(ListUtil.list("FOO", "bar", "One", "Two")));
    assertEquals(MapUtil.map("foo", "bar", "one", "two"),
		 MapUtil.fromList(ListUtil.list(ListUtil.list("foo", "bar"),
						ListUtil.list("one", "two"))));

    try {
      MapUtil.fromList(ListUtil.list("FOO", "bar", "One"));
      fail("Odd length arg list should throw");
    } catch (IllegalArgumentException e) {
    }
    try {
      MapUtil.fromList(ListUtil.list(ListUtil.list("foo", "bar"),
				     ListUtil.list("one")));
      fail("Short sublist should throw");
    } catch (IllegalArgumentException e) {
    }
  }

  public void testExpandMultiKeys() {
    assertEquals(MapUtil.map(),
		 MapUtil.expandAlternativeKeyLists(MapUtil.map()));
    assertEquals(MapUtil.map("1", "A"),
		 MapUtil.expandAlternativeKeyLists(MapUtil.map("1", "A")));
    assertEquals(MapUtil.map("1", "A", "2", "A"),
		 MapUtil.expandAlternativeKeyLists(MapUtil.map("1;2", "A")));
    assertEquals(MapUtil.map("1", "A", "2", "B", "*", "B"),
		 MapUtil.expandAlternativeKeyLists(MapUtil.map("1", "A", "2;*", "B")));
  }

}
