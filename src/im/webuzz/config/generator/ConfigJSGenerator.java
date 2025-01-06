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
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import im.webuzz.config.annotation.ConfigPreferredCodec;
import im.webuzz.config.common.TypeUtils;

/**
 * Generate configuration default file in JavaScript format.
 * 
 * @author zhourenjian
 *
 */
public class ConfigJSGenerator extends ConfigBaseGenerator {

	class JSCompactWriter extends CompactWriter {
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
		protected void appendLinebreak(StringBuilder builder) {
			int length = builder.length();
			if (length < 2 || builder.charAt(length - 1) != '\n') {
				builder.append(",\r\n");
			}
		}
	}
	
	public ConfigJSGenerator() {
		super();
		compactWriter = new JSCompactWriter();
	}

	@Override
	public void startLineComment(StringBuilder builder) {
		compactWriter.appendIndents(builder).append("// ");
	}

	@Override
	public void endLineComment(StringBuilder builder) {
		builder.append("\r\n");
	}

	@Override
	public void startBlockComment(StringBuilder builder) {
		compactWriter.appendIndents(builder).append("/**\r\n");
	}

	@Override
	public StringBuilder addMiddleComment(StringBuilder builder) {
		return compactWriter.appendIndents(builder).append(" * ");
	}

	@Override
	public void endBlockComment(StringBuilder builder) {
		compactWriter.appendIndents(builder).append(" */\r\n");
	}

	@Override
	public void startClassBlock(StringBuilder builder) {
		builder.append("$config = {\r\n");
	}

	@Override
	public void endClassBlock(StringBuilder builder) {
		builder.append("}\r\n");
	}

	@Override
	protected String prefixedField(String prefix, String name) {
		return FieldUtils.wrapAsJSFieldName(name);
	}
	
	@Override
	protected void appendSeparator(StringBuilder builder, boolean compact) {
		builder.append(compact ? ", " : ",\r\n");
	}

	@Override
	protected boolean startObjectBlock(StringBuilder builder, Class<?> type, boolean needsTypeInfo, boolean needsWrapping) {
		int length = builder.length();
		if (length > 4 && builder.substring(length - 4).equals("},\r\n")) {
			builder.insert(length - 2, " {");
		} else if (length > 4 && builder.substring(length - 3).equals("[\r\n")) {
			builder.insert(length - 2, " {");
		} else {
			if (length > 1 && builder.charAt(length - 1) == '\n' && compactWriter.indents.length() > 0) {
				builder.append(compactWriter.indents.substring(0, compactWriter.indents.length() - 1));
			}
			builder.append("{");
		}
		if (needsTypeInfo) {
			//appendIndents(builder);
			builder.append(" \"class\": \""); //object:");
			boolean isAnnotation = Annotation.class.isAssignableFrom(type);
			//String tagName = isAnnotation ? "annotation" : "object";
			//builder.append(tagName).append(':');
			if (isAnnotation) {
				StringBuilder typeBuilder = new StringBuilder();
				typeWriter.appendFieldType(typeBuilder, type, null);
				String typeName = typeBuilder.toString();
				String pkg = "im.webuzz.config.annotation.";
				if (typeName.startsWith(pkg + "Config")) {
					builder.append('@').append(typeName.substring(pkg.length()));
				} else {
					builder.append(typeName);
				}
			} else {
				typeWriter.appendFieldType(builder, type, null);
			}
			builder.append('\"');
			//builder.append(",");
			return true; // need to append separator
		}
		return false;
	}

	@Override
	protected void endObjectBlock(StringBuilder builder, Class<?> type, boolean needsIndents, boolean needsWrapping) {
		if (needsIndents) {
			compactWriter.appendIndents(builder);
		} else {
			builder.append(' ');
		}
		builder.append('}');
	}

	@Override
	protected void generateNull(StringBuilder builder, boolean hasNamePrefix) {
		builder.append("null");
	}

	@Override
	protected void generateEmptyArray(StringBuilder builder, boolean hasNamePrefix) {
		builder.append("[]");
	}

	@Override
	protected void generateEmptyObject(StringBuilder builder, boolean hasNamePrefix) {
		builder.append("{}");
	}

	@Override
	protected void appendEncodedString(StringBuilder builder, String codecKey, String encoded) {
		builder.append("{ ").append(codecKey).append(": \"").append(encoded).append("\" }");
	}

	@Override
	protected void generateString(StringBuilder builder, String v) {
		if (v.length() == 0) {
			builder.append("\"\""); //$emptyString;
			return;
		}
		builder.append('\"').append(formatStringForJS(v)).append('\"');
	}

	public static String formatStringForJS(String str) {
		return str.replaceAll("\\\\", "\\\\\\\\").replaceAll("\r", "\\\\r").replaceAll("\n", "\\\\n").replaceAll("\t", "\\\\t").trim();
	}

	@Override
	protected boolean generateClass(StringBuilder builder, Class<?> v,
			boolean needsTypeInfo, boolean needsWrapping, boolean compact) {
		if (!needsTypeInfo) {
			builder.append("\"").append(v.getName()).append("\"");
			return true;
		}
		builder.append("{ Class: \"").append(v.getName()).append("\" }");
		return true;
	}
	
	@Override
	protected boolean generateEnum(StringBuilder builder, Enum<?> v, Class<?> type,
			boolean needsTypeInfo, boolean needsWrapping, boolean compact) {
		if (!needsTypeInfo) {
			builder.append('\"').append(v.name()).append('\"');
			return true;
		}
		if (type != Enum.class) builder.append("{ Enum: ");
		builder.append('\"').append(v.getClass().getName()).append('.').append(v.name()).append('\"');
		if (type != Enum.class) builder.append(" }");
		return true;
	}

	@Override
	protected void generateBasicData(StringBuilder builder, Object v, Class<?> type,
			boolean needsTypeInfo, boolean needsWrapping, boolean compact) {
		Class<? extends Object> clazz = v.getClass();
		if (needsTypeInfo) builder.append("{ ").append(clazz.getSimpleName()).append(": ");
		if (BigInteger.class == clazz || BigDecimal.class == clazz) {
			builder.append("\"").append(v).append("\"");
		} else if (Character.class == clazz) {
			appendChar(builder, ((Character) v).charValue());
		} else {
			builder.append(v);
		}
		if (needsTypeInfo) builder.append(" }");
	}
	
	@Override
	protected void appendCollection(StringBuilder builder, String name, Object vs, int vsSize,
			StringBuilder typeBuilder, Class<?> type, Type paramType, Class<?> valueType, Type valueParamType, Class<?> componentType,
			boolean forKeys, boolean forValues, int depth, ConfigPreferredCodec[] codecs,
			boolean needsTypeInfo, boolean needsWrapping, boolean compact) {
		/*
		if ("localServerName".equals(name)) {
			System.out.println("Debug");
		} // */
		compactWriter.checkIndents(builder);
		Class<?> vsType = vs.getClass();
		if (valueType == null) valueType = Object.class;
		Object[] values = getObjectArray(vs, vsSize, vsType, valueType);
		if (needsTypeInfo) {
			if (valueType == null
					|| GeneratorConfig.summarizeCollectionType && valueType == Object.class) {
				Set<Class<?>> conflictedClasses = new HashSet<Class<?>>(5);
				Class<?> commonType = TypeUtils.calculateCommonType(values, conflictedClasses);
				if (commonType != null && commonType != Object.class && conflictedClasses.size() == 0) {
					valueType = commonType;
				}
			}
			if (type != Object.class && (valueType == null || TypeUtils.isObjectOrObjectArray(valueType) || valueType == String.class
					|| valueType.isInterface() || TypeUtils.isAbstractClass(valueType))) {
				needsTypeInfo = false;
			} else {
				builder.append("{ \"class\": \"").append(TypeUtils.getCollectionTypeName(vsType));
				// TODO
				if (valueType == null || TypeUtils.isObjectOrObjectArray(valueType) || valueType == String.class
						|| valueType.isInterface() || TypeUtils.isAbstractClass(valueType)) {
				} else {
					builder.append(':');
					typeWriter.appendFieldType(builder, valueType, null);
				}
				builder.append("\", value: ");
			}
		}
		if (compact) {
			builder.append("[");
			if (vsSize >= 1) builder.append(' ');
			for (int k = 0; k < vsSize; k++) {
				if (k > 0) builder.append(", ");
				if (values == null) {
					appendArrayPrimitive(builder, vs, k, valueType, compact);
					continue;
				}
				Object v = values[k];
				boolean diffTypes = v == null ? false : checkTypeDefinition(valueType, v.getClass());
				generateFieldValue(builder, null, null, null, v, valueType, valueParamType,
						forKeys, forValues, depth + 1, codecs,
						diffTypes, true, compact, false, false);
			}
			if (vsSize >= 1) builder.append(' ');
			builder.append("]");
			if (needsTypeInfo) builder.append(" }");
			return;
		}
		builder.append("[");
		boolean basicType = TypeUtils.isBasicType(valueType);
		int size = vsSize;
		boolean singleLine = size == 1 && basicType;
		boolean multipleLines = size > 1 || !basicType;
		boolean moreIndents = basicType || valueType.isPrimitive()
				|| Object.class == valueType
				|| Annotation.class.isAssignableFrom(valueType)
				|| valueType.isArray()
				|| List.class.isAssignableFrom(valueType)
				|| Set.class.isAssignableFrom(valueType)
				|| valueType.isEnum() || valueType == Enum.class;
		if (singleLine) builder.append(' ');
		if (multipleLines) {
			if (moreIndents) {
				builder.append("\r\n");
				compactWriter.increaseIndent();
			} else {
				builder.append(' ');
			}
		}
		
		for (int k = 0; k < vsSize; k++) {
			if (values == null) {
				if (!singleLine) compactWriter.appendIndents(builder);
				appendArrayPrimitive(builder, vs, k, valueType, compact);
			} else {
				Object v = values[k];
				boolean diffTypes = v == null ? false : checkTypeDefinition(valueType, v.getClass());
				generateFieldValue(builder, null, null, null,
						v, valueType, valueParamType,
						forKeys, forValues, depth + 1, codecs,
						diffTypes, true, singleLine, false, false);
			}
			if (singleLine && size > 1 && k != size - 1) builder.append(", ");
			if (multipleLines) compactWriter.appendLinebreak(builder);
		}
		if (singleLine) builder.append(' ');
		if (multipleLines) {
			if (moreIndents) {
				compactWriter.decreaseIndent();
				compactWriter.appendIndents(builder);
			}
		}
		int length = builder.length();
		if (!moreIndents && length > 4 && builder.substring(length - 4, length).equals("},\r\n")) {
			builder.delete(length - 3, length).append(needsTypeInfo ? " ] }" : " ]");
			//builder.insert(length - 3, needsTypeInfo ? " ] }" : " ]");
		} else {
			builder.append("]");
			if (needsTypeInfo) {
				builder.append(" }");
			}
		}
	}

	@Override
	protected boolean checkPlainKeys(Class<?> keyType, Object[] keys) {
		return keyType == String.class && FieldUtils.canKeysBeFieldNames(keys);		
	}

	@Override
	protected void appendMap(StringBuilder builder, String name, Map<Object, Object> vs, Object[] keys,
			StringBuilder typeBuilder, Class<?> keyType, Type keyParamType, Class<?> valueType, Type valueParamType,
			boolean forKeys, boolean forValues, int depth, ConfigPreferredCodec[] codecs,
			boolean needsTypeInfo, boolean keyNeedsTypeInfo, boolean valueNeedsTypeInfo,
			boolean needsWrapping, boolean compact) {
		/*
		if ("mapArrs".equals(name)) {
			System.out.println("xxx mapAas");
		} //*/
		// treating map as an object, starting with "{" and ending with "}"
		startObjectBlock(builder, valueType, false, needsWrapping); // needsTypeInfo = false, in this invocation, always return false
		if (needsTypeInfo || needsToAvoidCodecKeys(keys)) {
			// For cases need to avoid codec keys, we will get { "class": "[map]", aes: "AES Algorithm" }
			builder.append(" \"class\": \"");
			typeWriter.appendMapType(builder, keyType, valueType, keyNeedsTypeInfo, valueNeedsTypeInfo);
			builder.append("\",");
		}
		if (supportsDirectKeyValueMode(keys, keyType, depth, codecs)) {
			builder.append(compact ? " " : "\r\n");
			if (!compact) compactWriter.increaseIndent();
			int size = keys.length; // size > 0,  this method is invoked only if the map is not empty
			for (int i = 0; i < size; i++) {
				Object k = keys[i];
				appendMapKeyValue(builder, null, k, vs.get(k),
						valueType, valueParamType,
						depth, codecs, compact);
				// For non-compact mode, always adding "," at the end, so more key-value can be added easily later
				if (!compact || i != size - 1) appendSeparator(builder, compact);
			}
			if (!compact) compactWriter.decreaseIndent();
			endObjectBlock(builder, valueType, !compact, needsWrapping); // #endObjectBlock will prepare indents
			return;
		}
		
		// start entries mode
		builder.append(' ');
		StringBuilder valueBuilder = new StringBuilder();
		valueBuilder.append("[ {\r\n");
		compactWriter.increaseIndent();
		for (int i = 0; i < keys.length; i++) {
			Object k = keys[i];
			appendMapEntry(valueBuilder, null, k, vs.get(k),
					keyType, keyParamType, valueType, valueParamType,
					depth, codecs);
			if (i != keys.length - 1) {
				compactWriter.decreaseIndent();
				compactWriter.appendIndents(valueBuilder).append("}, {\r\n");
				compactWriter.increaseIndent();
			}
		}
		compactWriter.decreaseIndent();
		compactWriter.appendIndents(valueBuilder).append("} ]");
		assign(builder, "entries", valueBuilder, typeBuilder, true);
		endObjectBlock(builder, valueType, false, needsWrapping);
	}
	
	@Override
	protected StringBuilder assign(StringBuilder builder, String name, StringBuilder value, StringBuilder typeBuilder, boolean compact) {
		/*
		if ("localServerName".equals(name)) {
			System.out.println("Hello");
		} //*/
		if (name != null && name.length() > 0) {
			if (!compact) compactWriter.appendIndents(builder);
			return builder.append(name).append(": ").append(value);
		}
		
		// name is empty, inside complicate object
		if (compact) return builder.append(value);
		int length = value.length();
		if (length == 0) return builder;
		if (length >= 3 && value.substring(0, 3).equals("{\r\n")
				|| length > 10 && value.substring(0, 10).equals("{ \"class\":")) {
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
			compactWriter.appendIndents(builder);
		}
		return builder.append(value);
	}

	@Override
	protected int checkSuffix(byte[] content, int contentLength, int nextIdx, int nameLength, Field field, Field nextField, boolean generated, boolean found) {
		boolean quoted = false;
		byte next = content[nextIdx];
		while (next != ':') {
			nextIdx++;
			if (nextIdx >= contentLength) return -1;
			next = content[nextIdx];
			if (next == '\r' || next == '\n') return -1;
			if (next != ' ' && next != '\t' && next != '\"' && next != '\'') return -1;
			if (next == '\"' || next == '\'') {
				if (quoted) return -1;
				int prevIdx = nextIdx - 1 - nameLength - 1;
				if (prevIdx < 0) return -1;
				if (content[prevIdx] != next) return -1; // quotes are not matched
				quoted = true;
			}
		}
		if (nextField != null) {
			// search backward from the next field, ignoring all blank lines and comments
			String nextName = nextField.getName();
			byte[] nextNameBytes = nextName.getBytes();
			int nextNameLength = nextNameBytes.length;
			int startIdx = -1;
			int searchIdx = nextIdx;
			do {
				int idx = BytesHelper.indexOf(content, 0, contentLength, nextNameBytes, 0, nextNameLength, searchIdx); 
				if (idx == -1) break;
				int prevPrefix = checkPrefix(content, contentLength, idx, nextNameLength, field);
				if (prevPrefix != -1) {
					startIdx = prevPrefix;
					break;
				}
				searchIdx = idx + nextNameLength;
			} while (true);
			if (startIdx != -1) {
				// matched next field, to search backward ","
				int idx = startIdx;
				while (idx > 0) {
					byte prev = content[--idx];
					if (prev == ',' && handleComma(content, contentLength, idx) != -1) {
						return idx;
					}
				}
			} else if (found) { // already found one in previous search
				return -1;
			}
		} else if (generated) {
			return contentLength;
			/*
			int idx = contentLength;
			do {
				if (idx <= nextIdx) return contentLength;
			} while (content[--idx] != lastChar);
			return idx + 1;
			//*/
		}
		// !!! need to parse the JavaScript, pairing \', \", [, ], {, }, (, ), //, /*, */, 
		do {
			if (nextIdx >= contentLength - 1) return contentLength;
		} while (content[++nextIdx] != ',');
		return nextIdx;
	}
	
	@Override
	protected int checkPrefix(byte[] content, int contentLength, int idx, int nameLength, Field field) {
		if (idx == 0) return idx;
		byte prev = content[--idx];
		if (prev == '\"' || prev == '\'') { // e.g. "name": "John",
			int followIdx = idx + 1 + nameLength + 1;
			if (followIdx >= contentLength) return -1;
			if (content[followIdx] != prev) return -1; // quotes are not matched
			prev = content[--idx];
		}
		if (prev == '\n') return idx;
		if (prev == ',' && handleComma(content, contentLength, idx) != -1) return idx;
		if (prev == ' ' || prev == '\t') {
			while (idx > 0) {
				prev = content[--idx];
				if (prev == '\n') return idx;
				if (prev == ',' && handleComma(content, contentLength, idx) != -1) return idx;
				if (prev != ' ' && prev != '\t') return -1;
			}
			if (idx == 0) return idx;
		}
		return -1;
	}

	// Helper method to handle ',' processing
	private int handleComma(byte[] content, int contentLength, int idx) {
	    while (idx > 0) {
	        byte prev = content[--idx];
	        if (prev == '\n') {
	            if (isCommentLine(content, idx + 1, contentLength)) return -1;
	            return idx; // Valid prefix
	        }
	    }
	    // Check start of content
	    if (idx == 0 && isCommentLine(content, 0, contentLength)) return -1;
	    return idx; // Valid prefix
	}

	// Helper method to check if a line starts with a comment
	private boolean isCommentLine(byte[] content, int startIdx, int contentLength) {
	    // Skip leading spaces or tabs
	    while (startIdx < contentLength && (content[startIdx] == ' ' || content[startIdx] == '\t')) {
	        startIdx++;
	    }
	    // Check if it starts with "//" or "/*"
	    if (startIdx + 1 >= contentLength) return false; // Not enough characters for a comment
	    return content[startIdx] == '*' // the middle " * ####" block comment line 
	    		|| (content[startIdx] == '/' && (content[startIdx + 1] == '/' || content[startIdx + 1] == '*'));
	}

}
