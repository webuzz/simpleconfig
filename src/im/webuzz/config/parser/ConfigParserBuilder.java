package im.webuzz.config.parser;

import java.io.ByteArrayInputStream;
import java.io.File;
//import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Properties;

import im.webuzz.config.Config;
import im.webuzz.config.common.TypeUtils;

public class ConfigParserBuilder {

	/*
	@SuppressWarnings("unchecked")
	public static ConfigParser<?, ?> prepareParser(String fileExtension, File file,
			boolean combinedConfigs) throws Exception {
		Map<String, Class<? extends ConfigParser<?, ?>>> parsers = Config.configurationParsers;
		if (parsers == null) return null;
		Class<? extends ConfigParser<?, ?>> clazz = parsers.get(fileExtension.substring(1));
		if (clazz == null) return null;
		ConfigParser<?, ?> parser = clazz.newInstance();
		Class<?> rawType = TypeUtils.getInterfaceParamType(clazz, ConfigParser.class);
		if (rawType == InputStream.class) {
			FileInputStream fis = null;
			try {
				fis = new FileInputStream(file);
				((ConfigParser<InputStream, ?>) parser).loadResource(fis, combinedConfigs);
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				if (fis != null) {
					try {
						fis.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		} else if (rawType == byte[].class) {
			((ConfigParser<byte[], ?>) parser).loadResource(FileUtils.readFileBytes(file), combinedConfigs);
		} else if (rawType == File.class) {
			((ConfigParser<File, ?>) parser).loadResource(file, combinedConfigs);
		} else if (rawType == Properties.class) {
			Properties props = new Properties();
			FileInputStream fis = null;
			InputStreamReader reader = null;
			try {
				fis = new FileInputStream(file);
				reader = new InputStreamReader(fis, Config.configFileEncoding);
				props.load(reader);
				((ConfigParser<Properties, ?>) parser).loadResource(props, combinedConfigs);
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				if (reader != null) {
					try {
						reader.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				if (fis != null) {
					try {
						fis.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		}
		return parser;
	}
	//*/
	@SuppressWarnings("unchecked")
	public static ConfigParser<?, ?> prepareParser(String fileExtension, String str,
			boolean combinedConfigs) throws Exception {
		Map<String, Class<? extends ConfigParser<?, ?>>> parsers = Config.configurationParsers;
		if (parsers == null) return null;
		Class<? extends ConfigParser<?, ?>> clazz = parsers.get(fileExtension.substring(1));
		if (clazz == null) return null;
		ConfigParser<?, ?> parser = clazz.newInstance();
		Class<?> rawType = TypeUtils.getInterfaceParamType(clazz, ConfigParser.class);
		if (rawType == String.class) {
			((ConfigParser<String, ?>) parser).loadResource(str, combinedConfigs);
		} else if (rawType == InputStream.class) {
			InputStream fis = null;
			try {
				fis = new ByteArrayInputStream(str.getBytes(Config.configFileEncoding));
				((ConfigParser<InputStream, ?>) parser).loadResource(fis, combinedConfigs);
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				if (fis != null) {
					try {
						fis.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		} else if (rawType == byte[].class) {
			((ConfigParser<byte[], ?>) parser).loadResource(str.getBytes(Config.configFileEncoding), combinedConfigs);
		} else if (rawType == File.class) {
			Path tempFilePath = null;
			try {
				tempFilePath = Files.createTempFile(System.currentTimeMillis() + "." + Math.random(), ".tmp");
				Files.write(tempFilePath, str.getBytes(Config.configFileEncoding), StandardOpenOption.WRITE);
				((ConfigParser<File, ?>) parser).loadResource(tempFilePath.toFile(), combinedConfigs);
			} finally {
				if (tempFilePath != null) {
					try {
						Files.deleteIfExists(tempFilePath);
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		} else if (rawType == Properties.class) {
			Properties props = new Properties();
			InputStream fis = null;
			InputStreamReader reader = null;
			try {
				fis = new ByteArrayInputStream(str.getBytes(Config.configFileEncoding));
				reader = new InputStreamReader(fis, Config.configFileEncoding);
				props.load(reader);
				((ConfigParser<Properties, ?>) parser).loadResource(props, combinedConfigs);
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				if (reader != null) {
					try {
						reader.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				if (fis != null) {
					try {
						fis.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		}
		return parser;
	}

	@SuppressWarnings("unchecked")
	public static ConfigParser<?, ?> prepareParser(String fileExtension, byte[] content,
			boolean combinedConfigs) throws Exception {
		Map<String, Class<? extends ConfigParser<?, ?>>> parsers = Config.configurationParsers;
		if (parsers == null) return null;
		Class<? extends ConfigParser<?, ?>> clazz = parsers.get(fileExtension.substring(1));
		if (clazz == null) return null;
		ConfigParser<?, ?> parser = clazz.newInstance();
		Class<?> rawType = TypeUtils.getInterfaceParamType(clazz, ConfigParser.class);
		if (rawType == InputStream.class) {
			try {
				((ConfigParser<InputStream, ?>) parser).loadResource(new ByteArrayInputStream(content), combinedConfigs);
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else if (rawType == byte[].class) {
			((ConfigParser<byte[], ?>) parser).loadResource(content, combinedConfigs);
		} else if (rawType == File.class) {
			Path tempFilePath = null;
			try {
				tempFilePath = Files.createTempFile(System.currentTimeMillis() + "." + Math.random(), ".tmp");
				Files.write(tempFilePath, content, StandardOpenOption.WRITE);
				((ConfigParser<File, ?>) parser).loadResource(tempFilePath.toFile(), combinedConfigs);
			} finally {
				if (tempFilePath != null) {
					try {
						Files.deleteIfExists(tempFilePath);
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		} else if (rawType == Properties.class) {
			Properties props = new Properties();
			Reader reader = null;
			try {
				reader = new StringReader(new String(content, Config.configFileEncoding));
				props.load(reader);
				((ConfigParser<Properties, ?>) parser).loadResource(props, combinedConfigs);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return parser;
	}

}
