package im.webuzz.config.codec;

import im.webuzz.config.IConfigCodec;
import im.webuzz.config.SimpleAES;

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