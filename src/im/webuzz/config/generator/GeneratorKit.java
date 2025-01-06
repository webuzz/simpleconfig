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
//import java.util.concurrent.ConcurrentHashMap;

import im.webuzz.config.Config;
import im.webuzz.config.InternalConfigUtils;
import im.webuzz.config.common.FileUtils;
import im.webuzz.config.common.TypeUtils;


/**
 * Generate configuration default file.
 * 
 * @author zhourenjian
 *
 */
public class GeneratorKit {

	public static ConfigGenerator<?> getConfigurationGenerator(String extension) {
		try {
			Class<? extends ConfigGenerator<?>> clazz = GeneratorConfig.generatorExtensions.get(extension.substring(1));
			if (clazz != null) return clazz.newInstance();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	
	/**
	 * Generate configuration files to the specific file.
	 * 
	 * @param file
	 * @param classes
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	static void generateConfigurationFiles(String folder, String fileName, String fileExtension,
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
			if (fileExt == null) fileExt = fileExtension;
			ConfigGenerator generator = getConfigurationGenerator(fileExt);
			Class<?> rawType = TypeUtils.getInterfaceParamType(generator.getClass(), ConfigGenerator.class);
			Object builder = null;
			if (globalConfig) {
				if (globalBuilder == null) {
					globalBuilder = createABuilder(rawType);
				} else {
					Class<? extends Object> globalBuilderType = globalBuilder.getClass();
					if (globalBuilderType != rawType) {
						System.out.println("[Config:ERROR] Global generator " + globaleGenerator.getClass().getName()
								+ " can only process " + globalBuilderType.getName() + " builder. "
								+ "But now the generator for " + clz.getName() + " is designed to process " + rawType.getName() + " builder.");
						System.out.println("[Config:ERROR] Configuration file " + keyPrefix + fileExt + " is not generated because of the above buidler confliction!");
						return;
					}
				}
				builder = globalBuilder;
				globaleGenerator = generator;
			} else {
				builder = createABuilder(rawType);
			}
			generator.startGenerate(builder, clz);
			System.out.println("[Config:INFO] Generating " + clz.getName());
			if (!globalConfig) { // multiple configurations
				generator.endGenerate(builder, clz);
				writeObjectToFile(builder, new File(folder, FileUtils.parseFilePath(keyPrefix + fileExt)));
				System.out.println("[Config:INFO] " + keyPrefix + fileExt + " generated, class=" + clz.getName());
			}
		} // end of for classes
		globaleGenerator.endGenerate(globalBuilder, null);
		writeObjectToFile(globalBuilder, new File(folder, fileName + fileExtension));
		System.out.println("[Config:INFO] " + fileName + fileExtension + " generated");
	}
	
	private static Object createABuilder(Class<?> rawType) {
		try {
			return rawType.newInstance();
		} catch (Throwable e) {
			e.printStackTrace();
			return null;
		}
	}

	
	protected static void writeObjectToFile(Object obj, File file) {
		byte[] newBytes = convertBuilderToBytes(obj);
		if (newBytes == null) {
			System.out.println("[Config:ERROR] Failed to write object to file " + file.getName() + ": unsupported object type: " + obj.getClass().getName());
			return;
		} else if (newBytes.length == 0) return; // empty
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

	private static byte[] emptyBytes = new byte[0];
	protected static byte[] convertBuilderToBytes(Object obj) {
		byte[] newBytes = null;
		if (obj instanceof StringBuilder) {
			StringBuilder builder = (StringBuilder) obj;
			if (builder.length() == 0) return emptyBytes;
			newBytes = builder.toString().getBytes(Config.configFileEncoding);
		} else if (obj instanceof StringBuffer) {
			StringBuffer buffer = (StringBuffer) obj;
			if (buffer.length() == 0) return emptyBytes;
			newBytes = buffer.toString().getBytes(Config.configFileEncoding);
		} else if (obj instanceof ByteArrayOutputStream) {
			ByteArrayOutputStream baos = (ByteArrayOutputStream) obj;
			if (baos.size() == 0) return emptyBytes;
			newBytes = baos.toByteArray();
		}
		return newBytes;
	}


	private static void updateConfigExtension(Map<Class<?>, String> classExtensions, Class<?> lastClass, String targetExtension) {
		if (lastClass == null) return;
		String extension = targetExtension;
		String prefix = Config.getKeyPrefix(lastClass);
		if (prefix != null && prefix.length() > 0) {
			extension = InternalConfigUtils.getConfigExtension(lastClass);
		}
		classExtensions.put(lastClass, extension);
	}

	public static void run(String[] args, int indexOffset) {
		if (args.length > indexOffset) {
			String targetFolder = args[indexOffset];
			if ("-h".equals(targetFolder) || "--help".equals(targetFolder)) {
				printUsage();
				return;
			}
		}
		
		StringBuilder folderBuilder = new StringBuilder();
		StringBuilder nameBuilder = new StringBuilder();
		StringBuilder extBuilder = new StringBuilder();
		indexOffset = InternalConfigUtils.parseMainFile(args, indexOffset, folderBuilder, nameBuilder, extBuilder);
		String configFolder = folderBuilder.toString();
		String configMainName = nameBuilder.toString();
		String configMainExtension = extBuilder.toString();

		List<Class<?>> orderedClasses = new ArrayList<Class<?>>();
		Map<Class<?>, String> classExtensions = new HashMap<Class<?>, String>();
		orderedClasses.add(Config.class);
		classExtensions.put(Config.class, configMainExtension);
		if (args.length == indexOffset || "*".equals(args[indexOffset])) {
			// Not specifying any class, try to generate all known configuration classes
			String argExt = null;
			int extOffset = indexOffset + 1;
			boolean forceExt = args.length > extOffset && (argExt = args[extOffset]) != null && argExt.startsWith(".");
			for (Class<?> confgClazz : Config.getAllConfigurations()) {
				if (!orderedClasses.contains(confgClazz)) orderedClasses.add(confgClazz);
				if (forceExt) {
					classExtensions.put(confgClazz, argExt);
				} else {
					updateConfigExtension(classExtensions, confgClazz, configMainExtension);
				}
			}
		} else {
			Class<?> lastClass = null;
			for (int i = indexOffset; i < args.length; i++) {
				String clazz = args[i];
				if (clazz != null && clazz.length() > 0) {
					if (clazz.startsWith(".")) {
						// ./etc/ test.js im.webuzz.config.Security .js
						if (lastClass != null) classExtensions.put(lastClass, clazz); // clazz is a file extension
						lastClass = null;
						continue;
					}
					try {
						Class<?> c = Class.forName(clazz);
						if (!orderedClasses.contains(c)) orderedClasses.add(c);
						updateConfigExtension(classExtensions, c, configMainExtension);
						lastClass = c;
						Config.register(c);
					} catch (ClassNotFoundException e) {
						e.printStackTrace();
					}
				}
			}
			if (lastClass != null) updateConfigExtension(classExtensions, lastClass, configMainExtension);
		}

		Class<?>[] classes = orderedClasses.toArray(new Class<?>[orderedClasses.size()]);
		Config.register(GeneratorConfig.class);
		generateConfigurationFiles(configFolder, configMainName, configMainExtension, classExtensions, classes);
	}

	private static void printUsage() {
		System.out.println("Usage:");
		System.out.println("\t... " + Config.class.getName() + " [--c:xxx=### ...] <configuration file, e.g. config.ini> --run:generator"
				+ " [<target main file>|<target folder>|<target folder> <main file name>]"
				+ " [<configuration class> [file extension, e.g. .ini or .js or .xml] ...]");
	}

}