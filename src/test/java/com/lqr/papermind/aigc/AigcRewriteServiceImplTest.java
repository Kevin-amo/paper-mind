package com.lqr.papermind.aigc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqr.papermind.aigc.dto.AigcQualityScoreResponse;
import com.lqr.papermind.aigc.dto.AigcRewriteRequest;
import com.lqr.papermind.aigc.dto.AigcRewriteResponse;
import com.lqr.papermind.aigc.dto.AigcRiskPatternResponse;
import com.lqr.papermind.aigc.prompt.AigcRewritePromptTemplate;
import com.lqr.papermind.aigc.service.impl.AigcRewriteServiceImpl;
import com.lqr.papermind.ai.service.LlmService;
import com.lqr.papermind.ai.service.PromptConstructionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AigcRewriteServiceImpl 单元测试。
 *
 * <p>覆盖：正常请求返回结构化结果、模型返回非法 JSON 时返回合法降级响应。</p>
 */
class AigcRewriteServiceImplTest {

    private LlmService llmService;
    private AigcRewritePromptTemplate promptTemplate;
    private AigcRewriteServiceImpl service;

    private static final String VALID_PARAGRAPH = "依据社会建构主义理论，知识并非客观存在于外部世界，而是通过社会互动和语言协商建构而成的。该理论认为学习是一个主动建构的过程，综上所述，这一观点具有重要意义。";

    @BeforeEach
    void setUp() {
        llmService = mock(LlmService.class);
        promptTemplate = mock(AigcRewritePromptTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper();
        service = new AigcRewriteServiceImpl(llmService, promptTemplate, objectMapper);

        when(promptTemplate.buildPrompt(any(), any(), any(), any(), any()))
                .thenReturn(new PromptConstructionService.Prompt("system", "user"));
    }

    @Test
    void rewrite_normalRequest_returnsStructuredResult() {
        String validJson = """
                {
                  "riskPatterns": [
                    {"type": "理论起笔", "evidence": "依据社会建构主义理论", "suggestion": "将理论移至段中"}
                  ],
                  "rewrittenText": "在课堂观察中，学生通过与同伴对话修正自己的理解。社会建构主义理论能解释这一现象。",
                  "changeNotes": ["将理论从段首移至段中", "删除了段末套话"],
                  "warnings": [],
                  "qualityScore": {
                    "directness": 8,
                    "rhythm": 7,
                    "academicTone": 9,
                    "informationDensity": 8,
                    "meaningPreservation": 9,
                    "overall": 8
                  }
                }
                """;
        when(llmService.generate(any())).thenReturn(validJson);

        AigcRewriteRequest request = new AigcRewriteRequest(
                VALID_PARAGRAPH, null, null, null, null);
        AigcRewriteResponse result = service.rewrite(request);

        assertThat(result.riskPatterns()).hasSize(1);
        assertThat(result.riskPatterns().get(0).type()).isEqualTo("理论起笔");
        assertThat(result.rewrittenText()).contains("社会建构主义理论");
        assertThat(result.changeNotes()).hasSize(2);
        assertThat(result.warnings()).isEmpty();
        assertThat(result.qualityScore().overall()).isEqualTo(8);
    }

    @Test
    void rewrite_jsonCodeBlock_returnsStructuredResult() {
        String codeBlockJson = """
                ```json
                {
                  "riskPatterns": [],
                  "rewrittenText": "改写后的文本",
                  "changeNotes": ["调整了句式"],
                  "warnings": [],
                  "qualityScore": {"directness": 7, "rhythm": 7, "academicTone": 8, "informationDensity": 7, "meaningPreservation": 9, "overall": 7}
                }
                ```
                """;
        when(llmService.generate(any())).thenReturn(codeBlockJson);

        AigcRewriteRequest request = new AigcRewriteRequest(
                VALID_PARAGRAPH, null, null, null, null);
        AigcRewriteResponse result = service.rewrite(request);

        assertThat(result.rewrittenText()).isEqualTo("改写后的文本");
        assertThat(result.qualityScore().overall()).isEqualTo(7);
    }

    @Test
    void rewrite_invalidJson_returnsFallbackResponse() {
        when(llmService.generate(any())).thenReturn("这不是JSON，只是一段普通的中文文本，没有花括号。");

        AigcRewriteRequest request = new AigcRewriteRequest(
                VALID_PARAGRAPH, null, null, null, null);
        AigcRewriteResponse result = service.rewrite(request);

        assertThat(result.warnings()).contains("模型返回格式异常，请人工复核");
        assertThat(result.qualityScore().overall()).isEqualTo(0);
        assertThat(result.qualityScore().directness()).isEqualTo(0);
    }

    @Test
    void rewrite_emptyOutput_returnsFallbackResponse() {
        when(llmService.generate(any())).thenReturn("");

        AigcRewriteRequest request = new AigcRewriteRequest(
                VALID_PARAGRAPH, null, null, null, null);
        AigcRewriteResponse result = service.rewrite(request);

        assertThat(result.warnings()).contains("模型返回为空，请人工复核");
        assertThat(result.qualityScore().overall()).isEqualTo(0);
    }

    @Test
    void rewrite_jsonWithExtraText_returnsStructuredResult() {
        String jsonWithExtra = """
                好的，以下是改写结果：
                {
                  "riskPatterns": [],
                  "rewrittenText": "改写后的文本",
                  "changeNotes": [],
                  "warnings": [],
                  "qualityScore": {"directness": 9, "rhythm": 8, "academicTone": 9, "informationDensity": 8, "meaningPreservation": 10, "overall": 9}
                }
                希望对您有帮助！
                """;
        when(llmService.generate(any())).thenReturn(jsonWithExtra);

        AigcRewriteRequest request = new AigcRewriteRequest(
                VALID_PARAGRAPH, null, null, null, null);
        AigcRewriteResponse result = service.rewrite(request);

        assertThat(result.rewrittenText()).isEqualTo("改写后的文本");
        assertThat(result.qualityScore().overall()).isEqualTo(9);
    }

    @Test
    void rewrite_nullOutput_returnsFallbackResponse() {
        when(llmService.generate(any())).thenReturn(null);

        AigcRewriteRequest request = new AigcRewriteRequest(
                VALID_PARAGRAPH, null, null, null, null);
        AigcRewriteResponse result = service.rewrite(request);

        assertThat(result.warnings()).contains("模型返回为空，请人工复核");
    }

    @Test
    void rewrite_defaultsAppliedWhenNull() {
        String validJson = """
                {
                  "riskPatterns": [],
                  "rewrittenText": "改写后的文本",
                  "changeNotes": [],
                  "warnings": [],
                  "qualityScore": {"directness": 7, "rhythm": 7, "academicTone": 7, "informationDensity": 7, "meaningPreservation": 7, "overall": 7}
                }
                """;
        when(llmService.generate(any())).thenReturn(validJson);

        AigcRewriteRequest request = new AigcRewriteRequest(
                VALID_PARAGRAPH, null, null, null, null);
        AigcRewriteResponse result = service.rewrite(request);

        // Verify the service handled null discipline and rewriteStrength gracefully
        assertThat(result.rewrittenText()).isEqualTo("改写后的文本");
    }
}
