package im.webuzz.config.generator;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class FieldUtils {

	public static Set<String> keywords = new HashSet<String>(Arrays.asList(new String[] {
			"var", "let", "const",
			"if", "else", "switch", "case", "default",
			"for", "in", "while", "do", "break", "continue", "return",
			"throw", "try", "catch", "throws", "finally",
			"function", "class", "extends", "implements", "interface", "enum", "super", "constructor", "this",
			"new", "delete", "async", "await", "yield",
			"package", "import", "export",
			"pubic", "protected", "private", "static", "final",
			"instanceof", "typeof", "void",
			"prototype", "arguments", "null", "undefined", "true", "fasle",
			"with", "debugger", "valueOf",
			/*"label", "java", "javax", "sun", */
	}));

	public static String wrapAsJSFieldName(Object k) {
		return keywords.contains(k) ? "\"" + k + "\"" : String.valueOf(k);
	}

	public static boolean canAKeyBeAFieldName(String key) {
		if (keywords.contains(key)) return false;
		int len = key.length();
		for (int i = 0; i < len; i++) {
			char c = key.charAt(i);
			if (i == 0) {
				if (!(('a' <= c && c <= 'z') || ('A' <= c && c <= 'Z') || '_' == c || '$' == c)) {
					return false;
				}
			} else {
				if (!(('a' <= c && c <= 'z') || ('A' <= c && c <= 'Z') || '_' == c || '$' == c || ('0' <= c && c <= '9'))) {
					return false;
				}
			}
		}
		return true;
	}

	public static boolean canKeysBeFieldNames(Object[] keys) {
		for (int i = 0; i < keys.length; i++) {
			if (!(keys[i] instanceof String && canAKeyBeAFieldName((String) keys[i]))) return false;
		}
		return true;
	}

	public static boolean canAKeyBeAPrefixedName(String key) {
		int len = key.length();
		for (int i = 0; i < len; i++) {
			char c = key.charAt(i);
			if (!(('a' <= c && c <= 'z') || ('A' <= c && c <= 'Z') || '.' == c || '-' == c || '_' == c || '$' == c || ('0' <= c && c <= '9'))) {
				return false;
			}
		}
		return true;
	}

	public static boolean canKeysBePrefixedNames(Object[] keys) {
		for (int i = 0; i < keys.length; i++) {
			if (!(keys[i] instanceof String && canAKeyBeAPrefixedName((String) keys[i]))) return false;
		}
		return true;
	}

}
