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
import java.util.Map;
import java.util.Objects;

@Component
public class PaperFormatChecker {

    private static final double MM_TOLERANCE = 1.0;
    private static final double FONT_TOLERANCE = 0.25;
    private static final double LINE_SPACING_TOLERANCE = 0.05;

    public FormatCheckReport check(FormatSpec spec, DocumentFormatProfile profile) {
        FormatCheckReport report = new FormatCheckReport();
        checkPage(spec.getPageRule(), profile.getPageRule(), report);
        checkHeaderFooter(spec.getHeaderFooterRule(), profile.getHeaderFooterRule(), report);
        ParagraphFormatSnapshot body = profile.getParagraphs().isEmpty() ? null : profile.getParagraphs().getFirst();
        checkParagraphRule("BODY", "正文", spec.getBodyRule(), body, "ERROR", report);
        spec.getHeadingRules().forEach((level, rule) -> {
            ParagraphFormatSnapshot actual = profile.getHeadings().stream()
                    .filter(item -> Objects.equals(item.getLevel(), level))
                    .findFirst()
                    .orElse(null);
            checkParagraphRule("HEADING_" + level, level + "级标题", rule, actual, "WARNING", report);
        });
        summarize(report);
        return report;
    }

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

    private void checkHeaderFooter(HeaderFooterRule expected, HeaderFooterRule actual, FormatCheckReport report) {
        if (expected == null || actual == null) {
            return;
        }
        if (expected.getHeaderText() != null && !expected.getHeaderText().equals(actual.getHeaderText())) {
            add(report, "HEADER_TEXT", "ERROR", "页眉", expected.getHeaderText(), actual.getHeaderText(),
                    "页眉文字不符合模板要求", "按学校模板设置页眉文字");
        }
        if (expected.isHeaderCentered() != actual.isHeaderCentered()) {
            add(report, "HEADER_ALIGNMENT", "ERROR", "页眉", "居中", actual.isHeaderCentered() ? "居中" : "非居中",
                    "页眉对齐方式不符合要求", "将页眉设置为居中");
        }
        if (expected.isFooterPageNumber() != actual.isFooterPageNumber()) {
            add(report, "FOOTER_PAGE_NUMBER", "ERROR", "页脚", "PAGE 字段页码", actual.isFooterPageNumber() ? "PAGE 字段页码" : "未识别 PAGE 字段页码",
                    "页脚页码字段不符合要求", "使用 Word 页码字段插入居中页码");
        }
        if (expected.isFooterCentered() != actual.isFooterCentered()) {
            add(report, "FOOTER_ALIGNMENT", "ERROR", "页脚", "居中", actual.isFooterCentered() ? "居中" : "非居中",
                    "页脚对齐方式不符合要求", "将页脚页码设置为居中");
        }
    }

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

    private void compareMm(FormatCheckReport report, String code, String location, Double expected, Double actual) {
        if (!close(expected, actual, MM_TOLERANCE)) {
            add(report, code, "ERROR", "页面设置/" + location, mm(expected), mm(actual),
                    location + "不符合模板要求", "按模板调整" + location);
        }
    }

    private boolean close(Double expected, Double actual, double tolerance) {
        if (expected == null) {
            return true;
        }
        if (actual == null) {
            return false;
        }
        return Math.abs(expected - actual) <= tolerance;
    }

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

    private String pageSize(PageRule rule) {
        return mm(rule.getPageWidthMm()) + " x " + mm(rule.getPageHeightMm());
    }

    private String mm(Double value) {
        return value == null ? "未识别" : String.format(java.util.Locale.ROOT, "%.1fmm", value);
    }
}
