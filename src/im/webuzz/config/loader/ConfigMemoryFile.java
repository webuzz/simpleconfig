package im.webuzz.config.loader;

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
	
	// File f.exists() is true, while force saving is false
	public void synchronizeWithLocal(File f, boolean forceSaving) {
		if (forceSaving && !f.exists()) {
			if (FileUtils.writeFileBytes(f, content, modified)) {
				// saved to local file system
				localExisted = true;
			}
			return;
		}
		localExisted = true;
		byte[] bytes = FileUtils.readFileBytes(f);
		long lastModified = f.lastModified();
		if (Arrays.equals(bytes, content)) {
			if (modified > lastModified) f.setLastModified(modified);
			return;
		}
		if (modified > lastModified) {
			// The file in memory is the latest copy, save the content to local file system
			FileUtils.writeFileBytes(f, content, modified);
			return;
		}
		modified = lastModified;
		md5ETag = null;
		if (content == null) { // This ConfigMemoryFile is not in ConfigMemoryFS yet
			content = bytes;
			ConfigMemoryFS.saveToMemoryFS(this); // save to memory
		} else {
			content = bytes;
		}
	}
	
	public void synchronizeWithRemote(byte[] responseBytes, long lastModified) {
		if (responseBytes == null) return;
		if (lastModified <= 0) lastModified = System.currentTimeMillis();
		remoteExisted = true;
		if (Arrays.equals(responseBytes, content)) {
			if (modified < lastModified) modified = lastModified;
		} else if (content == null) { // This ConfigMemoryFile is not in ConfigMemoryFS yet
			content = responseBytes;
			md5ETag = null;
			modified = lastModified;
			ConfigMemoryFS.saveToMemoryFS(this); // save to memory
		} else if (lastModified >= modified) {
			content = responseBytes;
			md5ETag = null;
			modified = lastModified;
		//} else { // The memory copy is the latest copy, do nothing
		}
	}
	
}
