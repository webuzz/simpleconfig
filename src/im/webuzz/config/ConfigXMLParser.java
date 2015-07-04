/*******************************************************************************
 * Copyright (c) 2010 - 2015 java2script.org, webuzz.im and others
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

package im.webuzz.config;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class ConfigXMLParser {

	private static boolean isPlainObject(Element o) {
		if (o == null) return true;
		NodeList childNodes = o.getChildNodes();
		int length = childNodes.getLength();
		for (int i = 0; i < length; i++) {
			Node item = childNodes.item(i);
			if (item instanceof Element) {
				Element p = (Element) item;
				if (p.hasChildNodes()) {
					return false;
				}
			}
		}
		return true;
	}
	
//	private static boolean isSingleNode(Element o) {
//		NamedNodeMap attributes = o.getAttributes();
//		if (attributes != null && attributes.getLength() > 0) {
//			return false;
//		}
//		NodeList childNodes = o.getChildNodes();
//		if (childNodes != null && childNodes.getLength() > 1) {
//			return false;
//		}
//		return true;
//	}
	
	private static int getNodeLength(Element o) {
		NodeList childNodes = o.getChildNodes();
		int length = childNodes == null ? 0 : childNodes.getLength();
		int size = 0;
		for (int i = 0; i < length; i++) {
			Node item = childNodes.item(i);
			if (item instanceof Element) {
				size++;
			}
		}
		return size;
	}
	
	private static boolean isArrayListSetNode(Element o) {
		// <x><string>abc</string><x>
		// <x><object>abc</object><x>
		NamedNodeMap attributes = o.getAttributes();
		if (attributes != null && attributes.getLength() > 0) {
			return false;
		}
		NodeList childNodes = o.getChildNodes();
		int length = childNodes == null ? 0 : childNodes.getLength();
		if (childNodes != null && length > 0) {
			String name = null;
			for (int i = 0; i < length; i++) {
				Node item = childNodes.item(i);
				if (item instanceof Element) {
					Element p = (Element) item;
					if (name == null) {
						name = p.getNodeName();
						if (!primitives.contains(name) && !"object".equals(name)) {
							return false;
						}
					} else {
						if (!name.equals(p.getNodeName())) {
							return false;
						}
					}
				}
			}
		}
		return true;
	}
	
//	private static boolean isMapObjectNode(Element o) {
//		// <x><xy></xy><xz></xz></x>
//		NamedNodeMap attributes = o.getAttributes();
//		if (attributes != null && attributes.getLength() > 0) {
//			return false;
//		}
//		NodeList childNodes = o.getChildNodes();
//		int length = childNodes == null ? 0 : childNodes.getLength();
//		if (childNodes != null && length > 0) {
//			for (int i = 0; i < length; i++) {
//				Node item = childNodes.item(i);
//				if (item instanceof Element) {
//					Element p = (Element) item;
//					if (!primitives.contains(p.getNodeName())) {
//						return false;
//					}
//				}
//			}
//		}
//		return true;
//	}

	private static final Set<String> primitives = new HashSet<String>();
	
	static {
		primitives.add("string");
		primitives.add("integer");
		primitives.add("long");
		primitives.add("boolean");
		primitives.add("short");
		primitives.add("byte");
		primitives.add("char");
		primitives.add("double");
		primitives.add("float");
		primitives.add("i");
	}
	
	public static void visit(StringBuilder builder, String prefix, Element o) {
		if (o == null) {
			builder.append(prefix).append("=[null]\r\n");
			return;
		}
		String elName = o.getNodeName();
		if (primitives.contains(elName)) {
			String elValue = o.getNodeValue();
			builder.append(prefix).append("=").append(elValue == "" ? "[empty]" : elValue).append("\r\n");
			return;
		}
		if (isArrayListSetNode(o)) {
			NodeList childNodes = o.getChildNodes();
			int length = childNodes == null ? 0 : childNodes.getLength();
			if (length == 0) {
				builder.append(prefix).append("=[empty]\r\n");
			} else if (length == 1) {
				builder.append(prefix).append("=").append(childNodes.item(0).getNodeValue()).append("\r\n");
			} else {
				builder.append(prefix).append("=[]\r\n");
				int maxZeros = ("" + getNodeLength(o)).length();
				int idx = 0;
				for (int i = 0; i < length; i++) {
					Node item = childNodes.item(i);
					if (item instanceof Element) {
						String index = "" + (++idx);
						int leadingZeros = maxZeros - index.length();
						for (int j = 0; j < leadingZeros; j++) {
							index = "0" + index;
						}
						visit(builder, prefix + "." + index, (Element) item);
					}
				}
			}
			return;
		}
		if (isPlainObject(o)) {
			StringBuilder objBuilder = new StringBuilder();
			NamedNodeMap attributes = o.getAttributes();
			int attrLength = attributes == null ? 0 : attributes.getLength();
			for (int i = 0; i < attrLength; i++) {
				Node item = attributes.item(i);
				objBuilder.append(item.getNodeName()).append(">").append(item.getNodeValue()).append(";");
			}
			NodeList childNodes = o.getChildNodes();
			int length = childNodes == null ? 0 : childNodes.getLength();
			for (int i = 0; i < length; i++) {
				Node item = childNodes.item(i);
				objBuilder.append(item.getNodeName()).append(">").append(item.getNodeValue()).append(";");
			}
			int objLength = objBuilder.length();
			builder.append(prefix).append("=");
			if (objLength == 0) {
				builder.append("[empty]");
			} else {
				builder.append(objBuilder, 0, objLength - 1);
			}
			builder.append("\r\n");
			return;
		}
		boolean generated = false;
		NamedNodeMap attributes = o.getAttributes();
		int attrLength = attributes == null ? 0 : attributes.getLength();
		for (int i = 0; i < attrLength; i++) {
			Node item = attributes.item(i);
			if (!generated) {
				builder.append(prefix).append("=[]\r\n");
			}
			builder.append(prefix).append(".").append(item.getNodeName()).append("=").append(item.getNodeValue()).append("\r\n");				
			generated = true;
		}
		NodeList childNodes = o.getChildNodes();
		int length = childNodes == null ? 0 : childNodes.getLength();
		for (int i = 0; i < length; i++) {
			Node item = childNodes.item(i);
			if (item instanceof Element) {
				Element p = (Element) item;
				if (prefix == null) {
					visit(builder, p.getNodeName(), p);
				} else {
					if (!generated) {
						builder.append(prefix).append("=[]\r\n");
					}
					visit(builder, prefix + "." + p.getNodeName(), p);
				}
				generated = true;
			}
		}
		if (!generated) {
			builder.append(prefix).append("=[empty]\r\n");
		}
	}

	public static InputStream convertXML2Properties(InputStream fis) throws IOException {
		byte[] buffer = new byte[8096];
		int read = -1;
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		while ((read = fis.read(buffer)) != -1) {
			baos.write(buffer, 0, read);
		}
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        dbf.setAttribute("http://xml.org/sax/features/namespaces", Boolean.TRUE);
        Document xml = null;
		try {
            DocumentBuilder db = dbf.newDocumentBuilder();
            ByteArrayInputStream biStream = new ByteArrayInputStream(baos.toByteArray());
            xml = db.parse(biStream);
        } catch (Exception e) {
            e.printStackTrace();
            return new ByteArrayInputStream(baos.toByteArray());
        }
		Element configEl = xml.getDocumentElement();
		StringBuilder builder = new StringBuilder();
		visit(builder, null, configEl);
		//System.out.println(builder.toString());
		//System.out.println("==============");
		return new ByteArrayInputStream(builder.toString().getBytes(Config.configFileEncoding));
	}
	
}
