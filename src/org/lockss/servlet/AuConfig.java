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
import org.mortbay.html.*;
import org.mortbay.tools.*;
import org.lockss.util.*;
import org.lockss.plugin.*;
import org.lockss.daemon.*;
import org.lockss.daemon.status.*;

/** Create and update AU configuration.
 */
public class AuConfig extends LockssServlet {

  static Logger log = Logger.getLogger("AuConfig");

  // must not conflict with existing html element properties in browser
  static final String ACTION_TAG = "lockssAction";
  // prefix added to config prop keys when used in form
  static final String FORM_PREFIX = "lfp.";

  private ConfigManager configMgr;
  private PluginManager pluginMgr;

  // Used to insert messages into the page
  private String errMsg;
  private String statusMsg;

  String action;			// action request by form
  Plugin plugin;			// current plugin
  Configuration auConfig;		// current config from AU
  Configuration formConfig;		// config read from form
  java.util.List auConfigParams;
  Collection defKeys;			// plugin's definitional keys
  java.util.List editKeys;		// non-definitional keys

  // don't hold onto objects after request finished
  private void resetLocals() {
    plugin = null;
    auConfig = null;
    formConfig = null;
    auConfigParams = null;
    defKeys = null;
    editKeys = null;
  }

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
    configMgr = getLockssDaemon().getConfigManager();
    pluginMgr = getLockssDaemon().getPluginManager();
  }

  protected void lockssHandleRequest() throws IOException {
    action = req.getParameter(ACTION_TAG);

    errMsg = null;
    statusMsg = null;
    formConfig = null;

    op: {
      if (StringUtil.isNullString(action)) {
	displayAuSummary();
	break op;
      }
      if (action.equals("Add")) {
	displayAddAu();
	break op;
      }
      if (action.equals("EditNew")) {
	displayEditNew();
	break op;
      }
      if (action.equals("Create")) {
	createAu();
	break op;
      }
      // all other actions require AU.  If missing, display summary page
      String auid = req.getParameter("auid");
      ArchivalUnit au = pluginMgr.getAuFromId(auid);
      if (au == null) {
	errMsg = "Invalid AuId: " + auid;
	displayAuSummary();
	break op;
      }
      if (action.equals("Edit")){
	displayEditAu(au);
	break op;
      }
      if (action.equals("Restore")) {
	displayRestoreAu(au);
	break op;
      }
      if (action.equals("DoRestore")) {
	updateAu(au);
	break op;
      }
      if (action.equals("Update")) {
	updateAu(au);
	break op;
      }
      if (action.equals("Unconfigure")) {
	confirmUnconfigureAu(au);
	break op;
      }
      if (action.equals("Confirm Unconfigure")) {
	doUnconfigureAu(au);
	break op;
      }
      errMsg = "Unknown action: " + action;
      displayAuSummary();
    }
    resetLocals();
  }

  /** Display "Add Volume" button and list of configured AUs with Edit
   * buttons */
  private void displayAuSummary() throws IOException {
    Page page = newPage();
    addJavaScript(page);
    page.add(getErrBlock());
    page.add(getExplanationBlock("Add a new volume, or edit an existing one."));
    Form frm = new Form(srvURL(myServletDescr(), null));
    frm.method("POST");
    frm.add("<input type=hidden name=\"" + ACTION_TAG + "\">");
    addAuid(frm, null);
    Table tbl = new Table(0, "align=center cellspacing=4 cellpadding=0");
    addAddAuRow(tbl);
    Collection allAUs = pluginMgr.getAllAUs();
    if (!allAUs.isEmpty()) {
      for (Iterator iter = allAUs.iterator(); iter.hasNext(); ) {
	addAuSummaryRow(tbl, (ArchivalUnit)iter.next());
      }
    }
    frm.add(tbl);
    page.add(frm);
    endPage(page);
  }

  /** Add the "Add" row to the table */
  private void addAddAuRow(Table tbl) {
    tbl.newRow();
    tbl.newCell("align=right valign=center");
    tbl.add(submitButton("Add", "Add"));
    tbl.newCell("valign=center");
    tbl.add("Add new Volume");
  }

  /** Add an Edit row to the table */
  private void addAuSummaryRow(Table tbl, ArchivalUnit au) {
    Configuration cfg = pluginMgr.getStoredAuConfiguration(au);
    boolean deleted = cfg.isEmpty();
    tbl.newRow();
    tbl.newCell("align=right valign=center");
    String act = deleted ? "Restore": "Edit";
    tbl.add(submitButton(act, act, "auid", au.getAUId()));
    tbl.newCell("valign=center");
    tbl.add(greyText(au.getName(), deleted));
  }

  /** Display form to edit existing AU */
  private void displayEditAu(ArchivalUnit au) throws IOException {
    Page page = newPage();
    fetchAuConfig(au);

    page.add(getErrBlock());
    page.add(getExplanationBlock("Editing Configuration of: " + au.getName()));

    java.util.List actions = ListUtil.list("Unconfigure");
    if (!getEditKeys().isEmpty()) {
      actions.add(0, "Update");
    }
    Form frm = createAUEditForm(actions, au, true);
    page.add(frm);
    endPage(page);
  }

  /** Display form to restore unconfigured AU */
  private void displayRestoreAu(ArchivalUnit au) throws IOException {
    Page page = newPage();
    fetchAuConfig(au);

    page.add(getErrBlock());
    page.add(getExplanationBlock("Restoring Configuration of: " +
				 au.getName()));

    java.util.List actions =
      ListUtil.list(new Input(Input.Hidden, ACTION_TAG, "DoRestore"),
		    new Input(Input.Submit, "button", "Restore"));
    Form frm = createAUEditForm(actions, au, true);
    page.add(frm);
    endPage(page);
  }

  /** Display form to add a new AU */
  private void displayEditNew() throws IOException {
    String title = req.getParameter("Title");
    if (!StringUtil.isNullString(title)) {
      Collection c = pluginMgr.getTitlePlugins(title);
      if (c == null || c.isEmpty()) {
	errMsg = "Unknown title: " + title;
	displayAddAu();
	return;
      }
      // tk - need to deal with > 1 plugin for title
      plugin = (Plugin)c.iterator().next();
      formConfig = plugin.getConfigForTitle(title);
    } else {
      String pid = req.getParameter("PluginId");
      String pKey;
      if (!StringUtil.isNullString(pid)) {
	pKey = pluginMgr.pluginKeyFromId(pid);
      } else {
	pid = req.getParameter("PluginClass");
	pKey = pluginMgr.pluginKeyFromName(pid);
      }
      if (!pluginMgr.ensurePluginLoaded(pKey)) {
	if (StringUtil.isNullString(pKey)) {
	  errMsg = "You must specify a plugin.";
	} else {
	  errMsg = "Can't find plugin: " + pid;
	}
	displayAddAu();
	return;
      }
      plugin = pluginMgr.getPlugin(pKey);
    }
    
    Page page = newPage();
    page.add(getErrBlock());
    page.add(getExplanationBlock((StringUtil.isNullString(title)
				  ? "Creating new journal volume"
				  : ("Creating new volume of " + title)) +
				 " with plugin: " + plugin.getPluginName()));

    Form frm = createAUEditForm(ListUtil.list("Create"), null, true);
    page.add(frm);
    endPage(page);
  }

  /** Display form to select from list of title, or list of plugins, or
   * enter a plugin name */
  private void displayAddAu() throws IOException {
    Page page = newPage();
    page.add(getErrBlock());
    page.add(getExplanationBlock("Add New Journal Volume"));

    Form frm = new Form(srvURL(myServletDescr(), null));
    frm.method("POST");
    frm.add("<center>");

    Collection titles = pluginMgr.findAllTitles();
    if (!titles.isEmpty()) {
      frm.add("<br>Choose a title:<br>");
      Select sel = new Select("Title", false);
      sel.add("-no selection-", true, "");
      for (Iterator iter = titles.iterator(); iter.hasNext(); ) {
	String title = (String)iter.next();
	sel.add(title, false);
      }
      frm.add(sel);
      frm.add("<br>or");
    }

    SortedMap pMap = new TreeMap();
    for (Iterator iter = pluginMgr.getRegisteredPlugins().iterator();
	 iter.hasNext(); ) {
      Plugin p = (Plugin)iter.next();
      pMap.put(p.getPluginName(), p);
    }

    if (!pMap.isEmpty()) {
      frm.add("<br>Choose a plugin:<br>");
      Select sel = new Select("PluginId", false);
      sel.add("-no selection-", true, "");
      for (Iterator iter = pMap.keySet().iterator(); iter.hasNext(); ) {
	String pName = (String)iter.next();
	Plugin p = (Plugin)pMap.get(pName);
	sel.add(pName, false, p.getPluginId());
      }
      frm.add(sel);
      frm.add("<br>or");
    }
    frm.add("<br>Enter the class name of a plugin:<br>");
    Input in = new Input(Input.Text, "PluginClass");
    in.setSize(40);
    frm.add(in);
    frm.add("<br><br>");
    frm.add(new Input(Input.Hidden, ACTION_TAG, "EditNew"));
    frm.add(new Input(Input.Submit, "button", "Configure Volume"));
    page.add(frm);
    page.add("</center><br>");

    endPage(page);
  }

  /** Create a form to edit a (possibly not-yet-existing) AU.
   * @param actions list of action buttons for bottom of form.  If an
   * element is a String, a button will be created for it.
   * @param au the AU
   * @param editable true if the form should be editable.  (Not all the
   * fields will be editable in any case).
   */
  private Form createAUEditForm(java.util.List actions, ArchivalUnit au,
				boolean editable)
      throws IOException {
    boolean isNew = au == null;

    Configuration initVals = formConfig != null ? formConfig : auConfig;
    if (initVals == null) {
      initVals = ConfigManager.EMPTY_CONFIGURATION;
    }

    Form frm = new Form(srvURL(myServletDescr(), null));
    frm.method("POST");

    Table tbl = new Table(0, "align=center cellspacing=4 cellpadding=0");
    tbl.newRow();
    tbl.newCell("colspan=2 align=center");
    tbl.add("Volume Definition");
//     tbl.add(isNew ? "Defining Properties" : "Fixed Properties");
    addPropRows(tbl, getDefKeys(), initVals,
		(isNew
		 ? (org.apache.commons.collections.CollectionUtils.
		    subtract(getDefKeys(), initVals.keySet()))
		 : null));

    tbl.newRow();
    tbl.newRow();
    tbl.newCell("colspan=2 align=center");
    Collection eKeys = getEditKeys();
    if (eKeys.isEmpty()) {
      if (!isNew && editable) {
// 	tbl.add("No Editable Properties");
      }
    } else {
      tbl.add("Other Parameters");
      addPropRows(tbl, eKeys, initVals, editable ? eKeys : null);

      tbl.newRow();
      tbl.newCell("colspan=2 align=center");
    }
    if (isNew) {
      addPlugId(tbl, plugin);
    } else {
      addAuid(tbl, au);
    }
    for (Iterator iter = actions.iterator(); iter.hasNext(); ) {
      Object act = iter.next();
      if (act instanceof String) {
	tbl.add(new Input(Input.Submit, ACTION_TAG, (String)act));
	if (iter.hasNext()) {
	  tbl.add("&nbsp;");
	}
      } else {
	tbl.add(act);
      }
    }
    frm.add(tbl);
    return frm;
  }

  /** Add config props rows to edit AU table.
   * @param tbl the table
   * @param keys the kwys to include (subset of those from getAuConfigParams())
   * @param initVals initial values of fields, or null
   * @param editableKeys the keys whose values may be edited
   */
  private void addPropRows(Table tbl, Collection keys, Configuration initVals,
		      Collection editableKeys) {
    for (Iterator iter = getAuConfigParams().iterator(); iter.hasNext(); ) {
      ConfigParamDescr descr = descrFromObj(iter.next());
      if (!keys.contains(descr.getKey())) {
	continue;
      }
      String key = descr.getKey();
      tbl.newRow();
      tbl.newCell();
      tbl.add(descr.getDisplayName());
      tbl.add(addFootnote(descr.getDescription()));
      tbl.newCell();
      String val = initVals != null ? initVals.get(key) : null;
      if (editableKeys != null && editableKeys.contains(key)) {
	Input in = new Input(Input.Text, formKeyFromKey(descr.getKey()), val);
	if (descr.getSize() != 0) {
	  in.setSize(descr.getSize());
	}
	tbl.add(in);
      } else {
	tbl.add(val);
	tbl.add(new Input(Input.Hidden, formKeyFromKey(descr.getKey()), val));
      }
    }
  }

  /** Process the Create button */
  private void createAu() throws IOException {
    String pid = req.getParameter("PluginId");
    String pKey = pluginMgr.pluginKeyFromId(pid);
    if (!pluginMgr.ensurePluginLoaded(pKey)) {
      errMsg = "Can't find plugin: " + pid;
      displayAddAu();
      return;
    }
    plugin = pluginMgr.getPlugin(pKey);
    formConfig = getAuConfigFromForm(true);
    try {
      ArchivalUnit au =
	pluginMgr.createAndSaveAUConfiguration(plugin, formConfig);
      statusMsg = "Volume created.";
      displayEditAu(au);
    } catch (ArchivalUnit.ConfigurationException e) {
      log.error("Error configuring AU", e);
      errMsg = "Error configuring AU:<br>" + e.getMessage();
      displayEditNew();
    } catch (IOException e) {
      log.error("Error saving AU configuration", e);
      errMsg = "Error saving AU configuration:<br>" + e.getMessage();
      displayEditNew();
    }
  }

  /** Process the Update button */
  private void updateAu(ArchivalUnit au) throws IOException {
    fetchAuConfig(au);
    Configuration formConfig = getAuConfigFromForm(false);
    if (isChanged(auConfig, formConfig) ||
	isChanged(pluginMgr.getStoredAuConfiguration(au), formConfig)) {
      try {
	pluginMgr.setAndSaveAUConfiguration(au, formConfig);
	statusMsg = "Volume configuration saved.";
      } catch (ArchivalUnit.ConfigurationException e) {
	log.error("Couldn't reconfigure AU", e);
	errMsg = e.getMessage();
      } catch (IOException e) {
	log.error("Couldn't save AU configuraton", e);
	errMsg = "Error saving AU:<br>" + e.getMessage();
	displayEditAu(au);
      }
    } else {
      statusMsg = "No changes made.";
    }
    displayEditAu(au);
  }
    
  /** Display the Confirm Unconfigure  page */
  private void confirmUnconfigureAu(ArchivalUnit au) throws IOException {
    String permFoot = "Permanent deletion occurs only during a reboot," +
      " when configuration changes are backed up to the configuration floppy.";
    String unconfigureFoot =
      "Unconfigure will not take effect until the next daemon restart." +
      "  At that point the volume will be inactive, but its contents" +
      " will remain in the cache untill the deletion is made permanent." +
      " Permanent deletion occurs only during a reboot, when configuration" +
      " changes are backed up to the configuration floppy. (NIY)";

    Page page = newPage();
    fetchAuConfig(au);

    page.add(getErrBlock());
    page.add(getExplanationBlock("Are you sure you want to unconfigure" +
				 addFootnote(unconfigureFoot) + ": " +
				 au.getName()));

    Form frm = createAUEditForm(ListUtil.list("Confirm Unconfigure"),
				au, false);
    page.add(frm);
    endPage(page);
  }

  /** Process the Confirm Unconfigure button */
  private void doUnconfigureAu(ArchivalUnit au) throws IOException {
    try {
      pluginMgr.deleteAUConfiguration(au);
      statusMsg = "Volume configuration removed.";
    } catch (ArchivalUnit.ConfigurationException e) {
      log.error("Can't happen", e);
      errMsg = e.getMessage();
    } catch (IOException e) {
      log.error("Couldn't save AU configuraton", e);
      errMsg = "Error deleting AU:<br>" + e.getMessage();
    }
    displayAuSummary();
  }

  /** Put a value from the config form into the properties, iff it is set
   * in the form */
  private void putFormVal(Properties p, String key) {
    String val = req.getParameter(formKeyFromKey(key));
    // Must treat empty string as unset param.
    if (!StringUtil.isNullString(val)) {
      p.put(key, val);
    }
  }

  /** Return true iff newConfig is different from oldConfig */
  boolean isChanged(Configuration oldConfig, Configuration newConfig) {
    Collection dk = oldConfig.differentKeys(newConfig);
    boolean changed = false;
    for (Iterator iter = dk.iterator(); iter.hasNext(); ) {
      String key = (String)iter.next();
      String oldVal = oldConfig.get(key);
      String newVal = newConfig.get(key);
      if (!isEqualFormVal(oldVal, newVal)) {
	changed = true;
	log.debug("Key " + key + " changed from \"" +
		  oldVal + "\" to \"" + newVal + "\"");
      }
    }
    return changed;
  }

  /** Return the AU Configuration from the values in the form, except for
   * definitional props that already had values in the existing auConfig,
   * which are copied from the existing config.
   */
  Configuration getAuConfigFromForm(boolean isNew) {
    Properties p = new Properties();
    for (Iterator iter = getDefKeys().iterator(); iter.hasNext(); ) {
      String key = (String)iter.next();
      if (isNew) {
	putFormVal(p, key);
      } else {
	if (auConfig.get(key) != null) {
	  p.put(key, auConfig.get(key));
	}
      }
    }
    for (Iterator iter = getEditKeys().iterator(); iter.hasNext(); ) {
      String key = (String)iter.next();
      putFormVal(p, key);
    }
    return ConfigManager.fromProperties(p);
  }

  /** True iff both values are null (or null strings) or equal strings */
  private boolean isEqualFormVal(String formVal, String oldVal) {
    return (StringUtil.isNullString(formVal))
      ? StringUtil.isNullString(oldVal)
      : formVal.equals(oldVal);
    }

  /** Create message and error message block */
  private Composite getErrBlock() {
    Composite comp = new Composite();
    if (errMsg != null) {
      comp.add("<center><font color=red>");
      comp.add(errMsg);
      comp.add("</font></center><br>");
    }
    if (statusMsg != null) {
      comp.add("<center>");
      comp.add(statusMsg);
      comp.add("</center><br>");
    }
    return comp;
  }

  /** Return a button that invokes the javascript submit routine with the
   * specified action */
  protected Composite submitButton(String label, String action) {
    return submitButton(label, action, null, null);
  }

  /** Return a button that invokes the javascript submit routine with the
   * specified action, first storing the value in the specified form
   * prop. */
  protected Composite submitButton(String label, String action,
				   String prop, String value) {
    Composite comp = new Composite();
    comp.add("<input type=button value=\"");
    comp.add(label);
    comp.add("\" onClick=\"lockssButton(this, '");
    comp.add(action);
    comp.add("'");
    if (prop != null && value != null) {
      comp.add(", '");
      comp.add(prop);
      comp.add("', '");
      comp.add(value);
      comp.add("'");
    }
    comp.add(")\">");
    return comp;
  }

  /** Add auid to form in a hidden field */
  private void addAuid(Composite comp, ArchivalUnit au) {
    comp.add(new Input(Input.Hidden, "auid",
		       au != null ? au.getAUId() : ""));
  }

  /** Add plugin id to form in a hidden field */
  private void addPlugId(Composite comp, Plugin plugin) {
    comp.add(new Input(Input.Hidden, "PluginId", plugin.getPluginId()));
  }

  /** Common and page adds Back link, footer */
  protected void endPage(Page page) throws IOException {
    if (action != null) {
      page.add("<center>");
      page.add(srvLink(myServletDescr(), "Back to Journal Configuration"));
      page.add("</center>");
    }      
    page.add(getFooter());
    page.write(resp.getWriter());
  }

  // make me a link in nav table if not on initial journal config page
  protected boolean linkMeInNav() {
    return action != null;
  }

  private void fetchAuConfig(ArchivalUnit au) {
    auConfig = au.getConfiguration();
    log.debug("auConfig: " + auConfig);
    plugin = au.getPlugin();
  }

  void prepareConfigParams() {
    auConfigParams = new ArrayList(plugin.getAUConfigProperties());
    // let the plugin specify the order
    // Collections.sort(auConfigParams);
    defKeys = plugin.getDefiningConfigKeys();
    editKeys = new ArrayList();
    for (Iterator iter = auConfigParams.iterator(); iter.hasNext(); ) {
      ConfigParamDescr descr = descrFromObj(iter.next());
      if (!defKeys.contains(descr.getKey())) {
	editKeys.add(descr.getKey());
      }
    }
  }

  Collection getDefKeys() {
    if (defKeys == null) {
      prepareConfigParams();
    }
    return defKeys;
  }

  java.util.List getEditKeys() {
    if (editKeys == null) {
      prepareConfigParams();
    }
    return editKeys;
  }

  java.util.List getAuConfigParams() {
    if (auConfigParams == null) {
      prepareConfigParams();
    }
    return auConfigParams;
  }

  ConfigParamDescr descrFromObj(Object obj) {
    if (obj instanceof ConfigParamDescr) {
      return (ConfigParamDescr)obj;
    }
    return new ConfigParamDescr(obj.toString());
  }

  private String formKeyFromKey(String key) {
    return FORM_PREFIX + key;
  }

  private String keyFromFormKey(String formKey) {
    return formKey.substring(FORM_PREFIX.length());
  }

}
