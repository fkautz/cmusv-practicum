package org.lockss.prototypes.plugin;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.lockss.app.LockssDaemon;
import org.lockss.daemon.PluginException;
import org.lockss.extractor.GoslingHtmlLinkExtractor;
import org.lockss.extractor.LinkExtractor;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.proxy.CrawlProxyManager;
import org.lockss.util.Logger;
import org.openqa.selenium.By;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxProfile;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.Wait;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SeleniumLinkExtractor implements LinkExtractor {
	private static final Logger logger = Logger.getLogger("SeleniumLinkExtractor");
	
	public SeleniumLinkExtractor() { }

	@Override
	public void extractUrls(ArchivalUnit au, InputStream in, String encoding,
			String srcUrl, Callback cb) throws IOException, PluginException {
		HashSet<String> automatableUrls = new HashSet<String>(au.getSeleniumCandidateUrls());
		logger.debug("matching " + srcUrl + " to selenium candidate:" + au.getSeleniumCandidateUrls());
		if (automatableUrls.contains(srcUrl)) {
			WebDriver d = au.getBrowserContext().newWebDriver();
			logger.debug("Starting webdriver to get: " + srcUrl);
			d.get(srcUrl);
			//d.findElement(By.xpath("//ul[@class=\"journalYearListing\"]/li/a")).click();
			d.findElement(By.xpath("//select[@name=\"categorys\"]/option[@title=\"Biological physics\"]")).setSelected();
			d.findElement(By.xpath("//select[@name=\"time\"]/option[@value=\"week\"]")).setSelected();
			d.findElement(By.id("subjectsearch1")).submit();
			d.quit();
			// Query crawl proxy for urls that were recorded.
			Set<String> urls = au.getBrowserContext().getCrawlProxy().getProxiedRequests();
			for (Iterator<String> i = urls.iterator(); i.hasNext();) {
				String url = i.next();
				logger.debug("CrawlProxy collected " + url);
				cb.foundLink(url);
			}
		} else {
		}
	}

}
