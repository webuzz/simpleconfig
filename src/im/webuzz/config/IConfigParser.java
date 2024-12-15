package im.webuzz.config;

import java.util.Set;

public interface IConfigParser<S,L> {

	public int FLAG_UPDATE = 1;
	public int FLAG_CHECK = 2;
	public int FLAG_VALIDATE = 4;
	
	/**
	 * Load the source, and consume configuration items, and update internal states, and then return.
	 * The returned object is parser-specific:
	 * For command line arguments parser, non-configuration items will be returned.
	 * For configuration file parser, null will be returned.
	 * @param source
	 * @return 
	 */
	public L loadResource(S source, boolean combinedConfigs);
	
	public int parseConfiguration(Class<?> clz, int flag);

	public Set<String> unusedConfigurationItems();
}
