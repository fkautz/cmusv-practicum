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

package org.lockss.daemon;

import java.util.*;
import java.io.*;
import java.net.*;
import org.lockss.util.*;
import org.lockss.test.*;

/**
 * Test class for <code>org.lockss.util.Configuration</code>
 */

public class TestConfiguration extends LockssTestCase {
  public static Class testedClasses[] = {
    org.lockss.daemon.Configuration.class
  };


  public TestConfiguration(String msg) {
    super(msg);
  }

  private static final String c1 = "prop1=12\nprop2=foobar\nprop3=true\n" +
    "prop5=False\n";
  private static final String c1a = "prop2=xxx\nprop4=yyy\n";

  public void testLoad() throws IOException, Configuration.InvalidParam {
    String f = FileUtil.urlOfString(c1);
    Configuration config = Configuration.newConfiguration();
    config.load(f);
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

  public void testLoadList() throws IOException {
    Configuration config = Configuration.newConfiguration();
    config.loadList(ListUtil.list(FileUtil.urlOfString(c1),
				  FileUtil.urlOfString(c1a)));
    assertEquals("12", config.get("prop1"));
    assertEquals("xxx", config.get("prop2"));
    assertTrue(config.getBoolean("prop3", false));
    assertEquals("yyy", config.get("prop4"));
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
    Configuration config = Configuration.newConfiguration();
    config.load(FileUtil.urlOfString(c2));
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

  public void testParam() throws IOException, Configuration.InvalidParam {
    Configuration config = Configuration.newConfiguration();
    config.load(FileUtil.urlOfString(c2));
    Configuration.setCurrentConfig(config);
    assertEquals("12", Configuration.getParam("prop.p1"));
    assertEquals("foobar", Configuration.getParam("prop.p2"));
    assertTrue(Configuration.getBooleanParam("prop.p3.a", false));
    assertEquals(12, Configuration.getIntParam("prop.p1"));
    assertEquals(554, Configuration.getIntParam("propnot.p1", 554));
    assertEquals(2 * Constants.WEEK,
		 Configuration.getTimeIntervalParam("timeint", 554));
    assertEquals(554, Configuration.getTimeIntervalParam("noparam", 554));
  }

  public static boolean setCurrentConfigFromUrlList(List l) {
    Configuration config = Configuration.readConfig(l);
    return Configuration.installConfig(config);
  }

  public static boolean setCurrentConfigFromString(String s)
      throws IOException {
    return setCurrentConfigFromUrlList(ListUtil.list(FileUtil.urlOfString(s)));
  }

  public void testPercentage() throws Exception {
    Properties props = new Properties();
    props.put("p1", "-1");
    props.put("p2", "0");
    props.put("p3", "20");
    props.put("p4", "100");
    props.put("p5", "101");
    props.put("p6", "foo");
    Configuration config = ConfigurationUtil.fromProps(props);
    assertEquals(0.0, config.getPercentage("p2"), 0.0);
    assertEquals(0.2, config.getPercentage("p3"), 0.0000001);
    assertEquals(1.0, config.getPercentage("p4"), 0.0);
    assertEquals(0.1, config.getPercentage("p1", 0.1), 0.0000001);
    assertEquals(0.5, config.getPercentage("p6", 0.5), 0.0);
    assertEquals(0.1, config.getPercentage("p1", 0.1), 0.0000001);

    try {
      config.getPercentage("p1");
      fail("getPercentage(-1) should throw");
    } catch (Configuration.InvalidParam e) {
    }
    try {
      config.getPercentage("p5");
      fail("getPercentage(101) should throw");
    } catch (Configuration.InvalidParam e) {
    }
    try {
      config.getPercentage("p6");
      fail("getPercentage(foo) should throw");
    } catch (Configuration.InvalidParam e) {
    }
  }

  public void testCurrentConfig() throws IOException {
    assertTrue(setCurrentConfigFromUrlList(ListUtil.
					   list(FileUtil.urlOfString(c1),
						FileUtil.urlOfString(c1a))));
    assertEquals("12", Configuration.getParam("prop1"));
    Configuration config = Configuration.getCurrentConfig();
    assertEquals("12", config.get("prop1"));
    assertEquals("12", config.get("prop1", "wrong"));
    assertEquals("xxx", config.get("prop2"));
    assertTrue(config.getBoolean("prop3", false));
    assertEquals("yyy", config.get("prop4"));
    assertEquals("def", config.get("noprop", "def"));
    assertEquals("def", Configuration.getParam("noprop", "def"));
  }

  volatile Set diffSet = null;
  List configs;

  public void testCallback() throws IOException {
    configs = new ArrayList();
    setCurrentConfigFromUrlList(ListUtil.list(FileUtil.urlOfString(c1),
					      FileUtil.urlOfString(c1a)));
    assertEquals(0, configs.size());
    Configuration.registerConfigurationCallback(new Configuration.Callback() {
	public void configurationChanged(Configuration newConfig,
					 Configuration oldConfig,
					 Set changedKeys) {
	  assertNotNull(oldConfig);
	  configs.add(newConfig);
	}
      });
    assertEquals(1, configs.size());
  }

  public void testCallbackDiffs() throws IOException {
    setCurrentConfigFromUrlList(ListUtil.list(FileUtil.urlOfString(c1),
					      FileUtil.urlOfString(c1a)));
    System.out.println(Configuration.getCurrentConfig().toString());
    Configuration.registerConfigurationCallback(new Configuration.Callback() {
	public void configurationChanged(Configuration newConfig,
					 Configuration oldConfig,
					 Set changedKeys) {
	  System.out.println("Notify: " + changedKeys);
	  diffSet = changedKeys;
	}
      });
    assertTrue(setCurrentConfigFromUrlList(ListUtil.
					   list(FileUtil.urlOfString(c1a),
						FileUtil.urlOfString(c1))));
    assertEquals(SetUtil.set("prop2"), diffSet);
    System.out.println(Configuration.getCurrentConfig().toString());
    assertTrue(setCurrentConfigFromUrlList(ListUtil.
					   list(FileUtil.urlOfString(c1),
						FileUtil.urlOfString(c1))));
    assertEquals(SetUtil.set("prop4"), diffSet);
    System.out.println(Configuration.getCurrentConfig().toString());
    assertTrue(setCurrentConfigFromUrlList(ListUtil.
					   list(FileUtil.urlOfString(c1),
						FileUtil.urlOfString(c1a))));
    assertEquals(SetUtil.set("prop4", "prop2"), diffSet);
    System.out.println(Configuration.getCurrentConfig().toString());

  }

  public void testPlatformProps() throws Exception {
    Properties props = new Properties();
    props.put("org.lockss.platform.localIPAddress", "1.2.3.4");
    props.put("org.lockss.platform.logdirectory", "/var/log/foo");
    props.put("org.lockss.platform.logfile", "bar");
    ConfigurationUtil.setCurrentConfigFromProps(props);
    Configuration config = Configuration.getCurrentConfig();
    assertEquals("1.2.3.4", config.get("org.lockss.localIPAddress"));
    assertEquals("/var/log/foo/bar", config.get(FileTarget.PARAM_FILE));
  }
}
