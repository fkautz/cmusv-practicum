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

import java.security.MessageDigest;
import java.util.StringTokenizer;
import java.io.*;
import java.security.*;
import java.util.*;

import org.lockss.poller.*;
import org.lockss.util.*;
import org.lockss.config.Configuration;

import org.mortbay.util.B64Code;

/**
 * <p>Abstract base class for concrete implementations of LCAP message versions.</p>
 *
 * <p>Implementations of
 *
 * @author Claire Griffin
 * @version 1.0
 */

public abstract class LcapMessage {
  
  public static final String PARAM_HASH_ALGORITHM = Configuration.PREFIX +
    "protocol.hashAlgorithm";
  public static final String PARAM_MAX_PKT_SIZE = Configuration.PREFIX +
    "protocol.maxPktSize";

  public static final String DEFAULT_HASH_ALGORITHM = "SHA-1";
  public static final int SHA_LENGTH = 20;
  public static final int MAX_HOP_COUNT_LIMIT = 16;
  public static final byte[] EMPTY_BYTE_ARRAY = new byte[0];
  public static final byte[] signature = { 'l', 'p', 'm' };

  /* items that are not in the property list */
  protected int m_pollVersion; // poll version number
  protected byte[] m_pktHash; // hash of remaining packet
  protected int m_length; // length of remaining packet

  /* items that are in the property list */
  protected PeerIdentity m_originatorID; // the peer identity of the originator
  protected String m_hashAlgorithm; // the algorithm used to hash
  protected byte m_ttl; // The original time-to-live
  protected long m_startTime; // the original start time
  protected long m_stopTime; // the original stop time
  protected int m_opcode; // the kind of packet
  protected String m_pluginVersion; // the plugin version
  protected String m_archivalID; // the archival unit
  protected String m_targetUrl; // the target URL
  protected byte[] m_challenge; // the challenge bytes
  protected byte[] m_verifier; // the verifier bytes
  protected byte[] m_hashed; // the hash of content

  protected String m_key = null;

  protected EncodedProperty m_props;

  protected static IdentityManager m_idManager = null;

  private static Logger log = Logger.getLogger("LcapMessage");

  //
  // Methods that must be implemented by subclasses.
  //

  /**
   * decode the raw packet data into a property table
   * @param encodedBytes the array of encoded bytes
   * @throws IOException
   */
  public abstract void decodeMsg(byte[] encodedBytes) throws IOException;

  /**
   * Decode the raw packet data into a property table.
   *
   * @param inputStream An input stream from which to read encoded bytes.
   * @throws IOException
   */
  public abstract void decodeMsg(InputStream inputStream) throws IOException;
  
  /**
   * encode the message from a props table into a stream of bytes
   * @return the encoded message as bytes
   * @throws IOException if the packet can not be encoded
   */
  public abstract byte[] encodeMsg() throws IOException;

  /**
   * Return an output stream from which to read bytes representing
   * the encoded form of this message.
   *
   * @return An output stream containing the bytes of this message.
   * @throws IOException if the packet cannot be encoded.
   */
  public abstract ByteArrayOutputStream getOutputStream() throws IOException;

  /**
   * store the local variables in the property table
   * @throws IOException if the packet can not be stored
   */
  abstract public void storeProps() throws IOException;

  abstract public String getOpcodeString();

  abstract public String getKey();

  abstract public boolean supportedPollVersion(int vers);

  abstract public boolean isNoOp();

  //
  // Common functionality between all versions.
  //

  public int getOpcode() {
    return m_opcode;
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

  public long getStartTime() {
    return m_startTime;
  }

  public void setStartTime(long l) {
    m_startTime = l;
  }

  public long getStopTime() {
    return m_stopTime;
  }

  public void setStopTime(long l) {
    m_stopTime = l;
  }

  public byte getTimeToLive() {
    return m_ttl;
  }

  public void setTimeToLive(byte b) {
    m_ttl = b;
  }

  public PeerIdentity getOriginatorId() {
    return m_originatorID;
  }

  public void setOriginatorId(PeerIdentity id) {
    m_originatorID = id;
  }

  public String getArchivalId() {
    return m_archivalID;
  }

  public void setArchivalId(String s) {
    m_archivalID = s;
  }

  public String getPluginVersion() {
    return m_pluginVersion;
  }

  public void setPluginVersion(String s) {
    m_pluginVersion = s;
  }

  public int getPollVersion() {
    return m_pollVersion;
  }

  public void setPollVersion(int vers) {
    m_pollVersion = vers;
  }

  public byte[] getChallenge() {
    return m_challenge;
  }

  public void setChallenge(byte[] b) {
    m_challenge = b;
  }

  public byte[] getVerifier() {
    return m_verifier;
  }

  public void setVerifier(byte[] b) {
    m_verifier = b;
  }

  public byte[] getHashed() {
    return m_hashed;
  }

  public void setHashed(byte[] b) {
    m_hashed = b;
  }

  public String getTargetUrl() {
    return m_targetUrl;
  }

  public void setTargetUrl(String s) {
    m_targetUrl = s;
  }

  public String getHashAlgorithm() {
    if (m_hashAlgorithm == null) {
      return LcapMessage.getDefaultHashAlgorithm();
    } else {
      return m_hashAlgorithm;
    }
  }

  public void setHashAlgorithm(String s) {
    m_hashAlgorithm = s;
  }

  //
  // Statics.
  //
  public static void setIdentityManager(IdentityManager im) {
    m_idManager = im;
  }

  public static String getDefaultHashAlgorithm() {
    String algorithm = Configuration.getParam(PARAM_HASH_ALGORITHM,
					      DEFAULT_HASH_ALGORITHM);
    return algorithm;
  }

  public static MessageDigest getDefaultHasher() {
    MessageDigest hasher = null;
    try {
      hasher = MessageDigest.getInstance(getDefaultHashAlgorithm());
    }
    catch (NoSuchAlgorithmException ex) {
      log.error("Unable to run - no hasher");
    }

    return hasher;
  }


  //
  // Utility methods
  //
  protected boolean verifyHash(byte[] hashValue, byte[] data) {
    try {
      MessageDigest hasher = MessageDigest.getInstance("SHA");
      hasher.update(data);
      byte[] hashed = hasher.digest();
      return Arrays.equals(hashValue, hashed);
    } catch (java.security.NoSuchAlgorithmException e) {
      return false;
    }
  }

  protected byte[] computeHash(byte[] data) {
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
