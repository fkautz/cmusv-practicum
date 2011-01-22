/*
 * $Id: BaseArticleMetadataExtractor.java,v 1.2 2011/01/22 08:22:30 tlipkis Exp $
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

package org.lockss.extractor;

import java.io.*;

import org.lockss.util.*;
import org.lockss.daemon.*;
import org.lockss.plugin.*;

/** Base class for metadata extractors that return a single ArticleMetadata
 * or null.  This was the previous ArticleMetadataExtractor interface. */
public class BaseArticleMetadataExtractor
  implements ArticleMetadataExtractor {

  private static Logger log = Logger.getLogger("BaseArticleMetadataExtractor");


  protected String cuRole;
  protected boolean emitDefaultIfNone = false;

  public BaseArticleMetadataExtractor(String cuRole) {
    this.cuRole = cuRole;
  }

  protected void addAccessUrl(ArticleMetadata am, ArticleFiles af) {
    if (!am.hasValidValue(MetadataField.FIELD_ACCESS_URL)) {
      am.put(MetadataField.FIELD_ACCESS_URL, af.getFullTextUrl());
    }
  }

  protected CachedUrl getCuToExtract(ArticleFiles af) {
    return cuRole != null ? af.getRoleCu(cuRole) : af.getFullTextCu();
  }

  class MyEmitter implements FileMetadataExtractor.Emitter {
    private Emitter parent;
    private ArticleFiles af;
    private boolean isEmpty = true;

    MyEmitter(ArticleFiles af, Emitter parent) {
      this.af = af;
      this.parent = parent;
    }

    public void emitMetadata(CachedUrl cu, ArticleMetadata am) {
      isEmpty = false;
      addAccessUrl(am, af);
      parent.emitMetadata(af, am);
    }

    void setParentEmitter(Emitter parent) {
      this.parent = parent;
    }

    void setEmitDefaultIfNone(boolean val) {
      emitDefaultIfNone = val;
    }

    void finishArticle() {
      if (isEmpty && emitDefaultIfNone) {
	log.info("adding default");
	ArticleMetadata am = new ArticleMetadata();
	addAccessUrl(am, af);
	parent.emitMetadata(af, am);
      }
    }
  }

  public void extract(MetadataTarget target,
		      ArticleFiles af,
		      ArticleMetadataExtractor.Emitter emitter)
      throws IOException, PluginException {
    MyEmitter myEmitter = new MyEmitter(af, emitter);
    ArticleMetadata am = null;
    CachedUrl cu = getCuToExtract(af);
    if (cu != null) {
      FileMetadataExtractor me = cu.getFileMetadataExtractor(target);
      if (me != null) {
	me.extract(target, cu, myEmitter);
      }
      myEmitter.finishArticle();
    }
  }
}
