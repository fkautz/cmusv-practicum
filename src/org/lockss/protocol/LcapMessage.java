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

package org.lockss.protocol;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.StringTokenizer;
import org.lockss.util.*;
import java.io.*;
import org.mortbay.util.B64Code;
import org.lockss.daemon.Configuration;


/**
 * <p>Description: used to encapsulate a message which has been received
 * or will be sent over the wire. </p>
 * @author Claire Griffin
 * @version 1.0
 */

public class LcapMessage implements Serializable {
  public static final int NAME_POLL_REQ = 0;
  public static final int NAME_POLL_REP = 1;
  public static final int CONTENT_POLL_REQ = 2;
  public static final int CONTENT_POLL_REP = 3;
  public static final int VERIFY_POLL_REQ = 4;
  public static final int VERIFY_POLL_REP = 5;

  public static final String PARAM_HASH_ALGORITHM = Configuration.PREFIX +
      "protocol.hashAlgorithm";
  public static final String PARAM_SEND_HOPCOUNT = Configuration.PREFIX +
      "protocol.sendHopcount";

  public static final String DEFAULT_HASH_ALGORITM = "SHA-1";

  public static final String[] POLL_OPCODES =
  {"NameReq", "NameRep",
    "ContentReq", "ContentRep",
    "VerifyReq", "VerifyRep"};

  public static final String[] POLL_NAMES = {"NamePoll", "ContentPoll", "VerfiyPoll"};

  public static final int MAX_HOP_COUNT = 16;
  public static final int SHA_LENGTH = 20;
  public static final int MAX_PACKET_SIZE = 1024; // Don't exceed 1450 - 28

  /*
    byte
    0-3       signature
    4         multicast
    5         hopcount
    6-7       property length
    8-27      SHA-1 hash of encoded properties
    28-End    encoded properties
  */
  /* items which are not in the property list */
  byte[]             m_signature;   // magic number + version (4 bytes)
  boolean            m_multicast;   // multicast flag - modifiable
  byte               m_hopCount;    // current hop count - modifiable
  byte[]             m_pktHash;     // hash of remaining packet
  int                m_length;      // length of remaining packet

  /* items which are in the property list */
  LcapIdentity       m_originID;    // the address of the originator
  protected String   m_hashAlgorithm; // the algorithm used to hash
  byte               m_ttl;         // The original time-to-live
  long               m_startTime;   // the original start time
  long               m_stopTime;    // the original stop time
  int                m_opcode;      // the kind of packet
  protected String   m_targetUrl;   // the target URL
  protected String   m_regExp;      // the target regexp
  protected byte[]   m_challenge;   // the challenge bytes
  protected byte[]   m_verifier;    // th verifier bytes
  protected byte[]   m_hashed;      // the hash of content
  protected String   m_RERemaining; // the RegExp of the remaining entries (opt)
  protected String[] m_entries;     // the name poll entry list (opt)

  private EncodedProperty m_props;
  private static byte[] signature = {'l','p','m','1'};
  private static Logger log = Logger.getLogger("Message");
  private static byte theSendHopcount = -1;
  private String m_key = null;

  private static IdentityManager theIdentityMgr
      = IdentityManager.getIdentityManager();

  protected LcapMessage() throws IOException {
    m_props = new EncodedProperty();

  }

  protected LcapMessage(byte[] encodedBytes) throws IOException {
    m_props = new EncodedProperty();

    try {
      decodeMsg(encodedBytes);
    }
    catch (IOException ex) {
      log.error("Unreadable Packet",ex);
      throw new ProtocolException("Unable to decode pkt.");
    }
  }

  protected LcapMessage(String targetUrl,
                        String regExp,
                        String[] entries,
                        byte ttl,
                        byte[] challenge,
                        byte[] verifier,
                        byte[] hashedData,
                        int opcode) throws IOException {
    this();
    // assign the data
    m_targetUrl = targetUrl;
    m_regExp = regExp;
    m_ttl = ttl;
    m_challenge = challenge;
    m_verifier = verifier;
    m_hashed = hashedData;
    m_opcode = opcode;
    m_entries = entries;
    m_hashAlgorithm = getDefaultHashAlgorithm();
    // null the remaining undefined data
    m_startTime = 0;
    m_stopTime = 0;
    m_multicast = false;
    m_originID = null;
    m_hopCount = 0;
  }

  protected LcapMessage(LcapMessage trigger,
                        LcapIdentity localID,
                        byte[] verifier,
                        byte[] hashedContent,
                        String[] entries,
                        int opcode) throws IOException {

    this();
    // copy the essential information from the trigger packet
    m_hopCount =trigger.getHopCount();
    m_ttl = trigger.getTimeToLive();
    m_challenge = trigger.getChallenge();
    m_targetUrl = trigger.getTargetUrl();
    m_regExp = trigger.getRegExp();
    m_entries = entries;
    m_hashAlgorithm = trigger.getHashAlgorithm();
    m_originID = localID;
    m_verifier = verifier;
    m_hashed = hashedContent;
    m_opcode = opcode;

    m_startTime = 0;
    m_stopTime = 0;
    m_multicast = false;
  }

  public static String getDefaultHashAlgorithm() {
    String algorithm =  Configuration.getParam( PARAM_HASH_ALGORITHM,
        DEFAULT_HASH_ALGORITM);
    return algorithm;
  }

  /**
   * get a property that was decoded and stored for this packet
   * @param key - the property name under which the it is stored
   * @return a string representation of the property
   */
  public String getPacketProperty(String key) {
    return m_props.getProperty(key);
  }

  /**
   * set or add a new property into the packet.
   * @param key the key under which to store the property
   * @param value the value to store
   */
  protected void setMsgProperty(String key, String value) {
    m_props.setProperty(key, value);
  }

  /**
   * make a message to request a poll
   * @param targetUrl the url of the target poll
   * @param regExp the reg expression for the target poll
   * @param entries the array of entries found in the name poll
   * @param challenge the challange bytes
   * @param verifier the verifier bytes
   * @param opcode the kind of poll being requested
   * @param timeRemaining the time remaining for this poll
   * @param localID the identity of the requestor
   * @return message the new LcapMessage
   * @throws IOException if unable to creae message
   */
  static public LcapMessage makeRequestMsg(String targetUrl,
      String regExp,
      String[] entries,
      byte[] challenge,
      byte[] verifier,
      int opcode,
      long timeRemaining,
      LcapIdentity localID)
      throws IOException {

    LcapMessage msg = new LcapMessage(targetUrl,regExp,entries,(byte)0,
                                      challenge,verifier,new byte[0],opcode);
    if (msg != null) {
      msg.m_startTime = TimeBase.nowMs();
      msg.m_stopTime = msg.m_startTime + timeRemaining;
      msg.m_originID = localID;
      msg.m_hopCount = sendHopCount();
    }
    return msg;

  }

  /**
   * static method to make a reply message
   * @param trigger the message which trggered the reply
   * @param hashedContent the hashed content bytes
   * @param verifier the veerifier bytes
   * @param entries the entries which were used to calculate the hash
   * @param opcode an opcode for this message
   * @param timeRemaining the time remaining on the poll
   * @param localID the identity of the requestor
   * @return a new Message object
   * @throws IOException if message construction failed
   */
  static public LcapMessage makeReplyMsg(LcapMessage trigger,
      byte[] hashedContent,
      byte[] verifier,
      String[] entries,
      int opcode,
      long timeRemaining,
      LcapIdentity localID)
      throws IOException {
    if (hashedContent == null) {
      log.error("Making a reply message with null hashed content");
    }
    LcapMessage msg = new LcapMessage(trigger, localID, verifier,
                                      hashedContent, entries, opcode);
    if (msg != null) {
      msg.m_startTime = TimeBase.nowMs();
      msg.m_stopTime = msg.m_startTime + timeRemaining;
    }
    return msg;
  }

  /**
   * a static method to create a new message from a Datagram Packet
   * @param data the data from the message packet
   * @param mcast true if packet was from a multicast socket
   * @return a new Message object
   * @throws IOException
   */
  static public LcapMessage decodeToMsg(byte[] data,
                                        boolean mcast) throws IOException {
    LcapMessage msg = new LcapMessage(data);
    if(msg != null) {
      msg.m_multicast = mcast;
    }
    return msg;
  }

  /**
   * decode the raw packet data into a property table
   * @param encodedBytes the array of encoded bytes
   * @throws IOException
   * TODO: this needs to have the data broken into a variable portion
   * and a invariable portion and hashed
   */
  public void decodeMsg(byte[] encodedBytes) throws IOException {
    long duration;
    long elapsed;
    String addr;
    int    port;

    // the mutable stuff
    DataInputStream dis =
        new DataInputStream(new ByteArrayInputStream(encodedBytes));

    // read in the header
    for(int i=0; i< signature.length; i++) {
      if(signature[i] != dis.readByte()) {
        throw new ProtocolException("Invalid Signature");
      }
    }

    m_multicast = dis.readBoolean();
    m_hopCount =  dis.readByte();

    if(!hopCountInRange(m_hopCount)) {
      throw new ProtocolException("Hop count out of range.");
    }
    m_hopCount--;

    int prop_len = dis.readShort();
    byte[] hash_bytes = new byte[SHA_LENGTH];
    byte[] prop_bytes = new byte[prop_len];
    dis.read(hash_bytes);
    dis.read(prop_bytes);

    if(!verifyHash(hash_bytes,prop_bytes)) {
      throw new ProtocolException("Hash verification failed.");
    }

    // decode the properties
    m_props.decode(prop_bytes);

    // the immutable stuff
    port = m_props.getInt("origPort", -1);
    String addr_str = m_props.getProperty("origIP");
    try {
      m_originID = theIdentityMgr.getIdentity(LcapIdentity.stringToAddr(addr_str));
    }
    catch(UnknownHostException ex) {
      log.warning("Unknown originating host:" + addr_str);
    }

    addr_str = m_props.getProperty("group");
    m_hashAlgorithm = m_props.getProperty("hashAlgorithm");
    m_ttl = (byte) m_props.getInt("ttl", 0);
    duration = m_props.getInt("duration", 0) * 1000;
    elapsed = m_props.getInt("elapsed", 0)* 1000;
    m_opcode = m_props.getInt("opcode", -1);
    m_targetUrl = m_props.getProperty("url");
    m_regExp = m_props.getProperty("regexp");
    m_challenge = m_props.getByteArray("challenge", new byte[0]);
    m_verifier = m_props.getByteArray("verifier", new byte[0]);
    m_hashed = m_props.getByteArray("hashed", new byte[0]);
    if (m_props.getProperty("entries") != null) {
      m_entries = stringToEntries(m_props.getProperty("entries"));
    }
    m_RERemaining = m_props.getProperty("remaining");
    // calculate start and stop times
    long now = TimeBase.nowMs();
    m_startTime = now - elapsed;
    m_stopTime = now + duration;
  }

  /**
   * encode the message from a props table into a stream of bytes
   * @return the encoded message as bytes
   * @throws IOException if the packet can not be encoded
   */
  public byte[] encodeMsg() throws IOException {
    // make sure the props table is up to date
    try {
      m_props.setProperty("origIP",
                          LcapIdentity.addrToString(m_originID.getAddress()));
    }
    catch(NullPointerException npe) {
      throw new ProtocolException("encode - null origin host address.");
    }

    m_props.setProperty("hashAlgorithm", m_hashAlgorithm);
    m_props.putInt("ttl",m_ttl);
    m_props.putInt("duration",(int)(getDuration()/1000));
    m_props.putInt("elapsed",(int)(getElapsed()/1000));
    m_props.putInt("opcode",m_opcode);
    m_props.setProperty("url", m_targetUrl);
    if(m_regExp != null) {
      m_props.setProperty("regexp",m_regExp);
    }
    m_props.putByteArray("challenge", m_challenge);
    m_props.putByteArray("verifier", m_verifier);
    if(m_hashed != null) {
      m_props.putByteArray("hashed", m_hashed);
    }
    else if(m_opcode % 2 == 1) { // if we're a reply we'd better have a hash
      throw new ProtocolException("encode - missing hash in reply packet.");
    }
    byte[] cur_bytes = m_props.encode();
    int remaining_bytes = MAX_PACKET_SIZE - cur_bytes.length - 28;

    if (m_entries != null) {
      m_props.setProperty("entries",entriesToString(remaining_bytes));
    }

    if(m_RERemaining != null) {
      m_props.put("remaining", m_RERemaining);
    }

    byte[] prop_bytes = m_props.encode();
    byte[] hash_bytes = computeHash(prop_bytes);

    // build out the remaining packet
    int enc_len = prop_bytes.length + hash_bytes.length + 8;
    ByteArrayOutputStream baos = new ByteArrayOutputStream(enc_len);
    DataOutputStream dos = new DataOutputStream(baos);
    dos.write(signature);
    dos.writeBoolean(m_multicast);
    dos.writeByte(m_hopCount);
    dos.writeShort(prop_bytes.length);
    dos.write(hash_bytes);
    dos.write(prop_bytes);
    return baos.toByteArray();
  }

  public long getDuration() {
    long now = TimeBase.nowMs();
    long ret = m_stopTime - now;
    if (ret < 0)
      ret = 0;
    return ret;
  }

  public long getElapsed() {
    long now = TimeBase.nowMs();
    long ret = now - m_startTime;
    if (now > m_stopTime)
      ret = m_stopTime - m_startTime;
    return ret;
  }

  public boolean isReply() {
    return (m_opcode % 2 == 1) ? true : false;
  }

  public boolean isLocal() {
    return theIdentityMgr.isLocalIdentity(m_originID);

  }

  public boolean isNamePoll() {
    return m_opcode == NAME_POLL_REQ || m_opcode == NAME_POLL_REP;
  }

  public boolean isContentPoll() {
    return m_opcode == CONTENT_POLL_REQ || m_opcode == CONTENT_POLL_REP;
  }

  public boolean isVerifyPoll() {
    return m_opcode == VERIFY_POLL_REQ || m_opcode == VERIFY_POLL_REP;
  }

  /* methods to support data access */
  public long getStartTime() {
    return m_startTime;
  }

  public long getStopTime() {
    return m_stopTime;
  }

  public byte getTimeToLive() {
    return m_ttl;
  }

  public LcapIdentity getOriginID(){
    return m_originID;
  }

  public int getOpcode() {
    return m_opcode;
  }

  public String getOpcodeString() {
    return POLL_OPCODES[m_opcode];
  }

  public boolean getMulticast() {
    return m_multicast;
  }

  public void setMulticast(boolean multicast) {
    m_multicast = multicast;
  }

  public String[] getEntries() {
    return m_entries;
  }

  public String getRERemaining() {
    return m_RERemaining;
  }

  public byte getHopCount() {
    return m_hopCount;
  }

  public void setHopCount(int hopCount) {
    m_hopCount = (byte)hopCount;
  }

  public byte[] getChallenge() {
    return m_challenge;
  }

  public byte[] getVerifier() {
    return m_verifier;
  }

  public byte[] getHashed() {
    return m_hashed;
  }


  public String getTargetUrl() {
    return m_targetUrl;
  }

  public String getRegExp() {
    return m_regExp;
  }

  public String getHashAlgorithm() {
    return m_hashAlgorithm;
  }

  public String getKey() {
    if(m_key == null) {
      m_key = String.valueOf(B64Code.encode(m_challenge));
    }
    return m_key;
  }

  static byte sendHopCount() {
    if(theSendHopcount == -1) {
      theSendHopcount = (byte)Configuration.getIntParam(PARAM_SEND_HOPCOUNT,5);
    }
    return theSendHopcount;
  }

  String entriesToString(int maxBufSize) {
    StringBuffer buf = new StringBuffer(maxBufSize);

    for(int i= 0; i< m_entries.length; i++) {
      // if the length of this entry < max buffer
      byte[] cur_bytes = m_props.encodeString(buf.toString());
      byte[] entry_bytes = m_props.encodeString(m_entries[i]);
      if(cur_bytes.length + entry_bytes.length < maxBufSize) {
        buf.append(m_entries[i]);
        buf.append("\n");
      }
      else {
        // we need to set RERemaining and break
        setRERemaining(i);
        break;
      }
    }
    return buf.toString();
  }


  String[] stringToEntries(String estr) {
    StringTokenizer tokenizer = new StringTokenizer(estr,"\n");
    String[] ret = new String[tokenizer.countTokens()];
    int i = 0;

    while(tokenizer.hasMoreTokens()) {
      ret[i++] = tokenizer.nextToken();
    }
    return ret;
  }

  void setRERemaining(int firstIndex) {
    String last_entry = m_entries[firstIndex -1];
    String first_extra = m_entries[firstIndex];
    String last_extra = m_entries[m_entries.length -1];
    StringBuffer buf = new StringBuffer();
    // find a reg expression to represent the remaining entries in the list
    // our original match
    // plus the re for what's not in the entries array
    //m_RERemaining = buf.toString();
  }

  public String toString() {
    StringBuffer sb = new StringBuffer();
    sb.append("[LcapMessage: ");
    sb.append(m_targetUrl);
    sb.append(" ");
    sb.append(m_regExp);
    sb.append(" ");
    sb.append(POLL_OPCODES[m_opcode]);
    sb.append(" C:");
    sb.append(String.valueOf(B64Code.encode(m_challenge)));
    sb.append(" V:");
    sb.append(String.valueOf(B64Code.encode(m_verifier)));
    if (m_hashed != null) { //can be null for a request message
      sb.append(" H:");
      sb.append(String.valueOf(B64Code.encode(m_hashed)));
    }
    sb.append("]");
    return sb.toString();
  }

  private boolean hopCountInRange(byte hopCount) {
    if (hopCount < 0 || hopCount > MAX_HOP_COUNT)
      return false;
    return true;
  }

  private static boolean verifyHash(byte[] hashValue, byte[]data) {
    try {
      MessageDigest hasher = MessageDigest.getInstance("SHA");
      hasher.update(data);
      byte[] hashed = hasher.digest();
      return Arrays.equals(hashValue,hashed);
    } catch (java.security.NoSuchAlgorithmException e) {
      return false;
    }
  }

  private static byte[] computeHash(byte[] data) {
    try {
      MessageDigest hasher = MessageDigest.getInstance("SHA");
      hasher.update(data);
      byte[] hashed = hasher.digest();
      return hashed;
    } catch (java.security.NoSuchAlgorithmException e) {
      return new byte[0];
    }
  }
}