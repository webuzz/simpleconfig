package im.webuzz.config;

public interface IConfigGenerator {

	public void startGenerate(StringBuilder builder, Class<?> clz, boolean combinedConfigs);
	public void endGenerate(StringBuilder builder, Class<?> clz, boolean combinedConfigs);
	//public void startClassBlock(StringBuilder builder);
	//public void endClassBlock(StringBuilder builder);
	
	public void check(String clazzName, String fieldName);

}
