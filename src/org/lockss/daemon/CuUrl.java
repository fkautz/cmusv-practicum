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

package org.lockss.daemon;
import java.io.*;
import java.net.*;
import java.util.*;
import org.mortbay.http.*;
import org.lockss.app.*;
import org.lockss.util.*;
import org.lockss.plugin.*;
import org.lockss.plugin.base.*;

/** A CuUrl is a URL that accesses a CachedUrl, providing access to cached
 * content via the normal java url mechanism. */
public class CuUrl {
  private static Logger log = Logger.getLogger("CuUrl");

  public static final String PROTOCOL = UrlManager.PROTOCOL_CU;
  public static final String PROTOCOL_COLON = PROTOCOL + ":";
  static final int cmp_len = PROTOCOL_COLON.length();

  /** Return true if the supplied URL is an CuUrl.
   * @param url the URL to test.
   * @return true if the protocol in the url is LOCKSS:
   */
  public static boolean isCuUrl(URL url) {
    return PROTOCOL.equalsIgnoreCase(url.getProtocol());
  }

  /** Return true if the supplied URL string is an CuUrl.
   * @param url the string to test.
   * @return true if the protocol in the url is LOCKSS:
   */
  public static boolean isCuUrl(String url) {
    return PROTOCOL_COLON.regionMatches(true, 0, url, 0, cmp_len);
  }

  /** Create a CuUrl from a CachedUrl.
   * @param cu the CachedUrlSet
   * @return a LOCKSSCU: URL that will access the contents of the CachedUrlSet.
   */
  public static URL fromCu(CachedUrl cu)
      throws MalformedURLException {
    return fromCu(cu.getArchivalUnit(), cu);
  }

  /** Create a CuUrl from a CachedUrl.
   * @param cu the CachedUrlSet
   * @param au the cu's ArchivalUnit
   * @return a LOCKSSCU: URL that will access the contents of the CachedUrlSet.
   */
  public static URL fromCu(ArchivalUnit au, CachedUrl cu)
      throws MalformedURLException {
    String auId = au.getAUId();
    String pluginId = au.getPluginId();
    String url = cu.getUrl();
    if (log.isDebug3()) {
      log.debug3("fromCu("+cu+"): pid: " + pluginId + ", auid: " + auId +
		 ", url: " + url);
    }
    URL res = new URL(PROTOCOL, (pluginId + "!" + auId),
		      "/" + URLEncoder.encode(url));
    return res;
  }

  /** A URLConnection to a CachedUrl */
  static class CuUrlConnection extends URLConnection {

    private String urlString;		// string representation of our URL

    private String pluginId;
    private String auId;
    private String cachedUrlString;
    private CachedUrl cu;

    public CuUrlConnection(URL url) 
	throws MalformedURLException, IOException {
      super(url);
      parseUrl(url);
    }

    /** Parse and store identifying info for CachedUrl. */
    private void parseUrl(URL url) throws MalformedURLException {
      urlString = url.toString();
      String id = url.getHost();
      String spec = url.getFile();
      if (spec != null && spec.startsWith("/")) {
	spec = spec.substring(1, spec.length());
      }
      cachedUrlString = URLDecoder.decode(spec);
      if (log.isDebug3()) {
	log.debug3("parseUrl("+url+")");
	log.debug3("id:" + id);
	log.debug3("spec:" + spec);
	log.debug3("cachedUrlString:" + cachedUrlString);
      }
      int pos = id.indexOf('!');
      if (pos == -1) {
	throw new MalformedURLException("no ! found in url host:" + url);
      }

      try {
	pluginId = id.substring(0, pos++);
	auId = id.substring(pos, id.length());
      } catch (Exception e) {
	throw new MalformedURLException(e.toString());
      }
    }

    /** Look up the CachedUrl */
    public void connect() throws IOException {
      if (!connected) {
	PluginManager pluginManager =
	  (PluginManager)LockssDaemon.getManager(LockssDaemon.PLUGIN_MANAGER);
	if (pluginManager == null) {
	  log.warning("connect: no PluginManager");
	  throw new FileNotFoundException(urlString);
	}
	ArchivalUnit au = pluginManager.getAu(pluginId, auId);
	if (au == null) {
	  throw new FileNotFoundException(urlString);
	}
	if (!au.shouldBeCached(urlString)) {
	  throw new FileNotFoundException(urlString);
	}
	cu = au.getAUCachedUrlSet().makeCachedUrl(cachedUrlString);
	if (cu == null || !cu.hasContent()) {
	  throw new FileNotFoundException(urlString);
	}
	connected = true;
      }	    
    }

    public InputStream getInputStream() throws IOException {
      connect();
      InputStream res = cu.openForReading();
      return res;
    }

    public int getContentLength() {
      try {
	connect();
	return Integer.parseInt(getHeaderField(HttpFields.__ContentLength));
      } catch (Exception e) {
	return -1;
      }
    }

    public String getHeaderField(String name) {
      try {
	connect();
      } catch (IOException e) {
	return null;
      }
      Properties props = cu.getProperties();
      return props.getProperty(name);
    }

    public String getContentType() {
      try {
	connect();
      } catch (IOException e) {
	return null;
      }
      return getHeaderField(HttpFields.__ContentType);
    }
  }


}


