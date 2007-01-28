/*
 * $Id$
 */

/*
 Copyright (c) 2000-2006 Board of Trustees of Leland Stanford Jr. University,
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

import java.io.*;
import java.util.*;

import org.lockss.app.*;
import org.lockss.config.*;
import org.lockss.daemon.status.*;
import org.lockss.plugin.*;
import org.lockss.remote.RemoteApi;
import org.lockss.state.ArchivalUnitStatus;
import org.lockss.util.*;

/**
 * Collect and report the status of the LockssRepository
 */
public class LockssRepositoryStatus extends BaseLockssDaemonManager {
  public static final String SERVICE_STATUS_TABLE_NAME = "RepositoryTable";
  public static final String SPACE_TABLE_NAME = "RepositorySpace";

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
    LockssDaemon theDaemon = getDaemon();
    StatusService statusServ = theDaemon.getStatusService();
    statusServ.registerStatusAccessor(SERVICE_STATUS_TABLE_NAME,
				      new RepoStatusAccessor(theDaemon));
    statusServ.registerStatusAccessor(SPACE_TABLE_NAME,
				      new RepoSpaceStatusAccessor(theDaemon));
  }

  public void stopService() {
    StatusService statusServ = getDaemon().getStatusService();
    statusServ.unregisterStatusAccessor(SERVICE_STATUS_TABLE_NAME);
    statusServ.unregisterStatusAccessor(SPACE_TABLE_NAME);
    super.stopService();
  }

  static class RepoStatusAccessor implements StatusAccessor {
    private LockssDaemon daemon;
    private PluginManager pluginMgr;
    private RepositoryManager repoMgr;

    private static final List columnDescriptors = ListUtil.list
      (new ColumnDescriptor("dir", "Dir", ColumnDescriptor.TYPE_STRING),
       new ColumnDescriptor("au", "AU", ColumnDescriptor.TYPE_STRING)
       .setComparator(CatalogueOrderComparator.SINGLETON),
       new ColumnDescriptor("status", "Status", ColumnDescriptor.TYPE_STRING),
       new ColumnDescriptor("diskusage", "Disk Usage (MB)",
			    ColumnDescriptor.TYPE_FLOAT),
       new ColumnDescriptor("plugin", "Plugin", ColumnDescriptor.TYPE_STRING),
       new ColumnDescriptor("params", "Params", ColumnDescriptor.TYPE_STRING)
       .setSortable(false)
//        new ColumnDescriptor("auid", "AU Key", ColumnDescriptor.TYPE_STRING)
       );

    private static final List sortRules =
      ListUtil.list(new StatusTable.SortRule("dir", true));

    RepoStatusAccessor(LockssDaemon daemon) {
      this.daemon = daemon;
      pluginMgr = daemon.getPluginManager();
      repoMgr = daemon.getRepositoryManager();
    }

    public String getDisplayName() {
      return "Repositories";
    }

    public void populateTable(StatusTable table)
        throws StatusService.NoSuchTableException {
      table.setColumnDescriptors(columnDescriptors);
      table.setDefaultSortRules(sortRules);
      Stats stats = new Stats();
      table.setRows(getRows(table, stats));
      table.setSummaryInfo(getSummaryInfo(stats));
    }

    public boolean requiresKey() {
      return false;
    }

    private List getRows(StatusTable table, Stats stats) {
      boolean includeInternalAus =
	table.getOptions().get(StatusTable.OPTION_DEBUG_USER);
      List rows = new ArrayList();
      TreeSet roots = new TreeSet();
      List repos = daemon.getRepositoryManager().getRepositoryList();
      for (Iterator iter = repos.iterator(); iter.hasNext(); ) {
	String repoSpec = (String)iter.next();
	String path = LockssRepositoryImpl.getLocalRepositoryPath(repoSpec);
	if (path != null) {
	  roots.add(path);
	}
      }
      roots.add(getDefaultRepositoryLocation());
      for (Iterator iter = roots.iterator(); iter.hasNext(); ) {
	String root = (String)iter.next();
	addRows(rows, LockssRepositoryImpl.extendCacheLocation(root),
		includeInternalAus, stats);
      }
      return rows;
    }

    String getDefaultRepositoryLocation() {
      return CurrentConfig.getParam(LockssRepositoryImpl.PARAM_CACHE_LOCATION);
    }

    class Stats {
      int active = 0;
      int inactive = 0;
      int deleted = 0;
      int orphaned = 0;
    }

    private void addRows(Collection rows, String root,
			 boolean includeInternalAus, Stats stats) {
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
		if (!includeInternalAus &&
		    pluginMgr.isInternalAu(pluginMgr.getAuFromId(auid))) {
		  continue;
		}
	      }
	    }
	    rows.add(makeRow(sub, root, auid, stats));
	  }
	}
      }
    }

    Map makeRow(File dir, String root, String auid, Stats stats) {
      String dirString = dir.toString();
      Map row = new HashMap();
      row.put("dir", dirString);
      if (auid == null) {
	row.put("status", "No AUID");
      } else {
	String auKey = PluginManager.auKeyFromAuId(auid);
	row.put("auid", auKey);
	row.put("plugin", PluginManager.pluginNameFromAuId(auid));
	ArchivalUnit au = pluginMgr.getAuFromId(auid);
	String name = null;
	if (au != null) {
	  name = au.getName();
	  Configuration auConfig = au.getConfiguration();
	  String repoSpec = auConfig.get(PluginManager.AU_PARAM_REPOSITORY);
	  String repoRoot = (repoSpec == null)
	    ? getDefaultRepositoryLocation()
	    : LockssRepositoryImpl.getLocalRepositoryPath(repoSpec);
	  if (!LockssRepositoryImpl.isDirInRepository(root, repoRoot)) {
	    au = null;
	  }
	}

	if (au != null) {
	  row.put("status", "Active");
	  stats.active++;
	  long du = AuUtil.getAuDiskUsage(au, false);
	  if (du != -1) {
	    addDu(row, du);
	  }
	  row.put("au", new StatusTable.Reference(name,
						  AU_STATUS_TABLE_NAME,
						  auid));
	  Configuration config = au.getConfiguration();
	  row.put("params", config);
	} else {
	  row.put("au", "");
	  long du = repoMgr.getRepoDiskUsage(dirString, false);
	  if (du != -1) {
	    addDu(row, du);
	  }
	  Configuration config = pluginMgr.getStoredAuConfiguration(auid);
	  Properties auidProps = null;
	  try {
	    auidProps = PropUtil.canonicalEncodedStringToProps(auKey);
	    if (auidProps != null) {
	      row.put("params", auidProps);
	    }
	  } catch (Exception e) {
	    log.warning("Couldn't decode AUKey in " + dir + ": " + auKey, e);
	  }
	  if (isOrphaned(auid, auidProps)) {
	    row.put("status", "Orphaned");
	    stats.orphaned++;
	  } else {
	    if (config == null || config.isEmpty()) {
	      row.put("status", "Deleted");
	      stats.deleted++;
	    } else {
	      row.put("params", config);
	      boolean isInactive =
		config.getBoolean(PluginManager.AU_PARAM_DISABLED, false);
	      if (isInactive) {
		stats.inactive++;
	      } else {
		stats.deleted++;
	      }	  
	      name = config.get(PluginManager.AU_PARAM_DISPLAY_NAME);
	      row.put("status",
		      (isInactive
		       ? "Inactive" : "Deleted"));
	    }
	  }
	  if (name != null) {
	    row.put("au", name);
	  }
	}
      }
      return row;
    }

    void addDu(Map row, long size) {
      if (size >= 0) {
	row.put("diskusage", new Float((float)size / (1024 * 1024)));
      }
    }

    boolean isOrphaned(String auid, Properties auidProps) {
      String pluginKey =
	PluginManager.pluginKeyFromId(PluginManager.pluginIdFromAuId(auid));
      Plugin plugin = pluginMgr.getPlugin(pluginKey);
      if (plugin == null) return true;
      if (auidProps == null) {
	return true;
      }
      Configuration defConfig = ConfigManager.fromProperties(auidProps);
      return !AuUtil.isConfigCompatibleWithPlugin(defConfig, plugin);
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

    private List getSummaryInfo(Stats stats) {
      List res = new ArrayList();
      addIfNonZero(res, "Active", stats.active);
      addIfNonZero(res, "Inactive", stats.inactive);
      addIfNonZero(res, "Deleted", stats.deleted);
      addIfNonZero(res, "Orphaned", stats.orphaned);
      addIfNonZero(res, "Awaiting recalc", repoMgr.sizeCalcQueueLen());
      return res;
    }

    private void addIfNonZero(List res, String head, int val) {
      if (val != 0) {
	res.add(new StatusTable.SummaryInfo(head,
					    ColumnDescriptor.TYPE_INT,
					    new Long(val)));
      }
    }
  }

  static class RepoSpaceStatusAccessor implements StatusAccessor {
    private LockssDaemon daemon;
    private RemoteApi remoteApi;

    private static final List columnDescriptors = ListUtil.list
      (new ColumnDescriptor("repo", "Repository",
			    ColumnDescriptor.TYPE_STRING),
       new ColumnDescriptor("size", "Size", ColumnDescriptor.TYPE_STRING),
       new ColumnDescriptor("used", "Used", ColumnDescriptor.TYPE_STRING),
       new ColumnDescriptor("free", "Free", ColumnDescriptor.TYPE_STRING),
       new ColumnDescriptor("percent", "%Full", ColumnDescriptor.TYPE_PERCENT)
       );

    private static final List sortRules =
      ListUtil.list(new StatusTable.SortRule("repo", true));

    RepoSpaceStatusAccessor(LockssDaemon daemon) {
      this.daemon = daemon;
      remoteApi = daemon.getRemoteApi();
    }

    public String getDisplayName() {
      return "Repository Space";
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
      List repos = remoteApi.getRepositoryList();
      List rows = new ArrayList();
      for (Iterator iter = repos.iterator(); iter.hasNext(); ) {
	Map row = new HashMap();
	String repo = (String)iter.next();
	PlatformUtil.DF df = remoteApi.getRepositoryDF(repo);
	row.put("repo", repo);
	if (df != null) {
	  row.put("size", orderedKBObj(df.getSize()));
	  row.put("used", orderedKBObj(df.getUsed()));
	  row.put("free", orderedKBObj(df.getAvail()));
	  row.put("percent", new Double(df.getPercent()));
	} else {
	  row.put("size", "unavailable");
	}
	rows.add(row);
      }
      return rows;
    }

    OrderedObject orderedKBObj(long kb) {
      return new OrderedObject(StringUtil.sizeKBToString(kb), new Long(kb));
    }

    private String getTitle(String key) {
      return "Repository Space";
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
