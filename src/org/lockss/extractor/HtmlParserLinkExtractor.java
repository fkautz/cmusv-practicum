/**
 * 
 */
package org.lockss.extractor;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;


import org.htmlparser.Parser;
import org.htmlparser.Tag;
import org.htmlparser.lexer.Lexer;
import org.htmlparser.lexer.Page;
import org.htmlparser.tags.StyleTag;
import org.htmlparser.util.ParserException;
import org.htmlparser.visitors.NodeVisitor;

import org.lockss.daemon.PluginException;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.util.ReaderInputStream;
import org.lockss.util.StringUtil;
import org.lockss.util.UrlUtil;

/**
 * @author vibhor
 *
 */
public class HtmlParserLinkExtractor implements LinkExtractor {
	private interface TagLinkExtractor {
		public String extractLink();
	}
	
	private static class HrefTagLinkExtractor implements TagLinkExtractor {
		private Tag tag_;
		public HrefTagLinkExtractor(Tag tag) {
			this.tag_ = tag;
		}
		
		@Override
		public String extractLink() {
			return this.tag_.getAttribute("href");
		}
	}
		
	private static class SrcTagLinkExtractor implements TagLinkExtractor {
		private Tag tag_;
		public SrcTagLinkExtractor(Tag tag) {
			this.tag_ = tag;
		}
		
		@Override
		public String extractLink() {
			return this.tag_.getAttribute("src");
		}
	}
	
	private static class CodeTagLinkExtractor implements TagLinkExtractor {
		private Tag tag_;
		public CodeTagLinkExtractor(Tag tag) {
			this.tag_ = tag;
		}
		
		@Override
		public String extractLink() {
			return this.tag_.getAttribute("code");
		}
	}
	
	private static class CodeBaseTagLinkExtractor implements TagLinkExtractor {
		private Tag tag_;
		public CodeBaseTagLinkExtractor(Tag tag) {
			this.tag_ = tag;
		}
		
		@Override
		public String extractLink() {
			return this.tag_.getAttribute("codebase");
		}
	}
	
	private static class BackgroundTagLinkExtractor implements TagLinkExtractor {
		private Tag tag_;
		public BackgroundTagLinkExtractor(Tag tag) {
			this.tag_ = tag;
		}
		
		@Override
		public String extractLink() {
			return this.tag_.getAttribute("background");
		}
	}
	
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
	
	private class LinkExtractorNodeVisitor extends NodeVisitor {
		private Callback cb_;
		private ArchivalUnit au_;
		private String srcUrl_;
		private String encoding_;
		private boolean malformedBaseUrl_;
		private boolean inScriptMode_;
		
		public LinkExtractorNodeVisitor(ArchivalUnit au, String srcUrl, Callback cb, String encoding) {
			cb_ = cb;
			au_ = au;
			srcUrl_ = srcUrl;
			encoding_ = encoding;
			malformedBaseUrl_ = false;
			inScriptMode_ = false;
		}
		
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
		
		public void visitEndTag(Tag tag) {
			if ("script".equalsIgnoreCase(tag.getTagName())
					&& tag.getStartPosition() != tag.getEndPosition()) {
				inScriptMode_ = false;
			}			
		}
		
		public void visitTag(Tag tag) {
			if (inScriptMode_) return;
			if ("style".equalsIgnoreCase(tag.getTagName())) {
				StyleTag styleTag = (StyleTag)tag;
				InputStream in = new ReaderInputStream(new StringReader(styleTag.getStyleCode()), encoding_);
				try {
					au_.getLinkExtractor("text/css").extractUrls(au_, in, encoding_, srcUrl_, cb_);
					return;
				} catch (IOException e) {
				} catch (PluginException e) {
				}
			}
			
			if ("base".equalsIgnoreCase(tag.getTagName())) {
				String newBase = tag.getAttribute("href");
				  if (newBase != null && !"".equals(newBase)) {
					malformedBaseUrl_ = UrlUtil.isMalformedUrl(newBase);
					if (!malformedBaseUrl_ && UrlUtil.isAbsoluteUrl(newBase)) {
						srcUrl_ = newBase;
					}
				  }
				  return;
			}
			
			this.inScriptMode_ = "script".equalsIgnoreCase(tag.getTagName());
			
			String link;
			TagLinkExtractor tle = getTagLinkExtractor(tag);
			if (tle != null) {
				link = tle.extractLink();
				if (link != null) {
					try {
						if (malformedBaseUrl_) {
							if (!UrlUtil.isAbsoluteUrl(link)) {
							  return;
							}
						    cb_.foundLink(link);
						} else {
						 cb_.foundLink(resolveUri(new URL(srcUrl_), link));
						}
					} catch (MalformedURLException e) {
					}
				}
			}
			
		}
	}

	/* (non-Javadoc)
	 * @see org.lockss.extractor.LinkExtractor#extractUrls(org.lockss.plugin.ArchivalUnit, java.io.InputStream, java.lang.String, java.lang.String, org.lockss.extractor.LinkExtractor.Callback)
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

		Parser p = new Parser(new Lexer(new Page(in, encoding)));
		try {
			p.visitAllNodesWith(new LinkExtractorNodeVisitor(au, srcUrl, cb, encoding));
		} catch (ParserException e) {
		}
	}

}
