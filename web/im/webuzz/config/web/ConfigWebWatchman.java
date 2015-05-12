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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Date;

import net.sf.j2s.ajax.HttpRequest;
import net.sf.j2s.ajax.XHRCallbackAdapter;
import im.webuzz.config.Config;
import im.webuzz.config.ConfigMerger;

/**
 * Synchronize configuration files from given server to local file system.
 */
public class ConfigWebWatchman {

	private static boolean running = false;
	

	private final static String[] WEEK_DAYS_ABBREV = new String[] {
		"Sun", "Mon", "Tue", "Wed", "Thu",  "Fri", "Sat"
	};
	
	@SuppressWarnings("deprecation")
	public static String getHTTPDateString(long time) {
		if (time < 0) {
			time = System.currentTimeMillis();
		}
		Date date = new Date(time);
		return WEEK_DAYS_ABBREV[date.getDay()] + ", " + date.toGMTString();
	}
	
	static byte[] readFile(File file) {
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

	public static void startWatchman() {
		if (running) {
			return;
		}
		running = true;
		Config.registerUpdatingListener(WebConfig.class);
		
		Thread webThread = new Thread("Configuration Web Synchronizer") {
			
			private long currentRequestTime = 0;
			
			@Override
			public void run() {
				while (running) {
					for (int i = 0; i < 10; i++) {
						try {
							Thread.sleep(1000);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
						if (!running) {
							break;
						}
					}
					if (!WebConfig.synchronizing) {
						continue;
					}
					currentRequestTime = System.currentTimeMillis();
					if (running && WebConfig.targetURLPattern != null) {
						String cfgPath = Config.configurationFile;
						if (cfgPath != null) {
							File cfgFile = new File(cfgPath);
							String cfgName = cfgFile.getName();
							if (cfgName.endsWith(Config.configFileExtension)) {
								cfgName = cfgName.substring(0, cfgName.length() - 4);
							}
							synchronizeFile(cfgName, cfgFile, true);
						}
						
						Class<?>[] configs = Config.getAllConfigurations();
						for (int i = 0; i < configs.length; i++) {
							Class<?> clz = configs[i];
							final String keyPrefix = Config.getKeyPrefix(clz);
							if (keyPrefix == null || keyPrefix.length() == 0) {
								continue;
							}
							String folder = Config.configurationFolder;
							if (folder == null) {
								folder = Config.configurationFile;
								if (folder.endsWith(Config.configFileExtension)) {
									folder = new File(folder).getParent();
								}
							}
							synchronizeFile(keyPrefix, new File(folder, keyPrefix + Config.configFileExtension), false);
						}
					}
				}
			}
			
			void synchronizeFile(final String keyPrefix, final File file, final boolean globalConfig) {
				String urlPattern = WebConfig.targetURLPattern;
				if (urlPattern == null) {
					return;
				}
				String url = urlPattern.replaceAll("\\$\\{server.url.prefix\\}", WebConfig.globalServerURLPrefix)
						.replaceAll("\\$\\{server.auth.user\\}", WebConfig.globalServerAuthUser)
						.replaceAll("\\$\\{server.auth.password\\}", WebConfig.globalServerAuthPassword)
						.replaceAll("\\$\\{local.server.name\\}", WebConfig.localServerName)
						.replaceAll("\\$\\{config.key.prefix\\}", keyPrefix);
				final StringBuilder builder = new StringBuilder();
				HttpRequest httpReq = null;
				String reqClass = WebConfig.httpRequestClass;
				if (reqClass != null && reqClass.length() > 0) {
					Class<?> clz = Config.loadConfigurationClass(reqClass);
					if (clz != null) {
						try {
							if (HttpRequest.class.isAssignableFrom(clz)) {
								httpReq = (HttpRequest) clz.newInstance();
							}
						} catch (InstantiationException e) {
							e.printStackTrace();
						} catch (IllegalAccessException e) {
							e.printStackTrace();
						}
					}
				}
				if (httpReq == null) {
					httpReq = new HttpRequest();
				}
				final HttpRequest req = httpReq;
				req.open("GET", url, true, WebConfig.globalServerAuthUser, WebConfig.globalServerAuthPassword);
				final long requestTime = currentRequestTime;
				req.registerOnReadyStateChange(new XHRCallbackAdapter() {

					@Override
					public void onLoaded() {
						if (req.getStatus() == 200 && requestTime == currentRequestTime) { // HTTP OK
							byte[] response = req.getResponseBytes();
							if (response != null && response.length > 0) {
								String[] ignoringFields = WebConfig.ignoringFields;
								byte[] localBytes = readFile(file);
								if (ignoringFields != null && ignoringFields.length > 0) {
									response = ConfigMerger.mergeWithIgnoringFields(response, localBytes, globalConfig ? null : keyPrefix, ignoringFields);
								}
								if (!Arrays.equals(response, localBytes)) {
									FileOutputStream fos = null;
									try {
										fos = new FileOutputStream(file);
										fos.write(response);
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
										System.out.println("[Config] Configuration file " + file.getAbsolutePath() + " synchronized remotely.");
									}
								} else {
									file.setLastModified(System.currentTimeMillis());
								}
							}
						}
						synchronized (builder) {
							builder.append(1);
							builder.notify();
						}
					}
					
				});
				if (file.exists()) {
					req.setRequestHeader("If-Modified-Since", getHTTPDateString(file.lastModified()));
				}
				req.send();
				try {
					synchronized (builder) {
						if (builder.length() <= 0) {
							builder.wait(WebConfig.httpRequestTimeout);
						}
					}
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}
			}
			
		};
		webThread.setDaemon(true);
		webThread.start();
	}
	
	public static void stopWatchman() {
		running = false;
	}
	
	public static void loadConfigClass(Class<?> clazz) {
		// Do nothing!
	}

}
