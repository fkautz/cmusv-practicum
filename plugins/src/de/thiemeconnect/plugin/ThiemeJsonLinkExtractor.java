package de.thiemeconnect.plugin;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.lockss.daemon.PluginException;
import org.lockss.extractor.LinkExtractor;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.util.IOUtil;
import org.lockss.util.Logger;
import org.lockss.util.urlconn.CacheException;

import com.sun.org.apache.xerces.internal.impl.xs.identity.Selector.Matcher;

/**
 * Link Extractor for json-like response of generated url emitted by {@link ThiemeHtmlLinkExtractor}.
 * The response for https://www.thieme-connect.de/ejournals/json/issues?shortname=ajp&year=2011 is:
 * <pre>
 * issues = [
 * ["104225", "03", "169-252"], 
 * ["103946", "02", "89-168"], 
 * ["103623", "01", "1-88"]
 * 
 * ]
 * </pre>
 * Above is a javascript statement assigning a json array to a variables 'issues'.
 * 
 * @author vibhor
 *
 */
public class ThiemeJsonLinkExtractor implements LinkExtractor {
	static Logger logger = Logger.getLogger("ThiemeJsonLinkExtractor");

    /**
     * @param au current archival unit
     * @param in current page data
     * @param encoding current page data encoding
     * @param srcUrl current page source
     * @param cb callback to record extracted links.
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
		
		StringWriter w = new StringWriter();
		IOUtils.copy(in, w);
		String jsonString = w.toString();
		if (jsonString.isEmpty()) return;
		try {
			// The thieme plain/text response is actually not a json but javascript assignment statement of following form:
			// issues = [....]; <-- json array.
			// We are interested in parsing out the valid json array out of it.
			Pattern p = Pattern.compile(".*?(\\[\\[.*?\\]\\]).*");
			java.util.regex.Matcher m = p.matcher(jsonString);
			if (!m.matches()) throw new CacheException.UnexpectedNoRetryFailException(
					"Invalid thieme json response. Regex pattern did not match.");
			JSONArray json = new JSONArray(m.group(1));
			int length = json.length();
			for (int i = 0; i < length; ++i) {
				JSONArray innerJSON = json.getJSONArray(i);
				if (innerJSON.length() == 0) continue;
				cb.foundLink(au.getConfiguration().get("base_url") +  au.getConfiguration().get("journal_name") + "/" + innerJSON.getString(0));
			}
		} catch (JSONException e) {
			// Send a cache exception upstream to fail crawler for this url.
			throw new CacheException.UnexpectedNoRetryFailException(
					"Invalid thieme json response. JSONException: " + e);
		}
	}

}
