package im.webuzz.config.watchman;

public interface ConfigStrategy {

	public boolean start();
	public void stop();
	public void add(Class<?> configClazz);
}
