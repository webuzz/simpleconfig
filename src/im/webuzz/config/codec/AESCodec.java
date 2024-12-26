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
	
	@Override
	public boolean equals(Object obj) {
		if (obj instanceof AESCodec) return true;
		return false;
	}
	
	@Override
	public int hashCode() {
		return this.getClass().getName().hashCode();
	}

}