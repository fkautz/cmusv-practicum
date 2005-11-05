/*
 * $Id$
 */

/*

Copyright (c) 2000-2005 Board of Trustees of Leland Stanford Jr. University,
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
import java.util.ArrayList;
import java.util.List;

import org.lockss.app.BaseLockssDaemonManager;
import org.lockss.app.LockssAuManager;
import org.lockss.config.Configuration;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.plugin.AuUrl;
import org.lockss.plugin.CachedUrlSet;
import org.lockss.protocol.IdentityAgreementList;
import org.lockss.protocol.IdentityManager;
import org.lockss.repository.LockssRepositoryImpl;
import org.lockss.repository.LockssRepository.RepositoryStateException;
import org.lockss.util.*;
import org.lockss.util.ObjectSerializer.SerializationException;

/**
 * HistoryRepository is an inner layer of the NodeManager which handles the
 * actual storage of NodeStates.
 */
public class HistoryRepositoryImpl
  extends BaseLockssDaemonManager
  implements HistoryRepository {

  // CASTOR: Top-level Castor documentation; read below
  /*
   * IMPLEMENTATION NOTES
   *
   * This class definition is really huge but there is a lot of code
   * duplication. A clever refactoring may be possible in the future.
   *
   * Castor is going away soon (hopefully) and while refactoring the
   * marshalling/unmarshalling has led to a sizeable increase in code
   * length, it is not very difficult to filter out the Castor-related
   * snippets. Use grep or similar to find the string
   *    CASTOR:
   * in this file for immediate pointers to Castor-related passages.
   *
   * The load/store methods would have no Castor-aware functionality
   * if it was not for the fact that in Castor mode, we do not often
   * store data in the form of the type it came from. Instead, in
   * Castor, we often need to wrap the object being marshalled into
   * some sort of bean class or helper class, and that bean needs to
   * be unwrapped when it comes back from serialization. To avoid too
   * much Castor-aware code and if/else branches in the load/store
   * methods themselves, the compromise was to introduce a wrap/unwrap
   * call just before the object goes out to serialized form and right
   * after it comes back from it.
   *
   * The wrap methods just pass the object through unless we are in
   * Castor mode (in which case the object is wrapped in a bean/helper
   * class). The unwrap methods also pass objects right through unless
   * they are recognized as Castor-related bean/helper classes, which
   * causes them to be unwrapped.
   *
   * When Castor goes away, all the wrap/unwrap call will be removed
   * from load/store methods; all the wrap/unwrap methods will go
   * away; all the makeXXXSerializer methods will go away and be
   * replaced by makeObjectSerializer (which will be the only one to
   * stay).
   *
   * Thus there is a lot of added bloat; but in the end (meaning, with
   * the long-term objective of the phasing out of Castor), the load/
   * store methods are written in a way that is already marshaller-
   * agnostic, so that the only thing to remove is the wrap/unwrap
   * calls.
   */

  /**
   * <p>Factory class to create HistoryRepository instances.</p>
   */
  public static class Factory implements LockssAuManager.Factory {
    public LockssAuManager createAuManager(ArchivalUnit au) {
      return createNewHistoryRepository(au);
    }
  }

  private String rootLocation;

  private ArchivalUnit storedAu;

  HistoryRepositoryImpl(ArchivalUnit au, String rootPath) {
    storedAu = au;
    rootLocation = rootPath;
    if (rootLocation==null) {
      throw new NullPointerException();
    }
    if (!rootLocation.endsWith(File.separator)) {
      // this shouldn't happen
      rootLocation += File.separator;
    }
  }

  public File getIdentityAgreementFile() {
    return new File(rootLocation, IDENTITY_AGREEMENT_FILE_NAME);
  }

  /**
   * <p>Loads the state of an AU.</p>
   * @return An AuState instance loaded from file.
   * @see #loadAuState(ObjectSerializer)
   */
  public AuState loadAuState() {
    // CASTOR: change to makeObjectSerializer() when Castor is phased out
    return loadAuState(makeAuStateSerializer());
  }

  /**
   * <p>Loads the state of an AU using the given deserializer.</p>
   * @param deserializer A deserializer instance.
   * @return An AuState instance loaded from file.
   * @throws RepositoryStateException if an error condition arises
   *                                  that is neither a file not found
   *                                  exception nor a serialization
   *                                  exception.
   */
  AuState loadAuState(ObjectSerializer deserializer) {
    if (logger.isDebug3()) {
      logger.debug3("Loading state for AU '"
          + storedAu.getName() + "'.");
    }
    File auFile = new File(rootLocation, AU_FILE_NAME);

    try {
      // CASTOR: remove unwrap() when Castor is phased out
      AuState auState = (AuState)unwrap(deserializer.deserialize(auFile));
      return new AuState(storedAu,
                         auState.getLastCrawlTime(),
                         auState.getLastTopLevelPollTime(),
                         -1,
                         auState.getCrawlUrls(),
                         this);
    }
    catch (FileNotFoundException fnfe) {
      // drop down to return default
    }
    catch (SerializationException se) {
      logger.error("Marshalling exception for AU state '"
          + storedAu.getName() + "'", se);
      // drop down to return default
    }
    catch (Exception e) {
      logger.error("Could not load AU state '"
          + storedAu.getName() + "'", e);
      throw new RepositoryStateException("Could not load AU state.");
    }

    // Default: return default
    return new AuState(storedAu, -1, -1, -1, null, this);
  }

  /**
   * <p>Loads a damaged node set from file.</p>
   * @return A damaged node set retrieved from file.
   * @see #loadDamagedNodeSet(ObjectSerializer)
   */
  public DamagedNodeSet loadDamagedNodeSet() {
    // CASTOR: change to makeObjectSerializer() when Castor is phased out
    return loadDamagedNodeSet(makeDamagedNodeSetSerializer());
  }

  /**
   * <p>Loads a damaged node set from file using the given
   * deserializer.</p>
   * @param deserializer A deserializer instance.
   * @return A damaged node set retrieved from file.
   * @throws RepositoryStateException if an error condition arises
   *                                  that is neither a file not found
   *                                  exception nor a serialization
   *                                  exception.
   */
  DamagedNodeSet loadDamagedNodeSet(ObjectSerializer deserializer) {
    if (logger.isDebug3()) {
      logger.debug3("Loading damaged nodes for AU '"
          + storedAu.getName() + "'.");
    }
    File damFile = new File(rootLocation, DAMAGED_NODES_FILE_NAME);

    try {
      // CASTOR: NO CHANGE after Castor is phased out
      DamagedNodeSet damNodes = (DamagedNodeSet)deserializer.deserialize(damFile);
      // set these fields manually // post-deserialization method?
      damNodes.theAu = storedAu;
      damNodes.repository = this;
      return damNodes;
    }
    catch (FileNotFoundException fnfe) {
      if (logger.isDebug2()) {
        logger.debug2("No damaged node file for AU '"
            + storedAu.getName() + "'.");
      }
      // drop down to return empty set
    }
    catch (SerializationException se) {
      logger.error("Marshalling exception for damaged nodes for '"
          + storedAu.getName() + "'", se);
      // drop down to return empty set
    }
    catch (Exception e) {
      logger.error("Could not load damaged nodes", e);
      throw new RepositoryStateException("Could not load damaged nodes.");
    }

    // Default: return empty set
    return new DamagedNodeSet(storedAu, this);
  }

  /**
   * <p>Loads an identity agreement list.</p>
   * @return A list of identity agreements.
   * @see #loadIdentityAgreements(ObjectSerializer)
   */
  public List loadIdentityAgreements() {
    return loadIdentityAgreements(makeIdentityAgreementListSerializer());
  }
  /**
   * <p>Loads an identity agreement list using the given
   * deserializer.</p>
   * @param deserializer A deserializer instance.
   * @return A list of identity agreements.
   * @throws RepositoryStateException if an error condition arises
   *                                  that is neither a file not found
   *                                  exception nor a serialization
   *                                  exception.
   */
  List loadIdentityAgreements(ObjectSerializer deserializer) {
    if (logger.isDebug3()) {
      logger.debug3("Loading identity agreements for AU '"
          + storedAu.getName() + "'.");
    }
    File idFile = getIdentityAgreementFile();

    try {
      // CASTOR: remove unwrap() when Castor is phased out
      return (List)unwrap(deserializer.deserialize(idFile));
    }
    catch (FileNotFoundException fnfe) {
      logger.debug2("No identities file for AU '"
          + storedAu.getName() + "'.");
      // drop down to return empty list
    }
    catch (SerializationException se) {
      logger.error("Marshalling exception for identity agreements", se);
      // drop down to return empty list
    }
    catch (Exception e) {
      logger.error("Could not load identity agreements", e);
      throw new RepositoryStateException("Could not load identity agreements.");
    }

    // Default: return empty list
    return new ArrayList();
  }

  /**
   * <p>Loads the node state for a cached URL set.</p>
   * @param cus A cached URL set instance.
   * @return A NodeState instance retrieved from file.
   * @see #loadNodeState(ObjectSerializer, CachedUrlSet)
   */
  public NodeState loadNodeState(CachedUrlSet cus) {
    return loadNodeState(makeNodeStateSerializer(), cus);
  }

  /**
   * <p>Loads the node state for a cached URL set.</p>
   * <p>Also sets the returned node state's cached URL set to the
   * cached URL set argument.</p>
   * @param deserializer A deserializer instance.
   * @param cus          A cached URL set instance.
   * @return A NodeState instance retrieved from file.
   * @throws RepositoryStateException if an error condition arises
   *                                  that is neither a file not found
   *                                  exception nor a serialization
   *                                  exception.
   */
  NodeState loadNodeState(ObjectSerializer deserializer,
                          CachedUrlSet cus) {
    if (logger.isDebug3()) {
      logger.debug3("Loading state for CUS '" + cus.getUrl() + "'.");
    }

    try {
      // Can throw MalformedURLException
      File file = new File(getNodeLocation(cus), NODE_FILE_NAME);

      // CASTOR: remove unwrap() when Castor is phased out
      NodeStateImpl nodeState = (NodeStateImpl)unwrap(deserializer.deserialize(file),
                                                      cus,
                                                      this);
      nodeState.setCachedUrlSet(cus);
      nodeState.repository = this;
      // CASTOR: uncomment this or write post-deserialization method
      // nodeState.repository = this;
      return nodeState;
    }
    catch (FileNotFoundException fnfe) {
      logger.debug3("No node state file for node '" + cus.getUrl() + "'.");
      // drop down to return default value
    }
    catch (SerializationException se) {
      logger.error("Marshalling exception on node state for '"
          + cus.getUrl() + "'", se);
      // drop down to return default value
    }
    catch (Exception e) {
      logger.error("Could not load node state", e);
      throw new RepositoryStateException("Could not load node state.");
    }

    // Default value
    return new NodeStateImpl(cus,
                             -1,
                             new CrawlState(-1, CrawlState.FINISHED, 0),
                             new ArrayList(),
                             this);
  }

  /**
   * <p>Loads the poll histories associated with the node state.</p>
   * @param nodeState A node state instance.
   * @see #loadPollHistories(ObjectSerializer, NodeState)
   */
  public void loadPollHistories(NodeState nodeState) {
    loadPollHistories(makePollHistoriesSerializer(), nodeState);
  }

  /**
   * <p>Loads the poll histories associated with the node state
   * using the given deserializer.</p>
   * <p>Caution: the implementation of this method expects an
   * actual {@link NodeStateImpl} instance at runtime.</p>
   * @param deserializer A deserializer instance.
   * @param nodeState    A node state instance.
   * @throws RepositoryStateException if an error condition arises
   *                                  that is neither an input/output
   *                                  exception nor a serialization
   *                                  exception.
   */
  void loadPollHistories(ObjectSerializer deserializer,
                         NodeState nodeState) {
    /*
     * IMPLEMENTATION NOTES
     *
     * Note that this method casts the nodeState input parameter to
     * NodeStateImpl to gain access to the underlying methods which
     * is bad! Bad bad bad.
     *
     * This would have to change if the entire structure was not going
     * to disappear relatively soon with V3 polls.
     */

    CachedUrlSet cus = nodeState.getCachedUrlSet();
    if (logger.isDebug3()) {
      logger.debug3("Loading histories for CUS '"
          + cus.getUrl() + "'.");
    }
    File nodeFile = null;
    NodeStateImpl impl = (NodeStateImpl)nodeState; // eww

    try {
      // Can throw MalformedURLException
      nodeFile = new File(getNodeLocation(cus), HISTORY_FILE_NAME);

      // CASTOR: remove unwrap() when Castor is phased out
      List hist = (List)unwrap(deserializer.deserialize(nodeFile));
      if (hist.size() == 0) {
        logger.debug3("Empty history list loaded.");
        // drop down to the default setter
      }
      else {
        impl.setPollHistoryList(hist);
        return;
      }
    }
    catch (FileNotFoundException fnfe) {
      logger.debug3("No histories to load.");
      // drop down to the default setter
    }
    catch (SerializationException se) {
      logger.error("Cannot parse poll history (SerializationException): "
          + nodeFile, se);
      // rename file and drop down to default setter
      nodeFile.renameTo(new File(nodeFile.getAbsolutePath()+".old"));
    }
    catch (IOException ioe) {
      logger.error("Cannot parse poll history (IOException): "
          + nodeFile, ioe);
      // rename file and drop down to default setter
      nodeFile.renameTo(new File(nodeFile.getAbsolutePath()+".old"));
    }
    catch (Exception e) {
      logger.error("Could not load poll history", e);
      throw new RepositoryStateException("Could not load history.");
    }

    // Default setter
    impl.setPollHistoryList(new ArrayList());
  }

  public void setAuConfig(Configuration auConfig) {

  }

  public void startService() {
    super.startService();
    // check if file updates are needed
    // Disabled 10/5/04, may be needed again
    //    checkFileChange();
  }

  public void stopService() {
    // we want to checkpoint here
    super.stopService();
  }

  /**
   * <p>Stores the state of an AU.</p>
   * @param auState    A AU state instance.
   * @see #storeAuState(ObjectSerializer, AuState)
   */
  public void storeAuState(AuState auState) {
    // CASTOR: replace by makeObjectSerializer()
    storeAuState(makeAuStateSerializer(), auState);
  }

  /**
   * <p>Stores the state of an AU using the given serializer.</p>
   * @param serializer A serializer instance.
   * @param auState    A AU state instance.
   * @throws RepositoryStateException if an error condition arises.
   */
  void storeAuState(ObjectSerializer serializer,
                    AuState auState) {
    if (logger.isDebug3()) {
      logger.debug3("Storing state for AU '"
          + auState.getArchivalUnit().getName() + "'.");
    }
    File file = prepareFile(rootLocation, AU_FILE_NAME);

    try {
      // CASTOR: remove wrap() when Castor is phased out
      serializer.serialize(file, wrap(auState));
    }
    catch (Exception e) {
      logger.error("Could not store AU state", e);
      throw new RepositoryStateException("Could not store AU state.");
    }
  }

  /**
   * <p>Stores a damaged node set.</p>
   * @param nodeSet    A damaged node set.
   * @see #storeDamagedNodeSet(ObjectSerializer, DamagedNodeSet)
   */
  public void storeDamagedNodeSet(DamagedNodeSet nodeSet) {
    // CASTOR: change to makeObjectSerializer() when Castor is phased out
    storeDamagedNodeSet(makeDamagedNodeSetSerializer(), nodeSet);
  }

  /**
   * <p>Stores a damaged node set using the given serializer.</p>
   * @param serializer A serializer instance.
   * @param nodeSet    A damaged node set.
   * @throws RepositoryStateException if an error condition arises.
   */
  void storeDamagedNodeSet(ObjectSerializer serializer,
                           DamagedNodeSet nodeSet) {
    if (logger.isDebug3()) {
      logger.debug3("Storing damaged nodes for AU '" +
                    nodeSet.theAu.getName() + "'.");
    }
    File file = prepareFile(rootLocation, DAMAGED_NODES_FILE_NAME);

    try {
      // CASTOR: NO CHANGE when Castor is phased out
      serializer.serialize(file, nodeSet);
    }
    catch (Exception exc) {
      logger.error("Could not store damaged nodes", exc);
      throw new RepositoryStateException("Could not store damaged nodes");
    }
  }

  /**
   * <p>Stores an identity agreement list.</p>
   * @param idList     A list of identity agreements.
   * @see #storeIdentityAgreements(ObjectSerializer, List)
   */
  public void storeIdentityAgreements(List idList) {
    // CASTOR: change to makeObjectSerializer() when Castor is phased out
    storeIdentityAgreements(makeIdentityAgreementListSerializer(), idList);
  }

  /**
   * <p>Stores an identity agreement list using the given serializer
   * instance.</p>
   * @param serializer A serializer instance.
   * @param idList     A list of identity agreements.
   * @throws RepositoryStateException if an error condition arises.
   */
  void storeIdentityAgreements(ObjectSerializer serializer,
                               List idList) {
    if (logger.isDebug3()) {
      logger.debug3("Storing identity agreements for AU '"
          + storedAu.getName() + "'.");
    }
    File file = prepareFile(rootLocation, IDENTITY_AGREEMENT_FILE_NAME);

    try {
      // CASTOR: remove wrap() when Castor is phased out
      serializer.serialize(file, wrap(idList));
    }
    catch (Exception exc) {
      logger.error("Could not store identity agreements", exc);
      throw new RepositoryStateException("Could not store identity agreements");
    }
  }

  /**
   * <p>Stores the node state.</p>
   * @param nodeState A node state instance.
   * @see #storeNodeState(ObjectSerializer, NodeState)
   */
  public void storeNodeState(NodeState nodeState) {
    // CASTOR: change to makeObjectSerializer() when Castor is phased out
    storeNodeState(makeNodeStateSerializer(), nodeState);
  }

  /**
   * <p>Stores the node state with the given serializer.</p>
   * @param serializer A serializer instance.
   * @param nodeState  A node state instance.
   * @throws RepositoryStateException if an error condition arises.
   */
  void storeNodeState(ObjectSerializer serializer,
                      NodeState nodeState) {
    CachedUrlSet cus = nodeState.getCachedUrlSet();
    if (logger.isDebug3()) {
      logger.debug3("Storing state for CUS '"
          + cus.getUrl() + "'.");
    }

    try {
      // Can throw MalformedURLException
      File file = prepareFile(getNodeLocation(cus), NODE_FILE_NAME);

      // CASTOR: remove wrap() when Castor is phased out
      serializer.serialize(file, wrap(nodeState));
    }
    catch (Exception e) {
      logger.error("Could not store node state", e);
      throw new RepositoryStateException("Could not store node state.");
    }
  }

  /**
   * <p>Stores the poll histories associated with the node state.</p>
   * @param nodeState  A node state instance.
   * @see #storePollHistories(ObjectSerializer, NodeState)
   */
  public void storePollHistories(NodeState nodeState) {
    // CASTOR: change to makeObjectSerializer() when Castor is phased out
    storePollHistories(makePollHistoriesSerializer(), nodeState);
  }

  /**
   * <p>Stores the poll histories associated with the node state
   * using the given serializer.</p>
   * @param serializer A serializer instance.
   * @param nodeState  A node state instance.
   * @throws RepositoryStateException if an error condition arises.
   */
  void storePollHistories(ObjectSerializer serializer,
                          NodeState nodeState) {
    CachedUrlSet cus = nodeState.getCachedUrlSet();
    if (logger.isDebug3()) {
      logger.debug3("Storing histories for CUS '" + cus.getUrl() + "'.");
    }

    try {
      // Can throw MalformedURLException
      File file = prepareFile(getNodeLocation(cus), HISTORY_FILE_NAME);

      // CASTOR: only the else part stays
      if (isCastorMode()) {
        NodeHistoryBean nhb = new NodeHistoryBean();
        List histories = ((NodeStateImpl)nodeState).getPollHistoryList();
        nhb.historyBeans = NodeHistoryBean.fromListToBeanList(histories);
        serializer.serialize(file, nhb); // nhb is LockssSerializable
      }
      else {
        serializer.serialize(
            file,
            (Serializable)((NodeStateImpl)nodeState).getPollHistoryList()
        );
      }
    }
    catch (Exception e) {
      logger.error("Could not store poll history", e);
      throw new RepositoryStateException("Could not store poll history.");
    }
  }

  /**
   * <p>Computes the node location from a CUS URL. Uses
   * LockssRepositoryImpl static functions.</p>
   * @param cus A CachedUrlSet instance.
   * @return The CUS' file system location.
   * @throws MalformedURLException
   */
  protected String getNodeLocation(CachedUrlSet cus)
      throws MalformedURLException {
    String urlStr = (String)cus.getUrl();
    if (AuUrl.isAuUrl(urlStr)) {
      return rootLocation;
    } else {
      return LockssRepositoryImpl.mapUrlToFileLocation(rootLocation,
          LockssRepositoryImpl.canonicalizePath(urlStr));
    }
  }


  /**
   * Checks the file system to see if name updates are necessary.  Currently
   * converts from 'au_state.xml' to '#au_state.xml', and similarly with
   * the other state files.
   */
  void checkFileChange() {
    // XXX This is now here as a model for possible future conversions
    // XXX This version has the problem that if the au_state file doesn't
    // exist under either name, the recursion happens at every startup.
    if ((theDaemon==null) || (theDaemon.getPluginManager()==null)) {
      // abort if null, for test code
      return;
    }
    File topDir = new File(rootLocation);
    File topDirState = new File(topDir, AU_FILE_NAME);
    // if the new version doesn't exist, post-order recurse
    if (!topDirState.exists()) {
      logger.info("Older file versions being used; updating to current names");
      try {
        File auCusDir = new File(getNodeLocation(storedAu.getAuCachedUrlSet()));
        if (auCusDir.isDirectory()) {
          recurseFileChange(auCusDir);
        }
      } catch (MalformedURLException mue) {
        logger.error("Error updating from old state filenames: "+mue);
        return;
      }
    }
    // finish by fixing top level values
    File oldDamageFile = new File(topDir, "damaged_nodes.xml");
    if (oldDamageFile.exists()) {
      oldDamageFile.renameTo(new File(topDir, DAMAGED_NODES_FILE_NAME));
    }
    File oldAuState = new File(topDir, "au_state.xml");
    if (oldAuState.exists()) {
      oldAuState.renameTo(topDirState);
    }
    logger.debug("Finished updating.");
  }

  /**
   * Recursively checks for name changes.
   * @param nodeDir File
   * @throws MalformedURLException
   */
  void recurseFileChange(File nodeDir) throws MalformedURLException {
    File[] children = nodeDir.listFiles();
    for (int ii=0; ii<children.length; ii++) {
      // post-order recursion
      if (children[ii].isDirectory()) {
        recurseFileChange(children[ii]);
      }
    }

    // finish by fixing own values
    File oldNodeState = new File(nodeDir, "nodestate.xml");
    if (oldNodeState.exists()) {
      oldNodeState.renameTo(new File(nodeDir, NODE_FILE_NAME));
    }
    File oldHistoryFile = new File(nodeDir, "history.xml");
    if (oldHistoryFile.exists()) {
      oldHistoryFile.renameTo(new File(nodeDir, HISTORY_FILE_NAME));
    }
  }

  /**
   * <p>Name of top directory in which the histories are stored.</p>
   */
  public static final String HISTORY_ROOT_NAME = "cache";

  /**
   * <p>Configuration parameter name for Lockss history location.</p>
   */
  public static final String PARAM_HISTORY_LOCATION = Configuration.PREFIX + "history.location";

  /**
   * <p>The AU state file name.</p>
   */
  static final String AU_FILE_NAME = "#au_state.xml";

  /**
   * <p>The damaged nodes file name.</p>
   */
  static final String DAMAGED_NODES_FILE_NAME = "#damaged_nodes.xml";

  /**
   * <p>The history file name.</p>
   */
  static final String HISTORY_FILE_NAME = "#history.xml";

  /**
   * <p>The identity agreement list file name.</p>
   */
  static final String IDENTITY_AGREEMENT_FILE_NAME = "#id_agreement.xml";

  /**
   * <p>Mapping file for polls.</p>
   */
  static final String MAPPING_FILE_NAME = "/org/lockss/state/pollmapping.xml";

  /**
   * <p>All relevant mapping files used by this class.</p>
   */
  static final String[] MAPPING_FILES = {
      MAPPING_FILE_NAME,
      ExternalizableMap.MAPPING_FILE_NAME,
      IdentityManager.MAPPING_FILE_NAME
  };


  /**
   * <p>The node state file name.</p>
   */
  static final String NODE_FILE_NAME = "#nodestate.xml";

  /**
   * <p>A logger for use by this class.</p>
   */
  private static Logger logger = Logger.getLogger("HistoryRepository");

  /**
   * <p>Factory method to create new HistoryRepository instances.</p>
   * @param au The {@link ArchivalUnit}.
   * @return A new HistoryRepository instance.
   */
  public static HistoryRepository createNewHistoryRepository(ArchivalUnit au) {
    String root = LockssRepositoryImpl.getRepositoryRoot(au);
    return
      new HistoryRepositoryImpl(au,
                                LockssRepositoryImpl.mapAuToFileLocation(root,
                                                                         au));
  }

  /**
   * <p>Retrieves the current serialization mode.</p>
   * @return A mode constant from {@link CXSerializer}.
   */
  private static int getSerializationMode() {
    // CASTOR: Phase out with Castor
    return CXSerializer.getModeFromConfiguration();
  }

  /**
   * <p>Determines if the CXSerializer-based daemon is running in
   * Castor mode.</p>
   * @return true if and only if the underlying CXSerializer is
   *         configured to run in Castor mode.
   * @see CXSerializer#CASTOR_MODE
   */
  private static boolean isCastorMode() {
    return getSerializationMode() == CXSerializer.CASTOR_MODE;
  }

  /**
   * <p>Builds a new serializer for AU state.</p>
   * @return A serializer for AU state.
   */
  private ObjectSerializer makeAuStateSerializer() {
    // CASTOR: Phase out with Castor
    return makeObjectSerializer(AuStateBean.class);
  }

  /**
   * <p>Builds a new serializer for damaged node sets.</p>
   * @return A serializer for damaged node sets.
   */
  private ObjectSerializer makeDamagedNodeSetSerializer() {
    // CASTOR: Phase out with Castor
    return makeObjectSerializer(DamagedNodeSet.class);
  }

  /**
   * <p>Builds a new serializer for identity agreement lists.</p>
   * @return A serializer for identity agreement lists..
   */
  private ObjectSerializer makeIdentityAgreementListSerializer() {
    // CASTOR: Phase out with Castor
    return makeObjectSerializer(IdentityAgreementList.class);
  }

  /**
   * <p>Builds a new serializer for poll histories.</p>
   * @return A serializer for poll histories.
   */
  private ObjectSerializer makeNodeStateSerializer() {
    // CASTOR: Phase out with Castor
    return makeObjectSerializer(NodeStateBean.class);
  }

  /**
   * <p>Builds a new object serializer, suitable for the given class,
   * using the various mapping files referenced by
   * {@link #MAPPING_FILES}.</p>
   * @param cla The class of objects being serialized/deserialized.
   * @return A new object serializer ready to process objects of type
   *         <code>cla</code>.
   */
  private ObjectSerializer makeObjectSerializer(Class cla) {
    // CASTOR: Remove parameter; return an XStreamSerializer
    CXSerializer serializer =
      new CXSerializer(theDaemon, CastorSerializer.getMapping(MAPPING_FILES), cla);
    serializer.setCurrentMode(getSerializationMode());
    return serializer;
  }

  /**
   * <p>Builds a new serializer for poll histories.</p>
   * @return A serializer for poll histories.
   */
  private ObjectSerializer makePollHistoriesSerializer() {
    // CASTOR: Phase out with Castor
    return makeObjectSerializer(NodeHistoryBean.class);
  }

  /**
   * <p>Instantiates a {@link File} instance with the given prefix and
   * suffix, creating the path of directories denoted by the prefix if
   * needed by calling {@link File#mkdirs}.</p>
   * @param parent The path prefix.
   * @param child  The file name.
   * @return A new file instance with the prefix appropriately
   *         created.
   */
  private static File prepareFile(String parent, String child) {
    File parentFile = new File(parent);
    if (!parentFile.exists()) { parentFile.mkdirs(); }
    return new File(parentFile, child);
  }

  /**
   * <p>Might unwrap an object returning from serialization so that
   * it comes back in a form that is expected by deserialization
   * code.</p>
   * @param obj The object returning from serialized form.
   * @return An unwrapped object.
   */
  private static Object unwrap(Object obj) {
    // CASTOR: Phase out with Castor
    if (obj == null) {
      return null;
    }
    else if (obj instanceof IdentityAgreementList) {
      return ((IdentityAgreementList)obj).getList();
    }
    else if (obj instanceof NodeHistoryBean) {
      List histBeans = ((NodeHistoryBean)obj).getHistoryBeans();
      return NodeHistoryBean.fromBeanListToList(histBeans);
    }
    else {
      return obj;
    }
  }

  /**
   * <p>Might unwrap an object returning from serialization so that
   * it comes back in a form that is expected by
   * {@link HistoryRepositoryImpl#loadNodeState(CachedUrlSet)}.</p>
   * @param obj  The object returning from serialized form.
   * @param cus  The CachedUrlSet instance sometimes needed to unwrap
   *             obj.
   * @param hist The HistoryRepository instance sometimes needed to
   *             unwrap obj.
   * @return An unwrapped NodeStateImpl instance.
   */
  private static Object unwrap(Object obj,
                               CachedUrlSet cus,
                               HistoryRepository hist) {
    // CASTOR: Phase out with Castor
    if (obj == null) {
      return null;
    }
    else if (obj instanceof NodeStateBean) {
      return new NodeStateImpl(cus, (NodeStateBean)obj, hist);
    }
    else {
      return obj;
    }
  }

  /**
   * <p>Might wrap an AuState into an AuStateBean.</p>
   * @param auState An AuState instance.
   * @return An object suitable for serialization.
   */
  private static LockssSerializable wrap(AuState auState) {
    // CASTOR: Phase out with Castor
    if (isCastorMode()) { return new AuStateBean(auState); }
    else                { return auState; }
  }

  /**
   * <p>Might wrap a List into an IdentityAgreementList.</p>
   * @param idList An identity agreement list.
   * @return An object suitable for serialization.
   */
  private static Serializable wrap(List idList) {
    // CASTOR: Phase out with Castor
    if (isCastorMode()) { return new IdentityAgreementList(idList); }
    else                { return (Serializable)idList; }
  }

  /**
   * <p>Might wrap a NodeState into either a NodeHistoryBean.</p>
   * @param nodeState A NodeState instance.
   * @return An object suitable for serialization.
   */
  private static LockssSerializable wrap(NodeState nodeState) {
    // CASTOR: Phase out with Castor
    if (isCastorMode()) {
      return new NodeStateBean(nodeState);
    }
    else {
      return (NodeStateImpl)nodeState;
    }
  }

}
