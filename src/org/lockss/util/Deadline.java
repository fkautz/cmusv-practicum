// ========================================================================
// $Id$
// ========================================================================

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

package org.lockss.util;
import java.util.*;
import java.text.DateFormat;

/** Daedline represents a time (at which some operation must complete).
 */
public class Deadline implements Comparable {
  protected Date expiration;
  protected long duration;		// only for testing

  /** Create a Deadline that expires at the specified Date. */
  public Deadline(Date at) {
//      duration = at.getTime() - now().getTime();
    duration = at.getTime() - nowMs();
    expiration = at;
  }
  
  /** Create a Deadline that expires in <code>duration</code> milliseconds. */
  public Deadline(long duration) {
    this.duration = duration;
//      expiration = new Date(now() + duration);
    expiration = new Date(nowMs() + duration);
  }

  /** Return the absolute expiration time, in milliseconds */
  public long getExpirationTime() {
    return expiration.getTime();
  }

  /** Return the expiration time as a Date */
  public Date getExpiration() {
    return expiration;
  }

  /** Return the time remaining until expiration, in milliseconds */
  public synchronized long getRemainingTime() {
//      return (expired() ? 0 : expiration.getTime() - now().getTime());
    return (expired() ? 0 : expiration.getTime() - nowMs());
  }

  /** For testing only. */
  long getDuration() {
    return duration;
  }

  /** Cause the timer to expire immediately */
  public synchronized void expire() {
    expiration.setTime(0);
  }

  /** Return true iff the timer has expired */
  public synchronized boolean expired() {
    return (!now().before(expiration));
  }

  /** Return true iff this daedline expires before <code>other</code>. */
  public boolean before(Deadline other) {
    return expiration.before(other.expiration);
  }

  protected static Date now() {
    return new Date();
  }

  protected static long nowMs() {
    return System.currentTimeMillis();
  }

  // Comparable interface

  public int compareTo(Object o) {
    return expiration.compareTo(((Deadline)o).expiration);
  }

  public boolean equals(Object o) {
    return expiration.equals(((Deadline)o).expiration);
  }

  // tk - should include "+n days" or some such
  private static DateFormat df = DateFormat.getTimeInstance();
  public String toString() {
    return "[duration: " + duration + ",'til:" + df.format(expiration) + "]";
  }
}
