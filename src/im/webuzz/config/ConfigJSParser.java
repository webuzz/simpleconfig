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

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

public class ConfigJSParser {

	private static final String convertJS = "function isPlainObject(o, ignoringProps) {\r\n" +
			"	if (o == null) return true;\r\n" +
			"	for (var p in o) {\r\n" +
			"		var isCustomized = true;\r\n" +
			"		if (ignoringProps != null) {\r\n" +
			"			for (var i = 0; i < ignoringProps.length; i++) {\r\n" +
			"				if (ignoringProps[i] == p) {\r\n" +
			"					isCustomized = false;\r\n" +
			"					break;\r\n" +
			"				}\r\n" +
			"			}\r\n" +
			"		}\r\n" +
			"		if (isCustomized) {\r\n" +
			"			if (o[p] != null && typeof o[p] != \"string\" && typeof o[p] != \"number\" && typeof o[p] != \"boolean\") {\r\n" +
			"				return false;\r\n" +
			"			}\r\n" +
			"		}\r\n" +
			"	}\r\n" +
			"	return true;\r\n" +
			"}\r\n" +
			"\r\n" +
			"function visit(builder, ignoringProps, prefix, o) {\r\n" +
			"	if (o == null) {\r\n" +
			"		builder[builder.length] = prefix + \"=[null]\"; \r\n" +
			"	} else if (typeof o == \"string\" || typeof o == \"number\" || typeof o == \"boolean\") {\r\n" +
			"		builder[builder.length] = prefix + \"=\" + ((o == \"\") ? \"[empty]\" : o); \r\n" +
			"	} else if (o instanceof Array) {\r\n" +
			"		var length = o.length;\r\n" +
			"		if (length == 0) {\r\n" +
			"			builder[builder.length] = prefix + \"=[empty]\"; \r\n" +
			"		} else {\r\n" +
			"			builder[builder.length] = prefix + \"=[]\";\r\n" +
			"			var maxZeros = (\"\" + length).length;\r\n" +
			"			for (var i = 0; i < length; i++) {\r\n" +
			"				var index = \"\" + (i + 1);\r\n" +
			"				var leadingZeros = maxZeros - index.length;\r\n" +
			"				for (var j = 0; j < leadingZeros; j++) {\r\n" +
			"					index = \"0\" + index;\r\n" +
			"				}\r\n" +
			"				visit(builder, ignoringProps, prefix + \".\" + index, o[i]);\r\n" +
			"			}\r\n" +
			"		}\r\n" +
			"	} else if (isPlainObject(o, ignoringProps)) {\r\n" +
			"		var objBuilder = [];\r\n" +
			"		for (var p in o) {\r\n" +
			"			var isCustomized = true;\r\n" +
			"			if (ignoringProps != null) {\r\n" +
			"				for (var i = 0; i < ignoringProps.length; i++) {\r\n" +
			"					if (ignoringProps[i] == p) {\r\n" +
			"						isCustomized = false;\r\n" +
			"						break;\r\n" +
			"					}\r\n" +
			"				}\r\n" +
			"			}\r\n" +
			"			if (isCustomized) {\r\n" +
			"				objBuilder[objBuilder.length] = p + \">\" + o[p];\r\n" +
			"			}\r\n" +
			"		}\r\n" +
			"		builder[builder.length] = prefix + \"=\" + (objBuilder.length == 0 ? \"[empty]\" : objBuilder.join(\";\"));\r\n" +
			"	} else {\r\n" +
			"		var generated = false;\r\n" +
			"		for (var p in o) {\r\n" +
			"			var isCustomized = true;\r\n" +
			"			if (ignoringProps != null) {\r\n" +
			"				for (var i = 0; i < ignoringProps.length; i++) {\r\n" +
			"					if (ignoringProps[i] == p) {\r\n" +
			"						isCustomized = false;\r\n" +
			"						break;\r\n" +
			"					}\r\n" +
			"				}\r\n" +
			"			}\r\n" +
			"			if (isCustomized) {\r\n" +
			"				if (prefix == null) {\r\n" +
			"					visit(builder, ignoringProps, p, o[p]);\r\n" +
			"				} else {\r\n" +
			"					if (!generated) {\r\n" +
			"						builder[builder.length] = prefix + \"=[]\";\r\n" +
			"					}\r\n" +
			"					visit(builder, ignoringProps, prefix + \".\" + p, o[p]);\r\n" +
			"				}\r\n" +
			"				generated = true;\r\n" +
			"			}\r\n" +
			"		}\r\n" +
			"		if (!generated) {\r\n" +
			"			builder[builder.length] = prefix + \"=[empty]\";\r\n" +
			"		}\r\n" +
			"	}\r\n" +
			"}\r\n" +
			"\r\n" +
			"function convertToProperties(configObj) {\r\n" +
			"	var ignoringProps = [];\r\n" +
			"	for (var p in {}) {\r\n" +
			"		ignoringProps[baseProps.length] = p;\r\n" +
			"	}\r\n" +
			"	var configProps = [];\r\n" +
			"	visit(configProps, ignoringProps, null, configObj);\r\n" +
			"	return configProps.join(\"\\r\\n\");\r\n" +
			"}\r\n";

	public static InputStream convertJS2Properties(InputStream fis) throws IOException {
		byte[] buffer = new byte[8096];
		int read = -1;
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		while ((read = fis.read(buffer)) != -1) {
			baos.write(buffer, 0, read);
		}
		String js = new String(baos.toByteArray(), Config.configFileEncoding);
		ScriptEngineManager mgr = new ScriptEngineManager();
		ScriptEngine jsEngine = mgr.getEngineByName("JavaScript");
		try {
			Object o = jsEngine.eval(convertJS + "\r\n$config = " + js.trim() + ";\r\nconvertToProperties($config);");
			if (o instanceof String) {
				String props = (String) o;
				return new ByteArrayInputStream(props.getBytes(Config.configFileEncoding));
			}
		} catch (ScriptException ex) {
			ex.printStackTrace();
		}
		return null;
	}

}
