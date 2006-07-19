/*
 * $Id$
 */

/*

Copyright (c) 2000-2006 Board of Trustees of Leland Stanford Jr. University,
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

import org.lockss.config.Configuration;

/**
 * RateLimiter is used to limit the rate at which some class of events
 * occur.  Individual operations on this class are synchronized, so safe to
 * use from multiple threads.  However, in order to ensure the rate isn't
 * exceeded, multithreaded use requires the pair of calls {@link
 * #isEventOk()} and {@link #event()}, or the pair  {@link
 * #waitUntilEventOk()} and {@link #event()} to be synchronized as a unit:<pre>
    synchronized (rateLimiter) {
      rateLimiter.isEventOk();
      rateLimiter.event();
    }</pre>
 */
public class RateLimiter {
  static Logger log = Logger.getLogger("RateLimiter");

  /** A RateLimiter that allows events at an unlimited rate. */
  public final static RateLimiter UNLIMITED = new Constant("unlimited");

  private int events;			// limit on events / interval
  private long interval;
  private long time[];			// history of (events) event times,
					// or null if unlimited
  private int count = 0;
  private String rate;

  /** Create a RateLimiter according to the specified configuration parameters.
   * @param config the Configuration object
   * @param currentLimiter optional existing RateLimiter, modified if
   * necessary
   * @param maxEventsParam name of the parameter specifying the maximum
   * number of events per interval
   * @param intervalParam name of the parameter specifying the length of
   * the interval
   * @param maxEvantDefault default maximum number of events per interval,
   * if config param has no value
   * @param intervalDefault default interval, if config param has no value
   * @return a new RateLimiter iff currentLimiter is null, else
   * currentLimiter, possible reset to a new rate
   */
  public static RateLimiter
    getConfiguredRateLimiter(Configuration config, RateLimiter currentLimiter,
			     String maxEventsParam, int maxEvantDefault,
			     String intervalParam, long intervalDefault) {
    int events = config.getInt(maxEventsParam, maxEvantDefault);
    long interval = config.getTimeInterval(intervalParam, intervalDefault);
    if (currentLimiter == null) {
      return new RateLimiter(events, interval);
    }
    if (!currentLimiter.isRate(events, interval)) {
      currentLimiter.setRate(events, interval);
    }
    return currentLimiter;
  }

  /** Create a RateLimiter according to the specified configuration
   * parameter, whose value should be a string:
   * <i>events</i>/<i>time-interval</i>.
   * @param config the Configuration object
   * @param currentLimiter optional existing RateLimiter, modified if
   * necessary
   * @param param name of the rate string config parameter
   * @param dfault default rate string
   * @return a new RateLimiter iff currentLimiter is null, else
   * currentLimiter, possible reset to a new rate
   * @throws RuntimeException if the parameter value is either empty or
   * unparseable and the default string is unparseable
   */
  public static RateLimiter
    getConfiguredRateLimiter(Configuration config, RateLimiter currentLimiter,
			     String param, String dfault) {
    String rate = config.get(param, dfault);
    if (currentLimiter == null) {
      return makeRateLimiter(rate, dfault);
    }
    if (!currentLimiter.isRate(rate)) {
      currentLimiter.setRate(rate, dfault);
    }
    return currentLimiter;
  }

  /** Create a RateLimiter according to the rate string:
   * <i>events</i>/<i>time-interval</i>.
   * @param rate the rate string
   * @param dfault default rate string
   * @return a new RateLimiter
   * @throws RuntimeException iff the rate string is either empty or
   * unparseable and the default string is unparseable.
   */
   public static RateLimiter makeRateLimiter(String rate, String dfault) {
    try {
      return new RateLimiter(rate);
    } catch (RuntimeException e) {
      log.warning("Rate (" + rate +
		  ") illegal, using default (" + dfault + ")");
      return new RateLimiter(dfault);
    }
  }

  // helper to parse rate string  <events> / <time-interval>
  private static class Ept {
    private int events;
    private long interval;
    Ept(String rate) {
      if ("unlimited".equalsIgnoreCase(rate)) {
	events = 0;
	interval = 0;
      } else {
	List pair = StringUtil.breakAt(rate, '/', 3, false, true);
	if (pair.size() != 2) {
	  throw new IllegalArgumentException("Rate not n/interval: " + rate);
	}
	events = Integer.parseInt((String)pair.get(0));
	interval = StringUtil.parseTimeInterval((String)pair.get(1));
      }
    }
  }

  /** Create a RateLimiter that limits events to <code>events</code> per
   * <code>interval</code> milliseconds.
   * @param events max number of events per interval
   * @param interval length of interval in milliseconds
   */
  public RateLimiter(int events, long interval) {
    checkRate(events, interval, false);
    init(events, interval);
  }

  /** Create a RateLimiter that limits events to the specified rate
   * @param rate the rate string, <i>events</i>/<i>time-interval</i>.
   */
  public RateLimiter(String rate) {
    Ept ept = new Ept(rate);
    checkRate(ept.events, ept.interval, true);
    init(ept.events, ept.interval);
    this.rate = rate;
  }

  private void init(int events, long interval) {
    this.events = events;
    this.interval = interval;
    if (interval != 0) {
      time = new long[events];
      Arrays.fill(time, 0);
    } else {
      time = null;
    }
    count = 0;
  }

  private void checkRate(int events, long interval, boolean allowUnlimited) {
    if (allowUnlimited && events == 0 && interval == 0) {
      return;
    }
    if (events < 1) {
      throw new IllegalArgumentException("events: " + events);
    }
    if (interval < 1) {
      throw new IllegalArgumentException("interval: " + interval);
    }
  }

  /** Return the limit as a rate string n/interval */
  public String getRate() {
    if (rate == null) {
      rate = rateString();
    }
    return rate;
  }

  /** Return the limit on the number of events */
  public int getLimit() {
    return events;
  }

  /** Return the interval over which events are limited */
  public long getInterval() {
    return interval;
  }

  /** Return true if the rate limiter is of specified rate */
  public boolean isRate(String rate) {
    return getRate().equals(rate);
  }

  /** Return true if the rate limiter is of specified rate */
  public boolean isRate(int events, long interval) {
    return this.events == events && this.interval == interval;
  }

  /** Return true iff the rate limiter imposes no limit */
  public boolean isUnlimited() {
    return time == null;
  }

  /** Change the rate */
  public synchronized void setRate(String newRate) {
    if (!isRate(newRate)) {
      Ept ept = new Ept(newRate);
      checkRate(ept.events, ept.interval, true);
      setRate0(ept.events, ept.interval);
      rate = newRate;
    }
  }

  /** Change the rate */
  public synchronized void setRate(String newRate, String dfault) {
    if (!isRate(newRate)) {
      Ept ept;
      try {
	ept = new Ept(newRate);
	checkRate(ept.events, ept.interval, true);
      } catch (RuntimeException e) {
	log.warning("Configured rate (" + rate +
		    ") illegal, using default (" + dfault + ")");
	newRate = dfault;
	ept = new Ept(newRate);
	checkRate(ept.events, ept.interval, true);
      }
      setRate0(ept.events, ept.interval);
      rate = newRate;
    }
  }

  /** Change the rate */
  public synchronized void setRate(int newEvents, long newInterval) {
    if (!isRate(newEvents, newInterval)) {
      checkRate(newEvents, newInterval, false);
      setRate0(newEvents, newInterval);
      rate = null;
    }
  }

  private void setRate0(int newEvents, long newInterval) {
    if (newInterval != this.interval) {
      if (newInterval == 0 || this.interval == 0) {
	init(newEvents, newInterval);
	return;
      } else {
	this.interval = newInterval;
      }
    }
    if (events != newEvents) {
      this.time = resizeEventArray(time, count, newEvents);
      this.events = newEvents;
      count = 0;
    }
  }

  /** Return an array of size newEvents with all, or the logically last
   * newEvents elements from the source array inserted in proper order at
   * the end.  The resulting array assumes that the current pointer will be
   * reset to zero.  This is a purely functional method so it can be easily
   * tested.  It is static to ensure that it's functional. */
  static long[] resizeEventArray(long[] arr, int ptr, int newEvents) {
    int oldEvents = arr.length;
    long res[] = new long[newEvents];
    int p = newEvents;
    if (ptr != 0) {
      int alen = ptr < p ? ptr : p;
      p -= alen;
      System.arraycopy(arr, ptr - alen, res, p, alen);
    }
    int blen = (oldEvents - ptr) < p ? (oldEvents - ptr) : p;
    p -= blen;
    System.arraycopy(arr, oldEvents - blen, res, p, blen);
    if (p > 0) {
      Arrays.fill(res, 0, p, 0);
    }
    return res;
  }

  /** Record an occurrence of the event */
  public synchronized void event() {
    if (!isUnlimited()) {
      time[count] = TimeBase.nowMs();
      count = (count + 1) % events;
    }
  }

  /** Return true if an event could occur now without exceeding the limit */
  public synchronized boolean isEventOk() {
    if (isUnlimited()) {
      return true;
    }
    return time[count] == 0 || TimeBase.msSince(time[count]) >= interval;
  }

  /** Return the amount of time until the next event is allowed */
  public synchronized long timeUntilEventOk() {
    if (isUnlimited()) {
      return 0;
    }
    long res = TimeBase.msUntil(time[count] + interval);
    return (res > 0) ? res : 0;
  }

  /** Wait until the next event is allowed */
  public synchronized boolean waitUntilEventOk() throws InterruptedException {
    long time = timeUntilEventOk();
    if (time <= 0) {
      return true;
    }
    Deadline.in(time).sleep();
    return true;
  }

  public String rateString() {
    if (isUnlimited()) {
      return "unlimited";
    }
    return events + "/" + StringUtil.timeIntervalToString(interval);
  }

  public String toString() {
    return "[RL: " + getRate() + "]";
  }

  /** A RateLimiter whose rate cannot be reset */
  static class Constant extends RateLimiter {
    public Constant(int events, long interval) {
      super(events, interval);
    }

    public Constant(String rate) {
      super(rate);
    }

    public void setRate(String newRate) {
      ill();
    }

    public void setRate(String newRate, String dfault) {
      ill();
    }

    public void setRate(int newEvents, long newInterval) {
      ill();
    }

    private void ill() {
      throw new
	UnsupportedOperationException("Can't change constant RateLimiter");
    }
  }

  private static Pool pool = new Pool();

  /** Return the {@link RateLimiter.Pool} of shared, named RateLimiters */
  public static Pool getPool() {
    return pool;
  }

  /** A pool of named RateLimiters, to facilitate sharing between,
   * <i>eg</i>, AUs */
  public static class Pool {
    private Map limiterMap;

    Pool() {
      limiterMap = new HashMap();
    }

    /** Find or create a new RateLimiter associated with the key.
     * @param key An object that identifies the shared resource which which
     * the RateLimiter should be associated (<i>eg</i>, plugin, host name,
     * server name)
     * @param rate the rate to which a new RateLimiter will be set, or an
     * existing one reset.
     * @throws IllegalArgumentException if the rate if illegal */
    public synchronized RateLimiter findNamedRateLimiter(Object key,
							 String rate) {
      return findNamedRateLimiter(key, rate, null);
    }
      
    /** Find or create a new RateLimiter associated with the key.
     * @param key An object that identifies the shared resource which which
     * the RateLimiter should be associated (<i>eg</i>, plugin, host name,
     * server name)
     * @param rate the rate to which a new RateLimiter will be set, or an
     * existing one reset.
     * @param dfault the default rate to use if the rate is illegal.
     * @throws IllegalArgumentException if the default rate if illegal */
    public synchronized RateLimiter findNamedRateLimiter(Object key,
							 String rate,
							 String dfault) {
      RateLimiter limiter = (RateLimiter)limiterMap.get(key);
      if (limiter == null) {
	limiter = RateLimiter.makeRateLimiter(rate, dfault);
	limiterMap.put(key, limiter);
      } else if (!limiter.isRate(rate)) {
	limiter.setRate(rate, dfault);
      }
      return limiter;
    }

    /** Find or create a new RateLimiter associated with the key.
     * @param key An object that identifies the shared resource which which
     * the RateLimiter should be associated (<i>eg</i>, plugin, host name,
     * server name)
     * @param events the numerator of the rate to which a new RateLimiter
     * will be set, or an existing one reset.
     * @param interval the denominator of the rate to which a new
     * RateLimiter will be set, or an existing one reset.
     * @throws IllegalArgumentException if the rate is illegal */
    public synchronized RateLimiter findNamedRateLimiter(Object key,
							 int events,
							 long interval) {
      RateLimiter limiter = (RateLimiter)limiterMap.get(key);
      if (limiter == null) {
	limiter = new RateLimiter(events, interval);
	limiterMap.put(key, limiter);
      } else if (!limiter.isRate(events, interval)) {
	limiter.setRate(events, interval);
      }
      return limiter;
    }
  }
}
