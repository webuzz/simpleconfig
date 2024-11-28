package im.webuzz.config;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import im.webuzz.config.annotations.ConfigComment;
import im.webuzz.config.annotations.ConfigEnum;
import im.webuzz.config.annotations.ConfigIgnore;
import im.webuzz.config.annotations.ConfigLength;
import im.webuzz.config.annotations.ConfigNonNegative;
import im.webuzz.config.annotations.ConfigNotEmpty;
import im.webuzz.config.annotations.ConfigNotNull;
import im.webuzz.config.annotations.ConfigNumberEnum;
import im.webuzz.config.annotations.ConfigPattern;
import im.webuzz.config.annotations.ConfigPositive;
import im.webuzz.config.annotations.ConfigRange;
import im.webuzz.config.annotations.ConfigCodec;
import im.webuzz.config.annotations.ConfigSince;
import static im.webuzz.config.GeneratorConfig.*;

public class ConfigINIGenerator implements IConfigGenerator {

	protected String $null;
	protected String $object;
	protected String $array;
	protected String $map;
	protected String $set;
	protected String $list;
	protected String $emptyObject; // empty object
	protected String $emptyArray; // empty array
	protected String $emptyString; // empty string
	
	protected String $compactSeparator;
	
	protected Set<Field> commentGeneratedFields;

	private Map<String, String> allFields;

	protected String indents;
	
	public ConfigINIGenerator() {
		$null = ConfigINIParser.$null;
		$object = ConfigINIParser.$object;
		$array = ConfigINIParser.$array;
		$map = ConfigINIParser.$map;
		$set = ConfigINIParser.$set;
		$list = ConfigINIParser.$list;
		$emptyObject = ConfigINIParser.$empty;
		$emptyArray = ConfigINIParser.$empty;
		$emptyString = ConfigINIParser.$empty;
		
		$compactSeparator = ";";
		
		commentGeneratedFields = new HashSet<Field>();
		allFields = new HashMap<String, String>();
		
		indents = "";
	}
	
	// For .ini or .properties files, there is no indents
	// For .js or .xml files, indents will be generated
	protected void increaseIndent() {
	}
	protected void decreaseIndent() {
	}

	protected StringBuilder checkIndents(StringBuilder builder) {
		int builderLength = builder.length();
		if (builderLength > 1 && builder.charAt(builderLength - 1) == '\n') {
			builder.append(indents);
		}
		return builder;
	}
	
	protected StringBuilder appendIndents(StringBuilder builder) {
		int length = builder.length();
		if (length >= 2 && builder.charAt(length - 1) != '\n') {
			builder.append("\r\n");
		} else if (length > 0) {
			char lastChar = builder.charAt(length - 1);
			if (lastChar == '\t') return builder; // skip adding more indents
			if ((lastChar == '{' || lastChar == '[') && indents.length() > 0) {
				builder.append("\r\n");
			}
		}
		return builder.append(indents);
	}

	// To provide a line of comment, e.g. a field's type
	protected void startLineComment(StringBuilder builder) {
		builder.append("# ");
	}
	protected void endLineComment(StringBuilder builder) {
		builder.append("\r\n");
	}
	
	// To provide more information about a type or a field 
	protected void startBlockComment(StringBuilder builder) {
	}
	protected StringBuilder addMiddleComment(StringBuilder builder) {
		return builder.append("# ");
	}
	protected void endBlockComment(StringBuilder builder) {
	}

	// To wrap or separate each configuration class
	public void startClassBlock(StringBuilder builder) {
	}
	public void endClassBlock(StringBuilder builder) {
	}
	
	protected String prefixedField(String prefix, String name) {
		return prefix + "." + name;
	}
	protected void appendLinebreak(StringBuilder builder) {
		int length = builder.length();
		if (length < 2 || builder.charAt(length - 1) != '\n') {
			builder.append("\r\n");
		}
	}
	
	// To wrap or separate an object with fields
	protected void startObjectBlock(StringBuilder builder, Class<?> type, boolean needsTypeInfo, boolean needsWrapping) {
		if (needsTypeInfo) {
			builder.append("[object:");
			appendFieldType(builder, type, null, false);
			builder.append("]");
		} else {
			builder.append($object);
		}
		builder.append("\r\n");
	}
	protected void endObjectBlock(StringBuilder builder, boolean needsWrapping) {
	}
	
	public void startGenerate(StringBuilder builder, Class<?> clz, boolean combinedConfigs) {
		if (builder.length() == 0) startClassBlock(builder);
		increaseIndent();
		startLineComment(builder);
		builder.append(clz.getSimpleName());
		endLineComment(builder); //.append("\r\n");
		//boolean skipUnchangedLines = false;
		String keyPrefix = null;
		if (combinedConfigs) { // generating combined configurations into one file
			keyPrefix = Config.getKeyPrefix(clz);
		}
		appendConfigComment(builder, clz.getAnnotation(ConfigComment.class));
		Field[] fields = clz.getDeclaredFields();
		String clzName = clz.getName();
		Map<Class<?>, ConfigFieldFilter> configFilter = Config.configurationFilters;
		ConfigFieldFilter filter = configFilter != null ? configFilter.get(clz) : null;
		for (int i = 0; i < fields.length; i++) {
			Field f = fields[i];
			if (f == null) continue;
			if ("heights".equals(f.getName())) {
				System.out.println("Debug");
			}
			if (f.getAnnotation(ConfigIgnore.class) != null) continue;
			int modifiers = f.getModifiers();
			if (ConfigFieldFilter.filterModifiers(filter, modifiers, false)) continue;
			String name = f.getName();
			if (filter != null && filter.filterName(name)) continue;
			if ((modifiers & Modifier.PUBLIC) == 0) f.setAccessible(true);

			if (keyPrefix != null)  {
				name = prefixedField(keyPrefix, name);
			}
			// To check if there are duplicate fields over multiple configuration classes, especially for
			// those classes without stand-alone configuration files.
			if (allFields.containsKey(name)) {
				System.out.println("[WARN] " + clzName + "." + name + " is duplicated with " + (allFields.get(name)));
			}
			allFields.put(name, clzName + "." + name);

			//*
			if ("town".equals(name)) {
				System.out.println("Debug");
			}
			//*/
			int oldLength = builder.length();
			generateFieldValue(builder, f, name, clz, null, null, null,
					false, false, 0, f.getAnnotationsByType(ConfigCodec.class),
					false, false, false, true);
			if (builder.length() > oldLength) {
				appendLinebreak(builder);
			}
		} // end of for fields
		decreaseIndent();
		if (builder.length() != 0 && !combinedConfigs) endClassBlock(builder);
	}

	public void endGenerate(StringBuilder builder, Class<?> clz, boolean combinedConfigs) {
		endClassBlock(builder);
	}
	
	protected boolean appendConfigComment(StringBuilder builder, ConfigComment configAnn) {
		if (configAnn == null) return false;
		String[] comments = configAnn.value();
		if (comments == null || comments.length == 0) return false;
		if (comments.length > 1) {
			startBlockComment(builder);
			for (int i = 0; i < comments.length; i++) {
				addMiddleComment(builder).append(comments[i]).append("\r\n");
			}
			endBlockComment(builder); // If ended, line break should be appended.
		} else {
			startLineComment(builder);
			builder.append(comments[0]);
			endLineComment(builder);
		}
		return true;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	protected boolean generateFieldValue(StringBuilder builder, Field f, String name, Object o,
			Object v, Class<?> definedType, Type paramType,
			boolean forKeys, boolean forValues, int depth, ConfigCodec[] codecs,
			boolean needsTypeInfo, boolean needsWrapping,
			boolean compact, boolean topConfigClass) {
		StringBuilder valueBuilder = new StringBuilder();
		StringBuilder typeBuilder = new StringBuilder();
		if ("refToFloatArr".equals(name)) {
			System.out.println("object array");
		}
		Class<?> type = definedType;
		if (f != null) {
			type = f.getType();
		}
		if (definedType == null) definedType = type;
		//boolean needsTypeInfo = false;
		try {
			if (type == null || Utils.isObjectOrObjectArray(type) || Utils.isAbstractClass(type)) {
				if (f != null) v = f.get(o);
				if (v != null) {
					type = v.getClass();
				}
				needsTypeInfo = true;
			}
			if (definedType == null) definedType = type;
			if (f != null) {
				codecs = f.getAnnotationsByType(ConfigCodec.class);
			}
			if (type.isPrimitive()) { // Config.isPrimitiveType(type)) {
				//if (typeBuilder != null) typeBuilder.append(type.getName());
				if (type == int.class) valueBuilder.append(f.getInt(o));
				else if (type == boolean.class) valueBuilder.append(f.getBoolean(o));
				else if (type == long.class) valueBuilder.append(f.getLong(o));
				else if (type == double.class) valueBuilder.append(f.getDouble(o));
				else if (type == float.class) valueBuilder.append(f.getFloat(o));
				else if (type == short.class) valueBuilder.append(f.getShort(o));
				else if (type == byte.class) valueBuilder.append(f.getByte(o));
				else valueBuilder.append(f.getChar(o)); // if (type == char.class) {
			} else { 
				if (f != null) paramType = f.getGenericType();
				if (f != null) v = f.get(o);
				if (v == null) {
					valueBuilder.append($null);
				} else if (encode(valueBuilder, v, forKeys, forValues, depth, codecs)) {
					// 
				} else if (type == String.class) {
					//if (typeBuilder != null) typeBuilder.append("String");
					generateString(valueBuilder, (String) v);
				} else if (type == Class.class) {
					//if (typeBuilder != null) typeBuilder.append("Class");
					generateClass(valueBuilder, (Class<?>) v, needsTypeInfo, needsWrapping, compact);
				} else if (type.isEnum() || type == Enum.class) {
					generateEnums(valueBuilder, (Enum) v, definedType, needsTypeInfo, needsWrapping, compact);
				} else if (Utils.isBasicDataType(type)) {
					generateBasicData(valueBuilder, v, type, needsTypeInfo, needsWrapping, compact);
				} else if (type.isArray()) {
					int arrayLength = Array.getLength(v);
					if (arrayLength > 0) {
						boolean finalCompactMode = compact;
						if (!compact && !readableArrayFormat
								&& checkCompactness(v, definedType, paramType,
										forKeys, forValues, depth, codecs, f)) {
							finalCompactMode = true;
						}
						generateCollection(valueBuilder, f, name, v, arrayLength,
								typeBuilder, definedType, paramType,
								forKeys, forValues, depth, codecs,
								needsTypeInfo, needsWrapping, finalCompactMode);
					} else {
						//if (typeBuilder != null) typeBuilder.append("array");
						valueBuilder.append($emptyArray);
					}
				} else if (List.class.isAssignableFrom(type)
						|| Set.class.isAssignableFrom(type)) {
					if (!needsTypeInfo) {
						if (paramType == null) {
							needsTypeInfo = true;
						} else {
							Class<?> valueType = null;
							Type valueParamType = null;
							if (paramType instanceof ParameterizedType) {
								valueParamType = ((ParameterizedType) paramType).getActualTypeArguments()[0];
								valueType = Utils.getRawType(valueParamType);
							}
							if (valueType == null || Utils.isObjectOrObjectArray(valueType) || Utils.isAbstractClass(valueType)) {
								needsTypeInfo = true;
							}
						}
					}
					int size = ((Collection) v).size();
					if (size > 0) {
						boolean finalCompactMode = compact;
						if (!compact && (!readableListFormat && List.class.isAssignableFrom(type)
									|| !readableSetFormat && Set.class.isAssignableFrom(type))
								&& checkCompactness(v, type, paramType,
										forKeys, forValues, depth, codecs, f)) {
							finalCompactMode = true;
						}
						generateCollection(valueBuilder, f, name, v, size,
								typeBuilder, definedType, paramType,
								forKeys, forValues, depth, codecs,
								needsTypeInfo, needsWrapping, finalCompactMode);
					} else {
						//if (typeBuilder != null) typeBuilder.append(List.class.isAssignableFrom(type) ? "list" : "set");
						valueBuilder.append($emptyArray); // A list or a set is like an array
					}
				} else if (Map.class.isAssignableFrom(type)) {
					if (!needsTypeInfo) {
						if (paramType == null) {
							needsTypeInfo = true;
						} else {
							Class<?> keyType = null;
							Type keyParamType = null;
							Class<?> valueType = null;
							Type valueParamType = null;
							if (paramType instanceof ParameterizedType) {
								Type[] actualTypeArgs = ((ParameterizedType) paramType).getActualTypeArguments();
								keyParamType = actualTypeArgs[0];
								keyType = Utils.getRawType(keyParamType);
								valueParamType = actualTypeArgs[1];
								valueType = Utils.getRawType(valueParamType);
							}
							if (keyType == null || Utils.isObjectOrObjectArray(keyType) || Utils.isAbstractClass(keyType)
									|| valueType == null || Utils.isObjectOrObjectArray(valueType) || Utils.isAbstractClass(valueType)) {
								needsTypeInfo = true;
							}
						}
					}
					Map os = (Map) v;
					if (os.size() > 0) {
						boolean finalCompactMode = compact;
						if (!compact && !readableMapFormat && checkCompactness(v, definedType, paramType,
								forKeys, forValues, depth, codecs, f)) {
							finalCompactMode = true;
						}
						generateMap(valueBuilder, f, name, os,
								typeBuilder, definedType, paramType,
								forKeys, forValues, depth, codecs,
								needsTypeInfo, needsWrapping, finalCompactMode);
					} else {
						//if (typeBuilder != null) typeBuilder.append("map");
						valueBuilder.append($emptyObject); // A map is like an object 
					}
				} else {
					boolean finalCompactMode = compact;
					if (!compact && !readableObjectFormat && checkCompactness(v, definedType, paramType,
							forKeys, forValues, depth, codecs, f)) {
						finalCompactMode = true;
					}
					generateObject(valueBuilder, f, name, v,
							typeBuilder, definedType, paramType,
							forKeys, forValues, depth, codecs,
							needsTypeInfo, needsWrapping, finalCompactMode);
					if (valueBuilder.length() == 0) valueBuilder.append($emptyObject);
				}
			}
			if (compact) {
				assign(builder, name, valueBuilder, typeBuilder, compact);
			} else {
				if (topConfigClass) builder.append("\r\n"); // Leave a blank for each field
				if (f != null) generateFieldComment(builder, f, topConfigClass);
				assign(builder, name, valueBuilder, typeBuilder, compact);
				//appendLinebreak(builder);
			}
			return true;
		} catch (Throwable e) {
			e.printStackTrace();
		}
		return false;
	}

	protected void appendEncodedString(StringBuilder builder, String codecKey, String encoded) {
		builder.append('[').append(codecKey).append(':').append(encoded).append(']');
	}

	@SuppressWarnings("unchecked")
	protected <T> boolean encode(StringBuilder builder, T v, boolean isKeys, boolean isValues, int depth, ConfigCodec[] configCodecs) {
		if (v == null || configCodecs == null || configCodecs.length == 0) return false;
		Map<String, Class<? extends IConfigCodec<?>>> codecs = Config.configurationCodecs;
		if (codecs == null || codecs.size() == 0) return false;
		String[] preferredCodecs = null;
		if (configCodecs.length == 1) {
			ConfigCodec cc = configCodecs[0];
			if (cc.key() && !isKeys) return false;
			if (cc.value() && !isValues) return false;
			if (cc.depth() >= 0 && depth != cc.depth()) return false;
			preferredCodecs = cc.preferences();
			if (preferredCodecs == null) preferredCodecs = new String[0];
		} else {
			List<String> allCodecs = null;
			boolean matched = false;
			for (ConfigCodec cc : configCodecs) {
				if (cc.key() && !isKeys) continue;
				if (cc.value() && !isValues) continue;
				if (cc.depth() >= 0 && depth != cc.depth()) continue;
				if (!matched) {
					matched = true;
					allCodecs = new ArrayList<String>();
				}
				String[] values = cc.preferences();
				if (values != null) {
					for (String c : values) {
						allCodecs.add(c);
					}
				}
			}
			if (!matched) return false;
			preferredCodecs = allCodecs.toArray(new String[allCodecs.size()]);
		}
		boolean all = false;
		if (preferredCodecs.length == 0) { // default empty, try to get one
			preferredCodecs = codecs.keySet().toArray(new String[codecs.size()]);
			all = true;
		}
		do {
			for (String codecKey : preferredCodecs) {
				if (codecKey == null || codecKey.length() == 0) continue;
				Class<? extends IConfigCodec<?>> clazz = codecs.get(codecKey);
				if (clazz == null) continue;
				Type[] genericInterfaces = clazz.getGenericInterfaces();
				if (genericInterfaces == null || genericInterfaces.length == 0) continue;
				Type paramType = genericInterfaces[0];
				if (paramType instanceof ParameterizedType) {
					Type valueType = ((ParameterizedType) paramType).getActualTypeArguments()[0];
					if (Utils.getRawType(valueType) != v.getClass()) continue;
				} else if (paramType != v.getClass()) continue;
				try {
					IConfigCodec<T> codec = (IConfigCodec<T>) clazz.newInstance();
					String encoded = codec.encode(v);
					if (encoded == null || encoded.length() == 0) continue;
					appendEncodedString(builder, codecKey, encoded);
					return true;
				} catch (InstantiationException e) {
					e.printStackTrace();
				} catch (IllegalAccessException e) {
					e.printStackTrace();
				}
			}
			if (all) break;
			// check other codecs besides the preferred codecs
			List<String> leftCodecs = new ArrayList<String>();
			List<String> preferreds = Arrays.asList(preferredCodecs);
			for (String key : codecs.keySet()) {
				if (preferreds.contains(key)) continue;
				leftCodecs.add(key);
			}
			if (leftCodecs.size() == 0) break;
			preferredCodecs = leftCodecs.toArray(new String[leftCodecs.size()]);
			all = true;
		} while (true);
		return false;
	}

	protected void generateString(StringBuilder builder, String v) {
//		if (v == null) {
//			builder.append($null);
//		} else 
		if (v.length() == 0) {
			builder.append($emptyString);
		} else {
			builder.append(configFormat(v));
		}
	}

	protected String configFormat(String str) {
		return formatString(str);
	}

	public static String formatString(String str) {
		return str.replaceAll("\\\\", "\\\\").replaceAll("\r", "\\\\r").replaceAll("\n", "\\\\n").replaceAll("\t", "\\\\t").trim();
	}
	
	protected boolean generateClass(StringBuilder builder, Class<?> v,
			boolean needsTypeInfo, boolean needsWrapping, boolean compact) {
		if (needsTypeInfo) {
			builder.append("[Class:").append(v.getName()).append(']');
		} else {
			builder.append(v.getName());
		}
		return true;
	}
	
	protected boolean generateEnums(StringBuilder builder, Enum<?> v, Class<?> type,
			boolean needsTypeInfo, boolean needsWrapping, boolean compact) {
		if (needsTypeInfo) {
			if (type != Enum.class) builder.append("[Enum:");
			builder.append(v.getClass().getName()).append('.').append(v.name());
			if (type != Enum.class) builder.append(']');
		} else {
			builder.append(v.name());
		}
		return true;
	}

	protected void generateBasicData(StringBuilder builder, Object o, Class<?> type,
			boolean needsTypeInfo, boolean needsWrapping, boolean compact) {
		if (type == Class.class) {
			Class<?> c = (Class<?>) o;
			if (needsTypeInfo) {
				builder.append("[").append(type.getSimpleName()).append(':').append(c.getName()).append("]");
			} else {
				builder.append(c.getName());
			}
			return;
		}
		if (needsTypeInfo) {
			builder.append("[").append(type.getSimpleName()).append(':').append(o).append("]");
		} else {
			builder.append(o);
		}
	}

	protected boolean supportsCompactArrays() {
		return false;
	}
	
	// For array, list and set
	@SuppressWarnings({ "unchecked", "rawtypes" })
	protected boolean checkCompactness(Object value, Class<?> definedType, Type paramType,
			boolean forKeys, boolean forValues, int depth, ConfigCodec[] codecs, Field field) {
		//Class<?> type = f.getType();
		if (definedType.isPrimitive()) return true;
		if (value == null) return true;
		
		//boolean needsTypeInfo = false;
		Class<?> realType = value.getClass();
		if (definedType != realType) {
			if (definedType == List.class || definedType == Set.class) {
				if (((Collection) value).isEmpty()) return true;
				if (definedType == List.class) {
					if (realType != ArrayList.class) return false;
				} else {
					if (!realType.isMemberClass()) {
						// check java.util.Collections.SetFromMap.SetFromMap<E>(Map<E, Boolean>)
						return false;
					}
				}
			} else if (definedType == Map.class) {
				if (((Map) value).isEmpty()) return true;
				if (realType != ConcurrentHashMap.class) return false;
			} else {
				return false; //
			}
		}
		//if (!needsTypeInfo) realType = definedType;
		if (definedType == Object.class) {
			if (realType == Object.class) return true;
			definedType = value.getClass();
		}

		if (definedType == String.class) {
			String str = (String) value;
			int length = str.length();
			if (forKeys) {
				for (int i = 0; i < length; i++) {
					char c = str.charAt(i);
					if (c == ' ' || c == '\t' || c == '\n' || c == '\r'
							|| c == '=' || c == ';' || c == '>' || c == '#')
						return false;
				}
			} else {
				for (int i = 0; i < length; i++) {
					char c = str.charAt(i);
					if (c == ';' || c == '>' || c == '\n' || c == '\r'
							|| c == '#')
						return false;
				}
			}
			return true;
		}
		if (Utils.isBasicDataType(definedType)) {
			return true;
		}
		if (definedType.isArray()) {
			if (forKeys) return false;
			Class<?> definedCompType = definedType.getComponentType();
			if (definedCompType.isArray() && !supportsCompactArrays()) return false;
			int size = Array.getLength(value);
			if (definedCompType.isPrimitive()) {
				return checkPrimitiveArrayCompactness(value, size, definedCompType);
			}
			if (size == 0) return true;
			if (field == null && !Utils.isBasicDataType(definedCompType) && definedCompType != String.class) {
				return false; // Array object is wrapped in another object
			}
			Type paramCompType = null;
			if (paramType instanceof GenericArrayType) {
				GenericArrayType gaType = (GenericArrayType) paramType;
				paramCompType = gaType.getGenericComponentType();
			}
			if (size == 1) {
				return checkCompactness(Array.get(value, 0), definedCompType, paramCompType,
						forKeys, forValues, depth, codecs, null);
			}
			/*
			Class<?> realCompType = realType.getComponentType();
			if (definedCompType != realCompType && !(definedCompType.isInterface() || TypeUtils.isAbstractClass(definedCompType))) {
				// The define type is the super type of the given field value's type.
				needsTypeInfo = true;
			}
			//*/
			Object[] arr = (Object[]) value;
			return checkArrayCompactness(arr, definedCompType, paramCompType,
					forKeys, forValues, depth, codecs);
		}
		if (List.class.isAssignableFrom(definedType) || Set.class.isAssignableFrom(definedType)) {
			if (forKeys) return false;
			Collection<Object> collection = (Collection<Object>) value;
			int size = collection.size();
			if (size == 0) return true;
			Class<?> definedCompType = Object.class;
			Type paramCompType = null;
			if (paramType instanceof ParameterizedType) {
				paramCompType = ((ParameterizedType) paramType).getActualTypeArguments()[0];
				definedCompType = Utils.getRawType(paramCompType);
			}
			if (field == null && !Utils.isBasicDataType(definedCompType) && definedCompType != String.class) {
				return false; // List or set object is wrapped in another object
			}
			collection.iterator().next();
			if (size == 1) {
				return checkCompactness(collection.iterator().next(), definedCompType, paramCompType,
						forKeys, forValues, depth, codecs, null);
			}
			return checkArrayCompactness(collection.toArray(new Object[size]), definedCompType, paramCompType,
					forKeys, forValues, depth, codecs);
		}
		if (Map.class.isAssignableFrom(definedType)) {
			if (forKeys) return false;
			Map map = (Map) value;
			int size = map.size();
			if (size == 0) return true;
			if (field == null) return false; // Map object is wrapped in another object
			Class<?> definedKeyType = Object.class;
			Type paramKeyType = null;
			Class<?> definedValueType = Object.class;
			Type paramValueType = null;
			if (paramType instanceof ParameterizedType) {
				Type[] actualTypeArgs = ((ParameterizedType) paramType).getActualTypeArguments();
				paramKeyType = actualTypeArgs[0];
				definedKeyType = Utils.getRawType(paramKeyType);
				paramValueType = actualTypeArgs[1];
				definedValueType = Utils.getRawType(paramValueType);
			}
			for (Object o : map.keySet()) {
				if (checkCompactness(o, definedKeyType, paramKeyType,
						true, forValues, depth, codecs, null)) return false;
			}
			for (Object o : map.values()) {
				if (checkCompactness(o, definedValueType, paramValueType,
						forKeys, true, depth, codecs, null)) return false;
			}
			return true;
		}
		
		// Normal class
		if (definedType != realType) {
			// In this case, we need to include class information and no compact mode is supported.
			return false;
		}
		Class<?> clz = value.getClass();
		Field[] fields = clz.getDeclaredFields();
		if (fields.length == 0) return true;
		Map<Class<?>, ConfigFieldFilter> configFilter = Config.configurationFilters;
		ConfigFieldFilter filter = configFilter != null ? configFilter.get(clz) : null;
		for (int i = 0; i < fields.length; i++) {
			Field f = fields[i];
			if (f == null) continue;
			if (f.getAnnotation(ConfigIgnore.class) != null) continue;
			int modifiers = f.getModifiers();
			if (ConfigFieldFilter.filterModifiers(filter, modifiers, true)) continue;
			Class<?> type = f.getType();
			if (type == String.class || type.isPrimitive() || Utils.isBasicDataType(type)) {
				continue; // ignore
			}
			String name = f.getName();
			if (filter != null && filter.filterName(name)) continue;
			if (field == null) return false; // Map object is wrapped in another object
			if (forKeys) return false;
			if ((modifiers & Modifier.PUBLIC) == 0) f.setAccessible(true);
			try {
				Object v = f.get(value);
				Type genericType = f.getGenericType();
				if (!checkCompactness(v, type, genericType,
						forKeys, forValues, depth, codecs, f)) return false;
			} catch (Throwable e) {
			}
		}
		return false;
	}

	public boolean checkPrimitiveArrayCompactness(Object arr, int size, Class<?> compType) {
		StringBuilder cb = new StringBuilder();
		for (int i = 0; i < size; i++) {
			if (i != 0) cb.append($compactSeparator);
			if (compType == int.class) cb.append(Array.getInt(arr, i));
			else if (compType == boolean.class) cb.append(Array.getBoolean(arr, i));
			else if (compType == long.class) cb.append(Array.getLong(arr, i));
			else if (compType == double.class) cb.append(Array.getDouble(arr, i));
			else if (compType == float.class) cb.append(Array.getFloat(arr, i));
			else if (compType == short.class) cb.append(Array.getShort(arr, i));
			else if (compType == byte.class) cb.append(Array.getByte(arr, i));
			else cb.append(Array.getChar(arr, i));
		}
		if (cb.length() > 100) return false;
		return true;
	}

	public boolean checkArrayCompactness(Object[] arr, Class<?> compType, Type paramCompType,
			boolean forKeys, boolean forValues, int depth, ConfigCodec[] codecs) {
		StringBuilder compactBuilder = new StringBuilder();
		for (int i = 0; i < arr.length; i++) {
			if (!checkCompactness(arr[i], compType, paramCompType,
					forKeys, forValues, depth, codecs, null)) return false;
			if (compType.isPrimitive() || compType == String.class
					|| Utils.isBasicDataType(compType)) {
				if (i != 0) compactBuilder.append($compactSeparator);
				generateFieldValue(compactBuilder, null, null, null,
						arr[i], compType, paramCompType,
						forKeys, forValues, depth + 1, codecs,
						false, false, false, false);
			}
		}
		if (compactBuilder.length() > 100) return false;
		return true;
	}

	protected boolean isBasicType(Class<?> type) {
		return type == String.class || Utils.isBasicDataType(type);
	}

	void generateCollection(StringBuilder builder, Field f, String name, Object vs, int collectionSize,
			StringBuilder typeBuilder, Class<?> type, Type paramType,
			boolean forKeys, boolean forValues, int depth, ConfigCodec[] codecs,
			boolean needsTypeInfo, boolean needsWrapping, boolean compact) {
		if (type == Object.class) {
			Class<?> vsType = vs.getClass();
			Class<?> valueType = vsType.getComponentType();
			if (valueType == null) valueType = Object.class;
			Type valueParamType = null;
			appendCollection(builder, f, name, vs, collectionSize,
					typeBuilder, type, paramType, valueType, valueParamType, null,
					forKeys, forValues, depth, codecs,
					needsTypeInfo, needsWrapping || (f == null && (name == null || name.length() == 0)), compact);
			return;
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
		if (valueType == null) valueType = Object.class;
		appendCollection(builder, f, name, vs, collectionSize,
				typeBuilder, type, paramType, valueType, valueParamType, null,
				forKeys, forValues, depth, codecs,
				needsTypeInfo, needsWrapping || (f == null && (name == null || name.length() == 0)), compact);
	}
	
	// Will always end with line break
	@SuppressWarnings({ "unchecked", "rawtypes" })
	protected void appendCollection(StringBuilder builder, Field f, String name, Object vs, int vsSize,
			StringBuilder typeBuilder, Class<?> type, Type paramType, Class<?> valueType, Type valueParamType, Class<?> componentType,
			boolean forKeys, boolean forValues, int depth, ConfigCodec[] codecs,
			boolean needsTypeInfo, boolean needsWrapping, boolean compact) {
		if (type == null || type == Object.class) {
			type = vs.getClass();
		}
		String typeStr;
		if (List.class.isAssignableFrom(type)) {
			typeStr = $list;
		} else if (Set.class.isAssignableFrom(type)) {
			typeStr = $set;
		} else {
			typeStr = $array;
		}
		if (componentType != null && componentType != Object.class && componentType != valueType) {
			// array, use array's class type directly
			needsTypeInfo = true;
			valueType = componentType;
			valueParamType = null;
		}
		Class<?> vsType = vs.getClass();
		if (needsTypeInfo && summarizeCollectionType && valueType == Object.class) {
			Set<Class<?>> conflictedClasses = new HashSet<Class<?>>(5);
			Collection<Object> os = null;
			if (vsType.isArray()) {
				if (!valueType.isPrimitive()) {
					os = Arrays.asList((Object[]) vs);
				}
			} else { // Collection
				os = (Collection) vs;
			}
			if (os != null) {
				Class<?> commonType = Utils.calculateCommonType(os, conflictedClasses);
				if (commonType != null && commonType != Object.class && conflictedClasses.size() == 0) {
					valueType = commonType;
				}
			}
		}
		Object[] values = null;
		if (List.class.isAssignableFrom(vsType) || Set.class.isAssignableFrom(vsType)) {
			values = ((Collection) vs).toArray(new Object[vsSize]);
		} else if (vsType.isArray()) {
			if (!valueType.isPrimitive()) {
				values = (Object[]) vs;
			}
		}
		if ("refToFloatArr".equals(name)) {
			System.out.println("Debug.");
		}
		if (compact) {
			if (typeBuilder != null) typeBuilder.append(typeStr.substring(1, typeStr.length() - 1));
			if (valueType.isPrimitive()) {
				for (int k = 0; k < vsSize; k++) {
					if (k > 0) builder.append(";");
					appendArrayPrimitive(builder, vs, k, valueType, compact);
				}
				return;
			}
			for (int k = 0; k < vsSize; k++) {
				if (k > 0) builder.append(";");
				Class<?> targetType = null;
				//boolean diffTypes = o != null && valueType != (targetType = o.getClass());
				boolean diffTypes = false;
				Object o = values[k];
				if (o != null) {
					targetType = o.getClass();
					if (valueType != null && (valueType != targetType
							|| valueType.isInterface()
							|| Utils.isAbstractClass(valueType))) {
						diffTypes = true;
					}
				}
				if (!diffTypes) targetType = valueType;
				generateFieldValue(builder, null, null, null, o, valueType, null,
						forKeys, forValues, depth + 1, codecs,
						diffTypes, true, compact, false);
			}
			return;
		}
		int oldLength = builder.length();
		if (needsTypeInfo && valueType != null && valueType != Object.class && valueType != String.class) {
			builder.append(typeStr.substring(0, typeStr.length() - 1)).append(':');
			appendFieldType(builder, valueType, null, false);
			builder.append("]");
		} else {
			builder.append(typeStr);
		}
		if (typeBuilder != null) {
			typeBuilder.append(builder.substring(oldLength + 1, builder.length() - 1));
		}
		builder.append("\r\n");
		
		int index = Math.max(0, startingIndex);
		int size = vsSize + index - 1;
		int length = String.valueOf(size).length();
		for (int k = 0; k < vsSize; k++) {
			StringBuilder sb = new StringBuilder(name).append('.');
			int deltaLen = length - String.valueOf(index).length();
			for (int i = 0; i < deltaLen; i++) {
				sb.append('0');
			}
			sb.append(index);
			String prefix = sb.toString();
			if (values == null) {
				builder.append(prefix).append(index).append("=");
				appendArrayPrimitive(builder, vs, k, valueType, compact).append("\r\n");
				index++;
				continue;
			}
			Class<?> targetType = null;
			//boolean diffTypes = o != null && valueType != (targetType = o.getClass());
			boolean diffTypes = false;
			Object o = values[k];
			if (o != null) {
				targetType = o.getClass();
				if (valueType != null && (valueType != targetType
						|| valueType.isInterface()
						|| Utils.isAbstractClass(valueType))) {
					diffTypes = true;
				}
			}
			if (!diffTypes) targetType = valueType;
			generateFieldValue(builder, null, prefix, null, o, targetType, valueParamType,
					forKeys, forValues, depth + 1, codecs,
					diffTypes, true, compact, false);
			appendLinebreak(builder);
			index++;
		}
	}

	protected StringBuilder appendArrayPrimitive(StringBuilder builder, Object vs, int k, Class<?> compType, boolean compact) {
		if (compType == int.class) {
			builder.append(Array.getInt(vs, k));
		} else if (compType == long.class) {
			builder.append(Array.getLong(vs, k));
		} else if (compType == byte.class) {
			builder.append(Array.getByte(vs, k));
		} else if (compType == short.class) {
			builder.append(Array.getShort(vs, k));
		} else if (compType == char.class) {
			char ch = Array.getChar(vs, k);
			if (0x20 <= ch && ch <= 0x7e) {
				builder.append(ch);
			} else {
				builder.append("0x").append(Integer.toHexString(ch));
			}
		} else if (compType == boolean.class) {
			builder.append(Array.getBoolean(vs, k));
		} else if (compType == float.class) {
			builder.append(Array.getFloat(vs, k));
		} else if (compType == double.class) {
			builder.append(Array.getDouble(vs, k));
		}
		return builder;
	}

	void generateMap(StringBuilder builder, Field f, String name, Map<Object, Object> vs,
			StringBuilder typeBuilder, Class<?> type, Type paramType,
			boolean forKeys, boolean forValues, int depth, ConfigCodec[] codecs,
			boolean needsTypeInfo, boolean needsWrapping, boolean compact) {
		Class<?> keyType = null;
		Type keyParamType = null;
		Class<?> valueType = null;
		Type valueParamType = null;
		if (paramType instanceof ParameterizedType) {
			Type[] actualTypeArgs = ((ParameterizedType) paramType).getActualTypeArguments();
			keyParamType = actualTypeArgs[0];
			keyType = Utils.getRawType(keyParamType);
			valueParamType = actualTypeArgs[1];
			valueType = Utils.getRawType(valueParamType);
		}
		boolean keyNeedsTypeInfo = false; //needsTypeInfo;
		if (keyType == null || keyType == Object.class) {
			keyNeedsTypeInfo = true;
		}
		boolean valueNeedsTypeInfo = false; //needsTypeInfo;
		if (valueType == null || valueType == Object.class) {
			valueNeedsTypeInfo = true;
		}
		if (keyNeedsTypeInfo && summarizeCollectionType && (keyType == null || keyType == Object.class)) {
			Set<Class<?>> conflictedClasses = new HashSet<Class<?>>(5);
			Class<?> commonType = Utils.calculateCommonType(vs.keySet(), conflictedClasses);
			if (commonType != null && commonType != Object.class && conflictedClasses.size() == 0) {
				keyType = commonType;
			}
		}
		if (valueNeedsTypeInfo && summarizeCollectionType && (valueType == null || valueType == Object.class)) {
			Set<Class<?>> conflictedClasses = new HashSet<Class<?>>(5);
			Class<?> commonType = Utils.calculateCommonType(vs.values(), conflictedClasses);
			if (commonType != null && commonType != Object.class && conflictedClasses.size() == 0) {
				valueType = commonType;
			}
		}
		Object[] keys = vs.keySet().toArray(keyType == Class.class ? new Class[vs.size()] : new Object[vs.size()]);
		if (sortedMapFormat && keyType != null) {
			if (Comparable.class.isAssignableFrom(keyType)) {
				Arrays.sort(keys);
			} else if (keyType == Class.class) {
				Arrays.sort((Class[])keys, Comparator.comparing(Class::getName));
			}
		}
		appendMap(builder, f, name, vs, keys,
				typeBuilder, keyType, keyParamType, valueType, valueParamType,
				forKeys, forValues, depth, codecs,
				needsTypeInfo, keyNeedsTypeInfo, valueNeedsTypeInfo, needsWrapping, compact);
	}
	
	protected void appendMap(StringBuilder builder, Field f, String name, Map<Object, Object> vs, Object[] keys,
			StringBuilder typeBuilder, Class<?> keyType, Type keyParamType, Class<?> valueType, Type valueParamType,
			boolean forKeys, boolean forValues, int depth, ConfigCodec[] codecs,
			boolean mapNeedsTypeInfo, boolean keyNeedsTypeInfo, boolean valueNeedsTypeInfo,
			boolean needsWrapping, boolean compact) {
		if ("mms".equals(name)) {
			System.out.println("XXX");
		}
		if (compact) {
			if (typeBuilder != null) typeBuilder.append("map");
			boolean first = true;
			for (Object k : keys) {
				if (!first) {
					builder.append(";");
				}
				Object o = vs.get(k);
				builder.append(configFormat(k.toString()))
						.append('>');
				boolean diffValueTypes = false;
				Class<?> targetValueType = null;
				if (o != null) {
					targetValueType = o.getClass();
					if (valueType != null && valueType != targetValueType && !valueType.isInterface()
							&& !Utils.isAbstractClass(valueType)) {
						diffValueTypes = true;
					}
				}
				if (!diffValueTypes) targetValueType = valueType;
				generateFieldValue(builder, null, null, null, o, valueType, null,
						false, true, depth + 1, codecs,
						diffValueTypes, false, compact, false);
				first = false;
			}
			return;
		}
		int oldLength = builder.length();
		if ((keyNeedsTypeInfo && keyType != null && keyType != Object.class && keyType != String.class)
				|| (valueNeedsTypeInfo && valueType != null && valueType != Object.class && valueType != String.class)){
			builder.append("[map:");
			appendFieldType(builder, keyType, null, false);
			builder.append(',');
			appendFieldType(builder, valueType, null, false);
			builder.append("]");
		} else {
			builder.append("[map]");
		}
		if (typeBuilder != null) {
			typeBuilder.append(builder.substring(oldLength + 1, builder.length() - 1));
		}
		builder.append("\r\n");

		boolean directPropsMode = false;
		if (keys.length == 0 || GeneratorConfig.preferKeyValueMapFormat
				&& (keyType == null && isBasicType(keyType)
						|| keyType == String.class && Utils.canKeysBePrefixedNames(keys))) { // make sure if the key is valid here or not
			directPropsMode = true;
			if (keys.length > 0 && codecs != null && codecs.length > 0) {
				StringBuilder valueBuilder = new StringBuilder();
				if (encode(valueBuilder, keys[0], true, false, depth + 1, codecs)) {
					directPropsMode = false;
				}
			}
		}
		if (directPropsMode) {
			for (Object k : keys) {
				Object o = vs.get(k);
				boolean diffValueTypes = false;
				Class<?> targetValueType = null;
				if (o != null) {
					targetValueType = o.getClass();
					if (valueType != null && valueType != targetValueType && !valueType.isInterface()
							&& !Utils.isAbstractClass(valueType)) {
						diffValueTypes = true;
					}
				}
				if (!diffValueTypes) targetValueType = valueType;
				String newPrefix = name + "." + k;
				generateFieldValue(builder, null, newPrefix, null, o, targetValueType, valueParamType,
						false, true, depth + 1, codecs,
						diffValueTypes, false, compact, false);
				appendLinebreak(builder);
			}
		} else {
			int index = startingIndex;
			String entriesPrefix = name + ".entries";
			builder.append(entriesPrefix).append("=[]");
			builder.append("\r\n");
			for (Object k : keys) {
				String kvPrefix = entriesPrefix + "." + index;
				builder.append(kvPrefix).append("=[]");
				builder.append("\r\n");
				//Class<?> targetType = null;
				//boolean diffTypes = o != null && valueType != (targetType = o.getClass());
				boolean diffKeyTypes = false;
				Class<?> targetKeyType = k.getClass();
				if (keyType != null && keyType != targetKeyType && !keyType.isInterface()
						&& !Utils.isAbstractClass(keyType)) {
					diffKeyTypes = true;
				}
				if (!diffKeyTypes) targetKeyType = keyType;
				generateFieldValue(builder, null, kvPrefix + ".key", null, k, targetKeyType, keyParamType,
						true, false, depth + 1, codecs,
						diffKeyTypes, false, compact, false);
				appendLinebreak(builder);
				boolean diffValueTypes = false;
				Object o = vs.get(k);
				Class<?> targetValueType = null;
				if (o != null) {
					targetValueType = o.getClass();
					if (valueType != null && valueType != targetValueType && !valueType.isInterface()
							&& !Utils.isAbstractClass(valueType)) {
						diffValueTypes = true;
					}
				}
				if (!diffValueTypes) targetValueType = valueType;
				generateFieldValue(builder, null, kvPrefix + ".value", null, o, targetValueType, valueParamType,
						false, true, depth + 1, codecs,
						diffValueTypes, false, compact, false);
				appendLinebreak(builder);
				index++;
			}
		}
	}
	
	// type is not basic data type or collection type
	void generateObject(StringBuilder builder, Field field, String keyPrefix, Object o,
			StringBuilder typeBuilder, Class<?> type, Type paramType,
			boolean forKeys, boolean forValues, int depth, ConfigCodec[] codecs,
			boolean needsTypeInfo, boolean needsWrapping, boolean compact) {
		if (o instanceof Object[]) {
			StringBuilder valueBuilder = new StringBuilder();
			Object[] os = (Object[]) o;
			if (os.length > 0) {
				generateCollection(valueBuilder, field, keyPrefix, Arrays.asList(os), os.length,
						typeBuilder, type, paramType,
						forKeys, forValues, depth, codecs,
						needsTypeInfo, needsWrapping, compact);
			} else {
				valueBuilder.append($emptyArray);
			}
			assign(builder, keyPrefix, valueBuilder, null, false);
			return;
		}
		if (typeBuilder != null) {
			if (needsTypeInfo) {
				typeBuilder.append("object:");
				if (type == null || type == Object.class) {
					type = o.getClass();
				}
				appendFieldType(typeBuilder, type, null, false);
			} else {
				//typeBuilder.append("object");
			}
		}

		//int oldLength = builder.length();
		startObjectBlock(builder, type, needsTypeInfo, needsWrapping);
		increaseIndent();
		boolean multipleLines = !compact; //readableObjectFormat || needsTypeInfo;
				//|| !checkCompactness(o, type, paramType, field, compact); // !isPlainObject(o);
		//boolean generated = false;
		boolean separatorGenerated = true; //false;
//		boolean fieldGenerated = false;
		Field[] fields = o.getClass().getDeclaredFields();
		Map<Class<?>, ConfigFieldFilter> configFilter = Config.configurationFilters;
		ConfigFieldFilter filter = configFilter != null ? configFilter.get(o.getClass()) : null;
		for (int i = 0; i < fields.length; i++) {
			Field f = fields[i];
			if (f == null) continue;
			if (f.getAnnotation(ConfigIgnore.class) != null) continue;
			int modifiers = f.getModifiers();
			if (ConfigFieldFilter.filterModifiers(filter, modifiers, true)) continue;
			String name = f.getName();
			if (filter != null && filter.filterName(name)) continue;
			if ((modifiers & Modifier.PUBLIC) == 0) f.setAccessible(true);
			if (multipleLines) {
//				if (!generated) {
//					increaseIndent();
//					startObjectBlock(builder, type, needsTypeInfo, false);
//					//if (fields.length > 0) builder.append("\r\n");
//					generated = true;
//				}
				if (keyPrefix != null)  {
					name = prefixedField(keyPrefix, name);
				}
				int oldLength = builder.length();
				generateFieldValue(builder, f, name, o, null, null, null,
						false, false, 0, f.getAnnotationsByType(ConfigCodec.class),
						false, false, compact, false);
				if (builder.length() > oldLength && !compact) {
					appendLinebreak(builder);
					//generated = true;
				}
			} else {
				if (!separatorGenerated) {
					builder.append(", "); // separatorGenerated was false by default
					separatorGenerated = true;
				}

				int oldLength = builder.length();
				generateFieldValue(builder, f, name, o, null, null, null,
						false, false, 0, f.getAnnotationsByType(ConfigCodec.class),
						false, false, true, false);
				if (builder.length() > oldLength) {
					separatorGenerated = false;
					//generated = true;
				}
			} // end of if multiple/single line configuration
		} // end of for fields
		//if (generated) {
			decreaseIndent();
			endObjectBlock(builder, needsWrapping);
			//appendLinebreak(builder);
		//}
	}

	protected int appendFieldAnnotation(StringBuilder builder, Annotation[] anns) {
		for (Annotation ann : anns) {
			startLineComment(builder);
			ConfigValidator.appendAnnotation(builder, ann, anns.length > 1);
			endLineComment(builder);
		}
		return anns.length;
	}

	protected int appendAllFieldAnnotations(StringBuilder annBuilder, Field f) {
		int annCount = 0;
		// The followings are array/list/set/map/object related
		annCount += appendFieldAnnotation(annBuilder, f.getAnnotationsByType(ConfigNotNull.class));
		annCount += appendFieldAnnotation(annBuilder, f.getAnnotationsByType(ConfigNotEmpty.class));
		annCount += appendFieldAnnotation(annBuilder, f.getAnnotationsByType(ConfigLength.class));
		// The followings are string related
		annCount += appendFieldAnnotation(annBuilder, f.getAnnotationsByType(ConfigEnum.class));
		annCount += appendFieldAnnotation(annBuilder, f.getAnnotationsByType(ConfigPattern.class));
		annCount += appendFieldAnnotation(annBuilder, f.getAnnotationsByType(ConfigCodec.class));
		// The followings are number related
		annCount += appendFieldAnnotation(annBuilder, f.getAnnotationsByType(ConfigNonNegative.class));
		annCount += appendFieldAnnotation(annBuilder, f.getAnnotationsByType(ConfigPositive.class));
		annCount += appendFieldAnnotation(annBuilder, f.getAnnotationsByType(ConfigRange.class));
		annCount += appendFieldAnnotation(annBuilder, f.getAnnotationsByType(ConfigNumberEnum.class));
		// The followings are version controlling related
		annCount += appendFieldAnnotation(annBuilder, f.getAnnotationsByType(ConfigSince.class));
		return annCount;
	}

	protected void generateFieldComment(StringBuilder builder, Field f, boolean topConfigClass) {
		if (!commentGeneratedFields.add(f)) return; // already generated
		
		if (addFieldComment) {
			appendConfigComment(builder, f.getAnnotation(ConfigComment.class));
		}
		if (addTypeComment) {
			Class<?> type = f.getType();
			if (skipSimpleTypeComment
					&& (type == int.class || type == String.class || type == boolean.class)) {
				return;
			}
			appendAllFieldAnnotations(builder, f);
			startLineComment(builder);
			Type paramType = f.getGenericType();
			appendFieldType(builder, type, paramType, true);
			endLineComment(builder);
		}
	}
	
	protected StringBuilder appendFieldType(StringBuilder builder, Class<?> type, Type paramType, boolean forComment) {
		if (forComment) {
			// For comments
			if (type.isArray()) {
				Class<?> compType = type.getComponentType();
				Type compParamType = null;
				if (paramType instanceof GenericArrayType) {
					GenericArrayType gaType = (GenericArrayType) paramType;
					compParamType = gaType.getGenericComponentType();
				}
				appendFieldType(builder, compType, compParamType, forComment).append("[]");
			} else if (Map.class.isAssignableFrom(type)) {
				builder.append(getTypeName(type));
				StringBuilder typeBuilder = new StringBuilder();
				appendParameterizedType(paramType, 0, forComment, typeBuilder).append(", ");
				appendParameterizedType(paramType, 1, forComment, typeBuilder);
				String innerType = typeBuilder.toString();
				if (!"Object, Object".equals(innerType)) {
					builder.append('<').append(innerType).append('>');
				}
			} else if (List.class.isAssignableFrom(type) || Set.class.isAssignableFrom(type) || Class.class.isAssignableFrom(type)) {
				builder.append(getTypeName(type));
				StringBuilder typeBuilder = new StringBuilder();
				appendParameterizedType(paramType, 0, forComment, typeBuilder);
				String innerType = typeBuilder.toString();
				if (!"Object".equals(innerType)) {
					builder.append('<').append(innerType).append('>');
				}
			} else {
				builder.append(getTypeName(type));
			}
			return builder;
		}
		
		// For configured value
		if (type.isArray()) {
			Class<?> compType = type.getComponentType();
			Type compParamType = null;
			if (paramType instanceof GenericArrayType) {
				GenericArrayType gaType = (GenericArrayType) paramType;
				compParamType = gaType.getGenericComponentType();
			}
			// TODO: To test recursive array type, like [array:[array]]
			builder.append("[array:");
			appendFieldType(builder, compType, compParamType, forComment);
			builder.append(']');
		} else if (Map.class.isAssignableFrom(type) || List.class.isAssignableFrom(type) || Set.class.isAssignableFrom(type)) {
			if (Map.class.isAssignableFrom(type)) {
				builder.append($map);
			} else if (Set.class.isAssignableFrom(type)) {
				builder.append($set);
			} else { // if (List.class.isAssignableFrom(type)) {
				builder.append($list);
			}
		} else {
			builder.append(getTypeName(type));
		}
		return builder;
	}
	
	protected StringBuilder appendParameterizedType(Type paramType, int argIndex, boolean forComment, StringBuilder builder) {
		if (paramType instanceof ParameterizedType) {
			Type valueType = ((ParameterizedType) paramType).getActualTypeArguments()[argIndex];
			appendFieldType(builder, Utils.getRawType(valueType), valueType, forComment);
		} else {
			builder.append(forComment ? "Object" : "java.lang.Object");
		}
		return builder;
	}
	
	private String getTypeName(Class<?> type) {
		String typeName = type.getName();
		if (typeName.startsWith("java.") || typeName.startsWith("javax.")) {
			return type.getSimpleName();
		}
		return typeName;
	}

	protected StringBuilder assign(StringBuilder builder, String name, StringBuilder value, StringBuilder type, boolean compact) {
		if (name == null || name.length() == 0) return builder.append(value);
		return builder.append(name).append(compact ? '>' : '=').append(value);
	}

}
