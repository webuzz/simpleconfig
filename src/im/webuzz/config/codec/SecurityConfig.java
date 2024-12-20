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

@ConfigClass
@ConfigComment({
	"To update key A to new key B, following these steps:",
	"0: k0=A, k1=A, k2=null (t=0) // Default",
	"1: k0=A, k1=A, k2=A (t=+1m) // Start updating keys, adding key 2, but still the key A",
	"2: k0=A, k1=B, k2=A (t=+3m) // Add key B as first decrypt key, wait until all servers synchronize key B",
	"3: k0=B, k1=B, k2=A (t=+1d) // Use key B as encrypt key, wait until all servers no longer get session encrypted by key A",
	"4: K0=B, k1=B, k2=null (t=+3d) // Use key B totally, and remove key A for ever",
	"...",
})
public class SecurityConfig {
	
	public static final String configKeyPrefix = "security";
			
	@ConfigComment({
		"The following key values MUST be updated to different keys in production servers:",
		"encrypt key"
	})
	public static String key0 = "5c57deef93c0ffc41837f5aa704a4c78";
	@ConfigComment("decrypt key, same as encrypt key by default")
	public static String key1 = "5c57deef93c0ffc41837f5aa704a4c78";
	@ConfigComment("another decrypt key, or to be new encrypt/decrypt key")
	public static String key2 = null;
	
	
	@ConfigComment({ "Add randomness on encrypting given message or not."})
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
