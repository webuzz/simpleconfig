package im.webuzz.config;

import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class Utils {


	static Set<String> keywords = new HashSet<String>(Arrays.asList(new String[] {
			"var", "let", "const",
			"if", "else", "switch", "case", "default",
			"for", "in", "while", "do", "break", "continue", "return",
			"throw", "try", "catch", "throws", "finally",
			"function", "class", "extends", "implements", "interface", "enum", "super", "constructor", "this",
			"new", "delete", "async", "await", "yield",
			"package", "import", "export",
			"pubic", "protected", "private", "static", "final",
			"instanceof", "typeof", "void",
			"prototype", "arguments", "null", "undefined", "true", "fasle",
			"with", "debugger", "valueOf",
			/*"label", "java", "javax", "sun", */
	}));

	protected static String wrapAsJSFieldName(Object k) {
		return keywords.contains(k) ? "\"" + k + "\"" : String.valueOf(k);
	}
	
	protected static boolean isObjectOrObjectArray(Class<?> clazz) {
		if (clazz.isArray()) return isObjectOrObjectArray(clazz.getComponentType());
		return clazz == Object.class;
	}

	protected static boolean isAbstractClass(Class<?> clazz) {
		if (clazz == null) return true;
	    if (clazz.isPrimitive() || clazz.isArray() || clazz.isInterface()) {
	        return false;
	    }
	    return Modifier.isAbstract(clazz.getModifiers());
	}

	protected static boolean isBasicDataType(Class<?> type) {
		if (type == null) return false;
		return Number.class.isAssignableFrom(type) || type == Boolean.class || type == Character.class
				|| type == Class.class;
		/*
		return type == Integer.class || type == Long.class
				|| type == Boolean.class || type == Double.class || type == Float.class
				|| type == Short.class || type == Byte.class || type == Character.class
				|| type == Class.class;
		//*/
	}

	protected static Class<?> getRawType(Type vType) {
		if (vType instanceof GenericArrayType) {
			GenericArrayType aType = (GenericArrayType) vType;
			Type valueType = aType.getGenericComponentType();
			if (valueType instanceof Class<?>) {
				return Array.newInstance((Class<?>) valueType, 0).getClass();
			} else {
				return Array.newInstance(getRawType(valueType), 0).getClass();
			}
		}
		if (vType instanceof ParameterizedType) {
			return (Class<?>) ((ParameterizedType) vType).getRawType();
		}
		if (vType instanceof WildcardType) {
			WildcardType wType = (WildcardType) vType;
			Type[] upperBounds = wType.getUpperBounds();
			if (upperBounds != null && upperBounds.length > 0) {
				return getRawType(upperBounds[0]);
			}
			Type[] lowerBounds = wType.getLowerBounds();
			if (lowerBounds != null && lowerBounds.length > 0) {
				return getRawType(lowerBounds[0]);
			}
		}
		if (vType instanceof Class<?>) {
			return (Class<?>) vType;
		}
		throw new RuntimeException("Unknown raw type for " + vType);
	}

	public static Class<?> calculateCommonType(Collection<?> os, Set<Class<?>> conflictedClasses) {
		Class<?> commonType = null;
		for (Object o : os) {
			if (o == null) continue;
			Class<?> oClass = o.getClass();
			if (commonType == null) {
				commonType = oClass;
				continue;
			}
			if (oClass == commonType) continue;
			if (commonType.isAssignableFrom(oClass)) continue;
			if (oClass.isAssignableFrom(commonType)) {
				commonType = oClass;
				if (conflictedClasses.size() > 0) {
					Set<Class<?>> toRemoveds = new HashSet<Class<?>>();
					for (Class<?> cc : conflictedClasses) {
						if (commonType.isAssignableFrom(cc)) {
							toRemoveds.add(cc);
						}
					}
					conflictedClasses.removeAll(toRemoveds);
				}
				continue;
			}
			conflictedClasses.add(oClass);
		}
		return commonType;
	}

	public static boolean canAKeyBeAFieldName(String key) {
		int len = key.length();
		for (int i = 0; i < len; i++) {
			char c = key.charAt(i);
			if (i == 0) {
				if (!(('a' <= c && c <= 'z') || ('A' <= c && c <= 'Z') || '_' == c || '$' == c)) {
					return false;
				}
			} else {
				if (!(('a' <= c && c <= 'z') || ('A' <= c && c <= 'Z') || '_' == c || '$' == c || ('0' <= c && c <= '9'))) {
					return false;
				}
			}
		}
		return true;
	}

	public static boolean canKeysBeFieldNames(Object[] keys) {
		for (int i = 0; i < keys.length; i++) {
			if (!(keys[i] instanceof String) || !canAKeyBeAFieldName((String) keys[i])) return false;
		}
		return true;
	}

}
