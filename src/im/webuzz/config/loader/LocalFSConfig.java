package im.webuzz.config.loader;

import im.webuzz.config.annotation.ConfigClass;
import im.webuzz.config.annotation.ConfigComment;
import im.webuzz.config.annotation.ConfigKeyPrefix;
import im.webuzz.config.annotation.ConfigLocalOnly;

@ConfigClass
@ConfigLocalOnly
@ConfigComment("Configuration settings for managing local file updates.")
@ConfigKeyPrefix("localfs")
public class LocalFSConfig {

	@ConfigComment("Enable file system watcher mode. If false, periodic polling mode will be used instead.")
	public static boolean enableFileWatcher = true;

	@ConfigComment("Interval (in seconds) between checks in periodic polling mode.")
	public static long pollingIntervalSeconds = 10; // Default: 10s

}