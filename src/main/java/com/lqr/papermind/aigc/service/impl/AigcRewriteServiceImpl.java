package com.lqr.papermind.aigc.service.impl;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqr.papermind.aigc.dto.AigcQualityScoreResponse;
import com.lqr.papermind.aigc.dto.AigcRewriteRequest;
import com.lqr.papermind.aigc.dto.AigcRewriteResponse;
import com.lqr.papermind.aigc.dto.AigcRiskPatternResponse;
import com.lqr.papermind.aigc.prompt.AigcRewritePromptTemplate;
import com.lqr.papermind.aigc.service.AigcRewriteService;
import com.lqr.papermind.ai.service.LlmService;
import com.lqr.papermind.ai.service.PromptConstructionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 段落学术润色服务实现。
 *
 * <p>调用大模型对段落进行改写，并对模型返回的 JSON 进行容错解析。
 * 当模型输出格式异常时，返回合法的降级响应而非 500 错误。</p>
 *
 * <p>Prompt rules adapted from Yezery/aigc-down-skill, MIT License.
 * Repository: https://github.com/Yezery/aigc-down-skill</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AigcRewriteServiceImpl implements AigcRewriteService {

    private static final String DEFAULT_DISCIPLINE = "通用中文学术论文";
    private static final String DEFAULT_REWRITE_STRENGTH = "standard";

    private final LlmService llmService;
    private final AigcRewritePromptTemplate promptTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public AigcRewriteResponse rewrite(AigcRewriteRequest request) {
        String discipline = normalizeDiscipline(request.discipline());
        String rewriteStrength = normalizeRewriteStrength(request.rewriteStrength());
        List<String> keepTerms = request.keepTerms() != null ? request.keepTerms() : Collections.emptyList();

        PromptConstructionService.Prompt prompt = promptTemplate.buildPrompt(
                request.paragraph(), discipline, rewriteStrength, keepTerms, request.extraRequirements());

        String rawOutput = llmService.generate(prompt);

        return parseModelResponse(rawOutput);
    }

    private String normalizeDiscipline(String discipline) {
        if (discipline == null || discipline.isBlank()) {
            return DEFAULT_DISCIPLINE;
        }
        return discipline.trim();
    }

    private String normalizeRewriteStrength(String rewriteStrength) {
        if (rewriteStrength == null || rewriteStrength.isBlank()) {
            return DEFAULT_REWRITE_STRENGTH;
        }
        return rewriteStrength.trim();
    }

    /**
     * 解析模型返回的 JSON，带有多重容错机制。
     */
    AigcRewriteResponse parseModelResponse(String rawOutput) {
        if (rawOutput == null || rawOutput.isBlank()) {
            return fallbackResponse("", "模型返回为空，请人工复核");
        }

        String cleaned = cleanJsonOutput(rawOutput);

        try {
            ObjectMapper lenientMapper = objectMapper.copy()
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            AigcRewriteResponse response = lenientMapper.readValue(cleaned, AigcRewriteResponse.class);
            return sanitizeResponse(response, cleaned);
        } catch (Exception e) {
            log.warn("Failed to parse model output as JSON, falling back. Error: {}", e.getMessage());
            return fallbackResponse(cleaned, "模型返回格式异常，请人工复核");
        }
    }

    /**
     * 清理模型输出：去除 Markdown 代码块包裹，截取第一个 { 到最后一个 }。
     */
    private String cleanJsonOutput(String raw) {
        String trimmed = raw.trim();

        // 去除 ```json ... ``` 或 ``` ... ``` 包裹
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline >= 0) {
                trimmed = trimmed.substring(firstNewline + 1);
            }
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3);
            }
            trimmed = trimmed.trim();
        }

        // 截取第一个 { 到最后一个 }
        int firstBrace = trimmed.indexOf('{');
        int lastBrace = trimmed.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return trimmed.substring(firstBrace, lastBrace + 1);
        }

        return trimmed;
    }

    /**
     * 对解析后的响应进行空值兜底处理。
     */
    @SuppressWarnings("unchecked")
    private AigcRewriteResponse sanitizeResponse(AigcRewriteResponse response, String cleanedText) {
        if (response == null) {
            return fallbackResponse(cleanedText, "模型返回格式异常，请人工复核");
        }

        String riskLevel = response.riskLevel() != null ? response.riskLevel() : "MEDIUM";
        List<AigcRiskPatternResponse> riskPatterns = response.riskPatterns() != null
                ? response.riskPatterns() : Collections.emptyList();
        String rewrittenText = response.rewrittenText() != null ? response.rewrittenText() : cleanedText;
        List<String> changeNotes = response.changeNotes() != null
                ? response.changeNotes() : Collections.emptyList();
        List<String> warnings = response.warnings() != null
                ? new ArrayList<>(response.warnings()) : new ArrayList<>();
        AigcQualityScoreResponse qualityScore = response.qualityScore() != null
                ? response.qualityScore() : AigcQualityScoreResponse.defaultScore();

        return new AigcRewriteResponse(riskLevel, riskPatterns, rewrittenText, changeNotes, warnings, qualityScore);
    }

    /**
     * 构造降级响应：模型输出无法解析时使用。
     */
    private AigcRewriteResponse fallbackResponse(String text, String warning) {
        List<String> warnings = new ArrayList<>();
        warnings.add(warning);
        return new AigcRewriteResponse(
                "MEDIUM",
                Collections.emptyList(),
                text,
                Collections.emptyList(),
                warnings,
                AigcQualityScoreResponse.defaultScore()
        );
    }
}
