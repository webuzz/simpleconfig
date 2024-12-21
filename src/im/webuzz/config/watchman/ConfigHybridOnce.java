package im.webuzz.config.watchman;

import java.io.File;

import im.webuzz.config.Config;
import im.webuzz.config.util.FileUtils;

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

	protected void fetchAllConfigurations() {
		super.fetchAllConfigurations();
		String[] extraFiles = WebConfig.extraResourceFiles;
		if (WebConfig.extraTargetURLPattern != null && extraFiles != null) {
			String[] extraExts = WebConfig.extraResourceExtensions;
			for (String path : extraFiles) {
				if (path == null || path.length() == 0) {
					continue;
				}
				path = FileUtils.parseFilePath(path);
				String filePath = null;
				String fileName = null;
				String fileExt = null;
				if (extraExts != null && extraExts.length > 0) {
					boolean matched = false;
					for (String extraExt : extraExts) {
						if (extraExt == null || extraExt.length() == 0) {
							continue;
						}
						if (path.endsWith(extraExt)) {
							matched = true;
							fileExt = extraExt;
							File f = new File(path);
							String name = f.getName();
							filePath = path.substring(0, path.length() - name.length());
							fileName = name.substring(0, name.length() - fileExt.length());
							break;
						}
					}
					if (!matched) {
						if (Config.configurationLogging) {
							System.out.println("[Config] Resource file " + path + " is skipped as its extension is not permitted.");
						}
						continue;
					}
				}
				ConfigMemoryFile file = ConfigMemoryFS.checkAndPrepareFile(filePath, fileName, fileExt);
				synchronizeFile(null, fileName, fileExt, file, false, path, -1);
			}
		}
	}

	protected void saveResponseToFile(ConfigMemoryFile file, byte[] responseBytes, long lastModified) {
		if (file.loadFromWebResponse(responseBytes, lastModified)) {
			ConfigMemoryFS.saveToLocalFS(file);
		}
	}

}