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
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Vector;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.htmlparser.Node;
import org.htmlparser.Parser;
import org.htmlparser.Tag;
import org.htmlparser.lexer.Lexer;
import org.htmlparser.lexer.Page;
import org.htmlparser.nodes.TagNode;
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
	private static final Log logger =
		LogFactory.getLog(HtmlParserLinkExtractor.class);

	private interface TagLinkExtractor {
		public void extractLink(ArchivalUnit au,Callback cb); 
	}
	
	private static class HrefTagLinkExtractor implements TagLinkExtractor {
		private Tag tag_;
		public HrefTagLinkExtractor(Tag tag) {
			this.tag_ = tag;
		}
		
		@Override
		public void extractLink(ArchivalUnit au,Callback cb)  {
			String target = this.tag_.getAttribute("href");
			if (target != null ) { target = Translate.decode(target); }
			cb.foundLink(target);
		}
	}
		
	private static class SrcTagLinkExtractor implements TagLinkExtractor {
		private Tag tag_;
		public SrcTagLinkExtractor(Tag tag) {
			this.tag_ = tag;
		}
		
		@Override
		public void extractLink(ArchivalUnit au,Callback cb)  {
			cb.foundLink( this.tag_.getAttribute("src") );
		}
	}
	
	private static class CodeTagLinkExtractor implements TagLinkExtractor {
		private Tag tag_;
		public CodeTagLinkExtractor(Tag tag) {
			this.tag_ = tag;
		}
		
		@Override
		public void extractLink(ArchivalUnit au,Callback cb)  {
			cb.foundLink( this.tag_.getAttribute("code") );
		}
	}
	
	private static class CodeBaseTagLinkExtractor implements TagLinkExtractor {
		private Tag tag_;
		public CodeBaseTagLinkExtractor(Tag tag) {
			this.tag_ = tag;
		}
		
		@Override
		public void extractLink(ArchivalUnit au,Callback cb)  {
			cb.foundLink(this.tag_.getAttribute("codebase"));
		}
	}
	
	private static class BackgroundTagLinkExtractor implements TagLinkExtractor {
		private Tag tag_;
		public BackgroundTagLinkExtractor(Tag tag) {
			this.tag_ = tag;
		}
		
		@Override
		public void extractLink(ArchivalUnit au,Callback cb)  {
			cb.foundLink(this.tag_.getAttribute("background"));
		}
	}
	
	/** 
	 * @author mlanken,vibhor, fred
	 * all possible links are emitted from extractLinks
	 */
	private static class FormTagLinkExtractor implements TagLinkExtractor {
		private FormTag tag_;
		public FormTagLinkExtractor(Tag tag) {
			this.tag_ = (FormTag) tag;
		}
				
		//Note: We think we do not need to check AU to see if this form should be ignored? because the crawl rules should cover it.  It is possible for a site to require special handling to exclude forms.
		
		//TODO: post as post			if (!"GET".equalsIgnoreCase(this.tag_.getFormMethod())) { return null;} //ignore POST forms for now
		//TODO: do we need to support button submit tags? <BUTTON name="submit" value="submit" type="submit">
		//	    Send<IMG src="/icons/wow.gif" alt="wow"></BUTTON>

		@Override
		public void extractLink(ArchivalUnit au,Callback cb) {
			Runtime me = java.lang.Runtime.getRuntime();
			me.traceMethodCalls(true);
			boolean debug = true;
			if (debug) System.out.println("FTLE.extractLink: name - " + this.tag_.getFormName());
			if (debug) System.out.println("FTLE.extractLink: action - " + this.tag_.getAttribute("action"));
			if (debug) System.out.println("FTLE.extractLink: method - " + this.tag_.getFormMethod());

			Node[] inputs = this.tag_.getFormInputs().toNodeArray();

			// find all select
			LinkedList<Node> formComponents = new LinkedList<Node>();
			Collections.addAll(formComponents, inputs);
			Queue<Node> nodeQueue = new LinkedList<Node>();
			if(this.tag_.getChildCount() != 0) {
				Collections.addAll(nodeQueue, this.tag_.getChildren().toNodeArray());
			}
			HashMap<String,List<OptionTag>> selectNameToOptions = new HashMap<String, List<OptionTag>>();
			HashMap<OptionTag,SelectTag> optionToSelectMap = new HashMap<OptionTag, SelectTag>();
			while(!nodeQueue.isEmpty()) {
				Node node = nodeQueue.remove();
				if(node instanceof SelectTag || node instanceof OptionTag) {
					formComponents.add(node);
				}
				if(node.getChildren() != null && node.getChildren().size() > 0) {
					Node[] currentChildren = node.getChildren().toNodeArray();
					LinkedList<Node> currentChildrenList = new LinkedList<Node>();
					Collections.addAll(currentChildrenList, currentChildren);
					nodeQueue.addAll(currentChildrenList);
				}
			}

			Vector<String> links = new Vector<String>(); 
			//TODO: check is action is specified, if not, return null
			links.add(this.tag_.getAttribute("action") + "?"); //TODO: do we care if this is null? an empty string should be a legal value

			boolean submit_tag_found = false;
//			String submit_tag_value = null; //TODO: remove if we decide not to use this
			
			for (Node input : formComponents) {
				TagNode i = (TagNode)input;
				String type_of_input = i.getAttribute("type");
				String name = i.getAttribute("name");
				String value = i.getAttribute("value");
				if(i instanceof InputTag && type_of_input == null) {
					continue;
				}
				if ("submit".equalsIgnoreCase(type_of_input)) {
//					submit_tag_value = i.getAttribute("value"); //TODO: do we care if this is null?
					//TODO: do we care if name is null?
					submit_tag_found = true;
					continue;
				}
				//ignore this tag if name or value is null since we won't be able to create a url. TODO: is this correct?
				if (i instanceof InputTag && (name==null || value==null)) {
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
				else if (i instanceof SelectTag) {
					if(name != null)
						selectNameToOptions.put(name, new LinkedList<OptionTag>());
					//TODO:  add support for select lists
					//<select>
					//  <option value="volvo">Volvo</option>
					//  <option value="saab">Saab</option>
					//  <option value="mercedes">Mercedes</option>
					//  <option value="audi">Audi</option>
					//</select>
					
				}
				else if (i instanceof OptionTag) {
					// find parent
					Node iter = i;
					while(iter != null) {
						if(iter instanceof SelectTag) {
							SelectTag currentSelect = (SelectTag)iter;
							String select_name = currentSelect.getAttribute("name");
							if(select_name != null) {
								selectNameToOptions.get(select_name).add((OptionTag)i);
								optionToSelectMap.put((OptionTag)i, currentSelect);
							}
							break;
						}
						iter = iter.getParent();
					}
				}
				else {
					//ignore null, reset, other tags not covered above
					continue;
				}
			
			}

			//emit the link(s)
			if (!submit_tag_found) return;
			
			for(String key : selectNameToOptions.keySet()) {
				Vector<String> new_links = new Vector<String>();
				List<OptionTag> options = selectNameToOptions.get(key);
				for(OptionTag option : options) {
					SelectTag select = optionToSelectMap.get(option);
					String name = select.getAttribute("name");
					String value = option.getAttribute("value");
					if(name != null && value !=  null) {
						for(String link : links) {
							new_links.add(link + name + "=" + value + "&");	
						}
					}
				}
				if(!new_links.isEmpty())
					links = new_links;
			}

			for (String link :  links) { 
				if (link.endsWith("&")) { link = link.substring(0, link.length()-1);}
				if (link.endsWith("?")) { link = link.substring(0, link.length()-1);}//TODO: Is this correct?
				if (debug) { System.out.println("returning link: " + link); }
				cb.foundLink(link);
			}

			return;
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

	public class FormUrlNormalizer implements UrlNormalizer {
//sort form parameters alphabetically
		public String normalizeUrl(String url,
		                             ArchivalUnit au)
		      throws PluginException {
			if (url==null) { return null;}
			
			Vector<String> keyValuePairs = new Vector<String>(); 
			  //only process form urls that look like blah?key=value or blah?k=v&j=w
			  if (StringUtils.indexOf(url,"?") == -1 ||StringUtils.indexOf(url,"?") == 0) { return url; } 
			  String prefix = StringUtils.substringBefore(url, "?");
			  String rest = StringUtils.substringAfter(url, "?");
			  
			  while (rest != null && rest.length()>0) {
				  //disabled until needed.  This level of parsing is only needed if multiple inputs with the same names contain different values and their ordering needs to be preserved
				  //if (StringUtils.indexOf(rest,"=") == -1 ) { return url; } //no key/values or malformed

				  if (StringUtils.indexOf(rest,"&") != -1 ) {  
					  keyValuePairs.add(StringUtils.substringBefore(rest,"&"));
					  rest=StringUtils.substringAfter(rest, "&");
				  } 
				  else {
					  //last value
					  keyValuePairs.add(rest);
					  rest="";
				  }
			  }
			  Collections.sort(keyValuePairs);
			  StringBuffer newurl = new StringBuffer(prefix+"?"); // quadratic time if we use string
			  while (keyValuePairs.size() > 0) {
				  newurl.append(keyValuePairs.get(0));
				  if (keyValuePairs.size() > 1) { newurl.append("&"); }
				  keyValuePairs.remove(0);
			  }
				  
			  return newurl.toString();
		  }

  }

	
	private class LinkExtractorNodeVisitor extends NodeVisitor {
		protected static final int MAX_LOCKSS_URL_LENGTH = 255;
		private Callback cb_;
		private ArchivalUnit au_;
		private String srcUrl_;
		private String encoding_;
		private boolean malformedBaseUrl_;
		private boolean inScriptMode_;
		private boolean normalizeFormUrls_;
		private Callback emit_;
		private FormUrlNormalizer normalizer_;
		
		public LinkExtractorNodeVisitor(ArchivalUnit au, String srcUrl, Callback cb, String encoding) {
			cb_ = cb;
			au_ = au;
			srcUrl_ = srcUrl;
			encoding_ = encoding;
			malformedBaseUrl_ = false;
			inScriptMode_ = false;
			normalizeFormUrls_ = false; //TODO:this should read the value from the AU
			normalizer_ = new FormUrlNormalizer();
			emit_ = new LinkExtractor.Callback() {
			      public void foundLink(String url) {
						if (url != null) {
							try {
								if (malformedBaseUrl_) {
									if (!UrlUtil.isAbsoluteUrl(url)) {
									  return;
									}
								} else {
									url  = resolveUri(new URL(srcUrl_), url);
								}
								//check length
								if (StringUtils.lastIndexOf(url, "/") != -1 ) {
									int filename_length = url.length() - StringUtils.lastIndexOf(url, "/") - 1;
									if (filename_length > MAX_LOCKSS_URL_LENGTH) {
										return;
									}
								}
								//sort form parameters if enabled
								if (normalizeFormUrls_) { url = normalizer_.normalizeUrl(url, au_); }
								//emit the processed url
								cb_.foundLink(url);
							} catch (MalformedURLException e) {
							}
							catch (PluginException e) {
							}
						}
			      }};

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
			
			TagLinkExtractor tle = getTagLinkExtractor(tag);
			if (tle != null) {
				tle.extractLink(au_, emit_);
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

	    // Make a copy of input stream to be used with a fallback extractor (see comment before Gosling).
		StringWriter w = new StringWriter();
		IOUtils.copy(in, w);
		// Restore the input stream consumed by StringWriter.
		in = new ReaderInputStream(new StringReader(w.toString()), encoding);
		// Make a copy.
		InputStream inCopy = new ReaderInputStream(new StringReader(w.toString()), encoding);

		Parser p = new Parser(new Lexer(new Page(in, encoding)));
		try {
			p.visitAllNodesWith(new LinkExtractorNodeVisitor(au, srcUrl, cb, encoding));
		} catch (ParserException e) {
			logger.warn("Unable to parse url: " + srcUrl);
			logger.warn(e);
		}
		
		// For legacy reasons, we want to ensure link extraction using a more permissive Gosling parser.
		new GoslingHtmlLinkExtractor().extractUrls(au, inCopy, encoding, srcUrl, cb);
	}

}
