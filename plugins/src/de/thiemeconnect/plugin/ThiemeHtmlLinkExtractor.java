/**
 * 
 */
package de.thiemeconnect.plugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;

import org.apache.commons.io.IOUtils;
import org.lockss.daemon.PluginException;
import org.lockss.extractor.GoslingHtmlLinkExtractor;
import org.lockss.extractor.LinkExtractor;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.plugin.CachedUrlSet;
import org.lockss.util.Logger;
import org.lockss.util.ReaderInputStream;

/**
 * Enhanced html link extractor for Thieme journals. Used by ThiemeConnectPlugin.
 * The link extraction is delegated to {@link GoslingHtmlLinkExtractor} for all pages in an AU except
 * if it is the start page of AU and contains the special thieme form.
 * (The form sends out an ajax request when user changes one of the input values.) 
 * In this case, a url string is created using a pre-determined template and AU definitional parameters.
 * <pre>
 * For example,
 * AJP, 2011 -> http://www.thieme-connect.de/ejournals/json/issues?shortname=ajp&year=2011
 * </pre>
 * The response page of this url is processed by {@link ThiemeJsonLinkExtractor}
 *
 * @author nvibhor
 * 
 */
public class ThiemeHtmlLinkExtractor implements LinkExtractor {
	static Logger logger = Logger.getLogger("ThiemeHtmlLinkExtractor");

	/**
     * @param au current archival unit
     * @param in input stream containing page contents
     * @param encoding encoding information for in stream
     * @param srcUrl address of page being parsed
     * @param cb callback that should be called when an extracted link needs to be recorded.
	 * 
	 * @see
	 * org.lockss.extractor.LinkExtractor#extractUrls(org.lockss.plugin.ArchivalUnit
	 * , java.io.InputStream, java.lang.String, java.lang.String,
	 * org.lockss.extractor.LinkExtractor.Callback)
	 */
	@Override
	public void extractUrls(ArchivalUnit au, InputStream in, String encoding,
			String srcUrl, Callback cb) throws IOException, PluginException {
		// ASSUMPTION(vibhor): As of Feb 2011, the only
		// plugin (ThiemeConnectPlugin) using this extractor specifies a
		// start_url which is ONLY used to establish a valid session-id with
		// the publisher servers and emit the actual start url which is the ajax
		// sent out by the form selection. We do not care about the actual
		// content of this page.
		// For all pages other than start url, we are only interested in the
		// regular links extracted by Gosling and skip form processing.
		if (au.getCrawlSpec().getStartingUrls().contains(srcUrl)) {
			logger.debug1("Thieme start page.");
			StringWriter w = new StringWriter();
			IOUtils.copy(in, w);
			String html = w.toString();
			// TODO(vibhor): Once we have an html parser that can handle getting
			// form with an id, we should use that.
			if (html.contains("id=\"tocAuswahl\"")) {
				String jsonUrl = au.getConfiguration().get("base_url")
					+ "ejournals/json/issues?shortname="
					+ au.getConfiguration().get("journal_name") + "&year="
					+ au.getConfiguration().get("year");
				logger.debug1("Found tocAuswahl form, emitting:" + jsonUrl);			
				cb.foundLink(jsonUrl);
				return;
			}
			
			// The StringWriter has consumed the given input stream, populate it
			// again.
			// TODO(vibhor): Given the assumption above, this code should never
			// be executed in production.
			in = new ReaderInputStream(new StringReader(html),
					encoding);
		}

		(new GoslingHtmlLinkExtractor()).extractUrls(au, in, encoding, srcUrl,
				cb);
	}
}
