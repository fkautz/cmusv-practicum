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

/**
 * AuStateBean is a settable version of AuState used purely for
 * marshalling purposes.
 */
public class AuStateBean extends AuState {
  public AuStateBean() {
    super(null, -1, -1, -1, null);
  }

  AuStateBean(AuState auState) {
    super(auState.au, auState.lastCrawlTime, auState.lastTopLevelPoll,
          auState.lastTreeWalk, null);
  }

  /**
   * Sets the archival unit
   * @param au the au
   */
  void setArchivalUnit(ArchivalUnit au) {
    this.au = au;
  }

  /**
   * Sets the last crawl time to a new value.
   * @param newCrawlTime in ms
   */
  public void setLastCrawlTime(long newCrawlTime) {
    lastCrawlTime = newCrawlTime;
  }

  /**
   * Sets the last top level poll time to a new value.
   * @param newPollTime in ms
   */
  public void setLastTopLevelPollTime(long newPollTime) {
    lastTopLevelPoll = newPollTime;
  }

  /**
   * Sets the last treewalk time to a new value.
   * @param newTreeWalkTime in ms
   */
  public void setLastTreeWalkTime(long newTreeWalkTime) {
    lastTreeWalk = newTreeWalkTime;
  }

}