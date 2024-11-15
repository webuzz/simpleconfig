package im.webuzz.config;

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

	public static boolean addTypeComment = true;
	public static boolean addFieldComment = true;
	public static boolean addFieldTypeComment = true;
	@ConfigComment({"Skip simple type comment for fields of type String, int, boolean."})
	public static boolean skipSimpleTypeComment = true;
	public static boolean skipUnchangedLines = false;
	public static boolean skipObjectUnchangedFields = true;
	public static int startingIndex = 0;

	@ConfigComment("For array or collection, if all items are save typed, generate the type for the array or collection.")
	public static boolean summarizeCollectionType = true;
}
