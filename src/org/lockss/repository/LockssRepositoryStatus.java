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

package org.lockss.repository;

import java.util.*;
import java.io.*;
import org.lockss.daemon.*;
import org.lockss.daemon.status.*;
import org.lockss.plugin.*;
import org.lockss.util.*;
import org.lockss.app.*;
import org.lockss.state.*;

/**
 * Collect and report the status of the LockssRepository
 */
public class LockssRepositoryStatus extends BaseLockssManager {
  public static final String SERVICE_STATUS_TABLE_NAME = "RepositoryTable";
  public static final String AU_STATUS_TABLE_NAME =
    ArchivalUnitStatus.AU_STATUS_TABLE_NAME;

  static final String FOOT_DELETED =
    "An AU that has been deleted (unconfigured), " +
    "whose contents are still in the repository. " +
    "If the AU were reconifigured, the contents would become visible.";

  static final String FOOT_ORPHANED =
    "An AU that was created with an incompatible plugin, " +
    "and cannot be restored with any currently available plugins.";

  private static Logger log = Logger.getLogger("RepositoryStatus");


  public void startService() {
    super.startService();
    StatusService statusServ = theDaemon.getStatusService();
    statusServ.registerStatusAccessor(SERVICE_STATUS_TABLE_NAME,
				      new RepoStatusAccessor(theDaemon));
  }

  public void stopService() {
    StatusService statusServ = theDaemon.getStatusService();
    statusServ.unregisterStatusAccessor(SERVICE_STATUS_TABLE_NAME);
    super.stopService();
  }

  protected void setConfig(Configuration config, Configuration oldConfig,
                           Set changedKeys) {
  }

  static class RepoStatusAccessor implements StatusAccessor {
    private static LockssDaemon daemon;
    private PluginManager pluginMgr;

    private static final List columnDescriptors = ListUtil.list
      (new ColumnDescriptor("dir", "Dir", ColumnDescriptor.TYPE_STRING),
       new ColumnDescriptor("au", "AU", ColumnDescriptor.TYPE_STRING),
       new ColumnDescriptor("status", "Status", ColumnDescriptor.TYPE_STRING),
       new ColumnDescriptor("plugin", "Plugin", ColumnDescriptor.TYPE_STRING),
       new ColumnDescriptor("params", "Params", ColumnDescriptor.TYPE_STRING)
//        new ColumnDescriptor("auid", "AU Key", ColumnDescriptor.TYPE_STRING)
       );

    private static final List sortRules =
      ListUtil.list(new StatusTable.SortRule("dir", true));

    RepoStatusAccessor(LockssDaemon daemon) {
      this.daemon = daemon;
      pluginMgr = daemon.getPluginManager();
    }

    public String getDisplayName() {
      return "Repositories";
    }

    public void populateTable(StatusTable table)
        throws StatusService.NoSuchTableException {
      table.setColumnDescriptors(columnDescriptors);
      table.setDefaultSortRules(sortRules);
      table.setRows(getRows());
      table.setSummaryInfo(getSummaryInfo());
    }

    public boolean requiresKey() {
      return false;
    }

    private List getRows() {
      List rows = new ArrayList();
      TreeSet roots = new TreeSet();
      List repos = daemon.getConfigManager().getRepositoryList();
      for (Iterator iter = repos.iterator(); iter.hasNext(); ) {
	String repoSpec = (String)iter.next();
	if (repoSpec.startsWith("local:")) {
	  roots.add(repoSpec.substring(6));
	}
      }
      roots.add(getDefaultRepositoryLocation());
      for (Iterator iter = roots.iterator(); iter.hasNext(); ) {
	String root = (String)iter.next();
	addRows(rows, LockssRepositoryImpl.extendCacheLocation(root));
      }
      return rows;
    }

    String getDefaultRepositoryLocation() {
      return Configuration.getParam(LockssRepositoryImpl.PARAM_CACHE_LOCATION);
    }

    private void addRows(Collection rows, String root) {
      File dir = new File(root);
      File[] subs = dir.listFiles();
      if (subs != null) {
	for (int ix = 0; ix < subs.length; ix++) {
	  File sub = subs[ix];
	  String auid = null;
	  if (sub.isDirectory()) {
	    File auidfile = new File(sub, LockssRepositoryImpl.AU_ID_FILE);
	    if (auidfile.exists()) {
	      Properties props = propsFromFile(auidfile);
	      if (props != null) {
		auid = props.getProperty(LockssRepositoryImpl.AU_ID_PROP);
	      }
	    }
	    rows.add(makeRow(sub, auid));
	  }
	}
      }
    }

    Map makeRow(File dir, String auid) {
      Map row = new HashMap();
      row.put("dir", dir.toString());
      if (auid == null) {
	row.put("status", "No AUID");
      } else {
	String auKey = PropKeyEncoder.decode(pluginMgr.auKeyFromAuId(auid));
	row.put("auid", auKey);
	row.put("plugin", PluginManager.pluginNameFromAuId(auid));
	ArchivalUnit au = pluginMgr.getAuFromId(auid);
	String name = null;
	if (au != null) {
	  name = au.getName();
	  Configuration auConfig = au.getConfiguration();
	  String repoSpec = auConfig.get(PluginManager.AU_PARAM_REPOSITORY);
	  if (repoSpec == null) {
	    if (!dir.toString().startsWith(getDefaultRepositoryLocation())) {
	      au = null;
	    }
	  } else if (repoSpec.startsWith("local:")) {
	    String root = repoSpec.substring(6);
	    if (!dir.toString().startsWith(root)) {
	      au = null;
	    }
	  }
	}


	if (au != null) {
	  row.put("status", "Active");
	  row.put("au", new StatusTable.Reference(name,
						  AU_STATUS_TABLE_NAME,
						  auid));
	  Configuration config = au.getConfiguration();
	  row.put("params", config);
	} else {

	  Configuration config = pluginMgr.getStoredAuConfiguration(auid);
	  if (config == null | config.isEmpty()) {
	    Properties auidProps = null;
	    try {
	      auidProps = PropUtil.canonicalEncodedStringToProps(auKey);
	    } catch (Exception e) {
	      log.warning("Couldn't decode AUKey: " + auKey, e);
	    }
	    row.put("status",
		    (isOrphaned(auid, auidProps) ? "Orphaned" : "Deleted"));
	    if (auidProps != null) {
	      row.put("params", PropUtil.canonicalEncodedStringToProps(auKey));
	    }
	  } else {
	    row.put("status",
		    (config.getBoolean(PluginManager.AU_PARAM_DISABLED, false)
		     ? "Inactive" : "Deleted"));
	    row.put("au", config.get(PluginManager.AU_PARAM_DISPLAY_NAME));
	    row.put("params", config);
	  }
	  if (name != null) {
	    row.put("au", name);
	  }
	}
      }
      return row;
    }

    boolean isOrphaned(String auid, Properties auidProps) {
      String pluginKey = 
	PluginManager.pluginKeyFromId(PluginManager.pluginIdFromAuId(auid));
      Plugin plugin = pluginMgr.getPlugin(pluginKey);
      if (plugin == null) return true;
      List descrs = plugin.getAuConfigDescrs();
      String auKey = PluginManager.auKeyFromAuId(auid);
      if (auidProps == null) {
	return true;
      }
      Configuration defConfig = ConfigManager.fromProperties(auidProps);
      return !isConfigCompatibleWithPlugin(defConfig, plugin);
    }

    boolean isConfigCompatibleWithPlugin(Configuration config, Plugin plugin) {
      Set have = config.keySet();
      Set need = new HashSet();
      for (Iterator iter = plugin.getAuConfigDescrs().iterator();
	   iter.hasNext();) {
	ConfigParamDescr descr = (ConfigParamDescr)iter.next();
	if (descr.isDefinitional()) {
	  need.add(descr.getKey());
	}
      }
      return have.equals(need);
    }

    Properties propsFromFile(File file) {
      try {
	InputStream is = new FileInputStream(file);
	Properties props = new Properties();
	props.load(is);
	is.close();
	return props;
      } catch (IOException e) {
	log.warning("Error loading au id from " + file);
	return null;
      }
    }

    private String getTitle(String key) {
      return "Repositories";
    }

    private List getSummaryInfo() {
      List res = new ArrayList();
//       res.add(new StatusTable.SummaryInfo("Tasks accepted",
// 					  ColumnDescriptor.TYPE_STRING,
// 					  combStats(STAT_ACCEPTED)));
      return res;
    }
  }
}
