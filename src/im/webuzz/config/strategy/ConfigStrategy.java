package im.webuzz.config.strategy;

public interface ConfigStrategy {

	public boolean start();
	public void stop();
	public void add(Class<?> configClazz);
}
