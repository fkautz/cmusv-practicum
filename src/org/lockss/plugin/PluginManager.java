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

package org.lockss.plugin;

import java.util.*;
import java.util.jar.*;
import java.io.*;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.KeyStore;
import org.lockss.daemon.*;
import org.lockss.daemon.status.*;
import org.lockss.poller.*;
import org.lockss.util.*;
import org.lockss.app.BaseLockssManager;
import org.lockss.plugin.definable.DefinablePlugin;
import org.lockss.crawler.CrawlManager;

/**
 * Plugin global functionality
 *
 * @author  TAL
 * @version 0.0
 */
public class PluginManager extends BaseLockssManager {
  public static final String PARAM_AU_TREE = Configuration.PREFIX + "au";

  static final String PARAM_PLATFORM_DISK_SPACE_LIST =
    Configuration.PLATFORM + "diskSpacePaths";

  private static String PARAM_PLUGIN_LOCATION =
    Configuration.PLATFORM + "pluginDir";
  private static String DEFAULT_PLUGIN_LOCATION = "plugins";

  static String PARAM_PLUGIN_REGISTRY =
    Configuration.PREFIX + "plugin.registry";

  static String PARAM_PLUGIN_XML_PLUGINS =
    Configuration.PREFIX + "plugin.xmlPlugins";

  static String PARAM_REMOVE_STOPPED_AUS =
    Configuration.PREFIX + "plugin.removeStoppedAus";
  static boolean DEFAULT_REMOVE_STOPPED_AUS = true;

  static String PARAM_PLUGIN_REGISTRIES =
    Configuration.PREFIX + "plugin.registries";

  static String PARAM_KEYSTORE_LOCATION =
    Configuration.PREFIX + "plugin.keystore.location";
  static String DEFAULT_KEYSTORE_LOCATION =
    "/org/lockss/plugin/lockss.keystore";
  static String PARAM_KEYSTORE_PASSWORD =
    Configuration.PREFIX + "plugin.keystore.password";
  static String DEFAULT_KEYSTORE_PASSWORD =
    "password";

  static String PARAM_REGISTRY_CRAWL_INTERVAL =
    "plugin.registries.crawl.interval";
  static String DEFAULT_REGISTRY_CRAWL_INTERVAL =
    "1d"; // not specified as a long value because this will be passed as
          // a string literal to the AU config.

  public static final String PARAM_PLUGIN_LOAD_TIMEOUT =
    Configuration.PREFIX + "plugin.load.timeout";
  public static final long DEFAULT_PLUGIN_LOAD_TIMEOUT =
    Constants.MINUTE;

  static final String PARAM_TITLE_DB = ConfigManager.PARAM_TITLE_DB;

  // prefix for non-plugin AU params
  public static final String AU_PARAM_RESERVED = "reserved";
  // per AU params known to and processed by daemon, not plugin
  static final String AU_PARAM_WRAPPER = AU_PARAM_RESERVED + ".wrapper";
  public static final String AU_PARAM_DISABLED = AU_PARAM_RESERVED + ".disabled";
  public static final String AU_PARAM_REPOSITORY = AU_PARAM_RESERVED + ".repository";
  public static final String AU_PARAM_DISPLAY_NAME = AU_PARAM_RESERVED + ".displayName";

  public static final List NON_USER_SETTABLE_AU_PARAMS =
    Collections.unmodifiableList(ListUtil.list(AU_PARAM_WRAPPER));

  static final String CONFIGURABLE_PLUGIN_NAME =
    DefinablePlugin.class.getName();

  private static Logger log = Logger.getLogger("PluginMgr");

  private ConfigManager configMgr;
  private StatusService statusSvc;

  private File pluginDir = null;
  private AuOrderComparator auComparator = new AuOrderComparator();

  // maps plugin key(not id) to plugin
  private Map pluginMap = Collections.synchronizedMap(new HashMap());
  private Map auMap = Collections.synchronizedMap(new HashMap());
  private Map classloaderMap = Collections.synchronizedMap(new HashMap());
  private Set auSet = Collections.synchronizedSet(new TreeSet(auComparator));
  private Set inactiveAuIds = Collections.synchronizedSet(new HashSet());
  private List xmlPlugins = Collections.EMPTY_LIST;

  // List of loadable XML plugins.
  private List loadableXmlPlugins = Collections.synchronizedList(new ArrayList());

  // List of loadable plugin JAR CachedUrls.
  private List pluginCus = Collections.synchronizedList(new ArrayList());

  private KeyStore keystore;
  private JarValidator jarValidator;
  private boolean keystoreInited = false;

  public PluginManager() {
  }

  /* ------- LockssManager implementation ------------------ */
  /**
   * start the plugin manager.
   * @see org.lockss.app.LockssManager#startService()
   */
  public void startService() {
    super.startService();
    configMgr = getDaemon().getConfigManager();
    statusSvc = getDaemon().getStatusService();
    // Initialize the plugin directory.
    initPluginDir();
    //     statusSvc.registerStatusAccessor("AUS", new Status());
    resetConfig();   // causes setConfig to think previous config was empty
  }

  /**
   * stop the plugin manager
   * @see org.lockss.app.LockssManager#stopService()
   */
  public void stopService() {
    // tk - checkpoint if nec.
    for (Iterator iter = pluginMap.values().iterator(); iter.hasNext(); ) {
      Plugin plugin = (Plugin)iter.next();
      plugin.stopPlugin();
    }
    super.stopService();
  }

  Configuration currentAllPlugs = ConfigManager.EMPTY_CONFIGURATION;

  protected void setConfig(Configuration config, Configuration oldConfig,
			   Set changedKeys) {

    // Must load the xml plugin list *before* the plugin registry
    if (changedKeys.contains(PARAM_PLUGIN_XML_PLUGINS)) {
      xmlPlugins = config.getList(PARAM_PLUGIN_XML_PLUGINS);
    }

    // Don't load or start other plugins until the daemon is running.
    if (isDaemonInited()) {

      // Process the plugin registry.
      if (changedKeys.contains(PARAM_PLUGIN_REGISTRY)) {
	initPluginRegistry(config.getList(PARAM_PLUGIN_REGISTRY));
      }

      // Process loadable plugin registries.
      if (changedKeys.contains(PARAM_PLUGIN_REGISTRIES)) {
	initLoadablePluginRegistries(config.getList(PARAM_PLUGIN_REGISTRIES));
      }

      Configuration allPlugs = config.getConfigTree(PARAM_AU_TREE);
      if (!allPlugs.equals(currentAllPlugs)) {
	for (Iterator iter = allPlugs.nodeIterator(); iter.hasNext(); ) {
	  String pluginKey = (String)iter.next();
	  log.debug("Configuring plugin key: " + pluginKey);
	  Configuration pluginConf = allPlugs.getConfigTree(pluginKey);
	  Configuration prevPluginConf =
	    currentAllPlugs.getConfigTree(pluginKey);

	  configurePlugin(pluginKey, pluginConf, prevPluginConf);
	}
	currentAllPlugs = allPlugs;
      }
    }
  }


  /**
   * Convert plugin property key to plugin class name.
   * @param key the key
   * @return the plugin name
   */
  public static String pluginNameFromKey(String key) {
    return StringUtil.replaceString(key, "|", ".");
  }

  /**
   * Convert plugin class name to key suitable for property file.
   * @param className the class name
   * @return the plugin key
   */
  public static String pluginKeyFromName(String className) {
    return StringUtil.replaceString(className, ".", "|");
  }

  /**
   * Convert plugin id to key suitable for property file.  Plugin id is
   * currently the same as plugin class name, but that may change.
   * @param id the plugin id
   * @return String the plugin key
   */
  public static String pluginKeyFromId(String id) {
    // tk - needs to do real mapping from IDs obtained from all available
    // plugins.
    return StringUtil.replaceString(id, ".", "|");
  }

  /**
   * Return a unique identifier for an au based on its plugin id and defining
   * properties.
   * @param pluginId plugin id (with . not escaped)
   * @param auDefProps Properties with values for all definitional AU params
   * @return a unique identifier for an au based on its plugin id and defining
   * properties.
   */
  public static String generateAuId(String pluginId, Properties auDefProps) {
    return generateAuId(pluginId,
			PropUtil.propsToCanonicalEncodedString(auDefProps));
  }

  public static String generateAuId(Plugin plugin, Configuration auConfig) {
    Properties props = new Properties();
    for (Iterator iter = plugin.getAuConfigDescrs().iterator();
	 iter.hasNext();) {
      ConfigParamDescr descr = (ConfigParamDescr)iter.next();
      if (descr.isDefinitional()) {
	String key = descr.getKey();
	props.setProperty(key, auConfig.get(key));
      }
    }
    return generateAuId(plugin.getPluginId(), props);
  }

  public static String generateAuId(String pluginId, String auKey) {
    return pluginKeyFromId(pluginId)+"&"+auKey;
  }

  public static String auKeyFromAuId(String auid) {
    int pos = auid.indexOf("&");
    if (pos < 0) {
      throw new IllegalArgumentException("Illegal AuId: " + auid);
    }
    return auid.substring(pos + 1);
  }

  public static String pluginIdFromAuId(String auid) {
    int pos = auid.indexOf("&");
    if (pos < 0) {
      throw new IllegalArgumentException("Illegal AuId: " + auid);
    }
    return auid.substring(0, pos);
  }

  public static String pluginNameFromAuId(String auid) {
    return pluginNameFromKey(pluginIdFromAuId(auid));
  }

  public static String configKeyFromAuId(String auid) {
    return StringUtil.replaceFirst(auid, "&", ".");
  }

  /**
   * Return the plugin with the given key.
   * @param pluginKey the plugin key
   * @return the plugin or null
   */
  public Plugin getPlugin(String pluginKey) {
    return (Plugin)pluginMap.get(pluginKey);
  }

  /**
   * Returns true if the reserved.wrapper key is true
   * @param auConf the Configuration
   * @return true if wrapped
   */
  private boolean isAuWrapped(Configuration auConf) {
    return WrapperState.isUsingWrapping() &&
      auConf.getBoolean(AU_PARAM_WRAPPER, false);
  }

  private Configuration removeWrapper(Configuration auConf) {
    Configuration copy = auConf.copy();
    if (copy.containsKey(AU_PARAM_WRAPPER)) {
      copy.remove(AU_PARAM_WRAPPER);
    }
    return copy;
  }

  private void configurePlugin(String pluginKey, Configuration pluginConf,
			       Configuration oldPluginConf) {
    for (Iterator iter = pluginConf.nodeIterator(); iter.hasNext(); ) {
      String auKey = (String)iter.next();
      String auId = generateAuId(pluginKey, auKey);
      try {
	Configuration auConf = pluginConf.getConfigTree(auKey);
	Configuration oldAuConf = oldPluginConf.getConfigTree(auKey);
	if (auConf.getBoolean(AU_PARAM_DISABLED, false)) {
	  // tk should actually remove AU?
	  log.debug("Not configuring disabled AU id: " + auKey);
	  if (auMap.get(auId) == null) {
	    // don't add to inactive if it's still running
	    inactiveAuIds.add(auId);
	  }
	} else if (!auConf.equals(oldAuConf)) {
	  log.debug("Configuring AU id: " + auKey);
	  if (!isAuWrapped(auConf)) {
	    boolean pluginOk = ensurePluginLoaded(pluginKey);
	    if (pluginOk) {
	      Plugin plugin = getPlugin(pluginKey);
	      if (WrapperState.isWrappedPlugin(plugin)) {
		throw new ArchivalUnit.ConfigurationException("An attempt was made to load unwrapped AU " + auKey + " from plugin " + pluginKey + " which is already wrapped.");
	      }
	      configureAu(plugin, auConf, auId);
	    } else {
	      log.warning("Not configuring AU " + auKey);
	    }
	  } else {
	    log.debug("Wrapping " + auKey);
	    if ((pluginMap.containsKey(pluginKey)) &&
		(!WrapperState.isWrappedPlugin(pluginMap.get(pluginKey)))) {
	      throw new ArchivalUnit.ConfigurationException("An attempt was made to wrap AU " + auKey + " from plugin " + pluginKey + " which is already loaded.");
	    }
	    Plugin wrappedPlugin =
	      WrapperState.retrieveWrappedPlugin(pluginKey, theDaemon);
	    if (wrappedPlugin==null) {
	      log.warning("Not configuring AU " + auKey);
	      log.error("Error instantiating " +
			WrapperState.WRAPPED_PLUGIN_NAME);
	    } else {
	      setPlugin(pluginKey,wrappedPlugin);
	      Configuration wrappedAuConf = removeWrapper(auConf);
	      configureAu(wrappedPlugin, wrappedAuConf, auId);
	    }
	  }
	  inactiveAuIds.remove(generateAuId(pluginKey, auKey));
	} else {
	  log.debug("AU already configured, not reconfiguring: " + auKey);
	}
      } catch (ArchivalUnit.ConfigurationException e) {
	log.error("Failed to configure AU " + auKey, e);
      } catch (Exception e) {
	log.error("Unexpected exception configuring AU " + auKey, e);
      }
    }
  }

  void configureAu(Plugin plugin, Configuration auConf, String auId)
      throws ArchivalUnit.ConfigurationException {
    try {
      ArchivalUnit au = plugin.configureAu(auConf,
					   (ArchivalUnit)auMap.get(auId));
      log.debug("Configured AU " + au);
      try {
	getDaemon().startOrReconfigureAuManagers(au, auConf);
      } catch (Exception e) {
	throw new
	  ArchivalUnit.ConfigurationException("Couldn't configure AU managers",
					      e);
      }
      log.debug("putAuMap(" + au.getAuId() +", " + au);
      if (!auId.equals(au.getAuId())) {
	throw new ArchivalUnit.ConfigurationException("Configured AU has "
						      +"unexpected AUId, "
						      +"is: "+au.getAuId()
						      +" expected: "+auId);
      }
      putAuInMap(au);
    } catch (ArchivalUnit.ConfigurationException e) {
      throw e;
    } catch (Exception e) {
      log.error("Error configuring AU", e);
      throw new
	ArchivalUnit.ConfigurationException("Unexpected error creating AU", e);
    }
  }

  ArchivalUnit createAu(Plugin plugin, Configuration auConf)
      throws ArchivalUnit.ConfigurationException {
    String auid = null;
    ArchivalUnit oldAu = null;
    try {
      auid = generateAuId(plugin, auConf);
      oldAu = getAuFromId(auid);
    } catch (Exception e) {
      // no action.  Bad/missing config value might cause getAuFromId() to
      // throw.  It will be caught soon when creating the AU; for now we
      // can assume it means the AU doesn't already exist.
    }
    if (oldAu != null) {
      inactiveAuIds.remove(oldAu.getAuId());
      throw new ArchivalUnit.ConfigurationException("Cannot create that AU because it already exists");
    }
    try {
      ArchivalUnit au = plugin.createAu(auConf);
      inactiveAuIds.remove(au.getAuId());
      log.debug("Created AU " + au);
      try {
	getDaemon().startOrReconfigureAuManagers(au, auConf);
      } catch (Exception e) {
	log.error("Couldn't start AU processes", e);
	throw new
	  ArchivalUnit.ConfigurationException("Couldn't start AU processes",
					      e);
      }
      log.debug("putAuMap(" + au.getAuId() +", " + au);
      putAuInMap(au);
      return au;
    } catch (ArchivalUnit.ConfigurationException e) {
      throw e;
    } catch (Exception e) {
      log.error("Error creating AU", e);
      throw new
	ArchivalUnit.ConfigurationException("Unexpected error creating AU", e);
    }
  }

  /** Stop the AU's activity and remove it, as though it had never been
   * configured.  Does not affect the AU's current repository contents.
   * @return true if the AU was removed, false if it didn't exist or is
   * stale. */
  public boolean stopAu(ArchivalUnit au) {
    String auid = au.getAuId();
    ArchivalUnit mapAu = (ArchivalUnit)auMap.get(auid);
    if (mapAu == null) {
      log.warning("stopAu(" + au.getName() + "), wasn't in map");
      return false;
    }
    if (mapAu != au) {
      log.warning("stopAu(" + au.getName() + "), but map contains " + mapAu);
      return false;
    }
    log.debug("Deactivating AU: " + au.getName());
    // remove from map first, so no new activity can start (poll messages,
    // RemoteAPI, etc.)
    auMap.remove(auid);
    auSet.remove(au);

    theDaemon.getPollManager().cancelAuPolls(au);
    theDaemon.getCrawlManager().cancelAuCrawls(au);

    try {
      //     Plugin plugin = au.getPlugin();
      //     plugin.removeAu(au);
      theDaemon.stopAuManagers(au);
    } catch (Exception e) {
      // Shouldn't happen, as stopAuManagers() catches errors in
      // stopService().  Not clear what to do anyway, if some of the
      // managers don't stop cleanly.
    }
    return true;
  }

  protected void putAuInMap(ArchivalUnit au) {
    auMap.put(au.getAuId(), au);
    auSet.add(au);
  }

  public ArchivalUnit getAuFromId(String auId) {
    ArchivalUnit au = (ArchivalUnit)auMap.get(auId);
    log.debug3("getAu(" + auId + ") = " + au);
    return au;
  }

  // These don't belong here
  /**
   * Reconfigure an AU and save the new configuration in the local config
   * file.  Need to find a better place for this.
   * @param au the AU
   * @param auProps the new AU configuration, using simple prop keys (not
   * prefixed with org.lockss.au.<i>auid</i>)
   * @throws ArchivalUnit.ConfigurationException
   * @throws IOException
   */
  public void setAndSaveAuConfiguration(ArchivalUnit au,
					Properties auProps)
      throws ArchivalUnit.ConfigurationException, IOException {
    setAndSaveAuConfiguration(au, ConfigManager.fromProperties(auProps));
  }

  /**
   * Reconfigure an AU and save the new configuration in the local config
   * file.  Need to find a better place for this.
   * @param au the AU
   * @param auConf the new AU configuration, using simple prop keys (not
   * prefixed with org.lockss.au.<i>auid</i>)
   * @throws ArchivalUnit.ConfigurationException
   * @throws IOException
   */
  public void setAndSaveAuConfiguration(ArchivalUnit au,
					Configuration auConf)
      throws ArchivalUnit.ConfigurationException, IOException {
    log.debug("Reconfiguring AU " + au);
    au.setConfiguration(auConf);
    updateAuConfigFile(au, auConf);
  }

  private void updateAuConfigFile(ArchivalUnit au, Configuration auConf)
      throws ArchivalUnit.ConfigurationException, IOException {
    updateAuConfigFile(au.getAuId(), auConf);
  }

  public void updateAuConfigFile(String auid, Configuration auConf)
      throws ArchivalUnit.ConfigurationException, IOException {
    String prefix = PARAM_AU_TREE + "." + configKeyFromAuId(auid);
    Configuration fqConfig = auConf.addPrefix(prefix);
    configMgr.updateAuConfigFile(fqConfig, prefix);
  }


  /**
   * Create an AU and save its configuration in the local config
   * file.  Need to find a better place for this.
   * @param plugin the Plugin in which to create the AU
   * @param auProps the new AU configuration, using simple prop keys (not
   * prefixed with org.lockss.au.<i>auid</i>)
   * @return the new AU
   * @throws ArchivalUnit.ConfigurationException
   * @throws IOException
   */
  public ArchivalUnit createAndSaveAuConfiguration(Plugin plugin,
						   Properties auProps)
      throws ArchivalUnit.ConfigurationException, IOException {
    return createAndSaveAuConfiguration(plugin,
					ConfigManager.fromProperties(auProps));
  }

  /**
   * Create an AU and save its configuration in the local config
   * file.  Need to find a better place for this.
   * @param plugin the Plugin in which to create the AU
   * @param auConf the new AU configuration, using simple prop keys (not
   * prefixed with org.lockss.au.<i>auid</i>)
   * @return the new AU
   * @throws ArchivalUnit.ConfigurationException
   * @throws IOException
   */
  public ArchivalUnit createAndSaveAuConfiguration(Plugin plugin,
						   Configuration auConf)
      throws ArchivalUnit.ConfigurationException, IOException {
    auConf.put(AU_PARAM_DISABLED, "false");
    ArchivalUnit au = createAu(plugin, auConf);
    updateAuConfigFile(au, auConf);
    return au;
  }

  /**
   * Delete AU configuration from the local config file.  Need to find a
   * better place for this.
   * @param au the ArchivalUnit to be unconfigured
   * @throws ArchivalUnit.ConfigurationException
   * @throws IOException
   */
  public void deleteAuConfiguration(ArchivalUnit au)
      throws ArchivalUnit.ConfigurationException, IOException {
    log.debug("Deleting AU config: " + au);
    updateAuConfigFile(au, ConfigManager.EMPTY_CONFIGURATION);
  }

  /**
   * Delete AU configuration from the local config file.  Need to find a
   * better place for this.
   * @param auid the AuId
   * @throws ArchivalUnit.ConfigurationException
   * @throws IOException
   */
  public void deleteAuConfiguration(String auid)
      throws ArchivalUnit.ConfigurationException, IOException {
    log.debug("Deleting AU config: " + auid);
    updateAuConfigFile(auid, ConfigManager.EMPTY_CONFIGURATION);
    // might be deleting an inactive au
    inactiveAuIds.remove(auid);
  }

  /**
   * Deactivate an AU in the config file.  Does not actually stop the AU.
   * @param au the ArchivalUnit to be deactivated
   * @throws ArchivalUnit.ConfigurationException
   * @throws IOException
   */
  public void deactivateAuConfiguration(ArchivalUnit au)
      throws ArchivalUnit.ConfigurationException, IOException {
    log.debug("Deactivating AU: " + au);
    Configuration config = getStoredAuConfiguration(au);
    config.put(AU_PARAM_DISABLED, "true");
    config.put(AU_PARAM_DISPLAY_NAME, au.getName());
    updateAuConfigFile(au, config);
  }

  /**
   * Delete an AU
   * @param au the ArchivalUnit to be deleted
   * @throws ArchivalUnit.ConfigurationException
   * @throws IOException
   */
  public void deleteAu(ArchivalUnit au)
      throws ArchivalUnit.ConfigurationException, IOException {
    deleteAuConfiguration(au);
    if (isRemoveStoppedAus()) {
      stopAu(au);
    }
  }

  /**
   * Deactivate an AU
   * @param au the ArchivalUnit to be deactivated
   * @throws ArchivalUnit.ConfigurationException
   * @throws IOException
   */
  public void deactivateAu(ArchivalUnit au)
      throws ArchivalUnit.ConfigurationException, IOException {
    deactivateAuConfiguration(au);
    if (isRemoveStoppedAus()) {
      String auid = au.getAuId();
      stopAu(au);
      inactiveAuIds.add(auid);
    }
  }

  public boolean isRemoveStoppedAus() {
    return configMgr.getBooleanParam(PARAM_REMOVE_STOPPED_AUS,
				     DEFAULT_REMOVE_STOPPED_AUS);
  }

  /**
   * Return the stored config info for an AU (from config file, not from
   * AU instance).
   * @param au the ArchivalUnit
   * @return the AU's Configuration, with unprefixed keys.
   */
  public Configuration getStoredAuConfiguration(ArchivalUnit au) {
    return getStoredAuConfiguration(au.getAuId());
  }

  /**
   * Return the config tree for an AU id (from the local au config file,
   * not the au itself).
   * @param auid the AU's id.
   * @return the AU's Configuration, with unprefixed keys.
   */
  public Configuration getStoredAuConfiguration(String auid) {
    String aukey = configKeyFromAuId(auid);
    Configuration config = configMgr.readAuConfigFile();
    String prefix = PARAM_AU_TREE + "." + aukey;
    return config.getConfigTree(prefix);
  }

  Plugin retrievePlugin(String pluginKey, ClassLoader loader)
      throws Exception {
    if (pluginMap.containsKey(pluginKey)) {
      return (Plugin)pluginMap.get(pluginKey);
    }
    String pluginName = pluginNameFromKey(pluginKey);
    String confFile = null;
    if (xmlPlugins.contains(pluginName) ||
	loadableXmlPlugins.contains(pluginName)) {
      confFile = pluginName;
      pluginName = getConfigurablePluginName();
    }
    Class pluginClass;
    try {
      pluginClass = Class.forName(pluginName, true, loader);
    } catch (ClassNotFoundException e) {
      log.debug(pluginName + " not on classpath");
      // not on classpath
      try {
	// tk - search plugin dir for signed jar, try loading again
	throw e; // for now, simulate failure of that process
      }
      catch (ClassNotFoundException e1) {
	// plugin is really not available
	log.error(pluginName + " not found");
	return null;
      }
    }
    catch (Exception e) {
      // any other exception while loading class if not recoverable
      log.error("Error loading " + pluginName, e);
      return null;
    }
    Plugin plugin = (Plugin) pluginClass.newInstance();
    if (confFile != null && plugin instanceof DefinablePlugin) {
      log.debug("Instantiating Configurable plugin from " + confFile);
      ((DefinablePlugin)plugin).initPlugin(theDaemon, confFile, loader);
    } else {
      log.debug("Instantiating " + pluginClass);
      plugin.initPlugin(theDaemon);
    }
    return plugin;
  }


  /**
   * Load a plugin with the given class name from somewhere in our classpath
   * @param pluginKey the key for this plugin
   * @return true if loaded
   */
  public boolean ensurePluginLoaded(String pluginKey) {
    ClassLoader loader = (ClassLoader)classloaderMap.get(pluginKey);

    if (loader == null) {
      loader = this.getClass().getClassLoader();
    }

    if (pluginMap.containsKey(pluginKey)) {
      return true;
    }
    String pluginName = "";
    try {
      pluginName = pluginNameFromKey(pluginKey);
      Plugin plugin = retrievePlugin(pluginKey, loader);
      if (plugin != null) {
	setPlugin(pluginKey, plugin);
	return true;
      }
      else {
	return false;
      }
    } catch (Exception e) {
      log.error("Error instantiating " + pluginName, e);
      return false;
    }
  }

  // separate method so can be called by test code
  protected void setPlugin(String pluginKey, Plugin plugin) {
    if (log.isDebug3()) {
      log.debug3("PluginManager.setPlugin(" + pluginKey + ", " +
		 plugin.getPluginName() + ")");
    }
    pluginMap.put(pluginKey, plugin);
    resetTitles();
  }

  protected String getConfigurablePluginName() {
    return CONFIGURABLE_PLUGIN_NAME;
  }

  /**
   * Find the CachedUrlSet from a PollSpec.
   * @param spec the PollSpec (from an incoming message)
   * @return a CachedUrlSet for the plugin, au and URL in the spec, or
   * null if au not present on this cache
   */
  public CachedUrlSet findCachedUrlSet(PollSpec spec) {
    if (log.isDebug3()) log.debug3(this +".findCachedUrlSet2("+spec+")");
    String auId = spec.getAuId();
    ArchivalUnit au = getAuFromId(auId);
    if (log.isDebug3()) log.debug3("au: " + au);
    if (au == null) return null;
    Plugin plugin = au.getPlugin();
    String url = spec.getUrl();
    CachedUrlSet cus;
    if (AuUrl.isAuUrl(url)) {
      cus = au.getAuCachedUrlSet();
    } else if ((spec.getLwrBound()!=null) &&
	       (spec.getLwrBound().equals(PollSpec.SINGLE_NODE_LWRBOUND))) {
      cus = plugin.makeCachedUrlSet(au, new SingleNodeCachedUrlSetSpec(url));
    } else {
      RangeCachedUrlSetSpec rcuss =
	new RangeCachedUrlSetSpec(url, spec.getLwrBound(), spec.getUprBound());
      cus = plugin.makeCachedUrlSet(au, rcuss);
    }
    if (log.isDebug3()) log.debug3("ret cus: " + cus);
    return cus;
  }

  /**
   * Searches all ArchivalUnits to find the most recent CachedUrl for the URL.
   * @param url The URL to search for.
   * @return a CachedUrl, or null if URL not present in any AU
   */
  public CachedUrl findMostRecentCachedUrl(String url) {
    CachedUrl best = null;
    for (Iterator iter = getAllAus().iterator(); iter.hasNext();) {
      ArchivalUnit au = (ArchivalUnit)iter.next();
      Plugin plugin = au.getPlugin();
      if (au.shouldBeCached(url)) {
	CachedUrl cu = plugin.makeCachedUrl(au.getAuCachedUrlSet(), url);
	if (cu != null && cu.hasContent() && cuNewerThan(cu, best)) {
	  best = cu;
	}
      }
    }
    return best;
  }

  // return true if cu1 is newer than cu2, or cu2 is null
  // tk - no date available for comparison yet, return arbitrary order
  private boolean cuNewerThan(CachedUrl cu1, CachedUrl cu2) {
    if (cu2 == null) return true;
    CIProperties p1 = cu1.getProperties();
    CIProperties p2 = cu2.getProperties();
    // tk - this should use the crawl-date prop taht the crawler will add
    //     Long.parseLong(p1.getProperty(HttpFields.__LastModified, "-1"));
    return true;
  }

  /**
   * Return a list of all configured ArchivalUnits.
   * @return the List of aus
   */
  public List getAllAus() {
    return new ArrayList(auSet);
  }

  public Collection getInactiveAuIds() {
    return inactiveAuIds;
  }

  /** Return all the known titles from the title db, sorted by title */
  public List findAllTitles() {
    if (allTitles == null) {
      allTitles = new ArrayList(getTitleMap().keySet());
      Collections.sort(allTitles, CatalogueOrderComparator.SINGLETON);
    }
    return allTitles;
  }

  /** Find all the plugins that support the given title */
  public Collection getTitlePlugins(String title) {
    return (Collection)getTitleMap().get(title);
  }

  private Map titleMap = null;
  private List allTitles = null;

  public void resetTitles() {
    titleMap = null;
    allTitles = null;
  }

  public Map getTitleMap() {
    if (titleMap == null) {
      titleMap = buildTitleMap();
    }
    return titleMap;
  }

  Map buildTitleMap() {
    Map map = new org.apache.commons.collections.MultiHashMap();
    synchronized (pluginMap) {
      for (Iterator iter = getRegisteredPlugins().iterator();
	   iter.hasNext();) {
	Plugin p = (Plugin)iter.next();
	Collection titles = p.getSupportedTitles();
	for (Iterator iter2 = titles.iterator(); iter2.hasNext();) {
	  String title = (String)iter2.next();
	  if (title != null) {
	    map.put(title, p);
	  }
	}
      }
    }
    return map;
  }

  /** Return a SortedMap mapping (human readable) plugin name to plugin
   * instance */
  public SortedMap getPluginNameMap() {
    SortedMap pMap = new TreeMap();
    synchronized (pluginMap) {
      for (Iterator iter = getRegisteredPlugins().iterator();
	   iter.hasNext(); ) {
	Plugin p = (Plugin)iter.next();
	pMap.put(p.getPluginName(), p);
      }
    }
    return pMap;
  }

  /** @return All plugins that have been registered.  <i>Ie</i>, that are
   * either listed in org.lockss.plugin.registry, or were loaded by a
   * configured AU */
  public Collection getRegisteredPlugins() {
    return pluginMap.values();
  }

  /** @return list of xml plugin names.  For testing only. */
  List getXmlPlugins() {
    return xmlPlugins;
  }

  /**
   * Load all plugin registry plugins.
   */
  void initLoadablePluginRegistries(List urls) {
    // Load the keystore if necessary
    if (!isKeystoreInited()) {
      initKeystore();
    }

    BinarySemaphore bs = new BinarySemaphore();

    RegistryCallback regCallback = new RegistryCallback(urls, bs);

    List loadAus = new ArrayList();
    RegistryPlugin registryPlugin = new RegistryPlugin();
    String pluginKey = pluginKeyFromName("org.lockss.plugin.RegistryPlugin");
    registryPlugin.initPlugin(theDaemon);
    setPlugin(pluginKey, registryPlugin);

    for (Iterator iter = urls.iterator(); iter.hasNext(); ) {
      String url = (String)iter.next();
      Configuration auConf = ConfigManager.newConfiguration();
      auConf.put(ConfigParamDescr.BASE_URL.getKey(), url);
      auConf.put("nc_interval",
		 Configuration.getCurrentConfig().get(PARAM_REGISTRY_CRAWL_INTERVAL,
						      DEFAULT_REGISTRY_CRAWL_INTERVAL));

      String auId = generateAuId(registryPlugin, auConf);
      String auKey = auKeyFromAuId(auId);
      log.debug2("Setting Registry AU " + auKey + " recrawl interval to " +
		 auConf.get("nc_interval"));

      // Only process this registry if it is new.
      if (!auMap.containsKey(auId)) {

	try {
	  configureAu(registryPlugin, auConf, auId);
	} catch (ArchivalUnit.ConfigurationException ex) {
	  log.error("Failed to configure AU " + auKey, ex);
	  regCallback.processPluginsIfReady(url);
	  continue;
	}

	ArchivalUnit registryAu = getAuFromId(auId);

	loadAus.add(registryAu);

	if (registryAu.
	    shouldCrawlForNewContent(theDaemon.getNodeManager(registryAu).getAuState())) {
	  // Trigger a new content crawl.  The callback will handle loading
	  // all the loadable plugins when it is ready.
	  log.debug("Starting a new crawl of AU: " + registryAu.getName());
	  theDaemon.getCrawlManager().startNewContentCrawl(registryAu, regCallback,
							   url, null);

	} else {
	  // If we're not going to crawl this AU, signal the callback
	  // to process its plugins if it is ready.  It will only do so if
	  // all the new content crawls it's waiting on have finished, or
	  // if there are no new content crawls are needed.
	  log.debug("Don't need to do a crawl of AU: " + registryAu.getName());
	  regCallback.processPluginsIfReady(url);
	}
      } else {
	log.debug2("We already have this AU configured, notifying callback.");
	regCallback.processPluginsIfReady(url);
      }
    }

    // Wait for the AU crawls to complete, or for the BinarySemaphore
    // to time out.

    log.debug2("Waiting for loadable plugins to finish loading...");
    try {
      // TODO: make static variable for timeout
      long timeout = Configuration.
	getCurrentConfig().getTimeIntervalParam(PARAM_PLUGIN_LOAD_TIMEOUT,
						DEFAULT_PLUGIN_LOAD_TIMEOUT);
      bs.take(Deadline.in(timeout));
    } catch (InterruptedException ex) {
      log.warning("Binary semaphore threw InterruptedException while waiting." +
		  "Remaining registry URL list: " + regCallback.getRegistryUrls());
    }

    processRegistryAus(loadAus);
  }


  // Synch the plugin registry with the plugins listed in names
  void initPluginRegistry(List nameList) {
    Collection newKeys = new HashSet();
    for (Iterator iter = nameList.iterator(); iter.hasNext(); ) {
      String name = (String)iter.next();
      String key = pluginKeyFromName(name);
      ensurePluginLoaded(key);
      newKeys.add(key);
    }
    // remove plugins that are no longer listed, unless they have one or
    // more configured AUs
    synchronized (pluginMap) {
      for (Iterator iter = pluginMap.keySet().iterator(); iter.hasNext(); ) {
	String name = (String)iter.next();
	String key = pluginKeyFromName(name);
	if (!newKeys.contains(key)) {
	  Configuration tree = currentAllPlugs.getConfigTree(key);
	  if (tree == null || tree.isEmpty()) {
	    iter.remove();
	  }
	}
      }
    }
  }

  /** Comparator for sorting Aus alphabetically by title.  This is used in a
   * TreeSet, so must return 0 only for identical objects. */
  class AuOrderComparator implements Comparator {
    CatalogueOrderComparator coc = CatalogueOrderComparator.SINGLETON;

    public int compare(Object o1, Object o2) {
      if (o1 == o2) {
	return 0;
      }
      if (!((o1 instanceof ArchivalUnit)
	    && (o2 instanceof ArchivalUnit))) {
	throw new IllegalArgumentException("AuOrderComparator(" +
					   o1.getClass().getName() + "," +
					   o2.getClass().getName() + ")");
      }
      ArchivalUnit a1 = (ArchivalUnit)o1;
      ArchivalUnit a2 = (ArchivalUnit)o2;
      int res = coc.compare(a1.getName(), a2.getName());
      if (res == 0) {
	res = coc.compare(a1.getAuId(), a2.getAuId());
      }
      if (res == 0) {
	// this can happen during testing.  Don't care about order, but
	// mustn't be equal.
	res = 1;
      }
      return res;
    }
  }

  private class Status implements StatusAccessor {
    private final List sortRules =
      ListUtil.list(new StatusTable.SortRule("au", CatalogueOrderComparator.SINGLETON));

    private final List colDescs =
      ListUtil.list(
		    new ColumnDescriptor("au", "Journal Volume",
					 ColumnDescriptor.TYPE_STRING),
		    new ColumnDescriptor("poll", "Poll Status",
					 ColumnDescriptor.TYPE_STRING),
		    new ColumnDescriptor("crawl", "Crawl Status",
					 ColumnDescriptor.TYPE_STRING),
		    new ColumnDescriptor("auid", "AUID",
					 ColumnDescriptor.TYPE_STRING)
		    );

    Status() {
    }

    public String getDisplayName() {
      return "Archival Units";
    }

    public List getColumnDescriptors(String key) {
      return colDescs;
    }

    public List getRows(String key, boolean includeInternalAus) {
      List table = new ArrayList();
      for (Iterator iter = getAllAus().iterator(); iter.hasNext();) {
	Map row = new HashMap();
	ArchivalUnit au = (ArchivalUnit)iter.next();
	if (!includeInternalAus && (au instanceof RegistryArchivalUnit)) {
	  continue;
	}
	row.put("au", au.getName());
	row.put("auid", au.getAuId());
	row.put("poll",
		statusSvc.getReference(PollerStatus.MANAGER_STATUS_TABLE_NAME,
				       au));
	table.add(row);
      }
      return table;
    }

    public List getDefaultSortRules(String key) {
      return sortRules;
    }

    public boolean requiresKey() {
      return false;
    }

    public void populateTable(StatusTable table) {
      String key = table.getKey();
      table.setColumnDescriptors(getColumnDescriptors(key));
      table.setDefaultSortRules(getDefaultSortRules(key));
      table.setRows(getRows(key, table.getOptions().get(StatusTable.OPTION_INCLUDE_INTERNAL_AUS)));
    }
  }

  /**
   * Initialize the "blessed" loadable plugin directory.
   */
  private void initPluginDir() {
    if (pluginDir != null) {
      return;
    }

    List dSpaceList =
      ConfigManager.getCurrentConfig().getList(PARAM_PLATFORM_DISK_SPACE_LIST);
    String relPluginPath =
      ConfigManager.getCurrentConfig().get(PARAM_PLUGIN_LOCATION,
					   DEFAULT_PLUGIN_LOCATION);

    if (dSpaceList == null || dSpaceList.size() == 0) {
      log.error(PARAM_PLATFORM_DISK_SPACE_LIST +
		" not specified, not configuring plugin dir");
      return;
    }

    String pluginDisk = (String)dSpaceList.get(0);

    File dir = new File(new File(pluginDisk), relPluginPath);

    if (dir.exists() && !dir.isDirectory()) {
      // This should (hopefully) never ever happen.  Log an error and
      // return for now.
      log.error("Plugin directory " + dir + " cannot be created.  A file " +
		"already exists with that name!");
      return;
    }

    if (dir.exists() && dir.isDirectory()) {
      log.info("Plugin directory " + dir + " already exists.  Cleaning up...");

      File[] dirList = dir.listFiles();
      for (int ix = 0; ix < dirList.length; ix++) {
	if (!dirList[ix].delete()) {
	  log.error("Unable to clean up plugin directory " + dir +
		    ".  Could not delete " + dirList[ix]);
	  return;
	}
      }
    } else {
      if (!dir.mkdirs()) {
	log.error("Unable to create plugin directory " + dir);
	return;
      }
    }

    pluginDir = dir;
  }


  /*
   * Helper methods for handling loadable plugins.
   */

  /**
   * Initialize the keystore.
   */
  private void initKeystore() {
    Configuration config = Configuration.getCurrentConfig();

    try {
      String keystoreLoc =
	config.get(PARAM_KEYSTORE_LOCATION,
		   DEFAULT_KEYSTORE_LOCATION);
      String keystorePass =
	config.get(PARAM_KEYSTORE_PASSWORD,
		   DEFAULT_KEYSTORE_PASSWORD);
      if (keystoreLoc == null || keystorePass == null) {
	log.error("Unable to load keystore!  Loadable plugins will " +
		  "not be available.");
      } else {

	keystore = KeyStore.getInstance("JKS", "SUN");
	if (keystoreLoc == DEFAULT_KEYSTORE_LOCATION) {
	  keystore.load(getClass().getResourceAsStream(keystoreLoc),
			keystorePass.toCharArray());
	} else {
	  keystore.load(new FileInputStream(new File(keystoreLoc)),
			keystorePass.toCharArray());
	}
      }

    } catch (Exception ex) {
      // ensure the keystore is null.
      keystore = null;
      log.error("Unable to load keystore", ex);
      return;
    }

    log.debug2("Keystore successfully initialized.");
    keystoreInited = true;
  }

  private boolean isKeystoreInited() {
    return keystoreInited;
  }

  /**
   * Given a file representing a JAR, retrieve a list of available
   * plugin classes to load.
   */
  private List getJarPluginClasses(File blessedJar) throws IOException {
    JarFile jar = new JarFile(blessedJar);
    Manifest manifest = jar.getManifest();
    Map entries = manifest.getEntries();
    List plugins = new ArrayList();

    for (Iterator manIter = entries.keySet().iterator(); manIter.hasNext();) {
      String key = (String)manIter.next();

      String pluginName = null;

      if (StringUtil.endsWithIgnoreCase(key, "plugin.class")) {
	String s = StringUtil.replaceString(key, "/", ".");
	pluginName = StringUtil.replaceString(s, ".class", "");
	plugins.add(pluginName);
      } else if (StringUtil.endsWithIgnoreCase(key, "plugin.xml")) {
	String s = StringUtil.replaceString(key, "/", ".");
	pluginName = StringUtil.replaceString(s, ".xml", "");
	loadableXmlPlugins.add(pluginName);
	plugins.add(pluginName);
      }
    }

    jar.close();

    return plugins;
  }

  /**
   * Load the plugin classes from the given blessed jar file.
   */
  private void loadLoadablePluginClasses(File blessedJar, List loadPlugins)
      throws IOException {
    URLClassLoader pluginLoader =
      new URLClassLoader(new URL[] { blessedJar.toURL() });

    String pluginName = null;
    try {
      for (Iterator pluginIter = loadPlugins.iterator();
	   pluginIter.hasNext();) {
	pluginName = (String)pluginIter.next();
	String key = pluginKeyFromName(pluginName);
	classloaderMap.put(key, pluginLoader);
	ensurePluginLoaded(key);
      }

    } catch (Exception ex) {
      log.error("Unable to load class " + pluginName, ex);
    }
  }

  /**
   * Run through the list of Registry AUs and verify and load any JARs
   * that need to be loaded.
   */
  public synchronized void processRegistryAus(List registryAus) {

    if (jarValidator == null) {
      jarValidator = new JarValidator(keystore, pluginDir);
    }

    for (Iterator iter = registryAus.iterator(); iter.hasNext(); ) {
      CachedUrlSet cus = ((ArchivalUnit)iter.next()).getAuCachedUrlSet();

      for (Iterator cusIter = cus.contentHashIterator(); cusIter.hasNext(); ) {
	CachedUrlSetNode cusn = (CachedUrlSetNode)cusIter.next();
	// TODO: Eventually this should be replaced with
	// "cusn.hasContent()", which will add another loop if it is a
	// CachedUrlSet.
	if (cusn.isLeaf()) {
	  CachedUrl cu = (CachedUrl)cusn;
	  String url = cu.getUrl();
	  if (StringUtil.endsWithIgnoreCase(url, ".jar") &&
	      !pluginCus.contains(url)) {

	    try {
	      // Validate and bless the JAR file from the CU.
	      File blessedJar = jarValidator.getBlessedJar(cu);

	      if (blessedJar != null) {
		// Get the list of plugins to load from this jar.
		List loadPlugins = getJarPluginClasses(blessedJar);

		// Although this -should- never happen, it's possible.
		if (loadPlugins.size() == 0) {
		  log.warning("Jar " + blessedJar +
			      " does not contain any plugins.  Skipping...");
		  continue; // skip this CU.
		}

		// Load the plugin classes
		loadLoadablePluginClasses(blessedJar, loadPlugins);
	      }
	    } catch (IOException ex) {
	      log.error("Error processing jar file: " + url, ex);
	    } catch (JarValidator.JarValidationException ex) {
	      log.error("CachedUrl did not validate.", ex);
	    }

	    // Add the URL to the list of CUs that we have loaded, so
	    // we don't end up trying to load it again.
	    //
	    // TODO: At a later point, we'll need to check versions on
	    // these CUs as well, and if the version has changed,
	    // load.
	    pluginCus.add(url);
	  }
	}
      }
    }
  }


  /**
   * CrawlManager callback that is responsible for handling Registry
   * AUs when they're finished with their initial crawls.
   */
  private class RegistryCallback implements CrawlManager.Callback {
    private BinarySemaphore bs;

    List registryUrls = Collections.synchronizedList(new ArrayList());

    /*
     * Set the initial size of the list of registry URLs to process.
     */
    public RegistryCallback(List registryUrls, BinarySemaphore bs) {
      synchronized(registryUrls) {
	this.registryUrls.addAll(registryUrls);
      }
      this.bs = bs;
    }

    public void signalCrawlAttemptCompleted(boolean success, Object cookie) {
      String url = (String)cookie;

      processPluginsIfReady(url);
    }

    public void processPluginsIfReady(String url) {
      // Keep decrementing registryUrls.  When it's size 0 we're done
      // running crawls on all the plugin registries, and we can load
      // the plugin classes.
      synchronized(registryUrls) {
	registryUrls.remove(url);
      }

      if (registryUrls.size() == 0) {
	bs.give();
      }
    }

    /**
     * Used only in the case that our semaphore throws an Interrupted
     * exception -- we can print this list to see what was left.
     */
    public List getRegistryUrls() {
      return registryUrls;
    }
  }
}
