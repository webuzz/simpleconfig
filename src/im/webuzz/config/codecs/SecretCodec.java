package im.webuzz.config.codecs;

import im.webuzz.config.IConfigCodec;
import im.webuzz.config.security.SecurityKit;

public class SecretCodec implements IConfigCodec<String> {
	@Override
	public String encode(String source) {
		return SecurityKit.encrypt(source);
	}

	@Override
	public String decode(String encodedString) {
		return SecurityKit.decrypt(encodedString);
	}
}