package im.webuzz.config;

public interface IConfigGenerator {

	public void startGenerate(StringBuilder builder, Class<?> clz, boolean combinedConfigs);
	public void endGenerate(StringBuilder builder, Class<?> clz, boolean combinedConfigs);

}
