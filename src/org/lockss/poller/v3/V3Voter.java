/*
 * $Id$
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

package org.lockss.poller.v3;

import java.io.*;
import java.net.MalformedURLException;
import java.security.*;
import java.util.*;

import org.apache.commons.collections.*;

import org.lockss.app.*;
import org.lockss.config.*;
import org.lockss.daemon.CachedUrlSetHasher;
import org.lockss.hasher.*;
import org.lockss.plugin.*;
import org.lockss.poller.*;
import org.lockss.poller.v3.V3Serializer.PollSerializerException;
import org.lockss.protocol.*;
import org.lockss.protocol.V3LcapMessage.PollNak;
import org.lockss.protocol.psm.*;
import org.lockss.repository.RepositoryNode;
import org.lockss.state.*;
import org.lockss.scheduler.*;
import org.lockss.scheduler.Schedule.*;
import org.lockss.util.*;

/**
 * <p>Represents a voter in a V3 poll.</p>
 *
 * <p>State is maintained in a V3VoterState object, which is periodically 
 * serialized to disk so that polls may be resumed in case the daemon exits
 * before the poll is over.</p>
 */
public class V3Voter extends BasePoll {
  
  public static final int STATUS_INITIALIZED = 0;
  public static final int STATUS_ACCEPTED_POLL = 1;
  public static final int STATUS_HASHING = 2;
  public static final int STATUS_VOTED = 3;
  public static final int STATUS_NO_TIME = 4;
  public static final int STATUS_COMPLETE = 5;
  public static final int STATUS_EXPIRED = 6;
  public static final int STATUS_ERROR = 7;
  public static final int STATUS_DECLINED_POLL = 8;
  public static final int STATUS_VOTE_ACCEPTED = 9;
  public static final int STATUS_ABORTED = 10;
  
  public static final String[] STATUS_STRINGS = 
  {
   "Initialized", "Accepted Poll", "Hashing", "Voted",
   "No Time Available", "Complete", "Expired w/o Voting", "Error",
   "Declined Poll", "Vote Accepted", "Aborted",
  };

  private static final String PREFIX = Configuration.PREFIX + "poll.v3.";

  /** The minimum number of peers to select for a nomination message.
   * If there are fewer than this number of peers available to nominate,
   * an empty nomination message will be sent. */
  public static final String PARAM_MIN_NOMINATION_SIZE =
    PREFIX + "minNominationSize";
  public static final int DEFAULT_MIN_NOMINATION_SIZE = 1;

  /** The minimum number of peers to select for a nomination message. */
  public static final String PARAM_MAX_NOMINATION_SIZE = 
    PREFIX + "maxNominationSize";
  public static final int DEFAULT_MAX_NOMINATION_SIZE = 5;
  
  /** The maximum allowable number of simultaneous V3 Voters */
  public static final String PARAM_MAX_SIMULTANEOUS_V3_VOTERS =
    PREFIX + "maxSimultaneousV3Voters";
  public static final int DEFAULT_MAX_SIMULTANEOUS_V3_VOTERS = 60;
  
  /**
   * If false, do not serve any repairs via V3.
   */
  public static final String PARAM_ALLOW_V3_REPAIRS =
    PREFIX + "allowV3Repairs";
  public static final boolean DEFAULT_ALLOW_V3_REPAIRS = true;
  
  /**
   * If true, serve repairs to any trusted peer.  (A peer is trusted iff we
   * are communicating with it securely, and its identity has been verified
   * to match one of the public certs in our LCAP keystore.
   */
  public static final String PARAM_REPAIR_ANY_TRUSTED_PEER =
    PREFIX + "repairAnyTrustedPeer";
  public static final boolean DEFAULT_REPAIR_ANY_TRUSTED_PEER = false;

  /**
   * If true, use per-URL agreement to determine whether it's OK to serve
   * a repair.  If false, rely on partial agreement level for serving
   * repairs.
   */
  public static final String PARAM_ENABLE_PER_URL_AGREEMENT =
    PREFIX + "enablePerUrlAgreement";
  public static final boolean DEFAULT_ENABLE_PER_URL_AGREEMENT = false;
  
  /**
   * The minimum percent agreement required before we're willing to serve
   * repairs, if using per-AU agreement.
   */
  // CR: apply to bytes, not URLs
  public static final String PARAM_MIN_PERCENT_AGREEMENT_FOR_REPAIRS =
    PREFIX + "minPercentAgreementForRepairs";
  public static final double DEFAULT_MIN_PERCENT_AGREEMENT_FOR_REPAIRS = 0.5f; 

  /**
   * If true, previous agreement will be required to serve repairs even for
   * open access AUs
   */
  public static final String PARAM_OPEN_ACCESS_REPAIR_NEEDS_AGREEMENT =
    PREFIX + "openAccessRepairNeedsAgreement";
  public static final boolean
    DEFAULT_OPEN_ACCESS_REPAIR_NEEDS_AGREEMENT = false;

  /**
   * Extend reputation from old PID to new PID.  Reputation may be extended
   * from and to only one peer.  (E.g., both {A->B, A->C} and {B->A, C->A}
   * are illegal.) transitive mappings (E.g., {A->B, B->C}) are legal, up
   * to a macimum path length of 10.  This is for use by PLN admins when
   * changing IP of a node.  Should be replaced by secure, automatic
   * mechanism (e.g., box proves it's the same one by returning a
   * short-lived cookie from a recent poll).
   */
  public static final String PARAM_REPUTATION_TRANSFER_MAP =
    PREFIX + "reputationTransferMap";

  /** 
   * Allowance for vote message send time: hash time multiplier
   */
  public static final String PARAM_VOTE_SEND_HASH_MULTIPLIER =
    PREFIX + "voteMsgHashMultiplier";
  public static final double DEFAULT_VOTE_SEND_HASH_MULTIPLIER = 0.01;

  /** 
   * Allowance for vote message send time: padding
   */
  public static final String PARAM_VOTE_SEND_PADDING =
    PREFIX + "voteMsgPadding";
  public static final long DEFAULT_VOTE_SEND_PADDING = 15 * Constants.SECOND;

  /** 
   * Extra time added to the poll deadline (as sent by the poller) to 
   * wait for a receipt message.
   */
  public static final String PARAM_RECEIPT_PADDING = PREFIX + "receiptPadding";
  public static final long DEFAULT_RECEIPT_PADDING = 10 * Constants.MINUTE;

  /** 
   * Guess as to how long after we accept we'll get a vote request.
   * S.b. used to send vote request deadline to poller
   */
  public static final String PARAM_VOTE_REQUEST_DELAY =
    PREFIX + "voteRequestDelay";
  public static final long DEFAULT_VOTE_REQUEST_DELAY =
    30 * Constants.SECOND;

  /**
   * If true, previous agreement will be required to serve repairs even for
   * open access AUs
   */
  public static final String PARAM_RECALC_EXCESSIVE_HASH_ESTIMATE =
    PREFIX + "recalcExcessiveHashEstimate";
  public static final boolean DEFAULT_RECALC_EXCESSIVE_HASH_ESTIMATE = true;

  /** 
   * Factor of vote duration used as guess for duration of hash to recalc
   * hash estimate.
   */
  public static final String
    PARAM_RECALC_HASH_ESTIMATE_VOTE_DURATION_MULTIPLIER
    = PREFIX + "recalcHashEstimateVoteDurationMultiplier";
  /** Just used to document default */
  public static final String
    DEFAULT_RECALC_HASH_ESTIMATE_VOTE_DURATION_MULTIPLIER
    = "Twice reciprocal of " + V3Poller.PARAM_VOTE_DURATION_MULTIPLIER;

  /** Curve expressing decreasing weight of nominating peer who has last
   * voted in one of our polls X time ago.  X coord is time (ms) since last
   * vote, Y is float nomination weight.
   * @see org.lockss.util.CompoundLinearSlope */
  public static final String PARAM_NOMINATION_WEIGHT_AGE_CURVE =
    PREFIX + "nominationWeightAgeCurve";
  public static final String DEFAULT_NOMINATION_WEIGHT_AGE_CURVE =
    "[10d,1.0],[30d,0.1],[40d,0.01]";

  /** Curve giving vote message retry interval as a function of remaining
   * time before vote deadline.
   * @see org.lockss.util.CompoundLinearSlope */
  public static final String PARAM_VOTE_RETRY_INTERVAL_DURATION_CURVE =
    PREFIX + "voteRetryIntervalDurationCurve";
  public static final String DEFAULT_VOTE_RETRY_INTERVAL_DURATION_CURVE =
    "[5m,2m],[20m,4m],[1d,5h]";


  private PsmInterp stateMachine;
  private VoterUserData voterUserData;
  // CR: use global random
  private LockssRandom theRandom = new LockssRandom();
  private LockssDaemon theDaemon;
  private V3VoterSerializer pollSerializer;
  private PollManager pollManager;
  private LcapStreamComm scomm;
  private IdentityManager idManager;
  private boolean continuedPoll = false;
  private int nomineeCount;
  private File stateDir;
  private int blockErrorCount = 0;
  private int maxBlockErrorCount = V3Poller.DEFAULT_MAX_BLOCK_ERROR_COUNT;
  private boolean isAsynch;

  // Task used to reserve time for hashing at the start of the poll.
  // This task is cancelled before the real hash is scheduled.
  private SchedulableTask task;

  private static final Logger log = Logger.getLogger("V3Voter");

  // CR: refactor common parts of constructors
  /**
   * <p>Upon receipt of a request to participate in a poll, create a new
   * V3Voter.  The voter will not start running until {@link #startPoll()}
   * is called by {@link org.lockss.poller.v3.V3PollFactory}</p>
   */
  public V3Voter(LockssDaemon daemon, V3LcapMessage msg)
      throws V3Serializer.PollSerializerException {
    this.theDaemon = daemon;
    long padding =
      CurrentConfig.getTimeIntervalParam(V3Voter.PARAM_RECEIPT_PADDING,
                                         V3Voter.DEFAULT_RECEIPT_PADDING);
    long duration = msg.getDuration() + padding;

    log.debug3("Creating V3 Voter for poll: " + msg.getKey() +
               "; duration=" + StringUtil.timeIntervalToString(duration));

    pollSerializer = new V3VoterSerializer(theDaemon);
    
    stateDir = PollUtil.ensurePollStateRoot();

    maxBlockErrorCount =
      CurrentConfig.getIntParam(V3Poller.PARAM_MAX_BLOCK_ERROR_COUNT,
                                V3Poller.DEFAULT_MAX_BLOCK_ERROR_COUNT);

    try {
      this.voterUserData = new VoterUserData(new PollSpec(msg), this,
                                             msg.getOriginatorId(), 
                                             msg.getKey(),
                                             duration,
                                             msg.getHashAlgorithm(),
                                             msg.getPollerNonce(),
                                             PollUtil.makeHashNonce(20),
                                             msg.getEffortProof(),
                                             stateDir);
      voterUserData.setPollMessage(msg);
      voterUserData.setVoteDeadline(TimeBase.nowMs() + msg.getVoteDuration());
    } catch (IOException ex) {
      log.critical("IOException while trying to create VoterUserData: ", ex);
      stopPoll();
    }
    this.idManager = theDaemon.getIdentityManager();

    this.pollManager = daemon.getPollManager();
    this.scomm = daemon.getStreamCommManager();
    isAsynch = pollManager.isAsynch();

    int min = CurrentConfig.getIntParam(PARAM_MIN_NOMINATION_SIZE,
                                        DEFAULT_MIN_NOMINATION_SIZE);
    int max = CurrentConfig.getIntParam(PARAM_MAX_NOMINATION_SIZE,
                                        DEFAULT_MAX_NOMINATION_SIZE);
    if (min < 0) min = 0;
    if (max < 0) max = 0;
    if (min > max) {
      log.warning("Nomination size min (" +  min + ") > max (" + max
                  + "). Using min.");
      nomineeCount = min;
    } else if (min == max) {
      log.debug2("Minimum nominee size is same as maximum nominee size: " +
                 min);
      nomineeCount = min;
    } else {
      int r = theRandom.nextInt(max - min);
      nomineeCount = min + r;
    }
    log.debug2("Will choose " + nomineeCount
               + " outer circle nominees to send to poller");
    stateMachine = makeStateMachine(voterUserData);
    checkpointPoll();
  }

  /**
   * <p>Restore a V3Voter from a previously saved poll.  This method is called
   * by {@link org.lockss.poller.PollManager} when the daemon starts up if a
   * serialized voter is found.</p>
   */
  public V3Voter(LockssDaemon daemon, File pollDir)
      throws V3Serializer.PollSerializerException {
    this.theDaemon = daemon;
    // CR: why pollDir passed to some V3VoterSerializer constructors, not
    // others?
    this.pollSerializer = new V3VoterSerializer(theDaemon, pollDir);
    this.voterUserData = pollSerializer.loadVoterUserData();
    this.pollManager = daemon.getPollManager();
    this.scomm = daemon.getStreamCommManager();
    isAsynch = pollManager.isAsynch();
    this.continuedPoll = true;
    // Restore transient state.
    PluginManager plugMgr = theDaemon.getPluginManager();
    CachedUrlSet cus = plugMgr.findCachedUrlSet(voterUserData.getAuId());
    if (cus == null) {
      throw new NullPointerException("CUS for AU " + voterUserData.getAuId() +
                                     " is null!");
    }
    // Restore transient state
    voterUserData.setCachedUrlSet(cus);
    voterUserData.setPollSpec(new PollSpec(cus, Poll.V3_POLL));
    voterUserData.setVoter(this);

    stateMachine = makeStateMachine(voterUserData);
  }

  PsmInterp newPsmInterp(PsmMachine stateMachine, Object userData) {
    PsmManager mgr = theDaemon.getPsmManager();
    PsmInterp interp = mgr.newPsmInterp(stateMachine, userData);
    interp.setThreaded(isAsynch);
    return interp;
  }

  private PsmInterp makeStateMachine(final VoterUserData ud) {
    PsmMachine machine = makeMachine();
    PsmInterp interp = newPsmInterp(machine, ud);
    interp.setName(PollUtil.makeShortPollKey(getKey()));
    interp.setCheckpointer(new PsmInterp.Checkpointer() {
      public void checkpoint(PsmInterpStateBean resumeStateBean) {
        voterUserData.setPsmState(resumeStateBean);
        checkpointPoll();
      }
    });

    return interp;
  }
  
  public PsmInterp getPsmInterp() {
    return stateMachine;
  }

  /**
   * Provides a default no-arg constructor to be used for unit testing.
   */
  protected V3Voter() {
    
  }
  
  /**
   * <p>Reserve enough schedule time to hash our content and send our vote.</p>
   * 
   * @return True if time could be scheduled, false otherwise.
   */
  public boolean reserveScheduleTime() {
    long voteDeadline = voterUserData.getVoteDeadline();
    long estimatedHashDuration = getCachedUrlSet().estimatedHashDuration();
    long now = TimeBase.nowMs();

    // Ensure the vote deadline has not already passed.
    if (voteDeadline <= now) {
      String msg = "Vote deadline has already "
        + "passed.  Can't reserve schedule time.";
      voterUserData.setErrorDetail(msg);
      log.warning(msg);
      return false;
    }
    
    long voteReqDelay =
      CurrentConfig.getTimeIntervalParam(PARAM_VOTE_REQUEST_DELAY,
                                         DEFAULT_VOTE_REQUEST_DELAY);

    Deadline earliestStart = Deadline.at(now + voteReqDelay);
    // CR: eliminate reservation task; schedule hash here

    long messageSendPadding =
      calculateMessageSendPadding(estimatedHashDuration);

    Deadline latestFinish =
      Deadline.at(voterUserData.getVoteDeadline() - messageSendPadding);

    long voteDuration = latestFinish.minus(earliestStart);
    long schedDuration = getSchedDuration(voteDuration);

    if (estimatedHashDuration > voteDuration) {
      String msg = "Estimated hash duration (" 
        + StringUtil.timeIntervalToString(estimatedHashDuration) 
        + ") is too long to complete within the voting period ("
        + StringUtil.timeIntervalToString(voteDuration) + ")";
      voterUserData.setErrorDetail(msg);
      log.warning(msg);
      recalcHashEstimate(voterUserData.getVoteDeadline() - now);
      return false;
    }

    TaskCallback tc = new TaskCallback() {
      public void taskEvent(SchedulableTask task, EventType type) {
        // do nothing... yet!
      }
    };
    
    // Keep a hold of the task we're scheduling.
    this.task = new StepTask(earliestStart, latestFinish,
                             estimatedHashDuration,
                             tc, this) {
      public int step(int n) {
        // finish immediately, in case we start running
        setFinished();
        return n;
      }
    };

    boolean suc = theDaemon.getSchedService().scheduleTask(task);
    if (!suc) {
      voterUserData.setErrorDetail("No time for hash: " + task +
				   " at " + TimeBase.nowDate());
      log.warning("No time for hash: " + task);
    }
    return suc;
  }

  // Calculate min interval scheduler will require to accept a task,
  // including overhead.
  long getSchedDuration(long voteDuration) {
    Configuration config = ConfigManager.getCurrentConfig();
    double overheadLoad =
      config.getPercentage(SortScheduler.PARAM_OVERHEAD_LOAD,
			   SortScheduler.DEFAULT_OVERHEAD_LOAD);
    return (long)(voteDuration / (1.0 - overheadLoad));
  }

  /* XXX Ideally this would be a function of the number of vote blocks, but
   * that isn't available.  Instead, proportional to hash estimate, plus
   * padding  */
  private long calculateMessageSendPadding(long hashEst) {
    double mult =
      CurrentConfig.getDoubleParam(PARAM_VOTE_SEND_HASH_MULTIPLIER,
				   DEFAULT_VOTE_SEND_HASH_MULTIPLIER);
    return (long)(hashEst * mult)
      + CurrentConfig.getTimeIntervalParam(PARAM_VOTE_SEND_PADDING,
					   DEFAULT_VOTE_SEND_PADDING);
  }

  PsmInterp.ErrorHandler ehAbortPoll(final String msg) {
    return new PsmInterp.ErrorHandler() {
	public void handleError(PsmException e) {
	  log.warning(msg, e);
	  abortPollWithError();
	}
      };
  }

  private void sendNak(PollNak nak) {
    V3LcapMessage msg = voterUserData.makeMessage(V3LcapMessage.MSG_POLL_ACK);
    msg.setVoterNonce(null);
    msg.setNak(nak);
    try {
      sendMessageTo(msg, getPollerId());
      pollManager.countVoterNakEvent(nak);      
    } catch (IOException ex) {
      log.error("Unable to send POLL NAK message in poll " + getKey(), ex);
    }
  }

  void recalcHashEstimate(long voteDuration) {
    RecalcHashTime rht =
      new RecalcHashTime(theDaemon, getAu(), 2,
			 getHashAlgorithm(), voteDuration);
    rht.recalcHashTime();
    return;
  }

  /**
   * <p>Start the V3Voter running and participate in the poll.  Called by
   * {@link org.lockss.poller.v3.V3PollFactory} when a vote request message
   * has been received, and by {@link org.lockss.poller.PollManager} when
   * restoring serialized voters.</p>
   */
  public void startPoll() {
    log.debug("Starting poll " + voterUserData.getPollKey());
    Deadline pollDeadline = null;
    if (!continuedPoll) {
      // Skip deadline sanity check if this is a restored poll.
      pollDeadline = Deadline.at(voterUserData.getDeadline());
    } else {
      pollDeadline = Deadline.restoreDeadlineAt(voterUserData.getDeadline());
    }
    
    // If this poll has already expired, don't start it.
    if (pollDeadline.expired()) {
      log.info("Not restoring expired voter for poll " +
               voterUserData.getPollKey());
      stopPoll(STATUS_EXPIRED);
      return;
    }

    // First, see if we have time to participate.  If not, there's no
    // point in going on.
    if (reserveScheduleTime()) {
      long voteDeadline = voterUserData.getVoteDeadline();
      if (voteDeadline >= pollDeadline.getExpirationTime()) {
        log.warning("Voting deadline (" + voteDeadline + ") is later than " +
                    "the poll deadline (" + pollDeadline.getExpirationTime() + 
                    ").  Can't participate in poll " + getKey());
	// CR: s.b. poller error, not expired
        stopPoll(STATUS_EXPIRED);
        return;
      }
      log.debug("Found enough time to participate in poll " + getKey());
    } else {
      sendNak(V3LcapMessage.PollNak.NAK_NO_TIME);
      stopPoll(STATUS_NO_TIME);
      return;
    }

    // Register a callback for the end of the poll.
    TimerQueue.schedule(pollDeadline, new PollTimerCallback(), this);

    // Register a callback for the end of the voting period.  We must have
    // voted by this time, or we can't participate.
    // CR: could be folded into hash done cb
    TimerQueue.schedule(Deadline.at(voterUserData.getVoteDeadline()),
                        new TimerQueue.Callback() {
      public void timerExpired(Object cookie) {
        // In practice, we must prevent this from happening. Unfortunately,
        // due to the nature of the scheduler and the wide variety of machines
        // in the field, it is quite possible for us to still be hashing when
        // the vote deadline has arrived.
        // 
        // It's the poller's responsibility to ensure that it compensates for
        // slow machines by padding the vote deadline as much as necessary to
        // compensate for slow machines.
        if (!voterUserData.hashingDone()) {
          log.warning("Vote deadline has passed before my hashing was done " +
                      "in poll " + getKey() + ". Stopping the poll.");
          stopPoll(V3Voter.STATUS_EXPIRED);
        }
      }
    }, this);
    
    // Resume or start the state machine running.
    if (isAsynch) {
      if (continuedPoll) {
	String msg = "Error resuming poll";
	try {
	  stateMachine.enqueueResume(voterUserData.getPsmState(),
				     ehAbortPoll(msg));
	} catch (PsmException e) {
	  log.warning(msg, e);
	  abortPollWithError();
	}
      } else {
	String msg = "Error starting poll";
	try {
	  stateMachine.enqueueStart(ehAbortPoll(msg));
	} catch (PsmException e) {
	  log.warning(msg, e);
	  abortPollWithError();
	}
      }
    } else {
      if (continuedPoll) {
	try {
	  stateMachine.resume(voterUserData.getPsmState());
	} catch (PsmException e) {
	  log.warning("Error resuming poll", e);
	  abortPollWithError();
	}
      } else {
	try {
	  stateMachine.start();
	} catch (PsmException e) {
	  log.warning("Error starting poll", e);
	  abortPollWithError();
	}
      }
    }
  }

  /**
   * Stop the poll and tell the {@link PollManager} to let go of us.
   * 
   * @param status The final status code of the poll, for the status table.
   */
  public void stopPoll(final int status) {
    if (voterUserData.isPollActive()) {
      voterUserData.setActivePoll(false);
    } else {
      return;
    }
    if (task != null && !task.isExpired()) {
      log.debug2("Cancelling poll time reservation task");
      task.cancel();
    }
    voterUserData.setStatus(status);
    // Clean up after the serializer
    pollSerializer.closePoll();
    pollManager.closeThePoll(voterUserData.getPollKey());
    log.debug2("Closed poll " + voterUserData.getPollKey() + " with status " +
               getStatusString() );
    release();
  }
  
  /**
   * Stop the poll with STATUS_COMPLETE.
   */
  public void stopPoll() {
    stopPoll(STATUS_COMPLETE);
  }

  /**
   * Stop the poll with STATUS_ERROR.
   */
  public void abortPoll() {
    stopPoll(STATUS_ERROR);
  }

  /**
   * Stop the poll with STATUS_ERROR.
   */
  private void abortPollWithError() {
    stopPoll(STATUS_ERROR);
  }

  private Class getVoterActionsClass() {
    return VoterActions.class;
  }

  /**
   * Send a message to the poller.
   */
  void sendMessageTo(V3LcapMessage msg, PeerIdentity id)
      throws IOException {
    if (log.isDebug2()) {
      log.debug2("sendTo(" + msg + ", " + id + ")");
    }
    pollManager.sendMessageTo(msg, id);
  }

  /**
   * Handle an incoming V3LcapMessage.
   */
  public void receiveMessage(LcapMessage message) {
    // It's quite possible to receive a message after we've decided
    // to close the poll, but before the PollManager knows we're closed.
    if (voterUserData.isPollCompleted()) return;

    final V3LcapMessage msg = (V3LcapMessage)message;
    PeerIdentity sender = msg.getOriginatorId();
    PsmMsgEvent evt = V3Events.fromMessage(msg);
    log.debug3("Received message: " + message.getOpcodeString() + " " + message);
    String errmsg = "State machine error";
    if (isAsynch) {
      stateMachine.enqueueEvent(evt, ehAbortPoll(errmsg),
				new PsmInterp.Action() {
				  public void eval() {
				    msg.delete();
				  }
				});
    } else {
      try {
	stateMachine.handleEvent(evt);
      } catch (PsmException e) {
	log.warning(errmsg, e);
	abortPollWithError();
      }
    }
    // Finally, clean up after the V3LcapMessage
    msg.delete();    
  }

  /**
   * Generate a list of outer circle nominees.
   */
  public void nominatePeers() {
    // XXX:  'allPeers' should probably contain only peers that have agreed with
    //       us in the past for this au.
    if (idManager == null || voterUserData == null) {
      log.warning("nominatePeers called on a possibly closed poll: "
                  + getKey());
      return;
    }

    Collection<PeerIdentity> nominees;
    DatedPeerIdSet noAuSet = pollManager.getNoAuPeerSet(getAu());
    synchronized (noAuSet) {
      try {
	try {
	  noAuSet.load();
	  pollManager.ageNoAuSet(getAu(), noAuSet);
	} catch (IOException e) {
	  log.error("Failed to load no AU set", e);
	  noAuSet.release();
	  noAuSet = null;
	}
	nominees = idManager.getTcpPeerIdentities(new NominationPred(noAuSet));
      } finally {
	if (noAuSet != null) {
	  noAuSet.release();
	}
      }
    }
    if (nomineeCount <= nominees.size()) {
      Map availablePeers = new HashMap();
      for (PeerIdentity id : nominees) {
	availablePeers.put(id, nominateWeight(id));
      }
      nominees = CollectionUtil.weightedRandomSelection(availablePeers,
							nomineeCount);
    }
    if (!nominees.isEmpty()) {
      // VoterUserData expects the collection to be KEYS, not PeerIdentities.
      ArrayList nomineeStrings = new ArrayList(nominees.size());
      for (PeerIdentity id : nominees) {
	nomineeStrings.add(id.getIdString());
      }
      voterUserData.setNominees(nomineeStrings);
      log.debug2("Nominating the following peers: " + nomineeStrings);
    } else {
      log.warning("No peers to nominate");
    }
    checkpointPoll();
  }

  // Don't nominate peers unless have positive evidence of correct group.
  // Also, no aging as with poll invites
  class NominationPred implements Predicate {
    DatedPeerIdSet noAuSet;

    NominationPred(DatedPeerIdSet noAuSet) {
      this.noAuSet = noAuSet;
    }

    public boolean evaluate(Object obj) {
      if (obj instanceof PeerIdentity) {
	PeerIdentity pid = (PeerIdentity)obj;
	// Never nominate the poller
	if (pid == voterUserData.getPollerId()) {
	  return false;
	}
	try {
	  if (noAuSet != null && noAuSet.contains(pid)) {
	    return false;
	  }
	} catch (IOException e) {
	  log.warning("Couldn't chech NoAUSet", e);
	}
	PeerIdentityStatus status = idManager.getPeerIdentityStatus(pid);
	if (status == null) {
	  return false;
	}
	List hisGroups = status.getGroups();
	if (hisGroups == null || hisGroups.isEmpty()) {
	  return false;
	}
	List myGroups = ConfigManager.getPlatformGroupList();
	if (!CollectionUtils.containsAny(hisGroups, myGroups)) {
	  return false;
	}
	return true;
      }
      return false;
    }
  }

  /**
   * Compute the weight that a peer should be given for consideration for
   * nomination into the poll.
   *  
   * @param status
   * @return A double between 0.0 and 1.0 representing the invitation
   * weight that we want to give this peer.
   */
  double nominateWeight(PeerIdentity pid) {
    PeerIdentityStatus status = idManager.getPeerIdentityStatus(pid);
    CompoundLinearSlope nominationWeightCurve =
      pollManager.getNominationWeightAgeCurve();
    if (nominationWeightCurve  == null) {
      return 1.0;
    }
    long lastVoteTime = status.getLastVoterTime();
    long noVoteFor = TimeBase.nowMs() - lastVoteTime;
    return nominationWeightCurve.getY(noVoteFor);
  }


  /**
   * Create an array of byte arrays containing hasher initializer bytes for
   * this voter.  The result will be an array of two byte arrays:  The first
   * has no initializing bytes, and will be used for the plain hash.  The
   * second is constructed by concatenating the poller nonce and voter nonce,
   * and will be used for the challenge hash.
   *
   * @return Block hasher initialization bytes.
   */
  private byte[][] initHasherByteArrays() {
    return new byte[][] {
        {}, // Plain Hash
        ByteArray.concat(voterUserData.getPollerNonce(),
                         voterUserData.getVoterNonce()) // Challenge Hash
    };
  }

  /**
   * Create the message digesters for this voter's hasher -- one for
   * the plain hash, one for the challenge hash.
   *
   * @return An array of MessageDigest objects to be used by the BlockHasher.
   */
  private MessageDigest[] initHasherDigests() throws NoSuchAlgorithmException {
    return PollUtil.createMessageDigestArray(2, getHashAlgorithm());
  }

  private String getHashAlgorithm() {
    String hashAlg = voterUserData.getHashAlgorithm();
    if (hashAlg == null) {
      hashAlg = LcapMessage.DEFAULT_HASH_ALGORITHM;
    }
    return hashAlg;
  }

  /**
   * Schedule a hash.
   */
  boolean generateVote() throws NoSuchAlgorithmException {
    log.debug("Scheduling vote hash for poll " + voterUserData.getPollKey());
    CachedUrlSetHasher hasher =
      new BlockHasher(voterUserData.getCachedUrlSet(),
		      initHasherDigests(),
		      initHasherByteArrays(),
		      new BlockEventHandler());
    HashService hashService = theDaemon.getHashService();
    Deadline hashDeadline = task.getLatestFinish();

    // Cancel the old task.
    task.cancel();

    boolean scheduled = false;
    try {
      // Schedule the hash using the old task's latest finish as the deadline.
      scheduled =
	hashService.scheduleHash(hasher, hashDeadline,
				 new HashingCompleteCallback(), null);
    } catch (IllegalArgumentException e) {
      log.error("Error scheduling hash time", e);
    }
    if (scheduled) {
      log.debug("Successfully scheduled time for vote in poll " +
		getKey());
    } else {
      log.debug("Unable to schedule time for vote.  Dropping " +
                "out of poll " + getKey());
    }
    return scheduled;
  }

  /**
   * Called by the HashService callback when hashing for this CU is
   * complete.
   */
  public void hashComplete() {
    // The task should have been canceled by now if the poll ended before
    // hashing was complete, but it may not have been.  If stateMachine
    // is null, the poll has ended and its resources have been released.
    if (stateMachine == null) {
      log.debug("HashService callback called hashComplete() on a poll " +
      		"that was over.  Poll key = " + getKey());
      return;
    }
    
    // If we've received a vote request, send our vote right away.  Otherwise,
    // wait for a vote request.
    log.debug("Hashing complete for poll " + voterUserData.getPollKey());
    String errmsg = "State machine error";
    if (isAsynch) {
      stateMachine.enqueueEvent(V3Events.evtHashingDone,
				ehAbortPoll(errmsg));
    } else {
      try {
	stateMachine.handleEvent(V3Events.evtHashingDone);
      } catch (PsmException e) {
	log.warning(errmsg, e);
	abortPollWithError();
      }
    }
  }

  /*
   * Append the results of the block hasher to the VoteBlocks for this
   * voter.
   *
   * Called by the BlockHasher's event handler callback when hashing is complete
   * for one block.
   */
  public void blockHashComplete(HashBlock block) {
    // Add each hash block version to this vote block.
    VoteBlock vb = new VoteBlock(block.getUrl());
    Iterator hashVersionIter = block.versionIterator();
    while(hashVersionIter.hasNext()) {
      HashBlock.Version ver = (HashBlock.Version)hashVersionIter.next();
      byte[] plainDigest = ver.getHashes()[0];
      byte[] challengeDigest = ver.getHashes()[1];
      vb.addVersion(ver.getFilteredOffset(),
                    ver.getFilteredLength(),
                    ver.getUnfilteredOffset(),
                    ver.getUnfilteredLength(),
                    plainDigest,
                    challengeDigest,
                    ver.getHashError() != null);
    }
    
    // Add this vote block to our hash block container.
    VoteBlocks blocks = voterUserData.getVoteBlocks();
    try {
      blocks.addVoteBlock(vb);
    } catch (IOException ex) {
      log.error("Unexpected IO Exception trying to add vote block " +
                vb.getUrl() + " in poll " + getKey(), ex);
      if (++blockErrorCount > maxBlockErrorCount) {
        log.critical("Too many errors while trying to create my vote blocks, " +
                     "aborting participation in poll " + getKey());
        abortPollWithError();
      }
    }
  }

  public void setMessage(LcapMessage msg) {
    voterUserData.setPollMessage(msg);
  }

  public long getCreateTime() {
    return voterUserData.getCreateTime();
  }

  public long getHashStartTime() {
    if (task != null) {
      return task.getEarliestStart().getExpirationTime();
    } else {
      return 0;
    }
  }

  public PeerIdentity getCallerID() {
    return voterUserData.getPollerId();
  }
  
  public File getStateDir() {
    if (pollSerializer != null) {
      return pollSerializer.pollDir;
    }
    return null;
  }

  // Not used by V3.
  protected boolean isErrorState() {
    return false;
  }

  // Not used by V3.
  public boolean isMyPoll() {
    // Always return false
    return false;
  }

  public PollSpec getPollSpec() {
    return voterUserData.getPollSpec();
  }

  public CachedUrlSet getCachedUrlSet() {
    return voterUserData.getCachedUrlSet();
  }

  public int getVersion() {
    return voterUserData.getPollVersion();
  }

  public LcapMessage getMessage() {
    return voterUserData.getPollMessage();
  }

  public String getKey() {
    return voterUserData.getPollKey();
  }

  public Deadline getDeadline() {
    return Deadline.restoreDeadlineAt(voterUserData.getDeadline());
  }
  
  public Deadline getVoteDeadline() {
    return Deadline.restoreDeadlineAt(voterUserData.getVoteDeadline());
  }

  public long getDuration() {
    return voterUserData.getDuration();
  }

  public byte[] getPollerNonce() {
    return voterUserData.getPollerNonce();
  }

  public byte[] getVoterNonce() {
    return voterUserData.getVoterNonce();
  }

  public PollTally getVoteTally() {
    throw new UnsupportedOperationException("V3Voter does not have a tally.");
  }

  private class HashingCompleteCallback implements HashService.Callback {
    /**
     * Called when the timer expires or hashing is complete.
     *
     * @param cookie data supplied by caller to schedule()
     */
    public void hashingFinished(CachedUrlSet cus, long timeUsed, Object cookie,
                                CachedUrlSetHasher hasher, Exception e) {
      if (!isPollActive()) {
	log.warning("Hash finished after poll closed: " + getKey());
	return;
      }
      if (e == null) {
        hashComplete();
      } else {
        if (e instanceof SchedService.Timeout) {
          log.warning("Hash deadline passed before the hash was finished.");
	  sendNak(V3LcapMessage.PollNak.NAK_HASH_TIMEOUT);
          stopPoll(STATUS_EXPIRED);
        } else {
          log.warning("Hash failed : " + e.getMessage(), e);
          voterUserData.setErrorDetail(e.getMessage());
	  sendNak(V3LcapMessage.PollNak.NAK_HASH_ERROR);
          abortPollWithError();
        }
      }
    }
  }

  private class BlockEventHandler implements BlockHasher.EventHandler {
    public void blockStart(HashBlock block) { 
      log.debug2("Poll " + getKey() + ": Starting hash for block " 
                 + block.getUrl());
    }
    public void blockDone(HashBlock block) {
      if (!isPollActive()) return;

      log.debug2("Poll " + getKey() + ": Ending hash for block " 
                 + block.getUrl());
      blockHashComplete(block);
    }
  }

  public int getType() {
    return Poll.V3_POLL;
  }
  
  public LockssApp getLockssDaemon() {
    return theDaemon;
  }

  public ArchivalUnit getAu() {
    return voterUserData.getCachedUrlSet().getArchivalUnit();
  }

  public PeerIdentity getPollerId() {
    return voterUserData.getPollerId();
  }

  public PollManager getPollManager() {
    return pollManager;
  }

  public boolean isPollActive() {
    return voterUserData.isPollActive();
  }

  public boolean isPollCompleted() {
    return voterUserData.isPollCompleted();
  }

  public VoterUserData getVoterUserData() {
    return voterUserData;
  }
  
  public String getStatusString() {
    return V3Voter.STATUS_STRINGS[voterUserData.getStatus()];
  }
  
  public int getStatus() {
    return voterUserData.getStatus();
  }
  
  IdentityManager getIdentityManager() {
    return this.idManager;
  }
  
  /**
   * Returns true if we will serve a repair to the given peer for the
   * given AU and URL.
   */
  boolean serveRepairs(PeerIdentity pid, ArchivalUnit au, String url) {
    if (idManager == null) {
      log.warning("serveRepairs called on a possibly closed poll: "
                  + getKey());
      return false;
    }
    boolean allowRepairs = 
      CurrentConfig.getBooleanParam(PARAM_ALLOW_V3_REPAIRS,
                                    DEFAULT_ALLOW_V3_REPAIRS);
    
    // Short circuit.
    if (!allowRepairs) return false;
    
    if (!CurrentConfig.getBooleanParam(PARAM_OPEN_ACCESS_REPAIR_NEEDS_AGREEMENT,
				       DEFAULT_OPEN_ACCESS_REPAIR_NEEDS_AGREEMENT)) {
      AuState aus = AuUtil.getAuState(au);
      if (aus.isOpenAccess()) {
	return true;
      }
    }
    if (scomm.isTrustedNetwork() &&
	CurrentConfig.getBooleanParam(PARAM_REPAIR_ANY_TRUSTED_PEER,
				      DEFAULT_REPAIR_ANY_TRUSTED_PEER)) {
      return true;
    }
    return serveRepairs(pid, au, url, 10);
  }

  /** Return true if pid is entitled to a repair of url.  If false, see if
   * another peer's reputation has been extended to pid; if so check that
   * one. */
  private boolean serveRepairs(PeerIdentity pid, ArchivalUnit au,
			       String url, int depth) {
    if (serveRepairsTo(pid, au, url)) {
      return true;
    }
    if (depth > 0) {
      PeerIdentity reputationPid =
	pollManager.getReputationTransferredFrom(pid);
      if (reputationPid != null) {
	return serveRepairs(reputationPid, au, url, depth - 1);
      }
    }
    return false;
  }

  private boolean serveRepairsTo(PeerIdentity pid, ArchivalUnit au,
				 String url) {
    boolean perUrlAgreement =
      CurrentConfig.getBooleanParam(PARAM_ENABLE_PER_URL_AGREEMENT,
                                    DEFAULT_ENABLE_PER_URL_AGREEMENT);

    if (perUrlAgreement) {
      // Use per-URL agreement.
      try {
        RepositoryNode node = AuUtil.getRepositoryNode(au, url);
        boolean previousAgreement = node.hasAgreement(pid);
        if (previousAgreement) {
          log.debug("Previous agreement found for peer " + pid + " on URL "
                    + url);
        } else {
          log.debug("No previous agreement found for peer " + pid + " on URL "
                    + url);
        }
        return previousAgreement;
      } catch (MalformedURLException ex) {
        // Log the error, but certainly don't serve the repair.
        log.error("serveRepairs: The URL " + url + " appears to be malformed. "
                  + "Cannot serve repairs for this URL.");
        return false;
      }
    } else {
      // Use per-AU agreement.
      float percentAgreement = idManager.getHighestPercentAgreement(pid, au);
      log.debug2("Checking highest percent agreement for au and peer " + pid + ": " 
                 + percentAgreement);
      double minPercentForRepair = pollManager.getMinPercentForRepair();
      log.debug2("Minimum percent agreement required for repair: "
                 + minPercentForRepair);
      return (percentAgreement >= minPercentForRepair);
    }
  }

  /**
   * Checkpoint the current state of the voter.
   */
  void checkpointPoll() {
    // This is sometimes the case during testing.
    if (pollSerializer == null) return;
    try {
      pollSerializer.saveVoterUserData(voterUserData);
    } catch (PollSerializerException ex) {
      log.warning("Unable to save voter state.");
    }
  }

  private class PollTimerCallback implements TimerQueue.Callback {
    /**
     * Called when the timer for this poll expires.
     *
     * @param cookie data supplied by caller to schedule()
     */
    public void timerExpired(Object cookie) {
      stopPoll();
    }
    
    public String toString() {
      return "V3 Voter " + getKey();
    }
  }
  
  /**
   * Release unneeded resources.
   */
  // Do not set pollManager or theDaemon to null; it doesn't accomplish
  // anything (they're not GCable) and they may get referenced
  public void release() {
    if (task != null) task.cancel();
    voterUserData.release();
    stateDir = null;
    task = null;
    idManager = null;
    pollSerializer = null;
    stateMachine = null;
  }

  private PsmMachine makeMachine() {
    try {
      PsmMachine.Factory fact = VoterStateMachineFactory.class.newInstance();
      return fact.getMachine(getVoterActionsClass());
    } catch (Exception e) {
      String msg = "Can't create voter state machine";
      log.critical(msg, e);
      throw new RuntimeException(msg, e);
    }
  }

}
