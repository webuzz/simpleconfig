package im.webuzz.config.loader;

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
		if (file.content == null) {
			if (file.loadFromWebResponse(responseBytes, lastModified)) {
				ConfigMemoryFS.saveToMemoryFS(file);
				ConfigMemoryFS.saveToLocalFS(file);
			}
		} else {
			if (file.loadFromWebResponse(responseBytes, lastModified)) {
				ConfigMemoryFS.saveToLocalFS(file);
			}
		}
	}
}
