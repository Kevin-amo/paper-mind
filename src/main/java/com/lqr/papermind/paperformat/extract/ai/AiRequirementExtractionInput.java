package com.lqr.papermind.paperformat.extract.ai;

import com.lqr.papermind.paperformat.extract.DocxTemplateEvidence;

/** Input sent to the AI-assisted requirement extractor. */
public record AiRequirementExtractionInput(
        DocxTemplateEvidence evidence,
        int maxInputChars,
        String model
) {
}
