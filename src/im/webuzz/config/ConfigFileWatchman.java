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

package im.webuzz.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Synchronizing configuration from file system.
 * 
 * @author zhourenjian
 *
 */
public class ConfigFileWatchman {

	private static boolean running = false;
	
	private static Properties prop;
	private static long lastUpdated = 0;

	private static Map<String, Long> fileLastUpdateds = new ConcurrentHashMap<String, Long>();
	
	public static void startWatchman() {
		lastUpdated = 0;
		updateFromConfigurationFiles(Config.configurationFile, Config.configurationFolder);
		
		if (running) {
			return;
		}
		running = true;
		
		Thread thread = new Thread("Configuration Monitor") {
			@Override
			public void run() {
				lastUpdated = 0;
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
					if (running) {
						updateFromConfigurationFiles(Config.configurationFile, Config.configurationFolder);
					}
				}
			}
			
		};
		thread.setDaemon(true);
		thread.start();
	}
	
	public static void loadConfigClass(Class<?> clazz) {
		if (prop != null) {
			Config.parseConfiguration(clazz, true, prop);
		}
		
		if (!Config.configurationMultipeFiles) {
			return;
		}
		
		String keyPrefix = Config.getKeyPrefix(clazz);
		if (keyPrefix == null || keyPrefix.length() == 0) {
			return;
		}
		
		String folder = Config.configurationFolder;
		if (folder == null || folder.length() == 0) {
			if (Config.configurationFile == null) {
				return;
			}
			folder = new File(Config.configurationFile).getParent();
		}
		File file = new File(folder, keyPrefix + Config.configFileExtension);
		if (!file.exists()) {
			return;
		}
		long lastUpdated = 0;
		String absolutePath = file.getAbsolutePath();
		Properties prop = new Properties();
		FileInputStream fis = null;
		try {
			fis = new FileInputStream(file);
			prop.load(fis);
			lastUpdated = file.lastModified();
			fileLastUpdateds.put(absolutePath, lastUpdated);
			Config.parseConfiguration(clazz, false, prop);
			if (Config.configurationLogging) {
				System.out.println("[Config] Configuration " + clazz.getName() + "/" + absolutePath + " loaded.");
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (fis != null) {
				try {
					fis.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
				fis = null;
			}
		}
	}

	private static Properties readConfigurations(String configPath) {
		if (configPath == null) {
			return null;
		}
		File file = new File(configPath);
		if (!file.exists() || file.lastModified() == lastUpdated) {
			return null;
		}
		if (Config.configurationLogging && lastUpdated > 0) {
			System.out.println("[Config] Configuration file " + file.getAbsolutePath() + " updated.");
		}
		prop = new Properties();
		FileInputStream fis = null;
		try {
			fis = new FileInputStream(file);
			prop.load(fis);
			lastUpdated = file.lastModified();
			return prop;
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (fis != null) {
				try {
					fis.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
				fis = null;
			}
		}
		prop = null;
		return null;
	}
	
	private static void updateFromConfigurationFiles(final String configPath, final String extraFolder) {
		Properties mainProp = readConfigurations(configPath);
		if (mainProp != null) {
			Class<?>[] configs = Config.getAllConfigurations();
			for (int i = 0; i < configs.length; i++) {
				Config.parseConfiguration(configs[i], true, mainProp);
			}
		}
		
		if (Config.configurationLogging && configPath != null && !configPath.equals(Config.configurationFile)) {
			System.out.println("[Config] Switch configuration file from " + configPath + " to " + Config.configurationFile + ".");
		}
		if (Config.configurationLogging && extraFolder != null && !extraFolder.equals(Config.configurationFolder)) {
			System.out.println("[Config] Switch configuration folder from " + extraFolder + " to " + Config.configurationFolder + ".");
		}
		
		if (!Config.configurationMultipeFiles) {
			return;
		}
		
		String folder = extraFolder;
		if (folder == null || folder.length() == 0) {
			folder = new File(configPath).getParent();
		}
		Class<?>[] configs = Config.getAllConfigurations();
		for (int i = 0; i < configs.length; i++) {
			Class<?> clz = configs[i];
			String keyPrefix = Config.getKeyPrefix(clz);
			if (keyPrefix == null || keyPrefix.length() == 0) {
				continue;
			}
			File file = new File(folder, keyPrefix + Config.configFileExtension);
			if (!file.exists()) {
				continue;
			}
			long lastUpdated = 0;
			String absolutePath = file.getAbsolutePath();
			Long v = fileLastUpdateds.get(absolutePath);
			if (v != null) {
				lastUpdated = v.longValue();
			}
			if (file.lastModified() == lastUpdated) {
				continue;
			}
			if (Config.configurationLogging && lastUpdated > 0) {
				System.out.println("[Config] Configuration " + clz.getName() + "/" + absolutePath + " updated.");
			}
			Properties prop = new Properties();
			FileInputStream fis = null;
			try {
				fis = new FileInputStream(file);
				prop.load(fis);
				lastUpdated = file.lastModified();
				fileLastUpdateds.put(absolutePath, lastUpdated);
				Config.parseConfiguration(clz, false, prop);
			} catch (FileNotFoundException e) {
				e.printStackTrace();
				continue;
			} catch (IOException e) {
				e.printStackTrace();
				continue;
			} finally {
				if (fis != null) {
					try {
						fis.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
					fis = null;
				}
			}
		}
	}
	
	public static void stopWatchman() {
		running = false;
	}

}
