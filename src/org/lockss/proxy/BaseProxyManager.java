/*
 * $Id: BaseProxyManager.java,v 1.16 2011/06/21 22:10:35 tlipkis Exp $
 */

/*

Copyright (c) 2000-2007 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.proxy;

import java.net.*;
import java.util.*;

import org.lockss.app.*;
import org.lockss.util.*;
import org.lockss.util.urlconn.*;
import org.lockss.config.Configuration;
import org.lockss.daemon.*;
import org.lockss.jetty.*;
import org.mortbay.util.*;
import org.mortbay.http.*;
import org.mortbay.http.handler.*;

/** Abstract base class for LOCKSS proxy managers.
 */
public abstract class BaseProxyManager extends JettyManager {

  private static Logger log = Logger.getLogger("BaseProxy");

  public static final String PARAM_403_MSG =
    Configuration.PREFIX + "proxy.403Msg";
  public static final String DEFAULT_403_MSG =
    "Access to content in this LOCKSS box is not allowed from your IP address (%IP%)";

  protected int port;
  protected boolean start = false;
  protected String includeIps;
  protected String excludeIps;
  protected boolean logForbidden;
  protected IpAccessHandler accessHandler;
  protected ProxyHandler handler;
  private String _403Msg;
  protected List<String> bindAddrs;

  /* ------- LockssManager implementation ------------------ */
  /**
   * start the proxy.
   * @see org.lockss.app.LockssManager#startService()
   */
  public void startService() {
    super.startService();
    resetConfig();			// run setConfig() unconditionally
					// to set defaults in subclasses
    if (start) {
      startProxy();
    }
  }

  /**
   * stop the plugin manager
   * @see org.lockss.app.LockssManager#stopService()
   */
  public void stopService() {
    stopProxy();
    super.stopService();
  }

  protected LockssDaemon getDaemon() {
    return (LockssDaemon)getApp();
  }

  public void setConfig(Configuration config, Configuration prevConfig,
			Configuration.Differences changedKeys) {
    super.setConfig(config, prevConfig, changedKeys);
    _403Msg = config.get(PARAM_403_MSG, DEFAULT_403_MSG);
  }

  void setIpFilter() {
    if (accessHandler != null) {
      try {
	IpFilter filter = new IpFilter();
	filter.setFilters(includeIps, excludeIps);
	accessHandler.setFilter(filter);
      } catch (IpFilter.MalformedException e) {
	log.warning("Malformed IP filter, filters not changed", e);
      }
      accessHandler.setLogForbidden(logForbidden);
      accessHandler.setAllowLocal(true);
      accessHandler.set403Msg(_403Msg);
    }
  }

  protected void addListeners(HttpServer server) {
    if (/*bindAddrs != null && */bindAddrs.isEmpty()) {
      try {
	addListener(server, null, port);
      } catch (UnknownHostException e) {
	log.critical("UnknownHostException with null host, not starting "
		     + getServerName() + " server");
      }
    } else {
      for (String host : bindAddrs) {
	try {
	  addListener(server, host, port);
	} catch (UnknownHostException e) {
	  log.critical("Bind addr " + host +
		       " not found, " + getServerName() +
		       " not listening on that address");
	}
      }
    }
  }

  protected void addListener(HttpServer server,
			     String host, int port)
      throws UnknownHostException {
    HttpListener listener =
      new SocketListener(new org.mortbay.util.InetAddrPort(host,port));
    server.addListener(listener);
  }

  /** Start a Jetty handler for the proxy.  May be called redundantly, or
   * to change ports.  */
  protected void startProxy() {
    log.debug("StartProxy");
    if (isRunningOnPort(port)) {
      return;
    }
    if (isServerRunning()) {
      stopProxy();
    }
    try {
      // Create the server
      HttpServer server = new HttpServer();

      addListeners(server);

      // Create a context
      HttpContext context = server.getContext(null, "/");

      context.setAttribute(HttpContext.__ErrorHandler,
			   new LockssErrorHandler("proxy"));

      // In this environment there is no point in consuming memory with
      // cached resources
      context.setMaxCachedFileSize(0);

      // ProxyAccessHandler must be first
      accessHandler = new ProxyAccessHandler(getDaemon(), "Proxy");
      setIpFilter();
      context.addHandler(accessHandler);

      // Add a proxy handler to the context
      handler = makeProxyHandler();
      context.addHandler(handler);

      // Add a CuResourceHandler to handle requests for locally cached
      // content that the proxy handler modified and passed on.
      context.setBaseResource(new CuUrlResource());
      LockssResourceHandler rHandler = new CuResourceHandler(getDaemon());
      rHandler.setDirAllowed(false);
//       rHandler.setAcceptRanges(true);
      context.addHandler(rHandler);
      // Requests shouldn't get this far, so dump them
      context.addHandler(new org.mortbay.http.handler.DumpHandler());

      // Start the http server
      startServer(server, port);
    } catch (Exception e) {
      log.error("Couldn't start proxy", e);
    }
  }

  protected void stopProxy() {
    stopServer();
  }

  protected abstract org.lockss.proxy.ProxyHandler makeProxyHandler();
}
