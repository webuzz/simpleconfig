package im.webuzz.config.codec;

import im.webuzz.config.IConfigCodec;

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