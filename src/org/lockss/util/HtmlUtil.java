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

/** Static html utilities */
public class HtmlUtil {
  /** Encode for open html text */
  public static final int ENCODE_TEXT = 1;
  /** Encode for a textarea form field.  */
  public static final int ENCODE_TEXTAREA = 2;
  /** Encode for attr value in "" */
  public static final int ENCODE_QUOTED_ATTR = 3;
  /** Encode for attr value in '' */
  public static final int ENCODE_QUOTED1_ATTR = 4;
  /** Encode for unquoted attr value */
  public static final int ENCODE_ATTR = 5;
  /** Encode for javascript string */
  public static final int ENCODE_JS_STRING = 6;

  /** Encode a string in a context-dependent manner.
   * @param s the String to encode.
   * @param renderContext the context in which the encoded string will
   * appear, chosen from the ENCODE_ values */
  public static String encode(String s, int renderContext) {
    if (s == null) {
      return null;
    }
    switch (renderContext) {
    case ENCODE_TEXT:
      return htmlEncode(s);
    case ENCODE_TEXTAREA:
      return htmlEncode(s);
    case ENCODE_QUOTED_ATTR:
      return s;
    case ENCODE_QUOTED1_ATTR:
      return s;
    case ENCODE_ATTR:
      return htmlEncode(s);
    case ENCODE_JS_STRING:
      return jsEncode(s);

    default:
      return s;
    }
  }

  public static String htmlEncode(String s) {
    StringBuffer sb = new StringBuffer();
    int len = s.length();
    for (int counter = 0; counter < len; counter++) {
      char c = s.charAt(counter);
      // This will change, we need better support character level
      // entities.
      switch(c) {
	// Character level entities.
      case '<':
	sb.append("&lt;");
	break;
      case '>':
	sb.append("&gt;");
	break;
      case '&':
	sb.append("&amp;");
	break;
      case '"':
	sb.append("&quot;");
	break;
	// Special characters
	/*
	  case '\n':
	  case '\t':
	  case '\r':
	  break;
	*/
      default:
	if (c < ' ' || c > 127) {
	  // If the character is outside of ascii, write the
	  // numeric value.
	  sb.append("&#");
	  sb.append(String.valueOf((int)c));
	  sb.append(";");
	} else {
	  sb.append(c);
	}
	break;
      }
    }
    return sb.toString();
  }

  public static String jsEncode(String s) {
    StringBuffer sb = new StringBuffer();
    int len = s.length();
    for (int counter = 0; counter < len; counter++) {
      char c = s.charAt(counter);
      switch(c) {
	// Special characters
      case '\n':
	sb.append("\\n");
        break;
      case '\r':
	break;
      default:
	sb.append(c);
	break;
      }
    }
    return sb.toString();
  }

}
