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
}
