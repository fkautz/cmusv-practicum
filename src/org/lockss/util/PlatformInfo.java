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

package org.lockss.util;

import java.lang.reflect.*;
import java.util.*;
import java.text.*;
import java.io.*;
import java.net.*;

/** Utilities to communicate with platform to get info or take action not
 * possible from Java */
public class PlatformInfo {
  protected static Logger log = Logger.getLogger("PlatformInfo");
  private static final DecimalFormat percentFmt = new DecimalFormat("0%");

  static PlatformInfo instance;

  /** Return the singleton PlatformInfo instance */
  // no harm if happens in two threads, so not synchronized
  public static PlatformInfo getInstance() {
    if (instance == null) {
      String os = System.getProperty("os.name");
      if ("linux".equalsIgnoreCase(os)) {
	instance = new Linux();
      }
      if ("openbsd".equalsIgnoreCase(os)) {
	instance = new OpenBSD();
      }
      if (instance == null) {
	log.warning("No OS-specific PlatformInfo for '" + os + "'");
	instance = new PlatformInfo();
      }
    }
    return instance;
  }

  /** Return disk usage below path, in K (du -sk) */
  public long getDiskUsage(String path) {
    String cmd = "du -k -s " + path;
    if (log.isDebug2()) log.debug2("cmd: " + cmd);
    try {
      Process p = rt().exec(cmd);
      Reader rdr =
	new InputStreamReader(new BufferedInputStream(p.getInputStream()),
			      Constants.DEFAULT_ENCODING);
      String s;
      try {
	s = StringUtil.fromReader(rdr);
	int exit = p.waitFor();
	rdr.close();
	// any unreadable dirs cause exit=1; process if got any output
      } catch (IOException e) {
	log.error("Couldn't read from '" + cmd + "'", e);
	return -1;
      }
      List lines = StringUtil.breakAt(s, '\n');
      if (log.isDebug2()) {
	for (Iterator iter = lines.iterator(); iter.hasNext(); ) {
	  log.debug2("DU: " + (String)iter.next());
	}
      }
      if (lines == null || lines.isEmpty()) {
	return -1;
      }
      String ks = StringUtil.truncateAtAny((String)lines.get(0), " \t\n");
      return Long.parseLong(ks) * 1024;
    } catch (Exception e) {
      log.warning("DU(" + path + ")", e);
      return -1;
    }

  }


  public DF getDF(String path) throws UnsupportedException {
    String cmd = "df -k -P " + path;
    if (log.isDebug2()) log.debug2("cmd: " + cmd);
    try {
      Process p = rt().exec(cmd);
      Reader rdr =
	new InputStreamReader(new BufferedInputStream(p.getInputStream()),
			      Constants.DEFAULT_ENCODING);
      String s;
      try {
	s = StringUtil.fromReader(rdr);
	int exit = p.waitFor();
	rdr.close();
	if (exit != 0) {
	  return null;
	}
      } catch (IOException e) {
	log.error("Couldn't read from '" + cmd + "'", e);
	return null;
      }

      List lines = StringUtil.breakAt(s, '\n');
      if (log.isDebug2()) {
	for (Iterator iter = lines.iterator(); iter.hasNext(); ) {
	  log.debug2("DF: " + (String)iter.next());
	}
      }
      if (lines == null || lines.size() < 2) {
	return null;
      }
      return makeDFFromLine((String)lines.get(1));
    } catch (Exception e) {
      log.warning("DF(" + path + ")", e);
      return null;
    }

  }

  DF makeDFFromLine(String line) {
    String[] tokens = new String[6];
    StringTokenizer st = new StringTokenizer(line, " \t");
    int ntok = 0;
    while (st.hasMoreTokens()) {
      if (ntok > 5) {
	return null;
      }
      tokens[ntok++] = st.nextToken();
    }
    if (ntok != 6) {
      return null;
    }
    DF df = new DF();
    df.fs = tokens[0];
    df.size = getInt(tokens[1]);
    df.used = getInt(tokens[2]);
    df.avail = getInt(tokens[3]);
    df.percentString = tokens[4];
    df.mnt = tokens[5];
    try {
      df.percent = percentFmt.parse(df.percentString).doubleValue();
    } catch (ParseException e) {
    }
    if (log.isDebug2()) log.debug2(df.toString());
    return df;
  }

  int getInt(String s) throws NumberFormatException{
    try {
      return Integer.parseInt(s);
    } catch (NumberFormatException e) {
      log.warning("Illegal number in DF output: " + s);
      return 0;
    }
  }

  static Runtime rt() {
    return Runtime.getRuntime();
  }

  /** Linux implementation of platform-specific code */
  public static class Linux extends PlatformInfo {
  }

  /** OpenBSD implementation of platform-specific code */
  public static class OpenBSD extends PlatformInfo {
  }

  /** Struct holding disk space info (from df) */
  public static class DF {
    String fs;
    int size;
    int used;
    int avail;
    String percentString;
    double percent = -1.0;
    String mnt;

    public String getFs() {
      return fs;
    }
    public int getSize() {
      return size;
    }
    public int getUsed() {
      return used;
    }
    public int getAvail() {
      return avail;
    }
    public String getPercentString() {
      return percentString;
    }
    public double getPercent() {
      return percent;
    }
    public String getMnt() {
      return mnt;
    }
    public String toString() {
      return "[DF: " + fs + "]";
    }
  }

  /** Hack to get thread dump, using DebugUtils in test hierarchy, if it's
   * available.  Should be done differently, e.g., with 1.4 Thread.dumpStack()
   or by asking platform support process to do it. */
  public static void threadDump() {
    try {
      Class dbg = Class.forName("org.lockss.test.DebugUtils");
      Method meth = dbg.getDeclaredMethod("staticThreadDump", new Class[0]);
      if (log.isDebug2()) log.debug2("meth: " + meth);
      meth.invoke(null, null);
    } catch (Exception e) {
      log.warning("threadDump threw", e);
    }
  }

  /** Exception thrown if no implementation is available for the current
   * platform, or a platform-dependent error occurs.
   * In the case of an error, the original exception is available. */
  public static class UnsupportedException extends Exception {
    Throwable e;

    public UnsupportedException(String msg) {
      super(msg);
    }

    public UnsupportedException(String msg, Throwable e) {
      super(msg);
      this.e = e;
    }

    /** Return the nested Throwable */
    public Throwable getError() {
      return e;
    }
  }
}
