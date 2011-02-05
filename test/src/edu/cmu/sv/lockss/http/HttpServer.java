package edu.cmu.sv.lockss.http;

import java.io.IOException;

import org.mortbay.http.HttpContext;
import org.mortbay.http.HttpListener;
import org.mortbay.http.HttpServer;
import org.mortbay.http.handler.NotFoundHandler;
import org.mortbay.http.handler.ResourceHandler;
import org.mortbay.util.InetAddrPort;

public class HttpServer {
	private static HttpServer server;
	private static Object lock = new Object();

	public static void startTestServer() throws Exception {
		synchronized (lock) {
			if(server != null) 
				return;
			server = new HttpServer();
			// listener
			HttpListener listener = server.addListener(new InetAddrPort(8080));
			server.addListener(listener);

			// context
			HttpContext context = server.addContext("/");
			context.setResourceBase("test-www");

			// resource handler
			ResourceHandler handler = new ResourceHandler();
			handler.setDirAllowed(true);
			handler.setAcceptRanges(true);
			context.addHandler(handler);
			context.addHandler(new NotFoundHandler());
			server.start();
		}
	}

	public static void stopTestServer() throws IOException,
			InterruptedException {
		synchronized (lock) {
			if(server == null)
				return;
			server.stop();
		}
	}

	public static void main(String[] args) {
		try {
			startTestServer();
			Thread.sleep(30000);
			stopTestServer();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}