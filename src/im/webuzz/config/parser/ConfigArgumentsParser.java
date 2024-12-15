package im.webuzz.config.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import im.webuzz.config.IConfigParser;

public class ConfigArgumentsParser implements IConfigParser<String[], String[]> {

	private static final char[] configChars = new char[] {'c', 'o', 'n', 'f', 'i', 'g'};

	private ConfigINIParser iniParser;
	
	public ConfigArgumentsParser() {
		iniParser = new ConfigINIParser();
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
		List<String> argList = null;
		for (int i = 0; i < args.length; i++) {
			String arg = args[i];
			int idx = -1;
			if (arg == null || !arg.startsWith("--c")
					|| (idx = arg.indexOf('=')) == -1) {
				if (parsed) argList.add(arg);
				continue;
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
				if (parsed) argList.add(arg);
				continue;
			}
			// --c-###=xxxx, --c:###=xxxx, --config-###=xxxx, --config:###=xxxx
			startIdx++;
			if (!parsed) {
				argList = new ArrayList<String>(args.length);
				for (int j = 0; j < i; j++) {
					argList.add(args[j]);
				}
				parsed = true;
			}
			String key = arg.substring(startIdx, idx);//.replace('-', '.');
			String value = arg.substring(idx + 1);
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
		}
		return !parsed ? args : argList.toArray(new String[argList.size()]);
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
