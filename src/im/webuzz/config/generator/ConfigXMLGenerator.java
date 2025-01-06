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

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import im.webuzz.config.annotation.ConfigPreferredCodec;
import im.webuzz.config.common.TypeUtils;
import im.webuzz.config.Config;
import im.webuzz.config.annotation.ConfigComment;

/**
 * Generate configuration default file in XML format.
 * 
 * @author zhourenjian
 *
 */
public class ConfigXMLGenerator extends ConfigBaseGenerator {

	class XMLCompactWriter extends CompactWriter {

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
				builder.append("\r\n");
			}
		}

	}
	
	class XMLCommentWriter extends CommentWriter {

		public XMLCommentWriter(CommentWrapper wrapper) {
			super(wrapper);
		}

		public int appendConfigComment(StringBuilder builder, ConfigComment configAnn) {
			if (configAnn == null) return 0;
			String[] comments = configAnn.value();
			if (comments == null || comments.length == 0) return 0;
			for (int i = 0; i < comments.length; i++) {
				if (i > 0) compactWriter.appendIndents(builder);
				builder.append(comments[i]);
			}
			return comments.length;
		}
		
		@Override
		protected int appendFieldAnnotation(StringBuilder builder, Annotation[] anns) {
			for (Annotation ann : anns) {
				if (builder.length() > 0) compactWriter.appendIndents(builder);
				annWriter.appendAnnotation(builder, ann, anns.length > 1);
			}
			return anns.length;
		}

		@Override
		protected void generateTypeComment(StringBuilder builder, Class<?> clz) {
			StringBuilder typeCommentBuilder = new StringBuilder();
			int addedLines = commentWriter.appendConfigComment(typeCommentBuilder, clz.getAnnotation(ConfigComment.class));
			if (addedLines > 1) {
				startBlockComment(builder);
				builder.append(typeCommentBuilder);
				endBlockComment(builder); // If ended, line break should be appended.
			} else if (addedLines > 0) {
				startLineComment(builder);
				builder.append(typeCommentBuilder);
				endLineComment(builder);
			}
		}
		
		@Override
		protected void generateFieldComment(StringBuilder builder, Field f, boolean topConfigClass) {
			if (!topConfigClass && !commentGeneratedFields.add(f)) return; // already generated
			int addedLines = 0;
			StringBuilder commentBuilder = null;
			if (addFieldComment) {
				commentBuilder = new StringBuilder();
				addedLines += appendConfigComment(commentBuilder, f.getAnnotation(ConfigComment.class));
			}
			StringBuilder annBuilder = null;
			if (addFieldAnnotationComment) {
				annBuilder = new StringBuilder();
				addedLines += appendAllFieldAnnotations(annBuilder, f);
			}
			StringBuilder typeBuilder = null;
			if (addFieldTypeComment) {
				Class<?> type = f.getType();
				if (!skipSimpleTypeComment
						|| (type != int.class && type != String.class && type != boolean.class)) {
					typeBuilder = new StringBuilder();
					Type paramType = f.getGenericType();
					commentClassWriter.appendFieldType(typeBuilder, type, paramType);
					addedLines += 1;
				}
			}
			if (addedLines == 0) return;
			if (addedLines == 1) {
				startLineComment(builder);
				if (commentBuilder != null && commentBuilder.length() > 0) {
					builder.append(commentBuilder);
				}
				if (annBuilder != null && annBuilder.length() > 0) {
					builder.append(annBuilder);
				}
				if (typeBuilder != null && typeBuilder.length() > 0) {
					builder.append(typeBuilder); //.toString().replace('<', '{').replace('>', '}'));
				}
				endLineComment(builder);
				return;
			}
			startBlockComment(builder);
			if (commentBuilder != null && commentBuilder.length() > 0) {
				compactWriter.appendIndents(builder).append(commentBuilder);
			}
			if (annBuilder != null && annBuilder.length() > 0) {
				compactWriter.appendIndents(builder).append(annBuilder);
			}
			if (typeBuilder != null && typeBuilder.length() > 0) {
				compactWriter.appendIndents(builder).append(typeBuilder); //.toString().replace('<', '{').replace('>', '}'));
			}
			endBlockComment(builder);
		}

	}
	
	public ConfigXMLGenerator() {
		super();
		commentWriter = new XMLCommentWriter(this);
		compactWriter = new XMLCompactWriter();
	}

	// To provide a line of comment, e.g. a field's type
	@Override
	public void startLineComment(StringBuilder builder) {
		compactWriter.appendIndents(builder).append("<!-- ");
	}
	@Override
	public void endLineComment(StringBuilder builder) {
		builder.append(" -->\r\n");
	}
	
	// To provide more information about a type or a field 
	@Override
	public void startBlockComment(StringBuilder builder) {
		compactWriter.appendIndents(builder).append("<!--\r\n");
	}
	@Override
	public StringBuilder addMiddleComment(StringBuilder builder) {
		return compactWriter.appendIndents(builder); //.append("\t");
	}
	@Override
	public void endBlockComment(StringBuilder builder) {
		compactWriter.appendIndents(builder).append("-->\r\n");
	}

	// To wrap or separate each configuration class
	@Override
	public void startClassBlock(StringBuilder builder) {
		builder.append("<?xml version=\"1.0\" encoding=\"").append(Config.configFileEncoding.name()).append("\"?>\r\n");
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
	protected void appendSeparator(StringBuilder builder, boolean compact) {
		if (!compact) builder.append("\r\n");
	}

	// To wrap or separate an object with fields
	@Override
	protected boolean startObjectBlock(StringBuilder builder, Class<?> type, boolean needsTypeInfo, boolean needsWrapping) {
		if (!needsWrapping) return false;
		compactWriter.appendIndents(builder);
		boolean isAnnotation = Annotation.class.isAssignableFrom(type);
		String tagName = isAnnotation ? "annotation" : "object";
		builder.append('<').append(tagName);
		if (needsTypeInfo) {
			builder.append(" class=\"");
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
			builder.append("\"");
		}
		builder.append(">\r\n");
		return false; // No needs of new line breaks
	}
	@Override
	protected void endObjectBlock(StringBuilder builder, Class<?> type, boolean needsIndents, boolean needsWrapping) {
		if (!needsWrapping) return;
		if (needsIndents) {
			compactWriter.appendIndents(builder);
		} else {
			// empty annotation or object block
			int length = builder.length();
			if (builder.substring(length - 3).equals(">\r\n")) {
				builder.delete(length - 2, length);
			}
		}
		String tagName = (Annotation.class.isAssignableFrom(type)) ? "annotation" : "object";
		builder.append("</").append(tagName).append(">\r\n");
	}


	@Override
	protected void generateNull(StringBuilder builder, boolean hasNamePrefix) {
		builder.append("<null />");
	}

	@Override
	protected void generateEmptyArray(StringBuilder builder, boolean hasNamePrefix) {
		if (!hasNamePrefix) builder.append("<empty />");
	}

	@Override
	protected void generateEmptyObject(StringBuilder builder, boolean hasNamePrefix) {
		if (!hasNamePrefix) builder.append("<empty />");
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
			// TODO: "\"\"" ?
			builder.append("<empty />");
			return;
		}
		builder.append(configFormat(v));
	}

	private String configFormat(String str) {
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

	@Override
	protected void appendCollection(StringBuilder builder, String name, Object vs, int vsSize,
			StringBuilder typeBuilder, Class<?> type, Type paramType, Class<?> valueType, Type valueParamType, Class<?> componentType,
			boolean forKeys, boolean forValues, int depth, ConfigPreferredCodec[] codecs,
			boolean needsTypeInfo, boolean needsWrapping, boolean compact) {
		if (type == null || type == Object.class) type = vs.getClass();
		Class<?> vsType = vs.getClass();
		String typeStr = TypeUtils.getCollectionTypeName(vsType);
		Object[] values = getObjectArray(vs, vsSize, vsType, valueType);
		if (needsWrapping) compactWriter.appendIndents(builder).append('<').append(typeStr);
		if (compact) {
			if (needsTypeInfo) {
				//checkIndents(builder).append('<').append(typeStr).append('>');
				if (needsWrapping) {
					// TODO:
				}
				else if (typeBuilder != null) typeBuilder.append(typeStr);
			}
			if (needsWrapping) builder.append('>');
			for (int k = 0; k < vsSize; k++) {
				if (values == null) {
					if (k > 0) builder.append(";");
					appendArrayPrimitive(builder, vs, k, valueType, compact);
					continue;
				}
				Object v = values[k];
				if (k > 0 && builder.charAt(builder.length() - 1) != '>') builder.append(";");
				boolean diffTypes = v == null ? false : checkTypeDefinition(valueType, v.getClass());
				generateFieldValue(builder, null, null, null, v, valueType, valueParamType,
						forKeys, forValues, depth + 1, codecs,
						diffTypes, true, compact, false, false);
			}
			if (needsWrapping) builder.append("</").append(typeStr).append('>');
			return;
		}
		//checkIndents(builder);
		if (needsTypeInfo) {
			//builder.append("{ \"class\": \"");
			if (needsTypeInfo && values != null && (valueType == null
					|| GeneratorConfig.summarizeCollectionType && valueType == Object.class)) {
				Set<Class<?>> conflictedClasses = new HashSet<Class<?>>(5);
				Class<?> commonType = TypeUtils.calculateCommonType(values, conflictedClasses);
				if (commonType != null && commonType != Object.class && conflictedClasses.size() == 0) {
					valueType = commonType;
				}
			}
			if (valueType == null || TypeUtils.isObjectOrObjectArray(valueType) || valueType == String.class
					|| valueType.isInterface() || TypeUtils.isAbstractClass(valueType)) {
				//builder.append('<').append(typeStr);
				//builder.append(typeStr);
				if (needsWrapping) {
					// TODO:
				} else if (typeBuilder != null) typeBuilder.append(typeStr);
			} else {
				if (needsWrapping) {
					// TODO:
					builder.append(" class=\"").append(typeStr).append(':');
					typeWriter.appendFieldType(typeBuilder, valueType, null);
					builder.append('\"');
				} else if (typeBuilder != null) {
					typeBuilder.append(typeStr).append(':');
					typeWriter.appendFieldType(typeBuilder, valueType, null);
				}
			}
			//builder.append("\", value: ");
			//builder.append('>');
		}
		if (needsWrapping) builder.append('>');
		if (valueType == null) valueType = Object.class;
		boolean basicType = TypeUtils.isBasicType(valueType);
		boolean multipleLines = vsSize >= 1 || !basicType;
		if (multipleLines) compactWriter.increaseIndent();
		boolean first = true;
		for (int k = 0; k < vsSize; k++) {
			if (values == null) {
				appendArrayPrimitive(builder, vs, k, valueType, compact);
			} else {
				Object v = values[k];
				if (first && multipleLines && (basicType
						|| ((valueType == Object.class || TypeUtils.isAbstractClass(valueType))
								&& v != null && TypeUtils.isBasicType(v.getClass())))) {
					//appendIndents(builder);
					first = false;
				}
				boolean diffTypes = v == null ? false : checkTypeDefinition(valueType, v.getClass());
				boolean wrapping = needsWrapping;
				String wrappingTag = null;
				if (valueType == String.class) {
					StringBuilder valueBuilder = new StringBuilder();
					if (!encode(valueBuilder, v, forKeys, forValues, depth + 1, codecs)) {
						wrappingTag = "String";
					}
				} else {
					wrapping =true;
				}
				generateFieldValue(builder, null, wrappingTag,
								null, v, valueType, valueParamType,
								forKeys, forValues, depth + 1, codecs,
								diffTypes, wrapping, false, false, false);
			}
		}
		if (multipleLines) compactWriter.decreaseIndent();
		if (needsWrapping) compactWriter.appendIndents(builder).append("</").append(typeStr).append('>');
	}	


	@Override
	protected StringBuilder appendArrayPrimitive(StringBuilder builder, Object vs, int k, Class<?> compType, boolean compact) {
		if (!compact) compactWriter.appendIndents(builder).append("<").append(compType.getName()).append('>');
		super.appendArrayPrimitive(builder, vs, k, compType, compact);
		if (!compact) builder.append("</").append(compType.getName()).append('>');
		return builder;
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
		if ("strMap".equals(name)) {
			System.out.println(12344);
		}
		//*/
		if (compact) {
			// TODO:
			compact = false; // Compact mode for map is not supported yet.
		}
		if (needsWrapping) compactWriter.appendIndents(builder).append("<map");
		if (needsTypeInfo || (keyNeedsTypeInfo || valueNeedsTypeInfo) || needsToAvoidCodecKeys(keys)) {
			if (needsWrapping) {
				builder.append(" class=\"");
				typeWriter.appendMapType(builder, keyType, valueType, keyNeedsTypeInfo, valueNeedsTypeInfo);
				builder.append('\"');
			} else if (typeBuilder != null) {
				typeWriter.appendMapType(typeBuilder, keyType, valueType, keyNeedsTypeInfo, valueNeedsTypeInfo);
			}
		}
		if (needsWrapping) builder.append('>');
		if (supportsDirectKeyValueMode(keys, keyType, depth, codecs)) {
			compactWriter.increaseIndent();
			for (Object k : keys) {
				appendMapKeyValue(builder, null, k, vs.get(k),
						valueType, valueParamType,
						depth, codecs, compact);
				compactWriter.appendLinebreak(builder);
			}
			compactWriter.decreaseIndent();
			if (needsWrapping) compactWriter.appendIndents(builder).append("</map>");
			return;
		}
		// Start entries mode
		compactWriter.increaseIndent();
		for (Object k : keys) {
			StringBuilder valueBuilder = new StringBuilder();
			compactWriter.increaseIndent();
			appendMapEntry(valueBuilder, null, k, vs.get(k),
					keyType, keyParamType, valueType, valueParamType,
					depth, codecs);
			compactWriter.decreaseIndent();
			assign(builder, "entry", valueBuilder, null, false);
		}
		compactWriter.decreaseIndent();
		if (needsWrapping) compactWriter.appendIndents(builder).append("</map>");
	}
	
	@Override
	protected StringBuilder assign(StringBuilder builder, String name, StringBuilder value, StringBuilder typeBuilder, boolean compact) {
		if (compact) {
			if (name == null || name.length() == 0) return builder.append(value);
			if (GeneratorConfig.shortenEmptyObject && value.length() == 0) {
				return builder.append('<').append(name).append(" />");
			}
			if (GeneratorConfig.shortenNullObject && "<null />".equals(value.toString())) {
				return builder.append('<').append(name).append(" null=\"true\" />");
			}
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
				compactWriter.appendIndents(builder);
			}
			return builder.append(value);
		}
		compactWriter.appendIndents(builder);
		builder.append('<').append(name);
		if (typeBuilder != null && typeBuilder.length() > 0) {
			builder.append(" class=\"").append(typeBuilder).append('\"');
		}
		if (GeneratorConfig.shortenEmptyObject && value.length() == 0) {
			return builder.append(" />");
		}
		if (GeneratorConfig.shortenNullObject && "<null />".equals(value.toString())) {
			return builder.append(" null=\"true\" />");
		}
		builder.append('>');
		boolean wrapping = length > 0 && (value.charAt(0) == '\t' || value.charAt(length - 1) == '\n');
		if (wrapping) builder.append("\r\n");
		builder.append(value);
		if (wrapping) compactWriter.appendIndents(builder);
		builder.append("</").append(name).append('>');
		return builder;
	}

	@Override
	protected byte[] getFieldPrefixedBytes(Field f) {
		return ("<" + f.getName()).getBytes();
	}

	@Override
	protected int checkSuffix(byte[] content, int contentLength, int nextIdx, int nameLength,
			Field field, Field nextField, boolean generated, boolean found) {
		//byte keyChar = '>';
		//byte lastChar = '<';
		byte next = content[nextIdx];
		if (next != '>' && next != ' ' && next != '\t' && next != '/' && next != '\r' && next != '\n') return -1;
		//int startIdx = nextIdx;
		boolean gap = false;
	    while (nextIdx < contentLength && (next == ' ' || next == '\t' || next == '\r' || next == '\n')) {
	    	next = content[++nextIdx];
	    	gap = true;
	    }
	    if (!gap && next != '/' && next != '>') return -1;
	    if (next == '/') {
	    	// search '>'
		    while (nextIdx < contentLength - 1 && next != '>') {
		    	next = content[++nextIdx];
		    }
		    if (next != '>') return -1;
		    return nextIdx + 1;
	    }
	    if (next != '>') {
	    	// search '>'
		    while (nextIdx < contentLength - 1 && next != '>') {
		    	next = content[++nextIdx];
		    }
		    if (next != '>') return -1;
		    int lastIdx = nextIdx + 1;
		    while (nextIdx > 0 && (next == ' ' || next == '\t' || next == '\r' || next == '\n')) {
		    	next = content[--nextIdx];
		    }
	    	// backward search '/'
		    if (next == '/') return lastIdx;
	    }
	    if (nextIdx >= contentLength - 1) return contentLength; 
    	nextIdx++;
    	next = content[nextIdx];
    	// continue to search closing tag
    	if (nextField != null) {
			// search backward from the next field, ignoring all blank lines and comments
			String nextName = "<" + nextField.getName();
			byte[] nextNameBytes = nextName.getBytes();
			int nextNameLength = nextNameBytes.length;
			int lastIdx = -1;
			int startIdx = nextIdx;
			do {
				int idx = BytesHelper.indexOf(content, 0, contentLength, nextNameBytes, 0, nextNameLength, startIdx); 
				if (idx == -1) break;
				int prevPrefix = checkPrefix(content, contentLength, idx, nextNameLength, field);
				if (prevPrefix != -1) {
					lastIdx = prevPrefix;
					break;
				}
				startIdx = idx + nextNameLength;
			} while (true);
			if (lastIdx != -1) {
				// matched next field, to search backward ">"
				int idx = lastIdx;
				while (idx > 0) {
					byte prev = content[--idx];
					if (prev == '>') {
						int prevIdx = handleCommentTag(content, contentLength, idx);
						if (prevIdx == -1) return idx + 1;
						idx = prevIdx;
					}
				}
			} else if (found) { // already found one in previous search
				return -1;
			}
    	}
    	if (generated) {
    		int prevIdx = contentLength;
    		byte prev = content[--prevIdx];
    	    while (prevIdx >= nextIdx && prev != '>') {
    	    	prev = content[--prevIdx];
    	    }
    		return prevIdx + 1;
    	}
    	// !!! Should parse paired < and >
	    while (nextIdx < contentLength - 1 && next != '>') {
	    	next = content[++nextIdx];
	    }
	    if (next != '>') return -1;
    	return nextIdx + 1;
	}
	
	@Override
	protected int checkPrefix(byte[] content, int contentLength, int idx, int nameLength, Field field) {
		if (idx == 0) return idx;
		byte prev = content[--idx];
		if (prev == '\n') return idx;
		if (prev == '>') {
			int prevIdx = handleCommentTag(content, contentLength, idx);
			if (prevIdx == -1) return idx;
			idx = prevIdx;
			prev = content[idx];
		}
		if (prev == ' ' || prev == '\t') {
			while (idx > 0) {
				prev = content[--idx];
				if (prev == '\n') return idx;
				if (prev == '>') {
					int prevIdx = handleCommentTag(content, contentLength, idx);
					if (prevIdx == -1) return idx;
					idx = prevIdx;
					continue;
				}
				if (prev != ' ' && prev != '\t') return -1;
			}
			if (idx == 0) return idx;
		}
		return -1;
	}

	// Helper method to handle ',' processing
	private int handleCommentTag(byte[] content, int contentLength, int idx) {
		if (!(idx >= 2 && content[idx - 1] == '-' && content[idx - 2] == '-')) return -1; // not a comment node
		// ending with -->, search backward for <!--
		idx -= 2;
		while (idx > 0) {
			byte prev = content[--idx];
			if (prev == '-' && idx >= 3 && content[idx - 1] == '-'
					&& content[idx - 2] == '!' && content[idx - 3] == '<') {
				return idx - 3;
			}
		}
		return 0; // failed to match starting <!--
	}

}
