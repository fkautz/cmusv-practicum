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
package org.lockss.plugin.configurable;

import org.lockss.plugin.base.*;
import org.lockss.plugin.*;
import org.lockss.daemon.*;
import org.lockss.app.*;
import org.lockss.util.*;
import java.util.*;
import java.io.FileNotFoundException;

/**
 * <p>ConfigurablePlugin: a plugin which uses the data stored in an
*  ExternalizableMap to configure it self.</p>
 * @author Claire Griffin
 * @version 1.0
 */

public class ConfigurablePlugin extends BasePlugin {
  // configuration map keys
  static final protected String CM_NAME_KEY = "plugin_name";
  static final protected String CM_VERSION_KEY = "plugin_version";
  static final protected String CM_CONFIG_PROPS_KEY = "plugin_config_props";

  static final String DEFAULT_PLUGIN_VERSION = "1";

  String mapName = null;

  static Logger log = Logger.getLogger("ConfigurablePlugin");

  protected ExternalizableMap configurationMap = new ExternalizableMap();

  public void initPlugin(LockssDaemon daemon, String extMapName){
    mapName = extMapName;
    // load the configuration map from jar file
    String mapFile = "/" + mapName.replace('.','/') + ".xml";
    try {
      configurationMap.loadMapFromResource(mapFile);
    }
    catch(FileNotFoundException fnfe) {
      log.warning(fnfe.toString());
    }

   // then call the overridden initializaton.
    super.initPlugin(daemon);
  }

  public String getPluginName() {
    String default_name = StringUtil.shortName(getPluginId());
    return configurationMap.getString(CM_NAME_KEY, default_name);
  }

  public String getVersion() {
    return configurationMap.getString(CM_VERSION_KEY, DEFAULT_PLUGIN_VERSION);
  }

  public List getAuConfigDescrs() {
    return (List) configurationMap.getCollection(CM_CONFIG_PROPS_KEY,
                                                 Collections.EMPTY_LIST);
  }

  public ArchivalUnit createAu(Configuration auConfig)
      throws ArchivalUnit.ConfigurationException {
    ArchivalUnit au = new ConfigurableArchivalUnit(this);
    au.setConfiguration(auConfig);
    return au;
  }

  protected ExternalizableMap getConfigurationMap() {
    return configurationMap;
  }

  public String getPluginId() {
    String class_name;
    if(mapName != null) {
      class_name = mapName;
    }
    else {
      class_name = this.getClass().getName();
    }
    return class_name;
  }
}
