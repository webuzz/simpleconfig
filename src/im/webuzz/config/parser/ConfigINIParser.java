package im.webuzz.config.parser;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
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

import im.webuzz.config.Config;
import im.webuzz.config.ConfigFieldFilter;
import im.webuzz.config.AnnotationValidator;
import im.webuzz.config.DeepComparator;
import im.webuzz.config.IConfigCodec;
import im.webuzz.config.IConfigParser;
import im.webuzz.config.Utils;
import im.webuzz.config.annotation.ConfigIgnore;
import im.webuzz.config.annotation.ConfigRange;

public class ConfigINIParser implements IConfigParser<File, Object> {

	@ConfigRange(min = 1, max = 20)
	public static int configurationMapSearchingDots = 10;	

	protected static final String $null = "[null]";
	protected static final String $empty = "[empty]";
	protected static final String $array = "[array]";
	protected static final String $list = "[list]";
	protected static final String $set = "[set]";
	protected static final String $map = "[map]";
	protected static final String $object = "[object]";

	private final static Object error = new Object();

	private final static int unchanged = 0;

	private AnnotationValidator validator;
	protected Properties props;
	protected boolean combinedConfigs;
	public ConfigINIParser() {
		super();
		this.validator = new AnnotationValidator();
		this.props = new Properties();
	}
	
	@Override
	public Object loadResource(File source, boolean combinedConfigs) {
		this.combinedConfigs = combinedConfigs;
		FileInputStream fis = null;
		try {
			fis = new FileInputStream(source);
			props.load(new InputStreamReader(fis, Config.configFileEncoding));
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (fis != null) {
				try {
					fis.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		return null;
	}


//	@Override
//	public Object getConvertedResource() {
//		return props;
//	}


	/**
	 * Parse the given properties map into the given class's static fields.
	 * @param props
	 * @param clz
	 * @param combinedConfigs
	 * @param callUpdating
	 * @return Whether the given properties map contains matching configuration items or not
	 */
	@Override
	public int parseConfiguration(Class<?> clz, boolean updating) {
		if (clz == null || props.size() == 0) return 0;
		String keyPrefix = null;
		if (combinedConfigs) { // arguments or main file
			// all configuration items are in one file, use key prefix to distinguish fields
			keyPrefix = Config.getKeyPrefix(clz);
		} // else // single file, no keyPrefix
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
			String p = props.getProperty(keyName);
			if (p == null) continue; // given key does not exist
			itemMatched = true;
			p = p.trim();
			// Should NOT skip empty string, as it may mean empty string or default value
			//if (p.length() == 0) continue;
			if ((modifiers & Modifier.PUBLIC) == 0) f.setAccessible(true);
			int result = parseAndUpdateField(keyName, p, clz, f, updating);
			if (result == -1) return -1;
			if (result == 1 && updating && Config.configurationLogging && Config.isInitializationFinished()) {
				System.out.println("[Config] Configuration " + clz.getName() + "#" + name + " updated.");
			}
		}
		if (itemMatched && updating) {
			try {
				Method method = clz.getMethod("update", Properties.class);
				if (method != null && (method.getModifiers() & Modifier.STATIC) != 0) {
					method.invoke(null, props);
				}
			} catch (NoSuchMethodException e) {
				// ignore
			} catch (Throwable e) {
				e.printStackTrace();
			}
		}
		return itemMatched ? 1 : 0;
	}

	private Object parseEnumType(String p, String keyName) {
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
	 * @param props
	 * @param keyName
	 * @param p
	 * @param obj
	 * @param f
	 * @param updatingField
	 * @return -1: Errors are detected, 0: No fields are updated, 1: Field is updated.
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private int parseAndUpdateField(String keyName, String p,
			Object obj, Field f, boolean updatingField) {
		//*
		if ("configurationClasses".equals(keyName)) {
			System.out.println("X parse");
		} // */
		Class<?> type = f.getType();
		if (Utils.isObjectOrObjectArray(type) || Utils.isAbstractClass(type)) {
			Class<?> pType = recognizeObjectType(p);
			if (type == Enum.class && pType == String.class) {
				Object ret = parseEnumType(p, keyName);
				if (ret == error) return -1;
				pType = (Class<?>) ret;
			}
			if (pType != null && pType != Object.class) type = pType;
		}
		Object newVal = null;
		boolean changed = false;
		try {
			Object decoded = decode(p);
			if (type == String.class) {
				String nv = decoded != null ? (String) decoded : parseString(p);
				String ov = (String) f.get(obj);
				if ((nv == null && ov != null) || (nv != null && !nv.equals(ov))) {
					newVal = nv;
					changed = true;
				}
			} else if (type == int.class) {
				int nv = Integer.decode(p).intValue();
				if (nv == f.getInt(obj)) return unchanged;
				int result = validator.validatePrimitive(f, nv, 0, keyName);
				if (result == 1 && updatingField) f.setInt(obj, nv);
				return result;
			} else if (type == long.class) {
				long nv = Long.decode(p).longValue();
				if (nv == f.getLong(obj)) return unchanged;
				int result = validator.validatePrimitive(f, nv, 0, keyName);
				if (result == 1 && updatingField) f.setLong(obj, nv);
				return result;
			} else if (type == boolean.class) {
				boolean nv = Boolean.parseBoolean(p);
				if (nv == f.getBoolean(obj)) return unchanged;
				// Just true or false, no validating
				if (updatingField) f.setBoolean(obj, nv);
				return 1;
			} else if (type == double.class) {
				double nv = Double.parseDouble(p);
				if (nv == f.getDouble(obj)) return unchanged;
				int result = validator.validatePrimitive(f, nv, 0, keyName);
				if (result == 1 && updatingField) f.setDouble(obj, nv);
				return result;
			} else if (type == float.class) {
				float nv = Float.parseFloat(p);
				if (nv == f.getFloat(obj)) return unchanged;
				int result = validator.validatePrimitive(f, nv, 0, keyName);
				if (result == 1 && updatingField) f.setFloat(obj, nv);
				return result;
			} else if (type == short.class) {
				short nv = Short.decode(p).shortValue();
				if (nv == f.getShort(obj)) return unchanged;
				int result = validator.validatePrimitive(f, nv, 0, keyName);
				if (result == 1 && updatingField) f.setShort(obj, nv);
				return result;
			} else if (type == byte.class) {
				byte nv = Byte.decode(p).byteValue();
				if (nv == f.getByte(obj)) return unchanged;
				int result = validator.validatePrimitive(f, nv, 0, keyName);
				if (result == -1) return -1;
				if (result == 1 && updatingField) f.setByte(obj, nv);
				return result;
			} else if (type == char.class) {
				char nv = parseChar(p);
				if (nv == f.getChar(obj)) return unchanged;
				int result = validator.validatePrimitive(f, nv, 0, keyName);
				if (result == 1 && updatingField) f.setChar(obj, nv);
				return result;
			} else if (type != null && type.isArray()) {
				Object nv = decoded != null ? decoded : parseCollection(keyName, p, type, f.getGenericType());
				if (nv == error) return -1;
				if (!DeepComparator.arrayDeepEquals(type.getComponentType().isPrimitive(), nv, f.get(obj))) {
					newVal = nv;
					changed = true;
				}
			} else if (List.class.isAssignableFrom(type)) {
				Object ret = decoded != null ? decoded : parseCollection(keyName, p, type, f.getGenericType());
				if (ret == error) return -1;
				List nv = (List) ret;
				List ov = (List) f.get(obj);
				if (!DeepComparator.listDeepEquals(nv, ov)) {
					newVal = nv;
					changed = true;
				}
			} else if (Set.class.isAssignableFrom(type)) {
				Object ret = decoded != null ? decoded :  parseCollection(keyName, p, type, f.getGenericType());
				if (ret == error) return -1;
				Set nv = (Set) ret;
				Set ov = (Set) f.get(obj);
				if (!DeepComparator.setDeepEquals(nv, ov)) {
					newVal = nv;
					changed = true;
				}
			} else if (Map.class.isAssignableFrom(type)) {
				Object ret = decoded != null ? decoded : parseMap(keyName, p, type, f.getGenericType());
				if (ret == error) return -1;
				Map nv = (Map) ret;
				Map ov = (Map) f.get(obj);
				if (!DeepComparator.mapDeepEquals(nv, ov)) {
					newVal = nv;
					changed = true;
				}
			} else if (type == Integer.class || type == Long.class
					|| type == Byte.class || type == Short.class
					|| type == Float.class || type == Double.class
					|| type == BigDecimal.class || type == BigInteger.class
					|| type == Boolean.class || type == Character.class) {
				Object ov = f.get(obj);
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
					newVal = nv;
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
					newVal = nv;
					changed = true;
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
					} // else TODO:
					newVal = nv;
					changed = true;
				}
			} else {
				Object nv = decoded != null ? decoded : parseObject(keyName, p, type, f.getGenericType());
				if (nv == error) return -1;
				Object ov = f.get(obj);
				if ((nv == null && ov != null) || (nv != null && !nv.equals(ov))) {
					newVal = nv;
					changed = true;
				}
			}
			if (changed) {
				int result = validator.validateObject(f, newVal, 0, keyName);
				if (result == 1 && updatingField) f.set(obj, newVal);
				return result;
			}
		} catch (Throwable e) {
			e.printStackTrace();
			StringBuilder errMsg = new StringBuilder();
			errMsg.append("Invalid value for field \"").append(keyName)
					.append("\": ").append(e.getMessage());
			if (!Config.reportErrorToContinue(errMsg.toString())) return -1;
			return unchanged;
		}
		return unchanged;
	}

	/**
	 * [base64:###]: string encoded in Base64 format, see {@code im.webuzz.config.codecs.Base64Codec}
	 * [secret:###]: string encoded by the encryption, see {@code im.webuzz.config.codecs.SecretCodec}
	 * ...
	 */
	protected Object decode(String p) {
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

	protected Object decodeRaw(String codecKey, String rawEncoded) {
		IConfigCodec<?> codec = Config.configurationCodecs.get(codecKey);
		if (codec == null) return null;
		try {
			return codec.decode(rawEncoded);
		} catch (Throwable e) {
			e.printStackTrace();
			return null;
		}
//		Map<String, Class<? extends IConfigCodec<?>>> codecs = Config.configurationCodecs;
//		if (codecs == null) return null;
//		Class<? extends IConfigCodec<?>> clazz = codecs.get(codecKey);
//		if (clazz == null) return null;
//		try {
//			codec = (IConfigCodec<?>) clazz.newInstance();
//			Config.codecs.put(codecKey, codec);
//			return codec.decode(rawEncoded);
//		} catch (Throwable e) {
//			e.printStackTrace();
//		}
//		return null;
	}

	/**
	 * Parse the given string into a string, which will be used for the configuration field.
	 * Known string pattern:
	 * [null]: null string
	 * [empty]: ""
	 * @param p
	 * @return the raw string object
	 */
	private String parseString(String p) {
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
	private char parseChar(String p) {
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
	 * @param props
	 * @param keyName, The given prefix key
	 * @param p, Known value for the given key
	 * @param type, Field's type
	 * @param paramType, Field's generic type, if existed
	 * @return The parsed collection object. 
	 */
	private Object parseCollection(String keyName, String p, Class<?> type, Type paramType) {
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
				Set<String> names = props.stringPropertyNames();
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
				v = (String) props.getProperty(newPropName);
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
				o = recognizeAndParseObject(newPropName, v, valueType, valueParamType);
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
	 * @param props
	 * @param keyName
	 * @param p
	 * @param type
	 * @param paramType
	 * @return
	 */
	private Object parseMap(String keyName, String p, Class<?> type, Type paramType) {
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
			Set<String> names = props.stringPropertyNames();
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
					String v = (String) props.getProperty(newPropName);
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
								key = recognizeAndParseObject(newPropName, objStr, keyType, keyParamType);
								if (key == error) return error;
								if (val != null) break;
							} else if ("value".equals(keyStr)) {
								val = recognizeAndParseObject(newPropName, objStr, valueType, valueParamType);
								if (val == error) return error;
								if (key != null) break;
							}
						}
						if (key == null) continue;
						value.put(key, val);
					} else {
						String keyPrefix = newPropName + ".key";
						String kStr = (String) props.getProperty(keyPrefix);
						if (kStr == null) continue;
						Object key = recognizeAndParseObject(keyPrefix, kStr, keyType, keyParamType);
						if (key == error) return error;
						if (key == null) continue;
						String valuePrefix = newPropName + ".value";
						String vStr = (String) props.getProperty(valuePrefix);
						if (vStr == null) continue;
						Object val = recognizeAndParseObject(valuePrefix, vStr, valueType, valueParamType);
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
					Object key = recognizeAndParseObject(keyName, k, keyType, keyParamType);
					if (key == error) return error;
					if (key == null) continue;
					String v = (String) props.getProperty(newPropName);
					Object val = recognizeAndParseObject(newPropName, v, valueType, valueParamType);
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
			} while (dots < Math.max(1, configurationMapSearchingDots));
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
			Object key = recognizeAndParseObject(keyName, k, keyType, keyParamType);
			if (key == error) return error;
			if (key == null) continue;
			String v = kv[1].trim();
			Object val = recognizeAndParseObject(keyName, v, valueType, valueParamType);
			if (val == error) return error;
			value.put(key, val);
		}
		return value;
	}

	/**
	 * Parse the key-prefixed properties into a object with fields.
	 * @param props
	 * @param keyName
	 * @param p
	 * @param type
	 * @param paramType
	 * @return
	 */
	private Object parseObject(String keyName, String p, Class<?> type, Type paramType) {
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
				String pp = props.getProperty(fieldKeyName);
				if (pp == null) continue;
				pp = pp.trim();
				if (pp.length() == 0) continue;
				if ((modifiers & Modifier.PUBLIC) == 0) {
					f.setAccessible(true);
				}
				if (parseAndUpdateField(fieldKeyName, pp, obj, f, true) == -1) return error;
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
			if (parseAndUpdateField(prefix + k, pp, obj, f, true) == -1) return error;
		}
		return obj;
	}

	/**
	 * Recognize the object type and then parse the properties into an object of the given type.
	 * @param props
	 * @param keyName
	 * @param p
	 * @param type
	 * @param paramType
	 * @return
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private Object recognizeAndParseObject(String keyName, String p, Class<?> type, Type paramType) {
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
			return parseCollection(keyName, p, type, paramType);
		}
		if (Map.class.isAssignableFrom(type)) { // Map<String, Object>
			return parseMap(keyName, p, type, paramType);
		}
		return parseObject(keyName, p, type, paramType);
	}

	private Class<?> recognizeObjectType(String p) {
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
			
			IConfigCodec<?> codec = Config.configurationCodecs.get(prefix.substring(1).trim());
			if (codec != null) {
				Class<?> rawType = Utils.getInterfaceParamType(codec.getClass(), IConfigCodec.class);
				if (rawType != null) return rawType;
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
