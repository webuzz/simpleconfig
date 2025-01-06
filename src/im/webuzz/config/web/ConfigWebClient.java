package im.webuzz.config.web;

public interface ConfigWebClient {

	public void get(boolean async, String url, String user, String password, String[] headers, long lastModified, String eTag, WebCallback callback);
	
	public void post(boolean async, String url, String user, String password, String[] headers, String data, WebCallback callback);
	
}
