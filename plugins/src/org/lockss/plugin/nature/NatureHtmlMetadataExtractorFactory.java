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

package org.lockss.plugin.nature;

import java.io.*;
import java.util.*;

import org.lockss.util.*;
import org.lockss.daemon.*;
import org.lockss.extractor.*;
import org.lockss.plugin.*;

public class NatureHtmlMetadataExtractorFactory
  implements FileMetadataExtractorFactory {
  static Logger log = Logger.getLogger("NatureMetadataExtractorFactory");
 
  public FileMetadataExtractor createFileMetadataExtractor(String contentType)
      throws PluginException {
    return new NatureHtmlMetadataExtractor();
  }

  public static class NatureHtmlMetadataExtractor
    extends SimpleMetaTagMetadataExtractor {

    // Map BePress-specific HTML meta tag names to cooked metadata fields
    private static Map tagMap = new HashMap();
    static {
      tagMap.put("dc.creator", MetadataField.DC_FIELD_CONTRIBUTOR);
      // <meta name="citation_volume" content="19" />
      tagMap.put("citation_volume", MetadataField.FIELD_VOLUME);
      // <meta name="citation_issue" content="2" />
      tagMap.put("citation_issue", MetadataField.FIELD_ISSUE);
      // <meta name="citation_firstpage" content="119" />
      tagMap.put("citation_firstpage", MetadataField.FIELD_START_PAGE);
      tagMap.put("citation_doi", MetadataField.FIELD_DOI);
      // <meta name="prism.issn" content="0955-9930" />
      tagMap.put("prism.issn", MetadataField.FIELD_ISSN);
      // <meta name="prism.eIssn" content="1476-5489" />
      tagMap.put("prism.eIssn", MetadataField.FIELD_EISSN);
    }

    public ArticleMetadata extract(CachedUrl cu) throws IOException {
      ArticleMetadata am = super.extract(cu);
      am.cook(tagMap);
      return am;
    }
  }
}
