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
import java.util.*;
import java.util.List;
import javax.servlet.*;
import org.mortbay.html.*;
import org.lockss.app.*;
import org.lockss.util.*;
import org.lockss.config.ConfigManager;
import org.lockss.config.Configuration;
import org.lockss.daemon.*;
import org.lockss.proxy.*;

/** Display and update proxy IP access control lists.
 */
public class ProxyIpAccess extends IpAccessControl {
  public static final String PARAM_AUDIT_ENABLE =
    AuditProxyManager.PARAM_START;
  static final boolean DEFAULT_AUDIT_ENABLE = AuditProxyManager.DEFAULT_START;
  public static final String PARAM_AUDIT_PORT = AuditProxyManager.PARAM_PORT;

  private static final String exp =
    "Enter the list of IP addresses that should be allowed to use this " +
    "cache as a proxy server, and access the content stored on it.  " +
    commonExp;

  private ResourceManager resourceMgr;
  private boolean formAuditEnable;
  private String formAuditPort;
  private int auditPort;
  
  protected void resetLocals() {
    super.resetLocals();
    formAuditEnable = false;
    formAuditPort = null;
  }

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
    resourceMgr = getLockssApp().getResourceManager();
  }

  protected String getExplanation() {
    return exp;
  }

  protected String getParamPrefix() {
    return ProxyManager.IP_ACCESS_PREFIX;
  }

  protected String getConfigFileName() {
    return ConfigManager.CONFIG_FILE_PROXY_IP_ACCESS;
  }

  protected String getConfigFileComment() {
    return "Proxy IP Access Control";
  }

  protected void readForm() {
    formAuditEnable = !StringUtil.isNullString(req.getParameter("audit_ena"));
    formAuditPort = req.getParameter("audit_port");
    super.readForm();
  }

  protected void doUpdate() throws IOException {
    auditPort = -1;
    try {
      auditPort = Integer.parseInt(formAuditPort);
    } catch (NumberFormatException e) {
      if (formAuditEnable) {
	// bad number is an error only if enabling.  (so field can be blank
	// if checkbox is unchecked)
	errMsg = "Audit proxy port must be a number: " + formAuditPort;
	displayPage();
	return;
      }
    }
    if (formAuditEnable && !isLegalAuditPort(auditPort)) {
      errMsg = "Illegal audit proxy port number: " + formAuditPort +
	", must be >=1024 and not in use";
      displayPage();
      return;
    }
    super.doUpdate();
  }


  boolean isLegalAuditPort(int port) {
    return (port >= 1024 &&
	    resourceMgr.isTcpPortAvailable(port,
					   AuditProxyManager.SERVER_NAME));
  }

  boolean getDefaultAuditEnable() {
    if (isForm) {
      return formAuditEnable;
    }
    return Configuration.getBooleanParam(PARAM_AUDIT_ENABLE,
					 DEFAULT_AUDIT_ENABLE);
  }

  String getDefaultAuditPort() {
    String port = formAuditPort;
    if (StringUtil.isNullString(port)) {
      port = Configuration.getParam(PARAM_AUDIT_PORT);
    }
    return port;
  }

  static final String AUDIT_FOOT =
    "The audit proxy serves <b>only</b> cached content, and never " +
    "forwards requests to the publisher or any other site.  " +
    "By configuring a browser to proxy to this port, you can view the " +
    "content stored on the cache.  All requests for content not on the " +
    "cache will return a \"404 Not Found\" error.";

  static final String FILTER_FOOT =
    "Other ports can be configured but may not be reachable due to " +
    "packet filters.";

  protected Composite getAdditionalFormElement() {
    Table tbl = new Table(0, "align=center cellpadding=10");
    tbl.newRow();
    tbl.newCell("align=center");
    Input enaElem = new Input(Input.Checkbox, "audit_ena", "1");
    if (getDefaultAuditEnable()) {
      enaElem.check();
    }
    setTabOrder(enaElem);
    tbl.add(enaElem);
    tbl.add("Enable audit proxy");
    tbl.add(addFootnote(AUDIT_FOOT));
    tbl.add(" on port&nbsp;");
    
    Input portElem = new Input(Input.Text, "audit_port",
			       getDefaultAuditPort());
    portElem.setSize(6);
    setTabOrder(portElem);
    tbl.add(portElem);
    // avoid breaking anything with this patch
    try {
      List usablePorts =
	resourceMgr.getUsableTcpPorts(AuditProxyManager.SERVER_NAME);
      if (usablePorts != null) {
	tbl.add("<br>");
	if (usablePorts.isEmpty()) {
	  tbl.add("(No available ports)");
	  tbl.add(addFootnote(AUDIT_FOOT));
	} else {
	  tbl.add("Available ports");
	  tbl.add(addFootnote(FILTER_FOOT));
	  tbl.add(": ");
	  tbl.add(StringUtil.separatedString(usablePorts, ", "));
	}
      }
    } catch (Exception ignore) {
    }
    return tbl;
  }

  protected void addConfigProps(Properties props) {
    super.addConfigProps(props);
    props.setProperty(PARAM_AUDIT_ENABLE,  formAuditEnable ? "true" : "false");
    props.put(PARAM_AUDIT_PORT, Integer.toString(auditPort));
  }
}
