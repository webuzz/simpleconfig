package im.webuzz.config.notifier;

import im.webuzz.config.Config;
import im.webuzz.config.InternalConfigUtils;

public class ConfigConsoleNotifier implements ConfigNotifier {

	@Override
	public boolean reportError(String msg) {
		if (!logError(msg)) return false;
		
		if (InternalConfigUtils.isInitializationFinished()) {
			if (Config.configurationSkipInvalidUpdate) {
				// Stop parsing all the left items
				return false;
			}
			return true; // continue to parse other item
		}
		if (Config.configurationExitOnInvalidInit) {
			System.out.println("[Config:FATAL] Exit current configuration initialization!");
			System.exit(0);
			return false;
		}
		return true; // continue to parse other item
	}

	protected boolean logError(String msg) {
		String[] msgs = msg.split("(\r\n|\n|\r)");
		for (int i = 0; i < msgs.length; i++) {
			System.out.println("[Config:ERROR] " + msgs[i]);
		}
		return true; // continue;
	}
	
}
