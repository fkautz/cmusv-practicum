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

import org.lockss.daemon.Configuration;
import java.util.HashMap;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.lockss.util.Logger;
import java.util.Random;
import java.io.File;
import org.exolab.castor.xml.Unmarshaller;
import org.exolab.castor.xml.Marshaller;
import java.io.FileWriter;
import java.io.FileReader;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author Claire Griffin
 * @version 1.0
 */

public class IdentityManager {

  static final String PARAM_LOCAL_IP = Configuration.PREFIX + "localIPAddress";
  static final String PARAM_MAX_DELTA = Configuration.PREFIX + "id.maxReputationDelta";
  static final String PARAM_AGREE_DELTA = Configuration.PREFIX + "id.agreeDelta";
  static final String PARAM_DISAGREE_DELTA = Configuration.PREFIX + "id.disagreeDelta";
  static final String PARAM_CALL_INTERNAL = Configuration.PREFIX + "id.callInternalDelta";
  static final String PARAM_SPOOF_DETECTED = Configuration.PREFIX + "id.spoofDetected";
  static final String PARAM_REPLAY_DETECTED = Configuration.PREFIX + "id.replayDetected";
  static final String PARAM_ATTACK_DETECTED = Configuration.PREFIX + "id.attackDetected";
  static final String PARAM_VOTE_NOTVERIFIED = Configuration.PREFIX + "id.voteNotVerified ";
  static final String PARAM_VOTE_VERIFIED = Configuration.PREFIX + "id.voteVerified";
  static final String PARAM_VOTE_DISOWNED = Configuration.PREFIX + "id.voteDisowned";

  static final String PARAM_IDENTITY_DB_DIR = Configuration.PREFIX + "identityDatabaseDir";
  static final String IDENTITY_DB_FILENAME = "iddb.xml";
  /* Reputation constants */
  public static final int MAX_DELTA = 0;
  public static final int AGREE_VOTE = 1;
  public static final int DISAGREE_VOTE = 2;
  public static final int CALL_INTERNAL = 3;
  public static final int SPOOF_DETECTED = 4;
  public static final int REPLAY_DETECTED = 5;
  public static final int ATTACK_DETECTED = 6;
  public static final int VOTE_NOTVERIFIED = 7;
  public static final int VOTE_VERIFIED = 8;
  public static final int VOTE_DISOWNED = 9;

  int[] reputationDeltas;


  protected static final int INITIAL_REPUTATION = 500;
  protected static final int REPUTATION_NUMERATOR = 1000;
  private static IdentityManager theIdentityManager = null;
  static Logger theLog=Logger.getLogger("IdentityManager");
  static Random theRandom = new Random();
  LcapIdentity theLocalIdentity = null;
  String localIdentityStr = null;

  HashMap theIdentities = null; // all known identities

  private IdentityManager() {
    configure();
    reloadIdentities();
  }

  public static IdentityManager getIdentityManager() {
    if(theIdentityManager == null) {
      theIdentityManager = new IdentityManager();
    }

    return theIdentityManager;
  }


  /**
   * public constructor for the creation of an Identity object
   * from an address.
   * @param addr the InetAddress
   * @return a newly constructed Identity
   */
  public LcapIdentity getIdentity(InetAddress addr) {
    LcapIdentity ret;

    if(addr == null)  {
      ret = getLocalIdentity();
    }
    else  {
      ret = findIdentity(LcapIdentity.makeIdKey(addr));
      if(ret == null)  {
        ret = new LcapIdentity(addr);
        theIdentities.put(ret.getIdKey(), ret);
      }
    }

    return ret;
  }


  public LcapIdentity findIdentity(Object idKey)  {
    return (LcapIdentity)theIdentities.get(idKey);
  }

  /**
   * Get the Identity of the local host
   * @return newly constructed <code>Identity<\code>
   */
  public LcapIdentity getLocalIdentity() {
    if (theLocalIdentity == null)  {
      try {
        InetAddress addr = InetAddress.getByName(getLocalHostName());
        theLocalIdentity = new LcapIdentity(addr);
      } catch (UnknownHostException uhe) {
        theLog.error("Could not resolve: "+localIdentityStr, uhe);
      }
    }
    return theLocalIdentity;
  }

  /**
   * Get the local host name
   * @return hostname as a String
   */
  public String getLocalHostName() {
    if (localIdentityStr == null)  {
      localIdentityStr = Configuration.getParam(PARAM_LOCAL_IP);
    }
    return localIdentityStr;
  }

  /**
   * return true if this Identity is the same as the local host
   * @param id the LcapIdentity
   * @return boolean true if is the local identity, false otherwise
   */
  public boolean isLocalIdentity(LcapIdentity id) {
    if (theLocalIdentity == null)  {
      getLocalIdentity();
    }
    return id.isEqual(theLocalIdentity);
  }



  /**
   * return the max value of an Identity's reputation
   * @return the int value of max reputation
   */
  public int getMaxReputaion() {
    return REPUTATION_NUMERATOR;
  }

  private void configure() {
    reputationDeltas = new int[10];

    reputationDeltas[MAX_DELTA] = Configuration.getIntParam(PARAM_MAX_DELTA, 100);
    reputationDeltas[AGREE_VOTE] = Configuration.getIntParam(PARAM_AGREE_DELTA, 100);
    reputationDeltas[DISAGREE_VOTE] = Configuration.getIntParam(PARAM_DISAGREE_DELTA, -150);
    reputationDeltas[CALL_INTERNAL] = Configuration.getIntParam(PARAM_CALL_INTERNAL, 100);
    reputationDeltas[SPOOF_DETECTED] = Configuration.getIntParam(PARAM_SPOOF_DETECTED, -30);
    reputationDeltas[REPLAY_DETECTED] = Configuration.getIntParam(PARAM_REPLAY_DETECTED, -20);
    reputationDeltas[ATTACK_DETECTED] = Configuration.getIntParam(PARAM_ATTACK_DETECTED, -500);
    reputationDeltas[VOTE_NOTVERIFIED] = Configuration.getIntParam(PARAM_VOTE_NOTVERIFIED, -30);
    reputationDeltas[VOTE_VERIFIED] = Configuration.getIntParam(PARAM_VOTE_VERIFIED, 40);
    reputationDeltas[VOTE_DISOWNED] = Configuration.getIntParam(PARAM_VOTE_DISOWNED, -400);
  }

  void reloadIdentities()  {
 /*   try {
      String fn = Configuration.getParam(PARAM_IDENTITY_DB, "/tmp/iddb")
                  + File.separator + IDENTITY_DB_FILENAME;
      File iddbFile = new File(fn);

      Unmarshaller unmarshaller = new Unmarshaller(IdentityBean.class);
      IdentityBean nhb = (IdentityBean)unmarshaller.unmarshal(new FileReader(iddbFile));
      //XXX TODO  convert to our hashmap
      //((NodeStateImpl)nodeState).setPollHistoryBeanList(new ArrayList(nhb.historyBeans));
    } catch (Exception e) {
      theLog.error("Couldn't load identity history: ", e);
      throw new ProtocolException("Unable to load Identity Database XML");
    }
*/
    theIdentities = new HashMap();
  }

  void storeIdentities()  {
/*
    try {
      String fn = Configuration.getParam(PARAM_IDENTITY_DB_DIR,"/tmp/iddb");

      File iddbDir = new File(fn);
      if (!iddbDir.exists()) {
        iddbDir.mkdirs();
      }
      File iddbFile = new File(iddb, IDENTITY_DB_FILENAME);
      IdentityBean nhb = new IdentityBean();
     //XXX TODO: convert our hashmap
      Marshaller marshaller = new Marshaller(new FileWriter(iddbFile));
      marshaller.marshal(nhb);
    } catch (Exception e) {
      theLog.error("Couldn't store poll history: ", e);
      throw new ProtocolException("Unable to store Identity Database XML");
    }
*/
  }

  //
  public void changeReputation(LcapIdentity id, int changeKind) {
    int delta = reputationDeltas[changeKind];
    int max_delta = reputationDeltas[MAX_DELTA];
    int reputation = id.getReputation();

    if (id == theLocalIdentity) {
      theLog.debug(id.getIdKey() + " ignoring reputation delta " + delta);
      return;
    }

    delta = (int) (((float) delta) * theRandom.nextFloat());

    if (delta > 0) {
      if (delta > max_delta) {
        delta = max_delta;

      }
      if (delta > (REPUTATION_NUMERATOR - reputation)) {
        delta = (REPUTATION_NUMERATOR - reputation);

      }
    }
    else if (delta < 0) {
      if (delta < (-max_delta)) {
        delta = -max_delta;
      }
      if ((reputation + delta) < 0) {
        delta = -reputation;
      }
    }
    if (delta != 0)
      theLog.debug(id.getIdKey() +" change reputation from " + reputation +
                   " to " + (reputation + delta));
    id.changeReputation(delta);
  }
}