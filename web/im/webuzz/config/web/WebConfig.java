/*******************************************************************************
 * Copyright (c) 2010 - 2015 java2script.org, webuzz.im and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
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
		"configurationFile", "configurationExtraPath", "supportsMultipleConfigs", "synchronizers", 
		// Following is WebConfig fields
		configKeyPrefix + ".ignoringFields", // ignoring fields can not be updated from remote server
		configKeyPrefix + ".synchronizing", // could not be set to false from remote configuration server. or won't be able to re-synchronizing
		configKeyPrefix + ".localServerName", // changing local server name from remote server makes no sense
	};
	
	public static boolean synchronizing = false;
	
	public static String localServerName = null; // ${local.server.name}

	
	public static String globalServerURLPrefix = null; // ${server.url.prefix} HTTP server URL
	
	public static String globalServerAuthUser = null; // ${server.auth.user}
	
	public static String globalServerAuthPassword = null; // ${server.auth.password}
	
	// Static file pattern: "${server.url.prefix}/configs/${local.server.name}/${config.key.prefix}.ini"
	// Dynamic URL pattern: "${server.url.prefix}/config?server=${local.server.name}&prefix=${config.key.prefix}"
	// Server authorization pattern: "${server.auth.user}:${server.auth.password}"
	public static String targetURLPattern = null;
	
	public static String httpRequestClass = null;

	public static long httpRequestTimeout = 2000; // wait at least 2s before moving to next HTTP request

}
