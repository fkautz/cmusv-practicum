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

package org.lockss.servlet;

import java.io.*;
import java.net.*;
import java.util.*;
import org.lockss.app.*;
import org.lockss.daemon.*;
import org.lockss.util.*;
import org.lockss.jetty.*;
import org.mortbay.http.*;
import org.mortbay.http.handler.*;
import org.mortbay.jetty.servlet.*;

/**
 * Local UI servlet starter
 */
public class LocalServletManager extends BaseServletManager {
  private static Logger log = Logger.getLogger("ServletMgr");

  private HashUserRealm realm;

  public LocalServletManager() {
    super("UI");
  }

  public void startServlets() {
    try {
      // Create the server
      HttpServer server = new HttpServer();

      // Create a port listener
      HttpListener listener =
	server.addListener(new org.mortbay.util.InetAddrPort(port));

      // create auth realm
      if (doAuth) {
	try {
	  URL propsUrl = this.getClass().getResource(PASSWORD_PROPERTY_FILE);
	  if (propsUrl != null) {
	    log.debug("passwd props file: " + propsUrl);
	    realm = new HashUserRealm(UI_REALM, propsUrl.toString());
	  }
	} catch (IOException e) {
	  log.warning("Error loading admin.props", e);
	}
	if (realm == null) {
	  realm = new HashUserRealm(UI_REALM);
	}
	setConfiguredPasswords(realm);
	if (realm.isEmpty()) {
	  log.warning("No users created, UI is effectively disabled.");
	}
      }

      configureAdminContexts(server);

      // Start the http server
      startServer(server, port);
    } catch (Exception e) {
      log.warning("Couldn't start servlets", e);
    }
  }

  public void configureAdminContexts(HttpServer server) {
    try {
      if (true || logdir != null) {
	// Create a context
	setupLogContext(server, realm, "/log/", logdir);
      }
      // info currently has same auth as /, but could be different
      setupInfoContext(server);

      setupAdminContext(server);

      // no separate image context for now.  (Use if want different
      // access control or auth from / context
      // setupImageContext(server);

    } catch (Exception e) {
      log.warning("Couldn't create admin UI contexts", e);
    }
  }

  void setupAdminContext(HttpServer server) throws MalformedURLException {
    HttpContext context = makeContext(server, "/");

    // add handlers in the order they should be tried.

    // user authentication handler
    setContextAuthHandler(context, realm);

    // Create a servlet container
    ServletHandler handler = new ServletHandler();

    // Request dump servlet
    handler.addServlet("Dump", "/Dump", "org.mortbay.servlet.Dump");
    // Daemon status servlet
    handler.addServlet("JournalConfig", "/AuConfig",
		       "org.lockss.servlet.AuConfig");
    handler.addServlet("DaemonStatus", "/DaemonStatus",
		       "org.lockss.servlet.DaemonStatus");
    handler.addServlet("AdminIpAccess", "/AdminIpAccess",
		       "org.lockss.servlet.AdminIpAccess");
    handler.addServlet("ProxyIpAccess", "/ProxyIpAccess",
		       "org.lockss.servlet.ProxyIpAccess");
    handler.addServlet("Hash CUS", "/HashCUS",
		       "org.lockss.servlet.HashCUS");
    handler.addServlet("Raise Alert", "/RaiseAlert",
		       "org.lockss.servlet.RaiseAlert");
    addServletIfAvailable(handler, "ThreadDump", "/ThreadDump",
			  "org.lockss.servlet.ThreadDump");
    addServletIfAvailable(handler, "Api", "/Api",
			  "org.lockss.ui.servlet.Api");
    context.addHandler(handler);

    // ResourceHandler should come after servlets
    // find the htdocs directory, set as resource base
    ClassLoader loader = Thread.currentThread().getContextClassLoader();
    URL resourceUrl=loader.getResource("org/lockss/htdocs/");
    log.debug("Resource URL: " + resourceUrl);

    context.setResourceBase(resourceUrl.toString());
    LockssResourceHandler rHandler = new LockssResourceHandler();
    rHandler.setDirAllowed(false);
    //       rHandler.setAcceptRanges(true);
    context.addHandler(rHandler);

    // NotFoundHandler
    context.addHandler(new NotFoundHandler());

    //       context.addHandler(new DumpHandler());
  }

  void setupImageContext(HttpServer server) throws MalformedURLException {
    HttpContext context = makeContext(server, "/images");

    // add handlers in the order they should be tried.

    // ResourceHandler for /images dir
    // find the htdocs directory, set as resource base
    ClassLoader loader = Thread.currentThread().getContextClassLoader();
    URL resourceUrl=loader.getResource("org/lockss/htdocs/images/");
    log.debug("Images resource URL: " + resourceUrl);

    context.setResourceBase(resourceUrl.toString());
    LockssResourceHandler rHandler = new LockssResourceHandler();
    context.addHandler(rHandler);

    // NotFoundHandler
    context.addHandler(new NotFoundHandler());
  }

  void setupInfoContext(HttpServer server) {
    HttpContext context = makeContext(server, "/info");

    // add handlers in the order they should be tried.

    // user authentication handler
    setContextAuthHandler(context, realm);

    // Create a servlet container
    ServletHandler handler = new ServletHandler();
    handler.addServlet("ProxyInfo", "/ProxyInfo",
		       "org.lockss.servlet.ProxyConfig");
    context.addHandler(handler);

    // NotFoundHandler
    context.addHandler(new NotFoundHandler());
  }

  // common context setup
  // adds IpAccessHandler as all contexts want it
  // doesn't add AuthHandler as not all contexts want it
  HttpContext makeContext(HttpServer server, String path) {
    HttpContext context = server.getContext(path);
    context.setAttribute("LockssApp", theApp);
    // In this environment there is no point in consuming memory with
    // cached resources
    context.setMaxCachedFileSize(0);

    // IpAccessHandler is always first handler
    addAccessHandler(context);
    return context;
  }

}
