/*
 * $Id$
 */

/*

Copyright (c) 2000-2002 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.util;
import java.util.*;
import java.io.*;
import java.net.*;
import org.lockss.daemon.*;

/**
 * <code>Logger</code> provides message logging functions.
 * All logging is done via a <code>Logger</code> instance obtained from
 * <code>Logger.getLogger()</code>.  A message is output if its severity
 * level is at least as great as the minimum severity level set in the
 * log instance.  A log's minimum severity can be set with
 * <code>setLevel()</code>, or with the configuration parameter
 * <tt>org.lockss.log.level.<i>log-name</i></tt>.
 * Output is sent to possibly many targets - see <code>addTarget()</code>
 */
public class Logger {

  static final String PREFIX = Configuration.PREFIX + "log.";
  static final String PARAM_DEFAULT_LEVEL = PREFIX + "default.level";
  static final String PARAM_LOG_LEVEL = PREFIX + "<logname>.level";
  static final String PARAM_LOG_TARGETS = PREFIX + "targets";

  /** Critical errors, need immediate attention */
  public static final int LEVEL_CRITICAL = 1;
  /** Errors, system nay not operate correctly, but won't damage anything */
  public static final int LEVEL_ERROR = 2;
  /** Warnings are for conditions that don't prevent the system from
      continuing to run. */
  public static final int LEVEL_WARNING = 3;
  /** Informative messages. */
  public static final int LEVEL_INFO = 4;
  /** Debugging messages. */
  public static final int LEVEL_DEBUG = 5;

  // Mapping between numeric level and string
  static LevelDescr levelDescrs[] = {
    new LevelDescr(LEVEL_CRITICAL, "Critical"),
    new LevelDescr(LEVEL_ERROR, "Error"),
    new LevelDescr(LEVEL_WARNING, "Warning"),
    new LevelDescr(LEVEL_INFO, "Info"),
    new LevelDescr(LEVEL_DEBUG, "Debug"),
  };

  // Default default log level if config parameter not set.
  private static final int DEFAULT_LEVEL = LEVEL_INFO;

  private static final Map logs = new HashMap();
  private static List targets = new ArrayList();

  // allow default level to be specified on command line
  private static int defaultLevel;

  private static boolean deferredInitDone = false;

  private static ThreadLocal targetStack = new ThreadLocal() {
      protected Object initialValue() {
	return new Vector();
      }
    };

  private static Logger myLog;

  private int level;			// this log's level
  private String name;			// this log's name
  
  static {
    // until we get configured, output to default target
    defaultTarget();
    setInitialDefaultLevel();
  }

  // tk - make this private and use PrivilegedAccessor (which needs
  // to be enhanced to handle constructors)
  /** Constructor not intended for outside use - not private only so
   * test classes can use it
   */
  Logger(int level, String name) {
    this.level = level;
    this.name = name;
  }

  /**
   * Logger factory.  Return the unique instance
   * of <code>Logger</code> with the given name, creating it if necessary.
   * @param name identifies the log instance, appears in output
   */
  public static Logger getLogger(String name) {
    if (!deferredInitDone) {
      // set this true FIRST, as this method will be called recursively by
      // deferredInit()
      deferredInitDone = true;
      deferredInit();
    }
    if (name == null) {
      name = genName();
    }
    return getLogger(name, getConfiguredLevel(name));
  }

  private static void deferredInit() {
    // Can't call this until Configuration class is fully loaded.
    registerConfigCallback();
    myLog = Logger.getLogger("Logger");
  }

  /**
   * Special purpose Logger factory.  Return the unique instance
   * of <code>Logger</code> with the given name, creating it if necessary.
   * This is here primarily so <Conde>Configuration</code> can create a
   * log without being invoked recursively, which causes its class
   * initialization to not complete correctly.
   * @param name identifies the log instance, appears in output
   * @param level the initial log level (<code>Logger.LEVEL_XXX</code>).
   */
  public static Logger getLogger(String name, int level) {
    // This method MUST NOT make any reference to Configuration !!
    if (name == null) {
      name = genName();
    }
    Logger l = (Logger)logs.get(name);
    if (l == null) {
      l = new Logger(level, name);
      logs.put(name, l);
    }
    return l;
  }

  /** Get the log level for the given log name from the configuration.
   */
  static int getConfiguredLevel(String name) {
    String levelName =
      Configuration.getParam(StringUtil.replaceString(PARAM_LOG_LEVEL,
						      "<logname>", name),
			     Configuration.getParam(PARAM_DEFAULT_LEVEL));
    int level = 0;
    try {
      level = levelOf(levelName);
    } catch (IllegalArgumentException e) {
      level = defaultLevel;
    }
    return level;
  }

  static int uncnt = 0;
  static String genName() {
    return "Unnamed" + ++uncnt;
  }

  static Vector levelNames = null;

  // Make vector for fast level -> name lookup
  private static void initLevelNames() {
    levelNames = new Vector();
    for (int ix = 0; ix < levelDescrs.length; ix++) {
      LevelDescr l = levelDescrs[ix];
      if (levelNames.size() < l.level + 1) {
	levelNames.setSize(l.level + 1);
      }
      levelNames.set(l.level, l.name);
    }
  }

  /** Get the initial default log level, specified by the
   * org.lockss.defaultLogLevel system property if present, or DEFAULT_LEVEL
   */
  public static int getInitialDefaultLevel() {
    String s = System.getProperty("org.lockss.defaultLogLevel");
    int l = DEFAULT_LEVEL;
    if (s != null && !"".equals(s)) {
      try {
	l = levelOf(s);
      } catch (IllegalArgumentException e) {
	// no action
      }
    }
    return l;
  }

  /** Return numeric log level (<code>Logger.LEVEL_XXX</code>) for given name.
   */
  static int levelOf(String name) {
    for (int ix = 0; ix < levelDescrs.length; ix++) {
      LevelDescr l = levelDescrs[ix];
      if (l.name.equalsIgnoreCase(name)) {
	return l.level;
      }
    }
    throw new IllegalArgumentException("Log level not found: " + name);
  }

  /** Return name of given log level (<code>Logger.LEVEL_XXX</code>).
   */
  public static String nameOf(int level) {
    if (levelNames == null) {
      initLevelNames();
    }
    String name = null;
    if (level >= 0 && level < levelNames.size()) {
      name = (String)levelNames.get(level);
    }
    if (name == null) {
      name = "Unknown";
    }
    return name;
  }

  /**
   * Set a single output target for all loggers.
   * @param target object that implements <code>LogTarget</code> interface.
   */
  static void setTarget(LogTarget target) {
    targets.clear();
    addTarget(target);
  }

  /**
   * Set the default output target for all loggers.  Used by unit tests
   * to reset after tests.
   */
  static void defaultTarget() {
    setTarget(new StdErrTarget());
  }

  /**
   * Add an output target to all loggers.
   * @param target instance of <code>LogTarget</code> implementation.
   */
  public static void addTarget(LogTarget target) {
    addTargetTo(target, targets);
  }

  /**
   * Add an output target to a target list.  All target adding should
   * eventually call this
   * @param target instance of <code>LogTarget</code> implementation.
   * @param tgts list of targets to which target will be added after it is
   * initialize.
   */
  static void addTargetTo(LogTarget target, List tgts) {
    if (!tgts.contains(target)) {
      try {
	target.init();
	tgts.add(target);		// add only if init succeeded
      } catch (Exception e) {
	myLog.error("Log target " + target + " threw", e);
      }
    }
  }

  /**
   * Install the targets, replacing any current targets
   * @param newTargets list of targets to be installed
   */
  public static void setTargets(List newTargets) {
    List tgts = new ArrayList();
    // make list of initialized targets
    for (Iterator iter = newTargets.iterator(); iter.hasNext(); ) {
      addTargetTo((LogTarget)iter.next(), tgts);
    }
    if (!tgts.isEmpty()) {		// ensure targets is never empty
      targets = tgts;
    }
  }

  //private

  /** Set the initial default log level to that specified by the
   * org.lockss.defaultLogLevel system property if present, or DEFAULT_LEVEL
   */
  private static void setInitialDefaultLevel() {
    defaultLevel = getInitialDefaultLevel();
  }

  /** Remove the first line of the stack trace, iff it duplicates the end
   * of the exception message */
  static String trimStackTrace(String msg, String trace) {
    int pos = trace.indexOf("\n");
    if (pos > 0) {
      String l1 = trace.substring(0, pos);
      if (msg.endsWith(l1)) {
	return trace.substring(pos + 1);
      }
    }
    return trace;
  }

  /** Translate an exception's stack trace to a string.
   */
  private static String stackTraceString(Throwable exp) {
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    exp.printStackTrace(pw);
    return sw.toString();
  }

  /** Register callback to reset log levels when config changes
   */
  private static void registerConfigCallback() {
    Configuration.registerConfigurationCallback(new Configuration.Callback() {
	public void configurationChanged(Configuration oldConfig,
					 Configuration newConfig,
					 Set changedKeys) {
	  setAllLogLevels();
	  if (changedKeys.contains(PARAM_LOG_TARGETS)) {
	    setLogTargets();
	  }
	}
      });
  }

  /** Set log level of all logs to the currently configured value
   */
  private static void setAllLogLevels() {
    for (Iterator iter = logs.values().iterator();
	 iter.hasNext(); ) {
      Logger l = (Logger)iter.next();
      l.setLevel(getConfiguredLevel(l.name));
    }
  }

  /** Change list of targets to that specified by config param
   */
  private static void setLogTargets() {
    List tgts =
      targetListFromString(Configuration.getParam(PARAM_LOG_TARGETS));
    if (tgts != null && !tgts.isEmpty()) {
      setTargets(tgts);
    } else {
      myLog.error("Leaving log targets unchanged");
    }
  }

  /** Convert colon-separated string of log target class names to a list
   * of log target instances
   * @return List of instances, or null if any errors occurred
   */
  static List targetListFromString(String s) {
    boolean err = false;
    List tgts = new ArrayList();
    Vector names = StringUtil.breakAt(s, ':');
    for (Iterator iter = names.iterator(); iter.hasNext(); ) {
      String targetName = (String)iter.next();
      try {
	Class targetClass = Class.forName(targetName);
	LogTarget target = (LogTarget)targetClass.newInstance();
	tgts.add(target);
      } catch (Exception e) {
	myLog.error("Can't create log target \"" + targetName + "\": " +
		    e.toString());
	err = true;
      }
    }
    return err ? null : tgts;
  }

  static List getTargets() {
    return targets;
  }

  /**
   * Set minimum severity level logged by this log
   * @param level <code>Logger.LEVEL_XXX</code>
   */
  public void setLevel(int level) {
    if (this.level != level) {
//        info("Changing log level to " + nameOf(level));
      this.level = level;
    }
  }

  /**
   * Return true if this log is logging at or above specified level
   * Use this in cases where generating the log message is expensive,
   * to avoid the overhead when the message will not be output.
   * @param level (<code>Logger.LEVEL_XXX</code>)
   */
  boolean isLevel(int level) {
    return this.level >= level;
  }

  /** This is the common case of </code>isLevel()</code> */
  public boolean isDebug() {
    return isLevel(LEVEL_DEBUG);
  }

  /**
   * Return true if this log is logging at or above specified level
   * @param level name
   */
  public boolean isLevel(String level) {
    return this.level >= levelOf(level);
  }

  /**
   * Log a message with the specified log level
   * @param level log level (<code>Logger.LEVEL_XXX</code>)
   * @param msg log message
   * @param e <code>Throwable</code>
   */
  public void log(int level, String msg, Throwable e) {
    if (isLevel(level)) {
      StringBuffer sb = new StringBuffer();
      sb.append(name);
      sb.append(": ");
      sb.append(msg);
      if (e != null) {
	// toString() sometimes contains more info than getMessage()
	String emsg = e.toString();
	sb.append(": ");
	sb.append(emsg);
	sb.append("\n    ");
//  	sb.append(stackTraceString(e));
	sb.append(trimStackTrace(emsg, stackTraceString(e)));
      }
      writeMsg(level, sb.toString());
    }
  }

  /**
   * Log a message with the specified log level
   * @param level log level (<code>Logger.LEVEL_XXX</code>)
   * @param msg log message
   */
  public void log(int level, String msg) {
    log(level, msg, null);
  }

  // Invoke all the targets to write a message.
  // Maintain a thread-local stack of targets, to avoid invoking any
  // target recursively.
  private void writeMsg(int level, String msg) {
    Iterator iter = targets.iterator();
    while (iter.hasNext()) {
      LogTarget target = (LogTarget)iter.next();
      Vector ts = (Vector)targetStack.get();
      if ( ! ts.contains(target)) {
	try {
	  ts.add(target);
	  target.handleMessage(this, level, msg);
	} catch (Exception e) {
	  // should log this?
	} finally {
	  ts.remove(target);
	}
      }
    }
  }

  // log instance methods
  /** Log a critical message */
  public void critical(String msg) {
    log(LEVEL_CRITICAL, msg, null);
  }

  /** Log a critical message with an exception backtrace */
  public void critical(String msg, Throwable e) {
    log(LEVEL_CRITICAL, msg, e);
  }

  /** Log an error message */
  public void error(String msg) {
    log(LEVEL_ERROR, msg, null);
  }

  /** Log an error message with an exception backtrace */
  public void error(String msg, Throwable e) {
    log(LEVEL_ERROR, msg, e);
  }

  /** Log a warning message */
  public void warning(String msg) {
    log(LEVEL_WARNING, msg, null);
  }

  /** Log a warning message with an exception backtrace */
  public void warning(String msg, Throwable e) {
    log(LEVEL_WARNING, msg, e);
  }

  /** Log an information message */
  public void info(String msg) {
    log(LEVEL_INFO, msg, null);
  }

  /** Log an information message with an exception backtrace */
  public void info(String msg, Throwable e) {
    log(LEVEL_INFO, msg, e);
  }

  /** Log a debug message */
  public void debug(String msg) {
    log(LEVEL_DEBUG, msg, null);
  }

  /** Log a debug message with an exception backtrace */
  public void debug(String msg, Throwable e) {
    log(LEVEL_DEBUG, msg, e);
  }

  // log level descriptor class
  private static class LevelDescr {
    int level;
    String name;

    LevelDescr(int level, String name) {
    this.level = level;
    this.name = name;
    }
  }

}
