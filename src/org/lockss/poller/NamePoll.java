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

import org.lockss.daemon.CachedUrlSet;
import org.lockss.protocol.Message;
import java.security.*;
import org.lockss.hasher.*;
import java.util.Hashtable;
import java.util.Arrays;
import gnu.regexp.*;

/**
 * @author Claire Griffin
 * @version 1.0
 */

public class NamePoll extends Poll {
  Hashtable our_expansion;  // Our expansion of the page set
  Hashtable all_expansion;  // Each vote's expansion
  Hashtable m_voterState;
  int m_namesSent;
  int m_seq;

  public NamePoll(Message msg) {
    super(msg);
    our_expansion = new Hashtable();
    all_expansion = new Hashtable();
    m_voterState = new Hashtable();
    m_namesSent = 0;
    m_replyOpcode = Message.NAME_POLL_REP;
    m_seq++;
    m_thread = new Thread(this, "NamePoll-" + m_seq);
  }

  public void run()  {

    if(m_msg.isLocal())	 {
      if(m_voteChecked)  {
        log.debug(m_key + " local replay ignored");
        return;
      }
      m_voteChecked = true;
    }
    // make sure we have the right poll
    byte[] C = m_msg.getChallenge();

    if(C.length != m_challenge.length)  {
      log.debug(m_key + " challenge length mismatch");
      return;
    }

    if(!Arrays.equals(C, m_challenge))  {
      log.debug(m_key + " challenge mismatch");
      return;
    }

    // make sure our vote will actually matter
    int vote_margin =  m_agree - m_disagree;
    if(vote_margin > m_quorum)  {
      log.info(m_key + " " +  vote_margin + " lead is enough");
      return;
    }

    scheduleHash();
  }

  protected void tally() {
    int yes;
    int no;
    int yesWt;
    int noWt;
    synchronized (this) {
      yes = m_agree;
      no = m_disagree;
      yesWt = m_agreeWt;
      noWt = m_disagreeWt;
    }
 /* we need to extract all of the relevant info */

    thePolls.remove(m_key);
    //recordTally(m_urlset, this, yes, no, yesWt, noWt, m_replyOpcode);
  }
  void checkVote()  {
    byte[] H = m_msg.getHashed();
    if(Arrays.equals(H, m_hash)) {
      handleDisagreeVote(m_msg);

    }
    else {
      handleAgreeVote(m_msg);
    }
  }

  boolean scheduleHash() {
    MessageDigest hasher = null;
    try {
      hasher = MessageDigest.getInstance(HASH_ALGORITHM);
    } catch (NoSuchAlgorithmException ex) {
      return false;
    }
    hasher.update(m_challenge, 0, m_challenge.length);
    hasher.update(m_verifier, 0, m_verifier.length);

    try {
      return HashService.hashNames(m_arcUnit.makeCachedUrlSet(m_url,m_regExp),
                                   hasher,
                                   m_deadline,
                                   new HashCallback(),
                                   m_verifier);
    }
    catch (REException ex) {
      return false;
    }
  }

}