/**
 * 
 */
package org.lockss.extractor;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Iterator;
import java.util.Vector;

import org.htmlparser.Parser;
import org.htmlparser.Tag;
import org.htmlparser.lexer.Lexer;
import org.htmlparser.lexer.Page;
import org.htmlparser.nodes.TagNode;
import org.htmlparser.tags.StyleTag;
import org.htmlparser.util.ParserException;
import org.htmlparser.util.Translate;
import org.htmlparser.visitors.NodeVisitor;
import org.htmlparser.tags.FormTag;
import org.htmlparser.tags.InputTag;
import org.htmlparser.Node;
import org.htmlparser.util.NodeList;


import org.lockss.daemon.PluginException;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.util.ReaderInputStream;
import org.lockss.util.StringUtil;
import org.lockss.util.UrlUtil;

/**
 * @author vibhor
 * TODO: blah 
 * BUG: blah
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
			String target = this.tag_.getAttribute("href");
			if (target != null ) { target = Translate.decode(target); }
			return target;
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
	
	/**
	 * TODO: work in progress 
	 * @author mlanken
	 * @param tag
	 * @return all possible links
	 */
	private static class FormTagLinkExtractor implements TagLinkExtractor {
		private FormTag tag_;
		public FormTagLinkExtractor(Tag tag) {
			this.tag_ = (FormTag) tag;
		}
				
		//TODO: change to use callback and allow multiple links to be extracted from a single tag
		//TODO: check AU to see if this form should be ignored?
		//Current behavior is to ignore the method parameter?
		//TODO: post as post			if (!"GET".equalsIgnoreCase(this.tag_.getFormMethod())) { return null;} //ignore POST forms for now
		//TODO: do we need to support button submit tags? <BUTTON name="submit" value="submit" type="submit">
		//	    Send<IMG src="/icons/wow.gif" alt="wow"></BUTTON>

		@Override
		public String extractLink() {
			boolean debug = true;
			if (debug) System.out.println("FTLE.extractLink: name - " + this.tag_.getFormName());
			if (debug) System.out.println("FTLE.extractLink: action - " + this.tag_.getAttribute("action"));
			if (debug) System.out.println("FTLE.extractLink: method - " + this.tag_.getFormMethod());

			Node[] inputs = this.tag_.getFormInputs().toNodeArray();

			Vector<String> links = new Vector<String>(); 
			//TODO: check is action is specified, if not, return null
			links.add(this.tag_.getAttribute("action") + "?"); //TODO: do we care if this is null? an empty string should be ok

			boolean submit_tag_found = false;
			String submit_tag_value = null;
			
			for (Node input : inputs) {
				InputTag i = (InputTag) input;
				System.out.println(input.toString());				
				String type_of_input = i.getAttribute("type");
				String name = i.getAttribute("name");
				String value = i.getAttribute("value");
				if (type_of_input==null) {
					continue;
				}

				if ("submit".equalsIgnoreCase(type_of_input)) {
					submit_tag_value = i.getAttribute("value"); //TODO: do we care if this is null?
					//TODO: do we care if name is null?
					submit_tag_found = true;
					continue;
				}
				//ignore this tag if name or value is null since we won't be able to create a url. TODO: is this correct?
				if (name==null || value==null) {
					continue;
				}
				if (debug) System.out.println("name:" + name + ", type:" + type_of_input + ", value:" + value);
				if ("hidden".equalsIgnoreCase(type_of_input)) {
					Vector<String> new_links = new Vector<String>();						
					for (String link : links) {
						new_links.add(link + name + "=" + value + "&");						
					}
					links = new_links;
				}
				else if ("select".equalsIgnoreCase(type_of_input)) {
					//TODO:  add support for select lists
					//<select>
					//  <option value="volvo">Volvo</option>
					//  <option value="saab">Saab</option>
					//  <option value="mercedes">Mercedes</option>
					//  <option value="audi">Audi</option>
					//</select>
					
				 continue;
				}
				else {
					//ignore null, reset, other tags not covered above
					continue;
				}
			
			}

			//emit the link(s)
			if (!submit_tag_found) return null;

			for (String link :  links) { 
				if (link.endsWith("&")) { link = link.substring(0, link.length()-1);}
				if (link.endsWith("?")) { link = link.substring(0, link.length()-1);}//TODO: Is this correct?
				if (debug) { System.out.println("returning link: " + link); }
				return link;
			}

			return null;
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

		if ("form".equalsIgnoreCase(tagName)) {
			return new FormTagLinkExtractor(tag);
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

//		private Callback emit_;
		
		public LinkExtractorNodeVisitor(ArchivalUnit au, String srcUrl, Callback cb, String encoding) {
			cb_ = cb;
//			emit_ = emitUrl(cb);
			au_ = au;
			srcUrl_ = srcUrl;
			encoding_ = encoding;
			malformedBaseUrl_ = false;
			inScriptMode_ = false;
		}
//TODO - right now ExtractLink can only return a single URL, but a tag can produce multiple links,  especially forms
//		private Callback emitUrl(Callback cb);

		
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
