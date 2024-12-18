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
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import im.webuzz.config.annotation.AnnotationScanner;
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
import im.webuzz.config.codec.CodecKit;
import im.webuzz.config.codec.SecretCodec;
import im.webuzz.config.codec.SecurityConfig;
import im.webuzz.config.generator.GeneratorKit;
import im.webuzz.config.parser.ConfigArgumentsParser;
import im.webuzz.config.parser.ConfigINIParser;
import im.webuzz.config.parser.ConfigJSParser;
import im.webuzz.config.parser.ConfigParser;
import im.webuzz.config.parser.ConfigXMLParser;
import im.webuzz.config.parser.ValidatorKit;
import im.webuzz.config.util.DeepComparator;
import im.webuzz.config.watchman.SynchronizerKit;
import im.webuzz.config.watchman.ConfigFileWatchman;
import im.webuzz.config.watchman.ConfigWatchman;

@ConfigClass
@ConfigComment({
	"All configurations here are to control the class Config's behaviors.",
	"The configurations are considered as the entry of all other configurations."
})
public class Config {
	
	public static final Charset configFileEncoding = Charset.forName("UTF-8");

	private static final String keyPrefixFieldName = "configKeyPrefix";

	//public static boolean configurationMultipleFiles = true;
	private static String configurationFolder = null;
	private static String configurationFile = null;
	private static String configurationFileExtension = ".ini";
	
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
	
	// 
	public static List<Class<? extends ConfigWatchman>> configurationWatchmen = new ArrayList<>();
	// Once watchman is running, keep the instance in the map. In this way, adding new watchman into
	// configurationWatchmen will not affect the running watchman.
	private static Map<String, ConfigWatchman> watchmen = new ConcurrentHashMap<>();

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

	@ConfigComment({
		"Configure ignored public fields here for some classes to avoid unexpected modifications.",
		"Another way to ignore fields is using @ConfigIgnore annotation in source leve."
	})
	public static Map<Class<?>, ConfigFieldFilter> configurationFilters = new ConcurrentHashMap<>();
	@ConfigComment({
		"Containing those local-only configuration items, which are unique for each server/process.",
		"These items will be overrided by remote items.",
		"Especially for those sensitive items are not suitable for remote configuration center."
	})
	public static Map<Class<?>, Set<String>> configurationRemoteIgnoringFilters = new ConcurrentHashMap<>();
	
	@ConfigComment({
		"Codec for some known data. Codec can be used for encrypting some sensitive value in configuration file.",
		"As codec instance will be re-used over and over, codec implementation should be state-less."
	})
	@ConfigNotNull
	@ConfigLength(min = 3, max = 32, depth = 1)
	@ConfigPattern("([a-zA-Z][a-zA-Z0-9]+)")
	public static Map<String, IConfigCodec<?>> configurationCodecs = new ConcurrentHashMap<>();
	
	static {
		configurationWatchmen.add(ConfigFileWatchman.class);
		
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

	private static volatile ClassLoader configurationLoader = null;
	
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
				if (!allConfigs.containsValue(clazz)) register(clazz);
			}
		}
		String[] configPakages = configurationPackages;
		if (configPakages != null) {
			for (String pkg : configPakages) {
				if (!allConfigs.containsKey(pkg)) register(pkg);
			}
		}
	}

	/* Recommend using this method in other applications */
	public static void register(Object cfg) {
		if (cfg instanceof Class<?>) {
			registerUpdatingListener((Class<?>) cfg);
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
				registerUpdatingListener(clz);
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
				registerUpdatingListener(clz);
			}
			allConfigs.put(starredPkgName, Config.class);
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	protected static void registerClass(Class<?> clazz) {
		registerUpdatingListener(clazz);
	}

	// May dependent on disk IO
	public static void registerUpdatingListener(Class<?> clazz) {
		if (clazz == null) return;
		boolean updating = allConfigs.put(clazz.getName(), clazz) != clazz;
		if (!updating) return;
		orderedConfigs.add(clazz);
		initializedTime = System.currentTimeMillis();
		commandLineParser.parseConfiguration(clazz, ConfigParser.FLAG_UPDATE, null);
		// Load watchman classes and start loadConfigClass task
		List<Class<? extends ConfigWatchman>> syncClasses = configurationWatchmen;
		if (syncClasses != null && syncClasses.size() > 0) {
			for (Class<? extends ConfigWatchman> clz : syncClasses) {
				if (clz != null) {
					try {
						ConfigWatchman watchman = watchmen.get(clz.getName());
						if (watchman == null) {
							watchman = clz.newInstance();
							watchmen.put(clz.getName(), watchman);
						}
						watchman.watchConfigClass(clazz);
					} catch (Exception e) {
						//e.printStackTrace();
					}
				}
			}
		}
		if (configurationLogging) {
			System.out.println("[Config] Registering configuration class " + clazz.getName() + " done.");
		}
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
		if (ext == null) ext = configurationFileExtension;
		return ext;
	}
	
	public static String getConfigurationMainFile() {
		return configurationFile;
	}
	
	public static String getConfigurationMainExtension() {
		return configurationFileExtension;
	}
	
	public static String getConfigurationFolder() {
		String folder = configurationFolder;
		if (folder == null) {
			folder = configurationFile;
			File folderFile = new File(folder);
			if (folderFile.isFile() || !folderFile.exists() || folder.endsWith(configurationFileExtension)) {
				folder = folderFile.getParent();
			}
		}
		return folder;
	}

	public static File getConfigruationFile(String keyPrefix) {
		String folder = getConfigurationFolder();
		File file = null;
		List<String> exts = Config.configurationScanningExtensions;
		if (exts == null || exts.size() == 0) {
			exts = Arrays.asList(new String[] { ".ini" });
		}
		if (exts != null) {
			for (String ext : exts) {
				if (ext != null && ext.length() > 0 && ext.charAt(0) == '.') {
					file = new File(folder, Config.parseFilePath(keyPrefix + ext));
					if (file.exists()) {
						return file;
					}
				}
			}
		}
		return new File(folder, Config.parseFilePath(keyPrefix + exts.iterator().next()));
	}
	
	@Deprecated
	public static void initialize(String configPath) {
		initialize(new String[] { configPath });
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
			argumentsParser.parseConfiguration(Config.class, ConfigParser.FLAG_UPDATE, null);
			Class<?>[] configs = Config.getAllConfigurations();
			for (int i = 0; i < configs.length; i++) {
				argumentsParser.parseConfiguration(configs[i], ConfigParser.FLAG_UPDATE, null);
			}
		} while (argumentsParser != commandLineParser); // commandLineParser may be updated by the parser itself!
		
		
		if (retArgs != null && retArgs.length > 0) {
			String firstArg = retArgs[0];
			if (firstArg != null && firstArg.length() > 0) {
				File f = new File(firstArg);
				if (f.exists()) {
					if (f.isDirectory()) {
						configurationFolder = firstArg;
						f = getConfigruationFile("config");
						configurationFolder = f.getAbsolutePath();
					} else { // File
						configurationFolder = f.getParentFile().getAbsolutePath();
						//configurationFile = firstArg;
					}
					configurationFile = f.getAbsolutePath();
					String name = f.getName();
					int idx = name.lastIndexOf('.');
					if (idx != -1) {
						configurationFileExtension = name.substring(idx);
					}
					// Shift the first argument
					String[] newRetArgs = new String[retArgs.length - 1];
					System.arraycopy(retArgs, 1, newRetArgs, 0, newRetArgs.length);
					retArgs = newRetArgs;
				} else {
					List<String> allExtensions = Config.configurationScanningExtensions;
					if (allExtensions != null) {
						for (String ext : allExtensions) {
							if (firstArg.endsWith(ext)) {
								configurationFolder = f.getParent();
								configurationFile = firstArg;
								configurationFileExtension = ext;
								break;
							}
						}
					}
				}
			}
		}
		String configPath = configurationFile;
		if (configPath == null) {
			configPath = configurationFile = "./config.ini";
			if (configurationFolder == null) {
				configurationFolder = "./";
			}
		}
		int idx = configPath.lastIndexOf('.');
		if (idx != -1) {
			String ext = configPath.substring(idx + 1);
			if (ext.length() > 0) {
				configurationFileExtension = configPath.substring(idx);
			}
		}
		
		loadWatchmen();
		
		registerUpdatingListener(SecurityConfig.class);
		
		update(null);
		
		initializedTime = System.currentTimeMillis();
		if (configurationLogging) {
			System.out.println("[Config] Configuration initialized.");
		}
		initializationFinished = true;
		
		if (retArgs != null && retArgs.length > 0) {
			String actionStr = retArgs[0];
			if (actionStr.startsWith("--run:")) {
				actionStr = actionStr.substring(6);
				if ("generator".equals(actionStr)) {
					GeneratorKit.run(retArgs, 1);
				} else if ("encoder".equals(actionStr)) {
					CodecKit.run(retArgs, 1, false);
				} else if ("decoder".equals(actionStr)) {
					CodecKit.run(retArgs, 1, true);
				} else if ("usage".equals(actionStr)) {
					printUsage();
				} else if ("validator".equals(actionStr)) {
					if (ValidatorKit.validateAllConfigurations()) {
						System.out.println("[INFO] Configuration files are OK.");
					} else {
						System.out.println("[ERROR] Configuration validation failed!");
					}
				} else if ("synchronizer".equals(actionStr)) {
					SynchronizerKit.run(retArgs, 1);
				} else if ("class".equals(actionStr)) {
					// --run:class xxx.xxxxx.XXX 
					// By this way, we make all public configuration classes with public static fields configurable
					// without modifying the original sources or jars.
					if (retArgs.length == 1 || retArgs[1] == null || retArgs[1].length() == 0) {
						printUsage();
					} else {
						try {
							Class<?> clazz = Class.forName(retArgs[1]);
							Method method = clazz.getMethod("main", new Class[] { String[].class });
							if (method != null && (method.getModifiers() & Modifier.STATIC) != 0) { //
								String[] newRetArgs = new String[retArgs.length - 1];
								System.arraycopy(retArgs, 1, newRetArgs, 0, newRetArgs.length);
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
	
	public static void loadWatchmen() {
		// Load watchman classes and start synchronizing task
		Set<Class<?>> loadedWatchmen = new HashSet<Class<?>>();
		int loopLoadings = 5;
		List<Class<? extends ConfigWatchman>> syncClasses = configurationWatchmen;
		while (syncClasses != null && syncClasses.size() > 0 && loopLoadings-- > 0) {
			// by default, there is a watchman: im.webuzz.config.ConfigFileWatchman
			for (Class<? extends ConfigWatchman> clazz : syncClasses) {
				if (loadedWatchmen.contains(clazz)) continue;
				if (clazz != null) {
					try {
						ConfigWatchman watchman = watchmen.get(clazz.getName());
						if (watchman == null) {
							watchman = clazz.newInstance();
							watchmen.put(clazz.getName(), watchman);
						}
						watchman.startWatchman();
						if (configurationLogging) {
							System.out.println("[Config] Task " + clazz + "#startWatchman done.");
						}
						/*
						Method method = clazz.getMethod("startWatchman", new Class[0]);
						if (method != null && (method.getModifiers() & Modifier.STATIC) != 0) {
							method.invoke(null, new Object[0]);
							if (configurationLogging) {
								System.out.println("[Config] Task " + clazz + "#startWatchman done.");
							}
						}
						//*/
					} catch (Exception e) {
						e.printStackTrace();
					}
					loadedWatchmen.add(clazz);
				}
			}
			List<Class<? extends ConfigWatchman>> updatedClasses = configurationWatchmen;
			if (DeepComparator.listDeepEquals(configurationWatchmen, syncClasses)) break;
			// Config.configurationWatchmen may be updated from file
			syncClasses = updatedClasses;
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
		/*
		Collection<Class<?>> values = allConfigs.values();
		List<Class<?>> uniqClasses = new ArrayList<Class<?>>(values.size());
		uniqClasses.add(Config.class); // Add this Config.class by default
		for (Class<?> clz : values) {
			if (uniqClasses.contains(clz)) continue;
			uniqClasses.add(clz);
		}
		return uniqClasses.toArray(new Class<?>[uniqClasses.size()]);
		//*/
	}
	
	public static ClassLoader getConfigurationClassLoader() {
		return configurationLoader;
	}

	public static void setConfigurationClassLoader(ClassLoader loader) {
		Config.configurationLoader = loader;
	}

	public static Class<?> loadConfigurationClass(String clazz) {
		return loadConfigurationClass(clazz, null);
	}
	public static Class<?> loadConfigurationClass(String clazz, StringBuilder errBuilder) {
		Class<?> clz = loadedClasses.get(clazz);
		if (clz != null) return clz;
		if (configurationLoader != null) {
			try {
				clz = configurationLoader.loadClass(clazz);
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

	/**
	 * Remove "/../" or "/./" in path.
	 * 
	 * @param path
	 * @return file path without "/../" or "/./"
	 */
	public static String parseFilePath(String path) {
		int length = path.length();
		if (length == 0) return path;
		boolean slashStarted = path.charAt(0) == '/';
		boolean slashEnded = length > 1 && path.charAt(length - 1) == '/';
		
		int idxBegin = slashStarted ? 1 : 0;
		int idxEnd = slashEnded ? length - 1 : length;
		if (idxEnd - idxBegin <= 0) {
			return "";
		}
		String[] segments = path.substring(idxBegin, idxEnd).split("\\/|\\\\");
		int count = segments.length + 1;
		for (int i = 0; i < segments.length; i++) {
			count--;
			if (count < 0) {
				System.out.println("[ERROR] Failed to fix the URL: " + path);
				break;
			}
			String segment = segments[i];
			if (segment == null) break;
			if (segments[i].equals("..")) {
				int shift = 2;
				if (i > 0) {
					segments[i - 1] = null;
					segments[i] = null;
					if (i + 1 > segments.length - 1 || segments[i + 1] == null) {
						slashEnded = true;
					}
				} else {
					segments[i] = null;
					shift = 1;
				}
				for (int j = i - shift + 1; j < segments.length - shift; j++) {
					String s = segments[j + shift];
					segments[j] = s;
					if (j == segments.length - shift - 1 || s == null) {
						if (shift == 1) {
							segments[j + 1] = null;
						} else { // shift == 2
							segments[j + 1] = null;
							segments[j + 2] = null;
						}
					}
				}
				i -= shift;
			} else if (segments[i].equals(".")) {
				segments[i] = null;
				if (i + 1 > segments.length - 1 || segments[i + 1] == null) {
					slashEnded = true;
				}
				for (int j = i; j < segments.length - 1; j++) {
					String s = segments[j + 1];
					segments[j] = s;
					if (j == segments.length - 2) {
						segments[j + 1] = null;
					}
				}
				i--;
			}
		}
		StringBuilder builder = new StringBuilder(length);
		int lastLength = 0;
		boolean needSlash = true;
		for (int i = 0; i < segments.length; i++) {
			String segment = segments[i];
			if (segment == null) break;
			if (needSlash && builder.length() > 0) {
				builder.append("/");
			}
			builder.append(segment);
			lastLength = segment.length();
			needSlash = lastLength > 0;
		}
		//if (lastLength == 0 || slashEnded) {
		//	builder.append("/");
		//}
		return builder.toString();
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
				return parseFilePath(keyPrefix);
			}
		}
		return null;
	}

	public static void main(String[] args) {
		initialize(args);
	}
}
