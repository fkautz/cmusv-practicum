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
import org.lockss.hasher.*;
import org.lockss.protocol.*;
import org.lockss.poller.*;
import org.lockss.state.*;
import org.lockss.repository.*;
import org.lockss.proxy.*;
import org.lockss.crawler.*;
import org.lockss.plugin.*;
import org.lockss.app.*;
import org.lockss.daemon.*;
import org.lockss.daemon.status.*;

public class MockLockssDaemon extends LockssDaemon {
  WatchdogService wdogService = null;
  HashService hashService = null;
  SystemMetrics systemMetrics = null;
  PollManager pollManager = null;
  LcapComm commManager = null;
  LcapRouter routerManager = null;
  HistoryRepository historyRepository = null;
  ProxyManager proxyManager = null;
  CrawlManager crawlManager = null;
  PluginManager pluginManager = null;
  IdentityManager identityManager = null;
  StatusService statusService = null;

  MockAuSpecificManagerHandler mockHandler = null;

  public MockLockssDaemon() {
    this(null);
  }

  public MockLockssDaemon(List urls) {
    super(urls);
    auSpecificManagers = new MockAuSpecificManagerHandler(this, 3);
    mockHandler = (MockAuSpecificManagerHandler)auSpecificManagers;
  }

  public void startDaemon() throws Exception {
  }

  public void stopDaemon() {
    wdogService = null;
    hashService = null;
    pollManager = null;
    commManager = null;
    historyRepository = null;
    proxyManager = null;
    crawlManager = null;
    pluginManager = null;
    identityManager = null;
    statusService = null;

    mockHandler.clear();

    //super.stopDaemon();
  }

  /**
   * return the watchdog service instance
   * @return the WatchdogService
   */
  public WatchdogService getWatchdogService() {
    if (wdogService == null) {
      wdogService = new WatchdogService();
      try {
        wdogService.initService(this);
      }
      catch (LockssDaemonException ex) {
      }
      theManagers.put(LockssDaemon.WATCHDOG_SERVICE, wdogService);
    }
    return wdogService;
  }

  /**
   * return the hash service instance
   * @return the HashService
   */
  public HashService getHashService() {
    if (hashService == null) {
      hashService = new HashService();
      try {
        hashService.initService(this);
      }
      catch (LockssDaemonException ex) {
      }
      theManagers.put(LockssDaemon.HASH_SERVICE, hashService);
    }
    return hashService;
  }

  /**
   * return the SystemMetrics instance
   * @return the SystemMetrics
   */
  public SystemMetrics getSystemMetrics() {
    if (systemMetrics == null) {
      systemMetrics = new SystemMetrics();
      try {
        systemMetrics.initService(this);
      }
      catch (LockssDaemonException ex) {
      }
      theManagers.put(LockssDaemon.SYSTEM_METRICS, systemMetrics);
    }
    return systemMetrics;
  }

  /**
   * return the poll manager instance
   * @return the PollManager
   */
  public PollManager getPollManager() {
    if (pollManager == null) {
      pollManager = new PollManager();
      try {
        pollManager.initService(this);
      }
      catch (LockssDaemonException ex) {
      }
      theManagers.put(LockssDaemon.POLL_MANAGER, pollManager);
    }
    return pollManager;
  }

  /**
   * return the communication manager instance
   * @return the LcapComm
   */
  public LcapComm getCommManager() {
    if (commManager == null) {
      commManager = new LcapComm();
      try {
        commManager.initService(this);
      }
      catch (LockssDaemonException ex) {
      }
      theManagers.put(LockssDaemon.COMM_MANAGER, commManager);
    }
    return commManager;
  }

  /**
   * return the router manager instance
   * @return the LcapRouter
   */
  public LcapRouter getRouterManager() {
    if (routerManager == null) {
      routerManager = new LcapRouter();
      try {
        routerManager.initService(this);
      }
      catch (LockssDaemonException ex) {
      }
      theManagers.put(LockssDaemon.ROUTER_MANAGER, routerManager);
    }
    return routerManager;
  }

  /**
   * return the history repository instance
   * @return the HistoryRepository
   */
  public HistoryRepository getHistoryRepository() {
    if (historyRepository == null) {
      HistoryRepositoryImpl impl = new HistoryRepositoryImpl();
      try {
        impl.initService(this);
      }
      catch (LockssDaemonException ex) {
      }
      historyRepository = impl;
      theManagers.put(LockssDaemon.HISTORY_REPOSITORY, historyRepository);
    }
    return historyRepository;
  }

  /**
   * return the proxy manager instance
   * @return the ProxyManager
   */
  public ProxyManager getProxyManager() {
    if (proxyManager == null) {
      proxyManager = new ProxyManager();
      try {
        proxyManager.initService(this);
      }
      catch (LockssDaemonException ex) {
      }
      theManagers.put(LockssDaemon.PROXY_MANAGER, proxyManager);
    }
    return proxyManager;
  }

  /**
   * return the crawl manager instance
   * @return the CrawlManager
   */
  public CrawlManager getCrawlManager() {
    if (crawlManager == null) {
      CrawlManagerImpl impl = new CrawlManagerImpl();
      try {
        impl.initService(this);
      }
      catch (LockssDaemonException ex) {
      }
      crawlManager = impl;
      theManagers.put(LockssDaemon.CRAWL_MANAGER, crawlManager);
    }
    return crawlManager;
  }

  /**
   * return the plugin manager instance
   * @return the PluginManager
   */
  public PluginManager getPluginManager() {
    if (pluginManager == null) {
      pluginManager = new PluginManager();
      try {
        pluginManager.initService(this);
      }
      catch (LockssDaemonException ex) {
      }
      theManagers.put(LockssDaemon.PLUGIN_MANAGER, pluginManager);
    }
    return pluginManager;
  }

  /**
   * return the Identity Manager
   * @return IdentityManager
   */

  public IdentityManager getIdentityManager() {
    if (identityManager == null) {
      identityManager = new IdentityManager();
      identityManager.initService(this);
    }
    theManagers.put(LockssDaemon.IDENTITY_MANAGER, identityManager);
    return identityManager;
  }

  public StatusService getStatusService() {
    if (statusService == null) {
      StatusServiceImpl impl = new StatusServiceImpl();
      try {
        impl.initService(this);
      }
      catch (LockssDaemonException ex) {
      }
      statusService = impl;
      theManagers.put(LockssDaemon.STATUS_SERVICE, statusService);
    }

    return statusService;
  }

  public ActivityRegulator getActivityRegulator(ArchivalUnit au) {
    ActivityRegulator regulator =
        (ActivityRegulator)mockHandler.getAUSpecificManager(
        ACTIVITY_REGULATOR, au);
    if (regulator==null) {
      regulator = (ActivityRegulator)mockHandler.addAuSpecificManager(
          LockssDaemon.ACTIVITY_REGULATOR, au);
    }
    return regulator;
  }

  public LockssRepository getLockssRepository(ArchivalUnit au) {
    LockssRepository repository =
        (LockssRepository)mockHandler.getAUSpecificManager(
        LOCKSS_REPOSITORY, au);
    if (repository==null) {
      repository = (LockssRepository)mockHandler.addAuSpecificManager(
          LockssDaemon.LOCKSS_REPOSITORY, au);
    }
    return repository;
  }

  public NodeManager getNodeManager(ArchivalUnit au) {
    NodeManager manager =
        (NodeManager)mockHandler.getAUSpecificManager(NODE_MANAGER, au);
    if (manager==null) {
      manager = (NodeManager)mockHandler.addAuSpecificManager(
          LockssDaemon.NODE_MANAGER, au);
    }
    return manager;
  }

  /**
   * Set the CommManager
   * @param commMan the new manager
   */
  public void setCommManager(LcapComm commMan) {
    commManager = commMan;
    theManagers.put(LockssDaemon.COMM_MANAGER, commManager);
  }

  /**
   * Set the RouterManager
   * @param routerMan the new manager
   */
  public void setRouterManager(LcapRouter routerMan) {
    routerManager = routerMan;
    theManagers.put(LockssDaemon.ROUTER_MANAGER, routerManager);
  }

  /**
   * Set the CrawlManager
   * @param crawlMan the new manager
   */
  public void setCrawlManager(CrawlManager crawlMan) {
    crawlManager = crawlMan;
    theManagers.put(LockssDaemon.CRAWL_MANAGER, crawlManager);
  }

  /**
   * Set the ActivityRegulator for a given AU.
   * @param actReg the new regulator
   * @param au the ArchivalUnit
   */
  public void setActivityRegulator(ActivityRegulator actReg, ArchivalUnit au) {
    mockHandler.setActivityRegulator(actReg, au);
  }

  /**
   * Set the WatchdogService
   * @param wdogService the new service
   */
  public void setWatchdogService(WatchdogService wdogService) {
    this.wdogService = wdogService;
    theManagers.put(LockssDaemon.WATCHDOG_SERVICE, wdogService);
  }

  /**
   * Set the HashService
   * @param hashServ the new service
   */
  public void setHashService(HashService hashServ) {
    hashService = hashServ;
    theManagers.put(LockssDaemon.HASH_SERVICE, hashService);
  }

  /**
   * Set the HistoryRepository
   * @param histRepo the new repository
   */
  public void setHistoryRepository(HistoryRepository histRepo) {
    historyRepository = histRepo;
    theManagers.put(LockssDaemon.HISTORY_REPOSITORY, historyRepository);
  }

  /**
   * Set the IdentityManager
   * @param idMan the new manager
   */
  public void setIdentityManager(IdentityManager idMan) {
    identityManager = idMan;
    theManagers.put(LockssDaemon.IDENTITY_MANAGER, identityManager);
  }

  /**
   * Set the LockssRepository for a given AU.
   * @param repo the new repository
   * @param au the ArchivalUnit
   */
  public void setLockssRepository(LockssRepository repo, ArchivalUnit au) {
    mockHandler.setLockssRepository(repo, au);
  }

  /**
   * Set the NodeManager for a given AU.
   * @param nodeMan the new manager
   * @param au the ArchivalUnit
   */
  public void setNodeManager(NodeManager nodeMan, ArchivalUnit au) {
    mockHandler.setNodeManager(nodeMan, au);
  }

  /**
   * Set the PluginManager
   * @param pluginMan the new manager
   */
  public void setPluginManager(PluginManager pluginMan) {
    pluginManager = pluginMan;
    theManagers.put(LockssDaemon.PLUGIN_MANAGER, pluginManager);
  }

  /**
   * Set the PollManager
   * @param pollMan the new manager
   */
  public void setPollManager(PollManager pollMan) {
    pollManager = pollMan;
    theManagers.put(LockssDaemon.POLL_MANAGER, pollManager);
  }

  /**
   * Set the ProxyManager
   * @param proxyMgr the new manager
   */
  public void setProxyManager(ProxyManager proxyMgr) {
    proxyManager = proxyMgr;
    theManagers.put(LockssDaemon.PROXY_MANAGER, proxyManager);
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

  /**
   * Overridden version of AuSpecificManagerHandler which does not call
   * 'initService()' or 'startService()' on the managers it adds, and has
   * set functions.
   */
  static class MockAuSpecificManagerHandler extends AuSpecificManagerHandler {

    MockAuSpecificManagerHandler(LockssDaemon theDaemon, int managerNum) {
      super(theDaemon, managerNum);
    }

    public LockssManager addAuSpecificManager(String managerKey,
                                                 ArchivalUnit au) {
      HashMap managerMap = getAUSpecificManagerMap(managerKey);
      LockssManager manager = (LockssManager)managerMap.get(au);
      if (manager == null) {
        manager = getNewManager(managerKey, au);
        managerMap.put(au, manager);
        // doesn't init or start service
      }
      return manager;
    }

    void setActivityRegulator(ActivityRegulator regulator,
                                           ArchivalUnit au) {
      getAUSpecificManagerMap(LockssDaemon.ACTIVITY_REGULATOR).put(au,
          regulator);
    }

    void setLockssRepository(LockssRepository repository,
                                           ArchivalUnit au) {
      getAUSpecificManagerMap(LockssDaemon.LOCKSS_REPOSITORY).put(au,
          repository);
    }

    void setNodeManager(NodeManager manager, ArchivalUnit au) {
      getAUSpecificManagerMap(LockssDaemon.NODE_MANAGER).put(au, manager);
    }

    public void clear() {
      auSpecificManagers.clear();
    }
  }
}
