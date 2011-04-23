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
import org.lockss.plugin.FormUrlInput;
import org.lockss.plugin.FormUrlNormalizer;
import org.lockss.plugin.LinkExtractorStatisticsManager;
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
	private static final int MAX_NUM_FORM_URLS = 1000000;
	private int max_form_urls_;

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
		public FormUrlInput[] getUrlComponents();
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
			public FormUrlInput[] getUrlComponents() {
				FormUrlInput[] l = new FormUrlInput[1];
				String name = tag_.getAttribute("name");
				if (name == null || name.isEmpty()) {
					return null; // should never reach this, return null for defense
				}
				l[0] = new FormUrlInput(name,  tag_.getAttribute("value"));
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
			public FormUrlInput[] getUrlComponents() {
				if (name_ == null || name_.isEmpty()) {
					// should never reach this
					logger.error("Not a radio button. Aborting");
					// TODO: Figure out if we should throw an exception.
					return null;
				}

				FormUrlInput[] l = new FormUrlInput[inputs_.size()];
				int i = 0;
				// Like single select, radio allows ONLY one value at a time
				// (unlike multi-select or checkbox).
				for (InputTag in : inputs_) {
					l[i++] = new FormUrlInput(name_,in.getAttribute("value"));
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
			public FormUrlInput[] getUrlComponents() {
				String name = tag_.getAttribute("name");
				if (name == null || name.isEmpty()) return null; // shouldn't ever reach this, defensive
				// Only 2 possible values on/off (value sent or empty)
				FormUrlInput[] l = new FormUrlInput[2];
				String value = tag_.getAttribute("value");
				if (value == null || value.isEmpty())
					value = "on";
				l[0] = new FormUrlInput(name, value);
				l[1] = new FormUrlInput(name,"");
				return l;
			}
		}

		private class SingleSelectFormInput implements FormInputWrapper {
			private SelectTag selectTag_;

			public SingleSelectFormInput(SelectTag tag) {
				selectTag_ = tag;
			}

			@Override
			public FormUrlInput[] getUrlComponents() {
				FormUrlInput l[] = new FormUrlInput[selectTag_.getOptionTags().length];
				String name = selectTag_.getAttribute("name");
				if (name == null || name.isEmpty()) {
					// shouldn't ever reach this, defensive
					return null;
				}
				OptionTag[] options = selectTag_.getOptionTags();
				int i = 0;
				for (OptionTag option : options) {
					l[i++] = new FormUrlInput(name, option.getAttribute("value"));
				}
				return l;
			}
		}

		private FormTag formTag_;
		Vector<FormInputWrapper> orderedTags_;
		Map<String, RadioFormInput> nameToRadioInput_;
		private boolean isSubmitSeen_;
		private int max_form_urls_;

		public FormProcessor(FormTag formTag, int max_form_urls) {
			formTag_ = formTag;
			orderedTags_ = new Vector<HtmlParserLinkExtractor.FormInputWrapper>();
			nameToRadioInput_ = new HashMap<String, HtmlParserLinkExtractor.FormProcessor.RadioFormInput>();
			isSubmitSeen_ = false;
			max_form_urls_ = max_form_urls;
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

			FormUrlIterator iter = new FormUrlIterator(orderedTags_, baseUrl, max_form_urls_);
			iter.initialize();

			// TODO(fkautz): Instead of using a custom normalizer, investigate and use PluginManager to normalize the form urls. This
			// way we can share the logic between crawler and proxyhandler. (We do a similar normalization in ProxyHandler.java)
			// ***NOTE: We only need to use a normalizer if the task to use proxy request header fails.***
			FormUrlNormalizer normalizer = new FormUrlNormalizer(true,null);
			boolean isPost = formTag_.getFormMethod().equalsIgnoreCase("post");
			while (iter.hasMore()) {
				String link = iter.nextUrl();
				if (isPost) {
					try {
						link = normalizer.normalizeUrl(link, au);
					} catch (PluginException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				cb.foundLink(link);
			}
		}
	}
	
	public class FormUrlIterator {
		private Vector<FormInputWrapper> tags_;
		private Vector<FormUrlInput[]> components_;
		private int[] currentPositions_;
		private int totalUrls_;
		private int numUrlSeen_;
		private String baseUrl_;
		private int max_form_urls_;
		
		public FormUrlIterator(Vector<FormInputWrapper> tags, String baseUrl, int max_form_urls) {
			this.tags_ = tags;
			this.components_ = new Vector<FormUrlInput[]>();
			this.totalUrls_ = 1;
			this.currentPositions_ = null;
			this.numUrlSeen_ = 0;
			this.baseUrl_ = baseUrl;
			this.max_form_urls_ = max_form_urls;
		}

		public void initialize() {
			for (FormInputWrapper tag : this.tags_) {
				FormUrlInput[] urlComponents = tag.getUrlComponents();
				if (urlComponents != null && urlComponents.length > 0) {
					if (this.max_form_urls_ > this.totalUrls_ * urlComponents.length) {
						this.totalUrls_ *= urlComponents.length;
					} else {
						this.totalUrls_ = this.max_form_urls_;
					}
					this.components_.add(tag.getUrlComponents());
				}
			}
			this.currentPositions_ = new int[this.components_.size()];
			for (int i = 0; i < this.currentPositions_.length; ++i) {
				this.currentPositions_[i] = 0;
			}
			
			if (this.totalUrls_ > this.max_form_urls_) this.totalUrls_ = this.max_form_urls_;
		}

		public boolean hasMore() {
			return this.numUrlSeen_ < this.totalUrls_;
		}

		private boolean isLastComponent(int i) {
			return (this.currentPositions_[i] + 1) >= this.components_.get(i).length;
		}
		
		private void incrementPositions_() {
			if (!hasMore()) return;
			
			this.numUrlSeen_++;
			
			// If we have 3 select-option values, 1 checkbox and 2 radiobuttons, we can have 3 X 2 X 2 combinations:
			// This is how the iteration works:
			// <0,0,0> <0,0,1> <0,1,0> <0,1, 1>....<2,1,1>			
			for (int i = 0; i < this.currentPositions_.length; ++i) {
				if (isLastComponent(i)) {
					if (i + 1 == this.currentPositions_.length) break;
					this.currentPositions_[i] = 0;
				} else {
					this.currentPositions_[i]++;
					break;
				}
			}
		}
		
		public String nextUrl() {
			if (!hasMore()) return null;
			
			boolean isFirstArgSeen = false;
			String url = this.baseUrl_;
			int i = 0;
			for (FormUrlInput[] components : this.components_) {
				url += (isFirstArgSeen ? '&' : '?') + components[this.currentPositions_[i++]].toString();
				isFirstArgSeen = true;
			}
			incrementPositions_();
			return url;
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
		private int max_form_urls_;

		/**
		 * Constructor
		 * @param au Current archival unit to which this html document belongs.
		 * @param srcUrl The url of this html document.
		 * @param cb A callback to record extracted links.
		 * @param encoding Encoding needed to read this html document off input stream. 
		 */
		public LinkExtractorNodeVisitor(ArchivalUnit au, String srcUrl,
				Callback cb, String encoding, int max_form_urls) {
			cb_ = cb;
			au_ = au;
			srcUrl_ = srcUrl;
			encoding_ = encoding;
			malformedBaseUrl_ = false;
			inScriptMode_ = false;
			normalizeFormUrls_ = false; // TODO:this should read the value from
										// the AU
			max_form_urls_ = max_form_urls;
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
							//previously, a length check was done here
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
					logger.error("Internal inconsistency for formprocessor_");
					logger.error(Thread.currentThread().getStackTrace().toString());
				}
				// Initialize form processor
				formProcessor_ = new FormProcessor((FormTag) tag, max_form_urls_);
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

		//TODO:  change this value to a configuration parameter
//		boolean statistics_enabled =  logger.isDebug2();
		boolean statistics_enabled = false;
		Callback current_cb = cb;
		LinkExtractorStatisticsManager stats = new LinkExtractorStatisticsManager();
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
	    if (statistics_enabled) {
	    	stats.startMeasurement("HtmlParser");
	    	current_cb = stats.wrapCallback(cb,"HtmlParser");
		}
		try {
			p.visitAllNodesWith(new LinkExtractorNodeVisitor(au, srcUrl, current_cb,
					encoding, max_form_urls_));
		} catch (ParserException e) {
			logger.warning("Unable to parse url: " + srcUrl,e);
		} catch (RuntimeException e) {
			e.printStackTrace();
			logger.warning("Encountered a runtime exception, continuing link extraction with Gosling",e);
		}
		
		// For legacy reasons, we want to ensure link extraction using a more
		// permissive Gosling parser.
		//
		// TODO(vibhor): Instead of copying the IOStream, we should be able to specify pass multiple
		// link extractors in the plugin (for same mime type) and reopen stream for each.
	    if (statistics_enabled) {
	    	stats.startMeasurement("Gosling");
			current_cb = stats.wrapCallback(cb,"Gosling");
		}
	    
		new GoslingHtmlLinkExtractor().extractUrls(au, inCopy, encoding,
				srcUrl, current_cb);
		if (statistics_enabled) {
			stats.stopMeasurement();
			stats.compareExtractors("Gosling","HtmlParser", "AU: " + au.toString() + " src URL=" + srcUrl);
		}
}
	
	// For testing
	public HtmlParserLinkExtractor(int max_form_urls) {
		max_form_urls_ = max_form_urls;
	}
	
	public HtmlParserLinkExtractor() {
		max_form_urls_ = MAX_NUM_FORM_URLS;
	}
	
	public static class Factory implements LinkExtractorFactory {
		public LinkExtractor createLinkExtractor(String mimeType) {
			return new HtmlParserLinkExtractor();
		}
	}

}
