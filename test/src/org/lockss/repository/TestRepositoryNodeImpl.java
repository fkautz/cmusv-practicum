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

package org.lockss.repository;

import java.io.*;
import java.util.*;
import org.lockss.test.*;
import org.lockss.util.StreamUtil;

/**
 * This is the test class for org.lockss.repostiory.RepositoryNodeImpl
 */
public class TestRepositoryNodeImpl extends LockssTestCase {
  private LockssRepository repo;

  public TestRepositoryNodeImpl(String msg) {
    super(msg);
  }

  public void setUp() throws Exception {
    super.setUp();
    String tempDirPath = "";
    try {
      tempDirPath = super.getTempDir().getAbsolutePath() + File.separator;
    } catch (Exception e) { fail("Couldn't get tempDir."); }
    TestLockssRepositoryImpl.configCacheLocation(tempDirPath);
    MockArchivalUnit mau = new MockArchivalUnit(null);
    repo = LockssRepositoryImpl.repositoryFactory(mau);
  }

  public void testGetNodeUrl() {
    RepositoryNode node = new RepositoryNodeImpl("testUrl", "testDir", null);
    assertEquals("testUrl", node.getNodeUrl());
    node = new RepositoryNodeImpl("testUrl/test.txt", "testUrl/test.txt", null);
    assertEquals("testUrl/test.txt", node.getNodeUrl());
  }

  public void testGetState() {
    //XXX implement
  }

  public void testStoreState() {
    //XXX implement
  }

  public void testListEntries() throws Exception {
    createLeaf("http://www.example.com/testDir/branch1/leaf1",
               "test stream", null);
    createLeaf("http://www.example.com/testDir/branch1/leaf2",
               "test stream", null);
    createLeaf("http://www.example.com/testDir/branch2/leaf3",
               "test stream", null);
    createLeaf("http://www.example.com/testDir/leaf4", "test stream", null);

    RepositoryNode dirEntry =
        repo.getRepositoryNode("http://www.example.com/testDir");
    Iterator childIt = dirEntry.listNodes(null);
    ArrayList childL = new ArrayList(3);
    while (childIt.hasNext()) {
      RepositoryNode node = (RepositoryNode)childIt.next();
      childL.add(node.getNodeUrl());
    }
    String[] expectedA = new String[] {
      "http://www.example.com/testDir/branch1",
      "http://www.example.com/testDir/branch2",
      "http://www.example.com/testDir/leaf4"
      };
    assertIsomorphic(expectedA, childL);

    dirEntry = repo.getRepositoryNode("http://www.example.com/testDir/branch1");
    childL.clear();
    childIt = dirEntry.listNodes(null);
    while (childIt.hasNext()) {
      RepositoryNode node = (RepositoryNode)childIt.next();
      childL.add(node.getNodeUrl());
    }
    expectedA = new String[] {
      "http://www.example.com/testDir/branch1/leaf1",
      "http://www.example.com/testDir/branch1/leaf2",
      };
    assertIsomorphic(expectedA, childL);
  }

  public void testEntrySort() throws Exception {
    createLeaf("http://www.example.com/testDir/branch2/leaf1", null, null);
    createLeaf("http://www.example.com/testDir/leaf4", null, null);
    createLeaf("http://www.example.com/testDir/branch1/leaf1", null, null);
    createLeaf("http://www.example.com/testDir/leaf3", null, null);

    RepositoryNode dirEntry =
        repo.getRepositoryNode("http://www.example.com/testDir");
    Iterator childIt = dirEntry.listNodes(null);
    ArrayList childL = new ArrayList(4);
    while (childIt.hasNext()) {
      RepositoryNode node = (RepositoryNode)childIt.next();
      childL.add(node.getNodeUrl());
    }
    String[] expectedA = new String[] {
      "http://www.example.com/testDir/branch1",
      "http://www.example.com/testDir/branch2",
      "http://www.example.com/testDir/leaf3",
      "http://www.example.com/testDir/leaf4"
      };
    assertIsomorphic(expectedA, childL);
  }

  public void testMakeNewCache() throws Exception {
    RepositoryNode leaf =
      repo.createNewNode("http://www.example.com/testDir/test.cache");
    assertTrue(!leaf.hasContent());
    assertEquals(0, leaf.getCurrentVersion());
    leaf.makeNewVersion();
    writeToLeaf(leaf, "test stream");
    leaf.sealNewVersion();
    assertEquals(1, leaf.getCurrentVersion());
    assertTrue(leaf.hasContent());
  }

  public void testMakeNewVersion() throws Exception {
    Properties props = new Properties();
    props.setProperty("test 1", "value 1");
    RepositoryNode leaf =
        createLeaf("http://www.example.com/testDir/test.cache",
        "test stream 1", props);

    assertEquals(1, leaf.getCurrentVersion());
    leaf.makeNewVersion();
    props = new Properties();
    props.setProperty("test 1", "value 2");
    leaf.setNewProperties(props);
    writeToLeaf(leaf, "test stream 2");
    leaf.sealNewVersion();
    assertEquals(2, leaf.getCurrentVersion());

    String resultStr = getLeafContent(leaf);
    assertEquals("test stream 2", resultStr);
    props = leaf.getProperties();
    assertEquals("value 2", props.getProperty("test 1"));
  }

  public void testGetInputStream() throws Exception {
    RepositoryNode leaf =
        createLeaf("http://www.example.com/testDir/test.cache",
        "test stream", null);
    String resultStr = getLeafContent(leaf);
    assertEquals("test stream", resultStr);
  }

  public void testGetProperties() throws Exception {
    Properties props = new Properties();
    props.setProperty("test 1", "value 1");
    RepositoryNode leaf =
        createLeaf("http://www.example.com/testDir/test.cache",
        "test stream", props);

    props = leaf.getProperties();
    assertEquals("value 1", props.getProperty("test 1"));

    leaf.makeNewVersion();
    props = new Properties();
    props.setProperty("test 1", "value 2");
    leaf.setNewProperties(props);
    writeToLeaf(leaf, "test stream 2");
    leaf.sealNewVersion();

    props = leaf.getProperties();
    assertEquals("value 2", props.getProperty("test 1"));
  }

  public void testDirContent() throws Exception {
    RepositoryNode leaf =
        createLeaf("http://www.example.com/testDir/test.cache",
        "test stream", null);
    assertTrue(leaf.hasContent());

    RepositoryNode dir =
        repo.getRepositoryNode("http://www.example.com/testDir");
    dir.makeNewVersion();
    writeToLeaf(dir, "test stream");
    dir.sealNewVersion();
    assertTrue(dir.hasContent());

    dir = createLeaf("http://www.example.com/testDir/test.cache/new.test",
                     "test stream", null);
    assertTrue(dir.hasContent());
  }

  private RepositoryNode createLeaf(String url, String content,
                                    Properties props) throws Exception {
    return createLeaf(repo, url, content, props);
  }

  public static RepositoryNode createLeaf(LockssRepository repo, String url,
                                   String content, Properties props)
      throws Exception {
    RepositoryNode leaf = repo.createNewNode(url);
    leaf.makeNewVersion();
    writeToLeaf(leaf, content);
    if (props!=null) {
      leaf.setNewProperties(props);
    }
    leaf.sealNewVersion();
    return leaf;
  }

  public static void writeToLeaf(RepositoryNode leaf, String content)
      throws Exception {
    if (content!=null) {
      OutputStream os = leaf.getNewOutputStream();
      InputStream is = new StringInputStream(content);
      StreamUtil.copy(is, os);
      os.close();
      is.close();
    }
  }

  public static String getLeafContent(RepositoryNode leaf) throws IOException {
    InputStream is = leaf.getInputStream();
    OutputStream baos = new ByteArrayOutputStream(20);
    StreamUtil.copy(is, baos);
    is.close();
    String resultStr = baos.toString();
    baos.close();
    return resultStr;
  }
}
