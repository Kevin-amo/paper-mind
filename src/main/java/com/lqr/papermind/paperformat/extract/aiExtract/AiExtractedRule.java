package com.lqr.papermind.paperformat.extract.aiExtract;

/** AI 提取的单条格式要求候选项。 */
public record AiExtractedRule(
        String fieldPath,
        Object value,
        double confidence,
        String evidence,
        String source
) {
}
