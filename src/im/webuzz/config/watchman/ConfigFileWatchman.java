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

package im.webuzz.config.watchman;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import im.webuzz.config.Config;
import im.webuzz.config.DeepComparator;
import im.webuzz.config.IConfigParser;
import im.webuzz.config.IConfigWatchman;

import java.nio.file.*;
//import java.nio.file.attribute.FileTime;

import static java.nio.file.StandardWatchEventKinds.*;

/**
 * Synchronizing configuration from file system.
 * 
 * @author zhourenjian
 *
 */
public class ConfigFileWatchman implements IConfigWatchman {

	private static boolean running = false;
	
	//private static Properties props;
	private static long mainFileLastUpdated = 0;

	private static Map<String, Long> fileLastUpdateds = new ConcurrentHashMap<String, Long>();
	private static Map<String, Class<?>> keyPrefixClassMap = new ConcurrentHashMap<String, Class<?>>();
	
	private static IConfigParser<File, Object> defaultParser = null;
	/**
	 * Will be invoked by {@link im.webuzz.config.Config#loadWatchmen}
	 */
	public void startWatchman() {
		mainFileLastUpdated = 0;
		updateAllConfigurations(Config.getConfigurationMainFile(), Config.getConfigurationMainExtension(), Config.getConfigurationFolder());
		
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
								updateSingleConfiguration(file, newFileName, extension, clz);
							} else if (newKeyPrefix.equals(mainKeyPrefix)) {
								if (mainExtension != null && !mainExtension.equals(extension)) {
									System.out.println("[INFO] Configuration extension changed: switching from " + newKeyPrefix + mainExtension + " to " + newKeyPrefix + extension);
								}
								mainExtension = extension;
								updateAllConfigurations(mainPathPrefix + mainKeyPrefix + extension, extension, mainFolder);
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
	 * @param configClazz
	 */
	public void watchConfigClass(Class<?> configClazz) {
		defaultParser.parseConfiguration(configClazz, true);
		String keyPrefix = Config.getKeyPrefix(configClazz);
		if (keyPrefix == null || keyPrefix.length() == 0) {
			return;
		}
		
		File file = Config.getConfigruationFile(keyPrefix);
		if (!file.exists()) return;
		String fileName = file.getName();
		String extension = fileName.substring(fileName.lastIndexOf('.'));
		Map<String, Class<? extends IConfigParser<File, Object>>> parsers = Config.configurationParsers;
		if (parsers == null) return;
		Class<? extends IConfigParser<File, Object>> clazz = parsers.get(extension.substring(1));
		if (clazz == null) return;
		//Properties prop = new Properties();

		long lastUpdated = 0;
		//Properties fileProps = new Properties();
		try {
			IConfigParser<File, Object> parser = clazz.newInstance();
			parser.loadResource(file, false);
			//loadConfig(fileProps, file, extension);
			lastUpdated = file.lastModified();
			fileLastUpdateds.put(fileName, lastUpdated);
			Config.recordConfigExtension(configClazz, extension); // always update the configuration class' file extension
			parser.parseConfiguration(configClazz, true);
			if (Config.configurationLogging) {
				System.out.println("[Config] Configuration " + configClazz.getName() + "/" + file.getAbsolutePath() + " loaded.");
			}
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}
	
	private void updateAllConfigurations(String configPath, String configExtension, String extraFolder) {
		List<Class<? extends IConfigWatchman>> oldWatchmen = Config.configurationWatchmen;
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
		if (configPath == null) return;
		File file = new File(configPath);
		if (!file.exists() || file.lastModified() == mainFileLastUpdated) return;
		if (Config.configurationLogging && mainFileLastUpdated > 0) {
			System.out.println("[Config] Configuration file " + file.getAbsolutePath() + " updated.");
		}
		Map<String, Class<? extends IConfigParser<File, Object>>> parsers = Config.configurationParsers;
		if (parsers == null) return;
		Class<? extends IConfigParser<File, Object>> clazz = parsers.get(configExtension.substring(1));
		if (clazz == null) return;
		try {
			defaultParser = clazz.newInstance();
			defaultParser.loadResource(file, true);
			mainFileLastUpdated = file.lastModified();
		} catch (Throwable e) {
			e.printStackTrace();
			return;
		}

		Class<?>[] configs = Config.getAllConfigurations();
		for (int i = 0; i < configs.length; i++) {
			boolean matched = false;
			if (Config.skipUpdatingWithInvalidItems) {
				if (defaultParser.parseConfiguration(configs[i], false) != -1) { // checking
					matched = defaultParser.parseConfiguration(configs[i], true) == 1;
				}
			} else {
				matched = defaultParser.parseConfiguration(configs[i], true) == 1;
			}
			if (matched) {
				Config.recordConfigExtension(configs[i], configExtension);
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
		
		if (!DeepComparator.listDeepEquals(oldWatchmen, Config.configurationWatchmen)) {
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
		configs = Config.getAllConfigurations(); // update local variable configs again, configuration classes may be updated already 
		for (int i = 0; i < configs.length; i++) {
			Class<?> clz = configs[i];
			String keyPrefix = Config.getKeyPrefix(clz);
			if (keyPrefix == null || keyPrefix.length() == 0) continue;
			file = Config.getConfigruationFile(keyPrefix);
			if (!file.exists()) continue;
			String fileName = file.getName();
			int extIndex = fileName.lastIndexOf('.');
			keyPrefixClassMap.put(fileName.substring(0, extIndex), clz);
			String extension = fileName.substring(extIndex);
			updateSingleConfiguration(file, fileName, extension, clz);
		}
	}

	private void updateSingleConfiguration(File file, String fileName, String extension, Class<?> clz) {
		long lastUpdated = 0;
		Long v = fileLastUpdateds.get(fileName);
		if (v != null) {
			lastUpdated = v.longValue();
		}
		if (file.lastModified() == lastUpdated) return;
		if (Config.configurationLogging && lastUpdated > 0) {
			System.out.println("[Config] Configuration " + clz.getName() + " at " + file.getAbsolutePath() + " updated.");
		}
		Map<String, Class<? extends IConfigParser<File, Object>>> parsers = Config.configurationParsers;
		if (parsers == null) return;
		Class<? extends IConfigParser<File, Object>> clazz = parsers.get(extension);
		if (clazz == null) return;
		//Properties prop = new Properties();
		try {
			IConfigParser<File, Object> parser = clazz.newInstance();
			parser.loadResource(file, false);
			//loadConfig(prop, file, extension);
			lastUpdated = file.lastModified();
			fileLastUpdateds.put(fileName, lastUpdated);
			Config.recordConfigExtension(clz, extension); // always update the configuration class' file extension
			if (Config.skipUpdatingWithInvalidItems) {
				if (parser.parseConfiguration(clz, false) != -1) { // checking first
					parser.parseConfiguration(clz, true);
				}
			} else {
				parser.parseConfiguration(clz, true);
			}
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}
	
	public void stopWatchman() {
		running = false;
	}

}
