/*
 * $Id$
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
package org.lockss.plugin.definable;

import java.util.*;
import java.io.*;

import org.lockss.config.*;
import org.lockss.plugin.*;
import org.lockss.plugin.base.*;
import org.lockss.plugin.wrapper.*;
import org.lockss.daemon.*;
import org.lockss.test.*;
import org.lockss.util.*;
import org.lockss.crawler.*;
import org.lockss.extractor.*;

/**
 * Mock ArticleIterator and MetadataExtrator factories and implementations
 */
public class MockFactories {
  static Logger log = Logger.getLogger("MockFactories");

  public static class ArtIterFact implements ArticleIteratorFactory {

    public Iterator<ArticleFiles> createArticleIterator(ArchivalUnit au,
							MetadataTarget target)
	throws PluginException {
      return CollectionUtil.EMPTY_ITERATOR;
    }
  }

  public static class MetaExtFact implements ArticleMetadataExtractorFactory {

    public ArticleMetadataExtractor
      createArticleMetadataExtractor(MetadataTarget target)
	throws PluginException {
      return new MetaExt();
    }
  }

  public static class XmlMetaExtFact implements FileMetadataExtractorFactory {

    public FileMetadataExtractor
      createFileMetadataExtractor(String contentType) {
      return new XmlMetaExt();
    }
  }

  public static class XmlRindMetaExtFact
    implements FileMetadataExtractorFactory {

    public FileMetadataExtractor
      createFileMetadataExtractor(String contentType) {
      return new XmlMetaExt();
    }
  }

  public static class MetaExt implements ArticleMetadataExtractor {
    public Metadata extract(ArticleFiles af) {
      return null;
    }
  }

  public static class XmlMetaExt implements FileMetadataExtractor {
    public Metadata extract(CachedUrl cu) {
      return null;
    }
  }
}
