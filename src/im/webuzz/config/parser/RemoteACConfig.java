package im.webuzz.config.parser;

import im.webuzz.config.annotation.ConfigClass;
import im.webuzz.config.annotation.ConfigComment;
import im.webuzz.config.annotation.ConfigKeyPrefix;
import im.webuzz.config.annotation.ConfigNotEmpty;
import im.webuzz.config.annotation.ConfigPreferredCodec;

@ConfigClass
@ConfigComment("Configuration for reporting error to remote alarm center. Alarm center will forward errro to developer or edtior.")
@ConfigKeyPrefix("remoteac")
public class RemoteACConfig {

	@ConfigComment("Base URL of the global alarm center (e.g., HTTP server URL).")
	@ConfigNotEmpty
	public static String globalServerURLPrefix = "http://127.0.0.1:8080/report";

	@ConfigComment("Username for accessing the global alarm center.")
	public static String globalServerAuthUser = null;

	@ConfigComment("Password for accessing the global alarm center. Encrypted by default.")
	@ConfigPreferredCodec(value = {"secret", "aes"})
	public static String globalServerAuthPassword = null;

	@ConfigComment("Local server name for identifying requests at the alarm center.")
	@ConfigNotEmpty
	public static String localServerName = "app";

	@ConfigComment("Port number of the local server.")
	public static int localServerPort = 0;

}
