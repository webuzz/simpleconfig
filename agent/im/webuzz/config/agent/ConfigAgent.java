package im.webuzz.config.agent;

import im.webuzz.config.Config;

public class ConfigAgent {

	public static boolean agentRunning = true;
	
	public static long agentSleepInterval = 10000;
	
	public static void main(String[] args) {
		if (args == null || args.length <= 0 || args[0] == null || args[0].length() < 0) {
			System.out.println("Usage: java -cp . " + ConfigAgent.class.getName() + " <config.ini>");
			return;
		}
		
		Config.initialize(args[0]);
		Config.registerUpdatingListener(ConfigAgent.class);
		System.out.println("Config agent started.");
		while (agentRunning) {
			try {
				Thread.sleep(agentSleepInterval);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		System.out.println("Config agent stopped.");
	}

}
