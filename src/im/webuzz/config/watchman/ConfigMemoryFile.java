package im.webuzz.config.watchman;

import java.io.File;
import java.util.Arrays;

import im.webuzz.config.util.FileUtils;

public class ConfigMemoryFile {

	public String path; // null or "./" stands for default configuration folder
	public String name; // without extension
	public String extension; // e.g. .js, with the prefixed dot
	public long modified; // last modified or created timestamp
	public int size; // total content size

	public byte[] content; // OK for small files
	
	public String md5ETag;
	
	public boolean localExisted; // Existed in local file system or not
	public boolean remoteExisted; // Existed in remote configuration center or not
	
	public boolean loadFromFile(File f) {
		if (!f.exists()) {
			localExisted = false;
			return false;
		}
		localExisted = true;
		if (modified > f.lastModified()) {
			return false;
		}
		modified = f.lastModified();
		byte[] bytes = FileUtils.readFileBytes(f);
		if (Arrays.equals(bytes, content)) {
			return true;
		}
		content = bytes;
		return false;
	}
	
	public boolean loadFromWebResponse(byte[] responseBytes, long lastModified) {
		if (responseBytes == null) return false;
		remoteExisted = true;
		if (Arrays.equals(responseBytes, content)) {
			modified = lastModified;
			return true;
		}
		content = responseBytes;
		md5ETag = null;
		modified = lastModified;
		return true;
	}
	
}
