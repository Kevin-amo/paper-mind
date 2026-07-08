package com.lqr.papermind.paperformat.extract;

import com.lqr.papermind.paperformat.model.FormatRuleSource;
import com.lqr.papermind.paperformat.model.HeadingStyleRule;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 论文格式规范文本规则解析器，从正文段落文本中提取格式规则
 */
final class FixedRequirementRuleParser {

    /** 匹配厘米数值（上/下/内侧/外侧/装订线/页眉/页脚 + 数值 + cm） */
    private static final Pattern CM_VALUE = Pattern.compile("(上|下|内侧|外侧|装订线|页眉|页脚)\\s*([0-9]+(?:\\.[0-9]+)?)\\s*cm", Pattern.CASE_INSENSITIVE);
    /** 匹配页眉设置描述（字号、字体、文字内容） */
    private static final Pattern HEADER_TEXT = Pattern.compile("页眉设置为[:：].*?以\\s*([小一二三四五六七八九十0-9]+号?)字?([\\p{IsHan}A-Za-z ]{1,20}?)键入[\"“”]([^\"“”]+)[\"“”]");
    /** 匹配固定值行距 */
    private static final Pattern FIXED_LINE_SPACING = Pattern.compile("固定值[，,、\\s]*([0-9]+(?:\\.[0-9]+)?)\\s*磅");
    /** 匹配段前段后间距 */
    private static final Pattern SPACE_BEFORE_AFTER = Pattern.compile("段前[、,，]?段后均为\\s*([0-9]+(?:\\.[0-9]+)?)\\s*磅");
    /** 匹配参考文献类型标记 */
    private static final Pattern REFERENCE_TYPE = Pattern.compile("\\[([MCDRJS PNA])]");

    /**
     * 解析正文段落文本，提取格式规则
     *
     * @param paragraphs 正文段落文本列表
     * @return 规则提取结果
     */
    RequirementRuleExtractionResult parse(List<String> paragraphs) {
        RequirementRuleExtractionResult result = new RequirementRuleExtractionResult();
        if (paragraphs == null || paragraphs.isEmpty()) {
            return result;
        }
        String text = String.join("\n", paragraphs);
        parsePage(text, result);
        parseHeaderFooter(text, result);
        parseBody(text, result);
        parseHeadings(text, result);
        parseReferences(text, result);
        return result;
    }

    /** 解析页面规则（A4纸张、双面打印、对称页边距、各边距厘米值） */
    private void parsePage(String text, RequirementRuleExtractionResult result) {
        if (text.contains("A4")) {
            result.formatSpec().getPageRule().setPageWidthMm(210.0);
            result.formatSpec().getPageRule().setPageHeightMm(297.0);
            result.formatSpec().getPageRule().setSource(FormatRuleSource.TEXT_INFERRED);
            result.mark("pageRule.pageWidthMm", "A4");
            result.mark("pageRule.pageHeightMm", "A4");
        }
        if (text.contains("双面打印")) {
            result.formatSpec().getPageRule().setDuplexPrint(true);
            result.mark("pageRule.duplexPrint", "双面打印");
        }
        if (text.contains("对称页边距")) {
            result.formatSpec().getPageRule().setMirrorMargins(true);
            result.mark("pageRule.mirrorMargins", "对称页边距");
        }
        Matcher matcher = CM_VALUE.matcher(text);
        while (matcher.find()) {
            String label = matcher.group(1);
            double mm = Double.parseDouble(matcher.group(2)) * 10.0;
            switch (label) {
                case "上" -> {
                    result.formatSpec().getPageRule().setMarginTopMm(mm);
                    result.mark("pageRule.marginTopMm", "上页边距");
                }
                case "下" -> {
                    result.formatSpec().getPageRule().setMarginBottomMm(mm);
                    result.mark("pageRule.marginBottomMm", "下页边距");
                }
                case "内侧" -> {
                    result.formatSpec().getPageRule().setInsideMarginMm(mm);
                    result.mark("pageRule.insideMarginMm", "内侧页边距");
                }
                case "外侧" -> {
                    result.formatSpec().getPageRule().setOutsideMarginMm(mm);
                    result.mark("pageRule.outsideMarginMm", "外侧页边距");
                }
                case "装订线" -> {
                    result.formatSpec().getPageRule().setGutterMm(mm);
                    result.mark("pageRule.gutterMm", "装订线");
                }
                case "页眉" -> {
                    result.formatSpec().getPageRule().setHeaderDistanceMm(mm);
                    result.mark("pageRule.headerDistanceMm", "页眉距离");
                }
                case "页脚" -> {
                    result.formatSpec().getPageRule().setFooterDistanceMm(mm);
                    result.mark("pageRule.footerDistanceMm", "页脚距离");
                }
                default -> {
                }
            }
        }
    }

    /** 解析页眉页脚规则（页眉文字、字体、字号、居中、页码） */
    private void parseHeaderFooter(String text, RequirementRuleExtractionResult result) {
        String headerLine = linesContaining(text, "页眉设置为").stream().findFirst().orElse("");
        if (!headerLine.isBlank() && headerLine.contains("居中")) {
            result.formatSpec().getHeaderFooterRule().setHeaderCentered(true);
            result.mark("headerFooterRule.headerCentered", "页眉居中");
        }
        Matcher headerMatcher = HEADER_TEXT.matcher(headerLine);
        if (headerMatcher.find()) {
            Double fontSize = FontSizeNameMapper.toPt(headerMatcher.group(1));
            String font = headerMatcher.group(2).replace("字", "").trim();
            String headerText = headerMatcher.group(3).trim();
            result.formatSpec().getHeaderFooterRule().setHeaderText(headerText);
            result.formatSpec().getHeaderFooterRule().setHeaderFontEastAsia(font);
            result.formatSpec().getHeaderFooterRule().setHeaderFontSizePt(fontSize);
            result.formatSpec().getHeaderFooterRule().setSource(FormatRuleSource.TEXT_INFERRED);
            result.mark("headerFooterRule.headerText", "页眉文字");
            result.mark("headerFooterRule.headerFontEastAsia", "页眉中文字体");
            result.mark("headerFooterRule.headerFontSizePt", "页眉字号");
        }
        String footerLine = linesContaining(text, "页脚设置为").stream().findFirst().orElse("");
        if (!footerLine.isBlank() && footerLine.contains("插入页码")) {
            result.formatSpec().getHeaderFooterRule().setFooterPageNumber(true);
            result.mark("headerFooterRule.footerPageNumber", "页脚插入页码");
        }
        if (!footerLine.isBlank() && footerLine.contains("居中")) {
            result.formatSpec().getHeaderFooterRule().setFooterCentered(true);
            result.mark("headerFooterRule.footerCentered", "页脚页码居中");
        }
    }

    /** 解析正文段落规则（固定值行距、段前段后间距、Times New Roman字体） */
    private void parseBody(String text, RequirementRuleExtractionResult result) {
        Matcher lineMatcher = FIXED_LINE_SPACING.matcher(text);
        if (lineMatcher.find()) {
            result.formatSpec().getBodyRule().setLineSpacingRule("FIXED");
            result.formatSpec().getBodyRule().setLineSpacingPt(Double.parseDouble(lineMatcher.group(1)));
            result.formatSpec().getBodyRule().setSource(FormatRuleSource.TEXT_INFERRED);
            result.mark("bodyRule.lineSpacingRule", "固定值行距");
            result.mark("bodyRule.lineSpacingPt", "固定值行距");
        }
        Matcher spacingMatcher = SPACE_BEFORE_AFTER.matcher(text);
        if (spacingMatcher.find()) {
            double value = Double.parseDouble(spacingMatcher.group(1));
            result.formatSpec().getBodyRule().setSpaceBeforePt(value);
            result.formatSpec().getBodyRule().setSpaceAfterPt(value);
            result.mark("bodyRule.spaceBeforePt", "段前为0磅");
            result.mark("bodyRule.spaceAfterPt", "段后为0磅");
        }
        if (text.toLowerCase(Locale.ROOT).contains("times new roman")) {
            result.formatSpec().getBodyRule().setLatinFont("Times New Roman");
            result.formatSpec().getBodyRule().setAsciiFont("Times New Roman");
            result.mark("bodyRule.latinFont", "数字字母 Times New Roman");
            result.mark("bodyRule.asciiFont", "数字字母 Times New Roman");
        }
    }

    /** 解析标题规则（一级、二级、三级标题的编号模式） */
    private void parseHeadings(String text, RequirementRuleExtractionResult result) {
        addHeadingPattern(text, result, 1, "1", "一级标题");
        addHeadingPattern(text, result, 2, "1.1", "二级标题");
        addHeadingPattern(text, result, 3, "1.1.1", "三级标题");
    }

    /** 添加标题编号模式规则 */
    private void addHeadingPattern(String text, RequirementRuleExtractionResult result, int level, String pattern, String label) {
        if (text.contains(pattern + "（" + label + "）") || text.contains(pattern + "(" + label + ")")) {
            HeadingStyleRule heading = result.formatSpec().getHeadingRules().computeIfAbsent(level, ignored -> {
                HeadingStyleRule created = new HeadingStyleRule();
                created.setLevel(level);
                return created;
            });
            heading.setNumberingPattern(pattern);
            heading.setSource(FormatRuleSource.TEXT_INFERRED);
            result.mark("headingRules." + level + ".numberingPattern", label + "编号");
        }
    }

    /** 解析参考文献规则（正文引用格式、文献类型标记） */
    private void parseReferences(String text, RequirementRuleExtractionResult result) {
        if (text.contains("[1]") && text.contains("正文")) {
            result.referenceRequirements().add(Map.of("type", "BODY_CITATION", "pattern", "[1]"));
        }
        Matcher matcher = REFERENCE_TYPE.matcher(text);
        List<String> types = new ArrayList<>();
        while (matcher.find()) {
            String type = matcher.group(1).trim();
            if (!types.contains(type)) {
                types.add(type);
            }
        }
        if (!types.isEmpty()) {
            result.referenceRequirements().add(new LinkedHashMap<>(Map.of("type", "REFERENCE_TYPE_MARKERS", "markers", types)));
        }
    }

    /** 查找包含指定关键词的所有行 */
    private List<String> linesContaining(String text, String token) {
        List<String> lines = new ArrayList<>();
        for (String line : text.split("\\R")) {
            if (line.contains(token)) {
                lines.add(line);
            }
        }
        return lines;
    }
}