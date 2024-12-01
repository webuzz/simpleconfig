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

import im.webuzz.config.agent.ConfigAgent;
import im.webuzz.config.annotations.ConfigClass;
import im.webuzz.config.annotations.ConfigComment;
import im.webuzz.config.annotations.ConfigKeyPrefix;
import im.webuzz.config.annotations.ConfigLength;
import im.webuzz.config.annotations.ConfigNotEmpty;
import im.webuzz.config.annotations.ConfigNotNull;
import im.webuzz.config.annotations.ConfigPattern;
import im.webuzz.config.annotations.ConfigRange;
import im.webuzz.config.codecs.AESCodec;
import im.webuzz.config.codecs.Base64Codec;
import im.webuzz.config.codecs.Bytes64Codec;
import im.webuzz.config.codecs.BytesAESCodec;
import im.webuzz.config.codecs.SecretCodec;

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
			".js", ".json",
			".conf", ".config", ".cfg",
			".props", ".properties",
			".xml",
			".txt"
		});
	
	public static Class<?>[] configurationWatchmen = new Class<?>[] { ConfigFileWatchman.class };

	@ConfigComment({
		"Array of configuration class names, listing all classes which are to be configured via files.",
		"e.g im.webuzz.config.Config;im.webuzz.config.web.WebConfig"
	})
	public static String[] configurationClasses = null;

	public static Map<Class<?>, ConfigFieldFilter> configurationFilters = new ConcurrentHashMap<>();
	
	@ConfigNotNull
	@ConfigPattern("([a-zA-Z0-9]+)")
	public static Map<String, Class<? extends IConfigConverter>> converterExtensions = new ConcurrentHashMap<>();
	protected static Map<String, IConfigConverter> converters = new ConcurrentHashMap<>();
	
	@ConfigNotNull
	@ConfigLength(min = 3, max = 32, depth = 1)
	@ConfigPattern("([a-zA-Z][a-zA-Z0-9]+)")
	public static Map<String, Class<? extends IConfigCodec<?>>> configurationCodecs = new ConcurrentHashMap<>();
	protected static Map<String, IConfigCodec<?>> codecs = new ConcurrentHashMap<>();
	
	static {
		converterExtensions.put("js", ConfigJSParser.class);
		converterExtensions.put("xml", ConfigXMLParser.class);

		configurationCodecs.put("secret", SecretCodec.class);
		//configurationCodecs.put("Secret", SecretCodec.class);
		configurationCodecs.put("aes", AESCodec.class);
		//configurationCodecs.put("AES", AESCodec.class);
		configurationCodecs.put("bytesaes", BytesAESCodec.class);
		//configurationCodecs.put("BytesAES", BytesAESCodec.class);
		configurationCodecs.put("base64", Base64Codec.class);
		//configurationCodecs.put("Base64", Base64Codec.class);
		configurationCodecs.put("bytes64", Bytes64Codec.class);
		//configurationCodecs.put("Bytes64", Bytes64Codec.class);
	}
	
	@ConfigRange(min = 1, max = 20)
	public static int configurationMapSearchingDots = 10;
	
	public static boolean configurationLogging = true;
	
	public static Class<?> configurationAlarmer = null;
	public static boolean exitInitializingOnInvalidItems = true;
	public static boolean skipUpdatingWithInvalidItems = true;

	
	protected static volatile long initializedTime = 0;
	protected static boolean initializationFinished = false;

	private static Map<String, Class<?>> allConfigs = new ConcurrentHashMap<>();
	private static Map<Class<?>, String> configExtensions = new ConcurrentHashMap<>();

	private static volatile ClassLoader configurationLoader = null;
	
	// Keep not found classes, if next time trying to load these classes, do not print exceptions
	private static Set<String> notFoundClasses = Collections.newSetFromMap(new ConcurrentHashMap<>());
	private static Map<String, Class<?>> loadedClasses = new ConcurrentHashMap<>(50);
	
	private static Properties argProps = null;

	/*
	 * In case configurations got updated from file, try to add class into configuration system
	 */
	public static void update(Properties prop) {
		String[] configClasses = configurationClasses;
		if (configClasses == null) return;
		for (int i = 0; i < configClasses.length; i++) {
			String clazz = configClasses[i];
			if (!allConfigs.containsKey(clazz)) {
				register(clazz);
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
		initializedTime = System.currentTimeMillis();
		if (argProps != null && argProps.size() > 0) {
			ConfigINIParser.parseConfiguration(argProps, clazz, true, true, true);
		}
		// Load watchman classes and start loadConfigClass task
		Class<?>[] syncClasses = configurationWatchmen;
		if (syncClasses != null && syncClasses.length > 0) {
			for (int i = 0; i < syncClasses.length; i++) {
				Class<?> clz = syncClasses[i];
				if (clz != null) {
					try {
						Method method = clz.getMethod("loadConfigClass", Class.class);
						if (method != null && (method.getModifiers() & Modifier.STATIC) != 0) {
							method.invoke(null, clazz);
						}
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
	protected static boolean recordConfigExtension(Class<?> configClass, String configExtension) {
		String existedConfigExt = configExtensions.put(configClass, configExtension);
		if (existedConfigExt != null && !existedConfigExt.equals(configExtension)) {
			return true;
		}
		return false;
	}
	
	protected static String getConfigExtension(Class<?> configClass) {
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
		//allConfigs.put(SecurityConfig.class.getName(), SecurityConfig.class);
		argProps = new Properties();
		String[] retArgs = ConfigINIParser.parseArguments(args, argProps);

		if (argProps.size() > 0) {
			ConfigINIParser.parseConfiguration(argProps, Config.class, false, true, true);
			Class<?>[] configs = Config.getAllConfigurations();
			for (int i = 0; i < configs.length; i++) {
				ConfigINIParser.parseConfiguration(argProps, configs[i], true, true, true);
			}
		}
		
		if (retArgs != null && retArgs.length > 0) {
			String firstArg = retArgs[0];
			if (firstArg != null && firstArg.length() > 0) {
				File f = new File(firstArg);
				if (f.exists()) {
					if (f.isDirectory()) {
						configurationFolder = firstArg;
						File cfgFile = new File(f, "config.js");
						if (!cfgFile.exists()) {
							cfgFile = new File(f, "config.ini");
						}
						f = cfgFile;
						configurationFolder = f.getAbsolutePath();
						configurationFile = cfgFile.getAbsolutePath();
					} else { // File
						configurationFolder = f.getParentFile().getAbsolutePath();
						configurationFile = firstArg;
					}
					String name = f.getName();
					int idx = name.lastIndexOf('.');
					if (idx != -1) {
						configurationFileExtension = name.substring(idx);
					}
					// Shift the first argument
					String[] newRetArgs = new String[retArgs.length - 1];
					System.arraycopy(retArgs, 1, newRetArgs, 0, newRetArgs.length);
					retArgs = newRetArgs;
				}
			}
		}
		String configPath = configurationFile;
		if (configPath == null) {
			configPath = configurationFile = "./config.ini";
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
		
		String[] configClasses = configurationClasses;
		if (configClasses != null) {
			for (int i = 0; i < configClasses.length; i++) {
				String clazz = configClasses[i];
				if (!allConfigs.containsKey(clazz)) {
					// Add new configuration class may trigger file reading, might be IO blocking
					register(clazz);
				}
			}
		}
		
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
					ConfigGenerator.run(retArgs, 1);
				} else if ("checker".equals(actionStr)) {
					
				} else if ("synchronizer".equals(actionStr)) {
					ConfigAgent.run(retArgs, 1);
				} else if ("secretkit".equals(actionStr)) {
					SecurityKit.run(retArgs, 1);
				} else if ("usage".equals(actionStr)) {
					printUsage();
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
				+ " [--run:<generator | checker | synchronizer | secretkit | usage>] [...]");
		System.out.println();
		System.out.println("For argument --c:xxx=###, the following formats are supported:");
		System.out.println("\t--c:port=6173");
		System.out.println("\t--config:port=6173");
		System.out.println("\t--c-port=6173");
		System.out.println("\t--config-port=6173");
		System.out.println();
		System.out.println("For argument --run:xxx, the following actions are supported:");
		System.out.println("\t--run:generator\tTo generate configuration files");
		System.out.println("\t--run:checker\tTo verify configuration files");
		System.out.println("\t--run:synchronizer\tTo synchronize local configuration files from remote server");
		System.out.println("\t--run:secretkit\tTo encrypt or decrypt a password or a sensitive string");
		System.out.println("\t--run:usage\tPrint this usage");
	}

	public static boolean reportErrorToContinue(String msg) {
		String[] msgs = msg.split("(\r\n|\n|\r)");
		for (int i = 0; i < msgs.length; i++) {
			System.out.println("[ERROR] " + msgs[i]);
		}
		if (configurationAlarmer != null) {
			// TODO: Use alarm to send an alert to the operator 
		}
		if (initializationFinished) {
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
	
	protected static void loadWatchmen() {
		// Load watchman classes and start synchronizing task
		Set<Class<?>> loadedWatchmen = new HashSet<Class<?>>();
		int loopLoadings = 5;
		Class<?>[] syncClasses = configurationWatchmen;
		while (syncClasses != null && syncClasses.length > 0 && loopLoadings-- > 0) {
			// by default, there is a watchman: im.webuzz.config.ConfigFileWatchman
			for (int i = 0; i < syncClasses.length; i++) {
				Class<?> clazz = syncClasses[i];
				if (loadedWatchmen.contains(clazz)) continue;
				if (clazz != null) {
					try {
						Method method = clazz.getMethod("startWatchman", new Class[0]);
						if (method != null && (method.getModifiers() & Modifier.STATIC) != 0) {
							method.invoke(null, new Object[0]);
							if (configurationLogging) {
								System.out.println("[Config] Task " + clazz + "#startWatchman done.");
							}
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
					loadedWatchmen.add(clazz);
				}
			}
			Class<?>[] updatedClasses = configurationWatchmen;
			if (Arrays.equals(updatedClasses, syncClasses)) break;
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
		Collection<Class<?>> values = allConfigs.values();
		List<Class<?>> uniqClasses = new ArrayList<Class<?>>(values.size());
		uniqClasses.add(Config.class); // Add this Config.class by default
		for (Class<?> clz : values) {
			if (uniqClasses.contains(clz)) continue;
			uniqClasses.add(clz);
		}
		return uniqClasses.toArray(new Class<?>[uniqClasses.size()]);
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

	/**
	 * If secret is encrypted and security decrypter is configured, try decrypt
	 * given secret to raw secret.
	 * 
	 * @param secret
	 * @return
	 */
	@Deprecated
	public static String parseSecret(final String secret) {
		// TODO: Use codecs instead od decrypter
		if (secret == null || secret.length() == 0) return secret;
		Object result = ConfigINIParser.decodeRaw("secret", secret);
		if (result instanceof String) {
			//System.out.println("Decrypted: " + secret + " ==> " + result);
			return (String) result;
		}
		return secret;
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
		} catch (SecurityException e) {
		} catch (NoSuchFieldException e) {
		} catch (IllegalArgumentException e) {
		} catch (IllegalAccessException e) {
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

}
