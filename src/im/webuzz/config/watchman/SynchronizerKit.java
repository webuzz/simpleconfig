package im.webuzz.config.watchman;

import java.util.List;

import im.webuzz.config.Config;

public class SynchronizerKit {

	public static volatile boolean agentRunning = true;
	
	public static volatile long agentSleepInterval = 10000;

	/*
	public static void main(String[] args) {
		args = Config.initialize(args);
		if (args == null) {
			System.out.println("Usage:");
			System.out.println("\t... " + SynchronizerKit.class.getName() + " [--c:xxx=### ...] <configuration file, e.g. config.ini>");
			System.out.println("Config agent is provided for the following features:");
			System.out.println("1. Test and verify configuration files.");
			System.out.println("2. Synchronize configuration files from remote server, with web watchman enabled.");
			System.out.println("3. Develop other watchman or format converter.");
			return;
		}
		run(args, 0);
	}
	//*/

	public static void run(String[] args, int indexOffset) {
		List<Class<? extends ConfigWatchman>> watchmen = Config.configurationWatchmen;
		if (watchmen == null || watchmen.size() == 0) {
			System.out.println("[WARN] No watchmen are running. Config agent may be a dummy process, doing nothing.");
		}
		for (Class<? extends ConfigWatchman> watchman : watchmen) {
			if (watchman == null) continue;
			System.out.println("[INFO] " + watchman.getName() + " is running.");
		}
		Config.registerUpdatingListener(SynchronizerKit.class);
		Config.registerUpdatingListener(WebConfig.class);
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
