package im.webuzz.config.annotation;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import im.webuzz.config.Config;
import im.webuzz.config.InternalConfigUtils;
import im.webuzz.config.generator.AnnotationWriter;
import im.webuzz.config.util.TypeUtils;

public class AnnotationValidator {

	private AnnotationWriter annWriter;
	
	public AnnotationValidator() {
		super();
		this.annWriter = new AnnotationWriter();
	}

	private void formatExpectsAnnotation(StringBuilder expectBuilder, Annotation ann) {
		expectBuilder.append("expecting ");
		annWriter.appendAnnotation(expectBuilder, ann, false);
	}

//	protected static void appendAnnotation(StringBuilder builder, Annotation ann) {
//		appendAnnotation(builder, ann, false);
//	}

//	public static String formatString(String str) {
//		return str.replaceAll("\\\\", "\\\\\\\\").replaceAll("\r", "\\\\r").replaceAll("\n", "\\\\n").replaceAll("\t", "\\\\t").trim();
//	}

	private void getFieldAnnotations(Field f,
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
			} else if (ann instanceof ConfigPreferredCodec) {
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
	
	public int validatePrimitive(Field f, double num, int depth, String keyName) {
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
			annWriter.appendFieldNumber(errMsg, f, num);
			errMsg.append(": Invalid value for field \"").append(keyName)
					.append("\", ").append(expectBuilder);
			if (!Config.reportErrorToContinue(errMsg.toString())) return -1;
		}
		return result ? 1 : 0;
	}
	
	public int validateObject(Field f, Object obj, int depth, String keyName) {
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
			annWriter.appendFieldObject(errMsg, f, obj);
			errMsg.append(": Invalid value for field \"").append(keyName)
					.append("\", ").append(expectBuilder);
			if (!Config.reportErrorToContinue(errMsg.toString())) return -1;
		}
		return result ? 1 : 0;
	}
	
	protected boolean validateNumber(double num, List<Annotation> numberAnnotations, StringBuilder expectBuilder) {
		if (numberAnnotations.size() == 0) return true;
		for (Annotation annotation : numberAnnotations) {
			if (annotation instanceof ConfigNonNegative) {
				if (num >= 0) continue;
				formatExpectsAnnotation(expectBuilder, annotation);
				return false;
			}
			if (annotation instanceof ConfigPositive) {
				if (num > 0) continue;
				formatExpectsAnnotation(expectBuilder, annotation);
				return false;
			}
			if (annotation instanceof ConfigRange) {
				ConfigRange ann = (ConfigRange) annotation;
				if (ann.min() <= num && num <= ann.max()) continue;
				formatExpectsAnnotation(expectBuilder, annotation);
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
				formatExpectsAnnotation(expectBuilder, annotation);
				return false;
			}
			// Skip other annotations
		}
		return true;
	}
	
	/*
	static String getTypeName(Class<?> type) {
		if (type.isArray()) return "array";
		if (List.class.isAssignableFrom(type)) return "list";
		if (Set.class.isAssignableFrom(type)) return "set";
		if (Map.class.isAssignableFrom(type)) return "map";
		return "value";
	}
	//*/
	
	protected boolean validateSize(int size, int depth, Class<?> type, List<Annotation> annotations, StringBuilder expectBuilder) {
		boolean result = true;
		for (Annotation ann : annotations) {
			Class<? extends Annotation> annotationType = ann.annotationType();
			if (annotationType == ConfigNotEmpty.class) {
				ConfigNotEmpty len = (ConfigNotEmpty) ann;
				if (len.depth() != depth) continue;
				if (size <= 0) {
					formatExpectsAnnotation(expectBuilder, ann);
					result = false;
					break;
				}
			} else if (annotationType == ConfigLength.class) {
				ConfigLength len = (ConfigLength) ann;
				if (len.depth() != depth) continue;
				if (size < len.min() || len.max() < size) {
					formatExpectsAnnotation(expectBuilder, ann);
					result = false;
					break;
				}
			}
		}
		return result;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	protected boolean validateField(Object obj, int depth,
			List<Annotation> numberAnnotations, List<Annotation> stringAnnotations,
			List<Annotation> collectionAnnotations, List<Annotation> mapAnnotations,
			List<Annotation> nullAnnotations, StringBuilder expectBuilder) {
		if (obj == null) {
			for (Annotation ann : nullAnnotations) {
				if (ann instanceof ConfigNotNull) {
					if (((ConfigNotNull) ann).depth() != depth) continue;
					formatExpectsAnnotation(expectBuilder, ann);
					return false;
				} else if (ann instanceof ConfigNotEmpty) {
					if (((ConfigNotEmpty) ann).depth() != depth) continue;
					formatExpectsAnnotation(expectBuilder, ann);
					return false;
				} else if (ann instanceof ConfigLength) {
					ConfigLength lenAnn = (ConfigLength) ann;
					if (lenAnn.depth() != depth || lenAnn.min() < 0) continue;
					formatExpectsAnnotation(expectBuilder, ann);
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
					formatExpectsAnnotation(expectBuilder, annotation);
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
					formatExpectsAnnotation(expectBuilder, annotation);
					return false;
				}
				if (annotation instanceof ConfigPattern) {
					ConfigPattern ann = (ConfigPattern) annotation;
					String pattern = ann.value();
					if (pattern == null || pattern.length() == 0) continue;
					if (str != null && str.length() > 0 && str.matches(pattern)) continue;
					formatExpectsAnnotation(expectBuilder, annotation);
					return false;
				}
			}
			return true;
		} else if (type == Class.class || type == Boolean.class) {
			// not validating
			return true;
		} else if (type.isEnum() || type == Enum.class) {
			// not validating
			return true;
		} else if (TypeUtils.isBasicDataType(type)) {
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
			Map<Class<?>, Map<String, Annotation[]>> typeAnns = Config.configurationAnnotations;
			Map<String, Annotation[]> fieldAnns = typeAnns == null ? null : typeAnns.get(type);
			Field[] fields = type.getFields();
			for (int i = 0; i < fields.length; i++) {
				Field f = fields[i];
				if (InternalConfigUtils.isFiltered(f, false, fieldAnns, false)) continue;
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
