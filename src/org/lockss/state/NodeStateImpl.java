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
import org.lockss.plugin.CachedUrlSet;

/**
 * NodeState contains the current state information for a node, as well as the
 * poll histories.
 */
public class NodeStateImpl implements NodeState {
  protected CachedUrlSet cus;
  protected CrawlState crawlState;
  protected List polls;
  protected List pollHistories = null;
  protected HistoryRepository repository;

  protected NodeStateImpl(CachedUrlSet cus, CrawlState crawlState, List polls,
                HistoryRepository repository) {
    this.cus = cus;
    this.crawlState = crawlState;
    this.polls = polls;
    this.repository = repository;
  }

  public CachedUrlSet getCachedUrlSet() {
    return cus;
  }

  public CrawlState getCrawlState() {
    return crawlState;
  }

  public Iterator getActivePolls() {
    return Collections.unmodifiableList(polls).iterator();
  }

  public Iterator getPollHistories() {
    if (pollHistories==null) {
      repository.loadPollHistories(this);
    }
    return Collections.unmodifiableList(pollHistories).iterator();
  }

  public boolean isInternalNode() {
    return cus.flatSetIterator().hasNext();
  }

  protected void addPollState(PollState new_poll) {
    polls.add(new_poll);
  }

  protected void closeActivePoll(PollHistory finished_poll) {
    if (pollHistories==null) {
      repository.loadPollHistories(this);
    }
    pollHistories.add(finished_poll);
    polls.remove(finished_poll);
  }

  protected void setPollHistoryBeanList(List new_histories) {
    pollHistories = new ArrayList(new_histories.size());
    Iterator beanIter = new_histories.iterator();
    while (beanIter.hasNext()) {
      PollHistoryBean bean = (PollHistoryBean)beanIter.next();
      pollHistories.add(bean.getPollHistory());
    }
  }

  protected List getPollHistoryBeanList() {
    if (pollHistories==null) {
      return Collections.EMPTY_LIST;
    }
    List histBeans = new ArrayList(pollHistories.size());
    Iterator histIter = pollHistories.iterator();
    while (histIter.hasNext()) {
      PollHistory history = (PollHistory)histIter.next();
      histBeans.add(new PollHistoryBean(history));
    }
    return histBeans;
  }

}