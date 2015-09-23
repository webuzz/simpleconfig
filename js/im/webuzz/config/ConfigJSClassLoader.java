/*******************************************************************************
 * Copyright (c) 2010 - 2015 java2script.org, webuzz.im and others
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

package im.webuzz.config;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Load JavaScript related classes without Java adapters for security.
 * 
 * For JDK 6 or JDK 7. JDK 8 use nashorn engine directly.
 * 
 * @author zhourenjian
 */
public class ConfigJSClassLoader extends ClassLoader {

	private static ConfigJSClassLoader jsLoader = new ConfigJSClassLoader(ConfigJSClassLoader.class.getClassLoader());
	
	public static Map<String, Set<String>> scriptEngineJavaClassFilters = new ConcurrentHashMap<String, Set<String>>();
	
	static {
		Set<String> suffixes = new HashSet<String>();
		// JDK 1.6 or 1.7
		suffixes.add("JavaAdapter");
		suffixes.add("JavaMembers");
		suffixes.add("JavaImporter");
		scriptEngineJavaClassFilters.put("sun.org.mozilla.javascript.internal", suffixes);
	}

	private Map<String, Class<?>> loadedClasses;
	
	private ConfigJSClassLoader(ClassLoader parent) {
		super(parent);
		loadedClasses = new ConcurrentHashMap<String, Class<?>>();
	}

	private boolean checkClassPermission(String clazzName) {
		Map<String, Set<String>> filters = scriptEngineJavaClassFilters;
		if (filters == null) {
			return true;
		}
		for (String prefix : filters.keySet()) {
			if (!clazzName.startsWith(prefix)) {
				continue;
			}
			Set<String> suffixes = filters.get(prefix);
			if (suffixes == null) {
				continue;
			}
			if (suffixes.isEmpty()) {
				// empty means prefix filters all
				return false;
			}
			for (String suffix : suffixes) {
				if (suffix == null || suffix.length() <= 0) {
					continue;
				}
				if (clazzName.endsWith(suffix)) {
					return false;
				}
			}
		}
		return true;
	}

	/*
	 * Not overriding loadClass(String, boolean) or findClass(String).
	 * For loadClass(String, boolean), we don't know how to call resolveClass.
	 * For findClass(String), parent.loadClass(String, boolean) is called first
	 * and get existed class. It is not expected.
	 */
	@Override
	public Class<?> loadClass(String clazzName) throws ClassNotFoundException {
		//System.out.println("Loading: " + clazzName);
		if (!checkClassPermission(clazzName)) {
			//System.out.println("Skipping: " + clazzName);
			return null;
		}
		
		Class<?> clazz = loadedClasses.get(clazzName);
		if (clazz != null) {
			return clazz;
		}
		if (!clazzName.startsWith("java.") && !clazzName.startsWith("javax.")) {
			// The following lines are IO sensitive
			InputStream is = getParent().getResourceAsStream(clazzName.replace('.', '/') + ".class");
			if (is != null) {
				byte[] buffer = new byte[8096];
				int read = -1;
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				try {
					while ((read = is.read(buffer)) != -1) {
						baos.write(buffer, 0, read);
					}
				} catch (IOException e) {
					//e.printStackTrace();
				} finally {
					if (is != null) {
						try {
							is.close();
						} catch (IOException ee) {
						}
					}
				}
				int length = baos.size();
				if (length > 0) {
					try {
						clazz = defineClass(clazzName, baos.toByteArray(), 0, length);
						loadedClasses.put(clazzName, clazz);
						return clazz;
					} catch (Throwable e) {
						e.printStackTrace();
					}
				}
			}
		}
		// continue to load class by super class loader
		clazz = getParent().loadClass(clazzName);
		loadedClasses.put(clazzName, clazz);
		return clazz;
	}
	
	/*
	 * Load script engine factory by this class loader and invoke #getScriptEngine
	 * to get a script engine.
	 */
	public static Object loadScriptEngine(String scriptEngineFactoryClass) {
		try {
			Class<?> runnableClass = jsLoader.loadClass(scriptEngineFactoryClass);
			if (runnableClass != null) {
				Object factory = runnableClass.newInstance();
				if (factory != null) {
					Method method = factory.getClass().getMethod("getScriptEngine");
					if (method != null) {
						return method.invoke(factory);
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

}
