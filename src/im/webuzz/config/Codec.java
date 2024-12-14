package im.webuzz.config;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import im.webuzz.config.annotation.ConfigCodec;
import im.webuzz.config.generator.GeneratorConfig;

class Codec {

	public static class CodecItemConfig {
		// Need to configure GeneratorConfig#preferredCodecOrder for the final codec 
		@ConfigCodec
		public static Object encoded;
		public static Object decoded;
	}

	private static void printUsage() {
		System.out.println("Usage:");
		System.out.println("\t... " + Config.class.getName() + " [--c:xxx=### ...] <configuration file, e.g. config.ini> --run:[encoder|decoder]"
				+ " [--help|-h|--console|string value to be encoded or decoded]");
	}
	
	public static void run(String[] args, int indexOffset, boolean decoder) {
		if (args.length == indexOffset) {
			printUsage();
			return;
		}
		String value = args[indexOffset];
		if ("-h".equals(value) || "--help".equals(value)) {
			printUsage();
			return;
		}
		// Adjust output format
		GeneratorConfig.addFieldComment = false;
		GeneratorConfig.addFieldTypeComment = false;
		GeneratorConfig.addTypeComment = false;
		GeneratorConfig.separateFieldsByBlankLines = false;
		Config.configurationLogging = false;
		
		Config.register(GeneratorConfig.class);
		
		if (!"--console".equals(value)) {
			process(value, decoder);
			return;
		}
		// console mode, each line from the std-in will be processed
		BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
		do {
			String line = null;
			try {
				line = reader.readLine();
			} catch (IOException e) {
				e.printStackTrace();
			}
			if (line == null) break;
			line = line.trim();
			if (line.length() == 0) continue;
			process(line, decoder);
		} while (true);
		try {
			reader.close();
		} catch (IOException e) {
		}
	}
	
	private static void process(String value, boolean decoder) {
		//String value = "{ aes: \"e3670bf5fdb727b2813e29de5f7da9ff\" }";
		//String value = "{ bytes64: \"SGVsbG9Xb3JsZA==\" }";
		//String value = "<aes>e3670bf5fdb727b2813e29de5f7da9ff</aes>";
		//String value = "<bytes64>SGVsbG9Xb3JsZA==</bytes64>";
		//String value = "[bytes64:SGVsbG9Xb3JsZA==]";
		//String value = "[secret:yJL4c8g+r0r0dqIQG67Rdg]";
		//String value = "abc";
		String extension = null;
		int length = value.length();
		String field = decoder ? "decoded" : "encoded";
		if (length > 2) {
			int firstChar = value.charAt(0);
			int lastChar = value.charAt(length - 1);
			if (firstChar == '{' && (lastChar == '}' || lastChar == ',')) {
				value = "{ " + field + ": " + value + " }";
				extension = "js";
			} else if (firstChar == '<' && lastChar == '>') {
				value = "<config><" + field + ">" + value + "</" + field + "></config>";
				extension = "xml";
			} else if (firstChar == '[' && lastChar == ']') {
				value = field + "=" + value;
				extension = "ini";
			} else {
				if (decoder) System.out.println("[WARN] Unknown data format!");
				//extension = "raw";
				if (codecRaw(value, decoder)) return;
				if (decoder) {
					System.out.println("[ERROR] Failed to decode the given raw value, all existed codecs tried.");
				} else {
					System.out.println("[ERROR] Failed to encode the value, all existed codecs tried.");
				}
				return;
			}
		}
		if (decoder) {
			if (!decodec(value, extension)) {
				System.out.println("[ERROR] Failed to decode the given value as extension \"" + extension + "\".");
			}
		} else {
			if (!encode(value, extension)) {
				System.out.println("[ERROR] Failed to encode the value as extension \"" + extension + "\".");
			}
		}
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static boolean codecRaw(String value, boolean decoder) {
		// Try to decode it rawly
		Map<String, IConfigCodec<?>> codecs = Config.configurationCodecs;
		if (codecs == null) return false;
		boolean gotCha = false;
		String[] keys = GeneratorConfig.preferredCodecOrders;
		boolean all = false;
		if (keys == null|| keys.length == 0) {
			keys = codecs.keySet().toArray(new String[codecs.size()]);
			all = true;
		}
		do {
			for (String codecKey : keys) {
				try {
					IConfigCodec codec = codecs.get(codecKey);
					if (decoder) {
						Object decoded = codec.decode(value);
						if (decoded != null) {
							System.out.println("Decoded by codec \"" + codecKey + "\":");
							System.out.println(decoded);
							//return true;
							gotCha = true;
						}
						continue;
					}
					String decoded = null;
					Class<?> rawType = Utils.getInterfaceParamType(codec.getClass(), IConfigCodec.class);
					if (rawType == String.class) {
						decoded = codec.encode(value);
					} else if (rawType == byte[].class) {
						decoded = codec.encode(value.getBytes(Config.configFileEncoding));
					} else {
						continue; // skip
					}
					if (decoded != null) {
						System.out.println("Encoded by codec \"" + codecKey + "\":");
						System.out.println(decoded);
						//return true;
						gotCha = true;
					}
				} catch (Throwable e) {
					//e.printStackTrace();
				}
			}
			if (all) break;
			// check other codecs besides the preferred codecs
			List<String> leftCodecs = new ArrayList<String>();
			List<String> preferreds = Arrays.asList(keys);
			for (String key : codecs.keySet()) {
				if (preferreds.contains(key)) continue;
				leftCodecs.add(key);
			}
			if (leftCodecs.size() == 0) break;
			keys = leftCodecs.toArray(new String[leftCodecs.size()]);
			all = true;
		} while (!gotCha);
		return gotCha;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static boolean encode(String value, String extension) {
		Map<String, Class<? extends IConfigParser<?, ?>>> parsers = Config.configurationParsers;
		if (parsers == null) return false;
		Class<? extends IConfigParser<?, ?>> clazz = parsers.get(extension);
		if (clazz == null) return false;
		try {
			IConfigParser<?, ?> parser = prepareParserWithFile(clazz, value);
			parser.parseConfiguration(CodecItemConfig.class, true);
		} catch (Throwable e) {
			e.printStackTrace();
		}
		
		Config.configurationFilters.put(CodecItemConfig.class, new ConfigFieldFilter(null, new String[] { "decoded" }));
		IConfigGenerator generator = ConfigGenerator.getConfigurationGenerator(extension);
		if (generator == null) return false;
		Class<?> rawType = Utils.getInterfaceParamType(generator.getClass(), IConfigGenerator.class);
		Object builder = createABuilder(rawType);
		generator.startGenerate(builder, CodecItemConfig.class);
		generator.endGenerate(builder, null);
		System.out.println(builder);
		Config.configurationFilters.remove(CodecItemConfig.class);
		return true;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static boolean decodec(String value, String extension) {
		Map<String, Class<? extends IConfigParser<?, ?>>> parsers = Config.configurationParsers;
		if (parsers == null) return false;
		Class<? extends IConfigParser<?, ?>> clazz = parsers.get(extension);
		if (clazz == null) return false;
		try {
			IConfigParser<?, ?> parser = prepareParserWithFile(clazz, value);
			parser.parseConfiguration(CodecItemConfig.class, true);
		} catch (Throwable e) {
			e.printStackTrace();
		}
		//CodecItemConfig.decoded = CodecItemConfig.encoded;
		Config.configurationFilters.put(CodecItemConfig.class, new ConfigFieldFilter(null, new String[] { "encoded" }));
		IConfigGenerator generator = ConfigGenerator.getConfigurationGenerator(extension);
		if (generator == null) return false;
		Class<?> rawType = Utils.getInterfaceParamType(generator.getClass(), IConfigGenerator.class);
		Object builder = createABuilder(rawType);
		generator.startGenerate(builder, CodecItemConfig.class);
		generator.endGenerate(builder, null);
		if (builder instanceof byte[]) {
			System.out.println(new String((byte[]) builder, Config.configFileEncoding));
		} else {
			System.out.println(builder.toString());
		}
		Config.configurationFilters.remove(CodecItemConfig.class);
		return true;
	}

	private static Object createABuilder(Class<?> rawType) {
		try {
			return rawType.newInstance();
		} catch (Throwable e) {
			e.printStackTrace();
			return null;
		}
	}
	
	@SuppressWarnings("unchecked")
	private static IConfigParser<?, ?> prepareParserWithFile(Class<? extends IConfigParser<?, ?>> clazz, String str) throws Exception {
		IConfigParser<?, ?> parser = clazz.newInstance();
		Class<?> rawType = Utils.getInterfaceParamType(clazz, IConfigParser.class);
		if (rawType == String.class) {
			((IConfigParser<String, ?>) parser).loadResource(str, false);
		} else if (rawType == InputStream.class) {
			InputStream fis = null;
			try {
				fis = new ByteArrayInputStream(str.getBytes(Config.configFileEncoding));
				((IConfigParser<InputStream, ?>) parser).loadResource(fis, false);
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
			((IConfigParser<byte[], ?>) parser).loadResource(str.getBytes(Config.configFileEncoding), false);
		} else if (rawType == Properties.class) {
			Properties props = new Properties();
			InputStream fis = null;
			InputStreamReader reader = null;
			try {
				fis = new ByteArrayInputStream(str.getBytes(Config.configFileEncoding));
				reader = new InputStreamReader(fis, Config.configFileEncoding);
				props.load(reader);
				((IConfigParser<Properties, ?>) parser).loadResource(props, false);
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

}
