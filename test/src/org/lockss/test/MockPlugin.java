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
import org.lockss.app.*;
import org.lockss.daemon.*;
import org.lockss.plugin.*;
import org.lockss.plugin.base.*;
import org.lockss.util.*;

/**
 * This is a mock version of <code>Plugin</code> used for testing
 */

public class MockPlugin extends BasePlugin implements PluginTestable {
  static Logger log = Logger.getLogger("MockPlugin");

  public static final String KEY = "org|lockss|test|MockPlugin";
  public static final String CONFIG_PROP_1 = "base_url";
  public static final String CONFIG_PROP_2 = "volume";

  private String pluginId = this.getClass().getName();
  private int initCtr = 0;
  private int stopCtr = 0;
  private Configuration auConfig;
  private Collection defKeys = null;

  private List aus = new ArrayList();

  public MockPlugin(){
  }

  /**
   * Called after plugin is loaded to give the plugin time to perform any
   * needed initializations.
   * @param daemon the LockssDaemon
   */
  public void initPlugin(LockssDaemon daemon) {
    super.initPlugin(daemon);
    initCtr++;
  }

  /**
   * Called when the application is stopping to allow the plugin to perform
   * any necessary tasks needed to cleanly halt
   */
  public void stopPlugin() {
    stopCtr++;
  }

  public String getPluginId() {
/*    if (pluginId == null) {
      return this.getClass().getName();
//       return super.getPluginId();
    } else {*/
      return pluginId;
    //}
  }

  public void setPluginId(String id) {
    pluginId = id;
  }

  public String getVersion() {
    return "MockVersion";
  }

    public String getPluginName() {
      return "Mock Plugin";
    }

  /**
   * Return the list of names of the Archival Units and volranges supported by
   * this plugin
   * @return a List of Strings
   */
  public List getSupportedTitles() {
    return ListUtil.list("MockSupportedTitle");
  }

  /**
   * Return the set of configuration properties required to configure
   * an archival unit for this plugin.
   * @return a List of strings which are the names of the properties for
   * which values are needed in order to configure an AU
   */
  public List getAuConfigProperties() {
    return ListUtil.list(CONFIG_PROP_1, CONFIG_PROP_2);
  }

  public Collection getDefiningConfigKeys() {
    if (defKeys != null) {
      return defKeys;
    }
    return ListUtil.list(CONFIG_PROP_1, CONFIG_PROP_2);
  }

  public void setDefiningConfigKeys(Collection keys) {
    defKeys = keys;
  }

  /**
   * Create an ArchivalUnit for the AU specified by the configuration.
   * @param auConfig Configuration object with values for all properties
   * returned by {@link #getAuConfigProperties()}
   * @return the ArchivalUnit
   * @throws ArchivalUnit.ConfigurationException
   */
  public ArchivalUnit createAu(Configuration auConfig)
      throws ArchivalUnit.ConfigurationException {
    log.debug("createAu(" + auConfig + ")");
    MockArchivalUnit au = new MockArchivalUnit();
    au.setConfiguration(auConfig);
    au.setPlugin(this);
    return au;
  }

  // MockPlugin methods, not part of Plugin interface

  public int getInitCtr() {
    return initCtr;
  }

  public int getStopCtr() {
    return stopCtr;
  }

  public void registerArchivalUnit(ArchivalUnit au) {
    aus.add(au);
  }

  public void unregisterArchivalUnit(ArchivalUnit au) {
    aus.remove(au);
  }

  public CachedUrlSet makeCachedUrlSet(ArchivalUnit owner,
				       CachedUrlSetSpec spec) {
    return new MockCachedUrlSet((MockArchivalUnit)owner, spec);
  }

  public CachedUrl makeCachedUrl(CachedUrlSet owner, String url) {
    return ((MockCachedUrlSet)owner).makeCachedUrl(url);
  }

  public UrlCacher makeUrlCacher(CachedUrlSet owner, String url) {
    return ((MockCachedUrlSet)owner).makeUrlCacher(url);
  }

}
