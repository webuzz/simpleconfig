package im.webuzz.config.codec;

import im.webuzz.config.Config;
import im.webuzz.config.common.Base64;

public class Base64Codec implements ConfigCodec<String> {
	@Override
	public String encode(String source) {
		return Base64.byteArrayToBase64(source.getBytes(Config.configFileEncoding));
	}

	@Override
	public String decode(String encodedString) {
		byte[] bytes = Base64.base64ToByteArray(encodedString);
		return new String(bytes, Config.configFileEncoding);
	}
	
	@Override
	public boolean equals(Object obj) {
		if (obj instanceof Base64Codec) return true;
		return false;
	}
	
	@Override
	public int hashCode() {
		return this.getClass().getName().hashCode();
	}

}