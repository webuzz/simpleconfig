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

package im.webuzz.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
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
		
		Thread thread = new Thread(new Runnable() {
			
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
			
		}, "Configuration Local File Watchman");
		thread.setDaemon(true);
		thread.start();
	}
	
	public static void loadConfigClass(Class<?> clazz) {
		if (prop != null) {
			Config.parseConfiguration(clazz, true, prop, !Config.configurationMultipleFiles);
		}
		
		if (!Config.configurationMultipleFiles) {
			return;
		}
		
		String keyPrefix = Config.getKeyPrefix(clazz);
		if (keyPrefix == null || keyPrefix.length() == 0) {
			return;
		}
		
		String folder = Config.configurationFolder;
		if (folder == null || folder.length() == 0) {
			String file = Config.configurationFile;
			if (file == null) {
				return;
			}
			folder = new File(file).getParent();
		}
		boolean existed = false;
		File file = null;
		String extension = null;
		String[] exts = Config.configurationScanningExtensions;
		if (exts != null) {
			for (String ext : exts) {
				if (ext != null && ext.length() > 0 && ext.charAt(0) == '.') {
					file = new File(folder, keyPrefix + ext);
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
			file = new File(folder, keyPrefix + extension);
			existed = file.exists();
		}
		if (!existed) {
			return;
		}
		long lastUpdated = 0;
		String absolutePath = file.getAbsolutePath();
		Properties prop = new Properties();
		InputStream fis = null;
		try {
			fis = new FileInputStream(file);
			if (extension.startsWith(".js")) {
				InputStream pIS = ConfigJSParser.convertJS2Properties(fis);
				if (pIS != null) {
					prop.load(pIS);
				} else {
					prop.load(fis);
				}
			} else if (extension.startsWith(".xml")) {
				InputStream pIS = ConfigXMLParser.convertXML2Properties(fis);
				if (pIS != null) {
					prop.load(pIS);
				} else {
					prop.load(fis);
				}
			} else {
				prop.load(fis);
			}
			lastUpdated = file.lastModified();
			fileLastUpdateds.put(absolutePath, lastUpdated);
			Config.parseConfiguration(clazz, false, prop, true);
			if (Config.configurationLogging) {
				System.out.println("[Config] Configuration " + clazz.getName() + "/" + absolutePath + " loaded.");
			}
		} catch (IOException e) {
			e.printStackTrace();
		} catch (Throwable e) {
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

	private static Properties readConfigurations(String configPath, String extension) {
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
			if (extension.startsWith(".js")) {
				InputStream pIS = ConfigJSParser.convertJS2Properties(fis);
				if (pIS != null) {
					prop.load(pIS);
				} else {
					prop.load(fis);
				}
			} else if (extension.startsWith(".xml")) {
				InputStream pIS = ConfigXMLParser.convertXML2Properties(fis);
				if (pIS != null) {
					prop.load(pIS);
				} else {
					prop.load(fis);
				}
			} else {
				prop.load(fis);
			}
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
	
	private static void updateFromConfigurationFiles(String configPath, String extraFolder) {
		String[] oldWatchmen = Config.configurationWatchmen;
		boolean configurationSwitched = false;
		int loopLoadings = 5;
		do {
			configurationSwitched = false;
			String extension = null;
			int idx = configPath.lastIndexOf('.');
			if (idx == -1) {
				extension = Config.configurationFileExtension;
			} else {
				extension = configPath.substring(idx);
			}
			Properties mainProp = readConfigurations(configPath, extension);
			if (mainProp != null) {
				Class<?>[] configs = Config.getAllConfigurations();
				for (int i = 0; i < configs.length; i++) {
					Config.parseConfiguration(configs[i], true, mainProp, !Config.configurationMultipleFiles);
				}
			}
			
			if (configPath != null && !configPath.equals(Config.configurationFile)) {
				if (Config.configurationLogging) {
					System.out.println("[Config] Switch configuration file from " + configPath + " to " + Config.configurationFile + ".");
				}
				configPath = Config.configurationFile;
				lastUpdated = 0;
				configurationSwitched = true;
			}
		} while (configurationSwitched && loopLoadings-- > 0);
		
		if (loopLoadings <= 0 && Config.configurationLogging) {
			System.out.println("[Config] Configuration file is being redirected for too many times (5).");
		}
		
		if (!Arrays.equals(oldWatchmen, Config.configurationWatchmen)) {
			Config.loadWatchmen();
		}
		
		if (extraFolder != null && !extraFolder.equals(Config.configurationFolder)) {
			if (Config.configurationLogging) {
				System.out.println("[Config] Switch configuration folder from " + extraFolder + " to " + Config.configurationFolder + ".");
			}
			fileLastUpdateds.clear();
		}
		
		if (!Config.configurationMultipleFiles) {
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
			boolean existed = false;
			File file = null;
			String extension = null;
			String[] exts = Config.configurationScanningExtensions;
			if (exts != null) {
				for (String ext : exts) {
					if (ext != null && ext.length() > 0 && ext.charAt(0) == '.') {
						file = new File(folder, keyPrefix + ext);
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
				file = new File(folder, keyPrefix + extension);
				existed = file.exists();
			}
			if (!existed) {
				continue;
			}
			//File file = new File(folder, keyPrefix + Config.configurationFileExtension);
			//if (!file.exists()) {
			//	continue;
			//}
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
				if (extension.startsWith(".js")) {
					InputStream pIS = ConfigJSParser.convertJS2Properties(fis);
					if (pIS != null) {
						prop.load(pIS);
					} else {
						prop.load(fis);
					}
				} else if (extension.startsWith(".xml")) {
					InputStream pIS = ConfigXMLParser.convertXML2Properties(fis);
					if (pIS != null) {
						prop.load(pIS);
					} else {
						prop.load(fis);
					}
				} else {
					prop.load(fis);
				}
				lastUpdated = file.lastModified();
				fileLastUpdateds.put(absolutePath, lastUpdated);
				Config.parseConfiguration(clz, false, prop, true);
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
