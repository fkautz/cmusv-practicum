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

package org.lockss.plugin.highwire;

import java.net.*;
import java.util.*;
import gnu.regexp.*;
import org.lockss.util.*;
import org.lockss.app.*;
import org.lockss.daemon.*;
import org.lockss.plugin.*;
import org.lockss.plugin.base.*;
import org.lockss.plugin.configurable.*;

/**
 * This is a first cut at making a HighWire plugin
 *
 * @author  Thomas S. Robertson
 * @version 0.0
 */

public class HighWirePlugin
    extends ConfigurablePlugin {
  public static final String LOG_NAME = "HighWirePlugin";
  public static final String PLUGIN_NAME = "HighWire Press";
  public static final String CURRENT_VERSION = "Pre-release";

  static final ConfigParamDescr PD_BASE = ConfigParamDescr.BASE_URL;
  static final ConfigParamDescr PD_VOL = ConfigParamDescr.VOLUME_NUMBER;

  // public only so test methods can use them
  public static final String AUPARAM_BASE_URL = PD_BASE.getKey();
  public static final String AUPARAM_VOL = PD_VOL.getKey();

  public void initPlugin(LockssDaemon daemon){
    //todo: we override initPlugin largely to manually load the values that
    // should be put into the configuration map when we load it from disk
    configurationMap.putString(CM_NAME_KEY, PLUGIN_NAME);
    configurationMap.putString(CM_VERSION_KEY, CURRENT_VERSION);
    configurationMap.putCollection(CM_CONFIG_PROPS_KEY,
                                   ListUtil.list(PD_BASE, PD_VOL));
    configurationMap.putCollection(CM_DEFINING_CONFIG_PROPS_KEY,
                                   ListUtil.list(AUPARAM_BASE_URL, AUPARAM_VOL));
    // then call the overridden initializaton.
    super.initPlugin(daemon);
  }

  public ArchivalUnit createAu(Configuration configInfo) throws ArchivalUnit.
      ConfigurationException {
    ArchivalUnit au = new HighWireArchivalUnit(this);
    au.setConfiguration(configInfo);
    return au;
  }
}
