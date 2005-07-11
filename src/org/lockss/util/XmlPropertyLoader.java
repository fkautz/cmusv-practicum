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

import java.io.*;
import java.util.*;

import javax.xml.parsers.*;

import org.lockss.config.*;
import org.mortbay.tools.*;
import org.xml.sax.*;
import org.xml.sax.helpers.*;

public class XmlPropertyLoader {

  public static final char PROPERTY_SEPARATOR = '.';

  private static final String TAG_LOCKSSCONFIG = "lockss-config";
  private static final String TAG_PROPERTY     = "property";
  private static final String TAG_VALUE        = "value";
  private static final String TAG_LIST         = "list";
  private static final String TAG_IF           = "if";
  private static final String TAG_THEN         = "then";
  private static final String TAG_ELSE         = "else";
  private static final String TAG_AND          = "and";
  private static final String TAG_OR           = "or";
  private static final String TAG_NOT          = "not";
  private static final String TAG_TEST         = "test";

  private static XmlPropertyLoader m_instance = null;

  private static Logger log = Logger.getLogger("XmlPropertyLoader");

  public static void load(PropertyTree props, InputStream istr)
      throws ParserConfigurationException, SAXException, IOException {
    if (m_instance == null) {
      m_instance = new XmlPropertyLoader();
    }

    m_instance.loadProperties(props, istr);
  }

  /**
   * Load a set of XML properties from the input stream.
   */
  void loadProperties(PropertyTree props, InputStream istr)
      throws ParserConfigurationException, SAXException, IOException {
    
    SAXParserFactory factory = SAXParserFactory.newInstance();
    
    factory.setValidating(true);
    factory.setNamespaceAware(false);
    
    SAXParser parser = factory.newSAXParser();
    
    parser.parse(istr, new LockssConfigHandler(props));
  }

  public Version getDaemonVersion() {
    return ConfigManager.getDaemonVersion();
  }

  public PlatformVersion getPlatformVersion() {
    return ConfigManager.getPlatformVersion();
  }

  public String getPlatformHostname() {
    return ConfigManager.getPlatformHostname();
  }

  public String getPlatformGroup() {
    return ConfigManager.getPlatformGroup();
  }

  /**
   * SAX parser handler.
   */
  class LockssConfigHandler extends DefaultHandler {

    // Simple stack that helps us know what our current level in
    // the property tree is.
    private Stack m_propStack = new Stack();

    // Stack of conditionals being evaluated.  Empty if not inside
    // a conditional statement.
    private Stack m_condStack = new Stack();

    // The current state of the test.
    private boolean m_testEval = false;

    // When building a list of configuration values for a single key.
    private List m_propList = null;

    // True iff the parser is currently inside a "value" element.
    private boolean m_inValue = false;
    // True iff the parser is currently inside a "list" element.
    private boolean m_inList  = false;
    // True iff the parser is currently inside an "if" element.
    private boolean m_inIf = false;
    // True iff the parser is currently inside an "else" element.
    private boolean m_inElse = false;
    // True iff the parser is currently inside a "then" element.
    private boolean m_inThen = false;

    // False iff the conditions in the propgroup attribute conditionals
    // are not satisfied.
    private boolean m_evalIf = false;

    // The property tree we're adding to.
    private PropertyTree m_props;

    // A stringbuffer to hold current character data until it's all
    // been read.
    private StringBuffer m_charBuffer;

    // Save the current running daemon and platform version
    private PlatformVersion m_sysPlatformVer;
    private Version m_sysDaemonVer;
    private String m_sysPlatformName;
    private String m_sysGroup;
    private String m_sysHostname;

    /**
     * Default constructor.
     */
    public LockssConfigHandler(PropertyTree props) {
      super();
      // Conditionals
      m_sysPlatformVer = getPlatformVersion();
      m_sysDaemonVer = getDaemonVersion();
      if (m_sysPlatformVer != null) {
	m_sysPlatformName = m_sysPlatformVer.getName();
      }
      m_sysGroup = getPlatformGroup();
      m_sysHostname = getPlatformHostname();

      m_props = props;
      log.debug2("Conditionals: {platformVer=" + m_sysPlatformVer + "}, " +
		 "{daemonVer=" + m_sysDaemonVer + "}, " +
		 "{group=" + m_sysGroup + "}, " +
		 "{hostname=" + m_sysHostname + "}, " +
		 "{platformName=" + m_sysPlatformName + "}");
    }

    /**
     * <p>Evaluate this property iff:</p>
     * <ol>
     *   <li>We're not in a propgroup conditional, or</li>
     *   <li>We're in a propgroup that we eval to true AND we're
     *      in a &lt;then&gt;</li>
     *   <li>We're in a propgroup that we eval to false AND we're
     *      in an &lt;else&gt;</li>
     *   <li>We're in a propgroup that we eval to true AND we're
     *      in neither  a &lt;then&gt; or an &lt;else&gt;</li>
     * </ol>
     */
    private boolean doEval() {
      return (!m_inIf ||
	      ((m_evalIf && m_inThen) ||
	       (!m_evalIf && m_inElse)) ||
	      (m_evalIf && !m_inThen && !m_inElse));
    }

    /**
     * Handle the starting tags of elements.  Based on the name of the
     * tag, call the appropriate handler method.
     */
    public void startElement(String namespaceURI, String localName,
			     String qName, Attributes attrs)
	throws SAXException {

      if (TAG_IF.equals(qName)) {
	startIfTag(attrs);
      } else if (TAG_ELSE.equals(qName)) {
	startElseTag();
      } else if (TAG_LIST.equals(qName)) {
	startListTag();
      } else if (TAG_PROPERTY.equals(qName)) {
	startPropertyTag(attrs);
      } else if (TAG_THEN.equals(qName)) {
	startThenTag();
      } else if (TAG_VALUE.equals(qName)) {
	startValueTag();
      } else if (TAG_AND.equals(qName)) {
	startAndTag();
      } else if (TAG_OR.equals(qName)) {
	startOrTag();
      } else if (TAG_NOT.equals(qName)) {
	startNotTag();
      } else if (TAG_TEST.equals(qName)) {
	startTestTag(attrs);
      } else if (TAG_LOCKSSCONFIG.equals(qName)) {
	; // do nothing
      } else {
	throw new IllegalArgumentException("Unexpected tag: " + qName);
      }
    }

    /**
     * Handle the ending tags of elements.
     */
    public void endElement(String namespaceURI, String localName,
			   String qName)
	throws SAXException {

      if (TAG_IF.equals(qName)) {
	endIfTag();
      } else if (TAG_ELSE.equals(qName)) {
	endElseTag();
      } else if (TAG_LIST.equals(qName)) {
	endListTag();
      } else if (TAG_PROPERTY.equals(qName)) {
	endPropertyTag();
      } else if (TAG_THEN.equals(qName)) {
	endThenTag();
      } else if (TAG_VALUE.equals(qName)) {
	endValueTag();
      } else if (TAG_AND.equals(qName)) {
	endCondTag();
      } else if (TAG_OR.equals(qName)) {
	endCondTag();
      } else if (TAG_NOT.equals(qName)) {
	endCondTag();
      } else if (TAG_TEST.equals(qName)) {
	endTestTag();
      } else if (TAG_LOCKSSCONFIG.equals(qName)) {
	; // do nothing
      }
      // Don't need to throw here.  Unsupported tags will have already
      // thrown in startElement().
    }

    /**
     * Handle character data encountered in a tag.  The character data
     * should never be anything other than a property value.
     */
    public void characters(char[] ch, int start, int length)
	throws SAXException {
      // m_charBuffer shouldn't be null at this point, but
      // just in case...
      if (doEval() && m_charBuffer != null) {
	m_charBuffer.append(ch, start, length);
      }
    }

    /**
     * Handle encountering the start of an "else" tag.
     */
    private void startElseTag() {
      m_inElse = true;
    }

    /**
     * Handle encountering the start of a "list" tag.
     */
    private void startListTag() {
      if (doEval()) {
	m_inList = true;
	m_propList = new ArrayList();
      }
    }

    /**
     * Handle encountering a starting "property" tag.  Get the
     * property's name and value (if any).  Name is required, value is
     * not.
     */
    private void startPropertyTag(Attributes attrs) {
      if (doEval()) {
	boolean hasValueAttr = false;
	String name = attrs.getValue("name");
	String value = attrs.getValue("value");

	if (value != null) hasValueAttr = true;

	m_propStack.push(name);

	// If we have both a name and a value we can add it to the
	// property tree right away.
	if (hasValueAttr) {
	  setProperty(value);
	}
      }
    }

    /**
     * Handle encountering the start of an "if" tag by parsing
     * the conditional attributes and acting on them accordingly.
     */
    private void startIfTag(Attributes attrs) {
      m_inIf = true;
      if (attrs.getLength() > 0) {
	m_evalIf = evaluateAttributes(attrs);
      }
    }

    /**
     * Handle encountering a starting "then" tag.
     */
    private void startThenTag() {
      m_inThen = true;
    }

    /**
     * Handle encountering a starting "value" tag.
     */
    private void startValueTag() {
      if (doEval()) {
	// Inside a "value" element.
	m_inValue = true;

	// Prepare a buffer to hold character data, which may be
	// chunked across several characters() calls
	m_charBuffer = new StringBuffer();
      }
    }

    /**
     * Handle encountering a starting "and" tag.
     */
    private void startAndTag() {
      if (m_condStack.isEmpty()) {
	m_evalIf = true; // 'and' expressions start out true
      }

      m_condStack.push(TAG_AND);
    }

    /**
     * Handle encountering a starting "or" tag.
     */
    private void startOrTag() {
      if (m_condStack.isEmpty()) {
	m_evalIf = false; // 'or' expressions start out false
      }

      m_condStack.push(TAG_OR);
    }

    /**
     * Handle encountering a starting "not" tag.
     */
    private void startNotTag() {
      if (m_condStack.isEmpty()) {
	m_evalIf = true; // 'not' expressions start out true
      }

      m_condStack.push(TAG_NOT);
    }


    /**
     * Set the state of the test evaluation boolean.
     */
    private void startTestTag(Attributes attrs) {
      if (attrs.getLength() > 0) {
	m_testEval = evaluateAttributes(attrs);
      }
    }

    /**
     * Handle encoutering the end of an "else" tag.
     */
    private void endElseTag() {
      m_inElse = false;
    }

    /**
     * Handle encountering the end of a "list" tag.
     */
    private void endListTag() {
      if (doEval()) {
	setListProperty(m_propList);

	// Clean-up.
	m_propList = null;
	m_inList = false;
      }
    }

    /**
     * Handle encountering the end of a "property" tag.
     */
    private void endPropertyTag() {
      if (doEval()) {
	m_propStack.pop();
      }
    }

    /**
     * Handle encountering the end of a "propgroup" tag.
     */
    private void endIfTag() {
      m_inIf = false;
      m_evalIf = false; // Reset the evaluation boolean.
    }


    /**
     * Handle encountering the end of a "then" tag.
     */
    private void endThenTag() {
      m_inThen = false;
    }

    /**
     * Handle encountering the end of a "value" tag.
     */
    private void endValueTag() {
      // The only character data in the property file should be
      // inside "value" tags!  It doesn't belong anywhere else.
      if (doEval() && m_inValue && m_charBuffer != null) {
	if (m_inList) {
	  // If we're inside a list, we need to add this value to the
	  // current temporary property list.
	  if (log.isDebug3()) {
	    log.debug3("Adding '" + m_charBuffer.toString().trim() + "' to " +
		       getPropname() + " prop list");
	  }
	  m_propList.add(m_charBuffer.toString().trim());
	} else {
	  // Otherwise, just add the property key and value to the prop
	  // tree.
	  setProperty(m_charBuffer.toString().trim());
	}
      }

      m_inValue = false;
      // reset the char buffer
      m_charBuffer = null;
    }

    /**
     * Handle encountering the end of a boolean conditional.
     */
    private void endCondTag() {
      m_condStack.pop();

      // Handle nesting by conding with previous boolean level.
      if (!m_condStack.isEmpty()) {
	evalCurrentCondStackLevel();
      }
    }

    /**
     * Handle encountering the end of a "test" tag.
     */
    private void endTestTag() {
      if (m_condStack.isEmpty()) {
	// If we're not in a conditional at all, this should be a single
	// <test>, i.e. <if><test foo="bar"/><then>...</then></if>. Just
	// apply the current test results
	m_evalIf = m_testEval;
      } else {
	evalCurrentCondStackLevel();
      }
    }

    // Utility method used by endCondTag and endTestTag
    private void evalCurrentCondStackLevel() {
      String cond = (String)m_condStack.peek();
      if (cond == TAG_AND) {
	m_evalIf &= m_testEval;
      } else if (cond == TAG_OR) {
	m_evalIf |= m_testEval;
      } else if (cond == TAG_NOT) {
	m_evalIf &= !m_testEval;
      }      
    }

    /**
     * Return the current property name.
     */
    private String getPropname() {
      return StringUtil.separatedString(m_propStack, ".");
    }

    /**
     * Log a warning if overwriting an existing property.
     */
    private void setProperty(String value) {
      m_props.put(getPropname(), value);
    }

    /**
     * Set a list of property values.
     */
    private void setListProperty(List list) {
      setProperty(StringUtil.separatedString(list, ";"));
    }

    /**
     * Evaluate the attributes of this test (whether an <if...> or a
     * <test...> tag) and return the boolean value.
     */
    public boolean evaluateAttributes(Attributes attrs) {
      // Evaluate the attributes of the tag and set the
      // value "returnVal" appropriately.

      // Get the XML element attributes
      String group = null;
      String hostname = null;
      String platformName = null;
      Version daemonMin = null;
      Version daemonMax = null;
      Version platformMin = null;
      Version platformMax = null;

      group = attrs.getValue("group");
      hostname = attrs.getValue("hostname");
      platformName = attrs.getValue("platformName");

      if (attrs.getValue("daemonVersionMin") != null) {
	daemonMin = new DaemonVersion(attrs.getValue("daemonVersionMin"));
      }

      if (attrs.getValue("daemonVersionMax") != null) {
	daemonMax = new DaemonVersion(attrs.getValue("daemonVersionMax"));
      }

      if (attrs.getValue("daemonVersion") != null) {
	if (daemonMin != null || daemonMax != null) {
	  throw new IllegalArgumentException("Cannot mix daemonMin, daemonMax " +
					     "and daemonVersion!");
	}
	daemonMin = daemonMax =
	  new DaemonVersion(attrs.getValue("daemonVersion"));
      }

      if (attrs.getValue("platformVersionMin") != null) {
	platformMin = new PlatformVersion(attrs.getValue("platformVersionMin"));
      }

      if (attrs.getValue("platformVersionMax") != null) {
	platformMax = new PlatformVersion(attrs.getValue("platformVersionMax"));
      }

      if (attrs.getValue("platformVersion") != null) {
	if (platformMin != null || platformMax != null) {
	  throw new IllegalArgumentException("Cannot mix platformMin, " +
					     "platformMax and platformVersion!");
	}
	platformMin = platformMax =
	  new PlatformVersion(attrs.getValue("platformVersion"));
      }

      // Short-circuit.  If all values are null, there are no
      // conditionals, just return false.

      if (group == null && hostname == null && platformName == null &&
	  daemonMin == null && daemonMax == null &&
	  platformMin == null && platformMax == null) {
	return false;
      }

      boolean returnVal = true;

      /*
       * Group membership checking.
       */
      if (group != null) {
	returnVal &= StringUtil.equalStringsIgnoreCase(m_sysGroup, group);
      }

      /*
       * Hostname checking.
       */
      if (hostname != null) {
	returnVal &= StringUtil.equalStringsIgnoreCase(m_sysHostname, hostname);
      }

      /*
       * Daemon version checking.
       */
      if (daemonMin != null || daemonMax != null) {
	returnVal &= compareVersion(m_sysDaemonVer, daemonMin, daemonMax);
      }
      
      /*
       * Platform version checking.
       */
      if (platformMin != null || platformMax != null) {
	returnVal &= compareVersion(m_sysPlatformVer, platformMin, platformMax);
      }

      /*
       * Platform name checking.
       */
      if (platformName != null) {
	returnVal &= StringUtil.equalStringsIgnoreCase(m_sysPlatformName, platformName);
      }
      
      return returnVal;
    }

    boolean compareVersion(Version sysVersion, Version versionMin, Version versionMax) {
      boolean returnVal = true;

      if (sysVersion == null) {
	return false;
      }

      if (versionMin != null && versionMax != null) {
	// Have both min and max...
	returnVal &= ((sysVersion.toLong() >= versionMin.toLong()) &&
		      (sysVersion.toLong() <= versionMax.toLong()));
      } else if (versionMin != null) {
	// Have min...
	returnVal &= (sysVersion.toLong() >= versionMin.toLong());
      } else if (versionMax != null) {
	// Have max...
	returnVal &= (sysVersion.toLong() <= versionMax.toLong());
      }

      return returnVal;
    }
  }
}
