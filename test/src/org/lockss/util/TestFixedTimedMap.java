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

package org.lockss.util;

import java.util.*;
import junit.framework.*;
import org.lockss.test.LockssTestCase;

/**
 * <p>Title: TestFixedTimedMap </p>
 * <p>Description: A set of unit test for the FixedTimedMap class.</p>
 * @author Tyrone Nicholas
 * @version 1.0
 */

public class TestFixedTimedMap extends LockssTestCase {

  public static Class testedClasses[] = {
    org.lockss.util.FixedTimedMap.class
  };

  protected void setUp() throws Exception {
    TimeBase.setSimulated();
    super.setUp();
  }

  public void tearDown() throws Exception {
    TimeBase.setReal();
    super.tearDown();
  }

  static int timeout = 60000;

  Object keys[] = { new Integer(3),"foo",new Double(4.0) };
  Object values[] = { "three","bar",new Integer(4) };

  FixedTimedMap makeGeneric()
  {
    FixedTimedMap map = new FixedTimedMap(timeout);
    map.put(keys[0],values[0]);
    assertTrue(timeout>10);
    TimeBase.step(timeout/10);
    map.put(keys[1],values[1]);
    map.put(keys[2],values[2]);
    return map;
  }

  public void testTimeout()
  {
    FixedTimedMap map = makeGeneric();
    TimeBase.step(timeout*9/10-1);
    Object value = map.get(keys[0]);
    assertSame(value,values[0]);
    TimeBase.step(1);
    value = map.get(keys[0]);
    assertNull(value);
  }

  public void testClear()
  {
    FixedTimedMap map = makeGeneric();
    assertFalse(map.isEmpty());
    map.clear();
    assertTrue(map.isEmpty());
  }

  public void testPutGet() {
    FixedTimedMap map = makeGeneric();
    assertEquals(keys.length,values.length);
    for (int i=0; i<keys.length; i++)
      assertSame(map.get(keys[i]),values[i]);
  }

  public void testOverwrite() {
    FixedTimedMap map = makeGeneric();
    map.put(keys[1],"joe");
    String joe = (String)map.get(keys[1]);
    assertSame(joe,"joe");
  }

  public void testUpdate() {
    FixedTimedMap map = makeGeneric();
    TimeBase.step(timeout-1);
    map.put(keys[1],values[1]);
    TimeBase.step(timeout-1);
    assertTrue(map.containsKey(keys[1]));
  }

  public void testEqualityHash() {
    TimeBase.setSimulated();
    FixedTimedMap map = makeGeneric();
    TimeBase.setSimulated();
    FixedTimedMap map3 = makeGeneric();
    FixedTimedMap map2 = new FixedTimedMap(timeout);
    map2.putAll(map);
    assertEquals(map.hashCode(),map3.hashCode());
  }

  public void testSizeRemove() {
    FixedTimedMap map = makeGeneric();
    assertEquals(map.size(),keys.length);
    map.put("joe","sue");
    assertEquals(map.size(),keys.length+1);
    map.remove("joe");
    assertEquals(map.size(),keys.length);
  }

  public void testPutAll() {
    FixedTimedMap map = makeGeneric();
    Map t = new HashMap();
    t.put("hack","burn");
    t.put(new Integer(18),"eighteen");
    Integer eight = new Integer(8);
    t.put(new Float(8.8),eight);
    map.putAll(t);
    t = null;
    assertSame(map.get("hack"),"burn");
    assertEquals(map.size(),keys.length+3);
    assertSame(map.get(new Float(8.8)),eight);
  }


  void checkCollection(Collection coll,Object[] objs) {
    int loc = 0;
    Iterator it = coll.iterator();
    assertEquals(coll.size(), objs.length);
    while (it.hasNext()) {
      assertSame(it.next(), objs[loc++]);
    }
  }
  public void testSets() {
    FixedTimedMap map = makeGeneric();

    Set keyset = map.keySet();
    checkCollection(keyset,keys);

    Collection valuecoll = map.values();
    checkCollection(valuecoll,values);
  }

  public void testEntrySet() {
    FixedTimedMap map = makeGeneric();
    int loc = 0;
    Set entryset = map.entrySet();
    Iterator it = entryset.iterator();
    assertEquals(entryset.size(), keys.length);
    assertEquals(entryset.size(), values.length);
    while (it.hasNext()) {
      Map.Entry entry = (Map.Entry) it.next();
      assertSame(entry.getKey(), keys[loc]);
      assertSame(entry.getValue(), values[loc++]);
    }
  }

  public void testIteratorExpiry() {
    FixedTimedMap map = makeGeneric();
    Set entryset = map.entrySet();
    Iterator entryit = entryset.iterator();
    TimeBase.step(timeout + 1);
    Object obj;
    try {
      obj = entryit.next();
      fail("Should have thrown TimedIteratorExpiredException");
    }
    catch (TimedIteratorExpiredException e) {
      Iterator keyit = map.keySet().iterator();
      TimeBase.step(timeout);
      try {
        obj = keyit.next();
        fail("Should have thrown TimedIteratorExpiredException");
      }
      catch (TimedIteratorExpiredException f) {
        Iterator valueit = map.values().iterator();
        TimeBase.step(timeout);
        try {
          obj = valueit.next();
          fail("Should have thrown TimedIteratorExpiredException");
        }
        catch (TimedIteratorExpiredException g) {
        }
      }
    }
  }

  void verifyIteratorInList(Iterator it, List list) {
    while (it.hasNext()) {
      assertTrue(list.contains(it.next()));
    }
  }

  public void testIterators() {
    FixedTimedMap map = makeGeneric();
    Iterator entryit = map.entrySet().iterator();
    Iterator keyit = map.keySet().iterator();
    Iterator valueit = map.values().iterator();
    List keylist = Arrays.asList(keys);
    List valuelist = Arrays.asList(values);
    while (entryit.hasNext()) {
      Map.Entry entry = (Map.Entry)entryit.next();
      assertTrue(keylist.contains(entry.getKey()));
      assertTrue(valuelist.contains(entry.getValue()));
    }
    verifyIteratorInList(keyit,keylist);
    verifyIteratorInList(valueit,valuelist);
  }

  public static Test suite() {
    return new TestSuite(TestFixedTimedMap.class);
  }
}