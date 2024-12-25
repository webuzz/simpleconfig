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

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import im.webuzz.config.Config;
import im.webuzz.config.InternalConfigUtils;
import im.webuzz.config.annotation.ConfigPreferredCodec;
import im.webuzz.config.util.TypeUtils;

public class CompactWriter {

	protected String indents;
	
	public CompactWriter() {
		super();
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

	protected void appendLinebreak(StringBuilder builder) {
		int length = builder.length();
		if (length < 2 || builder.charAt(length - 1) != '\n') {
			builder.append("\r\n");
		}
	}
	
	public static String formatString(String str) {
		return str.replaceAll("\\\\", "\\\\\\\\").replaceAll("\r", "\\\\r").replaceAll("\n", "\\\\n").replaceAll("\t", "\\\\t").trim();
	}

	private static void appendChar(StringBuilder builder, char ch) {
		if (0x20 <= ch && ch <= 0x7e) {
			builder.append('\'').append(ch).append('\'');
		} else {
			builder.append("0x").append(Integer.toHexString(ch));
		}
	}

	// For array, list and set
	@SuppressWarnings({ "unchecked", "rawtypes" })
	protected boolean checkCompactness(ConfigBaseGenerator generator, Object value, Class<?> definedType, Type paramType,
			boolean forKeys, boolean forValues, int depth, ConfigPreferredCodec[] codecs, Object field) {
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
		if (TypeUtils.isBasicDataType(definedType)) return true;
		if (definedType.isArray()) {
			if (forKeys) return false;
			Class<?> definedCompType = definedType.getComponentType();
			if (definedCompType.isArray()) return false;
			int size = Array.getLength(value);
			if (definedCompType.isPrimitive()) {
				return checkPrimitiveArrayCompactness(generator, value, size, definedCompType);
			}
			if (size == 0) return true;
			if (field == null && !TypeUtils.isBasicDataType(definedCompType) && definedCompType != String.class) {
				return false; // Array object is wrapped in another object
			}
			Type paramCompType = null;
			if (paramType instanceof GenericArrayType) {
				GenericArrayType gaType = (GenericArrayType) paramType;
				paramCompType = gaType.getGenericComponentType();
			}
			if (size == 1) {
				return checkCompactness(generator, Array.get(value, 0), definedCompType, paramCompType,
						forKeys, forValues, depth + 1, codecs, null);
			}
			/*
			Class<?> realCompType = realType.getComponentType();
			if (definedCompType != realCompType && !(definedCompType.isInterface() || TypeUtils.isAbstractClass(definedCompType))) {
				// The define type is the super type of the given field value's type.
				needsTypeInfo = true;
			}
			//*/
			Object[] arr = (Object[]) value;
			return checkArrayCompactness(generator, arr, definedCompType, paramCompType,
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
				definedCompType = TypeUtils.getRawType(paramCompType);
			}
			if (field == null && !TypeUtils.isBasicDataType(definedCompType) && definedCompType != String.class) {
				return false; // List or set object is wrapped in another object
			}
			if (size == 1) {
				return checkCompactness(generator, collection.iterator().next(), definedCompType, paramCompType,
						forKeys, forValues, depth + 1, codecs, null);
			}
			return checkArrayCompactness(generator, collection.toArray(new Object[size]), definedCompType, paramCompType,
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
				definedKeyType = TypeUtils.getRawType(paramKeyType);
				paramValueType = actualTypeArgs[1];
				definedValueType = TypeUtils.getRawType(paramValueType);
			}
			if (!checkArrayCompactness(generator, map.keySet().toArray(new Object[size]),
					definedKeyType, paramKeyType,
					true, forValues, depth, codecs)) return false;

			if (!checkArrayCompactness(generator, map.values().toArray(new Object[size]),
					definedValueType, paramValueType,
					true, forValues, depth, codecs)) return false;
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
		Map<Class<?>, Map<String, Annotation[]>> typeAnns = Config.configurationAnnotations;
		Map<String, Annotation[]> fieldAnns = typeAnns == null ? null : typeAnns.get(clz);
		for (int i = 0; i < fields.length; i++) {
			Field f = fields[i];
			if (InternalConfigUtils.isFiltered(f, true, fieldAnns, false)) continue;
			Class<?> type = f.getType();
			if (type == String.class || type.isPrimitive() || TypeUtils.isBasicDataType(type)) {
				continue; // ignore
			}
			if (field == null) return false; // Map object is wrapped in another object
			if (forKeys) return false;
			try {
				Object v = f.get(value);
				Type genericType = f.getGenericType();
				if (!checkCompactness(generator, v, type, genericType,
						forKeys, forValues, depth, codecs, f)) return false;
			} catch (Throwable e) {
			}
		}
		return false;
	}

	private boolean checkPrimitiveArrayCompactness(ConfigBaseGenerator generator, Object arr, int size, Class<?> compType) {
		StringBuilder cb = new StringBuilder();
		for (int i = 0; i < size; i++) {
			if (i != 0) cb.append("; ");
			if (compType == int.class) cb.append(Array.getInt(arr, i));
			else if (compType == boolean.class) cb.append(Array.getBoolean(arr, i));
			else if (compType == long.class) cb.append(Array.getLong(arr, i));
			else if (compType == double.class) cb.append(Array.getDouble(arr, i));
			else if (compType == float.class) cb.append(Array.getFloat(arr, i));
			else if (compType == short.class) cb.append(Array.getShort(arr, i));
			else if (compType == byte.class) cb.append(Array.getByte(arr, i));
			else appendChar(cb, Array.getChar(arr, i)); // if (compType == char.class)
		}
		if (cb.length() > 100) return false;
		return true;
	}

	private boolean checkArrayCompactness(ConfigBaseGenerator generator,  Object[] arr, Class<?> compType, Type paramCompType,
			boolean forKeys, boolean forValues, int depth, ConfigPreferredCodec[] codecs) {
		StringBuilder compactBuilder = new StringBuilder();
		for (int i = 0; i < arr.length; i++) {
			if (!checkCompactness(generator, arr[i], compType, paramCompType,
					forKeys, forValues, depth + 1, codecs, null)) return false;
			if (compType.isPrimitive() || compType == String.class
					|| TypeUtils.isBasicDataType(compType)) {
				if (i != 0) compactBuilder.append("; ");
				// TODO: Make the following line independent from invoking #generateFieldValue
				generator.generateFieldValue(compactBuilder, null, null, null,
						arr[i], compType, paramCompType,
						forKeys, forValues, depth + 1, codecs,
						false, false, false, false, false);
			}
		}
		if (compactBuilder.length() > 100) return false;
		return true;
	}

//	protected abstract boolean generateFieldValue(StringBuilder builder, Field f, String name, Object o,
//			Object v, Class<?> definedType, Type paramType,
//			boolean forKeys, boolean forValues, int depth, ConfigCodec[] codecs,
//			boolean needsTypeInfo, boolean needsWrapping,
//			boolean compact, boolean topConfigClass);
	
}
