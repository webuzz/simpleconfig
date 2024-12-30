package im.webuzz.config.loader;

public interface ConfigLoader {

	public Class<?>[] prerequisites();
	public boolean start();
	public void stop();
	public void add(Class<?> configClazz);
}
