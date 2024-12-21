package im.webuzz.config.strategy;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import im.webuzz.config.Config;
import im.webuzz.config.parser.ConfigParser;
import im.webuzz.config.parser.ConfigParserBuilder;

public class ConfigMemoryFS {

	public static Map<String, ConfigMemoryFile> fs = new ConcurrentHashMap<>();
	
	public static ConfigMemoryFile checkAndPrepareFile(String path, String name, String extension) {
		String key = path + name + extension;
		ConfigMemoryFile memFile = fs.get(key);
		if (memFile == null) {
			memFile = new ConfigMemoryFile();
			memFile.path = path;
			memFile.name = name;
			memFile.extension = extension;
		}
		return memFile;
	}
	
	public static boolean saveToMemoryFS(ConfigMemoryFile file) {
		fs.put(file.path + file.name + file.extension, file);
		return true;
	}
	
	public static boolean saveToLocalFS(ConfigMemoryFile f) {
		File file = new File(f.path + f.name + f.extension);
		File folderFile = file.getParentFile();
		if (!folderFile.exists()) {
			folderFile.mkdirs();
		}
		FileOutputStream fos = null;
		try {
			fos = new FileOutputStream(file);
			fos.write(f.content);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (fos != null) {
				try {
					fos.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
				fos = null;
				if (f.modified > 0) file.setLastModified(f.modified);
			}
		}
		return true;
	}
	
	public static boolean validate() {
		String configFolder = Config.getConfigFolder();
		String configMainName = Config.getConfigMainName();
		String configMainExtension = Config.getConfigMainExtension();
		/*
		String configPath = configFolder + configMainName + configMainExtension;
		File file = new File(configPath);
		if (!file.exists()) {
			System.out.println("[ERROR] " + configPath + " does not exist!");
			return false;
		}
		//*/
		ConfigMemoryFile memFile = ConfigMemoryFS.checkAndPrepareFile(configFolder, configMainName, configMainExtension);
		//memFile.loadFromFile(file);
		if (memFile.content == null) {
			System.out.println("[WARN] Main configuration file " + configFolder + configMainName + configMainExtension + " does not exist!");
			//return false;
		} else {
			ConfigParser<?, ?> defaultParser = null;
			try {
				defaultParser = ConfigParserBuilder.prepareParser(configMainExtension, memFile.content, true);
				if (defaultParser == null) {
					System.out.println("[ERROR] No parsers are configured for extension \"" + configMainExtension + "\".");
					return false;
				}
			} catch (Throwable e) {
				e.printStackTrace();
				return false;
			}
		
			for (Class<?> config : Config.getAllConfigurations()) {
				if (defaultParser.parseConfiguration(config, ConfigParser.FLAG_VALIDATE, null) == -1) return false;
			}
			Set<String> unused = defaultParser.unusedConfigurationItems();
			if (unused != null) {
				String[] unusedKeys = unused.toArray(new String[unused.size()]);
				Arrays.sort(unusedKeys);
				for (String key : unusedKeys) {
					System.out.println("[WARN] Unused configuration item \"" + key + "\"");
				}
			}
		}
		/*
		String folder = configFolder;
		if (folder == null || folder.length() == 0) {
			folder = new File(configPath).getParent();
		}
		//*/
		for (Class<?> clz : Config.getAllConfigurations()) { // configuration classes may be updated already 
			String keyPrefix = Config.getKeyPrefix(clz);
			if (keyPrefix == null || keyPrefix.length() == 0) continue;
			String extension = Config.getConfigExtension(clz);
			if (extension == null) {
				System.out.println("[WARN] No existing " + keyPrefix + ".* files for configuration class " + clz.getName() + "!");
				continue;
			}
			/*
			StringBuilder extBuilder = new StringBuilder();
			file = Config.getConfigFile(keyPrefix, extBuilder);
			if (!file.exists()) {
				System.out.println("[WARN] " + file.getAbsolutePath() + " does not exist! The configuration file is expected for class " + clz.getName());
				continue;
			}
			String extension = extBuilder.toString();
			//*/
			memFile = ConfigMemoryFS.checkAndPrepareFile(configFolder, keyPrefix, extension);
			if (memFile.content == null) {
				System.out.println("[WARN] File " + configFolder + keyPrefix + extension + ", which isexpected for class " + clz.getName() + ", does not exist!");
				continue;
			}
			//memFile.loadFromFile(file);
			try {
				ConfigParser<?, ?> parser = ConfigParserBuilder.prepareParser(extension, memFile.content, false);
				if (parser == null) {
					System.out.println("[ERROR] No parser for configuration extension " + extension);
					return false;
				}
				if (parser.parseConfiguration(clz, ConfigParser.FLAG_VALIDATE, null) == -1) return false;
				Set<String> unused = parser.unusedConfigurationItems();
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
