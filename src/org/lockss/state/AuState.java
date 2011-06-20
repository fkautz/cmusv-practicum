/*
 * $Id: AuState.java,v 1.43 2011/06/20 07:00:06 tlipkis Exp $
 */

/*

Copyright (c) 2000-2008 Board of Trustees of Leland Stanford Jr. University,
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
import org.lockss.plugin.ArchivalUnit;
import org.lockss.plugin.Plugin;
import org.lockss.util.*;
import org.lockss.daemon.*;
import org.lockss.crawler.CrawlerStatus;
import org.lockss.poller.v3.*;
import org.lockss.repository.*;

/**
 * AuState contains the state information for an au.
 */
public class AuState implements LockssSerializable {

  private static final Logger logger = Logger.getLogger("AuState");

  /** The number of updates between writing to file  (currently unused) */
  static final int URL_UPDATE_LIMIT = 1;

  public enum AccessType {OpenAccess, Subscription};


  // Persistent state vars
  protected long lastCrawlTime;		// last successful crawl
  protected long lastCrawlAttempt;
  protected String lastCrawlResultMsg;
  protected int lastCrawlResult;
  protected long lastTopLevelPoll;	// last completed poll
  protected long lastPollStart;		// last time a poll started
  protected String lastPollResultMsg;
  protected int lastPollResult;
  protected long pollDuration;		// average of last two poll durations
  protected int clockssSubscriptionStatus;
  protected double v3Agreement = -1.0;
  protected double highestV3Agreement = -1.0;
  protected AccessType accessType;
  protected SubstanceChecker.State hasSubstance;
  protected String substanceVersion;
  protected String metadataVersion;

  protected transient long lastPollAttempt; // last time we attempted to
					    // start a poll

  // Non-persistent state vars

  // saves previous lastCrawl* state while crawl is running
  protected transient AuState previousCrawlState = null;

  // saves previous lastPoll* state while poll is running
  protected transient AuState previousPollState = null;

  // Runtime (non-state) vars
  protected transient ArchivalUnit au;
  private transient HistoryRepository historyRepo;

  // deprecated, kept for compatibility with old state files
  protected transient long lastTreeWalk = -1;
  // should be deprecated?
  protected HashSet crawlUrls;
  // deprecated, kept for compatibility with old state files
  /** @deprecated */
  protected transient boolean hasV3Poll = false;

  transient int urlUpdateCntr = 0;

  public AuState(ArchivalUnit au, HistoryRepository historyRepo) {
    this(au, -1, -1, -1, -1, -1, null,
	 CLOCKSS_SUB_UNKNOWN, -1.0, -1.0, historyRepo);
  }

  public AuState(ArchivalUnit au,
		 long lastCrawlTime, long lastCrawlAttempt,
		 long lastTopLevelPoll, long lastPollStart,
		 long lastTreeWalk, HashSet crawlUrls,
		 int clockssSubscriptionStatus,
		 double v3Agreement, double highestV3Agreement,
		 HistoryRepository historyRepo) {
    this(au,
	 lastCrawlTime, lastCrawlAttempt, -1, null,
	 lastTopLevelPoll, lastPollStart, -1, null, 0,
	 lastTreeWalk,
	 crawlUrls, null, clockssSubscriptionStatus,
	 v3Agreement, highestV3Agreement,
	 SubstanceChecker.State.Unknown,
	 historyRepo);
  }

  public AuState(ArchivalUnit au,
		 long lastCrawlTime, long lastCrawlAttempt,
		 int lastCrawlResult, String lastCrawlResultMsg,
		 long lastTopLevelPoll, long lastPollStart,
		 int lastPollResult, String lastPollResultMsg,
		 long pollDuration,
		 long lastTreeWalk, HashSet crawlUrls,
		 AccessType accessType,
		 int clockssSubscriptionStatus,
		 double v3Agreement,
		 double highestV3Agreement,
		 SubstanceChecker.State hasSubstance,
		 HistoryRepository historyRepo) {
    this.au = au;
    this.lastCrawlTime = lastCrawlTime;
    this.lastCrawlAttempt = lastCrawlAttempt;
    this.lastCrawlResult = lastCrawlResult;
    this.lastCrawlResultMsg = lastCrawlResultMsg;
    this.lastTopLevelPoll = lastTopLevelPoll;
    this.lastPollStart = lastPollStart;
    this.lastPollResult = lastPollResult;
    this.lastPollResultMsg = lastPollResultMsg;
    this.pollDuration = pollDuration;
    this.lastTreeWalk = lastTreeWalk;
    this.crawlUrls = crawlUrls;
    this.accessType = accessType;
    this.clockssSubscriptionStatus = clockssSubscriptionStatus;
    this.v3Agreement = v3Agreement;
    this.highestV3Agreement = highestV3Agreement;
    this.hasSubstance = hasSubstance;
    this.historyRepo = historyRepo;
  }

  /**
   * Returns the au
   * @return the au
   */
  public ArchivalUnit getArchivalUnit() {
    return au;
  }

  public boolean isCrawlActive() {
    return previousCrawlState != null;
  }

  public boolean isPollActive() {
    return previousPollState != null;
  }

  /**
   * Returns the date/time the au was created.
   * @return au creation time
   * If there is a Lockss repository exception, this method returns -1.
   */
  public long getAuCreationTime() {
    try {
      return historyRepo.getAuCreationTime();
    } catch (LockssRepositoryException e) {
      logger.error("getAuCreationTime: LockssRepositoryException: " + e.getMessage());
      return -1;
    }
  }

  /**
   * Returns the last new content crawl time of the au.
   * @return the last crawl time in ms
   */
  public long getLastCrawlTime() {
    return lastCrawlTime;
  }

  /**
   * Returns the last time a new content crawl was attempted.
   * @return the last crawl time in ms
   */
  public long getLastCrawlAttempt() {
    if (isCrawlActive()) {
      return previousCrawlState.getLastCrawlAttempt();
    }
    return lastCrawlAttempt;
  }

  /**
   * Returns the result code of the last new content crawl
   */
  public int getLastCrawlResult() {
    if (isCrawlActive()) {
      return previousCrawlState.getLastCrawlResult();
    }
    return lastCrawlResult;
  }

  /**
   * Returns the result of the last new content crawl
   */
  public String getLastCrawlResultMsg() {
    if (isCrawlActive()) {
      return previousCrawlState.getLastCrawlResultMsg();
    }
    if (lastCrawlResultMsg == null) {
      return CrawlerStatus.getDefaultMessage(lastCrawlResult);
    }
    return lastCrawlResultMsg;
  }

  /**
   * Returns true if the AU has ever successfully completed a new content
   * crawl
   */
  public boolean hasCrawled() {
    return getLastCrawlTime() >= 0;
  }

  /**
   * Returns the last time a top level poll completed.
   * @return the last poll time in ms
   */
  public long getLastTopLevelPollTime() {
    return lastTopLevelPoll;
  }

  /**
   * Returns the last time a poll started
   * @return the last poll time in ms
   */
  public long getLastPollStart() {
    if (isPollActive()) {
      return previousPollState.getLastPollStart();
    }
    return lastPollStart;
  }

  /**
   * Returns the last time a poll was attempted, since the last daemon
   * restart
   * @return the last poll time in ms
   */
  public long getLastPollAttempt() {
    return lastPollAttempt;
  }

  /**
   * Returns the result code of the last poll
   */
  public int getLastPollResult() {
    if (isPollActive()) {
      return previousPollState.getLastPollResult();
    }
    return lastPollResult;
  }

  /**
   * Returns the result of the last poll
   */
  public String getLastPollResultMsg() {
    if (isPollActive()) {
      return previousPollState.getLastPollResultMsg();
    }
    if (lastPollResultMsg == null) {
      try {
	return V3Poller.getStatusString(lastPollResult);
      } catch (IndexOutOfBoundsException e) {
	return null;
      }
    }
    return lastPollResultMsg;
  }

  /**
   * Returns the running average poll duration, or 0 if unknown
   */
  public long getPollDuration() {
    return pollDuration;
  }

  /**
   * Update the poll duration to the average of current and previous
   * average.  Return the new average.
   */
  public long setPollDuration(long duration) {
    if (pollDuration == 0) {
      pollDuration = duration;
    } else {
      pollDuration = (pollDuration + duration + 1) / 2;
    }
    return pollDuration;
  }

  /**
   * Returns the last treewalk time for the au.
   * @return the last treewalk time in ms
   */
  public long getLastTreeWalkTime() {
    return lastTreeWalk;
  }

  private void saveLastCrawl() {
    if (previousCrawlState != null) {
      logger.error("saveLastCrawl() called twice", new Throwable());
    }
    previousCrawlState = copy();
  }

  /**
   * Sets the last time a crawl was attempted.
   */
  public void newCrawlStarted() {
    saveLastCrawl();
    lastCrawlAttempt = TimeBase.nowMs();
    lastCrawlResult = Crawler.STATUS_RUNNING_AT_CRASH;
    lastCrawlResultMsg = null;
    historyRepo.storeAuState(this);
  }

  /**
   * Sets the last crawl time to the current time.  Saves itself to disk.
   */
  public void newCrawlFinished(int result, String resultMsg) {
    lastCrawlResultMsg = resultMsg;
    switch (result) {
    case Crawler.STATUS_SUCCESSFUL:
      lastCrawlTime = TimeBase.nowMs();
      // fall through
    default:
      lastCrawlResult = result;
      lastCrawlResultMsg = resultMsg;
      break;
    case Crawler.STATUS_ACTIVE:
      logger.warning("Storing Active state", new Throwable());
      break;
    }
    previousCrawlState = null;
    historyRepo.storeAuState(this);
  }

  private AuState copy() {
    return new AuState(au,
		       lastCrawlTime, lastCrawlAttempt,
		       lastCrawlResult, lastCrawlResultMsg,
		       lastTopLevelPoll, lastPollStart,
		       lastPollResult, lastPollResultMsg, pollDuration,
		       lastTreeWalk, crawlUrls,
		       accessType,
		       clockssSubscriptionStatus,
		       v3Agreement, highestV3Agreement,
		       hasSubstance,
		       null);
  }

  private void saveLastPoll() {
    if (previousPollState != null) {
      logger.error("saveLastPoll() called twice", new Throwable());
    }
    previousPollState = copy();
  }

  /**
   * Sets the last time a poll was started.
   */
  public void pollStarted() {
    saveLastPoll();
    lastPollStart = TimeBase.nowMs();
    lastPollResult = Crawler.STATUS_RUNNING_AT_CRASH;
    lastPollResultMsg = null;
    historyRepo.storeAuState(this);
  }

  /**
   * Sets the last time a poll was attempted.
   */
  public void pollAttempted() {
    lastPollAttempt = TimeBase.nowMs();
  }

  /**
   * Sets the last poll time to the current time.  Saves itself to disk.
   */
  public void pollFinished(int result, String resultMsg) {
    lastPollResultMsg = resultMsg;
    switch (result) {
    case V3Poller.POLLER_STATUS_COMPLETE:
      lastTopLevelPoll = TimeBase.nowMs();
      // fall through
    default:
      lastPollResult = result;
      lastPollResultMsg = resultMsg;
      break;
    }
    setPollDuration(TimeBase.msSince(lastPollAttempt));
    previousPollState = null;
    historyRepo.storeAuState(this);
  }

  /**
   * Sets the last poll time to the current time.  Saves itself to disk.
   */
  public void pollFinished(int result) {
    pollFinished(result, null);
  }

  /**
   * Sets the last poll time to the current time.  Saves itself to disk.
   */
  public void pollFinished() {
    pollFinished(V3Poller.POLLER_STATUS_COMPLETE, null);
  }

  public void setV3Agreement(double d) {
    v3Agreement = d;
    if (v3Agreement > highestV3Agreement) {
      highestV3Agreement = v3Agreement;
    }
    historyRepo.storeAuState(this);
  }

  public double getV3Agreement() {
    return v3Agreement;
  }
  
  public double getHighestV3Agreement() {
    // We didn't used to track highest, so return last if no highest recorded
    return v3Agreement > highestV3Agreement ? v3Agreement : highestV3Agreement;
  }
  
  public void setSubstanceState(SubstanceChecker.State state) {
    hasSubstance = state;
    setFeatureVersion(Plugin.Feature.Substance,
		      au.getPlugin().getFeatureVersion(Plugin.Feature.Substance));
  }

  public SubstanceChecker.State getSubstanceState() {
    if (hasSubstance == null) {
      hasSubstance = SubstanceChecker.State.Unknown;
    }
    return hasSubstance;
  }

  public boolean hasNoSubstance() {
    return hasSubstance == SubstanceChecker.State.No;
  }

  public String getFeatureVersion(Plugin.Feature feat) {
    switch (feat) {
    case Substance: return substanceVersion;
    case Metadata: return metadataVersion;
    default: return null;
    }
  }

  public void setFeatureVersion(Plugin.Feature feat, String ver) {
    switch (feat) {
    case Substance: substanceVersion = ver; break;
    case Metadata: metadataVersion = ver; break;
    default:
    }
    storeAuState();
  }

  /**
   * Sets the last treewalk time to the current time.  Does not save itself
   * to disk, as it is desireable for the treewalk to run every time the
   * server restarts.  Consequently, it is non-persistent.
   */
  void setLastTreeWalkTime() {
    lastTreeWalk = TimeBase.nowMs();
  }

  /**
   * Gets the collection of crawl urls.
   * @return a {@link Collection}
   */
  public HashSet getCrawlUrls() {
    if (crawlUrls==null) {
      crawlUrls = new HashSet();
    }
    return crawlUrls;
  }

  /**
   * Alert the AuState that the crawl url collection has been updated.  Waits
   * until URL_UPDATE_LIMIT updates have been made, then writes the state to
   * file.
   * @param forceWrite forces state storage if true
   */
  public void updatedCrawlUrls(boolean forceWrite) {
    urlUpdateCntr++;
    if (forceWrite || (urlUpdateCntr >= URL_UPDATE_LIMIT)) {
      historyRepo.storeAuState(this);
      urlUpdateCntr = 0;
    }
  }

  public void setAccessType(AccessType accessType) {
    // don't store, this will get stored at end of crawl
    this.accessType = accessType;
  }

  public AccessType getAccessType() {
    return accessType;
  }

  public boolean isOpenAccess() {
    return accessType == AccessType.OpenAccess;
  }

  // CLOCKSS status

  public static final int CLOCKSS_SUB_UNKNOWN = 0;
  public static final int CLOCKSS_SUB_YES = 1;
  public static final int CLOCKSS_SUB_NO = 2;
  public static final int CLOCKSS_SUB_INACCESSIBLE = 3;
  public static final int CLOCKSS_SUB_NOT_MAINTAINED = 4;

  /**
   * Return the CLOCKSS subscription status: CLOCKSS_SUB_UNKNOWN,
   * CLOCKSS_SUB_YES, CLOCKSS_SUB_NO
   */
  public int getClockssSubscriptionStatus() {
    return clockssSubscriptionStatus;
  }

  public String getClockssSubscriptionStatusString() {
    int status = getClockssSubscriptionStatus();
    switch (status) {
    case CLOCKSS_SUB_UNKNOWN: return "Unknown";
    case CLOCKSS_SUB_YES: return "Yes";
    case CLOCKSS_SUB_NO: return "No";
    case CLOCKSS_SUB_INACCESSIBLE: return "Inaccessible";
    case CLOCKSS_SUB_NOT_MAINTAINED: return "";
    default: return "Unknown status " + status;
    }
  }

  public void setClockssSubscriptionStatus(int val) {
    if (clockssSubscriptionStatus != val) {
      clockssSubscriptionStatus = val;
      historyRepo.storeAuState(this);
    }
  }

  public void storeAuState() {
    historyRepo.storeAuState(this);
  }

  public String toString() {
    StringBuffer sb = new StringBuffer();
    sb.append("[AuState: ");
    sb.append("lastCrawlTime=");
    sb.append(new Date(lastCrawlTime));
    sb.append(", ");
    sb.append("lastCrawlAttempt=");
    sb.append(new Date(lastCrawlAttempt));
    sb.append(", ");
    sb.append("lastCrawlResult=");
    sb.append(lastCrawlResult);
    sb.append(", ");
    sb.append("lastTopLevelPoll=");
    sb.append(new Date(lastTopLevelPoll));
    sb.append(", ");
    sb.append("clockssSub=");
    sb.append(clockssSubscriptionStatus);
    sb.append("]");
    return sb.toString();
  }
}
