package im.webuzz.config.agent;

import im.webuzz.config.Config;

public class ConfigAgent {

	public static volatile boolean agentRunning = true;
	
	public static volatile long agentSleepInterval = 10000;
	
	public static void main(String[] args) {
		args = Config.initialize(args);
		if (args == null) {
			System.out.println("Usage:");
			System.out.println("\t... " + ConfigAgent.class.getName() + " [--c:xxx=### ...] <configuration file, e.g. config.ini>");
			System.out.println("Config agent is provided for the following features:");
			System.out.println("1. Test and verify configuration files.");
			System.out.println("2. Synchronize configuration files from remote server, with web watchman enabled.");
			System.out.println("3. Develop other watchman or format converter.");
			return;
		}
		String[] watchmen = Config.configurationWatchmen;
		if (watchmen == null || watchmen.length == 0) {
			System.out.println("[WARN] No watchmen are running. Config agent may be a dummy process, doing nothing.");
		}
		for (int i = 0; i < watchmen.length; i++) {
			System.out.println("[INFO] " + watchmen[i] + " is running.");
		}
		Config.registerUpdatingListener(ConfigAgent.class);
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
