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

package org.lockss.jetty;

import java.util.*;
import org.lockss.app.*;
import org.lockss.util.*;
import org.lockss.config.Configuration;
import org.lockss.daemon.*;
import org.mortbay.http.*;
import org.mortbay.util.Code;

/**
 * Abstract base class for LOCKSS managers that use/start Jetty services.
 * Note: this class may be used in an environment where the LOCKSS app is
 * not running (<i>e.g.</i>, for {@link org.lockss.servlet.TinyUi}), so it
 * must not rely on any non-static app services, nor any other managers.
 */
public abstract class JettyManager
  extends BaseLockssManager implements ConfigurableManager {
  static final String PREFIX = Configuration.PREFIX + "jetty.";
  static final String DEBUG_PREFIX = Configuration.PREFIX + "jetty.debug";

  static final String PARAM_JETTY_DEBUG = DEBUG_PREFIX;
  static final boolean DEFAULT_JETTY_DEBUG = false;

  static final String PARAM_JETTY_DEBUG_PATTERNS = DEBUG_PREFIX + ".patterns";

  static final String PARAM_JETTY_DEBUG_VERBOSE = DEBUG_PREFIX + ".verbose";
  static final int DEFAULT_JETTY_DEBUG_VERBOSE = 0;
//   static final String PARAM_JETTY_DEBUG_OPTIONS = DEBUG_PREFIX + ".options";

  public static final String PARAM_NAMED_SERVER_PRIORITY =
    PREFIX + "<name>.priority";

  private String prioParam;

  private static Logger log = Logger.getLogger("JettyMgr");
  private static boolean isJettyInited = false;

  protected ResourceManager resourceMgr;
  // Used as token in resource reservations, and in messages
  protected String serverName;
  protected HttpServer runningServer;
  protected int ownedPort = -1;

  public JettyManager() {
  }

  public JettyManager(String serverName) {
    this.serverName = serverName;
    prioParam = StringUtil.replaceString(PARAM_NAMED_SERVER_PRIORITY,
					 "<name>", serverName);
  }

  protected String getServerName() {
    return serverName;
  }

  /**
   * Start the manager.  Note: not called by TinyUI.
   * @see org.lockss.app.LockssManager#startService()
   */
  public void startService() {
    super.startService();
    resourceMgr = getApp().getResourceManager();
    oneTimeJettySetup();
    resetConfig();
  }

  // synchronized on class
  private static synchronized void oneTimeJettySetup() {
    // install Jetty logger once only
    if (!isJettyInited) {
      org.mortbay.util.Log.instance().add(new LoggerLogSink());
      // Tell Jetty to allow symbolic links in file resources
      System.setProperty("org.mortbay.util.FileResource.checkAliases",
			 "false");
      isJettyInited = true;
    }
  }

  // Set Jetty debug properties from config params
  public void setConfig(Configuration config, Configuration prevConfig,
			Configuration.Differences changedKeys) {
    if (isJettyInited) {
      if (changedKeys.contains(PARAM_JETTY_DEBUG)) {
	boolean deb = config.getBoolean(PARAM_JETTY_DEBUG, 
					DEFAULT_JETTY_DEBUG);
	log.info("Turning Jetty DEBUG " + (deb ? "on." : "off."));
	Code.setDebug(deb);
      }
      if (changedKeys.contains(PARAM_JETTY_DEBUG_PATTERNS)) {
	String pat = config.get(PARAM_JETTY_DEBUG_PATTERNS);
	log.info("Setting Jetty debug patterns to: " + pat);
	Code.setDebugPatterns(pat);
      }
      if (changedKeys.contains(PARAM_JETTY_DEBUG_VERBOSE)) {
	int ver = config.getInt(PARAM_JETTY_DEBUG_VERBOSE, 
				DEFAULT_JETTY_DEBUG_VERBOSE);
	log.info("Setting Jetty verbosity to: " + ver);
	Code.setVerbose(ver);
      }
    }
    if (runningServer != null && changedKeys.contains(prioParam)) {
      setListenerParams(runningServer);
    }
  }

  long[] delayTime = {10 * Constants.SECOND, 60 * Constants.SECOND, 0};

  protected boolean startServer(HttpServer server, int port) {
    return startServer(server, port, getServerName());
  }

  protected boolean startServer(HttpServer server, int port,
				String serverName) {
    try {
      if (resourceMgr != null &&
	  !resourceMgr.reserveTcpPort(port, serverName)) {
	return false;
      }
      ownedPort = port;
      setListenerParams(server);
      for (int ix = 0; ix < delayTime.length; ix++) {
	try {
	  server.start();
	  runningServer = server;
	  return true;
	} catch (org.mortbay.util.MultiException e) {
	  log.debug("multi", e);
	  log.debug2("first", e.getException(0));
	  log.warning("Addr in use, sleeping " +
		      StringUtil.timeIntervalToString(delayTime[ix]));
	  Deadline.in(delayTime[ix]).sleep();
	}
      }
    } catch (Exception e) {
      log.warning("Couldn't start servlets", e);
    }
    releasePort(serverName);
    ownedPort = -1;
    return false;
  }


  /** Set the priority at which all requests will be handled */
  private void setListenerParams(HttpServer server) {
    String name = getServerName();
    int prio = getPriorityFromParam(name);
    log.debug("Setting priority of " + name + " listener to " + prio);
    HttpListener listeners[] = server.getListeners();
    for (int ix = 0; ix < listeners.length; ix++) {
      if (listeners[ix] instanceof org.mortbay.util.ThreadPool) {
	org.mortbay.util.ThreadPool tpool =
	  (org.mortbay.util.ThreadPool)listeners[ix];
	if (prio != -1) {
	  tpool.setThreadsPriority(prio);
	}
	// Set the name for threads in the pool, to id them in thread
	// dumps.  I think this is suppoesd to be done with
	// ThreadPool.setName(), as setPoolName() affects more stuff, but
	// this is what's necessary in Jetty 4.2.17.
	if (!tpool.isStarted()) {	// can't change name after started
	  tpool.setPoolName(name);
	}
      }
    }
  }

  int getPriorityFromParam(String name) {
    if (prioParam == null) {
      prioParam = StringUtil.replaceString(PARAM_NAMED_SERVER_PRIORITY,
					   "<name>", name);
    }
    return Configuration.getIntParam(prioParam, -1);
  }

  private void releasePort(String serverName) {
    if (resourceMgr != null) {
      resourceMgr.releaseTcpPort(ownedPort, serverName);
      ownedPort = -1;
    }
  }

  protected void stopServer() {
    stopServer(getServerName());
  }

  protected void stopServer(String serverName) {
    try {
      if (runningServer != null) {
	runningServer.stop();
	runningServer = null;
	releasePort(serverName);
      }
    } catch (InterruptedException e) {
      log.warning("Interrupted while stopping server");
    }
  }

  public boolean isServerRunning() {
    return runningServer != null;
  }

  public boolean isRunningOnPort(int port) {
    return ownedPort == port && isServerRunning();
  }

}
