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
package org.lockss.protocol.psm;

/**
 * Root of Protocol State Machine exceptions.  For now these are all
 * unchecked exceptions, but that might change
 */
public class PsmException extends RuntimeException {
  private Throwable nestedException;

  /** Create a PsmException with the supplied message. */
  public PsmException(String msg) {
    super(msg);
  }

  /** Create a PsmException wrapping the throwable. */
  public PsmException(Throwable nested) {
    super(nested.toString());
    this.nestedException = nested;
  }

  /** Create a PsmException with the supplied message, wrapping the
   * throwable. */
  public PsmException(String msg, Throwable nested) {
    super(msg);
    this.nestedException = nested;
  }

  /** Return the wrapped exception, if any */
  public Throwable getNestedException() {
    return nestedException;
  }

  /** Print the nested excpetion's stack track is preference to ours. */
  public void printStackTrace() {
    if (nestedException != null) {
      nestedException.printStackTrace();
    } else {
      super.printStackTrace();
    }
  }

  /** Print the nested excpetion's stack track is preference to ours. */
  public void printStackTrace(java.io.PrintStream s) {
    if (nestedException != null) {
      nestedException.printStackTrace();
    } else {
      super.printStackTrace();
    }
  }

  /** Print the nested excpetion's stack track is preference to ours. */
  public void printStackTrace(java.io.PrintWriter s) {
    if (nestedException != null) {
      nestedException.printStackTrace();
    } else {
      super.printStackTrace();
    }
  }

  /** Something about the state machine itself is illegal.  Thrown by state
   * machine component constructors */
  public static class IllegalStateMachine extends PsmException {
    public IllegalStateMachine(String msg) {
      super(msg);
    }
  }

  /** An event was signalled for which the current state has no response.
   * Thrown by the state machine interpreter */
  public static class UnknownEvent extends PsmException {
    public UnknownEvent(String msg) {
      super(msg);
    }
  }

  /** The maximum number of consecutive events has been signalled without
   * the state machine waiting.  Probably indicates a loop in the state
   * machine.  Thrown by the state machine interpreter */
  public static class MaxChainedEvents extends PsmException {
    public MaxChainedEvents(String msg) {
      super(msg);
    }
  }

  /** An action threw a RuntimeException.  Thrown by the state machine
   * interpreter */
  public static class ActionError extends PsmException {
    public ActionError(String msg, Throwable t) {
      super(msg, t);
    }
  }

}
