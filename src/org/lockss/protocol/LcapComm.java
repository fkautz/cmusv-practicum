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

package org.lockss.protocol;
//import java.util.*;
import java.io.*;
import java.net.*;
import org.lockss.util.*;
import org.lockss.daemon.*;
import org.lockss.poller.*;
/**
 * LcapComm implements the routing parts of the LCAP protocol, using
 * {@link LcapSocket} to send and receive packets.
 * Routing involves decisions about using unicast to supplement multicast,
 * including forwarding received unicast packets.
 */
public class LcapComm {

  static final String PARAM_GROUPNAME = Configuration.PREFIX + "group";
  static final String PARAM_MULTIPORT = Configuration.PREFIX + "port";
  static final String PARAM_UNIPORT = Configuration.PREFIX + "unicastport";

  protected static Logger log = Logger.getLogger("Comm");

  private static LcapComm singleton;
  private static LcapSocket sendSock;	// socket used for sending only

  // These may change if/when we use multiple groups/ports
  private static String groupName;	// multicast group
  private static InetAddress group;
  private static int multiPort = -1;	// multicast port
  private static int uniPort = -1;	// uncast port
  private LcapSocket.Multicast mSock;
  private LcapSocket.Unicast uSock;

  private FifoQueue socketInQ;		// received packets from LcapSocket
  private ReceiveThread rcvThread;

  /** Initialize and start the communications thread(s) */
  public static void startComm() {
    try {
      sendSock = new LcapSocket();
    } catch (SocketException e) {
      log.critical("Can't create send socket", e);
    }
    try {
      groupName = Configuration.getParam(PARAM_GROUPNAME);
      multiPort = Configuration.getIntParam(PARAM_MULTIPORT);
      uniPort = Configuration.getIntParam(PARAM_UNIPORT);
      if (groupName != null) {
	group = InetAddress.getByName(groupName);
      }
      if (group == null) {
	log.critical("null group addr");
	return;
      }
      singleton = new LcapComm();
      singleton.start();
    } catch (UnknownHostException e) {
      log.critical("Can't get group addr", e);
    } catch (Configuration.Error e) {
      log.critical("Multicast port not configured", e);
    }
  }

  /** Multicast a message to all caches holding the ArchivalUnit.
   * @param msg the message to send
   * @param au archival unit for which this message is relevant.  Used to
   * determine which multicast socket/port to send to.
   */
  public static void sendMessage(LcapMessage msg, ArchivalUnit au)
      throws IOException {
    if (multiPort < 0) {
      throw new IllegalStateException("Multicast port not configured");
    }
    if (group == null) {
      throw new IllegalStateException("Multicast group not configured");
    }
    sendMessageTo(msg, group, multiPort);
  }

  /** Unicast a message to a single cache.
   * @param msg the message to send
   * @param au archival unit for which this message is relevant.  Used to
   * determine which multicast socket/port to send to.
   * @param id the identity of the cache to which to send the message
   */
  public static void sendMessageTo(LcapMessage msg, ArchivalUnit au,
				   LcapIdentity id)
      throws IOException {
    if (uniPort < 0) {
      throw new IllegalStateException("Unicast port not configured");
    }
    sendMessageTo(msg, id.getAddress(), uniPort);
  }

  private static void sendMessageTo(LcapMessage msg, InetAddress addr,
				    int port)
      throws IOException {
    log.debug("sending "+msg+" to "+addr+":"+port);
    byte data[] = msg.encodeMsg();
    DatagramPacket pkt = new DatagramPacket(data, data.length, addr, port);
    sendSock.send(pkt);
  }

  private void start() {
    socketInQ = new FifoQueue();
    if (multiPort >= 0 && group != null) {
      try {
	log.debug("new LcapSocket.Multicast("+socketInQ+", "+group+", "+
		  multiPort);
	LcapSocket.Multicast mSock =
	  new LcapSocket.Multicast(socketInQ, group, multiPort);
	mSock.start();
	this.mSock = mSock;
	log.info("Multicast socket started: " + mSock);
      } catch (UnknownHostException e) {
	log.error("Can't create multicast socket", e);
      } catch (IOException e) {
	log.error("Can't create multicast socket", e);
      }
    } else {
      log.error("Multicast group or port not configured, not starting multicast receive");
    }

    if (uniPort >= 0) {
      try {
	log.debug("new LcapSocket.Unicast("+socketInQ+", "+uniPort);
	LcapSocket.Unicast uSock =
	  new LcapSocket.Unicast(socketInQ, uniPort);
	uSock.start();
	this.uSock = uSock;
	log.info("Unicast socket started: " + uSock);
      } catch (IOException e) {
	log.error("Can't create unicast socket", e);
      }
    } else {
      log.error("Unicast port not configured, not starting unicast receive");
    }
    ensureQRunner();
  }

  // tk add watchdog
  private void ensureQRunner() {
    if (rcvThread == null) {
      log.info("Starting receive thread");
      rcvThread = new ReceiveThread("CommRcv");
      rcvThread.start();
    }
  }

  private void stop() {
    if (rcvThread != null) {
      log.info("Stopping Q runner");
      rcvThread.stopRcvThread();
      rcvThread = null;
    }
  }

  private void processReceivedPacket(LockssDatagram dgram) {
    LcapMessage msg;
    try {
      msg = LcapMessage.decodeToMsg(dgram.getPacket().getData(), 
				    dgram.isMulticast());
      log.debug("Received " + msg);
      PollManager.handleMessage(msg); //XXX should modify node state instead
    } catch (IOException e) {
      log.warning("Error decoding packet", e);
    }
  }

  // Receive thread
  private class ReceiveThread extends Thread {
    private boolean goOn = false;
    private Deadline timeout;

    private ReceiveThread(String name) {
      super(name);
    }

    public void run() {
      //        if (rcvPriority > 0) {
      //  	Thread.currentThread().setPriority(rcvPriority);
      //        }
      goOn = true;

      while (goOn) {
	try {
	  timeout = Deadline.in(60000);
	  Object qObj = socketInQ.get(timeout);
	  if (qObj != null) {
	    if (qObj instanceof LockssDatagram) {
	      processReceivedPacket((LockssDatagram)qObj);
	    } else {	      
	      log.warning("Non-LockssDatagram on rcv queue" + qObj);
	    }
	  }
	} catch (InterruptedException e) {
	  // just wake up and check for exit
	} finally {
	  rcvThread = null;
	}
      }
    }

    private void stopRcvThread() {
      goOn = false;
      timeout.expire();
    }
  }
}
