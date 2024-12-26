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

	@ConfigComment("Enable folder watcher mode. If false, loop checking mode will be used instead.")
	public static boolean folderWatcherMode = true;

	@ConfigComment("Interval (in seconds) between checks in loop checking mode.")
	public static long loopSleepInterval = 100; // Default: 10s

}