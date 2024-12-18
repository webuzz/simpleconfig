package im.webuzz.config.codec;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import im.webuzz.config.Config;
import im.webuzz.config.ConfigFieldFilter;
import im.webuzz.config.annotation.ConfigPreferredCodec;
import im.webuzz.config.generator.ConfigGenerator;
import im.webuzz.config.generator.GeneratorConfig;
import im.webuzz.config.generator.GeneratorKit;
import im.webuzz.config.parser.ConfigParser;
import im.webuzz.config.parser.ConfigParserBuilder;
import im.webuzz.config.util.TypeUtils;

public class CodecKit {

	public static class CodecItemConfig {
		// Need to configure GeneratorConfig#preferredCodecOrder for the final codec 
		@ConfigPreferredCodec
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
				extension = ".js";
			} else if (firstChar == '<' && lastChar == '>') {
				value = "<config><" + field + ">" + value + "</" + field + "></config>";
				extension = ".xml";
			} else if (firstChar == '[' && lastChar == ']') {
				value = field + "=" + value;
				extension = ".ini";
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
		Map<String, ConfigCodec<?>> codecs = Config.configurationCodecs;
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
					ConfigCodec codec = codecs.get(codecKey);
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
					Class<?> rawType = TypeUtils.getInterfaceParamType(codec.getClass(), ConfigCodec.class);
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
		try {
			ConfigParser<?, ?> parser = ConfigParserBuilder.prepareParser(extension, value, false);
			if (parser == null) return false;
			parser.parseConfiguration(CodecItemConfig.class, ConfigParser.FLAG_UPDATE, null);
		} catch (Throwable e) {
			e.printStackTrace();
		}
		
		ConfigGenerator generator = GeneratorKit.getConfigurationGenerator(extension);
		if (generator == null) return false;
		Class<?> rawType = TypeUtils.getInterfaceParamType(generator.getClass(), ConfigGenerator.class);
		Object builder;
		try {
			builder = rawType.newInstance();
		} catch (Throwable e) {
			e.printStackTrace();
			return false;
		}
		Config.configurationFilters.put(CodecItemConfig.class, new ConfigFieldFilter(null, new String[] { "decoded" }));
		generator.startGenerate(builder, CodecItemConfig.class);
		generator.endGenerate(builder, null);
		System.out.println(builder);
		Config.configurationFilters.remove(CodecItemConfig.class);
		return true;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static boolean decodec(String value, String extension) {
		try {
			ConfigParser<?, ?> parser = ConfigParserBuilder.prepareParser(extension, value, false);
			if (parser == null) return false;
			parser.parseConfiguration(CodecItemConfig.class, ConfigParser.FLAG_UPDATE, null);
		} catch (Throwable e) {
			e.printStackTrace();
		}
		//CodecItemConfig.decoded = CodecItemConfig.encoded;
		ConfigGenerator generator = GeneratorKit.getConfigurationGenerator(extension);
		if (generator == null) return false;
		Class<?> rawType = TypeUtils.getInterfaceParamType(generator.getClass(), ConfigGenerator.class);
		Object builder;
		try {
			builder = rawType.newInstance();
		} catch (Throwable e) {
			e.printStackTrace();
			return false;
		}
		Config.configurationFilters.put(CodecItemConfig.class, new ConfigFieldFilter(null, new String[] { "encoded" }));
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

}
