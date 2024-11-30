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

package im.webuzz.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import java.nio.file.*;
//import java.nio.file.attribute.FileTime;

import static java.nio.file.StandardWatchEventKinds.*;

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
	private static Map<String, Class<?>> keyPrefixClassMap = new ConcurrentHashMap<String, Class<?>>();
	
	/**
	 * Will be invoked by {@link im.webuzz.config.Config#loadWatchmen}
	 */
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
				//lastUpdated = 0;
				String mainFile = Config.getConfigurationMainFile();
				String mainPathPrefix = null;
				String mainFileName = null;
				int mainIndex = mainFile.lastIndexOf('/');
				if (mainIndex != -1) {
					mainFileName = mainFile.substring(mainIndex + 1);
					mainPathPrefix = mainFile.substring(0, mainIndex + 1);
				} else {
					mainFileName = mainFile;
					mainPathPrefix = "";
				}
				String mainKeyPrefix = null;
				int index = mainFileName.lastIndexOf('.');
				if (index == -1) {
					mainKeyPrefix = mainFileName;
				} else {
					mainKeyPrefix = mainFileName.substring(0, index);
				}
				String mainExtension = Config.getConfigurationMainExtension();
				String mainFolder = Config.getConfigurationFolder();
				Path path = Paths.get(mainFolder);
				try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
					try {
						// SensitivityWatchEventModifier.HIGH); // Private SUN API
						@SuppressWarnings({ "unchecked", "rawtypes" })
						Enum modifier = Enum.valueOf((Class<? extends Enum>) Class.forName("com.sun.nio.file.SensitivityWatchEventModifier"), "HIGH");
						path.register(watchService, new WatchEvent.Kind[]{ENTRY_MODIFY, ENTRY_CREATE, ENTRY_DELETE}, (WatchEvent.Modifier) modifier);
					} catch (Throwable e) {
						// e.printStackTrace();
						path.register(watchService, new WatchEvent.Kind[]{ENTRY_MODIFY, ENTRY_CREATE, ENTRY_DELETE});
					}

					while (running) {
						WatchKey key = watchService.take();

						for (WatchEvent<?> event : key.pollEvents()) {
							WatchEvent.Kind<?> kind = event.kind();
							if (kind != ENTRY_MODIFY && kind != ENTRY_CREATE && kind != ENTRY_DELETE) continue;
							Path filePath = (Path) event.context();
							String newFileName = filePath.getFileName().toString();
							int dotIndex = newFileName.lastIndexOf('.');
							String extension = newFileName.substring(dotIndex); //.toLowerCase();
							List<String> exts = Config.configurationScanningExtensions;
							if (exts == null || !exts.contains(extension)) continue; // Unsupported extensions
							String newKeyPrefix = null;
							if (dotIndex == -1) {
								newKeyPrefix = newFileName;
							} else {
								newKeyPrefix = newFileName.substring(0, dotIndex);
							}
							// To check if the current key prefix and the extension is the
							// first available(or active) combination or not
							// If not, print warning and continue
							File file = Config.getConfigruationFile(newKeyPrefix);
							if (file.exists()) {
								// It should always be true for ENTRY_MODIFY and ENTRY_CREATE here
								String activeFileName = file.getName();
								if (kind == ENTRY_MODIFY || kind == ENTRY_CREATE) {
									if (!activeFileName.equals(newFileName)) {
										// inactive configuration file for the extension
										System.out.println("[WARN] The updated file " + newFileName + " is disabled for configurations. Current enabled file is " + activeFileName);
										continue;
									} // else continue following logic codes
								} else { // kind == ENTRY_DELETE
									// As the file exists, so the current(DELETED) entry/file should be disabled
									// and load the new existing file
									
									// Remove current file from fileLastUpdateds map
									fileLastUpdateds.remove(newFileName);
									// Update extension with the enable file
									newFileName = activeFileName;
									dotIndex = newFileName.lastIndexOf('.');
									extension = newFileName.substring(dotIndex); //.toLowerCase();
								}
							} else {
								// kind == ENTRY_DELETE may run into this branch
								// Remove current file from fileLastUpdateds map
								fileLastUpdateds.remove(newFileName);
								
								// In this case, the given file is deleted, and no new configuration files are
								// found, print warning, no need to update configurations.
								System.out.println("[WARN] After " + newFileName + " being deleted, no replacment configuration files are found!");
								System.out.println("[WARN] Application restarting will run with the default configurations!");
								continue;
							}
							Class<?> clz = keyPrefixClassMap.get(newKeyPrefix);
							if (clz != null) {
								String oldExtension = Config.getConfigExtension(clz);
								if (oldExtension != null && !oldExtension.equals(extension)) {
									System.out.println("[INFO] Configuration extension changed: switching from " + newKeyPrefix + oldExtension + " to " + newKeyPrefix + extension);
								}
								//Path fullPath = path.resolve(filePath);
								parseConfigFileForClass(file, newFileName, extension, clz);
							} else if (newKeyPrefix.equals(mainKeyPrefix)) {
								if (mainExtension != null && !mainExtension.equals(extension)) {
									System.out.println("[INFO] Configuration extension changed: switching from " + newKeyPrefix + mainExtension + " to " + newKeyPrefix + extension);
								}
								mainExtension = extension;
								updateFromConfigurationFiles(mainPathPrefix + mainKeyPrefix + extension, extension, mainFolder);
							} // else unknown files
						} // end of for key.pollEvents

						boolean valid = key.reset();
						if (!valid) {
							System.out.println("[ERROR] The watching key of the file system's WatchService is invalid!");
							//break;
						}
					}
				} catch (IOException | InterruptedException e) {
					e.printStackTrace();
				}
				/*
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
				//*/
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
			ConfigINIParser.parseConfiguration(props, clazz, true, true, true); //!Config.configurationMultipleFiles);
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
		Properties fileProps = new Properties();
		try {
			loadConfig(fileProps, file, extension);
			lastUpdated = file.lastModified();
			fileLastUpdateds.put(fileName, lastUpdated);
			Config.recordConfigExtension(clazz, extension); // always update the configuration class' file extension
			ConfigINIParser.parseConfiguration(fileProps, clazz, false, true, true);
			if (Config.configurationLogging) {
				System.out.println("[Config] Configuration " + clazz.getName() + "/" + file.getAbsolutePath() + " loaded.");
			}
		} catch (Throwable e) {
			e.printStackTrace();
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
		try {
			loadConfig(props, file, extension);
			lastUpdated = file.lastModified();
			return props;
		} catch (Throwable e) {
			e.printStackTrace();
		}
		props = null;
		return null;
	}
	
	private static void updateFromConfigurationFiles(String configPath, String configExtension, String extraFolder) {
		Class<?>[] oldWatchmen = Config.configurationWatchmen;
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
					boolean matched = false;
					if (Config.skipUpdatingWithInvalidItems) {
						if (ConfigINIParser.parseConfiguration(mainProp, configs[i], true, false, true) != -1) { // checking
							matched = ConfigINIParser.parseConfiguration(mainProp, configs[i], true, true, true) == 1;
						}
					} else {
						matched = ConfigINIParser.parseConfiguration(mainProp, configs[i], true, true, true) == 1;
					}
					if (matched) {
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
			if (keyPrefix == null || keyPrefix.length() == 0) continue;
			File file = Config.getConfigruationFile(keyPrefix);
			if (!file.exists()) continue;
			String fileName = file.getName();
			int extIndex = fileName.lastIndexOf('.');
			if (extIndex > 0) {
				keyPrefixClassMap.put(fileName.substring(0, extIndex), clz);
			} else {
				keyPrefixClassMap.put(fileName, clz);
			}
			String extension = fileName.substring(fileName.indexOf('.') + 1);
			
			parseConfigFileForClass(file, fileName, extension, clz);
		}
	}

	private static void parseConfigFileForClass(File file, String fileName, String extension, Class<?> clz) {
		long lastUpdated = 0;
		Long v = fileLastUpdateds.get(fileName);
		if (v != null) {
			lastUpdated = v.longValue();
		}
		if (file.lastModified() == lastUpdated) return;
		if (Config.configurationLogging && lastUpdated > 0) {
			System.out.println("[Config] Configuration " + clz.getName() + " at " + file.getAbsolutePath() + " updated.");
		}
		Properties prop = new Properties();
		try {
			loadConfig(prop, file, extension);
			lastUpdated = file.lastModified();
			fileLastUpdateds.put(fileName, lastUpdated);
			Config.recordConfigExtension(clz, extension); // always update the configuration class' file extension
			if (Config.skipUpdatingWithInvalidItems) {
				if (ConfigINIParser.parseConfiguration(prop, clz, false, false, true) != -1) { // checking first
					ConfigINIParser.parseConfiguration(prop, clz, false, true, true);
				}
			} else {
				ConfigINIParser.parseConfiguration(prop, clz, false, true, true);
			}
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}

	private static void loadConfig(Properties prop, File file, String extension) throws Exception {
		String ext = extension.substring(1);
		IConfigConverter converter = Config.converters.get(ext);
		if (converter == null) {
			Class<?> clazz = Config.converterExtensions.get(ext);
			if (clazz != null) {
				Object instance = clazz.newInstance();
				if (instance instanceof IConfigConverter) {
					converter = (IConfigConverter) instance;
					Config.converters.put(ext, converter);
				}
			}
		}
		InputStream is = null;
		try {
			is = new FileInputStream(file);
			prop.load(new InputStreamReader(converter != null ? converter.convertToProperties(is) : is, Config.configFileEncoding));
		} catch (Exception e) {
			e.printStackTrace();
			StringBuilder errMsg = new StringBuilder();
			String message = e.getMessage();
			if (message == null || message.length() == 0) {
				Throwable cause = e.getCause();
				if (cause != null) message = cause.getMessage();
			}
			errMsg.append("Error occurs in parsing file \"").append(file.getName())
					.append("\": ").append(message);
			Config.reportErrorToContinue(errMsg.toString());
			throw e;
		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (IOException e) {
					//e.printStackTrace();
				}
			}
		}
	}
	
	public static void stopWatchman() {
		running = false;
	}

}
