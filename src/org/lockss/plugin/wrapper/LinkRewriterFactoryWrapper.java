/*
 * $Id: LinkRewriterFactoryWrapper.java,v 1.7 2011/02/14 00:06:09 tlipkis Exp $
 */

/*

Copyright (c) 2000-2010 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.plugin.wrapper;
import java.io.*;
import org.lockss.daemon.*;
import org.lockss.plugin.*;
import org.lockss.rewriter.*;
import org.lockss.servlet.*;

/** Error catching wrapper for LinkRewriterFactory */
public class LinkRewriterFactoryWrapper
  implements LinkRewriterFactory, PluginCodeWrapper {

  LinkRewriterFactory inst;

  public LinkRewriterFactoryWrapper(LinkRewriterFactory inst) {
    this.inst = inst;
  }

  public Object getWrappedObj() {
    return inst;
  }

  public InputStream createLinkRewriter(String mimeType,
					ArchivalUnit au,
					InputStream is,
					String encoding,
					String url,
					ServletUtil.LinkTransform xform)
      throws PluginException, IOException {
    try {
      return inst.createLinkRewriter(mimeType, au, is, encoding,
					   url, xform);
    } catch (LinkageError e) {
      throw new PluginException.LinkageError(e);
    }
  }

  public String toString() {
    return "[W: " + inst.toString() + "]";
  }

  static class Factory implements WrapperFactory {
    public Object wrap(Object obj) {
      return new LinkRewriterFactoryWrapper((LinkRewriterFactory)obj);
    }
  }
}
