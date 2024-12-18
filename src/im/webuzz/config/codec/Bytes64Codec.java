package im.webuzz.config.codec;

import im.webuzz.config.IConfigCodec;
import im.webuzz.config.util.Base64;

public class Bytes64Codec implements IConfigCodec<byte[]> {
	@Override
	public String encode(byte[] source) {
		return Base64.byteArrayToBase64(source);
	}

	@Override
	public byte[] decode(String encodedString) {
		return Base64.base64ToByteArray(encodedString);
	}
}