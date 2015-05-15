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

/**
 * Web request call back. Interface is provided for the convenience of class
 * casting and method invoking. Client can use reflection to invoke method
 * got(int responseCode, byte[] responseBytes).
 * 
 * @author zhourenjian
 *
 */
public interface WebCallback {
	
	/**
	 * Be invoked after response is got.
	 * @param responseCode HTTP response code: 200 is OK, 304 is not modified
	 * @param responseBytes
	 */
	public void got(int responseCode, byte[] responseBytes);
	
}