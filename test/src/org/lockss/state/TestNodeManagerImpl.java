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

package org.lockss.state;

import java.io.*;
import java.net.*;
import java.util.*;

import org.lockss.daemon.*;
import org.lockss.plugin.*;
import org.lockss.plugin.base.*;
import org.lockss.poller.*;
import org.lockss.protocol.*;
import org.lockss.repository.*;
import org.lockss.test.*;
import org.lockss.util.*;

public class TestNodeManagerImpl extends LockssTestCase {
  public static final String TEST_URL = "http://www.example.com";
  private static Logger log = Logger.getLogger("TestNMI");
  private String tempDirPath;
  private MockArchivalUnit mau = null;
  private NodeManagerImpl nodeManager;
  private MockPollManager pollManager;
  private MockCrawlManager crawlManager;
  private HistoryRepository historyRepo;
  private List urlList = null;
  private Poll namePoll = null;
  private Poll contentPoll = null;
  private Random random = new Random();

  private MockLockssDaemon theDaemon;
  MockIdentityManager idManager;

  public void setUp() throws Exception {
    super.setUp();
    theDaemon = new MockLockssDaemon();
    tempDirPath = getTempDir().getAbsolutePath() + File.separator;
    Properties p = new Properties();
    p.setProperty(LockssRepositoryImpl.PARAM_CACHE_LOCATION, tempDirPath);
    p.setProperty(HistoryRepositoryImpl.PARAM_HISTORY_LOCATION, tempDirPath);
    p.setProperty(NodeManagerImpl.PARAM_NODESTATE_CACHE_SIZE, "10");
    p.setProperty(IdentityManager.PARAM_LOCAL_IP, "127.1.2.3");
    p.setProperty(NodeManagerImpl.PARAM_RECALL_DELAY, "5s");
    ConfigurationUtil.setCurrentConfigFromProps(p);

    mau = new MockArchivalUnit();
    mau.setAUCachedUrlSet(makeFakeCachedUrlSet(mau, TEST_URL, 2, 2));
    theDaemon.getPluginManager();
    PluginUtil.registerArchivalUnit(mau);

    crawlManager = new MockCrawlManager();
    theDaemon.setCrawlManager(crawlManager);

    pollManager = new MockPollManager();
    theDaemon.setPollManager(pollManager);
    idManager = new MockIdentityManager();
    idManager.initService(theDaemon);
    theDaemon.setIdentityManager(idManager);
    pollManager.initService(theDaemon);
    pollManager.startService();

    // create au state so thread doesn't throw null pointers
    theDaemon.getLockssRepository(mau).createNewNode(TEST_URL);

    nodeManager = new NodeManagerImpl(mau);
    nodeManager.initService(theDaemon);
    historyRepo = new HistoryRepositoryImpl(tempDirPath);
    historyRepo.startService();
    theDaemon.setHistoryRepository(historyRepo);
    nodeManager.historyRepo = historyRepo;
    nodeManager.startService();
    // don't need the thread
    nodeManager.killTreeWalk();

    theDaemon.getHashService().startService();

    loadNodeStates(mau, nodeManager);
  }

  public void tearDown() throws Exception {
    nodeManager.stopService();
    pollManager.stopService();
    historyRepo.stopService();
    theDaemon.getLockssRepository(mau).stopService();
    theDaemon.getHashService().stopService();
    PluginUtil.unregisterAllArchivalUnits();
    theDaemon.stopDaemon();
    TimeBase.setReal();
    super.tearDown();
  }

  public void testGetNodeState() throws Exception {
    CachedUrlSet cus = getCUS(mau, TEST_URL);
    NodeState node = nodeManager.getNodeState(cus);
    assertNotNull(node);
    assertEquals(cus, node.getCachedUrlSet());
    assertNotNull(node.getCrawlState());
    assertEquals(-1, node.getCrawlState().getType());
    assertEquals(CrawlState.FINISHED, node.getCrawlState().getStatus());

    NodeState node2 = nodeManager.getNodeState(cus);
    assertSame(node, node2);

    cus = getCUS(mau, TEST_URL + "/branch1");
    node = nodeManager.getNodeState(cus);
    assertNotNull(node);
    assertEquals(cus, node.getCachedUrlSet());

    // null, since not in repository
    cus = getCUS(mau, TEST_URL + "/branch3");
    node = nodeManager.getNodeState(cus);
    assertNull(node);

    // should find it now that it's added
    theDaemon.getLockssRepository(mau).createNewNode(TEST_URL + "/branch3");
    node = nodeManager.getNodeState(cus);
    assertNotNull(node);
  }

  public void testLoadingFromHistoryRepository() throws Exception {
    CachedUrlSet cus = getCUS(mau, TEST_URL + "/branch3");
    // add to lockss repository
    theDaemon.getLockssRepository(mau).createNewNode(TEST_URL + "/branch3");
    // store in history repository
    NodeState node = new NodeStateImpl(cus, 123, new CrawlState(-1, -1, -1),
                                       new ArrayList(), historyRepo);
    historyRepo.storeNodeState(node);

    // should load correctly
    node = nodeManager.getNodeState(cus);
    assertNotNull(node);
    assertEquals(TEST_URL + "/branch3", node.getCachedUrlSet().getUrl());
    assertEquals(123, node.getAverageHashDuration());
  }

  public void testDeadPollRemoval() throws Exception {
    CachedUrlSet cus = getCUS(mau, TEST_URL + "/branch3");
    // add to lockss repository
    theDaemon.getLockssRepository(mau).createNewNode(TEST_URL + "/branch3");
    // store in history repository
    ArrayList polls = new ArrayList(1);
    polls.add(new PollState(1, "lwr1", "upr1", 1, 0, Deadline.MAX, false));

    // start the poll
    pollManager.sendPollRequest(LcapMessage.CONTENT_POLL_REQ, new PollSpec(cus));

    NodeStateImpl node = new NodeStateImpl(cus, 123, new CrawlState(-1, -1, -1),
                                           polls, historyRepo);
    historyRepo.storeNodeState(node);

    // should keep poll active
    node = (NodeStateImpl)nodeManager.getNodeState(cus);
    assertEquals(1, node.polls.size());
    assertFalse(node.getPollHistories().hasNext());

    cus = getCUS(mau, TEST_URL + "/branch4");
    // add to lockss repository
    theDaemon.getLockssRepository(mau).createNewNode(TEST_URL + "/branch4");
    // store in history repository
    polls = new ArrayList(1);
    polls.add(new PollState(1, "lwr1", "upr1", 1, 0, Deadline.MAX, false));
    // don't start the poll

    node = new NodeStateImpl(cus, 123, new CrawlState(-1, -1, -1),
                             polls, historyRepo);
    historyRepo.storeNodeState(node);

    // should transfer poll to history
    node = (NodeStateImpl)nodeManager.getNodeState(cus);
    assertNotNull(node);
    assertEquals(TEST_URL + "/branch4", node.getCachedUrlSet().getUrl());
    assertEquals(123, node.getAverageHashDuration());
    assertEquals(0, node.polls.size());
    assertEquals(1, node.pollHistories.size());
  }

  public void testGetAuState() {
    AuState auState = nodeManager.getAuState();
    assertEquals(mau.getAUId(), auState.getArchivalUnit().getAUId());
  }

  public void testMapErrorCodes() {
    assertEquals(PollState.ERR_HASHING,
                 nodeManager.mapResultsErrorToPollError(Poll.ERR_HASHING));
    assertEquals(PollState.ERR_IO,
                 nodeManager.mapResultsErrorToPollError(Poll.ERR_IO));
    assertEquals(PollState.ERR_SCHEDULE_HASH,
                 nodeManager.mapResultsErrorToPollError(Poll.ERR_SCHEDULE_HASH));
    assertEquals(PollState.ERR_UNDEFINED,
                 nodeManager.mapResultsErrorToPollError(1));
  }

  public void testShouldStartPoll() throws Exception {
    TimeBase.setSimulated(10000);
    contentPoll = createPoll(TEST_URL, true, false, 15, 5);
    PollTally results = contentPoll.getVoteTally();
    // let's generate some history
    CachedUrlSet cus = results.getCachedUrlSet();
    Vector subFiles = new Vector(2);
    subFiles.add(getCUS(mau, TEST_URL));
    ( (MockCachedUrlSet) cus).setHashItSource(subFiles);
    NodeState node = nodeManager.getNodeState(cus);
    for (int i = 0; i < 3; i++) {
      ( (NodeStateImpl) node).closeActivePoll(new PollHistory(Poll.CONTENT_POLL,
          "", "", PollHistory.WON, 100, 123, null, false));
    }

    // if we got a valid node we should return true
    assertTrue(nodeManager.shouldStartPoll(cus, results));

    // if we haven't got one we should return false
    assertFalse(nodeManager.shouldStartPoll(getCUS(mau, TEST_URL + "/bogus"),
                                            results));

    // if we have a damaged node we return false
    nodeManager.damagedNodes.add(cus.getUrl());
    assertFalse(nodeManager.shouldStartPoll(cus, results));
    nodeManager.damagedNodes.remove(cus.getUrl());
    TimeBase.setReal();
  }

  public void testStartPoll() throws Exception {
    contentPoll = createPoll(TEST_URL, true, false, 15, 5);
    PollTally results = contentPoll.getVoteTally();
    // let's generate some history
    CachedUrlSet cus = results.getCachedUrlSet();
    Vector subFiles = new Vector(2);
    subFiles.add(getCUS(mau, TEST_URL));
    ( (MockCachedUrlSet) cus).setHashItSource(subFiles);

    assertNull(nodeManager.activeNodes.get(results.getPollKey()));
    nodeManager.startPoll(cus, results, false);
    assertSame(nodeManager.activeNodes.get(results.getPollKey()),
               nodeManager.getNodeState(cus));
    nodeManager.updatePollResults(cus, results);
    assertNull(nodeManager.activeNodes.get(results.getPollKey()));
  }

  public void testHandleStandardContentPoll() throws Exception {
    NodeState nodeState = nodeManager.getNodeState(getCUS(mau, TEST_URL));

    // won content poll
    contentPoll = createPoll(TEST_URL, true, true, 15, 5);
    PollTally results = contentPoll.getVoteTally();

    // - running
    PollState pollState = new PollState(results.getType(),
                                        results.getPollSpec().getLwrBound(),
                                        results.getPollSpec().getUprBound(),
                                        PollState.RUNNING,
                                        results.getStartTime(),
                                        Deadline.MAX,
                                        false);
    nodeState.setState(NodeState.CONTENT_RUNNING);
    nodeManager.handleContentPoll(pollState, results, nodeState);
    assertEquals(PollState.WON, pollState.getStatus());
    assertEquals(NodeState.OK, nodeState.getState());
    reputationChangeTest(results);

    // - repairing
    pollState = new PollState(results.getType(),
                              results.getPollSpec().getLwrBound(),
                              results.getPollSpec().getUprBound(),
                              PollState.REPAIRING,
                              results.getStartTime(),
                              Deadline.MAX,
                              false);
    nodeState.setState(NodeState.CONTENT_REPLAYING);
    nodeManager.handleContentPoll(pollState, results, nodeState);
    assertEquals(PollState.REPAIRED, pollState.getStatus());
    assertEquals(NodeState.OK, nodeState.getState());
    reputationChangeTest(results);

    // lost content poll
    contentPoll = createPoll(TEST_URL + "/branch1", true, true, 5, 15);
    results = contentPoll.getVoteTally();

    // - running
    pollState = new PollState(results.getType(),
                              results.getPollSpec().getLwrBound(),
                              results.getPollSpec().getUprBound(),
                              PollState.RUNNING,
                              results.getStartTime(),
                              Deadline.MAX,
                              false);
    nodeState.setState(NodeState.CONTENT_RUNNING);
    nodeManager.handleContentPoll(pollState, results, nodeState);
    assertEquals(PollState.LOST, pollState.getStatus());
    assertEquals(NodeState.CONTENT_LOST, nodeState.getState());
    reputationChangeTest(results);

    // - repairing
    pollState = new PollState(results.getType(),
                              results.getPollSpec().getLwrBound(),
                              results.getPollSpec().getUprBound(),
                              PollState.REPAIRING,
                              results.getStartTime(),
                              Deadline.MAX,
                              false);
    nodeState.setState(NodeState.CONTENT_REPLAYING);
    nodeManager.handleContentPoll(pollState, results, nodeState);
    assertEquals(PollState.UNREPAIRABLE, pollState.getStatus());
    assertEquals(NodeState.DAMAGE_AT_OR_BELOW, nodeState.getState());
    reputationChangeTest(results);

    // won ranged poll (should do nothing to NodeState)
    contentPoll = createPoll(TEST_URL + "/branch1",
                             "a", "b", true, true, 15, 5);
    results = contentPoll.getVoteTally();

    // - running
    pollState = new PollState(results.getType(),
                              results.getPollSpec().getLwrBound(),
                              results.getPollSpec().getUprBound(),
                              PollState.RUNNING,
                              results.getStartTime(),
                              Deadline.MAX,
                              false);
    nodeState.setState(NodeState.POSSIBLE_DAMAGE_BELOW);
    nodeManager.handleContentPoll(pollState, results, nodeState);
    assertEquals(PollState.WON, pollState.getStatus());
    assertEquals(NodeState.POSSIBLE_DAMAGE_BELOW, nodeState.getState());
    reputationChangeTest(results);

    // lost ranged poll (should set NodeState to 'CONTENT_LOST')
    contentPoll = createPoll(TEST_URL + "/branch1",
                             "a", "b", true, true, 5, 15);
    results = contentPoll.getVoteTally();

    // - running
    pollState = new PollState(results.getType(),
                              results.getPollSpec().getLwrBound(),
                              results.getPollSpec().getUprBound(),
                              PollState.RUNNING,
                              results.getStartTime(),
                              Deadline.MAX,
                              false);
    nodeState.setState(NodeState.POSSIBLE_DAMAGE_BELOW);
    nodeManager.handleContentPoll(pollState, results, nodeState);
    assertEquals(PollState.LOST, pollState.getStatus());
    assertEquals(NodeState.CONTENT_LOST, nodeState.getState());
    reputationChangeTest(results);
  }

  public void testHandleSingleNodeContentPoll() throws Exception {
    NodeState nodeState = nodeManager.getNodeState(getCUS(mau, TEST_URL));
    // won poll (not mine)
    contentPoll = createPoll(TEST_URL + "/branch1",
                             PollSpec.SINGLE_NODE_LWRBOUND, null,
                             true, false, 15, 5);
    PollTally results = contentPoll.getVoteTally();


    // - running
    PollState pollState = new PollState(results.getType(),
                                        results.getPollSpec().getLwrBound(),
                                        results.getPollSpec().getUprBound(),
                                        PollState.RUNNING,
                                        results.getStartTime(),
                                        Deadline.MAX,
                                        false);
    nodeState.setState(NodeState.SNCUSS_POLL_RUNNING);
    nodeManager.handleContentPoll(pollState, results, nodeState);
    assertEquals(PollState.WON, pollState.getStatus());
    assertEquals(NodeState.OK, nodeState.getState());
    reputationChangeTest(results);

    // - repairing
    pollState = new PollState(results.getType(),
                              results.getPollSpec().getLwrBound(),
                              results.getPollSpec().getUprBound(),
                              PollState.REPAIRING,
                              results.getStartTime(),
                              Deadline.MAX,
                              false);
    nodeState.setState(NodeState.SNCUSS_POLL_REPLAYING);
    nodeManager.handleContentPoll(pollState, results, nodeState);
    assertEquals(PollState.REPAIRED, pollState.getStatus());
    assertEquals(NodeState.OK, nodeState.getState());
    reputationChangeTest(results);

    // won poll (mine)
    contentPoll = createPoll(TEST_URL + "/branch1",
                             PollSpec.SINGLE_NODE_LWRBOUND, null,
                             true, true, 15, 5);
    results = contentPoll.getVoteTally();

    // - running
    pollState = new PollState(results.getType(),
                              results.getPollSpec().getLwrBound(),
                              results.getPollSpec().getUprBound(),
                              PollState.RUNNING,
                              results.getStartTime(),
                              Deadline.MAX,
                              true);
    nodeState.setState(NodeState.SNCUSS_POLL_RUNNING);
    nodeManager.handleContentPoll(pollState, results, nodeState);
    assertEquals(PollState.WON, pollState.getStatus());
    assertEquals(NodeState.POSSIBLE_DAMAGE_BELOW, nodeState.getState());
    reputationChangeTest(results);

    // - repairing
    pollState = new PollState(results.getType(),
                              results.getPollSpec().getLwrBound(),
                              results.getPollSpec().getUprBound(),
                              PollState.REPAIRING,
                              results.getStartTime(),
                              Deadline.MAX,
                              true);
    nodeState.setState(NodeState.SNCUSS_POLL_REPLAYING);
    nodeManager.handleContentPoll(pollState, results, nodeState);
    assertEquals(PollState.REPAIRED, pollState.getStatus());
    assertEquals(NodeState.POSSIBLE_DAMAGE_BELOW, nodeState.getState());
    reputationChangeTest(results);

    // lost content poll (ownership irrelevant)
    contentPoll = createPoll(TEST_URL + "/branch1",
                             PollSpec.SINGLE_NODE_LWRBOUND, null,
                             true, true, 5, 15);
    results = contentPoll.getVoteTally();

    // - running
    pollState = new PollState(results.getType(),
                              results.getPollSpec().getLwrBound(),
                              results.getPollSpec().getUprBound(),
                              PollState.RUNNING,
                              results.getStartTime(),
                              Deadline.MAX,
                              false);
    nodeState.setState(NodeState.SNCUSS_POLL_RUNNING);
    nodeManager.handleContentPoll(pollState, results, nodeState);
    assertEquals(PollState.REPAIRING, pollState.getStatus());
    assertEquals(NodeState.NEEDS_REPAIR, nodeState.getState());
    reputationChangeTest(results);

    // - repairing
    pollState = new PollState(results.getType(),
                              results.getPollSpec().getLwrBound(),
                              results.getPollSpec().getUprBound(),
                              PollState.REPAIRING,
                              results.getStartTime(),
                              Deadline.MAX,
                              false);
    nodeState.setState(NodeState.SNCUSS_POLL_REPLAYING);
    nodeManager.handleContentPoll(pollState, results, nodeState);
    assertEquals(PollState.UNREPAIRABLE, pollState.getStatus());
    assertEquals(NodeState.UNREPAIRABLE_SNCUSS, nodeState.getState());
    reputationChangeTest(results);
  }

  public void testHandleNamePoll() throws Exception {
    NodeState nodeState = nodeManager.getNodeState(getCUS(mau, TEST_URL));

    // won name poll (not mine)
    namePoll = createPoll(TEST_URL, false, false, 15, 5);
    PollTally results = namePoll.getVoteTally();

    // - running
    PollState pollState = new PollState(results.getType(),
                                        results.getPollSpec().getLwrBound(),
                                        results.getPollSpec().getUprBound(),
                                        PollState.RUNNING,
                                        results.getStartTime(),
                                        Deadline.MAX,
                                        false);
    nodeState.setState(NodeState.NAME_RUNNING);
    nodeManager.handleNamePoll(pollState, results, nodeState);
    assertEquals(PollState.WON, pollState.getStatus());
    assertEquals(NodeState.OK, nodeState.getState());
    reputationChangeTest(results);

    // - repairing
    pollState = new PollState(results.getType(),
                              results.getPollSpec().getLwrBound(),
                              results.getPollSpec().getUprBound(),
                              PollState.REPAIRING,
                              results.getStartTime(),
                              Deadline.MAX,
                              false);
    nodeState.setState(NodeState.NAME_REPLAYING);
    nodeManager.handleNamePoll(pollState, results, nodeState);
    assertEquals(PollState.WON, pollState.getStatus());
    assertEquals(NodeState.NEEDS_REPLAY_POLL, nodeState.getState());
    reputationChangeTest(results);

    // won name poll (mine)
    namePoll = createPoll(TEST_URL, false, true, 15, 5);
    results = namePoll.getVoteTally();

    // - running
    pollState = new PollState(results.getType(),
                              results.getPollSpec().getLwrBound(),
                              results.getPollSpec().getUprBound(),
                              PollState.RUNNING,
                              results.getStartTime(),
                              Deadline.MAX,
                              true);
    nodeState.setState(NodeState.NAME_RUNNING);
    nodeManager.handleNamePoll(pollState, results, nodeState);
    assertEquals(PollState.WON, pollState.getStatus());
    assertEquals(NodeState.DAMAGE_AT_OR_BELOW, nodeState.getState());
    reputationChangeTest(results);

    // - repairing
    pollState = new PollState(results.getType(),
                              results.getPollSpec().getLwrBound(),
                              results.getPollSpec().getUprBound(),
                              PollState.REPAIRING,
                              results.getStartTime(),
                              Deadline.MAX,
                              true);
    nodeState.setState(NodeState.NAME_REPLAYING);
    nodeManager.handleNamePoll(pollState, results, nodeState);
    assertEquals(PollState.REPAIRED, pollState.getStatus());
    assertEquals(NodeState.NEEDS_REPLAY_POLL, nodeState.getState());
    reputationChangeTest(results);

    // lost name poll (ownership irrelevant)
    namePoll = createPoll(TEST_URL + "/branch1", false, true, 5, 15);
    results = namePoll.getVoteTally();

    // - running
    pollState = new PollState(results.getType(),
                              results.getPollSpec().getLwrBound(),
                              results.getPollSpec().getUprBound(),
                              PollState.RUNNING,
                              results.getStartTime(),
                              Deadline.MAX,
                              false);
    nodeState.setState(NodeState.NAME_RUNNING);
    nodeManager.handleNamePoll(pollState, results, nodeState);
    assertEquals(PollState.REPAIRING, pollState.getStatus());
    assertEquals(NodeState.WRONG_NAMES, nodeState.getState());
    reputationChangeTest(results);

    // - repairing
    pollState = new PollState(results.getType(),
                              results.getPollSpec().getLwrBound(),
                              results.getPollSpec().getUprBound(),
                              PollState.REPAIRING,
                              results.getStartTime(),
                              Deadline.MAX,
                              false);
    nodeState.setState(NodeState.NAME_REPLAYING);
    nodeManager.handleNamePoll(pollState, results, nodeState);
    assertEquals(PollState.UNREPAIRABLE, pollState.getStatus());
    assertEquals(NodeState.UNREPAIRABLE_NAMES, nodeState.getState());
    reputationChangeTest(results);

    // won ranged poll (should do nothing to NodeState)
    namePoll = createPoll(TEST_URL + "/branch1",
                             "a", "b", false, true, 15, 5);
    results = namePoll.getVoteTally();

    // - running
    pollState = new PollState(results.getType(),
                              results.getPollSpec().getLwrBound(),
                              results.getPollSpec().getUprBound(),
                              PollState.RUNNING,
                              results.getStartTime(),
                              Deadline.MAX,
                              false);
    nodeState.setState(NodeState.POSSIBLE_DAMAGE_BELOW);
    nodeManager.handleNamePoll(pollState, results, nodeState);
    assertEquals(PollState.WON, pollState.getStatus());
    assertEquals(NodeState.POSSIBLE_DAMAGE_BELOW, nodeState.getState());
    reputationChangeTest(results);

    // lost ranged poll (should set NodeState to 'WRONG_NAMES')
    namePoll = createPoll(TEST_URL + "/branch1",
                             "a", "b", false, true, 5, 15);
    results = namePoll.getVoteTally();

    // - running
    pollState = new PollState(results.getType(),
                              results.getPollSpec().getLwrBound(),
                              results.getPollSpec().getUprBound(),
                              PollState.RUNNING,
                              results.getStartTime(),
                              Deadline.MAX,
                              false);
    nodeState.setState(NodeState.POSSIBLE_DAMAGE_BELOW);
    nodeManager.handleNamePoll(pollState, results, nodeState);
    assertEquals(PollState.REPAIRING, pollState.getStatus());
    assertEquals(NodeState.WRONG_NAMES, nodeState.getState());
    reputationChangeTest(results);
  }

  public void testHandleWrongNames() throws Exception {
    Vector masterV = new Vector();
    List localL = new ArrayList();
    String sharedUrl = "/test";
    String repairUrl = "/test2";
    String repairUrl2 = "/test3";
    String createUrl = "/test4";
    String sharedUrl2 = "/test5";
    String deleteUrl = "/test6";

    PollTally.NameListEntry entry = new PollTally.NameListEntry(false,
        sharedUrl);
    masterV.addElement(entry);
    localL.add(entry);
    entry = new PollTally.NameListEntry(true, repairUrl);
    masterV.addElement(entry);
    entry = new PollTally.NameListEntry(true, repairUrl2);
    masterV.addElement(entry);
    entry = new PollTally.NameListEntry(false, createUrl);
    masterV.addElement(entry);
    entry = new PollTally.NameListEntry(true, sharedUrl2);
    masterV.addElement(entry);
    localL.add(entry);
    entry = new PollTally.NameListEntry(true, deleteUrl);
    localL.add(entry);

    NodeState nodeState = nodeManager.getNodeState(getCUS(mau, TEST_URL));
    namePoll = createPoll(TEST_URL, false, true, 15, 5);
    PollTally results = namePoll.getVoteTally();
    // create node to be deleted
    RepositoryNode delNode = TestRepositoryNodeImpl.createLeaf(
        theDaemon.getLockssRepository(mau), TEST_URL + deleteUrl,
        "test stream", null);
    assertFalse(delNode.isDeleted());
    assertNull(theDaemon.getLockssRepository(mau).getNode(TEST_URL +
        createUrl));

    assertTrue(nodeManager.handleWrongNames(masterV.iterator(), localL,
                                            nodeState, results));
    // first repair scheduled
    assertEquals(MockCrawlManager.SCHEDULED, crawlManager.getUrlStatus(
        TEST_URL + repairUrl));
    // second repair scheduled
    assertEquals(MockCrawlManager.SCHEDULED, crawlManager.getUrlStatus(
        TEST_URL + repairUrl2));
    // node created
    assertNotNull(theDaemon.getLockssRepository(mau).getNode(TEST_URL +
        createUrl));
    // node deleted
    assertTrue(delNode.isDeleted());
  }

  public void testHandleAuPolls() throws Exception {
    // have to change the AUCUS for the MockArchivalUnit here
    // to properly test handling AuNode content polls
    MockCachedUrlSet mcus = (MockCachedUrlSet) getCUS(mau,
        new AUCachedUrlSetSpec());
    Vector auChildren = new Vector();
    mcus.setFlatItSource(auChildren);
    mau.setAUCachedUrlSet(mcus);

    nodeManager.createNodeState(mcus);

    // test with an inconclusive poll
    contentPoll = createPoll(mcus.getUrl(), true, true, 5, 5);
    PollTally results = contentPoll.getVoteTally();
    PollSpec spec = results.getPollSpec();

    AuState auState = nodeManager.getAuState();
    assertEquals(-1, auState.getLastTopLevelPollTime());

    NodeStateImpl nodeState = (NodeStateImpl)nodeManager.getNodeState(mcus);
    // test that error polls don't update last poll time
    PollState pollState = new PollState(results.getType(),
                                        spec.getLwrBound(),
                                        spec.getUprBound(),
                                        PollState.ERR_IO,
                                        results.getStartTime(),
                                        Deadline.MAX,
                                        false);
    nodeState.addPollState(pollState);
    nodeManager.updateState(nodeState, results);
    assertEquals(-1, auState.getLastTopLevelPollTime());

    // test that a finished top-level poll sets the time right
    contentPoll = createPoll(mcus.getUrl(), true, true, 15, 5);
    results = contentPoll.getVoteTally();
    spec = results.getPollSpec();

    TimeBase.setSimulated(TimeBase.nowMs());
    nodeState = (NodeStateImpl)nodeManager.getNodeState(mcus);
    pollState = new PollState(results.getType(),
                              spec.getLwrBound(),
                              spec.getUprBound(),
                              PollState.RUNNING,
                              results.getStartTime(),
                              Deadline.MAX,
                              false);
    nodeState.addPollState(pollState);
    nodeManager.updateState(nodeState, results);
    assertEquals(PollState.WON, pollState.getStatus());
    assertEquals(TimeBase.nowMs(), auState.getLastTopLevelPollTime());

    // add some fake children
    auChildren.add(getCUS(mau, "testDir1"));
    auChildren.add(getCUS(mau, "testDir2"));
    mcus.setFlatItSource(auChildren);

    // test that won name poll calls content polls on Au children
    contentPoll = createPoll(mcus.getUrl(), false, true, 15, 5);
    results = contentPoll.getVoteTally();
    MockCachedUrlSet mcus2 = (MockCachedUrlSet) results.getCachedUrlSet();
    Vector subFiles = new Vector(2);
    subFiles.add(getCUS(mau, "testDir1"));
    subFiles.add(getCUS(mau, "testDir2"));
    mcus2.setFlatItSource(subFiles);
    pollState = new PollState(results.getType(),
                              spec.getLwrBound(),
                              spec.getUprBound(),
                              PollState.RUNNING,
                              results.getStartTime(),
                              Deadline.MAX,
                              false);
    nodeState.addPollState(pollState);
    nodeState.setState(NodeState.NAME_RUNNING);
    nodeManager.updateState(nodeState, results);
    assertEquals(PollState.WON, pollState.getStatus());
    assertEquals(TimeBase.nowMs(), auState.getLastTopLevelPollTime());
    assertEquals(MockPollManager.CONTENT_REQUESTED,
                 pollManager.getPollStatus("testDir1"));
    assertEquals(MockPollManager.CONTENT_REQUESTED,
                 pollManager.getPollStatus("testDir2"));
  }

  public void testCheckLastHistory() throws Exception {
    TimeBase.setSimulated(10000);

    NodeState nodeState = nodeManager.getNodeState(getCUS(mau, TEST_URL));

    // these are true if the pollmanager doesn't know about them
    historyCheckTestName(PollState.SCHEDULED, nodeState, true, true);
    historyCheckTestName(PollState.SCHEDULED, nodeState, false, false);

    historyCheckTestName(PollState.RUNNING, nodeState, true, true);
    historyCheckTestName(PollState.RUNNING, nodeState, false, false);

    historyCheckTestName(PollState.REPAIRING, nodeState, true, true);
    historyCheckTestName(PollState.REPAIRING, nodeState, false, false);

    // shouldn't act if it isn't our poll
    historyCheckTestName(Poll.NAME_POLL, PollState.UNFINISHED, nodeState,
                         false, false, false);
    historyCheckTestName(Poll.NAME_POLL, PollState.UNFINISHED, nodeState,
                         true, true, false);

    // these shouldn't act
    historyCheckTest(Poll.CONTENT_POLL, PollState.WON, nodeState, false);
    historyCheckTest(Poll.CONTENT_POLL, PollState.REPAIRED, nodeState, false);

    // this is true because we're going to try and fix unrepairable
    historyCheckTestName(PollState.UNREPAIRABLE, nodeState, true, false);

    // this false for lost polls
    historyCheckTest(Poll.CONTENT_POLL, PollState.LOST, nodeState, false);
    historyCheckTest(Poll.NAME_POLL, PollState.LOST, nodeState, false);

    // this is true, since we called the poll (since it's used for ranged polls)
    historyCheckTestName(PollState.ERR_IO, nodeState, true, false);

    // shouldn't do anything to this poll since it's expiration date isn't up
    // (created with starttime 123 and duration 123)
    TimeBase.setSimulated(150);
    historyCheckTestName(PollState.RUNNING, nodeState, false, false);

    TimeBase.setReal();
  }

  public void testCheckCurrentState() throws Exception {
    TimeBase.setSimulated(10000);
    NodeStateImpl nodeState = (NodeStateImpl)nodeManager.getNodeState(
        getCUS(mau, TEST_URL));

    // no action for following:
    stateCheckTest(nodeState, Poll.CONTENT_POLL, false, false, false, false, true);
    nodeState.setState(NodeState.OK);
    stateCheckTest(nodeState, Poll.CONTENT_POLL, false, false, false,  false, true);
    nodeState.setState(NodeState.POSSIBLE_DAMAGE_BELOW);
    stateCheckTest(nodeState, Poll.CONTENT_POLL, false, false, false,  false, true);

    // change to 'needs poll' version
    nodeState.setState(NodeState.UNREPAIRABLE_NAMES);
    stateCheckTest(nodeState, Poll.CONTENT_POLL, false, false, false,  false, true);
    assertEquals(NodeState.UNREPAIRABLE_NAMES_NEEDS_POLL, nodeState.getState());
    nodeState.setState(NodeState.UNREPAIRABLE_SNCUSS);
    stateCheckTest(nodeState, Poll.CONTENT_POLL, false, false, false,  false, true);
    assertEquals(NodeState.UNREPAIRABLE_SNCUSS_NEEDS_POLL, nodeState.getState());

    // schedule content polls
    nodeState.setState(NodeState.NEEDS_POLL);
    stateCheckTest(nodeState, Poll.CONTENT_POLL, false, true, false,  false, true);
    //- change to 'NEEDS_POLL'
    nodeState.setState(NodeState.CONTENT_RUNNING);
    stateCheckTest(nodeState, Poll.CONTENT_POLL, false, true, false,  true, true);
    assertEquals(NodeState.NEEDS_POLL, nodeState.getState());
    nodeState.setState(NodeState.CONTENT_REPLAYING);
    stateCheckTest(nodeState, Poll.CONTENT_POLL, false, true, false,  true, true);
    assertEquals(NodeState.NEEDS_POLL, nodeState.getState());

    //- change to 'POSSIBLE_DAMAGE_HERE'
    nodeState.setState(NodeState.SNCUSS_POLL_RUNNING);
    stateCheckTest(nodeState, Poll.CONTENT_POLL, false, true, false,  true, true);
    assertEquals(NodeState.POSSIBLE_DAMAGE_HERE, nodeState.getState());
    nodeState.setState(NodeState.SNCUSS_POLL_REPLAYING);
    stateCheckTest(nodeState, Poll.CONTENT_POLL, false, true, false,  true, true);
    assertEquals(NodeState.POSSIBLE_DAMAGE_HERE, nodeState.getState());

    nodeState.setState(NodeState.POSSIBLE_DAMAGE_HERE);
    stateCheckTest(nodeState, Poll.NAME_POLL, false, true, false,  false, true);
    nodeState.setState(NodeState.UNREPAIRABLE_SNCUSS_NEEDS_POLL);
    stateCheckTest(nodeState, Poll.CONTENT_POLL, false, true, false,  true, true);


    // schedule name polls
    nodeState.setState(NodeState.CONTENT_LOST);
    stateCheckTest(nodeState, Poll.CONTENT_POLL, true, false, false,  false, true);
    nodeState.setState(NodeState.UNREPAIRABLE_NAMES_NEEDS_POLL);
    stateCheckTest(nodeState, Poll.NAME_POLL, true, false, false,  true, true);

    //- change to 'CONTENT_LOST'
    nodeState.setState(NodeState.NAME_RUNNING);
    stateCheckTest(nodeState, Poll.NAME_POLL, true, false, false,  true, true);
    assertEquals(NodeState.CONTENT_LOST, nodeState.getState());
    nodeState.setState(NodeState.NAME_REPLAYING);
    stateCheckTest(nodeState, Poll.NAME_POLL, true, false, false,  true, true);
    assertEquals(NodeState.CONTENT_LOST, nodeState.getState());

    // running poll (not mine)
    // should do nothing and change to 'INITIAL'
    nodeState.setState(NodeState.CONTENT_RUNNING);
    stateCheckTest(nodeState, Poll.CONTENT_POLL, false, false, false,  true, false);
    assertEquals(NodeState.INITIAL, nodeState.getState());

    // mark for repair
    nodeState.setState(NodeState.NEEDS_REPAIR);
    stateCheckTest(nodeState, Poll.CONTENT_POLL, false, false, true, false, true);

    // act on list of names
    String deleteUrl = TEST_URL + "/testentry2.html";
    RepositoryNode delNode = TestRepositoryNodeImpl.createLeaf(
        theDaemon.getLockssRepository(mau), deleteUrl, "test stream", null);
    nodeState.setState(NodeState.WRONG_NAMES);
    stateCheckTest(nodeState, Poll.NAME_POLL, false, false, true,  false, true);
    assertTrue(delNode.isDeleted());

    // history only, so recall name poll
    nodeState.setState(NodeState.WRONG_NAMES);
    stateCheckTest(nodeState, Poll.NAME_POLL, true, false, false,  true, true);

    // subdivide and recurse, plus SNCUSS
    // change to 'POSSIBLE_DAMAGE_HERE'
    nodeState.setState(NodeState.DAMAGE_AT_OR_BELOW);
    stateCheckTest(nodeState, Poll.NAME_POLL, false, true, false,  false, true);
    assertEquals(NodeState.POSSIBLE_DAMAGE_HERE, nodeState.getState());

    TimeBase.setReal();
  }

  public void testStatusRegistration() {
    assertTrue(nodeManager.registeredAccessors);

    NodeManagerImpl nodeManager2 = new NodeManagerImpl(new MockArchivalUnit());
    assertTrue(nodeManager2.registeredAccessors);
    nodeManager2.initService(theDaemon);
    nodeManager2.startService();

    nodeManager2.stopService();
    assertFalse(nodeManager2.registeredAccessors);
  }

  private void stateCheckTest(NodeState node, int pollType,
                              boolean shouldScheduleName,
                              boolean shouldScheduleContent,
                              boolean shouldRepair, boolean isHistory,
                              boolean isOurPoll)
      throws Exception {
    PollState pollState = null;
    PollTally results = null;
    if (isHistory) {
      pollState = new PollHistory(pollType, null, null,
                             PollState.RUNNING, 123,
                             123, null, isOurPoll);
    } else {
      Poll poll = createPoll(TEST_URL, (pollType==Poll.CONTENT_POLL),
                             isOurPoll, 15, 5);
      results = poll.getVoteTally();
      pollManager.thePolls.remove(TEST_URL);

      pollState = new PollState(results.getType(),
                                poll.getPollSpec().getLwrBound(),
                                poll.getPollSpec().getUprBound(),
                                PollState.RUNNING,
                                results.getStartTime(),
                                Deadline.MAX,
                                isOurPoll);
    }

    if (shouldScheduleName || shouldScheduleContent || shouldRepair) {
      assertTrue(nodeManager.checkCurrentState(pollState, results, node, true));
      assertNull(pollManager.getPollStatus(TEST_URL));
      assertTrue(nodeManager.checkCurrentState(pollState, results, node, false));
      if (shouldScheduleName) {
        assertEquals(MockPollManager.NAME_REQUESTED,
                     pollManager.getPollStatus(TEST_URL));
      } else if (shouldScheduleContent) {
        assertEquals(MockPollManager.CONTENT_REQUESTED,
                     pollManager.getPollStatus(TEST_URL));
      } else if (shouldRepair) {
        if (node.getState()==NodeState.NEEDS_REPAIR) {
          assertEquals(MockCrawlManager.SCHEDULED, crawlManager.getUrlStatus(
              TEST_URL));
        } else if (node.getState()==NodeState.WRONG_NAMES) {
          String repairUrl = TEST_URL + "/testentry4.html";
          String repairUrl2 = TEST_URL + "/testentry5.html";
          assertEquals(MockCrawlManager.SCHEDULED, crawlManager.getUrlStatus(
              repairUrl));
          assertNull(crawlManager.getUrlStatus(repairUrl2));
        }
      }
      pollManager.thePolls.remove(TEST_URL);
    } else {
      assertFalse(nodeManager.checkCurrentState(pollState, results, node, false));
      assertNull(pollManager.getPollStatus(TEST_URL));
    }
  }

  private void historyCheckTestName(int pollState, NodeState node,
                                boolean shouldSchedule, boolean dontRemove) {
    historyCheckTestName(Poll.NAME_POLL, pollState, node, true, shouldSchedule,
                         dontRemove);
  }

  private void historyCheckTest(int pollType, int pollState, NodeState node,
                                boolean shouldSchedule) {
    historyCheckTestName(pollType, pollState, node, true, shouldSchedule,
                         false);
  }

  private void historyCheckTestName(int pollType, int pollState, NodeState node,
                                boolean ourPoll, boolean shouldSchedule,
                                boolean dontRemove) {
    PollHistory history = new PollHistory(pollType, null, null,
                                          pollState, 123,
                                          123, null,
                                          ourPoll);
    if (shouldSchedule) {
      assertTrue(nodeManager.checkLastHistory(history, node, true));
      assertNull(pollManager.getPollStatus(TEST_URL));
      assertTrue(nodeManager.checkLastHistory(history, node, false));
      assertEquals(MockPollManager.NAME_REQUESTED,
                   pollManager.getPollStatus(TEST_URL));
    } else {
      assertFalse(nodeManager.checkLastHistory(history, node, false));
    }
    if (!dontRemove) {
      pollManager.thePolls.remove(TEST_URL);
    }
  }

  private void reputationChangeTest(PollTally results) {
    Iterator voteIt = results.getPollVotes().iterator();
    while (voteIt.hasNext()) {
      Vote vote = (Vote) voteIt.next();
      int repChange = IdentityManager.AGREE_VOTE;
      if (!vote.isAgreeVote()) {
        repChange = IdentityManager.DISAGREE_VOTE;
      }
      assertEquals(repChange, idManager.lastChange(vote.getIDAddress()));
    }
  }

  static CachedUrlSet getCUS(MockArchivalUnit mau, String url) {
    return new MockCachedUrlSet(mau, new RangeCachedUrlSetSpec(url));
  }

  static CachedUrlSet getCUS(MockArchivalUnit mau, CachedUrlSetSpec spec) {
    return new MockCachedUrlSet(mau, spec);
  }

  static CachedUrlSet makeFakeCachedUrlSet(MockArchivalUnit mau,
                                           String startUrl,
                                           int numBranches,
                                           int numFiles) throws Exception {
    Vector files = new Vector(numFiles * numBranches);
    Vector branches = new Vector(numBranches);

    for (int ib = 1; ib <= numBranches; ib++) {
      String b_url = TEST_URL + File.separator + "branch" + ib;
      MockCachedUrlSet mcus = new MockCachedUrlSet(mau,
          new RangeCachedUrlSetSpec(b_url));
      Vector subFiles = new Vector(numFiles);
      Vector subBranches = new Vector(numFiles);
      for (int ix = 1; ix <= numFiles; ix++) {
        String f_url = b_url + File.separator + "file" + ix + ".doc";
        MockCachedUrl cu = new MockCachedUrl(f_url);
        files.add(cu);
        subFiles.add(cu);
        MockCachedUrlSet subMcus = new MockCachedUrlSet(mau,
            new RangeCachedUrlSetSpec(f_url));
        subBranches.add(subMcus);
        subMcus.addUrl("test string" + ix, f_url);
        subMcus.setFlatItSource(new ArrayList());
        subMcus.setHashItSource(new ArrayList());
      }
      mcus.addUrl("test string" + ib, b_url);
      mcus.setFlatItSource(subBranches);
      mcus.setHashItSource(subFiles);
      branches.add(mcus);
    }
    MockCachedUrlSet cus = new MockCachedUrlSet(mau,
                                                new RangeCachedUrlSetSpec(
        startUrl));
    cus.addUrl("test string", startUrl);
    cus.setHashItSource(files);
    cus.setFlatItSource(branches);
    return cus;
  }

  static CachedUrlSet makeFakeAuCachedUrlSet(MockArchivalUnit mau,
                                             String startUrl,
                                             int numBranches, int numFiles)
      throws Exception {
    MockCachedUrlSet auCus = new MockCachedUrlSet(mau,
                                                  new AUCachedUrlSetSpec());

    CachedUrlSet mcus = makeFakeCachedUrlSet(mau, startUrl, numBranches,
                                             numFiles);
    Vector childV = new Vector(1);
    childV.addElement(mcus);

    Vector treeV = new Vector(numBranches * numFiles + 1);
    Iterator treeIt = mcus.contentHashIterator();
    treeV.addElement(mcus);
    while (treeIt.hasNext()) {
      treeV.addElement(treeIt.next());
    }

    auCus.addUrl(null, AuUrl.PROTOCOL_COLON, false, false, null);
    auCus.setHashItSource(treeV);
    auCus.setFlatItSource(childV);
    return auCus;
  }

  static void loadNodeStates(ArchivalUnit au, NodeManagerImpl nodeManager) {
    // recurse through au cachedurlsets
    CachedUrlSet cus = au.getAUCachedUrlSet();
    recurseLoadCachedUrlSets(nodeManager, cus, au);
  }

  static void recurseLoadCachedUrlSets(NodeManagerImpl nodeManager,
                                       CachedUrlSet cus, ArchivalUnit au) {
    // add the nodeState for this cus
    nodeManager.createNodeState(cus);
    // recurse the set's children
    Iterator children = cus.flatSetIterator();
    while (children.hasNext()) {
      CachedUrlSetNode child = (CachedUrlSetNode) children.next();
      switch (child.getType()) {
        case CachedUrlSetNode.TYPE_CACHED_URL_SET:
          recurseLoadCachedUrlSets(nodeManager, (CachedUrlSet) child, au);
          break;
        case CachedUrlSetNode.TYPE_CACHED_URL:
          CachedUrlSetSpec rSpec = new RangeCachedUrlSetSpec(child.getUrl());
          CachedUrlSet newSet = ( (BaseArchivalUnit) au).makeCachedUrlSet(rSpec);
          nodeManager.createNodeState(newSet);
      }
    }
  }

  private Poll createPoll(String url, boolean isContentPoll, boolean isLocal,
                          int numAgree, int numDisagree) throws Exception {
    return createPoll(url, null, null, isContentPoll, isLocal, numAgree,
                      numDisagree);
  }
  private Poll createPoll(String url, String lwrBound, String uprBound,
                          boolean isContentPoll, boolean isLocal,
                          int numAgree, int numDisagree) throws Exception {
    LcapIdentity testID = null;
    LcapMessage testmsg = null;
    if (isLocal) {
      testID = idManager.getLocalIdentity();
    }
    else {
      try {
        InetAddress testAddr = InetAddress.getByName("123.3.4.5");
        testID = idManager.findIdentity(testAddr);
      }
      catch (UnknownHostException ex) {
        fail("can't open test host");
      }
    }

    byte[] bytes = new byte[20];
    random.nextBytes(bytes);

    try {

      testmsg = LcapMessage.makeRequestMsg(
          new PollSpec(mau.getAUId(),
                       url, lwrBound, uprBound, null),
          null,
          bytes,
          bytes,
          (isContentPoll ? LcapMessage.CONTENT_POLL_REQ :
           LcapMessage.NAME_POLL_REQ),
          123321,
          testID);
    }
    catch (IOException ex) {
      fail("can't create test name message" + ex.toString());
    }
    log.debug("daemon = " + theDaemon);

    Poll p = TestPoll.createCompletedPoll(theDaemon, mau,
                                          testmsg, numAgree, numDisagree);
    TestHistoryRepositoryImpl.configHistoryParams(tempDirPath);

    return p;
  }

  public static void main(String[] argv) {
    String[] testCaseList = { TestNodeManagerImpl.class.getName()};
    junit.swingui.TestRunner.main(testCaseList);
  }
}
