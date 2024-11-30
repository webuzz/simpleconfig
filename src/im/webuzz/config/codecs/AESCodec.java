package im.webuzz.config.codecs;

import im.webuzz.config.IConfigCodec;
import im.webuzz.config.security.SimpleAES;

public class AESCodec implements IConfigCodec<String> {
	@Override
	public String encode(String source) {
		return SimpleAES.encrypt(source);
	}

	@Override
	public String decode(String encodedString) {
		return SimpleAES.decrypt(encodedString);
	}
}