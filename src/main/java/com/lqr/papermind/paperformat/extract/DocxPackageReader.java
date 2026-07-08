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

final class DocxPackageReader {

    private DocxPackageReader() {
    }

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

    static Element first(Element parent, String tagName) {
        if (parent == null) {
            return null;
        }
        NodeList nodes = parent.getElementsByTagName(tagName);
        return nodes.getLength() == 0 ? null : (Element) nodes.item(0);
    }

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

    static Double twipsToMm(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Integer.parseInt(value) / 1440.0 * 25.4;
    }

    static Double halfPointsToPt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Integer.parseInt(value) / 2.0;
    }

    static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

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
