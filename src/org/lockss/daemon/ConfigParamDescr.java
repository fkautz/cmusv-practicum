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

import java.io.*;
import java.util.*;
import org.lockss.util.*;

/**
 * Descriptor for a configuration parameter, and instances of descriptors
 * for common parameters.
 */
public class ConfigParamDescr implements Comparable {
  /** Value is any string */
  public static final int TYPE_STRING = 1;
  /** Value is an integer */
  public static final int TYPE_INT = 2;
  /** Value is a URL */
  public static final int TYPE_URL = 3;

  public static final ConfigParamDescr VOLUME_NUMBER = new ConfigParamDescr();
  static {
    VOLUME_NUMBER.setKey("volume");
    VOLUME_NUMBER.setDisplayName("Volume No.");
    VOLUME_NUMBER.setType(TYPE_INT);
    VOLUME_NUMBER.setSize(8);
  }

  public static final ConfigParamDescr BASE_URL = new ConfigParamDescr();
  static {
    BASE_URL.setKey("base_url");
    BASE_URL.setDisplayName("Base URL");
    BASE_URL.setType(TYPE_URL);
    BASE_URL.setSize(40);
    BASE_URL.setDescription("Usually of the form http://<i>journal</i>.com/");
  }

  private String key;			// param (prop) key
  private String displayName;		// human readable name
  private String description;		// explanatory test
  private int type = TYPE_STRING;
  private int size = 0;			// size of input field

  public ConfigParamDescr() {
  }

  public ConfigParamDescr(String key) {
    setKey(key);
  }

  /** Create a new ConfigParamDescr with values from old ConfigParamDescr */
  public ConfigParamDescr(ConfigParamDescr old) {
    super();
    setKey(old.getKey());
    setDisplayName(old.getDisplayName());
    setDescription(old.getDescription());
    setType(old.getType());
    setSize(old.getSize());
  }

  /** Return the parameter key */
  public String getKey() {
    return key;
  }

  /** Set the parameter key */
  public void setKey(String key) {
    this.key = key;
  }

  /** Return the display name, or the key if no display name set */
  public String getDisplayName() {
    return displayName != null ? displayName : getKey();
  }

  /** Set the parameter key */
  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  /** Return the parameter description */
  public String getDescription() {
    return description;
  }

  /** Set the parameter description */
  public void setDescription(String description) {
    this.description = description;
  }

  /** Return the specified value type */
  public int getType() {
    return type;
  }

  /** Set the expected value type */
  public void setType(int type) {
    this.type = type;
  }

  /** Return the suggested input field size */
  public int getSize() {
    return size;
  }

  /** Set the suggested input field size */
  public void setSize(int size) {
    this.size = size;
  }

  public int compareTo(Object o) {
    ConfigParamDescr od = (ConfigParamDescr)o;
    return getDisplayName().compareTo(od.getDisplayName());
  }

}
