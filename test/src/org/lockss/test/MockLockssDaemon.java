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

package org.lockss.test;

import java.util.*;
import org.lockss.util.*;
import org.lockss.mail.*;
import org.lockss.alert.*;
import org.lockss.hasher.*;
import org.lockss.scheduler.*;
import org.lockss.protocol.*;
import org.lockss.poller.*;
import org.lockss.state.*;
import org.lockss.repository.*;
import org.lockss.proxy.*;
import org.lockss.config.Configuration;
import org.lockss.crawler.*;
import org.lockss.plugin.*;
import org.lockss.app.*;
import org.lockss.daemon.*;
import org.lockss.daemon.status.*;
import org.lockss.remote.*;
import org.apache.commons.collections.map.LinkedMap;

public class MockLockssDaemon extends LockssDaemon {
  private static Logger log = Logger.getLogger("MockLockssDaemon");

  WatchdogService wdogService = null;
  MailService mailService = null;
  AlertManager alertManager = null;
  HashService hashService = null;
  SchedService schedService = null;
  SystemMetrics systemMetrics = null;
  PollManager pollManager = null;
  LcapDatagramComm commManager = null;
  LcapDatagramRouter routerManager = null;
  ProxyManager proxyManager = null;
  CrawlManager crawlManager = null;
  RepositoryManager repositoryManager = null;
  TreeWalkManager treeWalkManager = null;
  NodeManagerManager nodeManagerManager = null;
  PluginManager pluginManager = null;
  IdentityManager identityManager = null;
  StatusService statusService = null;
  RemoteApi remoteApi = null;

  public MockLockssDaemon() {
    this(null);
  }

  public MockLockssDaemon(List urls) {
    super(urls);
  }

  public void startDaemon() throws Exception {
  }

  public void stopDaemon() {
    auManagerMaps.clear();

    wdogService = null;
    hashService = null;
    schedService = null;
    pollManager = null;
    commManager = null;
    proxyManager = null;
    crawlManager = null;
    treeWalkManager = null;
    pluginManager = null;
    identityManager = null;
    statusService = null;

    //super.stopDaemon();
  }

  ManagerDesc findManagerDesc(String key) {
    return findDesc(managerDescs, key);
  }

  ManagerDesc findAuManagerDesc(String key) {
    return findDesc(getAuManagerDescs(), key);
  }

  ManagerDesc findDesc(ManagerDesc[] descs, String key) {
    for(int i=0; i< descs.length; i++) {
      ManagerDesc desc = descs[i];
      if (key.equals(desc.getKey())) {
	return desc;
      }
    }
    return null;
  }

  /** Create a manager instance, mimicking what LockssDaemon does */
  LockssManager newManager(String key) {
    log.debug2("Loading manager: " + key);
    ManagerDesc desc = findManagerDesc(key);
    if (desc == null) {
      throw new LockssAppException("No ManagerDesc for: " + key);
    }
    if (log.isDebug2()) {
      String mgr_name = Configuration.getParam(MANAGER_PREFIX + desc.getKey(),
					       desc.getDefaultClass());
      log.debug2("Manager class: " + mgr_name);
    }
    try {
      return initManager(desc);
    } catch (Exception e) {
      throw new LockssAppException("Can't load manager: " + e.toString());
    }
  }

  /**
   * return the watchdog service instance
   * @return the WatchdogService
   */
  public WatchdogService getWatchdogService() {
    if (wdogService == null) {
      wdogService = (WatchdogService)newManager(LockssDaemon.WATCHDOG_SERVICE);
      managerMap.put(LockssDaemon.WATCHDOG_SERVICE, wdogService);
    }
    return wdogService;
  }

  /**
   * return the mail manager instance
   * @return the MailService
   */
  public MailService getMailService() {
    if (mailService == null) {
      mailService = new NullMailService();
      managerMap.put(LockssDaemon.MAIL_SERVICE, mailService);
    }
    return mailService;
  }

  /**
   * return the alert manager instance
   * @return the AlertManager
   */
  public AlertManager getAlertManager() {
    if (alertManager == null) {
      alertManager = new NullAlertManager();
      managerMap.put(LockssDaemon.ALERT_MANAGER, alertManager);
    }
    return alertManager;
  }

  /**
   * return the hash service instance
   * @return the HashService
   */
  public HashService getHashService() {
    if (hashService == null) {
      hashService = (HashService)newManager(LockssDaemon.HASH_SERVICE);
      managerMap.put(LockssDaemon.HASH_SERVICE, hashService);
    }
    return hashService;
  }

  /**
   * return the sched service instance
   * @return the SchedService
   */
  public SchedService getSchedService() {
    if (schedService == null) {
      schedService = (SchedService)newManager(LockssDaemon.SCHED_SERVICE);
      managerMap.put(LockssDaemon.SCHED_SERVICE, schedService);
    }
    return schedService;
  }

  /**
   * return the SystemMetrics instance
   * @return the SystemMetrics
   */
  public SystemMetrics getSystemMetrics() {
    if (systemMetrics == null) {
      systemMetrics = (SystemMetrics)newManager(LockssDaemon.SYSTEM_METRICS);
      managerMap.put(LockssDaemon.SYSTEM_METRICS, systemMetrics);
    }
    return systemMetrics;
  }

  /**
   * return the poll manager instance
   * @return the PollManager
   */
  public PollManager getPollManager() {
    if (pollManager == null) {
      pollManager = (PollManager)newManager(LockssDaemon.POLL_MANAGER);
      managerMap.put(LockssDaemon.POLL_MANAGER, pollManager);
    }
    return pollManager;
  }

  /**
   * return the communication manager instance
   * @return the LcapDatagramComm
   */
  public LcapDatagramComm getDatagramCommManager() {
    if (commManager == null) {
      commManager =
	(LcapDatagramComm)newManager(LockssDaemon.DATAGRAM_COMM_MANAGER);
      managerMap.put(LockssDaemon.DATAGRAM_COMM_MANAGER, commManager);
    }
    return commManager;
  }

  /**
   * return the router manager instance
   * @return the LcapDatagramRouter
   */
  public LcapDatagramRouter getDatagramRouterManager() {
    if (routerManager == null) {
      routerManager =
	(LcapDatagramRouter)newManager(LockssDaemon.DATAGRAM_ROUTER_MANAGER);
      managerMap.put(LockssDaemon.DATAGRAM_ROUTER_MANAGER, routerManager);
    }
    return routerManager;
  }

  /**
   * return the proxy manager instance
   * @return the ProxyManager
   */
  public ProxyManager getProxyManager() {
    if (proxyManager == null) {
      proxyManager = (ProxyManager)newManager(LockssDaemon.PROXY_MANAGER);
      managerMap.put(LockssDaemon.PROXY_MANAGER, proxyManager);
    }
    return proxyManager;
  }

  /**
   * return the crawl manager instance
   * @return the CrawlManager
   */
  public CrawlManager getCrawlManager() {
    if (crawlManager == null) {
      crawlManager = (CrawlManager)newManager(LockssDaemon.CRAWL_MANAGER);
      managerMap.put(LockssDaemon.CRAWL_MANAGER, crawlManager);
    }
    return crawlManager;
  }

  /**
   * return the node manager status instance
   * @return the TreewalkManager
   */
  public NodeManagerManager getNodeManagerManager() {
    if (nodeManagerManager == null) {
      nodeManagerManager =
	(NodeManagerManager)newManager(LockssDaemon.NODE_MANAGER_MANAGER);
      managerMap.put(LockssDaemon.NODE_MANAGER_MANAGER, nodeManagerManager);
    }
    return nodeManagerManager;
  }

  /**
   * return the treewalk manager instance
   * @return the TreewalkManager
   */
  public TreeWalkManager getTreeWalkManager() {
    if (treeWalkManager == null) {
      treeWalkManager =
	(TreeWalkManager)newManager(LockssDaemon.TREEWALK_MANAGER);
      managerMap.put(LockssDaemon.TREEWALK_MANAGER, treeWalkManager);
    }
    return treeWalkManager;
  }

  /**
   * return the repository manager instance
   * @return the RepositoryManager
   */
  public RepositoryManager getRepositoryManager() {
    if (repositoryManager == null) {
      repositoryManager =
	(RepositoryManager)newManager(LockssDaemon.REPOSITORY_MANAGER);
      managerMap.put(LockssDaemon.REPOSITORY_MANAGER, repositoryManager);
    }
    return repositoryManager;
  }

  /**
   * return the plugin manager instance
   * @return the PluginManager
   */
  public PluginManager getPluginManager() {
    if (pluginManager == null) {
      pluginManager = (PluginManager)newManager(LockssDaemon.PLUGIN_MANAGER);
      managerMap.put(LockssDaemon.PLUGIN_MANAGER, pluginManager);
    }
    return pluginManager;
  }

  /**
   * return the Identity Manager
   * @return IdentityManager
   */

  public IdentityManager getIdentityManager() {
    if (identityManager == null) {
      identityManager =
	(IdentityManager)newManager(LockssDaemon.IDENTITY_MANAGER);
      managerMap.put(LockssDaemon.IDENTITY_MANAGER, identityManager);
    }
    return identityManager;
  }

  public StatusService getStatusService() {
    if (statusService == null) {
      statusService = (StatusService)newManager(LockssDaemon.STATUS_SERVICE);
      managerMap.put(LockssDaemon.STATUS_SERVICE, statusService);
    }
    return statusService;
  }

  /**
   * return the RemoteApi instance
   * @return the RemoteApi
   */
  public RemoteApi getRemoteApi() {
    if (remoteApi == null) {
      remoteApi = (RemoteApi)newManager(LockssDaemon.REMOTE_API);
      managerMap.put(LockssDaemon.REMOTE_API, remoteApi);
    }
    return remoteApi;
  }

  /**
   * Set the CommManager
   * @param commMan the new manager
   */
  public void setDatagramCommManager(LcapDatagramComm commMan) {
    commManager = commMan;
    managerMap.put(LockssDaemon.DATAGRAM_COMM_MANAGER, commManager);
  }

  /**
   * Set the RouterManager
   * @param routerMan the new manager
   */
  public void setDatagramRouterManager(LcapDatagramRouter routerMan) {
    routerManager = routerMan;
    managerMap.put(LockssDaemon.DATAGRAM_ROUTER_MANAGER, routerManager);
  }

  /**
   * Set the CrawlManager
   * @param crawlMan the new manager
   */
  public void setCrawlManager(CrawlManager crawlMan) {
    crawlManager = crawlMan;
    managerMap.put(LockssDaemon.CRAWL_MANAGER, crawlManager);
  }

  /**
   * Set the TreeWalkManager
   * @param treeWalkMan the new manager
   */
  public void setTreeWalkManager(TreeWalkManager treeWalkMan) {
    treeWalkManager = treeWalkMan;
    managerMap.put(LockssDaemon.TREEWALK_MANAGER, treeWalkManager);
  }

  /**
   * Set the RepositoryManager
   * @param treeWalkMan the new manager
   */
  public void setRepositoryManager(RepositoryManager repositoryMan) {
    repositoryManager = repositoryMan;
    managerMap.put(LockssDaemon.REPOSITORY_MANAGER, repositoryManager);
  }

  /**
   * Set the NodeManagerManager
   * @param nodeManMan the new manager
   */
  public void setNodeManagerManager(NodeManagerManager nodeManMan) {
    nodeManagerManager = nodeManMan;
    managerMap.put(LockssDaemon.NODE_MANAGER_MANAGER, nodeManMan);
  }

  /**
   * Set the WatchdogService
   * @param wdogService the new service
   */
  public void setWatchdogService(WatchdogService wdogService) {
    this.wdogService = wdogService;
    managerMap.put(LockssDaemon.WATCHDOG_SERVICE, wdogService);
  }

  /**
   * Set the MailService
   * @param mailMan the new manager
   */
  public void setMailService(MailService mailMan) {
    mailService = mailMan;
    managerMap.put(LockssDaemon.MAIL_SERVICE, mailService);
  }

  /**
   * Set the AlertManager
   * @param alertMan the new manager
   */
  public void setAlertManager(AlertManager alertMan) {
    alertManager = alertMan;
    managerMap.put(LockssDaemon.ALERT_MANAGER, alertManager);
  }

  /**
   * Set the HashService
   * @param hashServ the new service
   */
  public void setHashService(HashService hashServ) {
    hashService = hashServ;
    managerMap.put(LockssDaemon.HASH_SERVICE, hashService);
  }

  /**
   * Set the SchedService
   * @param schedServ the new service
   */
  public void setSchedService(SchedService schedServ) {
    schedService = schedServ;
    managerMap.put(LockssDaemon.SCHED_SERVICE, schedService);
  }

  /**
   * Set the IdentityManager
   * @param idMan the new manager
   */
  public void setIdentityManager(IdentityManager idMan) {
    identityManager = idMan;
    managerMap.put(LockssDaemon.IDENTITY_MANAGER, identityManager);
  }

  /**
   * Set the PluginManager
   * @param pluginMan the new manager
   */
  public void setPluginManager(PluginManager pluginMan) {
    pluginManager = pluginMan;
    managerMap.put(LockssDaemon.PLUGIN_MANAGER, pluginManager);
  }

  /**
   * Set the PollManager
   * @param pollMan the new manager
   */
  public void setPollManager(PollManager pollMan) {
    pollManager = pollMan;
    managerMap.put(LockssDaemon.POLL_MANAGER, pollManager);
  }

  /**
   * Set the ProxyManager
   * @param proxyMgr the new manager
   */
  public void setProxyManager(ProxyManager proxyMgr) {
    proxyManager = proxyMgr;
    managerMap.put(LockssDaemon.PROXY_MANAGER, proxyManager);
  }

  /**
   * Set the SystemMetrics
   * @param sysMetrics the new metrics
   */
  public void setSystemMetrics(SystemMetrics sysMetrics) {
    systemMetrics = sysMetrics;
    managerMap.put(LockssDaemon.SYSTEM_METRICS, sysMetrics);
  }

  /**
   * Set the RemoteApi
   * @param sysMetrics the new metrics
   */
  public void setRemoteApi(RemoteApi sysMetrics) {
    remoteApi = sysMetrics;
    managerMap.put(LockssDaemon.REMOTE_API, sysMetrics);
  }

  // AU managers

  /** Create an AU manager instance, mimicking what LockssDaemon does */
  public LockssAuManager newAuManager(String key, ArchivalUnit au) {
    ManagerDesc desc = findAuManagerDesc(key);
    if (desc == null) {
      throw new LockssAppException("No AU ManagerDesc for: " + key);
    }
    log.debug2("Loading manager: " + desc.getKey() + " for " + au);
    try {
      LockssAuManager mgr = initAuManager(desc, au);
      setAuManager(desc, au, mgr);
      return mgr;
    } catch (Exception e) {
      log.error("Error starting au manager", e);
      throw new LockssAppException("Can't load au manager: " +
				      e.toString());
    }
  }

  void setAuManager(String key, ArchivalUnit au, LockssAuManager mgr) {
    setAuManager(findAuManagerDesc(key), au, mgr);
  }

  void setAuManager(ManagerDesc desc, ArchivalUnit au, LockssAuManager mgr) {
    LinkedMap auMgrMap = (LinkedMap)auManagerMaps.get(au);
    if (auMgrMap == null) {
      auMgrMap = new LinkedMap();
      auManagerMaps.put(au, auMgrMap);
    }
    auMgrMap.put(desc.getKey(), mgr);
  }

  /** Overridden to prevent manager from being started */
  public void startOrReconfigureAuManagers(ArchivalUnit au,
					   Configuration auConfig)
      throws Exception {
  }

  /** Return ActivityRegulator for AU */
  public ActivityRegulator getActivityRegulator(ArchivalUnit au) {
    try {
      return super.getActivityRegulator(au);
    } catch (IllegalArgumentException e) {
      return (ActivityRegulator)newAuManager(LockssDaemon.ACTIVITY_REGULATOR,
					     au);
    }
  }

  /** Return LockssRepository for AU */
  public LockssRepository getLockssRepository(ArchivalUnit au) {
    try {
      return super.getLockssRepository(au);
    } catch (IllegalArgumentException e) {
      return (LockssRepository)newAuManager(LockssDaemon.LOCKSS_REPOSITORY,
					    au);
    }
  }

  /** Return NodeManager for AU */
  public NodeManager getNodeManager(ArchivalUnit au) {
    try {
      return super.getNodeManager(au);
    } catch (IllegalArgumentException e) {
      return (NodeManager)newAuManager(LockssDaemon.NODE_MANAGER, au);
    }
  }

  /** Return HistoryRepository for AU */
  public HistoryRepository getHistoryRepository(ArchivalUnit au) {
    try {
      return super.getHistoryRepository(au);
    } catch (IllegalArgumentException e) {
      return (HistoryRepository)newAuManager(LockssDaemon.HISTORY_REPOSITORY,
          au);
    }
  }

  /**
   * Set the ActivityRegulator for a given AU.
   * @param actReg the new regulator
   * @param au the ArchivalUnit
   */
  public void setActivityRegulator(ActivityRegulator actReg, ArchivalUnit au) {
    setAuManager(ACTIVITY_REGULATOR, au, actReg);
  }

  /**
   * Set the LockssRepository for a given AU.
   * @param repo the new repository
   * @param au the ArchivalUnit
   */
  public void setLockssRepository(LockssRepository repo, ArchivalUnit au) {
    setAuManager(LOCKSS_REPOSITORY, au, repo);
  }

  /**
   * Set the NodeManager for a given AU.
   * @param nodeMan the new manager
   * @param au the ArchivalUnit
   */
  public void setNodeManager(NodeManager nodeMan, ArchivalUnit au) {
    setAuManager(NODE_MANAGER, au, nodeMan);
  }

  /**
   * Set the HistoryRepository for a given AU.
   * @param histRepo the new repository
   * @param au the ArchivalUnit
   */
  public void setHistoryRepository(HistoryRepository histRepo, ArchivalUnit au) {
    setAuManager(HISTORY_REPOSITORY, au, histRepo);
  }



  private boolean daemonInited = false;
  private boolean daemonRunning = false;

  /**
   * @return true iff all managers have been inited
   */
  public boolean isDaemonInited() {
    return daemonInited;
  }

  /**
   * @return true iff all managers have been started
   */
  public boolean isDaemonRunning() {
    return daemonRunning;
  }

  /** set daemonInited
   * @param val true if inited
   */
  public void setDaemonInited(boolean val) {
    daemonInited = val;
  }

  /** set daemonRunning
   * @param val true if running
   */
  public void setDaemonRunning(boolean val) {
    daemonRunning = val;
  }

}
