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
import java.io.InputStreamReader;
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
	
	private static Properties props;
	private static long lastUpdated = 0;

	private static Map<String, Long> fileLastUpdateds = new ConcurrentHashMap<String, Long>();
	
	public static void startWatchman() {
		lastUpdated = 0;
		updateFromConfigurationFiles(Config.getConfigurationMainFile(), Config.getConfigurationMainExtension(), Config.getConfigurationFolder());
		
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
						updateFromConfigurationFiles(Config.getConfigurationMainFile(), Config.getConfigurationMainExtension(), Config.getConfigurationFolder());
					}
				}
			}
			
		}, "Configuration Local File Watchman");
		thread.setDaemon(true);
		thread.start();
	}
	
	/**
	 * This method will be invoked by {@link Config#registerUpdatingListener(Class)}
	 * @param clazz
	 */
	public static void loadConfigClass(Class<?> clazz) {
		if (props != null) {
			ConfigINIParser.parseConfiguration(props, clazz, true, true); //!Config.configurationMultipleFiles);
		}
		
		//if (!Config.configurationMultipleFiles) return;
		
		String keyPrefix = Config.getKeyPrefix(clazz);
		if (keyPrefix == null || keyPrefix.length() == 0) {
			return;
		}
		
		File file = Config.getConfigruationFile(keyPrefix);
		if (!file.exists()) return;
		String fileName = file.getName();
		String extension = fileName.substring(fileName.lastIndexOf('.'));

		long lastUpdated = 0;
		String absolutePath = file.getAbsolutePath();
		Properties fileProps = new Properties();
		InputStream fis = null;
		try {
			fis = new FileInputStream(file);
			loadConfig(fileProps, fis, extension);
			lastUpdated = file.lastModified();
			fileLastUpdateds.put(absolutePath, lastUpdated);
			Config.recordConfigExtension(clazz, extension); // always update the configuration class' file extension
			ConfigINIParser.parseConfiguration(fileProps, clazz, false, true);
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
		props = new Properties();
		FileInputStream fis = null;
		try {
			fis = new FileInputStream(file);
			loadConfig(props, fis, extension);
			lastUpdated = file.lastModified();
			return props;
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
		props = null;
		return null;
	}
	
	private static void updateFromConfigurationFiles(String configPath, String configExtension, String extraFolder) {
		String[] oldWatchmen = Config.configurationWatchmen;
//		boolean configurationSwitched = false;
//		int loopLoadings = 5;
//		do {
//			configurationSwitched = false;
//			String extension = null;
//			int idx = configPath.lastIndexOf('.');
//			if (idx == -1) {
//				extension = Config.configurationFileExtension;
//			} else {
//				extension = configPath.substring(idx);
//			}
			Properties mainProp = readConfigurations(configPath, configExtension);
			if (mainProp != null) {
				Class<?>[] configs = Config.getAllConfigurations();
				for (int i = 0; i < configs.length; i++) {
					if (ConfigINIParser.parseConfiguration(mainProp, configs[i], true, true)) {
						Config.recordConfigExtension(configs[i], configExtension);
					}
				}
			}
			
//			if (configPath != null && !configPath.equals(Config.configurationFile)) {
//				if (Config.configurationLogging) {
//					System.out.println("[Config] Switch configuration file from " + configPath + " to " + Config.configurationFile + ".");
//				}
//				configPath = Config.configurationFile;
//				lastUpdated = 0;
//				configurationSwitched = true;
//			}
//		} while (configurationSwitched && loopLoadings-- > 0);
//		
//		if (loopLoadings <= 0 && Config.configurationLogging) {
//			System.out.println("[Config] Configuration file is being redirected for too many times (5).");
//		}
		
		if (!Arrays.equals(oldWatchmen, Config.configurationWatchmen)) {
			Config.loadWatchmen();
		}
		
//		if (extraFolder != null && !extraFolder.equals(Config.configurationFolder)) {
//			if (Config.configurationLogging) {
//				System.out.println("[Config] Switch configuration folder from " + extraFolder + " to " + Config.configurationFolder + ".");
//			}
//			fileLastUpdateds.clear();
//		}
		
		//if (!Config.configurationMultipleFiles) return;
		
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
			File file = Config.getConfigruationFile(keyPrefix);
			if (!file.exists()) continue;
			String fileName = file.getName();
			String extension = fileName.substring(fileName.indexOf('.') + 1);
			
			long lastUpdated = 0;
			String absolutePath = file.getAbsolutePath();
			Long v = fileLastUpdateds.get(absolutePath);
			if (v != null) {
				lastUpdated = v.longValue();
			}
			if (file.lastModified() == lastUpdated) continue;
			if (Config.configurationLogging && lastUpdated > 0) {
				System.out.println("[Config] Configuration " + clz.getName() + " at " + absolutePath + " updated.");
			}
			Properties prop = new Properties();
			FileInputStream fis = null;
			try {
				fis = new FileInputStream(file);
				loadConfig(prop, fis, extension);
				lastUpdated = file.lastModified();
				fileLastUpdateds.put(absolutePath, lastUpdated);
				Config.recordConfigExtension(clz, extension); // always update the configuration class' file extension
				ConfigINIParser.parseConfiguration(prop, clz, false, true);
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

	public static void loadConfig(Properties prop, InputStream fis, String extension) throws IOException {
		String ext = extension.substring(1);
		IConfigConverter converter = Config.converters.get(ext);
		if (converter == null) {
			String converterClass = Config.converterExtensions.get(ext);
			if (converterClass != null && converterClass.length() > 0) {
				try {
					//Class<?> clazz = Class.forName(converterClass);
					Class<?> clazz = Config.loadConfigurationClass(converterClass);
					if (clazz != null) {
						Object instance = clazz.newInstance();
						if (instance instanceof IConfigConverter) {
							converter = (IConfigConverter) instance;
							Config.converters.put(ext, converter);
						}
					}
				} catch (InstantiationException e) {
					e.printStackTrace();
				} catch (IllegalAccessException e) {
					e.printStackTrace();
				}
			}
		}
		InputStream is = fis;
		if (converter != null) {
			InputStream pIS = converter.convertToProperties(fis);
			if (pIS != null) {
				is = pIS;
			}
		}
		prop.load(new InputStreamReader(is, Config.configFileEncoding));
	}
	
	public static void stopWatchman() {
		running = false;
	}

}
