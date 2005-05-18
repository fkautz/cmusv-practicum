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

import java.io.*;
import java.net.*;
import java.util.*;
import org.lockss.util.*;
import org.lockss.util.Queue;
import org.lockss.config.Configuration;
import org.lockss.daemon.*;

/** Manages a stream connection to a peer.  Channels are ephemeral, coming
 * and going as needed.
 */
class BlockingPeerChannel implements PeerChannel {
  static Logger log = Logger.getLogger("Channel");

  static final int COPY_BUFFER_SIZE = 256;

  // Channel state
  static final int STATE_INIT = 0;
  static final int STATE_CONNECTING = 1;
  static final int STATE_CONNECT_FAIL = 2;
  static final int STATE_ACCEPTED = 3;
  static final int STATE_STARTING = 4;
  static final int STATE_OPEN = 5;
  static final int STATE_DRAIN_INPUT = 6;
  static final int STATE_DRAIN_OUTPUT = 7;
  static final int STATE_CLOSING = 8;
  static final int STATE_CLOSED = 9;

  int state;
  private Object stateLock = new Object();
  private boolean needSendPeerId = true;

  volatile private PeerIdentity peer;
  volatile private PeerAddress pad;
  private PeerIdentity localPeer;
  private BlockingStreamComm scomm;
  private Socket sock;
  private Queue rcvQueue;
  private Queue sendQueue;
  private InputStream ins;
  private OutputStream outs;
  private long lastSendTime = 0;
  private long lastRcvTime = 0;
  private long lastActiveTime = 0;
  private boolean rcvActive = false;
  volatile private ChannelRunner reader;
  volatile private ChannelRunner writer;
  volatile private ChannelRunner connecter;

  private byte[] rcvHeader = new byte[HEADER_LEN];
  private byte[] sndHeader = new byte[HEADER_LEN];
  private byte[] peerbuf = new byte[MAX_PEERID_LEN];

  private Stats stats = new Stats();

  /** All other constructors should call this one */
  private BlockingPeerChannel(BlockingStreamComm scomm) {
    this.scomm = scomm;
    localPeer = scomm.getMyPeerId();
    rcvQueue = scomm.getReceiveQueue();
    sendQueue = new FifoQueue();
  }

  /** Create a channel to be connected to the peer; doesn't attempt to
   * connect (yet)
   * @param scomm parent stream comm
   * @param peer the peer to talk to
   */
  BlockingPeerChannel(BlockingStreamComm scomm, PeerIdentity peer) {
    this(scomm);
    this.peer = peer;
    state = STATE_INIT;
  }

  /** Create a channel from an existing (incoming) connection socket.  The
   * peer identity is not yet known.
   * @param scomm parent stream comm
   * @param sock the socket open to the peer
   */
  BlockingPeerChannel(BlockingStreamComm scomm, Socket sock) {
    this(scomm);
    this.sock = sock;
    state = STATE_ACCEPTED;
  }

  /** Used by tests only */
  BlockingPeerChannel(BlockingStreamComm scomm, PeerIdentity peer,
		      InputStream ins, OutputStream outs) {
    this(scomm);
    this.peer = peer;
    this.ins = ins;
    this.outs = outs;
  }

  /** Return our peer, or null if not known yet */
//   public PeerIdentity getPeer() {
//     return peer;
//   }

  boolean stateTrans(int from, int to, String errmsg) {
    synchronized (stateLock) {
      if (state == from) {
	state = to;
	return true;
      } else if (errmsg != null) {
	throw new IllegalStateException(errmsg + " in state " + state);
      } else {
	return false;
      }
    }
  }

  boolean stateTrans(int from, int to) {
    return stateTrans(from, to, null);
  }

  boolean notStateTrans(int notFrom, int to, String errmsg) {
    synchronized (stateLock) {
      if (state != notFrom) {
	state = to;
	return true;
      } else if (errmsg != null) {
	throw new IllegalStateException(errmsg + " in state " + state);
      } else {
	return false;
      }
    }
  }

  boolean notStateTrans(int[] notFrom, int to, String errmsg) {
    synchronized (stateLock) {
      if (!isState(state, notFrom)) {
	state = to;
	return true;
      } else if (errmsg != null) {
	throw new IllegalStateException(errmsg + " in state " + state);
      } else {
	return false;
      }
    }
  }

  boolean notStateTrans(int notFrom, int to) {
    return notStateTrans(notFrom, to, null);
  }

  boolean notStateTrans(int[] notFrom, int to) {
    return notStateTrans(notFrom, to, null);
  }

  void assertState(int expectedState, String errmsg) {
    if (state != expectedState)
      throw new IllegalStateException(errmsg + " in state " + state +
				      ", expected " + expectedState);
  }

  boolean isState(int state, int[] oneOf) {
    for (int ix = 0; ix < oneOf.length; ix++) {
      if (state == oneOf[ix]) return true;
    }
    return false;
  }

  /** Start thread to connect to the peer and start channel threads */
  public void startOriginate() throws IOException {
    // arrange for possible MalformedIdentityKeyException to be thrown
    // here, not in thread
    pad = peer.getPeerAddress();
    if (!(pad instanceof PeerAddress.Tcp)) {
      throw new IllegalArgumentException("Unknown PeerAddress: " + pad);
    }
    if (stateTrans(STATE_INIT, STATE_CONNECTING, "startOriginate")) {
      ChannelConnecter runner = new ChannelConnecter();
      try {
	scomm.execute(runner);
	connecter = runner;
      } catch (Exception e) {
	log.warning("Can't start channel connecter", e);
	stateTrans(STATE_CONNECTING, STATE_CLOSED);
	throw new IOException(e.toString());
      }
    }
  }

  /** Start channel threads */
  public void startIncoming() throws IOException {
    if (stateTrans(STATE_ACCEPTED, STATE_STARTING, "startIncoming")) {
      startConnectedChannel();
    }
  }

  /** Initialize streams, start reader and writer threads */
  private void startConnectedChannel() throws IOException {
    assertState(STATE_STARTING, "startConnectedChannel");
    try {
      sock.setSoTimeout((int)scomm.getSoTimeout());
      ins = sock.getInputStream();
      outs = sock.getOutputStream();

      // writer must be started first as reader refers to writer thread (to
      // set name when peerid received)
      startWriter();
      startReader();
      stateTrans(STATE_STARTING, STATE_OPEN);
    } catch (IOException e) {
      log.error("Channel didn't start", e);
      abortChannel();
      throw e;
    } catch (Exception e) {
      log.error("Channel didn't start", e);
      abortChannel();
      throw new IOException(e.toString());
    }
  }

  void abortChannel() {
    stopChannel(true);
  }

  void stopChannel() {
    stopChannel(false);
  }

  static int[] stopIgnStates = {STATE_INIT, STATE_CLOSED, STATE_CLOSING};
  
  void stopChannel(boolean abort) {
    if (notStateTrans(stopIgnStates, STATE_CLOSING)) {
      if (abort && peer != null) log.warning("Aborting " + peer.getIdString());
      scomm.dissociateChannelFromPeer(this, peer);
      IOUtil.safeClose(sock);
      IOUtil.safeClose(ins);
      IOUtil.safeClose(outs);
      connecter = stopThread(connecter);
      reader = stopThread(reader);
      writer = stopThread(writer);
      stateTrans(STATE_CLOSING, STATE_CLOSED);
    }
  }

  private ChannelRunner stopThread(ChannelRunner runner) {
    if (runner != null) {
      runner.stopRunner();
    }
    return null;
  }

  /** Open a connection to our peer; start things running if it works */
  void connect(ChannelConnecter connector) {
    assertState(STATE_CONNECTING, "connect");
    if (pad instanceof PeerAddress.Tcp) {
      PeerAddress.Tcp tpad = (PeerAddress.Tcp)pad;
      try {
	sock = scomm.getSocketFactory().newSocket(tpad.getIPAddr(),
						  tpad.getPort());
	connector.cancelTimeout();
	log.debug2("Connected to " + peer);
      } catch (IOException e) {
	connector.cancelTimeout();
	log.warning("Connect failed to " + peer + ": " + e.toString());
	stateTrans(STATE_CONNECTING, STATE_CONNECT_FAIL);
	abortChannel();
	return;
      }
      try {
	stateTrans(STATE_CONNECTING, STATE_STARTING);
	startConnectedChannel();
      } catch (IOException ignore) {
      }
    } else {
      throw new IllegalArgumentException("Unknown PeerAddress: " + pad);
    }
  }
    
  /** Start the reader thread */
  void startReader() {
    log.debug3("Starting reader");
    ChannelReader runner = new ChannelReader();
    try {
      reader = runner;
      scomm.execute(runner);
    } catch (RuntimeException e) {
      log.warning("Exception starting channel thread", e);
      reader = null;
      abortChannel();
    }
  }

  /** Start the writer thread */
  synchronized void startWriter() {
    if (writer == null) {
      log.debug3("Starting writer");
      ChannelWriter runner = new ChannelWriter();
      try {
	scomm.execute(runner);
	writer = runner;
      } catch (RuntimeException e) {
	log.warning("Exception starting channel thread", e);
	abortChannel();
      }
    }
  }

  /** Send a message to our peer, return true iff we expect to be able to
   * send it */
  boolean send(PeerMessage msg) {
    synchronized (stateLock) {
      switch (state) {
      case STATE_CLOSED:
	return false;
      default:
	sendQueue.put(msg);
	return true;
      }
    }    
  }

  // Message reception, invoked by ChannelReader

  void handleInputStream(ChannelRunner runner) {
    try {
      readMessages(runner);
      // input stream closed by peer, drain output
      synchronized (stateLock) {
	if (isSendIdle()) {
	  stopChannel();
	} else {
	  stateTrans(STATE_OPEN, STATE_DRAIN_OUTPUT);
	}
      }
      // exit reader thread
    } catch (InterruptedIOException e) {
      int xfer = e.bytesTransferred;
      log.debug("read timeout, " + xfer + " bytes read");
      abortChannel();
    } catch (SocketException e) {
      log.warning("handleInputStream: " + e.toString());
      abortChannel();
    } catch (IOException e) {
      log.warning("handleInputStream", e);
      abortChannel();
    }
    // exit thread
  }

  void readMessages(ChannelRunner runner) throws IOException {
    while (runner.goOn() && readHeader()) {
      int op = rcvHeader[HEADER_OFF_OP];
      if (peer == null && op != OP_PEERID) {
	throw new ProtocolException("Didn't receive peerid first: " + op);
      }
      switch (op) {
      case OP_PEERID:
	readPeerId();
	reader.setRunnerName();
	writer.setRunnerName();
	break;
      case OP_DATA:
	readDataMsg();
	break;
      default:
	throw new ProtocolException("Received unknown opcode: " + op);
      }
      lastRcvTime = lastActiveTime = TimeBase.nowMs();
      rcvActive = false;
    }
  }

  void readPeerId() throws IOException {
    int len = getRcvdMessageLength();
    if (len > MAX_PEERID_LEN) {
      String msg = "Peerid too long: " + len;
      log.warning(msg);
      throw new ProtocolException(msg);
    }
    if (!readBuf(peerbuf, len)) {
      String msg = "No data in Peerid message";
      log.warning(msg);
      throw new ProtocolException(msg);
    }
    String peerkey = new String(peerbuf, 0, len);
    PeerIdentity pid = scomm.findPeerIdentity(peerkey);
    if (peer == null) {
      peer = pid;
      log.debug3("Got peer: " + peer);
      
      scomm.associateChannelWithPeer(this, peer);
    } else if (!pid.equals(peer)) {
      String msg = "Received conflicting peerid msg: " + pid + " was: " + peer;
      log.warning(msg);
      throw new ProtocolException(msg);
    }
  }

  void readDataMsg() throws IOException {
    int len = getRcvdMessageLength();
    int proto = ByteArray.decodeInt(rcvHeader, HEADER_OFF_PROTO);
    if (log.isDebug3()) log.debug3("Got data: " + proto + ", len: " + len);
    PeerMessage msg = scomm.newPeerMessage(len);
    msg.setProtocol(proto);
    msg.setSender(peer);
    try {
      OutputStream msgOut = msg.getOutputStream();
      copyBytes(ins, msgOut, len);
      msgOut.close();
      rcvQueue.put(msg);
      stats.msgsRcvd++;
    } catch (IOException e) {
      msg.delete();
      throw e;
    }
  }

  /** Read size bytes into buf */
  boolean readBuf(byte[] buf, int size) throws IOException {
    int len = StreamUtil.readBytes(ins, buf, size);
    if (len == size) return true;
    if (len == 0) return false;
    throw new ProtocolException("Connection closed in middle of message");
  }

  /** Read size bytes from stream into buf.  Keeps trying to read until
   * enough bytes have been read or EOF or error.
   * @param ins stream to read from
   * @param buf buffer to read into
   * @param size number of bytes to read
   * @return number of bytes read, which will be less than size iff EOF is
   * reached
   * @throws IOException
   */
  int readBytes(InputStream ins, byte[] buf, int size) throws IOException {
    int off = 0;
    int rem = size;
    while (rem > 0) {
      int nread = ins.read(buf, off, rem);
      if (nread == -1) {
	return off;
      }
      rcvActive = true;
      off += nread;
      rem -= nread;
    }
    return off;
  }

  /** Read incoming message header into rcvHeader.
   * @return true if read a complete header, false if no more incoming
   * messages on this connection.
   * @throws ProtocolException if message header is malformed or connection
   * closed before complete header is read.
   */
  boolean readHeader() throws IOException {
    if (!readBuf(rcvHeader, HEADER_LEN)) {
      // connection closed cleanly
      log.debug2("Input closed");
      return false;
    }
    if (rcvHeader[HEADER_OFF_CHECK] != HEADER_CHECK) {
      throw new ProtocolException("Message doesn't start with " +
				  HEADER_CHECK);
    }
    return true;
  }

  int getRcvdMessageLength() {
    return ByteArray.decodeInt(rcvHeader, HEADER_OFF_LEN);
  }

  // Message sending, invoked by ChannelWriter

  boolean isSendIdle() {
    return sendQueue.isEmpty();
  }

  long calcSendWaitTime() {
    long i = scomm.getSendWakeupTime();
    if (lastActiveTime == 0) {
      return i;
    }
    long j = TimeBase.msUntil(lastActiveTime + scomm.getChannelIdleTime());
    if (log.isDebug3()) log.debug3("Send queue wait: " + (i < j ? i : j));
    return i < j ? i : j;

  }

  void handleOutputStream(ChannelWriter runner) {
    try {
      if (needSendPeerId) {
	writePeerId();
	needSendPeerId = false;
      }
      PeerMessage msg;
      while (runner.goOn()) {
	// don't remove msg from sendQueue until sent.  isEmpty() implies
	// nothing to send
	while (null != (msg = (PeerMessage)sendQueue.peekWait(Deadline.in(calcSendWaitTime())))) {
	  writeDataMsg(msg);
	  stats.msgsSent++;
	  sendQueue.get(Deadline.EXPIRED);
	  msg.delete();
	  lastSendTime = lastActiveTime = TimeBase.nowMs();
	  synchronized (stateLock) {
	    if (state == STATE_DRAIN_OUTPUT && isSendIdle()) {
	      stopChannel();
	      break;
	    }
	  }
	}
	synchronized (stateLock) {
	  if (!rcvActive &&
	      TimeBase.msSince(lastActiveTime) > scomm.getChannelIdleTime()) {
	    // time to close channel.  shutdown output only in case peer is
	    // now sending message
	    state = STATE_DRAIN_INPUT;
	    try {
	      log.debug2("Shutdown output");
	      sock.shutdownOutput();
	      break;
	    } catch (IOException e) {
	      log.debug("shutdownOutput", e);
	      abortChannel();
	      break;
	    }
	  }
	}
      }
    } catch (InterruptedException e) {
      abortChannel();
    } catch (IOException e) {
      abortChannel();
    }
  }

  /** send peerid msg */
  void writePeerId() throws IOException{
    String key = localPeer.getIdString();
    if (log.isDebug3()) log.debug3("Sending peerid: " + key);
    writeHeader(OP_PEERID, key.length(), 0);
    outs.write(key.getBytes());
    outs.flush();
  }

  /** send data msg */
  void writeDataMsg(PeerMessage msg) throws IOException {
    if (log.isDebug3()) log.debug3("Sending data: " + msg.getProtocol() +
				   ", len: " + msg.getDataSize());
    writeHeader(OP_DATA, msg.getDataSize(), msg.getProtocol());
    copyBytes(msg.getInputStream(), outs, msg.getDataSize());
    outs.flush();
  }

  /** send msg header */
  void writeHeader(int op, int len, int proto) throws IOException {
    sndHeader[HEADER_OFF_CHECK] = HEADER_CHECK;
    sndHeader[HEADER_OFF_OP] = (byte)op;
    ByteArray.encodeInt(len, sndHeader, HEADER_OFF_LEN);
    ByteArray.encodeInt(proto, sndHeader, HEADER_OFF_PROTO);
    outs.write(sndHeader);
  }

  /** Copy len bytes from input to output stream.
   * @return true if len bytes successfully copied
   * @throws ProtocolException if eof reached before len bytes
   * @throws IOException
   */
  boolean copyBytes(InputStream is, OutputStream os, int len)
      throws IOException {
    byte[] copybuf = new byte[COPY_BUFFER_SIZE];
    int rem = len;
    int bufsize = copybuf.length;
    while (rem > 0) {
      int nread = is.read(copybuf, 0, rem > bufsize ? bufsize : rem);
      if (nread < 0) {
	throw new ProtocolException("Connection closed in middle of message");
      }
      os.write(copybuf, 0, nread);
      rem -= nread;
    }
    return true;
  }

  public String toString() {
    return "[BChan(" + state + "): " +
      (peer != null ? peer.toString() : "(none)")
      + "]";
  }

  boolean wasOpen() {
    return (stats.msgsSent != 0) && (stats.msgsRcvd != 0);
  }

  Stats getStats() {
    return stats;
  }

  static class Stats {
    int msgsSent = 0;
    int msgsRcvd = 0;
  }


  abstract class ChannelRunner extends LockssRunnable {
    volatile Thread thread;
    TimerQueue.Request timerReq;
    boolean goOn = true;

    public ChannelRunner() {
      super("Runner");
    }

    abstract void doRunner();

    public void lockssRun() {
      try {
	thread = Thread.currentThread();
	setRunnerName();

	setPriority(BlockingStreamComm.PRIORITY_PARAM_CHANNEL,
		    BlockingStreamComm.PRIORITY_DEFAULT_CHANNEL);
// 	triggerWDogOnExit(true);
// 	startWDog(BlockingStreamComm.WDOG_PARAM_CHANNEL,
// 		  BlockingStreamComm.WDOG_DEFAULT_CHANNEL);

	doRunner();
      } finally {
// 	stopWDog();
// 	setRunnerName("ChanAvail");
      }
//       triggerWDogOnExit(false);
    }

    public boolean goOn() {
      return goOn;
    }

    public synchronized void stopRunner() {
//       triggerWDogOnExit(false);
      goOn = false;
      if (thread != null) {
	thread.interrupt();
      }
    }

    public void cancelTimeout() {
      if (timerReq != null) {
	TimerQueue.cancel(timerReq);
      }
    }

    abstract String getRunnerName();

    String getRunnerName(String name) {
      if (peer == null) {
	return name;
      } else {
	return name + ": " + peer.getIdString();
      }
    }

    public void setRunnerName() {
      setRunnerName(getRunnerName());
    }

    public void setRunnerName(String name) {
      if (thread != null) {
	String oldName = thread.getName();
	if (!oldName.equals(name)) {
	  thread.setName(name);
	  log.threadNameChanged();
	}
      }
    }
  }

  class ChannelReader extends ChannelRunner {

    public void doRunner() {
      handleInputStream(this);
    }

    String getRunnerName() {
      return getRunnerName("ChanReader");
    }
  }

  class ChannelConnecter extends ChannelRunner {

    public void doRunner() {
      connect(this);
    }

    public void setTimeout(long timeout) {
      timerReq =
	TimerQueue.schedule(Deadline.in(timeout),
			    new TimerQueue.Callback() {
			      public void timerExpired(Object cookie) {
				if (state == STATE_CONNECTING) {
				  stopRunner();
				}
			      }
			      public String toString() {
				return "Channel connector" + peer;
			      }
			    },
			    null);
    }

    String getRunnerName() {
      return getRunnerName("ChanConnecter");
    }
  }

  class ChannelWriter extends ChannelRunner {

    public void doRunner() {
      handleOutputStream(this);
    }

    String getRunnerName() {
      return getRunnerName("ChanWriter");
    }
  }
}
