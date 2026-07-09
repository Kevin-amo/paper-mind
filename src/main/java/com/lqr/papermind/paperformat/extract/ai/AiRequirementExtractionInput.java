package com.lqr.papermind.paperformat.extract.ai;

import com.lqr.papermind.paperformat.extract.DocxTemplateEvidence;

/** 输入已发送到AI辅助需求提取器。 */
public record AiRequirementExtractionInput(
        DocxTemplateEvidence evidence,
        int maxInputChars,
        String model
) {
}
