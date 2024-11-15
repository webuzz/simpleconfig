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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.List;

import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;

public class ConfigJSParser implements IConfigConverter {

	private static String convertJS = null;

	private static Object jsEngine = null;
	private static Method evalMethod = null;
	private static boolean initialized = false;
	
	private static boolean checkInitialize() {
		if (initialized) {
			return true;
		}
		InputStream jsStream = ConfigJSParser.class.getResourceAsStream("JS2Props.js");
		if (jsStream == null) {
			System.out.println("[FATAL] Failed to read JS2Props.js from resource stream.");
			return false;
		}
		byte[] buffer = new byte[8096];
		int read = -1;
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			while ((read = jsStream.read(buffer)) != -1) {
				baos.write(buffer, 0, read);
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				jsStream.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		convertJS = new String(baos.toByteArray(), Config.configFileEncoding);

		ScriptEngineManager mgr = new ScriptEngineManager();
		String factoryName = null;
		for (ScriptEngineFactory f : mgr.getEngineFactories()) {
			List<String> names = f.getNames();
			if (names != null) {
				for (String name : names) {
					if ("JavaScript".equalsIgnoreCase(name)) {
						factoryName = f.getClass().getName();
						break;
					}
				}
				if (factoryName != null) {
					break;
				}
			}
		}
		if (factoryName == null ) {
			System.out.println("You need to include nashorn Javascript engine after Java 15!");
			return false;
		}
		if (factoryName.indexOf(".nashorn.") != -1) {
			// For JDK 8+, Nashorn is secure, throwing exceptions on evaluating malicious script
			jsEngine = mgr.getEngineByName("JavaScript");
		} else {
			// For JDK 1.6, load script engine without Java adapters or members
			jsEngine = ConfigJSClassLoader.loadScriptEngine(factoryName);
		}
		if (jsEngine != null) {
			try {
				evalMethod = jsEngine.getClass().getMethod("eval", String.class);
				if (evalMethod != null) {
					evalMethod.invoke(jsEngine, convertJS);
					initialized = true;
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return initialized;
	}
	
	@Override
	public InputStream convertToProperties(InputStream is) throws IOException {
		byte[] buffer = new byte[8096];
		int read = -1;
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		while ((read = is.read(buffer)) != -1) {
			baos.write(buffer, 0, read);
		}
		String js = new String(baos.toByteArray(), Config.configFileEncoding).trim();
		if (!js.endsWith(";")) {
			js += ";";
		}
		try {
			if (checkInitialize()) {
				Object o = evalMethod.invoke(jsEngine, "$config = " + js + "\r\nconvertToProperties($config);");
				if (o instanceof String) {
//					System.out.println(convertJS);
//					System.out.println("js->ini");
//					System.out.println(o);
					return new ByteArrayInputStream(((String) o).getBytes(Config.configFileEncoding));
				}
			}
		} catch (Exception ex) {
			ex.printStackTrace();
			// If malicious js (last) modifies #convertToProperties, try to correct it to original JavaScript.
			// So normal js configuration won't be affected.
			try {
				if (checkInitialize()) {
					Object o = evalMethod.invoke(jsEngine, convertJS + "$config = " + js + "\r\nconvertToProperties($config);");
					if (o instanceof String) {
						// System.out.println(o);
						return new ByteArrayInputStream(((String) o).getBytes(Config.configFileEncoding));
					}
				}
			} catch (Exception ex2) {
				ex2.printStackTrace();
			}
		}
		return null;
	}

	public static void main(String[] args) {
		checkInitialize();
		System.out.println(convertJS);
	}
}
