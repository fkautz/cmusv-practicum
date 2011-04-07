package de.thiemeconnect.plugin;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.collections.SetUtils;
import org.lockss.config.ConfigManager;
import org.lockss.config.ConfigurationPropTreeImpl;
import org.lockss.daemon.PluginException;
import org.lockss.extractor.HtmlParserLinkExtractor;
import org.lockss.extractor.LinkExtractorFactory;
import org.lockss.test.LinkExtractorTestCase;
import org.lockss.test.LockssTestCase;
import org.lockss.test.MockArchivalUnit;
import org.lockss.util.SetUtil;
import org.lockss.util.urlconn.CacheException;

public class TestThiemeJsonLinkExtractor extends LinkExtractorTestCase {
	private String baseUrl_;
	
	@Override
	public String getMimeType() {
		return "text/plain";
	}

	@Override
	public LinkExtractorFactory getFactory() {
		return new ThiemeJsonLinkExtractorFactory();
	}
	
	public void setUp() throws Exception {
		super.setUp();
		// Is there a mock configuration that can be used here?
		mau.setConfiguration(ConfigManager.newConfiguration());
		mau.getConfiguration().put("base_url", "http://example.com/");
		mau.getConfiguration().put("journal_name", "ajp");
	}

	public void testSingleIssue() throws IOException, PluginException {
		assertEquals(SetUtil.set("http://example.com/ajp/1234"), extractUrls("issues = [['1234', 1, '1-2']];"));
	}

	public void testMultipleIssues() throws IOException, PluginException {
		assertEquals(SetUtil.set("http://example.com/ajp/1234",
								 "http://example.com/ajp/2345"),
				extractUrls("issues = [['1234', '1', '1-2'],['2345', '2', '3-4']];"));
	}
	
	public void testEmptyJson() throws IOException, PluginException {
		assertEquals(SetUtil.set(),
				extractUrls("issues = [[]];"));		
	}

	public void testInavlidJsonThrowsCacheException() throws IOException, PluginException {
		try {
			extractUrls("issues = [];");
			fail("Calling with invalid json response should have thrown");
		} catch (CacheException.UnexpectedNoRetryFailException e) {
		}
	}
}
