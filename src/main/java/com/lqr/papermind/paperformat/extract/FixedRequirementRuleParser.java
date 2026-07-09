package com.lqr.papermind.paperformat.extract;

import com.lqr.papermind.paperformat.model.FormatRuleSource;
import com.lqr.papermind.paperformat.model.HeadingStyleRule;
import com.lqr.papermind.paperformat.model.ParagraphStyleRule;

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
    /** 匹配“多倍行距：1.35”等标题行距描述 */
    private static final Pattern MULTIPLE_LINE_SPACING_VALUE = Pattern.compile("多倍行距\\s*[：:]\\s*([0-9]+(?:\\.[0-9]+)?)");
    /** 匹配“1.35倍行距”等标题行距描述 */
    private static final Pattern MULTIPLE_LINE_SPACING_PREFIX = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)\\s*倍行距");
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
        parseSections(text, result);
        parseKeywords(text, result);
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
        if (text.contains("正文文字")) {
            var body = result.formatSpec().getBodyRule();
            linesContaining(text, "正文文字").stream().findFirst().ifPresent(body::setEvidenceText);
            if (text.contains("正文文字：中文宋体") || text.contains("正文文字:中文宋体")) {
                body.setEastAsiaFont("宋体");
                result.mark("bodyRule.eastAsiaFont", "正文中文宋体");
            }
            if (text.contains("正文文字") && text.contains("五号")) {
                body.setFontSizePt(FontSizeNameMapper.toPt("五号"));
                result.mark("bodyRule.fontSizePt", "正文五号");
            }
            if (text.contains("两端对齐")) {
                body.setAlignment("BOTH");
                result.mark("bodyRule.alignment", "正文两端对齐");
            }
        }
    }

    private void parseKeywords(String text, RequirementRuleExtractionResult result) {
        String cnLine = linesContaining(text, "中文关键词").stream().findFirst().orElse("");
        if (!cnLine.isBlank()) {
            ParagraphStyleRule label = result.formatSpec().getRoleRules().computeIfAbsent("cnKeywordsLabel", ignored -> new ParagraphStyleRule());
            label.setBold(true);
            label.setSourcePriority("TEXT_REQUIREMENT");
            label.setEvidenceText(cnLine);
            result.mark("roleRules.cnKeywordsLabel.bold", "中文关键词标签加粗");

            ParagraphStyleRule content = result.formatSpec().getRoleRules().computeIfAbsent("cnKeywordsContent", ignored -> new ParagraphStyleRule());
            content.setEastAsiaFont(cnLine.contains("楷体") ? "楷体" : content.getEastAsiaFont());
            if (cnLine.contains("五号")) {
                content.setFontSizePt(FontSizeNameMapper.toPt("五号"));
            }
            content.setSourcePriority("TEXT_REQUIREMENT");
            content.setEvidenceText(cnLine);
            if (content.getEastAsiaFont() != null) {
                result.mark("roleRules.cnKeywordsContent.eastAsiaFont", "中文关键词楷体");
            }
            if (content.getFontSizePt() != null) {
                result.mark("roleRules.cnKeywordsContent.fontSizePt", "中文关键词五号");
            }
        }

        String enLine = linesContaining(text, "英文关键词").stream().findFirst().orElse("");
        if (!enLine.isBlank() && enLine.contains("Times New Roman")) {
            ParagraphStyleRule label = result.formatSpec().getRoleRules().computeIfAbsent("enKeywordsLabel", ignored -> new ParagraphStyleRule());
            label.setAsciiFont("Times New Roman");
            label.setLatinFont("Times New Roman");
            if (enLine.contains("五号")) {
                label.setFontSizePt(FontSizeNameMapper.toPt("五号"));
            }
            label.setBold(true);
            label.setSourcePriority("TEXT_REQUIREMENT");
            label.setEvidenceText(enLine);
            result.mark("roleRules.enKeywordsLabel.asciiFont", "英文关键词 Times New Roman");
            result.mark("roleRules.enKeywordsLabel.latinFont", "英文关键词 Times New Roman");
            if (label.getFontSizePt() != null) {
                result.mark("roleRules.enKeywordsLabel.fontSizePt", "英文关键词五号");
            }

            ParagraphStyleRule content = result.formatSpec().getRoleRules().computeIfAbsent("enKeywordsContent", ignored -> new ParagraphStyleRule());
            content.setAsciiFont("Times New Roman");
            content.setLatinFont("Times New Roman");
            if (enLine.contains("五号")) {
                content.setFontSizePt(FontSizeNameMapper.toPt("五号"));
            }
            content.setSourcePriority("TEXT_REQUIREMENT");
            content.setEvidenceText(enLine);
            result.mark("roleRules.enKeywordsContent.asciiFont", "英文关键词内容 Times New Roman");
            result.mark("roleRules.enKeywordsContent.latinFont", "英文关键词内容 Times New Roman");
            if (content.getFontSizePt() != null) {
                result.mark("roleRules.enKeywordsContent.fontSizePt", "英文关键词内容五号");
            }
        }
    }

    /** 解析题目、摘要、关键词、目录等前置部分的格式要求。 */
    private void parseSections(String text, RequirementRuleExtractionResult result) {
        if (text.contains("标题：宋体") || text.contains("标题:宋体")) {
            var title = result.formatSpec().getSectionRules().computeIfAbsent("title", ignored -> new com.lqr.papermind.paperformat.model.ParagraphStyleRule());
            title.setEastAsiaFont("宋体");
            title.setFontSizePt(FontSizeNameMapper.toPt("五号"));
            title.setBold(text.contains("标题：宋体（加粗") || text.contains("标题:宋体（加粗"));
            title.setAlignment(text.contains("两端对齐") ? "BOTH" : title.getAlignment());
            title.setLineSpacingRule("FIXED");
            title.setLineSpacingPt(16.0);
            result.mark("sectionRules.title.eastAsiaFont", "标题宋体");
            result.mark("sectionRules.title.fontSizePt", "标题五号");
            result.mark("sectionRules.title.bold", "标题加粗");
            result.mark("sectionRules.title.alignment", "标题两端对齐");
            result.mark("sectionRules.title.lineSpacingRule", "标题固定行距");
            result.mark("sectionRules.title.lineSpacingPt", "标题16磅行距");
        }
        if (text.contains("中文摘要") && text.contains("五号楷体")) {
            var abstractContent = result.formatSpec().getSectionRules().computeIfAbsent("abstractContent", ignored -> new com.lqr.papermind.paperformat.model.ParagraphStyleRule());
            abstractContent.setEastAsiaFont("楷体");
            abstractContent.setFontSizePt(FontSizeNameMapper.toPt("五号"));
            abstractContent.setAlignment("BOTH");
            abstractContent.setLineSpacingRule("FIXED");
            abstractContent.setLineSpacingPt(16.0);
            abstractContent.setSpaceBeforePt(0.0);
            abstractContent.setSpaceAfterPt(0.0);
            result.mark("sectionRules.abstractContent.eastAsiaFont", "中文摘要楷体");
            result.mark("sectionRules.abstractContent.fontSizePt", "中文摘要五号");
            result.mark("sectionRules.abstractContent.alignment", "中文摘要两端对齐");
            result.mark("sectionRules.abstractContent.lineSpacingRule", "中文摘要固定行距");
            result.mark("sectionRules.abstractContent.lineSpacingPt", "中文摘要16磅行距");
        }
        if (text.contains("英文摘要内容") && text.contains("Times New Roman")) {
            var englishAbstract = result.formatSpec().getSectionRules().computeIfAbsent("englishAbstractContent", ignored -> new com.lqr.papermind.paperformat.model.ParagraphStyleRule());
            englishAbstract.setAsciiFont("Times New Roman");
            englishAbstract.setLatinFont("Times New Roman");
            englishAbstract.setFontSizePt(FontSizeNameMapper.toPt("五号"));
            englishAbstract.setAlignment("BOTH");
            englishAbstract.setLineSpacingRule("FIXED");
            englishAbstract.setLineSpacingPt(16.0);
            englishAbstract.setSpaceBeforePt(0.0);
            englishAbstract.setSpaceAfterPt(0.0);
            result.mark("sectionRules.englishAbstractContent.asciiFont", "英文摘要 Times New Roman");
            result.mark("sectionRules.englishAbstractContent.latinFont", "英文摘要 Times New Roman");
            result.mark("sectionRules.englishAbstractContent.fontSizePt", "英文摘要五号");
        }
    }

    /** 解析标题规则（一级、二级、三级标题的编号模式） */
    private void parseHeadings(String text, RequirementRuleExtractionResult result) {
        addHeadingPattern(text, result, 1, "1", "一级标题");
        addHeadingPattern(text, result, 2, "1.1", "二级标题");
        addHeadingPattern(text, result, 3, "1.1.1", "三级标题");
        String heading1Line = linesContaining(text, "一级标题").stream()
                .filter(line -> line.contains("小四号黑体"))
                .findFirst()
                .orElse("");
        if (!heading1Line.isBlank()) {
            HeadingStyleRule heading = result.formatSpec().getHeadingRules().computeIfAbsent(1, ignored -> {
                HeadingStyleRule created = new HeadingStyleRule();
                created.setLevel(1);
                return created;
            });
            heading.setEastAsiaFont("黑体");
            heading.setFontSizePt(FontSizeNameMapper.toPt("小四号"));
            if (heading1Line.contains("加粗")) {
                heading.setBold(true);
                result.mark("headingRules.1.bold", "一级标题加粗");
            }
            Double multiple = parseLineSpacingMultiple(heading1Line);
            if (multiple != null) {
                heading.setLineSpacingMultiple(multiple);
                result.mark("headingRules.1.lineSpacingMultiple", "一级标题" + multiple + "倍行距");
            }
            result.mark("headingRules.1.eastAsiaFont", "一级标题黑体");
            result.mark("headingRules.1.fontSizePt", "一级标题小四号");
        }
        if (text.contains("二级和三级节标题") && text.contains("中文宋体") && text.contains("五号")) {
            for (int level : List.of(2, 3)) {
                HeadingStyleRule heading = result.formatSpec().getHeadingRules().computeIfAbsent(level, ignored -> {
                    HeadingStyleRule created = new HeadingStyleRule();
                    created.setLevel(level);
                    return created;
                });
                heading.setEastAsiaFont("宋体");
                heading.setAsciiFont("Times New Roman");
                heading.setLatinFont("Times New Roman");
                heading.setFontSizePt(FontSizeNameMapper.toPt("五号"));
                heading.setAlignment("BOTH");
                heading.setLineSpacingRule("FIXED");
                heading.setLineSpacingPt(16.0);
                heading.setSpaceBeforePt(0.0);
                heading.setSpaceAfterPt(0.0);
                result.mark("headingRules." + level + ".eastAsiaFont", level + "级标题宋体");
                result.mark("headingRules." + level + ".fontSizePt", level + "级标题五号");
            }
        }
    }

    private Double parseLineSpacingMultiple(String line) {
        Matcher matcher = MULTIPLE_LINE_SPACING_VALUE.matcher(line);
        if (matcher.find()) {
            return Double.parseDouble(matcher.group(1));
        }
        matcher = MULTIPLE_LINE_SPACING_PREFIX.matcher(line);
        if (matcher.find()) {
            return Double.parseDouble(matcher.group(1));
        }
        return null;
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
