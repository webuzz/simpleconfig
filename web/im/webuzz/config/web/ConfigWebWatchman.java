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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import im.webuzz.config.Config;
import im.webuzz.config.ConfigMerger;

/**
 * Synchronize configuration files from given server to local file system.
 */
public class ConfigWebWatchman implements Runnable {

	private static boolean running = false;
	
	private static BlockingQueue<Class<?>> queue = new LinkedBlockingQueue<Class<?>>();

	private final static String[] WEEK_DAYS_ABBREV = new String[] {
		"Sun", "Mon", "Tue", "Wed", "Thu",  "Fri", "Sat"
	};
	
	static ThreadPoolExecutor executor = null;
	
	static ConfigWebWatchman defaultWatchman = null;
	
	@SuppressWarnings("deprecation")
	protected static String getHTTPDateString(long time) {
		if (time < 0) {
			time = System.currentTimeMillis();
		}
		Date date = new Date(time);
		return WEEK_DAYS_ABBREV[date.getDay()] + ", " + date.toGMTString();
	}
	
	protected static byte[] readFile(File file) {
		FileInputStream fis = null;
		byte[] buffer = new byte[8096];
		int read = -1;
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			fis = new FileInputStream(file);
			while ((read = fis.read(buffer)) != -1) {
				baos.write(buffer, 0, read);
			}
		} catch (IOException e1) {
			//e1.printStackTrace();
		} finally {
			if (fis != null) {
				try {
					fis.close();
				} catch (IOException e) {
					//e.printStackTrace();
				}
			}
		}
		return baos.toByteArray();
	}
	
	protected static String getFileMD5ETag(File file) {
		if (!WebConfig.webRequestSupportsMD5ETag) {
			return null;
		}
		byte[] bytes = readFile(file);
		if (bytes == null) {
			return null;
		}
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
	
	private long currentRequestTime = 0;
	private Map<String, Integer> inQueueRequests = new ConcurrentHashMap<String, Integer>();
	private boolean loopMode = true;
	
	private long latestModified = -1;
	
	@Override
	public void run() {
		while (running) {
			try {
				int seconds = Math.max(1, (int) (WebConfig.webRequestInterval / 1000));
				for (int i = 0; i < seconds; i++) {
					Class<?> clazz = null;
					try {
						clazz = queue.poll(1000, TimeUnit.MILLISECONDS);
						//Thread.sleep(1000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					if (!running) {
						break;
					}
					if (clazz != null) {
						synchronizeClass(clazz, WebConfig.webRequestTimeout);								
						i = i > seconds / 2 ? Math.max(seconds / 2 - 2, 1) : 0; // restart sleep waiting
					}
				}
				refreshAll(false, WebConfig.webRequestTimeout);
				if (!WebConfig.synchronizing) {
					continue;
				}
			} catch (Throwable e) {
				// might be OOM
				try {
					Thread.sleep(10);
				} catch (InterruptedException e1) {
				}
			}
		}
	}
	
	void refreshAll(boolean firstRefresh, long timeout) {
		currentRequestTime = System.currentTimeMillis();
		if (firstRefresh) {
			inQueueRequests.clear();
		}
		if (running && WebConfig.targetURLPattern != null) {
			String cfgPath = Config.configurationFile;
			if (cfgPath != null) {
				File cfgFile = new File(cfgPath);
				String cfgName = cfgFile.getName();
				String fileExt = Config.configurationFileExtension;
				if (cfgName.endsWith(fileExt)) {
					cfgName = cfgName.substring(0, cfgName.length() - fileExt.length());
				} else {
					int idx = cfgName.lastIndexOf('.');
					if (idx != -1) {
						cfgName = cfgName.substring(0, idx);
						fileExt = cfgName.substring(idx);
					}
				}
				latestModified = Math.max(latestModified, cfgFile.lastModified());
				synchronizeFile(cfgName, fileExt, cfgFile, true, null, timeout);
			}
			
			Class<?>[] configs = Config.getAllConfigurations();
			for (int i = 0; i < configs.length; i++) {
				synchronizeClass(configs[i], timeout);
			}
		}
		String[] extraFiles = WebConfig.extraResourceFiles;
		if (running && WebConfig.extraTargetURLPattern != null && extraFiles != null) {
			String[] extraExts = WebConfig.extraResourceExtensions;
			for (String path : extraFiles) {
				if (path == null || path.length() == 0) {
					continue;
				}
				path = Config.parseFilePath(path);
				if (extraExts != null && extraExts.length > 0) {
					boolean matched = false;
					for (String extraExt : extraExts) {
						if (extraExt == null || extraExt.length() == 0) {
							continue;
						}
						if (path.endsWith(extraExt)) {
							matched = true;
							break;
						}
					}
					if (!matched) {
						if (Config.configurationLogging) {
							System.out.println("[Config] Resource file " + path + " is skipped as its extension is not permitted.");
						}
						continue;
					}
				}
				String folder = Config.configurationFolder;
				if (folder == null) {
					folder = Config.configurationFile;
					File folderFile = new File(folder);
					if (folderFile.isFile() || !folderFile.exists() || folder.endsWith(Config.configurationFileExtension)) {
						folder = folderFile.getParent();
					}
				}
				File resFile = new File(folder, path);
				latestModified = Math.max(latestModified, resFile.lastModified());
				synchronizeFile(null, null, resFile, false, path, timeout);
			}
		}
		if (running && inQueueRequests.isEmpty()) {
			String cfgPath = Config.configurationFile;
			if (cfgPath != null) {
				File file = new File(cfgPath + ".timestamp");
				FileOutputStream fos = null;
				try {
					fos = new FileOutputStream(file);
					fos.write(String.valueOf(System.currentTimeMillis()).getBytes());
				} catch (IOException e) {
					e.printStackTrace();
				} finally {
					if (fos != null) {
						try {
							fos.close();
						} catch (IOException e) {
							e.printStackTrace();
						}
						fos = null;
					}
				}
			}
		}
	}
	
	void synchronizeClass(Class<?> clz, long timeout) {
		String keyPrefix = Config.getKeyPrefix(clz);
		if (keyPrefix == null || keyPrefix.length() == 0) {
			return;
		}
		String folder = Config.configurationFolder;
		if (folder == null) {
			folder = Config.configurationFile;
			File folderFile = new File(folder);
			if (folderFile.isFile() || !folderFile.exists() || folder.endsWith(Config.configurationFileExtension)) {
				folder = folderFile.getParent();
			}
		}
		boolean existed = false;
		File file = null;
		String extension = null;
		String[] exts = Config.configurationScanningExtensions;
		if (exts != null) {
			for (String ext : exts) {
				if (ext != null && ext.length() > 0 && ext.charAt(0) == '.') {
					file = new File(folder, Config.parseFilePath(keyPrefix + ext));
					if (file.exists()) {
						extension = ext;
						existed = true;
						break;
					}
				}
			}
		}
		if (!existed) {
			extension = Config.configurationFileExtension;
			file = new File(folder, Config.parseFilePath(keyPrefix + extension));
		}
		latestModified = Math.max(latestModified, file.lastModified());
		synchronizeFile(keyPrefix, extension, file, false, null, timeout);
	}
	
	void synchronizeFile(final String keyPrefix, final String fileExtension, final File file,
			final boolean globalConfig, final String extraPath, final long timeout) {
		String url = extraPath == null ? WebConfig.targetURLPattern : WebConfig.extraTargetURLPattern;
		if (url == null) {
			return;
		}
		if (extraPath == null) { // configurations
			url = url.replaceAll("\\$\\{config.key.prefix\\}", keyPrefix);
			if (fileExtension != null && fileExtension.length() > 0
					&& url.indexOf("${config.file.extension}") != -1) {
				url = url.replaceAll("\\$\\{config.file.extension\\}", fileExtension);
			}
		} else { // resource files
			url = url.replaceAll("\\$\\{extra.file.path\\}", extraPath);
		}
		String server = WebConfig.globalServerURLPrefix;
		String user = WebConfig.globalServerAuthUser;
		String password = Config.parseSecret(WebConfig.globalServerAuthPassword);
		String localName = WebConfig.localServerName;
		if (server != null) {
			url = url.replaceAll("\\$\\{server.url.prefix\\}", server);
		}
		if (localName != null && url.indexOf("${local.server.name}") != -1) {
			url = url.replaceAll("\\$\\{local.server.name\\}", localName);
		}
		boolean userInURL = false;
		if (user != null && url.indexOf("${server.auth.user}") != -1) {
			url = url.replaceAll("\\$\\{server.auth.user\\}", user);
			userInURL = true;
		}
		boolean passwordInURL = false;
		if (password != null && url.indexOf("${server.auth.password}") != -1) {
			url = url.replaceAll("\\$\\{server.auth.password\\}", password);
			passwordInURL = true;
		}
		if (userInURL && passwordInURL) {
			user = null;
			password = null;
		}
		final StringBuilder builder = new StringBuilder();
		final long requestTime = currentRequestTime;
		final String requestURL = url;
		if (!loopMode) { // #startWatchman invokes once
			Integer count = inQueueRequests.get(url);
			if (count == null) {
				inQueueRequests.put(url, Integer.valueOf(1));
			} else {
				inQueueRequests.put(url, Integer.valueOf(count.intValue() + 1));
			}
		}
		WebCallback callback = new WebCallback() {
			
			@Override
			public void got(int responseCode, byte[] responseBytes, long lastModified) {
				if (!loopMode) { // #startWatchman invokes once
					if (responseCode != 0) {
						if (responseCode == 304 || responseCode == 200 || responseCode == 404) {
							inQueueRequests.remove(requestURL);
						}
					} else {
						boolean failed = false;
						Integer count = inQueueRequests.get(requestURL);
						if (count != null) {
							failed = count.intValue() > 3;
						}
						if (!failed) {
							// It is rare to reach this branch, use thread directly instead of executor
							Thread thread = new Thread(new Runnable() {
								
								@Override
								public void run() {
									try {
										Thread.sleep(10);
									} catch (InterruptedException e) {
									}
									synchronizeFile(keyPrefix, fileExtension, file, globalConfig, extraPath, timeout);
								}
								
							}, "SOMA Web Watchman Worker");
							thread.setDaemon(true);
							thread.start();
						} else {
							System.out.println("Failed to load " + requestURL + " for " + count + " times.");
						}
					}
				}
				if (responseCode == 200 && requestTime == currentRequestTime) { // HTTP OK
					if (responseBytes != null && responseBytes.length > 0) {
						byte[] localBytes = readFile(file);
						if (extraPath == null) {
							String[] ignoringFields = WebConfig.ignoringFields;
							if (ignoringFields != null && ignoringFields.length > 0
									// TODO: Skip all extensions which need conversions
									&& (fileExtension == null || !fileExtension.startsWith(".js") || !fileExtension.startsWith(".xml"))) {
								responseBytes = ConfigMerger.mergeWithIgnoringFields(responseBytes, localBytes,
										globalConfig ? null : keyPrefix, ignoringFields);
							}
						}
						if (!Arrays.equals(responseBytes, localBytes)) {
							File folderFile = file.getParentFile();
							if (!folderFile.exists()) {
								folderFile.mkdirs();
							}
							FileOutputStream fos = null;
							try {
								fos = new FileOutputStream(file);
								fos.write(responseBytes);
							} catch (IOException e) {
								e.printStackTrace();
							} finally {
								if (fos != null) {
									try {
										fos.close();
									} catch (IOException e) {
										e.printStackTrace();
									}
									fos = null;
									file.setLastModified(lastModified);
								}
							}
							if (Config.configurationLogging) {
								System.out.println("[Config] Configuration file " + file.getAbsolutePath() + " content synchronized remotely.");
							}
						} else {
							if (WebConfig.synchronizing) {
								file.setLastModified(System.currentTimeMillis());
								if (Config.configurationLogging) {
									System.out.println("[Config] Configuration file " + file.getAbsolutePath() + " last modified time synchronized remotely.");
								}
							}
						}
					} else {
						if (Config.configurationLogging) {
							System.out.println("[Config] Fetching configuration file " + file.getAbsolutePath() + " has no content.");
						}
					}
				} else if (responseCode != 304) {
					if (Config.configurationLogging) {
						System.out.println("[Config] Fetching configuration file " + file.getAbsolutePath() + " code=" + responseCode + ".");
					}
				}
				synchronized (builder) {
					builder.append(1);
					builder.notify();
				}
			}
		};
		sendWebRequest(url, user, password, file.exists() ? file.lastModified() : -1, getFileMD5ETag(file), callback);
		
		try {
			synchronized (builder) {
				if (builder.length() <= 0) {
					builder.wait(timeout);
				}
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	public static void startWatchman() {
		if (running) {
			return;
		}
		running = true;
		Config.registerUpdatingListener(WebConfig.class);
		
		ConfigWebWatchman watchman = new ConfigWebWatchman();
		defaultWatchman = watchman;
		if (WebConfig.blockingBeforeSynchronized) {
			boolean blocking = true; // blocking until all configurations or resources synchronized from remote servers
			String cfgPath = Config.configurationFile;
			if (cfgPath != null) {
				File file = new File(cfgPath + ".timestamp");
				if (file.exists()) {
					byte[] ts = readFile(file);
					if (ts != null) {
						long timestamp = Long.parseLong(new String(ts));
						if (System.currentTimeMillis() - timestamp < WebConfig.synchronizedExpiringInterval) { // default: less than 8 hours
							blocking = false;
						}
					}
				}
			}
			if (blocking) {
				watchman.loopMode = false;
				watchman.refreshAll(true, WebConfig.webRequestTimeout / 10);
				int refreshedCount = 1;
				int checkCount = 0;
				while (watchman.inQueueRequests.size() > 0) {
					try {
						Thread.sleep(WebConfig.webRequestTimeout * refreshedCount / 10);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					checkCount++;
					if (checkCount % 3 == 0) {
						System.out.println("Waiting to synchronize from following:");
						for (String url : watchman.inQueueRequests.keySet()) {
							System.out.println(url);
						}
					}
					if (checkCount % 20 == 0) {
						watchman.refreshAll(true, WebConfig.webRequestTimeout / 10);
						refreshedCount++;
					}
				}
			}
		}
		
		executor = new ThreadPoolExecutor(WebConfig.webCoreWorkers, WebConfig.webMaxWorkers, WebConfig.webWorkerIdleInterval, TimeUnit.SECONDS, new SynchronousQueue<Runnable>(), new ThreadFactory() {
			
			@Override
			public Thread newThread(Runnable r) {
				return new Thread(r, "Web Watchman Worker");
			}
			
		});

		if (!watchman.loopMode) { // block waiting
			watchman.loopMode = true;
		} else {
			watchman.refreshAll(true, WebConfig.webRequestTimeout);
		}
		Thread webThread = new Thread(watchman, "Configuration Remote Web Watchman");
		webThread.setDaemon(true);
		webThread.start();
	}
	
	public static void stopWatchman() {
		running = false;
		if (executor != null) {
			executor.shutdown();
		}
	}
	
	public static long getLatestModified() {
		if (defaultWatchman != null) {
			return defaultWatchman.latestModified;
		}
		return -1;
	}
	
	public static void loadConfigClass(Class<?> clazz) {
		// Do nothing!
		String keyPrefix = Config.getKeyPrefix(clazz);
		if (keyPrefix == null || keyPrefix.length() == 0) {
			return;
		}
		try {
			queue.put(clazz);
		} catch (InterruptedException e) {
			//e.printStackTrace();
			// Do nothing, watchman will try to synchronize this class from remote server later (in 10s)
		}
	}

	static void sendWebRequest(String url, String user, String password, long lastModified, String eTag, WebCallback callback) {
		boolean done = false;
		String reqClass = WebConfig.webRequestClient;
		if (reqClass != null && reqClass.length() > 0) {
			// Other class may be used to request remote configuration file besides HTTP client
			Class<?> clz = Config.loadConfigurationClass(reqClass);
			if (clz != null) {
				try {
					Method m = clz.getMethod("asyncWebRequest", 
							String.class, // url
							String.class, // user
							String.class, // password
							long.class, // lastModified
							String.class, // eTag
							Object.class // WebFetchCallback.class callback
					);
					m.invoke(clz, url, user, password, lastModified, eTag, callback);
					done = true;
				} catch (NoSuchMethodException e) {
					// do nothing
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		if (!done) { // fall back to HTTP request client
			asyncWebRequest(url, user, password, lastModified, eTag, callback);
		}
	}
	
	/*
	 * Default HTTP client to request remote configuration file.
	 */
	public static void asyncWebRequest(String url, String user, String password, long lastModified, String eTag, final Object callback) {
		//HttpRequest.DEFAULT_USER_AGENT = "SimpleConfig/2.1";
		final HttpRequest req = new HttpRequest();
		req.setRequestHeader("User-Agent", "SimpleConfig/2.1");
		req.open("GET", url, true, user, password);
		req.registerOnReadyStateChange(new IXHRCallback() {

			@Override
			public void onOpen() {
			}

			@Override
			public void onSent() {
			}

			@Override
			public void onReceiving() {
			}

			@Override
			public void onLoaded() {
				if (callback instanceof WebCallback) {
					long lastModified = -1;
					String modifiedStr = req.getResponseHeader("Last-Modified");
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
					((WebCallback) callback).got(req.getStatus(), req.getResponseBytes(), lastModified);
				} // else do nothing
			}
			
		});
		if (lastModified > 0) {
			req.setRequestHeader("If-Modified-Since", ConfigWebWatchman.getHTTPDateString(lastModified));
			if (eTag != null && eTag.length() > 0) {
				req.setRequestHeader("If-None-Match", eTag);
			}
		}
		req.send(); // Normal HTTP request may try to create new thread to do asynchronous job. NIO request may not.
	}

}
