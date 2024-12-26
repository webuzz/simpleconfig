package im.webuzz.config.loader;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.List;

import im.webuzz.config.Config;
import im.webuzz.config.InternalConfigUtils;

public class ConfigFileWatcher extends ConfigFileOnce implements Runnable {

	@Override
	public boolean start() {
		if (!super.start()) return false;
		if (Config.configurationLogging) System.out.println("[Config:INFO] Starting local configuration file watcher");
		Thread webThread = new Thread(this, "Local Configuration File Watcher");
		webThread.setDaemon(true);
		webThread.start();
		return true;
	}
	
	@Override
	public void run() {
		String mainKeyPrefix = Config.getConfigMainName();
		String mainExtension = Config.getConfigMainExtension();
		String mainFolder = Config.getConfigFolder();
		//System.out.println("Main folder:" + mainFolder);
		Path path = Paths.get(mainFolder);
		try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
			WatchKey watchKey;
			try {
				// SensitivityWatchEventModifier.HIGH); // Private SUN API
				@SuppressWarnings({ "unchecked", "rawtypes" })
				Enum modifier = Enum.valueOf((Class<? extends Enum>) Class.forName("com.sun.nio.file.SensitivityWatchEventModifier"), "HIGH");
				watchKey = path.register(watchService, new WatchEvent.Kind[]{ENTRY_MODIFY, ENTRY_CREATE, ENTRY_DELETE}, (WatchEvent.Modifier) modifier);
			} catch (Throwable e) {
				// e.printStackTrace();
				watchKey = path.register(watchService, new WatchEvent.Kind[]{ENTRY_MODIFY, ENTRY_CREATE, ENTRY_DELETE});
			}

			while (running) {
				WatchKey key = watchService.take();

				for (WatchEvent<?> event : key.pollEvents()) {
					WatchEvent.Kind<?> kind = event.kind();
					//System.out.println(kind);
					if (kind != ENTRY_MODIFY && kind != ENTRY_CREATE && kind != ENTRY_DELETE) continue;
					Path filePath = (Path) event.context();
					String newFileName = filePath.getFileName().toString();
					int dotIndex = newFileName.lastIndexOf('.');
					if (dotIndex == -1) continue; // skip
					String extension = newFileName.substring(dotIndex); //.toLowerCase();
					List<String> exts = Config.configurationScanningExtensions;
					if (!exts.contains(extension)) continue; // Unsupported extensions
					String newKeyPrefix = newFileName.substring(0, dotIndex);
					// To check if the current key prefix and the extension is the
					// first available(or active) combination or not
					// If not, print warning and continue
					StringBuilder extBuilder = new StringBuilder();
					File file = InternalConfigUtils.getConfigFile(newKeyPrefix, extBuilder);
					//System.out.println(file);
					if (!file.exists()) {
						// kind == ENTRY_DELETE may run into this branch
						// Remove current file from fileLastUpdateds map
						fileLastUpdateds.remove(newFileName);
						
						// In this case, the given file is deleted, and no new configuration files are
						// found, print warning, no need to update configurations.
						System.out.println("[Config:WARN] After " + newFileName + " being deleted, no replacment configuration files are found!");
						System.out.println("[Config:WARN] Application restarting may run with incorrect configurations!");
						continue;
					}
					// It should always be true for ENTRY_MODIFY and ENTRY_CREATE here
					String activeFileName = file.getName();
					if (kind == ENTRY_MODIFY || kind == ENTRY_CREATE) {
						if (!activeFileName.equals(newFileName)) {
							// inactive configuration file for the extension
							System.out.println("[Config:WARN] The updated file " + newFileName + " is disabled for configurations. Current enabled file is " + activeFileName);
							continue;
						} // else continue following logic codes
					} else { // kind == ENTRY_DELETE
						// As the file exists, so the current(DELETED) entry/file should be disabled
						// and load the new existing file
						
						// Remove current file from fileLastUpdateds map
						fileLastUpdateds.remove(newFileName);
						// Update extension with the enable file
						extension = extBuilder.toString();
					}
					Class<?> clz = keyPrefixClassMap.get(newKeyPrefix);
					//System.out.println("Yoho " + clz + " // " + newKeyPrefix + " xxx " + keyPrefixClassMap.size());
					if (clz != null) {
						String oldExtension = InternalConfigUtils.getConfigExtension(clz);
						if (oldExtension != null && !oldExtension.equals(extension)) {
							if (Config.configurationLogging) System.out.println("[Config:INFO] Configuration extension changed: switching from "
									+ newKeyPrefix + oldExtension + " to " + newKeyPrefix + extension);
						}
						//Path fullPath = path.resolve(filePath);
						updateSingleConfiguration(file, mainFolder, newKeyPrefix, extension, clz);
					} else if (newKeyPrefix.equals(mainKeyPrefix)) {
						if (mainExtension != null && !mainExtension.equals(extension)) {
							if (Config.configurationLogging) System.out.println("[Config:INFO] Configuration extension changed: switching from "
									+ newKeyPrefix + mainExtension + " to " + newKeyPrefix + extension);
						}
						Config.updateConfigMainExtension(extension);
						mainExtension = extension;
						updateAllConfigurations(mainFolder, mainKeyPrefix, extension);
					} // else unknown files
				} // end of for key.pollEvents

				boolean valid = key.reset();
				if (!valid) {
					System.out.println("[Config:ERROR] The watching key of the file system's WatchService is invalid!");
					//break;
				}
			}
			watchKey.cancel();
			watchService.close();
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
				if (!running) break;
			}
			if (running) updateAllConfigurations(mainFolder, mainKeyPrefix, extension);
		}
		//*/
	}
}
