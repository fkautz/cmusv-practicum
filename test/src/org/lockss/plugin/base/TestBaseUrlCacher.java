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

package org.lockss.plugin.base;

import java.io.*;
import java.util.Properties;
import org.lockss.plugin.*;
import org.lockss.daemon.*;
import org.lockss.test.*;
import org.lockss.util.TimeBase;

/**
 * This is the test class for org.lockss.plugin.simulated.GenericFileUrlCacher
 *
 * @author  Emil Aalto
 * @version 0.0
 */
public class TestBaseUrlCacher extends LockssTestCase {
  MockBaseUrlCacher cacher;
  MockCachedUrlSet mcus;
  private int pauseBeforeFetchCounter;

  public void setUp() throws Exception {
    super.setUp();

    mcus = new MockCachedUrlSet("test url");
    cacher = new MockBaseUrlCacher(mcus, "test url");
  }

  public void tearDown() throws Exception {
    TimeBase.setReal();
    super.tearDown();
  }

  public void testCache() throws IOException {
    pauseBeforeFetchCounter = 0;

    cacher.input = new StringInputStream("test stream");
    cacher.headers = new Properties();
    cacher.cache();
    // should cache
    assertTrue(cacher.wasStored);
    assertEquals(1, pauseBeforeFetchCounter);
  }

  public void testLastModifiedCache() throws IOException {
    // add the 'cached' version
    Properties cachedProps = new Properties();
    cachedProps.setProperty("date", ""+12345);
    mcus.addUrl("test stream", "test url", true, true, cachedProps);

    TimeBase.setSimulated(10000);
    cacher.input = new StringInputStream("test stream");
    cacher.headers = new Properties();
    cacher.cache();
    // shouldn't cache
    assertFalse(cacher.wasStored);

    TimeBase.step(5000);
    cacher.input = new StringInputStream("test stream");
    cacher.headers = new Properties();
    cacher.cache();
    // should cache now
    assertTrue(cacher.wasStored);

    TimeBase.setReal();
  }

  public void testForceCache() throws IOException {
    // add the 'cached' version
    Properties cachedProps = new Properties();
    cachedProps.setProperty("date", ""+12345);
    mcus.addUrl("test stream", "test url", true, true, cachedProps);

    TimeBase.setSimulated(10000);
    cacher.input = new StringInputStream("test stream");
    cacher.headers = cachedProps;
    // should still cache
    cacher.forceCache();
    assertTrue(cacher.wasStored);

    TimeBase.setReal();
  }

  public void testCacheExceptions() throws IOException {
    cacher.input = new StringInputStream("test stream");
    cacher.headers = null;
    try {
      cacher.cache();
      fail("Should have thrown CachingException.");
    } catch (BaseUrlCacher.CachingException ce) { }
    assertFalse(cacher.wasStored);

    // no exceptions from null inputstream
    cacher.input = null;
    cacher.headers = new Properties();
    cacher.cache();
    // should simply skip
    assertFalse(cacher.wasStored);

    cacher.input = new StringInputStream("test stream");
    cacher.headers = new Properties();
    cacher.cache();
    assertTrue(cacher.wasStored);
  }

  public static void main(String[] argv) {
    String[] testCaseList = { TestBaseUrlCacher.class.getName()};
    junit.swingui.TestRunner.main(testCaseList);
  }

  private class MockBaseUrlCacher extends BaseUrlCacher {
    InputStream input = null;
    Properties headers = null;
    boolean wasStored = false;

    public MockBaseUrlCacher(CachedUrlSet owner, String url) {
      super(owner, url);
    }

    public ArchivalUnit getArchivalUnit() {
      return new MyMockArchivalUnit();
    }

    public InputStream getUncachedInputStream(long lastCached) {
      // simple version which returns null if shouldn't fetch
      if (lastCached < TimeBase.nowMs()) {
        return input;
      } else {
        return null;
      }
    }

    public Properties getUncachedProperties() {
      return headers;
    }

    public void storeContent(InputStream input, Properties headers) {
      wasStored = true;
    }
  }

  private class MyMockArchivalUnit extends MockArchivalUnit {
    public void pauseBeforeFetch() {
      pauseBeforeFetchCounter++;
    }
    
    public Plugin getPlugin() {
      return new MockPlugin();
    }
  }
}
