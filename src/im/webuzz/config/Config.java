/*******************************************************************************
 * Copyright (c) 2010 - 2024 webuzz.im and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Source hosted at
 * https://github.com/webuzz/simpleconfig
 * 
 * Contributors:
 *   Zhou Renjian / zhourenjian@gmail.com - initial API and implementation
 *******************************************************************************/

package im.webuzz.config;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import im.webuzz.config.annotation.ConfigClass;
import im.webuzz.config.annotation.ConfigComment;
import im.webuzz.config.annotation.ConfigKeyPrefix;
import im.webuzz.config.annotation.ConfigLength;
import im.webuzz.config.annotation.ConfigNotEmpty;
import im.webuzz.config.annotation.ConfigNotNull;
import im.webuzz.config.annotation.ConfigPattern;
import im.webuzz.config.codec.AESCodec;
import im.webuzz.config.codec.Base64Codec;
import im.webuzz.config.codec.Bytes64Codec;
import im.webuzz.config.codec.BytesAESCodec;
import im.webuzz.config.codec.ConfigCodec;
import im.webuzz.config.codec.SecretCodec;
import im.webuzz.config.common.FileUtils;
import im.webuzz.config.codec.AESKeysConfig;
import im.webuzz.config.generator.GeneratorKit;
import im.webuzz.config.loader.ConfigFileWatcher;
import im.webuzz.config.loader.ConfigMemoryFS;
import im.webuzz.config.notifier.ConfigConsoleNotifier;
import im.webuzz.config.notifier.ConfigNotifier;
import im.webuzz.config.loader.ConfigLoader;
import im.webuzz.config.parser.ConfigArgumentsParser;
import im.webuzz.config.parser.ConfigINIParser;
import im.webuzz.config.parser.ConfigJSParser;
import im.webuzz.config.parser.ConfigParser;
import im.webuzz.config.parser.ConfigXMLParser;

@ConfigClass
@ConfigComment("Configuration class controlling the behavior of the Config system, serving as the entry point for all configurations.")
public class Config {
	
	public static final Charset configFileEncoding = Charset.forName("UTF-8");

	private static final String keyPrefixFieldName = "configKeyPrefix";

	//public static boolean configurationMultipleFiles = true;
	static String configFolder = null;
	static String configMainName = null;
	static String configMainExtension = null;
	
	@ConfigComment("Enable or disable configuration logging.")
	public static boolean configurationLogging = true;

	@ConfigComment("Supported configuration file formats, scanned in order of priority. The first extension is the default.")
	@ConfigNotNull
	@ConfigNotEmpty(depth = 1)
	@ConfigPattern("(\\.[a-zA-Z0-9]+)")
	public static List<String> configurationScanningExtensions = Arrays.asList(new String[] { ".ini", ".js", ".xml" });
	
	@ConfigComment("Parsers for different configuration file extensions. Each file uses a corresponding parser.")
	@ConfigNotEmpty
	@ConfigPattern("([a-zA-Z0-9]+)")
	// Each configuration class will have a parser for itself. Parser's class is configured here
	// so we can instantiate many parser instances.
	public static Map<String, Class<? extends ConfigParser<?, ?>>> configurationParsers = null;
	static {
		Map<String, Class<? extends ConfigParser<?, ?>>> parsers = new HashMap<>();
		parsers.put("ini", ConfigINIParser.class);
		parsers.put("js", ConfigJSParser.class);
		parsers.put("xml", ConfigXMLParser.class);
		configurationParsers = Collections.unmodifiableMap(parsers);
	}
	
	@ConfigComment("Singleton parser for command line arguments. Default: ConfigArgumentsParser.")
	// The command line argument parser is a singleton, configure parser object directly.
	public static ConfigParser<String[], String[]> commandLineParser = new ConfigArgumentsParser();

	@ConfigComment("Codecs for encrypting sensitive configuration values. Codecs must be stateless.")
	@ConfigLength(min = 3, max = 32, depth = 1)
	@ConfigPattern("([a-zA-Z][a-zA-Z0-9]+)")
	public static Map<String, ConfigCodec<?>> configurationCodecs = null;
	static {
		Map<String, ConfigCodec<?>> codecs = new HashMap<>();
		codecs.put("secret", new SecretCodec());
		codecs.put("aes", new AESCodec());
		codecs.put("bytesaes", new BytesAESCodec());
		codecs.put("base64", new Base64Codec());
		codecs.put("bytes64", new Bytes64Codec());
		configurationCodecs = Collections.unmodifiableMap(codecs);
	}
	
	@ConfigComment("List of classes to be configured via files, e.g., im.webuzz.config.Config.")
	public static List<Class<?>> configurationClasses = null;

	@ConfigComment("List of package names containing configuration classes, e.g., im.webuzz.config.*.")
	@ConfigPattern("[a-zA-Z]([a-zA-Z0-9_\\$]+\\.)*\\*$")
	public static List<String> configurationPackages = null;

	@ConfigComment("Add or modify class or field's annotation for validation.")
	public static Map<Class<?>, Map<String, Annotation[]>> configurationAnnotations = null;
	
	@ConfigComment({
		"Loader for managing configuration files. Available loaders:",
		"- ConfigFileWatcher: Watches a local folder for configuration updates (default).",
		"- ConfigFileOnce: Loads configuration files once without watching for updates.",
		"- ConfigWebWatcher: Fetches configurations from a remote server and checks for updates periodically.",
		"- ConfigWebOnce: Fetches configurations from a remote server once without watching for updates.",
		"- ConfigHybridWatcher: Checks both local folder and remote server for updates.",
		"- ConfigHybridOnce: Loads from local folder and remote server once without watching for updates."
	})
	@ConfigNotNull
	public static Class<? extends ConfigLoader> configurationLoader = ConfigFileWatcher.class;
	
	@ConfigComment({
		"Supported environments configuration map. Key represents environment variable (e.g.,",
		"'language', 'country', 'version'), and value is a list of supported values,",
		"and the first value of the list is the default value if given value is not listed."
	})
	@ConfigNotEmpty(depth = 2)
	public static Map<String, List<String>> configurationSupportedEnvironments = null;
	/*
	static {
		Map<String, List<String>> envVars = new HashMap<>();
		envVars.put("language", Arrays.asList("en", "zh"));
		envVars.put("country", Arrays.asList("US", "CN"));
		envVars.put("version", Arrays.asList("1.0", "1.5"));
		configurationSupportedEnvironments = Collections.unmodifiableMap(envVars);
	}
	//*/
	
	@ConfigComment({
		"Configuration map for file name patterns. Key represents file name,",
		"and value is the new file name pattern (e.g., 'string_${language}')."
	})
	public static Map<String, String> configurationFileNamePatterns = null;
	/*
	static {
		Map<String, String> prefixPattern = new HashMap<>();
		prefixPattern.put("string", "string_${language}"); // e.g. Android's I18N solution
		prefixPattern.put("news", "news_${country}");
		prefixPattern.put("settings", "settings.${version}");
		configurationFileNamePatterns = Collections.unmodifiableMap(prefixPattern);
	}
	//*/
	
	@ConfigComment("Notifier for invalid configuration events. Default is a console-based notifier.")
	@ConfigNotNull
	public static ConfigNotifier configurationNotifier = new ConfigConsoleNotifier();

	@ConfigComment({
		"If true, the application will terminate (System.exit) during initialization",
		"when invalid configuration items are detected."
	})
	public static boolean configurationExitOnInvalidInit = true;

	@ConfigComment({
		"If true, invalid configuration items will be skipped during updates,",
		"allowing the application to continue running."
	})
	public static boolean configurationSkipInvalidUpdate = true;

	private static Map<String, Class<?>> allConfigs = new ConcurrentHashMap<>();
	private static List<Class<?>> orderedConfigs = new ArrayList<>();
	static volatile ClassLoader classLoader = null;
	
	private static Map<String, String> environments = new ConcurrentHashMap<String, String>();
	
	/*
	 * In case configurations got updated from file, try to add class into configuration system
	 */
	public static void update(Properties prop) {
		List<Class<?>> configClasses = configurationClasses;
		if (configClasses != null) {
			for (Class<?> clazz : configClasses) {
				if (!allConfigs.containsValue(clazz)) registerClass(clazz);
			}
		}
		List<String> configPakages = configurationPackages;
		if (configPakages != null) {
			for (String pkg : configPakages) {
				if (!allConfigs.containsKey(pkg)) register(pkg);
			}
		}
		InternalConfigUtils.checkStrategyLoader();
	}

	/*
	 * Set an environment for configuration to load related configuration files. 
	 * E.g. For class UITextsConfig with @ConfigKeyPrefix("uitexts")
	 * Invoking #setEnvironment("language", "zh") after configuring
	 * configurationFileNamePatterns.uitexts=uitexts_${language}
	 * , it will try to load file uitexts_zh.ini for the class UITextsConfig.
	 */
	public static void setEnvironment(String key, String defaultValue) {
		environments.put(key, defaultValue);
	}
	
	/*
	 * Set all environments at once.
	 */
	public static void setEnvironments(Map<String, String> envs) {
		environments.putAll(envs);
	}
	
	public static String getEnvironment(String key) {
		return environments.get(key);
	}
	
	/* Recommend using this method in other applications */
	public static void register(Object cfg) {
		if (cfg instanceof Class<?>) {
			registerClass((Class<?>) cfg);
			return;
		}
		if (cfg instanceof String) {
			String clazz = (String) cfg;
			if (clazz.endsWith(".*")) {
				registerPackage(clazz);
				return;
			}
			Class<?> clz = InternalConfigUtils.loadConfigurationClass(clazz);
			if (clz != null) {
				registerClass(clz);
			}
			return;
		}
		if (cfg instanceof Object[]) {
			Object[] cfgs = (Object[]) cfg;
			for (Object c : cfgs) {
				register(c);
			}
			return;
		}
		if (cfg instanceof Collection<?>) {
			Collection<?> cfgs = (Collection<?>) cfg;
			for (Object c : cfgs) {
				register(c);
			}
			return;
		}
		System.out.println("[Config:ERROR] Unknown configuration item " + cfg);
	}
	// For reflection only
	public static void register(Object... cfgs) {
		for (Object c : cfgs) {
			register(c);
		}
	}

	protected static void registerPackage(String starredPkgName) {
		String pkgName = starredPkgName.substring(0, starredPkgName.length() - 2);
		try {
			List<Class<?>> classes = InternalConfigUtils.getConfigClassesInPackage(pkgName);
			for (Class<?> clz : classes) {
				// Add new configuration class may trigger file reading, might be IO blocking
				registerClass(clz);
			}
			allConfigs.put(starredPkgName, Config.class);
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	protected static void registerClass(Class<?> clazz) {
		if (clazz == null) return;
		boolean updating = allConfigs.put(clazz.getName(), clazz) != clazz;
		if (!updating) return;
		orderedConfigs.add(clazz);
		InternalConfigUtils.initializedTime = System.currentTimeMillis();
		ConfigParser<String[], String[]> cmdParser = commandLineParser;
		if (cmdParser != null) cmdParser.parseConfiguration(clazz, ConfigParser.FLAG_UPDATE);
		if (InternalConfigUtils.strategyLoader != null) InternalConfigUtils.strategyLoader.add(clazz);
		if (configurationLogging) {
			System.out.println("[Config:INFO] Registering configuration class " + clazz.getName() + " done.");
		}
	}

	// Use Config#register instead
	// May dependent on disk IO
	@Deprecated
	public static void registerUpdatingListener(Class<?> clazz) {
		registerClass(clazz);
	}
	
	public static String getConfigFolder() {
		return configFolder;
	}
	
	public static String getConfigMainName() {
		return configMainName;
	}
	
	public static String getConfigMainExtension() {
		return configMainExtension;
	}
	
	public static boolean updateConfigMainExtension(String extension) {
		if (!configurationScanningExtensions.contains(extension)
				|| configMainExtension.equals(extension)) return false;
		configMainExtension = extension;
		return true;
	}

	@Deprecated
	public static void initialize(String configPath) {
		initialize(new String[] { configPath });
	}

	public static String[] initialize(String[] args) {
		long before = System.currentTimeMillis();
		allConfigs.put(AESKeysConfig.class.getName(), AESKeysConfig.class);
		orderedConfigs.add(AESKeysConfig.class);
		ConfigParser<String[], String[]> argumentsParser = null; 
		String[] retArgs = args;
		do {
			argumentsParser = commandLineParser;
			if (argumentsParser == null) break;
			retArgs = argumentsParser.loadResource(retArgs, true);
			argumentsParser.parseConfiguration(Config.class, ConfigParser.FLAG_UPDATE);
		} while (argumentsParser != commandLineParser); // commandLineParser may be updated by the parser itself!
		
		if (argumentsParser != null) {
			for (Class<?> config : orderedConfigs) {
				argumentsParser.parseConfiguration(config, ConfigParser.FLAG_UPDATE);
			}
		}

		StringBuilder folderBuilder = new StringBuilder();
		StringBuilder nameBuilder = new StringBuilder();
		StringBuilder extBuilder = new StringBuilder();
		int indexOffset = InternalConfigUtils.parseMainFile(retArgs, 0, folderBuilder, nameBuilder, extBuilder);
		if (retArgs != null && retArgs.length > indexOffset && "--run:usage".equals(retArgs[indexOffset])) {
			printUsage(); // Just print usage without initializing anything
			return null;
		}
		configFolder = folderBuilder.toString();
		configMainName = nameBuilder.toString();
		configMainExtension = extBuilder.toString();
		
		InternalConfigUtils.initializeStrategyLoader();
		
		update(null);
		
		InternalConfigUtils.initializedTime = System.currentTimeMillis();
		if (configurationLogging) {
			System.out.println("[Config:INFO] Configuration initialized successfully, cost=" + (System.currentTimeMillis() - before) + "ms.");
		}
		InternalConfigUtils.initializationFinished = true;
		
		if (retArgs != null && retArgs.length > indexOffset) {
			String actionStr = retArgs[indexOffset];
			if (actionStr.startsWith("--run:")) {
				indexOffset++;
				actionStr = actionStr.substring(6);
				handleAction(actionStr, retArgs, indexOffset);
				System.exit(0); // Stop execution after handling the action.
				return null;
			}
		}
		return retArgs;
	}
	
	private static void handleAction(String actionStr, String[] retArgs, int indexOffset) {
		switch (actionStr) {
			case "usage":
				printUsage();
				break;
			case "generator":
				GeneratorKit.run(retArgs, indexOffset);
				break;
			case "encoder":
				InternalCodecKit.run(retArgs, indexOffset, false);
				break;
			case "decoder":
				InternalCodecKit.run(retArgs, indexOffset, true);
				break;
			case "validator":
				if (ConfigMemoryFS.validate()) {
					System.out.println("[Config:INFO] Configuration validation succeeded.");
				} else {
					System.out.println("[Config:ERROR] Configuration validation failed.");
				}
				break;
			case "synchronizer":
				runSynchronizer(retArgs, indexOffset);
				break;
			case "wrapper":
				runWrapper(retArgs, indexOffset);
				break;
			default:
				System.out.println("[Config:ERROR] Unknown action: \"" + actionStr + "\".");
				break;
		}
	}

	static volatile boolean agentRunning = true;
	static volatile long agentSleepInterval = 10000;

	private static void runSynchronizer(String[] args, int indexOffset) {
		System.out.println("[Config:INFO] Configuration synchronizer started.");
		while (agentRunning) {
			try {
				Thread.sleep(agentSleepInterval);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		System.out.println("[Config:INFO] Configuration synchronizer stopped.");
	}

	private static void runWrapper(String[] retArgs, int indexOffset) {
		if (retArgs.length <= indexOffset || retArgs[indexOffset] == null || retArgs[indexOffset].length() == 0) {
			printUsage();
			return;
		}
		try {
			Class<?> clazz = Class.forName(retArgs[indexOffset]);
			Method method = clazz.getMethod("main", String[].class);
			if (method != null && Modifier.isStatic(method.getModifiers())) {
				String[] newArgs = new String[retArgs.length - indexOffset];
				System.arraycopy(retArgs, indexOffset, newArgs, 0, newArgs.length);
				method.invoke(null, (Object) newArgs);
			} else {
				System.out.println("[Config:ERROR] The specified class must contain a public static void main(String[] args) method.");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private static void printUsage() {
		System.out.println("Usage:");
		System.out.println("\tjava (vm arguments, classpath...) " + Config.class.getName() + " [--c:xxx=### ...] <config file (e.g., config.ini)>"
				+ " [--run:<usage | generator | encoder | decoder | validator | synchronizer | wrapper>] [...]");
		System.out.println();
		System.out.println("For arguments like --c:xxx=###, supported formats include:");
		System.out.println("\t--c:port=6173");
		System.out.println("\t--config:port=6173");
		System.out.println("\t--c-port=6173");
		System.out.println("\t--config-port=6173");
		System.out.println();
		System.out.println("Supported actions for --run:xxx:");
		System.out.println("\t--run:usage\t\tDisplays this usage guide.");
		System.out.println("\t--run:generator\t\tGenerates configuration files.");
		System.out.println("\t--run:encoder\t\tEncodes a password or sensitive string.");
		System.out.println("\t--run:decoder\t\tDecodes an encoded string to its original value.");
		System.out.println("\t--run:validator\t\tValidates configuration files.");
		System.out.println("\t--run:synchronizer\tSynchronizes local configurations with a remote server.");
		System.out.println("\t--run:wrapper <class>\tExecutes the main method of the specified class.");
	}

	/*
	 * Will be invoked by watchman classes
	 */
	public static Class<?>[] getAllConfigurations() {
		return orderedConfigs.toArray(new Class<?>[orderedConfigs.size()]);
	}
	
	public static ClassLoader getConfigurationClassLoader() {
		return classLoader;
	}

	public static void setConfigurationClassLoader(ClassLoader classLoader) {
		Config.classLoader = classLoader;
	}

	public static String getKeyPrefix(Class<?> clz) {
		String keyPrefix = null;
		ConfigKeyPrefix prefixAnn = InternalConfigUtils.getSingleKnownAnnotations(clz, ConfigKeyPrefix.class);
		if (prefixAnn != null) keyPrefix = prefixAnn.value();
		if (keyPrefix == null || keyPrefix.length() == 0) {
			try {
				Field f = clz.getDeclaredField(keyPrefixFieldName);
				if (f != null) {
					int modifiers = f.getModifiers();
					if (/*(modifiers & (Modifier.PUBLIC | Modifier.PROTECTED)) != 0
							&& */(modifiers & Modifier.STATIC) != 0
							&& (modifiers & Modifier.FINAL) != 0
							&& f.getType() == String.class) {
						if ((modifiers & Modifier.PUBLIC) == 0) f.setAccessible(true);
						keyPrefix = (String) f.get(clz);
					}
				} // else continue to check annotation
			} catch (Throwable e) {
			}
		}
		if (keyPrefix != null) {
			keyPrefix = keyPrefix.trim();
			if (keyPrefix.length() != 0) {
				Map<String, String> patterns = configurationFileNamePatterns;
				String fileName = patterns == null ? null : patterns.get(keyPrefix);
				if (fileName == null || fileName.length() == 0) return FileUtils.parseFilePath(keyPrefix);
				return FileUtils.parseFilePath(compleFileName(fileName));
			}
		}
		return null;
	}
	
	private static String compleFileName(String pattern) {
		StringBuilder builder = null;
		int idx = -1;
		int lastIdx = 0;
		String propKeyPrefix = "${";
		Map<String, String> currentEnvs = environments;
		Map<String, List<String>> supportedEnvs = configurationSupportedEnvironments;
		while ((idx = pattern.indexOf(propKeyPrefix, lastIdx)) != -1) {
			if (builder == null) builder = new StringBuilder();
			builder.append(pattern.substring(lastIdx, idx));
			lastIdx = idx;
			int beginIdx = idx + propKeyPrefix.length();
			int endIdx = pattern.indexOf('}', beginIdx);
			if (endIdx == -1) break;
			String propName = pattern.substring(beginIdx, endIdx);
			String propValue = currentEnvs.get(propName);
			if (propValue != null && propValue.length() > 0) {
				List<String> supportedValues = supportedEnvs == null ? null : supportedEnvs.get(propName);
				if (supportedValues != null && !supportedValues.isEmpty() && !supportedValues.contains(propValue)) {
					// value is not supported, switch to the first value (considered as the default value
					propValue = supportedValues.get(0);
				}
				builder.append(propValue);
			}
			lastIdx = endIdx + 1;
		}
		if (lastIdx == 0) return pattern;
		builder.append(pattern.substring(lastIdx));
		return builder.toString();
	}
	
	public static void main(String[] args) {
		initialize(args);
	}

}
