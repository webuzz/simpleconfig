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

package im.webuzz.config.web;

import im.webuzz.config.ConfigComment;
import im.webuzz.config.ConfigSecret;

@ConfigComment({
	"This configuration file is used to be configured for synchronization between",
	"local and remote server. The remote server is considered as configuration center.",
})
public class WebConfig {

	public static final String configKeyPrefix = "webconfig";
	
	@ConfigComment({
		"Global configuration center URL.",
		"Marked as ${server.url.prefix} in {@link #targetURLPattern}. Usually it is", 
		"an HTTP server URL.",
	})
	public static String globalServerURLPrefix = null; 
	
	@ConfigComment({
		"Global configuration center should always be protected with user authorization.",
		"Marked as ${server.auth.user} in {@value #targetURLPattern}",
	})
	public static String globalServerAuthUser = null;
	
	@ConfigComment({
		"Marked as ${server.auth.password} in {@link #targetURLPattern}",
	})
	@ConfigSecret
	public static String globalServerAuthPassword = null;
	
	@ConfigComment({
		"Target URL with template support.",
		"",
		"Static file pattern: \"${server.url.prefix}/configs/${local.server.name}/${config.key.prefix}.ini\"",
		"Static file pattern: \"${server.url.prefix}/configs/${local.server.name}/${config.key.prefix}${config.file.extension}\"",
		"Dynamic URL pattern: \"${server.url.prefix}/config?server=${local.server.name}&prefix=${config.key.prefix}\"",
		"Server authorization pattern: \"${server.auth.user}:${server.auth.password}\"",
	})
	public static String targetURLPattern = null;
	
	@ConfigComment({
		"Web request client is a class with static method asyncWebRequest(String url, String user, String password,",
		"long lastModified, Object callback). Object callback has a method got(int responseCode, byte[] responseBytes).",
		"Object callback is an instance of interface WebCallback.",
		"",
		"Client class is provided to override the default HTTP request client. In such ways, other clients (like",
		"FTP client, HTTP 2.0 client or other socket client) can be used to fetch remote configuration file.",
	})
	public static String webRequestClient = null;

	@ConfigComment({
		"Try to request configuration one by one only put request to background if timeout is reached.",
		"By default, wait at least 2s before moving to next request.",
	})
	public static long webRequestTimeout = 2000;

	@ConfigComment({
		"Interval of checking web remote server for configuration file update.",
	})
	public static long webRequestInterval = 10000;

	@ConfigComment({
		"Supports MD5 ETag for HTTP request or not.",
	})
	public static boolean webRequestSupportsMD5ETag = true;

	public static int webCoreWorkers = 1;
	
	public static int webMaxWorkers = 50; // 50 for configuration web synchronizing is considered as enough
	
	public static int webWorkerIdleInterval = 30;
	
	@ConfigComment({
		"Try to synchronize other resource files from remote server.",
	})
	public static String[] extraResourceFiles = null;

	@ConfigComment({
		"Target URL with template support.",
		"",
		"Static file pattern: \"${server.url.prefix}/configs/${local.server.name}/${extra.file.path}\"",
		"Dynamic URL pattern: \"${server.url.prefix}/config?server=${local.server.name}&path=${extra.file.path}\"",
		"Server authorization pattern: \"${server.auth.user}:${server.auth.password}\"",
	})
	public static String extraTargetURLPattern = null;

	
	public static String timestampFilePath = null;

	@ConfigComment({
		"Block #startWatchman until local files are considered as synchronized.",
		"If local files are synchronized, there will be a *.timestamp file contains the last synchronized timestamp.",
	})
	public static boolean blockingBeforeSynchronized = false;

	@ConfigComment({
		"If local files are older than synchronizedExpringInterval, and blockingBeforeSynchronized is true, then",
		"#startWatchman will be blocked until being synchronized.",
	})
	public static long synchronizedExpiringInterval = 8 * 3600 * 1000; // 8 hours
	
}
