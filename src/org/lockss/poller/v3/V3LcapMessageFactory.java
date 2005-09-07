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

package org.lockss.poller.v3;

import org.lockss.protocol.*;

/**
 * Factory methods used by the V3 Polling system to create instances of
 * V3LcapMessages.
 */
public class V3LcapMessageFactory {

  public static V3LcapMessage makePollMsg(PollerUserData ud) {
    V3LcapMessage msg =
      V3LcapMessage.makeRequestMsg(ud.getAuId(),
                                   ud.getPollVersion(),
                                   ud.getPluginVersion(),
                                   ud.getUrl(),
                                   ud.getPollerNonce(),
                                   ud.getVoterNonce(),
                                   V3LcapMessage.MSG_POLL,
                                   ud.getDeadline(),
                                   ud.getPollerId());
    msg.setPollerNonce(ud.getPollerNonce());
    msg.setEffortProof(ud.getIntroEffortProof());
    return msg;
  }

  public static V3LcapMessage makePollProofMsg(PollerUserData ud) {
    V3LcapMessage msg =
      V3LcapMessage.makeRequestMsg(ud.getAuId(),
				   ud.getPollVersion(),
				   ud.getPluginVersion(),
				   ud.getUrl(),
				   ud.getPollerNonce(),
                                   ud.getVoterNonce(),
				   V3LcapMessage.MSG_POLL_PROOF,
				   ud.getDeadline(),
				   ud.getPollerId());
    msg.setEffortProof(ud.getRemainingEffortProof());
    return msg;
  }

  public static V3LcapMessage makeVoteRequestMsg(PollerUserData ud) {
    return V3LcapMessage.makeRequestMsg(ud.getAuId(),
					ud.getPollVersion(),
					ud.getPluginVersion(),
					ud.getUrl(),
                                        ud.getPollerNonce(),
					ud.getVoterNonce(),
					V3LcapMessage.MSG_VOTE_REQ,
					ud.getDeadline(),
					ud.getPollerId());
  }

  public static V3LcapMessage makeRepairRequestMsg(PollerUserData ud) {
    V3LcapMessage msg =
      V3LcapMessage.makeRequestMsg(ud.getAuId(),
				   ud.getPollVersion(),
				   ud.getPluginVersion(),
				   ud.getUrl(),
				   ud.getPollerNonce(),
                                   ud.getVoterNonce(),
				   V3LcapMessage.MSG_REPAIR_REQ,
				   ud.getDeadline(),
				   ud.getPollerId());
    msg.setTarget(ud.getTarget());
    msg.setEffortProof(ud.getRepairEffortProof());
    return msg;
  }

  public static V3LcapMessage makeEvaluationReceiptMsg(PollerUserData ud) {
    V3LcapMessage msg =
      V3LcapMessage.makeRequestMsg(ud.getAuId(),
				   ud.getPollVersion(),
				   ud.getPluginVersion(),
				   ud.getUrl(),
				   ud.getPollerNonce(),
                                   ud.getVoterNonce(),
				   V3LcapMessage.MSG_EVALUATION_RECEIPT,
				   ud.getDeadline(),
				   ud.getPollerId());
    msg.setEffortProof(ud.getReceiptEffortProof());
    return msg;
  }

  public static V3LcapMessage makePollAckMsg(VoterUserData ud) {
    V3LcapMessage msg =
      V3LcapMessage.makeRequestMsg(ud.getAuId(),
                                   ud.getPollVersion(),
                                   ud.getPluginVersion(),
                                   ud.getUrl(),
                                   ud.getPollerNonce(),
                                   ud.getVoterNonce(),
                                   V3LcapMessage.MSG_POLL_ACK,
                                   ud.getDeadline(),
                                   ud.getPollerId());
    msg.setEffortProof(ud.getPollAckEffortProof());
    return msg;
  }

  public static V3LcapMessage makeNominateMessage(VoterUserData ud) {
    V3LcapMessage msg =
      V3LcapMessage.makeRequestMsg(ud.getAuId(),
                                   ud.getPollVersion(),
                                   ud.getPluginVersion(),
                                   ud.getUrl(),
                                   ud.getPollerNonce(),
                                   ud.getVoterNonce(),
                                   V3LcapMessage.MSG_NOMINATE,
                                   ud.getDeadline(),
                                   ud.getPollerId());
    msg.setNominees(ud.getNominees());
    return msg;
  }
  
  public static V3LcapMessage makeVoteMessage(VoterUserData ud) {
    V3LcapMessage msg =
      V3LcapMessage.makeRequestMsg(ud.getAuId(),
                                   ud.getPollVersion(),
                                   ud.getPluginVersion(),
                                   ud.getUrl(),
                                   ud.getPollerNonce(),
                                   ud.getVoterNonce(),
                                   V3LcapMessage.MSG_VOTE,
                                   ud.getDeadline(),
                                   ud.getPollerId());
    msg.setVoteBlocks(ud.getVoteBlocks());
    return msg;
  }

}
