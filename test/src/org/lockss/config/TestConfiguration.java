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

package org.lockss.config;

import java.util.*;
import java.io.*;
import java.net.*;
import org.lockss.util.*;
import org.lockss.config.ConfigFile;
import org.lockss.config.Configuration;
import org.lockss.config.ConfigurationPropTreeImpl;
import org.lockss.test.*;

/**
 * Test class for <code>org.lockss.util.Configuration</code>
 */

public class TestConfiguration extends LockssTestCase {

  public static Class testedClasses[] = {
    org.lockss.config.Configuration.class
  };

  public void setUp() throws Exception {
    super.setUp();
  }

  public void tearDown() throws Exception {
    super.tearDown();
  }

  static Logger log = Logger.getLogger("TestConfig");

  private static final String c1 = "prop1=12\nprop2=foobar\nprop3=true\n" +
    "prop5=False\n";
  private static final String c1a = "prop2=xxx\nprop4=yyy\n";

  private Configuration newConfiguration() {
    return new ConfigurationPropTreeImpl();
  }

  public void testSet() {
    Configuration config = newConfiguration();
    assertEquals(0, config.keySet().size());
    config.put("a", "b");
    assertEquals(1, config.keySet().size());
    assertEquals("b", config.get("a"));
  }

  public void testRemove() {
    Configuration config = newConfiguration();
    config.put("a", "1");
    config.put("b", "2");
    assertEquals(2, config.keySet().size());
    assertEquals("1", config.get("a"));
    assertEquals("2", config.get("b"));
    config.remove("a");
    assertEquals(1, config.keySet().size());
    assertEquals(null, config.get("a"));
    assertEquals("2", config.get("b"));
  }

  public void testRemoveTree() {
    Configuration config = newConfiguration();
    config.put("a", "1");
    config.put("b", "2");
    config.put("b.a", "3");
    config.put("b.a.1", "4");
    config.put("b.a.1.1", "5");
    config.put("b.a.2", "6");
    config.put("b.b", "7");
    assertEquals(7, config.keySet().size());
    config.removeConfigTree("b.a");
    assertEquals(3, config.keySet().size());
    assertEquals("1", config.get("a"));
    assertEquals("2", config.get("b"));
    assertEquals(null, config.get("b.a"));
    assertEquals(null, config.get("b.a.1"));
    // removing a non-existent tree should do nothing
    config.removeConfigTree("dkdkdk");
    assertEquals(3, config.keySet().size());
  }

  public void testCopyTree() {
    Configuration from = newConfiguration();
    from.put("a", "1");
    from.put("b", "2");
    from.put("b.a", "3");
    from.put("b.a.1", "4");
    from.put("b.a.1.1", "5");
    from.put("b.a.2", "6");
    from.put("b.b", "7");
    Configuration to = newConfiguration();
    to.put("a", "2");
    to.copyConfigTreeFrom(from, "b.a");
    assertEquals(5, to.keySet().size());
    assertEquals("2", to.get("a"));
    assertEquals("3", to.get("b.a"));
    assertEquals("4", to.get("b.a.1"));
    assertEquals("5", to.get("b.a.1.1"));
    assertEquals("6", to.get("b.a.2"));
  }

  private void assertSealed(Configuration config) {
    try {
      config.put("b", "3");
      fail("put into sealed config should throw IllegalStateException");
    } catch (IllegalStateException e) {
    }
    try {
      config.remove("a");
      fail("remove from sealed config should throw IllegalStateException");
    } catch (IllegalStateException e) {
    }
    try {
      config.removeConfigTree("a");
      fail("remove from sealed config should throw IllegalStateException");
    } catch (IllegalStateException e) {
    }
  }

  public void testSeal() {
    Configuration config = newConfiguration();
    config.put("a", "1");
    config.put("b", "2");
    config.put("b.x", "3");
    config.seal();
    assertSealed(config);
    assertEquals(3, config.keySet().size());
    assertEquals("1", config.get("a"));
    assertEquals("2", config.get("b"));
    // check that subconfig of sealed config is sealed
    assertSealed(config.getConfigTree("b"));
  }

  public void testCopy() {
    Configuration c1 = newConfiguration();
    c1.put("a", "1");
    c1.put("b", "2");
    c1.put("b.x", "3");
    c1.seal();
    Configuration c2 = c1.copy();
    assertEquals(3, c2.keySet().size());
    assertEquals("1", c2.get("a"));
    assertEquals("2", c2.get("b"));
    assertFalse(c2.isSealed());
    c2.put("a", "cc");
    assertEquals("cc", c2.get("a"));
  }

  public void testCopyFrom() {
    Configuration c1 = newConfiguration();
    c1.put("a", "1");
    c1.put("b", "2");
    c1.put("b.x", "3");
    c1.seal();
    Configuration c2 = newConfiguration();
    c2.copyFrom(c1);
    assertEquals(3, c2.keySet().size());
    assertEquals("1", c2.get("a"));
    assertEquals("2", c2.get("b"));
    assertFalse(c2.isSealed());
    c2.put("a", "cc");
    assertEquals("cc", c2.get("a"));
  }

  public void testLoad() throws IOException, Configuration.InvalidParam {
    String f = FileTestUtil.urlOfString(c1);
    Configuration config = newConfiguration();
    config.load(loadFCF(f));
    assertEquals("12", config.get("prop1"));
    assertEquals("12", config.get("prop1", "wrong"));
    assertEquals("foobar", config.get("prop2"));
    assertEquals("not", config.get("propnot", "not"));

    assertTrue(config.getBoolean("prop3"));
    assertTrue(config.getBoolean("prop3", false));
    assertFalse(config.getBoolean("prop1", false));
    assertFalse(config.getBoolean("prop5"));
    assertFalse(config.getBoolean("prop5", true));
    assertEquals(12, config.getInt("prop1"));
    assertEquals(42, config.getInt("propnot", 42));
    assertEquals(123, config.getInt("prop2", 123));
    try {
      config.getBoolean("prop1");
      fail("getBoolean(non-boolean) didn't throw");
    } catch (Configuration.InvalidParam e) {
    }
    try {
      config.getBoolean("propnot");
      fail("getBoolean(missing) didn't throw");
    } catch (Configuration.InvalidParam e) {
    }
    try {
      config.getInt("prop2");
      fail("getInt(non-int) didn't throw");
    } catch (Configuration.InvalidParam e) {
    }
    try {
      config.getInt("propnot");
      fail("getInt(missing) didn't throw");
    } catch (Configuration.InvalidParam e) {
    }
    assertTrue(config.containsKey("prop1"));
    assertFalse( config.containsKey("propnot"));
  }

  private static final String c3 =
    "prop.p1=a;b;c;d;e;f;g";
  
  public void testGetList() throws IOException {
    Configuration config = newConfiguration();
    config.load(loadFCF(FileTestUtil.urlOfString(c3)));
    try {
      List l = config.getList("prop.p1");
      assertNotNull(l);
      assertEquals(7, l.size());
      Collections.sort(l);
      assertEquals("a", (String)l.get(0));
      assertEquals("b", (String)l.get(1));
      assertEquals("c", (String)l.get(2));
      assertEquals("d", (String)l.get(3));
      assertEquals("e", (String)l.get(4));
      assertEquals("f", (String)l.get(5));
      assertEquals("g", (String)l.get(6));
    } catch (Exception ex) {
      fail("Should not have thrown: " + ex);
    }
  }

  public void testGetListEmptyStrings() throws IOException {
    Configuration config = newConfiguration();
    config.load(loadFCF(FileTestUtil.urlOfString("prop.p1=a;;b;")));
    assertEquals(ListUtil.list("a", "b"), config.getList("prop.p1"));
  }

  private static final String c2 =
    "timeint=14d\n" +
    "prop.p1=12\n" +
    "prop.p2=foobar\n" +
    "prop.p3.a=true\n" +
    "prop.p3.b=false\n" +
    "otherprop.p3.b=foo\n";

  private static HashMap m2 = new HashMap();
  static {
    m2.put("prop.p1", "12");
    m2.put("prop.p2", "foobar");
    m2.put("timeint", "14d");
    m2.put("prop.p3.a", "true");
    m2.put("prop.p3.b", "false");
    m2.put("otherprop.p3.b", "foo");
  };
  private static HashMap m2a = new HashMap();
  static {
    m2a.put("p1", "12");
    m2a.put("p2", "foobar");
    m2a.put("p3.a", "true");
    m2a.put("p3.b", "false");
  };

  private Map mapFromIter(Iterator iter, Configuration config) {
    Map map = new HashMap();
    while (iter.hasNext()) {
      String key = (String)iter.next();
      map.put(key, config.get(key));
    }
    return map;
  }

  private Map mapFromConfig(Configuration config) {
    return mapFromIter(config.keyIterator(), config);
  }

  public void testStruct() throws IOException {
    Configuration config = newConfiguration();
    config.load(loadFCF(FileTestUtil.urlOfString(c2)));
    Set set = new HashSet();
    for (Iterator iter = config.keyIterator(); iter.hasNext();) {
      set.add(iter.next());
    }
    assertEquals(6, set.size());
    assertEquals(m2.keySet(), set);
    {
      Map map = mapFromConfig(config);
      assertEquals(6, map.size());
      assertEquals(m2, map);
    }
    {
      Map map = mapFromIter(config.nodeIterator(), config);
      assertEquals(3, map.size());
    }
    Configuration conf2 = config.getConfigTree("prop");
    {
      Map map = mapFromConfig(conf2);
      assertEquals(4, map.size());
      assertEquals(m2a, map);
    }
  }

  public void testEmptyTree() {
    Configuration config = newConfiguration();
    Iterator it1 = config.nodeIterator();
    assertNotNull(it1);
    assertFalse(it1.hasNext());
    Iterator it2 = config.nodeIterator("foo.bar");
    assertNotNull(it2);
    assertFalse(it2.hasNext());
  }

  public void testTimeInterval() throws Exception {
    Properties props = new Properties();
    props.put("p1", "1");
    props.put("p2", "0s");
    props.put("p3", "20m");
    props.put("p4", "100h");
    props.put("p5", "101d");
    props.put("p6", "foo");
    props.put("p7", "250x");
    Configuration config = ConfigurationUtil.fromProps(props);
    assertEquals(1, config.getTimeInterval("p1"));
    assertEquals(0, config.getTimeInterval("p2"));
    assertEquals(20*Constants.MINUTE, config.getTimeInterval("p3"));
    assertEquals(100*Constants.HOUR, config.getTimeInterval("p4"));
    assertEquals(101*Constants.DAY, config.getTimeInterval("p5"));
    try {
      config.getTimeInterval("p6");
      fail("getTimeInterval(foo) should throw");
    } catch (Configuration.InvalidParam e) {
    }
    try {
      config.getTimeInterval("p7");
      fail("getTimeInterval(250x) should throw");
    } catch (Configuration.InvalidParam e) {
    }
  }

  public void testSize() throws Exception {
    long k = 1024;
    long m = k*k;
    Properties props = new Properties();
    props.put("p0", "1");
    props.put("p1", "1000000b");
    props.put("p2", "100kb");
    props.put("p3", "2.5mb");
    props.put("p4", "100gb");
    props.put("p5", "6.8tb");
    props.put("p6", "1.5pb");
    props.put("p7", "foo");
    props.put("p8", "250x");
    Configuration config = ConfigurationUtil.fromProps(props);
    assertEquals(1, config.getSize("p0"));
    assertEquals(1000000, config.getSize("p1"));
    assertEquals(100*k, config.getSize("p2"));
    assertEquals((long)(2.5*m), config.getSize("p3"));
    assertEquals(100*k*m, config.getSize("p4"));
    assertEquals((long)(6.8f*(m*m)), config.getSize("p5"));
    assertEquals((long)(1.5f*(m*m*k)), config.getSize("p6"));
    try {
      config.getSize("p7");
      fail("getSize(foo) should throw");
    } catch (Configuration.InvalidParam e) {
    }
    try {
      config.getSize("p8");
      fail("getSize(250x) should throw");
    } catch (Configuration.InvalidParam e) {
    }
  }

  public void testPercentage() throws Exception {
    Properties props = new Properties();
    props.put("p1", "-1");
    props.put("p2", "0");
    props.put("p3", "20");
    props.put("p4", "100");
    props.put("p5", "101");
    props.put("p6", "foo");
    props.put("p7", "250");
    Configuration config = ConfigurationUtil.fromProps(props);
    assertEquals(0.0, config.getPercentage("p2"), 0.0);
    assertEquals(0.2, config.getPercentage("p3"), 0.0000001);
    assertEquals(1.0, config.getPercentage("p4"), 0.0);
    assertEquals(0.1, config.getPercentage("p1", 0.1), 0.0000001);
    assertEquals(0.5, config.getPercentage("p6", 0.5), 0.0);
    assertEquals(0.1, config.getPercentage("p1", 0.1), 0.0000001);
    assertEquals(2.5, config.getPercentage("p7", 0.1), 0.0000001);
    assertEquals(2.5, config.getPercentage("p7"), 0.0000001);

    try {
      config.getPercentage("p1");
      fail("getPercentage(-1) should throw");
    } catch (Configuration.InvalidParam e) {
    }
    try {
      config.getPercentage("p6");
      fail("getPercentage(foo) should throw");
    } catch (Configuration.InvalidParam e) {
    }
  }

  public void testAddPrefix() throws Exception {
    Properties props = new Properties();
    props.put("p1", "a");
    props.put("p2", "b");
    Configuration c1 = ConfigurationUtil.fromProps(props);
    Configuration c2 = c1.addPrefix("a");
    Configuration c3 = c1.addPrefix("foo.bar.");
    assertEquals(2, c2.keySet().size());
    assertEquals("a", c2.get("a.p1"));
    assertEquals("b", c2.get("a.p2"));
    assertEquals(2, c3.keySet().size());
    assertEquals("a", c3.get("foo.bar.p1"));
    assertEquals("b", c3.get("foo.bar.p2"));
  }

  public void testGroup() throws Exception {
    Properties props = new Properties();
    ConfigurationUtil.setCurrentConfigFromProps(props);
    assertEquals("nogroup", Configuration.getPlatformGroup());
    props.put(Configuration.PARAM_DAEMON_GROUP, "foog");
    ConfigurationUtil.setCurrentConfigFromProps(props);
    assertEquals("foog", Configuration.getPlatformGroup());
  }

  private ConfigFile loadFCF(String url) throws IOException {
    ConfigFile cf = new FileConfigFile(url);
    cf.reload();
    return cf;
  }
}
