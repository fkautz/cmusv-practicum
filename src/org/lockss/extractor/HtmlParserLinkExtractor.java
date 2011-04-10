/**
 * 
 */
package org.lockss.extractor;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.htmlparser.Parser;
import org.htmlparser.Tag;
import org.htmlparser.lexer.Lexer;
import org.htmlparser.lexer.Page;
import org.htmlparser.tags.FormTag;
import org.htmlparser.tags.InputTag;
import org.htmlparser.tags.OptionTag;
import org.htmlparser.tags.SelectTag;
import org.htmlparser.tags.StyleTag;
import org.htmlparser.util.ParserException;
import org.htmlparser.util.Translate;
import org.htmlparser.visitors.NodeVisitor;
import org.lockss.daemon.PluginException;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.plugin.UrlNormalizer;
import org.lockss.util.Logger;
import org.lockss.util.ReaderInputStream;
import org.lockss.util.StringUtil;
import org.lockss.util.UrlUtil;

/**
 * An all purpose HTML Link extractor which attempts to collect a superset of links extracted by {@link GoslingHtmlLinkExtractor}.
 * Specifically, this extractor provides an alternate {@link Parser} based implementation of LinkExtractor interface.
 * This dom traversal capability is suitable to extract links from an HTML FORM that consists of radio buttons, checkboxes or select options.
 *  
 * @author vibhor
 * 
 */
public class HtmlParserLinkExtractor implements LinkExtractor {
	private static final Logger logger = Logger.getLogger("HtmlParserLinkExtractor");

	/**
	 * A link extractor interface that can parse a given html tag and extract link(s) from it.
	 * 
	 * @author nvibhor
	 */
	private interface TagLinkExtractor {
		/**
		 * Extract link(s) from this tag.
		 * @param au Current archival unit to which this html document belongs.
		 * @param cb A callback to record extracted links.
		 */
		public void extractLink(ArchivalUnit au, Callback cb);
	}

	/**
	 * Implementation of {@link TagLinkExtractor} interface for html tags that contain an 'href' attribute.
	 * 
	 * @author nvibhor
	 *
	 */
	private static class HrefTagLinkExtractor implements TagLinkExtractor {
		private Tag tag_;

		/**
		 * Constructor
		 * @param tag {@link Tag} object corresponding to this html tag.
		 */
		public HrefTagLinkExtractor(Tag tag) {
			this.tag_ = tag;
		}

		@Override
		public void extractLink(ArchivalUnit au, Callback cb) {
			String target = this.tag_.getAttribute("href");
			if (target == null) return;
			
			target = Translate.decode(target);
			cb.foundLink(target);
		}
	}

	/**
	 * Implementation of {@link TagLinkExtractor} interface for html tags that contain a 'src' attribute.
	 * 
	 * @author nvibhor
	 *
	 */
	private static class SrcTagLinkExtractor implements TagLinkExtractor {
		private Tag tag_;

		/**
		 * Constructor
		 * @param tag {@link Tag} object corresponding to this html tag.
		 */
		public SrcTagLinkExtractor(Tag tag) {
			this.tag_ = tag;
		}

		@Override
		public void extractLink(ArchivalUnit au, Callback cb) {
			cb.foundLink(this.tag_.getAttribute("src"));
		}
	}

	/**
	 * Implementation of {@link TagLinkExtractor} interface for html tags that contain a 'code' attribute.
	 * 
	 * @author nvibhor
	 *
	 */
	private static class CodeTagLinkExtractor implements TagLinkExtractor {
		private Tag tag_;

		/**
		 * Constructor
		 * @param tag {@link Tag} object corresponding to this html tag.
		 */
		public CodeTagLinkExtractor(Tag tag) {
			this.tag_ = tag;
		}

		@Override
		public void extractLink(ArchivalUnit au, Callback cb) {
			cb.foundLink(this.tag_.getAttribute("code"));
		}
	}

	/**
	 * Implementation of {@link TagLinkExtractor} interface for html tags that contain a 'codebase' attribute.
	 * 
	 * @author nvibhor
	 *
	 */
	private static class CodeBaseTagLinkExtractor implements TagLinkExtractor {
		private Tag tag_;

		/**
		 * Constructor
		 * @param tag {@link Tag} object corresponding to this html tag.
		 */
		public CodeBaseTagLinkExtractor(Tag tag) {
			this.tag_ = tag;
		}

		@Override
		public void extractLink(ArchivalUnit au, Callback cb) {
			cb.foundLink(this.tag_.getAttribute("codebase"));
		}
	}

	/**
	 * Implementation of {@link TagLinkExtractor} interface for html tags that contain a 'background' attribute.
	 * 
	 * @author nvibhor
	 *
	 */
	private static class BackgroundTagLinkExtractor implements TagLinkExtractor {
		private Tag tag_;

		/**
		 * Constructor
		 * @param tag {@link Tag} object corresponding to this html tag.
		 */
		public BackgroundTagLinkExtractor(Tag tag) {
			this.tag_ = tag;
		}

		@Override
		public void extractLink(ArchivalUnit au, Callback cb) {
			cb.foundLink(this.tag_.getAttribute("background"));
		}
	}

	/**
	 * A factory of {@link TagLinkExtractor} objects.
	 * @param tag {@link Tag} object for which {@link TagLinkExtractor} is required.
	 * @return {@link TagLinkExtractor} object.
	 */
	private static TagLinkExtractor getTagLinkExtractor(Tag tag) {
		String tagName = tag.getTagName();
		if ("a".equalsIgnoreCase(tagName)) {
			return new HrefTagLinkExtractor(tag);
		}

		if ("area".equalsIgnoreCase(tagName)) {
			return new HrefTagLinkExtractor(tag);
		}

		if ("link".equalsIgnoreCase(tagName)) {
			return new HrefTagLinkExtractor(tag);
		}

		if ("script".equalsIgnoreCase(tagName)) {
			return new SrcTagLinkExtractor(tag);
		}

		if ("img".equalsIgnoreCase(tagName)) {
			return new SrcTagLinkExtractor(tag);
		}

		if ("embed".equalsIgnoreCase(tagName)) {
			return new SrcTagLinkExtractor(tag);
		}

		if ("frame".equalsIgnoreCase(tagName)) {
			return new SrcTagLinkExtractor(tag);
		}

		if ("applet".equalsIgnoreCase(tagName)) {
			return new CodeTagLinkExtractor(tag);
		}

		if ("object".equalsIgnoreCase(tagName)) {
			return new CodeBaseTagLinkExtractor(tag);
		}

		if ("body".equalsIgnoreCase(tagName)) {
			return new BackgroundTagLinkExtractor(tag);
		}

		if ("table".equalsIgnoreCase(tagName)) {
			return new BackgroundTagLinkExtractor(tag);
		}

		if ("tr".equalsIgnoreCase(tagName)) {
			return new BackgroundTagLinkExtractor(tag);
		}

		if ("td".equalsIgnoreCase(tagName)) {
			return new BackgroundTagLinkExtractor(tag);
		}

		if ("th".equalsIgnoreCase(tagName)) {
			return new BackgroundTagLinkExtractor(tag);
		}

		return null;
	}

	public interface FormInputWrapper {
		public String[] getUrlComponents();
	}

	/**
	 * @author mlanken,vibhor, fred all possible links are emitted from
	 *         emitLinks
	 */
	public class FormProcessor {
		private class HiddenFormInput implements FormInputWrapper {
			private Tag tag_;

			public HiddenFormInput(Tag tag) {
				tag_ = tag;
			}

			@Override
			public String[] getUrlComponents() {
				String[] l = new String[1];
				String name = tag_.getAttribute("name");
				if (name == null || name.isEmpty()) {
					return null; // should never reach this, return null for defense
				}
				l[0] = name + '=' + tag_.getAttribute("value");
				return l;
			}
		}

		private class RadioFormInput implements FormInputWrapper {
			// Assumed to be all input type=radio of same name.
			Vector<InputTag> inputs_;
			String name_;

			public RadioFormInput() {
				inputs_ = new Vector<InputTag>();
				name_ = null;
			}

			public void add(Tag tag) {
				String tagName = tag.getAttribute("name");
				if (name_ == null) {
					name_ = tagName;
					if (name_.isEmpty()) {
                        // shouldn't ever reach this
						logger.warning("Radio button with no name. Aborting");
						// TODO: Throw exception
						return;
					}
				}
				if (!tagName.equalsIgnoreCase(name_)) {
					// should never reach this
					logger.error("Radio button for different group. Aborting");
					// TODO: Figure out if we should throw an exception.
					return;
				}
				
				if (!(tag instanceof InputTag) || !tag.getAttribute("type").equalsIgnoreCase("radio")) {
					// should never reach this
					logger.error("Not a radio button. Aborting");
					// TODO: Figure out if we should throw an exception.
					return;
				}

				inputs_.add((InputTag) tag);
			}

			@Override
			public String[] getUrlComponents() {
				if (name_ == null || name_.isEmpty()) {
					// should never reach this
					logger.error("Not a radio button. Aborting");
					// TODO: Figure out if we should throw an exception.
					return null;
				}

				String[] l = new String[inputs_.size()];
				int i = 0;
				// Like single select, radio allows ONLY one value at a time
				// (unlike multi-select or checkbox).
				for (InputTag in : inputs_) {
					l[i++] = name_ + '=' + in.getAttribute("value");
				}
				return l;
			}
		}

		private class CheckboxFormInput implements FormInputWrapper {
			// Assumed to be all input type=radio of same name.
			private Tag tag_;

			public CheckboxFormInput(Tag tag) {
				tag_ = tag;
			}

			@Override
			public String[] getUrlComponents() {
				String name = tag_.getAttribute("name");
				if (name == null || name.isEmpty()) return null; // shouldn't ever reach this, defensive
				// Only 2 possible values on/off (value sent or empty)
				String[] l = new String[2];
				String value = tag_.getAttribute("value");
				if (value == null || value.isEmpty())
					value = "on";
				l[0] = name + '=' + value;
				l[1] = name + '=';
				return l;
			}
		}

		private class SingleSelectFormInput implements FormInputWrapper {
			private SelectTag selectTag_;

			public SingleSelectFormInput(SelectTag tag) {
				selectTag_ = tag;
			}

			@Override
			public String[] getUrlComponents() {
				String l[] = new String[selectTag_.getOptionTags().length];
				String name = selectTag_.getAttribute("name");
				if (name == null || name.isEmpty()) {
					// shouldn't ever reach this, defensive
					return null;
				}
				OptionTag[] options = selectTag_.getOptionTags();
				int i = 0;
				for (OptionTag option : options) {
					l[i++] = name + '=' + option.getAttribute("value");
				}
				return l;
			}
		}

		private FormTag formTag_;
		Vector<FormInputWrapper> orderedTags_;
		Map<String, RadioFormInput> nameToRadioInput_;
		private boolean isSubmitSeen_;

		public FormProcessor(FormTag formTag) {
			formTag_ = formTag;
			orderedTags_ = new Vector<HtmlParserLinkExtractor.FormInputWrapper>();
			nameToRadioInput_ = new HashMap<String, HtmlParserLinkExtractor.FormProcessor.RadioFormInput>();
			isSubmitSeen_ = false;
		}

		public void submitSeen() {
			isSubmitSeen_ = true;
		}

		public void addTag(Tag tag) {
			if ("submit".equalsIgnoreCase(tag.getAttribute("type"))) {
				// HACK(vibhor): If the submit button has a value, browser will send its value in a POST form.
				orderedTags_.add(new HiddenFormInput(tag));
				submitSeen();
				return;
			}
			String name = tag.getAttribute("name");
			if (name == null || name.isEmpty()) {
				logger.warning("Form input tag with no name found. Skipping");
				return;
			}

			if (tag instanceof SelectTag) {
				orderedTags_.add(new SingleSelectFormInput((SelectTag) tag));
			} else if (tag instanceof InputTag) {
				String type = tag.getAttribute("type");
				if (type.equalsIgnoreCase("hidden")) {
					orderedTags_.add(new HiddenFormInput(tag));
				} else if (type.equalsIgnoreCase("checkbox")) {
					orderedTags_.add(new CheckboxFormInput(tag));
				} else if (type.equalsIgnoreCase("radio")) {
					RadioFormInput in = nameToRadioInput_.get(name);
					if (in != null) {
						in.add(tag);
						return;
					}
					in = new RadioFormInput();
					nameToRadioInput_.put(name, in);
					in.add(tag);
					orderedTags_.add(in);
				}
			}
		}

		public void emitLinks(ArchivalUnit au, Callback cb) {
			// Do not extract links if submit button is not seen in the form.
			if (!isSubmitSeen_)
				return;

			// Get the absolute base url from action attribute.
			String baseUrl = formTag_.extractFormLocn();
			boolean isFirstArgSeen = false;

			Vector<String> links = new Vector<String>();
			Vector<String> newLinks = null;
			links.add(baseUrl);
			for (FormInputWrapper tag : orderedTags_) {
				String[] urlComponents = tag.getUrlComponents();
				if (urlComponents == null)
					continue;
				if (urlComponents.length <= 0)
					continue;
				newLinks = new Vector<String>();
				for (String url : links) {
					for (String component : urlComponents) {
						newLinks.add(url + (isFirstArgSeen ? '&' : '?')
								+ component);
					}
				}
				if (newLinks != null && newLinks.size() >= links.size()) {
					links = newLinks;
				}
				isFirstArgSeen = true;
			}

			// TODO(fkautz): Instead of using a custom normalizer, investigate and use PluginManager to normalize the form urls. This
			// way we can share the logic between crawler and proxyhandler. (We do a similar normalization in ProxyHandler.java)
			// ***NOTE: We only need to use a normalizer if the task to use proxy request header fails.***
			FormUrlNormalizer normalizer = new FormUrlNormalizer();
			boolean isPost = formTag_.getFormMethod().equalsIgnoreCase("post");
			for (String link : links) {
				if (isPost)
					try {
						link = normalizer.normalizeUrl(link, au);
					} catch (PluginException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				cb.foundLink(link);
			}
		}
	}

	public class FormUrlNormalizer implements UrlNormalizer {
		// sort form parameters alphabetically
		public String normalizeUrl(String url, ArchivalUnit au)
				throws PluginException {
			if (url == null) {
				return null;
			}

			Vector<String> keyValuePairs = new Vector<String>();
			// only process form urls that look like blah?key=value or
			// blah?k=v&j=w
			if (StringUtils.indexOf(url, "?") == -1
					|| StringUtils.indexOf(url, "?") == 0) {
				return url;
			}
			String prefix = StringUtils.substringBefore(url, "?");
			String rest = StringUtils.substringAfter(url, "?");

			while (rest != null && rest.length() > 0) {
				// disabled until needed. This level of parsing is only needed
				// if multiple inputs with the same names contain different
				// values and their ordering needs to be preserved
				// if (StringUtils.indexOf(rest,"=") == -1 ) { return url; }
				// //no key/values or malformed

				if (StringUtils.indexOf(rest, "&") != -1) {
					keyValuePairs.add(StringUtils.substringBefore(rest, "&"));
					rest = StringUtils.substringAfter(rest, "&");
				} else {
					// last value
					keyValuePairs.add(rest);
					rest = "";
				}
			}
			Collections.sort(keyValuePairs);
			StringBuffer newurl = new StringBuffer(prefix + "?"); // quadratic
																	// time if
																	// we use
																	// string
			while (keyValuePairs.size() > 0) {
				newurl.append(keyValuePairs.get(0));
				if (keyValuePairs.size() > 1) {
					newurl.append("&");
				}
				keyValuePairs.remove(0);
			}

			return newurl.toString();
		}

	}

	
	/**
	 * A custom NodeVisitor implementation that provides the support for link extraction from the current document.
	 * An instance of this class is passed to {@link Parser#visitAllNodesWith} which invokes visitTag & visitEngTag for each tag in the document tree.
	 * For each tag, a corresponding {@link TagLinkExtractor} object is used to emit links or in case of forms, a {@link FormProcessor} is used.
	 * 
	 * @author nvibhor
	 *
	 */
	private class LinkExtractorNodeVisitor extends NodeVisitor {
		protected static final int MAX_LOCKSS_URL_LENGTH = 255;
		private Callback cb_;
		private ArchivalUnit au_;
		private String srcUrl_;
		private String encoding_;
		private boolean malformedBaseUrl_;
		private boolean inScriptMode_;
		private boolean inFormMode_;
		private boolean normalizeFormUrls_;
		private Callback emit_;
		private FormUrlNormalizer normalizer_;
		private FormProcessor formProcessor_;

		/**
		 * Constructor
		 * @param au Current archival unit to which this html document belongs.
		 * @param srcUrl The url of this html document.
		 * @param cb A callback to record extracted links.
		 * @param encoding Encoding needed to read this html document off input stream. 
		 */
		public LinkExtractorNodeVisitor(ArchivalUnit au, String srcUrl,
				Callback cb, String encoding) {
			cb_ = cb;
			au_ = au;
			srcUrl_ = srcUrl;
			encoding_ = encoding;
			malformedBaseUrl_ = false;
			inScriptMode_ = false;
			normalizeFormUrls_ = false; // TODO:this should read the value from
										// the AU
			normalizer_ = new FormUrlNormalizer();
			formProcessor_ = null;
			
			// TODO: Refactor this custom callback to its own class for better readability.
			emit_ = new LinkExtractor.Callback() {
				
				@Override
				public void foundLink(String url) {
					if (url != null) {
						logger.debug3("Found link (before custom callback):" + url);
						try {
							if (malformedBaseUrl_) {
								if (!UrlUtil.isAbsoluteUrl(url)) {
									return;
								}
							} else {
								url = resolveUri(new URL(srcUrl_), url);
								if (url == null) return;
							}
							logger.debug3("Found link (custom callback) after resolver:" + url);
							// check length
							if (StringUtils.lastIndexOf(url, "/") != -1) {
								int filename_length = url.length()
										- StringUtils.lastIndexOf(url, "/") - 1;
								if (filename_length > MAX_LOCKSS_URL_LENGTH) {
									return;
								}
							}
							// sort form parameters if enabled
							if (normalizeFormUrls_) {
								url = normalizer_.normalizeUrl(url, au_);
							}
							logger.debug3("Found link (custom callback) after normalizer:" + url);							
							// emit the processed url
							cb_.foundLink(url);
						} catch (MalformedURLException e) {
							//if the link is malformed, we can safely ignore it
						} catch (PluginException e) {
							//If a PluginException results,  it can be safely ignored
						}
					}
				}
			};

		}

		/**
		 * Resolves a url relative to given base url and returns an absolute url. Also does some minor trasnformation (such as escaping).
		 * Derived from {@link GoslingHtmlLinkExtractor#resolveUri(URL, String)}
		 * @see UrlUtil#resolveUri(URL, String)
		 * 
		 * @param base The base url
		 * @param relative Url that needs to be resolved
		 * @return The absolute url.
		 * @throws MalformedURLException
		 */
		protected String resolveUri(URL base, String relative)
				throws MalformedURLException {
			String baseProto = null;
			if (base != null) {
				baseProto = base.getProtocol();
			}
			if ("javascript".equalsIgnoreCase(baseProto) || relative != null
					&& StringUtil.startsWithIgnoreCase(relative, "javascript:")) {
				return null;
			}
			if ("mailto".equalsIgnoreCase(baseProto) || relative != null
					&& StringUtil.startsWithIgnoreCase(relative, "mailto:")) {
				return null;
			}
			return UrlUtil.resolveUri(base, relative);
		}

		@Override
		public void visitEndTag(Tag tag) {
			// If end script tag visited and we were in script mode, exit script mode.
			if ("script".equalsIgnoreCase(tag.getTagName())
					&& tag.getStartPosition() != tag.getEndPosition()) {
				inScriptMode_ = false;
			}

			// If end form tag visited, we must have encountered all the form inputs that will be needed to generate form links.
			// Exit form mode, emit all form links and finish form processing for this form.
			if ("form".equalsIgnoreCase(tag.getTagName())
					&& tag.getStartPosition() != tag.getEndPosition()) {
				inFormMode_ = false;
				if (formProcessor_ == null) {
					logger.error("Null FormProcessor while trying to emit links.");
					// Possibly throw exception to abort completely from this
					// link extractor.
					return;
				}
				formProcessor_.emitLinks(au_, emit_);
				// Cleanup form processor
				formProcessor_ = null;
			}
		}

		@Override
		public void visitTag(Tag tag) {
			if (tag instanceof FormTag) {
				if (inFormMode_) {
					logger.error("Invalid HTML: Form inside a form");
					// Recover from this error by simply ignoring any child forms (popular browser behavior)
					return;
				}
				// Visited a form tag, enter form mode and start form processing logic.
				inFormMode_ = true;
				if (formProcessor_ != null) {
					// Possibly throw exception to abort completely from this
					// link extractor.
					logger.error("Non-null FormProcessor found. "
							+ "It is likely that a previous form processing was not finished. "
							+ "Report the error to dev team (vibhor)");
				}
				// Initialize form processor
				formProcessor_ = new FormProcessor((FormTag) tag);
			}

			// An input/select tag inside a form mode should be handled by form processor.
			if (inFormMode_
					&& (tag instanceof InputTag || tag instanceof SelectTag)) {
				formProcessor_.addTag(tag);
				return;
			}

			// We currently skip processing script tags.
			if (inScriptMode_)
				return;
			
			// The following code for style tag processing is heavily derived from GoslingHTmlLinkExtractor.
			if ("style".equalsIgnoreCase(tag.getTagName())) {
				StyleTag styleTag = (StyleTag) tag;
				InputStream in = new ReaderInputStream(new StringReader(
						styleTag.getStyleCode()), encoding_);
				try {
					au_.getLinkExtractor("text/css").extractUrls(au_, in,
							encoding_, srcUrl_, cb_);
					return;
				} catch (IOException e) {
				} catch (PluginException e) {
				}
			}

			// Visited a base tag, update the page url. All the relative links that follow base tag will need to be resolved to the new page url.
			if ("base".equalsIgnoreCase(tag.getTagName())) {
				String newBase = tag.getAttribute("href");
				if (newBase != null && !newBase.isEmpty()) {
					malformedBaseUrl_ = UrlUtil.isMalformedUrl(newBase);
					if (!malformedBaseUrl_ && UrlUtil.isAbsoluteUrl(newBase)) {
						srcUrl_ = newBase;
					}
				}
				return;
			}

			// Visited a script tag, enter script mode.
			this.inScriptMode_ = "script".equalsIgnoreCase(tag.getTagName());

			// For everything else, we fallback to a TagLinkExtractor instance if available for this tag.
			TagLinkExtractor tle = getTagLinkExtractor(tag);
			if (tle != null) {
				tle.extractLink(au_, emit_);
			}

		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.lockss.extractor.LinkExtractor#extractUrls(org.lockss.plugin.ArchivalUnit
	 * , java.io.InputStream, java.lang.String, java.lang.String,
	 * org.lockss.extractor.LinkExtractor.Callback)
	 */
	@Override
	public void extractUrls(ArchivalUnit au, InputStream in, String encoding,
			String srcUrl, Callback cb) throws IOException {

		if (in == null) {
			throw new IllegalArgumentException("Called with null InputStream");
		} else if (srcUrl == null) {
			throw new IllegalArgumentException("Called with null srcUrl");
		} else if (cb == null) {
			throw new IllegalArgumentException("Called with null callback");
		}

		Callback current_cb = cb;
		boolean statistics_enabled =  logger.isDebug2();
		// Make a copy of input stream to be used with a fallback extractor (see
		// comment before Gosling).
		StringWriter w = new StringWriter();
		IOUtils.copy(in, w);
		// Restore the input stream consumed by StringWriter.
		in = new ReaderInputStream(new StringReader(w.toString()), encoding);
		// Make a copy.
		InputStream inCopy = new ReaderInputStream(new StringReader(
				w.toString()), encoding);


	    Parser p = new Parser(new Lexer(new Page(in, encoding)));
	    LinkExtractorStatsCallback hple_cb = new LinkExtractorStatsCallback(cb);
	    if (statistics_enabled) {
	    	current_cb = hple_cb;
		}
		try {
			p.visitAllNodesWith(new LinkExtractorNodeVisitor(au, srcUrl, current_cb,
					encoding));
		} catch (ParserException e) {
			logger.warning("Unable to parse url: " + srcUrl,e);
		} catch (RuntimeException e) {
			logger.warning("Encountered a runtime exception, continuing link extraction with Gosling",e);
		}
		
		// For legacy reasons, we want to ensure link extraction using a more
		// permissive Gosling parser.
		//
		// TODO(vibhor): Instead of copying the IOStream, we should be able to specify pass multiple
		// link extractors in the plugin (for same mime type) and reopen stream for each.
	    LinkExtractorStatsCallback gosling_cb = new LinkExtractorStatsCallback(cb);
	    if (statistics_enabled) {
			current_cb = gosling_cb;
		}
	    
		new GoslingHtmlLinkExtractor().extractUrls(au, inCopy, encoding,
				srcUrl, current_cb);
		if (statistics_enabled) {
			Set<String> hple_urls = hple_cb.GetUrls();
			Set<String> gosling_urls = gosling_cb.GetUrls();
			Set<String> common_urls = new HashSet<String>(gosling_urls);
			common_urls.retainAll(hple_urls);

			int common_url_count = common_urls.size();
			int hple_url_count = hple_urls.size() - common_url_count;
			int gosling_url_count = gosling_urls.size() - common_url_count;
			logger.debug2("Stats AU: " + au.toString() + " Common URLs: " + common_url_count + " HPLE only: "+ hple_url_count + " Gosling only: " +gosling_url_count );

			if (logger.isDebug3()) {
				if (hple_url_count > 0) {
					Set<String> hple_only_urls = new HashSet<String>(hple_urls);
				    hple_only_urls.removeAll(common_urls);
				    logger.debug3("HPLE only urls: " + hple_only_urls.toString());
				}
				if  (gosling_url_count > 0 ) {
					Set<String> gosling_only_urls = new HashSet<String>(gosling_urls);
					gosling_only_urls.removeAll(common_urls);
					logger.debug3("Gosling only urls: " + gosling_only_urls.toString());
				}
			}
		}
}

//LinkExtractorStatsCallback wraps an existing Callback and stores all urls retrieved
	private class LinkExtractorStatsCallback implements LinkExtractor.Callback {
		private Set<String> urls_found_ = new HashSet<String>();
		private Callback cb_;
		public LinkExtractorStatsCallback(Callback cb) {
			cb_=cb;
		}

		public Set<String> GetUrls() {
			return urls_found_;
		}
		
	    public void foundLink(String url) {
	    urls_found_.add(url);
	    cb_.foundLink(url);
	    }
	  }

	
	public static class Factory implements LinkExtractorFactory {
		public LinkExtractor createLinkExtractor(String mimeType) {
			return new HtmlParserLinkExtractor();
		}
	}

}
