/*
 * $Id$
 *

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

package org.lockss.daemon;
import java.io.*;
import java.util.*;
import org.lockss.app.*;
import org.lockss.util.*;

/** LockssThread abstracts out common features of LOCKSS daemon threads,
 * notably watchdog timers.  The methods in this class should be called
 * only from the thread.
 */
public abstract class LockssThread extends Thread implements LockssWatchdog {
  static final String PREFIX = Configuration.PREFIX + "thread.";

  static final String PARAM_THREAD_WDOG_EXIT_IMM =
    Configuration.PREFIX + "exitImmediately";
  static final boolean DEFAULT_THREAD_WDOG_EXIT_IMM = true;

  public static final String PARAM_NAMED_WDOG_INTERVAL =
    PREFIX + "<name>.watchdog.interval";

  public static final String PARAM_NAMED_THREAD_PRIORITY =
    PREFIX + "<name>.priority";

  private static Logger log = Logger.getLogger("LockssThread");

  private OneShotSemaphore runningSem = new OneShotSemaphore();
  private Map wdogParamNameMap = new HashMap();
  private Map prioParamNameMap = new HashMap();
  private volatile boolean triggerOnExit = false;
  private volatile long interval = 0;
  private TimerQueue.Request timerReq;
  private Deadline timerDead;
  private TimerQueue.Callback timerCallback =
    new TimerQueue.Callback() {
      public void timerExpired(Object cookie) {
	// Don't trigger the watchdog if it has been turned off.  This could
	// happen if the timer event happens as it's being cancelled.
	if (interval != 0) {
	  threadHung();
	}
      }};

  private static boolean watchdogDisabled = false;

  /** Globally disable the watchdog.  Thread watchdogs can cause problems
   * during unit testing, esp. when switching between simulated and real
   * time.  LockssTestCase uses this to disable the watchdog during unit
   * tests.
   * @param disable disables watchdog if true, enables if false.
   * @return the previous state of the disable flag. */
  public static boolean disableWatchdog(boolean disable) {
    boolean old = watchdogDisabled;
    watchdogDisabled = disable;
    return old;
  }

  protected LockssThread(String name) {
    super(name);
  }

  /** Declare that the thread is running and initialized. */
  protected void nowRunning() {
    runningSem.fill();
  }

  /** Wait until the thread is running and initialized.  Useful to ensure
   * the thread has finished its initialization before it is asked to do
   * anything.  If used in the method that starts the thread, prevents test
   * code from finishing before the thread starts, which can cause
   * watchdogs to trigger spuriously.
   * @param timeout Deadline that limits how long to wait for the thread to
   * start running.
   * @return true iff the thread has said it's running, false if timeout or
   * interrupted.
   */
  public boolean waitRunning(Deadline timeout) {
    try {
      return runningSem.waitFull(timeout);
    } catch (InterruptedException e) {
    }
    return runningSem.isFull();
  }

  /** Wait until the thread is running and initialized.  Useful to ensure
   * the thread has finished its initialization before it is asked to do
   * anything.  If used in the method that starts the thread, prevents test
   * code from finishing before the thread starts, which can cause
   * watchdogs to trigger spuriously.
   * @return true iff the thread has said it's running, false if timeout or
   * interrupted.
   */
  public boolean waitRunning() {
    return waitRunning(Deadline.MAX);
  }

  /** Set the priority of the thread from a parameter value
   * @param name Used to derive a configuration parameter name
   * (org.lockss.thread.<i>name</i>.priority) whose value is the priority
   * @param defaultPriority the default priority if the config param has no
   * value.  If this is -1 and the config param has no value, the priority
   * will not be changed.
   */
  public void setPriority(String name, int defaultPriority) {
    int prio = getPriorityFromParam(name, defaultPriority);
    if (prio != -1) {
      log.debug("Setting priority of " + getName() + " thread to " + prio);
      setPriority(prio);
    }
  }

  /** Start a watchdog timer that will expire if not poked for interval
   * milliseconds.  Calls {@link #threadHung()} if triggered.
   * @param interval milliseconds after which watchdog will go off.
   */
  public void startWDog(long interval) {
    if (watchdogDisabled) {
      return;
    }
    stopWDog();
    if (interval != 0) {
      this.interval = interval;
      logEvent("Starting", true);
      timerDead = Deadline.in(interval);
      timerReq = TimerQueue.schedule(timerDead, timerCallback, null);
    } else {
      logEvent("Not starting", true);
    }
  }

  /** Start a watchdog timer that will expire if not poked for interval
   * milliseconds.  Calls {@link #threadHung()} if triggered.
   * @param name Used to derive a configuration parameter name
   * (org.lockss.thread.<i>name</i>.watchdog.interval) whose value is the
   * watchdog interval.
   * @param defaultInterval the default interval if the config param has no
   * value.
   */
  public void startWDog(String name, long defaultInterval) {
    startWDog(getIntervalFromParam(name, defaultInterval));
  }

  /** Stop the watchdog so that it will not trigger. */
  public void stopWDog() {
    if (interval != 0) {
      interval = 0;
      if (timerReq != null) {
	TimerQueue.cancel(timerReq);
	timerReq = null;
      }
      logEvent("Stopping", false);
    }
  }

  /** Refresh the watchdog for another interval milliseconds. */
  public void pokeWDog() {
    if (timerDead != null) {
      timerDead.expireIn(interval);
      logEvent("Resetting", false);
    }
  }

  /** Set whether thread death should trigger the watchdog.  The default is
   * false; threads that are supposed to be persistent and never exit
   * should set this true. */
  public void triggerWDogOnExit(boolean triggerOnExit) {
    if (watchdogDisabled) {
      return;
    }
    if (this.triggerOnExit != triggerOnExit) {
      logEvent(triggerOnExit ?
	       "Enabling thread exit" : "Disabling thread exit",
	       false);
    }
    this.triggerOnExit = triggerOnExit;
  }

  /** Called if thread is hung (hasn't poked the watchdog in too long).
   * Default action is to exit the daemon; can be overridden is thread is
   * able to take some less drastic corrective action (e.g., close socket
   * for hung socket reads.) */
  protected void threadHung() {
    exitDaemon("Thread hung for " + StringUtil.timeIntervalToString(interval));
  }

  /** Called if thread exited unexpectedly.  Default action is to exit the
   * daemon; can be overridden is thread is able to take some less drastic
   * corrective action. */
  protected void threadExited() {
    exitDaemon("Thread exited");
  }

  private void exitDaemon(String msg) {
    boolean exitImm = true;
    try {
      WatchdogService wdog = (WatchdogService)
	LockssDaemon.getManager(LockssDaemon. WATCHDOG_SERVICE);
      if (wdog != null) {
	wdog.forceStop();
      }
      log.error(msg + ": " + getName());
      exitImm = Configuration.getBooleanParam(PARAM_THREAD_WDOG_EXIT_IMM,
					      DEFAULT_THREAD_WDOG_EXIT_IMM);
    } finally {
      if (exitImm) {
	System.exit(1);
      }
    }
  }

  long getIntervalFromParam(String name, long defaultInterval) {
    String param = (String)wdogParamNameMap.get(name);
    if (param == null) {
      param = StringUtil.replaceString(PARAM_NAMED_WDOG_INTERVAL,
				       "<name>", name);
      wdogParamNameMap.put(name, param);
    }
    return Configuration.getTimeIntervalParam(param, defaultInterval);
  }

  int getPriorityFromParam(String name, int defaultInterval) {
    String param = (String)prioParamNameMap.get(name);
    if (param == null) {
      param = StringUtil.replaceString(PARAM_NAMED_THREAD_PRIORITY,
				       "<name>", name);
      prioParamNameMap.put(name, param);
    }
    return Configuration.getIntParam(param, defaultInterval);
  }

  private void logEvent(String event, boolean includeInterval) {
    if (log.isDebug3()) {
      StringBuffer sb = new StringBuffer();
      sb.append(event);
      sb.append(" thread watchdog (");
      sb.append(getName());
      sb.append(")");
      if (includeInterval) {
	sb.append(": ");
	sb.append(StringUtil.timeIntervalToString(interval));
      }
      log.debug3(sb.toString());
    }
  }

  /** Invoke the subclass's lockssRun() method, then cancel any outstanding
   * thread watchdog */
  public final void run() {
    try {
      lockssRun();
    } catch (RuntimeException e) {
      log.warning("Thread threw", e);
    } finally {
      if (triggerOnExit) {
	threadExited();
      } else {
	stopWDog();
      }
    }
  }

  /** Subclasses must implement this in place of the run() method */
  protected abstract void lockssRun();
}
