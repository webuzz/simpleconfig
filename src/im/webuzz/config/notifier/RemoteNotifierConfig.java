package im.webuzz.config.notifier;

import im.webuzz.config.annotation.ConfigClass;
import im.webuzz.config.annotation.ConfigComment;
import im.webuzz.config.annotation.ConfigKeyPrefix;
import im.webuzz.config.annotation.ConfigNotEmpty;
import im.webuzz.config.annotation.ConfigNotNull;
import im.webuzz.config.annotation.ConfigPattern;
import im.webuzz.config.annotation.ConfigPreferredCodec;
import im.webuzz.config.web.ConfigHttpRequest;
import im.webuzz.config.web.ConfigWebClient;

@ConfigClass
@ConfigComment("Configuration for reporting error to remote alarm center. Alarm center will forward errro to developer or edtior.")
@ConfigKeyPrefix("remotenotifier")
public class RemoteNotifierConfig {

	@ConfigComment("A custom web request client (e.g., for HTTP, FTP, or other protocols).")
	@ConfigNotNull
	public static ConfigWebClient webClient = new ConfigHttpRequest();

	@ConfigComment("Base URL of the global alarm center (e.g., HTTP server URL).")
	@ConfigNotEmpty
	public static String reportURL = "http://127.0.0.1:8080/report";

	@ConfigComment("Username for accessing the global alarm center.")
	public static String reportAuthUser = null;

	@ConfigComment("Password for accessing the global alarm center. Encrypted by default.")
	@ConfigPreferredCodec(value = {"secret", "aes"})
	public static String reportAuthPassword = null;

	@ConfigComment("Local server name for identifying requests at the alarm center.")
	@ConfigNotEmpty
	public static String localServerName = "app";

	@ConfigComment("Port number of the local server.")
	public static int localServerPort = 0;

	@ConfigPattern("[A-Z][a-zA-Z0-9\\-]*:\\s.*")
	public static String[] reportHeaders = null;
	
	@ConfigNotEmpty
	public static String reportDataPattern = "${error.message}";
	
	public static boolean reporting = true;
	
}
