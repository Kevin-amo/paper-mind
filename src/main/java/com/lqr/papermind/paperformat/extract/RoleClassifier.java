package com.lqr.papermind.paperformat.extract;

import java.util.Locale;

/** Classifies DOCX paragraphs into thesis structure roles. */
public final class RoleClassifier {

    public String classify(String text, String styleId, String partName, int paragraphIndex) {
        return classify(text, styleId, null, partName, paragraphIndex);
    }

    public String classify(String text, String styleId, String styleName, String partName, int paragraphIndex) {
        String normalized = text == null ? "" : text.strip();
        String lower = normalized.toLowerCase(Locale.ROOT);
        String normalizedStyleName = styleName == null ? "" : styleName.strip();
        String lowerStyleName = normalizedStyleName.toLowerCase(Locale.ROOT);
        if (partName != null && partName.matches("word/header\\d+\\.xml")) {
            return "header";
        }
        if (partName != null && partName.matches("word/footer\\d+\\.xml")) {
            return "footerPageNumber";
        }
        if ("TOC10".equals(styleId) || "tocTitle".equals(styleId)
                || normalizedStyleName.contains("目录标题") || lowerStyleName.contains("toc title")) {
            return "tocTitle";
        }
        if ("aff1".equals(styleId)) {
            return "paperTitle";
        }
        if ("TOC1".equals(styleId)) {
            return "tocEntry1";
        }
        if ("TOC2".equals(styleId)) {
            return "tocEntry2";
        }
        if ("TOC3".equals(styleId)) {
            return "tocEntry3";
        }
        if ("aff3".equals(styleId) || "Heading1".equals(styleId) || normalized.matches("^\\d+\\s+.*")) {
            return "heading1";
        }
        if ("aff4".equals(styleId) || "Heading2".equals(styleId) || normalized.matches("^\\d+\\.\\d+\\s+.*")) {
            return "heading2";
        }
        if ("aff5".equals(styleId) || "Heading3".equals(styleId) || normalized.matches("^\\d+\\.\\d+\\.\\d+\\s+.*")) {
            return "heading3";
        }
        if (normalized.equals("目录") || lower.equals("contents") || lower.equals("table of contents")) {
            return "tocTitle";
        }
        if (normalized.equals("摘要") || normalized.equals("中文摘要")) {
            return "cnAbstractTitle";
        }
        if (lower.equals("abstract")) {
            return "enAbstractTitle";
        }
        if (lower.startsWith("keywords") || normalized.contains("Keywords")) {
            return "enKeywordsLabel";
        }
        if (normalized.startsWith("关键词")) {
            return "cnKeywordsLabel";
        }
        if (normalized.matches("^\\[\\d+].*")) {
            return "references";
        }
        if (normalized.matches("^图\\d+[-－].*|^图\\d+\\s+.*")) {
            return "figureCaption";
        }
        if (normalized.matches("^表\\d+[-－].*|^表\\d+\\s+.*")) {
            return "tableCaption";
        }
        if ("aff2".equals(styleId) || normalizedStyleName.contains("论文正文")) {
            if (normalized.startsWith("指导老师") || normalized.startsWith("指导教师")) {
                return "advisorLine";
            }
            if (normalized.contains("学院") && normalized.contains("专业") && normalized.contains("学生")) {
                return "authorLine";
            }
            return "body";
        }
        if (paragraphIndex == 0 && normalized.length() > 8 && !normalized.contains("：") && !normalized.contains(":")) {
            return "paperTitle";
        }
        return "body";
    }

    public String refineWithContext(String currentRole, String previousRole) {
        if ("body".equals(currentRole) && ("cnAbstractTitle".equals(previousRole) || "cnAbstractContent".equals(previousRole))) {
            return "cnAbstractContent";
        }
        if ("body".equals(currentRole) && ("enAbstractTitle".equals(previousRole) || "enAbstractContent".equals(previousRole))) {
            return "enAbstractContent";
        }
        return currentRole;
    }
}
