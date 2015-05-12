# simpleconfig
Configuration library, binding Java classes' static fields to configuration files, modifying files to update fields' values

# Usage
Create configuration class with static fields:
	public class PiledConfig {
		public static int coreThreads = 20;
		public static int maxThreads = 128;
	}
Use static fields in your Java project as they are, like followings:
	workerPool = new SimpleThreadPoolExecutor(PiledConfig.coreThreads,
			PiledConfig.maxThreads <= 0 ? Integer.MAX_VALUE : PiledConfig.maxThreads,
			Math.max(PiledConfig.idleThreads, 1),
            PiledConfig.threadIdleSeconds, TimeUnit.SECONDS,
			PiledConfig.maxQueueRequests,
            new SimpleNamedThreadFactory("HTTP Service Worker" + (count == 1 ? "" : "-" + (index + 1))));
	...
	if (lastCoreThreads != PiledConfig.coreThreads) {
		workerPool.setCorePoolSize(PiledConfig.coreThreads);
	}
	if (lastMaxThreads != PiledConfig.maxThreads) {
		workerPool.setMaximumPoolSize(PiledConfig.maxThreads);
	}
On application starting up, just add lines like the following:
	public static void main(String[] args) {
		Config.initialize("./server.ini");
		Config.registerUpdatingListener(PiledConfig.class);
		...
	}
And edit the configuration file (server.ini) with lines like these:
	# PiledConfig
	coreThreads=20
	maxThreads=128
After file being saved, it will take some seconds (about 10s) for application to update static fields to new values.

# Features
1. Support almost all types of static fields: primitive types, array, List, Set, Map and nested types
2. Support multiple configuration files
3. Support synchronizing configurations from remote web servers
4. Support binding and modifying private static fields in third-parties classes 

# License
Eclipse Public License 1.0 