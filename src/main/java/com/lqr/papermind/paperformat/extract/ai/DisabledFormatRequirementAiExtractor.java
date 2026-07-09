package com.lqr.papermind.paperformat.extract.ai;

/** No-op implementation used when AI extraction is disabled. */
public class DisabledFormatRequirementAiExtractor implements FormatRequirementAiExtractor {
    @Override
    public AiRequirementExtractionResult extract(AiRequirementExtractionInput input) {
        return AiRequirementExtractionResult.empty();
    }
}
