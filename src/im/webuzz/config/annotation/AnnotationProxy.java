package im.webuzz.config.annotation;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AnnotationProxy {

	private final class AnnotationInvocationHandler implements InvocationHandler {
		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			String name = method.getName();
			if ("annotationType".equals(name)) {
				return type;
			}
			if ("equals".equals(name)) {
				System.out.println("Debug annotation#equals");
			}
			AnnotationField f = annotationFields.get(name);
			if (f == null) return null;
			/*
			if (f.fieldType == int.class) return f.intValue;
			if (f.fieldType == long.class) return f.longValue;
			if (f.fieldType == float.class) return f.floatValue;
			if (f.fieldType == double.class) return f.doubleValue;
			if (f.fieldType == byte.class) return f.byteValue;
			if (f.fieldType == short.class) return f.shortValue;
			if (f.fieldType == boolean.class) return f.booleanValue;
			if (f.fieldType == char.class) return f.charValue;
			if (f.fieldType == String.class) return f.stringValue;
			if (Enum.class.isAssignableFrom(f.fieldType)) return f.enumValue;
			if (f.fieldType == Class.class) return f.classValue;
			if (Annotation.class.isAssignableFrom(f.fieldType)) return f.annotationValue;
			//*/
			return f.value;
		}
	}
	
	private Class<? extends Annotation> type;
	private Map<String, AnnotationField> annotationFields;
	private Map<String, AnnotationField> cachedFieldMap;
	private AnnotationField[] cachedFields;
	
	public AnnotationProxy(Class<? extends Annotation> type, Annotation ann) {
		this.type = type;
		cachedFieldMap = new ConcurrentHashMap<String, AnnotationField>();
		annotationFields = new ConcurrentHashMap<String, AnnotationField>();
		Method[] methods = type.getDeclaredMethods();
		List<AnnotationField> fields = new ArrayList<AnnotationField>();
		for (Method method : methods) {
			String name = method.getName();
			AnnotationField f = new AnnotationField();
			f.name = name;
			f.type = method.getReturnType();
			/*
			if (f.fieldType == int.class) f.intValue = (Integer) method.getDefaultValue();
			if (f.fieldType == long.class) f.longValue = (Long) method.getDefaultValue();
			if (f.fieldType == float.class) f.floatValue = (Float) method.getDefaultValue();
			if (f.fieldType == double.class) f.doubleValue = (Double) method.getDefaultValue();
			if (f.fieldType == byte.class) f.byteValue = (Byte) method.getDefaultValue();
			if (f.fieldType == short.class) f.shortValue = (Short) method.getDefaultValue();
			if (f.fieldType == boolean.class) f.booleanValue = (Boolean) method.getDefaultValue();
			if (f.fieldType == char.class) f.charValue = (Character) method.getDefaultValue();
			if (f.fieldType == String.class) f.stringValue = (String) method.getDefaultValue();
			if (Enum.class.isAssignableFrom(f.fieldType)) f.enumValue = (Enum<?>) method.getDefaultValue();
			if (f.fieldType == Class.class) f.classValue = (Class<?>) method.getDefaultValue();
			if (Annotation.class.isAssignableFrom(f.fieldType)) f.annotationValue = (Annotation) method.getDefaultValue();
			//*/
			if (ann == null) {
				f.value = method.getDefaultValue();
			} else {
				try {
					f.value = method.invoke(ann);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			annotationFields.put(name, f);
//			Field field = (Field) Proxy.newProxyInstance(
//					Field.class.getClassLoader(),
//					new Class<?>[]{Field.class},
//					new FieldInvocationHandler(name, f.fieldType)
//				);
			cachedFieldMap.put(name, f);
			fields.add(f);
		}
		cachedFields = fields.toArray(new AnnotationField[fields.size()]);
	}
	
	public Annotation newAnnotation() {
		return (Annotation) Proxy.newProxyInstance(
				type.getClassLoader(),
				new Class<?>[]{type},
				new AnnotationInvocationHandler());
	}
	
	public Class<? extends Annotation> getAnnotationType() {
		return type;
	}
	
	public AnnotationField[] getDeclaredFields() {
		return cachedFields;
	}
	
	public AnnotationField getDeclaredField(String name) {
		return cachedFieldMap.get(name);
	}
}
