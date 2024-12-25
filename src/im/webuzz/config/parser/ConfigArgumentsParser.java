package im.webuzz.config.parser;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

import im.webuzz.config.Config;

public class ConfigArgumentsParser implements ConfigParser<String[], String[]> {

	private static final char[] configChars = new char[] {'c', 'o', 'n', 'f', 'i', 'g'};

	private ConfigINIParser iniParser;
	private boolean continueStdinReading;

	public ConfigArgumentsParser() {
		iniParser = new ConfigINIParser();
		continueStdinReading = false;
	}

	@Override
	public int hashCode() {
		return getClass().hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		return obj != null && obj.getClass() == ConfigArgumentsParser.class;
	}

	/**
	 * Parsing arguments into the given properties map:
	 * Recognize configuration items with pattern:
	 * "--c:xxx=###", "--config:xxx=###", "--c-xxx=###", "--config-xxx=###",
	 * store them into the given Properties map, and return the left arguments. 
	 * @param source, Command line arguments
	 * @return Left arguments without configuration items.
	 */
	@Override
	public String[] loadResource(String[] args, boolean combinedConfigs) {
		//String[] args = (String[]) source;
		iniParser.combinedConfigs = combinedConfigs;
		if (args == null || args.length == 0) return args;
		boolean parsed = false;
		List<String> argList = new ArrayList<String>(args.length);
		for (int i = 0; i < args.length; i++) {
			String arg = args[i];
			parsed = parseLine(arg, parsed, argList, args, i);
		}
		if (continueStdinReading) {
			System.out.println("[Config:INFO] Parsing stdin input, until \"--\" or EOF(Ctrl+D)...");
			// Reading key-value from stdin, especially for sensitive key-value, like password, token, etc.
			// stdin input will be read as properties directly
			StringBuilder builder = new StringBuilder();
			Scanner scanner = null;
			try {
				scanner = new Scanner(System.in, Config.configFileEncoding.name());
				while (scanner.hasNext()) {
					String line = scanner.nextLine();
					if (line.startsWith("--")) { // in command line arguments format
						parseLine(line, false, null, null, -1);
						if (line.length() == 2) break; // "--"
						continue;
					}
					builder.append(line).append("\r\n"); // normal properties format
				}
				if (builder.length() > 0) iniParser.props.load(new StringReader(builder.toString()));
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				if (scanner != null) {
					scanner.close();
				}
			}
			System.out.println("[Config:INFO] Done with stdin input.");
		}
		return !parsed ? args : argList.toArray(new String[argList.size()]);
	}

	private boolean parseLine(String arg, boolean parsed, List<String> argList, String[] args, int i) {
		int idx = -1;
		if (arg == null || !arg.startsWith("--c")
				|| (idx = arg.indexOf('=')) == -1) {
			if (parsed && argList != null) argList.add(arg);
			return parsed;
		}
		int startIdx = 3;
		char ch = 0;
		while (startIdx < idx) {
			ch = arg.charAt(startIdx);
			if (ch == '-' || ch == ':') break;
			if (startIdx - 2 >= configChars.length || ch != configChars[startIdx - 2]) break;
			startIdx++;
		}
		if (ch != '-' && ch != ':') {
			if (parsed && argList != null) argList.add(arg);
			return parsed;
		}
		// --c-###=xxxx, --c:###=xxxx, --config-###=xxxx, --config:###=xxxx
		startIdx++;
		if (!parsed) {
			if (argList != null) {
				for (int j = 0; j < i; j++) {
					argList.add(args[j]);
				}
			}
			parsed = true;
		}
		String key = arg.substring(startIdx, idx);//.replace('-', '.');
		String value = arg.substring(idx + 1);
		if ("continue".equals(key) && "stdin".equals(value)) {
			continueStdinReading = true;
			return parsed;
		}
		if (!iniParser.props.containsKey(key)) {
			iniParser.props.put(key, value);
		}
		// logging-path, logging.path => loggingPath
		char[] chars = key.toCharArray();
		int len = chars.length;
		for (int k = len - 2; k > 0; k--) {
			char c = chars[k];
			if (c == '.' || c == '-') {
				char nc = chars[k + 1];
				if ('a' <= nc && nc <= 'z') {
					chars[k] = (char)(nc + 'A' - 'a');
					len--;
					for (int j = k + 1; j < len; j++) {
						chars[j] = chars[j + 1];
					}
					key = new String(chars, 0, len); 
					if (!iniParser.props.containsKey(key)) {
						iniParser.props.put(key, value);
					}
				}
			}
		}
		return parsed;
	}
	@Override
	public int parseConfiguration(Class<?> clz, int flag) {
		return iniParser.parseConfiguration(clz, flag);
	}

	@Override
	public Set<String> unusedConfigurationItems() {
		return iniParser.unusedConfigurationItems();
	}
	
}
