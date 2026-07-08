package com.lqr.papermind.paperformat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqr.papermind.paperformat.extract.DocxFormatSpecExtractor;
import com.lqr.papermind.paperformat.model.FormatSpec;
import com.lqr.papermind.paperformat.model.HeadingStyleRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DOCX格式规则提取器测试类，验证从docx文件中提取格式规则的正确性
 */
class DocxFormatSpecExtractorTest {

    @TempDir
    Path tempDir;

    /** 测试提取页面边距、页眉文字和页脚页码字段 */
    @Test
    void extractShouldReadPageMarginsHeaderAndFooterPageField() throws Exception {
        Path docx = DocxTestDocuments.templateDocx(tempDir, true);

        FormatSpec spec;
        try (var input = Files.newInputStream(docx)) {
            spec = new DocxFormatSpecExtractor().extract(input);
        }

        assertThat(spec.getPageRule().getPageWidthMm()).isCloseTo(210.0, within(0.2));
        assertThat(spec.getPageRule().getPageHeightMm()).isCloseTo(297.0, within(0.2));
        assertThat(spec.getPageRule().getMarginTopMm()).isCloseTo(25.4, within(0.2));
        assertThat(spec.getPageRule().getMarginLeftMm()).isCloseTo(31.8, within(0.2));
        assertThat(spec.getHeaderFooterRule().getHeaderText()).isEqualTo("某大学本科毕业论文");
        assertThat(spec.getHeaderFooterRule().isHeaderCentered()).isTrue();
        assertThat(spec.getHeaderFooterRule().isFooterPageNumber()).isTrue();
        assertThat(spec.getHeaderFooterRule().isFooterCentered()).isTrue();
    }

    /** 测试不将缓存的页脚数字误判为PAGE字段 */
    @Test
    void extractShouldNotTreatCachedFooterNumberAsPageField() throws Exception {
        Path docx = DocxTestDocuments.templateDocx(tempDir, false);

        FormatSpec spec;
        try (var input = Files.newInputStream(docx)) {
            spec = new DocxFormatSpecExtractor().extract(input);
        }

        assertThat(spec.getHeaderFooterRule().isFooterPageNumber()).isFalse();
    }

    /** 测试文本规则优先于OOXML回退规则，并验证冲突检测 */
    @Test
    void extractShouldPreferRequirementTextRulesWithOoxmlFallback() throws Exception {
        Path docx = DocxTestDocuments.requirementTemplateDocx(tempDir, false);

        FormatSpec spec;
        try (var input = Files.newInputStream(docx)) {
            spec = new DocxFormatSpecExtractor().extract(input);
        }

        assertThat(spec.getHeaderFooterRule().getHeaderText()).isEqualTo("人工智能与信息工程学院毕业设计");
        assertThat(spec.getHeaderFooterRule().isHeaderCentered()).isTrue();
        assertThat(spec.getHeaderFooterRule().getHeaderFontEastAsia()).isEqualTo("宋体");
        assertThat(spec.getHeaderFooterRule().getHeaderFontSizePt()).isEqualTo(9.0);
        assertThat(spec.getPageRule().getMarginTopMm()).isCloseTo(20.0, within(0.01));
        assertThat(spec.getPageRule().getMarginBottomMm()).isCloseTo(20.0, within(0.01));
        assertThat(spec.getPageRule().getInsideMarginMm()).isCloseTo(25.0, within(0.01));
        assertThat(spec.getPageRule().getOutsideMarginMm()).isCloseTo(20.0, within(0.01));
        assertThat(spec.getPageRule().getGutterMm()).isCloseTo(5.0, within(0.01));
        assertThat(spec.getPageRule().getHeaderDistanceMm()).isCloseTo(15.0, within(0.01));
        assertThat(spec.getPageRule().getFooterDistanceMm()).isCloseTo(15.0, within(0.01));
        assertThat(spec.getHeaderFooterRule().isFooterPageNumber()).isTrue();
        assertThat(spec.getHeaderFooterRule().isFooterCentered()).isTrue();
        assertThat(spec.getBodyRule().getLineSpacingRule()).isEqualTo("FIXED");
        assertThat(spec.getBodyRule().getLineSpacingPt()).isEqualTo(16.0);
        assertThat(spec.getBodyRule().getSpaceBeforePt()).isEqualTo(0.0);
        assertThat(spec.getBodyRule().getSpaceAfterPt()).isEqualTo(0.0);
        assertThat(spec.getBodyRule().getLatinFont()).isEqualTo("Times New Roman");
        assertThat(spec.getBodyRule().getAsciiFont()).isEqualTo("Times New Roman");
        assertThat(spec.getHeadingRules().get(1).getNumberingPattern()).isEqualTo("1");
        assertThat(spec.getHeadingRules().get(2).getNumberingPattern()).isEqualTo("1.1");
        assertThat(spec.getHeadingRules().get(3).getNumberingPattern()).isEqualTo("1.1.1");
        assertThat(spec.getExtractionReport()).containsEntry("source", "text_requirement_with_ooxml_fallback");
        assertThat((List<?>) spec.getExtractionReport().get("textRules")).isNotEmpty();
        assertThat(spec.getPageRule().getPageWidthMm()).isCloseTo(210.0, within(0.01));
        assertThat(spec.getPageRule().getPageHeightMm()).isCloseTo(297.0, within(0.01));
        assertThat((List<String>) spec.getExtractionReport().get("ooxmlFallbackFields")).isNotEmpty();
        assertThat((List<?>) spec.getExtractionReport().get("conflicts")).isEmpty();
        assertThat((List<?>) spec.getExtractionReport().get("referenceRequirements")).isNotEmpty();
    }

    /** 测试从继承样式中解析页眉对齐方式 */
    @Test
    void extractShouldResolveHeaderAlignmentFromBasedOnStyle() throws Exception {
        Path docx = DocxTestDocuments.styleInheritedHeaderDocx(tempDir);

        FormatSpec spec;
        try (var input = Files.newInputStream(docx)) {
            spec = new DocxFormatSpecExtractor().extract(input);
        }

        assertThat(spec.getHeaderFooterRule().isHeaderCentered()).isTrue();
    }

    /** 测试冲突报告并保持文本规则值 */
    @Test
    void extractShouldReportConflictsAndKeepTextRuleValue() throws Exception {
        Path docx = DocxTestDocuments.requirementTemplateDocx(tempDir, true);

        FormatSpec spec;
        try (var input = Files.newInputStream(docx)) {
            spec = new DocxFormatSpecExtractor().extract(input);
        }

        assertThat(spec.getPageRule().getMarginTopMm()).isCloseTo(20.0, within(0.01));
        assertThat(spec.getHeaderFooterRule().getHeaderText()).isEqualTo("人工智能与信息工程学院毕业设计");
        List<Map<String, Object>> conflicts = (List<Map<String, Object>>) spec.getExtractionReport().get("conflicts");
        assertThat(conflicts).isNotEmpty();
        assertThat(conflicts).anySatisfy(conflict -> {
            assertThat(conflict.get("field")).isEqualTo("pageRule.marginTopMm");
            assertThat(conflict.get("textValue")).isEqualTo(20.0);
        });
    }

    /** 测试FormatSpec序列化/反序列化新字段的正确性 */
    @Test
    void formatSpecShouldSerializeNewFields() {
        FormatSpec spec = new FormatSpec();
        spec.getPageRule().setMirrorMargins(true);
        spec.getPageRule().setDuplexPrint(true);
        spec.getPageRule().setInsideMarginMm(25.0);
        spec.getHeaderFooterRule().setHeaderFontEastAsia("宋体");
        spec.getHeaderFooterRule().setHeaderFontSizePt(9.0);
        spec.getBodyRule().setLineSpacingRule("FIXED");
        spec.getBodyRule().setLineSpacingPt(16.0);
        spec.getBodyRule().setLatinFont("Times New Roman");
        HeadingStyleRule heading = new HeadingStyleRule();
        heading.setLevel(1);
        heading.setNumberingPattern("1");
        spec.getHeadingRules().put(1, heading);

        FormatSpec restored = new ObjectMapper().convertValue(spec, FormatSpec.class);

        assertThat(restored.getPageRule().getMirrorMargins()).isTrue();
        assertThat(restored.getPageRule().getDuplexPrint()).isTrue();
        assertThat(restored.getPageRule().getInsideMarginMm()).isEqualTo(25.0);
        assertThat(restored.getHeaderFooterRule().getHeaderFontEastAsia()).isEqualTo("宋体");
        assertThat(restored.getHeaderFooterRule().getHeaderFontSizePt()).isEqualTo(9.0);
        assertThat(restored.getBodyRule().getLineSpacingRule()).isEqualTo("FIXED");
        assertThat(restored.getBodyRule().getLineSpacingPt()).isEqualTo(16.0);
        assertThat(restored.getBodyRule().getLatinFont()).isEqualTo("Times New Roman");
        assertThat(restored.getHeadingRules().get(1).getNumberingPattern()).isEqualTo("1");
    }

    private static org.assertj.core.data.Offset<Double> within(double value) {
        return org.assertj.core.data.Offset.offset(value);
    }
}