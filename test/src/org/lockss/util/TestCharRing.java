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
import org.lockss.test.*;

public class TestCharRing extends LockssTestCase {
  CharRing cr;

  public void setUp() throws CharRing.RingFullException {
    cr = new CharRing(5);
    cr.add('a');
    cr.add('b');
    cr.add('c');
    cr.add('d');
    cr.add('e');
  }


  public void testZeroCapacityThrows() {
    try {
      CharRing cr2 = new CharRing(0);
      fail("Trying to create a CharRing with a capacity of "
	   +"zero should have thrown");
    } catch (IllegalArgumentException e) {
    }
  }

  public void testNegativeCapacityThrows() {
    try {
      CharRing cr2 = new CharRing(-1);
      fail("Trying to create a CharRing with a capacity of "
	   +"zero should have thrown");
    } catch (IllegalArgumentException e) {
    }
  }

  public void testGet() {
    assertEquals('a', cr.get(0));
    assertEquals('b', cr.get(1));
    assertEquals('c', cr.get(2));
    assertEquals('d', cr.get(3));
    assertEquals('e', cr.get(4));
  }

  public void testWrapping() throws CharRing.RingFullException {
    cr.remove();
    cr.add('f');
    assertEquals('b', cr.get(0));
    assertEquals('c', cr.get(1));
    assertEquals('d', cr.get(2));
    assertEquals('e', cr.get(3));
    assertEquals('f', cr.get(4));

    cr.remove();
    cr.add('g');
    assertEquals('c', cr.get(0));
    assertEquals('d', cr.get(1));
    assertEquals('e', cr.get(2));
    assertEquals('f', cr.get(3));
    assertEquals('g', cr.get(4));
  }

  public void testGetNthCharThrowsIfLargerThanCapacity() {
    try {
      cr.get(5);
      fail("getNthChar should have thrown when N was greater than capacity");
    } catch (IndexOutOfBoundsException e) {
    }
  }

  public void testGetNthCharThrowsIfLargerThanSize() {
    cr.remove();
    try {
      cr.get(4);
      fail("getNthChar should have thrown when N was greater than size");
    } catch (IndexOutOfBoundsException e) {
    }
  }

  public void testCapacityIsCorrect() {
    CharRing cr2 = new CharRing(7);
    assertEquals(7, cr2.capacity());
  }

  public void testAddingTooManyThrows() {
    try {
      cr.add('f');
      fail("Adding a 6th element to a 5 element CharRing should have thrown");
    } catch (CharRing.RingFullException e) {
    }
  }

  public void testRemoveDoes() {
    assertEquals('a', cr.remove());
    assertEquals('b', cr.get(0));
  }
  
  public void testRemoveThrowsIfNothingLeft() {
    while (cr.size() > 0) {
      cr.remove();
    }
    try {
      cr.remove();
      fail("remove() on empty char ring should have thrown");
    } catch (IndexOutOfBoundsException e) {
    }
  }

  public void testSize() {
    assertEquals(5, cr.size());
    cr.remove();
    assertEquals(4, cr.size());
    cr.remove();
    cr.remove();
    cr.remove();
    cr.remove();
    assertEquals(0, cr.size());
  }

  public void testClearNegative() {
    try {
      cr.clear(-1);
      fail("clear(-1) should have thrown");
    } catch (IndexOutOfBoundsException e) {
    }
  }

  public void testClearZero() {
    cr.clear(0);
    assertEquals('a', cr.get(0));
  }

  public void testClearMany() {
    assertEquals('a', cr.get(0));
    cr.clear(2);
    assertEquals('c', cr.get(0));
    cr.clear(1);
    assertEquals('d', cr.get(0));
    assertEquals(2, cr.size());
  }

  public void testClearThrowsIfOverSize() {
    try {
      cr.clear(cr.size()+1);
      fail("clear(6) Should have thrown");
    } catch (IndexOutOfBoundsException e) {
    }
  }

  public void testArrayAddThrowsIfArrayTooBig() {
    char chars[] = {'z', 'x', 'y'};
    CharRing cr2 = new CharRing(1);
    try {
      cr2.add(chars);
      fail("Add should have thrown with too many chars");
    } catch (CharRing.RingFullException e) {
    }
  }

  public void testArrayAddNoWrap() throws CharRing.RingFullException {
    char chars[] = {'z', 'x', 'y'};
    CharRing cr2 = new CharRing(3);
    cr2.add(chars);
    assertEquals('z', cr2.remove());
    assertEquals('x', cr2.remove());
    assertEquals('y', cr2.remove());
    assertEquals(0, cr2.size());
  }

  public void testArrayAddWrap1() throws CharRing.RingFullException {
    char chars[] = {'z', 'y', 'x', 'w'};
    CharRing cr2 = new CharRing(4);
    cr2.add('b');
    cr2.remove();
    cr2.add(chars);
    assertEquals('z', cr2.remove());
    assertEquals('y', cr2.remove());
    assertEquals('x', cr2.remove());
    assertEquals('w', cr2.remove());
    assertEquals(0, cr2.size());
  }

  public void testArrayAddWrap2() throws CharRing.RingFullException {
    char chars[] = {'z', 'y', 'x', 'w'};
    CharRing cr2 = new CharRing(4);
    cr2.add('b');
    cr2.add('c');
    cr2.remove();
    cr2.remove();
    cr2.add(chars);
    assertEquals('z', cr2.remove());
    assertEquals('y', cr2.remove());
    assertEquals('x', cr2.remove());
    assertEquals('w', cr2.remove());
    assertEquals(0, cr2.size());
  }

  public void testArrayAddWrap3() throws CharRing.RingFullException {
    char chars[] = {'z', 'y', 'x', 'w'};
    CharRing cr2 = new CharRing(4);
    cr2.add('b');
    cr2.add('c');
    cr2.add('d');
    cr2.remove();
    cr2.remove();
    cr2.remove();
    cr2.add(chars);
    assertEquals('z', cr2.remove());
    assertEquals('y', cr2.remove());
    assertEquals('x', cr2.remove());
    assertEquals('w', cr2.remove());
    assertEquals(0, cr2.size());
  }

  public void testArrayAddWrapWPosAndLength()
      throws CharRing.RingFullException {
    char chars[] = {'z', 'x', 'y', 'q'};
    CharRing cr2 = new CharRing(3);
    cr2.add('b');
    cr2.remove();
    cr2.add(chars, 1, 2);
    assertEquals('x', cr2.remove());
    assertEquals('y', cr2.remove());
    assertEquals(0, cr2.size());
  }

  public void testAddArrayShorterThanCharRing()
      throws CharRing.RingFullException {
    char chars[] = {'z', 'x', 'y', 'q'};
    CharRing cr2 = new CharRing(5);
    cr2.add(chars, 1, 2);
    assertEquals('x', cr2.remove());
    assertEquals('y', cr2.remove());
    assertEquals(0, cr2.size());
  }

  public void testArrayRemoveEmptyCharRing() {
    char chars[] = new char[4];
    assertEquals(0, new CharRing(4).remove(chars));
  }

  public void testArrayRemoveNoWrap() {
    char chars[] = new char[4];
    assertEquals(4, cr.remove(chars));
    assertEquals('a', chars[0]);
    assertEquals('b', chars[1]);
    assertEquals('c', chars[2]);
    assertEquals('d', chars[3]);
  }

  public void testArrayRemoveNoWrapWithPosAndLen() {
    char chars[] = {'w', 'x', 'y', 'z'};
    assertEquals(2, cr.remove(chars, 1, 2));
    assertEquals('w', chars[0]);
    assertEquals('a', chars[1]);
    assertEquals('b', chars[2]);
    assertEquals('z', chars[3]);
  }

  public void testArrayRemoveWrap1() throws CharRing.RingFullException {
    char chars[] = new char[5];
    cr.remove();
    cr.add('f');
    assertEquals(5, cr.remove(chars));
    assertEquals('b', chars[0]);
    assertEquals('c', chars[1]);
    assertEquals('d', chars[2]);
    assertEquals('e', chars[3]);
    assertEquals('f', chars[4]);
  }

  public void testArrayRemoveWrap2() throws CharRing.RingFullException {
    char chars[] = new char[5];
    cr.remove();
    cr.remove();
    cr.add('f');
    cr.add('g');
    assertEquals(5, cr.remove(chars));
    assertEquals('c', chars[0]);
    assertEquals('d', chars[1]);
    assertEquals('e', chars[2]);
    assertEquals('f', chars[3]);
    assertEquals('g', chars[4]);
  }

  public void testArrayRemoveWrap3() throws CharRing.RingFullException {
    char chars[] = new char[5];
    cr.remove();
    cr.remove();
    cr.remove();
    cr.add('f');
    cr.add('g');
    cr.add('h');
    assertEquals(5, cr.remove(chars));
    assertEquals('d', chars[0]);
    assertEquals('e', chars[1]);
    assertEquals('f', chars[2]);
    assertEquals('g', chars[3]);
    assertEquals('h', chars[4]);
  }

  public void testArrayAddThrowsIfPosNegative()
      throws CharRing.RingFullException {
    CharRing ring = new CharRing(5);
    try {
      ring.add(new char[4], -1, 3);
      fail("Adding with a negative position should have thrown");
    } catch (IndexOutOfBoundsException e) {
    }
  }

  public void testArrayAddThrowsIfLengthNegative()
      throws CharRing.RingFullException {
    CharRing ring = new CharRing(5);
    try {
      ring.add(new char[4], 0, -1);
      fail("Adding with a negative length should have thrown");
    } catch (IndexOutOfBoundsException e) {
    }
  }

  public void testArrayRemoveThrowsIfPosNegative()
      throws CharRing.RingFullException {
    CharRing ring = new CharRing(5);
    try {
      ring.remove(new char[4], -1, 3);
      fail("Removing with a negative position should have thrown");
    } catch (IndexOutOfBoundsException e) {
    }
  }

  public void testArrayRemoveThrowsIfLengthNegative()
      throws CharRing.RingFullException {
    CharRing ring = new CharRing(5);
    try {
      ring.remove(new char[4], 0, -1);
      fail("Removing with a negative length should have thrown");
    } catch (IndexOutOfBoundsException e) {
    }
  }
}
