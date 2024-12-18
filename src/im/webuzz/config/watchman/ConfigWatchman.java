package im.webuzz.config;

public interface IConfigWatchman {

	public void watchConfigClass(Class<?> clazz);

	public void startWatchman();

	public void stopWatchman();

}
