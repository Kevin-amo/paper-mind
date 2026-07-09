package com.lqr.papermind.paperformat.extract;

import org.docx4j.XmlUtils;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.wml.ContentAccessor;
import org.docx4j.wml.P;
import org.docx4j.wml.Text;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * DOCX正文规范文本提取器，从docx文件中提取所有段落文本
 */
@Component
public class DocxRequirementTextExtractor {

    /**
     * 从docx字节数组中提取所有非空段落文本
     *
     * @param docxBytes docx文件字节数组
     * @return 段落文本列表
     * @throws IllegalArgumentException 解析失败时抛出
     */
    public List<String> extract(byte[] docxBytes) {
        try {
            WordprocessingMLPackage wordPackage = WordprocessingMLPackage.load(new ByteArrayInputStream(docxBytes));
            List<String> paragraphs = new ArrayList<>();
            for (Object item : wordPackage.getMainDocumentPart().getContent()) {
                Object unwrapped = XmlUtils.unwrap(item);
                if (unwrapped instanceof P paragraph) {
                    String text = paragraphText(paragraph).trim();
                    if (!text.isBlank()) {
                        paragraphs.add(text);
                    }
                }
            }
            return paragraphs;
        } catch (Exception ex) {
            throw new IllegalArgumentException("DOCX 正文规范文本解析失败", ex);
        }
    }

    /** 获取段落的完整文本内容 */
    private String paragraphText(P paragraph) {
        StringBuilder result = new StringBuilder();
        collectText(paragraph, result);
        return result.toString();
    }

    /** 递归收集XML节点中的所有文本内容 */
    private void collectText(Object item, StringBuilder result) {
        Object value = XmlUtils.unwrap(item);
        if (value instanceof Text text) {
            result.append(text.getValue());
            return;
        }
        if (value instanceof ContentAccessor accessor) {
            for (Object child : accessor.getContent()) {
                collectText(child, result);
            }
        }
    }
}