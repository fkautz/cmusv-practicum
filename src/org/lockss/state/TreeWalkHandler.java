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

package org.lockss.state;

import java.util.*;
import org.lockss.util.*;
import org.lockss.plugin.*;
import org.lockss.crawler.CrawlManager;
import org.lockss.poller.PollSpec;
import org.lockss.daemon.Configuration;
import org.lockss.daemon.RangeCachedUrlSetSpec;
import org.lockss.app.LockssDaemon;
import org.lockss.daemon.ActivityRegulator;

/**
 * The treewalk thread handler in the NodeManager.  This starts a thread which
 * executes treewalks on a regular basis.
 */
public class TreeWalkHandler {
  public static final String TREEWALK_PREFIX =
      Configuration.PREFIX + "treewalk.";

  /**
   * Configuration parameter name for interval, in ms, between treewalks.
   */
  public static final String PARAM_TREEWALK_INTERVAL =
      TREEWALK_PREFIX + "interval";
  static final long DEFAULT_TREEWALK_INTERVAL = Constants.HOUR;

  /**
   * Configuration parameter name for interval, in ms, between treewalks.
   */
  public static final String PARAM_TREEWALK_START_DELAY =
      TREEWALK_PREFIX + "start.delay";
  static final long DEFAULT_TREEWALK_START_DELAY = Constants.MINUTE;

  static final double MAX_DEVIATION = 0.4;

  NodeManagerImpl manager;
  private static LockssDaemon theDaemon;
  private static CrawlManager theCrawlManager;
  private static ActivityRegulator theRegulator;
  private ArchivalUnit theAu;

  private Logger logger = Logger.getLogger("TreeWalkHandler");

  TreeWalkThread treeWalkThread;
  long treeWalkInterval;
  long topPollInterval;
  long treeWalkTestDuration;
  long startDelay;

  boolean treeWalkAborted;
  boolean forceTreeWalk = false;

  long treeWalkEstimate = -1;

  Configuration.Callback configCallback;

  TreeWalkHandler(NodeManagerImpl manager, LockssDaemon theDaemon) {
    this.manager = manager;
    theAu = manager.managedAu;
    this.theCrawlManager = theDaemon.getCrawlManager();
    this.theRegulator = theDaemon.getActivityRegulator(theAu);
    this.theDaemon = theDaemon;

    configCallback = new Configuration.Callback() {
      public void configurationChanged(Configuration newConfig,
                                       Configuration oldConfig,
                                       Set changedKeys) {
        setConfig(newConfig, oldConfig);
      }
    };
    Configuration.registerConfigurationCallback(configCallback);
  }

  private void setConfig(Configuration config, Configuration oldConfig) {
    treeWalkInterval = config.getTimeInterval(
        PARAM_TREEWALK_INTERVAL, DEFAULT_TREEWALK_INTERVAL);
    startDelay = config.getTimeInterval(
        PARAM_TREEWALK_START_DELAY, DEFAULT_TREEWALK_START_DELAY);
    logger.debug3("treeWalkInterval reset to "+treeWalkInterval);
  }

/**
 * The full treewalk only proceeds if no new content crawls or top level polls
 * are needed on the content.  As part of checking whether it should execute,
 * it triggers these actions by the CrawlManager and the NodeManager before
 * aborting its treewalk attempt.
 */
  void doTreeWalk() {
    logger.debug("Attempting tree walk: " + theAu.getName());

    //get expiration time
    long expiration = treeWalkInterval;
    if (getAverageTreeWalkDuration() > 0) {
      expiration = 2 * getAverageTreeWalkDuration();
    }
    // check with regulator to see if treewalk can proceed
    if (theRegulator.startAuActivity(ActivityRegulator.TREEWALK, expiration)) {
      try {
        // check with crawl manager
        if (theAu.shouldCrawlForNewContent(manager.getAuState())) {
          theRegulator.auActivityFinished(ActivityRegulator.TREEWALK);
          treeWalkAborted = true;
          theCrawlManager.startNewContentCrawl(theAu, null, null);
          logger.debug("Requested new content crawl.  Aborting...");
        }
        else {
          // do the actual treewalk
          logger.debug("Tree walk started: " + theAu.getName());
          long startTime = TimeBase.nowMs();
          nodeTreeWalk();
          long elapsedTime = TimeBase.msSince(startTime);
          updateEstimate(elapsedTime);
        }
        // after finishing treewalk successfully, check if we should schedule
        // a top-level poll (this way we handle damage first)
        if (!treeWalkAborted &&
            (theAu.shouldCallTopLevelPoll(manager.getAuState()))) {
          // query the AU if a top level poll should be started
          theRegulator.auActivityFinished(ActivityRegulator.TREEWALK);
          treeWalkAborted = true;
          manager.callTopLevelPoll();
          logger.debug("Requested top level poll...");
        }
      }
      finally {
        if (!treeWalkAborted) {
          theRegulator.auActivityFinished(ActivityRegulator.TREEWALK);
        } else {
          treeWalkAborted = false;
        }
      }
    } else {
      logger.debug2("Treewalk couldn't start due to activity lock.");
    }

    //alert the AuState (it writes through)
    manager.getAuState().setLastTreeWalkTime();
    logger.debug("Tree walk finished.");
  }

  private void nodeTreeWalk() {
    CachedUrlSet cus = theAu.getAUCachedUrlSet();
    recurseTreeWalk(cus);
  }

  /**
   * Recursive function which checks a CUS's children, then checks the state of
   * the given CUS (if no action was taken below it).
   * @param cus the {@link CachedUrlSet} to walk
   * @return true if the treewalk should continue
   */
  boolean recurseTreeWalk(CachedUrlSet cus) {
    if (treeWalkAborted) {
      // treewalk has been terminated
      return false;
    }
    boolean pContinue = true;
    // get the node state for the cus
    logger.debug3("Recursing treewalk on cus: "+cus.getUrl());
    NodeState parent = manager.getNodeState(cus);
    // walk the node's children first to process deepest damage first
    Iterator children = cus.flatSetIterator();
    while (children.hasNext()) {
      CachedUrlSetNode node = (CachedUrlSetNode)children.next();
      if (node.getType()==CachedUrlSetNode.TYPE_CACHED_URL_SET) {
        // recurse on the child cus
        //XXX recursion should be aware of total data size, and avoid
        // stack overflow
        // returns false if the treewalk shouldn't continue
        pContinue = recurseTreeWalk((CachedUrlSet)node);
      } else if (node.getType()==CachedUrlSetNode.TYPE_CACHED_URL) {
        // open a new state for the leaf and walk
        NodeState state = manager.getNodeState(
            theAu.makeCachedUrlSet(new RangeCachedUrlSetSpec(node.getUrl())));
        pContinue = checkNodeState(state);
      }
      if (!pContinue) {
        break;
      }
    }

    // if we took no action below here, check this node
    if (pContinue) {
      return checkNodeState(parent);
    }
    return false;
  }

  /**
   * Checks the state of a specific {@link NodeState}.  Returns true if no
   * state was detected (or action taken) which would interfere with continuing
   * the treewalk below this node.
   * @param node the {@link NodeState} to check
   * @return true if treewalk can continue below
   */
  boolean checkNodeState(NodeState node) {
    // at each node, check for recrawl needed
    if (node.getCachedUrlSet().hasContent()) {
      //XXX if (theCrawlManager.shouldRecrawl(managerAu, node)) {
      // then CrawlManager.scheduleBackgroundCrawl()
      // return false;
    }
    // check recent histories to see if something needs fixing
    PollHistory lastHistory = node.getLastPollHistory();
    if (lastHistory != null) {
      // give the last history to the manager to check for consistency
      if (manager.checkLastHistory(lastHistory, node, true)) {
        logger.debug3("Calling poll on node '"+
                      node.getCachedUrlSet().getUrl()+"'");
        // free treewalk state
        theRegulator.auActivityFinished(ActivityRegulator.TREEWALK);
        // take appropriate action
        manager.checkLastHistory(lastHistory, node, false);
        // abort treewalk
        treeWalkAborted = true;
        return false;
      }
    }
    return true;
  }

  /**
   * The amount of time, in ms, before the next treewalk should start.
   * @return time in ms
   */
  long timeUntilTreeWalkStart() {
    // if treewalk is forced
    if (forceTreeWalk) {
      logger.debug("Forcing treewalk start-in time of -1.");
      forceTreeWalk = false;
      return -1;
    }
    long lastTreeWalkTime = manager.getAuState().getLastTreeWalkTime();
    long timeSinceLastTW = TimeBase.msSince(lastTreeWalkTime);
    logger.debug3(StringUtil.timeIntervalToString(timeSinceLastTW) +
                  " since last treewalk");
    return treeWalkInterval - timeSinceLastTW;
  }

  /**
   * Ends the treewalk process.
   */
  public void end() {
    logger.debug3("TreeWalkHandler terminated.");
    if (treeWalkThread!=null) {
      treeWalkThread.end();
    }
    Configuration.unregisterConfigurationCallback(configCallback);
  }

  /**
   * Starts the treewalk process.
   */
  public void start() {
    logger.debug2("TreeWalkHandler started.");
    logger.debug2("Treewalks should happen every "+
                  StringUtil.timeIntervalToString(treeWalkInterval));
    treeWalkAborted = false;
    if (treeWalkThread==null) {
      treeWalkThread = new TreeWalkThread();
      treeWalkThread.start();
    }
  }

  /**
   * Used for testing.  Starts a treewalk if one isn't already running.
   */
  void forceTreeWalk() {
    boolean threadWasNull = false;
    if (treeWalkThread==null) {
      treeWalkThread = new TreeWalkThread();
      threadWasNull = true;
    }
    if (!treeWalkThread.doingTreeWalk) {
      forceTreeWalk = true;
      if (threadWasNull) {
        treeWalkThread.start();
      } else {
        // just wake it up
        treeWalkThread.deadline.expire();
      }
    }
  }

  /**
   * The thread which handles the treewalk itself.
   */
  class TreeWalkThread extends Thread {
    private boolean goOn = true;
    boolean doingTreeWalk = false;
    boolean randomDelay = true;
    Deadline deadline;
    private static final long SMALL_SLEEP = Constants.SECOND;

    public TreeWalkThread() {
      super("TreeWalk: "+theAu.getName());
    }

    public void run() {
      while (goOn) {
        long timeToStart = timeUntilTreeWalkStart();
        if (timeToStart <= 0) {
          if (randomDelay) {
            // only random delay the first time, to allow better test communication
            long delta = (long) ( (double) MAX_DEVIATION * startDelay);
            deadline = Deadline.inRandomRange(startDelay, startDelay + delta);
            logger.debug3("Random sleep for "+
                          StringUtil.timeIntervalToString(
                deadline.getRemainingTime()));
            try {
              deadline.sleep();
            } catch (InterruptedException ie) { }
            randomDelay = false;
          } else if (!theDaemon.isDaemonRunning()) {
            // if the daemon isn't up yet, do a short sleep
            logger.debug2("Daemon not running yet. Sleeping...");
            deadline = Deadline.in(SMALL_SLEEP);
            try {
              deadline.sleep();
            } catch (InterruptedException ie) { }
          } else {
            doingTreeWalk = true;
            doTreeWalk();
            doingTreeWalk = false;
          }
        } else {
          long delta = (long) ( (double) MAX_DEVIATION * timeToStart);
          logger.debug3("Creating a deadline for " +
                        StringUtil.timeIntervalToString(timeToStart) +
                        " with delta of " +
                        StringUtil.timeIntervalToString(delta));
          deadline = Deadline.inRandomRange(timeToStart, timeToStart + delta);
          try {
            deadline.sleep();
          } catch (InterruptedException ie) { }
        }
      }
    }

    public void end() {
      goOn = false;
      if (doingTreeWalk) {
        theRegulator.auActivityFinished(ActivityRegulator.TREEWALK);
      }
      treeWalkAborted = true;
      if (deadline != null) {
        deadline.expire();
      }
    }
  }

  /*
   * Returns the current treewalk average.  -1 until a treewalk is run.
   * @return the estimate, in ms
   */
  long getAverageTreeWalkDuration() {
    return treeWalkEstimate;
  }

  void updateEstimate(long elapsedTime) {
    if (treeWalkEstimate==-1) {
      treeWalkEstimate = elapsedTime;
    } else {
      // average with current estimate
      treeWalkEstimate = (treeWalkEstimate + elapsedTime) / 2;
    }
  }

}
