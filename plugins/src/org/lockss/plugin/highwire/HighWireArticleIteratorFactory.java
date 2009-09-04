/*
 * $Id$
 */

/*

Copyright (c) 2000-2009 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.plugin.highwire;

import java.util.*;
import java.util.regex.*;

import org.lockss.util.*;
import org.lockss.plugin.*;
import org.lockss.daemon.PluginException;

public class HighWireArticleIteratorFactory implements ArticleIteratorFactory {
  static Logger log = Logger.getLogger("HighWireArticleIterator");

  /*
   * The HighWire URL structure means that the PDF for an article
   * is at a URL like http://apr.sagepub.com/cgi/reprint/34/2/135
   * where 34 is a volume name, 2 an issue name and 135 a page name.
   * In the best of cases all three are integers but they can all be
   * strings, e.g. OUP's English Historical Review uses Roman numerals
   * for volume names, many HighWire titles have supplementary issues
   * named supp_1, supp_2, etc. and most APS journals have page names
   * prepended with a letter reminiscent of the journal title's main
   * keyword.
   */
  protected String subTreeRoot = "cgi/reprint";
  protected Pattern pat = Pattern.compile("/[^/]+/[^/]+/[^/]+",
				  Pattern.CASE_INSENSITIVE);

  public HighWireArticleIteratorFactory() {
  }
  /**
   * Create an Iterator that iterates through the AU's articles, pointing
   * to the appropriate CachedUrl of type mimeType for each, or to the plugin's
   * choice of CachedUrl if mimeType is null
   * @param mimeType the MIME type desired for the CachedUrls
   * @param au the ArchivalUnit to iterate through
   * @return the ArticleIterator
   */
  public Iterator createArticleIterator(String mimeType, ArchivalUnit au)
      throws PluginException {
    log.debug("createArticleIterator(" + mimeType + "," + au.toString() +
              ") " + subTreeRoot);
    return new SubTreeArticleIterator(mimeType, au, subTreeRoot, pat);
  }

}
