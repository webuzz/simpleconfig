package im.webuzz.config;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import im.webuzz.config.annotations.ConfigSecret;

public class ConfigValidator {

	private static ConfigINIGenerator generator = new ConfigINIGenerator();
	
	private static StringBuilder trimEndingDot0(StringBuilder builder) {
		int length = builder.length();
		if (builder.substring(length - 2, length).equals(".0")) {
			builder.delete(length - 2, length);
		}
		return builder;
	}

	private static void appendObject(StringBuilder builder, Object obj) {
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
		generator.appendFieldType(builder, type, null, true);
		builder.append(":...]");
//		if (type.isArray()) {
//			/*
//			Class<?> compType = type.getComponentType();
//			if (compType.isPrimitive()) {
//				//builder.append("[array:...]");
//			} else {
//				builder.append("[array:...]");
//			}
//			//*/
//			builder.append('[');
//			generator.appendFieldType(builder, type, null, true);
//			builder.append(":...]");
//			return;
//		}
//		if (obj instanceof List) {
//			builder.append("[list:...]");
//			return;
//		}
//		if (obj instanceof Set) {
//			builder.append("[set:...]");
//			return;
//		}
//		if (obj instanceof Map) {
//			builder.append("[map:...]");
//			return;
//		}
//		builder.append('[').append(type.getSimpleName()).append(":...]");
	}

	private static void appendRange(StringBuilder builder, double min, double max) {
		builder.append('[').append(min);
		trimEndingDot0(builder).append(", ").append(max);
		trimEndingDot0(builder).append(']');
	}

	private static void formatExpectsAnnotation(StringBuilder expectBuilder, String description, Annotation ann) {
		expectBuilder.append("expecting ").append(description).append(' ');
		boolean noGap = true;
		Class<? extends Annotation> annotationType = ann.annotationType();
		if (ann instanceof ConfigLength) {
			ConfigLength len = (ConfigLength) ann;
			int min = len.min();
			int max = len.max();
			if (min == max) {
				expectBuilder.append("= ").append(min);
			} else {
				appendRange(expectBuilder, min, max);
			}
		} else if (ann instanceof ConfigRange) {
			ConfigRange range = (ConfigRange) ann;
			appendRange(expectBuilder, range.min(), range.max());
		} else if (ann instanceof ConfigEnum) {
			String[] values = ((ConfigEnum) ann).value();
			expectBuilder.append('(');
			int count = 0;
			for (String v : values) {
				if (count > 0) expectBuilder.append(", ");
				if (v == null) expectBuilder.append("null");
				else expectBuilder.append('\"').append(v).append('\"');
				count++;
				if (count >= 5) {
					if (count != values.length) {
						expectBuilder.append(", ...");
					}
					break;
				}
			}
			expectBuilder.append(')');
		} else if (ann instanceof ConfigNumberEnum) {
			double[] values = ((ConfigNumberEnum) ann).value();
			expectBuilder.append('(');
			int count = 0;
			for (double v : values) {
				if (count > 0) expectBuilder.append(", ");
				expectBuilder.append(v);
				trimEndingDot0(expectBuilder);
				count++;
				if (count >= 5) {
					if (count != values.length) {
						expectBuilder.append(", ...");
					}
					break;
				}
			}
			expectBuilder.append(')');
		} else if (ann instanceof ConfigPattern) {
			expectBuilder.append('\"').append(((ConfigPattern) ann).value()).append('\"');
		} else {
			noGap = false;
		}
		if (noGap) expectBuilder.append(' ');
		expectBuilder.append("by @").append(annotationType.getSimpleName());
		int depth = -1;
		if (ann instanceof ConfigNotNull) {
			depth = ((ConfigNotNull) ann).depth();
		} else if (ann instanceof ConfigNotEmpty) {
			depth = ((ConfigNotEmpty) ann).depth();
		} else if (ann instanceof ConfigLength) {
			depth = ((ConfigLength) ann).depth();
		}
		if (depth > 0) {
			expectBuilder.append("(depth = ").append(depth).append(')');
		}
	}

	private static void getFieldAnnotations(Field f,
			List<Annotation> numberAnnotations,
			List<Annotation> stringAnnotations,
			List<Annotation> collectionAnnotations,
			List<Annotation> mapAnnotations,
			List<Annotation> nullAnnotations) {
		Annotation[] annotations = f.getAnnotations();
		for (Annotation ann : annotations) {
			if (ann instanceof ConfigEnum) {
				stringAnnotations.add(ann);
			} else if (ann instanceof ConfigNonNegative) {
				numberAnnotations.add(ann);
			} else if (ann instanceof ConfigNumberEnum) {
				numberAnnotations.add(ann);
			} else if (ann instanceof ConfigPattern) {
				stringAnnotations.add(ann);
			} else if (ann instanceof ConfigPositive) {
				numberAnnotations.add(ann);
			} else if (ann instanceof ConfigRange) {
				numberAnnotations.add(ann);
			} else if (ann instanceof ConfigSecret) {
				stringAnnotations.add(ann);
			}
		}
		annotations = f.getAnnotationsByType(ConfigNotNull.class);
		for (Annotation ann : annotations) {
			stringAnnotations.add(ann);
			collectionAnnotations.add(ann);
			mapAnnotations.add(ann);
			nullAnnotations.add(ann);
		}
		annotations = f.getAnnotationsByType(ConfigNotEmpty.class);
		for (Annotation ann : annotations) {
			stringAnnotations.add(ann);
			collectionAnnotations.add(ann);
			mapAnnotations.add(ann);
			nullAnnotations.add(ann);
		}
		annotations = f.getAnnotationsByType(ConfigLength.class);
		for (Annotation ann : annotations) {
			stringAnnotations.add(ann);
			collectionAnnotations.add(ann);
			mapAnnotations.add(ann);
			nullAnnotations.add(ann);
		}
	}
	
	static int validatePrimitive(Field f, double num, int depth, String keyName) {
		List<Annotation> numberAnnotations = new ArrayList<Annotation>();
		List<Annotation> stringAnnotations = new ArrayList<Annotation>();
		List<Annotation> collectionAnnotations = new ArrayList<Annotation>();
		List<Annotation> mapAnnotations = new ArrayList<Annotation>();
		List<Annotation> nullAnnotations = new ArrayList<Annotation>();
		getFieldAnnotations(f,
				numberAnnotations, stringAnnotations,
				collectionAnnotations, mapAnnotations,
				nullAnnotations);
		if (numberAnnotations.size() == 0) return 1; // No validate annotations
		StringBuilder expectBuilder = new StringBuilder();
		boolean result = validateNumber(num, numberAnnotations, expectBuilder);
		if (!result) { // invalid
			StringBuilder errMsg = new StringBuilder();
			errMsg.append('[').append(f.getType().getName()).append(':').append(num);
			trimEndingDot0(errMsg);
			errMsg.append(']');
			errMsg.append(": Invalid value for field \"").append(keyName)
					.append("\", ").append(expectBuilder);
			if (!Config.reportErrorToContinue(errMsg.toString())) return -1;
		}
		return result ? 1 : 0;
	}
	
	static int validateObject(Field f, Object obj, int depth, String keyName) {
		List<Annotation> numberAnnotations = new ArrayList<Annotation>();
		List<Annotation> stringAnnotations = new ArrayList<Annotation>();
		List<Annotation> collectionAnnotations = new ArrayList<Annotation>();
		List<Annotation> mapAnnotations = new ArrayList<Annotation>();
		List<Annotation> nullAnnotations = new ArrayList<Annotation>();
		StringBuilder expectBuilder = new StringBuilder();
		getFieldAnnotations(f,
				numberAnnotations, stringAnnotations,
				collectionAnnotations, mapAnnotations,
				nullAnnotations);
		boolean result = validateField(obj, 0,
				numberAnnotations, stringAnnotations,
				collectionAnnotations, mapAnnotations,
				nullAnnotations, expectBuilder);
		if (!result) { // invalid
			StringBuilder errMsg = new StringBuilder();
			appendObject(errMsg, obj);
			errMsg.append(": Invalid value for field \"").append(keyName)
					.append("\", ").append(expectBuilder);
			if (!Config.reportErrorToContinue(errMsg.toString())) return -1;
		}
		return result ? 1 : 0;
	}
	
	static boolean validateNumber(double num, List<Annotation> numberAnnotations, StringBuilder expectBuilder) {
		if (numberAnnotations.size() == 0) return true;
		for (Annotation annotation : numberAnnotations) {
			if (annotation instanceof ConfigNonNegative) {
				if (num >= 0) continue;
				formatExpectsAnnotation(expectBuilder, "a non-negative number", annotation);
				return false;
			}
			if (annotation instanceof ConfigPositive) {
				if (num > 0) continue;
				formatExpectsAnnotation(expectBuilder, "a positive number", annotation);
				return false;
			}
			if (annotation instanceof ConfigRange) {
				ConfigRange ann = (ConfigRange) annotation;
				if (ann.min() <= num && num <= ann.max()) continue;
				formatExpectsAnnotation(expectBuilder, "a number within range", annotation);
				return false;
			}
			if (annotation instanceof ConfigNumberEnum) {
				double[] values = ((ConfigNumberEnum) annotation).value();
				if (values != null && values.length > 0) {
					boolean matched = false;
					for (double v : values) {
						if (v == num) {
							matched = true;
							break;
						}
					}
					if (matched) continue;
				}
				formatExpectsAnnotation(expectBuilder, "a number in the set", annotation);
				return false;
			}
			// Skip other annotations
		}
		return true;
	}
	
	static String getTypeName(Class<?> type) {
		if (type.isArray()) return "array";
		if (List.class.isAssignableFrom(type)) return "list";
		if (Set.class.isAssignableFrom(type)) return "set";
		if (Map.class.isAssignableFrom(type)) return "map";
		return "value";
	}
	static boolean validateSize(int size, int depth, Class<?> type, List<Annotation> annotations, StringBuilder expectBuilder) {
		boolean result = true;
		for (Annotation ann : annotations) {
			Class<? extends Annotation> annotationType = ann.annotationType();
			if (annotationType == ConfigNotEmpty.class) {
				ConfigNotEmpty len = (ConfigNotEmpty) ann;
				if (len.depth() != depth) continue;
				if (size <= 0) {
					formatExpectsAnnotation(expectBuilder, "a non-empty " + getTypeName(type), ann);
					result = false;
					break;
				}
			} else if (annotationType == ConfigLength.class) {
				ConfigLength len = (ConfigLength) ann;
				if (len.depth() != depth) continue;
				if (size < len.min() || len.max() < size) {
					formatExpectsAnnotation(expectBuilder, "a " + getTypeName(type) + " with size", ann);
					result = false;
					break;
				}
			}
		}
		return result;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	static boolean validateField(Object obj, int depth,
			List<Annotation> numberAnnotations, List<Annotation> stringAnnotations,
			List<Annotation> collectionAnnotations, List<Annotation> mapAnnotations,
			List<Annotation> nullAnnotations, StringBuilder expectBuilder) {
		if (obj == null) {
			for (Annotation ann : nullAnnotations) {
				if (ann instanceof ConfigNotNull) {
					if (((ConfigNotNull) ann).depth() != depth) continue;
					formatExpectsAnnotation(expectBuilder, "a non-null value", ann);
					return false;
				} else if (ann instanceof ConfigNotEmpty) {
					if (((ConfigNotEmpty) ann).depth() != depth) continue;
					formatExpectsAnnotation(expectBuilder, "a non-empty value", ann);
					return false;
				} else if (ann instanceof ConfigLength) {
					ConfigLength lenAnn = (ConfigLength) ann;
					if (lenAnn.depth() != depth || lenAnn.min() < 0) continue;
					formatExpectsAnnotation(expectBuilder, "a value with length in range", ann);
					return false;
				}
			}
			return true;
		}
		Class<?> type = obj.getClass();
		if (type == String.class) {
			String str = (String) obj;
			for (Annotation annotation : stringAnnotations) {
				if (annotation instanceof ConfigLength) {
					ConfigLength ann = (ConfigLength) annotation;
					if (ann.depth() != depth) continue;
					int len = str.length();
					if (ann.min() <= len && len <= ann.max()) continue;
					formatExpectsAnnotation(expectBuilder, "a string with length in range", annotation);
					return false;
				}
				if (annotation instanceof ConfigEnum) {
					ConfigEnum ann = (ConfigEnum) annotation;
					String[] values = ann.value();
					if (values != null && values.length > 0) {
						boolean matched = false;
						for (String v : values) {
							if (v.equals(str)) {
								matched = true;
								break;
							}
						}
						if (matched) continue;
					}
					formatExpectsAnnotation(expectBuilder, "a non-empty string matching", annotation);
					return false;
				}
				if (annotation instanceof ConfigPattern) {
					ConfigPattern ann = (ConfigPattern) annotation;
					String pattern = ann.value();
					if (pattern == null || pattern.length() == 0) continue;
					if (str != null && str.length() > 0 && str.matches(pattern)) continue;
					formatExpectsAnnotation(expectBuilder, "a non-empty string matching", annotation);
					return false;
				}
			}
			return true;
		} else if (type == Class.class || type == Boolean.class) {
			// not validating
			return true;
		} else if (Utils.isBasicDataType(type)) {
			if (obj instanceof Number) {
				return validateNumber(((Number) obj).doubleValue(), numberAnnotations, expectBuilder);
			} else if (obj instanceof Character) {
				return validateNumber(((Character) obj).charValue(), numberAnnotations, expectBuilder);
			}
			return true;
		} else if (type.isArray()) {
			int size = Array.getLength(obj);
			if (!validateSize(size, depth, type, collectionAnnotations, expectBuilder)) return false;
			Class<?> compType = type.getComponentType();
			if (compType.isPrimitive()) {
				for (int i = 0; i < size; i++) {
					boolean needCheck = true;
					double num = 0;
					if (compType == int.class) num = Array.getInt(obj, i);
					else if (compType == long.class) num = Array.getLong(obj, i);
					else if (compType == short.class) num = Array.getShort(obj, i);
					else if (compType == byte.class) num = Array.getByte(obj, i);
					else if (compType == float.class) num = Array.getFloat(obj, i);
					else if (compType == double.class) num = Array.getDouble(obj, i);
					else if (compType == char.class) num = Array.getChar(obj, i);
					else needCheck = false;
					if (needCheck && !validateNumber(num, numberAnnotations, expectBuilder)) return false;
				}
				return true;
			}
			for (int i = 0; i < size; i++) {
				if (!validateField(Array.get(obj, i), depth + 1,
						numberAnnotations, stringAnnotations,
						collectionAnnotations, mapAnnotations,
						nullAnnotations, expectBuilder)) return false;
			}
			return true;
		} else if (Collection.class.isAssignableFrom(type)) {
			Collection col = (Collection) obj;
			int size = col.size(); //
			if (!validateSize(size, depth, type, collectionAnnotations, expectBuilder)) return false;
			Object[] values = col.toArray(new Object[size]);
			for (int i = 0; i < size; i++) {
				if (!validateField(values[i], depth + 1,
						numberAnnotations, stringAnnotations,
						collectionAnnotations, mapAnnotations,
						nullAnnotations, expectBuilder)) return false;
			}
			return true;
		} else if (Map.class.isAssignableFrom(type)) {
			Map map = (Map) obj;
			int size = map.size(); //
			if (!validateSize(size, depth, type, mapAnnotations, expectBuilder)) return false;
			Object[] keys = map.keySet().toArray(new Object[size]);
			for (int i = 0; i < size; i++) {
				if (!validateField(keys[i], depth + 1,
						numberAnnotations, stringAnnotations,
						collectionAnnotations, mapAnnotations,
						nullAnnotations, expectBuilder)) return false;
			}
			Object[] values = map.values().toArray(new Object[size]);
			for (int i = 0; i < size; i++) {
				if (!validateField(values[i], depth + 1,
						numberAnnotations, stringAnnotations,
						collectionAnnotations, mapAnnotations,
						nullAnnotations, expectBuilder)) return false;
			}
			return true;
		} else { // 
			Map<String, ConfigFieldFilter> configFilter = Config.configurationFilters;
			ConfigFieldFilter filter = configFilter != null ? configFilter.get(type.getName()) : null;
			Field[] fields = type.getFields();
			for (int i = 0; i < fields.length; i++) {
				Field f = fields[i];
				if (f == null) continue; // never happen
				if (f.getAnnotation(ConfigIgnore.class) != null) continue;
				int modifiers = f.getModifiers();
				if (ConfigFieldFilter.filterModifiers(filter, modifiers, true)) continue;
				String name = f.getName();
				if (filter != null && filter.filterName(name)) continue;
				if ((modifiers & Modifier.PUBLIC) == 0) {
					f.setAccessible(true);
				}
				Class<?> fieldType = f.getType();
				try {
					if (fieldType.isPrimitive()) {
						double fieldNum = 0;
						boolean needsCheck = true;
						if (fieldType == int.class) fieldNum = f.getInt(obj);
						else if (fieldType == long.class) fieldNum = f.getLong(obj);
						else if (fieldType == short.class) fieldNum = f.getShort(obj);
						else if (fieldType == byte.class) fieldNum = f.getByte(obj);
						else if (fieldType == float.class) fieldNum = f.getFloat(obj);
						else if (fieldType == double.class) fieldNum = f.getDouble(obj);
						else if (fieldType == char.class) fieldNum = f.getChar(obj);
						else needsCheck = false; // boolean.class
						if (needsCheck && !validateNumber(fieldNum, numberAnnotations, expectBuilder)) return false;
					} else {
						if (!validateField(f.get(obj), depth + 1,
								numberAnnotations, stringAnnotations,
								collectionAnnotations, mapAnnotations,
								nullAnnotations, expectBuilder)) return false;
					}
				} catch (Exception e) {
					e.printStackTrace();
					expectBuilder.append(e.getMessage());
					return false;
				}
			}
			return true;
		}
	}
	
}
