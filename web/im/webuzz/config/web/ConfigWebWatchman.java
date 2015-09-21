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
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.BlockingQueue;
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
	
	@Override
	public void run() {
		boolean first = true;
		while (running) {
			try {
				if (!first) {
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
							synchronizeClass(clazz);								
							i = i > seconds / 2 ? Math.max(seconds / 2 - 2, 1) : 0; // restart sleep waiting
						}
					}
				}
				first = false;
				if (!WebConfig.synchronizing) {
					continue;
				}
				currentRequestTime = System.currentTimeMillis();
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
						synchronizeFile(cfgName, fileExt, cfgFile, true, null);
					}
					
					Class<?>[] configs = Config.getAllConfigurations();
					for (int i = 0; i < configs.length; i++) {
						synchronizeClass(configs[i]);
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
						synchronizeFile(null, null, new File(folder, path), false, path);
					}
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
	
	void synchronizeClass(Class<?> clz) {
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
		synchronizeFile(keyPrefix, extension, file, false, null);
	}
	
	void synchronizeFile(final String keyPrefix, final String fileExtension, final File file, final boolean globalConfig, final String extraPath) {
		String url = extraPath == null ? WebConfig.targetURLPattern : WebConfig.extraTargetURLPattern;
		if (url == null) {
			return;
		}
		if (extraPath == null) {
			url = url.replaceAll("\\$\\{config.key.prefix\\}", keyPrefix);
			if (fileExtension != null && fileExtension.length() > 0
					&& url.indexOf("${config.file.extension}") != -1) {
				url = url.replaceAll("\\$\\{config.file.extension\\}", fileExtension);
			}
		} else {
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
		WebCallback callback = new WebCallback() {
			
			@Override
			public void got(int responseCode, byte[] responseBytes) {
				if (responseCode == 200 && requestTime == currentRequestTime) { // HTTP OK
					if (responseBytes != null && responseBytes.length > 0) {
						byte[] localBytes = readFile(file);
						if (extraPath == null) {
							String[] ignoringFields = WebConfig.ignoringFields;
							if (ignoringFields != null && ignoringFields.length > 0
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
								}
							}
							if (Config.configurationLogging) {
								System.out.println("[Config] Configuration file " + file.getAbsolutePath() + " content synchronized remotely.");
							}
						} else {
							file.setLastModified(System.currentTimeMillis());
							if (Config.configurationLogging) {
								System.out.println("[Config] Configuration file " + file.getAbsolutePath() + " last modified time synchronized remotely.");
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
					builder.wait(WebConfig.webRequestTimeout);
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
		
		executor = new ThreadPoolExecutor(WebConfig.webCoreWorkers, WebConfig.webMaxWorkers, WebConfig.webWorkerIdleInterval, TimeUnit.SECONDS, new SynchronousQueue<Runnable>(), new ThreadFactory() {
			
			@Override
			public Thread newThread(Runnable r) {
				return new Thread(r, "Web Watchman Worker");
			}
			
		});

		Thread webThread = new Thread(new ConfigWebWatchman(), "Configuration Remote Web Wathman");
		webThread.setDaemon(true);
		webThread.start();
	}
	
	public static void stopWatchman() {
		running = false;
		if (executor != null) {
			executor.shutdown();
		}
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
		HttpRequest.DEFAULT_USER_AGENT = "SimpleConfig/2.0";
		final HttpRequest req = new HttpRequest();
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
					((WebCallback) callback).got(req.getStatus(), req.getResponseBytes());
				} // else do nothing
			}
			
		});
		if (lastModified > 0) {
			req.setRequestHeader("If-Modified-Since", ConfigWebWatchman.getHTTPDateString(lastModified));
			if (eTag != null && eTag.length() > 0) {
				req.setRequestHeader("If-None-Match", eTag);
			}
		}
		req.send(); // May try to create new thread to do asynchronous job
	}

}
