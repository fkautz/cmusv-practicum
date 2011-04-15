package org.lockss.crawler;

import org.lockss.app.LockssDaemon;
import org.lockss.proxy.CrawlProxyManager;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxProfile;
import org.lockss.daemon.ResourceManager;

public class BrowserContext {
	private static int CRAWL_PROXY_PORT = 11400;
	private static int MAX_PORTS_IN_USE = 10;
	private CrawlProxyManager proxy_;
	private FirefoxProfile profile_;
	
	public BrowserContext() {
		// TODO(vibhor): Make the driver configurable using lockss.opt/txt files.
		profile_ = new FirefoxProfile();
		proxy_ = new CrawlProxyManager(LockssDaemon.getLockssDaemon());
	}
	
	public void setUp() throws BrowserContextException {
		ResourceManager m = LockssDaemon.getLockssDaemon().getResourceManager();
		int port = CRAWL_PROXY_PORT;
		int numTries = 0;
		while (!m.isTcpPortAvailable(port, null) && numTries < MAX_PORTS_IN_USE) port++;
		if (numTries >= MAX_PORTS_IN_USE) throw new BrowserContextException(
				MAX_PORTS_IN_USE + " consecutive ports tried but all in use, starting " +
				CRAWL_PROXY_PORT);
		
		proxy_.startProxy(port);
		
		Proxy browserProxy = new Proxy();
		String proxyString = "127.0.0.1:" + port;
		browserProxy.setHttpProxy(proxyString);
		browserProxy.setSslProxy(proxyString);
		profile_.setProxyPreferences(browserProxy);
	}
	
	public void tearDown() {		
		// Clean up the profile from disk.
		// profile_.clean(profile_.layoutOnDisk());
		
		// Shut down the crawl proxy.
		proxy_.stopProxy();

	}
	
	public WebDriver newWebDriver() {
		return new FirefoxDriver(profile_);
	}
	
	public CrawlProxyManager getCrawlProxy() {
		return proxy_;
	}
}
