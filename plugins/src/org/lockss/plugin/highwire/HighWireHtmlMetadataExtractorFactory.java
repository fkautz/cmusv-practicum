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

package org.lockss.plugin.highwire;

import java.io.*;

import org.lockss.util.*;
import org.lockss.daemon.*;
import org.lockss.extractor.*;
import org.lockss.plugin.*;


public class HighWireHtmlMetadataExtractorFactory
  implements FileMetadataExtractorFactory {
  static Logger log = Logger.getLogger("HighWireHtmlMetadataExtractorFactory");

  public FileMetadataExtractor createFileMetadataExtractor(String contentType)
      throws PluginException {
    return new HighWireHtmlMetadataExtractor();
  }

  public static class HighWireHtmlMetadataExtractor
    extends SimpleMetaTagMetadataExtractor {

    public ArticleMetadata extract(CachedUrl cu) throws IOException {
      ArticleMetadata ret = null;
      try {
        if (cu != null && cu.hasContent()) {
          ret = super.extract(cu);
          // HighWire doesn't prefix the DOI in dc.Identifier with doi:

          String content = ret.getProperty("dc.Identifier");
          if (content != null && !"".equals(content)) {
            ret.putDOI(content);
          }
          // Many HighWire journals either omit citation_issn
          // or have an empty citation_issn
          content = ret.getProperty("citation_issn");
          if (content != null && !"".equals(content)) {
            ret.putISSN(content);
          }
          content = ret.getProperty("citation_volume");
          if (content != null && !"".equals(content)) {
            ret.putVolume(content);
          }
          content = ret.getProperty("citation_issue");
          if (content != null && !"".equals(content)) {
            ret.putIssue(content);
          }
          content = ret.getProperty("citation_firstpage");
          if (content != null && !"".equals(content)) {
            ret.putStartPage(content);
          }
          content = ret.getProperty("citation_authors");
          if (content != null && !"".equals(content)) {
            if (content.contains(";")) { // if there is more than one authors then add them in a comma delimited list
              content = content.replaceAll(", ", " ");
              content = content.replaceAll(";", ",");
            }
            ret.putAuthor(content);
          }
          content = ret.getProperty("citation_title");
          if (content != null && !"".equals(content)) {
            ret.putArticleTitle(content);
          }
          content = ret.getProperty("citation_date");
          if (content != null && !"".equals(content)) {
            ret.putDate(content);
          }
        }
      }
      finally {
        AuUtil.safeRelease(cu);
      }
      return ret;
    }
  }

}
