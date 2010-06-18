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

package org.lockss.test;

import java.io.*;
import java.util.*;
import org.lockss.extractor.*;
import org.lockss.util.*;
import org.lockss.daemon.*;
import org.lockss.plugin.*;

/** Framework for FileMetadataExtractor tests.  Subs must implement only
 * {@link #getFactory()} and {@link #getMimeType()}, and tests which call
 * {@link #extractFrom(String)}. */
public abstract class FileMetadataExtractorTestCase extends LockssTestCase {
  public static String URL = "http://www.example.com/";

  public static String MIME_TYPE_HTML = "text/html";
  public static String MIME_TYPE_XML = "application/xml";
  public static String MIME_TYPE_RAM = "audio/x-pn-realaudio";

  protected FileMetadataExtractor extractor = null;
  protected String encoding;
  protected MockArchivalUnit mau;
  protected MockCachedUrl cu;

  public void setUp() throws Exception {
    super.setUp();
    mau = new MockArchivalUnit();
    cu = new MockCachedUrl(getUrl(), mau);
    extractor = getFactory().createFileMetadataExtractor(getMimeType());
    encoding = getEncoding();
  }

  public abstract String getMimeType();
  public abstract FileMetadataExtractorFactory getFactory();

  public String getEncoding() {
    return Constants.DEFAULT_ENCODING;
  }

  public String getUrl() {
    return URL;
  }

  public void testEmptyFileReturnsNoMetadata() throws Exception {
      assertTrue(extractor.extract(cu).isEmpty());
  }

  public void testThrows() throws IOException, PluginException {
    try {
      extractor.extract(null);
      fail("Calling extract with a null InputStream should have thrown");
    } catch (IllegalArgumentException e) {
    }
  }

  protected ArticleMetadata extractFrom(String content) {
    ArticleMetadata ret = null;
    try {
      ret = extractor.extract(cu.addVersion(content));
    } catch (Exception e) {
      fail("extract threw " + e);
    }
    return ret;
  }
      
}
