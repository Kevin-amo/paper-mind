package com.lqr.papermind.paperformat.extract;

import com.lqr.papermind.paperformat.model.FormatSpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 从模板 DOCX 中提取文本和结构证据。 */
@Component
@RequiredArgsConstructor
public class DocxTemplateEvidenceExtractor {

    private final DocxRequirementTextExtractor textExtractor;

    public DocxTemplateEvidence extract(byte[] docxBytes, FormatSpec ooxmlSpec) {
        try {
            Map<String, String> parts = DocxPackageReader.readXmlParts(new ByteArrayInputStream(docxBytes));
            List<String> bodyParagraphs = textExtractor.extract(docxBytes);
            List<String> headerTexts = new ArrayList<>();
            List<String> footerTexts = new ArrayList<>();
            List<String> textBoxTexts = new ArrayList<>();
            List<String> commentTexts = new ArrayList<>();

            parts.forEach((name, xml) -> {
                if (name.matches("word/header\\d+\\.xml")) {
                    headerTexts.addAll(allText(xml));
                    textBoxTexts.addAll(textBoxText(xml));
                } else if (name.matches("word/footer\\d+\\.xml")) {
                    footerTexts.addAll(allText(xml));
                    textBoxTexts.addAll(textBoxText(xml));
                } else if ("word/document.xml".equals(name)) {
                    textBoxTexts.addAll(textBoxText(xml));
                } else if ("word/comments.xml".equals(name)) {
                    commentTexts.addAll(allText(xml));
                }
            });

            Map<String, Object> hints = new LinkedHashMap<>();
            hints.put("bodyParagraphCount", bodyParagraphs.size());
            hints.put("textBoxTextCount", textBoxTexts.size());
            hints.put("commentTextCount", commentTexts.size());
            hints.put("headerTextCount", headerTexts.size());
            hints.put("footerTextCount", footerTexts.size());

            return new DocxTemplateEvidence(bodyParagraphs, textBoxTexts, commentTexts, headerTexts, footerTexts, ooxmlSpec, hints);
        } catch (Exception ex) {
            throw new IllegalArgumentException("DOCX 模板证据解析失败", ex);
        }
    }

    private List<String> allText(String xml) {
        Document document = DocxPackageReader.parse(xml);
        String text = DocxPackageReader.text(document.getDocumentElement());
        return text.isBlank() ? List.of() : List.of(text);
    }

    private List<String> textBoxText(String xml) {
        Document document = DocxPackageReader.parse(xml);
        List<String> values = new ArrayList<>();
        collectTextBoxText(document.getDocumentElement(), values);
        return values;
    }

    private void collectTextBoxText(Node node, List<String> values) {
        if (node instanceof Element element && isTextBoxElement(element)) {
            String text = DocxPackageReader.text(element);
            if (!text.isBlank()) {
                values.add(text);
            }
        }
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            collectTextBoxText(children.item(i), values);
        }
    }

    private boolean isTextBoxElement(Element element) {
        String name = element.getTagName();
        return "w:txbxContent".equals(name) || "wps:txbx".equals(name) || "v:textbox".equals(name);
    }
}
