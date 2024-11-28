package im.webuzz.config.codecs;

import im.webuzz.config.IConfigCodec;
import im.webuzz.config.security.SimpleAES;

public class BytesAESCodec implements IConfigCodec<byte[]> {
	@Override
	public String encode(byte[] source) {
		return SimpleAES.b64EncryptBytes(source);
	}

	@Override
	public byte[] decode(String encodedString) {
		return SimpleAES.b64DecryptBytes(encodedString);
	}
}