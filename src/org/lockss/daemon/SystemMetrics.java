/*
 * $Id$
 */

/*

Copyright (c) 2002 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.daemon;

import org.lockss.util.*;
import java.io.IOException;
import java.util.Hashtable;
import java.security.MessageDigest;
import org.lockss.protocol.LcapMessage;

/**
 * A singleton class which provides access to various system calculations, such
 * as hash speed estimates.
 */
public class SystemMetrics {
  /**
   * Configuration parameter name for duration, in ms, for which the hash test
   * should run.
   */
  public static final String PARAM_HASH_TEST_DURATION =
      Configuration.PREFIX + "metrics.hash.duration";
  /**
   * Configuration parameter name for the number of bytes per step in the hash
   * test.
   */
  public static final String PARAM_HASH_TEST_BYTE_STEP =
      Configuration.PREFIX + "metrics.hash.stepsize";

  public static final String PARAM_SLOWEST_RATE =
      Configuration.PREFIX + "metrics.slowest.hashrate";

  static final long DEFAULT_HASH_DURATION = 10 * Constants.SECOND;
  static final int DEFAULT_HASH_STEP = 1024;
  static final int DEFAULT_SLOWEST_RATE = 250;


  private static SystemMetrics metrics = null;
  static Hashtable estimateTable;
  static MessageDigest defaultAlgorithm;
  private static Logger logger = Logger.getLogger("SystemMetrics");

  /**
   * Static factory method for the singleton.
   * @return a SystemMetrics object
   */
  public static SystemMetrics getSystemMetrics() {
    if (metrics==null) {
      metrics = new SystemMetrics();
    }
    return metrics;
  }

  SystemMetrics() {
    estimateTable = new Hashtable();
    defaultAlgorithm = LcapMessage.getDefaultHasher();
  }

  /**
   * Update the hash estimate with a new value, presumably from an actual hash.
   * @param digest the algorithm
   * @param newEstimate the new estimate
   */
  public void updateHashEstimate(MessageDigest digest, int newEstimate) {
    estimateTable.put(digest.getAlgorithm(), new Integer(newEstimate));
  }

  /**
   * Returns an estimate on the hashed bytes per ms for this hasher.
   * Tests by hashing the CachedUrlSet for a small period of time.
   * @param hasher the CachedUrlSetHasher to test
   * @param digest the hashing algorithm
   * @return an int for estimated bytes/ms
   * @throws IOException
   */
  public int getBytesPerMsHashEstimate(CachedUrlSetHasher hasher,
                                       MessageDigest digest)
      throws IOException {
    Integer estimate = (Integer)estimateTable.get(digest.getAlgorithm());
    if (estimate==null) {
      long timeTaken = 0;
      long bytesHashed = 0;
      boolean earlyFinish = false;
      long hashDuration =
          Configuration.getTimeIntervalParam(PARAM_HASH_TEST_DURATION,
          DEFAULT_HASH_DURATION);
      int hashStep =
          Configuration.getIntParam(PARAM_HASH_TEST_BYTE_STEP,
          DEFAULT_HASH_STEP);

      long startTime = TimeBase.nowMs();
      Deadline deadline = Deadline.in(hashDuration);
      while (!deadline.expired() && !hasher.finished()) {
        bytesHashed += hasher.hashStep(hashStep);
      }
      timeTaken = TimeBase.msSince(startTime);
      if (timeTaken==0) {
        logger.warning("Test finished in zero time: using bytesHashed estimate.");
        return (int)bytesHashed;
      }
      estimate = new Integer((int)(bytesHashed / timeTaken));
      estimateTable.put(digest.getAlgorithm(), estimate);
    }
    return estimate.intValue();
  }

  /**
   * Returns a hash estimate based on the default algorithm.  Returns -1 if no
   * estimate calculated.
   * @return the estimate
   */
  public int getBytesPerMsHashEstimate() {
    Integer estimate = (Integer)estimateTable.get(
        defaultAlgorithm.getAlgorithm());
    if (estimate==null) {
      return -1;
    } else {
      return estimate.intValue();
    }
  }

  public int getSlowestRate() {
    return Configuration.getIntParam(PARAM_SLOWEST_RATE, DEFAULT_SLOWEST_RATE);
  }
}
