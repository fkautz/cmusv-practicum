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

import java.io.IOException;
import java.util.*;
import java.security.MessageDigest;
import org.lockss.app.*;
import org.lockss.util.*;
import org.lockss.hasher.*;
import org.lockss.protocol.LcapMessage;

/**
 * A singleton class which provides access to various system calculations, such
 * as hash speed estimates.
 */
public class SystemMetrics extends BaseLockssManager {
  static final String PREFIX = Configuration.PREFIX + "metrics.";
  /**
   * Configuration parameter name for duration, in ms, for which the hash test
   * should run.
   */
  public static final String PARAM_HASH_TEST_DURATION =
    PREFIX + "hash.duration";
  /**
   * Configuration parameter name for the number of bytes per step in the hash
   * test.
   */
  public static final String PARAM_HASH_TEST_BYTE_STEP =
    PREFIX + "hash.stepsize";

  public static final String PARAM_SLOWEST_RATE = PREFIX + "slowest.hashrate";

  static final long DEFAULT_HASH_TEST_DURATION = 10 * Constants.SECOND;
  static final int DEFAULT_HASH_TEST_BYTE_STEP = 10 * 1024;
  static final int DEFAULT_SLOWEST_RATE = 250;

  private static Logger logger = Logger.getLogger("SystemMetrics");
  private static SystemMetrics theMetrics = null;

  Hashtable estimateTable = new Hashtable();
  MessageDigest defaultDigest = LcapMessage.getDefaultHasher();
  HashService hashService;

  public void startService() {
    super.startService();
    theMetrics = this;
    hashService = theDaemon.getHashService();
  }

  public void setConfig(Configuration newConfig,
			Configuration prevConfig,
			Set changedKeys) {
  }

  /**
   * Static method to get the singleton
   * @return a SystemMetrics object
   */
  // tk - should go away
  public static SystemMetrics getSystemMetrics() {
    return theMetrics;
  }

  /**
   * Returns an estimate of the hash speed for this hasher.
   * First tries to get a real value from the HashService.  If not available,
   * calculates an estimate.
   * @param hasher the CachedUrlSetHasher to test
   * @param digest the hashing algorithm
   * @return hash speed in bytes/ms
   * @throws IOException
   */
  public int getBytesPerMsHashEstimate(CachedUrlSetHasher hasher,
                                       MessageDigest digest)
      throws IOException {
    int speed = -1;
    if (hashService != null) {
      speed = hashService.getHashSpeed(digest);
    }
    if (speed <= 0) {
      speed = getHashSpeedEstimate(hasher, digest);
    }
    return speed;
  }

  /**
   * Returns an estimate of the hash speed for this hasher.
   * First tries to get a real value from the HashService.  If not available,
   * calculates an estimate.
   * @param hasher the CachedUrlSetHasher to test
   * @param digest the hashing algorithm
   * @return hash speed in bytes/ms
   * @throws IOException
   */
  public int getHashSpeedEstimate(CachedUrlSetHasher hasher,
				  MessageDigest digest)
      throws IOException {

    Integer estimate = (Integer)estimateTable.get(digest.getAlgorithm());
    if (estimate==null) {
      estimate = new Integer(measureHashSpeed(hasher, digest));
      estimateTable.put(digest.getAlgorithm(), estimate);
    }
    return estimate.intValue();
  }

  /**
   * Calculate an estimate of the hash speed for a hasher.
   * Tests by hashing the CachedUrlSet for a small period of time.
   * @param hasher the CachedUrlSetHasher to test
   * @param digest the hashing algorithm
   * @return an int for estimated bytes/ms
   * @throws IOException
   */
  public int measureHashSpeed(CachedUrlSetHasher hasher, MessageDigest digest)
      throws IOException {
    long timeTaken = 0;
    long bytesHashed = 0;
    boolean earlyFinish = false;
    long hashDuration =
      Configuration.getTimeIntervalParam(PARAM_HASH_TEST_DURATION,
					 DEFAULT_HASH_TEST_DURATION);
    int hashStep =
      Configuration.getIntParam(PARAM_HASH_TEST_BYTE_STEP, DEFAULT_HASH_TEST_BYTE_STEP);

    long startTime = TimeBase.nowMs();
    Deadline deadline = Deadline.in(hashDuration);
    while (!deadline.expired() && !hasher.finished()) {
      bytesHashed += hasher.hashStep(hashStep);
    }
    timeTaken = TimeBase.msSince(startTime);
    if (timeTaken==0) {
      logger.warning("Test finished in zero time: using bytesHashed as estimate.");
      return (int)bytesHashed;
    }
    return (int)(bytesHashed / timeTaken);
  }

  /**
   * Returns a hash estimate based on the default algorithm.  If no
   * estimate is available, returns the slowest hash speed.
   * @return the hash speed estimate in bytes/ms
   * @throws NoHashEstimateAvailableException if no estimate is available
   */
  public int getBytesPerMsHashEstimate()
      throws NoHashEstimateAvailableException {
    int speed = -1;
    if (hashService != null) {
      speed = hashService.getHashSpeed(defaultDigest);
    }
    if (speed <= 0) {
      Integer estimate =
	(Integer)estimateTable.get(defaultDigest.getAlgorithm());
      if (estimate != null) {
	speed = estimate.intValue();
      }
    }
    if (speed <= 0) {
      throw new NoHashEstimateAvailableException();
    }
    return speed;
  }

  /**
   * Return the hash speed of the slowest cache
   * @return the slowest speed
   */
  public int getSlowestHashSpeed() {
    return Configuration.getIntParam(PARAM_SLOWEST_RATE, DEFAULT_SLOWEST_RATE);
  }

  /** Exception thrown if no hash estimate is available. */
  public static class NoHashEstimateAvailableException extends Exception {
    public NoHashEstimateAvailableException() {
      super();
    }
  }
}
