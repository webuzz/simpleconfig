package im.webuzz.config.codecs;

import im.webuzz.config.Base64;
import im.webuzz.config.IConfigCodec;

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