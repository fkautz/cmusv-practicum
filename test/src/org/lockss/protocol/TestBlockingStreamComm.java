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

package org.lockss.protocol;

import java.util.*;
import java.io.*;
import java.net.*;
import junit.framework.*;
import org.lockss.config.Configuration;
import org.lockss.daemon.*;
import org.lockss.plugin.*;
import org.lockss.util.*;
import org.lockss.util.Queue;
import org.lockss.test.*;

/**
 * This is the test class for org.lockss.protocol.BlockingStreamComm
 */
public class TestBlockingStreamComm extends LockssTestCase {
  public static Class testedClasses[] = {
    BlockingStreamComm.class,
    BlockingPeerChannel.class,
  };

  static Logger log = Logger.getLogger("TestBlockingStreamComm");

  // limit on number of BlockingStreamComm instances, and number created by
  // testMultipleChannels
  static final int MAX_COMMS = 5;

  static final int HEADER_LEN = PeerChannel.HEADER_LEN;
  static final int HEADER_CHECK = PeerChannel.HEADER_CHECK;
  static final int HEADER_OFF_CHECK = PeerChannel.HEADER_OFF_CHECK;
  static final int HEADER_OFF_OP = PeerChannel.HEADER_OFF_OP;
  static final int HEADER_OFF_LEN = PeerChannel.HEADER_OFF_LEN;
  static final int HEADER_OFF_PROTO = PeerChannel.HEADER_OFF_PROTO;
  static final byte OP_PEERID = PeerChannel.OP_PEERID;
  static final byte OP_DATA = PeerChannel.OP_DATA;

  // Arrays store multiple instances of test objects (ports, PeerIds,
  // queues, etc).  Usually used in parallel (comms[n] has local id
  // pids[n], on port testports[n]

  int[] testports = new int[MAX_COMMS];
  PeerIdentity[] pids = new PeerIdentity[MAX_COMMS];
  PeerAddress.Tcp[] pads = new PeerAddress.Tcp[MAX_COMMS];
  MyBlockingStreamComm[] comms = new MyBlockingStreamComm[MAX_COMMS];
  SimpleQueue[] rcvdMsgss = new SimpleQueue[MAX_COMMS];

  // Convenience equivalents to [1] and [2] entries in arrays
  int testport1, testport2;
  PeerIdentity pid1, pid2;
  PeerAddress.Tcp pad1, pad2;
  MyBlockingStreamComm comm1, comm2;
  SimpleQueue rcvdMsgs1, rcvdMsgs2;

  String testStr1 = "This is test data 1";
  String testStr2 = "This message contains a null \000 character";
  String testStr3 =
    "They that can give up essential liberty to obtain " +
    "a little temporary safety deserve neither liberty nor safety.";

  PeerMessage msg1, msg2, msg3;

  // buffers for manually sending to or receiving from an scomm under test
  private byte[] rcvHeader = new byte[HEADER_LEN];
  private byte[] peerbuf = new byte[BlockingPeerChannel.MAX_PEERID_LEN];

  private MockLockssDaemon daemon;
  private IdentityManager idmgr;

  Properties cprops;			// some tests add more to this and
					// reconfigure
  SimpleBinarySemaphore sem;
  SimpleQueue assocQ;
  boolean useInternalSockets = false;

  TestBlockingStreamComm(String name) {
    super(name);
  }

  void addSuiteProps(Properties p) {
  }

  public void setUp() throws Exception {
    super.setUp();
    sem = new SimpleBinarySemaphore();
    assocQ = new SimpleQueue.Fifo();
    daemon = getMockLockssDaemon();
    String tempDirPath = null;
    try {
      tempDirPath = getTempDir().getAbsolutePath() + File.separator;
    }
    catch (IOException ex) {
      fail("unable to create a temporary directory");
    }
    cprops = new Properties();
    addSuiteProps(cprops);
    cprops.setProperty(IdentityManager.PARAM_IDDB_DIR, tempDirPath + "iddb");
    cprops.setProperty(IdentityManager.PARAM_LOCAL_IP, "127.0.0.1");
    ConfigurationUtil.setCurrentConfigFromProps(cprops);
    idmgr = new MyIdentityManager();
    daemon.setIdentityManager(idmgr);
    idmgr.initService(daemon);
    daemon.setDaemonInited(true);
//    idmgr.startService();
    setupMessages();
    useInternalSockets(false);
  }

  public void tearDown() throws Exception {
    for (int comm = 0; comm < MAX_COMMS; comm++) {
      stopComm(comm);
    }
    if (idmgr != null) {
      idmgr.stopService();
      idmgr = null;
    }
    super.tearDown();
    TimeBase.setReal();
  }

  void stopComm(int ix) {
    if (comms[ix] != null) {
      comms[ix].stopService();
      comms[ix] = null;
    }
  }

  void useInternalSockets(boolean flg) {
    useInternalSockets = flg;
  }

  void setupPid(int ix) throws IOException {
    if (useInternalSockets) {
      setupInternalPid(ix);
    } else {
      setupRealPid(ix);
    }
  }

  /** Create a PeerIdentity for a port on localhost
   */
  void setupRealPid(int ix) throws IOException {
    if (pids[ix] == null) {
      testports[ix] = findUnboundTcpPort();
      pids[ix] = findPeerId("127.0.0.1", testports[ix]);
      pads[ix] = (PeerAddress.Tcp)pids[ix].getPeerAddress();
      peerhack(ix);
    }
  }

  PeerIdentity findPeerId(String addr, int port) {
    String id = IdentityManager.ipAddrToKey(addr, port);
    return idmgr.findPeerIdentity(id);
  }

  /** Create a PeerIdentity for an InternalSocket
   */
  void setupInternalPid(int ix) throws IOException {
    if (pids[ix] == null) {
      InetAddress ip = InternalSocket.internalInetAddr;
      String addr = ip.getHostAddress();
      testports[ix] = InternalServerSocket.findUnboundPort(2000);
      pids[ix] = findPeerId(addr, testports[ix]);
      pads[ix] = (PeerAddress.Tcp)pids[ix].getPeerAddress();
      peerhack(ix);
    }
  }

  void peerhack(int ix) {
    switch (ix) {
    case 1:
      testport1 = testports[ix];
      pid1 = pids[ix];
      pad1 = pads[ix];
      break;
    case 2:
      testport2 = testports[ix];
      pid2 = pids[ix];
      pad2 = pads[ix];
      break;
    default:
    }
  }

  /** Create and start BlockingStreamComm instance ix, register message
   * handlers for it
   */
  void setupComm(int ix) throws IOException {
    if (pids[ix] == null) setupPid(ix);
    comms[ix] = new MyBlockingStreamComm(pids[ix]);
    comms[ix].initService(daemon);
    comms[ix].startService();
    rcvdMsgss[ix] = new SimpleQueue.Fifo();
    for (int proto = 1; proto <= 3; proto++) {
      comms[ix].registerMessageHandler(proto,
				       new MessageHandler(rcvdMsgss[ix]));
    }
    commhack(ix);
  }

  void commhack(int ix) {
    switch (ix) {
    case 1:
      comm1 = comms[ix];
      rcvdMsgs1 = rcvdMsgss[ix];
      break;
    case 2:
      comm2 = comms[ix];
      rcvdMsgs2 = rcvdMsgss[ix];
      break;
    default:
    }
  }

  void setupComm1() throws IOException {
    setupComm(1);
  }

  void setupComm2() throws IOException {
    setupComm(2);
  }

  int nextPort = 2000;
  
  int findUnboundTcpPort() {
    for (int p = nextPort; p < 65535; p++) {
      try {
	ServerSocket sock = new ServerSocket(p);
	sock.close();
	nextPort = p + 1;
	return p;
      } catch (IOException e) {
      }
    }
    log.error("Couldn't find unused TCP port");
    return -1;
  }

  void setupMessages() throws IOException {
    msg1 = makePeerMessage(1, testStr1);
    msg2 = makePeerMessage(2, testStr2);
    msg3 = makePeerMessage(3, testStr3);
  }

  PeerMessage makePeerMessage(int proto) throws IOException {
    PeerMessage pm = new MyMemoryPeerMessage();
    pm.setProtocol(proto);
    return pm;
  }

  PeerMessage makePeerMessage(int proto, String data) throws IOException {
    return makePeerMessage(proto, data.getBytes());
  }

  PeerMessage makePeerMessage(int proto, byte[] data) throws IOException {
    PeerMessage pm = makePeerMessage(proto);
    OutputStream os = pm.getOutputStream();
    os.write(data);
    os.close();
    return pm;
  }

  PeerMessage makePeerMessage(int proto, String data, int rpt)
      throws IOException {
    PeerMessage pm = makePeerMessage(proto);
    byte[] bdata = data.getBytes();
    OutputStream os = pm.getOutputStream();
    for (int ix = rpt; ix > 0; ix--) {
      os.write(bdata);
    }
    os.close();
    return pm;
  }

  /** Write a peerid message to an output stream */
  void writePeerId(OutputStream outs, PeerIdentity pid) throws IOException {
    writePeerId(outs, pid.getIdString());
  }

  /** Write a peerid message to an output stream */
  void writePeerId(OutputStream outs, String idstr) throws IOException {
    writeHeader(outs, OP_PEERID, idstr.length(), 0);
    outs.write(idstr.getBytes());
    outs.flush();
  }

  /** Write a message header to an output stream */
  void writeHeader(OutputStream outs, int op, int len, int proto)
      throws IOException {
    byte[] sndHeader = new byte[HEADER_LEN];
    sndHeader[HEADER_OFF_CHECK] = HEADER_CHECK;
    sndHeader[HEADER_OFF_OP] = (byte)op;
    ByteArray.encodeInt(len, sndHeader, HEADER_OFF_LEN);
    ByteArray.encodeInt(proto, sndHeader, HEADER_OFF_PROTO);
    outs.write(sndHeader);
  }

  /** Read header from input stream, check that it's a message header */
  public void assertRcvHeader(InputStream ins, int op) throws IOException {
    StreamUtil.readBytes(ins, rcvHeader, HEADER_LEN);
    assertHeaderOp(rcvHeader, op);
  }

  /** Read message data from input stream, return as String */
  String rcvMsgData(InputStream ins) throws IOException {
    int len = ByteArray.decodeInt(rcvHeader, 2);
    StreamUtil.readBytes(ins, peerbuf, len);
    return new String(peerbuf, 0, len);
  }

  /** Assert that buf contains a valid header */
  public void assertHeader(byte[] buf) {
    assertEquals(HEADER_CHECK, buf[HEADER_OFF_CHECK]);
  }

  /** Assert that buf contains a valid header with expected op */
  public void assertHeaderOp(byte[] buf, int op) {
    assertHeader(buf);
    assertEquals(op, buf[HEADER_OFF_OP]);
  }

  /** Assert that messages are the same except for sender id */
  public static void assertEqualsButSender(PeerMessage expected,
					   PeerMessage actual) {
    if (expected == actual ||
	(expected != null && expected.equalsButSender(actual))) {
      return;
    }
    failNotEquals(null, expected, actual);
  }

  /** Assert that message is same as expected, with expected sender */
  public void assertEqualsMessageFrom(PeerMessage expectedMsg,
				      PeerIdentity expectedPid,
				      PeerMessage actualMsg) {
    assertNotNull("Null message", actualMsg);
    assertEquals(expectedPid, actualMsg.getSender());
    assertEqualsButSender(expectedMsg, actualMsg);
  }

  // Tests of BlockingPeerChannel

  public void testStateTrans() throws IOException {
    setupComm1();
    BlockingPeerChannel chan =
      new BlockingPeerChannel(comm1, pid1, null, null);
    assertEquals(0, chan.getState());
    assertFalse(chan.stateTrans(1, 2));
    assertEquals(0, chan.getState());
    assertTrue(chan.stateTrans(0, 3));
    assertEquals(3, chan.getState());
    assertTrue(chan.stateTrans(3, 4, "shouldn't"));
    assertEquals(4, chan.getState());
    try {
      chan.stateTrans(3, 4, "should error");
      fail("stateTrans should have thrown");
    } catch (IllegalStateException e) {
    }
    // array version of stateTrans() nyi
//     int[] lst = {2, 4, 6};
//     assertTrue(chan.stateTrans(lst, 6, "shouldn't"));
//     assertTrue(chan.stateTrans(lst, 8, "shouldn't"));
//     assertFalse(chan.stateTrans(lst, 8));
//     assertEquals(8, chan.getState());
  }

  public void testNotStateTrans() throws IOException {
    setupComm1();
    BlockingPeerChannel chan =
      new BlockingPeerChannel(comm1, pid1, null, null);
    assertEquals(0, chan.getState());
    assertFalse(chan.notStateTrans(0, 2));
    assertEquals(0, chan.getState());
    assertTrue(chan.notStateTrans(1, 3));
    assertEquals(3, chan.getState());
    assertTrue(chan.notStateTrans(2, 5, "shouldn't"));
    assertEquals(5, chan.getState());
    try {
      chan.notStateTrans(5, 4, "should error");
      fail("notStateTrans should have thrown");
    } catch (IllegalStateException e) {
    }
    assertEquals(5, chan.getState());
    int[] lst = {2, 4, 6};
    assertTrue(chan.notStateTrans(lst, 3, "shouldn't"));
    assertTrue(chan.notStateTrans(lst, 4, "shouldn't"));
    assertFalse(chan.notStateTrans(lst, 8));
    assertEquals(4, chan.getState());
  }

  public void testReadHeader() throws IOException {
    setupComm1();
    byte[] hdr = {(byte)0xff, 1, 1, 2, 3, 4, 2, 3, 4, 5};
    assertEquals(HEADER_LEN, hdr.length);
    byte[] buf = new byte[HEADER_LEN];
    InputStream ins = new ByteArrayInputStream(hdr);
    BlockingPeerChannel chan = new BlockingPeerChannel(comm1, pid1, ins, null);
    long t0 = chan.getLastActiveTime();
    assertTrue(chan.readHeader());
    // ensure using a readbytes() that updates the time
    assertNotEquals(t0, chan.getLastActiveTime());

    hdr[0] = 42;
    ins = new ByteArrayInputStream(hdr);
    chan = new BlockingPeerChannel(comm1, pid1, ins, null);
    try {
      chan.readHeader();
      fail("readHeader() of illegal header should throw");
    } catch (ProtocolException e) {
    }

    ins = new StringInputStream("");
    chan = new BlockingPeerChannel(comm1, pid1, ins, null);
    assertFalse(chan.readHeader());

    ins = new StringInputStream("\177");
    chan = new BlockingPeerChannel(comm1, pid1, ins, null);
    try {
      chan.readHeader();
      fail("readHeader() of partial header should throw");
    } catch (ProtocolException e) {
    }
  }

  public void testWriteHeader() throws IOException {
    setupComm1();
    ByteArrayOutputStream outs = new ByteArrayOutputStream();
    BlockingPeerChannel chan = new BlockingPeerChannel(comm1, pid1,
						       null, outs);
    chan.writeHeader(5, 16, 34);
    byte[] hdr = outs.toByteArray();
    assertEquals(HEADER_LEN, hdr.length);
    assertEquals(0xff, ByteArray.decodeByte(hdr, 0));
    assertEquals(5, hdr[1]);
    assertEquals(16, ByteArray.decodeInt(hdr, 2));
    assertEquals(34, ByteArray.decodeInt(hdr, 6));

  }

  // BlockingStreamComm functional tests

  public void testIllSend() throws IOException {
    setupComm1();
    try {
      comm1.sendTo(null, pid2, null);
      fail("Null message should throw");
    } catch (NullPointerException e) {
    }
    try {
      comm1.sendTo(msg1, null, null);
      fail("Null peer should throw");
    } catch (NullPointerException e) {
    }
  }

  public void testRefused() throws IOException {
    List event;
    setupComm1();
    setupPid(2);
    comm1.setAssocQueue(assocQ);
    comm1.sendTo(msg1, pid2, null);
    assertNotNull("Connecting channel didn't dissociate",
		  (event = (List)assocQ.get(TIMEOUT_SHOULDNT)));
    assertEquals("Connecting channel didn't dissociate",
		 "dissoc", event.get(0));
    assertEmpty(comm1.channels);
    assertEmpty(comm1.rcvChannels);
  }

  public void testIncoming() throws IOException {
    setupComm1();
    Interrupter intr1 = interruptMeIn(TIMEOUT_SHOULDNT);
    Socket sock = new Socket(pad1.getIPAddr().getInetAddr(), pad1.getPort());
    SockAbort intr2 = abortIn(TIMEOUT_SHOULDNT, sock);
    InputStream ins = sock.getInputStream();
    StreamUtil.readBytes(ins, rcvHeader, HEADER_LEN);
    assertHeaderOp(rcvHeader, OP_PEERID);
    assertEquals(pid1.getIdString(), rcvMsgData(ins));
    intr1.cancel();
    intr2.cancel();
  }

  public void testIncomingRcvPeerId(String peerid, boolean isGoodId)
      throws IOException {
    log.debug("Incoming rcv pid " + peerid); 
    setupComm1();
    Interrupter intr1 = interruptMeIn(TIMEOUT_SHOULDNT);
    Socket sock = new Socket(pad1.getIPAddr().getInetAddr(), pad1.getPort());
    SockAbort intr2 = abortIn(TIMEOUT_SHOULDNT, sock);
    InputStream ins = sock.getInputStream();
    OutputStream outs = sock.getOutputStream();
    StreamUtil.readBytes(ins, rcvHeader, HEADER_LEN);
    assertHeaderOp(rcvHeader, OP_PEERID);
    assertEquals(pid1.getIdString(), rcvMsgData(ins));
    comm1.setAssocQueue(assocQ);
    writePeerId(outs, peerid);
    List event;
    if (isGoodId) {
      assertNotNull("Connecting channel didn't associate",
		    (event = (List)assocQ.get(TIMEOUT_SHOULDNT)));
      assertEquals("Connecting channel didn't associate",
		   "assoc", event.get(0));
      assertEquals(1, comm1.channels.size());
      assertEquals(0, comm1.rcvChannels.size());
    } else {
      assertNotNull("Connecting channel didn't dissociate",
		    (event = (List)assocQ.get(TIMEOUT_SHOULDNT)));
      assertEquals("Connecting channel didn't dissociate",
		   "dissoc", event.get(0));
      assertEquals(0, comm1.channels.size());
      assertEquals(0, comm1.rcvChannels.size());
    }
    intr1.cancel();
    intr2.cancel();
  }

  public void testIncomingRcvGoodPeerId1() throws IOException {
    setupPid(2);
    testIncomingRcvPeerId(pid2.getIdString(), true);
  }

  public void testIncomingRcvBadPeerId1() throws IOException {
    // illegal tcp port
    int bogusport = 0x10005;
    String bogus1 = findPeerId("127.0.0.1", bogusport).getIdString();
    testIncomingRcvPeerId(bogus1, false);
  }

  public void testIncomingRcvBadPeerId2() throws IOException {
    // V1 (non-stream) id
    testIncomingRcvPeerId("127.0.0.1", false);
  }

  public void testOriginate() throws IOException {
    setupComm1();
    setupPid(2);
    ServerSocket server = new ServerSocket(pad2.getPort());
    SockAbort intr = abortIn(TIMEOUT_SHOULDNT, server);
    comm1.findOrMakeChannel(pid2);
    Socket sock = server.accept();
    InputStream ins = sock.getInputStream();
    SockAbort intr2 = abortIn(TIMEOUT_SHOULDNT, sock);
    assertRcvHeader(ins, OP_PEERID);
    assertEquals(pid1.getIdString(), rcvMsgData(ins));
    IOUtil.safeClose(server);
    IOUtil.safeClose(sock);
    intr.cancel();
    intr2.cancel();
  }

  public void testOriginateToBadPeerId() throws IOException {
    setupComm1();
    PeerIdentity bad1 = findPeerId("127.0.0.1", 100000);
    try {
      comm1.sendTo(msg1, bad1, null);
      fail("sendTo(..., " + bad1 + ") should throw");
    } catch (IdentityManager.MalformedIdentityKeyException e) {
    }
  }

  public void testOriginateRcvPeerId(String peerid, boolean isGoodId)
      throws IOException {
    log.debug("Orig, send pid " + peerid);
    setupComm1();
    comm1.setAssocQueue(assocQ);
    setupPid(2);
    log.debug2("Listening on " + pad2.getPort());
    ServerSocket server = new ServerSocket(pad2.getPort());
    SockAbort intr = abortIn(TIMEOUT_SHOULDNT, server);
    comm1.findOrMakeChannel(pid2);
    Socket sock = server.accept();
    InputStream ins = sock.getInputStream();
    OutputStream outs = sock.getOutputStream();
    assertRcvHeader(ins, OP_PEERID);
    assertEquals(pid1.getIdString(), rcvMsgData(ins));
    writePeerId(outs, peerid);
    List event;
    if (isGoodId) {
      assertNull("Connecting channel shouldn't call associate",
		 assocQ.get(TIMEOUT_SHOULD));
      assertEquals(1, comm1.channels.size());
      assertEquals(0, comm1.rcvChannels.size());
    } else {
      assertNotNull("Connecting channel didn't dissociate",
		    (event = (List)assocQ.get(TIMEOUT_SHOULDNT)));
      assertEquals("Connecting channel didn't dissociate",
		   "dissoc", event.get(0));
      assertEquals(0, comm1.channels.size());
      assertEquals(0, comm1.rcvChannels.size());
    }
    IOUtil.safeClose(server);
    IOUtil.safeClose(sock);
    intr.cancel();
  }

  public void testOriginateRcvGoodPeerId1() throws IOException {
    setupPid(2);
    testOriginateRcvPeerId(pid2.getIdString(), true);
  }

  public void testOriginateRcvBadPeerId1() throws IOException {
    // illegal tcp port
    int bogusport = 0x10005;
    String bogus1 = findPeerId("127.0.0.1", bogusport).getIdString();
    testOriginateRcvPeerId(bogus1, false);
  }

  // any illegal pid sent to originator causes conflict (can't be equal to
  // actual address used to open conn) before legality check, so this is
  // equivalent to the previous test
  public void testOriginateRcvBadPeerId2() throws IOException {
    // V1 (non-stream) id
    testOriginateRcvPeerId("127.0.0.1", false);
  }

  // Don't know how to test this without actually waiting for socket to timeout
  public void XXXtestHangingClose() throws IOException {
    TimeBase.setSimulated(1000);
    cprops.setProperty(BlockingStreamComm.PARAM_CHANNEL_IDLE_TIME, "5000");
    ConfigurationUtil.setCurrentConfigFromProps(cprops);
    setupComm1();
    setupPid(2);
    Interrupter intr1 = interruptMeIn(TIMEOUT_SHOULDNT);
    Socket sock = new Socket(pad1.getIPAddr().getInetAddr(), pad1.getPort());
    SockAbort intr2 = abortIn(TIMEOUT_SHOULDNT, sock);
    InputStream ins = sock.getInputStream();
    OutputStream outs = sock.getOutputStream();
    StreamUtil.readBytes(ins, rcvHeader, HEADER_LEN);
    assertHeaderOp(rcvHeader, OP_PEERID);
    assertEquals(pid1.getIdString(), rcvMsgData(ins));
    comm1.setAssocQueue(assocQ);
    writePeerId(outs, pid2);
    // wait for it to get peerid before sending to it
    List event;
    assertNotNull("Channel didn't assoc",
		  (event = (List)assocQ.get(TIMEOUT_SHOULDNT)));
    assertEquals("Channel didn't assoc",
		 "assoc", event.get(0));
    assertEquals(1, comm1.channels.size());
    msg1 = makePeerMessage(1, "1234567890123456789012345678901234567890", 100);
    int msgsize = msg1.getDataSize();
    int tobuffer = 20 * (sock.getReceiveBufferSize() +
			sock.getSendBufferSize());
    for (int bytes = 0; bytes < tobuffer; bytes += msgsize) {
//       comm1.sendTo(msg1, pid2, null);
    }
    assertEquals(1, comm1.channels.size());
    BlockingPeerChannel chan = (BlockingPeerChannel)comm1.channels.get(pid2);
    assertNotNull("Didn't find expected channel", chan);
//     assertFalse("Send queue shouldn't be empty", chan.isSendIdle());
    assertEquals(0, comm1.rcvChannels.size());
    TimeBase.step(6000);
    assertEquals(0, ins.available());
    assertNotNull("Channel didn't close automatically after timeout",
		  (event = (List)assocQ.get(TIMEOUT_SHOULDNT)));
    assertEquals("Channel didn't close automatically after timeout",
		 "dissoc", event.get(0));
    assertFalse(intr1.did());
    assertFalse(intr2.did());
    intr1.cancel();
    intr2.cancel();
  }

  public void testHangingSend() throws IOException {
    TimeBase.setSimulated(1000);
    cprops.setProperty(BlockingStreamComm.PARAM_CHANNEL_IDLE_TIME, "5000");
    ConfigurationUtil.setCurrentConfigFromProps(cprops);
    setupComm1();
    setupPid(2);
    Interrupter intr1 = interruptMeIn(TIMEOUT_SHOULDNT);
    Socket sock = new Socket(pad1.getIPAddr().getInetAddr(), pad1.getPort());
    SockAbort intr2 = abortIn(TIMEOUT_SHOULDNT * 2, sock);
    InputStream ins = sock.getInputStream();
    OutputStream outs = sock.getOutputStream();
    StreamUtil.readBytes(ins, rcvHeader, HEADER_LEN);
    assertHeaderOp(rcvHeader, OP_PEERID);
    assertEquals(pid1.getIdString(), rcvMsgData(ins));
    comm1.setAssocQueue(assocQ);
    writePeerId(outs, pid2);
    // wait for it to get peerid before sending to it
    List event;
    assertNotNull("Channel didn't assoc",
		  (event = (List)assocQ.get(TIMEOUT_SHOULDNT)));
    assertEquals("Channel didn't assoc",
		 "assoc", event.get(0));
    assertEquals(1, comm1.channels.size());
    // a 30KB message
    msg1 = makePeerMessage(1, "123456789012345678901234567890", 1000);
    int msgsize = msg1.getDataSize();
    int tobuffer = 200 * (sock.getReceiveBufferSize() +
			sock.getSendBufferSize());
    // Send lots of data to ensure send thread blocks waiting for socket to
    // have buffer space
    for (int bytes = 0; bytes < tobuffer; bytes += msgsize) {
      comm1.sendTo(msg1, pid2, null);
    }
    assertEquals(1, comm1.channels.size());
    BlockingPeerChannel chan = (BlockingPeerChannel)comm1.channels.get(pid2);
    assertNotNull("Didn't find expected channel", chan);
    assertFalse("Send queue shouldn't be empty", chan.isSendIdle());
    assertEquals(0, comm1.rcvChannels.size());

    // give hung checker a chance to run.  If fails, interrupter will stop us
    while (null == (event = (List)assocQ.get(10))) {
      TimeBase.step(6000);
    }
    assertNotNull("Channel didn't close automatically after timeout", event);
    assertEquals("Channel didn't close automatically after timeout",
		 "dissoc", event.get(0));
    assertFalse(intr1.did());
    assertFalse(intr2.did());
    intr1.cancel();
    intr2.cancel();
  }

  public void testSingleConnect() throws IOException {
    PeerMessage msgIn;
    setupComm1();
    setupComm2();
    comm1.sendTo(msg1, pid2, null);
    msgIn = (PeerMessage)rcvdMsgs2.get(TIMEOUT_SHOULDNT);
    assertEqualsMessageFrom(msg1, pid1, msgIn);
    // redundant checks
    assertEquals(1, msgIn.getProtocol());
    assertEquals(testStr1.length(), msgIn.getDataSize());
    assertEquals(testStr1.length(), msg1.getDataSize());

    // each comm should have only one channel
    assertEquals(1, comm1.channels.size());
    assertEquals(1, comm2.channels.size());
    assertEquals(0, comm1.rcvChannels.size());
    assertEquals(0, comm2.rcvChannels.size());

    assertTrue(rcvdMsgs1.isEmpty());
    assertTrue(rcvdMsgs2.isEmpty());

    comm1.sendTo(msg2, pid2, null);
    msgIn = (PeerMessage)rcvdMsgs2.get(TIMEOUT_SHOULDNT);
    assertEqualsMessageFrom(msg2, pid1, msgIn);

    comm2.sendTo(msg2, pid1, null);
    msgIn = (PeerMessage)rcvdMsgs1.get(TIMEOUT_SHOULDNT);
    assertNotNull(msgIn);
    assertEqualsMessageFrom(msg2, pid2, msgIn);

    comm2.sendTo(msg3, pid1, null);
    comm2.sendTo(msg1, pid1, null);
    comm1.sendTo(msg3, pid2, null);
    comm1.sendTo(msg2, pid2, null);

    // each comm should still have only one channel
    assertEquals(1, comm1.channels.size());
    assertEmpty(comm1.rcvChannels);
    assertEquals(1, comm2.channels.size());
    assertEmpty(comm2.rcvChannels);

    msgIn = (PeerMessage)rcvdMsgs1.get(TIMEOUT_SHOULDNT);
    assertEqualsMessageFrom(msg3, pid2, msgIn);
    msgIn = (PeerMessage)rcvdMsgs1.get(TIMEOUT_SHOULDNT);
    assertEqualsMessageFrom(msg1, pid2, msgIn);
    msgIn = (PeerMessage)rcvdMsgs2.get(TIMEOUT_SHOULDNT);
    assertEqualsMessageFrom(msg3, pid1, msgIn);
    msgIn = (PeerMessage)rcvdMsgs2.get(TIMEOUT_SHOULDNT);
    assertEqualsMessageFrom(msg2, pid1, msgIn);
  }

  public void testFileMessage() throws IOException {
    cprops.setProperty(BlockingStreamComm.PARAM_MIN_FILE_MESSAGE_SIZE, "1000");
    ConfigurationUtil.setCurrentConfigFromProps(cprops);

    PeerMessage msgIn;
    setupComm1();
    setupComm2();
    msg2 = makePeerMessage(1, "1234567890123456789012345678901234567890", 100);
    comm1.sendTo(msg1, pid2, null);
    comm1.sendTo(msg2, pid2, null);
    msgIn = (PeerMessage)rcvdMsgs2.get(TIMEOUT_SHOULDNT);
    assertEqualsMessageFrom(msg1, pid1, msgIn);
    assertTrue(msgIn.toString(), msgIn instanceof MemoryPeerMessage);
    msgIn = (PeerMessage)rcvdMsgs2.get(TIMEOUT_SHOULDNT);
    assertEqualsMessageFrom(msg2, pid1, msgIn);
    assertTrue(msgIn.toString(), msgIn instanceof FilePeerMessage);
  }

  public void testMaxMsg() throws IOException {
    cprops.setProperty(BlockingStreamComm.PARAM_MAX_MESSAGE_SIZE, "2000");
    ConfigurationUtil.setCurrentConfigFromProps(cprops);

    PeerMessage msgIn;
    setupComm1();
    setupComm2();
    List event;
    comm2.setAssocQueue(assocQ);
    comm1.sendTo(msg1, pid2, null);
    msg2 = makePeerMessage(1, "1234567890123456789012345678901234567890", 100);
    msgIn = (PeerMessage)rcvdMsgs2.get(TIMEOUT_SHOULDNT);
    assertEqualsMessageFrom(msg1, pid1, msgIn);
    assertNotNull("Channel didn't associate",
		  (event = (List)assocQ.get(TIMEOUT_SHOULDNT)));
    assertEquals("Channel didn't associate",
		 "assoc", event.get(0));
    comm1.sendTo(msg2, pid2, null);
    assertNotNull("Channel didn't close on too-large message",
		  (event = (List)assocQ.get(TIMEOUT_SHOULDNT)));
    assertEquals("Channel didn't close automatically after timeout",
		 "dissoc", event.get(0));
  }

  // force delayed connect in one direction
  public void testSimultaneousConnect1() throws IOException {
    PeerMessage msgIn;
    SimpleBinarySemaphore sem2 = new SimpleBinarySemaphore();
    setupComm1();
    setupComm2();
    // delay comm2's accept()
    comm2.setAcceptSem(sem2);
    comm1.sendTo(msg1, pid2, null);
    comm2.sendTo(msg2, pid1, null);
    // both should have one connecting channel
    assertEquals(1, comm1.channels.size());
    assertEquals(1, comm2.channels.size());
    // comm1 should receive message
    msgIn = (PeerMessage)rcvdMsgs1.get(TIMEOUT_SHOULDNT);
    assertEqualsMessageFrom(msg2, pid2, msgIn);
    assertEquals(1, comm1.rcvChannels.size());
    // comm2 shouldn't
    assertTrue(rcvdMsgs2.isEmpty());
    sem2.give();
    // now comm2 should receive
    msgIn = (PeerMessage)rcvdMsgs2.get(TIMEOUT_SHOULDNT);
    assertEqualsMessageFrom(msg1, pid1, msgIn);
  }

  // force delayed connect in both directions
  public void testSimultaneousConnect2() throws IOException {
    PeerMessage msgIn;
    SimpleBinarySemaphore sem1 = new SimpleBinarySemaphore();
    SimpleBinarySemaphore sem2 = new SimpleBinarySemaphore();
    setupComm1();
    setupComm2();
    // delay accept() in both channels
    comm1.setAcceptSem(sem1);
    comm2.setAcceptSem(sem2);
    comm1.sendTo(msg1, pid2, null);
    comm2.sendTo(msg2, pid1, null);
    // both should have one connecting channel
    assertEquals(1, comm1.channels.size());
    assertEquals(1, comm2.channels.size());
    assertEquals(0, comm1.rcvChannels.size());
    assertEquals(0, comm2.rcvChannels.size());
    assertTrue(rcvdMsgs1.isEmpty());
    assertTrue(rcvdMsgs2.isEmpty());
    // allow comm1 accept to proceed, it should receive message
    sem1.give();
    msgIn = (PeerMessage)rcvdMsgs1.get(TIMEOUT_SHOULDNT);
    // comm1 only should have secondary channel
    assertEqualsMessageFrom(msg2, pid2, msgIn);
    assertEquals(1, comm1.channels.size());
    assertEquals(1, comm2.channels.size());
    assertEquals(1, comm1.rcvChannels.size());
    assertEquals(0, comm2.rcvChannels.size());
    // allow comm2 to proceed
    sem2.give();
    msgIn = (PeerMessage)rcvdMsgs2.get(TIMEOUT_SHOULDNT);
    assertEqualsMessageFrom(msg1, pid1, msgIn);
    assertEquals(1, comm1.channels.size());
    assertEquals(1, comm2.channels.size());
    assertEquals(1, comm1.rcvChannels.size());
    assertEquals(1, comm2.rcvChannels.size());
  }

  // allow channel to timeout and close after use
  public void testChannelCloseAfterTimeout() throws IOException {
    TimeBase.setSimulated(1000);
    PeerMessage msgIn;
    cprops.setProperty(BlockingStreamComm.PARAM_CHANNEL_IDLE_TIME, "5000");
    ConfigurationUtil.setCurrentConfigFromProps(cprops);
    setupComm1();
    setupComm2();
    comm1.setAssocQueue(assocQ);
    comm1.sendTo(msg1, pid2, null);
    msgIn = (PeerMessage)rcvdMsgs2.get(TIMEOUT_SHOULDNT);
    comm2.sendTo(msg2, pid1, null);
    msgIn = (PeerMessage)rcvdMsgs1.get(TIMEOUT_SHOULDNT);

    // Must ensure we don't step time until after channel has calculated
    // the send wait time.  Set up a post-calcSendWaitTime semaphore, send
    // another message, wait on sem, them step time.
    MyBlockingPeerChannel chan =
      (MyBlockingPeerChannel)comm1.channels.get(pid2);
    assertNotNull(chan);
    
    assertNotNull(sem);
    chan.setCalcSendWaitSem(sem);
    comm1.sendTo(msg1, pid2, null);
    msgIn = (PeerMessage)rcvdMsgs2.get(TIMEOUT_SHOULDNT);
    assertTrue(sem.take(TIMEOUT_SHOULDNT));

    TimeBase.step(4000);
    assertEquals(1, comm1.channels.size());
    TimeBase.step(2000);
    List event;
    assertNotNull("Channel didn't close automatically after timeout",
		  (event = (List)assocQ.get(TIMEOUT_SHOULDNT)));
    assertEquals("Channel didn't close automatically after timeout",
		 "dissoc", event.get(0));
    assertEquals(0, comm1.channels.size());
    assertEquals(0, comm1.rcvChannels.size());
  }

  // read (so) timeout should abort channel
  public void testReadTimeout() throws IOException {
    TimeBase.setSimulated(1000);
    PeerMessage msgIn;
    cprops.setProperty(BlockingStreamComm.PARAM_CHANNEL_IDLE_TIME, "10h");
    cprops.setProperty(BlockingStreamComm.PARAM_DATA_TIMEOUT, "100");
    ConfigurationUtil.setCurrentConfigFromProps(cprops);
    setupComm1();
    setupComm2();
    comm1.setAssocQueue(assocQ);
    comm1.sendTo(msg1, pid2, null);
    msgIn = (PeerMessage)rcvdMsgs2.get(TIMEOUT_SHOULDNT);
    assertEqualsMessageFrom(msg1, pid1, msgIn);
    List event;
    assertNotNull("Channel didn't close automatically after timeout",
		  (event = (List)assocQ.get(TIMEOUT_SHOULDNT)));
    assertEquals("Channel didn't close automatically after timeout",
		 "dissoc", event.get(0));
    assertEquals(0, comm1.channels.size());
    assertEquals(0, comm1.rcvChannels.size());
  }

  // create MAX_COMMS comm instances, have each of them sent messages to
  // each
  public void testMultipleChannels() throws IOException {
    for (int comm = 0; comm < MAX_COMMS; comm++) {
      setupComm(comm);
    }
    for (int comm = 0; comm < MAX_COMMS; comm++) {
      for (int peer = 0; peer < MAX_COMMS; peer++) {
	String data =
	  pids[comm].getIdString() + ">" + pids[peer].getIdString();
	PeerMessage pm = makePeerMessage(1, data);
	comms[comm].sendTo(pm, pids[peer], null);
      }
    }
    for (int comm = 0; comm < MAX_COMMS; comm++) {
      Set peers = allPeers();
      while (!peers.isEmpty()) {
	PeerMessage msgIn = (PeerMessage)rcvdMsgss[comm].get(TIMEOUT_SHOULDNT);
	assertNotNull("Comm" + comm + " didn't receive messages from " + peers,
		      msgIn);
	peers.remove(msgIn.getSender());
      }
    }
  }

  Set allPeers() {
    return SetUtil.fromArray(pids);
  }

  class MyBlockingStreamComm extends BlockingStreamComm {
    SocketFactory sockFact;
    PeerIdentity localId;
    SimpleQueue assocEvents;
    SimpleBinarySemaphore acceptSem;

    MyBlockingStreamComm(PeerIdentity localId) {
      this.localId = localId;
      sockFact = new MySocketFactory();
    }

    SocketFactory getSocketFactory() {
      return sockFact;
    }

    protected PeerIdentity getLocalPeerIdentity() {
      return localId;
    }

    void associateChannelWithPeer(BlockingPeerChannel chan,
				  PeerIdentity peer) {
      super.associateChannelWithPeer(chan, peer);
      if (assocEvents != null) {
	assocEvents.put(ListUtil.list("assoc", this));
      }
    }
    void dissociateChannelFromPeer(BlockingPeerChannel chan,
				   PeerIdentity peer) {
      super.dissociateChannelFromPeer(chan, peer);
      if (assocEvents != null) {
	assocEvents.put(ListUtil.list("dissoc", this));
      }
    }

    void processIncomingConnection(Socket sock) throws IOException {
      if (acceptSem != null) {
	acceptSem.take();
      }
      super.processIncomingConnection(sock);
    }

    void setAssocQueue(SimpleQueue sem) {
      assocEvents = sem;
    }
    void setAcceptSem(SimpleBinarySemaphore sem) {
      acceptSem = sem;
    }

    /** Socket factory creates either real or internal sockets, and
     * MyBlockingPeerChannels.
     */
    class MySocketFactory implements BlockingStreamComm.SocketFactory {

      public ServerSocket newServerSocket(int port, int backlog)
	  throws IOException {
	return (useInternalSockets ? new InternalServerSocket(port, backlog)
		: new ServerSocket(port, backlog));
      }

      public Socket newSocket(IPAddr addr, int port) throws IOException {
	return (useInternalSockets ? new InternalSocket(addr.getInetAddr(), port)
		: new Socket(addr.getInetAddr(), port));
      }

      public BlockingPeerChannel newPeerChannel(BlockingStreamComm comm,
						Socket sock)
	  throws IOException {
	return new MyBlockingPeerChannel(comm, sock);
      }

      public BlockingPeerChannel newPeerChannel(BlockingStreamComm comm,
						PeerIdentity peer)
	  throws IOException {
	return new MyBlockingPeerChannel(comm, peer);
      }

    }

  }

  static class MyBlockingPeerChannel extends BlockingPeerChannel {
    volatile SimpleBinarySemaphore stopSem;
    volatile SimpleBinarySemaphore calcSendWaitSem;

    MyBlockingPeerChannel(BlockingStreamComm scomm, PeerIdentity peer) {
      super(scomm, peer);
    }

    MyBlockingPeerChannel(BlockingStreamComm scomm, Socket sock) {
      super(scomm, sock);
    }

    void stopChannel() {
      super.stopChannel();
      if (stopSem != null) {
	stopSem.give();
      }
    }
    void setStopSem(SimpleBinarySemaphore sem) {
      stopSem = sem;
    }

    long calcSendWaitTime() {
      long res = super.calcSendWaitTime();
      if (calcSendWaitSem != null) {
	calcSendWaitSem.give();
      }
      return res;
    }
    void setCalcSendWaitSem(SimpleBinarySemaphore sem) {
      calcSendWaitSem = sem;
    }
  }

  class MessageHandler implements BlockingStreamComm.MessageHandler {
    SimpleQueue queue;
    public MessageHandler(SimpleQueue queue) {
      this.queue = queue;
    }
    public void handleMessage(PeerMessage msg) {
      log.debug("handleMessage(" + msg + ")");
      queue.put(msg);
    }
  }

  static class MyIdentityManager extends IdentityManager {
    public void storeIdentities() throws ProtocolException {
    }
  }

  // Suppress delete() because it prevents comparison with messages that
  // have been sent
  static class MyMemoryPeerMessage extends MemoryPeerMessage {
    boolean isDeleted = false;
    public void delete() {
      isDeleted = true;
    }
  }

  SockAbort abortIn(long inMs, Socket sock) {
    SockAbort sa = new SockAbort(inMs, sock);
    if (Boolean.getBoolean("org.lockss.test.threadDump")) {
      sa.setThreadDump();
    }
    sa.start();
    return sa;
  }

  SockAbort abortIn(long inMs, ServerSocket sock) {
    SockAbort sa = new SockAbort(inMs, sock);
    if (Boolean.getBoolean("org.lockss.test.threadDump")) {
      sa.setThreadDump();
    }
    sa.start();
    return sa;
  }

  /** SockAbort aborts a socket by closing it
   */
  class SockAbort extends DoLater {
    Socket sock;
    ServerSocket servsock;

    SockAbort(long waitMs, Socket sock) {
      super(waitMs);
      this.sock = sock;
    }

    SockAbort(long waitMs, ServerSocket servsock) {
      super(waitMs);
      this.servsock = servsock;
    }

    protected void doit() {
      try {
	if (sock != null) {
	  log.debug("Closing sock");
	  sock.close();
	}
      } catch (IOException e) {
	log.warning("sock", e);
      }
      try {
	if (servsock != null) {
	  log.debug("Closing servsock");
	  servsock.close();
	}
      } catch (IOException e) {
	log.warning("servsock", e);
      }
    }
  }

  // Variants:

  /** Buffered socket OutputStream */
  public static class Buff extends TestBlockingStreamComm {
    public Buff(String name) {
      super(name);
    }
    public void addSuiteProps(Properties p) {
      p.setProperty(BlockingStreamComm.PARAM_IS_BUFFERED_SEND, "true");
      p.setProperty(BlockingStreamComm.PARAM_TCP_NODELAY, "false");
    }
  }

  /** Unbuffered socket OutputStream */
  public static class UnBuff extends TestBlockingStreamComm {
    public UnBuff(String name) {
      super(name);
    }
    public void addSuiteProps(Properties p) {
      p.setProperty(BlockingStreamComm.PARAM_IS_BUFFERED_SEND, "false");
      p.setProperty(BlockingStreamComm.PARAM_TCP_NODELAY, "false");
    }
  }

  /** Buffered OutputStream, TCP_NODELAY */
  public static class BuffNoDelay extends TestBlockingStreamComm {
    public BuffNoDelay(String name) {
      super(name);
    }
    public void addSuiteProps(Properties p) {
      p.setProperty(BlockingStreamComm.PARAM_IS_BUFFERED_SEND, "true");
      p.setProperty(BlockingStreamComm.PARAM_TCP_NODELAY, "true");
    }
  }

  /** Unbuffered OutputStream, TCP_NODELAY */
  public static class UnBuffNoDelay extends TestBlockingStreamComm {
    public UnBuffNoDelay(String name) {
      super(name);
    }
    public void addSuiteProps(Properties p) {
      p.setProperty(BlockingStreamComm.PARAM_IS_BUFFERED_SEND, "false");
      p.setProperty(BlockingStreamComm.PARAM_TCP_NODELAY, "true");
    }
  }

  /** Channel threads run at high priority */
  public static class HighPri extends TestBlockingStreamComm {
    public HighPri(String name) {
      super(name);
    }
    public void addSuiteProps(Properties p) {
      String prop = "org.lockss.thread." +
	BlockingStreamComm.PRIORITY_PARAM_CHANNEL + ".priority";
      p.setProperty(prop, "5");
    }
  }


  public static Test suite() {
    return variantSuites(new Class[] {
      Buff.class,
//       UnBuff.class,
//       BuffNoDelay.class,
      UnBuffNoDelay.class,
      HighPri.class,
    });
  }
}
