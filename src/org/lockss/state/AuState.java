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

import org.lockss.plugin.ArchivalUnit;
import org.lockss.util.TimeBase;

/**
 * AuState contains the state information for an au.
 */
public class AuState {
  protected ArchivalUnit au;
  protected long lastCrawlTime;
  protected long lastTopLevelPoll;
  protected long lastTreeWalk;

  protected AuState(ArchivalUnit au, long lastCrawlTime, long lastTopLevelPoll,
                    long lastTreeWalk) {
    this.au = au;
    this.lastCrawlTime = lastCrawlTime;
    this.lastTopLevelPoll = lastTopLevelPoll;
    this.lastTreeWalk = lastTreeWalk;
  }

  /**
   * Returns the au
   * @return the au
   */
  public ArchivalUnit getArchivalUnit() {
    return au;
  }

  /**
   * Returns the last new content crawl time of the au.
   * @return the last crawl time in ms
   */
  public long getLastCrawlTime() {
    return lastCrawlTime;
  }

  /**
   * Returns the last top level poll time for the au.
   * @return the last poll time in ms
   */
  public long getLastTopLevelPollTime() {
    return lastTopLevelPoll;
  }

  /**
   * Returns the last treewalk time for the au.
   * @return the last treewalk time in ms
   */
  public long getLastTreeWalkTime() {
    return lastTreeWalk;
  }

  /**
   * Sets the last crawl time to the current time.
   */
  void newCrawlFinished() {
    lastCrawlTime = TimeBase.nowMs();
  }

  /**
   * Sets the last poll time to the current time.
   */
  void newPollFinished() {
    lastTopLevelPoll = TimeBase.nowMs();
  }

  /**
   * Sets the last treewalk time to the current time.
   */
  void treeWalkFinished() {
    lastTreeWalk = TimeBase.nowMs();
  }


  public String toString() {
    StringBuffer sb = new StringBuffer();
    sb.append("[AuState: ");
    sb.append("lastCrawlTime=");
    sb.append(lastCrawlTime);
    sb.append("]");
    return sb.toString();
  }
}
