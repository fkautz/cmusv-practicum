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
import org.lockss.daemon.CachedUrlSet;
import org.lockss.test.LockssTestCase;
import org.lockss.util.CollectionUtil;

public class TestNodeStateImpl extends LockssTestCase {
  private NodeStateImpl state;
  private List polls;

  public TestNodeStateImpl(String msg) {
    super(msg);
  }

  public void setUp() throws Exception {
    polls = new ArrayList(3);
    polls.add(new PollState(1, "none1", 1, 0, null));
    polls.add(new PollState(2, "none2", 1, 0, null));
    polls.add(new PollState(3, "none3", 1, 0, null));
    state = new NodeStateImpl(null, null, polls, null);
  }

  public void testActivePollImmutability() {
    Iterator pollIter = state.getActivePolls();
    try {
      pollIter.remove();
      fail("Iterator should be immutable.");
    } catch (Exception e) { }
  }

  public void testGetPollHistories() {

  }

  public void testGetActivePolls() {
    Iterator expectedIter = polls.iterator();

    Iterator pollIter = state.getActivePolls();
    assertTrue(CollectionUtil.isIsomorphic(expectedIter, pollIter));
  }

  public void testAddPollState() {
    PollState state4 = new PollState(4, "none4", 1, 0, null);
    polls.add(state4);
    state.addPollState(state4);
    Iterator expectedIter = polls.iterator();

    Iterator pollIter = state.getActivePolls();
    assertTrue(CollectionUtil.isIsomorphic(expectedIter, pollIter));
  }

  public void testCloseActivePoll() {

  }

  public static void main(String[] argv) {
    String[] testCaseList = {TestNodeStateImpl.class.getName()};
    junit.swingui.TestRunner.main(testCaseList);
  }

}