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

import java.util.Iterator;
import org.lockss.daemon.CachedUrlSet;
import org.lockss.poller.Poll;
import org.lockss.daemon.ArchivalUnit;
import org.lockss.util.Deadline;

/**
 * NodeManager handles all nodes in a given system.  It retrieves and stores
 * polls histories, manages tree walks, and maintains current state of the
 * system.
 */
public interface NodeManager {

  /**
   * update a node state with current poll results
   * @param cus the cached url set used to identify the node
   * @param results the poll results
   */
  public void updatePollResults(CachedUrlSet cus, Poll.VoteTally results);

  /**
   * Return the node represented by a given CachedUrlSet
   * @param cus the cached url set used to identify the top node
   * @return the NodeState
   */
  public NodeState getNodeState(CachedUrlSet cus);

  /**
   * Returns an iterator of all node states currently being crawled
   * @param cus the cached url set used to identify the top node
   * @return an Iterator of NodeStates
   */
  public Iterator getActiveCrawledNodes(CachedUrlSet cus);

  /**
   * Returns an iterator of all node states in which a poll is running,
   * filtered by bitwise addition of states.
   * @param cus the cached url set used to identify the top node
   * @param filter the bitwise state filter
   * @return an Iterator of NodeStates
   */
  public Iterator getFilteredPolledNodes(CachedUrlSet cus, int filter);

  /**
   * Returns an iterator of node poll histories for a CachedUrlSet, up to
   * a maximum number.
   * @param cus the cached url set used to identify the top node
   * @param maxNumber the maximum number to fetch
   * @return an Iterator of PollHistory objects
   */
  public Iterator getNodeHistories(CachedUrlSet cus, int maxNumber);

  /**
   * Returns an iterator of the node poll histories for a CachedUrlSet since a
   * specific start time.
   * @param cus the cached url set used to identify the top node
   * @param since histories after this time (in ms)
   * @return an Iterator of PollHistory objects
   */
  public Iterator getNodeHistoriesSince(CachedUrlSet cus, Deadline since);

  /**
   * Returns the estimated time it will take to walk a given
   * {@link ArchivalUnit}.  This can be used to finetune the tree walk
   * parameter settings in the Configuration.
   * @param au the ArchivalUnit to treewalk
   * @return estimated time in ms
   */
  public long getEstimatedTreeWalkDuration(ArchivalUnit au);

}
