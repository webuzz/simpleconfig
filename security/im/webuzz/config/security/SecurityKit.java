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

package im.webuzz.config.security;

import im.webuzz.config.Config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
public class SecurityKit {

	private static int maxCredentials = 1024; // Maximum supported caching credentials.

	private static Map<String, String> caches = new ConcurrentHashMap<String, String>(64);
	
	private static boolean initialized = false;

	/**
	 * This method is considered as a public method of WebConfig#securityDecrypter class.
	 * 
	 * @param password
	 * @return
	 */
	public static String decrypt(final String password) {
		if (!initialized) {
			initialized = true;
			Config.registerUpdatingListener(SecurityConfig.class);
		}
		
		if (password == null || password.length() == 0) {
			return password;
		}
		String result = caches.get(password);
		if (result != null) {
			return result;
		}
		String b64Password = password;
		int sessionLength = b64Password.length();
		switch (sessionLength % 4) {
		case 2:
			b64Password += "==";
			break;
		case 1:
			b64Password += "===";
			break;
		case 3:
			b64Password += "=";
			break;
		default:
			break;
		}
		try {
			byte[] rawPassword = SimpleAES.b64DecryptBytes(b64Password);
			if (rawPassword != null && rawPassword.length > 0) {
				String newPassword = new String(rawPassword);
				int colonIdx = newPassword.indexOf(':');
				StringBuilder passwordBuilder = new StringBuilder();
				if (colonIdx != -1) {
					String user = newPassword.substring(0, colonIdx);
					passwordBuilder.append(user).append(':');
					newPassword = newPassword.substring(colonIdx + 1);
				}
				int newLength = newPassword.length();
				if (newLength % 2 == 1) { // incorrect
					passwordBuilder.append(newPassword); // keep it
				} else {
					int half = newLength / 2;
					for (int i = half - 1; i >= 0; i --) { // Exact password
						passwordBuilder.append(newPassword.charAt(i + i));
					}
				}
				result = passwordBuilder.toString();
				if (caches.size() < maxCredentials) {
					caches.put(password, result);
				}
				return result;
			}
		} catch (Exception e) {
		}
		return password;
	}

	public static String encrypt(String password) {
		if (!initialized) {
			initialized = true;
			Config.registerUpdatingListener(SecurityConfig.class);
		}
		
		if (password == null || password.length() == 0) {
			return password;
		}
		StringBuilder passwordBuilder = new StringBuilder();
		int colonIdx = password.indexOf(':');
		if (colonIdx != -1) { // May be HTTP Basic Authentication
			String user = password.substring(0, colonIdx);
			passwordBuilder.append(user).append(':'); // Keep user
			password = password.substring(colonIdx + 1);
		}
		int length = password.length();
		for (int i = length - 1; i >= 0; i--) { // Mix and double password length
			char c = Base64.intToBase64[(int) Math.floor(Math.random() * Base64.intToBase64.length)];
			passwordBuilder.append(password.charAt(i)).append(c);
		}
		String newPassword = passwordBuilder.toString();
		String encryptedPassword = SimpleAES.b64EncryptBytes(newPassword.getBytes());
		if(encryptedPassword != null) {
			encryptedPassword = encryptedPassword.replaceAll("=+$", "");
			return encryptedPassword;
		} else {
			return newPassword;
		}
	}

	/*
	 * Only keep those hackers who won't try decompiling classes.
	 */
	public static void main(String[] args) {
		if (args == null || args.length == 0 || args[0] == null) {
			System.out.println("Protecting password by private encryption.\r\n"
					+ "Usage: java -cp . " + SecurityKit.class.getName() + " [-c | --console] [config path] [password]");
			return;
		}
		int index = 0;
		boolean consoling = false;
		if ("-c".equals(args[index]) || "--console".equals(args[index])) {
			consoling = true;
			index++;
		}
		if (args.length > index + 1 // at least 2+
				|| (consoling && args.length > index)) {
			String configPath = args[index];
			if (configPath != null && configPath.length() > 0) {
				Config.initialize(configPath);
				Config.registerUpdatingListener(SecurityConfig.class);
			}
			index++;
		}
		
		if (!consoling) {
			String password = args[index];
			String decryptedPassword = SecurityKit.decrypt(password);
			if (decryptedPassword != null && !decryptedPassword.equals(password)) {
				System.out.println("Decrypted password: " + decryptedPassword);
			} else {
				System.out.println("Encrypted password: " + SecurityKit.encrypt(password));
			}
			return;
		}
		
		BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
		do {
			String line = null;
			try {
				line = reader.readLine();
			} catch (IOException e) {
				e.printStackTrace();
			}
			if (line == null) {
				break;
			}
			if (line.length() == 0) {
				continue;
			}
			String decryptedLine = SecurityKit.decrypt(line);
			if (line.equals(decryptedLine)) {
				System.out.println(SecurityKit.encrypt(line));
			} else {
				System.out.println(decryptedLine);
			}
		} while (true);
		try {
			reader.close();
		} catch (IOException e) {
		}
	}

}
