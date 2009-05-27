/*
 * $Id$
 */

/*

Copyright (c) 2000-2007 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.daemon;

import java.util.*;

import org.lockss.app.LockssApp;
import org.lockss.test.*;
import org.lockss.util.*;
import org.lockss.plugin.*;
import org.lockss.filter.*;
import org.lockss.extractor.*;
import org.lockss.rewriter.*;

/**
 * This is the test class for org.lockss.daemon.MimeTypeInfo
 */
public class TestMimeTypeInfo extends LockssTestCase {

  public void testAccessors() {
    MimeTypeInfo.Mutable mti = new MimeTypeInfo.Impl();
    assertNull(mti.getFilterFactory());
    assertNull(mti.getLinkExtractorFactory());
    assertNull(mti.getFetchRateLimiter());

    FilterFactory ff = new MockFilterFactory();
    mti.setFilterFactory(ff);
    assertSame(ff, mti.getFilterFactory());

    LinkExtractorFactory uf = new MockLinkExtractorFactory();
    mti.setLinkExtractorFactory(uf);
    assertSame(uf, mti.getLinkExtractorFactory());

    RateLimiter rl = new RateLimiter("1/1");
    mti.setFetchRateLimiter(rl);
    assertSame(rl, mti.getFetchRateLimiter());
    
    LinkRewriterFactory lr = new MockLinkRewriterFactory();
    mti.setLinkRewriterFactory(lr);
    assertSame(lr, mti.getLinkRewriterFactory());

    ArticleIteratorFactory ai = new MockArticleIteratorFactory();
    mti.setArticleIteratorFactory(ai);
    assertSame(ai, mti.getArticleIteratorFactory());

    Map factMap = new HashMap();
    MetadataExtractorFactory me = new MockMetadataExtractorFactory();
    factMap.put(MimeTypeInfo.DEFAULT_METADATA_TYPE, me);
    mti.setMetadataExtractorFactoryMap(factMap);
    assertSame(factMap, mti.getMetadataExtractorFactoryMap());
    assertSame(me, mti.getMetadataExtractorFactory());
    assertSame(me, mti.getMetadataExtractorFactory(MimeTypeInfo.DEFAULT_METADATA_TYPE));
    assertNull(mti.getMetadataExtractorFactory("BogusMetadataType"));

    MimeTypeInfo m2 = new MimeTypeInfo.Impl(mti);
    assertSame(ff, m2.getFilterFactory());
    assertSame(uf, m2.getLinkExtractorFactory());
    assertSame(rl, m2.getFetchRateLimiter());
    assertSame(lr, m2.getLinkRewriterFactory());
    assertSame(ai, m2.getArticleIteratorFactory());
    assertSame(factMap, m2.getMetadataExtractorFactoryMap());
    assertSame(me, m2.getMetadataExtractorFactory());

  }
}
