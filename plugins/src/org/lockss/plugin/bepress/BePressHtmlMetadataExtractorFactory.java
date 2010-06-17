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

import org.lockss.util.*;
import org.lockss.daemon.*;
import org.lockss.extractor.*;
import org.lockss.plugin.*;


public class BePressHtmlMetadataExtractorFactory
  implements FileMetadataExtractorFactory {
  static Logger log = Logger.getLogger("BePressHtmlMetadataExtractorFactory");

  public FileMetadataExtractor
    createFileMetadataExtractor(String contentType)
      throws PluginException {
    return new BePressHtmlMetadataExtractor();
  }

  public static class BePressHtmlMetadataExtractor
    extends SimpleMetaTagMetadataExtractor {

    String[] bePressField = {
            "bepress_citation_doi",
            "bepress_citation_date",
            "bepress_citation_authors",
            "bepress_citation_title",
    };
    String[] dublinCoreField = {
            "dc.Identifier",
            "dc.Date",
            "dc.Contributor",
            "dc.Title",
    };
    String[] bePressField2 = {
            "bepress_citation_volume",
            "bepress_citation_issue",
            "bepress_citation_firstpage",
            "bepress_citation_authors",
            "bepress_citation_title",
    };

    public Metadata extract(CachedUrl cu) throws IOException {
      Metadata ret = super.extract(cu);
      for (int i = 0; i < bePressField.length; i++) {
        String content = ret.getProperty(bePressField[i]);
        if (content != null) {
          ret.setProperty(dublinCoreField[i], content);
          
          if (dublinCoreField[i].equalsIgnoreCase("dc.Identifier")) {
            ret.putDOI(content);
          }
        }
      }
      for (int i = 0; i < bePressField2.length; i++) {
        String content = ret.getProperty(bePressField2[i]);
        if (content != null) {
          switch (i) {
            case 0:
              ret.putVolume(content);
              break;
            case 1:
              ret.putIssue(content);
              break;
            case 2:
              ret.putStartPage(content);
              break;
            case 3:
              if(content.contains(";")){
                content = content.replaceAll(",", "");
                content = content.replaceAll(";", ",");
              }
              ret.putAuthor(content);              
              break;
            case 4:
              ret.putArticleTitle(content);
              break;
          }
        }
      }
      // The ISSN is not in a meta tag but we can find it in the text
      if (cu == null) {
        throw new IllegalArgumentException("extract(null)");
      }
      BufferedReader bReader =
              new BufferedReader(cu.openForReading());
      for (String line = bReader.readLine();
           line != null;
           line = bReader.readLine()) {
        line = line.trim();
        if (StringUtil.startsWithIgnoreCase(line, "<div id=\"issn\">")) {
          log.debug2("Line: " + line);
          addISSN(line, ret);
        }
      }
      IOUtil.safeClose(bReader);
      return ret;
    }

    protected void addISSN(String line, Metadata ret) {
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
      ret.putISSN(issn);
    }
  }
}
