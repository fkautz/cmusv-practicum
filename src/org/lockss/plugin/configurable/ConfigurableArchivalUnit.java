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
package org.lockss.plugin.configurable;

import java.util.*;

import org.lockss.daemon.*;
import org.lockss.plugin.*;
import org.lockss.plugin.ArchivalUnit.*;
import org.lockss.plugin.base.*;
import org.lockss.util.*;
import gnu.regexp.*;
import java.net.URL;

/**
 * <p>ConfigurableArchivalUnit: An implementatation of Base Archival Unit used
 * with the ConfigurablePlugin to allow a Map of values to be used to configure
 * and define the behaviour of a plugin.</p>
 * @author claire griffin
 * @version 1.0
 */
public class ConfigurableArchivalUnit
    extends BaseArchivalUnit {
  static final protected String CM_AU_START_URL_KEY = "au_start_url";
  static final protected String CM_AU_NAME_KEY = "au_name";
  static final protected String CM_AU_RULES_KEY = "au_crawlrules";
  static final protected String CM_AU_SHORT_YEAR_PREFIX = "au_short_";
  static final protected String CM_AU_HOST_SUFFIX = "_host";
  static final protected String CM_AU_PATH_SUFFIX = "_path";
  static final protected String CM_AU_CRAWL_WINDOW_KEY = "au_crawlwindow";
  static final protected String CM_AU_EXPECTED_PATH = "au_expected_base_path";
  static final protected String CM_AU_CRAWL_DEPTH = "au_crawl_depth";
  static final protected String CM_AU_DEFAULT_NC_CRAWL_KEY =
      "au_def_new_content_crawl";
  static final protected String CM_AU_DEFAULT_PAUSE_TIME = "au_def_pause_time";

  protected ExternalizableMap configurationMap;
  static Logger log = Logger.getLogger("ConfigurableArchivalUnit");
/*
  protected ConfigurableArchivalUnit(Plugin myPlugin) {
    super(myPlugin);
    throw new UnsupportedOperationException(
        "ConfigurableArchvialUnit requires ConfigurablePlugin for construction");
  }
*/

  protected ConfigurableArchivalUnit(ConfigurablePlugin myPlugin,
                                     ExternalizableMap definitionMap) {
    super(myPlugin);
    configurationMap = definitionMap;
  }

  protected String makeStartUrl() {
    String startstr = configurationMap.getString(CM_AU_START_URL_KEY, "");
    String convstr = convertVariableString(startstr);
    log.debug2("setting start url " + convstr);
    return convstr;
  }

  protected void loadAuConfigDescrs(Configuration config) throws
      ConfigurationException {
    List descrList = plugin.getAuConfigDescrs();
    for (Iterator it = descrList.iterator(); it.hasNext(); ) {
      ConfigParamDescr descr = (ConfigParamDescr) it.next();
      String key = descr.getKey();

      try {
        Object val = descr.getValueOfType(config.get(key));
        configurationMap.setMapElement(key, val);
        // we store years in two formats - short and long
        if (descr.getType() == ConfigParamDescr.TYPE_YEAR) {
          int year = ((Integer)val).intValue() % 100;
          configurationMap.putInt(CM_AU_SHORT_YEAR_PREFIX + key, year);
        }
        if (descr.getType() == ConfigParamDescr.TYPE_URL) {
          URL url = configurationMap.getUrl(key, null);
          if(url != null) {
            configurationMap.putString(key+CM_AU_HOST_SUFFIX, url.getHost());
            configurationMap.putString(key+CM_AU_PATH_SUFFIX, url.getPath());
          }
        }

      }
      catch (Exception ex) {
        throw new ConfigurationException("Error configuring: " + key, ex);
      }
    }
    // now load any specialized parameters
    expectedUrlPath = configurationMap.getString(CM_AU_EXPECTED_PATH,"/");
    defaultFetchDelay =
        configurationMap.getLong(CM_AU_DEFAULT_PAUSE_TIME,
                                 DEFAULT_MILLISECONDS_BETWEEN_CRAWL_HTTP_REQUESTS);
    defaultContentCrawlIntv =
        configurationMap.getLong(CM_AU_DEFAULT_NC_CRAWL_KEY,
                                 DEFAULT_NEW_CONTENT_CRAWL_INTERVAL);
  }

  protected String makeName() {
    String namestr = configurationMap.getString(CM_AU_NAME_KEY, "");
    String convstr = convertVariableString(namestr);
    log.debug2("setting name string: " + convstr);
    return convstr;
  }

  protected CrawlRule makeRules() throws gnu.regexp.REException {
    List rules = new LinkedList();
    List templates = (List) configurationMap.getCollection(CM_AU_RULES_KEY,
        Collections.EMPTY_LIST);
    Iterator it = templates.iterator();
    while (it.hasNext()) {
      String rule_template = (String) it.next();
      rules.add(convertRule(rule_template));
    }
    if(rules.size() > 0)
      return new CrawlRules.FirstMatch(rules);
    else {
      log.error("No crawl rules found for plugin: " + makeName());
      return null;
    }
  }

  protected CrawlSpec makeCrawlSpec()
      throws REException {

    CrawlRule rule = makeRules();
    int depth = configurationMap.getInt(CM_AU_CRAWL_DEPTH, 1);
    return new CrawlSpec(startUrlString, rule, depth);
  }

  protected CrawlWindow makeCrawlWindow() {
    CrawlWindow window = null;
    String window_class;
    window_class = configurationMap.getString(CM_AU_CRAWL_WINDOW_KEY,
                                              null);
    if (window_class != null) {
      try {
        ConfigurableCrawlWindow ccw = (ConfigurableCrawlWindow)
            Class.forName(window_class).newInstance();
        window = ccw.makeCrawlWindow();
      }
      catch (Exception ex) {
        throw new ConfigurablePlugin.InvalidDefinitionException(
       auName + " failed to create crawl window from " + window_class, ex);
      }
    }
    return window;
  }

  protected FilterRule constructFilterRule(String mimeType) {
    String filter = configurationMap.getString(mimeType, null);
    if (filter != null) {
      try {
        return (FilterRule) Class.forName(filter).newInstance();
      }
      catch (Exception ex) {
        throw new ConfigurablePlugin.InvalidDefinitionException(
       auName + " failed to create filter rule from " + filter, ex);

      }
    }

    return super.constructFilterRule(mimeType);
  }

// ---------------------------------------------------------------------
//   VARIABLE ARGUMENT REPLACEMENT SUPPORT ROUTINES
// ---------------------------------------------------------------------
  private String[] getStringTokens(String tokenString) {
    StringTokenizer st = new StringTokenizer(tokenString, "\n");
    int num_tokens = st.countTokens();
    String[] strs = new String[num_tokens];
    for (int i = 0; i < num_tokens; i++) {
      strs[i] = st.nextToken();
    }
    return strs;
  }

  String convertVariableString(String variableString) {
    if (StringUtil.isNullString(variableString)) {
      return variableString;
    }
    String[] strs = getStringTokens(variableString);
    String cur_str = strs[0];
    ArrayList args = new ArrayList();
    boolean has_all_args = true;
    for (int i = 1; i < strs.length && has_all_args; i++) {
      String key = strs[i];
      Object val = configurationMap.getMapElement(key);
      if (val != null) {
        args.add(val);
      }
      else {
        log.warning("misssing argument for : " + key);
        has_all_args = false;
      }
    }
    if (has_all_args) {
      PrintfFormat pf = new PrintfFormat(cur_str);
      cur_str = pf.sprintf(args.toArray());
    }
    else {
      log.warning("missing variable arguments");
    }
    return cur_str;
  }

  CrawlRule convertRule(String variableString) throws REException {
    String[] strs = getStringTokens(variableString);
    int value = Integer.valueOf(strs[0]).intValue();
    String rule = strs[1];
    ArrayList args = new ArrayList();
    boolean has_all_args = true;
    for (int i = 2; i < strs.length && has_all_args; i++) {
      String key = strs[i];
      Object val = configurationMap.getMapElement(key);
      if (val != null) {
        args.add(val);
      }
      else {
        log.warning("misssing argument for : " + key);
        has_all_args = false;
      }
    }
    if (has_all_args) {
      PrintfFormat pf = new PrintfFormat(rule);
      rule = pf.sprintf(args.toArray());
      log.debug2("Adding crawl rule: " + rule);
      return new CrawlRules.RE(rule, value);
    }
    return null;
  }

  public interface ConfigurableCrawlWindow {
    public CrawlWindow makeCrawlWindow();
  }
}
