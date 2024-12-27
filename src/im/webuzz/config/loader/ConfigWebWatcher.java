package im.webuzz.config.loader;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import im.webuzz.config.Config;

public class ConfigWebWatcher extends ConfigWebOnce implements Runnable {

	protected BlockingQueue<Class<?>> queue = new LinkedBlockingQueue<Class<?>>();

	@Override
	public boolean start() {
		if (!super.start()) return false;
		if (Config.configurationLogging) {
			System.out.println("[Config:INFO] Starting remote web configuration center watcher");
		}
		Thread webThread = new Thread(this, "Remote Web Configuration Center Watcher");
		webThread.setDaemon(true);
		webThread.start();
		return true;
	}
	
	@Override
	public void add(Class<?> configClazz) {
		if (!running) return; // Not started yet
		String keyPrefix = Config.getKeyPrefix(configClazz);
		if (keyPrefix == null || keyPrefix.length() == 0) return;
		try {
			queue.put(configClazz);
		} catch (InterruptedException e) {
			//e.printStackTrace();
			// Do nothing, watchman will try to synchronize this class from remote server later (in 10s)
		}
	}
	@Override
	public void run() {
		while (running) {
			try {
				int seconds = Math.max(1, (int) (RemoteCCConfig.webRequestInterval / 1000));
				for (int i = 0; i < seconds; i++) {
					Class<?> clazz = null;
					try {
						clazz = queue.poll(1000, TimeUnit.MILLISECONDS);
						//Thread.sleep(1000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					if (!running) {
						break;
					}
					if (clazz != null) {
						synchronizeClass(clazz, RemoteCCConfig.webRequestTimeout);								
						i = i > seconds / 2 ? Math.max(seconds / 2 - 2, 1) : 0; // restart sleep waiting
					}
				}
				fetchAllConfigurations();
				fetchAllResourceFiles();
				//refreshAll(false, WebConfig.webRequestTimeout);
				if (!RemoteCCConfig.synchronizing) {
					continue;
				}
			} catch (Throwable e) {
				// might be OOM
				try {
					Thread.sleep(10);
				} catch (InterruptedException e1) {
				}
			}
		}
	}
	
}
