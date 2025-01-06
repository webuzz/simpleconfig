package im.webuzz.config.common;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class AnnotationProxy {

	private final class AnnotationInvocationHandler implements InvocationHandler {
		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			String name = method.getName();
			if ("annotationType".equals(name)) {
				return type;
			}
			if ("hashCode".equals(name)) {
				return proxyHashCode();
			}
			if ("equals".equals(name)) {
				//System.out.println("Debug annotation#equals : " + proxyEquals(args[0]));
				return proxyEquals(args[0]);
			}
			if ("toString".equals(name)) {
				StringBuilder builder = new StringBuilder();
				builder.append('@').append(type.getName());
				if (cachedFields != null && cachedFields.length > 0) {
					builder.append('(');
					for (int i = 0; i < cachedFields.length; i++) {
						if (i > 0) builder.append(',');
						AnnotationField f = cachedFields[i];
						builder.append(f.name).append('=').append(f.value);
					}
					builder.append(')');
				}
				return builder.toString();
			}
			AnnotationField f = annotationFields.get(name);
			if (f == null) return null;
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

	int proxyHashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Arrays.hashCode(cachedFields);
		result = prime * result + Objects.hash(type.getName());
		return result;
	}

	boolean proxyEquals(Object obj) {
		if (obj == null) return false;
		if (!type.isAssignableFrom(obj.getClass())) return false;
		Method[] methods = type.getDeclaredMethods();
		try {
			for (Method method : methods) {
				String name = method.getName();
				AnnotationField field = cachedFieldMap.get(name);
				if (!Objects.equals(field.value, method.invoke(obj)))
					return false;
			} 
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}
	
	
}
