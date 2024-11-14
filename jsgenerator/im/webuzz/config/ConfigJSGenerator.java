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

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import im.webuzz.config.security.SecurityKit;

import java.util.Set;

/**
 * Generate configuration default file in JavaScript format.
 * 
 * @author zhourenjian
 *
 */
public class ConfigJSGenerator extends ConfigINIGenerator {

	public ConfigJSGenerator() {
		$null = "null";
		$object = "{}";
		$array = "[]";
		$map = $object;
		$set = $object;
		$list = $object;
		$emptyObject = $object;
		$emptyArray = $array;
		$emptyString = "";
		indents = "";
	}

	@Override
	protected void increaseIndent() {
		indents += "\t";
	}

	@Override
	protected void decreaseIndent() {
		int length = indents.length();
		if (length > 0) {
			indents = indents.substring(0, length - 1);
		}
	}

	@Override
	protected void startLineComment(StringBuilder builder) {
		appendIndents(builder).append("// ");
	}

	@Override
	protected void endLineComment(StringBuilder builder) {
		builder.append("\r\n");
	}

	@Override
	protected void startBlockComment(StringBuilder builder) {
		appendIndents(builder).append("/**\r\n");
	}

	@Override
	protected StringBuilder addMiddleComment(StringBuilder builder) {
		return appendIndents(builder).append(" * ");
	}

	@Override
	protected void endBlockComment(StringBuilder builder) {
		appendIndents(builder).append(" */\r\n");
	}

	@Override
	public void startClassBlock(StringBuilder builder) {
		builder.append("$config = {\r\n");
	}

	@Override
	protected String prefixedField(String prefix, String name) {
		//return getPrefixIndent(prefix) + "\t" + (keywords.contains(name) ? "\"" + name + "\"" : name);
		//return getPrefixIndent(prefix) + (keywords.contains(name) ? "\"" + name + "\"" : name);
		//return getPrefixIndent(prefix) + "\t" + name;
		return Utils.wrapAsJSFieldName(name);
	}

	@Override
	protected void appendLinebreak(StringBuilder builder) {
		int length = builder.length();
		if (length < 2 || builder.charAt(length - 1) != '\n') {
			builder.append(",\r\n");
		}
	}

	@Override
	public void endClassBlock(StringBuilder builder) {
		builder.append("}\r\n");
	}
	
	@Override
	protected void startObjectBlock(StringBuilder builder, Class<?> type, boolean needsTypeInfo, boolean needsWrapping) {
		int length = builder.length();
		if (length > 4 && builder.substring(length - 4).equals("},\r\n")) {
			builder.insert(length - 2, " {");
		} else if (length > 4 && builder.substring(length - 3).equals("[\r\n")) {
			builder.insert(length - 2, " {");
		} else {
			if (length > 1 && builder.charAt(length - 1) == '\n' && indents.length() > 0) {
				builder.append(indents.substring(0, indents.length() - 1));
			}
			builder.append("{");
		}
		if (needsTypeInfo) {
			appendIndents(builder);
			builder.append("\"class\": \"[object:");
			appendFieldType(builder, type, null, false);
			builder.append("]\",");
		}
	}

	@Override
	protected void endObjectBlock(StringBuilder builder, boolean needsWrapping) {
		appendIndents(builder).append("}");
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
		if (length >= 1 && builder.charAt(length - 1) != '\n') {
			builder.append("\r\n");
		}
		return builder.append(indents);
	}

	protected void generateString(StringBuilder builder, String v, boolean secret) {
		if (v == null) {
			builder.append($null);
		} else if (v.length() == 0) {
			builder.append("\"\""); //$emptyString;
		} else if (secret) {
			builder.append("\"[secret:" + SecurityKit.encrypt(v) + "]\"");
		} else {
			builder.append("\"" + configFormat(v) + "\"");
		}
	}

	@Override
	protected boolean generateClass(StringBuilder builder, Class<?> v, boolean needsTypeInfo) {
		if (v == null) {
			builder.append($null);
		} else if (needsTypeInfo) {
			builder.append("{ Class: \"").append(v.getName()).append("\" }");
		} else {
			builder.append("\"").append(v.getName()).append("\"");
		}
		return true;
	}

	@Override
	protected void generateBasicData(StringBuilder builder, Object v, Class<?> type, boolean needsTypeInfo) {
		Class<? extends Object> clazz = v.getClass();
		if (needsTypeInfo) builder.append("{ ").append(clazz.getSimpleName()).append(": ");
		if (Class.class == clazz) {
			builder.append("\"").append(((Class<?>) v).getName()).append("\"");
		} else if (BigInteger.class == clazz || BigDecimal.class == clazz) {
			builder.append("\"").append(v).append("\"");
		} else if (Character.class == clazz) {
			Character c = (Character) v;
			char ch = c.charValue();
			if (0x20 <= ch && ch <= 0x7e) {
				builder.append('\'').append(ch).append('\'');
			} else {
				builder.append("0x").append(Integer.toHexString(ch));
			}
		} else {
			builder.append(v);
		}
		if (needsTypeInfo) builder.append(" }");
	}

	String getPrefixIndent(String prefix) {
		int nameLength = prefix.length();
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < nameLength; i++) {
			if (prefix.charAt(i) == '\t') {
				builder.append('\t');
			}
		}
		return builder.toString();
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	protected void appendCollection(StringBuilder builder, Field f, String name, Object vs, int vsSize,
			StringBuilder typeBuilder, Class<?> type, Type paramType, Class<?> valueType, Type valueParamType, Class<?> componentType,
			boolean needsTypeInfo, boolean needsWrapping, boolean compact) {
		if ("mapArrs".equals(name)) {
			System.out.println("DEbug");
		}
		if (compact) {
			checkIndents(builder).append("[");
			//boolean singleItem = vsSize == 1;
			if (vsSize >= 1) builder.append(' ');
			if (valueType.isPrimitive()) {
				for (int k = 0; k < vsSize; k++) {
					if (k > 0) builder.append(", ");
					appendArrayPrimitive(builder, vs, k, valueType);
				}
				if (vsSize >= 1) builder.append(' ');
				builder.append("]");
				return;
			}
			int size = vsSize;
			Object[] values = null;
			if (List.class.isAssignableFrom(type) || Set.class.isAssignableFrom(type)) {
				values = ((Collection) vs).toArray(new Object[size]);
			} else if (type.isArray()) {
				if (!valueType.isPrimitive()) {
					values = (Object[]) vs;
				}
			}
			for (int k = 0; k < vsSize; k++) {
				Object o = values[k];
			//int k = 0;
			//for (Object o : (Collection) vs) {
				if (k > 0) builder.append(", ");
				Class<?> targetType = null;
				//boolean diffTypes = o != null && valueType != (targetType = o.getClass());
				boolean diffTypes = false;
				if (o != null) {
					targetType = o.getClass();
					if (valueType != null && (valueType != targetType || valueType.isInterface()
							|| Utils.isAbstractClass(valueType))) {
						diffTypes = true;
					}
				}
				if (!diffTypes) targetType = valueType;
				generateFieldValue(builder, null, null, null, o, targetType, valueParamType, diffTypes, true, compact, false);
				//generateTypeObject(null, builder, null, o, type, null, needsTypeInfo, true);
				//k++;
			}
			/*
			builder.append("\r\n");

			boolean first = true;
			for (Object o : vs) {
				if (!first) builder.append(", ");
				first = false;
				builder.append(type == String.class ? wrapAsPlainString((String) o) : (o == null ? $null : o));
			}
			//*/
			if (vsSize >= 1) builder.append(' ');
			builder.append("]");
			//appendLinebreak(builder);
			return;
		}
		checkIndents(builder);
		if (needsTypeInfo) {
//			if ("anyList".equals(name)) {
//				System.out.println("Debug[[");
//			}
			String typeStr;
			if (List.class.isAssignableFrom(type)) {
				typeStr = "[list]"; //$list;
			} else if (Set.class.isAssignableFrom(type)) {
				typeStr = "[set]"; //$set;
			} else {
				typeStr = "[array]"; //$array;
			}
			builder.append("{ \"class\": \"");
			if (needsTypeInfo && (valueType == null
					|| GeneratorConfig.summarizeCollectionType && valueType == Object.class)) {
				Set<Class<?>> conflictedClasses = new HashSet<Class<?>>(5);
				Collection<Object> os = null;
				if (type.isArray()) {
					os = Arrays.asList((Object[]) vs);
				} else { // Collection
					os = (Collection) vs;
				}
				Class<?> commonType = Utils.calculateCommonType(os, conflictedClasses);
				if (commonType != null && commonType != Object.class && conflictedClasses.size() == 0) {
					valueType = commonType;
				}
			}
			if (valueType == null || Utils.isObjectOrObjectArray(valueType) || valueType == String.class
					|| valueType.isInterface() || Utils.isAbstractClass(valueType)) {
				builder.append(typeStr);
			} else {
				builder.append(typeStr.substring(0, typeStr.length() - 1)).append(':');
				//builder.append("[array:");
				appendFieldType(builder, valueType, null, false);
				builder.append("]");
			}
			builder.append("\", value: ");
		}
		builder.append("[");
		if (valueType == null) valueType = Object.class;
		boolean basicType = isBasicType(valueType);
		int size = vsSize;
		Object[] values = null;
		if (List.class.isAssignableFrom(type) || Set.class.isAssignableFrom(type)) {
			values = ((Collection) vs).toArray(new Object[size]);
		} else if (type.isArray()) {
			if (!valueType.isPrimitive()) {
				values = (Object[]) vs;
			}
		}
		boolean singleLine = size == 1 && basicType;
		boolean multipleLines = size > 1 || !basicType;
		boolean moreIndents = basicType || valueType.isPrimitive() || valueType.isArray()
				|| List.class.isAssignableFrom(valueType)
				|| Set.class.isAssignableFrom(valueType);
		if (size <= 15) { // && (basicType || valueType == Object.class || Utils.isAbstractClass(valueType))) {
			StringBuilder compactBuilder = new StringBuilder();
			//compactBuilder.append(indents).append("[ ");
			compactBuilder.append("[ ");
			for (int k = 0; k < size; k++) {
				if (values == null) {
					//appendIndents(compactBuilder);
					appendArrayPrimitive(compactBuilder, vs, k, valueType);
				} else {
					Object o = values[k];
					if (o != null) {
						if (isBasicType(o.getClass())) {
							if (!moreIndents) moreIndents = true;
						} else if (size > 1) {
							multipleLines = true;
						}
					}
					Class<?> targetType = null;
					//boolean diffTypes = o != null && valueType != (targetType = o.getClass());
					boolean diffTypes = false;
					if (o != null) {
						targetType = o.getClass();
						if (valueType != null && (valueType != targetType || valueType.isInterface()
								|| Utils.isAbstractClass(valueType))) {
							diffTypes = true;
						}
					}
					if (!diffTypes) targetType = valueType;
					generateFieldValue(compactBuilder, null, null, null, o, targetType, valueParamType, diffTypes, true, true, false);
					//generateTypeObject(f, compactBuilder, "", o, targetType, valueParamType, diffTypes, false);
				}
				if (size > 1 && k != size - 1) compactBuilder.append(", ");
				if (compactBuilder.length() > 100 && moreIndents) {
					break;
				}
			}
			if (compactBuilder.length() <= 100) {
				singleLine = true;
				multipleLines = false;
			}
		}
		if (singleLine) builder.append(' ');
		if (multipleLines) {
			if (moreIndents) {
				builder.append("\r\n");
				increaseIndent();
			} else {
				builder.append(' ');
			}
		}
		if (multipleLines && !moreIndents) {
			System.out.println("XXX");
		}
		
		for (int k = 0; k < vsSize; k++) {
			if (values == null) {
				if (!singleLine) appendIndents(builder);
				appendArrayPrimitive(builder, vs, k, valueType);
			} else {
				Object o = values[k];
//				if (multipleLines && (basicType
//						|| ((valueType == Object.class || Utils.isAbstractClass(valueType))
//								&& o != null && isBasicType(o.getClass())))) {
//					appendIndents(builder);
//				}
				Class<?> targetType = null;
				boolean diffTypes = false;
				if (o != null) {
					targetType = o.getClass();
					if (valueType != null && (valueType != targetType || valueType.isInterface()
							|| Utils.isAbstractClass(valueType))) {
						diffTypes = true;
					}
				}
				if (!diffTypes) targetType = valueType;
				generateFieldValue(builder, null, null, null, o, targetType, valueParamType, diffTypes, true, singleLine, false);
				//generateTypeObject(f, builder, "", o, targetType, valueParamType, diffTypes, false);
			}
			if (singleLine && size > 1 && k != size - 1) builder.append(", ");
			if (multipleLines) appendLinebreak(builder);
		}
		if (singleLine) builder.append(' ');
		if (multipleLines) {
			if (moreIndents) {
				decreaseIndent();
				appendIndents(builder);
			}
		}
		int length = builder.length();
		if (!moreIndents && length > 4 && builder.substring(length - 4, length).equals("},\r\n")) {
			builder.insert(length - 3, needsTypeInfo ? " ] }" : " ]");
		} else {
			builder.append("]");
			if (needsTypeInfo) {
				builder.append(" }");
			}
		}
	}	

	@Override
	protected void appendMap(StringBuilder builder, Field f, String name, Map<Object, Object> vs, Object[] keys,
			StringBuilder typeBuilder, Class<?> keyType, Type keyParamType, Class<?> valueType, Type valueParamType,
			boolean needsTypeInfo, boolean keyNeedsTypeInfo, boolean valueNeedsTypeInfo,
			boolean needsWrapping, boolean compact) {
		if (keyNeedsTypeInfo && GeneratorConfig.summarizeCollectionType && (keyType == null || keyType == Object.class)) {
			Set<Class<?>> conflictedClasses = new HashSet<Class<?>>(5);
			Class<?> commonType = Utils.calculateCommonType(vs.keySet(), conflictedClasses);
			if (commonType != null && commonType != Object.class && conflictedClasses.size() == 0) {
				keyType = commonType;
			}
		}
		if (valueNeedsTypeInfo && GeneratorConfig.summarizeCollectionType && (valueType == null || valueType == Object.class)) {
			Set<Class<?>> conflictedClasses = new HashSet<Class<?>>(5);
			Class<?> commonType = Utils.calculateCommonType(vs.values(), conflictedClasses);
			if (commonType != null && commonType != Object.class && conflictedClasses.size() == 0) {
				valueType = commonType;
			}
		}
		if (compact) {
			// TODO:
			System.out.println("!!!!!!!! Need to compact the map object!");
			//return;
		}
		startObjectBlock(builder, valueType, false, needsWrapping);
		if (keys.length == 0 || GeneratorConfig.preferKeyValueMapFormat && keyType == String.class && Utils.canKeysBeFieldNames(keys)) {
			boolean basicType = isBasicType(valueType);
			boolean singleLine = vs.size() == 1 && basicType;
			boolean multipleLines = vs.size() > 1 || !basicType;
			if (singleLine) {
				builder.append(' ');
			}
			if (multipleLines) {
				builder.append("\r\n");
				increaseIndent();
			}
			if (needsTypeInfo) {
				appendIndents(builder).append("\"class\": \"[map");
				if (keyNeedsTypeInfo || valueNeedsTypeInfo) {
					builder.append(':');
					appendFieldType(builder, keyType, null, false);
					builder.append(',');
					appendFieldType(builder, valueType, null, false);

				}
				builder.append("]\",\r\n");
			}
			for (Object k : keys) {
				//if (vs.size() > 1) builder.append(indents);
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
				String prefix = Utils.wrapAsJSFieldName(k); // keywords.contains(k) ? "\"" + k + "\"" : String.valueOf(k);
				generateFieldValue(builder, null, prefix, null, o, targetValueType, valueParamType, diffValueTypes, false, compact, false);
				//appendLinebreak(builder);
				if (multipleLines) appendLinebreak(builder);
			}
			if (singleLine) builder.append(' ');
			if (multipleLines) {
				decreaseIndent();
				appendIndents(builder);
			}
		} else {
			//builder.append("\r\n");
			//increaseIndent();
			builder.append(' ');
			if (needsTypeInfo) {
				builder.append("\"class\": \"[map");
				if (keyNeedsTypeInfo || valueNeedsTypeInfo) {
					builder.append(':');
					appendFieldType(builder, keyType, null, false);
					builder.append(',');
					appendFieldType(builder, valueType, null, false);

				}
				builder.append("]\", ");
				//builder.append("\"class\": \"[map]\", ");
			}
			StringBuilder valueBuilder = new StringBuilder();
			valueBuilder.append("[ {\r\n");
			increaseIndent();
			for (int i = 0; i < keys.length; i++) {
				Object k = keys[i];
				boolean diffKeyTypes = false;
				Class<?> targetKeyType = k.getClass();
				if (keyType != null && keyType != targetKeyType && !keyType.isInterface()
						&& !Utils.isAbstractClass(keyType)) {
					diffKeyTypes = true;
				}
				if (!diffKeyTypes) targetKeyType = keyType;
				generateFieldValue(valueBuilder, null, "key", null, k, targetKeyType, keyParamType, diffKeyTypes, false, compact, false);
				appendLinebreak(valueBuilder);
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
				generateFieldValue(valueBuilder, null, "value", null, o, targetValueType, valueParamType, diffValueTypes, false, compact, false);
				appendLinebreak(valueBuilder);
				
				if (i != keys.length - 1) {
					decreaseIndent();
					appendIndents(valueBuilder).append("}, {\r\n");
					increaseIndent();
				}
			}
			decreaseIndent();
			appendIndents(valueBuilder).append("} ]");
			assign(builder, "entries", valueBuilder, typeBuilder, true);
			builder.append(' ');
			//builder.append("\r\n");
			//decreaseIndent();
			//appendIndents(builder);
		}
		builder.append("}");
		//endObjectBlock(builder);
	}
	
	@Override
	protected StringBuilder assign(StringBuilder builder, String name, StringBuilder value, StringBuilder typeBuilder, boolean compact) {
		if (name == null || name.length() == 0) {
			if (!compact) {
				int length = value.length();
				if (length == 0) return builder;
				if (length >= 3 && value.substring(0, 3).equals("{\r\n")) {
					// start with no indents
					int builderLength = builder.length();
					if (builderLength >= 2 && builder.substring(builderLength - 2, builderLength).equals("[ ")) {
						return builder.append(value);
					}
					if (builderLength >= 1 && builder.charAt(builderLength - 1) == '[') {
						return builder.append(' ').append(value);
					}
					if (builderLength >= 5 && builder.substring(builderLength - 5, builderLength).equals("\t},\r\n")) {
						builder.delete(builderLength - 2, builderLength);
						return builder.append(' ').append(value);
					}
				}
				if (value.charAt(0) == '\t') {
					int builderLength = builder.length();
					if (builderLength > 0 && builder.charAt(builderLength - 1) != '\n') builder.append("\r\n");
				} else {
					appendIndents(builder);
				}
			}
			return builder.append(value);
		}
		if (!compact) appendIndents(builder);
		return builder.append(name).append(": ").append(value);
	}

}
