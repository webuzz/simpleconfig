package im.webuzz.config.parser;

import java.io.File;
import java.util.Arrays;
import java.util.Set;

import im.webuzz.config.Config;

public class ValidatorKit {

	public static boolean validateAllConfigurations() {
		String configPath = Config.getConfigurationMainFile();
		if (configPath == null) {
			System.out.println("[ERROR] Main configuration file path is missing.");
			return false;
		}
		File file = new File(configPath);
		if (!file.exists()) {
			System.out.println("[ERROR] " + configPath + " does not exist!");
			return false;
		}
		ConfigParser<?, ?> defaultParser = null;
		String configExtension = Config.getConfigurationMainExtension();
		try {
			defaultParser = ConfigParserBuilder.prepareParser(configExtension, file, true);
			if (defaultParser == null) {
				System.out.println("[ERROR] No parsers are configured.");
				return false;
			}
		} catch (Throwable e) {
			e.printStackTrace();
			return false;
		}
	
		Class<?>[] configs = Config.getAllConfigurations();
		for (int i = 0; i < configs.length; i++) {
			if (defaultParser.parseConfiguration(configs[i], ConfigParser.FLAG_VALIDATE, null) == -1) return false;
		}
		Set<String> unused = defaultParser.unusedConfigurationItems();
		if (unused != null) {
			String[] unusedKeys = unused.toArray(new String[unused.size()]);
			Arrays.sort(unusedKeys);
			for (String key : unusedKeys) {
				System.out.println("[WARN] Unused configuration item \"" + key + "\"");
			}
		}
		String extraFolder = Config.getConfigurationFolder();
		String folder = extraFolder;
		if (folder == null || folder.length() == 0) {
			folder = new File(configPath).getParent();
		}
		configs = Config.getAllConfigurations(); // update local variable configs again, configuration classes may be updated already 
		for (int i = 0; i < configs.length; i++) {
			Class<?> clz = configs[i];
			String keyPrefix = Config.getKeyPrefix(clz);
			if (keyPrefix == null || keyPrefix.length() == 0) continue;
			file = Config.getConfigruationFile(keyPrefix);
			if (!file.exists()) {
				System.out.println("[WARN] " + file.getAbsolutePath() + " does not exist! The configuration file is expected for class " + clz.getName());
				continue;
			}
			String fileName = file.getName();
			int extIndex = fileName.lastIndexOf('.');
			String extension = fileName.substring(extIndex);
			try {
				ConfigParser<?, ?> parser = ConfigParserBuilder.prepareParser(extension, file, false);
				if (parser == null) {
					System.out.println("[ERROR] No parser for configuration extension " + extension);
					return false;
				}
				if (parser.parseConfiguration(clz, ConfigParser.FLAG_VALIDATE, null) == -1) return false;
				unused = parser.unusedConfigurationItems();
				if (unused != null) {
					String[] unusedKeys = unused.toArray(new String[unused.size()]);
					Arrays.sort(unusedKeys);
					for (String key : unusedKeys) {
						System.out.println("[WARN] Unused configuration item \"" + key + "\"");
					}
				}
			} catch (Throwable e) {
				e.printStackTrace();
				return false;
			}
		}
	
		return true;
	}

}
