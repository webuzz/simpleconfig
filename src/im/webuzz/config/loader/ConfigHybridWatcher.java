package im.webuzz.config.loader;

import java.io.File;

public class ConfigHybridWatcher extends ConfigWebWatcher {

	private ConfigFileWatcher fileWatcher = new ConfigFileWatcher();
	
	private boolean running = false;
	@Override
	public boolean start() {
		if (running) return false;
		if (!fileWatcher.start()) return false;
		if (!super.start()) {
			fileWatcher.stop();
			return false;
		}
		running = true;
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

	protected void saveResponseToFile(ConfigMemoryFile file, byte[] responseBytes, long lastModified) {
		file.synchronizeWithRemote(responseBytes, lastModified); // sync data to memory
		file.synchronizeWithLocal(new File(file.path + file.name + file.extension), true); // sync data to local file system
	}
}
