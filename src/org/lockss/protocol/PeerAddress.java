/*
 * $Id$
 */

/*

Copyright (c) 2004 Board of Trustees of Leland Stanford Jr. University,
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
import java.net.*;
import org.lockss.util.*;

/**
 * Abstraction of protocol-specific peer addresses, currently UDP (V1) or
 * TCP (V3).  Should be used only be comm code when necessary to establish
 * a connection to the peer.  For perr identification purposes, always use
 * {@link PeerIdentity}
 */

// Everything in here should be package access.  It's public only so it's
// included in javadoc

public abstract class PeerAddress {
  static Logger log = Logger.getLogger("PeerAddress");
  private String key;

  protected PeerAddress(String key) {
    this.key = key;
  }

  protected static PeerAddress makePeerAddress(PeerIdentity pid) 
      throws IdentityManager.MalformedIdentityKeyException {
    return makePeerAddress(pid.getIdString());
  }

  protected static PeerAddress makePeerAddress(String key)
      throws IdentityManager.MalformedIdentityKeyException {
    try {
      List parts =
	StringUtil.breakAt(key, IdentityManager.V3_ID_SEPARATOR_CHAR);
      String ip;
      switch (parts.size()) {
      case 1:
	ip = (String)parts.get(0);
	checkIpAddr(ip);
	return new Udp(key, IPAddr.getByName(ip));
      case 2:
	ip = (String)parts.get(0);
	checkIpAddr(ip);
	int port = Integer.parseInt((String)parts.get(1));
	return new Tcp(key, IPAddr.getByName(ip), port);
      default:
	throw new
	  IdentityManager.MalformedIdentityKeyException("Unparseable PeerId: "
							+ key);
      }
    } catch (UnknownHostException e) {
      throw new
	IdentityManager.MalformedIdentityKeyException("Unparseable PeerId: " +
						      key + ": " +
						      e.toString());
    } catch (RuntimeException e) {
      throw new
	IdentityManager.MalformedIdentityKeyException("Unparseable PeerId: " +
						      key + ": " +
						      e.toString());
    }
  }

  abstract boolean isStream();

  /** Check for legal numeric IP address, throw
   * MalformedIdentityKeyException if not.  Currently accepts only IPV4
   * addresses
   */
  private static void checkIpAddr(String addr)
      throws IdentityManager.MalformedIdentityKeyException {
    try {
      new IpFilter.Addr(addr);
    } catch (IpFilter.MalformedException e) {
      throw new IdentityManager.MalformedIdentityKeyException(e.toString());
    }
  }

  protected static abstract class Ip extends PeerAddress {
    IPAddr addr;

    private Ip(String key, IPAddr addr) {
      super(key);
      this.addr = addr;
    }

    protected IPAddr getIPAddr() {
      return addr;
    }
  }

  protected static class Udp extends Ip {

    Udp(String key, IPAddr addr) {
      super(key, addr);
    }

    boolean isStream() {
      return false;
    }

    public boolean equals(Object o) {
      if (o instanceof Udp) {
	Udp u = (Udp)o;
	return getIPAddr().equals(u.getIPAddr());
      }
      return false;
    }

    public int hashCode() {
      throw new UnsupportedOperationException("Use PeerIdentity for key, not PeerAddress");
    }

    public String toString() {
      return "[PeerAddr.Udp: " + addr.getHostAddress() + "]";
    }
  }

  protected static class Tcp extends Ip {
    private short port;

    Tcp(String key, IPAddr addr, int port) {
      super(key, addr);
      if (port < 0 || port > 65535) {
	throw new IllegalArgumentException("Illegal TCP port: " + port);
      }
      this.port = (short)port;
    }

    protected short getPort() {
      return port;
    }

    boolean isStream() {
      return true;
    }

    public boolean equals(Object o) {
      if (o instanceof Tcp) {
	Tcp t = (Tcp)o;
	return getIPAddr().equals(t.getIPAddr()) &&
	  getPort() == t.getPort();
      }
      return false;
    }

    public int hashCode() {
      throw new UnsupportedOperationException("Use PeerIdentity for key, not PeerAddress");
    }

    public String toString() {
      return "[PeerAddr.Tcp: " + addr.getHostAddress() +
	IdentityManager.V3_ID_SEPARATOR + port + "]";
    }
  }
}
