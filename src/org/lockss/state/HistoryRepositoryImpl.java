/*
 * $Id$
 */

/*

Copyright (c) 2002 Board of Trustees of Leland Stanford Jr. University,
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

import java.io.*;
import java.net.MalformedURLException;
import java.util.*;

import org.exolab.castor.xml.Marshaller;
import org.exolab.castor.xml.Unmarshaller;
import org.exolab.castor.mapping.Mapping;

import org.lockss.util.*;
import org.lockss.repository.*;
import org.lockss.daemon.Configuration;
import org.lockss.app.*;
import org.lockss.plugin.*;
import java.net.URL;
import org.xml.sax.InputSource;


/**
 * HistoryRepository is an inner layer of the NodeManager which handles the actual
 * storage of NodeStates.
 */
public class HistoryRepositoryImpl
  extends BaseLockssManager implements HistoryRepository {

  /**
   * Configuration parameter name for Lockss history location.
   */
  public static final String PARAM_HISTORY_LOCATION =
    Configuration.PREFIX + "history.location";

  /**
   * Name of top directory in which the histories are stored.
   */
  public static final String HISTORY_ROOT_NAME = "cache";

  static final String MAPPING_FILE_NAME = "pollmapping.xml";
  static final String HISTORY_FILE_NAME = "history.xml";
  static final String NODE_FILE_NAME = "nodestate.xml";
  static final String AU_FILE_NAME = "au_state.xml";
  // this contains a '#' so that it's not defeatable by strings which
  // match the prefix in a url (like '../tmp/')
  private static final String TEST_PREFIX = "/#tmp";

  private static String rootDir;
  Mapping mapping = null;
  private static Logger logger = Logger.getLogger("HistoryRepository");

  private static LockssDaemon theDaemon;
  private static HistoryRepositoryImpl theRepository;

  HistoryRepositoryImpl(String repository_location) {
    rootDir = repository_location;
  }

  public HistoryRepositoryImpl() { }

  /**
   * start the plugin manager.
   * @see org.lockss.app.LockssManager#startService()
   */
  public void startService() {
    super.startService();
    if (rootDir==null) {
      String msg = PARAM_HISTORY_LOCATION + " not configured";
      logger.error(msg);
      throw new LockssDaemonException(msg);
    }
    loadMapping();
  }

  /**
   * stop the plugin manager
   * @see org.lockss.app.LockssManager#stopService()
   */
  public void stopService() {
    // we want to checkpoint here
    mapping = null;
    super.stopService();
  }

  protected void setConfig(Configuration config, Configuration oldConfig,
			   Set changedKeys) {
    // don't reset this once it's set
    if (rootDir==null) {
      rootDir = config.getParam(PARAM_HISTORY_LOCATION);
    }
  }

  public void storeNodeState(NodeState nodeState) {
    try {
      File nodeDir = new File(getNodeLocation(nodeState.getCachedUrlSet()));
      if (!nodeDir.exists()) {
        nodeDir.mkdirs();
      }
      logger.debug3("Storing state for CUS '" +
                    nodeState.getCachedUrlSet().getUrl() + "'");
      File nodeFile = new File(nodeDir, NODE_FILE_NAME);
      Marshaller marshaller = new Marshaller(new FileWriter(nodeFile));
      marshaller.setMapping(getMapping());
      marshaller.marshal(new NodeStateBean(nodeState));
    } catch (Exception e) {
      logger.error("Couldn't store node state: ", e);
      throw new LockssRepository.RepositoryStateException(
          "Couldn't store node state.");
    }
  }

  public NodeState loadNodeState(CachedUrlSet cus) {
    try {
      File nodeFile = new File(getNodeLocation(cus) + File.separator +
                               NODE_FILE_NAME);
      if (!nodeFile.exists()) {
        return new NodeStateImpl(cus, -1,
                                 new CrawlState(-1, CrawlState.FINISHED, 0),
                                 new ArrayList(), this);
      }
      logger.debug3("Loading state for CUS '" + cus.getUrl() + "'");
      Unmarshaller unmarshaller = new Unmarshaller(NodeStateBean.class);
      unmarshaller.setMapping(getMapping());
      NodeStateBean nsb = (NodeStateBean)unmarshaller.unmarshal(
          new FileReader(nodeFile));
      return new NodeStateImpl(cus, nsb, this);
    } catch (org.exolab.castor.xml.MarshalException me) {
      logger.error("Marshalling exception on nodestate for '" +
                   cus.getUrl() + "' ", me);
      // continue
      return new NodeStateImpl(cus, -1,
                               new CrawlState(-1, CrawlState.FINISHED, 0),
                               new ArrayList(), this);
    } catch (Exception e) {
      logger.error("Couldn't load node state: ", e);
      throw new LockssRepository.RepositoryStateException(
          "Couldn't load node state.");
    }
  }


  public void storePollHistories(NodeState nodeState) {
    CachedUrlSet cus = nodeState.getCachedUrlSet();
    try {
      File nodeDir = new File(getNodeLocation(cus));
      if (!nodeDir.exists()) {
        nodeDir.mkdirs();
      }
      logger.debug3("Storing histories for CUS '"+cus.getUrl()+"'");
      File nodeFile = new File(nodeDir, HISTORY_FILE_NAME);
      NodeHistoryBean nhb = new NodeHistoryBean();
      nhb.historyBeans = ((NodeStateImpl)nodeState).getPollHistoryBeanList();
      Marshaller marshaller = new Marshaller(new FileWriter(nodeFile));
      marshaller.setMapping(getMapping());
      marshaller.marshal(nhb);
    } catch (Exception e) {
      logger.error("Couldn't store poll history: ", e);
      throw new LockssRepository.RepositoryStateException(
          "Couldn't store history.");
    }
  }

  public void loadPollHistories(NodeState nodeState) {
    CachedUrlSet cus = nodeState.getCachedUrlSet();
    File nodeFile = null;
    try {
      nodeFile = new File(getNodeLocation(cus) + File.separator +
                               HISTORY_FILE_NAME);
      if (!nodeFile.exists()) {
        ((NodeStateImpl)nodeState).setPollHistoryBeanList(new ArrayList());
        logger.debug3("No history file found.");
        return;
      }
      logger.debug3("Loading histories for CUS '"+cus.getUrl()+"'");
      Unmarshaller unmarshaller = new Unmarshaller(NodeHistoryBean.class);
      unmarshaller.setMapping(getMapping());
      NodeHistoryBean nhb = (NodeHistoryBean)unmarshaller.unmarshal(
          new FileReader(nodeFile));
      if (nhb.historyBeans==null) {
        logger.debug3("Empty history list loaded.");
        nhb.historyBeans = new ArrayList();
      }
      ((NodeStateImpl)nodeState).setPollHistoryBeanList(
          new ArrayList(nhb.historyBeans));
    } catch (org.exolab.castor.xml.MarshalException me) {
      logger.error("Parsing exception.  Moving file to '.old'");
      nodeFile.renameTo(new File(nodeFile.getAbsolutePath()+".old"));
      ((NodeStateImpl)nodeState).setPollHistoryBeanList(new ArrayList());
    } catch (Exception e) {
      logger.error("Couldn't load poll history: ", e);
      throw new LockssRepository.RepositoryStateException(
          "Couldn't load history.");
    }
  }

  public void storeAuState(AuState auState) {
    try {
      File nodeDir = new File(getAuLocation(auState.getArchivalUnit()));
      if (!nodeDir.exists()) {
        nodeDir.mkdirs();
      }
      logger.debug3("Storing state for AU '" +
                    auState.getArchivalUnit().getName() + "'");
      File auFile = new File(nodeDir, AU_FILE_NAME);
      Marshaller marshaller = new Marshaller(new FileWriter(auFile));
      marshaller.setMapping(getMapping());
      marshaller.marshal(new AuStateBean(auState));
    } catch (Exception e) {
      logger.error("Couldn't store au state: ", e);
      throw new LockssRepository.RepositoryStateException(
          "Couldn't store au state.");
    }
  }

  public AuState loadAuState(ArchivalUnit au) {
    try {
      File auFile = new File(getAuLocation(au) + File.separator + AU_FILE_NAME);
      if (!auFile.exists()) {
        logger.debug3("No au file found.");
        return new AuState(au, -1, -1, -1, this);
      }
      logger.debug3("Loading state for AU '" + au.getName() + "'");
      Unmarshaller unmarshaller = new Unmarshaller(AuStateBean.class);
      unmarshaller.setMapping(getMapping());
      AuStateBean asb = (AuStateBean) unmarshaller.unmarshal(
          new FileReader(auFile));
      // does not load in an old treewalk time, so that one will be run
      // immediately
      return new AuState(au, asb.getLastCrawlTime(),
                         asb.getLastTopLevelPollTime(),
                         -1, this);
    } catch (org.exolab.castor.xml.MarshalException me) {
      logger.error("Marshalling exception for austate '"+au.getName()+"': "+me);
      // continue
      return new AuState(au, -1, -1, -1, this);
    } catch (Exception e) {
      logger.error("Couldn't load au state: ", e);
      throw new LockssRepository.RepositoryStateException(
          "Couldn't load au state.");
    }
  }

  protected String getNodeLocation(CachedUrlSet cus)
      throws MalformedURLException {
    StringBuffer buffer = new StringBuffer(rootDir);
    if (!rootDir.endsWith(File.separator)) {
      buffer.append(File.separator);
    }
    buffer.append(HISTORY_ROOT_NAME);
    buffer.append(File.separator);
    String auLoc = LockssRepositoryServiceImpl.mapAuToFileLocation(
        buffer.toString(), cus.getArchivalUnit());
    String urlStr = (String)cus.getUrl();
    if (AuUrl.isAuUrl(urlStr)) {
      return auLoc;
    } else {
      try {
        URL testUrl = new URL(urlStr);
        String path = testUrl.getPath();
        if (path.indexOf("/.")>=0) {
          // filtering to remove urls including '..' and such
          path = TEST_PREFIX + path;
          File testFile = new File(path);
          String canonPath = testFile.getCanonicalPath();
          if (canonPath.startsWith(TEST_PREFIX)) {
            urlStr = testUrl.getProtocol() + "://" +
                testUrl.getHost().toLowerCase()
                + canonPath.substring(TEST_PREFIX.length());
          }
          else {
            logger.error("Illegal URL detected: " + urlStr);
            throw new MalformedURLException("Illegal URL detected.");
          }
        }
      } catch (IOException ie) {
        logger.error("Error testing URL: "+ie);
        throw new MalformedURLException ("Error testing URL.");
      }
      return LockssRepositoryServiceImpl.mapUrlToFileLocation(auLoc, urlStr);
    }
  }

  protected String getAuLocation(ArchivalUnit au) {
    StringBuffer buffer = new StringBuffer(rootDir);
    if (!rootDir.endsWith(File.separator)) {
      buffer.append(File.separator);
    }
    buffer.append(HISTORY_ROOT_NAME);
    buffer.append(File.separator);
    return LockssRepositoryServiceImpl.mapAuToFileLocation(buffer.toString(),
        au);
  }

  private void loadMapping() {
    if (mapping==null) {
      URL mappingLoc = getClass().getResource(MAPPING_FILE_NAME);
      if (mappingLoc==null) {
        logger.error("Couldn't find resource '"+MAPPING_FILE_NAME+"'");
        throw new LockssDaemonException("Couldn't find mapping file.");
      }

      mapping = new Mapping();
      try {
        mapping.loadMapping(mappingLoc);
      } catch (Exception e) {
        logger.error("Couldn't load mapping file '"+mappingLoc+"'");
        throw new LockssDaemonException("Couldn't load mapping file.");
      }
    }
  }

  Mapping getMapping() {
    if (mapping==null) {
      logger.error("Mapping file not loaded.");
      throw new LockssDaemonException("Mapping file not loaded.");
    } else if (mapping.getRoot().getClassMappingCount()==0) {
      logger.error("Mapping file is empty.");
      throw new LockssDaemonException("Mapping file is empty.");
    } else {
      return mapping;
    }
  }
}
