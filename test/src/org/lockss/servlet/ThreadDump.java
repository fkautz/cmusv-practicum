// ========================================================================
// $Id$
// ========================================================================

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

package org.lockss.servlet;

import javax.servlet.http.*;
import javax.servlet.*;
import java.io.*;
import java.util.*;
import java.net.*;
import java.text.*;
//  import com.mortbay.servlet.*;
//  import org.mortbay.util.*;
import org.mortbay.html.*;
import org.mortbay.tools.*;
import org.lockss.util.*;
import org.lockss.daemon.*;
import org.lockss.test.*;

/** ThreadDump servlet
 */
public class ThreadDump extends LockssServlet {

  private DebugUtils dbg;

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
    dbg = DebugUtils.getInstance();
  }

  /** Handle a request */
  public void lockssHandleRequest() throws IOException {
    dbg.threadDump();
    PrintWriter wrtr = resp.getWriter();
    resp.setContentType("text/plain");

    wrtr.println("JVM threads dumped to stderr");

    writeDump(wrtr);
  }

  private void writeDump(PrintWriter wrtr) {
    // Even if we could rely on System.err still pointing to JVM's original
    // stderr, there doesn't seem to be a way to get an input stream from it.
  }
}
