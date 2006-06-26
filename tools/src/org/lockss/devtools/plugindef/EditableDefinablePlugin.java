/*
 * $Id$
 */

/*

Copyright (c) 2000-2006 Board of Trustees of Leland Stanford Jr. University,
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
package org.lockss.devtools.plugindef;

import java.util.*;

import org.lockss.daemon.*;
import org.lockss.plugin.*;
import org.lockss.plugin.definable.*;
import org.lockss.util.*;
import org.lockss.util.urlconn.*;

public class EditableDefinablePlugin
  extends DefinablePlugin {

  static final protected String PLUGIN_NAME
    = DefinablePlugin.CM_NAME_KEY;
  static final protected String PLUGIN_VERSION
    = DefinablePlugin.CM_VERSION_KEY;
  static final protected String PLUGIN_PROPS
    = DefinablePlugin.CM_CONFIG_PROPS_KEY;
  static final protected String PLUGIN_NOTES
    = DefinablePlugin.CM_NOTES_KEY;
  static final protected String PLUGIN_EXCEPTION_HANDLER
    = DefinablePlugin.CM_EXCEPTION_HANDLER_KEY;
  static final protected String CM_EXCEPTION_LIST_KEY
    = DefinablePlugin.CM_EXCEPTION_LIST_KEY;
  static final protected String CM_CRAWL_TYPE
    = DefinablePlugin.CM_CRAWL_TYPE;
  static final protected String[] CRAWL_TYPES
    = DefinablePlugin.CRAWL_TYPES;

  static final protected String PLUGIN_IDENTIFIER = "plugin_identifier";

  static final protected String AU_START_URL
    = DefinableArchivalUnit.AU_START_URL_KEY;
  static final protected String AU_NAME
    = DefinableArchivalUnit.AU_NAME_KEY;
  static final protected String AU_RULES
    = DefinableArchivalUnit.AU_RULES_KEY;
  static final protected String AU_CRAWL_WINDOW
  = DefinableArchivalUnit.AU_CRAWL_WINDOW_KEY;
  static final protected String AU_CRAWL_WINDOW_SPEC
  = DefinableArchivalUnit.AU_CRAWL_WINDOW_SPEC_KEY;
  static final protected String AU_EXPECTED_PATH
    = DefinableArchivalUnit.AU_EXPECTED_PATH;
  static final protected String AU_CRAWL_DEPTH
    = DefinableArchivalUnit.AU_CRAWL_DEPTH;
  static final protected String AU_NEWCONTENT_CRAWL
    = DefinableArchivalUnit.AU_DEFAULT_NC_CRAWL_KEY;
  static final protected String AU_PAUSE_TIME
    = DefinableArchivalUnit.AU_DEFAULT_PAUSE_TIME;
  static final protected String AU_MANIFEST
    = DefinableArchivalUnit.AU_MANIFEST_KEY;
  static final protected String AU_PARSER_SUFFIX
    = DefinableArchivalUnit.AU_PARSER_SUFFIX;
  static final public String AU_FILTER_SUFFIX
    = DefinableArchivalUnit.AU_FILTER_SUFFIX;

  static public String[] CONFIG_PARAM_TYPES = ConfigParamDescr.TYPE_STRINGS;

  static public Map DEFAULT_CONFIG_PARAM_DESCRS = getDefaultConfigParamDescrs();
  static Logger log = Logger.getLogger(PluginDefinerApp.LOG_ROOT +
				       ".editableDefinablePlugin");
  protected PersistentPluginState pluginState;

  public EditableDefinablePlugin() {
      pluginState = new PersistentPluginState();
  }

  // for reading map files
  public void loadMap(String location, String name) throws Exception {
    log.info("loading definition map: " + location + "/" + name);
    definitionMap.loadMap(location, name);
    String err = definitionMap.getLoadErr();
    if(err != null) {
      log.error(err);
      throw new Exception(err);
    }
  }

  // for writing map files
  public void writeMap(String location, String name) throws Exception {
    // make sure we don't have any AU Config info in map
    HashMap cmap = getPrintfDescrs();
    for(Iterator it = cmap.keySet().iterator(); it.hasNext();) {
      definitionMap.removeMapElement((String)it.next());
    }
    // store the configuration map
    log.info("storing definition map: " + location + "/" + name);
    definitionMap.storeMap(location, name);
    String err = definitionMap.getLoadErr();
    if(err != null) {
      log.error(err);
      throw new Exception(err);
    }
  }

  public String getMapName() {
    return mapName;
  }

  public void setMapName(String name) {
    if (name.endsWith(MAP_SUFFIX)) {
      mapName = name;
    }
    else {
      mapName = name + MAP_SUFFIX;
    }
  }

  public void setPluginState(int field,String key,String value){
      pluginState.setPluginState(field,key,value);
  }

  public PersistentPluginState getPluginState(){
      return pluginState;
  }

  public void setCrawlType(String crawlType) {
    definitionMap.putString(CM_CRAWL_TYPE, crawlType);
  }

  public String getCrawlType() {
    return definitionMap.getString(CM_CRAWL_TYPE, CRAWL_TYPES[0]);
  }

  public void removeCrawlType() {
    definitionMap.removeMapElement(CM_CRAWL_TYPE);
  }

  public void setAuStartURL(String startUrl) {
    definitionMap.putString(AU_START_URL, startUrl);
  }

  public String getAuStartUrl() {

    return definitionMap.getString(AU_START_URL, null);
  }

  public void removeAuStartURL() {
    definitionMap.removeMapElement(AU_START_URL);
  }

  public void setAuName(String name) {
    definitionMap.putString(AU_NAME, name);
  }

  public String getAuName() {
    return definitionMap.getString(AU_NAME, null);
  }

  public void removeAuName() {
    definitionMap.removeMapElement(AU_NAME);
  }

  public void setAuCrawlRules(Collection rules) {
    definitionMap.putCollection(AU_RULES, rules);
  }

  public Collection getAuCrawlRules() {
    Collection defaultCrawlRule = new ArrayList();
    defaultCrawlRule.add("4,\"^%s\",base_url");
    String startUrl = getAuStartUrl();
    if(startUrl != null) {
      defaultCrawlRule.add("1," + startUrl);
    }
    return definitionMap.getCollection(AU_RULES, defaultCrawlRule);
  }

  public void removeAuCrawlRules() {
    definitionMap.removeMapElement(AU_RULES);
  }

  public void addCrawlRule(String rule) {
    List rules = (List) definitionMap.getCollection(AU_RULES, new ArrayList());
    for (Iterator it = rules.iterator(); it.hasNext(); ) {
      String str = (String) it.next();
      if (str.equals(rule)) {
	return;
      }
    }
    rules.add(rule);
    definitionMap.putCollection(AU_RULES, rules);
  }

  public void removeCrawlRule(String rule) {
    List rules = (List) definitionMap.getCollection(AU_RULES, null);
    if (rules == null)return;

    for (Iterator it = rules.iterator(); it.hasNext(); ) {
      String str = (String) it.next();
      if (str.equals(rule)) {
	it.remove();
      }
    }

  }

  public void setAuCrawlWindow(String crawlWindow) {

    try {
      definitionMap.putString(AU_CRAWL_WINDOW, crawlWindow);
      CrawlWindow win = (CrawlWindow) Class.forName(crawlWindow).newInstance();
    }
    catch (Exception ex) {
      throw new DefinablePlugin.InvalidDefinitionException(
							   "Unable to create crawl window class: " + crawlWindow, ex);
    }
  }

  public String getAuCrawlWindow() {
    return definitionMap.getString(AU_CRAWL_WINDOW, null);
  }

  public void removeAuCrawlWindow() {
    definitionMap.removeMapElement(AU_CRAWL_WINDOW);
  }

  public void setAuCrawlWindowSpec(CrawlWindow crawlWindow){
      try {
	  definitionMap.setMapElement(AU_CRAWL_WINDOW_SPEC, crawlWindow);
      }
      catch (Exception ex) {
	  throw new DefinablePlugin.InvalidDefinitionException(
           "Unable to set crawl window spec: " + crawlWindow, ex);
      }

  }

  public CrawlWindow getAuCrawlWindowSpec() {

      return (CrawlWindow) (definitionMap.getMapElement(AU_CRAWL_WINDOW_SPEC));

  }

  public void removeAuCrawlWindowSpec() {

      definitionMap.removeMapElement(AU_CRAWL_WINDOW_SPEC);

  }

  public void setAuFilter(String mimetype, List rules) {
    if (rules.size() > 0) {
      try {
	FilterRule rule = new DefinableFilterRule(rules);
	definitionMap.putCollection(mimetype + AU_FILTER_SUFFIX, rules);
      }
      catch (Exception ex) {
	throw new DefinablePlugin.InvalidDefinitionException(
							     "Unable to create filter from " + rules + " for mimetype " + mimetype);
      }
    }
    else {
      definitionMap.removeMapElement(mimetype + AU_FILTER_SUFFIX);
    }
  }

  public void setAuFilter(String mimetype, String filter) {

    try {
      if(filter.indexOf(" ") != -1 || filter.indexOf(".") == -1
	 || filter.endsWith(".")) {
	throw new DefinablePlugin.InvalidDefinitionException(
							     filter + "is not a class name! Ignoring filter for " + mimetype );
      }
      definitionMap.putString(mimetype + AU_FILTER_SUFFIX, filter);
      FilterRule rule = (FilterRule) Class.forName(filter).newInstance();
    }
    catch (Exception ex) {
      /*     throw new DefinablePlugin.InvalidDefinitionException(
	     "Unable to create filter rule class " + filter +
	     "for mimetype " + mimetype);
      */
    }
  }

  public HashMap getAuFilters() {
    HashMap rules = new HashMap();
    Set keyset = definitionMap.keySet();
    for(Iterator it = keyset.iterator(); it.hasNext();) {
      String key = (String) it.next();
      if(key.endsWith(AU_FILTER_SUFFIX)) {
	String mimetype = key.substring(0,key.lastIndexOf(AU_FILTER_SUFFIX));
	rules.put(mimetype, definitionMap.getMapElement(key));
      }
    }
    return rules;
  }

  public void removeAuFilter(String mimetype) {
    definitionMap.removeMapElement(mimetype + AU_FILTER_SUFFIX);
  }

  public void setAuExpectedBasePath(String path) {
    definitionMap.putString(AU_EXPECTED_PATH, path);
  }

  public String getAuExpectedBasePath() {
    return definitionMap.getString(AU_EXPECTED_PATH, null);
  }

  public void removeAuExpectedBasePath() {
    definitionMap.removeMapElement(AU_EXPECTED_PATH);
  }

  public void setNewContentCrawlIntv(long crawlIntv) {
    definitionMap.putLong(AU_NEWCONTENT_CRAWL, crawlIntv);
  }

  public long getNewContentCrawlIntv() {
    return definitionMap.getLong(AU_NEWCONTENT_CRAWL,
				 DefinableArchivalUnit.DEFAULT_NEW_CONTENT_CRAWL_INTERVAL);
  }

  public  void removeNewContentCrawlIntv() {
    definitionMap.removeMapElement(AU_NEWCONTENT_CRAWL);
  }

  public void setAuCrawlDepth(int depth) {
    definitionMap.putInt(AU_CRAWL_DEPTH, depth);
  }

  public int getAuCrawlDepth() {
    return definitionMap.getInt(AU_CRAWL_DEPTH,
				DefinableArchivalUnit.DEFAULT_AU_CRAWL_DEPTH);
  }

  public void removeAuCrawlDepth() {
    definitionMap.removeMapElement(AU_CRAWL_DEPTH);
  }

  public void setAuPauseTime(long pausetime) {
    definitionMap.putLong(AU_PAUSE_TIME, pausetime);
  }

  public long getAuPauseTime() {
    return definitionMap.getLong(AU_PAUSE_TIME,
				 DefinableArchivalUnit.DEFAULT_FETCH_DELAY);
  }

  public void removeAuPauseTime() {
    definitionMap.removeMapElement(AU_PAUSE_TIME);
  }

  public void setAuManifestPage(String manifest) {
    definitionMap.putString(AU_MANIFEST, manifest);
  }

  public String getAuManifestPage() {
    return definitionMap.getString(AU_MANIFEST, getAuStartUrl());
  }

  public void removeAuManifestPage() {
    definitionMap.removeMapElement(AU_MANIFEST);
  }

  public void setPluginName(String name) {
    definitionMap.putString(PLUGIN_NAME, name);
  }

  public String getPluginName() {
    return definitionMap.getString(PLUGIN_NAME, "UNKNOWN");
  }

  public void removePluginName() {
    definitionMap.removeMapElement(PLUGIN_NAME);
  }

  public void setPluginIdentifier(String name) {
    definitionMap.putString(PLUGIN_IDENTIFIER, name);
  }

  public String getPluginIdentifier() {
    return definitionMap.getString(PLUGIN_IDENTIFIER, "UNKNOWN");
  }

  public void removePluginIdentifier() {
    definitionMap.removeMapElement(PLUGIN_IDENTIFIER);
  }

  public void setPluginVersion(String version) {
    definitionMap.putString(PLUGIN_VERSION, version);
  }

  public String getPluginVersion() {
    return definitionMap.getString(PLUGIN_VERSION,
				   DefinablePlugin.DEFAULT_PLUGIN_VERSION);
  }

  public void removePluginVersion() {
    definitionMap.removeMapElement(PLUGIN_VERSION);
  }

  public void setPluginNotes(String notes) {
    definitionMap.putString(PLUGIN_NOTES, notes);
  }

  public String getPluginNotes() {
    return definitionMap.getString(PLUGIN_NOTES, null);
  }

  public void removePluginNotes() {
    definitionMap.removeMapElement(PLUGIN_NOTES);
  }

  public void setPluginConfigDescrs(HashSet descrs) {
    List descrlist = ListUtil.fromArray(descrs.toArray());

    definitionMap.putCollection(PLUGIN_PROPS, descrlist);
  }

  public HashMap getPrintfDescrs() {
    Collection pcd_set = getConfigParamDescrs();
    HashMap pd_map = new HashMap(pcd_set.size());

    for(Iterator it = pcd_set.iterator(); it.hasNext();) {
      ConfigParamDescr cpd = (ConfigParamDescr) it.next();
      String key = cpd.getKey();
      int type = cpd.getType();
      pd_map.put(key, cpd);
      if (type == ConfigParamDescr.TYPE_YEAR) {
	key = DefinableArchivalUnit.AU_SHORT_YEAR_PREFIX + key;
	ConfigParamDescr descr = copyDescr(cpd);
	descr.setDescription(cpd.getDescription() + " (2 digits)");
	descr.setDisplayName(cpd.getDisplayName() + " (2 digits)");
	descr.setKey(key);
	pd_map.put(key, descr);
      }
      else if (type == ConfigParamDescr.TYPE_URL) {
	String mod_key = key + DefinableArchivalUnit.AU_HOST_SUFFIX;
	ConfigParamDescr descr = copyDescr(cpd);
	descr.setDescription(cpd.getDescription() + " (host only)");
	descr.setDisplayName(cpd.getDisplayName() + " (host only)");
	descr.setKey(mod_key);
	pd_map.put(mod_key, descr);
	mod_key = key + DefinableArchivalUnit.AU_PATH_SUFFIX;
	descr = copyDescr(cpd);
	descr.setDescription(cpd.getDescription() + " (path only)");
	descr.setDisplayName(cpd.getDisplayName() + " (path only)");
	descr.setKey(mod_key);
	pd_map.put(mod_key, descr);
      }
    }
    return pd_map;
  }

  private ConfigParamDescr copyDescr(ConfigParamDescr cpd) {
    ConfigParamDescr descr = new ConfigParamDescr();
    descr.setDefinitional(cpd.isDefinitional());
    descr.setDescription(cpd.getDescription());
    descr.setDisplayName(cpd.getDisplayName());
    descr.setKey(cpd.getKey());
    descr.setSize(cpd.getSize());
    descr.setType(cpd.getType());
    return descr;
  }

  public void removePluginConfigDescrs() {
    definitionMap.removeMapElement(PLUGIN_PROPS);
  }

  public void setPluginExceptionHandler(String handler) {
    try {
      definitionMap.putString(PLUGIN_EXCEPTION_HANDLER, handler);
      CacheResultHandler obj =
	(CacheResultHandler) Class.forName(handler).newInstance();
    }
    catch (Exception ex) {
      throw new DefinablePlugin.InvalidDefinitionException(
							   "Unable to create exception handler " + handler, ex);
    }

  }

  public String getPluginExceptionHandler() {
    return definitionMap.getString(PLUGIN_EXCEPTION_HANDLER, null);
  }

  public void removePluginExceptionHandler() {
    definitionMap.removeMapElement(PLUGIN_EXCEPTION_HANDLER);
  }

  public void addSingleExceptionHandler(int resultCode, String exceptionClass) {
    List xlist = (List) definitionMap.getCollection(CM_EXCEPTION_LIST_KEY, null);
    if (xlist == null) {
      xlist = new ArrayList();
      definitionMap.putCollection(CM_EXCEPTION_LIST_KEY, xlist);
    }
    else {
      // we need to remove any previously assigned value.
      removeSingleExceptionHandler(resultCode);
    }
    // add the new entry...
    String entry = String.valueOf(resultCode) + "=" + exceptionClass;
    xlist.add(entry);
  }

  public HashMap getSingleExceptionHandlers() {
    HashMap handlers = new HashMap();
    List xlist = (List) definitionMap.getCollection(CM_EXCEPTION_LIST_KEY, null);
    if(xlist != null) {
      for(Iterator it = xlist.iterator(); it.hasNext();) {
	String  entry = (String) it.next();
	Vector s_vec = StringUtil.breakAt(entry, '=', 2, true, true);
	handlers.put((String)s_vec.get(0), (String) s_vec.get(1));
      }
    }
    return handlers;
  }

  public void removeSingleExceptionHandler(int resultCode) {
    List xlist = (List) definitionMap.getCollection(CM_EXCEPTION_LIST_KEY, null);
    if (xlist == null)return;

    for (Iterator it = xlist.iterator(); it.hasNext(); ) {
      String entry = (String) it.next();
      Vector s_vec = StringUtil.breakAt(entry, '=', 2, true, true);
      int code = Integer.parseInt( ( (String) s_vec.get(0)));
      if (code == resultCode) {
	it.remove();
	break;
      }
    }
    // if this was the last entry we remove the item from the definition map
    if (xlist.size() < 1) {
      definitionMap.removeMapElement(CM_EXCEPTION_LIST_KEY);
    }
  }

  public void addConfigParamDescr(ConfigParamDescr descr) {
    List descrlist = (List) definitionMap.getCollection(PLUGIN_PROPS, null);
    if (descrlist == null) {
      descrlist = new ArrayList();
      definitionMap.putCollection(PLUGIN_PROPS, descrlist);
    }
    else {
      removeConfigParamDescr(descr.getKey());
    }
    descrlist.add(descr);
  }

  public void addConfigParamDescr(String key) {
    Collection knownDescrs = getKnownConfigParamDescrs();
    for(Iterator it = knownDescrs.iterator(); it.hasNext();) {
      ConfigParamDescr cpd = (ConfigParamDescr) it.next();
      if(cpd.getKey() == key) {
	addConfigParamDescr(cpd);
      }
    }
  }

  public ConfigParamDescr getConfigParamDescr(String key) {
    Collection knownDescrs = getKnownConfigParamDescrs();
    for(Iterator it = knownDescrs.iterator(); it.hasNext();) {
      ConfigParamDescr cpd = (ConfigParamDescr) it.next();
      if(cpd.getKey() == key) {
	return cpd;
      }
    }
    return null;
  }

  public Collection getConfigParamDescrs() {
    List defaultConfigParam = ListUtil.list(getConfigParamDescr("base_url"));
    List descrlist = (List) definitionMap.getCollection(PLUGIN_PROPS,
							defaultConfigParam);
    return SetUtil.fromList(descrlist);
  }

  public void removeConfigParamDescr(String key) {
    List descrlist = (List) definitionMap.getCollection(PLUGIN_PROPS, null);
    if (descrlist == null)return;

    for (Iterator it = descrlist.iterator(); it.hasNext(); ) {
      ConfigParamDescr cpd = (ConfigParamDescr) it.next();
      if (cpd.getKey().equals(key)) {
	it.remove();
	break;
      }
    }
  }

  ArrayList cpListeners = new ArrayList();

  public void addParamListener(ConfigParamListener listener) {
    if(!cpListeners.contains(listener)) {
      cpListeners.add(listener);
    }
  }

  public void removeParamListener(ConfigParamListener listener) {
    cpListeners.remove(listener);
  }

  public void notifyParamsChanged() {
    Iterator it = cpListeners.iterator();
    while (it.hasNext()) {
      ConfigParamListener listener = (ConfigParamListener) it.next();
      listener.notifiyParamsChanged();
    }
  }

  public boolean canRemoveParam(ConfigParamDescr descr) {
    String key = descr.getKey();
    PrintfTemplate template = new PrintfTemplate(getAuName());
    if(template.m_tokens.contains(key)) {
      return false;
    }
    template = new PrintfTemplate(getAuStartUrl());
    if(template.m_tokens.contains(key)) {
      return false;
    }
    Collection rules = getAuCrawlRules();
    for(Iterator it = rules.iterator(); it.hasNext();) {
      CrawlRuleTemplate crt = new CrawlRuleTemplate((String)it.next());
      if(crt.m_tokens.contains(key)) {
	return false;
      }
    }
    return true;
  }


  // list utils
  Collection getKnownCacheExceptions() {
    HashSet exceptions = new HashSet();
    // always add cache success
    exceptions.add("org.lockss.util.urlconn.CacheSuccess");
    Class ce = CacheException.class;
    Class[] ce_classes = ce.getDeclaredClasses();
    for (int ic = 0; ic < ce_classes.length; ic++) {
      try {
	if (ce_classes[ic].newInstance() instanceof CacheException) {
	  exceptions.add(ce_classes[ic].getName());
	}
      }
      catch (IllegalAccessException ex) {
      }
      catch (InstantiationException ex) {
      }
    }
    return exceptions;
  }

  public Collection getKnownConfigParamDescrs() {
    Collection descrs = new HashSet(getDefaultConfigParamDescrs().values());
    addUserDefinedConfigParamDescrs(descrs);
    return descrs;
  }


  static Map getDefaultConfigParamDescrs() {
    HashMap descrs = new HashMap();
    ConfigParamDescr[] defaults = ConfigParamDescr.DEFAULT_DESCR_ARRAY;
    for (int ic = 0; ic < defaults.length; ic++) {
      ConfigParamDescr descr = defaults[ic];
      descrs.put(descr.getKey(), descr);
    }
    return Collections.unmodifiableMap(descrs);
  }


  void addUserDefinedConfigParamDescrs(Collection descrs) {
    List descrlist = (List) definitionMap.getCollection(PLUGIN_PROPS, null);
    if (descrlist != null) {
      for (Iterator it = descrlist.iterator(); it.hasNext(); ) {
	ConfigParamDescr cpd = (ConfigParamDescr) it.next();
	descrs.add(cpd);
      }
    }
  }

  // validators
  boolean isValidPrintf(String format, String[] strs) {
    ArrayList args = new ArrayList();
    try {
      for (int i = 0; i < strs.length; i++) {
	Object val = definitionMap.getMapElement(strs[i]);
	args.add(val);
      }
      PrintfFormat pf = new PrintfFormat(format);
      return pf.sprintf(args.toArray()) != null;
    } catch (Exception ex) {
      return false;
    }
  }


  // fetching the map
  ExternalizableMap getMap() {
    return definitionMap;
  }

}
