/*
 * $Id: NaturePublishingGroupHtmlFilterFactory.java,v 1.12 2011/04/19 20:34:24 thib_gc Exp $
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

package org.lockss.plugin.nature;

import java.io.InputStream;

import org.htmlparser.NodeFilter;
import org.htmlparser.filters.*;
import org.lockss.daemon.PluginException;
import org.lockss.filter.html.*;
import org.lockss.plugin.*;

/**
 * <p>Normalizes HTML pages from Nature Publishing Group journals.</p>
 * @author Thib Guicherd-Callin
 */
public class NaturePublishingGroupHtmlFilterFactory implements FilterFactory {

  public InputStream createFilteredInputStream(ArchivalUnit au,
                                               InputStream in,
                                               String encoding)
      throws PluginException {
    NodeFilter[] filters = new NodeFilter[] {
        // Advertising
        HtmlNodeFilters.tagWithAttribute("div", "class", "leaderboard"),
        // Advertising
        HtmlNodeFilters.tagWithAttribute("div", "class", "ad-vert"),
        // Advertising
        HtmlNodeFilters.tagWithAttributeRegex("div", "class", "^ad-rh "),
        // Advertising
        HtmlNodeFilters.tagWithAttributeRegex("div", "class", "^ad "),
        // Contains the institution name
        HtmlNodeFilters.tagWithAttributeRegex("div", "class", "login-nav"),
        // User-submitted comments
        HtmlNodeFilters.tagWithText("p", "There are currently no comments."),
        // User-submitted comments
        HtmlNodeFilters.tagWithAttributeRegex("ul", "class", "^comments "),
        // Liable to change (navigational elements, links to other journals, etc.)
        HtmlNodeFilters.tagWithAttribute("div", "id", "journalnav"),
        // Advertising, jobs ticker, news ticker, etc.
        HtmlNodeFilters.tagWithAttribute("div", "id", "extranav"),
        // Contains the current year
        HtmlNodeFilters.tagWithAttribute("div", "id", "footer-copyright"),
        // Suggested similar pages
        HtmlNodeFilters.tagWithAttribute("div", "id", "more-like-this"),
        // Older table layout: contains the institution name
        HtmlNodeFilters.tagWithAttribute("div", "class", "logon"),
        // Older table layout: jobs ticket
        HtmlNodeFilters.tagWithAttribute("div", "id", "natjob"),
        // Older table layout: Nature Open Innovation Challenge ticker
        HtmlNodeFilters.tagWithAttribute("div", "id", "natpav"),
        // Variable scripts 
        new TagNameFilter("script"),
        // Variable scripts 
        new TagNameFilter("noscript"),
        // ?
        HtmlNodeFilters.tagWithAttribute("div", "class", "baseline-wrapper"),
        // Variability unknown; institution-dependent or time-dependent
        HtmlNodeFilters.tagWithAttribute("meta", "name", "WT.site_id"),
    };
    return new HtmlFilterInputStream(in,
                                     encoding,
                                     HtmlNodeFilterTransform.exclude(new OrFilter(filters)));
  }

}
