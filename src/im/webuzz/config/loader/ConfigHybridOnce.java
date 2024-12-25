package im.webuzz.config.loader;

public class ConfigHybridOnce extends ConfigWebOnce {

	protected ConfigFileOnce fileOnce = new ConfigFileOnce();
	
	private boolean running = false;
	
	@Override
	public boolean start() {
		if (running) return false;
		if (!fileOnce.start()) return false;
		if (!super.start()) {
			fileOnce.stop();
			return false;
		}
		running = true;
		return true;
	}
	
	@Override
	public void stop() {
		fileOnce.stop();
		super.stop();
		running = false;
	}
	
	@Override
	public void add(Class<?> configClazz) {
		if (!running) return;
		fileOnce.add(configClazz);
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
