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
import java.net.*;
import java.security.*;
import java.util.*;


import org.lockss.daemon.*;
import org.lockss.plugin.*;
import org.lockss.protocol.*;
import org.lockss.protocol.ProtocolException;
import org.lockss.util.*;
import org.mortbay.util.*;
import gnu.regexp.*;

public class PollManager {

  static final String HASH_ALGORITHM = "SHA-1";
  static final long EXPIRATION_TIME = 24 * 60 * 60 * 1000; // 1 day
  static final long VERIFIER_EXPIRATION_TIME = 24 * 60 * 60 * 1000;

  private static Logger theLog=Logger.getLogger("PollManager");
  private static HashMap thePolls = new HashMap();
  private static HashMap theRecentPolls = new HashMap();
  private static HashMap theVerifiers = new HashMap();
  private static Random theRandom = new Random();

  public PollManager() {
  }

  /**
   * make a new poll of the type defined by the incoming message.
   * @param msg <code>Message</code> to use for
   * @return a new Poll object of the required type
   * @throws IOException if message opcode is unknown or if new poll would
   * conflict with currently running poll.
   */
  public static Poll makePoll(LcapMessage msg) throws IOException {
    Poll ret_poll;
    ArchivalUnit au;
    CachedUrlSet cus;

    // check for presence of item in the cache
    try {
      au = Plugin.findArchivalUnit(msg.getTargetUrl());
      cus = au.makeCachedUrlSet(msg.getTargetUrl(), msg.getRegExp());
    }
    catch (Exception ex) {
      theLog.debug(msg.getTargetUrl() + " not in this cache.");
      return null;
    }

    // check for conflicts
    CachedUrlSet conflict = checkForConflicts(msg);
    if(conflict != null) {
      String err = msg.toString() + " conflicts with " + conflict.toString() +
                                  " in makeElection()";
      theLog.debug(err);
      throw new ProtocolException(err);
    }

    // create the appropriate message type
    switch(msg.getOpcode()) {
      case LcapMessage.CONTENT_POLL_REP:
      case LcapMessage.CONTENT_POLL_REQ:
        theLog.debug("Making a content poll on "+cus);
        ret_poll = new ContentPoll(msg, cus);
        break;
      case LcapMessage.NAME_POLL_REP:
      case LcapMessage.NAME_POLL_REQ:
        theLog.debug("Making a name poll on "+cus);
        ret_poll = new NamePoll(msg, cus);
        break;
      case LcapMessage.VERIFY_POLL_REP:
      case LcapMessage.VERIFY_POLL_REQ:
        theLog.debug("Checking for multicast verfiy poll...");
        String key = makeKey(msg.getChallenge());
        ret_poll = (Poll)thePolls.get(key);
        if( ret_poll == null) {
          theLog.debug("Making a verify poll on "+cus);
          ret_poll = new VerifyPoll(msg, cus);
        }
        break;
      default:
        throw new ProtocolException("Unknown opcode:" +
        msg.getOpcode());
    }
    if(ret_poll != null && thePolls.get(ret_poll.m_key) == null) {
      thePolls.put(ret_poll.m_key, ret_poll);
    }
    return ret_poll;
  }


  /**
   * make an election by sending a request packet.  This is only
   * called from the tree walk. The poll remains pending in the
   * @param url the String representing the url for this poll
   * @param regexp the String representing the regexp for this poll
   * @param opcode the poll message opcode
   * @param timeToLive the time to live for the new message
   * @param grpAddr the InetAddress used to construct the message
   * @param duration the time this poll has to run
   * @param voteRange the probabilistic vote range
   * @throws IOException thrown if Message construction fails.
   */
  public static void makePollRequest(String url,
                                     String regexp,
                                     int opcode,
                                     int timeToLive,
                                     InetAddress grpAddr,
                                     long duration,
                                     long voteRange)
      throws IOException {
    if (voteRange > (duration/4)) {
      voteRange = duration/4;
    }
    Deadline deadline = Deadline.inRandomRange(duration - voteRange,
        duration + voteRange);
    long timeUntilCount = deadline.getRemainingTime();
    byte[] challenge = makeVerifier();
    byte[] verifier = makeVerifier();
    LcapMessage msg = LcapMessage.makeRequestMsg(url,regexp,null,grpAddr,
        (byte)timeToLive,
        challenge,verifier,opcode,timeUntilCount,
        LcapIdentity.getLocalIdentity());

    theLog.debug("send: " +  msg.toString());
    LcapComm.sendMessage(msg, Plugin.findArchivalUnit(url));
  }


  /**
   * Find the poll defined by the <code>Message</code>.  If the poll
   * does not exist this will create a new poll
   * @param msg <code>Message</code>
   * @return <code>Poll</code> which matches the message opcode.
   * @throws IOException if message opcode is unknown or if new poll would
   * conflict with currently running poll.
   * @see <code>Poll.createPoll</code>
   */
  public static Poll findPoll(LcapMessage msg) throws IOException {
    String key = makeKey(msg.getChallenge());
    Poll ret = (Poll)thePolls.get(key);
    if(ret == null) {
      theLog.info("Making new poll: "+key);
      ret = makePoll(msg);
      theLog.info("Done making new poll: "+key);
      ret.startPoll();
    }
    return ret;
  }


  /**
   * handle an incoming message packet.  This will create a poll if
   * one is not already running. It will then call recieveMessage on
   * the poll.  This was moved from node state which kept track of the polls
   * running in the node.  This will need to be moved or amended to support this.
   * @param msg the message used to generate the poll
   * @throws IOException thrown if the poll was unsucessfully created
   */
  public static void handleMessage(LcapMessage msg) throws IOException {
    theLog.info("Got a message: " + msg);
    if(isPollClosed(msg.getChallenge())) {
      theLog.info("Message received after poll was closed." + msg.toString());
      // XXX - what to do here - not really an exception
      return;
    }
    Poll p = findPoll(msg);
    if (p == null) {
      theLog.error("Unable to create poll for Message: " + msg.toString());
      throw new ProtocolException("Failed to create poll for "
                                  + msg.toString());
    }
    // XXX - we need to notify someone that this poll is running in this node!!!
    p.receiveMessage(msg);
  }


  /**
   * remove the poll represented by the given key from the poll table and
   * return it.
   * @param key the String representation of the polls key
   * @return Poll the poll if found or null
   */
  static Poll removePoll(String key) {
    return (Poll)thePolls.remove(key);
  }


  /**
   * check for conflicts between the poll defined by the Message and any
   * currently exsiting poll.
   * @param msg the <code>Message</code> to check
   * @return the CachedUrlSet of the conflicting poll.
   */
  static CachedUrlSet checkForConflicts(LcapMessage msg) {
    String url = msg.getTargetUrl();
    String regexp = msg.getRegExp();
    int    opcode = msg.getOpcode();
    boolean isRegExp = regexp != null;

    // verify polls are never conflicts
    if((opcode == LcapMessage.VERIFY_POLL_REP)||
       (opcode == LcapMessage.VERIFY_POLL_REQ)) {
      return null;
    }

    Iterator iter = thePolls.values().iterator();
    while(iter.hasNext()) {
      Poll p = (Poll)iter.next();

      LcapMessage p_msg = p.getMessage();
      String  p_url = p_msg.getTargetUrl();
      String  p_regexp = p_msg.getRegExp();
      int     p_opcode = p_msg.getOpcode();
      boolean p_isRegExp = p_regexp != null;

      // eliminate verify polls
      if((p_opcode == LcapMessage.VERIFY_POLL_REP)||
         (p_opcode == LcapMessage.VERIFY_POLL_REQ)) {
        continue;
      }
      //XXX this should be handled by something else
      boolean alongside = p_url.equals(url);
      boolean below = (p_url.startsWith(url) &&
                       url.endsWith(File.separator));
      boolean above = (url.startsWith(p_url) &&
                       p_url.endsWith(File.separator));

      // check for content poll conflicts
      if((opcode == LcapMessage.CONTENT_POLL_REP) ||
         (opcode == LcapMessage.CONTENT_POLL_REQ)) {

        if(alongside) { // only verify polls are allowed
          theLog.debug("conflict new content or name poll alongside content poll");
          return p.getCachedUrlSet();
        }

        if(above || below) { // verify and regexp polls are allowed
          if((p_opcode == LcapMessage.CONTENT_POLL_REP) ||
             (p_opcode == LcapMessage.CONTENT_POLL_REQ)) {
            if(p_isRegExp) {
              continue;
            }
          }
          theLog.debug("conflict new name poll above or below content poll");
          return p.getCachedUrlSet();
        }
      }

      // check for name poll conflicts
      if((opcode == LcapMessage.NAME_POLL_REP) ||
         (opcode == LcapMessage.NAME_POLL_REQ)) {
        if(alongside) { // only verify polls are allowed
          theLog.debug("conflict new content or name poll alongside name poll");
          return p.getCachedUrlSet();
        }
        if(above) { // only reg exp polls are allowed
          if((p_opcode == LcapMessage.CONTENT_POLL_REP) ||
             (p_opcode == LcapMessage.CONTENT_POLL_REQ)) {
            if(p_isRegExp) {
              continue;
            }
          }
          theLog.debug("conflict new name poll above name poll");
          return p.getCachedUrlSet();
        }

        if(below) { // always a conflict
          theLog.debug("conflict new poll below name poll");
          return p.getCachedUrlSet();
        }
      }
    }

    return null;
  }

  /**
   * is this a poll that has been recently closed
   * @param challenge the challenge identifying the poll
   * @return true
   */
  static boolean isPollClosed(byte[] challenge) {
    String key = makeKey(challenge);
    synchronized(theRecentPolls) {
      return (theRecentPolls.containsKey(key));
    }
  }

  /**
   * close the poll from any further voting
   * @param key the poll signature
   */
  static void closeThePoll(String key)  {
    // remove the poll from the active poll table
    synchronized(thePolls) {
      thePolls.remove(key);
    }
    // add the key to the recent polls table with an expiration time
    long expiration = TimeBase.nowMs() + EXPIRATION_TIME;
    Deadline d = Deadline.in(expiration);
    TimerQueue.schedule(d, new ExpireRecentCallback(), key);
    theRecentPolls.put(key, new Long(expiration));

  }

  static MessageDigest getHasher() {
    MessageDigest hasher = null;
    try {
      hasher = MessageDigest.getInstance(PollManager.HASH_ALGORITHM);
    } catch (NoSuchAlgorithmException ex) {
      theLog.error("Unable to run - no hasher");
    }

    return hasher;
  }


  /**
   * return a key from an array of bytes.
   * @param keyBytes the array of bytes to use
   * @return a base64 string representation of the byte array
   */
  static String makeKey(byte[] keyBytes) {
    return String.valueOf(B64Code.encode(keyBytes));
  }


  /**
   * make a verifier by generating a secret and hashing it. Then store the
   * verifier/secret pair in the verifiers table.
   * @return the array of bytes representing the verifier
   */
  static byte[] makeVerifier() {
    byte[] s_bytes = generateRandomBytes();
    byte[] v_bytes = generateVerifier(s_bytes);
    if(v_bytes != null) {
      rememberVerifier(v_bytes, s_bytes);
    }
    return v_bytes;
  }


  /**
   * get the array of bytes that represents the secret used to generate the
   * verifier bytes.  This extracts the secret from the table of verifiers.
   * @param verifier the array of bytes that is a hash of the secret.
   * @return the array of bytes representing the secret or if no matching
   * verifier is found, null.
   */
  static byte[] getSecret(byte[] verifier) {
    String ver = String.valueOf(B64Code.encode(verifier));
    String sec = (String)theVerifiers.get(ver);
    if (sec != null && sec.length() > 0) {
      return (B64Code.decode(sec.toCharArray()));
    }
    return null;
  }


  /**
   * generate a random array of 20 bytes
   * @return the array of bytes
   */
  static byte[] generateRandomBytes() {
    byte[] secret = new byte[20];
    theRandom.nextBytes(secret);
    return secret;
  }


  /**
   * generate a verifier from a array of bytes representing a secret
   * @param secret the bytes representing a secret to be hashed
   * @return an array of bytes representing a verifier
   */
  static byte[] generateVerifier(byte[] secret) {
    byte[] verifier = null;
    MessageDigest hasher = getHasher();
    hasher.update(secret, 0, secret.length);
    verifier = hasher.digest();

    return verifier;
  }


  static private void rememberVerifier(byte[] verifier,
                                       byte[] secret) {
    String ver = String.valueOf(B64Code.encode(verifier));
    String sec = secret == null ? "" : String.valueOf(B64Code.encode(secret));
    long expiration = TimeBase.nowMs() + VERIFIER_EXPIRATION_TIME;
    Deadline d = Deadline.in(expiration);
    TimerQueue.schedule(d, new ExpireVerifierCallback(), ver);
    synchronized (theVerifiers) {
      theVerifiers.put(ver, sec);
    }
  }

  static class ExpireRecentCallback implements TimerQueue.Callback {
    /**
     * Called when the timer expires.
     * @param cookie  data supplied by caller to schedule()
     */
    public void timerExpired(Object cookie) {
      synchronized(theRecentPolls) {
        theRecentPolls.remove(cookie);
      }
    }
  }

  static class ExpireVerifierCallback implements TimerQueue.Callback {
    /**
     * Called when the timer expires.
     * @param cookie  data supplied by caller to schedule()
     */
    public void timerExpired(Object cookie) {
      synchronized(theVerifiers) {
        theVerifiers.remove(cookie);
      }
    }
  }
}