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

package org.lockss.util;

import java.util.*;
import java.io.*;
import java.net.URLEncoder;
import org.mortbay.tools.*;


/** Utilities for Properties
 */
public class PropUtil {

  private static final String noProp = new String(); // guaranteed unique obj

  private static boolean isKeySame(String key, Properties p1, Properties p2) {
    Object o1 = p1.getProperty(key);
    Object o2 = p2.getProperty(key, noProp);
    return (o1 == o2) ||
      // o2 == noProp means p2 didn't have key that p1 has.
      // we know o1 != o2, so if o1 == null, o2 != null.
      !((o2 == noProp || o1 == null || !o1.equals(o2)));
  }

  /**
   * Compare two Properties for equality (same set of properties with
   * same (.equals) values.
   * @param p1 first Properties
   * @param p2 second Properties
   * @return true iff Properties are equal
   */
  public static boolean equalProps(Properties p1, Properties p2) {
    if (p1 == null || p2 == null || p1.size() != p2.size()) {
      return false;
    }
    Enumeration en = p1.keys();
    while (en.hasMoreElements()) {
      String k1 = (String)en.nextElement();
      if (! isKeySame(k1, p1, p2)) {
	return false;
      }
    }
    return true;
  }

  /**
   * Compare two Properties, return the set of keys whose values are not
   * equal.  (The set may contain keys that don't exist in one or the other
   * Properties).
   * @param p1 first Properties
   * @param p2 second Properties
   * @return Set of keys whose values differ, or null if
   * there are no differences.
   */
  public static Set differentKeys(Properties p1, Properties p2) {
    if (p1 == null) {
      if (p2 == null) {
	return null;
      } else {
	return p2.keySet();
      }
    } else if (p2 == null) {
      return p1.keySet();
    }
    Set res = new HashSet();
    Set keys1 = p1.keySet();
    for (Iterator iter = keys1.iterator(); iter.hasNext();) {
      String k1 = (String)iter.next();
      if (! isKeySame(k1, p1, p2)) {
	res.add(k1);
      }
    }
    // add all the keys in p2 that don't appear in p1
    Set p2Only = new HashSet(p2.keySet());
    p2Only.removeAll(keys1);
    res.addAll(p2Only);
    return res;
  }

  /**
   * Turns the properties into a canonical string
   * @return a canonical string generated from the props
   * @param props properties
   */ 
  public static String propsToCanonicalEncodedString(Properties props) {
    if (props == null || props.isEmpty()) {
      return "";
    }
    StringBuffer sb = new StringBuffer();
    SortedSet sortedKeys = new TreeSet(props.keySet());

    for (Iterator it=sortedKeys.iterator(); it.hasNext();) {
      String key = (String)it.next();
      sb.append(PropKeyEncoder.encode(key));
      sb.append("~");
      sb.append(PropKeyEncoder.encode(props.getProperty(key)));
      if (it.hasNext()) {
	sb.append("&");
      }
    }
    return sb.toString();
  }

  /**
   * Turns the canonical string into properties
   * @param s a string returned by propsToCanonicalEncodedString()
   * @return Properties from which the canonical string was generated
   */ 
  public static Properties canonicalEncodedStringToProps(String s)
      throws IllegalArgumentException {
    Properties res = new Properties();
    if (StringUtil.isNullString(s)) {
      return res;
    }
    StringTokenizer tk = new StringTokenizer(s, "~&", true);
    while (tk.hasMoreElements()) {
      String key = tk.nextToken();
      String tok = tk.nextToken();
      if (!tok.equals("~")) {
	throw new IllegalArgumentException("Delimiter not \"~\": " + tok);
      }
      String val = tk.nextToken();
      res.setProperty(PropKeyEncoder.decode(key),
		      PropKeyEncoder.decode(val));

      if (tk.hasMoreElements()) {
	tok = tk.nextToken();
	if (!tok.equals("&")) {
	  throw new IllegalArgumentException("Delimiter not \"&\": " + tok);
	}
      }
    }
    return res;
  }

  /**
   * Prints the Properties to the PrintStream
   * @param props properties to print
   * @param out stream to print to
   */
  public static void printPropsTo(Properties props, PrintStream out) {
    SortedSet keys = new TreeSet();
    for (Iterator iter = props.keySet().iterator(); iter.hasNext(); ) {
      keys.add((String)iter.next());
    }
    for (Iterator iter = keys.iterator(); iter.hasNext(); ) {
      String key = (String)iter.next();
      out.println(key + " = " + (String)props.getProperty(key));
    }
  }


}
