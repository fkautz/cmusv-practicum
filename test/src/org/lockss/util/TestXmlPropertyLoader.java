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

import java.util.*;
import java.io.*;
import java.net.URL;
import javax.xml.parsers.*;
import org.mortbay.tools.PropertyTree;
import org.xml.sax.*;
import org.xml.sax.helpers.*;
import org.lockss.test.*;

/**
 * Test class for <code>org.lockss.util.XmlPropertyLoader</code> and
 * <code>org.lockss.util.LockssConfigHandler</code>.
 */

public class TestXmlPropertyLoader extends LockssTestCase {

  private static PropertyTree m_props = null;
  private static XmlPropertyLoader m_xmlPropertyLoader = new MockXmlPropertyLoader();

  public static Class testedClasses[] = {
    org.lockss.util.XmlPropertyLoader.class
  };

  public void setUp() throws Exception {
    super.setUp();
    m_props = setUpPropertyTree();
  }

  public void tearDown() throws Exception {
    super.tearDown();
  }

  static Logger log = Logger.getLogger("TestXmlPropertyLoader");

  /**
   * Set up a test XML configuration with good data.
   */
  private PropertyTree setUpPropertyTree() throws IOException {
    StringBuffer sb = new StringBuffer();

    String file = "configtest.xml";
    URL url = getClass().getResource(file);
    assertNotNull(file + " missing.", url);
    InputStream istr = UrlUtil.openInputStream(url.toString());

    PropertyTree props = new PropertyTree();

    m_xmlPropertyLoader.load(props, istr);
    return props;
  }

  /**
   * Test known-bad XML.
   */
  public void testUnknownXmlTag() throws IOException {
    PropertyTree props = new PropertyTree();
    StringBuffer sb = new StringBuffer();
    sb.append("<lockss-config>\n");
    sb.append("  <some-unknown-tag name=\"foo\" value=\"bar\" />\n");
    sb.append("</lockss-config>\n");
    InputStream istr = new ReaderInputStream(new StringReader(sb.toString()));
    try {
      m_xmlPropertyLoader.load(props, istr);
      fail("Should have thrown.");
    } catch (Throwable t) {
    }
  }

  /**
   * Test basic non-nested property getting from the static config.
   */
  public void testGet() throws IOException {
    assertEquals("foo", m_props.get("a"));
  }

  /**
   * Test a nested property.
   */
  public void testNestedGet() throws IOException {
    assertEquals("foo", m_props.get("b.c"));
  }

  /**
   * Test a non-existent property.
   */
  public void testNullValue() throws IOException {
    assertNull(m_props.get("this.prop.does.not.exist"));
  }

  /**
   * Test getting a list out of the config.
   */
  public void testGetList() throws IOException {
    List l = null;

    try {
      l = (List)m_props.get("org.lockss.d");
    } catch (ClassCastException ex) {
      fail("Class cast exception while getting list from properties.");
    }

    assertEquals(5, l.size());
    assertNotNull(l);

    Collections.sort(l);

    assertEquals("1", (String)l.get(0));
    assertEquals("2", (String)l.get(1));
    assertEquals("3", (String)l.get(2));
    assertEquals("4", (String)l.get(3));
    assertEquals("5", (String)l.get(4));
  }


  public void testDaemonVersionEquals() throws IOException {
    assertNull(m_props.get("org.lockss.test.a"));
    assertEquals("foo", m_props.get("org.lockss.test.b"));
  }

  public void testDaemonVersionMax() throws IOException {
    assertNull(m_props.get("org.lockss.test.c"));
    assertEquals("foo", m_props.get("org.lockss.test.d"));
  }

  public void testDaemonVersionMin() throws IOException {
    assertEquals("foo", m_props.get("org.lockss.test.e"));
    assertNull(m_props.get("org.lockss.test.f"));

  }

  public void testDaemonVersionMaxAndMin() throws IOException {
    assertEquals("foo", m_props.get("org.lockss.test.g"));
    assertNull(m_props.get("org.lockss.test.h"));
  }

  public void testPlatformVersionEquals() throws IOException {
    assertEquals("foo", m_props.get("org.lockss.test.i"));
    assertNull(m_props.get("org.lockss.test.j"));

  }

  public void testPlatformVersionMax() throws IOException {
    assertNull(m_props.get("org.lockss.test.k"));
    assertEquals("foo", m_props.get("org.lockss.test.l"));
  }

  public void testPlatformVersionMin() throws IOException {
    assertEquals("foo", m_props.get("org.lockss.test.m"));
    assertNull(m_props.get("org.lockss.test.n"));
  }

  public void testPlatformVersionMinAndMax() throws IOException {
    assertEquals("foo", m_props.get("org.lockss.test.o"));
    assertNull(m_props.get("org.lockss.test.p"));
  }

  public void testGroupMembership() throws IOException {
    assertEquals("foo", m_props.get("org.lockss.test.q"));
    assertNull(m_props.get("org.lockss.test.r"));
  }

  public void testHostnameMembership() throws IOException {
    assertEquals("foo", m_props.get("org.lockss.test.s"));
    assertNull(m_props.get("org.lockss.test.t"));
  }

  public void testThenElse() {
    assertEquals("foo", m_props.get("org.lockss.test.u"));
    assertEquals("bar", m_props.get("org.lockss.test.v"));
  }

  public void testConditionalCombo() throws IOException {
    assertEquals("bar", m_props.get("org.lockss.test.w"));
    assertEquals("foo", m_props.get("org.lockss.test.x"));

  }
}
