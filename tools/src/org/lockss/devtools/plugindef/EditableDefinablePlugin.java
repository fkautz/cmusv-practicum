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

import java.io.IOException;
import java.util.*;

import org.apache.commons.lang.StringUtils;
import org.lockss.daemon.*;
import org.lockss.plugin.*;
import org.lockss.plugin.definable.*;
import org.lockss.plugin.definable.DefinableArchivalUnit.ConfigurableCrawlWindow;
import org.lockss.util.*;
import org.lockss.util.urlconn.*;

public class EditableDefinablePlugin extends DefinablePlugin {

  protected static final String DEFAULT_RULE_INCLUDE_STARTURL_FRAGMENT = "1,";

  protected static final String DEFAULT_RULE_EXCLUDENOMATCH_BASEURL = "4,\"^%s\", base_url";

  static final protected String PLUGIN_IDENTIFIER = "plugin_identifier";

  static public String[] CONFIG_PARAM_TYPES = ConfigParamDescr.TYPE_STRINGS;

  static public Map DEFAULT_CONFIG_PARAM_DESCRS = getDefaultConfigParamDescrs();
  static Logger logger = Logger.getLogger("EditableDefinablePlugin");
  protected PersistentPluginState pluginState;

  public EditableDefinablePlugin() {
      pluginState = new PersistentPluginState();
      super.classLoader = getClass().getClassLoader();
  }

  // for reading map files
  public void loadMap(String location, String name) throws Exception {
    String fileName = location + "/" + name;
    logger.info("Loading definition map from: " + fileName);
    definitionMap.loadMap(location, name);
    logger.debug("Definition map loaded from: " + fileName);
    String err = definitionMap.getErrorString();
    if (err != null) {
      Exception exc = new Exception(err);
      logger.error("Error while loading definition map from: " + fileName, exc);
      throw exc;
    }
  }

  // for writing map files
  public void writeMap(String location, String name) throws IOException {
    String fileName = location + "/" + name;
    logger.info("Storing definition map in: " + fileName);
    // make sure we don't have any AU Config info in map
    HashMap printfMap = getPrintfDescrs();
    for (Iterator it = printfMap.keySet().iterator() ; it.hasNext() ; ) {
      definitionMap.removeMapElement((String)it.next());
    }
    // store the configuration map
    definitionMap.storeMap(location, name);
    logger.debug("Definition map stored in: " + fileName);
    String err = definitionMap.getErrorString();
    if (err != null) {
      IOException ioe = new IOException(err);
      logger.error("Error while storing definition map", ioe);
      throw ioe;
    }
  }

  public String getMapName() {
    logger.info("The map name is: " + mapName);
    return mapName;
  }

  public void setMapName(String name) {
    if (name.endsWith(MAP_SUFFIX)) {
      mapName = name;
    }
    else {
      mapName = name + MAP_SUFFIX;
    }
    logger.info("Setting the map name to: " + mapName);
  }

  public void setPluginState(int field, String key, String value) {
    // TODO
      pluginState.setPluginState(field, key, value);
  }

  public PersistentPluginState getPluginState() {
    // TODO
      return pluginState;
  }

  public void setCrawlType(String crawlType) {
    logger.info("Setting crawl type to: " + crawlType);
    definitionMap.putString(DefinablePlugin.CM_CRAWL_TYPE, crawlType);
  }

  public String getCrawlType() {
    // FIXME: default?
    String ret = definitionMap.getString(DefinablePlugin.CM_CRAWL_TYPE, DefinablePlugin.CRAWL_TYPES[0]);
    logger.info("The crawl type is: " + ret);
    return ret;
  }

  public void removeCrawlType() {
    logger.info("Removing the crawl type");
    definitionMap.removeMapElement(DefinablePlugin.CM_CRAWL_TYPE);
  }

  public void setAuStartUrl(String startUrl) {
    logger.info("Setting the AU start URL to: " + startUrl);
    definitionMap.putString(DefinableArchivalUnit.AU_START_URL_KEY, startUrl);
  }

  public String getAuStartUrl() {
    String ret = definitionMap.getString(DefinableArchivalUnit.AU_START_URL_KEY, null);
    logger.debug("The AU start URL is: " + ret);
    return ret;
  }

  public void removeAuStartUrl() {
    logger.info("Removing the AU start URL");
    definitionMap.removeMapElement(DefinableArchivalUnit.AU_START_URL_KEY);
  }

  public void setAuName(String name) {
    logger.info("Setting the AU name to: " + name);
    definitionMap.putString(DefinableArchivalUnit.AU_NAME_KEY, name);
  }

  public String getAuName() {
    String ret = definitionMap.getString(DefinableArchivalUnit.AU_NAME_KEY, null);
    logger.info("The AU name is: " + ret);
    return ret;
  }

  public void removeAuName() {
    logger.info("Removing the AU name");
    definitionMap.removeMapElement(DefinableArchivalUnit.AU_NAME_KEY);
  }

  public void setAuCrawlRules(Collection rules) {
    logger.info("Setting the AU crawl rules");
    definitionMap.putCollection(DefinableArchivalUnit.AU_RULES_KEY, rules);
  }

  public Collection getAuCrawlRules() {
    logger.info("Retrieving the AU crawl rules");
    Collection defaultCrawlRules = new ArrayList();
    defaultCrawlRules.add(DEFAULT_RULE_EXCLUDENOMATCH_BASEURL);
    String startUrl = getAuStartUrl();
    if (startUrl != null) {
      defaultCrawlRules.add(DEFAULT_RULE_INCLUDE_STARTURL_FRAGMENT + startUrl);
    }
    if (logger.isDebug()) {
      logger.debug("Retrieving the AU crawl rules in detail");
      List rules = (List)definitionMap.getCollection(DefinableArchivalUnit.AU_RULES_KEY, null);
      if (rules != null) {
        Iterator iter = rules.iterator();
        for (int ix = 1 ; iter.hasNext() ; ++ix) {
          logger.debug("AU crawl rule " + ix + ": " + iter.next());
        }
      }
      else {
        logger.debug("Retrieving the AU crawl rules in detail: none");
      }
    }
    return definitionMap.getCollection(DefinableArchivalUnit.AU_RULES_KEY, defaultCrawlRules);
  }

  public void removeAuCrawlRules() {
    logger.info("Removing the AU crawl rules");
    definitionMap.removeMapElement(DefinableArchivalUnit.AU_RULES_KEY);
  }

  public void addAuCrawlRule(String rule) {
    logger.info("Adding an AU crawl rule: " + rule);
    List rules = (List)definitionMap.getCollection(DefinableArchivalUnit.AU_RULES_KEY, new ArrayList());
    if (rules.contains(rule)) {
      logger.warning("Not adding the AU crawl rule (already in the list): " + rule);
      return;
    }
    rules.add(rule);
    definitionMap.putCollection(DefinableArchivalUnit.AU_RULES_KEY, rules);
  }

  public void removeCrawlRule(String rule) {
    logger.info("Removing an AU crawl rule: " + rule);
    List rules = (List)definitionMap.getCollection(DefinableArchivalUnit.AU_RULES_KEY, null);
    if (rules == null) {
      logger.warning("Not removing the AU crawl rule (no crawl rules in the list): " + rule);
      return;
    }

    int counter = 0;
    for (Iterator it = rules.iterator() ; it.hasNext() ; ) {
      String str = (String)it.next();
      if (str.equals(rule)) {
        ++counter;
        it.remove();
      }
    }
    logger.info("Removed " + counter + " matches of the AU crawl rule: " + rule);
  }

  public void setAuCrawlWindow(String configurableCrawlWindowClass,
                               boolean tryDynamic) {
    logger.info("Setting the AU configurable crawl window class to: " + configurableCrawlWindowClass);
    try {
      if (tryDynamic) {
        tryDynamic(configurableCrawlWindowClass, ConfigurableCrawlWindow.class);
      }
      definitionMap.putString(DefinableArchivalUnit.AU_CRAWL_WINDOW_KEY, configurableCrawlWindowClass);
    }
    catch (DynamicallyLoadedComponentException dlce) {
      String logMessage = "Failed to set the AU configurable crawl window class to " + configurableCrawlWindowClass;
      logger.error(logMessage, dlce);
      throw dlce; // rethrow
    }
    catch (InvalidDefinitionException ide) {
      String logMessage = "Failed to set the AU configurable crawl window class to " + configurableCrawlWindowClass;
      logger.error(logMessage, ide);
      throw new InvalidDefinitionException(logMessage, ide);
    }
  }

  public String getAuCrawlWindow() {
    String ret = definitionMap.getString(DefinableArchivalUnit.AU_CRAWL_WINDOW_KEY, null);
    logger.info("The AU configurable crawl window class is: " + ret);
    return ret;
  }

  public void removeAuCrawlWindow() {
    logger.info("Removing the AU configurable crawl window");
    definitionMap.removeMapElement(DefinableArchivalUnit.AU_CRAWL_WINDOW_KEY);
  }

  public void setAuCrawlWindowSer(CrawlWindow crawlWindow) {
    logger.info("Setting the serialized AU crawl window to: " + crawlWindow.toString());
    definitionMap.setMapElement(DefinableArchivalUnit.AU_CRAWL_WINDOW_SER_KEY, crawlWindow);
  }

  public CrawlWindow getAuCrawlWindowSer() {
    CrawlWindow ret = (CrawlWindow)(definitionMap.getMapElement(DefinableArchivalUnit.AU_CRAWL_WINDOW_SER_KEY));
    logger.debug("The serialized AU crawl window is: " + ret);
    return ret;
  }

  public void removeAuCrawlWindowSer() {
    logger.info("Removing the serialized AU crawl window");
    definitionMap.removeMapElement(DefinableArchivalUnit.AU_CRAWL_WINDOW_SER_KEY);
  }

  public void setAuFilter(String mimeType, List rules) {
    logger.info("Set the AU filter rule for MIME type " + mimeType);
    if (rules.size() > 0) {
      try {
	FilterRule rule = new DefinableFilterRule(rules);
        logger.debug("Definable filter rule instantiated for MIME type " + mimeType + "from List");
	definitionMap.putCollection(mimeType + DefinableArchivalUnit.AU_FILTER_SUFFIX, rules);
      }
      catch (Exception ex) {
	throw new InvalidDefinitionException("Failed to create the AU filter rule for MIME type " + mimeType);
      }
    }
    else {
      logger.warning("Empty list for MIME type " + mimeType + "; removing it from the AU filter rules");
      definitionMap.removeMapElement(mimeType + DefinableArchivalUnit.AU_FILTER_SUFFIX);
    }
  }

  public void setAuFilter(String mimeType,
                          String filterRuleClass,
                          boolean tryDynamic) {
    logger.info("Setting AU filter rule for MIME type " + mimeType + " to: " + filterRuleClass);
    try {
      if (tryDynamic) {
        tryDynamic(filterRuleClass, FilterRule.class);
      }
      definitionMap.putString(mimeType + DefinableArchivalUnit.AU_FILTER_SUFFIX, filterRuleClass);
    }
    catch (DynamicallyLoadedComponentException dlce) {
      throw dlce; // rethrow
    }
    catch (InvalidDefinitionException ide) {
      String logMessage = "Failed to set the AU filter rule for MIME type " + mimeType + " to " + filterRuleClass;
      logger.error(logMessage, ide);
      throw new InvalidDefinitionException(logMessage, ide);
    }
  }

  public HashMap getAuFilters() {
    logger.info("Retrieving the AU filter rules");
    HashMap rules = new HashMap();
    Iterator it = definitionMap.keySet().iterator();
    for(int ix = 1 ; it.hasNext() ; ++ix) {
      String key = (String)it.next();
      if (key.endsWith(DefinableArchivalUnit.AU_FILTER_SUFFIX)) {
        String mimeType = StringUtils.chomp(key, DefinableArchivalUnit.AU_FILTER_SUFFIX);
        logger.debug("MIME type " + ix + ": " + key + " maps to " + definitionMap.getMapElement(key));
	rules.put(mimeType, definitionMap.getMapElement(key));
      }
    }
    return rules;
  }

  public void removeAuFilter(String mimeType) {
    logger.info("Removing the AU filter rule for MIME type " + mimeType);
    definitionMap.removeMapElement(mimeType + DefinableArchivalUnit.AU_FILTER_SUFFIX);
  }

  public void setAuExpectedBasePath(String path) {
    logger.info("Setting AU expected base path to: " + path);
    definitionMap.putString(DefinableArchivalUnit.AU_EXPECTED_PATH, path);
  }

  public String getAuExpectedBasePath() {
    String ret = definitionMap.getString(DefinableArchivalUnit.AU_EXPECTED_PATH, null);
    logger.info("The AU expected base path is: " + ret);
    return ret;
  }

  public void removeAuExpectedBasePath() {
    logger.info("Removing the AU expected base path");
    definitionMap.removeMapElement(DefinableArchivalUnit.AU_EXPECTED_PATH);
  }

  public void setNewContentCrawlInterval(long newContentCrawlInterval) {
    logger.info("Setting the new content crawl itnerval to: " + newContentCrawlInterval);
    definitionMap.putLong(DefinableArchivalUnit.AU_DEFAULT_NC_CRAWL_KEY, newContentCrawlInterval);
  }

  public long getNewContentCrawlInterval() {
    // FIXME: Uses daemon default
    long ret = definitionMap.getLong(DefinableArchivalUnit.AU_DEFAULT_NC_CRAWL_KEY,
                                     DefinableArchivalUnit.DEFAULT_NEW_CONTENT_CRAWL_INTERVAL);
    logger.debug("The new content crawl interval is: " + ret);
    return ret;
  }

  public void removeNewContentCrawlInterval() {
    logger.info("Removing the new content crawl interval");
    definitionMap.removeMapElement(DefinableArchivalUnit.AU_DEFAULT_NC_CRAWL_KEY);
  }

  public void setAuCrawlDepth(int depth) {
    logger.info("Setting the AU crawl depth to: " + depth);
    definitionMap.putInt(DefinableArchivalUnit.AU_CRAWL_DEPTH, depth);
  }

  public int getAuCrawlDepth() {
    // FIXME: Uses daemon default
    int ret = definitionMap.getInt(DefinableArchivalUnit.AU_CRAWL_DEPTH,
                                   DefinableArchivalUnit.DEFAULT_AU_CRAWL_DEPTH);
    logger.debug("The AU crawl depth is: " + ret);
    return ret;
  }

  public void removeAuCrawlDepth() {
    logger.info("Removing the AU crawl depth");
    definitionMap.removeMapElement(DefinableArchivalUnit.AU_CRAWL_DEPTH);
  }

  public void setAuPauseTime(long pausetime) {
    logger.info("Setting the AU pause time to: " + pausetime);
    definitionMap.putLong(DefinableArchivalUnit.AU_DEFAULT_PAUSE_TIME, pausetime);
  }

  public long getAuPauseTime() {
    // FIXME: Uses daemon default
    long ret = definitionMap.getLong(DefinableArchivalUnit.AU_DEFAULT_PAUSE_TIME,
                                     DefinableArchivalUnit.DEFAULT_FETCH_DELAY);
    logger.debug("The AU pause time is: " + ret);
    return ret;
  }

  public void removeAuPauseTime() {
    logger.info("Removing the AU pause time");
    definitionMap.removeMapElement(DefinableArchivalUnit.AU_DEFAULT_PAUSE_TIME);
  }

  public void setAuManifestPage(String manifest) {
    logger.info("Setting the AU manifest page to: " + manifest);
    definitionMap.putString(DefinableArchivalUnit.AU_MANIFEST_KEY, manifest);
  }

  public String getAuManifestPage() {
    // FIXME: default?
    String ret = definitionMap.getString(DefinableArchivalUnit.AU_MANIFEST_KEY, getAuStartUrl());
    logger.info("The AU manifest page is: " + ret);
    return ret;
  }

  public void removeAuManifestPage() {
    logger.info("Removing the AU manifest page");
    definitionMap.removeMapElement(DefinableArchivalUnit.AU_MANIFEST_KEY);
  }

  public void setPluginName(String name) {
    logger.info("Setting the plugin name to: " + name);
    definitionMap.putString(DefinablePlugin.CM_NAME_KEY, name);
  }

  public String getPluginName() {
    // FIXME: default value
    String ret = super.getPluginName();
    if (StringUtils.isEmpty(ret)) {
      ret = "UNKNOWN";
    }
    logger.debug("The plugin name is: " + ret);
    return ret;
  }

  public void removePluginName() {
    logger.info("Removing the plugin name");
    definitionMap.removeMapElement(DefinablePlugin.CM_NAME_KEY);
  }

  public void setPluginIdentifier(String name) {
    logger.info("Setting the plugin identifier to: " + name);
    definitionMap.putString(PLUGIN_IDENTIFIER, name);
  }

  public String getPluginIdentifier() {
    // FIXME: default value
    String ret = definitionMap.getString(PLUGIN_IDENTIFIER, "UNKNOWN");
    logger.debug("The plugin identifier is: " + ret);
    return ret;
  }

  public void removePluginIdentifier() {
    logger.info("Removing the plugin identifier");
    definitionMap.removeMapElement(PLUGIN_IDENTIFIER);
  }

  public void setPluginVersion(String version) {
    logger.info("Setting the plugin version to: " + version);
    definitionMap.putString(DefinablePlugin.CM_VERSION_KEY, version);
  }

  public String getPluginVersion() {
    String ret = super.getVersion();
    logger.info("The plugin version is: " + ret);
    return ret;
  }

  public void removePluginVersion() {
    logger.info("Removing the plugin version");
    definitionMap.removeMapElement(DefinablePlugin.CM_VERSION_KEY);
  }

  public void setRequiredDaemonVersion(String requiredDaemonVersion) {
    logger.info("Setting the required daemon version to: " + requiredDaemonVersion);
    definitionMap.putString(DefinablePlugin.CM_REQUIRED_DAEMON_VERSION_KEY,
                            requiredDaemonVersion);
  }

  public String getRequiredDaemonVersion() {
    String ret = super.getRequiredDaemonVersion();
    logger.info("The required daemon version is: " + ret);
    return ret;
  }

  public void removeRequiredDaemonVersion() {
    logger.info("Removing the required daemon version");
    definitionMap.removeMapElement(DefinablePlugin.CM_REQUIRED_DAEMON_VERSION_KEY);
  }

  public void setPluginNotes(String notes) {
    if (notes != null) {
      logger.info("Setting the plugin notes to: "
                  + (logger.isDebug() ? notes : StringUtils.abbreviate(notes, 50)));
    }
    else {
      logger.warning("Setting plugin notes to null");
    }
    definitionMap.putString(DefinablePlugin.CM_NOTES_KEY, notes);
  }

  public String getPluginNotes() {
    String ret = super.getPluginNotes();
    if (ret != null) {
      logger.info("The plugin notes are: "
                  + (logger.isDebug() ? ret : StringUtils.abbreviate(ret, 50)));
    }
    else {
      logger.info("The plugin notes are null");
    }
    return ret;
  }

  public void removePluginNotes() {
    logger.info("Removing the plugin notes");
    definitionMap.removeMapElement(DefinablePlugin.CM_NOTES_KEY);
  }

  public void setPluginConfigDescrs(HashSet descrs) {
    logger.info("Setting the plugin configuration parameters");
    if (logger.isDebug()) {
      logger.debug("Setting the plugin configuration parameters in detail");
      Iterator iter = descrs.iterator();
      for (int ix = 1 ; iter.hasNext() ; ++ix) {
        logger.debug("Plugin configuration parameter " + ix + ": "
                     + ((ConfigParamDescr)iter.next()).toDetailedString());
      }
    }
    List descrlist = ListUtil.fromArray(descrs.toArray());
    definitionMap.putCollection(DefinablePlugin.CM_CONFIG_PROPS_KEY, descrlist);
  }

  /**
   * <p>Same as calling {@link #getPrintfDescrs(boolean)} with a
   * true argument.</p>
   * @return The result of <code>getPrinfDescrs(true)</code>
   * @see #getPrintfDescrs(boolean)
   */
  public HashMap getPrintfDescrs() {
    return getPrintfDescrs(true);
  }

  public HashMap getPrintfDescrs(boolean includeUrlVariants) {
    logger.info("Retrieving configuration parameters for printf, "
                + (includeUrlVariants ? "including" : "without")
                + " URL variants");
    Collection cpdSet = getConfigParamDescrs();
    HashMap printfMap = new HashMap(cpdSet.size());

    for (Iterator it = cpdSet.iterator() ; it.hasNext() ; ) {
      ConfigParamDescr cpd = (ConfigParamDescr)it.next();
      String key = cpd.getKey();
      int type = cpd.getType();
      printfMap.put(key, cpd);
      if (type == ConfigParamDescr.TYPE_YEAR) {
        logger.debug("Adding a 2-digit version of parameter " + key);
	key = DefinableArchivalUnit.AU_SHORT_YEAR_PREFIX + key;
	ConfigParamDescr descr = copyDescr(cpd);
	descr.setDescription(cpd.getDescription() + " (2 digits)");
	descr.setDisplayName(cpd.getDisplayName() + " (2 digits)");
	descr.setKey(key);
	printfMap.put(key, descr);
      }
      else if (type == ConfigParamDescr.TYPE_URL && includeUrlVariants) {
        logger.debug("Adding a host-only version of parameter " + key);
	String mod_key = key + DefinableArchivalUnit.AU_HOST_SUFFIX;
	ConfigParamDescr descr = copyDescr(cpd);
	descr.setDescription(cpd.getDescription() + " (host only)");
	descr.setDisplayName(cpd.getDisplayName() + " (host only)");
	descr.setKey(mod_key);
	printfMap.put(mod_key, descr);
        logger.debug("Adding a path-only version of parameter " + key);
	mod_key = key + DefinableArchivalUnit.AU_PATH_SUFFIX;
	descr = copyDescr(cpd);
	descr.setDescription(cpd.getDescription() + " (path only)");
	descr.setDisplayName(cpd.getDisplayName() + " (path only)");
	descr.setKey(mod_key);
	printfMap.put(mod_key, descr);
      }
    }
    return printfMap;
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
    logger.info("Removing the plugin configuration parameters");
    definitionMap.removeMapElement(DefinablePlugin.CM_CONFIG_PROPS_KEY);
  }

  public void setPluginExceptionHandler(String cacheResultHandlerClass,
                                        boolean tryDynamic) {
    logger.info("Setting the plugin exception handler to: " + cacheResultHandlerClass);
    try {
      if (tryDynamic) {
        tryDynamic(cacheResultHandlerClass, CacheResultHandler.class);
      }
      definitionMap.putString(DefinablePlugin.CM_EXCEPTION_HANDLER_KEY, cacheResultHandlerClass);
    }
    catch (DynamicallyLoadedComponentException dlce) {
      throw dlce; // rethrow
    }
    catch (InvalidDefinitionException ide) {
      String logMessage = "Failed to set the plugin exception handler to " + cacheResultHandlerClass;
      logger.error(logMessage, ide);
      throw new InvalidDefinitionException(logMessage, ide);
    }
  }

  public String getPluginExceptionHandler() {
    String ret = definitionMap.getString(DefinablePlugin.CM_EXCEPTION_HANDLER_KEY, null);
    logger.debug("The plugin exception handler is: " + ret);
    return ret;
  }

  public void removePluginExceptionHandler() {
    logger.info("Removing the plugin exception handler");
    definitionMap.removeMapElement(DefinablePlugin.CM_EXCEPTION_HANDLER_KEY);
  }

  public void addSingleExceptionHandler(int resultCode, String exceptionClass) {
    logger.info("Setting the single exception handler for code " + resultCode + " to: " + exceptionClass);
    List xlist = (List)definitionMap.getCollection(DefinablePlugin.CM_EXCEPTION_LIST_KEY, null);
    if (xlist == null) {
      logger.debug("Exception list was empty; creating a list");
      xlist = new ArrayList();
      definitionMap.putCollection(DefinablePlugin.CM_EXCEPTION_LIST_KEY, xlist);
    }
    else {
      // we need to remove any previously assigned value.
      logger.debug("Removing the existing exception handler for code" + resultCode);
    }
    // add the new entry...
    String entry = String.valueOf(resultCode) + "=" + exceptionClass;
    xlist.add(entry);
  }

  public HashMap getSingleExceptionHandlers() {
    logger.info("Retrieving the single exception handlers");
    // FIXME: default?
    HashMap handlers = new HashMap();
    List xlist = (List) definitionMap.getCollection(DefinablePlugin.CM_EXCEPTION_LIST_KEY, null);
    if (xlist != null) {
      logger.debug("Retrieving the single exception handlers in detail");
      Iterator it = xlist.iterator();
      for (int ix = 1 ; it.hasNext() ; ++ix) {
	String  entry = (String) it.next();
	Vector s_vec = StringUtil.breakAt(entry, '=', 2, true, true);
	String code = (String)s_vec.get(0);
        String handler = (String)s_vec.get(1);
        logger.debug("Single exception handler " + ix + ": "
                     + code + " maps to " + handler);
        handlers.put(code, handler);
      }
    }
    else {
      logger.debug("There are no single exception handlers");
    }
    return handlers;
  }

  public void removeSingleExceptionHandler(int resultCode) {
    logger.info("Removing the single exception handler for code " + resultCode);
    List xlist = (List)definitionMap.getCollection(DefinablePlugin.CM_EXCEPTION_LIST_KEY, null);
    if (xlist == null) {
      logger.debug("Not removing the single exception handler for code" + resultCode
                   + " (empty exception handler list)");
      return;
    }

    String codeString = Integer.toString(resultCode);
    for (Iterator it = xlist.iterator() ; it.hasNext() ; ) {
      String entry = (String)it.next();
      Vector s_vec = StringUtil.breakAt(entry, '=', 2, true, true);
      if (codeString.equals((String)s_vec.get(0))) {
        logger.debug("Found single exception handler to remove for code " + codeString);
	it.remove();
	break;
      }
    }
    // if this was the last entry we remove the item from the definition map
    if (xlist.size() < 1) {
      logger.debug("Removing the handler results in an empty list; removing from map");
      definitionMap.removeMapElement(DefinablePlugin.CM_EXCEPTION_LIST_KEY);
    }
  }

  public void addConfigParamDescr(ConfigParamDescr descr) {
    logger.info("Adding a configuration parameter with key " + descr.toDetailedString());
    List descrList = (List)definitionMap.getCollection(DefinablePlugin.CM_CONFIG_PROPS_KEY, null);
    if (descrList == null) {
      logger.debug("There were no configuration parameters; creating list");
      descrList = new ArrayList();
      definitionMap.putCollection(DefinablePlugin.CM_CONFIG_PROPS_KEY, descrList);
    }
    else {
      logger.debug("Possibly removing parameter with same key prior to adding");
      removeConfigParamDescr(descr.getKey());
    }
    descrList.add(descr);
  }

  /*
   * Temporarily disabling: essentially identical to
   * getConfigParamDescr() below and uncalled.
   */
//  public void addConfigParamDescr(String key) {
//    logger.info("Adding a configuration parameter by name: " + key);
//    Collection knownDescrs = getKnownConfigParamDescrs();
//    for (Iterator it = knownDescrs.iterator() ; it.hasNext() ; ) {
//      ConfigParamDescr cpd = (ConfigParamDescr)it.next();
//      if (cpd.getKey().equals(key)) {
//        logger.debug("Found a matching known parameters; adding it");
//	addConfigParamDescr(cpd);
//        break;
//      }
//    }
//  }

  public ConfigParamDescr getConfigParamDescr(String key) {
    Collection knownDescrs = getKnownConfigParamDescrs();
    for (Iterator it = knownDescrs.iterator() ; it.hasNext() ; ) {
      ConfigParamDescr cpd = (ConfigParamDescr) it.next();
      if (cpd.getKey().equals(key)) {
        logger.debug("Found a configuration parameter with key " + key);
	return cpd;
      }
    }
    logger.warning("No known configuration parameter with key " + key);
    return null;
  }

  public Collection getConfigParamDescrs() {
    logger.info("Retrieving configuration parameters");
    // FIXME: default?
    List defaultConfigParam = ListUtil.list(getConfigParamDescr(ConfigParamDescr.BASE_URL.getKey()));
    List descrList = (List)definitionMap.getCollection(DefinablePlugin.CM_CONFIG_PROPS_KEY,
                                                       defaultConfigParam);
    return SetUtil.fromList(descrList);
  }

  public void removeConfigParamDescr(String key) {
    logger.info("Removing the configuration parameter with key " + key);
    List descrlist = (List)definitionMap.getCollection(DefinablePlugin.CM_CONFIG_PROPS_KEY, null);
    if (descrlist == null) {
      logger.error("Not removing the configuration parameter with key " + key
                   + " (no configuration parameters)");
      return;
    }

    for (Iterator it = descrlist.iterator() ; it.hasNext() ; ) {
      ConfigParamDescr cpd = (ConfigParamDescr)it.next();
      if (cpd.getKey().equals(key)) {
        logger.debug("Removed a configuration parameter with key " + key);
	it.remove();
	return;
      }
    }
    logger.debug("Not removing the configuration parameter with key " + key
                 + " (no such key)");
  }

  ArrayList cpListeners = new ArrayList();

  public void addParamListener(ConfigParamListener listener) {
    if (!cpListeners.contains(listener)) {
      cpListeners.add(listener);
    }
  }

  public void removeParamListener(ConfigParamListener listener) {
    cpListeners.remove(listener);
  }

  public void notifyParamsChanged() {
    logger.debug3("Notifying configuration parameter listeners");
    for (Iterator it = cpListeners.iterator() ; it.hasNext() ; ) {
      ConfigParamListener listener = (ConfigParamListener) it.next();
      listener.notifiyParamsChanged();
    }
    logger.debug3("Notified configuration parameter listeners");
  }

  public boolean canRemoveParam(ConfigParamDescr descr) {
    String key = descr.getKey();
    PrintfTemplate template = new PrintfTemplate(getAuName());
    if (template.m_tokens.contains(key)) {
      return false;
    }
    template = new PrintfTemplate(getAuStartUrl());
    if (template.m_tokens.contains(key)) {
      return false;
    }
    Collection rules = getAuCrawlRules();
    for (Iterator it = rules.iterator() ; it.hasNext() ; ) {
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
    exceptions.add(CacheSuccess.class.getName());
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
    logger.debug("Retrieving known configuration parameters");
    Collection descrs = new HashSet(getDefaultConfigParamDescrs().values());
    addUserDefinedConfigParamDescrs(descrs);
    return descrs;
  }


  static Map getDefaultConfigParamDescrs() {
    if (logger != null) { // FIXME: class loading circularity: prevent NPE
      logger.debug("Retrieving default configuration parameters");
    }
    HashMap descrs = new HashMap();
    ConfigParamDescr[] defaults = ConfigParamDescr.DEFAULT_DESCR_ARRAY;
    for (int ic = 0 ; ic < defaults.length ; ic++) {
      ConfigParamDescr descr = defaults[ic];
      descrs.put(descr.getKey(), descr);
    }
    return Collections.unmodifiableMap(descrs);
  }


  void addUserDefinedConfigParamDescrs(Collection descrs) {
    logger.debug("Adding user-defined configuration parameters");
    List descrlist = (List)definitionMap.getCollection(DefinablePlugin.CM_CONFIG_PROPS_KEY, null);
    if (descrlist != null) {
      for (Iterator it = descrlist.iterator() ; it.hasNext() ; ) {
	ConfigParamDescr cpd = (ConfigParamDescr)it.next();
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

  /**
   * <p>Tries to load a class with the given name using
   * {@link Class#forName(String)}, then tries to instantiate an
   * object using {@link Class#newInstance()}, then verifies that
   * the given class is the same as, a superclass of, or a
   * superinterface of, the resulting object using
   * {@link Class#isAssignableFrom(Class)}.</p>
   * @param className The name of a class that needs to be loaded and
   *                  of which an instance is to be created.
   * @param cla       The class that needs to be the same as, a
   *                  superclass of, or a superinterface of, the
   *                  resulting instance.
   * @return The resulting instance.
   * @throws InvalidDefinitionException if {@link Class#forName(String)}
   *                                    or {@link Class#newInstance()}
   *                                    throw, or if the resulting
   *                                    instance is not assignable to
   *                                    the given class.
   * @see Class#forName(String)
   * @see Class#newInstance
   * @see Class#isAssignableFrom
   */
  protected static Object tryDynamic(String className, Class cla) {
    try {
      Class retClass = Class.forName(className);
      if (!cla.isAssignableFrom(retClass)) {
        throw new ClassCastException(className);
      }
      Object ret = retClass.newInstance();
      logger.debug("Instantiated an object of type " + className);
      return ret;
    }
    catch (ClassCastException cce) {
      String logMessage = className + " is not of type " + cla.getName();
      logger.debug(logMessage, cce);
      throw new DynamicallyLoadedComponentException(logMessage, cce);
    }
    catch (ClassNotFoundException cnfe) {
      String logMessage = "Failed to load class " + className + " dynamically";
      logger.debug(logMessage, cnfe);
      throw new DynamicallyLoadedComponentException(logMessage, cnfe);
    }
    catch (InstantiationException ie) {
      String logMessage = "Failed to instantiate an object of type " + className + " dynamically";
      logger.debug(logMessage, ie);
      throw new DynamicallyLoadedComponentException(logMessage, ie);
    }
    catch (IllegalAccessException iae) {
      String logMessage = "Failed to instantiate an object of type " + className + " dynamically";
      logger.debug(logMessage, iae);
      throw new DynamicallyLoadedComponentException(logMessage, iae);
    }
  }

  public static class DynamicallyLoadedComponentException extends InvalidDefinitionException {
    public DynamicallyLoadedComponentException() {
      super();
    }
    public DynamicallyLoadedComponentException(String message) {
      super(message);
    }
    public DynamicallyLoadedComponentException(String message, Throwable cause) {
      super(message, cause);
    }
    public DynamicallyLoadedComponentException(Throwable cause) {
      super(cause);
    }
  }

}
