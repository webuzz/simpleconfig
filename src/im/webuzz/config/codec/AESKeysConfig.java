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

import java.util.Properties;

import im.webuzz.config.annotation.ConfigClass;
import im.webuzz.config.annotation.ConfigComment;
import im.webuzz.config.annotation.ConfigKeyPrefix;

@ConfigClass
@ConfigComment({
	"Key rotation process for updating AES encryption keys:",
	"Step 0: k0=A, k1=A, k2=null (default state)",
	"Step 1: k0=A, k1=A, k2=A (add k2 but keep A as active key)",
	"Step 2: k0=A, k1=B, k2=A (introduce B for decryption, wait for sync)",
	"Step 3: k0=B, k1=B, k2=A (switch to B for encryption, retain A temporarily)",
	"Step 4: k0=B, k1=B, k2=null (fully migrate to B, remove A)",
})
@ConfigKeyPrefix("aeskeys")
public class AESKeysConfig {
	
	@ConfigComment("Primary encryption key (update in production)")
	public static String key0 = "5c57deef93c0ffc41837f5aa704a4c78";

	@ConfigComment("Primary decryption key (defaults to same as key0)")
	public static String key1 = "5c57deef93c0ffc41837f5aa704a4c78";

	@ConfigComment("Secondary decryption key or candidate for new key")
	public static String key2 = null;
	
	@ConfigComment("Add randomness to encrypted messages ([secret:xxxxx])")
	public static boolean generateRandomness = true;
	
	public static void update(Properties prop) {
		if (key0 != null && key1 == null && key2 == null) {
			key1 = key0;
		}
		try {
			SimpleAES.updateKeys(key0, key1, key2);
		} catch (Exception e){
			// for example, keys contains non-hex characters
			System.out.println("[Security] AES Keys Initialization Error!!!");
			e.printStackTrace();
		}
	}

}
