/*
 * $Id$
 */

/*

Copyright (c) 2000-2003 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.test;

import java.util.Collection;
import org.lockss.util.Deadline;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.daemon.Crawler;

public class MockCrawler extends NullCrawler {
  ArchivalUnit au;
  Collection urls;
  boolean followLinks;
  boolean doCrawlCalled = false;
  Deadline deadline = null;
  boolean crawlSuccessful = true;
  int type = -1;
  long startTime = -1;
  long endTime = -1;
  long numFetched = -1;
  long numParsed = -1;


  Crawler.Status status = null;

  boolean wasAborted = false;


  public void abortCrawl() {
    wasAborted = true;
  }

  public boolean wasAborted() {
    return wasAborted;
  }

  public void setCrawlSuccessful(boolean crawlSuccessful) {
    this.crawlSuccessful = crawlSuccessful;
  }

  public boolean doCrawl(Deadline deadline) {
    doCrawlCalled = true;
    this.deadline = deadline;
    return crawlSuccessful;
  }

  public Deadline getDeadline() {
    return deadline;
  }

  public boolean doCrawlCalled() {
    return doCrawlCalled;
  }

  public ArchivalUnit getAu() {
    return au;
  }

  public void setAu(ArchivalUnit au) {
    this.au = au;
  }

  public void setUrls(Collection urls) {
    this.urls = urls;
  }

  public void setFollowLinks(boolean followLinks) {
    this.followLinks = followLinks;
  }

  public void setType(int type) {
    this.type = type;
  }

  public int getType() {
    return type;
  }

  public Collection getStartUrls() {
    return urls;
  }

//   public void setStartTime(long time) {
//     startTime = time;
//   }

//   public void setEndTime(long time) {
//     endTime = time;
//   }

//   public void setNumFetched(long num) {
//     numFetched = num;
//   }

//   public void setNumParsed(long num) {
//     numParsed = num;
//   }

//   public long getStartTime() {
//     return startTime;
//   }

//   public long getEndTime() {
//     return endTime;
//   }

//   public long getNumFetched() {
//     return numFetched;
//   }

//   public long getNumParsed() {
//     return numParsed;
//   }

  public void setStatus(Crawler.Status status) {
    this.status = status;
  }

  public Crawler.Status getStatus() {
    if (status == null) {
      status = new MockCrawlStatus();
    }
    return status;
  }

}
