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

import java.io.*;
import java.math.*;

import org.lockss.plugin.*;
import org.lockss.util.*;

/**
 * This is a mock version of <code>CachedUrl</code> used for testing
 *
 * @author  Thomas S. Robertson
 * @version 0.0
 */

public class MockCachedUrl implements CachedUrl {
  private ArchivalUnit au;
  private String url;
  private InputStream cachedIS;
  private CIProperties cachedProp;

  private boolean isLeaf = true;

  private boolean doesExist = false;
  private String content = null;
  private long contentSize = -1;
  private Reader reader = null;
  private String cachedFile = null;
  private boolean isResource;

  public MockCachedUrl(String url) {
    this.url = url;
  }

  public MockCachedUrl(String url, ArchivalUnit au) {
    this(url);
    this.au = au;
  }

  /**
   * Construct a mock cached URL that is backed by a file.
   *
   * @param url
   * @param file The name of the file to load.
   * @param isResource If true, load the file name as a
   * resource.  If false, load as a file.
   */
  public MockCachedUrl(String url, String file, boolean isResource) {
    this(url);
    this.isResource = isResource;
    cachedFile = file;
  }

  public ArchivalUnit getArchivalUnit() {
    return au;
  }

  public String getUrl() {
    return url;
  }

  public CachedUrl getCuVersion(int version) {
    throw new UnsupportedOperationException("Not implemented");
  }

  public CachedUrl[] getCuVersions() {
    throw new UnsupportedOperationException("Not implemented");
  }

  public CachedUrl[] getCuVersions(int maxVersions) {
    throw new UnsupportedOperationException("Not implemented");
  }

  public int getVersion() {
    return 1;
  }

  public Reader openForReading() {
    if (content != null) {
      return new StringReader(content);
    }
    if (reader != null) {
      return reader;
    }

    return new StringReader("");
  }

  public boolean hasContent() {
    return doesExist || content != null;
  }

  public boolean isLeaf() {
    return isLeaf;
  }

  public void setIsLeaf(boolean isLeaf) {
    this.isLeaf = isLeaf;
  }

  public int getType() {
    return CachedUrlSetNode.TYPE_CACHED_URL;
  }

  public void setExists(boolean doesExist) {
    this.doesExist = doesExist;
  }

  // Read interface - used by the proxy.

  public InputStream getUnfilteredInputStream() {
    try {
      if (cachedFile != null) {
	if (isResource) {
	  return ClassLoader.getSystemClassLoader().
	    getResourceAsStream(cachedFile);
	} else {
	  return new FileInputStream(cachedFile);
	}
      }
    } catch (IOException ex) {
      return null;
    }
    if (content != null) {
      return new StringInputStream(content);
    }
    return cachedIS;
  }

  public InputStream openForHashing() {
    return getUnfilteredInputStream();
  }

  public long getContentSize() {
    if (contentSize != -1) {
      return contentSize;
    }
    return content == null ? 0 : content.length();
  }

  public CIProperties getProperties(){
    return cachedProp;
  }

    // Write interface - used by the crawler.

  public void storeContent(InputStream input, CIProperties headers)
      throws IOException {
    cachedIS = input;
    cachedProp = headers;
  }

  //mock specific acessors

  public void setInputStream(InputStream is){
    cachedIS = is;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public void setContentSize(long size) {
    this.contentSize = size;
  }

  public void setReader(Reader reader) {
    this.reader = reader;
  }

  public void setProperties(CIProperties prop){
    cachedProp = prop;
  }

  public void release() {
  }

  public String toString() {
    StringBuffer sb = new StringBuffer(url.length()+17);
    sb.append("[MockCachedUrl: ");
    sb.append(url);
    sb.append("]");
    return sb.toString();
  }
}
