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

import org.lockss.protocol.psm.*;

public class VoterStateMachineFactory implements PsmMachine.Factory {

  /**
   * Obtain a PsmMachine for the Voter state table.
   *
   * @return A PsmMachine representing the Voter state table.
   * @param actionClass A class containing static handler methods
   *                    for the state machine to call.
   */
  public PsmMachine getMachine(Class actionClass) {
    return new PsmMachine("Voter",
                          makeStates(actionClass),
                          "Initialize");
  }

  /**
   * Mapping of states to response handlers.
   */
  private static PsmState[] makeStates(Class actionClass) {
    PsmState[] states = {
      // PollManager will supply the Poll message event after
      // creating the voter.
      new PsmState("Initialize", PsmWait.FOREVER,
                   new PsmResponse(V3Events.msgPoll,
                                   new PsmMethodMsgAction(actionClass,
                                                          "handleReceivePoll")),
                   new PsmResponse(V3Events.evtOk, "VerifyPollEffort")).setResumable(true),
      new PsmState("VerifyPollEffort",
                   new PsmMethodAction(actionClass, "handleVerifyPollEffort"),
                   new PsmResponse(V3Events.evtOk, "ProvePollAck")).setResumable(true),
      new PsmState("ProvePollAck",
                   new PsmMethodAction(actionClass, "handleProvePollAck"),
                   new PsmResponse(V3Events.evtOk, "SendPollAck")).setResumable(true),
      new PsmState("SendPollAck",
                   new PsmMethodAction(actionClass, "handleSendPollAck"),
                   new PsmResponse(V3Events.evtDeclinePoll, "FinalizeVoter"),
                   new PsmResponse(V3Events.evtOk, "WaitPollProof")).setResumable(true),
      new PsmState("WaitPollProof", PsmWait.FOREVER,
                   new PsmResponse(V3Events.msgPollProof,
                                   new PsmMethodMsgAction(actionClass,
                                                          "handleReceivePollProof")),
                   new PsmResponse(V3Events.evtOk, "VerifyPollProof")).setResumable(true),
      new PsmState("VerifyPollProof",
                   new PsmMethodAction(actionClass, "handleVerifyPollProof"),
                   new PsmResponse(V3Events.evtOk, "SendNominate")).setResumable(true),
      new PsmState("SendNominate",
                   new PsmMethodAction(actionClass, "handleSendNominate"),
                   new PsmResponse(V3Events.evtOk, "GenerateVote")).setResumable(true),
      new PsmState("GenerateVote",
                   new PsmMethodAction(actionClass, "handleGenerateVote"),
                   new PsmResponse(V3Events.evtOk, "WaitVoteRequest")).setResumable(true),
      new PsmState("WaitVoteRequest", PsmWait.FOREVER,
                   new PsmResponse(V3Events.msgVoteRequest,
                                   new PsmMethodMsgAction(actionClass,
                                                          "handleReceiveVoteRequest")),
                   new PsmResponse(V3Events.evtHashingDone,
                                   new PsmMethodAction(actionClass,
                                                          "handleHashingDone")),
                   new PsmResponse(V3Events.evtWait, PsmWait.FOREVER),
                   new PsmResponse(V3Events.evtReadyToVote, "SendVote")).setResumable(true),
      new PsmState("SendVote",
                   new PsmMethodAction(actionClass, "handleSendVote"),
                   new PsmResponse(V3Events.evtOk, "WaitReceipt")),
      new PsmState("WaitReceipt", PsmWait.FOREVER,
                   new PsmResponse(V3Events.msgVoteRequest,
                                   new PsmMethodMsgAction(actionClass,
                                                         "handleReceiveVoteRequest")),
                   new PsmResponse(V3Events.msgRepairRequest,
                                   new PsmMethodMsgAction(actionClass,
                                                         "handleReceiveRepairRequest")),
                   new PsmResponse(V3Events.msgReceipt,
                                   new PsmMethodMsgAction(actionClass,
                                                         "handleReceiveReceipt")),
                   new PsmResponse(V3Events.evtNoSuchRepair, "WaitReceipt"),
                   new PsmResponse(V3Events.evtReadyToVote, "SendVote"),
                   new PsmResponse(V3Events.evtRepairRequestOk, "SendRepair"),
                   new PsmResponse(V3Events.evtReceiptOk, "ProcessReceipt")).setResumable(true),
      new PsmState("SendRepair",
                   new PsmMethodAction(actionClass, "handleSendRepair"),
                   new PsmResponse(V3Events.evtOk, "WaitReceipt")),
      new PsmState("ProcessReceipt",
                   new PsmMethodAction(actionClass, "handleProcessReceipt"),
                   new PsmResponse(V3Events.evtOk, "FinalizeVoter")).setResumable(true),
      new PsmState("FinalizeVoter")
    };
    return states;
  }
}
