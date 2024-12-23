package im.webuzz.config.loader;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import im.webuzz.config.Config;
import im.webuzz.config.parser.ConfigParser;
import im.webuzz.config.parser.ConfigParserBuilder;
import im.webuzz.config.util.HttpRequest;

public class ConfigWebOnce implements ConfigLoader {

	protected boolean running = false;
	
	private ConfigParser<?, ?> defaultParser = null;

	protected Map<String, AtomicInteger> inQueueRequests = new ConcurrentHashMap<>();

	protected Object queueTaskMutex = new Object();
	protected Queue<String> clazzQueue = new ConcurrentLinkedQueue<String>();
	protected Map<String, Callable<Object>> taskCallbacks = new ConcurrentHashMap<>();

	protected void processQueue(String currentQueueKey, Callable<Object> currentTask) {
		List<Callable<Object>> tasks = null;
		synchronized (queueTaskMutex) {
			String first = clazzQueue.peek();
			if (first.equals(currentQueueKey)) {
				if (currentTask != null) {
					tasks = new ArrayList<Callable<Object>>();
					tasks.add(currentTask);
				}
				clazzQueue.poll();
				int currentIdx = -1;
				while ((first = clazzQueue.peek()) != null) {
					Callable<Object> task = taskCallbacks.get(first);
					if (task == null) {
						// If the next is same name with a different extension, it is safe to remove it already
						// e.g. config:js is loaded with current task is not null (file content is loaded),
						// the following config:ini or config.xml is not useless, try to ignore them
						if (currentTask != null) {
							if (currentIdx == -1) currentIdx = currentQueueKey.indexOf(':');
							int idx = first.indexOf(':');
							if (currentIdx == idx && currentQueueKey.substring(0, idx).equals(first.substring(0, idx))) {
								clazzQueue.poll();
								continue;
							}
						}
						break;
					}
					taskCallbacks.remove(first);
					if (tasks == null) tasks = new ArrayList<Callable<Object>>();
					tasks.add(task);
					clazzQueue.poll();
				}
			} else {
				if (currentTask != null) {
					if (clazzQueue.contains(currentQueueKey)) {
						taskCallbacks.put(currentQueueKey, currentTask);
					} // else // already removed, e.g. config.js is already loaded, the following loaded config.ini will be discarded 
				} else {
					clazzQueue.remove(currentQueueKey);
				}
			}
		}
		if (tasks != null) { // Task execution is considered as time-consuming
			for (Callable<Object> task : tasks) {
				try {
					task.call();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	protected String getFileMD5ETag(ConfigMemoryFile file) {
		if (file == null || !RemoteCCConfig.webRequestSupportsMD5ETag) return null;
		return HttpRequest.calculateMD5ETag(file.content);
	}


	@Override
	public boolean start() {
		if (running) return false;
		
		Config.register(RemoteCCConfig.class);

		fetchAllConfigurations();
		// Wait until all classes' configuration files are loaded & parsed
		running = true;
		
		int refreshedCount = 1;
		//int checkCount = 0;
		//System.out.println("Start with size=" + inQueueRequests.size());
		while (inQueueRequests.size() > 0) {
			try {
				Thread.sleep(RemoteCCConfig.webRequestTimeout * refreshedCount / 10);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			//System.out.println(".." + inQueueRequests.size() + "..");
		}
		System.out.println("[INFO] Started.");
		return true;
	}

	@Override
	public void stop() {
		// do nothing, there is no watchers which need to be stopped
		running = false;
	}

	@Override
	public void add(Class<?> configClazz) {
		if (!running) return; // Not started yet
		// to load
		//System.out.println("Adding " + configClazz.getName());
		if (defaultParser != null) defaultParser.parseConfiguration(configClazz, ConfigParser.FLAG_UPDATE | ConfigParser.FLAG_REMOTE);
		synchronizeClass(configClazz, -1);
	}

	protected void fetchAllConfigurations() {
		String cfgName = Config.getConfigMainName();
		String cfgExt = Config.getConfigMainExtension();
		ConfigMemoryFile memFile = ConfigMemoryFS.checkAndPrepareFile(Config.getConfigFolder(), cfgName, cfgExt);
		synchronizeFile(null, cfgName, cfgExt, memFile, true, null, -1);
		
		Class<?>[] configs = Config.getAllConfigurations();
		for (int i = 0; i < configs.length; i++) {
			synchronizeClass(configs[i], -1);
		}
	}
	
	protected void synchronizeClass(Class<?> clz, long timeout) {
		String keyPrefix = Config.getKeyPrefix(clz);
		if (keyPrefix == null || keyPrefix.length() == 0) {
			// Given class is already updated by default or command line parser
			return;
		}
		List<String> exts = Config.configurationScanningExtensions;
		if (exts == null || exts.size() == 0) {
			exts = Arrays.asList(new String[] { ".ini" });
		}
		String configFolder = Config.getConfigFolder();
		for (String ext : exts) {
			if (ext != null && ext.length() > 0 && ext.charAt(0) == '.') {
				//WebFile file = load(keyPrefix, ext);
				ConfigMemoryFile file = ConfigMemoryFS.checkAndPrepareFile(configFolder, keyPrefix, ext);
				if (file != null) { // already cached
					synchronizeFile(clz, keyPrefix, ext, file, false, null, timeout);
					return;
				}
			}
		}
		// Scan all extensions
		for (String ext : exts) {
			if (ext != null && ext.length() > 0 && ext.charAt(0) == '.') {
				ConfigMemoryFile file = ConfigMemoryFS.checkAndPrepareFile(configFolder, keyPrefix, ext);
				//WebFile file = new WebFile(keyPrefix, ext, null, -1, null);
				synchronizeFile(clz, keyPrefix, ext, file, false, null, timeout);
			}
		}
	}

	protected void synchronizeFile(final Class<?> clz, final String keyPrefix, final String fileExtension, ConfigMemoryFile file,
			final boolean globalConfig, final String extraPath, final long timeout) {
		final String requestURL = buildURL(keyPrefix, fileExtension, extraPath);
		if (requestURL == null) return;
		final StringBuilder builder = new StringBuilder();
		AtomicInteger count = inQueueRequests.get(requestURL);
		if (count == null) {
			inQueueRequests.put(requestURL, new AtomicInteger(1));
		} else {
			count.incrementAndGet();
		}
		final String currentQueueKey = (clz == null ? "*" : clz.getName()) + ":" + fileExtension;
		synchronized (queueTaskMutex) {
			clazzQueue.add(currentQueueKey);
		}
		final long last = System.currentTimeMillis();
		WebCallback callback = new WebCallback() {
			
			@Override
			public void got(int responseCode, byte[] responseBytes, long lastModified) {
				if (responseCode == 0) { // response code == 0, error occurs
					if (processNoResponseError(requestURL, responseCode, file, last, this)) {
						processQueue(currentQueueKey, null);
						if (timeout > 0) synchronized (builder) {
							builder.append(1);
							builder.notify();
						}
					}
					return;
				}
				if (responseCode == 200) { // && (lastModified < 0 || requestTime == currentRequestTime)) { // HTTP OK
					if (responseBytes != null && responseBytes.length > 0) {
						//System.out.println("GOOOOOOOOOOOOOOT " + currentQueueKey);
						byte[] localBytes = file.content; // readContent(keyPrefix, fileExtension);
						if (!Arrays.equals(responseBytes, localBytes)) {
							saveResponseToFile(file, responseBytes, lastModified);
							if (RemoteCCConfig.synchronizing) {
								System.out.println("[Config] Configuration file " + keyPrefix + fileExtension + " content synchronized remotely.");
							}
							processQueue(currentQueueKey, buildParserCallback(clz, file));
							inQueueRequests.remove(requestURL);
							if (timeout > 0) synchronized (builder) {
								builder.append(1);
								builder.notify();
							}
							return;
						}
					} else {
						if (Config.configurationLogging) {
							System.out.println("[Config] Fetching configuration file " + keyPrefix + fileExtension + " has no content.");
						}
					}
				} else if (responseCode != 304) {
					if (Config.configurationLogging) {
						System.out.println("[Config] Fetching configuration file " + keyPrefix + fileExtension+ " code=" + responseCode + ".");
					}
				}
				processQueue(currentQueueKey, null);
				if (responseCode == 304 || responseCode == 200 || responseCode == 404) {
					inQueueRequests.remove(requestURL);
				}
				if (timeout > 0) synchronized (builder) {
					builder.append(1);
					builder.notify();
				}
			}
		};
		sendWebRequest(requestURL, RemoteCCConfig.globalServerAuthUser, RemoteCCConfig.globalServerAuthPassword,
				file.modified, getFileMD5ETag(file), callback);
		
		if (timeout > 0) {
			try {
				synchronized (builder) {
					if (builder.length() <= 0) builder.wait(timeout);
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
	
	// Return if we need to process queue and notify the waiter
	protected boolean processNoResponseError(final String requestURL, int responseCode, ConfigMemoryFile file,
			final long waitingTime, final WebCallback thisCallback) {
		if (Config.configurationLogging) {
			System.out.println("[Config] Fetching configuration file " + file.name + file.extension+ " code=" + responseCode + ".");
		}
		AtomicInteger count = inQueueRequests.get(requestURL);
		if (count == null || (count != null && count.intValue() > 3)) {
			System.out.println("Failed to load " + requestURL + " for " + (count == null ? 0 : count.intValue()) + " times.");
			return true;
		}
		System.out.println("Failed to load " + requestURL + " for " + count.intValue() + " times.");
		long now = System.currentTimeMillis();
		if (now - waitingTime > RemoteCCConfig.webRequestTimeout * 2 / 3) {
			// TODO: There is no enough time left
		}
		count.incrementAndGet();
		HttpRequest.runTask(new Runnable() {
			@Override
			public void run() {
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
				}
				sendWebRequest(requestURL, RemoteCCConfig.globalServerAuthUser, RemoteCCConfig.globalServerAuthPassword,
						file.modified, getFileMD5ETag(file), thisCallback);
			}
		});
		return false;
	}

	protected Callable<Object> buildParserCallback(final Class<?> clz, final ConfigMemoryFile webFile) {
		return new Callable<Object>() {
			public Object call() throws Exception {
				System.out.println("========== " + webFile.name + webFile.extension);
				ConfigParser<?, ?> parser = ConfigParserBuilder.prepareParser(webFile.extension, webFile.content, false);
				if (parser == null) return null;
				if (clz == null) {
					defaultParser = parser;
					for (Class<?> configClazz : Config.getAllConfigurations()) {
						parser.parseConfiguration(configClazz, ConfigParser.FLAG_UPDATE | ConfigParser.FLAG_REMOTE);
					}
					if (Config.configurationLogging) {
						System.out.println("[Config] Configuration " + webFile.name + webFile.extension + " loaded.");
					}
					return null;
				}
				Config.recordConfigExtension(clz, webFile.extension); // always update the configuration class' file extension
				//if (ignoringFilters != null) System.out.println(ignoringFilters.size());
				parser.parseConfiguration(clz, ConfigParser.FLAG_UPDATE | ConfigParser.FLAG_REMOTE);
				if (Config.configurationLogging) {
					System.out.println("[Config] Configuration " + clz.getName() + "/" + webFile.name + webFile.extension + " loaded.");
				}
				return null;
			}
		};
	}

	protected static String buildURL(String keyPrefix, String fileExtension,  String extraPath) {
		String url = extraPath == null ? RemoteCCConfig.targetURLPattern : RemoteCCConfig.extraTargetURLPattern;
		if (url == null) return null;
		if (extraPath == null) { // configurations
			url = url.replaceAll("\\$\\{config.key.prefix\\}", keyPrefix);
			if (fileExtension != null && fileExtension.length() > 0
					&& url.indexOf("${config.file.extension}") != -1) {
				url = url.replaceAll("\\$\\{config.file.extension\\}", fileExtension);
			}
		} else { // resource files
			url = url.replaceAll("\\$\\{extra.file.path\\}", extraPath);
		}
		String server = RemoteCCConfig.globalServerURLPrefix;
		String localName = RemoteCCConfig.localServerName;
		if (server != null) {
			url = url.replaceAll("\\$\\{server.url.prefix\\}", server);
		}
		if (localName != null && url.indexOf("${local.server.name}") != -1) {
			url = url.replaceAll("\\$\\{local.server.name\\}", localName);
		}
		return url;
	}

	protected void sendWebRequest(String url, String user, String password, long lastModified, String eTag, WebCallback callback) {
		boolean done = false;
		String reqClass = RemoteCCConfig.webRequestClient;
		if (reqClass != null && reqClass.length() > 0) {
			// Other class may be used to request remote configuration file besides HTTP client
			Class<?> clz = Config.loadConfigurationClass(reqClass);
			if (clz != null) {
				try {
					Method m = clz.getMethod("asyncWebRequest", 
							String.class, // url
							String.class, // user
							String.class, // password
							long.class, // lastModified
							String.class, // eTag
							Object.class // WebCallback.class callback
					);
					m.invoke(clz, url, user, password, lastModified, eTag, callback);
					done = true;
				} catch (NoSuchMethodException e) {
					// do nothing
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		if (!done) { // fall back to HTTP request client
			asyncWebRequest(url, user, password, lastModified, eTag, callback);
		}
	}

	protected void saveResponseToFile(ConfigMemoryFile file, byte[] responseBytes, long lastModified) {
		file.loadFromWebResponse(responseBytes, lastModified);
	}

	/*
	 * Default HTTP client to request remote configuration file.
	 */
	public static void asyncWebRequest(String url, String user, String password, long lastModified, String eTag, final Object callback) {
		final HttpRequest req = new HttpRequest();
		req.open("GET", url, true, user, password);
		req.registerOnLoaded(new Callable<Object>() {
			@Override
			public Object call() throws Exception {
				if (callback instanceof WebCallback) {
					long lastModified = HttpRequest.parseHeaderTimestamp(req, "Last-Modified");
					((WebCallback) callback).got(req.getStatus(), req.getResponseBytes(), lastModified);
				} // else do nothing
				return null;
			}
		});
		if (lastModified > 0) {
			req.setRequestHeader("If-Modified-Since", HttpRequest.getHTTPDateString(lastModified));
			if (eTag != null && eTag.length() > 0) {
				req.setRequestHeader("If-None-Match", eTag);
			}
		}
		req.send(); // Normal HTTP request may try to create new thread to do asynchronous job. NIO request may not.
	}

}
