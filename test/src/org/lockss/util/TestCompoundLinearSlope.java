/*
 * $Id$
 */

/*

Copyright (c) 2000-2008 Board of Trustees of Leland Stanford Jr. University,
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
import org.lockss.test.*;
import org.lockss.util.CompoundLinearSlope.Point;
import static org.lockss.util.Constants.*;

/**
 * This is the test class for org.lockss.util.CompoundLinearSlope
 */
public class TestCompoundLinearSlope extends LockssTestCase {
  public void testIll() {
    try {
      new CompoundLinearSlope((List)null);
      fail("Should have throw IllegalArgumentException");
    } catch (IllegalArgumentException e) {
    }
    try {
      new CompoundLinearSlope(ListUtil.list());
      fail("Should have throw IllegalArgumentException");
    } catch (IllegalArgumentException e) {
    }
    try {
      new CompoundLinearSlope((String)null);
      fail("Should have throw IllegalArgumentException");
    } catch (IllegalArgumentException e) {
    }
    try {
      new CompoundLinearSlope("");
      fail("Should have throw IllegalArgumentException");
    } catch (IllegalArgumentException e) {
    }
    try {
      new CompoundLinearSlope("foo");
      fail("Should have throw IllegalArgumentException");
    } catch (IllegalArgumentException e) {
    }
  }

  public void testAscending() {
    CompoundLinearSlope c1 =
      new CompoundLinearSlope(ListUtil.list(new Point(100, 10),
					    new Point(1100, 20),
					    new Point(11100, 90)));
    testAscending(c1);
    CompoundLinearSlope c2 =
      new CompoundLinearSlope("[100,10],[1100,20],[11100,90]");
    testAscending(c2);
    CompoundLinearSlope c3 =
      new CompoundLinearSlope("[100, 10], [1100 ,20] , [11100 , 90]");
    testAscending(c2);
  }

  public void testAscending(CompoundLinearSlope c) {
    assertEquals(10, c.getY(-10));
    assertEquals(10, c.getY(0));
    assertEquals(10, c.getY(100));
    assertEquals(10, c.getY(110));
    assertEquals(11, c.getY(200));
    assertEquals(20, c.getY(1110));
    assertEquals(30, c.getY(1110 + 1428));
    assertEquals(40, c.getY(1110 + 1428 + 1428));
    assertEquals(90, c.getY(111000));
    assertEquals(90, c.getY(1110000));
  }

  public void testDescending() {
    CompoundLinearSlope c1 =
      new CompoundLinearSlope(ListUtil.list(new Point(10, 100),
					    new Point(110, 20),
					    new Point(1110, 10)));
    assertEquals(100, c1.getY(-10));
    assertEquals(100, c1.getY(0));
    assertEquals(100, c1.getY(10));
    assertEquals(60, c1.getY(60));
    assertEquals(10, c1.getY(1110));
    assertEquals(10, c1.getY(111000));
  }

  public void testTimeInterval() {
    CompoundLinearSlope c =
      new CompoundLinearSlope("[1h,10],[2d,50],[4w,100]");
    assertEquals(10, c.getY(-10));
    assertEquals(10, c.getY(0));
    assertEquals(10, c.getY(1 * HOUR));
    assertEquals(50, c.getY(2 * DAY));
    assertEquals(100, c.getY(4 * WEEK));
  }

  public void testStairStep() {
    CompoundLinearSlope c =
      new CompoundLinearSlope("[10,100],[10,50],[20,50],[20,10]");
    assertEquals(100, c.getY(0));
    assertEquals(100, c.getY(10));
    assertEquals(50, c.getY(11));
    assertEquals(50, c.getY(12));
    assertEquals(50, c.getY(20));
    assertEquals(10, c.getY(21));
  }

}
