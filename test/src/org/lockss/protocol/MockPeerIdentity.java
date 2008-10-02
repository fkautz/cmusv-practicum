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
import org.lockss.util.*;

public class MockPeerIdentity extends PeerIdentity {
  private String key;
  private transient PeerAddress pAddr;

  public MockPeerIdentity(String newKey) {
    key = newKey;
  }

  /**
   * toString results in a string describing the peer that is
   * understandable to humans.
   */
  public String toString() {
    StringBuffer sb = new StringBuffer();
    sb.append("[");
    if (isLocalIdentity()) sb.append("L");
    sb.append("MockPeer: ");
    sb.append(key);
    sb.append("]");
    return sb.toString();
  }

  /**
   * getIdString returns a string describing the peer that is
   * parseable and convertable into a PeerIdentity object.  At
   * present this is a dotted-quad IP address optionally followed
   * by a colon and a numeric port number
   */
  public String getIdString() {
    return key;
  }

  public PeerAddress getPeerAddress() {
    throw new UnsupportedOperationException("not implemented");
  }

  /** Return true iff this is a local PeerIdentity.
   */
  public boolean isLocalIdentity() {
    return false;
  }
}
