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

package org.lockss.proxy;

import java.util.*;
import org.lockss.app.*;
import org.lockss.util.*;
import org.lockss.util.urlconn.*;
import org.lockss.daemon.*;
import org.lockss.jetty.*;
import org.mortbay.util.*;
import org.mortbay.http.*;
import org.mortbay.http.handler.*;

/** Audit proxy manager, starts a proxy that serves only content from the
 * cache, useful for auditing the content.
 */
public class AuditProxyManager extends BaseProxyManager {

  private static Logger log = Logger.getLogger("AuditProxy");
  public static final String PREFIX = Configuration.PREFIX + "proxy.audit.";
  public static final String PARAM_START = PREFIX + "start";
  public static final boolean DEFAULT_START = false;

  public static final String PARAM_PORT = PREFIX + "port";

  protected void setConfig(Configuration config, Configuration prevConfig,
			   Set changedKeys) {
    super.setConfig(config, prevConfig, changedKeys);
    if (changedKeys.contains(ProxyManager.PARAM_IP_INCLUDE) ||
	changedKeys.contains(ProxyManager.PARAM_IP_EXCLUDE) ||
	changedKeys.contains(ProxyManager.PARAM_LOG_FORBIDDEN)) {
      includeIps = config.get(ProxyManager.PARAM_IP_INCLUDE, "");
      excludeIps = config.get(ProxyManager.PARAM_IP_EXCLUDE, "");
      logForbidden = config.getBoolean(ProxyManager.PARAM_LOG_FORBIDDEN,
				       ProxyManager.DEFAULT_LOG_FORBIDDEN);
      log.debug("Installing new ip filter: incl: " + includeIps +
		", excl: " + excludeIps);
      setIpFilter();
    }
    port = config.getInt(PARAM_PORT, -1);
    start = config.getBoolean(PARAM_START, DEFAULT_START);
    if (changedKeys.contains(PARAM_PORT) ||
	changedKeys.contains(PARAM_START)) {
      if (start) {
	if (theDaemon.isDaemonRunning()) {
	  startProxy();
	}
      } else {
	stopProxy();
      }
    }
  }

  // Proxy handler for auditing doesn't make outgoing connections, doesn't
  // need connection pool
  protected org.lockss.proxy.ProxyHandler makeProxyHandler() {
    org.lockss.proxy.ProxyHandler handler =
      new org.lockss.proxy.ProxyHandler(getDaemon());
    handler.setFromCacheOnly(true);
    return handler;
  }
}
