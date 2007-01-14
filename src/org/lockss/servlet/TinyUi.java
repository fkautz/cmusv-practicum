/*
 * $Id$
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

package org.lockss.servlet;

import java.io.*;
import java.net.*;
import org.lockss.config.ConfigManager;
import org.lockss.config.Configuration;
import org.lockss.util.*;
import org.lockss.jetty.*;
import org.mortbay.http.*;
import org.mortbay.http.handler.*;
import org.mortbay.jetty.servlet.*;
import javax.servlet.*;
import javax.servlet.http.*;
import org.mortbay.html.*;

/**
 * Starts a tiny context running a tiny servlet that says the cache isn't
 * up, and displays an error mesage from the context.  This is run before
 * the application is running, so must not rely on any services.
 */
public class TinyUi extends BaseServletManager {
  public static final String SERVER_NAME = "TinyUi";
  private static Logger log = Logger.getLogger("TinyUi");

  private String[] tinyData;

  public TinyUi() {
    super(SERVER_NAME);
  }

  public TinyUi(String[] tinyData) {
    this();
    this.tinyData = tinyData;
  }

  /** Entry point to start tiny UI without general daemon startup  */
  public void startTiny() {
    log.debug("Starting");
    Configuration config = ConfigManager.getCurrentConfig();
    setConfig(config, ConfigManager.EMPTY_CONFIGURATION,
	      Configuration.DIFFERENCES_ALL);
    startServlets();
  }

  public void stopTiny() {
    log.debug("Stopping");
    stopServer();
  }

  void setIpFilter(IpAccessHandler ah) {
    super.setIpFilter(ah);
    ah.setLogForbidden(true);
  }

  public void startServlets() {
    try {
      // Create the server
      HttpServer server = new HttpServer();

      // Create a port listener
      server.addListener(new org.mortbay.util.InetAddrPort(port));

      setupAuthRealm();

      configureTinyContexts(server);

      // Start the http server
      startServer(server, port);
    } catch (Exception e) {
      log.warning("Couldn't start servlets", e);
    }
  }

  public void configureTinyContexts(HttpServer server) {
    try {
      setupTinyContext(server);
      setupImageContext(server);
    } catch (Exception e) {
      log.warning("Couldn't start tiny UI contexts", e);
    }
  }

  void setupTinyContext(HttpServer server) throws MalformedURLException {
    HttpContext context = makeContext(server, "/");

    // add handlers in the order they should be tried.

    // user authentication handler
    setContextAuthHandler(context, realm);

    // Add a servlet handler for TinyServlet
    ServletHandler handler = new ServletHandler();
    handler.addServlet("Tiny", "/", TinyServlet.class.getName());
    context.addHandler(handler);

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
    ResourceHandler rHandler = new ResourceHandler();
    rHandler.setDirAllowed(false);
    context.addHandler(rHandler);

    // NotFoundHandler
    context.addHandler(new NotFoundHandler());
  }

  // common context setup
  // adds IpAccessHandler as all contexts want it
  // doesn't add AuthHandler as not all contexts want it
  HttpContext makeContext(HttpServer server, String path) {
    HttpContext context = server.getContext(path);
    context.setAttribute("TinyData", tinyData);
    context.setAttribute(HttpContext.__ErrorHandler,
			 new LockssErrorHandler("daemon")); 
    // In this environment there is no point in consuming memory with
    // cached resources
    context.setMaxCachedFileSize(0);

    // IpAccessHandler is always first handler
//     addAccessHandler(context);
    return context;
  }

  public static class TinyServlet extends HttpServlet {
    private ServletContext context;
    private String[] tinyData;

    public void init(ServletConfig config) throws ServletException {
      super.init(config);
      context = config.getServletContext();
      tinyData = (String[])context.getAttribute("TinyData");
    }

    public void doGet(HttpServletRequest request,
		      HttpServletResponse response)
	throws ServletException, IOException {
      Page page= new Page();
      page.title("LOCKSS cache");
      page.addHeader("");

      Table table = new Table(0, "cellspacing=0 cellpadding=0 align=center");
      table.newRow();
      table.newCell("valign=top align=center");
      table.add(new Link(Constants.LOCKSS_HOME_URL,
			 ServletUtil.IMAGE_LOGO_LARGE));
      table.add(ServletUtil.IMAGE_TM);

      Composite b = new Font(1, true);
      b.add("<br>This LOCKSS cache");
      String name = PlatformUtil.getLocalHostname();
      if (name != null) {
	b.add(" (");
	b.add(name);
	b.add(")");
      }
      b.add(" has not started because ");
      b.add("it is unable to load configuration data.<br>");
      if (tinyData[0] != null) {
	b.add(tinyData[0]);
      }
      table.newRow();
      table.newCell("valign=top align=left");
      table.add(b);
      page.add(table);
      response.setContentType("text/html");
      response.setHeader("Pragma", "no-cache");
      response.setHeader("Cache-Control", "no-cache,no-store");
      Writer writer=response.getWriter();
      page.write(writer);
      writer.flush();
    }
  }
}
