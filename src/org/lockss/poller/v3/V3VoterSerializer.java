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

import java.io.*;

import org.lockss.app.*;
import org.lockss.protocol.psm.*;

public class V3VoterSerializer extends V3Serializer {

  private static final String VOTER_USER_DATA_FILE = "voter_user_data.xml";
  private static final String VOTER_STATE_TABLE_FILE = "voter_state_table.xml";

  private File voterUserDataFile;
  private File voterStateTableFile;

  public V3VoterSerializer(LockssDaemon daemon)
      throws PollSerializerException {
    this(daemon, null);
  }

  public V3VoterSerializer(LockssDaemon daemon, String dir)
      throws PollSerializerException {
    super(daemon, dir);
    this.voterUserDataFile = new File(pollDir, VOTER_USER_DATA_FILE);
    this.voterStateTableFile = new File(pollDir, VOTER_STATE_TABLE_FILE);
  }

  public void saveVoterUserData(VoterUserData data)
      throws PollSerializerException {
    try {
      xstr.serialize(voterUserDataFile, data);
    } catch (Exception ex) {
      throw new PollSerializerException("Unable to save VoterUserData", ex);
    }
  }

  public VoterUserData loadVoterUserData()
      throws PollSerializerException {
    try {
      return (VoterUserData) xstr.deserialize(voterUserDataFile);
    } catch (Exception ex) {
      throw new PollSerializerException("Unable to restore VoterUserData", ex);
    }
  }

  /**
   * Save a PsmInterpStateBean for a V3Voter.
   */
  public void saveVoterInterpState(PsmInterpStateBean bean)
      throws PollSerializerException {
    try {
      xstr.serialize(this.voterStateTableFile, bean);
    } catch (Exception ex) {
      throw new PollSerializerException("Unable to save PsmInterpStateBean", ex);
    }
  }

  /**
   * Restore a PsmInterpStateBean for a V3Voter.
   */
  public PsmInterpStateBean loadVoterInterpState()
      throws PollSerializerException {
    try {
      return (PsmInterpStateBean) xstr.deserialize(this.voterStateTableFile);
    } catch (Exception ex) {
      throw new PollSerializerException("Unable to restore PsmInterpStateBean",
                                        ex);
    }
  }
}
