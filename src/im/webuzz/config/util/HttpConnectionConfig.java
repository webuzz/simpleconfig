package im.webuzz.config.util;

import im.webuzz.config.annotation.ConfigComment;
import im.webuzz.config.annotation.ConfigKeyPrefix;
import im.webuzz.config.annotation.ConfigRange;

@ConfigKeyPrefix("httppool")
public class HttpConnectionConfig {

	@ConfigRange(min = 0, max = 64)
	public static int webCoreWorkers = 1;
	@ConfigComment("Default is 50, It is considered as enough for configuration web synchronizing.")
	@ConfigRange(min = 4, max = 512)
	public static int webMaxWorkers = 50;
	@ConfigComment("Time unit is second.")
	@ConfigRange(min = 1, max = 300)
	public static int webWorkerIdleInterval = 30;

}
