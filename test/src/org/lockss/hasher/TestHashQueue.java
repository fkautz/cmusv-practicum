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

package org.lockss.hasher;

import java.util.*;
import java.io.*;
import java.security.MessageDigest;
import junit.framework.TestCase;
import org.lockss.daemon.*;
import org.lockss.util.*;
import org.lockss.test.*;


/**
 * Test class for <code>org.lockss.hasher.HashQueue</code>
 */

public class TestHashQueue extends LockssTestCase {
  public static Class testedClasses[] = {
    org.lockss.hasher.HashQueue.class
  };

  static Logger log = Logger.getLogger("HashQueue");
//    static HashServiceTestPlugin.CUS cus = HashServiceTestPlugin.getCUS();
  MockCachedUrlSet cus;
  static final String hashAlgorithm = "SHA-1";
  static MessageDigest dig;

  public TestHashQueue(String msg) {
    super(msg);
  }

  public void setUp() throws Exception {
    log.setLevel(Logger.LEVEL_DEBUG);
    if (dig == null) {
      dig = MessageDigest.getInstance(hashAlgorithm);
    }
    cus = new MockCachedUrlSet(null, null);
  }

  public void tearDown() throws Exception {
    TimeBase.setReal();
    super.tearDown();
  }

//  	HashQueue.Request(CachedUrlSet urlset,
//  			  MessageDigest hasher,
//  			  Deadline deadline,
//  			  HashService.Callback callback,
//  			  Object cookie,
//  			  CachedUrlSetHasher urlsetHasher,
//  			  long estimatedDuration);

  HashQueue.Request simpleReq(long deadlineIn, int duration) {
    return new HashQueue.Request(cus, dig, Deadline.in(deadlineIn),
				 null, null, null, duration);
  }

  HashQueue.Request req(long deadlineIn,
			       int duration,
			       int bytes,
			       HashService.Callback callback) {
    HashQueue.Request req = req(null, deadlineIn, duration, bytes, callback);
    req.cookie = req;
    return req;
  }

  HashQueue.Request req(Object cookie,
			long deadlineIn,
			int duration,
			int bytes,
			HashService.Callback callback) {
    MockCachedUrlSetHasher hasher = new MockCachedUrlSetHasher();
    hasher.setNumBytes(bytes);
    cus.setContentHasher(hasher);
//      cus.setHashDuration(duration, bytes);
    HashQueue.Request req =
      new HashQueue.Request(cus, dig, Deadline.in(deadlineIn),
			    callback, cookie,
			    cus.getContentHasher(dig),
			    duration);
    return req;
  }

  public void testReqOrder() {
    HashQueue q = new HashQueue();
    HashQueue.Request req1 = simpleReq(1000, 100);
    HashQueue.Request req2 = simpleReq(2000, 100);
    assertTrue(req1.runBefore(req2));
    assertTrue(!req2.runBefore(req1));
  }

  public void testOverrunReqOrder() {
    HashQueue q = new HashQueue();
    HashQueue.Request req1 = simpleReq(1000, 100);
    HashQueue.Request req2 = simpleReq(2000, 100);
    HashQueue.Request reqO1 = simpleReq(500, 100);
    reqO1.timeUsed = 600;
    HashQueue.Request reqO2 = simpleReq(1500, 100);
    reqO2.timeUsed = 1600;
    assertTrue(reqO1.runBefore(reqO2));
    assertTrue(!reqO2.runBefore(reqO1));
    assertTrue(req1.runBefore(reqO1));
    assertTrue(req2.runBefore(reqO1));
    assertTrue(req1.runBefore(reqO2));
    assertTrue(req2.runBefore(reqO2));
  }

  // test request acceptance
  public void testAccept() {
    HashQueue q = new HashQueue();
    HashQueue.Request r1, r2, r3 ,r4, r5, r6;
    r1 = simpleReq(-1, 100);
    r2 = simpleReq(2000, 1000);
    r3 = simpleReq(3000, 2900);
    assertEquals(null, q.head());
    assertTrue(!q.insert(r1));
    assertTrue(q.insert(r2));
    assertTrue(!q.insert(r3));
    // change r2 to overrun
    r2.timeUsed = 1200;
    // r3 should now be accepted.  It would prevent r2 from finishing in
    // time, but sr2 should be ignored as it has overrun.
    assertTrue(q.insert(r3));
  }

  // test insert order
  public void testInsertOrder() throws Exception {
    HashQueue q = new HashQueue();
    HashQueue.Request r1, r2, r3 ,r4, r5, r6;
    r1 = simpleReq(2000, 0);
    r2 = simpleReq(3000, 0);
    r3 = simpleReq(5000, 0);
    r4 = simpleReq(2500, 0);
    r5 = simpleReq(200, 0);
    r6 = simpleReq(200, 0);
    // overrun
    r6.timeUsed = 201;
    Object ord[] = {r5, r1, r4, r2, r3, r6};
    assertTrue(q.insert(r1));
    assertTrue(q.insert(r2));
    assertTrue(q.insert(r3));
    assertTrue(q.insert(r6));
    assertTrue(q.insert(r4));
    assertTrue(q.insert(r5));
    assertIsomorphic(ord, (Collection)PrivilegedAccessor.getValue(q, "qlist"));
  }

  // test completion & callback
  public void testDone() throws Exception {
    HashQueue q = new HashQueue();
    final List cookieList = new LinkedList();
    final List eList = new LinkedList();
    HashService.Callback cb = new HashService.Callback() {
	public void hashingFinished(CachedUrlSet urlset,
				    Object cookie,
				    MessageDigest hasher,
				    Exception e) {
	  cookieList.add(cookie);
	  eList.add(e);
	}
      };
    HashQueue.Request r1, r2, r3 ,r4, r5;
    TimeBase.setSimulated();
    r1 = req(2000, 0, 100, cb);
    r2 = req(10000, 0, 200, cb);
    r3 = req(20000, 0, 0, cb);
    r4 = req(50000, 0, 1, cb);
    assertTrue(q.insert(r1));
    assertTrue(q.insert(r2));
    assertTrue(q.insert(r4));
    assertEquals(0, cookieList.size());
    q.removeCompleted();
    assertEquals(0, cookieList.size());
    // make r1 timeout
    r1.deadline.expire();
    q.removeCompleted();
    Object exp[] = {r1};
    assertIsomorphic(exp, cookieList);
    // make r2 timeout
    TimeBase.step(11000);
    // r3 is finished
    assertTrue(q.insert(r3));
    Exception r4e = new Exception();
    // make r4 error
    r4.e = r4e;
    q.removeCompleted();
    // check that they all finished, and in the right order
    Object exp2[] = {r1, r2, r3, r4};
    assertIsomorphic(exp2, cookieList);
    // check their exceptions
    assertTrue(eList.get(0) instanceof HashService.Timeout);
    assertTrue(eList.get(1) instanceof HashService.Timeout);
    assertSame(null, eList.get(2));
    assertSame(r4e, eList.get(3));
  }

  // test stepper
  public void testStep() throws Exception {
    TimeBase.setSimulated();
    HashQueue q = new HashQueue();
    final List cookieList = new LinkedList();
    HashService.Callback cb = new HashService.Callback() {
	public void hashingFinished(CachedUrlSet urlset,
				    Object cookie,
				    MessageDigest hasher,
				    Exception e) {
	  cookieList.add(cookie);
	}
      };
    HashQueue.Request r1, r2, r3 ,r4, r5;
    r1 = req("1", 20000, 10000, 10000, cb);
    r2 = req("2", 100000, 20000, 20000, cb);
    r3 = req("3", 200000, 30000, 40000, cb);
    assertTrue(q.insert(r2));
    q.runAndNotify(3, 75, Boolean.TRUE);
    long n2 = 20000-3*75;
    assertEquals(n2, getBytesLeft(r2));
    assertTrue(q.insert(r1));
    assertTrue(q.insert(r3));
    q.runAndNotify(3, 75, Boolean.TRUE);
//      assertEquals(n2, getBytesLeft(r2));
//      assertEquals(n2, getBytesLeft(r1));
  }

  private long getBytesLeft(HashQueue.Request req) {
    MockCachedUrlSetHasher hasher = (MockCachedUrlSetHasher)req.urlsetHasher;
    return hasher.getBytesLeft();
  }
}
