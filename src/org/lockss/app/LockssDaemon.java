/*
 * $Id$
 */

/*

Copyright (c) 2000-2002 Board of Trustees of Leland Stanford Jr. University,
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
package org.lockss.app;

import java.util.*;
import org.lockss.util.*;
import org.lockss.mail.*;
import org.lockss.alert.*;
import org.lockss.daemon.*;
import org.lockss.daemon.status.*;
import org.lockss.hasher.*;
import org.lockss.scheduler.*;
import org.lockss.plugin.*;
import org.lockss.poller.*;
import org.lockss.protocol.*;
import org.lockss.repository.*;
import org.lockss.state.*;
import org.lockss.proxy.*;
import org.lockss.servlet.*;
import org.lockss.config.Configuration;
import org.lockss.crawler.*;
import org.lockss.remote.*;
import org.apache.commons.collections.map.LinkedMap;

/**
 * The LOCKSS daemon application
 */
public class LockssDaemon extends LockssApp {
  private static Logger log = Logger.getLogger("LockssDaemon");

  private static String PREFIX = Configuration.PREFIX + "daemon.";

/**
 * LOCKSS is a trademark of Stanford University.  Stanford hereby grants you
 * limited permission to use the LOCKSS trademark only in connection with
 * this software, including in the User-Agent HTTP request header generated
 * by the software and provided to web servers, provided the software or any
 * output of the software is used solely for the purpose of populating a
 * certified LOCKSS cache from a web server that has granted permission for
 * the LOCKSS system to collect material.  You may not remove or delete any
 * reference to LOCKSS in the software indicating that LOCKSS is a mark owned
 * by Stanford University.  No other permission is granted you to use the
 * LOCKSS trademark or any other trademark of Stanford University.  Without
 * limiting the foregoing, if you adapt or use the software for any other
 * purpose, you must delete all references to or uses of the LOCKSS mark from
 * the software.  All good will associated with your use of the LOCKSS mark
 * shall inure to the benefit of Stanford University.
 */
private final static String LOCKSS_USER_AGENT = "LOCKSS cache";

  static final String PARAM_DAEMON_DEADLINE_REASONABLE =
    Configuration.PREFIX + "daemon.deadline.reasonable.";
  static final String PARAM_DAEMON_DEADLINE_REASONABLE_PAST =
    PARAM_DAEMON_DEADLINE_REASONABLE + "past";
  static final long DEFAULT_DAEMON_DEADLINE_REASONABLE_PAST = Constants.SECOND;

  static final String PARAM_DAEMON_DEADLINE_REASONABLE_FUTURE =
    PARAM_DAEMON_DEADLINE_REASONABLE + "future";
  static final long DEFAULT_DAEMON_DEADLINE_REASONABLE_FUTURE =
    20 * Constants.WEEK;

  // Parameter keys for daemon managers
  public static String ACTIVITY_REGULATOR = "ActivityRegulator";
  public static String ALERT_MANAGER = "AlertManager";
  public static String HASH_SERVICE = "HashService";
  public static String TIMER_SERVICE = "TimerService";
  public static String DATAGRAM_COMM_MANAGER = "DatagramCommManager";
  public static String DATAGRAM_ROUTER_MANAGER = "DatagramRouterManager";
  public static String IDENTITY_MANAGER = "IdentityManager";
  public static String CRAWL_MANAGER = "CrawlManager";
  public static String PLUGIN_MANAGER = "PluginManager";
  public static String POLL_MANAGER = "PollManager";
  public static String REPOSITORY_MANAGER = "RepositoryManager";
  public static String LOCKSS_REPOSITORY = "LockssRepository";
  public static String HISTORY_REPOSITORY = "HistoryRepository";
  public static String NODE_MANAGER = "NodeManager";
  public static String TREEWALK_MANAGER = "TreeWalkManager";
  public static String PROXY_MANAGER = "ProxyManager";
  public static String AUDIT_PROXY_MANAGER = "AuditProxyManager";
  public static String FAIL_OVER_PROXY_MANAGER = "FailOverProxyManager";
  public static String SYSTEM_METRICS = "SystemMetrics";
  public static String REMOTE_API = "RemoteApi";
  public static String URL_MANAGER = "UrlManager";
  public static String NODE_MANAGER_MANAGER = "NodeManagerManager";
  public static String AU_TREEWALK_MANAGER = "AuTreeWalkManager";
  public static String REPOSITORY_STATUS = "RepositoryStatus";
  public static String ARCHIVAL_UNIT_STATUS = "ArchivalUnitStatus";

  // Manager descriptors.  The order of this table determines the order in
  // which managers are initialized and started.
  protected static final ManagerDesc[] managerDescs = {
    new ManagerDesc(MAIL_SERVICE, DEFAULT_MAIL_SERVICE),
    new ManagerDesc(ALERT_MANAGER, "org.lockss.alert.AlertManagerImpl"),
    new ManagerDesc(STATUS_SERVICE, DEFAULT_STATUS_SERVICE),
    new ManagerDesc(URL_MANAGER, "org.lockss.daemon.UrlManager"),
    new ManagerDesc(TIMER_SERVICE, "org.lockss.util.TimerQueue$Manager"),
    new ManagerDesc(SCHED_SERVICE, DEFAULT_SCHED_SERVICE),
    new ManagerDesc(HASH_SERVICE, "org.lockss.hasher.HashSvcQueueImpl"),
    new ManagerDesc(SYSTEM_METRICS, "org.lockss.daemon.SystemMetrics"),
    new ManagerDesc(IDENTITY_MANAGER, "org.lockss.protocol.IdentityManager"),
    new ManagerDesc(POLL_MANAGER, "org.lockss.poller.PollManager"),
    new ManagerDesc(CRAWL_MANAGER, "org.lockss.crawler.CrawlManagerImpl"),
    new ManagerDesc(REPOSITORY_MANAGER,
		    "org.lockss.repository.RepositoryManager"),
    new ManagerDesc(TREEWALK_MANAGER, "org.lockss.state.TreeWalkManager"),
    // start plugin manager after generic services
    new ManagerDesc(PLUGIN_MANAGER, "org.lockss.plugin.PluginManager"),
    // start proxy and servlets after plugin manager
    new ManagerDesc(REMOTE_API, "org.lockss.remote.RemoteApi"),
    new ManagerDesc(SERVLET_MANAGER, "org.lockss.servlet.LocalServletManager"),
    new ManagerDesc(PROXY_MANAGER, "org.lockss.proxy.ProxyManager"),
    new ManagerDesc(AUDIT_PROXY_MANAGER, "org.lockss.proxy.AuditProxyManager"),
    new ManagerDesc(FAIL_OVER_PROXY_MANAGER ,
		    "org.lockss.proxy.FailOverProxyManager"),
    // comm layer at end so don't process messages until other services ready
    new ManagerDesc(DATAGRAM_COMM_MANAGER,
		    "org.lockss.protocol.LcapDatagramComm"),
    new ManagerDesc(DATAGRAM_ROUTER_MANAGER,
		    "org.lockss.protocol.LcapDatagramRouter"),
    new ManagerDesc(WATCHDOG_SERVICE, DEFAULT_WATCHDOG_SERVICE),
    new ManagerDesc(NODE_MANAGER_MANAGER, "org.lockss.state.NodeManagerManager"),
    new ManagerDesc(ARCHIVAL_UNIT_STATUS,
		    "org.lockss.state.ArchivalUnitStatus"),
    new ManagerDesc(REPOSITORY_STATUS,
		    "org.lockss.repository.LockssRepositoryStatus"),
  };

  // AU-specific manager descriptors.  As each AU is created its managers
  // are started in this order.
  protected static final ManagerDesc[] auManagerDescs = {
    new ManagerDesc(ACTIVITY_REGULATOR,
		    "org.lockss.daemon.ActivityRegulator$Factory"),
    // LockssRepository uses ActivityRegulator
    new ManagerDesc(LOCKSS_REPOSITORY,
		    "org.lockss.repository.LockssRepositoryImpl$Factory"),
    // HistoryRepository needs no extra managers
    new ManagerDesc(HISTORY_REPOSITORY,
		    "org.lockss.state.HistoryRepositoryImpl$Factory"),
    // NodeManager uses LockssRepository, HistoryRepository, and
    // ActivityRegulator
    new ManagerDesc(NODE_MANAGER, "org.lockss.state.NodeManagerImpl$Factory"),
    // AuTreeWalkManager uses NodeManager
    new ManagerDesc(AU_TREEWALK_MANAGER,
		    "org.lockss.state.AuTreeWalkManager$Factory"),
  };

  // Maps au to sequenced map of managerKey -> manager instance
  protected static HashMap auManagerMaps = new HashMap();

  // Maps managerKey -> LockssAuManager.Factory instance
  protected HashMap auManagerFactoryMap = new HashMap();

  private static LockssDaemon theDaemon;

  protected LockssDaemon(List propUrls) {
    super(propUrls);
    theDaemon = this;
  }

  protected LockssDaemon(List propUrls, String groupName) {
    super(propUrls, groupName);
    theDaemon = this;
  }

  protected ManagerDesc[] getManagerDescs() {
    return managerDescs;
  }

  // General information accessors

  /**
   * True iff all managers have been inited.
   * @return true iff all managers have been inited */
  public boolean isDaemonInited() {
    return isAppInited();
  }

  /**
   * True if all managers have been started.
   * @return true iff all managers have been started */
  public boolean isDaemonRunning() {
    return isAppRunning();
  }

  /** Return the LOCKSS user-agent string.
   * @return the LOCKSS user-agent string. */
  public static String getUserAgent() {
    return LOCKSS_USER_AGENT;
  }


  /** Stop the daemon.  Currently only used in testing. */
  public void stopDaemon() {
    stopApp();
  }

  // LockssManager accessors

  /**
   * return the alert manager instance
   * @return the AlertManager
   * @throws IllegalArgumentException if the manager is not available.
   */
  public AlertManager getAlertManager() {
    return (AlertManager)getManager(ALERT_MANAGER);
  }

  /**
   * return the hash service instance
   * @return the HashService
   * @throws IllegalArgumentException if the manager is not available.
   */
  public HashService getHashService() {
    return (HashService) getManager(HASH_SERVICE);
  }

  /**
   * return the sched service instance
   * @return the SchedService
   * @throws IllegalArgumentException if the manager is not available.
   */
  public SchedService getSchedService() {
    return (SchedService) getManager(SCHED_SERVICE);
  }

  /**
   * return the poll manager instance
   * @return the PollManager
   * @throws IllegalArgumentException if the manager is not available.
   */
  public PollManager getPollManager() {
    return (PollManager) getManager(POLL_MANAGER);
  }

  /**
   * return the communication manager instance
   * @return the LcapDatagramComm
   * @throws IllegalArgumentException if the manager is not available.
   */
  public LcapDatagramComm getDatagramCommManager()  {
    return (LcapDatagramComm) getManager(DATAGRAM_COMM_MANAGER);
  }

  /**
   * return the communication router manager instance
   * @return the LcapDatagramRouter
   * @throws IllegalArgumentException if the manager is not available.
   */
  public LcapDatagramRouter getDatagramRouterManager()  {
    return (LcapDatagramRouter) getManager(DATAGRAM_ROUTER_MANAGER);
  }

  /**
   * return the proxy handler instance
   * @return the ProxyManager
   * @throws IllegalArgumentException if the manager is not available.
  */
  public ProxyManager getProxyManager() {
    return (ProxyManager) getManager(PROXY_MANAGER);
  }

  /**
   * return the crawl manager instance
   * @return the CrawlManager
   * @throws IllegalArgumentException if the manager is not available.
   */
  public CrawlManager getCrawlManager() {
    return (CrawlManager) getManager(CRAWL_MANAGER);
  }

  /**
   * return the treewalk manager instance
   * @return the TreeWalkManager
   * @throws IllegalArgumentException if the manager is not available.
   */
  public TreeWalkManager getTreeWalkManager()  {
    return (TreeWalkManager)getManager(TREEWALK_MANAGER);
  }

  /**
   * return the repository manager instance
   * @return the RepositoryManager
   * @throws IllegalArgumentException if the manager is not available.
   */
  public RepositoryManager getRepositoryManager()  {
    return (RepositoryManager)getManager(REPOSITORY_MANAGER);
  }

  /**
   * return the SystemMetrics instance.
   * @return SystemMetrics instance.
   * @throws IllegalArgumentException if the manager is not available.
   */
  public SystemMetrics getSystemMetrics() {
    return (SystemMetrics) getManager(SYSTEM_METRICS);
  }

  /**
   * return the plugin manager instance
   * @return the PluginManager
   * @throws IllegalArgumentException if the manager is not available.
   */
  public PluginManager getPluginManager() {
    return (PluginManager) getManager(PLUGIN_MANAGER);
  }

  /**
   * return the Identity Manager
   * @return IdentityManager
   * @throws IllegalArgumentException if the manager is not available.
   */

  public IdentityManager getIdentityManager() {
    return (IdentityManager) getManager(IDENTITY_MANAGER);
  }

  /**
   * return the RemoteApi instance.
   * @return RemoteApi instance.
   * @throws IllegalArgumentException if the manager is not available.
   */
  public RemoteApi getRemoteApi() {
    return (RemoteApi) getManager(REMOTE_API);
  }

  /**
   * return the NodeManagerManager instance.
   * @return NodeManagerManager instance.
   * @throws IllegalArgumentException if the manager is not available.
   */
  public NodeManagerManager getNodeManagerManager() {
    return (NodeManagerManager) getManager(NODE_MANAGER_MANAGER);
  }

  /**
   * return the ArchivalUnitStatus instance.
   * @return ArchivalUnitStatus instance.
   * @throws IllegalArgumentException if the manager is not available.
   */
  public ArchivalUnitStatus getArchivalUnitStatus() {
    return (ArchivalUnitStatus) getManager(ARCHIVAL_UNIT_STATUS);
  }

  // LockssAuManager accessors

  /**
   * Return an AU-specific lockss manager. This will need to be cast to the
   * appropriate class.
   * @param key the name of the manager
   * @param au the AU
   * @return a LockssAuManager
   * @throws IllegalArgumentException if the manager is not available.
   */
  public static LockssAuManager getAuManager(String key, ArchivalUnit au) {
    LockssAuManager mgr = null;
    LinkedMap auMgrMap =
      (LinkedMap)auManagerMaps.get(au);
    if (auMgrMap != null) {
      mgr = (LockssAuManager)auMgrMap.get(key);
    }
    if (mgr == null) {
      log.error(key + " not found for au: " + au);
      throw new IllegalArgumentException("Unavailable au manager:" + key);
    }
    return mgr;
  }

  /**
   * Get Lockss Repository instance
   * @param au the ArchivalUnit
   * @return the LockssRepository
   * @throws IllegalArgumentException if the manager is not available.
   */
  public LockssRepository getLockssRepository(ArchivalUnit au) {
    return (LockssRepository)getAuManager(LOCKSS_REPOSITORY, au);
  }

  /**
   * Return the NodeManager instance
   * @param au the ArchivalUnit
   * @return the NodeManager
   * @throws IllegalArgumentException if the manager is not available.
   */
  public NodeManager getNodeManager(ArchivalUnit au) {
    return (NodeManager)getAuManager(NODE_MANAGER, au);
  }

  /**
   * Return the AuTreeWalkManager instance
   * @param au the ArchivalUnit
   * @return the AuTreeWalkManager
   * @throws IllegalArgumentException if the manager is not available.
   */
  public AuTreeWalkManager getAuTreeWalkManager(ArchivalUnit au) {
    return (AuTreeWalkManager)getAuManager(AU_TREEWALK_MANAGER, au);
  }

  /**
   * Return the HistoryRepository instance
   * @param au the ArchivalUnit
   * @return the HistoryRepository
   * @throws IllegalArgumentException if the manager is not available.
   */
  public HistoryRepository getHistoryRepository(ArchivalUnit au) {
    return (HistoryRepository)getAuManager(HISTORY_REPOSITORY, au);
  }

  /**
   * Return ActivityRegulator instance
   * @param au the ArchivalUnit
   * @return the ActivityRegulator
   * @throws IllegalArgumentException if the manager is not available.
   */
  public ActivityRegulator getActivityRegulator(ArchivalUnit au) {
    return (ActivityRegulator)getAuManager(ACTIVITY_REGULATOR, au);
  }

  /**
   * Return all ActivityRegulators.
   * @return a list of all ActivityRegulators for all AUs
   */
  public List getAllActivityRegulators() {
    return getAuManagersOfType(ACTIVITY_REGULATOR);
  }

  /**
   * Return all LockssRepositories.
   * @return a list of all LockssRepositories for all AUs
   */
  public List getAllLockssRepositories() {
    return getAuManagersOfType(LOCKSS_REPOSITORY);
  }

  /**
   * Return all NodeManagers.
   * @return a list of all NodeManagers for all AUs
   */
  public List getAllNodeManagers() {
    return getAuManagersOfType(NODE_MANAGER);
  }

  // AU specific manager loading, starting, stopping

  /**
   * Start or reconfigure all managers necessary to handle the ArchivalUnit.
   * @param au the ArchivalUnit
   * @param auConfig the AU's confignuration
   */
  public void startOrReconfigureAuManagers(ArchivalUnit au,
					   Configuration auConfig)
      throws Exception {
    LinkedMap auMgrMap = (LinkedMap)auManagerMaps.get(au);
    if (auMgrMap != null) {
      // If au has a map it's been created, just set new config
      configAuManagers(au, auConfig, auMgrMap);
    } else {
      // create a new map, init, configure and start managers
      auMgrMap = new LinkedMap();
      initAuManagers(au, auMgrMap);
      // Store map once all managers inited
      auManagerMaps.put(au, auMgrMap);
      configAuManagers(au, auConfig, auMgrMap);
      try {
	startAuManagers(au, auMgrMap);
      } catch (Exception e) {
	log.warning("Stopping managers for " + au);
	stopAuManagers(au);
	throw e;
      }
    }
  }

  /** Stop the managers for the AU in the reverse order in which they
   * appear in the map */
  public void stopAuManagers(ArchivalUnit au) {
    LinkedMap auMgrMap =
      (LinkedMap)auManagerMaps.get(au);
    List rkeys = ListUtil.reverseCopy(auMgrMap.asList());
    for (Iterator iter = rkeys.iterator(); iter.hasNext(); ) {
      String key = (String)iter.next();
      LockssAuManager mgr = (LockssAuManager)auMgrMap.get(key);
      try {
	mgr.stopService();
      } catch (Exception e) {
	log.warning("Couldn't stop au manager " + mgr, e);
	// continue to try to stop other managers
      }
    }
    auManagerMaps.remove(au);
  }

  /** Create and init all AU managers for the AU, and associate them with
   * their keys in auMgrMap. */
  private void initAuManagers(ArchivalUnit au, LinkedMap auMgrMap)
      throws Exception {
    ManagerDesc descs[] = getAuManagerDescs();
    for (int ix = 0; ix < descs.length; ix++) {
      ManagerDesc desc = descs[ix];
      try {
	LockssAuManager mgr = initAuManager(desc, au);
	auMgrMap.put(desc.key, mgr);
      } catch (Exception e) {
	log.error("Couldn't init AU manager " + desc.key + " for " + au,
		  e);
	// don't try to init remaining managers
	throw e;
      }
    }
  }

  protected ManagerDesc[] getAuManagerDescs() {
    return auManagerDescs;
  }

  /** Create and init an AU manager. */
  protected LockssAuManager initAuManager(ManagerDesc desc, ArchivalUnit au)
      throws Exception {
    LockssAuManager mgr = instantiateAuManager(desc, au);
    mgr.initService(this);
    return mgr;
  }

  /** Start the managers for the AU in the order in which they appear in
   * the map.  protected so MockLockssDaemon can override to suppress
   * startService() */
  protected void startAuManagers(ArchivalUnit au, LinkedMap auMgrMap)
      throws Exception {
    for (Iterator iter = auMgrMap.keySet().iterator(); iter.hasNext(); ) {
      String key = (String)iter.next();
      LockssAuManager mgr = (LockssAuManager)auMgrMap.get(key);
      try {
	mgr.startService();
      } catch (Exception e) {
	log.error("Couldn't start AU manager " + mgr + " for " + au,
		  e);
	// don't try to start remaining managers
	throw e;
      }
    }
  }

  /** (re)configure the au managers */
  private void configAuManagers(ArchivalUnit au, Configuration auConfig,
				LinkedMap auMgrMap) {
    for (Iterator iter = auMgrMap.keySet().iterator(); iter.hasNext(); ) {
      String key = (String)iter.next();
      LockssAuManager mgr = (LockssAuManager)auMgrMap.get(key);
      try {
	mgr.setAuConfig(auConfig);
      } catch (Exception e) {
	log.error("Error configuring AU manager " + mgr + " for " + au, e);
	// continue after config errors
      }
    }
  }

  /** Instantiate a LockssAuManager, using a LockssAuManager.Factory, which
   * is created is necessary */
  private LockssAuManager instantiateAuManager(ManagerDesc desc,
					       ArchivalUnit au)
      throws Exception {
    String key = desc.key;
    LockssAuManager.Factory factory =
      (LockssAuManager.Factory)auManagerFactoryMap.get(key);
    if (factory == null) {
      factory = instantiateAuFactory(desc);
      auManagerFactoryMap.put(key, factory);
    }
    LockssAuManager mgr = factory.createAuManager(au);
    return mgr;
  }

  /** Instantiate a LockssAuManager.Factory, which is used to create
   * instances of a LockssAuManager */
  private LockssAuManager.Factory instantiateAuFactory(ManagerDesc desc)
      throws Exception {
    String managerName = Configuration.getParam(MANAGER_PREFIX + desc.key,
						desc.defaultClass);
    LockssAuManager.Factory factory;
    try {
      factory = (LockssAuManager.Factory)makeInstance(managerName);
    } catch (ClassNotFoundException e) {
      log.warning("Couldn't load au manager factory class " + managerName);
      if (!managerName.equals(desc.defaultClass)) {
	log.warning("Trying default factory class " + desc.defaultClass);
	factory = (LockssAuManager.Factory)makeInstance(desc.defaultClass);
      } else {
	throw e;
      }
    }
    return factory;
  }

  /**
   * Calls 'stopService()' on all AU managers for all AUs,
   */
  public void stopAllAuManagers() {
    for (Iterator iter = auManagerMaps.keySet().iterator();
	 iter.hasNext(); ) {
      ArchivalUnit au = (ArchivalUnit)iter.next();
      log.debug2("Stopping all managers for " + au);
      stopAuManagers(au);
    }
    auManagerMaps.clear();
  }

  /**
   * Return the LockssAuManagers of a particular type.
   * @param managerKey the manager type
   * @return a list of LockssAuManagers
   */
  List getAuManagersOfType(String managerKey) {
    List res = new ArrayList(auManagerMaps.size());
    for (Iterator iter = auManagerMaps.values().iterator();
	 iter.hasNext(); ) {
      LinkedMap auMgrMap = (LinkedMap)iter.next();
      Object auMgr = auMgrMap.get(managerKey);
      if (auMgr != null) {
	res.add(auMgr);
      }
    }
    return res;
  }

  // Daemon start, stop

  protected void startDaemon() throws Exception {
    startApp();

    // Install loadable plugin support
    getPluginManager().startLoadablePlugins();

    log.info("Started");
  }


  /**
   * Stop the daemon, by stopping the managers in the reverse order of
   * starting.
   */
  protected void stop() {
    appRunning = false;

    // stop all au-specific managers
    stopAllAuManagers();

    super.stop();
  }

  protected void setConfig(Configuration config, Configuration prevConfig,
			   Configuration.Differences changedKeys) {

    if (changedKeys.contains(PARAM_DAEMON_DEADLINE_REASONABLE)) {
      long maxInPast =
	config.getTimeInterval(PARAM_DAEMON_DEADLINE_REASONABLE_PAST,
			       DEFAULT_DAEMON_DEADLINE_REASONABLE_PAST);
      long maxInFuture =
	config.getTimeInterval(PARAM_DAEMON_DEADLINE_REASONABLE_FUTURE,
			       DEFAULT_DAEMON_DEADLINE_REASONABLE_FUTURE);
      Deadline.setReasonableDeadlineRange(maxInPast, maxInFuture);
    }
    super.setConfig(config, prevConfig, changedKeys);
  }

  /**
   * Parse and handle command line arguments.
   */
  protected static StartupOptions getStartupOptions(String[] args) {
    List propUrls = new ArrayList();
    String groupName = null;

    // True if named command line arguments are being passed to
    // the daemon at startup.  Otherwise, just treat the command
    // line arguments as if they were a list of URLs, for backward
    // compatibility and testing.
    boolean useNewSyntax = false;

    List tmpList = new ArrayList();
    for (int i = 0; i < args.length - 1; i++) {
      if (args[i].equals(StartupOptions.OPTION_GROUP)) {
	groupName = args[++i];
	useNewSyntax = true;
      }
      else if (args[i].equals(StartupOptions.OPTION_PROPURL)) {
	// TODO: If not available, keep selecting prop files to load
	// until one is loaded, or the list is exhausted.
	// For now, just select one at random.
	Vector v = StringUtil.breakAt(args[++i], ';', -1, true, true);
	int idx = (int)(Math.random() * v.size());
	propUrls.add(v.get(idx));
	useNewSyntax = true;
      }
    }

    if (!useNewSyntax) {
      propUrls = ListUtil.fromArray(args);
    }

    return new StartupOptions(propUrls, groupName);
  }

  /**
   * Main entry to the daemon.  Startup arguments:
   *
   * -p url1
   *     Load properties from url1
   * -p url1 -p url2;url3;url4
   *     Load properties from url1 AND from one of
   *     (url2 | url3 | url4)
   * -g group_name
   *     Set the daemon group to 'group_name'
   */
  public static void main(String[] args) {
    LockssDaemon daemon;
    StartupOptions opts = getStartupOptions(args);

    try {
      daemon = new LockssDaemon(opts.getPropUrls(),
				opts.getGroupName());
      daemon.startDaemon();
      // raise priority after starting other threads, so we won't get
      // locked out and fail to exit when told.
      Thread.currentThread().setPriority(Thread.NORM_PRIORITY + 2);

    } catch (Throwable e) {
      log.error("Exception thrown in main loop", e);
      System.exit(1);
      return;				// compiler doesn't know that
					// System.exit() doesn't return
    }
    if (Configuration.getBooleanParam(PARAM_APP_EXIT_IMM,
				      DEFAULT_APP_EXIT_IMM)) {
      daemon.stop();
      System.exit(0);
    }
    daemon.keepRunning();
    log.info("Exiting because time to die");
    System.exit(0);
  }

  /**
   * Command line startup options container.
   * Currently supports propUrl (-p) and daemon group (-g)
   * parameters.
   */
  static class StartupOptions {

    public static final String OPTION_PROPURL = "-p";
    public static final String OPTION_GROUP = "-g";

    private String groupName;
    private List propUrls;

    public StartupOptions(List propUrls, String groupName) {
      this.propUrls = propUrls;
      this.groupName = groupName;
    }

    public List getPropUrls() {
      return propUrls;
    }

    public String getGroupName() {
      return groupName;
    }
  }
}
