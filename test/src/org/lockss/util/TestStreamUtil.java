/*
 * $Id$
 */

/*

Copyright (c) 2000-2006 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.util;

import java.util.*;
import java.io.*;
import junit.framework.TestCase;
import org.apache.commons.io.input.*;
import org.apache.commons.io.output.NullOutputStream;

import org.lockss.test.*;
import org.lockss.daemon.LockssWatchdog;

/**
 * This is the test class for org.lockss.util.StreamUtil
 */
public class TestStreamUtil extends LockssTestCase {
  public TestStreamUtil(String msg) {
    super(msg);
  }

  public void testCopyNullInputStream() throws IOException {
    OutputStream baos = new ByteArrayOutputStream(11);
    assertEquals(0, StreamUtil.copy(null, baos));
    String resultStr = baos.toString();
    baos.close();
    assertEquals("", baos.toString());
  }

  public void testCopyNullOutputStream() throws IOException {
    InputStream is = new StringInputStream("test string");
    assertEquals(0, StreamUtil.copy(is, null));
    is.close();
  }

  public void testCopyInputStream() throws IOException {
    InputStream is = new StringInputStream("test string");
    OutputStream baos = new ByteArrayOutputStream(11);
    StreamUtil.copy(is, baos);
    is.close();
    String resultStr = baos.toString();
    baos.close();
    assertTrue(resultStr.equals("test string"));
  }

  public void testCopyInputStreamReturnsCount() throws IOException {
    InputStream is = new StringInputStream("test string");
    OutputStream baos = new ByteArrayOutputStream(11);
    assertEquals(11, StreamUtil.copy(is, baos));
    is.close();
    baos.close();
  }

  public void testCopyInputStreamLength() throws IOException {
    InputStream is = new StringInputStream("012345678901234567890");
    OutputStream baos = new ByteArrayOutputStream(20);
    assertEquals(5, StreamUtil.copy(is, baos, 5));
    assertEquals("01234", baos.toString());
    assertEquals(5, StreamUtil.copy(is, baos, 5));
    assertEquals("0123456789", baos.toString());
    assertEquals(5, StreamUtil.copy(is, baos, 5));
    assertEquals("012345678901234", baos.toString());
    StreamUtil.copy(is, baos, 5);
    assertEquals("01234567890123456789", baos.toString());
    is.close();
    baos.close();

    is = new StringInputStream("01234567890123456789012345");
    baos = new ByteArrayOutputStream(2);
    assertEquals(2, StreamUtil.copy(is, baos, 2));
    assertEquals("01", baos.toString());
    baos = new ByteArrayOutputStream(5);
    assertEquals(5, StreamUtil.copy(is, baos, 5));
    assertEquals("23456", baos.toString());
    baos = new ByteArrayOutputStream(7);
    assertEquals(7, StreamUtil.copy(is, baos, 7));
    assertEquals("7890123", baos.toString());
    baos = new ByteArrayOutputStream(7);
    assertEquals(12, StreamUtil.copy(is, baos, -1));
    assertEquals("456789012345", baos.toString());
    is.close();
    baos.close();
  }

  public void testCopyInputStreamPokeWatchdog() throws IOException {
    TimeBase.setSimulated(1000);
    final MyLockssWatchdog wdog = new MyLockssWatchdog(100);
    SimpleBinarySemaphore wait = new SimpleBinarySemaphore();
    SimpleBinarySemaphore poke = new SimpleBinarySemaphore();
    int size = 50 * StreamUtil.COPY_WDOG_CHECK_EVERY_BYTES;
    // make an InputStream that waits every time it has supplied twice the
    // byte interval at which StreamUtil.copy() checks to see if it's time
    // to poke the watchdog
    final InputStream is =
      new SemwaitInputStream(new ZeroInputStream(size),
			     StreamUtil.COPY_WDOG_CHECK_EVERY_BYTES * 2,
			     wait,
			     poke);
    final OutputStream os = new NullOutputStream();
    Thread th = new Thread() {
	public void run() {
	  try {
	    StreamUtil.copy(is, os, wdog);
	  } catch (IOException e) {
	    throw new RuntimeException("", e);
	  }
	}};
    th.start();
    poke.give();
    assertTrue(wait.take(TIMEOUT_SHOULDNT));
    assertEquals(0, wdog.times.size());
    TimeBase.step(1000);
    poke.give();
    assertTrue(wait.take(TIMEOUT_SHOULDNT));
    assertEquals(1, wdog.times.size());
    TimeBase.step(1000);
    poke.give();
    assertTrue(wait.take(TIMEOUT_SHOULDNT));
    poke.give();
    assertTrue(wait.take(TIMEOUT_SHOULDNT));
    assertEquals(2, wdog.times.size());
    is.close();
    os.close();
  }

  public void testCopyNullReader() throws IOException {
    Writer writer = new CharArrayWriter(11);
    assertEquals(0, StreamUtil.copy(null, writer));
    String resultStr = writer.toString();
    writer.close();
    assertEquals("", writer.toString());
  }

  public void testCopyNullWriter() throws IOException {
    Reader reader = new StringReader("test string");
    assertEquals(0, StreamUtil.copy(reader, null));
    reader.close();
  }

  public void testCopyReader() throws IOException {
    Reader reader = new StringReader("test string");
    Writer writer = new CharArrayWriter(11);
    assertEquals(11, StreamUtil.copy(reader, writer));
    reader.close();
    String resultStr = writer.toString();
    writer.close();
    assertTrue(resultStr.equals("test string"));
  }

  public void testReadBuf() throws IOException {
    int len = 12;
    byte[] buf = new byte[len];
    InputStream ins = new StringInputStream("01\0003456789abcdefghijklmnopq");
    StreamUtil.readBytes(ins, buf, len);
    byte[] exp = {'0', '1', 0, '3', '4', '5', '6', '7', '8', '9', 'a', 'b'};
    assertEquals(exp, buf);
  }

  public void testReadBufShortRead() throws Exception {
    byte[] snd1 = {'0', '1', 0, '3'};
    final int len = 12;
    final byte[] buf = new byte[len];
    PipedOutputStream outs = new PipedOutputStream();
    final InputStream ins = new PipedInputStream(outs);
    final Exception[] ex = {null};
    final int[] res = {0};
    Thread th = new Thread() {
	public void run() {
	  try {
	    res[0] = StreamUtil.readBytes(ins, buf, len);
	    StreamUtil.readBytes(ins, buf, len);
	  } catch (IOException e) {
	    ex[0] = e;
	  }
	}};
    th.start();
    outs.write(snd1);
    outs.close();
    th.join();

    assertEquals(snd1.length, res[0]);
    assertEquals(null, ex[0]);
  }

  public void testReadBufMultipleRead() throws Exception {
    byte[] snd1 = {'0', '1', 0, '3'};
    byte[] snd2 = {'4', '5', '6', '7', '8', '9', 'a', 'b'};
    byte[] exp = {'0', '1', 0, '3', '4', '5', '6', '7', '8', '9', 'a', 'b'};
    final int len = exp.length;
    final byte[] buf = new byte[len];
    PipedOutputStream outs = new PipedOutputStream();
    final InputStream ins = new PipedInputStream(outs);
    final Exception[] ex = {null};
    final int[] res = {0};
    Thread th = new Thread() {
	public void run() {
	  try {
	    res[0] = StreamUtil.readBytes(ins, buf, len);
	  } catch (IOException e) {
	    ex[0] = e;
	  }
	}};
    th.start();
    outs.write(snd1);
    TimerUtil.guaranteedSleep(100);
    outs.write(snd2);
    outs.flush();
    th.join();

    assertEquals(exp, buf);
    assertEquals(len, res[0]);
    assertNull(ex[0]);
    outs.close();
  }

  public void testReadBufReader() throws IOException {
    int len = 12;
    char[] buf = new char[len];
    Reader reader = new StringReader("0103456789abcdefghijklmnopq");
    StreamUtil.readChars(reader, buf, len);
    char[] exp = {'0', '1', '0', '3', '4', '5', '6', '7', '8', '9', 'a', 'b'};
    assertEquals(exp, buf);
  }


  public void testReadCharShortRead() throws Exception {
    char[] snd1 = {'0', '1', 0, '3'};
    final int len = 12;
    final char[] buf = new char[len];
    PipedWriter outs = new PipedWriter();
    final Reader ins = new PipedReader(outs);
    final Exception[] ex = {null};
    final int[] res = {0};
    Thread th = new Thread() {
	public void run() {
	  try {
	    res[0] = StreamUtil.readChars(ins, buf, len);
	    StreamUtil.readChars(ins, buf, len);
	  } catch (IOException e) {
	    ex[0] = e;
	  }
	}};
    th.start();
    outs.write(snd1);
    outs.close();
    th.join();

    assertEquals(snd1.length, res[0]);
    assertEquals(null, ex[0]);
  }

  public void testReadCharMultipleRead() throws Exception {
    char[] snd1 = {'0', '1', 0, '3'};
    char[] snd2 = {'4', '5', '6', '7', '8', '9', 'a', 'b'};
    char[] exp = {'0', '1', 0, '3', '4', '5', '6', '7', '8', '9', 'a', 'b'};
    final int len = exp.length;
    final char[] buf = new char[len];
    PipedWriter outs = new PipedWriter();
    final Reader ins = new PipedReader(outs);
    final Exception[] ex = {null};
    final int[] res = {0};
    Thread th = new Thread() {
	public void run() {
	  try {
	    res[0] = StreamUtil.readChars(ins, buf, len);
	  } catch (IOException e) {
	    ex[0] = e;
	  }
	}};
    th.start();
    outs.write(snd1);
    TimerUtil.guaranteedSleep(100);
    outs.write(snd2);
    outs.flush();
    th.join();

    assertEquals(exp, buf);
    assertEquals(len, res[0]);
    assertNull(ex[0]);
    outs.close();
  }

  public void testCompareStreams() throws IOException {
    String s1 = "01\0003456789abcdefghijklmnopq";
    assertTrue(StreamUtil.compare(new StringInputStream(""),
				  new StringInputStream("")));
    assertTrue(StreamUtil.compare(new StringInputStream(s1),
				  new StringInputStream(s1)));
    assertFalse(StreamUtil.compare(new StringInputStream(s1),
				   new StringInputStream("")));
    assertFalse(StreamUtil.compare(new StringInputStream(""),
				   new StringInputStream(s1)));
    assertFalse(StreamUtil.compare(new StringInputStream(s1),
				   new StringInputStream(s1+"a")));
    assertFalse(StreamUtil.compare(new StringInputStream(s1+"a"),
				   new StringInputStream(s1)));
    assertFalse(StreamUtil.compare(new StringInputStream("foo"),
				   new StringInputStream("bar")));
  }

  public static class MyLockssWatchdog implements LockssWatchdog {
    List times = new ArrayList();
    long intr;
    MyLockssWatchdog(long interval) {
      this.intr = interval;
    }
    public void startWDog(long interval) {}
    public void stopWDog() {}
    public void pokeWDog() {
      times.add(new Long(TimeBase.nowMs()));
    }
    public long getWDogInterval() {
      return intr;
    }
  }

  public static class SemwaitInputStream extends ProxyInputStream {
    private CountingInputStream in;
    private int everyNBytes;
    private SimpleBinarySemaphore firstPoke;
    private SimpleBinarySemaphore thenWait;
    private long next;


    public SemwaitInputStream(InputStream in, int everyNBytes,
			      SimpleBinarySemaphore firstPoke,
			      SimpleBinarySemaphore thenWait) {
      this(new CountingInputStream(in), everyNBytes, firstPoke, thenWait);
    }

    private SemwaitInputStream(CountingInputStream in, int everyNBytes,
			       SimpleBinarySemaphore firstPoke,
			       SimpleBinarySemaphore thenWait) {
      super(in);
      this.in = in;
      this.everyNBytes = everyNBytes;
      this.firstPoke = firstPoke;
      this.thenWait = thenWait;
      next = everyNBytes;
    }

    void doit() {
      log.debug("doit()");
      firstPoke.give();
      thenWait.take();
      log.debug("done()");
      next = in.getCount() + everyNBytes;
    }

    public int read(byte[] b) throws IOException {
      int found = super.read(b);
      if (in.getCount() >= next) doit();
      return found;
    }

    public int read(byte[] b, int off, int len) throws IOException {
      int found = super.read(b, off, len);
      if (in.getCount() >= next) doit();
      return found;
    }

    public int read() throws IOException {
      int found = super.read();
      if (in.getCount() >= next) doit();
      return found;
    }
  }
}
