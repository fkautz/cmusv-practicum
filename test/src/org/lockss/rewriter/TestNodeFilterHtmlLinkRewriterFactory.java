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

package org.lockss.rewriter;
import org.lockss.test.*;
import org.lockss.util.*;
import org.lockss.daemon.*;
import org.lockss.plugin.*;
import java.util.*;
import java.io.*;

public class TestNodeFilterHtmlLinkRewriterFactory extends LockssTestCase {
  static Logger log = Logger.getLogger("FuncArcExploder");

  private MockArchivalUnit au;
  private NodeFilterHtmlLinkRewriterFactory nfhlrf;
  private String encoding = null;
  private static final String urlStem = "http://www.example.com/";
  private static final String urlSuffix = "content/index.html";
  private static final String url = urlStem + urlSuffix;
  private static final String page =
    "<!DOCTYPE html PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\">\n" +
    "<html>\n" +
    "<head>\n" +
    "<title>example.com website</title>\n" +
    "<meta http-equiv=\"content-type\"" +
    "content=\"text/html; charset=ISO-8859-1\">" +
    "</head>" +
    "<body>" +
    "<h1 align=\"center\">example.com website</h1>" +
    "<br>" +
    "<a href=\"" + url + "\">an absolute link to rewrite</a>" +
    "<br>" +
    "<a href=\"" + urlSuffix + "\">a relative link to rewrite</a>" +
    "<br>" +
    "<a href=\"" + "/more/" + urlSuffix + "\">a relative link to rewrite</a>" +
    "<br>" +
    "<a href=\"http://www.content.org/index.html\">an absolute link not to rewrite</a>" +
    "<br>" +
    "</body>" +
    "</HTML>";
  private static final int linkCount = 3;
  private InputStream in;

  public void setUp() throws Exception {
    super.setUp();
    au = new MockArchivalUnit();
    List l = new ArrayList();
    l.add(urlStem);
    au.setUrlStems(l);
    nfhlrf = new NodeFilterHtmlLinkRewriterFactory();
  }

  public void testThrowsIfNotHtml() {
    in = new ReaderInputStream(new StringReader(page));
    try {
      InputStream ret = nfhlrf.createLinkRewriter("application/pdf", au, in,
						  encoding, url);
      fail("createLinkRewriter should have thrown on non-html mime type");
    } catch (Exception ex) {
      if (ex instanceof PluginException) {
	return;
      }
      fail("createLinkRewriter should have thrown PluginException but threw " +
	   ex.toString());
    }
  }

  public void testRewriting() {
    in = new ReaderInputStream(new StringReader(page));
    try {
      InputStream ret = nfhlrf.createLinkRewriter("text/html", au, in,
						 encoding, url);
      assertNotNull(ret);
      // Read from ret, make String
      Reader r = new InputStreamReader(ret);
      StringBuffer sb = new StringBuffer();
      char[] buf = new char[4096];
      int i;
      while ((i = r.read(buf)) > 0) {
	sb.append(buf, 0, i);
      }
      String out = sb.toString();
      assertNotNull(out);
      log.debug3(out);
      // Now check the rewriting
      int ix = 0;
      for (i = 0; i < linkCount; i++) {
	int nix = out.indexOf("ServeContent?url=" + urlStem, ix);
	assertTrue(nix > ix);
	int endix = out.indexOf("\"", nix);
	assertTrue(endix > nix);
	log.debug3(out.substring(nix, endix));
	ix = endix;
      }
      ix = out.indexOf("ServeContent?url=" + urlStem, ix);
      assertTrue("wrong url rewritten", ix < 0);
    } catch (Exception ex) {
      fail("createLinkRewriter should not have thrown " + ex +
	   " on html mime type");
    }
  }

  public static void main(String[] argv) {
    String[] testCaseList = {
      TestNodeFilterHtmlLinkRewriterFactory.class.getName()
    };
    junit.textui.TestRunner.main(testCaseList);
  }

}
