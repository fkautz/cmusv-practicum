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
import java.util.HashMap;
import java.util.Random;
import org.mortbay.util.B64Code;
import org.lockss.util.*;
import org.lockss.daemon.*;

/**
 * quick and dirty wrapper class for a network identity.
 * @author Claire Griffin
 * @version 1.0
 */
public class LcapIdentity {

  public static final int EVENT_ORIG = 0;
  public static final int EVENT_ORIG_OP = 1;
  public static final int EVENT_SEND_ORIG = 2;
  public static final int EVENT_SEND_FWD = 3;
  public static final int EVENT_ERRPKT = 4;
  public static final int EVENT_DUPLICATE = 5;

  public static final int EVENT_MAX = 6;

  long eventCount[] = new long[EVENT_MAX];
  transient long m_lastActiveTime = 0;
  transient long m_lastOpTime = 0;
  transient long m_incrPackets = 0;   // Total packets arrived this interval
  transient long m_origPackets = 0;   // Unique pkts from this identity this interval
  transient long m_forwPackets = 0;   // Unique pkts forwarded by this identity this interval
  transient long m_duplPackets = 0;   // Duplicate packets originated by this identity
  transient long m_totalPackets = 0;
  transient long m_lastTimeZeroed = 0;
  transient HashMap m_pktsThisInterval = new HashMap();
  transient HashMap m_pktsLastInterval = new HashMap();
  /*
    END PACKET HISTOGRAM SUPPORT - CURRENTLY NOT BEING USED
  */

  private PeerIdentity m_pid;
  transient IPAddr m_address = null;
  int m_port; // Zero for the well-known UDP port

  int m_reputation;
  String m_idKey;

  static Logger theLog=Logger.getLogger("LcapIdentity");

//   protected LcapIdentity(PeerIdentity pid, int reputation)
//     throws UnknownHostException {
//     if (pid == null) {
//       throw new NullPointerException();
//     }
//     m_pid = pid;
//     m_reputation = reputation;
//   }

  // internal use only
  private LcapIdentity(PeerIdentity pid) {
    if (pid == null) {
      throw new NullPointerException();
    }
    m_pid = pid;
  }

  protected LcapIdentity(PeerIdentity pid, String idKey)
      throws IdentityManager.MalformedIdentityKeyException {
    this(pid, idKey, IdentityManager.INITIAL_REPUTATION);
  }

  protected LcapIdentity(PeerIdentity pid, String idKey, int reputation)
      throws IdentityManager.MalformedIdentityKeyException {
    this(pid);
    m_idKey = idKey;
    m_address = stringToAddr(idKey);
    m_port = stringToPort(idKey);
    m_reputation = reputation;
  }

  /**
   * construct a new Identity from an address
   * @param addr the IPAddr
   */
  LcapIdentity(PeerIdentity pid, IPAddr addr, int port) {
    this(pid);
    m_idKey = makeIdKey(addr, port);
    m_reputation = IdentityManager.INITIAL_REPUTATION;
    m_address = addr;
    m_port = port;
  }


  // accessor methods

  /**
   * return the PeerIdentity
   * @return the PeerIdentity
   */
  public PeerIdentity getPeerIdentity() {
    return m_pid;
  }

  /**
   * return the address of the Identity
   * @return the <code>IPAddr<\code> for this Identity
   */
  public IPAddr getAddress() {
    return m_address;
  }

  /**
   * return the address of the Identity
   * @return the <code>IPAddr<\code> for this Identity
   */
  public int getPort() {
    return m_port;
  }

  /**
   * return the current value of this Identity's reputation
   * @return the int value of reputation
   */
  public int getReputation() {
    return m_reputation;
  }

  public long getLastActiveTime() {
    return m_lastActiveTime;
  }

  public long getLastOpTime() {
    return m_lastOpTime;
  }

  public long getLastTimeZeroed() {
    return m_lastTimeZeroed;
  }


  public String getIdKey() {
    return m_idKey;
  }

  public void rememberEvent(int event, LcapMessage msg) {
    checkEvent(event);
    eventCount[event]++;
    m_lastActiveTime = TimeBase.nowMs();
    if (msg != null && !msg.isNoOp()) {
      m_lastOpTime = TimeBase.nowMs();
    }
  }

  public long getEventCount(int event) {
    checkEvent(event);
    return eventCount[event];
  }

  void checkEvent(int event) {
    if (event < 0 || event >= EVENT_MAX) {
      throw new RuntimeException("Illegal event number: " + event);
    }
  }

  // methods which may need to be overridden

  public boolean isEqual(IPAddr addr, int port) {
    String key = makeIdKey(addr, port);

    return key.equals(m_idKey);
  }

  /**
   * return true if two Identity are found to be the same
   * @param id the Identity to compare with this one
   * @return true if the id keys are the same
   */
  public boolean isEqual(LcapIdentity id) {
    String idKey = id.m_idKey;

    return idKey.equals(m_idKey);
  }

  /**
   * return the identity of the Identity
   * @return the String representation of the Identity
   */
  public String toString() {
    return m_idKey;
  }

  /**
   * return the name of the host as a string
   * @return the String representation of the Host
   */
  public String toHost() {
    return m_idKey;
  }


  /**
   * update the active packet counter
   * @param NoOp boolean true if this is a no-op message
   * @param msg the active message
   */
  public void rememberActive(boolean NoOp, LcapMessage msg) {
    m_lastActiveTime = TimeBase.nowMs();
    if (!NoOp) {
      m_lastOpTime = m_lastActiveTime;
    }
    m_incrPackets++;
    m_totalPackets++;
    if (msg.getOriginatorID() == m_pid) {
      char[] encoded = B64Code.encode(msg.getVerifier());

      String verifier = String.valueOf(encoded);
      Integer count = (Integer) m_pktsThisInterval.get(verifier);
      if (count != null) {
	// We've seen this packet before
	count = new Integer(count.intValue() + 1);
      }
      else {
	count = new Integer(1);
      }
      m_pktsThisInterval.put(verifier, count);
    }
  }

  /**
   * increment the originator packet counter
   * @param msg Message ignored
   */
  public void rememberValidOriginator(LcapMessage msg) {
    m_origPackets++;
  }

  /**
   * increment the forwarded packet counter
   * @param msg Message ignored
   */
  public void rememberValidForward(LcapMessage msg) {
    m_forwPackets++;
  }

  /**
   * increment the duplicate packet counter
   * @param msg Message ignored
   */
  public void rememberDuplicate(LcapMessage msg) {
    m_duplPackets++;
  }


  /**
   * update the reputation value for this Identity
   * @param delta the change in reputation
   */
  void changeReputation(int delta) {
    m_reputation += delta;
  }

  static String makeIdKey(IPAddr addr, int port) {
    return addrToString(addr, port);
  }

  /**
   * turn an IPAddr into a dotted quartet string since
   * get host address doesn't necessarily return an address
   * @param addr the address to turn into a string
   * @param port the port
   * @return the address as dotted quartet sting
   */
  public static String addrToString(IPAddr addr, int port)  {
    String ret = addr.getHostAddress();
    if (port != 0)
      ret += ":" + port;
    return ret;
  }

  public static IPAddr stringToAddr(String idKey)
      throws IdentityManager.MalformedIdentityKeyException {
    IPAddr ret = null;
    try {
      int colon = idKey.indexOf(':');
      if (colon > 0) {
	// XXX V3 identity not really supported
	ret = IPAddr.getByName(idKey.substring(0,colon));
      } else if (colon < 0) {
	// V1 identity,  no port part
	ret = IPAddr.getByName(idKey);
      }
    } catch (UnknownHostException ignore) {
    }
    if (ret == null) {
      throw new IdentityManager.MalformedIdentityKeyException(idKey);
    }
    return ret;
  }

  public static int stringToPort(String idKey)
      throws IdentityManager.MalformedIdentityKeyException {
    int colon = idKey.indexOf(':');
    int ret = 0;
    if (colon >= 0) {
      try {
	ret = Short.parseShort(idKey.substring(colon+1));
      } catch (NumberFormatException nfe) {
	throw new IdentityManager.MalformedIdentityKeyException(idKey);
      }
    }
    return ret;
  }
}
