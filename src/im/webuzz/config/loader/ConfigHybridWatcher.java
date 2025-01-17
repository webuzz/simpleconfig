package im.webuzz.config.loader;

import java.io.File;

public class ConfigHybridWatcher extends ConfigWebWatcher {

	private ConfigFileWatcher fileWatcher = new ConfigFileWatcher();
	
	private boolean running = false;
	@Override
	public boolean start() {
		if (running) return false;
		running = true;
		if (!fileWatcher.start()) return false;
		if (!super.start()) {
			fileWatcher.stop();
			return false;
		}
		return true;
	}
	
	@Override
	public void stop() {
		fileWatcher.stop();
		super.stop();
		running = false;
	}
	
	@Override
	public void add(Class<?> configClazz) {
		if (!running) return;
		fileWatcher.add(configClazz);
		super.add(configClazz);
	}

	@Override
	protected void fetchAllResourceFiles() {
		// ConfigFileOnce does not load resources into ConfigMemoryFS, so ConfigWebOnce
		// will always have 200 response for first time synchronization.
		// Here try to load all resource files into ConfigMemoryFS for 304 response
		fileWatcher.loadAllResourceFiles();
		
		super.fetchAllResourceFiles();
	}

	protected void saveResponseToFile(ConfigMemoryFile file, byte[] responseBytes, long lastModified) {
		file.synchronizeWithRemote(responseBytes, lastModified); // sync data to memory
		// Need to check if there are @ConfigLocalOnly fields and update responseBytes accordingly.
		ConfigHybridOnce.checkAndMergeFields(file, fileWatcher.keyPrefixClassMap);
		file.synchronizeWithLocal(new File(file.path + file.name + file.extension), true); // sync data to local file system
	}

}
