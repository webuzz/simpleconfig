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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Merge given configuration to a new configuration file basing on given old configuration.
 * 
 * @author zhourenjian
 *
 */
public class ConfigMerger {

	public static interface IConfigurable {
	
		public void updateConfigs();
		
	}

	public static String generateDeltaConfiguration(Class<?> clz, boolean combinedConfigs, Properties baseProps) {
		StringBuilder builder = new StringBuilder();
		//boolean skipUnchangedLines = false;
		String keyPrefix = null;
		if (combinedConfigs) {
			keyPrefix = Config.getKeyPrefix(clz);
		}
		boolean first = true;
		Field[] fields = clz.getDeclaredFields();
		int filteringModifiers = Modifier.PUBLIC;
		String clzName = clz.getName();
		ConfigFieldFilter filter = Config.configurationFilters.get(clzName);
		if (filter != null && filter.modifiers >= 0) {
			filteringModifiers = filter.modifiers;
		}
		for (int i = 0; i < fields.length; i++) {
			Field f = fields[i];
			if (f != null) {
				int modifiers = f.getModifiers();
				if ((filteringModifiers <= 0 ? false : (modifiers & filteringModifiers) == 0)
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
				if ((modifiers & Modifier.PUBLIC) == 0) {
					f.setAccessible(true);
				}
				if (!first) {
					builder.append("\r\n");
				}
				/*
				 * There are some temporary fields in Config and should
				 * not be generated down for remote server.
				 */
				boolean ignoringTemporyField = false;
				if (clz == Config.class && (name.equals("configurationFile") || name.equals("configurationFolder"))) {
					ignoringTemporyField = true; // String field
				}
				first = false;
				if (keyPrefix != null)  {
					name = keyPrefix + "." + name;
				}
				String v0 = baseProps.getProperty(name);
				Class<?> type = f.getType();
				try {
					if (type == int.class) {
						int v = f.getInt(clz);
						if ((v0 != null && v0.equals(String.valueOf(v))) || (v0 == null && v == 0)) {
							if (ConfigGenerator.skipUnchangedLines) continue;
							builder.append("#");
						}
						builder.append(name).append("=").append(v);
					} else if (type == String.class) {
						String v = (String)f.get(clz);
						if ((v0 != null && v0.equals(v)) || (v0 == null && v == null)) {
							if (ConfigGenerator.skipUnchangedLines) continue;
							builder.append("#");
						} else if (ignoringTemporyField) {
							builder.append("#");
						}
						ConfigGenerator.generateString(builder, name, v);
					} else if (type == boolean.class) {
						boolean v = f.getBoolean(clz);
						if ((v0 != null && v0.equals(String.valueOf(v))) || (v0 == null && v == false)) {
							if (ConfigGenerator.skipUnchangedLines) continue;
							builder.append("#");
						}
						builder.append(name).append("=").append(v);
					} else if (type == long.class) {
						long v = f.getLong(clz);
						if ((v0 != null && v0.equals(String.valueOf(v))) || (v0 == null && v == 0)) {
							if (ConfigGenerator.skipUnchangedLines) continue;
							builder.append("#");
						}
						builder.append(name).append("=").append(v);
					} else if (type == int[].class) {
						int[] vs = (int[])f.get(clz);
						boolean unchanged = false;
						if (Arrays.equals(vs, Config.parseIntegerArray(v0, name, baseProps))) {
							if (ConfigGenerator.skipUnchangedLines) continue;
							builder.append("#");
							unchanged = true;
						}
						ConfigGenerator.generateIntegerArray(builder, name, vs, unchanged);
					} else if (type == long[].class) {
						long[] vs = (long[])f.get(clz);
						boolean unchanged = false;
						if (Arrays.equals(vs, Config.parseLongArray(v0, name, baseProps))) {
							if (ConfigGenerator.skipUnchangedLines) continue;
							builder.append("#");
							unchanged = true;
						}
						ConfigGenerator.generateLongArray(builder, name, vs, unchanged);
					} else if (type == boolean[].class) {
						boolean[] vs = (boolean[])f.get(clz);
						boolean unchanged = false;
						if (Arrays.equals(vs, Config.parseBooleanArray(v0, name, baseProps))) {
							if (ConfigGenerator.skipUnchangedLines) continue;
							builder.append("#");
							unchanged = true;
						}
						ConfigGenerator.generateBooleanArray(builder, name, vs, unchanged);
					} else if (type == double[].class) {
						double[] vs = (double[])f.get(clz);
						boolean unchanged = false;
						if (Arrays.equals(vs, Config.parseDoubleArray(v0, name, baseProps))) {
							if (ConfigGenerator.skipUnchangedLines) continue;
							builder.append("#");
							unchanged = true;
						}
						ConfigGenerator.generateDoubleArray(builder, name, vs, unchanged);
					} else if (type == float[].class) {
						float[] vs = (float[])f.get(clz);
						boolean unchanged = false;
						if (Arrays.equals(vs, Config.parseFloatArray(v0, name, baseProps))) {
							if (ConfigGenerator.skipUnchangedLines) continue;
							builder.append("#");
							unchanged = true;
						}
						ConfigGenerator.generateFloatArray(builder, name, vs, unchanged);
					} else if (type == short[].class) {
						short[] vs = (short[])f.get(clz);
						boolean unchanged = false;
						if (Arrays.equals(vs, Config.parseShortArray(v0, name, baseProps))) {
							if (ConfigGenerator.skipUnchangedLines) continue;
							builder.append("#");
							unchanged = true;
						}
						ConfigGenerator.generateShortArray(builder, name, vs, unchanged);
					} else if (type == byte[].class) {
						byte[] vs = (byte[])f.get(clz);
						boolean unchanged = false;
						if (Arrays.equals(vs, Config.parseByteArray(v0, name, baseProps))) {
							if (ConfigGenerator.skipUnchangedLines) continue;
							builder.append("#");
							unchanged = true;
						}
						ConfigGenerator.generateByteArray(builder, name, vs, unchanged);
					} else if (type == char[].class) {
						char[] vs = (char[])f.get(clz);
						boolean unchanged = false;
						if (Arrays.equals(vs, Config.parseCharArray(v0, name, baseProps))) {
							if (ConfigGenerator.skipUnchangedLines) continue;
							builder.append("#");
							unchanged = true;
						}
						ConfigGenerator.generateCharArray(builder, name, vs, unchanged);
					} else if (type.isArray()) {
						Class<?> compType = type.getComponentType();
						Object[] vs = (Object[])f.get(clz);
						Object[] arr = Config.parseArray(v0, new Class<?>[] { compType }, name, baseProps);
						boolean unchanged = false;
						if (Arrays.deepEquals(vs, arr)) {
							if (ConfigGenerator.skipUnchangedLines) continue;
							builder.append("#");
							unchanged = true;
						}
						ConfigGenerator.generateArray(builder, name, vs, new Class<?>[] { type.getComponentType() }, unchanged);
					} else if (type == double.class) {
						double v = f.getDouble(clz);
						if ((v0 != null && v0.equals(String.valueOf(v))) || (v0 == null && v == 0)) {
							if (ConfigGenerator.skipUnchangedLines) continue;
							builder.append("#");
						}
						builder.append(name).append("=").append(v);
					} else if (type == float.class) {
						float v = f.getFloat(clz);
						if ((v0 != null && v0.equals(String.valueOf(v))) || (v0 == null && v == 0)) {
							if (ConfigGenerator.skipUnchangedLines) continue;
							builder.append("#");
						}
						builder.append(name).append("=").append(v);
					} else if (type == short.class) {
						short v = f.getShort(clz);
						if ((v0 != null && v0.equals(String.valueOf(v))) || (v0 == null && v == 0)) {
							if (ConfigGenerator.skipUnchangedLines) continue;
							builder.append("#");
						}
						builder.append(name).append("=").append(v);
					} else if (type == byte.class) {
						byte v = f.getByte(clz);
						if ((v0 != null && v0.equals(String.valueOf(v))) || (v0 == null && v == 0)) {
							if (ConfigGenerator.skipUnchangedLines) continue;
							builder.append("#");
						}
						builder.append(name).append("=").append(v);
					} else if (type == char.class) {
						char v = f.getChar(clz);
						if ((v0 != null && v0.equals(String.valueOf(v))) || (v0 == null && v == 0)) {
							if (ConfigGenerator.skipUnchangedLines) continue;
							builder.append("#");
						}
						builder.append(name).append("=").append(v);
					} else if (type == List.class || type == Set.class || type == Map.class) {
						Class<?>[] valueTypes = null;
						Type genericType = f.getGenericType();
						if (genericType instanceof ParameterizedType) {
							valueTypes = Config.getValueTypes(type, (ParameterizedType) genericType);
						}
						if (type == List.class) {
							List<Object> list = Config.parseList(v0, valueTypes, name, baseProps);
							boolean unchanged = false;
							@SuppressWarnings("unchecked")
							List<Object> vs = (List<Object>)f.get(clz);
							if (Config.listEquals(vs, list)) { // (vs == null && list == null) || (vs != null && vs.equals(list))) {
								if (ConfigGenerator.skipUnchangedLines) continue;
								builder.append("#");
								unchanged = true;
							}
							ConfigGenerator.generateList(builder, name, vs, valueTypes, unchanged);							
						} else if (type == Set.class) {
							Set<Object> set = Config.parseSet(v0, valueTypes, name, baseProps);
							boolean unchanged = false;
							@SuppressWarnings("unchecked")
							Set<Object> vs = (Set<Object>)f.get(clz);
							if (Config.setEquals(vs, set)) { // (vs == null && set == null) || (vs != null && vs.equals(set))) {
								if (ConfigGenerator.skipUnchangedLines) continue;
								builder.append("#");
								unchanged = true;
							}
							ConfigGenerator.generateSet(builder, name, vs, valueTypes, unchanged);
						} else { // Map.class
							Map<String, Object> map = Config.parseMap(v0, valueTypes, name, baseProps);
							boolean unchanged = false;
							@SuppressWarnings("unchecked")
							Map<String, Object> vs = (Map<String, Object>)f.get(clz);
							if (Config.mapEquals(vs, map)) { // (vs == null && map == null) || (vs != null && vs.equals(map))) {
								if (ConfigGenerator.skipUnchangedLines) continue;
								builder.append("#");
								unchanged = true;
							}
							ConfigGenerator.generateMap(builder, name, vs, valueTypes, unchanged);
						}
					} else if (type == Integer.class) {
						Integer v = (Integer) f.get(clz);
						if ((v0 != null && v0.equals(v == null ? Config.$null : String.valueOf(v))) || (v0 == null && v == null)) {
							if (ConfigGenerator.skipUnchangedLines) continue;
							builder.append("#");
						}
						builder.append(name).append("=").append(v == null ? Config.$null : v);
					} else if (type == Boolean.class) {
						Boolean v = (Boolean) f.get(clz);
						if ((v0 != null && v0.equals(v == null ? Config.$null : String.valueOf(v))) || (v0 == null && v == null)) {
							if (ConfigGenerator.skipUnchangedLines) continue;
							builder.append("#");
						}
						builder.append(name).append("=").append(v == null ? Config.$null : v);
					} else if (type == Long.class) {
						Long v = (Long) f.get(clz);
						if ((v0 != null && v0.equals(v == null ? Config.$null : String.valueOf(v))) || (v0 == null && v == null)) {
							if (ConfigGenerator.skipUnchangedLines) continue;
							builder.append("#");
						}
						builder.append(name).append("=").append(v == null ? Config.$null : v);
					} else if (type == Double.class) {
						Double v = (Double) f.get(clz);
						if ((v0 != null && v0.equals(v == null ? Config.$null : String.valueOf(v))) || (v0 == null && v == null)) {
							if (ConfigGenerator.skipUnchangedLines) continue;
							builder.append("#");
						}
						builder.append(name).append("=").append(v == null ? Config.$null : v);
					} else if (type == Float.class) {
						Float v = (Float) f.get(clz);
						if ((v0 != null && v0.equals(v == null ? Config.$null : String.valueOf(v))) || (v0 == null && v == null)) {
							if (ConfigGenerator.skipUnchangedLines) continue;
							builder.append("#");
						}
						builder.append(name).append("=").append(v == null ? Config.$null : v);
					} else if (type == Short.class) {
						Short v = (Short) f.get(clz);
						if ((v0 != null && v0.equals(v == null ? Config.$null : String.valueOf(v))) || (v0 == null && v == null)) {
							if (ConfigGenerator.skipUnchangedLines) continue;
							builder.append("#");
						}
						builder.append(name).append("=").append(v == null ? Config.$null : v);
					} else if (type == Byte.class) {
						Byte v = (Byte) f.get(clz);
						if ((v0 != null && v0.equals(v == null ? Config.$null : String.valueOf(v))) || (v0 == null && v == null)) {
							if (ConfigGenerator.skipUnchangedLines) continue;
							builder.append("#");
						}
						builder.append(name).append("=").append(v == null ? Config.$null : v);
					} else if (type == Character.class) {
						Character v = (Character) f.get(clz);
						if ((v0 != null && v0.equals(v == null ? Config.$null : String.valueOf(v))) || (v0 == null && v == null)) {
							if (ConfigGenerator.skipUnchangedLines) continue;
							builder.append("#");
						}
						builder.append(name).append("=").append(v == null ? Config.$null : v);
					} else {
						Object v = f.get(clz);
						Object o = Config.parseObject(v0, f.getType(), name, baseProps);
						boolean unchanged = false;
						if ((v == null && o == null) || (v != null && v.equals(o))) {
							if (ConfigGenerator.skipUnchangedLines) continue;
							builder.append("#");
							unchanged = true;
						}
						ConfigGenerator.generateObject(builder, name, v, unchanged);
					}
				} catch (Throwable e) {
					e.printStackTrace();
				}
			} // end of if
		} // end of for fields
		if (!first) {
			builder.append("\r\n");
		}
		return builder.toString();
	}

	public static boolean isConfigurationChanged(Class<?> clz, boolean combinedConfigs, Properties baseProps) {
		return isConfigurationChanged(clz, combinedConfigs, baseProps, null);
	}
	
	private static boolean isConfigurationChanged(Class<?> clz, boolean combinedConfigs, Properties baseProps, StringBuilder diffBuilder) {
		String keyPrefix = null;
		if (combinedConfigs) {
			keyPrefix = Config.getKeyPrefix(clz);
		}
		Field[] fields = clz.getDeclaredFields();
		int filteringModifiers = Modifier.PUBLIC;
		ConfigFieldFilter filter = Config.configurationFilters.get(clz.getName());
		if (filter != null && filter.modifiers >= 0) {
			filteringModifiers = filter.modifiers;
		}
		for (int i = 0; i < fields.length; i++) {
			Field f = fields[i];
			if (f != null) {
				int modifiers = f.getModifiers();
				if ((filteringModifiers <= 0 ? false : (modifiers & filteringModifiers) == 0)
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
				if (keyPrefix != null)  {
					name = keyPrefix + "." + name;
				}
				String v0 = baseProps.getProperty(name);
				if (v0 == null) {
					continue; // not existed in properties
				}
				if ((modifiers & Modifier.PUBLIC) == 0) {
					f.setAccessible(true);
				}
				if (Config.checkAndUpdateField(f, clz, v0, name, baseProps, false, diffBuilder)) {
					return true;
				}
			} // end of if
		} // end of for fields
		return false;
	}

	// FIXME: Multiple lines of given configuration item are not correctly kept or commented out.
	public static byte[] mergeWithIgnoringFields(byte[] responseBytes, byte[] localBytes, String keyPrefix, String[] ignoringFields) {
		if (ignoringFields == null || ignoringFields.length == 0) {
			return localBytes;
		}
		Map<String, String> filteredFields = new HashMap<String, String>();
		List<String> filteredAllFields = new ArrayList<String>();
		String localContent = new String(localBytes, Config.configFileEncoding);
		String[] localLines = localContent.split("(\r\n|\n|\r)");
		for (int i = 0; i < localLines.length; i++) {
			String line = localLines[i];
			for (int j = 0; j < ignoringFields.length; j++) {
				String f = ignoringFields[j];
				if (f == null || f.length() == 0) {
					continue;
				}
				if (keyPrefix == null && f.contains(".")) {
					continue;
				}
				if (keyPrefix != null) {
					String prefix = keyPrefix + ".";
					if (!f.startsWith(prefix)) {
						continue;
					}
					f = f.substring(prefix.length());
				}
				if (line.startsWith(f + "=")) {
					filteredFields.put(f, line);
					filteredAllFields.add(line);
					break;
				}
				if (line.startsWith(f + ".")) {
					int idx = line.indexOf('=');
					if (idx != -1) {
						String k = line.substring(0, idx);
						filteredFields.put(k, line);
						filteredAllFields.add(line);
						break;
					}
				}
				if (line.startsWith("#" + f + "=")) {
					if (!filteredFields.containsKey(f)) {
						filteredFields.put(f, line);
						filteredAllFields.add(line);
					}
					break;
				}
				if (line.startsWith("#" + f + ".")) {
					int idx = line.indexOf('=');
					if (idx != -1) {
						String k = line.substring(1, idx);
						if (!filteredFields.containsKey(k)) {
							filteredFields.put(k, line);
							filteredAllFields.add(line);
						}
						break;
					}
				}
			}
		}
		boolean filtered = false;
		String content = new String(responseBytes, Config.configFileEncoding);
		String[] lines = content.split("(\r\n|\n|\r)");
		for (int i = 0; i < lines.length; i++) {
			String line = lines[i];
			for (int j = 0; j < ignoringFields.length; j++) {
				String f = ignoringFields[j];
				if (f == null || f.length() == 0) {
					continue;
				}
				if (keyPrefix == null && f.contains(".")) {
					continue;
				}
				if (keyPrefix != null) {
					String prefix = keyPrefix + ".";
					if (!f.startsWith(prefix)) {
						continue;
					}
					f = f.substring(prefix.length());
				}
				if (line.startsWith(f + "=")) {
					filtered = true;
					String newLine = filteredFields.get(f);
					if (newLine != null) {
						lines[i] = newLine;
						filteredAllFields.remove(newLine);
					} else {
						lines[i] = "#" + lines[i]; // comment it out
					}
					break;
				}
				if (line.startsWith(f + ".")) {
					int idx = line.indexOf('=');
					if (idx != -1) {
						String k = line.substring(0, idx);
						filtered = true;
						String newLine = filteredFields.get(k);
						if (newLine != null) {
							lines[i] = newLine;
							filteredAllFields.remove(newLine);
						} else {
							lines[i] = "#" + lines[i]; // comment it out
						}
						break;
					}
				}
				if (line.startsWith("#" + f + "=")) {
					filtered = true;
					String newLine = filteredFields.get(f);
					if (newLine != null) {
						lines[i] = newLine;
						filteredAllFields.remove(newLine);
					}
					break;
				}
				if (line.startsWith("#" + f + ".")) {
					int idx = line.indexOf('=');
					if (idx != -1) {
						String k = line.substring(1, idx);
						filtered = true;
						String newLine = filteredFields.get(k);
						if (newLine != null) {
							lines[i] = newLine;
							filteredAllFields.remove(newLine);
						}
						break;
					}
				}
			}
		}
		if (filtered) {
			for (int i = lines.length - 1; i >= 0; i--) {
				String line = lines[i];
				for (int j = 0; j < ignoringFields.length; j++) {
					String f = ignoringFields[j];
					if (f == null || f.length() == 0) {
						continue;
					}
					if (keyPrefix == null && f.contains(".")) {
						continue;
					}
					if (keyPrefix != null) {
						String prefix = keyPrefix + ".";
						if (!f.startsWith(prefix)) {
							continue;
						}
						f = f.substring(prefix.length());
					}
					String newLine = null;
					if (line.startsWith(f + "=")) {
						newLine = filteredFields.get(f);
					}
					if (newLine == null && line.startsWith(f + ".")) {
						int idx = line.indexOf('=');
						if (idx != -1) {
							String k = line.substring(0, idx);
							newLine = filteredFields.get(k);
						}
					}
					if (newLine == null && line.startsWith("#" + f + "=")) {
						newLine = filteredFields.get(f);
					}
					if (newLine == null && line.startsWith("#" + f + ".")) {
						int idx = line.indexOf('=');
						if (idx != -1) {
							String k = line.substring(1, idx);
							newLine = filteredFields.get(k);
						}
					}
					int size = filteredAllFields.size();
					if (newLine != null && size > 0) {
						StringBuilder newLineBuilder = new StringBuilder(newLine);
						for (String fLine : filteredAllFields.toArray(new String[size])) {
							if (fLine.startsWith(f + "=") || fLine.startsWith(f + ".") || fLine.startsWith("#" + f + "=") || fLine.startsWith("#" + f + ".")) {
								newLineBuilder.append("\r\n").append(fLine);
								filteredAllFields.remove(fLine);
							}
						}
						lines[i] = newLineBuilder.toString();
					}
				}
			}
			int size = filteredAllFields.size();
			if (size > 0) {
				// Insert missed local ignored configurations back to correct position, if possible
				for (String fLine : filteredAllFields.toArray(new String[size])) {
					String prevLine = null;
					String nextLine = null;
					for (int i = 0; i < localLines.length; i++) {
						String line = localLines[i];
						if (fLine.equals(line)) {
							if (i > 0) {
								prevLine = localLines[i - 1];
							}
							if (i < localLines.length - 1) {
								nextLine = localLines[i + 1];
							}
							break;
						}
					}
					boolean inserted = false;
					if (nextLine != null) {
						for (int i = lines.length - 1; i >= 0; i--) {
							if (lines[i].startsWith(nextLine)) {
								StringBuilder newLineBuilder = new StringBuilder(fLine);
								newLineBuilder.append("\r\n").append(lines[i]);
								lines[i] = newLineBuilder.toString();
								inserted = true;
								break;
							}
						}
					}
					if (!inserted && prevLine != null) {
						for (int i = 0; i < lines.length; i++) {
							if (lines[i].startsWith(prevLine)) {
								StringBuilder newLineBuilder = new StringBuilder(lines[i]);
								newLineBuilder.append("\r\n").append(fLine);
								lines[i] = newLineBuilder.toString();
								inserted = true;
								break;
							}
						}
					}
					if (inserted) {
						filteredAllFields.remove(fLine);
					}
				}
			}
			StringBuilder builder = new StringBuilder();
			for (int i = 0; i < lines.length; i++) {
				builder.append(lines[i]).append("\r\n");
			}
			// Append missed local ignored configurations to the end
			for (String fLine : filteredAllFields) {
				builder.append(fLine).append("\r\n");
			}
			return builder.toString().getBytes(Config.configFileEncoding);
		} else {
			return responseBytes;
		}
	}
	
	private static boolean isConfigurationFileIncorrect(boolean multipleConfigs, String file, List<Class<?>> allConfigs, StringBuilder diffBuilder) {
		Properties defaultProps = new Properties();
		FileInputStream fis = null;
		try {
			fis = new FileInputStream(file);
			defaultProps.load(fis);
		} catch (IOException e1) {
			e1.printStackTrace();
		} finally {
			if (fis != null) {
				try {
					fis.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
				fis = null;
			}
		}
		
		String fileExt = Config.configurationFileExtension;
		int idx = file.lastIndexOf('.');
		if (idx != -1) {
			String ext = file.substring(idx + 1);
			if (ext.length() > 0) {
				fileExt = file.substring(idx);
			}
		}
		for (Iterator<Class<?>> itr = allConfigs.iterator(); itr.hasNext();) {
			Class<?> clz = (Class<?>) itr.next();
			String keyPrefix = Config.getKeyPrefix(clz);
			Properties props = null;
			boolean globalConfig = !multipleConfigs || keyPrefix == null || keyPrefix.length() == 0;
			if (globalConfig) {
				props = defaultProps;
			} else {
				String folder = file;
				File folderFile = new File(folder);
				if (folderFile.isFile() || !folderFile.exists() || folder.endsWith(fileExt)) {
					folder = folderFile.getParent();
				}
				File configFile = new File(folder, Config.parseFilePath(keyPrefix + fileExt));
				if (!configFile.exists()) {
					props = defaultProps;
				} else {
					props = new Properties();
					fis = null;
					try {
						fis = new FileInputStream(configFile);
						props.load(fis);
					} catch (IOException e1) {
						e1.printStackTrace();
					} finally {
						if (fis != null) {
							try {
								fis.close();
							} catch (IOException e) {
								e.printStackTrace();
							}
							fis = null;
						}
					}
				}
			}
			if (isConfigurationChanged(clz, false, props, diffBuilder)) { // incorrect configuration
				return true;
			}
		} // end of for classes
		return false;
	}

	/**
	 * Read configuration from merging files and then generate delta configuration files based on
	 * default configuration from classes' static fields.
	 * 
	 * If merging file does not change the value of static field, then it will output a configuration
	 * line with prefix "#", notifying it is not modified.
	 * 
	 * The target files generated should have lots of lines with comment prefix "#" an only a
	 * few lines are not prefixed with "#". 
	 * 
	 * @param mularetipleConfigs
	 * @param targetFile File to be written with merged configurations
	 * @param mergingFile File to with old data to be merging into new configuration
	 * @param classes
	 * @param checking
	 */
	public static void mergeDeltaConfiguration(final boolean multipleConfigs, String targetFile, String mergingFile, Class<?>[] classes, IConfigurable checking) {
		if (multipleConfigs && new File(targetFile).getParent().equals(new File(mergingFile).getParent())) {
			System.out.println("Warning: Merging multiple configuration files in the same folder may result in wrong delta file.");
			return;
		}
		List<Class<?>> allConfigs = new ArrayList<Class<?>>();
		for (int i = 0; i < classes.length; i++) {
			allConfigs.add(classes[i]);
		}

		final Map<String, String> allFields = new HashMap<String, String>();
		ConfigGenerator.ICheckConfiguration warnChecking = new ConfigGenerator.ICheckConfiguration() {
			
			@Override
			public void check(String clazzName, String fieldName) {
				if (allFields.containsKey(fieldName)) {
					System.out.println("[WARN] " + clazzName + "." + fieldName + " is duplicated with " + (allFields.get(fieldName)));
				}
				allFields.put(fieldName, clazzName + "." + fieldName);
			}
			
		};

		StringBuilder defaultBuilder = new StringBuilder();
		Map<String, StringBuilder> configBuilders = new HashMap<String, StringBuilder>();
		for (Iterator<Class<?>> itr = allConfigs.iterator(); itr.hasNext();) {
			Class<?> clz = (Class<?>) itr.next();
			String keyPrefix = Config.getKeyPrefix(clz);
			StringBuilder builder = null;
			boolean globalConfig = !multipleConfigs || keyPrefix == null || keyPrefix.length() == 0;
			if (globalConfig) {
				builder = defaultBuilder;
				if (builder.length() > 0) {
					builder.append("\r\n");
				}
				builder.append("# ").append(clz.getSimpleName()).append("\r\n");
			} else {
				builder = new StringBuilder();
			}
			builder.append(ConfigGenerator.generateConfigruation(clz, globalConfig, warnChecking));
			if (!globalConfig) { // multiple configurations
				configBuilders.put(clz.getName(), builder);
			}
		} // end of for classes
		
		Properties defaultProps = new Properties();
		String source = defaultBuilder.toString();
		InputStream bais = new ByteArrayInputStream(source.getBytes(Config.configFileEncoding));
		try {
			defaultProps.load(bais);
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		
		Config.initialize(mergingFile, null, multipleConfigs);
		for (Iterator<Class<?>> itr = allConfigs.iterator(); itr.hasNext();) {
			Class<?> clz = (Class<?>) itr.next();
			Config.registerUpdatingListener(clz);
		}
		
		if (checking != null) {
			checking.updateConfigs();
		}
		
		String fileExt = Config.configurationFileExtension;
		int idx = targetFile.lastIndexOf('.');
		if (idx != -1) {
			String ext = targetFile.substring(idx + 1);
			if (ext.length() > 0) {
				fileExt = targetFile.substring(idx);
			}
		}
		defaultBuilder = new StringBuilder();
		for (Iterator<Class<?>> itr = allConfigs.iterator(); itr.hasNext();) {
			Class<?> clz = (Class<?>) itr.next();
			String keyPrefix = Config.getKeyPrefix(clz);
			Properties props = null;
			StringBuilder builder = null;
			boolean globalConfig = !multipleConfigs || keyPrefix == null || keyPrefix.length() == 0;
			if (globalConfig) {
				builder = defaultBuilder;
				if (builder.length() > 0) {
					builder.append("\r\n");
				}
				builder.append("# ").append(clz.getSimpleName()).append("\r\n");
				props = defaultProps;
			} else {
				builder = new StringBuilder();
				StringBuilder configBuilder = configBuilders.get(clz.getName());
				if (configBuilder == null) {
					props = defaultProps;
				} else {
					props = new Properties();
					InputStream configBAIS = new ByteArrayInputStream(configBuilder.toString().getBytes(Config.configFileEncoding));
					try {
						props.load(configBAIS);
					} catch (IOException e1) {
						e1.printStackTrace();
					}
				}
			}
			builder.append(generateDeltaConfiguration(clz, false, props));
			if (!globalConfig) { // multiple configurations
				source = builder.toString();
				String folder = targetFile;
				File folderFile = new File(folder);
				if (folderFile.isFile() || !folderFile.exists() || folder.endsWith(fileExt)) {
					folder = folderFile.getParent();
				}
				File configFile = new File(folder, Config.parseFilePath(keyPrefix + fileExt));
				String oldSource = ConfigGenerator.readFile(configFile);
				if (!source.equals(oldSource)) {
					System.out.println(((oldSource == null || oldSource.length() == 0) ? "Write " : "Update ") + keyPrefix + fileExt);
					folderFile = new File(folder);
					if (!folderFile.exists()) {
						folderFile.mkdirs();
					}
					FileOutputStream fos = null;
					try {
						fos = new FileOutputStream(configFile);
						fos.write(source.getBytes(Config.configFileEncoding));
					} catch (IOException e) {
						e.printStackTrace();
					} finally {
						if (fos != null) {
							try {
								fos.close();
							} catch (IOException e) {
								e.printStackTrace();
							}
						}
					}
				} // end if
			}
		} // end of for classes
		
		source = defaultBuilder.toString();
		String oldSource = ConfigGenerator.readFile(new File(targetFile));
		if (!source.equals(oldSource)) {
			System.out.println(((oldSource == null || oldSource.length() == 0) ? "Write " : "Update ") + targetFile);
			FileOutputStream fos = null;
			try {
				fos = new FileOutputStream(targetFile);
				fos.write(source.getBytes(Config.configFileEncoding));
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				if (fos != null) {
					try {
						fos.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		} // end if
		Config.initialize(targetFile, null, multipleConfigs);
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		StringBuilder diffBuilder = new StringBuilder();
		if (isConfigurationFileIncorrect(multipleConfigs, targetFile, allConfigs, diffBuilder)) {
			// not the same, changed
			System.out.println("Saved " + targetFile + " is incorrect!");
			System.out.println(diffBuilder.toString());
		} else {
			System.out.println("Done!");
		}
	}

	public static void main(String[] args) {
		if (args == null || args.length < 2) {
			System.out.println("Usage: " + ConfigMerger.class.getName()
					+ " [--multiple-configs] [--compact-object] [--compact-array] [--compact-list] [--compact-set] [--compact-map]"
					+ " <target config file> <merge config file> <config class> [config class ...] [checking class]");
			return;
		}
		boolean multipleConfigs = false;
		int index = 0;
		ConfigGenerator.readableArrayFormat = true;
		ConfigGenerator.readableListFormat = true;
		ConfigGenerator.readableMapFormat = true;
		ConfigGenerator.readableObjectFormat = true;
		ConfigGenerator.readableObjectFormat = true;
		do {
			String nextArg = args[index];
			if ("--multiple-configs".equals(nextArg)) {
				multipleConfigs = true;
				index++;
			} else if ("--compact-object".equals(nextArg)) {
				ConfigGenerator.readableObjectFormat = false;
				index++;
			} else if ("--compact-array".equals(nextArg)) {
				ConfigGenerator.readableArrayFormat = false;
				index++;
			} else if ("--compact-list".equals(nextArg)) {
				ConfigGenerator.readableListFormat = false;
				index++;
			} else if ("--compact-set".equals(nextArg)) {
				ConfigGenerator.readableSetFormat = false;
				index++;
			} else if ("--compact-map".equals(nextArg)) {
				ConfigGenerator.readableMapFormat = false;
				index++;
			} else {
				break;
			}
		} while (true);
		String targetFile = args[index];
		String mergingFile = args[index + 1];
		IConfigurable checking = null;
		List<Class<?>> allNames = new ArrayList<Class<?>>();
		for (int i = index + 2; i < args.length; i++) {
			String clazz = args[i];
			if (clazz != null && clazz.length() > 0) {
				try {
					Class<?> c = Class.forName(clazz);
					if (IConfigurable.class.isAssignableFrom(c)) {
						checking = (IConfigurable) c.newInstance();
					} else {
						allNames.add(c);
					}
				} catch (ClassNotFoundException e) {
					e.printStackTrace();
				} catch (InstantiationException e) {
					e.printStackTrace();
				} catch (IllegalAccessException e) {
					e.printStackTrace();
				}
			}
		}
		
		Class<?>[] classes = allNames.toArray(new Class<?>[allNames.size()]);
		mergeDeltaConfiguration(multipleConfigs, targetFile, mergingFile, classes, checking);
	}

}
