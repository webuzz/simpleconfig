package im.webuzz.config.common;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CommentClassWriter {
	
	public StringBuilder appendFieldType(StringBuilder builder, Class<?> type, Type paramType) {
		if (type.isArray()) {
			Class<?> compType = type.getComponentType();
			Type compParamType = null;
			if (paramType instanceof GenericArrayType) {
				GenericArrayType gaType = (GenericArrayType) paramType;
				compParamType = gaType.getGenericComponentType();
			}
			appendFieldType(builder, compType, compParamType).append("[]");
		} else if (Map.class.isAssignableFrom(type)) {
			builder.append(getTypeName(type));
			StringBuilder typeBuilder = new StringBuilder();
			appendParameterizedType(paramType, 0, typeBuilder).append(", ");
			appendParameterizedType(paramType, 1, typeBuilder);
			String innerType = typeBuilder.toString();
			if (!"Object, Object".equals(innerType)) {
				builder.append('<').append(innerType).append('>');
			}
		} else if (List.class.isAssignableFrom(type) || Set.class.isAssignableFrom(type) || Class.class.isAssignableFrom(type)) {
			builder.append(getTypeName(type));
			StringBuilder typeBuilder = new StringBuilder();
			appendParameterizedType(paramType, 0, typeBuilder);
			String innerType = typeBuilder.toString();
			if (!"Object".equals(innerType)) {
				builder.append('<').append(innerType).append('>');
			}
		} else {
			builder.append(getTypeName(type));
		}
		return builder;
	}

	private StringBuilder appendParameterizedType(Type paramType, int argIndex, StringBuilder builder) {
		if (paramType instanceof ParameterizedType) {
			Type valueType = ((ParameterizedType) paramType).getActualTypeArguments()[argIndex];
			appendFieldType(builder, TypeUtils.getRawType(valueType), valueType);
		} else {
			builder.append("Object");
		}
		return builder;
	}

	private static String getTypeName(Class<?> type) {
		String typeName = type.getName();
		if (typeName.startsWith("java.") || typeName.startsWith("javax.")) {
			return type.getSimpleName();
		}
		return typeName;
	}

//	public void appendMapType(StringBuilder builder, Class<?> keyType, Class<?> valueType, boolean keyNeedsTypeInfo,
//			boolean valueNeedsTypeInfo) {
//		builder.append("map");
//		if ((keyNeedsTypeInfo && keyType != null && keyType != Object.class)
//				|| (valueNeedsTypeInfo && valueType != null && valueType != Object.class)){
//			builder.append(':');
//			appendFieldType(builder, keyType, null, false);
//			builder.append(',');
//			appendFieldType(builder, valueType, null, false);
//		}
//	}

}
