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
package org.lockss.app;

import org.lockss.daemon.*;
import java.util.*;
import org.lockss.util.*;

/**
 * Base implementation of LockssManager
 */

public abstract class BaseLockssManager implements LockssManager {

  private LockssManager theManager = null;
  protected LockssDaemon theDaemon = null;
  private Configuration.Callback configCallback;
  private static Logger log=Logger.getLogger("BaseLockssManager");

  /**
   * Called to initialize each service in turn.  Service should extend
   * this to perform any internal initialization necessary before service
   * can be called from outside.  No calls to other services may be made in
   * this method.
   * @param daemon the {@link LockssDaemon}
   * @throws LockssDaemonException
   */
  public void initService(LockssDaemon daemon) throws LockssDaemonException {
    if(theManager == null) {
      theDaemon = daemon;
      theManager = this;
      registerDefaultConfigCallback();
    }
    else {
      throw new LockssDaemonException("Multiple Instantiation.");
    }
  }

  /** Called to start each service in turn, after all services have been
   * initialized.  Service should extend this to perform any startup
   * necessary. */
  public void startService() {
  }

  /** Called to stop a service.  Service should extend this to stop all
   * ongoing activity (<i>eg</i>, threads). */
  public void stopService() {
    // checkpoint here
    unregisterConfig();
    theManager = null;
  }

  protected LockssDaemon getDaemon() {
    return theDaemon;
  }

  /**
   * Return true iff all the daemon services have been initialized.
   * @return true if the daemon is inited
   */
  protected boolean isDaemonInited() {
    return theDaemon.isDaemonInited();
  }

  private void registerConfigCallback(Configuration.Callback callback) {
    if(callback == null || this.configCallback != null) {
      throw new LockssDaemonException("Invalid callback registration: "
                                       + callback);
    }
    configCallback = callback;
    Configuration.registerConfigurationCallback(configCallback);
  }

  private void registerDefaultConfigCallback() {
    configCallback = new DefaultConfigCallback();
    Configuration.registerConfigurationCallback(configCallback);
  }

  private void unregisterConfig() {
    if(configCallback != null) {
      Configuration.unregisterConfigurationCallback(configCallback);
      configCallback = null;
    }
  }

  /** Convenience method to (re)invoke the manager's setConfig(new, old,
   * ...) method with the current config and empty previous config. */
  protected void resetConfig() {
    Configuration cur = Configuration.getCurrentConfig();
    setConfig(cur, Configuration.EMPTY_CONFIGURATION, cur.keySet());
  }

  /** Managers must implement this method.  It is called once at daemon
   * init time (during initService()) and again whenever the current
   * configuration changes.  This method should not invoke other services
   * unless isDaemonInited() is true.
   * @param newConfig the new {@link Configuration}
   * @param prevConfig the previous {@link Configuration}
   * @param changedKeys the {@link Set} of changed keys
   */
  protected abstract void setConfig(Configuration newConfig,
				    Configuration prevConfig,
				    Set changedKeys);

  private class DefaultConfigCallback implements Configuration.Callback {
    public void configurationChanged(Configuration newConfig,
                                     Configuration prevConfig,
                                     Set changedKeys) {
      setConfig(newConfig, prevConfig, changedKeys);
    }
  }
}
