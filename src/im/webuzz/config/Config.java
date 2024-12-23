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

import java.io.File;
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
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import im.webuzz.config.annotation.AnnotationScanner;
import im.webuzz.config.annotation.ConfigClass;
import im.webuzz.config.annotation.ConfigComment;
import im.webuzz.config.annotation.ConfigIgnored;
import im.webuzz.config.annotation.ConfigKeyPrefix;
import im.webuzz.config.annotation.ConfigLength;
import im.webuzz.config.annotation.ConfigLocalOnly;
import im.webuzz.config.annotation.ConfigNotEmpty;
import im.webuzz.config.annotation.ConfigNotNull;
import im.webuzz.config.annotation.ConfigOverridden;
import im.webuzz.config.annotation.ConfigPattern;
import im.webuzz.config.annotation.Configurable;
import im.webuzz.config.codec.AESCodec;
import im.webuzz.config.codec.Base64Codec;
import im.webuzz.config.codec.Bytes64Codec;
import im.webuzz.config.codec.BytesAESCodec;
import im.webuzz.config.codec.CodecKit;
import im.webuzz.config.codec.ConfigCodec;
import im.webuzz.config.codec.SecretCodec;
import im.webuzz.config.codec.SecurityConfig;
import im.webuzz.config.generator.GeneratorKit;
import im.webuzz.config.loader.ConfigFileWatcher;
import im.webuzz.config.loader.ConfigMemoryFS;
import im.webuzz.config.loader.ConfigLoader;
import im.webuzz.config.loader.SynchronizerKit;
import im.webuzz.config.parser.ConfigArgumentsParser;
import im.webuzz.config.parser.ConfigINIParser;
import im.webuzz.config.parser.ConfigJSParser;
import im.webuzz.config.parser.ConfigParser;
import im.webuzz.config.parser.ConfigXMLParser;
import im.webuzz.config.util.FileUtils;

@ConfigClass
@ConfigComment({
	"All configurations here are to control the class Config's behaviors.",
	"The configurations are considered as the entry of all other configurations."
})
public class Config {
	
	public static final Charset configFileEncoding = Charset.forName("UTF-8");

	private static final String keyPrefixFieldName = "configKeyPrefix";

	//public static boolean configurationMultipleFiles = true;
	private static String configFolder = null;
	private static String configMainName = null;
	private static String configMainExtension = null;
	
	@ConfigComment({
		"Supporting multiple configuration formats. The scanning extension order will be used",
		"to find configuration file and will decide the priority of which file will be used.",
		"The first extension will be the default file extension."
	})
	@ConfigNotNull
	@ConfigNotEmpty
	@ConfigPattern("(\\.[a-zA-Z0-9]+)")
	public static List<String> configurationScanningExtensions = Arrays.asList(new String[] {
			".ini", // default file extension
			".js", //".json",
			//".conf", ".config", ".cfg",
			//".props", ".properties",
			".xml",
			//".txt"
		});
	
	@ConfigComment({
		"Array of configuration class names, listing all classes which are to be configured via files.",
		"e.g im.webuzz.config.Config;im.webuzz.config.web.WebConfig"
	})
	@ConfigNotNull(depth = 1)
	public static Class<?>[] configurationClasses = null;

	@ConfigComment({
		"Array of configuration package names, listing all classes which are to be configured via files.",
		"e.g im.webuzz.config.*;im.webuzz.config.generator.*"
	})
	@ConfigNotNull(depth = 1)
	@ConfigPattern("[a-zA-Z]([a-zA-Z0-9_\\$]+\\.)*\\*$")
	public static String[] configurationPackages = null;
	
	@ConfigComment({
		"Parsers for different configuration file extensions. Each configuration file will have a parser",
		"instance for itself."
	})
	@ConfigNotNull
	@ConfigNotEmpty
	@ConfigPattern("([a-zA-Z0-9]+)")
	// Each configuration class will have a parser for itself. Parser's class is configured here
	// so we can instantiate many parser instances.
	public static Map<String, Class<? extends ConfigParser<?, ?>>> configurationParsers = new ConcurrentHashMap<>();
	
	@ConfigComment({
		"Singleton for parsing command line arguments into configuration clases.",
		"Default parser is im.webuzz.config.parser.ConfigCommandArgumentParser.",
		"If other parser is provided, #hashCode & #equals method should be overrided."
	})
	@ConfigNotNull
	// The command line argument parser is a singleton, configure parser object directly.
	public static ConfigParser<String[], String[]> commandLineParser = new ConfigArgumentsParser();

	public static Map<Class<?>, Map<String, Annotation[]>> configurationAnnotations = new ConcurrentHashMap<>();
	
	@ConfigNotNull
	public static Class<? extends ConfigLoader> configurationLoader = ConfigFileWatcher.class;
	private static ConfigLoader resourceLoader = null;
	
	@ConfigComment({
		"Codec for some known data. Codec can be used for encrypting some sensitive value in configuration file.",
		"As codec instance will be re-used over and over, codec implementation should be state-less."
	})
	@ConfigNotNull
	@ConfigLength(min = 3, max = 32, depth = 1)
	@ConfigPattern("([a-zA-Z][a-zA-Z0-9]+)")
	public static Map<String, ConfigCodec<?>> configurationCodecs = new ConcurrentHashMap<>();
	
	static {
		configurationParsers.put("ini", ConfigINIParser.class);
		configurationParsers.put("js", ConfigJSParser.class);
		configurationParsers.put("xml", ConfigXMLParser.class);

		configurationCodecs.put("secret", new SecretCodec());
		configurationCodecs.put("aes", new AESCodec());
		configurationCodecs.put("bytesaes", new BytesAESCodec());
		configurationCodecs.put("base64", new Base64Codec());
		configurationCodecs.put("bytes64", new Bytes64Codec());
	}
	
	public static boolean configurationLogging = true;
	
	public static Class<?> configurationAlarmer = null;
	public static boolean exitInitializingOnInvalidItems = true;
	public static boolean skipUpdatingWithInvalidItems = true;

	
	// ****** The following fields are internal, non-configurable ****** //
	protected static volatile long initializedTime = 0;
	protected static boolean initializationFinished = false;

	private static Map<String, Class<?>> allConfigs = new ConcurrentHashMap<>();
	private static List<Class<?>> orderedConfigs = new ArrayList<>();
	private static Map<Class<?>, String> configExtensions = new ConcurrentHashMap<>();

	private static volatile ClassLoader classLoader = null;
	
	// Keep not found classes, if next time trying to load these classes, do not print exceptions
	private static Set<String> notFoundClasses = Collections.newSetFromMap(new ConcurrentHashMap<>());
	private static Map<String, Class<?>> loadedClasses = new ConcurrentHashMap<>(50);
	
	/*
	 * In case configurations got updated from file, try to add class into configuration system
	 */
	public static void update(Properties prop) {
		Class<?>[] configClasses = configurationClasses;
		if (configClasses != null) {
			for (Class<?> clazz : configClasses) {
				if (!allConfigs.containsValue(clazz)) registerClass(clazz);
			}
		}
		String[] configPakages = configurationPackages;
		if (configPakages != null) {
			for (String pkg : configPakages) {
				if (!allConfigs.containsKey(pkg)) register(pkg);
			}
		}
		if (isInitializationFinished() && ((resourceLoader == null && configurationLoader != null)
				|| resourceLoader.getClass() != configurationLoader)) {
			initializeLoadingStrategy();
		}
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
			Class<?> clz = loadConfigurationClass(clazz);
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
		System.out.println("[ERROR] Unknown configuration item " + cfg);
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
			List<Class<?>> classes = AnnotationScanner.getAnnotatedClassesInPackage(pkgName, ConfigClass.class);
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
		initializedTime = System.currentTimeMillis();
		commandLineParser.parseConfiguration(clazz, ConfigParser.FLAG_UPDATE);
		if (resourceLoader != null) resourceLoader.add(clazz);
		if (configurationLogging) {
			System.out.println("[Config] Registering configuration class " + clazz.getName() + " done.");
		}
	}

	// Use Config#register instead
	// May dependent on disk IO
	@Deprecated
	public static void registerUpdatingListener(Class<?> clazz) {
		registerClass(clazz);
	}
	
	/**
	 * Record configuration class's existing file extension.
	 * @param configClass
	 * @param configExtension
	 * @return whether file extension is changed or not.
	 */
	public static boolean recordConfigExtension(Class<?> configClass, String configExtension) {
		String existedConfigExt = configExtensions.put(configClass, configExtension);
		if (existedConfigExt != null && !existedConfigExt.equals(configExtension)) {
			return true;
		}
		return false;
	}
	
	public static String getConfigExtension(Class<?> configClass) {
		String ext = configExtensions.get(configClass);
		if (ext == null) ext = configMainExtension;
		return ext;
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

	public static File getConfigFile(String keyPrefix, StringBuilder extBuilder) {
		String folder = getConfigFolder();
		return getConfigFile(folder, keyPrefix, extBuilder);
	}
	
	public static File getConfigFile(String folder, String keyPrefix, StringBuilder extBuilder) {
		String firstExt = null;
		for (String ext : Config.configurationScanningExtensions) {
			if (ext != null && ext.length() > 0 && ext.charAt(0) == '.') {
				if (firstExt == null) firstExt = ext;
				File file = new File(folder, FileUtils.parseFilePath(keyPrefix + ext));
				if (file.exists()) {
					if (extBuilder != null) extBuilder.append(ext);
					return file;
				}
			}
		}
		if (extBuilder != null) extBuilder.append(firstExt);
		return new File(folder, FileUtils.parseFilePath(keyPrefix + firstExt));
	}
	
	@Deprecated
	public static void initialize(String configPath) {
		initialize(new String[] { configPath });
	}

	public static int parseMainFile(String[] args, int indexOffset,
			StringBuilder folderBuilder, StringBuilder nameBuilder, StringBuilder extBuilder) {
		String defaultConfigName = configMainName != null ? configMainName : "config"; 
		String firstArg;
		if (args != null && args.length > indexOffset
				&& (firstArg = args[indexOffset]) != null && firstArg.length() > 0) {
			File f = new File(firstArg);
			if (f.exists()) {
				int argsOffset = 1;
				if (f.isDirectory()) {
					String configFolderPath = f.getAbsolutePath();
					if (args.length >= 2) {
						String secondArg = args[indexOffset + 1];
						if (secondArg != null && secondArg.length() > 0
								&& secondArg.indexOf('/') == -1 && secondArg.indexOf('\\') == -1) { // just a single file name
							// ./etc/ piled.ini
							for (String ext : Config.configurationScanningExtensions) {
								if (!secondArg.endsWith(ext)) continue;
								if (secondArg.equals(ext)) {
									f = new File(configFolderPath, defaultConfigName + ext);
								} else {
									f = new File(configFolderPath, secondArg);
								}
								argsOffset = 2;
								break;
							}
						}
					}
					if (argsOffset == 1) { // The second argument is not a valid file name
						// ./etc/ im.webuzz.config.Config ...
						f = getConfigFile(configFolderPath, defaultConfigName, null);
					}
					folderBuilder.append(configFolderPath);
				} else { // File
					// ./etc/piled.ini
					String folder = f.getParent();
					if (folder == null) folder = ".";
					folderBuilder.append(folder).append(File.separatorChar);;
				}
				String name = f.getName();
				int idx = name.lastIndexOf('.');
				if (idx != -1) {
					// ./etc/config.js
					nameBuilder.append(name.substring(0, idx));
					extBuilder.append(name.substring(idx));
					if (configurationScanningExtensions.contains(extBuilder.toString())) {
						return indexOffset + argsOffset;
					}
					// else // ./file.tgz // not supporting .tgz file
				}
				// ./etc/config
				System.out.println("[FATAL] Unknown configuration file extension for " + name);
				System.exit(0);
				return -1;
			}
			// ./etc/new.ini
			// Configuration file does not exist! To check if it has a known configuration extension or not
			for (String ext : configurationScanningExtensions) {
				if (!firstArg.endsWith(ext)) continue;
				if (firstArg.equals(ext)) {
					folderBuilder.append(".").append(File.separatorChar);;
					nameBuilder.append(defaultConfigName);
					extBuilder.append(ext);
					return indexOffset + 1;
				}
				String folder = f.getParent();
				if (folder == null) folder = ".";
				folderBuilder.append(folder).append(File.separatorChar);
				String name = f.getName();
				nameBuilder.append(name.substring(0, name.length() - ext.length()));
				extBuilder.append(ext);
				return indexOffset + 1;
			}
		}
		// No main configuration file in the arguments!
		folderBuilder.append(".").append(File.separatorChar);;
		nameBuilder.append(defaultConfigName);
		getConfigFile(folderBuilder.toString(), nameBuilder.toString(), extBuilder);
		return indexOffset;
	}
	
	/**
	 * Need to set configurationFile and configurationExtraPath before calling this method.
	 */
	public static String[] initialize(String[] args) {
		allConfigs.put(Config.class.getName(), Config.class);
		orderedConfigs.add(Config.class);
		ConfigParser<String[], String[]> argumentsParser = null; 
		String[] retArgs = args;
		do {
			argumentsParser = commandLineParser;
			retArgs = argumentsParser.loadResource(retArgs, true);
			argumentsParser.parseConfiguration(Config.class, ConfigParser.FLAG_UPDATE);
			Class<?>[] configs = Config.getAllConfigurations();
			for (int i = 0; i < configs.length; i++) {
				argumentsParser.parseConfiguration(configs[i], ConfigParser.FLAG_UPDATE);
			}
		} while (argumentsParser != commandLineParser); // commandLineParser may be updated by the parser itself!
		
		StringBuilder folderBuilder = new StringBuilder();
		StringBuilder nameBuilder = new StringBuilder();
		StringBuilder extBuilder = new StringBuilder();
		int indexOffset = parseMainFile(retArgs, 0, folderBuilder, nameBuilder, extBuilder);
		configFolder = folderBuilder.toString();
		configMainName = nameBuilder.toString();
		configMainExtension = extBuilder.toString();
		
		initializeLoadingStrategy();
		
		registerClass(SecurityConfig.class);
		
		update(null);
		
		initializedTime = System.currentTimeMillis();
		if (configurationLogging) {
			System.out.println("[Config] Configuration initialized.");
		}
		initializationFinished = true;
		
		if (retArgs != null && retArgs.length > indexOffset) {
			String actionStr = retArgs[indexOffset];
			if (actionStr.startsWith("--run:")) {
				indexOffset++;
				actionStr = actionStr.substring(6);
				if ("generator".equals(actionStr)) {
					GeneratorKit.run(retArgs, indexOffset);
				} else if ("encoder".equals(actionStr)) {
					CodecKit.run(retArgs, indexOffset, false);
				} else if ("decoder".equals(actionStr)) {
					CodecKit.run(retArgs, indexOffset, true);
				} else if ("usage".equals(actionStr)) {
					printUsage();
				} else if ("validator".equals(actionStr)) {
					if (ConfigMemoryFS.validate()) {
						System.out.println("[INFO] Configuration files are OK.");
					} else {
						System.out.println("[ERROR] Configuration validation failed!");
					}
				} else if ("synchronizer".equals(actionStr)) {
					SynchronizerKit.run(retArgs, indexOffset);
				} else if ("class".equals(actionStr)) {
					// --run:class xxx.xxxxx.XXX 
					// By this way, we make all public configuration classes with public static fields configurable
					// without modifying the original sources or jars.
					if (retArgs.length == indexOffset || retArgs[indexOffset] == null || retArgs[indexOffset].length() == 0) {
						printUsage();
					} else {
						try {
							Class<?> clazz = Class.forName(retArgs[1]);
							Method method = clazz.getMethod("main", new Class[] { String[].class });
							if (method != null && (method.getModifiers() & Modifier.STATIC) != 0) { //
								String[] newRetArgs = new String[retArgs.length - indexOffset];
								System.arraycopy(retArgs, indexOffset, newRetArgs, 0, newRetArgs.length);
								method.invoke(null, new Object[] { newRetArgs});
							} else {
								System.out.println("[ERROR] Class " + retArgs[1] + " must contains public static void main(String[] args) method!");
							}
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				} else {
					System.out.println("[ERROR] Unknown action \"" + actionStr + "\"!");
				}
				System.exit(0); // Stop
				return null;
			}
		}
		return retArgs;
	}
	
	private static void printUsage() {
		System.out.println("Usage:");
		System.out.println("\t... " + Config.class.getName() + " [--c:xxx=### ...] <configuration file, e.g. config.ini>"
				+ " [--run:<usage | generator | encoder | decoder | validator | synchronizer | class>] [...]");
		System.out.println();
		System.out.println("For argument --c:xxx=###, the following formats are supported:");
		System.out.println("\t--c:port=6173");
		System.out.println("\t--config:port=6173");
		System.out.println("\t--c-port=6173");
		System.out.println("\t--config-port=6173");
		System.out.println();
		System.out.println("For argument --run:xxx, the following actions are supported:");
		System.out.println("\t--run:usage\tPrint this usage");
		System.out.println("\t--run:generator\tTo generate configuration files");
		System.out.println("\t--run:encoder\tTo encode a password or a sensitive string");
		System.out.println("\t--run:decoder\tTo decode an encoded string back to original value");
		System.out.println("\t--run:validator\tTo verify configuration files");
		System.out.println("\t--run:synchronizer\tTo synchronize local configuration files from remote server");
		System.out.println("\t--run:class <App class with #main(String[]) method>\tContinue to run the main class");
	}

	public static boolean isInitializationFinished() {
		return initializationFinished && initializedTime > 0 && System.currentTimeMillis() - initializedTime > 3000;
	}

	public static boolean reportErrorToContinue(String msg) {
		String[] msgs = msg.split("(\r\n|\n|\r)");
		for (int i = 0; i < msgs.length; i++) {
			System.out.println("[ERROR] " + msgs[i]);
		}
		if (configurationAlarmer != null) {
			// TODO: Use alarm to send an alert to the operator 
		}
		if (isInitializationFinished()) {
			if (skipUpdatingWithInvalidItems) {
				// Stop parsing all the left items
				return false;
			}
			return true; // continue to parse other item
		}
		if (exitInitializingOnInvalidItems) {
			System.out.println("[FATAL] Exit current configuration initialization!");
			System.exit(0);
			return false;
		}
		return true; // continue to parse other item
	}
	
	public static void initializeLoadingStrategy() {
		int loopLoadings = 5;
		while ((resourceLoader == null || resourceLoader.getClass() != configurationLoader)) {
			if (resourceLoader != null) resourceLoader.stop();
			try {
				resourceLoader = configurationLoader.newInstance();
			} catch (Exception e) {
				e.printStackTrace();
				return;
			}
			resourceLoader.start();
			if (loopLoadings-- <= 0) break;
		}
		if (loopLoadings <= 0 && configurationLogging) {
			System.out.println("[Config] Loading watchman classes results in too many loops (5).");
		}
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

	public static Class<?> loadConfigurationClass(String clazz) {
		return loadConfigurationClass(clazz, null);
	}
	public static Class<?> loadConfigurationClass(String clazz, StringBuilder errBuilder) {
		Class<?> clz = loadedClasses.get(clazz);
		if (clz != null) return clz;
		if (classLoader != null) {
			try {
				clz = classLoader.loadClass(clazz);
			} catch (ClassNotFoundException e) {
				if (!notFoundClasses.contains(clazz)) {
					notFoundClasses.add(clazz);
					//e.printStackTrace();
					System.err.println("Class " + clazz + " not found!");
				}
			}
		}
		if (clz == null) {
			try {
				clz = Class.forName(clazz);
			} catch (ClassNotFoundException e) {
				if (errBuilder != null) errBuilder.append(e.getMessage());
				if (!notFoundClasses.contains(clazz)) {
					notFoundClasses.add(clazz);
					//e.printStackTrace();
					System.err.println("Class " + clazz + " not found!");
				}
			}
		}
		if (clz != null) loadedClasses.put(clazz, clz);
		return clz;
	}

	public static String getKeyPrefix(Class<?> clz) {
		String keyPrefix = null;
		try {
			Field f = clz.getDeclaredField(keyPrefixFieldName);
			if (f != null) {
				int modifiers = f.getModifiers();
				if (/*(modifiers & (Modifier.PUBLIC | Modifier.PROTECTED)) != 0
						&& */(modifiers & Modifier.STATIC) != 0
						&& (modifiers & Modifier.FINAL) != 0
						&& f.getType() == String.class) {
					if ((modifiers & Modifier.PUBLIC) == 0) {
						f.setAccessible(true);
					}
					keyPrefix = (String) f.get(clz);
				}
			} // else continue to check annotation
		} catch (Throwable e) {
		}
		if (keyPrefix == null || keyPrefix.length() == 0) {
			ConfigKeyPrefix prefixAnn = clz.getAnnotation(ConfigKeyPrefix.class);
			if (prefixAnn != null) {
				keyPrefix = prefixAnn.value();
			}
		}
		if (keyPrefix != null) {
			keyPrefix = keyPrefix.trim();
			if (keyPrefix.length() != 0) {
				return FileUtils.parseFilePath(keyPrefix);
			}
		}
		return null;
	}
	
	
	/**
	 * Check if given field is filtered/skipped or not.
	 * @param modifiers
	 * @param filterStatic
	 * @return true, should be skipped; false, should not be skipped.
	 */
	public static boolean filterModifiers(int modifiers, boolean filterStatic) {
		int filteringModifiers = Modifier.PUBLIC;
		if ((filteringModifiers <= 0 ? false : (modifiers & filteringModifiers) == 0)
				|| (modifiers & Modifier.FINAL) != 0) {
			// Ignore final fields, ignore non-matched modifier
			return true;
		}
		boolean staticField = (modifiers & Modifier.STATIC) != 0;
		if (filterStatic) {
			if (staticField) return true;
		} else {
			// not filter static fields
			if (!staticField) return true;
		}
		return false;
	}

	public static boolean isFiltered(Field field, boolean filterStatic, Map<String, Annotation[]> fieldAnns, boolean filterLocalOnly) {
		if (field == null) return true;
		int modifiers = field.getModifiers();
		if ((modifiers & Modifier.FINAL) != 0) return true;
		boolean staticField = (modifiers & Modifier.STATIC) != 0;
		if (filterStatic) {
			if (staticField) return true;
		} else {
			// not filter static fields
			if (!staticField) return true;
		}
		boolean annOverridden = false;
		Annotation[] anns = fieldAnns == null ? null : fieldAnns.get(field.getName());
		if (anns != null && anns.length > 0) {
			// Check the first annotation to see if Annotation declared in the source
			// file should be discarded or not 
			annOverridden = anns[0] != null && anns[0] instanceof ConfigOverridden;
		}
		Class<ConfigIgnored> ignoringClass = ConfigIgnored.class;
		if (!annOverridden && field.getAnnotation(ignoringClass) != null) return true;
		int filteringModifiers = Modifier.PUBLIC;
		if (anns != null) {
			for (Annotation ann : anns) {
				Class<? extends Annotation> annClass = ann.getClass();
				if (annClass == ignoringClass) return true;
				if (filterLocalOnly && annClass == ConfigLocalOnly.class) return true;
				if (annClass == Configurable.class) {
					if ((modifiers & Modifier.PUBLIC) == 0) field.setAccessible(true);
					filteringModifiers = 0;
				}
			}
		}
		if (filteringModifiers > 0 && (modifiers & filteringModifiers) == 0) return true;
		return false;
	}

	public static void main(String[] args) {
		initialize(args);
	}
}
