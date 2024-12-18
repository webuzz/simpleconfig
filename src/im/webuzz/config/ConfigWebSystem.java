package im.webuzz.config;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

public class ConfigWebSystem {
	
	public static class WebFile {
		public String name;
		public String extension;
		public byte[] content;
		public long modified;
		public String md5;

		public WebFile(String name, String extension, byte[] content, long modified, String md5) {
			super();
			this.name = name;
			this.extension = extension;
			this.content = content;
			this.modified = modified;
			this.md5 = md5;
		}
 	}
	
	private static Map<String, WebFile> fs = new ConcurrentHashMap<String, ConfigWebSystem.WebFile>();

	public static WebFile save(String fileName, String fileExtension, byte[] content, long lastUpdated, String md5Value) {
		WebFile file = new WebFile(fileName, fileExtension, content, lastUpdated, md5Value);
		fs.put(fileName, file);
		return file;
	}
	
	public static WebFile load(String fileName, String fileExtension) {
		WebFile webFile = fs.get(fileName);
		if (webFile == null) return null;
		if (webFile.extension == null) {
			if (fileExtension == null) return webFile;
			return null;
		}
		if (!webFile.extension.equals(fileExtension)) return null;
		return webFile;
	}
	
	public static byte[] readContent(String fileName, String fileExtension) {
		WebFile file = load(fileName, fileExtension);
		if (file == null || file.extension == null || !file.extension.equals(fileExtension)) return null;
		return file.content;
	}
	
	public static String getMD5(String fileName, String fileExtension) {
		WebFile file = load(fileName, fileExtension);
		if (file == null || file.extension == null || !file.extension.equals(fileExtension)) return null;
		return file.md5;
	}
	
	public static long getLastUpdated(String fileName, String fileExtension) {
		WebFile file = load(fileName, fileExtension);
		if (file == null || file.extension == null || !file.extension.equals(fileExtension)) return -1l;
		return file.modified;
	}
	
//	public static void asyncLoad(String keyPrefix, Callable<WebFile> task) {
//		
//	}
}
