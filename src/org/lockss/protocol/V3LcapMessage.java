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

package org.lockss.protocol;

import java.io.*;
import java.util.*;

import org.mortbay.util.*;

import org.lockss.plugin.CachedUrl;
import org.lockss.poller.*;
import org.lockss.util.*;
import org.lockss.util.StringUtil;

/**
 * Class that encapsulates a V3 LCAP message that has been received or will be
 * sent over the wire.
 *
 * V3 LCAP Messages are not carried over UDP, so their encoded forms are not
 * required to fit in one UDP packet. They do not have Lower and Upper bounds or
 * remainders like V1 LCAP Messages.
 */
public class V3LcapMessage extends LcapMessage implements LockssSerializable {
  public static final int MSG_POLL = 10;
  public static final int MSG_POLL_ACK = 11;
  public static final int MSG_POLL_PROOF = 12;
  public static final int MSG_NOMINATE = 13;
  public static final int MSG_VOTE_REQ = 14;
  public static final int MSG_VOTE = 15;
  public static final int MSG_REPAIR_REQ = 16;
  public static final int MSG_REPAIR_REP = 17;
  public static final int MSG_EVALUATION_RECEIPT = 18;
  public static final int MSG_NO_OP = 19;

  public static final int POLL_MESSAGES_BASE = 10;
  public static final String[] POLL_MESSAGES = { "Poll", "PollAck", "PollProof",
    "Nominate", "VoteRequest", "Vote", "RepairReq", "RepairRep",
    "EvaluationReceipt", "NoOp" };
  private static Logger log = Logger.getLogger("V3LcapMessage");

  public static final int V3_PROTOCOL_R1 = 1;
  public static final int V3_PROTOCOL_REV = V3_PROTOCOL_R1;

  /** The poller nonce bytes generated by the poller. */
  private byte[] m_pollerNonce;

  /** The voter nonce bytes generated by a poll participant. */
  private byte[] m_voterNonce;

  /** The effort proof for this message (if any). */
  private byte[] m_effortProof;

  /** In Vote messages: A list of vote blocks for this vote. */
  private VoteBlocks m_voteBlocks;

  /**
   * In Nominate messages: The list of outer circle nominees, in the form of
   * peer identity strings.
   */
  private List m_nominees;

  /**
   * In Vote messages: True if all vote blocks have been sent, false otherwise.
   */
  private boolean m_voteComplete = false;

  /**
   * Repair Data.
   */
  private EncodedProperty m_repairProps;
  private long m_repairDataLen = 0;

  // XXX: A kludge.  InputStream from which to read repair data
  //      when encoding this message.
  private transient InputStream m_repairDataInputStream;

  /** File used to store vote blocks, repair data, etc. */
  private transient File m_dataFile;

  /**
   * In Vote Request messages: the URL of the last vote block received. Null if
   * this is the first (or only) request.
   */
  private String m_lastVoteBlockURL;

  /*
   * Common to all versions:
   * bytes  0 -   2:  Signature ('lpm' in ASCII) (3 bytes)
   * byte         3:  Protocol major version (1 byte)
   *
   * Fixed length V3 fields:
   * bytes        4:  Protocol minor version (1 byte)
   * bytes  5  -  8:  Property length (4 bytes)
   * bytes  9  - 16:  Repair Data length (8 bytes)
   * bytes  17 - 36:  SHA-1 hash of encoded properties (20 bytes)
   *
   * Variable length V3 Fields
   * bytes  37 - <property_length>:  Encoded properties
   * <property_length+1>   - <repair_data_length>: Repair data
   */

  /**
   * Construct a new V3LcapMessage.
   */
  public V3LcapMessage() {
    m_props = new EncodedProperty();
    m_voteBlocks = new MemoryVoteBlocks();
    m_pollProtocol = Poll.V3_PROTOCOL;
  }

  public V3LcapMessage(int opcode, String key, PeerIdentity origin,
                       String url, long start, long stop, byte[] pollerNonce,
                       byte[] voterNonce) {
    this();
    m_key = key;
    m_opcode = opcode;
    m_originatorID = origin;
    m_targetUrl = url;
    m_startTime = start;
    m_stopTime = stop;
    m_pollerNonce = pollerNonce;
    m_voterNonce = voterNonce;
  }

  /**
   * Construct a V3LcapMessage from an encoded array of bytes.
   */
  public V3LcapMessage(byte[] encodedBytes, File dir) throws IOException {
    this(new ByteArrayInputStream(encodedBytes), dir);
  }

  /**
   * Construct a V3LcapMessage from an encoded InputStream.
   */
  public V3LcapMessage(InputStream inputStream, File dir) throws IOException {
    this();
    if (dir != null) {
      m_dataFile = FileUtil.createTempFile("v3lcapmessage-", ".data", dir);
      log.debug2("Creating V3LcapMessage Data File: " + m_dataFile);
    }
    try {
      decodeMsg(inputStream);
    } catch (IOException ex) {
      log.error("Unreadable Packet", ex);
      throw new ProtocolException("Unable to decode pkt.");
    }
  }

  /** Method suitable for unit tests. */
  protected int getProtocolRev() {
    return V3_PROTOCOL_REV;
  }

  /**
   * Build out this message from an InputStream.
   *
   * @param is An input stream from which the message bytes can be read.
   */
  public void decodeMsg(InputStream is) throws IOException {
    long duration;
    long elapsed;
    // the mutable stuff
    DataInputStream dis = new DataInputStream(is);
    // read in the three header bytes
    for (int i = 0; i < signature.length; i++) {
      if (signature[i] != dis.readByte()) {
        throw new ProtocolException("Invalid Signature");
      }
    }
    // read in the poll version byte and decode
    int majorVersion = dis.readByte();
    int minorVersion = dis.readByte();
    if (majorVersion != Poll.V3_POLL ||
        minorVersion != getProtocolRev()) {
      throw new ProtocolException("Unsupported inbound protocol: " +
                "major=" + majorVersion + ", minor=" + minorVersion);
    }
    int prop_len = dis.readInt();
    m_repairDataLen = dis.readLong();
    byte[] hash_bytes = new byte[SHA_LENGTH];
    byte[] prop_bytes = new byte[prop_len];
    dis.read(hash_bytes);
    dis.read(prop_bytes);
    if (!verifyHash(hash_bytes, prop_bytes)) {
      throw new ProtocolException("Hash verification failed.");
    }
    // Decode the repair data.
    if  (m_repairDataLen > 0) {
      if (m_dataFile != null) {
        OutputStream out = new FileOutputStream(m_dataFile);
        StreamUtil.copy(dis, out);
        IOUtil.safeClose(out);
      } else {
        log.warning("Warning:  Repair data payload detected, " +
                    "but no temporary file to store it in!");
      }
    }
    //
    // decode the properties
    m_props.decode(prop_bytes);
    // the immutable stuff
    m_key = m_props.getProperty("key");
    String addr_str = m_props.getProperty("origId");
    m_originatorID = m_idManager.stringToPeerIdentity(addr_str);
    m_hashAlgorithm = m_props.getProperty("hashAlgorithm");
    duration = m_props.getInt("duration", 0) * 1000;
    elapsed = m_props.getInt("elapsed", 0) * 1000;
    m_opcode = m_props.getInt("opcode", -1);
    m_archivalID = m_props.getProperty("au", "UNKNOWN");
    m_targetUrl = m_props.getProperty("url");
    m_pollerNonce = m_props.getByteArray("pollerNonce", EMPTY_BYTE_ARRAY);
    m_voterNonce = m_props.getByteArray("voterNonce", EMPTY_BYTE_ARRAY);
    m_effortProof = m_props.getByteArray("effortproof", EMPTY_BYTE_ARRAY);
    m_pluginVersion = m_props.getProperty("plugVer");

    // V3 specific message parameters
    String nomineesString = m_props.getProperty("nominees");
    if (nomineesString != null) {
      m_nominees = StringUtil.breakAt(nomineesString, ',');
    }
    m_lastVoteBlockURL = m_props.getProperty("lastvoteblockurl");
    m_voteComplete = m_props.getBoolean("votecomplete", false);
    m_repairProps = m_props.getEncodedProperty("repairProps");

    // Decode the list of vote blocks.
    // encodedVoteBlocks is a list of EncodedProperty objects, each one
    // representing a VoteBlock
    List encodedVoteBlocks = m_props.getEncodedPropertyList("voteblocks");

    if (encodedVoteBlocks != null) {
      m_voteBlocks = new MemoryVoteBlocks(encodedVoteBlocks.size());

      if (encodedVoteBlocks != null) {
        for (Iterator ix = encodedVoteBlocks.iterator(); ix.hasNext();) {
          EncodedProperty vbProps = (EncodedProperty) ix.next();
          VoteBlock vb = new VoteBlock();
          vb.setFileName(vbProps.getProperty("fn"));
          vb.setVoteType(vbProps.getInt("vt", VoteBlock.CONTENT_VOTE));
          vb.setFilteredLength(vbProps.getLong("fl", 0));
          vb.setUnfilteredLength(vbProps.getLong("ul", 0));
          vb.setFilteredOffset(vbProps.getLong("fo", 0));
          vb.setUnfilteredOffset(vbProps.getLong("uo", 0));
          vb.setChallengeHash(vbProps.getByteArray("ch", EMPTY_BYTE_ARRAY));
          vb.setPlainHash(vbProps.getByteArray("ph", EMPTY_BYTE_ARRAY));
          m_voteBlocks.addVoteBlock(vb);
        }
      }
    }

    // calculate start and stop times
    long now = TimeBase.nowMs();
    m_startTime = now - elapsed;
    m_stopTime = now + duration;
  }

  /**
   * Build out this message from a byte array.
   *
   * @param encodedBytes The encoded byte array representing this message.
   */
  public void decodeMsg(byte[] encodedBytes) throws IOException {
    this.decodeMsg(new ByteArrayInputStream(encodedBytes));
  }

  // XXX:  This is obviously a hack and will need to be refactored for 1.16.
  public byte[] encodeMsg() throws IOException {
    storeProps();
    byte[] prop_bytes = m_props.encode();
    byte[] hash_bytes = computeHash(prop_bytes);
    // msg header is 37 bytes
    long enc_len = prop_bytes.length + m_repairDataLen + 37;
    ByteArrayOutputStream baos = new ByteArrayOutputStream((int)enc_len);
    DataOutputStream dos = new DataOutputStream(baos);
    dos.write(signature);
    dos.writeByte(Poll.V3_POLL);
    dos.writeByte(V3_PROTOCOL_REV);
    dos.writeInt(prop_bytes.length);
    dos.writeLong(m_repairDataLen);
    dos.write(hash_bytes);
    dos.write(prop_bytes);
    if (m_repairDataInputStream != null) {
      // Write repair data
      StreamUtil.copy(m_repairDataInputStream, dos);
    }
    return baos.toByteArray();
  }

  /**
   * Obtain an InputStream from which the bytes of this message can be read.
   */
  public InputStream getInputStream() throws IOException {
    return new ByteArrayInputStream(encodeMsg());
  }

  /**
   * Store all properties.
   */
  public void storeProps() throws IOException {
    // make sure the props table is up to date
    try {
      // PeerIdentity.getIdString() returns an IP:Port string.
      m_props.put("origId", m_originatorID.getIdString());
    } catch (NullPointerException npe) {
      throw new ProtocolException("encode - null origin host address.");
    }
    if (m_opcode == MSG_NO_OP) {
      m_props.putInt("opcode", m_opcode);
      if (m_pollerNonce != null) {
        m_props.putByteArray("pollerNonce", m_pollerNonce);
      }
      if (m_voterNonce != null) {
        m_props.putByteArray("voterNonce", m_voterNonce);
      }
      return;
    }
    m_props.setProperty("hashAlgorithm", getHashAlgorithm());
    m_props.putInt("duration", (int) (getDuration() / 1000));
    m_props.putInt("elapsed", (int) (getElapsed() / 1000));
    m_props.setProperty("key", m_key);
    m_props.putInt("opcode", m_opcode);
    m_props.setProperty("url", m_targetUrl);
    if (m_pluginVersion != null) {
      m_props.setProperty("plugVer", m_pluginVersion);
    }
    if (m_archivalID == null) {
      throw new ProtocolException("Null AU ID not allowed.");
    }
    m_props.setProperty("au", m_archivalID);
    if (m_pollerNonce != null) {
      m_props.putByteArray("pollerNonce", m_pollerNonce);
    }
    if (m_voterNonce != null) {
      m_props.putByteArray("voterNonce", m_voterNonce);
    }

    // V3 specific message parameters.

    if (m_effortProof != null) {
      m_props.putByteArray("effortproof", m_effortProof);
    }
    if (m_nominees != null) {
      m_props.setProperty("nominees",
                          StringUtil.separatedString(m_nominees, ","));
    }
    if (m_lastVoteBlockURL != null) {
      m_props.setProperty("lastvoteblockurl", m_lastVoteBlockURL);
    }
    m_props.putBoolean("votecomplete", m_voteComplete);
    if (m_repairProps != null) {
      m_props.putEncodedProperty("repairProps", m_repairProps);
    }

    // XXX: These should eventually be refactored out of the encoded
    // property object. The large size of some AUs will quickly lead
    // to memory exhaustion if a lot of EncodedProperty objects full
    // of VoteBlocks are hanging around.

    // Store the vote block list
    ArrayList encodedVoteBlocks = new ArrayList();
    Iterator ix = getVoteBlockIterator();
    while (ix.hasNext()) {
      VoteBlock vb = (VoteBlock) ix.next();
      EncodedProperty vbProps = new EncodedProperty();
      vbProps.setProperty("fn", vb.getUrl());
      vbProps.putInt("vt", vb.getVoteType());
      vbProps.putLong("fl", vb.getFilteredLength());
      vbProps.putLong("fo", vb.getFilteredOffset());
      vbProps.putLong("ul", vb.getUnfilteredLength());
      vbProps.putLong("uo", vb.getUnfilteredOffset());
      vbProps.putByteArray("ch", vb.getHash());
      vbProps.putByteArray("ph", vb.getPlainHash());
      encodedVoteBlocks.add(vbProps);
    }
    m_props.putEncodedPropertyList("voteblocks", encodedVoteBlocks);
  }

  /**
   * Return the unique identifying Poll Key for this poll.
   *
   * @return The unique poll identifier for this poll.
   */
  public String getKey() {
    return m_key;
  }

  public void setKey(String key) {
    m_key = key;
  }

  /**
   * Return an effort proof.
   *
   * @return The effort proof for this message.
   */
  public byte[] getEffortProof() {
    return m_effortProof;
  }

  public void setEffortProof(byte[] b) {
    m_effortProof = b;
  }

  public String getTargetUrl() {
    return m_targetUrl;
  }

  public void setTargetUrl(String url) {
    m_targetUrl = url;
  }

  public boolean isNoOp() {
    return m_opcode == MSG_NO_OP;
  }

  public String getOpcodeString() {
    return POLL_MESSAGES[m_opcode - POLL_MESSAGES_BASE];
  }

  public byte[] getPollerNonce() {
    return m_pollerNonce;
  }

  public void setPollerNonce(byte[] b) {
    m_pollerNonce = b;
  }

  public byte[] getVoterNonce() {
    return m_voterNonce;
  }

  public void setVoterNonce(byte[] b) {
    this.m_voterNonce = b;
  }

  public List getNominees() {
    return this.m_nominees;
  }

  public void setNominees(List nominees) {
    this.m_nominees = nominees;
  }

  public void setVoteComplete(boolean val) {
    this.m_voteComplete = val;
  }

  /**
   * In Vote messages, determine whether more vote blocks are available.
   *
   * @return True if the vote is complete, false if more votes should be
   *         requested.
   */
  public boolean isVoteComplete() {
    return m_voteComplete;
  }

  /**
   * In Vote Request messages, return the URL of the last vote block received.
   * If this is the first vote request message, or the only one, this value will
   * be null.
   *
   * @return The URL of the last vote block received.
   */
  public String getLastVoteBlockURL() {
    return m_lastVoteBlockURL;
  }

  // Vote Block accessors and iterator
  //
  // NOTE: For now, the list of vote blocks is implemented as an in-memory
  // array list. It will be desirable to refactor this into on-disk storage
  // because of the size of this list
  //

  public void addVoteBlock(VoteBlock vb) {
    m_voteBlocks.addVoteBlock(vb);
  }

  public ListIterator getVoteBlockIterator() {
    return m_voteBlocks.listIterator();
  }

  public VoteBlocks getVoteBlocks() {
    return m_voteBlocks;
  }

  public void setVoteBlocks(VoteBlocks voteBlocks) {
    m_voteBlocks = voteBlocks;
  }

  public void setRepairProps(CIProperties props) {
    if (props != null) {
      m_repairProps = EncodedProperty.fromProps(props);
    }
  }

  /**
   * Set the size of the repair data.
   *
   * @param len
   */
  public void setRepairDataLength(long len) {
    m_repairDataLen = len;
  }

  /**
   * Return the size of the repair data.
   */
  public long getRepairDataLength() {
    return m_repairDataLen;
  }

  /**
   *
   */
  public void setInputStream(InputStream is) {
    this.m_repairDataInputStream = is;
  }

  /**
   *
   * @return Input stream from which to read repair data.
   */
  public InputStream getRepairDataInputStream() throws IOException {
    if (m_dataFile != null && m_dataFile.exists() && m_dataFile.canRead()) {
      return new FileInputStream(m_dataFile);
    }
    return null;
  }

  public CIProperties getRepairProperties() {
    if (m_repairProps != null) {
      return CIProperties.fromProperties(m_repairProps);
    } else {
      return null;
    }
  }

  public void delete() {
    if (m_dataFile != null && m_dataFile.exists() && m_dataFile.canWrite()) {
      log.debug2("Deleting V3LcapMessage Data File: " + m_dataFile);
      m_dataFile.delete();
    }
  }
  //
  // Factory Methods
  //
  static public V3LcapMessage makeNoOpMsg(PeerIdentity originator,
                                          byte[] pollerNonce,
                                          byte[] voterNonce) {
    V3LcapMessage msg = new V3LcapMessage();
    msg.m_originatorID = originator;
    msg.m_opcode = MSG_NO_OP;
    msg.m_pollerNonce = pollerNonce;
    msg.m_voterNonce = voterNonce;
    msg.m_pollProtocol = Poll.V1_PROTOCOL;
    return msg;
  }

  /**
   * Make a NoOp message with randomly generated bytes.
   */
  static public V3LcapMessage makeNoOpMsg(PeerIdentity originator) {
    return V3LcapMessage.makeNoOpMsg(originator,
                                     ByteArray.makeRandomBytes(20),
                                     ByteArray.makeRandomBytes(20));
  }

  /**
   * make a message to request a poll using a pollspec.
   *
   * @param ps the pollspec specifying the url and bounds of interest
   * @param pollerNonce the poller nonce bytes
   * @param voterNonce the voter nonce bytes
   * @param opcode the kind of poll being requested
   * @param deadline the deadline for this poll
   * @param origin the identity of the requestor
   * @return message the new V3LcapMessage
   */
  static public V3LcapMessage makeRequestMsg(PollSpec ps, String key,
                                             byte[] pollerNonce,
                                             byte[] voterNonce, int opcode,
                                             long deadline,
                                             PeerIdentity origin) {
    return V3LcapMessage.makeRequestMsg(ps.getAuId(), key,
                                        ps.getProtocolVersion(),
                                        ps.getPluginVersion(), ps.getUrl(),
                                        pollerNonce, voterNonce, opcode,
                                        deadline, origin);
  }

  static public V3LcapMessage makeRequestMsg(String auId, String key,
                                             int pollVersion,
                                             String pluginVersion, String url,
                                             byte[] pollerNonce,
                                             byte[] voterNonce,
                                             int opcode, long deadline,
                                             PeerIdentity origin) {
    long start = TimeBase.nowMs();
    long stop = deadline;
    V3LcapMessage msg = new V3LcapMessage(opcode, key, origin, url, start, stop,
                                          pollerNonce, voterNonce);
    msg.setKey(key);
    msg.setArchivalId(auId);
    msg.setPollVersion(pollVersion);
    msg.setPluginVersion(pluginVersion);
    return msg;
  }

  // XXX: The implementation of getting the count of vote blocks
  // will have to change when the underlying structure is
  // refactored from an in-memory arraylist to a
  // disk backed structure.
  //
  public String toString() {
    StringBuffer sb = new StringBuffer();
    sb.append("[V3LcapMessage: from ");
    sb.append(m_originatorID);
    sb.append(", ");
    if (isNoOp()) {
      sb.append(getOpcodeString());
    } else {
      sb.append(m_targetUrl);
      sb.append(" ");
      sb.append(getOpcodeString());
      sb.append(" Key:");
      sb.append(m_key);
      sb.append(" PN:");
      sb.append(ByteArray.toBase64(m_pollerNonce));
      sb.append(" VN:");
      sb.append(ByteArray.toBase64(m_voterNonce));
      sb.append(" B:");
      sb.append(String.valueOf(m_voteBlocks.size()));
      sb.append(" ver " + m_pollProtocol + " rev " + V3_PROTOCOL_REV);
    }
    sb.append("]");
    return sb.toString();
  }
}
