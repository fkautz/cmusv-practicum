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

package org.lockss.servlet;

public class ServletDescr {
  
  /** Marker for servlets whose class can't be found */
  static class UnavailableServletMarker {}

  public Class cls;
  
  public String heading;	// display name
  
  public String name;		// url path component to invoke servlet
  
  public String expl;
  
  public int flags = 0;

  static Class UNAVAILABLE_SERVLET_MARKER = UnavailableServletMarker.class;
  
  // flags
  public static final int ON_CLIENT = 0x01; // runs on client (else on admin)
  public static final int PER_CLIENT = 0x02; // per client (takes client arg)
  public static final int NOT_IN_NAV = 0x04; // no link in nav table
  public static final int LARGE_LOGO = 0x08; // use large LOCKSS logo
  public static final int DEBUG_ONLY = 0x10; // debug user only
  public static final int NAME_IS_URL = 0x20; // debug user only
  public static final int STATUS = ON_CLIENT | PER_CLIENT; // shorthand

  public ServletDescr(Class cls, String heading, String name, int flags) {
    this.cls = cls;
    this.heading = heading;
    this.name = name;
    this.flags = flags;
  }
  
  public ServletDescr(Class cls, String heading, int flags) {
    this(cls, heading,
         cls.getName().substring(cls.getName().lastIndexOf('.') + 1),
         flags);
  }

  public ServletDescr(Class cls, String heading) {
    this(cls, heading, 0);
  }

  public ServletDescr(String className, String heading, String name) {
    this(classForName(className), heading, name, 0);
  }

  public ServletDescr(String className, String heading, int flags) {
    this(classForName(className), heading, flags);
  }

  static Class classForName(String className) {
    try {
      return Class.forName(className);
    } catch (ClassNotFoundException e) {
      return ServletDescr.UNAVAILABLE_SERVLET_MARKER;
    }	
  }

  public String getName() {
    return name;
  }

  String getExplanation() {
    return expl;
  }

  void setExplanation(String s) {
    expl = s;
  }

  boolean isPerClient() {
    return (flags & PER_CLIENT) != 0;
  }
  
  boolean runsOnClient() {
    return (flags & ON_CLIENT) != 0;
  }
  
  boolean isInNavTable() {
    return (flags & NOT_IN_NAV) == 0;
  }
  
  boolean isDebugOnly() {
    return (flags & DEBUG_ONLY) != 0;
  }
  
  boolean isLargeLogo() {
    return (flags & LARGE_LOGO) != 0;
  }
  
  boolean isNameIsUrl() {
    return (flags & NAME_IS_URL) != 0;
  }
  
}