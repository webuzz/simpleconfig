package im.webuzz.config.generator;

import java.lang.reflect.Field;
import java.util.List;

// T will be StringBuilder, StringBuffer or ByteArrayOutputStream
// T should be a class which can be initialized by new T()
public interface ConfigGenerator<T> {

	public void startGenerate(T builder, Class<?> clz);
	public void endGenerate(T builder, Class<?> clz);

	public byte[] mergeFields(byte[] originalContent, List<Field> fields, List<Field> nextFields);

}
