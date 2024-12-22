package im.webuzz.config.loader;

public interface ConfigLoader {

	public boolean start();
	public void stop();
	public void add(Class<?> configClazz);
}
