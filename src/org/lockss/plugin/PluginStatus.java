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

package org.lockss.plugin;

import java.util.*;
import org.lockss.app.*;
import org.lockss.daemon.status.*;
import org.lockss.util.*;
import org.lockss.config.*;
import org.lockss.plugin.definable.DefinablePlugin;

/** Base class for plugin status accessors, and static register/unregister
 */
class PluginStatus {
  static Logger log = Logger.getLogger("PluginStatus");
  final static String PLUGIN_TABLE = "Plugins";
  final static String PLUGIN_DETAIL = "PluginDetail";

  /** If true the definition of definable plugins will be displayed along
   * with its details. */
  static final String PARAM_PLUGIN_SHOWDEF =
    Configuration.PREFIX + "plugin.showDef";
  static final boolean DEFAULT_PLUGIN_DHOWDEF = false;

  LockssDaemon daemon;
  PluginManager mgr;

  static void register(LockssDaemon daemon, PluginManager mgr) {
    StatusService statusServ = daemon.getStatusService();
    statusServ.registerStatusAccessor(PLUGIN_TABLE,
				      new Plugins(daemon, mgr));
    statusServ.registerStatusAccessor(PLUGIN_DETAIL,
				      new PluginDetail(daemon, mgr));
  }

  static void unregister(LockssDaemon daemon) {
    StatusService statusServ = daemon.getStatusService();
    statusServ.unregisterStatusAccessor(PLUGIN_TABLE);
    statusServ.unregisterStatusAccessor(PLUGIN_DETAIL);
  }

  PluginStatus(LockssDaemon daemon, PluginManager mgr) {
    this.daemon = daemon;
    this.mgr = mgr;
  }

  String getPluginType(Plugin plugin) {
    if (mgr.isLoadablePlugin(plugin)) {
      return "Loadable";
    } else if (mgr.isInternalPlugin(plugin)) {
      return "Internal";
    } else {
      return "Builtin";
    }
  }
}

/**
 * Plugin summary.  For all plugins, lists name, version, id, and URL (if
 * loadable)
 */
class Plugins extends PluginStatus implements StatusAccessor {

  private final List sortRules =
    ListUtil.list(new StatusTable.SortRule("plugin",
					   CatalogueOrderComparator.SINGLETON));

  private final List colDescs =
    ListUtil.list(
		  new ColumnDescriptor("plugin", "Name",
				       ColumnDescriptor.TYPE_STRING),
		  new ColumnDescriptor("version", "Version",
				       ColumnDescriptor.TYPE_STRING),
		  new ColumnDescriptor("type", "Type",
				       ColumnDescriptor.TYPE_STRING),
// 		  new ColumnDescriptor("id", "Plugin ID",
// 				       ColumnDescriptor.TYPE_STRING),
		  new ColumnDescriptor("registry", "Registry",
				       ColumnDescriptor.TYPE_STRING)
// 		  new ColumnDescriptor("cu", "Loaded From",
// 				       ColumnDescriptor.TYPE_STRING)
		  );

  Plugins(LockssDaemon daemon, PluginManager mgr) {
    super(daemon, mgr);
  }

  public String getDisplayName() {
    return "Publisher Plugins";
  }

  public boolean requiresKey() {
    return false;
  }

  public void populateTable(StatusTable table) {
    table.setColumnDescriptors(colDescs);
    table.setDefaultSortRules(sortRules);
    table.setRows(getRows(table.getOptions().get(StatusTable.OPTION_INCLUDE_INTERNAL_AUS)));
  }

  public List getRows(boolean includeInternalAus) {
    List rows = new ArrayList();

    Collection plugins = mgr.getRegisteredPlugins();
    synchronized (plugins) {
      for (Iterator iter = plugins.iterator(); iter.hasNext(); ) {
	Plugin plugin = (Plugin)iter.next();
	if (!includeInternalAus && mgr.isInternalPlugin(plugin)) {
	  continue;
	}
	Map row = new HashMap();
	row.put("plugin", PluginDetail.makePlugRef(plugin.getPluginName(),
						   plugin));
	row.put("version", plugin.getVersion());
	row.put("id", plugin.getPluginId());
	row.put("type", getPluginType(plugin));
	if (mgr.isLoadablePlugin(plugin)) {
	  PluginManager.PluginInfo info = mgr.getLoadablePluginInfo(plugin);
	  if (info != null) {
// 	    row.put("cu", info.getCuUrl());
	    ArchivalUnit au = info.getRegistryAu();
	    if (au != null) {
	      row.put("registry", au.getName());
	    }
	  }
	}
	rows.add(row);
      }
    }
    return rows;
  }
}

/**
 * Details of single plugin
 */
class PluginDetail extends PluginStatus implements StatusAccessor {

  private final List sortRules =
    ListUtil.list(new StatusTable.SortRule("key", true));

  private final List colDescs =
    ListUtil.list(
		  new ColumnDescriptor("key", "Key",
				       ColumnDescriptor.TYPE_STRING),
		  new ColumnDescriptor("val", "Val",
				       ColumnDescriptor.TYPE_STRING)
		  );

  PluginDetail(LockssDaemon daemon, PluginManager mgr) {
    super(daemon, mgr);
  }

  public String getDisplayName() {
    return "Plugin Details";
  }

  private String getTitle(Plugin plug) {
    return "Plugin " + plug.getPluginName();
  }

  public boolean requiresKey() {
    return true;
  }

  public void populateTable(StatusTable table)
      throws StatusService.NoSuchTableException {
    String key = table.getKey();
    Plugin plug = mgr.getPlugin(key);
    if (plug == null) {
      throw new StatusService.NoSuchTableException("Unknown plugin: " + key);
    }
    populateTable(table, plug);
  }

  public void populateTable(StatusTable table, Plugin plug) {
    table.setTitle(getTitle(plug));
    table.setDefaultSortRules(sortRules);
    ExternalizableMap plugDef = null;
    if (plug instanceof DefinablePlugin) {
      DefinablePlugin dplug = (DefinablePlugin)plug;
      plugDef = dplug.getDefinitionMap();
      if (ConfigManager.getBooleanParam(PARAM_PLUGIN_SHOWDEF,
					DEFAULT_PLUGIN_DHOWDEF)) {
	table.setColumnDescriptors(colDescs);
	table.setRows(getRows(dplug, plugDef));
      }
    }
    table.setSummaryInfo(getSummaryInfo(plug, plugDef));
  }

  public List getRows(DefinablePlugin plug, ExternalizableMap plugDef) {
    List rows = new ArrayList();
    for (Iterator iter = plugDef.entrySet().iterator(); iter.hasNext(); ) {
      Map.Entry entry = (Map.Entry)iter.next();
      String key = (String)entry.getKey();
      String val = entry.getValue().toString();
      Map row = new HashMap();
      row.put("key", key);
      row.put("val", HtmlUtil.htmlEncode(val));
      rows.add(row);
    }
    return rows;
  }

  private List getSummaryInfo(Plugin plug, ExternalizableMap plugDef) {
    List res = new ArrayList();
    res.add(new StatusTable.SummaryInfo("Name",
					ColumnDescriptor.TYPE_STRING,
					plug.getPluginName()));

    res.add(new StatusTable.SummaryInfo("Id",
					ColumnDescriptor.TYPE_STRING,
					plug.getPluginId()));

    res.add(new StatusTable.SummaryInfo("Version",
					ColumnDescriptor.TYPE_STRING,
					plug.getVersion()));

    if (plugDef != null) {
      String notes = plugDef.getString(DefinablePlugin.CM_NOTES_KEY, null);
      if (notes != null) {
	res.add(new StatusTable.SummaryInfo("Notes",
					    ColumnDescriptor.TYPE_STRING,
					    HtmlUtil.htmlEncode(notes)));
      }
    }
    res.add(new StatusTable.SummaryInfo("Type",
					ColumnDescriptor.TYPE_STRING,
					getPluginType(plug)));
    res.add(new StatusTable.SummaryInfo("# AUs",
					ColumnDescriptor.TYPE_STRING,
					plug.getAllAus().size()));
    if (mgr.isLoadablePlugin(plug)) {
      PluginManager.PluginInfo info = mgr.getLoadablePluginInfo(plug);
      if (info != null) {
	String url = info.getCuUrl();
	if (url != null) {
	  CachedUrl cu = mgr.findMostRecentCachedUrl(url);
	  ArchivalUnit au = info.getRegistryAu();
	  res.add(new StatusTable.SummaryInfo("Plugin Registry",
					      ColumnDescriptor.TYPE_STRING,
					      au.getName()));
	  res.add(new StatusTable.SummaryInfo("URL",
					      ColumnDescriptor.TYPE_STRING,
					      url));
// 	  res.add(new StatusTable.SummaryInfo("Loaded from",
// 					      ColumnDescriptor.TYPE_STRING,
// 					      info.getJarUrl()));
	}
      }
    }
    return res;
  }

  // utility method for making a Reference
  public static StatusTable.Reference makePlugRef(Object value,
						  Plugin plug) {
    String key = PluginManager.pluginKeyFromId(plug.getPluginId());
    return new StatusTable.Reference(value, PLUGIN_DETAIL, key);
  }

}



