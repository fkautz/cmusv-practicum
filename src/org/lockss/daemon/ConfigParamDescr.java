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

import java.net.*;

import org.lockss.util.*;

/**
 * Descriptor for a configuration parameter, and instances of descriptors
 * for common parameters.  These have a sort order equal to the sort order
 * of their displayName.
 */
public class ConfigParamDescr implements Comparable {
  /** Value is any string */
  public static final int TYPE_STRING = 1;
  /** Value is an integer */
  public static final int TYPE_INT = 2;
  /** Value is a URL */
  public static final int TYPE_URL = 3;
  /** Value is a 4 digit year */
  public static final int TYPE_YEAR = 4;
  /** Value is a true or false */
  public static final int TYPE_BOOLEAN = 5;
  /** Value is a positive integer */
  public static final int TYPE_POS_INT = 6;

  public static final String[] TYPE_STRINGS = {
      "String", "Integer", "URL", "Year", "Boolean", "Positive Integer" };

  public static final ConfigParamDescr VOLUME_NUMBER = new ConfigParamDescr();
  static {
    VOLUME_NUMBER.setKey("volume");
    VOLUME_NUMBER.setDisplayName("Volume No.");
    VOLUME_NUMBER.setType(TYPE_POS_INT);
    VOLUME_NUMBER.setSize(8);
  }

  public static final ConfigParamDescr ISSUE_RANGE = new ConfigParamDescr();
  static {
    ISSUE_RANGE.setKey("issue_range");
    ISSUE_RANGE.setDisplayName("Issue Range");
    ISSUE_RANGE.setType(TYPE_STRING);
    ISSUE_RANGE.setSize(20);
  }

  public static final ConfigParamDescr YEAR = new ConfigParamDescr();
  static {
    YEAR.setKey("year");
    YEAR.setDisplayName("Year");
    YEAR.setType(TYPE_YEAR);
    YEAR.setSize(4);
    YEAR.setDescription("Four digit year (e.g., 2004)");
  }

  public static final ConfigParamDescr BASE_URL = new ConfigParamDescr();
  static {
    BASE_URL.setKey("base_url");
    BASE_URL.setDisplayName("Base URL");
    BASE_URL.setType(TYPE_URL);
    BASE_URL.setSize(40);
    BASE_URL.setDescription("Usually of the form http://<journal-name>.com/");
  }

  public static final ConfigParamDescr JOURNAL_DIR = new ConfigParamDescr();
  static {
    JOURNAL_DIR.setKey("journal_dir");
    JOURNAL_DIR.setDisplayName("Journal Directory");
    JOURNAL_DIR.setType(TYPE_STRING);
    JOURNAL_DIR.setSize(40);
    JOURNAL_DIR.setDescription("Directory name for journal content (i.e. 'american_imago').");
  }

  public static final ConfigParamDescr JOURNAL_ABBR = new ConfigParamDescr();
  static {
    JOURNAL_ABBR.setKey("journal_abbr");
    JOURNAL_ABBR.setDisplayName("Journal Abbreviation");
    JOURNAL_ABBR.setType(TYPE_STRING);
    JOURNAL_ABBR.setSize(10);
    JOURNAL_ABBR.setDescription("Abbreviation for journal (often used as part of file names).");
  }

  public static final ConfigParamDescr JOURNAL_ID = new ConfigParamDescr();
  static {
    JOURNAL_ID.setKey("journal_id");
    JOURNAL_ID.setDisplayName("Journal Identifier");
    JOURNAL_ID.setType(TYPE_STRING);
    JOURNAL_ID.setSize(40);
    JOURNAL_ID.setDescription("Identifier for journal (often used as part of file names)");
  }

  public static final ConfigParamDescr PUBLISHER_NAME = new ConfigParamDescr();
  static {
    PUBLISHER_NAME.setKey("publisher_name");
    PUBLISHER_NAME.setDisplayName("Publisher Name");
    PUBLISHER_NAME.setType(TYPE_STRING);
    PUBLISHER_NAME.setSize(40);
    PUBLISHER_NAME.setDescription("Publisher Name for Archival Unit");

  }

  public static final ConfigParamDescr[] DEFAULT_DESCR_ARRAY = {
      BASE_URL, VOLUME_NUMBER, YEAR, JOURNAL_ID, PUBLISHER_NAME, ISSUE_RANGE
  };


  private String key;			// param (prop) key
  private String displayName;		// human readable name
  private String description;		// explanatory test
  private int type = TYPE_STRING;
  private int size = -1;		// size of input field

  // A parameter is definitional if its value is integral to the identity
  // of the AU.  (I.e., if changing it results in a different AU.)
  private boolean definitional = true;

  public ConfigParamDescr() {
  }

  public ConfigParamDescr(String key) {
    setKey(key);
  }

  /**
   * Return the parameter key
   * @return the key String
   */
  public String getKey() {
    return key;
  }

  /**
   * Set the parameter key
   * @param key the new key
   */
  public void setKey(String key) {
    this.key = key;
  }

  /**
   * Return the display name, or the key if no display name set
   * @return the display name String
   */
  public String getDisplayName() {
    return displayName != null ? displayName : getKey();
  }

  /**
   * Set the parameter display name
   * @param displayName the new display name
   */
  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  /**
   * Return the parameter description
   * @return the description String
   */
  public String getDescription() {
    return description;
  }

  /**
   * Set the parameter description
   * @param description the new description
   */
  public void setDescription(String description) {
    this.description = description;
  }

  /**
   * Return the specified value type
   * @return the type int
   */
  public int getType() {
    return type;
  }

  /**
   * Set the expected value type.  If {@link #setSize(int)} has not been
   * called, and the type is one for which there is a reasonable default
   * size, this will also set the size to the reasonable default.
   * @param type the new type
   */
  public void setType(int type) {
    this.type = type;
    // if no size has been set, set a reasonable default for some types
    if (size == -1) {
      switch (type) {
      case TYPE_YEAR: size = 4; break;
      case TYPE_BOOLEAN: size = 4; break;
      case TYPE_INT:
      case TYPE_POS_INT: size = 10; break;
      default:
      }
    }
  }

  /**
   * Return the suggested input field size, or 0 if no suggestion
   * @return the size int
   */
  public int getSize() {
    return (size != -1) ? size : 0;
  }

  /**
   * Set the suggested input field size
   * @param size the new size
   */
  public void setSize(int size) {
    this.size = size;
  }

  public void setDefinitional(boolean isDefinitional) {
    definitional = isDefinitional;
  }

  /** A parameter is definitional if its value is integral to the identity
   * of the AU.  (I.e., if changing it results in a different AU.)
   * @return true if the parameter is definitional
   */
  public boolean isDefinitional() {
    return definitional;
  }

  public boolean isValidValueOfType(String val) {
    try {
      return getValueOfType(val) != null;
    }
    catch (InvalidFormatException ex) {
      return false;
    }
  }

  public Object getValueOfType(String val) throws InvalidFormatException {
    Object ret_val = null;
    switch (type) {
      case TYPE_INT:
        try {
          ret_val = new Integer(val);
        } catch (NumberFormatException nfe) {
          throw new InvalidFormatException("Invalid Int: " + val);
        }
        break;
      case TYPE_POS_INT:
          try {
            ret_val = new Integer(val);
            if(((Integer)ret_val).intValue() < 0) {
              throw new InvalidFormatException("Invalid Positive Int: " + val);
            }
          } catch (NumberFormatException nfe) {
            throw new InvalidFormatException("Invalid Positive Int: " + val);
          }
          break;

      case TYPE_STRING:
        if (!StringUtil.isNullString(val)) {
          ret_val = val;
        }
        else {
          throw new InvalidFormatException("Invalid String: " + val);
        }
        break;
      case TYPE_URL:
        try {
          ret_val = new URL(val);
        }
        catch (MalformedURLException ex) {
          throw new InvalidFormatException("Invalid URL: " + val, ex);
        }
        break;
      case TYPE_YEAR:
        if (val.length() == 4) {
          try {
            int i_val = Integer.parseInt(val);
            if (i_val > 0) {
              ret_val = new Integer(val);
            }
          }
          catch (NumberFormatException fe) {
          }
        }
        if(ret_val == null) {
          throw new InvalidFormatException("Invalid Year: " + val);
        }
        break;
      case TYPE_BOOLEAN:
        if(val.equalsIgnoreCase("true") ||
           val.equalsIgnoreCase("yes") ||
           val.equalsIgnoreCase("on") ||
           val.equalsIgnoreCase("1")) {
          ret_val = Boolean.TRUE;
        }
        else if(val.equalsIgnoreCase("false") ||
           val.equalsIgnoreCase("no") ||
           val.equalsIgnoreCase("off") ||
           val.equalsIgnoreCase("0")) {
          ret_val = Boolean.FALSE;
        }
        else
          throw new InvalidFormatException("Invalid Boolean: " + val);
        break;
      default:
        throw new InvalidFormatException("Unknown type: " + type);
    }
    return ret_val;
  }

  public int compareTo(Object o) {
    ConfigParamDescr od = (ConfigParamDescr)o;
    return getDisplayName().compareTo(od.getDisplayName());
  }

  /** Returns a short string suitable for error messages.  Includes the key
   * and the display name if present */
  public String shortString() {
    StringBuffer sb = new StringBuffer(40);
    sb.append(getDisplayName());
    if (!key.equals(displayName)) {
      sb.append("(");
      sb.append(key);
      sb.append(")");
    }
    return sb.toString();
  }

  public String toString() {
    StringBuffer sb = new StringBuffer(40);
    sb.append("[CPD: key: ");
    sb.append(getKey());
    sb.append("]");
    return sb.toString();
  }

  public boolean equals(Object o) {
    if (! (o instanceof ConfigParamDescr)) {
      return false;
    }
    ConfigParamDescr opd = (ConfigParamDescr)o;
    return type == opd.getType() && getSize() == opd.getSize() &&
      key.equals(opd.getKey());
  }

  public int hashCode() {
    int hash = 0x46600555;
    hash += key.hashCode();
    return hash;
  }

  public static class InvalidFormatException extends Exception {
    private Throwable nestedException;

    public InvalidFormatException(String msg) {
      super(msg);
    }

    public InvalidFormatException(String msg, Throwable e) {
      super(msg + (e.getMessage() == null ? "" : (": " + e.getMessage())));
      this.nestedException = e;
    }

    public Throwable getNestedException() {
      return nestedException;
    }
  }

}
