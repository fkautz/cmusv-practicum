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

package org.lockss.poller;

import java.io.*;
import java.security.*;
import java.util.*;


import org.lockss.daemon.*;
import org.lockss.hasher.*;
import org.lockss.plugin.*;
import org.lockss.protocol.*;
import org.lockss.util.*;


/**
 * @author Claire Griffin
 * @version 1.0
 */
class VerifyPoll extends Poll {

  public VerifyPoll(LcapMessage msg, CachedUrlSet urlSet) {
    super(msg, urlSet);
    m_replyOpcode = LcapMessage.VERIFY_POLL_REP;
    m_quorum = 1;
  }



  /**
   * will request a verify at random based on some percentage.
   * @param msg the Message to use to request verification
   * @param pct the percentage of time you should verify
   * @throws IOException thrown by ProbbilisticChoice
   */
  protected static void randomRequestVerify(LcapMessage msg, int pct)
      throws IOException {
    double prob = ((double) pct) / 100.0;
    if (ProbabilisticChoice.choose(prob)) {
      requestVerify(msg);
    }
  }

  /**
   * handle a message which may be a incoming vote
   * @param msg the Message to handle
   */
  void receiveMessage(LcapMessage msg) {
    int opcode = msg.getOpcode();

    if(opcode == LcapMessage.VERIFY_POLL_REP) {
      startVote(msg);
    }
  }


  /**
   * schedule the hash for this poll - this is a no-op provided for completeness.
   * @param hasher the MessageDigest used to hash the content
   * @param timer the Deadline by which we must complete
   * @param key the Object which will be returned from the hasher. Always the
   * message which triggered the hash
   * @param callback the hashing callback to use on return
   * @return true we never do anything here
   */
  boolean scheduleHash(MessageDigest hasher, Deadline timer,
                                Object key, HashService.Callback callback) {
    return true;
  }



  /**
   * start the poll.  set a deadline in which to actually verify the message.
   */
  void startPoll() {
    // if someone else called the poll, we verify and we're done
    log.debug("Starting new verify poll:" + m_key);

    Deadline pt = Deadline.atRandomBefore(m_deadline);

    TimerQueue.schedule(pt, new PollTimerCallback(), this);
  }


  /**
   * finish the poll once the deadline has expired
   */
  void stopPoll() {
    // if we didn't call the poll
    if(m_pollstate != PS_WAIT_TALLY) {
      try {
        // send our reply message
        replyVerify(m_msg);
      }
      catch (IOException ex) {
        m_pollstate = ERR_IO;
      }
    }
    super.stopPoll();
  }


  /**
   * tally the poll results
   */
  protected void tally()  {
    super.tally();
    log.info(m_msg.toString() + " tally " + toString());
    LcapIdentity id = m_msg.getOriginID();
    if ((m_agree + m_disagree) < 1) {
      id.voteNotVerify();
    } else if (m_agree > 0 && m_disagree == 0) {
      id.voteVerify();
    } else {
      id.voteDisown();
    }
  }


  private boolean performHash(LcapMessage msg) {
    byte[] challenge = msg.getChallenge();
    byte[] hashed = msg.getHashed();
    MessageDigest hasher = PollManager.getHasher();
    // check this vote verification hashed in the message should
    // hash to the challenge, which is the verifier of the poll
    // thats being verified

    hasher.update(hashed, 0, hashed.length);
    byte[] HofHashed = hasher.digest();
    if(!Arrays.equals(challenge, HofHashed))  {
      log.debug("verify vote disagreed");
      handleDisagreeVote(msg);
    }
    else  {
      log.debug("verify vote agreed.");
      handleAgreeVote(msg);
    }
    return true;

  }

  private static void requestVerify(LcapMessage msg) throws IOException {
    String url = new String(msg.getTargetUrl());
    String regexp = new String(msg.getRegExp());
    int opcode = LcapMessage.VERIFY_POLL_REQ;

    log.debug("Calling a verify poll...");

    LcapMessage reqmsg = LcapMessage.makeRequestMsg(url,
        regexp,
        new String[0],
        msg.getGroupAddress(),
        msg.getTimeToLive(),
        msg.getVerifier(),
        PollManager.makeVerifier(),
        opcode,
        msg.getDuration(),
        LcapIdentity.getLocalIdentity());
    LcapIdentity originator = msg.getOriginID();
    LcapComm.sendMessageTo(msg, Plugin.findArchivalUnit(url), originator);

    log.debug("Creating a local poll instance...");
    Poll poll = PollManager.findPoll(reqmsg);
    poll.m_pollstate = PS_WAIT_TALLY;
    poll.startPoll();
  }

  private void replyVerify(LcapMessage msg) throws IOException  {
    byte[] secret = PollManager.getSecret(msg.getChallenge());
    byte[] verifier = PollManager.makeVerifier();
    LcapMessage repmsg = LcapMessage.makeReplyMsg(msg,
        secret,
        verifier,
        LcapMessage.VERIFY_POLL_REP,
        msg.getDuration(),
        LcapIdentity.getLocalIdentity());
    log.debug("sending our verification reply.");
    LcapIdentity originator = msg.getOriginID();
    LcapComm.sendMessageTo(repmsg, Plugin.findArchivalUnit(msg.getTargetUrl()),
                           originator);

  }

  private void startVote(LcapMessage msg) {
    super.startVote();
    performHash(msg);
    stopVote();
  }

}
