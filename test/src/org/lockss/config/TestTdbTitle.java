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

package org.lockss.config;

import org.lockss.util.*;
import org.lockss.config.*;
import org.lockss.config.Tdb.TdbException;
import org.lockss.test.*;

import java.util.*;

/**
 * Test class for <code>org.lockss.config.TdbTitle</code>
 *
 * @author  Philip Gust
 * @version $Id$
 */

public class TestTdbTitle extends LockssTestCase {

  public static Class testedClasses[] = {
    org.lockss.config.TdbTitle.class
  };

  public void setUp() throws Exception {
    super.setUp();
  }

  public void tearDown() throws Exception {
    super.tearDown();
  }

  static Logger log = Logger.getLogger("TestTdbTitle");

  /**
   * Test creating valid TdbTitle.
   */
  public void testValidTitle() {
    TdbTitle title = null;
    try {
      title = new TdbTitle("Test Title", "0000-0000");
    } catch (IllegalArgumentException ex) {
    }
    assertNotNull(title);
    assertEquals("Test Title", title.getName());
  }

  /**
   * Test creating TdbPublisher with null name and/or id.
   */
  public void testNullTitleNameAndId() {
    TdbTitle title = null;
    try {
      title = new TdbTitle(null, "0000-0000");
      fail("TdbTitle did not throw IllegalArgumentException for null title argument.");
    } catch (IllegalArgumentException ex) {
    }
    assertNull(title);
   
    try {
      title = new TdbTitle("title", null);
      fail("TdbTitle did not throw IllegalArgumentException for null id argument.");
    } catch (IllegalArgumentException ex) {
    }
    assertNull(title);

  }
  
  /**
   * Test equality between titles.
   * @throws TdbException for invalid Tdb operations
   */
  public void testEquals() throws TdbException {
    TdbAu au1 = new TdbAu("Test AU", "pluginA");
    au1.setParam("name1", "val1");
    au1.setParam("name2", "val2");
    assertEquals(au1, au1);
    
    TdbTitle title1 = new TdbTitle("Test Title", "0001-0001");
    title1.addTdbAu(au1);
    assertEquals(title1, title1);
    
    // same as title1
    TdbAu au2 = new TdbAu("Test AU", "pluginA");
    au2.setParam("name1", "val1");
    au2.setParam("name2", "val2");
    assertEquals(au1, au2);

    TdbTitle title2 = new TdbTitle("Test Title", "0001-0001");
    title2.addTdbAu(au2);
    assertEquals(title1, title2);
    
    // differes from title1 only by au param
    TdbAu au3 = new TdbAu("Test AU", "pluginA");
    au3.setParam("name1", "val1");
    au3.setParam("name2", "val3");
    assertNotEquals(au1, au3);
    
    TdbTitle title3 = new TdbTitle("Test Title", "0001-0001");
    title3.addTdbAu(au3);
    assertNotEquals(title1, title3);
    
    // differs from title3 only by title id 
    TdbAu au4 = new TdbAu("Test AU", "pluginA");
    au4.setParam("name1", "val1");
    au4.setParam("name2", "val3");
    assertEquals(au3, au4);
    
    TdbTitle title4 = new TdbTitle("Test Title", "0002-0002");
    title4.addTdbAu(au4);
    assertNotEquals(title3, title4);
  }
  
  /**
   * Test addTitle() method.
   * @throws TdbException for invalid Tdb operations
   */
  public void testGetPublisher() throws TdbException {
    TdbPublisher publisher = new TdbPublisher("Test Publisher");
    Collection titles = publisher.getTdbTitles();
    assertEmpty(titles);
    
    // add title
    TdbTitle title = new TdbTitle("Test Title 1", "0000-0000");
    publisher.addTdbTitle(title);
    titles = publisher.getTdbTitles();
    assertEquals(1, titles.size());
    assertTrue(titles.contains(title));
    
    // get publisher
    TdbPublisher getPublisher = title.getTdbPublisher();
    assertSame(publisher, getPublisher);
  }
  
  /**
   * Test addTdbAu() method.
   * @throws TdbException for invalid Tdb operations
   */
  public void testAddTdbAu() throws TdbException {
    TdbPublisher publisher = new TdbPublisher("Test Publisher");
    Collection titles = publisher.getTdbTitles();
    assertEmpty(titles);
    
    // add title
    TdbTitle title = new TdbTitle("Test Title", "0010-9876");
    publisher.addTdbTitle(title);

    // vaidate link collection for TdbTitle.LinkType.cntinuedBy link type
    title.addLinkToTdbTitleId(TdbTitle.LinkType.continuedBy, "0020-2468");
    Collection<String>titleIds = title.getLinkedTdbTitleIdsForType(TdbTitle.LinkType.continuedBy);
    assertNotNull(titleIds);
    assertEquals(1, titleIds.size());
    assertTrue(titleIds.contains("0020-2468"));

    titles = publisher.getTdbTitles();
    assertEquals(1, titles.size());
    assertTrue(titles.contains(title));

    TdbAu au = new TdbAu("Test Title, Volume 1", "plugin1");
    title.addTdbAu(au);
    
    Collection<TdbAu> aus = title.getTdbAus();
    assertNotNull(aus);
    assertEquals(1, aus.size());
    assertTrue(aus.contains(au));
    
    // can't add same AU twice
    try {
      title.addTdbAu(au);
      fail("TdbTitle did not throw IllegalArgumentException adding same AU twice.");
    } catch (IllegalArgumentException ex) {
    }
    aus = title.getTdbAus();
    assertEquals(1, aus.size());

    // can't add null title
    try {
      title.addTdbAu(null);
      fail("TdbTitle did not throw IllegalArgumentException adding null AU.");
    } catch (IllegalArgumentException ex) {
    }
    assertEquals(1, aus.size());
    assertTrue(aus.contains(au));

    // can't add another AU with the same id
    TdbAu au2 = new TdbAu("Test Title, Volume 1a", "plugin1");
    try {
      title.addTdbAu(au2);
      fail("TdbTitle did not throw TdbException adding au with Id of existing one.");
    } catch (TdbException ex) {
    }
    aus = title.getTdbAus();
    assertEquals(1, aus.size());
  }

  /**
   * Test getTdbAusByName() and getTdbAuById() methods.
   * @throws TdbException for invalid Tdb operations
   */
  public void testGetTdbAu() throws TdbException {
    TdbPublisher publisher = new TdbPublisher("Test Publisher");

    // add title
    TdbTitle title = new TdbTitle("Journal Title", "0000-0000");
    publisher.addTdbTitle(title);

    // add an AU to the title
    TdbAu au1 = new TdbAu("Journal Title, Issue 1", "plugin1");
    title.addTdbAu(au1);
    
    TdbAu au2 = new TdbAu("Journal Title, Issue 2", "plugin2");
    title.addTdbAu(au2);

    // retrieve the AU from the title
    Collection<TdbAu> getAus1 = title.getTdbAusByName("Journal Title, Issue 1");
    assertFalse(getAus1.isEmpty());
    assertTrue(getAus1.contains(au1));
    Collection<TdbAu> getAus2 = title.getTdbAusByName("unknown");
    assertTrue(getAus2.isEmpty());

    assertSame(au1, title.getTdbAuById(au1.getId()));
    assertSame(au2, title.getTdbAuById(au2.getId()));
  }
  
  /**
   * Test the getId() and equals() functions.
   * @throws TdbException for invalid Tdb operations
   */
  public void testGetId() throws TdbException {
    TdbPublisher publisher = new TdbPublisher("Test Publisher");

    // add title with ID
    TdbTitle title1 = new TdbTitle("Journal Title 1", "1234-5678");
    publisher.addTdbTitle(title1);
    
    // ensure specified ID is being returned
    assertEquals("1234-5678", title1.getId());
  }
  
  /**
   * Test TdbAu.addPluginIdsForDifferences() method.
   * @throws TdbException for invalid Tdb operations
   */
  public void testAddPluginIdsForDifferences() throws TdbException {
    TdbTitle title1 = new TdbTitle("Test Title", "0001-0001");
    
    TdbAu au1 = new TdbAu("Test AU1", "pluginA");
    au1.setAttr("a", "A");
    au1.setAttr("b", "A");
    au1.setParam("x", "X");
    au1.setPluginVersion("3");
    title1.addTdbAu(au1);
    
    TdbTitle title2 = new TdbTitle("Test Title", "0001-0001");
    
    TdbAu au2 = new TdbAu("Test AU1", "pluginA");
    au2.setAttr("a", "A");
    au2.setAttr("b", "A");
    au2.setParam("x", "X");
    au2.setPluginVersion("3");
    title2.addTdbAu(au2);

    TdbTitle title3 = new TdbTitle("Test Title", "0001-0001");
    
    TdbAu au3 = new TdbAu("Test AU1", "pluginB");
    au3.setAttr("a", "A");
    au3.setAttr("b", "A");
    au3.setParam("x", "X");
    au3.setPluginVersion("3");
    title3.addTdbAu(au3);

    Set<String> diff12 = new HashSet();
    title1.addPluginIdsForDifferences(diff12, title2);
    assertEquals(0, diff12.size());
    
    Set<String> diff13 = new HashSet();
    title1.addPluginIdsForDifferences(diff13, title3);
    assertEquals(2, diff13.size());
  }
  
  /**
   * Test copyForTdbPublisher() method.
   * @throws TdbException for invalid Tdb operations
   */
  public void testCopyForTdbPublisher() throws TdbException {
    TdbTitle title1 = new TdbTitle("Test Title1", "0001-0001");
    TdbPublisher publisher1 = new TdbPublisher("Test Publisher1");
    publisher1.addTdbTitle(title1);
    
    TdbPublisher publisher2 = new TdbPublisher("Test Publisher2");
    TdbTitle title2 = title1.copyForTdbPublisher(publisher2);
    assertEquals(publisher2, title2.getTdbPublisher());
    assertEquals(title1.getId(), title2.getId());

    TdbPublisher publisher3 = new TdbPublisher("Test Publisher3");
    TdbTitle title3 = new TdbTitle("Test Title3", "0001-0002");
    publisher3.addTdbTitle(title3);
    
    TdbPublisher publisher4 = new TdbPublisher("Test Publisher4");
    TdbTitle title4 = title3.copyForTdbPublisher(publisher4);
    assertSame(publisher4, title4.getTdbPublisher());
    assertNotSame(title3, title4);
    assertEquals(title3.getId(), title4.getId());
    assertEquals(title3, title4);
  }
}
