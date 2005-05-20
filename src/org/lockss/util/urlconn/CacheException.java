/*
 * $Id$
 *

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

package org.lockss.util.urlconn;

import java.io.*;
import java.util.*;

/** Hierarchy of exceptions that may be returned from a plugin's {@link
 * org.lockss.plugin.UrlCacher#cache()} method, or its componenet methods
 * (<i>eg</i> {@link org.lockss.plugin.UrlCacher#getUncachedInputStream()}.
 * It is the plugin's responsibility to map all possible fetch or store
 * errors into one of these categories, so that the generic crawler can
 * handle the error in a standardized way. */
public class CacheException extends IOException {
  //Exceptions with this attribute will cause the crawl to be marked
  //as a failure
  public static final int ATTRIBUTE_FAIL = 1;

  //Exceptions with this attribute will cause the URL being fetched to be
  //retried a fixed number of times
  public static final int ATTRIBUTE_RETRY = 2;

  //Exceptions with this attribute will signal that we have a serious error
  //such as a permission problem or a site wide issue
  public static final int ATTRIBUTE_FATAL = 3;

  protected static boolean defaultSuppressStackTrace = true;

  protected String message;
  protected Exception nestedException = null;
  protected BitSet attributeBits = new BitSet();

  // Most of these exceptions are created at a known place (in
  // HttpResultMap) and there is no point in polluting logs with stack
  // traces.
  protected boolean suppressStackTrace = defaultSuppressStackTrace;

  public CacheException() {
    super();
    setAttributes();
  }

  public CacheException(String message) {
    super(message);
    this.message = message;
    setAttributes();
  }

  public static boolean
    setDefaultSuppressStackTrace(boolean defaultSuppress) {
    boolean res = defaultSuppressStackTrace;
    defaultSuppressStackTrace = defaultSuppress;
    return res;
  }

  void initMessage(String message) {
    this.message = message;
  }

  public String getMessage() {
    return message;
  }

  /** Return the nested (causal) exception, if any. */
  public Exception getNestedException() {
    return nestedException;
  }

  public boolean isAttributeSet(int attribute) {
    return attributeBits.get(attribute);
  }

  protected void setAttributes() {

  }

  /** If stack trace is suppressed, substitute the stack trace of the
   * nested exception, if any.  Should be cleaned up, but achieves the
   * desired effect for now. */
  public void printStackTrace() {
    if (!suppressStackTrace) {
      super.printStackTrace();
    } else if (nestedException != null) {
      nestedException.printStackTrace();
    } else {
      System.err.println(this);
    }
  }

  /** If stack trace is suppressed, substitute the stack trace of the
   * nested exception, if any.  Should be cleaned up, but achieves the
   * desired effect for now. */
  public void printStackTrace(java.io.PrintStream s) {
    if (!suppressStackTrace) {
      super.printStackTrace(s);
    } else if (nestedException != null) {
      nestedException.printStackTrace(s);
    } else {
      s.println(this);
    }
  }

  /** If stack trace is suppressed, substitute the stack trace of the
   * nested exception, if any.  Should be cleaned up, but achieves the
   * desired effect for now. */
  public void printStackTrace(java.io.PrintWriter s) {
    if (!suppressStackTrace) {
      super.printStackTrace(s);
    } else if (nestedException != null) {
      nestedException.printStackTrace(s);
    } else {
      s.println(this);
    }
  }

  /** Unknown response code */
  public static class UnknownCodeException extends CacheException {

    public UnknownCodeException() {
      super();
    }

    public UnknownCodeException(String message) {
      super(message);
    }

    protected void setAttributes() {
      attributeBits.set(ATTRIBUTE_FAIL);
    }
  }

  /** Any error result that should be retried */
  public static class RetryableException
      extends CacheException {
    public RetryableException() {
      super();
    }

    public RetryableException(String message) {
      super(message);
    }

    protected void setAttributes() {
      attributeBits.set(ATTRIBUTE_RETRY);
      attributeBits.set(ATTRIBUTE_FAIL);
    }
  }

  /** An error that should be retried with the same URL */
  public static class RetrySameUrlException
      extends RetryableException {
    public RetrySameUrlException() {
      super();
    }

    public RetrySameUrlException(String message) {
      super(message);
    }

  }

  public static class RetryDeadLinkException extends RetryableException {
    public RetryDeadLinkException() {
      super();
    }

    public RetryDeadLinkException(String message) {
      super(message);
    }

    protected void setAttributes() {
      super.setAttributes();
      attributeBits.clear(ATTRIBUTE_FAIL);
    }
  }


  /** An error that is likely permanent and not likely to succeed if
   * retried.
   */
  public static class UnretryableException
      extends CacheException {
    public UnretryableException() {
      super();
    }

    public UnretryableException(String message) {
      super(message);
    }
    protected void setAttributes() {
      attributeBits.clear(ATTRIBUTE_RETRY);
      attributeBits.set(ATTRIBUTE_FAIL);
    }
  }

  public static class PermissionException extends UnretryableException {
    public PermissionException() {
      super();
    }

    public PermissionException(String message) {
      super(message);
    }

    protected void setAttributes() {
      super.setAttributes();
      attributeBits.set(ATTRIBUTE_FATAL);
    }
  }

  /** An error connecting to the host serving the URL.  (<i>Eg</i>, host
   * not found, down, connection refused) */
  public static class HostException
      extends UnretryableException {
    public HostException() {
      super();
      suppressStackTrace = false;
    }

    public HostException(String message) {
      super(message);
      suppressStackTrace = false;
    }

    /** Create this if details of causal exception are more relevant. */
    public HostException(Exception e) {
      super(e.toString());
      nestedException = e;
    }
  }

  /** An error from trying to connect to a malformed URL*/
  public static class MalformedURLException
      extends UnretryableException {
    public MalformedURLException() {
      super();
      suppressStackTrace = false;
    }

    public MalformedURLException(String message) {
      super(message);
      suppressStackTrace = false;
    }

    /** Create this if details of causal exception are more relevant. */
    public MalformedURLException(Exception e) {
      super(e.toString());
      nestedException = e;
    }

    protected void setAttributes() {
      attributeBits.clear(ATTRIBUTE_FAIL);
    }
  }

  /** An error storing the cached content or properties in the repository */
  public static class RepositoryException
      extends UnretryableException {
    public RepositoryException() {
      super();
      suppressStackTrace = false;
    }

    public RepositoryException(String message) {
      super(message);
      suppressStackTrace = false;
    }

    /** Create this if details of causal exception are more relevant. */
    public RepositoryException(Exception e) {
      super(e.toString());
      nestedException = e;
    }
  }

  /** An error that should be retried with a different URL (in the
   * Location: response header), <i>ie</i>, a redirect */
  public static class NoRetryNewUrlException
      extends UnretryableException {
    public NoRetryNewUrlException() {
      super();
    }

    public NoRetryNewUrlException(String message) {
      super(message);
    }

    protected void setAttributes() {
      attributeBits.clear(ATTRIBUTE_RETRY);
      attributeBits.clear(ATTRIBUTE_FAIL);
    }

  }

  /** Permanent redirect */
  public static class NoRetryPermUrlException
      extends NoRetryNewUrlException {
    public NoRetryPermUrlException() {
      super();
    }

    public NoRetryPermUrlException(String message) {
      super(message);
    }
  }

  /** Temporary redirect */
  public static class NoRetryTempUrlException
      extends NoRetryNewUrlException {
    public NoRetryTempUrlException() {
      super();
    }

    public NoRetryTempUrlException(String message) {
      super(message);
    }
  }

  /** Unretryable errors that are expectd to happen in normal operation and
   * don't necessarily indicate anything is wrong. */
  public static class ExpectedNoRetryException
      extends UnretryableException {
    public ExpectedNoRetryException() {
      super();
    }

    public ExpectedNoRetryException(String message) {
      super(message);
    }
  }

  public static class NoRetryDeadLinkException
      extends ExpectedNoRetryException {
    public NoRetryDeadLinkException() {
      super();
    }

    public NoRetryDeadLinkException(String message) {
      super(message);
    }

    protected void setAttributes() {
      attributeBits.clear(ATTRIBUTE_RETRY);
      attributeBits.clear(ATTRIBUTE_FAIL);
    }

  }
  /** Unretryable errors that are not expected to happen in normal
   * operation and might warrant a message or alert. */
  public static class UnexpectedNoRetryFailException
      extends UnretryableException {
    public UnexpectedNoRetryFailException() {
      super();
    }

    public UnexpectedNoRetryFailException(String message) {
      super(message);
    }
  }

  public static class UnexpectedNoRetryNoFailException
      extends UnretryableException {
    public UnexpectedNoRetryNoFailException() {
      super();
    }

    public UnexpectedNoRetryNoFailException(String message) {
      super(message);
    }
    
    protected void setAtrributes() {
      attributeBits.clear(ATTRIBUTE_FAIL);
    }
  }
  
  public static class NoRetryHostException
      extends UnretryableException {
    public NoRetryHostException() {
      super();
    }

    public NoRetryHostException(String message) {
      super(message);
    }
  }

  public static class NoRetryRepositoryException
      extends UnretryableException {
    public NoRetryRepositoryException() {
      super();
    }

    public NoRetryRepositoryException(String message) {
      super(message);
    }
  }

  public static class UnimplementedCodeException
      extends ExpectedNoRetryException {
    public UnimplementedCodeException() {
      super();
    }

    public UnimplementedCodeException(String message) {
      super(message);
    }

    protected void setAttributes() {
      attributeBits.clear(ATTRIBUTE_RETRY);
      attributeBits.clear(ATTRIBUTE_FAIL);
    }
  }
}
