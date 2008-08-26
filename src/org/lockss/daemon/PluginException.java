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

package org.lockss.daemon;

/** Checked exception hierarchy for errors in plugin code. */
public class PluginException extends Exception {

  public PluginException() {
    super();
  }

  public PluginException(String msg) {
    super(msg);
  }

  public PluginException(Throwable cause) {
    super(cause);
  }

  public PluginException(String msg, Throwable cause) {
    super(msg, cause);
  }

  /** Thrown by wrappers for plugin java code in response to a
   * LinkageError */
  public static class LinkageError extends PluginException {

    public LinkageError() {
      super();
    }

    public LinkageError(String msg) {
      super(msg);
    }

    public LinkageError(Throwable cause) {
      super(cause);
    }

    public LinkageError(String msg, Throwable cause) {
      super(msg, cause);
    }
  }

  /** Thrown internally by PluginMAnager if plugin requires a more recent
   * daemon */
  public static class IncompatibleDaemonVersion extends PluginException {

    public IncompatibleDaemonVersion() {
      super();
    }

    public IncompatibleDaemonVersion(String msg) {
      super(msg);
    }

    public IncompatibleDaemonVersion(Throwable cause) {
      super(cause);
    }

    public IncompatibleDaemonVersion(String msg, Throwable cause) {
      super(msg, cause);
    }
  }

  /** Thrown by definable plugin code if there's something illegal about a
   * plugin's definition.  Should be changed to a checked exception */
  public static class InvalidDefinition extends RuntimeException {

    public InvalidDefinition() {
      super();
    }

    public InvalidDefinition(String msg) {
      super(msg);
    }

    public InvalidDefinition(Throwable cause) {
      super(cause);
    }

    public InvalidDefinition(String msg, Throwable cause) {
      super(msg, cause);
    }
  }

  /** Thrown internall by PluginManager if it can't find a plugin */
  public static class PluginNotFound extends PluginException {

    public PluginNotFound() {
      super();
    }

    public PluginNotFound(String msg) {
      super(msg);
    }

    public PluginNotFound(Throwable cause) {
      super(cause);
    }

    public PluginNotFound(String msg, Throwable cause) {
      super(msg, cause);
    }
  }

  /** Exception thrown by daemon methods when illegal plugin behavior is
   * detected, and no more specific exception is appropriate.  This is
   * generally not thrown by plugin code. */
  public static class BehaviorException extends PluginException {
    private Throwable causeException;

    public BehaviorException() {
      super();
    }

    public BehaviorException(String msg) {
      super(msg);
    }

    public BehaviorException(Throwable cause) {
      super(cause);
    }

    public BehaviorException(String msg, Throwable cause) {
      super(msg, cause);
    }
  }

}

