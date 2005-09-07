/*
 * $Id$
 */

/*
Copyright (c) 2000-2005 Board of Trustees of Leland Stanford Jr. University,
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

import org.lockss.plugin.CachedUrlSet;
import org.lockss.protocol.*;
import org.lockss.util.*;
import org.lockss.protocol.psm.*;

import java.io.*;
import java.util.*;

/**
 * Persistent user data state object used by V3Poller state machine.
 */
public class PollerUserData implements Serializable {

  private PeerIdentity voterId;
  private String hashAlgorithm;
  private VoteBlocks voteBlocks;
  private String targetUrl;
  private List nominees;
  private byte[] pollerNonce;
  private byte[] voterNonce;
  private byte[] introEffortProof;
  private byte[] pollAckEffortProof;
  private byte[] remainingEffortProof;
  private byte[] repairEffortProof;
  private byte[] receiptEffortProof;
  
  private static Logger log = Logger.getLogger("PollerUserData");
  
  /** Transient non-serialized fields */
  private transient V3Poller poller;
  private transient V3PollerSerializer serializer;
  private transient PollerStateBean pollState;

  /**
   * Constructor used by the V3Poller.
   * 
   * @param id
   * @param poller
   * @param serializer
   */
  public PollerUserData(PeerIdentity id, V3Poller poller,
                        V3PollerSerializer serializer) {
    this.voterId = id;
    this.poller = poller;
    this.pollState = poller.getPollerStateBean();
    this.serializer = serializer;
  }
  
  PollerUserData(PeerIdentity voterId) {
    this.voterId = voterId;
    saveState();
  }

  public void setVoterId(PeerIdentity id) {
    this.voterId = id;
    saveState();
  }

  public PeerIdentity getVoterId() {
    return voterId;
  }

  public void setTarget(String url) {
    this.targetUrl = url;
    saveState();
  }

  public String getTarget() {
    return targetUrl;
  }

  public void setNominees(List l) {
    this.nominees = l;
    saveState();
  }

  public List getNominees() {
    return nominees;
  }
  
  public void setHashAlgorithm(String s) {
    this.hashAlgorithm = s;
    saveState();
  }

  public String getHashAlgorithm() {
    return hashAlgorithm;
  }

  public byte[] getPollerNonce() {
    return pollerNonce;
  }

  public void setPollerNonce(byte[] pollerNonce) {
    this.pollerNonce = pollerNonce;
    saveState();
  }

  public byte[] getVoterNonce() {
    return voterNonce;
  }

  public void setVoterNonce(byte[] voterNonce) {
    this.voterNonce = voterNonce;
    saveState();
  }

  public void setIntroEffortProof(byte[] b) {
    this.introEffortProof = b;
    saveState();
  }

  public byte[] getIntroEffortProof() {
    return introEffortProof;
  }

  public void setRemainingEffortProof(byte[] b) {
    this.remainingEffortProof = b;
    saveState();
  }

  public byte[] getRemainingEffortProof() {
    return remainingEffortProof;
  }

  public void setPollAckEffortProof(byte[] b) {
    pollAckEffortProof = b;
  }

  public byte[] getPollAckEffortProof() {
    return pollAckEffortProof;
  }

  public void setRepairEffortProof(byte[] b) {
    repairEffortProof = b;
  }

  public byte[] getRepairEffortProof() {
    return repairEffortProof;
  }

  public void setReceiptEffortProof(byte[] b) {
    receiptEffortProof = b;
  }

  public byte[] getReceiptEffortProof() {
    return receiptEffortProof;
  }

  public void setVoteBlocks(VoteBlocks blocks) {
    voteBlocks = blocks;
    saveState();
  }

  public VoteBlocks getVoteBlocks() {
    return voteBlocks;
  }

  public String toString() {
    return "[V3VoterState: voterId=" +
      voterId + "]";
  }
  
  public String getPollKey() {
    return poller.getPollKey();
  }
  
  public V3Poller getPoller() {
    return poller;
  }
  
  /** Poller State delegate methods */
  public String getAuId() {
    return pollState.getAuId();
  }

  public CachedUrlSet getCachedUrlSet() {
    return pollState.getCachedUrlSet();
  }

  public long getDeadline() {
    return pollState.getDeadline();
  }

  public String getLastHashedBlock() {
    return pollState.getLastHashedBlock();
  }

  public String getPluginVersion() {
    return pollState.getPluginVersion();
  }

  public PeerIdentity getPollerId() {
    return pollState.getPollerId();
  }

  public int getPollVersion() {
    return pollState.getPollVersion();
  }

  public String getUrl() {
    return pollState.getUrl();
  }

  public int hashCode() {
    return pollState.hashCode();
  }

  /** Poller delegate methods */
  void sendMessageTo(V3LcapMessage msg, PeerIdentity to) {
    poller.sendMessageTo(msg, to);
  }

  /*
   * Callbacks methods.
   */
  void nominatePeers(List peers) {
    poller.nominatePeers(getVoterId(), peers);
  }

  void tallyIfReady() {
    poller.tallyIfReady(getVoterId());
  }

  void handleRepair() {
    // XXX: TBD
  }

  /**
   * Store the current V3VoterState
   */
  private void saveState() {
    try {
      serializer.savePollerUserData(this);
    } catch (V3Serializer.PollSerializerException ex) {
      log.error("Unable to save voter state for peer " + this.voterId, ex);
    }
  }
}
