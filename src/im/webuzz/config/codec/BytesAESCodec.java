package im.webuzz.config.codec;

public class BytesAESCodec implements ConfigCodec<byte[]> {
	@Override
	public String encode(byte[] source) {
		return SimpleAES.b64EncryptBytes(source);
	}

	@Override
	public byte[] decode(String encodedString) {
		return SimpleAES.b64DecryptBytes(encodedString);
	}
}