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

package org.lockss.test;
import java.util.*;
import java.io.*;
import org.lockss.plugin.*;
import org.lockss.crawler.ContentParser;

public class MockContentParser implements ContentParser {

  private String urlToReturn = null;
  private HashMap urlSets = new HashMap();

  private Set srcUrls = new HashSet();

  public MockContentParser() {
  }

  public void parseForUrls(Reader reader, String srcUrl,
			   ContentParser.FoundUrlCallback cb) {
    srcUrls.add(srcUrl);
    if (urlToReturn != null) {
      cb.foundUrl(urlToReturn);
    } else if (urlSets != null) {
      Set setToAdd = (Set)urlSets.get(srcUrl);
      if (setToAdd != null) {
	Iterator it = setToAdd.iterator();
	while(it.hasNext()) {
	  cb.foundUrl((String)it.next());
	}
      }
    }

  }

  public Set getSrcUrls() {
    return srcUrls;
  }

  /*
  public void parseForUrls(CachedUrl cu, ContentParser.FoundUrlCallback cb)
      throws IOException {
    if (urlToReturn != null) {
      cb.foundUrl(urlToReturn);
    } else if (urlSets != null) {
      Set setToAdd = (Set)urlSets.get(cu.getUrl());
      if (setToAdd != null) {
	Iterator it = setToAdd.iterator();
	while(it.hasNext()) {
	  cb.foundUrl((String)it.next());
	}
      }
    }
    }*/

  public void setUrlToReturn(String url) {
    this.urlToReturn = url;
  }

  public void addUrlSetToReturn(String url, Set urls) {
    urlSets.put(url, urls);
  }

}
