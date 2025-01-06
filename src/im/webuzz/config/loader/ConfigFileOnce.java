package im.webuzz.config.loader;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import im.webuzz.config.Config;
import im.webuzz.config.InternalConfigUtils;
import im.webuzz.config.common.FileUtils;
import im.webuzz.config.parser.ConfigParser;
import im.webuzz.config.parser.ConfigParserBuilder;

public class ConfigFileOnce implements ConfigLoader {

	protected boolean running = false;
	protected Map<String, Long> fileLastUpdateds = new ConcurrentHashMap<String, Long>();
	protected Map<String, Class<?>> keyPrefixClassMap = new ConcurrentHashMap<String, Class<?>>();

	private long mainFileLastUpdated = 0;
	private ConfigParser<?, ?> defaultParser = null;

	@Override
	public Class<?>[] prerequisites() {
		return new Class<?>[] { LocalFSConfig.class };
	}

	@Override
	public boolean start() {
		if (running) return false;
		updateAllConfigurations(Config.getConfigFolder(), Config.getConfigMainName(), Config.getConfigMainExtension());
		running = true;
		return running;
	}

	@Override
	public void stop() {
		running = false;
	}

	@Override
	public void add(Class<?> configClazz) {
		if (!running) return; // Not started yet
		if (defaultParser != null) defaultParser.parseConfiguration(configClazz, ConfigParser.FLAG_UPDATE);
		String keyPrefix = Config.getKeyPrefix(configClazz);
		if (keyPrefix == null || keyPrefix.length() == 0) return;
		StringBuilder extBuilder = new StringBuilder();
		File file = InternalConfigUtils.getConfigFile(keyPrefix, extBuilder);
		if (!file.exists()) return;
		String extension = extBuilder.toString();
		ConfigMemoryFile memFile = ConfigMemoryFS.checkAndPrepareFile(Config.getConfigFolder(), keyPrefix, extension);
		memFile.synchronizeWithLocal(file, false);
		long lastUpdated = 0;
		try {
			ConfigParser<?, ?> parser = ConfigParserBuilder.prepareParser(extension, memFile.content, false);
			if (parser == null) return;
			lastUpdated = file.lastModified();
			fileLastUpdateds.put(file.getName(), lastUpdated);
			keyPrefixClassMap.put(keyPrefix, configClazz);
			InternalConfigUtils.recordConfigExtension(configClazz, extension); // always update the configuration class' file extension
			parser.parseConfiguration(configClazz, ConfigParser.FLAG_UPDATE);
			if (Config.configurationLogging) {
				System.out.println("[Config:INFO] Configuration " + configClazz.getName() + "/" + file.getAbsolutePath() + " loaded.");
			}
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}

	protected void loadAllResourceFiles() {
		String[] extraFiles = RemoteCCConfig.extraResourceFiles;
		if (extraFiles == null || extraFiles.length == 0) return;
		String[] extraExts = RemoteCCConfig.extraResourceExtensions;
		String configFolder = Config.getConfigFolder();
		for (String path : extraFiles) {
			path = FileUtils.parseFilePath(path);
			File f = new File(configFolder, path);
			String folder = f.getParent();
			if (folder == null) folder = ".";
			String filePath = folder + File.separatorChar;
			String name = f.getName();
			String fileName = null;
			String fileExt = null;
			boolean matched = false;
			for (String extraExt : extraExts) {
				if (path.endsWith(extraExt)) {
					matched = true;
					fileName = name.substring(0, name.length() - extraExt.length());
					fileExt = extraExt;
					break;
				}
			}
			if (!matched) continue;
			/*
			if (!matched) {
				if (Config.configurationLogging) {
					System.out.println("[Config:INFO] Resource file " + path + " is skipped as its extension is not permitted.");
				}
				continue;
			}
			//*/
			ConfigMemoryFile memFile = ConfigMemoryFS.checkAndPrepareFile(filePath, fileName, fileExt);
			memFile.synchronizeWithLocal(f, false);
		}
	}

	private boolean parseConfig(ConfigParser<?, ?> parser, Class<?> config) {
		if (Config.configurationSkipInvalidUpdate) {
			if (parser.parseConfiguration(config, ConfigParser.FLAG_CHECK) != -1) { // checking
				return parser.parseConfiguration(config, ConfigParser.FLAG_UPDATE) == 1;
			}
			return false;
		}
		return parser.parseConfiguration(config, ConfigParser.FLAG_UPDATE) == 1;
	}
	
	protected void updateAllConfigurations(String configFolder, String configName, String configExtension) {
		//if (configPath == null) return;
		String configPath = configFolder + configName + configExtension;
		File file = new File(configPath);
		if (!file.exists()) {
			System.out.println("[Config:ERROR] Given main configuration file \"" + configPath + "\" does not exist!");
			//return;
		} else if (file.lastModified() != mainFileLastUpdated) {
			ConfigMemoryFile memFile = ConfigMemoryFS.checkAndPrepareFile(configFolder, configName, configExtension);
			memFile.synchronizeWithLocal(file, false);
			if (Config.configurationLogging && mainFileLastUpdated > 0) {
				System.out.println("[Config:INFO] Configuration file " + configPath + " updated.");
			}
			try {
				defaultParser = ConfigParserBuilder.prepareParser(configExtension, memFile.content, true);
				if (defaultParser == null) {
					System.out.println("[Config:ERROR] Fail to prepare parser for file extension \"" + configExtension + "\"!");
					//return;
				}
				mainFileLastUpdated = file.lastModified();
			} catch (Throwable e) {
				e.printStackTrace();
				//return;
			}
		}
		Class<?> oldLoader = Config.configurationLoader; // old loader should be this class
		if (defaultParser != null && parseConfig(defaultParser, Config.class)) {
			//InternalConfigUtils.recordConfigExtension(Config.class, configExtension);
			if (oldLoader != Config.configurationLoader) { // loader changed!
				InternalConfigUtils.checkStrategyLoader();
			}
		}
		for (Class<?> clz : Config.getAllConfigurations()) {
			String keyPrefix = Config.getKeyPrefix(clz);
			if (defaultParser != null && parseConfig(defaultParser, clz)) {
				if (keyPrefix == null || keyPrefix.length() == 0) {
					InternalConfigUtils.recordConfigExtension(clz, configExtension);
				}
			}
			if (keyPrefix == null || keyPrefix.length() == 0) continue;
			keyPrefixClassMap.put(keyPrefix, clz);
			StringBuilder extBuilder = new StringBuilder();
			file = InternalConfigUtils.getConfigFile(configFolder, keyPrefix, extBuilder);
			if (!file.exists()) continue;
			updateSingleConfiguration(file, configFolder, keyPrefix, extBuilder.toString(), clz);
		}
	}

	protected void updateSingleConfiguration(File file, String filePath, String filePrefix, String extension, Class<?> clz) {
		String fileName = filePrefix + extension;
		long lastUpdated = 0;
		Long v = fileLastUpdateds.get(fileName);
		if (v != null) {
			lastUpdated = v.longValue();
		}
		if (file.lastModified() == lastUpdated) return;
		ConfigMemoryFile memFile = ConfigMemoryFS.checkAndPrepareFile(filePath, filePrefix, extension);
		memFile.synchronizeWithLocal(file, false); // file.exists() == true, see call hierarchy
		
		if (Config.configurationLogging && lastUpdated > 0) {
			System.out.println("[Config:INFO] Configuration " + clz.getName() + " at " + file.getAbsolutePath() + " updated.");
		}
		try {
			ConfigParser<?, ?> parser = ConfigParserBuilder.prepareParser(extension, memFile.content, false);
			if (parser == null) return;
			lastUpdated = file.lastModified();
			fileLastUpdateds.put(fileName, lastUpdated);
			InternalConfigUtils.recordConfigExtension(clz, extension); // always update the configuration class' file extension
			parseConfig(parser, clz);
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}
	
}
