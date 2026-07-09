package com.lqr.papermind.paperformat.check;

import com.lqr.papermind.paperformat.model.DocumentFormatProfile;
import com.lqr.papermind.paperformat.model.FormatCheckReport;
import com.lqr.papermind.paperformat.model.FormatSpec;
import com.lqr.papermind.paperformat.model.FormatViolation;
import com.lqr.papermind.paperformat.model.HeaderFooterRule;
import com.lqr.papermind.paperformat.model.PageRule;
import com.lqr.papermind.paperformat.model.ParagraphFormatSnapshot;
import com.lqr.papermind.paperformat.model.ParagraphStyleRule;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 论文格式检查器，对比文档格式画像与模板规则，生成检查报告
 */
@Component
public class PaperFormatChecker {

    /** 毫米数值容差 */
    private static final double MM_TOLERANCE = 1.0;
    /** 字号容差（磅） */
    private static final double FONT_TOLERANCE = 0.25;
    /** 行距倍数容差 */
    private static final double LINE_SPACING_TOLERANCE = 0.05;
    /** 聚合正文违规时最多展示的段落定位数量 */
    private static final int MAX_COLLAPSED_LOCATION_MARKERS = 8;

    /**
     * 执行格式检查，对比文档格式画像与模板规则
     *
     * @param spec    模板格式规则
     * @param profile 文档格式画像
     * @return 格式检查报告
     */
    public FormatCheckReport check(FormatSpec spec, DocumentFormatProfile profile) {
        FormatCheckReport report = new FormatCheckReport();
        checkPage(spec.getPageRule(), profile.getPageRule(), report);
        checkHeaderFooter(spec.getHeaderFooterRule(), profile.getHeaderFooterRule(), report);
        if (spec.getRoleRules() == null || !spec.getRoleRules().containsKey("body")) {
            ParagraphFormatSnapshot body = profile.getParagraphs().isEmpty() ? null : profile.getParagraphs().getFirst();
            checkParagraphRule("BODY", "正文", spec.getBodyRule(), body, "ERROR", report);
        }
        spec.getHeadingRules().forEach((level, rule) -> {
            ParagraphFormatSnapshot actual = profile.getHeadings().stream()
                    .filter(item -> Objects.equals(item.getLevel(), level))
                    .findFirst()
                    .orElse(null);
            checkParagraphRule("HEADING_" + level, level + "级标题", rule, actual, "WARNING", report);
        });
        checkSectionRules(spec, profile, report);
        checkRoleRules(spec, profile, report);
        summarize(report);
        return report;
    }

    private void checkRoleRules(FormatSpec spec, DocumentFormatProfile profile, FormatCheckReport report) {
        if (spec.getRoleRules() == null || spec.getRoleRules().isEmpty()) {
            return;
        }
        List<ParagraphFormatSnapshot> snapshots = new ArrayList<>();
        if (profile.getRoleParagraphs() != null && !profile.getRoleParagraphs().isEmpty()) {
            snapshots.addAll(profile.getRoleParagraphs());
        } else {
            if (profile.getParagraphs() != null) {
                snapshots.addAll(profile.getParagraphs());
            }
            if (profile.getHeadings() != null) {
                snapshots.addAll(profile.getHeadings());
            }
        }
        snapshots.sort(java.util.Comparator.comparingInt(ParagraphFormatSnapshot::getIndex));
        spec.getRoleRules().forEach((role, rule) -> {
            List<ParagraphFormatSnapshot> matches = snapshots.stream()
                    .filter(item -> role.equals(item.getRole()))
                    .toList();
            if (matches.isEmpty()) {
                add(report, roleCode(role) + "_MISSING", "WARNING", roleLabel(role), "存在", "未识别",
                        roleLabel(role) + "未识别", "检查是否使用了模板中的对应样式");
                return;
            }
            FormatCheckReport roleReport = new FormatCheckReport();
            for (ParagraphFormatSnapshot actual : matches) {
                checkRoleParagraph(role, rule, actual, roleReport);
            }
            report.getViolations().addAll(collapseRepeatedRoleViolations(role, roleReport.getViolations()));
        });
    }

    private List<FormatViolation> collapseRepeatedRoleViolations(String role, List<FormatViolation> violations) {
        if (violations.size() < 2) {
            return violations;
        }
        Map<String, List<FormatViolation>> groups = new LinkedHashMap<>();
        for (FormatViolation violation : violations) {
            String key = String.join("\u0001",
                    normalizeViolationCode(role, violation.getCode()),
                    nullToEmpty(violation.getSeverity()),
                    nullToEmpty(violation.getExpected()),
                    nullToEmpty(violation.getMessage()),
                    nullToEmpty(violation.getSuggestion()));
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(violation);
        }
        List<FormatViolation> result = new ArrayList<>();
        for (List<FormatViolation> group : groups.values()) {
            if (group.size() == 1) {
                result.add(group.getFirst());
            } else {
                result.add(collapseRoleGroup(role, group));
            }
        }
        return result;
    }

    private FormatViolation collapseRoleGroup(String role, List<FormatViolation> group) {
        FormatViolation first = group.getFirst();
        return FormatViolation.of(
                normalizeViolationCode(role, first.getCode()),
                first.getSeverity(),
                summarizeLocations(role, group),
                first.getExpected(),
                summarizeActuals(group),
                first.getMessage(),
                first.getSuggestion()
        );
    }

    private String normalizeViolationCode(String role, String code) {
        if ("body".equals(role)) {
            return normalizeBodyCode(code);
        }
        return code == null ? "" : code;
    }

    private String normalizeBodyCode(String code) {
        return code == null ? "" : code.replaceFirst("^BODY_\\d+_", "BODY_");
    }

    private String summarizeLocations(String role, List<FormatViolation> group) {
        List<String> locations = group.stream()
                .map(FormatViolation::getLocation)
                .map(this::paragraphMarker)
                .distinct()
                .toList();
        String markerText = String.join("、", locations.stream().limit(MAX_COLLAPSED_LOCATION_MARKERS).toList());
        if (locations.size() > MAX_COLLAPSED_LOCATION_MARKERS) {
            markerText = markerText + "，等 " + group.size() + " 段";
        }
        return roleLabel(role) + "，共 " + group.size() + " 段（" + markerText + "）";
    }

    private String paragraphMarker(String location) {
        if (location == null || location.isBlank()) {
            return "未知段落";
        }
        int marker = location.lastIndexOf('#');
        return marker >= 0 ? location.substring(marker) : location;
    }

    private String summarizeActuals(List<FormatViolation> group) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (FormatViolation violation : group) {
            counts.merge(nullToEmpty(violation.getActual()), 1, Integer::sum);
        }
        return counts.entrySet().stream()
                .map(entry -> entry.getValue() == group.size()
                        ? entry.getKey()
                        : entry.getKey() + "（" + entry.getValue() + "段）")
                .reduce((left, right) -> left + "；" + right)
                .orElse("");
    }

    private void checkRoleParagraph(String role,
                                    ParagraphStyleRule expected,
                                    ParagraphFormatSnapshot actual,
                                    FormatCheckReport report) {
        String code = roleCode(role);
        String indexedCode = "body".equals(role) ? code + "_" + actual.getIndex() : code;
        String location = roleLabel(role) + "#" + actual.getIndex();
        if (expected.getEastAsiaFont() != null && actual.getEastAsiaFont() != null && !expected.getEastAsiaFont().equals(actual.getEastAsiaFont())) {
            add(report, code + "_FONT", "ERROR", location, expected.getEastAsiaFont(), actual.getEastAsiaFont(),
                    roleLabel(role) + "字体不符合要求", "将" + roleLabel(role) + "中文字体设置为" + expected.getEastAsiaFont());
        }
        String expectedAscii = firstNonBlank(expected.getAsciiFont(), expected.getLatinFont());
        String actualAscii = firstNonBlank(actual.getAsciiFont(), actual.getHAnsiFont());
        if (expectedAscii != null && actualAscii != null && !expectedAscii.equals(actualAscii)) {
            add(report, code + "_ASCII_FONT", "ERROR", location, expectedAscii, actualAscii,
                    roleLabel(role) + "英文字体不符合要求", "将" + roleLabel(role) + "英文字体设置为" + expectedAscii);
        }
        if (!close(expected.getFontSizePt(), actual.getFontSizePt(), FONT_TOLERANCE)) {
            add(report, code + "_FONT_SIZE", "ERROR", location, expected.getFontSizePt() + "pt", actual.getFontSizePt() + "pt",
                    roleLabel(role) + "字号不符合要求", "将" + roleLabel(role) + "字号设置为" + expected.getFontSizePt() + "pt");
        }
        if (expected.getLineSpacingRule() != null && actual.getLineSpacingRule() != null && !expected.getLineSpacingRule().equals(actual.getLineSpacingRule())) {
            add(report, indexedCode + "_LINE_SPACING_RULE", "ERROR", location, expected.getLineSpacingRule(), actual.getLineSpacingRule(),
                    roleLabel(role) + "行距规则不符合要求", "按模板调整行距规则");
        }
        if (!close(expected.getLineSpacingPt(), actual.getLineSpacingPt(), FONT_TOLERANCE)) {
            add(report, indexedCode + "_LINE_SPACING", "ERROR", location, expected.getLineSpacingPt() + "pt", actual.getLineSpacingPt() + "pt",
                    roleLabel(role) + "固定行距不符合要求", "按模板调整固定行距");
        }
        if (!close(expected.getLineSpacingMultiple(), actual.getLineSpacingMultiple(), LINE_SPACING_TOLERANCE)) {
            add(report, indexedCode + "_LINE_SPACING", "ERROR", location, expected.getLineSpacingMultiple() + "倍", actual.getLineSpacingMultiple() + "倍",
                    roleLabel(role) + "行距倍数不符合要求", "按模板调整行距倍数");
        }
        if (expected.getAlignment() != null && actual.getAlignment() != null && !expected.getAlignment().equals(actual.getAlignment())) {
            add(report, code + "_ALIGNMENT", "ERROR", location, expected.getAlignment(), actual.getAlignment(),
                    roleLabel(role) + "对齐方式不符合要求", "按模板调整对齐方式");
        }
        if (expected.getBold() != null && actual.getBold() != null && !expected.getBold().equals(actual.getBold())) {
            add(report, code + "_BOLD", "ERROR", location, expected.getBold() ? "加粗" : "不加粗", actual.getBold() ? "加粗" : "不加粗",
                    roleLabel(role) + "加粗设置不符合要求", "按模板调整加粗设置");
        }
    }

    private void checkSectionRules(FormatSpec spec, DocumentFormatProfile profile, FormatCheckReport report) {
        if (spec.getSectionRules() == null || spec.getSectionRules().isEmpty()) {
            return;
        }
        List<ParagraphFormatSnapshot> snapshots = new ArrayList<>();
        if (profile.getParagraphs() != null) {
            snapshots.addAll(profile.getParagraphs());
        }
        if (profile.getHeadings() != null) {
            snapshots.addAll(profile.getHeadings());
        }
        snapshots.sort(java.util.Comparator.comparingInt(ParagraphFormatSnapshot::getIndex));
        spec.getSectionRules().forEach((section, rule) -> {
            ParagraphFormatSnapshot actual = sectionSnapshot(section, snapshots);
            checkParagraphRule("SECTION_" + sectionCode(section), sectionLabel(section), rule, actual, "ERROR", report);
        });
    }

    private ParagraphFormatSnapshot sectionSnapshot(String section, List<ParagraphFormatSnapshot> paragraphs) {
        if (paragraphs == null || paragraphs.isEmpty()) {
            return null;
        }
        return switch (section) {
            case "title" -> paragraphs.getFirst();
            case "abstractLabel", "abstractContent" -> firstStartingWith(paragraphs, "摘要");
            case "tocTitle" -> firstMatchingAny(paragraphs, List.of("目录", "目次", "contents", "table of contents"));
            case "keywordsLabel" -> firstStartingWith(paragraphs, "关键词");
            case "englishAbstractContent" -> firstStartingWithIgnoreCase(paragraphs, "abstract");
            default -> null;
        };
    }

    private ParagraphFormatSnapshot firstStartingWith(List<ParagraphFormatSnapshot> paragraphs, String prefix) {
        return paragraphs.stream()
                .filter(item -> item.getText() != null && item.getText().strip().startsWith(prefix))
                .findFirst()
                .orElse(null);
    }

    private ParagraphFormatSnapshot firstStartingWithIgnoreCase(List<ParagraphFormatSnapshot> paragraphs, String prefix) {
        return paragraphs.stream()
                .filter(item -> item.getText() != null && item.getText().strip().toLowerCase(java.util.Locale.ROOT).startsWith(prefix))
                .findFirst()
                .orElse(null);
    }

    private ParagraphFormatSnapshot firstMatchingAny(List<ParagraphFormatSnapshot> paragraphs, List<String> values) {
        return paragraphs.stream()
                .filter(item -> item.getText() != null && values.stream().anyMatch(value -> item.getText().strip().equalsIgnoreCase(value)))
                .findFirst()
                .orElse(null);
    }

    private String sectionCode(String section) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < section.length(); i++) {
            char ch = section.charAt(i);
            if (Character.isUpperCase(ch) && i > 0) {
                result.append('_');
            }
            result.append(Character.toUpperCase(ch));
        }
        return result.toString();
    }

    private String sectionLabel(String section) {
        return switch (section) {
            case "title" -> "标题";
            case "tocTitle" -> "目录标题";
            case "abstractLabel" -> "摘要标签";
            case "abstractContent" -> "摘要内容";
            case "keywordsLabel" -> "关键词标签";
            case "englishAbstractContent" -> "英文摘要内容";
            default -> section;
        };
    }

    private String roleCode(String role) {
        return switch (role) {
            case "paperTitle" -> "PAPER_TITLE";
            case "authorLine" -> "AUTHOR_LINE";
            case "advisorLine" -> "ADVISOR_LINE";
            case "cnAbstractTitle" -> "CN_ABSTRACT_TITLE";
            case "cnAbstractContent" -> "CN_ABSTRACT_CONTENT";
            case "cnKeywordsLabel" -> "CN_KEYWORDS_LABEL";
            case "cnKeywordsContent" -> "CN_KEYWORDS_CONTENT";
            case "enAbstractTitle" -> "EN_ABSTRACT_TITLE";
            case "enAbstractContent" -> "EN_ABSTRACT_CONTENT";
            case "enKeywordsLabel" -> "EN_KEYWORDS_LABEL";
            case "enKeywordsContent" -> "EN_KEYWORDS_CONTENT";
            case "tocTitle" -> "TOC_TITLE";
            case "tocEntry1" -> "TOC_ENTRY_1";
            case "tocEntry2" -> "TOC_ENTRY_2";
            case "tocEntry3" -> "TOC_ENTRY_3";
            case "body" -> "BODY";
            case "heading1" -> "HEADING_1";
            case "heading2" -> "HEADING_2";
            case "heading3" -> "HEADING_3";
            case "header" -> "HEADER";
            case "footerPageNumber" -> "FOOTER_PAGE_NUMBER";
            case "references" -> "REFERENCES";
            case "figureCaption" -> "FIGURE_CAPTION";
            case "tableCaption" -> "TABLE_CAPTION";
            default -> sectionCode(role);
        };
    }

    private String roleLabel(String role) {
        return switch (role) {
            case "paperTitle" -> "论文题目";
            case "cnAbstractContent" -> "中文摘要内容";
            case "cnKeywordsLabel" -> "中文关键词标签";
            case "enAbstractContent" -> "英文摘要内容";
            case "enKeywordsLabel" -> "英文关键词标签";
            case "tocTitle" -> "目录标题";
            case "tocEntry1" -> "一级目录项";
            case "tocEntry2" -> "二级目录项";
            case "tocEntry3" -> "三级目录项";
            case "body" -> "正文";
            case "heading1" -> "一级标题";
            case "heading2" -> "二级标题";
            case "heading3" -> "三级标题";
            case "header" -> "页眉";
            case "footerPageNumber" -> "页脚页码";
            case "references" -> "参考文献";
            case "figureCaption" -> "图标题";
            case "tableCaption" -> "表标题";
            default -> role;
        };
    }

    private String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /** 检查页面设置（尺寸、边距、装订线、页眉页脚距离） */
    private void checkPage(PageRule expected, PageRule actual, FormatCheckReport report) {
        if (expected == null || actual == null) {
            return;
        }
        if (!close(expected.getPageWidthMm(), actual.getPageWidthMm(), MM_TOLERANCE)
                || !close(expected.getPageHeightMm(), actual.getPageHeightMm(), MM_TOLERANCE)) {
            add(report, "PAGE_SIZE", "ERROR", "页面设置",
                    pageSize(expected), pageSize(actual), "页面尺寸不符合模板要求", "将页面大小设置为 A4 或模板指定尺寸");
        }
        compareMm(report, "MARGIN_TOP", "上边距", expected.getMarginTopMm(), actual.getMarginTopMm());
        compareMm(report, "MARGIN_RIGHT", "右边距", expected.getMarginRightMm(), actual.getMarginRightMm());
        compareMm(report, "MARGIN_BOTTOM", "下边距", expected.getMarginBottomMm(), actual.getMarginBottomMm());
        compareMm(report, "MARGIN_LEFT", "左边距", expected.getMarginLeftMm(), actual.getMarginLeftMm());
        compareMm(report, "GUTTER", "装订线", expected.getGutterMm(), actual.getGutterMm());
        compareMm(report, "HEADER_DISTANCE", "页眉距", expected.getHeaderDistanceMm(), actual.getHeaderDistanceMm());
        compareMm(report, "FOOTER_DISTANCE", "页脚距", expected.getFooterDistanceMm(), actual.getFooterDistanceMm());
    }

    /** 检查页眉页脚设置（页眉文字、居中、页码字段、页脚对齐） */
    private void checkHeaderFooter(HeaderFooterRule expected, HeaderFooterRule actual, FormatCheckReport report) {
        if (expected == null || actual == null) {
            return;
        }
        if (expected.getHeaderText() != null && !expected.getHeaderText().equals(actual.getHeaderText())) {
            add(report, "HEADER_TEXT", "ERROR", "页眉", expected.getHeaderText(), actual.getHeaderText(),
                    "页眉文字不符合模板要求", "按学校模板设置页眉文字");
        }
        if (expected.isHeaderCentered() != actual.isHeaderCentered()) {
            add(report, "HEADER_ALIGNMENT", "ERROR", "页眉",
                    expected.isHeaderCentered() ? "居中" : "非居中",
                    actual.isHeaderCentered() ? "居中" : "非居中",
                    "页眉对齐方式不符合要求",
                    expected.isHeaderCentered() ? "将页眉设置为居中" : "将页眉设置为非居中");
        }
        if (expected.isFooterPageNumber() != actual.isFooterPageNumber()) {
            add(report, "FOOTER_PAGE_NUMBER", "ERROR", "页脚", "PAGE 字段页码", actual.isFooterPageNumber() ? "PAGE 字段页码" : "未识别 PAGE 字段页码",
                    "页脚页码字段不符合要求", "使用 Word 页码字段插入居中页码");
        }
        if (expected.isFooterCentered() != actual.isFooterCentered()) {
            add(report, "FOOTER_ALIGNMENT", "ERROR", "页脚",
                    expected.isFooterCentered() ? "居中" : "非居中",
                    actual.isFooterCentered() ? "居中" : "非居中",
                    "页脚对齐方式不符合要求",
                    expected.isFooterCentered() ? "将页脚页码设置为居中" : "将页脚页码设置为非居中");
        }
    }

    /** 检查段落样式规则（字体、字号、行距、对齐方式） */
    private void checkParagraphRule(String prefix,
                                    String location,
                                    ParagraphStyleRule expected,
                                    ParagraphFormatSnapshot actual,
                                    String severity,
                                    FormatCheckReport report) {
        if (expected == null) {
            return;
        }
        if (actual == null) {
            add(report, prefix + "_MISSING", severity, location, "存在", "未识别",
                    location + "未识别", "检查是否使用了模板中的" + location + "样式");
            return;
        }
        if (expected.getEastAsiaFont() != null && !expected.getEastAsiaFont().equals(actual.getEastAsiaFont())) {
            add(report, prefix + "_FONT", severity, location, expected.getEastAsiaFont(), actual.getEastAsiaFont(),
                    location + "字体不符合要求", "将" + location + "中文字体设置为 " + expected.getEastAsiaFont());
        }
        if (!close(expected.getFontSizePt(), actual.getFontSizePt(), FONT_TOLERANCE)) {
            add(report, prefix + "_FONT_SIZE", severity, location, expected.getFontSizePt() + "pt", actual.getFontSizePt() + "pt",
                    location + "字号不符合要求", "将" + location + "字号设置为 " + expected.getFontSizePt() + "pt");
        }
        if (!close(expected.getLineSpacingMultiple(), actual.getLineSpacingMultiple(), LINE_SPACING_TOLERANCE)) {
            add(report, prefix + "_LINE_SPACING", severity, location, expected.getLineSpacingMultiple() + "倍", actual.getLineSpacingMultiple() + "倍",
                    location + "行距不符合要求", "按模板调整行距");
        }
        if (expected.getAlignment() != null && actual.getAlignment() != null && !expected.getAlignment().equals(actual.getAlignment())) {
            add(report, prefix + "_ALIGNMENT", severity, location, expected.getAlignment(), actual.getAlignment(),
                    location + "对齐方式不符合要求", "按模板调整对齐方式");
        }
    }

    /** 比较两个毫米数值是否在容差范围内 */
    private void compareMm(FormatCheckReport report, String code, String location, Double expected, Double actual) {
        if (!close(expected, actual, MM_TOLERANCE)) {
            add(report, code, "ERROR", "页面设置/" + location, mm(expected), mm(actual),
                    location + "不符合模板要求", "按模板调整" + location);
        }
    }

    /** 判断两个Double值是否在容差范围内 */
    private boolean close(Double expected, Double actual, double tolerance) {
        if (expected == null) {
            return true;
        }
        if (actual == null) {
            return false;
        }
        return Math.abs(expected - actual) <= tolerance;
    }

    /** 向检查报告中添加一条违规记录 */
    private void add(FormatCheckReport report,
                     String code,
                     String severity,
                     String location,
                     String expected,
                     String actual,
                     String message,
                     String suggestion) {
        report.getViolations().add(FormatViolation.of(code, severity, location, expected, actual, message, suggestion));
    }

    /** 统计违规数量并设置报告状态（有ERROR则FAILED，否则PASSED） */
    private void summarize(FormatCheckReport report) {
        Map<String, Integer> summary = new LinkedHashMap<>();
        summary.put("ERROR", 0);
        summary.put("WARNING", 0);
        summary.put("REVIEW", 0);
        for (FormatViolation violation : report.getViolations()) {
            summary.computeIfPresent(violation.getSeverity(), (key, value) -> value + 1);
        }
        report.setSummary(summary);
        report.setStatus(summary.get("ERROR") > 0 ? "FAILED" : "PASSED");
    }

    /** 格式化页面尺寸为 "宽 x 高" 的字符串 */
    private String pageSize(PageRule rule) {
        return mm(rule.getPageWidthMm()) + " x " + mm(rule.getPageHeightMm());
    }

    /** 格式化毫米数值，为null时返回"未识别" */
    private String mm(Double value) {
        return value == null ? "未识别" : String.format(java.util.Locale.ROOT, "%.1fmm", value);
    }
}
