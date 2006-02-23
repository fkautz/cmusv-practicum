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
import java.util.*;

import org.lockss.app.*;
import org.lockss.config.*;
import org.lockss.config.Configuration.*;
import org.lockss.hasher.*;
import org.lockss.plugin.*;
import org.lockss.poller.*;
import org.lockss.poller.v3.V3Serializer.*;
import org.lockss.protocol.*;
import org.lockss.repository.*;
import org.lockss.test.*;
import org.lockss.util.*;
import org.lockss.util.Queue;

public class FuncV3Voter extends LockssTestCase {

  private IdentityManager idmgr;
  private MockLockssDaemon theDaemon;

  private PeerIdentity pollerId;
  private PeerIdentity voterId;

  private String tempDirPath;
  private ArchivalUnit testau;
  private PollManager pollmanager;
  private HashService hashService;

  private V3LcapMessage msgPoll;
  private V3LcapMessage msgPollProof;
  private V3LcapMessage msgVoteRequest;
  private V3LcapMessage msgRepairRequest;
  private V3LcapMessage msgReceipt;

  private static final String BASE_URL = "http://www.test.org/";

  private static String[] urls = {
    "lockssau:",
    BASE_URL,
    BASE_URL + "index.html",
    BASE_URL + "file1.html",
    BASE_URL + "file2.html",
    BASE_URL + "file3.html",
    BASE_URL + "file4.html",
  };

  public void setUp() throws Exception {
    super.setUp();
    tempDirPath = null;
    tempDirPath = getTempDir().getAbsolutePath() + File.separator;
    startDaemon();
  }

  public void tearDown() throws Exception {
    stopDaemon();
    super.tearDown();
  }

  private void startDaemon() throws Exception {
    TimeBase.setSimulated();
    this.testau = setupAu();
    theDaemon = getMockLockssDaemon();
    Properties p = new Properties();
    p.setProperty(IdentityManager.PARAM_IDDB_DIR, tempDirPath + "iddb");
    p.setProperty(LockssRepositoryImpl.PARAM_CACHE_LOCATION, tempDirPath);
    p.setProperty(IdentityManager.PARAM_LOCAL_IP, "127.0.0.1");
    p.setProperty(ConfigManager.PARAM_NEW_SCHEDULER, "true");
    p.setProperty(V3Poller.PARAM_MIN_POLL_SIZE, "4");
    p.setProperty(V3Poller.PARAM_MAX_POLL_SIZE, "4");
    p.setProperty(V3PollFactory.PARAM_POLL_DURATION_MIN, "5m");
    p.setProperty(V3PollFactory.PARAM_POLL_DURATION_MAX, "6m");
    p.setProperty(V3Poller.PARAM_QUORUM, "3");
    p.setProperty(LcapStreamComm.PARAM_ENABLED, "true");
    p.setProperty(LcapDatagramComm.PARAM_ENABLED, "false");
    p.setProperty(IdentityManager.PARAM_LOCAL_V3_IDENTITY,
		  "TCP:[127.0.0.1]:3456");
    p.setProperty(ConfigManager.PARAM_PLATFORM_DISK_SPACE_LIST, tempDirPath);
    p.setProperty(IdentityManagerImpl.PARAM_INITIAL_PEERS,
                  "TCP:[127.0.0.2]:3456,TCP:[127.0.0.3]:3456,"
                  + "TCP:[127.0.0.4]:3456,TCP:[127.0.0.5]:3456,"
                  + "TCP:[127.0.0.6]:3456,TCP:[127.0.0.7]:3456");
    ConfigurationUtil.setCurrentConfigFromProps(p);
    idmgr = theDaemon.getIdentityManager();
    pollmanager = theDaemon.getPollManager();
    hashService = theDaemon.getHashService();
    theDaemon.setStreamCommManager(new MyMockStreamCommManager(theDaemon));
    theDaemon.setDatagramRouterManager(new MyMockLcapDatagramRouter());
    theDaemon.setRouterManager(new MyMockLcapRouter());
    theDaemon.setNodeManager(new MockNodeManager(), testau);
    theDaemon.setPluginManager(new MyMockPluginManager(theDaemon, testau));
    theDaemon.setDaemonInited(true);
    theDaemon.getSchedService().startService();
    theDaemon.getActivityRegulator(testau).startService();
    idmgr.startService();
    hashService.startService();
    pollmanager.startService();
    this.pollerId = idmgr.stringToPeerIdentity("127.0.0.1");
    this.msgPoll = makePollMsg();
    this.msgPollProof = makePollProofMsg();
    this.msgVoteRequest = makeVoteReqMsg();
    this.msgRepairRequest = makeRepairReqMsg();
    this.msgReceipt = makeReceiptMsg();
  }

  private void stopDaemon() throws Exception {
    theDaemon.getPollManager().stopService();
    theDaemon.getPluginManager().stopService();
    theDaemon.getActivityRegulator(testau).stopService();
    theDaemon.getSystemMetrics().stopService();
    theDaemon.getRouterManager().stopService();
    theDaemon.getDatagramRouterManager().stopService();
    theDaemon.getHashService().stopService();
    theDaemon.getSchedService().stopService();
    theDaemon.getIdentityManager().stopService();
    theDaemon.setDaemonInited(false);
    this.testau = null;
    TimeBase.setReal();
  }

  private MockArchivalUnit setupAu() {
    MockArchivalUnit mau = new MockArchivalUnit();
    mau.setAuId("mock");
    MockPlugin plug = new MockPlugin();
    mau.setPlugin(plug);
    MockCachedUrlSet cus = (MockCachedUrlSet)mau.getAuCachedUrlSet();
    List files = new ArrayList();
    for (int ix = 2; ix < urls.length; ix++) {
      MockCachedUrl cu = (MockCachedUrl)mau.addUrl(urls[ix], false, true);
      // Add mock file content.
      cu.setContent("This is content for CUSasdfasdfasdfadsfasdsassdfafile " + ix);
      files.add(cu);
    }
    cus.setHashItSource(files);
    cus.setFlatItSource(files);
    return mau;
  }

  public V3LcapMessage makePollMsg() {
    V3LcapMessage msg =
      new V3LcapMessage(V3LcapMessage.MSG_POLL,
                        "randomkeyforpoll",
                        pollerId,
                        "http://www.test.org",
                        123456789, 987654321,
                        ByteArray.makeRandomBytes(20),
                        ByteArray.makeRandomBytes(20));
    msg.setEffortProof(ByteArray.makeRandomBytes(20));
    return msg;
  }

  public V3LcapMessage makePollProofMsg() {
    V3LcapMessage msg =
      new V3LcapMessage(V3LcapMessage.MSG_POLL_PROOF,
                        "randomkeyforpoll",
                        pollerId,
			"http://www.test.org",
			123456789, 987654321,
			ByteArray.makeRandomBytes(20),
                        ByteArray.makeRandomBytes(20));
    msg.setEffortProof(ByteArray.makeRandomBytes(20));
    return msg;
  }

  public V3LcapMessage makeVoteReqMsg() {
    V3LcapMessage msg =
      new V3LcapMessage(V3LcapMessage.MSG_VOTE_REQ,
                        "randomkeyforpoll",
                        pollerId,
			"http://www.test.org",
			123456789, 987654321,
			ByteArray.makeRandomBytes(20),
                        ByteArray.makeRandomBytes(20));
    return msg;
  }

  public V3LcapMessage makeRepairReqMsg() {
    V3LcapMessage msg =
      new V3LcapMessage(V3LcapMessage.MSG_REPAIR_REQ,
                        "randomkeyforpoll",
                        pollerId,
			"http://www.test.org",
			123456789, 987654321,
			ByteArray.makeRandomBytes(20),
                        ByteArray.makeRandomBytes(20));
    return msg;
  }

  public V3LcapMessage makeReceiptMsg() {
    V3LcapMessage msg =
      new V3LcapMessage(V3LcapMessage.MSG_EVALUATION_RECEIPT,
                        "randomkeyforpoll",
                        pollerId,
			"http://www.test.org",
			123456789, 987654321,
			ByteArray.makeRandomBytes(20),
                        ByteArray.makeRandomBytes(20));
    return msg;
  }

  public void testNonRepairPoll() throws Exception {
    PollSpec ps = new PollSpec(testau.getAuCachedUrlSet(), null, null,
                               Poll.V1_CONTENT_POLL);
    byte[] introEffortProof = ByteArray.makeRandomBytes(20);
    MyMockV3Voter voter =
      new MyMockV3Voter(ps, theDaemon, pollerId,
                        "this_is_my_pollkey",
                        introEffortProof,
                        ByteArray.makeRandomBytes(20),
                        100000, "SHA-1");

    voter.startPoll();

    voter.receiveMessage(msgPoll);

    V3LcapMessage pollAck = voter.getSentMessage();
    assertNotNull(pollAck);
    assertEquals(pollAck.getOpcode(), V3LcapMessage.MSG_POLL_ACK);

    voter.receiveMessage(msgPollProof);

    V3LcapMessage nominate = voter.getSentMessage();
    assertNotNull(nominate);
    assertEquals(nominate.getOpcode(), V3LcapMessage.MSG_NOMINATE);

    voter.receiveMessage(msgVoteRequest);

    V3LcapMessage vote = voter.getSentMessage();
    assertNotNull(vote);
    assertEquals(vote.getOpcode(), V3LcapMessage.MSG_VOTE);

    /*
    voter.handleMessage(msgRepairRequest);
    voter.handleMessage(msgRepairRequest);
    voter.handleMessage(msgRepairRequest);
    */

    voter.receiveMessage(msgReceipt);
  }

  private class MyMockV3Voter extends V3Voter {
    private Queue sentMessages = new FifoQueue();

    public MyMockV3Voter(PollSpec spec, LockssDaemon daemon, PeerIdentity orig,
                         String key, byte[] introEffortProof,
                         byte[] pollerNonce, long duration, String hashAlg)
        throws PollSerializerException {
      super(spec, daemon, orig, key, introEffortProof, pollerNonce,
            duration, hashAlg);
    }

    public void sendMessageTo(V3LcapMessage msg, PeerIdentity id) {
      this.sentMessages.put(msg);
    }

    public V3LcapMessage getSentMessage() throws InterruptedException {
      log.debug2("Waiting for next message...");
      V3LcapMessage msg = (V3LcapMessage)sentMessages.get(Deadline.in(200));
      log.debug2("Got message: " + msg);
      return msg;
    }
  }

  public void testRestorePoll() throws Exception {
    PollSpec pollspec = new PollSpec(testau.getAuCachedUrlSet(),
                                     Poll.V3_POLL);
    V3LcapMessage myPollMsg =
      V3LcapMessage.makeRequestMsg(pollspec, "arandomkey",
                                   ByteArray.makeRandomBytes(20),
                                   ByteArray.makeRandomBytes(20),
                                   V3LcapMessage.MSG_POLL,
                                   120000,
                                   pollerId);
    PrivilegedAccessor.invokeMethod(pollmanager,
                                    "handleIncomingMessage",
                                    myPollMsg);
    Poll p1 = pollmanager.getPoll(myPollMsg.getKey());
    assertNotNull(p1);
    assertEquals(1, pollmanager.getV3Voters().size());
    stopDaemon();
    assertEquals(0, pollmanager.getV3Voters().size());
    startDaemon();
    assertEquals(1, pollmanager.getV3Voters().size());
    Poll p2 = pollmanager.getPoll(p1.getKey());
    assertNotNull(p2);
    assertEquals(p2.getKey(), p1.getKey());
    V3Voter p1V3 = (V3Voter)p1;
    V3Voter p2V3 = (V3Voter)p2;
    assertEquals(p1V3.getCallerID(), p2V3.getCallerID());
    assertEquals(p1V3.getDeadline(), p2V3.getDeadline());
    assertEquals(p1V3.getDuration(), p2V3.getDuration());
    assertEquals(p1V3.getStatusString(), p2V3.getStatusString());
    V3TestUtil.assertEqualVoterUserData(p1V3.getVoterUserData(),
                                        p2V3.getVoterUserData());
    theDaemon.getPluginManager().stopAu(testau);
  }

  class MyMockLcapRouter extends LcapRouter {
    public void registerMessageHandler(org.lockss.protocol.LcapRouter.MessageHandler handler) {
    }

    public void send(V1LcapMessage msg, ArchivalUnit au) throws IOException {
    }

    public void sendTo(V1LcapMessage msg, ArchivalUnit au, PeerIdentity id)
        throws IOException {
    }

    public void sendTo(V3LcapMessage msg, PeerIdentity id) throws IOException {
    }

    public void setConfig(Configuration config, Configuration oldConfig,
                          Differences changedKeys) {
    }

    public void startService() {
    }

    public void stopService() {
    }

    public void unregisterMessageHandler(org.lockss.protocol.LcapRouter.MessageHandler handler) {
    }
  }

  class MyMockLcapDatagramRouter extends LcapDatagramRouter {
    public void registerMessageHandler(MessageHandler handler) {
    }
    public void send(V1LcapMessage msg, ArchivalUnit au)
        throws IOException {
    }
    public void sendTo(V1LcapMessage msg, ArchivalUnit au, PeerIdentity id)
        throws IOException {
    }
    public void setConfig(Configuration config, Configuration oldConfig,
                          Differences changedKeys) {
    }
    public void startService() {
    }
    public void stopService() {
    }
    public void unregisterMessageHandler(MessageHandler handler) {
    }
  }

  class MyMockStreamCommManager extends BlockingStreamComm {
    private LockssDaemon theDaemon;

    public MyMockStreamCommManager(LockssDaemon daemon) {
      this.theDaemon = daemon;
    }
    public void sendTo(PeerMessage msg, PeerIdentity id,
                       RateLimiter limiter) throws IOException {
      log.debug("sendTo: id=" + id);
    }
    public void setConfig(Configuration config, Configuration prevConfig,
                          Differences changedKeys) {
    }
    public PeerMessage newPeerMessage() {
      throw new UnsupportedOperationException("Not implemented");
    }
    public PeerMessage newPeerMessage(int estSize) {
      throw new UnsupportedOperationException("Not implemented");
    }
    public void registerMessageHandler(int protocol, MessageHandler handler) {
      log.debug("MockStreamCommManager: registerMessageHandler");
    }
    public void unregisterMessageHandler(int protocol) {
      log.debug("MockStreamCommManager: unregisterMessageHandler");
    }
    public void startService() {
      log.debug("MockStreamCommManager: startService()");
    }
    public void stopService() {
      log.debug("MockStreamCommManager: stopService()");
    }
    public LockssDaemon getDaemon() {
      return theDaemon;
    }
    public void initService(LockssApp app) throws LockssAppException {
      log.debug("MockStreamCommManager: initService(app)");
    }
    public void initService(LockssDaemon daemon) throws LockssAppException {
      log.debug("MockStreamCommManager: initService(daemon)");
    }
    public LockssApp getApp() {
      log.debug("MockStreamCommManager: getApp()");
      return null;
    }
    protected boolean isAppInited() {
      return true;
    }
    protected void resetConfig() {
      log.debug("MockStreamCommManager: resetConfig()");
    }

  }

  class MyMockPluginManager extends PluginManager {
    ArchivalUnit au;
    LockssDaemon daemon;

    public MyMockPluginManager(LockssDaemon daemon, ArchivalUnit au) {
      this.daemon = daemon;
      this.au = au;
    }

    public LockssDaemon getDaemon() {
      return daemon;
    }

    public CachedUrlSet findCachedUrlSet(PollSpec spec) {
      return au.getAuCachedUrlSet();
    }

    public CachedUrlSet findCachedUrlSet(String auId) {
      return au.getAuCachedUrlSet();
    }
  }
}
