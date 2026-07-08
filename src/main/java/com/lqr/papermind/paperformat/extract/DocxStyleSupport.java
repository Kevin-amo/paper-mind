package com.lqr.papermind.paperformat.extract;

import com.lqr.papermind.paperformat.model.ParagraphStyleRule;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.LinkedHashMap;
import java.util.Map;

final class DocxStyleSupport {

    private DocxStyleSupport() {
    }

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
                styles.byId.put(id, readRule(style));
            }
        }
        return styles;
    }

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
            if (rule.getFirstLineIndentMm() != null) {
                result.setFirstLineIndentMm(rule.getFirstLineIndentMm());
            }
            if (rule.getAlignment() != null) {
                result.setAlignment(rule.getAlignment());
            }
            if (rule.getBold() != null) {
                result.setBold(rule.getBold());
            }
        }
        return result;
    }

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
        }
        Element sz = DocxPackageReader.first(rPr, "w:sz");
        rule.setFontSizePt(DocxPackageReader.halfPointsToPt(DocxPackageReader.attr(sz, "val")));
        rule.setBold(DocxPackageReader.first(rPr, "w:b") == null ? null : Boolean.TRUE);
        Element spacing = DocxPackageReader.first(pPr, "w:spacing");
        String line = DocxPackageReader.attr(spacing, "line");
        String lineRule = DocxPackageReader.attr(spacing, "lineRule");
        if (line != null && ("auto".equalsIgnoreCase(lineRule) || lineRule == null)) {
            rule.setLineSpacingMultiple(Integer.parseInt(line) / 240.0);
        }
        Element ind = DocxPackageReader.first(pPr, "w:ind");
        rule.setFirstLineIndentMm(DocxPackageReader.twipsToMm(DocxPackageReader.attr(ind, "firstLine")));
        rule.setAlignment(DocxPackageReader.paragraphAlignment(scope));
        return rule;
    }

    static class Styles {
        private ParagraphStyleRule docDefaults = new ParagraphStyleRule();
        private final Map<String, ParagraphStyleRule> byId = new LinkedHashMap<>();

        ParagraphStyleRule docDefaults() {
            return docDefaults;
        }

        ParagraphStyleRule style(String styleId) {
            return byId.get(styleId);
        }
    }
}
