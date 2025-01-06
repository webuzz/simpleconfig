package im.webuzz.config.parser;

import im.webuzz.config.Config;
import im.webuzz.config.InternalConfigUtils;

public class AlarmerKit {

	public static boolean reportErrorToContinue(String msg) {
		String[] msgs = msg.split("(\r\n|\n|\r)");
		for (int i = 0; i < msgs.length; i++) {
			System.out.println("[Config:ERROR] " + msgs[i]);
		}
		if (Config.configurationAlarmer != null) {
			// TODO: Use alarm to send an alert to the operator 
		}
		if (InternalConfigUtils.isInitializationFinished()) {
			if (Config.skipUpdatingWithInvalidItems) {
				// Stop parsing all the left items
				return false;
			}
			return true; // continue to parse other item
		}
		if (Config.exitInitializingOnInvalidItems) {
			System.out.println("[Config:FATAL] Exit current configuration initialization!");
			System.exit(0);
			return false;
		}
		return true; // continue to parse other item
	}

}
