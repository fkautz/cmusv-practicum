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

package org.lockss.plugin;

import java.io.*;
import java.util.*;
import java.math.BigInteger;
import org.lockss.daemon.*;
import org.lockss.repository.*;
import org.lockss.test.*;
import org.lockss.util.StreamUtil;

/**
 * This is the test class for
 * org.lockss.plugin.GenericFileCachedUrl.
 *
 * @author  Emil Aalto
 * @version 0.0
 */
public class TestGenericFileCachedUrl extends LockssTestCase {
  private LockssRepository repo;
  private MockGenericFileArchivalUnit mgfau;
  private MockLockssDaemon theDaemon;
  private CachedUrlSet cus;

  public void setUp() throws Exception {
    super.setUp();
    String tempDirPath = getTempDir().getAbsolutePath() + File.separator;
    TestLockssRepositoryServiceImpl.configCacheLocation(tempDirPath);

    theDaemon = new MockLockssDaemon();
    theDaemon.getHashService();
    theDaemon.getLockssRepositoryService().startService();
    theDaemon.setNodeManagerService(new MockNodeManagerService());

    mgfau = new MockGenericFileArchivalUnit();
    MockPlugin plugin = new MockPlugin();
    plugin.setDefiningConfigKeys(Collections.EMPTY_LIST);
    mgfau.setPlugin(plugin);
    repo = theDaemon.getLockssRepository(mgfau);
    theDaemon.getNodeManager(mgfau);
    CachedUrlSetSpec rSpec =
        new RangeCachedUrlSetSpec("http://www.example.com/testDir");
    cus = mgfau.makeCachedUrlSet(rSpec);
  }

  public void tearDown() throws Exception {
    theDaemon.getLockssRepositoryService().stopService();
    super.tearDown();
  }

  public void testGetUrl() throws Exception {
    createLeaf("http://www.example.com/testDir/leaf1", "test stream", null);

    CachedUrl url = cus.makeCachedUrl("http://www.example.com/testDir/leaf1");
    assertEquals("http://www.example.com/testDir/leaf1", url.getUrl());
  }

  public void testIsLeaf() throws Exception {
    createLeaf("http://www.example.com/testDir/leaf1", "test stream", null);
    createLeaf("http://www.example.com/testDir/leaf2", null, null);

    CachedUrl url = cus.makeCachedUrl("http://www.example.com/testDir/leaf1");
    assertTrue(url.isLeaf());
    url = cus.makeCachedUrl("http://www.example.com/testDir/leaf2");
    assertTrue(url.isLeaf());
  }

  public void testGetContentSize() throws Exception {
    createLeaf("http://www.example.com/testDir/leaf1", "test stream", null);
    createLeaf("http://www.example.com/testDir/leaf2", "test stream2", null);
    createLeaf("http://www.example.com/testDir/leaf3", "", null);

    CachedUrl url = cus.makeCachedUrl("http://www.example.com/testDir/leaf1");
    BigInteger bi = new BigInteger(url.getContentSize());
    assertEquals(11, bi.intValue());

    url = cus.makeCachedUrl("http://www.example.com/testDir/leaf2");
    bi = new BigInteger(url.getContentSize());
    assertEquals(12, bi.intValue());

    url = cus.makeCachedUrl("http://www.example.com/testDir/leaf3");
    bi = new BigInteger(url.getContentSize());
    assertEquals(0, bi.intValue());
  }

  public void testOpenForReading() throws Exception {
    createLeaf("http://www.example.com/testDir/leaf1", "test stream", null);
    createLeaf("http://www.example.com/testDir/leaf2", "test stream2", null);
    createLeaf("http://www.example.com/testDir/leaf3", "", null);

    CachedUrl url = cus.makeCachedUrl("http://www.example.com/testDir/leaf1");
    InputStream urlIs = url.openForReading();
    ByteArrayOutputStream baos = new ByteArrayOutputStream(11);
    StreamUtil.copy(urlIs, baos);
    assertEquals("test stream", baos.toString());

    url = cus.makeCachedUrl("http://www.example.com/testDir/leaf2");
    urlIs = url.openForReading();
    baos = new ByteArrayOutputStream(12);
    StreamUtil.copy(urlIs, baos);
    assertEquals("test stream2", baos.toString());

    url = cus.makeCachedUrl("http://www.example.com/testDir/leaf3");
    urlIs = url.openForReading();
    baos = new ByteArrayOutputStream(0);
    StreamUtil.copy(urlIs, baos);
    assertEquals("", baos.toString());
  }

  public void testOpenForHashingDefaultsToNoFiltering() throws Exception {
    createLeaf("http://www.example.com/testDir/leaf1", "<test stream>", null);

    CachedUrl url = cus.makeCachedUrl("http://www.example.com/testDir/leaf1");
    InputStream urlIs = url.openForReading();
    ByteArrayOutputStream baos = new ByteArrayOutputStream(11);
    StreamUtil.copy(urlIs, baos);
    assertEquals("<test stream>", baos.toString());
  }

  public void testOpenForHashingCanFilter() throws Exception {
    String config = "org.lockss.genericFileCachedUrl.filterHashStream=true";
    Properties props = new Properties();
    props.setProperty("content-type", "text/html");
    TestConfiguration.setCurrentConfigFromString(config);
    createLeaf("http://www.example.com/testDir/leaf1", "<test stream>", props);

    CachedUrl url = cus.makeCachedUrl("http://www.example.com/testDir/leaf1");
    InputStream urlIs = url.openForHashing();
    ByteArrayOutputStream baos = new ByteArrayOutputStream(11);
    StreamUtil.copy(urlIs, baos);
    assertEquals("", baos.toString());
  }

  public void testOpenForHashingDoesntFilterNonHtml() throws Exception {
    String config = "org.lockss.genericFileCachedUrl.filterHashStream=true";
    Properties props = new Properties();
    props.setProperty("content-type", "blah");
    TestConfiguration.setCurrentConfigFromString(config);
    createLeaf("http://www.example.com/testDir/leaf1", "<test stream>", props);

    CachedUrl url = cus.makeCachedUrl("http://www.example.com/testDir/leaf1");
    InputStream urlIs = url.openForHashing();
    ByteArrayOutputStream baos = new ByteArrayOutputStream(11);
    StreamUtil.copy(urlIs, baos);
    assertEquals("<test stream>", baos.toString());
  }

  public void testOpenForHashingWontFilterIfConfigedNotTo() throws Exception {
    String config = "org.lockss.genericFileCachedUrl.filterHashStream=false";
    TestConfiguration.setCurrentConfigFromString(config);
    createLeaf("http://www.example.com/testDir/leaf1", "<test stream>", null);

    CachedUrl url = cus.makeCachedUrl("http://www.example.com/testDir/leaf1");
    InputStream urlIs = url.openForReading();
    ByteArrayOutputStream baos = new ByteArrayOutputStream(11);
    StreamUtil.copy(urlIs, baos);
    assertEquals("<test stream>", baos.toString());
  }

  public void testGetProperties() throws Exception {
    Properties newProps = new Properties();
    newProps.setProperty("test", "value");
    newProps.setProperty("test2", "value2");
    createLeaf("http://www.example.com/testDir/leaf1", null, newProps);

    CachedUrl url = cus.makeCachedUrl("http://www.example.com/testDir/leaf1");
    Properties urlProps = url.getProperties();
    assertEquals("value", urlProps.getProperty("test"));
    assertEquals("value2", urlProps.getProperty("test2"));
  }

   public void testGetReader() throws Exception {
    createLeaf("http://www.example.com/testDir/leaf1", "test stream", null);
  
    CachedUrl cu = cus.makeCachedUrl("http://www.example.com/testDir/leaf1");
    Reader reader = cu.getReader();
    CharArrayWriter writer = new CharArrayWriter(11);
    StreamUtil.copy(reader, writer);
    assertEquals("test stream", writer.toString());
  }

  private RepositoryNode createLeaf(String url, String content,
                                    Properties props) throws Exception {
    return TestRepositoryNodeImpl.createLeaf(repo, url, content, props);
  }

  public static void main(String[] argv) {
    String[] testCaseList = {TestGenericFileCachedUrl.class.getName()};
    junit.swingui.TestRunner.main(testCaseList);
  }
}
