/*
 * $Id$
 */

/*

 Copyright (c) 2000-2002 Board of Trustees of Leland Stanford Jr. University,
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

import java.security.*;

import org.lockss.protocol.*;
import org.lockss.protocol.psm.*;
import org.lockss.util.*;

public class VoterActions {
  private static final Logger log = Logger.getLogger("VoterActions");

  public static PsmEvent handleVerifyPollEffort(PsmEvent evt, PsmInterp interp) {
    // XXX: Implement effort service
    return V3Events.evtOk;
  }

  public static PsmEvent handleProvePollAck(PsmEvent evt, PsmInterp interp) {
    VoterUserData ud = getUserData(interp);
    byte[] pollAckEffort = ByteArray.makeRandomBytes(20);
    ud.setPollAckEffortProof(pollAckEffort);
    return V3Events.evtOk;
  }

  public static PsmEvent handleSendPollAck(PsmEvent evt, PsmInterp interp) {
    VoterUserData ud = getUserData(interp);
    V3LcapMessage msg = V3LcapMessageFactory.makePollAckMsg(ud);
    msg.setEffortProof(ud.getPollAckEffortProof());
    msg.setVoterNonce(ud.getVoterNonce());
    ud.sendMessage(msg);
    return V3Events.evtOk;
  }

  public static PsmEvent handleReceivePollProof(PsmMsgEvent evt, PsmInterp interp) {
    VoterUserData ud = getUserData(interp);
    V3LcapMessage msg = (V3LcapMessage)evt.getMessage();
    ud.setRemainingEffortProof(msg.getEffortProof());
    return V3Events.evtOk;
  }

  public static PsmEvent handleVerifyPollProof(PsmEvent evt, PsmInterp interp) {
    VoterUserData ud = getUserData(interp);
    // XXX: Implement effort service
    return V3Events.evtOk;
  }

  public static PsmEvent handleSendNominate(PsmEvent evt, PsmInterp interp) {
    VoterUserData ud = getUserData(interp);
    V3LcapMessage msg = V3LcapMessageFactory.makeNominateMessage(ud);
    ud.sendMessage(msg);
    return V3Events.evtOk;
  }

  public static PsmEvent handleGenerateVote(PsmEvent evt, PsmInterp interp) {
    VoterUserData ud = getUserData(interp);
    try {
      if (ud.generateVote()) {
        return V3Events.evtOk;
      } else {
        return V3Events.evtError;
      }
    } catch (NoSuchAlgorithmException ex) {
      log.error("No such hashing algorithm: " + ex.getMessage());
      return V3Events.evtError;
    }
  }
  
  public static PsmEvent handleReceiveVoteRequest(PsmMsgEvent evt, PsmInterp interp) {
    VoterUserData ud = getUserData(interp);
    // If we're ready to cast our vote right away, do so.  Otherwise, wait
    // until V3Voter tells us to.
    if (ud.hashingDone()) {
      return V3Events.evtReadyToVote;
    } else {
      ud.voteRequested(true);
      return V3Events.evtWaitHashingDone;
    }
  }

  public static PsmEvent handleSendVote(PsmEvent evt, PsmInterp interp) {
    VoterUserData ud = getUserData(interp);
    // Actually cast our vote.
    V3LcapMessage msg = V3LcapMessageFactory.makeVoteMessage(ud);
    ud.sendMessage(msg);
    ud.hashingDone(false);
    return V3Events.evtOk;
  }

  public static PsmEvent handleReceiveRepairRequest(PsmMsgEvent evt, PsmInterp interp) {
    // XXX: Implement.
    return V3Events.evtRepairRequestOk;
  }

  public static PsmEvent handleSendRepair(PsmEvent evt, PsmInterp interp) {
    // XXX: Implement.
    return V3Events.evtOk;
  }

  public static PsmEvent handleReceiveReceipt(PsmMsgEvent evt, PsmInterp interp) {
    // XXX: Implement.
    return V3Events.evtReceiptOk;
  }

  public static PsmEvent handleProcessReceipt(PsmEvent evt, PsmInterp interp) {
    // XXX: Implement.
    return V3Events.evtOk;
  }

  public static PsmEvent handleError(PsmEvent evt, PsmInterp interp) {
    // XXX: Implement.
    return V3Events.evtOk;
  }
  
  private static VoterUserData getUserData(PsmInterp interp) {
    return (VoterUserData)interp.getUserData();
  }
}
