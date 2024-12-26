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

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import im.webuzz.config.util.Base64;

//import org.bouncycastle.jce.provider.BouncyCastleProvider;

public class SimpleAES {

	private static SecretKeySpec sKeySpec0 = null; // encrypt key
	private static SecretKeySpec sKeySpec1 = null; // decrypt key1, same as encrypt key by default
	private static SecretKeySpec sKeySpec2 = null; // decrypt key2, another encrypt key, to be default encrypt/decrypt key
	
	private static boolean initialized = false;
	
	// Set these default values, so this class can be used without configuration
	private static String oldKey0 = "5c57deef93c0ffc41837f5aa704a4c78";
	private static String oldKey1 = "5c57deef93c0ffc41837f5aa704a4c78";
	private static String oldKey2 = null;

	// This method is considered as single-threaded
	public static void updateKeys(String key0, String key1, String key2) {
		if (!testKeys(key0, key1, key2)) {
			System.out.println("[Config:ERROR] Invalid AES keys!!!");
			return;
		}
		
		SecretKeySpec newKeySpec0 = sKeySpec0;
		String newKey0 = oldKey0;
		SecretKeySpec newKeySpec1 = sKeySpec1;
		String newKey1 = oldKey1;
		SecretKeySpec newKeySpec2 = sKeySpec2;
		String newKey2 = oldKey2;
		
		if (!isSameKeys(oldKey0, key0) || !initialized) {
			if (key0 != null && key0.length() > 0) {
				byte[] rawKey0 = fromHexString(key0);
				newKeySpec0 = new SecretKeySpec(rawKey0, "AES");
				newKey0 = key0;
			}
		}
		if (!isSameKeys(oldKey1, key1) || !initialized) {
			if (key1 != null && key1.length() > 0) {
				byte[] rawKey1 = fromHexString(key1);
				newKeySpec1 = new SecretKeySpec(rawKey1, "AES");
				newKey1 = key1;
			}
		}
		if (!isSameKeys(oldKey2, key2) || !initialized) {
			if (key2 != null && key2.length() > 0) {
				byte[] rawKey2 = fromHexString(key2);
				newKeySpec2 = new SecretKeySpec(rawKey2, "AES");
				newKey2 = key2;
			} else {
				// key0 and key1 are default keys, will not be reset to null!
				newKeySpec2 = null;
				newKey2 = null;
			}
		}
		// Update keys
		sKeySpec0 = newKeySpec0;
		oldKey0 = newKey0;
		sKeySpec1 = newKeySpec1;
		oldKey1 = newKey1;
		sKeySpec2 = newKeySpec2;
		oldKey2 = newKey2;
		initialized = true;
	}

	private static boolean testKeys(String newKey0, String newKey1, String newKey2) {
		if (newKey0 == null || newKey1 == null) {
			return false;
		}
		if (!newKey0.equals(newKey1) && !newKey0.equals(newKey2)) {
			return false;
		}
		// Key should be 8 * 16 = 128-bit
		if (newKey0.length() != 32 || newKey1.length() != 32
				|| (newKey2 != null && newKey2.length() != 32)) {
			return false;
		}
		return true;
	}
	
	/*
	 * Return whether newKey and oldKey are the same or not.
	 */
	private static boolean isSameKeys(String oldKey, String newKey) {
		if (oldKey != null && !oldKey.equals(newKey)) {
			return false;
		} else if (oldKey == null && newKey != null && newKey.length() > 0) {
			return false;
		}
		return true;
	}

	/*
	 * Turns array of bytes into string
	 */
	private static String asHex(byte buf[]) {
		if (buf == null) {
			return null;
		}
		StringBuilder strBuilder = new StringBuilder(buf.length * 2);
		int i;

		for (i = 0; i < buf.length; i++) {
			if (((int) buf[i] & 0xff) < 0x10)
				strBuilder.append("0");

			strBuilder.append(Long.toString((int) buf[i] & 0xff, 16));
		}

		return strBuilder.toString();
	}

	private static byte[] fromHexString(String hexStr) {
		if (hexStr == null) {
			return null;
		}
		int length = hexStr.length();
		if (length % 2 == 1) {
			return null;
		}
		int byteLength = length / 2;
		byte[] raw = new byte[byteLength];
		for (int i = 0; i < byteLength; i++) {
			raw[i] = (byte) Integer.parseInt(hexStr.substring(i + i, i + i + 2), 16);
		}
		return raw;
	}
	
	private static byte[] doAES(int mode, SecretKeySpec key, byte[] bytes) throws Exception {
		Cipher cipher = Cipher.getInstance("AES");
		cipher.init(mode, key);
		byte[] r = cipher.doFinal(bytes);
		return r;
	}

	private static byte[] encryptBytes(byte[] sBytes) {
		if (sBytes == null) {
			return null;
		}
		try {
			return doAES(Cipher.ENCRYPT_MODE, sKeySpec0, sBytes);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	
	private static byte[] decryptBytes(byte[] sBytes) {
		if (sBytes == null || sBytes.length % 16 != 0) {
			return null;
		}
		try {
			return doAES(Cipher.DECRYPT_MODE, sKeySpec1, sBytes);
		} catch (Exception e) {
			SecretKeySpec key2 = sKeySpec2;
			if (key2 != null) {
				try {
					return doAES(Cipher.DECRYPT_MODE, key2, sBytes);
				} catch (Exception e2){
					e2.printStackTrace();
					return null;
				}
			} else {
				e.printStackTrace();
			}
			return null;
		}
	}
	
	public static String encrypt(String s) {
		if (s == null) {
			return null;
		}
		if (!initialized) updateKeys(oldKey0, oldKey1, oldKey2);
		byte[] encrypted = encryptBytes(s.getBytes());
		return encrypted == null ? null : asHex(encrypted); 
	}
	
	public static String decrypt(String s) {
		if (s == null) {
			return null;
		}
		if (!initialized) updateKeys(oldKey0, oldKey1, oldKey2);
		byte[] decrypted = decryptBytes(fromHexString(s));
		return decrypted == null ? null : new String(decrypted);
	}
	
	public static String b64EncryptBytes(byte[] bytes) {
		if (bytes == null) {
			return null;
		}
		if (!initialized) updateKeys(oldKey0, oldKey1, oldKey2);
		byte[] encrypted = encryptBytes(bytes);
		return encrypted == null ? null : Base64.byteArrayToBase64(encrypted); 
	}

	public static byte[] b64DecryptBytes(String s) {
		if (s == null) {
			return null;
		}
		if (!initialized) updateKeys(oldKey0, oldKey1, oldKey2);
		return decryptBytes(Base64.base64ToByteArray(s));
	}

}
