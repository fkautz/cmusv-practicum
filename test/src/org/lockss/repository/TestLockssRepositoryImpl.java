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

package org.lockss.repository;

import java.io.*;
import java.util.*;
import java.net.*;
import org.lockss.test.*;
import org.lockss.util.*;
import org.lockss.plugin.*;
import org.lockss.daemon.*;

/**
 * This is the test class for org.lockss.daemon.LockssRepositoryImpl
 */

public class TestLockssRepositoryImpl extends LockssTestCase {
  private LockssRepositoryImpl repo;
  private MockArchivalUnit mau;
  private String tempDirPath;

  public TestLockssRepositoryImpl(String msg) {
    super(msg);
  }

  public void setUp() throws Exception {
    super.setUp();
    tempDirPath = getTempDir().getAbsolutePath() + File.separator;
    configCacheLocation(tempDirPath);
    mau = new MockArchivalUnit();
    repo = (LockssRepositoryImpl)LockssRepositoryImpl.createNewLockssRepository(
        mau);
  }

  public void tearDown() throws Exception {
    repo.stopService();
    super.tearDown();
  }

  public void testFileLocation() throws Exception {
    String cachePath = LockssRepositoryImpl.mapAuToFileLocation(
        LockssRepositoryImpl.extendCacheLocation(tempDirPath),
        mau);
    File testFile = new File(cachePath);

    createLeaf("http://www.example.com/testDir/branch1/leaf1",
               "test stream", null);
    assertTrue(testFile.exists());
    cachePath += "www.example.com/http/";
    testFile = new File(cachePath);
    assertTrue(testFile.exists());
    cachePath += "testDir/branch1/leaf1/";
    testFile = new File(cachePath);
    assertTrue(testFile.exists());
  }

  public void testGetRepositoryNode() throws Exception {
    createLeaf("http://www.example.com/testDir/branch1/leaf1",
               "test stream", null);
    createLeaf("http://www.example.com/testDir/branch1/leaf2",
               "test stream", null);
    createLeaf("http://www.example.com/testDir/branch2/leaf3",
               "test stream", null);
    createLeaf("http://www.example.com/testDir/leaf4", "test stream", null);

    RepositoryNode node = repo.getNode("http://www.example.com/testDir");
    assertFalse(node.hasContent());
    assertEquals("http://www.example.com/testDir", node.getNodeUrl());
    node = repo.getNode("http://www.example.com/testDir/branch1");
    assertFalse(node.hasContent());
    assertEquals("http://www.example.com/testDir/branch1", node.getNodeUrl());
    node = repo.getNode("http://www.example.com/testDir/branch2/leaf3");
    assertTrue(node.hasContent());
    assertEquals("http://www.example.com/testDir/branch2/leaf3",
                 node.getNodeUrl());
    node = repo.getNode("http://www.example.com/testDir/leaf4");
    assertTrue(node.hasContent());
    assertEquals("http://www.example.com/testDir/leaf4", node.getNodeUrl());
  }

  public void testDotUrlHandling() throws Exception {
    //testing correction of nodes with bad '..'-including urls,
    //filtering the first '..' but resolving the second
    RepositoryNode node = repo.createNewNode(
        "http://www.example.com/branch/test/../test2");
    assertEquals("http://www.example.com/branch/test2", node.getNodeUrl());

    //remove single '.' references
    node = repo.createNewNode(
        "http://www.example.com/branch/./test/");
    assertEquals("http://www.example.com/branch/test", node.getNodeUrl());

    try {
      node = repo.createNewNode("http://www.example.com/..");
      fail("Should have thrown MalformedURLException.");
    } catch (MalformedURLException mue) { }
    try {
      node = repo.createNewNode("http://www.example.com/test/../../test2");
      fail("Should have thrown MalformedURLException.");
    } catch (MalformedURLException mue) { }
  }

  public void testGetAuNode() throws Exception {
    createLeaf("http://www.example.com/testDir1/leaf1", "test stream", null);
    createLeaf("http://www.example.com/testDir2/leaf2", "test stream", null);
    createLeaf("http://image.example.com/testDir3/leaf3", "test stream", null);
    createLeaf("ftp://www.example.com/file", "test stream", null);

    RepositoryNode auNode = repo.getNode(AuUrl.PROTOCOL_COLON+"//www.example.com");
    assertFalse(auNode.hasContent());
    assertEquals(AuUrl.PROTOCOL +"://www.example.com", auNode.getNodeUrl());
    Iterator childIt = auNode.listNodes(null, false);
    ArrayList childL = new ArrayList(3);
    while (childIt.hasNext()) {
      RepositoryNode node = (RepositoryNode)childIt.next();
      childL.add(node.getNodeUrl());
    }
    String[] expectedA = new String[] {
      "ftp://www.example.com",
      "http://image.example.com",
      "http://www.example.com",
      };
    assertIsomorphic(expectedA, childL);
  }

  public void testDeleteNode() throws Exception {
    createLeaf("http://www.example.com/test1", "test stream", null);

    RepositoryNode node = repo.getNode("http://www.example.com/test1");
    assertTrue(node.hasContent());
    assertFalse(node.isDeleted());
    repo.deleteNode("http://www.example.com/test1");
    assertFalse(node.hasContent());
    assertTrue(node.isDeleted());
  }

  public void testDeactivateNode() throws Exception {
    createLeaf("http://www.example.com/test1", "test stream", null);

    RepositoryNode node = repo.getNode("http://www.example.com/test1");
    assertTrue(node.hasContent());
    assertFalse(node.isInactive());
    repo.deactivateNode("http://www.example.com/test1");
    assertFalse(node.hasContent());
    assertTrue(node.isInactive());
  }

  public void testCaching() throws Exception {
    createLeaf("http://www.example.com/testDir/leaf1", null, null);
    createLeaf("http://www.example.com/testDir/leaf2", null, null);

    LockssRepositoryImpl repoImpl = (LockssRepositoryImpl)repo;
    assertEquals(0, repoImpl.getCacheHits());
    assertEquals(2, repoImpl.getCacheMisses());
    RepositoryNode leaf = repo.getNode("http://www.example.com/testDir/leaf1");
    assertEquals(1, repoImpl.getCacheHits());
    RepositoryNode leaf2 = repo.getNode("http://www.example.com/testDir/leaf1");
    assertSame(leaf, leaf2);
    assertEquals(2, repoImpl.getCacheHits());
    assertEquals(2, repoImpl.getCacheMisses());
  }

  public void testWeakReferenceCaching() throws Exception {
    createLeaf("http://www.example.com/testDir/leaf1", null, null);

    LockssRepositoryImpl repoImpl = (LockssRepositoryImpl)repo;
    RepositoryNode leaf = repo.getNode("http://www.example.com/testDir/leaf1");
    RepositoryNode leaf2 = null;
    int loopSize = 1;
    int refHits = 0;
    // create leafs in a loop until fetching an leaf1 creates a cache miss
    while (true) {
      loopSize *= 2;
      for (int ii=0; ii<loopSize; ii++) {
        createLeaf("http://www.example.com/testDir/testleaf"+ii, null, null);
      }
      int misses = repoImpl.getCacheMisses();
      refHits = repoImpl.getRefHits();
      leaf2 = repo.getNode("http://www.example.com/testDir/leaf1");
      if (repoImpl.getCacheMisses() == misses+1) {
        break;
      }
    }
    assertSame(leaf, leaf2);
    assertEquals(refHits+1, repoImpl.getRefHits());
  }

  public void testCusCompare() throws Exception {
    CachedUrlSetSpec spec1 =
        new RangeCachedUrlSetSpec("http://www.example.com/test");
    CachedUrlSetSpec spec2 =
        new RangeCachedUrlSetSpec("http://www.example.com");
    MockCachedUrlSet cus1 = new MockCachedUrlSet(mau, spec1);
    MockCachedUrlSet cus2 = new MockCachedUrlSet(mau, spec2);
    assertEquals(LockssRepository.BELOW, repo.cusCompare(cus1, cus2));

    spec1 = new RangeCachedUrlSetSpec("http://www.example.com/test");
    spec2 = new RangeCachedUrlSetSpec("http://www.example.com/test/subdir");
    cus1 = new MockCachedUrlSet(mau, spec1);
    cus2 = new MockCachedUrlSet(mau, spec2);
    assertEquals(LockssRepository.ABOVE, repo.cusCompare(cus1, cus2));

    spec1 = new RangeCachedUrlSetSpec("http://www.example.com/test", "/a", "/b");
    spec2 = new RangeCachedUrlSetSpec("http://www.example.com/test", "/c", "/d");
    cus1 = new MockCachedUrlSet(mau, spec1);
    cus2 = new MockCachedUrlSet(mau, spec2);
    assertEquals(LockssRepository.SAME_LEVEL_NO_OVERLAP,
                 repo.cusCompare(cus1, cus2));

    spec2 = new RangeCachedUrlSetSpec("http://www.example.com/test", "/b", "/d");
    cus2 = new MockCachedUrlSet(mau, spec2);
    assertEquals(LockssRepository.SAME_LEVEL_OVERLAP,
                 repo.cusCompare(cus1, cus2));

    spec1 = new RangeCachedUrlSetSpec("http://www.example.com/test/subdir2");
    spec2 = new RangeCachedUrlSetSpec("http://www.example.com/subdir");
    cus1 = new MockCachedUrlSet(mau, spec1);
    cus2 = new MockCachedUrlSet(mau, spec2);
    assertEquals(LockssRepository.NO_RELATION, repo.cusCompare(cus1, cus2));

    // test for single node specs
    spec1 = new SingleNodeCachedUrlSetSpec("http://www.example.com");
    spec2 = new RangeCachedUrlSetSpec("http://www.example.com/test");
    cus1 = new MockCachedUrlSet(mau, spec1);
    cus2 = new MockCachedUrlSet(mau, spec2);
    assertEquals(LockssRepository.SAME_LEVEL_NO_OVERLAP, repo.cusCompare(cus1, cus2));
    // reverse
    assertEquals(LockssRepository.SAME_LEVEL_NO_OVERLAP, repo.cusCompare(cus2, cus1));

    // test for Au urls
    spec1 = new AUCachedUrlSetSpec();
    spec2 = new AUCachedUrlSetSpec();
    cus1 = new MockCachedUrlSet(mau, spec1);
    cus2 = new MockCachedUrlSet(mau, spec2);
    assertEquals(LockssRepository.SAME_LEVEL_OVERLAP, repo.cusCompare(cus1, cus2));

    spec2 = new RangeCachedUrlSetSpec("http://www.example.com");
    cus2 = new MockCachedUrlSet(mau, spec2);
    assertEquals(LockssRepository.ABOVE, repo.cusCompare(cus1, cus2));
    // reverse
    assertEquals(LockssRepository.BELOW, repo.cusCompare(cus2, cus1));

    // test for different AUs
    spec1 = new RangeCachedUrlSetSpec("http://www.example.com");
    spec2 = new RangeCachedUrlSetSpec("http://www.example.com");
    cus1 = new MockCachedUrlSet(mau, spec1);
    cus2 = new MockCachedUrlSet(new MockArchivalUnit(), spec2);
    assertEquals(LockssRepository.NO_RELATION, repo.cusCompare(cus1, cus2));

    // test for exclusive ranges
    spec1 = new RangeCachedUrlSetSpec("http://www.example.com", "/abc", "/xyz");
    spec2 = new RangeCachedUrlSetSpec("http://www.example.com/test");
    cus1 = new MockCachedUrlSet(mau, spec1);
    cus2 = new MockCachedUrlSet(mau, spec2);
    // this range is inclusive, so should be parent
    assertEquals(LockssRepository.ABOVE, repo.cusCompare(cus1, cus2));
    assertEquals(LockssRepository.BELOW, repo.cusCompare(cus2, cus1));
    spec1 = new RangeCachedUrlSetSpec("http://www.example.com", "/abc", "/mno");
    cus1 = new MockCachedUrlSet(mau, spec1);
    // this range is exclusive, so should be no relation
    assertEquals(LockssRepository.NO_RELATION, repo.cusCompare(cus1, cus2));
    // reverse
    assertEquals(LockssRepository.NO_RELATION, repo.cusCompare(cus2, cus1));
  }

  public void testConsistencyCheck() throws Exception {
    createLeaf("http://www.example.com/testDir/leaf1", "test stream", null);

    RepositoryNodeImpl leaf = (RepositoryNodeImpl)
        repo.getNode("http://www.example.com/testDir/leaf1");
    assertTrue(leaf.hasContent());

    // delete content directory
    leaf.currentCacheFile.delete();
    // version still indicates content
    assertEquals(1, leaf.getCurrentVersion());

    try {
      leaf.getNodeContents();
      fail("Should have thrown state exception.");
    } catch (LockssRepository.RepositoryStateException rse) { }

    assertTrue(leaf.cacheLocationFile.exists());
    assertEquals(RepositoryNodeImpl.INACTIVE_VERSION, leaf.getCurrentVersion());
    assertFalse(leaf.hasContent());
  }

  // test static naming calls

  public void testAuKey() {
    String expectedStr = mau.getAUId();
    assertEquals(expectedStr, LockssRepositoryImpl.getAuKey(mau));
  }

  public void testGetNewPluginDir() {
    // call this to 'reblank' after the effects of setUp()
    repo.stopService();

    // should start with the char before 'a'
    assertEquals(""+(char)('a'-1), LockssRepositoryImpl.lastPluginDir);
    LockssRepositoryImpl.getNewPluginDir();
    assertEquals("a", LockssRepositoryImpl.lastPluginDir);
    LockssRepositoryImpl.getNewPluginDir();
    assertEquals("b", LockssRepositoryImpl.lastPluginDir);

    LockssRepositoryImpl.lastPluginDir = "z";
    LockssRepositoryImpl.getNewPluginDir();
    assertEquals("aa", LockssRepositoryImpl.lastPluginDir);
    LockssRepositoryImpl.getNewPluginDir();
    assertEquals("ab", LockssRepositoryImpl.lastPluginDir);
    LockssRepositoryImpl.lastPluginDir = "az";
    LockssRepositoryImpl.getNewPluginDir();
    assertEquals("ba", LockssRepositoryImpl.lastPluginDir);
    LockssRepositoryImpl.lastPluginDir = "czz";
    LockssRepositoryImpl.getNewPluginDir();
    assertEquals("daa", LockssRepositoryImpl.lastPluginDir);

    LockssRepositoryImpl.lastPluginDir = ""+ (char)('a'-1);
  }

  public void testGetAuDirFromMap() {
    HashMap newNameMap = new HashMap();
    newNameMap.put(LockssRepositoryImpl.getAuKey(mau), "testDir");
    LockssRepositoryImpl.nameMap = newNameMap;
    StringBuffer buffer = new StringBuffer();
    LockssRepositoryImpl.getAuDir(mau, buffer);
    assertEquals("testDir", buffer.toString());
  }

  public void testSaveAndLoadNames() {
    Properties newProps = new Properties();
    newProps.setProperty(LockssRepositoryImpl.AU_ID_PROP, mau.getAUId());

    HashMap newNameMap = new HashMap();
    newNameMap.put(LockssRepositoryImpl.getAuKey(mau), "testDir");
    LockssRepositoryImpl.nameMap = newNameMap;
    String location = LockssRepositoryImpl.mapAuToFileLocation(
        LockssRepositoryImpl.cacheLocation, mau);

    LockssRepositoryImpl.saveAuIdProperties(location, newProps);
    File idFile = new File(location + LockssRepositoryImpl.AU_ID_FILE);
    assertTrue(idFile.exists());

    newProps = LockssRepositoryImpl.getAuIdProperties(location);
    assertNotNull(newProps);
    assertEquals(mau.getAUId(),
                 newProps.getProperty(LockssRepositoryImpl.AU_ID_PROP));
  }

  public void testLoadNameMap() {
    Properties newProps = new Properties();
    newProps.setProperty(LockssRepositoryImpl.AU_ID_PROP, mau.getAUId());
    String location = LockssRepositoryImpl.cacheLocation + "ab";
    LockssRepositoryImpl.saveAuIdProperties(location, newProps);

    LockssRepositoryImpl.loadNameMap(LockssRepositoryImpl.cacheLocation);
    assertEquals("ab", repo.nameMap.get(LockssRepositoryImpl.getAuKey(mau)));
  }

  public void testLoadNameMapSkipping() {
    // clear the prop file from setUp()
    String propsLoc = LockssRepositoryImpl.cacheLocation + "a" + File.separator +
        LockssRepositoryImpl.AU_ID_FILE;
    File propsFile = new File(propsLoc);
    propsFile.delete();

    LockssRepositoryImpl.loadNameMap(LockssRepositoryImpl.cacheLocation);
    assertNull(LockssRepositoryImpl.nameMap.get(
        LockssRepositoryImpl.getAuKey(mau)));
  }

  public void testMapAuToFileLocation() {
    LockssRepositoryImpl.lastPluginDir = "ca";
    String expectedStr = LockssRepositoryImpl.cacheLocation + "root/cb/";
    assertEquals(expectedStr, LockssRepositoryImpl.mapAuToFileLocation(
        LockssRepositoryImpl.cacheLocation+"root", new MockArchivalUnit()));
  }

  public void testGetAuDirSkipping() {
    String location = LockssRepositoryImpl.cacheLocation + "root/ab";
    File dirFile = new File(location);
    dirFile.mkdirs();

    LockssRepositoryImpl.lastPluginDir = "aa";
    String expectedStr = LockssRepositoryImpl.cacheLocation + "root/ac/";
    assertEquals(expectedStr, LockssRepositoryImpl.mapAuToFileLocation(
        LockssRepositoryImpl.cacheLocation+"root", new MockArchivalUnit()));
  }

  public void testMapUrlToFileLocation() throws MalformedURLException {
    String testStr = "http://www.example.com/branch1/branch2/index.html";
    String expectedStr = "root/www.example.com/http/branch1/branch2/index.html";
    assertEquals(expectedStr, LockssRepositoryImpl.mapUrlToFileLocation("root",
        testStr));

    testStr = "hTTp://www.exaMPLE.com/branch1/branch2/index.html";
    expectedStr = "root/www.example.com/http/branch1/branch2/index.html";
    assertEquals(expectedStr, LockssRepositoryImpl.mapUrlToFileLocation("root",
        testStr));

    try {
      testStr = ":/brokenurl.com/branch1/index/";
      LockssRepositoryImpl.mapUrlToFileLocation("root", testStr);
      fail("Should have thrown MalformedURLException");
    } catch (MalformedURLException mue) {}
  }

/* XXX should be testing Daemon

  public void testGetLockssRepository() {
    String auId = mau.getAUId();
    try {
      repo.getLockssRepository(mau);
      fail("Should throw IllegalArgumentException.");
    } catch (IllegalArgumentException iae) { }

    repo.addLockssRepository(mau);
    LockssRepository repo1 = repo.getLockssRepository(mau);
    assertNotNull(repo1);

    mau = new MockArchivalUnit();
    repo.addLockssRepository(mau);
    LockssRepository repo2 = repo.getLockssRepository(mau);
    assertNotSame(repo1, repo2);

    mau = new MockArchivalUnit();
    try {
      repo.getLockssRepository(mau);
      fail("Should throw IllegalArgumentException.");
    } catch (IllegalArgumentException iae) { }
  }

*/

  public static void configCacheLocation(String location) throws IOException {
    String s = LockssRepositoryImpl.PARAM_CACHE_LOCATION + "=" + location;
    TestConfiguration.setCurrentConfigFromString(s);
  }

  private RepositoryNode createLeaf(String url, String content,
                                    Properties props) throws Exception {
    return TestRepositoryNodeImpl.createLeaf(repo, url, content, props);
  }

  public static void main(String[] argv) {
    String[] testCaseList = { TestLockssRepositoryImpl.class.getName()};
    junit.swingui.TestRunner.main(testCaseList);
  }

}
