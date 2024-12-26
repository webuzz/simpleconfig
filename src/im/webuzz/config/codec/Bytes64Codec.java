package im.webuzz.config.codec;

import im.webuzz.config.util.Base64;

public class Bytes64Codec implements ConfigCodec<byte[]> {
	@Override
	public String encode(byte[] source) {
		return Base64.byteArrayToBase64(source);
	}

	@Override
	public byte[] decode(String encodedString) {
		return Base64.base64ToByteArray(encodedString);
	}
	
	@Override
	public boolean equals(Object obj) {
		if (obj instanceof Bytes64Codec) return true;
		return false;
	}
	
	@Override
	public int hashCode() {
		return this.getClass().getName().hashCode();
	}

}