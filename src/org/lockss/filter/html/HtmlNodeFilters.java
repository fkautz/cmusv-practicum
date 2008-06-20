/*
 * $Id$
 */

/*

Copyright (c) 2000-2008 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.filter.html;

import org.apache.oro.text.regex.*;
import org.htmlparser.*;
import org.htmlparser.tags.*;
import org.htmlparser.filters.*;
import org.lockss.util.*;

/** Factory methods for making various useful combinations of NodeFilters,
 * and additional  {@link NodeFilter}s to supplement those in
 * {@link org.htmlparser.filters}
 */
public class HtmlNodeFilters {
  static Logger log = Logger.getLogger("HtmlNodeFilters");

  /** No instances */
  private HtmlNodeFilters() {
  }

  /** Create a NodeFilter that matches tags with a specified tagname and
   * attribute value.  Equivalant to
   * <pre>new AndFilter(new TagNameFilter(tag),
   new HasAttributeFilter(attr, val))</pre> */
  public static NodeFilter tagWithAttribute(String tag,
					    String attr, String val) {
    return new AndFilter(new TagNameFilter(tag),
			 new HasAttributeFilter(attr, val));
  }

  /** Create a NodeFilter that matches tags with a specified tagname and
   * that define a given attribute.  Equivalant to
   * <pre>new AndFilter(new TagNameFilter(tag),
   new HasAttributeFilter(attr))</pre>
   * @since 1.32.0
   */
  public static NodeFilter tagWithAttribute(String tag,
                                            String attr) {
    return new AndFilter(new TagNameFilter(tag),
                         new HasAttributeFilter(attr));
  }

  /** Create a NodeFilter that matches div tags with a specified
   * attribute value.  Equivalant to
   * <pre>new AndFilter(new TagNameFilter("div"),
   new HasAttributeFilter(attr, val))</pre> */
  public static NodeFilter divWithAttribute(String attr, String val) {
    return tagWithAttribute("div", attr, val);
  }

  /** Create a NodeFilter that matches tags with a specified tagname and
   * an attribute value matching a regex.
   * @param tag The tagname.
   * @param attr The attribute name.
   * @param valRegexp The pattern to match against the attribute value.
   */
  public static NodeFilter tagWithAttributeRegex(String tag,
						 String attr,
						 String valRegexp) {
    return new AndFilter(new TagNameFilter(tag),
			 new HasAttributeRegexFilter(attr, valRegexp));
  }

  /** Create a NodeFilter that matches tags with a specified tagname and
   * an attribute value matching a regex.
   * @param tag The tagname.
   * @param attr The attribute name.
   * @param valRegexp The pattern to match against the attribute value.
   * @param ignoreCase If true, match is case insensitive
   */
  public static NodeFilter tagWithAttributeRegex(String tag,
						 String attr,
						 String valRegexp,
						 boolean ignoreCase) {
    return new AndFilter(new TagNameFilter(tag),
			 new HasAttributeRegexFilter(attr, valRegexp,
						     ignoreCase));
  }

  /** Create a NodeFilter that matches tags with a specified tagname and
   * nested text containing a substring.  <i>Eg,</i> in <code>
   * &lt;select&gt;&lt;option value="1"&gt;Option text
   * 1&lt;/option&gt;...&lt;/select&gt;</code>, <code>tagWithText("option", "Option text") </code>would match the <code>&lt;option&gt; </code>node.
   * @param tag The tagname.
   * @param text The nested text to match.
   */
  public static NodeFilter tagWithText(String tag, String text) {
    return new AndFilter(new TagNameFilter(tag),
			 new CompositeStringFilter(text));
  }

  /** Create a NodeFilter that matches tags with a specified tagname and
   * nested text containing a substring.
   * @param tag The tagname.
   * @param text The nested text to match.
   * @param ignoreCase If true, match is case insensitive
   */
  public static NodeFilter tagWithText(String tag, String text,
				       boolean ignoreCase) {
    return new AndFilter(new TagNameFilter(tag),
			 new CompositeStringFilter(text, ignoreCase));
  }

  /** Create a NodeFilter that matches composite tags with a specified
   * tagname and nested text matching a regex.  <i>Eg</i>,  <code>
   * @param tag The tagname.
   * @param regex The pattern to match against the nested text.
   */
  public static NodeFilter tagWithTextRegex(String tag, String regex) {
    return new AndFilter(new TagNameFilter(tag),
			 new CompositeRegexFilter(regex));
  }

  /** Create a NodeFilter that matches tags with a specified tagname and
   * nested text matching a regex.  Equivalant to
   * <pre>new AndFilter(new TagNameFilter(tag),
              new RegexFilter(regex))</pre>
   * @param tag The tagname.
   * @param regex The pattern to match against the nested text.
   * @param ignoreCase If true, match is case insensitive
   */
  public static NodeFilter tagWithTextRegex(String tag,
					    String regex,
					    boolean ignoreCase) {
    return new AndFilter(new TagNameFilter(tag),
			 new CompositeRegexFilter(regex, ignoreCase));
  }

  /**
   * Creates a NodeFilter that accepts comment nodes containing the
   * specified string.  The match is case sensitive.
   * @param string The string to look for
   */
  public static NodeFilter commentWithString(String string) {
    return new CommentStringFilter(string);
  }

  /**
   * Creates a NodeFilter that accepts comment nodes containing the
   * specified string.
   * @param string The string to look for
   * @param ignoreCase If true, match is case insensitive
   */
  public static NodeFilter commentWithString(String string,
					     boolean ignoreCase) {
    return new CommentStringFilter(string, ignoreCase);
  }

  /** Create a NodeFilter that matches html comments containing a match for
   * a specified regex.  The match is case sensitive.
   * @param regex The pattern to match.
   */
  public static NodeFilter commentWithRegex(String regex) {
    return new CommentRegexFilter(regex);
  }

  /** Create a NodeFilter that matches html comments containing a match for
   * a specified regex.
   * @param regex The pattern to match.
   * @param ignoreCase If true, match is case insensitive
   */
  public static NodeFilter commentWithRegex(String regex, boolean ignoreCase) {
    return new CommentRegexFilter(regex, ignoreCase);
  }

  /** Create a NodeFilter that matches the lowest level node that matches the
   * specified filter.  This is useful for searching for text within a tag,
   * because the default is to match parent nodes as well.
   */
  public static NodeFilter lowestLevelMatchFilter(NodeFilter filter) {
    return new AndFilter(filter,
			 new NotFilter(new HasChildFilter(filter, true)));
  }

  /** Create a NodeFilter that applies all of an array of LinkRegexXforms
   */
  public static NodeFilter linkRegexYesXforms(String[] regex,
					      boolean[] ignoreCase,
					      String[] target,
					      String[] replace) {
    NodeFilter[] filters = new NodeFilter[regex.length];
    for (int i = 0; i < regex.length; i++) {
      filters[i] = new LinkRegexYesXform(regex[i], ignoreCase[i],
					 target[i], replace[i]);
    }
    // XXX htmlparser 2.0 return new OrFilter(filters);
    NodeFilter ret = filters[0];
    for (int i = 1; i < filters.length; i++) {
      ret = new OrFilter(ret, filters[i]);
    }
    return ret;
  }

  /** Create a NodeFilter that applies all of an array of LinkRegexXforms
   */
  public static NodeFilter linkRegexNoXforms(String[] regex,
					     boolean[] ignoreCase,
					     String[] target,
					     String[] replace) {
    NodeFilter[] filters = new NodeFilter[regex.length];
    for (int i = 0; i < regex.length; i++) {
      filters[i] = new LinkRegexNoXform(regex[i], ignoreCase[i],
					target[i], replace[i]);
    }
    // XXX htmlparser 2.0 return new OrFilter(filters);
    NodeFilter ret = filters[0];
    for (int i = 1; i < filters.length; i++) {
      ret = new OrFilter(ret, filters[i]);
    }
    return ret;
  }

  /**
   * This class accepts all comment nodes containing the given string.
   */
  public static class CommentStringFilter implements NodeFilter {
    private String string;
    private boolean ignoreCase = false;

    /**
     * Creates a CommentStringFilter that accepts comment nodes containing
     * the specified string.  The match is case sensitive.
     * @param substring The string to look for
     */
    public CommentStringFilter(String substring) {
      this(substring, false);
    }

    /**
     * Creates a CommentStringFilter that accepts comment nodes containing
     * the specified string.
     * @param substring The string to look for
     * @param ignoreCase If true, match is case insensitive
     */
    public CommentStringFilter(String substring, boolean ignoreCase) {
      this.string = substring;
      this.ignoreCase = ignoreCase;
    }

    public boolean accept(Node node) {
      if (node instanceof Remark) {
	String nodestr = ((Remark)node).getText();
	return -1 != (ignoreCase
		      ? StringUtil.indexOfIgnoreCase(nodestr, string)
		      : nodestr.indexOf(string));
      }
      return false;
    }
  }

  /**
   * This class accepts all composite nodes containing the given string.
   */
  public static class CompositeStringFilter implements NodeFilter {
    private String string;
    private boolean ignoreCase = false;

    /**
     * Creates a CompositeStringFilter that accepts composite nodes
     * containing the specified string.  The match is case sensitive.
     * @param substring The string to look for
     */
    public CompositeStringFilter(String substring) {
      this(substring, false);
    }

    /**
     * Creates a CompositeStringFilter that accepts composite nodes
     * containing the specified string.
     * @param substring The string to look for
     * @param ignoreCase If true, match is case insensitive
     */
    public CompositeStringFilter(String substring, boolean ignoreCase) {
      this.string = substring;
      this.ignoreCase = ignoreCase;
    }

    public boolean accept(Node node) {
      if (node instanceof CompositeTag) {
	String nodestr = ((CompositeTag)node).getStringText();
	return -1 != (ignoreCase
		      ? StringUtil.indexOfIgnoreCase(nodestr, string)
		      : nodestr.indexOf(string));
      }
      return false;
    }
  }

  /**
   * Abstract class for regex filters
   */
  public abstract static class BaseRegexFilter implements NodeFilter {
    protected Pattern pat;
    protected Perl5Matcher matcher;

    /**
     * Creates a BaseRegexFilter that performs a case sensitive match for
     * the specified regex
     * @param regex The pattern to match.
     */
    public BaseRegexFilter(String regex) {
      this(regex, false);
    }

    /**
     * Creates a BaseRegexFilter that performs a match for
     * the specified regex
     * @param regex The pattern to match.
     * @param ignoreCase If true, match is case insensitive
     */
    public BaseRegexFilter(String regex, boolean ignoreCase) {
      int flags = 0;
      if (ignoreCase) {
	flags |= Perl5Compiler.CASE_INSENSITIVE_MASK;
      }
      try {
	pat = RegexpUtil.getCompiler().compile(regex, flags);
	matcher = RegexpUtil.getMatcher();
      } catch (MalformedPatternException e) {
	throw new RuntimeException(e);
      }
    }

    public abstract boolean accept(Node node);
  }

  /**
   * This class accepts all comment nodes whose text contains a match for
   * the regex.
   */
  public static class CommentRegexFilter extends BaseRegexFilter {
    /**
     * Creates a CommentRegexFilter that accepts comment nodes whose text
     * contains a match for the regex.  The match is case sensitive.
     * @param regex The pattern to match.
     */
    public CommentRegexFilter(String regex) {
      super(regex);
    }

    /**
     * Creates a CommentRegexFilter that accepts comment nodes containing
     * the specified string.
     * @param regex The pattern to match.
     * @param ignoreCase If true, match is case insensitive
     */
    public CommentRegexFilter(String regex, boolean ignoreCase) {
      super(regex, ignoreCase);
    }

    public boolean accept(Node node) {
      if (node instanceof Remark) {
	String nodestr = ((Remark)node).getText();
	return matcher.contains(nodestr, pat);
      }
      return false;
    }
  }

  /**
   * This class accepts everything but applies a transform to
   * links that match the regex.
   */
  public static class LinkRegexYesXform extends BaseRegexFilter {
    /**
     * Creates a LinkRegexYesXform that rejects everything but applies
     * a transform to nodes whose text
     * contains a match for the regex.
     * @param regex The pattern to match.
     * @param ignoreCase If true, match is case insensitive
     * @param target Regex to replace
     * @param replace Text to replace it with
     */
    private String target;
    private String replace;
    public LinkRegexYesXform(String regex, boolean ignoreCase,
			  String target, String replace) {
      super(regex, ignoreCase);
      this.target = target;
      this.replace = replace;
    }

    public boolean accept(Node node) {
      if (node instanceof LinkTag) {
	String url = ((LinkTag)node).getLink();
	if (matcher.contains(url, pat)) {
	  ((LinkTag)node).setLink(url.replaceFirst(target, replace));
	}
      }
      return false;
    }
  }

  /**
   * This class accepts everything but applies a transform to
   * links that don't match the regex.
   */
  public static class LinkRegexNoXform extends BaseRegexFilter {
    /**
     * Creates a LinkRegexNoXform that rejects everything but applies
     * a transform to nodes whose text
     * contains a match for the regex.
     * @param regex The pattern to match.
     * @param ignoreCase If true, match is case insensitive
     * @param target Regex to replace
     * @param replace Text to replace it with
     */
    private String target;
    private String replace;
    public LinkRegexNoXform(String regex, boolean ignoreCase,
			    String target, String replace) {
      super(regex, ignoreCase);
      this.target = target;
      this.replace = replace;
    }

    public boolean accept(Node node) {
      if (node instanceof LinkTag) {
	String url = ((LinkTag)node).getLink();
	if (!matcher.contains(url, pat)) {
	  ((LinkTag)node).setLink(url.replaceFirst(target, replace));
	}
      }
      return false;
    }
  }

  /**
   * This class accepts all composite nodes whose text contains a match for
   * the regex.
   */
  public static class CompositeRegexFilter extends BaseRegexFilter {
    /**
     * Creates a CompositeRegexFilter that accepts composite nodes whose
     * text contains a match for the regex.  The match is case sensitive.
     * @param regex The pattern to match.
     */
    public CompositeRegexFilter(String regex) {
      super(regex);
    }

    /**
     * Creates a CompositeRegexFilter that accepts composite nodes whose
     * text contains a match for the regex.
     * @param regex The pattern to match.
     * @param ignoreCase If true, match is case insensitive
     */
    public CompositeRegexFilter(String regex, boolean ignoreCase) {
      super(regex, ignoreCase);
    }

    public boolean accept(Node node) {
      if (node instanceof CompositeTag) {
	String nodestr = ((CompositeTag)node).getStringText();
	return matcher.contains(nodestr, pat);
      }
      return false;
    }
  }

  /**
   * A filter that matches tags that have a specified attribute whose value
   * matches a regex
   */
  public static class HasAttributeRegexFilter extends BaseRegexFilter {
    private String attr;

    /**
     * Creates a HasAttributeRegexFilter that accepts nodes whose specified
     * attribute value matches a regex.
     * The match is case insensitive.
     * @param attr The attribute name
     * @param regex The regex to match against the attribute value.
     */
    public HasAttributeRegexFilter(String attr, String regex) {
      this(attr, regex, true);
    }

    /**
     * Creates a HasAttributeRegexFilter that accepts nodes whose specified
     * attribute value matches a regex.
     * @param attr The attribute name
     * @param regex The regex to match against the attribute value.
     * @param ignoreCase if true match is case insensitive
     */
    public HasAttributeRegexFilter(String attr, String regex,
				   boolean ignoreCase) {
      super(regex, ignoreCase);
      this.attr = attr;
    }

    public boolean accept(Node node) {
      if (node instanceof Tag) {
	Tag tag = (Tag)node;
	String attribute = tag.getAttribute(attr);
	if (attribute != null) {
	  return matcher.contains(attribute, pat);
	}
      }
      return false;
    }
  }
}
