/*
 * $Id$
 *

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
import java.net.*;
import javax.servlet.http.HttpServletRequest;
import org.lockss.plugin.*;
import org.lockss.util.urlconn.*;
import org.lockss.config.Configuration;
import org.lockss.daemon.*;
import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.methods.*;

/** Utilities for URLs and URLConnections
 */
public class UrlUtil {
  private static Logger log = Logger.getLogger("UrlUtil");
  /**
   * The separator string for URLs.
   */
  public static final String URL_PATH_SEPARATOR = "/";
  /**
   * The separator char for URLs.
   */
  public static final char URL_PATH_SEPARATOR_CHAR = '/';

  /** Normalize URL to a canonical form: lowercase scheme and hostname,
   * normalize path.  Removes any reference part.  XXX need to add
   * character escaping
   * @throws MalformedURLException
   */
  public static String normalizeUrl(String urlString)
      throws MalformedURLException {
    log.debug3("Normalizing "+urlString);
    urlString = urlString.trim();	// remove extra spaces
    if ("".equals(urlString)) {		// permit empty
      return urlString;
    }
    URL url = new URL(urlString);

    String protocol = url.getProtocol(); // returns lowercase proto
    String host = url.getHost();
    int port = url.getPort();
    String file = url.getFile();
    if (log.isDebug3()) {
      log.debug3("protocal: "+protocol);
      log.debug3("host: "+host);
      log.debug3("port: "+port);
      log.debug3("file: "+file);
    }


    boolean changed = false;

    if (!urlString.startsWith(protocol)) { // protocol was lowercased
      changed = true;
    }
//     if ("http".equals(protocol) || "ftp".equals(protocol)) {
    if (host != null) {
      String newHost = host.toLowerCase(); // lowercase host
      if (!host.equals(newHost)) {
	host = newHost;
	changed = true;
      }
    }

    if (port == getDefaultPort(protocol)) {
      // if url includes a port that is the default port for the protocol,
      // remove it (by passing port -1 to constructor)
      port = -1;
      changed = true;
    }

    if (StringUtil.isNullString(file)) {
      file = "/";
      changed = true;
    } else {
      String normPath = normalizePath(file);
      if (!normPath.equals(file)) {
	log.debug3("Normalized "+file+" to "+normPath);
	file = normPath;
	changed = true;
      }
    }

    if (url.getRef() != null) {		// remove the ref
      changed = true;
    }
//   }
    if (changed) {
      urlString = new URL(protocol, host, port, file).toString();
//       String query = url.getQuery();
//       log.debug3("Has query string: "+query);
//       if (query != null) {
// 	urlString += "?" + query;
//       }
      log.debug3("Changed, so returning "+urlString);
    }
    return urlString;
  }

  /** Normalize URL to a canonical form for the specified AU.  First
   * applies any plugin-specific normalization, then generic normalization.
   * Disallows changes to protocol, host or port on the theory that such
   * changes constitute more than "normalization".  This might be too
   * strict; transformations such as <code>publisher.com ->
   * www.publisher.com</code> might fall within the scope of normalization.
   * @throws MalformedURLException if the plugin's nromalizer throws, or if
   * the URL it returns is malformed.
   * @throws PluginBehaviorException if the plugin changes the URL in a way
   * it should (<i>e.g.</i>, the protocol)
   */
  public static String normalizeUrl(String url, ArchivalUnit au)
      throws MalformedURLException, PluginBehaviorException {
    String site;
    try {
      site = au.siteNormalizeUrl(url);
    } catch (RuntimeException w) {
      throw new MalformedURLException(url);
    }
    if (site != url) {
      URL origUrl = new URL(url);
      URL siteUrl = new URL(site);
      if (! (origUrl.getProtocol().equals(siteUrl.getProtocol()) &&
	     origUrl.getHost().equals(siteUrl.getHost()) &&
	     origUrl.getPort() == siteUrl.getPort())) {
	throw new PluginBehaviorException("siteNormalizeUrl(" + url +
					  ") altered non-alterable component: " +
				   site);
      }      
    }
    return normalizeUrl(site);
  }

  /** Return the default port for the (already lowercase) protocol */
  // 1.3 URL doesn't expose this
  static int getDefaultPort(String protocol) {
    if ("http".equals(protocol)) return 80;
    if ("https".equals(protocol)) return 443;
    if ("ftp".equals(protocol)) return 21;
    if ("gopher".equals(protocol)) return 70;
    return -1;
  }

  /** Normalize the path component.  Replaces multiple consecutive "/" with
   *  a single "/", removes "." components and resolves ".."  * components.
   *  Exactly mimics the behavior of Java 1.4 URI.normalize(), becuase we
   *  don't want the canonical form to change if/when we switch to that. */
  public static String normalizePath(String path)
      throws MalformedURLException {
    return normalizePath(path, false);
  }

  /* Normalize the path component.  Replaces multiple consecutive "/" with
   *  a single "/", removes "." components and resolves ".."  * components.
   *  Exactly mimics the behavior of Java 1.4 URI.normalize(), becuase we
   *  don't want the canonical form to change if/when we switch to that. */
  public static String normalizePath(String path, boolean evenIfIllegal)
      throws MalformedURLException {
    path = path.trim();
    // special case compatability with Java 1.4 URI
    if (path.equals(".") || path.equals("./")) {
      return "";
    }
    // quickly determine whether anything needs to be done
    if (! (path.endsWith("/.") || path.endsWith("/..") ||
	   path.indexOf("/./") >= 0 || path.indexOf("/../") >= 0 || 
	   path.indexOf("//") >= 0)) {
      return path;
    }

    StringTokenizer st = new StringTokenizer(path, "/");
    List names = new ArrayList();
    int dotdotcnt = 0;
    boolean prevdotdot = false;
    while (st.hasMoreTokens()) {
      String comp = st.nextToken();
      prevdotdot = false;
      if (comp.equals(".")) {
	continue;
      }
      if (comp.equals("..")) {
	if (names.size() > 0) {
	  names.remove(names.size() - 1);
	  prevdotdot = true;
	} else {
	  if (evenIfIllegal) {
	    dotdotcnt++;
	  } else {
	    throw new MalformedURLException(path);
	  }
	}
      } else {
	names.add(comp);
      }
    }

    StringBuffer sb = new StringBuffer();
    if (path.startsWith("/")) {
      sb.append("/");
    }
    for (int ix = dotdotcnt; ix > 0; ix--) {
      if (ix > 1 || !names.isEmpty()) {
	sb.append("../");
      } else {
	sb.append("..");
      }
    }
    StringUtil.separatedString(names, "/", sb);
    if ((path.endsWith("/") || (prevdotdot && !names.isEmpty())) &&
	!(sb.length() == 1 && path.startsWith("/"))) {
      sb.append("/");
    }
    return sb.toString();
  }

  /** Compare two URLs for equality.  Unlike URL.equals(), this does not
   * cause DNS lookups.
   * @param u1 a URL
   * @param u2 a nother URL
   * @return true iff the URLs have the same protocol (case-independent),
   * the same host (case-independent), the same port number on the host,
   * and the same file and anchor on the host.
   */
  public static boolean equalUrls(URL u1, URL u2) {
    return
      u1.getPort() == u2.getPort() &&
      StringUtil.equalStringsIgnoreCase(u1.getProtocol(), u2.getProtocol()) &&
      StringUtil.equalStringsIgnoreCase(u1.getHost(), u2.getHost()) &&
      StringUtil.equalStrings(u1.getFile(), u2.getFile()) &&
      StringUtil.equalStrings(u1.getRef(), u2.getRef());
  }

  /** Return true if an http: or https: url */
  // XXX does this need to trim blanks?
  public static boolean isHttpUrl(String url) {
    return StringUtil.startsWithIgnoreCase(url, "http:") ||
      StringUtil.startsWithIgnoreCase(url, "https:");
  }

  /** Return true if a file: url */
  public static boolean isFileUrl(String url) {
    return StringUtil.startsWithIgnoreCase(url, "file:");
  }

  /** Return true if a jar: url */
  public static boolean isJarUrl(String url) {
    return StringUtil.startsWithIgnoreCase(url, "jar:");
  }

  /**
   * @param urlStr string representation of a url
   * @return urlStr up to but not including the path
   * @throws MalformedURLException if urlStr is not a well formed URL
   */
  public static String getUrlPrefix(String urlStr)
      throws MalformedURLException{
    URL url = new URL(urlStr);
    URL url2 = new URL(url.getProtocol(), url.getHost(), url.getPort(), "");
    return url2.toString();
  }

  /**
   * @param urlStr string representation of a url
   * @return the host portion of the url
   * @throws MalformedURLException if urlStr is not a well formed URL
   */
  public static String getHost(String urlStr) throws MalformedURLException {
    URL url = new URL(urlStr);
    return url.getHost();
  }

  /**
   * @param urlStr string representation of a url
   * @return the domain portion of the hostname
   * @throws MalformedURLException if urlStr is not a well formed URL
   */
  public static String getDomain(String urlStr) throws MalformedURLException {
    String host = getHost(urlStr);
    int pos = host.indexOf('.');
    if (pos == -1 || pos == (host.length() - 1)) {
      return host;
    } else {
      return host.substring(pos + 1);
    }
  }

  /** Reconstructs the URL the client used to make the request, using
   * information in the HttpServletRequest object. The returned URL
   * contains a protocol, server name, port number, and server path, but it
   * does not include query string parameters.  This method duplicates the
   * deprecated method from javax.servlet.http.HttpUtils
   * @param req - a HttpServletRequest object containing the client's request
   * @return string containing the reconstructed URL
   */
  // http://hostname.com:80/mywebapp/servlet/MyServlet/a/b;c=123?d=789
  public static String getRequestURL(HttpServletRequest req) {
    String scheme = req.getScheme();             // http
    String serverName = req.getServerName();     // hostname.com
    int serverPort = req.getServerPort();        // 80
    String contextPath = req.getContextPath();   // /mywebapp
    String servletPath = req.getServletPath();   // /servlet/MyServlet
    String pathInfo = req.getPathInfo();         // /a/b;c=123
//     String queryString = req.getQueryString();          // d=789

    // Reconstruct original requesting URL
    StringBuffer sb = new StringBuffer(40);
    sb.append(scheme);
    sb.append("://");
    sb.append(serverName);
    sb.append(":");
    sb.append(serverPort);
    sb.append(contextPath);
    sb.append(servletPath);
    if (pathInfo != null) {
      sb.append(pathInfo);
    }
//     if (queryString != null) {
//       sb.append("?");
//       sb.append(queryString);
//     }
    return sb.toString();
  }

  public static String minimallyEncodeUrl(String url) {
    url = StringUtil.replaceString(url, " ", "%20");
    url = StringUtil.replaceString(url, "\"", "%22");
    url = StringUtil.replaceString(url, "|", "%7c");
    return url;
  }

  /** Resolve possiblyRelativeUrl relative to baseUrl.
   * @param baseUrl The base URL relative to which to resolve
   * @param possiblyRelativeUrl resolved relative to baseUrl
   * @return The URL formed by combining the two URLs
   */
  public static String resolveUri(String baseUrl, String possiblyRelativeUrl)
      throws MalformedURLException {
    return resolveUri(new URL(baseUrl), possiblyRelativeUrl);
  }

  public static String resolveUri(URL baseUrl, String possiblyRelativeUrl)
      throws MalformedURLException {
    String encodedUri = minimallyEncodeUrl(possiblyRelativeUrl.trim());
    URL url = new URL(baseUrl, encodedUri);
    return url.toString();
  }
  
  /**
   * Takes a javascript url of the following formats:
   * javascript:newWindow('http://www.example.com/link3.html')
   * javascript:popup('http://www.example.com/link3.html')
   * and resolves it to a URL
   */

  public static String[] supportedJSFunctions =
  {
    "newWindow",
    "popup"
  };

  public static String parseJavascriptUrl(String jsUrl) {
    
    int jsIdx = StringUtil.indexOfIgnoreCase(jsUrl, "javascript:");
    if (jsIdx < 0) {
      log.debug("Doesn't appear to be a javascript URL: "+jsUrl);
      return null;
    }

    int protocolEnd = jsIdx + "javascript:".length();
    int funcEnd = -1;

    for (int ix=0; ix<supportedJSFunctions.length && funcEnd==-1; ix++) {
      if (jsUrl.regionMatches(true, protocolEnd,
			      supportedJSFunctions[ix], 0,
			      supportedJSFunctions[ix].length())) {
	funcEnd = protocolEnd + supportedJSFunctions[ix].length();
	log.debug3("matched supported JS function "+supportedJSFunctions[ix]);
	break;
      } 
    }

    if (funcEnd == -1) {
      // if we got here, there was no match
      log.debug("Can't parse js url: "+jsUrl);
      return null;
    }

    int urlStart = funcEnd+1;//+1 to skip the "("
    char firstChar = jsUrl.charAt(urlStart);

    if (firstChar == '\'') {
      urlStart++;
    }
    String url = jsUrl.substring(urlStart); 
    return StringUtil.trimAfterChars(url, ")'");
  }

  // resolveUri() using HttpClient URI.  Allows all protocols (no
  // StreamHandler required), but is more picky about allowable characters,
  // and quite a bit slower.
//   /** Resolve possiblyRelativeUrl relative to baseUrl.
//    * @param baseUrl The base URL relative to which to resolve
//    * @param possiblyRelativeUrl resolved relative to baseUrl
//    * @return The URL formed by combining the two URLs
//    */
//   public static String resolveUri(String baseUrl, String possiblyRelativeUrl)
//       throws MalformedURLException {
//     try {
//       String encodedUri = minimallyEncodeUrl(possiblyRelativeUrl);
//       org.apache.commons.httpclient.URI resultURI =
// 	new org.apache.commons.httpclient.URI(encodedUri.toCharArray());
//       if (resultURI.isRelativeURI()) {
// 	//location is incomplete, use base values for defaults
// 	org.apache.commons.httpclient.URI baseURI =
// 	  new org.apache.commons.httpclient.URI(baseUrl.toCharArray());
// 	resultURI = new org.apache.commons.httpclient.URI(baseURI, resultURI);
//       }
//       return resultURI.toString();
//     } catch (URIException e) {
//       throw new MalformedURLException(e.toString());
//     }
//   }

  public static boolean isAbsoluteUrl(String url) {
    if (url != null) {
      try {
	org.apache.commons.httpclient.URI resultURI =
	  new org.apache.commons.httpclient.URI(url.toCharArray());
	return resultURI.isAbsoluteURI();
      } catch (URIException e) {
      }
    }
    return false;
  }

  /** Return true if both URLs have the same host part */
  public static boolean isSameHost(String url1, String url2) {
    try {
      return getHost(url1).equalsIgnoreCase(getHost(url2));
    } catch (MalformedURLException e) {
      log.warning("isSameHost", e);
      return false;
    }
  }

  /** Return true if <code>to</code> looks like a directory redirection
   * from <code>from</code>; <i>ie</i>, that path has had a slash appended
   * to it. */
  // XXX does this need to be insensitive to differently encoded URLs?
  public static boolean isDirectoryRedirection(String from, String to) {
    if (to.length() != (from.length() + 1)) return false;
    try {
      URL ufrom = new URL(from);
      URL uto = new URL(to);
      String toPath = uto.getPath();
      String fromPath = ufrom.getPath();
      int len = fromPath.length();
      return (
	      toPath.length() == (len + 1) &&
	      toPath.charAt(len) == '/' &&
	      toPath.startsWith(fromPath) &&
	      ufrom.getHost().equalsIgnoreCase(uto.getHost()) &&
	      ufrom.getProtocol().equalsIgnoreCase(uto.getProtocol()) &&
	      ufrom.getPort() == uto.getPort() &&
	      StringUtil.equalStringsIgnoreCase(ufrom.getQuery(),
						uto.getQuery())

	      );
    } catch (MalformedURLException e) {
      return false;
    }
  }

  /**
   * Strips the query string off of a url and returns the rest
   * @param url url string from which to remove the query
   * @return url with the query string stripped, or null if url isn't absolute
   * @throws MalformedURLException if we can't parse the url
   */
  public static String stripQuery(String url) throws MalformedURLException {
    if (url != null) {
      try {
	org.apache.commons.httpclient.URI uri =
	  new org.apache.commons.httpclient.URI(url.toCharArray());
	if (uri.isAbsoluteURI()) {
	  StringBuffer sb = new StringBuffer();
	  sb.append(uri.getScheme());
	  sb.append("://");
	  sb.append(uri.getHost());
	  sb.append(uri.getPath());
	  return sb.toString();
	} 
      } catch (URIException e) {
	throw new MalformedURLException(e.toString());
      }
    }
    return null;
  }

  /**
   * Return a list of header fields (in the format "key;fieldValue") for conn
   * @param conn URLConnection to get headers from
   * @return list of header fields (in the format "key;fieldValue") for conn
   * @throws IllegalArgumentException if a null conn is supplied
   */
  public static List getHeaders(URLConnection conn) {
    if (conn == null) {
      throw new IllegalArgumentException("Called with null URLConnection");
    }
    List returnList = new ArrayList();
    boolean done = false;
    for(int ix=0; !done; ix++) {
      String headerField = conn.getHeaderField(ix);
      String headerFieldKey = conn.getHeaderFieldKey(ix);
      done = (headerField == null && headerFieldKey == null);
      if (!done) {
	returnList.add(headerFieldKey+";"+headerField);
      }
    }
    return returnList;
  }

  /** Return input stream for url iff 200 response code, else throw.
   * @param urlString the url
   * @return an InputStream
   * @throws IOException
   */
  public static InputStream openHttpClientInputStream(String urlString)
      throws IOException {
    log.debug2("openInputStream(\"" + urlString + "\")");
    HttpClient client = new HttpClient();
    HttpMethod method = new GetMethod(urlString);

    // Execute the method.
    int statusCode = -1;
    // retry up to 2 times.
    int attempt = 1;
    while (true) {
      log.debug3("try " + attempt);
      try {
	// execute the method.
	method.addRequestHeader(new Header("user-agent", "lockss"));
	statusCode = client.executeMethod(method);
	if (statusCode == 200) {
	  InputStream ins = method.getResponseBodyAsStream();
	  return ins;
	} else {
	  throw new IOException("Server returned HTTP response code: " +
				statusCode + " for URL: " + urlString);
	}
      } catch (HttpRecoverableException e) {
	if (attempt++ < 2) {
	  log.warning("Recoverable error: " + e.getMessage());
	} else {
	  log.warning("Too many recoverable errors, giving up: " +
		      e.getMessage());
	  throw e;
	}
      } catch (IOException e) {
	throw e;
      }
    }
  }

  /** Return input stream for url.  If url is http or https, uses Jakarta
   * HttpClient, else Java URLConnection.
   * @param urlString the url
   * @return an InputStream
   * @throws IOException
   */
  public static InputStream openInputStream(String urlString)
      throws IOException {
    if (isHttpUrl(urlString)) {
      return openHttpClientInputStream(urlString);
    } else {
      URL url = new URL(urlString);
      URLConnection uc = url.openConnection();
      return uc.getInputStream();
    }
  }

  /** Create and Return a LockssUrlConnection appropriate for the url
   * protocol.  If url is http or https, uses Jakarta HttpClient, else Java
   * URLConnection.
   * @param urlString the url
   * @return a LockssUrlConnection wrapper for the actual url connection
   * @throws IOException
   */
  public static LockssUrlConnection openConnection(String urlString)
      throws IOException {
    return openConnection(urlString, null);
  }

  /** Create and Return a LockssUrlConnection appropriate for the url
   * protocol.  If url is http or https, uses Jakarta HttpClient, else Java
   * URLConnection.
   * @param urlString the url
   * @param connectionPool optional connection pool
   * @return a LockssUrlConnection wrapper for the actual url connection
   * @throws IOException
   */
  public static LockssUrlConnection
    openConnection(String urlString, LockssUrlConnectionPool connectionPool)
      throws IOException {
    return openConnection(LockssUrlConnection.METHOD_GET, urlString,
			  connectionPool);
  }

  /** Create and Return a LockssUrlConnection appropriate for the url
   * protocol.  If url is http or https, uses Jakarta HttpClient, else Java
   * URLConnection.
   * @param urlString the url
   * @param connectionPool optional connection pool
   * @return a LockssUrlConnection wrapper for the actual url connection
   * @throws IOException
   */
  public static LockssUrlConnection
    openConnection(int methodCode, String urlString,
		   LockssUrlConnectionPool connectionPool)
      throws IOException {
    LockssUrlConnection luc;
    if (isHttpUrl(urlString)) {
      boolean useHttpClient =
	Configuration.getBooleanParam("org.lockss.UrlUtil.useHttpClient",
				      true);
      if (useHttpClient) {
	HttpClient client = null;
	if (connectionPool != null) {
	  client = connectionPool.getHttpClient();
	} else {
	  client = new HttpClient();
	}
	luc = new HttpClientUrlConnection(methodCode, urlString, client);
      } else {
	luc = new JavaHttpUrlConnection(urlString);
      }
    } else {
      luc = new JavaUrlConnection(urlString);
    }
    return luc;
  }

//   /** Return input stream for url iff 200 response code, else throw.
//    * In Java 1.1.7, URL.openStream() returns an InputStream in some cases
//    * where it should throw, e.g., a 403 response on a filename that
//    * ends in ".txt".
//    * <br>In Java 1.3 and later this should not be necessary, as an
//    * IOException is thrown in all the right cases.  But there's no harm
//    * in continuing to use it, and it may be handy in the future.
//    * @param urlString the url
//    * @return an InputStream
//    * @throws IOException
//    */
//   public static InputStream openInputStream(String urlString)
//       throws IOException {
//     URL url = new URL(urlString);
//     URLConnection uc = url.openConnection();
//     if (!(uc instanceof HttpURLConnection)) {
//       return uc.getInputStream();
//     }
//     HttpURLConnection huc = (HttpURLConnection)uc;
//     int rc = huc.getResponseCode();
//     if (rc == HttpURLConnection.HTTP_OK) {
//       return huc.getInputStream();
//     } else {
//       throw new IOException("Server returned HTTP response code: " + rc +
// 			    " for URL: " + urlString);
//     }
//   }

}
