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

import java.util.*;
import junit.framework.TestCase;
import org.lockss.daemon.*;
import org.lockss.plugin.*;
import org.lockss.util.*;
import org.lockss.test.*;
import org.lockss.protocol.*;
import java.net.*;
import java.io.*;

/**
 * This is the test class for org.lockss.poller.PollSpec
 */

public class TestPollSpec extends LockssTestCase {
  private MockLockssDaemon theDaemon;

  public void setUp() throws Exception {
    super.setUp();


    String tempDirPath = getTempDir().getAbsolutePath() + File.separator;
    Properties p = new Properties();
    p.setProperty(IdentityManager.PARAM_IDDB_DIR, tempDirPath + "iddb");
    p.setProperty(IdentityManager.PARAM_LOCAL_IP, "127.0.0.1");
    ConfigurationUtil.setCurrentConfigFromProps(p);

    theDaemon = new MockLockssDaemon();
    theDaemon.getIdentityManager();
    theDaemon.getPluginManager();
  }

  public void tearDown() throws Exception {
    super.tearDown();
  }

  public void testFromCus() {
    String auid = "aaai1";
    String url = "http://foo.bar/";
    String lower = "abc";
    String upper = "xyz";
    String plugVer = "ver42";
    MockArchivalUnit au = new MockArchivalUnit();
    au.setAuId(auid);
    MockPlugin mp = new MockPlugin();
    mp.setVersion(plugVer);
    au.setPlugin(mp);

    CachedUrlSet cus =
      new MockCachedUrlSet(au, new RangeCachedUrlSetSpec(url, lower, upper));
    PollSpec ps = new PollSpec(cus);
    assertEquals(auid, ps.getAuId());
    assertEquals(url, ps.getUrl());
    assertEquals(lower, ps.getLwrBound());
    assertEquals(upper, ps.getUprBound());
    assertEquals(plugVer, ps.getPluginVersion());
  }

  public void testFromLcapMessage() throws Exception {
    byte[] testbytes = {0,1,2,3,4,5,6,8,10};
    String auid = "aaai1";
    String url = "http://foo.bar/";
    String lower = "abc";
    String upper = "xyx";
    MockArchivalUnit au = new MockArchivalUnit();
    au.setAuId(auid);
    Plugin plug = new MockPlugin();
    au.setPlugin(plug);
    CachedUrlSet cus =
      new MockCachedUrlSet(au, new RangeCachedUrlSetSpec(url, lower, upper));
    PollSpec ps = new PollSpec(cus);
    LcapIdentity id = null;
    try {
      IPAddr addr = IPAddr.getByName("127.0.0.1");
      id = theDaemon.getIdentityManager().findIdentity(addr);
    }
    catch (UnknownHostException ex) {
      fail("can't open test host");
    }
    LcapMessage msg = null;
      msg =
	LcapMessage.makeRequestMsg(ps,
				   null,
				   testbytes,
				   testbytes,
				   LcapMessage.NAME_POLL_REQ,
				   10000000,
				   id);
    ps = new PollSpec(msg);
    assertEquals(auid, ps.getAuId());
    assertEquals(url, ps.getUrl());
    assertEquals(lower, ps.getLwrBound());
    assertEquals(upper, ps.getUprBound());
    assertEquals(plug.getVersion(), ps.getPluginVersion());
}

  public static void main(String[] argv) {
    String[] testCaseList = {TestPollSpec.class.getName()};
    junit.textui.TestRunner.main(testCaseList);
  }
}
