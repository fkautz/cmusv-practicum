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

package org.lockss.crawler;

import java.util.*;
import org.apache.commons.collections.*;
import org.lockss.daemon.*;
import org.lockss.daemon.status.*;
import org.lockss.state.NodeState;
import org.lockss.util.*;
import org.lockss.app.*;
import org.lockss.state.*;
import org.lockss.plugin.*;
import org.lockss.plugin.base.*;

/**
 * This is the interface for the object that will sit between the crawler
 * and the rest of the world.  It mediates the different crawl types.
 */
public class CrawlManagerImpl extends BaseLockssManager
    implements CrawlManager, CrawlManager.StatusSource {

  /**
   * The expiration deadline for a new content crawl, in ms.
   */
  public static final String PARAM_NEW_CONTENT_CRAWL_EXPIRATION =
      Configuration.PREFIX + "crawler.new_content.expiration";

  /**
   * The expiration deadline for a repair crawl, in ms.
   */
  public static final String PARAM_REPAIR_CRAWL_EXPIRATION =
      Configuration.PREFIX + "crawler.repair.expiration";

  /**
   * The priority that the crawl thread should run at
   */
  public static final String PARAM_PRIORITY =
      Configuration.PREFIX + "crawler.priority";

  /**
   * ToDo:
   * 1)handle background crawls
   * 2)check for conflicting crawl types
   * 3)check crawl schedule rules
   */
  private static final String CRAWL_STATUS_TABLE_NAME = "crawl_status_table";

  private static final long DEFAULT_NEW_CONTENT_CRAWL_EXPIRATION =
    10 * Constants.DAY;
  private static final long DEFAULT_REPAIR_CRAWL_EXPIRATION =
    5 * Constants.DAY;
  private static final int DEFAULT_PRIORITY = Thread.NORM_PRIORITY-1;

  //Tracking crawls for the status info
  private MultiMap crawlHistory = new MultiHashMap();

  private long contentCrawlExpiration;
  private long repairCrawlExpiration;
  private int crawlPriority = DEFAULT_PRIORITY;
  private static Logger logger = Logger.getLogger("CrawlManager");


  /**
   * start the plugin manager.
   * @see org.lockss.app.LockssManager#startService()
   */
  public void startService() {
    super.startService();

    StatusService statusServ = theDaemon.getStatusService();
    statusServ.registerStatusAccessor(CRAWL_STATUS_TABLE_NAME,
				      new CrawlManagerStatus(this));
  }

  /**
   * stop the plugin manager
   * @see org.lockss.app.LockssManager#stopService()
   */
  public void stopService() {
    // checkpoint here
    StatusService statusServ = theDaemon.getStatusService();
    if (statusServ != null) {
      statusServ.unregisterStatusAccessor(CRAWL_STATUS_TABLE_NAME);
    }
    super.stopService();
  }

  protected void setConfig(Configuration newConfig, Configuration prevConfig,
			   Set changedKeys) {
    contentCrawlExpiration =
      newConfig.getTimeInterval(PARAM_NEW_CONTENT_CRAWL_EXPIRATION,
				DEFAULT_NEW_CONTENT_CRAWL_EXPIRATION);
    repairCrawlExpiration =
      newConfig.getTimeInterval(PARAM_REPAIR_CRAWL_EXPIRATION,
				DEFAULT_REPAIR_CRAWL_EXPIRATION);

    crawlPriority = newConfig.getInt(PARAM_PRIORITY, DEFAULT_PRIORITY);
  }

  public void startRepair(ArchivalUnit au, Collection urls,
			  CrawlManager.Callback cb, Object cookie) {
    //XXX check to make sure no other crawls are running and queue if they are
    if (au == null) {
      throw new IllegalArgumentException("Called with null AU");
    }
    if (urls == null) {
      throw new IllegalArgumentException("Called with null URL");
    }

    // check with regulator and start repair
    Map locks = getRepairLocks(au, urls);
    if (locks.size() > 0) {
      if (locks.size() < urls.size()) {
	cb = new FailingCallbackWrapper(cb);
      }
      Crawler crawler = makeCrawler(au, locks.keySet(), Crawler.REPAIR, false);
      CrawlThread crawlThread =
	new CrawlThread(crawler, Deadline.MAX, cb, cookie);
      crawlThread.start();
      crawlHistory.put(au.getAUId(), crawler);
    } else {
      logger.debug3("Repair aborted due to activity lock.");
      cb.signalCrawlAttemptCompleted(false, cookie);
    }
  }

  private Map getRepairLocks(ArchivalUnit au, Collection urlStrs) {
    Map locks = new HashMap();
    ActivityRegulator ar = theDaemon.getActivityRegulator(au);
    for (Iterator it = urlStrs.iterator(); it.hasNext();) {
      String url = (String)it.next();
      ActivityRegulator.Lock lock =
	ar.startCusActivity(ActivityRegulator.REPAIR_CRAWL,
			    createSingleNodeCachedUrlSet(au, url),
			    repairCrawlExpiration);
	if (lock != null) {
	  locks.put(url, lock);
	  logger.debug3("Locked "+url);
	} else {
	  logger.debug3("Couldn't lock "+url);
	}
    }
    return locks;
  }

  private static CachedUrlSet createSingleNodeCachedUrlSet(ArchivalUnit au,
							   String url) {
    return au.makeCachedUrlSet(new SingleNodeCachedUrlSetSpec(url));
  }

  public void startNewContentCrawl(ArchivalUnit au, CrawlManager.Callback cb,
                                   Object cookie) {
    if (au == null) {
      throw new IllegalArgumentException("Called with null AU");
    }
    ActivityRegulator.Lock lock = getNewContentLock(au);
    if (lock != null) {
      scheduleNewContentCrawl(au, cb, cookie, lock);
    } else {
      logger.debug2("Couldn't schedule new content crawl due "
		    +"to activity lock.");
      if (cb != null) {
	cb.signalCrawlAttemptCompleted(false, cookie);
      }
    }
  }

  private ActivityRegulator.Lock getNewContentLock(ArchivalUnit au) {
    ActivityRegulator ar = theDaemon.getActivityRegulator(au);
    return ar.startAuActivity(ActivityRegulator.NEW_CONTENT_CRAWL,
			      contentCrawlExpiration);
  }

  public boolean shouldRecrawl(ArchivalUnit au, NodeState ns) {
    //XXX move to AU
    return false;
  }

  private void scheduleNewContentCrawl(ArchivalUnit au,
                                       CrawlManager.Callback cb, Object cookie,
                                       ActivityRegulator.Lock lock) {
    List callBackList = new ArrayList();
    Crawler crawler = makeCrawler(au, au.getNewContentCrawlUrls(),
				  Crawler.NEW_CONTENT, true);
    CrawlThread crawlThread =
      new CrawlThread(crawler, Deadline.MAX, cb, cookie);
    crawlThread.start();
    crawlHistory.put(au.getAUId(), crawler);
  }

  private void addCrawl(Map crawlMap, ArchivalUnit au, Crawler crawler) {
    synchronized (crawlMap) {
      Collection crawlsForAu = (Collection)crawlMap.get(au);
      if (crawlsForAu == null) {
	crawlsForAu = new HashSet();
	crawlMap.put(au, crawlsForAu);
      }
      crawlsForAu.add(crawler);
    }
  }

  protected Crawler makeCrawler(ArchivalUnit au, Collection urls,
				int type, boolean followLinks) {
    return new GoslingCrawlerImpl(au, urls, type, followLinks);
  }


  public class CrawlThread extends Thread {
    private Deadline deadline;
    private List callbacks;
    private Object cookie;
    private Crawler crawler;
    private CrawlManager.Callback cb;


    private CrawlThread(Crawler crawler, Deadline deadline,
			CrawlManager.Callback cb, Object cookie) {
      super(crawler.toString());
      this.deadline = deadline;
      this.cb = cb;
      this.cookie = cookie;
      this.crawler = crawler;
    }

    public void run() {
      if (crawlPriority > 0) {
	logger.debug("Setting crawl thread priority to "+crawlPriority);
	Thread.currentThread().setPriority(crawlPriority);
      } else {
	logger.warning("Crawl thread priority less than zero, not set: "
		       +crawlPriority);
      }
      boolean crawlSuccessful = crawler.doCrawl(deadline);
      ArchivalUnit au = crawler.getAU();

      // free activity regulator so polls can resume
      ActivityRegulator ar = theDaemon.getActivityRegulator(au);

      if (crawler.getType() == Crawler.NEW_CONTENT) {
	if (crawlSuccessful) {
	  NodeManager nodeManager = theDaemon.getNodeManager(crawler.getAU());
	  nodeManager.newContentCrawlFinished();
	}
        ar.auActivityFinished(ActivityRegulator.NEW_CONTENT_CRAWL);
      } else {
	releaseRepairLocks(au, crawler.getStartUrls());
      }
      if (cb != null) {
	try {
	  cb.signalCrawlAttemptCompleted(crawlSuccessful, cookie);
	} catch (Exception e) {
	  logger.error("Callback threw", e);
	}
      }
    }
  }

  private void releaseRepairLocks(ArchivalUnit au, Collection urlStrs) {
    ActivityRegulator ar = theDaemon.getActivityRegulator(au);
    for (Iterator it = urlStrs.iterator(); it.hasNext();) {
      String curUrl = (String)it.next();
      ar.cusActivityFinished(ActivityRegulator.REPAIR_CRAWL,
			     createSingleNodeCachedUrlSet(au, curUrl));
    }
  }

  private static class FailingCallbackWrapper
    implements CrawlManager.Callback {
    CrawlManager.Callback cb;
    public FailingCallbackWrapper(CrawlManager.Callback cb) {
      this.cb = cb;
    }
    public void signalCrawlAttemptCompleted(boolean success, Object cookie) {
      cb.signalCrawlAttemptCompleted(false, cookie);
    }
  }

  //CrawlManager.StatusSource methods
  public Collection getActiveAUs() {
    return crawlHistory.keySet();
  }

  public Collection getCrawls(String auid) {
    Collection returnColl = (Collection)crawlHistory.get(auid);
    return returnColl != null ? returnColl : Collections.EMPTY_LIST;
  }
}
