/*
 * $Id$
 */

/*

Copyright (c) 2001-2002 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.daemon;

import java.util.*;
import java.io.*;
import java.net.*;
import java.text.*;
import org.mortbay.tools.*;
import org.lockss.util.*;

/** <code>Configuration</code> provides access to the LOCKSS configuration
 * parameters.  Instances of (concrete subclasses of)
 * <code>Configuration</code> hold a set of configuration parameters, and
 * have a standard set of accessors.  Static methods on this class provide
 * convenient access to parameter values in the "current" configuration;
 * these accessors all have <code>Param</code> in their name.  (If called
 * on a <code>Configuration</code> <i>instance</i>, they will return values
 * from the current configuration, not that instance.  So don't do that.)
 * */
public abstract class Configuration {
  /** The common prefix string of all LOCKSS configuration parameters. */
  public static final String PREFIX = "org.lockss.";

  // MUST pass in explicit log level to avoid recursive call back to
  // Configuration to get Config log level.
  protected static Logger log = Logger.getLogger("Config", Logger.LEVEL_INFO);

  private static List configChangedCallbacks = new ArrayList();

  private static List configUrlList;	// list of urls

  // Current configuration instance.
  // Start with an empty one to avoid errors in the static accessors.
  private static Configuration currentConfig = newConfiguration();

  private static HandlerThread handlerThread; // reload handler thread

  // Factory to create instance of appropriate class
  static Configuration newConfiguration() {
    return new ConfigurationPropTreeImpl();
  }

  /** Return current configuration */
  public static Configuration getCurrentConfig() {
    return currentConfig;
  }

  static void setCurrentConfig(Configuration newConfig) {
    if (newConfig == null) {
      log.warning("attempt to install null Configuration");
    }
    currentConfig = newConfig;
  }

  static void runCallbacks(Configuration oldConfig,
			   Configuration newConfig) {
    for (Iterator iter = configChangedCallbacks.iterator();
	 iter.hasNext();) {
      Callback c = (Callback)iter.next();
      try {
	c.configurationChanged(oldConfig, newConfig,
			       newConfig.differentKeys(oldConfig));
      } catch (Exception e) {
	log.error("callback threw", e);
      }
    }
  }

  static void setConfigUrls(List urls) {
    configUrlList = new ArrayList(urls);
  }

  static void setConfigUrls(String urls) {
    configUrlList = new ArrayList();
    for (StringTokenizer st = new StringTokenizer(urls);
	 st.hasMoreElements(); ) {
      String url = st.nextToken();
      configUrlList.add(url);
    }
  }

  /**
   * Return a new <code>Configuration</code> instance loaded from the
   * url list
   */
  static Configuration readConfig(List urlList) {
    if (urlList == null) {
      return null;
    }
    Configuration newConfig = newConfiguration();
    boolean gotIt = newConfig.loadList(urlList);
    return gotIt ? newConfig : null;
  }

  static boolean updateConfig() {
    Configuration newConfig = readConfig(configUrlList);
    return installConfig(newConfig);
  }

  static boolean installConfig(Configuration newConfig) {
    if (newConfig == null) {
      return false;
    }
    Configuration oldConfig = currentConfig;
    if (!oldConfig.isEmpty() && newConfig.equals(oldConfig)) {
      log.info("Config unchanged, not updated");
      return false;
    }
    setCurrentConfig(newConfig);
    log.info("Config updated");
    runCallbacks(oldConfig, newConfig);
    return true;
  }

  /**
   * Register a <code>Configuration.Callback</code>, which will be
   * called whenever the current configuration has changed.
   * @param c <code>Configuration.Callback</code> to add.
   */
  public static void
    registerConfigurationCallback(Callback c) {
    if (!configChangedCallbacks.contains(c)) {
      configChangedCallbacks.add(c);
    }
  }
      
  /**
   * Unregister a <code>Configuration.Callback</code>.
   * @param c <code>Configuration.Callback</code> to remove.
   */
  public static void
    unregisterConfigurationCallback(Callback c) {
    configChangedCallbacks.remove(c);
  }

  // instance methods

  /**
   * Try to load config from a list or urls
   * @return true iff properties were successfully loaded
   */
  boolean loadList(List urls) {
    for (Iterator iter = urls.iterator(); iter.hasNext();) {
      String url = (String)iter.next();
      try {
	load(url);
      } catch (IOException e) {
	// This load failed.  Fail the whole thing.
	log.warning("Couldn't load props from " + url + ": " + e.toString());
	reset();			// ensure config is empty
	return false;
      }
    }
    return true;
  }

  void load(String url) throws IOException {
    InputStream istr = UrlUtil.openInputStream(url);
    load(new BufferedInputStream(istr));
  }

  abstract boolean load(InputStream istr)
      throws IOException;

  abstract Set differentKeys(Configuration otherConfig);

  /** Return true iff config has no keys/ */
  public boolean isEmpty() {
    return !(keyIterator().hasNext());
  }

  /** Return the config value associated with <code>key</code>.
   * If the value is null or the key is missing, return <code>dfault</code>.
   */
  public String get(String key, String dfault) {
    String val = get(key);
    if (val == null) {
      val = dfault;
    }
    return val;
  }

  static Map boolStrings = new HashMap();
  static {
    boolStrings.put("true", Boolean.TRUE);
    boolStrings.put("yes", Boolean.TRUE);
    boolStrings.put("on", Boolean.TRUE);
    boolStrings.put("1", Boolean.TRUE);
    boolStrings.put("false", Boolean.FALSE);
    boolStrings.put("no", Boolean.FALSE);
    boolStrings.put("off", Boolean.FALSE);
    boolStrings.put("0", Boolean.FALSE);
  }

  private Boolean stringToBool(String s) {
    if (s == null) {
      return null;
    }
    Boolean res = (Boolean)boolStrings.get(s);
    if (res != null) {
      return res;
    } else {
      return (Boolean)boolStrings.get(s.toLowerCase());
    }
  }

  /** Return the config value as a boolean.
   * @throws Configuration.Error if the value is missing or
   * not parsable as a boolean.
   */
  public boolean getBoolean(String key) throws Error {
    String val = get(key);
    Boolean bool = stringToBool(val);
    if (bool != null) {
      return bool.booleanValue();
    }
    throw new Error("Not a boolean value: " + val);
  }

  /** Return the config value as a boolean.  If it's missing, return the
   * default value.  If it's present but not parsable as a boolean, log a
   * warning and return the default value.
   */
  public boolean getBoolean(String key, boolean dfault) {
    String val = get(key);
    if (val == null) {
      return dfault;
    }
    Boolean bool = stringToBool(val);
    if (bool != null) {
      return bool.booleanValue();
    }
    log.warning("getBoolean(\'" + key + "\") = \"" + val + "\"");
    return dfault;
  }

  /** Return the config value as an int.
   * @throws Configuration.Error if the value is missing or
   * not parsable as an int.
   */
  public int getInt(String key) throws Error {
    String val = get(key);
    try {
      return Integer.parseInt(val);
    } catch (NumberFormatException e) {
      throw new Error("Not an int value: " + val);
    }
  }

  /** Return the config value as an int.  If it's missing, return the
   * default value.  If it's present but not parsable as an int, log a
   * warning and return the default value
   */
  public int getInt(String key, int dfault) {
    String val = get(key);
    if (val == null) {
      return dfault;
    }
    try {
      return Integer.parseInt(val);
    } catch (NumberFormatException e) {
      log.warning("getInt(\'" + key + "\") = \"" + val + "\"");
      return dfault;
    }
  }

  // must be implemented by implementation subclass

  abstract void reset();

  /** return true iff the configurations have the same keys
   * with the same values.
   */
  public abstract boolean equals(Object c);

  /** Return the config value associated with <code>key</code>.
   * @return the string, or null if the key is not present
   * or its value is null.
   */
  public abstract String get(String key);

  /** Returns a Configuration instance containing all the keys at or
   * below <code>key</code>
   */
  public abstract Configuration getConfigTree(String key);

  /** Returns an <code>Iterator</code> over all the keys in the configuration.
   */
  public abstract Iterator keyIterator();

  /** Returns an <code>Iterator</code> over all the top level
     keys in the configuration.
   */
  public abstract Iterator nodeIterator();

  /** Returns an <code>Iterator</code> over the top-level keys in the
   * configuration subtree below <code>key</code>
   */
  public abstract Iterator nodeIterator(String key);

  // static convenience methods

  /** Static convenience method to get param from current configuration.
   * Don't accidentally use this on a <code>Configuration</code> instance.
   */
  public static String getParam(String key) {
    return currentConfig.get(key);
  }

  /** Static convenience method to get param from current configuration.
   * Don't accidentally use this on a <code>Configuration</code> instance.
   */
  public static String getParam(String key, String dfault) {
    return currentConfig.get(key, dfault);
  }

  /** Static convenience method to get param from current configuration.
   * Don't accidentally use this on a <code>Configuration</code> instance.
   */
  public static boolean getBooleanParam(String key) throws Error {
    return currentConfig.getBoolean(key);
  }

  /** Static convenience method to get param from current configuration.
   * Don't accidentally use this on a <code>Configuration</code> instance.
   */
  public static boolean getBooleanParam(String key, boolean dfault) {
    return currentConfig.getBoolean(key, dfault);
  }

  /** Static convenience method to get param from current configuration.
   * Don't accidentally use this on a <code>Configuration</code> instance.
   */
  public static int getIntParam(String key) throws Error {
    return currentConfig.getInt(key);
  }

  /** Static convenience method to get param from current configuration.
   * Don't accidentally use this on a <code>Configuration</code> instance.
   */
  public static int getIntParam(String key, int dfault) {
    return currentConfig.getInt(key, dfault);
  }

  /** Static convenience method to get a <code>Configuration</code>
   * subtree from the current configuration.
   * Don't accidentally use this on a <code>Configuration</code> instance.
   */
  public static Configuration paramConfigTree(String key) {
    return currentConfig.getConfigTree(key);
  }

  /** Static convenience method to get key iterator from the
   * current configuration.
   * Don't accidentally use this on a <code>Configuration</code> instance.
   */
  public static Iterator paramKeyIterator() {
    return currentConfig.keyIterator();
  }

  /** Static convenience method to get a node iterator from the
   * current configuration.
   * Don't accidentally use this on a <code>Configuration</code> instance.
   */
  public static Iterator paramNodeIterator(String key) {
    return currentConfig.nodeIterator(key);
  }

  public static void startHandler(List urls) {
    setConfigUrls(urls);
    startHandler();
  }

  public static void startHandler() {
    if (handlerThread != null) {
      log.warning("Handler already running; stopping old one first");
      stopHandler();
    } else {
      log.info("Starting handler");
    }
    handlerThread = new HandlerThread("ConfigHandler");
    handlerThread.start();
  }

  public static void stopHandler() {
    if (handlerThread != null) {
      log.info("Stopping handler");
      handlerThread.stopHandler();
      handlerThread = null;
    } else {
      log.warning("Attempt to stop handler when it isn't running");
    }
  }

  // Handler thread, periodicially reloads config

  private static class HandlerThread extends Thread {
    private long lastReload = 0;
    private boolean goOn = false;

    private HandlerThread(String name) {
      super(name);
    }

    public void run() {
      Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
      long reloadInterval = 600000;
      goOn = true;

      // repeat every 10ish minutes until first successful load, then
      // according to org.lockss.parameterReloadInterval, or 30 minutes.
      while (goOn) {
	if (updateConfig()) {
	  // true iff loaded config has changed
	  if (!goOn) {
	    break;
	  }
	  lastReload = System.currentTimeMillis();
	  //  	stopAndOrStartThings(true);
	  reloadInterval = Integer.getInteger(Configuration.PREFIX +
					      "parameterReloadInterval",
					      1800000).longValue();
	}
	ProbabilisticTimer nextReload =
	  new ProbabilisticTimer(reloadInterval, reloadInterval/4);
	log.info(nextReload.toString());
	if (goOn) {
	  nextReload.sleepUntil();
	}
      }
    }

    private void stopHandler() {
      goOn = false;
      this.interrupt();
    }
  }

  /**
   * The <code>Configuration.Callback</code> interface defines the
   * callback registered by clients of <code>Configuration</code>
   * who want to know when the configuration has changed.
   */
  public interface Callback {
    /**
     * Called to indicate that something in the configuration has changed.
     * It is called after the new config is installed as current.
     * @param newConfig  the new (just installed) <code>Configuration</code>.
     * @param oldConfig  the previous <code>Configuration</code>.
     * @param changedKeys  the set of keys whose value has changed.
     */
    public void configurationChanged(Configuration oldConfig,
				     Configuration newConfig,
				     Set changedKeys);
  }

  /** Exception thrown for errors in accessors. */
  public class Error extends Exception {
    public Error(String message) {
      super(message);
    }
  }
}
