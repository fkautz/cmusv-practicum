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

package org.lockss.state;

import java.util.*;
import java.net.MalformedURLException;
import org.lockss.daemon.*;
import org.lockss.daemon.status.*;
import org.lockss.plugin.*;
import org.lockss.util.*;
import org.lockss.app.*;
import org.lockss.repository.*;

/**
 * Collect and report the status of the ArchivalUnits
 */
public class ArchivalUnitStatus extends BaseLockssManager {
  public static final String SERVICE_STATUS_TABLE_NAME =
      "ArchivalUnitStatusTable";
  public static final String AU_STATUS_TABLE_NAME = "ArchivalUnitTable";

  private static Logger logger = Logger.getLogger("AuStatus");

  public void startService() {
    super.startService();

    StatusService statusServ = theDaemon.getStatusService();

    statusServ.registerStatusAccessor(ArchivalUnitStatus.SERVICE_STATUS_TABLE_NAME,
                                      new ArchivalUnitStatus.ServiceStatus(theDaemon));
    statusServ.registerStatusAccessor(ArchivalUnitStatus.AU_STATUS_TABLE_NAME,
                                      new ArchivalUnitStatus.AuStatus(theDaemon));
    logger.debug2("Status accessors registered.");
  }

  public void stopService() {
    // unregister our status accessors
    StatusService statusServ = theDaemon.getStatusService();
    statusServ.unregisterStatusAccessor(
      ArchivalUnitStatus.SERVICE_STATUS_TABLE_NAME);
    statusServ.unregisterStatusAccessor(
      ArchivalUnitStatus.AU_STATUS_TABLE_NAME);
    logger.debug2("Status accessors unregistered.");

    super.stopService();
  }

  protected void setConfig(Configuration config, Configuration oldConfig,
                           Set changedKeys) {
  }

  private static ArchivalUnit getArchivalUnit(String auId,
      LockssDaemon theDaemon) {
    return theDaemon.getPluginManager().getAuFromId(auId);
  }

  static class ServiceStatus implements StatusAccessor {
    static final String TABLE_TITLE = "ArchivalUnit Status Table";

    private static final List columnDescriptors = ListUtil.list(
      new ColumnDescriptor("AuName", "Volume", ColumnDescriptor.TYPE_STRING),
      new ColumnDescriptor("AuNodeCount", "Nodes", ColumnDescriptor.TYPE_INT),
      new ColumnDescriptor("AuSize", "Size", ColumnDescriptor.TYPE_INT),
      new ColumnDescriptor("AuLastCrawl", "Last Crawl",
                           ColumnDescriptor.TYPE_DATE),
      new ColumnDescriptor("AuLastPoll", "Last Poll",
                           ColumnDescriptor.TYPE_DATE),
      new ColumnDescriptor("AuLastTreeWalk", "Last TreeWalk",
                           ColumnDescriptor.TYPE_DATE)
      );

    private static final List sortRules =
      ListUtil.list(new StatusTable.SortRule("AuName", true));

    private static LockssDaemon theDaemon;

    ServiceStatus(LockssDaemon theDaemon) {
      this.theDaemon = theDaemon;
    }

    public String getDisplayName() {
      return TABLE_TITLE;
    }

    public void populateTable(StatusTable table)
        throws StatusService.NoSuchTableException {
      table.setColumnDescriptors(columnDescriptors);
      table.setDefaultSortRules(sortRules);
      table.setRows(getRows());
    }

    public boolean requiresKey() {
      return false;
    }

    private List getRows() {
      List rowL = new ArrayList();
      for (Iterator iter = theDaemon.getPluginManager().getAllAus().iterator();
	   iter.hasNext(); ) {
        ArchivalUnit au = (ArchivalUnit)iter.next();
        NodeManager nodeMan = theDaemon.getNodeManager(au);
        rowL.add(makeRow(au, nodeMan.getAuState()));
      }
      return rowL;
    }

    private Map makeRow(ArchivalUnit au, AuState state) {
      HashMap rowMap = new HashMap();
      //"AuID"
      rowMap.put("AuName", AuStatus.makeAuRef(au.getName(),
          au.getAuId()));
      //XXX start caching this info
      rowMap.put("AuNodeCount", new Integer(-1));
      rowMap.put("AuSize", new Integer(-1));
      rowMap.put("AuLastCrawl", new Long(state.getLastCrawlTime()));
      rowMap.put("AuLastPoll", new Long(state.getLastTopLevelPollTime()));
      rowMap.put("AuLastTreeWalk", new Long(state.getLastTreeWalkTime()));

      return rowMap;
    }
  }

  static class AuStatus implements StatusAccessor {
    static final String TABLE_TITLE = "AU Status Table";

    private static final List columnDescriptors = ListUtil.list(
      new ColumnDescriptor("NodeName", "Node Url",
                           ColumnDescriptor.TYPE_STRING),
      new ColumnDescriptor("NodeStatus", "Status",
                           ColumnDescriptor.TYPE_STRING),
      new ColumnDescriptor("NodeHasContent", "Content",
                           ColumnDescriptor.TYPE_STRING),
      new ColumnDescriptor("NodeVersion", "Version",
                           ColumnDescriptor.TYPE_INT),
      new ColumnDescriptor("NodeContentSize", "Size",
                           ColumnDescriptor.TYPE_INT),
      new ColumnDescriptor("NodeChildCount", "Children",
                           ColumnDescriptor.TYPE_INT),
      new ColumnDescriptor("NodeTreeSize", "Tree Size",
                           ColumnDescriptor.TYPE_INT)
      );

    private static final List sortRules = ListUtil.list(
      new StatusTable.SortRule("NodeName", false)
      );

    private static LockssDaemon theDaemon;

    AuStatus(LockssDaemon theDaemon) {
      this.theDaemon = theDaemon;
    }

    public String getDisplayName() {
      throw new
	UnsupportedOperationException("Au table has no generic title");
    }

    public void populateTable(StatusTable table)
        throws StatusService.NoSuchTableException {
      ArchivalUnit au = getArchivalUnit(table.getKey(), theDaemon);
      LockssRepository repo = theDaemon.getLockssRepository(au);
      NodeManager nodeMan = theDaemon.getNodeManager(au);

      table.setTitle(getTitle(au.getName()));
      table.setSummaryInfo(getSummaryInfo(au, nodeMan.getAuState()));
      table.setColumnDescriptors(columnDescriptors);
      table.setDefaultSortRules(sortRules);
      table.setRows(getRows(au, repo, nodeMan));
    }

    public boolean requiresKey() {
      return true;
    }

    private List getRows(ArchivalUnit au, LockssRepository repo,
                         NodeManager nodeMan) {
      List rowL = new ArrayList();
      Iterator cusIter = au.getAuCachedUrlSet().contentHashIterator();
      while (cusIter.hasNext()) {
        CachedUrlSetNode cusn = (CachedUrlSetNode)cusIter.next();
        CachedUrlSet cus;
        if (cusn.getType() == cusn.TYPE_CACHED_URL_SET) {
          cus = (CachedUrlSet)cusn;
        } else {
          CachedUrlSetSpec spec = new RangeCachedUrlSetSpec(cusn.getUrl());
          cus = au.getPlugin().makeCachedUrlSet(au, spec);
        }
        try {
          rowL.add(makeRow(repo.getNode(cus.getUrl()),
                           nodeMan.getNodeState(cus)));
        } catch (MalformedURLException ignore) { }
      }
      return rowL;
    }

    private String getTitle(String key) {
      return "Status Table for AU: " + key;
    }

    private List getSummaryInfo(ArchivalUnit au, AuState state) {
        List summaryList =  ListUtil.list(
            new StatusTable.SummaryInfo("Volume" , ColumnDescriptor.TYPE_STRING,
                                        au.getName()),
            new StatusTable.SummaryInfo("Nodes", ColumnDescriptor.TYPE_INT,
                                        new Integer(-1)),
            new StatusTable.SummaryInfo("Size", ColumnDescriptor.TYPE_INT,
                                        new Integer(-1)),
            new StatusTable.SummaryInfo("Last Crawl Time",
                                        ColumnDescriptor.TYPE_DATE,
                                        new Long(state.getLastCrawlTime())),
            new StatusTable.SummaryInfo("Last Top-level Poll",
                                        ColumnDescriptor.TYPE_DATE,
                                        new Long(state.getLastTopLevelPollTime())),
            new StatusTable.SummaryInfo("Last Treewalk",
                                        ColumnDescriptor.TYPE_DATE,
                                        new Long(state.getLastTreeWalkTime())),
            new StatusTable.SummaryInfo("Has Damage",
                                        ColumnDescriptor.TYPE_STRING,
                                        "-"),
            new StatusTable.SummaryInfo("Current Activity",
                                        ColumnDescriptor.TYPE_STRING,
                                        "-")
            );
        return summaryList;
    }

    private Map makeRow(RepositoryNode node, NodeState state) {
      HashMap rowMap = new HashMap();
      rowMap.put("NodeName", node.getNodeUrl());

      String status;
      if (node.isDeleted()) {
        status = "Deleted";
      } else if (node.isInactive()) {
        status = "Inactive";
      } else if (state.hasDamage()) {
        status = "Damaged";
      } else {
        status = "Active";
      }
      rowMap.put("NodeStatus", status);
      boolean content = node.hasContent();
      Object versionObj = "-";
      Object sizeObj = "-";
      if (content) {
        versionObj = new Integer(node.getCurrentVersion());
        sizeObj = new Long(node.getContentSize());
      }
      rowMap.put("NodeHasContent", (content ? "yes" : "no"));
      rowMap.put("NodeVersion", versionObj);
      rowMap.put("NodeContentSize", sizeObj);
      Object childObj = "-";
      if (!node.isLeaf()) {
        childObj = new Integer(node.getChildCount());
      }
      rowMap.put("NodeChildCount", childObj);
      rowMap.put("NodeContentSize", new Long(node.getTreeContentSize(null)));

      return rowMap;
    }

    // utility method for making a Reference
    public static StatusTable.Reference makeAuRef(Object value,
                                                  String key) {
      return new StatusTable.Reference(value, AU_STATUS_TABLE_NAME, key);
    }
  }
}
