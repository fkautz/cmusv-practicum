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
import gnu.regexp.*;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import org.lockss.util.*;
import org.lockss.daemon.*;
import org.lockss.poller.*;
import org.lockss.crawler.CrawlManager;
import org.lockss.plugin.PluginManager;
import org.lockss.protocol.LcapMessage;
import org.lockss.protocol.IdentityManager;
import org.lockss.repository.LockssRepository;
import org.lockss.repository.LockssRepositoryImpl;
import org.lockss.app.*;

/**
 * Implementation of the NodeManager.
 */
public class NodeManagerImpl implements NodeManager, LockssManager {
  private static LockssDaemon theDaemon;
  private static NodeManager theManager = null;
  private static NodeManager nodeManager = null;
  static HistoryRepository repository;
  private static HashMap auMaps = new HashMap();
  private static HashMap auEstimateMap = new HashMap();

  private ArchivalUnit managerAu;
  private AuState auState;
  private TreeMap nodeMap = new TreeMap();
  private static Logger logger = Logger.getLogger("NodeManager");

  public NodeManagerImpl() {}

  /**
   * init the plugin manager.
   * @param daemon the LockssDaemon instance
   * @throws LockssDaemonException if we already instantiated this manager
   * @see org.lockss.app.LockssManager.initService()
   */
  public void initService(LockssDaemon daemon) throws LockssDaemonException {
    if(theManager == null) {
      theDaemon = daemon;
      theManager = this;
    }
    else {
      throw new LockssDaemonException("Multiple Instantiation.");
    }
  }

  /**
   * start the plugin manager.
   * @see org.lockss.app.LockssManager.startService()
   */
  public void startService() {
    repository = theDaemon.getHistoryRepository();
  }

  /**
   * stop the plugin manager
   * @see org.lockss.app.LockssManager.stopService()
   */
  public void stopService() {
    // checkpoint here

    theManager = null;
  }

  /**
   * Factory method to retrieve NodeManager.
   * @param au the ArchivalUnit being managed
   * @return the current NodeManager
   */
  public synchronized static NodeManager getNodeManager(ArchivalUnit au) {
    nodeManager = (NodeManager)auMaps.get(au);
    if (nodeManager==null) {
      nodeManager = new NodeManagerImpl(au);
      auMaps.put(au, nodeManager);
    }
    return nodeManager;
  }

  NodeManagerImpl(ArchivalUnit au) {
    managerAu = au;
    loadStateTree();
  }

  public void updatePollResults(CachedUrlSet cus, Poll.VoteTally results) {
    NodeState state = getNodeState(cus);
    updateState(state, results);
    repository.storePollHistories(state);
  }

  public NodeState getNodeState(CachedUrlSet cus) {
    return (NodeState)nodeMap.get(cus.getPrimaryUrl());
  }

  public AuState getAuState() {
    if (auState==null) {
      auState = repository.loadAuState(managerAu);
    }
    return auState;
  }

  public Iterator getActiveCrawledNodes(CachedUrlSet cus) {
    Iterator keys = nodeMap.keySet().iterator();
    Vector stateV = new Vector();
    while (keys.hasNext()) {
      String key = (String)keys.next();
      if (cus.containsUrl(key)) {
        NodeState state = (NodeState)nodeMap.get(key);
        int status = state.getCrawlState().getStatus();
        if ((status != CrawlState.FINISHED) &&
            (status != CrawlState.NODE_DELETED)) {
          stateV.addElement(state);
        }
      }
    }
    return stateV.iterator();
  }

  public Iterator getFilteredPolledNodes(CachedUrlSet cus, int filter) {
    Iterator keys = nodeMap.keySet().iterator();
    Vector stateV = new Vector();
    while (keys.hasNext()) {
      String key = (String)keys.next();
      if (cus.containsUrl(key)) {
        NodeState state = (NodeState)nodeMap.get(key);
        Iterator polls = state.getActivePolls();
        while (polls.hasNext()) {
          PollState pollState = (PollState)polls.next();
          if ((pollState.getStatus() & filter) != 0) {
            stateV.addElement(state);
            break;
          }
        }
      }
    }
    return stateV.iterator();
  }

  public Iterator getNodeHistories(CachedUrlSet cus, int maxNumber) {
    Iterator keys = nodeMap.keySet().iterator();
    Vector historyV = new Vector();
    while (keys.hasNext()) {
      String key = (String)keys.next();
      if (cus.containsUrl(key)) {
        NodeState state = (NodeState)nodeMap.get(key);
        Iterator pollHistories = state.getPollHistories();
        while (pollHistories.hasNext()) {
          PollHistory history = (PollHistory)pollHistories.next();
          historyV.addElement(history);
          if (historyV.size() >= maxNumber) {
            return historyV.iterator();
          }
        }
      }
    }
    return historyV.iterator();
  }

  public Iterator getNodeHistoriesSince(CachedUrlSet cus, Deadline since) {
    Iterator keys = nodeMap.keySet().iterator();
    Vector historyV = new Vector();
    while (keys.hasNext()) {
      String key = (String)keys.next();
      if (cus.containsUrl(key)) {
        NodeState state = (NodeState)nodeMap.get(key);
        Iterator pollHistories = state.getPollHistories();
        while (pollHistories.hasNext()) {
          PollHistory history = (PollHistory)pollHistories.next();
          Deadline started = Deadline.at(history.getStartTime());
          if (!started.before(since)) {
            historyV.addElement(history);
          }
        }
      }
    }
    return historyV.iterator();
  }

  public long getEstimatedTreeWalkDuration() {
    Long estimateL = (Long)auEstimateMap.get(managerAu);
    if (estimateL==null) {
      // check size (node count) of tree
      int nodeCount = nodeMap.size();
      // estimate via short walk
      // this is not a fake walk; it functionally walks part of the tree
      long startTime = TimeBase.nowMs();
      Iterator nodesIt = nodeMap.values().iterator();
      String deleteSub = null;
      int NUMBER_OF_NODES_TO_TEST = 200; //XXX fix
      int numberNodesTested = 0;
      //XXX do for set time?
      for (int ii=0; ii<NUMBER_OF_NODES_TO_TEST; ii++) {
        if (!nodesIt.hasNext()) {
          break;
        }
        walkNodeState((NodeState)nodesIt.next());
        numberNodesTested++;
      }
      long elapsedTime = TimeBase.nowMs() - startTime;

      // calculate
      double nodesPerMs = ((double)elapsedTime / numberNodesTested);
      estimateL = new Long((long)(nodeCount * nodesPerMs));

      auEstimateMap.put(managerAu, estimateL);
    }
    return estimateL.longValue();
  }

  void doTreeWalk() {
    //XXX check if ok with CrawlManager
    // if CrawlManager.canTreeWalkStart(managerAu, null, null);
    long startTime = TimeBase.nowMs();
    nodeTreeWalk(nodeMap);
    long elapsedTime = TimeBase.nowMs() - startTime;
    updateEstimate(elapsedTime);
  }

  void updateEstimate(long elapsedTime) {
    if (auEstimateMap==null) {
      auEstimateMap = new HashMap();
    }
    Long estimateL = (Long)auEstimateMap.get(managerAu);
    if (estimateL==null) {
      auEstimateMap.put(managerAu, new Long(elapsedTime));
    } else {
      long newEstimate = (estimateL.longValue() + elapsedTime)/2;
      auEstimateMap.put(managerAu, new Long(newEstimate));
    }
  }

  private void nodeTreeWalk(TreeMap nodeMap) {
    Iterator nodesIt = nodeMap.values().iterator();
    while (nodesIt.hasNext()) {
      walkNodeState((NodeState)nodesIt.next());
    }
  }

  void walkNodeState(NodeState node) {
    CrawlState crawlState = node.getCrawlState();

    // at each node, check for crawl state
    switch (crawlState.getType()) {
      case CrawlState.NODE_DELETED:
        // skip node if deleted
        return;
      case CrawlState.BACKGROUND_CRAWL:
      case CrawlState.NEW_CONTENT_CRAWL:
      case CrawlState.REPAIR_CRAWL:
        if (crawlState.getStatus()==CrawlState.FINISHED) {
          //XXX has content
          // if node.getCachedUrlSet().hasContent();
          // unless some sort of poll is currently running.
          // and !node.getActivePolls().hasNext();
          // if CrawlManager.shouldCrawl()
          // then CrawlManager.scheduleBackgroundCrawl()
        }
    }
    Iterator historyIt = node.getPollHistories();
    //XXX check recent histories to see if something needs fixing
    // if latest is PollState.LOST and it's been a long time
    // and there're no active Polls
    // then callNamePoll(node.getCachedUrlSet());
    // if latest is Poll.REPAIRING and it's been a long time
    // and there're no active Polls
    // then callNamePoll(node.getCachedUrlSet());
    repository.storePollHistories(node);
  }

  private void loadStateTree() {
    // get list of aus
    // recurse through au cachedurlsets
    CachedUrlSet cus = managerAu.getAUCachedUrlSet();
    recurseLoadCachedUrlSets(cus);
  }

  private void recurseLoadCachedUrlSets(CachedUrlSet cus) {
    // add the nodeState for this cus
    addNewNodeState(cus);
    // recurse the set's children
    Iterator children = cus.flatSetIterator();
    while (children.hasNext()) {
      CachedUrlSet child = (CachedUrlSet)children.next();
      recurseLoadCachedUrlSets(child);
    }
  }

  private void addNewNodeState(CachedUrlSet cus) {
    NodeState state = new NodeStateImpl(cus, new CrawlState(-1,
        CrawlState.FINISHED, 0), new ArrayList(), repository);
    nodeMap.put(cus.getPrimaryUrl(), state);
  }

  private void updateState(NodeState state, Poll.VoteTally results) {
    PollState pollState = getPollState(state, results);
    if (pollState == null) {
      logger.error("Results updated for a non-existent poll.");
      throw new UnsupportedOperationException("Results updated for a non-existent poll.");
    }
    if (results.getErr() < 0) {
      pollState.status = mapResultsErrorToPollError(results.getErr());
      logger.info("Poll didn't finish fully.  Error code: " + pollState.status);
      return;
    }

    if (results.getType() == Poll.CONTENT_POLL) {
      handleContentPoll(pollState, results, state);
    } else if (results.getType() == Poll.NAME_POLL) {
      handleNamePoll(pollState, results, state);
    } else {
      logger.error("Updating state for invalid results type: " +
                   results.getType());
      throw new UnsupportedOperationException("Updating state for invalid results type.");
    }
  }

  void handleContentPoll(PollState pollState, Poll.VoteTally results,
                                 NodeState nodeState) {
    if (results.didWinPoll()) {
      // if agree
      if (pollState.getStatus() == PollState.RUNNING) {
        // if normal poll, we won!
        pollState.status = PollState.WON;
      } else if (pollState.getStatus() == PollState.REPAIRING) {
        // if repair poll, we're repaired
        pollState.status = PollState.REPAIRED;
      }
      closePoll(pollState, results.getDuration(), results.getPollVotes(),
                nodeState);
      updateReputations(results);
    } else {
      // if disagree
      if (pollState.getStatus() == PollState.REPAIRING) {
        // if repair poll, can't be repaired
        pollState.status = PollState.UNREPAIRABLE;
        closePoll(pollState, results.getDuration(), results.getPollVotes(),
                  nodeState);
        updateReputations(results);
      } else if (nodeState.isInternalNode()) {
        // if internal node, we need to call a name poll
        pollState.status = PollState.LOST;
        closePoll(pollState, results.getDuration(), results.getPollVotes(),
                  nodeState);
        callNamePoll(nodeState.getCachedUrlSet());
      } else {
        // if leaf node, we need to repair
        pollState.status = PollState.REPAIRING;
        try {
          markNodeForRepair(nodeState.getCachedUrlSet(), results);
          closePoll(pollState, results.getDuration(), results.getPollVotes(),
                    nodeState);
          //XXX results.suspend(nodeState.getCachedUrlSet().getPrimaryUrl());
        } catch (IOException ioe) {
          logger.error("Repair attempt failed.", ioe);
          //XXX schedule something?
          // or allow treewalk to fix?
        }
      }
    }
  }

  void handleNamePoll(PollState pollState, Poll.VoteTally results,
                              NodeState nodeState) {
    if (results.didWinPoll()) {
      // if agree
      if (results.isMyPoll()) {
        // if poll is mine
        try {
          callContentPollOnSubNodes(nodeState, results);
          pollState.status = PollState.WON;
        } catch (Exception e) {
          logger.error("Error scheduling content polls.", e);
          pollState.status = PollState.ERR_IO;
        }
      } else {
        // if poll is not mine stop - set to WON
        pollState.status = PollState.WON;
      }
      closePoll(pollState, results.getDuration(), results.getPollVotes(),
                nodeState);
    } else {
      // if disagree
      pollState.status = PollState.REPAIRING;
      Iterator masterIt = results.getCorrectEntries();
      Iterator localIt = results.getLocalEntries();
      Set localSet = createUrlSetFromCusIterator(localIt);
      ArchivalUnit au = nodeState.getCachedUrlSet().getArchivalUnit();
      // iterate through master list
      while (masterIt.hasNext()) {
        String url = (String)masterIt.next();
        // compare against my list
        if (localSet.contains(url)) {
          // removing from the set to leave only files for deletion
          localSet.remove(url);
        } else {
          // if not found locally, fetch
          try {
            CachedUrlSet newCus = au.makeCachedUrlSet(url, null);
            markNodeForRepair(newCus, results);
            //add to NodeState list
            addNewNodeState(newCus);
          } catch (Exception e) {
            logger.error("Couldn't fetch new node.", e);
            //XXX schedule something
            // or allow treewalk to fix?
          }
        }
      }
      localIt = localSet.iterator();
      while (localIt.hasNext()) {
        // for extra items - deletion
        String url = (String)localIt.next();
        try {
          CachedUrlSet oldCus = au.makeCachedUrlSet(url, null);
          deleteNode(oldCus);
          //set crawl status to DELETED
          NodeState oldState = getNodeState(oldCus);
          oldState.getCrawlState().type = CrawlState.NODE_DELETED;
        } catch (Exception e) {
          logger.error("Couldn't delete node.", e);
          //XXX schedule something
          // or allow treewalk to fix?
        }
      }
      pollState.status = PollState.REPAIRED;
      closePoll(pollState, results.getDuration(), results.getPollVotes(),
                nodeState);
    }
  }

  private void callNamePoll(CachedUrlSet cus) {
    try {
      theDaemon.getPollManager().requestPoll(cus, null,
          LcapMessage.NAME_POLL_REQ);
    } catch (IOException ioe) {
      logger.error("Couldn't make name poll request.", ioe);
      //XXX schedule something
    }
  }

  private void closePoll(PollState pollState, long duration, Collection votes,
                         NodeState nodeState) {
    PollHistory history = new PollHistory(pollState, duration, votes);
    ((NodeStateImpl)nodeState).closeActivePoll(history);
    repository.storePollHistories(nodeState);
  }

  private PollState getPollState(NodeState state, Poll.VoteTally results) {
    Iterator polls = state.getActivePolls();
    while (polls.hasNext()) {
      PollState pollState = (PollState)polls.next();
      if ((pollState.getRegExp() == results.getRegExp()) &&
          (pollState.getType() == results.getType())) {
        return pollState;
      }
    }
    return null;
  }

  static int mapResultsErrorToPollError(int resultsErr) {
    switch (resultsErr) {
      case Poll.ERR_HASHING:
        return PollState.ERR_HASHING;
      case Poll.ERR_IO:
        return PollState.ERR_IO;
      case Poll.ERR_NO_QUORUM:
        return PollState.ERR_NO_QUORUM;
      case Poll.ERR_SCHEDULE_HASH:
        return PollState.ERR_SCHEDULE_HASH;
    }
    return PollState.ERR_UNDEFINED;
  }

  private void markNodeForRepair(CachedUrlSet cus, Poll.VoteTally tally)
      throws IOException {
    //theDaemon.getPollManager().suspendPoll(tally.getPollKey());
    //CrawlManger.scheduleRepair(new URL(cus.getPrimaryUrl()),
    //                           new ContentRepairCallback(),
    //                             tally.getPollKey());
    //cus.makeUrlCacher(cus.getPrimaryUrl()).cache();
  }

  private void deleteNode(CachedUrlSet cus) throws IOException {
    // delete the node from the LockssRepository
    //XXX change to get from RunDaemon
    LockssRepository repository =
        LockssRepositoryImpl.repositoryFactory(cus.getArchivalUnit());
    repository.deleteNode(cus.getPrimaryUrl());
  }

  private void callContentPollOnSubNodes(NodeState state,
      Poll.VoteTally results) throws IOException {
    Iterator children = state.getCachedUrlSet().flatSetIterator();
    while (children.hasNext()) {
      CachedUrlSet child = (CachedUrlSet)children.next();
      theDaemon.getPollManager().requestPoll(child, null,
          LcapMessage.CONTENT_POLL_REQ);
    }
  }

  private void updateReputations(Poll.VoteTally results) {
    IdentityManager idManager = theDaemon.getIdentityManager();
    Iterator voteIt = results.getPollVotes().iterator();
    while (voteIt.hasNext()) {
      Vote vote = (Vote)voteIt.next();
      int repChange = IdentityManager.AGREE_VOTE;
      if (!vote.isAgreeVote()) {
        repChange = IdentityManager.DISAGREE_VOTE;
      }
      idManager.changeReputation(vote.getIdentity(), repChange);
    }
  }

  private Set createUrlSetFromCusIterator(Iterator cusIt) {
    Set set = new HashSet();
    while (cusIt.hasNext()) {
      String key = (String) cusIt.next();
      set.add(key);
    }
    return set;
  }

  static class ContentRepairCallback  implements CrawlManager.Callback {
    /**
     * @param success whether the repair was successful or not
     * @param cookie object used by callback to designate which repair
     * attempt this is
     */
    public void signalCrawlAttemptCompleted(boolean success, Object cookie) {
      theDaemon.getPollManager().resumePoll(success, cookie);
    }

  }

}
