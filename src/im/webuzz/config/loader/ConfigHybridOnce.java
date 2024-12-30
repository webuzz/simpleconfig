package im.webuzz.config.loader;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import im.webuzz.config.Config;
import im.webuzz.config.InternalConfigUtils;
import im.webuzz.config.generator.ConfigGenerator;
import im.webuzz.config.generator.GeneratorKit;

public class ConfigHybridOnce extends ConfigWebOnce {

	protected ConfigFileOnce fileOnce = new ConfigFileOnce();
	
	private boolean running = false;
	
	@Override
	public boolean start() {
		if (running) return false;
		running = true;
		if (!fileOnce.start()) return false;
		if (!super.start()) {
			fileOnce.stop();
			return false;
		}
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

	@Override
	protected void fetchAllResourceFiles() {
		// ConfigFileOnce does not load resources into ConfigMemoryFS, so ConfigWebOnce
		// will always have 200 response for first time synchronization.
		// Here try to load all resource files into ConfigMemoryFS for 304 response
		fileOnce.loadAllResourceFiles();
		
		super.fetchAllResourceFiles();
	}
	
	protected void saveResponseToFile(ConfigMemoryFile file, byte[] responseBytes, long lastModified) {
		file.synchronizeWithRemote(responseBytes, lastModified); // sync data to memory
		// Need to check if there are @ConfigLocalOnly fields and update responseBytes accordingly.
		checkAndMergeFields(file, fileOnce.keyPrefixClassMap);
		file.synchronizeWithLocal(new File(file.path + file.name + file.extension), true); // sync data to local file system
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	protected static void checkAndMergeFields(ConfigMemoryFile file, Map<String, Class<?>> keyPrefixClassMap) {
		List<Field> localOnlyFields = new ArrayList<Field>();
		List<Field> nextFields = new ArrayList<Field>(); // Following field
		Class<?> clz = keyPrefixClassMap.get(file.name);
		if (clz != null) {
			Field[] fields = clz.getDeclaredFields();
			Map<Class<?>, Map<String, Annotation[]>> typeAnns = Config.configurationAnnotations;
			Map<String, Annotation[]> fieldAnns = typeAnns == null ? null : typeAnns.get(clz);
			for (int i = 0; i < fields.length; i++) {
				Field f = fields[i];
				if (InternalConfigUtils.isFiltered(f, fieldAnns, false, false, true)) {
					localOnlyFields.add(f);
					if (i != fields.length - 1) {
						nextFields.add(fields[i + 1]);
					} else {
						nextFields.add(null);
					}
				}
			}
		}
		if (localOnlyFields.size() > 0) {
			ConfigGenerator merger = GeneratorKit.getConfigurationGenerator(file.extension);
			if (merger != null) {
				byte[] mergedContent = merger.mergeFields(file.content, clz, localOnlyFields, nextFields);
				if (mergedContent != null && mergedContent != file.content && !Arrays.equals(mergedContent, file.content)) {
					file.originalWebContent = file.content;
					file.content = mergedContent;
				}
			}
		}
	}

}
