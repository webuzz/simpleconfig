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
		Map<Class<?>, Map<String, Annotation[]>> typeAnns = Config.configurationAnnotations;
		List<Field> allFields = new ArrayList<Field>();
		List<Class<?>> allClasses = new ArrayList<Class<?>>();
		Class<?> clz = keyPrefixClassMap.get(file.name);
		boolean globalConfig = false;
		if (clz != null) {
			allClasses.add(clz);
		} else {
			globalConfig = true;
			allClasses.add(Config.class);
			allClasses.addAll(Arrays.asList(Config.getAllConfigurations()));
		}
		for (Class<?> clazz : allClasses) {
			if (globalConfig) {
				String keyPrefix = Config.getKeyPrefix(clazz);
				if (keyPrefix != null && keyPrefix.length() > 0) continue;
			}
			Map<String, Annotation[]> fieldAnns = typeAnns == null ? null : typeAnns.get(clazz);
			for (Field f : clazz.getDeclaredFields()) {
				if (!InternalConfigUtils.isFiltered(f, fieldAnns, false, false)) {
					allFields.add(f);
				}
			}
		}
		int size = allFields.size();
		for (int i = 0; i < size; i++) {
			Field f = allFields.get(i);
			Map<String, Annotation[]> fieldAnns = typeAnns == null ? null : typeAnns.get(f.getDeclaringClass());
			if (InternalConfigUtils.isFiltered(f, fieldAnns, false, true)) {
				localOnlyFields.add(f);
				if (i != size - 1) {
					nextFields.add(allFields.get(i + 1));
				} else {
					nextFields.add(null);
				}
			}
		}
		if (localOnlyFields.size() > 0) {
			ConfigGenerator merger = GeneratorKit.getConfigurationGenerator(file.extension);
			if (merger != null) {
				byte[] mergedContent = merger.mergeFields(file.content, localOnlyFields, nextFields);
				if (mergedContent != null && mergedContent != file.content && !Arrays.equals(mergedContent, file.content)) {
					file.originalWebContent = file.content;
					file.content = mergedContent;
				}
			}
		}
	}

}
