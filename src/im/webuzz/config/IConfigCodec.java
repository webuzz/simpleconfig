package im.webuzz.config;

/**
 * Interface for encoding and decoding configuration data.
 * @param <T> The type of object to be encoded or decoded.
 */
public interface IConfigCodec<T> {

    /**
     * Encodes the given object into a string.
     * @param source The object to encode.
     * @return The encoded string representation of the object.
     */
    String encode(T source);

    /**
     * Decodes the given string into an object.
     * @param encodedString The string to decode.
     * @return The decoded object.
     */
    T decode(String encodedString);

}
