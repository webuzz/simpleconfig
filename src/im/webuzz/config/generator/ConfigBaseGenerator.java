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

package im.webuzz.config.generator;

import static im.webuzz.config.generator.GeneratorConfig.*;

import java.io.ByteArrayOutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
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

import im.webuzz.config.Config;
import im.webuzz.config.InternalConfigUtils;
import im.webuzz.config.annotation.ConfigPreferredCodec;
import im.webuzz.config.codec.ConfigCodec;
import im.webuzz.config.parser.AnnotationField;
import im.webuzz.config.parser.AnnotationProxy;
import im.webuzz.config.parser.ConfigField;
import im.webuzz.config.parser.ConfigFieldProxy;
import im.webuzz.config.util.BytesHelper;
import im.webuzz.config.util.TypeUtils;

public abstract class ConfigBaseGenerator implements CommentWriter.CommentWrapper, ConfigGenerator<StringBuilder> {

	private Map<String, String> allFields;
	protected CommentWriter commentWriter;
	protected AnnotationWriter annotationWriter;
	protected ClassWriter typeWriter;
	protected CompactWriter compactWriter;
	
	public ConfigBaseGenerator() {
		super();
		allFields = new HashMap<String, String>();
		typeWriter = new ClassWriter();
		annotationWriter = new AnnotationWriter();
		commentWriter = new CommentWriter(this);
		compactWriter = new CompactWriter();
	}

	protected abstract void generateNull(StringBuilder builder, boolean hasNamePrefix);
	protected abstract void generateEmptyArray(StringBuilder builder, boolean hasNamePrefix);
	protected abstract void generateEmptyObject(StringBuilder builder, boolean hasNamePrefix);

	protected abstract void generateString(StringBuilder builder, String v);
	protected abstract boolean generateClass(StringBuilder builder, Class<?> v,
			boolean needsTypeInfo, boolean needsWrapping, boolean compact);
	
	protected abstract boolean generateEnum(StringBuilder builder, Enum<?> v, Class<?> type,
			boolean needsTypeInfo, boolean needsWrapping, boolean compact);

	protected abstract void generateBasicData(StringBuilder builder, Object v, Class<?> type,
			boolean needsTypeInfo, boolean needsWrapping, boolean compact);

	protected abstract StringBuilder assign(StringBuilder builder, String name, StringBuilder value, StringBuilder type, boolean compact);

	protected abstract String prefixedField(String prefix, String name);

	protected abstract void appendSeparator(StringBuilder builder, boolean compact);
	
	protected void appendChar(StringBuilder builder, char ch) {
		if (0x20 <= ch && ch <= 0x7e) {
			builder.append('\'').append(ch).append('\'');
		} else {
			builder.append("0x").append(Integer.toHexString(ch));
		}
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	protected boolean generateFieldValue(StringBuilder builder, ConfigField f, String name, Object o,
			Object v, Class<?> definedType, Type paramType,
			boolean forKeys, boolean forValues, int depth, ConfigPreferredCodec[] codecs,
			boolean needsTypeInfo, boolean needsWrapping,
			boolean compact, boolean hasNamePrefix, boolean topConfigClass) {
		StringBuilder valueBuilder = new StringBuilder();
		StringBuilder typeBuilder = new StringBuilder();
		/*
		if ("configurationPackages".equals(name)) {
			System.out.println("object array");
		}
		//*/
		Class<?> type = definedType;
		if (f != null) {
			type = f.getType();
			if (definedType == null) definedType = type;
		}
		//boolean needsTypeInfo = false;
		try {
			if (type == Annotation.class) {
				if (f != null) v = f.get(o);
				if (v != null) type = ((Annotation) v).annotationType();
				needsTypeInfo = true;
			} else if (type == null || TypeUtils.isObjectOrObjectArray(type) || TypeUtils.isAbstractClass(type)) {
				if (f != null) v = f.get(o);
				if (v != null) type = v.getClass();
				needsTypeInfo = true;
			}
			if (definedType == null) definedType = type;
			Field field = f == null ? null : f.getField();
			if (field != null) codecs = field.getAnnotationsByType(ConfigPreferredCodec.class);
			if (type.isPrimitive()) { // Config.isPrimitiveType(type)) {
				//if (typeBuilder != null) typeBuilder.append(type.getName());
				if (type == int.class) valueBuilder.append(f.getInt(o));
				else if (type == boolean.class) valueBuilder.append(f.getBoolean(o));
				else if (type == long.class) valueBuilder.append(f.getLong(o));
				else if (type == double.class) valueBuilder.append(f.getDouble(o));
				else if (type == float.class) valueBuilder.append(f.getFloat(o));
				else if (type == short.class) valueBuilder.append(f.getShort(o));
				else if (type == byte.class) valueBuilder.append(f.getByte(o));
				else appendChar(valueBuilder, f.getChar(o)); // if (type == char.class) {
			} else { 
				if (f != null) paramType = f.getGenericType();
				if (f != null) v = f.get(o);
				if (v == null) {
					generateNull(valueBuilder, f != null || hasNamePrefix);
				} else if (encode(valueBuilder, v, forKeys, forValues, depth, codecs)) {
					// 
				} else if (type == String.class) {
					//if (typeBuilder != null) typeBuilder.append("String");
					generateString(valueBuilder, (String) v);
				} else if (type == Class.class) {
					//if (typeBuilder != null) typeBuilder.append("Class");
					generateClass(valueBuilder, (Class<?>) v, needsTypeInfo, needsWrapping, compact);
				} else if (type.isEnum() || type == Enum.class) {
					generateEnum(valueBuilder, (Enum) v, definedType, needsTypeInfo, needsWrapping, compact);
				} else if (TypeUtils.isBasicDataType(type)) {
					generateBasicData(valueBuilder, v, type, needsTypeInfo, needsWrapping, compact);
				} else if (type.isArray()) {
					int arrayLength = Array.getLength(v);
					if (arrayLength > 0) {
						boolean finalCompactMode = compact;
						if (!compact && !readableArrayFormat
								&& compactWriter.checkCompactness(this, v, definedType, paramType,
										forKeys, forValues, depth, codecs, f)) {
							finalCompactMode = true;
						}
						generateCollection(valueBuilder, f, name, v, arrayLength,
								typeBuilder, definedType, paramType,
								forKeys, forValues, depth, codecs,
								needsTypeInfo, needsWrapping, finalCompactMode);
					} else {
						//if (typeBuilder != null) typeBuilder.append("array");
						generateEmptyArray(valueBuilder, f != null || hasNamePrefix);
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
								valueType = TypeUtils.getRawType(valueParamType);
							}
							if (valueType == null || TypeUtils.isObjectOrObjectArray(valueType) || TypeUtils.isAbstractClass(valueType)) {
								needsTypeInfo = true;
							}
						}
					}
					int size = ((Collection) v).size();
					if (size > 0) {
						boolean finalCompactMode = compact;
						if (!compact && (!readableListFormat && List.class.isAssignableFrom(type)
									|| !readableSetFormat && Set.class.isAssignableFrom(type))
								&& compactWriter.checkCompactness(this, v, type, paramType,
										forKeys, forValues, depth, codecs, f)) {
							finalCompactMode = true;
						}
						generateCollection(valueBuilder, f, name, v, size,
								typeBuilder, definedType, paramType,
								forKeys, forValues, depth, codecs,
								needsTypeInfo, needsWrapping, finalCompactMode);
					} else {
						//if (typeBuilder != null) typeBuilder.append(List.class.isAssignableFrom(type) ? "list" : "set");
						generateEmptyArray(valueBuilder, f != null || hasNamePrefix); // A list or a set is like an array
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
								keyType = TypeUtils.getRawType(keyParamType);
								valueParamType = actualTypeArgs[1];
								valueType = TypeUtils.getRawType(valueParamType);
							}
							if (keyType == null || TypeUtils.isObjectOrObjectArray(keyType) || TypeUtils.isAbstractClass(keyType)
									|| valueType == null || TypeUtils.isObjectOrObjectArray(valueType) || TypeUtils.isAbstractClass(valueType)) {
								needsTypeInfo = true;
							}
						}
					}
					Map os = (Map) v;
					if (os.size() > 0) {
						boolean finalCompactMode = compact;
						if (!compact && !readableMapFormat && compactWriter.checkCompactness(this, v, definedType, paramType,
								forKeys, forValues, depth, codecs, f)) {
							finalCompactMode = true;
						}
						generateMap(valueBuilder, name, os,
								typeBuilder, definedType, paramType,
								forKeys, forValues, depth, codecs,
								needsTypeInfo, needsWrapping, finalCompactMode);
					} else {
						//if (typeBuilder != null) typeBuilder.append("map");
						generateEmptyObject(valueBuilder, f != null || hasNamePrefix); // A map is like an object 
					}
				} else if (Annotation.class.isAssignableFrom(type)) {
					boolean finalCompactMode = compact;
					if (!compact && !readableObjectFormat && compactWriter.checkCompactness(this, v, definedType, paramType,
							forKeys, forValues, depth, codecs, f)) {
						finalCompactMode = true;
					}
					generateAnnotation(valueBuilder, name, v,
							typeBuilder, definedType, paramType,
							forKeys, forValues, depth, codecs,
							needsTypeInfo, needsWrapping, finalCompactMode);
					if (valueBuilder.length() == 0) generateEmptyObject(valueBuilder, f != null || hasNamePrefix);
				} else {
					boolean finalCompactMode = compact;
					if (!compact && !readableObjectFormat && compactWriter.checkCompactness(this, v, definedType, paramType,
							forKeys, forValues, depth, codecs, f)) {
						finalCompactMode = true;
					}
					generateObject(valueBuilder, name, v,
							typeBuilder, definedType, paramType,
							forKeys, forValues, depth, codecs,
							needsTypeInfo, needsWrapping, finalCompactMode);
					if (valueBuilder.length() == 0) generateEmptyObject(valueBuilder, f != null || hasNamePrefix);
				}
			}
			if (compact) {
				assign(builder, name, valueBuilder, typeBuilder, compact);
			} else {
				if (topConfigClass && GeneratorConfig.separateFieldsByBlankLines) builder.append("\r\n"); // Leave a blank for each field
				if (field != null) commentWriter.generateFieldComment(builder, field, topConfigClass);
				assign(builder, name, valueBuilder, typeBuilder, compact);
				//appendLinebreak(builder);
			}
			return true;
		} catch (Throwable e) {
			e.printStackTrace();
		}
		return false;
	}

	protected abstract void appendEncodedString(StringBuilder builder, String codecKey, String encoded);

	@SuppressWarnings("unchecked")
	protected <T> boolean encode(StringBuilder builder, T v, boolean isKeys, boolean isValues, int depth, ConfigPreferredCodec[] configCodecs) {
		if (v == null || configCodecs == null || configCodecs.length == 0) return false;
		Map<String, ConfigCodec<?>> codecs = Config.configurationCodecs;
		if (codecs == null || codecs.size() == 0) return false;
		String[] preferredCodecs = null;
		if (configCodecs.length == 1) {
			ConfigPreferredCodec cc = configCodecs[0];
			if (cc.mapKey() && !isKeys) return false;
			if (cc.mapValue() && !isValues) return false;
			if (cc.depth() >= 0 && depth != cc.depth()) return false;
			preferredCodecs = cc.value();
			if (preferredCodecs == null) preferredCodecs = new String[0];
		} else {
			List<String> allCodecs = null;
			boolean matched = false;
			for (ConfigPreferredCodec cc : configCodecs) {
				if (cc.mapKey() && !isKeys) continue;
				if (cc.mapValue() && !isValues) continue;
				if (cc.depth() >= 0 && depth != cc.depth()) continue;
				if (!matched) {
					matched = true;
					allCodecs = new ArrayList<String>();
				}
				String[] values = cc.value();
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
			String[] order = GeneratorConfig.preferredCodecOrders;
			if (order != null && order.length > 0) {
				preferredCodecs = order;
			} else {
				preferredCodecs = codecs.keySet().toArray(new String[codecs.size()]);
				all = true;
			}
		}
		do {
			for (String codecKey : preferredCodecs) {
				if (codecKey == null || codecKey.length() == 0) continue;
				ConfigCodec<T> codec = (ConfigCodec<T>) codecs.get(codecKey);
				if (codec == null) continue;
				Class<?> rawType = TypeUtils.getInterfaceParamType(codec.getClass(), ConfigCodec.class);
				if (rawType != v.getClass()) continue;
				/*
				Type paramType = codec.getClass().getGenericInterfaces()[0];
				Type valueType = ((ParameterizedType) paramType).getActualTypeArguments()[0];
				if (Utils.getRawType(valueType) != v.getClass()) continue;
				//*/
				//IConfigCodec<T> codec = (IConfigCodec<T>) Config.codecs.get(codecKey);
				try {
//					if (codec == null) {
//						codec = (IConfigCodec<T>) clazz.newInstance();
//						Config.codecs.put(codecKey, codec);
//					}
					String encoded = codec.encode(v);
					if (encoded == null || encoded.length() == 0) continue;
					appendEncodedString(builder, codecKey, encoded);
					return true;
				} catch (Throwable e) {
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

	// Will always end with line break
	protected abstract void appendCollection(StringBuilder builder, String name, Object vs, int vsSize,
			StringBuilder typeBuilder, Class<?> type, Type paramType, Class<?> valueType, Type valueParamType, Class<?> componentType,
			boolean forKeys, boolean forValues, int depth, ConfigPreferredCodec[] codecs,
			boolean needsTypeInfo, boolean needsWrapping, boolean compact);

	void generateCollection(StringBuilder builder, Object f, String name, Object vs, int collectionSize,
			StringBuilder typeBuilder, Class<?> type, Type paramType,
			boolean forKeys, boolean forValues, int depth, ConfigPreferredCodec[] codecs,
			boolean needsTypeInfo, boolean needsWrapping, boolean compact) {
		if (type == Object.class) {
			Class<?> vsType = vs.getClass();
			Class<?> valueType = vsType.getComponentType();
			if (valueType == null) valueType = Object.class;
			Type valueParamType = null;
			appendCollection(builder, name, vs, collectionSize,
					typeBuilder, type, paramType, valueType, valueParamType, null,
					forKeys, forValues, depth, codecs,
					needsTypeInfo, needsWrapping || (f == null && (name == null || name.length() == 0)), compact);
			return;
		}
		Class<?> valueType = type.getComponentType();
		Type valueParamType = null;
		if (paramType instanceof ParameterizedType) {
			valueParamType = ((ParameterizedType) paramType).getActualTypeArguments()[0];
			valueType = TypeUtils.getRawType(valueParamType);
		} else if (paramType instanceof GenericArrayType) {
			GenericArrayType gaType = (GenericArrayType) paramType;
			valueParamType = gaType.getGenericComponentType();
		}
		if (valueType == null) valueType = Object.class;
		appendCollection(builder, name, vs, collectionSize,
				typeBuilder, type, paramType, valueType, valueParamType, null,
				forKeys, forValues, depth, codecs,
				needsTypeInfo, needsWrapping || (f == null && (name == null || name.length() == 0)), compact);
	}

	protected Object[] getObjectArray(Object vs, int vsSize, Class<?> realType, Class<?> valueType) {
		Object[] values = null;
		if (List.class.isAssignableFrom(realType) || Set.class.isAssignableFrom(realType)) {
			values = ((Collection<?>) vs).toArray(new Object[vsSize]);
		} else if (realType.isArray()) {
			if (!valueType.isPrimitive()) {
				values = (Object[]) vs;
			}
		}
		return values;
	}

	protected StringBuilder appendArrayPrimitive(StringBuilder builder, Object vs, int k, Class<?> compType, boolean compact) {
		if (compType == int.class) builder.append(Array.getInt(vs, k));
		else if (compType == long.class) builder.append(Array.getLong(vs, k));
		else if (compType == byte.class) builder.append(Array.getByte(vs, k));
		else if (compType == short.class) builder.append(Array.getShort(vs, k));
		else if (compType == boolean.class) builder.append(Array.getBoolean(vs, k));
		else if (compType == float.class) builder.append(Array.getFloat(vs, k));
		else if (compType == double.class) builder.append(Array.getDouble(vs, k));
		else if (compType == char.class) appendChar(builder, Array.getChar(vs, k));
		return builder;
	}

	protected abstract void appendMap(StringBuilder builder, String name, Map<Object, Object> vs, Object[] keys,
			StringBuilder typeBuilder, Class<?> keyType, Type keyParamType, Class<?> valueType, Type valueParamType,
			boolean forKeys, boolean forValues, int depth, ConfigPreferredCodec[] codecs,
			boolean mapNeedsTypeInfo, boolean keyNeedsTypeInfo, boolean valueNeedsTypeInfo,
			boolean needsWrapping, boolean compact);

	void generateMap(StringBuilder builder, String name, Map<Object, Object> vs,
			StringBuilder typeBuilder, Class<?> type, Type paramType,
			boolean forKeys, boolean forValues, int depth, ConfigPreferredCodec[] codecs,
			boolean needsTypeInfo, boolean needsWrapping, boolean compact) {
		Class<?> keyType = null;
		Type keyParamType = null;
		Class<?> valueType = null;
		Type valueParamType = null;
		if (paramType instanceof ParameterizedType) {
			Type[] actualTypeArgs = ((ParameterizedType) paramType).getActualTypeArguments();
			keyParamType = actualTypeArgs[0];
			keyType = TypeUtils.getRawType(keyParamType);
			valueParamType = actualTypeArgs[1];
			valueType = TypeUtils.getRawType(valueParamType);
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
			Class<?> commonType = TypeUtils.calculateCommonType(vs.keySet(), conflictedClasses);
			if (commonType != null && commonType != Object.class && conflictedClasses.size() == 0) {
				keyType = commonType;
			}
		}
		if (valueNeedsTypeInfo && summarizeCollectionType && (valueType == null || valueType == Object.class)) {
			Set<Class<?>> conflictedClasses = new HashSet<Class<?>>(5);
			Class<?> commonType = TypeUtils.calculateCommonType(vs.values(), conflictedClasses);
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
		appendMap(builder, name, vs, keys,
				typeBuilder, keyType, keyParamType, valueType, valueParamType,
				forKeys, forValues, depth, codecs,
				needsTypeInfo, keyNeedsTypeInfo, valueNeedsTypeInfo, needsWrapping, compact);
	}
	
	// To wrap or separate an object with fields
	protected abstract boolean startObjectBlock(StringBuilder builder, Class<?> type, boolean needsTypeInfo, boolean needsWrapping);
	protected abstract void endObjectBlock(StringBuilder builder, Class<?> type, boolean needsIndents, boolean needsWrapping);

	@SuppressWarnings("unchecked")
	void generateAnnotation(StringBuilder builder, String keyPrefix, Object o,
			StringBuilder typeBuilder, Class<?> type, Type paramType,
			boolean forKeys, boolean forValues, int depth, ConfigPreferredCodec[] codecs,
			boolean needsTypeInfo, boolean needsWrapping, boolean compact) {
		if (typeBuilder != null) {
			if (needsTypeInfo) {
				typeBuilder.append("annotation:");
				if (type == null || type == Annotation.class || type.isInterface()) {
					type = ((Annotation) o).annotationType();
				}
				typeWriter.appendFieldType(typeBuilder, type, null);
			} else {
				//typeBuilder.append("object");
			}
		}

		//int oldLength = builder.length();
		boolean needsSeparator = startObjectBlock(builder, type, needsTypeInfo, needsWrapping);
		compactWriter.increaseIndent();
		boolean generated = false;
		boolean separatorGenerated = !needsSeparator; //false;
		AnnotationProxy p = new AnnotationProxy((Class<? extends Annotation>) type, (Annotation) o);
		AnnotationField[] fields = p.getDeclaredFields();
		for (int i = 0; i < fields.length; i++) {
			AnnotationField f = fields[i];
			if (f == null) continue;
			String name = f.getName();
			if (!separatorGenerated) {
				appendSeparator(builder, compact);
				separatorGenerated = true;
			}
			if (keyPrefix != null)  {
				name = prefixedField(keyPrefix, name);
			}
			int oldLength = builder.length();
			generateFieldValue(builder, f, name, o, null, null, null,
					false, false, 0, null, //f.getAnnotationsByType(ConfigPreferredCodec.class),
					false, false, compact, true, false);
			if (builder.length() > oldLength) {
				separatorGenerated = false;
				generated = true;
			}
		} // end of for fields
		compactWriter.decreaseIndent();
		endObjectBlock(builder, type, generated, needsWrapping);
	}

	// type is not basic data type or collection type
	void generateObject(StringBuilder builder, String keyPrefix, Object o,
			StringBuilder typeBuilder, Class<?> type, Type paramType,
			boolean forKeys, boolean forValues, int depth, ConfigPreferredCodec[] codecs,
			boolean needsTypeInfo, boolean needsWrapping, boolean compact) {
		if (typeBuilder != null) {
			if (needsTypeInfo) {
				typeBuilder.append("object:");
				if (type == null || type == Object.class || type.isInterface()) {
					type = o.getClass();
				}
				typeWriter.appendFieldType(typeBuilder, type, null);
			} else {
				//typeBuilder.append("object");
			}
		}

		//int oldLength = builder.length();
		boolean needsSeparator = startObjectBlock(builder, type, needsTypeInfo, needsWrapping);
		compactWriter.increaseIndent();
		boolean generated = false;
		boolean separatorGenerated = !needsSeparator; //false;
		Field[] fields = o.getClass().getDeclaredFields();
		Map<Class<?>, Map<String, Annotation[]>> typeAnns = Config.configurationAnnotations;
		Map<String, Annotation[]> fieldAnns = typeAnns == null ? null : typeAnns.get(o.getClass());
		for (int i = 0; i < fields.length; i++) {
			Field f = fields[i];
			if (InternalConfigUtils.isFiltered(f, fieldAnns, true, false)) continue;
			if (!separatorGenerated) {
				appendSeparator(builder, compact);
				separatorGenerated = true;
			}
			String name = f.getName();
			if (keyPrefix != null)  name = prefixedField(keyPrefix, name);
			int oldLength = builder.length();
			generateFieldValue(builder, new ConfigFieldProxy(f), name, o, null, null, null,
					false, false, 0, f.getAnnotationsByType(ConfigPreferredCodec.class),
					false, false, compact, true, false);
			if (builder.length() > oldLength) {
				separatorGenerated = false;
				generated = true;
			}
		} // end of for fields
		compactWriter.decreaseIndent();
		endObjectBlock(builder, type, generated, needsWrapping);
	}

	protected boolean needsToAvoidCodecKeys(Object[] keys) {
		if (keys.length != 1) return false;
		Object k = keys[0];
		if (k instanceof Number || k.getClass().isArray()
				|| k instanceof Collection<?> || k instanceof Map<?, ?>) return false;
		Map<String, ConfigCodec<?>> configCodecs = Config.configurationCodecs;
		if (configCodecs == null) return false;
		// e.g { aes: "AES algorithm" }
		return configCodecs.containsKey(String.valueOf(k));
	}

	protected void appendMapKeyValue(StringBuilder builder, String name, Object k, Object v,
			Class<?> valueType, Type valueParamType,
			int depth, ConfigPreferredCodec[] codecs, boolean compact) {
		boolean diffValueTypes = false;
		//Class<?> targetValueType = null;
		if (v != null) {
			//targetValueType = v.getClass();
			diffValueTypes = checkTypeDefinition(valueType, v.getClass());
		}
		//if (!diffValueTypes) targetValueType = valueType;
		String prefix = k == null ? null : prefixedField(name, String.valueOf(k));;
		generateFieldValue(builder, null, prefix, null, v, valueType, valueParamType,
				false, true, depth + 1, codecs,
				diffValueTypes, false, compact, true, false);
	}

	protected void appendMapEntry(StringBuilder builder, String kvPrefix, Object k, Object v,
			Class<?> keyType, Type keyParamType, Class<?> valueType, Type valueParamType,
			int depth, ConfigPreferredCodec[] codecs) {
		boolean compact = false;
		//Class<?> targetKeyType = k.getClass();
		boolean diffKeyTypes = checkTypeDefinition(keyType, k.getClass());
		//if (!diffKeyTypes) targetKeyType = keyType;
		String prefix = prefixedField(kvPrefix, "key");
		generateFieldValue(builder, null, prefix, null, k, keyType, keyParamType,
				true, false, depth + 1, codecs,
				diffKeyTypes, false, compact, true, false);
		compactWriter.appendLinebreak(builder);
		boolean diffValueTypes = false;
		//Class<?> targetValueType = null;
		if (v != null) {
			//targetValueType = v.getClass();
			diffValueTypes = checkTypeDefinition(valueType, valueType);
		}
		//if (!diffValueTypes) targetValueType = valueType;
		prefix = prefixedField(kvPrefix, "value");
		generateFieldValue(builder, null, prefix, null, v, valueType, valueParamType,
				false, true, depth + 1, codecs,
				diffValueTypes, false, compact, true, false);
		compactWriter.appendLinebreak(builder);
	}

	// Return given real type is different the defined type or not
	protected boolean checkTypeDefinition(Class<?> definedType, Class<?> realType) {
		if (definedType == null || definedType == realType) return false;
		// definedType != realType
		if (definedType.isInterface()) {
			// value is not defined as List, Set, Map, ...
			if (List.class == definedType
					|| Set.class == definedType
					|| Map.class == definedType) {
				// These 3 known collection types will be initialized to specific instances
				// List => ArrayList
				// Set => SynchronizedSet
				// Map => ConcurrentHashMap
				// TODO: Needs further checking
				return false;
			}
			//return true;
		}
		/*
		if (Utils.isAbstractClass(definedType)) {
			// value is not defined as Number, AbstractList, ...
			return true;
		}
		//*/
		return true;
	}

	protected abstract boolean checkPlainKeys(Class<?> keyType, Object[] keys);
	
	protected boolean supportsDirectKeyValueMode(Object[] keys, Class<?> keyType, int depth, ConfigPreferredCodec[] codecs) {
		boolean directPropsMode = false;
		if (keys.length == 0 || GeneratorConfig.preferKeyValueMapFormat
				&& checkPlainKeys(keyType, keys)) { // make sure if the key is valid here or not
			directPropsMode = true;
			if (keys.length > 0 && codecs != null && codecs.length > 0) {
				StringBuilder valueBuilder = new StringBuilder();
				if (encode(valueBuilder, keys[0], true, false, depth + 1, codecs)) {
					directPropsMode = false;
				}
			}
		}
		return directPropsMode;
	}

	// To wrap or separate each configuration class
	public abstract void startClassBlock(StringBuilder builder);
	public abstract void endClassBlock(StringBuilder builder);

	public void startGenerate(StringBuilder builder, Class<?> clz) {
		if (builder.length() == 0) {
			startClassBlock(builder);
		} else {
			compactWriter.appendLinebreak(builder);
		}
		compactWriter.increaseIndent();
		if (GeneratorConfig.addTypeComment) {
			// The first line may be "$config={" in .js and
			// "<?xml version="1.0" encoding="UTF-8"?><config>" in .xml,
			// both < 64 characters
			if (builder.length() > 64) builder.append("\r\n");
			startLineComment(builder);
			builder.append(clz.getName());
			endLineComment(builder); //.append("\r\n");
		}
		//boolean skipUnchangedLines = false;
		//String keyPrefix = Config.getKeyPrefix(clz);
		commentWriter.generateTypeComment(builder, clz);
		Field[] fields = clz.getDeclaredFields();
		String clzName = clz.getName();
		Map<Class<?>, Map<String, Annotation[]>> typeAnns = Config.configurationAnnotations;
		Map<String, Annotation[]> fieldAnns = typeAnns == null ? null : typeAnns.get(clz);
		for (int i = 0; i < fields.length; i++) {
			Field f = fields[i];
			if (InternalConfigUtils.isFiltered(f, fieldAnns, false, false)) continue;
			//if (keyPrefix != null) name = prefixedField(keyPrefix, name);
			// To check if there are duplicate fields over multiple configuration classes, especially for
			// those classes without stand-alone configuration files.
			String name = f.getName();
			String fullFieldName = clzName + "." + name;
			if (allFields.containsKey(name) && !fullFieldName.equals(allFields.get(name))) {
				System.out.println("[Config:WARN] " + fullFieldName + " is duplicated with " + (allFields.get(name)));
			}
			allFields.put(name, fullFieldName);
			int oldLength = builder.length();
			generateFieldValue(builder, new ConfigFieldProxy(f), name, clz, null, null, null,
					false, false, 0, f.getAnnotationsByType(ConfigPreferredCodec.class),
					false, false, false, true, true);
			if (builder.length() > oldLength) {
				compactWriter.appendLinebreak(builder);
			}
		} // end of for fields
		compactWriter.decreaseIndent();
	}

	public void endGenerate(StringBuilder builder, Class<?> clz) {
		String keyPrefix = clz == null ? null : Config.getKeyPrefix(clz);
		boolean combinedConfigs = !GeneratorConfig.multipleFiles || keyPrefix == null || keyPrefix.length() == 0;
		if (builder.length() != 0 && (clz == null || !combinedConfigs)) endClassBlock(builder);
	}

	@Override
	public byte[] mergeFields(byte[] originalContent, List<Field> fields, List<Field> nextFields) {
		byte[] content = originalContent;
		int size = fields.size();
		for (int i = 0; i < size; i++) {
			Field f = fields.get(i);
			Field nextField = nextFields.get(i);
			int contentLength = content.length;
			byte[] nameBytes = getFieldPrefixedBytes(f);
			int nameLength = nameBytes.length;
			int startIdx = -1;
			int endIdx = -1;
			int searchIdx = 0;
			do {
				int idx = BytesHelper.indexOf(content, 0, contentLength, nameBytes, 0, nameLength, searchIdx); 
				if (idx == -1) break;
				int nextIdx = idx + nameLength;
				if (checkPrefix(content, contentLength, idx, nameLength, f) != -1) {
					int suffixIdx = checkSuffix(content, contentLength, nextIdx, nameLength,
							f, nextField, false, startIdx != -1);
					if (suffixIdx != -1) {
						if (startIdx == -1) startIdx = idx;
						endIdx = suffixIdx;
						nextIdx = suffixIdx;
					}
				}
				searchIdx = nextIdx;
			} while (true);
			if (startIdx < 0) continue; // not matched
			ByteArrayOutputStream baos = new ByteArrayOutputStream(contentLength + 64); // 64 extra size for potential modification 
			if (startIdx > 0) baos.write(content, 0, startIdx);
			StringBuilder builder = new StringBuilder();
			compactWriter.increaseIndent();
			generateFieldValue(builder, new ConfigFieldProxy(f), f.getName(), f.getDeclaringClass(), null, null, null,
					false, false, 0, f.getAnnotationsByType(ConfigPreferredCodec.class),
					false, false, false, true, true);
			compactWriter.decreaseIndent();
			if (builder.length() > 0) {
				byte[] bytes = builder.toString().getBytes(Config.configFileEncoding);
				int localStartIdx = -1;
				int localEndIdx = -1;
				int localSearchIdx = 0;
				int byteLength = bytes.length;
				do {
					int idx = BytesHelper.indexOf(bytes, 0, byteLength, nameBytes, 0, nameLength, localSearchIdx); 
					if (idx == -1) break;
					int nextIdx = idx + nameLength;
					if (checkPrefix(bytes, byteLength, idx, nameLength, f) != -1) {
						int suffixIdx = checkSuffix(bytes, byteLength, nextIdx, nameLength,
								f, null, true, localStartIdx != -1);
						if (suffixIdx != -1) {
							if (localStartIdx == -1) localStartIdx = idx;
							localEndIdx = suffixIdx;
							nextIdx = suffixIdx;
						}
					}
					localSearchIdx = nextIdx;
				} while (true);
				int originalLength = endIdx - startIdx;
				String originalStr = new String(content, startIdx, originalLength).trim();
				int localLength = localEndIdx - localStartIdx;
				String localStr = new String(bytes, localStartIdx, localLength).trim();
				if (originalStr.equals(localStr)) continue; // No update!
				boolean merged = false;
				StringBuilder simpleBuilder = new StringBuilder();
				compactWriter.increaseIndent();
				// Generate the field for the second time, comments for sub-object's fields won't be generated.
				generateFieldValue(simpleBuilder, new ConfigFieldProxy(f), f.getName(), f.getDeclaringClass(), null, null, null,
						false, false, 0, f.getAnnotationsByType(ConfigPreferredCodec.class),
						false, false, false, true, true);
				compactWriter.decreaseIndent();
				if (builder.length() > simpleBuilder.length()) {
					byte[] simpleBytes = simpleBuilder.toString().getBytes(Config.configFileEncoding);
					int simpleStartIdx = -1;
					int simpleEndIdx = -1;
					int simpleSearchIdx = 0;
					int simpleByteLength = simpleBytes.length;
					do {
						int idx = BytesHelper.indexOf(simpleBytes, 0, simpleByteLength, nameBytes, 0, nameLength, simpleSearchIdx); 
						if (idx == -1) break;
						int nextIdx = idx + nameLength;
						if (checkPrefix(simpleBytes, simpleByteLength, idx, nameLength, f) != -1) {
							int suffixIdx = checkSuffix(simpleBytes, simpleByteLength, nextIdx, nameLength,
									f, null, true, simpleStartIdx != -1);
							if (suffixIdx != -1) {
								if (simpleStartIdx == -1) simpleStartIdx = idx;
								simpleEndIdx = suffixIdx;
								nextIdx = suffixIdx;
							}
						}
						simpleSearchIdx = nextIdx;
					} while (true);
					int simpleLength = simpleEndIdx - simpleStartIdx;
					String simpleStr = new String(simpleBytes, simpleStartIdx, simpleLength).trim();
					if (originalStr.equals(simpleStr)) continue; // No update!
					/*
					System.out.println("=====VS=====");
					System.out.println(simpleStr);
					// */
					if (Math.abs(simpleLength - originalLength) < Math.abs(localLength - originalLength)) {
						baos.write(simpleBytes, simpleStartIdx, simpleByteLength - simpleStartIdx);
						merged = true;
					}
				}
				/*
				System.out.println("============");
				System.out.println(originalStr);
				System.out.println("=====vs=====");
				System.out.println(localStr);
				System.out.println("============");
				// */
				if (!merged) baos.write(bytes, localStartIdx, byteLength - localStartIdx);
			}
			if (contentLength > endIdx) {
				baos.write(content, endIdx, contentLength - endIdx);
			}
			byte[] newContent = baos.toByteArray();
			//System.out.println(new String(newContent, Config.configFileEncoding));
			content = newContent;
		}
		return content;
	}

	protected byte[] getFieldPrefixedBytes(Field f) {
		return f.getName().getBytes();
	}

	protected abstract int checkPrefix(byte[] content, int contentLength, int idx, int nameLength, Field field);

	protected abstract int checkSuffix(byte[] content, int contentLength, int nextIdx, int nameLength,
			Field field, Field nextField, boolean generated, boolean found);

}
