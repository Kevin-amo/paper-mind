package com.lqr.papermind.paperformat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqr.papermind.paperformat.config.PaperFormatAiExtractionProperties;
import com.lqr.papermind.paperformat.extract.DocxTemplateEvidence;
import com.lqr.papermind.paperformat.extract.DocxFormatSpecExtractor;
import com.lqr.papermind.paperformat.extract.aiExtract.AiExtractedRule;
import com.lqr.papermind.paperformat.extract.aiExtract.AiRequirementExtractionResult;
import com.lqr.papermind.paperformat.extract.aiExtract.FormatRequirementAiExtractor;
import com.lqr.papermind.paperformat.model.FormatSpec;
import com.lqr.papermind.paperformat.model.HeadingStyleRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

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

    /** Real software templates use custom named styles and prose requirements, not built-in Heading styles. */
    @Test
    void extractShouldReadSoftwareExampleSectionAndCustomHeadingStyles() throws Exception {
        Path docx = DocxTestDocuments.softwareExampleTemplateDocx(tempDir);

        FormatSpec spec;
        try (var input = Files.newInputStream(docx)) {
            spec = new DocxFormatSpecExtractor().extract(input);
        }

        assertThat(spec.getSectionRules().get("title").getEastAsiaFont()).isEqualTo("宋体");
        assertThat(spec.getSectionRules().get("title").getFontSizePt()).isEqualTo(10.5);
        assertThat(spec.getSectionRules().get("title").getBold()).isTrue();
        assertThat(spec.getSectionRules().get("tocTitle").getEastAsiaFont()).isEqualTo("黑体");
        assertThat(spec.getSectionRules().get("tocTitle").getFontSizePt()).isEqualTo(16.0);
        assertThat(spec.getSectionRules().get("abstractContent").getEastAsiaFont()).isEqualTo("楷体");
        assertThat(spec.getSectionRules().get("abstractContent").getFontSizePt()).isEqualTo(10.5);
        assertThat(spec.getSectionRules().get("englishAbstractContent").getAsciiFont()).isEqualTo("Times New Roman");
        assertThat(spec.getBodyRule().getEastAsiaFont()).isEqualTo("宋体");
        assertThat(spec.getBodyRule().getFontSizePt()).isEqualTo(10.5);
        assertThat(spec.getHeadingRules().get(1).getEastAsiaFont()).isEqualTo("黑体");
        assertThat(spec.getHeadingRules().get(1).getFontSizePt()).isEqualTo(12.0);
        assertThat(spec.getHeadingRules().get(2).getEastAsiaFont()).isEqualTo("宋体");
        assertThat(spec.getHeadingRules().get(2).getFontSizePt()).isEqualTo(10.5);
        assertThat(spec.getHeadingRules().get(3).getEastAsiaFont()).isEqualTo("宋体");
        assertThat(spec.getHeadingRules().get(3).getFontSizePt()).isEqualTo(10.5);
    }

    /** Role rules should be populated from text requirements, custom styles, and actual template paragraphs. */
    @Test
    void extractShouldPopulateRoleRulesSourcesAndRealTemplateStyleEvidence() throws Exception {
        Path docx = DocxTestDocuments.softwareExampleTemplateDocx(tempDir);

        FormatSpec spec;
        try (var input = Files.newInputStream(docx)) {
            spec = new DocxFormatSpecExtractor().extract(input);
        }

        assertThat(spec.getRoleRules()).containsKeys(
                "paperTitle",
                "cnAbstractContent",
                "cnKeywordsLabel",
                "enAbstractContent",
                "enKeywordsLabel",
                "tocTitle",
                "tocEntry1",
                "tocEntry2",
                "tocEntry3",
                "body",
                "heading1",
                "heading2",
                "heading3",
                "header",
                "footerPageNumber"
        );
        assertThat(spec.getRoleRules().get("paperTitle").getStyleId()).isEqualTo("aff1");
        assertThat(spec.getRoleRules().get("body").getStyleId()).isEqualTo("aff2");
        assertThat(spec.getRoleRules().get("heading1").getStyleId()).isEqualTo("aff3");
        assertThat(spec.getRoleRules().get("heading2").getStyleId()).isEqualTo("aff4");
        assertThat(spec.getRoleRules().get("heading3").getStyleId()).isEqualTo("aff5");
        assertThat(spec.getRoleRules().get("tocEntry1").getStyleId()).isEqualTo("TOC1");
        assertThat(spec.getRoleRules().get("tocEntry2").getStyleId()).isEqualTo("TOC2");
        assertThat(spec.getRoleRules().get("tocEntry3").getStyleId()).isEqualTo("TOC3");
        assertThat(spec.getRoleRules().get("cnAbstractContent").getEastAsiaFont()).isEqualTo("楷体");
        assertThat(spec.getRoleRules().get("enAbstractContent").getAsciiFont()).isEqualTo("Times New Roman");
        assertThat(spec.getRoleRules().get("tocEntry1").getEastAsiaFont()).isEqualTo("宋体");
        assertThat(spec.getRoleRules().get("tocEntry1").getFontSizePt()).isEqualTo(10.5);

        assertThat((Map<String, Object>) spec.getExtractionReport().get("sources"))
                .containsKeys("roleRules.body.eastAsiaFont", "roleRules.heading1.eastAsiaFont", "roleRules.cnAbstractContent.eastAsiaFont");
        assertThat((List<?>) spec.getExtractionReport().get("templateParagraphSamples")).isNotEmpty();
    }

    /** 测试冲突报告并保持文本规则值 */
    @Test
    void extractShouldIgnoreToc10AsBodyCandidateAndPreserveTocRules() throws Exception {
        Path docx = DocxTestDocuments.toc10BodyCandidateTemplateDocx(tempDir);

        FormatSpec spec;
        try (var input = Files.newInputStream(docx)) {
            spec = new DocxFormatSpecExtractor().extract(input);
        }

        assertThat(spec.getBodyRule().getStyleId()).isNotIn("TOC10", "TOC1", "TOC2", "TOC3");
        assertThat(spec.getRoleRules().get("body").getStyleId()).isEqualTo("aff2");
        assertThat(spec.getRoleRules().get("tocTitle").getStyleId()).isEqualTo("TOC10");
        assertThat(spec.getSectionRules().get("tocTitle").getStyleId()).isEqualTo("TOC10");
        assertThat((List<String>) spec.getExtractionReport().get("warnings"))
                .contains("ignored body candidate from role=tocTitle styleId=TOC10");
    }

    @Test
    void extractShouldKeepHeadingAndKeywordRegressionRules() throws Exception {
        Path docx = DocxTestDocuments.misalignedSoftwareExampleTemplateDocx(tempDir);

        FormatSpec spec;
        try (var input = Files.newInputStream(docx)) {
            spec = new DocxFormatSpecExtractor().extract(input);
        }

        HeadingStyleRule heading1 = spec.getHeadingRules().get(1);
        HeadingStyleRule heading2 = spec.getHeadingRules().get(2);
        HeadingStyleRule heading3 = spec.getHeadingRules().get(3);

        assertThat(heading1.getLineSpacingMultiple()).isEqualTo(1.35);
        assertThat(heading1.getLineSpacingRule()).isNull();
        assertThat(spec.getRoleRules().get("heading1").getLineSpacingMultiple()).isEqualTo(1.35);
        assertThat(spec.getRoleRules().get("heading1").getLineSpacingRule()).isNull();
        assertThat(heading2.getBold()).isNull();
        assertThat(heading3.getBold()).isNull();
        assertThat(heading2.getLineSpacingRule()).isEqualTo("FIXED");
        assertThat(heading2.getLineSpacingPt()).isEqualTo(16.0);
        assertThat(heading2.getLineSpacingMultiple()).isNull();
        assertThat(heading3.getLineSpacingRule()).isEqualTo("FIXED");
        assertThat(heading3.getLineSpacingPt()).isEqualTo(16.0);
        assertThat(heading3.getLineSpacingMultiple()).isNull();
        assertThat(spec.getRoleRules().get("cnKeywordsLabel").getBold()).isTrue();
        assertThat(spec.getRoleRules().get("cnKeywordsContent").getEastAsiaFont()).isEqualTo("楷体");
        assertThat(spec.getRoleRules().get("cnKeywordsContent").getFontSizePt()).isEqualTo(10.5);
        assertThat(spec.getRoleRules().get("enKeywordsLabel").getAsciiFont()).isEqualTo("Times New Roman");
        assertThat(spec.getRoleRules().get("enKeywordsLabel").getHAnsiFont()).isEqualTo("Times New Roman");
        assertThat(spec.getRoleRules().get("enKeywordsLabel").getLatinFont()).isEqualTo("Times New Roman");
        assertThat(spec.getRoleRules().get("enKeywordsLabel").getFontSizePt()).isEqualTo(10.5);
        assertThat(spec.getRoleRules().get("enKeywordsContent").getAsciiFont()).isEqualTo("Times New Roman");
        assertThat(spec.getRoleRules().get("enKeywordsContent").getHAnsiFont()).isEqualTo("Times New Roman");
        assertThat(spec.getRoleRules().get("enKeywordsContent").getLatinFont()).isEqualTo("Times New Roman");
        assertThat(spec.getRoleRules().get("enKeywordsContent").getFontSizePt()).isEqualTo(10.5);
        assertThat(spec.getBodyRule().getStyleId()).isEqualTo("aff2");
        assertThat(spec.getRoleRules().get("body").getStyleId()).isEqualTo("aff2");
        assertThat(spec.getBodyRule().getEvidenceText()).contains("正文").doesNotContain("一级标题");
        assertThat(spec.getRoleRules().get("body").getEvidenceText()).contains("正文").doesNotContain("一级标题");
        assertThat(spec.getRoleRules().get("tocTitle").getStyleId()).isEqualTo("TOC10");
        assertThat(spec.getRoleRules().get("tocEntry1").getStyleId()).isEqualTo("TOC1");
        assertThat(spec.getRoleRules().get("tocEntry2").getStyleId()).isEqualTo("TOC2");
        assertThat(spec.getRoleRules().get("tocEntry3").getStyleId()).isEqualTo("TOC3");

        List<String> warnings = (List<String>) spec.getExtractionReport().get("warnings");
        List<Map<String, Object>> conflicts = (List<Map<String, Object>>) spec.getExtractionReport().get("conflicts");
        String diagnostics = warnings + " " + conflicts;
        assertThat(diagnostics)
                .contains("ignored body candidate")
                .contains("TOC10")
                .contains("heading")
                .contains("keyword");
    }

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

    /** Text requirements must win over conflicting OOXML while preserving conflict evidence. */
    @Test
    void extractShouldReportRoleRuleConflictsWithoutOverwritingTextRequirement() throws Exception {
        Path docx = DocxTestDocuments.conflictingSoftwareTemplateDocx(tempDir);

        FormatSpec spec;
        try (var input = Files.newInputStream(docx)) {
            spec = new DocxFormatSpecExtractor().extract(input);
        }

        assertThat(spec.getRoleRules().get("body").getEastAsiaFont()).isEqualTo("宋体");
        List<Map<String, Object>> conflicts = (List<Map<String, Object>>) spec.getExtractionReport().get("conflicts");
        assertThat(conflicts).anySatisfy(conflict -> {
            assertThat(conflict.get("fieldPath")).isEqualTo("roleRules.body.eastAsiaFont");
            assertThat(conflict.get("preferredSource")).isEqualTo("TEXT_REQUIREMENT");
            assertThat(conflict.get("preferredValue")).isEqualTo("宋体");
            assertThat(conflict.get("otherSource")).isEqualTo("TEMPLATE_INSTANCE");
            assertThat(conflict.get("otherValue")).isEqualTo("黑体");
        });
    }

    /** AI rules should fill fields that fixed text does not express. */
    @Test
    void extractShouldUseAiRulesFromTextBoxEvidence() throws Exception {
        Path docx = DocxTestDocuments.aiAnnotatedTemplateDocx(tempDir, 25.0);
        AtomicReference<DocxTemplateEvidence> capturedEvidence = new AtomicReference<>();
        FormatRequirementAiExtractor aiExtractor = input -> {
            capturedEvidence.set(input.evidence());
            AiRequirementExtractionResult result = new AiRequirementExtractionResult();
            result.addRule(new AiExtractedRule("bodyRule.eastAsiaFont", "宋体", 0.93, "标题：宋体（加粗），五号，两端对齐。行距固定值16磅。", "TEXT_BOX"));
            result.addRule(new AiExtractedRule("bodyRule.fontSizePt", 10.5, 0.93, "标题：宋体（加粗），五号，两端对齐。行距固定值16磅。", "TEXT_BOX"));
            result.addRule(new AiExtractedRule("bodyRule.bold", true, 0.93, "标题：宋体（加粗），五号，两端对齐。行距固定值16磅。", "TEXT_BOX"));
            result.addRule(new AiExtractedRule("bodyRule.alignment", "BOTH", 0.91, "标题：宋体（加粗），五号，两端对齐。行距固定值16磅。", "TEXT_BOX"));
            result.addRule(new AiExtractedRule("bodyRule.lineSpacingRule", "FIXED", 0.94, "行距固定值16磅", "TEXT_BOX"));
            result.addRule(new AiExtractedRule("bodyRule.lineSpacingPt", 16.0, 0.94, "行距固定值16磅", "TEXT_BOX"));
            result.addRule(new AiExtractedRule("sectionRules.title.eastAsiaFont", "宋体", 0.94, "标题：宋体（加粗），五号，两端对齐。行距固定值16磅。", "TEXT_BOX"));
            result.addRule(new AiExtractedRule("sectionRules.title.fontSizePt", 10.5, 0.94, "标题：宋体（加粗），五号，两端对齐。行距固定值16磅。", "TEXT_BOX"));
            result.addRule(new AiExtractedRule("sectionRules.title.bold", true, 0.94, "标题：宋体（加粗），五号，两端对齐。行距固定值16磅。", "TEXT_BOX"));
            result.addRule(new AiExtractedRule("sectionRules.title.alignment", "BOTH", 0.94, "标题：宋体（加粗），五号，两端对齐。行距固定值16磅。", "TEXT_BOX"));
            result.addRule(new AiExtractedRule("headerFooterRule.headerText", "人工智能与信息工程学院毕业设计", 0.95, "页眉：中文宋体，小五号，居中，人工智能与信息工程学院毕业设计", "TEXT_BOX"));
            result.addRule(new AiExtractedRule("headerFooterRule.headerFontEastAsia", "宋体", 0.95, "页眉：中文宋体，小五号", "TEXT_BOX"));
            result.addRule(new AiExtractedRule("headerFooterRule.headerFontSizePt", 9.0, 0.95, "页眉：中文宋体，小五号", "TEXT_BOX"));
            result.addRule(new AiExtractedRule("headerFooterRule.headerCentered", true, 0.95, "页眉：居中", "TEXT_BOX"));
            result.referenceRequirements().add(Map.of("type", "citationStyle", "value", "[1]", "confidence", 0.95, "evidence", "使用[1]方式引用"));
            result.notes().add("封面不要出现页眉，当前模型暂不表达封面例外。");
            return result;
        };

        FormatSpec spec;
        try (var input = Files.newInputStream(docx)) {
            spec = new DocxFormatSpecExtractor(aiExtractor, aiProperties(true)).extract(input);
        }

        assertThat(capturedEvidence.get().textBoxTexts()).isNotEmpty();
        assertThat(capturedEvidence.get().textBoxTexts()).anySatisfy(text -> assertThat(text).contains("页眉"));
        assertThat(spec.getHeaderFooterRule().getHeaderText()).isEqualTo("人工智能与信息工程学院毕业设计");
        assertThat(spec.getHeaderFooterRule().getHeaderFontSizePt()).isEqualTo(9.0);
        assertThat(spec.getBodyRule().getEastAsiaFont()).isEqualTo("宋体");
        assertThat(spec.getBodyRule().getFontSizePt()).isEqualTo(10.5);
        assertThat(spec.getBodyRule().getLineSpacingPt()).isEqualTo(16.0);
        assertThat(spec.getSectionRules().get("title").getFontSizePt()).isEqualTo(10.5);
        assertThat(spec.getSectionRules().get("title").getBold()).isTrue();
        assertThat((List<?>) spec.getExtractionReport().get("aiRules")).isNotEmpty();
        assertThat((List<?>) spec.getExtractionReport().get("referenceRequirements")).isNotEmpty();
        assertThat((List<?>) spec.getExtractionReport().get("notes")).isNotEmpty();
        assertThat(spec.getExtractionReport()).containsEntry("source", "text_requirement_with_ai_and_ooxml_fallback");
    }

    /** Fixed requirement text should outrank conflicting AI output. */
    @Test
    void extractShouldPreferFixedTextRulesOverAiRules() throws Exception {
        Path docx = DocxTestDocuments.requirementTemplateDocx(tempDir, false);
        FormatRequirementAiExtractor aiExtractor = input -> {
            AiRequirementExtractionResult result = new AiRequirementExtractionResult();
            result.addRule(new AiExtractedRule("headerFooterRule.headerFontSizePt", 10.5, 0.92, "页眉：五号", "TEXT_BOX"));
            return result;
        };

        FormatSpec spec;
        try (var input = Files.newInputStream(docx)) {
            spec = new DocxFormatSpecExtractor(aiExtractor, aiProperties(true)).extract(input);
        }

        assertThat(spec.getHeaderFooterRule().getHeaderFontSizePt()).isEqualTo(9.0);
        List<Map<String, Object>> conflicts = (List<Map<String, Object>>) spec.getExtractionReport().get("conflicts");
        assertThat(conflicts).anySatisfy(conflict -> {
            assertThat(conflict.get("fieldPath")).isEqualTo("headerFooterRule.headerFontSizePt");
            assertThat(conflict.get("preferredSource")).isEqualTo("TEXT_REQUIREMENT");
            assertThat(conflict.get("preferredValue")).isEqualTo(9.0);
            assertThat(conflict.get("otherSource")).isEqualTo("AI_REQUIREMENT");
        });
    }

    /** AI should outrank OOXML fallback when no fixed text rule exists. */
    @Test
    void extractShouldPreferAiRulesOverOoxmlFallback() throws Exception {
        Path docx = DocxTestDocuments.aiAnnotatedTemplateDocx(tempDir, 25.0);
        FormatRequirementAiExtractor aiExtractor = input -> {
            AiRequirementExtractionResult result = new AiRequirementExtractionResult();
            result.addRule(new AiExtractedRule("pageRule.marginTopMm", 20.0, 0.9, "上边距2cm", "TEXT_BOX"));
            return result;
        };

        FormatSpec spec;
        try (var input = Files.newInputStream(docx)) {
            spec = new DocxFormatSpecExtractor(aiExtractor, aiProperties(true)).extract(input);
        }

        assertThat(spec.getPageRule().getMarginTopMm()).isEqualTo(20.0);
        List<Map<String, Object>> conflicts = (List<Map<String, Object>>) spec.getExtractionReport().get("conflicts");
        assertThat(conflicts).anySatisfy(conflict -> {
            assertThat(conflict.get("fieldPath")).isEqualTo("pageRule.marginTopMm");
            assertThat(conflict.get("preferredSource")).isEqualTo("AI_REQUIREMENT");
            assertThat(conflict.get("preferredValue")).isEqualTo(20.0);
            assertThat(conflict.get("otherSource")).isEqualTo("OOXML_FALLBACK");
        });
    }

    /** Low-confidence AI output should be reported but not applied. */
    @Test
    void extractShouldReportLowConfidenceAiRulesWithoutApplyingThem() throws Exception {
        Path docx = DocxTestDocuments.aiAnnotatedTemplateDocx(tempDir, 25.0);
        FormatRequirementAiExtractor aiExtractor = input -> {
            AiRequirementExtractionResult result = new AiRequirementExtractionResult();
            result.addRule(new AiExtractedRule("headerFooterRule.headerText", "低置信页眉", 0.45, "疑似页眉", "TEXT_BOX"));
            return result;
        };

        FormatSpec spec;
        try (var input = Files.newInputStream(docx)) {
            spec = new DocxFormatSpecExtractor(aiExtractor, aiProperties(true)).extract(input);
        }

        assertThat(spec.getHeaderFooterRule().getHeaderText()).isNotEqualTo("低置信页眉");
        assertThat((List<?>) spec.getExtractionReport().get("lowConfidenceAiRules")).isNotEmpty();
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

    private PaperFormatAiExtractionProperties aiProperties(boolean enabled) {
        PaperFormatAiExtractionProperties properties = new PaperFormatAiExtractionProperties();
        properties.setEnabled(enabled);
        properties.setMinConfidence(0.70);
        properties.setMaxInputChars(12000);
        return properties;
    }
}
