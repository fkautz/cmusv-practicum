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

package org.lockss.clockss;

import java.io.*;
import java.util.*;
import org.lockss.util.*;
import org.lockss.util.urlconn.*;
import org.lockss.daemon.*;
import org.lockss.state.*;
import org.lockss.plugin.*;
import org.lockss.crawler.PermissionMap;

/**
 * Wrapper for UrlConnection, which performs CLOCKSS subscription
 * detection.
 */
public class ClockssUrlCacher implements UrlCacher {
  private static Logger log = Logger.getLogger("ClockssUrlCacher");

  public static final int PROBE_NONE = 0;
  public static final int PROBE_INST = 1;
  public static final int PROBE_CLOCKSS = 2;

  private UrlCacher uc;
  private int probeState = PROBE_NONE;
  private boolean executed = false;

  public ClockssUrlCacher(UrlCacher uc) {
    this.uc = uc;
  }

  // State machine to control the local address we fetch from, and whether
  // to retry from the other address.  There are two UrlCacher methods that
  // might open a connection to be opened to the server: cache() and
  // getUncachedInputStream().  The wrappers for those methods call
  // setupAddr before forwarding the call.

  boolean setupAddr() {
    if (executed) {
      // already succeeded or failed, don't change state, don't retry
      return false;
    }
    ArchivalUnit au = uc.getArchivalUnit();
    AuState aus = AuUtil.getAuState(au);
    ClockssParams mgr = AuUtil.getDaemon(au).getClockssParams();
    switch (aus.getClockssSubscriptionStatus()) {
    case AuState.CLOCKSS_SUB_UNKNOWN:
    case AuState.CLOCKSS_SUB_YES:
    case AuState.CLOCKSS_SUB_INACCESSIBLE:
      switch (probeState) {
      case PROBE_NONE:
	uc.setLocalAddress(mgr.getInstitutionSubscriptionAddr());
	probeState = PROBE_INST;
	return true;
      case PROBE_INST:
	uc.setLocalAddress(mgr.getClockssSubscriptionAddr());
	probeState = PROBE_CLOCKSS;
	return true;
      case PROBE_CLOCKSS:
	return false;
      default:
	log.error("Unexpected probeState: " + probeState);
      }
      return false;
//     case AuState.CLOCKSS_SUB_YES:
//       uc.setLocalAddress(mgr.getInstitutionSubscriptionAddr());
//       return true;
    case AuState.CLOCKSS_SUB_NO:
      switch (probeState) {
      case PROBE_NONE:
	uc.setLocalAddress(mgr.getClockssSubscriptionAddr());
	probeState = PROBE_CLOCKSS;
	return true;
      case PROBE_CLOCKSS:
      default:
	return false;
      }
    default:
      log.error("Unknown subscription state: " +
		aus.getClockssSubscriptionStatus());
    }
    return false;
  }

  // XXX this isn't quite right.  If we're fetching the permission page,
  // subscription status shouldn't be updated until the permissions are
  // found, probe page checked, etc.  This probably has to be done at a
  // higher level, e.g, in the crawler.
  void updateSubscriptionStatus(boolean worked) {
    executed = true;
    ArchivalUnit au = uc.getArchivalUnit();
    AuState aus = AuUtil.getAuState(au);
    if (worked) {
      switch (aus.getClockssSubscriptionStatus()) {
      case AuState.CLOCKSS_SUB_UNKNOWN:
	switch (probeState) {
	case PROBE_INST:
	  aus.setClockssSubscriptionStatus(AuState.CLOCKSS_SUB_YES);
	  break;
	case PROBE_CLOCKSS:
	  aus.setClockssSubscriptionStatus(AuState.CLOCKSS_SUB_NO);
	  break;
	default:
	  log.error("Unexpected probeState: " + probeState);
	}
	break;
      case AuState.CLOCKSS_SUB_YES:
	switch (probeState) {
	case PROBE_INST:
	  break;
	case PROBE_CLOCKSS:
	  aus.setClockssSubscriptionStatus(AuState.CLOCKSS_SUB_NO);
	  break;
	default:
	  log.error("Unexpected probeState: " + probeState);
	}
	break;
      case AuState.CLOCKSS_SUB_NO:
	// If we determined we don't have a subscription, should be change
	// our mind here?
	break;
      default:
	log.error("Unknown subscription state: " +
		  aus.getClockssSubscriptionStatus());
      }
    } else {
      aus.setClockssSubscriptionStatus(AuState.CLOCKSS_SUB_INACCESSIBLE);
    }
  }

  public ArchivalUnit getArchivalUnit() {
    return uc.getArchivalUnit();
  }

  public String getUrl() {
    return uc.getUrl();
  }

  /** @deprecated */
  public CachedUrlSet getCachedUrlSet() {
    return uc.getCachedUrlSet();
  }

  public boolean shouldBeCached(){
    return uc.shouldBeCached();
  }

  public CachedUrl getCachedUrl() {
    return uc.getCachedUrl();
  }

  public void setConnectionPool(LockssUrlConnectionPool connectionPool) {
    uc.setConnectionPool(connectionPool);
  }

  public void setProxy(String proxyHost, int proxyPort) {
    uc.setProxy(proxyHost, proxyPort);
  }

  public void setLocalAddress(IPAddr addr) {
    uc.setLocalAddress(addr);
  }

  public void setFetchFlags(BitSet fetchFlags) {
    uc.setFetchFlags(fetchFlags);
  }

  public void setRequestProperty(String key, String value) {
    uc.setRequestProperty(key, value);
  }

  public void setRedirectScheme(RedirectScheme scheme) {
    uc.setRedirectScheme(scheme);
  }

  public void storeContent(InputStream input, CIProperties headers)
      throws IOException {
    uc.storeContent(input, headers);
  }

  public int cache() throws IOException {
    int res;
    boolean worked = false;
    setupAddr();
    try {
      res = uc.cache();
      worked = true;
      return res;
    } catch (CacheException.PermissionException e) {
      if (setupAddr()) {
	uc.reset();
	res = uc.cache();
	worked = true;
	return res;
      } else {
	throw e;
      }
    } finally {
      updateSubscriptionStatus(worked);
    }
  }

  public InputStream getUncachedInputStream() throws IOException {
    InputStream res = null;
    setupAddr();
    try {
      res = uc.getUncachedInputStream();
      return res;
    } catch (CacheException.PermissionException e) {
      if (setupAddr()) {
	uc.reset();
	res = uc.getUncachedInputStream();
	return res;
      } else {
	throw e;
      }
    } finally {
      updateSubscriptionStatus(res != null);
    }
  }

  public CIProperties getUncachedProperties() {
    return uc.getUncachedProperties();
  }

  public void reset() {
    uc.reset();
    executed = false;
    probeState = PROBE_NONE;
  }

  public void setPermissionMapSource(PermissionMapSource pmSource) {
    uc.setPermissionMapSource(pmSource);
  }

  public String toString() {
    return "[ClockssUrlCacher: " + uc + "]";
  }
}
