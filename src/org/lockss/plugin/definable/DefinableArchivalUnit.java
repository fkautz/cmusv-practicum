/*
 * $Id$
 */

/*
 Copyright (c) 2000-2006 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.plugin.definable;

import java.net.*;
import java.util.*;

import org.apache.commons.collections.*;
import org.apache.oro.text.regex.*;
import org.lockss.config.*;
import org.lockss.crawler.*;
import org.lockss.daemon.*;
import org.lockss.plugin.*;
import org.lockss.plugin.base.*;
import org.lockss.util.*;
import org.lockss.plugin.definable.DefinablePlugin.*;
import org.lockss.oai.*;
import org.lockss.state.AuState;

/**
 * <p>ConfigurableArchivalUnit: An implementatation of Base Archival Unit used
 * with the ConfigurablePlugin to allow a Map of values to be used to configure
 * and define the behaviour of a plugin.</p>
 * @author claire griffin
 * @version 1.0
 */
public class DefinableArchivalUnit extends BaseArchivalUnit {
  static Logger log = Logger.getLogger("DefinableArchivalUnit");

  /** If true, crawl rules in definable plugins are case-independent by
   * default.  Can override per-plugin with
   * <code>au_crawlrules_ignore_case</code> */
  static final String PARAM_CRAWL_RULES_IGNORE_CASE =
    Configuration.PREFIX + "plugin.crawlRulesIgnoreCase";
  static final boolean DEFAULT_CRAWL_RULES_IGNORE_CASE = true;


  public static final String PREFIX_NUMERIC = "numeric_";
  public static final int DEFAULT_AU_CRAWL_DEPTH = 1;
  public static final String DEFAULT_AU_EXPLODER_PATTERN = null;
  public static final String KEY_AU_NAME = "au_name";
  public static final String KEY_AU_CRAWL_RULES = "au_crawlrules";
  public static final String KEY_AU_CRAWL_RULES_IGNORE_CASE =
    "au_crawlrules_ignore_case";
  public static final String KEY_AU_CRAWL_WINDOW = "au_crawlwindow";
  public static final String KEY_AU_CRAWL_WINDOW_SER = "au_crawlwindow_ser";
  public static final String KEY_AU_EXPECTED_BASE_PATH = "au_expected_base_path";
  public static final String KEY_AU_CRAWL_DEPTH = "au_crawl_depth";
  public static final String KEY_AU_MANIFEST = "au_manifest";
  //public static final String KEY_AU_URL_NORMALIZER = "au_url_normalizer";
  public static final String KEY_AU_EXPLODER_HELPER = "au_exploder_helper";
  public static final String KEY_AU_EXPLODER_PATTERN = "au_exploder_pattern";

  public static final String SUFFIX_PARSER = "_parser";
  public static final String SUFFIX_LINK_EXTRACTOR_FACTORY =
    "_link_extractor_factory";
  public static final String SUFFIX_FILTER_RULE = "_filter";
  public static final String SUFFIX_FILTER_FACTORY = "_filter_factory";
  public static final String SUFFIX_LINK_REWRITER_FACTORY =
    "_link_rewriter_factory";
  public static final String SUFFIX_FETCH_RATE_LIMITER = "_fetch_rate_limiter";

  public static final String KEY_AU_PERMISSION_CHECKER_FACTORY =
    "au_permission_checker_factory";

  public static final String KEY_AU_LOGIN_PAGE_CHECKER =
    "au_login_page_checker";
  public static final String KEY_AU_REDIRECT_TO_LOGIN_URL_PATTERN =
    "au_redirect_to_login_url_pattern";
  public static final String KEY_DONT_POLL =
    "au_dont_poll";

  public static final String RANGE_SUBSTITUTION_STRING = "(.*)";
  public static final String NUM_SUBSTITUTION_STRING = "(\\d+)";

  protected ExternalizableMap definitionMap;

  /** Array of  DefinablePlugin keys that hold parameterized printf
   * strings, excluding regexps */ 
  public static String[] printfPatternKeys = {
    KEY_AU_NAME,
    KEY_AU_START_URL,
    KEY_AU_MANIFEST,
    KEY_AU_REDIRECT_TO_LOGIN_URL_PATTERN,
  };

  /** Array of all DefinablePlugin keys that hold parameterized printf
   * regexp strings */ 
  public static String[] printfRegexpKeys = {
    KEY_AU_CRAWL_RULES,
  };

  protected DefinableArchivalUnit(Plugin myPlugin) {
    super(myPlugin);
    throw new UnsupportedOperationException(
        "DefinableArchvialUnit requires DefinablePlugin for construction");
  }

  protected DefinableArchivalUnit(DefinablePlugin myPlugin,
                                  ExternalizableMap definitionMap) {
    super(myPlugin);
    this.definitionMap = definitionMap;
  }

  DefinablePlugin getDefinablePlugin() {
    return (DefinablePlugin)plugin;
  }

  protected List<String> getElementList(String key) {
    return getDefinablePlugin().getElementList(key);
  }

  protected List convertPatternList(String key) {
    List<String> patternList = getElementList(key);

    if (patternList == null) {
      return null;
    }
    ArrayList<String> res = new ArrayList(patternList.size());
    for (String pattern : patternList) {
      if (StringUtil.isNullString(pattern)) {
	log.warning("Null pattern string in " + key);
	continue;
      }
      List<String> lst = convertVariableString(pattern);
      if (lst == null) {
	log.warning("Null converted string in " + key + ", from " + pattern);
	continue;
      }
      res.addAll(lst);
    }
    res.trimToSize();
    return res;
  }

  protected List getPermissionPages() {
    List res = convertPatternList(KEY_AU_MANIFEST);
    if (res == null) {
      return super.getPermissionPages();
    }
    return res;
  }

  @Override
  protected List<String> makeStartUrls() throws ConfigurationException {
    List res = convertPatternList(KEY_AU_START_URL);
    if (res == null) {
      String msg = "Bad start url pattern: "
	+ getElementList(KEY_AU_START_URL);
      log.error(msg);
      throw new ConfigurationException(msg);
    }
    log.debug2("Setting start urls " + res);
    return res;
  }

  protected void loadAuConfigDescrs(Configuration config) throws
      ConfigurationException {
    super.loadAuConfigDescrs(config);
    // override any defaults
    defaultFetchDelay = definitionMap.getLong(KEY_AU_DEFAULT_PAUSE_TIME,
        DEFAULT_FETCH_DELAY);

    defaultContentCrawlIntv = definitionMap.getLong(KEY_AU_DEFAULT_NEW_CONTENT_CRAWL_INTERVAL,
        DEFAULT_NEW_CONTENT_CRAWL_INTERVAL);

    // install any other values - should these be config params?
    long l_val;
    l_val = definitionMap.getLong(KEY_AU_MAX_SIZE,
                                  DEFAULT_AU_MAX_SIZE);
    paramMap.putLong(KEY_AU_MAX_SIZE, l_val);

    l_val = definitionMap.getLong(KEY_AU_MAX_FILE_SIZE,
                                  DEFAULT_AU_MAX_FILE_SIZE);
    paramMap.putLong(KEY_AU_MAX_FILE_SIZE, l_val);

  }

  protected void addImpliedConfigParams()
      throws ArchivalUnit.ConfigurationException {
    super.addImpliedConfigParams();
    String umsg =
      definitionMap.getString(DefinablePlugin.KEY_PLUGIN_AU_CONFIG_USER_MSG,
			      null);
    if (umsg != null) {
      paramMap.putString(KEY_AU_CONFIG_USER_MSG, umsg);
    }
    String urlPat =
      (String)definitionMap.getMapElement(KEY_AU_REDIRECT_TO_LOGIN_URL_PATTERN);
    if (urlPat != null) {
      paramMap.setMapElement(KEY_AU_REDIRECT_TO_LOGIN_URL_PATTERN,
			     makeLoginUrlPattern(urlPat));
    }
  }

  protected Pattern makeLoginUrlPattern(String val)
      throws ArchivalUnit.ConfigurationException {

    String patStr = convertVariableRegexpString(val).regexp;
    if (patStr == null) {
      String msg = "Missing regexp args: " + val;
      log.error(msg);
      throw new ConfigurationException(msg);
    }
    try {
      return
	RegexpUtil.getCompiler().compile(patStr, Perl5Compiler.READ_ONLY_MASK);
    } catch (MalformedPatternException e) {
      String msg = "Can't compile URL pattern: " + patStr;
      log.error(msg + ": " + e.toString());
      throw new ArchivalUnit.ConfigurationException(msg, e);
    }
  }

  public boolean isLoginPageUrl(String url) {
    Pattern urlPat =
      (Pattern)paramMap.getMapElement(KEY_AU_REDIRECT_TO_LOGIN_URL_PATTERN);
    if (urlPat == null) {
      return false;
    }
    Perl5Matcher matcher = RegexpUtil.getMatcher();
    return  matcher.contains(url, urlPat);
  }    

  protected String makeName() {
    String namestr = definitionMap.getString(KEY_AU_NAME, "");
    List<String> lst = convertVariableString(namestr);
    if (lst.size() != 1) {
      throw new PluginException.InvalidDefinition("Illegal AU name pattern:"
						  + namestr);
    }
    String name = lst.get(0);
    log.debug2("setting name string: " + name);
    return name;
  }

  protected CrawlRule makeRules() throws LockssRegexpException {
    Object rule = definitionMap.getMapElement(KEY_AU_CRAWL_RULES);
    boolean defaultIgnoreCase =
      CurrentConfig.getBooleanParam(PARAM_CRAWL_RULES_IGNORE_CASE,
				    DEFAULT_CRAWL_RULES_IGNORE_CASE);
    boolean ignoreCase =
      definitionMap.getBoolean(KEY_AU_CRAWL_RULES_IGNORE_CASE,
			       defaultIgnoreCase);

    if (rule instanceof String) {
	CrawlRuleFromAuFactory fact = (CrawlRuleFromAuFactory)
            newAuxClass((String) rule, CrawlRuleFromAuFactory.class);
	return fact.createCrawlRule(this);
    }
    ArrayList rules = null;
    if (rule instanceof List) {
      List<String> templates = (List<String>)rule;
      rules = new ArrayList(templates.size());
      for (String rule_template : templates) {
	CrawlRule cr = convertRule(rule_template, ignoreCase);
	if (cr != null) {
	  rules.add(cr);
	}
      }
      rules.trimToSize();

      if (rules.size() > 0) {
	return new CrawlRules.FirstMatch(rules);
      } else {
	log.error("No crawl rules found for plugin: " + makeName());
	return null;
      }
    }
    return null;
  }

  protected OaiRequestData makeOaiData() {
    URL oai_request_url =
      paramMap.getUrl(ConfigParamDescr.OAI_REQUEST_URL.getKey());
    String oaiRequestUrlStr = oai_request_url.toString();
    String oai_au_spec = null;
    try {
      oai_au_spec = paramMap.getString(ConfigParamDescr.OAI_SPEC.getKey());
    } catch (NoSuchElementException ex) {
      // This is acceptable.  Null value will fetch all entries.
      log.debug("No oai_spec for this plugin.");
    }
    log.debug3("Creating OaiRequestData with oaiRequestUrlStr" +
	       oaiRequestUrlStr + " and oai_au_spec " + oai_au_spec);
    return new OaiRequestData(oaiRequestUrlStr,
                      "http://purl.org/dc/elements/1.1/",
                      "identifier",
                      oai_au_spec,
                      "oai_dc"
                      );

  }

  protected CrawlSpec makeCrawlSpec() throws LockssRegexpException {

    CrawlRule rule = makeRules();
    String crawl_type = definitionMap.getString(DefinablePlugin.KEY_CRAWL_TYPE,
                                                DefinablePlugin.DEFAULT_CRAWL_TYPE);
    //XXX put makePermissionCheckersHere

    if(crawl_type.equals(DefinablePlugin.CRAWL_TYPE_OAI)) {
      boolean follow_links =
          definitionMap.getBoolean(DefinablePlugin.KEY_FOLLOW_LINKS, true);
      return new OaiCrawlSpec(makeOaiData(), getPermissionPages(),
                              null, rule, follow_links,
                              makeLoginPageChecker());
    } else { // for now use the default spider crawl spec
      int depth = definitionMap.getInt(KEY_AU_CRAWL_DEPTH, DEFAULT_AU_CRAWL_DEPTH);
      String exploderPattern = definitionMap.getString(KEY_AU_EXPLODER_PATTERN,
						  DEFAULT_AU_EXPLODER_PATTERN);
      ExploderHelper eh = getDefinablePlugin().getExploderHelper();

      return new SpiderCrawlSpec(getNewContentCrawlUrls(),
				 getPermissionPages(), rule, depth,
				 makePermissionChecker(),
				 makeLoginPageChecker(), exploderPattern, eh);
    }
  }

  protected LoginPageChecker makeLoginPageChecker() {
    String loginPageCheckerClass =
      definitionMap.getString(KEY_AU_LOGIN_PAGE_CHECKER, null);
    if (loginPageCheckerClass == null) {
      return null;
    }
    LoginPageChecker checker =
      (LoginPageChecker)newAuxClass(loginPageCheckerClass,
				    LoginPageChecker.class);
    return checker;
  }

  protected PermissionChecker makePermissionChecker() {
    String permissionCheckerFactoryClass =
      definitionMap.getString(KEY_AU_PERMISSION_CHECKER_FACTORY, null);
    if (permissionCheckerFactoryClass == null) {
      return null;
    }
    log.debug3("Found PermissionCheckerFactory class: " +
	       permissionCheckerFactoryClass);

    PermissionCheckerFactory fact =
      (PermissionCheckerFactory)newAuxClass(permissionCheckerFactoryClass,
					    PermissionCheckerFactory.class);
    log.debug("Loaded PermissionCheckerFactory: " + fact);
    try {
      List permissionCheckers = fact.createPermissionCheckers(this);
      if (permissionCheckers.size() > 1) {
        log.error("Plugin specifies multiple permission checkers, but we " +
		  "only support one: " + this);

      }
      return (PermissionChecker)permissionCheckers.get(0);
    } catch (PluginException e) {
      throw new RuntimeException(e);
    }
  }

  protected CrawlWindow makeCrawlWindow() {
    return getDefinablePlugin().makeCrawlWindow();
  }

  public boolean shouldCallTopLevelPoll(AuState aus) {
    if (definitionMap.getBoolean(KEY_DONT_POLL, false)) {
      return false;
    }
    return super.shouldCallTopLevelPoll(aus);
  }

// ---------------------------------------------------------------------
//   CLASS LOADING SUPPORT ROUTINES
// ---------------------------------------------------------------------

  Object newAuxClass(String className, Class expectedType) {
    return getDefinablePlugin().newAuxClass(className, expectedType);
  }

// ---------------------------------------------------------------------
//   VARIABLE ARGUMENT REPLACEMENT SUPPORT ROUTINES
// ---------------------------------------------------------------------

  MatchPattern convertVariableRegexpString(String printfString) {
    return convertVariableRegexpString(PrintfUtil.stringToPrintf(printfString));
  }

  List<String> convertVariableString(String printfString) {
    return convertVariableString(PrintfUtil.stringToPrintf(printfString));
  }

  MatchPattern convertVariableRegexpString(PrintfUtil.PrintfData p_data) {
    String format = p_data.getFormat();
    Collection p_args = p_data.getArguments();
    ArrayList substitute_args = new ArrayList(p_args.size());
    ArrayList matchArgs = new ArrayList();
    ArrayList matchArgDescrs = new ArrayList();

    boolean has_all_args = true;
    for (Iterator it = p_args.iterator(); it.hasNext(); ) {
      String key = (String) it.next();
      Object val = paramMap.getMapElement(key);
      if (val != null) {
	Object substVal = null;
	ConfigParamDescr descr = plugin.findAuConfigDescr(key);
	switch (descr != null ? descr.getType()
		: ConfigParamDescr.TYPE_STRING) {
	case ConfigParamDescr.TYPE_SET:
	  // val must be a list; ok to throw if not
	  List<String> vec = (List<String>)val;
	  List tmplst = new ArrayList(vec.size());
	  for (String ele : vec) {
	    tmplst.add(Perl5Compiler.quotemeta(ele));
	  }
	  substVal = StringUtil.separatedString(tmplst, "(?:", "|", ")");
	  break;
	case ConfigParamDescr.TYPE_RANGE:
	  substVal = RANGE_SUBSTITUTION_STRING;
	  matchArgs.add(val);
	  matchArgDescrs.add(descr);
	  break;
	case ConfigParamDescr.TYPE_NUM_RANGE:
	  substVal = NUM_SUBSTITUTION_STRING;
	  matchArgs.add(val);
	  matchArgDescrs.add(descr);
	  break;
	default:
	  if (val instanceof String) {
	    val = Perl5Compiler.quotemeta((String)val);
	  }
	  substVal = val;
	}
	substitute_args.add(substVal);
      } else {
        log.warning("misssing argument for : " + key);
        has_all_args = false;
      }
    }

    if (!has_all_args) {
      log.warning("Missing variable arguments: " + p_data);
      return new MatchPattern();
    }
    PrintfFormat pf = new PrintfFormat(format);
    if (log.isDebug3()) {
      log.debug3("sprintf(\""+format+"\", "+substitute_args+")");
    }

    return new MatchPattern(pf.sprintf(substitute_args.toArray()),
			    matchArgs, matchArgDescrs);
  }

  List<String> convertVariableString(PrintfUtil.PrintfData p_data) {
    String format = p_data.getFormat();
    Collection<String> p_args = p_data.getArguments();

    // If any set-valued args are present, substitute_args holds sets
    // (lists) of arg values.  If not, it holds individual arg values.
    ArrayList substitute_args = new ArrayList(p_args.size());
    ArrayList res = new ArrayList();
    boolean has_all_args = true;
    boolean haveSets = false;

    for (String key : p_args) {
      Object val = paramMap.getMapElement(key);
      if (val != null) {
	ConfigParamDescr descr = plugin.findAuConfigDescr(key);
	switch (descr != null ? descr.getType()
		: ConfigParamDescr.TYPE_STRING) {
	case ConfigParamDescr.TYPE_SET:
	  if (!haveSets) {
	    // if this is first set seen, replace all values so far with
	    // singleton list of value
	    for (int ix = 0; ix < substitute_args.size(); ix++) {
	      substitute_args.set(ix,
				  Collections.singletonList(substitute_args.get(ix)));
	    }
	    haveSets = true;
	  }
	  // val must be a list; ok to throw if not
	  List<String> vec = (List<String>)val;
	  substitute_args.add(vec);
	  break;
	case ConfigParamDescr.TYPE_RANGE:
	case ConfigParamDescr.TYPE_NUM_RANGE:
	  throw new PluginException.InvalidDefinition("Range params legal only in regexps:" + key);
	default:
	  if (haveSets) {
	    substitute_args.add(Collections.singletonList(val));
	  } else {
	    substitute_args.add(val);
	  }
	  break;
	}
      } else {
        log.warning("misssing argument for : " + key);
        has_all_args = false;
      }
    }

    if (!has_all_args) {
      log.warning("Missing variable arguments: " + p_data);
      return null;
    }
    PrintfFormat pf = new PrintfFormat(format);
    if (!substitute_args.isEmpty() && (haveSets)) {
      for (CartesianProductIterator iter =
	     new CartesianProductIterator(substitute_args);
	   iter.hasNext(); ) {
	Object[] oneCombo = (Object[])iter.next();
	if (log.isDebug3()) {
	  log.debug3("sprintf(\""+format+"\", "+oneCombo+")");
	}
	res.add(pf.sprintf(oneCombo));
      }
    } else {
      if (log.isDebug3()) {
	log.debug3("sprintf(\""+format+"\", "+substitute_args+")");
      }
      res.add(pf.sprintf(substitute_args.toArray()));
    }
    res.trimToSize();
    return res;
  }

  CrawlRule convertRule(String ruleString, boolean ignoreCase)
      throws LockssRegexpException {

    int pos = ruleString.indexOf(",");
    int action = Integer.parseInt(ruleString.substring(0, pos));
    String printfString = ruleString.substring(pos + 1);

    MatchPattern mp = convertVariableRegexpString(printfString);
    if (mp.regexp == null) {
      return null;
    }
    List<List> matchArgs = mp.matchArgs;
    switch (matchArgs.size()) {
    case 0:
      return new CrawlRules.RE(mp.regexp, ignoreCase, action);
    case 1:
      List argPair = matchArgs.get(0);
      ConfigParamDescr descr = mp.matchArgDescrs.get(0);
      switch (descr.getType()) {
      case ConfigParamDescr.TYPE_RANGE:
	return new CrawlRules.REMatchRange(mp.regexp,
					   ignoreCase,
					   action,
					   (String)argPair.get(0),
					   (String)argPair.get(1));
      case ConfigParamDescr.TYPE_NUM_RANGE:
	return new CrawlRules.REMatchRange(mp.regexp,
					   ignoreCase,
					   action,
					   ((Long)argPair.get(0)).longValue(),
					   ((Long)argPair.get(1)).longValue());
      default:
	throw new RuntimeException("Shouldn't happen.  Unknown REMatchRange arg type: " + descr);
      }

    default:
      throw new LockssRegexpException("Multiple range args not yet supported");
    }
  }

  class MatchPattern {
    String regexp;
    List<List> matchArgs;
    List<ConfigParamDescr> matchArgDescrs;

    MatchPattern() {
    }

    MatchPattern(String regexp,
		 List<List> matchArgs,
		 List<ConfigParamDescr> matchArgDescrs) {
      this.regexp = regexp;
      this.matchArgs = matchArgs;
      this.matchArgDescrs = matchArgDescrs;
    }
  }

  public interface ConfigurableCrawlWindow {
    public CrawlWindow makeCrawlWindow()
	throws PluginException;
  }

}
