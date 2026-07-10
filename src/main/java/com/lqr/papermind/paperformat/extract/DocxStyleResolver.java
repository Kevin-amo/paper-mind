package com.lqr.papermind.paperformat.extract;

import com.lqr.papermind.paperformat.model.ParagraphStyleRule;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** 从 Word OOXML 默认样式、命名样式和直接格式化中解析段落/文本的有效样式。 */
public class DocxStyleResolver {

    private final ParagraphStyleRule docDefaults;
    private final Map<String, Element> stylesById = new LinkedHashMap<>();
    private final Map<String, String> styleNames = new LinkedHashMap<>();
    private final Map<String, String> basedOn = new LinkedHashMap<>();
    private final Map<String, ParagraphStyleRule> resolvedStyles = new LinkedHashMap<>();

    private DocxStyleResolver(String stylesXml) {
        if (stylesXml == null || stylesXml.isBlank()) {
            this.docDefaults = new ParagraphStyleRule();
            return;
        }
        Document document = DocxPackageReader.parse(stylesXml);
        Element defaults = DocxPackageReader.first(document.getDocumentElement(), "w:docDefaults");
        this.docDefaults = readRule(defaults);
        NodeList nodes = document.getElementsByTagName("w:style");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element style = (Element) nodes.item(i);
            String id = DocxPackageReader.attr(style, "styleId");
            if (id == null) {
                continue;
            }
            stylesById.put(id, style);
            Element name = DocxPackageReader.first(style, "w:name");
            String styleName = DocxPackageReader.attr(name, "val");
            if (styleName != null) {
                styleNames.put(id, styleName);
            }
            Element parent = DocxPackageReader.first(style, "w:basedOn");
            String parentId = DocxPackageReader.attr(parent, "val");
            if (parentId != null) {
                basedOn.put(id, parentId);
            }
        }
        for (String id : stylesById.keySet()) {
            resolvedStyles.put(id, resolveStyle(id, new HashSet<>()));
        }
    }

    public static DocxStyleResolver from(DocxPackageReader.PackageParts parts) {
        return new DocxStyleResolver(parts.xml("word/styles.xml").orElse(null));
    }

    public ParagraphStyleRule resolveParagraph(Element paragraph, String partName) {
        String styleId = paragraphStyleId(paragraph);
        ParagraphStyleRule effective = merge(docDefaults, style(styleId), readRule(paragraph), readRuns(paragraph));
        effective.setStyleId(styleId);
        effective.setStyleName(styleName(styleId));
        effective.setSourcePart(partName);
        if (effective.getLatinFont() == null) {
            effective.setLatinFont(effective.getAsciiFont() != null ? effective.getAsciiFont() : effective.getHAnsiFont());
        }
        if (effective.getAsciiFont() == null && effective.getHAnsiFont() != null) {
            effective.setAsciiFont(effective.getHAnsiFont());
        }
        return effective;
    }

    public ParagraphStyleRule docDefaults() {
        return cloneRule(docDefaults);
    }

    public ParagraphStyleRule style(String styleId) {
        return cloneRule(resolvedStyles.get(styleId));
    }

    public String styleName(String styleId) {
        return styleNames.get(styleId);
    }

    public static String paragraphStyleId(Element paragraph) {
        Element pPr = directChild(paragraph, "w:pPr");
        Element pStyle = directChild(pPr, "w:pStyle");
        return DocxPackageReader.attr(pStyle, "val");
    }

    static ParagraphStyleRule merge(ParagraphStyleRule... rules) {
        ParagraphStyleRule result = new ParagraphStyleRule();
        for (ParagraphStyleRule rule : rules) {
            if (rule == null) {
                continue;
            }
            if (rule.getAsciiFont() != null) result.setAsciiFont(rule.getAsciiFont());
            if (rule.getHAnsiFont() != null) result.setHAnsiFont(rule.getHAnsiFont());
            if (rule.getEastAsiaFont() != null) result.setEastAsiaFont(rule.getEastAsiaFont());
            if (rule.getFontSizePt() != null) result.setFontSizePt(rule.getFontSizePt());
            if (rule.getLineSpacingMultiple() != null) {
                result.setLineSpacingMultiple(rule.getLineSpacingMultiple());
                result.setLineSpacingRule(null);
                result.setLineSpacingPt(null);
            }
            if (rule.getLineSpacingRule() != null || rule.getLineSpacingPt() != null) {
                result.setLineSpacingMultiple(null);
            }
            if (rule.getLineSpacingRule() != null) result.setLineSpacingRule(rule.getLineSpacingRule());
            if (rule.getLineSpacingPt() != null) result.setLineSpacingPt(rule.getLineSpacingPt());
            if (rule.getSpaceBeforePt() != null) result.setSpaceBeforePt(rule.getSpaceBeforePt());
            if (rule.getSpaceAfterPt() != null) result.setSpaceAfterPt(rule.getSpaceAfterPt());
            if (rule.getFirstLineIndentMm() != null) result.setFirstLineIndentMm(rule.getFirstLineIndentMm());
            if (rule.getAlignment() != null) result.setAlignment(rule.getAlignment());
            if (rule.getBold() != null) result.setBold(rule.getBold());
            if (rule.getLatinFont() != null) result.setLatinFont(rule.getLatinFont());
            if (rule.getStyleId() != null) result.setStyleId(rule.getStyleId());
            if (rule.getStyleName() != null) result.setStyleName(rule.getStyleName());
            if (rule.getSourcePart() != null) result.setSourcePart(rule.getSourcePart());
            if (rule.getSourcePriority() != null) result.setSourcePriority(rule.getSourcePriority());
            if (rule.getEvidenceText() != null) result.setEvidenceText(rule.getEvidenceText());
        }
        return result;
    }

    static ParagraphStyleRule readRule(Element scope) {
        ParagraphStyleRule rule = new ParagraphStyleRule();
        if (scope == null) {
            return rule;
        }
        Element pPr = DocxPackageReader.first(scope, "w:pPr");
        Element rPr = DocxPackageReader.first(scope, "w:rPr");
        applyRunProperties(rule, rPr);
        applyParagraphProperties(rule, pPr == null ? scope : pPr);
        return rule;
    }

    private ParagraphStyleRule resolveStyle(String styleId, Set<String> visiting) {
        if (styleId == null || !visiting.add(styleId)) {
            return null;
        }
        ParagraphStyleRule parent = resolveStyle(basedOn.get(styleId), visiting);
        ParagraphStyleRule current = readRule(stylesById.get(styleId));
        current.setStyleId(styleId);
        current.setStyleName(styleNames.get(styleId));
        visiting.remove(styleId);
        return merge(parent, current);
    }

    private ParagraphStyleRule readRuns(Element paragraph) {
        ParagraphStyleRule result = new ParagraphStyleRule();
        if (paragraph == null) {
            return result;
        }
        NodeList children = paragraph.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element run && "w:r".equals(run.getTagName())) {
                ParagraphStyleRule runRule = new ParagraphStyleRule();
                applyRunProperties(runRule, directChild(run, "w:rPr"));
                result = merge(result, runRule);
            }
        }
        return result;
    }

    private static void applyRunProperties(ParagraphStyleRule rule, Element rPr) {
        Element fonts = DocxPackageReader.first(rPr, "w:rFonts");
        if (fonts != null) {
            rule.setAsciiFont(concreteFont(DocxPackageReader.attr(fonts, "ascii")));
            rule.setHAnsiFont(concreteFont(DocxPackageReader.attr(fonts, "hAnsi")));
            rule.setEastAsiaFont(concreteFont(DocxPackageReader.attr(fonts, "eastAsia")));
            rule.setLatinFont(firstNonBlank(rule.getAsciiFont(), rule.getHAnsiFont()));
        }
        Element sz = DocxPackageReader.first(rPr, "w:sz");
        rule.setFontSizePt(DocxPackageReader.halfPointsToPt(DocxPackageReader.attr(sz, "val")));
        if (DocxPackageReader.first(rPr, "w:b") != null) {
            rule.setBold(true);
        }
    }

    private static void applyParagraphProperties(ParagraphStyleRule rule, Element pPr) {
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
        Element jc = directChild(pPr, "w:jc");
        String alignmentValue = DocxPackageReader.attr(jc, "val");
        String alignment = switch (alignmentValue == null ? "" : alignmentValue.toLowerCase()) {
            case "center" -> "CENTER";
            case "right" -> "RIGHT";
            case "both", "distribute" -> "BOTH";
            case "" -> null;
            default -> alignmentValue.toUpperCase();
        };
        if (alignment != null) {
            rule.setAlignment(alignment);
        }
    }

    private static Element directChild(Element parent, String tagName) {
        if (parent == null) {
            return null;
        }
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element element && tagName.equals(element.getTagName())) {
                return element;
            }
        }
        return null;
    }

    private static String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    private static String concreteFont(String value) {
        if (value == null || value.isBlank() || value.toLowerCase(java.util.Locale.ROOT).contains("theme")) {
            return null;
        }
        return value;
    }

    private static ParagraphStyleRule cloneRule(ParagraphStyleRule source) {
        if (source == null) {
            return null;
        }
        ParagraphStyleRule copy = new ParagraphStyleRule();
        copy.setAsciiFont(source.getAsciiFont());
        copy.setHAnsiFont(source.getHAnsiFont());
        copy.setEastAsiaFont(source.getEastAsiaFont());
        copy.setFontSizePt(source.getFontSizePt());
        copy.setLineSpacingMultiple(source.getLineSpacingMultiple());
        copy.setLineSpacingRule(source.getLineSpacingRule());
        copy.setLineSpacingPt(source.getLineSpacingPt());
        copy.setSpaceBeforePt(source.getSpaceBeforePt());
        copy.setSpaceAfterPt(source.getSpaceAfterPt());
        copy.setFirstLineIndentMm(source.getFirstLineIndentMm());
        copy.setAlignment(source.getAlignment());
        copy.setBold(source.getBold());
        copy.setLatinFont(source.getLatinFont());
        copy.setStyleId(source.getStyleId());
        copy.setStyleName(source.getStyleName());
        copy.setSourcePart(source.getSourcePart());
        copy.setSourcePriority(source.getSourcePriority());
        copy.setEvidenceText(source.getEvidenceText());
        copy.setSource(source.getSource());
        return copy;
    }
}
