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
 * This is the test class for org.lockss.mail.SmtpClient
 */
public class TestSmtpClient extends LockssTestCase {
  MockSmtpClient client;
  ByteArrayOutputStream baos = new ByteArrayOutputStream(128);
  PrintStream pstrm;

  public void setUp() throws Exception {
    super.setUp();
    ConfigurationUtil.setFromArgs(Configuration.PARAM_PLATFORM_HOSTNAME,
				  "foohost");
    client = new MockSmtpClient("hostx");
    baos = new ByteArrayOutputStream(128);
    pstrm = new PrintStream(baos);
  }


  public void testConstruct() throws IOException {
    MockSmtpClient client = new MockSmtpClient("hostx");
    assertEquals("hostx", client.host);
    assertEquals(25, client.port);

    client = new MockSmtpClient("hostx", 22);
    assertEquals(22, client.port);
  }

  public void testRespType() throws IOException {
    assertEquals(SmtpClient.RESP_POS, client.respType(200));
    assertEquals(SmtpClient.RESP_POS, client.respType(201));
    assertEquals(SmtpClient.RESP_POS, client.respType(299));
    assertEquals(SmtpClient.RESP_INTER, client.respType(300));
    assertEquals(SmtpClient.RESP_INTER, client.respType(399));
    assertEquals(SmtpClient.RESP_TRANS, client.respType(400));
    assertEquals(SmtpClient.RESP_PERM, client.respType(500));
    assertEquals(SmtpClient.RESP_PERM, client.respType(100));
    assertEquals(SmtpClient.RESP_PERM, client.respType(600));
  }

  public void testSendBodyDot() throws IOException {
    client.sendBody(pstrm, "foo.\r\n.foo\r\n");
    assertEquals("foo.\r\n..foo\r\n.\r\n", baos.toString());
  }

  public void testSendBodyNL() throws IOException {
    client.sendBody(pstrm, "foo\nbar\rbaz\r\nzot.\n.\n.foo");
    assertEquals("foo\r\nbar\rbaz\r\nzot.\r\n..\r\n..foo\r\n.\r\n",
		 baos.toString());
  }

  public void testSendOk() throws Exception {
    client.setResponses("220\n250\n250\n250\n354\n250\n");
    int res = client.sendMsg("source@s.com", "target@t.com", "test message");
    assertEquals(SmtpClient.RESULT_OK, res);
    String expectedMessage = "HELO foohost\r\n" +
                             "MAIL FROM: <source@s.com>\r\n" +
                             "RCPT TO: <target@t.com>\r\n" +
                             "DATA\r\n" +
                             "test message\r\n" +
                             ".\r\n" +
                             "QUIT\r\n";
    assertEquals(expectedMessage, client.getServerInput());
  }

  public void testSendRetry() throws Exception {
    client.setResponses("220\n250\n250\n450\n354\n250\n");
    int res = client.sendMsg("source@s.com", "target@t.com", "test message");
    assertEquals(SmtpClient.RESULT_RETRY, res);
    String expectedMessage = "HELO foohost\r\n" +
                             "MAIL FROM: <source@s.com>\r\n" +
                             "RCPT TO: <target@t.com>\r\n" +
                             "QUIT\r\n";
    assertEquals(expectedMessage, client.getServerInput());
  }

  public void testSendNoRetry() throws Exception {
    client.setResponses("220\n250\n250\n550\n354\n250\n");
    int res = client.sendMsg("source@s.com", "target@t.com", "test message");
    assertEquals(SmtpClient.RESULT_FAIL, res);
    String expectedMessage = "HELO foohost\r\n" +
                             "MAIL FROM: <source@s.com>\r\n" +
                             "RCPT TO: <target@t.com>\r\n" +
                             "QUIT\r\n";
    assertEquals(expectedMessage, client.getServerInput());
  }

  class MockSmtpClient extends SmtpClient {
    String host;
    int port;
    String responses;
    ByteArrayOutputStream baos;
    boolean isOpen;

    MockSmtpClient(String smtpHost)
	throws IOException {
      super(smtpHost);
    }

    MockSmtpClient(String smtpHost, int smtpPort) throws IOException {
      super(smtpHost, smtpPort);
    }

    public void openServer(String server, int port)
	throws IOException, UnknownHostException {
      log.debug3("openServer()");
      this.host = server;
      this.port = port;
      baos = new ByteArrayOutputStream(1024);
      serverOutput = new PrintStream(baos);
      ByteArrayInputStream bais = new ByteArrayInputStream("".getBytes());
      serverInput = new BufferedInputStream(bais);
      isOpen = true;
    }

    public boolean serverIsOpen() {
      return isOpen;
    }

    public void closeServer() {
      isOpen = false;
    }

    void setResponses(String responses) {
      ByteArrayInputStream bais =
	new ByteArrayInputStream(responses.getBytes());
      serverInput = new BufferedInputStream(bais);
    }

    String getServerInput() {
      serverOutput.flush();
      return baos.toString();
    }
  }
}
