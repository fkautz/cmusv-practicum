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

/**
 * @author vibhor
 *
 */
public class ThiemeJsonLinkExtractor implements LinkExtractor {

	@Override
	public void extractUrls(ArchivalUnit au, InputStream in, String encoding,
			String srcUrl, Callback cb) throws IOException {
		StringWriter w = new StringWriter();
		IOUtils.copy(in, w);
		System.out.println("My thieme json:" + w);
		try {
			JSONArray json = new JSONArray(w.toString().substring(9));
			int length = json.length();
			for (int i = 0; i < length; ++i) {
				JSONArray innerJSON = json.getJSONArray(i);
				cb.foundLink("https://www.thieme-connect.de/ejournals/toc/" +  au.getConfiguration().get("journal_name") + "/" + innerJSON.getString(0));
			}
		} catch (JSONException e) {
			// Emit a log for invalid json but don't raise an exception
		}
	}

}
