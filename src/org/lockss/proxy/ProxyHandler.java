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
// Some portions of this code are:
// ========================================================================
// Copyright (c) 2003 Mort Bay Consulting (Australia) Pty. Ltd.
// $Id$
// ========================================================================

package org.lockss.proxy;

import java.io.*;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import org.mortbay.http.*;
import org.mortbay.http.handler.*;
import org.mortbay.util.*;
import org.lockss.util.*;
import org.lockss.util.urlconn.*;
import org.lockss.app.*;
import org.lockss.config.Configuration;
import org.lockss.daemon.*;
import org.lockss.plugin.*;
import org.apache.commons.httpclient.util.*;

/* ------------------------------------------------------------ */
/** Proxy request handler.  A HTTP/1.1 Proxy with special handling for
 * content in a LOCKSS cache.  Uses the JVMs URL implementation or
 * LockssUrlConnection to make proxy requests.  Serves content out of cache
 * if not available from publisher.
 * <P>The HttpTunnel mechanism is also used to implement the CONNECT method.
 * 
 * @author Greg Wilkins (gregw)
 * @author tal
 */
public class ProxyHandler extends AbstractHttpHandler {
  private static Logger log = Logger.getLogger("ProxyHandler");

  static final String LOCKSS_PROXY_ID = "1.1 (LOCKSS/jetty)";

  /** Force the proxy to serve only locally cached content.  Mainly useful
   * in testing. */
  static final String PARAM_NEVER_PROXY =
    Configuration.PREFIX + "proxy.neverProxy";
  static final boolean DEFAULT_NEVER_PROXY = false;

  private LockssDaemon theDaemon = null;
  private PluginManager pluginMgr = null;
  private ProxyManager proxyMgr = null;
  private LockssUrlConnectionPool connPool = null;
  private LockssUrlConnectionPool quickFailConnPool = null;
  private boolean neverProxy = DEFAULT_NEVER_PROXY;
  private boolean isFailOver = false;
  private URI failOverTargetUri;

  ProxyHandler(LockssDaemon daemon) {
    theDaemon = daemon;
    pluginMgr = theDaemon.getPluginManager();
    proxyMgr = theDaemon.getProxyManager();
    neverProxy = Configuration.getBooleanParam(PARAM_NEVER_PROXY,
					       DEFAULT_NEVER_PROXY);
  }

  ProxyHandler(LockssDaemon daemon, LockssUrlConnectionPool pool) {
    this(daemon);
    this.connPool = pool;
    this.quickFailConnPool = pool;
  }

  ProxyHandler(LockssDaemon daemon, LockssUrlConnectionPool pool,
	       LockssUrlConnectionPool quickFailConnPool) {
    this(daemon, pool);
    this.quickFailConnPool = quickFailConnPool;
  }

  /** If set to true, content will be served only from the cache; requests
   * will never be proxied */
  public void setFromCacheOnly(boolean flg) {
    neverProxy = flg;
  }

  /** Set a target to use as a base (protocol, host, port) for all incoming
   * request URLs.  To be used to cause the proxy to serve locally cached
   * content in response to direct (non-proxy) GET requests. */
  public void setProxiedTarget(String target) {
    failOverTargetUri = new URI(target);
    isFailOver = true;
  }

  protected int _tunnelTimeoutMs=250;
    
  /* ------------------------------------------------------------ */
  /** Map of leg by leg headers (not end to end).
   * Should be a set, but more efficient string map is used instead.
   */
  protected StringMap _DontProxyHeaders = new StringMap();
  {
    Object o = new Object();
    _DontProxyHeaders.setIgnoreCase(true);
    _DontProxyHeaders.put(HttpFields.__ProxyConnection,o);
    _DontProxyHeaders.put(HttpFields.__Connection,o);
    _DontProxyHeaders.put(HttpFields.__KeepAlive,o);
    _DontProxyHeaders.put(HttpFields.__TransferEncoding,o);
    _DontProxyHeaders.put(HttpFields.__TE,o);
    _DontProxyHeaders.put(HttpFields.__Trailer,o);
    _DontProxyHeaders.put(HttpFields.__ProxyAuthorization,o);
    _DontProxyHeaders.put(HttpFields.__ProxyAuthenticate,o);
    _DontProxyHeaders.put(HttpFields.__Upgrade,o);
  }
    
  /* ------------------------------------------------------------ */
  /**  Map of allows schemes to proxy
   * Should be a set, but more efficient string map is used instead.
   */
  protected StringMap _ProxySchemes = new StringMap();
  {
    Object o = new Object();
    _ProxySchemes.setIgnoreCase(true);
    _ProxySchemes.put(HttpMessage.__SCHEME,o);
    _ProxySchemes.put(HttpMessage.__SSL_SCHEME,o);
    _ProxySchemes.put("ftp",o);
  }
    
  /* ------------------------------------------------------------ */
  public int getTunnelTimeoutMs() {
    return _tunnelTimeoutMs;
  }
    
  /* ------------------------------------------------------------ */
  /** Tunnel timeout.
   * IE on win2000 has connections issues with normal timeout handling.
   * This timeout should be set to a low value that will expire to allow IE to
   * see the end of the tunnel connection.
   * /
   public void setTunnelTimeoutMs(int ms) {
   _tunnelTimeoutMs = ms;
   }
    
   /* ------------------------------------------------------------ */
  public void handle(String pathInContext,
		     String pathParams,
		     HttpRequest request,
		     HttpResponse response)
      throws HttpException, IOException {
    URI uri = request.getURI();
        
    // Is this a CONNECT request?
    if (HttpRequest.__CONNECT.equals(request.getMethod())) {
      response.setField(HttpFields.__Connection,"close"); // XXX Needed for IE????
      handleConnect(pathInContext,pathParams,request,response);
      return;
    }
        
    if (log.isDebug3()) {
      log.debug3("pathInContext="+pathInContext);
      log.debug3("URI="+uri);
    }
    if (isFailOver) {
      if (uri.getHost() == null && failOverTargetUri.getHost() != null) {
	uri.setHost(failOverTargetUri.getHost());
	uri.setPort(failOverTargetUri.getPort());
	uri.setScheme(failOverTargetUri.getScheme());
      }
      if (log.isDebug2()) log.debug2("Failover URI: " + uri);
    } else {
      // XXX what to do here?
    }

    String urlString = uri.toString();
    CachedUrl cu = pluginMgr.findMostRecentCachedUrl(urlString);
    boolean isRepairRequest = proxyMgr.isRepairRequest(request);

    if (log.isDebug2()) {
      log.debug2("cu: " + (isRepairRequest ? "(repair) " : "") + cu);
    }
    if (isRepairRequest || neverProxy) {
      if (cu != null && cu.hasContent()) {
	if (isRepairRequest && log.isDebug()) {
	  log.debug("Serving repair to " + request.getRemoteAddr() + ", " + cu);
	}
	serveFromCache(pathInContext, pathParams, request,
		       response, cu);
	return;
      } else {
      // Don't forward request if it's a repair or we were told not to.
	response.sendError(HttpResponse.__404_Not_Found);
	request.setHandled(true);
	return; 
      }
    }
    if (UrlUtil.isHttpUrl(urlString)) {
      if (HttpRequest.__GET.equals(request.getMethod())) {
	doLockss(pathInContext, pathParams, request, response, urlString, cu);
	return;
      }
    }
    doSun(pathInContext, pathParams, request, response);
  }

  /** Proxy a connection using Java's native URLConection */
  void doSun(String pathInContext,
	     String pathParams,
	     HttpRequest request,
	     HttpResponse response) throws IOException {
    URI uri = request.getURI();
    try {
      // Do we proxy this?
      URL url=isProxied(uri);
      if (url==null) {
	if (isForbidden(uri))
	  sendForbid(request,response,uri);
	return;
      }
            
      Code.debug("PROXY URL=",url);

      URLConnection connection = url.openConnection();
      connection.setAllowUserInteraction(false);
            
      // Set method
      HttpURLConnection http = null;
      if (connection instanceof HttpURLConnection) {
	http = (HttpURLConnection)connection;
	http.setRequestMethod(request.getMethod());
	http.setInstanceFollowRedirects(false);
      }

      // check connection header
      String connectionHdr = request.getField(HttpFields.__Connection);
      if (connectionHdr!=null &&
	  (connectionHdr.equalsIgnoreCase(HttpFields.__KeepAlive)||
	   connectionHdr.equalsIgnoreCase(HttpFields.__Close)))
	connectionHdr=null;

      // copy headers
      boolean xForwardedFor=false;
      boolean hasContent=false;
      Enumeration enum = request.getFieldNames();

      while (enum.hasMoreElements()) {
	// XXX could be better than this!
	String hdr=(String)enum.nextElement();

	if (_DontProxyHeaders.containsKey(hdr))
	  continue;

	if (connectionHdr!=null && connectionHdr.indexOf(hdr)>=0)
	  continue;

	if (HttpFields.__ContentType.equalsIgnoreCase(hdr))
	  hasContent=true;

	xForwardedFor |=
	  HttpFields.__XForwardedFor.equalsIgnoreCase(hdr);

	Enumeration vals = request.getFieldValues(hdr);
	while (vals.hasMoreElements()) {
	  String val = (String)vals.nextElement();
	  if (val!=null) {
	    //                         connection.addRequestProperty(hdr,val);
	    connection.setRequestProperty(hdr, val);
	  }
	}
      }

      // Proxy headers
      connection.setRequestProperty("Via", LOCKSS_PROXY_ID);
      if (!xForwardedFor) {
	// XXX Should be addRequest... , but that doesn't exist in 1.3
	// connection.addRequestProperty(HttpFields.__XForwardedFor,
	//                               request.getRemoteAddr());
	connection.setRequestProperty(HttpFields.__XForwardedFor,
				      request.getRemoteAddr());

      }
      // a little bit of cache control
      String cache_control = request.getField(HttpFields.__CacheControl);
      if (cache_control!=null &&
	  (cache_control.indexOf("no-cache")>=0 ||
	   cache_control.indexOf("no-store")>=0))
	connection.setUseCaches(false);

      // customize Connection
      customizeConnection(pathInContext,pathParams,request,connection);
            
      try {    
	connection.setDoInput(true);
                
	// do input thang!
	InputStream in=request.getInputStream();
	if (hasContent) {
	  connection.setDoOutput(true);
	  IO.copy(in,connection.getOutputStream());
	}
                
	// Connect
	connection.connect();    
      } catch (Exception e) {
	Code.ignore(e);
      }
            
      InputStream proxy_in = null;

      // handler status codes etc.
      int code=HttpResponse.__500_Internal_Server_Error;
      if (http!=null) {
	proxy_in = http.getErrorStream();
                
	code=http.getResponseCode();
	response.setStatus(code);
	response.setReason(http.getResponseMessage());
      }
            
      if (proxy_in==null) {
	try {proxy_in=connection.getInputStream();}
	catch (Exception e) {
	  Code.ignore(e);
	  proxy_in = http.getErrorStream();
	}
      }
            
      // clear response defaults.
      response.removeField(HttpFields.__Date);
      response.removeField(HttpFields.__Server);
            
      // set response headers
      int h=0;
      String hdr=connection.getHeaderFieldKey(h);
      String val=connection.getHeaderField(h);
      while(hdr!=null || val!=null) {
	if (hdr!=null && val!=null && !_DontProxyHeaders.containsKey(hdr))
	  response.addField(hdr,val);
	h++;
	hdr=connection.getHeaderFieldKey(h);
	val=connection.getHeaderField(h);
      }
      response.setField("Via", LOCKSS_PROXY_ID);

      // Handled
      request.setHandled(true);
      if (proxy_in!=null)
	IO.copy(proxy_in,response.getOutputStream());
            
    } catch (Exception e) {
      Code.warning(e.toString());
      Code.ignore(e);
      if (!response.isCommitted())
	response.sendError(HttpResponse.__400_Bad_Request);
    }
  }

  /** Proxy a connection using LockssUrlConnection */
  void doLockss(String pathInContext,
		String pathParams,
		HttpRequest request,
		HttpResponse response,
		String urlString,
		CachedUrl cu) throws IOException {

    boolean isInCache = cu != null && cu.hasContent();

    LockssUrlConnection conn = null;
    try {
      conn =
	UrlUtil.openConnection(LockssUrlConnection.METHOD_PROXY,
			       UrlUtil.minimallyEncodeUrl(urlString),
			       (isInCache ? quickFailConnPool : connPool));

      conn.setFollowRedirects(false);

      // check connection header
      String connectionHdr = request.getField(HttpFields.__Connection);
      if (connectionHdr!=null &&
	  (connectionHdr.equalsIgnoreCase(HttpFields.__KeepAlive)||
	   connectionHdr.equalsIgnoreCase(HttpFields.__Close)))
	connectionHdr=null;

      // copy request headers into new request
      boolean xForwardedFor=false;
      boolean hasContent=false;
      String ifModified = null;

      for (Enumeration enum = request.getFieldNames();
	   enum.hasMoreElements(); ) {
	String hdr=(String)enum.nextElement();

	if (_DontProxyHeaders.containsKey(hdr)) continue;

	if (connectionHdr!=null && connectionHdr.indexOf(hdr)>=0) continue;

	if (HttpFields.__ContentType.equalsIgnoreCase(hdr)) hasContent=true;

	xForwardedFor |= HttpFields.__XForwardedFor.equalsIgnoreCase(hdr);

	if (isInCache) {
	  if (HttpFields.__IfModifiedSince.equalsIgnoreCase(hdr)) {
	    ifModified = request.getField(hdr);
	    continue;
	  }
	}

	Enumeration vals = request.getFieldValues(hdr);
	while (vals.hasMoreElements()) {
	  String val = (String)vals.nextElement();
	  if (val!=null) {
	    conn.addRequestProperty(hdr, val);
	  }
	}
      }

      // If the user sent an if-modified-since header, use it unless the
      // cache file has a later last-modified
      if (isInCache) {
	CIProperties cuprops = cu.getProperties();
	String cuLast = cuprops.getProperty(CachedUrl.PROPERTY_LAST_MODIFIED);
	if (log.isDebug3()) {
	  log.debug3("ifModified: " + ifModified);
	  log.debug3("cuLast: " + cuLast);
	}
	if (cuLast != null) {
	  if (ifModified == null) {
	    ifModified = cuLast;
	  } else {
	    try {
	      if (isEarlier(ifModified, cuLast)) {
		ifModified = cuLast;
	      }
	    } catch (DateParseException e) {
	      // preserve user's header if parse failure
	    }
	  }
	}
      }

      if (ifModified != null) {
	conn.setRequestProperty(HttpFields.__IfModifiedSince, ifModified);
      }

      // Proxy-specifix headers
      conn.setRequestProperty("Via", LOCKSS_PROXY_ID);
      if (!xForwardedFor) {
	conn.addRequestProperty(HttpFields.__XForwardedFor,
				request.getRemoteAddr());

      }

      // If we ever handle input, this is (more-or-less) the HttpClient way
      // to do it

      // if (method instanceof EntityEnclosingMethod) {
      //   EntityEnclosingMethod emethod = (EntityEnclosingMethod) method;
      //   emethod.setRequestBody(conn.getInputStream());
      // }
                
      // Send the request

      try {
	conn.execute();
      } catch (IOException e) {
	// if we get any error and it's in the cache, serve it from there
	if (isInCache) {
	  serveFromCache(pathInContext, pathParams, request, response, cu);
	} else {
	  // else generate an error page
	  sendProxyErrorPage(e, request, response);
	  request.setHandled(true);
	}
	return;
      }
      // We got a response, should we prefer it to what's in the cache?
      if (isInCache && preferCacheOverPubResponse(cu, conn)) {
	serveFromCache(pathInContext, pathParams, request,
		       response, cu);
	return;
      }	

      // return response from server
      response.setStatus(conn.getResponseCode());
      response.setReason(conn.getResponseMessage());

      InputStream proxy_in = conn.getResponseInputStream();
            
      // clear response defaults.
      response.removeField(HttpFields.__Date);
      response.removeField(HttpFields.__Server);
            
      // copy response headers
      for (int ix = 0; ; ix ++) {
	String hdr = conn.getResponseHeaderFieldKey(ix);
	String val = conn.getResponseHeaderFieldVal(ix);

	if (hdr==null && val==null) {
	  break;
	}
	if (hdr!=null && val!=null && !_DontProxyHeaders.containsKey(hdr)) {
	  response.addField(hdr,val);
	}
      }
      response.setField("Via", LOCKSS_PROXY_ID);

      // Handled
      request.setHandled(true);
      if (proxy_in!=null) {
	IO.copy(proxy_in,response.getOutputStream());
      }            
    } catch (Exception e) {
      log.error("doLockss error:", e);
      if (!response.isCommitted())
	response.sendError(HttpResponse.__500_Internal_Server_Error);
    } finally {
      safeReleaseConn(conn);
    }
  }

  void safeReleaseConn(LockssUrlConnection conn) {
    if (conn != null) {
      try {
	conn.release();
      } catch (Exception e) {}
    }
  }
    
  boolean isEarlier(String datestr1, String datestr2)
      throws DateParseException {
    // common case, no conversion necessary
    if (datestr1.equalsIgnoreCase(datestr2)) return false;
    long d1 = DateParser.parseDate(datestr1).getTime();
    long d2 = DateParser.parseDate(datestr2).getTime();
    return d1 < d2;
  }

  // return true to pass the request along to the resource handler to
  // (conditionally) serve from the CachedUrl, false to return the server's
  // response to the user.
  boolean preferCacheOverPubResponse(CachedUrl cu, LockssUrlConnection conn) {
    if (cu == null || !cu.hasContent()) {
      return false;
    }
    int code=conn.getResponseCode();

    // Current policy is to serve from cache unless server supplied content.
    switch (code) {
    case HttpResponse.__200_OK:
      return false;
    }
    return true;
  }
    
  /* ------------------------------------------------------------ */
  public void handleConnect(String pathInContext,
			    String pathParams,
			    HttpRequest request,
			    HttpResponse response)
      throws HttpException, IOException {
    URI uri = request.getURI();
        
    try {
      Code.debug("CONNECT: ",uri);
      InetAddrPort addrPort=new InetAddrPort(uri.toString());

      if (isForbidden(HttpMessage.__SSL_SCHEME, false)) {
	sendForbid(request,response,uri);
      } else {
	Socket socket =
	  new Socket(addrPort.getInetAddress(),addrPort.getPort());

	// XXX - need to setup semi-busy loop for IE.
	int timeoutMs=30000;
	if (_tunnelTimeoutMs > 0) {
	  socket.setSoTimeout(_tunnelTimeoutMs);
	  Object maybesocket = request.getHttpConnection().getConnection();
	  try {
	    Socket s = (Socket) maybesocket;
	    timeoutMs=s.getSoTimeout();
	    s.setSoTimeout(_tunnelTimeoutMs);
	  } catch (Exception e) {
	    Code.ignore(e);
	  }
	}
                
	customizeConnection(pathInContext,pathParams,request,socket);
	request.getHttpConnection().setHttpTunnel(new HttpTunnel(socket,
								 timeoutMs));
	response.setStatus(HttpResponse.__200_OK);
	response.setContentLength(0);
	request.setHandled(true);
      }
    } catch (Exception e) {
      Code.ignore(e);
      response.sendError(HttpResponse.__500_Internal_Server_Error);
    }
  }
    
  /* ------------------------------------------------------------ */
  /** Customize proxy Socket connection for CONNECT.
   * Method to allow derived handlers to customize the tunnel sockets.
   *
   */
  protected void customizeConnection(String pathInContext,
				     String pathParams,
				     HttpRequest request,
				     Socket socket)
      throws IOException {
  }
    
        
  /* ------------------------------------------------------------ */
  /** Customize proxy URL connection.
   * Method to allow derived handlers to customize the connection.
   */
  protected void customizeConnection(String pathInContext,
				     String pathParams,
				     HttpRequest request,
				     URLConnection connection)
      throws IOException {
  }
    
    
  /* ------------------------------------------------------------ */
  /** Is URL Proxied.  Method to allow derived handlers to select which
   * URIs are proxied and to where.
   * @param uri The requested URI, which should include a scheme, host and
   * port.
   * @return The URL to proxy to, or null if the passed URI should not be
   * proxied.  The default implementation returns the passed uri if
   * isForbidden() returns true.
   */
  protected URL isProxied(URI uri) throws MalformedURLException {
    // Is this a proxy request?
    if (isForbidden(uri))
      return null;
        
    // OK return URI as untransformed URL.
    return new URL(uri.toString());
  }
    

  /* ------------------------------------------------------------ */
  /** Is URL Forbidden.
   * 
   * @return True if the URL is not forbidden. Calls
   * isForbidden(scheme,true);
   */
  protected boolean isForbidden(URI uri) {
    String scheme=uri.getScheme();
    String host=uri.getHost();
    int port = uri.getPort();
    return isForbidden(scheme,true);
  }
    

  /* ------------------------------------------------------------ */
  /** Is scheme,host & port Forbidden.
   *
   * @param scheme A scheme that mast be in the proxySchemes StringMap.
   * @param openNonPrivPorts If true ports greater than 1024 are allowed.
   * @return  True if the request to the scheme,host and port is not forbidden.
   */
  protected boolean isForbidden(String scheme, boolean openNonPrivPorts) {
    // Must be a scheme that can be proxied.
    if (scheme==null || !_ProxySchemes.containsKey(scheme))
      return true;

    return false;
  }
    
  /* ------------------------------------------------------------ */
  /** Send Forbidden.
   * Method called to send forbidden response. Default implementation calls
   * sendError(403)
   */
  protected void sendForbid(HttpRequest request, HttpResponse response,
			    URI uri)
      throws IOException {
    response.sendError(HttpResponse.__403_Forbidden,"Forbidden for Proxy");
  }


  /**
   * Add a Lockss-Cu: field to the request with the locksscu: url to serve
   * from the cache, then allow request to be passed on to a
   * LockssResourceHandler.
   * @param pathInContext the path
   * @param pathParams params
   * @param request the HttpRequest
   * @param response the HttpResponse
   * @param cu the CachedUrl
   * @throws HttpException
   * @throws IOException
   */
  // XXX Should this use jetty's request forwarding mechanism instead?
  private void serveFromCache(String pathInContext,
			      String pathParams,
			      HttpRequest request,
			      HttpResponse response,
			      CachedUrl cu)
      throws HttpException, IOException {

    // Save current state then make request editable
    int oldState = request.getState();
    request.setState(HttpMessage.__MSG_EDITABLE);
    request.setField("Lockss-Cu", CuUrl.fromCu(cu).toString());
    request.setState(oldState);
    // Add a header to the response to identify content from LOCKSS cache
    response.setField(Constants.X_LOCKSS, Constants.X_LOCKSS_FROM_CACHE);
    if (log.isDebug2()) {
      log.debug2("serveFromCache(" + cu + ")");
    }
    // Return without handling the request, next in chain is
    // LockssResourceHandler.  (There must be a better way to do this.)
  }

  void sendProxyErrorPage(IOException e,
			  HttpRequest request,
			  HttpResponse response)
      throws IOException {
    URI uri = request.getURI();
    if (e instanceof java.net.UnknownHostException) {
      // DNS failure
      sendErrorPage(request, response, 502,
		    "Unable to connect to", uri.getHost(), "Unknown host");
      return;
    }
    if (e instanceof java.net.NoRouteToHostException) {
      sendErrorPage(request, response, 502,
		    "Unable to connect to", uri.getHost(), "No route to host");
      return;
    }
    if (e instanceof LockssUrlConnection.ConnectionTimeoutException) {
      sendErrorPage(request, response, 504,
		    "Unable to connect to", uri.getHost(),
		    "Host not responding");
      return;
    }
    if (e instanceof java.net.ConnectException) {
      sendErrorPage(request, response, 502, "Unable to connect to",
		    uri.getHost() +
		    (uri.getPort() != 80 ? (":" + uri.getPort()) : ""),
		    "Connection refused");
      return;
    }
    if (e instanceof java.io.InterruptedIOException) {
      sendErrorPage(request, response, 504,
		    "Timeout waiting for data from", uri.getHost(),
		    "Server not responding");
      return;
    }
    if (e instanceof java.io.IOException) {
      sendErrorPage(request, response, 502,
		    "Error communicating with", uri.getHost(), e.getMessage());
      return;
    }
  }

  void sendErrorPage(HttpRequest request,
		     HttpResponse response,
		     int code, String msg, String host, String reason)
      throws HttpException, IOException {
    response.setStatus(code);
    Integer codeInt = new Integer(code);
    String respMsg = (String)HttpResponse.__statusMsg.get(codeInt);
    if (respMsg == null) {
      respMsg = Integer.toString(code);
    }
    response.setReason(respMsg);

    response.setContentType(HttpFields.__TextHtml);
    ByteArrayISO8859Writer writer = new ByteArrayISO8859Writer(2048);

    host = HtmlUtil.htmlEncode(host);
    reason = HtmlUtil.htmlEncode(reason);
    URI uri = request.getURI();

    writer.write("<html>\n<head>\n<title>Error ");
    writer.write(Integer.toString(code));
    writer.write(' ');
    writer.write(respMsg);
    writer.write("</title>\n<body>\n<h2>Proxy Error (");
    writer.write(code + " " + respMsg);
    writer.write(")</h2>");
    writer.write("<font size=+1>");
    writer.write(msg);
    writer.write(" <b>");
    writer.write(host);
    writer.write("</b>: ");
    writer.write(reason);
    writer.write("<br>Attempting to proxy request for <b>");
    writer.write(uri.toString());
    writer.write("</b>");
    writer.write("</font>");
    writer.write("<br>");
    writer.write("</p>\n<p><i><small>");
    writer.write("<a href=\"" + Constants.LOCKSS_HOME_URL +
		 "\">LOCKSS proxy</a>, ");
    writer.write("<a href=\"http://jetty.mortbay.org/\">powered by Jetty</a>");
    writer.write("</small></i></p>");
    writer.write("\n</body>\n</html>\n");
    writer.flush();
    response.setContentLength(writer.size());
    writer.writeTo(response.getOutputStream());
    writer.destroy();
  }
}
