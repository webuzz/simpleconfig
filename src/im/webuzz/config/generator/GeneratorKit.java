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

package im.webuzz.config.generator;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import im.webuzz.config.Config;
import im.webuzz.config.util.FileUtils;
import im.webuzz.config.util.TypeUtils;


/**
 * Generate configuration default file.
 * 
 * @author zhourenjian
 *
 */
public class GeneratorKit {

	protected static Map<String, ConfigGenerator<?>> generators = new ConcurrentHashMap<>();

	public static ConfigGenerator<?> getConfigurationGenerator(String extension) {
		//String ext = extension.substring(1);
		ConfigGenerator<?> generator = generators.get(extension);
		if (generator != null) return generator;
		try {
			Class<?> clazz = GeneratorConfig.generatorExtensions.get(extension);
			if (clazz != null) {
				Object instance = clazz.newInstance();
				if (instance instanceof ConfigGenerator) {
					generator = (ConfigGenerator<?>) instance;
					generators.put(extension, generator);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		if (generator == null) generator = new ConfigINIGenerator();
		return generator;
	}

	/**
	 * Generate configuration files to the specific file.
	 * 
	 * @param file
	 * @param classes
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	static void generateConfigurationFiles(String folder, String fileName,
			Map<Class<?>, String> classWithExtensions, Class<?>[] orderedClasses) {
		File ff = new File(folder);
		if (!ff.exists()) {
			ff.mkdirs();
		}

		Object globalBuilder = null;
		ConfigGenerator globaleGenerator = null;
		for (Class<?> clz : orderedClasses) {
			String keyPrefix = Config.getKeyPrefix(clz);
			boolean globalConfig = !GeneratorConfig.multipleFiles || keyPrefix == null || keyPrefix.length() == 0;
			String fileExt = classWithExtensions.get(clz);
			ConfigGenerator generator = getConfigurationGenerator(fileExt.substring(1));
			Class<?> rawType = TypeUtils.getInterfaceParamType(generator.getClass(), ConfigGenerator.class);
			Object builder = null;
			if (globalConfig) {
				if (globalBuilder == null) {
					globalBuilder = createABuilder(rawType);
				} else {
					Class<? extends Object> globalBuilderType = globalBuilder.getClass();
					if (globalBuilderType != rawType) {
						System.out.println("[ERROR] Global generator " + globaleGenerator.getClass().getName()
								+ " can only process " + globalBuilderType.getName() + " builder. "
								+ "But now the generator for " + clz.getName() + " is designed to process " + rawType.getName() + " builder.");
						System.out.println("[ERROR] Configuration file " + keyPrefix + fileExt + " is not generated because of the above buidler confliction!");
						return;
					}
				}
				builder = globalBuilder;
				globaleGenerator = generator;
			} else {
				builder = createABuilder(rawType);
			}
			generator.startGenerate(builder, clz);
			if (!globalConfig) { // multiple configurations
				generator.endGenerate(builder, clz);
				writeObjectToFile(builder, new File(folder, Config.parseFilePath(keyPrefix + fileExt)));
			}
		} // end of for classes
		globaleGenerator.endGenerate(globalBuilder, null);
		writeObjectToFile(globalBuilder, new File(folder, fileName));
	}
	
	private static Object createABuilder(Class<?> rawType) {
		try {
			return rawType.newInstance();
		} catch (Throwable e) {
			e.printStackTrace();
			return null;
		}
	}

	private static void writeObjectToFile(Object obj, File file) {
		byte[] newBytes = null;
		if (obj instanceof StringBuilder) {
			StringBuilder builder = (StringBuilder) obj;
			if (builder.length() == 0) return;
			newBytes = builder.toString().getBytes(Config.configFileEncoding);
		} else if (obj instanceof StringBuffer) {
			StringBuffer buffer = (StringBuffer) obj;
			if (buffer.length() == 0) return;
			newBytes = buffer.toString().getBytes(Config.configFileEncoding);
		} else if (obj instanceof ByteArrayOutputStream) {
			ByteArrayOutputStream baos = (ByteArrayOutputStream) obj;
			if (baos.size() == 0) return;
			newBytes = baos.toByteArray();
		} else {
			System.out.println("[ERROR] Failed to write object to file " + file.getName() + ": unsupported object type: " + obj.getClass().getName());
			return;
		}
		byte[] oldBytes = FileUtils.readFileBytes(file);
		if (Arrays.equals(newBytes, oldBytes)) return; // unchanged
		System.out.println(((oldBytes == null || oldBytes.length == 0) ? "Write " : "Update ") + file.getAbsolutePath());
		FileOutputStream fos = null;
		try {
			fos = new FileOutputStream(file);
			fos.write(newBytes);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (fos != null) {
				try {
					fos.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	private static void updatedConfigExtension(Map<Class<?>, String> classExtensions, Class<?> lastClass,
			String targetExtension, boolean force) {
		if (lastClass == null) return;
		String extension = targetExtension;
		if (!force) {
			String prefix = Config.getKeyPrefix(lastClass);
			if (prefix != null && prefix.length() > 0) {
				extension = Config.getConfigExtension(lastClass);
			}
		}
		classExtensions.put(lastClass, extension);
	}

	public static void run(String[] args, int indexOffset) {
		if (args.length - indexOffset < 2) {
			printUsage();
			return;
		}
		String targetFolder = args[indexOffset];
		if (targetFolder == null || targetFolder.length() <= 0) {
			System.out.println("[ERROR] Target configuration folder path can not be empty.");
			printUsage();
			return;
		}
		if ("-h".equals(targetFolder) || "--help".equals(targetFolder)) {
			printUsage();
			return;
		}
		indexOffset++;
		String mainTargetFileName = args[indexOffset];
		String mainFileName = new File(Config.getConfigurationMainFile()).getName();
		String mainExtension = null;
		if (mainTargetFileName.startsWith(".") && mainTargetFileName.lastIndexOf('.') != 0) {
			mainExtension = mainTargetFileName;
			if (mainFileName.endsWith(mainExtension)) {
				mainTargetFileName = mainFileName;;
			} else {
				mainTargetFileName = mainFileName.substring(0, mainFileName.lastIndexOf('.')) + mainExtension;
			}
			indexOffset++;
		} else {
			// check if mainTargetFileName is a configuration file name or a class name
			boolean existed = false;
			int idx = mainTargetFileName.lastIndexOf('.');
			if (idx != -1) {
				mainExtension = mainTargetFileName.substring(idx);
				List<String> exts = Config.configurationScanningExtensions;
				if (exts != null) existed = exts.contains(mainExtension);
			}
			if (existed) {
				indexOffset++;
			} else {
				mainExtension = mainFileName.substring(mainFileName.lastIndexOf('.') + 1);
			}
		}
		List<Class<?>> orderedClasses = new ArrayList<Class<?>>();
		Map<Class<?>, String> classExtensions = new HashMap<Class<?>, String>();
		Class<?> lastClass = null;
		for (int i = indexOffset; i < args.length; i++) {
			String clazz = args[i];
			if (clazz != null && clazz.length() > 0) {
				if (clazz.startsWith(".")) {
					updatedConfigExtension(classExtensions, lastClass, clazz, true); // clazz is a file extension
					lastClass = null;
					continue;
				}
				try {
					Class<?> c = Class.forName(clazz);
					orderedClasses.add(c);
					updatedConfigExtension(classExtensions, lastClass, mainExtension, false);
					lastClass = c;
					Config.registerUpdatingListener(c);
				} catch (ClassNotFoundException e) {
					e.printStackTrace();
				}
			}
		}
		updatedConfigExtension(classExtensions, lastClass, mainExtension, false);
		Class<?>[] classes = orderedClasses.toArray(new Class<?>[orderedClasses.size()]);

		Config.registerUpdatingListener(GeneratorConfig.class);
		generateConfigurationFiles(targetFolder, mainTargetFileName, classExtensions, classes);
	}

	private static void printUsage() {
		System.out.println("Usage:");
		System.out.println("\t... " + Config.class.getName() + " [--c:xxx=### ...] <configuration file, e.g. config.ini> --run:generator"
				+ " <target configuration folder> [main configuration file name or file extension, e.g. config.ini or .ini]"
				+ " <<configuration class> [file extension, e.g. .ini or .js or .xml]>"
				+ " [<configuration class> [file extension, e.g. .ini or .js or .xml] ...]"
				+ " [checking class]");
	}

}