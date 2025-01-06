package im.webuzz.config.common;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class FileUtils {

	public static byte[] readFileBytes(File file) {
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
			return null;
		} finally {
			if (fis != null) {
				try {
					fis.close();
				} catch (IOException e) {
					//e.printStackTrace();
				}
			}
		}
		return baos.toByteArray();
	}

	public static boolean writeFileBytes(File file, byte[] bytes, long lastModified) {
		File folderFile = file.getParentFile();
		if (folderFile != null && !folderFile.exists()) {
			folderFile.mkdirs();
		}
		FileOutputStream fos = null;
		try {
			fos = new FileOutputStream(file);
			fos.write(bytes);
			return true;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		} finally {
			if (fos != null) {
				try {
					fos.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
				fos = null;
				if (lastModified > 0) file.setLastModified(lastModified);
			}
		}
	}
	
	/**
	 * Remove "/../" or "/./" in path.
	 * 
	 * @param path
	 * @return file path without "/../" or "/./"
	 */
	public static String parseFilePath(String path) {
		int length = path.length();
		if (length == 0) return path;
		boolean slashStarted = path.charAt(0) == '/';
		boolean slashEnded = length > 1 && path.charAt(length - 1) == '/';
		
		int idxBegin = slashStarted ? 1 : 0;
		int idxEnd = slashEnded ? length - 1 : length;
		if (idxEnd - idxBegin <= 0) {
			return "";
		}
		String[] segments = path.substring(idxBegin, idxEnd).split("\\/|\\\\");
		int count = segments.length + 1;
		for (int i = 0; i < segments.length; i++) {
			count--;
			if (count < 0) {
				System.out.println("[Config:ERROR] Failed to fix the URL: " + path);
				break;
			}
			String segment = segments[i];
			if (segment == null) break;
			if (segments[i].equals("..")) {
				int shift = 2;
				if (i > 0) {
					segments[i - 1] = null;
					segments[i] = null;
					if (i + 1 > segments.length - 1 || segments[i + 1] == null) {
						slashEnded = true;
					}
				} else {
					segments[i] = null;
					shift = 1;
				}
				for (int j = i - shift + 1; j < segments.length - shift; j++) {
					String s = segments[j + shift];
					segments[j] = s;
					if (j == segments.length - shift - 1 || s == null) {
						if (shift == 1) {
							segments[j + 1] = null;
						} else { // shift == 2
							segments[j + 1] = null;
							segments[j + 2] = null;
						}
					}
				}
				i -= shift;
			} else if (segments[i].equals(".")) {
				segments[i] = null;
				if (i + 1 > segments.length - 1 || segments[i + 1] == null) {
					slashEnded = true;
				}
				for (int j = i; j < segments.length - 1; j++) {
					String s = segments[j + 1];
					segments[j] = s;
					if (j == segments.length - 2) {
						segments[j + 1] = null;
					}
				}
				i--;
			}
		}
		StringBuilder builder = new StringBuilder(length);
		int lastLength = 0;
		boolean needSlash = true;
		for (int i = 0; i < segments.length; i++) {
			String segment = segments[i];
			if (segment == null) break;
			if (needSlash && builder.length() > 0) {
				builder.append("/");
			}
			builder.append(segment);
			lastLength = segment.length();
			needSlash = lastLength > 0;
		}
		//if (lastLength == 0 || slashEnded) {
		//	builder.append("/");
		//}
		return builder.toString();
	}

}
