package im.webuzz.config;

// T will be StringBuilder, StringBuffer or ByteArrayOutputStream
// T should be a class which can be initialized by new T()
public interface IConfigGenerator<T> {

	public void startGenerate(T builder, Class<?> clz);
	public void endGenerate(T builder, Class<?> clz);

}
