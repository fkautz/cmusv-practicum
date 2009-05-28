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

package org.lockss.plugin.bepress;

import java.util.*;
import org.lockss.util.*;
import org.lockss.daemon.*;
import org.lockss.plugin.*;
import org.lockss.config.*;
import org.lockss.daemon.PluginException;

public class BePressArticleIteratorFactory implements ArticleIteratorFactory {
  static Logger log = Logger.getLogger("BePressArticleIteratorFactory");

  /*
   * The BePress URL structure means that the HTML for an article
   * is normally at a URL like http://www.bepress.com/bis/vol3/iss3/art7
   * but is sometimes at a URL like
   * http://www.bepress.com/bejte/frontiers/vol1/iss1/art1 where "frontiers"
   * is an apparently arbitrary word.  So for now we just use the journal
   * abbreviation as the subTreeRoot.
   */
  protected String subTreeRoot;

  public BePressArticleIteratorFactory() {
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
    String abbr;
    abbr = au.getConfiguration().get(ConfigParamDescr.JOURNAL_ABBR.getKey());
    // int vol;
    // try {
    //   vol = au.getConfiguration().getInt(ConfigParamDescr.VOLUME_NUMBER.getKey());
    // } catch (Configuration.InvalidParam ex) {
    //   throw new PluginException("BePressArticleIteratorFactory: " + ex);
    // }
    subTreeRoot = abbr;
    log.debug("createArticleIterator(" + mimeType + "," + au.toString() +
              ") " + subTreeRoot);
    return new SubTreeArticleIterator(mimeType, au, subTreeRoot);
  }
}
