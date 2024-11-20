/*******************************************************************************
 * Copyright (c) 2010 - 2015 java2script.org, webuzz.im and others
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

package im.webuzz.config;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
//import java.util.Iterator;
import java.util.List;
import java.util.Map;


/**
 * Generate configuration default file.
 * 
 * @author zhourenjian
 *
 */
public class ConfigGenerator {

	static String readFile(File file) {
		FileInputStream fis = null;
		byte[] buffer = new byte[8096];
		int read = -1;
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			fis = new FileInputStream(file);
			while ((read = fis.read(buffer)) != -1) {
				baos.write(buffer, 0, read);
			}
		} catch (IOException e1) {
			//e1.printStackTrace();
		} finally {
			if (fis != null) {
				try {
					fis.close();
				} catch (IOException e) {
					//e.printStackTrace();
				}
			}
		}
		return new String(baos.toByteArray(), Config.configFileEncoding);
	}
	
	/**
	 * Generate configuration files to the specific file.
	 * 
	 * @param file
	 * @param classes
	 */
	static void generateUpdatedConfiguration(String folder, String fileName,
			Map<Class<?>, String> classWithExtensions, Class<?>[] orderedClasses) {
		//List<String> allNames = new ArrayList<String>();
//		String[] oldConfigClasses = Config.configurationClasses;
//		if (oldConfigClasses != null) {
//			for (String clazz : oldConfigClasses) {
//				allNames.add(clazz);
//			}
//		}
//		List<Class<?>> allConfigs = new ArrayList<Class<?>>();
//		classWithExtensions.keySet();
//		for (Iterator<Class<?>> iterator = classWithExtensions.keySet().iterator(); iterator.hasNext();) {
//			Class<?> clz = (Class<?>) iterator.next();
//			if (clz != null) {
//				allConfigs.add(clz);
//				//allNames.add(clz.getName());
//			}
//		}
//		for (int i = 0; i < classes.length; i++) {
//			Class<?> clz = classes[i];
//		}

//		String fileExt = Config.configurationFileExtension;
//		String oldFileExt = fileExt;
//		int idx = file.lastIndexOf('.');
//		if (idx != -1) {
//			String ext = file.substring(idx + 1);
//			if (ext.length() > 0) {
//				fileExt = file.substring(idx);
//				Config.configurationFileExtension = fileExt;
//			}
//		}
		
//		File f = new File(folder, fileName);
		//String fileName = f.getName();
		//String fileExt = fileName.substring(fileName.indexOf('.') + 1);
		//String folder = file;
//		if (f.isFile() || !f.exists()) { // || folder.endsWith(fileExt)) {
//			folder = f.getParent();
//		}
		File ff = new File(folder);
		if (!ff.exists()) {
			ff.mkdirs();
		}

		StringBuilder defaultBuilder = new StringBuilder();
		IConfigGenerator defaultCG = null;
//		for (Iterator<Class<?>> itr = classWithExtensions.keySet().iterator(); itr.hasNext();) {
//			Class<?> clz = (Class<?>) itr.next();
		for (Class<?> clz : orderedClasses) {
			String keyPrefix = Config.getKeyPrefix(clz);
			StringBuilder builder = null;
			boolean globalConfig = !GeneratorConfig.multipleFiles || keyPrefix == null || keyPrefix.length() == 0;
			String fileExt = classWithExtensions.get(clz);
			IConfigGenerator cg = Config.getConfigurationGenerator(fileExt);
			if (globalConfig) {
				builder = defaultBuilder;
				if (builder.length() > 0) {
					builder.append("\r\n");
				} else {
					defaultCG = cg;
				}
			} else {
				builder = new StringBuilder();
			}
			cg.startGenerate(builder, clz, globalConfig); //, warnChecking));
			if (!globalConfig) { // multiple configurations
				//cg.endClassBlock(builder);
				String source = builder.toString();
				File oldConfigFile = new File(folder, Config.parseFilePath(keyPrefix + fileExt));
				String oldSource = readFile(oldConfigFile);
				if (!source.equals(oldSource)) {
					boolean newFile = oldSource == null || oldSource.length() == 0;
					System.out.println((newFile ? "Write " : "Update ") + keyPrefix + fileExt);
					FileOutputStream fos = null;
					try {
						fos = new FileOutputStream(oldConfigFile);
						fos.write(source.getBytes(Config.configFileEncoding));
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
				} // end if
				builder.delete(0, builder.length());
			}
		} // end of for classes
		defaultCG.endGenerate(defaultBuilder, null, true);
		String source = defaultBuilder.toString();
		File cfgFile = new File(folder, fileName);
		String oldSource = readFile(cfgFile);
		if (!source.equals(oldSource)) {
			System.out.println(((oldSource == null || oldSource.length() == 0) ? "Write " : "Update ") + cfgFile.getAbsolutePath());
			FileOutputStream fos = null;
//			File folderFile = cfgFile.getParentFile();
//			if (!folderFile.exists()) {
//				folderFile.mkdirs();
//			}
			try {
				fos = new FileOutputStream(cfgFile);
				fos.write(source.getBytes(Config.configFileEncoding));
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
		} // end if
	}
	
	static void printUsage() {
		System.out.println("Usage:");
		System.out.println("\t... " + ConfigGenerator.class.getName() + " [--c:xxx=### ...] <configuration file, e.g. config.ini>"
				+ " <target configuration folder> [main configuration file name or file extension, e.g. config.ini or .ini]"
				+ " <<configuration class> [file extension, e.g. .ini or .js or .xml]>"
				+ " [<configuration class> [file extension, e.g. .ini or .js or .xml] ...]"
				+ " [checking class]");
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

	public static void main(String[] args) {
 		args = Config.initialize(args);
		if (args == null || args.length < 2) {
			printUsage();
			return;
		}
		run(args, 0);
	}

	public static void run(String[] args, int indexOffset) {
		String targetFolder = args[indexOffset];
		if (targetFolder == null || targetFolder.length() <= 0) {
			System.out.println("Target configuration folder path can not be empty.");
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
				String[] exts = Config.configurationScanningExtensions;
				if (exts != null) {
					for (int i = 0; i < exts.length; i++) {
						if (exts[i].equals(mainExtension)) {
							existed = true;
							break;
						}
					}
				}
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
		generateUpdatedConfiguration(targetFolder, mainTargetFileName, classExtensions, classes);
	}

    public static boolean isAbstractClass(Class<?> clazz) {
        int modifiers = clazz.getModifiers();
        return Modifier.isAbstract(modifiers);
    }

}