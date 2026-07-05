package com.lqr.papermind.document.structured.model;

/**
 * 模型补全结果与原始输出。
 */
public record ModelCompletionResult(
        StructuredParseResult result,
        String rawModelOutput,
        String errorMessage
) {
    public static ModelCompletionResult empty() {
        PaperStructuredContent content = PaperStructuredContentSupport.emptyContent();
        return new ModelCompletionResult(
                new StructuredParseResult(content, PaperStructuredContentSupport.emptyEvidence("MODEL"), PaperStructuredContentSupport.emptyFields(content)),
                null,
                null
        );
    }
}