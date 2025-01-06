package im.webuzz.config.notifier;

import java.util.List;
import java.util.Map;

import im.webuzz.config.Config;
import im.webuzz.config.web.WebCallback;

public class ConfigRemoteNotifier extends ConfigConsoleNotifier {

	@Override
	protected boolean logError(String msg) {
		super.logError(msg);
		if (RemoteNotifierConfig.reporting) {
			RemoteNotifierConfig.webClient.post(false, RemoteNotifierConfig.reportURL, RemoteNotifierConfig.reportAuthUser, RemoteNotifierConfig.reportAuthPassword,
					RemoteNotifierConfig.reportHeaders, compileData(RemoteNotifierConfig.reportDataPattern, msg),
					new WebCallback() {
				
				@Override
				public void got(int responseCode, byte[] responseBytes, long lastModified) {
					System.out.println("Report response code=" + responseCode);
				}
			});
		}
		return true; // continue;
	}
	
	protected String compileData(String data, String msg) {
		StringBuilder builder = null;
		int idx = -1;
		int lastIdx = 0;
		String propKeyPrefix = "${";
		Map<String, List<String>> supportedEnvs = Config.configurationSupportedEnvironments;
		while ((idx = data.indexOf(propKeyPrefix, lastIdx)) != -1) {
			if (builder == null) builder = new StringBuilder();
			builder.append(data.substring(lastIdx, idx));
			lastIdx = idx;
			int beginIdx = idx + propKeyPrefix.length();
			int endIdx = data.indexOf('}', beginIdx);
			if (endIdx == -1) break;
			String propName = data.substring(beginIdx, endIdx);
			String propValue = null;
			if ("error.message".equals(propName)) {
				propValue = msg;
			} else if ("local.server.name".equals(propName)) {
				propValue = RemoteNotifierConfig.localServerName;
			} else if ("local.server.port".equals(propName)) {
				propValue = String.valueOf(RemoteNotifierConfig.localServerPort);
			} else {
				propValue = Config.getEnvironment(propName);
				if (propValue != null && propValue.length() > 0) {
					List<String> supportedValues = supportedEnvs == null ? null : supportedEnvs.get(propName);
					if (supportedValues != null && !supportedValues.isEmpty() && !supportedValues.contains(propValue)) {
						// value is not supported, switch to the first value (considered as the default value
						propValue = supportedValues.get(0);
					}
				}
			}
			if (propValue != null && propValue.length() > 0) {
				builder.append(propValue);
			}
			lastIdx = endIdx + 1;
		}
		if (lastIdx == 0) return data;
		builder.append(data.substring(lastIdx));
		return builder.toString();
	}

}
