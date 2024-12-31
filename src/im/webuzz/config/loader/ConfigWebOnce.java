package im.webuzz.config.loader;

import java.io.File;
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
import im.webuzz.config.InternalConfigUtils;
import im.webuzz.config.parser.ConfigParser;
import im.webuzz.config.parser.ConfigParserBuilder;
import im.webuzz.config.util.FileUtils;
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
			//if (first == null) {
			//	System.out.println("debug");
			//}
			if (currentQueueKey.equals(first)) {
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
		return HttpRequest.calculateMD5ETag(file.originalWebContent != null ? file.originalWebContent : file.content);
	}

	@Override
	public Class<?>[] prerequisites() {
		return new Class<?>[] { RemoteCCConfig.class };
	}

	@Override
	public boolean start() {
		if (running) return false;
		
		fetchAllConfigurations();
		fetchAllResourceFiles();
		// Wait until all classes' configuration files are loaded & parsed
		running = true;
		
		//System.out.println("Start with size=" + inQueueRequests.size());
		while (inQueueRequests.size() > 0) {
			try {
				Thread.sleep(RemoteCCConfig.webRequestTimeout / 10);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			//System.out.println(".." + inQueueRequests.size() + "..");
		}
		if (Config.configurationLogging) {
			System.out.println("[Config:INFO] Finished loading from remote configuration center, strategy=" + this.getClass().getName());
		}
		return true;
	}

	@Override
	public void stop() {
		// do nothing, there is no watchers which need to be stopped
		if (!running) return;
		long before = System.currentTimeMillis();
		while (inQueueRequests.size() > 0) {
			try {
				Thread.sleep(RemoteCCConfig.webRequestTimeout / 10);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			//System.out.println(".." + inQueueRequests.size() + "..");
			if (System.currentTimeMillis() - before > RemoteCCConfig.webRequestInterval * 3) break;
		}
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
	
	protected void fetchAllResourceFiles() {
		String[] extraFiles = RemoteCCConfig.extraResourceFiles;
		if (extraFiles == null || extraFiles.length == 0) return;
		String[] extraExts = RemoteCCConfig.extraResourceExtensions;
		String configFolder = Config.getConfigFolder();
		for (String path : extraFiles) {
			path = FileUtils.parseFilePath(path);
			File f = new File(configFolder, path);
			String folder = f.getParent();
			if (folder == null) folder = ".";
			String filePath = folder + File.separatorChar;
			String name = f.getName();
			String fileName = null;
			String fileExt = null;
			boolean matched = false;
			for (String extraExt : extraExts) {
				if (path.endsWith(extraExt)) {
					matched = true;
					fileName = name.substring(0, name.length() - extraExt.length());
					fileExt = extraExt;
					break;
				}
			}
			if (!matched) {
				if (Config.configurationLogging) {
					System.out.println("[Config:INFO] Resource file " + path + " is skipped as its extension is not permitted.");
				}
				continue;
			}
			ConfigMemoryFile file = ConfigMemoryFS.checkAndPrepareFile(filePath, fileName, fileExt);
			synchronizeFile(null, fileName, fileExt, file, false, path, -1);
		}
	}

	protected void synchronizeClass(Class<?> clz, long timeout) {
		String keyPrefix = Config.getKeyPrefix(clz);
		if (keyPrefix == null || keyPrefix.length() == 0) {
			// Given class is already updated by default or command line parser
			return;
		}
		String configFolder = Config.getConfigFolder();
		String extension = InternalConfigUtils.getConfigExtension(clz);
		if (extension != null) {
			ConfigMemoryFile file = ConfigMemoryFS.checkAndPrepareFile(configFolder, keyPrefix, extension);
			if (file.content != null) { // already cached
				synchronizeFile(clz, keyPrefix, extension, file, false, null, timeout);
				return;
			}
			return;
		}
		List<String> exts = Config.configurationScanningExtensions;
		if (exts == null || exts.size() == 0) {
			exts = Arrays.asList(new String[] { ".ini" });
		}
		for (String ext : exts) {
			if (ext != null && ext.length() > 0 && ext.charAt(0) == '.' && !ext.equals(extension)) {
				ConfigMemoryFile file = ConfigMemoryFS.checkAndPrepareFile(configFolder, keyPrefix, ext);
				if (file.content != null) { // already cached
					synchronizeFile(clz, keyPrefix, ext, file, false, null, timeout);
					return;
				}
			}
		}
		// Scan all extensions from remote web to see which exists.
		for (String ext : exts) {
			if (ext != null && ext.length() > 0 && ext.charAt(0) == '.') {
				ConfigMemoryFile file = ConfigMemoryFS.checkAndPrepareFile(configFolder, keyPrefix, ext);
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
		final String currentQueueKey = (extraPath != null ? extraPath : (clz == null ? "*" : clz.getName())) + ":" + fileExtension;
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
						if (!Arrays.equals(responseBytes, file.content) || file.modified != lastModified) {
							try {
								saveResponseToFile(file, responseBytes, lastModified);
							} catch (Exception e) {
								e.printStackTrace();
							}
							if (Config.configurationLogging) {
								System.out.println("[Config:INFO] Configuration file " + keyPrefix + fileExtension + " content synchronized remotely.");
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
							System.out.println("[Config:INFO] Fetching configuration file " + keyPrefix + fileExtension + " has no content.");
						}
					}
				} else if (responseCode != 304) {
					if (Config.configurationLogging) {
						System.out.println("[Config:INFO] Fetching configuration file " + keyPrefix + fileExtension+ " code=" + responseCode + ".");
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
			System.out.println("[Config:INFO] Fetching configuration file " + file.name + file.extension+ " code=" + responseCode + ".");
		}
		AtomicInteger count = inQueueRequests.get(requestURL);
		if (count == null || (count != null && count.intValue() > 3)) {
			System.out.println("[Config:ERROR] Failed to load " + requestURL + " for " + (count == null ? 0 : count.intValue()) + " times.");
			return true;
		}
		System.out.println("[Config:WARN] Failed to load " + requestURL + " for " + count.intValue() + " times.");
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
				if (Config.configurationLogging) {
					System.out.println("[Config:INFO] === " + webFile.name + webFile.extension + " ===");
				}
				ConfigParser<?, ?> parser = ConfigParserBuilder.prepareParser(webFile.extension, webFile.content, false);
				if (parser == null) return null;
				if (clz == null) {
					defaultParser = parser;
					Class<?> oldLoader = Config.configurationLoader; // old loader should be this class
					parser.parseConfiguration(Config.class, ConfigParser.FLAG_UPDATE | ConfigParser.FLAG_REMOTE);
					//InternalConfigUtils.recordConfigExtension(Config.class, webFile.extension);
					/*
					for (Class<?> configClazz : Config.getAllConfigurations()) {
						parser.parseConfiguration(configClazz, ConfigParser.FLAG_UPDATE | ConfigParser.FLAG_REMOTE);
					}
					//*/
					if (Config.configurationLogging) {
						System.out.println("[Config:INFO] Configuration " + webFile.name + webFile.extension + " remotely loaded.");
					}
					if (oldLoader != Config.configurationLoader) { // loader changed!
						InternalConfigUtils.checkStrategyLoader();
					}
					return null;
				}
				InternalConfigUtils.recordConfigExtension(clz, webFile.extension); // always update the configuration class' file extension
				if (defaultParser != null) defaultParser.parseConfiguration(clz, ConfigParser.FLAG_UPDATE | ConfigParser.FLAG_REMOTE);
				//if (ignoringFilters != null) System.out.println(ignoringFilters.size());
				parser.parseConfiguration(clz, ConfigParser.FLAG_UPDATE | ConfigParser.FLAG_REMOTE);
				if (Config.configurationLogging) {
					System.out.println("[Config:INFO] Configuration " + clz.getName() + "/" + webFile.name + webFile.extension + " loaded.");
				}
				if (defaultParser == null) return null;
				boolean got = false;
				for (Class<?> configClazz : Config.getAllConfigurations()) {
					if (!got) {
						if (configClazz == clz) got = true;
						continue;
					}
					// after current clz
					String keyPrefix = Config.getKeyPrefix(configClazz);
					if (keyPrefix != null && keyPrefix.length() > 0) break; // Other web call back will continue to deal with this
					defaultParser.parseConfiguration(configClazz, ConfigParser.FLAG_UPDATE | ConfigParser.FLAG_REMOTE);
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
			Class<?> clz = InternalConfigUtils.loadConfigurationClass(reqClass);
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
		file.synchronizeWithRemote(responseBytes, lastModified);
	}

	/*
	 * Default HTTP client to request remote configuration file.
	 */
	public static void asyncWebRequest(String url, String user, String password, long lastModified, String eTag, final Object callback) {
		final HttpRequest req = new HttpRequest();
		req.open("GET", url, true, user, password);
		// if (Config.configurationLogging) System.out.println("[Config:INFO] Requesting " + url);
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
