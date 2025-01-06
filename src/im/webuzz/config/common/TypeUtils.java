package im.webuzz.config.common;

import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TypeUtils {


	public static boolean isObjectOrObjectArray(Class<?> clazz) {
		if (clazz.isArray()) return isObjectOrObjectArray(clazz.getComponentType());
		return clazz == Object.class;
	}

	public static boolean isAbstractClass(Class<?> clazz) {
		if (clazz == null) return true;
		if (clazz.isPrimitive() || clazz.isArray()) {
			return false;
		}
		if (clazz.isInterface()) {
			if (clazz == List.class || clazz == Set.class || clazz == Map.class) {
				return false;
			}
			return true;
		}
		return Modifier.isAbstract(clazz.getModifiers());
	}

	public static boolean isBasicDataType(Class<?> type) {
		if (type == null) return false;
		return Number.class.isAssignableFrom(type) || type == Boolean.class || type == Character.class
				|| type == Class.class || type.isEnum() || type == Enum.class;
		/*
		Number.class.isAssignableFrom(type): type == Integer.class || type == Long.class
				|| type == Double.class || type == Float.class
				|| type == Short.class || type == Byte.class;
		//*/
	}

	public static Class<?> getRawType(Type vType) {
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

	public static Class<?> getInterfaceParamType(Class<?> sourceClass, Class<?> bindingClass) {
		Class<?> rawType = null;
		do {
			Type[] genericInterfaces = sourceClass.getGenericInterfaces();
			if (genericInterfaces == null || genericInterfaces.length == 0) {
				sourceClass = sourceClass.getSuperclass();
				continue;
			}
			for (Type type : genericInterfaces) {
				if (type instanceof Class<?>) {
					Class<?> paramClass = (Class<?>) type;
					if (bindingClass.isAssignableFrom(paramClass)) {
						sourceClass = paramClass;
						break;
					}
				} else if (type instanceof ParameterizedType) {
					ParameterizedType paramType = (ParameterizedType) type;
					if (paramType.getRawType() == bindingClass) {
						Type valueType = paramType.getActualTypeArguments()[0];
						rawType = getRawType(valueType);
						break;
					}
				}
			}
		} while (rawType == null);
		return rawType;
	}

	public static Class<?> calculateCommonType(Object[] os, Set<Class<?>> conflictedClasses) {
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

	public static Class<?> calculateCommonType(Collection<Object> os, Set<Class<?>> conflictedClasses) {
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

	public static boolean isBasicType(Class<?> type) {
		return type == String.class || isBasicDataType(type);
	}

	public static String getCollectionTypeName(Class<?> vsType) {
		String typeStr;
		if (Map.class.isAssignableFrom(vsType)) {
			typeStr = "map"; //$map;
		} else if (List.class.isAssignableFrom(vsType)) {
			typeStr = "list"; //$list;
		} else if (Set.class.isAssignableFrom(vsType)) {
			typeStr = "set"; //$set;
		} else {
			typeStr = "array"; //$array;
		}
		return typeStr;
	}


}
