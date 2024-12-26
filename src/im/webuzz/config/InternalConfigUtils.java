package im.webuzz.config;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import im.webuzz.config.annotation.ConfigIgnored;
import im.webuzz.config.annotation.ConfigLocalOnly;
import im.webuzz.config.annotation.ConfigOverridden;
import im.webuzz.config.annotation.Configurable;
import im.webuzz.config.loader.ConfigLoader;
import im.webuzz.config.util.FileUtils;

public class InternalConfigUtils {
	
	static ConfigLoader strategyLoader = null;

	// Keep not found classes, if next time trying to load these classes, do not print exceptions
	private static Set<String> notFoundClasses = Collections.newSetFromMap(new ConcurrentHashMap<>());
	private static Map<String, Class<?>> loadedClasses = new ConcurrentHashMap<>(50);

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

	public static int parseMainFile(String[] args, int indexOffset,
			StringBuilder folderBuilder, StringBuilder nameBuilder, StringBuilder extBuilder) {
		String defaultConfigName = Config.configMainName != null ? Config.configMainName : "config"; 
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
						f = InternalConfigUtils.getConfigFile(configFolderPath, defaultConfigName, null);
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
					if (Config.configurationScanningExtensions.contains(extBuilder.toString())) {
						return indexOffset + argsOffset;
					}
					// else // ./file.tgz // not supporting .tgz file
				}
				// ./etc/config
				System.out.println("[Config:FATAL] Unknown configuration file extension for " + name);
				System.exit(0);
				return -1;
			}
			// ./etc/new.ini
			// Configuration file does not exist! To check if it has a known configuration extension or not
			for (String ext : Config.configurationScanningExtensions) {
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
		InternalConfigUtils.getConfigFile(folderBuilder.toString(), nameBuilder.toString(), extBuilder);
		return indexOffset;
	}

	public static File getConfigFile(String keyPrefix, StringBuilder extBuilder) {
		String folder = Config.getConfigFolder();
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

	public static Class<?> loadConfigurationClass(String clazz) {
		return loadConfigurationClass(clazz, null);
	}

	public static Class<?> loadConfigurationClass(String clazz, StringBuilder errBuilder) {
		Class<?> clz = loadedClasses.get(clazz);
		if (clz != null) return clz;
		if (Config.classLoader != null) {
			try {
				clz = Config.classLoader.loadClass(clazz);
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

	protected static void initializeStrategyLoader() {
		int loopLoadings = 5;
		while ((InternalConfigUtils.strategyLoader == null || InternalConfigUtils.strategyLoader.getClass() != Config.configurationLoader)) {
			if (InternalConfigUtils.strategyLoader != null) {
				if (Config.configurationLogging) {
					System.out.println("[Config:INFO] Switching configuration loader from "
							+ InternalConfigUtils.strategyLoader.getClass().getName() + " to "
							+ Config.configurationLoader.getName());
				}
				InternalConfigUtils.strategyLoader.stop();
			}
			try {
				InternalConfigUtils.strategyLoader = Config.configurationLoader.newInstance();
			} catch (Exception e) {
				e.printStackTrace();
				return;
			}
			InternalConfigUtils.strategyLoader.start();
			if (loopLoadings-- <= 0) break;
		}
		if (loopLoadings <= 0) {
			System.out.println("[ERROR] Switching configuration loader results in too many loops (5).");
		}
	}

	public static void checkStrategyLoader() {
		if (InternalConfigUtils.isInitializationFinished() && ((strategyLoader == null && Config.configurationLoader != null)
				|| strategyLoader.getClass() != Config.configurationLoader)) {
			initializeStrategyLoader();
		}
	}

	// ****** The following fields are internal, non-configurable ****** //
	protected static volatile long initializedTime = 0;

	protected static boolean initializationFinished = false;

	public static boolean isInitializationFinished() {
		return initializationFinished && initializedTime > 0 && System.currentTimeMillis() - initializedTime > 3000;
	}

	private static Map<Class<?>, String> configExtensions = new ConcurrentHashMap<>();

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
		if (ext == null) ext = Config.configMainExtension;
		return ext;
	}

}
