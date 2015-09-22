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


public class WebConfig {

	public static final String configKeyPrefix = "webconfig";
	
	/**
	 * If local fields should be kept and remote configuration should not be synchronized, put them in this array
	 */
	public static String[] ignoringFields = new String[] {
		// Config.class kernel fields should only be changed or updated locally
		"configurationFile",
		"configurationFileExtension",
		"configurationFolder",
		"configurationMultipleFiles",
		"configurationWatchmen", 
		// Following is WebConfig fields
		configKeyPrefix + ".ignoringFields", // ignoring fields can not be updated from remote server
		configKeyPrefix + ".synchronizing", // could not be set to false from remote configuration server. or won't be able to re-synchronizing
		configKeyPrefix + ".localServerName", // changing local server name from remote server makes no sense
		configKeyPrefix + ".extraResourceExtensions", // resource extensions is limited to given extensions
	};
	
	/**
	 * Whether start synchronizing configuration files from remote server or not.
	 * Synchronization can be turned on or off at any time. 
	 */
	public static boolean synchronizing = false;
	
	/**
	 * Local server name which is used to tell configuration center who is requesting configurations.
	 * Marked as ${local.server.name} in {@link #targetURLPattern}
	 */
	public static String localServerName = null;

	/**
	 * Global configuration center URL.
	 * Marked as ${server.url.prefix} in {@link #targetURLPattern}. Usually it is 
	 * an HTTP server URL.
	 */
	public static String globalServerURLPrefix = null; 
	
	/**
	 * Global configuration center should always be protected with user authorization.
	 * Marked as ${server.auth.user} in {@value #targetURLPattern}
	 */
	public static String globalServerAuthUser = null;
	
	/**
	 * Marked as ${server.auth.password} in {@link #targetURLPattern}
	 */
	public static String globalServerAuthPassword = null;
	
	/**
	 * Target URL with template support.
	 * 
	 * Static file pattern: "${server.url.prefix}/configs/${local.server.name}/${config.key.prefix}.ini"
	 * Static file pattern: "${server.url.prefix}/configs/${local.server.name}/${config.key.prefix}${config.file.extension}"
	 * Dynamic URL pattern: "${server.url.prefix}/config?server=${local.server.name}&prefix=${config.key.prefix}"
	 * Server authorization pattern: "${server.auth.user}:${server.auth.password}"
	 */
	public static String targetURLPattern = null;
	
	/**
	 * Web request client is a class with static method asyncWebRequest(String url, String user, String password,
	 * long lastModified, Object callback). Object callback has a method got(int responseCode, byte[] responseBytes).
	 * Object callback is an instance of interface WebCallback.
	 * 
	 * Client class is provided to override the default HTTP request client. In such ways, other clients (like
	 * FTP client, HTTP 2.0 client or other socket client) can be used to fetch remote configuration file.
	 */
	public static String webRequestClient = null;

	/**
	 * Try to request configuration one by one only put request to background if timeout is reached.
	 * By default, wait at least 2s before moving to next request.
	 */
	public static long webRequestTimeout = 2000;

	/**
	 * Interval of checking web remote server for configuration file update.
	 */
	public static long webRequestInterval = 10000;

	/**
	 * Supports MD5 ETag for HTTP request or not.
	 */
	public static boolean webRequestSupportsMD5ETag = true;

	public static int webCoreWorkers = 1;
	
	public static int webMaxWorkers = 50; // 50 for configuration web synchronizing is considered as enough
	
	public static int webWorkerIdleInterval = 30;
	
	/**
	 * Only allows specified extensions. Ignore others extension file.
	 */
	public static String[] extraResourceExtensions = new String[] {
		".xml", ".properties", ".props", ".ini", ".txt", ".config", ".conf", ".cfg", ".js", ".json",
		".key", ".crt", ".pem", ".keystore", // HTTPS
		".html", ".htm", ".css", // web pages
	};

	/**
	 * Try to synchronize other resource files from remote server.
	 */
	public static String[] extraResourceFiles = null;

	/**
	 * Target URL with template support.
	 * 
	 * Static file pattern: "${server.url.prefix}/configs/${local.server.name}/${extra.file.path}"
	 * Dynamic URL pattern: "${server.url.prefix}/config?server=${local.server.name}&path=${extra.file.path}"
	 * Server authorization pattern: "${server.auth.user}:${server.auth.password}"
	 */
	public static String extraTargetURLPattern = null;

	/**
	 * Block #startWatchman until local files are considered as synchronized.
	 * If local files are synchronized, there will be a *.timestamp file contains the last synchronized time stamp.
	 */
	public static boolean blockingBeforeSynchronized = false;

	/**
	 * If local files are older than synchronizedExpringInterval, and blockingBeforeSynchronized is true, then
	 * #startWatchman will be blocked until being synchronized.
	 */
	public static long synchronizedExpiringInterval = 8 * 3600 * 1000; // 8 hours
	
}
