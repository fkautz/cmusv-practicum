/*
 * $Id$
 */

/*

Copyright (c) 2000-2004 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.mail;

import java.io.*;
import java.util.*;
import java.net.*;
import org.lockss.test.*;
import org.lockss.util.*;
import org.lockss.daemon.*;
import org.lockss.plugin.*;

/**
 * This is the test class for org.lockss.mail.SmtpMailService
 */
public class TestSmtpMailService extends LockssTestCase {
  private static Logger log = Logger.getLogger("TestSMS");

  private MockLockssDaemon daemon;
  MockSmtpMailService svc;
  MockSmtpClient client;

  public void setUp() throws Exception {
    super.setUp();
    daemon = getMockLockssDaemon();

    client = new MockSmtpClient();
    svc = new MockSmtpMailService(client);
    daemon.setMailService(svc);
    daemon.setDaemonInited(true);
    svc.initService(daemon);
  }


  public void tearDown() throws Exception {
    TimeBase.setReal();
    if (svc != null) {
      svc.stopService();
      svc = null;
      client = null;
    }
    super.tearDown();
  }

  public void testEnqueue() throws IOException {
    assertTrue(svc.sendMail("frump", "toop", "bawdeep"));
    Queue q = svc.getQueue();
    assertTrue(svc.threadKicked);
    assertFalse(q.isEmpty());
    SmtpMailService.Req req = (SmtpMailService.Req)q.peek();
    assertEquals("frump", req.sender);
    assertEquals("toop", req.recipient);
    assertEquals("bawdeep", req.body);
  }

  public void testDequeue() throws IOException, InterruptedException {
    svc.setRunThread(true);
    SimpleBinarySemaphore sem = client.getSem();
    svc.sendMail("fff", "ttt", "bbb");
    sem.take(TIMEOUT_SHOULDNT);
    assertEquals("fff", client.sender);
    assertEquals("ttt", client.recipient);
    assertEquals("bbb", client.body);
  }

  public void testRateLimit() throws IOException, InterruptedException {
    ConfigurationUtil.setFromArgs(SmtpMailService.PARAM_MAX_MAILS_PER_INTERVAL,
				  "3",
				  SmtpMailService.PARAM_MAX_MAILS_INTERVAL,
				  "10");
    TimeBase.setSimulated(100);
    svc.setRunThread(true);
    SimpleBinarySemaphore sem = client.getSem();
    svc.sendMail("m1", "t", "b");
    svc.sendMail("m2", "t", "b");
    svc.sendMail("m3", "t", "b");
    svc.sendMail("m4", "t", "b");
    while (client.senders.size() < 3) {
      if (! sem.take(TIMEOUT_SHOULDNT)) {
	fail("only " + client.senders.size() + " messages arrived");
      }
    }
    sem.take(100);
    if (client.senders.size() > 3) {
      fail(client.senders.size() + " messages arrived, only 3 should have");
    }
    TimeBase.step(10);
    while (client.senders.size() < 4) {
      if (! sem.take(TIMEOUT_SHOULDNT)) {
	fail("only " + client.senders.size() + " messages arrived");
      }
    }
  }

  public void testRetry() throws IOException, InterruptedException {
    ConfigurationUtil.setFromArgs(SmtpMailService.PARAM_RETRY_INTERVAL,
				  "101");
    TimeBase.setSimulated(100);
    svc.setRunThread(true);
    SimpleBinarySemaphore sem = client.getSem();
    client.setResult(SmtpClient.RESULT_RETRY);
    assertEquals(0, client.senders.size());
    svc.sendMail("m1", "t", "b");
    assertTrue("message wasn't sent", sem.take(TIMEOUT_SHOULDNT));
    assertEquals(1, client.senders.size());
    client.setResult(SmtpClient.RESULT_OK);
    svc.sendMail("m2", "t", "b");
    assertTrue("message wasn't sent", sem.take(TIMEOUT_SHOULDNT));
    assertEquals(2, client.senders.size());
    Queue q = svc.getQueue();
    assertFalse(q.isEmpty());
    TimeBase.step(102);
    assertTrue("message wasn't sent", sem.take(TIMEOUT_SHOULDNT));
    assertEquals(ListUtil.list("m1", "m2", "m1"), client.senders);
    // thread may not have emptied queue yet.  need better way to test this
//     assertTrue(q.isEmpty());
  }

  public void testMaxQueuelen() throws IOException, InterruptedException {
    ConfigurationUtil.setFromArgs(SmtpMailService.PARAM_MAX_QUEUELEN,
				  "2");
    TimeBase.setSimulated(100);
    Queue q = svc.getQueue();
    SimpleBinarySemaphore sem = client.getSem();
    assertTrue(svc.sendMail("m1", "t", "b"));
    assertTrue(svc.sendMail("m2", "t", "b"));
    assertEquals(2, q.size());
    assertFalse(svc.sendMail("m3", "t", "b"));
    svc.setRunThread(true);
    // can't try to add another to queue until certain at least one of the
    // previous ones has been removed (which happens after send semaphore
    // is posted), so must wait until *second* is sent
    while (client.senders.size() < 2) {
      if (! sem.take(TIMEOUT_SHOULDNT)) {
	fail("only " + client.senders.size() + " messages arrived");
      }
    }
    assertTrue(svc.sendMail("m4", "t", "b"));
  }

  static class MockSmtpMailService extends SmtpMailService {
    MockSmtpClient client;
    IOException e;
    boolean threadKicked = false;
    boolean runThread = false;

    MockSmtpMailService(MockSmtpClient client) {
      this.client = client;
    }

    SmtpClient makeClient() throws IOException {
      if (e != null) throw e;
      return client;
    }

    synchronized void ensureThreadRunning() {
      threadKicked = true;
      if (runThread) super.ensureThreadRunning();
    }

    void setException(IOException e) {
      this.e = e;
    }

    void setRunThread(boolean runThread) {
      this.runThread = runThread;
      if (runThread && threadKicked) {
	ensureThreadRunning();
      }
    }

    Queue getQueue() {
      return queue;
    }
  }

  static class MockSmtpClient extends SmtpClient {
    volatile String sender;
    volatile String recipient;
    volatile String body;
    volatile int result = SmtpClient.RESULT_OK;
    SimpleBinarySemaphore sem;
    List senders = new ArrayList();

    MockSmtpClient() throws IOException {
      super();
    }

    public int sendMsg(String sender, String recipient, String body) {
      senders.add(sender);
      this.sender = sender;
      this.recipient = recipient;
      this.body = body;
      // must grab intended result *before* posting semaphore, as main may
      // then go on and change result for next one
      int res = result;

      if (sem != null) sem.give();
      return res;
    }

    SimpleBinarySemaphore getSem() {
      sem = new SimpleBinarySemaphore();
      return sem;
    }

    void setResult(int result) {
      this.result = result;
    }      

  }
}
