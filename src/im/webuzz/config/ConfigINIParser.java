package im.webuzz.config;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
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

public class ConfigINIParser {

	protected static final String $null = "[null]";
	protected static final String $empty = "[empty]";
	protected static final String $array = "[array]";
	protected static final String $list = "[list]";
	protected static final String $set = "[set]";
	protected static final String $map = "[map]";
	protected static final String $object = "[object]";


	/**
	 * Parsing arguments into the given properties map:
	 * Recognize configuration items with pattern:
	 * "--c:xxx=###", "--config:xxx=###", "--c-xxx=###", "--config-xxx=###",
	 * store them into the given Properties map, and return the left arguments. 
	 * @param args, Command line arguments
	 * @param props
	 * @return Left arguments without configuration items.
	 */
	public static String[] parseArguments(String[] args, Properties props) {
		if (args == null || args.length == 0) return args;
		boolean parsed = false;
		List<String> argList = null;
		char[] configChars = new char[] {'c', 'o', 'n', 'f', 'i', 'g'};
		for (int i = 0; i < args.length; i++) {
			String arg = args[i];
			int idx = -1;
			if (arg == null || !arg.startsWith("--c")
					|| (idx = arg.indexOf('=')) == -1) {
				if (parsed) argList.add(arg);
				continue;
			}
			int startIdx = 3;
			char ch = 0;
			while (startIdx < idx) {
				ch = arg.charAt(startIdx);
				if (ch == '-' || ch == ':') break;
				if (startIdx - 2 >= configChars.length || ch != configChars[startIdx - 2]) break;
				startIdx++;
			}
			if (ch != '-' && ch != ':') {
				if (parsed) argList.add(arg);
				continue;
			}
			// --c-###=xxxx, --c:###=xxxx, --config-###=xxxx, --config:###=xxxx
			startIdx++;
			if (!parsed) {
				argList = new ArrayList<String>(args.length);
				for (int j = 0; j < i; j++) {
					argList.add(args[j]);
				}
				parsed = true;
			}
			String key = arg.substring(startIdx, idx);//.replace('-', '.');
			String value = arg.substring(idx + 1);
			if (!props.containsKey(key)) {
				props.put(key, value);
			}
			// logging-path, logging.path => loggingPath
			char[] chars = key.toCharArray();
			int len = chars.length;
			for (int k = len - 2; k > 0; k--) {
				char c = chars[k];
				if (c == '.' || c == '-') {
					char nc = chars[k + 1];
					if ('a' <= nc && nc <= 'z') {
						chars[k] = (char)(nc + 'A' - 'a');
						len--;
						for (int j = k + 1; j < len; j++) {
							chars[j] = chars[j + 1];
						}
						key = new String(chars, 0, len); 
						if (!props.containsKey(key)) {
							props.put(key, value);
						}
					}
				}
			}
		}
		return !parsed ? args : argList.toArray(new String[argList.size()]);
	}
	
	/**
	 * Parse the given properties map into the given class's static fields.
	 * @param prop
	 * @param clz
	 * @param combinedConfigs
	 * @param callUpdating
	 * @return Whether the given properties map contains matching configuration items or not
	 */
	// 
	public static boolean parseConfiguration(Properties prop, Class<?> clz, boolean combinedConfigs, boolean callUpdating) {
		if (clz == null) return false;
		long now = System.currentTimeMillis();
		String keyPrefix = null;
		if (combinedConfigs) { // all configuration items are in one file, use key prefix to distinguish fields
			keyPrefix = Config.getKeyPrefix(clz);
		}
		Field[] fields = clz.getDeclaredFields();
		Map<String, ConfigFieldFilter> configFilter = Config.configurationFilters;
		ConfigFieldFilter filter = configFilter != null ? configFilter.get(clz.getName()) : null;
		boolean itemMatched = false;
		for (int i = 0; i < fields.length; i++) {
			Field f = fields[i];
			if (f != null) {
				int modifiers = f.getModifiers();
				if (ConfigFieldFilter.filterModifiers(filter, modifiers, false)) continue;
				String name = f.getName();
				if (filter != null && filter.filterName(name)) continue;
				String keyName = keyPrefix != null ? keyPrefix + "." + name : name;
				String p = prop.getProperty(keyName);
				if (p == null) continue; // given key does not exist
				itemMatched = true;
				p = p.trim();
				// Should NOT skip empty string, as it may mean empty string or default value
				//if (p.length() == 0) continue;
				if ((modifiers & Modifier.PUBLIC) == 0) {
					f.setAccessible(true);
				}
				boolean updated = parseAndUpdateField(prop, keyName, p, clz, f, true, null);
				if (updated && Config.configurationLogging && Config.initializedTime > 0
						&& now - Config.initializedTime > 3000) { // start monitoring fields after 3s
					System.out.println("[Config] Configuration " + clz.getName() + "#" + name + " updated.");
				}
			}
		}
		if (itemMatched && (callUpdating || keyPrefix == null || keyPrefix.length() == 0)) {
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
		return itemMatched;
	}


	/**
	 * Parse the given key-prefixed properties for a field value, update the field if necessary.
	 * @param prop
	 * @param keyName
	 * @param p
	 * @param obj
	 * @param f
	 * @param updatingField
	 * @param diffB Keep the differences if the parsed value and the existed field value is different
	 * @return Whether the field is updated or not.
	 */
	@SuppressWarnings("rawtypes")
	private static boolean parseAndUpdateField(Properties prop, String keyName, String p,
			Object obj, Field f, boolean updatingField, StringBuilder diffB) {
		Class<?> type = f.getType();
		if (type == Object.class || Utils.isAbstractClass(type)) {
			type = recognizeObjectType(p);
		}
		try {
			if (type == String.class) {
				String newStr = parseString(p);
				String oldStr = (String) f.get(obj);
				if ((newStr == null && oldStr != null) || (newStr != null && !newStr.equals(oldStr))) {
					if (diffB != null) diffB.append(f.get(obj)).append('>').append(p);
					if (updatingField) f.set(obj, newStr);
					return true;
				}
			} else if (type == int.class) {
				int v = Integer.decode(p).intValue();
				if (v != f.getInt(obj)) {
					if (diffB != null) diffB.append(f.getInt(obj)).append('>').append(p);
					if (updatingField) f.setInt(obj, v);
					return true;
				}
			} else if (type == long.class) {
				long v = Long.decode(p).longValue();
				if (v != f.getLong(obj)) {
					if (diffB != null) diffB.append(f.getLong(obj)).append('>').append(p);
					if (updatingField) f.setLong(obj, v);
					return true;
				}
			} else if (type == boolean.class) {
				if (!p.equals(String.valueOf(f.getBoolean(obj)))) {
					if (diffB != null) diffB.append(f.getBoolean(obj)).append('>').append(p);
					if (updatingField) f.setBoolean(obj, Boolean.parseBoolean(p));
					return true;
				}
			} else if (type == double.class) {
				if (!p.equals(String.valueOf(f.getDouble(obj)))) {
					if (diffB != null) diffB.append(f.getDouble(obj)).append('>').append(p);
					if (updatingField) f.setDouble(obj, Double.parseDouble(p));
					return true;
				}
			} else if (type == float.class) {
				if (!p.equals(String.valueOf(f.getFloat(obj)))) {
					if (diffB != null)
						diffB.append(f.getFloat(obj)).append('>').append(p);
					if (updatingField) f.setFloat(obj, Float.parseFloat(p));
					return true;
				}
			} else if (type == short.class) {
				short v = Short.decode(p).shortValue();
				if (v != f.getShort(obj)) {
					if (diffB != null)
						diffB.append(f.getShort(obj)).append('>').append(p);
					if (updatingField) f.setShort(obj, v);
					return true;
				}
			} else if (type == byte.class) {
				byte v = Byte.decode(p).byteValue();
				if (v != f.getByte(obj)) {
					if (diffB != null) diffB.append(f.getByte(obj)).append('>').append(p);
					if (updatingField) f.setByte(obj, v);
					return true;
				}
			} else if (type == char.class) {
				char c = parseChar(p);
				if (c != f.getChar(obj)) {
					if (diffB != null) diffB.append(f.getChar(obj)).append('>').append(p);
					if (updatingField) f.setChar(obj, c);
					return true;
				}
			} else if (type.isArray()) {
				Object newArr = parseCollection(prop, keyName, p, type, f.getGenericType());
				if (!DeepComparator.arrayDeepEquals(type.getComponentType().isPrimitive(), newArr, f.get(obj))) {
					if (diffB != null) diffB.append("[...]").append('>')
							.append(newArr == null ? null : "[" + Array.getLength(newArr) + "]");
					if (updatingField) f.set(obj, newArr);
					return true;
				}
			} else if (List.class.isAssignableFrom(type)) {
				List newList = (List) parseCollection(prop, keyName, p, type, f.getGenericType());
				List oldList = (List) f.get(obj);
				if (!DeepComparator.listDeepEquals(newList, oldList)) {
					if (diffB != null) diffB.append("[...]").append('>')
							.append(newList == null ? null : "[" + newList.size() + "]");
					if (updatingField) f.set(obj, newList);
					return true;
				}
			} else if (Set.class.isAssignableFrom(type)) {
				Set newSet = (Set) parseCollection(prop, keyName, p, type, f.getGenericType());
				Set oldSet = (Set) f.get(obj);
				if (!DeepComparator.setDeepEquals(newSet, oldSet)) {
					if (diffB != null) diffB.append("[...]").append('>')
							.append(newSet == null ? null : "[" + newSet.size() + "]");
					if (updatingField) f.set(obj, newSet);
					return true;
				}
			} else if (Map.class.isAssignableFrom(type)) {
				Map newMap = parseMap(prop, keyName, p, type, f.getGenericType());
				Map oldMap = (Map) f.get(obj);
				if (!DeepComparator.mapDeepEquals(newMap, oldMap)) {
					if (diffB != null) diffB.append("[...]").append('>')
							.append(newMap == null ? null : "[" + newMap.size() + "]");
					if (updatingField) f.set(obj, newMap);
					return true;
				}
			} else if (type == Integer.class || type == Long.class
					|| type == Byte.class || type == Short.class) {
				Object v = f.get(obj);
				boolean changed = false;
				Object nv = null;
				if (!p.equals(ConfigINIParser.$null)) {
					if (v == null) {
						changed = true;
					} else {
						int length = p.length();
						if (length > 2 && p.charAt(0) == '[' && p.charAt(length - 1) == ']') {
							int idx = p.indexOf(':');
							if (idx != -1) {
								p = p.substring(idx + 1, length - 1);
							}
						}
						if (type == Integer.class) nv = Integer.decode(p);
						else if (type == Long.class) nv = Long.decode(p);
						else if (type == Short.class) nv = Short.decode(p);
						else nv = Byte.decode(p);
						changed = nv.equals(v);
					}
				} else {
					changed = v != null;
				}
				if (changed) {
					if (diffB != null) diffB.append(v).append('>').append(p);
					if (updatingField) f.set(obj, nv);
					return true;
				}
			} else if (type == Boolean.class || type == Float.class
					|| type == Double.class || type == Character.class
					|| type == BigDecimal.class || type == BigInteger.class) {
				Object v = f.get(obj);
				if (!p.equals(v == null ? ConfigINIParser.$null : String.valueOf(v))) {
					if (diffB != null) diffB.append(v).append('>').append(p);
					if (updatingField) {
						if (ConfigINIParser.$null.equals(p)) {
							f.set(obj, null);
						} else {
							int length = p.length();
							if (length > 2 && p.charAt(0) == '[' && p.charAt(length - 1) == ']') {
								int idx = p.indexOf(':');
								if (idx != -1) {
									p = p.substring(idx + 1, length - 1);
								}
							}
							if (type == Boolean.class) f.set(obj, Boolean.valueOf(p));
							else if (type == Float.class) f.set(obj, Float.valueOf(p));
							else if (type == Double.class) {
								f.set(obj, Double.valueOf(p));
							}
							else if (type == BigDecimal.class) f.set(obj, new BigDecimal(p));
							else if (type == BigInteger.class) f.set(obj, new BigInteger(p));
							else {
								f.set(obj, Character.valueOf(parseChar(p)));
							}
						}
					}
					return true;
				}
			} else if (type == Class.class) {
				Object v = f.get(obj);
				String clazzName = null;
				if (!p.equals(v == null ? ConfigINIParser.$null : (clazzName = ((Class<?>) v).getName()))) {
					if (diffB != null) diffB.append(v == null ? null : clazzName).append('>').append(p);
					if (updatingField) {
						if (ConfigINIParser.$null.equals(p)) {
							f.set(obj, null);
						} else {
							int length = p.length();
							if (length > 2 && p.charAt(0) == '[' && p.charAt(length - 1) == ']') {
								int idx = p.indexOf(':');
								if (idx != -1) {
									p = p.substring(idx + 1, length - 1);
								}
							}
							Class<?> clazz = Config.loadConfigurationClass(clazzName);
							if (clazz != null) f.set(obj, clazz);
						}
					}
					return true;
				}
			} else {
				Object newObj = parseObject(prop, keyName, p, type, f.getGenericType());
				Object oldObj = f.get(obj);
				if ((newObj == null && oldObj != null) || (newObj != null && !newObj.equals(oldObj))) {
					if (diffB != null) diffB.append(f.get(obj)).append('>').append(p);
					if (updatingField) f.set(obj, newObj);
					return true;
				}
			}
		} catch (Throwable e) {
			e.printStackTrace();
		}
		return false;
	}

	/**
	 * Parse the given string into a string, which will be used for the configuration field.
	 * Known string pattern:
	 * [null]: null string
	 * [empty]: ""
	 * [base64:###]: string encoded in Base64 format
	 * [secret:###]: string encoded by the encryption, see {@code im.webuzz.config.security.SecurityKit}
	 * @param p
	 * @return the raw string object
	 */
	private static String parseString(String p) {
		if (ConfigINIParser.$null.equals(p) || p == null) return null;
		if (ConfigINIParser.$empty.equals(p) || p.length() == 0) return "";
		if (p.indexOf("[secret:") == 0) { // "[secret:#######]";
			return Config.parseSecret(p.substring(8, p.length() - 1));
		}
		if (p.indexOf("[base64:") == 0) { // "[base64:#######]";
			byte[] bytes = Base64.base64ToByteArray(p.substring(8, p.length() - 1));
			return new String(bytes, Config.configFileEncoding);
		}
		return p;
	}

	/**
	 * Parse the given string into a char, which will be used for the configuration field.
	 * Known char pattern:
	 * 'a', 'z', '0', ...
	 * "", will be '\0'
	 * 0x20, will be " ",
	 * 48, 64, ...
	 * @param p
	 * @return the char
	 */
	private static char parseChar(String p) {
		int len = p.length();
		char c;
		if (len == 1) {
			c = p.charAt(0);
		} else if (len == 0) {
			c = 0;
		} else {
			try {
				int n = Integer.parseInt(p);
				c = (char) n;
			} catch (NumberFormatException e) {
				e.printStackTrace();
				c = 0;
			}
		}
		return c;
	}

	/**
	 * Parse the key-prefixed properties into a collection object:
	 * 1. Primitive array
	 * 2. Object array
	 * 3. List object
	 * 4. Set object
	 * The object may be encoded in multiple lines or a single line.
	 * @param prop
	 * @param keyName, The given prefix key
	 * @param p, Known value for the given key
	 * @param type, Field's type
	 * @param paramType, Field's generic type, if existed
	 * @return The parsed collection object. 
	 */
	private static Object parseCollection(Properties prop, String keyName, String p, Class<?> type, Type paramType) {
		if (ConfigINIParser.$null.equals(p) || p == null) return null;
		if (ConfigINIParser.$empty.equals(p) || p.length() == 0) {
			if (List.class.isAssignableFrom(type)) {
				return new ArrayList<Object>();
			} else if (Set.class.isAssignableFrom(type)) {
				return Collections.newSetFromMap(new ConcurrentHashMap<Object, Boolean>());
			} else { // Array
				return Array.newInstance(type.getComponentType(), 0);
			}
		}
		Class<?> valueType = type.getComponentType();
		Type valueParamType = null;
		if (paramType instanceof ParameterizedType) {
			valueParamType = ((ParameterizedType) paramType).getActualTypeArguments()[0];
			valueType = Utils.getRawType(valueParamType);
		} else if (paramType instanceof GenericArrayType) {
			GenericArrayType gaType = (GenericArrayType) paramType;
			valueParamType = gaType.getGenericComponentType();
		}
		
		boolean isArray = type.isArray();

		boolean singleLine = true;
		String[] keyNames = null;
		String[] ss = null;
		int arrayLength = -1;
		String prefix = null;

		int length = p.length();
		if (p.charAt(0) == '[' && p.charAt(length - 1) == ']' && p.indexOf(';') == -1) { // readable format
			if (length > 2) {
				int idx = p.indexOf(':');
				if (idx != -1) {
					p = p.substring(idx + 1, length - 1).trim();
					if (valueType == null || valueType == Object.class) {
						valueType = recognizeRawType(p);
					}
				}
			}

			List<String> filteredNames = new ArrayList<String>();
			Set<String> names = prop.stringPropertyNames();
			prefix = keyName + ".";
			for (String propName : names) {
				if (propName.startsWith(prefix)) {
					String k = propName.substring(prefix.length());
					String[] split = k.split("\\.");
					if (split.length > 1) continue;
					filteredNames.add(k);
				}
			}
			arrayLength = filteredNames.size();
			keyNames = filteredNames.toArray(new String[arrayLength]);
			if (isArray || List.class.isAssignableFrom(type)) Arrays.sort(keyNames); // keep array's order
			singleLine = false;
		} else {
			ss = p.split("\\s*;\\s*");
			arrayLength = ss.length;
		}
		if (valueType == null) valueType = Object.class;
		
		Object value = null;
		if (List.class.isAssignableFrom(type)) {
			value = new ArrayList<Object>(arrayLength);
		} else if (Set.class.isAssignableFrom(type)) {
			value = Collections.newSetFromMap(new ConcurrentHashMap<Object, Boolean>(arrayLength << 2));
		} else { // Array
			value = Array.newInstance(valueType, arrayLength);
		}
		boolean isPrimitiveArray = isArray && valueType.isPrimitive();
		for (int j = 0; j < arrayLength; j++) {
			String v = null;
			Object o = null;
			String newPropName = keyName;
			if (!singleLine) {
				newPropName = prefix + keyNames[j];
				v = (String) prop.getProperty(newPropName);
			} else {
				v = ss[j];
			}
			if (isPrimitiveArray) {
				if (v == null || v.length() == 0) {
					if (type == boolean[].class) {
						v = "false";
					} else if (type == char[].class) {
						v = "\0";
					} else {
						v = "0";
					}
				}
				try {
					if (type == int[].class) {
						Array.setInt(value, j, Integer.decode(v).intValue());
					} else if (type == long[].class) {
						Array.setLong(value, j, Long.decode(v).longValue());
					} else if (type == byte[].class) {
						Array.setByte(value, j, Byte.decode(v).byteValue());
					} else if (type == short[].class) {
						Array.setShort(value, j, Short.decode(v).shortValue());
					} else if (type == float[].class) {
						Array.setFloat(value, j, Float.parseFloat(v));
					} else if (type == double[].class) {
						Array.setDouble(value, j, Double.parseDouble(v));
					} else if (type == boolean[].class) {
						Array.setBoolean(value, j, Boolean.parseBoolean(v));
					} else if (type == char[].class) {
						Array.setChar(value, j, parseChar(v));
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			} else {
				o = recognizeAndParseObject(prop, newPropName, v, valueType, valueParamType);
				if (isArray) {
					Array.set(value, j, o);
				} else { // List or Set
					@SuppressWarnings("unchecked")
					Collection<Object> collection = (Collection<Object>) value;
					collection.add(o);
				}
			}
		}
		return value;
	}

	/**
	 * Parse the key-prefixed properties into a map object.
	 * There are two ways to encode the map:
	 * 1. Direct key-value pairs, in this case, the key should be a string
	 * ppp.ooo.kkk0=vvv0
	 * ppp.ooo.kkk1=vvv1
	 * 2. Entries with key-value object, in this case, the key may be complicate object
	 * ppp.ooo=[map]
	 * ppp.ooo.entries=[]
	 * ppp.ooo.entries.0=[]
	 * ppp.ooo.entries.0.key=kkk0
	 * ppp.ooo.entries.0.value=vvv0
	 * ppp.ooo.entries.1=[]
	 * ppp.ooo.entries.1.key=kkk1
	 * ppp.ooo.entries.2.value=vvv1
	 * The object may be encoded in multiple lines or a single line.
	 * @param prop
	 * @param keyName
	 * @param p
	 * @param type
	 * @param paramType
	 * @return
	 */
	private static Map<Object, Object> parseMap(Properties prop, String keyName, String p, Class<?> type, Type paramType) {
		if (ConfigINIParser.$null.equals(p) || p == null) return null;
		Map<Object, Object> value = new ConcurrentHashMap<Object, Object>();
		if (ConfigINIParser.$empty.equals(p) || p.length() == 0) return value;
		Class<?> keyType = null;
		Type keyParamType = null;
		Class<?> valueType = null;
		Type valueParamType = null;
		if (paramType instanceof ParameterizedType) {
			Type[] actualTypeArgs = ((ParameterizedType) paramType).getActualTypeArguments();
			keyParamType = actualTypeArgs[0];
			keyType = Utils.getRawType(keyParamType);
			valueParamType = actualTypeArgs[1]; // For map, second generic type
			valueType = Utils.getRawType(valueParamType);
		}
		int length = p.length();
		if (length >= 2 && p.charAt(0) == '[' && p.charAt(length - 1) == ']') { // readable format, multiple line configuration
			if (length > 2) {
				int idx = p.indexOf(':');
				if (idx != -1) {
					p = p.substring(idx + 1, length - 1);
					int commasIndex = p.indexOf(',');
					if (commasIndex != -1) {
						String keyTypeStr = p.substring(0, commasIndex).trim();
						if (keyType == null || keyType == Object.class) {
							keyType = recognizeRawType(keyTypeStr);
						}
						String valueTypeStr = p.substring(commasIndex + 1).trim();
						if (valueType == null || valueType == Object.class) {
							valueType = recognizeRawType(valueTypeStr);
						}
					}
				}
			}
			Set<String> names = prop.stringPropertyNames();
			int dots = 1;
			boolean hasKey = false;
			String prefix = keyName + ".";
			String entriesKey = prefix + "entries";
			String entries = (String) prop.getProperty(entriesKey);
			if (entries != null && entries.startsWith("[") && entries.endsWith("]")) {
				String entriesPrefix = entriesKey + ".";
				List<String> filteredNames = new ArrayList<String>();
				for (String propName : names) {
					if (propName.startsWith(entriesPrefix)) {
						String k = propName.substring(entriesPrefix.length());
						String[] split = k.split("\\.");
						if (split.length > 1) continue;
						filteredNames.add(k);
					}
				}
				String[] keyNames = filteredNames.toArray(new String[filteredNames.size()]);
				//Set<Object> value = Collections.newSetFromMap(new ConcurrentHashMap<Object, Boolean>(keyNames.length << 2));
				for (String propName : keyNames) {
					String newPropName = entriesPrefix + propName;
					String v = (String) prop.getProperty(newPropName);
					if (v == null) continue;
					if (!v.startsWith("[") || !v.endsWith("]")) {
						// =key>###;value>####
						Object key = null;
						Object val = null;
						String[] arr = p.split("\\s*;\\s*");
						for (int j = 0; j < arr.length; j++) {
							String item = arr[j].trim();
							if (item.length() == 0) continue;
							String[] kv = item.split("\\s*>+\\s*");
							if (kv.length != 2) {
								if (kv.length != 1 || item.indexOf('>') == -1) { // 1.0.1>;1.2.0>true
									continue;
								}
								// 1.0.1>
								kv = new String[] { kv[0], "" };
							}
							String keyStr = kv[0].trim();
							String objStr = kv[1].trim();
							if ("key".equals(keyStr)) {
								key = recognizeAndParseObject(prop, newPropName, objStr, keyType, keyParamType);
								if (val != null) break;
							} else if ("value".equals(keyStr)) {
								val = recognizeAndParseObject(prop, newPropName, objStr, valueType, valueParamType);
								if (key != null) break;
							}
						}
						if (key == null) continue;
						value.put(key, value);
					} else {
						String keyPrefix = newPropName + ".key";
						String kStr = (String) prop.getProperty(keyPrefix);
						if (kStr == null) continue;
						Object key = recognizeAndParseObject(prop, keyPrefix, kStr, keyType, keyParamType);
						if (key == null) continue;
						String valuePrefix = newPropName + ".value";
						String vStr = (String) prop.getProperty(valuePrefix);
						if (vStr == null) continue;
						Object val = recognizeAndParseObject(prop, valuePrefix, vStr, valueType, valueParamType);
						value.put(key, val);
					}
				}
				return value;
			}
			
			Set<String> parsedKeys = new HashSet<String>();
			Set<String> dotsNames = null;
			do {
				boolean dotsReached = false;
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
						if (alreadyParsed) continue;
						String k = propName.substring(prefix.length());
						Object key = recognizeAndParseObject(prop, prefix, k, keyType, keyParamType);
						if (key == null) continue;
						String[] split = k.split("\\.");
						if (split.length > dots) {
							if (!dotsReached) {
								dotsNames = new HashSet<String>();
								dotsReached = true;
							}
							dotsNames.add(propName);
							continue;
						}
						String v = (String) prop.getProperty(propName);
						value.put(key, recognizeAndParseObject(prop, propName, v, valueType, valueParamType));
						if (v == null || v.length() <= 0 || (v.startsWith("[") && v.endsWith("]"))) {
							parsedKeys.add(propName + ".");
						} // else given v is a line for object, no need to put it into parsed keys set
					}
				}
				if (!dotsReached) break;
				dots++;
				names = dotsNames;
				dotsNames = null;
			} while (hasKey && dots < Math.max(1, Config.configurationMapSearchingDots));
			return value;
		}
		// single line configuration, should be simple like Map<String, String>
		String[] arr = p.split("\\s*;\\s*");
		for (int j = 0; j < arr.length; j++) {
			String item = arr[j].trim();
			if (item.length() == 0) continue;
			String[] kv = item.split("\\s*>+\\s*");
			if (kv.length != 2) {
				if (kv.length != 1 || item.indexOf('>') == -1) { // 1.0.1>;1.2.0>true
					continue;
				}
				// 1.0.1>
				kv = new String[] { kv[0], "" };
			}
			String k = kv[0].trim();
			Object key = recognizeAndParseObject(prop, keyName, k, keyType, keyParamType);
			if (key == null) continue;
			String v = kv[1].trim();
			value.put(key, recognizeAndParseObject(prop, keyName, v, valueType, valueParamType));
		}
		return value;
	}

	/**
	 * Parse the key-prefixed properties into a object with fields.
	 * @param prop
	 * @param keyName
	 * @param p
	 * @param type
	 * @param paramType
	 * @return
	 */
	private static Object parseObject(Properties prop, String keyName, String p, Class<?> type, Type paramType) {
		if (p == null || ConfigINIParser.$null.equals(p)) return null;
		if (type == null) return new Object();
		Object obj = null;
		try {
			obj = type.newInstance();
		} catch (Exception e) {
			e.printStackTrace();
		}
		if (ConfigINIParser.$empty.equals(p) || p.length() == 0 || obj == null) return obj;

		String prefix = keyName + ".";
		Map<String, ConfigFieldFilter> configFilter = Config.configurationFilters;
		ConfigFieldFilter filter = configFilter != null ? configFilter.get(type.getName()) : null;
		if (ConfigINIParser.$object.equals(p) || (p.startsWith("[") && p.endsWith("]"))) { // Multiple line configuration
			Field[] fields = type.getFields();
			for (int i = 0; i < fields.length; i++) {
				Field f = fields[i];
				if (f == null) continue; // never happen
				int modifiers = f.getModifiers();
				if (ConfigFieldFilter.filterModifiers(filter, modifiers, true)) continue;
				String name = f.getName();
				if (filter != null && filter.filterName(name)) continue;
				String fieldKeyName = prefix + name;
				String pp = prop.getProperty(fieldKeyName);
				if (pp == null) continue;
				pp = pp.trim();
				if (pp.length() == 0) continue;
				if ((modifiers & Modifier.PUBLIC) == 0) {
					f.setAccessible(true);
				}
				parseAndUpdateField(prop, fieldKeyName, pp, obj, f, true, null);
			}
			return obj;
		}
		// Single line configuration
		String[] arr = p.split("\\s*;\\s*");
		for (int j = 0; j < arr.length; j++) {
			String item = arr[j].trim();
			if (item.length() == 0) continue;
			String[] kv = item.split("\\s*>+\\s*");
			if (kv.length != 2) continue;
			String k = kv[0].trim();
			if (filter != null && filter.filterName(k)) continue;
			Field f = null;
			try {
				f = type.getField(k);
			} catch (Exception e1) {
				//e1.printStackTrace();
			}
			if (f == null) continue;
			int modifiers = f.getModifiers();
			if (ConfigFieldFilter.filterModifiers(filter, modifiers, true)) continue;
			if ((modifiers & Modifier.PUBLIC) == 0) {
				f.setAccessible(true);
			}
			String pp = kv[1].trim();
			parseAndUpdateField(prop, prefix + k, pp, obj, f, true, null);
		}
		return obj;
	}

	/**
	 * Recognize the object type and then parse the properties into an object of the given type.
	 * @param prop
	 * @param keyName
	 * @param p
	 * @param type
	 * @param paramType
	 * @return
	 */
	private static Object recognizeAndParseObject(Properties prop, String keyName, String p, Class<?> type, Type paramType) {
		if (p == null || ConfigINIParser.$null.equals(p)) return null;
		if (type == null || type == Object.class || Utils.isAbstractClass(type)) { // type == null
			type = recognizeObjectType(p);
			if (type == null) return new Object();
		}
		int length = p.length();
		if (length > 2 && p.charAt(0) == '[' && p.charAt(length - 1) == ']') {
			int idx = p.indexOf(':');
			if (idx != -1) {
				String typePrefix = p.substring(0, idx);
				if (!($object.startsWith(typePrefix)
						|| $array.startsWith(typePrefix) || $list.startsWith(typePrefix)
						|| $set.startsWith(typePrefix) || $map.startsWith(typePrefix))) {
					p = p.substring(idx + 1, length - 1);
				} // else ignore [object:, [array:, [list:, [set:, and [map:
			}
		}

		if (type == String.class) return parseString(p);
		if (p.length() == 0 && Number.class.isAssignableFrom(type)) {
			p = "0";
		}
		if (type == Integer.class) return Integer.decode(p);
		if (type == Long.class) return Long.decode(p);
		if (type == Short.class) return Short.decode(p);
		if (type == Byte.class) return Byte.decode(p);
		if (type == Boolean.class) return Boolean.valueOf(p);
		if (type == Double.class) return Double.valueOf(p);
		if (type == Float.class) return Float.valueOf(p);
		if (type == Character.class) return parseChar(p);
		//if (type == AtomicInteger.class) return new AtomicInteger(Integer.decode(p));
		//if (type == AtomicLong.class) return new AtomicLong(Long.decode(p));
		//if (type == AtomicBoolean.class) return new AtomicBoolean(Boolean.decode(p));
		if (type == Class.class) {
			if (p.length() == 0) return null;
			return Config.loadConfigurationClass(p);
		}
		if (type == BigDecimal.class) return new BigDecimal(p);
		if (type == BigInteger.class) return new BigInteger(p);
		if (type.isArray()) {
			return parseCollection(prop, keyName, p, type, paramType);
		}
		if (List.class.isAssignableFrom(type)) { // List<Object>
			return parseCollection(prop, keyName, p, type, paramType);
		}
		if (Set.class.isAssignableFrom(type)) { // Set<Object>
			return parseCollection(prop, keyName, p, type, paramType);
		}
		if (Map.class.isAssignableFrom(type)) { // Map<String, Object>
			return parseMap(prop, keyName, p, type, paramType);
		}
		return parseObject(prop, keyName, p, type, paramType);
	}

	private static Class<?> recognizeObjectType(String p) {
		int length = p.length();
		if (length >= 2 && p.charAt(0) == '[' && p.charAt(length - 1) == ']' && !ConfigINIParser.$empty.equals(p)) {
			if (length == 2) return Object.class;
			int idx = p.indexOf(':');
			if (idx == -1) {
				if (ConfigINIParser.$array.equals(p)) return String[].class;
				if (ConfigINIParser.$list.equals(p)) return List.class;
				if (ConfigINIParser.$map.equals(p)) return Map.class;
				if (ConfigINIParser.$set.equals(p)) return Set.class;
				if (ConfigINIParser.$object.equals(p)) return String.class;
				String rawType = p.substring(1, length - 1);
				return Config.loadConfigurationClass(rawType);
			}
			String prefix = p.substring(0, idx);
			if (prefix.equals("[base64") || prefix.equals("[secret")) return String.class;
			
			String suffix = p.substring(idx + 1, length - 1);
			if (ConfigINIParser.$array.startsWith(prefix)) {
				if (suffix.startsWith("[array")) {
					Class<?> compType = recognizeObjectType(suffix);
					return Array.newInstance(compType, 0).getClass();
				}
				
				if ("int".equals(suffix)) return int[].class;
				if ("long".equals(suffix)) return long[].class;
				if ("boolean".equals(suffix)) return boolean[].class;
				if ("string".equals(suffix)) return String[].class;
				if ("double".equals(suffix)) return double[].class;
				if ("float".equals(suffix)) return float[].class;
				if ("short".equals(suffix)) return short[].class;
				if ("byte".equals(suffix)) return byte[].class;
				if ("char".equals(suffix)) return char[].class;
				Class<?> compType = recognizeRawType(suffix);
				if (compType != null) {
					return Array.newInstance(compType, 0).getClass();
				}
				return Object[].class;
			}
			if (ConfigINIParser.$list.startsWith(prefix)) return List.class;
			if (ConfigINIParser.$map.startsWith(prefix)) return Map.class;
			if (ConfigINIParser.$set.startsWith(prefix)) return Set.class;
			if (ConfigINIParser.$object.startsWith(prefix)) {
				Class<?> type = Config.loadConfigurationClass(suffix);
				if (type == null) type = Object.class;
				return type;
			}
			String rawType = prefix.substring(1); // e.g. "[im.webuzz.config.####
			Class<?> type = recognizeRawType(rawType);
			if (type == null) type = Object.class;
			return type;
		}
		return String.class;
	}
	
	private static Class<?> recognizeRawType(String rawType) {
		if ("Integer".equals(rawType))  return Integer.class;
		if ("Long".equals(rawType)) return Long.class;
		if ("Boolean".equals(rawType)) return Boolean.class;
		if ("String".equals(rawType)) return String.class;
		if ("Double".equals(rawType)) return Double.class;
		if ("Float".equals(rawType)) return Float.class;
		if ("Short".equals(rawType)) return Short.class;
		if ("Byte".equals(rawType)) return Byte.class;
		if ("Character".equals(rawType)) return Character.class;
		if ("BigDecimal".equals(rawType)) return BigDecimal.class;
		if ("BigInteger".equals(rawType)) return BigInteger.class;
		if ("Class".equals(rawType) || "class".equals(rawType)) return Class.class;
		if ("List".equals(rawType) || "list".equals(rawType)) return List.class;
		if ("Map".equals(rawType) || "map".equals(rawType)) return Map.class;
		if ("Set".equals(rawType) || "set".equals(rawType)) return Set.class;
		return Config.loadConfigurationClass(rawType);
	}

}
