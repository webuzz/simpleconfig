package im.webuzz.config.loader;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import im.webuzz.config.Config;
import im.webuzz.config.InternalConfigUtils;
import im.webuzz.config.parser.ConfigParser;
import im.webuzz.config.parser.ConfigParserBuilder;

public class ConfigFileOnce implements ConfigLoader {

	protected boolean running = false;
	protected Map<String, Long> fileLastUpdateds = new ConcurrentHashMap<String, Long>();
	protected Map<String, Class<?>> keyPrefixClassMap = new ConcurrentHashMap<String, Class<?>>();

	private long mainFileLastUpdated = 0;
	private ConfigParser<?, ?> defaultParser = null;

	@Override
	public boolean start() {
		if (running) return false;
		Config.register(LocalFSConfig.class);
		updateAllConfigurations(Config.getConfigFolder(), Config.getConfigMainName(), Config.getConfigMainExtension());
		running = true;
		return true;
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
		if (keyPrefix == null || keyPrefix.length() == 0) {
			return;
		}
		StringBuilder extBuilder = new StringBuilder();
		File file = InternalConfigUtils.getConfigFile(keyPrefix, extBuilder);
		if (!file.exists()) return;
		String extension = extBuilder.toString();
		ConfigMemoryFile memFile = ConfigMemoryFS.checkAndPrepareFile(Config.getConfigFolder(), keyPrefix, extension);
		if (memFile.content == null) {
			memFile.loadFromFile(file);
			ConfigMemoryFS.saveToMemoryFS(memFile);
		} else {
			memFile.loadFromFile(file);
		}
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
	
	private boolean parseConfig(ConfigParser<?, ?> parser, Class<?> config) {
		if (Config.skipUpdatingWithInvalidItems) {
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
			if (memFile.content == null) {
				memFile.loadFromFile(file);
				ConfigMemoryFS.saveToMemoryFS(memFile);
			} else {
				memFile.loadFromFile(file);
			}
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
		String folder = Config.getConfigFolder();
		if (folder == null || folder.length() == 0) {
			folder = new File(configPath).getParent();
		}
		Class<?> oldLoader = Config.configurationLoader; // old loader should be this class
		if (defaultParser != null && parseConfig(defaultParser, Config.class)) {
			//InternalConfigUtils.recordConfigExtension(Config.class, configExtension);
			if (oldLoader != Config.configurationLoader) { // loader changed!
				System.out.println("[Config:INFO] Switching configuration loader from " + oldLoader.getName() + " to " + Config.configurationLoader.getName());
				InternalConfigUtils.checkStrategyLoader();
				return;
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
			file = InternalConfigUtils.getConfigFile(keyPrefix, extBuilder);
			if (!file.exists()) continue;
			updateSingleConfiguration(file, keyPrefix, extBuilder.toString(), clz);
		}
	}

	protected void updateSingleConfiguration(File file, String filePrefix, String extension, Class<?> clz) {
		String fileName = filePrefix + extension;
		long lastUpdated = 0;
		Long v = fileLastUpdateds.get(fileName);
		if (v != null) {
			lastUpdated = v.longValue();
		}
		if (file.lastModified() == lastUpdated) return;
		ConfigMemoryFile memFile = ConfigMemoryFS.checkAndPrepareFile(file.getParent(), filePrefix, extension);
		if (memFile.content == null) {
			memFile.loadFromFile(file);
			ConfigMemoryFS.saveToMemoryFS(memFile);
		} else {
			memFile.loadFromFile(file);
		}
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
