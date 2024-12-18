package im.webuzz.config.codec;

public class AESCodec implements ConfigCodec<String> {
	@Override
	public String encode(String source) {
		return SimpleAES.encrypt(source);
	}

	@Override
	public String decode(String encodedString) {
		return SimpleAES.decrypt(encodedString);
	}
}