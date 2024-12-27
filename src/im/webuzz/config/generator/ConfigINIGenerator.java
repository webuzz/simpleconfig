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
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import im.webuzz.config.Config;
import im.webuzz.config.annotation.ConfigPreferredCodec;
import im.webuzz.config.parser.ConfigFieldProxy;
import im.webuzz.config.util.BytesHelper;
import im.webuzz.config.util.FieldUtils;
import im.webuzz.config.util.TypeUtils;

public class ConfigINIGenerator extends ConfigBaseGenerator {

	// To provide a line of comment, e.g. a field's type
	@Override
	public void startLineComment(StringBuilder builder) {
		builder.append("# ");
	}
	@Override
	public void endLineComment(StringBuilder builder) {
		builder.append("\r\n");
	}
	
	// To provide more information about a type or a field 
	@Override
	public void startBlockComment(StringBuilder builder) {
	}
	@Override
	public StringBuilder addMiddleComment(StringBuilder builder) {
		return builder.append("# ");
	}
	@Override
	public void endBlockComment(StringBuilder builder) {
	}

	// To wrap or separate each configuration class
	@Override
	public void startClassBlock(StringBuilder builder) {
	}
	@Override
	public void endClassBlock(StringBuilder builder) {
	}
	
	@Override
	protected String prefixedField(String prefix, String name) {
		return prefix + "." + name;
	}
	
	@Override
	protected void appendSeparator(StringBuilder builder, boolean compact) {
		builder.append(compact ? ";" : "\r\n");
	}

	// To wrap or separate an object with fields
	@Override
	protected boolean startObjectBlock(StringBuilder builder, Class<?> type, boolean needsTypeInfo, boolean needsWrapping) {
		boolean isAnnotation = Annotation.class.isAssignableFrom(type);
		String tagName = isAnnotation ? "annotation" : "object";
		builder.append('[').append(tagName);
		if (needsTypeInfo) {
			builder.append(':');
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
		}
		builder.append("]\r\n");
		return false; // No needs of appending new separators or line breaks
	}
	@Override
	protected void endObjectBlock(StringBuilder builder, Class<?> type, boolean needsIndents, boolean needsWrapping) {
	}

	@Override
	protected void generateNull(StringBuilder builder, boolean hasNamePrefix) {
		builder.append("[null]");
	}
	
	@Override
	protected void generateEmptyArray(StringBuilder builder, boolean hasNamePrefix) {
		builder.append("[empty]");
	}

	@Override
	protected void generateEmptyObject(StringBuilder builder, boolean hasNamePrefix) {
		builder.append("[empty]");
	}

	@Override
	protected void appendEncodedString(StringBuilder builder, String codecKey, String encoded) {
		builder.append('[').append(codecKey).append(':').append(encoded).append(']');
	}

	@Override
	protected void generateString(StringBuilder builder, String v) {
		if (v.length() == 0) {
			builder.append("[empty]");
			return;
		} 
		builder.append(formatStringForProperties(v));
	}

	public static String formatStringForProperties(String str) {
		return str.replaceAll("\\\\", "\\\\\\\\").replaceAll("\r", "\\\\r").replaceAll("\n", "\\\\n").replaceAll("\t", "\\\\t").trim();
	}

	@Override
	protected boolean generateClass(StringBuilder builder, Class<?> v,
			boolean needsTypeInfo, boolean needsWrapping, boolean compact) {
		if (!needsTypeInfo) {
			builder.append(v.getName());
			return true;
		}
		builder.append("[Class:").append(v.getName()).append(']');
		return true;
	}
	
	@Override
	protected boolean generateEnum(StringBuilder builder, Enum<?> v, Class<?> type,
			boolean needsTypeInfo, boolean needsWrapping, boolean compact) {
		if (!needsTypeInfo) {
			builder.append(v.name());
			return true;
		}			
		if (type != Enum.class) builder.append("[Enum:");
		builder.append(v.getClass().getName()).append('.').append(v.name());
		if (type != Enum.class) builder.append(']');
		return true;
	}

	@Override
	protected void generateBasicData(StringBuilder builder, Object v, Class<?> type,
			boolean needsTypeInfo, boolean needsWrapping, boolean compact) {
		Class<? extends Object> clazz = v.getClass();
		if (needsTypeInfo) builder.append('[').append(clazz.getSimpleName()).append(':');
		if (Character.class == clazz) {
			appendChar(builder, ((Character) v).charValue());
		} else {
			builder.append(v);
		}
		if (needsTypeInfo) builder.append(']');
	}
	
	// Will always end with line break
	@Override
	protected void appendCollection(StringBuilder builder, String name, Object vs, int vsSize,
			StringBuilder typeBuilder, Class<?> type, Type paramType, Class<?> valueType, Type valueParamType, Class<?> componentType,
			boolean forKeys, boolean forValues, int depth, ConfigPreferredCodec[] codecs,
			boolean needsTypeInfo, boolean needsWrapping, boolean compact) {
		if (type == null || type == Object.class) type = vs.getClass();
		Class<?> vsType = vs.getClass();
		String typeStr = TypeUtils.getCollectionTypeName(vsType);
		if (componentType != null && componentType != Object.class && componentType != valueType) {
			// array, use array's class type directly
			needsTypeInfo = true;
			valueType = componentType;
			valueParamType = null;
		}
		Object[] values = getObjectArray(vs, vsSize, vsType, valueType);
		if (needsTypeInfo && summarizeCollectionType && valueType == Object.class && values != null) {
			Set<Class<?>> conflictedClasses = new HashSet<Class<?>>(5);
			Class<?> commonType = TypeUtils.calculateCommonType(values, conflictedClasses);
			if (commonType != null && commonType != Object.class && conflictedClasses.size() == 0) {
				valueType = commonType;
			}
		}
		/*
		if ("localServerName".equals(name)) {
			System.out.println("Debug.");
		}
		//*/
		if (compact) {
			if (typeBuilder != null) typeBuilder.append(typeStr);
			for (int k = 0; k < vsSize; k++) {
				if (k > 0) builder.append(";");
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
			return;
		}
		int oldLength = builder.length();
		builder.append('[').append(typeStr);
		if (needsTypeInfo && valueType != null && valueType != Object.class && valueType != String.class) {
			builder.append(':');
			typeWriter.appendFieldType(builder, valueType, null);
		}
		builder.append("]");
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
			if (values == null) { // primitive array
				builder.append(prefix).append("=");
				appendArrayPrimitive(builder, vs, k, valueType, compact).append("\r\n");
				index++;
				continue;
			}
			// object array
			Object v = values[k];
			boolean diffTypes = v == null ? false : checkTypeDefinition(valueType, v.getClass());
			generateFieldValue(builder, null, prefix, null, v, valueType, valueParamType,
					forKeys, forValues, depth + 1, codecs,
					diffTypes, true, compact, false, false);
			compactWriter.appendLinebreak(builder);
			index++;
		}
	}

	@Override
	protected boolean checkPlainKeys(Class<?> keyType, Object[] keys) {
		return keyType == String.class && FieldUtils.canKeysBePrefixedNames(keys);		
	}

	@Override
	protected void appendMap(StringBuilder builder, String name, Map<Object, Object> vs, Object[] keys,
			StringBuilder typeBuilder, Class<?> keyType, Type keyParamType, Class<?> valueType, Type valueParamType,
			boolean forKeys, boolean forValues, int depth, ConfigPreferredCodec[] codecs,
			boolean mapNeedsTypeInfo, boolean keyNeedsTypeInfo, boolean valueNeedsTypeInfo,
			boolean needsWrapping, boolean compact) {
		/*
		if ("mms".equals(name)) {
			System.out.println("XXX");
		}
		//*/
		if (compact) {
			int size = keys.length;
			for (int i = 0; i < size; i++) {
				Object k = keys[i];
				builder.append(k).append('>');
				appendMapKeyValue(builder, null, null, vs.get(k),
						valueType, valueParamType,
						depth, codecs, compact);
				if (i != size - 1) builder.append(";");
			}
			return;
		}
		builder.append('[');
		typeWriter.appendMapType(builder, keyType, valueType, keyNeedsTypeInfo, valueNeedsTypeInfo);
		builder.append("]\r\n");

		if (supportsDirectKeyValueMode(keys, keyType, depth, codecs)) {
			for (Object k : keys) {
				appendMapKeyValue(builder, name, k, vs.get(k),
						valueType, valueParamType,
						depth, codecs, compact);
				compactWriter.appendLinebreak(builder);
			}
			return;
		}
		// Start entries mode
		int index = startingIndex;
		//String entriesPrefix = name + ".entries";
		//builder.append(entriesPrefix).append("=[]\r\n");
		for (Object k : keys) {
			String kvPrefix = name + "." + index;
			builder.append(kvPrefix).append("=[]\r\n");
			appendMapEntry(builder, kvPrefix, k, vs.get(k),
					keyType, keyParamType, valueType, valueParamType,
					depth, codecs);
			index++;
		}
	}
	
	@Override
	protected StringBuilder assign(StringBuilder builder, String name, StringBuilder value, StringBuilder type, boolean compact) {
		if (name == null || name.length() == 0) return builder.append(value);
		return builder.append(name).append(compact ? '>' : '=').append(value);
	}

	@Override
	public byte[] mergeFields(byte[] originalContent, Class<?> clz, List<Field> fields) {
		byte[] content = originalContent;
		for (Field f : fields) {
			int contentLength = content.length;
			ByteArrayOutputStream baos = new ByteArrayOutputStream(contentLength + fields.size() * 32);
			String name = f.getName();
			byte[] nameBytes = name.getBytes();
			int nameLength = nameBytes.length;
			int lastIdx = 0;
			int startIdx = 0;
			do {
				int idx = BytesHelper.indexOf(content, 0, contentLength, nameBytes, 0, nameLength, startIdx); 
				if (idx == -1) break;
				int nextIdx = idx + nameLength;
				if (checkPrefix(content, idx, clz)) {
					byte next = content[nextIdx];
					if (next == '=' || next == '.' || next == ' ' || next == '\t' || next == ':') {
						if (lastIdx == 0) {
							baos.write(content, lastIdx, idx - lastIdx);
							lastIdx = idx;
						}
						nextIdx++;
						do {
							if (nextIdx == contentLength) break;
						} while (content[nextIdx++] != '\n');
					} 
				}
				startIdx = nextIdx;
			} while (true);
			if (lastIdx == 0) continue; // not matched
			StringBuilder builder = new StringBuilder();
			generateFieldValue(builder, new ConfigFieldProxy(f), name, clz, null, null, null,
					false, false, 0, f.getAnnotationsByType(ConfigPreferredCodec.class),
					false, false, false, true, true);
			if (builder.length() > 0) {
				byte[] bytes = builder.toString().getBytes(Config.configFileEncoding);
				int localStartIdx = 0;
				int localLastIdx = 0;
				int byteLength = bytes.length;
				do {
					int idx = BytesHelper.indexOf(bytes, 0, byteLength, nameBytes, 0, nameLength, localStartIdx); 
					if (idx == -1) break;
					if (checkPrefix(bytes, idx, clz)) {
						localLastIdx = idx;
						break;
					}
					localStartIdx = idx + nameLength;
				} while (true);
				String original = new String(content, lastIdx, startIdx - lastIdx).trim();
				String update = new String(bytes, localLastIdx, byteLength - localLastIdx).trim();
				if (original.equals(update)) continue; // No update!
				/*
				System.out.println("============");
				System.out.println(original);
				System.out.println("=====vs=====");
				System.out.println(update);
				System.out.println("============");
				// */
				try {
					baos.write(bytes, localLastIdx, byteLength - localLastIdx);
					if (bytes[byteLength - 1] != '\n') {
						baos.write("\r\n".getBytes());
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			if (contentLength > startIdx) {
				baos.write(content, startIdx, contentLength - startIdx);
			}
			byte[] newContent = baos.toByteArray();
			//System.out.println(new String(newContent, Config.configFileEncoding));
			content = newContent;
		}
		return content;
	}

	protected boolean checkPrefix(byte[] originalContent, int idx, Class<?> clz) {
		if (idx == 0) return true;
		byte prev = originalContent[idx - 1];
		if (prev == '\n' || prev == ' ' || prev == '\t') return true;
		if (prev == '.') {
			int prevIdx = idx;
			do {
				prevIdx--;
				if (prevIdx <= 0) break;
			} while (originalContent[prevIdx] != '\n');
			String prefix = new String(originalContent, prevIdx, idx - 1).trim();
			if (prefix.equals(Config.getKeyPrefix(clz))) return true;
		}
		return false;
	}
	
}
