/*******************************************************************************
 * Copyright (c) 2010 - 2024 webuzz.im and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Source hosted at
 * https://github.com/webuzz/simpleconfig
 * 
 * Contributors:
 *   Zhou Renjian / zhourenjian@gmail.com - initial API and implementation
 *******************************************************************************/

package im.webuzz.config.parser;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Comment;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

import im.webuzz.config.Config;
import im.webuzz.config.codec.ConfigCodec;
import im.webuzz.config.common.StringUtils;

public class ConfigXMLParser implements ConfigParser<InputStream, Object> {

	// To add comments to the *.ini format while converting .xml format to .ini format.
	// This is to help comparing parsed .ini format with the original .ini format.
	public static boolean xmlToINIAddComments = false;
	
	public static boolean xmlToINIDebugOutput = false;
	
	private static final Set<String> primitives = new HashSet<String>();
	private static final Set<String> basicData = new HashSet<String>();
	private static final Set<String> knownObjects = new HashSet<String>();
	
	static {
		//primitives.add("string");
		//primitives.add("integer");
		primitives.add("int");
		primitives.add("long");
		primitives.add("boolean");
		primitives.add("short");
		primitives.add("byte");
		primitives.add("char");
		primitives.add("double");
		primitives.add("float");
		//primitives.add("i");
		
		basicData.add("String");
		basicData.add("Class");
		basicData.add("Enum");
		basicData.add("Boolean");
		basicData.add("Integer");
		basicData.add("Long");
		basicData.add("Short");
		basicData.add("Byte");
		basicData.add("Double");
		basicData.add("Float");
		basicData.add("Character");
		basicData.add("BigDecimal");
		basicData.add("BigInteger");
		
		knownObjects.add("object");
		knownObjects.add("annotation");
		knownObjects.add("map");
		knownObjects.add("set");
		knownObjects.add("list");
		knownObjects.add("array");
		knownObjects.add("null");
		knownObjects.add("empty");
	}
	
	private static enum NodeType {
		none,
		unknown,
		plain,
		emptyObject,
		emptyString,
		codecObject,
		codecDirect,
		nullValue,
		basicData,
		basicDirect,
		mapEntries,
		mapKnown,
		mapGeneric,
		mapDirect,
		collection,
		plainCollection,
		array,
		list,
		set,
		arrayDirect,
		listDirect,
		setDirect,
		annotation,
		object,
	};

	private ConfigINIParser iniParser;
	
	public ConfigXMLParser() {
		super();
		iniParser = new ConfigINIParser();
	}

	
	@Override
	public Object loadResource(InputStream fis, boolean combinedConfigs) {
		if (fis == null) return null;
		iniParser.combinedConfigs = combinedConfigs;
		InputStreamReader reader = null;
		try {
			//fis = new FileInputStream(source);
			InputStream is = convertToProperties(fis);
			reader = new InputStreamReader(is, Config.configFileEncoding);
			iniParser.props.load(reader);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		return null;
	}

	@Override
	public int parseConfiguration(Class<?> clz, int flag) {
		return iniParser.parseConfiguration(clz, flag);
	}

	private NodeType parseType(Element o, NodeType containerType) {
		if (o == null) return NodeType.unknown;
		NamedNodeMap attributes = o.getAttributes();
		if (attributes != null && attributes.getLength() > 0) {
			Node nullItem = attributes.getNamedItem("null");
			if (nullItem != null) {
				String nullValue = nullItem.getNodeValue();
				if ("true".equals(nullValue)) return NodeType.nullValue;
			}
			// <xxx class="array"> ...
			// <xxx class="array:..."> ...
			// <xxx class="list:..."> ...
			// <xxx class="set:..."> ...
			Node namedItem = attributes.getNamedItem("class");
			if (namedItem != null) {
				String type = namedItem.getNodeValue();
				if (type != null) {
					if (type.startsWith("array")) return NodeType.array;
					if (type.startsWith("list")) return NodeType.list;
					if (type.startsWith("set")) return NodeType.set;
					if (type.startsWith("object")) return NodeType.object;
					if (type.charAt(0) == '@' || type.startsWith("annotation")) return NodeType.annotation;
					if (type.startsWith("map")) {
						Element[] children = getChildElements(o, true);
						if (children.length == 0) return NodeType.mapGeneric;
						if ("entry".equals(children[0].getNodeName())) return NodeType.mapEntries;
						return NodeType.mapKnown;
					}
				}
			}
		}
		String typeName = o.getNodeName();
		if ("annotation".equals(typeName)) return NodeType.annotation;
		if ("map".equals(typeName)) return NodeType.mapDirect;
		if ("list".equals(typeName)) return NodeType.listDirect;
		if ("set".equals(typeName)) return NodeType.setDirect;
		if ("array".equals(typeName)) return NodeType.arrayDirect;
		Map<String, ConfigCodec<?>> codecs = Config.configurationCodecs;
		if (codecs != null && codecs.containsKey(typeName)) {
			if (containerType != NodeType.mapDirect && containerType != NodeType.mapGeneric
					&& containerType != NodeType.mapKnown && containerType != NodeType.object) {
				return NodeType.codecDirect;
			}
		}
		NodeList childNodes = o.getChildNodes();
		if (childNodes == null) return NodeType.object;
		int length = childNodes.getLength();
		int elementCount = 0;
		int textCount = 0;
		String firstText = null;
		String firstType = null;
		boolean sameElementName = true;
		boolean containsBasicData = false;
		boolean containsKnowObjects = false;
		for (int i = 0; i < length; i++) {
			Node item = childNodes.item(i);
			if (item instanceof Text) {
				Text t = (Text) item;
				String str = t.getNodeValue();
				if (str == null) continue;
				str = str.trim();
				if (str.length() > 0) {
					if (firstText == null) {
						firstText = str;
					}
					if (firstType == null) {
						firstType = "#text";
					}
					textCount++;
				}
			} else if (item instanceof Element) {
				Element el = (Element) item;
				String nodeName = el.getNodeName();
				if (basicData.contains(nodeName)) {
					containsBasicData = true;
				} else if (knownObjects.contains(nodeName)) {
					containsKnowObjects = true;
				}
				if (firstType == null) {
					firstType = nodeName;
				} else if (!firstType.equals(nodeName)) {
					sameElementName = false;
				}
				elementCount++;
			}
		}
		if (elementCount == 0) {
			if (textCount == 0) return NodeType.emptyObject;
			if (basicData.contains(o.getNodeName())) return NodeType.basicDirect;
			if (textCount == 1) {
				if (firstText.indexOf('\n') == -1) return NodeType.plain;
			}
			return NodeType.collection;
 		}
		if (elementCount == 1) {
			if (textCount != 0) return NodeType.collection; // mixed collection type
			if ("null".equals(firstType)) {
				return NodeType.nullValue;
			}
			if ("empty".equals(firstType)) {
				return NodeType.emptyString;
			}
			if (basicData.contains(firstType)) {
				return NodeType.basicData;
			}
			if (codecs != null && codecs.containsKey(firstType)) {
				return NodeType.codecObject;
			}
			// else other types
			//return objectType;
		}
		if (sameElementName) {
			if ("entry".equals(firstType)) return NodeType.mapEntries;
			if (primitives.contains(firstType)
					|| "#text".equals(firstType)) {
				return NodeType.plainCollection;
			}
			if (knownObjects.contains(firstType) || basicData.contains(firstType)
					|| (codecs != null && codecs.containsKey(firstType))) {
				return NodeType.collection;
			}
			return NodeType.mapKnown;
		}
		if (containsBasicData || containsKnowObjects) return NodeType.collection;
		return NodeType.object;
	}
	
	private Element getFirstElement(Element o) {
		if (o == null) return null;
		NodeList childNodes = o.getChildNodes();
		int length = childNodes.getLength();
		for (int i = 0; i < length; i++) {
			Node item = childNodes.item(i);
			if (item instanceof Element) {
				return (Element) item;
			}
		}
		return null;
	}

	private Element[] getChildElements(Element o, boolean noInlineTexts) {
		NodeList childNodes = o.getChildNodes();
		int length = childNodes.getLength();
		List<Element> entries = new ArrayList<Element>();
		for (int i = 0; i < length; i++) {
			Node item = childNodes.item(i);
			if (item instanceof Element) {
				Element p = (Element) item;
				entries.add(p);
			} else if (noInlineTexts && item instanceof Text) {
				Text t = (Text) item;
				String text = t.getNodeValue();
				if (text != null && text.trim().length() > 0) {
					throw new RuntimeException("Unexpected text \"" + text + "\" inside element <" + o.getNodeName() + ">!");
				}
			}
		}
		return entries.toArray(new Element[entries.size()]);
	}
	
	private String getTextValue(Element o) {
		StringBuilder builder = new StringBuilder();
		NodeList childNodes = o.getChildNodes();
		int length = childNodes == null ? 0 : childNodes.getLength();
		if (childNodes != null && length > 0) {
			for (int i = 0; i < length; i++) {
				Node item = childNodes.item(i);
				if (item instanceof Text) {
					Text t = (Text) item;
					builder.append(t.getNodeValue().trim());
				}
			}
		}
		return builder.toString();
	}
	
	
	protected boolean checkCompactness(String str, boolean forKey) {
		int length = str.length();
		if (forKey) {
			for (int i = 0; i < length; i++) {
				char c = str.charAt(i);
				if (c == ' ' || c == '\t' || c == '\n' || c == '\r'
						|| c == '=' || c == ';' || c == '>' || c == '#')
					return false;
			}
		} else {
			for (int i = 0; i < length; i++) {
				char c = str.charAt(i);
				if (c == ';' || c == '>' || c == '\n' || c == '\r'
						|| c == '#')
					return false;
			}
		}
		return true;
	}
	public void visit(StringBuilder builder, String prefix, Element o, NodeType containerType) {
		/*
		if ("includes".equals(o.getTagName()) && o.getChildNodes().getLength() == 0) {
			System.out.println("..xx");
		}
		//*/
		NodeType type = prefix == null ? NodeType.none : parseType(o, containerType);
		if (type == NodeType.plain) {
			assign(builder, prefix, null).append(StringUtils.formatAsProperties(getTextValue(o).trim())).append("\r\n");
			return;
		}
		if (type == NodeType.nullValue) {
			assign(builder, prefix, null).append(ConfigINIParser.$null).append("\r\n");
			return;
		}
		if (type == NodeType.emptyString) {
			assign(builder, prefix, null).append(ConfigINIParser.$empty).append("\r\n");
			return;
		}
		if (type == NodeType.codecObject) {
			Element el = getFirstElement(o);
			String secretStr = getTextValue(el).trim();
			assign(builder, prefix, null).append('[').append(el.getNodeName()).append(':').append(secretStr).append("]\r\n");
			return;
		}
		if (type == NodeType.codecDirect) {
			String secretStr = getTextValue(o).trim();
			assign(builder, prefix, null).append('[').append(o.getNodeName()).append(':').append(secretStr).append("]\r\n");
			return;
		}
		if (type == NodeType.emptyObject) {
			if ("empty".equals(o.getTagName())) return;
			appendType(builder, prefix, o, "empty");
			return;
		}
		if (type == NodeType.basicDirect) {
			String typeName = o.getNodeName();
			String content = getTextValue(o);
			assign(builder, prefix, null);
			if ("String".equalsIgnoreCase(typeName)) {
				builder.append(StringUtils.formatAsProperties(content)).append("\r\n");;
			} else {
				builder.append('[').append(typeName).append(':').append(content).append("]\r\n");
			}
			return;
		}
		if (type == NodeType.basicData) {
			Element first = getFirstElement(o);
			String typeName = first.getNodeName();
			assign(builder, prefix, null);
			String content = getTextValue(first);
			if ("String".equalsIgnoreCase(typeName)) {
				builder.append(StringUtils.formatAsProperties(content)).append("\r\n");;
			} else {
				builder.append('[').append(typeName).append(':').append(content).append("]\r\n");
			}
			return;
		}
		int startingIndex = 0; //GeneratorConfig.startingIndex;
		if (type == NodeType.mapEntries) {
			appendType(builder, prefix, o, null);
			Element[] mapEntries = getChildElements(o, true);
			int nodeLength = mapEntries.length + startingIndex;
			int maxZeros = ("" + nodeLength).length();
			int idx = startingIndex;
			for (Element entry : mapEntries) {
				String index = String.valueOf(idx);
				int leadingZeros = maxZeros - index.length();
				for (int j = 0; j < leadingZeros; j++) {
					index = "0" + index;
				}
				visit(builder, prefix + "." + index, entry, type);
				idx++;
			}
			return;
		}
		if (type == NodeType.mapKnown) {
			appendType(builder, prefix, o, null);
			Element[] children = getChildElements(o, true);
			Element[] mapKVs = children;
			for (Element entry : mapKVs) {
				visit(builder, prefix + "." + entry.getNodeName(), entry, type);
			}
			return;
		}
		if (type == NodeType.mapDirect) {
			appendType(builder, prefix, o, "map");
			Element[] children = getChildElements(o, true);
			Element[] mapKVs = children;
			for (Element entry : mapKVs) {
				visit(builder, prefix + "." + entry.getNodeName(), entry, type);
			}
			return;
		}
		if (type == NodeType.mapGeneric) {
			appendType(builder, prefix, o, null);
			return;
		}
		
		if (type == NodeType.plainCollection) {
			NodeList childNodes = o.getChildNodes();
			int length = childNodes.getLength();
			boolean first = true;
			boolean ok2Compact = true;
			StringBuilder compactBuilder = new StringBuilder();
			compactBuilder.append(prefix).append('=');
			for (int i = 0; i < length; i++) {
				Node item = childNodes.item(i);
				if (item instanceof Text) {
					Text t = (Text) item;
					String str = t.getNodeValue();
					if (str == null) continue;
					str = str.trim();
					if (str.length() > 0) {
						if (!checkCompactness(str, false)) {
							ok2Compact = false;
							break;
						}
						if (!first) compactBuilder.append(";");
						first = false;
						compactBuilder.append(str);
					}
				} else if (item instanceof Element) {
					String nodeName = ((Element) item).getNodeName();
					String str;
					if ("null".equals(nodeName)) {
						str = "[null]";
					} else if ("empty".equals(nodeName)) {
						str = "[empty]";
					} else {
						str = getTextValue((Element) item);
						if (!checkCompactness(str, false)) {
							ok2Compact = false;
							break;
						}
					}
					if (!first) compactBuilder.append(";");
					first = false;
					compactBuilder.append(str);
				}
				if (compactBuilder.length() > 80) break; 
			}
			if (compactBuilder.length() <= 80 && ok2Compact) { 
				builder.append(compactBuilder);
				builder.append("\r\n");
				return;
			}
			type = NodeType.collection;
		}
		if (type == NodeType.collection
				|| type == NodeType.array || type == NodeType.list || type == NodeType.set
				|| type == NodeType.arrayDirect || type == NodeType.listDirect || type == NodeType.setDirect
				) {
			Element[] children = getChildElements(o, false);
			if (children.length == 0) {
				String valueText = getTextValue(o);
				if (valueText != null && valueText.length() > 0) {
					builder.append(prefix).append('=').append(valueText).append("\r\n");
					return;
				}
			} else {
				String valueText = getTextValue(o);
				if (valueText != null && valueText.length() > 0) {
					throw new RuntimeException("Unexpected text inside <" + o.getNodeName() + ">!");
				}				
			}
			String defaultType = null;
			if (type == NodeType.arrayDirect) defaultType = "array";
			else if (type == NodeType.listDirect) defaultType = "list";
			else if (type == NodeType.setDirect) defaultType = "set";
			appendType(builder, prefix, o, defaultType);
			int nodeLength = children.length + startingIndex;
			int maxZeros = ("" + nodeLength).length();
			int idx = startingIndex;
			for (Element element : children) {
				String index = "" + idx;
				int leadingZeros = maxZeros - index.length();
				for (int j = 0; j < leadingZeros; j++) {
					index = "0" + index;
				}
				String newPrefix = prefix + "." + index;
				visit(builder, newPrefix, element, type);
				idx++;
			}
			return;
		}
		/*
		if (isPlainObject(o)) {
			StringBuilder objBuilder = new StringBuilder();
			NamedNodeMap attributes = o.getAttributes();
			int attrLength = attributes == null ? 0 : attributes.getLength();
			for (int i = 0; i < attrLength; i++) {
				Node item = attributes.item(i);
				if ("class".equals(item.getNodeName())) continue;
				objBuilder.append(item.getNodeName()).append(">").append(item.getNodeValue()).append(";");
			}
			NodeList childNodes = o.getChildNodes();
			int length = childNodes == null ? 0 : childNodes.getLength();
			for (int i = 0; i < length; i++) {
				Node item = childNodes.item(i);
				if ("null".equals(item.getNodeName())) {
					objBuilder.append("[null];");
					continue;
				}
				objBuilder.append(item.getNodeName()).append(">").append(item.getNodeValue()).append(";");
			}
			int objLength = objBuilder.length();
			builder.append(prefix).append("=");
			if (objLength == 0) {
				builder.append(ConfigINIParser.$empty);
			} else {
				builder.append(objBuilder, 0, objLength - 1);
			}
			builder.append("\r\n");
			return;
		}
		//*/
		NodeList childNodes = o.getChildNodes();
		int length = childNodes.getLength();
		boolean generated = false;
		for (int i = 0; i < length; i++) {
			Node item = childNodes.item(i);
			short nodeType = item.getNodeType();
			if (Node.COMMENT_NODE == nodeType) {
				Comment c =  (Comment) item;
				String comments = c.getNodeValue();
				String[] lines = comments.split("(\\r\\n|\\n|\\r)");
				for (int j = 0; j < lines.length; j++) {
					String commentLine = lines[j].trim();
					if (j != 0 && j != lines.length - 1 || commentLine.length() != 0) {
						if (xmlToINIAddComments) builder.append("# ").append(commentLine).append("\r\n");
					}
				}
			}
			if (!(item instanceof Element)) {
				if (item instanceof Text) {
					Text t = (Text) item;
					String text = t.getNodeValue();
					if (text != null) {
						text = text.trim();
						if (text.length() > 0) {
							throw new RuntimeException("Unexpected text \"" + text + "\" inside element <" + o.getNodeName() + ">!");
						}
					}
				}
				continue;
			}
			Element el = (Element) item;
			if (prefix == null) {
				visit(builder, el.getNodeName(), el, type);
				if (xmlToINIAddComments) builder.append("\r\n");
			} else {
				if (!generated) {
					appendType(builder, prefix, o, null);
					generated = true;
				}
				String newPrefix = prefix + "." + el.getNodeName();
				visit(builder, newPrefix, el, type);
			}
		}
		if (!generated) {
			appendType(builder, prefix, o, null);
			return;
		}
	}

	public StringBuilder assign(StringBuilder builder, String prefix, Element o) {
		if (o == null) return builder.append(prefix).append('=');
		String nodeName = o.getNodeName();
		if ("item".equals(nodeName)) {
			return builder.append(prefix).append('=');
		} else {
			return builder.append(prefix).append('.').append(nodeName).append('=');
		}
	}

	private void appendType(StringBuilder builder, String prefix, Element o, String defaultType) {
		builder.append(prefix).append("=[");
		String type = null;
		NamedNodeMap attributes = o.getAttributes();
		if (attributes != null && attributes.getLength() > 0) {
			Node namedItem = attributes.getNamedItem("class");
			if (namedItem != null) {
				type = namedItem.getNodeValue();
				if (type != null && type.length() > 0) {
					if ("object".equals(o.getNodeName()) && !type.startsWith("object:")) {
						builder.append("object:");
					} else if ("annotation".equals(o.getNodeName()) && !type.startsWith("annotation:")) {
						builder.append("annotation:");
					}
					builder.append(type);
				}
			}
		}
		if (type == null && defaultType != null) builder.append(defaultType);
		builder.append("]\r\n");
	}

	public InputStream convertToProperties(InputStream fis) throws Exception {
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		dbf.setNamespaceAware(true);
		dbf.setAttribute("http://xml.org/sax/features/namespaces", Boolean.TRUE);
		StringBuilder builder = new StringBuilder();
		visit(builder, null, dbf.newDocumentBuilder().parse(fis).getDocumentElement(), NodeType.none);
		if (xmlToINIDebugOutput) System.out.println(builder.toString());
		return new ByteArrayInputStream(builder.toString().getBytes(Config.configFileEncoding));
	}

	@Override
	public Set<String> unusedConfigurationItems() {
		return iniParser.unusedConfigurationItems();
	}

}
