/*
 * $Id$
 */

/*
 Copyright (c) 2000-2002 Board of Trustees of Leland Stanford Jr. University,
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

import java.util.*;
import java.io.*;
import java.net.*;

import org.lockss.daemon.*;
import org.lockss.hasher.HashService;
import org.lockss.protocol.LcapComm;
import org.lockss.plugin.simulated.*;
import org.lockss.test.*;
import org.lockss.protocol.*;
import org.lockss.poller.*;
import org.lockss.util.*;
import org.lockss.crawler.*;
import org.lockss.repository.*;
import org.lockss.app.*;
import org.lockss.plugin.*;
import org.lockss.state.*;

public class RunDaemon
    extends LockssDaemon {
  private static final String DEFAULT_DIR_PATH = "./";

  static final String PARAM_CACHE_LOCATION =
      LockssRepositoryImpl.PARAM_CACHE_LOCATION;
  static final String PARAM_CALL_POLL = Configuration.PREFIX + "test.poll";
  static final String PARAM_RUN_TREEWALK = Configuration.PREFIX + "test.treewalk";
  static final String PARAM_TREEWALK_AUID = Configuration.PREFIX + "treewalk.auId";
  static final String PARAM_POLL_TYPE = Configuration.PREFIX + "poll.type";
  static final String PARAM_PS_PLUGINID = Configuration.PREFIX + "pollspec.pluginId";
  static final String PARAM_PS_AUID = Configuration.PREFIX + "pollspec.auId";
  static final String PARAM_PS_URL = Configuration.PREFIX + "pollspec.url";
  static final String PARAM_PS_LWRBND = Configuration.PREFIX + "pollspec.lwrBound";
  static final String PARAM_PS_UPRBND = Configuration.PREFIX + "pollspec.uprBound";

  private static Logger log = Logger.getLogger("RunDaemon");

  public static void main(String argv[]) {
    Vector urls = new Vector();
    for (int i = 0; i < argv.length; i++) {
      urls.add(argv[i]);
    }
    try {
      RunDaemon daemon = new RunDaemon(urls);
      daemon.runDaemon();
    }
    catch (Throwable e) {
      System.err.println("Exception thrown in main loop:");
      e.printStackTrace();
    }
  }

  protected RunDaemon(List propUrls) {
    super(propUrls);
  }

  public void runDaemon() throws Exception {
    super.runDaemon();

    boolean testPoll = Configuration.getBooleanParam(PARAM_CALL_POLL,
        false);

    boolean testTreeWalk = Configuration.getBooleanParam(PARAM_RUN_TREEWALK,
        false);

    if(testTreeWalk) {
      runTreeWalk();
    }

    if (testPoll) {
      callPoll();
    }
  }

  private void runTreeWalk() {
    ArchivalUnit au;
    String auId = Configuration.getParam(PARAM_TREEWALK_AUID);
    if(auId != null) {
      au = getPluginManager().findArchivalUnit(auId);
      startWalk(au);
    }
    else {
      Iterator iter = getPluginManager().getAllAUs().iterator();
      while(iter.hasNext()) {
        au = (ArchivalUnit) iter.next();
        startWalk(au);
      }
    }

  }

  private void startWalk(ArchivalUnit au) {
    NodeManager nodeMgr = getNodeManager(au);
    nodeMgr.startTreeWalk();
  }

  private void callPoll() {
    int poll_type = Configuration.getIntParam(PARAM_POLL_TYPE,
                                              LcapMessage.CONTENT_POLL_REQ);
    String pluginId = Configuration.getParam(PARAM_PS_PLUGINID);
    String auId = Configuration.getParam(PARAM_PS_AUID);
    String url = Configuration.getParam(PARAM_PS_URL, "LOCKSSAU:");
    String lwrBound = Configuration.getParam(PARAM_PS_LWRBND);
    String uprBound = Configuration.getParam(PARAM_PS_UPRBND);

    PollSpec spec = new PollSpec(pluginId, auId, url,lwrBound,uprBound, null);

    CachedUrlSet cus = getPluginManager().findCachedUrlSet(spec);
    try {
      Thread.currentThread().sleep(1000);
      getPollManager().requestPoll(poll_type, new PollSpec(cus));
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }
}