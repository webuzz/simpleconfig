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

package im.webuzz.config;

import static im.webuzz.config.GeneratorConfig.addFieldComment;
import static im.webuzz.config.GeneratorConfig.addTypeComment;
import static im.webuzz.config.GeneratorConfig.skipSimpleTypeComment;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import im.webuzz.config.annotations.ConfigCodec;
import im.webuzz.config.annotations.ConfigComment;

import java.util.Set;

/**
 * Generate configuration default file in XML format.
 * 
 * @author zhourenjian
 *
 */
public class ConfigXMLGenerator extends ConfigINIGenerator {

	public ConfigXMLGenerator() {
		$null = "<null />";
		$object = "object";
		$array = "array";
		$map = "map";
		$set = "set";
		$list = "list";
		$emptyObject = "<empty />";
		$emptyArray = "<empty />";
		$emptyString = "<empty />";
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

	// To provide a line of comment, e.g. a field's type
	@Override
	protected void startLineComment(StringBuilder builder) {
		appendIndents(builder).append("<!-- ");
	}
	@Override
	protected void endLineComment(StringBuilder builder) {
		builder.append(" -->\r\n");
	}
	
	// To provide more information about a type or a field 
	@Override
	protected void startBlockComment(StringBuilder builder) {
		appendIndents(builder).append("<!--\r\n");
	}
	@Override
	protected StringBuilder addMiddleComment(StringBuilder builder) {
		return appendIndents(builder); //.append("\t");
	}
	@Override
	protected void endBlockComment(StringBuilder builder) {
		appendIndents(builder).append("-->\r\n");
	}

	// To wrap or separate each configuration class
	@Override
	public void startClassBlock(StringBuilder builder) {
		builder.append("<config>\r\n");
	}
	@Override
	public void endClassBlock(StringBuilder builder) {
		builder.append("</config>");
	}
	
	@Override
	protected String prefixedField(String prefix, String name) {
		return name;
	}
	@Override
	protected void appendLinebreak(StringBuilder builder) {
		int length = builder.length();
		if (length < 2 || builder.charAt(length - 1) != '\n') {
			builder.append("\r\n");
		}
	}
	
	// To wrap or separate an object with fields
	@Override
	protected void startObjectBlock(StringBuilder builder, Class<?> type, boolean needsTypeInfo, boolean needsWrapping) {
		if (!needsWrapping) return;
		appendIndents(builder);
		if (needsTypeInfo) {
			builder.append("<object class=\"");
			appendFieldType(builder, type, null, false);
			builder.append("\"");
		} else {
			builder.append("<object");
		}
		builder.append(">\r\n");
	}
	@Override
	protected void endObjectBlock(StringBuilder builder, boolean needsIndents, boolean needsWrapping) {
		if (!needsWrapping) return;
		if (needsIndents) appendIndents(builder);
		builder.append("</object>\r\n");
	}

	@Override
	protected int appendFieldAnnotation(StringBuilder builder, Annotation[] anns) {
		for (Annotation ann : anns) {
			ConfigValidator.appendAnnotation(builder, ann, anns.length > 1);
			appendIndents(builder);
		}
		return anns.length;
	}

	@Override
	protected void generateFieldComment(StringBuilder builder, Field f, boolean topConfigClass) {
		if (!commentGeneratedFields.add(f)) return; // already generated
		boolean commentAdded = false;
		if (addFieldComment) {
			commentAdded = appendConfigComment(builder, f.getAnnotation(ConfigComment.class));
		}
		if (addTypeComment) {
			Class<?> type = f.getType();
			if (skipSimpleTypeComment
					&& (type == int.class || type == String.class || type == boolean.class)) {
				return;
			}
			StringBuilder annBuilder = new StringBuilder();
			int annCount = appendAllFieldAnnotations(annBuilder, f);
			Type paramType = f.getGenericType();
			if (commentAdded) { 
				StringBuilder typeBuilder = new StringBuilder();
				if (annCount > 0) typeBuilder.append(annBuilder);
				appendFieldType(typeBuilder, type, paramType, true);
				typeBuilder.append("\r\n");
				appendIndents(typeBuilder);
				// Insert field type back into block comment
				builder.insert(builder.length() - 5, typeBuilder);
			} else if (annCount > 0) {
				startBlockComment(builder);
				appendIndents(builder);
				builder.append(annBuilder);
				appendFieldType(builder, type, paramType, true);
				endBlockComment(builder);
			} else {
				startLineComment(builder);
				appendFieldType(builder, type, paramType, true);
				endLineComment(builder);
			}
		}
	}
	
	@Override
	protected void appendEncodedString(StringBuilder builder, String codecKey, String encoded) {
		builder.append('<').append(codecKey).append('>')
				.append(encoded)
				.append("</").append(codecKey).append('>');
	}

	@Override
	protected void generateString(StringBuilder builder, String v) {
		if (v.length() == 0) {
			builder.append($emptyString);
			return;
		}
		builder.append(configFormat(v));
	}

	@Override
	protected String configFormat(String str) {
		return str.replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;").replaceAll("\"", "&quot;").trim();
	}
	
	@Override
	protected boolean generateClass(StringBuilder builder, Class<?> v,
			boolean needsTypeInfo, boolean needsWrapping, boolean compact) {
		if (needsTypeInfo || needsWrapping && !compact) {
			builder.append("<Class>").append(v.getName()).append("</Class>");
		} else {
			builder.append(v.getName());
		}
		return true;
	}
	
	@Override
	protected boolean generateEnum(StringBuilder builder, Enum<?> v, Class<?> type,
			boolean needsTypeInfo, boolean needsWrapping, boolean compact) {
		if (needsTypeInfo || needsWrapping && !compact) {
			boolean needsTag = type != Enum.class || needsWrapping && !compact;
			if (needsTag) builder.append("<Enum>");
			if (needsTypeInfo) builder.append(v.getClass().getName()).append('.');
			builder.append(v.name());
			if (needsTag) builder.append("</Enum>");
		} else {
			builder.append(v.name());
		}
		return true;
	}

	@Override
	protected void generateBasicData(StringBuilder builder, Object v, Class<?> type,
			boolean needsTypeInfo, boolean needsWrapping, boolean compact) {
		Class<? extends Object> clazz = v.getClass();
		String typeName = clazz.getSimpleName();
		boolean needsTag = needsTypeInfo || needsWrapping && !compact;
		if (needsTag) builder.append('<').append(typeName).append('>');
		if (Character.class == clazz) {
			appendChar(builder, ((Character) v).charValue());
		} else {
			builder.append(v);
		}
		if (needsTag) builder.append("</").append(typeName).append('>');
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	protected void appendCollection(StringBuilder builder, Field f, String name, Object vs, int vsSize,
			StringBuilder typeBuilder, Class<?> type, Type paramType, Class<?> valueType, Type valueParamType, Class<?> componentType,
			boolean forKeys, boolean forValues, int depth, ConfigCodec[] codecs,
			boolean needsTypeInfo, boolean needsWrapping, boolean compact) {
		if (type == null || type == Object.class) type = vs.getClass();
		String typeStr;
		if (List.class.isAssignableFrom(type)) {
			typeStr = $list;
		} else if (Set.class.isAssignableFrom(type)) {
			typeStr = $set;
		} else {
			typeStr = $array;
		}
		if ("iarrs".equals(name)) {
			System.out.println(1234);
		}
		if (needsWrapping) appendIndents(builder).append('<').append(typeStr);
		Class<?> vsType = vs.getClass();
		if (compact) {
			if (needsTypeInfo) {
				//checkIndents(builder).append('<').append(typeStr).append('>');
				if (needsWrapping) {
					// TODO:
				}
				else if (typeBuilder != null) typeBuilder.append(typeStr);
			}
			if (needsWrapping) builder.append('>');
			if (vsType.isArray() && valueType.isPrimitive()) {
				for (int k = 0; k < vsSize; k++) {
					if (k > 0) builder.append(";");
					appendArrayPrimitive(builder, vs, k, valueType, compact);
				}
				if (needsWrapping) builder.append("</").append(typeStr).append('>');
				return;
			}
			int size = vsSize;
			Object[] values = null;
			if (List.class.isAssignableFrom(vsType) || Set.class.isAssignableFrom(vsType)) {
				values = ((Collection) vs).toArray(new Object[size]);
			} else if (vsType.isArray()) {
				if (!valueType.isPrimitive()) {
					values = (Object[]) vs;
				}
			}

			for (int k = 0; k < values.length; k++) {
				Object o = values[k];
				if (k > 0 && builder.charAt(builder.length() - 1) != '>') builder.append(";");
				//Class<?> targetType = null;
				//boolean diffTypes = o != null && valueType != (targetType = o.getClass());
				boolean diffTypes = false;
				if (o != null) {
					Class<?> targetType = o.getClass();
					if (valueType != null && valueType != targetType) {
						if (List.class == valueType
								|| Set.class == valueType
								|| Map.class == valueType) {
							// These 3 collection types will be initialized to specific instances
							// List => ArrayList
							// Set => SynchronizedSet
							// Map => ConcurrentHashMap
						} else { // if (valueType.isInterface() || Utils.isAbstractClass(valueType)) {
							diffTypes = true;
						}
					}
				}
//				if (o != null) {
//					targetType = o.getClass();
//					if (valueType != null && (valueType != targetType || valueType.isInterface()
//							|| Utils.isAbstractClass(valueType))) {
//						diffTypes = true;
//					}
//				}
//				if (!diffTypes) targetType = valueType;
//				generateFieldValue(builder, null, null, null, o, targetType, valueParamType, diffTypes, true, compact, false);
				generateFieldValue(builder, null, null, null, o, valueType, valueParamType,
						forKeys, forValues, depth + 1, codecs,
						diffTypes, true, compact, false);
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
			//if (singleItem) builder.append(' ');
			//builder.append("]");
			if (needsWrapping) builder.append("</").append(typeStr).append('>');
			//appendLinebreak(builder);
			return;
		}
		//checkIndents(builder);
		if (needsTypeInfo) {
//			if ("anyList".equals(name)) {
//				System.out.println("Debug[[");
//			}
//			String typeStr;
//			if (List.class.isAssignableFrom(type)) {
//				typeStr = "[list]"; //$list;
//			} else if (Set.class.isAssignableFrom(type)) {
//				typeStr = "[set]"; //$set;
//			} else {
//				typeStr = "[array]"; //$array;
//			}
			//builder.append("{ \"class\": \"");
			if (needsTypeInfo && (valueType == null
					|| GeneratorConfig.summarizeCollectionType && valueType == Object.class)) {
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
			if (valueType == null || Utils.isObjectOrObjectArray(valueType) || valueType == String.class
					|| valueType.isInterface() || Utils.isAbstractClass(valueType)) {
				//builder.append('<').append(typeStr);
				//builder.append(typeStr);
				if (needsWrapping) {
					// TODO:
				} else if (typeBuilder != null) typeBuilder.append(typeStr);
			} else {
				/*
				builder.append('<').append(typeStr);
				builder.append(" class=\"");
				//builder.append(typeStr.substring(0, typeStr.length() - 1)).append(':');
				//builder.append("[array:");
				appendFieldType(builder, valueType, null, false);
				builder.append('\"');
				//*/
				if (needsWrapping) {
					// TODO:
					builder.append(" class=\"").append(typeStr).append(':');
					appendFieldType(typeBuilder, valueType, null, false);
					builder.append('\"');
				} else if (typeBuilder != null) {
					typeBuilder.append(typeStr).append(':');
					appendFieldType(typeBuilder, valueType, null, false);
				}
			}
			//builder.append("\", value: ");
			//builder.append('>');
		}
		if (needsWrapping) builder.append('>');
		//builder.append("[");
		if (valueType == null) valueType = Object.class;
		boolean basicType = isBasicType(valueType);
		int size = vsSize;
		Object[] values = null;
		if (List.class.isAssignableFrom(vsType) || Set.class.isAssignableFrom(vsType)) {
			values = ((Collection) vs).toArray(new Object[size]);
		} else if (vsType.isArray()) {
			if (!valueType.isPrimitive()) {
				values = (Object[]) vs;
			}
		}
		boolean multipleLines = size >= 1 || !basicType;
		if (multipleLines) increaseIndent();
		boolean first = true;
		for (int k = 0; k < vsSize; k++) {
			if (values == null) {
				appendArrayPrimitive(builder, vs, k, valueType, compact);
			} else {
				Object o = values[k];
				if (first && multipleLines && (basicType
						|| ((valueType == Object.class || Utils.isAbstractClass(valueType))
								&& o != null && isBasicType(o.getClass())))) {
					//appendIndents(builder);
					first = false;
				}
				//Class<?> targetType = null;
				boolean diffTypes = false;
				if (o != null) {
					Class<?> targetType = o.getClass();
					if (valueType != null && valueType != targetType) {
						if (List.class == valueType
								|| Set.class == valueType
								|| Map.class == valueType) {
							// These 3 collection types will be initialized to specific instances
							// List => ArrayList
							// Set => SynchronizedSet
							// Map => ConcurrentHashMap
						} else { // if (valueType.isInterface() || Utils.isAbstractClass(valueType)) {
							diffTypes = true;
						}
					}
				}
//				if (o != null) {
//					targetType = o.getClass();
//					if (valueType != null) {
//						if (valueType == List.class || valueType == Map.class || valueType == Set.class) {
//							
//						}
//						if (valueType != targetType && !valueType.isInterface()
//								&& !Utils.isAbstractClass(valueType)) {
//							diffTypes = true;
//						}
//					}
//				}
				//if (!diffTypes) targetType = valueType;
				boolean wrapping = needsWrapping;
				String wrappingTag = null;
				if (valueType == String.class) {
					StringBuilder valueBuilder = new StringBuilder();
					if (!encode(valueBuilder, o, forKeys, forValues, depth + 1, codecs)) {
						wrappingTag = "String";
					}
				} else {
					wrapping =true;
				}
				generateFieldValue(builder, null, wrappingTag,
								null, o, valueType, valueParamType,
								forKeys, forValues, depth + 1, codecs,
								diffTypes, wrapping, false, false);
			}
		}
		if (multipleLines) decreaseIndent();
		if (needsWrapping) appendIndents(builder).append("</").append(typeStr).append('>');
	}	


	@Override
	protected StringBuilder appendArrayPrimitive(StringBuilder builder, Object vs, int k, Class<?> compType, boolean compact) {
		if (!compact) appendIndents(builder).append("<").append(compType.getName()).append('>');
		super.appendArrayPrimitive(builder, vs, k, compType, compact);
		if (!compact) builder.append("</").append(compType.getName()).append('>');
		return builder;
	}

	@Override
	protected boolean checkPlainKeys(Class<?> keyType, Object[] keys) {
		return keyType == String.class && Utils.canKeysBeFieldNames(keys);		
	}

	@Override
	protected void appendMap(StringBuilder builder, Field f, String name, Map<Object, Object> vs, Object[] keys,
			StringBuilder typeBuilder, Class<?> keyType, Type keyParamType, Class<?> valueType, Type valueParamType,
			boolean forKeys, boolean forValues, int depth, ConfigCodec[] codecs,
			boolean needsTypeInfo, boolean keyNeedsTypeInfo, boolean valueNeedsTypeInfo,
			boolean needsWrapping, boolean compact) {
		if ("strMap".equals(name)) {
			System.out.println(12344);
		}
		if (compact) {
			// TODO:
			compact = false; // Compact mode for map is not supported yet.
		}
		if (needsWrapping) appendIndents(builder).append("<map");
		if (needsTypeInfo || (keyNeedsTypeInfo || valueNeedsTypeInfo) || needsToAvoidCodecKeys(keys)) {
			if (needsWrapping) {
				builder.append(" class=\"");
				appendMapType(builder, keyType, valueType, keyNeedsTypeInfo, valueNeedsTypeInfo);
				builder.append('\"');
			} else if (typeBuilder != null) {
				appendMapType(typeBuilder, keyType, valueType, keyNeedsTypeInfo, valueNeedsTypeInfo);
			}
		}
		if (needsWrapping) builder.append('>');
		if (supportsDirectKeyValueMode(keys, keyType, depth, codecs)) {
			increaseIndent();
			for (Object k : keys) {
				appendMapKeyValue(builder, null, k, vs.get(k),
						valueType, valueParamType,
						depth, codecs, compact);
				appendLinebreak(builder);
			}
			decreaseIndent();
			if (needsWrapping) appendIndents(builder).append("</map>");
			return;
		}
		// Start entries mode
		increaseIndent();
		for (Object k : keys) {
			StringBuilder valueBuilder = new StringBuilder();
			increaseIndent();
			appendMapEntry(valueBuilder, null, k, vs.get(k),
					keyType, keyParamType, valueType, valueParamType,
					depth, codecs);
			decreaseIndent();
			assign(builder, "entry", valueBuilder, null, false);
		}
		decreaseIndent();
		if (needsWrapping) appendIndents(builder).append("</map>");
	}
	
	@Override
	protected StringBuilder assign(StringBuilder builder, String name, StringBuilder value, StringBuilder typeBuilder, boolean compact) {
		if (compact) {
			if (name == null || name.length() == 0) return builder.append(value);
			return builder.append('<').append(name).append('>')
					.append(value)
					.append("</").append(name).append('>');
		}
		int length = value.length();
		if (name == null || name.length() == 0) {
			if (length == 0) return builder;
			if (value.charAt(0) == '\t') {
				int builderLength = builder.length();
				if (builderLength > 0 && builder.charAt(builderLength - 1) != '\n') builder.append("\r\n");
			} else {
				appendIndents(builder);
			}
			return builder.append(value);
		}
		appendIndents(builder);
		builder.append('<').append(name);
		if (typeBuilder != null && typeBuilder.length() > 0) {
			builder.append(" class=\"").append(typeBuilder).append('\"');
		}
		builder.append('>');
		boolean wrapping = length > 0 && (value.charAt(0) == '\t' || value.charAt(length - 1) == '\n');
		if (wrapping) builder.append("\r\n");
		builder.append(value);
		if (wrapping) appendIndents(builder);
		builder.append("</").append(name).append('>');
		return builder;
	}

}
