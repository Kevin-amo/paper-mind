package com.lqr.papermind.paperformat.extract;

import com.lqr.papermind.paperformat.model.FormatSpec;
import com.lqr.papermind.paperformat.model.HeadingStyleRule;
import com.lqr.papermind.paperformat.model.HeaderFooterRule;
import com.lqr.papermind.paperformat.model.PageRule;
import com.lqr.papermind.paperformat.model.ParagraphStyleRule;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * DOCX格式规则提取器，从docx文件中解析页面、页眉页脚、段落等格式规则
 */
@Component
public class DocxFormatSpecExtractor {

    /** 数值比较容差 */
    private static final double NUMERIC_TOLERANCE = 0.25;

    private final DocxRequirementTextExtractor textExtractor;
    private final FixedRequirementRuleParser ruleParser;

    /** 默认构造器，创建文本提取器和规则解析器 */
    public DocxFormatSpecExtractor() {
        this(new DocxRequirementTextExtractor(), new FixedRequirementRuleParser());
    }

    /** 构造器，注入文本提取器和规则解析器（用于测试） */
    DocxFormatSpecExtractor(DocxRequirementTextExtractor textExtractor, FixedRequirementRuleParser ruleParser) {
        this.textExtractor = textExtractor;
        this.ruleParser = ruleParser;
    }

    /** 从docx输入流中提取格式规则，合并文本规则和OOXML结构规则 */
    public FormatSpec extract(InputStream input) {
        try {
            byte[] bytes = input.readAllBytes();
            FormatSpec ooxmlSpec = extractOoxmlOnly(bytes);
            RequirementRuleExtractionResult textResult = ruleParser.parse(textExtractor.extract(bytes));
            return merge(textResult, ooxmlSpec);
        } catch (Exception ex) {
            throw new IllegalArgumentException("DOCX 格式规则解析失败", ex);
        }
    }

    /** 仅从OOXML结构中提取格式规则（不含文本解析） */
    FormatSpec extractOoxmlOnly(InputStream input) {
        try {
            return extractOoxmlOnly(input.readAllBytes());
        } catch (Exception ex) {
            throw new IllegalArgumentException("DOCX OOXML 格式规则解析失败", ex);
        }
    }

    /** 从字节数组中提取OOXML格式规则 */
    FormatSpec extractOoxmlOnly(byte[] bytes) {
        try {
            Map<String, String> parts = DocxPackageReader.readXmlParts(new ByteArrayInputStream(bytes));
            FormatSpec spec = new FormatSpec();
            DocxStyleSupport.Styles styles = DocxStyleSupport.parse(parts.get("word/styles.xml"));
            spec.setPageRule(readPageRule(parts.get("word/document.xml"), parts.get("word/settings.xml")));
            spec.setHeaderFooterRule(readHeaderFooter(parts, styles));
            spec.setBodyRule(DocxStyleSupport.merge(styles.docDefaults(), styles.style("Normal")));
            for (int level = 1; level <= 3; level++) {
                ParagraphStyleRule style = DocxStyleSupport.merge(styles.docDefaults(), styles.style("Heading" + level));
                HeadingStyleRule heading = new HeadingStyleRule();
                copy(style, heading);
                heading.setLevel(level);
                spec.getHeadingRules().put(level, heading);
            }
            spec.getExtractionReport().put("source", "OOXML");
            return spec;
        } catch (Exception ex) {
            throw new IllegalArgumentException("DOCX OOXML 格式规则解析失败", ex);
        }
    }

    /** 合并文本解析结果和OOXML结构规则，处理冲突和回退 */
    private FormatSpec merge(RequirementRuleExtractionResult textResult, FormatSpec ooxmlSpec) {
        FormatSpec merged = textResult.formatSpec();
        List<String> fallbackFields = new ArrayList<>();
        List<Map<String, Object>> conflicts = new ArrayList<>();

        mergePage(textResult, merged, ooxmlSpec, fallbackFields, conflicts);
        mergeHeaderFooter(textResult, merged, ooxmlSpec, fallbackFields, conflicts);
        mergeParagraph(textResult, "bodyRule", merged.getBodyRule(), ooxmlSpec.getBodyRule(), fallbackFields, conflicts);
        for (int level = 1; level <= 3; level++) {
            int headingLevel = level;
            HeadingStyleRule textHeading = merged.getHeadingRules().computeIfAbsent(headingLevel, ignored -> {
                HeadingStyleRule heading = new HeadingStyleRule();
                heading.setLevel(headingLevel);
                return heading;
            });
            HeadingStyleRule ooxmlHeading = ooxmlSpec.getHeadingRules().get(headingLevel);
            if (ooxmlHeading != null) {
                mergeParagraph(textResult, "headingRules." + headingLevel, textHeading, ooxmlHeading, fallbackFields, conflicts);
            }
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("source", "text_requirement_with_ooxml_fallback");
        report.put("textRules", textResult.textRules());
        report.put("ooxmlFallbackFields", fallbackFields);
        report.put("conflicts", conflicts);
        report.put("referenceRequirements", textResult.referenceRequirements());
        merged.setExtractionReport(report);
        return merged;
    }

    /** 合并页面规则（尺寸、边距等字段） */
    private void mergePage(RequirementRuleExtractionResult textResult,
                           FormatSpec merged,
                           FormatSpec ooxmlSpec,
                           List<String> fallbackFields,
                           List<Map<String, Object>> conflicts) {
        mergeField(textResult, "pageRule.pageWidthMm", merged.getPageRule(), ooxmlSpec.getPageRule(), PageRule::getPageWidthMm, PageRule::setPageWidthMm, fallbackFields, conflicts);
        mergeField(textResult, "pageRule.pageHeightMm", merged.getPageRule(), ooxmlSpec.getPageRule(), PageRule::getPageHeightMm, PageRule::setPageHeightMm, fallbackFields, conflicts);
        mergeField(textResult, "pageRule.marginTopMm", merged.getPageRule(), ooxmlSpec.getPageRule(), PageRule::getMarginTopMm, PageRule::setMarginTopMm, fallbackFields, conflicts);
        mergeField(textResult, "pageRule.marginRightMm", merged.getPageRule(), ooxmlSpec.getPageRule(), PageRule::getMarginRightMm, PageRule::setMarginRightMm, fallbackFields, conflicts);
        mergeField(textResult, "pageRule.marginBottomMm", merged.getPageRule(), ooxmlSpec.getPageRule(), PageRule::getMarginBottomMm, PageRule::setMarginBottomMm, fallbackFields, conflicts);
        mergeField(textResult, "pageRule.marginLeftMm", merged.getPageRule(), ooxmlSpec.getPageRule(), PageRule::getMarginLeftMm, PageRule::setMarginLeftMm, fallbackFields, conflicts);
        mergeField(textResult, "pageRule.mirrorMargins", merged.getPageRule(), ooxmlSpec.getPageRule(), PageRule::getMirrorMargins, PageRule::setMirrorMargins, fallbackFields, conflicts);
        mergeField(textResult, "pageRule.duplexPrint", merged.getPageRule(), ooxmlSpec.getPageRule(), PageRule::getDuplexPrint, PageRule::setDuplexPrint, fallbackFields, conflicts);
        mergeField(textResult, "pageRule.insideMarginMm", merged.getPageRule(), ooxmlSpec.getPageRule(), PageRule::getInsideMarginMm, PageRule::setInsideMarginMm, fallbackFields, conflicts);
        mergeField(textResult, "pageRule.outsideMarginMm", merged.getPageRule(), ooxmlSpec.getPageRule(), PageRule::getOutsideMarginMm, PageRule::setOutsideMarginMm, fallbackFields, conflicts);
        mergeField(textResult, "pageRule.gutterMm", merged.getPageRule(), ooxmlSpec.getPageRule(), PageRule::getGutterMm, PageRule::setGutterMm, fallbackFields, conflicts);
        mergeField(textResult, "pageRule.headerDistanceMm", merged.getPageRule(), ooxmlSpec.getPageRule(), PageRule::getHeaderDistanceMm, PageRule::setHeaderDistanceMm, fallbackFields, conflicts);
        mergeField(textResult, "pageRule.footerDistanceMm", merged.getPageRule(), ooxmlSpec.getPageRule(), PageRule::getFooterDistanceMm, PageRule::setFooterDistanceMm, fallbackFields, conflicts);
    }

    /** 合并页眉页脚规则 */
    private void mergeHeaderFooter(RequirementRuleExtractionResult textResult,
                                   FormatSpec merged,
                                   FormatSpec ooxmlSpec,
                                   List<String> fallbackFields,
                                   List<Map<String, Object>> conflicts) {
        mergeField(textResult, "headerFooterRule.headerText", merged.getHeaderFooterRule(), ooxmlSpec.getHeaderFooterRule(), HeaderFooterRule::getHeaderText, HeaderFooterRule::setHeaderText, fallbackFields, conflicts);
        mergeBoolean(textResult, "headerFooterRule.headerCentered", merged.getHeaderFooterRule(), ooxmlSpec.getHeaderFooterRule(), HeaderFooterRule::isHeaderCentered, HeaderFooterRule::setHeaderCentered, fallbackFields, conflicts);
        mergeField(textResult, "headerFooterRule.headerFontEastAsia", merged.getHeaderFooterRule(), ooxmlSpec.getHeaderFooterRule(), HeaderFooterRule::getHeaderFontEastAsia, HeaderFooterRule::setHeaderFontEastAsia, fallbackFields, conflicts);
        mergeField(textResult, "headerFooterRule.headerFontSizePt", merged.getHeaderFooterRule(), ooxmlSpec.getHeaderFooterRule(), HeaderFooterRule::getHeaderFontSizePt, HeaderFooterRule::setHeaderFontSizePt, fallbackFields, conflicts);
        mergeBoolean(textResult, "headerFooterRule.footerPageNumber", merged.getHeaderFooterRule(), ooxmlSpec.getHeaderFooterRule(), HeaderFooterRule::isFooterPageNumber, HeaderFooterRule::setFooterPageNumber, fallbackFields, conflicts);
        mergeBoolean(textResult, "headerFooterRule.footerCentered", merged.getHeaderFooterRule(), ooxmlSpec.getHeaderFooterRule(), HeaderFooterRule::isFooterCentered, HeaderFooterRule::setFooterCentered, fallbackFields, conflicts);
    }

    /** 合并段落样式规则（字体、字号、行距、缩进、对齐等） */
    private void mergeParagraph(RequirementRuleExtractionResult textResult,
                                String prefix,
                                ParagraphStyleRule merged,
                                ParagraphStyleRule ooxml,
                                List<String> fallbackFields,
                                List<Map<String, Object>> conflicts) {
        mergeField(textResult, prefix + ".asciiFont", merged, ooxml, ParagraphStyleRule::getAsciiFont, ParagraphStyleRule::setAsciiFont, fallbackFields, conflicts);
        mergeField(textResult, prefix + ".eastAsiaFont", merged, ooxml, ParagraphStyleRule::getEastAsiaFont, ParagraphStyleRule::setEastAsiaFont, fallbackFields, conflicts);
        mergeField(textResult, prefix + ".fontSizePt", merged, ooxml, ParagraphStyleRule::getFontSizePt, ParagraphStyleRule::setFontSizePt, fallbackFields, conflicts);
        mergeField(textResult, prefix + ".lineSpacingMultiple", merged, ooxml, ParagraphStyleRule::getLineSpacingMultiple, ParagraphStyleRule::setLineSpacingMultiple, fallbackFields, conflicts);
        mergeField(textResult, prefix + ".lineSpacingRule", merged, ooxml, ParagraphStyleRule::getLineSpacingRule, ParagraphStyleRule::setLineSpacingRule, fallbackFields, conflicts);
        mergeField(textResult, prefix + ".lineSpacingPt", merged, ooxml, ParagraphStyleRule::getLineSpacingPt, ParagraphStyleRule::setLineSpacingPt, fallbackFields, conflicts);
        mergeField(textResult, prefix + ".spaceBeforePt", merged, ooxml, ParagraphStyleRule::getSpaceBeforePt, ParagraphStyleRule::setSpaceBeforePt, fallbackFields, conflicts);
        mergeField(textResult, prefix + ".spaceAfterPt", merged, ooxml, ParagraphStyleRule::getSpaceAfterPt, ParagraphStyleRule::setSpaceAfterPt, fallbackFields, conflicts);
        mergeField(textResult, prefix + ".firstLineIndentMm", merged, ooxml, ParagraphStyleRule::getFirstLineIndentMm, ParagraphStyleRule::setFirstLineIndentMm, fallbackFields, conflicts);
        mergeField(textResult, prefix + ".alignment", merged, ooxml, ParagraphStyleRule::getAlignment, ParagraphStyleRule::setAlignment, fallbackFields, conflicts);
        mergeField(textResult, prefix + ".bold", merged, ooxml, ParagraphStyleRule::getBold, ParagraphStyleRule::setBold, fallbackFields, conflicts);
        mergeField(textResult, prefix + ".latinFont", merged, ooxml, ParagraphStyleRule::getLatinFont, ParagraphStyleRule::setLatinFont, fallbackFields, conflicts);
    }

    /** 合并单个字段：文本规则优先，无则回退到OOXML值，记录冲突 */
    private <T, V> void mergeField(RequirementRuleExtractionResult textResult,
                                   String field,
                                   T target,
                                   T fallback,
                                   Function<T, V> getter,
                                   BiConsumer<T, V> setter,
                                   List<String> fallbackFields,
                                   List<Map<String, Object>> conflicts) {
        V textValue = getter.apply(target);
        V ooxmlValue = fallback == null ? null : getter.apply(fallback);
        if (textResult.hasField(field)) {
            if (textValue != null && ooxmlValue != null && !equivalent(textValue, ooxmlValue)) {
                conflicts.add(conflict(field, textValue, ooxmlValue));
            }
            return;
        }
        if (ooxmlValue != null) {
            setter.accept(target, ooxmlValue);
            fallbackFields.add(field);
        }
    }

    /** 合并布尔类型字段：文本规则优先，无则回退到OOXML值 */
    private <T> void mergeBoolean(RequirementRuleExtractionResult textResult,
                                  String field,
                                  T target,
                                  T fallback,
                                  Function<T, Boolean> getter,
                                  BiConsumer<T, Boolean> setter,
                                  List<String> fallbackFields,
                                  List<Map<String, Object>> conflicts) {
        boolean textValue = Boolean.TRUE.equals(getter.apply(target));
        boolean ooxmlValue = fallback != null && Boolean.TRUE.equals(getter.apply(fallback));
        if (textResult.hasField(field)) {
            if (textValue != ooxmlValue) {
                conflicts.add(conflict(field, textValue, ooxmlValue));
            }
            return;
        }
        if (ooxmlValue) {
            setter.accept(target, true);
            fallbackFields.add(field);
        }
    }

    /** 判断两个值是否等价（数值类型考虑容差，其他类型使用equals） */
    private boolean equivalent(Object left, Object right) {
        if (left instanceof Number leftNumber && right instanceof Number rightNumber) {
            return Math.abs(leftNumber.doubleValue() - rightNumber.doubleValue()) <= NUMERIC_TOLERANCE;
        }
        return Objects.equals(left, right);
    }

    /** 构建冲突记录，包含字段名、文本解析值和OOXML解析值 */
    private Map<String, Object> conflict(String field, Object textValue, Object ooxmlValue) {
        Map<String, Object> conflict = new LinkedHashMap<>();
        conflict.put("field", field);
        conflict.put("textValue", textValue);
        conflict.put("ooxmlValue", ooxmlValue);
        return conflict;
    }

    /** 从document.xml和settings.xml中读取页面规则（尺寸、边距、装订线等） */
    private PageRule readPageRule(String documentXml, String settingsXml) {
        PageRule page = new PageRule();
        if (documentXml == null) {
            return page;
        }
        Document document = DocxPackageReader.parse(documentXml);
        Element section = DocxPackageReader.first(document.getDocumentElement(), "w:sectPr");
        Element size = DocxPackageReader.first(section, "w:pgSz");
        Element margins = DocxPackageReader.first(section, "w:pgMar");
        page.setPageWidthMm(DocxPackageReader.twipsToMm(DocxPackageReader.attr(size, "w")));
        page.setPageHeightMm(DocxPackageReader.twipsToMm(DocxPackageReader.attr(size, "h")));
        page.setMarginTopMm(DocxPackageReader.twipsToMm(DocxPackageReader.attr(margins, "top")));
        page.setMarginRightMm(DocxPackageReader.twipsToMm(DocxPackageReader.attr(margins, "right")));
        page.setMarginBottomMm(DocxPackageReader.twipsToMm(DocxPackageReader.attr(margins, "bottom")));
        page.setMarginLeftMm(DocxPackageReader.twipsToMm(DocxPackageReader.attr(margins, "left")));
        page.setGutterMm(DocxPackageReader.twipsToMm(DocxPackageReader.attr(margins, "gutter")));
        page.setHeaderDistanceMm(DocxPackageReader.twipsToMm(DocxPackageReader.attr(margins, "header")));
        page.setFooterDistanceMm(DocxPackageReader.twipsToMm(DocxPackageReader.attr(margins, "footer")));
        if (settingsXml != null && settingsXml.contains("mirrorMargins")) {
            page.setMirrorMargins(true);
            page.setInsideMarginMm(page.getMarginLeftMm());
            page.setOutsideMarginMm(page.getMarginRightMm());
        }
        return page;
    }

    /** 从header/footer XML中读取页眉页脚规则 */
    private HeaderFooterRule readHeaderFooter(Map<String, String> parts, DocxStyleSupport.Styles styles) {
        HeaderFooterRule rule = new HeaderFooterRule();
        String headerXml = parts.get("word/header1.xml");
        if (headerXml != null) {
            Document header = DocxPackageReader.parse(headerXml);
            Element paragraph = DocxPackageReader.first(header.getDocumentElement(), "w:p");
            ParagraphStyleRule effective = effectiveParagraphRule(paragraph, styles);
            rule.setHeaderText(DocxPackageReader.text(header.getDocumentElement()));
            rule.setHeaderCentered("CENTER".equals(effective.getAlignment()));
            rule.setHeaderFontEastAsia(effective.getEastAsiaFont());
            rule.setHeaderFontSizePt(effective.getFontSizePt());
        }
        String footerXml = parts.get("word/footer1.xml");
        if (footerXml != null) {
            Document footer = DocxPackageReader.parse(footerXml);
            Element paragraph = DocxPackageReader.first(footer.getDocumentElement(), "w:p");
            ParagraphStyleRule effective = effectiveParagraphRule(paragraph, styles);
            rule.setFooterPageNumber(DocxPackageReader.hasField(footer.getDocumentElement(), "PAGE"));
            rule.setFooterCentered("CENTER".equals(effective.getAlignment()));
        }
        return rule;
    }

    /** 获取段落的有效样式规则（合并样式链） */
    private ParagraphStyleRule effectiveParagraphRule(Element paragraph, DocxStyleSupport.Styles styles) {
        String styleId = paragraphStyleId(paragraph);
        return DocxStyleSupport.merge(styles.style(styleId), DocxStyleSupport.readRule(paragraph));
    }

    /** 获取段落的样式ID */
    private String paragraphStyleId(Element paragraph) {
        Element pPr = DocxPackageReader.first(paragraph, "w:pPr");
        Element pStyle = DocxPackageReader.first(pPr, "w:pStyle");
        return DocxPackageReader.attr(pStyle, "val");
    }

    /** 复制段落样式规则的所有属性 */
    private void copy(ParagraphStyleRule source, ParagraphStyleRule target) {
        target.setAsciiFont(source.getAsciiFont());
        target.setEastAsiaFont(source.getEastAsiaFont());
        target.setFontSizePt(source.getFontSizePt());
        target.setLineSpacingMultiple(source.getLineSpacingMultiple());
        target.setLineSpacingRule(source.getLineSpacingRule());
        target.setLineSpacingPt(source.getLineSpacingPt());
        target.setSpaceBeforePt(source.getSpaceBeforePt());
        target.setSpaceAfterPt(source.getSpaceAfterPt());
        target.setFirstLineIndentMm(source.getFirstLineIndentMm());
        target.setAlignment(source.getAlignment());
        target.setBold(source.getBold());
        target.setLatinFont(source.getLatinFont());
        target.setSource(source.getSource());
    }
}