package com.lqr.papermind.paperformat.extract.aiExtract;

import com.lqr.papermind.ai.service.LlmService;
import com.lqr.papermind.ai.service.PromptConstructionService;
import lombok.RequiredArgsConstructor;

/** 基于项目现有 LLM 抽象层的 AI 格式需求提取器实现。 */
@RequiredArgsConstructor
public class AiFormatRequirementExtractor implements FormatRequirementAiExtractor {

    private final LlmService llmService;
    private final FormatRequirementPromptBuilder promptBuilder;
    private final AiRequirementResultMapper resultMapper;

    @Override
    public AiRequirementExtractionResult extract(AiRequirementExtractionInput input) {
        try {
            PromptConstructionService.Prompt prompt = promptBuilder.build(input);
            return resultMapper.parse(llmService.generate(prompt));
        } catch (RuntimeException ex) {
            AiRequirementExtractionResult result = AiRequirementExtractionResult.empty();
            result.warnings().add("AI requirement extraction failed: " + safeMessage(ex));
            return result;
        }
    }

    private String safeMessage(RuntimeException ex) {
        return ex.getMessage() == null || ex.getMessage().isBlank() ? ex.getClass().getSimpleName() : ex.getMessage();
    }
}
