/*
 * $Id: TestTdbAu.java,v 1.5 2011/03/22 12:58:51 pgust Exp $
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

package org.lockss.config;

import org.lockss.util.*;
import org.lockss.config.Tdb.TdbException;
import org.lockss.test.*;

import java.util.*;

/**
 * Test class for <code>org.lockss.config.TdbAu</code>
 *
 * @author  Philip Gust
 * @version $Id: TestTdbAu.java,v 1.5 2011/03/22 12:58:51 pgust Exp $
 */

public class TestTdbAu extends LockssTestCase {

  public void setUp() throws Exception {
    super.setUp();
  }

  public void tearDown() throws Exception {
    super.tearDown();
  }

  static Logger log = Logger.getLogger("TestTdbAu");

  /**
   * Test creating valid TdbAu.
   */
  public void testValidAu() {
    TdbAu au = null;
    try {
      au = new TdbAu("Test Au", "Test Plugin");
    } catch (IllegalArgumentException ex) {
      fail("Unexpected xception creating TdbAu", ex);
    }
    assertNotNull(au);
    assertEquals("Test Au", au.getName());
  }

  /**
   * Test creating TdbPublisher with null name.
   */
  public void testNullTitleName() {
    TdbPublisher publisher = null;
    try {
      publisher = new TdbPublisher(null);
      fail("TdbPublisher did not throw IllegalArgumentException for null argument.");
    } catch (IllegalArgumentException ex) {
      
    }
    assertNull(publisher);
  }
  
  /**
   * Test equals() method
   * @throws TdbException for invalid Tdb operations
   */
  public void testEquals() throws TdbException{
    TdbAu au1 = new TdbAu("Test AU", "pluginA");
    au1.setParam("name1", "val1");
    au1.setParam("name2", "val2");
    assertEquals(au1, au1);
    
    // same as title1
    TdbAu au2 = new TdbAu("Test AU", "pluginA");
    au2.setParam("name1", "val1");
    au2.setParam("name2", "val2");
    assertEquals(au1, au2);

    // differs from title1 only by au param
    TdbAu au3 = new TdbAu("Test AU", "pluginA");
    au3.setParam("name1", "val1");
    au3.setParam("name2", "val3");
    assertNotEquals(au1, au3);
    
    // differs from title3 only by plugin id 
    TdbAu au4 = new TdbAu("Test AU", "pluginB");
    au4.setParam("name1", "val1");
    au4.setParam("name2", "val3");
    assertNotEquals(au3, au4);
  }
  
  /**
   * Test addAu() method.
   * @throws TdbException for invalid Tdb operations
   */
  public void testGetTitle() throws TdbException {
    TdbTitle title = new TdbTitle("Test Title", "0000-0000");
    
    TdbAu au = new TdbAu("Test AU", "pluginA");
    title.addTdbAu(au);
    Collection<TdbAu> aus = title.getTdbAus();
    assertEquals(1, aus.size());
    assertTrue(aus.contains(au));
    
    // get title
    TdbTitle getTitle = au.getTdbTitle();
    assertSame(title, getTitle);
  }
  
  /**
   * Test year operations
   * @throws TdbException for invalid Tdb operations
   */
  public void testYear() throws TdbException {
    TdbAu au = new TdbAu("Test AU", "pluginA");
    assertNull(au.getYear());
    assertNull(au.getStartYear());
    assertNull(au.getEndYear());
    
    au.setParam("year", "1971");
    assertEquals("1971", au.getYear());
    assertEquals("1971", au.getStartYear());
    assertEquals("1971", au.getEndYear());
    assertTrue(au.includesYear("1971"));
    assertFalse(au.includesYear("1970"));

    au = new TdbAu("TestAU", "pluginA");
    au.setAttr("year", "1971-1977");
    assertEquals("1971-1977", au.getYear());
    assertEquals("1971", au.getStartYear());
    assertEquals("1977", au.getEndYear());
    assertTrue(au.includesYear("1971"));
    assertFalse(au.includesYear("1970"));
    assertFalse(au.includesYear("1979"));
  }
  
  /**
   * Test volumes operations
   * @throws TdbException for invalid Tdb operations
   */
  public void testVolumes() throws TdbException {
    TdbAu au = new TdbAu("Test AU", "pluginA");
    assertNull(au.getVolume());
    assertNull(au.getStartVolume());
    assertNull(au.getEndVolume());
    
    au.setParam("volume", "1971");
    assertEquals("1971", au.getVolume());
    assertEquals("1971", au.getStartVolume());
    assertEquals("1971", au.getEndVolume());
    assertTrue(au.includesVolume("1971"));
    assertFalse(au.includesVolume("1970"));

    au = new TdbAu("TestAU", "pluginA");
    au.setAttr("volume", "1971-1977");
    assertEquals("1971-1977", au.getVolume());
    assertEquals("1971", au.getStartVolume());
    assertEquals("1977", au.getEndVolume());
    assertTrue(au.includesVolume("1971"));
    assertFalse(au.includesVolume("1970"));
    assertFalse(au.includesVolume("1979"));
  }
  
  /**
   * Test ISBNs and ISBNs
   * @throws TdbException for invalid Tdb operations
   */
  public void testIssns() throws TdbException {
    TdbAu au = new TdbAu("Test AU", "pluginA");
    au.setPropertyByName("issn", "1234-5678");
    au.setPropertyByName("eissn", "2468-1357");
    au.setPropertyByName("issnl", "8765-4321");
    au.setPropertyByName("isbn", "1234567890");
    assertEquals("1234-5678", au.getPrintIssn());
    assertEquals("2468-1357", au.getEissn());
    assertEquals("8765-4321", au.getIssnL());
    assertNotNull(au.getIssn());
    assertEquals("1234567890", au.getIsbn());
  }
  
  /**
   * Test getPublisher() method.
   * @throws TdbException for invalid Tdb operations
   */
  public void testGetPublisher() throws TdbException {
    TdbPublisher publisher = new TdbPublisher("Test Publisher");
    Collection<TdbTitle> titles = publisher.getTdbTitles();
    assertEmpty(titles);
    
    // add title
    TdbTitle title = new TdbTitle("Test Title", "0000-0000");
    publisher.addTdbTitle(title);
    
    // add au
    TdbAu au = new TdbAu("Test AU", "pluginA");
    title.addTdbAu(au);
    
    // ensure same as publisher for AU's title
    TdbPublisher getPublisher = au.getTdbPublisher();
    assertEquals(publisher, getPublisher);
  }

  /**
   * Test convenience methods.
   * @throws TdbException for invalid Tdb operations
   */
  public void testConvenienceMethods() throws TdbException {
    TdbPublisher publisher = new TdbPublisher("Test Publisher");
    Collection<TdbTitle> titles = publisher.getTdbTitles();
    assertEmpty(titles);
    
    // add title
    TdbTitle title = new TdbTitle("Test Title", "1234-5678");
    publisher.addTdbTitle(title);

    // add au
    TdbAu au = new TdbAu("Test AU", "org.lockss.TestPlugin");
    au.setPluginVersion("7");
    au.setPropertyByName("estSize", "32.5MB");
    au.setParam("p1", "v1");
    au.setParam("p2", "v2");
    title.addTdbAu(au);
    assertEquals("7", au.getPropertyByName("pluginVersion"));
    assertEquals("Test Title", au.getJournalTitle()); 
    assertEquals(32500000, au.getEstimatedSize());
    Properties props = au.toProperties();
    assertEquals("v1", props.getProperty("param.1.value"));
    /*
    assertEquals("32.5MB", props.getProperty("estSize"));
    */
  }
  /**
   * Test param methods
   * @throws TdbException for invalid Tdb operations
   */
  public void testParams() throws TdbException {
    // set two params
    TdbAu au = new TdbAu("Test AU", "pluginA");
    au.setParam("name1", "val1");
    au.setParam("name2", "val2");
    au.setParam("name3", "val3");
    
    // set invalid params
    try {
      au.setParam("name4", null);
      fail("TdbAu did not throw IllegalArgumentException for null param argument.");
    } catch (IllegalArgumentException ex) {
    }
    
    // ensure params contain expected names and values
    Map<String,String> getParams = au.getParams();
    assertNotNull(getParams);
    assertEquals(3, getParams.size());
    assertEquals("val1", getParams.get("name1"));
    assertEquals("val2", getParams.get("name2"));
    assertEquals("val3", getParams.get("name3"));
  
    // ensure param cannot be reset
    try {
      au.setParam("name2", "newval2");
      fail("TdbAu did not throw TdbException resetting param.");
    } catch (TdbException ex) {
    }
    assertNotNull(getParams);
    assertEquals(3, getParams.size());
    getParams = au.getParams();
    assertEquals("val2", getParams.get("name2"));
    
    TdbTitle title = new TdbTitle("Test Title", "0000-0000");
    title.addTdbAu(au);
    try {
      au.setParam("name4", "newval4");
      fail("TdbAu did not throw TdbException setting param once added to title.");
    } catch (TdbException ex) {
    }
    assertNotNull(getParams);
    assertEquals(3, getParams.size());
    getParams = au.getParams();
    assertNull(getParams.get("name4"));
  }

  /**
   * Test attr methods
   */
  public void testAttrs() throws TdbException {
    // set two attrs
    TdbAu au = new TdbAu("Test AU", "Test ID");
    au.setAttr("name1", "val1");
    au.setAttr("name2", "val2");
    
    // set invalid params
    try {
      au.setAttr("name3", null);
      fail("TdbAu did not throw IllegalArgumentException for null attr argument.");
    } catch (IllegalArgumentException ex) {
    }
    
    // ensure params contain expected names and values
    Map<String,String> getAttrs = au.getAttrs();
    assertNotNull(getAttrs);
    assertEquals(2, getAttrs.size());
    assertEquals("val1", getAttrs.get("name1"));
    assertEquals("val2", getAttrs.get("name2"));
  
    // ensure attr cannot be reset
    try {
      au.setAttr("name2", "newval2");
      fail("TdbAu did not throw TdbException resetting attr.");
    } catch (TdbException ex) {
    }
    assertNotNull(getAttrs);
    assertEquals(2, getAttrs.size());
    getAttrs = au.getAttrs();
    assertEquals("val2", getAttrs.get("name2"));
  }
  
  /**
   * Test TdbAu.Id
   * @throws TdbException for invalid Tdb operations
   */
  public void testTdbAuId() throws TdbException {
    TdbAu au1 = new TdbAu("Test AU1", "pluginA");
    au1.setAttr("a", "A");
    au1.setAttr("b", "A");
    au1.setParam("x", "X");
    au1.setPluginVersion("3");
    
    TdbAu au2 = new TdbAu("Test AU1", "pluginA");
    au2.setAttr("a", "A");
    au2.setAttr("b", "A");
    au2.setParam("x", "X");
    au2.setPluginVersion("3");

    TdbAu au3 = new TdbAu("Test AU1", "pluginB");
    au3.setAttr("a", "A");
    au3.setAttr("b", "A");
    au3.setParam("x", "X");
    au3.setPluginVersion("3");

    assertEquals(au1.getId(), au2.getId());
    assertNotEquals(au1.getId(), au3.getId());
  }
  
  /**
   * Test copyForTdbTitle method.
   * @throws TdbException for invalid Tdb operations
   */
  public void testCopyForTdbTitle() throws TdbException {
    TdbAu au1 = new TdbAu("Test AU1", "pluginA");
    au1.setAttr("a", "A");
    au1.setAttr("b", "A");
    au1.setParam("x", "X");
    au1.setPluginVersion("3");

    TdbTitle title1 = new TdbTitle("Test Title1", "0000-0000");
    title1.addTdbAu(au1);
    
    TdbTitle title2 = new TdbTitle("Test Title2", "0000-0000");
    TdbAu au2 = au1.copyForTdbTitle(title2);
    assertSame(title2, au2.getTdbTitle());
    assertNotSame(title1, title2);
    assertEquals(au1.getName(), au2.getName());
    assertEquals(au1.getAttr("a"), au2.getAttr("a"));
    assertEquals(au1.getParam("x"), au2.getParam("x"));
    assertEquals(au1.getPluginId(), au2.getPluginId());
    assertEquals(au1.getPluginVersion(), au2.getPluginVersion());
  }
}
