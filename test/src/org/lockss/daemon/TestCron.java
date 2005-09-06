/*
 * $Id$
 */

/*

Copyright (c) 2000-2003 Board of Trustees of Leland Stanford Jr. University,
all rights reserved.
n
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

package org.lockss.daemon;

import junit.framework.TestCase;
import java.io.*;
import java.util.*;
import java.text.*;
import org.lockss.app.*;
import org.lockss.util.*;
import org.lockss.config.*;
import org.lockss.remote.*;
import org.lockss.test.*;

/**
 * This is the test class for org.lockss.daemon.Cron
 */
public class TestCron extends LockssTestCase {
  MockLockssDaemon daemon;
  MyCron cron;

  public void setUp() throws Exception {
    super.setUp();
    daemon = getMockLockssDaemon();
  }

  void initCron() {
    initCron(null);
  }

  void initCron(Cron.Task task) {
    cron = new MyCron(task);
    cron.initService(daemon);
    daemon.setDaemonInited(true);
  }

  public void testLoadStateNoFile() {
    initCron();
    cron.loadState(new File("no/such/file"));
    assertEmpty(cron.getState().times);
  }

  public void testState() throws IOException {
    initCron();
    File file = FileTestUtil.tempFile("fff");
    cron.loadState(file);
    assertEmpty(cron.getState().times);
    cron.getState().setLastTime("foo", 1234);
    cron.getState().setLastTime("bar", 777);
    assertEquals(1234, cron.getState().getLastTime("foo"));
    assertEquals(777, cron.getState().getLastTime("bar"));
    cron.storeState(file);
    assertEquals(1234, cron.getState().getLastTime("foo"));
    assertEquals(777, cron.getState().getLastTime("bar"));
    cron.getState().setLastTime("foo", 1);
    cron.getState().setLastTime("bar", 2);
    assertEquals(1, cron.getState().getLastTime("foo"));
    assertEquals(2, cron.getState().getLastTime("bar"));
    cron.loadState(file);
    assertEquals(1234, cron.getState().getLastTime("foo"));
    assertEquals(777, cron.getState().getLastTime("bar"));
  }

  static DateFormat df = new SimpleDateFormat("MM/dd/yyyy hh:mm");
  static {
    df.setTimeZone(Constants.DEFAULT_TIMEZONE);
  }

  public void testCron() throws IOException {
    TimeBase.setSimulated(1000);
    TestTask task = new TestTask(daemon);
    initCron(task);
    File dir = getTempDir();
    Properties p = new Properties();
    p.put(ConfigManager.PARAM_PLATFORM_DISK_SPACE_LIST, dir.toString());
    p.put(Cron.PARAM_SLEEP, "10");
    p.put(Cron.PARAM_ENABLED, "true");
    ConfigurationUtil.setCurrentConfigFromProps(p);
    cron.startService();
    TimeBase.step(10);
    assertEquals(ListUtil.list(new Long(1010)), task.getTrace());
    TimeBase.step(10);
    assertEquals(ListUtil.list(new Long(1010)), task.getTrace());
    TimeBase.step(10);
    assertEquals(ListUtil.list(new Long(1010)), task.getTrace());
    TimeBase.step(70);
    assertEquals(ListUtil.list(new Long(1010)), task.getTrace());
    TimeBase.step(20);
    assertEquals(ListUtil.list(new Long(1010), new Long(1120)),
		 task.getTrace());
  }

  public void testMailBackupFileNext() throws Exception {
    Cron.MailBackupFile task = new Cron.MailBackupFile(daemon);
    // time 0 is midnight Jan 1, result s.b. Feb 1
    assertEquals(31*Constants.DAY, task.nextTime(0));
    assertEquals(df.parse("2/1/2005 0:00").getTime(),
		 task.nextTime(df.parse("1/3/2005 1:00").getTime()));
    assertEquals(df.parse("12/1/2007 0:00").getTime(),
		 task.nextTime(df.parse("11/30/2007 4:00").getTime()));
    assertEquals(df.parse("2/1/2008 0:00").getTime(),
		 task.nextTime(df.parse("1/7/2008 4:00").getTime()));
    assertEquals(df.parse("1/1/2008 0:00").getTime(),
		 task.nextTime(df.parse("12/7/2007 4:00").getTime()));
  }

  public void testMailBackupFileMail() {
    MyRemoteApi rmt = new MyRemoteApi();
    daemon.setRemoteApi(rmt);
    Cron.MailBackupFile task = new Cron.MailBackupFile(daemon);
    assertFalse(rmt.sent);
    task.execute();
    assertTrue(rmt.sent);
  }

  static class MyCron extends Cron {
    Cron.Task task = null;
    MyCron() {
      super();
    }
    MyCron(Cron.Task task) {
      super();
      this.task = task;
    }

    void installTasks() {
      if (false) super.installTasks();
      if (task != null) {
	addTask(task);
      }
    }
  }

  static class TestTask implements Cron.Task {
    List trace = new ArrayList();

    TestTask(LockssDaemon daemon) {
    }

    public String getId() {
      return "TestTask";
    }

    public long nextTime(long lastTime) {
      return lastTime + 100;
    }

    public void execute() {
      trace.add(new Long(TimeBase.nowMs()));
    }

    List getTrace() {
      return trace;
    }
  }


  static class MyRemoteApi extends RemoteApi {
    boolean sent = false;

    public void sendMailBackup() throws IOException {
      sent = true;
    }
  }

}
