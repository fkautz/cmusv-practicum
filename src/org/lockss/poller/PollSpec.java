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

package org.lockss.poller;

import java.io.*;
import java.net.*;
import java.util.*;
import org.lockss.util.*;
import org.lockss.daemon.*;
import org.lockss.plugin.*;
import org.lockss.protocol.*;
import org.lockss.app.*;

/**
 * Class implementing the concept of the set of URLs covered by a poll.
 * @author Claire Griffin
 * @version 1.0
 */

public class PollSpec {
  /**
   * A lower bound value which indicates the poll should use a
   * {@link SingleNodeCachedUrlSetSpec} instead of a
   * {@link RangeCachedUrlSetSpec}.
   */
  public static final String SINGLE_NODE_LWRBOUND = ".";

  public static final String PARAM_USE_POLL_VERSION =
    Configuration.PREFIX + "protocol.usePollVersion";

  public static final int DEFAULT_USE_POLL_VERSION = 1;
  static final String DEFAULT_PLUGIN_VERSION = "1";

  private static Logger theLog=Logger.getLogger("PollSpec");
  private static LockssRandom theRandom = new LockssRandom();
  private String auId;
  private String pluginVersion;
  private String url;
  private String uprBound = null;
  private String lwrBound = null;
  private CachedUrlSet cus = null;
  private PluginManager pluginMgr = null;
  private int pollVersion; // poll protocol version


  /**
   * Construct a PollSpec from a CachedUrlSet and an upper and lower bound
   * @param cus the CachedUrlSet
   * @param lwrBound the lower boundary
   * @param uprBound the upper boundary
   */
  public PollSpec(CachedUrlSet cus, String lwrBound, String uprBound) {
    commonSetup(cus, lwrBound, uprBound);
  }

  /**
   * Construct a PollSpec from a CachedUrlSet, an upper and lower bound
   * and an explicit poll version
   * @param cus the CachedUrlSet
   * @param lwrBound the lower boundary
   * @param uprBound the upper boundary
   * @param pollVersion the poll version to use
   */
  public PollSpec(CachedUrlSet cus,
		  String lwrBound,
		  String uprBound,
		  int pollVersion) {
    commonSetup(cus, lwrBound, uprBound, pollVersion);
  }

  /**
   * Construct a PollSpec from a CachedUrlSet
   * @param cus the CachedUrlSpec which defines the range of interest
   */
  public PollSpec(CachedUrlSet cus) {
    CachedUrlSetSpec cuss = cus.getSpec();
    if (cuss instanceof RangeCachedUrlSetSpec) {
      RangeCachedUrlSetSpec rcuss = (RangeCachedUrlSetSpec)cuss;
      commonSetup(cus, rcuss.getLowerBound(), rcuss.getUpperBound());
    } else if (cuss.isSingleNode()) {
      commonSetup(cus, SINGLE_NODE_LWRBOUND, null);
    } else {
      commonSetup(cus, null, null);
    }
  }

  /**
   * Construct a PollSpec from a LcapMessage
   * @param msg the LcapMessage which defines the range of interest
   */
  public PollSpec(LcapMessage msg) {
    auId = msg.getArchivalId();
    pluginVersion = msg.getPluginVersion();
    url = msg.getTargetUrl();
    uprBound = msg.getUprBound();
    lwrBound = msg.getLwrBound();
    pollVersion = msg.getPollVersion();
    cus = getPluginManager().findCachedUrlSet(this);
  }

  /**
   * Construct a PollSpec from explicit args
   */
  public PollSpec(String auId, String url, String lower, String upper) {
    this.auId = auId;
    this.url = url;
    uprBound = upper;
    lwrBound = lower;
    cus = getPluginManager().findCachedUrlSet(this);
  }

  /** Setup common to most constructors */
  private void commonSetup(CachedUrlSet cus,
			   String lwrBound,
			   String uprBound) {
    commonSetup(cus, lwrBound, uprBound, getDefaultPollVersion());
  }

  /** Setup common to most constructors */
  private void commonSetup(CachedUrlSet cus,
			   String lwrBound,
			   String uprBound,
			   int pollVersion) {
    this.cus = cus;
    ArchivalUnit au = cus.getArchivalUnit();
    auId = au.getAuId();
    this.pluginVersion = au.getPlugin().getVersion();
    CachedUrlSetSpec cuss = cus.getSpec();
    url = cuss.getUrl();
    this.lwrBound = lwrBound;
    this.uprBound = uprBound;
    this.pollVersion = pollVersion;
  }

  protected int getDefaultPollVersion() {
    return Configuration.getIntParam(PARAM_USE_POLL_VERSION,
				     DEFAULT_USE_POLL_VERSION);
  }

  public CachedUrlSet getCachedUrlSet() {
    return cus;
  }

  public String getAuId() {
    return auId;
  }

  public String getPluginVersion() {
    return (pluginVersion != null) ? pluginVersion : DEFAULT_PLUGIN_VERSION;
  }

  public String getUrl() {
    return url;
  }

  public String getLwrBound() {
    return lwrBound;
  }

  public String getUprBound() {
    return uprBound;
  }

  public String getRangeString() {
    if (StringUtil.equalStrings(lwrBound, SINGLE_NODE_LWRBOUND)) {
      return "single node";
    }
    if (lwrBound != null || uprBound != null) {
      String lwrDisplay = lwrBound;
      String uprDisplay = uprBound;
      if (lwrBound != null && lwrBound.startsWith("/")) {
        lwrDisplay = lwrBound.substring(1);
      }
      if (uprBound != null && uprBound.startsWith("/")) {
        uprDisplay = uprBound.substring(1);
      }
      return lwrDisplay + " - " + uprDisplay;
    }
    return null;
  }

  public int getPollVersion() {
    return pollVersion;
  }

  private PluginManager getPluginManager() {
    if (pluginMgr == null) {
      pluginMgr =
	(PluginManager)LockssDaemon.getManager(LockssDaemon.PLUGIN_MANAGER);
    }
    return pluginMgr;
  }

  public String toString() {
    return "[PS: auid=" + auId + ", url=" + url
      + ", l=" + lwrBound + ", u=" + uprBound + ", pollV=" + pollVersion + "]";
  }
}
