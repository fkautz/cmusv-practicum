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
import org.lockss.util.*;

import org.mortbay.tools.*;

/** <code>ConfigurationPropTreeImpl</code> represents the config parameters
 * as a <code>PropertyTree</code>
 */
public class ConfigurationPropTreeImpl extends Configuration {
  private PropertyTree props;

  ConfigurationPropTreeImpl() {
    super();
    props = new PropertyTree();
  }

  private ConfigurationPropTreeImpl(PropertyTree tree) {
    super();
    props = tree;
  }

  PropertyTree getPropertyTree() {
    return props;
  }

  boolean load(InputStream istr) throws IOException {
    props.load(istr);
    return true;
  }

  void reset() {
    props.clear();
  }

  public boolean equals(Object c) {
    if (! (c instanceof ConfigurationPropTreeImpl)) {
      return false;
    }
    ConfigurationPropTreeImpl c0 = (ConfigurationPropTreeImpl)c;
    return PropUtil.equalProps(props, c0.getPropertyTree());
  }

  /** Return the set of keys whose values differ.
   * @param otherConfig the config to compare with.  May be null.
   */
  Set differentKeys(Configuration otherConfig) {
    if (otherConfig == null) {
      return props.keySet();
    }
    ConfigurationPropTreeImpl oc = (ConfigurationPropTreeImpl)otherConfig;
    return PropUtil. differentKeys(getPropertyTree(), oc.getPropertyTree());
  }

  public boolean containsKey(String key) {
    return props.containsKey(key);
  }

  public String get(String key) {
    return (String)props.get(key);
  }

  public Configuration getConfigTree(String key) {
    PropertyTree tree = props.getTree(key);
    return (tree == null) ? null : new ConfigurationPropTreeImpl(tree);
  }

  public Set keySet() {
    return Collections.unmodifiableSet(props.keySet());
  }

  public Iterator keyIterator() {
    return keySet().iterator();
  }

  public Iterator nodeIterator() {
    return new EnumerationIterator(props.getNodes());
  }

  public Iterator nodeIterator(String key) {
    return new EnumerationIterator(props.getNodes(key));
  }

  public String toString() {
    return getPropertyTree().toString();
  }

}
