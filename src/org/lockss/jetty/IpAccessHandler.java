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
// ===========================================================================
// Copyright (c) 1996-2002 Mort Bay Consulting Pty. Ltd. All rights reserved.
// $Id$
// ---------------------------------------------------------------------------

package org.lockss.jetty;

import java.io.*;
import java.net.MalformedURLException;
import java.text.DateFormat;
import java.util.*;
import org.mortbay.http.*;
import org.mortbay.http.handler.*;
import org.mortbay.util.*;
import org.lockss.util.*;

/** Extension of ResourceHandler that allows flexibility in finding the
 * Resource.  Mostly copied here because some things in ResourceHandler
 * aren't public or protected. */
public class IpAccessHandler extends AbstractHttpHandler {
  private static Logger log = Logger.getLogger("IpAccess");

  private IpFilter filter = new IpFilter();
  private String serverName;
  private boolean allowLocal = false;
  private Set localIps;
  private boolean logForbidden;

  public IpAccessHandler(String serverName) {
    this.serverName = serverName;
  }

  public void setFilter(IpFilter filter) {
    this.filter = filter;
  }

  public void setLogForbidden(boolean logForbidden) {
    this.logForbidden = logForbidden;
  }

  public void setAllowLocal(boolean allowLocal) {
    if (localIps == null) {
      localIps = new HashSet();
      localIps.add("127.0.0.1");
      // tk - add local interfaces
    }
    this.allowLocal = allowLocal;
  }

  boolean isAuthorized(String ip) throws IpFilter.MalformedException {
    return (filter.isIpAllowed(ip) || (allowLocal && localIps.contains(ip)));
  }

  /**
   * Handles the incoming request
   *
   * @param pathInContext	
   * @param pathParams	
   * @param request	The incoming HTTP-request
   * @param response	The outgoing HTTP-response
   */
  public void handle(String pathInContext,
		     String pathParams,
		     HttpRequest request,
		     HttpResponse response)
      throws HttpException, IOException {

    try	{
      String ip = request.getRemoteAddr();
      boolean authorized = isAuthorized(ip);
		
      if (!authorized) {
	// The IP is NOT allowed
	if (logForbidden) {
	  log.info("Access to " + serverName + " forbidden from " + ip);
	}
	response.sendError(HttpResponse.__403_Forbidden);
	request.setHandled(true);
	return; 
      } else {
	// The IP is allowed
	return;
      }
    } catch (Exception e) {
      log.warning("Error checking IP", e);
      response.sendError(HttpResponse.__500_Internal_Server_Error);
      request.setHandled(true);
    }
  }
}
