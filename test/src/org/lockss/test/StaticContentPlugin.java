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
import java.net.*;
import java.security.MessageDigest;
import org.lockss.app.*;
import org.lockss.daemon.*;
import org.lockss.util.*;
import org.lockss.plugin.*;
import org.lockss.plugin.base.*;
import org.lockss.state.*;
import org.lockss.test.*;

import java.math.BigInteger;

/**
 * Minimal fully functional plugin capable of serving a little static content.
 */
public class StaticContentPlugin extends BasePlugin implements PluginTestable {
  static Logger log = Logger.getLogger("StaticContentPlugin");

  Map cuMap = new HashMap();

  public StaticContentPlugin() {
  }

  public String getVersion() {
    throw new UnsupportedOperationException("Not implemented");
  }

  public List getSupportedAUNames() {
    throw new UnsupportedOperationException("Not implemented");
  }

  public List getAUConfigProperties() {
    throw new UnsupportedOperationException("Not implemented");
  }

  public ArchivalUnit createAU(Configuration auConfig)
      throws ArchivalUnit.ConfigurationException {
    return new SAU(this);
  }

  public void registerArchivalUnit(ArchivalUnit au) {
    aus.add(au);
  }

  public void unregisterArchivalUnit(ArchivalUnit au) {
    aus.remove(au);
  }

  public Collection getDefiningConfigKeys() {
    return Collections.EMPTY_LIST;
  }

  public class SAU extends BaseArchivalUnit {

    protected SAU(Plugin myPlugin) {
      super(myPlugin);
    }

    public void setConfiguration(Configuration config)
	throws ArchivalUnit.ConfigurationException {
      super.setConfiguration(config);
    }

    public String getName() {
      return "Static Content AU";
    }

    public boolean shouldBeCached(String url) {
      return cuMap.containsKey(url);
    }

    public List getNewContentCrawlUrls() {
      throw new UnsupportedOperationException("Not implemented");
    }

    public CachedUrlSet makeCachedUrlSet(CachedUrlSetSpec cuss) {
      return new SCUS(this, cuss);
    }

    public CachedUrlSet cachedUrlSetFactory(ArchivalUnit owner,
					    CachedUrlSetSpec cuss) {
      throw new UnsupportedOperationException("Not implemented");
    }

    public CachedUrl cachedUrlFactory(CachedUrlSet owner, String url) {
      throw new UnsupportedOperationException("Not implemented");
    }

    public UrlCacher urlCacherFactory(CachedUrlSet owner, String url) {
      throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * Create a CU with content and store it in AU
     * @param owner the CUS owner
     * @param url the url
     * @param type the type
     * @param contents the contents
     */
    public void storeCachedUrl(CachedUrlSet owner, String url,
			       String type, String contents) {
      SCU scu = new SCU(owner, url, type, contents);
      cuMap.put(scu.getUrl(), scu);
    }

    public void storeCachedUrl(String url, String type, String contents) {
      storeCachedUrl(null, url, type, contents);
    }

    public String toString() {
      return "[sau: " + cuMap + "]";
    }
  }

  public class SCU extends BaseCachedUrl {
    private String contents = null;
    private Properties props = new Properties();

    public SCU(CachedUrlSet owner, String url) {
      super(owner, url);
    }

    /**
     * Create a CachedUrl with content
     * @param owner the CUS owner
     * @param url the url
     * @param type the type
     * @param contents the contents
     */
    public SCU(CachedUrlSet owner, String url,
	       String type, String contents) {
      this(owner, url);
      setContents(contents);
      props.setProperty("Content-Type", type);
    }

    private void setContents(String s) {
      contents = s;
      props.setProperty("Content-Length", ""+s.length());
    }

    public String getUrl() {
      return url;
    }

    public boolean hasContent() {
      return contents != null;
    }

    public boolean isLeaf() {
      throw new UnsupportedOperationException("Not implemented");
    }

    public InputStream openForReading() {
      return new StringInputStream(contents);
    }

    public InputStream openForHashing() {
      return openForReading();
    }

    public Reader getReader() {
      throw new UnsupportedOperationException("Not implemented");
    }

    public byte[] getContentSize() {
      BigInteger bigInt = new BigInteger(Integer.toString(contents.length()));
      return bigInt.toByteArray();
    }

    public Properties getProperties() {
      return props;
    }
  }

  class SCUS extends BaseCachedUrlSet {
    public SCUS(ArchivalUnit owner, CachedUrlSetSpec spec) {
      super(owner, spec);
    }

    public void storeActualHashDuration(long elapsed, Exception err) {
      throw new UnsupportedOperationException("Not implemented");
    }

    public Iterator flatSetIterator() {
      throw new UnsupportedOperationException("Not implemented");
    }

    public Iterator contentHashIterator() {
      throw new UnsupportedOperationException("Not implemented");
    }

    public boolean isLeaf() {
      throw new UnsupportedOperationException("Not implemented");
    }

    public CachedUrlSetHasher getContentHasher(MessageDigest hasher) {
      throw new UnsupportedOperationException("Not implemented");
    }

    public CachedUrlSetHasher getNameHasher(MessageDigest hasher) {
      throw new UnsupportedOperationException("Not implemented");
    }

    public long estimatedHashDuration() {
      return 1000;
    }


    public CachedUrl makeCachedUrl(String url) {
      CachedUrl res = (CachedUrl)cuMap.get(url);;
      log.debug("makeCachedUrl(" + url + ") = " + res);
      return (CachedUrl)cuMap.get(url);
    }

    public org.lockss.plugin.UrlCacher makeUrlCacher(String url) {
      throw new UnsupportedOperationException("Not implemented");
    }
  }

}
