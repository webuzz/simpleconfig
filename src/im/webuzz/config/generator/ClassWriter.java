package im.webuzz.config.generator;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Set;

import im.webuzz.config.Utils;

public class ClassWriter {
	
	public StringBuilder appendFieldType(StringBuilder builder, Class<?> type, Type paramType) {
		if (type.isArray()) {
			Class<?> compType = type.getComponentType();
			Type compParamType = null;
			if (paramType instanceof GenericArrayType) {
				GenericArrayType gaType = (GenericArrayType) paramType;
				compParamType = gaType.getGenericComponentType();
			}
			// TODO: To test recursive array type, like [array:[array]]
			builder.append("[array:");
			appendFieldType(builder, compType, compParamType);
			builder.append(']');
		} else if (Map.class.isAssignableFrom(type) || List.class.isAssignableFrom(type) || Set.class.isAssignableFrom(type)) {
			builder.append('[').append(Utils.getCollectionTypeName(type)).append(']');
		} else {
			builder.append(getTypeName(type));
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

	public void appendMapType(StringBuilder builder, Class<?> keyType, Class<?> valueType, boolean keyNeedsTypeInfo,
			boolean valueNeedsTypeInfo) {
		builder.append("map");
		if ((keyNeedsTypeInfo && keyType != null && keyType != Object.class)
				|| (valueNeedsTypeInfo && valueType != null && valueType != Object.class)){
			builder.append(':');
			appendFieldType(builder, keyType, null);
			builder.append(',');
			appendFieldType(builder, valueType, null);
		}
	}

}
