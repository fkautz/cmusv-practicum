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

import java.security.*;
import java.util.*;


import org.lockss.daemon.*;
import org.lockss.hasher.*;
import org.lockss.protocol.*;
import org.lockss.util.*;

/**
 * @author Claire Griffin
 * @version 1.0
 */

public class NamePoll extends Poll {
  int m_seq;

  public NamePoll(LcapMessage msg, CachedUrlSet urlSet) {
    super(msg, urlSet);
    m_replyOpcode = LcapMessage.NAME_POLL_REP;
    m_seq++;
  }


  /**
   * prepare to run a poll.  This should check any conditions that might
   * make running a poll unneccessary.
   * @param msg the message which is triggering the poll
   * @return boolean true if the poll should run, false otherwise
   */
  boolean prepareVoteCheck(LcapMessage msg) {
   // make sure we have the right poll
    byte[] challenge = msg.getChallenge();

    if(challenge.length != m_challenge.length)  {
      log.debug(m_key + " challenge length mismatch");
      return false;
    }

    if(!Arrays.equals(challenge, m_challenge))  {
      log.debug(m_key + " challenge mismatch");
      return false;
    }

    // make sure our vote will actually matter
    int vote_margin =  m_agree - m_disagree;
    if(vote_margin > m_quorum)  {
      log.info(m_key + " " +  vote_margin + " lead is enough");
      return false;
    }

    // are we too busy
    if((m_counting - 1) > m_quorum)  {
      log.info(m_key + " too busy to count " + m_counting + " votes");
      return false;
    }

    // do we have time to complete the hash
    int votes = m_agree + m_disagree + m_counting - 1;
    long duration = msg.getDuration();
    if (votes > 0 && duration < m_hashTime) {
      log.info(m_key + " no time to hash vote " + duration + ":" + m_hashTime);
      return false;
    }
    return true;
  }


  /**
   * schedule the hash for this poll.
   * @param challenge the challenge
   * @param verifier the verifier
   * @param urlSet the cachedUrlSet
   * @param timer the probabilistic timer
   * @param key the Object which will be returned from the hasher, either the
   * poll or the VoteChecker.
   * @return true if hash successfully completed.
   */
  boolean scheduleHash(byte[] challenge, byte[] verifier, CachedUrlSet urlSet,
                       ProbabilisticTimer timer, Object key) {

    MessageDigest hasher = null;
    try {
      hasher = MessageDigest.getInstance(PollManager.HASH_ALGORITHM);
    } catch (NoSuchAlgorithmException ex) {
      return false;
    }
    hasher.update(challenge, 0, challenge.length);
    hasher.update(verifier, 0, verifier.length);

    return HashService.hashNames(urlSet,
                                 hasher,
                                 timer,
                                 new HashCallback(),
                                 this);
  }

  static class NPVoteChecker extends VoteChecker {

    NPVoteChecker(Poll poll, LcapMessage msg, CachedUrlSet urlSet, long hashTime) {
      super(poll, msg, urlSet, hashTime);
    }

    void startVote() {
      if(m_poll.prepareVoteCheck(m_msg)) {
        m_poll.scheduleHash(m_poll.m_challenge, m_poll.m_verifier, m_urlSet,
                            new ProbabilisticTimer(m_msg.getDuration()), this);
      }
    }

    void stopVote() {
      m_poll.m_voteCheckers.remove(this);
      m_poll.m_counting--;
    }
  }
}