/*******************************************************************************
 * Copyright (c) 2010 - 2024 webuzz.im and others
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
package im.webuzz.config.codec;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import im.webuzz.config.util.Base64;

/**
 * This is a reference implementation of configuration security kit for
 * protecting secret configuration item, for example, password, authorization
 * secret, API key.
 * 
 * For production environment, please implement your own security kit for a
 * better protection.
 * 
 * @author zhourenjian
 */
public class SecretCodec implements ConfigCodec<String> {

	private static int maxCredentials = 1024; // Maximum supported caching credentials.

	private static Map<String, String> caches = new ConcurrentHashMap<String, String>(64);
	
	@Override
	public String encode(String source) {
		if (source == null || source.length() == 0) {
			return source;
		}
		StringBuilder builder = new StringBuilder();
		int colonIdx = source.indexOf(':');
		if (colonIdx != -1) { // May be HTTP Basic Authentication
			String user = source.substring(0, colonIdx);
			builder.append(user).append(':'); // Keep user
			source = source.substring(colonIdx + 1);
		}
		int length = source.length();
		for (int i = length - 1; i >= 0; i--) { // Mix and double password length
			int base64Len = Base64.intToBase64.length;
			int idx = AESKeysConfig.generateRandomness ? (int) Math.floor(Math.random() * base64Len) : i % base64Len;
			char c = Base64.intToBase64[idx];
			builder.append(source.charAt(i)).append(c);
		}
		String newValue = builder.toString();
		String encodedValue = SimpleAES.b64EncryptBytes(newValue.getBytes());
		if(encodedValue != null) {
			encodedValue = encodedValue.replaceAll("=+$", "");
			return encodedValue;
		} else {
			return null;
		}
	}

	@Override
	public String decode(String encodedString) {
		if (encodedString == null || encodedString.length() == 0) {
			return encodedString;
		}
		String result = caches.get(encodedString);
		if (result != null) {
			return result;
		}
		String b64Value = encodedString;
		int sessionLength = b64Value.length();
		switch (sessionLength % 4) {
		case 2:
			b64Value += "==";
			break;
		case 1:
			b64Value += "===";
			break;
		case 3:
			b64Value += "=";
			break;
		default:
			break;
		}
		try {
			byte[] rawValue = SimpleAES.b64DecryptBytes(b64Value);
			if (rawValue != null && rawValue.length > 0) {
				String newValue = new String(rawValue);
				int colonIdx = newValue.indexOf(':');
				StringBuilder builder = new StringBuilder();
				if (colonIdx != -1) {
					String user = newValue.substring(0, colonIdx);
					builder.append(user).append(':');
					newValue = newValue.substring(colonIdx + 1);
				}
				int newLength = newValue.length();
				if (newLength % 2 == 1) { // incorrect
					builder.append(newValue); // keep it
				} else {
					int half = newLength / 2;
					for (int i = half - 1; i >= 0; i --) { // Exact password
						builder.append(newValue.charAt(i + i));
					}
				}
				result = builder.toString();
				if (caches.size() < maxCredentials) {
					caches.put(encodedString, result);
				}
				return result;
			}
		} catch (Exception e) {
		}
		return null;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (obj instanceof SecretCodec) return true;
		return false;
	}
	
	@Override
	public int hashCode() {
		return this.getClass().getName().hashCode();
	}

}