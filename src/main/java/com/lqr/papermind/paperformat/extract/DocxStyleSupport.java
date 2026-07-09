package com.lqr.papermind.paperformat.extract;

import com.lqr.papermind.paperformat.model.ParagraphStyleRule;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * DOCX样式支持工具类，解析styles.xml并合并段落样式规则链
 */
final class DocxStyleSupport {

    /** 私有构造器，防止实例化 */
    private DocxStyleSupport() {
    }

    /**
     * 解析styles.xml，构建样式继承树
     *
     * @param stylesXml styles.xml内容
     * @return 解析后的样式对象
     */
    static Styles parse(String stylesXml) {
        Styles styles = new Styles();
        if (stylesXml == null || stylesXml.isBlank()) {
            return styles;
        }
        Document document = DocxPackageReader.parse(stylesXml);
        Element defaults = DocxPackageReader.first(document.getDocumentElement(), "w:docDefaults");
        styles.docDefaults = readRule(defaults);

        NodeList styleNodes = document.getElementsByTagName("w:style");
        for (int i = 0; i < styleNodes.getLength(); i++) {
            Element style = (Element) styleNodes.item(i);
            String id = DocxPackageReader.attr(style, "styleId");
            if (id != null) {
                styles.elements.put(id, style);
                Element name = DocxPackageReader.first(style, "w:name");
                String styleName = DocxPackageReader.attr(name, "val");
                if (styleName != null) {
                    styles.names.put(id, styleName);
                }
                Element basedOn = DocxPackageReader.first(style, "w:basedOn");
                String parentId = DocxPackageReader.attr(basedOn, "val");
                if (parentId != null) {
                    styles.basedOn.put(id, parentId);
                }
            }
        }
        for (String id : styles.elements.keySet()) {
            styles.byId.put(id, styles.resolve(id, new HashSet<>()));
        }
        return styles;
    }

    /** 合并多个段落样式规则，后者覆盖前者（非null属性优先） */
    static ParagraphStyleRule merge(ParagraphStyleRule... rules) {
        ParagraphStyleRule result = new ParagraphStyleRule();
        for (ParagraphStyleRule rule : rules) {
            if (rule == null) {
                continue;
            }
            if (rule.getAsciiFont() != null) {
                result.setAsciiFont(rule.getAsciiFont());
            }
            if (rule.getEastAsiaFont() != null) {
                result.setEastAsiaFont(rule.getEastAsiaFont());
            }
            if (rule.getFontSizePt() != null) {
                result.setFontSizePt(rule.getFontSizePt());
            }
            if (rule.getLineSpacingMultiple() != null) {
                result.setLineSpacingMultiple(rule.getLineSpacingMultiple());
            }
            if (rule.getLineSpacingRule() != null) {
                result.setLineSpacingRule(rule.getLineSpacingRule());
            }
            if (rule.getLineSpacingPt() != null) {
                result.setLineSpacingPt(rule.getLineSpacingPt());
            }
            if (rule.getSpaceBeforePt() != null) {
                result.setSpaceBeforePt(rule.getSpaceBeforePt());
            }
            if (rule.getSpaceAfterPt() != null) {
                result.setSpaceAfterPt(rule.getSpaceAfterPt());
            }
            if (rule.getFirstLineIndentMm() != null) {
                result.setFirstLineIndentMm(rule.getFirstLineIndentMm());
            }
            if (rule.getAlignment() != null) {
                result.setAlignment(rule.getAlignment());
            }
            if (rule.getBold() != null) {
                result.setBold(rule.getBold());
            }
            if (rule.getLatinFont() != null) {
                result.setLatinFont(rule.getLatinFont());
            }
        }
        return result;
    }

    /** 从XML元素中读取段落样式规则（字体、字号、行距、间距、缩进、对齐等） */
    static ParagraphStyleRule readRule(Element scope) {
        ParagraphStyleRule rule = new ParagraphStyleRule();
        if (scope == null) {
            return rule;
        }
        Element rPr = DocxPackageReader.first(scope, "w:rPr");
        Element pPr = DocxPackageReader.first(scope, "w:pPr");
        Element fonts = DocxPackageReader.first(rPr, "w:rFonts");
        if (fonts != null) {
            rule.setAsciiFont(DocxPackageReader.attr(fonts, "ascii"));
            rule.setEastAsiaFont(DocxPackageReader.attr(fonts, "eastAsia"));
            rule.setLatinFont(rule.getAsciiFont());
        }
        Element sz = DocxPackageReader.first(rPr, "w:sz");
        rule.setFontSizePt(DocxPackageReader.halfPointsToPt(DocxPackageReader.attr(sz, "val")));
        rule.setBold(DocxPackageReader.first(rPr, "w:b") == null ? null : Boolean.TRUE);
        Element spacing = DocxPackageReader.first(pPr, "w:spacing");
        String line = DocxPackageReader.attr(spacing, "line");
        String lineRule = DocxPackageReader.attr(spacing, "lineRule");
        if (line != null && ("auto".equalsIgnoreCase(lineRule) || lineRule == null)) {
            rule.setLineSpacingMultiple(Integer.parseInt(line) / 240.0);
        } else if (line != null && "exact".equalsIgnoreCase(lineRule)) {
            rule.setLineSpacingRule("FIXED");
            rule.setLineSpacingPt(DocxPackageReader.twipsToPt(line));
        } else if (line != null && "atLeast".equalsIgnoreCase(lineRule)) {
            rule.setLineSpacingRule("AT_LEAST");
            rule.setLineSpacingPt(DocxPackageReader.twipsToPt(line));
        }
        rule.setSpaceBeforePt(DocxPackageReader.twipsToPt(DocxPackageReader.attr(spacing, "before")));
        rule.setSpaceAfterPt(DocxPackageReader.twipsToPt(DocxPackageReader.attr(spacing, "after")));
        Element ind = DocxPackageReader.first(pPr, "w:ind");
        rule.setFirstLineIndentMm(DocxPackageReader.twipsToMm(DocxPackageReader.attr(ind, "firstLine")));
        rule.setAlignment(DocxPackageReader.paragraphAlignment(scope));
        return rule;
    }

    /** DOCX样式容器，维护样式继承关系和已解析的样式规则 */
    static class Styles {
        private ParagraphStyleRule docDefaults = new ParagraphStyleRule();
        private final Map<String, ParagraphStyleRule> byId = new LinkedHashMap<>();
        private final Map<String, Element> elements = new LinkedHashMap<>();
        private final Map<String, String> basedOn = new LinkedHashMap<>();
        private final Map<String, String> names = new LinkedHashMap<>();

        /** 获取文档默认样式 */
        ParagraphStyleRule docDefaults() {
            return docDefaults;
        }

        /** 根据样式ID获取已解析的样式规则 */
        ParagraphStyleRule style(String styleId) {
            return byId.get(styleId);
        }

        /** 按 Word 样式显示名称关键字查找样式 ID。 */
        String styleIdByNameContaining(String token) {
            if (token == null || token.isBlank()) {
                return null;
            }
            for (Map.Entry<String, String> entry : names.entrySet()) {
                if (entry.getValue() != null && entry.getValue().contains(token)) {
                    return entry.getKey();
                }
            }
            return null;
        }

        /** 获取 Word 样式显示名称。 */
        String styleName(String styleId) {
            return names.get(styleId);
        }

        /** 递归解析样式继承链，合并父样式和当前样式 */
        private ParagraphStyleRule resolve(String styleId, Set<String> visiting) {
            if (styleId == null || !visiting.add(styleId)) {
                return null;
            }
            ParagraphStyleRule parent = resolve(basedOn.get(styleId), visiting);
            ParagraphStyleRule current = readRule(elements.get(styleId));
            visiting.remove(styleId);
            return merge(parent, current);
        }
    }
}
