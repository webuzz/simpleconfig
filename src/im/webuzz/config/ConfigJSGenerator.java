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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Generate configuration default file in JavaScript format.
 * 
 * @author zhourenjian
 *
 */
public class ConfigJSGenerator {

	public static interface ICheckConfiguration {
		
		public void check(String clazzName, String fieldName);
		
	}
	
	protected static final String $null = "null";
	protected static final String $empty = "[]";
	protected static final String $arrayOpen = "[";
	protected static final String $listOpen = "[";
	protected static final String $setOpen = "[";
	protected static final String $mapOpen = "{";
	protected static final String $objectOpen = "{";
	protected static final String $arrayClose = "]";
	protected static final String $listClose = "]";
	protected static final String $setClose = "]";
	protected static final String $mapClose = "}";
	protected static final String $objectClose = "}";


	public static boolean readableArrayFormat = false; // For array
	public static boolean readableSetFormat = false; // For set
	public static boolean readableListFormat = false; // For true
	public static boolean readableMapFormat = false; // For true
	public static boolean readableObjectFormat = false; // For true
	public static boolean skipUnchangedLines = false;
	public static boolean skipObjectUnchangedFields = true;

	public static String generateConfigruation(Class<?> clz, boolean combinedConfigs, ICheckConfiguration checking) {
		StringBuilder builder = new StringBuilder();
		builder.append("{\r\n");
		//boolean skipUnchangedLines = false;
		String keyPrefix = null;
		if (combinedConfigs) { // generating combined configurations into one file
			keyPrefix = Config.getKeyPrefix(clz);
		}
		boolean fieldGenerated = false;
		boolean linebreakGenerated = false;
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
				if ((modifiers & Modifier.PUBLIC) == 0) {
					f.setAccessible(true);
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
				/*
				 * There are some temporary fields in Config and should
				 * not be generated down for remote server.
				 */
				boolean ignoringTemporyField = false;
				if (clz == Config.class && (name.equals("configurationFile") || name.equals("configurationFolder"))) {
					ignoringTemporyField = true;
				}
				if (keyPrefix != null)  {
					name = getPrefixIndent(keyPrefix) + "\t" + name;
				}
				if (checking != null) {
					checking.check(clz.getName(), name);
				}
				if (fieldGenerated && !linebreakGenerated) {
					builder.append(",\r\n");
					linebreakGenerated = true;
				}
				fieldGenerated = generateField(f, name, builder, clz, ignoringTemporyField);
				if (fieldGenerated) {
					linebreakGenerated = false;
				}
			} // end of if
		} // end of for fields
		if (fieldGenerated && !linebreakGenerated) {
			builder.append("\r\n");
		}
		builder.append("}\r\n");
		return builder.toString();
	}

	/*
	 * Check to see if given object can be serialized as a plain object or simple enough for a single line configuration
	 */
	static boolean isPlainObject(Object o) {
		if (o == null) {
			return true;
		}
		Class<?> clz = o.getClass();
		Field[] fields = clz.getDeclaredFields();
		int filteringModifiers = Modifier.PUBLIC;
		ConfigFieldFilter filter = Config.configurationFilters.get(clz.getName());
		if (filter != null && filter.modifiers >= 0) {
			filteringModifiers = filter.modifiers;
		}
		for (int i = 0; i < fields.length; i++) {
			Field f = fields[i];
			if (f == null) {
				continue;
			}
			int modifiers = f.getModifiers();
			if ((filteringModifiers <= 0 ? false : (modifiers & filteringModifiers) == 0)
					|| (modifiers & Modifier.STATIC) != 0
					|| (modifiers & Modifier.FINAL) != 0) {
				continue; // ignore
			}
			Class<?> type = f.getType();
			if (type == int.class || type == long.class || type == String.class || type == boolean.class
					|| type == double.class || type == float.class || type == byte.class
					|| type == short.class || type == char.class) {
				continue; // ignore
			}
			if (type == Integer.class || type == Long.class || type == Boolean.class
					|| type == Double.class || type == Float.class || type == Byte.class
					|| type == Short.class || type == Character.class) {
				continue; // ignore
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
			try {
				if (type.isArray()) {
					Class<?> componentType = type.getComponentType();
					if (isBasicType(componentType)) {
						Object[] vs = (Object[]) f.get(o);
						if (vs == null || vs.length <= 1) {
							continue;
						}
						return false; // complicate object
					} else if (componentType == long.class) {
						long[] vs = (long[]) f.get(o);
						if (vs == null || vs.length <= 1) {
							continue;
						}
						return false; // complicate object
					} else if (componentType == int.class) {
						int[] vs = (int[]) f.get(o);
						if (vs == null || vs.length <= 1) {
							continue;
						}
						return false; // complicate object
					} else if (componentType == boolean.class) {
						boolean[] vs = (boolean[]) f.get(o);
						if (vs == null || vs.length <= 1) {
							continue;
						}
						return false; // complicate object
					} else if (componentType == double.class) {
						double[] vs = (double[]) f.get(o);
						if (vs == null || vs.length <= 1) {
							continue;
						}
						return false; // complicate object
					} else if (componentType == float.class) {
						float[] vs = (float[]) f.get(o);
						if (vs == null || vs.length <= 1) {
							continue;
						}
						return false; // complicate object
					} else if (componentType == short.class) {
						short[] vs = (short[]) f.get(o);
						if (vs == null || vs.length <= 1) {
							continue;
						}
						return false; // complicate object
					} else if (componentType == byte.class) {
						byte[] vs = (byte[]) f.get(o);
						if (vs == null || vs.length <= 1) {
							continue;
						}
						return false; // complicate object
					} else if (componentType == char.class) {
						char[] vs = (char[]) f.get(o);
						if (vs == null || vs.length <= 1) {
							continue;
						}
						return false; // complicate object
					} else {
						Object[] vs = (Object[]) f.get(o);
						if (vs == null || vs.length == 0) {
							continue;
						} else if (vs.length == 1) {
							if (vs[0] == null) {
								continue;
							} else if (isBasicType(vs[0].getClass())) {
								continue;
							}
						}
						return false; // complicate object
					}
				} else if (type == List.class) {
					@SuppressWarnings("unchecked")
					List<Object> vs = (List<Object>) f.get(o);
					if (vs == null || vs.size() == 0) {
						continue;
					} else if (vs.size() == 1) {
						Object el = vs.get(0);
						if (el == null) {
							continue;
						} else if (isBasicType(el.getClass())) {
							continue;
						}
					}
					return false; // complicate object
				} else if (type == Set.class) {
					@SuppressWarnings("unchecked")
					Set<Object> vs = (Set<Object>) f.get(o);
					if (vs == null || vs.size() == 0) {
						continue;
					} else if (vs.size() == 1) {
						Object el = vs.iterator().next();
						if (el == null) {
							continue;
						} else if (isBasicType(el.getClass())) {
							continue;
						}
					}
					return false; // complicate object
				} else if (type == Map.class) {
					@SuppressWarnings("unchecked")
					Map<String, Object> vs = (Map<String, Object>) f.get(o);
					if (vs == null || vs.size() == 0) {
						continue;
					} else if (vs.size() == 1) {
						Object el = vs.values().iterator().next();
						if (el == null) {
							continue;
						} else if (isBasicType(el.getClass())) {
							continue;
						}
					}
					return false; // complicate object
				} else {
					Object v = f.get(o);
					if (v == null) {
						continue;
					}
					return false; // complicate object
				}
			} catch (Throwable e) {
			}
		}
		return true;
	}

	private static boolean isBasicType(Class<?> typ) {
		return typ == String.class || typ == Integer.class || typ == Long.class
				|| typ == Boolean.class || typ == Double.class || typ == Float.class
				|| typ == Short.class || typ == Byte.class || typ == Character.class;
	}

	static void generateTypeObject(StringBuilder builder, String keyPrefix, Object o, Class<?>[] valueTypes, boolean unchanged) {
		Class<?> type = valueTypes[0];
		if (type == Integer.class) {
			builder.append(keyPrefix).append(":").append(o == null ? $null : o);
		} else if (type == String.class) {
			generateString(builder, keyPrefix, (String) o);
		} else if (type == Boolean.class) {
			builder.append(keyPrefix).append(":").append(o == null ? $null : o);
		} else if (type == Long.class) {
			builder.append(keyPrefix).append(":").append(o == null ? $null : o);
		} else if (type == int[].class) {
			generateIntegerArray(builder, keyPrefix, (int[]) o, unchanged);
		} else if (type == long[].class) {
			generateLongArray(builder, keyPrefix, (long[]) o, unchanged);
		} else if (type == boolean[].class) {
			generateBooleanArray(builder, keyPrefix, (boolean[]) o, unchanged);
		} else if (type == double[].class) {
			generateDoubleArray(builder, keyPrefix, (double[]) o, unchanged);
		} else if (type == float[].class) {
			generateFloatArray(builder, keyPrefix, (float[]) o, unchanged);
		} else if (type == short[].class) {
			generateShortArray(builder, keyPrefix, (short[]) o, unchanged);
		} else if (type == byte[].class) {
			generateByteArray(builder, keyPrefix, (byte[]) o, unchanged);
		} else if (type == char[].class) {
			generateCharArray(builder, keyPrefix, (char[]) o, unchanged);
		} else if (type.isArray()) {
			Class<?> compType = type.getComponentType();
			generateArray(builder, keyPrefix, (Object[]) o, new Class<?>[] { compType }, unchanged);
		} else if (type == Double.class) {
			builder.append(keyPrefix).append(":").append(o == null ? $null : o);
		} else if (type == Float.class) {
			builder.append(keyPrefix).append(":").append(o == null ? $null : o);
		} else if (type == Short.class) {
			builder.append(keyPrefix).append(":").append(o == null ? $null : o);
		} else if (type == Byte.class) {
			builder.append(keyPrefix).append(":").append(o == null ? $null : o);
		} else if (type == Character.class) {
			builder.append(keyPrefix).append(":").append(o == null ? $null : o);
		} else if (type == List.class || type == Set.class || type == Map.class) {
			Class<?>[] nextValueTypes = null;
			if (valueTypes.length > 1) {
				nextValueTypes = new Class<?>[valueTypes.length - 1];
				System.arraycopy(valueTypes, 1, nextValueTypes, 0, nextValueTypes.length);
			}
			if (type == List.class) { // List<Object>
				@SuppressWarnings("unchecked")
				List<Object> vs = (List<Object>) o;
				generateList(builder, keyPrefix, vs, nextValueTypes, unchanged);
			} else if (type == Set.class) { // Set<Object>
				@SuppressWarnings("unchecked")
				Set<Object> vs = (Set<Object>) o;
				generateSet(builder, keyPrefix, vs, nextValueTypes, unchanged);
			} else { // if (type == Map.class) { // Map<String, Object>
				@SuppressWarnings("unchecked")
				Map<String, Object> vs = (Map<String, Object>) o;
				generateMap(builder, keyPrefix, vs, nextValueTypes, unchanged);
			}
		} else {
			generateObject(builder, keyPrefix, o, unchanged);
		}
	}
	
	static boolean generateField(Field f, String name, StringBuilder builder, Object o, boolean unchanged) {
		Class<?> type = f.getType();
		try {
			if (type == int.class) {
				int v = f.getInt(o);
				if (v == 0) {
					if (skipUnchangedLines) return false;
					builder.append("//");
				} else if (unchanged) {
					builder.append("//");
				}
				builder.append(name).append(":").append(v);
				return true;
			} else if (type == String.class) {
				String v = (String) f.get(o);
				if (v == null) {
					if (skipUnchangedLines) return false;
					builder.append("//");
				} else if (unchanged) {
					builder.append("//");
				}
				generateString(builder, name, v);
				return true;
			} else if (type == boolean.class) {
				boolean v = f.getBoolean(o);
				if (v == false) {
					if (skipUnchangedLines) return false;
					builder.append("//");
				} else if (unchanged) {
					builder.append("//");
				}
				builder.append(name).append(":").append(v);
				return true;
			} else if (type == long.class) {
				long v = f.getLong(o);
				if (v == 0) {
					if (skipUnchangedLines) return false;
					builder.append("//");
				} else if (unchanged) {
					builder.append("//");
				}
				builder.append(name).append(":").append(v);
				return true;
			} else if (type == int[].class) {
				int[] vs = (int[])f.get(o);
				if (vs == null) {
					if (skipUnchangedLines) return false;
					builder.append("//");
				} else if (unchanged) {
					builder.append("//");
				}
				generateIntegerArray(builder, name, vs, unchanged);
				return true;
			} else if (type == long[].class) {
				long[] vs = (long[])f.get(o);
				if (vs == null) {
					if (skipUnchangedLines) return false;
					builder.append("//");
				} else if (unchanged) {
					builder.append("//");
				}
				generateLongArray(builder, name, vs, unchanged);
				return true;
			} else if (type == boolean[].class) {
				boolean[] vs = (boolean[])f.get(o);
				if (vs == null) {
					if (skipUnchangedLines) return false;
					builder.append("//");
				} else if (unchanged) {
					builder.append("//");
				}
				generateBooleanArray(builder, name, vs, unchanged);
				return true;
			} else if (type == double[].class) {
				double[] vs = (double[])f.get(o);
				if (vs == null) {
					if (skipUnchangedLines) return false;
					builder.append("//");
				} else if (unchanged) {
					builder.append("//");
				}
				generateDoubleArray(builder, name, vs, unchanged);
				return true;
			} else if (type == float[].class) {
				float[] vs = (float[])f.get(o);
				if (vs == null) {
					if (skipUnchangedLines) return false;
					builder.append("//");
				} else if (unchanged) {
					builder.append("//");
				}
				generateFloatArray(builder, name, vs, unchanged);
				return true;
			} else if (type == short[].class) {
				short[] vs = (short[])f.get(o);
				if (vs == null) {
					if (skipUnchangedLines) return false;
					builder.append("//");
				} else if (unchanged) {
					builder.append("//");
				}
				generateShortArray(builder, name, vs, unchanged);
				return true;
			} else if (type == byte[].class) {
				byte[] vs = (byte[])f.get(o);
				if (vs == null) {
					if (skipUnchangedLines) return false;
					builder.append("//");
				} else if (unchanged) {
					builder.append("//");
				}
				generateByteArray(builder, name, vs, unchanged);
				return true;
			} else if (type == char[].class) {
				char[] vs = (char[])f.get(o);
				if (vs == null) {
					if (skipUnchangedLines) return false;
					builder.append("//");
				} else if (unchanged) {
					builder.append("//");
				}
				generateCharArray(builder, name, vs, unchanged);
				return true;
			} else if (type.isArray()) {
				Class<?> compType = type.getComponentType();
				Object[] vs = (Object[])f.get(o);
				if (vs == null) {
					if (skipUnchangedLines) return false;
					builder.append("//");
				} else if (unchanged) {
					builder.append("//");
				}
				generateArray(builder, name, vs, new Class<?>[] { compType }, unchanged);
				return true;
			} else if (type == double.class) {
				double v = f.getDouble(o);
				if (v == 0) {
					if (skipUnchangedLines) return false;
					builder.append("//");
				} else if (unchanged) {
					builder.append("//");
				}
				builder.append(name).append(":").append(v);
				return true;
			} else if (type == float.class) {
				float v = f.getFloat(o);
				if (v == 0) {
					if (skipUnchangedLines) return false;
					builder.append("//");
				} else if (unchanged) {
					builder.append("//");
				}
				builder.append(name).append(":").append(v);
				return true;
			} else if (type == short.class) {
				short v = f.getShort(o);
				if (v == 0) {
					if (skipUnchangedLines) return false;
					builder.append("//");
				} else if (unchanged) {
					builder.append("//");
				}
				builder.append(name).append(":").append(v);
				return true;
			} else if (type == byte.class) {
				byte v = f.getByte(o);
				if (v == 0) {
					if (skipUnchangedLines) return false;
					builder.append("//");
				} else if (unchanged) {
					builder.append("//");
				}
				builder.append(name).append(":").append(v);
				return true;
			} else if (type == char.class) {
				char v = f.getChar(o);
				if (v == 0) {
					if (skipUnchangedLines) return false;
					builder.append("//");
				} else if (unchanged) {
					builder.append("//");
				}
				builder.append(name).append(":").append(v);
				return true;
			} else if (type == List.class || type == Set.class || type == Map.class) { // List<Object>
				Class<?>[] valueTypes = null;
				Type genericType = f.getGenericType();
				if (genericType instanceof ParameterizedType) {
					valueTypes = Config.getValueTypes(type, (ParameterizedType) genericType);
				}
				if (type == List.class) {
					@SuppressWarnings("unchecked")
					List<Object> vs = (List<Object>)f.get(o);
					if (vs == null) {
						if (skipUnchangedLines) return false;
						builder.append("//");
					} else if (unchanged) {
						builder.append("//");
					}
					generateList(builder, name, vs, valueTypes, unchanged);
				} else if (type == Set.class) {
					@SuppressWarnings("unchecked")
					Set<Object> vs = (Set<Object>)f.get(o);
					if (vs == null) {
						if (skipUnchangedLines) return false;
						builder.append("//");
					} else if (unchanged) {
						builder.append("//");
					}
					generateSet(builder, name, vs, valueTypes, unchanged);
				} else { // Map.class
					@SuppressWarnings("unchecked")
					Map<String, Object> vs = (Map<String, Object>) f.get(o);
					if (vs == null) {
						if (skipUnchangedLines) return false;
						builder.append("//");
					} else if (unchanged) {
						builder.append("//");
					}
					generateMap(builder, name, vs, valueTypes, unchanged);
				}
				return true;
			} else if (type == Integer.class) {
				Integer v = (Integer) f.get(o);
				if (v == null || v.intValue() == 0) {
					if (skipUnchangedLines) return false;
					builder.append("//");
				} else if (unchanged) {
					builder.append("//");
				}
				builder.append(name).append(":").append(v == null ? $null : v);
				return true;
			} else if (type == Boolean.class) {
				Boolean v = (Boolean) f.get(o);
				if (v == null || v.booleanValue() == false) {
					if (skipUnchangedLines) return false;
					builder.append("//");
				} else if (unchanged) {
					builder.append("//");
				}
				builder.append(name).append(":").append(v == null ? $null : v);
				return true;
			} else if (type == Long.class) {
				Long v = (Long) f.get(o);
				if (v == null || v.longValue() == 0) {
					if (skipUnchangedLines) return false;
					builder.append("//");
				} else if (unchanged) {
					builder.append("//");
				}
				builder.append(name).append(":").append(v == null ? $null : v);
				return true;
			} else if (type == Double.class) {
				Double v = (Double) f.get(o);
				if (v == null || v.doubleValue() == 0) {
					if (skipUnchangedLines) return false;
					builder.append("//");
				} else if (unchanged) {
					builder.append("//");
				}
				builder.append(name).append(":").append(v == null ? $null : v);
				return true;
			} else if (type == Float.class) {
				Float v = (Float) f.get(o);
				if (v == null || v.floatValue() == 0) {
					if (skipUnchangedLines) return false;
					builder.append("//");
				} else if (unchanged) {
					builder.append("//");
				}
				builder.append(name).append(":").append(v == null ? $null : v);
				return true;
			} else if (type == Short.class) {
				Short v = (Short) f.get(o);
				if (v == null || v.shortValue() == 0) {
					if (skipUnchangedLines) return false;
					builder.append("//");
				} else if (unchanged) {
					builder.append("//");
				}
				builder.append(name).append(":").append(v == null ? $null : v);
				return true;
			} else if (type == Byte.class) {
				Byte v = (Byte) f.get(o);
				if (v == null || v.byteValue() == 0) {
					if (skipUnchangedLines) return false;
					builder.append("//");
				} else if (unchanged) {
					builder.append("//");
				}
				builder.append(name).append(":").append(v == null ? $null : v);
				return true;
			} else if (type == Character.class) {
				Character v = (Character) f.get(o);
				if (v == null || v.charValue() == 0) {
					if (skipUnchangedLines) return false;
					builder.append("//");
				} else if (unchanged) {
					builder.append("//");
				}
				builder.append(name).append(":").append(v == null ? $null : v);
				return true;
			} else {
				Object v = f.get(o);
				if (v == null) {
					if (skipUnchangedLines) return false;
					builder.append("//");
				} else if (unchanged) {
					builder.append("//");
				}
				generateObject(builder, name, v, unchanged);
				return true;
			}
		} catch (Throwable e) {
			e.printStackTrace();
		}
		return false;
	}
	
	static String getPrefixIndent(String prefix) {
		int nameLength = prefix.length();
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < nameLength; i++) {
			if (prefix.charAt(i) == '\t') {
				builder.append('\t');
			}
		}
		return builder.toString();
	}

	static Set<String> keywords = new HashSet<String>(Arrays.asList(new String[] {
			"class", /*"java", "javax", "sun", */"for", "while", "do", "in", "return", "function", "var", 
			"class", "pubic", "protected", "private", "new", "delete",
			"static", "package", "import", "extends", "implements",
			"instanceof", "typeof", "void", "if", "this", "super",
			"prototype", "else", "break", "true", "fasle", "try",
			"catch", "throw", "throws", "continue", "switch", "default",
			"case", "export", "import", "const", /*"label", */"with",
			"arguments",
			"valueOf"
	}));

	static void generateObject(StringBuilder builder, String keyPrefix, Object o, boolean unchanged) {
		if (o instanceof Object[]) {
			generateArray(builder, keyPrefix, (Object[]) o, new Class<?>[] { o.getClass().getComponentType() }, unchanged);
			return;
		}
		char ch = builder.charAt(builder.length() - 1);
		if (ch == '\n' || ch == '\t') {
			builder.append(keyPrefix);
		}
		if (hasNames(keyPrefix)) {
			builder.append(":");
		}
		if (o != null) {
			boolean multipleLines = readableObjectFormat || !isPlainObject(o);
			boolean generated = false;
			boolean separatorGenerated = false;
			boolean fieldGenerated = false;
			Field[] fields = o.getClass().getDeclaredFields();
			int filteringModifiers = Modifier.PUBLIC;
			ConfigFieldFilter filter = Config.configurationFilters.get(o.getClass().getName());
			if (filter != null && filter.modifiers >= 0) {
				filteringModifiers = filter.modifiers;
			}
			builder.append($objectOpen);
			for (int i = 0; i < fields.length; i++) {
				Field f = fields[i];
				if (f == null) {
					continue;
				}
				int modifiers = f.getModifiers();
				if ((filteringModifiers <= 0 ? false : (modifiers & filteringModifiers) == 0)
						|| (modifiers & Modifier.STATIC) != 0
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
				if (multipleLines) {
					if (!generated) {
						builder.append("\r\n");
						separatorGenerated = true;
						generated = true;
					}
					if (fieldGenerated && !separatorGenerated) {
						builder.append(",\r\n");
						separatorGenerated = true;
					}
					if (keyPrefix != null)  {
						name = getPrefixIndent(keyPrefix) + "\t" + (keywords.contains(name) ? "\"" + name + "\"" : name);
					}
					fieldGenerated = generateField(f, name, builder, o, unchanged);
					if (fieldGenerated) {
						generated = true;
						separatorGenerated = false;
					}
				} else {
					if (fieldGenerated && !separatorGenerated) {
						builder.append(","); // separatorGenerated was false by default
						separatorGenerated = true;
					}
					Class<?> type = f.getType();
					try {
						if (type == int.class) {
							int v = f.getInt(o);
							if (v == 0 && (skipUnchangedLines || skipObjectUnchangedFields)) continue;
							builder.append(name).append(":").append(v);
							fieldGenerated = true;
						} else if (type == String.class) {
							String v = (String)f.get(o);
							if (v == null && (skipUnchangedLines || skipObjectUnchangedFields)) continue;
							builder.append(name).append(":").append(generatePlainString(v));
							fieldGenerated = true;
						} else if (type == boolean.class) {
							boolean v = f.getBoolean(o);
							if (v == false && (skipUnchangedLines || skipObjectUnchangedFields)) continue;
							builder.append(name).append(":").append(v);
							fieldGenerated = true;
						} else if (type == long.class) {
							long v = f.getLong(o);
							if (v == 0 && (skipUnchangedLines || skipObjectUnchangedFields)) continue;
							builder.append(name).append(":").append(v);
							fieldGenerated = true;
						} else if (type == int[].class) {
							int[] vs = (int[]) f.get(o);
							if (vs == null && (skipUnchangedLines || skipObjectUnchangedFields)) continue;
							builder.append(name).append(":");
							if (vs == null) {
								builder.append($null);
							} else if (vs.length == 1) {
								builder.append(vs[0]);
							} else { // vs.length == 0
								builder.append($empty);
							}
							fieldGenerated = true;
						} else if (type == long[].class) {
							long[] vs = (long[]) f.get(o);
							if (vs == null && (skipUnchangedLines || skipObjectUnchangedFields)) continue;
							builder.append(name).append(":");
							if (vs == null) {
								builder.append($null);
							} else if (vs.length == 1) {
								builder.append(vs[0]);
							} else { // vs.length == 0
								builder.append($empty);
							}
							fieldGenerated = true;
						} else if (type == boolean[].class) {
							boolean[] vs = (boolean[]) f.get(o);
							if (vs == null && (skipUnchangedLines || skipObjectUnchangedFields)) continue;
							builder.append(name).append(":");
							if (vs == null) {
								builder.append($null);
							} else if (vs.length == 1) {
								builder.append(vs[0]);
							} else { // vs.length == 0
								builder.append($empty);
							}
							fieldGenerated = true;
						} else if (type == double[].class) {
							double[] vs = (double[]) f.get(o);
							if (vs == null && (skipUnchangedLines || skipObjectUnchangedFields)) continue;
							builder.append(name).append(":");
							if (vs == null) {
								builder.append($null);
							} else if (vs.length == 1) {
								builder.append(vs[0]);
							} else { // vs.length == 0
								builder.append($empty);
							}
							fieldGenerated = true;
						} else if (type == float[].class) {
							float[] vs = (float[]) f.get(o);
							if (vs == null && (skipUnchangedLines || skipObjectUnchangedFields)) continue;
							builder.append(name).append(":");
							if (vs == null) {
								builder.append($null);
							} else if (vs.length == 1) {
								builder.append(vs[0]);
							} else { // vs.length == 0
								builder.append($empty);
							}
							fieldGenerated = true;
						} else if (type == short[].class) {
							short[] vs = (short[]) f.get(o);
							if (vs == null && (skipUnchangedLines || skipObjectUnchangedFields)) continue;
							builder.append(name).append(":");
							if (vs == null) {
								builder.append($null);
							} else if (vs.length == 1) {
								builder.append(vs[0]);
							} else { // vs.length == 0
								builder.append($empty);
							}
							fieldGenerated = true;
						} else if (type == byte[].class) {
							byte[] vs = (byte[]) f.get(o);
							if (vs == null && (skipUnchangedLines || skipObjectUnchangedFields)) continue;
							builder.append(name).append(":");
							if (vs == null) {
								builder.append($null);
							} else if (vs.length == 1) {
								builder.append(vs[0]);
							} else { // vs.length == 0
								builder.append($empty);
							}
							fieldGenerated = true;
						} else if (type == char[].class) {
							char[] vs = (char[]) f.get(o);
							if (vs == null && (skipUnchangedLines || skipObjectUnchangedFields)) continue;
							builder.append(name).append(":");
							if (vs == null) {
								builder.append($null);
							} else if (vs.length == 1) {
								builder.append(vs[0]);
							} else { // vs.length == 0
								builder.append($empty);
							}
							fieldGenerated = true;
						} else if (type.isArray()) {
							Object[] vs = (Object[]) f.get(o);
							if (vs == null && (skipUnchangedLines || skipObjectUnchangedFields)) continue;
							builder.append(name).append(":");
							if (vs == null) {
								builder.append($null);
							} else if (vs.length == 1) {
								Object v = vs[0];
								if (v == null) {
									builder.append($null);
								} else if (v instanceof String) {
									builder.append(generatePlainString((String) v));
								} else if (isBasicType(v.getClass())) {
									builder.append(v == null ? $null : v);
								} else { // v is empty
									builder.append($empty);
								}
							} else { // vs.length == 0
								builder.append($empty);
							}
							fieldGenerated = true;
						} else if (type == double.class) {
							double v = f.getDouble(o);
							if (v == 0 && (skipUnchangedLines || skipObjectUnchangedFields)) continue;
							builder.append(name).append(":").append(v);
							fieldGenerated = true;
						} else if (type == float.class) {
							float v = f.getFloat(o);
							if (v == 0 && (skipUnchangedLines || skipObjectUnchangedFields)) continue;
							builder.append(name).append(":").append(v);
							fieldGenerated = true;
						} else if (type == short.class) {
							short v = f.getShort(o);
							if (v == 0 && (skipUnchangedLines || skipObjectUnchangedFields)) continue;
							builder.append(name).append(":").append(v);
							fieldGenerated = true;
						} else if (type == byte.class) {
							byte v = f.getByte(o);
							if (v == 0 && (skipUnchangedLines || skipObjectUnchangedFields)) continue;
							builder.append(name).append(":").append(v);
							fieldGenerated = true;
						} else if (type == char.class) {
							char v = f.getChar(o);
							if (v == 0 && (skipUnchangedLines || skipObjectUnchangedFields)) continue;
							builder.append(name).append(":").append(v);
							fieldGenerated = true;
						} else if (type == List.class) { // List<Object>
							@SuppressWarnings("unchecked")
							List<String> vs = (List<String>)f.get(o);
							if (vs == null && (skipUnchangedLines || skipObjectUnchangedFields)) continue;
							builder.append(name).append(":");
							if (vs == null) {
								builder.append($null);
							} else if (vs.size() == 1) {
								Object v = vs.get(0);
								if (v instanceof String) {
									builder.append(generatePlainString((String) v));
								} else if (isBasicType(v.getClass())) {
									builder.append(v == null ? $null : v);
								} else {
									builder.append($empty);
								}
							} else { // vs.size() == 0
								builder.append($empty);
							}
							fieldGenerated = true;
						} else if (type == Set.class) { // Set<Object>
							@SuppressWarnings("unchecked")
							Set<String> vs = (Set<String>)f.get(o);
							if (vs == null && (skipUnchangedLines || skipObjectUnchangedFields)) continue;
							builder.append(name).append(":");
							if (vs == null) {
								builder.append($null);
							} else if (vs.size() == 1) {
								Object v = vs.iterator().next();
								if (v instanceof String) {
									builder.append(generatePlainString((String) v));
								} else if (isBasicType(v.getClass())) {
									builder.append(v == null ? $null : v);
								} else {
									builder.append($empty);
								}
							} else { // vs.size() == 0
								builder.append($empty);
							}
							fieldGenerated = true;
						} else if (type == Map.class) { // Map<String, Object>
							@SuppressWarnings("unchecked")
							Map<String, Object> vs = (Map<String, Object>) f.get(o);
							if (vs == null && (skipUnchangedLines || skipObjectUnchangedFields)) continue;
							builder.append(name).append(":");
							if (vs == null) {
								builder.append($null);
							} else if (vs.size() == 1) {
								Object v = vs.values().iterator().next();
								if (v instanceof String) {
									builder.append(generatePlainString((String) v));
								} else if (isBasicType(v.getClass())) {
									builder.append(v == null ? $null : v);
								} else {
									builder.append($empty);
								}
							} else { // vs.size() == 0
								builder.append($empty);
							}
							fieldGenerated = true;
						} else if (type == Integer.class) {
							Integer v = (Integer) f.get(o);
							if ((v == null || v.intValue() == 0) && (skipUnchangedLines || skipObjectUnchangedFields)) continue;
							builder.append(name).append(":").append(v == null ? $null : v);
							fieldGenerated = true;
						} else if (type == Boolean.class) {
							Boolean v = (Boolean) f.get(o);
							if ((v == null || v.booleanValue() == false) && (skipUnchangedLines || skipObjectUnchangedFields)) continue;
							builder.append(name).append(":").append(v == null ? $null : v);
							fieldGenerated = true;
						} else if (type == Long.class) {
							Long v = (Long) f.get(o);
							if ((v == null || v.longValue() == 0) && (skipUnchangedLines || skipObjectUnchangedFields)) continue;
							builder.append(name).append(":").append(v == null ? $null : v);
							fieldGenerated = true;
						} else if (type == Double.class) {
							Double v = (Double) f.get(o);
							if ((v == null || v.doubleValue() == 0) && (skipUnchangedLines || skipObjectUnchangedFields)) continue;
							builder.append(name).append(":").append(v == null ? $null : v);
							fieldGenerated = true;
						} else if (type == Float.class) {
							Float v = (Float) f.get(o);
							if ((v == null || v.floatValue() == 0) && (skipUnchangedLines || skipObjectUnchangedFields)) continue;
							builder.append(name).append(":").append(v == null ? $null : v);
							fieldGenerated = true;
						} else if (type == Short.class) {
							Short v = (Short) f.get(o);
							if ((v == null || v.shortValue() == 0) && (skipUnchangedLines || skipObjectUnchangedFields)) continue;
							builder.append(name).append(":").append(v == null ? $null : v);
							fieldGenerated = true;
						} else if (type == Byte.class) {
							Byte v = (Byte) f.get(o);
							if ((v == null || v.byteValue() == 0) && (skipUnchangedLines || skipObjectUnchangedFields)) continue;
							builder.append(name).append(":").append(v == null ? $null : v);
							fieldGenerated = true;
						} else if (type == Character.class) {
							Character v = (Character) f.get(o);
							if ((v == null || v.charValue() == 0) && (skipUnchangedLines || skipObjectUnchangedFields)) continue;
							builder.append(name).append(":").append(v == null ? $null : v);
							fieldGenerated = true;
						} else {
							Object v = f.get(o);
							builder.append(name).append(":");
							if (v == null) {
								builder.append($null);
							} else { // no fields
								builder.append($empty);
							}
							fieldGenerated = true;
						}
					} catch (Throwable e) {
						//e.printStackTrace();
					}
					if (fieldGenerated) {
						generated = true;
						separatorGenerated = false;
					}
				} // end of if multiple/single line configuration
			} // end of for fields
//			if (!fieldGenerated && !separatorGenerated && !generated) { // length == 0
//				builder.append($empty);
//			}
			if (multipleLines) {
				if (builder.length() > 3 && ",\r\n".equals(builder.substring(builder.length() - 3, builder.length()))) {
					builder.delete(builder.length() - 3, builder.length());
				}
				if (builder.length() > 2 && !"\r\n".equals(builder.substring(builder.length() - 2, builder.length()))) {
					builder.append("\r\n");
				}
				builder.append(getPrefixIndent(keyPrefix));
			}
			builder.append($objectClose);
		} else {
			builder.append($null);
		}
	}

	static void generateMap(StringBuilder builder, String name, Map<String, Object> vs, Class<?>[] valueTypes, boolean unchanged) {
		builder.append(name).append(":");
		if (vs != null) {
			boolean isTypeString = valueTypes == null || valueTypes.length == 0
					|| (valueTypes.length == 1 && valueTypes[0] == String.class);
			Set<Entry<String, Object>> entries = vs.entrySet();
			if (entries.size() > 0) {
				builder.append($mapOpen);
				//if (readableMapFormat || !isBasicType(isTypeString ? String.class : valueTypes[0])) {
					boolean generated = false;
					for (Entry<String, Object> entry : entries) {
						if (generated) {
							builder.append(",");
						}
						builder.append("\r\n");
						if (unchanged) {
							builder.append("//");
						}
						String k = entry.getKey().trim();
						String prefix = getPrefixIndent(name) + "\t" + (keywords.contains(k) ? "\"" + k + "\"" : k);
						if (isTypeString) {
							String v = (String) entry.getValue();
							generateString(builder, prefix, v != null ? v.trim() : null);
						} else {
							generateTypeObject(builder, prefix, entry.getValue(), valueTypes, unchanged);
						}
						generated = true;
					}
					if (builder.length() > 3 && ",\r\n".equals(builder.substring(builder.length() - 3, builder.length()))) {
						builder.delete(builder.length() - 3, builder.length());
					}
					if (builder.length() > 2 && !"\r\n".equals(builder.substring(builder.length() - 2, builder.length()))) {
						builder.append("\r\n");
					}
					builder.append(getPrefixIndent(name));
				/*
				} else {
					boolean first = true;
					for (Entry<String, Object> entry : entries) {
						if (!first) {
							builder.append(",");
						}
						String k = entry.getKey().trim();
						Object v = entry.getValue();
						String keyStr = configFormat(k);
						keyStr = keywords.contains(keyStr) ? "\"" + keyStr + "\"" : keyStr;
						builder.append(keyStr)
								.append('>')
								.append(isTypeString ? generatePlainString((String) v) : (v == null ? $null : v));
						first = false;
					}
				}
				// */
				builder.append($mapClose);
			} else { // length == 0
				builder.append($empty);
			}
		} else {
			builder.append($null);
		}
	}

	static void generateSet(StringBuilder builder, String name, Set<Object> vs, Class<?>[] valueTypes, boolean unchanged) {
		builder.append(name).append(":");
		if (vs != null && vs.size() > 0) {
			boolean isTypeString = valueTypes == null || valueTypes.length == 0
					|| (valueTypes.length == 1 && valueTypes[0] == String.class);
			boolean first = true;
			builder.append($setOpen);
			/*
			if (readableSetFormat || !isBasicType(isTypeString ? String.class : valueTypes[0])) {
				int size = vs.size();
				int length = String.valueOf(size).length();
				int index = 1;
				for (Object o : vs) {
					if (index != 1) {
						builder.append(",");
					}
					builder.append("\r\n");
					if (unchanged) {
						builder.append("//");
					}
					StringBuilder sb = new StringBuilder(name);
					sb.append('.');
					int deltaLen = length - String.valueOf(index).length();
					for (int i = 0; i < deltaLen; i++) {
						sb.append('0');
					}
					sb.append(index);
					if (isTypeString) {
						String v = (String) o;
						generateString(builder, sb.toString(), v != null ? v.trim() : null);
					} else {
						generateTypeObject(builder, sb.toString(), o, valueTypes, unchanged);
					}
					index++;
				}
				builder.append("\r\n").append(getPrefixIndent(name));
			} else {
			// */
				for (Object o : vs) {
					if (!first) {
						builder.append(",");
					}
					first = false;
					builder.append(isTypeString ? generatePlainString((String) o) : (o == null ? $null : o));
				}
			// }
			builder.append($setClose);
		} else if (vs != null) { // vs.length == 0
			builder.append($empty);
		} else {
			builder.append($null);
		}
	}

	static void generateList(StringBuilder builder, String name, List<Object> vs, Class<?>[] valueTypes, boolean unchanged) {
		builder.append(name).append(":");
		if (vs != null && vs.size() > 0) {
			boolean isTypeString = valueTypes == null || valueTypes.length == 0
					|| (valueTypes.length == 1 && valueTypes[0] == String.class);
			boolean first = true;
			builder.append($listOpen);
			/*
			if (readableListFormat || !isBasicType(isTypeString ? String.class : valueTypes[0])) {
				int size = vs.size();
				int length = String.valueOf(size).length();
				int index = 1;
				for (Object o : vs) {
					if (index != 1) {
						builder.append(",");
					}
					builder.append("\r\n");
					if (unchanged) {
						builder.append("//");
					}
					StringBuilder sb = new StringBuilder(name).append('.');
					int deltaLen = length - String.valueOf(index).length();
					for (int i = 0; i < deltaLen; i++) {
						sb.append('0');
					}
					sb.append(index);
					if (isTypeString) {
						String v = (String) o;
						generateString(builder, sb.toString(), v != null ? v.trim() : null);
					} else {
						generateTypeObject(builder, sb.toString(), o, valueTypes, unchanged);
					}
					index++;
				}
				builder.append("\r\n").append(getPrefixIndent(name));
			} else {
			// */
				for (Object o : vs) {
					if (!first) {
						builder.append(",");
					}
					first = false;
					builder.append(isTypeString ? generatePlainString((String) o) : (o == null ? $null : o));
				}
			// }
			builder.append($listClose);
		} else if (vs != null) { // vs.length == 0
			builder.append($empty);
		} else {
			builder.append($null);
		}
	}

	static void generateCharArray(StringBuilder builder, String name, char[] vs, boolean unchanged) {
		builder.append(name).append(":");
		if (vs != null && vs.length > 0) {
			builder.append($arrayOpen);
			for (int k = 0; k < vs.length; k++) {
				if (k > 0) {
					builder.append(",");
				}
				builder.append('\'').append(vs[k]).append('\'');
			}
			builder.append($arrayClose);
		} else if (vs != null) { // vs.length == 0
			builder.append($empty);
		} else {
			builder.append($null);
		}
	}

	static void generateByteArray(StringBuilder builder, String name, byte[] vs, boolean unchanged) {
		builder.append(name).append(":");
		if (vs != null && vs.length > 0) {
			builder.append($arrayOpen);
			for (int k = 0; k < vs.length; k++) {
				if (k > 0) {
					builder.append(",");
				}
				builder.append(vs[k]);
			}
			builder.append($arrayClose);
		} else if (vs != null) { // vs.length == 0
			builder.append($empty);
		} else {
			builder.append($null);
		}
	}

	static void generateShortArray(StringBuilder builder, String name, short[] vs, boolean unchanged) {
		builder.append(name).append(":");
		if (vs != null && vs.length > 0) {
			builder.append($arrayOpen);
			for (int k = 0; k < vs.length; k++) {
				if (k > 0) {
					builder.append(",");
				}
				builder.append(vs[k]);
			}
			builder.append($arrayClose);
		} else if (vs != null) { // vs.length == 0
			builder.append($empty);
		} else {
			builder.append($null);
		}
	}

	static void generateFloatArray(StringBuilder builder, String name, float[] vs, boolean unchanged) {
		builder.append(name).append(":");
		if (vs != null && vs.length > 0) {
			builder.append($arrayOpen);
			for (int k = 0; k < vs.length; k++) {
				if (k > 0) {
					builder.append(",");
				}
				builder.append(vs[k]);
			}
			builder.append($arrayClose);
		} else if (vs != null) { // vs.length == 0
			builder.append($empty);
		} else {
			builder.append($null);
		}
	}

	static void generateDoubleArray(StringBuilder builder, String name, double[] vs, boolean unchanged) {
		builder.append(name).append(":");
		if (vs != null && vs.length > 0) {
			builder.append($arrayOpen);
			for (int k = 0; k < vs.length; k++) {
				if (k > 0) {
					builder.append(",");
				}
				builder.append(vs[k]);
			}
			builder.append($arrayClose);
		} else if (vs != null) { // vs.length == 0
			builder.append($empty);
		} else {
			builder.append($null);
		}
	}

	static void generateBooleanArray(StringBuilder builder, String name, boolean[] vs, boolean unchanged) {
		builder.append(name).append(":");
		if (vs != null && vs.length > 0) {
			builder.append($arrayOpen);
			for (int k = 0; k < vs.length; k++) {
				if (k > 0) {
					builder.append(",");
				}
				builder.append(vs[k]);
			}
			builder.append($arrayClose);
		} else if (vs != null) { // vs.length == 0
			builder.append($empty);
		} else {
			builder.append($null);
		}
	}

	static void generateLongArray(StringBuilder builder, String name, long[] vs, boolean unchanged) {
		builder.append(name).append(":");
		if (vs != null && vs.length > 0) {
			builder.append($arrayOpen);
			for (int k = 0; k < vs.length; k++) {
				if (k > 0) {
					builder.append(",");
				}
				builder.append(vs[k]);
			}
			builder.append($arrayClose);
		} else if (vs != null) { // vs.length == 0
			builder.append($empty);
		} else {
			builder.append($null);
		}
	}

	static void generateIntegerArray(StringBuilder builder, String name, int[] vs, boolean unchanged) {
		builder.append(name).append(":");
		if (vs != null && vs.length > 0) {
			builder.append($arrayOpen);
			for (int k = 0; k < vs.length; k++) {
				if (k > 0) {
					builder.append(",");
				}
				builder.append(vs[k]);
			}
			builder.append($arrayClose);
		} else if (vs != null) { // vs.length == 0
			builder.append($empty);
		} else {
			builder.append($null);
		}
	}

	static boolean hasNames(String name) {
		int nameLength = name.length();
		for (int i = 0; i < nameLength; i++) {
			char c = name.charAt(i);
			if (c != '\t') {
				return true;
			}
		}
		return false;
	}
	
	static void generateArray(StringBuilder builder, String name, Object[] vs, Class<?>[] valueTypes, boolean unchanged) {
		builder.append(name);
		if (hasNames(name)) {
			builder.append(":");
		}
		if (vs != null && vs.length > 0) {
			builder.append($arrayOpen);
			boolean isTypeString = valueTypes == null || valueTypes.length == 0
					|| (valueTypes.length == 1 && valueTypes[0] == String.class);
			if (readableArrayFormat || !isBasicType(isTypeString ? String.class : valueTypes[0])) {
				String prefixIndent = getPrefixIndent(name);
				if (vs.length > 1 && isTypeString) {
					prefixIndent += "\t";
				}
				for (int k = 0; k < vs.length; k++) {
					if (k != 0) {
						builder.append(",");
					}
					if (vs.length > 1 && isTypeString) {
						builder.append("\r\n");
					}
					if (unchanged) {
						builder.append("//");
					}
					if (isTypeString) {
						builder.append(prefixIndent).append(generatePlainString((String) vs[k]));
					} else {
						generateTypeObject(builder, prefixIndent, vs[k], valueTypes, unchanged);
					}
				}
				if (vs.length > 1 && isTypeString) {
					builder.append("\r\n").append(prefixIndent).delete(builder.length() - 1, builder.length());
				}
			} else {
				for (int k = 0; k < vs.length; k++) {
					if (k > 0) {
						builder.append(",");
					}
					builder.append(isTypeString ? generatePlainString((String) vs[k]) : (vs[k] == null ? $null : vs[k]));
				}
			}
			builder.append($arrayClose);
		} else if (vs != null) { // vs.length == 0
			builder.append($empty);
		} else {
			builder.append($null);
		}
	}

	static void generateString(StringBuilder builder, String name, String v) {
		builder.append(name).append(":").append(generatePlainString(v));
	}

	static String generatePlainString(String v) {
		if (v != null && v.length() > 0) {
			return "\"" + configFormat(v) + "\"";
		} else if (v != null) { // v.length() == 0
			return $empty;
		} else {
			return $null;
		}
	}

	static String configFormat(String str) {
		return str.replaceAll("\\\\", "\\\\").replaceAll("\r", "\\\\r").replaceAll("\n", "\\\\n").replaceAll("\t", "\\\\t").trim();
	}

	static String readFile(File file) {
		FileInputStream fis = null;
		byte[] buffer = new byte[8096];
		int read = -1;
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			fis = new FileInputStream(file);
			while ((read = fis.read(buffer)) != -1) {
				baos.write(buffer, 0, read);
			}
		} catch (IOException e1) {
			//e1.printStackTrace();
		} finally {
			if (fis != null) {
				try {
					fis.close();
				} catch (IOException e) {
					//e.printStackTrace();
				}
			}
		}
		return new String(baos.toByteArray(), Config.configFileEncoding);
	}

	/**
	 * Generate default configuration. Ignoring existed configuration files.
	 * 
	 * @param multipleConfigs
	 * @param file
	 * @param classes
	 */
	public static void generateDefaultConfiguration(boolean multipleConfigs, String file, Class<?>[] classes) {
		// Old file is null, generate default configuration file.
		generateUpdatedConfiguration(multipleConfigs, file, null, classes);
	}

	/**
	 * Generate updated configuration files: static fields default value + old file value.
	 * If old file is not specific, generate default configuration files with default static
	 * field values.
	 * 
	 * @param multipleConfigs
	 * @param file
	 * @param oldFile
	 * @param classes
	 */
	public static void generateUpdatedConfiguration(boolean multipleConfigs, String file, String oldFile, Class<?>[] classes) {
		generateUpdatedConfiguration(multipleConfigs, file, oldFile, classes, false); // Only overwriting main file. 
	}
	
	/**
	 * Generate updated configuration files: static fields default value + old file value.
	 * If old file is not specific, generate default configuration files with default static
	 * field values.
	 * If sub-configuration or common configurations are updated, try to overwrite it or create
	 * a new configuration with a warning.
	 * 
	 * @param multipleConfigs
	 * @param file
	 * @param oldFile
	 * @param classes
	 * @param overwritingSubConfigs
	 */
	public static void generateUpdatedConfiguration(boolean multipleConfigs, String file, String oldFile, Class<?>[] classes, boolean overwritingSubConfigs) {
		List<String> allNames = new ArrayList<String>();
		String[] oldConfigClasses = Config.configurationClasses;
		if (oldConfigClasses != null) {
			for (String clazz : oldConfigClasses) {
				allNames.add(clazz);
			}
		}
		List<Class<?>> allConfigs = new ArrayList<Class<?>>();
		for (int i = 0; i < classes.length; i++) {
			Class<?> clz = classes[i];
			if (clz != null) {
				allConfigs.add(clz);
				allNames.add(clz.getName());
			}
		}
		if (oldFile != null) {
			Config.configurationClasses = allNames.toArray(new String[allNames.size()]);
			Config.initialize(oldFile);
			Config.configurationClasses = oldConfigClasses;
		}

		String fileExt = Config.configurationFileExtension;
		String oldFileExt = fileExt;
		int idx = file.lastIndexOf('.');
		if (idx != -1) {
			String ext = file.substring(idx + 1);
			if (ext.length() > 0) {
				fileExt = file.substring(idx);
				Config.configurationFileExtension = fileExt;
			}
		}
		
		final Map<String, String> allFields = new HashMap<String, String>();
		ICheckConfiguration warnChecking = new ICheckConfiguration() {
			
			@Override
			public void check(String clazzName, String fieldName) {
				if (allFields.containsKey(fieldName)) {
					System.out.println("[WARN] " + clazzName + "." + fieldName + " is duplicated with " + (allFields.get(fieldName)));
				}
				allFields.put(fieldName, clazzName + "." + fieldName);
			}
		};
	
		StringBuilder defaultBuilder = new StringBuilder();
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
				builder.append("// ").append(clz.getSimpleName()).append("\r\n");
			} else {
				builder = new StringBuilder();
			}
			builder.append(generateConfigruation(clz, globalConfig, warnChecking));
			if (!globalConfig) { // multiple configurations
				String source = builder.toString();
				String folder = file;
				File folderFile = new File(folder);
				if (folderFile.isFile() || !folderFile.exists() || folder.endsWith(fileExt)) {
					folder = folderFile.getParent();
				}
				File oldConfigFile = new File(folder, keyPrefix + fileExt);
				String oldSource = readFile(oldConfigFile);
				if (!source.equals(oldSource)) {
					boolean newFile = oldSource == null || oldSource.length() == 0;
					System.out.println((newFile ? "Write " : "Update ") + keyPrefix + fileExt);
					folderFile = new File(folder);
					if (!folderFile.exists()) {
						folderFile.mkdirs();
					}
					FileOutputStream fos = null;
					try {
						if (newFile || overwritingSubConfigs) {
							fos = new FileOutputStream(oldConfigFile);
						} else {
							File newConfigFile = new File(folder, keyPrefix + ".new" + fileExt);
							fos = new FileOutputStream(newConfigFile);
							System.out.println(oldConfigFile.getAbsolutePath() + " is NOT updated with latest configuration.\r\n"
									+ "Please try to merge it with " + newConfigFile.getAbsolutePath() + " manually.");
						}
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
				builder.delete(0, builder.length());
			}
		} // end of for classes
		
		String source = defaultBuilder.toString();
		File cfgFile = new File(file);
		String oldSource = readFile(cfgFile);
		if (!source.equals(oldSource)) {
			System.out.println(((oldSource == null || oldSource.length() == 0) ? "Write " : "Update ") + file);
			FileOutputStream fos = null;
			File folderFile = cfgFile.getParentFile();
			if (!folderFile.exists()) {
				folderFile.mkdirs();
			}
			try {
				fos = new FileOutputStream(file);
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
		if (!Config.configurationFileExtension.equals(oldFileExt)) {
			Config.configurationFileExtension = oldFileExt;
		}
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		if (args == null || args.length < 2) {
			System.out.println("Usage: " + ConfigJSGenerator.class.getName()
					+ " [--multiple-configs] [--compact-object] [--compact-array] [--compact-list] [--compact-set] [--compact-map]"
					+ " <target config file> [old config file] <config class> [config class ...] [checking class]");
			return;
		}

		boolean multipleConfigs = false;
		int index = 0;
		ConfigJSGenerator.readableArrayFormat = true;
		ConfigJSGenerator.readableListFormat = true;
		ConfigJSGenerator.readableMapFormat = true;
		ConfigJSGenerator.readableObjectFormat = true;
		ConfigJSGenerator.readableSetFormat = true;
		do {
			String nextArg = args[index];
			if ("--multiple-configs".equals(nextArg)) {
				multipleConfigs = true;
				index++;
			} else if ("--compact-object".equals(nextArg)) {
				ConfigJSGenerator.readableObjectFormat = false;
				index++;
			} else if ("--compact-array".equals(nextArg)) {
				ConfigJSGenerator.readableArrayFormat = false;
				index++;
			} else if ("--compact-list".equals(nextArg)) {
				ConfigJSGenerator.readableListFormat = false;
				index++;
			} else if ("--compact-set".equals(nextArg)) {
				ConfigJSGenerator.readableSetFormat = false;
				index++;
			} else if ("--compact-map".equals(nextArg)) {
				ConfigJSGenerator.readableMapFormat = false;
				index++;
			} else {
				break;
			}
		} while (true);
		String targetFile = args[index];
		if (targetFile == null || targetFile.length() <= 0) {
			System.out.println("Usage: " + ConfigJSGenerator.class.getName() + " [--multiple-configs] <target config file> [old config file] <config class> [config class ...] [checking class]");
			System.out.println("Target config file path can not be empty.");
			return;
		}
		String oldFile = args[index + 1];
		if (new File(oldFile).exists()) {
			index++;
		} else {
			oldFile = null;
		}
		List<Class<?>> allClasses = new ArrayList<Class<?>>();
		for (int i = index + 1; i < args.length; i++) {
			String clazz = args[i];
			if (clazz != null && clazz.length() > 0) {
				try {
					Class<?> c = Class.forName(clazz);
					allClasses.add(c);
				} catch (ClassNotFoundException e) {
					e.printStackTrace();
				}
			}
		}
		
		Class<?>[] classes = allClasses.toArray(new Class<?>[allClasses.size()]);
		if (oldFile == null) {
			generateDefaultConfiguration(multipleConfigs, targetFile, classes);
		} else {
			generateUpdatedConfiguration(multipleConfigs, targetFile, oldFile, classes);
		}
	}

}
