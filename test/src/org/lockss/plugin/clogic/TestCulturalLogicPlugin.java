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

package org.lockss.plugin.clogic;

import java.net.*;
import java.util.Properties;
import org.lockss.test.*;
import org.lockss.daemon.*;
import org.lockss.util.ListUtil;
import org.lockss.plugin.ArchivalUnit;

public class TestCulturalLogicPlugin extends LockssTestCase {
  private CulturalLogicPlugin plugin;

  public void setUp() throws Exception {
    super.setUp();
    plugin = new CulturalLogicPlugin();
    plugin.initPlugin(getMockLockssDaemon());
  }

  public void testGetAuNullConfig() throws ArchivalUnit.ConfigurationException {
    try {
      plugin.configureAu(null, null);
      fail("Didn't throw ArchivalUnit.ConfigurationException");
    } catch (ArchivalUnit.ConfigurationException e) { }
  }

  private CulturalLogicArchivalUnit makeAuFromProps(Properties props)
      throws ArchivalUnit.ConfigurationException {
    Configuration config = ConfigurationUtil.fromProps(props);
    return (CulturalLogicArchivalUnit)plugin.configureAu(config, null);
  }

  public void testGetAuHandlesBadUrl()
      throws ArchivalUnit.ConfigurationException, MalformedURLException {
    Properties props = new Properties();
    props.setProperty(CulturalLogicPlugin.AUPARAM_BASE_URL, "blah");
    props.setProperty(CulturalLogicPlugin.AUPARAM_YEAR, "2002");

    try {
      CulturalLogicArchivalUnit au = makeAuFromProps(props);
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
    props.setProperty(CulturalLogicPlugin.AUPARAM_BASE_URL,
                      "http://www.example.com/clogic/");
    props.setProperty(CulturalLogicPlugin.AUPARAM_YEAR, "2002");

    CulturalLogicArchivalUnit au = makeAuFromProps(props);
    assertEquals("www.example.com/clogic/, 2002", au.getName());
  }

  public void testGetPluginId() {
    assertEquals("org.lockss.plugin.clogic.CulturalLogicPlugin",
		 plugin.getPluginId());
  }

  public void testGetAuConfigProperties() {
    assertEquals(ListUtil.list(ConfigParamDescr.BASE_URL,
			       ConfigParamDescr.YEAR),
		 plugin.getAuConfigProperties());
  }

  public void testGetDefiningProperties() {
    assertEquals(ListUtil.list(ConfigParamDescr.BASE_URL.getKey(),
                               ConfigParamDescr.YEAR.getKey()),
		 plugin.getDefiningConfigKeys());
  }
}
