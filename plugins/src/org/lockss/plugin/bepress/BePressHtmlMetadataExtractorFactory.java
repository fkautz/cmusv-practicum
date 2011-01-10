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

package org.lockss.plugin.bepress;

import java.io.*;
import java.util.*;

import org.lockss.util.*;
import org.lockss.daemon.*;
import org.lockss.extractor.*;
import org.lockss.plugin.*;

/**
 * One of the articles used to get the html source for this plugin is:
 * http://www.bepress.com/cppm/vol5/iss1/17/
 *
 */
public class BePressHtmlMetadataExtractorFactory
  implements FileMetadataExtractorFactory {
  static Logger log = Logger.getLogger("BePressHtmlMetadataExtractorFactory");

  public FileMetadataExtractor createFileMetadataExtractor(String contentType)
      throws PluginException {
    return new BePressHtmlMetadataExtractor();
  }

  public static class BePressHtmlMetadataExtractor
    extends SimpleMetaTagMetadataExtractor {

    // Map BePress-specific HTML meta tag names to cooked metadata fields
    private static Map tagMap = new HashMap();
    static {
      tagMap.put("bepress_citation_doi",
		 ListUtil.list(MetadataField.DC_FIELD_IDENTIFIER,
			       MetadataField.FIELD_DOI));
      tagMap.put("bepress_citation_date",
		 ListUtil.list(MetadataField.DC_FIELD_DATE,
			       MetadataField.FIELD_DATE));
      tagMap.put("bepress_citation_volume", MetadataField.FIELD_VOLUME);
      tagMap.put("bepress_citation_issue", MetadataField.FIELD_ISSUE);
      tagMap.put("bepress_citation_firstpage", MetadataField.FIELD_START_PAGE);
      tagMap.put("bepress_citation_authors",
		 new MetadataField(MetadataField.FIELD_AUTHOR) {
		   // XXX Change to handle lists properly
		   @Override
		   public String validate(String value) {
		     if (value.contains(";")) {
		       value = value.replaceAll(",", "");
		       value = value.replaceAll(";", ",");
		     }
		     return value;
		   }});;
      tagMap.put("bepress_citation_title", MetadataField.FIELD_ARTICLE_TITLE);
      tagMap.put("bepress_citation_journal_title", MetadataField.FIELD_JOURNAL_TITLE);
    }

    /**
     * Use SimpleMetaTagMetadataExtractor to extract raw metadata, map to
     * cooked fields, then extract the ISSN by reading the file.
     */
    public ArticleMetadata extract(CachedUrl cu) throws IOException {
      ArticleMetadata am = super.extract(cu);

      // extract metadata from BePress specific metadata tags
      am.cook(tagMap);

      // XXX Should this be conditional on
      //     !am.hasValidValue(MetadataField.FIELD_ISSN) ?

      // The ISSN is not in a meta tag but we can find it in the source
      // code. Here we need to some string manipulation and use some REGEX
      // to find the right index where the ISSN number is located.
      if (cu == null) {
	throw new IllegalArgumentException("extract(null)");
      }
      BufferedReader bReader = new BufferedReader(cu.openForReading());
      try {
	for (String line = bReader.readLine();
	     line != null;
	     line = bReader.readLine()) {
	  line = line.trim();
	  if (StringUtil.startsWithIgnoreCase(line, "<div id=\"issn\">") ||
	      StringUtil.startsWithIgnoreCase(line, "<p>ISSN:")) {
	    log.debug2("Line: " + line);
	    addISSN(line, am);
	  }
	}
      } finally {
	IOUtil.safeClose(bReader);
      }
      return am;
    }
		
    /**
     * Extract the ISSN from a line of HTML source code.
     * @param line The HTML source code that contains the ISSN and should have the following form:
     * <div id="issn"><p><!-- FILE: /main/production/doc/data/templates/www.bepress.com/proto_bpjournal/assets/issn.inc -->ISSN: 1934-2659 <!-- FILE:/main/production/doc/data/templates/www.bepress.com/proto_bpjournal/assets/footer.pregen (cont) --></p></div> 
     * @param ret The ArticleMetadat object used to put the ISSN in extracted metadata
     */
    protected void addISSN(String line, ArticleMetadata ret) {
      String issnFlag = "ISSN: ";
      int issnBegin = StringUtil.indexOfIgnoreCase(line, issnFlag);
      if (issnBegin <= 0) {
	log.debug(line + " : no " + issnFlag);
	return;
      }
      issnBegin += issnFlag.length();
      String issn = line.substring(issnBegin, issnBegin + 9);
      if (issn.length() < 9) {
	log.debug(line + " : too short");
	return;
      }			
      ret.put(MetadataField.FIELD_ISSN, issn);
    }
  }
}
