package im.webuzz.config.loader;

public interface ConfigStrategy {

	public boolean start();
	public void stop();
	public void add(Class<?> configClazz);
}
