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

package org.lockss.plugin.projmuse;

import java.net.*;
import java.util.Properties;
import org.lockss.test.*;
import org.lockss.daemon.*;
import org.lockss.util.ListUtil;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.plugin.ArchivalUnit.*;

public class TestProjectMusePlugin extends LockssTestCase {
  private ProjectMusePlugin plugin;

  public void setUp() throws Exception {
    super.setUp();
    plugin = new ProjectMusePlugin();
    plugin.initPlugin(getMockLockssDaemon());
  }

  public void testGetAuNullConfig() throws ArchivalUnit.ConfigurationException {
    try {
      plugin.configureAu(null, null);
      fail("Didn't throw ArchivalUnit.ConfigurationException");
    } catch (ArchivalUnit.ConfigurationException e) { }
  }
  public void testCreateAu() {
    Properties props = new Properties();
    props.setProperty(ProjectMusePlugin.AUPARAM_BASE_URL, "http://www.example.com/");
    props.setProperty(ProjectMusePlugin.AUPARAM_JOURNAL_DIR, "blah2");
    props.setProperty(ProjectMusePlugin.AUPARAM_VOL, "322");

    ProjectMuseArchivalUnit au = null;
    try {
      au = makeAuFromProps(props);
    }
    catch (ConfigurationException ex) {
    }

  }

  private ProjectMuseArchivalUnit makeAuFromProps(Properties props)
      throws ArchivalUnit.ConfigurationException {
    Configuration config = ConfigurationUtil.fromProps(props);
    return (ProjectMuseArchivalUnit)plugin.configureAu(config, null);
  }

  public void testGetAuHandlesBadUrl()
      throws ArchivalUnit.ConfigurationException, MalformedURLException {
    Properties props = new Properties();
    props.setProperty(ProjectMusePlugin.AUPARAM_BASE_URL, "blah");
    props.setProperty(ProjectMusePlugin.AUPARAM_JOURNAL_DIR, "blah2");
    props.setProperty(ProjectMusePlugin.AUPARAM_VOL, "322");

    try {
      ProjectMuseArchivalUnit au = makeAuFromProps(props);
      fail ("Didn't throw InstantiationException when given a bad url");
    } catch (ArchivalUnit.ConfigurationException auie) {
      ConfigParamDescr.InvalidFormatException murle =
        (ConfigParamDescr.InvalidFormatException)auie.getNestedException();
      assertNotNull(auie.getNestedException());
    }
  }

  public void testGetAuConstructsProperAU()
      throws ArchivalUnit.ConfigurationException, MalformedURLException {
    Properties props = new Properties();
    props.setProperty(ProjectMusePlugin.AUPARAM_BASE_URL,
                      "http://www.example.com/");
    props.setProperty(ProjectMusePlugin.AUPARAM_JOURNAL_DIR, "journal_dir");
    props.setProperty(ProjectMusePlugin.AUPARAM_VOL, "322");

    ProjectMuseArchivalUnit au = makeAuFromProps(props);
    assertEquals("www.example.com, journal_dir, vol. 322", au.getName());
  }

  public void testGetPluginId() {
    assertEquals("org.lockss.plugin.projmuse.ProjectMusePlugin",
		 plugin.getPluginId());
  }

  public void testGetAuConfigProperties() {
    assertEquals(ListUtil.list(ConfigParamDescr.BASE_URL,
                               ConfigParamDescr.JOURNAL_DIR,
			       ConfigParamDescr.VOLUME_NUMBER),
		 plugin.getAuConfigProperties());
  }

  public void testGetDefiningProperties() {
    assertEquals(ListUtil.list(ConfigParamDescr.BASE_URL.getKey(),
                               ConfigParamDescr.JOURNAL_DIR.getKey(),
                               ConfigParamDescr.VOLUME_NUMBER.getKey()),
		 plugin.getDefiningConfigKeys());
  }
}
