package de.thiemeconnect.plugin;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
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

/**
 * @author vibhor
 *
 */
public class ThiemeJsonLinkExtractor implements LinkExtractor {
	static Logger logger = Logger.getLogger("ThiemeJsonLinkExtractor");
	// The thieme plain/text response is actually not a json but javascript assignment statement of following form:
	// issues = [....]; <-- json array.
	// We are interested in parsing out the valid json array out of it.
	static int JSON_ARRAY_START_POS = 9;

	@Override
	public void extractUrls(ArchivalUnit au, InputStream in, String encoding,
			String srcUrl, Callback cb) throws IOException {
		StringWriter w = new StringWriter();
		IOUtils.copy(in, w);
		try {
			JSONArray json = new JSONArray(w.toString().substring(JSON_ARRAY_START_POS));
			int length = json.length();
			for (int i = 0; i < length; ++i) {
				JSONArray innerJSON = json.getJSONArray(i);
				cb.foundLink("https://www.thieme-connect.de/ejournals/toc/" +  au.getConfiguration().get("journal_name") + "/" + innerJSON.getString(0));
			}
		} catch (JSONException e) {
			// Emit a log for invalid json but don't raise an exception
			logger.error("Invalid thieme json response.");
			
		}
	}

}
