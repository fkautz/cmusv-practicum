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

import org.lockss.test.LockssTestCase;
import org.lockss.util.TimeBase;

public class TestAuStateImpl extends LockssTestCase {
  public TestAuStateImpl(String msg) {
    super(msg);
  }

  public void tearDown() throws Exception {
    TimeBase.setReal();
    super.tearDown();
  }

  public void testCrawlFinished() {
    AuState auState = new AuState(null, 123, -1, -1);
    assertEquals(123, auState.getLastCrawlTime());

    TimeBase.setSimulated(TimeBase.nowMs());
    auState.newCrawlFinished();
    assertEquals(TimeBase.nowMs(), auState.getLastCrawlTime());
  }

  public void testPollFinished() {
    AuState auState = new AuState(null, -1, 123, -1);
    assertEquals(123, auState.getLastTopLevelPollTime());

    TimeBase.setSimulated(TimeBase.nowMs());
    auState.newPollFinished();
    assertEquals(TimeBase.nowMs(), auState.getLastTopLevelPollTime());
  }

  public void testTreeWalkFinished() {
    AuState auState = new AuState(null, -1, -1, 123);
    assertEquals(123, auState.getLastTreeWalkTime());

    TimeBase.setSimulated(TimeBase.nowMs());
    auState.treeWalkFinished();
    assertEquals(TimeBase.nowMs(), auState.getLastTreeWalkTime());
  }


  public static void main(String[] argv) {
    String[] testCaseList = { TestAuStateImpl.class.getName()};
    junit.swingui.TestRunner.main(testCaseList);
  }

}