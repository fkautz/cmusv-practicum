/*
 * $Id$
 */

/*

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

package org.lockss.poller;

import java.io.*;
import java.security.*;
import java.util.*;

import gnu.regexp.*;
import org.mortbay.util.B64Code;
import org.lockss.daemon.*;
import org.lockss.hasher.*;
import org.lockss.plugin.*;
import org.lockss.protocol.*;
import org.lockss.util.*;
import org.lockss.state.PollHistory;
import org.lockss.state.NodeManager;
import org.lockss.daemon.status.*;

/**
 * PollTally is a struct-like class which maintains the current state of
 * votes within a poll.
 */
public abstract class PollTally {
  public static final int STATE_POLLING = 0;
  public static final int STATE_ERROR = 1;
  public static final int STATE_NOQUORUM = 2;
  public static final int STATE_RESULTS_TOO_CLOSE = 3;
  public static final int STATE_RESULTS_UNTRUSTED = 4;
  public static final int STATE_WON = 5;
  public static final int STATE_LOST = 6;
  public static final int STATE_UNVERIFIED = 7;
  public static final int STATE_VERIFIED = 8;
  public static final int STATE_DISOWNED = 9;
  public static final int STATE_SUSPENDED = 10;

  PollSpec pollSpec;
  String key;
  Poll poll;
  int type;
  long startTime;
  long duration;
  int numAgree;     // The # of votes that agree with us
  int numDisagree;  // The # of votes that disagree with us
  int wtAgree;      // The weight of the votes that agree with us
  int wtDisagree;   // The weight of the votes that disagree with us
  int quorum;       // The # of votes needed to have a quorum
  int status;
  ArrayList pollVotes;
  String hashAlgorithm; // the algorithm used to hash this poll
  long m_createTime;       // poll creation time

  List localEntries = null;  // the local entries less the remaining RegExp
  List votedEntries = null;  // entries which match the won votes in a poll
  protected Deadline replayDeadline = null;
  protected Iterator replayIter = null;
  protected ArrayList originalVotes = null;
  protected static IdentityManager idManager = null;

  static Logger log=Logger.getLogger("PollTally");

  PollTally(int type, long startTime, long duration, int numAgree,
            int numDisagree, int wtAgree, int wtDisagree, int quorum,
            String hashAlgorithm) {
    this.type = type;
    this.startTime = startTime;
    this.duration = duration;
    this.numAgree = numAgree;
    this.numDisagree = numDisagree;
    this.wtAgree = wtAgree;
    this.wtDisagree = wtDisagree;
    this.quorum = quorum;
    pollVotes = new ArrayList(quorum * 2);
    this.hashAlgorithm = hashAlgorithm;
    log.warning("Constructor type " + type + " " + this.toString());
  }

  abstract public String toString();

  /**
   * return the unique key for the poll for this tally
   * @return a String representing the key
   */
  public String getPollKey() {
    return key;
  }

  /**
   * Returns true if the poll belongs to this Identity
   * @return true if this Identity
   */
  public boolean isMyPoll() {
    return poll.isMyPoll();
  }

  /**
   * Return the poll spec used by this poll
   * @return the PollSpec
   */
  public PollSpec getPollSpec() {
    return pollSpec;
  }

  public CachedUrlSet getCachedUrlSet() {
    return poll.m_cus;
  }

  public ArchivalUnit getArchivalUnit()  {
    return getCachedUrlSet().getArchivalUnit();
  }

  /**
   * Returns poll type constant - one of Poll.NamePoll, Poll.ContentPoll,
   * Poll.VerifyPoll
   * @return integer constant for this poll
   */
  public int getType() {
    return type;
  }

  /**
   * returns the poll start time
   * @return start time as a long
   */
  public long getStartTime() {
    return startTime;
  }

  /**
   * returns the poll duration
   * @return the duration as a long
   */
  public long getDuration() {
    return duration;
  }

  /**
   * return the votes cast in this poll
   * @return the list of votes
   */

  public List getPollVotes() {
    return Collections.unmodifiableList(pollVotes);
  }

  /**
   * return an interator for the set of entries tallied during the vote
   * @return the completed list of entries
   */
  public Iterator getCorrectEntries() {
    return votedEntries == null ? CollectionUtil.EMPTY_ITERATOR :
        votedEntries.iterator();
  }

  /**
   * return an interator for the set of entries we have locally
   * @return the list of entries
   */
  public Iterator getLocalEntries() {
    return localEntries == null ? CollectionUtil.EMPTY_ITERATOR :
        localEntries.iterator();
  }


  abstract public boolean isErrorState();

  abstract public boolean isInconclusiveState();

  /**
   * get the error state for this poll
   * @return 0 == NOERR or one of the poll err conditions
   */
  abstract public int getErr();

  abstract public String getErrString();

  public int getStatus() {
    return status;
  }

  abstract public String getStatusString();

  abstract void tallyVotes();

  abstract void verifyTally();

  abstract boolean isLeadEnough();

  abstract boolean haveQuorum();

  abstract boolean isWithinMargin();

  abstract public boolean isTrustedResults();

  boolean hasVoted(LcapIdentity voterID) {
    Iterator it = pollVotes.iterator();
    while(it.hasNext()) {
      Vote vote = (Vote) it.next();
      if(voterID.isEqual(vote.getIDAddress())) {
        return true;
      }
    }
    return false;
  }

  abstract void adjustReputation(LcapIdentity voterID, int repDelta);

  abstract void addVote(Vote vote, LcapIdentity id, boolean isLocal);

  /**
   * replay all of the votes in a previously held poll.
   * @param deadline the deadline by which the replay must be complete
   */
  public void startReplay(Deadline deadline) {
    originalVotes = pollVotes;
    pollVotes = new ArrayList(originalVotes.size());
    replayIter =  originalVotes.iterator();
    replayDeadline = deadline;
    numAgree = 0;
    numDisagree = 0;
    wtAgree = 0;
    wtDisagree = 0;
    replayNextVote();
  }

  void replayNextVote() {
    if(replayIter == null) {
      log.warning("Call to replay a poll vote without call to replay all");
    }
    if(poll.isErrorState() || !replayIter.hasNext()) {
      replayIter = null;
      poll.stopPoll();
    }
    else {
      Vote vote = (Vote)replayIter.next();
      replayVoteCheck(vote, replayDeadline);
    }
  }


  /**
   * replay a previously checked vote
   * @param vote the vote to recheck
   * @param deadline the deadline by which the check must complete
   */

  abstract void replayVoteCheck(Vote vote, Deadline deadline);

  public static class NameListEntry {
    public boolean hasContent;
    public String name;

    public NameListEntry(boolean hasContent, String name) {
      this.hasContent = hasContent;
      this.name = name;
    }

    /**
     * Overrides Object.equals().
     * Returns true if the obj is the same object and the names are the same
     * @param obj the Object to compare
     * @return the hashcode
     */
    public boolean equals(Object obj) {
      if (obj instanceof NameListEntry) {
        NameListEntry entry = (NameListEntry) obj;
        return name.equals(entry.name);
      }
      return false;
    }

    /**
     * Overrides Object.hashCode().
     * Returns the hash of the strings
     * @return the hashcode
     */
    public int hashCode() {
      return name.hashCode();
    }
  }

}



