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

package org.lockss.test;
import java.util.*;
import java.text.DateFormat;


/** Timer utilities
 */
public class TimerUtil {
  // no instances
  private TimerUtil() {
  }

  /** Sleep for <code>ms</code> milliseconds,
   * throwing <code>InterruptedException</code> if interrupted.
   */
  public static void sleep(long ms) throws InterruptedException {
    Thread.currentThread().sleep(ms);
  }

  // Is there a use for one that just returns if interrupted?
//    public static void interruptableSleep(long ms) {
//      try {
//        Thread.currentThread().sleep(ms);
//      }
//      catch (InterruptedException e) {
//      }
//    }

  /** Sleep for <code>ms</code> milliseconds, ignoring interrupts */
  public static void guaranteedSleep(long ms) {
    long expMS = new Date().getTime() + ms;
    Date expiration = new Date(expMS);

    for (Date now = new Date();
	 now.before(expiration);
	 now = new Date()) {
      long nowMS = now.getTime();
      try {
	sleep(expMS - nowMS);
      } catch (InterruptedException e) {
      }
    }
  }

  /** Return the millisecond difference between two <code>Date</code>s. */
  public static long diff(Date d1, Date d2) {
    return d1.getTime() - d2.getTime();
  }

  /** Return the milliseconds since <code>Date</code>. */
  public static long timeSince(Date start) {
    return diff(new Date(), start);
  }
}
