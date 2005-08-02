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
import java.util.*;
import org.lockss.util.*;
import org.lockss.util.urlconn.*;
import org.lockss.daemon.*;
import org.lockss.plugin.*;
import org.lockss.crawler.PermissionMap;

/**
 * This is a mock version of <code>UrlCacher</code> used for testing
 */

public class MockUrlCacher implements UrlCacher {
  private static Logger logger = Logger.getLogger("MockUrlCacher");

  private MockArchivalUnit au = null;
  private MockCachedUrlSet cus = null;
  private MockCachedUrl cu;
  private String url;
  private InputStream cachedIS;
  private InputStream uncachedIS;
  private CIProperties cachedProp;
  private CIProperties uncachedProp;

  private boolean shouldBeCached = false;
  private IOException cachingException = null;
  private RuntimeException cachingRuntimException = null;
  private int numTimesToThrow = 1;
  private int timesThrown = 0;
//   private boolean forceRefetch = false;
  private BitSet fetchFlags = new BitSet();
  private PermissionMapSource permissionMapSource;

  public MockUrlCacher(String url, MockArchivalUnit au){
    this.url = url;
    this.au = au;
    this.cus = (MockCachedUrlSet)au.getAuCachedUrlSet();
  }

  public String getUrl() {
    return url;
  }

  public CachedUrlSet getCachedUrlSet() {
    return cus;
  }

  public void setCachedUrlSet(MockCachedUrlSet cus) {
    this.cus = cus;
  }

  public boolean shouldBeCached(){
    return shouldBeCached;
  }

  public void setShouldBeCached(boolean shouldBeCached){
    this.shouldBeCached = shouldBeCached;
  }

  public CachedUrl getCachedUrl() {
    return cu;
  }

  public void setCachedUrl(MockCachedUrl cu) {
    this.cu = cu;
  }

  public void setConnectionPool(LockssUrlConnectionPool connectionPool) {
  }

  public void setProxy(String proxyHost, int proxyPort) {
  }

//   public void setForceRefetch(boolean force) {
//     this.forceRefetch = force;
//   }

  public void setFetchFlags(BitSet fetchFlags) {
    this.fetchFlags = fetchFlags;
  }

  public BitSet getFetchFlags() {
    return this.fetchFlags;
  }

  public void setRequestProperty(String key, String value) {
  }

  public void setRedirectScheme(RedirectScheme scheme) {
  }

  public void setupCachedUrl(String contents) throws IOException {
    MockCachedUrl cu = new MockCachedUrl(url);
    cu.setProperties(getUncachedProperties());
    if (contents != null) {
      cu.setContent(contents);
    }
    setCachedUrl(cu);
  }

  // Read interface - used by the proxy.

  public InputStream openForReading(){
    return cachedIS;
  }

  public CIProperties getProperties(){
    return cachedProp;
  }

    // Write interface - used by the crawler.

  public void storeContent(InputStream input,
			   CIProperties headers) throws IOException{
    cachedIS = input;
    cachedProp = headers;
    if (cus != null) {
      if (fetchFlags.get(UrlCacher.REFETCH_FLAG)) {
//       if (forceRefetch) {
	cus.addForceCachedUrl(url);
      } else {
	cus.addCachedUrl(url);
      }
    }
  }

  public void setCachingException(IOException e, int numTimesToThrow) {
    this.cachingException = e;
    this.numTimesToThrow = numTimesToThrow;
  }

  public void setCachingException(RuntimeException e, int numTimesToThrow) {
    this.cachingRuntimException = e;
  }

  private void throwExceptionIfSet() throws IOException {
    logger.debug3("Deciding whether to throw an exception");
    if (cachingException != null && timesThrown < numTimesToThrow) {
      timesThrown++;
      throw cachingException;
    } else {
      logger.debug3("No cachingException set");
    }
    if (cachingRuntimException != null) {
      // Get a stack trace from here, not from the test case where the
      // exception was created
      cachingRuntimException.fillInStackTrace();
      timesThrown++;
      throw cachingRuntimException;
    } else {
      logger.debug3("No cachingRuntimeException set");
    }
  }

  public int cache() throws IOException {
    int resultCode;

    if(cus == null) System.out.println("Warning cache() called with null cus");
    if (cus != null) {
      cus.signalCacheAttempt(url);
    }

    throwExceptionIfSet();

    if (cus != null) {
      if (fetchFlags.get(UrlCacher.REFETCH_FLAG)) {
// 	  if (forceRefetch) {
	cus.addForceCachedUrl(url);
      } else {
	cus.addCachedUrl(url);
      }
    }


    //XXX messy
    //content already there, so we should be doing a not modified response
//     if (!forceRefetch && cu.hasContent()) {
    if (cu.hasContent()) {
      return CACHE_RESULT_NOT_MODIFIED;
    } 
    
    //otherwise, mark that there is content and send a fetched response

    if (cu != null) {
      cu.setExists(true);
    }
    return CACHE_RESULT_FETCHED;
  }

  public InputStream getUncachedInputStream() throws IOException {
    throwExceptionIfSet();
    return uncachedIS;
  }

  public CIProperties getUncachedProperties() throws IOException {
    throwExceptionIfSet();
    return uncachedProp;
  }

  //mock specific acessors

  public void setCachedInputStream(InputStream is){
    cachedIS = is;
  }

  public void setUncachedInputStream(InputStream is){
    uncachedIS = is;
  }

  public void setCachedProperties(CIProperties prop){
    cachedProp = prop;
  }

  public void setUncachedProperties(CIProperties prop){
    uncachedProp = prop;
  }

  public String toString() {
    StringBuffer sb = new StringBuffer(url.length()+17);
    sb.append("[MockUrlCacher: ");
    sb.append(url);
    sb.append("]");
    return sb.toString();
  }

  /**
   * setPermissionMap
   *
   * @param pmSource PermissionMap source
   */
  public void setPermissionMapSource(PermissionMapSource pmSource) {
    this.permissionMapSource = pmSource;
  }

  public PermissionMapSource getPermissionMapSource() {
    return permissionMapSource;
  }
}
