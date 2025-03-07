package im.webuzz.config.generator;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import im.webuzz.config.annotation.ConfigClass;
import im.webuzz.config.annotation.ConfigComment;
import im.webuzz.config.annotation.ConfigKeyPrefix;
import im.webuzz.config.annotation.ConfigNotNull;
import im.webuzz.config.annotation.ConfigPattern;

@ConfigClass
@ConfigKeyPrefix("generator")
public class GeneratorConfig {

	public static boolean multipleFiles = true;
	public static boolean readableArrayFormat = true; // For array
	public static boolean readableSetFormat = true; // For set
	public static boolean readableListFormat = true; // For true
	public static boolean readableMapFormat = true; // For true
	@ConfigComment({ 
		"If possible (key is String or other basic type), generate map in",
		"format of xxxx.key=value directly instead of the complicate format",
		"xxxx.entries=[]"
	})
	public static boolean preferKeyValueMapFormat = true;
	
	public static boolean readableObjectFormat = true; // For true
	public static boolean sortedMapFormat = true;

	public static boolean separateFieldsByBlankLines = true;
	
	public static boolean addTypeComment = true;
	public static boolean addTypeAnnotationComment = true;
	public static boolean addFieldComment = true;
	public static boolean addFieldTypeComment = true;
	public static boolean addFieldAnnotationComment = true;
	
	@ConfigComment({"Skip simple type comment for fields of type String, int, boolean."})
	public static boolean skipSimpleTypeComment = false;
	
	public static int startingIndex = 0;

	@ConfigComment("For array or collection, if all items are save typed, generate the type for the array or collection.")
	public static boolean summarizeCollectionType = true;

	public static String[] preferredCodecOrders = null;
	
	@ConfigComment("For XML format, <xxx /> will present as the empty object, instead of <xxx></xxx> or <xxx><empty /></xxx>")
	public static boolean shortenEmptyObject = true;
	@ConfigComment("For XML format, <xxx null=\"true\" /> will present as the empty object, instead of <xxx><null /></xxx>")
	public static boolean shortenNullObject = true;
	
	@ConfigNotNull
	@ConfigPattern("([a-zA-Z0-9]+)")
	public static Map<String, Class<? extends ConfigGenerator<?>>> generatorExtensions = null;
	static {
		Map<String, Class<? extends ConfigGenerator<?>>> generators = new HashMap<>();
		generators.put("ini", ConfigINIGenerator.class);
		generators.put("js", ConfigJSGenerator.class);
		generators.put("xml", ConfigXMLGenerator.class);
		generatorExtensions = Collections.unmodifiableMap(generators);
	}
	
}
