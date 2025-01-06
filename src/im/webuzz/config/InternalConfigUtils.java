package im.webuzz.config;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarFile;

import im.webuzz.config.annotation.ConfigClass;
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

	@SuppressWarnings("unchecked")
	public static List<Annotation> getAllKnownAnnotations(AnnotatedElement el, Class<?>[] containerAnnTypes) {
		List<Annotation> anns = new ArrayList<Annotation>();
		Map<Class<?>, Map<String, Annotation[]>> typeAnns = Config.configurationAnnotations;
		Class<?> clz = null;
		String name = null;
		if (el instanceof Field) {
			Field field = (Field) el;
			clz = field.getDeclaringClass();
			name = field.getName();
		} else if (el instanceof Class) {
			clz = (Class<?>) el;
			name = "class";
		} else {
			return anns;
		}
		Map<String, Annotation[]> fieldAnns = typeAnns == null ? null : typeAnns.get(clz);
		Annotation[] configuredAnns = null;
		boolean annOverridden = false;
		if (fieldAnns != null) {
			configuredAnns = fieldAnns.get(name);
			if (configuredAnns != null && configuredAnns.length > 0) {
				// Check the first annotation to see if Annotation declared in the source
				// file should be discarded or not 
				annOverridden = configuredAnns[0] != null && configuredAnns[0] instanceof ConfigOverridden;
			}
		}
		if (!annOverridden) {
			Annotation[] annotations = el.getAnnotations();
			if (annotations != null) {
				for (Annotation ann : annotations) {
					boolean container = false;
					if (containerAnnTypes != null) {
						for (Class<?> caType : containerAnnTypes) {
							if (caType.isAssignableFrom(ann.annotationType())) {
								container = true;
								break;
							}
						}
					}
					if (!container) anns.add(ann);
				}
				//anns.addAll(Arrays.asList(annotations));
			}
			if (containerAnnTypes != null) {
				for (Class<?> containerType : containerAnnTypes) {
					annotations = el.getAnnotationsByType((Class<? extends Annotation>)containerType);
					if (annotations != null) anns.addAll(Arrays.asList(annotations));
				}
			}
		}
		if (configuredAnns != null) anns.addAll(Arrays.asList(configuredAnns));
		return anns;
	}

	@SuppressWarnings("unchecked")
	public static  <T extends Annotation> T getSingleKnownAnnotations(AnnotatedElement el, Class<T> annType) {
		Map<Class<?>, Map<String, Annotation[]>> typeAnns = Config.configurationAnnotations;
		Class<?> clz = null;
		String name = null;
		if (el instanceof Field) {
			Field field = (Field) el;
			clz = field.getDeclaringClass();
			name = field.getName();
		} else if (el instanceof Class) {
			clz = (Class<?>) el;
			name = "class";
		} else {
			return null;
		}
		Map<String, Annotation[]> fieldAnns = typeAnns == null ? null : typeAnns.get(clz);
		Annotation[] configuredAnns = null;
		boolean annOverridden = false;
		if (fieldAnns != null) {
			configuredAnns = fieldAnns.get(name);
			if (configuredAnns != null && configuredAnns.length > 0) {
				// Check the first annotation to see if Annotation declared in the source
				// file should be discarded or not 
				annOverridden = configuredAnns[0] != null && configuredAnns[0] instanceof ConfigOverridden;
			}
		}
		if (!annOverridden) {
			Annotation annotation = el.getAnnotation(annType);
			if (annotation != null) return (T) annotation;
		}
		if (configuredAnns != null) {
			for (Annotation ann : configuredAnns) {
				if (annType.isAssignableFrom(ann.annotationType())) return (T) ann;
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

	public static boolean isFiltered(Field field, Map<String, Annotation[]> fieldAnns,
			boolean filterStatic, boolean filterLocalOnly) {
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
		int filteringModifiers = Modifier.PUBLIC;
		boolean typeAnnOverridden = false;
		Annotation[] typeAnns = fieldAnns == null ? null : fieldAnns.get("class");
		if (typeAnns != null && typeAnns.length > 0) {
			// Check the first annotation to see if Annotation declared in the source
			// file should be discarded or not 
			typeAnnOverridden = typeAnns[0] != null && typeAnns[0] instanceof ConfigOverridden;
		}
		if (!typeAnnOverridden && filterLocalOnly
				&& field.getDeclaringClass().getAnnotation(ConfigLocalOnly.class) != null) {
			return true;
		}
		if (typeAnns != null) {
			for (Annotation ann : typeAnns) {
				Class<? extends Annotation> annClass = ann.getClass();
				if (filterLocalOnly && ConfigLocalOnly.class.isAssignableFrom(annClass)) {
					return true;
				}
			}
		}
		
		boolean annOverridden = false;
		Annotation[] anns = fieldAnns == null ? null : fieldAnns.get(field.getName());
		if (anns != null && anns.length > 0) {
			// Check the first annotation to see if Annotation declared in the source
			// file should be discarded or not 
			annOverridden = anns[0] != null && anns[0] instanceof ConfigOverridden;
		}
		if (!annOverridden) {
			if (field.getAnnotation(ConfigIgnored.class) != null
					|| (filterLocalOnly && field.getAnnotation(ConfigLocalOnly.class) != null)) {
				return true;
			}
			if (field.getAnnotation(Configurable.class) != null) {
				if ((modifiers & Modifier.PUBLIC) == 0) field.setAccessible(true);
				filteringModifiers = 0;
			}
		}
		if (anns != null) {
			for (Annotation ann : anns) {
				Class<? extends Annotation> annClass = ann.getClass();
				if (ConfigIgnored.class.isAssignableFrom(annClass)
						|| (filterLocalOnly && ConfigLocalOnly.class.isAssignableFrom(annClass))) {
					return true;
				}
				if (Configurable.class.isAssignableFrom(annClass)) {
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
						f = getConfigFile(configFolderPath, defaultConfigName, null);
					}
					folderBuilder.append(configFolderPath).append(File.separatorChar);
				} else { // File
					// ./etc/piled.ini
					String folder = f.getParent();
					if (folder == null) folder = ".";
					folderBuilder.append(folder).append(File.separatorChar);
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
		getConfigFile(folderBuilder.toString(), nameBuilder.toString(), extBuilder);
		return indexOffset;
	}

	public static File getConfigFile(String keyPrefix, StringBuilder extBuilder) {
		String folder = Config.getConfigFolder();
		return getConfigFile(folder, keyPrefix, extBuilder);
	}

	public static File getConfigFile(String folder, String keyPrefix, StringBuilder extBuilder) {
		String mainExt = Config.configMainExtension;
		File fileMainExt = new File(folder, FileUtils.parseFilePath(keyPrefix + mainExt));
		if (fileMainExt.exists()) {
			if (extBuilder != null) extBuilder.append(mainExt);
			return fileMainExt;
		}
		String firstExt = null;
		for (String ext : Config.configurationScanningExtensions) {
			if (ext != null && ext.length() > 0 && ext.charAt(0) == '.') {
				if (firstExt == null) firstExt = ext;
				if (ext.equals(mainExt)) continue;
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
		while ((strategyLoader == null || strategyLoader.getClass() != Config.configurationLoader)) {
			ConfigLoader newLoader = null;
			try {
				newLoader = Config.configurationLoader.newInstance();
			} catch (Exception e) {
				e.printStackTrace();
				return;
			}
			ConfigLoader oldLoader = strategyLoader;
			if (oldLoader != null && Config.configurationLogging) {
				System.out.println("[Config:INFO] Switching configuration loader from "
						+ oldLoader.getClass().getName() + " to "
						+ newLoader.getClass().getName());
			}
			Class<?>[] configClasses = newLoader.prerequisites();
			if (configClasses != null && configClasses.length > 0) {
				// Here will trigger loading new configuration loader's prerequisites via old configuration loader
				for (Class<?> clz : configClasses) {
					Config.registerClass(clz);
				}
			}
			if (oldLoader != null) oldLoader.stop();
			strategyLoader = newLoader;
			strategyLoader.start();
			if (loopLoadings-- <= 0) break;
		}
		if (loopLoadings <= 0) {
			System.out.println("[ERROR] Switching configuration loader results in too many loops (5).");
		}
	}

	public static void checkStrategyLoader() {
		if (isInitializationFinished() && ((strategyLoader == null && Config.configurationLoader != null)
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

	/**
	 * Scans the given package for classes annotated with the specified annotation.
	 *
	 * @param packageName The package to scan.
	 * @param annotation  The annotation to look for.
	 * @return A list of classes annotated with the specified annotation.
	 * @throws IOException If an error occurs while accessing resources.
	 * @throws ClassNotFoundException If a class cannot be loaded.
	 */
	public static List<Class<?>> getConfigClassesInPackage(String packageName)
			throws IOException, ClassNotFoundException {
		List<Class<?>> annotatedClasses = new ArrayList<>();
		String packagePath = packageName.replace('.', '/');
		ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
	
		// Get all resources corresponding to the package path
		Enumeration<URL> resources = classLoader.getResources(packagePath);
	
		while (resources.hasMoreElements()) {
			URL resource = resources.nextElement();
			String protocol = resource.getProtocol();
	
			if ("file".equals(protocol)) {
				// If the resource is a directory in the file system
				File directory = new File(resource.getFile());
				if (directory.exists()) {
					findConfigClassesInDirectory(directory, packageName, annotatedClasses);
				}
			} else if ("jar".equals(protocol)) {
				// If the resource is a JAR file
				String jarPath = resource.getPath().substring(5, resource.getPath().indexOf("!"));
				try (JarFile jarFile = new JarFile(jarPath)) {
					findConfigClassesInJar(jarFile, packagePath, annotatedClasses);
				}
			}
		}
		return annotatedClasses;
	}

	/**
	 * Recursively scans a directory for classes annotated with the specified annotation.
	 *
	 * @param directory   The directory to scan.
	 * @param packageName The corresponding package name for the directory.
	 * @param annotation  The annotation to look for.
	 * @param annotatedClasses The list to store annotated classes.
	 * @throws ClassNotFoundException If a class cannot be loaded.
	 */
	private static void findConfigClassesInDirectory(File directory, String packageName,
			List<Class<?>> annotatedClasses)
			throws ClassNotFoundException {
		File[] files = directory.listFiles();
	
		if (files == null) {
			return;
		}
	
		for (File file : files) {
			if (file.isDirectory()) {
				// Recursively scan subdirectories
				findConfigClassesInDirectory(file, packageName + "." + file.getName(), annotatedClasses);
			} else if (file.getName().endsWith(".class")) {
				// Convert the file name to a class name and load the class
				String className = packageName + "." + file.getName().replace(".class", "");
				Class<?> clazz = Class.forName(className);
				// Check if the class is annotated with the target annotation
				if (isConfigClass(clazz)) annotatedClasses.add(clazz);
			}
		}
	}

	private static boolean isConfigClass(Class<?> clazz) {
		if (clazz.isAnnotationPresent(ConfigClass.class)) return true;
		try {
			Field f = clazz.getDeclaredField("configKeyPrefix");
			if (f == null) return false;
			int modifiers = f.getModifiers();
			int expectedModifiers = Modifier.PUBLIC | Modifier.FINAL | Modifier.STATIC;
			if ((modifiers & expectedModifiers) == expectedModifiers) return true;
		} catch (Exception e) {
			//e.printStackTrace();
		}
		return false;
	}
	
	/**
	 * Scans a JAR file for classes annotated with the specified annotation.
	 *
	 * @param jarFile     The JAR file to scan.
	 * @param packagePath The package path inside the JAR file.
	 * @param annotation  The annotation to look for.
	 * @param annotatedClasses The list to store annotated classes.
	 * @throws ClassNotFoundException If a class cannot be loaded.
	 */
	private static void findConfigClassesInJar(JarFile jarFile, String packagePath,
			List<Class<?>> annotatedClasses)
			throws ClassNotFoundException {
		jarFile.stream()
				.filter(entry -> !entry.isDirectory() && entry.getName().endsWith(".class"))
				.filter(entry -> entry.getName().startsWith(packagePath))
				.forEach(entry -> {
					String className = entry.getName().replace("/", ".").replace(".class", "");
					try {
						Class<?> clazz = Class.forName(className);
						// Check if the class is annotated with the target annotation
						if (isConfigClass(clazz)) annotatedClasses.add(clazz);
					} catch (ClassNotFoundException e) {
						e.printStackTrace();
					}
				});
	}

}
