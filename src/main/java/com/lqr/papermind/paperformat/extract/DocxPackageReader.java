package com.lqr.papermind.paperformat.extract;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * DOCX包读取器，从docx文件（ZIP格式）中读取和解析XML部件
 */
final class DocxPackageReader {

    /** 私有构造器，防止实例化 */
    private DocxPackageReader() {
    }

    /**
     * 从docx ZIP流中读取所有XML部件
     *
     * @param input docx文件输入流
     * @return XML部件名称与内容的映射
     * @throws java.io.IOException IO异常
     */
    static Map<String, String> readXmlParts(InputStream input) throws java.io.IOException {
        Map<String, String> parts = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory() && entry.getName().endsWith(".xml")) {
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    zip.transferTo(buffer);
                    parts.put(entry.getName(), buffer.toString(StandardCharsets.UTF_8));
                }
            }
        }
        return parts;
    }

    /** 将XML字符串解析为DOM Document对象 */
    static Document parse(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid OOXML part", ex);
        }
    }

    /** 获取元素的属性值（优先w:前缀，无则取无前缀属性） */
    static String attr(Element element, String localName) {
        if (element == null) {
            return null;
        }
        String prefixed = element.getAttribute("w:" + localName);
        if (!prefixed.isBlank()) {
            return prefixed;
        }
        String plain = element.getAttribute(localName);
        return plain.isBlank() ? null : plain;
    }

    /** 获取父元素下第一个指定标签名的子元素 */
    static Element first(Element parent, String tagName) {
        if (parent == null) {
            return null;
        }
        NodeList nodes = parent.getElementsByTagName(tagName);
        return nodes.getLength() == 0 ? null : (Element) nodes.item(0);
    }

    /** 获取元素下所有w:t文本节点的拼接内容 */
    static String text(Element parent) {
        if (parent == null) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        NodeList texts = parent.getElementsByTagName("w:t");
        for (int i = 0; i < texts.getLength(); i++) {
            result.append(texts.item(i).getTextContent());
        }
        return result.toString().trim();
    }

    /** 判断元素中是否包含指定的域代码（w:instrText） */
    static boolean hasField(Element parent, String fieldName) {
        if (parent == null) {
            return false;
        }
        NodeList instructions = parent.getElementsByTagName("w:instrText");
        for (int i = 0; i < instructions.getLength(); i++) {
            String value = instructions.item(i).getTextContent();
            if (value != null && value.matches("(?is).*\\b" + fieldName + "\\b.*")) {
                return true;
            }
        }
        return false;
    }

    /** 获取段落的对齐方式（CENTER/RIGHT/BOTH等） */
    static String paragraphAlignment(Element paragraph) {
        Element pPr = first(paragraph, "w:pPr");
        Element jc = first(pPr, "w:jc");
        String value = attr(jc, "val");
        if (value == null) {
            return null;
        }
        return switch (value.toLowerCase()) {
            case "center" -> "CENTER";
            case "right" -> "RIGHT";
            case "both", "distribute" -> "BOTH";
            default -> value.toUpperCase();
        };
    }

    /** 将twips（二十分之一磅）转换为毫米 */
    static Double twipsToMm(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Integer.parseInt(value) / 1440.0 * 25.4;
    }

    /** 将twips转换为磅 */
    static Double twipsToPt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Integer.parseInt(value) / 20.0;
    }

    /** 将半磅值转换为磅 */
    static Double halfPointsToPt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Integer.parseInt(value) / 2.0;
    }

    /** 将字符串转换为UTF-8字节数组 */
    static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    /** 将字节数组解析为DOM Document对象 */
    static Document parseBytes(byte[] bytes) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            return factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid XML", ex);
        }
    }

    /** 获取父元素下所有指定标签名的直接子元素 */
    static Iterable<Element> childElements(Element parent, String tagName) {
        java.util.List<Element> elements = new java.util.ArrayList<>();
        if (parent == null) {
            return elements;
        }
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element element && tagName.equals(element.getTagName())) {
                elements.add(element);
            }
        }
        return elements;
    }
}
