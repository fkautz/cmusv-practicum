/*
 * $Id: WrapperUtil.java,v 1.12 2011/08/09 04:17:30 tlipkis Exp $
 */

/*

Copyright (c) 2000-2011 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.plugin.wrapper;
import java.util.*;
import org.lockss.util.*;
import org.lockss.daemon.*;
import org.lockss.plugin.*;
import org.lockss.plugin.ExploderHelper;
import org.lockss.plugin.definable.*;

/** Utilities to wrap plugin classes in error-catching proxy classes */
public class WrapperUtil {
  static final Logger log = Logger.getLogger("WrapperUtil");

  private static Map wrapperFactories = new HashMap();

  static {
    registerFactories();
  }

  /** Register factories for all interfaces that should be wrapped. */
  static void registerFactories() {
    registerWrapperFactory(FilterFactory.class,
			   new FilterFactoryWrapper.Factory());
    registerWrapperFactory(FilterRule.class,
			   new FilterRuleWrapper.Factory());
    registerWrapperFactory(UrlNormalizer.class,
			   new UrlNormalizerWrapper.Factory());
    registerWrapperFactory(org.lockss.extractor.LinkExtractor.class,
			   new LinkExtractorWrapper.Factory());
    registerWrapperFactory(org.lockss.extractor.LinkExtractorFactory.class,
			   new LinkExtractorFactoryWrapper.Factory());
    registerWrapperFactory(org.lockss.rewriter.LinkRewriterFactory.class,
			   new LinkRewriterFactoryWrapper.Factory());
    registerWrapperFactory(org.lockss.plugin.ArticleIteratorFactory.class,
			   new ArticleIteratorFactoryWrapper.Factory());
    registerWrapperFactory(org.lockss.extractor.ArticleMetadataExtractorFactory.class,
			   new ArticleMetadataExtractorFactoryWrapper.Factory());
    registerWrapperFactory(org.lockss.extractor.ArticleMetadataExtractor.class,
			   new ArticleMetadataExtractorWrapper.Factory());
    registerWrapperFactory(org.lockss.extractor.FileMetadataExtractorFactory.class,
			   new FileMetadataExtractorFactoryWrapper.Factory());
    registerWrapperFactory(org.lockss.extractor.FileMetadataExtractor.class,
			   new FileMetadataExtractorWrapper.Factory());
    registerWrapperFactory(LoginPageChecker.class,
			   new LoginPageCheckerWrapper.Factory());
    registerWrapperFactory(PermissionCheckerFactory.class,
			   new PermissionCheckerFactoryWrapper.Factory());
    registerWrapperFactory(DefinableArchivalUnit.ConfigurableCrawlWindow.class,
			   new ConfigurableCrawlWindowWrapper.Factory());
    registerWrapperFactory(org.lockss.util.urlconn.CacheResultHandler.class,
			   new CacheResultHandlerWrapper.Factory());
    registerWrapperFactory(ExploderHelper.class,
			   new ExploderHelperWrapper.Factory());
    registerWrapperFactory(CrawlUrlComparatorFactory.class,
			   new CrawlUrlComparatorFactoryWrapper.Factory());
  }

  /** Register a wrapper factory for instances of the interface */
  static void registerWrapperFactory(Class inter, WrapperFactory fact) {
    wrapperFactories.put(inter, fact);
    log.debug2("Registered " + fact.getClass().getName());
  }  

  /** Wrap the object using the wrapper factory registered for inter */
  public static <T> T wrap(T obj, Class<? super T> inter) {
//     ClassLoader objCL = obj.getClass().getClassLoader();
//     if (WrapperUtil.class.getClassLoader() == objCL) {
//       log.debug("Not wrapping " + obj.getClass());
//       return obj;
//     }
    WrapperFactory fact = (WrapperFactory)wrapperFactories.get(inter);
    if (fact == null) {
      warnNoWrapper(obj);
      return obj;
    }
    T wrapped = fact.wrap(obj);
    log.debug2("Wrapped " + obj);
    return wrapped;
  }

  static Set missingWrappers = new HashSet();

  static void warnNoWrapper(Object obj) {
    String name = obj.getClass().getName();
    synchronized (missingWrappers) {
      if (!missingWrappers.contains(name)) {
	missingWrappers.add(name);
	log.warning("No wrapper for " + name);
      }
    }
  }

  /** For tests */
  static Map getWrapperFactories() {
    return new HashMap(wrapperFactories);
  }  

  /** Return the wrapped object, for testing */
  public static Object unwrap(Object wrappedObj) {
    if (wrappedObj instanceof PluginCodeWrapper) {
      return ((PluginCodeWrapper)wrappedObj).getWrappedObj();
    } else {
      return wrappedObj;
    }
  }
}
