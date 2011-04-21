package org.lockss.proxy;

import java.util.Set;

import org.lockss.app.LockssDaemon;

public class CrawlProxyManager extends ProxyManager {
	public CrawlProxyManager(LockssDaemon daemon) {
		initService(daemon);
	}
	
	protected ProxyHandler makeProxyHandler() {
		ProxyHandler handler = super.makeProxyHandler();
		handler.setCrawlProxy(true);
		return handler;
	}
	
	public void startProxy(int proxyPort) {
		this.start = true;
		this.port = proxyPort;
		super.startProxy();
	}
	
	public void stopProxy() {
		this.start = false;
		super.stopProxy();
	}
	
	public Set<String> getProxiedRequests() {
		return this.handler.getProxiedRequests();
	}
}
