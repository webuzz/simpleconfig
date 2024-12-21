package im.webuzz.config.watchman;

//import java.util.List;

import im.webuzz.config.Config;

public class SynchronizerKit {

	public static volatile boolean agentRunning = true;
	
	public static volatile long agentSleepInterval = 10000;

	public static void run(String[] args, int indexOffset) {
//		Config.configurationLoadingStrategy = ConfigWebOnce.class;
//		Config.configurationLoadingStrategy = ConfigWebWatcher.class;
//		Config.initializeLoadingStrategy();
		
		Config.register(SynchronizerKit.class);
		Config.register(WebConfig.class);
		System.out.println("[INFO] Config agent started.");
		while (agentRunning) {
			try {
				Thread.sleep(agentSleepInterval);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		System.out.println("[INFO] Config agent stopped.");
	}

}
