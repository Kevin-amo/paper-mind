package com.lqr.papermind.paperformat.extract;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 从所有相关的 DOCX XML 部件中提取段落级格式证据。 */
public class TemplateEvidenceExtractor {

    private final DocxStyleResolver styleResolver;
    private final RoleClassifier roleClassifier = new RoleClassifier();

    public TemplateEvidenceExtractor(DocxStyleResolver styleResolver) {
        this.styleResolver = styleResolver;
    }

    public List<TemplateEvidence> extract(DocxPackageReader.PackageParts parts) {
        List<String> partNames = parts.partNames().stream()
                .filter(this::isEvidencePart)
                .sorted(Comparator.comparing(this::partOrder).thenComparing(name -> name))
                .toList();
        List<TemplateEvidence> evidence = new ArrayList<>();
        for (String partName : partNames) {
            Document document = parts.parsePart(partName).orElse(null);
            if (document == null) {
                continue;
            }
            collectParagraphs(document.getDocumentElement(), partName, evidence);
        }
        return evidence;
    }

    private boolean isEvidencePart(String name) {
        return "word/document.xml".equals(name)
                || name.matches("word/header\\d+\\.xml")
                || name.matches("word/footer\\d+\\.xml")
                || "word/comments.xml".equals(name)
                || "word/footnotes.xml".equals(name)
                || "word/endnotes.xml".equals(name);
    }

    private int partOrder(String partName) {
        if ("word/document.xml".equals(partName)) {
            return 0;
        }
        if (partName.startsWith("word/header")) {
            return 1;
        }
        if (partName.startsWith("word/footer")) {
            return 2;
        }
        return 3;
    }

    private void collectParagraphs(Element root, String partName, List<TemplateEvidence> evidence) {
        NodeList paragraphs = root.getElementsByTagName("w:p");
        String previousRole = null;
        for (int i = 0; i < paragraphs.getLength(); i++) {
            Element paragraph = (Element) paragraphs.item(i);
            String text = DocxPackageReader.text(paragraph);
            if (text.isBlank() || looksLikeEmbeddedRawXml(text)) {
                continue;
            }
            var style = styleResolver.resolveParagraph(paragraph, partName);
            String role = roleClassifier.classify(text, style.getStyleId(), style.getStyleName(), partName, i);
            role = roleClassifier.refineWithContext(role, previousRole);
            style.setEvidenceText(text);
            evidence.add(new TemplateEvidence(
                    text,
                    style.getStyleId(),
                    style.getStyleName(),
                    style,
                    partName,
                    i,
                    hasAncestor(paragraph, "w:tbl"),
                    hasAncestor(paragraph, "w:txbxContent") || hasAncestor(paragraph, "v:textbox") || hasAncestor(paragraph, "wps:txbx"),
                    role
            ));
            previousRole = role;
        }
    }

    private boolean looksLikeEmbeddedRawXml(String text) {
        return text.startsWith("<w:p ") || text.startsWith("<w:p>");
    }

    private boolean hasAncestor(Node node, String tagName) {
        Node current = node.getParentNode();
        while (current != null) {
            if (current instanceof Element element && tagName.equals(element.getTagName())) {
                return true;
            }
            current = current.getParentNode();
        }
        return false;
    }
}
