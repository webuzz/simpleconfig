package im.webuzz.config.web;

import java.util.concurrent.Callable;

public class ConfigHttpRequest implements ConfigWebClient {

	@Override
	public void get(boolean async, String url, String user, String password, String[] headers, long lastModified,
			String eTag, WebCallback callback) {
		final HttpRequest req = new HttpRequest();
		req.open("GET", url, async, user, password);
		// if (Config.configurationLogging) System.out.println("[Config:INFO] Requesting " + url);
		req.registerOnLoaded(new Callable<Object>() {
			@Override
			public Object call() throws Exception {
				if (callback instanceof WebCallback) {
					long lastModified = HttpRequest.parseHeaderTimestamp(req, "Last-Modified");
					callback.got(req.getStatus(), req.getResponseBytes(), lastModified);
				} // else do nothing
				return null;
			}
		});
		if (lastModified > 0) {
			req.setRequestHeader("If-Modified-Since", HttpRequest.getHTTPDateString(lastModified));
			if (eTag != null && eTag.length() > 0) {
				req.setRequestHeader("If-None-Match", eTag);
			}
		}
		req.send(); // Normal HTTP request may try to create new thread to do asynchronous job. NIO request may not.
	}

	@Override
	public void post(boolean async, String url, String user, String password, String[] headers, String data,
			WebCallback callback) {
		final HttpRequest req = new HttpRequest();
		req.open("POST", url, async, user, password);
		// if (Config.configurationLogging) System.out.println("[Config:INFO] Requesting " + url);
		req.registerOnLoaded(new Callable<Object>() {
			@Override
			public Object call() throws Exception {
				if (callback instanceof WebCallback) {
					long lastModified = HttpRequest.parseHeaderTimestamp(req, "Last-Modified");
					callback.got(req.getStatus(), req.getResponseBytes(), lastModified);
				} // else do nothing
				return null;
			}
		});
		if (headers != null) {
			for (String header : headers) {
				if (header == null) continue;
				int idx = header.indexOf(':');
				if (idx == -1) continue;
				req.setRequestHeader(header.substring(0, idx).trim(), header.substring(idx + 1).trim());
			}
		}
		req.send(data);
	}

}
