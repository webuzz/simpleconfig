package im.webuzz.config.common;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;

import im.webuzz.config.annotation.ConfigEnum;
import im.webuzz.config.annotation.ConfigKeyPrefix;
import im.webuzz.config.annotation.ConfigLength;
import im.webuzz.config.annotation.ConfigNotEmpty;
import im.webuzz.config.annotation.ConfigNotNull;
import im.webuzz.config.annotation.ConfigNumberEnum;
import im.webuzz.config.annotation.ConfigPattern;
import im.webuzz.config.annotation.ConfigPreferredCodec;
import im.webuzz.config.annotation.ConfigRange;
import im.webuzz.config.annotation.ConfigSince;

public class AnnotationWriter {
	
	private CommentClassWriter classWriter;
	
	private static StringBuilder trimEndingDot0(StringBuilder builder) {
		int length = builder.length();
		if (builder.substring(length - 2, length).equals(".0")) {
			builder.delete(length - 2, length);
		}
		return builder;
	}

	public void appendAnnotation(StringBuilder builder, Annotation ann, boolean multiple) {
		builder.append('@').append(ann.annotationType().getSimpleName());
		if (ann instanceof ConfigNotNull || ann instanceof ConfigNotEmpty || ann instanceof ConfigLength
				|| ann instanceof ConfigEnum || ann instanceof ConfigPattern || ann instanceof ConfigPreferredCodec
				|| ann instanceof ConfigRange || ann instanceof ConfigNumberEnum
				|| ann instanceof ConfigSince || ann instanceof ConfigKeyPrefix) {
			builder.append('(');
			if (ann instanceof ConfigNotNull) {
				ConfigNotNull a = (ConfigNotNull) ann;
				if (a.depth() > 0 || multiple) {
					builder.append("depth = ").append(a.depth());
				}
			} else if (ann instanceof ConfigNotEmpty) {
				ConfigNotEmpty a = (ConfigNotEmpty) ann;
				if (a.depth() > 0 || multiple) {
					builder.append("depth = ").append(a.depth());
				}
			} else if (ann instanceof ConfigLength) {
				ConfigLength a = (ConfigLength) ann;
				if (a.min() > 0) {
					builder.append("min = ").append(a.min());
				}
				if (a.max() != Integer.MAX_VALUE) {
					if (builder.charAt(builder.length() - 1) != '(') {
						builder.append(", ");
					}
					builder.append("max = ").append(a.max());
				}
				if (a.depth() > 0 || multiple) {
					if (builder.charAt(builder.length() - 1) != '(') {
						builder.append(", ");
					}
					builder.append("depth = ").append(a.depth());
				}
			} else if (ann instanceof ConfigEnum) {
				ConfigEnum a = (ConfigEnum) ann;
				String[] values = a.value();
				if (values != null) {
					if (values.length > 1) builder.append('{');
					boolean first = true;
					for (String value : values) {
						if (!first) builder.append(", ");
						if (value == null) {
							builder.append("null");
						} else if (value.length() == 0) {
							builder.append("\"\"");
						} else {
							builder.append('\"').append(StringUtils.formatAsString(value)).append('\"');
						}
						first = false;
					}
					if (values.length > 1) builder.append('}');
				}
			} else if (ann instanceof ConfigPattern) {
				ConfigPattern a = (ConfigPattern) ann;
				String value = a.value();
				if (value != null && value.length() > 0) {
					builder.append('\"').append(StringUtils.formatAsString(value)).append('\"');
				}
			} else if (ann instanceof ConfigPreferredCodec) {
				ConfigPreferredCodec a = (ConfigPreferredCodec) ann;
				String[] values = a.value();
				if (values != null && values.length > 0) {
					if (a.mapKey() || a.mapValue() || a.depth() >= 0 || multiple) {
						builder.append("value = ");
					}
					if (values.length > 1) builder.append('{');
					boolean first = true;
					for (String value : values) {
						if (!first) builder.append(", ");
						if (value == null) {
							builder.append("null");
						} else if (value.length() == 0) {
							builder.append("\"\"");
						} else {
							builder.append('\"').append(StringUtils.formatAsString(value)).append('\"');
						}
						first = false;
					}
					if (values.length > 1) builder.append('}');
				}
				if (a.mapKey()) {
					if (builder.charAt(builder.length() - 1) != '(') {
						builder.append(", ");
					}
					builder.append("key = true");
				}
				if (a.mapValue()) {
					if (builder.charAt(builder.length() - 1) != '(') {
						builder.append(", ");
					}
					builder.append("value = true");
				}
				if (a.depth() >= 0 || multiple) {
					if (builder.charAt(builder.length() - 1) != '(') {
						builder.append(", ");
					}
					builder.append("depth = ").append(a.depth());
				}
			} else if (ann instanceof ConfigRange) {
				ConfigRange a = (ConfigRange) ann;
				if (a.min() > 0) {
					builder.append("min = ").append(a.min());
					trimEndingDot0(builder);
				}
				if (a.max() != Integer.MAX_VALUE) {
					if (builder.charAt(builder.length() - 1) != '(') {
						builder.append(", ");
					}
					builder.append("max = ").append(a.max());
					trimEndingDot0(builder);
				}
			} else if (ann instanceof ConfigNumberEnum) {
				ConfigNumberEnum a = (ConfigNumberEnum) ann;
				double[] values = a.value();
				if (values != null) {
					if (values.length > 1) builder.append('{');
					boolean first = true;
					for (double value : values) {
						if (!first) builder.append(", ");
						builder.append(value);
						trimEndingDot0(builder);
						first = false;
					}
					if (values.length > 1) builder.append('}');
				}
			} else if (ann instanceof ConfigSince) {
				ConfigSince a = (ConfigSince) ann;
				String value = a.value();
				if (value != null && value.length() > 0) {
					builder.append('\"').append(StringUtils.formatAsString(value)).append('\"');
				}
			} else if (ann instanceof ConfigKeyPrefix) {
				ConfigKeyPrefix a = (ConfigKeyPrefix) ann;
				String value = a.value();
				if (value != null && value.length() > 0) {
					builder.append('\"').append(StringUtils.formatAsString(value)).append('\"');
				}
			}			
			int length = builder.length();
			if (builder.charAt(length - 1) == '(') {
				builder.delete(length - 1, length);
			} else {
				builder.append(')');
			}
		}
	}


	public void appendFieldObject(StringBuilder builder, Field f, Object obj) {
		if (obj == null) {
			builder.append("null");
			return;
		}
		if (obj instanceof String) {
			String s = (String) obj;
			builder.append('\"').append(s).append('\"');
			return;
		}
		Class<? extends Object> type = obj.getClass();
		if (obj instanceof Number) {
			builder.append('[').append(type.getSimpleName()).append(':').append(((Number) obj).doubleValue());
			trimEndingDot0(builder).append(']');
			return;
		}
		builder.append('[');
		if (classWriter == null) classWriter = new CommentClassWriter();
		classWriter.appendFieldType(builder, type, null);
		builder.append(":...]");
	}

	public void appendFieldNumber(StringBuilder builder, Field f, double num) {
		builder.append('[').append(f.getType().getName()).append(':').append(num);
		trimEndingDot0(builder);
		builder.append(']');
	}

}
