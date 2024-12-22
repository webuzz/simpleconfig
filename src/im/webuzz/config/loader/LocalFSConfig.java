package im.webuzz.config.loader;

import im.webuzz.config.annotation.ConfigClass;
import im.webuzz.config.annotation.ConfigComment;
import im.webuzz.config.annotation.ConfigKeyPrefix;
import im.webuzz.config.annotation.ConfigLocalOnly;

@ConfigClass
@ConfigLocalOnly
@ConfigComment({
	"This configuration file is used to configure local configuration file watchman.",
	"It will control how the file watchman keep eyes on local files."
})
@ConfigKeyPrefix("localfs")
public class LocalFSConfig {

	public static boolean watching = true;
	
	public static long loopingInterval = 10000; // 10s
	
	public static boolean detectingChanges = true;
	
}
