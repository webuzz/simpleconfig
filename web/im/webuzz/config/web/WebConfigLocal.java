package im.webuzz.config.web;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import im.webuzz.config.annotation.ConfigClass;
import im.webuzz.config.annotation.ConfigComment;
import im.webuzz.config.annotation.ConfigLocalOnly;

@ConfigClass
@ConfigLocalOnly
@ConfigComment({
    "The WebConfigLocal class represents configuration properties intended",
    "for local use within the application. These properties are excluded from",
    "synchronization with external configuration sources, such as configuration",
    "centers or remote servers, ensuring they remain locally controlled.",
    "",
    "This configuration file includes fields that are essential for internal",
    "operations but should not be exposed or synchronized externally. This ensures",
    "application-specific control over sensitive or static settings that are meant",
    "to be managed directly within the local environment.",
    "",
    "Typically, properties in WebConfigLocal are manually modified by updating",
    "local configuration files. This approach ensures that changes to these fields",
    "remain within the application environment and are not influenced by external",
    "synchronization processes."
})
public class WebConfigLocal {

	public static final String configKeyPrefix = "webconfig.local";

	@ConfigComment({
		"If local fields should be kept and remote configuration should not be",
		"synchronized, put them in this array.",
	})
	public static Set<String> skippingConfigurationFiles = Collections.synchronizedSet(
			new HashSet<String>(Arrays.asList(new String[] {
					WebConfigLocal.configKeyPrefix,
					//ConfigLocal.configKeyPrefix,
			})));
	public static Set<String> skippingConfigurationClasses = Collections.synchronizedSet(
			new HashSet<String>(Arrays.asList(new String[] {
					WebConfigLocal.class.getName(),
			})));


	@ConfigComment({
		"Whether start synchronizing configuration files from remote server or not.",
		"Synchronization can be turned on or off at any time by updating local file.",
		"This configuration item is here to avoid being changed from true to false by",
		"remote configuration server after unintended updates."
	})
	public static boolean synchronizing = false;

	@ConfigComment({
		"Local server name which is used to tell configuration center who is requesting configurations.",
		"Marked as ${local.server.name} in {@link WebConfig#targetURLPattern}",
	})
	public static String localServerName = null;

	@ConfigComment({
		"Local server port which is used to identifier different server session.",
	})
	public static int localServerPort = 0;

	@ConfigComment({
		"Only allows specified extensions. Ignore others extension file.",
		"Be careful of those file extensions that may be harmful to the OS,",
		"like .sh, .bashprofile, .bat, .exe, ..."
	})
	public static String[] extraResourceExtensions = new String[] {
		".xml", ".properties", ".props", ".ini", ".txt", ".config", ".conf", ".cfg", ".js", ".json",
		".key", ".crt", ".pem", ".keystore", // HTTPS
		".html", ".htm", ".css", // web pages
	};

}
