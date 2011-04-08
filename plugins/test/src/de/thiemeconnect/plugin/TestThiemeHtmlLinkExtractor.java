package de.thiemeconnect.plugin;

import java.io.IOException;
import java.util.List;


import org.lockss.config.ConfigManager;
import org.lockss.daemon.BaseCrawlSpec;
import org.lockss.daemon.PluginException;
import org.lockss.extractor.LinkExtractorFactory;
import org.lockss.test.LinkExtractorTestCase;
import org.lockss.util.ListUtil;
import org.lockss.util.SetUtil;

public class TestThiemeHtmlLinkExtractor extends LinkExtractorTestCase {
	private class TestCrawlSpec extends BaseCrawlSpec {

		protected TestCrawlSpec() throws ClassCastException {
			super(ListUtil.list("foo"), null, null, null);
		}
		
		private List<String> startUrls_;
		public void setStartingUrls(List<String> startUrls) {
			this.startUrls_ = startUrls;
		}
		
		public List<String> getStartingUrls() {
			return this.startUrls_;
		}
	}

	public void setUp() throws Exception {
		super.setUp();
		TestCrawlSpec s = new TestCrawlSpec();
		s.setStartingUrls(ListUtil.list(URL));
		mau.setCrawlSpec(s);
		mau.setConfiguration(ConfigManager.newConfiguration());
		mau.getConfiguration().put("base_url", URL);
		mau.getConfiguration().put("journal_name", "ajp");
		mau.getConfiguration().put("year", "2011");
	}

	@Override
	public String getMimeType() {
		return "text/html";
	}

	@Override
	public LinkExtractorFactory getFactory() {
		return new ThiemeHtmlLinkExtractorFactory();
	}
	
	public void testStartingUrlWithForm() throws IOException, PluginException {
		assertEquals(SetUtil.set("http://www.example.com/ejournals/json/issues?shortname=ajp&year=2011"),
				     extractUrls("id=\"tocAuswahl\""));
	}

	public void testStartingUrlWithoutForm() throws IOException, PluginException {
		assertEquals(SetUtil.set(),
				     extractUrls("<html></html>"));
	}
	
	public void testNonStartingUrlEmpty() throws IOException, PluginException {
		((TestCrawlSpec)mau.getCrawlSpec()).setStartingUrls(ListUtil.list());
		assertEquals(SetUtil.set(),
				     extractUrls("id=\"tocAuswahl\""));
	}

	public void testNonStartingUrl() throws IOException, PluginException {
		((TestCrawlSpec)mau.getCrawlSpec()).setStartingUrls(ListUtil.list());
		assertEquals(SetUtil.set("http://www.example.com/link1"),
				     extractUrls("<html><head></head><body><a href=\"/link1\"></a></body></html>"));
	}
}
