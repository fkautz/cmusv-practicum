/*
 * $Id$
 */

/*

Copyright (c) 2000-2003 Board of Trustees of Leland Stanford Jr. University,
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
import org.lockss.plugin.*;

public class RamLinkExtractor implements LinkExtractor {
  BufferedReader reader = null;
  String source = null;
  String dest = null;

  public static RamLinkExtractor makeBasicRamLinkExtractor() {
    return new RamLinkExtractor();
  }

  public static RamLinkExtractor makeTranslatingRamLinkExtractor(String source,
								 String dest) {
    return new RamLinkExtractor(source, dest);
  }

  private RamLinkExtractor() {
  }

  /**
   * Creates a RamLinkExtractor which will find rtsp:// urls starting with
   * source and replace that string with dest
   */
  private RamLinkExtractor(String source, String dest) {
    this.source = source;
    this.dest = dest;
  }


  public void extractUrls(ArchivalUnit au, InputStream in, String encoding,
			  String srcUrl, LinkExtractor.Callback cb)
      throws IOException {

    if (in == null) {
      throw new IllegalArgumentException("Called with null InputStream");
    }
    if (cb == null) {
      throw new IllegalArgumentException("Called with null callback");
    }
    BufferedReader bReader =
      new BufferedReader(StreamUtil.getReader(in, encoding));
    for (String line = bReader.readLine();
	 line != null;
	 line = bReader.readLine()) {
      line = line.trim();
      if (StringUtil.startsWithIgnoreCase(line, "http://")) {
	cb.foundLink(UrlUtil.stripQuery(line));
      } else if (source != null
		 && dest != null
		 && StringUtil.startsWithIgnoreCase(line, source)) {
	line = translateString(line, source, dest);
	cb.foundLink(UrlUtil.stripQuery(line));
      }
    }
    IOUtil.safeClose(bReader);
  }

  //presumes line starts with source (ignoring case)
  private static String translateString(String line, String source,
					String dest) {
    StringBuffer sb = new StringBuffer();
    sb.append(dest);
    sb.append(line.substring(source.length()));
    return sb.toString();
  }

}
