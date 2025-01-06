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

package im.webuzz.config.loader;

import im.webuzz.config.annotation.ConfigClass;
import im.webuzz.config.annotation.ConfigPreferredCodec;
import im.webuzz.config.web.ConfigHttpRequest;
import im.webuzz.config.web.ConfigWebClient;
import im.webuzz.config.annotation.ConfigComment;
import im.webuzz.config.annotation.ConfigKeyPrefix;
import im.webuzz.config.annotation.ConfigNotEmpty;
import im.webuzz.config.annotation.ConfigNotNull;
import im.webuzz.config.annotation.ConfigPattern;
import im.webuzz.config.annotation.ConfigPositive;

@ConfigClass
@ConfigComment("Configuration for synchronizing local and remote servers. The remote server acts as the configuration center.")
@ConfigKeyPrefix("remotecc")
public class RemoteCCConfig {

	@ConfigComment("A custom web request client (e.g., for HTTP, FTP, or other protocols).")
	@ConfigNotNull
	public static ConfigWebClient webClient = new ConfigHttpRequest();

	
	@ConfigComment("Base URL of the global configuration center (e.g., HTTP server URL).")
	@ConfigNotEmpty
	public static String globalServerURLPrefix = "http://127.0.0.1:8080";

	@ConfigComment("Username for accessing the global configuration center.")
	public static String globalServerAuthUser = null;

	@ConfigComment("Password for accessing the global configuration center. Encrypted by default.")
	@ConfigPreferredCodec(value = {"secret", "aes"})
	public static String globalServerAuthPassword = null;

	
	@ConfigComment("Local server name for identifying requests at the configuration center.")
	@ConfigNotEmpty
	public static String localServerName = "app";

	@ConfigComment("Port number of the local server.")
	public static int localServerPort = 0;

	
	@ConfigComment("Template for generating the target configuration URL.")
	@ConfigNotEmpty
	public static String targetURLPattern = "${server.url.prefix}/${local.server.name}/${config.key.prefix}${config.file.extension}";

	@ConfigComment("Timeout (in milliseconds) for each configuration request.")
	@ConfigPositive
	public static long webRequestTimeout = 2000;

	@ConfigComment("Interval (in milliseconds) between checks for remote configuration updates.")
	@ConfigPositive
	public static long webRequestInterval = 10000;

	@ConfigComment("Enable MD5-based ETag support for HTTP requests.")
	public static boolean webRequestSupportsMD5ETag = true;

	
	@ConfigComment("Allowed file extensions for synchronized resources. Blocks harmful extensions like .exe or .sh.")
	@ConfigNotEmpty
	@ConfigPattern("(\\.[a-zA-Z0-9]+)")
	public static String[] extraResourceExtensions = new String[] {
		".xml", ".properties", ".props", ".ini", ".txt", ".config", ".conf", ".cfg", ".js", ".json",
		".key", ".crt", ".pem", ".keystore", // HTTPS
		".html", ".htm", ".css", // web pages
	};

	@ConfigComment("Additional resource files to synchronize from the remote server.")
	@ConfigNotEmpty(depth = 1)
	public static String[] extraResourceFiles = null;

	@ConfigComment("Template for generating target URLs for extra resource files.")
	@ConfigNotEmpty
	public static String extraTargetURLPattern = "${server.url.prefix}/${local.server.name}/${extra.file.path}";

}
