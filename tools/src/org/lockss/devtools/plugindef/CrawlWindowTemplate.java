/*
 * $Id$
 */

/*

Copyright (c) 2000-2004 Board of Trustees of Leland Stanford Jr. University,
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
package org.lockss.devtools.plugindef;

import org.lockss.util.*;
import org.lockss.daemon.*;
import java.util.TimeZone;

public class CrawlWindowTemplate extends PrintfTemplate {
  int m_windowKind = 0;


  static final String[] WINDOW_KIND_STRINGS         = {
      "Unknown", "Include", "Exclude", "Include No Match", "Exclude No Match",
      "Include Match, Else Exclude", "Exclude Match, Else Include"
  };

  static final String[] WINDOW_ACTION_STRINGS       = {
      "Crawl","Don't Crawl"
  };

  static final String[] WINDOW_HOUR_STRINGS         = {
      "12","1","2","3","4","5","6","7",
      "8","9","10","11",
  };
  
  static final String[] WINDOW_MINUTE_STRINGS       = {
      ":00",":01",":02",":03",":04",":05",":06",":07",":08",":09",
      ":10",":11",":12",":13",":14",":15",":16",":17",":18",":19",
      ":20",":21",":22",":23",":24",":25",":26",":27",":28",":29",
      ":30",":31",":32",":33",":34",":35",":36",":37",":38",":39",
      ":40",":41",":42",":43",":44",":45",":46",":47",":48",":49",
      ":50",":51",":52",":53",":54",":55",":56",":57",":58",":59"
  };

  static final String[] WINDOW_AMPM_STRINGS         = {
      "AM","PM"
  };

  static final String[] WINDOW_TIMEZONE_STRINGS     = TimeZone.getAvailableIDs();

  public CrawlWindowTemplate() {
    m_windowKind = 1;
  }

  public CrawlWindowTemplate(String templateString) {
    super(templateString);
    String val_str = templateString.substring(0, templateString.indexOf(","));
    m_windowKind = Integer.valueOf(val_str).intValue();
  }

    //RI
    public CrawlWindowTemplate(CrawlWindows window){

    }

  int getWindowKind() {
    return m_windowKind;
  }

  void setWindowKind(int kind) {
    m_windowKind = kind;
  }

  void setWindowKind(String kindString) {
    m_windowKind = 0;
    for (int i=0; i< WINDOW_KIND_STRINGS.length; i++) {
      if(WINDOW_KIND_STRINGS[i].equals(kindString)) {
        m_windowKind = i;
        break;
      }
    }

  }

  public String getCrawlWindowString() {
    StringBuffer sb = new StringBuffer();
    sb.append(m_windowKind);
    String template = getTemplateString();
    if(!StringUtil.isNullString(template)) {
      sb.append(",");
      sb.append(template);
      return sb.toString();
    }
    return null;
  }

  String getKindString() {
    if(m_windowKind < 1 || m_windowKind > WINDOW_KIND_STRINGS.length) {
      return WINDOW_KIND_STRINGS[0];
    }
    return WINDOW_KIND_STRINGS[m_windowKind];
  }

}
