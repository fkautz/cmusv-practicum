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

import org.lockss.test.*;
import org.lockss.util.TimeBase;

public class TestAuStateImpl extends LockssTestCase {
  MockHistoryRepository historyRepo;
  MockArchivalUnit mau;

  public void setUp() throws Exception {
    super.setUp();

    historyRepo = new MockHistoryRepository();
    mau = new MockArchivalUnit();
  }


  public void tearDown() throws Exception {
    TimeBase.setReal();
    super.tearDown();
  }

  public void testCrawlFinished() {
    AuState auState = new AuState(mau, 123, -1, -1, historyRepo);
    assertEquals(123, auState.getLastCrawlTime());
    assertNull(historyRepo.storedAus.get(auState.getArchivalUnit()));

    TimeBase.setSimulated(456);
    auState.newCrawlFinished();
    assertEquals(456, auState.getLastCrawlTime());
    assertNotNull(historyRepo.storedAus.get(auState.getArchivalUnit()));
  }

  public void testPollFinished() {
    AuState auState = new AuState(mau, -1, 123, -1, historyRepo);
    assertEquals(123, auState.getLastTopLevelPollTime());
    assertNull(historyRepo.storedAus.get(auState.getArchivalUnit()));

    TimeBase.setSimulated(456);
    auState.newPollFinished();
    assertEquals(456, auState.getLastTopLevelPollTime());
    assertNotNull(historyRepo.storedAus.get(auState.getArchivalUnit()));
  }

  public void testTreeWalkFinished() {
    AuState auState = new AuState(mau, -1, -1, 123, historyRepo);
    assertEquals(123, auState.getLastTreeWalkTime());

    TimeBase.setSimulated(456);
    auState.setLastTreeWalkTime();
    assertEquals(456, auState.getLastTreeWalkTime());
  }

  public static void main(String[] argv) {
    String[] testCaseList = { TestAuStateImpl.class.getName()};
    junit.swingui.TestRunner.main(testCaseList);
  }

}