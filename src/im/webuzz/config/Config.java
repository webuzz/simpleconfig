/*******************************************************************************
 * Copyright (c) 2010 - 2015 java2script.org, webuzz.im and others
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

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class Config {
	
	public static final Charset configFileEncoding = Charset.forName("UTF-8");

	protected static final String keyPrefixFieldName = "configKeyPrefix";
	
	protected static final String $null = "[null]";
	protected static final String $empty = "[empty]";
	protected static final String $array = "[array]";
	protected static final String $list = "[list]";
	protected static final String $set = "[set]";
	protected static final String $map = "[map]";
	protected static final String $object = "[object]";

	public static String configurationFile = null;
	public static String configurationFileExtension = ".ini";
	/**
	 * Supporting multiple configuration formats. The scanning extension order will be used
	 * to find configuration file and will decide the priority of which file will be used.
	 */
	public static String[] configurationScanningExtensions = new String[] { ".js", ".json", ".xml", ".ini", ".properties", ".props", ".config", ".conf", ".cfg", ".txt" };
	public static String configurationFolder = null;
	public static boolean configurationMultipleFiles = true;
	
	public static String[] configurationWatchmen = new String[] { "im.webuzz.config.ConfigFileWatchman" };
	public static String[] configurationClasses = null;
	public static Map<String, ConfigFieldFilter> configurationFilters = new ConcurrentHashMap<String, ConfigFieldFilter>();
	
	public static Map<String, String> converterExtensions = new ConcurrentHashMap<String, String>();
	protected static Map<String, IConfigConverter> converters = new ConcurrentHashMap<String, IConfigConverter>();
	
	public static int configurationMapSearchingDots = 10;

	/**
	 * The class for configured password decryption.
	 * If not set, password is in plain text. 
	 * 
	 * im.webuzz.config.security.SecurityKit is a reference implementation.
	 */
	public static String configurationSecurityDecrypter = "im.webuzz.config.security.SecurityKit";
	
	public static boolean configurationLogging = false;
	
	protected static Map<String, Class<?>> allConfigs = new ConcurrentHashMap<String, Class<?>>();
	
	private static volatile ClassLoader configurationLoader = null;
	
	private static volatile long initializedTime = 0;
	
	// Keep not found classes, if next time trying to load these classes, do not print exceptions
	private static Set<String> notFoundClasses = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
	
	// May dependent on disk IO
	public static void registerUpdatingListener(Class<?> clazz) {
		if (clazz == null) {
			return;
		}
		boolean updating = allConfigs.put(clazz.getName(), clazz) != clazz;
		if (updating) {
			initializedTime = System.currentTimeMillis();
			// Load watchman classes and start loadConfigClass task
			String[] syncClasses = configurationWatchmen;
			if (syncClasses != null && syncClasses.length > 0) {
				for (int i = 0; i < syncClasses.length; i++) {
					String syncClazz = syncClasses[i];
					Class<?> clz = loadConfigurationClass(syncClazz);
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
	}

	public static void initialize(String configPath) {
		initialize(configPath, null, true);
	}

	public static void initialize(String configPath, String extraFolder, boolean multipleConfigs) {
		configurationFile = configPath;
		configurationFolder = extraFolder;
		configurationMultipleFiles = multipleConfigs;
		initialize(); // call default initialize method
	}

	/**
	 * Need to set configurationFile and configurationExtraPath before calling this method.
	 */
	public static void initialize() {
		allConfigs.put(Config.class.getName(), Config.class);
		
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
		
		String[] configClasses = configurationClasses;
		if (configClasses != null) {
			for (int i = 0; i < configClasses.length; i++) {
				String clazz = configClasses[i];
				if (!allConfigs.containsKey(clazz)) {
					Class<?> clz = loadConfigurationClass(clazz);
					if (clz != null) {
						// Add new configuration class may trigger file reading, might be IO blocking
						registerUpdatingListener(clz);
					}
				}
			}
		}
		
		initializedTime = System.currentTimeMillis();
		if (configurationLogging) {
			System.out.println("[Config] Configuration initialized.");
		}
	}

	public static void loadWatchmen() {
		// Load watchman classes and start synchronizing task
		Set<String> loadedWatchmen = new HashSet<String>();
		int loopLoadings = 5;
		String[] syncClasses = configurationWatchmen;
		while (syncClasses != null && syncClasses.length > 0 && loopLoadings-- > 0) {
			// by default, there is a watchman: im.webuzz.config.ConfigFileWatchman
			for (int i = 0; i < syncClasses.length; i++) {
				String clazz = syncClasses[i];
				if (loadedWatchmen.contains(clazz)) {
					continue;
				}
				Class<?> clz = loadConfigurationClass(clazz);
				if (clz != null) {
					try {
						Method method = clz.getMethod("startWatchman", new Class[0]);
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
			String[] updatedClasses = configurationWatchmen;
			if (Arrays.equals(updatedClasses, syncClasses)) {
				break;
			}
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
		return values.toArray(new Class<?>[values.size()]);
	}
	
	public static ClassLoader getConfigurationClassLoader() {
		return configurationLoader;
	}

	public static void setConfigurationClassLoader(ClassLoader loader) {
		Config.configurationLoader = loader;
	}

	public static Class<?> loadConfigurationClass(String clazz) {
		Class<?> clz = null;
		if (configurationLoader != null) {
			try {
				clz = configurationLoader.loadClass(clazz);
			} catch (ClassNotFoundException e) {
				if (!notFoundClasses.contains(clazz)) {
					notFoundClasses.add(clazz);
					e.printStackTrace();
				}
			}
		}
		if (clz == null) {
			try {
				clz = Class.forName(clazz);
			} catch (ClassNotFoundException e) {
				if (!notFoundClasses.contains(clazz)) {
					notFoundClasses.add(clazz);
					e.printStackTrace();
				}
			}
		}
		return clz;
	}

	public static String getKeyPrefix(Class<?> clz) {
		Field f = null;
		try {
			f = clz.getDeclaredField(keyPrefixFieldName);
			if (f == null) {
				return null;
			}
			int modifiers = f.getModifiers();
			if (/*(modifiers & (Modifier.PUBLIC | Modifier.PROTECTED)) != 0
					&& */(modifiers & Modifier.STATIC) != 0
					&& (modifiers & Modifier.FINAL) != 0
					&& f.getType() == String.class) {
				if ((modifiers & Modifier.PUBLIC) == 0) {
					f.setAccessible(true);
				}
				String keyPrefix = (String) f.get(clz);
				if (keyPrefix.length() == 0) {
					keyPrefix = null;
				}
				return parseFilePath(keyPrefix);
			}
		} catch (SecurityException e) {
		} catch (NoSuchFieldException e) {
		} catch (IllegalArgumentException e) {
		} catch (IllegalAccessException e) {
		}
		ConfigKeyPrefix prefixAnn = clz.getAnnotation(ConfigKeyPrefix.class);
		if (prefixAnn != null) {
			String prefix = prefixAnn.value();
			if (prefix != null && prefix.length() > 0) {
				return parseFilePath(prefix);
			}
		}
		return null;
	}
	
	static Object parseTypedObject(Class<?>[] types, String p, String keyName, Properties prop) {
		Class<?> type = types[0];
		if (type == Integer.class) {
			return $null.equals(p) ? null : Integer.valueOf(p);
		} else if (type == String.class) {
			return $null.equals(p) ? null : parseString(p);
		} else if (type == Boolean.class) {
			return $null.equals(p) ? null : Boolean.valueOf(p);
		} else if (type == Long.class) {
			return $null.equals(p) ? null : Long.valueOf(p);
		} else if (type == int[].class) {
			return parseIntegerArray(p, keyName, prop);
		} else if (type == long[].class) {
			return parseLongArray(p, keyName, prop);
		} else if (type == boolean[].class) {
			return parseBooleanArray(p, keyName, prop);
		} else if (type == double[].class) {
			return parseDoubleArray(p, keyName, prop);
		} else if (type == float[].class) {
			return parseFloatArray(p, keyName, prop);
		} else if (type == short[].class) {
			return parseShortArray(p, keyName, prop);
		} else if (type == byte[].class) {
			return parseByteArray(p, keyName, prop);
		} else if (type == char[].class) {
			return parseCharArray(p, keyName, prop);
		} else if (type.isArray()) {
			Class<?> compType = type.getComponentType();
			Class<?>[] nextValueTypes = null;
			if (types.length > 1) {
				nextValueTypes = new Class<?>[types.length - 1];
				System.arraycopy(types, 1, nextValueTypes, 0, nextValueTypes.length);
			} else {
				nextValueTypes = new Class<?>[] { compType };
			}
			return parseArray(p, nextValueTypes, keyName, prop);
		} else if (type == Double.class) {
			return $null.equals(p) ? null : Double.valueOf(p);
		} else if (type == Float.class) {
			return $null.equals(p) ? null : Float.valueOf(p);
		} else if (type == Short.class) {
			return $null.equals(p) ? null : Short.valueOf(p);
		} else if (type == Byte.class) {
			return $null.equals(p) ? null : Byte.valueOf(p);
		} else if (type == Character.class) {
			return $null.equals(p) ? null : Character.valueOf(p.charAt(0));
		} else if (type == List.class || type == Set.class || type == Map.class) {
			Class<?>[] nextValueTypes = null;
			if (types.length > 1) {
				nextValueTypes = new Class<?>[types.length - 1];
				System.arraycopy(types, 1, nextValueTypes, 0, nextValueTypes.length);
			}
			if (type == List.class) { // List<Object>
				return parseList(p, nextValueTypes, keyName, prop);
			} else if (type == Set.class) { // Set<Object>
				return parseSet(p, nextValueTypes, keyName, prop);
			} else { // if (type == Map.class) { // Map<String, Object>
				return parseMap(p, nextValueTypes, keyName, prop);
			}
		} else {
			return parseObject(p, type, keyName, prop);
		}
	}

	static Object parseObject(String p, Class<?> type, String keyName, Properties prop) {
		if ($null.equals(p) || p == null) {
			return null;
		}
		Object obj = null;
		try {
			obj = type.newInstance();
		} catch (Exception e) {
			e.printStackTrace();
		}
		if ($empty.equals(p) || p.length() == 0 || obj == null) {
			return obj;
		}
		if ($object.equals(p) || (p.startsWith("[") && p.endsWith("]"))) { // Multiple line configuration
			Field[] fields = type.getDeclaredFields();
			int filteringModifiers = Modifier.PUBLIC;
			Map<String, ConfigFieldFilter> configFilter = configurationFilters;
			ConfigFieldFilter filter = configFilter != null ? configFilter.get(type.getName()) : null;
			if (filter != null && filter.modifiers >= 0) {
				filteringModifiers = filter.modifiers;
			}
			for (int i = 0; i < fields.length; i++) {
				Field f = fields[i];
				if (f == null) continue; // never happen
				int modifiers = f.getModifiers();
				if (filteringModifiers <= 0 ? false : (modifiers & filteringModifiers) == 0
						|| (modifiers & Modifier.STATIC) != 0
						|| (modifiers & Modifier.FINAL) != 0) {
					// Ignore static, final fields
					continue;
				}
				String name = f.getName();
				if (filter != null) {
					if (filter.excludes != null) {
						if (filter.excludes.contains(name)) {
							continue;
						}
					}
					if (filter.includes != null) {
						if (!filter.includes.contains(name)) {
							// skip fields not included in #includes
							continue;
						}
					}
				}
				String fieldKeyName = keyName + "." + name;
				String pp = prop.getProperty(fieldKeyName);
				if (pp == null) {
					continue;
				}
				pp = pp.trim();
				if (pp.length() == 0) {
					continue;
				}
				if ((modifiers & Modifier.PUBLIC) == 0) {
					f.setAccessible(true);
				}
				checkAndUpdateField(f, obj, pp, fieldKeyName, prop, true);
			}
			return obj;
		}
		// Single line configuration
		String[] arr = p.split("\\s*;\\s*");
		int filteringModifiers = Modifier.PUBLIC;
		Map<String, ConfigFieldFilter> configFilter = configurationFilters;
		ConfigFieldFilter filter = configFilter != null ? configFilter.get(type.getName()) : null;
		if (filter != null && filter.modifiers >= 0) {
			filteringModifiers = filter.modifiers;
		}
		for (int j = 0; j < arr.length; j++) {
			String item = arr[j].trim();
			if (item.length() == 0) {
				continue;
			}
			String[] kv = item.split("\\s*>+\\s*");
			if (kv.length != 2) {
				continue;
			}
			String k = kv[0].trim();
			if (filter != null) {
				if (filter.excludes != null) {
					if (filter.excludes.contains(k)) {
						continue;
					}
				}
				if (filter.includes != null) {
					if (!filter.includes.contains(k)) {
						// skip fields not included in #includes
						continue;
					}
				}
			}
			Field f = null;
			try {
				f = type.getDeclaredField(k);
			} catch (Exception e1) {
				//e1.printStackTrace();
			}
			if (f == null) {
				continue;
			}
			int modifiers = f.getModifiers();
			if (filteringModifiers <= 0 ? false : (modifiers & filteringModifiers) == 0
					|| (modifiers & Modifier.STATIC) != 0
					|| (modifiers & Modifier.FINAL) != 0) {
				// Ignore static, final, private fields
				continue;
			}
			if ((modifiers & Modifier.PUBLIC) == 0) {
				f.setAccessible(true);
			}
			String pp = kv[1].trim();
			checkAndUpdateField(f, obj, pp, keyName + "." + k, prop, true);
		}
		return obj;
	}

	static Map<String, Object> parseMap(String p, Class<?>[] valueTypes, String keyName, Properties prop) {
		if ($null.equals(p) || p == null) {
			return null;
		}
		Map<String, Object> value = new ConcurrentHashMap<String, Object>();
		if ($empty.equals(p) || p.length() == 0) {
			return value;
		}
		boolean isTypeString = valueTypes == null || valueTypes.length == 0
				|| (valueTypes.length == 1 && valueTypes[0] == String.class);
		if ($map.equals(p) || (p.startsWith("[") && p.endsWith("]"))) { // readable map, multiple line configuration
			Set<String> names = prop.stringPropertyNames();
			if (isTypeString) {
				for (String propName : names) {
					String prefix = keyName + ".";
					if (propName.startsWith(prefix)) {
						String k = propName.substring(prefix.length());
						String v = (String) prop.getProperty(propName);
						value.put(k, parseString(v));
					}
				}
			} else {
				int dots = 1;
				boolean hasKey = false;
				String prefix = keyName + ".";
				Set<String> parsedKeys = new HashSet<String>();
				do {
					for (String propName : names) {
						if (propName.startsWith(prefix)) {
							hasKey = true;
							boolean alreadyParsed = false;
							for (String key : parsedKeys) {
								if (propName.startsWith(key)) {
									alreadyParsed = true;
									break;
								}
							}
							if (alreadyParsed) {
								continue;
							}
							String k = propName.substring(prefix.length());
							String[] split = k.split("\\.");
							if (split.length > dots) {
								continue;
							}
							String v = (String) prop.getProperty(propName);
							value.put(k, parseTypedObject(valueTypes, v, propName, prop));
							if (v == null || v.length() <= 0 || (v.startsWith("[") && v.endsWith("]"))) {
								parsedKeys.add(propName + ".");
							} // else given v is a line for object, no need to put it into parsed keys set
						}
					}
					dots++;
				} while (hasKey && dots < Math.max(1, configurationMapSearchingDots));
			}
			return value;
		}
		// single line configuration, should be simple like Map<String, String>
		String[] arr = p.split("\\s*;\\s*");
		for (int j = 0; j < arr.length; j++) {
			String item = arr[j].trim();
			if (item.length() == 0) {
				continue;
			}
			String[] kv = item.split("\\s*>+\\s*");
			if (kv.length != 2) {
				continue;
			}
			String k = kv[0].trim();
			String v = kv[1].trim();
			if (isTypeString) {
				value.put(k, parseString(v));
			} else {
				value.put(k, parseTypedObject(valueTypes, v, keyName, prop));
			}
		}
		return value;
	}

	static List<Object> parseList(String p, Class<?> valueTypes[], String keyName, Properties prop) {
		if ($null.equals(p) || p == null) {
			return null;
		}
		if ($empty.equals(p) || p.length() == 0) {
			return new ArrayList<Object>();
		}
		boolean isTypeString = valueTypes == null || valueTypes.length == 0
				|| (valueTypes.length == 1 && valueTypes[0] == String.class);
		if ($list.equals(p) || (p.startsWith("[") && p.endsWith("]"))) { // readable list, multiple line configuration
			List<String> filteredNames = new ArrayList<String>();
			Set<String> names = prop.stringPropertyNames();
			for (String propName : names) {
				String prefix = keyName + ".";
				if (propName.startsWith(prefix)) {
					String k = propName.substring(prefix.length());
					String[] split = k.split("\\.");
					if (split.length > 1) {
						continue;
					}
					filteredNames.add(k);
				}
			}
			String[] keyNames = filteredNames.toArray(new String[filteredNames.size()]);
			Arrays.sort(keyNames); // keep list order
			List<Object> value = new ArrayList<Object>(keyNames.length);
			for (String propName : keyNames) {
				String v = (String) prop.getProperty(keyName + "." + propName);
				if (isTypeString) {
					value.add(parseString(v));
				} else {
					value.add(parseTypedObject(valueTypes, v, keyName + "." + propName, prop));
				}
			}
			return value;
		}
		// single line configuration, should be simple structure, like List<String>
		String[] arr = p.split("\\s*;\\s*");
		List<Object> value = new ArrayList<Object>(arr.length);
		for (int j = 0; j < arr.length; j++) {
			String v = arr[j].trim();
			if (isTypeString) {
				value.add(parseString(v));
			} else {
				value.add(parseTypedObject(valueTypes, v, keyName, prop));
			}
		}
		return value;
	}

	static Set<Object> parseSet(String p, Class<?>[] valueTypes, String keyName, Properties prop) {
		if ($null.equals(p) || p == null) {
			return null;
		}
		if ($empty.equals(p) || p.length() == 0) {
			return Collections.newSetFromMap(new ConcurrentHashMap<Object, Boolean>());
		}
		boolean isTypeString = valueTypes == null || valueTypes.length == 0
				|| (valueTypes.length == 1 && valueTypes[0] == String.class);
		if ($set.equals(p) || (p.startsWith("[") && p.endsWith("]"))) { // readable map
			List<String> filteredNames = new ArrayList<String>();
			Set<String> names = prop.stringPropertyNames();
			for (String propName : names) {
				String prefix = keyName + ".";
				if (propName.startsWith(prefix)) {
					String k = propName.substring(prefix.length());
					String[] split = k.split("\\.");
					if (split.length > 1) {
						continue;
					}
					filteredNames.add(k);
				}
			}
			String[] keyNames = filteredNames.toArray(new String[filteredNames.size()]);
			Set<Object> value = Collections.newSetFromMap(new ConcurrentHashMap<Object, Boolean>(keyNames.length << 2));
			for (String propName : keyNames) {
				String v = (String) prop.getProperty(keyName + "." + propName);
				if (isTypeString) {
					value.add(parseString(v));
				} else {
					Object o = parseTypedObject(valueTypes, v, keyName + "." + propName, prop);
					value.add(o);
				}
			}
			return value;
		}
		// single line configuration
		String[] arr = p.split("\\s*;\\s*");
		Set<Object> value = Collections.newSetFromMap(new ConcurrentHashMap<Object, Boolean>(arr.length << 2));
		for (int j = 0; j < arr.length; j++) {
			String v = arr[j].trim();
			if (isTypeString) {
				value.add(parseString(v));
			} else {
				value.add(parseTypedObject(valueTypes, v, keyName, prop));
			}
		}
		return value;
	}

	static char[] parseCharArray(String p, String keyName, Properties prop) {
		if ($null.equals(p) || p == null) {
			return null;
		}
		if ($empty.equals(p) || p.length() == 0) {
			return new char[0];
		} 
		if ($array.equals(p) || (p.startsWith("[") && p.endsWith("]"))) { // readable char array, multiple line configuration
			List<String> filteredNames = new ArrayList<String>();
			Set<String> names = prop.stringPropertyNames();
			for (String propName : names) {
				String prefix = keyName + ".";
				if (propName.startsWith(prefix)) {
					String k = propName.substring(prefix.length());
					filteredNames.add(k);
				}
			}
			String[] keyNames = filteredNames.toArray(new String[filteredNames.size()]);
			Arrays.sort(keyNames); // keep array's order
			char[] cs = new char[keyNames.length];
			for (int j = 0; j < keyNames.length; j++) {
				String propName = keyNames[j];
				String v = (String) prop.getProperty(keyName + "." + propName);
				if (v != null && v.length() > 0) {
					try {
						cs[j] = v.charAt(0);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
			return cs;
		}
		// single line configuration
		String[] ss = p.split("\\s*;\\s*");
		char[] cs = null;
		if (ss != null) {
			cs = new char[ss.length];
			for (int j = 0; j < ss.length; j++) {
				if (ss[j] != null) {
					try {
						cs[j] = ss[j].charAt(0);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		}
		return cs;
	}

	static byte[] parseByteArray(String p, String keyName, Properties prop) {
		if ($null.equals(p) || p == null) {
			return null;
		}
		if ($empty.equals(p) || p.length() == 0) {
			return new byte[0];
		} 
		if ($array.equals(p) || (p.startsWith("[") && p.endsWith("]"))) { // readable byte array, multiple line configuration
			List<String> filteredNames = new ArrayList<String>();
			Set<String> names = prop.stringPropertyNames();
			for (String propName : names) {
				String prefix = keyName + ".";
				if (propName.startsWith(prefix)) {
					String k = propName.substring(prefix.length());
					filteredNames.add(k);
				}
			}
			String[] keyNames = filteredNames.toArray(new String[filteredNames.size()]);
			Arrays.sort(keyNames); // keep array's order
			byte[] bs = new byte[keyNames.length];
			for (int j = 0; j < keyNames.length; j++) {
				String propName = keyNames[j];
				String v = (String) prop.getProperty(keyName + "." + propName);
				if (v != null) {
					try {
						bs[j] = Byte.parseByte(v);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
			return bs;
		}
		// single line configuration
		String[] ss = p.split("\\s*;\\s*");
		byte[] bs = null;
		if (ss != null) {
			bs = new byte[ss.length];
			for (int j = 0; j < ss.length; j++) {
				if (ss[j] != null) {
					try {
						bs[j] = Byte.parseByte(ss[j]);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		}
		return bs;
	}

	static short[] parseShortArray(String p, String keyName, Properties prop) {
		if ($null.equals(p) || p == null) {
			return null;
		}
		if ($empty.equals(p) || p.length() == 0) {
			return new short[0];
		} 
		if ($array.equals(p) || (p.startsWith("[") && p.endsWith("]"))) { // readable short array, multiple line configuration
			List<String> filteredNames = new ArrayList<String>();
			Set<String> names = prop.stringPropertyNames();
			for (String propName : names) {
				String prefix = keyName + ".";
				if (propName.startsWith(prefix)) {
					String k = propName.substring(prefix.length());
					filteredNames.add(k);
				}
			}
			String[] keyNames = filteredNames.toArray(new String[filteredNames.size()]);
			Arrays.sort(keyNames); // keep array's order
			short[] ns = new short[keyNames.length];
			for (int j = 0; j < keyNames.length; j++) {
				String propName = keyNames[j];
				String v = (String) prop.getProperty(keyName + "." + propName);
				if (v != null) {
					try {
						ns[j] = Short.parseShort(v);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
			return ns;
		}
		// single line configuration
		String[] ss = p.split("\\s*;\\s*");
		short[] ns = null;
		if (ss != null) {
			ns = new short[ss.length];
			for (int j = 0; j < ss.length; j++) {
				if (ss[j] != null) {
					try {
						ns[j] = Short.parseShort(ss[j]);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		}
		return ns;
	}

	static float[] parseFloatArray(String p, String keyName, Properties prop) {
		if ($null.equals(p) || p == null) {
			return null;
		}
		if ($empty.equals(p) || p.length() == 0) {
			return new float[0];
		} 
		if ($array.equals(p) || (p.startsWith("[") && p.endsWith("]"))) { // readable float array, multiple line configuration
			List<String> filteredNames = new ArrayList<String>();
			Set<String> names = prop.stringPropertyNames();
			for (String propName : names) {
				String prefix = keyName + ".";
				if (propName.startsWith(prefix)) {
					String k = propName.substring(prefix.length());
					filteredNames.add(k);
				}
			}
			String[] keyNames = filteredNames.toArray(new String[filteredNames.size()]);
			Arrays.sort(keyNames); // keep array's order
			float[] fs = new float[keyNames.length];
			for (int j = 0; j < keyNames.length; j++) {
				String propName = keyNames[j];
				String v = (String) prop.getProperty(keyName + "." + propName);
				if (v != null) {
					try {
						fs[j] = Float.parseFloat(v);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
			return fs;
		}
		// single line configuration
		String[] ss = p.split("\\s*;\\s*");
		float[] fs = null;
		if (ss != null) {
			fs = new float[ss.length];
			for (int j = 0; j < ss.length; j++) {
				if (ss[j] != null) {
					try {
						fs[j] = Float.parseFloat(ss[j]);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		}
		return fs;
	}

	static double[] parseDoubleArray(String p, String keyName, Properties prop) {
		if ($null.equals(p) || p == null) {
			return null;
		}
		if ($empty.equals(p) || p.length() == 0) {
			return new double[0];
		} 
		if ($array.equals(p) || (p.startsWith("[") && p.endsWith("]"))) { // readable double array, multiple line configuration
			List<String> filteredNames = new ArrayList<String>();
			Set<String> names = prop.stringPropertyNames();
			for (String propName : names) {
				String prefix = keyName + ".";
				if (propName.startsWith(prefix)) {
					String k = propName.substring(prefix.length());
					filteredNames.add(k);
				}
			}
			String[] keyNames = filteredNames.toArray(new String[filteredNames.size()]);
			Arrays.sort(keyNames); // keep array's order
			double[] ds = new double[keyNames.length];
			for (int j = 0; j < keyNames.length; j++) {
				String propName = keyNames[j];
				String v = (String) prop.getProperty(keyName + "." + propName);
				if (v != null) {
					try {
						ds[j] = Double.parseDouble(v);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
			return ds;
		}
		// single line configuration
		String[] ss = p.split("\\s*;\\s*");
		double[] ds = null;
		if (ss != null) {
			ds = new double[ss.length];
			for (int j = 0; j < ss.length; j++) {
				if (ss[j] != null) {
					try {
						ds[j] = Double.parseDouble(ss[j]);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		}
		return ds;
	}

	static boolean[] parseBooleanArray(String p, String keyName, Properties prop) {
		if ($null.equals(p) || p == null) {
			return null;
		}
		if ($empty.equals(p) || p.length() == 0) {
			return new boolean[0];
		} 
		if ($array.equals(p) || (p.startsWith("[") && p.endsWith("]"))) { // readable boolean array, multiple line configuration
			List<String> filteredNames = new ArrayList<String>();
			Set<String> names = prop.stringPropertyNames();
			for (String propName : names) {
				String prefix = keyName + ".";
				if (propName.startsWith(prefix)) {
					String k = propName.substring(prefix.length());
					filteredNames.add(k);
				}
			}
			String[] keyNames = filteredNames.toArray(new String[filteredNames.size()]);
			Arrays.sort(keyNames); // keep array's order
			boolean[] bs = new boolean[keyNames.length];
			for (int j = 0; j < keyNames.length; j++) {
				String propName = keyNames[j];
				String v = (String) prop.getProperty(keyName + "." + propName);
				if (v != null) {
					try {
						bs[j] = Boolean.parseBoolean(v);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
			return bs;
		}
		// single line configuration
		String[] ss = p.split("\\s*;\\s*");
		boolean[] bs = null;
		if (ss != null) {
			bs = new boolean[ss.length];
			for (int j = 0; j < ss.length; j++) {
				if (ss[j] != null) {
					try {
						bs[j] = Boolean.parseBoolean(ss[j]);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		}
		return bs;
	}

	static long[] parseLongArray(String p, String keyName, Properties prop) {
		if ($null.equals(p) || p == null) {
			return null;
		}
		if ($empty.equals(p) || p.length() == 0) {
			return new long[0];
		} 
		if ($array.equals(p) || (p.startsWith("[") && p.endsWith("]"))) { // readable long array, multiple line configuration
			List<String> filteredNames = new ArrayList<String>();
			Set<String> names = prop.stringPropertyNames();
			for (String propName : names) {
				String prefix = keyName + ".";
				if (propName.startsWith(prefix)) {
					String k = propName.substring(prefix.length());
					filteredNames.add(k);
				}
			}
			String[] keyNames = filteredNames.toArray(new String[filteredNames.size()]);
			Arrays.sort(keyNames); // keep array's order
			long[] ls = new long[keyNames.length];
			for (int j = 0; j < keyNames.length; j++) {
				String propName = keyNames[j];
				String v = (String) prop.getProperty(keyName + "." + propName);
				if (v != null) {
					try {
						ls[j] = Long.parseLong(v);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
			return ls;
		}
		// single line configuration
		String[] ss = p.split("\\s*;\\s*");
		long[] ls = null;
		if (ss != null) {
			ls = new long[ss.length];
			for (int j = 0; j < ss.length; j++) {
				if (ss[j] != null) {
					try {
						ls[j] = Long.parseLong(ss[j]);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		}
		return ls;
	}

	static int[] parseIntegerArray(String p, String keyName, Properties prop) {
		if ($null.equals(p) || p == null) {
			return null;
		}
		if ($empty.equals(p) || p.length() == 0) {
			return new int[0];
		}
		if ($array.equals(p) || (p.startsWith("[") && p.endsWith("]"))) { // readable integer array, multiple line configuration
			List<String> filteredNames = new ArrayList<String>();
			Set<String> names = prop.stringPropertyNames();
			for (String propName : names) {
				String prefix = keyName + ".";
				if (propName.startsWith(prefix)) {
					String k = propName.substring(prefix.length());
					filteredNames.add(k);
				}
			}
			String[] keyNames = filteredNames.toArray(new String[filteredNames.size()]);
			Arrays.sort(keyNames); // keep array's order
			int[] is = new int[keyNames.length];
			for (int j = 0; j < keyNames.length; j++) {
				String propName = keyNames[j];
				String v = (String) prop.getProperty(keyName + "." + propName);
				if (v != null) {
					try {
						is[j] = Integer.parseInt(v);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
			return is;
		}
		// single line configuration
		String[] ss = p.split("\\s*;\\s*");
		int[] is = null;
		if (ss != null) {
			is = new int[ss.length];
			for (int j = 0; j < ss.length; j++) {
				if (ss[j] != null) {
					try {
						is[j] = Integer.parseInt(ss[j]);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		}
		return is;
	}

	static Object[] parseArray(String p, Class<?>[] valueTypes, String keyName, Properties prop) {
		if ($null.equals(p) || p == null) {
			return null;
		}
		boolean isTypeString = valueTypes == null || valueTypes.length == 0
				|| (valueTypes.length == 1 && valueTypes[0] == String.class);
		Class<? extends Object> componentType = isTypeString ? String.class : valueTypes[0];
		if ($empty.equals(p) || p.length() == 0) {
			return (Object[]) Array.newInstance(componentType, 0);
		}
		if ($array.equals(p) || (p.startsWith("[") && p.endsWith("]"))) { // readable map, multiple line configuration
			List<String> filteredNames = new ArrayList<String>();
			Set<String> names = prop.stringPropertyNames();
			String prefix = keyName + ".";
			for (String propName : names) {
				if (propName.startsWith(prefix)) {
					String k = propName.substring(prefix.length());
					String[] split = k.split("\\.");
					if (split.length > 1) {
						continue;
					}
					filteredNames.add(k);
				}
			}
			String[] keyNames = filteredNames.toArray(new String[filteredNames.size()]);
			Arrays.sort(keyNames); // keep array's order
			Object[] value = (Object[]) Array.newInstance(componentType, keyNames.length);
			for (int j = 0; j < keyNames.length; j++) {
				String propName = keyNames[j];
				String v = (String) prop.getProperty(keyName + "." + propName);
				if (isTypeString) {
					value[j] = parseString(v);
				} else {
					value[j] = parseTypedObject(valueTypes, v, keyName + "." + propName, prop);
				}
			}
			return value;
		}
		// single line configuration
		String[] arr = p.split("\\s*;\\s*");
		Object[] value = (Object[]) Array.newInstance(componentType, arr.length);
		for (int j = 0; j < arr.length; j++) {
			String v = arr[j].trim();
			if (isTypeString) {
				value[j] = parseString(v);
			} else {
				value[j] = parseTypedObject(valueTypes, v, keyName, prop);
			}
		}
		return value;
	}

	static String parseString(String p) {
		if ($null.equals(p) || p == null) {
			return null;
		} else if ($empty.equals(p)) {
			return "";
		} else if (p.indexOf("[secret:") == 0) { // "[secret:#######]";
			return parseSecret(p.substring(8, p.length() - 1));
		} else {
			return p;
		}
	}
	
	public static void parseConfiguration(Class<?> clz, boolean combinedConfigs, Properties prop, boolean callUpdating) {
		if (clz == null) {
			return;
		}
		long now = System.currentTimeMillis();
		String keyPrefix = null;
		if (combinedConfigs) { // all configuration items are in one file, use key prefix to distinguish fields
			keyPrefix = getKeyPrefix(clz);
		}
		Field[] fields = clz.getDeclaredFields();
		int filteringModifiers = Modifier.PUBLIC;
		Map<String, ConfigFieldFilter> configFilter = configurationFilters;
		ConfigFieldFilter filter = configFilter != null ? configFilter.get(clz.getName()) : null;
		if (filter != null && filter.modifiers >= 0) {
			filteringModifiers = filter.modifiers;
		}
		for (int i = 0; i < fields.length; i++) {
			Field f = fields[i];
			if (f != null) {
				int modifiers = f.getModifiers();
				if (filteringModifiers <= 0 ? false : (modifiers & filteringModifiers) == 0
						|| (modifiers & Modifier.STATIC) == 0
						|| (modifiers & Modifier.FINAL) != 0) {
					continue;
				}
				String name = f.getName();
				if (filter != null) {
					if (filter.excludes != null) {
						if (filter.excludes.contains(name)) {
							continue;
						}
					}
					if (filter.includes != null) {
						if (!filter.includes.contains(name)) {
							// skip fields not included in #includes
							continue;
						}
					}
				}
				String keyName = keyPrefix != null ? keyPrefix + "." + name : name;
				String p = prop.getProperty(keyName);
				if (p == null) {
					continue;
				}
				p = p.trim();
				if (p.length() == 0) {
					continue;
				}
				if ((modifiers & Modifier.PUBLIC) == 0) {
					f.setAccessible(true);
				}
				boolean updated = checkAndUpdateField(f, clz, p, keyName, prop, true);
				if (updated && configurationLogging && initializedTime > 0
						&& now - initializedTime > 3000) { // start monitoring fields after 3s
					System.out.println("[Config] Configuration " + clz.getName() + "#" + name + " updated.");
				}
			}
		}
		if (callUpdating || keyPrefix == null || keyPrefix.length() == 0) {
			try {
				Method method = clz.getMethod("update", Properties.class);
				if (method != null && (method.getModifiers() & Modifier.STATIC) != 0) {
					method.invoke(null, prop);
				}
			} catch (NoSuchMethodException e) {
				// ignore
			} catch (Throwable e) {
				e.printStackTrace();
			}
		}
	}
	
	protected static Class<?>[] getValueTypes(Class<?> type, ParameterizedType paramType) {
		List<Class<?>> valueTypes = new ArrayList<Class<?>>();
		do {
			Type vType = paramType.getActualTypeArguments()[type == Map.class ? 1 : 0]; // For map, second generic type
			if (vType instanceof GenericArrayType) {
				GenericArrayType aType = (GenericArrayType) vType;
				Class<?> valueType = (Class<?>) aType.getGenericComponentType();
				valueTypes.add(Array.newInstance(valueType, 0).getClass());
				break;
			} else if (vType instanceof ParameterizedType) {
				paramType = (ParameterizedType) vType; 
				type = (Class<?>) paramType.getRawType();
				valueTypes.add(type);
			} else {
				valueTypes.add((Class<?>) vType);
				break;
			}
		} while (true);
		return valueTypes.toArray(new Class<?>[valueTypes.size()]);
	}

	protected static boolean checkAndUpdateField(Field f, Object obj, String p, String keyName, Properties prop, boolean updatingField) {
		return checkAndUpdateField(f, obj, p, keyName, prop, updatingField, null);
	}
	
	private static StringBuilder diffFieldPrefix(StringBuilder diffBuilder, Object obj, Field f) {
		return diffBuilder.append((obj instanceof Class<?> ? (Class<?>) obj : obj.getClass()).getSimpleName())
			.append('.').append(f.getName()).append(':');
	}

	protected static boolean checkAndUpdateField(Field f, Object obj, String p, String keyName, Properties prop, boolean updatingField, StringBuilder diffBuilder) {
		Class<?> type = f.getType();
		try {
			if (type == int.class) {
				if (!p.equals(String.valueOf(f.getInt(obj)))) {
					if (diffBuilder != null)
						diffFieldPrefix(diffBuilder, obj, f).append(f.getInt(obj)).append('>').append(p).append("\r\n");
					if (updatingField) f.setInt(obj, Integer.parseInt(p));
					return true;
				}
			} else if (type == String.class) {
				String newStr = parseString(p);
				String oldStr = (String) f.get(obj);
				if ((newStr == null && oldStr != null) || (newStr != null && !newStr.equals(oldStr))) {
					if (diffBuilder != null)
						diffFieldPrefix(diffBuilder, obj, f).append(f.get(obj)).append('>').append(p).append("\r\n");
					if (updatingField) f.set(obj, newStr);
					return true;
				}
			} else if (type == boolean.class) {
				if (!p.equals(String.valueOf(f.getBoolean(obj)))) {
					if (diffBuilder != null)
						diffFieldPrefix(diffBuilder, obj, f).append(f.getBoolean(obj)).append('>').append(p).append("\r\n");
					if (updatingField) f.setBoolean(obj, Boolean.parseBoolean(p));
					return true;
				}
			} else if (type == long.class) {
				if (!p.equals(String.valueOf(f.getLong(obj)))) {
					if (diffBuilder != null)
						diffFieldPrefix(diffBuilder, obj, f).append(f.getLong(obj)).append('>').append(p).append("\r\n");
					if (updatingField) f.setLong(obj, Long.parseLong(p));
					return true;
				}
			} else if (type == int[].class) {
				int[] newArr = parseIntegerArray(p, keyName, prop);
				if (!Arrays.equals(newArr, (int[]) f.get(obj))) { // Only update necessary fields
					if (diffBuilder != null)
						diffFieldPrefix(diffBuilder, obj, f).append("[...]").append('>')
							.append(newArr == null ? null : "[" + newArr.length + "]").append("\r\n");
					if (updatingField) f.set(obj, newArr);
					return true;
				}
			} else if (type == long[].class) {
				long[] newArr = parseLongArray(p, keyName, prop);
				if (!Arrays.equals(newArr, (long[]) f.get(obj))) { // Only update necessary fields
					if (diffBuilder != null)
						diffFieldPrefix(diffBuilder, obj, f).append("[...]").append('>')
							.append(newArr == null ? null : "[" + newArr.length + "]").append("\r\n");
					if (updatingField) f.set(obj, newArr);
					return true;
				}
			} else if (type == boolean[].class) {
				boolean[] newArr = parseBooleanArray(p, keyName, prop);
				if (!Arrays.equals(newArr, (boolean[]) f.get(obj))) { // Only update necessary fields
					if (diffBuilder != null)
						diffFieldPrefix(diffBuilder, obj, f).append("[...]").append('>')
							.append(newArr == null ? null : "[" + newArr.length + "]").append("\r\n");
					if (updatingField) f.set(obj, newArr);
					return true;
				}
			} else if (type == double[].class) {
				double[] newArr = parseDoubleArray(p, keyName, prop);
				if (!Arrays.equals(newArr, (double[]) f.get(obj))) { // Only update necessary fields
					if (diffBuilder != null)
						diffFieldPrefix(diffBuilder, obj, f).append("[...]").append('>')
							.append(newArr == null ? null : "[" + newArr.length + "]").append("\r\n");
					if (updatingField) f.set(obj, newArr);
					return true;
				}
			} else if (type == float[].class) {
				float[] newArr = parseFloatArray(p, keyName, prop);
				if (!Arrays.equals(newArr, (float[]) f.get(obj))) { // Only update necessary fields
					if (diffBuilder != null)
						diffFieldPrefix(diffBuilder, obj, f).append("[...]").append('>')
							.append(newArr == null ? null : "[" + newArr.length + "]").append("\r\n");
					if (updatingField) f.set(obj, newArr);
					return true;
				}
			} else if (type == short[].class) {
				short[] newArr = parseShortArray(p, keyName, prop);
				if (!Arrays.equals(newArr, (short[]) f.get(obj))) { // Only update necessary fields
					if (diffBuilder != null)
						diffFieldPrefix(diffBuilder, obj, f).append("[...]").append('>')
							.append(newArr == null ? null : "[" + newArr.length + "]").append("\r\n");
					if (updatingField) f.set(obj, newArr);
					return true;
				}
			} else if (type == byte[].class) {
				byte[] newArr = parseByteArray(p, keyName, prop);
				if (!Arrays.equals(newArr, (byte[]) f.get(obj))) { // Only update necessary fields
					if (diffBuilder != null)
						diffFieldPrefix(diffBuilder, obj, f).append("[...]").append('>')
							.append(newArr == null ? null : "[" + newArr.length + "]").append("\r\n");
					if (updatingField) f.set(obj, newArr);
					return true;
				}
			} else if (type == char[].class) {
				char[] newArr = parseCharArray(p, keyName, prop);
				if (!Arrays.equals(newArr, (char[]) f.get(obj))) { // Only update necessary fields
					if (diffBuilder != null)
						diffFieldPrefix(diffBuilder, obj, f).append("[...]").append('>')
							.append(newArr == null ? null : "[" + newArr.length + "]").append("\r\n");
					if (updatingField) f.set(obj, newArr);
					return true;
				}
			} else if (type.isArray()) {
				Class<?> compType = type.getComponentType();
				Object[] newArr = parseArray(p, new Class<?>[] { compType }, keyName, prop);
				if (!Arrays.deepEquals(newArr, (Object[]) f.get(obj))) { // Only update necessary fields
					if (diffBuilder != null)
						diffFieldPrefix(diffBuilder, obj, f).append("[...]").append('>')
							.append(newArr == null ? null : "[" + newArr.length + "]").append("\r\n");
					if (updatingField) f.set(obj, newArr);
					return true;
				}
			} else if (type == double.class) {
				if (!p.equals(String.valueOf(f.getDouble(obj)))) {
					if (diffBuilder != null)
						diffFieldPrefix(diffBuilder, obj, f).append(f.getDouble(obj)).append('>').append(p).append("\r\n");
					if (updatingField) f.setDouble(obj, Double.parseDouble(p));
					return true;
				}
			} else if (type == float.class) {
				if (!p.equals(String.valueOf(f.getFloat(obj)))) {
					if (diffBuilder != null)
						diffFieldPrefix(diffBuilder, obj, f).append(f.getFloat(obj)).append('>').append(p).append("\r\n");
					if (updatingField) f.setFloat(obj, Float.parseFloat(p));
					return true;
				}
			} else if (type == short.class) {
				if (!p.equals(String.valueOf(f.getShort(obj)))) {
					if (diffBuilder != null)
						diffFieldPrefix(diffBuilder, obj, f).append(f.getShort(obj)).append('>').append(p).append("\r\n");
					if (updatingField) f.setShort(obj, Short.parseShort(p));
					return true;
				}
			} else if (type == byte.class) {
				if (!p.equals(String.valueOf(f.getByte(obj)))) {
					if (diffBuilder != null)
						diffFieldPrefix(diffBuilder, obj, f).append(f.getByte(obj)).append('>').append(p).append("\r\n");
					if (updatingField) f.setByte(obj, Byte.parseByte(p));
					return true;
				}
			} else if (type == char.class) {
				if (!p.equals(String.valueOf(f.getChar(obj)))) {
					if (diffBuilder != null)
						diffFieldPrefix(diffBuilder, obj, f).append(f.getChar(obj)).append('>').append(p).append("\r\n");
					if (updatingField) f.setChar(obj, p.charAt(0));
					return true;
				}
			} else if (type == List.class || type == Set.class || type == Map.class) {
				Class<?>[] valueTypes = null;
				Type genericType = f.getGenericType();
				if (genericType instanceof ParameterizedType) {
					valueTypes = getValueTypes(type, (ParameterizedType) genericType);
				}
				if (type == List.class) {
					List<Object> newList = parseList(p, valueTypes, keyName, prop);
					@SuppressWarnings("unchecked")
					List<Object> oldList = (List<Object>) f.get(obj);
					if (!listEquals(newList, oldList)) {
						if (diffBuilder != null)
							diffFieldPrefix(diffBuilder, obj, f).append("[...]").append('>')
								.append(newList == null ? null : "[" + newList.size() + "]").append("\r\n");
						if (updatingField) f.set(obj, newList);
						return true;
					}
				} else if (type == Set.class) {
					Set<Object> newSet = parseSet(p, valueTypes, keyName, prop);
					@SuppressWarnings("unchecked")
					Set<Object> oldSet = (Set<Object>) f.get(obj);
					if (!setEquals(newSet, oldSet)) {
						if (diffBuilder != null)
							diffFieldPrefix(diffBuilder, obj, f).append("[...]").append('>')
								.append(newSet == null ? null : "[" + newSet.size() + "]").append("\r\n");
						if (updatingField) f.set(obj, newSet);
						return true;
					}
				} else { // if (type == Map.class) {
					Map<String, Object> newMap = parseMap(p, valueTypes, keyName, prop);
					@SuppressWarnings("unchecked")
					Map<String, Object> oldMap = (Map<String, Object>) f.get(obj);
					if (!mapEquals(newMap, oldMap)) {
						if (diffBuilder != null)
							diffFieldPrefix(diffBuilder, obj, f).append("[...]").append('>')
								.append(newMap == null ? null : "[" + newMap.size() + "]").append("\r\n");
						if (updatingField) f.set(obj, newMap);
						return true;
					}
				}
			} else if (type == Integer.class) {
				Integer v = (Integer) f.get(obj);
				if (!p.equals(v == null ? $null : String.valueOf(v))) {
					if (diffBuilder != null)
						diffFieldPrefix(diffBuilder, obj, f).append(f.get(obj)).append('>').append(p).append("\r\n");
					if (updatingField) f.set(obj, $null.equals(p) ? null : Integer.valueOf(p));
					return true;
				}
			} else if (type == Boolean.class) {
				Boolean v = (Boolean) f.get(obj);
				if (!p.equals(v == null ? $null : String.valueOf(v))) {
					if (diffBuilder != null)
						diffFieldPrefix(diffBuilder, obj, f).append(f.get(obj)).append('>').append(p).append("\r\n");
					if (updatingField) f.set(obj, $null.equals(p) ? null : Boolean.valueOf(p));
					return true;
				}
			} else if (type == Long.class) {
				Long v = (Long) f.get(obj);
				if (!p.equals(v == null ? $null : String.valueOf(v))) {
					if (diffBuilder != null)
						diffFieldPrefix(diffBuilder, obj, f).append(f.get(obj)).append('>').append(p).append("\r\n");
					if (updatingField) f.set(obj, $null.equals(p) ? null : Long.valueOf(p));
					return true;
				}
			} else if (type == Double.class) {
				Double v = (Double) f.get(obj);
				if (!p.equals(v == null ? $null : String.valueOf(v))) {
					if (diffBuilder != null)
						diffFieldPrefix(diffBuilder, obj, f).append(f.get(obj)).append('>').append(p).append("\r\n");
					if (updatingField) f.setDouble(obj, $null.equals(p) ? null : Double.valueOf(p));
					return true;
				}
			} else if (type == Float.class) {
				Float v = (Float) f.get(obj);
				if (!p.equals(v == null ? $null : String.valueOf(v))) {
					if (diffBuilder != null)
						diffFieldPrefix(diffBuilder, obj, f).append(f.get(obj)).append('>').append(p).append("\r\n");
					if (updatingField) f.setFloat(obj, $null.equals(p) ? null : Float.valueOf(p));
					return true;
				}
			} else if (type == Short.class) {
				Short v = (Short) f.get(obj);
				if (!p.equals(v == null ? $null : String.valueOf(v))) {
					if (diffBuilder != null)
						diffFieldPrefix(diffBuilder, obj, f).append(f.get(obj)).append('>').append(p).append("\r\n");
					if (updatingField) f.setShort(obj, $null.equals(p) ? null : Short.valueOf(p));
					return true;
				}
			} else if (type == Byte.class) {
				Byte v = (Byte) f.get(obj);
				if (!p.equals(v == null ? $null : String.valueOf(v))) {
					if (diffBuilder != null)
						diffFieldPrefix(diffBuilder, obj, f).append(f.get(obj)).append('>').append(p).append("\r\n");
					if (updatingField) f.setByte(obj, $null.equals(p) ? null : Byte.valueOf(p));
					return true;
				}
			} else if (type == Character.class) {
				Character v = (Character) f.get(obj);
				if (!p.equals(v == null ? $null : String.valueOf(v))) {
					if (diffBuilder != null)
						diffFieldPrefix(diffBuilder, obj, f).append(f.get(obj)).append('>').append(p).append("\r\n");
					if (updatingField) f.setChar(obj, $null.equals(p) ? null : Character.valueOf(p.charAt(0)));
					return true;
				}
			} else {
				Object newObj = parseObject(p, type, keyName, prop);
				Object oldObj = f.get(obj);
				if ((newObj == null && oldObj != null) || (newObj != null && !newObj.equals(oldObj))) {
					if (diffBuilder != null)
						diffFieldPrefix(diffBuilder, obj, f).append(f.get(obj)).append('>').append(p).append("\r\n");
					if (updatingField) f.set(obj, newObj);
					return true;
				}
			}
		} catch (Throwable e) {
			e.printStackTrace();
		}
		return false;
	}

	/*
	 * In case configurations got updated from file, try to add class into configuration system
	 */
	public static void update(Properties prop) {
		String[] configClasses = configurationClasses;
		if (configClasses != null) {
			for (int i = 0; i < configClasses.length; i++) {
				String clazz = configClasses[i];
				if (!allConfigs.containsKey(clazz)) {
					Class<?> clz = loadConfigurationClass(clazz);
					if (clz != null) {
						registerUpdatingListener(clz);
					}
				}
			}
		}
	}

	// Support List#equals with array comparison
	@SuppressWarnings("unchecked")
	public static <T> boolean listEquals(List<T> l1, List<T> l2) {
		if (l1 == l2)
		    return true;
		if ((l1 == null && l2 != null) || (l1 != null && l2 == null)) {
			return false;
		}
		ListIterator<T> e1 = l1.listIterator();
		ListIterator<T> e2 = l2.listIterator();
		while(e1.hasNext() && e2.hasNext()) {
		    T o1 = e1.next();
		    T o2 = e2.next();
		    if (o1 == null && o2 != null) {
		    	return false;
		    } else if (o1 != null) {
		    	if (o1 instanceof Object[]) {
		    		if (!Arrays.deepEquals((Object[])o1, (Object[])o2))
		    			return false;
		    	} else if (o1 instanceof List<?>) {
		    		if (!listEquals((List<Object>)o1, (List<Object>)o2))
		    			return false;
		    	} else if (o1 instanceof Set<?>) {
		    		if (!setEquals((Set<Object>)o1, (Set<Object>)o2))
		    			return false;
		    	} else if (o1 instanceof Map<?,?>) {
		    		if (!mapEquals((Map<String, Object>)o1, (Map<String, Object>)o2))
		    			return false;
		    	} else if (o1 instanceof int[]) {
		    		if (!Arrays.equals((int[])o1, (int[])o2))
		    			return false;
		    	} else if (o1 instanceof long[]) {
		    		if (!Arrays.equals((long[])o1, (long[])o2))
		    			return false;
		    	} else if (o1 instanceof boolean[]) {
		    		if (!Arrays.equals((boolean[])o1, (boolean[])o2))
		    			return false;
		    	} else if (o1 instanceof double[]) {
		    		if (!Arrays.equals((double[])o1, (double[])o2))
		    			return false;
		    	} else if (o1 instanceof float[]) {
		    		if (!Arrays.equals((float[])o1, (float[])o2))
		    			return false;
		    	} else if (o1 instanceof short[]) {
		    		if (!Arrays.equals((short[])o1, (short[])o2))
		    			return false;
		    	} else if (o1 instanceof byte[]) {
		    		if (!Arrays.equals((byte[])o1, (byte[])o2))
		    			return false;
		    	} else if (o1 instanceof char[]) {
		    		if (!Arrays.equals((char[])o1, (char[])o2))
		    			return false;
		    	} else if (!o1.equals(o2)) {
		    		return false;
		    	}
		    }
		}
		return !(e1.hasNext() || e2.hasNext());
	}

    @SuppressWarnings("unchecked")
	static <T> boolean setContains(Set<T> s, Object o) {
		Iterator<T> e = s.iterator();
		if (o==null) {
		    while (e.hasNext())
				if (e.next()==null)
				    return true;
		} else if (o instanceof Object[]) {
			Object[] os = (Object[]) o;
		    while (e.hasNext())
				if (Arrays.deepEquals(os, (Object[]) e.next()))
				    return true;
		} else if (o instanceof List<?>) {
			List<Object> os = (List<Object>) o;
		    while (e.hasNext())
				if (listEquals(os, (List<Object>) e.next()))
				    return true;
		} else if (o instanceof Set<?>) {
			Set<Object> os = (Set<Object>) o;
		    while (e.hasNext())
				if (setEquals(os, (Set<Object>) e.next()))
				    return true;
		} else if (o instanceof Map<?, ?>) {
			Map<String, Object> os = (Map<String, Object>) o;
		    while (e.hasNext())
				if (mapEquals(os, (Map<String, Object>) e.next()))
				    return true;
		} else if (o instanceof int[]) {
			int[] os = (int[]) o;
		    while (e.hasNext())
				if (Arrays.equals(os, (int[]) e.next()))
				    return true;
		} else if (o instanceof long[]) {
			long[] os = (long[]) o;
		    while (e.hasNext())
				if (Arrays.equals(os, (long[]) e.next()))
				    return true;
		} else if (o instanceof boolean[]) {
			boolean[] os = (boolean[]) o;
		    while (e.hasNext())
				if (Arrays.equals(os, (boolean[]) e.next()))
				    return true;
		} else if (o instanceof double[]) {
			double[] os = (double[]) o;
		    while (e.hasNext())
				if (Arrays.equals(os, (double[]) e.next()))
				    return true;
		} else if (o instanceof float[]) {
			float[] os = (float[]) o;
		    while (e.hasNext())
				if (Arrays.equals(os, (float[]) e.next()))
				    return true;
		} else if (o instanceof short[]) {
			short[] os = (short[]) o;
		    while (e.hasNext())
				if (Arrays.equals(os, (short[]) e.next()))
				    return true;
		} else if (o instanceof byte[]) {
			byte[] os = (byte[]) o;
		    while (e.hasNext())
				if (Arrays.equals(os, (byte[]) e.next()))
				    return true;
		} else if (o instanceof char[]) {
			char[] os = (char[]) o;
		    while (e.hasNext())
				if (Arrays.equals(os, (char[]) e.next()))
				    return true;
		} else {
		    while (e.hasNext())
				if (o.equals(e.next()))
				    return true;
		}
		return false;
    }

	// Support Set#equals with array comparison
	public static <T> boolean setEquals(Set<T> s1, Set<T> s2) {
		if (s1 == s2)
		    return true;
		if ((s1 == null && s2 != null) || (s1 != null && s2 == null)) {
			return false;
		}
		if (s2.size() != s1.size())
		    return false;
	    try {
	    	Iterator<T> e2 = s2.iterator();
	    	while (e2.hasNext()) {
	    	    if (!setContains(s1, e2.next()))
	    	    	return false;
	    	}
	    	return true;
	    } catch (ClassCastException unused)   {
	        return false;
	    } catch (NullPointerException unused) {
	        return false;
	    }
	}

	// Support Map#equals with array comparison
	@SuppressWarnings("unchecked")
	public static <T> boolean mapEquals(Map<String, T> m1, Map<String, T> m2) {
		if (m1 == m2)
		    return true;
		if ((m1 == null && m2 != null) || (m1 != null && m2 == null)) {
			return false;
		}
		if (m2.size() != m1.size())
		    return false;
	
	    try {
	        Iterator<Entry<String, T>> i = m1.entrySet().iterator();
	        while (i.hasNext()) {
	            Entry<String, T> e = i.next();
	            String key = e.getKey();
	            Object value = e.getValue();
	            if (value == null) {
	                if (!(m2.get(key)==null && m2.containsKey(key)))
	                    return false;
	            } else if (value instanceof Object[]) {
	                if (!Arrays.deepEquals((Object[])value, (Object[])m2.get(key)))
	                    return false;
	            } else if (value instanceof List<?>) {
	                if (!listEquals((List<Object>)value, (List<Object>)m2.get(key)))
	                    return false;
	            } else if (value instanceof Set<?>) {
	                if (!setEquals((Set<Object>)value, (Set<Object>)m2.get(key)))
	                    return false;
	            } else if (value instanceof Map<?, ?>) {
	                if (!mapEquals((Map<String, Object>)value, (Map<String, Object>)m2.get(key)))
	                    return false;
	            } else if (value instanceof int[]) {
	                if (!Arrays.equals((int[])value, (int[])m2.get(key)))
	                    return false;
	            } else if (value instanceof long[]) {
	                if (!Arrays.equals((long[])value, (long[])m2.get(key)))
	                    return false;
	            } else if (value instanceof boolean[]) {
	                if (!Arrays.equals((boolean[])value, (boolean[])m2.get(key)))
	                    return false;
	            } else if (value instanceof double[]) {
	                if (!Arrays.equals((double[])value, (double[])m2.get(key)))
	                    return false;
	            } else if (value instanceof float[]) {
	                if (!Arrays.equals((float[])value, (float[])m2.get(key)))
	                    return false;
	            } else if (value instanceof short[]) {
	                if (!Arrays.equals((short[])value, (short[])m2.get(key)))
	                    return false;
	            } else if (value instanceof byte[]) {
	                if (!Arrays.equals((byte[])value, (byte[])m2.get(key)))
	                    return false;
	            } else if (value instanceof char[]) {
	                if (!Arrays.equals((char[])value, (char[])m2.get(key)))
	                    return false;
	            } else {
	                if (!value.equals(m2.get(key)))
	                    return false;
	            }
	        }
	    } catch (ClassCastException unused) {
	        return false;
	    } catch (NullPointerException unused) {
	        return false;
	    }
		return true;
	}

	/**
	 * If secret is encrypted and security decrypter is configured, try decrypt
	 * given secret to raw secret.
	 * 
	 * @param secret
	 * @return
	 */
	public static String parseSecret(final String secret) {
		if (secret == null || secret.length() == 0) {
			return secret;
		}
		String decrypterClass = configurationSecurityDecrypter;
		if (decrypterClass != null && decrypterClass.length() > 0) {
			Class<?> clz = loadConfigurationClass(decrypterClass);
			if (clz != null) {
				try {
					Method m = clz.getMethod("decrypt", String.class); // password
					Object result = m.invoke(clz, secret);
					if (result instanceof String) {
						return (String) result;
					}
				} catch (NoSuchMethodException e) {
					// do nothing
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			// password = SecurityKit.decrypt(password);
		}
		return secret;
	}
	
	/**
	 * Remove "/../" or "/./" in path.
	 * 
	 * @param path
	 * @return file path without "/../" or "/./"
	 */
	public static String parseFilePath(String path) {
		int length = path.length();
		if (length == 0) {
			return path;
		}
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
				System.out.println("Error in fixing URL: " + path);
				break;
			}
			String segment = segments[i];
			if (segment == null) {
				break;
			}
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
			if (segment == null) {
				break;
			}
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
	
}
