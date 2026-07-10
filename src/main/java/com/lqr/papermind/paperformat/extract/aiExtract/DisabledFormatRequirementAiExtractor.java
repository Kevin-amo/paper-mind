package com.lqr.papermind.paperformat.extract.aiExtract;

/** AI 提取功能禁用时使用的空实现。 */
public class DisabledFormatRequirementAiExtractor implements FormatRequirementAiExtractor {
    @Override
    public AiRequirementExtractionResult extract(AiRequirementExtractionInput input) {
        return AiRequirementExtractionResult.empty();
    }
}
