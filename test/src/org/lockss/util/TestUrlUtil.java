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

import java.net.*;
import junit.framework.TestCase;
import org.lockss.test.*;
import org.lockss.util.*;

public class TestUrlUtil extends LockssTestCase {

  public void testEqualUrls() throws MalformedURLException {
    assertTrue(UrlUtil.equalUrls(new URL("http://foo.bar/xyz#tag"),
				 new URL("http://foo.bar/xyz#tag")));
    assertTrue(UrlUtil.equalUrls(new URL("http://foo.bar/xyz#tag"),
				 new URL("HTTP://FOO.bar/xyz#tag")));
    assertFalse(UrlUtil.equalUrls(new URL("http://foo.bar/xyz#tag"),
				  new URL("ftp://foo.bar/xyz#tag")));
    assertFalse(UrlUtil.equalUrls(new URL("http://foo.bar/xyz#tag"),
				  new URL("http://foo.baz/xyz#tag")));
    assertFalse(UrlUtil.equalUrls(new URL("http://foo.bar/xyz#tag"),
				  new URL("http://foo.bar/xyzz#tag")));
    assertFalse(UrlUtil.equalUrls(new URL("http://foo.bar/xyz#tag"),
				  new URL("http://foo.bar/xYz#tag")));
    assertFalse(UrlUtil.equalUrls(new URL("http://foo.bar/xyz#tag"),
				  new URL("http://foo.bar/xyz#tag2")));
    assertFalse(UrlUtil.equalUrls(new URL("http://foo.bar/xyz#tag"),
				  new URL("http://foo.bar/xyz#Tag")));
    assertFalse(UrlUtil.equalUrls(new URL("http:80//foo.bar/xyz#tag"),
				  new URL("http:81//foo.bar/xyz#tag")));
  }

  public void testIsHttpUrl() {
    assertTrue(UrlUtil.isHttpUrl("http://foo"));
    assertTrue(UrlUtil.isHttpUrl("https://foo"));
    assertTrue(UrlUtil.isHttpUrl("HTTP://foo"));
    assertTrue(UrlUtil.isHttpUrl("HTTPS://foo"));
    assertFalse(UrlUtil.isHttpUrl("ftp://foo"));
    assertFalse(UrlUtil.isHttpUrl("file://foo"));
  }

  public void testGetUrlPrefixNullUrl(){
    try{
      UrlUtil.getUrlPrefix(null);
      fail("Should have thrown MalformedURLException");
    }
    catch(MalformedURLException mue){
    }
  }

  public void testGetUrlPrefixNotHttpUrl(){
    try{
      UrlUtil.getUrlPrefix("bad test string");
      fail("Should have thrown MalformedURLException");
    }
    catch(MalformedURLException mue){
    }
  }

  public void testGetUrlPrefixRootHighWireUrl() throws MalformedURLException{
    String root = "http://shadow8.stanford.edu";
    String url = root + "/lockss-volume327.shtml";
    assertEquals(root, UrlUtil.getUrlPrefix(url));
  }

  public void testGetUrlPrefixRootHighWireUrlWithOddPort()
      throws MalformedURLException{
    String root = "http://shadow8.stanford.edu:8080";
    String url = root + "/lockss-volume327.shtml";
    assertEquals(root, UrlUtil.getUrlPrefix(url));
  }

  public void testGetUrlPrefixPrefixUrl() throws MalformedURLException{
    String root = "http://shadow8.stanford.edu";
    assertEquals(root, UrlUtil.getUrlPrefix(root));
  }

  public void testGetHost() throws Exception {
    assertEquals("xx.foo.bar", UrlUtil.getHost("http://xx.foo.bar/123"));
    assertEquals("foo", UrlUtil.getHost("http://foo/123"));
    assertEquals("foo.", UrlUtil.getHost("http://foo./123"));
    try{
      UrlUtil.getHost("garbage://xx.foo.bar/123");
      fail("Should have thrown MalformedURLException");
    }
    catch (MalformedURLException mue) {
    }
  }

  public void testGetDomain() throws Exception {
    assertEquals("foo.bar", UrlUtil.getDomain("http://xx.foo.bar/123"));
    assertEquals("foo", UrlUtil.getDomain("http://foo/123"));
    assertEquals("foo.", UrlUtil.getDomain("http://foo./123"));
    try{
      UrlUtil.getDomain("garbage://xx.foo.bar/123");
      fail("Should have thrown MalformedURLException");
    }
    catch (MalformedURLException mue) {
    }
  }

  public void testResolveUrl() throws Exception {
    assertEquals("http://test.com/foo/bar/a.html",
		 UrlUtil.resolveUri("ftp://gorp.org/xxx.jpg",
				    "http://test.com/foo/bar/a.html"));
    assertEquals("http://test.com/foo/bar/a.html",
		 UrlUtil.resolveUri("http://test.com/foo/bar/xxx.html",
				    "a.html"));
    assertEquals("http://test.com/foo/bar/a.html",
		 UrlUtil.resolveUri("http://test.com/foo/bar/baz/xxx.html",
				    "../a.html"));
    assertEquals("http://test.com/foo/bar/a.html",
		 UrlUtil.resolveUri("http://test.com/foo/bar/",
				    "a.html"));
    // ensure query string preserved
    assertEquals("http://test.com/foo/bar/a.html?foo=bar",
		 UrlUtil.resolveUri("http://test.com/foo/bar/",
				    "a.html?foo=bar"));
    try {
      UrlUtil.resolveUri("foo", "bar");
      fail("Should throw MalformedURLException");
    } catch (MalformedURLException e) {}
  }

  //should trip leading and trailing whitespace from the second arg
  public void testResolveUrlTrimsLeadingAndTrailingWhiteSpace()
      throws MalformedURLException {
    assertEquals("http://test.com/foo/bar/a.html",
		 UrlUtil.resolveUri("http://test.com/foo/bar/", " a.html"));
    assertEquals("http://test.com/foo/bar/a.html",
		 UrlUtil.resolveUri("http://test.com/foo/bar/", "a.html "));
    assertEquals("http://test.com/foo/bar/a.html",
 		 UrlUtil.resolveUri("http://test.com/foo/bar/", "\ta.html "));
    assertEquals("http://test.com/foo/bar/a.html",
 		 UrlUtil.resolveUri("http://test.com/foo/bar/", "\na.html "));
    assertEquals("http://test.com/foo/bar/a.html",
 		 UrlUtil.resolveUri("http://test.com/foo/bar/", "a.html\n "));
    assertEquals("http://test.com/foo/bar/a.html",
 		 UrlUtil.resolveUri("http://test.com/foo/bar/", "a.html\r "));
  }

  public void testResolveUrlEncoding() throws MalformedURLException {
    // Embedded space should be escaped
    assertEquals("http://test.com/foo/bar/a%20test.html",
		 UrlUtil.resolveUri("http://test.com/foo/bar/",
				    "a test.html"));
    // Percents should not be escaped, or risk double escapement
    assertEquals("http://test.com/foo/bar/a%20.html",
		 UrlUtil.resolveUri("http://test.com/foo/bar/",
				    "a%20.html"));
  }

  public void testIsDirectoryRedirection() {
    assertTrue(UrlUtil.isDirectoryRedirection("http://xx.com/foo",
					      "http://xx.com/foo/"));
    assertTrue(UrlUtil.isDirectoryRedirection("http://xx.com/foo",
					      "Http://xx.com/foo/"));
    assertTrue(UrlUtil.isDirectoryRedirection("http://xx.com/foo",
					      "Http://Xx.COM/foo/"));
    assertFalse(UrlUtil.isDirectoryRedirection("http://xx.com/foo",
					       "http://xx.com/FOO/"));
    assertFalse(UrlUtil.isDirectoryRedirection("http://xx.com/foo",
					       "http://xx.com/foo"));
    assertFalse(UrlUtil.isDirectoryRedirection("http://xx.com/foo",
					       "http://zz.com/foo/"));

    assertTrue(UrlUtil.isDirectoryRedirection("http://xx.com/foo?a=b",
					      "http://xx.com/foo/?a=b"));
    // slash appended to query string isn't
    assertFalse(UrlUtil.isDirectoryRedirection("http://xx.com/foo?a=b",
					      "http://xx.com/foo?a=b/"));
    // ensure doesn't totally ignore query
    assertFalse(UrlUtil.isDirectoryRedirection("http://xx.com/foo?a=b",
					      "http://xx.com/foo/"));
    // not legal URLs, so returns false
    assertFalse(UrlUtil.isDirectoryRedirection("foo", "foo/"));
  }

  public void testGetHeadersNullConnection() {
    try {
      UrlUtil.getHeaders(null);
      fail("Calling getHeaderFields with a null argument should have thrown");
    } catch (IllegalArgumentException e) {
    }
  }

  public void testGetHeadersOneHeader() throws MalformedURLException {
    URL url = new URL("http://www.example.com");
    MockURLConnection conn = new MockURLConnection(url);
    conn.setHeaderFieldKeys(ListUtil.list("key1"));
    conn.setHeaderFields(ListUtil.list("field1"));
    assertEquals(ListUtil.list("key1;field1"), UrlUtil.getHeaders(conn));
  }

  public void testGetHeadersMultiHeaders() throws MalformedURLException {
    URL url = new URL("http://www.example.com");
    MockURLConnection conn = new MockURLConnection(url);
    conn.setHeaderFieldKeys(ListUtil.list("key1", "key2"));
    conn.setHeaderFields(ListUtil.list("field1", "field2"));

    assertEquals(ListUtil.list("key1;field1", "key2;field2"),
		 UrlUtil.getHeaders(conn));
  }

  public void testGetHeadersNullHeaders() throws MalformedURLException {
    URL url = new URL("http://www.example.com");
    MockURLConnection conn = new MockURLConnection(url);
    conn.setHeaderFieldKeys(ListUtil.list(null, "key2"));
    conn.setHeaderFields(ListUtil.list("field1", null));

    assertEquals(ListUtil.list("null;field1", "key2;null"),
		 UrlUtil.getHeaders(conn));
  }

  public void testIsAbsoluteUrl() {
    assertFalse(UrlUtil.isAbsoluteUrl(null));
    assertFalse(UrlUtil.isAbsoluteUrl(""));
    assertTrue(UrlUtil.isAbsoluteUrl("http://www.example.com/"));
    assertTrue(UrlUtil.isAbsoluteUrl("http://www.example.com/blah/"));
    assertFalse(UrlUtil.isAbsoluteUrl("www.example.com/"));
    assertFalse(UrlUtil.isAbsoluteUrl("blah/blah"));
  }

  public void testStripsParams() throws MalformedURLException {
    assertNull(UrlUtil.stripQuery(null));
    assertEquals(null, UrlUtil.stripQuery(""));
    assertEquals("http://www.example.com/",
		 UrlUtil.stripQuery("http://www.example.com/"));
    assertEquals("http://www.example.com/blah",
		 UrlUtil.stripQuery("http://www.example.com/blah?param1=blah"));
    assertEquals("rtsp://www.example.com/blah",
		 UrlUtil.stripQuery("rtsp://www.example.com/blah?param1=blah"));
  }

}
