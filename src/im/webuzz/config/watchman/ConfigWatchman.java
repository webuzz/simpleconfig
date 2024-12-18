package im.webuzz.config.watchman;

public interface ConfigWatchman {

	public void watchConfigClass(Class<?> clazz);

	public void startWatchman();

	public void stopWatchman();

}
