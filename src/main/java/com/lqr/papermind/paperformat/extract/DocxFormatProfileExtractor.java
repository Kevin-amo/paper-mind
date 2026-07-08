package com.lqr.papermind.paperformat.extract;

import com.lqr.papermind.paperformat.model.DocumentFormatProfile;
import com.lqr.papermind.paperformat.model.HeaderFooterRule;
import com.lqr.papermind.paperformat.model.PageRule;
import com.lqr.papermind.paperformat.model.ParagraphFormatSnapshot;
import com.lqr.papermind.paperformat.model.ParagraphStyleRule;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.InputStream;
import java.util.Map;

@Component
public class DocxFormatProfileExtractor {

    public DocumentFormatProfile extract(InputStream input) {
        try {
            Map<String, String> parts = DocxPackageReader.readXmlParts(input);
            DocumentFormatProfile profile = new DocumentFormatProfile();
            profile.setPageRule(new DocxFormatSpecExtractor().extract(new java.io.ByteArrayInputStream(toDocxLike(parts))).getPageRule());
            profile.setHeaderFooterRule(readHeaderFooter(parts));
            readParagraphs(parts, profile);
            return profile;
        } catch (Exception ex) {
            throw new IllegalArgumentException("DOCX 格式画像解析失败", ex);
        }
    }

    private byte[] toDocxLike(Map<String, String> parts) throws java.io.IOException {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(output)) {
            for (Map.Entry<String, String> entry : parts.entrySet()) {
                zip.putNextEntry(new java.util.zip.ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private HeaderFooterRule readHeaderFooter(Map<String, String> parts) {
        return new DocxFormatSpecExtractor().extract(new java.io.ByteArrayInputStream(uncheckedZip(parts))).getHeaderFooterRule();
    }

    private byte[] uncheckedZip(Map<String, String> parts) {
        try {
            return toDocxLike(parts);
        } catch (java.io.IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private void readParagraphs(Map<String, String> parts, DocumentFormatProfile profile) {
        String documentXml = parts.get("word/document.xml");
        if (documentXml == null) {
            return;
        }
        DocxStyleSupport.Styles styles = DocxStyleSupport.parse(parts.get("word/styles.xml"));
        Document document = DocxPackageReader.parse(documentXml);
        Element body = DocxPackageReader.first(document.getDocumentElement(), "w:body");
        int index = 0;
        for (Element paragraph : DocxPackageReader.childElements(body, "w:p")) {
            String text = DocxPackageReader.text(paragraph);
            if (text.isBlank()) {
                continue;
            }
            String styleId = paragraphStyleId(paragraph);
            ParagraphStyleRule effective = DocxStyleSupport.merge(
                    styles.docDefaults(),
                    styles.style("Normal"),
                    styles.style(styleId),
                    DocxStyleSupport.readRule(paragraph)
            );
            ParagraphFormatSnapshot snapshot = new ParagraphFormatSnapshot();
            snapshot.setIndex(index++);
            snapshot.setText(text);
            snapshot.setStyleId(styleId);
            snapshot.setLevel(headingLevel(styleId));
            snapshot.setAsciiFont(effective.getAsciiFont());
            snapshot.setEastAsiaFont(effective.getEastAsiaFont());
            snapshot.setFontSizePt(effective.getFontSizePt());
            snapshot.setLineSpacingMultiple(effective.getLineSpacingMultiple());
            snapshot.setFirstLineIndentMm(effective.getFirstLineIndentMm());
            snapshot.setAlignment(effective.getAlignment());
            snapshot.setBold(effective.getBold());
            if (snapshot.getLevel() == null) {
                profile.getParagraphs().add(snapshot);
            } else {
                profile.getHeadings().add(snapshot);
            }
        }
    }

    private String paragraphStyleId(Element paragraph) {
        Element pPr = DocxPackageReader.first(paragraph, "w:pPr");
        Element pStyle = DocxPackageReader.first(pPr, "w:pStyle");
        return DocxPackageReader.attr(pStyle, "val");
    }

    private Integer headingLevel(String styleId) {
        if (styleId == null) {
            return null;
        }
        return switch (styleId) {
            case "Heading1" -> 1;
            case "Heading2" -> 2;
            case "Heading3" -> 3;
            default -> null;
        };
    }
}
