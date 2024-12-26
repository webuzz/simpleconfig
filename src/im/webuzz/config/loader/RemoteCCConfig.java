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
import im.webuzz.config.annotation.ConfigComment;
import im.webuzz.config.annotation.ConfigKeyPrefix;
import im.webuzz.config.annotation.ConfigNonNegative;
import im.webuzz.config.annotation.ConfigNotEmpty;
import im.webuzz.config.annotation.ConfigNotNull;
import im.webuzz.config.annotation.ConfigPattern;

@ConfigClass
@ConfigComment("Configuration for synchronizing local and remote servers. The remote server acts as the configuration center.")
@ConfigKeyPrefix("remotecc")
public class RemoteCCConfig {

	@ConfigComment("Base URL of the global configuration center (e.g., HTTP server URL).")
	public static String globalServerURLPrefix = null;

	@ConfigComment("Username for accessing the global configuration center.")
	public static String globalServerAuthUser = null;

	@ConfigComment("Password for accessing the global configuration center. Encrypted by default.")
	@ConfigPreferredCodec(value = {"secret", "aes"})
	public static String globalServerAuthPassword = null;

	@ConfigComment("Local server name for identifying requests at the configuration center.")
	@ConfigNotNull
	@ConfigNotEmpty
	public static String localServerName = "app";


	@ConfigComment("Port number of the local server.")
	public static int localServerPort = 0;

	@ConfigComment("Template for generating the target configuration URL.")
	@ConfigNotNull
	@ConfigNotEmpty
	public static String targetURLPattern = "${server.url.prefix}/${local.server.name}/${config.key.prefix}${config.file.extension}";

	@ConfigComment("Class providing a custom web request client (e.g., for HTTP, FTP, or other protocols).")
	public static String webRequestClient = null;


	@ConfigComment("Timeout (in milliseconds) for each configuration request.")
	public static long webRequestTimeout = 2000;

	@ConfigComment("Interval (in milliseconds) between checks for remote configuration updates.")
	public static long webRequestInterval = 10000;

	@ConfigComment("Enable MD5-based ETag support for HTTP requests.")
	public static boolean webRequestSupportsMD5ETag = true;

	@ConfigComment("Allowed file extensions for synchronized resources. Blocks harmful extensions like .exe or .sh.")
	@ConfigNotNull
	@ConfigNotNull(depth = 1)
	@ConfigNotEmpty
	@ConfigPattern("(\\.[a-zA-Z0-9]+)")
	public static String[] extraResourceExtensions = new String[] {
		".xml", ".properties", ".props", ".ini", ".txt", ".config", ".conf", ".cfg", ".js", ".json",
		".key", ".crt", ".pem", ".keystore", // HTTPS
		".html", ".htm", ".css", // web pages
	};

	@ConfigComment("Additional resource files to synchronize from the remote server.")
	public static String[] extraResourceFiles = null;

	@ConfigComment("Template for generating target URLs for extra resource files.")
	@ConfigNotNull
	@ConfigNotEmpty
	public static String extraTargetURLPattern = "${server.url.prefix}/${local.server.name}/${extra.file.path}";

	@ConfigComment("Block execution until local files are synchronized (based on *.timestamp).")
	public static boolean blockingBeforeSynchronized = false;

	@ConfigComment("Interval (in milliseconds) to consider local files outdated. Requires synchronization if expired.")
	@ConfigNonNegative
	public static long synchronizedExpiringInterval = 8 * 3600 * 1000; // 8 hours

	@ConfigComment("Enable or disable synchronization with the remote server.")
	public static boolean synchronizing = false;

}
