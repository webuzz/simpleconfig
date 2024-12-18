package im.webuzz.config.annotation;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarFile;

//import im.webuzz.config.annotation.ConfigClass;

public class AnnotationScanner {

	/*
	public static void main(String[] args) {
		try {
			String packageName = "im.webuzz.config.web"; // The target package to scan
			Class<? extends Annotation> annotation = ConfigClass.class; // Target annotation

			// Get all classes annotated with @ConfigClass
			List<Class<?>> annotatedClasses = getAnnotatedClassesInPackage(packageName, annotation);

			// Print the results
			annotatedClasses.forEach(clazz -> System.out.println(clazz.getName()));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	//*/

	/**
	 * Scans the given package for classes annotated with the specified annotation.
	 *
	 * @param packageName The package to scan.
	 * @param annotation  The annotation to look for.
	 * @return A list of classes annotated with the specified annotation.
	 * @throws IOException If an error occurs while accessing resources.
	 * @throws ClassNotFoundException If a class cannot be loaded.
	 */
	public static List<Class<?>> getAnnotatedClassesInPackage(String packageName, Class<? extends Annotation> annotation)
			throws IOException, ClassNotFoundException {
		List<Class<?>> annotatedClasses = new ArrayList<>();
		String packagePath = packageName.replace('.', '/');
		ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

		// Get all resources corresponding to the package path
		Enumeration<URL> resources = classLoader.getResources(packagePath);

		while (resources.hasMoreElements()) {
			URL resource = resources.nextElement();
			String protocol = resource.getProtocol();

			if ("file".equals(protocol)) {
				// If the resource is a directory in the file system
				File directory = new File(resource.getFile());
				if (directory.exists()) {
					findAnnotatedClassesInDirectory(directory, packageName, annotation, annotatedClasses);
				}
			} else if ("jar".equals(protocol)) {
				// If the resource is a JAR file
				String jarPath = resource.getPath().substring(5, resource.getPath().indexOf("!"));
				try (JarFile jarFile = new JarFile(jarPath)) {
					findAnnotatedClassesInJar(jarFile, packagePath, annotation, annotatedClasses);
				}
			}
		}
		return annotatedClasses;
	}

	/**
	 * Recursively scans a directory for classes annotated with the specified annotation.
	 *
	 * @param directory   The directory to scan.
	 * @param packageName The corresponding package name for the directory.
	 * @param annotation  The annotation to look for.
	 * @param annotatedClasses The list to store annotated classes.
	 * @throws ClassNotFoundException If a class cannot be loaded.
	 */
	private static void findAnnotatedClassesInDirectory(File directory, String packageName,
			Class<? extends Annotation> annotation, List<Class<?>> annotatedClasses)
			throws ClassNotFoundException {
		File[] files = directory.listFiles();

		if (files == null) {
			return;
		}

		for (File file : files) {
			if (file.isDirectory()) {
				// Recursively scan subdirectories
				findAnnotatedClassesInDirectory(file, packageName + "." + file.getName(), annotation, annotatedClasses);
			} else if (file.getName().endsWith(".class")) {
				// Convert the file name to a class name and load the class
				String className = packageName + "." + file.getName().replace(".class", "");
				Class<?> clazz = Class.forName(className);

				// Check if the class is annotated with the target annotation
				if (clazz.isAnnotationPresent(annotation)) {
					annotatedClasses.add(clazz);
				}
			}
		}
	}

	/**
	 * Scans a JAR file for classes annotated with the specified annotation.
	 *
	 * @param jarFile     The JAR file to scan.
	 * @param packagePath The package path inside the JAR file.
	 * @param annotation  The annotation to look for.
	 * @param annotatedClasses The list to store annotated classes.
	 * @throws ClassNotFoundException If a class cannot be loaded.
	 */
	private static void findAnnotatedClassesInJar(JarFile jarFile, String packagePath,
			Class<? extends Annotation> annotation, List<Class<?>> annotatedClasses)
			throws ClassNotFoundException {
		jarFile.stream()
				.filter(entry -> !entry.isDirectory() && entry.getName().endsWith(".class"))
				.filter(entry -> entry.getName().startsWith(packagePath))
				.forEach(entry -> {
					String className = entry.getName().replace("/", ".").replace(".class", "");
					try {
						Class<?> clazz = Class.forName(className);

						// Check if the class is annotated with the target annotation
						if (clazz.isAnnotationPresent(annotation)) {
							annotatedClasses.add(clazz);
						}
					} catch (ClassNotFoundException e) {
						e.printStackTrace();
					}
				});
	}
}