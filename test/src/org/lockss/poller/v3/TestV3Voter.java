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

package org.lockss.poller.v3;

import java.io.*;
import java.util.Properties;
import org.lockss.app.*;
import org.lockss.config.ConfigManager;
import org.lockss.plugin.*;
import org.lockss.plugin.base.*;
import org.lockss.poller.*;
import org.lockss.poller.v3.*;
import org.lockss.protocol.*;
import org.lockss.repository.*;
import org.lockss.state.*;
import org.lockss.test.*;
import org.lockss.util.*;

import static org.lockss.util.Constants.*;

public class TestV3Voter extends LockssTestCase {
  
  V3Voter voter;
  MockLockssDaemon lockssDaemon;
  PeerIdentity repairRequestor;
  ArchivalUnit au;
  MockAuState aus;
  RepositoryNode repoNode;
  V3LcapMessage startMsg;
  

  String repairUrl = "http://www.example.com/foo/bar.html";
  
  public void setUp() throws Exception {
    super.setUp();
    repairRequestor = 
      new MockPeerIdentity("TCP:[192.168.0.100]:9723");
    lockssDaemon = getMockLockssDaemon();
    
    File tempDir = getTempDir();
    String tempDirPath = tempDir.getAbsolutePath();
    
    Properties p = new Properties();
    p.setProperty(IdentityManager.PARAM_IDDB_DIR, tempDirPath + "iddb");
    p.setProperty(LockssRepositoryImpl.PARAM_CACHE_LOCATION, tempDirPath);
    p.setProperty(IdentityManager.PARAM_LOCAL_IP, "127.0.0.1");
    p.setProperty(IdentityManager.PARAM_LOCAL_V3_IDENTITY, "TCP:[127.0.0.1]:9729");
    p.setProperty(ConfigManager.PARAM_NEW_SCHEDULER, "true");
    p.setProperty(V3Poller.PARAM_QUORUM, "3");
    p.setProperty(ConfigManager.PARAM_PLATFORM_DISK_SPACE_LIST, tempDirPath);
    p.setProperty(V3Serializer.PARAM_V3_STATE_LOCATION, tempDirPath);
    ConfigurationUtil.setCurrentConfigFromProps(p);
    
    startMsg = new V3LcapMessage("auid", "key", "1",
                                 ByteArray.makeRandomBytes(20),
                                 ByteArray.makeRandomBytes(20),
                                 V3LcapMessage.MSG_POLL,
                                 987654321,
                                 repairRequestor,
                                 tempDir, lockssDaemon);
    
    IdentityManager idmgr = lockssDaemon.getIdentityManager();
    idmgr.startService();
    lockssDaemon.getSchedService().startService();
    lockssDaemon.getDatagramRouterManager().startService();
    lockssDaemon.getRouterManager().startService();
    lockssDaemon.getSystemMetrics().startService();
    lockssDaemon.getPluginManager().startService();

    voter = new V3Voter(lockssDaemon, startMsg);

    // Create an AU
    au = new MockArchivalUnit(new MockPlugin(lockssDaemon));
    ((MockArchivalUnit)au).addUrl(repairUrl);

    // Create the repository
    MockLockssRepository repo = new MockLockssRepository("/foo", au);
    repoNode = repo.createNewNode(repairUrl);

    lockssDaemon.setLockssRepository(repo, au);

    aus = new MockAuState();
    MockNodeManager nodeManager = new MockNodeManager();
    getMockLockssDaemon().setNodeManager(nodeManager, au);
    nodeManager.setAuState(aus);
  }
  
  public void tearDown() throws Exception {
    super.tearDown();
  }
  
  /* 
   * Tests for V3Voter.serveRepairs(), which returns true iff the given URL
   * may be served as a repair to the specified peer.
   * 
   * The method relies on the value of the configuration property 
   * org.lockss.poll.v3.allowV3Repairs, as well as on the agreement history
   * of the URL in question.
   */
  
  public void testServeRepairsWithNoAgreementAndFalseProperty() {
    ConfigurationUtil.setFromArgs(V3Voter.PARAM_ALLOW_V3_REPAIRS, "false");
    ConfigurationUtil.addFromArgs(V3Voter.PARAM_ENABLE_PER_URL_AGREEMENT, "true");
    assertFalse(voter.serveRepairs(repairRequestor, au, repairUrl));
  }
  
  public void testServeRepairsWithNoAgreementAndTrueProperty() {
    ConfigurationUtil.setFromArgs(V3Voter.PARAM_ALLOW_V3_REPAIRS, "true");
    ConfigurationUtil.addFromArgs(V3Voter.PARAM_ENABLE_PER_URL_AGREEMENT, "true");
    assertFalse(voter.serveRepairs(repairRequestor, au, repairUrl));
  }
  
  public void testServeRepairsWithAgreementAndFalseProperty() {
    ConfigurationUtil.setFromArgs(V3Voter.PARAM_ALLOW_V3_REPAIRS, "false");
    ConfigurationUtil.addFromArgs(V3Voter.PARAM_ENABLE_PER_URL_AGREEMENT, "true");
    repoNode.signalAgreement(ListUtil.list(repairRequestor));
    assertFalse(voter.serveRepairs(repairRequestor, au, repairUrl));
  }

  public void testServeRepairsWithAgreementAndTrueProperty() {
    ConfigurationUtil.setFromArgs(V3Voter.PARAM_ALLOW_V3_REPAIRS, "true");
    ConfigurationUtil.addFromArgs(V3Voter.PARAM_ENABLE_PER_URL_AGREEMENT, "true");
    repoNode.signalAgreement(ListUtil.list(repairRequestor));
    assertTrue(voter.serveRepairs(repairRequestor, au, repairUrl));
  }

  public void testServeOpenAccessRepairs() {
    ConfigurationUtil.setFromArgs(V3Voter.PARAM_ALLOW_V3_REPAIRS, "true");
    ConfigurationUtil.addFromArgs(V3Voter.PARAM_ENABLE_PER_URL_AGREEMENT, "true");
    assertFalse(voter.serveRepairs(repairRequestor, au, repairUrl));
    aus.setAccessType(AuState.AccessType.OpenAccess);
    assertTrue(voter.serveRepairs(repairRequestor, au, repairUrl));
    ConfigurationUtil.addFromArgs(V3Voter.PARAM_OPEN_ACCESS_REPAIR_NEEDS_AGREEMENT,
				  "true");
    assertFalse(voter.serveRepairs(repairRequestor, au, repairUrl));
  }

  double nominateProb(long now, long lastVoteTime)
      throws Exception {
    String id = "tcp:[1.2.3.4]:4321";
    IdentityManager idMgr = lockssDaemon.getIdentityManager();
    PeerIdentity pid = idMgr.findPeerIdentity(id);
    idMgr.findLcapIdentity(pid, id);
    PeerIdentityStatus status = idMgr.getPeerIdentityStatus(pid);
    status.setLastVoterTime(lastVoteTime);
    TimeBase.setSimulated(now);
    return voter.nominateProb(status);
  }

  public void testNominateProb() throws Exception {
    // default is [10d,100],[30d,10],[40d,1]

    assertEquals(1.0, nominateProb(-1, 0));
    assertEquals(1.0, nominateProb(0, 0));
    assertEquals(1.0, nominateProb(10, 1));
    assertEquals(1.0, nominateProb(1, 10));
    
    assertEquals(1.0, nominateProb(10*DAY, 0));
    assertEquals(.55, nominateProb(20*DAY, 0));
    assertEquals(.1, nominateProb(30*DAY, 0));
    assertEquals(.01, nominateProb(40*DAY,0));

    ConfigurationUtil.addFromArgs(V3Voter.PARAM_NOMINATION_PROBABILITY_AGE_CURVE,
				  "[1w,100],[20w,10]");
    assertEquals(1.0, nominateProb(1*WEEK, 0));
    assertEquals(0.1, nominateProb(20*WEEK, 0));
  }

  static String PARAM_OVERHEAD_LOAD =
    org.lockss.scheduler.SortScheduler.PARAM_OVERHEAD_LOAD;

  public void testGetSchedDuration() {
    assertEquals(125, voter.getSchedDuration(100));
    ConfigurationUtil.addFromArgs(PARAM_OVERHEAD_LOAD, "40");
    assertEquals(500, voter.getSchedDuration(300));
  }

}
