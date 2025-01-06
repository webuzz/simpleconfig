package im.webuzz.config.common;

public class StringUtils {

	public static String formatAsProperties(String str) {
		str = str.replaceAll("\\\\", "\\\\\\\\").replaceAll("\r", "\\\\r").replaceAll("\n", "\\\\n").replaceAll("\t", "\\\\t").replaceAll("(#|!)", "\\\\$1");
		int length = str.length();
		if (length > 0) {
			if (str.charAt(length - 1) == ' ') str = str.substring(0, length - 1) + "\\ ";
			if (str.charAt(0) == ' ') str = "\\" + str;
		}
		return str;
	}

	public static String formatAsString(String str) {
		return str.replaceAll("\\\\", "\\\\\\\\").replaceAll("\r", "\\\\r").replaceAll("\n", "\\\\n").replaceAll("\t", "\\\\t").trim();
	}

}
