/*
 * $Id$
 */

/*
 Copyright (c) 2000-2010 Board of Trustees of Leland Stanford Jr. University,
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
import static org.lockss.plugin.definable.DefinablePlugin.PrintfContext;

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

  /** If true, crawl rules implicitly include the start URLs and permission
   * URLs */
  static final String PARAM_CRAWL_RULES_INCLUDE_START =
    Configuration.PREFIX + "plugin.crawlRulesIncludeStartUrl";
  static final boolean DEFAULT_CRAWL_RULES_INCLUDE_START = true;

  static final int CRAWL_RULE_CONTAINS_SET_THRESHOLD = 12;

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
  public static final String KEY_AU_SUBSTANCE_URL_PATTERN =
    "au_substance_url_pattern";
  public static final String KEY_AU_NON_SUBSTANCE_URL_PATTERN =
    "au_non_substance_url_pattern";
  public static final String KEY_AU_CRAWL_COOKIE_POLICY =
    "au_crawl_cookie_policy";
  public static final String KEY_AU_CRAWL_SELENIUM_CANDIDATES =
	  "au_crawl_selenium_candidates";

  /** Suffix for testing override submaps.  Values in a XXX_override map
   * will be copied to the main map when in testing mode XXX.  In the
   * presence of plugin inheritence, the first entry in (child-overrides,
   * child, parent-overrides, parent) wins.  */
  public static final String SUFFIX_OVERRIDE = "_override";

  public static final String SUFFIX_PARSER = "_parser";
  public static final String SUFFIX_LINK_EXTRACTOR_FACTORY =
    "_link_extractor_factory";
  public static final String SUFFIX_FILTER_RULE = "_filter";
  // XXX _filter_factory should be changed to _hash_filter_factory but
  // plugins will have to be changed.  Note that this symbol is also used
  // in PdfUtil to refer to the PDF filter factory hint in the title DB;
  // either that will need to change or the title DB will.
  public static final String SUFFIX_HASH_FILTER_FACTORY = "_filter_factory";
  public static final String SUFFIX_CRAWL_FILTER_FACTORY =
    "_crawl_filter_factory";
  public static final String SUFFIX_LINK_REWRITER_FACTORY =
    "_link_rewriter_factory";
  public static final String SUFFIX_ARTICLE_MIME_TYPE =
    "_article_mime_type";
  public static final String SUFFIX_METADATA_EXTRACTOR_FACTORY_MAP =
    "_metadata_extractor_factory_map"; 

 public static final String SUFFIX_FETCH_RATE_LIMIT = "_fetch_rate_limit";

  public static final String KEY_AU_PERMISSION_CHECKER_FACTORY =
    "au_permission_checker_factory";

  public static final String KEY_AU_LOGIN_PAGE_CHECKER =
    "au_login_page_checker";
  public static final String KEY_AU_REDIRECT_TO_LOGIN_URL_PATTERN =
    "au_redirect_to_login_url_pattern";
  public static final String KEY_DONT_POLL =
    "au_dont_poll";

  protected ExternalizableMap definitionMap;

  /** Context in which various printf templates are interpreted, for
   * argument type checking */
  static Map<String,PrintfContext> printfKeysContext = new HashMap();
  static {
    printfKeysContext.put(KEY_AU_START_URL, PrintfContext.URL);
    printfKeysContext.put(KEY_AU_MANIFEST, PrintfContext.URL);
    printfKeysContext.put(KEY_AU_START_URL, PrintfContext.URL);

    printfKeysContext.put(KEY_AU_REDIRECT_TO_LOGIN_URL_PATTERN,
			  PrintfContext.Regexp);
    printfKeysContext.put(KEY_AU_CRAWL_RULES, PrintfContext.Regexp);
    printfKeysContext.put(KEY_AU_SUBSTANCE_URL_PATTERN, PrintfContext.Regexp);
    printfKeysContext.put(KEY_AU_NON_SUBSTANCE_URL_PATTERN,
			  PrintfContext.Regexp);

    printfKeysContext.put(KEY_AU_NAME, PrintfContext.Display);
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

  protected List<String> getPermissionPages() {
    List res = convertUrlListList(KEY_AU_MANIFEST);
    if (res == null) {
      return super.getPermissionPages();
    }
    return res;
  }

  public String getPerHostPermissionPath() {
    return (String)definitionMap.getMapElement(DefinablePlugin.KEY_PER_HOST_PERMISSION_PATH);
  }

  /** Use rate limiter source specified in AU, if any, then in plugin, then
   * default */
  @Override
  protected String getFetchRateLimiterSource() {
    String defaultSource =
      CurrentConfig.getParam(PARAM_DEFAULT_FETCH_RATE_LIMITER_SOURCE,
			     DEFAULT_DEFAULT_FETCH_RATE_LIMITER_SOURCE);
    String pluginSrc = 
      definitionMap.getString(DefinablePlugin.KEY_PLUGIN_FETCH_RATE_LIMITER_SOURCE,
			      defaultSource);
    String auSrc =
      paramMap.getString(KEY_AU_FETCH_RATE_LIMITER_SOURCE, pluginSrc);
    return CurrentConfig.getParam(PARAM_OVERRIDE_FETCH_RATE_LIMITER_SOURCE,
				  auSrc);
  }

  @Override
  protected List<String> makeStartUrls() throws ConfigurationException {
    List<String> res = convertUrlListList(KEY_AU_START_URL);
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
    // If/when these are used, don't waste map entries on default values
//     long l_val;
//     l_val = definitionMap.getLong(KEY_AU_MAX_SIZE,
//                                   DEFAULT_AU_MAX_SIZE);
//     paramMap.putLong(KEY_AU_MAX_SIZE, l_val);

//     l_val = definitionMap.getLong(KEY_AU_MAX_FILE_SIZE,
//                                   DEFAULT_AU_MAX_FILE_SIZE);
//     paramMap.putLong(KEY_AU_MAX_FILE_SIZE, l_val);

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

    String patStr = convertVariableRegexpString(val).getRegexp();
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

  public List<Pattern> makeNonSubstanceUrlPatterns()
      throws ArchivalUnit.ConfigurationException {
    return compileRegexpList(KEY_AU_NON_SUBSTANCE_URL_PATTERN);
  }

  public List<Pattern> makeSubstanceUrlPatterns()
      throws ArchivalUnit.ConfigurationException {
    return compileRegexpList(KEY_AU_SUBSTANCE_URL_PATTERN);
  }

  List<Pattern> compileRegexpList(String key)
      throws ArchivalUnit.ConfigurationException {
    List<String> lst = convertRegexpList(key);
    if (lst == null) {
      return null;
    }
    return compileRegexpList(lst, key);
  }

  List<Pattern> compileRegexpList(List<String> regexps, String key)
      throws ArchivalUnit.ConfigurationException {
    List<Pattern> res = new ArrayList<Pattern>(regexps.size());
    Perl5Compiler comp = RegexpUtil.getCompiler();
    int flags = Perl5Compiler.READ_ONLY_MASK;
    if (isCaseIndependentCrawlRules()) {
      flags += Perl5Compiler.CASE_INSENSITIVE_MASK;
    }
    for (String re : regexps) {
      try {
	res.add(comp.compile(re, flags));
      } catch (MalformedPatternException e) {
	String msg = "Can't compile URL pattern: " + re;
	log.error(msg + ": " + e.toString());
	throw new ArchivalUnit.ConfigurationException(msg, e);
      }
    }
    return res;
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
    String name = convertNameString(namestr);
    log.debug2("setting name string: " + name);
    return name;
  }

  protected CrawlRule makeRules() throws LockssRegexpException {
    CrawlRule rule = makeRules0();
    if (rule == null
	|| !CurrentConfig.getBooleanParam(PARAM_CRAWL_RULES_INCLUDE_START,
					  DEFAULT_CRAWL_RULES_INCLUDE_START)) {
      return rule;
    }
    // If any of the the start URLs or permission URLs aren't otherwise
    // included in the crawl rule, add them explicitly, by wrapping the
    // rule in one that first checks the start and permission URLs.

    Collection<String> expUrls = new HashSet<String>();

    List<String> perms = getPermissionPages();
    if (perms != null) {
      for (String url : perms) {
	if (rule.match(url) != CrawlRule.INCLUDE) {
	  expUrls.add(url);
	}
      }
    }
    List<String> starts = getNewContentCrawlUrls();
    if (starts != null) {
      for (String url : starts) {
	if (rule.match(url) != CrawlRule.INCLUDE) {
	  expUrls.add(url);
	}
      }
    }
    if (expUrls.isEmpty()) {
      return rule;
    } else {
      if (expUrls.size() < CRAWL_RULE_CONTAINS_SET_THRESHOLD) {
	expUrls = new ArrayList(expUrls);
      }
      // Must check the explicit list first, even though it will hardly
      // ever match, as main rule could return EXCLUDE
      return new CrawlRules.FirstMatch(ListUtil.list(new CrawlRules.Contains(expUrls),
						     rule));

    }
  }
  
  protected List<String> makeSeleniumCandidates() throws ConfigurationException {
	  Object o = definitionMap.getMapElement(KEY_AU_CRAWL_SELENIUM_CANDIDATES);
	  if (!(o instanceof List<?>)) {
		  throw new ConfigurationException("au_crawl_selenium_candidates should be list of urls (String)");
	  }
	  return (List<String>)o;
  }

  boolean isCaseIndependentCrawlRules() {
    boolean defaultIgnoreCase =
      CurrentConfig.getBooleanParam(PARAM_CRAWL_RULES_IGNORE_CASE,
				    DEFAULT_CRAWL_RULES_IGNORE_CASE);
    return definitionMap.getBoolean(KEY_AU_CRAWL_RULES_IGNORE_CASE,
				    defaultIgnoreCase);
  }

  CrawlRule makeRules0() throws LockssRegexpException {
    Object rule = definitionMap.getMapElement(KEY_AU_CRAWL_RULES);

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
	CrawlRule cr = convertRule(rule_template,
				   isCaseIndependentCrawlRules());
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
    CrawlSpec res;
    CrawlRule rule = makeRules();
    String crawl_type = definitionMap.getString(DefinablePlugin.KEY_CRAWL_TYPE,
                                                DefinablePlugin.DEFAULT_CRAWL_TYPE);
    //XXX put makePermissionCheckersHere

    if(crawl_type.equals(DefinablePlugin.CRAWL_TYPE_OAI)) {
      boolean follow_links =
          definitionMap.getBoolean(DefinablePlugin.KEY_FOLLOW_LINKS, true);
      res = new OaiCrawlSpec(makeOaiData(), getPermissionPages(),
			     null, rule, follow_links,
			     makeLoginPageChecker());
    } else { // for now use the default spider crawl spec
      int depth = definitionMap.getInt(KEY_AU_CRAWL_DEPTH, DEFAULT_AU_CRAWL_DEPTH);
      String exploderPattern = definitionMap.getString(KEY_AU_EXPLODER_PATTERN,
						  DEFAULT_AU_EXPLODER_PATTERN);
      ExploderHelper eh = getDefinablePlugin().getExploderHelper();

      res = new SpiderCrawlSpec(getNewContentCrawlUrls(),
				getPermissionPages(), rule, depth,
				makePermissionChecker(),
				makeLoginPageChecker(), exploderPattern, eh);
      String cookiePolicy =
          definitionMap.getString(KEY_AU_CRAWL_COOKIE_POLICY, null);
      if (cookiePolicy != null) {
	res.setCookiePolicy(cookiePolicy);
      }
    }
    
    return res;
  }

  protected LoginPageChecker makeLoginPageChecker() {
    return getDefinablePlugin().makeLoginPageChecker();
  }

  protected PermissionChecker makePermissionChecker() {
    PermissionCheckerFactory fact =
      getDefinablePlugin().getPermissionCheckerFactory();
    if (fact != null) {
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
    return null;
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

  protected List<String> convertUrlListList(String key) {
    return convertUrlList(getElementList(key), key);
  }

  protected List<String> convertRegexpList(String key) {
    return convertRegexpList(getElementList(key), key);
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

  PrintfConverter.MatchPattern
    convertVariableRegexpString(String printfString) {
    return new PrintfConverter.RegexpConverter(plugin, paramMap).getMatchPattern(printfString);
  }

  List<String> convertUrlList(String printfString) {
    return new PrintfConverter.UrlListConverter(plugin, paramMap).getUrlList(printfString);
  }

  String convertNameString(String printfString) {
    return new PrintfConverter.NameConverter(plugin, paramMap).getName(printfString);
  }

  protected List<String> convertUrlList(List<String> printfStrings, String key) {
    if (printfStrings == null) {
      return null;
    }
    // Just a guess; each printf may generate more than one URL
    ArrayList<String> res = new ArrayList<String>(printfStrings.size());
    for (String pattern : printfStrings) {
      if (StringUtil.isNullString(pattern)) {
	log.warning("Null pattern string in " + key);
	continue;
      }
      List<String> lst = convertUrlList(pattern);
      if (lst == null) {
	log.warning("Null converted string in " + key + ", from " + pattern);
	continue;
      }
      res.addAll(lst);
    }
    res.trimToSize();
    return res;
  }

  protected List<String> convertRegexpList(List<String> printfStrings,
					    String key) {
    if (printfStrings == null) {
      return null;
    }
    ArrayList<String> res = new ArrayList<String>(printfStrings.size());
    for (String pattern : printfStrings) {
      if (StringUtil.isNullString(pattern)) {
	log.warning("Null pattern string in " + key);
	continue;
      }
      String pat = convertVariableRegexpString(pattern).getRegexp();
      if (pat == null) {
	log.warning("Null converted regexp in " + key + ", from " + pattern);
	continue;
      }
      res.add(pat);
    }
    return res;
  }

  protected List<String> getElementList(String key) {
    return getDefinablePlugin().getElementList(key);
  }

  CrawlRule convertRule(String ruleString, boolean ignoreCase)
      throws LockssRegexpException {

    int pos = ruleString.indexOf(",");
    int action = Integer.parseInt(ruleString.substring(0, pos));
    String printfString = ruleString.substring(pos + 1);

    PrintfConverter.MatchPattern mp = convertVariableRegexpString(printfString);
    if (mp.getRegexp() == null) {
      return null;
    }
    List<List> matchArgs = mp.getMatchArgs();
    switch (matchArgs.size()) {
    case 0:
      return new CrawlRules.RE(mp.getRegexp(), ignoreCase, action);
    case 1:
      List argPair = matchArgs.get(0);
      ConfigParamDescr descr = mp.getMatchArgDescrs().get(0);
      switch (descr.getType()) {
      case ConfigParamDescr.TYPE_RANGE:
	return new CrawlRules.REMatchRange(mp.getRegexp(),
					   ignoreCase,
					   action,
					   (String)argPair.get(0),
					   (String)argPair.get(1));
      case ConfigParamDescr.TYPE_NUM_RANGE:
	return new CrawlRules.REMatchRange(mp.getRegexp(),
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


  public interface ConfigurableCrawlWindow {
    public CrawlWindow makeCrawlWindow()
	throws PluginException;
  }

}
