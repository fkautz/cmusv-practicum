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

package org.lockss.mbf;

import java.io.*;
import java.security.*;
import java.util.*;
import org.lockss.test.*;
import org.lockss.util.*;

/**
 * JUnitTest case for class: org.lockss.mbf.MBF2
 * @author David S. H. Rosenthal
 * @version 1.0
 */
public class TestMBF2 extends LockssTestCase {
  private static Logger log = null;
  private static Random rand = null;
  private static File f = null;
  private static byte[] basis;
  private int pathsTried;
  private static String[] names = {
    "MOCK",
    "MBF1",
    "MBF2",
  };

  protected void setUp() throws Exception {
    super.setUp();
    log = Logger.getLogger("TestMBF2");
    if (false)
      rand = new Random(100);
    else 
      rand = new Random(System.currentTimeMillis());
    if (f == null) {
      f = FileUtil.tempFile("mbf1test");
      FileOutputStream fis = new FileOutputStream(f);
      basis = new byte[16*1024*1024 + 1024];
      rand.nextBytes(basis);
      fis.write(basis);
      fis.close();
      log.info(f.getPath() + " bytes " + f.length() + " long created");
    }
    if (!f.exists())
      log.warning(f.getPath() + " doesn't exist");
    MemoryBoundFunction.configure(f);
  }

  /** tearDown method for test case
   * @throws Exception if XXX
   */
  public void tearDown() throws Exception {
    super.tearDown();
  }

  // XXX test that calling computeSteps() when finished does nothing.
  // XXX test behavior of empty proof
  // XXX separate timing tests etc into Func

  /**
   * Test exceptions
   */
  public void testExceptions() {
    byte[] nonce = new byte[4];
    rand.nextBytes(nonce);
    try {
      int[] bad = new int[0];
      MemoryBoundFunction mbf =
	MemoryBoundFunction.getInstance("BOGUS", nonce, 3, 2048, bad, 9); 
      fail("BOGUS" + ": didn't throw");
    } catch (MemoryBoundFunctionException ex) {
      fail("BOGUS" + ": threw " + ex.toString());
    } catch (NoSuchAlgorithmException ex) {
      // No action intended
    }
    for (int i = 0; i < names.length; i++) {
      try {
	int[] bad = new int[0];
	MemoryBoundFunction mbf =
	  MemoryBoundFunction.getInstance(names[i], nonce, 3, 2048, bad, 9);
	fail(names[i] + ": didn't throw exception on empty proof");
      } catch (MemoryBoundFunctionException ex) {
	// No action intended
      } catch (NoSuchAlgorithmException ex) {
	fail(names[i] + ": threw " + ex.toString());
      }
      try {
	int[] bad = new int[12];
	MemoryBoundFunction mbf =
	  MemoryBoundFunction.getInstance(names[i], nonce, 3, 2048, bad, 9);
	fail(names[i] + ": didn't throw exception on too-long proof");
      } catch (MemoryBoundFunctionException ex) {
	// No action intended
      } catch (NoSuchAlgorithmException ex) {
	fail(names[i] + ": threw " + ex.toString());
      }
    }
  }

  /**
   * Test one generate/verify pair
   */
  public void testOnce() throws IOException {
    for (int i = 0; i < names.length; i++)
      onePair(names[i], 63, 2048);
  }

  /*
   * Test a series of generate/verify pairs
   */
  public void testMultiple() throws IOException {
    for (int i = 0; i < names.length; i++)
      for (int j = 0; j < 64; j++) {
	onePair(names[i], 31, 32);
      }
  }

  /**
   * Timing test.  Verify that the MBF requirements are met, i.e:
   * * On average generate takes longer than verify.
   * * Increasing l increases the cost of both generate and verify
   * * Increasing e increases the factor by which generate is more
   *   costly than verify.
   */
  public void TimingOne(String name) throws IOException {
    byte[] nonce = new byte[24];
    int e;
    int l;
    int[] proof;
    int numTries = 10;
    long totalGenerateTime;
    long totalVerifyTime;

    // Generate time > Verify time
    e = 31;
    l = 2048;
    totalGenerateTime = totalVerifyTime = 0;
    for (int i = 0; i < numTries; i++) {
      rand.nextBytes(nonce);
      long startTime = System.currentTimeMillis();
      proof = generate(name, nonce, e, l, l);
      long endTime = System.currentTimeMillis();
      totalGenerateTime += (endTime - startTime);
      startTime = endTime;
      verify(name, nonce, e, l, proof, l);
      endTime = System.currentTimeMillis();
      totalVerifyTime += (endTime - startTime);
    }
    log.info(name + " timing(" + e + "," + l + ") test " +
	     totalGenerateTime + " > " + totalVerifyTime + " msec");
    assertTrue(totalGenerateTime > totalVerifyTime);
  }

  public void TimingTwo(String name) throws IOException {
    byte[] nonce = new byte[24];
    int e = 63;
    int[] l = { 64, 256, 1024, 4096 };
    int[] proof;
    int numTries = 20;
    long[] totalGenerateTime = new long[l.length];
    long[] totalVerifyTime = new long[l.length];

    // Increasing l increases cost
    for (int j = 0; j < l.length; j++) {
      totalGenerateTime[j] = totalVerifyTime[j] = 0;
      for (int i = 0; i < numTries; i++) {
	rand.nextBytes(nonce);
	long startTime = System.currentTimeMillis();
	proof = generate(name, nonce, e, l[j], l[j]);
	long endTime = System.currentTimeMillis();
	totalGenerateTime[j] += (endTime - startTime);
	startTime = endTime;
	verify(name, nonce, e, l[j], proof, l[j]);
	endTime = System.currentTimeMillis();
	totalVerifyTime[j] += (endTime - startTime);
	log.debug(name + " generate l " + l[j] + " " + totalGenerateTime[j] +
		 " msec verify " + totalVerifyTime[j] + " msec");
      }
      if (j > 0) {
	log.info(name + " timing(" + e + ",[" + l[j] + "," + l[j-1] + "]) test " +
		 totalGenerateTime[j] + " > " + totalGenerateTime[j-1] + " msec");
	log.info(name + " timing(" + e + ",[" + l[j] + "," + l[j-1] + "]) test " +
		 totalVerifyTime[j] + " > " + totalVerifyTime[j-1] + " msec");
	assertTrue(totalGenerateTime[j] > totalGenerateTime[j-1]);
	if (true) {
	  // We'd like to be able to say this but it seems we can't
	  assertTrue(totalVerifyTime[j] > totalVerifyTime[j-1]);
	}
      }
    }
  }

  public void TimingThree(String name) throws IOException {
    byte[] nonce = new byte[24];
    int[] e = { 3, 15, 63 };
    int l = 64;
    int[] proof;
    int numTries = 10;
    long[] totalGenerateTime = new long[e.length];
    long[] totalVerifyTime = new long[e.length];

    // Increasing e increases cost & factor generate/verify
    for (int j = 0; j < e.length; j++) {
      totalGenerateTime[j] = totalVerifyTime[j] = 0;
      for (int i = 0; i < numTries; i++) {
	rand.nextBytes(nonce);
	long startTime = System.currentTimeMillis();
	proof = generate(name, nonce, e[j], l, 2*l);
	long endTime = System.currentTimeMillis();
	totalGenerateTime[j] += (endTime - startTime);
	startTime = endTime;
	verify(name, nonce, e[j], l, proof, 2*l);
	endTime = System.currentTimeMillis();
	totalVerifyTime[j] += (endTime - startTime);
	log.debug(name + "generate e " + e[j] + " " + totalGenerateTime[j] +
		 " msec verify " + totalVerifyTime[j] + " msec");
      }
      if (j > 0) {
	log.info(name + " timing([" + e[j] + "," + e[j-1] + "]," + l + ") test " +
		 totalGenerateTime[j] + " > " + totalGenerateTime[j-1] + " msec");
	// Increasing e increases generation cost
	assertTrue(totalGenerateTime[j] > totalGenerateTime[j-1]);
	// Increasing e increases factor by which generate costs more
	// than verify
	float oldFactor = ((float) totalGenerateTime[j-1]) /
	  ((float) totalVerifyTime[j-1]);
	float newFactor = ((float) totalGenerateTime[j]) /
	  ((float) totalVerifyTime[j]);
	log.info(name + " timing([" + e[j] + "," + e[j-1] + "]," + l + ") test " +
		 newFactor + " > " + oldFactor);
	assertTrue(newFactor > oldFactor);
      }
    }
  }    

  public void VerifyRandom(String name) throws IOException {
    byte[] nonce = new byte[24];
    int e = 7;
    int l = 32;
    int[] proof = new int[1];
    int numTries = 2048;
    int numYes = 0;
    int numNo = 0;

    // Verifying random proofs has about 1/(2**e) chance of success

    for (int i = 0; i < numTries; i++) {
      rand.nextBytes(nonce);
      proof[0] = i;
      if (verify(name, nonce, e, l, proof, 64))
	numYes++;
      else
	numNo++;
    }
    assertTrue(numYes > 0);
    float factor = ((float)(numYes + numNo)) / ((float)numYes);
    log.info(name + " random verify yes " + numYes + " no " + numNo +
	      " factor " + factor);
    assertTrue(factor > 6.5 && factor < 9.5);
  }

  private int[] generate(String name,
			 byte[] nonce,
			 int e,
			 int l,
			 int steps)
    throws IOException {
    int[] res = null;
    try{
      MemoryBoundFunction mbf =
	MemoryBoundFunction.getInstance(name, nonce, e, l, null, 0);
      pathsTried = 0;
      while (mbf.computeSteps(steps)) {
	assertFalse(mbf.done());
	pathsTried += steps;
      }
      pathsTried /= l;
      res = mbf.result();
      if (res.length > 0) {
	for (int i = 0; i < res.length; i++) {
	  log.debug(name + "generate [" + i + "] "  + res[i] +
		    " tries " + pathsTried);
	  if (res[i] < 0)
	    fail(name + ": proof < 0");
	  if (res[i] >= MemoryBoundFunction.basisSize())
	    fail(name + ": proof " + res[i] + " >= " +
		 MemoryBoundFunction.basisSize());
	}
      } else {
	log.debug("generate [" + res.length + "]  tries " + pathsTried);
	res = null;
      }
    } catch (NoSuchAlgorithmException ex) {
      fail(name + " threw " + ex.toString());
    }
    return (res);
  }

  private boolean verify(String name,
			 byte[] nonce,
			 int e,
			 int l,
			 int[] proof,
			 int steps)
    throws IOException {
    boolean ret = false;
    pathsTried = 0;
    if (proof != null) {
      assertTrue(proof.length >= 1);
      try {
	MemoryBoundFunction mbf2 =
	  MemoryBoundFunction.getInstance(name, nonce, e, l, proof, e);
	while (mbf2.computeSteps(steps)) {
	  assertFalse(mbf2.done());
	  pathsTried += steps;
	}
	pathsTried /= l;
	int[] res2 = mbf2.result();
	if (res2 == null)
	  log.debug("verify [" + proof.length + "] fails" + pathsTried + " tries");
	else {
	  ret = true;
	  for (int i = 0; i < res2.length; i++) {
	    log.debug("verify [" + i + "] " + res2[i] + " OK tries " + pathsTried);
	  }
	}
      } catch (NoSuchAlgorithmException ex) {
	fail(name + " threw " + ex.toString());
      }
    }      
    return (ret);
  }

  /**
   * Functional test of generate/verify pair
   */
  private void onePair(String name, int e, int l) throws IOException {
    // Make sure its configured
    assertTrue(f != null);
    assertTrue(MemoryBoundFunction.basisSize() > 0);
    long startTime = System.currentTimeMillis();
    byte[] nonce = new byte[64];
    rand.nextBytes(nonce);
    int[] proof = null;
    int numNulls = 0;
    for (int i = 0; i < 100 && proof == null; i++) {
      proof = generate(name, nonce, e, l, 8);
      numNulls++;
    }
    assertTrue(proof != null);
    assertTrue(proof.length > 0);
    boolean ret = verify(name, nonce, e, l, proof, 8);
    assertTrue(ret);
    if (numNulls > 2) {
      log.info(name + " onePair(" + e + "," + l + ") took " +
	     (System.currentTimeMillis() - startTime) + " msec " +
	       numNulls + " retries");
    } else {
      log.debug(name + " onePair(" + e + "," + l + ") took " +
		(System.currentTimeMillis() - startTime) + " msec");
    }
  }


}
