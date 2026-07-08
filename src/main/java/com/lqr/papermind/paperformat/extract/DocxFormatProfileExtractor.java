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

/**
 * DOCX格式画像提取器，从docx文件中提取文档的实际格式信息
 */
@Component
public class DocxFormatProfileExtractor {

    /**
     * 从docx输入流中提取文档格式画像（页面、页眉页脚、段落样式）
     *
     * @param input docx文件输入流
     * @return 文档格式画像
     * @throws IllegalArgumentException 解析失败时抛出
     */
    public DocumentFormatProfile extract(InputStream input) {
        try {
            Map<String, String> parts = DocxPackageReader.readXmlParts(input);
            DocumentFormatProfile profile = new DocumentFormatProfile();
            profile.setPageRule(new DocxFormatSpecExtractor().extractOoxmlOnly(new java.io.ByteArrayInputStream(toDocxLike(parts))).getPageRule());
            profile.setHeaderFooterRule(readHeaderFooter(parts));
            readParagraphs(parts, profile);
            return profile;
        } catch (Exception ex) {
            throw new IllegalArgumentException("DOCX 格式画像解析失败", ex);
        }
    }

    /** 将XML部分重新打包为docx字节数组 */
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

    /** 读取页眉页脚规则 */
    private HeaderFooterRule readHeaderFooter(Map<String, String> parts) {
        return new DocxFormatSpecExtractor().extractOoxmlOnly(new java.io.ByteArrayInputStream(uncheckedZip(parts))).getHeaderFooterRule();
    }

    /** 将XML部分打包为zip字节数组（包装IOException为IllegalStateException） */
    private byte[] uncheckedZip(Map<String, String> parts) {
        try {
            return toDocxLike(parts);
        } catch (java.io.IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** 读取文档中所有段落的格式快照，分别归类为正文和标题 */
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

    /** 获取段落的样式ID */
    private String paragraphStyleId(Element paragraph) {
        Element pPr = DocxPackageReader.first(paragraph, "w:pPr");
        Element pStyle = DocxPackageReader.first(pPr, "w:pStyle");
        return DocxPackageReader.attr(pStyle, "val");
    }

    /** 根据样式ID解析标题级别（Heading1/2/3 -> 1/2/3） */
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
