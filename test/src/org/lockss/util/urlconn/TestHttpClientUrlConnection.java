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

package org.lockss.util.urlconn;

import java.io.*;
import java.util.*;
import java.text.*;
import org.lockss.daemon.*;
import org.lockss.test.*;
import org.lockss.util.*;
import org.apache.commons.httpclient.*;

/**
 * Test class for org.lockss.util.urlconn.HttpClientUrlConnection
 */
public class TestHttpClientUrlConnection extends LockssTestCase {
  static Logger log = Logger.getLogger("TestHttpClientUrlConnection");

  MockHttpClient client;
  MyMockGetMethod method;
  MockHttpClientUrlConnection conn;
  int newClientCtr;
  String urlString = "http://Test.Url/";

  public void setUp() throws Exception {
    super.setUp();
    client = new MockHttpClient();
    conn = newConn();
    method = conn.getMockMethod();
  }  

  public void tearDown() throws Exception {
  }

  MockHttpClientUrlConnection newConn() {
    return new MockHttpClientUrlConnection(urlString, client);
  }

  public void testPred() {
    assertTrue(conn.isHttp());
    assertTrue(conn.canProxy());
  }    

  public void testReqProps() {
    Header hdr;

    assertEquals(urlString, conn.getURL());
    conn.setUserAgent("irving");
    hdr = method.getRequestHeader("user-agent");
    assertEquals("irving", hdr.getValue());

    conn.setRequestProperty("p2", "v7");
    hdr = method.getRequestHeader("p2");
    assertEquals("v7", hdr.getValue());

    assertTrue(method.getFollowRedirects());
    conn.setFollowRedirects(false);
    assertFalse(method.getFollowRedirects());
    conn.setFollowRedirects(true);
    assertTrue(method.getFollowRedirects());
  }

  public void testStateNotEx() throws Exception {
    assertFalse(conn.isExecuted());
    try {
      conn.getResponseCode();
      fail("Failed to throw IllegalStateException");
    } catch (IllegalStateException e) {}
    try {
      conn.getResponseMessage();
      fail("Failed to throw IllegalStateException");
    } catch (IllegalStateException e) {}
    try {
      conn.getResponseDate();
      fail("Failed to throw IllegalStateException");
    } catch (IllegalStateException e) {}
    try {
      conn.getResponseContentLength();
      fail("Failed to throw IllegalStateException");
    } catch (IllegalStateException e) {}
    try {
      conn.getResponseContentType();
      fail("Failed to throw IllegalStateException");
    } catch (IllegalStateException e) {}
    try {
      conn.getResponseContentEncoding();
      fail("Failed to throw IllegalStateException");
    } catch (IllegalStateException e) {}
    try {
      conn.getResponseHeaderValue("foo");
      fail("Failed to throw IllegalStateException");
    } catch (IllegalStateException e) {}
    conn.execute();
  }

  public void testStateEx() throws Exception {
    assertFalse(conn.isExecuted());
    conn.execute();
    assertTrue(conn.isExecuted());
    try {
      conn.setUserAgent("foo");
      fail("Failed to throw IllegalStateException");
    } catch (IllegalStateException e) {}
    try {
      conn.setRequestProperty("foo", "bar");
      fail("Failed to throw IllegalStateException");
    } catch (IllegalStateException e) {}
    try {
      conn.setFollowRedirects(true);
      fail("Failed to throw IllegalStateException");
    } catch (IllegalStateException e) {}
  }

  public void testExecute() throws Exception {
    client.setRes(201, 202);
    conn.execute();
    assertTrue(conn.isExecuted());
    assertEquals(201, conn.getResponseCode());
    Header hdr;
    hdr = method.getRequestHeader("connection");
    assertEquals("keep-alive", hdr.getValue());
    hdr = method.getRequestHeader("accept");
    assertEquals(HttpClientUrlConnection.ACCEPT_STRING, hdr.getValue());
    
  }

  public void testExecuteProxy() throws Exception {
    client.setRes(201, 202);
    conn.setProxy("phost", 9009);
    conn.execute();
    assertTrue(conn.isExecuted());
    assertEquals(202, conn.getResponseCode());
    Header hdr;
    hdr = method.getRequestHeader("connection");
    assertEquals("keep-alive", hdr.getValue());
    hdr = method.getRequestHeader("accept");
    assertEquals(HttpClientUrlConnection.ACCEPT_STRING, hdr.getValue());
    HostConfiguration hc = client.getHostConfiguration();
    assertEquals("phost", hc.getProxyHost());
    assertEquals(9009, hc.getProxyPort());
  }

  public void testResponse() throws Exception {
    String datestr = "Mon, 23 Feb 2004 00:28:11 GMT";
    client.setRes(201, 202);
    method.setResponseHeader("Date", datestr);
    method.setResponseHeader("Content-Encoding", "text/html");
    method.setResponseHeader("Content-type", "type1");
    method.setStatusText("stext");
    method.setResponseContentLength(3333);

    conn.execute();
    assertEquals(201, conn.getResponseCode());
    assertEquals("stext", conn.getResponseMessage());
    assertEquals(1077496091000L, conn.getResponseDate());
    assertEquals(3333, conn.getResponseContentLength());
    assertEquals("text/html", conn.getResponseContentEncoding());
    assertEquals("type1", conn.getResponseContentType());
    Properties props = new Properties();
    conn.storeResponseHeaderInto(props, "x_");
    Properties eprops = new Properties();
    eprops.put("x_content-type", "type1");
    eprops.put("x_date", datestr);
    eprops.put("x_content-encoding", "text/html");
    assertEquals(eprops, props);

  }


  class MockHttpClient extends HttpClient {
    int res1 = -1;
    int res2 = -2;
    HostConfiguration hc = null;

    public int executeMethod(HttpMethod method)
        throws IOException, HttpException  {
      return res1;
    }
    public int executeMethod(HostConfiguration hostConfiguration,
			     HttpMethod method)
        throws IOException, HttpException {
      hc = hostConfiguration;
      return res2;
    }    
    void setRes(int res1, int res2) {
      this.res1 = res1;
      this.res2 = res2;
    }
    public HostConfiguration getHostConfiguration() {
      return hc;
    }
  }

  class MyMockGetMethod extends MockHttpMethod
    implements HttpClientUrlConnection.LockssGetMethod {

    String url;
    HttpClientUrlConnection.LockssGetMethodImpl getMeth;
    Properties respProps = new Properties();
    String statusText;
    int contentLength = -1;

    public MyMockGetMethod(String url) {
      super();
      this.url = url;
      getMeth = new HttpClientUrlConnection.LockssGetMethodImpl(url);
    }

    public void setRequestHeader(String headerName, String headerValue) {
      getMeth.setRequestHeader(headerName, headerValue);
    }

    public Header getRequestHeader(String headerName) {
      return getMeth.getRequestHeader(headerName);
    }

    public void setFollowRedirects(boolean followRedirects) {
      getMeth.setFollowRedirects(followRedirects);
    }

    public boolean getFollowRedirects() {
      return getMeth.getFollowRedirects();
    }

    public String getStatusText() {
      return statusText;
    }
    void setStatusText(String s) {
      statusText = s;
    }
    void setResponseHeader(String name, String value) {
      log.info("put: " + name + ": " + value);
      respProps.put(name.toLowerCase(), value);
    }
    public Header getResponseHeader(String headerName) {        
      String val = (String)respProps.get(headerName.toLowerCase());
      log.info(headerName + ": " + val);
      if (val != null) {
	return new Header(headerName, val);
      }
      return null;
    }
    public Header[] getResponseHeaders() {
      List keys = new ArrayList(respProps.keySet());
      int n = keys.size();
      Header[] hdrs = new Header[n];
      for (int ix = 0; ix < n; ix++) {
	String key = (String)keys.get(ix);
	hdrs[ix] = new Header(key, respProps.getProperty(key));
      }
      return hdrs;
    }
	
    public int getResponseContentLength() {
      return contentLength;
    }
    void setResponseContentLength(int l) {
      contentLength = l;
    }

  }

  class MockHttpClientUrlConnection extends HttpClientUrlConnection {
    MyMockGetMethod mockMeth;

    MockHttpClientUrlConnection(String urlString, MockHttpClient client) {
      super(urlString, client);
    }	
    protected LockssGetMethod newLockssGetMethodImpl(String urlString) {
      mockMeth = new MyMockGetMethod(urlString);
      return mockMeth;
    }
    MyMockGetMethod getMockMethod() {
      return mockMeth;
    }

    boolean getFollowRedirects() {
      return mockMeth.getFollowRedirects();
    }

  }


}
