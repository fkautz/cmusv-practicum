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
  private Logger log=Logger.getLogger("BaseLockssManager");

  public void initService(LockssDaemon daemon) throws LockssDaemonException {
    if(theManager == null) {
      theDaemon = daemon;
      theManager = this;
    }
    else {
      throw new LockssDaemonException("Multiple Instantiation.");
    }
  }

  public void startService() {
  }

  public void stopService() {
    // checkpoint here
    unregisterConfig();
    theManager = null;
  }

  protected LockssDaemon getDaemon() {
    return theDaemon;
  }

  protected void registerConfigCallback(Configuration.Callback callback) {
    if(callback == null || this.configCallback != null) {
      throw new LockssDaemonException("Invalid callback registration: "
                                       + callback);
    }
    configCallback = callback;
    Configuration.registerConfigurationCallback(configCallback);
  }

  protected void registerDefaultConfigCallback() {
    configCallback = new DefaultConfigCallback();
    Configuration.registerConfigurationCallback(configCallback);
  }

  protected void unregisterConfig() {
    if(configCallback != null) {
      Configuration.unregisterConfigurationCallback(configCallback);
      configCallback = null;
    }
  }

  protected void setConfig(Configuration newConfig,
                           Configuration oldConfig,
                           Set changedKeys) {
    /** override to actually set the config **/
    log.error("setConfig called, should be overridden for correct behaviour");

  }

  class DefaultConfigCallback implements Configuration.Callback {
    public void configurationChanged(Configuration newConfig,
                                     Configuration oldConfig,
                                     Set changedKeys) {
      setConfig(newConfig, oldConfig, changedKeys);
    }
  }
}
