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

import javax.servlet.http.*;
import javax.servlet.*;
import java.io.*;
import java.util.*;
import java.net.*;
import java.text.*;
//  import com.mortbay.servlet.*;
//  import org.mortbay.util.*;
import org.mortbay.html.*;
import org.mortbay.tools.*;
import org.mortbay.servlet.MultiPartRequest;
import org.lockss.app.*;
import org.lockss.util.*;
import org.lockss.daemon.*;

/** Abstract base class for LOCKSS servlets
 */
// SingleThreadModel causes servlet instances to be assigned to only a
// single thread (request) at a time.
public abstract class LockssServlet extends HttpServlet
  implements SingleThreadModel {

  // Constants
  static final String PARAM_LOCAL_IP = Configuration.PREFIX + "localIPAddress";
//   static final String PARAM_IS_CLUSTER_ADMIN =
//     Configuration.PREFIX + "clusterAdmin";
  static final String PARAM_CONTACT_ADDR =
    Configuration.PREFIX + "admin.contactEmail";
  static final String DEFAULT_CONTACT_ADDR = "contactnotset@notset";

  static final String PARAM_PLATFORM_VERSION =
    Configuration.PREFIX + "platform.version";
  static final String PARAM_ADMIN_ADDRESS =
    Configuration.PREFIX + "admin.IPAddress";

  public static final String JAVASCRIPT_RESOURCE =
    "org/lockss/htdocs/admin.js";

  /** Format to display date/time in headers */
  public static final DateFormat headerDf =
    new SimpleDateFormat("HH:mm:ss MM/dd/yy");

  static final String BACKGROUND_COLOR = "#FFFFFF";
  static final Image IMAGE_LOGO_LARGE = image("lockss-logo-large.gif",
					      160, 160, 0);
  static final Image IMAGE_LOGO_SMALL = image("lockss-logo-small.gif",
					      80, 81, 0);
  static final Image IMAGE_TM = image("tm.gif", 16, 16, 0);
  static final Image IMAGE_LOCKSS_RED = image("lockss-type-red.gif",
					      595, 31, 0);

  protected static final String footAccessDenied =
    "Clicking on this link will result in an access denied error, unless your browser is configured to proxy through a LOCKSS cache, or your workstation is allowed access by the publisher.";

  protected static Logger log = Logger.getLogger("LockssServlet");

  protected ServletContext context;

  private LockssDaemon theDaemon = null;

  // Request-local storage.  Convenient, but requires servlet instances
  // to be single threaded, and must ensure reset them to avoid carrying
  // over state between requests.
  protected HttpServletRequest req;
  protected HttpServletResponse resp;
  protected URL reqURL;
  private String adminDir = null;
  protected String client;	// client param
  protected String clientAddr;	// client addr, even if no param
  protected String adminAddr;
  protected String adminHost;
  protected String localAddr;

  private Vector footnotes;
  private int footNumber;
  private int tabindex;
  ServletDescr _myServletDescr = null;
  private String myName = null;


  /** Marker for servlets whose class can't be found */
  static class UnavailableServletMarker {
  }
  static Class UNAVAILABLE_SERVLET_MARKER = UnavailableServletMarker.class;


  // Servlet descriptor.
  static class ServletDescr {
    public Class cls;
    public String heading;	// display name
    public String name;		// url path component to invoke servlet
    public int flags = 0;
    // flags
    public static int ON_CLIENT = 1;	// runs on client (else on admin)
    public static int PER_CLIENT = 2;	// per client (takes client arg)
    public static int NOT_IN_NAV = 4;	// no link in nav table
    public static int LARGE_LOGO = 8;	// use large LOCKSS logo
    public static int DEBUG_ONLY = 0x10; // debug user only
    public static int STATUS = ON_CLIENT | PER_CLIENT; // shorthand

    public ServletDescr(Class cls, String heading, String name, int flags) {
      this.cls = cls;
      this.heading = heading;
      this.name = name;
      this.flags = flags;
    }
    public ServletDescr(Class cls, String heading, int flags) {
      this(cls, heading,
	   cls.getName().substring(cls.getName().lastIndexOf('.') + 1),
	   flags);
    }

    public ServletDescr(Class cls, String heading) {
      this(cls, heading, 0);
    }

    public ServletDescr(String className, String heading, String name) {
      this(classForName(className), heading, name, 0);
    }

    public ServletDescr(String className, String heading, int flags) {
      this(classForName(className), heading, flags);
    }

    static Class classForName(String className) {
      try {
	return Class.forName(className);
      } catch (ClassNotFoundException e) {
	return UNAVAILABLE_SERVLET_MARKER;
      }	
    }
    boolean isPerClient() {
      return (flags & PER_CLIENT) != 0;
    }
    boolean runsOnClient() {
      return (flags & ON_CLIENT) != 0;
    }
    boolean isInNavTable() {
      return (flags & NOT_IN_NAV) == 0;
    }
    boolean isDebugOnly() {
      return (flags & DEBUG_ONLY) != 0;
    }
    boolean isLargeLogo() {
      return (flags & LARGE_LOGO) != 0;
    }
  }

  // Descriptors for all servlets.
  protected static ServletDescr SERVLET_AU_CONFIG =
    new ServletDescr(AuConfig.class, "Journal Configuration");
  protected static ServletDescr SERVLET_DAEMON_STATUS =
    new ServletDescr(DaemonStatus.class, "Daemon Status");
  protected static ServletDescr SERVLET_PROXY_INFO =
    new ServletDescr(ProxyConfig.class, "Proxy Info", "info/ProxyInfo", 0);
  protected static ServletDescr SERVLET_THREAD_DUMP =
    new ServletDescr("org.lockss.servlet.ThreadDump", "Thread Dump",
		     ServletDescr.DEBUG_ONLY);
  protected static ServletDescr LINK_LOGS =
    new ServletDescr(null, "Logs", "log", ServletDescr.DEBUG_ONLY);
  protected static ServletDescr SERVLET_ADMIN_ACCESS_CONTROL =
    new ServletDescr(AdminIpAccess.class, "Admin Access Control");
  protected static ServletDescr SERVLET_PROXY_ACCESS_CONTROL =
    new ServletDescr(ProxyIpAccess.class, "Proxy Access Control");
  protected static ServletDescr SERVLET_HASH_CUS =
    new ServletDescr(HashCUS.class, "Hash CUS");


  // All servlets must be listed here (even if not in van table).
  // Order of descrs determines order in nav table.
  static ServletDescr servletDescrs[] = {
     SERVLET_AU_CONFIG,
     SERVLET_ADMIN_ACCESS_CONTROL,
     SERVLET_PROXY_ACCESS_CONTROL,
     SERVLET_PROXY_INFO,
     SERVLET_DAEMON_STATUS,
     SERVLET_HASH_CUS,
     LINK_LOGS,
     SERVLET_THREAD_DUMP,
//      SERVLET_ADMIN_HOME,
//      SERVLET_JOURNAL_STATUS,
//      SERVLET_JOURNAL_SETUP,
//      SERVLET_DAEMON_STATUS,
//      SERVLET_ACCESS_CONTROL,
  };

  // Create mapping from servlet class to ServletDescr
  private static final Hashtable servletToDescr = new Hashtable();
  static {
    for (int i = 0; i < servletDescrs.length; i++) {
      ServletDescr d = servletDescrs[i];
      if (d.cls != null && d.cls != UNAVAILABLE_SERVLET_MARKER) {
	servletToDescr.put(d.cls, d);
      }
    }
  };

  private ServletDescr findServletDescr(Object o) {
    ServletDescr d = (ServletDescr)servletToDescr.get(o.getClass());
    if (d != null) return d;
    // if not in map, o might be an instance of a subclass of a servlet class
    // that's in the map.
    for (int i = 0; i < servletDescrs.length; i++) {
      d = servletDescrs[i];
      if (d.cls != null && d.cls.isInstance(o)) {
	// found a descr that describes a superclass.  Add actual class to map
	servletToDescr.put(o.getClass(), d);
	return d;
      }
    }
    return null;		// shouldn't happen
				// XXX do something better here
  }


  /** Run once when servlet loaded. */
  public void init(ServletConfig config) throws ServletException {
    super.init(config);
    context = config.getServletContext();
    theDaemon = (LockssDaemon)context.getAttribute("LockssDaemon");
  }

  /** Servlets must implement this method. */
  protected abstract void lockssHandleRequest() throws ServletException, IOException;

  /** Common request handling. */
  public void service(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    try {
      this.req = req;
      this.resp = resp;
      if (log.isDebug()) {
	logParams();
      }
      resp.setContentType("text/html");
      footNumber = 0;
      tabindex = 1;
      reqURL = new URL(UrlUtil.getRequestURL(req));
      adminAddr = req.getParameter("admin");
      if (adminAddr == null) {
	adminAddr = Configuration.getParam(PARAM_ADMIN_ADDRESS);
      }
      adminHost = reqURL.getHost();
      client = req.getParameter("client");
      clientAddr = client;
      if (clientAddr == null) {
	clientAddr = getLocalIPAddr();
      }
      lockssHandleRequest();
    }
    finally {
      // Don't hold on to stuff forever
      req = null;
      resp = null;
      reqURL = null;
      adminDir = null;
      localAddr = null;
      footnotes = null;
      _myServletDescr = null;
      myName = null;
    }
  }


  // Return descriptor of running servlet
  protected ServletDescr myServletDescr() {
    if (_myServletDescr == null) {
      _myServletDescr = findServletDescr(this);
    }
    return _myServletDescr;
  }

  // By default, servlet heading is in descr.  Override method to
  // compute other heading
  protected String getHeading(ServletDescr d) {
    if (d == null) return "Unknown Servlet";
    return d.heading;
  }

  protected String getHeading() {
    return getHeading(myServletDescr());
  }

  String getLocalIPAddr() {
    if (localAddr == null) {
      try {
	IPAddr localHost = IPAddr.getLocalHost();
	localAddr = localHost.getHostAddress();
      } catch (UnknownHostException e) {
	// shouldn't happen
	log.error("LockssServlet: getLocalHost: " + e.toString());
	return "???";
      }
    }
    return localAddr;
  }

  // Return IP addr used by LCAP.  If specified by (misleadingly named)
  // localIPAddress prop, might not really be our address (if we are
  // behind NAT).
  String getLcapIPAddr() {
    String ip = Configuration.getParam(PARAM_LOCAL_IP);
    if (ip.length() <= 0)  {
      return getLocalIPAddr();
    }
    return ip;
  }

  String getMachineName() {
    if (myName == null) {
      // Return the canonical name of the interface the request was aimed
      // at.  (localIPAddress prop isn't necessarily right here, as it
      // might be the address of a NAT that we're behind.)
      String host = reqURL.getHost();
      try {
	IPAddr localHost = IPAddr.getByName(host);
	String ip = localHost.getHostAddress();
	myName = getMachineName(ip);
      } catch (UnknownHostException e) {
	// shouldn't happen
	log.error("getMachineName", e);
	return host;
      }
    }
    return myName;
  }

  String getMachineName(String ip) {
    try {
      IPAddr inet = IPAddr.getByName(ip);
      return inet.getHostName();
    } catch (UnknownHostException e) {
      log.warning("getMachineName", e);
    }
    return ip;
  }

  // return IP given name or IP
  String getMachineIP(String name) {
    try {
      IPAddr inet = IPAddr.getByName(name);
      return inet.getHostAddress();
    } catch (UnknownHostException e) {
      return null;
    }
  }

  // Servlet predicates
  boolean isPerClient() {
    return myServletDescr().isPerClient();
  }

  boolean runsOnClient() {
    return myServletDescr().runsOnClient();
  }


  boolean isServletLinkInNav(ServletDescr d) {
    return !isThisServlet(d) || linkMeInNav();
  }

  boolean isThisServlet(ServletDescr d) {
    return d == myServletDescr();
  }

  /** servlets may override this to determine whether they should be
   * a link in nav table */
  protected boolean linkMeInNav() {
    return false;
  }

  boolean isLargeLogo() {
    return myServletDescr().isLargeLogo();
  }

  // machine predicates
  boolean isClusterAdmin() {
    return false;
//     return Configuration.getBooleanParam(PARAM_IS_CLUSTER_ADMIN, false);
  }

  // user predicates
  protected boolean isDebugUser() {
    return req.isUserInRole("debugRole");
  }

  // Called when a servlet doesn't get the parameters it expects/needs
  protected void paramError() throws IOException {
    PrintWriter wrtr = resp.getWriter();
    Page page = new Page();
    // add referer, params, msg to contact lockss unless from old bookmark
    // or manually entered url
    page.add("Parameter error");
    page.write(wrtr);
  }

  // return true iff error
  protected boolean checkParam(boolean ok, String msg) throws IOException {
    if (ok) return false;
    log.error(myServletDescr().name + ": " + msg);
    paramError();
    return true;
  }

  /** Construct servlet URL, with params as necessary.  Avoid generating a
   *  hostname different from that used in the original request, or
   *  browsers will prompt again for login
   */
  String srvURL(ServletDescr d, String params) {
    return srvURL(null, d, params);
  }

  /** Construct servlet absolute URL, with params as necessary.
   */
  String srvAbsURL(ServletDescr d, String params) {
    return srvURL(getMachineName(), d, params);
  }

  /** Construct servlet URL, with params as necessary.  Avoid generating a
   *  hostname different from that used in the original request, or
   *  browsers will prompt again for login
   */
  String srvURL(String host, ServletDescr d, String params) {
    StringBuffer sb = new StringBuffer();
    StringBuffer paramsb = new StringBuffer();

    if (!clientAddr.equals(adminAddr)) {
      if (!d.runsOnClient()) {
	if (runsOnClient()) {	// invoking admin servlet from client
	  host = adminAddr;
	}
      } else if (!runsOnClient()) { // invoking client servlet from admin
	host = clientAddr;
	paramsb.append("&admin=");
	paramsb.append(adminHost);
      }
    }
    if (params != null) {
      paramsb.append('&');
      paramsb.append(params);
    }
    if (d.isPerClient()) {
      paramsb.append("&client=");
      paramsb.append(clientAddr);
    }
    if (host != null) {
      sb.append(reqURL.getProtocol());
      sb.append("://");
      sb.append(host);
      sb.append(':');
      sb.append(reqURL.getPort());
    }
    sb.append('/');
    sb.append(d.name);
    if (paramsb.length() != 0) {
      paramsb.setCharAt(0, '?');
      sb.append(paramsb.toString());
    }
    return sb.toString();
  }

  /** Return a link to a servlet */
  String srvLink(ServletDescr d, String text) {
    return srvLink(d, text, null);
  }

  /** Return a link to a servlet with params */
  String srvLink(ServletDescr d, String text, String params) {
    return new Link(srvURL(d, params),
		    (text != null ? text : d.heading)).toString();
  }

  /** Return an absolute link to a servlet with params */
  String srvAbsLink(ServletDescr d, String text, String params) {
    return new Link(srvAbsURL(d, params),
		    (text != null ? text : d.heading)).toString();
  }

  /** Return text as a link iff isLink */
  String conditionalSrvLink(ServletDescr d, String text, String params,
			    boolean isLink) {
    if (isLink) {
      return srvLink(d, text, params);
    } else {
      return text;
    }
  }

  /** Return text as a link iff isLink */
  String conditionalSrvLink(ServletDescr d, String text, boolean isLink) {
    return conditionalSrvLink(d, text, null, isLink);
  }

  /** Concatenate params for URL string */
  static String concatParams(String p1, String p2) {
    if (p1 == null || p1.equals("")) {
      return p2;
    }
    if (p2 == null || p2.equals("")) {
      return p1;
    }
    return p1 + "&" + p2;
  }

  /** Concatenate params for URL string */
  String concatParams(Properties props) {
    java.util.List list = new ArrayList();
    StringBuffer sb = new StringBuffer();
    for (Iterator iter = props.keySet().iterator(); iter.hasNext(); ) {
      String key = (String)iter.next();
      String val = props.getProperty(key);
      list.add(key + "=" + urlEncode(val));
    }
    return StringUtil.separatedString(list, "&");
  }

  String modifyParams(String key, String val) {
    Properties props = getParamsAsProps();
    props.setProperty(key, val);
    return concatParams(props);
  }

  Properties getParamsAsProps() {
    Properties props = new Properties();
    for (Enumeration en = req.getParameterNames(); en.hasMoreElements(); ) {
      String name = (String)en.nextElement();
      props.setProperty(name, req.getParameter(name));
    }
    return props;
  }

  protected String urlEncode(String param) {
    return URLEncoder.encode(param);
  }


  protected boolean isServletInNav(ServletDescr d) {
    if (!isDebugUser() && d.isDebugOnly()) return false;
    if (d.cls == UNAVAILABLE_SERVLET_MARKER) return false;
    return d.isInNavTable() && (!d.isPerClient() || isPerClient());
  }

  // Build servlet navigation table
  private Table getNavTable() {
    Table navTable = new Table(0, " CELLSPACING=2 CELLPADDING=0 ");
    boolean clientTitle = false;

    for (int i = 0; i < servletDescrs.length; i++) {
      ServletDescr d = servletDescrs[i];
      if (isServletInNav(d)) {
	navTable.newRow();
	navTable.newCell();
	if (d.isPerClient()) {
	  if (!clientTitle) {
	    // Insert client name before first per-client servlet
	    navTable.cell().attribute("WIDTH=\"15\"");
	    navTable.newCell();
	    navTable.cell().attribute("COLSPAN=\"2\"");
	    navTable.add("<b>" + getMachineName(clientAddr) + "</b>");
	    navTable.newRow();
	    navTable.newCell();
	    clientTitle = true;
	  }
	  navTable.cell().attribute("WIDTH=\"15\"");
	  navTable.newCell();
	  navTable.cell().attribute("WIDTH=\"15\"");
	  navTable.newCell();
	} else {
	  navTable.cell().attribute("COLSPAN=\"3\"");
	}
	if (false /*isThisServlet(d)*/) {
	  navTable.add("<font size=-1 color=green>");
	} else {
	  navTable.add("<font size=-1>");
	}
	navTable.add(conditionalSrvLink(d, d.heading, isServletLinkInNav(d)));
	navTable.add("</font>");
      }
    }
    navTable.newRow();
    navTable.newCell();
    navTable.cell().attribute("COLSPAN=\"3\"");
    String contactAddr =
      Configuration.getParam(PARAM_CONTACT_ADDR, DEFAULT_CONTACT_ADDR);
    navTable.add("<font size=-1>");
    navTable.add(new Link("mailto:" + contactAddr, "Contact Us"));
    navTable.add("</font>");
    return navTable;
  }

  protected Table getExplanationBlock(String text) {
    Table exp = new Table(0, "width=\"85%\"");
    exp.center();
    exp.newCell("align=center");
    exp.add(text);
    return exp;
  }

  protected String getRequestKey() {
    String key = req.getPathInfo();
    if (key != null && key.startsWith("/")) {
      return key.substring(1);
    }
    return key;
  }

  /** Common page setup. */
  protected Page newPage() {
    Page page = new Page();
    String heading = getHeading();

    page.add("<!doctype html public \"-//w3c//dtd html 4.0 transitional//en\">");
    page.addHeader("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=iso-8859-1\">");
    page.addHeader("<meta http-equiv=\"content-type\" content=\"text/html;charset=ISO-8859-1\">");

    page.addHeader("<style type=\"text/css\"><!-- sup {font-weight: normal} --> </STYLE>");

    if (heading != null)
      page.title("LOCKSS: " + heading);
    else
      page.title("LOCKSS");

    page.attribute("BGCOLOR", BACKGROUND_COLOR);

    page.add(getHeader());
    return page;
  }

  // Common page header
  private Composite getHeader() {
    String heading = getHeading();
    if (heading == null) {
      heading = "Cache Administration";
    }

    Composite comp = new Composite();
    Table table = new Table(0, " cellspacing=2 cellpadding=0 width=\"100%\"");
    comp.add(table);
    String machineName = getMachineName();

    table.newRow();
    Image logo = isLargeLogo() ? IMAGE_LOGO_LARGE : IMAGE_LOGO_SMALL;
    table.newCell("valign=top align=center width=\"20%\"");
//     table.newCell("valign=top width=\"25%\"");
//     table.newCell("valign=top align=center width=" +
// 		  (logo.width() + IMAGE_TM.width() + 20));

    table.add(new Link(Constants.LOCKSS_HOME_URL, logo));
    table.add(IMAGE_TM);
//     table.newCell("valign=center align=center width=\"60%\"");
    table.newCell("valign=top align=center width=\"60%\"");
    table.add("<br>");
    table.add("<font size=+2><b>");
    table.add(heading);
    table.add("</b></font>");

    table.add("<br>");
    Date startDate = getLockssDaemon().getStartDate();
    String since =
      StringUtil.timeIntervalToString(TimeBase.msSince(startDate.getTime()));
    table.add(getMachineName() + " at " +
	      headerDf.format(new Date()) + ", up " + since);

//     table.newCell("valign=center align=right width=\"25%\"");
    table.newCell("valign=center align=center width=\"20%\"");
    table.add(getNavTable());

//     comp.add("<br><center><font size=+2><b>Cache Administration</b></font></center>");
//     comp.add("<center><b>" + machineName + "</b></center>");
//     comp.add("<br><center><font size=+1><b>"+heading+"</b></font></center>");

//     Date startDate = getLockssDaemon().getStartDate();
//     String since =
//       StringUtil.timeIntervalToString(TimeBase.msSince(startDate.getTime()));
//     comp.add("<center>" + getMachineName() + " at " +
// 	     headerDf.format(new Date()) + ", up " + since + "</center>");
//     //       comp.add("<center>Running since " + headerDf.format(startDate) + "</center>");
    comp.add("<br>");
    return comp;
  }

  // Common page footer
  public Element getFooter() {
    Composite comp = new Composite();
    String vDaemon = theDaemon.getVersionInfo();

    addNotes(comp);
    comp.add("<p>");

    Table table = new Table(0, " CELLSPACING=0 CELLPADDING=0 ALIGN=CENTER");
    table.newRow("VALIGN=TOP");
    table.newCell();
    table.add(IMAGE_LOCKSS_RED);
    table.newCell();
    table.add(IMAGE_TM);
    table.newRow();
    table.newCell("COLSPAN=2");
    table.add("<center><font size=-1>" + vDaemon + "</font></center>");
    comp.add(table);

//     comp.add("<center><font size=-1>" + vDaemon + "</font></center>");
    return comp;
  }

  // eventually this should calculate w & h
  static Image image(String file, int w, int h, int border) {
    return new Image("/images/" + file, w, h, border);
  }

  /** Add html tags to grey the text if isGrey is true */
  protected String greyText(String txt, boolean isGrey) {
    if (!isGrey) {
      return txt;
    }
    return "<font color=gray>" + txt + "</font>";
  }

  /** Set this element next in the tab order.  Returns the element for
   * easier nesting in expressions. */
  protected Element setTabOrder(Element ele) {
    ele.attribute("tabindex", tabindex++);
    return ele;
  }

  /** Store a footnote, assign it a number, return html for footnote
   * reference.  If footnote in null or empty, no footnote is added and am
   * empty string is returned.  Footnote numbers get turned into links;
   * <b>Do not put the result of addFootnote inside a link!</b>.  */
  protected String addFootnote(String s) {
    if (s == null || s.length() == 0) {
      return "";
    }
    if (footNumber == 0) {
      if (footnotes == null) {
	footnotes = new Vector(10, 10);
      } else {
	footnotes.removeAllElements();
      }
    }
    int n = footnotes.indexOf(s);
    if (n < 0) {
      n = footNumber++;
      footnotes.addElement(s);
    }
    return "<sup><font size=-1><a href=#foottag" + (n+1) + ">" +
      (n+1) + "</a></font></sup>";
  }

  /** Add accumulated footnotes to Composite. */
  protected void addNotes(Composite elem) {
    if (footnotes == null || footNumber == 0) {
      return;
    }
    elem.add("<p><b>Notes:</b>");
    elem.add("<ol><font size=-1>");
    for (int n = 0; n < footNumber; n++) {
      elem.add("<li value=" + (n+1) + "><a name=foottag" + (n+1) + ">" + 
	       footnotes.elementAt(n) + "</a>");
    }
    footnotes.removeAllElements();
    elem.add("</font></ol>");
  }

  protected void addJavaScript(Composite comp) {
    Script script = new Script(getJavascript());
    comp.add(script);
  }

  private static String jstext = null;

  private static synchronized String getJavascript() {
    if (jstext == null) {
      try {
	ClassLoader loader = Thread.currentThread().getContextClassLoader();
	InputStream istr = loader.getResourceAsStream(JAVASCRIPT_RESOURCE);
	jstext = StringUtil.fromInputStream(istr);
	istr.close();
      } catch (Exception e) {
	log.error("Can't load javascript", e);
      }
    }
    return jstext;
  }

  public MultiPartRequest getMultiPartRequest(int maxLen)
      throws FormDataTooLongException, IOException {
    if (req.getContentType() == null ||
	!req.getContentType().startsWith("multipart/form-data")) {
      return null;
    }
    if (req.getContentLength() > maxLen) {
      throw new FormDataTooLongException(req.getContentLength() + " bytes, " +
					 maxLen + " allowed");
    }
    MultiPartRequest multi = new MultiPartRequest(req);
    if (log.isDebug2()) {
      String[] parts = multi.getPartNames();
      log.debug3("Multipart request, " + parts.length + " parts");
      if (log.isDebug3()) {
	for (int p = 0; p < parts.length; p++) {
	  String name = parts[p];
	  String cont = multi.getString(parts[p]);
	  log.debug3(name + ": " + cont);
	}
      }
    }
    return multi;
  }

  /** Return the daemon instance. */
  protected LockssDaemon getLockssDaemon() {
    return theDaemon;
  }

  protected void logParams() {
    Enumeration en = req.getParameterNames();
    while (en.hasMoreElements()) {
      String name = (String)en.nextElement();
      log.debug(name + " = " + req.getParameter(name));
    }
  }

  /** Convenience method */
  protected String encodeText(String s) {
    return HtmlUtil.encode(s, HtmlUtil.ENCODE_TEXT);
  }

  /** Convenience method */
  protected String encodeTextArea(String s) {
    return HtmlUtil.encode(s, HtmlUtil.ENCODE_TEXTAREA);
  }

  /** Convenience method */
  protected String encodeAttr(String s) {
    return HtmlUtil.encode(s, HtmlUtil.ENCODE_ATTR);
  }

  /** Exception thrown if multipart form data is longer than the
   * caller-supplied max */
  public class FormDataTooLongException extends Exception {
    public FormDataTooLongException(String message) {
      super(message);
    }
  }
}
