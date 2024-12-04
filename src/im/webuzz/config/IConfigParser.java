package im.webuzz.config;

public interface IConfigParser<S,L> {

	/**
	 * Load the source, and consume configuration items, and update internal states, and then return.
	 * The returned object is parser-specific:
	 * For command line arguments parser, non-configuration items will be returned.
	 * For configuration file parser, null will be returned.
	 * @param source
	 * @return 
	 */
	public L loadResource(S source, boolean combinedConfigs);
	
	public int parseConfiguration(Class<?> clz, boolean updating);

}
