package com.lqr.papermind.paperformat;

import com.lqr.papermind.paperformat.check.PaperFormatChecker;
import com.lqr.papermind.paperformat.model.DocumentFormatProfile;
import com.lqr.papermind.paperformat.model.FormatCheckReport;
import com.lqr.papermind.paperformat.model.FormatSpec;
import com.lqr.papermind.paperformat.model.HeaderFooterRule;
import com.lqr.papermind.paperformat.model.PageRule;
import com.lqr.papermind.paperformat.model.ParagraphFormatSnapshot;
import com.lqr.papermind.paperformat.model.ParagraphStyleRule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 论文格式检查器测试类，验证页面、页眉页脚、正文格式检查的正确性
 */
class PaperFormatCheckerTest {

    /** 测试页面尺寸、页眉文字和正文字号不匹配时应生成清晰的违规报告 */
    @Test
    void checkShouldGenerateClearViolationsForMismatchedPageAndBodyFormat() {
        FormatSpec spec = spec();
        DocumentFormatProfile profile = profile();

        FormatCheckReport report = new PaperFormatChecker().check(spec, profile);

        assertThat(report.getStatus()).isEqualTo("FAILED");
        assertThat(report.getSummary().get("ERROR")).isEqualTo(3);
        assertThat(report.getViolations()).extracting("code")
                .contains("PAGE_SIZE", "HEADER_TEXT", "BODY_FONT_SIZE");
        assertThat(report.getViolations()).allSatisfy(violation -> {
            assertThat(violation.getExpected()).isNotBlank();
            assertThat(violation.getActual()).isNotBlank();
            assertThat(violation.getSuggestion()).isNotBlank();
        });
    }

    /** Section rules extracted from template callouts should be checked against matching document paragraphs. */
    @Test
    void checkShouldReportTitleFontSizeMismatchFromSectionRule() {
        FormatSpec spec = new FormatSpec();
        ParagraphStyleRule titleRule = new ParagraphStyleRule();
        titleRule.setFontSizePt(10.5);
        titleRule.setEastAsiaFont("宋体");
        titleRule.setBold(true);
        spec.getSectionRules().put("title", titleRule);
        DocumentFormatProfile profile = new DocumentFormatProfile();
        ParagraphFormatSnapshot title = new ParagraphFormatSnapshot();
        title.setText("基于 XX 系统的设计与实现");
        title.setFontSizePt(12.0);
        title.setEastAsiaFont("宋体");
        title.setBold(true);
        profile.setParagraphs(List.of(title));

        FormatCheckReport report = new PaperFormatChecker().check(spec, profile);

        assertThat(report.getStatus()).isEqualTo("FAILED");
        assertThat(report.getViolations()).anySatisfy(violation -> {
            assertThat(violation.getCode()).isEqualTo("SECTION_TITLE_FONT_SIZE");
            assertThat(violation.getExpected()).isEqualTo("10.5pt");
            assertThat(violation.getActual()).isEqualTo("12.0pt");
        });
    }

    /** Section checks must inspect the matched section paragraph instead of only the first body paragraph. */
    @Test
    void checkShouldReportTocTitleFontMismatchWhereTheContentsParagraphAppears() {
        FormatSpec spec = new FormatSpec();
        ParagraphStyleRule tocRule = new ParagraphStyleRule();
        tocRule.setEastAsiaFont("黑体");
        tocRule.setFontSizePt(16.0);
        spec.getSectionRules().put("tocTitle", tocRule);

        DocumentFormatProfile profile = new DocumentFormatProfile();
        ParagraphFormatSnapshot title = new ParagraphFormatSnapshot();
        title.setText("基于 XX 系统的设计与实现");
        title.setEastAsiaFont("宋体");
        title.setFontSizePt(10.5);
        ParagraphFormatSnapshot contents = new ParagraphFormatSnapshot();
        contents.setText("目录");
        contents.setEastAsiaFont("宋体");
        contents.setFontSizePt(10.5);
        profile.setParagraphs(List.of(title, contents));

        FormatCheckReport report = new PaperFormatChecker().check(spec, profile);

        assertThat(report.getStatus()).isEqualTo("FAILED");
        assertThat(report.getViolations()).anySatisfy(violation -> {
            assertThat(violation.getCode()).isEqualTo("SECTION_TOC_TITLE_FONT");
            assertThat(violation.getExpected()).isEqualTo("黑体");
            assertThat(violation.getActual()).isEqualTo("宋体");
        });
    }

    /** Role-based checks must inspect all matching paragraphs, not only the first body or first heading. */
    @Test
    void checkShouldReportRoleViolationsAcrossAbstractTocBodyAndRepeatedHeadings() {
        FormatSpec spec = new FormatSpec();
        ParagraphStyleRule abstractRule = new ParagraphStyleRule();
        abstractRule.setEastAsiaFont("楷体");
        abstractRule.setFontSizePt(10.5);
        spec.getRoleRules().put("cnAbstractContent", abstractRule);
        ParagraphStyleRule tocRule = new ParagraphStyleRule();
        tocRule.setEastAsiaFont("宋体");
        tocRule.setFontSizePt(10.5);
        spec.getRoleRules().put("tocEntry1", tocRule);
        ParagraphStyleRule bodyRule = new ParagraphStyleRule();
        bodyRule.setEastAsiaFont("宋体");
        bodyRule.setLineSpacingRule("FIXED");
        bodyRule.setLineSpacingPt(16.0);
        spec.getRoleRules().put("body", bodyRule);
        ParagraphStyleRule heading2Rule = new ParagraphStyleRule();
        heading2Rule.setEastAsiaFont("宋体");
        heading2Rule.setFontSizePt(10.5);
        spec.getRoleRules().put("heading2", heading2Rule);

        DocumentFormatProfile profile = new DocumentFormatProfile();
        profile.setParagraphs(List.of(
                snapshot(1, "摘要内容", "cnAbstractContent", "黑体", 10.5, null),
                snapshot(2, "1 绪论\t1", "tocEntry1", "黑体", 12.0, null),
                snapshot(3, "第一个正文段落", "body", "宋体", 10.5, 16.0),
                snapshot(4, "第二个正文段落", "body", "宋体", 10.5, 18.0)
        ));
        profile.setHeadings(List.of(
                snapshot(5, "1.1 正确标题", "heading2", "宋体", 10.5, null),
                snapshot(6, "1.2 错误标题", "heading2", "黑体", 10.5, null)
        ));

        FormatCheckReport report = new PaperFormatChecker().check(spec, profile);

        assertThat(report.getStatus()).isEqualTo("FAILED");
        assertThat(report.getViolations()).extracting("code").contains(
                "CN_ABSTRACT_CONTENT_FONT",
                "TOC_ENTRY_1_FONT",
                "TOC_ENTRY_1_FONT_SIZE",
                "BODY_4_LINE_SPACING",
                "HEADING_2_FONT"
        );
    }

    /** Repeated body paragraphs should be summarized by problem type to avoid thousands of duplicate rows. */
    @Test
    void checkShouldCollapseRepeatedBodyViolationsByProblemType() {
        FormatSpec spec = new FormatSpec();
        ParagraphStyleRule bodyRule = new ParagraphStyleRule();
        bodyRule.setEastAsiaFont("宋体");
        bodyRule.setAsciiFont("Times New Roman");
        bodyRule.setFontSizePt(10.5);
        bodyRule.setLineSpacingRule("FIXED");
        bodyRule.setLineSpacingPt(16.0);
        spec.getRoleRules().put("body", bodyRule);

        DocumentFormatProfile profile = new DocumentFormatProfile();
        profile.setRoleParagraphs(List.of(
                bodySnapshot(8, "第一段正文", "黑体", "宋体", 16.0, null),
                bodySnapshot(9, "第二段正文", "黑体", "宋体", 16.0, null),
                bodySnapshot(10, "第三段正文", "黑体", "宋体", 16.0, null)
        ));

        FormatCheckReport report = new PaperFormatChecker().check(spec, profile);

        assertThat(report.getViolations()).filteredOn(violation -> violation.getCode().equals("BODY_FONT_SIZE")).hasSize(1);
        assertThat(report.getViolations()).filteredOn(violation -> violation.getCode().equals("BODY_ASCII_FONT")).hasSize(1);
        assertThat(report.getViolations()).filteredOn(violation -> violation.getCode().equals("BODY_LINE_SPACING")).hasSize(1);
        assertThat(report.getViolations()).extracting("code").doesNotContain("BODY_8_LINE_SPACING", "BODY_9_LINE_SPACING");
        assertThat(report.getViolations()).anySatisfy(violation -> {
            assertThat(violation.getCode()).isEqualTo("BODY_FONT_SIZE");
            assertThat(violation.getLocation()).contains("正文", "共 3 段", "#8", "#9", "#10");
            assertThat(violation.getActual()).contains("16.0pt");
        });
    }

    /** Collapsed body locations should stay readable even when a long document has many matching violations. */
    @Test
    void checkShouldLimitCollapsedBodyLocationSummary() {
        FormatSpec spec = new FormatSpec();
        ParagraphStyleRule bodyRule = new ParagraphStyleRule();
        bodyRule.setFontSizePt(10.5);
        spec.getRoleRules().put("body", bodyRule);

        DocumentFormatProfile profile = new DocumentFormatProfile();
        profile.setRoleParagraphs(java.util.stream.IntStream.rangeClosed(1, 12)
                .mapToObj(index -> bodySnapshot(index, "正文段落" + index, "宋体", "Times New Roman", 16.0, 16.0))
                .toList());

        FormatCheckReport report = new PaperFormatChecker().check(spec, profile);

        assertThat(report.getViolations()).singleElement().satisfies(violation -> {
            assertThat(violation.getCode()).isEqualTo("BODY_FONT_SIZE");
            assertThat(violation.getLocation()).contains("正文", "共 12 段", "#1", "#8", "等 12 段");
            assertThat(violation.getLocation()).doesNotContain("#9");
        });
    }

    private FormatSpec spec() {
        FormatSpec spec = new FormatSpec();
        PageRule page = new PageRule();
        page.setPageWidthMm(210.0);
        page.setPageHeightMm(297.0);
        page.setMarginTopMm(25.4);
        page.setMarginBottomMm(25.4);
        page.setMarginLeftMm(31.8);
        page.setMarginRightMm(31.8);
        spec.setPageRule(page);
        HeaderFooterRule headerFooter = new HeaderFooterRule();
        headerFooter.setHeaderText("学校论文");
        headerFooter.setHeaderCentered(true);
        headerFooter.setFooterPageNumber(true);
        headerFooter.setFooterCentered(true);
        spec.setHeaderFooterRule(headerFooter);
        ParagraphStyleRule body = new ParagraphStyleRule();
        body.setFontSizePt(12.0);
        body.setEastAsiaFont("宋体");
        body.setAlignment("BOTH");
        spec.setBodyRule(body);
        return spec;
    }

    private DocumentFormatProfile profile() {
        DocumentFormatProfile profile = new DocumentFormatProfile();
        PageRule page = new PageRule();
        page.setPageWidthMm(215.0);
        page.setPageHeightMm(297.0);
        page.setMarginTopMm(25.4);
        page.setMarginBottomMm(25.4);
        page.setMarginLeftMm(31.8);
        page.setMarginRightMm(31.8);
        profile.setPageRule(page);
        HeaderFooterRule headerFooter = new HeaderFooterRule();
        headerFooter.setHeaderText("错误页眉");
        headerFooter.setHeaderCentered(true);
        headerFooter.setFooterPageNumber(true);
        headerFooter.setFooterCentered(true);
        profile.setHeaderFooterRule(headerFooter);
        ParagraphFormatSnapshot paragraph = new ParagraphFormatSnapshot();
        paragraph.setText("正文");
        paragraph.setFontSizePt(10.5);
        paragraph.setEastAsiaFont("宋体");
        paragraph.setAlignment("BOTH");
        profile.setParagraphs(List.of(paragraph));
        return profile;
    }

    private ParagraphFormatSnapshot snapshot(int index, String text, String role, String font, Double fontSize, Double lineSpacingPt) {
        ParagraphFormatSnapshot snapshot = new ParagraphFormatSnapshot();
        snapshot.setIndex(index);
        snapshot.setParagraphIndex(index);
        snapshot.setText(text);
        snapshot.setRole(role);
        snapshot.setEastAsiaFont(font);
        snapshot.setFontSizePt(fontSize);
        snapshot.setLineSpacingRule(lineSpacingPt == null ? null : "FIXED");
        snapshot.setLineSpacingPt(lineSpacingPt);
        return snapshot;
    }

    private ParagraphFormatSnapshot bodySnapshot(int index, String text, String eastAsiaFont, String asciiFont, Double fontSize, Double lineSpacingPt) {
        ParagraphFormatSnapshot snapshot = snapshot(index, text, "body", eastAsiaFont, fontSize, lineSpacingPt);
        snapshot.setAsciiFont(asciiFont);
        snapshot.setHAnsiFont(asciiFont);
        return snapshot;
    }
}
