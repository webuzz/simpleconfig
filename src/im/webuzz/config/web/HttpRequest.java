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

package im.webuzz.config.web;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import im.webuzz.config.common.Base64;

/**
 * This class is a Java implementation of browser's XMLHttpRequest object.
 * This class can be considered as a bridge of Java's AJAX programming and
 * JavaScript/Browser's AJAX programming.
 * 
 * @author zhou renjian
 *
 * 2006-2-11
 */
public class HttpRequest {
	
	
	public static ThreadPoolExecutor executor = null;

	protected int status;
	protected String statusText;
	protected int readyState;
	
	protected String responseType;
	protected ByteArrayOutputStream responseBAOS;
	protected byte[] responseBytes;
	//private boolean overrideMimeType;
	protected Callable<Object> loadedCB;
	
	protected boolean asynchronous;
	private HttpURLConnection connection;
	protected String url;
	protected String method;
	protected String user;
	protected String password;
	
	protected Map<String, String> headers = new ConcurrentHashMap<String, String>();
	protected String content;
	
	protected boolean toAbort = false;
	protected boolean isDisconnected = false;
	private OutputStream activeOS;
	private InputStream activeIS;
	
	private final static String[] WEEK_DAYS_ABBREV = new String[] {
		"Sun", "Mon", "Tue", "Wed", "Thu",  "Fri", "Sat"
	};
	
	/**
	 * Return read state of XMLHttpRequest.
	 * @return int ready state
	 */
	public int getReadyState() {
		return readyState;
	}
	/**
	 * Return response raw bytes of XMLHttpRequest 
	 * @return byte[] response bytes. May be null if the request is not sent
	 * or an error happens. 
	 */
	public byte[] getResponseBytes() {
		if (responseBytes == null && responseBAOS != null) {
			responseBytes = responseBAOS.toByteArray();
		}
		return responseBytes;
	}
	/**
	 * Return response code.
	 * @return int response code. For more information please read about
	 * HTTP protocol.
	 */
	public int getStatus() {
		return status;
	}
	/**
	 * Return response code related text.
	 * @return int response code. For more information please read about
	 * HTTP protocol.
	 */
	public String getStatusText() {
		return statusText;
	}
	public void registerOnLoaded(Callable<Object> onLoaded) {
		this.loadedCB = onLoaded;
	}
	/**
	 * Set request header with given key and value.
	 * @param key String request header keyword. For more information please 
	 * read about HTTP protocol.
	 * @param value String request header value
	 */
	public void setRequestHeader(String key, String value) {
		headers.put(key, value);
	}
	/**
	 * Get all response headers.
	 * @return String the all response header value.
	 */
	public String getAllResponseHeaders() {
		StringBuilder builder = new StringBuilder();
		int i = 1;
		while (true) {
			String key = connection.getHeaderFieldKey(i);
			if (key != null) {
				String value = connection.getHeaderField(i); 
				builder.append(key);
				builder.append(": ");
				builder.append(value);
				builder.append("\r\n");
			} else {
				break;
			}
			i++;
		}
		builder.append("\r\n");
		return builder.toString();
	}
	/**
	 * Get response header with given key.
	 * @param key String header keyword. For more information please 
	 * read about HTTP protocol.
	 * @return String the response header value.
	 */
	public String getResponseHeader(String key) {
		if (connection == null) return null;
		Map<String, List<String>> headerFields = connection.getHeaderFields();
		List<String> list = headerFields.get(key);
		if (list == null) {
			return null;
		}
		if (list.size() == 0) {
			return "";
		}
		String headerValue = null;
		for (Iterator<String> itr = list.iterator(); itr.hasNext();) {
			String value = (String) itr.next();
	 		if (value != null) {
	 			if (headerValue == null) {
	 				headerValue = value;
	 			} else {
	 				headerValue = value + "\r\n" + headerValue;
	 			}
	 		}
		}
		return headerValue; // may have multiple Set-Cookie headers 
		// return connection.getHeaderField(key);
	}
	/**
	 * Open connection for HTTP request with given method and URL 
	 * synchronously.
	 * 
	 * @param method String "POST" or "GET" usually.
	 * @param url String remote URL. Should always be absolute URL.
	 */
	public void open(String method, String url) {
		open(method, url, false, null, null);
	}
	/**
	 * Open connection for HTTP request with given method, URL and mode.
	 * 
	 * @param method String "POST" or "GET" usually.
	 * @param url String remote URL. Should always be absolute URL.
	 * @param async boolean whether send request asynchronously or not. 
	 */
	public void open(String method, String url, boolean async) {
		open(method, url, async, null, null);
	}
	/**
	 * Open connection for HTTP request with given method, URL and mode.
	 * 
	 * @param method String "POST" or "GET" usually.
	 * @param url String remote URL. Should always be absolute URL.
	 * @param async boolean whether send request asynchronously or not. 
	 * @param user String user name
	 */
	public void open(String method, String url, boolean async, String user) {
		open(method, url, async, user, null);
	}
	/**
	 * Open connection for HTTP request with given method, URL and mode.
	 * 
	 * @param method String "POST" or "GET" usually.
	 * @param url String remote URL. Should always be absolute URL.
	 * @param async boolean whether send request asynchronously or not.
	 * @param user String user name
	 * @param password String user password 
	 */
	public void open(String method, String url, boolean async, String user, String password) {
		this.asynchronous = async;
		this.method = method;
		this.url = url;
		this.user = user;
		this.password = password;
		responseType = null;
		responseBAOS = null;
		responseBytes = null;
		readyState = 1;
		status = 0; // default OK
		statusText = null;
		toAbort = false;
//		if (onreadystatechange != null) {
//			onreadystatechange.onOpen();
//		}
	}
	
	public static void runTask(Runnable task) {
		if (executor == null) {
			synchronized (HttpRequest.class) {
				if (executor == null) {
					executor = new ThreadPoolExecutor(HttpConnectionConfig.webCoreWorkers, HttpConnectionConfig.webMaxWorkers, HttpConnectionConfig.webWorkerIdleInterval,
							TimeUnit.SECONDS, new SynchronousQueue<Runnable>(), new ThreadFactory() {
						@Override
						public Thread newThread(Runnable r) {
							return new Thread(r, "Web Watchman Worker");
						}
					});
				}
			}
		}
		executor.execute(task);
	}
	
	/**
	 * Send the HTTP request without extra content.
	 */
	public void send() {
		send(null);
	}
	/**
	 * Send the HTTP request with given content.
	 * @param str String HTTP request content. May be null.
	 */
	public void send(String str) {
		content = str;
		if (asynchronous) {
			runTask(new Runnable() {
				public void run() {
					if (!toAbort) {
						request();
					}
				}
			});
		} else {
			request();
		}
	}
	/**
	 * Abort the sending or receiving data process.
	 */
	public void abort() {
		toAbort = true;
		isDisconnected = false;
		checkAbort();
	}
	
	protected boolean checkAbort() {
		if (!toAbort) return false;
		if (activeOS != null) {
			try {
				activeOS.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			activeOS = null;
		}
		if (activeIS != null) {
			try {
				activeIS.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			activeIS = null;
		}
		if (!isDisconnected && connection != null) {
			connection.disconnect();
			isDisconnected = true;
		}
		return true;
	}
	/*
	 * Try setup the real connection and send the request over HTTP protocol. 
	 */
	private void request() {
		try {
			connection = (HttpURLConnection) new URL(url).openConnection();
			connection.setConnectTimeout(30000); // 30s
			connection.setReadTimeout(30000); // 30s
			connection.setInstanceFollowRedirects(false);
			connection.setDoInput(true);
			connection.setRequestMethod(method);
			connection.setRequestProperty("User-Agent", HttpConnectionConfig.userAgent);
			if ("POST".equalsIgnoreCase(method)) {
				connection.setDoOutput(true);
				connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
			}
			if (user != null) {
				String auth = user + ":" + (password != null ? password : "");
				String base64Auth = Base64.byteArrayToBase64(auth.getBytes());
				connection.setRequestProperty("Authorization", "Basic " + base64Auth);
			}
			for (Iterator<String> iter = headers.keySet().iterator(); iter.hasNext();) {
				String key = (String) iter.next();
				connection.setRequestProperty(key, (String) headers.get(key));
			}
			connection.setUseCaches(false);
			if (checkAbort()) return; // not yet send out a byte
			if ("post".equalsIgnoreCase(method)) {
				DataOutputStream dos = new DataOutputStream(connection.getOutputStream());
				activeOS = dos;
				if (content != null) {
					dos.writeBytes(content);
				}
				if (checkAbort()) return; // do not flush anything and close the connection
				dos.flush();
				dos.close();
				activeOS = null;
			}
			if (checkAbort()) return; // just disconnect without receiving anything
			InputStream is = null;
			try {
				is = connection.getInputStream();
			} catch (IOException e) {
				if (checkAbort()) return; // exception caused by abort action
				status = connection.getResponseCode();
				if (status != 404) {
					e.printStackTrace();
				}
				readyState = 4;
				if (loadedCB != null) {
					try {
						loadedCB.call();
					} catch (Exception e1) {
						e1.printStackTrace();
					}
					loadedCB = null;
				}
				connection = null;
				readyState = 0;
				return;
			}
			activeIS = is;
			
			if (readyState < 2) {
				readyState = 2;
				status = connection.getResponseCode();
				statusText = connection.getResponseMessage();
			}
			
			int bufferSize = connection.getContentLength();
			if (bufferSize <= 0) {
				bufferSize = 10240;
			} else if (bufferSize > 512 * 1024) {
				bufferSize = 512 * 1024; // buffer increases by 512k
			}
			ByteArrayOutputStream baos = new ByteArrayOutputStream(bufferSize);
			responseBAOS = baos;
			byte[] buffer = new byte[Math.min(bufferSize, 10240)];
			int read;
			while (!toAbort && (read = is.read(buffer)) != -1) {
				if (checkAbort()) return; // stop receiving anything
				if (readyState != 3) {
					readyState = 3;
				}
				baos.write(buffer, 0, read);
			}
			if (checkAbort()) return; // stop receiving anything
			is.close();
			activeIS = null;
			responseType = connection.getHeaderField("Content-Type");
			readyState = 4;
			if (loadedCB != null) {
				try {
					loadedCB.call();
				} catch (Exception e1) {
					e1.printStackTrace();
				}
				loadedCB = null;
			}
			connection.disconnect();
			readyState = 0;
		} catch (Exception e) {
			if (checkAbort()) return; // exception caused by abort action
			e.printStackTrace();
			readyState = 4;
			if (loadedCB != null) {
				try {
					loadedCB.call();
				} catch (Exception e1) {
					e1.printStackTrace();
				}
				loadedCB = null;
			}
			connection = null;
			readyState = 0;
		}
	}
	
	public static String calculateMD5ETag(byte[] bytes) {
		if (bytes == null) return null;
		MessageDigest mdAlgorithm = null;
		try {
			mdAlgorithm = MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException e1) {
			e1.printStackTrace();
		}
		if (mdAlgorithm != null) {
			mdAlgorithm.update(bytes);
			byte[] digest = mdAlgorithm.digest();
			StringBuilder eTag = new StringBuilder();
			eTag.append("\"");
			for (int i = 0; i < digest.length; i++) {
				String plainText = Integer.toHexString(0xFF & digest[i]);
				if (plainText.length() < 2) {
					plainText = "0" + plainText;
				}
				eTag.append(plainText);
			}
			eTag.append("\"");
			return eTag.toString();
		}
		return null;
	}
	
	@SuppressWarnings("deprecation")
	public static String getHTTPDateString(long time) {
		if (time < 0) {
			time = System.currentTimeMillis();
		}
		Date date = new Date(time);
		return WEEK_DAYS_ABBREV[date.getDay()] + ", " + date.toGMTString();
	}
	

	public static long parseHeaderTimestamp(HttpRequest req, String headerName) {
		long lastModified = -1;
		String modifiedStr = req.getResponseHeader(headerName);
		if (modifiedStr != null && modifiedStr.length() > 0) {
			SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");
			try {
				Date d = format.parse(modifiedStr);
				if (d != null) {
					lastModified = d.getTime();
				}
			} catch (ParseException e) {
				e.printStackTrace();
			}
		}
		if (lastModified == -1) {
			lastModified = System.currentTimeMillis();
		}
		return lastModified;
	}

}
