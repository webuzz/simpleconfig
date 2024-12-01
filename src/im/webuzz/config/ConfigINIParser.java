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

import im.webuzz.config.annotations.ConfigIgnore;

public class ConfigINIParser {

	protected static final String $null = "[null]";
	protected static final String $empty = "[empty]";
	protected static final String $array = "[array]";
	protected static final String $list = "[list]";
	protected static final String $set = "[set]";
	protected static final String $map = "[map]";
	protected static final String $object = "[object]";

	private final static Object error = new Object();

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
	public static int parseConfiguration(Properties prop, Class<?> clz, boolean combinedConfigs, boolean updating, boolean callUpdating) {
		if (clz == null) return 0;
		long now = System.currentTimeMillis();
		String keyPrefix = null;
		if (combinedConfigs) { // all configuration items are in one file, use key prefix to distinguish fields
			keyPrefix = Config.getKeyPrefix(clz);
		}
		Field[] fields = clz.getDeclaredFields();
		Map<Class<?>, ConfigFieldFilter> configFilter = Config.configurationFilters;
		ConfigFieldFilter filter = configFilter != null ? configFilter.get(clz) : null;
		boolean itemMatched = false;
		for (int i = 0; i < fields.length; i++) {
			Field f = fields[i];
			if (f == null) continue;
			if (f.getAnnotation(ConfigIgnore.class) != null) continue;
			int modifiers = f.getModifiers();
			if (ConfigFieldFilter.filterModifiers(filter, modifiers, false)) continue;
			String name = f.getName();
			//*
			if ("genders".equals(name)) {
				System.out.println("X city");
			} // */
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
			int result = parseAndUpdateField(prop, keyName, p, clz, f, updating, null);
			if (result == -1) return -1;
			if (result == 1 && updating && Config.configurationLogging && Config.initializedTime > 0
					&& now - Config.initializedTime > 3000) { // start monitoring fields after 3s
				System.out.println("[Config] Configuration " + clz.getName() + "#" + name + " updated.");
			}
		}
		if (itemMatched && updating && (callUpdating || keyPrefix == null || keyPrefix.length() == 0)) {
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
		return itemMatched ? 1 : 0;
	}

	private static Object parseEnumType(String p, String keyName) {
		String suffix = null;
		int length = p.length();
		if (length > 2 && p.charAt(0) == '[' && p.charAt(length - 1) == ']') {
			int idx = p.indexOf(':');
			if (idx != -1) {
				suffix = p.substring(idx + 1, length - 1);
			} else {
				suffix = p.substring(1, length - 1);
			}
		} else {
			suffix = p;
		}
		int nameIdx = suffix.lastIndexOf('.');
		if (nameIdx == -1) {
			StringBuilder errMsg = new StringBuilder();
			errMsg.append("Invalid value for field \"").append(keyName)
					.append("\": \"").append(p).append("\" is not an enum!");
			Config.reportErrorToContinue(errMsg.toString());
			return error;
		}
		String typeStr = suffix.substring(0, nameIdx).trim();
		StringBuilder err = new StringBuilder();
		Class<?> pType = Config.loadConfigurationClass(typeStr, err);
		if (pType == null) {
			StringBuilder errMsg = new StringBuilder();
			errMsg.append("Invalid value for field \"").append(keyName)
					.append("\": ").append(err);
			if (!Config.reportErrorToContinue(errMsg.toString())) return error;
		}
		return pType;
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
	 * @return -1: Errors are detected, 0: No fields are updated, 1: Field is updated.
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static int parseAndUpdateField(Properties prop, String keyName, String p,
			Object obj, Field f, boolean updatingField, StringBuilder diffB) {
		Class<?> type = f.getType();
		//*
		if ("genders".equals(keyName)) {
			System.out.println("X parse");
		} // */
		if (Utils.isObjectOrObjectArray(type) || Utils.isAbstractClass(type)) {
			Class<?> pType = recognizeObjectType(p);
			if (type == Enum.class && pType == String.class) {
				Object ret = parseEnumType(p, keyName);
				if (ret == error) return -1;
				pType = (Class<?>) ret;
			}
			if (pType != null && pType != Object.class) type = pType;
		}
		//StringBuilder errBuilder = new StringBuilder();
		try {
			Object decoded = decode(p);
			if (type == String.class) {
				String nv = decoded != null ? (String) decoded : parseString(p);
				String ov = (String) f.get(obj);
				if ((nv == null && ov != null) || (nv != null && !nv.equals(ov))) {
					if (diffB != null) diffB.append(ov).append('>').append(p);
					int result = ConfigValidator.validateObject(f, nv, 0, keyName);
					if (result == -1) return -1;
					if (result == 1 && updatingField) f.set(obj, nv);
					return result;
				}
			} else if (type == int.class) {
				int nv = Integer.decode(p).intValue();
				int ov = f.getInt(obj);
				if (nv != ov) {
					if (diffB != null) diffB.append(ov).append('>').append(p);
					int result = ConfigValidator.validatePrimitive(f, nv, 0, keyName);
					if (result == -1) return -1;
					if (result == 1 && updatingField) f.setInt(obj, nv);
					return result;
				}
			} else if (type == long.class) {
				long nv = Long.decode(p).longValue();
				long ov = f.getLong(obj);
				if (nv != ov) {
					if (diffB != null) diffB.append(ov).append('>').append(p);
					int result = ConfigValidator.validatePrimitive(f, nv, 0, keyName);
					if (result == -1) return -1;
					if (result == 1 && updatingField) f.setLong(obj, nv);
					return result;
				}
			} else if (type == boolean.class) {
				if (!p.equals(String.valueOf(f.getBoolean(obj)))) {
					if (diffB != null) diffB.append(f.getBoolean(obj)).append('>').append(p);
					if (updatingField) f.setBoolean(obj, Boolean.parseBoolean(p));
					return 1;
				}
			} else if (type == double.class) {
				double nv = Double.parseDouble(p);
				double ov = f.getDouble(obj);
				if (nv != ov) {
					if (diffB != null) diffB.append(ov).append('>').append(p);
					int result = ConfigValidator.validatePrimitive(f, nv, 0, keyName);
					if (result == -1) return -1;
					if (result == 1 && updatingField) f.setDouble(obj, nv);
					return result;
				}
			} else if (type == float.class) {
				float nv = Float.parseFloat(p);
				float ov = f.getFloat(obj);
				if (nv != ov) {
					if (diffB != null) diffB.append(ov).append('>').append(p);
					int result = ConfigValidator.validatePrimitive(f, nv, 0, keyName);
					if (result == -1) return -1;
					if (result == 1 && updatingField) f.setFloat(obj, nv);
					return result;
				}
			} else if (type == short.class) {
				short nv = Short.decode(p).shortValue();
				short ov = f.getShort(obj);
				if (nv != ov) {
					if (diffB != null) diffB.append(ov).append('>').append(p);
					int result = ConfigValidator.validatePrimitive(f, nv, 0, keyName);
					if (result == -1) return -1;
					if (result == 1 && updatingField) f.setShort(obj, nv);
					return result;
				}
			} else if (type == byte.class) {
				byte nv = Byte.decode(p).byteValue();
				byte ov = f.getByte(obj);
				if (nv != ov) {
					if (diffB != null) diffB.append(ov).append('>').append(p);
					int result = ConfigValidator.validatePrimitive(f, nv, 0, keyName);
					if (result == -1) return -1;
					if (result == 1 && updatingField) f.setByte(obj, nv);
					return result;
				}
			} else if (type == char.class) {
				char nv = parseChar(p);
				char ov = f.getChar(obj);
				if (nv != ov) {
					if (diffB != null) diffB.append(ov).append('>').append(p);
					int result = ConfigValidator.validatePrimitive(f, nv, 0, keyName);
					if (result == -1) return -1;
					if (result == 1 && updatingField) f.setChar(obj, nv);
					return result;
				}
			} else if (type != null && type.isArray()) {
				Object nv = decoded != null ? decoded : parseCollection(prop, keyName, p, type, f.getGenericType());
				if (nv == error) return -1;
				if (!DeepComparator.arrayDeepEquals(type.getComponentType().isPrimitive(), nv, f.get(obj))) {
					if (diffB != null) diffB.append("[...]").append('>')
							.append(nv == null ? null : "[" + Array.getLength(nv) + "]");
					int result = ConfigValidator.validateObject(f, nv, 0, keyName);
					if (result == -1) return -1;
					if (result == 1 && updatingField) f.set(obj, nv);
					return result;
				}
			} else if (List.class.isAssignableFrom(type)) {
				Object ret = decoded != null ? decoded : parseCollection(prop, keyName, p, type, f.getGenericType());
				if (ret == error) return -1;
				List nv = (List) ret;
				List ov = (List) f.get(obj);
				if (!DeepComparator.listDeepEquals(nv, ov)) {
					if (diffB != null) diffB.append("[...]").append('>')
							.append(nv == null ? null : "[" + nv.size() + "]");
					int result = ConfigValidator.validateObject(f, nv, 0, keyName);
					if (result == -1) return -1;
					if (result == 1 && updatingField) f.set(obj, nv);
					return result;
				}
			} else if (Set.class.isAssignableFrom(type)) {
				Object ret =decoded != null ? decoded :  parseCollection(prop, keyName, p, type, f.getGenericType());
				if (ret == error) return -1;
				Set nv = (Set) ret;
				Set ov = (Set) f.get(obj);
				if (!DeepComparator.setDeepEquals(nv, ov)) {
					if (diffB != null) diffB.append("[...]").append('>')
							.append(nv == null ? null : "[" + nv.size() + "]");
					int result = ConfigValidator.validateObject(f, nv, 0, keyName);
					if (result == -1) return -1;
					if (result == 1 && updatingField) f.set(obj, nv);
					return result;
				}
			} else if (Map.class.isAssignableFrom(type)) {
				Object ret = decoded != null ? decoded : parseMap(prop, keyName, p, type, f.getGenericType());
				if (ret == error) return -1;
				Map nv = (Map) ret;
				Map ov = (Map) f.get(obj);
				if (!DeepComparator.mapDeepEquals(nv, ov)) {
					if (diffB != null) diffB.append("[...]").append('>')
							.append(nv == null ? null : "[" + nv.size() + "]");
					int result = ConfigValidator.validateObject(f, nv, 0, keyName);
					if (result == -1) return -1;
					if (result == 1 && updatingField) f.set(obj, nv);
					return result;
				}
			} else if (type == Integer.class || type == Long.class
					|| type == Byte.class || type == Short.class
					|| type == Float.class || type == Double.class
					|| type == BigDecimal.class || type == BigInteger.class
					|| type == Boolean.class || type == Character.class) {
				Object ov = f.get(obj);
				boolean changed = false;
				Object nv = null;
				if (decoded != null) {
					nv = decoded;
					changed = ov == null ? true : !nv.equals(ov);
				} else if (!p.equals($null)) {
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
					else if (type == Byte.class) nv = Byte.decode(p);
					else if (type == Float.class) nv = Float.valueOf(p);
					else if (type == Double.class) nv = Double.valueOf(p);
					else if (type == BigDecimal.class) nv = new BigDecimal(p);
					else if (type == BigInteger.class) nv = new BigInteger(p);
					else if (type == Boolean.class) nv = Boolean.valueOf(p);
					else nv = Character.valueOf(parseChar(p)); // Character.class
					changed = ov == null ? true : !nv.equals(ov);
				} else {
					changed = ov != null;
				}
				if (changed) {
					if (diffB != null) diffB.append(ov).append('>').append(p);
					int result = ConfigValidator.validateObject(f, nv, 0, keyName);
					if (result == -1) return -1;
					if (result == 1 && updatingField) f.set(obj, nv);
					return result;
				}
			} else if (type == Class.class) {
				Class<?> ov = (Class<?>) f.get(obj);
				Class<?> nv = null;
				String nvStr = null;
				if (decoded != null) {
					nv = (Class<?>) decoded;
					nvStr = nv.getName();
				} else if (p != null && !$null.equals(p)) {
					int length = p.length();
					if (length > 2 && p.charAt(0) == '[' && p.charAt(length - 1) == ']') {
						int idx = p.indexOf(':');
						if (idx != -1) {
							nvStr = p.substring(idx + 1, length - 1);
						}
					} else {
						nvStr = p;
					}
				}
				String ovStr = null;
				if (ov != null) ovStr = ov.getName();
				if ((nvStr == null && ov != null) || (nvStr != null && !nvStr.equals(ovStr))) {
					if (nvStr != null && nvStr.length() > 0 && nv == null) {
						StringBuilder err = new StringBuilder();
						nv = Config.loadConfigurationClass(nvStr, err);
						if (nv == null) {
							StringBuilder errMsg = new StringBuilder();
							errMsg.append("Invalid value for field \"").append(keyName)
									.append("\": ").append(err);
							if (!Config.reportErrorToContinue(errMsg.toString())) return -1;
							return 0;
						}
					}
					int result = ConfigValidator.validateObject(f, nv, 0, keyName);
					if (result == -1) return -1;
					if (result == 1 && updatingField) f.set(obj, nv);
					return result;
				}
			} else if (type.isEnum() || type == Enum.class) {
				Enum<?> ov = (Enum<?>) f.get(obj);
				Enum<?> nv = null;
				String nvStr = null;
				if (decoded != null) {
					nv = (Enum<?>) decoded;
					nvStr = nv.name();
				} else if (p != null && !$null.equals(p)) {
					String suffix = null;
					int length = p.length();
					if (length > 2 && p.charAt(0) == '[' && p.charAt(length - 1) == ']') {
						int idx = p.indexOf(':');
						if (idx != -1) {
							suffix = p.substring(idx + 1, length - 1);
						} else {
							suffix = p.substring(1, length - 1);
						}
					} else {
						suffix = p;
					}
					int nameIdx = suffix.lastIndexOf('.');
					if (nameIdx != -1) {
						nvStr = suffix.substring(nameIdx + 1).trim();
					} else {
						nvStr = suffix;
					}
				}
				String ovStr = null;
				if (ov != null) ovStr = ov.name();
				if ((nvStr == null && ov != null) || (nvStr != null && !nvStr.equals(ovStr))) {
					if (nvStr != null && nvStr.length() > 0 && nv == null) {
						nv = Enum.valueOf((Class<? extends Enum>) type, nvStr);
					}
					int result = ConfigValidator.validateObject(f, nv, 0, keyName);
					if (result == -1) return -1;
					if (result == 1 && updatingField) f.set(obj, nv);
					return result;
				}
			} else {
				Object nv = decoded != null ? decoded : parseObject(prop, keyName, p, type, f.getGenericType());
				if (nv == error) return -1;
				Object ov = f.get(obj);
				if ((nv == null && ov != null) || (nv != null && !nv.equals(ov))) {
					if (diffB != null) diffB.append(f.get(obj)).append('>').append(p);
					int result = ConfigValidator.validateObject(f, nv, 0, keyName);
					if (result == -1) return -1;
					if (result == 1 && updatingField) f.set(obj, nv);
					return result;
				}
			}
		} catch (Throwable e) {
			e.printStackTrace();
			StringBuilder errMsg = new StringBuilder();
			errMsg.append("Invalid value for field \"").append(keyName)
					.append("\": ").append(e.getMessage());
			if (!Config.reportErrorToContinue(errMsg.toString())) return -1;
			return 0;
		}
		return 0;
	}

	protected static Object decode(String p) {
		if (p == null || $null.equals(p)) return null;
		int length = p.length();
		if (length <= 1 || p.charAt(0) != '[' || p.charAt(length - 1) != ']'
				|| p.indexOf(']') != length - 1) { // e.g. =[...];[...];[...]
			return null;
		}
		int idx = p.indexOf(':');
		if (idx == -1) return null;
		String key = p.substring(1, idx);
		String rawEncoded = p.substring(idx + 1, length - 1);
		return decodeRaw(key, rawEncoded);
	}

	protected static Object decodeRaw(String codecKey, String rawEncoded) {
		IConfigCodec<?> codec = Config.codecs.get(codecKey);
		if (codec != null) {
			try {
				return codec.decode(rawEncoded);
			} catch (Throwable e) {
				e.printStackTrace();
				return null;
			}
		}
		Map<String, Class<? extends IConfigCodec<?>>> codecs = Config.configurationCodecs;
		if (codecs == null) return null;
		Class<? extends IConfigCodec<?>> clazz = codecs.get(codecKey);
		if (clazz == null) return null;
		try {
			codec = (IConfigCodec<?>) clazz.newInstance();
			Config.codecs.put(codecKey, codec);
			return codec.decode(rawEncoded);
		} catch (Throwable e) {
			e.printStackTrace();
		}
		return null;
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
		if ($null.equals(p) || p == null) return null;
		if ($empty.equals(p) || p.length() == 0) return "";
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
		/*
		if ("strBytes".equals(keyName)) {
			System.out.println("Tow");
		} // */
		if ($null.equals(p) || p == null) return null;
		if ($empty.equals(p) || p.length() == 0) {
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

		boolean singleLine = false;
		String[] keyNames = null;
		String[] ss = null;
		int arrayLength = -1;
		String prefix = null;

		int length = p.length();
		if (length > 1 && p.charAt(0) == '[' && p.charAt(length - 1) == ']' && p.indexOf(']') == length - 1) { // readable format
			if (length > 2) {
				int idx = p.indexOf(':');
				if (idx != -1) {
					Object decoded = decode(p);
					if (decoded != null) return decoded;
					String typeStr = p.substring(1, idx).trim();
					if ("array".equals(typeStr) || "list".equals(typeStr) || "set".equals(typeStr)) {
						p = p.substring(idx + 1, length - 1).trim();
						if (valueType == null || valueType == Object.class) {
							valueType = recognizeRawType(p);
						}
					} else {
						// Single object item in the array
						singleLine = true;
					}
				}
			}
			if (!singleLine) {
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
			} else {
				// singleLine = true;
				ss = new String[1];
				ss[0] = p;
				arrayLength = 1;
			}
		} else {
			singleLine = true;
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
				if (o == error) return error;
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
	 * ppp.ooo.0=[]
	 * ppp.ooo.0.key=kkk0
	 * ppp.ooo.0.value=vvv0
	 * ppp.ooo.1=[]
	 * ppp.ooo.1.key=kkk1
	 * ppp.ooo.2.value=vvv1
	 * The object may be encoded in multiple lines or a single line.
	 * @param prop
	 * @param keyName
	 * @param p
	 * @param type
	 * @param paramType
	 * @return
	 */
	private static Object parseMap(Properties prop, String keyName, String p, Class<?> type, Type paramType) {
		if ($null.equals(p) || p == null) return null;
		Map<Object, Object> value = new ConcurrentHashMap<Object, Object>();
		if ($empty.equals(p) || p.length() == 0) return value;
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
		/*
		if ("mms".equals(keyName)) {
			System.out.println("city map");
		} // */
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
			String prefix = keyName + ".";
			int prefixLength = prefix.length();
			boolean entriesMode = true;
			Set<String> filteredKeyNames = new HashSet<String>();
			for (String propName : names) {
				if (propName.startsWith(prefix)) {
					String k = propName.substring(prefixLength);
					filteredKeyNames.add(k);
					if (!entriesMode) continue;
					int kLength = k.length();
					for (int i = 0; i < kLength; i++) {
						char c = k.charAt(i);
						if (c == '.') break;
						if (c < '0' || '9' < c) {
							entriesMode = false;
							break;
						}
					}
				}
			}
			if (filteredKeyNames.isEmpty()) return value;
			if (entriesMode) {
				for (String propName : filteredKeyNames) {
					String newPropName = prefix + propName;
					String v = (String) prop.getProperty(newPropName);
					if (v == null) continue;
					if (!v.startsWith("[") || !v.endsWith("]")) {
						// =key>###;value>####
						Object key = null;
						Object val = null;
						String[] arr = v.split("\\s*;\\s*");
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
								if (key == error) return error;
								if (val != null) break;
							} else if ("value".equals(keyStr)) {
								val = recognizeAndParseObject(prop, newPropName, objStr, valueType, valueParamType);
								if (val == error) return error;
								if (key != null) break;
							}
						}
						if (key == null) continue;
						value.put(key, val);
					} else {
						String keyPrefix = newPropName + ".key";
						String kStr = (String) prop.getProperty(keyPrefix);
						if (kStr == null) continue;
						Object key = recognizeAndParseObject(prop, keyPrefix, kStr, keyType, keyParamType);
						if (key == error) return error;
						if (key == null) continue;
						String valuePrefix = newPropName + ".value";
						String vStr = (String) prop.getProperty(valuePrefix);
						if (vStr == null) continue;
						Object val = recognizeAndParseObject(prop, valuePrefix, vStr, valueType, valueParamType);
						if (val == error) return error;
						value.put(key, val);
					}
				}
				return value;
			}
			
			Set<String> parsedKeys = new HashSet<String>();
			Set<String> dotsNames = null;
			names = filteredKeyNames;
			int dots = 0;
			do {
				boolean dotsReached = false;
				for (String k : names) {
					boolean alreadyParsed = false;
					for (String key : parsedKeys) {
						if (k.startsWith(key)) {
							alreadyParsed = true;
							break;
						}
					}
					if (alreadyParsed) continue;
					int foundDots = 0;
					int kLength = k.length();
					for (int i = 0; i < kLength; i++) {
						if (k.charAt(i) == '.') {
							foundDots++;
							if (foundDots > dots) break;
						}
					}
					if (foundDots > dots) {
						if (!dotsReached) {
							dotsNames = new HashSet<String>();
							dotsReached = true;
						}
						dotsNames.add(k);
						continue;
					}
					String newPropName = prefix + k;
					Object key = recognizeAndParseObject(prop, keyName, k, keyType, keyParamType);
					if (key == error) return error;
					if (key == null) continue;
					String v = (String) prop.getProperty(newPropName);
					Object val = recognizeAndParseObject(prop, newPropName, v, valueType, valueParamType);
					if (val == error) return error;
					value.put(key, val);
					if (v == null || v.length() <= 0 || (v.startsWith("[") && v.endsWith("]"))) {
						parsedKeys.add(k + ".");
					} // else given v is a line for object, no need to put it into parsed keys set
				}
				if (!dotsReached) break;
				dots++;
				names = dotsNames;
				dotsNames = null;
			} while (dots < Math.max(1, Config.configurationMapSearchingDots));
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
			if (key == error) return error;
			if (key == null) continue;
			String v = kv[1].trim();
			Object val = recognizeAndParseObject(prop, keyName, v, valueType, valueParamType);
			if (val == error) return error;
			value.put(key, val);
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
		if (p == null || $null.equals(p)) return null;
		if (type == null) return new Object();
		Object obj = null;
		try {
			obj = type.newInstance();
		} catch (Exception e) {
			e.printStackTrace();
		}
		if ($empty.equals(p) || p.length() == 0 || obj == null) return obj;

		String prefix = keyName + ".";
		Map<Class<?>, ConfigFieldFilter> configFilter = Config.configurationFilters;
		ConfigFieldFilter filter = configFilter != null ? configFilter.get(type) : null;
		if ($object.equals(p) || (p.startsWith("[") && p.endsWith("]"))) { // Multiple line configuration
			Field[] fields = type.getFields();
			for (int i = 0; i < fields.length; i++) {
				Field f = fields[i];
				if (f == null) continue; // never happen
				if (f.getAnnotation(ConfigIgnore.class) != null) continue;
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
				if (parseAndUpdateField(prop, fieldKeyName, pp, obj, f, true, null) == -1) return error;
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
			if (parseAndUpdateField(prop, prefix + k, pp, obj, f, true, null) == -1) return error;
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
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static Object recognizeAndParseObject(Properties prop, String keyName, String p, Class<?> type, Type paramType) {
		if (p == null || $null.equals(p)) return null;
		if (type == null || Utils.isObjectOrObjectArray(type) || Utils.isAbstractClass(type)) {
			Class<?> pType = recognizeObjectType(p);
			if (type == Enum.class && pType == String.class) {
				Object ret = parseEnumType(p, keyName);
				if (ret == error) return error;
				pType = (Class<?>) ret;
			}
			if (pType != null && pType != Object.class) type = pType;
			if (type == null) return new Object();
		}
		int length = p.length();
		if (length > 2 && p.charAt(0) == '[' && p.charAt(length - 1) == ']') {
			int idx = p.indexOf(':');
			if (idx != -1) {
				Object decoded = decode(p);
				if (decoded != null) return decoded;
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
		if (type == Class.class) {
			if (p.length() == 0) return null;
			StringBuilder err = new StringBuilder();
			Class<?> clazz = Config.loadConfigurationClass(p, err);
			if (clazz == null) {
				StringBuilder errMsg = new StringBuilder();
				errMsg.append("Invalid value for field \"").append(keyName)
						.append("\": ").append(err);
				if (!Config.reportErrorToContinue(errMsg.toString())) return error;
			}
			return clazz;
		}
		if (type.isEnum() || type == Enum.class) {
			int nameIdx = p.lastIndexOf('.');
			String nameStr = nameIdx != -1 ? p.substring(nameIdx + 1).trim() : p;
			return Enum.valueOf((Class<? extends Enum>) type, nameStr);
		}
		if (type == BigDecimal.class) return new BigDecimal(p);
		if (type == BigInteger.class) return new BigInteger(p);
		if (type.isArray() || List.class.isAssignableFrom(type)
				|| Set.class.isAssignableFrom(type)) {
			return parseCollection(prop, keyName, p, type, paramType);
		}
		if (Map.class.isAssignableFrom(type)) { // Map<String, Object>
			return parseMap(prop, keyName, p, type, paramType);
		}
		return parseObject(prop, keyName, p, type, paramType);
	}

	private static Class<?> recognizeObjectType(String p) {
		int length = p.length();
		if (length >= 2 && p.charAt(0) == '[' && p.charAt(length - 1) == ']' && !$empty.equals(p)) {
			if (length == 2) return Object.class;
			int idx = p.indexOf(':');
			if (idx == -1) {
				if ($array.equals(p)) return Object[].class;
				if ($list.equals(p)) return List.class;
				if ($map.equals(p)) return Map.class;
				if ($set.equals(p)) return Set.class;
				if ($object.equals(p)) return Object.class;
				String rawType = p.substring(1, length - 1);
				return Config.loadConfigurationClass(rawType);
			}
			String prefix = p.substring(0, idx);
			
			Map<String, Class<? extends IConfigCodec<?>>> codecs = Config.configurationCodecs;
			if (codecs != null) {
				Class<? extends IConfigCodec<?>> clazz = codecs.get(prefix.substring(1).trim());
				if (clazz != null) {
					Type paramType = clazz.getGenericInterfaces()[0];
					Type valueType = ((ParameterizedType) paramType).getActualTypeArguments()[0];
					Class<?> objType = Utils.getRawType(valueType);
					if (objType != null) return objType;
				}
			}
			
			String suffix = p.substring(idx + 1, length - 1);
			if ($array.startsWith(prefix)) {
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
			if ($list.startsWith(prefix)) return List.class;
			if ($map.startsWith(prefix)) return Map.class;
			if ($set.startsWith(prefix)) return Set.class;
			if ($object.startsWith(prefix)) {
				Class<?> type = Config.loadConfigurationClass(suffix);
				if (type == null) type = Object.class;
				return type;
			}
			if (prefix.startsWith("[Enum")) {
				int nameIdx = suffix.lastIndexOf('.');
				if (nameIdx == -1) return Enum.class;
				String rawType = suffix.substring(0, nameIdx);
				return Config.loadConfigurationClass(rawType);
			}
			String rawType = prefix.substring(1); // e.g. "[im.webuzz.config.####
			Class<?> type = recognizeRawType(rawType);
			if (type == null) type = Object.class;
			return type;
		}
		return String.class;
	}
	
	private static Map<String, Class<?>> knownTypes = new ConcurrentHashMap<String, Class<?>>();
	static {
		knownTypes.put("Integer", Integer.class);
		knownTypes.put("Long", Long.class);
		knownTypes.put("Boolean", Boolean.class);
		knownTypes.put("Double", Double.class);
		knownTypes.put("Float", Float.class);
		knownTypes.put("Short", Short.class);
		knownTypes.put("Byte", Byte.class);
		knownTypes.put("Character", Character.class);
		knownTypes.put("BigDecimal", BigDecimal.class);
		knownTypes.put("BigInteger", BigInteger.class);
		knownTypes.put("String", String.class);
		knownTypes.put("string", String.class);
		knownTypes.put("Class", Class.class);
		knownTypes.put("class", Class.class);
		knownTypes.put("List", List.class);
		knownTypes.put("list", List.class);
		knownTypes.put("Map", Map.class);
		knownTypes.put("map", Map.class);
		knownTypes.put("Set", Set.class);
		knownTypes.put("set", Set.class);
	}
	
	private static Class<?> recognizeRawType(String rawType) {
		Class<?> c = knownTypes.get(rawType);
		if (c != null) return c;
		return Config.loadConfigurationClass(rawType);
	}

}
