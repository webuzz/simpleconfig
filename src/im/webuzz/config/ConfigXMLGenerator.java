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

import static im.webuzz.config.GeneratorConfig.addFieldComment;
import static im.webuzz.config.GeneratorConfig.addTypeComment;
import static im.webuzz.config.GeneratorConfig.skipSimpleTypeComment;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import im.webuzz.config.annotations.ConfigComment;
import im.webuzz.config.security.SecurityKit;

import java.util.Set;

/**
 * Generate configuration default file in JavaScript format.
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
		} else if (length > 0 && builder.charAt(length - 1) == '\t') {
			return builder; // skip adding more indents
		}
		return builder.append(indents);
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
		//increaseIndent();
	}
	@Override
	protected void endObjectBlock(StringBuilder builder, boolean needsWrapping) {
		if (!needsWrapping) return;
		//decreaseIndent();
		appendIndents(builder).append("</object>").append("\r\n");
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
			Type paramType = f.getGenericType();
			if (commentAdded) {
				StringBuilder typeBuilder = new StringBuilder();
				appendFieldType(typeBuilder, type, paramType, true);
				typeBuilder.append("\r\n");
				appendIndents(typeBuilder);
				// Insert field type back into block comment
				builder.insert(builder.length() - 5, typeBuilder);
			} else {
				startLineComment(builder);
				appendFieldType(builder, type, paramType, true);
				endLineComment(builder);
			}
		}
	}
	
	@Override
	protected void generateString(StringBuilder builder, String v, boolean secret) {
		if (v == null) {
			builder.append($null);
		} else if (v.length() == 0) {
			builder.append($emptyString);
		} else if (secret) {
			builder.append("<secret>" + SecurityKit.encrypt(v) + "</secret>");
		} else {
			builder.append(configFormat(v));
		}
	}

	@Override
	protected String configFormat(String str) {
		return str.replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;").replaceAll("\"", "&quot;").trim();
	}
	

	@Override
	protected boolean generateClass(StringBuilder builder, Class<?> v, boolean needsTypeInfo, boolean needsWrapping) {
		if (v == null) {
			builder.append($null);
		} else if (needsTypeInfo) {
			builder.append("<Class>").append(v.getName()).append("</Class>");
		} else {
			builder.append(v.getName());
		}
		return true;
	}

	@Override
	protected void generateBasicData(StringBuilder builder, Object v, Class<?> type, boolean needsTypeInfo, boolean needsWrapping, boolean compact) {
		Class<? extends Object> clazz = v.getClass();
		if (needsTypeInfo || needsWrapping && !compact) {
			String typeName = clazz.getSimpleName();
			builder.append("<").append(typeName).append(">");
		}
		if (Class.class == clazz) {
			builder.append(((Class<?>) v).getName());
		} else if (Character.class == clazz) {
			Character c = (Character) v;
			char ch = c.charValue();
			if (0x20 <= ch && ch <= 0x7e) {
				builder.append(ch);
			} else {
				builder.append("0x").append(Integer.toHexString(ch));
			}
		} else {
			builder.append(v);
		}
		if (needsTypeInfo || needsWrapping && !compact) builder.append("</").append(clazz.getSimpleName()).append(">");
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	protected void appendCollection(StringBuilder builder, Field f, String name, Object vs, int vsSize,
			StringBuilder typeBuilder, Class<?> type, Type paramType, Class<?> valueType, Type valueParamType, Class<?> componentType,
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
		if ("iarrs".equals(name)) {
			System.out.println(1234);
		}
		if (needsWrapping) {
			appendIndents(builder).append('<').append(typeStr);
		}
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
//			boolean singleItem = vsSize == 1;
//			if (singleItem) builder.append(' ');
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
				generateFieldValue(builder, null, null, null, o, valueType, valueParamType, diffTypes, true, compact, false);
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
					wrappingTag = "String";
				} else {
					wrapping =true;
				}
				generateFieldValue(builder, null, wrappingTag,
								null, o, valueType, valueParamType, diffTypes, wrapping, false, false);
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
	protected void appendMap(StringBuilder builder, Field f, String name, Map<Object, Object> vs, Object[] keys,
			StringBuilder typeBuilder, Class<?> keyType, Type keyParamType, Class<?> valueType, Type valueParamType,
			boolean needsTypeInfo, boolean keyNeedsTypeInfo, boolean valueNeedsTypeInfo,
			boolean needsWrapping, boolean compact) {
		if ("mapArrs".equals(name)) {
			System.out.println(12344);
		}
		if (compact) {
			// TODO:
			System.out.println("Continue to generate raw map!!!");
			//return;
			compact = false;
		}
		//startObjectBlock(builder, valueType, false);
		//builder.append("<map");
		if (needsWrapping) {
			appendIndents(builder).append("<map");
		}
		if (keys.length == 0 || GeneratorConfig.preferKeyValueMapFormat
				&& keyType == String.class && Utils.canKeysBeFieldNames(keys)) {
			boolean basicType = isBasicType(valueType);
			//boolean singleLine = vs.size() == 1 && basicType;
			boolean multipleLines = vs.size() > 1 || !basicType;
//			if (singleLine) {
//				builder.append(' ');
//			}
			if (needsTypeInfo) {
				/*
				builder.append("<map class=\"");
//				appendIndents(builder).append("\"class\": \"[map");
//				if (keyNeedsTypeInfo || valueNeedsTypeInfo) {
//					builder.append(':');
					appendFieldType(builder, keyType, null, false);
					builder.append(',');
					appendFieldType(builder, valueType, null, false);
					builder.append('\"');
//				}
//				builder.append("]\",\r\n");
// */
					//builder.append('>');
				if (needsWrapping) {
					builder.append(" class=\"map:");
					appendFieldType(builder, keyType, null, false);
					builder.append(',');
					appendFieldType(builder, valueType, null, false);
					builder.append('\"');
				} else if (typeBuilder != null) {
						typeBuilder.append("map:");
						appendFieldType(typeBuilder, keyType, null, false);
						typeBuilder.append(',');
						appendFieldType(typeBuilder, valueType, null, false);
					}
			}
			if (needsWrapping) builder.append('>');

			if (multipleLines) {
				//builder.append("\r\n");
				increaseIndent();
			}
			if ("msas".equals(name)) {
				System.out.println("Dxxxx");
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
				String prefix = String.valueOf(k); // keywords.contains(k) ? "\"" + k + "\"" : String.valueOf(k);
				generateFieldValue(builder, null, prefix, null, o, targetValueType, valueParamType, diffValueTypes, 
						false, //targetValueType.isArray(),
						compact, false);
				//generateTypeObject(f, builder, prefix, o, targetValueType, valueParamType, diffValueTypes, compact);
				//appendLinebreak(builder);
				if (multipleLines) appendLinebreak(builder);
			}
			//if (singleLine) builder.append(' ');
			if (multipleLines) {
				decreaseIndent();
			}
			if (needsTypeInfo) {
				//appendIndents(builder);
				//builder.append("</map>");
			}
		} else {
			//builder.append("\r\n");
			//increaseIndent();
			//builder.append(' ');
			if (needsTypeInfo) {
//				builder.append("\"class\": \"[map");
				if (keyNeedsTypeInfo || valueNeedsTypeInfo) {
					/*
					builder.append("<map class=\"");
//					builder.append(':');
					appendFieldType(builder, keyType, null, false);
					builder.append(',');
					appendFieldType(builder, valueType, null, false);
					builder.append('\"');
					builder.append(">\r\n");
					//*/
					if (needsWrapping) {
						builder.append(" class=\"map:");
						appendFieldType(builder, keyType, null, false);
						builder.append(',');
						appendFieldType(builder, valueType, null, false);
						builder.append('\"');
					} else if (typeBuilder != null) {
						typeBuilder.append("map:");
						appendFieldType(typeBuilder, keyType, null, false);
						typeBuilder.append(',');
						appendFieldType(typeBuilder, valueType, null, false);
					}
				}
				//builder.append("]\", ");
				//builder.append("\"class\": \"[map]\", ");
			}
			if (needsWrapping) builder.append('>');
			StringBuilder entriesBuilder = builder; //new StringBuilder();
//			valueBuilder.append("[ {\r\n");
			increaseIndent();
			for (int i = 0; i < keys.length; i++) {
				StringBuilder valueBuilder = new StringBuilder();
//				valueBuilder.append("[ {\r\n");
				increaseIndent();
				Object k = keys[i];
				boolean diffKeyTypes = false;
				Class<?> targetKeyType = k.getClass();
				if (keyType != null && keyType != targetKeyType && !keyType.isInterface()
						&& !Utils.isAbstractClass(keyType)) {
					diffKeyTypes = true;
				}
				if (!diffKeyTypes) targetKeyType = keyType;
				generateFieldValue(valueBuilder, null, "key", null, k, targetKeyType, keyParamType, diffKeyTypes, false, compact, false);
				//appendLinebreak(valueBuilder);
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
				//appendLinebreak(valueBuilder);
				
				decreaseIndent();
				//appendIndents(entriesBuilder);
				//decreaseIndent();
				assign(entriesBuilder, "entry", valueBuilder, null, false);
				//increaseIndent();
//				if (i != keys.length - 1) {
//					decreaseIndent();
//					appendIndents(valueBuilder).append("}, {\r\n");
//					increaseIndent();
//				}
			}
			//assign(builder, "entries", entriesBuilder, null, false);
			decreaseIndent();
//			appendIndents(valueBuilder).append("} ]");
			//builder.append(' ');
			//builder.append("\r\n");
			//decreaseIndent();
			if (needsTypeInfo) {
//				builder.append("\"class\": \"[map");
				if (keyNeedsTypeInfo || valueNeedsTypeInfo) {
					//appendIndents(builder);
					//builder.append("</map>");
				}
			}
		}
		if (needsWrapping) appendIndents(builder).append("</map>");
		//endObjectBlock(builder);
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
