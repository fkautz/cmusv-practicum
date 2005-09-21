/*
 * $Id$
 */

/*

Copyright (c) 2000-2005 Board of Trustees of Leland Stanford Jr. University,
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

import javax.servlet.*;
import java.io.*;
import org.mortbay.html.*;

/** UiHome servlet
 */
public class UiHome extends LockssServlet {

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }

  /** Handle a request */
  public void lockssHandleRequest() throws IOException {
    Page page = newPage();
    resp.setContentType("text/html");
    page.add(getHomeHeader());
    Table nav = getMainNavTable();
    page.add(nav);
    page.add(getFooter());
    page.write(resp.getWriter());
  }

  protected Table getHomeHeader() {
    Table tab = new Table(0, "align=center width=\"80%\"");
    tab.newRow();
    tab.newCell("align=center");
    tab.add("Welcome to the administration page for LOCKSS cache <b>");
    tab.add(getMachineName());
    tab.add("</b>.");
    tab.newRow();
    tab.newCell("align=center");
    tab.add("&nbsp;");
    return tab;
  }

  protected Table getMainNavTable() {
    Table navTable = new Table(0, "cellspacing=2 cellpadding=4 align=center");

    for (int i = 0; i < servletDescrs.length; i++) {
      ServletDescr d = servletDescrs[i];
      String expl = d.getExplanation();
      if (expl != null) {
	navTable.newRow("valign=top");
	navTable.newCell();
	navTable.add("<font size=+1>");
	navTable.add(srvLink(d, d.heading));
	navTable.add("</font>");
	navTable.newCell();
	navTable.add(expl);
      }
    }
    return navTable;
  }
}
