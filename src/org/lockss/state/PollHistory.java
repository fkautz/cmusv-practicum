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


package org.lockss.state;

import java.util.*;
import org.lockss.protocol.LcapIdentity;

/**
 * PollHistory contains the information for a completed poll.  It extends
 * PollState but ignores 'getDeadline()' (returns null).
 */
public class PollHistory extends PollState {
  long duration;
  Collection votes;
  Collection immutableVotes;

  /**
   * Empty constructor used for marshalling.  Needed to create the
   * PollHistoryBean.
   */
  public PollHistory() {
    super(-1, null, -1, 0, null);
    duration = 0;
    votes = null;
    immutableVotes = null;
  }

  PollHistory(PollState state, long duration, Collection votes) {
    super(state.type, state.regExp, state.status, state.startTime, null);
    this.duration = duration;
    this.votes = votes;
    immutableVotes = null;
  }

  /**
   * Returns the duration the poll took.
   * @return the duration in ms
   */
  public long getDuration() {
    return duration;
  }

  /**
   * Returns an immutable collection of Votes.
   * @return a Collection of Poll.Vote objects.
   */
  public Collection getVotes() {
    if (immutableVotes==null) {
      immutableVotes = Collections.unmodifiableCollection(votes);
    }
    return immutableVotes;
  }
}