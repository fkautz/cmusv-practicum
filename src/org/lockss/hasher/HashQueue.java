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

// todo
// locking needed on currently running req?  what if dequeued by remove()
//  while it's running.  Can't happen?

package org.lockss.hasher;
import java.io.*;
import java.util.*;
import java.text.*;
import java.math.*;
import java.security.MessageDigest;
import org.lockss.daemon.*;
import org.lockss.daemon.status.*;
import org.lockss.util.*;
import org.lockss.plugin.*;

class HashQueue implements Serializable {
  static final String PREFIX = Configuration.PREFIX + "hasher.";
  static final String PARAM_PRIORITY = PREFIX + "priority";
  static final String PARAM_STEP_BYTES = PREFIX + "stepBytes";
  static final String PARAM_NUM_STEPS = PREFIX + "numSteps";
  static final String PARAM_COMPLETED_MAX = PREFIX + "historySize";

  protected static Logger log = Logger.getLogger("HashQueue");

  private LinkedList qlist = new LinkedList(); // the queue
  // last n completed requests
  private HistoryList completed = new HistoryList(50);
  private HashThread hashThread;
  private BinarySemaphore sem = new BinarySemaphore();
  private int hashPriority = -1;
  private int hashStepBytes = 10000;
  private int hashNumSteps = 10;

  private int schedCtr = 0;
  private int finishCtr = 0;
  private BigInteger totalBytesHashed = BigInteger.valueOf(0);
  private long totalTime = 0;

  HashQueue() {
  }

  private synchronized List getQlistSnapshot() {
    return new ArrayList(qlist);
  }

  List getCompletedSnapshot() {
    synchronized (completed) {
      return new ArrayList(completed);
    }
  }

  // Return head of queue or null if empty
  synchronized Request head() {
    return qlist.isEmpty() ? null : (Request)qlist.getFirst();
  }

  // scan queue, removing and notifying hashes that have finshed or errored
  // or passed their deadline.
  void removeCompleted() {
    List done = findAndRemoveCompleted();
    doCallbacks(done);
  }

  synchronized List findAndRemoveCompleted() {
    List done = new ArrayList();
    for (ListIterator iter = qlist.listIterator(); iter.hasNext();) {
      Request req = (Request)iter.next();
      if (req.e != null) {
	removeAndNotify(req, iter, done, "Errored: ");
      } else if (req.urlsetHasher.finished()) {
	removeAndNotify(req, iter, done, "Finished: ");
      } else if (req.deadline.expired()) {
	req.e = new HashService.Timeout("hash not finished before deadline");
	removeAndNotify(req, iter, done, "Expired: ");
      }
    }
    return done;
  }

  private void removeAndNotify(Request req, Iterator iter, List done,
			       String msg) {
    req.finish = ++finishCtr;
    if (log.isDebug()) {
      log.debug(msg + ((req.e != null) ? (req.e + ": ") : "") + req);
    }
    iter.remove();
    req.urlset.storeActualHashDuration(req.timeUsed, req.e);
    done.add(req);
    synchronized (completed) {
      completed.add(req);
    }
  }

  // separated out so callbacks are run outside of synchronized block,
  // and so can put in another thread if necessary.  
  void doCallbacks(List list) {
    for (Iterator iter = list.iterator(); iter.hasNext();) {
      Request req = (Request)iter.next();
      try {
	req.callback.hashingFinished(req.urlset, req.cookie,
				     req.hasher, req.e);
      } catch (Exception e) {
	log.error("Hash callback threw", e);
      }
      // completed list for status only, don't hold on to caller's objects
      req.hasher = null;
      req.callback = null;
      req.cookie = null;
      req.urlsetHasher = null;
    }
  }

  // Insert request in queue iff it can finish in time and won't prevent
  // any that are already queued from finishing in time.
  synchronized boolean insert(Request req) {
    long totalDuration = 0;
    long durationUntilNewReq = -1;
    int pos = 0;
    long now = TimeBase.nowMs();
    if (log.isDebug()) log.debug("Insert: " + req);
    for (ListIterator iter = qlist.listIterator(); iter.hasNext();) {
      Request qreq = (Request)iter.next();
      if (req.runBefore(qreq)) {
	// New req goes here.  Remember duration so far, then add new one
	durationUntilNewReq = totalDuration;
	totalDuration += req.curEst();
	// Now check that all that follow could still finish in time
	// Requires backing up so will start with one we just looked at
	iter.previous();
	while (iter.hasNext()) {
	  qreq = (Request)iter.next();
	  if (qreq.overrun()) {
	    // Don't let overrunners prevent others from getting into queue.
	    // (Their curEst() is zero so they wouldn't affect the
	    //  totalDuration, but their deadline might precede the
	    //  new request's deadline, so might now be unachievable.)
	    break;
	  }
	  totalDuration += qreq.curEst();
	  if (now + totalDuration > qreq.deadline.getExpirationTime()) {
	    return false;
	  }
	}
      } else {
	pos++;
	totalDuration += qreq.curEst();
      }
    }
    // check that new req can finish in time
    if (durationUntilNewReq < 0) {
      // new req will be at end
      durationUntilNewReq = totalDuration;
    }
    if ((now + durationUntilNewReq + req.curEst())
	> req.deadline.getExpirationTime()) {
      return false;
    }
    req.sched = ++schedCtr;
    qlist.add(pos, req);
    return true;
  }

  // Resort the queue.  Necessary when any request's sort order might have
  // changed, such as when it becomes overrun.
  synchronized void reschedule() {
    Collections.sort(qlist);
  }

  boolean scheduleReq(Request req) {
    if (!insert(req)) {
      log.debug("Can't schedule hash");
      return false;
    }
    ensureQRunner();
    sem.give();
    log.debug("Scheduled hash:" +req);
    return true;
  }

  void init() {
    registerConfigCallback();
  }

  // Register config callback
  private void registerConfigCallback() {
    Configuration.registerConfigurationCallback(new Configuration.Callback() {
	public void configurationChanged(Configuration oldConfig,
					 Configuration newConfig,
					 Set changedKeys) {
	  setConfig(newConfig, changedKeys);
	}
      });
  }

  private void setConfig(Configuration config, Set changedKeys) {
    hashPriority = config.getInt(PARAM_PRIORITY, -1);
    hashStepBytes = config.getInt(PARAM_STEP_BYTES, 10000);
    hashNumSteps = config.getInt(PARAM_NUM_STEPS, 10);
    int cMax = config.getInt(PARAM_COMPLETED_MAX, 50);
    if (changedKeys.contains(PARAM_COMPLETED_MAX) ) {
      synchronized (completed) {
	completed.setMax(config.getInt(PARAM_COMPLETED_MAX, 50));
      }
    }
  }

  // Request - hash queue element.
  static class Request implements Serializable, Comparable {
    CachedUrlSet urlset;
    MessageDigest hasher;
    Deadline deadline;
    HashService.Callback callback;
    Object cookie;
    CachedUrlSetHasher urlsetHasher;
    String type;
    int sched;
    int finish;
    long origEst;
    long timeUsed = 0;
    long bytesHashed = 0;
    Exception e;
    boolean firstOverrun;

    Request(CachedUrlSet urlset,
	    MessageDigest hasher,
	    Deadline deadline,
	    HashService.Callback callback,
	    Object cookie,
	    CachedUrlSetHasher urlsetHasher,
	    long estimatedDuration,
	    String type) {
      this.urlset = urlset;
      this.hasher = hasher;
      this.deadline = deadline;
      this.callback = callback;
      this.cookie = cookie;
      this.urlsetHasher = urlsetHasher;
      this.origEst = estimatedDuration;
      this.type = type;
    }

    long curEst() {
      long t = origEst - timeUsed;
      return (t > 0 ? t : 0);
    }

    boolean finished() {
      return (e != null) || urlsetHasher.finished();
    }

    boolean overrun() {
      return timeUsed > origEst;
    }

    boolean runBefore(Request other) {
      return compareTo(other) < 0;
    }

    // tk - should this take into account other factors, such as
    // giving priority to requests with the least time remaining?
    public int compareTo(Request other) {
      boolean over1 = overrun();
      boolean over2 = other.overrun();
      if (over1 && !over2) return 1;
      if (!over1 && over2) return -1;
      if (deadline.before(other.deadline)) return -1;
      if (other.deadline.before(deadline)) return 1;
      return 0;
    }

    public int compareTo(Object o) {
      return compareTo((Request)o);
    }

  static final DateFormat dfDeadline = new SimpleDateFormat("HH:mm:ss");

    public String toString() {
      StringBuffer sb = new StringBuffer();
      sb.append("[HQ.Req:");
      sb.append(urlset);
      sb.append(' ');
      if (cookie instanceof String) {
	sb.append("\"");
	sb.append(cookie);
	sb.append("\"");
      }
      sb.append(" ");
      sb.append(origEst);
      sb.append("ms by ");
      sb.append(deadline);
//        sb.append(dfDeadline.format(deadline.getExpiration()));
      sb.append("]");
      return sb.toString();
    }
  }

  public void stop() {
    if (hashThread != null) {
      log.info("Stopping Q runner");
      hashThread.stopQRunner();
      hashThread = null;
    }
  }

  // tk add watchdog
  void ensureQRunner() {
    if (hashThread == null) {
      log.info("Starting Q runner");
      hashThread = new HashThread("HashQ");
      hashThread.start();
    }
  }

  void runNSteps(Request req, int nsteps, int nbytes, Boolean goOn) {
    CachedUrlSetHasher ush = req.urlsetHasher;
    req.firstOverrun = false;
    Deadline overrunDeadline = null;
    if (! req.overrun()) {
      // watch for overrun only if it hasn't overrun yet
      overrunDeadline = Deadline.in(req.curEst());
    }
    long startTime = TimeBase.nowMs();
    long bytesHashed = 0;
    try {
      // repeat up to nsteps steps, while goOn is true,
      // the request we're working on is still at the head of the queue,
      // and it isn't finished
      for (int cnt = nsteps;
	   cnt > 0 && goOn.booleanValue() && req == head() && !req.finished();
	   cnt--) {

	if (log.isDebug()) log.debug("hashStep(" + nbytes + "): " + req);

	bytesHashed += ush.hashStep(nbytes);

	// break if it's newly overrun
	if (!ush.finished() &&
	    overrunDeadline != null && overrunDeadline.expired()) {
	  req.firstOverrun = true;
	  if (log.isDebug()) log.debug("Overrun: " + req);
	  break;
	}
      }
      if (!req.finished() && req.deadline.expired()) {
	if (log.isDebug()) log.debug("Expired: " + req);
	throw
	  new HashService.Timeout("hash not finished before deadline");
      }
    } catch (Exception e) {
      // tk - should this catch all Throwable?
      req.e = e;
    }
    long timeDelta = TimeBase.nowMs() - startTime;
    req.timeUsed += timeDelta;
    req.bytesHashed += bytesHashed;
    totalBytesHashed = totalBytesHashed.add(BigInteger.valueOf(bytesHashed));
    totalTime += timeDelta;
  }

  // Run up to n steps of the request at the head of the queue, and call
  // its callback if it's done.
  // Return true if we did any work, false if no requests on queue.
  boolean runAndNotify(int nsteps, int nbytes, Boolean goOn) {
    Request req = head();
    if (req == null) {
      log.debug("runAndNotify no work");
      return false;
    }
    runNSteps(req, nsteps, nbytes, goOn);
    if (req.firstOverrun) {
      reschedule();
    }
    // need to do this more often than just when one finishes, so will
    // notice those that expired.  Better way than every time?
    if (true || req.finished()) {
      removeCompleted();
    }
    return true;
  }

  // Hash thread
  private class HashThread extends Thread {
    private Boolean goOn =  Boolean.FALSE;

    private HashThread(String name) {
      super(name);
    }

    public void run() {
      if (hashPriority > 0) {
	Thread.currentThread().setPriority(hashPriority);
      }
      goOn = Boolean.TRUE;

      try {
	while (goOn.booleanValue()) {
	  if (!runAndNotify(hashNumSteps, hashStepBytes, goOn)) {
	    sem.take(Deadline.in(Constants.MINUTE));
	  }
	}
      } catch (InterruptedException e) {
 	// no action - expected when stopping
      } catch (Exception e) {
	log.error("Unexpected exception caught in hash thread", e);
      } finally {
	hashThread = null;
      }
    }

    private void stopQRunner() {
      goOn = Boolean.FALSE;
      this.interrupt();
    }
  }

  StatusAccessor getStatusAccessor() {
    return new Status();
  }

  private static final List statusSortRules =
    ListUtil.list(new StatusTable.SortRule("state", true),
		  new StatusTable.SortRule("sort", true));

  static final String FOOT_IN = "Order in which requests were made.";

  static final String FOOT_OVER = "Red indicates overrun.";

  static final String FOOT_TITLE =
    "Pending requests are first in table, in the order they will be executed."+
    "  Completed requests follow, in the order they were completed " +
    "(most recent first).";

  private static final List statusColDescs =

    ListUtil.list(
		  new ColumnDescriptor("sched", "In",
				       ColumnDescriptor.TYPE_INT, FOOT_IN),
// 		  new ColumnDescriptor("finish", "Out",
// 				       ColumnDescriptor.TYPE_INT),
		  new ColumnDescriptor("state", "State",
				       ColumnDescriptor.TYPE_STRING,
				       FOOT_OVER),
		  new ColumnDescriptor("au", "Volume",
				       ColumnDescriptor.TYPE_STRING),
		  new ColumnDescriptor("type", "Type",
				       ColumnDescriptor.TYPE_STRING),
		  new ColumnDescriptor("cus", "Cached Url Set",
				       ColumnDescriptor.TYPE_STRING),
		  new ColumnDescriptor("deadline", "Deadline",
				       ColumnDescriptor.TYPE_DATE),
		  new ColumnDescriptor("estimate", "Estimated",
				       ColumnDescriptor.TYPE_TIME_INTERVAL),
		  new ColumnDescriptor("timeused", "Used",
				       ColumnDescriptor.TYPE_TIME_INTERVAL),
		  new ColumnDescriptor("bytesHashed", "Bytes<br>Hashed",
				       ColumnDescriptor.TYPE_INT)
		  );
		    

  private class Status implements StatusAccessor {
    public void populateTable(StatusTable table) {
      String key = table.getKey();
      table.setTitle("Hash Queue");
      table.setTitleFootnote(FOOT_TITLE);
      table.setColumnDescriptors(statusColDescs);
      table.setDefaultSortRules(statusSortRules);
      table.setRows(getRows(key));
      table.setSummaryInfo(getSummaryInfo(key));
    }

    public boolean requiresKey() {
      return false;
    }

    private List getRows(String key) {
      List table = new ArrayList();
      int ix = 0;
      for (ListIterator iter = getQlistSnapshot().listIterator();
	   iter.hasNext();) {
	table.add(makeRow((Request)iter.next(), false, ix));
      }
      for (ListIterator iter = getCompletedSnapshot().listIterator();
	   iter.hasNext();) {
	table.add(makeRow((Request)iter.next(), true, 0));
      }
      return table;
    }

    private Map makeRow(Request req, boolean done, int qpos) {
      Map row = new HashMap();
      ReqState state = (done ? REQ_STATE_DONE :
		      (req == head()) ? REQ_STATE_DONE : REQ_STATE_WAIT);
      row.put("sort", new Integer(done ? -req.finish : qpos));
      row.put("sched", new Integer(req.sched));
//       row.put("finish", new Integer(req.finish));
      row.put("state", getState(req, done));
      row.put("au", req.urlset.getArchivalUnit().getName());
      row.put("cus", req.urlset.getSpec());
      row.put("type", req.type);
      row.put("deadline", req.deadline.getExpiration());
      row.put("estimate", new Long(req.origEst));
      row.put("timeused", new Long(req.timeUsed));
      row.put("bytesHashed", new Long(req.bytesHashed));
      return row;
    }

    private ReqState getState(Request req, boolean done) {
      if (req.overrun()) {
	return (done ? REQ_STATE_DONE_O :
		(req == head()) ? REQ_STATE_RUN_O : REQ_STATE_WAIT_O);
      } else {
	return (done ? REQ_STATE_DONE :
		(req == head()) ? REQ_STATE_RUN : REQ_STATE_WAIT);
      }
    }

    private List getSummaryInfo(String key) {
      List res = new ArrayList();
      res.add(new StatusTable.SummaryInfo("Total bytes hashed",
					  ColumnDescriptor.TYPE_INT,
					  totalBytesHashed));
      res.add(new StatusTable.SummaryInfo("Total hash time",
					  ColumnDescriptor.TYPE_TIME_INTERVAL,
					  new Long(totalTime)));
      if (totalTime != 0) {
	long bpms =
	  totalBytesHashed.divide(BigInteger.valueOf(totalTime)).intValue();
	if (bpms < (100 * Constants.SECOND)) {
	  res.add(new StatusTable.SummaryInfo("Bytes/ms",
					      ColumnDescriptor.TYPE_INT,
					      new Long(bpms)));
	} else {
	  res.add(new 
		  StatusTable.SummaryInfo("Bytes/sec",
					  ColumnDescriptor.TYPE_INT,
					  new Long(bpms / Constants.SECOND)));
	}
      }
      return res;
    }

  }

  static class ReqState implements Comparable {
    String name;
    int order;

    ReqState(String name, int order) {
      this.name = name;
      this.order = order;
    }

    ReqState(String name, int order, boolean red) {
      this(name, order);
      if (red) {
	this.name = "<font color=red>" + this.name + "</font>";
      }
    }

    public int compareTo(Object o) {
      return order - ((ReqState)o).order;
    }
    public String toString() {
      return name;
    }
  }
  static final ReqState REQ_STATE_RUN = new ReqState("Run", 1);
  static final ReqState REQ_STATE_WAIT = new ReqState("Wait", 2);
  static final ReqState REQ_STATE_DONE = new ReqState("Done", 3);
  static final ReqState REQ_STATE_RUN_O = new ReqState("Run", 1, true);
  static final ReqState REQ_STATE_WAIT_O = new ReqState("Wait", 2, true);
  static final ReqState REQ_STATE_DONE_O = new ReqState("Done", 3, true);
    
}
